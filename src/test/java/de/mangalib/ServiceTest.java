package de.mangalib;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import de.mangalib.entity.MangaReihe;
import de.mangalib.service.MangaReiheService;

@SpringBootTest
public class ServiceTest {

    @Autowired
    private MangaReiheService mangaReiheService;

    @Test
    public void testSaveMangaReihe() {
        // Testwerte definieren
        Integer mangaIndex = 100;
        Long statusId = 1L; // Angenommen, dies ist eine gültige ID in Ihrer Datenbank
        Long verlagId = 1L;
        Long typId = 1L;
        Long formatId = 1L;
        String titel = "Test Titel";
        Integer anzahlBaende = 10;
        BigDecimal preisProBand = new BigDecimal("5.99");
        Boolean istGelesen = false;
        Boolean istVergriffen = false;
        Boolean istEbayPreis = false;
        String anilistUrl = "https://testurl.com";
        Long sammelbandTypId = null; // Angenommen, dies ist optional
        BigDecimal gesamtpreisAenderung = null; // Optional
        Map <String, String> erweitereDetails = null;
    
        // Methode aufrufen
        MangaReihe gespeicherteMangaReihe = mangaReiheService.saveMangaReihe(mangaIndex, statusId, verlagId, typId, formatId, titel,
                anzahlBaende, preisProBand, istVergriffen, istEbayPreis, anilistUrl, sammelbandTypId, gesamtpreisAenderung, istGelesen, erweitereDetails);
    
        System.out.println("Gespeicherte MangaReihe: " + gespeicherteMangaReihe);
        // Ergebnisse überprüfen
        // Sie können hier Assertions verwenden, um zu überprüfen, ob die gespeicherteMangaReihe die erwarteten Werte hat
        // Beispiel:
        // assertEquals(titel, gespeicherteMangaReihe.getTitel());
        // assertEquals(anzahlBaende, gespeicherteMangaReihe.getAnzahlBaende());
        // ...weitere Überprüfungen
    }

    // Weitere Tests für andere Methoden von MangaReiheService können hier
    // hinzugefügt werden
}
