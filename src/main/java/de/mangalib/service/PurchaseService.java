package de.mangalib.service;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.validation.WriteValidation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;

/** Beide Kaufwege: ein Mutex, eine Transaktion, keine Netzwerkarbeit und keine Retries.
 * Erst nach Rückkehr des Coordinators darf ein Controller Erfolg melden. */
@Service
public class PurchaseService {
    public enum Outcome {
        UPDATED, CREATED, ALREADY_PURCHASED, STATUS_REQUIRED, CONFIRM_VOLUMES,
        NOT_FOUND, DETAILS_NOT_FOUND, UNKNOWN_STATUS, AMBIGUOUS_SERIES, INCOMPATIBLE_SERIES,
        BAND_CONFLICT, INVALID_INVENTORY, BUSY
    }
    public record Request(long itemId, Long statusId, Long confirmedSeriesId, Integer firstBand, Integer lastBand) {
        public Request {
            WriteValidation.require(statusId == null || statusId > 0);
            boolean any = confirmedSeriesId != null || firstBand != null || lastBand != null;
            WriteValidation.require(!any || confirmedSeriesId != null && confirmedSeriesId > 0
                    && firstBand != null && lastBand != null && firstBand > 0
                    && lastBand >= firstBand && lastBand <= WriteValidation.MAX_VOLUMES);
        }
    }
    public record Result(Outcome outcome, Long seriesId, Integer firstBand, Integer lastBand) {
        static Result of(Outcome outcome) { return new Result(outcome, null, null, null); }
    }

    private final LibraryWriteCoordinator coordinator;
    private final EinkaufslisteRepository shopping;
    private final EinkaufslisteItemDetailsRepository shoppingDetails;
    private final MangaReiheRepository rows;
    private final BandRepository bands;
    private final MangaReiheService library;

    public PurchaseService(LibraryWriteCoordinator coordinator, EinkaufslisteRepository shopping,
            EinkaufslisteItemDetailsRepository shoppingDetails, MangaReiheRepository rows,
            BandRepository bands, MangaReiheService library) {
        this.coordinator = coordinator; this.shopping = shopping; this.shoppingDetails = shoppingDetails;
        this.rows = rows; this.bands = bands; this.library = library;
    }

    public Result purchase(Request request) {
        if (request.itemId() <= 0) return Result.of(Outcome.NOT_FOUND);
        try { return coordinator.execute(() -> transfer(request)); }
        catch (LibraryWriteCoordinator.WriteBusyException busy) { return Result.of(Outcome.BUSY); }
    }

