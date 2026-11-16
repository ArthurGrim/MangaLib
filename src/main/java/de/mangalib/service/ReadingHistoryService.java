package de.mangalib.service;

import de.mangalib.entity.Band;
import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.BandRepository;
import de.mangalib.repository.MangaReiheRepository;
import de.mangalib.validation.WriteValidation;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static de.mangalib.service.ReadingHistoryStatistics.Kind;

/** Datierte Leseaktionen unter der bestehenden T03-Commit-/Quarantänegrenze.
 * Aktivierung erst nach Migration; historische Zähler werden niemals still initialisiert.
 */
@Service
@ConditionalOnProperty(name = "mangalib.reading-history.enabled", havingValue = "true")
public class ReadingHistoryService {
    private final ReadingHistoryStore store;
    private final BandRepository bands;
    private final MangaReiheRepository rows;
    private final LibraryWriteCoordinator coordinator;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ReadingHistoryService(ReadingHistoryStore store, BandRepository bands, MangaReiheRepository rows,
                                 LibraryWriteCoordinator coordinator) {
        this(store, bands, rows, coordinator, Clock.systemDefaultZone());
    }

    ReadingHistoryService(ReadingHistoryStore store, BandRepository bands, MangaReiheRepository rows,
                          LibraryWriteCoordinator coordinator, Clock clock) {
        this.store = store; this.bands = bands; this.rows = rows; this.coordinator = coordinator; this.clock = clock;
    }

    @PostConstruct public void validateSchema() { store.validateSchema(); }
    public LocalDate today() { return LocalDate.now(clock); }

    public static class Conflict extends RuntimeException {}
    public static class Missing extends RuntimeException {}

    /** Clientkennung bleibt bei Wiederholung erhalten. Ganze Sammelaktion prüfen, bevor ein Band mutiert. */
    public List<ReadingHistoryStore.Event> complete(List<Long> ids, UUID operation, Kind kind, LocalDate date) {
        validate(ids, operation, kind, date);
        var requestedIds = List.copyOf(ids);
        return coordinator.execute(() -> {
            var previous = store.forOperation(operation);
            if (!previous.isEmpty()) {
                if (previous.size() != requestedIds.size() || !previous.stream().allMatch(e -> requestedIds.contains(e.bandId())
                        && e.originalDate().equals(date) && e.kind() == kind)) throw new Conflict();
                return previous;
            }
            List<Band> selected = requestedIds.stream().map(id -> bands.findById(id).orElseThrow(Missing::new)).toList();
            for (Band band : selected) {
                WriteValidation.editableRelations(band.getMangaReihe());
                var events = store.forBand(band.getId());
                if (kind == Kind.FIRST_READ && events.stream().anyMatch(e -> e.kind() == Kind.FIRST_READ)) throw new Conflict();
                chronological(events, kind, date, -1);
                if (kind == Kind.REREAD && (!band.isIstGelesen() && events.isEmpty()
                        || band.getReread() == null || band.getReread() < 0
                        || band.getReread() >= WriteValidation.MAX_REREAD)) throw new Conflict();
            }
            var affected = new LinkedHashMap<Long, MangaReihe>();
            for (Band band : selected) {
                band.setIstGelesen(true);
                if (kind == Kind.REREAD) band.setReread(band.getReread() + 1);
                bands.save(band);
                store.insert(band.getId(), operation, date, kind, factor(band));
                affected.put(band.getMangaReihe().getId(), band.getMangaReihe());
            }
            for (var row : affected.values()) {
                var inventory = bands.findByMangaReiheId(row.getId());
                row.getMangaDetails().setIstGelesen(!inventory.isEmpty() && inventory.stream().allMatch(Band::isIstGelesen));
                if (kind == Kind.REREAD) row.getMangaDetails().setReread(inventory.stream()
                        .mapToInt(b -> b.getReread() == null ? 0 : b.getReread()).min().orElse(0));
                rows.save(row);
            }
            return store.forOperation(operation);
        });
    }

    /** Automatische Erstlektüre bei einer echten Statusänderung im vorhandenen Editor.
     * Zurücksetzen und erneutes Markieren erzeugen keinen impliziten Reread.
     */
    public void recordTransition(Band band, boolean previouslyRead) {
        requireTransaction();
        if (previouslyRead || !band.isIstGelesen()) return;
        if (store.forBand(band.getId()).isEmpty())
            store.insert(band.getId(), UUID.randomUUID(), today(), Kind.FIRST_READ, factor(band));
    }

    public void requireUnchangedCounter(Integer before, Integer after) {
        if (after != null && !Objects.equals(before, after)) throw new Conflict();
    }

    public ReadingHistoryStore.Event correct(long bandId, long eventId, int revision, LocalDate date) {
        validDate(date);
        WriteValidation.require(bandId > 0 && eventId > 0 && revision >= 0 && revision < Integer.MAX_VALUE);
        return coordinator.execute(() -> {
            var event = store.forBand(bandId).stream().filter(e -> e.id() == eventId).findFirst().orElseThrow(Missing::new);
            if (event.date().equals(date)) return event; // Wiederholung einer bestätigten Datumskorrektur.
            chronological(store.forBand(bandId), event.kind(), date, eventId);
            if (event.revision() != revision || !store.correct(eventId, bandId, revision, date)) throw new Conflict();
            return store.forBand(bandId).stream().filter(e -> e.id() == eventId).findFirst().orElseThrow(Missing::new);
        });
    }

