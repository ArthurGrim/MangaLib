package de.mangalib;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Lesender Vergleich über den bestehenden Guard; keine Verbindungen oder Datendumps außerhalb von JPA. */
final class TestDatabaseSnapshot {
    private static final Map<String, String> KEYS = Map.of(
            "status", "status_id", "verlage", "verlag_id", "typen", "typ_id", "formate", "format_id",
            "sammelbaende", "id", "mangareihe", "id", "mangadetails", "id", "baende", "id",
            "einkaufsliste", "id", "einkaufslistedetails", "id");
    record Snapshot(Map<String, List<List<Object>>> values) {
        String fingerprint() throws Exception {
            var digest = MessageDigest.getInstance("SHA-256");
            values.forEach((table, rows) -> {
                update(digest, table);
                rows.forEach(row -> {
                    update(digest, "row");
                    row.forEach(value -> update(digest, canonical(value)));
                });
            });
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private TestDatabaseSnapshot() { }

    static Snapshot capture(EntityManager em) {
        assertEquals("mangalib_test", em.createNativeQuery("select database()").getSingleResult());
        assertEquals("mangalib_test_user", em.createNativeQuery("select substring_index(current_user(), '@', 1)").getSingleResult());
        var values = new TreeMap<String, List<List<Object>>>();
        KEYS.forEach((table, key) -> values.put(table, rows(em, "select * from " + table + " order by " + key)));
        // AUTO_INCREMENT-Zähler bewusst ausnehmen; Schema und Constraints dürfen sich nicht ändern.
        values.put("schema.columns", rows(em, """
                select TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE,
                       COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME
                from information_schema.COLUMNS where TABLE_SCHEMA = database()
                order by TABLE_NAME, ORDINAL_POSITION
                """));
        values.put("schema.tables", rows(em, """
                select TABLE_NAME, ENGINE, TABLE_COLLATION from information_schema.TABLES
                where TABLE_SCHEMA = database() order by TABLE_NAME
                """));
        values.put("schema.indexes", rows(em, """
                select TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE
                from information_schema.STATISTICS where TABLE_SCHEMA = database()
                order by TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """));
        values.put("schema.foreignKeys", rows(em, """
                select TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
                from information_schema.REFERENTIAL_CONSTRAINTS where CONSTRAINT_SCHEMA = database()
                order by TABLE_NAME, CONSTRAINT_NAME
                """));
        values.put("schema.keyColumns", rows(em, """
                select TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, ORDINAL_POSITION,
                       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                from information_schema.KEY_COLUMN_USAGE where TABLE_SCHEMA = database()
                order by TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION
                """));
        return new Snapshot(Collections.unmodifiableMap(values));
    }

    private static List<List<Object>> rows(EntityManager em, String sql) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(row -> Collections.unmodifiableList(Arrays.asList(row.clone()))).toList();
    }

    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Number number) return "number:" + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Timestamp stamp) return "time:" + stamp.toLocalDateTime();
        if (value instanceof java.time.LocalDateTime time) return "time:" + time;
        if (value instanceof java.sql.Date date) return "date:" + date.toLocalDate();
        if (value instanceof java.time.LocalDate date) return "date:" + date;
        if (value instanceof byte[] bytes) return "bytes:" + HexFormat.of().formatHex(bytes);
        return "value:" + value;
    }

    private static void update(MessageDigest digest, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(bytes);
    }
}
