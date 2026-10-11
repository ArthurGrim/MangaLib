package de.mangalibmanual;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.sql.DataSource;
import de.mangalib.service.ScraperGateway;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Unabhängig vom JUnit-Guard: feste Ziele, keine freien Konfigurations-/Verbindungswege. */
public final class ManualTestGuard {
    public static final String URL = "jdbc:mysql://127.0.0.1:3306/mangalib_manual_test?useSSL=false";
    public static final String USER = "mangalib_manual_test_user";
    public static final String PASSWORD_VARIABLE = "MANGALIB_MANUAL_DB_PASSWORD";

    private ManualTestGuard() { }

    static Properties settings() {
        var properties = new Properties();
        try (var input = new ClassPathResource("application-manual-test.properties").getInputStream()) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Manuelle Konfiguration fehlt.");
        }
        return properties;
    }

    static void rejectExternalConfiguration(Map<?, ?> values) {
        for (Object key : values.keySet()) {
            String name = key.toString().toLowerCase(java.util.Locale.ROOT).replace('_', '.');
            require(!Set.of("java.tool.options", "jdk.java.options", ".java.options").contains(name)
                && !name.matches("^(spring|server|management|hibernate|jakarta|logging|tomcat|jetty|undertow)\\..*"),
                "Externe Framework-/JVM-Konfiguration ist im manuellen Startweg nicht erlaubt.");
        }
    }

    static void verify(ConfigurableEnvironment environment, String password) {
        require(Arrays.equals(environment.getActiveProfiles(), new String[] { "manual-test" }), "Fehlprofil.");
        require(password != null && !password.isBlank(), "Manuelle Passwortvariable fehlt oder ist leer.");
        Binder binder = Binder.get(environment);
        require(URL.equals(binder.bind("spring.datasource.url", String.class).orElse(null)), "Falsches JDBC-Ziel.");
        require(USER.equals(binder.bind("spring.datasource.username", String.class).orElse(null)), "Falscher Benutzer.");
        require(password.equals(binder.bind("spring.datasource.password", String.class).orElse(null)),
                "Passwort muss ausschließlich aus der manuellen Prozessvariable stammen.");
        for (var setting : Map.ofEntries(
                Map.entry("server.address", "127.0.0.1"), Map.entry("server.port", "18080"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"), Map.entry("spring.jpa.generate-ddl", "false"),
                Map.entry("spring.sql.init.mode", "never"), Map.entry("spring.flyway.enabled", "false"),
                Map.entry("spring.liquibase.enabled", "false"),
                Map.entry("spring.config.location", "classpath:/application-manual-test.properties"),
                Map.entry("spring.main.allow-bean-definition-overriding", "false")).entrySet()) {
            require(setting.getValue().equals(binder.bind(setting.getKey(), String.class).orElse(null)),
                    "Verbindliche manuelle Startkonfiguration verletzt.");
        }
        // Exakte Teilbäume erkennen auch JNDI, Pool-Overrides und native Hibernate-Initialisierung.
        var expectedEnvironment = new org.springframework.core.env.StandardEnvironment();
        expectedEnvironment.getPropertySources().remove("systemProperties");
        expectedEnvironment.getPropertySources().remove("systemEnvironment");
        expectedEnvironment.getPropertySources().addFirst(new org.springframework.core.env.PropertiesPropertySource("manual", settings()));
        expectedEnvironment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("password",
                Map.of(PASSWORD_VARIABLE, password)));
        Binder expected = Binder.get(expectedEnvironment);
        for (String root : Set.of("spring.datasource", "spring.jpa", "spring.sql", "spring.flyway", "spring.liquibase",
                "spring.config", "spring.main", "spring.autoconfigure", "spring.profiles", "server", "management")) {
            require(tree(binder, root).equals(tree(expected, root)), "Unsicherer Konfigurations-Override.");
        }
    }

    private static Map<String, Object> tree(Binder binder, String root) {
        return binder.bind(root, Bindable.mapOf(String.class, Object.class)).orElse(Map.of());
    }

    static void verifyBeans(ConfigurableListableBeanFactory factory, RootBeanDefinition expected,
            SyntheticScraperGateway doubleInstance) {
        require(Arrays.equals(factory.getBeanNamesForType(DataSource.class, true, false), new String[] { "dataSource" })
            && factory.getBeanDefinition("dataSource") == expected && !factory.containsSingleton("dataSource"),
            "Alternative, vorzeitig erzeugte oder ersetzte DataSource.");
        require(Arrays.equals(factory.getBeanNamesForType(ScraperGateway.class, true, false), new String[] { "manualScraper" })
            && factory.getSingleton("manualScraper") == doubleInstance && doubleInstance != null,
            "Synthetische Scraper fehlen oder wurden ersetzt.");
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Manueller Testschutz: " + message);
    }
}
