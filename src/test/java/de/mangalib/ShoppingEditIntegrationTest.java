package de.mangalib;

import de.mangalib.entity.Format;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Status;
import de.mangalib.service.EinkaufslisteService;
import de.mangalib.service.LibraryWriteCoordinator;
import de.mangalib.service.PurchaseService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ShoppingEditIntegrationTest {
    @Autowired EntityManager em;
    @Autowired LibraryWriteCoordinator coordinator;
    @Autowired EinkaufslisteService shopping;
    @Autowired PurchaseService purchases;
    @MockitoSpyBean PlatformTransactionManager manager;

    @Test
    @EnabledIfEnvironmentVariable(named = "MANGALIB_M01_COMMIT_TEST", matches = "synthetic-two-rows")
    void committedEditWinsAgainstAnAlreadyOpenPurchase() throws Exception {
        var before = TestDatabaseSnapshot.capture(em);
        long id = coordinator.execute(() -> {
            long publisher = em.createQuery("from Verlag", Verlag.class).setMaxResults(1).getSingleResult().getVerlagId();
            long type = em.createQuery("from Typ", Typ.class).setMaxResults(1).getSingleResult().getTypId();
            long format = em.createQuery("from Format", Format.class).setMaxResults(1).getSingleResult().getFormatId();
            return shopping.saveEinkaufslisteItem(null, publisher, type, format, "M01_COMMIT_" + UUID.randomUUID(),
                    3, new BigDecimal("2.00"), false, true, null, null, new BigDecimal("-0.01"),
                    LocalDate.of(2026, 11, 10), Map.of()).getId();
        });
        var allowed = Map.of("einkaufsliste", java.util.Set.of(id), "einkaufslistedetails", java.util.Set.of(id));
        var preview = purchases.purchase(new PurchaseService.Request(id, null, null, null, null));
        assertEquals(PurchaseService.Outcome.STATUS_REQUIRED, preview.outcome());
        long status = em.createQuery("from Status", Status.class).setMaxResults(1).getSingleResult().getStatusId();
        var token = shopping.editState(id).get("editToken");
        var overlap = new T03PurchaseOverlap(manager, coordinator);
        assertEquals(java.util.List.of("EDITED", "PURCHASE_STATE_CONFLICT"), overlap.run(
                () -> {
                    shopping.updateShoppingItem(id, Map.of("editToken", token, "preisProBand", "3.00"));
                    return "EDITED";
                },
                () -> purchases.purchase(new PurchaseService.Request(id, status, null, null, null,
                        preview.purchaseToken())).outcome().name()));
        assertTrue(overlap.completionConfirmed());
        var stored = shopping.editState(id);
        assertEquals("8.99", stored.get("gesamtpreis"));
        assertEquals(false, stored.get("gekauft"));
        T03Preservation.verify(before, TestDatabaseSnapshot.capture(em), allowed);
    }

    @Test void editSurvivesReloadAndFixtureRollsBack() throws Exception {
        var before = TestDatabaseSnapshot.capture(em);
        assertThrows(RollBackFixture.class, () -> coordinator.execute(() -> {
            long publisher = em.createQuery("from Verlag", Verlag.class).setMaxResults(1).getSingleResult().getVerlagId();
            long type = em.createQuery("from Typ", Typ.class).setMaxResults(1).getSingleResult().getTypId();
            long format = em.createQuery("from Format", Format.class).setMaxResults(1).getSingleResult().getFormatId();
            long id = shopping.saveEinkaufslisteItem(null, publisher, type, format, "M01_" + UUID.randomUUID(),
                    1, new BigDecimal("2.00"), false, true, null, null, new BigDecimal("-0.01"),
                    LocalDate.of(2026, 11, 10), Map.of()).getId();
            em.flush();
            em.clear();
            var state = shopping.editState(id);
            long status = em.createQuery("from Status", Status.class).setMaxResults(1).getSingleResult().getStatusId();
            var previewRequest = new PurchaseService.Request(id, null, null, null, null);
            var oldPreview = purchases.purchase(previewRequest);
            assertEquals(PurchaseService.Outcome.STATUS_REQUIRED, oldPreview.outcome());
            var update = new HashMap<String, Object>();
            update.put("editToken", state.get("editToken"));
            update.put("anzahlBaende", 3);
            update.put("istEbayPreis", false);
            update.put("erscheinungsdatum", null);
            shopping.updateShoppingItem(id, update);
            em.clear();
            var stored = shopping.editState(id);
            assertEquals("5.99", stored.get("gesamtpreis"));
            assertEquals("-0.01", stored.get("gesamtpreisAenderung"));
            assertEquals(false, stored.get("istEbayPreis"));
            assertNull(stored.get("erscheinungsdatum"));
            assertTrue(shopping.undatedItems().stream().anyMatch(item -> item.getId().equals(id)));
            assertThrows(EinkaufslisteService.ShoppingConflict.class, () -> shopping.updateShoppingItem(id, update));
            assertEquals(PurchaseService.Outcome.PURCHASE_STATE_CONFLICT, purchases.purchase(
                    new PurchaseService.Request(id, status, null, null, null, oldPreview.purchaseToken())).outcome());
            assertEquals(PurchaseService.Outcome.PURCHASE_STATE_CONFLICT, purchases.purchase(
                    new PurchaseService.Request(id, status, null, null, null)).outcome());
            var currentPreview = purchases.purchase(previewRequest);
            assertEquals(PurchaseService.Outcome.CREATED, purchases.purchase(
                    new PurchaseService.Request(id, status, null, null, null, currentPreview.purchaseToken())).outcome());
            em.clear();
            assertEquals(true, shopping.editState(id).get("gekauft"));
            throw new RollBackFixture();
        }));
        assertEquals(before, TestDatabaseSnapshot.capture(em));
    }

    private static final class RollBackFixture extends RuntimeException {}
}
