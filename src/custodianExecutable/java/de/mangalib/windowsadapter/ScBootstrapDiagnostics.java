package de.mangalib.windowsadapter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** Nur feste Kategorien, niemals Exceptiontexte/Stacks, Pfade, Rohhandles oder IPC-Inhalte. */
final class ScBootstrapDiagnostics {
    enum Phase { ENTRY, MANIFEST, NATIVE_LOAD, INTENT, BOOTSTRAP, FINISH }
    private ScBootstrapDiagnostics() { }
    static String stop(ScExecutablePlatform.Role role, Phase phase, Throwable error) {
        if(error instanceof InvocationTargetException invocation) error=invocation.getCause();
        String category=error==null ? "INCOMPLETE" : error instanceof LinkageError ? "LINKAGE"
                : error instanceof IOException ? "IO" : error instanceof ReflectiveOperationException ? "BOOTSTRAP"
                : error instanceof IllegalArgumentException ? "CONFIGURATION" : "FAILURE";
        return "SC_STOP_V1 role="+role.name()+" phase="+phase.name()+" category="+category
                +" exit=78 end=UNCONFIRMED resources=UNCONFIRMED";
    }
    static void emit(Consumer<String> output, ScExecutablePlatform.Role role, Phase phase, Throwable error) {
        // Diagnoseausfall darf weder Cleanup ersetzen noch einen zweiten Start bewirken.
        try { output.accept(stop(role,phase,error)); } catch(RuntimeException ignored) { }
    }
}
