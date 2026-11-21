package de.mangalib.diagnostics;

import org.slf4j.LoggerFactory;

/** Ausschließlich feste Ereignisnamen; keine Fachwerte oder fremden Fehlertexte. */
public final class DiagnosticEvents {
    private DiagnosticEvents() {}
    public enum Action { STORAGE, LEGACY_VALUE, IMPORT_SERIES, IMPORT_VOLUME, STARTUP, SHUTDOWN_REQUESTED }
    public static void started(Action action) {
        LoggerFactory.getLogger(DiagnosticEvents.class).info("event=operation_started action={}", action);
    }
    public static void failure(Action action, Throwable failure) {
        var safe = new Exception("Operation failed");
        safe.setStackTrace(failure.getStackTrace());
        LoggerFactory.getLogger(DiagnosticEvents.class).error("event=operation_failed action={}", action, safe);
    }
    public static void completed(Action action, long start) {
        LoggerFactory.getLogger(DiagnosticEvents.class).info("event=operation_complete action={} durationMs={}",
                action, (System.nanoTime() - start) / 1_000_000);
    }
}
