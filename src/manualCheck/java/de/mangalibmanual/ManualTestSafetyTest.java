package de.mangalibmanual;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import de.mangalib.service.BandService;
import de.mangalib.service.LiveScraperGateway;
import de.mangalib.service.MangaScraper;
import de.mangalib.service.BandScraper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Keine SpringApplication, kein refresh, keine JDBC-Verbindung und kein Browser. */
class ManualTestSafetyTest {
    private static final String SYNTHETIC = "synthetic-unit-test-only";
    private StandardEnvironment environment() {
        var env = new StandardEnvironment();
        env.getPropertySources().remove("systemProperties");
        env.getPropertySources().remove("systemEnvironment");
        env.getPropertySources().addLast(new PropertiesPropertySource("manual", ManualTestGuard.settings()));
        env.getPropertySources().addFirst(new MapPropertySource("synthetic", Map.of(ManualTestGuard.PASSWORD_VARIABLE, SYNTHETIC)));
        env.setActiveProfiles("manual-test");
        return env;
    }

    @Test void validConfigurationIsPure() { assertDoesNotThrow(() -> ManualTestGuard.verify(environment(), SYNTHETIC)); }

    static Stream<String[]> overrides() {
        return Stream.of(
            new String[]{"spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/mangalib?useSSL=false"},
            new String[]{"spring.datasource.url", "jdbc:mysql://localhost:3306/mangalib_test?useSSL=false"},
            new String[]{"spring.datasource.url", ManualTestGuard.URL + "&allowMultiQueries=true"},
            new String[]{"spring.datasource.username", "mangalib_user"},
            new String[]{"spring.datasource.password", "override"},
            new String[]{"spring.datasource.hikari.jdbc-url", "jdbc:mysql://localhost/mangalib"},
            new String[]{"spring.datasource.jndi-name", "jdbc/untrusted"},
            new String[]{"spring.jpa.hibernate.ddl-auto", "update"},
            new String[]{"spring.jpa.generate-ddl", "true"},
            new String[]{"spring.jpa.properties.hibernate.hbm2ddl.auto", "create"},
            new String[]{"spring.sql.init.mode", "always"},
            new String[]{"spring.flyway.enabled", "true"},
            new String[]{"spring.liquibase.enabled", "true"},
            new String[]{"spring.config.import", "optional:file:application.properties"},
            new String[]{"spring.config.location", "classpath:/application.properties"},
            new String[]{"spring.autoconfigure.exclude", "Anything"},
            new String[]{"spring.main.allow-bean-definition-overriding", "true"},
            new String[]{"server.address", "0.0.0.0"},
            new String[]{"server.address", "::1"},
            new String[]{"server.port", "8080"},
            new String[]{"server.ssl.enabled", "true"},
            new String[]{"server.servlet.session.cookie.http-only", "false"},
            new String[]{"server.forward-headers-strategy", "native"},
            new String[]{"management.server.port", "18081"},
            new String[]{"management.endpoints.web.exposure.include", "*"});
    }
    @ParameterizedTest @MethodSource("overrides") void overridesFailBeforeJdbc(String key, String value) {
        var env = environment();
        env.getPropertySources().addFirst(new MapPropertySource("override", Map.of(key, value)));
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verify(env, SYNTHETIC));
    }
    @Test void wrongAndAdditionalProfilesFail() {
        for (String[] profiles : new String[][] { {}, {"test"}, {"default"}, {"manual-test", "test"} }) {
            var env = environment();
            env.getPropertySources().addFirst(new MapPropertySource("profile", Map.of("spring.profiles.active", String.join(",", profiles))));
            env.setActiveProfiles(profiles);
            assertThrows(RuntimeException.class, () -> ManualTestGuard.verify(env, SYNTHETIC));
        }
    }
    @Test void missingAndEmptyPasswordFail() {
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verify(environment(), null));
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verify(environment(), " "));
    }
    @Test void externalEnvironmentAndJvmOptionsFail() {
        for (String key : new String[]{"SPRING_DATASOURCE_URL", "SPRING_APPLICATION_JSON", "SERVER_ADDRESS",
                "spring.profiles.active", "LOGGING_CONFIG", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) {
            assertThrows(RuntimeException.class, () -> ManualTestGuard.rejectExternalConfiguration(Map.of(key, "synthetic")));
        }
    }
    @Test void onlyExplicitValidateArgumentAllowed() {
        assertFalse(ManualTestLauncher.validateArguments(new String[0]));
        assertTrue(ManualTestLauncher.validateArguments(new String[]{"--validate-only"}));
        assertThrows(RuntimeException.class, () -> ManualTestLauncher.validateArguments(new String[]{"--spring.profiles.active=test"}));
        assertThrows(RuntimeException.class, () -> ManualTestLauncher.validateArguments(new String[]{"--validate-only", "--server.port=8080"}));
    }
    private DefaultListableBeanFactory factory(RootBeanDefinition ds, SyntheticScraperGateway scraper) {
        var factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("dataSource", ds);
        if (scraper != null) factory.registerSingleton("manualScraper", scraper);
        return factory;
    }
    @Test void soleGuardedDatasourceAndDoubleAccepted() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class, () -> new ManualTestDataSource(environment()));
        var scraper = new SyntheticScraperGateway();
        assertDoesNotThrow(() -> ManualTestGuard.verifyBeans(factory(ds, scraper), ds, scraper));
    }
    @Test void alternativeDatasourceRejectedWithoutInstantiation() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class);
        var scraper = new SyntheticScraperGateway();
        var factory = factory(ds, scraper);
        factory.registerBeanDefinition("alternative", new RootBeanDefinition(DataSource.class, () -> { fail("Must never instantiate"); return null; }));
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory, ds, scraper));
    }
    @Test void replacementOfNamedDatasourceRejected() {
        var original = new RootBeanDefinition(ManualTestDataSource.class);
        var scraper = new SyntheticScraperGateway();
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory(new RootBeanDefinition(ManualTestDataSource.class), scraper), original, scraper));
    }
    @Test void missingDoubleRejected() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class);
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory(ds, null), ds, new SyntheticScraperGateway()));
    }
    @Test void singletonReplacementOfDatasourceRejected() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class);
        var scraper = new SyntheticScraperGateway();
        var factory = factory(ds, scraper);
        factory.registerSingleton("dataSource", mock(DataSource.class));
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory, ds, scraper));
    }
    @Test void replacedDoubleRejected() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class);
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory(ds, new SyntheticScraperGateway()), ds, new SyntheticScraperGateway()));
    }
    @Test void additionalLiveGatewayRejectedWithoutCallingIt() {
        var ds = new RootBeanDefinition(ManualTestDataSource.class);
        var scraper = new SyntheticScraperGateway();
        var factory = factory(ds, scraper);
        factory.registerBeanDefinition("live", new RootBeanDefinition(LiveScraperGateway.class));
        assertThrows(RuntimeException.class, () -> ManualTestGuard.verifyBeans(factory, ds, scraper));
    }
    @Test void alternativeCredentialsBlocked() {
        assertThrows(java.sql.SQLException.class, () -> new ManualTestDataSource(environment()).getConnection("wrong", "synthetic"));
    }
    @Test void manualBrowserPolicyBlocksExternalImagesButKeepsScriptProbe() throws Exception {
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new ManualBrowserPolicy().doFilter(new org.springframework.mock.web.MockHttpServletRequest(), response,
                new org.springframework.mock.web.MockFilterChain());
        String policy = response.getHeader("Content-Security-Policy");
        assertTrue(policy.contains("img-src 'self' http://localhost:18080 http://127.0.0.1:18080"));
        assertFalse(policy.contains("https:"));
        assertFalse(policy.contains("script-src"));
    }
    @Test void missingPackagedDoubleRejectedWithoutLoadingAnyClasses() {
        assertThrows(RuntimeException.class, () -> ManualTestLauncher.verifyArtifact(new ClassLoader(null) { }));
    }
    @Test void liveResourceInArtifactRejectedWithoutLoadingIt() throws Exception {
        var marker = java.net.URI.create("file:/synthetic-resource").toURL();
        var loader = new ClassLoader(null) {
            @Override public java.net.URL getResource(String name) {
                return name.equals("de/mangalib/service/BandScraper.class") || name.equals("de/mangalibmanual/SyntheticScraperGateway.class") ? marker : null;
            }
        };
        assertThrows(RuntimeException.class, () -> ManualTestLauncher.verifyArtifact(loader));
    }
    @Test void invalidConfigurationBlocksDefaultConnectionBeforeDriver() {
        var env = environment();
        env.setActiveProfiles("test");
        assertThrows(RuntimeException.class, () -> new ManualTestDataSource(env).getConnection());
    }
    @Test void connectionIdentityIsCheckedUsingOnlyJdbcMocks() throws Exception {
        for (String[] identity : new String[][] { {"mangalib_manual_test", ManualTestGuard.USER},
                {"mangalib", ManualTestGuard.USER}, {"mangalib_manual_test", "mangalib_user"} }) {
            var connection = mock(java.sql.Connection.class);
            var statement = mock(java.sql.Statement.class);
            var result = mock(java.sql.ResultSet.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getString(1)).thenReturn(identity[0]);
            when(result.getString(2)).thenReturn(identity[1]);
            if (identity[0].equals("mangalib_manual_test") && identity[1].equals(ManualTestGuard.USER)) {
                assertSame(connection, ManualTestDataSource.guardConnection(connection));
                verify(connection, never()).close();
            } else {
                assertThrows(java.sql.SQLException.class, () -> ManualTestDataSource.guardConnection(connection));
                verify(connection).close();
            }
        }
    }
    @Test void doublesAndAutofillPreserveFieldsWithoutNetwork() {
        var gateway = new SyntheticScraperGateway();
        var bandService = new BandService();
        ReflectionTestUtils.setField(bandService, "scraperGateway", gateway);
        assertEquals(Map.of("BildUrl", SyntheticScraperGateway.COVER, "Preis", "6.99", "mpUrl", "https://www.manga-passion.de/volumes/900001"), bandService.autofillBandData("900001"));
        assertEquals("6.99", gateway.scrapeSeries("900002").get("Band 1 Preis"));
        assertEquals(SyntheticScraperGateway.COVER, gateway.scrapeSeries("900001").get("Band 1 Bild Url"));
        assertThrows(IllegalArgumentException.class, () -> gateway.scrapeVolume("1"));
        assertThrows(IllegalArgumentException.class, () -> gateway.scrapeSeries("1"));
    }
    @Test void normalGatewayKeepsStaticMockCompatibility() {
        try (var series = mockStatic(MangaScraper.class); var volumes = mockStatic(BandScraper.class)) {
            var payload = new HashMap<String, String>();
            payload.put("Titel", "synthetic");
            series.when(() -> MangaScraper.scrapeMangaData("1")).thenReturn(payload);
            volumes.when(() -> BandScraper.scrapeBandData("2")).thenReturn(payload);
            var gateway = new LiveScraperGateway();
            assertSame(payload, gateway.scrapeSeries("1"));
            assertSame(payload, gateway.scrapeVolume("2"));
            series.verify(() -> MangaScraper.scrapeMangaData("1"));
            volumes.verify(() -> BandScraper.scrapeBandData("2"));
        }
    }
}
