package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Ein gebundener Request, eine flache String/null-Map, CHANNEL_END und EOF. Kein Objektparser. */
final class WorkerWire {
    static final int REQUEST=4096, RESULT=32*1024*1024, ENTRIES=30_032, TOKEN=4096;
    static final int MAGIC=0x4d4c5731, END=0x454e4431;
    enum Operation { SERIES, VOLUME }
    record Request(UUID ticket,Operation operation,String index) {
        Request {
            Objects.requireNonNull(ticket);Objects.requireNonNull(operation);
            if(ticket.equals(new UUID(0,0)) || index==null || !index.matches("[1-9][0-9]{0,9}")
                    || Long.parseLong(index)>Integer.MAX_VALUE)throw new IllegalArgumentException("worker-request");
        }
    }
    record Reply(boolean success,Map<String,String> values) {
        Reply { values=Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    private WorkerWire(){ }
    static void request(OutputStream out,Request r) throws IOException {
        var bytes=new ByteArrayOutputStream();var d=new DataOutputStream(bytes);
        header(d,r);string(d,r.index());frame(out,bytes.toByteArray(),REQUEST);new DataOutputStream(out).writeInt(END);out.flush();
    }
    static Request request(InputStream in) throws IOException {
        var d=new DataInputStream(new ByteArrayInputStream(frame(in,REQUEST)));UUID id=header(d);
        int operation=d.readUnsignedByte();if(operation>1)throw error();
        String index=string(d,false);if(d.available()!=0)throw error();end(in);
        try{return new Request(id,Operation.values()[operation],index);}catch(RuntimeException e){throw error();}
    }
    static void reply(OutputStream out,Request r,Reply answer) throws IOException {
        byte[] result=encodeReply(r,answer); // Erst validieren, dann den bisherigen Wirevertrag schreiben.
        hello(out,r);result(out,result);
    }
    /** HELLO bestätigt den gebundenen Worker, nicht den Abschluss des langen Imports. */
    static void hello(OutputStream out,Request r)throws IOException{
        var hello=new ByteArrayOutputStream();header(new DataOutputStream(hello),r);frame(out,hello.toByteArray(),REQUEST);out.flush();
    }
    static byte[] encodeReply(Request r,Reply answer)throws IOException{
        if(answer.values().size()>ENTRIES || (!answer.success() && !answer.values().isEmpty()))throw error();
        long size=4+16+1+1+4;
        for(var e:answer.values().entrySet()){
            validate(e.getKey(),e.getValue());size+=8L+utf8(e.getKey()).length+(e.getValue()==null?0:utf8(e.getValue()).length);
            if(size>RESULT)throw error();
        }
        var bytes=new ByteArrayOutputStream((int)size);var d=new DataOutputStream(bytes);
        header(d,r);d.writeBoolean(answer.success());d.writeInt(answer.values().size());
        for(var e:answer.values().entrySet()){string(d,e.getKey());string(d,e.getValue());}
        return bytes.toByteArray();
    }
    static void result(OutputStream out,byte[] encoded)throws IOException{
        frame(out,encoded,RESULT);new DataOutputStream(out).writeInt(END);out.flush();
    }
    static Reply reply(InputStream in,Request r) throws IOException {
        return reply(in,r,()->{});
    }
    static Reply reply(InputStream in,Request r,Runnable bound) throws IOException {
        var hello=new DataInputStream(new ByteArrayInputStream(frame(in,REQUEST)));
        if(!header(hello).equals(r.ticket()) || hello.readUnsignedByte()!=r.operation().ordinal() || hello.available()!=0)throw error();
        bound.run();
        var d=new DataInputStream(new ByteArrayInputStream(frame(in,RESULT)));
        if(!header(d).equals(r.ticket()) || d.readUnsignedByte()!=r.operation().ordinal())throw error();
        int status=d.readUnsignedByte();if(status>1)throw error();int count=d.readInt();
        if(count<0 || count>ENTRIES || (status==0 && count!=0))throw error();
        var result=new LinkedHashMap<String,String>();
        for(int i=0;i<count;i++){
            String key=string(d,false),value=string(d,true);validate(key,value);
            if(result.containsKey(key))throw error();result.put(key,value);
        }
        if(d.available()!=0)throw error();end(in);return new Reply(status==1,result);
    }
    private static void header(DataOutputStream d,Request r)throws IOException{
        d.writeInt(MAGIC);d.writeLong(r.ticket().getMostSignificantBits());d.writeLong(r.ticket().getLeastSignificantBits());
        d.writeByte(r.operation().ordinal());
    }
    private static UUID header(DataInputStream d)throws IOException{
        if(d.readInt()!=MAGIC)throw error();UUID id=new UUID(d.readLong(),d.readLong());
        if(id.equals(new UUID(0,0)))throw error();return id;
    }
    private static void validate(String key,String value)throws IOException{
        if(key==null || key.isEmpty() || key.length()>255 || (value!=null && value.codePointCount(0,value.length())>255))throw error();
        if(utf8(key).length>TOKEN || (value!=null && utf8(value).length>TOKEN))throw error();
    }
    private static byte[] utf8(String text)throws IOException{
        try{var b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            byte[] bytes=new byte[b.remaining()];b.get(bytes);return bytes;}catch(CharacterCodingException e){throw error();}
    }
    private static void string(DataOutputStream d,String text)throws IOException{
        if(text==null){d.writeInt(-1);return;}byte[] bytes=utf8(text);if(bytes.length>TOKEN)throw error();d.writeInt(bytes.length);d.write(bytes);
    }
    private static String string(DataInputStream d,boolean nullable)throws IOException{
        int size=d.readInt();if(nullable && size==-1)return null;if(size<0 || size>TOKEN || size>d.available())throw error();
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(d.readNBytes(size))).toString();}catch(CharacterCodingException e){throw error();}
    }
    private static void frame(OutputStream out,byte[] bytes,int max)throws IOException{
        if(bytes.length<1 || bytes.length>max)throw error();var d=new DataOutputStream(out);d.writeInt(bytes.length);d.write(bytes);
    }
    private static byte[] frame(InputStream in,int max)throws IOException{
        var d=new DataInputStream(in);int size=d.readInt();if(size<1 || size>max)throw error();
        byte[] bytes=d.readNBytes(size);if(bytes.length!=size)throw error();return bytes;
    }
    private static void end(InputStream in)throws IOException{
        if(new DataInputStream(in).readInt()!=END || in.read()!=-1)throw error();
    }
    private static IOException error(){return new IOException("worker-protocol");}
}
