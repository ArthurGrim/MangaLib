package de.mangalib.windowsadapter;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Kalte native Verdrahtung. Kein main, DLL-Lader, freier Executablepfad oder Produktstart. */
final class SimpleWorkerNative {
    private static final String JAVA="C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
    private static final String JAVA_HASH="7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8";
    private SimpleWorkerNative(){ }
    /** Bindings dürfen erst durch einen später separat freigegebenen OS-Harness geladen werden. */
    static WorkerJobOwner.Factory synthetic(Win64NativeBindings.StdioPrepared prepared,Path root,Path temp,LongSupplier clock)throws Exception{
        Path java=Path.of(JAVA),classes=root.toAbsolutePath().normalize().resolve("build/classes/java/workerOwner");
        canonical(root);canonical(java);canonical(classes);canonical(temp);
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(java))).equals(JAVA_HASH))
            throw new IOException("worker-java-pin");
        try(var files=Files.walk(classes)){
            var all=files.toList();for(Path f:all){canonical(f);if(f.getFileName().toString().contains("Test"))throw new IOException("worker-test-class");}
        }
        return (deadline,allowed)->{
            try{return new NativeSession(prepared,root,classes,Files.createTempDirectory(temp,"worker-"),clock,deadline,allowed,null);}
            catch(IOException e){throw new UncheckedIOException(e);}
        };
    }
    /** Produktiver Start verwendet denselben atomaren Job-/Handlepfad ohne alternativen Startweg. */
    static WorkerJobOwner.Factory live(Win64NativeBindings.StdioPrepared prepared,Path root,Path jar,String jarHash,Path temp,
                                      List<String> browserArguments,LongSupplier clock)throws Exception{
        canonical(root);canonical(jar);canonical(temp);canonical(Path.of(JAVA));
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(JAVA)))).equals(JAVA_HASH))
            throw new IOException("worker-java-pin");
        var arguments=new ArrayList<String>(List.of("-Xmx256m","-XX:-UsePerfData","-jar",jar.toString()));
        arguments.addAll(browserArguments);
        if(browserArguments.size()!=5 || !browserArguments.getFirst().equals("--live-scraper-v1"))throw new IOException("worker-arguments");
        return (deadline,allowed)->{
            try{
                // Auch ein langlebiger Owner darf nachträglich ersetzte Artefakte nicht starten.
                requirePinned(Path.of(JAVA),JAVA_HASH);requirePinned(jar,jarHash);
                requirePinned(Path.of(browserArguments.get(1)),browserArguments.get(2));
                requirePinned(Path.of(browserArguments.get(3)),browserArguments.get(4));
                if(!allowed.getAsBoolean() || clock.getAsLong()>=deadline)throw new IOException("worker-start-expired");
                return new NativeSession(prepared,root,jar,Files.createTempDirectory(temp,"worker-"),clock,deadline,allowed,List.copyOf(arguments));
            }
            catch(IOException e){throw new UncheckedIOException(e);}
        };
    }
    /** Reine Dateiprüfung ohne JNA-/Prozessaufruf, unmittelbar vor jedem Start erneut verwendet. */
    static void requirePinned(Path path,String expected)throws IOException{
        canonical(path);
        if(expected==null || !expected.matches("[a-f0-9]{64}") || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("worker-artifact-pin");
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(path)){byte[] buffer=new byte[64*1024];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
            if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new IOException("worker-artifact-pin");
        }catch(java.security.NoSuchAlgorithmException e){throw new IOException("worker-artifact-pin");}
    }
    private static void canonical(Path p)throws IOException{
        if(!p.isAbsolute() || !p.toRealPath().equals(p) || Files.isSymbolicLink(p))throw new IOException("worker-path");
        for(Path a=p.getParent();a!=null;a=a.getParent())if(Files.isSymbolicLink(a) || !a.toRealPath().equals(a))throw new IOException("worker-path");
    }
    private static final class NativeSession implements WorkerJobOwner.Session {
        final Win64ProcessKernelCalls kernel;
        final WorkerNativePipes pipes;
        final Path temp;
        final WorkerTemporaryDirectory tempDirectory;
        final boolean live;
        final AtomicInteger calls=new AtomicInteger();final AtomicBoolean unknown=new AtomicBoolean();
        volatile String failedCall="NONE";
        volatile boolean stopped,readerDone,readerFailed,protocolEnded,tempClosed;
        Thread reader;
        volatile String proxyEvidence="NOT_MEASURED";
        volatile String workerFailure="NOT_MEASURED";
        NativeSession(Win64NativeBindings.StdioPrepared bindings,Path root,Path classes,Path temp,LongSupplier clock,long deadline,BooleanSupplier allowed,List<String> liveArguments)throws IOException{
            this.temp=temp;live=liveArguments!=null;tempDirectory=new WorkerTemporaryDirectory(temp);pipes=new WorkerNativePipes(bindings.stdio());
            // Proxy zählt die tatsächlich delegierten Prozess-/Jobaufrufe, nicht zusammengesetzte Handshakes.
            var api=(Win64ProcessKernelCalls.ProcessRawApi)Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Win64ProcessKernelCalls.ProcessRawApi.class},(proxy,method,args)->{
                        calls.incrementAndGet();try{return method.invoke(bindings.process(),args);}
                        catch(InvocationTargetException e){failedCall=method.getName();unknown.set(true);throw e.getCause();}
                        finally{calls.decrementAndGet();}
                    });
            var spec=new Win64LaunchSpec(JAVA,liveArguments==null?List.of("-Xmx256m","-cp",classes.toString(),
                    "de.mangalib.windowsadapter.SyntheticWorkerMain","--synthetic-worker-v1"):liveArguments,root.toString(),
                    Map.of("SystemRoot","C:\\Windows","TEMP",temp.toString(),"TMP",temp.toString()),deadline);
            var authorization=new Win64ProcessKernelCalls.StartAuthorization(){
                Resource job,process;
                public boolean beforeCreate(Resource j){job=j;return !stopped && allowed.getAsBoolean();}
                public boolean created(Resource j,Resource p){process=p;return j==job && p!=null && !stopped && allowed.getAsBoolean();}
                public boolean beforeResume(Resource j,Resource p){return j==job && p==process && !stopped && allowed.getAsBoolean();}
                public boolean afterResume(Resource j,Resource p){return beforeResume(j,p);}
            };
            kernel=new Win64ProcessKernelCalls(api,spec,clock,pipes,authorization);
        }
        public KernelCalls kernel(){return kernel;}
        public String executable(){return JAVA;}
        public WorkerWire.Reply exchange(WorkerWire.Request request,Runnable bound)throws IOException{
            reader=new Thread(()->{try{pipes.drainStderr();}catch(IOException e){readerFailed=true;}finally{readerDone=true;}},"worker-bounded-stderr");
            reader.setDaemon(true);reader.start();
            try(var out=pipes.stdin()){WorkerWire.request(out,request);}
            WorkerWire.Reply reply=WorkerWire.reply(pipes.stdout(),request,bound);protocolEnded=true;return reply;
        }
        public void cancelStarts(){stopped=true;}
        public boolean settled(){return calls.get()==0 && !unknown.get() && pipes.settled();}
        public boolean healthy(){return !readerFailed;}
        public boolean channelEnded(){return protocolEnded && readerDone && !readerFailed;}
        public Map<String,String> diagnostic(){
            var values=new LinkedHashMap<>(pipes.diagnostic());
            values.put("NativeCallsInFlight",Integer.toString(calls.get()));values.put("UnknownNativeCall",Boolean.toString(unknown.get()));
            values.put("FailedNativeMethod",failedCall);values.put("ReaderDone",Boolean.toString(readerDone));
            values.put("ReaderFailed",Boolean.toString(readerFailed));values.put("ProtocolEnded",Boolean.toString(protocolEnded));
            values.put("ProxyEvidence",proxyEvidence);
            values.put("WorkerFailure",workerFailure);
            return Map.copyOf(values);
        }
        public boolean resourcesClosed(){return settled() && pipes.resourcesClosed() && tempClosed
                && kernel.outstandingHandles()==0 && kernel.outstandingBuffers()==0
                && kernel.retainedProcessHandles()==0 && kernel.retainedLaunchBuffers()==0;}
        public int exitCode(Resource p){return kernel.exitCode(p);}
        public boolean closeIo(long remaining){
            if(remaining<=0)return false;
            if(reader!=null){try{reader.join(java.time.Duration.ofNanos(remaining));}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
                if(reader.isAlive())return false;}
            if(!pipes.closeAll())return false;
            if(live){
                Path failure=temp.resolve("scraper-failure.evidence");
                if(Files.exists(failure,LinkOption.NOFOLLOW_LINKS))try{
                    canonical(failure);if(Files.size(failure)<=768){String value=Files.readString(failure);
                        if(value.matches("WORKER_FAILURE_V1\n(?:[A-Za-z0-9_$.]{1,160}\n){1,4}"))workerFailure=value.replace('\n','|');}
                }catch(IOException invalid){workerFailure="UNAVAILABLE";}
                Path evidence=temp.resolve("scraper-proxy.evidence");
                try{
                    canonical(evidence);
                    if(Files.size(evidence)<=128){String value=Files.readString(evidence);
                        if(value.matches("SCRAPER_PROXY_V1\n[0-9]{1,4}\n[0-9]{1,4}\n[0-9]{1,4}\n[0-9]{1,4}\n"))proxyEvidence=value.replace('\n','|');}
                }catch(IOException invalid){proxyEvidence="UNAVAILABLE";}
            }
            tempClosed=live?tempDirectory.closeOwnedTree(true,remaining):tempDirectory.close(true);return tempClosed;
        }
    }
}
