package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerWireTest {
    final WorkerWire.Request request=new WorkerWire.Request(UUID.randomUUID(),WorkerWire.Operation.SERIES,"900001");
    byte[] reply(Map<String,String> map)throws Exception{
        var bytes=new ByteArrayOutputStream();WorkerWire.reply(bytes,request,new WorkerWire.Reply(true,map));return bytes.toByteArray();
    }
    @Test void preservesUnicodePricesNullAndLegacyMapFieldNames()throws Exception{
        var values=new LinkedHashMap<String,String>();values.put("titel","Ä & <script> ' 漫画");values.put("preis","8,49");values.put("optional",null);
        assertEquals(values,WorkerWire.reply(new ByteArrayInputStream(reply(values)),request).values());
    }
    @Test void syntheticWorkerUsesTheActualBoundMemoryProtocol()throws Exception{
        var input=new ByteArrayOutputStream();WorkerWire.request(input,request);var output=new ByteArrayOutputStream();
        SyntheticWorkerMain.serve(new ByteArrayInputStream(input.toByteArray()),output);
        assertEquals(Map.of("synthetic","true"),WorkerWire.reply(new ByteArrayInputStream(output.toByteArray()),request).values());
    }
    @ParameterizedTest @ValueSource(ints={-1,0,4097,Integer.MAX_VALUE})
    void rejectsDeclaredRequestLengthBeforeAllocation(int length){
        assertThrows(IOException.class,()->WorkerWire.request(new ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array())));
    }
    @ParameterizedTest @ValueSource(strings={"truncated","wrong-ticket","wrong-operation","second-frame","wrong-end","malformed-utf8"})
    void rejectsCorruptOrUnboundResponse(String kind)throws Exception{
        byte[] bytes=reply(Map.of("key","value"));
        switch(kind){
            case "truncated"->bytes=Arrays.copyOf(bytes,bytes.length-2);
            case "wrong-ticket"->bytes[9]^=1;
            case "wrong-operation"->bytes[24]=1;
            case "second-frame"->{byte[] longer=Arrays.copyOf(bytes,bytes.length+1);longer[longer.length-1]=1;bytes=longer;}
            case "wrong-end"->bytes[bytes.length-1]^=1;
            default-> {int i=bytes.length-5;bytes[i]=(byte)0xff;}
        }
        byte[] data=bytes;assertThrows(IOException.class,()->WorkerWire.reply(new ByteArrayInputStream(data),request));
    }
    @Test void actualStringAndEntryLimitsRemainBounded()throws Exception{
        assertDoesNotThrow(()->reply(Map.of("k","😀".repeat(255))));
        assertThrows(IOException.class,()->reply(Map.of("k","😀".repeat(256))));
        assertThrows(IOException.class,()->reply(Map.of("k","\ud800")));
        assertThrows(IOException.class,()->reply(Map.of("x".repeat(256),"v")));
        var tooMany=new LinkedHashMap<String,String>();for(int i=0;i<=WorkerWire.ENTRIES;i++)tooMany.put("k"+i,"v");
        assertThrows(IOException.class,()->reply(tooMany));
    }
    @Test void declaredHugeResultIsRejectedBeforeAllocatingItsPayload()throws Exception{
        byte[] valid=reply(Map.of());byte[] data=Arrays.copyOf(valid,29);ByteBuffer.wrap(data).putInt(25,Integer.MAX_VALUE);
        assertThrows(IOException.class,()->WorkerWire.reply(new ByteArrayInputStream(data),request));
    }
    @Test void duplicateKeysCannotSilentlyOverwriteAField()throws Exception{
        byte[] hello=Arrays.copyOf(reply(Map.of()),25);var body=new ByteArrayOutputStream();var d=new DataOutputStream(body);
        d.writeInt(WorkerWire.MAGIC);d.writeLong(request.ticket().getMostSignificantBits());d.writeLong(request.ticket().getLeastSignificantBits());
        d.writeByte(request.operation().ordinal());d.writeByte(1);d.writeInt(2);
        for(int i=0;i<2;i++){d.writeInt(1);d.writeByte('a');d.writeInt(1);d.writeByte('v');}
        var wire=new ByteArrayOutputStream();wire.write(hello);var out=new DataOutputStream(wire);out.writeInt(body.size());wire.write(body.toByteArray());out.writeInt(WorkerWire.END);
        assertThrows(IOException.class,()->WorkerWire.reply(new ByteArrayInputStream(wire.toByteArray()),request));
    }
    @Test void nativeWiringIsColdAndHasNoCustodianOrFallback()throws Exception{
        String source=Files.readString(Path.of("src/workerOwnerNative/java/de/mangalib/windowsadapter/SimpleWorkerNative.java"));
        assertTrue(source.contains("new Win64ProcessKernelCalls(api,spec,clock,pipes,authorization)"));
        assertFalse(source.contains("ProcessBuilder"));assertFalse(source.contains("Native.load"));assertFalse(source.contains("ScCustodian"));
        String pipes=Files.readString(Path.of("src/workerOwnerNative/java/de/mangalib/windowsadapter/WorkerNativePipes.java"));
        assertTrue(pipes.contains("new Memory(24)"));assertFalse(pipes.contains("DuplicateHandle"));
        String launch=Files.readString(Path.of("src/jobAdapter/java/de/mangalib/windowsadapter/Win64ProcessKernelCalls.java"));
        assertTrue(launch.contains("JOB_LIST"));assertFalse(launch.contains("AssignProcessToJobObject"));
        // Ergänzende Quellenassertions sind kein nativer Laufzeitnachweis.
    }
}
