package de.mangalib.diagnostics;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Lokale Betriebsereignisse; Shutdown-Anforderung behauptet keinen Worker-Cleanup. */
@Component
public class LifecycleDiagnostics {
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { event(DiagnosticEvents.Action.STARTUP); }
    @EventListener(ContextClosedEvent.class)
    public void closing() { event(DiagnosticEvents.Action.SHUTDOWN_REQUESTED); }
    private void event(DiagnosticEvents.Action action) {
        org.slf4j.LoggerFactory.getLogger(DiagnosticEvents.class).info("event=lifecycle action={}", action);
    }
}
