package de.mangalibmanual;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import static org.junit.jupiter.api.Assertions.*;

/** Archivkontrolle und ausschließlich ValidateOnly-Unterprozesse: kein Spring-Kontext/Listener/JDBC. */
class ManualArtifactTest {
    private String artifact() { return System.getProperty("manualArtifact"); }

    @Test void archiveContainsNoLiveScrapersOrDefaultConfiguration() throws Exception {
        try (var jar = new JarFile(artifact())) {
            assertEquals("de.mangalibmanual.ManualTestLauncher", jar.getManifest().getMainAttributes().getValue("Start-Class"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/application-manual-test.properties"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/de/mangalibmanual/SyntheticScraperGateway.class"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/static/manual-test/cover.svg"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/static/manual-test/banner.css"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/static/manual-test/banner.js"));
            for (String name : new String[]{"application.properties", "de/mangalib/MangalibApplication.class",
                    "de/mangalib/service/BandScraper.class", "de/mangalib/service/MangaScraper.class",
                    "de/mangalib/service/LiveScraperGateway.class"}) {
                assertNull(jar.getEntry("BOOT-INF/classes/" + name));
            }
            assertFalse(jar.stream().anyMatch(e -> e.getName().matches("BOOT-INF/lib/(selenium-|webdrivermanager-|spring-boot-devtools-).*")));
        }
    }

    private String validate(String override, int exit) throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-jar", artifact(), "--validate-only").redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.startsWith("MANGALIB_") || key.matches("(?i)(SPRING|SERVER|MANAGEMENT|HIBERNATE|JAKARTA|LOGGING|TOMCAT|JETTY|UNDERTOW)_.*")
                || java.util.Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(key));
        builder.environment().put(ManualTestGuard.PASSWORD_VARIABLE, "synthetic-validation-only");
        if (!override.isEmpty()) builder.environment().put(override, "synthetic-unsafe");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "ValidateOnly muss zügig enden.");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(exit, process.exitValue(), "ValidateOnly-Exitcode");
            assertFalse(output.contains("synthetic-validation-only"));
            assertFalse(output.contains("synthetic-unsafe"));
            assertFalse(output.contains("Tomcat started"));
            assertFalse(output.contains("Hibernate"));
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
    @Test void packagedValidateOnlyWorksWithoutDatabase() throws Exception {
        assertTrue(validate("", 0).contains("KEINE Datenbankverbindung"));
    }
    @ParameterizedTest @ValueSource(strings = {"SPRING_PROFILES_ACTIVE", "SPRING_DATASOURCE_URL", "SPRING_APPLICATION_JSON", "SERVER_ADDRESS"})
    void packagedValidateOnlyRejectsOverrides(String key) throws Exception { validate(key, 1); }

    @Test void bannerOnlyAppearsInManualModel() {
        var engine = new SpringTemplateEngine();
        var context = new Context();
        String fragment = "<div th:if=\"${manualTestEnvironment == true}\">MANUELLE TESTUMGEBUNG</div>";
        assertFalse(engine.process(fragment, context).contains("MANUELLE TESTUMGEBUNG"));
        context.setVariable("manualTestEnvironment", new ManualBannerAdvice().manualTestEnvironment());
        assertTrue(engine.process(fragment, context).contains("MANUELLE TESTUMGEBUNG"));
    }
}
