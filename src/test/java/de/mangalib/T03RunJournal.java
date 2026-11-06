package de.mangalib;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Nur Laufkennung/Tabellen/IDs und feste Phasen, niemals Feldwerte oder Fehlerbodys.
 * Jeder Eintrag wird vor Rückkehr dauerhaft geflusht. Keine Löschfunktion. */
final class T03RunJournal implements AutoCloseable {
    static final Set<String> BUSINESS = Set.of("mangareihe", "mangadetails", "baende", "einkaufsliste", "einkaufslistedetails");
    private final UUID run;
    private final FileChannel file;
    private final Map<String, Set<Long>> ids = new TreeMap<>();

    T03RunJournal(UUID run, Path directory) throws IOException {
        this.run = Objects.requireNonNull(run);
        file = FileChannel.open(directory.resolve("ids.txt"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        append("PREPARED");
    }
    synchronized void register(String table, long id) {
        if (!BUSINESS.contains(table) || id <= 0) throw new IllegalArgumentException("Unzulässige Fixture-ID.");
        if (ids.computeIfAbsent(table, k -> new TreeSet<>()).contains(id)) return;
        append("ID " + table + " " + id);
        ids.get(table).add(id);
    }
    synchronized Map<String, Set<Long>> ids() {
        var copy = new TreeMap<String, Set<Long>>();
        ids.forEach((table, values) -> copy.put(table, Set.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
    synchronized void phase(boolean success) { append(success ? "STAGE_CONFIRMED" : "STOP"); }
    private void append(String value) {
        try {
            var bytes = ByteBuffer.wrap((run + " " + value + "\n").getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        } catch (IOException failure) { throw new IllegalStateException("Fixture-Journal nicht bestätigt.", failure); }
    }
    @Override public void close() throws IOException { file.close(); }
}
