package de.mangalib.validation;

import de.mangalib.controller.MainController;
import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.security.LocalRequestFilter;
import de.mangalib.service.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Vom historischen Ist auf die freigegebenen Soll-Regeln überführte Regressionen.
 * Echte Controller/Services, ausschließlich Repository-Mocks; kein Boot-Kontext,
 * JDBC, DNS, HTTP-Listener oder Scraper. Historische Abweichungen stehen in TESTING.md.
 * Mock-Mutationen sind kein Nachweis eines Datenbank-Commits oder Rollbacks.
 */
class LegacyEditCharacterizationTest {

    @ParameterizedTest @ValueSource(ints = {2, 5})
    void pureCountCorrectionThroughSecuredMvcPreservesUnknownPrices(int declared) throws Exception {
        row.setAnzahlBaende(declared); band.setPreis(null); row.setGesamtpreis(null);
        var input = seriesForm(read("/getMangaReiheData/42"));
        input.put("titel", row.getTitel()); input.put("extendVolumes", false);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(1, row.getAnzahlBaende()); assertNull(band.getPreis()); assertNull(row.getGesamtpreis());
        verify(volumes, never()).save(any()); verify(detailRows, never()).save(any());
    }
    private final JsonMapper json = new JsonMapper();
    private MangaReiheRepository rows;
    private BandRepository volumes;
    private MangaDetailsRepository detailRows;
    private MangaReiheService series;
    private BandService bands;
    private MangaReihe row;
    private MangaDetails details;
    private Band band;
    private MockMvc mvc;
    private MockHttpSession session;
    private String token;

