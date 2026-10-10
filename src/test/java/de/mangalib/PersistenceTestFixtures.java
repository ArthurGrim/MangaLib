package de.mangalib;

import de.mangalib.entity.*;
import jakarta.persistence.EntityManager;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Kleine Testaggregate; Stammdaten werden ausschließlich referenziert, niemals angelegt oder geändert. */
final class PersistenceTestFixtures {
    private PersistenceTestFixtures() {
    }

    record Graph(MangaReihe series, MangaDetails seriesDetails, List<Band> volumes,
            EinkaufslisteItem shoppingItem, EinkaufslisteItemDetails shoppingDetails) {
    }

    static Graph persistGraph(EntityManager entityManager) throws Exception {
        assertTrue(TestTransaction.isActive(), "Testdaten benötigen eine Spring-Testtransaktion.");
        assertTrue(TestTransaction.isFlaggedForRollback(), "Testdaten dürfen nicht committed werden.");
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        verifyTransactionalTestDatabase(entityManager);

        var status = first(entityManager, Status.class, "statusId");
        var publisher = first(entityManager, Verlag.class, "verlagId");
        var type = first(entityManager, Typ.class, "typId");
        var format = first(entityManager, Format.class, "formatId");
        var collection = first(entityManager, Sammelband.class, "id");
        String title = "P0 Persistenz äöü " + UUID.randomUUID();

        var series = new MangaReihe();
        series.setTitel(title);
        series.setMangaIndex(12345);
        series.setStatus(status);
        series.setVerlag(publisher);
        series.setTyp(type);
        series.setFormat(format);
        series.setAnzahlBaende(2);
        series.setPreisProBand(new BigDecimal("5.99"));
        series.setGesamtpreis(new BigDecimal("12.34"));
        series.setAenderungGesamtpreis(new BigDecimal("0.36"));
        series.setIstEbayPreis(false);
        series.setIstVergriffen(null);
        entityManager.persist(series);

        var details = new MangaDetails();
        details.setMangaReihe(series);
        details.setSammelbaende(collection);
        details.setStatusDe("Laufend");
        details.setStatusErstv("Abgeschlossen");
        details.setHerkunft("Japan");
        details.setStartJahr(2020);
        details.setAnzahlBaendeDe(2);
        details.setAnzahlBaendeErstv(3);
        details.setAnilistUrl("https://example.invalid/series/12345");
        details.setCoverUrl("https://example.invalid/cover.jpg");
        details.setIstGelesen(false);
        details.setReread(2);
        // @MapsId soll die Eltern-ID übernehmen; keine manuell gesetzte Detail-ID.
        entityManager.persist(details);
        series.setMangaDetails(details);

        var firstVolume = volume(series, 1, "5.99", "0.00");
        var secondVolume = volume(series, 2, "6.35", "0.36");
        entityManager.persist(firstVolume);
        entityManager.persist(secondVolume);

        var item = new EinkaufslisteItem();
        item.setTitel(title + " Einkauf");
        item.setMangaIndex(12345);
        item.setVerlagId(publisher);
        item.setTypId(type);
        item.setFormatId(format);
        item.setAnzahlBaende(2);
        item.setPreis(new BigDecimal("7.49"));
        item.setGesamtpreis(new BigDecimal("14.98"));
        item.setAenderungGesamtpreis(new BigDecimal("-0.02"));
        item.setErscheinungsdatum(LocalDate.of(2024, 2, 29));
        item.setIstEbayPreis(null);
        item.setIstVergriffen(false);
        item.setGekauft(false);
        entityManager.persist(item);

        var itemDetails = new EinkaufslisteItemDetails();
        itemDetails.setEinkaufslisteItem(item);
        itemDetails.setSammelbaendeId(collection);
        itemDetails.setStatusDe("Laufend");
        itemDetails.setStatusErstv("Abgeschlossen");
        itemDetails.setHerkunft("Japan");
        itemDetails.setStartJahr(2021);
        itemDetails.setAnzahlBaendeDe(2);
        itemDetails.setAnzahlBaendeErstv(4);
        itemDetails.setAnilistUrl("https://example.invalid/shopping/12345");
        itemDetails.setCoverUrl("https://example.invalid/shopping.jpg");
        entityManager.persist(itemDetails);
        item.setEinkaufslisteItemDetails(itemDetails);
        return new Graph(series, details, List.of(firstVolume, secondVolume), item, itemDetails);
    }

    private static Band volume(MangaReihe series, int number, String price, String adjustment) throws Exception {
        var volume = new Band();
        volume.setMangaReihe(series);
        volume.setBandNr(number);
        volume.setBandIndex(12345 + number);
        volume.setPreis(new BigDecimal(price));
        volume.setAenderungPreis(new BigDecimal(adjustment));
        volume.setBildUrl(url("https://example.invalid/cover-" + number + ".jpg?size=large"));
        volume.setMpUrl(url("https://example.invalid/volumes/" + number));
        volume.setIstSpecial(null);
        volume.setIstGelesen(number == 1);
        volume.setReread(number == 1 ? 2 : 0);
        return volume;
    }

    static URL url(String value) throws Exception {
        // Nur parsen; URL.equals kann DNS auflösen und wird in Assertions deshalb vermieden.
        return URI.create(value).toURL();
    }

    private static <T> T first(EntityManager entityManager, Class<T> type, String id) {
        return entityManager.createQuery("select e from " + type.getSimpleName() + " e order by e." + id, type)
                .setMaxResults(1).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Fehlende Test-Stammdaten: " + type.getSimpleName()));
    }

    private static void verifyTransactionalTestDatabase(EntityManager entityManager) {
        // Ausschließlich lesende Metadaten über denselben geschützten EntityManager.
        assertEquals("mangalib_test", entityManager.createNativeQuery("select database()").getSingleResult());
        assertEquals("mangalib_test_user", entityManager.createNativeQuery(
                "select substring_index(current_user(), '@', 1)").getSingleResult());
        for (String table : List.of("mangareihe", "mangadetails", "baende", "einkaufsliste", "einkaufslistedetails")) {
            var engine = entityManager.createNativeQuery("""
                    select ENGINE from information_schema.TABLES
                    where TABLE_SCHEMA = database() and TABLE_NAME = :table
                    """).setParameter("table", table).getSingleResult();
            assertEquals("InnoDB", engine, "Rollback-fähige Tabelle erforderlich: " + table);
        }
    }
}
