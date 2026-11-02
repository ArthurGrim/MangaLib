package de.mangalib.windowsadapter;

import org.junit.jupiter.api.Test;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class AcceptancePackagingTest {
    @Test void successfulLiveDataWithoutMeasuredProxyConnectionsCannotPass(){
        assertFalse(ScraperAcceptanceMain.networkEvidence("SCRAPER_PROXY_V1|0|0|0|0|",true));
        assertFalse(ScraperAcceptanceMain.networkEvidence("SCRAPER_PROXY_V1|3|0|0|3|",true));
        assertTrue(ScraperAcceptanceMain.networkEvidence("SCRAPER_PROXY_V1|3|2|2|1|",true));
    }
    @Test void negativeProfileCannotPassWithAnyUpstreamConnectionOrDns(){
        assertTrue(ScraperAcceptanceMain.networkEvidence("SCRAPER_PROXY_V1|3|0|0|3|",false));
        assertFalse(ScraperAcceptanceMain.networkEvidence("SCRAPER_PROXY_V1|3|1|0|2|",false));
        assertFalse(ScraperAcceptanceMain.networkEvidence("UNAVAILABLE",false));
    }
    @Test void boundaryUsesIdenticalProductionBrowserPolicyAndProxyBytecode()throws Exception{
        try(var product=new JarFile(System.getProperty("productionWorkerJar"));var probe=new JarFile(System.getProperty("boundaryJar"))){
            for(String name:product.stream().map(e->e.getName()).filter(n->n.endsWith(".class")).toList()){
                assertNotNull(probe.getEntry(name),name);
                assertArrayEquals(product.getInputStream(product.getEntry(name)).readAllBytes(),probe.getInputStream(probe.getEntry(name)).readAllBytes(),name);
            }
            assertNull(product.getEntry("de/mangalib/service/BrowserBoundaryProbe.class"));
            assertNull(product.getEntry("de/mangalib/service/BrowserTimeoutProbe.class"));
            assertNotNull(probe.getEntry("de/mangalib/service/BrowserBoundaryProbe.class"));
            assertNotNull(probe.getEntry("de/mangalib/service/BrowserTimeoutProbe.class"));
        }
    }
    @Test void ownerArtifactHasNoSpringDatabaseOrBrowserRuntime()throws Exception{
        try(var owner=new JarFile(System.getProperty("acceptanceJar"))){
            assertEquals("de.mangalib.windowsadapter.ScraperAcceptanceMain",owner.getManifest().getMainAttributes().getValue("Main-Class"));
            for(String prefix:new String[]{"org/springframework/","com/mysql/","org/openqa/selenium/","de/mangalib/entity/"})
                assertTrue(owner.stream().noneMatch(e->e.getName().startsWith(prefix)),prefix);
            assertNull(owner.getEntry("application.properties"));
        }
    }
}