    @BeforeEach void setup() throws Exception {
        rows = mock(MangaReiheRepository.class); volumes = mock(BandRepository.class);
        detailRows = mock(MangaDetailsRepository.class);
        series = new MangaReiheService(rows, mock(StatusRepository.class), mock(FormatRepository.class),
                mock(TypRepository.class), mock(VerlagRepository.class), volumes, detailRows);
        bands = new BandService();
        var detailService = new MangaDetailsService();
        ReflectionTestUtils.setField(detailService, "mangaDetailsRepository", detailRows);
        ReflectionTestUtils.setField(bands, "bandRepository", volumes);
        ReflectionTestUtils.setField(bands, "mangaReiheRepository", rows);
        ReflectionTestUtils.setField(bands, "mangaDetailsService", detailService);
        ReflectionTestUtils.setField(series, "bandService", bands);
        ReflectionTestUtils.setField(series, "mangaDetailsService", detailService);
        row = new MangaReihe(); row.setId(42L); row.setTitel("Synthetischer Altbestand");
        row.setAnzahlBaende(1); row.setPreisProBand(new BigDecimal("6.99"));
        row.setGesamtpreis(new BigDecimal("6.99")); row.setAenderungGesamtpreis(BigDecimal.ZERO);
        var status = new Status(); status.setStatusId(1L); row.setStatus(status);
        var publisher = new Verlag(); publisher.setVerlagId(1L); row.setVerlag(publisher);
        var type = new Typ(); type.setTypId(1L); row.setTyp(type);
        var format = new Format(); format.setFormatId(1L); row.setFormat(format);
        details = new MangaDetails(); details.setId(42L); details.setMangaReihe(row);
        var omnibus = new Sammelband(); omnibus.setId(1L); details.setSammelbaende(omnibus);
        row.setMangaDetails(details); details.setReread(0);
        band = new Band(); band.setId(11L); band.setBandNr(1); band.setMangaReihe(row);
        band.setPreis(new BigDecimal("6.99")); band.setAenderungPreis(BigDecimal.ZERO);
        band.setBildUrl(null); band.setReread(0); band.setIstSpecial(false);
        when(rows.findById(42L)).thenReturn(Optional.of(row));
        when(volumes.findById(11L)).thenReturn(Optional.of(band));
        when(volumes.findByMangaReiheId(42L)).thenReturn(List.of(band));
        when(rows.save(any())).thenAnswer(i -> i.getArgument(0));
        when(volumes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(detailRows.save(any())).thenAnswer(i -> i.getArgument(0));
        var controller = new MainController();
        ReflectionTestUtils.setField(controller, "mangaReiheService", series);
        ReflectionTestUtils.setField(controller, "bandService", bands);
        // Falls ein Test versehentlich einen Scraperpfad erreicht: sofort scheitern.
        var scraper = mock(ScraperGateway.class, i -> { throw new AssertionError("Kein Scraper erlaubt"); });
        ReflectionTestUtils.setField(controller, "scraperGateway", scraper);
        ReflectionTestUtils.setField(bands, "scraperGateway", scraper);
        var csrf = new HttpSessionCsrfTokenRepository();
        session = new MockHttpSession(); var seed = new MockHttpServletRequest(); seed.setSession(session);
        var generated = csrf.generateToken(seed);
        csrf.saveToken(generated, seed, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(seed, new MockHttpServletResponse(), () -> generated);
        token = ((CsrfToken) seed.getAttribute(CsrfToken.class.getName())).getToken();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new de.mangalib.security.RequestBodyLimitFilter(), new LocalRequestFilter(8080), new CsrfFilter(csrf)).build();
    }

    private MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host", "localhost:8080").with(r -> { r.setServerPort(8080); return r; });
    }
    private Map<String, Object> read(String path) throws Exception {
        var response = mvc.perform(local(get(path))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return json.readValue(response.getContentAsString(),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
    }
    private MockHttpServletResponse write(String path, Map<String, Object> input) throws Exception {
        return mvc.perform(local(post(path)).header("Origin", "http://localhost:8080")
                .session(session).header("X-CSRF-TOKEN", token).contentType("application/json")
                .content(json.writeValueAsString(input))).andReturn().getResponse();
    }
    private Map<String, Object> bandForm(Map<String, Object> data) {
        var input = new HashMap<String, Object>();
        input.put("id", data.get("id").toString()); input.put("bandNr", data.get("bandNr").toString());
        // Wie das echte Bandformular toFixed(2); insbesondere JSON 0.0 wird zu "0.00".
        input.put("preis", data.get("preis") == null ? null : new BigDecimal(data.get("preis").toString()).setScale(2).toPlainString());
        input.put("aenderungPreis", data.get("aenderungPreis") == null ? null
                : new BigDecimal(data.get("aenderungPreis").toString()).setScale(2).toPlainString());
        input.put("reread", Boolean.TRUE.equals(data.get("rereadNull")) ? null : data.get("reread").toString());
        input.put("istGelesen", data.get("istGelesen"));
        input.put("istSpecial", Boolean.TRUE.equals(data.get("specialNull")) ? null : data.get("istSpecial"));
        input.put("bildUrl", data.get("bildUrl")); input.put("mpUrl", data.get("mpUrl"));
        return input;
    }
    private Map<String, Object> seriesForm(Map<String, Object> data) {
        var input = new HashMap<String, Object>();
        input.put("mangaReiheId", "42"); input.put("titel", "Nur Titel geändert");
        input.put("anzahlBaende", 1); input.put("scrapedData", Map.of("istEdit", "true"));
        for (String key : List.of("statusId", "verlagId", "typId", "formatId"))
            input.put(key, data.get(key).toString());
        for (String key : List.of("reread", "istGelesen", "istVergriffen", "istEbayPreis", "anilistUrl", "coverUrl"))
            input.put(key, data.get(key));
        return input;
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3})
    void bandRoundTripPreservesUnknownRereadAndSpecialFlag(Integer reread) throws Exception {
        band.setReread(reread); band.setIstSpecial(null); details.setReread(7);
        var data = read("/getBand/11");
        assertEquals(reread == null ? 0 : reread, data.get("reread"));
        assertEquals(false, data.get("istSpecial"));
        assertEquals(204, write("/editBand", bandForm(data)).getStatus());
        assertEquals(reread, band.getReread());
        assertNull(band.getIstSpecial());
        assertEquals(7, details.getReread());
        verify(volumes).save(band);
    }

    @ParameterizedTest @NullSource @ValueSource(booleans = {false, true})
    void unchangedSeriesFlagsAreAlreadyPreserved(Boolean flag) throws Exception {
        row.setIstVergriffen(flag); row.setIstEbayPreis(flag);
        assertEquals(200, write("/addMangaReihe", seriesForm(read("/getMangaReiheData/42"))).getStatus());
        assertEquals(flag, row.getIstVergriffen()); assertEquals(flag, row.getIstEbayPreis());
        assertEquals("Nur Titel geändert", row.getTitel()); assertEquals(1, row.getAnzahlBaende());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3})
    void ordinaryTitleEditPreservesSeriesAndBandRereads(Integer reread) throws Exception {
        details.setReread(reread); band.setReread(null);
        assertEquals(200, write("/addMangaReihe", seriesForm(read("/getMangaReiheData/42"))).getStatus());
        assertEquals(reread, details.getReread());
        assertNull(band.getReread());
        assertEquals(1, row.getAnzahlBaende());
    }

