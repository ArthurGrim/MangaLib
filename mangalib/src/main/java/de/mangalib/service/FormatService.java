package de.mangalib.service;

import de.mangalib.entity.Format;
import de.mangalib.repository.FormatRepository;
import org.springframework.stereotype.Service;

import java.util.*;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class FormatService {

    // Das Repository für die Format-Entität
    private final FormatRepository formatRepository;

    // Konstruktorbasierte Dependency Injection des FormatRepository
    public FormatService(FormatRepository formatRepository) {
        this.formatRepository = formatRepository;
    }

    // Eine Methode, um alle Format-Objekte aus der Datenbank abzurufen
    public List<Format> findAll() {
        return formatRepository.findAll();
    }

    // Eine Methode, um alle Format-Objekte aus der Datenbank geordnet nach ID abzurufen
    public List<Format> findAllSortById() {
        List<Format> result = formatRepository.findAll();
        result.sort(Comparator.comparing(Format::getFormatId));
        return result;
    }

    /**
     * Fügt ein neues Format hinzu.
     *
     * @param format Das Format-Objekt, das hinzugefügt werden soll.
     * @return Das gespeicherte Format-Objekt.
     */
    public Format addFormat(Format format) {
        if (format == null) {
            throw new IllegalArgumentException("Format darf nicht null sein");
        }
        if (format.getBezeichnung() == null || format.getBezeichnung().trim().isEmpty()) {
            throw new IllegalArgumentException("Formatbezeichnung darf nicht leer sein");
        }
        return formatRepository.save(format);
    }

    /**
     * Aktualisiert die Bezeichnung eines Formats.
     *
     * @param formatId        Die ID des zu aktualisierenden Formats.
     * @param neueBezeichnung Die neue Bezeichnung des Formats.
     * @return Ein Optional, das das aktualisierte Format enthält, falls gefunden.
     */
    public Optional<Format> updateFormatBezeichnung(Long formatId, String neueBezeichnung) {
        if (formatId == null) {
            throw new IllegalArgumentException("Format-ID darf nicht null sein");
        }
        if (neueBezeichnung == null || neueBezeichnung.trim().isEmpty()) {
            throw new IllegalArgumentException("Neue Bezeichnung darf nicht leer sein");
        }
        return formatRepository.findById(formatId).map(format -> {
            format.setBezeichnung(neueBezeichnung);
            return formatRepository.save(format);
        });
    }

    /**
     * Ruft Formatinformationen anhand der ID ab.
     *
     * @param formatId Die ID des Formats, das abgerufen werden soll.
     * @return Ein Optional, das das Format enthält, falls gefunden.
     */
    public Optional<Format> getFormatById(Long formatId) {
        if (formatId == null) {
            throw new IllegalArgumentException("Format-ID darf nicht null sein");
        }
        return formatRepository.findById(formatId);
    }

}
