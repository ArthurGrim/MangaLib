package de.mangalib;

import de.mangalib.controller.*;
import de.mangalib.entity.Band;
import de.mangalib.repository.*;
import de.mangalib.security.*;
import de.mangalib.service.*;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Boots unveränderte MockMvc-Filterregistrierung einschließlich Security, kein HTTP-Listener.
 * Spies beobachten echte Beans; nur die statischen Live-Scraper sind fail-fast ersetzt.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback
class RequestLimitsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext context;
    @MockitoSpyBean MainController main;
    @MockitoSpyBean EinkaufslisteController cart;
    @MockitoSpyBean MangaReiheService rows;
    @MockitoSpyBean BandService bands;
    @MockitoSpyBean EinkaufslisteService shopping;
    @MockitoSpyBean ScraperGateway gateway;
    @MockitoSpyBean MangaReiheRepository rowRepository;
    @MockitoSpyBean BandRepository bandRepository;
    @MockitoSpyBean MangaDetailsRepository detailsRepository;
    @MockitoSpyBean EinkaufslisteRepository shoppingRepository;
    @MockitoSpyBean EinkaufslisteItemDetailsRepository shoppingDetailsRepository;
    private TestDatabaseSnapshot.Snapshot initial;
    private MockedStatic<MangaScraper> seriesScraper;
    private MockedStatic<BandScraper> volumeScraper;

    @BeforeTransaction void beforeTransaction() {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        initial = TestDatabaseSnapshot.capture(em);
    }

    @BeforeEach void forbidScrapers() {
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        seriesScraper = mockStatic(MangaScraper.class, i -> { throw new AssertionError("Live-Scraper gesperrt"); });
        volumeScraper = mockStatic(BandScraper.class, i -> { throw new AssertionError("Live-Scraper gesperrt"); });
        clearWork();
    }

    @AfterEach void noEscapes() {
        try {
            assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
            seriesScraper.verifyNoInteractions(); volumeScraper.verifyNoInteractions();
        } finally { seriesScraper.close(); volumeScraper.close(); }
    }

    @AfterTransaction void unchangedAfterRollback() {
        assertFalse(TestTransaction.isActive());
        assertEquals(initial, TestDatabaseSnapshot.capture(em), "Zeilen und Schema nach Rollback unverändert");
    }

    private Object[] observed() {
        return new Object[] {main, cart, rows, bands, shopping, gateway, rowRepository, bandRepository,
                detailsRepository, shoppingRepository, shoppingDetailsRepository};
    }
    private void clearWork() { clearInvocations(observed()); }
    private void noWork() { verifyNoInteractions(observed()); }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host", "localhost:8080").with(r -> { r.setServerPort(8080); return r; });
    }

    // Echter Sessiontoken aus derselben realen Security-Kette; kein csrf()-Testpostprozessor.
    private Token token() throws Exception {
        var result = mvc.perform(local(get("/home"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var csrf = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        var token = new Token((MockHttpSession) result.getRequest().getSession(false), csrf.getHeaderName(), csrf.getToken());
        clearWork(); return token;
    }
    private record Token(MockHttpSession session, String header, String value) {
        MockHttpServletRequestBuilder on(MockHttpServletRequestBuilder request) {
            return local(request).session(session).header(header, value).header("Origin", "http://localhost:8080");
        }
    }

    static Stream<Arguments> rejectedRequests() {
        return Stream.of("/addMangaReihe", "/editBand", "/addToEinkaufsliste", "/buyItemUpdate",
                "/buyItemCreate", "/scrape", "/autofillBandData/900001", "/items/1")
                .flatMap(path -> Stream.of("long", "malformed", "duplicate", "form", "multipart")
                        .map(kind -> Arguments.of(path, kind)));
    }

    @ParameterizedTest @MethodSource("rejectedRequests")
    void registeredFilterRejectsBeforeAllBusinessWork(String path, String kind) throws Exception {
        var token = token();
        var before = TestDatabaseSnapshot.capture(em);
        String body = switch (kind) {
            case "long" -> "{\"x\":\"" + "x".repeat(4097) + "\"}";
            case "malformed" -> "{\"synthetic-private-marker\":";
            case "duplicate" -> "{\"id\":1,\"id\":2}";
            default -> "synthetic-private-marker=1";
        };
        String type = switch (kind) {
            case "form" -> "application/x-www-form-urlencoded";
            case "multipart" -> "multipart/form-data; boundary=synthetic";
            default -> "application/json";
        };
        int expected = kind.equals("long") ? 413 : (kind.equals("form") || kind.equals("multipart") ? 415 : 400);
        var request = path.startsWith("/items/") ? delete(path) : post(path);
        var result = mvc.perform(token.on(request).contentType(type).content(body)).andReturn();
        var response = result.getResponse();
        assertEquals(expected, response.getStatus());
        assertEquals("text/plain;charset=UTF-8", response.getContentType());
        assertEquals("close", response.getHeader("Connection"));
        assertEquals(switch (expected) {
            case 413 -> "Anfrage überschreitet die technischen Größenbegrenzungen.";
            case 415 -> "Nicht unterstütztes Anfrageformat.";
            default -> "Ungültige Anfrage.";
        }, response.getContentAsString());
        assertFalse(result.getRequest().isAsyncStarted()); noWork();
        assertEquals(before, TestDatabaseSnapshot.capture(em), "Ablehnung ohne Datenmutation");
    }

    @Test void registrationAndEffectivePrecedenceComeFromBoot() throws Exception {
        FilterRegistrationBean<?> registration = context.getBean("requestBodyLimits", FilterRegistrationBean.class);
        assertInstanceOf(RequestBodyLimitFilter.class, registration.getFilter());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, registration.getOrder());
        assertEquals(Set.of("/*"), new HashSet<>(registration.getUrlPatterns()));
        assertFalse(registration.isAsyncSupported());
        assertEquals(1, context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .filter(r -> r.getFilter() instanceof RequestBodyLimitFilter).count());
        var security = context.getBean(SecurityFilterChain.class).getFilters();
        int host = -1, csrf = -1;
        for (int i = 0; i < security.size(); i++) {
            if (security.get(i) instanceof LocalRequestFilter) host = i;
            if (security.get(i) instanceof CsrfFilter) csrf = i;
        }
        assertTrue(host >= 0 && csrf > host);
        // Ohne Token würde Security 403 liefern. 400/413/415 beweisen frühe Registrierung;
        // insbesondere darf FormContentFilter den PUT-Body nicht vorzeitig parsen.
        assertEquals(400, mvc.perform(local(post("/editBand")).contentType("application/json")
                .content("{broken")).andReturn().getResponse().getStatus());
        assertEquals(413, mvc.perform(local(post("/editBand")).header("Content-Length",
                RequestBodyLimitFilter.MAX_BODY_BYTES + 1)).andReturn().getResponse().getStatus());
        assertEquals(415, mvc.perform(local(put("/editBand")).contentType("application/x-www-form-urlencoded")
                .content("synthetic=1")).andReturn().getResponse().getStatus());
        noWork();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "invalid", "other-session", "host", "origin"})
    void acceptableJsonStillRequiresLocalHostOriginAndOwnSession(String mode) throws Exception {
        var token = token();
        var request = local(post("/editBand")).contentType("application/json").content("{}");
        switch (mode) {
            case "missing" -> { }
            case "invalid" -> request.session(token.session()).header(token.header(), "invalid");
            case "other-session" -> request.session(new MockHttpSession()).header(token.header(), token.value());
            case "host" -> request.with(r -> {
                r.removeHeader("Host"); r.addHeader("Host", "foreign.invalid:8080"); return r;
            }).session(token.session()).header(token.header(), token.value());
            case "origin" -> request.header("Origin", "http://foreign.invalid:8080").session(token.session()).header(token.header(), token.value());
        }
        assertEquals(403, mvc.perform(request).andReturn().getResponse().getStatus()); noWork();
    }

    @Test void ordinaryJsonKeepsBusiness400AndMissingPurchase404() throws Exception {
        var token = token();
        var invalid = mvc.perform(token.on(post("/addMangaReihe")).contentType("application/json").content("{}"))
                .andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertEquals("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.", invalid.getContentAsString());
        var missing = mvc.perform(token.on(post("/buyItemUpdate")).contentType("application/json")
                .content("{\"itemId\":-1}")).andReturn().getResponse();
        assertEquals(404, missing.getStatus());
        verifyNoInteractions(rows, bands, shopping, gateway, rowRepository, bandRepository, detailsRepository,
                shoppingRepository, shoppingDetailsRepository);
    }

    @Test void ordinaryBandEditReplaysExplicitNullAndRetains204AfterReload() throws Exception {
        var graph = PersistenceTestFixtures.persistGraph(em);
        long id = graph.volumes().getFirst().getId();
        graph.volumes().getFirst().setReread(null);
        graph.volumes().getFirst().setIstSpecial(null);
        em.flush(); em.clear();
        var before = TestDatabaseSnapshot.capture(em);
        var token = token();
        var result = mvc.perform(token.on(post("/editBand")).contentType("application/json")
                .content("{\"id\":\"" + id + "\",\"bandNr\":\"1\",\"reread\":null,\"istSpecial\":null,\"istGelesen\":true}"))
                .andReturn();
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString()); assertNull(result.getResponse().getContentType());
        assertFalse(result.getRequest().isAsyncStarted());
        em.flush(); em.clear();
        var loaded = em.find(Band.class, id);
        assertNull(loaded.getReread()); assertNull(loaded.getIstSpecial()); assertTrue(loaded.isIstGelesen());
        assertEquals(before, TestDatabaseSnapshot.capture(em), "Unberührte historische Werte vollständig erhalten");
    }
}
