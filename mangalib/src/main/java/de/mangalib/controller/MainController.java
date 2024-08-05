package de.mangalib.controller;

import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Band;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaDetails;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class MainController {

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
    public String home(Model model,
            @RequestParam(name = "sortierung", required = false) String sortierung,
            @RequestParam(name = "suche", required = false) String suche,
            @RequestParam(name = "statusFilter", required = false) Long statusId,
            @RequestParam(name = "verlagFilter", required = false) Long verlagId,
            @RequestParam(name = "typFilter", required = false) Long typId,
            @RequestParam(name = "formatFilter", required = false) Long formatId,
            @RequestParam(name = "jahrFilter", required = false) Integer jahrFilter,
            @RequestParam(name = "monatFilter", required = false) Integer monatFilter,
            @RequestParam(name = "gelesenFilter", required = false) Boolean gelesenFilter,
            @RequestParam(name = "vergriffenFilter", required = false) String vergriffenFilter) {

        List<Status> alleStatus = statusService.findAllSortById();
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();
        List<Sammelband> alleSammelbaende = sammelbaendeService.findAll();
        List<MangaReihe> alleMangaReihen = mangaReiheService.findAllSortById(); // Standard: Sortiert nach ID
        Map<Long, String> coverUrlsMap = new HashMap<>();

        for (MangaReihe mangaReihe : alleMangaReihen) {
            MangaDetails details = mangaReihe.getMangaDetails();
            String coverUrl = (details != null) ? details.getCoverUrl() : null;
            coverUrlsMap.put(mangaReihe.getId(), coverUrl);
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
                    .filter(mangaReihe -> mangaReihe.getErstelltAm().getYear() == jahr &&
                            mangaReihe.getErstelltAm().getMonthValue() == monatFilter)
                    .collect(Collectors.toList());
        } else if (jahrFilter != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getErstelltAm().getYear() == jahrFilter)
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

        if (gelesenFilter != null) {
            alleMangaReihen = alleMangaReihen.stream()
                    .filter(mangaReihe -> mangaReihe.getMangaDetails().isIstGelesen() == gelesenFilter)
                    .collect(Collectors.toList());
        }

        // Sortierung anwenden
        if (sortierung != null) {
            switch (sortierung) {
                case "titel":
                    alleMangaReihen.sort(Comparator.comparing(MangaReihe::getTitel));
                    break;
                case "aktualisiertAm":
                    alleMangaReihen.sort(Comparator.comparing(MangaReihe::getAktualisiertAm).reversed());
                    break;
                case "preisProBand":
                    alleMangaReihen.sort(Comparator.comparing(MangaReihe::getPreisProBand).reversed());
                    break;
                case "gesamtpreis":
                    alleMangaReihen.sort(Comparator.comparing(MangaReihe::getGesamtpreis).reversed());
                    break;
                default:
                    alleMangaReihen.sort(Comparator.comparing(MangaReihe::getId));
                    break;
            }
        } else {
            alleMangaReihen.sort(Comparator.comparing(MangaReihe::getId));
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
        model.addAttribute("gelesenFilter", gelesenFilter);

        model.addAttribute("alleStatus", alleStatus);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleMangaReihen", alleMangaReihen);
        model.addAttribute("alleSammelbaende", alleSammelbaende);
        model.addAttribute("coverUrlsMap", coverUrlsMap);

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
            System.out.println("Start des Extrahierens der übermittelten Werte");
            // Extrahieren der Daten aus dem requestData-Map
            Long mangaReiheId = requestData.get("mangaReiheId") != null
                    ? Long.valueOf(((String) requestData.get("mangaReiheId")))
                    : null;
            System.out.println("Die Id der Reihe ist: " + mangaReiheId);
            Integer mangaIndex = requestData.get("mangaIndex") != null ? (Integer) requestData.get("mangaIndex") : null;
            System.out.println("Der Index der Manga Reihe ist: " + mangaIndex);
            Long statusId = Long.valueOf((String) requestData.get("statusId"));
            Long verlagId = Long.valueOf((String) requestData.get("verlagId"));
            Long typId = Long.valueOf((String) requestData.get("typId"));
            Long formatId = Long.valueOf((String) requestData.get("formatId"));
            String titel = (String) requestData.get("titel");
            Integer anzahlBaende = (Integer) requestData.get("anzahlBaende");
            BigDecimal preisProBand = requestData.containsKey("preisProBand") ? new BigDecimal((String) requestData.get("preisProBand")) : BigDecimal.ZERO;
            Boolean istGelesen = (Boolean) requestData.get("istGelesen");
            Integer reread = (Integer) requestData.get("reread") != null ? (Integer) requestData.get("reread") : 0;
            Boolean istVergriffen = (Boolean) requestData.get("istVergriffen");
            Boolean istEbayPreis = (Boolean) requestData.get("istEbayPreis");
            String anilistUrl = (String) requestData.get("anilistUrl");
            String coverUrl = (String) requestData.get("coverUrl");
            Long sammelbandTypId = requestData.get("sammelbandTypId") != null
                    ? Long.valueOf((String) requestData.get("sammelbandTypId"))
                    : null;
            BigDecimal gesamtpreisAenderung = requestData.containsKey("gesamtpreisAenderung") ? new BigDecimal((String) requestData.get("gesamtpreisAenderung")) : BigDecimal.ZERO;
            @SuppressWarnings("unchecked")
            Map<String, String> scrapedData = (Map<String, String>) requestData.get("scrapedData");
            Boolean istEdit = Boolean.valueOf(scrapedData.get("istEdit"));
            System.out.println("Ist Edit? " + istEdit);
            System.out.println("Extrahieren der übermittelten Werte abgeschlossen");

            // Verwendung der saveMangaReihe-Methode aus dem Service
            MangaReihe savedMangaReihe;
            if (!istEdit) {
                System.out.println("Eine neue Reihe wird hinzugefügt");
                savedMangaReihe = mangaReiheService.saveMangaReihe(mangaIndex, statusId, verlagId, typId,
                        formatId, titel, anzahlBaende, preisProBand, istVergriffen, istEbayPreis, anilistUrl,
                        sammelbandTypId, gesamtpreisAenderung, istGelesen, scrapedData);
            } else {
                System.out.println("Die Reihe wird aktualisiert");
                savedMangaReihe = mangaReiheService
                        .updateMangaReihe(mangaReiheId, mangaIndex, statusId, verlagId, typId,
                                formatId, titel, anzahlBaende, preisProBand, istVergriffen, istEbayPreis, anilistUrl,
                                coverUrl,
                                sammelbandTypId, gesamtpreisAenderung, istGelesen, reread, scrapedData)
                        .orElse(null);
            }
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

    @GetMapping("/getMangaReiheData/{id}")
    public ResponseEntity<Map<String, Object>> getMangaReiheData(@PathVariable Long id) {
        System.out.println("------------------------------Edit ID: " + id);
        Optional<MangaReihe> mangaReiheOpt = mangaReiheService.findById(id);

        if (!mangaReiheOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        MangaReihe mangaReihe = mangaReiheOpt.get();
        Map<String, Object> response = new HashMap<>();

        response.put("id", mangaReihe.getId());
        response.put("mangaIndex", mangaReihe.getMangaIndex() != null ? mangaReihe.getMangaIndex() : null);
        response.put("statusId", mangaReihe.getStatus() != null ? mangaReihe.getStatus().getStatusId() : null);
        response.put("verlagId", mangaReihe.getVerlag() != null ? mangaReihe.getVerlag().getVerlagId() : null);
        response.put("typId", mangaReihe.getTyp() != null ? mangaReihe.getTyp().getTypId() : null);
        response.put("formatId", mangaReihe.getFormat() != null ? mangaReihe.getFormat().getFormatId() : null);
        response.put("titel", mangaReihe.getTitel());
        response.put("anzahlBaende", mangaReihe.getAnzahlBaende());
        BigDecimal preisProBand = mangaReihe.getPreisProBand();
        response.put("preisProBand", String.valueOf(preisProBand).replace(".", ","));
        response.put("istGelesen", mangaReihe.getMangaDetails().isIstGelesen());
        response.put("reread", mangaReihe.getMangaDetails().getReread() != null ? mangaReihe.getMangaDetails().getReread() : 0);
        response.put("istEbayPreis", mangaReihe.getIstEbayPreis());
        response.put("istVergriffen", mangaReihe.getIstVergriffen());
        BigDecimal gesamtpreisAenderung = mangaReihe.getAenderungGesamtpreis() != null
                ? mangaReihe.getAenderungGesamtpreis()
                : null;
        response.put("gesamtpreisAenderung",
                gesamtpreisAenderung != null ? gesamtpreisAenderung.toString().replace(".", ",") : null);

        if (mangaReihe.getMangaDetails() != null) {
            MangaDetails details = mangaReihe.getMangaDetails();
            response.put("anilistUrl", details.getAnilistUrl() != null ? details.getAnilistUrl() : null);
            // Überprüfen Sie zuerst, ob details.getSammelbaende() nicht null ist
            if (details.getSammelbaende().getId() != 1) {
                response.put("istSammelband", true);
                response.put("sammelbandTypId", details.getSammelbaende().getId());
                response.put("sammelbandTyp", details.getSammelbaende().getTyp());
                System.out.println("IstSammelband: " + response.get("istSammelband"));
            } else {
                response.put("istSammelband", false);
                response.put("sammelbandTypId", null);
            }
            response.put("coverUrl", details.getCoverUrl());
        }

        Band ersterBand = bandService.getFirstBandByMangaReiheId(mangaReihe.getId());
        if (ersterBand != null) {
            response.put("bildUrl", ersterBand.getBildUrl() != null ? ersterBand.getBildUrl().toString() : null);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/mangaReihe/{mangaReiheId}/bands")
    @ResponseBody
    public List<Map<String, Object>> getBandsByMangaReiheId(@PathVariable Long mangaReiheId) {
        MangaReihe mangaReihe = mangaReiheService.findById(mangaReiheId)
                .orElseThrow(() -> new RuntimeException("MangaReihe not found"));
        List<Band> bands = bandService.findByMangaReiheId(mangaReiheId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Band band : bands) {
            Map<String, Object> bandData = new HashMap<>();
            bandData.put("id", band.getId());
            bandData.put("bildUrl", band.getBildUrl() != null ? band.getBildUrl().toString() : "");
            bandData.put("bandNr", band.getBandNr());
            bandData.put("preis", band.getPreis());
            bandData.put("aenderungPreis", band.getAenderungPreis());
            bandData.put("istGelesen", band.isIstGelesen());
            bandData.put("istSpecial", band.getIstSpecial() != null ? band.getIstSpecial() : false);
            bandData.put("mpUrl", band.getMpUrl() != null ? band.getMpUrl().toString() : "");
            bandData.put("formattedPreis", String.format("%.2f €", band.getPreis()));
            bandData.put("bandIndex", band.getBandIndex() != null ? band.getBandIndex() : "");
            bandData.put("titel", mangaReihe.getTitel());

            // Calculate and format the total price
        BigDecimal totalPrice = band.getPreis().add(band.getAenderungPreis());
        bandData.put("totalPreis", String.format("%.2f €", totalPrice));
            result.add(bandData);
        }
        return result;
    }

    @GetMapping("/getBand/{bandId}")
    public ResponseEntity<Map<String, Object>> getBand(@PathVariable Long bandId) {
        Optional<Band> bandOptional = bandService.findById(bandId);
        if (bandOptional.isPresent()) {
            Band band = bandOptional.get();
            Map<String, Object> bandData = new HashMap<>();
            bandData.put("id", band.getId());
            bandData.put("bildUrl", band.getBildUrl() != null ? band.getBildUrl().toString() : "");
            bandData.put("bandNr", band.getBandNr());
            bandData.put("preis", band.getPreis());
            bandData.put("aenderungPreis", band.getAenderungPreis());
            bandData.put("istGelesen", band.isIstGelesen());
            bandData.put("istSpecial", band.getIstSpecial() != null ? band.getIstSpecial() : false);
            bandData.put("mpUrl", band.getMpUrl() != null ? band.getMpUrl().toString() : "");
            bandData.put("bandIndex", band.getBandIndex() != null ? band.getBandIndex() : "");
            bandData.put("reread", band.getReread() != null ? band.getReread() : 0);

            // Titel der Manga-Reihe abrufen
            MangaReihe mangaReihe = band.getMangaReihe();
            if (mangaReihe != null) {
                bandData.put("titel", mangaReihe.getTitel());
            } else {
                bandData.put("titel", "Unbekannt");
            }

            return ResponseEntity.ok(bandData);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/editBand", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Void> updateBand(@RequestBody Map<String, Object> bandData) {
        try {
            // Wandeln der Map in ein Band Objekt um
            Band band = new Band();
            band.setId(Long.parseLong((String) bandData.get("id")));
            band.setBandNr(Integer.parseInt((String) bandData.get("bandNr")));

            // Überprüfen und setzen von bandIndex, wenn vorhanden
            String bandIndexString = (String) bandData.get("bandIndex");
            if (bandIndexString != null && !bandIndexString.isEmpty()) {
                band.setBandIndex(Integer.parseInt(bandIndexString));
            } else {
                band.setBandIndex(null);
            }

            // Überprüfen und setzen von preis, wenn vorhanden
            String preisString = (String) bandData.get("preis");
            if (preisString != null && !preisString.isEmpty()) {
                band.setPreis(new BigDecimal(preisString));
            } else {
                band.setPreis(null);
            }

            // Überprüfen und setzen von aenderungPreis, wenn vorhanden
            String aenderungPreisString = (String) bandData.get("aenderungPreis");
            if (aenderungPreisString != null && !aenderungPreisString.isEmpty()) {
                band.setAenderungPreis(new BigDecimal(aenderungPreisString));
            } else {
                band.setAenderungPreis(null);
            }

            // Überprüfen und setzen von bildUrl und mpUrl, wenn vorhanden
            try {
                String bildUrlString = (String) bandData.get("bildUrl");
                if (bildUrlString != null && !bildUrlString.isEmpty()) {
                    URI bildUri = new URI(bildUrlString);
                    URL bildUrl = bildUri.toURL();
                    band.setBildUrl(bildUrl);
                }

                String mpUrlString = (String) bandData.get("mpUrl");
                if (mpUrlString != null && !mpUrlString.isEmpty()) {
                    URI mpUri = new URI(mpUrlString);
                    URL mpUrl = mpUri.toURL();
                    band.setMpUrl(mpUrl);
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Überprüfen und setzen von bandIndex, wenn vorhanden
            String bandReread = (String) bandData.get("reread");
            if (bandReread != null && !bandReread.isEmpty()) {
                band.setReread(Integer.parseInt(bandReread));
            } else {
                band.setReread(0);
            }

            // Überprüfen und setzen von bandIndex, wenn vorhanden
            String bandRereadString = (String) bandData.get("reread");
            if (bandRereadString != null && !bandRereadString.isEmpty()) {
                band.setReread(Integer.parseInt(bandRereadString));
            } else {
                band.setReread(0);
            }

            band.setIstGelesen((Boolean) bandData.get("istGelesen"));
            band.setIstSpecial((Boolean) bandData.get("istSpecial"));

            // Loggen der umgewandelten Band-Daten
            System.out.println("Umgewandelte Band-Daten: " + band);

            // Update des Bands
            bandService.updateBand(band);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/autofillBandData/{bandIndex}")
    public ResponseEntity<Map<String, String>> autofillBandData(@PathVariable String bandIndex) {
        Map<String, String> autofillData = bandService.autofillBandData(bandIndex);
        return ResponseEntity.ok(autofillData);
    }

}