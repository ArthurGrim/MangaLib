package de.mangalib.watchdogmodel;

import java.util.Objects;
import java.util.function.Consumer;
import de.mangalib.watchdogmodel.BinaryIpcCodec.Error;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;

/**
 * Ein gerichteter In-Memory-Kanal. Nur explizite writeStep/readStep bewegen Bytes;
 * kein Thread, Timer, OS-Kanal oder eigener Lifecycleautomat. Höchstens ein Sender
 * plus fester Ring. Andere Richtungen können dieselbe globale Reserve verwenden.
 */
final class MemoryIpcLink implements AutoCloseable {
    private final Limits limits;
    private final BufferBudget.Account account;
    private final BufferBudget.Lease ringReservation;
    private final Decoder decoder;
    private final Consumer<Error> onEnd;
    private byte[] ring;
    private Encoder writer;
    private int head, size;
    private boolean writeClosed, closed, notified;
    private Error error;

    // null als Endgrund bedeutet ausschließlich erfolgreiches Decoder-Boundary-EOF.
    MemoryIpcLink(Limits limits, BufferBudget.Account account, int capacity, Sink sink, Consumer<Error> onEnd) {
        this.limits = Objects.requireNonNull(limits); this.account = Objects.requireNonNull(account);
        this.onEnd = Objects.requireNonNull(onEnd);
        if (capacity < 1) throw new IllegalArgumentException("ring");
        ringReservation = account.reserve(capacity);
        if (ringReservation == null) throw new IllegalStateException("ring-budget");
        Decoder created = null;
        try { created = new Decoder(limits, account, sink); ring = new byte[capacity]; }
        catch (RuntimeException | java.lang.Error failure) {
            if (created != null) created.close(); ringReservation.close(); throw failure;
        }
        decoder = created;
    }

    boolean send(Frame frame) {
        if (closed || writeClosed || writer != null) return false;
        writer = Encoder.open(frame, limits, account);
        return writer != null;
    }

    int writeStep(int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("step");
        if (closed || writer == null || maximum == 0) return 0;
        int total = 0;
        while (total < maximum && size < ring.length) {
            int tail = (int) (((long) head + size) % ring.length);
            int available = Math.min(maximum - total, Math.min(ring.length - size, ring.length - tail));
            int written = writer.read(ring, tail, available);
            size += written; total += written;
            if (writer.done()) { writer.close(); writer = null; break; }
            if (written == 0) break;
        }
        return total;
    }

    int readStep(int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("step");
        if (closed) return 0;
        int total = 0;
        while (total < maximum && size > 0 && !closed) {
            int count = Math.min(maximum - total, Math.min(size, ring.length - head));
            int consumed = decoder.feed(ring, head, count);
            head = (int) (((long) head + consumed) % ring.length); size -= consumed; total += consumed;
            if (decoder.error() != null) { error = decoder.error(); close(); break; }
            if (consumed == 0) break;
        }
        if (!closed && writeClosed && writer == null && size == 0) {
            decoder.eof(); error = decoder.error(); close();
        }
        return total;
    }

    /** Explizite synthetische Bytezufuhr für Korruptions-/Fragmenttests, keine Queue. */
    int offerRaw(byte[] bytes, int offset, int count) {
        Objects.checkFromIndexSize(offset, count, bytes.length);
        if (closed || writeClosed || writer != null) return 0;
        int accepted = 0;
        while (accepted < count && size < ring.length) {
            ring[(int) (((long) head + size) % ring.length)] = bytes[offset + accepted++]; size++;
        }
        return accepted;
    }

    void halfClose() { writeClosed = true; }
    boolean sending() { return writer != null; }
    int buffered() { return size; }
    boolean closed() { return closed; }
    Error error() { return error; }

    @Override public void close() {
        if (closed) return;
        closed = writeClosed = true;
        if (writer != null) { writer.close(); writer = null; }
        if (!decoder.ended() && decoder.error() == null) decoder.fail(Error.ABORTED);
        if (error == null) error = decoder.error();
        decoder.close(); ring = null; size = 0; ringReservation.close();
        if (!notified) {
            notified = true;
            try { onEnd.accept(error); } catch (RuntimeException ignored) { if (error == null) error = Error.SINK; }
        }
    }
}
