package de.mangalib.service;

import de.mangalib.repository.EinkaufslisteRepository;
import de.mangalib.security.LocalSecurityConfiguration;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reale Advisor-Konfiguration und realer Listener-Guard-BPP, aber ausschließlich
 * Fake-Transaktionen/Repositories. Kein Boot, DataSource, Listener oder TestContext. */
class LibraryWriteBootstrapTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void earlyGuardCreationKeepsWriterAdviceAndTransactionCompletion(boolean rollback) {
        var tx = new FakeLibraryTransactionManager();
        var fixtures = new T03Fixtures.Library();
        var cart = mock(EinkaufslisteRepository.class);
        var item = T03Fixtures.item(false);
        when(cart.findById(7L)).thenReturn(Optional.of(item));
        if (rollback) when(cart.save(any())).thenThrow(new IllegalStateException("synthetic save failure"));
        else when(cart.save(any())).thenAnswer(call -> call.getArgument(0));

        try (var context = new AnnotationConfigApplicationContext()) {
            // Fertige Doubles für Feldinjektion; keine weiteren Service-/Repositorygraphen erzeugen.
            for (Class<?> dependency : List.of(StatusService.class, VerlagService.class, TypService.class,
                    FormatService.class, MangaDetailsService.class, BandService.class,
                    SammelbandService.class, EinkaufslisteItemDetailsService.class))
                context.getBeanFactory().registerSingleton(dependency.getSimpleName(), mock(dependency));
            context.register(LibraryWriteConfiguration.class);
            context.registerBean("transactionManager", PlatformTransactionManager.class, () -> tx);
            context.registerBean(LibraryWriteCoordinator.class, () -> new LibraryWriteCoordinator(tx));
            // Exakte produktive statische Factory, ohne SecurityFilterChain/HTTP zu erzeugen.
            var guard = new RootBeanDefinition(LocalSecurityConfiguration.class);
            guard.setFactoryMethodName("localWebServerFactoryGuard");
            guard.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            context.registerBeanDefinition("localWebServerFactoryGuard", guard);
            context.registerBean(MangaReiheService.class, () -> fixtures.series);
            context.registerBean(EinkaufslisteService.class, () -> new EinkaufslisteService(cart));
            context.refresh();

            var series = context.getBean(MangaReiheService.class);
            var shopping = context.getBean(EinkaufslisteService.class);
            assertTrue(AopUtils.isAopProxy(series));
            assertTrue(AopUtils.isAopProxy(shopping));
            var advice = Arrays.asList(((Advised) shopping).getAdvisors());
            assertTrue(advice.indexOf(context.getBean("libraryWriteAdvisor")) >= 0);
            assertTrue(advice.indexOf(context.getBean("libraryWriteAdvisor"))
                    < advice.indexOf(context.getBean("org.springframework.transaction.config.internalTransactionAdvisor")));

            series.updateMangaReiheTitel(42L, "Synthetic metadata");
            assertEquals(1, tx.begins.get()); assertEquals(1, tx.commits.get());
            if (rollback) assertThrows(IllegalStateException.class, () -> shopping.updateGekauft(7, true));
            else shopping.updateGekauft(7, true);
            assertEquals(2, tx.begins.get()); // Kein zweiter äußerer @Transactional-Rahmen.
            assertEquals(rollback ? 1 : 2, tx.commits.get());
            assertEquals(rollback ? 1 : 0, tx.rollbacks.get());
            assertEquals("cleanup", tx.events.getLast());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }
}
