package de.mangalib.controller;

import de.mangalib.security.LocalRequestFilter;
import de.mangalib.security.RequestBodyLimitFilter;
import de.mangalib.service.ReadingHistoryService;
import de.mangalib.service.ReadingHistoryStatistics.Kind;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Standalone-MVC mit unveränderten echten CSRF-/Host-/Origin-/Größenfiltern. */
class ReadingHistoryControllerTest {
    private final ReadingHistoryService service = mock(ReadingHistoryService.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<ReadingHistoryService> provider = mock(ObjectProvider.class);
    private MockMvc mvc;
    private MockHttpSession session;
    private String token;
    private final UUID operation = UUID.fromString("d3a1f8a5-47bf-4880-827e-934fe106b854");
    private final JsonMapper json = new JsonMapper();

    @BeforeEach void setup() {
        when(provider.getIfAvailable()).thenReturn(service);
        var csrf = new HttpSessionCsrfTokenRepository();
        session = new MockHttpSession(); var seed = new MockHttpServletRequest(); seed.setSession(session);
        var generated = csrf.generateToken(seed); csrf.saveToken(generated, seed, new MockHttpServletResponse());
        new XorCsrfTokenRequestAttributeHandler().handle(seed, new MockHttpServletResponse(), () -> generated);
        token = ((CsrfToken) seed.getAttribute(CsrfToken.class.getName())).getToken();
        mvc = MockMvcBuilders.standaloneSetup(new ReadingHistoryController(provider))
                .addFilters(new RequestBodyLimitFilter(), new LocalRequestFilter(8080), new CsrfFilter(csrf)).build();
    }

    private String body() {
        return "{\"bandIds\":[\"7\"],\"operationId\":\"" + operation + "\",\"kind\":\"REREAD\",\"date\":\"2026-11-15\"}";
    }
    private MockHttpServletRequestBuilder write(String body) {
        return post("http://localhost:8080/reading-history/complete").header("Host", "localhost:8080")
                .header("Origin", "http://localhost:8080").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content(body);
    }

    @Test void validRequestPreservesActionIdentityAndPayload() throws Exception {
        when(service.complete(anyList(), any(), any(), any())).thenReturn(List.of());
        var result = mvc.perform(write(body())).andReturn().getResponse();
        assertEquals(200, result.getStatus()); assertEquals("{\"events\":[]}", result.getContentAsString());
        verify(service).complete(List.of(7L), operation, Kind.REREAD, LocalDate.of(2026, 11, 15));
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "[]", "{\"bandIds\":false}", "{\"bandIds\":null}",
        "{\"bandIds\":[null],\"operationId\":\"x\",\"kind\":\"REREAD\",\"date\":\"2026-11-15\"}"
    })
    void malformedRequestsHaveNoServiceEffects(String body) throws Exception {
        assertEquals(400, mvc.perform(write(body)).andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"kind", "date", "operationId", "bandIds"})
    void missingRequiredFieldHasNoServiceEffects(String field) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("bandIds", List.of("7")); data.put("operationId", operation.toString());
        data.put("kind", "REREAD"); data.put("date", "2026-11-15"); data.remove(field);
        assertEquals(400, mvc.perform(write(json.writeValueAsString(data))).andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"2026-02-30", "2026-1-1", "<script>private</script>", "2026-11-15T12:00:00Z"})
    void invalidDateIsRejectedBeforeService(String date) throws Exception {
        assertEquals(400, mvc.perform(write(body().replace("2026-11-15", date))).andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @Test void csrfHostOriginAndContentTypeRemainMandatory() throws Exception {
        assertEquals(403, mvc.perform(write(body()).header("Origin", "https://evil.invalid")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("http://localhost:8080/reading-history/complete").header("Host", "localhost:8080")
                .contentType("application/json").content(body())).andReturn().getResponse().getStatus());
        assertEquals(415, mvc.perform(write(body()).contentType("text/plain")).andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @Test void disabledFeatureHasNoStorageAccess() throws Exception {
        when(provider.getIfAvailable()).thenReturn(null);
        var status = mvc.perform(get("http://localhost:8080/reading-history/status").header("Host", "localhost:8080")).andReturn().getResponse();
        assertEquals(200, status.getStatus()); assertEquals("{\"enabled\":false}", status.getContentAsString());
        assertEquals(503, mvc.perform(write(body())).andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @Test void failuresExposeNoExceptionBodyAndConflictsStay409() throws Exception {
        when(service.complete(anyList(), any(), any(), any())).thenThrow(new IllegalStateException("password SQL private details"));
        var response = mvc.perform(write(body())).andReturn().getResponse();
        assertEquals(500, response.getStatus()); assertEquals("", response.getContentAsString());
        doThrow(new ReadingHistoryService.Conflict()).when(service).complete(anyList(), any(), any(), any());
        response = mvc.perform(write(body())).andReturn().getResponse();
        assertEquals(409, response.getStatus()); assertFalse(response.getContentAsString().contains("Exception"));
    }

    @Test void dateCorrectionCarriesRevisionAndCannotDelete() throws Exception {
        var request = post("http://localhost:8080/reading-history/bands/7/events/2/date")
                .header("Host", "localhost:8080").header("Origin", "http://localhost:8080")
                .session(session).header("X-CSRF-TOKEN", token).contentType("application/json")
                .content("{\"revision\":0,\"date\":\"2026-11-14\"}");
        when(service.correct(anyLong(), anyLong(), anyInt(), any())).thenThrow(new ReadingHistoryService.Missing());
        assertEquals(404, mvc.perform(request).andReturn().getResponse().getStatus());
        verify(service).correct(7, 2, 0, LocalDate.of(2026,11,14));
        assertEquals(405, mvc.perform(delete("http://localhost:8080/reading-history/bands/7/events/2/date")
                .header("Host", "localhost:8080").header("Origin", "http://localhost:8080").session(session)
                .header("X-CSRF-TOKEN", token).contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }
}
