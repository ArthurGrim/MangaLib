package de.mangalib.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Ungepoolte Test-DataSource ohne konfigurierbare alternative Verbindungswege. */
public final class GuardedTestDataSource extends AbstractDataSource {
    private final ConfigurableEnvironment environment;

    public GuardedTestDataSource(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public Connection getConnection() throws SQLException {
        String password = System.getenv(TestDatabaseGuard.PASSWORD_VARIABLE);
        TestDatabaseGuard.verify(environment, password);
        // Nie frei überschreibbare Spring-Einstellungen an den Treiber weitergeben.
        return DriverManager.getConnection(TestDatabaseGuard.URL, TestDatabaseGuard.USERNAME, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Alternative Zugangsdaten sind für Testverbindungen nicht erlaubt.");
    }
}
