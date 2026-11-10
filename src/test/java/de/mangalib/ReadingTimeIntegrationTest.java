package de.mangalib;

import de.mangalib.service.ReadingTimeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.AfterTransaction;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReadingTimeIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ReadingTimeService service;
    private TestDatabaseSnapshot.Snapshot before;

    @BeforeTransaction void capture() throws Exception { before = TestDatabaseSnapshot.capture(em); }
    @AfterTransaction void preserve() throws Exception { assertEquals(before, TestDatabaseSnapshot.capture(em)); }

    @Test void projectionUsesIndividualReadStatesAndKnownRereadsAfterReload() throws Exception {
        var baseline = service.estimate();
        var graph = PersistenceTestFixtures.persistGraph(em);
        graph.volumes().getFirst().setIstGelesen(true);
        graph.volumes().getFirst().setReread(2);
        graph.volumes().getLast().setIstGelesen(false);
        graph.volumes().getLast().setReread(100);
        long expected = 45L * graph.seriesDetails().getSammelbaende().getMultiplikator();
        em.flush();
        em.clear();
        var actual = service.estimate();
        assertEquals(baseline.minutes() + expected, actual.minutes());
        assertEquals(baseline.minutesWithRereads() + 3 * expected, actual.minutesWithRereads());
        var first = em.find(de.mangalib.entity.Band.class, graph.volumes().getFirst().getId());
        first.setIstGelesen(false);
        em.flush();
        em.clear();
        assertEquals(baseline, service.estimate());
    }
}
