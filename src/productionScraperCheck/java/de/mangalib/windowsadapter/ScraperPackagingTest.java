package de.mangalib.windowsadapter;

import org.junit.jupiter.api.Test;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class ScraperPackagingTest {
    @Test void workerContainsBothScrapersAndBoundedProtocolButNoApplicationOrDatabase()throws Exception{
        try(var jar=new JarFile(System.getProperty("scraperWorkerArtifact"))){
            assertEquals("de.mangalib.windowsadapter.LiveScraperWorkerMain",jar.getManifest().getMainAttributes().getValue("Main-Class"));
            for(String name:new String[]{"de/mangalib/windowsadapter/LiveScraperWorkerMain.class",
                    "de/mangalib/windowsadapter/WorkerImportRunner.class","de/mangalib/windowsadapter/WorkerWire.class",
                    "de/mangalib/service/MangaScraper.class","de/mangalib/service/BandScraper.class",
                    "de/mangalib/service/ScraperEgressProxy.class","org/openqa/selenium/firefox/FirefoxDriver.class"})
                assertNotNull(jar.getEntry(name),name);
            var entries=jar.stream().map(e->e.getName()).toList();
            for(String prefix:new String[]{"org/springframework/","com/mysql/","de/mangalib/entity/","de/mangalibmanual/"})
                assertTrue(entries.stream().noneMatch(n->n.startsWith(prefix)),prefix);
            for(String prefix:new String[]{"org/jsoup/", "io/github/bonigarcia/", "org/openqa/selenium/chrome/",
                    "org/openqa/selenium/edge/", "org/openqa/selenium/devtools/v120/",
                    "org/openqa/selenium/devtools/v121/", "org/openqa/selenium/devtools/v122/",
                    "org/openqa/selenium/devtools/v85/"})
                assertTrue(entries.stream().noneMatch(n->n.startsWith(prefix)),prefix);
            for(String name:new String[]{"application.properties","application-test.properties","application-manual-test.properties",
                    "de/mangalib/windowsadapter/SyntheticWorkerMain.class","de/mangalib/windowsadapter/WorkerJobProofMain.class",
                    "de/mangalib/windowsadapter/ProductionScraperClient.class"})assertNull(jar.getEntry(name),name);
        }
    }
    @Test void applicationContainsProductionOwnerAndExactlyPinnedParentJna()throws Exception{
        try(var jar=new JarFile(System.getProperty("scraperApplicationArtifact"))){
            assertNotNull(jar.getEntry("BOOT-INF/classes/de/mangalib/windowsadapter/ProductionScraperClient.class"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/de/mangalib/windowsadapter/WorkerJobOwner.class"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/jna-5.17.0.jar"));
            for(String module:new String[]{"core", "el", "websocket"})
                assertNotNull(jar.getEntry("BOOT-INF/lib/tomcat-embed-"+module+"-11.0.27.jar"));
            assertTrue(jar.stream().noneMatch(e->e.getName().startsWith("BOOT-INF/lib/tomcat-embed-")
                    && !e.getName().endsWith("-11.0.27.jar")));
            assertNull(jar.getEntry("BOOT-INF/lib/jna-5.13.0.jar"));
            assertTrue(jar.stream().noneMatch(e->e.getName().startsWith("BOOT-INF/lib/selenium-")));
            for(String prefix:new String[]{"BOOT-INF/lib/jsoup-", "BOOT-INF/lib/webdrivermanager-",
                    "BOOT-INF/lib/docker-java-", "BOOT-INF/lib/jna-platform-"})
                assertTrue(jar.stream().noneMatch(e->e.getName().startsWith(prefix)),prefix);
            for(String name:new String[]{"MangaScraper","BandScraper","ScraperBrowser","ScraperEgressProxy"})
                assertNull(jar.getEntry("BOOT-INF/classes/de/mangalib/service/"+name+".class"),name);
            for(String name:new String[]{"SyntheticWorkerMain","WorkerJobProofMain","Win64EmptyJobProbe","LiveScraperWorkerMain"})
                assertNull(jar.getEntry("BOOT-INF/classes/de/mangalib/windowsadapter/"+name+".class"),name);
        }
    }
}
