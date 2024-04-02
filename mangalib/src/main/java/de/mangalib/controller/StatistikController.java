package de.mangalib.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatistikController {

    @GetMapping("/statistik")
    public String statistik(Model model) {
        // Hier können Sie Model-Attribute hinzufügen, die auf der Seite benötigt werden
        // Beispiel: model.addAttribute("attributName", attributWert);

        return "statistik"; // Name der Thymeleaf-Template-Datei ohne die .html-Erweiterung
    }
}
