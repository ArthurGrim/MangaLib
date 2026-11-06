package de.mangalib.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MoneyTest {
    @ParameterizedTest @CsvSource({"0.01,0.01,0.00,0.00", "0.02,0.01,0.01,0.00",
            "-0.01,-0.01,0.00,0.00", "-0.02,-0.01,-0.01,0.00", "20.00,6.67,6.67,6.66", "0.00,0.00,0.00,0.00"})
    void exactSignedDistribution(String sum, String a, String b, String c) {
        assertEquals(List.of(new BigDecimal(a), new BigDecimal(b), new BigDecimal(c)), Money.distribute(new BigDecimal(sum), 3));
    }
    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 10_000})
    void sumAndDeterminismAtTechnicalLimits(int count) {
        for (String raw : List.of("0.00", "0.01", "-0.01", "99999999.99", "-99999999.99")) {
            var value = new BigDecimal(raw); var plan = Money.distribute(value, count);
            assertEquals(value, plan.stream().reduce(new BigDecimal("0.00"), BigDecimal::add));
            assertEquals(plan, Money.distribute(value, count));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"0.001", "100000000", "-100000000", "1E100", "1E-100"})
    void rejectsUnrepresentableMoney(String raw) {
        assertThrows(IllegalArgumentException.class, () -> Money.amount(new BigDecimal(raw)));
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 10001})
    void rejectsInvalidTargetCount(int count) {
        assertThrows(IllegalArgumentException.class, () -> Money.distribute(BigDecimal.ONE, count));
    }
    @Test void noUnknownNewPriceAndNoMutationOnInvalidLastImport() {
        var f = new T03Fixtures.Library(); f.stored.clear();
        assertThrows(IllegalArgumentException.class, () -> f.bandService.createAndSaveBaende(f.row, 3, null, BigDecimal.ZERO, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> f.bandService.createAndSaveBaende(f.row, 3, BigDecimal.ONE, BigDecimal.ZERO,
                Map.of("Band 3 Preis", "0.001")));
        assertEquals(0, f.stored.size()); verify(f.bands, never()).save(any());
    }
    @Test void explicitZeroBulkAndNegativeAdjustmentPreserveIdentityAndRereads() {
        var f = new T03Fixtures.Library(); Collections.reverse(f.stored); var before = f.state();
        assertTrue(f.edit(3, BigDecimal.ZERO, new BigDecimal("-0.02"),
                Map.of("priceEdited", "true", "adjustmentEdited", "true")).isPresent());
        assertEquals(new BigDecimal("-0.02"), f.row.getGesamtpreis());
        for (var b : f.stored) {
            assertEquals(0, b.getPreis().signum());
            assertEquals(before.get(b.getId()).get(5), b.getReread());
        }
        assertEquals(List.of(new BigDecimal("-0.01"), new BigDecimal("-0.01"), new BigDecimal("0.00")),
                BandInventory.indexed(f.row, f.stored).values().stream().map(de.mangalib.entity.Band::getAenderungPreis).toList());
    }
    @Test void nullIsNotMoneyAndMeanHasExplicitRounding() {
        assertThrows(IllegalArgumentException.class, () -> Money.amount(null));
        assertEquals(new BigDecimal("6.67"), Money.mean(new BigDecimal("20.00"), 3));
    }

    @Test void explicitAdjustmentOnlyPreservesAllIndividualPrices() {
        var f = new T03Fixtures.Library(); f.stored.getFirst().setPreis(new BigDecimal("1.23"));
        var before = f.state();
        f.series.updateMangaReiheAenderungGesamtpreis(42L, new BigDecimal("0.02"));
        for (var b : f.stored) assertEquals(before.get(b.getId()).get(2), b.getPreis());
        assertEquals(new BigDecimal("13.25"), f.row.getGesamtpreis());
    }

    @Test void explicitZeroSetterAndBandAdjustmentRefreshAggregates() {
        var f = new T03Fixtures.Library();
        f.series.updateMangaReihePreisProBand(42L, BigDecimal.ZERO);
        assertEquals(0, f.row.getGesamtpreis().signum());
        f.bandService.updateBandAenderungPreis(102L, new BigDecimal("-0.01"));
        assertEquals(new BigDecimal("-0.01"), f.row.getGesamtpreis());
        assertEquals(new BigDecimal("-0.01"), f.row.getAenderungGesamtpreis());
    }

    @Test void aggregateSetterCannotInventOrRedistributeIndividualPrices() {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.series.updateMangaReiheGesamtpreis(42L, new BigDecimal("1.00")));
        assertEquals(before, f.state()); verify(f.rows, never()).save(any());
    }

    @Test void rawNewEntitiesAlsoRequireKnownPriceButHistoricalNullIsNotNormalized() {
        var f = new T03Fixtures.Library(); var row = T03Fixtures.row(42); row.setId(null); row.setPreisProBand(null);
        assertThrows(IllegalArgumentException.class, () -> f.series.saveMangaReihe(row));
        var band = new de.mangalib.entity.Band();
        assertThrows(IllegalArgumentException.class, () -> f.bandService.saveBand(band));
        verify(f.rows, never()).save(any()); verify(f.bands, never()).save(any());
        f.row.setPreisProBand(null); f.series.saveMangaReihe(f.row);
        assertNull(f.row.getPreisProBand());
    }
}
