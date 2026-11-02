package de.mangalib.windowsadapter;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import java.io.*;
import java.util.*;

/** Drei exakte stdio-Vererbungsziele; keine Job-/Ownerhandles. Eigener begrenzter Pipeledger. */
final class WorkerNativePipes implements Win64ProcessKernelCalls.LaunchStdio {
    private final Win64StdioChannels.Api api;
    private final List<Pointer> held=new ArrayList<>();
    private final Set<Pointer> closed=new HashSet<>(),closing=new HashSet<>();
    private final Pointer[] parent=new Pointer[3],child=new Pointer[3];
    private Memory list;
    private boolean prepared,attached,returned,deleted;
    private volatile boolean pending;
    private volatile boolean uncertain;
    private volatile int users;private final Map<Pointer,Integer> uses=new HashMap<>();
    private volatile long stdout,stderr,written;
    private volatile int lastIoError;
    private volatile boolean stdoutEof,stderrEof;
    private volatile String ioPhase="NONE";
    private final byte[] tail=new byte[64*1024];private int tailPosition;
    WorkerNativePipes(Win64StdioChannels.Api api){this.api=api;}
    public void prepare(Pointer job){
        if(prepared)throw fail();prepared=true;
        for(int stream=0;stream<3;stream++){
            try(Memory r=new Memory(8);Memory w=new Memory(8)){
                r.clear();w.clear();boolean ok=api.CreatePipe(r,w,null,1024);
                Pointer read=r.getPointer(0),write=w.getPointer(0);
                acquire(read);acquire(write);if(!ok)throw fail();
                parent[stream]=stream==0?write:read;child[stream]=stream==0?read:write;
                if(!api.SetHandleInformation(parent[stream],1,0) || !api.SetHandleInformation(child[stream],1,1)
                        || flags(parent[stream])!=0 || flags(child[stream])!=1)throw fail();
            }catch(RuntimeException e){throw e;}
        }
    }
    private void acquire(Pointer p){
        if(p==null || Pointer.nativeValue(p)==0 || Pointer.nativeValue(p)==-1 || held.contains(p))throw fail();held.add(p);
    }
    private int flags(Pointer p){var n=new IntByReference();try(Memory m=(Memory)n.getPointer()){
        if(!api.GetHandleInformation(p,n))throw fail();return n.getValue()&1;}}
    public void attach(Pointer attributes,Pointer startup){
        if(held.size()!=6 || attached)throw fail();list=new Memory(24);
        for(int i=0;i<3;i++)list.setPointer(i*8L,child[i]);
        if(!api.UpdateProcThreadAttribute(attributes,0,Win64StdioChannels.HANDLE_LIST,list,24,null,null))throw fail();
        startup.setInt(60,0x100);for(int i=0;i<3;i++)startup.setPointer(80+i*8L,child[i]);attached=true;
    }
    public boolean canStart(){return attached && !pending && !returned && !uncertain;}
    public void beginStart(){if(!canStart())throw fail();pending=true;}
    public boolean returned(){pending=false;returned=true;boolean ok=true;for(Pointer p:child)ok&=close(p);return ok;}
    public boolean canResume(){return returned && !uncertain;}
    public void attributesDeleted(){deleted=true;if(list!=null){list.close();list=null;}}
    private byte[] read(int stream)throws IOException{
        ioPhase=stream==1?"STDOUT_READ":"STDERR_READ";
        Pointer p=parent[stream];synchronized(this){if(p==null || closing.contains(p))throw new IOException("worker-pipe");users++;uses.merge(p,1,Integer::sum);}
        try(Memory data=new Memory(65536)){
            var got=new IntByReference();try(Memory n=(Memory)got.getPointer()){
                boolean ok=api.ReadFile(p,data,65536,got,null);int error=ok?0:api.error(),count=got.getValue();
                if(!ok)lastIoError=error;
                if(!ok && error==109 && count==0){if(stream==1)stdoutEof=true;else stderrEof=true;return new byte[0];}
                if(!ok || count<=0 || count>65536)throw new IOException("worker-pipe");
                if(stream==1){stdout+=count;if(stdout>WorkerWire.RESULT+2L*WorkerWire.REQUEST+16)throw new IOException("worker-output-limit");}
                else {stderr+=count;if(stderr>1024*1024)throw new IOException("worker-stderr-limit");}
                return data.getByteArray(0,count);
            }
        }catch(RuntimeException e){uncertain=true;throw new IOException("worker-native-io");}
        finally{synchronized(this){users--;uses.merge(p,-1,Integer::sum);}}
    }
    InputStream stdout(){return new InputStream(){
        byte[] bytes=new byte[0];int offset;boolean eof;
        public int read()throws IOException{
            if(eof)return -1;if(offset==bytes.length){bytes=WorkerNativePipes.this.read(1);offset=0;if(bytes.length==0){eof=true;return -1;}}
            return bytes[offset++]&255;
        }
        public int read(byte[] b,int off,int len)throws IOException{
            Objects.checkFromIndexSize(off,len,b.length);if(len==0)return 0;int first=read();if(first<0)return -1;b[off]=(byte)first;
            int more=Math.min(len-1,bytes.length-offset);System.arraycopy(bytes,offset,b,off+1,more);offset+=more;return more+1;
        }
    };}
    OutputStream stdin(){return new OutputStream(){
        public void write(int b)throws IOException{write(new byte[]{(byte)b});}
        public void write(byte[] b,int off,int len)throws IOException{
            ioPhase="STDIN_WRITE";
            Objects.checkFromIndexSize(off,len,b.length);if(len==0)return;
            if(written+len>WorkerWire.REQUEST+8L)throw new IOException("worker-request-limit");
            Pointer p=parent[0];synchronized(WorkerNativePipes.this){if(p==null || closing.contains(p))throw new IOException("worker-input");users++;uses.merge(p,1,Integer::sum);}
            try(Memory data=new Memory(len)){
                data.write(0,b,off,len);int sent=0;
                while(sent<len){var n=new IntByReference();try(Memory count=(Memory)n.getPointer()){
                    boolean ok=api.WriteFile(p,data.share(sent),len-sent,n,null);
                    if(!ok)lastIoError=api.error();
                    if(!ok || n.getValue()<1 || n.getValue()>len-sent)throw new IOException("worker-input");
                    sent+=n.getValue();}}
                written+=sent;
            }catch(RuntimeException e){uncertain=true;throw new IOException("worker-native-io");}
            finally{synchronized(WorkerNativePipes.this){users--;uses.merge(p,-1,Integer::sum);}}
        }
        public void close()throws IOException{if(!WorkerNativePipes.this.close(parent[0]))throw new IOException("worker-half-close");}
    };}
    void drainStderr()throws IOException{
        while(true){byte[] bytes=read(2);if(bytes.length==0)return;
            for(byte b:bytes){tail[tailPosition++%tail.length]=b;}}
    }
    synchronized boolean settled(){return !uncertain && !pending;}
    private boolean close(Pointer p){
        if(p==null)return true;
        synchronized(this){if(closed.contains(p))return true;if(uses.getOrDefault(p,0)!=0 || !closing.add(p))return false;}
        try{boolean ok=api.CloseHandle(p);synchronized(this){if(ok)closed.add(p);else uncertain=true;}return ok;}
        catch(RuntimeException e){uncertain=true;return false;}
    }
    boolean closeAll(){
        if(!settled())return false;
        synchronized(this){if(users!=0)return false;}
        boolean ok=true;for(Pointer p:held)ok&=close(p);return ok && closed.size()==held.size();
    }
    synchronized boolean resourcesClosed(){return !uncertain && !pending && users==0 && list==null && closed.size()==held.size();}
    /** Nur flüchtig gespeicherte Zähler/Flags, bewusst keine frische Nativeabfrage. */
    Map<String,String> diagnostic(){
        return Map.ofEntries(Map.entry("PipeUncertain",Boolean.toString(uncertain)),Map.entry("PipeStartPending",Boolean.toString(pending)),
                Map.entry("ActivePipeUsers",Integer.toString(users)),Map.entry("StdoutBytes",Long.toString(stdout)),Map.entry("StderrBytes",Long.toString(stderr)),
                Map.entry("WrittenBytes",Long.toString(written)),Map.entry("StdoutEof",Boolean.toString(stdoutEof)),Map.entry("StderrEof",Boolean.toString(stderrEof)),
                Map.entry("LastNativeIoError",Integer.toString(lastIoError)),Map.entry("IoPhase",ioPhase));
    }
    private IllegalStateException fail(){return new IllegalStateException("worker-pipe-preparation");}
}
