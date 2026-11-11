package de.mangalib;

import de.mangalib.service.StatisticsDistributionService;
import de.mangalib.service.EinkaufslisteService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatisticsIntegrationTest {
    @Autowired EntityManager em;
    @Autowired StatisticsDistributionService statistics;
    @Autowired EinkaufslisteService shopping;
    private TestDatabaseSnapshot.Snapshot before;

    @BeforeTransaction void capture() throws Exception { before = TestDatabaseSnapshot.capture(em); }
    @AfterTransaction void preserve() throws Exception { assertEquals(before, TestDatabaseSnapshot.capture(em)); }

    @Test void groupsActualVolumesUnknownFieldsAndPreservesCentsAcrossYears() throws Exception {
        var overview = statistics.overview();
        var details = statistics.details();
        int year = 2401;
        while (em.createQuery("select count(e) from EinkaufslisteItem e where year(e.erscheinungsdatum) in :years",
                Long.class).setParameter("years", java.util.List.of(year, year + 1)).getSingleResult() != 0) year += 2;
        var fixture = PersistenceTestFixtures.persistGraph(em);
        fixture.seriesDetails().setHerkunft(null);
        fixture.seriesDetails().setStartJahr(null);
        fixture.volumes().getFirst().setIstSpecial(true);
        fixture.volumes().getLast().setIstSpecial(null);
        fixture.volumes().getFirst().setIstGelesen(true);
        fixture.volumes().getLast().setIstGelesen(false);
        fixture.shoppingItem().setGekauft(true);
        fixture.shoppingItem().setErscheinungsdatum(LocalDate.of(year, 12, 31));
        em.flush(); em.clear();
        var actual = statistics.overview();
        assertEquals(total(overview.getFirst()) + 1, total(actual.getFirst()));
        assertEquals(total(overview.get(4)) + 2, total(actual.get(4)));
        assertEquals(count(overview.get(3), "Unbekannt") + 1, count(actual.get(3), "Unbekannt"));
        var actualDetails = statistics.details();
        assertEquals(count(details.get(2), "Unbekannt") + 1, count(actualDetails.get(2), "Unbekannt"));
        assertEquals(count(details.get(3), "Spezialband") + 1, count(actualDetails.get(3), "Spezialband"));
        assertEquals(count(details.get(3), "Unbekannt") + 1, count(actualDetails.get(3), "Unbekannt"));
        assertEquals(0, new BigDecimal("14.98").compareTo(shopping.getGeldProMonat(year).get(12)));
        assertTrue(shopping.getGeldProMonat(year + 1).isEmpty());
        var item = em.find(de.mangalib.entity.EinkaufslisteItem.class, fixture.shoppingItem().getId());
        item.setErscheinungsdatum(LocalDate.of(year + 1, 1, 1));
        em.flush(); em.clear();
        assertTrue(shopping.getGeldProMonat(year).isEmpty());
        assertEquals(0, new BigDecimal("14.98").compareTo(shopping.getGeldProMonat(year + 1).get(1)));
    }

    private long count(StatisticsDistributionService.Distribution distribution, String key) {
        return distribution.counts().getOrDefault(key, 0L);
    }
    private long total(StatisticsDistributionService.Distribution distribution) {
        return distribution.counts().values().stream().mapToLong(Long::longValue).sum();
    }
}
