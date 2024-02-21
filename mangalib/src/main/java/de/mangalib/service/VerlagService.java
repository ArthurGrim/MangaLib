package de.mangalib.service;

import de.mangalib.entity.Verlag;
import de.mangalib.repository.VerlagRepository;
import org.springframework.stereotype.Service;

import java.util.*;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class VerlagService {

    // Das Repository für die Verlag-Entität
    private final VerlagRepository verlagRepository;

    // Konstruktorbasierte Dependency Injection des VerlagRepository
    public VerlagService(VerlagRepository verlageRepository) {
        this.verlagRepository = verlageRepository;
    }

    // Eine Methode, um alle Verlag-Objekte aus der Datenbank abzurufen
    public List<Verlag> findAll() {
        return verlagRepository.findAll();
    }

    // Eine Methode, um alle Verlag-Objekte aus der Datenbank geordnet nach ID abzurufen
    public List<Verlag> findAllSortById() {
        List<Verlag> result = verlagRepository.findAll();
        result.sort(Comparator.comparing(Verlag::getVerlagId));
        return result;
    }

    /**
     * Speichert eines neuen Verlags in der Datenbank.
     * 
     * @param verlag Der neue Verlag.
     * @return Der gespeicherte Verlag mit zugewiesener ID.
     */
    public Verlag addVerlag(Verlag verlag) {
        if (verlag == null) {
            throw new IllegalArgumentException("Verlag darf nicht null sein");
        }
        if (verlag.getName() == null || verlag.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Verlagsname darf nicht leer sein");
        }
        return verlagRepository.save(verlag);
    }

    /**
     * Aktualisiert den Namen des Verlags.
     * 
     * @param verlagId        Die ID des zu ändernden Verlags.
     * @param neueBezeichnung Der neue Name des Verlags.
     * @return Der gespeicherte Verlag zur ID.
     */
    public Optional<Verlag> updateVerlagBezeichnung(Long verlagId, String neuerName) {
        if (verlagId == null) {
            throw new IllegalArgumentException("Verlag-ID darf nicht null sein");
        }
        if (neuerName == null || neuerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Neuer Name darf nicht leer sein");
        }
        return verlagRepository.findById(verlagId).map(verlag -> {
            verlag.setName(neuerName);
            return verlagRepository.save(verlag);
        });
    }

    /**
     * Gibt den Verlag zur eingegeben ID zurück.
     * 
     * @param verlagId Die ID des gesuchten Verlags.
     * @return Der gespeicherte Verlag zur ID.
     */
    public Optional<Verlag> getVerlagById(Long verlagId) {
        if (verlagId == null) {
            throw new IllegalArgumentException("Verlag-ID darf nicht null sein");
        }
        return verlagRepository.findById(verlagId);
    }

}
