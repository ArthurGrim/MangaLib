package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ShoppingCoverIntegrationTest {
    @Autowired EntityManager em;
    @Autowired LibraryWriteCoordinator coordinator;
    @Autowired EinkaufslisteService shopping;
    @Autowired PurchaseService purchases;

    @Test void aliasesSurviveBothPurchasePathsAndRollback() throws Exception {
        var before = TestDatabaseSnapshot.capture(em);
        try {
        assertThrows(FixtureRollback.class, () -> coordinator.execute(() -> {
            String title = "S02_ROLLBACK_" + UUID.randomUUID();
            int index = 2_100_050_000;
            while (em.createQuery("select count(r) from MangaReihe r where r.mangaIndex = :index", Long.class)
                    .setParameter("index", index).getSingleResult() != 0) index--;
            String original = "https://example.invalid/first.jpg";
            String added = "https://example.invalid/added.jpg";
            long first = create(title, index, Map.of("BildUrl", original));
            assertEquals(original, shopping.editState(first).get("coverUrl"));
            var preview = purchases.purchase(new PurchaseService.Request(first, null, null, null, null));
            assertEquals(PurchaseService.Outcome.STATUS_REQUIRED, preview.outcome());
            long status = em.createQuery("from Status", Status.class).setMaxResults(1).getSingleResult().getStatusId();
            assertEquals(PurchaseService.Outcome.CREATED, purchases.purchase(new PurchaseService.Request(
                    first, status, null, null, null, preview.purchaseToken())).outcome());
            em.flush(); em.clear();
            var row = em.createQuery("from MangaReihe where titel = :title", MangaReihe.class)
                    .setParameter("title", title).getSingleResult();
            long rowId = row.getId();
            var band = volumes(rowId).getFirst();
            long oldBandId = band.getId();
            assertEquals(original, band.getBildUrl().toExternalForm());
            assertEquals(original, row.getMangaDetails().getCoverUrl());
            long second = create(title, index, Map.of("Cover URL", added));
            var append = purchases.purchase(new PurchaseService.Request(second, null, null, null, null));
            assertEquals(PurchaseService.Outcome.CONFIRM_VOLUMES, append.outcome());
            assertEquals(PurchaseService.Outcome.UPDATED, purchases.purchase(new PurchaseService.Request(
                    second, null, rowId, 2, 2, append.purchaseToken())).outcome());
            em.flush(); em.clear();
            assertEquals(2, volumes(rowId).size());
            assertEquals(original, em.find(Band.class, oldBandId).getBildUrl().toExternalForm());
            assertEquals(added, volumes(rowId).getLast().getBildUrl().toExternalForm());
            assertEquals(original, em.find(MangaReihe.class, rowId).getMangaDetails().getCoverUrl());
            throw new FixtureRollback();
        }));
        } finally {
            assertEquals(before, TestDatabaseSnapshot.capture(em));
        }
    }

    private long create(String title, int index, Map<String, String> imported) {
        long publisher = em.createQuery("from Verlag", Verlag.class).setMaxResults(1).getSingleResult().getVerlagId();
        long type = em.createQuery("from Typ", Typ.class).setMaxResults(1).getSingleResult().getTypId();
        long format = em.createQuery("from Format", Format.class).setMaxResults(1).getSingleResult().getFormatId();
        long id = shopping.saveEinkaufslisteItem(index, publisher, type, format, title, 1,
                new BigDecimal("6.99"), false, false, null, null, BigDecimal.ZERO, null, imported).getId();
        em.flush(); em.clear();
        return id;
    }

    private java.util.List<Band> volumes(long rowId) {
        return em.createQuery("from Band b where b.mangaReihe.id = :id order by b.bandNr", Band.class)
                .setParameter("id", rowId).getResultList();
    }
    private static final class FixtureRollback extends RuntimeException {}
}
