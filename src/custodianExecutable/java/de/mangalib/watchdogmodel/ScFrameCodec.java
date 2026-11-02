package de.mangalib.watchdogmodel;

import java.util.UUID;
import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;

/** Enges S/C-Profil auf dem vorhandenen Codec. Keine neue H-/Workerrolle. */
public final class ScFrameCodec {
    public static final int MAX_FRAME = 512;
    private static final Limits LIMITS = new Limits(MAX_FRAME, MAX_FRAME, 64, 4, 2);
    private ScFrameCodec() { }
    private static Id id(UUID id) { return new Id(id.getMostSignificantBits(), id.getLeastSignificantBits()); }
    private static Envelope envelope(UUID session, UUID ticket, UUID channel, boolean custodian, long sequence) {
        // Die Richtungsbytes des alten Codecs sind nur Transportcodes dieses separaten SC1-Profils.
        return new Envelope(id(session), id(ticket), id(channel), new Id(0, custodian ? 2 : 1),
                custodian ? Direction.H_S : Direction.S_H, sequence);
    }
    public static byte[] encode(UUID session, UUID ticket, UUID channel, boolean custodian, long sequence, String text) {
        return encode(session,ticket,channel,custodian,sequence,text,Type.STATUS);
    }
    public static byte[] encodeEnd(UUID session,UUID ticket,UUID channel,boolean custodian,long sequence) {
        return encode(session,ticket,channel,custodian,sequence,"",Type.CHANNEL_END);
    }
    private static byte[] encode(UUID session,UUID ticket,UUID channel,boolean custodian,long sequence,String text,Type type) {
        var account = new BufferBudget(4096).account(4096);
        try (Encoder encoder = Encoder.open(new Frame(envelope(session,ticket,channel,custodian,sequence),
                new Control(type,text)),LIMITS,account)) {
            if (encoder == null) throw new IllegalStateException("sc-budget");
            byte[] buffer = new byte[MAX_FRAME];
            int length = encoder.read(buffer,0,buffer.length);
            if (!encoder.done()) throw new IllegalArgumentException("sc-frame-limit");
            return java.util.Arrays.copyOf(buffer,length);
        }
    }
    public static String decode(byte[] bytes, UUID session, UUID ticket, UUID channel, boolean custodian, long sequence) {
        return decode(bytes,session,ticket,channel,custodian,sequence,Type.STATUS);
    }
    public static void decodeEnd(byte[] bytes,UUID session,UUID ticket,UUID channel,boolean custodian,long sequence) {
        if(!decode(bytes,session,ticket,channel,custodian,sequence,Type.CHANNEL_END).isEmpty())throw new IllegalArgumentException("sc-end");
    }
    private static String decode(byte[] bytes,UUID session,UUID ticket,UUID channel,boolean custodian,long sequence,Type type) {
        if (bytes.length > MAX_FRAME) throw new IllegalArgumentException("sc-frame-limit");
        String[] value = new String[1];
        var account = new BufferBudget(4096).account(4096);
        try (Decoder decoder = new Decoder(LIMITS,account,(frame,size) -> {
            if (value[0] != null || !envelope(session,ticket,channel,custodian,sequence).equals(frame.envelope())
                    || !(frame.payload() instanceof Control c) || c.type() != type) return false;
            value[0] = c.text(); return true;
        })) {
            if (decoder.feed(bytes,0,bytes.length) != bytes.length || decoder.error() != null || !decoder.eof() || value[0] == null)
                throw new IllegalArgumentException("sc-frame-invalid");
            return value[0];
        }
    }
}
