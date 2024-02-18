package de.mangalib.service;

import de.mangalib.Verlag;
import de.mangalib.repository.VerlagRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class VerlagService {

    // Das Repository für die Verlag-Entität
    private final VerlagRepository verlageRepository;

    // Konstruktorbasierte Dependency Injection des VerlagRepository
    public VerlagService(VerlagRepository verlageRepository) {
        this.verlageRepository = verlageRepository;
    }

    // Eine Methode, um alle Verlag-Objekte aus der Datenbank abzurufen
    public List<Verlag> findAll() {
        return verlageRepository.findAll();
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von Verlag-Objekten
}
