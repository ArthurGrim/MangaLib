package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StartIntentStoreTest {
    @TempDir Path temporary;
    static final StartIntentStore.Binding BINDING = new StartIntentStore.Binding(new UUID(1, 1), new UUID(2, 2), new UUID(3, 3));
    @Test void confirmedIntentBindsAllFieldsAndTerminalRevisionsNeverOverwriteHistory() throws Exception {
        Path root = temporary.resolve("run");
        try (var store = StartIntentStore.create(root, BINDING, point -> { })) {
            var proof = store.prepare(); assertTrue(store.valid(proof)); byte[] history = Files.readAllBytes(root.resolve("r1.bin"));
            var end = store.complete(); assertFalse(store.valid(proof));
            assertEquals(StartIntentStore.Kind.COMPLETED, StartIntentStore.inspect(root, BINDING, end).kind());
            assertArrayEquals(history, Files.readAllBytes(root.resolve("r1.bin")));
            assertThrows(IOException.class, store::prepare); assertThrows(IOException.class, store::abort);
        }
        assertThrows(IOException.class, () -> StartIntentStore.create(root, BINDING, point -> { }));
    }
    @ParameterizedTest @EnumSource(StartIntentStore.Point.class)
    void simulatedCrashNeverMintsAProofOrAllowsAutomaticRestart(StartIntentStore.Point point) throws Exception {
        Path root = temporary.resolve("crash");
        try (var store = StartIntentStore.create(root, BINDING, at -> { if (at == point) throw new IOException("synthetic-crash"); })) {
            assertThrows(IOException.class, store::prepare); assertFalse(store.valid(null));
            assertThrows(IOException.class, store::prepare);
        }
        assertFalse(StartIntentStore.inspect(root, BINDING, null).verified());
        assertThrows(IOException.class, () -> StartIntentStore.create(root, BINDING, at -> { }));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void missingTruncatedCorruptExtraOrForeignRecoveryFilesFailClosed(int broken) throws Exception {
        Path root = temporary.resolve("invalid");
        try (var store = StartIntentStore.create(root, BINDING, point -> { })) {
            var proof = store.prepare(); Path file = root.resolve("r1.bin");
            if (broken == 0) Files.delete(file); // Ausschließlich gerade neu erzeugte TempDir-Testdatei.
            if (broken == 1) Files.write(file, new byte[]{1});
            if (broken == 2) { byte[] data = Files.readAllBytes(file); data[40] ^= 1; Files.write(file, data); }
            if (broken == 3) Files.write(root.resolve("r2.bin.pending"), new byte[]{1});
            if (broken == 4) Files.write(root.resolve("other"), new byte[0]);
            var expected = broken == 5 ? new StartIntentStore.Binding(new UUID(9, 9), BINDING.session(), BINDING.group()) : BINDING;
            assertFalse(StartIntentStore.inspect(root, expected, proof.anchor()).verified());
            if (broken != 5) assertFalse(store.valid(proof));
        }
    }
    @Test void staleAnchorAndForeignStoreProofAreRejectedAndAbortIsImmutable() throws Exception {
        try (var a = StartIntentStore.create(temporary.resolve("a"), BINDING, point -> { });
             var b = StartIntentStore.create(temporary.resolve("b"), BINDING, point -> { })) {
            var proof = a.prepare(); b.prepare(); assertFalse(b.valid(proof));
            var aborted = a.abort(); assertFalse(a.valid(proof));
            assertFalse(StartIntentStore.inspect(a.directory(), BINDING, proof.anchor()).verified());
            assertTrue(StartIntentStore.inspect(a.directory(), BINDING, aborted).verified());
            assertThrows(IOException.class, a::complete);
        }
    }
}
