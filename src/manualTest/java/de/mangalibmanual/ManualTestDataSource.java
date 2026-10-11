package de.mangalibmanual;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Einziger JDBC-Weg im manuellen Launcher; keine Pool-/JNDI- oder fremden Credentials. */
public final class ManualTestDataSource extends AbstractDataSource {
    private final ConfigurableEnvironment environment;
    ManualTestDataSource(ConfigurableEnvironment environment) { this.environment = environment; }

    @Override
    public Connection getConnection() throws SQLException {
        String password = System.getenv(ManualTestGuard.PASSWORD_VARIABLE);
        ManualTestGuard.verify(environment, password);
        return guardConnection(DriverManager.getConnection(ManualTestGuard.URL, ManualTestGuard.USER, password));
    }

    static Connection guardConnection(Connection connection) throws SQLException {
        // Erst nach Anmeldung gegen das feste manuelle Ziel, noch vor Übergabe an Hibernate.
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE(), SUBSTRING_INDEX(CURRENT_USER(), '@', 1)")) {
            if (!result.next() || !"mangalib_manual_test".equals(result.getString(1))
                    || !ManualTestGuard.USER.equals(result.getString(2))) {
                throw new SQLException("Manuelle DB-Identität stimmt nicht.");
            }
            return connection;
        } catch (SQLException | RuntimeException exception) {
            try { connection.close(); } catch (SQLException ignored) { }
            throw new SQLException("Manuelle Verbindung gesperrt: DB-Identität nicht bestätigt.");
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Alternative manuelle Zugangsdaten sind gesperrt.");
    }
}
