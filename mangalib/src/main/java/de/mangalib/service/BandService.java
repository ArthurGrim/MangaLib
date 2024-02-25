package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.entity.Band;
import de.mangalib.repository.BandRepository;

import java.util.List;
import java.util.Optional;

@Service
public class BandService {

    @Autowired
    private BandRepository baendeRepository;

    /**
     * Findet einen Band anhand der ID.
     * 
     * @param id Die ID des Bandes.
     * @return Ein Optional von Baende.
     */
    public Optional<Band> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        return baendeRepository.findById(id);
    }

    /**
     * Speichert einen Band.
     * 
     * @param baende Der zu speichernde Band.
     * @return Der gespeicherte Band.
     */
    public Band save(Band baende) {
        if (baende == null) {
            throw new IllegalArgumentException("Baende darf nicht null sein");
        }
        return baendeRepository.save(baende);
    }

    /**
     * Löscht einen Band anhand der ID.
     * 
     * @param id Die ID des zu löschenden Bandes.
     */
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        baendeRepository.deleteById(id);
    }

    /**
     * Listet alle Bände auf.
     * 
     * @return Eine Liste von Baende.
     */
    public List<Band> findAll() {
        return baendeRepository.findAll();
    }

    // Weitere Methoden nach Bedarf...
}

