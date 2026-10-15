package de.mangalibmanual;

import de.mangalib.entity.EinkaufslisteItem;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import static org.junit.jupiter.api.Assertions.*;

/** Echte Preis-Ausdrücke aus dem Template, ohne Boot, Datenbank oder HTTP-Listener. */
class ShoppingPriceTemplateTest {
    @ParameterizedTest @CsvSource({"8.49,'8,49 €'", "13.98,'13,98 €'", "0,'0,00 €'",
            "-0.02,'−0,02 €'", "1.20,'1,20 €'", "1234.56,'1.234,56 €'",
            "1234567.89,'1.234.567,89 €'", "12.345,'12,34 €'"})
    void allThreeDisplaysUseGermanMoneyWithoutChangingValues(String input, String expected) throws Exception {
        String source = Files.readString(Path.of("src/main/resources/templates/einkaufsliste.html"));
        var matcher = Pattern.compile("th:text=\"([^\"]*(?:#numbers\\.formatDecimal|totalPriceByMonth)[^\"]*)\"").matcher(source);
        var expressions = new ArrayList<String>();
        while (matcher.find()) expressions.add(matcher.group(1));
        assertEquals(3, expressions.size(), "Einzelpreis, Positionssumme und Monatssumme prüfen");
        var value = new BigDecimal(input); var item = new EinkaufslisteItem();
        item.setPreis(value); item.setGesamtpreis(value);
        var context = new Context(Locale.US); // Nicht auf die Rechner-/Kontext-Locale vertrauen.
        context.setVariable("item", item);
        context.setVariable("items", Map.entry("Oktober", List.of(item)));
        var monthlyFormat = new DecimalFormat("#,##0.00 €", new DecimalFormatSymbols(Locale.GERMANY));
        context.setVariable("totalPriceByMonth", Map.of("Oktober", monthlyFormat.format(value)));
        var engine = new SpringTemplateEngine();
        for (String expression : expressions) {
            String output = engine.process("<div th:text=\"" + expression + "\"></div>", context);
            assertTrue(output.contains(">" + (expression.contains("totalPriceByMonth") ? "Gesamtpreis: " : "") + expected + "</div>"), output);
            assertEquals(1L, output.chars().filter(c -> c == '€').count());
        }
        assertSame(value, item.getPreis()); assertSame(value, item.getGesamtpreis());
        assertEquals(new BigDecimal(input), item.getPreis());
    }
}
