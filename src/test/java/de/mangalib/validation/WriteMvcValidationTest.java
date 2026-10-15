package de.mangalib.validation;

import de.mangalib.controller.MainController;
import de.mangalib.controller.EinkaufslisteController;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.MangaDetails;
import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Format;
import de.mangalib.entity.Sammelband;
import de.mangalib.service.*;
import de.mangalib.security.LocalRequestFilter;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Standalone MVC mit echten Host-/Origin-/Session-CSRF-Filtern und Service-Mocks.
 * Kein Boot-Kontext, keine DataSource, kein Listener, kein echter Scraper. */
class WriteMvcValidationTest {
    private MockMvc mvc;
    private MockHttpSession session;
    private String token;
    private final JsonMapper mapper = new JsonMapper();
    private MangaReiheService series;
    private BandService bands;
    private EinkaufslisteService shopping;
    private EinkaufslisteItemDetailsService details;
    private ScraperGateway scraper;
    private VerlagService publishers;
    private TypService types;
    private FormatService formats;

    @BeforeEach void setup() {
        var main = new MainController(); var cart = new EinkaufslisteController();
        series = mock(MangaReiheService.class); bands = mock(BandService.class);
        shopping = mock(EinkaufslisteService.class); details = mock(EinkaufslisteItemDetailsService.class);
        scraper = mock(ScraperGateway.class); publishers = mock(VerlagService.class);
        types = mock(TypService.class); formats = mock(FormatService.class);
        ReflectionTestUtils.setField(main, "mangaReiheService", series);
        ReflectionTestUtils.setField(main, "bandService", bands);
        ReflectionTestUtils.setField(main, "scraperGateway", scraper);
        ReflectionTestUtils.setField(main, "verlagService", publishers);
        ReflectionTestUtils.setField(main, "typService", types);
        ReflectionTestUtils.setField(main, "formatService", formats);
        ReflectionTestUtils.setField(cart, "mangaReiheService", series);
        ReflectionTestUtils.setField(cart, "einkaufslisteService", shopping);
        ReflectionTestUtils.setField(cart, "einkaufslisteItemDetailsService", details);
        var csrf = new HttpSessionCsrfTokenRepository();
        var request = new MockHttpServletRequest(); session = new MockHttpSession(); request.setSession(session);
        var generated = csrf.generateToken(request); csrf.saveToken(generated, request, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> generated);
        token = ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
        mvc = MockMvcBuilders.standaloneSetup(main, cart)
                .addFilters(new LocalRequestFilter(8080), new CsrfFilter(csrf)).build();
    }

