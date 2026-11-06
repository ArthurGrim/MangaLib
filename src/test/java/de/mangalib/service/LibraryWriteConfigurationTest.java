package de.mangalib.service;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Echte Spring-Bean-Proxys und Jakarta-Transaktionsberatung; keine Boot-Autokonfiguration,
 * DataSource oder TestContext. Die Transaktionen sind ausschließlich lokale Fakes. */
class LibraryWriteConfigurationTest {
    GenericApplicationContext context(LibraryWriteCoordinator coordinator, FakeLibraryTransactionManager tx, Object... targets) {
        var context = new GenericApplicationContext();
        context.registerBean(InfrastructureAdvisorAutoProxyCreator.class, () -> {
            var creator = new InfrastructureAdvisorAutoProxyCreator(); creator.setProxyTargetClass(true); return creator;
        });
        context.registerBean("writeAdvisor", org.springframework.aop.support.DefaultPointcutAdvisor.class,
                () -> new LibraryWriteConfiguration().libraryWriteAdvisor(coordinator), bd -> bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE));
        var source = new AnnotationTransactionAttributeSource();
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(tx); advice.setTransactionAttributeSource(source);
        context.registerBean("txAdvisor", BeanFactoryTransactionAttributeSourceAdvisor.class, () -> {
            var advisor = new BeanFactoryTransactionAttributeSourceAdvisor(); advisor.setTransactionAttributeSource(source);
            advisor.setAdvice(advice); return advisor;
        }, bd -> bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE));
        for (int i = 0; i < targets.length; i++) {
            Object target = targets[i]; context.registerBean("target" + i, Object.class, () -> target);
        }
        context.refresh(); return context;
    }

    @Test void actualInfrastructureProxiesCoordinateNormalWritesAndTransactionalCalls() {
        var f = new T03Fixtures.Library(); var tx = new FakeLibraryTransactionManager();
        var coordinator = new LibraryWriteCoordinator(tx);
        var cart = mock(EinkaufslisteRepository.class); var item = T03Fixtures.item(false);
        when(cart.findById(7L)).thenReturn(Optional.of(item));
        when(cart.save(any())).thenAnswer(i -> i.getArgument(0));
        try (var context = context(coordinator, tx, f.series, new EinkaufslisteService(cart))) {
            var rows = (MangaReiheService) context.getBean("target0");
            var shopping = (EinkaufslisteService) context.getBean("target1");
            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(rows));
            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(shopping));
            rows.updateMangaReiheTitel(42L, "Metadaten");
            assertEquals(1, tx.commits.get());
            shopping.updateGekauft(7, true); // Jakarta @Transactional muss INNERHALB liegen.
            assertEquals(2, tx.begins.get()); assertEquals(2, tx.commits.get());
            assertTrue(item.getGekauft());
            coordinator.execute(() -> rows.saveMangaReihe(f.row));
            assertEquals(3, tx.begins.get()); assertEquals(3, tx.commits.get());
        }
    }

    @Test void writerInventoryIncludesAllFiveServicesButNoReadOrScraperMethods() throws Exception {
        for (Class<?> type : List.of(MangaReiheService.class, BandService.class, MangaDetailsService.class,
                EinkaufslisteService.class, EinkaufslisteItemDetailsService.class)) {
            assertTrue(Arrays.stream(type.getMethods()).anyMatch(m -> LibraryWriteConfiguration.coordinates(m, type)));
            for (var method : type.getMethods()) {
                if (method.getName().startsWith("find") || method.getName().startsWith("get")
                        || method.getName().toLowerCase().contains("autofill"))
                    assertFalse(LibraryWriteConfiguration.coordinates(method, type), method.toString());
            }
        }
        for (var method : ScraperGateway.class.getMethods())
            assertFalse(LibraryWriteConfiguration.coordinates(method, ScraperGateway.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ordinarySeriesCreationCannotPassPurchaseCommitOrRollback(boolean rollback) throws Exception {
        var f = new T03Fixtures.Library(); var tx = new FakeLibraryTransactionManager();
        var coordinator = new LibraryWriteCoordinator(tx);
        var completion = new CountDownLatch(1); var release = new CountDownLatch(1);
        Runnable block = () -> { completion.countDown(); LibraryWriteCoordinatorTest.await(release); };
        if (rollback) tx.beforeRollback = block; else tx.beforeCommit = block;
        var pool = Executors.newFixedThreadPool(2);
        try (var context = context(coordinator, tx, f.series)) {
            var rows = (MangaReiheService) context.getBean("target0");
            var first = pool.submit(() -> {
                try { coordinator.execute(() -> { if (rollback) throw new IllegalStateException("synthetic"); return 1; }); }
                catch (IllegalStateException failure) { if (!rollback) throw failure; }
            });
            assertTrue(completion.await(5, TimeUnit.SECONDS));
            var atWriter = new CountDownLatch(1);
            var second = pool.submit(() -> { atWriter.countDown(); return rows.saveMangaReihe(f.row); });
            assertTrue(atWriter.await(5, TimeUnit.SECONDS));
            assertEquals(1, tx.begins.get()); verify(f.rows, never()).save(any());
            release.countDown(); first.get(5, TimeUnit.SECONDS); assertSame(f.row, second.get(5, TimeUnit.SECONDS));
            assertEquals(2, tx.begins.get()); verify(f.rows).save(f.row);
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @ParameterizedTest @ValueSource(strings = {"index", "sameEdition", "otherEdition", "existing"})
    void normalCreationRejectsIdentityDuplicatesWithoutMergingOrChangingExistingRows(String variant) {
        var f = new T03Fixtures.Library(); var candidate = T03Fixtures.row(99); candidate.setId(null);
        if (variant.equals("index")) when(f.rows.findByMangaIndex(123)).thenReturn(List.of(f.row));
        else {
            candidate.setMangaIndex(null); when(f.rows.findByTitel(candidate.getTitel())).thenReturn(List.of(f.row));
            if (variant.equals("otherEdition")) { var p = new Verlag(); p.setVerlagId(2L); candidate.setVerlag(p); }
            if (variant.equals("existing")) candidate.setId(42L);
        }
        var before = f.state();
        if (variant.equals("index") || variant.equals("sameEdition")) {
            assertThrows(IllegalArgumentException.class, () -> f.series.saveMangaReihe(candidate));
            verify(f.rows, never()).save(any());
        } else assertSame(candidate, f.series.saveMangaReihe(candidate));
        assertEquals(before, f.state());
    }
}
