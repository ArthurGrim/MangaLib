package de.mangalib.controller;

import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Band;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Sammelband;
import de.mangalib.service.StatusService;
import de.mangalib.service.VerlagService;
import de.mangalib.service.TypService;
import de.mangalib.service.BandService;
import de.mangalib.service.FormatService;
import de.mangalib.service.MangaReiheService;
import de.mangalib.service.MangaScraper;
import de.mangalib.service.SammelbandService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.DecimalFormat;
import java.time.Year;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class MyController {

    @Autowired
    private StatusService statusService;

    @Autowired
    private VerlagService verlagService;

    @Autowired
    private TypService typService;

    @Autowired
    private FormatService formatService;

    @Autowired
    private MangaReiheService mangaReiheService;

    @Autowired
    private SammelbandService sammelbaendeService;

    @Autowired
    private BandService bandService;

    @GetMapping("/home")
    public String meineSeite(Model model,
            @RequestParam(name = "sortierung", required = false) String sortierung,
            @RequestParam(name = "suche", required = false) String suche,
            @RequestParam(name = "statusFilter", required = false) Long statusId,
            @RequestParam(name = "verlagFilter", required = false) Long verlagId,
            @RequestParam(name = "typFilter", required = false) Long typId,
            @RequestParam(name = "formatFilter", required = false) Long formatId,
            @RequestParam(name = "jahrFilter", required = false) Integer jahrFilter,
            @RequestParam(name = "monatFilter", required = false) Integer monatFilter,
            @RequestParam(name = "vergriffenFilter", required = false) String vergriffenFilter) {

        List<Status> alleStatus = statusService.findAllSortById();
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();
        List<Sammelband> alleSammelbaende = sammelbaendeService.findAll();
        List<MangaReihe> alleMangaReihen = mangaReiheService.findAllSortById(); // Standard: Sortiert nach ID
        Map<Long, Band> ersteBaendeMap = new HashMap<>();

        for (MangaReihe mangaReihe : alleMangaReihen) {
            Band ersterBand = bandService.getFirstBandByMangaReiheId(mangaReihe.getId());
            ersteBaendeMap.put(mangaReihe.getId(), ersterBand);
        }

        if (suche != null && !suche.trim().isEmpty()) {
            alleMangaReihen = mangaReiheService.searchByTitelOrIndex(suche);
        } else {
            alleMangaReihen = mangaReiheService.findAllSortById(); // Standard: Sortiert nach ID
        }

        if (statusId != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getStatus().getStatusId().equals(statusId))
                    .collect(Collectors.toList());
        }
        if (verlagId != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getVerlag().getVerlagId().equals(verlagId))
                    .collect(Collectors.toList());
        }
        if (typId != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getTyp().getTypId().equals(typId))
                    .collect(Collectors.toList());
        }
        if (formatId != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getFormat().getFormatId().equals(formatId))
                    .collect(Collectors.toList());
        }

        // Filtern nach Jahr und Monat
        if (monatFilter != null && monatFilter >= 1 && monatFilter <= 12) {
            int jahr = (jahrFilter != null) ? jahrFilter : Year.now().getValue(); // Aktuelles Jahr, wenn kein Jahr
                                                                                  // angegeben
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getErstelltAm().toLocalDateTime().getYear() == jahr &&
                            mangaReihe.getErstelltAm().toLocalDateTime().getMonthValue() == monatFilter)
                    .collect(Collectors.toList());
        } else if (jahrFilter != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getErstelltAm().toLocalDateTime().getYear() == jahrFilter)
                    .collect(Collectors.toList());
        }

        if ("true".equals(vergriffenFilter)) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(MangaReihe::getIstVergriffen)
                    .collect(Collectors.toList());
        } else if ("false".equals(vergriffenFilter)) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> !mangaReihe.getIstVergriffen())
                    .collect(Collectors.toList());
        }

        if ("titel".equals(sortierung)) {
            alleMangaReihen.sort(Comparator.comparing(MangaReihe::getTitel));
        }

        DecimalFormat df = new DecimalFormat("0.00 €");

        alleMangaReihen.forEach(mangaReihe -> {
            if (mangaReihe.getPreisProBand() != null) {
                mangaReihe.setPreisProBandString(df.format(mangaReihe.getPreisProBand()));
            }
            if (mangaReihe.getGesamtpreis() != null) {
                mangaReihe.setGesamtpreisString(df.format(mangaReihe.getGesamtpreis()));
            }
        });

        model.addAttribute("sortierung", sortierung);
        model.addAttribute("statusFilter", statusId);
        model.addAttribute("verlagFilter", verlagId);
        model.addAttribute("typFilter", typId);
        model.addAttribute("formatFilter", formatId);
        model.addAttribute("jahrFilter", jahrFilter);
        model.addAttribute("monatFilter", monatFilter);
        model.addAttribute("vergriffenFilter", vergriffenFilter);

        model.addAttribute("alleStatus", alleStatus);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleMangaReihen", alleMangaReihen);
        model.addAttribute("alleSammelbaende", alleSammelbaende);
        model.addAttribute("ersteBaendeMap", ersteBaendeMap);

        // Gibt die ID des nächsten Datensatzes zurück
        Long nextId = mangaReiheService.getNextId();
        model.addAttribute("nextId", nextId);

        return "home"; // Name der HTML-Datei ohne .html
    }

    @GetMapping("/resetFilters")
    public String resetFilters(RedirectAttributes redirectAttributes) {
        // Setzen Sie hier die Filter und Sortierparameter auf ihre Standardwerte
        redirectAttributes.addAttribute("sortierung", "id"); // Standardwert für Sortierung
        redirectAttributes.addAttribute("statusFilter", ""); // Standardwert für Status-Filter
        redirectAttributes.addAttribute("verlagFilter", ""); // Standardwert für Verlag-Filter
        redirectAttributes.addAttribute("typFilter", ""); // Standardwert für Typ-Filter
        redirectAttributes.addAttribute("formatFilter", ""); // Standardwert für Format-Filter
        redirectAttributes.addAttribute("monatFilter", ""); // Standardwert für Monats-Filter
        redirectAttributes.addAttribute("jahrFilter", ""); // Standardwert für Jahres-Filter

        // Leiten Sie zur Hauptseite um
        return "redirect:/home";
    }

    // Methode zum Hinzufügen einer MangaReihe
    @PostMapping(value = "/addMangaReihe", consumes = "application/json")
    @ResponseBody // Damit der Rückgabewert als Response Body gesendet wird
    public ResponseEntity<?> addMangaReihe(@RequestBody Map<String, Object> requestData) {
        try {
            // Extrahieren der Daten aus dem requestData-Map
            Integer mangaIndex = (Integer) requestData.get("mangaIndex");
            Long statusId = Long.valueOf((String) requestData.get("statusId"));
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
            // Verwendung der saveMangaReihe-Methode aus dem Service
            MangaReihe savedMangaReihe = mangaReiheService.saveMangaReihe(mangaIndex, statusId, verlagId, typId,
                    formatId, titel, anzahlBaende, preisProBand, istVergriffen, istEbayPreis, anilistUrl,
                    sammelbandTypId, gesamtpreisAenderung, scrapedData);
            return ResponseEntity.ok(savedMangaReihe);
        } catch (Exception e) {
            System.out.println("Fehler");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler bei der Verarbeitung der Anfrage: " + e.getMessage());
        }
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> scrapeManga(@RequestBody Map<String, String> request) {
        try {
            String mangaIndex = request.get("mangaIndex");
            Map<String, String> mangaData = MangaScraper.scrapeMangaData(mangaIndex);
            // Umwandlung der Namen in IDs
            Long verlagId = verlagService.findVerlagIdByName(mangaData.get("Deutsche Ausgabe Verlag"));
            Long typId;
            if (((mangaData.get("Erstveröffentlichung Herkunft").equals("China")
                    || mangaData.get("Erstveröffentlichung Herkunft").equals("Südkorea"))
                    && !mangaData.get("Erstveröffentlichung Typ").equals("Light Novel"))
                    && !mangaData.get("Erstveröffentlichung Typ").equals("Artbook & Sonstiges")) {
                typId = typService.findTypIdByBezeichnung("Webtoon");
            } else {
                typId = typService.findTypIdByBezeichnung(mangaData.get("Erstveröffentlichung Typ"));
            }
            Long formatId;
            if (mangaData.get("Deutsche Ausgabe Format").equals("Print")) {
                formatId = formatService.findFormatIdByBezeichnung("Softcover");
            } else {
                formatId = formatService.findFormatIdByBezeichnung(mangaData.get("Deutsche Ausgabe Format"));
            }

            // Erstellen einer neuen Map, die sowohl Strings als auch Longs enthält
            Map<String, Object> response = new HashMap<>(mangaData);
            response.put("verlagId", verlagId);
            response.put("typId", typId);
            response.put("formatId", formatId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}