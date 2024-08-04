package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.repository.MangaDetailsRepository;
import de.mangalib.entity.MangaDetails;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Sammelband;

import java.util.*;

@Service
public class MangaDetailsService {

    @Autowired
    private MangaDetailsRepository mangaDetailsRepository;

    @Autowired
    private SammelbandService sammelbaendeService;

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

    /**
     * Aktualisiert den Lesestatus eines Bands.
     *
     * @param bandId     Die ID des Bands, der aktualisiert werden soll.
     * @param istGelesen Der Lesestatus des Bands.
     * @return Der aktualisierte Band, falls gefunden, sonst Optional.empty().
     */
    public Optional<MangaDetails> updateMangaDetailsIstGelesen(Long id, Boolean istGelesen) {
        if (id == null || istGelesen == null) {
            throw new IllegalArgumentException("ID und Lesestatus dürfen nicht null sein");
        }
        return mangaDetailsRepository.findById(id).map(mangaDetails -> {
            mangaDetails.setIstGelesen(istGelesen);
            return mangaDetailsRepository.save(mangaDetails);
        });
    }

    /**
     * Erstellt eine neue MangaDetails.
     * 
     * @param savedMangaReihe Die gespeicherte MangaReihe.
     * @param anilistUrl      Die URL zu AniList.
     * @param sammelbandTypId Die ID des Sammelbandtyps.
     * @param scrapedData     Die gescrapten Daten.
     * @return Die erstellte MangaDetails.
     */
    public MangaDetails createMangaDetails(MangaReihe savedMangaReihe, String anilistUrl, Long sammelbandTypId,
            Boolean istGelesen,
            Map<String, String> scrapedData) {
        MangaDetails details = new MangaDetails();
        details.setMangaReihe(savedMangaReihe);
        if (anilistUrl != null) {
            System.out.println("Setze Anilist Url");
            details.setAnilistUrl(anilistUrl);
        }
        if (sammelbandTypId != null) {
            Sammelband sammelband = sammelbaendeService.findById(sammelbandTypId).orElse(null);
            details.setSammelbaende(sammelband);
            System.out.println("SammelbandID: " + details.getSammelbaende().getId());
        } else {
            Sammelband defaultSammelband = sammelbaendeService.findById(1L).orElse(null);
            details.setSammelbaende(defaultSammelband);
        }
        if (istGelesen != null) {
            System.out.println("Setze Gelesenstatus");
            details.setIstGelesen(istGelesen);
        }
        if (scrapedData.containsKey("Band 1 Bild Url")) {
            System.out.println("Setze Cover Url");
            details.setCoverUrl(scrapedData.get("Band 1 Bild Url"));
        }
        if (scrapedData.containsKey("Deutsche Ausgabe Status")) {
            System.out.println("Setze Deutschen Status");
            details.setStatusDe(String.valueOf(scrapedData.get("Deutsche Ausgabe Status")));
        }
        if (scrapedData.containsKey("Deutsche Ausgabe Bände")) {
            System.out.println("Setze Anzahl in Deutschland erschienender Bände");
            try {
                if (scrapedData.get("Deutsche Ausgabe Bände") != null) {
                    String baendeString = scrapedData.get("Deutsche Ausgabe Bände").replace("+", "").trim();
                    details.setAnzahlBaendeDe(Integer.parseInt(baendeString));
                } else {
                    details.setAnzahlBaendeDe(null);
                }
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Status")) {
            System.out.println("Setze Status Erstveröffentlichung");
            details.setStatusErstv(String.valueOf(scrapedData.get("Erstveröffentlichung Status")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Herkunft")) {
            System.out.println("Setze Herkunftsland");
            details.setHerkunft(String.valueOf(scrapedData.get("Erstveröffentlichung Herkunft")));
        }
        if (scrapedData.containsKey("Erstveröffentlichung Startjahr")) {
            System.out.println("Setze Erscheinungsjahr");
            try {
                details.setStartJahr(Integer.parseInt(scrapedData.get("Erstveröffentlichung Startjahr")));
            } catch (NumberFormatException e) {
                // Behandlung des Fehlers oder Setzen eines Standardwerts
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Bände")) {
            System.out.println("Setze Anzahl Bände der Erstveröffentlichung");
            try {
                System.out.println(scrapedData.get("Erstveröffentlichung Bände"));
                String baendeString = scrapedData.get("Erstveröffentlichung Bände").replace("+", "").trim();
                details.setAnzahlBaendeErstv(Integer.parseInt(baendeString));
            } catch (Exception e) {
                details.setAnzahlBaendeErstv(1);
            }
        }
        return details;
    }

    /**
     * Aktualisiert die MangaDetails einer MangaReihe.
     * 
     * @param mangaReihe      Die MangaReihe, zu der die Details gehören.
     * @param anilistUrl      Die URL zu AniList.
     * @param coverUrl        Die URL zum Cover.
     * @param sammelbandTypId Die ID des Sammelbandtyps.
     * @param scrapedData     Zusätzliche Daten, die durch Web-Scraping erhalten
     *                        wurden.
     * @return Die aktualisierten MangaDetails.
     */
    public MangaDetails updateMangaDetails(MangaReihe mangaReihe, String anilistUrl, String coverUrl,
            boolean istGelesen,
            Long sammelbandTypId,
            Map<String, String> scrapedData) {
        MangaDetails details = mangaReihe.getMangaDetails();
        if (details == null) {
            details = new MangaDetails();
            details.setMangaReihe(mangaReihe);
        }
        if (anilistUrl != null) {
            if (!anilistUrl.equals(details.getAnilistUrl() != null ? details.getAnilistUrl() : "")) {
                details.setAnilistUrl(anilistUrl);
            }
        }
        if (coverUrl != null) {
            if (!coverUrl.equals(details.getCoverUrl() != null ? details.getCoverUrl() : "")) {
                details.setCoverUrl(coverUrl);
            }
        }
        if (details.isIstGelesen() != istGelesen) {
            details.setIstGelesen(istGelesen);
        }
        if (sammelbandTypId != null) {
            if (details.getSammelbaende() == null || !details.getSammelbaende().getId().equals(sammelbandTypId)) {
                Sammelband sammelband = sammelbaendeService.findById(sammelbandTypId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Sammelband mit ID " + sammelbandTypId + " nicht gefunden"));
                details.setSammelbaende(sammelband);
            }
        }

        // Weitere Details aktualisieren
        if (scrapedData.containsKey("Deutsche Ausgabe Status")) {
            if (!scrapedData.get("Deutsche Ausgabe Status")
                    .equals(details.getStatusDe() != null ? details.getStatusDe() : "")) {
                details.setStatusDe(scrapedData.get("Deutsche Ausgabe Status"));
            }
        }
        if (scrapedData.containsKey("Deutsche Ausgabe Bände")) {
            try {
                int anzahlBaendeDe = Integer.parseInt(scrapedData.get("Deutsche Ausgabe Bände"));
                if (!Integer.valueOf(anzahlBaendeDe)
                        .equals(details.getAnzahlBaendeDe() != null ? details.getAnzahlBaendeDe() : 0)) {
                    details.setAnzahlBaendeDe(anzahlBaendeDe);
                }
            } catch (NumberFormatException e) {
                // Fehlerbehandlung
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Status")) {
            if (!scrapedData.get("Erstveröffentlichung Status")
                    .equals(details.getStatusErstv() != null ? details.getStatusErstv() : "")) {
                details.setStatusErstv(scrapedData.get("Erstveröffentlichung Status"));
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Herkunft")) {
            if (!scrapedData.get("Erstveröffentlichung Herkunft")
                    .equals(details.getHerkunft() != null ? details.getHerkunft() : "")) {
                details.setHerkunft(scrapedData.get("Erstveröffentlichung Herkunft"));
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Startjahr")) {
            try {
                int startjahr = Integer.parseInt(scrapedData.get("Erstveröffentlichung Startjahr"));
                if (!Integer.valueOf(startjahr).equals(details.getStartJahr() != null ? details.getStartJahr() : 0)) {
                    details.setStartJahr(startjahr);
                }
            } catch (NumberFormatException e) {
                // Fehlerbehandlung
            }
        }
        if (scrapedData.containsKey("Erstveröffentlichung Bände")) {
            try {
                int anzahlBaendeErstv = Integer.parseInt(scrapedData.get("Erstveröffentlichung Bände"));
                if (!Integer.valueOf(anzahlBaendeErstv)
                        .equals(details.getAnzahlBaendeErstv() != null ? details.getAnzahlBaendeErstv() : 0)) {
                    details.setAnzahlBaendeErstv(anzahlBaendeErstv);
                }
            } catch (NumberFormatException e) {
                // Fehlerbehandlung
            }
        }

        return details;
    }

}
