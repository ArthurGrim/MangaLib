package de.mangalib;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import de.mangalib.service.FormatService;
import de.mangalib.service.MangaReiheService;
import de.mangalib.service.StatusService;
import de.mangalib.service.TypService;
import de.mangalib.service.VerlagService;

import java.util.List;

@SpringBootTest
@Transactional
public class ServiceTest {

    @Autowired
    private MangaReiheService mangaReiheService;
    @Autowired
    private FormatService formatService;
    @Autowired
    private StatusService statusService;
    @Autowired
    private TypService typService;
    @Autowired
    private VerlagService verlagService;

    @Test
    @Rollback(true)
    public void testAddAndRetrieveMangaReihe() {
        // Erstellen und Speichern von Status, Verlag, Typ und Format
        // Status
        Status status = statusService.getStatusById(1L)
                .orElseThrow(() -> new IllegalStateException("Status mit ID 1 konnte nicht gefunden werden"));

        // Verlag
        Verlag verlag = verlagService.getVerlagById(1L)
                .orElseThrow(() -> new IllegalStateException("Verlag mit ID 1 konnte nicht gefunden werden"));

        // Typ
        Typ typ = typService.getTypById(1L)
                .orElseThrow(() -> new IllegalStateException("Typ mit ID 1 konnte nicht gefunden werden"));

        // Format
        Format format = formatService.getFormatById(1L)
                .orElseThrow(() -> new IllegalStateException("Format mit ID 1 konnte nicht gefunden werden"));

        // Erstellen einer neuen MangaReihe mit den oben erstellten Entitäten
        MangaReihe mangaReihe = new MangaReihe();
        mangaReihe.setTitel("TestMangaReihe");
        mangaReihe.setStatus(status);
        mangaReihe.setVerlag(verlag);
        mangaReihe.setTyp(typ);
        mangaReihe.setFormat(format);
        mangaReihe.setAnzahlBaende(2);
        mangaReihe.setPreisProBand(6.95);
        mangaReihe.setIstEbayPreis(true);
        MangaReihe gespeicherteMangaReihe = mangaReiheService.saveMangaReihe(mangaReihe);

        // Überprüfen, ob die MangaReihe gespeichert wurde
        assertNotNull(gespeicherteMangaReihe);
        assertNotNull(gespeicherteMangaReihe.getId());
        assertEquals("TestMangaReihe", gespeicherteMangaReihe.getTitel());

        // Abrufen aller MangaReihen und Überprüfen, ob die neue MangaReihe vorhanden
        // ist
        List<MangaReihe> alleMangaReihen = mangaReiheService.findAll();
        assertTrue(alleMangaReihen.stream().anyMatch(m -> "TestMangaReihe".equals(m.getTitel())));

        // Aktualisieren des Titels der MangaReihe
        mangaReiheService.updateMangaReiheTitel(gespeicherteMangaReihe.getId(), "TestMangaReihe2");

        // Abrufen der aktualisierten MangaReihe und Überprüfen der Änderungen
        MangaReihe aktualisierteMangaReihe = mangaReiheService.findById(gespeicherteMangaReihe.getId()).orElse(null);
        assertNotNull(aktualisierteMangaReihe);
        assertEquals("TestMangaReihe2", aktualisierteMangaReihe.getTitel());

        // Ausgeben aller MangaReihen mit ID und Titel
        System.out.println("Alle MangaReihen:");
        for (MangaReihe m : alleMangaReihen) {
            System.out.println("ID: " + m.getId() + ", Titel: " + m.getTitel() + ", Status: "
                    + m.getStatus().getBeschreibung() + ", Verlag: " + m.getVerlag().getName() + ", Typ: " + m.getTyp().getBezeichnung()
                    + ", Format: " + m.getFormat().getBezeichnung());
        }
    }

    // Weitere Tests für andere Methoden von MangaReiheService können hier
    // hinzugefügt werden
}
