package de.mangalib.service;

import de.mangalib.repository.BandRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Geschätzte Dauer für vorhandene, als gelesen markierte physische Bände.
 * Fehlende Sammelbandangaben werden sichtbar ausgelassen statt historisch ergänzt.
 */
@Service
public class ReadingTimeService {
    public static final int MINUTES_PER_STANDARD_VOLUME = 45;
    private final BandRepository bands;

    public ReadingTimeService(BandRepository bands) { this.bands = bands; }

    public record Estimate(long minutes, long minutesWithRereads, int unknownVolumes,
                           int unknownRereads) {
        public String duration() { return format(minutes); }
        public String durationWithRereads() { return format(minutesWithRereads); }
    }

    @Transactional(readOnly = true)
    public Estimate estimate() { return calculate(bands.readingEntries()); }

    static Estimate calculate(List<BandRepository.ReadingEntry> entries) {
        long minutes = 0, repeated = 0;
        int unknown = 0, unknownRereads = 0;
        for (var entry : entries) {
            Integer multiplier = entry.getMultiplier();
            if (multiplier == null || multiplier <= 0) {
                unknown++;
                continue;
            }
            long base = Math.multiplyExact((long) multiplier, MINUTES_PER_STANDARD_VOLUME);
            minutes = Math.addExact(minutes, base);
            Integer reread = entry.getReread();
            if (reread == null || reread < 0) unknownRereads++;
            long count = reread == null || reread < 0 ? 1L : 1L + reread;
            repeated = Math.addExact(repeated, Math.multiplyExact(base, count));
        }
        return new Estimate(minutes, repeated, unknown, unknownRereads);
    }

    private static String format(long minutes) {
        return minutes / 1440 + " Tage " + minutes % 1440 / 60 + " Std. " + minutes % 60 + " Min.";
    }
}
