package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.repository.SammelbandRepository;
import de.mangalib.entity.Sammelband;

import java.util.List;
import java.util.Optional;

@Service
public class SammelbandService {

    @Autowired
    private SammelbandRepository sammelbaendeRepository;

    /**
     * Findet Sammelbaende anhand der ID.
     * 
     * @param id Die ID der Sammelbaende.
     * @return Ein Optional von Sammelbaende.
     */
    public Optional<Sammelband> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        return sammelbaendeRepository.findById(id);
    }

    /**
     * Speichert Sammelbaende.
     * 
     * @param sammelbaende Die zu speichernden Sammelbaende.
     * @return Die gespeicherten Sammelbaende.
     */
    public Sammelband save(Sammelband sammelbaende) {
        if (sammelbaende == null) {
            throw new IllegalArgumentException("Sammelbaende dürfen nicht null sein");
        }
        // Weitere Validierungen nach Bedarf...
        return sammelbaendeRepository.save(sammelbaende);
    }

    /**
     * Löscht Sammelbaende anhand der ID.
     * 
     * @param id Die ID der zu löschenden Sammelbaende.
     */
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        sammelbaendeRepository.deleteById(id);
    }

    /**
     * Listet alle Sammelbaende auf.
     * 
     * @return Eine Liste von Sammelbaende.
     */
    public List<Sammelband> findAll() {
        return sammelbaendeRepository.findAll();
    }

    // Weitere Methoden nach Bedarf...
}