    private Result transfer(Request request) {
        var optional = shopping.findByIdForPurchase(request.itemId());
        if (optional.isEmpty()) return Result.of(Outcome.NOT_FOUND);
        var item = optional.get();
        if (Boolean.TRUE.equals(item.getGekauft())) return Result.of(Outcome.ALREADY_PURCHASED);
        if (item.getGekauft() == null) return Result.of(Outcome.UNKNOWN_STATUS);
        WriteValidation.title(item.getTitel()); WriteValidation.volumes(item.getAnzahlBaende());
        requireKnownMoney(item.getPreis()); requireKnownMoney(item.getAenderungGesamtpreis());
        WriteValidation.require(item.getVerlagId() != null && item.getVerlagId().getVerlagId() != null
                && item.getTypId() != null && item.getTypId().getTypId() != null
                && item.getFormatId() != null && item.getFormatId().getFormatId() != null);

        // Historische 0 ist weder fehlend noch ein freigegebener positiver externer Index.
        if (item.getMangaIndex() != null && item.getMangaIndex() <= 0) return Result.of(Outcome.AMBIGUOUS_SERIES);
        var indexed = item.getMangaIndex() == null ? List.<MangaReihe>of() : rows.findByMangaIndex(item.getMangaIndex());
        if (indexed.size() > 1) return Result.of(Outcome.AMBIGUOUS_SERIES);
        if (indexed.size() == 1) {
            var candidate = indexed.getFirst();
            if (candidate.getId() == null) return Result.of(Outcome.INVALID_INVENTORY);
            var locked = rows.findByIdForPurchase(candidate.getId());
            if (locked.isEmpty()) return Result.of(Outcome.AMBIGUOUS_SERIES);
            var row = locked.get();
            if (!Objects.equals(row.getMangaIndex(), item.getMangaIndex()) || !compatible(row, item))
                return Result.of(Outcome.INCOMPATIBLE_SERIES);
            return append(request, item, row);
        }
        // Titel nur Kandidatenhinweis, niemals automatische Editionszuordnung.
        if (!rows.findByTitel(item.getTitel()).isEmpty()) return Result.of(Outcome.AMBIGUOUS_SERIES);
        if (request.confirmedSeriesId() != null) return Result.of(Outcome.BAND_CONFLICT);
        if (request.statusId() == null) return Result.of(Outcome.STATUS_REQUIRED);
        var details = shoppingDetails.findById(item.getId());
        if (details.isEmpty()) return Result.of(Outcome.DETAILS_NOT_FOUND);
        var d = details.get();
        if (!Objects.equals(d.getId(), item.getId()) || d.getEinkaufslisteItem() == null
                || !Objects.equals(d.getEinkaufslisteItem().getId(), item.getId()))
            return Result.of(Outcome.INVALID_INVENTORY);
        Map<String, String> imported = new HashMap<>();
        imported.put("Deutsche Ausgabe Status", d.getStatusDe());
        imported.put("Band 1 Bild Url", d.getCoverUrl());
        imported.put("Deutsche Ausgabe Bände", text(d.getAnzahlBaendeDe()));
        imported.put("Erstveröffentlichung Status", d.getStatusErstv());
        imported.put("Erstveröffentlichung Herkunft", d.getHerkunft());
        imported.put("Erstveröffentlichung Startjahr", text(d.getStartJahr()));
        imported.put("Erstveröffentlichung Bände", text(d.getAnzahlBaendeErstv()));
        // Unbekannte Metadaten nicht über String.valueOf(null) als "null" erfinden.
        imported.values().removeIf(Objects::isNull);
        var created = library.saveMangaReihe(item.getMangaIndex(), request.statusId(), item.getVerlagId().getVerlagId(),
                item.getTypId().getTypId(), item.getFormatId().getFormatId(), item.getTitel(), item.getAnzahlBaende(),
                item.getPreis(), item.getIstVergriffen(), item.getIstEbayPreis(), d.getAnilistUrl(),
                d.getSammelbaendeId() == null ? 1L : d.getSammelbaendeId().getId(), item.getAenderungGesamtpreis(), false, imported);
        if (created == null || created.getId() == null) throw new IllegalStateException("Kaufanlage nicht bestätigt.");
        finish(item, created, List.of());
        return Result.of(Outcome.CREATED);
    }

    private Result append(Request request, EinkaufslisteItem item, MangaReihe row) {
        try { WriteValidation.editableRelations(row); }
        catch (IllegalArgumentException invalid) { return Result.of(Outcome.INVALID_INVENTORY); }
        var existing = bands.findByMangaReiheId(row.getId());
        if (!consistent(row, existing) || !Objects.equals(row.getAnzahlBaende(), existing.size()))
            return Result.of(Outcome.INVALID_INVENTORY);
        int first = existing.size() + 1;
        int last = Math.addExact(existing.size(), item.getAnzahlBaende());
        WriteValidation.volumes(last);
        if (request.confirmedSeriesId() == null)
            return new Result(Outcome.CONFIRM_VOLUMES, row.getId(), first, last);
        if (!Objects.equals(request.confirmedSeriesId(), row.getId()) || request.firstBand() != first || request.lastBand() != last)
            return Result.of(Outcome.BAND_CONFLICT);
        // Fehlende konkrete externe Bandindices werden nicht aus dem Reihenindex erfunden.
        var result = library.updateMangaReihe(row.getId(), row.getMangaIndex(), row.getStatus().getStatusId(),
                row.getVerlag().getVerlagId(), row.getTyp().getTypId(), row.getFormat().getFormatId(), row.getTitel(), last,
                item.getPreis(), row.getIstVergriffen(), row.getIstEbayPreis(), row.getMangaDetails().getAnilistUrl(),
                row.getMangaDetails().getCoverUrl(), row.getMangaDetails().getSammelbaende().getId(),
                item.getAenderungGesamtpreis(), false, row.getMangaDetails().getReread(), Map.of("itemId", item.getId().toString()));
        if (result.isEmpty() || !Objects.equals(result.get().getId(), row.getId()))
            throw new IllegalStateException("Kaufänderung nicht bestätigt.");
        finish(item, result.get(), existing);
        return Result.of(Outcome.UPDATED);
    }

