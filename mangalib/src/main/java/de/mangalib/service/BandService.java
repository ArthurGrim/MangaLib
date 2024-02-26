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
    private BandRepository bandRepository;

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
        return bandRepository.findById(id);
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
        return bandRepository.save(baende);
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
        bandRepository.deleteById(id);
    }

    /**
     * Listet alle Bände auf.
     * 
     * @return Eine Liste von Baende.
     */
    public List<Band> findAll() {
        return bandRepository.findAll();
    }

    /**
     * Findet den ersten Band einer MangaReihe anhand der MangaReihe-ID.
     * 
     * @param mangaReiheId Die ID der MangaReihe, für die der erste Band gesucht
     *                     wird.
     * @return Der erste Band der MangaReihe, falls vorhanden, sonst null.
     */
    public Band getFirstBandByMangaReiheId(Long mangaReiheId) {
        if (mangaReiheId == null) {
            // Optional: Werfen Sie eine Ausnahme oder geben Sie null zurück, wenn die
            // mangaReiheId null ist
            throw new IllegalArgumentException("MangaReiheId darf nicht null sein.");
        }

        return bandRepository.findFirstBandByMangaReiheId(mangaReiheId)
                .orElse(null); // oder werfen Sie eine Ausnahme, wenn der Band nicht gefunden wird
    }
}
