package de.mangalib.watchdogmodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static de.mangalib.watchdogmodel.WatchdogModel.*;

/**
 * Prozessinterner Wire-Entwurf. Keine Streams, Dateisysteme, Sockets oder nativen
 * Kanäle. Datenbytes gehen vorläufig an einen expliziten Streamempfänger;
 * erst accept bestätigt den vollständigen Frame. Noch kein Importmap-Parser.
 */
final class BinaryIpcCodec {
    static final int HEADER = 96, MAGIC = 0x4d4c4950, VERSION = 1;
    // V1-Abschlussprofil: Code 23, leere Payload. Alte V1-Peers lehnen ihn ab;
    // ein späterer Host muss gleiche Profile verlangen (keine Mischkompatibilität).
    static final int BEGIN = 20, DATA = 21, GROUP = 22, CHANNEL_END = 23;
    enum Error { MAGIC, VERSION, TYPE, LENGTH, HEADER, PAYLOAD, UTF8, TRUNCATED,
        DELIVERY, SINK, ABORTED }
    record Id(long high, long low) {
        Id { if (high == 0 && low == 0) throw new IllegalArgumentException("zero-id"); }
    }
    record Envelope(Id session, Id ticket, Id channel, Id sender, Direction direction, long sequence) {
        Envelope {
            Objects.requireNonNull(session); Objects.requireNonNull(ticket); Objects.requireNonNull(channel);
            Objects.requireNonNull(sender); Objects.requireNonNull(direction);
            if (sequence < 1) throw new IllegalArgumentException("sequence");
        }
    }
    sealed interface Payload permits Control, Begin, Data, DataCount, GroupProof { }
    record Control(Type type, String text) implements Payload {
        Control { Objects.requireNonNull(type); Objects.requireNonNull(text); }
    }
    record Begin(long bytes, int fields, int depth) implements Payload { }
    record Data(String text) implements Payload { Data { Objects.requireNonNull(text); } }
    /** Nur Decoder-Ergebnis, niemals ein kodierbarer Ersatz für fehlende Bytes. */
    record DataCount(long bytes) implements Payload { }
    record GroupProof(Id group, long revision, boolean complete, boolean stable, int active, int accounting) implements Payload { }
    record Frame(Envelope envelope, Payload payload) {
        Frame { Objects.requireNonNull(envelope); Objects.requireNonNull(payload); }
    }
    record Limits(int controlFrame, int dataFrame, long resultBytes, int fields, int depth) {
        Limits {
            if (controlFrame < HEADER + 37 || dataFrame < HEADER + 1 || resultBytes < 1 || fields < 1 || depth < 1)
                throw new IllegalArgumentException("limits");
        }
        static Limits candidates() { return new Limits(4096, 65536, 32L * 1024 * 1024, 30_032, 4); }
        long decoderReservation() { return HEADER + 3L * (controlFrame - HEADER); }
    }
    interface Sink {
        boolean accept(Frame frame, long wireBytes);
        /** Kein stilles Verwerfen: Ohne expliziten Empfänger sind Datenframes verboten. */
        default boolean dataByte(Envelope envelope, int unsignedByte) { return false; }
        /** Vorläufige Bytes dürfen erst nach accept fachlich übernommen werden. */
        default void discard(Envelope envelope) { }
    }

