package de.mangalib.ownerprobe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Enger eigenständiger Provenienzversuch: keine Worker-/Job-/Native-Factory auf dem Klassenpfad. */
final class OwnerProbeFiles {
    static final String JAVA="C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
    static final String DLL="C:\\Windows\\System32\\kernel32.dll";
    static final String JAVA_HASH="7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8";
    static final List<String> SCRIPTS=List.of("OwnerProbe.ps1","OwnerNativeInspection.ps1","Start-OwnerProvenance.ps1",
            "OwnerCompletion.ps1","Start-OwnerCompletion.ps1");
    private OwnerProbeFiles(){ }
    static UUID id(String text)throws IOException{
        try{UUID id=UUID.fromString(text);if(!id.toString().equals(text) || id.equals(new UUID(0,0)))throw new IllegalArgumentException();return id;}
        catch(RuntimeException e){throw new IOException("owner-id");}
    }
    static void canonical(Path p)throws IOException{
        for(Path a=p;a!=null;a=a.getParent())if(!a.isAbsolute() || Files.isSymbolicLink(a) || !a.toRealPath().equals(a))throw new IOException("owner-path");
    }
    static String sha(byte[] b){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static Path directory(Path root,String id)throws IOException{return root.resolve(".gradle/t02-owner-provenance").resolve(id(id).toString());}
    static Path directory(Path root,String id,String profile)throws IOException{
        if(profile.equals("OWNER_PROBE_V1"))return directory(root,id);
        if(profile.equals("OWNER_COMPLETION_V1"))return root.resolve(".gradle/t02-owner-completion").resolve(id(id).toString());
        throw new IOException("owner-profile");
    }
    static String fingerprint(Path root,Path java,Path dll,Path classes,Path scripts)throws IOException{
        try{
            var hash=MessageDigest.getInstance("SHA-256");canonical(root);canonical(java);canonical(dll);canonical(classes);canonical(scripts);
            hash.update(root.toString().getBytes(StandardCharsets.UTF_8));hash.update(Files.readAllBytes(java));hash.update(Files.readAllBytes(dll));
            // Auch Launcher/VM und Releasebeschreibung binden; java.exe allein identifiziert nicht die VM.
            for(String name:List.of("bin/jli.dll","bin/server/jvm.dll","release")){
                Path f=java.getParent().getParent().resolve(name);canonical(f);
                var fileHash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(f)){
                    byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)fileHash.update(buffer,0,count);
                }
                hash.update(name.getBytes(StandardCharsets.UTF_8));hash.update(HexFormat.of().formatHex(fileHash.digest()).getBytes(StandardCharsets.US_ASCII));
            }
            List<Path> files;
            try(var walk=Files.walk(classes)){files=walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p->classes.relativize(p).toString(),String.CASE_INSENSITIVE_ORDER)).limit(65).toList();}
            if(files.isEmpty() || files.size()>64)throw new IOException("owner-classes");
            for(Path f:files){canonical(f);String name=classes.relativize(f).toString();
                if(Files.size(f)>1_048_576)throw new IOException("owner-class-size");
                if(!name.matches("de\\\\mangalib\\\\ownerprobe\\\\(OwnerProbeFiles|OwnerProbeLifecycle|OwnerProvenanceMain|OwnerCompletionMain)(\\$[A-Za-z0-9_]+)?\\.class"))throw new IOException("owner-class-scope");
                byte[] b=Files.readAllBytes(f);if(b.length<8 || ByteBuffer.wrap(b).getInt()!=0xcafebabe || ByteBuffer.wrap(b).getShort(6)!=65)throw new IOException("owner-java-version");
                hash.update(name.getBytes(StandardCharsets.UTF_8));hash.update(b);
            }
            for(String name:SCRIPTS){Path f=scripts.resolve(name);canonical(f);if(Files.size(f)>100_000)throw new IOException("owner-script-size");
                hash.update(name.getBytes(StandardCharsets.UTF_8));hash.update(Files.readAllBytes(f));}
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static void verify(Path root,String attempt,String artifact)throws IOException{
        verify(root,attempt,artifact,"OWNER_PROBE_V1");
    }
    static void verify(Path root,String attempt,String artifact,String profile)throws IOException{
        id(attempt);if(!artifact.matches("[a-f0-9]{64}") || Runtime.version().feature()!=21)throw new IOException("owner-profile");
        Path java=Path.of(JAVA),dll=Path.of(DLL),classes=root.resolve("build/classes/java/ownerProbe"),scripts=root.resolve("src/ownerProbe/powershell");
        if(!Path.of(System.getProperty("java.home"),"bin","java.exe").toRealPath().equals(java)
                || !System.getProperty("java.class.path").equals(classes.toString()))throw new IOException("owner-runtime");
        for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH"))if(System.getenv(key)!=null)throw new IOException("owner-override");
        if(!sha(Files.readAllBytes(java)).equals(JAVA_HASH) || !fingerprint(root,java,dll,classes,scripts).equals(artifact))throw new IOException("owner-artifact");
        Path dir=directory(root,attempt,profile);canonical(dir);
        String manifest=readRequired(dir.resolve("manifest.txt"),1024);
        String approval=readRequired(dir.resolve("approval.txt"),256);
        validateTuple(manifest,approval,readRequired(dir.resolve("consumed"),256),attempt,artifact,sha(Files.readAllBytes(dll)),root.toString(),profile);
    }
    static void validateTuple(String manifest,String approval,String consumed,String attempt,String artifact,String dllHash,String root)throws IOException{
        validateTuple(manifest,approval,consumed,attempt,artifact,dllHash,root,"OWNER_PROBE_V1");
    }
    static void validateTuple(String manifest,String approval,String consumed,String attempt,String artifact,String dllHash,String root,String profile)throws IOException{
        String approved,used;
        switch(profile){
            case "OWNER_PROBE_V1"->{approved="OWNER_APPROVED_V1";used="OWNER_CONSUMED_V1";}
            case "OWNER_COMPLETION_V1"->{approved="OWNER_COMPLETION_APPROVED_V1";used="OWNER_COMPLETION_CONSUMED_V1";}
            default->throw new IOException("owner-profile");
        }
        id(attempt);if(!artifact.matches("[a-f0-9]{64}") || !dllHash.matches("[a-f0-9]{64}"))throw new IOException("owner-hash");
        if(!manifest.equals(profile+"\n"+attempt+"\n"+artifact+"\n"+JAVA_HASH+"\n"+dllHash+"\n"+root+"\n"))throw new IOException("owner-manifest");
        if(!approval.equals(approved+"\n"+attempt+"\n"+artifact+"\n"+sha(manifest.getBytes(StandardCharsets.UTF_8))+"\n"))throw new IOException("owner-approval");
        if(!consumed.equals(used+"\n"+attempt+"\n"+artifact+"\n"+sha(approval.getBytes(StandardCharsets.UTF_8))+"\n"))throw new IOException("owner-consumption");
    }
    static String readRequired(Path p,int max)throws IOException{
        canonical(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>max)throw new IOException("owner-file");
        byte[] b;try(var in=Files.newInputStream(p)){b=in.readNBytes(max+1);}if(b.length>max)throw new IOException("owner-file");
        for(byte c:b)if(c<0)throw new IOException("owner-ascii");return new String(b,StandardCharsets.US_ASCII);
    }
    static String message(Path dir,String name)throws IOException{
        Path p=dir.resolve(name);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;
        String text=readRequired(p,512);return text.endsWith("\n")?text:null;
    }
    static void write(Path dir,String name,String text)throws IOException{
        if(text.length()>512)throw new IOException("owner-message-size");canonical(dir);
        try(var f=FileChannel.open(dir.resolve(name),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            ByteBuffer b=StandardCharsets.US_ASCII.encode(text);while(b.hasRemaining())f.write(b);f.force(true);
        }
    }
}
