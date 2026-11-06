package de.mangalib;

import de.mangalib.entity.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class T03BandSnapshotTest {
    private List<Band> volumes() throws Exception {
        var row = new MangaReihe(); row.setId(51L);
        var bands = new ArrayList<Band>();
        for (int i = 1; i <= 4; i++) {
            var b = new Band(); b.setId(100L + i); b.setMangaReihe(row); b.setBandNr(i); b.setBandIndex(200+i);
            b.setPreis(new BigDecimal(i + ".01")); b.setAenderungPreis(new BigDecimal("-0.01"));
            b.setReread(i == 1 ? null : i-2); b.setIstSpecial(i == 1 ? null : i == 2); b.setIstGelesen(i == 1);
            b.setBildUrl(URI.create("https://mangalib.invalid/cover-"+i).toURL()); b.setMpUrl(null);
            b.setErstelltAm(LocalDate.of(2020, 1, i)); b.setAktualisiertAm(LocalDate.of(2021, 1, i));
            bands.add(b);
        }
        return bands;
    }
    @Test void acceptsOnlyFourthAndIgnoresRepositoryOrderWithImmutableBeforeValues() throws Exception {
        var bands=volumes(); var before=T03BandSnapshot.capture(bands.subList(0,3));
        Collections.reverse(bands);
        T03BandSnapshot.verifyFourthOnly(before,T03BandSnapshot.capture(bands),51);
    }
    @ParameterizedTest @ValueSource(strings={"id","mangaReihe","bandNr","bandIndex","preis","aenderungPreis",
            "istGelesen","reread","istSpecial","bildUrl","mpUrl","erstelltAm","aktualisiertAm"})
    void detectsChangesToEveryPersistedFieldWithoutRawValues(String field) throws Exception {
        var bands=volumes(); var before=T03BandSnapshot.capture(bands.subList(0,3)); var b=bands.getFirst();
        Object replacement=switch(field) {
            case "id" -> 999L;
            case "mangaReihe" -> { var r=new MangaReihe(); r.setId(99L); yield r; }
            case "bandNr", "bandIndex", "reread" -> 99;
            case "preis", "aenderungPreis" -> new BigDecimal("0.02");
            case "istGelesen" -> false;
            case "istSpecial" -> false;
            case "bildUrl", "mpUrl" -> URI.create("https://mangalib.invalid/changed").toURL();
            default -> LocalDate.of(2025,1,1);
        };
        ReflectionTestUtils.setField(b,field,replacement);
        assertThrows(AssertionError.class,()->T03BandSnapshot.verifyFourthOnly(before,T03BandSnapshot.capture(bands),51));
    }
    @Test void rejectsReplacementDeletionDuplicatesAndWrongNewNumber() throws Exception {
        var bands=volumes(); var before=T03BandSnapshot.capture(bands.subList(0,3));
        assertThrows(AssertionError.class,()->T03BandSnapshot.verifyFourthOnly(before,T03BandSnapshot.capture(bands.subList(0,3)),51));
        bands.getLast().setBandNr(5);
        assertThrows(AssertionError.class,()->T03BandSnapshot.verifyFourthOnly(before,T03BandSnapshot.capture(bands),51));
        bands.getLast().setId(bands.getFirst().getId());
        assertThrows(AssertionError.class,()->T03BandSnapshot.capture(bands));
    }
    @Test void fieldInventoryFailsIfEntityGainsAnUncoveredPersistentField() {
        var fields=new TreeSet<String>();
        for(var f:Band.class.getDeclaredFields()) if(!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                && !f.isAnnotationPresent(jakarta.persistence.Transient.class)) fields.add(f.getName());
        assertEquals(Set.of("id","mangaReihe","bandNr","bandIndex","preis","bildUrl","mpUrl","istSpecial",
                "aenderungPreis","erstelltAm","aktualisiertAm","istGelesen","reread"),fields);
    }
}
