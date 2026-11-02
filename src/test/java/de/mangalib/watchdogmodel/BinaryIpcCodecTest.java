package de.mangalib.watchdogmodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static org.junit.jupiter.api.Assertions.*;

/** Kleine injizierte Budgets; kein I/O, keine großen Ergebnisallokationen. */
class BinaryIpcCodecTest {
    static final Limits LIMITS = new Limits(160, 512, 128, 100, 4);
    static final Envelope ENVELOPE = new Envelope(new Id(1, 2), new Id(3, 4), new Id(5, 6),
            new Id(7, 8), WatchdogModel.Direction.S_H, 1);
    static Frame frame(Payload payload) { return new Frame(ENVELOPE, payload); }

    /** Nur begrenzte Testhilfe; der Harness verwendet gerade KEIN vollständiges Framearray. */
    static byte[] encode(Frame frame, Limits limits) {
        BufferBudget budget = new BufferBudget(100_000);
        byte[] bytes = new byte[Math.max(limits.controlFrame(), limits.dataFrame())];
        int count;
        try (Encoder encoder = Encoder.open(frame, limits, budget.account(100_000))) {
            assertNotNull(encoder); count = encoder.read(bytes, 0, bytes.length); assertTrue(encoder.done());
        }
        assertEquals(0, budget.used()); return Arrays.copyOf(bytes, count);
    }
    private static final class Receiver implements AutoCloseable {
        final BufferBudget budget = new BufferBudget(4096);
        final List<Frame> frames = new ArrayList<>();
        final List<Long> sizes = new ArrayList<>();
        final List<Integer> data = new ArrayList<>(); // Nur begrenzte Testbeobachtung, kein Codec-Puffer.
        final Decoder decoder = new Decoder(LIMITS, budget.account(4096), new Sink() {
            public boolean accept(Frame f, long size) { frames.add(f); sizes.add(size); return true; }
            public boolean dataByte(Envelope envelope, int value) { if (data.size() == 512) return false; data.add(value); return true; }
            public void discard(Envelope envelope) { data.clear(); }
        });
        @Override public void close() { decoder.close(); assertEquals(0, budget.used()); }
    }

    @ParameterizedTest @EnumSource(WatchdogModel.Type.class)
    void allExistingSignalCodesRoundTripWithoutOrdinalContract(WatchdogModel.Type type) {
        Frame expected = frame(new Control(type, type == WatchdogModel.Type.CHANNEL_END ? "" : "Ä & 😀")); byte[] bytes = encode(expected, LIMITS);
        try (Receiver receiver = new Receiver()) {
            assertEquals(bytes.length, receiver.decoder.feed(bytes, 0, bytes.length));
            assertEquals(List.of(expected), receiver.frames); assertEquals((long) bytes.length, receiver.sizes.getFirst());
            assertTrue(receiver.decoder.eof());
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 7, 95, 96, 97, 4096})
    void arbitraryFragmentationPreservesHeaderUtf8PayloadAndFollowingFrames(int fragment) {
        Frame a = frame(new Control(WatchdogModel.Type.HELLO, "Ä漢😀"));
        Frame b = frame(new Begin(100, 30, 4)); Frame c = frame(new GroupProof(new Id(9, 10), 7, false, true, 1, 1));
        byte[] aa = encode(a, LIMITS), bb = encode(b, LIMITS), cc = encode(c, LIMITS);
        byte[] all = new byte[aa.length + bb.length + cc.length];
        System.arraycopy(aa, 0, all, 0, aa.length); System.arraycopy(bb, 0, all, aa.length, bb.length);
        System.arraycopy(cc, 0, all, aa.length + bb.length, cc.length);
        try (Receiver receiver = new Receiver()) {
            for (int at = 0; at < all.length;) {
                int count = Math.min(fragment, all.length - at);
                assertEquals(count, receiver.decoder.feed(all, at, count)); at += count;
            }
            assertEquals(List.of(a, b, c), receiver.frames); assertTrue(receiver.decoder.eof());
        }
    }

