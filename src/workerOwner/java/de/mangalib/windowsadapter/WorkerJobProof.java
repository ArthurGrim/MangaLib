package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Enger synthetischer Jobnachweis. Nur JDK-Datei-/Ergebnisprüfung, kein nativer Lader. */
final class WorkerJobProof {
    static final String PROFILE="WORKER_JOB_PROOF_V1";
    static final String JAVA="C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
    static final String JNA=Path.of(System.getProperty("user.home"),".gradle/caches/modules-2/files-2.1/net.java.dev.jna/jna/5.17.0/33d12735bef894440780fce64f9758d420c7bae2/jna-5.17.0.jar").toString();
    static final List<String> DIRECTORIES=List.of("workerOwner","workerOwnerNative","jobAdapter");
    static final List<String> SCRIPTS=List.of("src/workerOwnerNative/powershell/Start-WorkerJobProof.ps1",
            "src/ownerProbe/powershell/OwnerNativeInspection.ps1");
    static final String JAVA_HASH="7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8";
    static final String JNA_HASH="b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0";
    private WorkerJobProof(){ }
    static String sha(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static void canonical(Path path)throws IOException{
        for(Path p=path;p!=null;p=p.getParent())if(!p.isAbsolute() || Files.isSymbolicLink(p) || !p.toRealPath().equals(p))throw new IOException("proof-path");
    }
    static Path directory(Path root,String id)throws IOException{
        try{UUID u=UUID.fromString(id);if(!u.toString().equals(id) || u.equals(new UUID(0,0)))throw new IllegalArgumentException();}
        catch(RuntimeException e){throw new IOException("proof-id");}
        return root.resolve(".gradle/t02-worker-job-proof").resolve(id);
    }
    static String classpath(Path root){
        var parts=new ArrayList<String>();for(String dir:DIRECTORIES)parts.add(root.resolve("build/classes/java/"+dir).toString());
        parts.add(JNA);return String.join(";",parts);
    }
    /** Einfacher, beidseitig identischer Hashplan: feste relative Namen plus Datei-SHA256. */
    static String fingerprint(Path root)throws IOException{
        canonical(root);var lines=new ArrayList<String>();lines.add(PROFILE);lines.add(root.toString());
        for(String name:List.of(JAVA,"C:\\Windows\\System32\\kernel32.dll",JNA,
                "C:\\Program Files\\Java\\jdk-21\\bin\\jli.dll","C:\\Program Files\\Java\\jdk-21\\bin\\server\\jvm.dll","C:\\Program Files\\Java\\jdk-21\\release")){
            Path p=Path.of(name);canonical(p);lines.add(name+"|"+fileHash(p));
        }
        for(String dir:DIRECTORIES){
            Path base=root.resolve("build/classes/java/"+dir);canonical(base);List<Path> files;
            try(var walk=Files.walk(base)){files=walk.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString,String.CASE_INSENSITIVE_ORDER)).limit(513).toList();}
            if(files.isEmpty() || files.size()>512)throw new IOException("proof-classes");
            for(Path p:files){canonical(p);String relative=base.relativize(p).toString();
                if(!relative.matches("de\\\\mangalib\\\\windowsadapter\\\\[A-Za-z0-9_$]+\\.class") || relative.contains("Test")
                        || Files.size(p)>1_048_576)throw new IOException("proof-class");
                byte[] bytes=Files.readAllBytes(p);
                if(bytes.length<8 || ByteBuffer.wrap(bytes).getInt()!=0xcafebabe || ByteBuffer.wrap(bytes).getShort(6)!=65)throw new IOException("proof-class-version");
                lines.add(dir+"/"+relative+"|"+sha(bytes));
            }
        }
        for(String name:SCRIPTS){Path p=root.resolve(name);canonical(p);if(Files.size(p)>100_000)throw new IOException("proof-script");lines.add(name+"|"+fileHash(p));}
        return sha((String.join("\n",lines)+"\n").getBytes(StandardCharsets.UTF_8));
    }
    static String fileHash(Path p)throws IOException{
        try{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){
            byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static void tuple(String manifest,String approval,String consumed,String id,String artifact,String root)throws IOException{
        directory(Path.of(root),id);
        if(!artifact.matches("[a-f0-9]{64}") || !manifest.equals(PROFILE+"\n"+id+"\n"+artifact+"\n"+root+"\n"))throw new IOException("proof-manifest");
        if(!approval.equals("WORKER_JOB_APPROVED_V1\n"+id+"\n"+artifact+"\n"+sha(manifest.getBytes(StandardCharsets.UTF_8))+"\n"))throw new IOException("proof-approval");
        if(!consumed.equals("WORKER_JOB_CONSUMED_V1\n"+id+"\n"+artifact+"\n"+sha(approval.getBytes(StandardCharsets.UTF_8))+"\n"))throw new IOException("proof-consumed");
    }
    static Path verify(Path root,String id,String artifact)throws IOException{
        Path dir=directory(root,id);canonical(dir);
        if(Runtime.version().feature()!=21 || !System.getProperty("java.class.path").equals(classpath(root))
                || !Path.of(System.getProperty("java.home"),"bin/java.exe").toRealPath().equals(Path.of(JAVA)))throw new IOException("proof-runtime");
        for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH"))if(System.getenv(key)!=null)throw new IOException("proof-override");
        if(!fileHash(Path.of(JAVA)).equals(JAVA_HASH) || !fileHash(Path.of(JNA)).equals(JNA_HASH) || !fingerprint(root).equals(artifact))throw new IOException("proof-integrity");
        tuple(read(dir.resolve("manifest.txt"),1024),read(dir.resolve("approval.txt"),256),read(dir.resolve("consumed"),256),id,artifact,root.toString());
        return dir;
    }
    static String read(Path p,int max)throws IOException{
        canonical(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("proof-file");
        byte[] bytes;try(var in=Files.newInputStream(p)){bytes=in.readNBytes(max+1);}if(bytes.length>max)throw new IOException("proof-size");
        for(byte b:bytes)if(b<0)throw new IOException("proof-ascii");return new String(bytes,StandardCharsets.US_ASCII);
    }
    static void write(Path dir,String name,String value)throws IOException{
        canonical(dir);if(value.length()>4096 || value.chars().anyMatch(c->c>127))throw new IOException("proof-result-size");
        try(var file=FileChannel.open(dir.resolve(name),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            var b=StandardCharsets.US_ASCII.encode(value);while(b.hasRemaining())file.write(b);file.force(true);
        }
    }
    /** Ausschließlich feste Phase, begrenzter Typ und numerischer Kernelcode; keine Message. */
    static String diagnostic(String step,Throwable failure){
        var allowed=List.of("PREFLIGHT","BINDING","NATIVE_LOAD","FACTORY","EXECUTE","REPORT",
                "createJob","configure","readLimits","prepareCleanupControl","prepareLaunch","startSuspended","resume",
                "observe","awaitEmptyJob","openProcess","identity","member","terminate","waitEnded","close");
        String type=failure.getClass().getName();
        if(type.length()>160 || !(type.startsWith("java.") || type.equals("de.mangalib.windowsadapter.KernelCalls$Failure")))type="OTHER";
        return "JOB_PROOF_FAILURE_V1\n"+(allowed.contains(step)?step:"UNCLASSIFIED")+"\n"+type+"\n"+
                (failure instanceof Failure f?f.code:0)+"\n";
    }
    static String describe(WorkerJobOwner.Diagnostic d){
        var text=new StringBuilder("WORKER_CACHED_DIAGNOSTIC_V1\nState="+d.state()+"\nPhase="+d.phase()+"\n");
        for(var i:d.issues())text.append("Issue=").append(i.phase()).append(',').append(i.kind()).append(',').append(i.type()).append(',').append(i.nativeCode()).append('\n');
        var o=d.lastObservation();
        if(o==null)text.append("Observation=NOT_RECORDED\n");
        else text.append("Observation=").append(o.complete()).append(',').append(o.stable()).append(',').append(o.active()).append(',').append(o.total()).append(',').append(o.pids().size()).append('\n');
        d.session().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->text.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
        return text.toString();
    }
    /** Ein historisches Jobtotal zählt auch bereits beendete Nachfahren. */
    static boolean accepted(WorkerJobOwner.Result r,WorkerJobOwner.State state,boolean journalClear,int factories){
        return r!=null && r.status()==WorkerJobOwner.Status.SUCCESS && r.groupEnded() && r.resourcesClosed() && r.channelEnded()
                && Integer.valueOf(0).equals(r.exitCode()) && r.assignedTotal()>=1 && r.ticket()!=null && !r.ticket().equals(new UUID(0,0))
                && r.worker()!=null && r.worker().pid()>0 && r.worker().creationTime()>0 && JAVA.equalsIgnoreCase(r.worker().image())
                && r.values().equals(Map.of("synthetic","true")) && state==WorkerJobOwner.State.IDLE && journalClear && factories==1;
    }
}
