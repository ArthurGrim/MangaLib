package de.mangalib;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.*;
import org.springframework.data.repository.core.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Reale Spring-Data- und AOP-Dispatchkette, konkrete RAM-Implementierung.
 * Kein EntityManager, Spring-Boot-Kontext, Treiber oder Datenbankzugriff. */
public class T03RepositoryHookTest {
    public record Row(Long id,String group) { }
    public interface Rows extends Repository<Row,Long> {
        Row save(Row row);
        Row saveAndFlush(Row row);
        void flush();
        Optional<Row> findById(Long id);
        List<Row> findByGroup(String group);
        List<Row> findAll();
    }
    public static class Store implements Rows {
        final Map<Long,Row> data=new ConcurrentHashMap<>();
        final Map<String,AtomicInteger> calls=new ConcurrentHashMap<>();
        final AtomicReference<RuntimeException> failure=new AtomicReference<>();
        void count(String method){calls.computeIfAbsent(method,key->new AtomicInteger()).incrementAndGet();}
        int countOf(String method){return calls.getOrDefault(method,new AtomicInteger()).get();}
        public Row save(Row row){count("save");if(failure.get()!=null)throw failure.get();data.put(row.id(),row);return row;}
        public Row saveAndFlush(Row row){count("saveAndFlush");Row saved=save(row);flush();return saved;}
        public void flush(){count("flush");}
        public Optional<Row> findById(Long id){count("findById");return Optional.ofNullable(data.get(id));}
        public List<Row> findByGroup(String group){count("findByGroup");return data.values().stream().filter(row->row.group().equals(group)).toList();}
        public List<Row> findAll(){count("findAll");return List.copyOf(data.values());}
    }
    /** Die echte RepositoryFactorySupport erzeugt den Interfaceproxy samt
     * Spring-Data-Methodenauflösung. Nur der Speicher ist hier bewusst ein Fake. */
    static class Factory extends RepositoryFactorySupport {
        private final Store store;
        Factory(Store store){this.store=store;}
        public EntityInformation<?,?> getEntityInformation(RepositoryMetadata metadata){
            return new AbstractEntityInformation<Row,Long>(Row.class){
                public Long getId(Row entity){return entity.id();}
                public Class<Long> getIdType(){return Long.class;}
            };
        }
        protected Object getTargetRepository(RepositoryInformation info){return store;}
        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata){return Store.class;}
    }
    private static Rows proxy(String kind,Store store){
        if(kind.equals("factory"))return new Factory(store).getRepository(Rows.class);
        var factory=new ProxyFactory(store);factory.setInterfaces(Rows.class);
        factory.setProxyTargetClass(kind.equals("cglib"));return (Rows)factory.getProxy();
    }

    @Test void historicalCallRealMethodFailsOnDelegatingInterfaceMock() {
        var store=new Store();Rows real=proxy("factory",store);
        Rows mock=mock(Rows.class,delegatesTo(real));
        var first=new Row(1L,"synthetic");assertSame(first,mock.save(first));assertEquals(1,store.countOf("save"));
        doAnswer(call->call.callRealMethod()).when(mock).save(any(Row.class));
        var failure=assertThrows(org.mockito.exceptions.base.MockitoException.class,()->mock.save(new Row(2L,"synthetic")));
        assertTrue(failure.getMessage().contains("Cannot call abstract real method"));
        assertEquals(1,store.countOf("save"));assertEquals(Set.of(1L),store.data.keySet());
    }

    @ParameterizedTest @ValueSource(strings={"factory","jdk","cglib"})
    void realChainHandlesAllUsedOperationsExactlyOnce(String kind){
        var store=new Store();Rows repo=proxy(kind,store);Object same=repo;var intercepted=new AtomicInteger();
        try(var hook=new T03RepositoryHook(repo,call->{intercepted.incrementAndGet();return call.proceed();})){
            Row first=new Row(1L,"synthetic"),second=new Row(2L,"synthetic");
            assertSame(first,repo.save(first));assertSame(second,repo.saveAndFlush(second));repo.flush();
            assertSame(first,repo.findById(1L).orElseThrow());assertEquals(2,repo.findByGroup("synthetic").size());assertEquals(2,repo.findAll().size());
            assertSame(same,repo);assertEquals(6,intercepted.get());
            assertEquals(2,store.countOf("save")); // Zweiter Save gehört zum echten saveAndFlush-Körper.
            assertEquals(1,store.countOf("saveAndFlush"));assertEquals(2,store.countOf("flush"));
            for(String method:List.of("findById","findByGroup","findAll"))assertEquals(1,store.countOf(method));
        }
        repo.findAll();assertEquals(6,intercepted.get());assertEquals(2,store.countOf("findAll"));
    }

    @ParameterizedTest @ValueSource(strings={"factory","jdk","cglib"})
    void beforeAndAfterFailuresKeepTheExactOperationAndExceptionSemantics(String kind){
        var store=new Store();Rows repo=proxy(kind,store);var before=new IllegalStateException("synthetic before");
        try(var hook=new T03RepositoryHook(repo,call->{throw before;})){
            assertSame(before,assertThrows(IllegalStateException.class,()->repo.save(new Row(1L,"synthetic"))));
            assertEquals(0,store.countOf("save"));
        }
        var after=new IllegalStateException("synthetic after");
        try(var hook=new T03RepositoryHook(repo,call->{Object result=call.proceed();throw after;})){
            assertSame(after,assertThrows(IllegalStateException.class,()->repo.save(new Row(2L,"synthetic"))));
            assertEquals(1,store.countOf("save"));assertEquals(Set.of(2L),store.data.keySet());
        }
        repo.save(new Row(3L,"synthetic"));assertEquals(2,store.countOf("save"));
    }

    @ParameterizedTest @ValueSource(strings={"factory","jdk","cglib"})
    void originalFailureIsNotRetriedWrappedOrPassedToAfterHook(String kind){
        var store=new Store();Rows repo=proxy(kind,store);var expected=new IllegalArgumentException("synthetic repository failure");
        store.failure.set(expected);var after=new AtomicInteger();
        try(var hook=new T03RepositoryHook(repo,call->{Object result=call.proceed();after.incrementAndGet();return result;})){
            assertSame(expected,assertThrows(IllegalArgumentException.class,()->repo.save(new Row(1L,"synthetic"))));
            assertEquals(1,store.countOf("save"));assertTrue(store.data.isEmpty());assertEquals(0,after.get());
        }
    }

    @Test void concreteMockitoSpyRemainsCompatibleBehindSpringDataProxy(){
        var store=spy(new Store());Rows repo=proxy("factory",store);
        try(var hook=new T03RepositoryHook(repo,call->call.proceed())){
            var row=new Row(1L,"synthetic");assertSame(row,repo.save(row));verify(store,times(1)).save(row);
        }
        assertEquals(Set.of(1L),store.data.keySet());
    }

    @Test void closeIsIdempotentAndRetainsOtherAdvisors(){
        Rows repo=proxy("factory",new Store());var advised=(Advised)repo;
        var original=List.of(advised.getAdvisors());var calls=new AtomicInteger();
        var hook=new T03RepositoryHook(repo,call->{calls.incrementAndGet();return call.proceed();});
        repo.flush();hook.close();hook.close();repo.flush();
        assertEquals(1,calls.get());assertEquals(original,List.of(advised.getAdvisors()));
    }

    @Test void repeatedScopesDoNotLeaveHooksOrDuplicateCallbacks(){
        var store=new Store();Rows repo=proxy("factory",store);var advised=(Advised)repo;
        var original=List.of(advised.getAdvisors());var observed=new AtomicInteger();
        for(long id=1;id<=3;id++){
            try(var hook=new T03RepositoryHook(repo,call->{observed.incrementAndGet();return call.proceed();})){
                repo.save(new Row(id,"synthetic"));
            }
            assertEquals(original,List.of(advised.getAdvisors()));repo.flush();
        }
        assertEquals(3,observed.get());assertEquals(3,store.countOf("save"));
    }

    @Test void unexpectedAdvisorRemovalFailsClosedWithoutRemovingOthers(){
        Rows repo=proxy("factory",new Store());var advised=(Advised)repo;var original=List.of(advised.getAdvisors());
        var hook=new T03RepositoryHook(repo,call->call.proceed());advised.removeAdvisor(advised.getAdvisors()[0]);
        assertThrows(IllegalStateException.class,hook::close);assertEquals(original,List.of(advised.getAdvisors()));
    }

    @Test void matrixCannotReintroduceTheHistoricalRepositoryDelegation()throws Exception{
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/t03Integration/java/de/mangalib/T03MatrixAcceptanceTest.java"));
        assertFalse(source.contains("callRealMethod()"));
        assertTrue(source.contains("@Autowired EinkaufslisteRepository carts"));
        assertTrue(source.contains("@Autowired BandRepository bands"));
        assertEquals(4,source.split("new T03RepositoryHook\\(",-1).length-1);
        assertTrue(source.contains("closeRepositoryHooks();"));
        assertTrue(source.contains("T03Preservation.verify(baseline,after,journal.ids())"));
    }

    @Test void refusesUnsupportedOrFrozenRepositoriesWithoutModifyingThem(){
        assertThrows(IllegalStateException.class,()->new T03RepositoryHook(new Store(),call->call.proceed()));
        Rows repo=proxy("jdk",new Store());var advised=(Advised)repo;var original=List.of(advised.getAdvisors());
        var factory=new ProxyFactory(new Store());factory.setInterfaces(Rows.class);factory.setFrozen(true);Rows frozen=(Rows)factory.getProxy();
        assertThrows(IllegalStateException.class,()->new T03RepositoryHook(frozen,call->call.proceed()));
        assertEquals(original,List.of(advised.getAdvisors()));
    }

    @Test void parallelCallsDoNotShareInvocationOrDuplicateWork()throws Exception{
        var store=new Store();Rows repo=proxy("factory",store);var observed=ConcurrentHashMap.<Long>newKeySet();
        var together=new CountDownLatch(2);var executor=Executors.newFixedThreadPool(2);
        try(var hook=new T03RepositoryHook(repo,call->{
            if(call.getMethod().getName().equals("save")){
                assertTrue(observed.add(((Row)call.getArguments()[0]).id()));together.countDown();
                assertTrue(together.await(3,TimeUnit.SECONDS));
            }return call.proceed();
        })){
            var first=executor.submit(()->repo.save(new Row(1L,"synthetic")));
            var second=executor.submit(()->repo.save(new Row(2L,"synthetic")));
            assertEquals(1L,first.get(5,TimeUnit.SECONDS).id());assertEquals(2L,second.get(5,TimeUnit.SECONDS).id());
            assertEquals(2,store.countOf("save"));assertEquals(Set.of(1L,2L),store.data.keySet());
        }finally{executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }

    @Test void earlyIdCanBeJournaledEvenIfTheNextGraphOperationFails()throws Exception{
        var store=new Store();Rows repo=proxy("factory",store);
        var directory=java.nio.file.Files.createTempDirectory("t03-hook-journal-");
        try(var journal=new T03RunJournal(UUID.randomUUID(),directory);
            var hook=new T03RepositoryHook(repo,call->{Object result=call.proceed();if(result instanceof Row row)journal.register("mangareihe",row.id());return result;})){
            var expected=new IllegalStateException("later graph step");
            assertSame(expected,assertThrows(IllegalStateException.class,()->{repo.save(new Row(51L,"synthetic"));throw expected;}));
            assertEquals(Set.of(51L),journal.ids().get("mangareihe"));assertEquals(1,store.countOf("save"));
            assertTrue(java.nio.file.Files.readString(directory.resolve("ids.txt")).contains("ID mangareihe 51"));
        }finally{java.nio.file.Files.deleteIfExists(directory.resolve("ids.txt"));java.nio.file.Files.deleteIfExists(directory);}
    }
}
