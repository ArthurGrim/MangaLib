package de.mangalib.service;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

/** Ausschließlich synthetische Objekte und Repository-Mocks, keine Spring-/JPA-Infrastruktur. */
final class T03Fixtures {
    static MangaReihe row(long id) {
        var row = new MangaReihe();
        row.setId(id); row.setMangaIndex(123); row.setTitel("T03 synthetische Reihe");
        row.setAnzahlBaende(3); row.setPreisProBand(new BigDecimal("6.00"));
        row.setGesamtpreis(new BigDecimal("18.00")); row.setAenderungGesamtpreis(BigDecimal.ZERO);
        var status = new Status(); status.setStatusId(1L); row.setStatus(status);
        var publisher = new Verlag(); publisher.setVerlagId(1L); row.setVerlag(publisher);
        var type = new Typ(); type.setTypId(1L); row.setTyp(type);
        var format = new Format(); format.setFormatId(1L); row.setFormat(format);
        var details = new MangaDetails(); details.setId(id); details.setMangaReihe(row);
        details.setReread(4); details.setIstGelesen(true);
        var omnibus = new Sammelband(); omnibus.setId(1L); details.setSammelbaende(omnibus);
        row.setMangaDetails(details);
        return row;
    }

    static EinkaufslisteItem item(Boolean bought) {
        var item = new EinkaufslisteItem(); var row = row(42);
        item.setId(7L); item.setMangaIndex(123); item.setTitel(row.getTitel());
        item.setVerlagId(row.getVerlag()); item.setTypId(row.getTyp()); item.setFormatId(row.getFormat());
        item.setAnzahlBaende(1); item.setPreis(new BigDecimal("6.00"));
        item.setAenderungGesamtpreis(BigDecimal.ZERO); item.setGekauft(bought);
        return item;
    }

    static Band band(MangaReihe row, int number) {
        var band = new Band(); band.setId(100L + number); band.setBandNr(number);
        band.setMangaReihe(row); band.setPreis(new BigDecimal("6.00"));
        band.setAenderungPreis(BigDecimal.ZERO); band.setReread(number == 1 ? null : number);
        band.setIstGelesen(number != 2); band.setIstSpecial(number == 1 ? null : true);
        band.setBildUrl(null); // Der Konstruktor-URL wird niemals gefolgt.
        return band;
    }

    /** Identitätsmap in RAM. Mutationen/Saves sind ausdrücklich kein Commit-/Rollbacknachweis. */
    static final class Library {
        final MangaReihe row = row(42);
        final List<Band> stored = new ArrayList<>();
        final BandRepository bands = mock(BandRepository.class);
        final MangaReiheRepository rows = mock(MangaReiheRepository.class);
        final MangaDetailsRepository details = mock(MangaDetailsRepository.class);
        final StatusRepository statuses = mock(StatusRepository.class);
        final FormatRepository formats = mock(FormatRepository.class);
        final TypRepository types = mock(TypRepository.class);
        final VerlagRepository publishers = mock(VerlagRepository.class);
        final BandService bandService = new BandService();
        final MangaDetailsService detailService = new MangaDetailsService();
        final MangaReiheService series = new MangaReiheService(rows, statuses, formats, types, publishers, bands, details);

        Library() {
            for (int i = 1; i <= 3; i++) stored.add(band(row, i));
            ReflectionTestUtils.setField(bandService, "bandRepository", bands);
            ReflectionTestUtils.setField(bandService, "mangaReiheRepository", rows);
            ReflectionTestUtils.setField(bandService, "mangaDetailsService", detailService);
            ReflectionTestUtils.setField(detailService, "mangaDetailsRepository", details);
            ReflectionTestUtils.setField(series, "bandService", bandService);
            ReflectionTestUtils.setField(series, "mangaDetailsService", detailService);
            var statusService = mock(StatusService.class);
            var publisherService = mock(VerlagService.class);
            var typeService = mock(TypService.class);
            var formatService = mock(FormatService.class);
            when(statusService.getStatusById(1L)).thenReturn(Optional.of(row.getStatus()));
            when(publisherService.getVerlagById(1L)).thenReturn(Optional.of(row.getVerlag()));
            when(typeService.getTypById(1L)).thenReturn(Optional.of(row.getTyp()));
            when(formatService.getFormatById(1L)).thenReturn(Optional.of(row.getFormat()));
            ReflectionTestUtils.setField(series, "statusService", statusService);
            ReflectionTestUtils.setField(series, "verlagService", publisherService);
            ReflectionTestUtils.setField(series, "typService", typeService);
            ReflectionTestUtils.setField(series, "formatService", formatService);
            var scraper = mock(ScraperGateway.class, call -> { throw new AssertionError("Kein Scraper erlaubt"); });
            ReflectionTestUtils.setField(bandService, "scraperGateway", scraper);
            when(rows.findById(42L)).thenReturn(Optional.of(row));
            when(rows.save(any())).thenAnswer(i -> i.getArgument(0));
            when(details.save(any())).thenAnswer(i -> i.getArgument(0));
            when(bands.findByMangaReiheId(42L)).thenAnswer(i -> new ArrayList<>(stored));
            when(bands.findById(anyLong())).thenAnswer(i -> stored.stream()
                    .filter(b -> Objects.equals(b.getId(), i.getArgument(0))).findFirst());
            when(bands.save(any())).thenAnswer(i -> {
                Band b = i.getArgument(0);
                if (b.getId() == null) { b.setId(1000L + stored.size()); stored.add(b); }
                return b;
            });
        }

        Optional<MangaReihe> edit(int target, BigDecimal price, BigDecimal adjustment, Map<String, String> imported) {
            var currentDetails = row.getMangaDetails();
            return series.updateMangaReihe(42L, row.getMangaIndex(), 1L, 1L, 1L, 1L,
                    row.getTitel(), target, price, row.getIstVergriffen(), row.getIstEbayPreis(),
                    null, null, 1L, adjustment, currentDetails != null && currentDetails.isIstGelesen(),
                    currentDetails == null ? null : currentDetails.getReread(), imported);
        }

        Map<Long, List<Object>> state() {
            var state = new TreeMap<Long, List<Object>>();
            for (Band b : stored) state.put(b.getId(), Arrays.asList(b.getBandNr(), b.getMangaReihe(),
                    b.getPreis(), b.getAenderungPreis(), b.isIstGelesen(), b.getReread(), b.getIstSpecial(), b.getBandIndex()));
            return state;
        }
    }
}
