package de.mangalib;

import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;

import de.mangalib.repository.FormatRepository;
import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.repository.StatusRepository;
import de.mangalib.repository.TypRepository;
import de.mangalib.repository.VerlagRepository;

import de.mangalib.service.MangaReiheService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ServiceTest {

    @Autowired
    private MangaReiheService mangaReiheService;

    @Autowired
    private MangaReiheRepository mangaReiheRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Autowired
    private VerlagRepository verlagRepository;

    @Autowired
    private TypRepository typRepository;

    @Autowired
    private FormatRepository formatRepository;

    @Test
    void testSaveMangaReihe() {

        // ---------- Stammdaten anlegen ----------
        Status status = new Status();
        status.setBeschreibung("TEST_STATUS");
        status = statusRepository.save(status);
        assertNotNull(status.getStatusId(), "statusId wurde nicht gesetzt");

        Verlag verlag = new Verlag();
        verlag.setName("TEST_VERLAG");
        verlag = verlagRepository.save(verlag);
        assertNotNull(verlag.getVerlagId(), "verlagId wurde nicht gesetzt");

        Typ typ = new Typ();
        typ.setBezeichnung("TEST_TYP");
        typ = typRepository.save(typ);
        assertNotNull(typ.getTypId(), "typId wurde nicht gesetzt");

        Format format = new Format();
        format.setBezeichnung("TEST_FORMAT");
        format = formatRepository.save(format);
        assertNotNull(format.getFormatId(), "formatId wurde nicht gesetzt");

        // ---------- Testdaten ----------
        Integer mangaIndex = 100;
        String titel = "Test Titel";
        Integer anzahlBaende = 10;
        BigDecimal preisProBand = new BigDecimal("5.99");

        Boolean istGelesen = false;
        Boolean istVergriffen = false;
        Boolean istEbayPreis = false;

        String anilistUrl = "https://testurl.com";
        Long sammelbandTypId = null;

        // WICHTIG: nicht null (sonst NPE in createMangaDetails / createAndSaveBaende sehr wahrscheinlich)
        Map<String, String> erweitereDetails = new HashMap<>();

        // Optional: wenn irgendwo gerechnet wird, lieber 0 statt null
        BigDecimal gesamtpreisAenderung = BigDecimal.ZERO;

        // ---------- Methode testen ----------
        MangaReihe gespeicherteMangaReihe = mangaReiheService.saveMangaReihe(
                mangaIndex,
                status.getStatusId(),
                verlag.getVerlagId(),
                typ.getTypId(),
                format.getFormatId(),
                titel,
                anzahlBaende,
                preisProBand,
                istVergriffen,
                istEbayPreis,
                anilistUrl,
                sammelbandTypId,
                gesamtpreisAenderung,
                istGelesen,
                erweitereDetails
        );

        assertNotNull(gespeicherteMangaReihe, "saveMangaReihe hat null zurückgegeben");
        assertNotNull(gespeicherteMangaReihe.getId(), "MangaReihe-ID wurde nicht gesetzt");
        assertEquals(titel, gespeicherteMangaReihe.getTitel());
        assertEquals(anzahlBaende, gespeicherteMangaReihe.getAnzahlBaende());

        // ---------- Reload aus DB ----------
        Optional<MangaReihe> optReloaded = mangaReiheRepository.findById(gespeicherteMangaReihe.getId());
        assertTrue(optReloaded.isPresent(), "MangaReihe wurde nicht in der DB gefunden");

        MangaReihe reloaded = optReloaded.get();

        // ---------- Relationen prüfen ----------
        assertNotNull(reloaded.getStatus(), "Status ist NULL nach Reload");
        assertNotNull(reloaded.getVerlag(), "Verlag ist NULL nach Reload");
        assertNotNull(reloaded.getTyp(), "Typ ist NULL nach Reload");
        assertNotNull(reloaded.getFormat(), "Format ist NULL nach Reload");

        assertEquals(status.getStatusId(), reloaded.getStatus().getStatusId());
        assertEquals(verlag.getVerlagId(), reloaded.getVerlag().getVerlagId());
        assertEquals(typ.getTypId(), reloaded.getTyp().getTypId());
        assertEquals(format.getFormatId(), reloaded.getFormat().getFormatId());
    }
}
