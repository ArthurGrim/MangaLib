package de.mangalibmanual;

import de.mangalib.controller.MainController;
import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Echter Controller-/Update-Pfad, aber ausschließlich lokale Objekte und Repository-Mocks.
 * Kein Spring-Kontext, keine EntityManager/DataSource und keine Netzwerkzugriffe. */
class SeriesEditRegressionTest {
    private final MangaReiheRepository seriesRepository = mock(MangaReiheRepository.class);
    private final MangaDetailsRepository detailsRepository = mock(MangaDetailsRepository.class);
    private final BandRepository bandRepository = mock(BandRepository.class);
    private final MangaReihe series = new MangaReihe();
    private final MangaDetails details = new MangaDetails();
    private final Band band = new Band();
    private final MainController controller = new MainController();

    SeriesEditRegressionTest() throws Exception {
        var service = new MangaReiheService(seriesRepository, mock(StatusRepository.class), mock(FormatRepository.class),
                mock(TypRepository.class), mock(VerlagRepository.class), bandRepository, detailsRepository);
        var detailsService = new MangaDetailsService();
        var bands = new BandService();
        ReflectionTestUtils.setField(detailsService, "mangaDetailsRepository", detailsRepository);
        ReflectionTestUtils.setField(bands, "bandRepository", bandRepository);
        ReflectionTestUtils.setField(service, "mangaDetailsService", detailsService);
        ReflectionTestUtils.setField(service, "bandService", bands);
        ReflectionTestUtils.setField(controller, "mangaReiheService", service);
        series.setId(2L); series.setMangaIndex(900002); series.setTitel("Alter Titel"); series.setAnzahlBaende(1);
        series.setPreisProBand(new BigDecimal("7.49")); series.setGesamtpreis(new BigDecimal("7.49"));
        series.setAenderungGesamtpreis(BigDecimal.ZERO); series.setErstelltAm(LocalDate.of(2025, 1, 1));
        var status = new Status(); status.setStatusId(1L); series.setStatus(status);
        var publisher = new Verlag(); publisher.setVerlagId(1L); series.setVerlag(publisher);
        var type = new Typ(); type.setTypId(1L); series.setTyp(type);
        var format = new Format(); format.setFormatId(1L); series.setFormat(format);
        series.setMangaDetails(details); details.setMangaReihe(series);
        details.setCoverUrl("http://localhost:18080/manual-test/cover.svg"); details.setAnilistUrl(null);
        details.setHerkunft("Japan"); details.setStatusDe("Abgeschlossen"); details.setStartJahr(2025);
        details.setIstGelesen(true); details.setReread(1);
        var collected = new Sammelband(); collected.setId(1L); details.setSammelbaende(collected);
        band.setId(3L); band.setMangaReihe(series); band.setBandNr(1); band.setBandIndex(900002);
        band.setPreis(new BigDecimal("7.49")); band.setAenderungPreis(BigDecimal.ZERO);
        band.setIstGelesen(true); band.setReread(1); band.setIstSpecial(null);
        band.setBildUrl(URI.create("http://localhost:18080/manual-test/cover.svg").toURL());
        band.setMpUrl(URI.create("http://localhost:18080/home?band=3").toURL());
        when(seriesRepository.findById(2L)).thenReturn(Optional.of(series));
        when(seriesRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(detailsRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(bandRepository.findByMangaReiheId(2L)).thenReturn(List.of(band));
    }

    private Map<String, Object> request(Boolean outOfPrint, Boolean adjustedPrice) {
        var data = new HashMap<String, Object>();
        data.put("mangaReiheId", "2"); data.put("mangaIndex", 900002);
        for (String key : List.of("statusId", "verlagId", "typId", "formatId", "sammelbandTypId")) data.put(key, "1");
        data.put("titel", "MANUELL O'Brien \"Test\" – neuer Titel"); data.put("anzahlBaende", 1);
        data.put("istVergriffen", outOfPrint); data.put("istEbayPreis", adjustedPrice);
        data.put("istGelesen", true); data.put("reread", 1);
        data.put("coverUrl", details.getCoverUrl()); data.put("anilistUrl", null);
        data.put("scrapedData", Map.of("istEdit", "true"));
        return data;
    }

    static Stream<Arguments> flags() {
        return Arrays.asList(null, false, true).stream().flatMap(a ->
                Arrays.asList(null, false, true).stream().map(b -> Arguments.of(a, b)));
    }

    @ParameterizedTest @MethodSource("flags")
    void titleOnlyEditPreservesNullableFlagsAndOtherFields(Boolean outOfPrint, Boolean adjustedPrice) {
        series.setIstVergriffen(outOfPrint); series.setIstEbayPreis(adjustedPrice);
        var image = band.getBildUrl(); var link = band.getMpUrl();
        var status = series.getStatus(); var publisher = series.getVerlag();
        var type = series.getTyp(); var format = series.getFormat(); var collected = details.getSammelbaende();
        var response = controller.addMangaReihe(request(outOfPrint, adjustedPrice));
        assertEquals(200, response.getStatusCode().value()); assertSame(series, response.getBody());
        assertEquals("MANUELL O'Brien \"Test\" – neuer Titel", series.getTitel());
        assertEquals(outOfPrint, series.getIstVergriffen()); assertEquals(adjustedPrice, series.getIstEbayPreis());
        assertEquals(900002, series.getMangaIndex()); assertEquals(1, series.getAnzahlBaende());
        assertEquals(new BigDecimal("7.49"), series.getPreisProBand());
        assertEquals(new BigDecimal("7.49"), series.getGesamtpreis()); assertEquals(BigDecimal.ZERO, series.getAenderungGesamtpreis());
        assertEquals(LocalDate.of(2025, 1, 1), series.getErstelltAm()); assertNotNull(series.getAktualisiertAm());
        assertSame(status, series.getStatus()); assertSame(publisher, series.getVerlag());
        assertSame(type, series.getTyp()); assertSame(format, series.getFormat()); assertSame(details, series.getMangaDetails());
        assertSame(collected, details.getSammelbaende()); assertNull(details.getAnilistUrl());
        assertEquals("http://localhost:18080/manual-test/cover.svg", details.getCoverUrl());
        assertEquals("Japan", details.getHerkunft()); assertEquals("Abgeschlossen", details.getStatusDe()); assertEquals(2025, details.getStartJahr());
        assertTrue(details.isIstGelesen()); assertEquals(1, details.getReread());
        assertSame(image, band.getBildUrl()); assertSame(link, band.getMpUrl()); assertNull(band.getIstSpecial());
        assertEquals(new BigDecimal("7.49"), band.getPreis()); assertEquals(BigDecimal.ZERO, band.getAenderungPreis());
        assertTrue(band.isIstGelesen()); assertEquals(1, band.getReread()); assertEquals(900002, band.getBandIndex());
        verify(seriesRepository).save(series); verify(bandRepository).save(band);
    }

    static Stream<Arguments> transitions() {
        return Stream.of(Arguments.of(null, false), Arguments.of(null, true), Arguments.of(false, true),
                Arguments.of(true, false), Arguments.of(false, null), Arguments.of(true, null));
    }
    @ParameterizedTest @MethodSource("transitions")
    void explicitBooleanChangesAreAppliedWithoutConflatingUnknown(Boolean previous, Boolean next) {
        series.setIstVergriffen(previous); series.setIstEbayPreis(previous);
        assertEquals(200, controller.addMangaReihe(request(next, next)).getStatusCode().value());
        assertEquals(next, series.getIstVergriffen()); assertEquals(next, series.getIstEbayPreis());
    }
    @Test void missingExistingSeriesDoesNotCreateRecords() {
        when(seriesRepository.findById(2L)).thenReturn(Optional.empty());
        assertNull(controller.addMangaReihe(request(null, null)).getBody());
        verify(seriesRepository, never()).save(any()); verifyNoInteractions(detailsRepository, bandRepository);
    }
    @Test void existingPriceAdjustmentStillSetsPriceFlag() {
        band.setAenderungPreis(new BigDecimal("0.01")); series.setAenderungGesamtpreis(new BigDecimal("0.01"));
        assertEquals(200, controller.addMangaReihe(request(null, null)).getStatusCode().value());
        assertTrue(series.getIstEbayPreis()); assertEquals(new BigDecimal("0.01"), series.getAenderungGesamtpreis());
    }
}
