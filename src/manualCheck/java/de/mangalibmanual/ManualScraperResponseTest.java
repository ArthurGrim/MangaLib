package de.mangalibmanual;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.mangalib.controller.MainController;
import de.mangalib.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.mangalib.security.LocalRequestFilter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MockMvc mit echten Host-/Origin-/CSRF-Filtern, synthetischem Gateway
 * und Service-Mocks. Kein Boot-/JPA-Kontext, kein Listener, kein Live-Scraper. */
class ManualScraperResponseTest {
    private MockMvc mvc;
    private MockHttpSession session;
    private String maskedToken;
    private SyntheticScraperGateway gateway;
    private VerlagService publishers;
    private TypService types;
    private FormatService formats;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ManualBannerAdvice.class);

    @BeforeEach void setup() {
        var controller = new MainController();
        gateway = spy(new SyntheticScraperGateway());
        publishers = mock(VerlagService.class); types = mock(TypService.class); formats = mock(FormatService.class);
        when(publishers.findVerlagIdByName("Testverlag")).thenReturn(1L);
        when(types.findTypIdByBezeichnung("Manga")).thenReturn(1L);
        when(formats.findFormatIdByBezeichnung("Softcover")).thenReturn(1L);
        var bands = new BandService();
        ReflectionTestUtils.setField(bands, "scraperGateway", gateway);
        ReflectionTestUtils.setField(controller, "scraperGateway", gateway);
        ReflectionTestUtils.setField(controller, "bandService", bands);
        ReflectionTestUtils.setField(controller, "verlagService", publishers);
        ReflectionTestUtils.setField(controller, "typService", types);
        ReflectionTestUtils.setField(controller, "formatService", formats);
        var repository = new HttpSessionCsrfTokenRepository();
        var request = new MockHttpServletRequest(); session = new MockHttpSession(); request.setSession(session);
        var token = repository.generateToken(request);
        repository.saveToken(token, request, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
        maskedToken = ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ManualBannerAdvice())
                .addFilters(new LocalRequestFilter(18080), new CsrfFilter(repository)).build();
        logs.start(); logger.addAppender(logs);
    }
    @AfterEach void cleanup() { logger.detachAppender(logs); logs.stop(); }
    private MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("Host", "localhost:18080").with(r -> { r.setServerPort(18080); return r; });
    }
    private MockHttpServletRequestBuilder write(String route) {
        return local(post(route)).session(session).header("X-CSRF-TOKEN", maskedToken)
                .header("Origin", "http://localhost:18080").contentType("application/json");
    }
    @ParameterizedTest @ValueSource(strings = {"900001", "900002"})
    void bothAllowedIndicesKeepSeriesAndVolumePayloads(String index) throws Exception {
        mvc.perform(write("/scrape").content("{\"mangaIndex\":\"" + index + "\"}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.Titel").value("MANUELL " + index + " – ÄÖÜ & Sonderzeichen"))
                .andExpect(jsonPath("$.verlagId").value(1)).andExpect(jsonPath("$.typId").value(1))
                .andExpect(jsonPath("$.formatId").value(1));
        mvc.perform(write("/autofillBandData/" + index)).andExpect(status().isOk())
                .andExpect(content().json("{\"BildUrl\":\"" + SyntheticScraperGateway.COVER
                        + "\",\"Preis\":\"6.99\",\"mpUrl\":\"https://www.manga-passion.de/volumes/" + index + "\"}"));
        verify(gateway).scrapeSeries(index); verify(gateway).scrapeVolume(index); assertTrue(logs.list.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"900005", "", "900001\r\nprivate-input"})
    void rejectedSeriesUsesNeutral400AndSingleLogWithoutStacktrace(String index) throws Exception {
        var json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("mangaIndex", index));
        mvc.perform(write("/scrape").content(json)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("In der manuellen Testumgebung sind nur die synthetischen Indizes 900001/900002 erlaubt."));
        verifyNoInteractions(publishers, types, formats);
        assertEquals(1, logs.list.size()); assertNull(logs.list.getFirst().getThrowableProxy());
        assertEquals(ch.qos.logback.classic.Level.WARN, logs.list.getFirst().getLevel());
        assertEquals("Manuelle Scraperanfrage abgewiesen: Index nicht freigegeben.", logs.list.getFirst().getFormattedMessage());
    }
    @Test void rejectedVolumeHasSameControlledResponse() throws Exception {
        mvc.perform(write("/autofillBandData/900005")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verify(gateway).scrapeVolume("900005"); assertEquals(1, logs.list.size()); assertNull(logs.list.getFirst().getThrowableProxy());
    }
    @Test void missingSeriesIndexIsControlled() throws Exception {
        mvc.perform(write("/scrape").content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(publishers, types, formats); assertEquals(1, logs.list.size());
    }
    @Test void getHeadAndOptionsDoNotReachDoubles() throws Exception {
        for (String route : new String[]{"/scrape", "/autofillBandData/900001"}) {
            mvc.perform(local(get(route))).andExpect(status().isMethodNotAllowed());
            mvc.perform(local(head(route))).andExpect(status().isMethodNotAllowed());
            mvc.perform(local(options(route))).andExpect(status().isOk());
        }
        verifyNoInteractions(gateway, publishers, types, formats); assertTrue(logs.list.isEmpty());
    }
    @Test void hostOriginAndMissingCsrfFailBeforeGateway() throws Exception {
        mvc.perform(post("/scrape").header("Host", "other.invalid:18080").contentType("application/json").content("{}")
                .with(r -> { r.setServerPort(18080); return r; })).andExpect(status().isForbidden());
        mvc.perform(local(post("/scrape")).header("Origin", "http://other.invalid:18080").session(session)
                .header("X-CSRF-TOKEN", maskedToken).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(local(post("/scrape")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(local(post("/scrape")).session(session).header("X-CSRF-TOKEN", "invalid-token")
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(local(post("/scrape")).session(new MockHttpSession()).header("X-CSRF-TOKEN", maskedToken)
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(gateway, publishers, types, formats); assertTrue(logs.list.isEmpty());
    }
}
