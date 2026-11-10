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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import de.mangalib.validation.WriteRequests;
import de.mangalib.validation.WriteValidation;
import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.EinkaufslisteItemDetails;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Sammelband;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.service.EinkaufslisteItemDetailsService;
import de.mangalib.service.EinkaufslisteService;
import de.mangalib.service.PurchaseService;
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

    @Autowired
    private EinkaufslisteItemDetailsService einkaufslisteItemDetailsService;

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

        // Starte die Schleife bei -12, um die letzten 12 Monate rückwirkend zu
        // überprüfen
        LocalDate currentDate = LocalDate.now();
        for (int i = -12; i <= 6; i++) { // -12 für die letzten 12 Monate, 6 für die kommenden Monate
            LocalDate month = currentDate.plusMonths(i);
            List<EinkaufslisteItem> items = einkaufslisteService.getItemsForMonth(month);

            if (items != null && !items.isEmpty()) {
                // Prüfe, ob alle Items in diesem Monat gekauft wurden
                boolean allItemsBought = items.stream().allMatch(item -> Boolean.TRUE.equals(item.getGekauft()));

                if (!allItemsBought) {
                    String monthName = getGermanMonthName(month.getMonthValue());
                    itemsByMonth.put(monthName, items);


                    int totalVolumes = items.stream().map(EinkaufslisteItem::getAnzahlBaende).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
                    BigDecimal totalPrice = items.stream()
                            .map(EinkaufslisteItem::getGesamtpreis).filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    totalVolumesByMonth.put(monthName, totalVolumes);
                    totalPriceByMonth.put(monthName, items.stream().anyMatch(item -> item.getGesamtpreis() == null) ? "Unbekannt" : df.format(totalPrice));
                }
            }
        }

        model.addAttribute("itemsByMonth", itemsByMonth);
        var undated = einkaufslisteService.undatedItems();
        if (!undated.isEmpty()) {
            String group = "Ohne Erscheinungsdatum";
            itemsByMonth.put(group, undated);
            totalVolumesByMonth.put(group, undated.stream().map(EinkaufslisteItem::getAnzahlBaende)
                    .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum());
            totalPriceByMonth.put(group, undated.stream().anyMatch(item -> item.getGesamtpreis() == null)
                    ? "Unbekannt" : df.format(undated.stream().map(EinkaufslisteItem::getGesamtpreis)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        model.addAttribute("totalVolumesByMonth", totalVolumesByMonth);
        model.addAttribute("totalPriceByMonth", totalPriceByMonth);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleSammelbaende", alleSammelbaende);
        model.addAttribute("alleStatus", alleStatus);
        return "einkaufsliste";

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
            var input = WriteRequests.Shopping.from(requestData);
            EinkaufslisteItem saved = einkaufslisteService.saveEinkaufslisteItem(input.index(), input.publisher(),
                    input.type(), input.format(), input.title(), input.volumes(), input.price(),
                    input.outOfPrint(), input.adjustedPrice(), input.anilist(), input.omnibus(),
                    input.adjustment(), input.date(), input.scraped());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return ResponseEntity.badRequest().body("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Anfrage konnte nicht verarbeitet werden.");
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

    @Autowired
    private PurchaseService purchaseService;

    @GetMapping("/items/{id}/edit")
    public ResponseEntity<?> editState(@PathVariable long id) {
        if (id <= 0) return ResponseEntity.badRequest().build();
        var state = einkaufslisteService.editState(id);
        return state == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(state);
    }

    @PatchMapping(value = "/items/{id}", consumes = "application/json")
    public ResponseEntity<?> edit(@PathVariable long id, @RequestBody Map<String, Object> data) {
        try {
            WriteValidation.require(id > 0);
            var state = einkaufslisteService.updateShoppingItem(id, data);
            return state == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(state);
        } catch (EinkaufslisteService.ShoppingConflict conflict) {
            return ResponseEntity.status(409).header("X-MangaLib-Purchase-Outcome", "SHOPPING_EDIT_CONFLICT").build();
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            return ResponseEntity.badRequest().build();
        } catch (Exception failure) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/buyItemUpdate")
    public ResponseEntity<?> buyItemUpdate(@RequestBody Map<String, Object> data) {
        return purchase(data, false);
    }

    @PostMapping("/buyItemCreate")
    public ResponseEntity<?> buyItemCreate(@RequestBody Map<String, Object> data) {
        return purchase(data, true);
    }

    private ResponseEntity<?> purchase(Map<String, Object> data, boolean create) {
        try {
            long id = WriteRequests.purchaseId(data, "itemId");
            Long status = create ? WriteRequests.purchaseId(data, "statusId") : null;
            WriteValidation.require(status == null || status > 0);
            if (id <= 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Keinen Item mit dieser ID gefunden");
            Long confirmed = data.containsKey("confirmedSeriesId") ? WriteRequests.purchaseId(data, "confirmedSeriesId") : null;
            Object token = data.get("purchaseToken");
            WriteValidation.require(!data.containsKey("purchaseToken") || token instanceof String);
            var request = new PurchaseService.Request(id, status, confirmed,
                    rangeNumber(data, "firstBand"), rangeNumber(data, "lastBand"), (String) token);
            return purchaseResponse(purchaseService.purchase(request));
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().body("Fehler bei der Verarbeitung der Anfrage: Ungültige Eingabe.");
        } catch (Exception failure) {
            return ResponseEntity.internalServerError().body("Anfrage konnte nicht verarbeitet werden.");
        }
    }

    private static Integer rangeNumber(Map<String, Object> data, String field) {
        Object value = data.get(field);
        WriteValidation.require(!data.containsKey(field) || value instanceof Integer);
        return (Integer) value;
    }

    private static ResponseEntity<?> purchaseResponse(PurchaseService.Result result) {
        var outcome = result.outcome();
        String message = switch (outcome) {
            case UPDATED -> "Reihe aktualisiert";
            case CREATED -> "Neue MangaReihe erfolgreich gespeichert";
            case ALREADY_PURCHASED -> "Kauf bereits übernommen";
            case STATUS_REQUIRED -> "Bitte Status für neue Reihe auswählen";
            case CONFIRM_VOLUMES -> "Bitte Bände bestätigen";
            case NOT_FOUND -> "Keinen Item mit dieser ID gefunden";
            case DETAILS_NOT_FOUND -> "Keine Details mit dieser ID gefunden";
            default -> "Kaufübernahme erfordert eine Klärung.";
        };
        int status = switch (outcome) {
            case UPDATED, CREATED, ALREADY_PURCHASED, CONFIRM_VOLUMES -> 200;
            case STATUS_REQUIRED -> 417;
            case NOT_FOUND, DETAILS_NOT_FOUND -> 404;
            default -> 409;
        };
        var json = new java.util.LinkedHashMap<String, Object>();
        json.put("message", message);
        if (outcome == PurchaseService.Outcome.CONFIRM_VOLUMES) {
            json.put("seriesId", result.seriesId().toString());
            json.put("firstBand", result.firstBand());
            json.put("lastBand", result.lastBand());
        }
        if (result.purchaseToken() != null) json.put("purchaseToken", result.purchaseToken());
        if (result.purchaseSummary() != null) json.put("purchaseSummary", result.purchaseSummary());
        Object body = status == 404 ? message : json;
        return ResponseEntity.status(status).header("X-MangaLib-Purchase-Outcome", outcome.name()).body(body);
    }
}
