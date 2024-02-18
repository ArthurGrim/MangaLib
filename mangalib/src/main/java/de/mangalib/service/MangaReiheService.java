package de.mangalib.service;

import de.mangalib.MangaReihe;
import de.mangalib.repository.MangaReiheRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class MangaReiheService {

    // Das Repository für die MangaReihe-Entität
    private final MangaReiheRepository mangaReiheRepository;

    // Konstruktorbasierte Dependency Injection des MangaReiheRepository
        public MangaReiheService(MangaReiheRepository mangaReiheRepository) {
        this.mangaReiheRepository = mangaReiheRepository;
    }

    // Eine Methode, um alle MangaReihe-Objekte aus der Datenbank abzurufen
    public List<MangaReihe> findAll() {
        return mangaReiheRepository.findAll();
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von MangaReihe-Objekten
}
