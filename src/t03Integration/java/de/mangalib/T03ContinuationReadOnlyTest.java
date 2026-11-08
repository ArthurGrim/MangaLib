package de.mangalib;

import de.mangalib.service.ScraperGateway;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Eigene spätere Freigabe: aktueller Hash einschließlich der zwölf alten Fixtures. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers=T03ContinuationReadOnlyTest.Approval.class)
class T03ContinuationReadOnlyTest {
    public static class Approval implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
        public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("t03.continuation.approved"))) throw new IllegalStateException("Neue lesende Baseline nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @MockitoBean ScraperGateway scrapers;
    @Test void verifiesCurrentBaselineWithoutChangingHistoricalReference() throws Exception {
        assertInstanceOf(GuardedTestDataSource.class,dataSource);
        var prior=T03ContinuationPolicy.priorIds();
        var before=capture(); T03ContinuationPolicy.verify(before,prior);
        var after=capture(); assertTrue(before.equals(after),"Baseline während Prüfung verändert: STOP");
        verifyNoInteractions(scrapers);
        System.out.println("T03_CONTINUATION_CURRENT_FINGERPRINT="+after.fingerprint());
        System.out.println("T03_CONTINUATION_TABLES=10 REFERENCES=78 PRIOR_BUSINESS_ROWS=12");
    }
    private TestDatabaseSnapshot.Snapshot capture() {
        var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setTimeout(20);
        return tx.execute(status -> {
            @SuppressWarnings("unchecked") List<String> grants=em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();
            T03ServerRights.verify(grants); return TestDatabaseSnapshot.capture(em);
        });
    }
}
