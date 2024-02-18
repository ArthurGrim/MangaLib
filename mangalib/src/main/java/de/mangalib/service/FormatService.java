package de.mangalib.service;

import de.mangalib.Format;
import de.mangalib.repository.FormatRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class FormatService {

    // Das Repository für die Format-Entität
    private final FormatRepository formatRepository;

    // Konstruktorbasierte Dependency Injection des FormatRepository
    public FormatService(FormatRepository formatRepository) {
        this.formatRepository = formatRepository;
    }

    // Eine Methode, um alle Format-Objekte aus der Datenbank abzurufen
    public List<Format> findAll() {
        return formatRepository.findAll();
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von Format-Objekten
}
