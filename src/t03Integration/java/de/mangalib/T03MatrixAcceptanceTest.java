package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import de.mangalib.testsupport.GuardedTestDataSource;
import de.mangalib.validation.WriteValidation;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Eigene spätere Freigabe. Fremdbestand einschließlich erster Fixturegruppe bleibt
 * unverändert; jeder Fall schützt außerdem die Fixtures aller vorherigen Fälle. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers=T03MatrixAcceptanceTest.Approval.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class T03MatrixAcceptanceTest {
    public static class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            if (!"true".equals(System.getProperty("t03.matrix.approved"))) throw new IllegalStateException("Matrix nicht freigegeben.");
        }
    }
    @Autowired EntityManager em;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    // Der konkrete Manager bleibt Spy für den vorhandenen Abschlussbeobachter.
    // Repository-Interfaces dagegen über ihre echte Spring-Advicekette ausführen.
    @MockitoSpyBean(reset=MockReset.NONE) PlatformTransactionManager manager;
    @Autowired EinkaufslisteRepository carts;
    @Autowired BandRepository bands;
    @Autowired MangaReiheRepository rows;
    @Autowired MangaDetailsRepository details;
    @Autowired LibraryWriteCoordinator coordinator;
    @Autowired MangaReiheService library;
    @Autowired BandService bandService;
    @Autowired PurchaseService purchases;
    @Autowired MockMvc mvc;
    @MockitoBean ScraperGateway scrapers;
    private enum Fault { NONE, SAVE, FLUSH, BAND_SAVE, BEFORE_COMMIT }
    private Fault fault=Fault.NONE;
    private final AtomicInteger completion=new AtomicInteger(-1);
    private boolean reverse, stopped, bodyFailed, confirmedQuarantine, casePrepared;
    private T03PurchaseOverlap overlap;
    private T03RunJournal journal;
    private final List<T03RepositoryHook> repositoryHooks=new ArrayList<>();
    private TestDatabaseSnapshot.Snapshot baseline, caseBefore;
    private final Map<String,Set<Long>> currentIds=new TreeMap<>();
    private final Set<String> titles=new HashSet<>();
    private String prefix;
    private int sequence, nextIndex;
    private long statusId, publisherId, typeId, formatId;
    private static final Map<String,Integer> MAX_DURABLE = Map.of(
            "mangareihe",8,"mangadetails",8,"baende",28,"einkaufsliste",8,"einkaufslistedetails",8);

    @RegisterExtension final Outcome outcome=new Outcome();
    class Outcome implements TestExecutionExceptionHandler, TestWatcher {
        public void handleTestExecutionException(ExtensionContext c,Throwable failure)throws Throwable {bodyFailed=true;throw failure;}
        public void testSuccessful(ExtensionContext c){stopped=false;}
        public void testFailed(ExtensionContext c,Throwable failure){stopped=true;if(journal!=null)journal.phase(false);}
    }
    @BeforeAll void prepare() throws Exception {
        assertInstanceOf(GuardedTestDataSource.class,dataSource);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertEquals(1,context.getBeansOfType(LibraryWriteCoordinator.class).size());
        var template=(TransactionTemplate)org.springframework.test.util.ReflectionTestUtils.getField(coordinator,"transaction");
        assertSame(manager,template.getTransactionManager());
        assertSame(carts,org.springframework.test.util.ReflectionTestUtils.getField(purchases,"shopping"));
        assertSame(bands,org.springframework.test.util.ReflectionTestUtils.getField(purchases,"bands"));
        assertSame(bands,org.springframework.test.util.ReflectionTestUtils.getField(bandService,"bandRepository"));
        assertSame(rows,org.springframework.test.util.ReflectionTestUtils.getField(library,"mangaReiheRepository"));
        assertSame(details,org.springframework.test.util.ReflectionTestUtils.getField(library,"mangaDetailsRepository"));
        Object write=context.getBean("libraryWriteAdvisor"),tx=context.getBean("org.springframework.transaction.config.internalTransactionAdvisor");
        var attributes=new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
        for(Class<?> type:List.of(MangaReiheService.class,BandService.class,MangaDetailsService.class,EinkaufslisteService.class,EinkaufslisteItemDetailsService.class)){
            var proxy=assertInstanceOf(org.springframework.aop.framework.Advised.class,context.getBean(type));
            var advice=Arrays.asList(proxy.getAdvisors());int w=advice.indexOf(write),t=advice.indexOf(tx);
            assertTrue(w>=0&&(t<0||w<t));
            if(Arrays.stream(type.getMethods()).anyMatch(m->attributes.getTransactionAttribute(m,type)!=null))assertTrue(t>=0);
        }
        var run=UUID.fromString(System.getProperty("t03.run.id")); prefix="T03_MATRIX_"+run+"_";
        journal=new T03RunJournal(run,Path.of(System.getProperty("t03.evidence.directory")));
        read(()->{ @SuppressWarnings("unchecked") List<String> grants=em.createNativeQuery("SHOW GRANTS FOR CURRENT_USER()").getResultList();T03ServerRights.verify(grants);return null;});
        baseline=read(()->TestDatabaseSnapshot.capture(em));
        verifyBaseline(baseline);
        String expected=System.getProperty("t03.matrix.baseline");
        assertTrue(expected!=null&&expected.matches("[a-f0-9]{64}"));
        assertEquals(expected,baseline.fingerprint(),"Aktuelle freigegebene Baseline verändert: STOP");
        nextIndex=1_500_000_000+Math.floorMod(run.hashCode(),100_000_000)*4;
        read(()->{statusId=em.createQuery("from Status order by statusId",Status.class).setMaxResults(1).getSingleResult().getStatusId();
            publisherId=em.createQuery("from Verlag order by verlagId",Verlag.class).setMaxResults(1).getSingleResult().getVerlagId();
            typeId=em.createQuery("from Typ order by typId",Typ.class).setMaxResults(1).getSingleResult().getTypId();
            formatId=em.createQuery("from Format order by formatId",Format.class).setMaxResults(1).getSingleResult().getFormatId();
            assertNotNull(em.find(Sammelband.class,1L));return null;});
    }
    private void installRepositoryHooks(){
        repositoryHooks.add(new T03RepositoryHook(carts,call->{
            String method=call.getMethod().getName();
            if(!method.equals("save")&&!method.equals("saveAndFlush")){
                Object result=call.proceed();if(method.equals("flush")&&fault==Fault.FLUSH)throw new SyntheticFailure();return result;
            }
            var item=(EinkaufslisteItem)call.getArguments()[0];
            assertTrue(currentIds.getOrDefault("einkaufsliste",Set.of()).contains(item.getId()),"Fremder Einkauf: STOP");
            Object result=call.proceed();
            if(Boolean.TRUE.equals(item.getGekauft())){
                registerGraph();
                if(fault==Fault.SAVE)throw new SyntheticFailure();
                if(fault==Fault.BEFORE_COMMIT)TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                    public void beforeCommit(boolean readOnly){throw new SyntheticFailure();}
                    public void afterCompletion(int state){completion.set(state);}
                });
            }return result;
        }));
        repositoryHooks.add(new T03RepositoryHook(bands,call->{
            Object result=call.proceed();String method=call.getMethod().getName();
            if(method.equals("save")||method.equals("saveAndFlush")){
                var band=(Band)result;recordSaved("baende",band.getId(),band.getMangaReihe());
                if(fault==Fault.BAND_SAVE){assertTrue(currentIds.getOrDefault("baende",Set.of()).contains(band.getId()));throw new SyntheticFailure();}
            }
            if(method.equals("findByMangaReiheId")){
                @SuppressWarnings("unchecked") var found=(List<Band>)result;
                var copy=new ArrayList<>(found);if(reverse)Collections.reverse(copy);return copy;
            }
            return result;
        }));
        repositoryHooks.add(new T03RepositoryHook(rows,call->{Object result=call.proceed();
            if(call.getMethod().getName().equals("save")||call.getMethod().getName().equals("saveAndFlush")){
                var row=(MangaReihe)result;recordSaved("mangareihe",row.getId(),row);
            }return result;
        }));
        repositoryHooks.add(new T03RepositoryHook(details,call->{Object result=call.proceed();
            if(call.getMethod().getName().equals("save")||call.getMethod().getName().equals("saveAndFlush")){
                var detail=(MangaDetails)result;recordSaved("mangadetails",detail.getId(),detail.getMangaReihe());
            }return result;
        }));
    }
    private void closeRepositoryHooks(){
        for(int i=repositoryHooks.size()-1;i>=0;i--)repositoryHooks.get(i).close();
        repositoryHooks.clear();
    }
    @BeforeEach void ready(){casePrepared=false;assertFalse(stopped,"Vorherige Stufe unbestätigt: STOP");stopped=true;bodyFailed=false;
        caseBefore=null;currentIds.clear();titles.clear();overlap=null;fault=Fault.NONE;reverse=false;
        caseBefore=read(()->TestDatabaseSnapshot.capture(em));casePrepared=true;installRepositoryHooks();}
    @AfterEach void preserve(){
        assertTrue(overlap==null||overlap.threadsEnded(),"Hookabbau bei laufender Arbeit gesperrt: STOP");
        closeRepositoryHooks();
        if(!casePrepared)return; // Kein Folgezugriff nach bereits gesperrtem BeforeEach.
        casePrepared=false;
        fault=Fault.NONE;reverse=false;
        assertTrue(overlap==null||overlap.completionConfirmed(),"Hintergrundabschluss unbestätigt: STOP");
        assertTrue(!quarantined()||confirmedQuarantine,"Transaktionsabschluss unbestätigt: STOP");
        assertNotNull(caseBefore);
        var after=read(()->TestDatabaseSnapshot.capture(em));
        T03Preservation.verify(caseBefore,after,currentIds);
        T03Preservation.verify(baseline,after,journal.ids());
        durableBudget().forEach((table,limit)->assertTrue(newCount(after,table)<=limit,"Fixturebudget überschritten: STOP"));
        verifyNoInteractions(scrapers);journal.phase(!bodyFailed);
    }
    @AfterAll void close()throws Exception{
        try{assertTrue(overlap==null||overlap.threadsEnded());closeRepositoryHooks();}
        finally{if(journal!=null)journal.close();}
    }

    @Test @Order(1) void httpExistingPurchaseFaultsAndMixedRouteReplay()throws Exception{
        long row=row(3,"6.00","0.00"),item=cart(row,1);
        faultRequests(item,false);
        var old=read(()->T03BandSnapshot.capture(bands.findByMangaReiheId(row)));
        overlap=new T03PurchaseOverlap(manager,coordinator);
        assertEquals(List.of("UPDATED","ALREADY_PURCHASED"),overlap.run(
                ()->http(item,false,row,4,4,200),()->http(item,true,row,4,4,200)));
        read(()->{T03BandSnapshot.verifyFourthOnly(old,T03BandSnapshot.capture(bands.findByMangaReiheId(row)),row);
            assertTrue(em.find(EinkaufslisteItem.class,item).getGekauft());return null;});
        unchanged(()->assertEquals("ALREADY_PURCHASED",http(item,false,row,4,4,200)));
    }
    @Test @Order(2) void httpNewPurchaseFaultsAndReplay()throws Exception{
        long item=cart(null,3);
        unchanged(()->assertEquals("STATUS_REQUIRED",http(item,false,null,null,null,417)));
        faultRequests(item,true);
        assertEquals("CREATED",http(item,true,null,null,null,200));
        read(()->{assertTrue(em.find(EinkaufslisteItem.class,item).getGekauft());return null;});
        unchanged(()->assertEquals("ALREADY_PURCHASED",http(item,false,null,null,null,200)));
        unchanged(()->{var response=mvc.perform(post("/addToEinkaufsliste").header("Host","localhost:8080")
                .with(request->{request.setServerPort(8080);return request;})
                .header("Origin","http://localhost:8080").with(csrf().asHeader()).contentType("application/json")
                .content("{\"titel\":\"synthetic\",\"anzahlBaende\":0}")).andReturn();assertEquals(400,response.getResponse().getStatus());});
    }
    @Test @Order(3) void twoDifferentItemsAppendToTheSameSeriesSerially()throws Exception{
        long row=row(3,"6.00","0.00"),first=cart(row,1),second=cart(row,1);
        var old=read(()->T03BandSnapshot.capture(bands.findByMangaReiheId(row)));
        overlap=new T03PurchaseOverlap(manager,coordinator);
        assertEquals(List.of(PurchaseService.Outcome.UPDATED,PurchaseService.Outcome.UPDATED),overlap.run(
                ()->purchases.purchase(new PurchaseService.Request(first,null,row,4,4)).outcome(),
                ()->purchases.purchase(new PurchaseService.Request(second,null,row,5,5)).outcome()));
        read(()->{var after=T03BandSnapshot.capture(bands.findByMangaReiheId(row));assertEquals(5,after.size());
            old.forEach((id,value)->assertTrue(Objects.equals(value,after.get(id)),"Altband geändert"));
            assertTrue(em.find(EinkaufslisteItem.class,first).getGekauft());assertTrue(em.find(EinkaufslisteItem.class,second).getGekauft());return null;});
    }
    @Test @Order(4) void twoItemsForANewIdentityRequireASecondExplicitConfirmation()throws Exception{
        long first=cart(null,1),second=cart(null,1);
        coordinator.execute(()->{var a=em.find(EinkaufslisteItem.class,first);var b=em.find(EinkaufslisteItem.class,second);
            b.setTitel(a.getTitel());b.setMangaIndex(a.getMangaIndex());return null;});
        overlap=new T03PurchaseOverlap(manager,coordinator);
        assertEquals(List.of(PurchaseService.Outcome.CREATED,PurchaseService.Outcome.CONFIRM_VOLUMES),overlap.run(
                ()->purchases.purchase(new PurchaseService.Request(first,statusId,null,null,null)).outcome(),
                ()->purchases.purchase(new PurchaseService.Request(second,statusId,null,null,null)).outcome()));
        read(()->{assertTrue(em.find(EinkaufslisteItem.class,first).getGekauft());assertFalse(em.find(EinkaufslisteItem.class,second).getGekauft());return null;});
        var identity=read(()->{var item=em.find(EinkaufslisteItem.class,first);
            return em.createQuery("from MangaReihe where mangaIndex=:i",MangaReihe.class).setParameter("i",item.getMangaIndex()).getSingleResult();});
        overlap=new T03PurchaseOverlap(manager,coordinator);
        assertEquals(List.of("UPDATED","DUPLICATE_REJECTED"),overlap.runWithRejectedSecondWrite(
                ()->purchases.purchase(new PurchaseService.Request(second,null,identity.getId(),2,2)).outcome().name(),
                ()->{assertThrows(IllegalArgumentException.class,()->library.saveMangaReihe(identity.getMangaIndex(),statusId,publisherId,typeId,formatId,
                        identity.getTitel(),1,new BigDecimal("2.00"),false,false,null,1L,BigDecimal.ZERO,false,Map.of()));return "DUPLICATE_REJECTED";}));
        read(()->{assertEquals(2,bands.findByMangaReiheId(identity.getId()).size());assertTrue(em.find(EinkaufslisteItem.class,second).getGekauft());return null;});
    }
    @Test @Order(5) void purchaseAndSingleBandEditKeepStableIdentitiesWithUnsortedLists()throws Exception{
        long row=row(3,"6.00","0.00"),item=cart(row,1);
        coordinator.execute(()->{var list=bands.findByMangaReiheId(row).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
            for(int i=0;i<list.size();i++){list.get(i).setPreis(BigDecimal.valueOf(4L+2L*i).setScale(2));list.get(i).setBandIndex(index());}return null;});
        var old=read(()->T03BandSnapshot.capture(bands.findByMangaReiheId(row)));long target=old.keySet().stream().min(Long::compare).orElseThrow();
        reverse=true;overlap=new T03PurchaseOverlap(manager,coordinator);
        assertEquals(List.of("UPDATED","EDITED"),overlap.run(
                ()->purchases.purchase(new PurchaseService.Request(item,null,row,4,4)).outcome().name(),
                ()->{var input=new Band();input.setBildUrl(null);input.setId(target);input.setIstGelesen(!old.get(target).read());bandService.updateBand(input);return "EDITED";}));
        read(()->{var after=T03BandSnapshot.capture(bands.findByMangaReiheId(row));assertEquals(4,after.size());
            old.forEach((id,value)->{if(id!=target)assertTrue(Objects.equals(value,after.get(id)));});
            var value=old.get(target);var changed=after.get(target);
            assertEquals(new T03BandSnapshot.State(value.id(),value.row(),value.number(),value.index(),value.price(),value.adjustment(),
                    !value.read(),value.reread(),value.special(),value.cover(),value.passion(),value.created(),changed.updated()),changed);
            assertEquals(0,new BigDecimal("19.99").compareTo(em.find(MangaReihe.class,row).getGesamtpreis()));return null;});
        fault=Fault.BAND_SAVE;
        unchanged(()->assertThrows(SyntheticFailure.class,()->{
            var input=new Band();input.setId(target);input.setBildUrl(null);input.setIstGelesen(!old.get(target).read());
            input.setPreis(new BigDecimal("9.99"));bandService.updateBand(input);
        }));
        fault=Fault.NONE;assertFalse(quarantined());
        unchanged(()->{assertNull(read(()->em.find(Band.class,Long.MAX_VALUE)));
            var input=new Band();input.setId(Long.MAX_VALUE);input.setBildUrl(null);bandService.updateBand(input);});
    }
    @Test @Order(6) void signedRestcentsSurviveSeparateCommitsAndReloads(){
        long row=row(3,"0.00","0.01");
        var expected=Map.of("0.01",List.of("0.01","0.00","0.00"),"-0.01",List.of("-0.01","0.00","0.00"),
                "0.02",List.of("0.01","0.01","0.00"),"20.00",List.of("6.67","6.67","6.66"),"-20.00",List.of("-6.67","-6.67","-6.66"));
        for(String total:List.of("0.01","-0.01","0.02","20.00","-20.00")){
            if(!total.equals("0.01"))edit(row,3,null,new BigDecimal(total),Map.of("adjustmentEdited","true"));
            read(()->{var list=bands.findByMangaReiheId(row).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
                assertEquals(expected.get(total).stream().map(BigDecimal::new).toList(),list.stream().map(Band::getAenderungPreis).toList());
                assertEquals(0,new BigDecimal(total).compareTo(em.find(MangaReihe.class,row).getGesamtpreis()));return null;});
        }
    }
    @Test @Order(7) void legacyNullMoneyAndCountCorrectionsPreserveEveryBand(){
        long row=row(3,"6.00","0.00");
        coordinator.execute(()->{var b=bands.findByMangaReiheId(row).getFirst();b.setPreis(null);b.setAenderungPreis(null);
            var r=em.find(MangaReihe.class,row);r.setGesamtpreis(null);r.setPreisProBand(null);r.setAenderungGesamtpreis(null);return null;});
        var old=read(()->T03BandSnapshot.capture(bands.findByMangaReiheId(row)));
        library.updateMangaReiheTitel(row,prefix+(++sequence)+"_metadata");
        unchanged(()->assertThrows(IllegalArgumentException.class,()->edit(row,3,new BigDecimal("2.00"),null,Map.of("priceEdited","true"))));
        for(int declared:List.of(2,5)){
            coordinator.execute(()->{em.find(MangaReihe.class,row).setAnzahlBaende(declared);return null;});
            edit(row,3,null,null,Map.of("extendVolumes","false"));
            read(()->{assertEquals(3,em.find(MangaReihe.class,row).getAnzahlBaende());assertNull(em.find(MangaReihe.class,row).getGesamtpreis());
                assertTrue(old.equals(T03BandSnapshot.capture(bands.findByMangaReiheId(row)))) ;return null;});
        }
    }
    @Test @Order(8) void explicitMismatchExtensionAndReductionRejectionsKeepM06(){
        long row=row(3,"6.00","0.00");
        coordinator.execute(()->{var r=em.find(MangaReihe.class,row);r.setAnzahlBaende(5);r.getMangaDetails().setReread(2);
            var list=bands.findByMangaReiheId(row).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
            for(int i=0;i<3;i++){list.get(i).setIstGelesen(i!=1);list.get(i).setReread(i==0?null:i-1);list.get(i).setIstSpecial(i==0?null:i==2);}return null;});
        var old=read(()->T03BandSnapshot.capture(bands.findByMangaReiheId(row)));
        for(int target:List.of(2,4))unchanged(()->assertThrows(IllegalArgumentException.class,()->edit(row,target,null,null,Map.of("extendVolumes","false"))));
        edit(row,5,null,null,Map.of("extendVolumes","false")); // Keine versteckte Anlage.
        assertEquals(3,read(()->bands.findByMangaReiheId(row).size()));
        coordinator.execute(()->{em.find(MangaReihe.class,row).setAnzahlBaende(2);return null;});
        edit(row,4,new BigDecimal("2.00"),new BigDecimal("0.01"),Map.of("extendVolumes","true"));
        read(()->{var after=T03BandSnapshot.capture(bands.findByMangaReiheId(row));T03BandSnapshot.verifyFourthOnly(old,after,row);
            assertFalse(after.values().stream().filter(v->v.number()==4).findFirst().orElseThrow().read());
            assertEquals(2,em.find(MangaReihe.class,row).getMangaDetails().getReread());assertFalse(em.find(MangaReihe.class,row).getMangaDetails().isIstGelesen());return null;});
    }
    @ParameterizedTest @ValueSource(strings={"ambiguous","null","stale","details","duplicate","unknownPrice","cartDetails","edition"}) @Order(9)
    void controlledRejectionsUseFullyRolledBackFixtures(String scenario){
        var before=read(()->TestDatabaseSnapshot.capture(em));
        assertThrows(RollbackFixture.class,()->coordinator.execute(()->{
            if(scenario.equals("cartDetails")){
                long item=cart(null,1,false);
                assertEquals(PurchaseService.Outcome.DETAILS_NOT_FOUND,purchases.purchase(new PurchaseService.Request(item,statusId,null,null,null)).outcome());
                throw new RollbackFixture();
            }
            long row;
            if(scenario.equals("details")){
                var r=new MangaReihe();r.setTitel(title());r.setMangaIndex(index());r.setAnzahlBaende(1);
                r.setStatus(em.find(Status.class,statusId));r.setVerlag(em.find(Verlag.class,publisherId));r.setTyp(em.find(Typ.class,typeId));r.setFormat(em.find(Format.class,formatId));
                em.persist(r);em.flush();record("mangareihe",r.getId());row=r.getId();
            }else row=row(3,"6.00","0.00");
            if(scenario.equals("ambiguous")){long other=row(3,"6.00","0.00");em.find(MangaReihe.class,other).setMangaIndex(em.find(MangaReihe.class,row).getMangaIndex());}
            if(scenario.equals("null"))em.find(MangaReihe.class,row).setMangaIndex(null);
            if(scenario.equals("duplicate")){var list=bands.findByMangaReiheId(row);list.get(1).setBandNr(list.getFirst().getBandNr());}
            if(scenario.equals("unknownPrice"))bands.findByMangaReiheId(row).getFirst().setPreis(null);
            long item=cart(row,1);em.flush();
            if(scenario.equals("edition")){
                var other=em.createQuery("from Format where formatId <> :id order by formatId",Format.class)
                        .setParameter("id",formatId).setMaxResults(1).getResultList();
                assertEquals(1,other.size(),"Zweiter bestehender Formatwert für Editionskonflikt fehlt: STOP");
                em.find(EinkaufslisteItem.class,item).setFormatId(other.getFirst());em.flush();
            }
            // Die Anlage speichert die besitzende Detailseite. Ein Reload
            // materialisiert auch die inverse Seite wie beim späteren Kaufrequest.
            em.clear();
            if(scenario.equals("stale")){
                var reloaded=em.find(MangaReihe.class,row);
                WriteValidation.editableRelations(reloaded);
                var existing=bands.findByMangaReiheId(row);
                assertEquals(3,reloaded.getAnzahlBaende());
                assertEquals(List.of(1,2,3),existing.stream().map(Band::getBandNr).sorted().toList());
                assertEquals(3,existing.stream().map(Band::getId).distinct().count());
                assertTrue(existing.stream().allMatch(b->b.getId()!=null&&b.getId()>0
                        &&Objects.equals(b.getMangaReihe().getId(),row)
                        &&b.getPreis()!=null&&b.getAenderungPreis()!=null));
                assertEquals(Boolean.FALSE,em.find(EinkaufslisteItem.class,item).getGekauft());
            }
            var result=purchases.purchase(new PurchaseService.Request(item,null,row,2,2)).outcome();
            assertEquals(switch(scenario){case "ambiguous","null"->PurchaseService.Outcome.AMBIGUOUS_SERIES;
                case "details","duplicate","unknownPrice"->PurchaseService.Outcome.INVALID_INVENTORY;
                case "edition"->PurchaseService.Outcome.INCOMPATIBLE_SERIES;default->PurchaseService.Outcome.BAND_CONFLICT;},result);
            if(scenario.equals("stale")){em.find(EinkaufslisteItem.class,item).setGekauft(null);
                assertEquals(PurchaseService.Outcome.UNKNOWN_STATUS,purchases.purchase(new PurchaseService.Request(item,null,row,4,4)).outcome());}
            throw new RollbackFixture();
        }));
        assertFalse(quarantined());T03Preservation.verify(before,read(()->TestDatabaseSnapshot.capture(em)),Map.of());
    }
    @Test @Order(10) void beforeCommitFailureRollsBackAndQuarantinesFurtherWrites()throws Exception{
        long item=cart(null,3);var before=read(()->TestDatabaseSnapshot.capture(em));fault=Fault.BEFORE_COMMIT;
        assertEquals("",http(item,true,null,null,null,500));fault=Fault.NONE;
        assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK,completion.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertTrue(quarantined());confirmedQuarantine=true;
        T03Preservation.verify(before,read(()->TestDatabaseSnapshot.capture(em)),Map.of());
        assertThrows(IllegalStateException.class,()->coordinator.execute(()->fail("Quarantäne muss Writes sperren")));
        var after=read(()->TestDatabaseSnapshot.capture(em));
        durableBudget().forEach((table,count)->assertEquals(count.intValue(),newCount(after,table),"Abschlussbudget abweichend: STOP"));
    }

    private void faultRequests(long item,boolean create)throws Exception{
        for(Fault point:List.of(Fault.SAVE,Fault.FLUSH)){
            var before=read(()->TestDatabaseSnapshot.capture(em));fault=point;
            Long row=create?null:read(()->em.createQuery("from MangaReihe where mangaIndex = :i",MangaReihe.class)
                    .setParameter("i",em.find(EinkaufslisteItem.class,item).getMangaIndex()).getSingleResult().getId());
            assertEquals("",http(item,create,row,create?null:4,create?null:4,500));fault=Fault.NONE;
            assertFalse(quarantined());T03Preservation.verify(before,read(()->TestDatabaseSnapshot.capture(em)),Map.of());
        }
    }
    private String http(long item,boolean create,Long row,Integer first,Integer last,int expected)throws Exception{
        String json="{\"itemId\":\""+item+"\""+(create?",\"statusId\":\""+statusId+"\"":"")
                +(row==null?"":",\"confirmedSeriesId\":\""+row+"\",\"firstBand\":"+first+",\"lastBand\":"+last)+"}";
        var response=mvc.perform(post(create?"/buyItemCreate":"/buyItemUpdate").header("Host","localhost:8080")
                .with(request->{request.setServerPort(8080);return request;})
                .header("Origin","http://localhost:8080").with(csrf().asHeader()).contentType("application/json").content(json)).andReturn();
        assertFalse(response.getRequest().isAsyncStarted());assertEquals(expected,response.getResponse().getStatus());
        if(expected==500)return "";
        assertTrue(response.getResponse().getContentType().startsWith("application/json"));
        return response.getResponse().getHeader("X-MangaLib-Purchase-Outcome");
    }
    private long row(int count,String price,String adjustment){
        String title=title();int index=index();
        return coordinator.execute(()->{var r=library.saveMangaReihe(index,statusId,publisherId,typeId,formatId,title,count,
                new BigDecimal(price),false,false,null,1L,new BigDecimal(adjustment),false,Map.of());registerGraph();return r.getId();});
    }
    private long cart(Long row,int count){return cart(row,count,true);}
    private long cart(Long row,int count,boolean withDetails){return coordinator.execute(()->{
        var r=row==null?null:em.find(MangaReihe.class,row);
        var item=T03CartFixture.identity(row,r,this::title,this::index);
        item.setVerlagId(em.find(Verlag.class,publisherId));item.setTypId(em.find(Typ.class,typeId));item.setFormatId(em.find(Format.class,formatId));
        item.setAnzahlBaende(count);item.setPreis(new BigDecimal("2.00"));item.setAenderungGesamtpreis(new BigDecimal("-0.01"));
        item.setGesamtpreis(item.getPreis().multiply(BigDecimal.valueOf(count)).add(item.getAenderungGesamtpreis()));item.setGekauft(false);
        em.persist(item);em.flush();record("einkaufsliste",item.getId());
        if(withDetails){var d=new EinkaufslisteItemDetails();d.setEinkaufslisteItem(item);d.setSammelbaendeId(em.find(Sammelband.class,1L));em.persist(d);em.flush();record("einkaufslistedetails",d.getId());}return item.getId();});}
    private void registerGraph(){em.flush();for(String title:titles){for(var row:em.createQuery("from MangaReihe where titel = :t",MangaReihe.class).setParameter("t",title).getResultList()){
        record("mangareihe",row.getId());if(row.getMangaDetails()!=null)record("mangadetails",row.getMangaDetails().getId());
        bands.findByMangaReiheId(row.getId()).forEach(b->record("baende",b.getId()));}}}
    private void record(String table,long id){assertFalse(T03Preservation.ids(caseBefore,table).contains(id),"Vorbestand als Fixture beansprucht: STOP");
        journal.register(table,id);currentIds.computeIfAbsent(table,key->new HashSet<>()).add(id);}
    private void recordSaved(String table,Long id,MangaReihe row){
        assertNotNull(id,"Keine zurückgegebene Fixture-ID: STOP");
        if(currentIds.getOrDefault(table,Set.of()).contains(id))return;
        assertNotNull(row);
        if(table.equals("mangareihe")){
            assertTrue(titles.contains(row.getTitel())&&row.getTitel().startsWith(prefix),"Unbekannter Fixturegraph: STOP");
        }else{
            // Kindobjekte nur dem bereits journalisierten eigenen Root zuordnen;
            // keine Titel-/Detailauflösung eines möglicherweise lazy Elternobjekts.
            assertTrue(currentIds.getOrDefault("mangareihe",Set.of()).contains(row.getId()),"Nicht registrierte Fixturewurzel: STOP");
        }
        record(table,id); // Ohne Query/Flush unmittelbar nach erfolgreichem save.
    }
    private String title(){String value=prefix+(++sequence);titles.add(value);return value;}
    private int index(){int value=nextIndex++;assertEquals(0L,readOrCurrent(()->em.createQuery("select count(r) from MangaReihe r where mangaIndex=:i",Long.class).setParameter("i",value).getSingleResult()));return value;}
    private boolean quarantined(){return Boolean.TRUE.equals(org.springframework.test.util.ReflectionTestUtils.getField(coordinator,"completionUnconfirmed"));}
    void verifyBaseline(TestDatabaseSnapshot.Snapshot snapshot)throws Exception{
        T03ContinuationPolicy.verify(snapshot,T03ContinuationPolicy.priorIds());
    }
    Map<String,Integer> durableBudget(){return MAX_DURABLE;}
    private int newCount(TestDatabaseSnapshot.Snapshot after,String table){
        var ids=new HashSet<>(T03Preservation.ids(after,table));ids.removeAll(T03Preservation.ids(baseline,table));return ids.size();
    }
    private void edit(long id,int count,BigDecimal price,BigDecimal adjustment,Map<String,String> options){
        // Lazy-Details innerhalb der unabhängigen Read-TX materialisieren, ohne
        // eine äußere Transaktion um die produktive Schreibgrenze zu legen.
        var r=read(()->{var row=em.find(MangaReihe.class,id);row.getMangaDetails().getSammelbaende().getId();return row;});var d=r.getMangaDetails();
        assertTrue(library.updateMangaReihe(id,r.getMangaIndex(),statusId,publisherId,typeId,formatId,r.getTitel(),count,price,
                r.getIstVergriffen(),r.getIstEbayPreis(),d.getAnilistUrl(),d.getCoverUrl(),d.getSammelbaende().getId(),adjustment,
                d.isIstGelesen(),d.getReread(),options).isPresent());
    }
    private void unchanged(Checked work){var before=read(()->TestDatabaseSnapshot.capture(em));try{work.run();}catch(Exception failure){throw new IllegalStateException("Kontrollierte Anfrage fehlgeschlagen",failure);}
        T03Preservation.verify(before,read(()->TestDatabaseSnapshot.capture(em)),Map.of());}
    private <T>T read(java.util.function.Supplier<T> work){assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setTimeout(20);return tx.execute(s->{em.clear();return work.get();});}
    private <T>T readOrCurrent(java.util.function.Supplier<T> work){return TransactionSynchronizationManager.isActualTransactionActive()?work.get():read(work);}
    private interface Checked{void run()throws Exception;}
    private static class SyntheticFailure extends RuntimeException{}
    private static class RollbackFixture extends RuntimeException{}
}
