package de.mangalib.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reale Query-Erzeugung mit gemocktem EntityManager; kein SQL wird ausgeführt. */
class ReadingHistoryStoreTest {
    private final EntityManager em = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final ReadingHistoryStore store = new ReadingHistoryStore();
    private final UUID operation = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 11, 15);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(store, "em", em);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @Test void mapsNativeDatesAndNullableFactorWithoutInventingValues() {
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(new Object[] {
                4L, 7L, operation.toString(), Date.valueOf(date), Date.valueOf(date.minusDays(1)), "REREAD", 2, null }));
        var event = store.forBand(7).getFirst();
        assertEquals(date, event.date()); assertEquals(date.minusDays(1), event.originalDate());
        assertEquals(operation, event.operationId()); assertEquals(2, event.revision()); assertNull(event.volumeFactor());
        verify(query).setParameter("id", 7L); verify(query, never()).executeUpdate();
    }

    @Test void insertFlushesSamePersistenceContextAndBindsValues() {
        store.insert(7, operation, date, ReadingHistoryStatistics.Kind.FIRST_READ, 3);
        var order = inOrder(em, query); order.verify(em).flush(); order.verify(em).createNativeQuery(startsWith("insert into"));
        verify(query).setParameter("operation", operation.toString()); verify(query).setParameter("band", 7L);
        verify(query).setParameter("date", Date.valueOf(date)); verify(query).setParameter("factor", 3);
        verify(query, times(1)).executeUpdate();
    }

    @Test void correctionUsesBandIdentityAndRevisionAndNeverUpdatesCounters() {
        when(query.executeUpdate()).thenReturn(1, 0);
        assertTrue(store.correct(4, 7, 2, date)); assertFalse(store.correct(4, 7, 2, date));
        verify(query, times(2)).setParameter("band", 7L); verify(query, times(2)).setParameter("revision", 2);
        verify(em, times(2)).createNativeQuery(argThat(sql -> sql.contains("revision = revision + 1")
                && !sql.contains("reread") && !sql.contains("initial_completion_date")));
    }

    @Test void activationFailsForNonTransactionalOrMissingConstraints() {
        when(query.getResultList()).thenReturn(List.of());
        when(query.getSingleResult()).thenReturn("MyISAM", 1L, 1L);
        assertThrows(IllegalStateException.class, store::validateSchema);
        when(query.getSingleResult()).thenReturn("InnoDB", 0L, 1L);
        assertThrows(IllegalStateException.class, store::validateSchema);
        when(query.getSingleResult()).thenReturn("InnoDB", 1L, 0L);
        assertThrows(IllegalStateException.class, store::validateSchema);
    }

    @Test void activationChecksSchemaWithSelectsOnly() {
        when(query.getResultList()).thenReturn(List.of());
        when(query.getSingleResult()).thenReturn("InnoDB", 1L, 1L);
        store.validateSchema();
        verify(query, never()).executeUpdate();
        verify(em, times(4)).createNativeQuery(startsWith("select"));
    }
}
