package de.mangalib;

import de.mangalib.service.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich lesende Messung am bestehenden geschützten Testbestand. */
@SpringBootTest @ActiveProfiles("test")
class ShoppingOverviewIntegrationTest {
    @Autowired EntityManager em;
    @Autowired EinkaufslisteService service;
    @MockitoBean ScraperGateway scrapers;
    @Test void boundedReadPreservesGroupsAndUsesFewerSqlStatements() {
        var before = TestDatabaseSnapshot.capture(em);
        var stats = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            var expected = new LinkedHashMap<String,List<Long>>();
            var month = YearMonth.now(); var formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",Locale.GERMANY);
            long start = stats.getPrepareStatementCount();
            for (int i=-12;i<=6;i++) {
                var period = month.plusMonths(i); var items = service.getItemsForMonth(period.atDay(1));
                if (!items.isEmpty() && !items.stream().allMatch(e -> Boolean.TRUE.equals(e.getGekauft())))
                    expected.put(period.format(formatter),items.stream().map(e -> e.getId()).toList());
            }
            var undated = service.undatedItems();
            if (!undated.isEmpty()) expected.put("Ohne Erscheinungsdatum",undated.stream().map(e -> e.getId()).toList());
            long oldCount = stats.getPrepareStatementCount()-start;
            start = stats.getPrepareStatementCount();
            var overview = service.overview(LocalDate.now());
            var actual = new LinkedHashMap<String,List<Long>>();
            overview.itemsByMonth().forEach((key,items)->actual.put(key,items.stream().map(e -> e.getId()).toList()));
            long newCount = stats.getPrepareStatementCount()-start;
            assertEquals(expected,actual); assertTrue(newCount<oldCount,"Abfragebündelung muss tatsächlich SQL einsparen");
            System.out.println("T05_SQL_STATEMENTS old="+oldCount+" new="+newCount);
        } finally {
            stats.setStatisticsEnabled(enabled); em.clear();
            assertEquals(before,TestDatabaseSnapshot.capture(em));
        }
        org.mockito.Mockito.verifyNoInteractions(scrapers);
    }
}
