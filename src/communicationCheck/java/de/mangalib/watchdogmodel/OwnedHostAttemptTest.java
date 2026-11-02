package de.mangalib.watchdogmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Keine echten Prozesse oder Timer: jeder Warteaufruf ist ein kontrolliertes Fakeereignis. */
class OwnedHostAttemptTest {
    private static final class Fixture implements OwnedHostAttempt.Start, OwnedHostAttempt.Child, OwnedHostAttempt.Exchange {
        long now;
        int starts, stops, forces, closes;
        boolean alive = true, handshake = true, completed = true, resourceEnd = true,
                ioEnd = true, stopWorks = true, forceWorks = true, startFailure, exchangeFailure;
        int exit;
        long handshakeElapsed, resultElapsed, startElapsed;
        final OwnedHostAttempt attempt = new OwnedHostAttempt(() -> now);
        public OwnedHostAttempt.Child start() {
            starts++; now += startElapsed;
            if (startFailure) throw new IllegalStateException("private-start");
            return this;
        }
        public OwnedHostAttempt.Exchange exchange(OwnedHostAttempt.Child child) {
            assertSame(this, child); if (exchangeFailure) throw new IllegalStateException("private-io"); return this;
        }
        public boolean alive() { return alive; }
        public boolean awaitEnd(long nanos) { assertTrue(nanos >= 0); return !alive; }
        public int exitCode() { return exit; }
        public void stop() { stops++; if (stopWorks) alive = false; }
        public void forceStop() { forces++; if (forceWorks) alive = false; }
        public boolean closeResources() { assertFalse(alive); closes++; return resourceEnd; }
        public boolean handshake(long nanos) {
            assertTrue(nanos >= 0 && nanos <= OwnedHostAttempt.HANDSHAKE); now += handshakeElapsed; return handshake;
        }
        public boolean completed(long nanos) {
            assertTrue(nanos > 0 && nanos <= OwnedHostAttempt.TOTAL); now += resultElapsed;
            if (completed) alive = false; return completed;
        }
        public boolean close(long nanos) { assertTrue(nanos > 0 && nanos <= OwnedHostAttempt.GRACE); return ioEnd; }
        OwnedHostAttempt.Result run() { return attempt.run(this); }
    }

    @Test void normalEndSeparatelyConfirmsProtocolProcessExitAndResources() {
        Fixture f = new Fixture(); var result = f.run();
        assertTrue(result.protocolComplete()); assertTrue(result.processEnded()); assertEquals(0, result.exitCode());
        assertTrue(result.resourcesClosed()); assertFalse(result.stopRequired());
        assertEquals(0, f.stops); assertEquals(0, f.forces); assertEquals(1, f.closes);
        assertThrows(IllegalStateException.class, f::run); assertEquals(1, f.starts);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void handshakeResultTimeoutCrashAndLateCompletionDoNotCountAsSuccess(int failure) {
        Fixture f = new Fixture();
        switch (failure) {
            case 0 -> f.handshake = false;
            case 1 -> f.completed = false;
            case 2 -> { f.alive = false; f.exit = 2; f.completed = false; }
            case 3 -> f.handshakeElapsed = OwnedHostAttempt.HANDSHAKE;
            default -> f.resultElapsed = OwnedHostAttempt.TOTAL;
        }
        var result = f.run(); assertTrue(result.stopRequired()); assertFalse(result.protocolComplete());
        assertTrue(result.processEnded()); assertTrue(result.resourcesClosed());
        assertTrue(f.stops <= 1); assertTrue(f.forces <= 1);
    }

    @Test void failedCooperativeStopUsesOnlyOwnInstanceOnceThenRequiresConfirmedExit() {
        Fixture f = new Fixture(); f.handshake = false; f.stopWorks = false;
        var result = f.run(); assertTrue(result.abortRequested()); assertTrue(result.forceRequested());
        assertTrue(result.processEnded()); assertTrue(result.stopRequired());
        assertEquals(1, f.stops); assertEquals(1, f.forces); assertEquals(1, f.starts);
    }

    @Test void unconfirmedEndIsStopAndNeverReleasesResourcesOrStartsAgain() {
        Fixture f = new Fixture(); f.handshake = false; f.stopWorks = f.forceWorks = false;
        var result = f.run(); assertFalse(result.processEnded()); assertFalse(result.resourcesClosed());
        assertNull(result.exitCode()); assertTrue(result.stopRequired()); assertEquals(0, f.closes);
        assertThrows(IllegalStateException.class, f::run); assertEquals(1, f.starts);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void failedResourceCallbackOrNonzeroExitIsNotCompleteEvenWithProtocolSuccess(int failure) {
        Fixture f = new Fixture();
        if (failure == 0) f.resourceEnd = false; else if (failure == 1) f.ioEnd = false; else f.exit = 2;
        var result = f.run(); assertTrue(result.protocolComplete()); assertTrue(result.processEnded());
        assertTrue(result.stopRequired());
        if (failure == 1) assertEquals(0, f.closes); // Keine Streams unter aktiven Callbacks schließen.
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void startOrExchangeExceptionNeverRetriesAndDoesNotExposeExceptionText(boolean exchangeFailure) {
        Fixture f = new Fixture(); f.exchangeFailure = exchangeFailure; f.startFailure = !exchangeFailure;
        var result = f.run(); assertTrue(result.stopRequired()); assertFalse(result.toString().contains("private"));
        assertThrows(IllegalStateException.class, f::run); assertEquals(1, f.starts);
        if (exchangeFailure) { assertTrue(result.processEnded()); assertTrue(result.resourcesClosed()); }
    }

    @Test void lateStartReturnStillBelongsToSameAttemptAndIsStoppedWithoutExchange() {
        Fixture f = new Fixture(); f.startElapsed = OwnedHostAttempt.TOTAL;
        var result = f.run(); assertTrue(result.stopRequired()); assertTrue(result.abortRequested());
        assertTrue(result.processEnded()); assertEquals(1, f.starts);
    }
}
