package de.mangalib.service;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.Sammelband;
import de.mangalib.repository.EinkaufslisteRepository;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class EinkaufslisteService {
    private final EinkaufslisteRepository einkaufslisteRepository;

    @Autowired
    private VerlagService verlagService;

    @Autowired
    private TypService typService;

    @Autowired
    private FormatService formatService;

    @Autowired
    private SammelbandService sammelbaendeService;

    // Konstruktorbasierte Dependency Injection des FormatRepository
    public EinkaufslisteService(EinkaufslisteRepository einkaufslisteRepository) {
        this.einkaufslisteRepository = einkaufslisteRepository;
    }

    /**
     * Findet ein EinkaufslisteItem anhand der ID.
     * 
     * @param id Die ID des EinkaufslisteItems.
     * @return Ein Optional von EinkaufslisteItem.
     */
    public Optional<EinkaufslisteItem> findItemById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        return einkaufslisteRepository.findById(id);
    }

    /**
     * Ruft alle EinkaufslisteItems für einen bestimmten Monat ab.
     * 
     * @param date Das Datum, das den Monat und das Jahr bestimmt, für den die Items
     *             abgerufen werden sollen.
     * @return Eine Liste von EinkaufslisteItems, die in dem angegebenen Monat
     *         erscheinen.
     * @throws IllegalArgumentException wenn das übergebene Datum null ist.
     */
    public List<EinkaufslisteItem> getItemsForMonth(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Das Datum darf nicht null sein.");
        }

        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        List<EinkaufslisteItem> items = einkaufslisteRepository.findByErscheinungsdatumBetween(start, end);
        System.out.println("Startdatum: " + start + ", Enddatum: " + end + ", Anzahl der Items: " + items.size());
        return items;
    }

    /**
     * Speichert ein EinkaufslisteItem.
     * 
     * @param item Das zu speichernde EinkaufslisteItem.
     * @return Das gespeicherte EinkaufslisteItem.
     */
    public EinkaufslisteItem saveItem(EinkaufslisteItem item) {
        if (item == null) {
            throw new IllegalArgumentException("EinkaufslisteItem darf nicht null sein");
        }
        // Weitere Validierungen nach Bedarf...
        return einkaufslisteRepository.save(item);
    }

    /**
     * Löscht ein EinkaufslisteItem anhand der ID.
     * 
     * @param id Die ID des zu löschenden EinkaufslisteItems.
     */
    public void deleteItemById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        einkaufslisteRepository.deleteById(id);
    }

    /**
     * Listet alle EinkaufslisteItems auf.
     * 
     * @return Eine Liste von EinkaufslisteItems.
     */
    public List<EinkaufslisteItem> findAllItems() {
        return einkaufslisteRepository.findAll();
    }

    /**
     * Speichert eine neue MangaReihe in der Datenbank mit den zugehörigen
     * MangaDetails und Bänden.
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
     * @return Das gespeicherte MangaReihe-Objekt.
     */
    @Transactional
    public EinkaufslisteItem saveEinkaufslisteItem(Integer mangaIndex, Long verlagId, Long typId, Long formatId,
            String titel, Integer anzahlBaende, Double preisProBand, Boolean istVergriffen, Boolean istEbayPreis,
            String anilistUrl, Long sammelbandTypId, Double gesamtpreisAenderung, LocalDate erscheinungsdatum, Map<String, String> scrapedData) {
        // Erstellen der MangaReihe
        EinkaufslisteItem einkaufslisteItem = new EinkaufslisteItem();
        einkaufslisteItem.setMangaIndex(mangaIndex);
        // Setzen der abhängigen Objekte wie Status, Verlag, Typ, Format basierend auf
        // deren IDs
        einkaufslisteItem.setVerlagId(verlagService.getVerlagById(verlagId).orElse(null));
        einkaufslisteItem.setTypId(typService.getTypById(typId).orElse(null));
        einkaufslisteItem.setFormatId(formatService.getFormatById(formatId).orElse(null));
        // Setzen der anderen Attribute
        einkaufslisteItem.setTitel(titel);
        einkaufslisteItem.setAnzahlBaende(anzahlBaende);
        einkaufslisteItem.setIstEbayPreis(istEbayPreis);
        einkaufslisteItem.setIstVergriffen(istVergriffen);

        // Berechnen des Gesamtpreises
        BigDecimal preisProBandBigDecimal = BigDecimal.valueOf(preisProBand);
        einkaufslisteItem.setPreis(preisProBandBigDecimal);
        BigDecimal gesamtpreisAenderungBigDecimal = gesamtpreisAenderung != null
                ? BigDecimal.valueOf(gesamtpreisAenderung)
                : BigDecimal.ZERO;
        BigDecimal anzahlBaendeBigDecimal = BigDecimal.valueOf(anzahlBaende);

        BigDecimal gesamtpreis = preisProBandBigDecimal.multiply(anzahlBaendeBigDecimal)
                .add(gesamtpreisAenderungBigDecimal);
        einkaufslisteItem.setGesamtpreis(gesamtpreis);
        einkaufslisteItem.setAenderungGesamtpreis(gesamtpreisAenderungBigDecimal);
        einkaufslisteItem.setErscheinungsdatum(erscheinungsdatum);

        if (anilistUrl != null)
            einkaufslisteItem.setAnilistUrl(anilistUrl);
        if (sammelbandTypId != null) {
            Sammelband sammelband = sammelbaendeService.findById(sammelbandTypId).orElse(null);
            einkaufslisteItem.setSammelbaendeId(sammelband);
            System.out.println(einkaufslisteItem.getSammelbaendeId().getId());
        }

        if (scrapedData.containsKey("Band 1 Bild Url")) {
            einkaufslisteItem.setCoverUrl(scrapedData.get("Band 1 Bild Url"));
        }

        if (scrapedData.containsKey("Deutsche Ausgabe Status")) {
            einkaufslisteItem.setStatusDe(String.valueOf(scrapedData.get("Deutsche Ausgabe Status")));
        }

        if (scrapedData.containsKey("Deutsche Ausgabe Bände")) {
            try {
                String baendeString = scrapedData.get("Deutsche Ausgabe Bände").replace("+", "").trim();
                einkaufslisteItem.setAnzahlBaendeDe(Integer.parseInt(baendeString));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Status")) {
            einkaufslisteItem.setStatusErstv(String.valueOf(scrapedData.get("Erstveröffentlichung Status")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Herkunft")) {
            einkaufslisteItem.setHerkunft(String.valueOf(scrapedData.get("Erstveröffentlichung Herkunft")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Startjahr")) {
            try {
                einkaufslisteItem.setStartJahr(Integer.parseInt(scrapedData.get("Erstveröffentlichung Startjahr")));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Bände")) {
            try {
                String baendeString = scrapedData.get("Erstveröffentlichung Bände").replace("+", "").trim();
                einkaufslisteItem.setAnzahlBaendeErstv(Integer.parseInt(baendeString));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }

        // Speichern in der Datenbank
        EinkaufslisteItem savedeinkaufslisteItem = einkaufslisteRepository.save(einkaufslisteItem);

        return savedeinkaufslisteItem;
    }
}
