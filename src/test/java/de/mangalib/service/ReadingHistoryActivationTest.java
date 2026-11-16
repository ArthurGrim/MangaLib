package de.mangalib.service;

import de.mangalib.repository.BandRepository;
import de.mangalib.repository.MangaReiheRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Kleiner reiner Bean-Kontext ohne Boot, JPA, DataSource oder Listener. */
class ReadingHistoryActivationTest {
    @Test void disabledByDefaultWithoutAnyDatasourceOrStore() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ReadingHistoryService.class, ReadingHistoryStore.class); context.refresh();
            assertTrue(context.getBeansOfType(ReadingHistoryService.class).isEmpty());
            assertTrue(context.getBeansOfType(ReadingHistoryStore.class).isEmpty());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }

    @Test void explicitEnableValidatesStoreBeforeFeatureCanBeUsed() {
        var store = mock(ReadingHistoryStore.class);
        try (var context = context(store)) {
            context.refresh();
            assertNotNull(context.getBean(ReadingHistoryService.class)); verify(store).validateSchema();
            verifyNoMoreInteractions(store);
        }
    }

    @Test void schemaFailureDoesNotFallBackToUnprotectedOrLegacyWrites() {
        var store = mock(ReadingHistoryStore.class);
        doThrow(new IllegalStateException("Synthetisch fehlendes Schema")).when(store).validateSchema();
        try (var context = context(store)) {
            assertThrows(org.springframework.beans.BeansException.class, context::refresh);
            verify(store).validateSchema(); verifyNoMoreInteractions(store);
        }
    }

    private AnnotationConfigApplicationContext context(ReadingHistoryStore store) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("mangalib.reading-history.enabled", "true")));
        // Fertiges Double: keine @PersistenceContext-Verarbeitung des realen Store-Typs.
        context.getBeanFactory().registerSingleton("historyStore", store);
        context.registerBean(BandRepository.class, () -> mock(BandRepository.class));
        context.registerBean(MangaReiheRepository.class, () -> mock(MangaReiheRepository.class));
        context.registerBean(LibraryWriteCoordinator.class, () -> new LibraryWriteCoordinator(new FakeLibraryTransactionManager()));
        context.register(ReadingHistoryService.class);
        return context;
    }
}
