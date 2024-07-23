package de.mangalib.service;

import org.springframework.stereotype.Service;

import de.mangalib.entity.EinkaufslisteItemDetails;
import de.mangalib.repository.EinkaufslisteItemDetailsRepository;

import java.util.List;
import java.util.Optional;

@Service
public class EinkaufslisteItemDetailsService {

    private final EinkaufslisteItemDetailsRepository einkaufslisteItemDetailsRepository;

    public EinkaufslisteItemDetailsService(EinkaufslisteItemDetailsRepository einkaufslisteItemDetailsRepository) {
        this.einkaufslisteItemDetailsRepository = einkaufslisteItemDetailsRepository;
    }

    /**
     * Speichert eine EinkaufslisteItemDetails in der Datenbank.
     *
     * @param einkaufslisteItemDetails Das zu speichernde EinkaufslisteItemDetails-Objekt.
     * @return Das gespeicherte EinkaufslisteItemDetails-Objekt.
     */
    public EinkaufslisteItemDetails saveEinkaufslisteItemDetails(EinkaufslisteItemDetails einkaufslisteItemDetails) {
        if (einkaufslisteItemDetails == null) {
            throw new IllegalArgumentException("EinkaufslisteItemDetails darf nicht null sein");
        }
        return einkaufslisteItemDetailsRepository.save(einkaufslisteItemDetails);
    }

    /**
     * Findet ein EinkaufslisteItemDetails nach seiner ID.
     *
     * @param id Die ID des gesuchten EinkaufslisteItemDetails.
     * @return Das gefundene EinkaufslisteItemDetails-Objekt, oder Optional.empty() falls nicht gefunden.
     */
    public Optional<EinkaufslisteItemDetails> findItemDetailsById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        return einkaufslisteItemDetailsRepository.findById(id);
    }

    /**
     * Listet alle EinkaufslisteItemDetails auf.
     * 
     * @return Eine Liste von EinkaufslisteItemDetails.
     */
    public List<EinkaufslisteItemDetails> findAll() {
        return einkaufslisteItemDetailsRepository.findAll();
    }

    /**
     * Löscht ein EinkaufslisteItemDetails nach seiner ID.
     *
     * @param id Die ID des zu löschenden EinkaufslisteItemDetails.
     */
    public void deleteItemDetailsById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        einkaufslisteItemDetailsRepository.deleteById(id);
    }
}
