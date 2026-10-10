package de.mangalib;

import de.mangalib.service.BandScraper;
import de.mangalib.service.MangaScraper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Reale Views außerhalb einer Testtransaktion; vorhandene Daten ausschließlich lesen.
 * Ohne persistierte Geschäftsdatensätze deckt dieser Test keine befüllten Lazy-Beziehungen ab.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplateRenderingTest {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/einkaufsliste", "/statistik/st_overview"})
    void rendersCommittedTestDataWithoutAnEnclosingTransaction(String path) throws Exception {
        assertFalse(TestTransaction.isActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var before = TestDatabaseSnapshot.capture(em);
        try (var manga = mockStatic(MangaScraper.class, invocation -> { throw new AssertionError("Scraping gesperrt."); });
             var band = mockStatic(BandScraper.class, invocation -> { throw new AssertionError("Scraping gesperrt."); })) {
            mvc.perform(get(path)).andExpect(request().asyncNotStarted())
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(view().name(path.substring(1))).andExpect(content().string(org.hamcrest.Matchers.containsString("MangaLib")));
            manga.verifyNoInteractions();
            band.verifyNoInteractions();
        } finally {
            assertEquals(before, TestDatabaseSnapshot.capture(em), "Rendering muss Zeilen und Schema unverändert lassen.");
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
