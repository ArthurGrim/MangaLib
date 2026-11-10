package de.mangalib.service;

import de.mangalib.entity.Band;
import de.mangalib.entity.MangaReihe;
import de.mangalib.validation.WriteValidation;
import java.util.*;

/** Prüft gespeicherte Identitäten und liefert eine von Repositoryreihenfolgen
 * unabhängige Nummernzuordnung. Repariert oder mutiert keine Altbestände. */
final class BandInventory {
    private BandInventory() { }

    enum Operation { UNCHANGED, CORRECTION, EXTENSION }

    /** Fehlende Absicht bleibt nur bei eindeutigem Wachstum gegenüber beiden
     * Anzahlen kompatibel. Eine Teilkorrektur darf keine versteckte Anlage sein. */
    static Operation operation(MangaReihe row, List<Band> bands, int target, Boolean extend) {
        WriteValidation.volumes(target);
        int actual = indexed(row, bands).size();
        WriteValidation.require(actual > 0 && target >= actual);
        if (Objects.equals(row.getAnzahlBaende(), target) && !Boolean.TRUE.equals(extend)) return Operation.UNCHANGED;
        if (target == actual) {
            WriteValidation.require(!Boolean.TRUE.equals(extend));
            return Objects.equals(row.getAnzahlBaende(), target) ? Operation.UNCHANGED : Operation.CORRECTION;
        }
        WriteValidation.require(Boolean.TRUE.equals(extend) || extend == null
                && row.getAnzahlBaende() != null && target > row.getAnzahlBaende());
        return Operation.EXTENSION;
    }

    static NavigableMap<Integer, Band> indexed(MangaReihe row, List<Band> bands) {
        var result = byPublicationNumber(row, bands);
        // Automatische Erweiterungen benötigen weiterhin einen lückenlosen Bestand.
        for (int number = 1; number <= bands.size(); number++) {
            WriteValidation.require(result.containsKey(number));
        }
        return result;
    }

    static NavigableMap<Integer, Band> byPublicationNumber(MangaReihe row, List<Band> bands) {
        WriteValidation.require(row != null && row.getId() != null && bands != null);
        var ids = new HashSet<Long>();
        var result = new TreeMap<Integer, Band>();
        for (Band band : bands) {
            WriteValidation.require(band != null && band.getId() != null && band.getId() > 0
                    && ids.add(band.getId()) && band.getBandNr() != null && band.getBandNr() > 0
                    && band.getMangaReihe() != null
                    && Objects.equals(row.getId(), band.getMangaReihe().getId()));
            WriteValidation.require(result.putIfAbsent(band.getBandNr(), band) == null);
        }
        return result;
    }
}
