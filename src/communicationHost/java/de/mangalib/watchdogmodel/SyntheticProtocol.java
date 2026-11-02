package de.mangalib.watchdogmodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;

/**
 * Festes Zweipeer-Kommunikationsprofil, KEIN Ersatz für den Watchdog-Lifecycle.
 * Kein Worker/J/K wird simuliert und aus dem Abschluss wird niemals C abgeleitet.
 */
final class SyntheticProtocol implements AutoCloseable {
    static final String PROFILE = "--synthetic-stdio-v1-end23";
    static final String REQUEST = "synthetic-probe-ä", RESPONSE = "synthetic-ok-ä";
    static final Limits LIMITS = new Limits(160, 160, 64, 4, 2);
    static final int TOTAL_BYTES = 512, FRAGMENT = 7;
    private final boolean host;
    private final Id session;
    private final InputStream input;
    private final OutputStream output;
    private final BufferBudget budget = new BufferBudget(2048);
    private final BufferBudget.Account account = budget.account(2048);
    private final Decoder decoder;
    private long incoming = 1, outgoing = 1;
    private int inputBytes, outputBytes;
    private Type expected;
    private String expectedText;
    private boolean received, ended, outputEnded, closed;

    SyntheticProtocol(boolean host, Id session, InputStream input, OutputStream output) {
        this.host = host; this.session = Objects.requireNonNull(session);
        this.input = Objects.requireNonNull(input); this.output = Objects.requireNonNull(output);
        decoder = new Decoder(LIMITS, account, (frame, bytes) -> {
            if (expected == null || received || !envelope(!host, incoming).equals(frame.envelope())
                    || !(frame.payload() instanceof Control control) || control.type() != expected
                    || !expectedText.equals(control.text())) return false;
            incoming++; received = true; return true;
        });
    }

    Envelope envelope(boolean fromHost, long sequence) {
        return new Envelope(session, new Id(2, 1), new Id(3, fromHost ? 2 : 1),
                new Id(4, fromHost ? 2 : 1), fromHost ? Direction.H_S : Direction.S_H, sequence);
    }

    void send(Type type, String text) throws IOException {
        if (closed || outputEnded) throw new IOException("protocol-closed");
        Frame frame = new Frame(envelope(host, outgoing), new Control(type, text));
        try (Encoder encoder = Encoder.open(frame, LIMITS, account)) {
            if (encoder == null) throw new IOException("protocol-backpressure");
            byte[] fragment = new byte[FRAGMENT];
            while (!encoder.done()) {
                int count = encoder.read(fragment, 0, fragment.length);
                if (count < 1 || count > TOTAL_BYTES - outputBytes) throw new IOException("protocol-budget");
                output.write(fragment, 0, count); outputBytes += count;
            }
            output.flush(); outgoing++; outputEnded = type == Type.CHANNEL_END;
        }
    }

    void receive(Type type, String text) throws IOException {
        if (closed || ended || expected != null) throw new IOException("protocol-phase");
        expected = type; expectedText = text; received = false;
        byte[] fragment = new byte[FRAGMENT];
        while (!received) {
            int count = input.read(fragment);
            if (count < 0) { decoder.eof(); throw new IOException("protocol-early-eof"); }
            if (count == 0) throw new IOException("protocol-no-progress");
            inputBytes += count;
            if (inputBytes > TOTAL_BYTES || decoder.feed(fragment, 0, count) != count || decoder.error() != null)
                throw new IOException("protocol-frame");
        }
        expected = null; expectedText = null;
    }

    void receiveEnd() throws IOException {
        receive(Type.CHANNEL_END, "");
        // Auch ein korrektes CHANNEL_END erlaubt keine zusätzlichen Bytes.
        if (input.read() != -1 || !decoder.eof()) throw new IOException("protocol-end");
        ended = true;
    }

    static void parent(InputStream input, OutputStream output, Id session, Runnable handshake) throws IOException {
        try (SyntheticProtocol p = new SyntheticProtocol(false, session, input, output)) {
            p.send(Type.HELLO, PROFILE); p.receive(Type.READY, PROFILE); handshake.run();
            p.send(Type.START, REQUEST); p.receive(Type.RESULT_AVAILABLE, RESPONSE);
            p.send(Type.CHANNEL_END, ""); output.close(); p.receiveEnd();
        }
    }

    static void host(InputStream input, OutputStream output, Id session) throws IOException {
        try (SyntheticProtocol p = new SyntheticProtocol(true, session, input, output)) {
            p.receive(Type.HELLO, PROFILE); p.send(Type.READY, PROFILE);
            p.receive(Type.START, REQUEST); p.send(Type.RESULT_AVAILABLE, RESPONSE);
            // Ende der Gegenrichtung vor eigenem Abschluss beweisen.
            p.receiveEnd();
            p.send(Type.CHANNEL_END, ""); output.close();
        }
    }

    long heldBytes() { return budget.used(); }
    @Override public void close() { if (!closed) { closed = true; decoder.close(); } }
}
