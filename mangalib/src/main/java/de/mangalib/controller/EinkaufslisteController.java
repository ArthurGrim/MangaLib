package de.mangalib.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Sammelband;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.service.EinkaufslisteService;
import de.mangalib.service.FormatService;
import de.mangalib.service.MangaReiheService;
import de.mangalib.service.SammelbandService;
import de.mangalib.service.StatusService;
import de.mangalib.service.TypService;
import de.mangalib.service.VerlagService;

@Controller
public class EinkaufslisteController {

    @Autowired
    private VerlagService verlagService;

    @Autowired
    private TypService typService;

    @Autowired
    private FormatService formatService;

    @Autowired
    private SammelbandService sammelbaendeService;

    @Autowired
    private EinkaufslisteService einkaufslisteService;

    @Autowired
    private MangaReiheService mangaReiheService;

    @Autowired
    private StatusService statusService;

    @GetMapping("/einkaufsliste")
    public String showEinkaufsliste(Model model) {
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();
        List<Sammelband> alleSammelbaende = sammelbaendeService.findAll();
        List<Status> alleStatus = statusService.findAllSortById();

        System.out.println("Der Controller wird aufgerufen");
        // Erstellen einer Map, um die Items nach Monaten zu gruppieren
        Map<String, List<EinkaufslisteItem>> itemsByMonth = new LinkedHashMap<>();
        Map<String, Integer> totalVolumesByMonth = new HashMap<>();
        Map<String, String> totalPriceByMonth = new HashMap<>();
        DecimalFormat df = new DecimalFormat("#,##0.00 €", new DecimalFormatSymbols(Locale.GERMANY));

        // Beispiel: Daten für die nächsten 6 Monate abrufen
        LocalDate currentDate = LocalDate.now().minusMonths(1);
        for (int i = 0; i < 7; i++) {
            LocalDate month = currentDate.plusMonths(i);
            List<EinkaufslisteItem> items = einkaufslisteService.getItemsForMonth(month);

            if (items != null && !items.isEmpty()) {
                // Prüfe, ob alle Items in diesem Monat gekauft wurden
                boolean allItemsBought = items.stream().allMatch(EinkaufslisteItem::getGekauft);

                if (!allItemsBought) {
                    String monthName = getGermanMonthName(month.getMonthValue());
                    itemsByMonth.put(monthName, items);

                    for (EinkaufslisteItem e : items) {
                        System.out.println("Monat: " + month.getMonthValue());
                        System.out.println("ID: " + e.getId());
                        System.out.println("Verlag: " + e.getVerlagId().getName());
                        System.out.println("Typ: " + e.getTypId().getBezeichnung());
                        System.out.println("Format: " + e.getFormatId().getBezeichnung());
                        System.out.println("Titel: " + e.getTitel());
                        System.out.println("---------");
                    }

                    int totalVolumes = items.stream().mapToInt(EinkaufslisteItem::getAnzahlBaende).sum();
                    BigDecimal totalPrice = items.stream()
                            .map(EinkaufslisteItem::getGesamtpreis)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    totalVolumesByMonth.put(monthName, totalVolumes);
                    totalPriceByMonth.put(monthName, df.format(totalPrice));
                }
            }
        }

        model.addAttribute("itemsByMonth", itemsByMonth);
        model.addAttribute("totalVolumesByMonth", totalVolumesByMonth);
        model.addAttribute("totalPriceByMonth", totalPriceByMonth);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleSammelbaende", alleSammelbaende);
        model.addAttribute("alleStatus", alleStatus);
        return "einkaufsliste"; // Name Ihrer Thymeleaf-Vorlage
    }

    public String getGermanMonthName(int monthNumber) {
        String[] germanMonths = {
                "Januar", "Februar", "März", "April", "Mai", "Juni",
                "Juli", "August", "September", "Oktober", "November", "Dezember"
        };

        if (monthNumber < 1 || monthNumber > 12) {
            throw new IllegalArgumentException("Monatsnummer muss zwischen 1 und 12 liegen");
        }

        return germanMonths[monthNumber - 1];
    }

