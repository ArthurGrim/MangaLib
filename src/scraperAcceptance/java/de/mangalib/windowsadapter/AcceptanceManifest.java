package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Kalte Manifest-/Einmalprüfung. Keine Prozess-, DNS- oder JNA-Aufrufe. */
final class AcceptanceManifest {
    static final List<String> FILES=List.of("owner","worker","productionWorker","java","gecko","firefox","jna","jvm","jli","release","kernel","launcher","inspector","manual","historicalManifest","historicalResult","historicalConsumed");
    final Map<String,String> values;
    final Path directory;
    final String hash;
    private AcceptanceManifest(Map<String,String> values,Path directory,String hash){this.values=Map.copyOf(values);this.directory=directory;this.hash=hash;}
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static AcceptanceManifest read(Path file,String expected)throws Exception{
        SimpleWorkerNative.requirePinned(file,expected);
        if(Files.size(file)>16_384)throw new IOException("acceptance-manifest-size");
        var values=new LinkedHashMap<String,String>();
        for(String line:Files.readString(file).split("\n",-1)){
            if(line.isEmpty())continue;
            int eq=line.indexOf('=');
            if(eq<1 || line.length()>2048 || line.contains("\r") || values.putIfAbsent(line.substring(0,eq),line.substring(eq+1))!=null)
                throw new IOException("acceptance-manifest-format");
        }
        var keys=new HashSet<>(List.of("version","attempt","profile","root","slot","series","volume","outerMs","network","starts"));
        if("BROWSER_TIMEOUT_V1".equals(values.get("profile")))keys.addAll(List.of("timeoutStartMs","timeoutRunMs","timeoutCleanupMs","timeoutOuterMs"));
        FILES.forEach(k->keys.add("file."+k));
        if(!keys.equals(values.keySet()) || !"SCRAPER_ACCEPTANCE_V1".equals(values.get("version"))
                || !Set.of("LIVE_IMPORTS_V1","BROWSER_BOUNDARY_V1","BROWSER_TIMEOUT_V1").contains(values.get("profile"))
                || !"2502".equals(values.get("series")) || !"15295".equals(values.get("volume")) || !"480000".equals(values.get("outerMs")))
            throw new IOException("acceptance-manifest-contract");
        String id=values.get("attempt");if(!UUID.fromString(id).toString().equals(id) || UUID.fromString(id).equals(new UUID(0,0)))throw new IOException("acceptance-id");
        Path directory=file.getParent();
        if(!file.getFileName().toString().equals("manifest.txt") || !directory.equals(Path.of(values.get("root"),".gradle","t02-scraper-acceptance",id)))
            throw new IOException("acceptance-directory");
        if(!values.get("slot").equals(Path.of(System.getProperty("user.home"),".mangalib","scraper","import-slot").toString()))throw new IOException("acceptance-slot");
        var result=new AcceptanceManifest(values,directory,expected);
        for(String name:FILES)SimpleWorkerNative.requirePinned(result.file(name),result.fileHash(name));
        boolean live=values.get("profile").equals("LIVE_IMPORTS_V1");
        boolean timeout=values.get("profile").equals("BROWSER_TIMEOUT_V1");
        if(timeout && !("30000".equals(values.get("timeoutStartMs")) && "45000".equals(values.get("timeoutRunMs"))
                && "30000".equals(values.get("timeoutCleanupMs")) && "100000".equals(values.get("timeoutOuterMs"))))throw new IOException("acceptance-timeout-budgets");
        if(!values.get("starts").equals(live?"OWNER1_WORKERS2_JOBS2_BROWSERS2":"OWNER1_WORKER1_JOB1_BROWSER1")
                || !values.get("network").equals(live?"DNS_HTTPS_WWW_MEDIA_ONLY":timeout?"ABOUT_BLANK_LOOPBACK_PROXY_ONLY":"LOOPBACK_FIXTURES_ONLY_NO_DNS")
                || !result.file("worker").equals(Path.of(values.get("root"),"build","libs",live?"mangalib-scraper-worker.jar":"mangalib-scraper-boundary-worker.jar"))
                || !result.file("productionWorker").equals(Path.of(values.get("root"),"build","libs","mangalib-scraper-worker.jar")))throw new IOException("acceptance-scope");
        if(!result.file("java").equals(Path.of("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe"))
                || !result.file("owner").getFileName().toString().equals("mangalib-scraper-acceptance.jar"))throw new IOException("acceptance-launcher");
        return result;
    }
    Path file(String key){return Path.of(values.get("file."+key).split("\\|",-1)[0]);}
    String fileHash(String key){var parts=values.get("file."+key).split("\\|",-1);if(parts.length!=2)throw new IllegalArgumentException("acceptance-pin");return parts[1];}
    void verifyConsumption()throws Exception{
        String approval="SCRAPER_ACCEPTANCE_APPROVED_V1\n"+values.get("attempt")+"\n"+hash+"\n";
        Path a=directory.resolve("approval.txt"),c=directory.resolve("consumed");
        SimpleWorkerNative.requirePinned(a,sha(approval.getBytes(StandardCharsets.UTF_8)));
        SimpleWorkerNative.requirePinned(c,sha(("SCRAPER_ACCEPTANCE_CONSUMED_V1\n"+values.get("attempt")+"\n"+hash+"\n"+sha(approval.getBytes(StandardCharsets.UTF_8))+"\n").getBytes(StandardCharsets.UTF_8)));
        if(Files.size(a)>256 || Files.size(c)>512 || !Files.readString(a).equals(approval)
                || !Files.readString(c).equals("SCRAPER_ACCEPTANCE_CONSUMED_V1\n"+values.get("attempt")+"\n"+hash+"\n"+sha(approval.getBytes(StandardCharsets.UTF_8))+"\n"))
            throw new IOException("acceptance-consumption");
    }
    static void writeNew(Path path,String value)throws IOException{
        try(var file=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            var bytes=ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())file.write(bytes);file.force(true);
        }
    }
}
