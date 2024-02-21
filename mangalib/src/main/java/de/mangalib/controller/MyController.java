package de.mangalib.controller;

import de.mangalib.entity.Status;
import de.mangalib.entity.Verlag;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Format;
import de.mangalib.service.StatusService;
import de.mangalib.service.VerlagService;
import de.mangalib.service.TypService;
import de.mangalib.service.FormatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

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

    @GetMapping("/home")
    public String meineSeite(Model model) {
        List<Status> alleStatus = statusService.findAllSortById();
        List<Verlag> alleVerlage = verlagService.findAll();
        List<Typ> alleTypen = typService.findAllSortById();
        List<Format> alleFormate = formatService.findAllSortById();

        model.addAttribute("alleStatus", alleStatus);
        model.addAttribute("alleVerlage", alleVerlage);
        model.addAttribute("alleTypen", alleTypen);
        model.addAttribute("alleFormate", alleFormate);

        return "home"; // Name der HTML-Datei ohne .html
    }
}