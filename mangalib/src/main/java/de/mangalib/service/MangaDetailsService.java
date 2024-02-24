package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.repository.MangaDetailsRepository;
import de.mangalib.entity.MangaDetails;

import java.util.List;
import java.util.Optional;

@Service
public class MangaDetailsService {

    @Autowired
    private MangaDetailsRepository mangaDetailsRepository;

    /**
     * Findet MangaDetails anhand der ID.
     * 
     * @param id Die ID der MangaDetails.
     * @return Ein Optional von MangaDetails.
     */
    public Optional<MangaDetails> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        return mangaDetailsRepository.findById(id);
    }

    /**
     * Speichert MangaDetails.
     * 
     * @param mangaDetails Die zu speichernden MangaDetails.
     * @return Die gespeicherten MangaDetails.
     */
    public MangaDetails save(MangaDetails mangaDetails) {
        if (mangaDetails == null) {
            throw new IllegalArgumentException("MangaDetails dürfen nicht null sein");
        }
        // Weitere Validierungen nach Bedarf...
        return mangaDetailsRepository.save(mangaDetails);
    }

    /**
     * Löscht MangaDetails anhand der ID.
     * 
     * @param id Die ID der zu löschenden MangaDetails.
     */
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID darf nicht null sein");
        }
        mangaDetailsRepository.deleteById(id);
    }

    /**
     * Listet alle MangaDetails auf.
     * 
     * @return Eine Liste von MangaDetails.
     */
    public List<MangaDetails> findAll() {
        return mangaDetailsRepository.findAll();
    }

    // Weitere Methoden nach Bedarf...
}

