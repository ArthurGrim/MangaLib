package de.mangalib;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetische SHOW-GRANTS-Darstellungen; keine Verbindung und keine Accountoperation. */
class T03ServerRightsTest {
    private static final String ACCOUNT = " TO 'mangalib_test_user'@'localhost'";
    private static final String DML = "GRANT SELECT, INSERT, UPDATE, DELETE ON ";
    private static final String SAFE = "`mangalib\\_test`.*";

    @ParameterizedTest @ValueSource(strings = {"`mangalib\\_test`.*", "mangalib\\_test.*"})
    void acceptsOnlyExplicitLiteralSchema(String scope) {
        T03ServerRights.verify(List.of("GRANT USAGE ON *.*" + ACCOUNT, DML + scope + ACCOUNT));
    }

    @Test void acceptsSplitDmlAndMysqlBacktickAccountWithoutGrantOption() {
        T03ServerRights.verify(List.of("GRANT SELECT, INSERT ON " + SAFE + " TO `mangalib_test_user`@`localhost`",
                "GRANT UPDATE, DELETE ON " + SAFE + ACCOUNT));
    }

    @ParameterizedTest @ValueSource(strings = {"mangalib_test.*", "`mangalib_test`.*"})
    void unescapedUnderscoreIsNotAssumedLiteralWithoutServerModeEvidence(String scope) {
        assertEquals(T03ServerRights.Reason.AMBIGUOUS_SCHEMA_PATTERN, reject(DML + scope + ACCOUNT).reason());
    }

    @ParameterizedTest @ValueSource(strings = {"`mangalib`.*", "`mangalib_manual_test`.*", "*.*",
            "`mangalib%`.*", "`mangalib\\_test%`.*", "`mangalib\\\\_test`.*",
            "`mangalib\\_test`.baende", "`MANGALIB\\_TEST`.*", "`mangalib\\_tes_`.*"})
    void rejectsEveryOtherScopeIncludingCaseAndWildcards(String scope) { reject(DML + scope + ACCOUNT); }

    @ParameterizedTest @ValueSource(strings = {"DROP", "CREATE", "ALTER", "INDEX", "REFERENCES", "EXECUTE",
            "TRIGGER", "EVENT", "CREATE TEMPORARY TABLES", "LOCK TABLES", "ALL PRIVILEGES", "FILE", "SYSTEM_USER"})
    void rejectsExtraPrivilegesEvenBesideCompleteValidDml(String privilege) {
        reject("GRANT " + privilege + " ON " + SAFE + ACCOUNT);
        reject("GRANT " + privilege + " ON *.*" + ACCOUNT);
    }

    @ParameterizedTest @ValueSource(strings = {"GRANT 'role' TO 'mangalib_test_user'@'localhost'",
            "SET DEFAULT ROLE ALL TO 'mangalib_test_user'@'localhost'", "REVOKE SELECT ON `other`.* FROM 'x'@'localhost'",
            "GRANT PROXY ON 'other'@'localhost' TO 'x'@'localhost'", "", "unknown-sensitive-token"})
    void unsupportedStatementsFailClosedWithoutRawOutput(String statement) { reject(statement); }

    @ParameterizedTest @ValueSource(strings = {" WITH GRANT OPTION", " WITH ADMIN OPTION", "; DROP TABLE secret",
            " /* unexpected */", " TO 'other'@'localhost'", " IDENTIFIED BY 'synthetic-secret'"})
    void refusesTrailingClausesAndNeverExposesTheirContents(String suffix) { reject(DML + SAFE + ACCOUNT + suffix); }

    @Test void requiresAllFourDmlPrivilegesAndRejectsNullMalformedAndDuplicateLists() {
        for (String privileges : List.of("SELECT", "SELECT, INSERT, UPDATE", "USAGE", "SELECT,SELECT", "SELECT,,UPDATE"))
            assertThrows(T03ServerRights.Rejected.class, () -> T03ServerRights.verify(List.of("GRANT " + privileges + " ON " + SAFE + ACCOUNT)));
        assertThrows(T03ServerRights.Rejected.class, () -> T03ServerRights.verify(List.of()));
        assertThrows(T03ServerRights.Rejected.class, () -> T03ServerRights.verify(null));
        assertThrows(T03ServerRights.Rejected.class, () -> T03ServerRights.verify(Arrays.asList((String) null)));
    }

    private static T03ServerRights.Rejected reject(String grant) {
        var failure = assertThrows(T03ServerRights.Rejected.class,
                () -> T03ServerRights.verify(List.of(DML + SAFE + ACCOUNT, grant)));
        assertEquals("Testrechte: " + failure.reason() + ": STOP.", failure.getMessage());
        assertNull(failure.getCause());
        return failure;
    }
}
