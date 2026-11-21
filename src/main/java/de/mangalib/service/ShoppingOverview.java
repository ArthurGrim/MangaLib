package de.mangalib.service;

import de.mangalib.entity.EinkaufslisteItem;
import java.math.BigDecimal;
import java.text.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Aufbereitung der Einkaufsansicht ohne weitere Datenbankabfragen oder Mutationen. */
public record ShoppingOverview(Map<String,List<EinkaufslisteItem>> itemsByMonth,
                               Map<String,Integer> totalVolumesByMonth,
                               Map<String,String> totalPriceByMonth) {
    static ShoppingOverview from(List<EinkaufslisteItem> dated, List<EinkaufslisteItem> undated) {
        var periods = new TreeMap<YearMonth,List<EinkaufslisteItem>>();
        for (var item : dated) periods.computeIfAbsent(YearMonth.from(item.getErscheinungsdatum()), ignored -> new ArrayList<>()).add(item);
        var groups = new LinkedHashMap<String,List<EinkaufslisteItem>>();
        var volumes = new LinkedHashMap<String,Integer>(); var prices = new LinkedHashMap<String,String>();
        var label = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY);
        periods.forEach((period, items) -> {
            // Ein gemischter Monat enthält wie bisher auch seine bereits gekauften Positionen.
            if (!items.stream().allMatch(item -> Boolean.TRUE.equals(item.getGekauft()))) groups.put(period.format(label), List.copyOf(items));
        });
        if (!undated.isEmpty()) groups.put("Ohne Erscheinungsdatum", List.copyOf(undated));
        var money = new DecimalFormat("#,##0.00 €", new DecimalFormatSymbols(Locale.GERMANY));
        groups.forEach((key, items) -> {
            volumes.put(key, items.stream().map(EinkaufslisteItem::getAnzahlBaende).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
            prices.put(key, items.stream().anyMatch(item -> item.getGesamtpreis() == null) ? "Unbekannt"
                    : money.format(items.stream().map(EinkaufslisteItem::getGesamtpreis).reduce(BigDecimal.ZERO, BigDecimal::add)));
        });
        return new ShoppingOverview(Collections.unmodifiableMap(groups), Collections.unmodifiableMap(volumes), Collections.unmodifiableMap(prices));
    }
}
