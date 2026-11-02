package de.mangalib.service;

import de.mangalib.windowsadapter.ProductionScraperClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveScraperGatewayTest {
    @Test void bothOperationsPreservePayloadAndOnlyUseJobClient(){
        var client=mock(ProductionScraperClient.class);
        var values=new java.util.HashMap<String,String>();values.put("Titel","Ä & <Test>");values.put("Cover",null);
        when(client.series("1")).thenReturn(values);when(client.volume("2")).thenReturn(values);
        try(var series=mockStatic(MangaScraper.class);var volume=mockStatic(BandScraper.class)){
            var gateway=new LiveScraperGateway(client);
            assertSame(values,gateway.scrapeSeries("1"));assertSame(values,gateway.scrapeVolume("2"));
            verify(client).series("1");verify(client).volume("2");verifyNoMoreInteractions(client);
            series.verifyNoInteractions();volume.verifyNoInteractions();
        }
    }
    @Test void quarantinedOrFailedClientNeverFallsBackToSelenium(){
        var client=mock(ProductionScraperClient.class);
        when(client.series("1")).thenThrow(new IllegalStateException("unavailable"));
        when(client.volume("2")).thenThrow(new IllegalStateException("unavailable"));
        try(var series=mockStatic(MangaScraper.class);var volume=mockStatic(BandScraper.class)){
            var gateway=new LiveScraperGateway(client);
            assertThrows(IllegalStateException.class,()->gateway.scrapeSeries("1"));
            assertThrows(IllegalStateException.class,()->gateway.scrapeVolume("2"));
            series.verifyNoInteractions();volume.verifyNoInteractions();
        }
    }
    @Test void testProfileDisablesNativeClientAndShutdownNeedsNoNativeLoading(){
        try(var client=LiveScraperGateway.sharedScraperWorker(new MockEnvironment().withProperty("spring.profiles.active","test"))){
            assertThrows(IllegalStateException.class,()->client.series("1"));
        }
        var client=mock(ProductionScraperClient.class);new LiveScraperGateway(client).close();verify(client).close();
    }
}
