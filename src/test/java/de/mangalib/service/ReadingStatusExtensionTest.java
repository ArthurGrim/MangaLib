package de.mangalib.service;

import de.mangalib.controller.EinkaufslisteController;
import de.mangalib.controller.MainController;
import de.mangalib.entity.*;
import de.mangalib.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Echte Controller und drei echte Services; ausschließlich Repository-Mocks, kein Spring-/DB-Start.
 * Die Mock-Identitätsmap ersetzt kein Flush/Clear oder einen transaktionalen Integrationstest.
 */
class ReadingStatusExtensionTest {
    private MangaReiheRepository rows;
    private BandRepository bands;
    private MangaDetailsRepository detailsRepository;
    private MangaReiheService service;
    private BandService bandService;
    private MangaReihe row;
    private MangaDetails details;
    private List<Band> stored;
    private MainController main;
    private EinkaufslisteController cart;
    private EinkaufslisteRepository shoppingRepository;
    private EinkaufslisteService shopping;

    @BeforeEach void setup() throws Exception {
        rows = mock(MangaReiheRepository.class); bands = mock(BandRepository.class);
        detailsRepository = mock(MangaDetailsRepository.class);
        service = new MangaReiheService(rows, mock(StatusRepository.class), mock(FormatRepository.class),
                mock(TypRepository.class), mock(VerlagRepository.class), bands, detailsRepository);
        var detailService = new MangaDetailsService();
        ReflectionTestUtils.setField(detailService, "mangaDetailsRepository", detailsRepository);
        bandService = new BandService();
        ReflectionTestUtils.setField(bandService, "bandRepository", bands);
        ReflectionTestUtils.setField(bandService, "mangaReiheRepository", rows);
        ReflectionTestUtils.setField(bandService, "mangaDetailsService", detailService);
        ReflectionTestUtils.setField(service, "bandService", bandService);
        ReflectionTestUtils.setField(service, "mangaDetailsService", detailService);
        row = new MangaReihe(); row.setId(42L); row.setMangaIndex(123); row.setTitel("M06 Altbestand");
        row.setAnzahlBaende(2); row.setPreisProBand(new BigDecimal("6.99"));
        row.setGesamtpreis(new BigDecimal("13.98")); row.setAenderungGesamtpreis(BigDecimal.ZERO);
        var status = new Status(); status.setStatusId(1L); row.setStatus(status);
        var publisher = new Verlag(); publisher.setVerlagId(1L); row.setVerlag(publisher);
        var type = new Typ(); type.setTypId(1L); row.setTyp(type);
        var format = new Format(); format.setFormatId(1L); row.setFormat(format);
        details = new MangaDetails(); details.setId(42L); details.setMangaReihe(row); row.setMangaDetails(details);
        details.setReread(3); details.setHerkunft("Japan"); details.setStartJahr(2000);
        var omnibus = new Sammelband(); omnibus.setId(1L); details.setSammelbaende(omnibus);
        stored = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            var band = new Band(); band.setId(10L + i); band.setBandNr(i); band.setBandIndex(800 + i);
            band.setMangaReihe(row); band.setPreis(new BigDecimal("6.99"));
            band.setAenderungPreis(new BigDecimal(i == 1 ? "-0.02" : "0.02"));
            band.setReread(i == 1 ? null : 5); band.setIstSpecial(i == 1 ? null : true);
            band.setBildUrl(URI.create("https://example.invalid/old/" + i).toURL());
            band.setMpUrl(URI.create("https://example.invalid/volumes/" + i).toURL());
            band.setErstelltAm(LocalDate.of(2020, 1, i)); band.setAktualisiertAm(LocalDate.of(2021, 1, i));
            stored.add(band);
        }
        when(rows.findById(42L)).thenReturn(Optional.of(row));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(detailsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Absichtlich wechselnde, unsortierte Ergebnisse: keine Positionszuordnung zulassen.
        when(bands.findByMangaReiheId(42L)).thenAnswer(invocation -> {
            var result = new ArrayList<>(stored); Collections.reverse(result); return result;
        });
        when(bands.save(any())).thenAnswer(invocation -> {
            Band band = invocation.getArgument(0);
            if (band.getId() == null) { band.setId(100L + stored.size()); stored.add(band); }
            return band;
        });
        main = new MainController(); ReflectionTestUtils.setField(main, "mangaReiheService", service);
        shoppingRepository = mock(EinkaufslisteRepository.class);
        shopping = new EinkaufslisteService(shoppingRepository);
        cart = new EinkaufslisteController();
        ReflectionTestUtils.setField(cart, "mangaReiheService", service);
        ReflectionTestUtils.setField(cart, "einkaufslisteService", shopping);
    }

