package de.mangalib.testsupport;

import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ContextCustomizerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** Reine Schutzprüfungen: kein Spring-Boot-Start, kein Treiber und kein Datenbankzugriff. */
class TestDatabaseGuardTest {
    // Ausschließlich ein synthetischer Vergleichswert, keine verwendbaren Zugangsdaten.
    private static final String PASSWORD = "synthetic-guard-test-value";

    private MockEnvironment safeEnvironment() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", TestDatabaseGuard.URL)
                .withProperty("spring.datasource.username", TestDatabaseGuard.USERNAME)
                .withProperty("spring.datasource.password", PASSWORD)
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.generate-ddl", "false")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.liquibase.enabled", "false");
        environment.setActiveProfiles("test");
        return environment;
    }

    @Test
    void acceptsOnlyTheExplicitTestConfiguration() {
        assertDoesNotThrow(() -> TestDatabaseGuard.verify(safeEnvironment(), PASSWORD));
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:mysql://localhost:3306/mangalib",
            "spring.datasource.url,jdbc:mysql://localhost:3306/mangalib_test_backup?useSSL=false",
            "spring.datasource.url,jdbc:mysql://remote:3306/mangalib_test?useSSL=false",
            "spring.datasource.url,jdbc:mysql://localhost:3306/mangalib_test?useSSL=false&user=mangalib_user",
            "spring.datasource.username,mangalib_user",
            "spring.datasource.password,other-synthetic-value",
            "spring.datasource.hikari.jdbc-url,jdbc:mysql://localhost:3306/mangalib",
            "spring.datasource.hikari.username,mangalib_user",
            "spring.datasource.jndi-name,java:comp/env/jdbc/production",
            "spring.datasource.type,com.zaxxer.hikari.HikariDataSource",
            "spring.jpa.hibernate.ddl-auto,create-drop",
            "spring.jpa.generate-ddl,true",
            "spring.sql.init.mode,always",
            "spring.flyway.enabled,true",
            "spring.liquibase.enabled,true",
            "spring.jpa.properties.hibernate.hbm2ddl.auto,update",
            "spring.jpa.properties.jakarta.persistence.jdbc.url,jdbc:mysql://localhost:3306/mangalib",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action,drop-and-create"
    })
    void rejectsUnsafeOverrides(String key, String value) {
        var environment = safeEnvironment().withProperty(key, value);
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
    }

    @ParameterizedTest
    @CsvSource({
            "SPRING_DATASOURCE_URL,jdbc:mysql://localhost:3306/mangalib",
            "SPRING_DATASOURCE_USERNAME,mangalib_user",
            "SPRING_DATASOURCE_HIKARI_JDBCURL,jdbc:mysql://localhost:3306/mangalib",
            "SPRING_JPA_HIBERNATE_DDLAUTO,create-drop"
    })
    void rejectsEnvironmentOverrides(String key, String value) {
        var environment = safeEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("systemEnvironment", Map.of(key, value)));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    void rejectsMissingPassword(String password) {
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(safeEnvironment(), password));
    }

    @Test
    void rejectsMissingOrAdditionalProfiles() {
        var environment = safeEnvironment();
        environment.setActiveProfiles();
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
        environment.setActiveProfiles("test", "production");
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
    }

    @Test
    void isRegisteredForSpringTestContexts() {
        var factories = SpringFactoriesLoader.loadFactories(ContextCustomizerFactory.class, getClass().getClassLoader());
        assertTrue(factories.stream().anyMatch(TestDatabaseContextCustomizerFactory.class::isInstance));
    }

    @Test
    void abortsContextRefreshBeforeSingletonCreation() {
        var singletonCreated = new AtomicBoolean();
        try (var context = new GenericApplicationContext()) {
            var environment = safeEnvironment();
            environment.setActiveProfiles("production");
            context.setEnvironment(environment);
            context.registerBean("probe", Object.class, () -> {
                singletonCreated.set(true);
                return new Object();
            });
            new TestDatabaseContextCustomizerFactory().createContextCustomizer(getClass(), List.of())
                    .customizeContext(context, null);
            assertThrows(IllegalStateException.class, context::refresh);
            assertFalse(singletonCreated.get(), "Die Schutzprüfung muss vor Bean-Erzeugung abbrechen.");
        }
    }

    @Test
    void rejectsUnsafeConnectionBeforeCallingJdbc() {
        var environment = safeEnvironment();
        environment.setActiveProfiles("production");
        var datasource = new GuardedTestDataSource(environment);
        // Auch bei einer Regression darf dieser Negativtest niemals einen echten Treiber erreichen.
        try (var jdbc = mockStatic(DriverManager.class, invocation -> {
            // SQLException fragt den LogWriter ab; das öffnet keine Verbindung.
            if (invocation.getMethod().getName().equals("getLogWriter")) {
                return null;
            }
            throw new AssertionError("Unerlaubter JDBC-Aufruf im datenbankfreien Schutztest.");
        })) {
            assertThrows(IllegalStateException.class, datasource::getConnection);
            assertThrows(SQLException.class, () -> datasource.getConnection("other", PASSWORD));
            jdbc.verify(DriverManager::getLogWriter);
            jdbc.verifyNoMoreInteractions();
        }
    }

    @Test
    void acceptsGuardedContextWithoutOpeningAConnection() {
        try (var jdbc = mockStatic(DriverManager.class);
                var context = new GenericApplicationContext()) {
            context.setEnvironment(safeEnvironment());
            TestDatabaseContextCustomizerFactory.registerDatabaseGuard(context, () -> PASSWORD);
            assertDoesNotThrow(context::refresh);
            assertInstanceOf(GuardedTestDataSource.class, context.getBean("dataSource"));
            jdbc.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "additionalDataSource", "dataSource" })
    void rejectsAdditionalOrReplacedBeanBeforeSingletonCreation(String beanName) {
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(safeEnvironment());
            TestDatabaseContextCustomizerFactory.registerDatabaseGuard(context, () -> PASSWORD);
            // Simuliert die spätere Registrierung durch @Configuration/@Bean. Beim selben
            // Namen bleibt sogar der Typ gleich: Die Identitätsprüfung muss trotzdem greifen.
            context.addBeanFactoryPostProcessor(new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    registry.registerBeanDefinition(beanName, new RootBeanDefinition(GuardedTestDataSource.class,
                            () -> new GuardedTestDataSource(context.getEnvironment())));
                }

                @Override
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                }
            });
            assertAbortsBeforePersistenceInitialization(context, "Alternative DataSource erkannt.");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:mysql://localhost:3306/mangalib,Unzulässige Test-Datenbank-URL.",
            "spring.datasource.username,mangalib_user,Unzulässiger Test-Datenbankbenutzer.",
            "spring.jpa.hibernate.ddl-auto,update,Tests dürfen das Schema ausschließlich validieren."
    })
    void rejectsOverridesAddedAfterCustomizerRegistration(String key, String value, String message) {
        try (var context = new GenericApplicationContext()) {
            var environment = safeEnvironment();
            context.setEnvironment(environment);
            assertDoesNotThrow(() -> TestDatabaseGuard.verify(environment, PASSWORD));
            TestDatabaseContextCustomizerFactory.registerDatabaseGuard(context, () -> PASSWORD);
            environment.withProperty(key, value);
            assertAbortsBeforePersistenceInitialization(context, message);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "systemProperties,spring.datasource.url,jdbc:mysql://localhost:3306/mangalib",
            "systemProperties,spring.jpa.hibernate.ddl-auto,update",
            "Inlined Test Properties,spring.datasource.username,mangalib_user",
            "Inlined Test Properties,spring.jpa.properties.hibernate.hbm2ddl.auto,create"
    })
    void rejectsHigherPriorityPropertySources(String source, String key, String value) {
        var environment = safeEnvironment();
        // Nur die lokale PropertySource simulieren; keine JVM-weiten Properties verändern.
        environment.getPropertySources().addFirst(new MapPropertySource(source, Map.of(key, value)));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"spring\":{\"datasource\":{\"url\":\"jdbc:mysql://localhost:3306/mangalib\"}}}",
            "{\"spring\":{\"datasource\":{\"username\":\"mangalib_user\"}}}",
            "{\"spring\":{\"jpa\":{\"hibernate\":{\"ddl-auto\":\"create-drop\"}}}}"
    })
    void rejectsSpringApplicationJsonOverrides(String json) {
        var environment = safeEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("systemEnvironment", Map.of("SPRING_APPLICATION_JSON", json)));
        // Den echten Boot-Parser verwenden, ohne SpringApplication oder Datenbank zu starten.
        new SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        assertTrue(environment.getPropertySources().contains("spring.application.json"));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verify(environment, PASSWORD));
    }

    private void assertAbortsBeforePersistenceInitialization(GenericApplicationContext context, String message) {
        var persistenceInitialized = new AtomicBoolean();
        context.registerBean("entityManagerFactory", Object.class, () -> {
            persistenceInitialized.set(true);
            throw new AssertionError("Persistenz-Initialisierung vor bestandener Schutzprüfung.");
        });
        try (var jdbc = mockStatic(DriverManager.class, invocation -> {
            throw new AssertionError("Unerlaubter JDBC-Aufruf im datenbankfreien Schutztest.");
        })) {
            var failure = assertThrows(IllegalStateException.class, context::refresh);
            assertEquals("Test-Datenbankschutz: " + message, failure.getMessage());
            assertFalse(persistenceInitialized.get());
            jdbc.verifyNoInteractions();
        }
    }
}
