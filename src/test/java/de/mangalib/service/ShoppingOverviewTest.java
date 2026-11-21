package de.mangalib.service;

import de.mangalib.entity.EinkaufslisteItem;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingOverviewTest {
    @Test void keepsMixedMonthsUnknownStatesNullPricesAndUndatedBoughtItems() {
        var a = item("2025-11-02", true, "0.01", 1);
        var b = item("2025-11-03", null, "-0.02", null);
        var bought = item("2026-01-01", true, "4.00", 1);
        var unknown = item("2026-11-01", false, null, 3);
        var undated = item(null, true, "0.00", 0);
        var view = ShoppingOverview.from(List.of(unknown, bought, a, b), List.of(undated));
        assertEquals(List.of("November 2025","November 2026","Ohne Erscheinungsdatum"), new ArrayList<>(view.itemsByMonth().keySet()));
        assertEquals(List.of(a,b), view.itemsByMonth().get("November 2025"));
        assertEquals("-0,01 €", view.totalPriceByMonth().get("November 2025"));
        assertEquals(1, view.totalVolumesByMonth().get("November 2025"));
        assertEquals("Unbekannt", view.totalPriceByMonth().get("November 2026"));
        assertEquals(List.of(undated), view.itemsByMonth().get("Ohne Erscheinungsdatum"));
        assertNull(b.getGekauft()); assertNull(b.getAnzahlBaende()); assertNull(unknown.getGesamtpreis());
        assertThrows(UnsupportedOperationException.class, () -> view.itemsByMonth().clear());
    }
    @Test void emptyInventoryHasNoArtificialGroups() {
        var result = ShoppingOverview.from(List.of(),List.of());
        assertTrue(result.itemsByMonth().isEmpty()); assertTrue(result.totalPriceByMonth().isEmpty());
    }
    private EinkaufslisteItem item(String date, Boolean bought, String price, Integer count) {
        var e = new EinkaufslisteItem(); e.setErscheinungsdatum(date == null ? null : LocalDate.parse(date));
        e.setGekauft(bought); e.setGesamtpreis(price == null ? null : new BigDecimal(price)); e.setAnzahlBaende(count); return e;
    }
}