    /** Nur die neuen Kaufbände korrigieren; globale Preis-/Einzelbandpfade bleiben T03-3/4.
     * Rechenplan und Summe werden vor Kaufmarkierung validiert; Fehler rollen alles zurück. */
    private void finish(EinkaufslisteItem item, MangaReihe row, List<Band> old) {
        var all = bands.findByMangaReiheId(row.getId());
        if (!consistent(row, all) || !Objects.equals(row.getAnzahlBaende(), all.size()))
            throw new IllegalStateException("Kaufbestand nicht bestätigt.");
        var oldIds = new HashSet<Long>(); old.forEach(b -> oldIds.add(b.getId()));
        var added = all.stream().filter(b -> !oldIds.contains(b.getId())).sorted(Comparator.comparing(Band::getBandNr)).toList();
        if (added.size() != item.getAnzahlBaende()) throw new IllegalStateException("Kaufbände nicht bestätigt.");
        var adjustments = Money.distribute(item.getAenderungGesamtpreis(), added.size());
        for (int i = 0; i < added.size(); i++) {
            var band = added.get(i);
            if (band.getBandNr() != old.size() + i + 1) throw new IllegalStateException("Kaufnummerierung nicht bestätigt.");
            band.setAenderungPreis(adjustments.get(i));
            bands.save(band);
        }
        BigDecimal adjustment = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (var band : all) {
            requireKnownMoney(band.getPreis()); requireKnownMoney(band.getAenderungPreis());
            adjustment = adjustment.add(band.getAenderungPreis()); total = total.add(band.getPreis()).add(band.getAenderungPreis());
        }
        WriteValidation.money(total); WriteValidation.money(adjustment);
        row.setGesamtpreis(total); row.setAenderungGesamtpreis(adjustment);
        row.setPreisProBand(Money.mean(total, all.size()));
        if (adjustment.signum() != 0) row.setIstEbayPreis(true);
        rows.save(row);
        item.setGekauft(true); shopping.save(item);
        shopping.flush(); // Gemeinsamer JPA-Kontext: Bibliothek UND Markierung vor Rückkehr prüfen.
    }

    private static boolean compatible(MangaReihe row, EinkaufslisteItem item) {
        return row.getVerlag() != null && row.getTyp() != null && row.getFormat() != null
                && Objects.equals(row.getVerlag().getVerlagId(), item.getVerlagId().getVerlagId())
                && Objects.equals(row.getTyp().getTypId(), item.getTypId().getTypId())
                && Objects.equals(row.getFormat().getFormatId(), item.getFormatId().getFormatId());
    }
    private static boolean consistent(MangaReihe row, List<Band> volumes) {
        if (volumes.isEmpty()) return false;
        try { BandInventory.indexed(row, volumes); }
        catch (IllegalArgumentException invalid) { return false; }
        return volumes.stream().allMatch(b -> b.getPreis() != null && b.getAenderungPreis() != null);
    }
    private static void requireKnownMoney(BigDecimal value) {
        WriteValidation.require(value != null); WriteValidation.money(value);
        WriteValidation.require(value.stripTrailingZeros().scale() <= 2);
    }
    private static String text(Object value) { return value == null ? null : value.toString(); }
}
