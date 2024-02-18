package de.mangalib.service;

import de.mangalib.Typ;
import de.mangalib.repository.TypRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class TypService {

    // Das Repository für die Typ-Entität
    private final TypRepository typRepository;

    // Konstruktorbasierte Dependency Injection des TypRepository
    public TypService(TypRepository typRepository) {
        this.typRepository = typRepository;
    }

    // Eine Methode, um alle Typ-Objekte aus der Datenbank abzurufen
    public List<Typ> findAll() {
        return typRepository.findAll();
    }

    /**
     * Fügt einen neuen Typ hinzu.
     *
     * @param typ Das Typ-Objekt, das hinzugefügt werden soll.
     * @return Das gespeicherte Typ-Objekt.
     */
    public Typ addTyp(Typ typ) {
        if (typ == null) {
            throw new IllegalArgumentException("Typ darf nicht null sein");
        }
        if (typ.getBezeichnung() == null || typ.getBezeichnung().trim().isEmpty()) {
            throw new IllegalArgumentException("Typbezeichnung darf nicht leer sein");
        }
        return typRepository.save(typ);
    }

    /**
     * Aktualisiert die Bezeichnung eines Typs.
     *
     * @param typId           Die ID des zu aktualisierenden Typs.
     * @param neueBezeichnung Die neue Bezeichnung des Typs.
     * @return Ein Optional, das den aktualisierten Typ enthält, falls gefunden.
     */
    public Optional<Typ> updateTypBezeichnung(Long typId, String neueBezeichnung) {
        if (typId == null) {
            throw new IllegalArgumentException("Typ-ID darf nicht null sein");
        }
        if (neueBezeichnung == null || neueBezeichnung.trim().isEmpty()) {
            throw new IllegalArgumentException("Neue Bezeichnung darf nicht leer sein");
        }
        return typRepository.findById(typId).map(typ -> {
            typ.setBezeichnung(neueBezeichnung);
            return typRepository.save(typ);
        });
    }

    /**
     * Ruft Typinformationen anhand der ID ab.
     *
     * @param typId Die ID des Typs, der abgerufen werden soll.
     * @return Ein Optional, das den Typ enthält, falls gefunden.
     */
    public Optional<Typ> getTypById(Long typId) {
        if (typId == null) {
            throw new IllegalArgumentException("Typ-ID darf nicht null sein");
        }
        return typRepository.findById(typId);
    }

}
