package de.mangalib.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatistikController {

    // Methode um die Statistik Overview Seite aufzurufen
    @GetMapping("/statistik/st_overview")
    public String statistikOverview(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik/st_overview"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }

    // Methode um die Statistik Status Seite aufzurufen
    @GetMapping("/statistik/st_status")
    public String statistikStatus(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik/status"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }

    // Methode um die Statistik Verlag Seite aufzurufen
    @GetMapping("/statistik/st_verlag")
    public String statistikVerlag(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik/st_verlag"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }

    // Methode um die Statistik Typ Seite aufzurufen
    @GetMapping("/statistik/st_typ")
    public String statistikTyp(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik/st_typ"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }

    // Methode um die Statistik Format Seite aufzurufen
    @GetMapping("/statistik/st_format")
    public String statistikFormat(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik/st_format"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }
}
