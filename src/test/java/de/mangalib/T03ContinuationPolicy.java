package de.mangalib;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Die akzeptierten zwölf Zeilen sind Fremdbestand für jeden neuen Lauf.
 * Journalbindung ersetzt keine aktuelle DB-Abnahme; keine Baseline neu erfinden. */
final class T03ContinuationPolicy {
    static final String PRIOR_RUN = "264bc431-7c43-4fc2-b2d4-3efd35d8bb1c";
    static final String PRIOR_JOURNAL_SHA = "8606d5080f4608c5e6a7cab0edf89258e0e4e677f5998b15434a38736b8edc97";
    static Map<String, Set<Long>> priorIds() throws Exception {
        return parse(Files.readAllBytes(Path.of(".gradle/t03-integration", PRIOR_RUN, "ids.txt")),
                PRIOR_RUN, PRIOR_JOURNAL_SHA);
    }
    static Map<String,Set<Long>> priorRolledBackIds()throws Exception{
        byte[] bytes=Files.readAllBytes(Path.of(".gradle/t03-integration",PRIOR_RUN,"ids.txt"));
        return rolledBackIds(bytes,PRIOR_RUN,PRIOR_JOURNAL_SHA);
    }
    static Map<String,Set<Long>> rolledBackIds(byte[] bytes,String run,String hash)throws Exception{
        var committed=parse(bytes,run,hash);var result=new TreeMap<String,Set<Long>>();
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().toList()){
            var fields=line.split(" ");if(fields.length==4&&fields[1].equals("ID"))
                result.computeIfAbsent(fields[2],key->new TreeSet<>()).add(Long.parseLong(fields[3]));
        }
        result.forEach((table,ids)->ids.removeAll(committed.getOrDefault(table,Set.of())));
        assertEquals(5,result.values().stream().mapToInt(Set::size).sum());
        var frozen=new TreeMap<String,Set<Long>>();result.forEach((table,ids)->frozen.put(table,Set.copyOf(ids)));
        return Collections.unmodifiableMap(frozen);
    }
    static Map<String, Set<Long>> parse(byte[] bytes, String run, String hash) throws Exception {
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "Journalbindung abweichend: STOP");
        var result = new TreeMap<String, Set<Long>>();
        var attempted = new HashSet<String>();
        int stage = 0, count = 0;
        for (String line : new String(bytes, StandardCharsets.UTF_8).lines().toList()) {
            String[] fields = line.split(" ", -1);
            assertTrue(fields.length >= 2 && fields[0].equals(run), "Fremdes/ungültiges Journal: STOP");
            if (fields.length == 2 && fields[1].equals("PREPARED")) { assertEquals(0, stage); stage = 1; }
            else if (fields.length == 2 && fields[1].equals("STAGE_CONFIRMED")) { assertTrue(stage >= 1 && stage <= 3); stage++; }
            else {
                assertTrue(stage >= 1 && stage <= 3 && fields.length == 4 && fields[1].equals("ID")
                        && T03RunJournal.BUSINESS.contains(fields[2]) && fields[3].matches("[1-9][0-9]*"), "Journalphase unbestätigt: STOP");
                long id = Long.parseLong(fields[3]); assertTrue(attempted.add(fields[2]+"/"+id)); count++;
                if (stage != 2) result.computeIfAbsent(fields[2], key -> new TreeSet<>()).add(id);
            }
        }
        assertEquals(4, stage); assertEquals(17, count);
        assertEquals(Map.of("mangareihe",2,"mangadetails",2,"baende",7,"einkaufsliste",1),
                result.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e -> e.getValue().size())));
        var copy = new TreeMap<String,Set<Long>>(); result.forEach((key, ids) -> copy.put(key, Set.copyOf(ids)));
        return Collections.unmodifiableMap(copy);
    }
    static void verify(TestDatabaseSnapshot.Snapshot snapshot, Map<String, Set<Long>> prior) throws Exception {
        verify(snapshot, prior, T03ReadOnlyPolicy.EXPECTED_FINGERPRINT);
    }
    // Nur synthetische Unit-Snapshots haben einen eigenen Referenzhash. Die DB-Tasks
    // rufen ausschließlich den oben fest gebundenen Einstieg auf.
    static void verify(TestDatabaseSnapshot.Snapshot snapshot, Map<String, Set<Long>> prior,
            String historicalEmptyFingerprint) throws Exception {
        // Ausschließlich für den historischen Schema-/Stammdatenvergleich im RAM leeren.
        // Keine SQL-Änderung, keine Abschwächung der unveränderten historischen Policy.
        var empty = new TreeMap<>(snapshot.values());
        T03RunJournal.BUSINESS.forEach(table -> empty.put(table, List.of()));
        T03ReadOnlyPolicy.verify(new TestDatabaseSnapshot.Snapshot(empty), historicalEmptyFingerprint);
        for (String table : T03RunJournal.BUSINESS)
            assertEquals(prior.getOrDefault(table, Set.of()), T03Preservation.ids(snapshot, table), "Ungeklärter bestehender Geschäftsdatenbestand: STOP");
    }
}
