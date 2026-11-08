package de.mangalib.service;

import de.mangalib.T03RepositoryHook;
import de.mangalib.T03RepositoryHookTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Nur der vorhandene RAM-Transaktionsmanager; kein Nachweis für SQL-Rollback. */
class T03RepositoryHookTransactionTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void hookFailurePropagatesToTheExistingCoordinatorRollback(boolean after){
        var manager=new FakeLibraryTransactionManager();var coordinator=new LibraryWriteCoordinator(manager);
        var store=spy(new T03RepositoryHookTest.Store());
        var factory=new ProxyFactory(store);factory.setInterfaces(T03RepositoryHookTest.Rows.class);
        var repo=(T03RepositoryHookTest.Rows)factory.getProxy();
        var failure=new IllegalStateException("synthetic hook failure");var row=new T03RepositoryHookTest.Row(1L,"synthetic");
        try(var hook=new T03RepositoryHook(repo,call->{if(after)call.proceed();throw failure;})){
            assertSame(failure,assertThrows(IllegalStateException.class,()->coordinator.execute(()->repo.save(row))));
            assertEquals(1,manager.rollbacks.get());assertEquals(0,manager.commits.get());
            verify(store,times(after?1:0)).save(row);
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSame(row,coordinator.execute(()->repo.save(row)));assertEquals(1,manager.commits.get());
    }
    @Test void removingRepositoryHookDoesNotLiftCommitQuarantine(){
        var manager=new FakeLibraryTransactionManager();var coordinator=new LibraryWriteCoordinator(manager);
        var store=spy(new T03RepositoryHookTest.Store());var factory=new ProxyFactory(store);
        factory.setInterfaces(T03RepositoryHookTest.Rows.class);var repo=(T03RepositoryHookTest.Rows)factory.getProxy();
        var failure=new IllegalStateException("synthetic beforeCommit");var row=new T03RepositoryHookTest.Row(1L,"synthetic");
        try(var hook=new T03RepositoryHook(repo,call->{Object result=call.proceed();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                public void beforeCommit(boolean readOnly){throw failure;}
            });return result;
        })){
            assertSame(failure,assertThrows(IllegalStateException.class,()->coordinator.execute(()->repo.save(row))));
            verify(store,times(1)).save(row);assertEquals(1,manager.rollbacks.get());
        }
        assertThrows(IllegalStateException.class,()->coordinator.execute(()->fail("Quarantäne darf nicht aufgehoben werden")));
        assertEquals(0,manager.commits.get());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
