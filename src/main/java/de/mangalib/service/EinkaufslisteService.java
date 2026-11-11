package de.mangalib.service;

import de.mangalib.validation.WriteValidation;

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

    public List<EinkaufslisteItem> undatedItems() {
        return einkaufslisteRepository.findByErscheinungsdatumIsNullOrderByIdAsc();
    }

    @Transactional
    public Map<String, Object> editState(long id) {
        return einkaufslisteRepository.findById(id).map(this::editState).orElse(null);
    }

    private Map<String, Object> editState(EinkaufslisteItem item) {
        var detail = item.getEinkaufslisteItemDetails();
        var state = ShoppingState.fields(item, detail);
        state.put("editable", Boolean.FALSE.equals(item.getGekauft()));
        state.put("editToken", ShoppingState.token(item, detail, false));
        return state;
    }

    @Transactional
    public Map<String, Object> updateShoppingItem(long id, Map<String, Object> input) {
        WriteValidation.require(Set.of("editToken", "titel", "mangaIndex", "verlagId", "typId", "formatId",
                "anzahlBaende", "preisProBand", "gesamtpreisAenderung", "erscheinungsdatum", "istEbayPreis",
                "istVergriffen", "sammelbandTypId").containsAll(input.keySet()));
        Object token = input.get("editToken");
        WriteValidation.require(token instanceof String text && text.matches("[a-f0-9]{64}"));
        var item = einkaufslisteRepository.findByIdForPurchase(id).orElse(null);
        if (item == null) return null;
        var detail = item.getEinkaufslisteItemDetails();
        if (!Boolean.FALSE.equals(item.getGekauft()) || !token.equals(ShoppingState.token(item, detail, false)))
            throw new ShoppingConflict();

        boolean financial = input.containsKey("preisProBand") || input.containsKey("gesamtpreisAenderung")
                || input.containsKey("anzahlBaende");
        if (financial) {
            Integer count = input.containsKey("anzahlBaende") ? integer(input.get("anzahlBaende")) : item.getAnzahlBaende();
            WriteValidation.volumes(count);
            BigDecimal price = input.containsKey("preisProBand") ? amount(input.get("preisProBand")) : item.getPreis();
            BigDecimal adjustment = input.containsKey("gesamtpreisAenderung")
                    ? amount(input.get("gesamtpreisAenderung")) : item.getAenderungGesamtpreis();
            WriteValidation.require(price != null && adjustment != null);
            item.setGesamtpreis(Money.amount(price.multiply(BigDecimal.valueOf(count)).add(adjustment)));
            item.setAnzahlBaende(count);
            item.setPreis(price);
            item.setAenderungGesamtpreis(adjustment);
        }
        if (input.containsKey("titel")) {
            String title = string(input.get("titel"));
            WriteValidation.title(title);
            item.setTitel(title);
        }
        if (input.containsKey("mangaIndex")) {
            Object value = input.get("mangaIndex");
            Integer index = value == null ? null : integer(value);
            WriteValidation.index(index);
            item.setMangaIndex(index);
        }
        if (input.containsKey("verlagId")) item.setVerlagId(verlagService.getVerlagById(reference(input.get("verlagId")))
                .orElseThrow(IllegalArgumentException::new));
        if (input.containsKey("typId")) item.setTypId(typService.getTypById(reference(input.get("typId")))
                .orElseThrow(IllegalArgumentException::new));
        if (input.containsKey("formatId")) item.setFormatId(formatService.getFormatById(reference(input.get("formatId")))
                .orElseThrow(IllegalArgumentException::new));
        if (input.containsKey("erscheinungsdatum")) {
            Object date = input.get("erscheinungsdatum");
            item.setErscheinungsdatum(date == null ? null : LocalDate.parse(string(date)));
        }
        if (input.containsKey("istEbayPreis")) item.setIstEbayPreis(flag(input.get("istEbayPreis")));
        if (input.containsKey("istVergriffen")) item.setIstVergriffen(flag(input.get("istVergriffen")));
        if (input.containsKey("sammelbandTypId")) {
            if (detail == null || !Objects.equals(detail.getId(), item.getId()) || detail.getEinkaufslisteItem() == null
                    || !Objects.equals(detail.getEinkaufslisteItem().getId(), item.getId())) throw new ShoppingConflict();
            Object omnibus = input.get("sammelbandTypId");
            detail.setSammelbaendeId(omnibus == null ? null : sammelbaendeService.findById(reference(omnibus))
                    .orElseThrow(IllegalArgumentException::new));
        }
        einkaufslisteRepository.flush();
        return editState(item);
    }

    public static final class ShoppingConflict extends RuntimeException {}

    private static String string(Object value) {
        WriteValidation.require(value instanceof String);
        return (String) value;
    }

    private static Integer integer(Object value) {
        WriteValidation.require(value instanceof Integer);
        return (Integer) value;
    }

    private static boolean flag(Object value) {
        WriteValidation.require(value instanceof Boolean);
        return (Boolean) value;
    }

    private static long reference(Object value) {
        String id = string(value);
        WriteValidation.require(id.matches("[1-9][0-9]{0,18}"));
        return Long.parseLong(id);
    }

    private static BigDecimal amount(Object value) {
        return Money.amount(WriteValidation.money(string(value)));
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
     * Liefert die exakten Monatsbeträge gekaufter Positionen nach Erscheinungsdatum.
     * 
     * @param jahr Das Jahr, für das die Daten abgerufen werden.
     * @return Monate und unformatierte Dezimalbeträge.
     */
    public Map<Integer, BigDecimal> getGeldProMonat(int jahr) {
        List<Object[]> results = einkaufslisteRepository.findGeldProMonat(jahr);
        Map<Integer, BigDecimal> amounts = new HashMap<>();
        for (Object[] result : results) {
            amounts.put(((Number) result[0]).intValue(), (BigDecimal) result[1]);
        }
        return amounts;
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
            String titel, Integer anzahlBaende, BigDecimal preisProBand, Boolean istVergriffen, Boolean istEbayPreis,
            String anilistUrl, Long sammelbandTypId, BigDecimal gesamtpreisAenderung, LocalDate erscheinungsdatum, Map<String, String> scrapedData) {
        WriteValidation.volumes(anzahlBaende);
        Money.amount(preisProBand);
        Money.amount(gesamtpreisAenderung == null ? BigDecimal.ZERO : gesamtpreisAenderung);
        Money.amount(preisProBand.multiply(BigDecimal.valueOf(anzahlBaende))
                .add(gesamtpreisAenderung == null ? BigDecimal.ZERO : gesamtpreisAenderung));
        scrapedData = WriteValidation.scrapedData(scrapedData);
        // Erstellen der MangaReihe
        EinkaufslisteItem einkaufslisteItem = new EinkaufslisteItem();
        einkaufslisteItem.setMangaIndex(mangaIndex);
        // Setzen der abhängigen Objekte wie Status, Verlag, Typ, Format basierend auf
        // deren IDs
        einkaufslisteItem.setVerlagId(verlagService.getVerlagById(verlagId).orElseThrow(() -> new IllegalArgumentException("Ungültige Referenz.")));
        einkaufslisteItem.setTypId(typService.getTypById(typId).orElseThrow(() -> new IllegalArgumentException("Ungültige Referenz.")));
        einkaufslisteItem.setFormatId(formatService.getFormatById(formatId).orElseThrow(() -> new IllegalArgumentException("Ungültige Referenz.")));
        Sammelband sammelband = sammelbandTypId == null ? null : sammelbaendeService.findById(sammelbandTypId)
                .orElseThrow(() -> new IllegalArgumentException("Ungültige Referenz."));
        // Setzen der anderen Attribute
        einkaufslisteItem.setTitel(titel);
        einkaufslisteItem.setAnzahlBaende(anzahlBaende);
        einkaufslisteItem.setIstEbayPreis(istEbayPreis);
        einkaufslisteItem.setIstVergriffen(istVergriffen);

        // Berechnen des Gesamtpreises
        BigDecimal preisProBandBigDecimal = preisProBand;
        einkaufslisteItem.setPreis(preisProBandBigDecimal);
        BigDecimal gesamtpreisAenderungBigDecimal = gesamtpreisAenderung != null
                ? gesamtpreisAenderung
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
