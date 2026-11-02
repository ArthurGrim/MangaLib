package de.mangalib.windowsadapter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionScraperClientTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void perStartArtifactCheckRejectsChangedBytesAndInvalidHashes()throws Exception{
        var file=java.nio.file.Files.writeString(directory.resolve("synthetic.jar"),"SYNTHETIC");
        var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));
        SimpleWorkerNative.requirePinned(file.toRealPath(),hash);
        java.nio.file.Files.writeString(file,"CHANGED");
        assertThrows(java.io.IOException.class,()->SimpleWorkerNative.requirePinned(file.toRealPath(),hash));
        assertThrows(java.io.IOException.class,()->SimpleWorkerNative.requirePinned(file.toRealPath(),"invalid"));
    }
    @Test void constructionAndShutdownNeedNeitherNativeNorBrowserResources(){
        try(var client=new ProductionScraperClient()){client.close();
            assertThrows(IllegalStateException.class,()->client.series("1"));
            assertThrows(IllegalStateException.class,()->client.volume("1"));
        }
    }
    @Test void invalidIndicesFailBeforeConfigurationOrNativeLoading(){
        try(var client=new ProductionScraperClient()){
            assertThrows(IllegalArgumentException.class,()->client.series("../1"));
            assertThrows(IllegalArgumentException.class,()->client.volume("0"));
        }
    }
    @Test void missingArtifactConfigurationFailsClosedWithoutRawException(){
        String key="mangalib.scraper.worker.path",old=System.clearProperty(key);
        try(var client=new ProductionScraperClient()){
            assertEquals("Import nicht verfügbar oder nicht sicher abgeschlossen.",
                    assertThrows(IllegalStateException.class,()->client.series("1")).getMessage());
            assertEquals("Import nicht verfügbar oder nicht sicher abgeschlossen.",
                    assertThrows(IllegalStateException.class,()->client.volume("1")).getMessage());
        }finally{if(old!=null)System.setProperty(key,old);}
    }
}
