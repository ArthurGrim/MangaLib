package de.mangalib;

import de.mangalib.service.ScraperGateway;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reine SELECT-/SHOW-Vorprüfung vor der ersten S03-Migration. Keine Fixtures. */
@SpringBootTest(properties = "mangalib.reading-history.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = S03SchemaPreflightTest.Approval.class)
class S03SchemaPreflightTest {
    public static class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("s03.schema.approved")))
                throw new IllegalStateException("Lesende S03-Schemavorprüfung nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean ScraperGateway scrapers;

    @Test void capturesIdentityRightsTypesAndUnchangedRowsWithoutWrites() throws Exception {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        var tx = new TransactionTemplate(manager); tx.setReadOnly(true); tx.setTimeout(30);
        var before = tx.execute(status -> {
            assertEquals("mangalib_test", em.createNativeQuery("select database()").getSingleResult());
            assertEquals("mangalib_test_user@localhost", em.createNativeQuery("select current_user()").getSingleResult());
            @SuppressWarnings("unchecked") List<String> grants = em.createNativeQuery("show grants for current_user()").getResultList();
            assertTrue(T03RightsDiagnosis.analyze(em.createNativeQuery("select @@global.partial_revokes").getSingleResult(), grants).verified());
            var snapshot = TestDatabaseSnapshot.capture(em);
            var tables = snapshot.values().get("schema.tables");
            boolean afterMigration = "AFTER".equals(System.getProperty("s03.schema.phase", "BEFORE"));
            assertEquals(afterMigration ? 11 : 10, tables.size()); assertTrue(tables.stream().allMatch(row -> "InnoDB".equals(row.get(1))));
            var expectedNames = new java.util.TreeSet<>(snapshot.values().keySet());
            expectedNames.removeIf(name -> name.startsWith("schema."));
            if (afterMigration) expectedNames.add("band_reading_events");
            assertEquals(expectedNames, new java.util.TreeSet<>(tables.stream().map(row -> row.getFirst().toString()).toList()));
            if (afterMigration) {
                assertEquals(0, ((Number) em.createNativeQuery("select count(*) from band_reading_events").getSingleResult()).intValue());
                var store = new de.mangalib.service.ReadingHistoryStore();
                org.springframework.test.util.ReflectionTestUtils.setField(store, "em", em);
                store.validateSchema();
            }
            assertEquals(78, java.util.stream.Stream.of("status", "verlage", "typen", "formate", "sammelbaende")
                    .mapToInt(table -> snapshot.values().get(table).size()).sum());
            // Der vorhandene Geschäftsbestand wird vollständig verglichen, nicht bereinigt oder als leer vorausgesetzt.
            String type = em.createNativeQuery("select COLUMN_TYPE from information_schema.COLUMNS where TABLE_SCHEMA=database() and TABLE_NAME='baende' and COLUMN_NAME='id'")
                    .getSingleResult().toString();
            assertTrue(type.equals("bigint") || type.equals("bigint unsigned"), "Band-ID-Typ benötigt separate Prüfung.");
            System.out.println("S03_BAND_ID_TYPE=" + type);
            System.out.println("S03_SERVER_VERSION=" + em.createNativeQuery("select version()").getSingleResult());
            return snapshot;
        });
        var after = tx.execute(status -> TestDatabaseSnapshot.capture(em));
        assertEquals(before, after);
        String expected = System.getProperty("s03.schema.expectedFingerprint");
        if (expected != null) assertEquals(expected, before.fingerprint());
        String rows = fingerprint(before, false);
        String schema = fingerprint(before, true);
        if ("AFTER".equals(System.getProperty("s03.schema.phase"))) {
            assertEquals(System.getProperty("s03.schema.expectedRows"), rows);
            assertEquals(System.getProperty("s03.schema.expectedOldSchema"), schema);
        }
        System.out.println("S03_SCHEMA_READ_ONLY_FINGERPRINT=" + before.fingerprint());
        System.out.println("S03_OLD_ROWS_FINGERPRINT=" + rows);
        System.out.println("S03_OLD_SCHEMA_FINGERPRINT=" + schema);
        System.out.println(expected == null ? "S03_SCHEMA_CAPTURED_NOT_HISTORICALLY_VERIFIED" : "S03_SCHEMA_REFERENCE_VERIFIED");
        verifyNoInteractions(scrapers);
    }

    private String fingerprint(TestDatabaseSnapshot.Snapshot snapshot, boolean schema) throws Exception {
        var selected = new java.util.TreeMap<String, List<List<Object>>>();
        snapshot.values().forEach((key, value) -> {
            if (key.startsWith("schema.") == schema) selected.put(key, schema
                    ? value.stream().filter(row -> !"band_reading_events".equals(row.getFirst())).toList() : value);
        });
        return new TestDatabaseSnapshot.Snapshot(selected).fingerprint();
    }
}
