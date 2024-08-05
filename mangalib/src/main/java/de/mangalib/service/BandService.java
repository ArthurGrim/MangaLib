package de.mangalib.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.mangalib.entity.Band;
import de.mangalib.entity.MangaDetails;
import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.BandRepository;
import de.mangalib.repository.MangaReiheRepository;

import java.util.*;
import java.time.Year;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BandService {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MangaReiheRepository mangaReiheRepository;

    private final BandScraper bandScraper;

    public BandService(BandScraper bandScraper) {
        this.bandScraper = bandScraper;
    }

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
    public void createAndSaveBaende(MangaReihe mangaReihe, Integer anzahlBaende, BigDecimal preisProBand,
            BigDecimal gesamtpreisAenderung, Map<String, String> scrapedData) {
        System.out.println("createAndSaveBaende Aenderung Gesamtpreis: " + gesamtpreisAenderung);
        for (int i = 1; i <= anzahlBaende; i++) {
            Band band = new Band();
            band.setMangaReihe(mangaReihe);
            band.setBandNr(i);

            String bildUrlKey = "Band " + i + " Bild Url";
            String mpUrlKey = "Band " + i + " href";
            String preisKey = "Band " + i + " Preis";

            try {
                // Fuege den Link zum Bild bei den ersten 5 Baenden ein
                if (scrapedData.containsKey(bildUrlKey)) {
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
                        // Extrahiere den Index aus dem Link
                        Pattern pattern = Pattern.compile("volumes/(\\d+)");
                        Matcher matcher = pattern.matcher(mpUrlString);
                        if (matcher.find()) {
                            int bandIndex = Integer.parseInt(matcher.group(1));
                            band.setBandIndex(bandIndex);
                        }

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
                        band.setPreis(preisProBand);
                    }
                } else {
                    band.setPreis(preisProBand);
                }

                // Setzt AenderungGesamtpreis, wenn vorhanden
                if (gesamtpreisAenderung.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal aenderungPreis = gesamtpreisAenderung.divide(BigDecimal.valueOf(anzahlBaende), 2,
                            RoundingMode.HALF_UP);
                    band.setAenderungPreis(aenderungPreis);
                    System.out.println("Änderung Preis: " + aenderungPreis + " für Band: " + i);
                } else {
                    band.setAenderungPreis(new BigDecimal(0));
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            System.out.println("Es wird ein neuer Band für eine neue Reihe erzeugt!");
            bandRepository.save(band);
            System.out.println("Band gespeichert: " + band.getBandNr() + ", ID: " + band.getId());
        }
    }

    public BigDecimal calculateAenderungGesamtpreis(MangaReihe mangaReihe) {
        List<Band> baende = bandRepository.findByMangaReiheId(mangaReihe.getId());
        BigDecimal aenderungGesamtpreis = BigDecimal.ZERO;
        for (Band band : baende) {
            BigDecimal aenderungPreis = band.getAenderungPreis();
            if (aenderungPreis != null) {
                aenderungGesamtpreis = aenderungGesamtpreis.add(aenderungPreis);
            }
        }
        return aenderungGesamtpreis;
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
            gesamtpreis = gesamtpreis.add(band.getPreis());
        }
        gesamtpreis = gesamtpreis.add(mangaReihe.getAenderungGesamtpreis());
        return gesamtpreis;
    }

    /**
     * Aktualisiert oder erstellt die Bände einer MangaReihe.
     * 
     * @param mangaReihe           Die MangaReihe, zu der die Bände gehören.
     * @param anzahlBaende         Die Anzahl der Bände in der Reihe.
     * @param preisProBand         Der Preis pro Band.
     * @param gesamtpreisAenderung Die Änderung des Gesamtpreises.
     * @param scrapedData          Zusätzliche Daten, die durch Web-Scraping
     *                             erhalten wurden.
     * @param anzahlBaendeChanged  Gibt an, ob sich die Anzahl der Bände geändert
     *                             hat.
     */
    public void updateOrCreateBaende(MangaReihe mangaReihe, Integer anzahlBaende, BigDecimal preisProBand,
            BigDecimal gesamtpreisAenderung,
            Map<String, String> scrapedData, boolean anzahlBaendeChanged) {
        List<Band> existierendeBaende = bandRepository.findByMangaReiheId(mangaReihe.getId());
        int existierendeAnzahl = existierendeBaende.size();
        System.out.println("Anzahl existierender Bände: " + existierendeAnzahl);
        System.out.println("Anzahl Bände: " + anzahlBaende);

        for (int i = 1; i <= anzahlBaende; i++) {
            Band band;
            if (i <= existierendeAnzahl) {
                // Aktualisieren eines existierenden Bandes
                band = existierendeBaende.get(i - 1);
                System.out.println("Aktualisiere existierenden Band: " + band.getBandNr());
                // Aktualisieren des Preises, wenn die Anzahl der Bände sich nicht geändert hat
                if (!anzahlBaendeChanged) {
                    if (preisProBand.compareTo(BigDecimal.ZERO) != 0) {
                        band.setPreis(preisProBand);
                    }
                    if (gesamtpreisAenderung.compareTo(BigDecimal.ZERO) != 0) {
                        band.setAenderungPreis(gesamtpreisAenderung.divide(new BigDecimal(anzahlBaende)));
                    }
                }
            } else {
                // Erstellen eines neuen Bandes
                band = new Band();
                band.setMangaReihe(mangaReihe);
                band.setBandNr(i);
                System.out.println("Es wurde ein neuer Band erstellt mit der Nr.:" + i);
                band.setPreis(preisProBand);
            }

            String bildUrlKey = "Band " + i + " Bild Url";
            String mpUrlKey = "Band " + i + " href";
            String preisKey = "Band " + i + " Preis";

            try {
                System.out.println("Hat BildURL Key: " + scrapedData.containsKey(bildUrlKey));
                // Fuege den Link zum Bild bei den ersten 5 Baenden ein
                if (scrapedData.containsKey(bildUrlKey)) {
                    if (band.getBildUrl() == null || !scrapedData.get(bildUrlKey)
                            .equals(band.getBildUrl().toString() != null ? band.getBildUrl().toString() : "")) {
                        String bildUrlString = scrapedData.get(bildUrlKey);
                        if (bildUrlString != null && !bildUrlString.isEmpty()) {
                            URI bildUri = new URI(bildUrlString);
                            URL bildUrl = bildUri.toURL();
                            band.setBildUrl(bildUrl);
                        }
                    }
                }

                // Fuege den Link zur entsprechenden MangaPassion Seite ein
                if (scrapedData.containsKey(mpUrlKey)) {
                    if (band.getMpUrl() == null || !scrapedData.get(mpUrlKey)
                            .equals(band.getMpUrl().toString() != null ? band.getMpUrl().toString() : "")) {
                        String mpUrlString = scrapedData.get(mpUrlKey);
                        System.out
                                .println("MP Url String existiert: " + (mpUrlString != null && !mpUrlString.isEmpty()));
                        if (mpUrlString != null && !mpUrlString.isEmpty()) {
                            // Extrahiere den Index aus dem Link
                            Pattern pattern = Pattern.compile("volumes/(\\d+)");
                            Matcher matcher = pattern.matcher(mpUrlString);
                            if (matcher.find()) {
                                int bandIndex = Integer.parseInt(matcher.group(1));
                                band.setBandIndex(bandIndex);
                                System.out.println("Band Index gesetzt auf: " + bandIndex);
                            }

                            URI mpUri = new URI(mpUrlString);
                            URL mpUrl = mpUri.toURL();
                            band.setMpUrl(mpUrl);
                        }
                    }
                }

                if (i > existierendeAnzahl) {
                    // Fuege den Preis ein, wenn vorhanden
                    if (scrapedData.containsKey(preisKey)) {
                        try {
                            band.setPreis(new BigDecimal(scrapedData.get(preisKey).replace(",", ".")));
                        } catch (NumberFormatException e) {
                            band.setPreis(preisProBand);
                            System.out.println("Fehler beim Setzen des Preises für Band " + i);
                            e.printStackTrace();
                        }
                    } else {
                        band.setPreis(preisProBand);
                        System.out.println("Setze Standardpreis: " + preisProBand);
                    }
                }

                if (i > existierendeAnzahl) {
                    // Setzt AenderungGesamtpreis, wenn vorhanden
                    System.out.println("GesamtpreisAenderung: " + gesamtpreisAenderung + " größer als 0: "
                            + (gesamtpreisAenderung.compareTo(BigDecimal.ZERO) != 0));
                    if (gesamtpreisAenderung.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal aenderungPreis = gesamtpreisAenderung
                                .divide(BigDecimal.valueOf(anzahlBaende - existierendeAnzahl), 2, RoundingMode.HALF_UP);
                        band.setAenderungPreis(aenderungPreis);
                        System.out.println("Änderung Preis: " + aenderungPreis + " für Band: " + i);
                    } else {
                        band.setAenderungPreis(new BigDecimal(0));
                    }
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            bandRepository.save(band);
            System.out.println("Band gespeichert: " + band.getBandNr() + ", ID: " + band.getId());
        }
    }

    /**
     * Gibt eien Liste an Baenden zu einer Mangareihe zurück
     * 
     * @param mangaReiheId ID der Mangareihe zu welcher die Baende gefunden werden
     *                     sollen
     * @return Liste der Baende zu einer Mangareihe
     */
    public List<Band> findByMangaReiheId(Long mangaReiheId) {
        if (mangaReiheId == null) {
            throw new IllegalArgumentException("Ungültige ID");
        }
        return bandRepository.findByMangaReiheId(mangaReiheId);
    }

    /**
     * Aktualisiert einen Band und passt den Preis und den Status der zugehörigen
     * MangaDetails an.
     * 
     * @param bandData Der zu aktualisierende Band.
     */
    public void updateBand(Band bandData) {
        Optional<Band> optionalBand = bandRepository.findById(bandData.getId());
        if (optionalBand.isPresent()) {
            Band band = optionalBand.get();

            boolean preisChanged = false;

            // Aktualisiere die Attribute des Bands
            if (bandData.getBandNr() != null) {
                if (!bandData.getBandNr().equals(band.getBandNr())) {
                    band.setBandNr(bandData.getBandNr());
                }
            }
            if (bandData.getBandIndex() != null) {
                if (!bandData.getBandIndex().equals(band.getBandIndex())) {
                    band.setBandIndex(bandData.getBandIndex());
                }
            }
            if (bandData.getPreis() != null) {
                if (!bandData.getPreis().equals(band.getPreis())) {
                    preisChanged = true;
                    band.setPreis(bandData.getPreis());
                }
            }
            if (bandData.getAenderungPreis() != null) {
                if (!bandData.getAenderungPreis().equals(band.getAenderungPreis())) {
                    preisChanged = true;
                    band.setAenderungPreis(bandData.getAenderungPreis());
                    System.out.println("AenderungPreis: " + bandData.getAenderungPreis());
                }
            }
            if (bandData.getBildUrl() != null) {
                if (bandData.getBildUrl() != band.getBildUrl()) {
                    band.setBildUrl(bandData.getBildUrl());
                }
            }
            if (bandData.getMpUrl() != null) {
                if (bandData.getMpUrl() != band.getMpUrl()) {
                    band.setMpUrl(bandData.getMpUrl());
                }
            }
            if (bandData.isIstGelesen() != band.isIstGelesen()) {
                band.setIstGelesen(bandData.isIstGelesen());
            }
            if (bandData.getReread() != null) {
                if (bandData.getReread() != band.getReread()) {
                    band.setReread(bandData.getReread());
                }
            }
            if (bandData.getIstSpecial() != band.getIstSpecial()) {
                band.setIstSpecial(bandData.getIstSpecial());
            }

            bandRepository.save(band);

            // Berechne und speichere die neuen Gesamtpreise
            MangaReihe mangaReihe = band.getMangaReihe();
            System.out.println("Alte Aenderung Gesamtpreis: " + mangaReihe.getAenderungGesamtpreis());
            BigDecimal neueAenderungGesamtpreis = calculateAenderungGesamtpreis(mangaReihe);
            System.out.println("Neue Aenderung Gesamtpreis: " + neueAenderungGesamtpreis);
            mangaReihe.setAenderungGesamtpreis(neueAenderungGesamtpreis);
            System.out.println("Aenderung Gesamtpreis gesetzt auf: " + mangaReihe.getAenderungGesamtpreis());
            System.out.println("Alter Gesamtpreis: " + mangaReihe.getGesamtpreis());
            BigDecimal neuerGesamtpreis = calculateGesamtpreis(mangaReihe);
            System.out.println("Neuer Gesamtpreis: " + neuerGesamtpreis);
            mangaReihe.setGesamtpreis(neuerGesamtpreis);
            System.out.println("Gesamtpreis gesetzt auf: " + mangaReihe.getGesamtpreis());

            // Nur aktualisieren, wenn sich die Anzahl der Bände geändert hat
            if (preisChanged) {
                System.out.println("Preis Pro Band wird neu berechnet");
                mangaReihe.setPreisProBand(
                        neuerGesamtpreis.divide(BigDecimal.valueOf(mangaReihe.getAnzahlBaende()), 2,
                                RoundingMode.HALF_UP));
            }

            if (neueAenderungGesamtpreis.compareTo(BigDecimal.ZERO) != 0) {
                mangaReihe.setIstEbayPreis(true);
            }

            // Überprüfen und aktualisieren des istGelesen-Status der MangaDetails
            updateMangaDetailsIstGelesenStatus(mangaReihe.getMangaDetails());

            // Speichere die aktualisierte MangaReihe
            mangaReiheRepository.save(mangaReihe);
        }
    }

    /**
     * Überprüft den Status aller Bände einer MangaDetails und aktualisiert
     * entsprechend den istGelesen-Status.
     * 
     * @param mangaDetails Die MangaDetails, deren istGelesen-Status überprüft und
     *                     aktualisiert werden soll.
     */
    private void updateMangaDetailsIstGelesenStatus(MangaDetails mangaDetails) {
        List<Band> baende = bandRepository.findByMangaReiheId(mangaDetails.getMangaReihe().getId());
        boolean alleBaendeGelesen = baende.stream().allMatch(Band::isIstGelesen);

        if (alleBaendeGelesen && !mangaDetails.isIstGelesen()) {
            mangaDetails.setIstGelesen(true);
        } else if (!alleBaendeGelesen && mangaDetails.isIstGelesen()) {
            mangaDetails.setIstGelesen(false);
        }
    }

    // Wenn der MP Autofill Button gedrückt wird
    public Map<String, String> autofillBandData(String bandIndex) {
        Map<String, String> scrapedData = bandScraper.scrapeBandData(bandIndex);
        Map<String, String> relevantData = new HashMap<>();

        if (scrapedData.containsKey("BildUrl")) {
            relevantData.put("BildUrl", scrapedData.get("BildUrl"));
        }
        if (scrapedData.containsKey("Preis")) {
            String preis = scrapedData.get("Preis");
            if (preis.contains("€")) {
                preis = preis.replace("€", "").trim();
            }
            relevantData.put("Preis", preis);
        }
        if (!scrapedData.isEmpty()) {
            relevantData.put("mpUrl", "https://www.manga-passion.de/volumes/" + bandIndex);
        }

        return relevantData;
    }

}
