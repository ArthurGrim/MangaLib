package de.mangalib.watchdogmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Nur endliche Bytearrays, keine Prozesse, Threads, Pipe- oder Netzwerkverbindungen. */
class SyntheticProtocolTest {
    private static final Id SESSION = new Id(11, 12);
    private static byte[] wire(boolean fromHost, long sequence, Type type, String text) throws Exception {
        try (var protocol = new SyntheticProtocol(!fromHost, SESSION, new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())) {
            return encode(new Frame(protocol.envelope(fromHost, sequence), new Control(type, text)));
        }
    }
    private static byte[] encode(Frame frame) {
        BufferBudget budget = new BufferBudget(2048);
        try (Encoder encoder = Encoder.open(frame, SyntheticProtocol.LIMITS, budget.account(2048))) {
            assertNotNull(encoder); byte[] bytes = new byte[160]; int count = encoder.read(bytes, 0, bytes.length);
            assertTrue(encoder.done()); return Arrays.copyOf(bytes, count);
        } finally { assertEquals(0, budget.used()); }
    }
    private static byte[] concat(byte[]... parts) throws IOException {
        var out = new ByteArrayOutputStream(); for (byte[] part : parts) out.write(part); return out.toByteArray();
    }
    private static ByteArrayInputStream fragments(byte[] bytes, int maximum) {
        return new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] target, int offset, int count) {
                return super.read(target, offset, Math.min(maximum, count));
            }
        };
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 7, 160})
    void completeParentHandshakeResultAndEndWithFragmentedInputAndOutput(int fragment) throws Exception {
        byte[] input = concat(wire(true, 1, Type.READY, SyntheticProtocol.PROFILE),
                wire(true, 2, Type.RESULT_AVAILABLE, SyntheticProtocol.RESPONSE), wire(true, 3, Type.CHANNEL_END, ""));
        var output = new ByteArrayOutputStream(); int[] hello = {0};
        SyntheticProtocol.parent(fragments(input, fragment), output, SESSION, () -> hello[0]++);
        assertEquals(1, hello[0]); assertArrayEquals(concat(wire(false, 1, Type.HELLO, SyntheticProtocol.PROFILE),
                wire(false, 2, Type.START, SyntheticProtocol.REQUEST), wire(false, 3, Type.CHANNEL_END, "")), output.toByteArray());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 7, 160})
    void completeHostSupportsExactlyOneFixedMessageAndWaitsForParentEof(int fragment) throws Exception {
        byte[] input = concat(wire(false, 1, Type.HELLO, SyntheticProtocol.PROFILE),
                wire(false, 2, Type.START, SyntheticProtocol.REQUEST), wire(false, 3, Type.CHANNEL_END, ""));
        var output = new ByteArrayOutputStream();
        SyntheticProtocol.host(fragments(input, fragment), output, SESSION);
        assertArrayEquals(concat(wire(true, 1, Type.READY, SyntheticProtocol.PROFILE),
                wire(true, 2, Type.RESULT_AVAILABLE, SyntheticProtocol.RESPONSE), wire(true, 3, Type.CHANNEL_END, "")), output.toByteArray());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void wrongVersionTicketChannelSenderSessionDirectionOrSequenceIsTerminal(int wrong) throws Exception {
        byte[] bytes = wire(true, 1, Type.READY, SyntheticProtocol.PROFILE);
        int offset = switch (wrong) { case 0 -> 5; case 1 -> 47; case 2 -> 63; case 3 -> 79;
            case 4 -> 31; case 5 -> 88; default -> 23; };
        bytes[offset] ^= 7;
        try (var protocol = new SyntheticProtocol(false, SESSION, fragments(bytes, 1), new ByteArrayOutputStream())) {
            assertThrows(IOException.class, () -> protocol.receive(Type.READY, SyntheticProtocol.PROFILE));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 95, 96, 97})
    void eofDuringHeaderOrPayloadCannotCompleteHandshake(int length) throws Exception {
        byte[] frame = wire(true, 1, Type.READY, SyntheticProtocol.PROFILE);
        try (var protocol = new SyntheticProtocol(false, SESSION, fragments(Arrays.copyOf(frame, length), 2), new ByteArrayOutputStream())) {
            assertThrows(IOException.class, () -> protocol.receive(Type.READY, SyntheticProtocol.PROFILE));
        }
    }

    @Test void lateOrDuplicateFramesCannotReplaceTheExpectedResult() throws Exception {
        byte[] ready = wire(true, 1, Type.READY, SyntheticProtocol.PROFILE);
        for (byte[] duplicate : new byte[][]{ready, wire(true, 2, Type.READY, SyntheticProtocol.PROFILE)}) {
            byte[] input = concat(ready, duplicate);
            try (var protocol = new SyntheticProtocol(false, SESSION, fragments(input, 1), new ByteArrayOutputStream())) {
                protocol.receive(Type.READY, SyntheticProtocol.PROFILE);
                assertThrows(IOException.class, () -> protocol.receive(Type.RESULT_AVAILABLE, SyntheticProtocol.RESPONSE));
            }
        }
    }

    @Test void eofWithoutMarkerOrExtraByteAfterMarkerIsRejected() throws Exception {
        for (byte[] input : new byte[][]{new byte[0], concat(wire(true, 1, Type.CHANNEL_END, ""), new byte[]{1})}) {
            try (var protocol = new SyntheticProtocol(false, SESSION, fragments(input, 2), new ByteArrayOutputStream())) {
                assertThrows(IOException.class, protocol::receiveEnd);
            }
        }
    }

    @Test void dataOrUnexpectedTextCannotTriggerAnyGeneralOperation() throws Exception {
        for (byte[] input : new byte[][]{wire(true, 1, Type.READY, "other-profile"),
                encode(new Frame(new Envelope(SESSION, new Id(2, 1), new Id(3, 2), new Id(4, 2), Direction.H_S, 1), new Data("x")))}) {
            try (var protocol = new SyntheticProtocol(false, SESSION, fragments(input, 1), new ByteArrayOutputStream())) {
                assertThrows(IOException.class, () -> protocol.receive(Type.READY, SyntheticProtocol.PROFILE));
            }
        }
    }

    @Test void oversizedInputIsRejectedAtHeaderWithoutReadingPayload() throws Exception {
        byte[] input = wire(true, 1, Type.READY, SyntheticProtocol.PROFILE);
        java.nio.ByteBuffer.wrap(input).putLong(8, Long.MAX_VALUE);
        var stream = fragments(input, 1);
        try (var protocol = new SyntheticProtocol(false, SESSION, stream, new ByteArrayOutputStream())) {
            assertThrows(IOException.class, () -> protocol.receive(Type.READY, SyntheticProtocol.PROFILE));
            assertEquals(input.length - HEADER, stream.available());
        }
    }

    @Test void outputIsFragmentedBoundedAndClosedProtocolCannotSendAgain() throws Exception {
        int[] largest = {0};
        var output = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] bytes, int offset, int count) {
                largest[0] = Math.max(largest[0], count); super.write(bytes, offset, count);
            }
        };
        var protocol = new SyntheticProtocol(false, SESSION, new ByteArrayInputStream(new byte[0]), output);
        assertThrows(IllegalArgumentException.class, () -> protocol.send(Type.START, "x".repeat(65)));
        protocol.send(Type.CHANNEL_END, ""); assertTrue(largest[0] <= 7);
        assertThrows(IOException.class, () -> protocol.send(Type.HELLO, ""));
        protocol.close(); protocol.close(); assertEquals(0, protocol.heldBytes());
    }

    @Test void exhaustedCodecBudgetRejectsSendingWithoutQueueOrReservationGrowth() {
        BufferBudget budget = new BufferBudget(1024); var account = budget.account(1024);
        Frame frame = new Frame(new Envelope(SESSION, new Id(2, 1), new Id(3, 1), new Id(4, 1), Direction.S_H, 1),
                new Control(Type.HELLO, SyntheticProtocol.PROFILE));
        try (var held = account.reserve(1024)) {
            assertNotNull(held);
            for (int i = 0; i < 20; i++) {
                assertNull(Encoder.open(frame, SyntheticProtocol.LIMITS, account)); assertEquals(1024, budget.used());
            }
        }
        assertEquals(0, budget.used());
        try (var encoder = Encoder.open(frame, SyntheticProtocol.LIMITS, account)) { assertNotNull(encoder); }
        assertEquals(0, budget.used());
    }
}