    private Map<Long, List<Object>> oldState() {
        Map<Long, List<Object>> result = new HashMap<>();
        for (Band b : stored) result.put(b.getId(), Arrays.asList(b.getBandNr(), b.getBandIndex(), b.getMangaReihe(),
                b.isIstGelesen(), b.getReread(), b.getPreis(), b.getAenderungPreis(), b.getBildUrl().toExternalForm(),
                b.getMpUrl() == null ? null : b.getMpUrl().toExternalForm(), b.getIstSpecial(), b.getErstelltAm(), b.getAktualisiertAm()));
        return result;
    }

    private void assertOldState(Map<Long, List<Object>> before) {
        var after = oldState();
        before.forEach((id, values) -> {
            assertEquals(values, after.get(id), "Altband " + id);
            verify(bands, never()).save(argThat(b -> b != null && Objects.equals(id, b.getId())));
        });
    }

    private Map<String, Object> request(int target) {
        var data = new HashMap<String, Object>();
        data.put("mangaReiheId", "42"); data.put("mangaIndex", 123);
        data.put("statusId", "1"); data.put("verlagId", "1"); data.put("typId", "1"); data.put("formatId", "1");
        data.put("titel", "M06 geändert 漫画"); data.put("anzahlBaende", target);
        data.put("preisProBand", "7.49"); data.put("gesamtpreisAenderung", "-0.02");
        data.put("istGelesen", !details.isIstGelesen()); data.put("reread", 9);
        data.put("anilistUrl", "http://localhost:18080/metadata");
        data.put("scrapedData", Map.of("istEdit", "true", "Band 1 Bild Url", "https://example.invalid/replacement",
                "Band 1 href", "https://example.invalid/volumes/999", "Band 1 Preis", "99.99"));
        return data;
    }