    @ParameterizedTest @ValueSource(strings = {"anilistUrl", "coverUrl"})
    void unchangedLegacySeriesUrlSurvivesIndependentTitleEdit(String field) throws Exception {
        if (field.equals("anilistUrl")) details.setAnilistUrl("https://user@example.invalid/old");
        else details.setCoverUrl("/legacy/cover.png");
        var input = seriesForm(read("/getMangaReiheData/42"));
        org.mockito.Mockito.<Object>clearInvocations(rows, volumes, detailRows);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals("Nur Titel geändert", row.getTitel());
        assertEquals(field.equals("anilistUrl") ? "https://user@example.invalid/old" : "/legacy/cover.png",
                field.equals("anilistUrl") ? details.getAnilistUrl() : details.getCoverUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"file:///synthetic/old.png", "https://user@example.invalid/old"})
    void unchangedLegacyBandUrlIsPreserved(String legacy) throws Exception {
        band.setBildUrl(URI.create(legacy).toURL());
        String original = band.getBildUrl().toExternalForm();
        var input = bandForm(read("/getBand/11"));
        org.mockito.Mockito.<Object>clearInvocations(rows, volumes, detailRows);
        assertEquals(204, write("/editBand", input).getStatus());
        assertEquals(original, band.getBildUrl().toExternalForm());
    }

    @ParameterizedTest @ValueSource(strings = {"", "http://localhost:18080/synthetic/new.png"})
    void seriesUrlCanBeExplicitlyClearedOrReplaced(String replacement) throws Exception {
        details.setAnilistUrl("http://localhost:18080/synthetic/old");
        var input = seriesForm(read("/getMangaReiheData/42")); input.put("anilistUrl", replacement);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(replacement, details.getAnilistUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"", "http://localhost:18080/synthetic/new.png"})
    void bandUrlEmptyMeansKeepOldWhereasNewValidUrlReplacesIt(String replacement) throws Exception {
        String old = "http://localhost:18080/synthetic/old.png"; band.setBildUrl(URI.create(old).toURL());
        var input = bandForm(read("/getBand/11")); input.put("bildUrl", replacement);
        assertEquals(204, write("/editBand", input).getStatus());
        assertEquals(replacement.isEmpty() ? old : replacement, band.getBildUrl().toExternalForm());
        // Freigegeben: Leeren einer Band-URL bedeutet weiterhin unverändert.
    }

