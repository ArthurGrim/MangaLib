package de.mangalib;

import de.mangalib.controller.MainController;
import de.mangalib.entity.Band;
import de.mangalib.entity.Format;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.service.LibraryWriteCoordinator;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VolumeImportIntegrationTest {
    @Autowired EntityManager em;
    @Autowired MainController controller;
    @Autowired LibraryWriteCoordinator coordinator;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    org.springframework.transaction.PlatformTransactionManager manager;

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "MANGALIB_M02_COMMIT_TEST", matches = "synthetic-three-rows")
    void overlappingImportsCommitExactlyOneVolume() throws Exception {
        var before = TestDatabaseSnapshot.capture(em);
        int index = 2_100_010_000;
        while (em.createQuery("select count(b) from Band b where b.bandIndex = :index", Long.class)
                .setParameter("index", index).getSingleResult() != 0) index--;
        var input = new HashMap<String, Object>(Map.of("importMode", "volume", "anzahlBaende", 1,
                "bandIndex", String.valueOf(index), "bandNumber", "31", "preisProBand", "8.50",
                "titel", "M02_COMMIT_" + UUID.randomUUID()));
        input.put("statusId", String.valueOf(first(Status.class).getStatusId()));
        input.put("verlagId", String.valueOf(first(Verlag.class).getVerlagId()));
        input.put("typId", String.valueOf(first(Typ.class).getTypId()));
        input.put("formatId", String.valueOf(first(Format.class).getFormatId()));
        var overlap = new T03PurchaseOverlap(manager, coordinator);
        var results = overlap.runWithRejectedSecondWrite(
                () -> controller.addMangaReihe(input), () -> controller.addMangaReihe(input));
        assertTrue(overlap.completionConfirmed());
        assertEquals(List.of(200, 409), results.stream().map(r -> r.getStatusCode().value()).toList());
        var row = (de.mangalib.entity.MangaReihe) results.getFirst().getBody();
        assertNotNull(row);
        var bands = volumes(row.getId());
        assertEquals(1, bands.size());
        assertEquals(31, bands.getFirst().getBandNr());
        assertEquals(0, new BigDecimal("8.50").compareTo(em.find(de.mangalib.entity.MangaReihe.class, row.getId()).getGesamtpreis()));
        T03Preservation.verify(before, TestDatabaseSnapshot.capture(em), Map.of(
                "mangareihe", java.util.Set.of(row.getId()),
                "mangadetails", java.util.Set.of(row.getId()),
                "baende", java.util.Set.of(bands.getFirst().getId())));
    }

    @Test void laterVolumeAndGapFillPersistInsideOneRollbackFixture() throws Exception {
        var before = TestDatabaseSnapshot.capture(em);
        assertThrows(RollbackFixture.class, () -> coordinator.execute(() -> {
            int index = 2_100_000_000;
            while (em.createQuery("select count(b) from Band b where b.bandIndex in :indices", Long.class)
                    .setParameter("indices", List.of(index, index + 1)).getSingleResult() != 0) index -= 2;
            var input = new HashMap<String, Object>(Map.of("importMode", "volume",
                    "anzahlBaende", 1, "bandIndex", String.valueOf(index), "bandNumber", "31",
                    "preisProBand", "8.50", "titel", "M02_ROLLBACK_" + UUID.randomUUID()));
            input.put("statusId", String.valueOf(first(Status.class).getStatusId()));
            input.put("verlagId", String.valueOf(first(Verlag.class).getVerlagId()));
            input.put("typId", String.valueOf(first(Typ.class).getTypId()));
            input.put("formatId", String.valueOf(first(Format.class).getFormatId()));
            var created = controller.addMangaReihe(input);
            assertEquals(200, created.getStatusCode().value());
            var row = (de.mangalib.entity.MangaReihe) created.getBody();
            assertNotNull(row);
            long id = row.getId();
            em.flush();
            em.clear();
            var first = volumes(id).getFirst();
            assertEquals(31, first.getBandNr());
            assertEquals(1, volumes(id).size());
            long originalId = first.getId();
            input.put("targetSeriesId", String.valueOf(id));
            assertEquals(409, controller.addMangaReihe(input).getStatusCode().value());
            input.put("bandIndex", String.valueOf(index + 1));
            assertEquals(409, controller.addMangaReihe(input).getStatusCode().value());
            input.put("bandNumber", "2");
            assertEquals(200, controller.addMangaReihe(input).getStatusCode().value());
            em.flush();
            em.clear();
            assertEquals(List.of(2, 31), volumes(id).stream().map(Band::getBandNr).toList());
            assertEquals(originalId, volumes(id).getLast().getId());
            var reloaded = em.find(de.mangalib.entity.MangaReihe.class, id);
            assertEquals(2, reloaded.getAnzahlBaende());
            assertEquals(0, new BigDecimal("17.00").compareTo(reloaded.getGesamtpreis()));
            throw new RollbackFixture();
        }));
        assertEquals(before, TestDatabaseSnapshot.capture(em));
    }

    private <T> T first(Class<T> type) {
        return em.createQuery("from " + type.getSimpleName(), type).setMaxResults(1).getSingleResult();
    }

    private List<Band> volumes(long id) {
        return em.createQuery("from Band b where b.mangaReihe.id = :id order by b.bandNr", Band.class)
                .setParameter("id", id).getResultList();
    }

    private static final class RollbackFixture extends RuntimeException {}
}
