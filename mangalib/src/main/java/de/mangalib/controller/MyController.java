package de.mangalib.controller;

import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.service.StatusService;
import de.mangalib.service.VerlagService;
import de.mangalib.service.TypService;
import de.mangalib.service.FormatService;
import de.mangalib.service.MangaReiheService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.DecimalFormat;
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

    @GetMapping("/home")
    public String meineSeite(Model model,
            @RequestParam(name = "sortierung", required = false) String sortierung,
            @RequestParam(name = "statusFilter", required = false) Long statusId,
            @RequestParam(name = "verlagFilter", required = false) Long verlagId,
            @RequestParam(name = "typFilter", required = false) Long typId,
            @RequestParam(name = "formatFilter", required = false) Long formatId) {

        List<Status> alleStatus = statusService.findAllSortById();
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();

        List<MangaReihe> alleMangaReihen = mangaReiheService.findAllSortById(); // Standard: Sortiert nach ID

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

        model.addAttribute("alleStatus", alleStatus);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);
        model.addAttribute("alleMangaReihen", alleMangaReihen);

        return "home"; // Name der HTML-Datei ohne .html
    }
}