    @Test void frameLayoutUsesFixedBigEndianFieldsAndFull128BitIds() {
        Id id = new Id(0x123456789abcdef0L, 0xfedcba9876543210L);
        Envelope e = new Envelope(id, id, id, id, WatchdogModel.Direction.W_H, 0x0102030405060708L);
        byte[] bytes = encode(new Frame(e, new Control(WatchdogModel.Type.ERROR, "ü")), LIMITS);
        ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(HEADER + 2, bytes.length); assertEquals(MAGIC, view.getInt(0));
        assertEquals(1, view.getShort(4)); assertEquals(12, view.getShort(6)); assertEquals(2, view.getLong(8));
        assertEquals(e.sequence(), view.getLong(16));
        for (int offset : new int[]{24, 40, 56, 72}) {
            assertEquals(id.high(), view.getLong(offset)); assertEquals(id.low(), view.getLong(offset + 8));
        }
        assertEquals(4, bytes[88]); for (int i = 89; i < HEADER; i++) assertEquals(0, bytes[i]);
        assertArrayEquals("ü".getBytes(StandardCharsets.UTF_8), Arrays.copyOfRange(bytes, HEADER, bytes.length));
        try (Receiver r = new Receiver()) { r.decoder.feed(bytes, 0, bytes.length); assertEquals(e, r.frames.getFirst().envelope()); }
    }

