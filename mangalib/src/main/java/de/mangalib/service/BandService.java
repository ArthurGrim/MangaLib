package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.entity.Band;
import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.BandRepository;

import java.util.*;
import java.time.Year;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

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
    public Band saveBand(Band baende) {
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
     * @param bandId         Die ID des Bands, der aktualisiert werden soll.
     * @param aenderungPreis Die Änderung des Preises des Bands.
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
     * @return Eine Liste von Bänden, die im angegebenen Jahr und Monat erstellt
     *         wurden.
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
     * @param monat Der Monat, nach dem gefiltert wird. Muss zwischen 1 und 12
     *              liegen.
     * @return Eine Liste von Bänden, die im aktuellen Jahr und im angegebenen Monat
     *         erstellt wurden.
     * @throws IllegalArgumentException wenn der Monat außerhalb des Bereichs 1-12
     *                                  liegt.
     */
    public List<Band> findByErstelltAmCurrentYearAndMonth(int monat) {
        // Ermitteln des aktuellen Jahres
        int currentYear = Year.now().getValue();

        // Überprüfen, ob der Monat im gültigen Bereich liegt
        if (monat < 1 || monat > 12) {
            throw new IllegalArgumentException("Der Monat muss zwischen 1 und 12 liegen");
        }

        // Aufruf der Methode findByErstelltAmYearAndMonth mit dem aktuellen Jahr und
        // dem übergebenen Monat
        return findByErstelltAmYearAndMonth(currentYear, monat);
    }

    /**
     * Erstellt und speichert die Bände einer MangaReihe in der Datenbank.
     * 
     * @param mangaReihe           Die MangaReihe, zu der die Bände gehören.
     * @param anzahlBaende         Die Anzahl der Bände.
     * @param preisProBand         Der Preis pro Band.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     * @param scrapedData          Zusätzliche Daten, die durch Web-Scraping
     *                             erhalten wurden.
     */
    public void createAndSaveBaende(MangaReihe mangaReihe, Integer anzahlBaende, Double preisProBand,
            Double gesamtpreisAenderung, Map<String, String> scrapedData) {
        for (int i = 1; i <= anzahlBaende; i++) {
            Band band = new Band();
            band.setMangaReihe(mangaReihe);
            band.setBandNr(i);

            String bildUrlKey = "Band " + i + " Bild Url";
            String mpUrlKey = "Band " + i + " href";
            String preisKey = "Band " + i + " Preis";

            try {
                // Fuege den Link zum Bild bei den ersten 5 Baenden ein
                if (scrapedData.containsKey(bildUrlKey) && i <= 5) {
                    String bildUrlString = scrapedData.get(bildUrlKey);
                    if (bildUrlString != null && !bildUrlString.isEmpty()) {
                        URI bildUri = new URI(bildUrlString);
                        URL bildUrl = bildUri.toURL();
                        band.setBildUrl(bildUrl);
                    }
                }

                // Fuege den Link zur entsprechenden MangaPassion Seite ein
                if (scrapedData.containsKey(mpUrlKey)) {
                    String mpUrlString = scrapedData.get(mpUrlKey);
                    if (mpUrlString != null && !mpUrlString.isEmpty()) {
                        URI mpUri = new URI(mpUrlString);
                        URL mpUrl = mpUri.toURL();
                        band.setMpUrl(mpUrl);
                    }
                }

                // Fuege den Preis ein, wenn vorhanden
                if (scrapedData.containsKey(preisKey)) {
                    try {
                        band.setPreis(new BigDecimal(scrapedData.get(preisKey)));
                    } catch (NumberFormatException e) {
                        band.setPreis(BigDecimal.valueOf(preisProBand));
                    }
                } else {
                    band.setPreis(BigDecimal.valueOf(preisProBand));
                }

                //Setzt AenderungGesamtpreis, wenn vorhanden
                if (gesamtpreisAenderung > 0) {
                    BigDecimal aenderungPreis = (gesamtpreisAenderung != null
                            ? BigDecimal.valueOf(gesamtpreisAenderung)
                            : BigDecimal.ZERO)
                            .divide(BigDecimal.valueOf(anzahlBaende), 2, RoundingMode.HALF_UP);
                    band.setAenderungPreis(aenderungPreis);
                } else {
                    band.setAenderungPreis(new BigDecimal(0));
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            bandRepository.save(band);
        }
    }

    /**
     * Berechnet den Gesamtpreis einer MangaReihe basierend auf den Preisen der
     * Bände.
     * 
     * @param mangaReihe Die MangaReihe, deren Gesamtpreis berechnet werden soll.
     * @return Der Gesamtpreis der MangaReihe.
     */
    public BigDecimal calculateGesamtpreis(MangaReihe mangaReihe) {
        List<Band> baende = bandRepository.findByMangaReiheId(mangaReihe.getId());
        BigDecimal gesamtpreis = BigDecimal.ZERO;
        for (Band band : baende) {
            gesamtpreis = gesamtpreis.add(band.getPreis().add(band.getAenderungPreis()));
        }
        return gesamtpreis;
    }

    /**
     * Aktualisiert oder erstellt die Bände einer MangaReihe.
     * 
     * @param mangaReihe   Die MangaReihe, zu der die Bände gehören.
     * @param anzahlBaende Die Anzahl der Bände in der Reihe.
     * @param preisProBand Der Preis pro Band.
     * @param scrapedData  Zusätzliche Daten, die durch Web-Scraping erhalten
     *                     wurden.
     */
    public void updateOrCreateBaende(MangaReihe mangaReihe, Integer anzahlBaende, Double preisProBand, Double gesamtpreisAenderung,
            Map<String, String> scrapedData) {
        List<Band> existierendeBaende = bandRepository.findByMangaReiheId(mangaReihe.getId());
        int existierendeAnzahl = existierendeBaende.size();

        for (int i = 1; i <= anzahlBaende; i++) {
            Band band;
            if (i <= existierendeAnzahl) {
                // Aktualisieren eines existierenden Bandes
                band = existierendeBaende.get(i - 1);
            } else {
                // Erstellen eines neuen Bandes
                band = new Band();
                band.setMangaReihe(mangaReihe);
                band.setBandNr(i);
            }

            band.setPreis(BigDecimal.valueOf(preisProBand));

            String bildUrlKey = "Band " + i + " Bild Url";
            String mpUrlKey = "Band " + i + " href";
            String preisKey = "Band " + i + " Preis";

            try {
                // Fuege den Link zum Bild bei den ersten 5 Baenden ein
                if ((scrapedData.containsKey(bildUrlKey) && i <= 5)  && !scrapedData.get(bildUrlKey).equals(band.getBildUrl().toString())) {
                    String bildUrlString = scrapedData.get(bildUrlKey);
                    if (bildUrlString != null && !bildUrlString.isEmpty()) {
                        URI bildUri = new URI(bildUrlString);
                        URL bildUrl = bildUri.toURL();
                        band.setBildUrl(bildUrl);
                    }
                }

                // Fuege den Link zur entsprechenden MangaPassion Seite ein
                if (scrapedData.containsKey(mpUrlKey) && !scrapedData.get(mpUrlKey).equals(band.getMpUrl().toString())) {
                    String mpUrlString = scrapedData.get(mpUrlKey);
                    if (mpUrlString != null && !mpUrlString.isEmpty()) {
                        URI mpUri = new URI(mpUrlString);
                        URL mpUrl = mpUri.toURL();
                        band.setMpUrl(mpUrl);
                    }
                }

                // Fuege den Preis ein, wenn vorhanden
                if (scrapedData.containsKey(preisKey)) {
                    try {
                        band.setPreis(new BigDecimal(scrapedData.get(preisKey)));
                    } catch (NumberFormatException e) {
                        band.setPreis(BigDecimal.valueOf(preisProBand));
                    }
                } else {
                    band.setPreis(BigDecimal.valueOf(preisProBand));
                }

                //Setzt AenderungGesamtpreis, wenn vorhanden
                if (gesamtpreisAenderung > 0) {
                    BigDecimal aenderungPreis = (gesamtpreisAenderung != null
                            ? BigDecimal.valueOf(gesamtpreisAenderung)
                            : BigDecimal.ZERO)
                            .divide(BigDecimal.valueOf(anzahlBaende), 2, RoundingMode.HALF_UP);
                    band.setAenderungPreis(aenderungPreis);
                } else {
                    band.setAenderungPreis(new BigDecimal(0));
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            bandRepository.save(band);
        }
    }

}
