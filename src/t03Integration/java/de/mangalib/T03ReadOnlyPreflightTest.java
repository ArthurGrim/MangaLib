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
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Nur SELECT/SHOW über vorhandene geschützte JPA-Verbindungen. Keine Fixtures,
 * Schreibservices, Commitklasse, Lifecycle-Setups oder HTTP-Anfragen. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers = T03ReadOnlyPreflightTest.Approval.class)
class T03ReadOnlyPreflightTest {
    public static final class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("t03.readOnly.approved")))
                throw new IllegalStateException("Lesende Datenbankvorprüfung nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean ScraperGateway scrapers;

    @Test void verifiesOnlyIdentityRightsSchemaAndExistingRows() throws Exception {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        var before = capture();
        System.out.println("T03_READ_ONLY_BEFORE_FINGERPRINT=" + before.fingerprint());
        var names = new java.util.TreeSet<>(T03ReadOnlyPolicy.REFERENCES); names.addAll(T03RunJournal.BUSINESS);
        names.forEach(table -> System.out.println("T03_READ_ONLY_ROWS " + table + "=" + before.values().get(table).size()));
        T03ReadOnlyPolicy.verify(before, T03ReadOnlyPolicy.EXPECTED_FINGERPRINT);
        var after = capture(); // Unabhängige Transaktion, kein gemeinsamer Repeatable-Read-Snapshot.
        assertTrue(before.equals(after), "Zustand während lesender Vorprüfung geändert: STOP");
        verifyNoInteractions(scrapers);
        System.out.println("T03_READ_ONLY_AFTER_FINGERPRINT=" + after.fingerprint());
        System.out.println("T03_READ_ONLY_TABLES=10 REFERENCES=78 BUSINESS_ROWS=0");
    }
    private TestDatabaseSnapshot.Snapshot capture() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true); transaction.setTimeout(20);
        return transaction.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            String identity = (String) em.createNativeQuery("SELECT CURRENT_USER()").getSingleResult();
            assertTrue(identity.matches("mangalib_test_user@[A-Za-z0-9_.:%-]+"), "Unerwartete MySQL-Accountidentität: STOP");
            System.out.println("T03_READ_ONLY_ACCOUNT=" + identity);
            @SuppressWarnings("unchecked")
            List<String> grants = em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();
            T03ServerRights.verify(grants);
            return TestDatabaseSnapshot.capture(em);
        });
    }
}
