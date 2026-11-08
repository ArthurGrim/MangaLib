package de.mangalib;

import de.mangalib.entity.MangaReihe;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class T03CartFixtureTest {
    @ParameterizedTest @NullSource @ValueSource(ints={0,2502})
    void preservesKnownIdentityIncludingExplicitNullAndZero(Integer index){
        var row=new MangaReihe();row.setId(51L);row.setTitel("synthetic");row.setMangaIndex(index);
        var item=T03CartFixture.identity(51L,row,()->fail("Kein Ersatztitel"),()->fail("Kein Ersatzindex"));
        assertEquals(index,item.getMangaIndex());assertEquals("synthetic",item.getTitel());
        assertNull(item.getId());assertNull(item.getVerlagId()); // Noch nicht ergänzte optionale Fixturebezüge.
    }
    @Test void optionalSeriesReferenceCreatesExactlyOneNewIdentity(){
        var titleCalls=new AtomicInteger();var indexCalls=new AtomicInteger();
        var item=T03CartFixture.identity(null,null,()->{titleCalls.incrementAndGet();return "synthetic-new";},()->{indexCalls.incrementAndGet();return 900;});
        assertEquals(900,item.getMangaIndex());assertEquals("synthetic-new",item.getTitel());
        assertEquals(1,titleCalls.get());assertEquals(1,indexCalls.get());
    }
    @Test void missingOrConflictingRequestedReferenceNeverBecomesANewSeries(){
        var row=new MangaReihe();row.setId(51L);
        for(Long requested:new Long[]{0L,52L})
            assertThrows(IllegalArgumentException.class,()->T03CartFixture.identity(requested,row,()->fail("Kein Fallback"),()->fail("Kein Fallback")));
        assertThrows(IllegalArgumentException.class,()->T03CartFixture.identity(51L,null,()->fail("Kein Fallback"),()->fail("Kein Fallback")));
        assertThrows(IllegalArgumentException.class,()->T03CartFixture.identity(null,row,()->fail("Kein Fallback"),()->fail("Kein Fallback")));
    }
    @Test void doesNotNormalizeOtherNullableIdentityValues(){
        var row=new MangaReihe();row.setId(51L);row.setMangaIndex(null);row.setTitel(null);
        var item=T03CartFixture.identity(51L,row,()->fail("Kein Fallback"),()->fail("Kein Fallback"));
        assertNull(item.getTitel());assertNull(item.getMangaIndex());
    }
    @Test void historicalMixedPrimitiveExpressionReallyUnboxesNull(){
        var row=new MangaReihe();row.setMangaIndex(null);
        assertThrows(NullPointerException.class,()->{Integer ignored=row==null?900:row.getMangaIndex();});
        assertNull(T03CartFixture.identity(51L,identified(row),()->"unused",()->900).getMangaIndex());
    }
    private MangaReihe identified(MangaReihe row){row.setId(51L);return row;}
}
