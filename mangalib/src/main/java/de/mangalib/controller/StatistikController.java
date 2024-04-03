package de.mangalib.controller;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import de.mangalib.service.MangaReiheService;

@Controller
public class StatistikController {

    @Autowired
    private MangaReiheService mangaReiheService;

    // Methode um die Statistik Overview Seite aufzurufen
    @GetMapping("/statistik/st_overview")
    public String statistikOverview(Model model) {
        // Die Gesamtanzahl aller Baende aufrufen
        int gesamtAnzahlBaende = mangaReiheService.berechneGesamtAnzahlBaende();
        int gesamtAnzahlMitMultiplikator = mangaReiheService.berechneGesamtAnzahlMitMultiplikator();
        BigDecimal gesamtSummeGesamtpreis = mangaReiheService.getGesamtSummeGesamtpreis();
        BigDecimal durchschnittlicherPreisProBand = mangaReiheService.berechneDurchschnittlichenPreisProBand();


        model.addAttribute("gesamtAnzahlBaende", gesamtAnzahlBaende);
        model.addAttribute("gesamtAnzahlMitMultiplikator", gesamtAnzahlMitMultiplikator);
        model.addAttribute("gesamtSummeGesamtpreis", gesamtSummeGesamtpreis);
        model.addAttribute("durchschnittlicherPreisProBand", durchschnittlicherPreisProBand);

        return "statistik/st_overview";
    }

    // Methode um die Statistik Status Seite aufzurufen
    @GetMapping("/statistik/st_status")
    public String statistikStatus(Model model) {

        return "statistik/st_status";
    }

    // Methode um die Statistik Verlag Seite aufzurufen
    @GetMapping("/statistik/st_verlag")
    public String statistikVerlag(Model model) {

        return "statistik/st_verlag";
    }

    // Methode um die Statistik Typ Seite aufzurufen
    @GetMapping("/statistik/st_typ")
    public String statistikTyp(Model model) {

        return "statistik/st_typ";
    }

    // Methode um die Statistik Format Seite aufzurufen
    @GetMapping("/statistik/st_format")
    public String statistikFormat(Model model) {

        return "statistik/st_format";
    }
}
