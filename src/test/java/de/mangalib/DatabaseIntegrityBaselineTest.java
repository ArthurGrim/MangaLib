package de.mangalib;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Fingerabdruck zum Vergleich der isolierten Datenbank vor/nach dem Frameworkwechsel. */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseIntegrityBaselineTest {
    @Autowired private EntityManager em;

    @Test
    void recordsOnlyHashesAndCountsOfExistingRowsAndSchema() throws Exception {
        var snapshot = TestDatabaseSnapshot.capture(em);
        System.out.println("TEST_DATABASE_FINGERPRINT=" + snapshot.fingerprint());
        snapshot.values().forEach((table, rows) -> System.out.println("TEST_DATABASE_ROWS " + table + "=" + rows.size()));
    }
}
