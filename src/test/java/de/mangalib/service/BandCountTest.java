package de.mangalib.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** D4 auf echten Services mit Repository-Fakes, ohne Spring oder Datenbank. */
class BandCountTest {
    @ParameterizedTest @CsvSource({"5,false", "2,false", "5,true", "2,true"})
    void pureCorrectionRetainsBandsDetailsAndUnknownPrices(int declared, boolean reversed) {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(declared);
        if (reversed) Collections.reverse(f.stored);
        f.stored.getFirst().setPreis(null); f.row.setGesamtpreis(null);
        var before = f.state(); var metadata = bandMetadata(f); var read = f.row.getMangaDetails().isIstGelesen();
        var reread = f.row.getMangaDetails().getReread();
        assertTrue(f.edit(3, null, null, Map.of("extendVolumes", "false")).isPresent());
        assertEquals(3, f.row.getAnzahlBaende()); assertEquals(before, f.state());
        assertEquals(metadata, bandMetadata(f));
        assertNull(f.row.getGesamtpreis()); assertEquals(new BigDecimal("6.00"), f.row.getPreisProBand());
        assertEquals(read, f.row.getMangaDetails().isIstGelesen()); assertEquals(reread, f.row.getMangaDetails().getReread());
        verifyNoWritesExceptRow(f); verify(f.rows).save(f.row);
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void reductionRejectsEvenWithPriceAndMetadataChanges(int target) {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.series.updateMangaReihe(42L, 123, 1L, 1L, 1L, 1L,
                "NEVER STORED", target, BigDecimal.ZERO, null, null, null, null, 1L,
                BigDecimal.ZERO, false, 0, Map.of("priceEdited", "true")));
        assertEquals(before, f.state()); assertEquals(3, f.row.getAnzahlBaende());
        assertEquals("T03 synthetische Reihe", f.row.getTitel());
        verifyNoWritesExceptRow(f); verify(f.rows, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"duplicate", "gap", "nullId", "nullNumber", "foreign", "details"})
    void invalidInventoryCannotBeCorrected(String fault) {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(5);
        switch (fault) {
            case "duplicate" -> f.stored.getFirst().setBandNr(2);
            case "gap" -> f.stored.getFirst().setBandNr(4);
            case "nullId" -> f.stored.getFirst().setId(null);
            case "nullNumber" -> f.stored.getFirst().setBandNr(null);
            case "foreign" -> f.stored.getFirst().setMangaReihe(T03Fixtures.row(99));
            case "details" -> f.row.setMangaDetails(null);
        }
        assertThrows(IllegalArgumentException.class, () -> f.edit(3, null, null, Map.of()));
        assertEquals(5, f.row.getAnzahlBaende()); verifyNoWritesExceptRow(f); verify(f.rows, never()).save(any());
    }
    @Test void partialCorrectionIsNotAnImplicitExtensionButExplicitAppendIsAllowed() {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(5); var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.edit(4, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> f.edit(4, BigDecimal.ONE, BigDecimal.ZERO, Map.of("extendVolumes", "false")));
        assertEquals(5, f.row.getAnzahlBaende()); assertEquals(before, f.state());
        assertTrue(f.edit(4, BigDecimal.ONE, BigDecimal.ZERO, Map.of("extendVolumes", "true")).isPresent());
        before.forEach((id, values) -> assertEquals(values, f.state().get(id)));
        assertEquals(4, f.row.getAnzahlBaende()); assertEquals(4, f.stored.getLast().getBandNr());
        assertFalse(f.stored.getLast().isIstGelesen());
    }
    @Test void correctionCannotCarryFinancialOrBulkChanges() {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(5);
        assertThrows(IllegalArgumentException.class, () -> f.edit(3, BigDecimal.ZERO, null, Map.of("priceEdited", "true")));
        assertThrows(IllegalArgumentException.class, () -> f.edit(3, null, null, Map.of("applyReadToVolumes", "true")));
        assertEquals(5, f.row.getAnzahlBaende()); verifyNoWritesExceptRow(f); verify(f.rows, never()).save(any());
    }
    @Test void directCountSetterCanCorrectButCannotAppendOrReduce() {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(5);
        assertThrows(IllegalArgumentException.class, () -> f.series.updateMangaReiheAnzahlBaende(42L, 2));
        assertThrows(IllegalArgumentException.class, () -> f.series.updateMangaReiheAnzahlBaende(42L, 4));
        assertEquals(3, f.series.updateMangaReiheAnzahlBaende(42L, 3).orElseThrow().getAnzahlBaende());
        verifyNoWritesExceptRow(f);
    }
    @Test void directBandWriterCannotReduce() {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.bandService.updateOrCreateBaende(f.row, 2,
                null, null, Map.of(), true));
        assertEquals(before, f.state()); verifyNoWritesExceptRow(f);
    }
    @Test void unchangedHighDeclarationDoesNotImplicitlyCreateMissingVolumes() {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(5); f.stored.getFirst().setPreis(null);
        var before = f.state();
        assertTrue(f.edit(5, null, null, Map.of("extendVolumes", "false")).isPresent());
        assertEquals(5, f.row.getAnzahlBaende()); assertEquals(before, f.state());
        // Normale Metadatenbearbeitung speichert Details; sie ist keine reine Korrektur.
        verify(f.bands, never()).save(any()); verify(f.bands, never()).delete(any());
        verify(f.bands, never()).deleteById(any());
    }
    @ParameterizedTest @ValueSource(ints = {2, 4})
    void rawEntitySaveCannotBypassCountRules(int target) {
        var f = new T03Fixtures.Library(); f.row.setAnzahlBaende(target);
        assertThrows(IllegalArgumentException.class, () -> f.series.saveMangaReihe(f.row));
        assertThrows(IllegalArgumentException.class, () -> f.series.createMangaReihe(f.row));
        verify(f.rows, never()).save(any()); verifyNoWritesExceptRow(f);
    }
    private static void verifyNoWritesExceptRow(T03Fixtures.Library f) {
        verify(f.bands, never()).save(any()); verify(f.bands, never()).delete(any());
        verify(f.bands, never()).deleteById(any()); verify(f.details, never()).save(any());
    }
    private static Map<Long, List<Object>> bandMetadata(T03Fixtures.Library f) {
        var values = new TreeMap<Long, List<Object>>();
        for (var band : f.stored) values.put(band.getId(), Arrays.asList(
                band.getBildUrl() == null ? null : band.getBildUrl().toExternalForm(),
                band.getMpUrl() == null ? null : band.getMpUrl().toExternalForm(),
                band.getErstelltAm(), band.getAktualisiertAm()));
        return values;
    }
}
