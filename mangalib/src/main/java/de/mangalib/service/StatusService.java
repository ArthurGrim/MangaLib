package de.mangalib.service;

import de.mangalib.entity.Status;
import de.mangalib.repository.StatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    /**
     * Speichert eines neuen Status in der Datenbank.
     * 
     * @param status Der neue Status.
     * @return Der gespeicherte Status mit zugewiesener ID.
     */
    public Status addStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("Status darf nicht null sein");
        }
        if (status.getBeschreibung() == null || status.getBeschreibung().trim().isEmpty()) {
            throw new IllegalArgumentException("Statusbezeichnung darf nicht leer sein");
        }
        return statusRepository.save(status);
    }

    /**
     * Aktualisiert die Beschreibung des Status.
     * 
     * @param statusId        Die ID des zu ändernden Status.
     * @param neueBeschreibung Die neue Beschreibung des Status.
     * @return Der gespeicherte Status zur ID.
     */
    public Optional<Status> updateStatusBezeichnung(Long statusId, String neueBeschreibung) {
        if (statusId == null) {
            throw new IllegalArgumentException("Status-ID darf nicht null sein");
        }
        if (neueBeschreibung == null || neueBeschreibung.trim().isEmpty()) {
            throw new IllegalArgumentException("Neue Bezeichnung darf nicht leer sein");
        }
        return statusRepository.findById(statusId).map(status -> {
            status.setBeschreibung(neueBeschreibung);
            return statusRepository.save(status);
        });
    }

    /**
     * Gibt den Status zur eingegeben ID zurück.
     * 
     * @param statusId Die ID des gesuchten Status.
     * @return Der gespeicherte Status zur ID.
     */
    public Optional<Status> getStatusById(Long statusId) {
        if (statusId == null) {
            throw new IllegalArgumentException("Status-ID darf nicht null sein");
        }
        return statusRepository.findById(statusId);
    }
    
}
