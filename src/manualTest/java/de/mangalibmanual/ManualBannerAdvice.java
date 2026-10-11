package de.mangalibmanual;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import de.mangalib.service.ScraperRequestRejectedException;
import java.util.Map;

/** Nur im manuellen Artefakt registriert; reguläre Ansichten erhalten keinen Testbanner. */
@ControllerAdvice
public final class ManualBannerAdvice {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ManualBannerAdvice.class);

    @ExceptionHandler(ScraperRequestRejectedException.class)
    public ResponseEntity<Map<String, String>> rejectedScraperRequest() {
        // Festes Ereignis ohne Nutzereingabe, Exception-/Token-/Konfigurationsdetails.
        log.warn("Manuelle Scraperanfrage abgewiesen: Index nicht freigegeben.");
        return ResponseEntity.badRequest().body(Map.of("error", "In der manuellen Testumgebung sind nur die synthetischen Indizes 900001/900002 erlaubt."));
    }
    @ModelAttribute("manualTestEnvironment")
    public boolean manualTestEnvironment() { return true; }
}
