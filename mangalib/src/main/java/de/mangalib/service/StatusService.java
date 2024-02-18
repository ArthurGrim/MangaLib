package de.mangalib.service;

import de.mangalib.Status;
import de.mangalib.repository.StatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class StatusService {

    // Das Repository für die Status-Entität
    private final StatusRepository statusRepository;

    // Konstruktorbasierte Dependency Injection des StatusRepository
    public StatusService(StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    // Eine Methode, um alle Status-Objekte aus der Datenbank abzurufen
    public List<Status> findAll() {
        return statusRepository.findAll();
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von Status-Objekten
}
