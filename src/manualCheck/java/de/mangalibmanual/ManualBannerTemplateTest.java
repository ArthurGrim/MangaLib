package de.mangalibmanual;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import static org.junit.jupiter.api.Assertions.*;

/** Reale Template-Ausschnitte mit Thymeleaf, ohne Web-/Spring-/DB-Kontext.
 * Keine Aussage über die vom Browser berechnete Pixelgeometrie. */
class ManualBannerTemplateTest {
    @ParameterizedTest @ValueSource(strings = {"home", "einkaufsliste", "statistik/st_overview",
            "statistik/st_status", "statistik/st_verlag", "statistik/st_typ", "statistik/st_format"})
    void manualMarkerAndAssetsAreAbsentFromNormalPages(String name) throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/" + name + ".html"));
        // Alles bis zum eigentlichen Header enthält die echten head-/Banner-Ausdrücke.
        String fragment = template.substring(0, template.indexOf("<header class=\"header\">")) + "</body></html>";
        var engine = new SpringTemplateEngine(); var context = new Context();
        context.setVariable("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-only"));
        // Die statischen Assets benutzen normale relative URLs, keinen Web-Kontext.
        fragment = fragment.replaceAll("th:href=\"[^\"]*\"", "");
        String normal = engine.process(fragment, context);
        assertFalse(normal.contains("MANUELLE TESTUMGEBUNG"));
        assertFalse(normal.contains("/manual-test/banner."));
        assertFalse(normal.contains("class=\"manual-test-banner\""));
        if (name.equals("home") || name.equals("einkaufsliste")) {
            assertTrue(normal.contains("data-cover-placeholder=\"/svg/filler_img2.png\""));
            assertFalse(normal.contains("/manual-test/cover.svg"));
        }
        context.setVariable("manualTestEnvironment", new ManualBannerAdvice().manualTestEnvironment());
        String manual = engine.process(fragment, context);
        assertTrue(manual.contains("MANUELLE TESTUMGEBUNG")); assertTrue(manual.contains("class=\"manual-test-banner\""));
        assertTrue(manual.contains("/manual-test/banner.css")); assertTrue(manual.contains("/manual-test/banner.js"));
        assertTrue(manual.contains("defer")); assertTrue(manual.contains("role=\"status\""));
        if (name.equals("home") || name.equals("einkaufsliste")) {
            assertTrue(manual.contains("data-cover-placeholder=\"/manual-test/cover.svg\""));
        }
    }
}
