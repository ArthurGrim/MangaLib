package de.mangalib.windowsadapter;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ausschließlich isolierte Ladevorbereitung; kein main, Starttask oder automatischer
 * DLL-Aufruf bei Klasseninitialisierung. Laden ist KEINE Freigabe eines OS-Versuchs.
 * Insbesondere ist noch kein unabhängiger Cleanup für blockierte Aufrufe vorhanden.
 */
final class Win64NativeBindings {
    interface Symbols extends Win64KernelCalls.Kernel32, Win64ProcessKernelCalls.ProcessFunctions { }
    interface StdioSymbols extends Symbols, Win64StdioChannels.Functions { }
    // Separates künftiges Profil: vorhandene 18-/23-Exportverträge nicht verändern.
    interface TransferSymbols extends StdioSymbols, Win64TransferBindings.CopyFunctions { }
    interface ComparisonSymbols extends Win64TransferBindings.ComparisonFunctions { }
    static final List<String> TRANSFER_EXPORTS = Win64DllPlan.TRANSFER;
    static final List<String> STDIO_EXPORTS = Win64DllPlan.STDIO;
    // Dieser isolierte Versuch gilt für den bisher dokumentierten C:\Windows-Host.
    // Eine umgebogene SystemRoot-Variable darf keine andere DLL als vertrauenswürdig ausgeben.
    private static final String SYSTEM_ROOT = "C:\\Windows";

    static final List<String> EXPORTS = Win64DllPlan.CORE;
    static final List<String> OVERRIDES = List.of("jna.boot.library.path", "jna.library.path",
            "jna.platform.library.path", "jna.tmpdir", "jna.nounpack", "jna.noclasspath");

    /** Fakten werden explizit erhoben, niemals durch einen statischen Initialisierer. */
    record Abi(int pointerBytes, int longBytes, int wcharBytes, String jnaVersion) { }
    record Host(String os, String arch, int pointerBytes, int longBytes, int wcharBytes,
                int javaFeature, String jnaVersion, String systemRoot, Map<String, String> properties) {
        Host { properties = Map.copyOf(properties); }
        static Host current() {
            var properties = new LinkedHashMap<String, String>();
            for (String key : OVERRIDES) if (System.getProperty(key) != null) properties.put(key, System.getProperty(key));
            if (System.getProperty("jna.nosys") != null) properties.put("jna.nosys", System.getProperty("jna.nosys"));
            return detect(System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.version().feature(),
                    System.getenv("SystemRoot"), properties,
                    () -> new Abi(Native.POINTER_SIZE, Native.LONG_SIZE, Native.WCHAR_SIZE, Native.VERSION));
        }
        static Host detect(String os, String arch, int java, String root, Map<String, String> properties,
                           Supplier<Abi> readAbi) {
            // Insbesondere Ladepfad-Overrides VOR der ersten Native-Klasseninitialisierung ablehnen.
            configuration(os, arch, java, root, properties);
            Abi abi;
            try { abi = Objects.requireNonNull(readAbi.get()); }
            catch (RuntimeException | LinkageError error) { throw new LoadFailure("abi-dispatch", error); }
            var host = new Host(os, arch, abi.pointerBytes(), abi.longBytes(), abi.wcharBytes(), java,
                    abi.jnaVersion(), root, properties);
            host.checkedDll(); return host;
        }
        String checkedDll() {
            configuration(os, arch, javaFeature, systemRoot, properties);
            if (pointerBytes != 8 || longBytes != 4 || wcharBytes != 2 || !"5.17.0".equals(jnaVersion))
                throw new IllegalArgumentException("Expected Windows x64 / Java 21 / JNA 5.17.0 ABI");
            return SYSTEM_ROOT + "\\System32\\kernel32.dll";
        }
        private static void configuration(String os, String arch, int java, String systemRoot,
                                          Map<String, String> properties) {
            if (os == null || !os.startsWith("Windows ") || !("amd64".equalsIgnoreCase(arch)
                    || "x86_64".equalsIgnoreCase(arch)) || java != 21)
                throw new IllegalArgumentException("Expected Windows x64 / Java 21");
            if (!"true".equals(properties.get("jna.nosys"))
                    || OVERRIDES.stream().anyMatch(properties::containsKey)
                    || properties.keySet().stream().anyMatch(key -> !key.equals("jna.nosys")))
                throw new IllegalArgumentException("Unexpected native loading override");
            Win64LaunchSpec.requirePath(systemRoot);
            if (!SYSTEM_ROOT.equalsIgnoreCase(systemRoot))
                throw new IllegalArgumentException("SystemRoot differs from the fixed experiment host");
        }
    }

