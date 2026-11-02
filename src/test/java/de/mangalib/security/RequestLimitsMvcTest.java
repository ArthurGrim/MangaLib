package de.mangalib.security;

import de.mangalib.controller.*;
import de.mangalib.entity.MangaReihe;
import de.mangalib.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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

/** Standalone-MVC mit echten Filtern und Controllern, nur Service-Doubles; kein Boot/JPA/HTTP-Listener. */
class RequestLimitsMvcTest {
    private final JsonMapper json = new JsonMapper();
    private MangaReiheService rows;
    private BandService bands;
    private EinkaufslisteService shopping;
    private ScraperGateway scraper;
    private MockMvc mvc;
    private MockHttpSession session;
    private String token;
    private final AtomicInteger downstream = new AtomicInteger();

    @BeforeEach void setup() {
        rows = mock(MangaReiheService.class); bands = mock(BandService.class);
        shopping = mock(EinkaufslisteService.class); scraper = mock(ScraperGateway.class);
        var main = new MainController(); var cart = new EinkaufslisteController();
        ReflectionTestUtils.setField(main, "mangaReiheService", rows);
        ReflectionTestUtils.setField(main, "bandService", bands);
        ReflectionTestUtils.setField(main, "scraperGateway", scraper);
        ReflectionTestUtils.setField(cart, "einkaufslisteService", shopping);
        ReflectionTestUtils.setField(cart, "mangaReiheService", rows);
        var csrf = new HttpSessionCsrfTokenRepository();
        session = new MockHttpSession(); var seed = new MockHttpServletRequest(); seed.setSession(session);
        var generated = csrf.generateToken(seed); csrf.saveToken(generated, seed, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(seed, new MockHttpServletResponse(), () -> generated);
        token = ((CsrfToken) seed.getAttribute(CsrfToken.class.getName())).getToken();
        mvc = MockMvcBuilders.standaloneSetup(main, cart)
                .addFilters(new RequestBodyLimitFilter(), (request, response, chain) -> {
                    downstream.incrementAndGet(); chain.doFilter(request, response);
                }, new LocalRequestFilter(8080), new CsrfFilter(csrf)).build();
    }

    private MockHttpServletRequestBuilder write(String method, String path, String type, String body) {
        var request = request(org.springframework.http.HttpMethod.valueOf(method), "http://localhost:8080" + path)
                .header("Host", "localhost:8080").header("Origin", "http://localhost:8080")
                .session(session).header("X-CSRF-TOKEN", token).content(body);
        if (type != null) request.contentType(type);
        return request;
    }
    private void noWork() { verifyNoInteractions(rows, bands, shopping, scraper); }

    @ParameterizedTest @ValueSource(strings = {"/addMangaReihe", "/editBand", "/addToEinkaufsliste", "/buyItemUpdate",
            "/buyItemCreate", "/scrape", "/autofillBandData/900001", "/items/1"})
    void rejectsEveryWritePathBeforeAnyDownstreamFilterOrService(String path) throws Exception {
        // Ein überlanges JSON-Feld genügt hier; echte Byte-Streaminggrenzen separat im Filtertest.
        var response = mvc.perform(write(path.startsWith("/items/") ? "DELETE" : "POST", path,
                "application/json", "{\"x\":\"" + "x".repeat(4097) + "\"}" )).andReturn().getResponse();
        assertEquals(413, response.getStatus()); assertEquals(0, downstream.get()); noWork();
    }

    @ParameterizedTest @ValueSource(strings = {"application/x-www-form-urlencoded", "multipart/form-data; boundary=synthetic", "application/json"})
    void unsupportedFormatsOrDuplicateKeysHaveNoSideEffects(String type) throws Exception {
        var response = mvc.perform(write("POST", "/buyItemUpdate", type, "{\"itemId\":1,\"itemId\":2}"))
                .andReturn().getResponse();
        assertEquals(type.equals("application/json") ? 400 : 415, response.getStatus());
        assertEquals(0, downstream.get()); noWork();
    }

    @ParameterizedTest @ValueSource(strings = {"text/plain", "application/octet-stream", "application/xml"})
    void existingUnsupportedMediaTypeContractStillReturns415(String type) throws Exception {
        assertEquals(415, mvc.perform(write("POST", "/addMangaReihe", type, "{}"))
                .andReturn().getResponse().getStatus()); noWork();
    }

    @Test void csrfHostAndOriginProtectionRemainEffectiveForAllowedSize() throws Exception {
        assertEquals(403, mvc.perform(post("http://localhost:8080/editBand").header("Host", "localhost:8080")
                .contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(write("POST", "/editBand", "application/json", "{}")
                .header("Origin", "http://foreign.invalid:8080")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("http://localhost:8080/editBand").header("Host", "foreign.invalid:8080")
                .contentType("application/json").content("{}")).andReturn().getResponse().getStatus()); noWork();
    }

    @Test void ordinaryBandPayloadAndEmpty204IncludingExplicitNullRemainIntact() throws Exception {
        var response = mvc.perform(write("POST", "/editBand", "application/json",
                "{\"id\":\"42\",\"bandNr\":\"1\",\"istGelesen\":false,\"reread\":null,\"istSpecial\":null}"))
                .andReturn().getResponse();
        assertEquals(204, response.getStatus()); assertEquals("", response.getContentAsString());
        assertNull(response.getContentType());
        var captured = org.mockito.ArgumentCaptor.forClass(de.mangalib.entity.Band.class);
        verify(bands).updateBand(captured.capture()); assertNull(captured.getValue().getReread());
        assertNull(captured.getValue().getIstSpecial()); verifyNoInteractions(rows, shopping, scraper);
    }

    @Test void emptyAutofillPostRetainsSyntheticServicePayload() throws Exception {
        when(bands.autofillBandData("900001")).thenReturn(Map.of("Preis", "6.99", "BildUrl", "http://localhost:8080/cover.svg"));
        var response = mvc.perform(write("POST", "/autofillBandData/900001", "application/json", ""))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("6.99", json.readTree(response.getContentAsString()).get("Preis").stringValue());
        verify(bands).autofillBandData("900001"); verifyNoInteractions(scraper, rows, shopping);
    }

    @Test void ordinaryBusinessValidationAndMissingPurchaseStillKeepTheirContracts() throws Exception {
        var invalid = mvc.perform(write("POST", "/addMangaReihe", "application/json", "{}"))
                .andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertEquals("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.", invalid.getContentAsString());
        var missing = mvc.perform(write("POST", "/buyItemUpdate", "application/json", "{\"itemId\":-1}"))
                .andReturn().getResponse();
        assertEquals(404, missing.getStatus()); noWork();
    }

    @Test void tenThousandVolumeImportWithLongUnicodeUrlsAndMaximumRereadStillPasses() throws Exception {
        var imported = new LinkedHashMap<String, String>(); imported.put("istEdit", "false");
        String url = "https://example.invalid/" + "😀".repeat(230); // Unter 255 Codepoints, über 900 UTF-8-Bytes.
        for (int number = 1; number <= 10_000; number++) {
            imported.put("Band " + number + " Bild Url", url);
            imported.put("Band " + number + " href", url);
            imported.put("Band " + number + " Preis", "6.99");
        }
        var input = new HashMap<String, Object>();
        input.put("statusId", "1"); input.put("verlagId", "1"); input.put("typId", "1"); input.put("formatId", "1");
        input.put("titel", "Synthetischer Grenzimport"); input.put("anzahlBaende", 10_000);
        input.put("reread", 10_000); input.put("preisProBand", "6.99"); input.put("scrapedData", imported);
        var saved = new MangaReihe(); saved.setId(42L);
        when(rows.saveMangaReihe(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);
        String body = json.writeValueAsString(input);
        assertTrue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < RequestBodyLimitFilter.MAX_BODY_BYTES);
        var response = mvc.perform(write("POST", "/addMangaReihe", "application/json", body)).andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertEquals(42, json.readTree(response.getContentAsString()).get("id").intValue());
        var calls = mockingDetails(rows).getInvocations().stream().filter(i -> i.getMethod().getName().equals("saveMangaReihe")).toList();
        assertEquals(1, calls.size()); assertEquals(Integer.valueOf(10_000), calls.getFirst().getArgument(6));
        assertEquals(Integer.valueOf(10_000), calls.getFirst().getArgument(14));
        assertEquals(30_001, ((Map<?, ?>) calls.getFirst().getArgument(15)).size());
        verifyNoInteractions(bands, shopping, scraper);
    }
}