    @Test void dataIsStrictlyValidatedButNeverReassembledAsPayloadString() {
        String text = "ä漢😀"; byte[] bytes = encode(frame(new Data(text)), LIMITS);
        try (Receiver r = new Receiver()) {
            for (byte value : bytes) assertEquals(1, r.decoder.feed(new byte[]{value}, 0, 1));
            assertEquals(new DataCount(text.getBytes(StandardCharsets.UTF_8).length), r.frames.getFirst().payload());
            byte[] received = new byte[r.data.size()]; for (int i = 0; i < received.length; i++) received[i] = (byte) (int) r.data.get(i);
            assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), received);
            assertEquals(LIMITS.decoderReservation(), r.budget.peak()); assertTrue(r.decoder.eof());
        }
    }

    @Test void emptyInputDoesNotAdvanceOrAllocateAndEofIsIdempotent() {
        try (Receiver r = new Receiver()) {
            long reserved = r.budget.used(); assertEquals(0, r.decoder.feed(new byte[0], 0, 0));
            assertEquals(reserved, r.budget.used()); assertTrue(r.frames.isEmpty());
            assertTrue(r.decoder.eof()); assertTrue(r.decoder.eof()); assertEquals(0, r.budget.used());
            byte[] after = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
            assertEquals(0, r.decoder.feed(after, 0, after.length)); assertTrue(r.frames.isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 4, 95, 96, 97})
    void eofInHeaderOrPayloadPoisonsSessionPermanently(int delivered) {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "abcd")), LIMITS);
        try (Receiver r = new Receiver()) {
            r.decoder.feed(bytes, 0, delivered); assertFalse(r.decoder.eof());
            assertEquals(BinaryIpcCodec.Error.TRUNCATED, r.decoder.error());
            assertEquals(0, r.decoder.feed(bytes, delivered, bytes.length - delivered));
            assertEquals(0, r.budget.used()); assertTrue(r.frames.isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(longs = {-1, Long.MIN_VALUE, 65, Integer.MAX_VALUE, Long.MAX_VALUE})
    void hostileLengthFailsAfterFixedHeaderWithoutPayloadAllocation(long length) {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
        ByteBuffer.wrap(bytes).putLong(8, length);
        try (Receiver r = new Receiver()) {
            long peak = r.budget.peak(); assertEquals(HEADER, r.decoder.feed(bytes, 0, bytes.length));
            assertEquals(BinaryIpcCodec.Error.LENGTH, r.decoder.error()); assertEquals(peak, r.budget.peak());
            assertEquals(0, r.budget.used()); assertTrue(r.frames.isEmpty());
            assertEquals(0, r.decoder.feed(bytes, 0, bytes.length));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 65535})
    void unsupportedVersionFailsClosed(int version) {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
        ByteBuffer.wrap(bytes).putShort(4, (short) version);
        try (Receiver r = new Receiver()) { r.decoder.feed(bytes, 0, bytes.length); assertEquals(BinaryIpcCodec.Error.VERSION, r.decoder.error()); }
    }

    @ParameterizedTest @ValueSource(ints = {0, 13, 19, 24, 65535})
    void unknownWireTypeCannotResolveAnArbitraryClass(int type) {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
        ByteBuffer.wrap(bytes).putShort(6, (short) type);
        try (Receiver r = new Receiver()) { r.decoder.feed(bytes, 0, bytes.length); assertEquals(BinaryIpcCodec.Error.TYPE, r.decoder.error()); }
    }

    @ParameterizedTest @ValueSource(ints = {0, 16, 24, 88, 89})
    void malformedMagicSequenceIdsDirectionAndReservedFieldsAreRejected(int offset) {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
        if (offset == 0) bytes[0] = 0;
        else if (offset == 16) ByteBuffer.wrap(bytes).putLong(16, 0);
        else if (offset == 24) Arrays.fill(bytes, 24, 40, (byte) 0);
        else bytes[offset] = (byte) 127;
        try (Receiver r = new Receiver()) {
            r.decoder.feed(bytes, 0, bytes.length); assertNotNull(r.decoder.error()); assertTrue(r.frames.isEmpty());
        }
    }

    static Stream<byte[]> invalidUtf8() {
        return Stream.of(new byte[]{(byte) 0xc0, (byte) 0xaf}, new byte[]{(byte) 0xe0, (byte) 0x80, (byte) 0x80},
                new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80}, new byte[]{(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                new byte[]{(byte) 0x80}, new byte[]{(byte) 0xf8}, new byte[]{(byte) 0xc2}, new byte[]{(byte) 0xc2, 0x20});
    }
    @ParameterizedTest @MethodSource("invalidUtf8")
    void invalidUtf8IsNeverSilentlyReplaced(byte[] invalid) {
        byte[] bytes = new byte[HEADER + invalid.length];
        byte[] header = encode(frame(new Control(WatchdogModel.Type.ERROR, "")), LIMITS);
        System.arraycopy(header, 0, bytes, 0, HEADER); ByteBuffer.wrap(bytes).putLong(8, invalid.length);
        System.arraycopy(invalid, 0, bytes, HEADER, invalid.length);
        for (int type : new int[]{12, DATA}) {
            ByteBuffer.wrap(bytes).putShort(6, (short) type);
            try (Receiver r = new Receiver()) {
                for (int i = 0; i < bytes.length && r.decoder.error() == null; i++) r.decoder.feed(bytes, i, 1);
                assertEquals(BinaryIpcCodec.Error.UTF8, r.decoder.error()); assertTrue(r.frames.isEmpty());
            }
        }
    }

    @Test void encoderRejectsMalformedUtf16AndOversizeBeforeReservation() {
        BufferBudget budget = new BufferBudget(4096); var account = budget.account(4096);
        for (String text : new String[]{"\ud800", "\udc00", "\ud800x", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> Encoder.open(frame(new Control(WatchdogModel.Type.ERROR, text)), LIMITS, account));
            assertEquals(0, budget.used());
        }
        assertThrows(IllegalArgumentException.class, () -> Encoder.open(frame(new DataCount(12)), LIMITS, account));
    }

    @Test void fixedPayloadsAndResultLimitsAreValidatedOnBothSides() {
        byte[] bytes = encode(frame(new Begin(128, 100, 4)), LIMITS);
        ByteBuffer.wrap(bytes).putLong(HEADER, 129);
        try (Receiver r = new Receiver()) { r.decoder.feed(bytes, 0, bytes.length); assertEquals(BinaryIpcCodec.Error.PAYLOAD, r.decoder.error()); }
        byte[] group = encode(frame(new GroupProof(new Id(1, 1), 1, true, true, 0, 0)), LIMITS);
        ByteBuffer.wrap(group).putInt(HEADER + 33, 1);
        try (Receiver r = new Receiver()) { r.decoder.feed(group, 0, group.length); assertEquals(BinaryIpcCodec.Error.PAYLOAD, r.decoder.error()); }
        BufferBudget budget = new BufferBudget(4096);
        assertThrows(IllegalArgumentException.class, () -> Encoder.open(frame(new Begin(129, 1, 1)), LIMITS, budget.account(4096)));
        assertEquals(0, budget.used());
    }

    @Test void budgetRefusalOccursBeforeLargeDecoderAllocationAndLeasesAreIdempotent() {
        BufferBudget budget = new BufferBudget(64); var account = budget.account(64);
        assertThrows(IllegalStateException.class, () -> new Decoder(new Limits(Integer.MAX_VALUE, 512, 1, 1, 1), account, (f, s) -> true));
        assertEquals(0, budget.used());
        BufferBudget.Lease lease = account.reserve(64); assertNotNull(lease); assertNull(account.reserve(1));
        lease.close(); lease.close(); assertEquals(0, budget.used());
        assertNull(Encoder.open(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS, account));
        assertEquals(0, budget.used());
    }

    @Test void consumerFailureIsTerminalBoundedAndReleasesDecoderReservation() {
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.HELLO, "")), LIMITS);
        for (boolean throwsException : new boolean[]{false, true}) {
            BufferBudget budget = new BufferBudget(4096);
            Decoder decoder = new Decoder(LIMITS, budget.account(4096), (f, s) -> {
                if (throwsException) throw new IllegalStateException("synthetic-private-detail"); return false;
            });
            decoder.feed(bytes, 0, bytes.length); assertNotNull(decoder.error());
            assertEquals(0, budget.used()); assertEquals(0, decoder.feed(bytes, 0, bytes.length)); decoder.close();
            assertFalse(decoder.error().name().contains("private"));
        }
    }

    @Test void strictUtf8AcceptsAllScalarBoundaryValuesWithoutReplacement() {
        String text = "\u0000\u007f\u0080\u07ff\u0800\ud7ff\ue000\uffff\ud800\udc00\udbff\udfff";
        byte[] bytes = encode(frame(new Control(WatchdogModel.Type.STATUS, text)), LIMITS);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), Arrays.copyOfRange(bytes, HEADER, bytes.length));
        try (Receiver r = new Receiver()) {
            for (int i = 0; i < bytes.length; i++) r.decoder.feed(bytes, i, 1);
            assertEquals(new Control(WatchdogModel.Type.STATUS, text), r.frames.getFirst().payload());
        }
    }

    @Test void exactControlAndDataFrameLimitsUseActualUtf8Bytes() {
        byte[] control = encode(frame(new Control(WatchdogModel.Type.ERROR, "ä".repeat(32))), LIMITS);
        assertEquals(LIMITS.controlFrame(), control.length);
        byte[] data = encode(frame(new Data("x".repeat(LIMITS.dataFrame() - HEADER))), LIMITS);
        assertEquals(LIMITS.dataFrame(), data.length);
        try (Receiver r = new Receiver()) {
            r.decoder.feed(control, 0, control.length); r.decoder.feed(data, 0, data.length);
            assertEquals(2, r.frames.size()); assertNull(r.decoder.error());
        }
        BufferBudget budget = new BufferBudget(4096);
        assertThrows(IllegalArgumentException.class, () -> Encoder.open(frame(new Data("x".repeat(LIMITS.dataFrame() - HEADER + 1))), LIMITS, budget.account(4096)));
        assertEquals(0, budget.used());
    }

    @Test void configuredLargeResultDeclarationDoesNotAllocateTheDeclaredResult() {
        Limits limits = new Limits(160, 512, 160L * 1024 * 1024, 30_032, 4);
        Frame expected = frame(new Begin(64L * 1024 * 1024, 30_032, 4));
        byte[] encoded = encode(expected, limits); assertEquals(HEADER + 16, encoded.length);
        BufferBudget budget = new BufferBudget(4096); List<Frame> decoded = new ArrayList<>();
        try (Decoder decoder = new Decoder(limits, budget.account(4096), (f, s) -> { decoded.add(f); return true; })) {
            decoder.feed(encoded, 0, encoded.length); assertEquals(List.of(expected), decoded);
            assertEquals(limits.decoderReservation(), budget.peak());
        }
        assertEquals(0, budget.used());
    }

    @Test void dataRequiresExplicitConsumerInsteadOfSilentPayloadLoss() {
        byte[] data = encode(frame(new Data("abc")), LIMITS); BufferBudget budget = new BufferBudget(4096);
        try (Decoder decoder = new Decoder(LIMITS, budget.account(4096), (f, s) -> true)) {
            decoder.feed(data, 0, data.length); assertEquals(BinaryIpcCodec.Error.DELIVERY, decoder.error());
            assertEquals(0, budget.used());
        }
    }

    @Test void invalidTrailingUtf8DiscardsProvisionalDataWithoutCommit() {
        byte[] data = encode(frame(new Data("ab")), LIMITS); data[data.length - 1] = (byte) 0xff;
        int[] staged = {0}, commits = {0}, discards = {0}; BufferBudget budget = new BufferBudget(4096);
        try (Decoder decoder = new Decoder(LIMITS, budget.account(4096), new Sink() {
            public boolean dataByte(Envelope e, int value) { staged[0]++; return true; }
            public boolean accept(Frame f, long size) { commits[0]++; return true; }
            public void discard(Envelope e) { staged[0] = 0; discards[0]++; }
        })) {
            decoder.feed(data, 0, data.length); assertEquals(BinaryIpcCodec.Error.UTF8, decoder.error());
            assertEquals(0, staged[0]); assertEquals(0, commits[0]); assertEquals(1, discards[0]);
        }
        assertEquals(0, budget.used());
    }
}