    @ParameterizedTest @ValueSource(strings = {"preis", "aenderungPreis"})
    void nullablePricesRemainUnknownAndBandListIsReadable(String field) throws Exception {
        if (field.equals("preis")) band.setPreis(null); else band.setAenderungPreis(null);
        var data = read("/getBand/11"); assertNull(data.get(field));
        var response = mvc.perform(local(get("/mangaReihe/42/bands"))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("Unbekannt", json.readTree(response.getContentAsString()).get(0).get("totalPreis").stringValue());
        verify(volumes, never()).save(any());
    }

    @Test void clearingKnownPriceIsRejectedWithoutMutation() throws Exception {
        var input = bandForm(read("/getBand/11")); input.put("preis", "");
        assertEquals(400, write("/editBand", input).getStatus());
        assertEquals(new BigDecimal("6.99"), band.getPreis());
    }

    @ParameterizedTest @ValueSource(strings = {"details", "omnibus"})
    void missingDetailsOrOmnibusReturnsControlledReadError(String missing) throws Exception {
        if (missing.equals("details")) row.setMangaDetails(null); else details.setSammelbaende(null);
        var response = mvc.perform(local(get("/getMangaReiheData/42"))).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertFalse(response.getContentAsString().contains("NullPointerException"));
        verify(rows, never()).save(any()); verify(volumes, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"status", "publisher", "type", "format"})
    void missingReferenceIsRejectedBeforeMutation(String missing) throws Exception {
        var input = seriesForm(read("/getMangaReiheData/42"));
        switch (missing) {
            case "status" -> row.setStatus(null);
            case "publisher" -> row.setVerlag(null);
            case "type" -> row.setTyp(null);
            case "format" -> row.setFormat(null);
            default -> throw new AssertionError(missing);
        }
        assertEquals(400, mvc.perform(local(get("/getMangaReiheData/42"))).andReturn().getResponse().getStatus());
        var response = write("/addMangaReihe", input);
        assertEquals(400, response.getStatus());
        assertEquals("Synthetischer Altbestand", row.getTitel());
        verify(rows, never()).save(any()); verify(volumes, never()).save(any());
    }

    @Test void ordinaryTitleEditOfPartialSeriesAlsoPropagatesRowReadFlag() throws Exception {
        band.setIstGelesen(true); details.setIstGelesen(false);
        var second = new Band(); second.setId(12L); second.setBandNr(2); second.setMangaReihe(row);
        second.setPreis(BigDecimal.ZERO); second.setIstGelesen(false); second.setReread(0);
        row.setAnzahlBaende(2);
        when(volumes.findByMangaReiheId(42L)).thenReturn(List.of(band, second));
        var input = seriesForm(read("/getMangaReiheData/42")); input.put("anzahlBaende", 2);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertTrue(band.isIstGelesen()); assertFalse(second.isIstGelesen());
        assertEquals(2, row.getAnzahlBaende());
        // Freigegeben: bewusste Massenaktionen benötigen die explizite Option.
        // M06-Erweiterungen werden weder geändert noch als gleiches Verhalten vorausgesetzt.
    }

    @Test void localAndCsrfFiltersAreActiveBeforeLegacyMutation() throws Exception {
        assertEquals(403, mvc.perform(local(post("/editBand")).contentType("application/json")
                .content("{}")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/getBand/11").header("Host", "foreign.invalid:8080"))
                .andReturn().getResponse().getStatus());
        verifyNoInteractions(rows, volumes, detailRows);
    }

    @ParameterizedTest @NullSource @ValueSource(booleans = {false, true})
    void nullableBandSpecialFlagSurvivesRoundTrip(Boolean flag) throws Exception {
        band.setIstSpecial(flag);
        assertEquals(204, write("/editBand", bandForm(read("/getBand/11"))).getStatus());
        assertEquals(flag, band.getIstSpecial());
    }

    @Test void newUnsafeUrlIsStillRejectedBeforeServiceWork() throws Exception {
        var input = bandForm(read("/getBand/11")); input.put("mpUrl", "javascript:alert(1)");
        org.mockito.Mockito.<Object>clearInvocations(rows, volumes, detailRows);
        assertEquals(400, write("/editBand", input).getStatus());
        verify(volumes, never()).save(any()); verify(rows, never()).save(any()); assertNull(band.getMpUrl());
    }

    @Test void normalEditWithMissingDetailsCannotCreateImplicitReplacement() throws Exception {
        var input = seriesForm(read("/getMangaReiheData/42")); row.setMangaDetails(null);
        assertEquals(400, write("/addMangaReihe", input).getStatus());
        verifyNoInteractions(detailRows); verify(rows, never()).save(any());
        verify(volumes, never()).save(any()); assertNull(row.getMangaDetails());
        assertEquals("Synthetischer Altbestand", row.getTitel());
    }

    @ParameterizedTest @ValueSource(strings = {"0.00", "-0.02", "6.99"})
    void existingSignedBandPricesAreNotChangedBySpecialFlagEdit(String price) throws Exception {
        band.setPreis(new BigDecimal(price));
        var input = bandForm(read("/getBand/11")); input.put("istSpecial", true);
        assertEquals(204, write("/editBand", input).getStatus());
        assertEquals(new BigDecimal(price), band.getPreis());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyExplicitReadBulkOptionChangesBandsEvenWhenRowValueAlreadyMatches(boolean apply) throws Exception {
        band.setIstGelesen(true); details.setIstGelesen(false);
        var input = seriesForm(read("/getMangaReiheData/42")); input.put("applyReadToVolumes", apply);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(!apply, band.isIstGelesen()); assertFalse(details.isIstGelesen());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyExplicitRereadBulkOptionTouchesUnknownBandReread(boolean apply) throws Exception {
        band.setReread(null); details.setReread(0);
        var input = seriesForm(read("/getMangaReiheData/42")); input.put("applyRereadToVolumes", apply);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(apply ? Integer.valueOf(0) : null, band.getReread());
    }

    @Test void changedRowRereadWithoutBulkDoesNotTouchBandReread() throws Exception {
        band.setReread(null); var input = seriesForm(read("/getMangaReiheData/42")); input.put("reread", 3);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(3, details.getReread()); assertNull(band.getReread());
    }

    @Test void explicitZeroCanReplaceUnknownRowRereadWithoutTouchingBands() throws Exception {
        details.setReread(null); band.setReread(null);
        var input = seriesForm(read("/getMangaReiheData/42")); input.put("rereadEdited", true);
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(0, details.getReread()); assertNull(band.getReread());
    }

    @Test void importCannotSmuggleBulkInstructions() throws Exception {
        band.setIstGelesen(true); band.setReread(null);
        var input = seriesForm(read("/getMangaReiheData/42"));
        input.put("scrapedData", Map.of("istEdit", "true", "applyReadToVolumes", "true", "applyRereadToVolumes", "true"));
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertTrue(band.isIstGelesen()); assertNull(band.getReread());
    }

    @ParameterizedTest @ValueSource(strings = {"preis", "aenderungPreis"})
    void unrelatedBandEditPreservesUnknownPricesAndExistingSums(String field) throws Exception {
        if (field.equals("preis")) band.setPreis(null); else band.setAenderungPreis(null);
        row.setGesamtpreis(new BigDecimal("43.21"));
        var input = bandForm(read("/getBand/11")); input.put("istSpecial", true);
        assertEquals(204, write("/editBand", input).getStatus());
        assertNull(field.equals("preis") ? band.getPreis() : band.getAenderungPreis());
        assertEquals(new BigDecimal("43.21"), row.getGesamtpreis());
    }

    @Test void titleEditWithUnknownBandPriceDoesNotInventTotalsOrSaveBands() throws Exception {
        band.setPreis(null); row.setGesamtpreis(null);
        assertEquals(200, write("/addMangaReihe", seriesForm(read("/getMangaReiheData/42"))).getStatus());
        assertNull(band.getPreis()); assertNull(row.getGesamtpreis()); verify(volumes, never()).save(any());
    }

    @Test void financialEditWithUnresolvedNullAdjustmentIsRejectedBeforeMutation() throws Exception {
        band.setAenderungPreis(null);
        var input = bandForm(read("/getBand/11")); input.put("preis", "8.99");
        assertEquals(400, write("/editBand", input).getStatus());
        assertEquals(new BigDecimal("6.99"), band.getPreis()); assertNull(band.getAenderungPreis());
        verify(volumes, never()).save(any()); verify(rows, never()).save(any());
    }

    @Test void bandEditWithMissingDetailsIsRejectedBeforeAnyBandMutation() throws Exception {
        var input = bandForm(read("/getBand/11")); input.put("istSpecial", true); row.setMangaDetails(null);
        assertEquals(400, write("/editBand", input).getStatus());
        assertEquals(false, band.getIstSpecial()); verify(volumes, never()).save(any()); verifyNoInteractions(detailRows);
    }

    @Test void forgedNewInvalidUrlCannotUseHistoricalValueException() throws Exception {
        details.setCoverUrl("/old"); var input = seriesForm(read("/getMangaReiheData/42"));
        input.put("coverUrl", "/different"); input.put("applyReadToVolumes", true);
        assertEquals(400, write("/addMangaReihe", input).getStatus());
        assertEquals("/old", details.getCoverUrl()); assertEquals("Synthetischer Altbestand", row.getTitel());
        verify(rows, never()).save(any()); verify(volumes, never()).save(any());
    }

    @Test void explicitPriceImportStillRecalculatesKnownSeriesTotals() throws Exception {
        row.setGesamtpreis(new BigDecimal("43.21"));
        var input = seriesForm(read("/getMangaReiheData/42"));
        input.put("scrapedData", Map.of("istEdit", "true", "Band 1 Preis", "6.99"));
        assertEquals(200, write("/addMangaReihe", input).getStatus());
        assertEquals(new BigDecimal("6.99"), row.getGesamtpreis());
    }

    @Test void priceImportWithUnknownExistingPricesIsRejectedBeforeMutation() throws Exception {
        band.setPreis(null); var input = seriesForm(read("/getMangaReiheData/42"));
        input.put("scrapedData", Map.of("istEdit", "true", "Band 1 Preis", "6.99"));
        assertEquals(400, write("/addMangaReihe", input).getStatus());
        assertNull(band.getPreis()); assertEquals("Synthetischer Altbestand", row.getTitel());
        verify(rows, never()).save(any()); verify(volumes, never()).save(any());
    }
}
