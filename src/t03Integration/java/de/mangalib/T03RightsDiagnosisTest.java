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

/** Exakt vier freigegebene SELECT/SHOW-Anweisungen, keine Tabelleninhalte/Fixtures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers = T03RightsDiagnosisTest.Approval.class)
class T03RightsDiagnosisTest {
    public static final class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("t03.rightsDiagnosis.approved")))
                throw new IllegalStateException("Berechtigungsdiagnose nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean ScraperGateway scrapers;

    @Test void diagnosesOnlyIdentityModeAndGrants() {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true); transaction.setTimeout(20);
        var result = transaction.execute(status -> {
            assertTrue("mangalib_test".equals(em.createNativeQuery("SELECT DATABASE()").getSingleResult()), "DATABASE_IDENTITY_STOP");
            assertTrue("mangalib_test_user@localhost".equals(em.createNativeQuery("SELECT CURRENT_USER()").getSingleResult()), "ACCOUNT_IDENTITY_STOP");
            System.out.println("T03_RIGHTS_DATABASE=EXPECTED_TEST_DATABASE ACCOUNT=EXPECTED_TEST_ACCOUNT");
            Object mode = em.createNativeQuery("SELECT @@GLOBAL.partial_revokes").getSingleResult();
            @SuppressWarnings("unchecked")
            List<String> grants = em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();
            return T03RightsDiagnosis.analyze(mode, grants);
        });
        assertNotNull(result);
        System.out.println("T03_RIGHTS_RESULT=" + result);
        verifyNoInteractions(scrapers);
        assertTrue(result.verified(), "RIGHTS_DIAGNOSIS_STOP");
        System.out.println("T03_RIGHTS_DIAGNOSIS_VERIFIED");
    }
}