    interface Module {
        void resolve(String name);
        Symbols bind();
        int lastError();
        void closeAfterFailedPreparation();
    }
    interface Loader { Module open(String absoluteDll, Map<String, ?> options); }
    interface StdioModule extends Module { @Override StdioSymbols bind(); }
    interface StdioLoader { StdioModule open(String absoluteDll, Map<String, ?> options); }
    interface TransferModule extends StdioModule { @Override TransferSymbols bind(); }
    interface TransferLoader { TransferModule open(String absoluteDll, Map<String, ?> options); }
    interface ComparisonModule {
        void resolve(String name);
        ComparisonSymbols bind();
        void closeAfterFailedPreparation();
    }
    interface ComparisonLoader { ComparisonModule open(String absoluteDll, Map<String, ?> options); }
    record TransferPrepared(StdioPrepared base, TransferSymbols copies, ComparisonSymbols comparison) {
        Win64TransferBindings raw() { return new Win64TransferBindings(base.process(),base.stdio(),copies,comparison); }
    }
    /** Beide festen Module vollständig prüfen, bevor irgendeine Prozessoperation möglich wird. */
    static TransferPrepared prepareTransferModules(Host host, TransferLoader loader, ComparisonLoader comparisonLoader, boolean sc) {
        if (!Objects.requireNonNull(host).checkedDll().equals(Win64DllPlan.KERNEL32))
            throw new IllegalArgumentException("transfer-dll-plan");
        return Win64DllPlan.prepare(sc,new Win64DllPlan.Loader<>() {
            TransferModule copies;
            ComparisonModule comparison;
            public Win64DllPlan.Module open(String dll) {
                if (Win64DllPlan.KERNEL32.equals(dll)) {
                    copies=Objects.requireNonNull(loader.open(dll,loadOptions()));
                    return new Win64DllPlan.Module() {
                        public void resolve(String name) { copies.resolve(name); }
                        public void closeAfterFailure() { copies.closeAfterFailedPreparation(); }
                    };
                }
                if (!Win64DllPlan.KERNEL_BASE.equals(dll)) throw new IllegalArgumentException("transfer-dll-plan");
                comparison=Objects.requireNonNull(comparisonLoader.open(dll,loadOptions()));
                return new Win64DllPlan.Module() {
                    public void resolve(String name) { comparison.resolve(name); }
                    public void closeAfterFailure() { comparison.closeAfterFailedPreparation(); }
                };
            }
            public TransferPrepared bind(Win64DllPlan.Module k,Win64DllPlan.Module b) {
                TransferSymbols symbols=Objects.requireNonNull(copies.bind());
                ComparisonSymbols compare=Objects.requireNonNull(comparison.bind());
                var base=prepareStdio(host,(dll,options)->new StdioModule() {
                    public void resolve(String name) { copies.resolve(name); }
                    public StdioSymbols bind() { return symbols; }
                    public int lastError() { return copies.lastError(); }
                    // Der äußere Ladeplan besitzt beide Module und schließt bei Fehler genau einmal.
                    public void closeAfterFailedPreparation() { }
                });
                return new TransferPrepared(base,symbols,compare);
            }
        });
    }
    static Win64TransferBindings prepareTransfer(Host host, TransferLoader loader, ComparisonLoader comparisonLoader) {
        return prepareTransferModules(host,loader,comparisonLoader,false).raw();
    }
    record StdioPrepared(Win64ProcessKernelCalls.ProcessRawApi process, Win64StdioChannels.Api stdio) { }

    /** Separater Opt-in: alte 18-Symbol-Bindung bleibt unverändert. Keine echte Ladefactory. */
    static StdioPrepared prepareStdio(Host host, StdioLoader loader) {
        String dll = Objects.requireNonNull(host).checkedDll();
        StdioModule module = null;
        try {
            Win64Layouts.verify(); Win64ProcessLayouts.verify();
            module = Objects.requireNonNull(loader.open(dll, loadOptions()));
            for (String symbol : EXPORTS) module.resolve(symbol);
            for (String symbol : STDIO_EXPORTS) module.resolve(symbol);
            StdioSymbols symbols = Objects.requireNonNull(module.bind());
            StdioModule held = module;
            var process = new Win64ProcessKernelCalls.ProcessBridge(new JobCalls(module, symbols), symbols);
            Win64StdioChannels.Api stdio = new Win64StdioChannels.Api() {
                public boolean CreatePipe(Pointer r, Pointer w, Pointer s, int n) { return symbols.CreatePipe(r, w, s, n); }
                public boolean SetHandleInformation(Pointer p, int m, int f) { return symbols.SetHandleInformation(p, m, f); }
                public boolean GetHandleInformation(Pointer p, IntByReference f) { return symbols.GetHandleInformation(p, f); }
                public boolean ReadFile(Pointer p, Pointer b, int n, IntByReference c, Pointer o) { return symbols.ReadFile(p, b, n, c, o); }
                public boolean WriteFile(Pointer p, Pointer b, int n, IntByReference c, Pointer o) { return symbols.WriteFile(p, b, n, c, o); }
                public boolean CloseHandle(Pointer p) { return symbols.CloseHandle(p); }
                public boolean UpdateProcThreadAttribute(Pointer l, int f, long a, Pointer v, long n, Pointer p, Pointer r) {
                    return symbols.UpdateProcThreadAttribute(l, f, a, v, n, p, r);
                }
                public int error() { return held.lastError(); }
            };
            return new StdioPrepared(process, stdio);
        } catch (RuntimeException | LinkageError failure) {
            if (module != null) try { module.closeAfterFailedPreparation(); } catch (RuntimeException | LinkageError secondary) { failure.addSuppressed(secondary); }
            throw new LoadFailure("stdio-preparation", failure);
        }
    }