    /** Gültiges UTF-16 zählen, bevor irgendeine längenabhängige Allokation erfolgt. */
    static long utf8Length(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("utf16");
                bytes += 4;
            } else {
                if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("utf16");
                bytes += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
            }
        }
        return bytes;
    }

    private static int signalCode(Type type) {
        return switch (type) {
            case HELLO -> 1; case READY -> 2; case START -> 3; case STARTED -> 4;
            case STATUS -> 5; case LEASE -> 6; case STOP -> 7; case STOP_ACCEPTED -> 8;
            case RESULT_END -> 9; case RESULT_AVAILABLE -> 10; case RESOURCES_CLOSED -> 11; case ERROR -> 12;
            case CHANNEL_END -> CHANNEL_END;
        };
    }
    private static Type signalType(int code) {
        return switch (code) {
            case 1 -> Type.HELLO; case 2 -> Type.READY; case 3 -> Type.START; case 4 -> Type.STARTED;
            case 5 -> Type.STATUS; case 6 -> Type.LEASE; case 7 -> Type.STOP; case 8 -> Type.STOP_ACCEPTED;
            case 9 -> Type.RESULT_END; case 10 -> Type.RESULT_AVAILABLE; case 11 -> Type.RESOURCES_CLOSED; case 12 -> Type.ERROR;
            case CHANNEL_END -> Type.CHANNEL_END;
            default -> throw new IllegalArgumentException("type");
        };
    }
    private static int directionCode(Direction direction) {
        return switch (direction) { case S_H -> 1; case H_S -> 2; case H_W -> 3; case W_H -> 4; };
    }
    private static Direction direction(int code) {
        return switch (code) { case 1 -> Direction.S_H; case 2 -> Direction.H_S; case 3 -> Direction.H_W; case 4 -> Direction.W_H;
            default -> throw new IllegalArgumentException("direction"); };
    }
    private static void id(ByteBuffer buffer, Id id) { buffer.putLong(id.high()).putLong(id.low()); }
    private static Id id(ByteBuffer buffer) { return new Id(buffer.getLong(), buffer.getLong()); }

    static final class Encoder implements AutoCloseable {
        private final BufferBudget.Lease reservation;
        private byte[] header, fixed;
        private String text;
        private int headerAt, fixedAt, charAt, utfBytes;
        private long utfBits;

        private Encoder(BufferBudget.Lease reservation, Frame frame, int type, long bytes, String text, int fixedSize) {
            this.reservation = reservation; this.text = text;
            header = new byte[HEADER]; fixed = new byte[fixedSize];
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            h.putInt(MAGIC).putShort((short) VERSION).putShort((short) type).putLong(bytes).putLong(frame.envelope().sequence());
            id(h, frame.envelope().session()); id(h, frame.envelope().ticket());
            id(h, frame.envelope().channel()); id(h, frame.envelope().sender());
            h.put((byte) directionCode(frame.envelope().direction())); // Rest ist reserviert und null.
            ByteBuffer p = ByteBuffer.wrap(fixed).order(ByteOrder.BIG_ENDIAN);
            if (frame.payload() instanceof Begin b) p.putLong(b.bytes()).putInt(b.fields()).putInt(b.depth());
            if (frame.payload() instanceof GroupProof g) {
                id(p, g.group()); p.putLong(g.revision()).put((byte) ((g.complete() ? 1 : 0) | (g.stable() ? 2 : 0)));
                p.putInt(g.active()).putInt(g.accounting()).putInt(0); // GROUP_END behauptet leere Mitgliedsliste.
            }
        }

        /** null = Backpressure, keine Warteschlange/Allokation trotz fehlender Reserve. */
        static Encoder open(Frame frame, Limits limits, BufferBudget.Account account) {
            String text = ""; int type, fixed = 0; long bytes;
            if (frame.payload() instanceof Control c) {
                type = signalCode(c.type()); text = c.text(); bytes = utf8Length(text);
                if (type == CHANNEL_END && bytes != 0) throw new IllegalArgumentException("channel-end-payload");
            }
            else if (frame.payload() instanceof Begin b) {
                validateBegin(b, limits); type = BEGIN; fixed = 16; bytes = fixed;
            } else if (frame.payload() instanceof Data d) {
                text = d.text(); type = DATA; bytes = utf8Length(text);
                if (bytes == 0) throw new IllegalArgumentException("empty-data");
            } else if (frame.payload() instanceof GroupProof g) {
                validateGroup(g); type = GROUP; fixed = 37; bytes = fixed;
            } else throw new IllegalArgumentException("decode-only-payload");
            if (bytes > (type == DATA ? limits.dataFrame() : limits.controlFrame()) - (long) HEADER)
                throw new IllegalArgumentException("length");
            long held = HEADER + (long) fixed + 2L * text.length();
            BufferBudget.Lease lease = account.reserve(held);
            if (lease == null) return null;
            try { return new Encoder(lease, frame, type, bytes, text, fixed); }
            catch (RuntimeException | java.lang.Error failure) { lease.close(); throw failure; }
        }

        int read(byte[] target, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, target.length);
            if (header == null) return 0;
            int written = 0;
            while (written < length && !done()) {
                int value;
                if (headerAt < header.length) value = header[headerAt++] & 255;
                else if (fixedAt < fixed.length) value = fixed[fixedAt++] & 255;
                else {
                    if (utfBytes == 0) {
                        int cp = text.codePointAt(charAt); charAt += Character.charCount(cp);
                        if (cp < 0x80) { utfBits = cp; utfBytes = 1; }
                        else if (cp < 0x800) { utfBits = ((0xc0 | cp >> 6) << 8) | (0x80 | cp & 63); utfBytes = 2; }
                        else if (cp < 0x10000) { utfBits = ((long) (0xe0 | cp >> 12) << 16) | ((0x80 | cp >> 6 & 63) << 8) | (0x80 | cp & 63); utfBytes = 3; }
                        else { utfBits = ((long) (0xf0 | cp >> 18) << 24) | ((long) (0x80 | cp >> 12 & 63) << 16) | ((0x80 | cp >> 6 & 63) << 8) | (0x80 | cp & 63); utfBytes = 4; }
                    }
                    value = (int) (utfBits >> (--utfBytes * 8)) & 255;
                }
                target[offset + written++] = (byte) value;
            }
            return written;
        }
        boolean done() { return header == null || headerAt == header.length && fixedAt == fixed.length && charAt == text.length() && utfBytes == 0; }
        @Override public void close() { header = null; fixed = null; text = null; reservation.close(); }
    }

    /** Zustandsprüfung ohne Ersatzzeichen, auch über beliebige feed-Grenzen. */
    private static final class Utf8 {
        int remaining, min = 0x80, max = 0xbf;
        boolean next(int b) {
            if (remaining != 0) {
                if (b < min || b > max) return false;
                remaining--; min = 0x80; max = 0xbf; return true;
            }
            if (b <= 0x7f) return true;
            if (b >= 0xc2 && b <= 0xdf) remaining = 1;
            else if (b >= 0xe0 && b <= 0xef) { remaining = 2; if (b == 0xe0) min = 0xa0; if (b == 0xed) max = 0x9f; }
            else if (b >= 0xf0 && b <= 0xf4) { remaining = 3; if (b == 0xf0) min = 0x90; if (b == 0xf4) max = 0x8f; }
            else return false;
            return true;
        }
    }

    static final class Decoder implements AutoCloseable {
        private final Limits limits;
        private final Sink sink;
        private final BufferBudget.Lease reservation;
        private byte[] header, control;
        private int headerAt, controlAt, type;
        private long payloadLength, remaining;
        private Envelope envelope;
        private Utf8 utf8;
        private Error error;
        private boolean ended;

        Decoder(Limits limits, BufferBudget.Account account, Sink sink) {
            this.limits = Objects.requireNonNull(limits); this.sink = Objects.requireNonNull(sink);
            reservation = account.reserve(limits.decoderReservation());
            if (reservation == null) throw new IllegalStateException("decoder-budget");
            try { header = new byte[HEADER]; control = new byte[limits.controlFrame() - HEADER]; }
            catch (RuntimeException | java.lang.Error failure) { reservation.close(); throw failure; }
        }

        int feed(byte[] bytes, int offset, int count) {
            Objects.checkFromIndexSize(offset, count, bytes.length);
            int consumed = 0;
            while (consumed < count && !ended && error == null) {
                if (headerAt < HEADER) {
                    header[headerAt++] = bytes[offset + consumed++];
                    if (headerAt == HEADER && !parseHeader()) break;
                    if (headerAt == HEADER && remaining == 0) emit();
                } else {
                    int b = bytes[offset + consumed++] & 255;
                    if ((type == DATA || type <= 12) && !utf8.next(b)) { fail(Error.UTF8); break; }
                    if (type == DATA) {
                        try { if (!sink.dataByte(envelope, b)) { fail(Error.DELIVERY); break; } }
                        catch (RuntimeException failure) { fail(Error.SINK); break; }
                    }
                    if (type != DATA) control[controlAt++] = (byte) b;
                    if (--remaining == 0) emit();
                }
            }
            return consumed;
        }

        private boolean parseHeader() {
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            if (h.getInt() != MAGIC) return fail(Error.MAGIC);
            if (Short.toUnsignedInt(h.getShort()) != VERSION) return fail(Error.VERSION);
            type = Short.toUnsignedInt(h.getShort());
            if (!(type >= 1 && type <= 12 || type == BEGIN || type == DATA || type == GROUP || type == CHANNEL_END)) return fail(Error.TYPE);
            payloadLength = h.getLong(); long sequence = h.getLong();
            int limit = type == DATA ? limits.dataFrame() : limits.controlFrame();
            if (payloadLength < 0 || payloadLength > limit - (long) HEADER) return fail(Error.LENGTH);
            if (type == DATA && payloadLength == 0 || type == BEGIN && payloadLength != 16 || type == GROUP && payloadLength != 37)
                return fail(Error.LENGTH);
            if (type == CHANNEL_END && payloadLength != 0) return fail(Error.LENGTH);
            try {
                Id session = id(h), ticket = id(h), channel = id(h), sender = id(h);
                Direction direction = direction(Byte.toUnsignedInt(h.get()));
                while (h.hasRemaining()) if (h.get() != 0) return fail(Error.HEADER);
                envelope = new Envelope(session, ticket, channel, sender, direction, sequence);
            } catch (IllegalArgumentException failure) { return fail(Error.HEADER); }
            remaining = payloadLength; controlAt = 0; utf8 = new Utf8(); return true;
        }

        private void emit() {
            if (utf8.remaining != 0) { fail(Error.UTF8); return; }
            Payload payload;
            try {
                ByteBuffer p = ByteBuffer.wrap(control).order(ByteOrder.BIG_ENDIAN);
                if (type == DATA) payload = new DataCount(payloadLength);
                else if (type == BEGIN) {
                    Begin b = new Begin(p.getLong(), p.getInt(), p.getInt()); validateBegin(b, limits); payload = b;
                } else if (type == GROUP) {
                    Id group = id(p); long revision = p.getLong(); int flags = Byte.toUnsignedInt(p.get());
                    int active = p.getInt(), accounting = p.getInt(), members = p.getInt();
                    if ((flags & ~3) != 0 || members != 0) throw new IllegalArgumentException("group-fields");
                    GroupProof g = new GroupProof(group, revision, (flags & 1) != 0, (flags & 2) != 0, active, accounting);
                    validateGroup(g); payload = g;
                } else payload = new Control(signalType(type), new String(control, 0, controlAt, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException failure) { fail(Error.PAYLOAD); return; }
            try {
                if (!sink.accept(new Frame(envelope, payload), HEADER + payloadLength)) { fail(Error.DELIVERY); return; }
            } catch (RuntimeException failure) { fail(Error.SINK); return; }
            headerAt = 0; controlAt = 0; envelope = null; utf8 = null;
        }

        boolean eof() {
            if (error != null) return false;
            if (ended) return true;
            if (headerAt != 0) return fail(Error.TRUNCATED);
            ended = true; release(); return true;
        }
        boolean fail(Error reason) {
            if (error == null && !ended) error = reason;
            if (type == DATA && envelope != null) {
                try { sink.discard(envelope); } catch (RuntimeException ignored) { /* Primärfehler bleibt fest. */ }
            }
            release(); return false;
        }
        private void release() { header = null; control = null; envelope = null; utf8 = null; reservation.close(); }
        Error error() { return error; }
        boolean ended() { return ended; }
        @Override public void close() { if (!ended && error == null) fail(Error.ABORTED); else release(); }
    }

    private static void validateBegin(Begin b, Limits limits) {
        if (b.bytes() < 1 || b.bytes() > limits.resultBytes() || b.fields() < 0 || b.fields() > limits.fields()
                || b.depth() < 1 || b.depth() > limits.depth()) throw new IllegalArgumentException("result-budget");
    }
    private static void validateGroup(GroupProof g) {
        if (g.group() == null || g.revision() < 1 || g.active() < 0 || g.accounting() < 0) throw new IllegalArgumentException("group-proof");
    }
}
