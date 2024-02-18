package de.mangalib.service;

import de.mangalib.MangaReihe;
import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.Status;
import de.mangalib.repository.StatusRepository;
import de.mangalib.Verlag;
import de.mangalib.repository.VerlagRepository;
import de.mangalib.Typ;
import de.mangalib.repository.TypRepository;
import de.mangalib.Format;
import de.mangalib.repository.FormatRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Markiert die Klasse als Service-Komponente für Spring
@Service
public class MangaReiheService {

    // Das Repository für die MangaReihe-Entität
    private final MangaReiheRepository mangaReiheRepository;
    private final StatusRepository statusRepository;
    private final VerlagRepository verlagRepository;
    private final TypRepository typRepository;
    private final FormatRepository formatRepository;

    // Konstruktorbasierte Dependency Injection des MangaReiheRepository
    public MangaReiheService(MangaReiheRepository mangaReiheRepository, StatusRepository statusRepository, FormatRepository formatRepository, TypRepository typRepository, VerlagRepository verlagRepository) {
        this.mangaReiheRepository = mangaReiheRepository;
        this.statusRepository = statusRepository;
        this.verlagRepository = verlagRepository;
        this.typRepository = typRepository;
        this.formatRepository = formatRepository;
    }

    // Eine Methode, um alle MangaReihe-Objekte aus der Datenbank abzurufen
    public List<MangaReihe> findAll() {
        return mangaReiheRepository.findAll();
    }

    /**
     * Speichert eine neue MangaReihe in der Datenbank.
     * 
     * @param mangaReihe Die zu speichernde MangaReihe.
     * @return Die gespeicherte MangaReihe mit zugewiesener ID.
     */
    public MangaReihe saveMangaReihe(MangaReihe mangaReihe) {
        if (mangaReihe == null) {
            throw new IllegalArgumentException("MangaReihe darf nicht null sein");
        }
        return mangaReiheRepository.save(mangaReihe);
    }

    /**
     * Aktualisiert des Titels einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerTitel   Der neue Titel der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheTitel(Long mangaReiheId, String neuerTitel) {
        if (mangaReiheId == null || neuerTitel == null) {
            throw new IllegalArgumentException("ID und Titel dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setTitel(neuerTitel);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert der AnzahlBaende einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neueAnzahl   Der Anzahl der Baende der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheAnzahlBaende(Long mangaReiheId, Integer neueAnzahl) {
        if (mangaReiheId == null || neueAnzahl == null) {
            throw new IllegalArgumentException("ID und Anzahl der Bände dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setAnzahlBaende(neueAnzahl);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des PreisProBand einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerPreis   Der PreisProBand der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReihePreisProBand(Long mangaReiheId, Double neuerPreis) {
        if (mangaReiheId == null || neuerPreis == null) {
            throw new IllegalArgumentException("ID und Preis pro Band dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setPreisProBand(neuerPreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Gesamtpreis einer MangaReihe.
     * 
     * @param mangaReiheId     Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerGesamtpreis Der Gesamtpreis der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheGesamtpreis(Long mangaReiheId, Double neuerGesamtpreis) {
        if (mangaReiheId == null || neuerGesamtpreis == null) {
            throw new IllegalArgumentException("ID und Gesamtpreis dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setGesamtpreis(neuerGesamtpreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des istEbayPreis einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param istEbayPreis Handelt es sich um einen EbayPreis der MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheIstEbayPreis(Long mangaReiheId, Boolean istEbayPreis) {
        if (mangaReiheId == null || istEbayPreis == null) {
            throw new IllegalArgumentException("ID und Ebay-Preis-Status dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setIstEbayPreis(istEbayPreis);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des istVergriffen einer MangaReihe.
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param istVergriffen Handelt es sich um eine vergriffene MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheIstVergriffen(Long mangaReiheId, Boolean istVergriffen) {
        if (mangaReiheId == null || istVergriffen == null) {
            throw new IllegalArgumentException("ID und Vergriffen-Status dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setIstVergriffen(istVergriffen);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Status einer MangaReihe.
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param statusId Die ID des neuen Status.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheStatus(Long mangaReiheId, Long statusId) {
        if (mangaReiheId == null || statusId == null) {
            throw new IllegalArgumentException("ID und Status-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Status status = statusRepository.findById(statusId)
                    .orElseThrow(() -> new IllegalArgumentException("Status mit ID " + statusId + " nicht gefunden"));
            mangaReihe.setStatusID(status);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Verlag einer MangaReihe.
     * Nur um die Option zu haben. Der Verlag sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param verlagId Die ID des neuen Verlags.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheVerlag(Long mangaReiheId, Long verlagId) {
        if (mangaReiheId == null || verlagId == null) {
            throw new IllegalArgumentException("ID und Verlag-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Verlag verlag = verlagRepository.findById(verlagId)
                    .orElseThrow(() -> new IllegalArgumentException("Verlag mit ID " + verlagId + " nicht gefunden"));
            mangaReihe.setVerlagID(verlag);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Typs einer MangaReihe.
     * Nur um die Option zu haben. Der Typ sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param typId Die ID des neuen Typs.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheTyp(Long mangaReiheId, Long typId) {
        if (mangaReiheId == null || typId == null) {
            throw new IllegalArgumentException("ID und Typ-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Typ typ = typRepository.findById(typId)
                    .orElseThrow(() -> new IllegalArgumentException("Typ mit ID " + typId + " nicht gefunden"));
            mangaReihe.setTypID(typ);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Typs einer MangaReihe.
     * Nur um die Option zu haben. Das Format sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId  Die ID der MangaReihe, die aktualisiert werden soll.
     * @param formatId Die ID des neuen Formats.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheFormat(Long mangaReiheId, Long formatId) {
        if (mangaReiheId == null || formatId == null) {
            throw new IllegalArgumentException("ID und Format-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Format format = formatRepository.findById(formatId)
                    .orElseThrow(() -> new IllegalArgumentException("Format mit ID " + formatId + " nicht gefunden"));
            mangaReihe.setFormatID(format);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    // Weitere Methoden können hier hinzugefügt werden, um spezifische
    // Geschäftslogik zu implementieren
    // Zum Beispiel: Erstellen, Aktualisieren, Löschen von MangaReihe-Objekten
}
