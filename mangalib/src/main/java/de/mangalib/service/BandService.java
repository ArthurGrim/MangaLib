package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.entity.Band;
import de.mangalib.repository.BandRepository;

import java.util.List;
import java.util.Optional;
import java.time.Year;
import java.math.BigDecimal;

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
    public Band saveBand (Band baende) {
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

    /**
     * Aktualisiert die Änderung des Preises eines Bands.
     *
     * @param bandId          Die ID des Bands, der aktualisiert werden soll.
     * @param aenderungPreis  Die Änderung des Preises des Bands.
     * @return Der aktualisierte Band, falls gefunden, sonst Optional.empty().
     */
    public Optional<Band> updateBandAenderungPreis(Long bandId, BigDecimal aenderungPreis) {
        if (bandId == null || aenderungPreis == null) {
            throw new IllegalArgumentException("ID und Änderung des Preises dürfen nicht null sein");
        }
        return bandRepository.findById(bandId).map(band -> {
            band.setAenderungPreis(aenderungPreis);
            return bandRepository.save(band);
        });
    }

    /**
     * Aktualisiert den Lesestatus eines Bands.
     *
     * @param bandId     Die ID des Bands, der aktualisiert werden soll.
     * @param istGelesen Der Lesestatus des Bands.
     * @return Der aktualisierte Band, falls gefunden, sonst Optional.empty().
     */
    public Optional<Band> updateBandIstGelesen(Long bandId, Boolean istGelesen) {
        if (bandId == null || istGelesen == null) {
            throw new IllegalArgumentException("ID und Lesestatus dürfen nicht null sein");
        }
        return bandRepository.findById(bandId).map(band -> {
            band.setIstGelesen(istGelesen);
            return bandRepository.save(band);
        });
    }

    /**
     * Filtert Bände nach Jahr.
     * 
     * @param jahr Das Jahr, nach welchem gefiltert wird.
     * @return Eine Liste von Bänden, die im angegebenen Jahr erstellt wurden.
     */
    public List<Band> findByErstelltAmYear(int jahr) {
        if (jahr < 1900 || jahr > Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        return bandRepository.findByErstelltAmYear(jahr);
    }

    /**
     * Filtert Bände nach Jahr und Monat.
     * 
     * @param jahr  Das Jahr, nach welchem gefiltert wird.
     * @param monat Der Monat, nach welchem gefiltert wird.
     * @return Eine Liste von Bänden, die im angegebenen Jahr und Monat erstellt wurden.
     */
    public List<Band> findByErstelltAmYearAndMonth(int jahr, int monat) {
        if (jahr < 1900 || jahr > Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }
        return bandRepository.findByErstelltAmYearAndMonth(jahr, monat);
    }

    /**
     * Filtert Bände nach dem aktuellen Jahr und einem spezifischen Monat.
     * 
     * @param monat Der Monat, nach dem gefiltert wird. Muss zwischen 1 und 12 liegen.
     * @return Eine Liste von Bänden, die im aktuellen Jahr und im angegebenen Monat erstellt wurden.
     * @throws IllegalArgumentException wenn der Monat außerhalb des Bereichs 1-12 liegt.
     */
    public List<Band> findByErstelltAmCurrentYearAndMonth(int monat) {
        // Ermitteln des aktuellen Jahres
        int currentYear = Year.now().getValue();

        // Überprüfen, ob der Monat im gültigen Bereich liegt
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }

        // Aufruf der Methode findByErstelltAmYearAndMonth mit dem aktuellen Jahr und dem übergebenen Monat
        return findByErstelltAmYearAndMonth(currentYear, monat);
    }
}
