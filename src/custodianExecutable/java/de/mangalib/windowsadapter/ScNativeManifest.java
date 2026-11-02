package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Fester lokaler Versuch, ausdrückliche neue Hashfreigabe; niemals historische Marker übernehmen. */
final class ScNativeManifest {
    static final String JAVA="C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
    static final String PROFILE="--sc-native-empty-v1";
    static final String JNA_HASH="b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0";
    final Path root,run;
    final UUID session;
    final String classpath;
    private ScNativeManifest(Path root,Path run,UUID session,String classpath){this.root=root;this.run=run;this.session=session;this.classpath=classpath;}
    static ScNativeManifest read(boolean supervisor) throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();canonical(root);
        if(Runtime.version().feature()!=21 || !Path.of(System.getProperty("java.home"),"bin","java.exe").toRealPath().equals(Path.of(JAVA)))throw new IOException("sc-java-manifest");
        for(String env:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH"))if(System.getenv(env)!=null)throw new IOException("sc-jvm-override");
        if(!"true".equals(System.getProperty("jna.nosys")))throw new IOException("sc-native-options");
        List<Path> allowed=new ArrayList<>();
        for(String set:List.of("scNative","custodianExecutable","jobAdapter","communicationHost")){
            Path p=root.resolve("build/classes/java/"+set);canonical(p);allowed.add(p);
        }
        String[] cp=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator),-1);
        if(cp.length!=5)throw new IOException("sc-classpath-count");
        List<Path> actual=new ArrayList<>();for(String p:cp){Path path=Path.of(p).toAbsolutePath().normalize();canonical(path);actual.add(path);}
        requireClasspath(root,actual);
        Path jna=actual.get(4);if(!jna.getFileName().toString().equals("jna-5.17.0.jar") || !digest(Files.readAllBytes(jna)).equals(JNA_HASH))throw new IOException("sc-jna-pin");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");sha.update(Files.readAllBytes(Path.of(JAVA)));
        for(Path directory:allowed)try(var files=Files.walk(directory)){
            List<Path> all=files.filter(Files::isRegularFile).sorted().limit(1001).toList();
            if(all.isEmpty() || all.size()>1000)throw new IOException("sc-classpath-size");
            for(Path f:all){canonical(f);sha.update(directory.relativize(f).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));sha.update(Files.readAllBytes(f));}
        }
        sha.update(Files.readAllBytes(jna));
        for(String name:List.of("ScAttempt.ps1","ScOwnedObservation.ps1","Start-ScEmptyOnce.ps1","ScOuterControl.ps1")){
            Path script=root.resolve("src/scNative/powershell/"+name);canonical(script);
            if(Files.size(script)>100_000)throw new IOException("sc-owner-script-size");
            sha.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));sha.update(Files.readAllBytes(script));
        }
        String hash=HexFormat.of().formatHex(sha.digest());
        UUID session=ScAttemptFiles.session(System.getProperty(ScAttemptFiles.PROPERTY));
        Path attempt=ScAttemptFiles.directory(root,session.toString());canonical(attempt);
        // Der Besitzer muss die neue Freigabe VOR dem ersten S-Start atomar verbraucht haben.
        Path approval=attempt.resolve("approval.txt");canonical(approval);
        if(Files.size(approval)>128)throw new IOException("sc-approval-size");
        Path consumed=attempt.resolve("consumed");canonical(consumed);
        if(Files.size(consumed)>256)throw new IOException("sc-consumption-size");
        ScAttemptFiles.verify(Files.readString(approval),Files.readString(consumed),session,hash,digest(Files.readAllBytes(approval)));
        Path run=attempt.resolve("native");
        if(supervisor){Files.createDirectory(run);Files.writeString(run.resolve("consumed"),hash,StandardOpenOption.CREATE_NEW);}
        else {canonical(run);if(!Files.readString(run.resolve("consumed")).equals(hash))throw new IOException("sc-consumption");}
        return new ScNativeManifest(root,run,session,String.join(java.io.File.pathSeparator,actual.stream().map(Path::toString).toList()));
    }
    Win64LaunchSpec launch(long deadline){return new Win64LaunchSpec(JAVA,List.of("-Djna.nosys=true","-D"+ScAttemptFiles.PROPERTY+"="+session,"-cp",classpath,
            "de.mangalib.windowsadapter.ScCustodianMain",PROFILE),root.toString(),Map.of("SystemRoot","C:\\Windows","TEMP",run.toString(),"TMP",run.toString()),deadline);}
    private static void canonical(Path p) throws IOException {if(!p.isAbsolute() || !p.toRealPath().equals(p) || Files.isSymbolicLink(p))throw new IOException("sc-path");}
    static void requireClasspath(Path root,List<Path> actual) throws IOException {
        if(actual.size()!=5 || !actual.get(4).getFileName().toString().equals("jna-5.17.0.jar"))throw new IOException("sc-classpath-count");
        var expected=List.of("scNative","custodianExecutable","jobAdapter","communicationHost").stream().map(s->root.resolve("build/classes/java/"+s)).toList();
        if(!actual.subList(0,4).equals(expected))throw new IOException("sc-classpath-roots");
    }
    static UUID requireApproval(String text,String hash) throws IOException {
        String[] lines=text.strip().split("\\R");
        if(lines.length!=2 || !hash.matches("[0-9a-f]{64}") || !lines[1].equals(hash))throw new IOException("sc-unapproved-manifest");
        try{UUID session=UUID.fromString(lines[0]);if(!session.toString().equals(lines[0]) || session.equals(new UUID(0,0)))throw new IOException("sc-approval-session");return session;}
        catch(IllegalArgumentException e){throw new IOException("sc-approval-session");}
    }
    private static String digest(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