    static final class LoadFailure extends IllegalStateException {
        final String phase;
        LoadFailure(String phase, Throwable cause) { super("Native binding preparation failed: " + phase, cause); this.phase = phase; }
    }

    /**
     * Eager Symbolprüfung ohne Job-/Prozessoperation. DLL-Laden/Symbolauflösen selbst
     * sind native Ladevorgänge. Die DLL bleibt für die Lebenszeit des Prüfhosts
     * referenziert: keine Unload-Funktion, die unter einem laufenden Aufruf schließen könnte.
     */
    static Win64ProcessKernelCalls.ProcessRawApi prepare(Host host, Loader loader) {
        Objects.requireNonNull(loader);
        String dll = Objects.requireNonNull(host).checkedDll();
        Win64Layouts.verify(); Win64ProcessLayouts.verify();
        Module module = null;
        String phase = "load";
        try {
            module = Objects.requireNonNull(loader.open(dll, loadOptions()), "Missing native module");
            phase = "exports";
            for (String name : EXPORTS) module.resolve(name);
            phase = "bind";
            Symbols symbols = Objects.requireNonNull(module.bind(), "Missing native proxy");
            return new Win64ProcessKernelCalls.ProcessBridge(new JobCalls(module, symbols), symbols);
        } catch (RuntimeException | LinkageError error) {
            var failure = new LoadFailure(phase, error);
            if (module != null) {
                try { module.closeAfterFailedPreparation(); }
                catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure; // Kein Retry, kein zweiter DLL-Name oder Startfallback.
        }
    }

    private static Map<String, ?> loadOptions() {
        // Beide JNA-Zugriffe verwenden denselben ClassLoader-/Optionsschlüssel.
        return Map.of(Library.OPTION_CALLING_CONVENTION, Function.ALT_CONVENTION,
                Library.OPTION_CLASSLOADER, Symbols.class.getClassLoader());
    }

    private record JobCalls(Module module, Symbols symbols) implements Win64KernelCalls.RawApi {
        public Pointer create() { return symbols.CreateJobObjectW(null, null); }
        public boolean set(Pointer j, int type, Pointer p, int size) { return symbols.SetInformationJobObject(j, type, p, size); }
        public boolean query(Pointer j, int type, Pointer p, int size, IntByReference returned) {
            return symbols.QueryInformationJobObject(j, type, p, size, returned);
        }
        public boolean close(Pointer handle) { return symbols.CloseHandle(handle); }
        // JNAs gesicherter Threadfehler; GetLastError nicht noch einmal per DLL aufrufen.
        public int error() { return module.lastError(); }
    }

    /**
     * Reale Ladeimplementierung nur für einen später freizugebenden Prüfhost.
     * B-ADAPTER-4-Tests rufen open ausschließlich an Loader-/Module-Fakes auf.
     */
    static final class JnaLoader implements Loader {
        @Override public Module open(String absoluteDll, Map<String, ?> options) {
            if (!Host.current().checkedDll().equals(absoluteDll) || !loadOptions().equals(options))
                throw new IllegalArgumentException("Native loader contract mismatch");
            Path dll = Path.of(absoluteDll);
            try {
                if (!dll.isAbsolute() || !Files.isRegularFile(dll, LinkOption.NOFOLLOW_LINKS)
                        || !dll.toRealPath().equals(dll)) throw new IOException("Noncanonical system DLL");
            } catch (IOException error) { throw new IllegalStateException("System DLL preflight failed", error); }
            // Absoluter SystemRoot/System32-Pfad, keine Bare-name-/CWD-Suchroute.
            NativeLibrary library = NativeLibrary.getInstance(dll.toString(), options);
            return new Module() {
                private Symbols symbols;
                public void resolve(String name) { library.getFunction(name, Function.ALT_CONVENTION); }
                public Symbols bind() {
                    if (symbols != null) throw new IllegalStateException("Native module already bound");
                    symbols = Native.load(dll.toString(), Symbols.class, options);
                    return symbols;
                }
                public int lastError() { return Native.getLastError(); }
                public void closeAfterFailedPreparation() { library.close(); }
            };
        }
    }

    /** Expliziter kalter Transferloader. Neue Faketests laden KEINE DLL. */
    static final class JnaTransferLoader implements TransferLoader {
        public TransferModule open(String absoluteDll, Map<String, ?> options) {
            if (!Host.current().checkedDll().equals(absoluteDll) || !loadOptions().equals(options))
                throw new IllegalArgumentException("Transfer native loader contract mismatch");
            Path dll = Path.of(absoluteDll);
            try {
                if (!dll.isAbsolute() || !Files.isRegularFile(dll,LinkOption.NOFOLLOW_LINKS)
                        || !dll.toRealPath().equals(dll)) throw new IOException("Noncanonical system DLL");
            } catch (IOException failure) { throw new IllegalStateException("Transfer DLL preflight failed",failure); }
            NativeLibrary library = NativeLibrary.getInstance(dll.toString(),options);
            return new TransferModule() {
                private TransferSymbols bound;
                public void resolve(String name) { library.getFunction(name,Function.ALT_CONVENTION); }
                public TransferSymbols bind() {
                    if (bound != null) throw new IllegalStateException("Transfer module already bound");
                    return bound = Native.load(dll.toString(),TransferSymbols.class,options);
                }
                public int lastError() { return Native.getLastError(); }
                public void closeAfterFailedPreparation() { library.close(); }
            };
        }
    }

    /** CompareObjectHandles gehört ausschließlich zu KernelBase; kein Suchpfad-/Kernel32-Fallback. */
    static final class JnaComparisonLoader implements ComparisonLoader {
        public ComparisonModule open(String absoluteDll, Map<String, ?> options) {
            Host.current().checkedDll();
            if (!Win64DllPlan.KERNEL_BASE.equals(absoluteDll) || !loadOptions().equals(options))
                throw new IllegalArgumentException("Comparison native loader contract mismatch");
            Path dll=Path.of(absoluteDll);
            try {
                if (!Files.isRegularFile(dll,LinkOption.NOFOLLOW_LINKS) || !dll.toRealPath().equals(dll))
                    throw new IOException("Noncanonical comparison DLL");
            } catch(IOException failure) { throw new IllegalStateException("Comparison DLL preflight failed",failure); }
            NativeLibrary library=NativeLibrary.getInstance(absoluteDll,options);
            return new ComparisonModule() {
                ComparisonSymbols bound;
                public void resolve(String name) { library.getFunction(name,Function.ALT_CONVENTION); }
                public ComparisonSymbols bind() {
                    if(bound!=null)throw new IllegalStateException("Comparison module already bound");
                    return bound=Native.load(absoluteDll,ComparisonSymbols.class,options);
                }
                public void closeAfterFailedPreparation() { library.close(); }
            };
        }
    }

    /** Separater expliziter Loader für das stdio-Profil; in diesem Auftrag niemals aufrufen. */
    static final class JnaStdioLoader implements StdioLoader {
        public StdioModule open(String absoluteDll, Map<String, ?> options) {
            if (!Host.current().checkedDll().equals(absoluteDll) || !loadOptions().equals(options))
                throw new IllegalArgumentException("Stdio native loader contract mismatch");
            Path dll = Path.of(absoluteDll);
            try {
                if (!dll.isAbsolute() || !Files.isRegularFile(dll, LinkOption.NOFOLLOW_LINKS)
                        || !dll.toRealPath().equals(dll)) throw new IOException("Noncanonical system DLL");
            } catch (IOException error) { throw new IllegalStateException("Stdio DLL preflight failed", error); }
            NativeLibrary library = NativeLibrary.getInstance(dll.toString(), options);
            return new StdioModule() {
                private StdioSymbols symbols;
                public void resolve(String name) { library.getFunction(name, Function.ALT_CONVENTION); }
                public StdioSymbols bind() {
                    if (symbols != null) throw new IllegalStateException("Stdio native module already bound");
                    symbols = Native.load(dll.toString(), StdioSymbols.class, options); return symbols;
                }
                public int lastError() { return Native.getLastError(); }
                public void closeAfterFailedPreparation() { library.close(); }
            };
        }
    }
}