    @ParameterizedTest @CsvSource({"true,true,true", "true,false,false", "false,false,false", "false,true,true", "false,false,true"})
    void directExtensionPreservesEveryOldValueAndAcceptsSeriesMetadata(boolean first, boolean second, boolean rowRead) {
        stored.get(0).setIstGelesen(first); stored.get(1).setIstGelesen(second); details.setIstGelesen(rowRead);
        var before = oldState();
        assertEquals(200, main.addMangaReihe(request(3)).getStatusCode().value());
        assertOldState(before);
        assertEquals(3, stored.size()); assertEquals(3, stored.get(2).getBandNr()); assertFalse(stored.get(2).isIstGelesen());
        assertSame(row, stored.get(2).getMangaReihe()); assertEquals(3, details.getReread());
        assertFalse(details.isIstGelesen()); assertEquals("M06 geändert 漫画", row.getTitel());
        assertEquals("http://localhost:18080/metadata", details.getAnilistUrl());
        assertEquals("Japan", details.getHerkunft()); assertEquals(2000, details.getStartJahr());
        assertEquals(3, row.getAnzahlBaende());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3, 10_000})
    void seriesRereadIsPreservedIncludingUnknown(Integer reread) {
        details.setReread(reread); var before = oldState();
        assertEquals(200, main.addMangaReihe(request(4)).getStatusCode().value());
        assertOldState(before); assertEquals(reread, details.getReread());
        assertEquals(4, stored.size()); assertTrue(stored.subList(2, 4).stream().noneMatch(Band::isIstGelesen));
        assertTrue(stored.subList(2, 4).stream().allMatch(b -> b.getReread() == null));
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 1, 3, 5})
    void unambiguousActualPrefixCanGrowDespiteDeclaredCount(Integer declared) {
        row.setAnzahlBaende(declared); var before = oldState();
        assertEquals(200, main.addMangaReihe(request(4)).getStatusCode().value());
        assertOldState(before); assertEquals(4, row.getAnzahlBaende()); assertEquals(4, stored.size());
        assertEquals(List.of(1, 2, 3, 4), stored.stream().map(Band::getBandNr).sorted().toList());
    }

    @ParameterizedTest @ValueSource(strings = {"gap", "duplicateNumber", "nullNumber", "zeroNumber", "duplicateId", "nullId", "foreignParent", "nullParent", "missingDetails", "foreignDetails", "foreignDetailsId", "noActualAddition"})
    void ambiguousExtensionIsRejectedBeforeAnyMutation(String fault) {
        switch (fault) {
            case "gap" -> stored.get(1).setBandNr(4);
            case "duplicateNumber" -> stored.get(1).setBandNr(1);
            case "nullNumber" -> stored.get(1).setBandNr(null);
            case "zeroNumber" -> stored.get(1).setBandNr(0);
            case "duplicateId" -> stored.get(1).setId(stored.get(0).getId());
            case "nullId" -> stored.get(1).setId(null);
            case "foreignParent" -> { var other = new MangaReihe(); other.setId(99L); stored.get(1).setMangaReihe(other); }
            case "nullParent" -> stored.get(1).setMangaReihe(null);
            case "missingDetails" -> row.setMangaDetails(null);
            case "foreignDetails" -> { var other = new MangaReihe(); other.setId(99L); details.setMangaReihe(other); }
            case "foreignDetailsId" -> details.setId(99L);
            case "noActualAddition" -> row.setAnzahlBaende(1);
            default -> throw new AssertionError(fault);
        }
        var before = oldState(); int target = fault.equals("noActualAddition") ? 2 : 3;
        var payload = request(target);
        assertEquals(400, main.addMangaReihe(payload).getStatusCode().value());
        assertEquals(before, oldState()); assertEquals("M06 Altbestand", row.getTitel());
        assertEquals(3, details.getReread()); verify(rows, never()).save(any());
        verify(detailsRepository, never()).save(any()); verify(bands, never()).save(any());
    }

    @ParameterizedTest @CsvSource({"true,true,true", "true,true,false", "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
    void bothPurchaseMatchesUseRealServicesAndPreserveOldBands(boolean first, boolean second, boolean indexMatch) {
        stored.get(0).setIstGelesen(first); stored.get(1).setIstGelesen(second);
        details.setIstGelesen(first && second); var before = oldState();
        var item = new EinkaufslisteItem(); item.setId(77L); item.setMangaIndex(123); item.setTitel(row.getTitel());
        item.setAnzahlBaende(1); item.setPreis(new BigDecimal("8.49")); item.setAenderungGesamtpreis(BigDecimal.ZERO);
        when(shoppingRepository.findById(77L)).thenReturn(Optional.of(item));
        when(rows.findByMangaIndex(123)).thenReturn(indexMatch ? List.of(row) : List.of());
        when(rows.findByTitel(item.getTitel())).thenReturn(List.of(row));
        var response = cart.buyItemUpdate(Map.of("itemId", "77"));
        assertEquals(200, response.getStatusCode().value()); assertEquals(Map.of("message", "Reihe aktualisiert"), response.getBody());
        assertOldState(before); assertFalse(details.isIstGelesen()); assertEquals(3, details.getReread());
        assertEquals(3, stored.size()); assertFalse(stored.get(2).isIstGelesen()); assertTrue(item.getGekauft());
        verify(shoppingRepository).save(item);
        if (indexMatch) verify(rows, never()).findByTitel(any()); else verify(rows).findByTitel(item.getTitel());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void purchaseWithMissingDetailsIsControlledAndDoesNotMarkBought(boolean indexMatch) {
        row.setMangaDetails(null);
        var item = new EinkaufslisteItem(); item.setId(77L); item.setMangaIndex(123); item.setTitel(row.getTitel());
        when(shoppingRepository.findById(77L)).thenReturn(Optional.of(item));
        when(rows.findByMangaIndex(123)).thenReturn(indexMatch ? List.of(row) : List.of());
        when(rows.findByTitel(item.getTitel())).thenReturn(List.of(row));
        assertEquals(400, cart.buyItemUpdate(Map.of("itemId", "77")).getStatusCode().value());
        verify(shoppingRepository, never()).save(any()); verify(rows, never()).save(any()); verify(bands, never()).save(any());
        verify(detailsRepository, never()).save(any()); assertFalse(item.getGekauft());
    }

    @Test void aggregateUsesAllActualBandsWithoutChangingThem() {
        stored.forEach(b -> b.setIstGelesen(true)); details.setIstGelesen(false);
        bandService.updateMangaDetailsIstGelesenStatus(details); assertTrue(details.isIstGelesen());
        stored.get(1).setIstGelesen(false); var before = oldState();
        bandService.updateMangaDetailsIstGelesenStatus(details); assertFalse(details.isIstGelesen());
        assertEquals(before, oldState()); verify(bands, never()).save(any()); verifyNoInteractions(rows, detailsRepository);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void normalEditWithoutAdditionalVolumesKeepsExistingExplicitBulkBehavior(boolean read) {
        var input = request(2); input.put("istGelesen", read); input.put("reread", 3);
        assertEquals(200, main.addMangaReihe(input).getStatusCode().value());
        assertEquals(2, stored.size()); assertEquals(read, details.isIstGelesen());
        assertTrue(stored.stream().allMatch(b -> b.isIstGelesen() == read));
        assertEquals(3, stored.get(0).getReread()); assertEquals(5, stored.get(1).getReread());
    }

    private void prepareCreation() {
        stored.clear();
        var statusService = mock(StatusService.class); var publisherService = mock(VerlagService.class);
        var typeService = mock(TypService.class); var formatService = mock(FormatService.class);
        var omnibusService = mock(SammelbandService.class);
        when(statusService.getStatusById(1L)).thenReturn(Optional.of(row.getStatus()));
        when(publisherService.getVerlagById(1L)).thenReturn(Optional.of(row.getVerlag()));
        when(typeService.getTypById(1L)).thenReturn(Optional.of(row.getTyp()));
        when(formatService.getFormatById(1L)).thenReturn(Optional.of(row.getFormat()));
        when(omnibusService.findById(1L)).thenReturn(Optional.of(details.getSammelbaende()));
        ReflectionTestUtils.setField(service, "statusService", statusService);
        ReflectionTestUtils.setField(service, "verlagService", publisherService);
        ReflectionTestUtils.setField(service, "typService", typeService);
        ReflectionTestUtils.setField(service, "formatService", formatService);
        var detailService = new MangaDetailsService();
        ReflectionTestUtils.setField(detailService, "mangaDetailsRepository", detailsRepository);
        ReflectionTestUtils.setField(detailService, "sammelbaendeService", omnibusService);
        ReflectionTestUtils.setField(service, "mangaDetailsService", detailService);
        var statuses = (StatusRepository) ReflectionTestUtils.getField(service, "statusRepository");
        var publishers = (VerlagRepository) ReflectionTestUtils.getField(service, "verlagRepository");
        var types = (TypRepository) ReflectionTestUtils.getField(service, "typRepository");
        var formats = (FormatRepository) ReflectionTestUtils.getField(service, "formatRepository");
        when(statuses.findById(1L)).thenReturn(Optional.of(row.getStatus()));
        when(publishers.findById(1L)).thenReturn(Optional.of(row.getVerlag()));
        when(types.findById(1L)).thenReturn(Optional.of(row.getTyp()));
        when(formats.findById(1L)).thenReturn(Optional.of(row.getFormat()));
        when(rows.save(any())).thenAnswer(invocation -> {
            MangaReihe saved = invocation.getArgument(0); if (saved.getId() == null) saved.setId(42L); return saved;
        });
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void newSeriesRetainsItsExistingExplicitReadSelection(boolean read) {
        prepareCreation();
        var result = service.saveMangaReihe(null, 1L, 1L, 1L, 1L, "Neuanlage", 2,
                new BigDecimal("6.99"), null, null, null, 1L, BigDecimal.ZERO, read, 3, Map.of());
        assertEquals(2, stored.size()); assertTrue(stored.stream().allMatch(b -> b.isIstGelesen() == read));
        assertTrue(stored.stream().allMatch(b -> b.getMangaReihe() == result));
        verify(detailsRepository).save(argThat(d -> d.getReread() == 3 && d.isIstGelesen() == read));
    }

    @Test void purchaseOfNewSeriesKeeps417And200ContractsAndStartsUnread() {
        prepareCreation();
        var item = new EinkaufslisteItem(); item.setId(77L); item.setTitel("Neue Kaufreihe"); item.setMangaIndex(123);
        item.setVerlagId(row.getVerlag()); item.setTypId(row.getTyp()); item.setFormatId(row.getFormat());
        item.setAnzahlBaende(2); item.setPreis(new BigDecimal("6.99")); item.setAenderungGesamtpreis(BigDecimal.ZERO);
        when(shoppingRepository.findById(77L)).thenReturn(Optional.of(item));
        var itemDetails = new EinkaufslisteItemDetails(); itemDetails.setId(77L);
        var detailService = mock(EinkaufslisteItemDetailsService.class);
        when(detailService.findItemDetailsById(77L)).thenReturn(Optional.of(itemDetails));
        ReflectionTestUtils.setField(cart, "einkaufslisteItemDetailsService", detailService);
        var pending = cart.buyItemUpdate(Map.of("itemId", "77"));
        assertEquals(417, pending.getStatusCode().value());
        assertEquals(Map.of("message", "Bitte Status für neue Reihe auswählen"), pending.getBody());
        var result = cart.buyItemCreate(Map.of("itemId", "77", "statusId", "1"));
        assertEquals(200, result.getStatusCode().value());
        assertEquals(Map.of("message", "Neue MangaReihe erfolgreich gespeichert"), result.getBody());
        assertEquals(2, stored.size()); assertTrue(stored.stream().noneMatch(Band::isIstGelesen));
        assertTrue(item.getGekauft());
    }
}
