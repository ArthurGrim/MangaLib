package de.mangalib.service;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import de.mangalib.repository.EinkaufslisteRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T04RegressionTest {
    @Test void missingNewVolumePriceIsRejectedButExplicitZeroIsValid() {
        assertThrows(IllegalArgumentException.class, () -> BandService.newPrices(1, 1, null, Map.of()));
        assertEquals(List.of(new BigDecimal("0.00")), BandService.newPrices(1, 1, BigDecimal.ZERO, Map.of()));
    }
    @Test void malformedImportPriceIsNotSilentlyReplacedByFallback() {
        assertThrows(IllegalArgumentException.class, () -> BandService.newPrices(1, 1, new BigDecimal("8.50"), Map.of("Band 1 Preis", "unbekannt")));
        assertEquals(List.of(new BigDecimal("8.50")), BandService.newPrices(1, 1, null, Map.of("Band 1 Preis", " € 8,50 ")));
    }
    @Test void monthlyMoneyKeepsPositiveAndNegativeCents() {
        var repository = mock(EinkaufslisteRepository.class);
        when(repository.findGeldProMonat(2026)).thenReturn(List.of(new Object[]{1, new BigDecimal("0.01")}, new Object[]{2, new BigDecimal("-0.02")}));
        assertEquals(Map.of(1,new BigDecimal("0.01"),2,new BigDecimal("-0.02")), new EinkaufslisteService(repository).getGeldProMonat(2026));
    }
    @Test void historicalStructureTemplateHasNoFixedTargetOrDestructiveStatements() throws Exception {
        var sql = Files.readString(Path.of("SQLQuery.sql"));
        var statements = sql.replaceAll("(?m)--.*$", "");
        assertFalse(java.util.regex.Pattern.compile("(?mi)^\\s*(USE|DROP|TRUNCATE|ALTER|INSERT|UPDATE|DELETE)\\b").matcher(statements).find());
        assertEquals(10, statements.split("CREATE TABLE", -1).length - 1);
        assertEquals(10, statements.split("ENGINE=InnoDB", -1).length - 1);
        assertTrue(statements.contains("reread INT DEFAULT 0,\n    PRIMARY KEY") || statements.contains("reread INT DEFAULT 0,\r\n    PRIMARY KEY"));
        assertFalse(statements.contains("REFERENCES Verlage"));
    }
}
