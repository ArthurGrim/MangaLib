package de.mangalib.testsupport;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/** Prüft das effektive Testziel, ohne einen JDBC-Treiber oder eine Verbindung zu öffnen. */
public final class TestDatabaseGuard {
    public static final String URL = "jdbc:mysql://localhost:3306/mangalib_test?useSSL=false";
    public static final String USERNAME = "mangalib_test_user";
    public static final String PASSWORD_VARIABLE = "MANGALIB_TEST_DB_PASSWORD";

    private TestDatabaseGuard() {
    }

    public static void verify(ConfigurableEnvironment environment, String testPassword) {
        require(Arrays.equals(environment.getActiveProfiles(), new String[] { "test" }),
                "Ausschließlich das Spring-Profil test muss aktiv sein.");
        require(testPassword != null && !testPassword.isBlank(),
                "Die Umgebungsvariable MANGALIB_TEST_DB_PASSWORD muss gesetzt sein.");

        // Binder berücksichtigt auch relaxed binding von Umgebungsvariablen.
        Binder binder = Binder.get(environment);
        require(URL.equals(property(binder, "spring.datasource.url")), "Unzulässige Test-Datenbank-URL.");
        require(USERNAME.equals(property(binder, "spring.datasource.username")), "Unzulässiger Test-Datenbankbenutzer.");
        require(testPassword.equals(property(binder, "spring.datasource.password")),
                "Das Datenbankpasswort muss aus MANGALIB_TEST_DB_PASSWORD stammen.");
        Map<String, Object> datasource = binder.bind("spring.datasource", Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of());
        require(Set.of("url", "username", "password").containsAll(datasource.keySet()),
                "Zusätzliche DataSource-/Pool-/JNDI-Einstellungen sind in Tests nicht erlaubt.");
        require(binder.bind("spring.jpa.properties", Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of()).isEmpty(),
                "Direkte JPA-/Hibernate-Einstellungen sind in Tests nicht erlaubt.");
        require("validate".equals(property(binder, "spring.jpa.hibernate.ddl-auto")),
                "Tests dürfen das Schema ausschließlich validieren.");
        require("false".equals(property(binder, "spring.jpa.generate-ddl")), "DDL-Erzeugung muss deaktiviert sein.");
        require("never".equals(property(binder, "spring.sql.init.mode")), "SQL-Initialisierung muss deaktiviert sein.");
        require("false".equals(property(binder, "spring.flyway.enabled")), "Flyway muss deaktiviert sein.");
        require("false".equals(property(binder, "spring.liquibase.enabled")), "Liquibase muss deaktiviert sein.");
    }

    private static String property(Binder binder, String name) {
        return binder.bind(name, String.class).orElse(null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            // Keine fremden Konfigurationswerte oder Passwörter ausgeben.
            throw new IllegalStateException("Test-Datenbankschutz: " + message);
        }
    }
}
