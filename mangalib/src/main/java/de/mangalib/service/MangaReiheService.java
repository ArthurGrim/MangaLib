package de.mangalib.service;

import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.repository.StatusRepository;
import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;
import de.mangalib.repository.VerlagRepository;
import de.mangalib.repository.TypRepository;
import de.mangalib.repository.FormatRepository;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.*;

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
    public MangaReiheService(MangaReiheRepository mangaReiheRepository, StatusRepository statusRepository,
            FormatRepository formatRepository, TypRepository typRepository, VerlagRepository verlagRepository) {
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

    // Eine Methode, um alle MangaReihe-Objekte aus der Datenbank geordnet nach ID abzurufen
    public List<MangaReihe> findAllSortById() {
        List<MangaReihe> result = mangaReiheRepository.findAll();
        result.sort(Comparator.comparing(MangaReihe::getId));
        return result;
    }

    public List<MangaReihe> findAllSortByTitel() {
        return mangaReiheRepository.findAll(Sort.by(Sort.Direction.ASC, "titel"));
    }

    /**
     * Speichert eine neuen MangaReihe in der Datenbank.
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

    // ------------------------------Aktualisieren--------------------------------

    /**
     * Aktualisiert den mangaIndex einer MangaReihe.
     * 
     * @param mangaReiheId    Die ID der MangaReihe, die aktualisiert werden soll.
     * @param neuerMangaIndex Der neue mangaIndex für die MangaReihe.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheMangaIndex(Long mangaReiheId, Integer neuerMangaIndex) {
        if (mangaReiheId == null || neuerMangaIndex == null) {
            throw new IllegalArgumentException("ID und mangaIndex dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            mangaReihe.setMangaIndex(neuerMangaIndex);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Status einer MangaReihe.
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param statusId     Die ID des neuen Status.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheStatus(Long mangaReiheId, Long statusId) {
        if (mangaReiheId == null || statusId == null) {
            throw new IllegalArgumentException("ID und Status-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Status status = statusRepository.findById(statusId)
                    .orElseThrow(() -> new IllegalArgumentException("Status mit ID " + statusId + " nicht gefunden"));
            mangaReihe.setStatus(status);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert den Verlag einer MangaReihe.
     * Nur um die Option zu haben. Der Verlag sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param verlagId     Die ID des neuen Verlags.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheVerlag(Long mangaReiheId, Long verlagId) {
        if (mangaReiheId == null || verlagId == null) {
            throw new IllegalArgumentException("ID und Verlag-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Verlag verlag = verlagRepository.findById(verlagId)
                    .orElseThrow(() -> new IllegalArgumentException("Verlag mit ID " + verlagId + " nicht gefunden"));
            mangaReihe.setVerlag(verlag);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Typs einer MangaReihe.
     * Nur um die Option zu haben. Der Typ sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param typId        Die ID des neuen Typs.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheTyp(Long mangaReiheId, Long typId) {
        if (mangaReiheId == null || typId == null) {
            throw new IllegalArgumentException("ID und Typ-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Typ typ = typRepository.findById(typId)
                    .orElseThrow(() -> new IllegalArgumentException("Typ mit ID " + typId + " nicht gefunden"));
            mangaReihe.setTyp(typ);
            return mangaReiheRepository.save(mangaReihe);
        });
    }

    /**
     * Aktualisiert des Formats einer MangaReihe.
     * Nur um die Option zu haben. Das Format sollte sich eigentlich nicht ändern
     * 
     * @param mangaReiheId Die ID der MangaReihe, die aktualisiert werden soll.
     * @param formatId     Die ID des neuen Formats.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> updateMangaReiheFormat(Long mangaReiheId, Long formatId) {
        if (mangaReiheId == null || formatId == null) {
            throw new IllegalArgumentException("ID und Format-ID dürfen nicht null sein");
        }
        return mangaReiheRepository.findById(mangaReiheId).map(mangaReihe -> {
            Format format = formatRepository.findById(formatId)
                    .orElseThrow(() -> new IllegalArgumentException("Format mit ID " + formatId + " nicht gefunden"));
            mangaReihe.setFormat(format);
            return mangaReiheRepository.save(mangaReihe);
        });
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

    // ------------------------------Filtern--------------------------------

    /**
     * Filtert MangaReihen nach Status.
     * 
     * @param statusId Die ID des Status nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Status entsprechen.
     */
    public List<MangaReihe> findByStatus(Long statusId) {
        if (statusId == null) {
            throw new IllegalArgumentException("Status-ID darf nicht null sein");
        }

        // Hole das Status-Objekt anhand der ID
        Optional<Status> status = statusRepository.findById(statusId);

        // Überprüfe, ob der Status vorhanden ist
        if (!status.isPresent()) {
            throw new IllegalArgumentException("Status mit der ID " + statusId + " existiert nicht");
        }

        // Verwende das Status-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByStatus(status.get());
    }

    /**
     * Filtert MangaReihen nach Verlag.
     * 
     * @param verlagId Die ID des Verlags nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Verlag entsprechen.
     */
    public List<MangaReihe> findByVerlag(Long verlagId) {
        if (verlagId == null) {
            throw new IllegalArgumentException("Verlag-ID darf nicht null sein");
        }

        // Hole das Verlag-Objekt anhand der ID
        Optional<Verlag> verlag = verlagRepository.findById(verlagId);

        // Überprüfe, ob der Verlag vorhanden ist
        if (!verlag.isPresent()) {
            throw new IllegalArgumentException("Verlag mit der ID " + verlagId + " existiert nicht");
        }

        // Verwende das Verlag-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByVerlag(verlag.get());
    }

    /**
     * Filtert MangaReihen nach Typ.
     * 
     * @param typId Die ID des Typs nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Typ entsprechen.
     */
    public List<MangaReihe> findByTyp(Long typId) {
        if (typId == null) {
            throw new IllegalArgumentException("Typ-ID darf nicht null sein");
        }

        // Hole das Typ-Objekt anhand der ID
        Optional<Typ> typ = typRepository.findById(typId);

        // Überprüfe, ob der Typ vorhanden ist
        if (!typ.isPresent()) {
            throw new IllegalArgumentException("Typ mit der ID " + typId + " existiert nicht");
        }

        // Verwende das Typ-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByTyp(typ.get());
    }

    /**
     * Filtert MangaReihen nach Format.
     * 
     * @param formatId Die ID des Formats nach welcher gefiltert wird.
     * @return Eine Liste von MangaReihen, die dem gegebenen Format entsprechen.
     */
    public List<MangaReihe> findByFormat(Long formatId) {
        if (formatId == null) {
            throw new IllegalArgumentException("Format-ID darf nicht null sein");
        }

        // Hole das Format-Objekt anhand der ID
        Optional<Format> format = formatRepository.findById(formatId);

        // Überprüfe, ob das Format vorhanden ist
        if (!format.isPresent()) {
            throw new IllegalArgumentException("Format mit der ID " + formatId + " existiert nicht");
        }

        // Verwende das Format-Objekt, um die MangaReihen zu filtern
        return mangaReiheRepository.findByFormat(format.get());
    }

    /**
     * Filtert MangaReihen nach Jahr.
     * 
     * @param jahr Das Jahr nach welchem gefiltert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByErstelltAmYear(int jahr) {
        if (jahr < 1900 || jahr > java.time.Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        return mangaReiheRepository.findByErstelltAmYear(jahr);
    }

    /**
     * Filtert MangaReihen nach Jahr und Monat.
     * 
     * @param jahr  Das Jahr nach welchem gefiltert wird.
     * @param monat Der Monat nach welchem gefiltert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByErstelltAmYearAndMonth(int jahr, int monat) {
        if (jahr < 1900 || jahr > java.time.Year.now().getValue()) {
            throw new IllegalArgumentException("Das Jahr muss zwischen 1900 und dem aktuellen Jahr liegen");
        }
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }
        return mangaReiheRepository.findByErstelltAmYearAndMonth(jahr, monat);
    }

    // ------------------------------Suchen--------------------------------

    /**
     * Sucht nach einer bestimmten ID.
     * 
     * @param id Die ID welche gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaReihe> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Die ID darf nicht null sein");
        }
        return mangaReiheRepository.findById(id);
    }

    /**
     * Sucht nach einem bestimmten Index.
     * 
     * @param mangaIndex Der Index welche gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByMangaIndex(Integer mangaIndex) {
        if (mangaIndex == null) {
            throw new IllegalArgumentException("Der mangaIndex darf nicht null sein");
        }
        return mangaReiheRepository.findByMangaIndex(mangaIndex);
    }

    /**
     * Sucht nach einer bestimmten Titel.
     * 
     * @param titel Der Titel nach welchem gesucht wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findByTitel(String titel) {
        if (titel == null || titel.trim().isEmpty()) {
            throw new IllegalArgumentException("Der Titel darf nicht leer sein");
        }
        return mangaReiheRepository.findByTitelContainingIgnoreCase(titel);
    }

    // ------------------------------Sortieren--------------------------------

    /**
     * Methode zum Sortieren von MangaReihen nach einem bestimmten Attribut
     * 
     * @param sortAttribute Das Attribut nach welchem sortiert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findAllSorted(String sortAttribute) {
        if (sortAttribute == null || sortAttribute.trim().isEmpty()) {
            throw new IllegalArgumentException("Sortierattribut darf nicht leer sein");
        }
        return mangaReiheRepository.findAll(Sort.by(sortAttribute));
    }

    /**
     * Methode zum Sortieren von MangaReihen nach einem bestimmten Attribut und
     * Richtung
     * 
     * @param sortAttribute Das Attribut nach welchem sortiert wird.
     * @param direction     Ob absteigend oder aufsteigend sortiert wird.
     * @return Die aktualisierte MangaReihe, falls gefunden, sonst Optional.empty().
     */
    public List<MangaReihe> findAllSorted(String sortAttribute, Sort.Direction direction) {
        if (sortAttribute == null || sortAttribute.trim().isEmpty()) {
            throw new IllegalArgumentException("Sortierattribut darf nicht leer sein");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Sortierrichtung darf nicht null sein");
        }
        return mangaReiheRepository.findAll(Sort.by(direction, sortAttribute));
    }

}
