package de.mangalib.service;

import de.mangalib.repository.BandRepository;
import de.mangalib.repository.MangaReiheRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gruppierte, rein lesende Auswertungen ohne Laden von Entity-Beziehungsgraphen. */
@Service
public class StatisticsDistributionService {
    private final MangaReiheRepository series;
    private final BandRepository volumes;

    public StatisticsDistributionService(MangaReiheRepository series, BandRepository volumes) {
        this.series = series;
        this.volumes = volumes;
    }

    public record Distribution(String title, String unit, Map<String, Long> counts) {}

    @Transactional(readOnly = true)
    public List<Distribution> overview() {
        return List.of(
                text("Typ", series.statisticsByType()),
                text("Format", series.statisticsByFormat()),
                text("Status", series.statisticsByStatus()),
                text("Herkunft", series.statisticsByOrigin()),
                new Distribution("Lesestatus", "Bände", grouped(volumes.statisticsByReadState(),
                        value -> boolLabel(value, "Gelesen", "Ungelesen"))),
                new Distribution("Vergriffenheit", "Reihen", grouped(series.statisticsByOutOfPrint(),
                        value -> boolLabel(value, "Vergriffen", "Nicht vergriffen"))));
    }

    private static Distribution text(String title, List<Object[]> rows) {
        return new Distribution(title, "Reihen", grouped(rows, value ->
                value instanceof String label && !label.isBlank() ? label.strip() : "Unbekannt"));
    }

    @Transactional(readOnly = true)
    public List<Distribution> details() {
        return List.of(text("Verlage", series.statisticsByPublisher()),
                text("Formate", series.statisticsByFormat()),
                new Distribution("Reihen-Startjahr", "Reihen", grouped(series.statisticsByStartYear(),
                        value -> value instanceof Number year && year.intValue() > 0
                                ? year.toString() : "Unbekannt")),
                new Distribution("Spezialbände", "Bände", grouped(volumes.statisticsBySpecialState(),
                        value -> boolLabel(value, "Spezialband", "Regulärer Band"))));
    }

    private static String boolLabel(Object value, String yes, String no) {
        return value instanceof Boolean state ? (state ? yes : no) : "Unbekannt";
    }

    private static Map<String, Long> grouped(List<Object[]> rows, Function<Object, String> label) {
        var counts = new TreeMap<String, Long>();
        for (Object[] row : rows) {
            counts.merge(label.apply(row[0]), ((Number) row[1]).longValue(), Math::addExact);
        }
        return new LinkedHashMap<>(counts);
    }
}
