package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import de.mangalib.watchdogmodel.ScFrameCodec;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Festes natives SCN1-Profil auf den exklusiven Startpipes. Keine Diagnose von Nutzdaten/Handles. */
final class ScNativeWire {
    record Boot(ScChannel.Binding binding,Identity owner,Identity child,long ownerHandle) { }
    private final ScChannel.Binding binding;
    private final boolean child;
    private final InputStream input;
    private final OutputStream output;
    private long incoming=1,outgoing=1;
    private int readBytes,writeBytes;
    private volatile boolean failed;
    private boolean reading,writing,inputEnded,outputEnded;
    ScNativeWire(ScChannel.Binding binding,boolean child,InputStream input,OutputStream output) {
        this.binding=binding;this.child=child;this.input=input;this.output=output;
    }
    void send(String opcode,String payload) throws IOException {
        if(!allowed(opcode,child))throw fail();
        write(ScFrameCodec.encode(binding.intent().session(),binding.intent().ticket(),binding.channel(),child,next(false),prefix(opcode)+payload));
    }
    String receive(String opcode) throws IOException {
        if(!allowed(opcode,!child))throw fail();
        long seq=next(true);
        String text;
        try{text=ScFrameCodec.decode(read(),binding.intent().session(),binding.intent().ticket(),binding.channel(),!child,seq);}
        catch(RuntimeException error){throw fail();}
        String prefix=prefix(opcode);
        if(failed || !text.startsWith(prefix))throw fail();return text.substring(prefix.length());
    }
    void finishOutput() throws IOException {
        write(ScFrameCodec.encodeEnd(binding.intent().session(),binding.intent().ticket(),binding.channel(),child,next(false)));
        outputEnded=true;output.close();
    }
    void finishInput() throws IOException {
        long seq=next(true);
        try{ScFrameCodec.decodeEnd(read(),binding.intent().session(),binding.intent().ticket(),binding.channel(),!child,seq);}
        catch(RuntimeException e){throw fail();}
        if(input.read()!=-1 || failed)throw fail();inputEnded=true;
    }
    private synchronized long next(boolean read) throws IOException {
        if(failed || (read?inputEnded:outputEnded) || (read?incoming:outgoing)>6)throw fail();
        return read?incoming++:outgoing++;
    }
    private byte[] read() throws IOException {
        synchronized(this){if(reading || failed)throw fail();reading=true;}
        try {
            int size=new DataInputStream(input).readInt();if(size<1 || size>512 || readBytes+size+4>2048)throw fail();
            byte[] data=input.readNBytes(size);if(data.length!=size)throw fail();readBytes+=size+4;return data;
        } catch(IOException e){failed=true;throw new IOException("sc-native-wire");}
        finally{synchronized(this){reading=false;}}
    }
    private void write(byte[] bytes) throws IOException {
        synchronized(this){if(writing || failed || writeBytes+bytes.length+4>2048)throw fail();writing=true;}
        try{new DataOutputStream(output).writeInt(bytes.length);output.write(bytes);output.flush();writeBytes+=bytes.length+4;}
        catch(IOException e){failed=true;throw new IOException("sc-native-wire");}
        finally{synchronized(this){writing=false;}}
    }
    private static boolean allowed(String op,boolean fromC) {
        return (fromC?Set.of("BOUND","ACK","GROUP_END","CLOSED"):Set.of("GRANT","STOP","FENCE")).contains(op);
    }
    private String prefix(String opcode){return "SCN1|"+binding.intent().group()+"|"+binding.anchor().hash()+"|"+opcode+"|";}
    private IOException fail(){failed=true;return new IOException("sc-native-wire");}
    boolean failed(){return failed;}
    synchronized boolean outstanding(){return reading || writing;}
    static void writeBoot(OutputStream output,Boot boot) throws IOException {
        var data=new ByteArrayOutputStream();var d=new DataOutputStream(data);d.writeInt(0x53434e31);d.writeLong(boot.ownerHandle());
        for(UUID id:List.of(boot.binding().intent().ticket(),boot.binding().intent().session(),boot.binding().intent().group(),boot.binding().channel())){
            d.writeLong(id.getMostSignificantBits());d.writeLong(id.getLeastSignificantBits());
        }
        d.writeLong(boot.binding().anchor().revision());d.write(HexFormat.of().parseHex(boot.binding().anchor().hash()));
        for(Identity id:List.of(boot.owner(),boot.child())){
            byte[] image=id.image().getBytes(StandardCharsets.UTF_8);if(image.length<1 || image.length>512)throw new IOException("sc-boot-image");
            d.writeLong(id.pid());d.writeLong(id.creationTime());d.writeShort(image.length);d.write(image);
        }
        byte[] bytes=data.toByteArray();if(bytes.length>1536)throw new IOException("sc-boot-limit");
        new DataOutputStream(output).writeInt(bytes.length);output.write(bytes);output.flush();
    }
    static Boot readBoot(InputStream input) throws IOException {
        var source=new DataInputStream(input);int length=source.readInt();if(length<160 || length>1536)throw new IOException("sc-boot-limit");
        byte[] bytes=source.readNBytes(length);if(bytes.length!=length)throw new IOException("sc-boot-eof");
        var d=new DataInputStream(new ByteArrayInputStream(bytes));if(d.readInt()!=0x53434e31)throw new IOException("sc-boot-profile");
        long owner=d.readLong();UUID[] ids=new UUID[4];for(int i=0;i<4;i++)ids[i]=new UUID(d.readLong(),d.readLong());
        long revision=d.readLong();byte[] hash=d.readNBytes(32);Identity[] processes=new Identity[2];
        for(int i=0;i<2;i++){
            long pid=d.readLong(),birth=d.readLong();int size=d.readUnsignedShort();if(pid<=0 || birth<=0 || size<1 || size>512)throw new IOException("sc-boot-identity");
            byte[] image=d.readNBytes(size);if(image.length!=size)throw new IOException("sc-boot-image");
            String text=new String(image,StandardCharsets.UTF_8);if(!Arrays.equals(image,text.getBytes(StandardCharsets.UTF_8)))throw new IOException("sc-boot-utf8");
            processes[i]=new Identity(pid,birth,text);
        }
        if(d.available()!=0 || owner==0 || owner==-1 || processes[0].pid()==processes[1].pid())throw new IOException("sc-boot-extra");
        try{return new Boot(new ScChannel.Binding(new StartIntentStore.Binding(ids[0],ids[1],ids[2]),new StartIntentStore.Anchor(revision,HexFormat.of().formatHex(hash)),ids[3]),processes[0],processes[1],owner);}
        catch(RuntimeException e){throw new IOException("sc-boot-binding");}
    }
}
