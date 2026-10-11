package de.mangalib;

import de.mangalib.controller.MainController;
import de.mangalib.controller.EinkaufslisteController;
import de.mangalib.controller.StatistikController;
import de.mangalib.service.BandScraper;
import de.mangalib.service.MangaScraper;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Reale Sicherheitsfilter; keine Schreibfixtures und ausschließlich statisch ersetzte Scraper. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class LocalSecurityMvcTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;
    @MockitoSpyBean MainController main;
    @MockitoSpyBean EinkaufslisteController shopping;
    @MockitoSpyBean StatistikController statistics;
    private TestDatabaseSnapshot.Snapshot before;
    private MockedStatic<BandScraper> bandScraper;
    private MockedStatic<MangaScraper> mangaScraper;

    @BeforeEach
    void preserveDataAndReplaceEveryScraper() {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        before = TestDatabaseSnapshot.capture(em);
        bandScraper = mockStatic(BandScraper.class);
        mangaScraper = mockStatic(MangaScraper.class);
        clearInvocations(main, shopping, statistics);
    }

    @AfterEach
    void verifyUnchangedRowsAndSchema() {
        try {
            assertEquals(before, TestDatabaseSnapshot.capture(em), "Sicherheitstests müssen Zeilen und Schema erhalten.");
        } finally {
            if (bandScraper != null) bandScraper.close();
            if (mangaScraper != null) mangaScraper.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/einkaufsliste"})
    void rendersSessionTokenAndHelperWithoutLogin(String path) throws Exception {
        var result = mvc.perform(local(get(path))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var html = result.getResponse().getContentAsString();
        assertTrue(html.contains("name=\"_csrf\" content=\""));
        assertTrue(html.contains("name=\"_csrf_header\" content=\"X-CSRF-TOKEN\""));
        assertTrue(html.indexOf("/js/security.js") < html.indexOf(path.equals("/home")
                ? "/js/popup_add_reihe.js" : "/js/einkaufsliste.js"));
        assertNotNull(result.getRequest().getSession(false));
        assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty(), "Kein generierter Standardbenutzer.");
        assertEquals("DENY", result.getResponse().getHeader("X-Frame-Options"));
        assertEquals("nosniff", result.getResponse().getHeader("X-Content-Type-Options"));
        noScraping();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/js/security.js", "/actuator/health", "/autofillBandData/123"})
    void rejectsForeignHostAcrossViewsAssetsActuatorAndScraping(String path) throws Exception {
        reject(get("http://localhost:8080" + path).header("Host", "localhost.evil:8080"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/addMangaReihe", "/editBand", "/addToEinkaufsliste",
            "/buyItemUpdate", "/buyItemCreate", "/scrape", "/autofillBandData/123"})
    void rejectsMissingTokenEvenWithoutOriginBeforeController(String path) throws Exception {
        reject(local(post(path)).contentType("application/json").content("{}"));
    }

    @Test
    void rejectsDeleteWithoutToken() throws Exception {
        reject(local(delete("/items/-1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "http://evil:8080", "http://localhost:8081", "http://127.0.0.1:8080"})
    void rejectsForeignOriginDespiteCorrectSessionToken(String origin) throws Exception {
        var token = sessionToken();
        reject(token.on(local(post("/autofillBandData/123"))).header("Origin", origin));
    }

    @Test
    void rejectsInvalidTokenAndTokenFromDifferentSession() throws Exception {
        var first = sessionToken();
        reject(local(post("/autofillBandData/123")).session(first.session()).header(first.header(), "invalid"));
        var other = sessionToken();
        reject(local(post("/autofillBandData/123")).session(other.session()).header(first.header(), first.value()));
    }

    @Test
    void rejectsFormPostsAndForwardedHeadersWithValidToken() throws Exception {
        reject(local(post("/scrape")).contentType("application/x-www-form-urlencoded").content("mangaIndex=123"));
        var token = sessionToken();
        reject(token.on(local(post("/autofillBandData/123"))).header("Forwarded", "host=localhost:8080"));
        reject(token.on(local(post("/autofillBandData/123"))).header("X-Forwarded-Host", "localhost:8080"));
        reject(token.on(local(post("/autofillBandData/123"))).header("Referer", "https://evil.invalid/"));
        reject(token.on(local(post("/autofillBandData/123"))).header("Sec-Fetch-Site", "cross-site"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void oldAutofillMethodsReturn405WithoutControllerOrScraper(String method) throws Exception {
        var result = mvc.perform(local(request(HttpMethod.valueOf(method), "/autofillBandData/123"))).andReturn();
        assertEquals(405, result.getResponse().getStatus());
        verifyNoInteractions(main, shopping, statistics);
        noScraping();
    }

    @Test
    void optionsIsSideEffectFree() throws Exception {
        var result = mvc.perform(local(options("/autofillBandData/123"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getHeader("Allow").contains("POST"));
        verifyNoInteractions(main, shopping, statistics);
        noScraping();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validAutofillPostPreservesPayloadWithOrWithoutOrigin(boolean origin) throws Exception {
        var token = sessionToken();
        bandScraper.when(() -> BandScraper.scrapeBandData("123"))
                .thenReturn(Map.of("BildUrl", "https://example.invalid/cover.jpg", "Preis", "6,99 €"));
        var request = token.on(local(post("/autofillBandData/123")));
        if (origin) request.header("Origin", "http://localhost:8080")
                .header("Referer", "http://localhost:8080/home").header("Sec-Fetch-Site", "same-origin");
        var result = mvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith("application/json"));
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(3, json.size());
        assertEquals("https://example.invalid/cover.jpg", json.get("BildUrl").stringValue());
        assertEquals("6,99", json.get("Preis").stringValue());
        assertEquals("https://www.manga-passion.de/volumes/123", json.get("mpUrl").stringValue());
        verify(main).autofillBandData("123");
        bandScraper.verify(() -> BandScraper.scrapeBandData("123"), times(1));
        bandScraper.verifyNoMoreInteractions();
        mangaScraper.verifyNoInteractions();
    }

    @Test
    void validScrapePostUsesOnlyDoubleAndPreservesImportFields() throws Exception {
        var token = sessionToken();
        mangaScraper.when(() -> MangaScraper.scrapeMangaData("123")).thenReturn(Map.of(
                "Titel", "Security Test", "Deutsche Ausgabe Verlag", "Nicht vorhandener Testverlag",
                "Erstveröffentlichung Typ", "Manga", "Erstveröffentlichung Herkunft", "Japan",
                "Deutsche Ausgabe Format", "Print"));
        var result = mvc.perform(token.on(local(post("/scrape"))).contentType("application/json")
                .content("{\"mangaIndex\":\"123\"}")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("Security Test", mapper.readTree(result.getResponse().getContentAsString()).get("Titel").stringValue());
        verify(main).scrapeManga(Map.of("mangaIndex", "123"));
        mangaScraper.verify(() -> MangaScraper.scrapeMangaData("123"), times(1));
        mangaScraper.verifyNoMoreInteractions();
        bandScraper.verifyNoInteractions();
    }

    @Test
    void acceptsIpv4AndExposesOnlyMinimalHealth() throws Exception {
        var result = mvc.perform(get("http://127.0.0.1:8080/home").header("Host", "127.0.0.1:8080")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        result = mvc.perform(local(get("/actuator/health"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var health = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, health.size());
        assertEquals("UP", health.get("status").stringValue());
        assertEquals(404, mvc.perform(local(get("/actuator/env"))).andReturn().getResponse().getStatus());
        noScraping();
    }

    private Token sessionToken() throws Exception {
        MvcResult result = mvc.perform(local(get("/home"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var token = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        var value = new Token((MockHttpSession) result.getRequest().getSession(false), token.getHeaderName(), token.getToken());
        clearInvocations(main, shopping, statistics);
        return value;
    }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host", "localhost:8080").with(req -> { req.setServerPort(8080); return req; });
    }

    private void reject(MockHttpServletRequestBuilder request) throws Exception {
        assertEquals(403, mvc.perform(request).andReturn().getResponse().getStatus());
        verifyNoInteractions(main, shopping, statistics);
        noScraping();
    }

    private void noScraping() {
        bandScraper.verifyNoInteractions();
        mangaScraper.verifyNoInteractions();
    }

    // Kein toString mit Tokenwert verwenden; auch Assertions geben Tokens nicht aus.
    private record Token(MockHttpSession session, String header, String value) {
        MockHttpServletRequestBuilder on(MockHttpServletRequestBuilder request) {
            return request.session(session).header(header, value);
        }
    }
}
