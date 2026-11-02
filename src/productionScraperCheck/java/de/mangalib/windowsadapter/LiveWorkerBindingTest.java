package de.mangalib.windowsadapter;

import de.mangalib.service.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveWorkerBindingTest {
    @Test void typedFailureSinkCannotChangeOrExposeTheControlledReply()throws Exception{
        var request=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.VOLUME,"123");
        var input=new ByteArrayOutputStream();WorkerWire.request(input,request);var output=new ByteArrayOutputStream();
        var types=new ArrayList<String>();
        assertFalse(WorkerImportRunner.serve(new ByteArrayInputStream(input.toByteArray()),output,
                LiveScraperWorkerMain.bind(()->{throw new IllegalStateException("PRIVATE_SYNTHETIC_DETAIL");}),
                failure->{types.add(failure.getClass().getName());throw new IllegalArgumentException("DIAG_FAILED");}));
        assertEquals(List.of("java.lang.IllegalStateException"),types);
        var result=WorkerWire.reply(new ByteArrayInputStream(output.toByteArray()),request);
        assertFalse(result.success());assertTrue(result.values().isEmpty());
    }
    @Test void bothProductiveExtractorsAreBoundToExistingWireContract()throws Exception{
        for(var operation:WorkerWire.Operation.values())try(var series=mockStatic(MangaScraper.class);var volume=mockStatic(BandScraper.class)){
            var payload=new LinkedHashMap<String,String>();payload.put("Titel","日本語 & <Band>");payload.put("Preis","6,99");payload.put("Cover",null);
            series.when(()->MangaScraper.scrapeMangaData("123")).thenReturn(payload);
            volume.when(()->BandScraper.scrapeBandData("123")).thenReturn(payload);
            var request=new WorkerWire.Request(UUID.randomUUID(),operation,"123");
            var input=new ByteArrayOutputStream();WorkerWire.request(input,request);
            var output=new ByteArrayOutputStream();var calls=new AtomicInteger();
            assertTrue(WorkerImportRunner.serve(new ByteArrayInputStream(input.toByteArray()),output,LiveScraperWorkerMain.bind(calls::incrementAndGet)));
            assertEquals(1,calls.get());var answer=WorkerWire.reply(new ByteArrayInputStream(output.toByteArray()),request);
            assertTrue(answer.success());assertEquals(payload,answer.values());
            if(operation==WorkerWire.Operation.SERIES){series.verify(()->MangaScraper.scrapeMangaData("123"));volume.verifyNoInteractions();}
            else{volume.verify(()->BandScraper.scrapeBandData("123"));series.verifyNoInteractions();}
        }
    }
    @Test void initializationFailureReturnsOnlyControlledFailureAndNeverInvokesExtractors()throws Exception{
        try(var series=mockStatic(MangaScraper.class);var volume=mockStatic(BandScraper.class)){
            var request=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.VOLUME,"123");
            var input=new ByteArrayOutputStream();WorkerWire.request(input,request);var output=new ByteArrayOutputStream();
            assertFalse(WorkerImportRunner.serve(new ByteArrayInputStream(input.toByteArray()),output,
                    LiveScraperWorkerMain.bind(()->{throw new IllegalStateException("PRIVATE_SYNTHETIC_DETAIL");})));
            var answer=WorkerWire.reply(new ByteArrayInputStream(output.toByteArray()),request);
            assertFalse(answer.success());assertTrue(answer.values().isEmpty());
            assertFalse(new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("PRIVATE_SYNTHETIC_DETAIL"));
            series.verifyNoInteractions();volume.verifyNoInteractions();
        }
    }
}
