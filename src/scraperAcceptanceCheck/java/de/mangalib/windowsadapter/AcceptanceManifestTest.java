package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceManifestTest {
    @TempDir Path root;
    Path manifest;
    LinkedHashMap<String,String> values;
    @BeforeEach void prepare()throws Exception{
        String id=UUID.randomUUID().toString();Path dir=Files.createDirectories(root.resolve(".gradle/t02-scraper-acceptance/"+id));manifest=dir.resolve("manifest.txt");
        values=new LinkedHashMap<>();values.put("version","SCRAPER_ACCEPTANCE_V1");values.put("attempt",id);values.put("profile","LIVE_IMPORTS_V1");
        values.put("root",root.toString());values.put("slot",Path.of(System.getProperty("user.home"),".mangalib","scraper","import-slot").toString());
        values.put("series","2502");values.put("volume","15295");values.put("outerMs","480000");values.put("network","DNS_HTTPS_WWW_MEDIA_ONLY");values.put("starts","OWNER1_WORKERS2_JOBS2_BROWSERS2");
        Path libs=Files.createDirectories(root.resolve("build/libs"));
        for(String role:AcceptanceManifest.FILES){
            Path file=switch(role){case "java"->Path.of("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe");
                case "owner"->libs.resolve("mangalib-scraper-acceptance.jar");case "worker","productionWorker"->libs.resolve("mangalib-scraper-worker.jar");default->root.resolve(role+".synthetic");};
            if(!role.equals("java"))Files.writeString(file,"SYNTHETIC");
            values.put("file."+role,file+"|"+AcceptanceManifest.sha(Files.readAllBytes(file)));
        }
        save();
    }
    void save()throws Exception{Files.writeString(manifest,String.join("\n",values.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"\n");}
    AcceptanceManifest read()throws Exception{return AcceptanceManifest.read(manifest,AcceptanceManifest.sha(Files.readAllBytes(manifest)));}
    @Test void coldReadChecksFilesWithoutCreatingApprovalOrConsumption()throws Exception{
        var m=read();assertEquals("LIVE_IMPORTS_V1",m.values.get("profile"));assertFalse(Files.exists(m.directory.resolve("approval.txt")));assertFalse(Files.exists(m.directory.resolve("consumed")));
    }
    @Test void changedArtifactBlocksBeforeAnyNativeOperation()throws Exception{
        Files.writeString(root.resolve("gecko.synthetic"),"CHANGED");assertThrows(Exception.class,this::read);
    }
    @Test void arbitraryNetworkScopeIsRejected()throws Exception{
        values.put("network","ANY_NETWORK");save();assertThrows(Exception.class,this::read);
    }
    @Test void differentImportIndicesOrBudgetAreRejected()throws Exception{
        values.put("series","123");save();assertThrows(Exception.class,this::read);
        values.put("series","2502");values.put("outerMs","999999");save();assertThrows(Exception.class,this::read);
    }
    @Test void duplicateManifestKeysAreRejected()throws Exception{
        Files.writeString(manifest,"profile=LIVE_IMPORTS_V1\n",StandardOpenOption.APPEND);assertThrows(Exception.class,this::read);
    }
    @Test void unapprovedOrUnconsumedAttemptCannotExecute()throws Exception{
        var m=read();assertThrows(Exception.class,m::verifyConsumption);
        String approval="SCRAPER_ACCEPTANCE_APPROVED_V1\n"+values.get("attempt")+"\n"+m.hash+"\n";
        AcceptanceManifest.writeNew(m.directory.resolve("approval.txt"),approval);assertThrows(Exception.class,m::verifyConsumption);
        String consumed="SCRAPER_ACCEPTANCE_CONSUMED_V1\n"+values.get("attempt")+"\n"+m.hash+"\n"+AcceptanceManifest.sha(approval.getBytes(StandardCharsets.UTF_8))+"\n";
        AcceptanceManifest.writeNew(m.directory.resolve("consumed"),consumed);m.verifyConsumption();
        assertThrows(FileAlreadyExistsException.class,()->AcceptanceManifest.writeNew(m.directory.resolve("consumed"),consumed));
    }
    @Test void alteredConsumptionOrUnknownFieldsAreRejected()throws Exception{
        values.put("extra","x");save();assertThrows(Exception.class,this::read);
    }
    @Test void timeoutProfileIsOneBrowserWithNoUpstreamScope()throws Exception{
        values.put("profile","BROWSER_TIMEOUT_V1");values.put("network","ABOUT_BLANK_LOOPBACK_PROXY_ONLY");
        values.put("timeoutStartMs","30000");values.put("timeoutRunMs","45000");values.put("timeoutCleanupMs","30000");values.put("timeoutOuterMs","100000");
        values.put("starts","OWNER1_WORKER1_JOB1_BROWSER1");
        Path worker=Files.writeString(root.resolve("build/libs/mangalib-scraper-boundary-worker.jar"),"SYNTHETIC_TIMEOUT");
        values.put("file.worker",worker+"|"+AcceptanceManifest.sha(Files.readAllBytes(worker)));save();
        assertEquals("BROWSER_TIMEOUT_V1",read().values.get("profile"));
        values.put("network","DNS_HTTPS_WWW_MEDIA_ONLY");save();assertThrows(Exception.class,this::read);
        values.put("network","ABOUT_BLANK_LOOPBACK_PROXY_ONLY");values.put("timeoutRunMs","60000");save();assertThrows(Exception.class,this::read);
    }
}
