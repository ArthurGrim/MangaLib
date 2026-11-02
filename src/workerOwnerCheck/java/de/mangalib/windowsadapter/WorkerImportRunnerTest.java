package de.mangalib.windowsadapter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkerImportRunnerTest {
    byte[] request(WorkerWire.Request r)throws IOException{
        var bytes=new ByteArrayOutputStream();WorkerWire.request(bytes,r);return bytes.toByteArray();
    }
    @ParameterizedTest @EnumSource(WorkerWire.Operation.class)
    void helloPrecedesTheImportAndTheOriginalFlatMapRoundtrips(WorkerWire.Operation operation)throws Exception{
        var r=new WorkerWire.Request(UUID.randomUUID(),operation,"900001");var bytes=new ByteArrayOutputStream();
        var values=new LinkedHashMap<String,String>();values.put("Titel","Ä & < 漫画");values.put("Preis","8,49");values.put("optional",null);
        var calls=new AtomicInteger();
        WorkerImportRunner.Scrapers scrapers=new WorkerImportRunner.Scrapers(){
            Map<String,String> run(WorkerWire.Operation actual,String index){
                assertEquals(operation,actual);assertEquals(r.index(),index);assertEquals(1,calls.incrementAndGet());
                try{
                    var d=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
                    assertEquals(21,d.readInt());assertEquals(WorkerWire.MAGIC,d.readInt());
                    assertEquals(r.ticket().getMostSignificantBits(),d.readLong());assertEquals(r.ticket().getLeastSignificantBits(),d.readLong());
                    assertEquals(operation.ordinal(),d.readUnsignedByte());assertEquals(0,d.available());
                }catch(IOException e){throw new UncheckedIOException(e);}return values;
            }
            public Map<String,String> series(String index){return run(WorkerWire.Operation.SERIES,index);}
            public Map<String,String> volume(String index){return run(WorkerWire.Operation.VOLUME,index);}
        };
        assertTrue(WorkerImportRunner.serve(new ByteArrayInputStream(request(r)),bytes,scrapers));
        var reply=WorkerWire.reply(new ByteArrayInputStream(bytes.toByteArray()),r);
        assertTrue(reply.success());assertEquals(values,reply.values());assertEquals(1,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"exception","null","oversize","bad-unicode"})
    void completedScraperOrPayloadErrorsProduceOnlyOneBoundEmptyFailure(String fault)throws Exception{
        var r=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.SERIES,"1");var bytes=new ByteArrayOutputStream();var calls=new AtomicInteger();
        var scrapers=new WorkerImportRunner.Scrapers(){
            public Map<String,String> series(String index){calls.incrementAndGet();return switch(fault){
                case "null"->null;case "oversize"->Map.of("Titel","SECRET".repeat(256));
                case "bad-unicode"->Map.of("Titel","\uD800");default->throw new IllegalStateException("SECRET");};}
            public Map<String,String> volume(String index){throw new AssertionError("wrong-dispatch");}
        };
        assertFalse(WorkerImportRunner.serve(new ByteArrayInputStream(request(r)),bytes,scrapers));
        var reply=WorkerWire.reply(new ByteArrayInputStream(bytes.toByteArray()),r);
        assertFalse(reply.success());assertTrue(reply.values().isEmpty());assertEquals(1,calls.get());
        assertFalse(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("SECRET"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void malformedOrTrailingRequestsCannotReachEitherScraper(boolean trailing)throws Exception{
        var r=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.VOLUME,"1");byte[] input=trailing?Arrays.copyOf(request(r),request(r).length+1):new byte[]{0,0,0,0};
        var scrapers=new WorkerImportRunner.Scrapers(){
            public Map<String,String> series(String i){fail("unexpected-scraper");return Map.of();}
            public Map<String,String> volume(String i){fail("unexpected-scraper");return Map.of();}
        };var out=new ByteArrayOutputStream();
        assertThrows(IOException.class,()->WorkerImportRunner.serve(new ByteArrayInputStream(input),out,scrapers));assertEquals(0,out.size());
    }
    @Test void resultWriteFailureNeverRetriesThePotentiallyPartialResponse()throws Exception{
        var r=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.VOLUME,"1");var calls=new AtomicInteger();var writes=new AtomicInteger();
        var out=new OutputStream(){public void write(int b)throws IOException{if(writes.incrementAndGet()>25)throw new IOException("synthetic-write-failure");}};
        var scrapers=new WorkerImportRunner.Scrapers(){
            public Map<String,String> series(String i){fail("wrong-dispatch");return Map.of();}
            public Map<String,String> volume(String i){calls.incrementAndGet();return Map.of("Titel","synthetic");}
        };
        assertThrows(IOException.class,()->WorkerImportRunner.serve(new ByteArrayInputStream(request(r)),out,scrapers));
        assertEquals(1,calls.get());assertEquals(26,writes.get());
    }
}
