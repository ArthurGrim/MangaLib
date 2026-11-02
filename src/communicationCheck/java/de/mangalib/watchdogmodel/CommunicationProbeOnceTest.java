package de.mangalib.watchdogmodel;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Niemals Teil von test/check: nur explizite Task communicationProbeOnce mit CREATE_NEW-Marker. */
class CommunicationProbeOnceTest {
    @Test void singleAuthorizedStdioAttempt() throws Exception {
        var plan = CommunicationLaunchPlan.inspect(Path.of(System.getProperty("communicationWorkspace")),
                Path.of(System.getProperty("communicationHostClasses")));
        var evidence = CommunicationProbe.run(plan);
        System.out.println(evidence); // Nur strukturierte synthetische IDs/Status, keine Umgebungswerte.
        assertAll(() -> assertTrue(evidence.result().protocolComplete()),
                () -> assertTrue(evidence.result().processEnded()),
                () -> assertEquals(0, evidence.result().exitCode()),
                () -> assertTrue(evidence.result().resourcesClosed()),
                () -> assertFalse(evidence.result().abortRequested()),
                () -> assertFalse(evidence.result().forceRequested()),
                () -> assertFalse(evidence.result().stopRequired()),
                () -> assertTrue(evidence.stderrEnded()), () -> assertEquals(0, evidence.stderrBytes()));
    }
}
