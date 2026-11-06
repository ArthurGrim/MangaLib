package de.mangalib.service;

import de.mangalib.controller.EinkaufslisteController;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** T03-0-Fälle wurden in PurchaseServiceTest zu ausführbaren Soll-Regressionsfällen
 * weiterentwickelt. Hier werden die freigegebenen Controller-Verträge geprüft.
 * Historische IST-Nachweise bleiben im T03-Plan und den ursprünglichen Reports erhalten. */
class PurchaseFlowCharacterizationTest {
    EinkaufslisteController controller;
    PurchaseService purchases;
    @BeforeEach void setup() {
        controller = new EinkaufslisteController(); purchases = mock(PurchaseService.class);
        ReflectionTestUtils.setField(controller, "purchaseService", purchases);
    }
    private org.springframework.http.ResponseEntity<?> buy(boolean create) {
        return create ? controller.buyItemCreate(Map.of("itemId", "7", "statusId", "1"))
                : controller.buyItemUpdate(Map.of("itemId", "7"));
    }
    @ParameterizedTest @EnumSource(PurchaseService.Outcome.class)
    void typedResultsHaveFixedStatusHeaderAndPayload(PurchaseService.Outcome outcome) {
        when(purchases.purchase(any())).thenReturn(new PurchaseService.Result(outcome, 42L, 4, 5));
        for (boolean create : List.of(false, true)) {
            var result = buy(create);
            int expected = switch (outcome) {
                case UPDATED, CREATED, ALREADY_PURCHASED, CONFIRM_VOLUMES -> 200;
                case STATUS_REQUIRED -> 417;
                case NOT_FOUND, DETAILS_NOT_FOUND -> 404;
                default -> 409;
            };
            assertEquals(expected, result.getStatusCode().value());
            assertEquals(outcome.name(), result.getHeaders().getFirst("X-MangaLib-Purchase-Outcome"));
            if (outcome == PurchaseService.Outcome.CONFIRM_VOLUMES)
                assertEquals(Map.of("message", "Bitte Bände bestätigen", "seriesId", "42", "firstBand", 4, "lastBand", 5), result.getBody());
            if (expected == 409) assertEquals(Map.of("message", "Kaufübernahme erfordert eine Klärung."), result.getBody());
            if (outcome == PurchaseService.Outcome.ALREADY_PURCHASED)
                assertEquals(Map.of("message", "Kauf bereits übernommen"), result.getBody());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bothRoutesPassExplicitIdentityAndRangeExactlyOnce(boolean create) {
        when(purchases.purchase(any())).thenReturn(PurchaseService.Result.of(PurchaseService.Outcome.UPDATED));
        var body = new HashMap<String,Object>(Map.of("itemId", "7", "confirmedSeriesId", "42", "firstBand", 4, "lastBand", 5));
        if (create) body.put("statusId", "1");
        var response = create ? controller.buyItemCreate(body) : controller.buyItemUpdate(body);
        assertEquals(200, response.getStatusCode().value());
        verify(purchases).purchase(new PurchaseService.Request(7, create ? 1L : null, 42L, 4, 5));
        verifyNoMoreInteractions(purchases);
    }
    @ParameterizedTest @ValueSource(strings = {"missingSeries", "missingFirst", "missingLast", "zero", "reverse", "over", "string", "null"})
    void malformedConfirmationNeverReachesService(String fault) {
        var body = new HashMap<String,Object>(Map.of("itemId", "7", "confirmedSeriesId", "42", "firstBand", 4, "lastBand", 5));
        switch (fault) {
            case "missingSeries" -> body.remove("confirmedSeriesId");
            case "missingFirst" -> body.remove("firstBand");
            case "missingLast" -> body.remove("lastBand");
            case "zero" -> body.put("firstBand", 0);
            case "reverse" -> body.put("lastBand", 3);
            case "over" -> body.put("lastBand", 10001);
            case "string" -> body.put("firstBand", "4");
            case "null" -> body.put("lastBand", null);
        }
        assertEquals(400, controller.buyItemUpdate(body).getStatusCode().value()); verifyNoInteractions(purchases);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void internalFailureNeverLeaksOrReturnsSuccess(boolean create) {
        when(purchases.purchase(any())).thenThrow(new IllegalStateException("synthetic private SQL detail"));
        var response = buy(create);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Anfrage konnte nicht verarbeitet werden.", response.getBody());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void validationFailureRemainsNeutral400(boolean create) {
        when(purchases.purchase(any())).thenThrow(new IllegalArgumentException("synthetic private detail"));
        var response = buy(create); assertEquals(400, response.getStatusCode().value());
        assertEquals("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.", response.getBody());
    }
}
