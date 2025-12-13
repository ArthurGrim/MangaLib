package de.mangalib.service;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.EinkaufslisteItemDetails;
import de.mangalib.entity.Sammelband;
import de.mangalib.repository.EinkaufslisteRepository;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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

    @Autowired
    private EinkaufslisteItemDetailsService einkaufslisteItemDetailsService;

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
        
        // Hinzufügen der Sortierung nach Erscheinungsdatum
        Sort sort = Sort.by("erscheinungsdatum").ascending();
        
        List<EinkaufslisteItem> items = einkaufslisteRepository.findByErscheinungsdatumBetween(start, end, sort);
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
     * Holt die Anzahl der gekauften Bände pro Monat für ein bestimmtes Jahr.
     * 
     * @param jahr Das Jahr, für das die Daten abgerufen werden.
     * @return Eine Map, die den Monat als Schlüssel und die Anzahl der gekauften Bände als Wert enthält.
     */
    public Map<Integer, Long> getBaendeProMonat(int jahr) {
        List<Object[]> results = einkaufslisteRepository.findBandeProMonat(jahr);
        return mapResultsToLong(results);
    }

    /**
     * Holt die Gesamtausgaben pro Monat für ein bestimmtes Jahr und formatiert den Betrag mit einem Euro-Zeichen.
     * 
     * @param jahr Das Jahr, für das die Daten abgerufen werden.
     * @return Eine Map, die den Monat als Schlüssel und den formatierten Betrag (als String) mit Euro-Zeichen als Wert enthält.
     */
    public Map<Integer, Long> getGeldProMonat(int jahr) {
        List<Object[]> results = einkaufslisteRepository.findGeldProMonat(jahr);
        return mapResultsToLong(results);
    }

    /**
     * Hilfsmethode, um die rohen Ergebnisse aus den Repository-Abfragen in eine Map mit Long-Werten zu konvertieren.
     * 
     * @param results Die rohen Ergebnisse, die aus der Datenbankabfrage stammen.
     * @return Eine Map, die den Monat als Schlüssel und die Anzahl der gekauften Bände (als Long) als Wert enthält.
     */
    private Map<Integer, Long> mapResultsToLong(List<Object[]> results) {
        Map<Integer, Long> resultMap = new HashMap<>();
        for (Object[] result : results) {
            Integer month = (Integer) result[0];
            Long value = ((Number) result[1]).longValue();
            resultMap.put(month, value);
        }
        return resultMap;
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

        // Speichern in der Datenbank
        EinkaufslisteItem savedeinkaufslisteItem = einkaufslisteRepository.save(einkaufslisteItem);

        // Erstellen und Speichern der EinkaufslisteItemDetails
        EinkaufslisteItemDetails einkaufslistenItemDetails = new EinkaufslisteItemDetails();
        einkaufslistenItemDetails.setEinkaufslisteItem(savedeinkaufslisteItem);

        if (anilistUrl != null)
        einkaufslistenItemDetails.setAnilistUrl(anilistUrl);
        if (sammelbandTypId != null) {
            Sammelband sammelband = sammelbaendeService.findById(sammelbandTypId).orElse(null);
            einkaufslistenItemDetails.setSammelbaendeId(sammelband);
            System.out.println(einkaufslistenItemDetails.getSammelbaendeId().getId());
        }

        if (scrapedData.containsKey("Band 1 Bild Url")) {
            einkaufslistenItemDetails.setCoverUrl(scrapedData.get("Band 1 Bild Url"));
        }

        if (scrapedData.containsKey("Deutsche Ausgabe Status")) {
            einkaufslistenItemDetails.setStatusDe(String.valueOf(scrapedData.get("Deutsche Ausgabe Status")));
        }

        if (scrapedData.containsKey("Deutsche Ausgabe Bände")) {
            try {
                String baendeString = scrapedData.get("Deutsche Ausgabe Bände").replace("+", "").trim();
                einkaufslistenItemDetails.setAnzahlBaendeDe(Integer.parseInt(baendeString));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Status")) {
            einkaufslistenItemDetails.setStatusErstv(String.valueOf(scrapedData.get("Erstveröffentlichung Status")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Herkunft")) {
            einkaufslistenItemDetails.setHerkunft(String.valueOf(scrapedData.get("Erstveröffentlichung Herkunft")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Startjahr")) {
            try {
                einkaufslistenItemDetails.setStartJahr(Integer.parseInt(scrapedData.get("Erstveröffentlichung Startjahr")));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Bände")) {
            try {
                String baendeString = scrapedData.get("Erstveröffentlichung Bände").replace("+", "").trim();
                einkaufslistenItemDetails.setAnzahlBaendeErstv(Integer.parseInt(baendeString));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }

        einkaufslisteItemDetailsService.saveEinkaufslisteItemDetails(einkaufslistenItemDetails);

        return savedeinkaufslisteItem;
    }

    @Transactional
    public void updateGekauft(long id, boolean gekauft) {
        // Validierung der ID
        if (id <= 0) {
            throw new IllegalArgumentException("Ungültige ID: " + id);
        }

        // Suche das Item mit der gegebenen ID
        EinkaufslisteItem item = einkaufslisteRepository.findById(id).orElseThrow(() -> 
            new IllegalArgumentException("Item mit der ID " + id + " nicht gefunden"));

        // Aktualisiere den gekauft-Status
        item.setGekauft(gekauft);

        // Speichere das aktualisierte Item
        einkaufslisteRepository.save(item);
    }
}
