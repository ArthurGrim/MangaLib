package de.mangalib.service;

import de.mangalib.Typ;
import de.mangalib.repository.TypRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class TypService {

    // Das Repository für die Typ-Entität
    private final TypRepository typRepository;

    // Konstruktorbasierte Dependency Injection des TypRepository
    public TypService(TypRepository typRepository) {
        this.typRepository = typRepository;
    }

    // Eine Methode, um alle Typ-Objekte aus der Datenbank abzurufen
    public List<Typ> findAll() {
        return typRepository.findAll();
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von Typ-Objekten
}
