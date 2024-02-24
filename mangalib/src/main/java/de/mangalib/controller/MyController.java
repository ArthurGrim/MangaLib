package de.mangalib.controller;

import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Sammelbaende;
import de.mangalib.service.StatusService;
import de.mangalib.service.VerlagService;
import de.mangalib.service.TypService;
import de.mangalib.service.FormatService;
import de.mangalib.service.MangaReiheService;
import de.mangalib.service.SammelbaendeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.DecimalFormat;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
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
    private SammelbaendeService sammelbaendeService;

    @GetMapping("/home")
    public String meineSeite(Model model,
            @RequestParam(name = "sortierung", required = false) String sortierung,
            @RequestParam(name = "suche", required = false) String suche,
            @RequestParam(name = "statusFilter", required = false) Long statusId,
            @RequestParam(name = "verlagFilter", required = false) Long verlagId,
            @RequestParam(name = "typFilter", required = false) Long typId,
            @RequestParam(name = "formatFilter", required = false) Long formatId,
            @RequestParam(name = "jahrFilter", required = false) Integer jahrFilter,
            @RequestParam(name = "monatFilter", required = false) Integer monatFilter) {

        List<Status> alleStatus = statusService.findAllSortById();
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();
        List<Sammelbaende> alleSammelbaende = sammelbaendeService.findAll();

        List<MangaReihe> alleMangaReihen = mangaReiheService.findAllSortById(); // Standard: Sortiert nach ID

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

        model.addAttribute("alleStatus", alleStatus);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleMangaReihen", alleMangaReihen);
        model.addAttribute("alleSammelbaende", alleSammelbaende);

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

    @PostMapping("/addMangaReihe")
    public String addMangaReihe(@ModelAttribute MangaReihe mangaReihe) {
        // Speichern des neuen MangaReihe-Objekts
        mangaReiheService.saveMangaReihe(mangaReihe);

        // Umleitung zurück zur Hauptseite
        return "redirect:/home";
    }
}