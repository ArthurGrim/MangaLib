package de.mangalib.service;

import de.mangalib.entity.Band;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reale Serviceobjekte mit synthetischen Repository-Fakes; kein Spring-/JPA-Kontext. */
class BandInventoryTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyPermutationAssignsImportedOrdinalToSameId(int permutation) {
        var f = new T03Fixtures.Library();
        int[][] orders = {{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        var original = new ArrayList<>(f.stored);
        f.stored.clear();
        for (int index : orders[permutation]) f.stored.add(original.get(index));
        var before = f.state();
        f.bandService.updateOrCreateBaende(f.row, 3, null, null,
                Map.of("Band 2 href", "https://example.invalid/volumes/8123"), false);
        assertEquals(8123, original.get(1).getBandIndex());
        assertEquals(before.get(101L), f.state().get(101L));
        assertEquals(before.get(103L), f.state().get(103L));
        assertEquals(102L, original.get(1).getId());
        assertEquals(2, original.get(1).getBandNr());
        assertSame(f.row, original.get(1).getMangaReihe());
    }

    @ParameterizedTest @ValueSource(strings = {"duplicateNumber", "gap", "nullNumber", "nullId", "duplicateId", "foreignRow"})
    void inconsistentImportIsRejectedBeforeSeriesOrBandMutation(String fault) {
        var f = new T03Fixtures.Library();
        Band first = f.stored.getFirst();
        switch (fault) {
            case "duplicateNumber" -> first.setBandNr(2);
            case "gap" -> first.setBandNr(4);
            case "nullNumber" -> first.setBandNr(null);
            case "nullId" -> first.setId(null);
            case "duplicateId" -> first.setId(102L);
            case "foreignRow" -> first.setMangaReihe(T03Fixtures.row(99));
        }
        String title = f.row.getTitel();
        assertThrows(IllegalArgumentException.class, () -> f.edit(3, BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of("Band 1 href", "https://example.invalid/volumes/8123")));
        assertEquals(title, f.row.getTitel());
        assertNull(first.getBandIndex());
        verify(f.bands, never()).save(any()); verify(f.rows, never()).save(any());
        verify(f.details, never()).save(any());
    }

    @Test void singleBandCannotOverwriteAnotherNumber() {
        var f = new T03Fixtures.Library(); var before = f.state();
        var input = new Band(); input.setId(101L); input.setBandNr(2);
        assertThrows(IllegalArgumentException.class, () -> f.bandService.updateBand(input));
        assertEquals(before, f.state()); verify(f.bands, never()).save(any());
    }

    @Test void importAddsExactlyOneLaterVolumeAndRejectsReplay() {
        var f = new T03Fixtures.Library();
        when(f.rows.findByIdForPurchase(42L)).thenReturn(Optional.of(f.row));
        when(f.bands.saveAndFlush(any())).thenAnswer(call -> {
            Band added = call.getArgument(0);
            added.setId(131L);
            f.stored.add(added);
            return added;
        });
        var before = f.state();
        Band volume = new Band();
        volume.setBandIndex(24092);
        volume.setBandNr(31);
        volume.setPreis(new BigDecimal("8.50"));
        f.series.saveImportedVolume(null, 42L, volume);
        assertEquals(4, f.row.getAnzahlBaende());
        assertEquals(new BigDecimal("26.50"), f.row.getGesamtpreis());
        assertEquals(Set.of(1, 2, 3, 31), BandInventory.byPublicationNumber(f.row, f.stored).keySet());
        before.forEach((id, state) -> assertEquals(state, f.state().get(id)));
        when(f.bands.existsByBandIndex(24092)).thenReturn(true);
        assertThrows(MangaReiheService.VolumeImportConflict.class,
                () -> f.series.saveImportedVolume(null, 42L, volume));
        verify(f.bands).saveAndFlush(volume);
    }

    @Test void editingLaterVolumePreservesGapsAndOtherVolumes() {
        var f = new T03Fixtures.Library();
        var later = f.stored.getLast();
        later.setBandNr(31);
        var before = f.state();
        var input = new Band(); input.setId(later.getId()); input.setPreis(new BigDecimal("9.00"));
        f.bandService.updateBand(input);
        assertEquals(31, later.getBandNr());
        assertEquals(new BigDecimal("21.00"), f.row.getGesamtpreis());
        assertEquals(before.get(101L), f.state().get(101L));
        assertEquals(before.get(102L), f.state().get(102L));
        assertEquals(Set.of(1, 2, 31), BandInventory.byPublicationNumber(f.row, f.stored).keySet());
        assertThrows(IllegalArgumentException.class, () -> BandInventory.indexed(f.row, f.stored));
    }

    @Test void financialAggregateUsesActualCountAndKnownIndividualValues() {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(8);
        Collections.reverse(f.stored); var before = f.state();
        var input = new Band(); input.setId(102L); input.setPreis(new BigDecimal("9.00"));
        f.bandService.updateBand(input);
        assertEquals(new BigDecimal("21.00"), f.row.getGesamtpreis());
        assertEquals(new BigDecimal("7.00"), f.row.getPreisProBand());
        assertEquals(8, f.row.getAnzahlBaende(), "Deklarationskorrektur bleibt T03-5");
        assertEquals(before.get(101L), f.state().get(101L));
        assertEquals(before.get(103L), f.state().get(103L));
    }

    @Test void totalUsesIndividualAdjustmentsInsteadOfStaleRowAggregate() {
        var f = new T03Fixtures.Library(); f.row.setAenderungGesamtpreis(new BigDecimal("999.00"));
        f.stored.getFirst().setAenderungPreis(new BigDecimal("-0.02"));
        assertEquals(new BigDecimal("17.98"), f.bandService.calculateGesamtpreis(f.row));
        assertEquals(new BigDecimal("-0.02"), f.bandService.calculateAenderungGesamtpreis(f.row));
        assertEquals(new BigDecimal("999.00"), f.row.getAenderungGesamtpreis());
        verify(f.bands, never()).save(any());
    }

    @Test void unchangedCountWithExplicitNewPriceRecalculatesMean() {
        var f = new T03Fixtures.Library();
        assertTrue(f.edit(3, new BigDecimal("8.00"), BigDecimal.ZERO, Map.of()).isPresent());
        assertEquals(new BigDecimal("24.00"), f.row.getGesamtpreis());
        assertEquals(new BigDecimal("8.00"), f.row.getPreisProBand());
    }
}
