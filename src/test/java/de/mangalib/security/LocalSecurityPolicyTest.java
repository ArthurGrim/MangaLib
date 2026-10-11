package de.mangalib.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Reine Filter-/Konfigurationsprüfungen, ohne Boot, JDBC, DNS oder Browser. */
class LocalSecurityPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080", "127.0.0.1:8080", "LOCALHOST:8080"})
    void acceptsOnlySupportedLocalAuthorities(String host) throws Exception {
        var request = request(host);
        request.addHeader("Origin", "http://" + host);
        assertAccepted(request, 8080);
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "localhost:80", "localhost:8081", "localhost.evil:8080",
            "evil.localhost:8080", "127.0.0.1.evil:8080", "localhost.:8080", "user@localhost:8080",
            "localhost:08080", "localhost:8080,evil", " localhost:8080", "localhost:8080 ",
            "[::1]:8080", "0.0.0.0:8080", "192.168.1.2:8080", "localhost:8080/", ""})
    void rejectsMalformedAndNonlocalAuthoritiesBeforeDownstreamWork(String host) throws Exception {
        assertRejected(request(host));
    }

    @Test
    void rejectsMissingAndRepeatedHost() throws Exception {
        assertRejected(request(null));
        var repeated = request("localhost:8080");
        repeated.addHeader("Host", "localhost:8080");
        assertRejected(repeated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "https://localhost:8080", "http://localhost:8081",
            "http://127.0.0.1:8080", "http://localhost:8080/", "http://localhost.evil:8080",
            "http://localhost:8080 http://evil", "", "http://user@localhost:8080"})
    void rejectsForeignMalformedAndOpaqueOrigins(String origin) throws Exception {
        var request = request("localhost:8080");
        request.addHeader("Origin", origin);
        assertRejected(request);
    }

    @Test
    void permitsNavigationWithoutOriginButRejectsMultipleOrigins() throws Exception {
        assertAccepted(request("localhost:8080"), 8080);
        var request = request("localhost:8080");
        request.addHeader("Origin", "http://localhost:8080");
        request.addHeader("Origin", "http://localhost:8080");
        assertRejected(request);
    }

    @Test
    void checksRefererAndFetchMetadataForWritesButAllowsNormalNavigation() throws Exception {
        var navigation = request("localhost:8080");
        navigation.addHeader("Referer", "https://example.invalid/page");
        navigation.addHeader("Sec-Fetch-Site", "cross-site");
        assertAccepted(navigation, 8080);
        for (var referer : java.util.List.of("http://localhost:8080/home", "http://localhost:8080/home?q=1")) {
            var write = request("localhost:8080");
            write.setMethod("POST");
            write.addHeader("Referer", referer);
            write.addHeader("Sec-Fetch-Site", "same-origin");
            assertAccepted(write, 8080);
        }
        for (var referer : java.util.List.of("https://evil.invalid/page", "http://127.0.0.1:8080/home", "bad URI", "/home")) {
            var write = request("localhost:8080");
            write.setMethod("POST");
            write.addHeader("Referer", referer);
            assertRejected(write);
        }
        for (var site : java.util.List.of("cross-site", "invalid", "")) {
            var write = request("localhost:8080");
            write.setMethod("DELETE");
            write.addHeader("Sec-Fetch-Site", site);
            assertRejected(write);
        }
        var duplicates = request("localhost:8080");
        duplicates.setMethod("POST");
        duplicates.addHeader("Referer", "http://localhost:8080/home");
        duplicates.addHeader("Referer", "http://localhost:8080/home");
        assertRejected(duplicates);
        duplicates = request("localhost:8080");
        duplicates.setMethod("POST");
        duplicates.addHeader("Sec-Fetch-Site", "same-origin");
        duplicates.addHeader("Sec-Fetch-Site", "same-origin");
        assertRejected(duplicates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Forwarded", "X-Forwarded-Host", "X-Forwarded-Port", "X-Forwarded-Proto", "X-Forwarded-For"})
    void rejectsProxyHeadersEvenForOtherwiseValidRequests(String header) throws Exception {
        var request = request("localhost:8080");
        request.addHeader(header, "localhost:8080");
        assertRejected(request);
    }

    @Test
    void enforcesRequestSchemeAndPortAndSupportsConfiguredPort() throws Exception {
        var request = request("localhost:8080");
        request.setScheme("https");
        assertRejected(request);
        // MockHttpServletRequest leitet getServerPort() sonst erneut aus Host ab.
        var wrongPort = org.mockito.Mockito.spy(request("localhost:8080"));
        org.mockito.Mockito.doReturn(80).when(wrongPort).getServerPort();
        assertRejected(wrongPort);
        request = request("127.0.0.1:8181");
        request.setServerPort(8181);
        assertAccepted(request, 8181);
        request = request("localhost");
        request.setServerPort(80);
        assertAccepted(request, 80);
        request = request("localhost:80");
        request.setServerPort(80);
        request.addHeader("Origin", "http://localhost");
        assertAccepted(request, 80);
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.address=0.0.0.0", "server.address=localhost", "server.address=::",
            "server.port=0", "server.port=65536", "server.forward-headers-strategy=framework",
            "server.servlet.session.cookie.http-only=false", "server.servlet.session.cookie.same-site=none",
            "server.servlet.session.tracking-modes=url", "server.ssl.enabled=true",
            "management.server.port=8080", "server.servlet.session.cookie.domain=localhost",
            "management.endpoints.web.exposure.include=*", "management.endpoint.health.show-details=always",
            "management.endpoint.health.show-components=always", "management.endpoint.health.probes.enabled=true",
            "management.endpoints.web.discovery.enabled=true", "server.ssl.bundle=local",
            "server.servlet.session.cookie.secure=true", "server.servlet.session.cookie.path=/other",
            "server.servlet.context-path=/other"})
    void rejectsUnsafeEffectiveConfigurationOverrides(String override) throws Exception {
        var env = defaults();
        var pair = override.split("=", 2);
        env.setProperty(pair[0], pair[1]);
        assertThrows(IllegalStateException.class, () -> LocalSecurityConfiguration.validateLocalConfiguration(env));
    }

    @Test
    void acceptsSafeDefaultsAndConfiguredPort() throws Exception {
        var env = defaults();
        assertDoesNotThrow(() -> LocalSecurityConfiguration.validateLocalConfiguration(env));
        env.setProperty("server.port", "8181");
        assertDoesNotThrow(() -> LocalSecurityConfiguration.validateLocalConfiguration(env));
    }

    @Test
    void rejectsFactoryCustomizationToWildcardOrLanWithoutStartingServer() throws Exception {
        var factory = org.mockito.Mockito.mock(org.springframework.boot.web.server.AbstractConfigurableWebServerFactory.class);
        var guard = LocalSecurityConfiguration.localWebServerFactoryGuard(defaults());
        org.mockito.Mockito.when(factory.getPort()).thenReturn(8080);
        assertThrows(IllegalStateException.class, () -> guard.postProcessAfterInitialization(factory, "factory"));
        for (var bytes : java.util.List.of(new byte[]{0, 0, 0, 0}, new byte[]{(byte) 192, (byte) 168, 1, 2})) {
            org.mockito.Mockito.when(factory.getAddress()).thenReturn(java.net.InetAddress.getByAddress(bytes));
            assertThrows(IllegalStateException.class, () -> guard.postProcessAfterInitialization(factory, "factory"));
        }
        org.mockito.Mockito.when(factory.getAddress()).thenReturn(java.net.InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        assertSame(factory, guard.postProcessAfterInitialization(factory, "factory"));
        org.mockito.Mockito.when(factory.getPort()).thenReturn(8181);
        assertThrows(IllegalStateException.class, () -> guard.postProcessAfterInitialization(factory, "factory"));
        org.mockito.Mockito.when(factory.getPort()).thenReturn(8080);
        org.mockito.Mockito.when(factory.getSsl()).thenReturn(new org.springframework.boot.web.server.Ssl());
        assertThrows(IllegalStateException.class, () -> guard.postProcessAfterInitialization(factory, "factory"));
    }

    private MockEnvironment defaults() throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        var env = new MockEnvironment();
        properties.forEach((key, value) -> env.setProperty(key.toString(), value.toString()));
        return env;
    }

    private static MockHttpServletRequest request(String host) {
        var request = new MockHttpServletRequest("GET", "/autofillBandData/123");
        request.setServerPort(8080);
        if (host != null) request.addHeader("Host", host);
        return request;
    }

    private static void assertAccepted(MockHttpServletRequest request, int port) throws Exception {
        var calls = new AtomicInteger();
        new LocalRequestFilter(port).doFilter(request, new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        assertEquals(1, calls.get());
    }

    private static void assertRejected(MockHttpServletRequest request) throws Exception {
        var calls = new AtomicInteger();
        var response = new MockHttpServletResponse();
        new LocalRequestFilter(8080).doFilter(request, response, (req, res) -> calls.incrementAndGet());
        assertEquals(403, response.getStatus());
        assertEquals(0, calls.get(), "Keine Controller-/Service-/Repositoryarbeit nach Ablehnung.");
    }
}
