package de.mangalib.windowsadapter;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Der tatsächlich verwendete Ladeplan mit reinen Modul-Fakes, ohne JNA/OS. */
class ScLoadPreparationTest {
    static final class Loader implements Win64DllPlan.Loader<String> {
        final Map<String,List<String>> resolved=new LinkedHashMap<>();
        final List<String> closed=new ArrayList<>();
        String missing="", openFailure="";
        boolean wrongOwner, bindFailure, closeFailure;
        int binds;
        public Win64DllPlan.Module open(String dll) {
            assertTrue(List.of(Win64DllPlan.KERNEL32,Win64DllPlan.KERNEL_BASE).contains(dll));
            assertFalse(resolved.containsKey(dll),"Kein zweiter Ladeversuch");
            if(dll.equals(openFailure))throw new UnsatisfiedLinkError("secret bootstrap/path 0x123456");
            var names=new ArrayList<String>();resolved.put(dll,names);
            return new Win64DllPlan.Module() {
                public void resolve(String name) {
                    names.add(name);
                    if(name.equals(missing) || (wrongOwner && dll.equals(Win64DllPlan.KERNEL_BASE)))
                        throw new UnsatisfiedLinkError("private IPC/rawhandle=0x123456 " + name);
                    assertEquals(name.equals("CompareObjectHandles")?Win64DllPlan.KERNEL_BASE:Win64DllPlan.KERNEL32,dll);
                }
                public void closeAfterFailure() {
                    assertFalse(closed.contains(dll));closed.add(dll);
                    if(closeFailure)throw new IllegalStateException("private cleanup detail");
                }
            };
        }
        public String bind(Win64DllPlan.Module a,Win64DllPlan.Module b) {
            binds++;
            assertEquals(List.of("CompareObjectHandles"),resolved.get(Win64DllPlan.KERNEL_BASE));
            if(bindFailure)throw new UnsatisfiedLinkError("private bind failure");
            return "prepared";
        }
    }
    @Test void scChecksExactlyTwentySevenKernel32AndOneKernelBaseExportsBeforeBinding() {
        var loader=new Loader();assertEquals("prepared",Win64DllPlan.prepare(true,loader));
        assertEquals(27,loader.resolved.get(Win64DllPlan.KERNEL32).size());
        assertEquals(27,new HashSet<>(loader.resolved.get(Win64DllPlan.KERNEL32)).size());
        assertFalse(loader.resolved.get(Win64DllPlan.KERNEL32).contains("CompareObjectHandles"));
        assertTrue(loader.closed.isEmpty());assertEquals(1,loader.binds);
    }
    @Test void transferRetainsExistingTwentySixExportsButSeparatesComparisonOwner() {
        var loader=new Loader();Win64DllPlan.prepare(false,loader);
        assertEquals(25,loader.resolved.get(Win64DllPlan.KERNEL32).size());
        assertFalse(loader.resolved.get(Win64DllPlan.KERNEL32).contains("GetStdHandle"));
    }
    @ParameterizedTest @ValueSource(strings={"CompareObjectHandles","DuplicateHandle","GetCurrentProcessId","CreateJobObjectW","SetInformationJobObject","QueryInformationJobObject","TerminateJobObject","CreateProcessW","TerminateProcess","GetStdHandle","ReadFile","WriteFile"})
    void anyMissingRequiredExportStopsBeforeBindingOrCustodianOrJobCreation(String name) {
        var loader=new Loader();loader.missing=name;
        AtomicInteger custodians=new AtomicInteger(),jobs=new AtomicInteger();
        assertThrows(UnsatisfiedLinkError.class,()->{
            Win64DllPlan.prepare(true,loader);
            custodians.incrementAndGet();jobs.incrementAndGet();
        });
        assertEquals(0,loader.binds);assertEquals(0,custodians.get());assertEquals(0,jobs.get());
        assertEquals(loader.resolved.size(),loader.closed.size());
    }
    @Test void exportPresentOnlyInKernel32CannotSubstituteMissingKernelBaseExport() {
        var loader=new Loader();loader.wrongOwner=true;
        assertThrows(UnsatisfiedLinkError.class,()->Win64DllPlan.prepare(true,loader));
        assertEquals(0,loader.binds);assertEquals(2,loader.closed.size());
        assertEquals(List.of(Win64DllPlan.KERNEL32,Win64DllPlan.KERNEL_BASE),new ArrayList<>(loader.resolved.keySet()));
    }
    @ParameterizedTest @ValueSource(strings={Win64DllPlan.KERNEL32,Win64DllPlan.KERNEL_BASE})
    void moduleOpenFailureNeverRetriesAnotherPath(String dll) {
        var loader=new Loader();loader.openFailure=dll;
        assertThrows(UnsatisfiedLinkError.class,()->Win64DllPlan.prepare(true,loader));
        assertEquals(0,loader.binds);assertEquals(loader.resolved.size(),loader.closed.size());
    }
    @Test void bindFailureClosesBothOwnModulesOnceWithoutClaimingCleanupSuccess() {
        var loader=new Loader();loader.bindFailure=true;loader.closeFailure=true;
        var failure=assertThrows(UnsatisfiedLinkError.class,()->Win64DllPlan.prepare(true,loader));
        assertEquals(2,failure.getSuppressed().length);assertEquals(2,loader.closed.size());assertEquals(1,loader.binds);
    }
    @ParameterizedTest @ValueSource(strings={"linkage","reflection","runtime","null-driver"})
    void entryInitializationFailureReportsBoundedStopAndDoesNotEnterEitherRole(String kind) {
        List<String> diagnostics=new ArrayList<>();AtomicInteger factories=new AtomicInteger();
        int result=ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.SUPERVISOR,new String[]{ScNativeManifest.PROFILE},()->{
            factories.incrementAndGet();
            return switch(kind) {
                case "linkage" -> throw new UnsatisfiedLinkError("SECRET/password/IPC 0x123456");
                case "reflection" -> throw new InvocationTargetException(new UnsatisfiedLinkError("SECRET"));
                case "runtime" -> throw new IllegalStateException("SECRET");
                default -> null;
            };
        },diagnostics::add);
        assertEquals(78,result);assertEquals(1,factories.get());assertEquals(1,diagnostics.size());
        assertTrue(diagnostics.getFirst().length()<=192);assertFalse(diagnostics.getFirst().contains("SECRET"));
        assertTrue(diagnostics.getFirst().contains("end=UNCONFIRMED resources=UNCONFIRMED"));
    }
    @Test void malformedProfileCannotInitializeLoaderOrEmitRawDiagnostics() {
        assertEquals(64,ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.SUPERVISOR,new String[]{"--other"},
                ()->{fail("Factory verboten");return null;},s->fail("Keine Diagnose mit freien Argumenten")));
    }
    @Test void diagnosticSinkFailureDoesNotRetryFactoryOrChangeStopIntoSuccess() {
        AtomicInteger calls=new AtomicInteger();
        assertEquals(78,ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.SUPERVISOR,new String[]{ScNativeManifest.PROFILE},
                ()->{calls.incrementAndGet();throw new UnsatisfiedLinkError();},s->{throw new IllegalStateException();}));
        assertEquals(1,calls.get());
    }
    @Test void allPhaseReportsAreFixedBoundedAndNeverAssertProcessOrResourceEnd() {
        for(var phase:ScBootstrapDiagnostics.Phase.values()) {
            String report=ScBootstrapDiagnostics.stop(ScExecutablePlatform.Role.SUPERVISOR,phase,
                    new java.io.IOException("SECRET".repeat(10000)));
            assertTrue(report.length()<=192);assertFalse(report.contains("SECRET"));
            assertTrue(report.contains("phase="+phase.name()));assertTrue(report.endsWith("end=UNCONFIRMED resources=UNCONFIRMED"));
        }
    }
    @Test void nativeWiringUsesSplitComparisonAndLoadsBeforeIntentOrStartProvider() throws Exception {
        // Ergänzende Verdrahtungsprüfung, kein Ersatz für spätere native OS-Abnahme.
        String calls=Files.readString(Path.of("src/scNative/java/de/mangalib/windowsadapter/Win64ScCalls.java"));
        assertTrue(calls.contains("prepareTransferModules(host"));assertTrue(calls.contains("new Win64NativeBindings.JnaComparisonLoader()"));
        assertTrue(calls.contains("bindings.symbols(),bindings.comparison()"));
        String driver=Files.readString(Path.of("src/scNative/java/de/mangalib/windowsadapter/ScNativeDriver.java"));
        String supervisor=driver.substring(driver.indexOf("public int runSupervisor()"),driver.indexOf("public int runCustodian()"));
        assertTrue(supervisor.indexOf("Win64ScCalls.load()")<supervisor.indexOf("StartIntentStore.create("));
        assertTrue(supervisor.indexOf("Win64ScCalls.load()")<supervisor.indexOf("new ScStartProvider("));
        assertTrue(supervisor.contains("Phase.NATIVE_LOAD"));assertTrue(supervisor.contains("ScBootstrapDiagnostics.emit("));
    }
    @Test void consoleStrategyRemainsNoWindowWithExplicitPipesAndNoBreakawayOrNewConsole() throws Exception {
        String layout=Files.readString(Path.of("src/jobAdapter/java/de/mangalib/windowsadapter/Win64ProcessLayouts.java"));
        assertTrue(layout.contains("START_FLAGS = SUSPENDED | EXTENDED | UNICODE_ENV | NO_WINDOW"));
        String calls=Files.readString(Path.of("src/scNative/java/de/mangalib/windowsadapter/Win64ScCalls.java"));
        assertTrue(calls.contains("START_FLAGS,environment"));assertTrue(calls.contains("pipes.prepareCustodian("));
    }
    @Test void newChecksHaveNoJnaOrNativeRuntime() {
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("de.mangalib.windowsadapter.ScNativeDriver"));
    }
}