    // Methode zum Hinzufügen einer MangaReihe
    @PostMapping(value = "/addToEinkaufsliste", consumes = "application/json")
    @ResponseBody // Damit der Rückgabewert als Response Body gesendet wird
    public ResponseEntity<?> addToEinkaufsliste(@RequestBody Map<String, Object> requestData) {
        try {
            System.out.println("Start des Extrahierens der übermittelten Werte");
            // Extrahieren der Daten aus dem requestData-Map
            Long mangaReiheId = requestData.get("mangaReiheId") != null
                    ? Long.valueOf(((String) requestData.get("mangaReiheId")))
                    : null;
            System.out.println("Die Id der Reihe ist: " + mangaReiheId);
            Integer mangaIndex = (Integer) requestData.get("mangaIndex");
            System.out.println("Der Index der Manga Reihe ist: " + mangaIndex);
            Long verlagId = Long.valueOf((String) requestData.get("verlagId"));
            Long typId = Long.valueOf((String) requestData.get("typId"));
            Long formatId = Long.valueOf((String) requestData.get("formatId"));
            String titel = (String) requestData.get("titel");
            Integer anzahlBaende = (Integer) requestData.get("anzahlBaende");
            Double preisProBand = Double.valueOf((String) requestData.get("preisProBand"));
            Boolean istVergriffen = (Boolean) requestData.get("istVergriffen");
            Boolean istEbayPreis = (Boolean) requestData.get("istEbayPreis");
            String anilistUrl = (String) requestData.get("anilistUrl");
            Long sammelbandTypId = requestData.get("sammelbandTypId") != null
                    ? Long.valueOf((String) requestData.get("sammelbandTypId"))
                    : null;
            Double gesamtpreisAenderung = Double.valueOf((String) requestData.get("gesamtpreisAenderung"));

            @SuppressWarnings("unchecked")
            Map<String, String> scrapedData = (Map<String, String>) requestData.get("scrapedData");

            System.out.println("Extrahieren der übermittelten Werte abgeschlossen");

            // Extrahieren und Umwandeln des erscheinungsdatums
            String erscheinungsdatumString = (String) requestData.get("erscheinungsdatum");
            LocalDate erscheinungsdatum = erscheinungsdatumString != null ? LocalDate.parse(erscheinungsdatumString)
                    : null;

            // Verwendung der saveEinkaufslisteItem-Methode aus dem Service
            EinkaufslisteItem savedEinkaufslisteItem = einkaufslisteService.saveEinkaufslisteItem(
                    mangaIndex,
                    verlagId,
                    typId,
                    formatId,
                    titel,
                    anzahlBaende,
                    preisProBand,
                    istVergriffen,
                    istEbayPreis,
                    anilistUrl,
                    sammelbandTypId,
                    gesamtpreisAenderung,
                    erscheinungsdatum,
                    scrapedData);
            return ResponseEntity.ok(savedEinkaufslisteItem);
        } catch (Exception e) {
            System.out.println("Fehler");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler bei der Verarbeitung der Anfrage: " + e.getMessage());
        }
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItemById(@PathVariable Long id) {
        try {
            einkaufslisteService.deleteItemById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Controller-Methode zum finden und updaten von MangaReihen
    @PostMapping("/buyItemUpdate")
    public ResponseEntity<?> buyItemUpdate(@RequestBody Map<String, String> itemId) {
        System.out.println(
                "------------------------------ Methode buyItemUpdate gestartet ----------------------------------");
        // Item aus der Einkaufsliste holen
        Optional<EinkaufslisteItem> itemOptional = einkaufslisteService
                .findItemById(Long.valueOf(itemId.get("itemId")));
        if (!itemOptional.isPresent()) {
            System.out.println("Keine Reihe mit der ID gefunden");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Keinen Item mit dieser ID gefunden");
        }
        EinkaufslisteItem item = itemOptional.get();

        // Versuchen, eine MangaReihe anhand des MangaIndex zu finden
        List<MangaReihe> reihen = mangaReiheService.findByMangaIndex(item.getMangaIndex());
        System.out.println(reihen.isEmpty());
        if (!reihen.isEmpty()) {
            // Update der vorhandenen Reihe
            MangaReihe reihe = reihen.get(0);
            mangaReiheService.updateMangaReiheAnzahlBaende(reihe.getId(),
                    (reihe.getAnzahlBaende() + item.getAnzahlBaende()));
            BigDecimal anzahlBaendeBigDecimal = BigDecimal.valueOf(item.getAnzahlBaende());
            mangaReiheService.updateMangaReiheGesamtpreis(reihe.getId(), (reihe.getGesamtpreis().add(item.getPreis()
                    .multiply(anzahlBaendeBigDecimal)
                    .add(item.getAenderungGesamtpreis() != null ? item.getAenderungGesamtpreis() : BigDecimal.ZERO))));
            einkaufslisteService.updateGekauft(item.getId(), true);
            return ResponseEntity.ok(Collections.singletonMap("message", "Reihe aktualisiert"));

        }

        // Suche anhand des vollen Titels
        List<MangaReihe> reihenMitGleichemTitel = mangaReiheService.findByFullTitel(item.getTitel());
        if (!reihenMitGleichemTitel.isEmpty()) {
            // Update der gefundenen Reihe
            MangaReihe reihe = reihenMitGleichemTitel.get(0);
            mangaReiheService.updateMangaReiheAnzahlBaende(reihe.getId(),
                    (reihe.getAnzahlBaende() + item.getAnzahlBaende()));
            BigDecimal anzahlBaendeBigDecimal = BigDecimal.valueOf(item.getAnzahlBaende());
            mangaReiheService.updateMangaReiheGesamtpreis(reihe.getId(), (reihe.getGesamtpreis().add(item.getPreis()
                    .multiply(anzahlBaendeBigDecimal)
                    .add(item.getAenderungGesamtpreis() != null ? item.getAenderungGesamtpreis() : BigDecimal.ZERO))));
            einkaufslisteService.updateGekauft(item.getId(), true);
            einkaufslisteService.updateGekauft(item.getId(), true);
            return ResponseEntity.ok(Collections.singletonMap("message", "Reihe aktualisiert"));

        }

        System.out.println("Die Reihe existiert noch nicht");
        // Ansonsten Aufforderung zur Auswahl des Status für eine neue Reihe
        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                .body(Collections.singletonMap("message", "Bitte Status für neue Reihe auswählen"));

    }

    // Controller-Methode zum erzeugen von MangaReihen
    @PostMapping("/buyItemCreate")
    public ResponseEntity<?> buyItemCreate(@RequestBody Map<String, Long> requestBody) {
        System.out.println(
                "------------------------------ Methode buyItemCreate gestartet ----------------------------------");
        try {
            Long itemId = requestBody.get("itemId");
            Long statusId = requestBody.get("statusId");

            // Item aus der Einkaufsliste holen
            Optional<EinkaufslisteItem> itemOptional = einkaufslisteService.findItemById(itemId);
            if (!itemOptional.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Keinen Item mit dieser ID gefunden");
            }
            EinkaufslisteItem item = itemOptional.get();

            // Daten aus dem Einkaufslisten-Item extrahieren
            Integer mangaIndex = item.getMangaIndex(); // Annahme: mangaIndex ist Integer
            Long verlagId = item.getVerlagId().getVerlagId(); // Annahme: verlagId ist verfügbar
            Long typId = item.getTypId().getTypId(); // Annahme: typId ist verfügbar
            Long formatId = item.getFormatId().getFormatId(); // Annahme: formatId ist verfügbar
            String titel = item.getTitel();
            Integer anzahlBaende = item.getAnzahlBaende();
            Double preisProBand = item.getPreis().doubleValue(); // Annahme: preisProBand ist BigDecimal
            Boolean istVergriffen = item.getIstVergriffen();
            Boolean istEbayPreis = item.getIstEbayPreis();
            String anilistUrl = item.getAnilistUrl() != null ? item.getAnilistUrl() : null;
            Long sammelbandTypId = (item.getSammelbaendeId() != null) ? item.getSammelbaendeId().getId() : null;
            Double gesamtpreisAenderung = item.getAenderungGesamtpreis().doubleValue(); // Annahme: gesamtpreisAenderung
                                                                                        // ist BigDecimal

            System.out.println("Test 1");

            // ScrapedData von item verwenden, falls erforderlich
            Map<String, String> scrapedData = new HashMap<>();
            scrapedData.put("Deutsche Ausgabe Status", item.getStatusDe() != null ? item.getStatusDe() : null);
            scrapedData.put("Band 1 Bild Url", item.getCoverUrl() != null ? item.getCoverUrl() : null);
            scrapedData.put("Deutsche Ausgabe Bände",
                    item.getAnzahlBaendeDe() != null ? String.valueOf(item.getAnzahlBaendeDe()) : null);
            scrapedData.put("Deutsche Ausgabe Status", item.getStatusDe() != null ? item.getStatusDe() : null);
            scrapedData.put("Erstveröffentlichung Status",
                    item.getStatusErstv() != null ? item.getStatusErstv() : null);
            scrapedData.put("Erstveröffentlichung Herkunft", item.getHerkunft() != null ? item.getHerkunft() : null);
            scrapedData.put("Erstveröffentlichung Startjahr",
                    item.getStartJahr() != null ? String.valueOf(item.getStartJahr()) : null);
            scrapedData.put("Erstveröffentlichung Bände",
                    item.getAnzahlBaendeErstv() != null ? String.valueOf(item.getAnzahlBaendeErstv()) : null);

            System.out.println("Test 2");

            // Neue MangaReihe erstellen und speichern
            MangaReihe neueReihe = mangaReiheService.saveMangaReihe(
                    mangaIndex, statusId, verlagId, typId, formatId, titel, anzahlBaende, preisProBand,
                    istVergriffen, istEbayPreis, anilistUrl, sammelbandTypId, gesamtpreisAenderung, scrapedData);

            System.out.println("Test 3 - Nach der saveMethode");

            if (neueReihe != null) {
                einkaufslisteService.updateGekauft(item.getId(), true);
                Map<String, String> response = new HashMap<>();
                response.put("message", "Neue MangaReihe erfolgreich gespeichert");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Fehler beim Speichern der neuen MangaReihe");
            }
        } catch (Exception e) {
            System.out.println("Fehler");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler bei der Verarbeitung der Anfrage: " + e.getMessage());
        }
    }
}
