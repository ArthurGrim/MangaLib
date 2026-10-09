package de.mangalib;

import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.BandRepository;
import de.mangalib.repository.FormatRepository;
import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.repository.SammelbandRepository;
import de.mangalib.repository.StatusRepository;
import de.mangalib.repository.TypRepository;
import de.mangalib.repository.VerlagRepository;
import de.mangalib.service.MangaReiheService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class ServiceTest {
    @Autowired
    private MangaReiheService mangaReiheService;
    @Autowired
    private MangaReiheRepository mangaReiheRepository;
    @Autowired
    private BandRepository bandRepository;
    @Autowired
    private StatusRepository statusRepository;
    @Autowired
    private VerlagRepository verlagRepository;
    @Autowired
    private TypRepository typRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private SammelbandRepository sammelbandRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesSeriesWithDetailsAndVolumes() {
        // Vorhandene Stammdaten nur lesen; fehlende Voraussetzungen nicht durch Seeding reparieren.
        var status = statusRepository.findAll().stream().findFirst().orElseThrow();
        var verlag = verlagRepository.findAll().stream().findFirst().orElseThrow();
        var typ = typRepository.findAll().stream().findFirst().orElseThrow();
        var format = formatRepository.findAll().stream().findFirst().orElseThrow();
        var sammelband = sammelbandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Test-Stammdaten: Sammelbandtyp 1 fehlt."));
        String titel = "Test Titel " + UUID.randomUUID();

        MangaReihe saved = mangaReiheService.saveMangaReihe(
                null, status.getStatusId(), verlag.getVerlagId(), typ.getTypId(), format.getFormatId(),
                titel, 10, new BigDecimal("5.99"), false, false, null, sammelband.getId(),
                BigDecimal.ZERO, false, Map.of());
        assertNotNull(saved.getId());

        // Tatsächlichen Datenbank-Reload prüfen, nicht nur den Persistence Context.
        entityManager.flush();
        entityManager.clear();
        MangaReihe reloaded = mangaReiheRepository.findById(saved.getId()).orElseThrow();
        assertEquals(titel, reloaded.getTitel());
        assertEquals(10, reloaded.getAnzahlBaende().intValue());
        assertEquals(status.getStatusId(), reloaded.getStatus().getStatusId());
        assertEquals(verlag.getVerlagId(), reloaded.getVerlag().getVerlagId());
        assertEquals(typ.getTypId(), reloaded.getTyp().getTypId());
        assertEquals(format.getFormatId(), reloaded.getFormat().getFormatId());
        assertNotNull(reloaded.getMangaDetails());
        assertEquals(sammelband.getId(), reloaded.getMangaDetails().getSammelbaende().getId());
        assertFalse(reloaded.getMangaDetails().isIstGelesen());
        assertEquals(0, new BigDecimal("59.90").compareTo(reloaded.getGesamtpreis()));

        var bands = bandRepository.findByMangaReiheId(reloaded.getId());
        assertEquals(10, bands.size());
        assertEquals(10L, bands.stream().map(band -> band.getBandNr()).distinct().count());
        for (var band : bands) {
            assertTrue(band.getBandNr() >= 1 && band.getBandNr() <= 10);
            assertEquals(0, new BigDecimal("5.99").compareTo(band.getPreis()));
            assertEquals(0, BigDecimal.ZERO.compareTo(band.getAenderungPreis()));
            assertFalse(band.isIstGelesen());
        }
        // Spring rollt ausschließlich die in dieser Testtransaktion erzeugten Daten zurück.
    }
}
