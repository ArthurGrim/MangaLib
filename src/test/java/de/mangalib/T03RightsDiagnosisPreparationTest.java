package de.mangalib;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class T03RightsDiagnosisPreparationTest {
    private static final String SUFFIX = " TO 'mangalib_test_user'@'localhost'";
    private static final String DML = "GRANT SELECT, INSERT, UPDATE, DELETE ON ";

    @Test void explainsEscapedSchemaFailureWithoutStoringAnyRawGrant() {
        var result = T03RightsDiagnosis.analyze(0, List.of("GRANT USAGE ON *.*" + SUFFIX,
                DML + "`mangalib\\_test`.*" + SUFFIX));
        assertTrue(result.verified()); assertFalse(result.legacyAccepted());
        assertEquals(T03RightsDiagnosis.Mode.OFF, result.partialRevokes());
        assertEquals(Set.of(T03RightsDiagnosis.Scope.GLOBAL_USAGE, T03RightsDiagnosis.Scope.EXACT_ESCAPED_TEST_SCHEMA), result.scopes());
        assertFalse(result.toString().contains("mangalib")); assertFalse(result.toString().contains("localhost"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1", "unexpected"})
    void plainSchemaRemainsRejectedEvenWhenServerModeWouldMakeItLiteral(String mode) {
        var result = T03RightsDiagnosis.analyze(mode, List.of(DML + "`mangalib_test`.*" + SUFFIX));
        assertFalse(result.verified()); assertTrue(result.legacyAccepted());
        assertEquals("AMBIGUOUS_SCHEMA_PATTERN", result.corrected());
    }

    @ParameterizedTest @ValueSource(strings = {"GRANT DROP ON `private_sensitive_schema`.*",
            "GRANT SELECT ON *.*", "GRANT FILE ON *.*", "GRANT SELECT ON `mangalib\\_test`.baende",
            "GRANT 'sensitive_role'", "unrecognized_sensitive_payload"})
    void unsafeAndUnknownStatementsStopWithoutDisclosingRawNames(String statement) {
        var result = T03RightsDiagnosis.analyze(0, List.of(statement + SUFFIX));
        assertFalse(result.verified());
        assertFalse(result.toString().contains("sensitive"));
        assertFalse(result.toString().contains("baende"));
    }

    @Test void grantOptionAndUnknownServerModeNeverPass() {
        var safe = DML + "`mangalib\\_test`.*" + SUFFIX;
        var option = T03RightsDiagnosis.analyze(0, List.of(safe + " WITH GRANT OPTION"));
        assertTrue(option.grantOption()); assertFalse(option.verified());
        for (Object mode : List.of("UNKNOWN", "2", new Object()))
            assertFalse(T03RightsDiagnosis.analyze(mode, List.of(safe)).verified());
        assertEquals(T03RightsDiagnosis.Mode.UNKNOWN, T03RightsDiagnosis.mode(null));
        assertEquals(T03RightsDiagnosis.Mode.ON, T03RightsDiagnosis.mode(true));
    }

    @Test void taskAndSourceContainOnlyTheFourAllowedQueriesAndNoFixtureHooks() throws Exception {
        var source = Files.readString(Path.of("src/t03Integration/java/de/mangalib/T03RightsDiagnosisTest.java"));
        var query = java.util.regex.Pattern.compile("createNativeQuery\\(\"([^\"]+)\"\\)").matcher(source);
        var queries = new ArrayList<String>(); while (query.find()) queries.add(query.group(1));
        assertEquals(List.of("SELECT DATABASE()", "SELECT CURRENT_USER()", "SELECT @@GLOBAL.partial_revokes",
                "SHOW GRANTS FOR CURRENT_USER()"), queries);
        for (String forbidden : List.of("@Before", "@After", "persist(", "merge(", "remove(", "flush(",
                "TestDatabaseSnapshot", "coordinator", "purchases", "executeUpdate("))
            assertFalse(source.contains(forbidden));
        assertTrue(source.contains("transaction.setReadOnly(true)"));
        var build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("include(\"**/T03RightsDiagnosisTest.class\")"));
        assertFalse(build.matches("(?s).*dependsOn\\([^)]*t03RightsDiagnosisTest[^)]*\\).*"));
    }
}
