package de.mangalib.service;

import de.mangalib.controller.EinkaufslisteController;
import de.mangalib.entity.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** IST-Tests echter Servicepfade auf synthetischen Objektgraphen. Keine Proxys,
 * Transaktionen, Datenbank oder Scraper. Dokumentierte SOLL-Regeln stehen im T03-Plan,
 * nicht als deaktivierte/absichtlich fehlschlagende Tests im regulären Lauf. */
class BandIntegrityCharacterizationTest {
    @Test void repeatedConfirmedPurchaseDoesNotAppendTwiceThroughActualServices() {
        var f = new T03Fixtures.Library(); var before = f.state();
        var item = T03Fixtures.item(false);
        var shoppingRows = mock(de.mangalib.repository.EinkaufslisteRepository.class);
        when(shoppingRows.findByIdForPurchase(7L)).thenReturn(Optional.of(item));
        when(shoppingRows.save(any())).thenAnswer(i -> i.getArgument(0));
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of(f.row));
        when(f.rows.findByIdForPurchase(42L)).thenReturn(Optional.of(f.row));
        var controller = new EinkaufslisteController();
        ReflectionTestUtils.setField(controller, "mangaReiheService", f.series);
        ReflectionTestUtils.setField(controller, "einkaufslisteService", new EinkaufslisteService(shoppingRows));
        ReflectionTestUtils.setField(controller, "purchaseService", new PurchaseService(
                new LibraryWriteCoordinator(new FakeLibraryTransactionManager()), shoppingRows,
                mock(de.mangalib.repository.EinkaufslisteItemDetailsRepository.class), f.rows, f.bands, f.series));
        var confirmed = Map.<String,Object>of("itemId", "7", "confirmedSeriesId", "42", "firstBand", 4, "lastBand", 4);
        assertEquals(200, controller.buyItemUpdate(confirmed).getStatusCode().value());
        assertTrue(item.getGekauft()); assertEquals(4, f.stored.size());
        assertEquals(200, controller.buyItemUpdate(Map.of("itemId", "7")).getStatusCode().value());
        assertEquals(4, f.stored.size()); assertEquals(4, f.row.getAnzahlBaende());
        before.forEach((id, values) -> assertEquals(values, f.state().get(id)));
        assertFalse(f.row.getMangaDetails().isIstGelesen()); assertEquals(4, f.row.getMangaDetails().getReread());
        verify(shoppingRows, times(1)).save(item);
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "-0.01", "0.02"})
    void m06AppendDistributesExactAdjustmentOnlyToNewBands(String amount) {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertTrue(f.edit(6, new BigDecimal("6.00"), new BigDecimal(amount), Map.of()).isPresent());
        before.forEach((id, values) -> assertEquals(values, f.state().get(id)));
        BigDecimal sum = f.stored.subList(3, 6).stream().map(Band::getAenderungPreis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal(amount)), "Altbände erhalten; neue Anpassungen centgenau");
    }

    @ParameterizedTest @CsvSource({"0.01,0.01", "-0.01,-0.01", "0.02,0.02"})
    void creationPreservesEveryCent(String amount, String observedTotal) {
        var f = new T03Fixtures.Library(); f.stored.clear();
        f.bandService.createAndSaveBaende(f.row, 3, new BigDecimal("6.00"), new BigDecimal(amount), Map.of());
        assertEquals(3, f.stored.size());
        BigDecimal actual = f.stored.stream().map(Band::getAenderungPreis).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, actual.compareTo(new BigDecimal(observedTotal)));
        assertEquals(0, actual.compareTo(new BigDecimal(amount)));
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "-0.01", "0.02"})
    void existingThreeBandAdjustmentIsExactWithoutNonTerminatingDivision(String amount) {
        var f = new T03Fixtures.Library();
        assertDoesNotThrow(() -> f.bandService.updateOrCreateBaende(f.row, 3,
                new BigDecimal("7.00"), new BigDecimal(amount), Map.of(), false));
        assertEquals(new BigDecimal("7.00"), f.stored.getFirst().getPreis());
        assertEquals(new BigDecimal("7.00"), f.stored.get(1).getPreis());
        assertEquals(0, new BigDecimal(amount).compareTo(f.stored.stream().map(Band::getAenderungPreis)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        verify(f.bands, times(3)).save(any());
    }

    @Test void unsortedRepositoryListAssignsImportOrdinalToCorrectStableId() {
        var f = new T03Fixtures.Library(); Collections.reverse(f.stored);
        var third = f.stored.getFirst(); assertEquals(3, third.getBandNr());
        f.bandService.updateOrCreateBaende(f.row, 3, null, null,
                Map.of("Band 1 href", "https://example.invalid/volumes/8001"), false);
        assertEquals(103L, third.getId()); assertEquals(3, third.getBandNr());
        assertNull(third.getBandIndex(), "Bandnummer 3 bleibt unverändert");
        assertEquals(8001, f.stored.get(2).getBandIndex());
    }

    @Test void m06AppendRetainsEveryExistingIdentityAndValueDespiteUnsortedList() {
        var f = new T03Fixtures.Library(); Collections.reverse(f.stored);
        var before = f.state();
        assertTrue(f.edit(4, new BigDecimal("6.00"), BigDecimal.ZERO,
                Map.of("Band 1 Preis", "99.00", "Band 1 href", "https://example.invalid/volumes/8001")).isPresent());
        before.forEach((id, values) -> assertEquals(values, f.state().get(id)));
        assertEquals(4, f.stored.size()); var added = f.stored.getLast();
        assertEquals(4, added.getBandNr()); assertFalse(added.isIstGelesen()); assertNull(added.getReread());
        assertFalse(f.row.getMangaDetails().isIstGelesen()); assertEquals(4, f.row.getMangaDetails().getReread());
        before.keySet().forEach(id -> verify(f.bands, never()).save(argThat(b -> b != null && id.equals(b.getId()))));
    }

    @ParameterizedTest @ValueSource(strings = {"gap", "duplicate", "nullNumber", "foreignParent", "missingDetails"})
    void m06RejectsAmbiguousExtensionBeforeMutation(String fault) {
        var f = new T03Fixtures.Library();
        switch (fault) {
            case "gap" -> f.stored.getFirst().setBandNr(4);
            case "duplicate" -> f.stored.getFirst().setBandNr(2);
            case "nullNumber" -> f.stored.getFirst().setBandNr(null);
            case "foreignParent" -> f.stored.getFirst().setMangaReihe(T03Fixtures.row(99));
            case "missingDetails" -> f.row.setMangaDetails(null);
        }
        var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.edit(4, new BigDecimal("6.00"), BigDecimal.ZERO, Map.of()));
        assertEquals(before, f.state()); assertEquals(3, f.row.getAnzahlBaende());
        verify(f.bands, never()).save(any()); verify(f.rows, never()).save(any());
    }

    @Test void unchangedCountAndMetadataOnlyEditRetainIndividualBandValues() {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertTrue(f.edit(3, null, null, Map.of()).isPresent());
        assertEquals(before, f.state()); verify(f.bands, never()).save(any());
    }

    @Test void reductionRejectsBeforeChangingDeclaredCountOrAggregates() {
        var f = new T03Fixtures.Library(); var before = f.state();
        assertThrows(IllegalArgumentException.class, () -> f.edit(2, null, null, Map.of()));
        assertEquals(3, f.row.getAnzahlBaende()); assertEquals(3, f.stored.size());
        assertEquals(before, f.state()); assertEquals(new BigDecimal("18.00"), f.row.getGesamtpreis());
        assertEquals(new BigDecimal("6.00"), f.row.getPreisProBand());
        verify(f.bands, never()).delete(any()); verify(f.bands, never()).deleteById(any());
    }

    @Test void omittedSeriesPriceMeansKeepButZeroBandPriceIsAnExplicitChange() {
        var f = new T03Fixtures.Library();
        assertTrue(f.edit(3, null, null, Map.of()).isPresent());
        assertEquals(new BigDecimal("6.00"), f.stored.getFirst().getPreis());
        var input = new Band(); input.setId(101L); input.setPreis(new BigDecimal("0.00")); input.setIstGelesen(true);
        f.bandService.updateBand(input);
        assertEquals(new BigDecimal("0.00"), f.stored.getFirst().getPreis());
        assertEquals(new BigDecimal("12.00"), f.row.getGesamtpreis());
    }

    @Test void singleBandUpdateUsesStableIdEvenWhenRepositoryListIsReversed() {
        var f = new T03Fixtures.Library(); Collections.reverse(f.stored);
        var before = f.state();
        var input = new Band(); input.setId(102L); input.setPreis(new BigDecimal("8.00"));
        f.bandService.updateBand(input);
        assertEquals(new BigDecimal("8.00"), f.stored.get(1).getPreis());
        assertEquals(before.get(101L), f.state().get(101L)); assertEquals(before.get(103L), f.state().get(103L));
        assertEquals(2, f.stored.get(1).getBandNr());
    }

    @Test void istMissingBandIdIsASilentNoopAtServiceLevel() {
        var f = new T03Fixtures.Library(); var before = f.state();
        var input = new Band(); input.setId(999L);
        assertDoesNotThrow(() -> f.bandService.updateBand(input));
        assertEquals(before, f.state()); verify(f.bands, never()).save(any()); verify(f.rows, never()).save(any());
    }

    @Test void unknownBandPriceAllowsMetadataButRejectsFinancialChangeAndExtension() {
        var f = new T03Fixtures.Library(); f.stored.getFirst().setPreis(null);
        var before = f.state();
        assertTrue(f.edit(3, null, null, Map.of()).isPresent());
        assertEquals(before, f.state());
        assertThrows(IllegalArgumentException.class, () -> f.edit(3, new BigDecimal("7.00"), BigDecimal.ZERO, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> f.edit(4, new BigDecimal("6.00"), BigDecimal.ZERO, Map.of()));
        assertEquals(before, f.state());
        assertThrows(IllegalArgumentException.class, () -> f.bandService.calculateGesamtpreis(f.row));
    }

    @Test void unknownAdjustmentRejectsAggregationWithoutInventingAValue() {
        var f = new T03Fixtures.Library(); f.stored.getFirst().setAenderungPreis(null);
        assertThrows(IllegalArgumentException.class, () -> f.bandService.calculateAenderungGesamtpreis(f.row));
        assertNull(f.stored.getFirst().getAenderungPreis());
    }

    @Test void financialBandChangeIsRejectedBeforeMutationWhenSiblingPriceIsUnknown() {
        var f = new T03Fixtures.Library(); f.stored.get(1).setPreis(null); var before = f.state();
        var input = new Band(); input.setId(101L); input.setPreis(new BigDecimal("8.00"));
        assertThrows(IllegalArgumentException.class, () -> f.bandService.updateBand(input));
        assertEquals(before, f.state()); verify(f.bands, never()).save(any());
    }

    @Test void istAggregateSaveFailureFollowsBandSaveWithoutSharedMethodTransaction() throws Exception {
        var f = new T03Fixtures.Library();
        when(f.rows.save(any())).thenThrow(new IllegalStateException("SYNTHETIC_AGGREGATE_FAILURE"));
        var input = new Band(); input.setId(101L); input.setPreis(new BigDecimal("8.00")); input.setIstGelesen(true);
        assertThrows(IllegalStateException.class, () -> f.bandService.updateBand(input));
        var order = inOrder(f.bands, f.rows);
        order.verify(f.bands).save(f.stored.getFirst()); order.verify(f.rows).save(f.row);
        assertFalse(transactional(BandService.class.getMethod("updateBand", Band.class)));
        // SOLL: später atomarer Rollback. Ohne DB kann dieser Test keinen Teilcommit nachweisen.
    }

    @ParameterizedTest @ValueSource(strings = {"detail", "band"})
    void istSeriesCreationCanFailAfterEarlierRepositoryCalls(String phase) {
        var f = new T03Fixtures.Library();
        when(f.statuses.findById(1L)).thenReturn(Optional.of(f.row.getStatus()));
        when(f.formats.findById(1L)).thenReturn(Optional.of(f.row.getFormat()));
        when(f.types.findById(1L)).thenReturn(Optional.of(f.row.getTyp()));
        when(f.publishers.findById(1L)).thenReturn(Optional.of(f.row.getVerlag()));
        var details = mock(MangaDetailsService.class); var bands = mock(BandService.class);
        ReflectionTestUtils.setField(f.series, "mangaDetailsService", details);
        ReflectionTestUtils.setField(f.series, "bandService", bands);
        if (phase.equals("detail")) when(details.createMangaDetails(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("SYNTHETIC_DETAILS_FAILURE"));
        else {
            when(details.createMangaDetails(any(), any(), any(), any(), any())).thenReturn(f.row.getMangaDetails());
            doThrow(new IllegalStateException("SYNTHETIC_BANDS_FAILURE")).when(bands)
                    .createAndSaveBaende(any(), any(), any(), any(), any());
        }
        assertThrows(IllegalStateException.class, () -> f.series.saveMangaReihe(123, 1L, 1L, 1L, 1L,
                "T03 synthetische Neuanlage", 3, new BigDecimal("6.00"), false, false, null, 1L,
                BigDecimal.ZERO, false, Map.of()));
        verify(f.rows).save(any());
        if (phase.equals("detail")) verify(f.details, never()).save(any());
        else verify(f.details).save(any());
    }

    @Test void transactionAnnotationsDescribeSeparateBoundariesNotAnActualRollbackProof() throws Exception {
        assertFalse(transactional(EinkaufslisteController.class.getMethod("buyItemUpdate", Map.class)));
        assertFalse(transactional(EinkaufslisteController.class.getMethod("buyItemCreate", Map.class)));
        assertTrue(transactional(EinkaufslisteService.class.getMethod("updateGekauft", long.class, boolean.class)));
        assertTrue(Arrays.stream(MangaReiheService.class.getMethods()).filter(m -> m.getName().equals("updateMangaReihe"))
                .allMatch(BandIntegrityCharacterizationTest::transactional));
        assertTrue(Arrays.stream(MangaReiheService.class.getMethods())
                .filter(m -> m.getName().equals("saveMangaReihe") && m.getParameterCount() > 1)
                .allMatch(BandIntegrityCharacterizationTest::transactional));
        assertFalse(transactional(BandService.class.getMethod("updateBand", Band.class)));
    }

    private static boolean transactional(java.lang.reflect.Method method) {
        return method.isAnnotationPresent(jakarta.transaction.Transactional.class)
                || method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)
                || method.getDeclaringClass().isAnnotationPresent(jakarta.transaction.Transactional.class)
                || method.getDeclaringClass().isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class);
    }
}
