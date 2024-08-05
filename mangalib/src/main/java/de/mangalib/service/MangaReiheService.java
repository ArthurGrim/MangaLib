package de.mangalib.service;

import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.repository.StatusRepository;
import de.mangalib.entity.Band;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaDetails;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.repository.VerlagRepository;
import jakarta.transaction.Transactional;
import de.mangalib.repository.TypRepository;
import de.mangalib.repository.BandRepository;
import de.mangalib.repository.FormatRepository;
import de.mangalib.repository.MangaDetailsRepository;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class MangaReiheService {

    // Das Repository für die MangaReihe-Entität
    private final MangaReiheRepository mangaReiheRepository;
    private final StatusRepository statusRepository;
    private final VerlagRepository verlagRepository;
    private final TypRepository typRepository;
    private final FormatRepository formatRepository;
    private final MangaDetailsRepository mangaDetailsRepository;
    private final BandRepository bandRepository;

    @Autowired
    private StatusService statusService;

    @Autowired
    private VerlagService verlagService;

    @Autowired
    private TypService typService;

    @Autowired
    private FormatService formatService;

    @Autowired
    private MangaDetailsService mangaDetailsService;

    @Autowired
    private BandService bandService;

    // Konstruktorbasierte Dependency Injection des MangaReiheRepository
    public MangaReiheService(MangaReiheRepository mangaReiheRepository, StatusRepository statusRepository,
            FormatRepository formatRepository, TypRepository typRepository, VerlagRepository verlagRepository,
            BandRepository bandRepository, MangaDetailsRepository mangaDetailsRepository) {
        this.mangaReiheRepository = mangaReiheRepository;
        this.statusRepository = statusRepository;
        this.verlagRepository = verlagRepository;
        this.typRepository = typRepository;
        this.formatRepository = formatRepository;
        this.mangaDetailsRepository = mangaDetailsRepository;
        this.bandRepository = bandRepository;
    }

    // Eine Methode, um alle MangaReihe-Objekte aus der Datenbank abzurufen
    public List<MangaReihe> findAll() {
        return mangaReiheRepository.findAll();
    }

    // Eine Methode, um alle MangaReihe-Objekte aus der Datenbank geordnet nach ID
    // abzurufen
    public List<MangaReihe> findAllSortById() {
        List<MangaReihe> result = mangaReiheRepository.findAll();
        result.sort(Comparator.comparing(MangaReihe::getId));
        return result;
    }

    public List<MangaReihe> findAllSortByTitel() {
        return mangaReiheRepository.findAll(Sort.by(Sort.Direction.ASC, "titel"));
    }

    /**
     * Speichert eine neuen MangaReihe in der Datenbank.
     * 
     * @param mangaReihe Die zu speichernde MangaReihe.
     * @return Die gespeicherte MangaReihe mit zugewiesener ID.
     */
    public MangaReihe saveMangaReihe(MangaReihe mangaReihe) {
        if (mangaReihe == null) {
            throw new IllegalArgumentException("MangaReihe darf nicht null sein");
        }
        return mangaReiheRepository.save(mangaReihe);
    }

    /**
     * Speichert eine neuen MangaReihe in der Datenbank.
     * 
     * @param mangaIndex           Der Index der MangaReihe.
     * @param statusId             Die ID des Status.
     * @param verlagId             Die ID des Verlags.
     * @param typId                Die ID des Typs.
     * @param formatId             Die ID des Formats.
     * @param titel                Der Titel der MangaReihe.
     * @param anzahlBaende         Die Anzahl der Bände in der Reihe.
     * @param preisProBand         Der Preis pro Band.
     * @param istVergriffen        Gibt an, ob die Reihe vergriffen ist.
     * @param istEbayPreis         Gibt an, ob es sich um einen eBay-Preis handelt.
     * @param anilistUrl           Die URL zu AniList.
     * @param sammelbandTypId      Die ID des Sammelbandtyps.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     * @param scrapedData          Zusätzliche Daten, die durch Web-Scraping
     *                             erhalten wurden.
     * @return Die gespeicherte MangaReihe mit zugewiesener ID.
     */
    @Transactional
    public MangaReihe saveMangaReihe(Integer mangaIndex, Long statusId, Long verlagId, Long typId, Long formatId,
            String titel, Integer anzahlBaende, BigDecimal preisProBand, Boolean istVergriffen, Boolean istEbayPreis,
            String anilistUrl, Long sammelbandTypId, BigDecimal gesamtpreisAenderung, Boolean istGelesen,
            Map<String, String> scrapedData) {
        System.out.println("SaveMangaReihe gestartet");

        MangaReihe mangaReihe = createMangaReihe(mangaIndex, statusId, verlagId, typId, formatId, titel, anzahlBaende,
                preisProBand, istVergriffen, istEbayPreis, gesamtpreisAenderung);

        MangaReihe savedMangaReihe = mangaReiheRepository.save(mangaReihe);

        System.out.println("MangaReihe Objekt erstellt");

        System.out.println("Setze Details");
        MangaDetails details = mangaDetailsService.createMangaDetails(savedMangaReihe, anilistUrl, sammelbandTypId,
                istGelesen,
                scrapedData);
        mangaDetailsRepository.save(details);
        System.out.println("Details gesetzt");

        System.out.println("Bände werden erzeugt");
        bandService.createAndSaveBaende(savedMangaReihe, anzahlBaende, preisProBand, gesamtpreisAenderung, scrapedData);
        System.out.println("Bände erzeugt");

        BigDecimal aenderungGesamtPreis = bandService.calculateAenderungGesamtpreis(savedMangaReihe);
        savedMangaReihe.setAenderungGesamtpreis(aenderungGesamtPreis);

        // Aktualisierung des Gesamtpreises der MangaReihe basierend auf den Bänden
        BigDecimal gesamtpreis = bandService.calculateGesamtpreis(savedMangaReihe);
        savedMangaReihe.setGesamtpreis(gesamtpreis);
        savedMangaReihe.setPreisProBand(
                gesamtpreis.divide(BigDecimal.valueOf(savedMangaReihe.getAnzahlBaende()), 2, RoundingMode.HALF_UP));
        mangaReiheRepository.save(savedMangaReihe);

        updateBaendeIstGelesenStatus(details);

        return savedMangaReihe;
    }

    /**
     * Erstellt eine neue MangaReihe mit den gegebenen Attributen.
     * 
     * @param mangaIndex           Der Index der MangaReihe.
     * @param statusId             Die ID des Status.
     * @param verlagId             Die ID des Verlags.
     * @param typId                Die ID des Typs.
     * @param formatId             Die ID des Formats.
     * @param titel                Der Titel der MangaReihe.
     * @param anzahlBaende         Die Anzahl der Bände in der Reihe.
     * @param preisProBand         Der Preis pro Band.
     * @param istVergriffen        Gibt an, ob die Reihe vergriffen ist.
     * @param istEbayPreis         Gibt an, ob es sich um einen eBay-Preis handelt.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     * @return Die erstellte MangaReihe.
     */
    private MangaReihe createMangaReihe(Integer mangaIndex, Long statusId, Long verlagId, Long typId, Long formatId,
            String titel, Integer anzahlBaende, BigDecimal preisProBand, Boolean istVergriffen, Boolean istEbayPreis,
            BigDecimal gesamtpreisAenderung) {
        MangaReihe mangaReihe = new MangaReihe();
        mangaReihe.setMangaIndex(mangaIndex);
        mangaReihe.setStatus(statusService.getStatusById(statusId).orElse(null));
        mangaReihe.setVerlag(verlagService.getVerlagById(verlagId).orElse(null));
        mangaReihe.setTyp(typService.getTypById(typId).orElse(null));
        mangaReihe.setFormat(formatService.getFormatById(formatId).orElse(null));
        mangaReihe.setTitel(titel);
        mangaReihe.setAnzahlBaende(anzahlBaende);
        mangaReihe.setIstVergriffen(istVergriffen);
        mangaReihe.setIstEbayPreis(istEbayPreis);

        mangaReihe.setPreisProBand(preisProBand);

        mangaReihe.setAenderungGesamtpreis(gesamtpreisAenderung);

        return mangaReihe;
    }

    public Long getNextId() {
        Long maxId = mangaReiheRepository.findMaxId();
        return (maxId == null) ? 1 : maxId + 1;
    }

    // ------------------------------Aktualisieren--------------------------------

    /**
     * Aktualisiert eine bestehende MangaReihe in der Datenbank zusammen mit den
     * zugehörigen
     * MangaDetails und Bänden. Die Methode findet die MangaReihe anhand ihrer ID
     * und aktualisiert
     * ihre Attribute sowie die zugehörigen MangaDetails und Bände, falls vorhanden.
     * 
     * @param mangaReiheId         Die ID der zu aktualisierenden MangaReihe.
     * @param mangaIndex           Der Index der MangaReihe.
     * @param statusId             Die ID des Status.
     * @param verlagId             Die ID des Verlags.
     * @param typId                Die ID des Typs.
     * @param formatId             Die ID des Formats.
     * @param titel                Der Titel der MangaReihe.
     * @param anzahlBaende         Die Anzahl der Bände in der Reihe.
     * @param preisProBand         Der Preis pro Band.
     * @param istVergriffen        Gibt an, ob die Reihe vergriffen ist.
     * @param istEbayPreis         Gibt an, ob es sich um einen eBay-Preis handelt.
     * @param anilistUrl           Die URL zu AniList.
     * @param coverUrl             Die URL zum Cover.
     * @param sammelbandTypId      Die ID des Sammelbandtyps.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     * @param scrapedData          Zusätzliche Daten, die durch Web-Scraping
     *                             erhalten wurden.
     * @return Ein Optional, das die aktualisierte MangaReihe enthält, falls die
     *         Aktualisierung erfolgreich war.
     */
    @Transactional
    public Optional<MangaReihe> updateMangaReihe(Long mangaReiheId, Integer mangaIndex, Long statusId, Long verlagId,
            Long typId, Long formatId, String titel, Integer anzahlBaende, BigDecimal preisProBand,
            Boolean istVergriffen, Boolean istEbayPreis, String anilistUrl, String coverUrl, Long sammelbandTypId,
            BigDecimal gesamtpreisAenderung, Boolean istGelesen, Integer reread, Map<String, String> scrapedData) {
        System.out.println("----------------------updateMangaReihe gestartet----------------------");

        Optional<MangaReihe> mangaReiheOptional = findById(mangaReiheId);
        if (!mangaReiheOptional.isPresent()) {
            System.out.println("Keine Reihe mit der ID gefunden");
            return Optional.empty();
        }
        MangaReihe mangaReihe = mangaReiheOptional.get();

        boolean anzahlBaendeChanged = !mangaReihe.getAnzahlBaende().equals(anzahlBaende);
        System.out.println("Update die Stammdaten");

        updateMangaReiheAttributes(mangaReihe, mangaIndex, statusId, verlagId, typId, formatId, titel, anzahlBaende,
                preisProBand, istVergriffen, istEbayPreis,
                gesamtpreisAenderung != null ? gesamtpreisAenderung : BigDecimal.ZERO);

        System.out.println("MangaReihe Objekt geupdated");

        System.out.println("Setze Details");

        MangaDetails details = mangaDetailsService.updateMangaDetails(mangaReihe, anilistUrl, coverUrl, istGelesen, reread,
                sammelbandTypId,
                scrapedData);
        mangaDetailsRepository.save(details);
        System.out.println("Details gesetzt");

        System.out.println("Update den Gelesenstatus");
        // Überprüfen und aktualisieren des istGelesen-Status der Bände
        updateBaendeIstGelesenStatus(details);

        System.out.println("Update die Bände");

        bandService.updateOrCreateBaende(mangaReihe, anzahlBaende, preisProBand, gesamtpreisAenderung, scrapedData,
                anzahlBaendeChanged);

        System.out.println("Bände geupdatet");

        if (anzahlBaendeChanged)
            details.setIstGelesen(false);

        mangaDetailsRepository.save(details);

        BigDecimal aenderungGesamtPreis = bandService.calculateAenderungGesamtpreis(mangaReihe);
        mangaReihe.setAenderungGesamtpreis(aenderungGesamtPreis);

        if (aenderungGesamtPreis.compareTo(BigDecimal.ZERO) != 0) {
            mangaReihe.setIstEbayPreis(true);
        }

        // Aktualisierung des Gesamtpreises der MangaReihe basierend auf den Bänden
        BigDecimal gesamtpreis = bandService.calculateGesamtpreis(mangaReihe);
        mangaReihe.setGesamtpreis(gesamtpreis);

        // Nur aktualisieren, wenn sich die Anzahl der Bände geändert hat
        if (anzahlBaendeChanged) {
            mangaReihe.setPreisProBand(
                    gesamtpreis.divide(BigDecimal.valueOf(mangaReihe.getAnzahlBaende()), 2, RoundingMode.HALF_UP));
        }
        mangaReihe.setAktualisiertAm(Timestamp.valueOf(LocalDateTime.now()));

        return Optional.of(mangaReiheRepository.save(mangaReihe));
    }

    /**
     * Aktualisiert die Attribute einer MangaReihe.
     * 
     * @param mangaReihe           Die zu aktualisierende MangaReihe.
     * @param mangaIndex           Der Index der MangaReihe.
     * @param statusId             Die ID des Status.
     * @param verlagId             Die ID des Verlags.
     * @param typId                Die ID des Typs.
     * @param formatId             Die ID des Formats.
     * @param titel                Der Titel der MangaReihe.
     * @param anzahlBaende         Die Anzahl der Bände in der Reihe.
     * @param preisProBand         Der Preis pro Band.
     * @param istVergriffen        Gibt an, ob die Reihe vergriffen ist.
     * @param istEbayPreis         Gibt an, ob es sich um einen eBay-Preis handelt.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     */
    private void updateMangaReiheAttributes(MangaReihe mangaReihe, Integer mangaIndex, Long statusId, Long verlagId,
            Long typId, Long formatId, String titel, Integer anzahlBaende, BigDecimal preisProBand,
            Boolean istVergriffen,
            Boolean istEbayPreis, BigDecimal gesamtpreisAenderung) {

        if (mangaIndex == null || !mangaIndex.equals(mangaReihe.getMangaIndex())) {
            mangaReihe.setMangaIndex(mangaIndex);
        }
        if (!mangaReihe.getStatus().getStatusId().equals(statusId)) {
            mangaReihe.setStatus(statusService.getStatusById(statusId).orElse(null));
        }
        if (!mangaReihe.getVerlag().getVerlagId().equals(verlagId)) {
            mangaReihe.setVerlag(verlagService.getVerlagById(verlagId).orElse(null));
        }
        if (!mangaReihe.getTyp().getTypId().equals(typId)) {
            mangaReihe.setTyp(typService.getTypById(typId).orElse(null));
        }
        if (!mangaReihe.getFormat().getFormatId().equals(formatId)) {
            mangaReihe.setFormat(formatService.getFormatById(formatId).orElse(null));
        }
        if (!mangaReihe.getTitel().equals(titel)) {
            mangaReihe.setTitel(titel);
        }
        if (!mangaReihe.getAnzahlBaende().equals(anzahlBaende)) {
            mangaReihe.setAnzahlBaende(anzahlBaende);
            mangaReihe.setPreisProBand(mangaReihe.getPreisProBand());
        }
        if (!mangaReihe.getIstVergriffen().equals(istVergriffen)) {
            mangaReihe.setIstVergriffen(istVergriffen);
        }
        if (!mangaReihe.getIstEbayPreis().equals(istEbayPreis)) {
            mangaReihe.setIstEbayPreis(istEbayPreis);
        }
        if (preisProBand.compareTo(BigDecimal.ZERO) != 0) {
            mangaReihe.setPreisProBand(preisProBand);
        }
        if (gesamtpreisAenderung.compareTo(BigDecimal.ZERO) != 0) {
            mangaReihe.setAenderungGesamtpreis(gesamtpreisAenderung);
        }
    }

    /**
     * Aktualisiert den mangaIndex einer MangaReihe.
     * 
     * @param mangaReiheId    Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerMangaIndex Der neue mangaIndex für die MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheMangaIndex(Long mangaReiheId, Integer neuerMangaIndex) {
        if (mangaReiheId == null || neuerMangaIndex == null) {
            throw new IllegalArgumentException("ID und mangaIndex dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setMangaIndex(neuerMangaIndex);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Status einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param statusId     Die ID des neuen Status.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheStatus(Long mangaReiheId, Long statusId) {
        if (mangaReiheId == null || statusId == null) {
            throw new IllegalArgumentException("ID und Status-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Status status = statusRepository.findById(statusId)
                    .orElseThrow(() -> new IllegalArgumentException("Status mit ID " + statusId + " nicht gefunden"));
            mangaReihe.setStatus(status);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Verlag einer MangaReihe.
     * Nur um die Option zu haben. Der Verlag sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param verlagId     Die ID des neuen Verlags.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheVerlag(Long mangaReiheId, Long verlagId) {
        if (mangaReiheId == null || verlagId == null) {
            throw new IllegalArgumentException("ID und Verlag-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Verlag verlag = verlagRepository.findById(verlagId)
                    .orElseThrow(() -> new IllegalArgumentException("Verlag mit ID " + verlagId + " nicht gefunden"));
            mangaReihe.setVerlag(verlag);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Typs einer MangaReihe.
     * Nur um die Option zu haben. Der Typ sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param typId        Die ID des neuen Typs.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheTyp(Long mangaReiheId, Long typId) {
        if (mangaReiheId == null || typId == null) {
            throw new IllegalArgumentException("ID und Typ-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Typ typ = typRepository.findById(typId)
                    .orElseThrow(() -> new IllegalArgumentException("Typ mit ID " + typId + " nicht gefunden"));
            mangaReihe.setTyp(typ);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Formats einer MangaReihe.
     * Nur um die Option zu haben. Das Format sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param formatId     Die ID des neuen Formats.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheFormat(Long mangaReiheId, Long formatId) {
        if (mangaReiheId == null || formatId == null) {
            throw new IllegalArgumentException("ID und Format-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Format format = formatRepository.findById(formatId)
                    .orElseThrow(() -> new IllegalArgumentException("Format mit ID " + formatId + " nicht gefunden"));
            mangaReihe.setFormat(format);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Titels einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerTitel   Der neue Titel der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheTitel(Long mangaReiheId, String neuerTitel) {
        if (mangaReiheId == null || neuerTitel == null) {
            throw new IllegalArgumentException("ID und Titel dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setTitel(neuerTitel);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert der AnzahlBaende einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neueAnzahl   Der Anzahl der Baende der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheAnzahlBaende(Long mangaReiheId, Integer neueAnzahl) {
        if (mangaReiheId == null || neueAnzahl == null) {
            throw new IllegalArgumentException("ID und Anzahl der Bände dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setAnzahlBaende(neueAnzahl);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des PreisProBand einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerPreis   Der PreisProBand der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReihePreisProBand(Long mangaReiheId, Double neuerPreis) {
        if (mangaReiheId == null || neuerPreis == null) {
            throw new IllegalArgumentException("ID und Preis pro Band dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            BigDecimal preisProBandBigDecimal = neuerPreis != null ? BigDecimal.valueOf(neuerPreis) : BigDecimal.ZERO;
            mangaReihe.setPreisProBand(preisProBandBigDecimal);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Gesamtpreis einer MangaReihe.
     * 
     * @param mangaReiheId     Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerGesamtpreis Der Gesamtpreis der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheGesamtpreis(Long mangaReiheId, BigDecimal neuerGesamtpreis) {
        if (mangaReiheId == null || neuerGesamtpreis == null) {
            throw new IllegalArgumentException("ID und Gesamtpreis dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setGesamtpreis(neuerGesamtpreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert die Änderung des Gesamtpreises einer MangaReihe.
     *
     * @param mangaReiheId         Die ID der MangaReihe, die aktualisiert werden
     *                             soll.
     * @param aenderungGesamtpreis Die Änderung des Gesamtpreises der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheAenderungGesamtpreis(Long mangaReiheId,
            BigDecimal aenderungGesamtpreis) {
        if (mangaReiheId == null || aenderungGesamtpreis == null) {
            throw new IllegalArgumentException("ID und Änderung des Gesamtpreises dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setAenderungGesamtpreis(aenderungGesamtpreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des istEbayPreis einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param istEbayPreis Handelt es sich um einen EbayPreis der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheIstEbayPreis(Long mangaReiheId, Boolean istEbayPreis) {
        if (mangaReiheId == null || istEbayPreis == null) {
            throw new IllegalArgumentException("ID und Ebay-Preis-Status dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setIstEbayPreis(istEbayPreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des istVergriffen einer MangaReihe.
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param istVergriffen Handelt es sich um eine vergriffene MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheIstVergriffen(Long mangaReiheId, Boolean istVergriffen) {
        if (mangaReiheId == null || istVergriffen == null) {
            throw new IllegalArgumentException("ID und Vergriffen-Status dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setIstVergriffen(istVergriffen);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Überprüft und aktualisiert den istGelesen-Status aller Bände basierend auf
     * dem istGelesen-Status der MangaDetails.
     * 
     * @param mangaDetails Die MangaDetails, deren istGelesen-Status überprüft und
     *                     aktualisiert werden soll.
     */
    private void updateBaendeIstGelesenStatus(MangaDetails mangaDetails) {
        List<Band> baende = bandRepository.findByMangaReiheId(mangaDetails.getMangaReihe().getId());

        if (mangaDetails.isIstGelesen()) {
            baende.forEach(band -> {
                if (!band.isIstGelesen()) {
                    band.setIstGelesen(true);
                    bandRepository.save(band);
                }
            });
        } else {
            baende.forEach(band -> {
                if (band.isIstGelesen()) {
                    band.setIstGelesen(false);
                    bandRepository.save(band);
                }
            });
        }
    }

    // ------------------------------Filtern--------------------------------

    /**
     * Filtert MangaReihen nach Status.
     * 
     * @param statusId Die ID des Status nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Status entsprechen.
     */
    public List<MangaReihe> findByStatus(Long statusId) {
        if (statusId == null) {
            throw new IllegalArgumentException("Status-ID darf nicht null sein");
        }

        // Hole das Status-Objekt anhand der ID
        Optional<Status> status = statusRepository.findById(statusId);

        // Überprüfe, ob der Status vorhanden ist
        if (!status.isPresent()) {
            throw new IllegalArgumentException("Status mit der ID " + statusId + " existiert nicht");
        }

        // Verwende das Status-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByStatus(status.get());
    }

    /**
     * Filtert MangaReihen nach Verlag.
     * 
     * @param verlagId Die ID des Verlags nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Verlag entsprechen.
     */
    public List<MangaReihe> findByVerlag(Long verlagId) {
        if (verlagId == null) {
            throw new IllegalArgumentException("Verlag-ID darf nicht null sein");
        }

        // Hole das Verlag-Objekt anhand der ID
        Optional<Verlag> verlag = verlagRepository.findById(verlagId);

        // Überprüfe, ob der Verlag vorhanden ist
        if (!verlag.isPresent()) {
            throw new IllegalArgumentException("Verlag mit der ID " + verlagId + " existiert nicht");
        }

        // Verwende das Verlag-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByVerlag(verlag.get());
    }

    /**
     * Filtert MangaReihen nach Typ.
     * 
     * @param typId Die ID des Typs nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Typ entsprechen.
     */
    public List<MangaReihe> findByTyp(Long typId) {
        if (typId == null) {
            throw new IllegalArgumentException("Typ-ID darf nicht null sein");
        }

        // Hole das Typ-Objekt anhand der ID
        Optional<Typ> typ = typRepository.findById(typId);

        // Überprüfe, ob der Typ vorhanden ist
        if (!typ.isPresent()) {
            throw new IllegalArgumentException("Typ mit der ID " + typId + " existiert nicht");
        }

        // Verwende das Typ-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByTyp(typ.get());
    }

    /**
     * Filtert MangaReihen nach Format.
     * 
     * @param formatId Die ID des Formats nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Format entsprechen.
     */
    public List<MangaReihe> findByFormat(Long formatId) {
        if (formatId == null) {
            throw new IllegalArgumentException("Format-ID darf nicht null sein");
        }

        // Hole das Format-Objekt anhand der ID
        Optional<Format> format = formatRepository.findById(formatId);

        // Überprüfe, ob das Format vorhanden ist
        if (!format.isPresent()) {
            throw new IllegalArgumentException("Format mit der ID " + formatId + " existiert nicht");
        }

        // Verwende das Format-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByFormat(format.get());
    }

    /**
     * Filtert MangaReihen nach Jahr.
     * 
     * @param jahr Das Jahr nach welchem gefiltert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByErstelltAmYear(int jahr) {
        if (jahr < 1900 || jahr > java.time.Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        return mangaReiheRepository.findByErstelltAmYear(jahr);
    }

    /**
     * Filtert MangaReihen nach Jahr und Monat.
     * 
     * @param jahr  Das Jahr nach welchem gefiltert wird.
     * @param monat Der Monat nach welchem gefiltert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByErstelltAmYearAndMonth(int jahr, int monat) {
        if (jahr < 1900 || jahr > java.time.Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }
        return mangaReiheRepository.findByErstelltAmYearAndMonth(jahr, monat);
    }

    /**
     * Filtert MangaReihen nach dem aktuellen Jahr und einem spezifischen Monat.
     * 
     * @param monat Der Monat, nach dem gefiltert wird. Muss zwischen 1 und 12
     *              liegen.
     * @return Eine Liste von MangaReihen, die im aktuellen Jahr und im angegebenen
     *         Monat erstellt wurden.
     * @throws IllegalArgumentException wenn der Monat außerhalb des Bereichs 1-12
     *                                  liegt.
     */
    public List<MangaReihe> findByErstelltAmCurrentYearAndMonth(int monat) {
        // Ermitteln des aktuellen Jahres
        int currentYear = Year.now().getValue();

        // Überprüfen, ob der Monat im gültigen Bereich liegt
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }

        // Aufruf der Methode findByErstelltAmYearAndMonth mit dem aktuellen Jahr und
        // dem übergebenen Monat
        return findByErstelltAmYearAndMonth(currentYear, monat);
    }

    /**
     * Filtert MangaReihe nach dem bestimmten Wert von istGelesen
     * 
     * @param istGelesen Der gesuchte Wert von istGelesen
     * @return Eine Liste von MangaReihe, die den gesuchten Wert von istGelesen
     *         erfüllen
     */
    public List<MangaReihe> findByIstGelesen(Boolean istGelesen) {
        if (istGelesen == null) {
            throw new IllegalArgumentException("istGelesen ist ungültig");
        }
        return mangaReiheRepository.findByIstGelesen(istGelesen);
    }

    // ------------------------------Suchen--------------------------------

    /**
     * Sucht nach einer bestimmten ID.
     * 
     * @param id Die ID welche gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Die ID darf nicht null sein");
        }
        return mangaReiheRepository.findById(id);
    }

    /**
     * Sucht nach einem bestimmten Index.
     * 
     * @param mangaIndex Der Index welche gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByMangaIndex(Integer mangaIndex) {
        if (mangaIndex == null) {
            throw new IllegalArgumentException("Der mangaIndex darf nicht null sein");
        }
        return mangaReiheRepository.findByMangaIndex(mangaIndex);
    }

    /**
     * Sucht nach einer bestimmten Titel.
     * 
     * @param titel Der Titel nach welchem gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByTitel(String titel) {
        if (titel == null || titel.trim().isEmpty()) {
            throw new IllegalArgumentException("Der Titel darf nicht leer sein");
        }
        return mangaReiheRepository.findByTitelContainingIgnoreCase(titel);
    }

    // Methode in MangaReiheService
    public List<MangaReihe> findByFullTitel(String titel) {
        if (titel == null || titel.trim().isEmpty()) {
            throw new IllegalArgumentException("Der Titel darf nicht leer sein");
        }
        return mangaReiheRepository.findByTitel(titel);
    }

    /**
     * Sucht nach MangaReihen basierend auf einem Suchbegriff, der entweder ein
     * Titel oder ein Index sein kann.
     * Wenn der Suchbegriff in eine Zahl umgewandelt werden kann, wird angenommen,
     * dass es sich um einen Index handelt,
     * und es wird nach diesem Index gesucht. Andernfalls wird angenommen, dass es
     * sich um einen Titel handelt,
     * und es wird nach Titeln gesucht, die den Suchbegriff enthalten.
     *
     * @param searchQuery Der Suchbegriff, der entweder ein Titel oder ein Index
     *                    sein kann.
     * @return Eine Liste von MangaReihen, die dem Suchbegriff entsprechen. Die
     *         Liste kann leer sein, wenn keine Übereinstimmungen gefunden werden.
     * @throws IllegalArgumentException wenn die Suchanfrage leer oder null ist.
     */
    public List<MangaReihe> searchByTitelOrIndex(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Die Suchanfrage darf nicht leer sein");
        }

        // Versuchen Sie, die Suchanfrage in eine Zahl umzuwandeln
        Integer index = null;
        try {
            index = Integer.parseInt(searchQuery);
        } catch (NumberFormatException e) {
            // Nichts tun, wenn die Umwandlung fehlschlägt, da es sich um einen Titel
            // handeln könnte
        }

        List<MangaReihe> result;

        // Wenn die Suchanfrage eine Zahl ist, suchen Sie nach dem Index
        if (index != null) {
            result = mangaReiheRepository.findByMangaIndex(index);
        } else {
            // Andernfalls suchen Sie nach dem Titel
            result = findByTitel(searchQuery);
        }

        return result;
    }

    public MangaReihe createMangaReihe(MangaReihe mangaReihe) {
        // Erstellen eines neuen MangaDetails Objekts
        MangaDetails mangaDetails = new MangaDetails();
        mangaDetails.setMangaReihe(mangaReihe); // Setzen der Beziehung

        // Setzen der MangaDetails in der MangaReihe
        mangaReihe.setMangaDetails(mangaDetails);

        // Speichern der MangaReihe (und automatisch der MangaDetails)
        return mangaReiheRepository.save(mangaReihe);
    }

    // ------------------------------Sortieren--------------------------------

    /**
     * Methode zum Sortieren von MangaReihen nach einem bestimmten Attribut
     * 
     * @param sortAttribute Das Attribut nach welchem sortiert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findAllSorted(String sortAttribute) {
        if (sortAttribute == null || sortAttribute.trim().isEmpty()) {
            throw new IllegalArgumentException("Sortierattribut darf nicht leer sein");
        }
        return mangaReiheRepository.findAll(Sort.by(sortAttribute));
    }

    /**
     * Methode zum Sortieren von MangaReihen nach einem bestimmten Attribut und
     * Richtung
     * 
     * @param sortAttribute Das Attribut nach welchem sortiert wird.
     * @param direction     Ob absteigend oder aufsteigend sortiert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findAllSorted(String sortAttribute, Sort.Direction direction) {
        if (sortAttribute == null || sortAttribute.trim().isEmpty()) {
            throw new IllegalArgumentException("Sortierattribut darf nicht leer sein");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Sortierrichtung darf nicht null sein");
        }
        return mangaReiheRepository.findAll(Sort.by(direction, sortAttribute));
    }

    // ------------------------------Statistiken--------------------------------
    /**
     * Methoden zum Berechnen der Gesamtanzahl aller Baende
     * 
     * @return Die Gesamtanzahl aller Baende
     */
    public Integer berechneGesamtAnzahlBaende() {
        Integer anzahl = mangaReiheRepository.findeGesamtAnzahlBaende();
        return anzahl != null ? anzahl : 0;
    }

    /**
     * Methode zum Berechnen der Gesamtanzahl aller Baende unter Berücksichtigung
     * des Sammelband Multiplikators
     * 
     * @return Die Gesamtanzahl aller Baende mit Sammelband Multiplikator
     */
    public Integer berechneGesamtAnzahlMitMultiplikator() {
        Integer anzahlMitMultiplikator = mangaReiheRepository.findeGesamtAnzahlMitMultiplikator();
        return anzahlMitMultiplikator != null ? anzahlMitMultiplikator : 0;
    }

    /**
     * Methode zum Berechnen des Gesamtpreis aller Baende
     * 
     * @return Der Gesamtpreis aller Baende
     */
    public BigDecimal getGesamtSummeGesamtpreis() {
        return mangaReiheRepository.findeGesamtSummeGesamtpreis();
    }

    /**
     * Methode zum Berechnen des durchschnittlichen Preises pro Band.
     * 
     * @return Der durchschnittliche Preis pro Band
     */
    public BigDecimal berechneDurchschnittlichenPreisProBand() {
        // Berechnen der Gesamtanzahl der Bände
        Integer gesamtAnzahlBaende = berechneGesamtAnzahlBaende();

        // Abrufen der Gesamtsumme der Gesamtpreise
        BigDecimal gesamtSummeGesamtpreis = getGesamtSummeGesamtpreis();

        // Überprüfen, ob die Gesamtanzahl der Bände nicht null ist und größer als 0 ist
        if (gesamtAnzahlBaende != null && gesamtAnzahlBaende > 0) {
            // Berechnen des durchschnittlichen Preises pro Band und Rückgabe des
            // Ergebnisses
            return gesamtSummeGesamtpreis.divide(BigDecimal.valueOf(gesamtAnzahlBaende), 2, RoundingMode.HALF_UP);
        } else {
            // Falls die Gesamtanzahl der Bände null oder nicht größer als 0 ist, wird 0
            // zurückgegeben
            return BigDecimal.ZERO;
        }
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit nur einem Band.
     * 
     * @return Die Anzahl der Manga-Reihen mit nur einem Band
     */
    public int getReihenMitEinemBand() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit nur einem
        // Band und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitEinemBand();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit zwei bis fünf Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit zwei bis fünf Bänden
     */
    public int getReihenMitZweiBisFuenfBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit zwei bis
        // fünf Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitZweiBisFuenfBaenden();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit sechs bis zehn Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit sechs bis zehn Bänden
     */
    public int getReihenMitSechsBisZehnBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit sechs bis
        // zehn Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitSechsBisZehnBaenden();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit elf bis zwanzig Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit elf bis zwanzig Bänden
     */
    public int getReihenMitElfBisZwanzigBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit elf bis
        // zwanzig Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitElfBisZwanzigBaenden();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit einundzwanzig bis fünfzig
     * Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit einundzwanzig bis fünfzig Bänden
     */
    public int getReihenMitEinundzwanzigBisFuenfzigBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit
        // einundzwanzig bis fünfzig Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitEinundzwanzigBisFuenfzigBaenden();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit einundfünfzig bis hundert
     * Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit einundfünfzig bis hundert Bänden
     */
    public int getReihenMitEinundfuenfzigBisHundertBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit
        // einundfünfzig bis hundert Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitEinundfuenfzigBisHundertBaenden();
    }

    /**
     * Methode zum Abrufen der Anzahl der Manga-Reihen mit mehr als hundert Bänden.
     * 
     * @return Die Anzahl der Manga-Reihen mit mehr als hundert Bänden
     */
    public int getReihenMitMehrAlsHundertBaenden() {
        // Aufrufen der Repository-Methode zur Zählung der Manga-Reihen mit mehr als
        // hundert Bänden und Rückgabe des Ergebnisses
        return mangaReiheRepository.countReihenMitMehrAlsHundertBaenden();
    }

}
