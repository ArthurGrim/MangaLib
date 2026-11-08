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

/** Eigene spätere Freigabe: 70 Zeilen prüfen, niemals eine Referenz selbst anpassen. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers=T03SeventyReadOnlyTest.Approval.class)
class T03SeventyReadOnlyTest {
    public static class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext>{
        public void initialize(ConfigurableApplicationContext context){
            if(!"true".equals(System.getProperty("t03.seventy.approved")))throw new IllegalStateException("70-Zeilen-Vorprüfung nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @MockitoBean ScraperGateway scrapers;
    @Test void verifiesHistoricalProjectionAndCurrentSeventyRows()throws Exception{
        assertInstanceOf(GuardedTestDataSource.class,dataSource);
        var before=capture();T03MatrixHistory.verifySeventy(before);
        var after=capture();assertTrue(before.equals(after),"Bestand während Prüfung verändert: STOP");
        verifyNoInteractions(scrapers);
        System.out.println("T03_SEVENTY_CURRENT_FINGERPRINT="+after.fingerprint());
        System.out.println("T03_SEVENTY_TABLES=10 REFERENCES=78 BUSINESS_ROWS=70");
    }
    private TestDatabaseSnapshot.Snapshot capture(){
        var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setTimeout(20);
        return tx.execute(status->{
            @SuppressWarnings("unchecked") List<String> grants=em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();
            T03ServerRights.verify(grants);return TestDatabaseSnapshot.capture(em);
        });
    }
}
