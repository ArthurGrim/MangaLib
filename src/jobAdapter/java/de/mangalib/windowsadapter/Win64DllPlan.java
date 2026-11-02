package de.mangalib.windowsadapter;

import java.util.List;
import java.util.Objects;

/** JDK-only Ladeplan: feste DLL-Zuständigkeit schon vor JNA-Initialisierung sichtbar. */
final class Win64DllPlan {
    static final String KERNEL32 = "C:\\Windows\\System32\\kernel32.dll";
    static final String KERNEL_BASE = "C:\\Windows\\System32\\KernelBase.dll";
    static final List<String> CORE = List.of("CreateJobObjectW", "SetInformationJobObject",
            "QueryInformationJobObject", "CloseHandle", "GetLastError",
            "InitializeProcThreadAttributeList", "UpdateProcThreadAttribute", "DeleteProcThreadAttributeList",
            "CreateProcessW", "ResumeThread", "OpenProcess", "GetProcessId", "QueryFullProcessImageNameW",
            "GetProcessTimes", "GetExitCodeProcess", "WaitForSingleObject", "IsProcessInJob", "TerminateJobObject");
    static final List<String> STDIO = List.of("CreatePipe", "SetHandleInformation", "GetHandleInformation", "ReadFile", "WriteFile");
    static final List<String> TRANSFER = List.of("DuplicateHandle", "GetCurrentProcessId");
    static final List<String> COMPARISON = List.of("CompareObjectHandles");
    static final List<String> SC = List.of("TerminateProcess", "GetStdHandle");
    interface Module { void resolve(String name); void closeAfterFailure(); }
    interface Loader<T> {
        Module open(String absoluteDll);
        T bind(Module kernel32, Module kernelBase);
    }
    private Win64DllPlan() { }

    /** Kein Bind/Prozessstart nach fehlendem Export, kein alternatives Modul, kein Retry. */
    static <T> T prepare(boolean sc, Loader<T> loader) {
        Objects.requireNonNull(loader);
        Module kernel32 = null, kernelBase = null;
        try {
            kernel32 = Objects.requireNonNull(loader.open(KERNEL32));
            for (var names : List.of(CORE, STDIO, TRANSFER, sc ? SC : List.<String>of()))
                for (String name : names) kernel32.resolve(name);
            kernelBase = Objects.requireNonNull(loader.open(KERNEL_BASE));
            for (String name : COMPARISON) kernelBase.resolve(name);
            return Objects.requireNonNull(loader.bind(kernel32, kernelBase));
        } catch (RuntimeException | LinkageError failure) {
            // Nur eigene Modulreferenzen freigeben. Unbekannter Close ist kein Ressourcenendbeleg.
            for (Module module : new Module[]{kernelBase, kernel32}) if (module != null) {
                try { module.closeAfterFailure(); }
                catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
}