    @Transactional(readOnly = true)
    public List<ReadingHistoryStore.Event> history(long bandId) {
        if (!bands.existsById(bandId)) throw new Missing();
        return store.forBand(bandId);
    }

    public record Month(String month, long firstReads, long rereads, long minutes, long unknownFactors) {}

    @Transactional(readOnly = true)
    public List<Month> months() {
        var events = store.all();
        var counts = ReadingHistoryStatistics.byMonth(events.stream().map(e -> new ReadingHistoryStatistics.Completion(
                e.operationId(), e.bandId(), e.date(), e.kind())).toList());
        var durations = new HashMap<java.time.YearMonth, long[]>();
        var seen = new HashSet<String>();
        for (var event : events) {
            if (!seen.add(event.operationId() + ":" + event.bandId())) continue;
            var duration = durations.computeIfAbsent(java.time.YearMonth.from(event.date()), ignored -> new long[2]);
            if (event.volumeFactor() == null || event.volumeFactor() <= 0) duration[1]++;
            else duration[0] = Math.addExact(duration[0], Math.multiplyExact((long) event.volumeFactor(), ReadingTimeService.MINUTES_PER_STANDARD_VOLUME));
        }
        return counts.entrySet().stream().map(entry -> {
            var duration = durations.get(entry.getKey());
            return new Month(entry.getKey().toString(), entry.getValue().firstReads(), entry.getValue().rereads(), duration[0], duration[1]);
        }).toList();
    }

    private void validate(List<Long> ids, UUID operation, Kind kind, LocalDate date) {
        WriteValidation.require(ids != null && !ids.isEmpty() && ids.size() <= WriteValidation.MAX_VOLUMES
                && ids.stream().allMatch(id -> id != null && id > 0) && new HashSet<>(ids).size() == ids.size()
                && operation != null && kind != null);
        validDate(date);
    }

    /** Bekannte Gesamtsicht plus datierte Ereignisse ohne Doppelzählung.
     * Ein Statusreset entfernt nachgewiesene Lektüren nicht aus der Schätzung.
     * Historische undatierte Anteile erhalten weiterhin keinen Zeitstempel.
     */
    @Transactional(readOnly = true)
    public ReadingTimeService.Estimate readingTime() {
        var byBand = store.all().stream().collect(java.util.stream.Collectors.groupingBy(ReadingHistoryStore.Event::bandId));
        long firstMinutes = 0, repeatedMinutes = 0; int unknown = 0, unknownRereads = 0;
        for (var state : bands.readingStates()) {
            var events = byBand.getOrDefault(state.getId(), List.of());
            if (!Boolean.TRUE.equals(state.getReadState()) && events.isEmpty()
                    && (state.getReread() == null || state.getReread() <= 0)) continue;
            var first = events.stream().filter(e -> e.kind() == Kind.FIRST_READ).findFirst();
            Integer initialFactor = first.isPresent() ? first.get().volumeFactor() : state.getMultiplier();
            boolean factorUnknown = initialFactor == null || initialFactor <= 0;
            long base = factorUnknown ? 0 : Math.multiplyExact((long) initialFactor, ReadingTimeService.MINUTES_PER_STANDARD_VOLUME);
            firstMinutes = Math.addExact(firstMinutes, base); repeatedMinutes = Math.addExact(repeatedMinutes, base);
            long datedRereads = 0;
            for (var event : events) if (event.kind() == Kind.REREAD) {
                datedRereads++;
                if (event.volumeFactor() == null || event.volumeFactor() <= 0) factorUnknown = true;
                else repeatedMinutes = Math.addExact(repeatedMinutes,
                        Math.multiplyExact((long) event.volumeFactor(), ReadingTimeService.MINUTES_PER_STANDARD_VOLUME));
            }
            Integer total = state.getReread();
            if (total == null || total < 0) unknownRereads++;
            else {
                if (total < datedRereads) throw new IllegalStateException("Reread-Zähler widerspricht der Lesehistorie.");
                long undated = total - datedRereads;
                if (undated > 0) {
                    if (state.getMultiplier() == null || state.getMultiplier() <= 0) factorUnknown = true;
                    else repeatedMinutes = Math.addExact(repeatedMinutes, Math.multiplyExact(undated,
                            Math.multiplyExact((long) state.getMultiplier(), ReadingTimeService.MINUTES_PER_STANDARD_VOLUME)));
                }
            }
            if (factorUnknown) unknown++;
        }
        return new ReadingTimeService.Estimate(firstMinutes, repeatedMinutes, unknown, unknownRereads);
    }
    private void validDate(LocalDate date) {
        WriteValidation.require(date != null && date.getYear() >= 1000 && !date.isAfter(today()));
    }
    private void chronological(List<ReadingHistoryStore.Event> events, Kind kind, LocalDate date, long ignoredId) {
        if (events.stream().filter(e -> e.id() != ignoredId).anyMatch(e -> kind == Kind.FIRST_READ
                ? e.kind() == Kind.REREAD && e.date().isBefore(date)
                : e.kind() == Kind.FIRST_READ && e.date().isAfter(date))) throw new Conflict();
    }
    private Integer factor(Band band) {
        var details = band.getMangaReihe().getMangaDetails();
        return details.getSammelbaende() == null ? null : details.getSammelbaende().getMultiplikator();
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Leseaktion benötigt den gemeinsamen Schreibrahmen.");
    }
}
