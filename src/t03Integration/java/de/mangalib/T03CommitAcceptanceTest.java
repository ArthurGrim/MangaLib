package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.service.*;
import de.mangalib.repository.*;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ausschließlich im getrennten Opt-in-Source-Set. Kein @Transactional und kein Cleanup.
 * Diese erste reale Stufe ersetzt noch nicht sämtliche historischen HTTP-Schreibtests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers = T03CommitAcceptanceTest.Approval.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class T03CommitAcceptanceTest {
    public static final class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("t03.approved"))) throw new IllegalStateException("Keine Commit-Testfreigabe.");
            UUID.fromString(System.getProperty("t03.run.id"));
            if (!Files.isDirectory(Path.of(System.getProperty("t03.evidence.directory"))))
                throw new IllegalStateException("Reservierter Testlauf fehlt.");
        }
    }
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @MockitoSpyBean PlatformTransactionManager manager;
    @Autowired ApplicationContext context;
    @Autowired LibraryWriteCoordinator coordinator;
    @Autowired MangaReiheService library;
    @Autowired PurchaseService purchases;
    @Autowired BandRepository bands;
    @MockitoBean ScraperGateway scrapers;
    private T03RunJournal journal;
    private TestDatabaseSnapshot.Snapshot before;
    private UUID run;
    private int index;
    private boolean stopped;
    private boolean bodyFailed;
    private T03PurchaseOverlap overlap;
    @RegisterExtension final OutcomeObserver outcome = new OutcomeObserver();
    final class OutcomeObserver implements TestWatcher, TestExecutionExceptionHandler {
        @Override public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
            bodyFailed = true; throw failure;
        }
        @Override public void testSuccessful(ExtensionContext context) {
            stopped = false;
        }
        @Override public void testFailed(ExtensionContext context, Throwable failure) {
            stopped = true; if (journal != null) journal.phase(false);
        }
    }

    @BeforeAll void prepare() throws Exception {
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Äußere Testtransaktion: STOP");
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(), "Äußere Synchronisation: STOP");
        assertEquals(1, context.getBeansOfType(LibraryWriteCoordinator.class).size(), "Mehrere Schreibkoordinatoren: STOP");
        assertSame(coordinator, context.getBean(LibraryWriteCoordinator.class));
        var coordinatorTx = (TransactionTemplate) org.springframework.test.util.ReflectionTestUtils.getField(coordinator, "transaction");
        assertSame(manager, coordinatorTx.getTransactionManager(), "Managerbeobachtung nicht verdrahtet: STOP");
        var writeAdvisor = context.getBean("libraryWriteAdvisor");
        var txAdvisor = context.getBean("org.springframework.transaction.config.internalTransactionAdvisor");
        var txAttributes = new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
        for (Class<?> writer : List.of(MangaReiheService.class, BandService.class, MangaDetailsService.class,
                EinkaufslisteService.class, EinkaufslisteItemDetailsService.class)) {
            var proxy = assertInstanceOf(org.springframework.aop.framework.Advised.class, context.getBean(writer),
                    "Schreibservice ohne prüfbaren Proxy: STOP");
            var advice = Arrays.asList(proxy.getAdvisors());
            int write = advice.indexOf(writeAdvisor), tx = advice.indexOf(txAdvisor);
            assertTrue(write >= 0 && (tx < 0 || write < tx), "Schreibadvisor fehlt oder liegt innerhalb der Transaktion: STOP");
            if (Arrays.stream(writer.getMethods()).anyMatch(method -> txAttributes.getTransactionAttribute(method, writer) != null))
                assertTrue(tx >= 0, "Deklarierter Transaktionsadvisor fehlt: STOP");
        }
        run = UUID.fromString(System.getProperty("t03.run.id"));
        journal = new T03RunJournal(run, Path.of(System.getProperty("t03.evidence.directory")));
        read(() -> {
            @SuppressWarnings("unchecked")
            List<String> grants = em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();
            T03ServerRights.verify(grants); return null;
        });
        before = read(() -> TestDatabaseSnapshot.capture(em));
        T03ReadOnlyPolicy.verify(before, T03ReadOnlyPolicy.EXPECTED_FINGERPRINT);
        index = 1_000_000 + Math.floorMod(run.hashCode(), 1_000_000_000);
        for (int candidate : List.of(index, index + 1))
            assertEquals(0L, read(() -> em.createQuery("select count(r) from MangaReihe r where r.mangaIndex = :index", Long.class)
                    .setParameter("index", candidate).getSingleResult()), "Indexkollision: STOP, kein automatischer Ersatzlauf");
    }
    @BeforeEach void ready() { assertFalse(stopped, "Vorherige Stufe nicht bestätigt: STOP"); stopped = true; bodyFailed = false; }
    @AfterEach void preserve(TestInfo info) {
        stopped = true;
        assertTrue(overlap == null || overlap.completionConfirmed(), "Abschluss unbestätigt: keine weiteren Datenbankabfragen");
        T03Preservation.verify(before, read(() -> TestDatabaseSnapshot.capture(em)), journal.ids());
        verifyNoInteractions(scrapers);
        journal.phase(!bodyFailed); // IO-Fehler hier sind echte Testfehler, nicht nur Watcher-Warnungen.
    }
    @AfterAll void close() throws Exception { if (journal != null) journal.close(); }

    @Test @Order(1) void committedSeriesReloadsExactMoneyAndOwnedIds() {
        long id = create("series", index);
        read(() -> {
            var row = em.find(MangaReihe.class, id);
            assertEquals(3, row.getAnzahlBaende());
            assertEquals(0, new BigDecimal("18.02").compareTo(row.getGesamtpreis()));
            var values = bands.findByMangaReiheId(id).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
            assertEquals(List.of(new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.00")),
                    values.stream().map(Band::getAenderungPreis).toList());
            return null;
        });
    }
    @Test @Order(2) void failureAfterBandsRollsBackOwnNewRows() {
        var attempted = new ArrayList<Long>();
        assertThrows(SyntheticFailure.class, () -> coordinator.execute(() -> {
            attempted.add(create("rollback", null));
            throw new SyntheticFailure();
        }));
        assertEquals(1, attempted.size());
        read(() -> { assertNull(em.find(MangaReihe.class, attempted.getFirst()));
            assertNull(em.find(MangaDetails.class, attempted.getFirst()));
            assertTrue(bands.findByMangaReiheId(attempted.getFirst()).isEmpty()); return null; });
    }
    @Test @Order(3) void sameConfirmedPurchaseInTwoThreadsAppendsOnlyOnce() throws Exception {
        long rowId = create("purchase", index + 1);
        coordinator.execute(() -> {
            var old = bands.findByMangaReiheId(rowId).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
            assertEquals(3, old.size());
            var prices = List.of("5.01", "6.02", "6.97");
            var adjustments = List.of("0.03", "-0.01", "0.00");
            for (int i = 0; i < old.size(); i++) {
                var b = old.get(i);
                b.setBandIndex(index + 100 + i);
                b.setPreis(new BigDecimal(prices.get(i))); b.setAenderungPreis(new BigDecimal(adjustments.get(i)));
                b.setIstGelesen(i != 1); b.setReread(i == 0 ? null : i == 1 ? 0 : 3);
                b.setIstSpecial(i == 0 ? null : i == 2);
                b.setBildUrl(i == 1 ? null : java.net.URI.create("https://mangalib.invalid/cover-" + i + ".png").toURL());
                b.setMpUrl(i == 2 ? null : java.net.URI.create("https://mangalib.invalid/band-" + i).toURL());
                b.setErstelltAm(java.time.LocalDate.of(2020, 2, i + 1));
            }
            em.flush(); return null;
        });
        var oldBands = read(() -> T03BandSnapshot.capture(bands.findByMangaReiheId(rowId)));
        long itemId = coordinator.execute(() -> {
            var row = em.find(MangaReihe.class, rowId);
            var item = new EinkaufslisteItem(); item.setTitel(row.getTitel()); item.setMangaIndex(index + 1);
            item.setVerlagId(row.getVerlag()); item.setTypId(row.getTyp()); item.setFormatId(row.getFormat());
            item.setAnzahlBaende(1); item.setPreis(new BigDecimal("0.00"));
            item.setAenderungGesamtpreis(new BigDecimal("-0.01")); item.setGesamtpreis(new BigDecimal("-0.01"));
            item.setGekauft(false); em.persist(item); em.flush(); journal.register("einkaufsliste", item.getId());
            return item.getId();
        });
        var request = new PurchaseService.Request(itemId, null, rowId, 4, 4);
        overlap = new T03PurchaseOverlap(manager, coordinator);
        try {
            assertEquals(List.of(PurchaseService.Outcome.UPDATED, PurchaseService.Outcome.ALREADY_PURCHASED),
                    overlap.run(() -> purchases.purchase(request).outcome()));
        } finally {
            assertTrue(overlap.completionConfirmed(), "Thread-/Transaktionsabschluss unbestätigt: STOP");
            // Auch nach Future-/Outcomefehlern die beobachtbaren IDs erfassen; niemals
            // vor bestätigtem Threadende lesen. ID-Versuch ist kein Commitnachweis.
            read(() -> { bands.findByMangaReiheId(rowId).forEach(b -> journal.register("baende", b.getId())); return null; });
        }
        read(() -> {
            var volumes = bands.findByMangaReiheId(rowId);
            assertEquals(4, volumes.size());
            T03BandSnapshot.verifyFourthOnly(oldBands, T03BandSnapshot.capture(volumes), rowId);
            volumes.forEach(b -> journal.register("baende", b.getId()));
            assertTrue(em.find(EinkaufslisteItem.class, itemId).getGekauft());
            assertEquals(0, new BigDecimal("18.01").compareTo(em.find(MangaReihe.class, rowId).getGesamtpreis()));
            return null;
        });
    }

    private long create(String suffix, Integer mangaIndex) {
        return coordinator.execute(() -> {
            var status = em.createQuery("from Status order by statusId", Status.class).setMaxResults(1).getSingleResult();
            var publisher = em.createQuery("from Verlag order by verlagId", Verlag.class).setMaxResults(1).getSingleResult();
            var type = em.createQuery("from Typ order by typId", Typ.class).setMaxResults(1).getSingleResult();
            var format = em.createQuery("from Format order by formatId", Format.class).setMaxResults(1).getSingleResult();
            assertNotNull(em.find(Sammelband.class, 1L));
            var row = library.saveMangaReihe(mangaIndex, status.getStatusId(), publisher.getVerlagId(), type.getTypId(), format.getFormatId(),
                    "T03_6B_" + run + "_" + suffix, 3, new BigDecimal("6.00"), false, false, null, 1L,
                    new BigDecimal("0.02"), false, Map.of());
            em.flush();
            journal.register("mangareihe", row.getId()); journal.register("mangadetails", row.getId());
            bands.findByMangaReiheId(row.getId()).forEach(b -> journal.register("baende", b.getId()));
            return row.getId();
        });
    }
    private <T> T read(java.util.function.Supplier<T> work) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var tx = new TransactionTemplate(manager); tx.setReadOnly(true); tx.setTimeout(15);
        return tx.execute(status -> { em.clear(); return work.get(); });
    }
    private static final class SyntheticFailure extends RuntimeException { }
}