    private MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host", "localhost:8080").with(r -> { r.setServerPort(8080); return r; });
    }
    private MockHttpServletRequestBuilder write(String path) {
        return local(post(path)).header("Origin", "http://localhost:8080").session(session)
                .header("X-CSRF-TOKEN", token).contentType("application/json");
    }
    private Map<String, Object> seriesInput() {
        var input = new HashMap<String, Object>();
        input.put("statusId", "1"); input.put("verlagId", "1"); input.put("typId", "1"); input.put("formatId", "1");
        input.put("titel", "ÄÖÜ 漫画 & <img onerror='alert(1)'>"); input.put("anzahlBaende", 2);
        input.put("preisProBand", "5.99"); input.put("gesamtpreisAenderung", "-0.02");
        input.put("scrapedData", Map.of("istEdit", "false")); return input;
    }
    private Map<String, Object> bandInput() {
        return new HashMap<>(Map.of("id", "1", "bandNr", "1", "preis", "6.01", "aenderungPreis", "-0.02",
                "istGelesen", false, "istSpecial", true, "reread", "3"));
    }
    private void noWork() { verifyNoInteractions(series, bands, shopping, details, scraper, publishers, types, formats); }
    private void bad(String path, Map<String, Object> input) throws Exception {
        var result = mvc.perform(write(path).content(mapper.writeValueAsString(input))).andReturn();
        assertEquals(400, result.getResponse().getStatus()); noWork();
    }

    @ParameterizedTest @ValueSource(strings = {"statusId", "verlagId", "typId", "formatId", "titel", "anzahlBaende"})
    void rejectsMissingSeriesFieldsBeforeAnyService(String field) throws Exception {
        var input = seriesInput(); input.remove(field); bad("/addMangaReihe", input);
    }

    @ParameterizedTest @ValueSource(strings = {"statusId", "verlagId", "typId", "formatId", "mangaIndex", "anzahlBaende", "reread",
            "titel", "istGelesen", "istVergriffen", "istEbayPreis", "anilistUrl", "coverUrl", "sammelbandTypId", "preisProBand", "gesamtpreisAenderung", "scrapedData"})
    void rejectsWrongJsonStructuresWithoutCoercion(String field) throws Exception {
        var input = seriesInput(); input.put(field, Map.of("nested", Map.of("value", "untrusted"))); bad("/addMangaReihe", input);
    }

    @ParameterizedTest @ValueSource(strings = {"statusId", "anzahlBaende", "reread", "istGelesen", "preisProBand"})
    void rejectsWrongPrimitiveJsonTypes(String field) throws Exception {
        var input = seriesInput();
        input.put(field, switch (field) {
            case "statusId", "preisProBand" -> 1;
            case "anzahlBaende", "reread" -> "2";
            default -> "false";
        });
        bad("/addMangaReihe", input);
    }

    @Test void editRequiresIdAndReadFlagButPreservesOptionalPriceAndNullFlags() throws Exception {
        var input = seriesInput(); input.put("scrapedData", Map.of("istEdit", "true"));
        bad("/addMangaReihe", input);
        input.put("mangaReiheId", "1"); bad("/addMangaReihe", input);
        input.put("istGelesen", false); input.remove("preisProBand");
        input.put("reread", null); input.put("istVergriffen", null); input.put("istEbayPreis", null);
        when(series.updateMangaReihe(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        var response = mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(series).updateMangaReihe(eq(1L), isNull(), eq(1L), eq(1L), eq(1L), eq(1L), eq((String) input.get("titel")),
                eq(2), eq(java.math.BigDecimal.ZERO), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(new java.math.BigDecimal("-0.02")), eq(false), eq(0), eq(Map.of("istEdit", "true")));
    }

    @Test void missingOptionalBandFieldsKeepNullAndZeroSemantics() throws Exception {
        var input = bandInput(); input.remove("preis"); input.remove("aenderungPreis"); input.remove("reread");
        input.put("bildUrl", ""); input.put("mpUrl", null); input.put("istSpecial", null);
        assertEquals(204, mvc.perform(write("/editBand").content(mapper.writeValueAsString(input))).andReturn().getResponse().getStatus());
        var capture = org.mockito.ArgumentCaptor.forClass(de.mangalib.entity.Band.class);
        verify(bands).updateBand(capture.capture());
        assertNull(capture.getValue().getPreis()); assertNull(capture.getValue().getAenderungPreis());
        assertNull(capture.getValue().getBildUrl()); assertNull(capture.getValue().getMpUrl());
        assertNull(capture.getValue().getIstSpecial()); assertEquals(0, capture.getValue().getReread());
    }

    @Test void regularShoppingCreationKeepsSignedAdjustmentDateAndJsonResponse() throws Exception {
        var input = seriesInput(); input.put("erscheinungsdatum", "2024-02-29");
        var saved = new EinkaufslisteItem(); saved.setId(42L); saved.setTitel((String) input.get("titel"));
        when(shopping.saveEinkaufslisteItem(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(saved);
        var response = mvc.perform(write("/addToEinkaufsliste").content(mapper.writeValueAsString(input))).andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals(42, mapper.readTree(response.getContentAsString()).get("id").intValue());
        assertEquals(input.get("titel"), mapper.readTree(response.getContentAsString()).get("titel").stringValue());
        verify(shopping).saveEinkaufslisteItem(isNull(), eq(1L), eq(1L), eq(1L), eq((String) input.get("titel")), eq(2),
                eq(5.99d), isNull(), isNull(), isNull(), isNull(), eq(-0.02d), eq(java.time.LocalDate.of(2024, 2, 29)),
                eq(Map.of("istEdit", "false")));
    }

    @Test void seriesInputAndUnexpectedServiceErrorsDoNotExposeInternals() throws Exception {
        var input = seriesInput();
        doThrow(new IllegalArgumentException("private-password-and-internal-path")).when(series).saveMangaReihe(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        var response = mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input))).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertEquals("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.", response.getContentAsString());
        doThrow(new IllegalStateException("private-password-and-internal-path")).when(series).saveMangaReihe(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        response = mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input))).andReturn().getResponse();
        assertEquals(500, response.getStatus()); assertEquals("Anfrage konnte nicht verarbeitet werden.", response.getContentAsString());
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\t\n"})
    void blankTitleIsRejectedOnBothCreationPaths(String title) throws Exception {
        var input = seriesInput(); input.put("titel", title);
        bad("/addMangaReihe", input); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "not-an-id", "9223372036854775808"})
    void rejectsInvalidReferenceIds(String id) throws Exception {
        var input = seriesInput(); input.put("verlagId", id); bad("/addMangaReihe", input); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 10_001, Integer.MAX_VALUE})
    void rejectsCountBeforeEitherSavePath(int count) throws Exception {
        var input = seriesInput(); input.put("anzahlBaende", count); bad("/addMangaReihe", input); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(ints = {1, 10_000})
    void bothCountBoundariesReachServiceWithOriginalTitleAndSignedPrice(int count) throws Exception {
        var input = seriesInput(); input.put("anzahlBaende", count);
        assertEquals(200, mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input))).andReturn().getResponse().getStatus());
        verify(series).saveMangaReihe(isNull(), eq(1L), eq(1L), eq(1L), eq(1L), eq((String) input.get("titel")), eq(count),
                eq(new java.math.BigDecimal("5.99")), isNull(), isNull(), isNull(), isNull(),
                eq(new java.math.BigDecimal("-0.02")), isNull(), eq(0), eq(Map.of("istEdit", "false")));
        verifyNoInteractions(bands, shopping, scraper);
    }

    @Test void nullFlagsUrlsAndAbsentPricesKeepCreationSemantics() throws Exception {
        var input = seriesInput(); input.remove("preisProBand"); input.remove("gesamtpreisAenderung");
        input.put("anilistUrl", ""); input.put("istVergriffen", null); input.put("istEbayPreis", null);
        var parsed = WriteRequests.Series.from(input);
        assertEquals(java.math.BigDecimal.ZERO, parsed.price()); assertEquals(java.math.BigDecimal.ZERO, parsed.adjustment());
        assertNull(parsed.outOfPrint()); assertNull(parsed.adjustedPrice()); assertEquals("", parsed.anilist());
        assertEquals(0, parsed.reread());
        assertEquals(200, mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input))).andReturn().getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null", "0", "1", "10000"})
    void creationForwardsValidatedRereadToSaveService(String value) throws Exception {
        var input = seriesInput();
        if (!value.equals("missing")) input.put("reread", value.equals("null") ? null : Integer.valueOf(value));
        int expected = value.equals("missing") || value.equals("null") ? 0 : Integer.parseInt(value);
        assertEquals(200, mvc.perform(write("/addMangaReihe").content(mapper.writeValueAsString(input)))
                .andReturn().getResponse().getStatus());
        verify(series).saveMangaReihe(isNull(), eq(1L), eq(1L), eq(1L), eq(1L), eq((String) input.get("titel")),
                eq(2), eq(new java.math.BigDecimal("5.99")), isNull(), isNull(), isNull(), isNull(),
                eq(new java.math.BigDecimal("-0.02")), isNull(), eq(expected), eq(Map.of("istEdit", "false")));
        verifyNoMoreInteractions(series);
        verifyNoInteractions(bands, shopping, scraper);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 10_001})
    void invalidRereadRejectedBeforeSeriesAndBandUpdates(int value) throws Exception {
        var input = seriesInput(); input.put("reread", value); bad("/addMangaReihe", input);
        var band = bandInput(); band.put("reread", Integer.toString(value)); bad("/editBand", band);
    }

    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "/relative", "https://user:password@example.invalid/a", "ftp://example.invalid/a"})
    void validatesEveryAcceptedUrlBeforeWrites(String url) throws Exception {
        for (String field : List.of("anilistUrl", "coverUrl")) {
            var input = seriesInput(); input.put(field, url); bad("/addMangaReihe", input);
        }
        var input = seriesInput(); input.put("anilistUrl", url); bad("/addToEinkaufsliste", input);
        for (String field : List.of("bildUrl", "mpUrl")) {
            var band = bandInput(); band.put(field, url); bad("/editBand", band);
        }
        input = seriesInput(); input.put("scrapedData", Map.of("Band 1 Bild Url", url));
        bad("/addMangaReihe", input); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(strings = {"id", "bandNr", "istGelesen"})
    void bandRequiredFieldsAreChecked(String field) throws Exception {
        var input = bandInput(); input.remove(field); bad("/editBand", input);
    }

    @Test void normalBandEditStillReturnsEmpty204() throws Exception {
        var input = bandInput(); input.put("bildUrl", "https://example.invalid/cover.jpg"); input.put("mpUrl", "http://example.invalid/volumes/2");
        var response = mvc.perform(write("/editBand").content(mapper.writeValueAsString(input))).andReturn().getResponse();
        assertEquals(204, response.getStatus()); assertEquals("", response.getContentAsString()); assertNull(response.getContentType());
        var capture = org.mockito.ArgumentCaptor.forClass(de.mangalib.entity.Band.class);
        verify(bands).updateBand(capture.capture());
        assertEquals(new java.math.BigDecimal("-0.02"), capture.getValue().getAenderungPreis());
        assertEquals(3, capture.getValue().getReread()); assertEquals(Boolean.TRUE, capture.getValue().getIstSpecial());
        assertEquals("https://example.invalid/cover.jpg", capture.getValue().getBildUrl().toExternalForm());
    }

    @ParameterizedTest @ValueSource(strings = {"verlagId", "typId", "formatId", "preisProBand", "gesamtpreisAenderung", "anzahlBaende"})
    void shoppingRequiredFieldsAreChecked(String field) throws Exception {
        var input = seriesInput(); input.remove(field); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(strings = {"NaN", "Infinity", "1e999999", "100000000"})
    void invalidPriceIsRejectedBeforeServices(String price) throws Exception {
        var input = seriesInput(); input.put("preisProBand", price);
        bad("/addMangaReihe", input); bad("/addToEinkaufsliste", input);
        var band = bandInput(); band.put("preis", price); bad("/editBand", band);
    }

    @Test void invalidDateCannotReachShoppingService() throws Exception {
        var input = seriesInput(); input.put("erscheinungsdatum", "2024-02-30"); bad("/addToEinkaufsliste", input);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"itemId\":true}", "{\"itemId\":1.5}", "{\"itemId\":{}}", "{\"itemId\":\"abc\"}"})
    void malformedPurchaseIdsAreRejectedWithoutLookup(String input) throws Exception {
        for (String route : List.of("/buyItemUpdate", "/buyItemCreate")) {
            assertEquals(400, mvc.perform(write(route).content(input)).andReturn().getResponse().getStatus());
        }
        noWork();
    }

    @Test void purchaseCreationRequiresValidStatusBeforeLookup() throws Exception {
        for (String input : List.of("{\"itemId\":1}", "{\"itemId\":1,\"statusId\":0}", "{\"itemId\":1,\"statusId\":true}")) {
            assertEquals(400, mvc.perform(write("/buyItemCreate").content(input)).andReturn().getResponse().getStatus());
        }
        noWork();
    }

    @Test void existingNegativePurchaseIdContractIs404WithoutLookup() throws Exception {
        assertEquals(404, mvc.perform(write("/buyItemUpdate").content("{\"itemId\":\"-1\"}")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(write("/buyItemCreate").content("{\"itemId\":-1,\"statusId\":1}")).andReturn().getResponse().getStatus()); noWork();
    }

    @Test void combinedPurchaseCountIsCheckedBeforeUpdateOrPurchaseMarker() throws Exception {
        var item = new EinkaufslisteItem(); item.setId(1L); item.setMangaIndex(123); item.setAnzahlBaende(1);
        var row = mock(MangaReihe.class, RETURNS_DEEP_STUBS);
        when(row.getAnzahlBaende()).thenReturn(WriteValidation.MAX_VOLUMES);
        when(shopping.findItemById(1L)).thenReturn(Optional.of(item));
        when(series.findByMangaIndex(123)).thenReturn(List.of(row));
        var response = mvc.perform(write("/buyItemUpdate").content("{\"itemId\":\"1\"}")).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        verify(series, never()).updateMangaReihe(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(shopping, never()).updateGekauft(anyLong(), anyBoolean());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"mangaIndex\":1}", "{\"mangaIndex\":false}", "{\"mangaIndex\":\"\"}", "{\"mangaIndex\":\"-1\"}", "{\"mangaIndex\":\"2147483648\"}"})
    void invalidSeriesImportCannotStartScraper(String input) throws Exception {
        assertEquals(400, mvc.perform(write("/scrape").content(input)).andReturn().getResponse().getStatus()); noWork();
    }

    @ParameterizedTest @ValueSource(strings = {"zero", "0", "-1", "2147483648"})
    void invalidVolumeAutofillCannotCallBandService(String index) throws Exception {
        assertEquals(400, mvc.perform(write("/autofillBandData/" + index)).andReturn().getResponse().getStatus()); noWork();
    }

    @Test void unsafeScraperOutputCannotReachReferenceLookups() throws Exception {
        when(scraper.scrapeSeries("123")).thenReturn(Map.of("Band 1 Bild Url", "javascript:alert(1)"));
        assertEquals(400, mvc.perform(write("/scrape").content("{\"mangaIndex\":\"123\"}")).andReturn().getResponse().getStatus());
        verify(scraper).scrapeSeries("123"); verifyNoInteractions(publishers, types, formats, series, bands, shopping);
    }

    @Test void missingOptionalScraperMetadataIsReturnedWithoutLookupOrNpe() throws Exception {
        when(scraper.scrapeSeries("123")).thenReturn(Map.of("Titel", "Test"));
        var response = mvc.perform(write("/scrape").content("{\"mangaIndex\":\"123\"}")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("Test", mapper.readTree(response.getContentAsString()).get("Titel").stringValue());
        verifyNoInteractions(publishers, types, formats);
    }

    @Test void unexpectedScraperExceptionIsNeutral500() throws Exception {
        when(scraper.scrapeSeries("123")).thenThrow(new IllegalStateException("private-password-and-internal-path"));
        var response = mvc.perform(write("/scrape").content("{\"mangaIndex\":\"123\"}")).andReturn().getResponse();
        assertEquals(500, response.getStatus()); assertEquals("", response.getContentAsString());
    }

    @Test void serviceValidationExceptionDoesNotExposeItsMessage() throws Exception {
        doThrow(new IllegalArgumentException("private-password-and-internal-path")).when(bands).updateBand(any());
        var response = mvc.perform(write("/editBand").content(mapper.writeValueAsString(bandInput()))).andReturn().getResponse();
        assertEquals(400, response.getStatus()); assertEquals("", response.getContentAsString());
    }

    @Test void unexpectedPurchaseExceptionIsNeutral500() throws Exception {
        when(shopping.findItemById(1L)).thenThrow(new IllegalStateException("private-password-and-internal-path"));
        for (String route : List.of("/buyItemUpdate", "/buyItemCreate")) {
            var response = mvc.perform(write(route).content("{\"itemId\":\"1\",\"statusId\":\"1\"}")).andReturn().getResponse();
            assertEquals(500, response.getStatus()); assertEquals("Anfrage konnte nicht verarbeitet werden.", response.getContentAsString());
        }
        verifyNoInteractions(series, details);
    }

    @Test void securityFiltersStillRejectBeforeInputProcessing() throws Exception {
        assertEquals(403, mvc.perform(local(post("/addMangaReihe")).contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(write("/scrape").header("Origin", "http://evil.invalid").content("{\"mangaIndex\":\"1\"}")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/editBand").header("Host", "evil.invalid:8080")).andReturn().getResponse().getStatus()); noWork();
    }

    @Test void autofillReadMethodsAndOptionsRemainSideEffectFree() throws Exception {
        assertEquals(405, mvc.perform(local(get("/autofillBandData/123"))).andReturn().getResponse().getStatus());
        assertEquals(405, mvc.perform(local(head("/autofillBandData/123"))).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(local(options("/autofillBandData/123"))).andReturn().getResponse().getStatus()); noWork();
    }
}
