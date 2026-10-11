package de.mangalib;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mangalib.entity.Band;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.service.BandScraper;
import de.mangalib.service.MangaScraper;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** HTTP-/JSON-Baseline: echte Controller, Services, Templates und Mapper unter dem Test-DB-Guard. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class MvcContractTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private DataSource dataSource;
    private PersistenceTestFixtures.Graph graph;
    private MockedStatic<MangaScraper> mangaScraper;
    private MockedStatic<BandScraper> bandScraper;
    private Map<String, List<List<Object>>> before;

    @BeforeTransaction
    void captureExistingData() {
        assertFalse(TestTransaction.isActive());
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        before = snapshot();
    }

    @BeforeEach
    void prepareOnlyTransactionalFixturesAndBlockScrapers() throws Exception {
        // Jackson 2 bleibt transitiv vorhanden; MVC muss die getestete Jackson-3-Bean verwenden.
        var adapter = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerAdapter.class);
        var jsonConverter = adapter.getMessageConverters().stream()
                .filter(converter -> converter.canRead(Map.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertSame(mapper, assertInstanceOf(JacksonJsonHttpMessageConverter.class, jsonConverter).getMapper());
        mangaScraper = mockStatic(MangaScraper.class, invocation -> {
            throw new AssertionError("Scraper-Aufruf im MVC-Vertragstest gesperrt.");
        });
        bandScraper = mockStatic(BandScraper.class, invocation -> {
            throw new AssertionError("Scraper-Aufruf im MVC-Vertragstest gesperrt.");
        });
        // Prüft vor INSERT außerdem tatsächliches Schema, Benutzer und InnoDB.
        graph = PersistenceTestFixtures.persistGraph(em);
        graph.series().setIstVergriffen(false);
        graph.shoppingItem().setErscheinungsdatum(LocalDate.now().plusMonths(1).withDayOfMonth(15));
        flushAndClear();
    }

    @AfterEach
    void verifyNoScrapingAndReleaseMocks() {
        try {
            if (mangaScraper != null) mangaScraper.verifyNoInteractions();
            if (bandScraper != null) bandScraper.verifyNoInteractions();
        } finally {
            if (mangaScraper != null) mangaScraper.close();
            if (bandScraper != null) bandScraper.close();
        }
    }

    @AfterTransaction
    void verifyActualRollbackAndPreservedRows() {
        assertFalse(TestTransaction.isActive());
        var after = snapshot();
        assertNotNull(before);
        assertAll(before.keySet().stream().map(table -> () ->
                assertTrue(before.get(table).equals(after.get(table)),
                        "Daten nach HTTP-Test nicht vollständig erhalten/zurückgerollt: " + table)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/einkaufsliste", "/statistik/st_overview",
            "/statistik/st_status", "/statistik/st_verlag", "/statistik/st_typ", "/statistik/st_format"})
    void servesExistingHtmlViews(String path) throws Exception {
        var result = request(get(path).accept(MediaType.TEXT_HTML), 200, MediaType.TEXT_HTML);
        assertEquals(path.substring(1), result.getModelAndView().getViewName());
        var html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (path.equals("/home") || path.equals("/einkaufsliste")) {
            assertTrue(html.contains(graph.series().getTitel()));
            assertTrue(html.contains("id=\"popupContainer\""));
            assertTrue(html.contains(path.equals("/home") ? "/js/home.js" : "/js/einkaufsliste.js"));
        } else if (path.equals("/statistik/st_overview")) {
            assertTrue(html.contains("MangaLib Statistik"));
            assertTrue(html.contains("/js/statistik.js"));
        } else {
            // Die vier Unterseiten haben Navigation, aber noch keinen Statistikinhalt.
            assertTrue(html.contains("MangaLib Statistik"));
            assertTrue(html.contains("class=\"statistik-container\""));
        }
    }

    @Test
    void redirectsResetFiltersToHome() throws Exception {
        var result = request(get("/resetFilters"), 302, null);
        assertTrue(result.getResponse().getRedirectedUrl().startsWith("/home?sortierung=id"));
    }

    @Test
    void returnsSeriesEditorFieldsWithTheirFrontendTypes() throws Exception {
        var data = jsonGet("/getMangaReiheData/" + graph.series().getId());
        number(data, "id", graph.series().getId());
        number(data, "mangaIndex", 12345);
        number(data, "statusId", graph.series().getStatus().getStatusId());
        number(data, "verlagId", graph.series().getVerlag().getVerlagId());
        number(data, "typId", graph.series().getTyp().getTypId());
        number(data, "formatId", graph.series().getFormat().getFormatId());
        number(data, "anzahlBaende", 2);
        number(data, "reread", 2);
        assertTrue(data.get("preisProBand").isString());
        assertEquals("5,99", data.get("preisProBand").stringValue());
        assertEquals("0,36", data.get("gesamtpreisAenderung").stringValue());
        bool(data, "istGelesen", false);
        bool(data, "istEbayPreis", false);
        bool(data, "istVergriffen", false);
        assertEquals(graph.seriesDetails().getAnilistUrl(), data.get("anilistUrl").stringValue());
        assertFalse(data.has("mangaDetails"));
        assertFalse(data.has("status"));
    }

    @Test
    void omitsNullSeriesFieldsAndUsesBandFallbacks() throws Exception {
        var series = em.find(MangaReihe.class, graph.series().getId());
        series.setMangaIndex(null);
        series.setIstEbayPreis(null);
        series.setIstVergriffen(null);
        series.getMangaDetails().setAnilistUrl(null);
        series.getMangaDetails().setCoverUrl(null);
        series.getMangaDetails().setReread(null);
        var band = em.find(Band.class, graph.volumes().getFirst().getId());
        band.setBildUrl(null);
        band.setMpUrl(null);
        band.setBandIndex(null);
        band.setReread(null);
        flushAndClear();
        var data = jsonGet("/getMangaReiheData/" + series.getId());
        for (var field : List.of("mangaIndex", "istEbayPreis", "istVergriffen", "anilistUrl", "coverUrl", "bildUrl")) {
            assertFalse(data.has(field), "NON_NULL-Vertrag: " + field);
        }
        number(data, "reread", 0);
        var bandData = jsonGet("/getBand/" + band.getId());
        for (var field : List.of("bildUrl", "mpUrl", "bandIndex")) {
            assertTrue(bandData.get(field).isString());
            assertEquals("", bandData.get(field).stringValue());
        }
        bool(bandData, "istSpecial", false);
        number(bandData, "reread", 0);
    }

    @Test
    void returnsBandAndListAsFlatJsonWithoutParentCycles() throws Exception {
        var data = jsonGet("/getBand/" + graph.volumes().getFirst().getId());
        number(data, "id", graph.volumes().getFirst().getId());
        number(data, "bandNr", 1);
        money(data, "preis", "5.99");
        money(data, "aenderungPreis", "0.00");
        bool(data, "istGelesen", true);
        bool(data, "istSpecial", false);
        assertTrue(data.get("bildUrl").isString());
        assertEquals(graph.volumes().getFirst().getBildUrl().toExternalForm(), data.get("bildUrl").stringValue());
        assertFalse(data.has("mangaReihe"));
        var list = jsonGet("/mangaReihe/" + graph.series().getId() + "/bands");
        assertTrue(list.isArray());
        assertEquals(2, list.size());
        for (var row : list) {
            assertTrue(row.get("id").isIntegralNumber());
            assertTrue(row.get("preis").isNumber());
            assertTrue(row.get("formattedPreis").isString());
            assertTrue(row.get("totalPreis").isString());
            assertFalse(row.has("mangaReihe"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/getMangaReiheData/-1", "/getBand/-1"})
    void missingRowsReturnEmpty404(String path) throws Exception {
        var result = request(get(path), 404, null);
        assertEquals("", result.getResponse().getContentAsString());
        assertNull(result.getResponse().getContentType());
    }

    @Test
    void createsSeriesFromAddPopupPayloadAndReloadsIt() throws Exception {
        var data = jsonPost("/addMangaReihe", seriesPayload(false), 200);
        assertTrue(data.get("id").isIntegralNumber());
        money(data, "preisProBand", "5.99");
        money(data, "gesamtpreis", "11.98");
        assertEquals(LocalDate.now(), LocalDate.parse(data.get("erstelltAm").stringValue()));
        assertNotNull(OffsetDateTime.parse(data.get("aktualisiertAm").stringValue()));
        bool(data, "istVergriffen", false);
        assertFalse(data.has("status"));
        assertFalse(data.has("verlag"));
        assertFalse(data.has("typ"));
        assertFalse(data.has("format"));
        // Der bestehende Service setzt die inverse Referenz bei Neuanlage nicht am Rückgabeobjekt.
        assertFalse(data.has("mangaDetails"));
        flushAndClear();
        var saved = em.find(MangaReihe.class, data.get("id").longValue());
        assertEquals(2, saved.getAnzahlBaende());
        assertEquals(2, em.createQuery("select b from Band b where b.mangaReihe.id=:id", Band.class)
                .setParameter("id", saved.getId()).getResultList().size());
        assertEquals(saved.getId(), saved.getMangaDetails().getId());
    }

    @Test
    void editsSeriesUsingPopupPayloadWithOmittedPricesAndSerializesDetailsWithoutCycles() throws Exception {
        var data = jsonPost("/addMangaReihe", seriesPayload(true), 200);
        number(data, "id", graph.series().getId());
        assertEquals("MVC Reihe geändert", data.get("titel").stringValue());
        var details = data.get("mangaDetails");
        assertNotNull(details);
        number(details, "id", graph.series().getId());
        assertFalse(details.has("mangaReihe"));
        assertFalse(details.has("sammelbaende"));
        assertFalse(data.has("status"));
        assertFalse(data.has("verlag"));
        money(data, "preisProBand", "5.99");
        flushAndClear();
        assertEquals("MVC Reihe geändert", em.find(MangaReihe.class, graph.series().getId()).getTitel());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsNullOrAbsentOptionalFieldsOnCreation(boolean explicitNull) throws Exception {
        var payload = mapper.readTree(seriesPayload(false));
        var object = (tools.jackson.databind.node.ObjectNode) payload;
        for (var field : List.of("mangaIndex", "anilistUrl", "sammelbandTypId", "reread",
                "istGelesen", "istEbayPreis", "istVergriffen")) {
            if (explicitNull) object.putNull(field); else object.remove(field);
        }
        var result = jsonPost("/addMangaReihe", mapper.writeValueAsString(object), 200);
        assertFalse(result.has("mangaIndex"));
        assertFalse(result.has("istEbayPreis"));
        assertFalse(result.has("istVergriffen"));
        flushAndClear();
        assertNull(em.find(MangaReihe.class, result.get("id").longValue()).getMangaIndex());
    }

    @Test
    void editsBandFromJavascriptStringsAndReturnsEmpty204() throws Exception {
        // Unterschiedliche Protokolle verhindern URL.equals-DNS im bestehenden Service.
        var payload = """
                {"id":"%s","bandNr":"1","bandIndex":"12346","preis":"6.01",
                 "aenderungPreis":"-0.02","bildUrl":"http://127.0.0.1/cover.jpg",
                 "mpUrl":"http://127.0.0.1/volumes/1","istGelesen":false,"istSpecial":true,"reread":"3"}
                """.formatted(graph.volumes().getFirst().getId());
        var result = request(post("/editBand").contentType(MediaType.APPLICATION_JSON).content(payload), 204, null);
        assertEquals("", result.getResponse().getContentAsString());
        assertNull(result.getResponse().getContentType());
        flushAndClear();
        var data = jsonGet("/getBand/" + graph.volumes().getFirst().getId());
        money(data, "preis", "6.01");
        money(data, "aenderungPreis", "-0.02");
        bool(data, "istGelesen", false);
        bool(data, "istSpecial", true);
        number(data, "reread", 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"statusId\":1}"})
    void malformedOrWronglyTypedSeriesInputsReturn400(String payload) throws Exception {
        var result = request(post("/addMangaReihe").contentType(MediaType.APPLICATION_JSON).content(payload),
                400, payload.equals("{") ? null : MediaType.TEXT_PLAIN);
        if (!payload.equals("{")) {
            assertTrue(result.getResponse().getContentAsString().startsWith("Fehler bei der Verarbeitung der Anfrage:"));
        }
    }

    @Test
    void rejectsWrongContentTypeAndWrongBandIdType() throws Exception {
        request(post("/addMangaReihe").contentType(MediaType.TEXT_PLAIN).content("{}"), 415, null);
        var result = request(post("/editBand").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":1,\"bandNr\":\"1\"}"), 400, null);
        assertEquals("", result.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsShoppingItemsWithDateOrNullFromJavascriptPayload(boolean missingDate) throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(seriesPayload(false));
        payload.remove("statusId");
        if (!missingDate) payload.put("erscheinungsdatum", "2024-02-29");
        var data = jsonPost("/addToEinkaufsliste", mapper.writeValueAsString(payload), 200);
        assertTrue(data.get("id").isIntegralNumber());
        money(data, "preis", "5.99");
        money(data, "gesamtpreis", "11.98");
        bool(data, "gekauft", false);
        assertFalse(data.has("verlagId"));
        assertFalse(data.has("typId"));
        assertFalse(data.has("formatId"));
        if (missingDate) assertFalse(data.has("erscheinungsdatum"));
        else assertEquals("2024-02-29", data.get("erscheinungsdatum").stringValue());
        flushAndClear();
        var saved = em.find(EinkaufslisteItem.class, data.get("id").longValue());
        assertEquals(saved.getId(), saved.getEinkaufslisteItemDetails().getId());
    }

    @Test
    void shoppingErrorsAndMissingPurchaseHaveTheirExistingStatusAndContentType() throws Exception {
        var bad = request(post("/addToEinkaufsliste").contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, MediaType.TEXT_PLAIN);
        assertTrue(bad.getResponse().getContentAsString().startsWith("Fehler bei der Verarbeitung der Anfrage:"));
        request(post("/buyItemUpdate").contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"-1\"}"),
                404, MediaType.TEXT_PLAIN);
        request(post("/buyItemCreate").contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":-1,\"statusId\":1}"),
                404, MediaType.TEXT_PLAIN);
    }

    @Test
    void requestsStatusThenCreatesANewSeriesFromOnlyTheOwnedShoppingItem() throws Exception {
        var item = em.find(EinkaufslisteItem.class, graph.shoppingItem().getId());
        item.setMangaIndex(2_000_000_000);
        item.setTitel("MVC Kauf " + UUID.randomUUID());
        // buyItemUpdate darf über Index/Titel keinesfalls einen Bestandsdatensatz treffen.
        assertEquals(0L, em.createQuery(
                "select count(m) from MangaReihe m where m.mangaIndex=:index or m.titel=:title", Long.class)
                .setParameter("index", item.getMangaIndex()).setParameter("title", item.getTitel()).getSingleResult());
        flushAndClear();
        var pending = jsonPost("/buyItemUpdate", "{\"itemId\":\"" + item.getId() + "\"}", 417);
        assertEquals(Set.of("message"), fields(pending));
        assertEquals("Bitte Status für neue Reihe auswählen", pending.get("message").stringValue());
        var bought = jsonPost("/buyItemCreate", """
                {"itemId":"%s","statusId":"%s"}
                """.formatted(item.getId(), graph.series().getStatus().getStatusId()), 200);
        assertEquals(Set.of("message"), fields(bought));
        assertEquals("Neue MangaReihe erfolgreich gespeichert", bought.get("message").stringValue());
        flushAndClear();
        assertTrue(em.find(EinkaufslisteItem.class, item.getId()).getGekauft());
        var created = em.createQuery("select m from MangaReihe m where m.titel=:title", MangaReihe.class)
                .setParameter("title", item.getTitel()).getSingleResult();
        assertEquals(created.getId(), created.getMangaDetails().getId());
        assertEquals(2, created.getAnzahlBaende());
    }

    @Test
    void deletesOnlyTheNewFixtureShoppingItemAndReturnsEmpty200() throws Exception {
        var result = request(delete("/items/" + graph.shoppingItem().getId()), 200, null);
        assertEquals("", result.getResponse().getContentAsString());
        assertNull(result.getResponse().getContentType());
        flushAndClear();
        assertNull(em.find(EinkaufslisteItem.class, graph.shoppingItem().getId()));
    }

    @Test
    void returnsCategoryCountsAsIntegralJsonValues() throws Exception {
        var data = jsonGet("/statistik/bandKategorien");
        assertEquals(Set.of("reihenMitEinemBand", "reihenMitZweiBisFuenfBaenden", "reihenMitSechsBisZehnBaenden",
                "reihenMitElfBisZwanzigBaenden", "reihenMitEinundzwanzigBisFuenfzigBaenden",
                "reihenMitEinundfuenfzigBisHundertBaenden", "reihenMitMehrAlsHundertBaenden"), fields(data));
        data.forEach(value -> assertTrue(value.isIntegralNumber()));
        assertTrue(data.get("reihenMitZweiBisFuenfBaenden").longValue() >= 1);
    }

    @Test
    void monthlyStatisticsKeepStringKeysAndExistingCentTruncation() throws Exception {
        // Eigener Datensatz in einem vorher leeren Testjahr; keine Bestandsänderung.
        int year = 2400;
        assertEquals(0L, em.createQuery("select count(e) from EinkaufslisteItem e where year(e.erscheinungsdatum)=:year", Long.class)
                .setParameter("year", year).getSingleResult());
        var item = em.find(EinkaufslisteItem.class, graph.shoppingItem().getId());
        item.setErscheinungsdatum(LocalDate.of(year, 2, 29));
        item.setGekauft(true);
        flushAndClear();
        number(jsonGet("/statistik/monatsdaten?jahr=" + year + "&typ=baende"), "2", 2);
        // Bestehender Roadmap-Bug: longValue() verliert 0,98 EUR; hier ausdrücklich Baseline.
        number(jsonGet("/statistik/monatsdaten?jahr=" + year + "&typ=geld"), "2", 14);
        request(get("/statistik/monatsdaten").param("jahr", "2400").param("typ", "unbekannt"), 400, null);
        request(get("/statistik/monatsdaten").param("typ", "geld"), 400, null);
    }

    private String seriesPayload(boolean edit) {
        // Entspricht popup_add_reihe.js:244 und popup_edit_reihe.js:316, einschließlich Typen.
        return """
                {"mangaIndex":12345,"statusId":"%s","verlagId":"%s","typId":"%s","formatId":"%s",
                 "titel":"%s","anzahlBaende":2,"istGelesen":false,"istVergriffen":false,"istEbayPreis":false,
                 "anilistUrl":"https://example.invalid/anilist","sammelbandTypId":null,"reread":0,
                 "scrapedData":{"istEdit":"%s"}%s}
                """.formatted(graph.series().getStatus().getStatusId(), graph.series().getVerlag().getVerlagId(),
                graph.series().getTyp().getTypId(), graph.series().getFormat().getFormatId(),
                edit ? "MVC Reihe geändert" : "MVC Reihe " + UUID.randomUUID(), edit,
                edit ? ",\"mangaReiheId\":\"" + graph.series().getId() + "\",\"coverUrl\":\"https://example.invalid/edit.jpg\""
                        : ",\"preisProBand\":\"5.99\",\"gesamtpreisAenderung\":\"0\"");
    }

    private MvcResult request(RequestBuilder builder, int status, MediaType type) throws Exception {
        assertTrue(TestTransaction.isActive());
        assertTrue(TestTransaction.isFlaggedForRollback());
        // Neue lokale Requestgrenze erfüllen; sämtliche bisherigen fachlichen Assertions bleiben bestehen.
        var local = (MockHttpServletRequestBuilder) builder;
        local.header("Host", "localhost:8080").with(request -> {
            request.setServerPort(8080);
            return request;
        }).with(csrf().asHeader());
        var result = mvc.perform(local).andReturn();
        assertFalse(result.getRequest().isAsyncStarted(), "Keine asynchronen Schreibpfade im Test.");
        assertTrue(TestTransaction.isActive());
        assertEquals(status, result.getResponse().getStatus());
        if (type != null) assertTrue(type.isCompatibleWith(MediaType.parseMediaType(result.getResponse().getContentType())));
        return result;
    }

    private JsonNode jsonGet(String path) throws Exception {
        return mapper.readTree(request(get(path).accept(MediaType.APPLICATION_JSON), 200, MediaType.APPLICATION_JSON)
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode jsonPost(String path, String payload, int status) throws Exception {
        return mapper.readTree(request(post(path).contentType(MediaType.APPLICATION_JSON).content(payload),
                status, MediaType.APPLICATION_JSON).getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static void number(JsonNode data, String field, long expected) {
        assertNotNull(data.get(field), field);
        assertTrue(data.get(field).isIntegralNumber(), field);
        assertEquals(expected, data.get(field).longValue(), field);
    }

    private static void bool(JsonNode data, String field, boolean expected) {
        assertNotNull(data.get(field), field);
        assertTrue(data.get(field).isBoolean(), field);
        assertEquals(expected, data.get(field).booleanValue(), field);
    }

    private static void money(JsonNode data, String field, String expected) {
        assertNotNull(data.get(field), field);
        assertTrue(data.get(field).isNumber(), field);
        assertEquals(0, new BigDecimal(expected).compareTo(data.get(field).decimalValue()), field);
    }

    private static Set<String> fields(JsonNode data) {
        return new HashSet<>(data.propertyNames());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Map<String, List<List<Object>>> snapshot() {
        // Feste SQL-Allowlist; vorhandene Werte nur lesen, keine Dumps oder Cleanup-Skripte.
        var keys = Map.of("status", "status_id", "verlage", "verlag_id", "typen", "typ_id", "formate", "format_id",
                "sammelbaende", "id", "mangareihe", "id", "mangadetails", "id", "baende", "id",
                "einkaufsliste", "id", "einkaufslistedetails", "id");
        var rows = new LinkedHashMap<String, List<List<Object>>>();
        keys.forEach((table, key) -> {
            @SuppressWarnings("unchecked")
            List<Object[]> values = em.createNativeQuery("select * from " + table + " order by " + key).getResultList();
            rows.put(table, values.stream().map(row -> Collections.unmodifiableList(Arrays.asList(row.clone()))).toList());
        });
        return rows;
    }
}
