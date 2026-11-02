package de.mangalib.windowsadapter;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import java.io.*;
import java.util.*;
import java.util.function.LongSupplier;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import static de.mangalib.windowsadapter.Win64ProcessLayouts.*;

/** Kalter nativer C-Startadapter. Ein Attribut: HANDLE_LIST; niemals JOB_LIST/BREAKAWAY. */
final class Win64ScCalls implements ScStartProvider.Calls {
    interface Symbols extends Win64NativeBindings.TransferSymbols {
        boolean TerminateProcess(Pointer process,int code); // BOOL/HANDLE/UINT, nur eigene C-Instanz.
        Pointer GetStdHandle(int which); // DWORD-Konstanten -10/-11/-12, HANDLE als Rückgabe.
    }
    record Bindings(Win64ProcessKernelCalls.ProcessRawApi process,Win64StdioChannels.Api stdio,
                    Symbols symbols,Win64NativeBindings.ComparisonSymbols comparison) { }
    private final Bindings nativeApi;
    private final Win64TransferBindings transfer;
    private final Win64StdioChannels pipes;
    private final Win64LaunchSpec spec;
    private final List<Memory> frame=new ArrayList<>();
    private Memory attr,command,environment,startup,info,uncertainCopy;
    private volatile boolean pending,settled;
    private boolean initialized,attempted;
    private volatile boolean diagnosticsStarted,diagnosticsEnded,diagnosticsOk=true;
    Win64ScCalls(Bindings bindings,Win64LaunchSpec spec,LongSupplier clock) {
        nativeApi=bindings;this.spec=spec;
        transfer=new Win64TransferBindings(bindings.process(),bindings.stdio(),bindings.symbols(),bindings.comparison());
        pipes=new Win64StdioChannels(bindings.stdio(),clock,spec.startDeadline());
    }
    /** Expliziter Aufruf ausschließlich durch spätere freigegebene main-Ausführung. */
    static Bindings load() {
        var host=Win64NativeBindings.Host.current();String dll=host.checkedDll();
        var options=Map.<String,Object>of(Library.OPTION_CALLING_CONVENTION,Function.ALT_CONVENTION,
                Library.OPTION_CLASSLOADER,Win64NativeBindings.Symbols.class.getClassLoader());
        try {
            var path=java.nio.file.Path.of(dll);
            if(!java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path))throw new IOException();
        } catch(IOException e){throw new IllegalStateException("sc-system-dll");}
        NativeLibrary library=NativeLibrary.getInstance(dll,options);
        var prepared=Win64NativeBindings.prepareTransferModules(host,(path,opts)->new Win64NativeBindings.TransferModule(){
            public void resolve(String name){library.getFunction(name,Function.ALT_CONVENTION);}
            public Symbols bind(){return Native.load(path,Symbols.class,opts);}
            public int lastError(){return Native.getLastError();}
            public void closeAfterFailedPreparation(){library.close();}
        },new Win64NativeBindings.JnaComparisonLoader(),true);
        return new Bindings(prepared.base().process(),prepared.base().stdio(),(Symbols)prepared.copies(),prepared.comparison());
    }
    public long currentProcessId(){return transfer.currentProcessId();}
    public ProcessState inspectProcess(long h){return transfer.inspectProcess(h);}
    public boolean nonInherited(long h){return transfer.nonInherited(h);}
    public Duplicate duplicate(long s,long h,long t,int rights,boolean inherit,int flags){return transfer.duplicate(s,h,t,rights,inherit,flags);}
    public boolean sameLocalObject(long a,long b){return transfer.sameLocalObject(a,b);}
    public long selfCopy(int access){return processCopy(-1,access);}
    public long processCopy(long held,int access){
        if(access!=PROCESS_ACCESS && access!=OuterJobCustodian.PROCESS_RIGHTS)throw new IllegalArgumentException("sc-process-rights");
        Memory output;
        synchronized(this){if(uncertainCopy!=null)throw failure();output=new Memory(8);output.clear();uncertainCopy=output;}
        boolean ok=nativeApi.symbols().DuplicateHandle(new Pointer(-1),new Pointer(held),new Pointer(-1),output,access,false,0);
        long value=Pointer.nativeValue(output.getPointer(0));
        // Bei Teilrückgabe/Exception Puffer behalten; kein Retry und kein verlorenes unbekanntes Handle.
        if(!ok || value==0 || value==-1)throw failure();
        output.close();synchronized(this){uncertainCopy=null;}return value;
    }
    private Memory memory(int size){Memory m=new Memory(size);m.clear();frame.add(m);return m;}
    private Memory bytes(byte[] bytes){Memory m=memory(bytes.length);m.write(0,bytes,0,bytes.length);return m;}
    public void prepare(long owner){
        if(attr!=null)throw failure();pipes.prepareCustodian(new Pointer(owner));
        try(Memory size=new Memory(8)){
            size.clear();boolean first=nativeApi.process().InitializeProcThreadAttributeList(null,1,0,size);
            long needed=size.getLong(0);
            if(first || nativeApi.process().error()!=122 || needed<1 || needed>4096)throw failure();
            attr=memory((int)needed);
            if(!nativeApi.process().InitializeProcThreadAttributeList(attr,1,0,size))throw failure();initialized=true;
            command=bytes(spec.commandBytes());environment=bytes(spec.environmentBytes());startup=memory(112);info=memory(24);
            startup.setInt(0,112);startup.setPointer(104,attr);pipes.attach(attr,startup);
        }
    }
    public ScStartProvider.Created create(ScStartProvider.Acquisition register){
        if(attempted || !initialized || !pipes.canStart())throw failure();attempted=true;pipes.beginStart();pending=true;
        // Keine Synchronisierung über CreateProcessW; bei Exception pending/Memory erhalten.
        boolean ok=nativeApi.process().CreateProcessW(new WString(spec.executable()),command,null,null,true,
                START_FLAGS,environment,new WString(spec.directory()),startup,info);
        long process=Pointer.nativeValue(info.getPointer(0)),thread=Pointer.nativeValue(info.getPointer(8));
        long pid=Integer.toUnsignedLong(info.getInt(16));
        RuntimeException invalid=null;
        try{if(process!=0 && process!=-1)register.acquired(ScStartProvider.HandleKind.PROCESS,process);}catch(RuntimeException e){invalid=e;}
        try{if(thread!=0 && thread!=-1)register.acquired(ScStartProvider.HandleKind.THREAD,thread);}catch(RuntimeException e){invalid=e;}
        pending=false;
        boolean childClosed=pipes.returned();
        if(initialized){nativeApi.process().DeleteProcThreadAttributeList(attr);initialized=false;pipes.attributesDeleted();}
        for(Memory m:frame)m.close();frame.clear();settled=true;
        if(invalid!=null)throw invalid;
        return new ScStartProvider.Created(ok && childClosed,process,thread,pid);
    }
    public boolean resume(long thread){return nativeApi.process().ResumeThread(new Pointer(thread))==1;}
    public boolean terminateProcess(long process){return nativeApi.symbols().TerminateProcess(new Pointer(process),73);}
    public boolean close(long h){return nativeApi.process().close(new Pointer(h));}
    public int exitCode(long h){
        if(nativeApi.process().WaitForSingleObject(new Pointer(h),0)!=0)throw failure();
        IntByReference code=new IntByReference();
        try(Memory ref=(Memory)code.getPointer()){
            if(!nativeApi.process().GetExitCodeProcess(new Pointer(h),code))throw failure();return code.getValue();
        }
    }
    public InputStream input(){return new InputStream(){
        byte[] chunk=new byte[0];int offset;
        public int read(){if(offset==chunk.length){var r=pipes.read(Win64StdioChannels.Stream.STDOUT,512);if(r.eof())return -1;chunk=r.bytes();offset=0;}return chunk[offset++]&255;}
        public void close(){/* Physischer Close erst über den gemeinsamen Pipeledger. */}
    };}
    public OutputStream output(){return new OutputStream(){
        public void write(int value){write(new byte[]{(byte)value},0,1);}
        public void write(byte[] bytes,int off,int len){
            Objects.checkFromIndexSize(off,len,bytes.length);
            while(len>0){int count=Math.min(512,len);int sent=pipes.write(Arrays.copyOfRange(bytes,off,off+count));off+=sent;len-=sent;}
        }
        public void close(){if(!pipes.closeInput())throw failure();}
    };}
    /** Genau eine begrenzte Diagnose-Lesespur; Rohdaten werden verworfen, niemals geloggt. */
    void beginDiagnostics(){
        if(diagnosticsStarted)throw failure();diagnosticsStarted=true;
        Thread reader=new Thread(()->{
            try{while(true){var data=pipes.read(Win64StdioChannels.Stream.STDERR,256);if(data.eof())break;}}
            catch(RuntimeException e){diagnosticsOk=false;}
            finally{diagnosticsEnded=true;}
        },"sc-bounded-stderr");reader.setDaemon(true);reader.start();
    }
    public boolean preparationSettled(){return settled && !pending && uncertainCopy==null && pipes.inFlight()==0
            && (!diagnosticsStarted || (diagnosticsEnded && diagnosticsOk));}
    public boolean closePipes(){return preparationSettled() && pipes.closeAll();}
    Bindings bindings(){return nativeApi;}
    private static IllegalStateException failure(){return new IllegalStateException("sc-native-unconfirmed");}
}
