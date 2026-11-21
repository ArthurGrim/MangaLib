package de.mangalib.controller;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingMonthRegressionTest {
    @Test void sameMonthInDifferentYearsKeepsBothGroupsAndExactTotals() {
        var controller = new EinkaufslisteController();
        for (var entry : Map.<String,Class<?>>of("verlagService", VerlagService.class,
                "typService", TypService.class, "formatService", FormatService.class,
                "sammelbaendeService", SammelbandService.class, "statusService", StatusService.class).entrySet())
            ReflectionTestUtils.setField(controller, entry.getKey(), mock(entry.getValue()));
        var service = mock(EinkaufslisteService.class);
        ReflectionTestUtils.setField(controller, "einkaufslisteService", service);
        var now = YearMonth.now(); var old = now.minusYears(1);
        var first = item("0.01"); var second = item("13.98"); var unknown = item(null);
        when(service.getItemsForMonth(any())).thenAnswer(call -> {
            LocalDate date = call.getArgument(0); assertEquals(1, date.getDayOfMonth());
            var period = YearMonth.from(date);
            return period.equals(old) ? List.of(first) : period.equals(now) ? List.of(second) : List.of();
        });
        when(service.undatedItems()).thenReturn(List.of(unknown));
        var model = new ExtendedModelMap();
        assertEquals("einkaufsliste", controller.showEinkaufsliste(model));
        var groups = (Map<?,?>) model.get("itemsByMonth");
        String month = controller.getGermanMonthName(now.getMonthValue());
        assertEquals(List.of(first), groups.get(month + " " + old.getYear()));
        assertEquals(List.of(second), groups.get(month + " " + now.getYear()));
        assertEquals(3, groups.size());
        var totals = (Map<?,?>) model.get("totalPriceByMonth");
        assertEquals("0,01 €", totals.get(month + " " + old.getYear()));
        assertEquals("13,98 €", totals.get(month + " " + now.getYear()));
        assertEquals("Unbekannt", totals.get("Ohne Erscheinungsdatum"));
        verify(service, times(19)).getItemsForMonth(any());
        verifyNoMoreInteractions(ignoreStubs(service));
    }
    private EinkaufslisteItem item(String price) {
        var item = new EinkaufslisteItem(); item.setGekauft(false); item.setAnzahlBaende(1);
        item.setGesamtpreis(price == null ? null : new BigDecimal(price)); return item;
    }
}
