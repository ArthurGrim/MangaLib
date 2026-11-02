package de.mangalib.watchdogmodel;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Einmaliger Besitzer einer eigenen synthetischen Instanz, keine PID-Suche/Jobabstraktion. */
final class OwnedHostAttempt {
    interface Child {
        boolean alive();
        boolean awaitEnd(long nanos) throws Exception;
        int exitCode();
        void stop();
        void forceStop();
        boolean closeResources();
    }
    interface Exchange {
        boolean handshake(long nanos) throws Exception;
        boolean completed(long nanos) throws Exception;
        boolean close(long nanos) throws Exception;
    }
    interface Start {
        Child start() throws Exception;
        Exchange exchange(Child child) throws Exception;
    }
    record Result(boolean protocolComplete, boolean processEnded, Integer exitCode,
                  boolean resourcesClosed, boolean abortRequested, boolean forceRequested, boolean stopRequired) { }
    static final long HANDSHAKE = TimeUnit.SECONDS.toNanos(2), TOTAL = TimeUnit.SECONDS.toNanos(6),
            GRACE = TimeUnit.SECONDS.toNanos(1);
    private final LongSupplier clock;
    private boolean consumed;
    OwnedHostAttempt(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    Result run(Start starter) {
        if (consumed) throw new IllegalStateException("attempt-consumed");
        consumed = true; long start = clock.getAsLong();
        Child child = null; Exchange io = null;
        boolean protocol = false, ended = false, resources = false, abort = false, force = false;
        Integer exit = null;
        try {
            child = starter.start();
            if (remaining(start, TOTAL) > 0) {
                io = starter.exchange(child);
                protocol = io.handshake(remaining(start, HANDSHAKE)) && remaining(start, HANDSHAKE) > 0
                        && io.completed(remaining(start, TOTAL)) && remaining(start, TOTAL) > 0;
            }
            if (protocol && child.awaitEnd(remaining(start, TOTAL)) && !child.alive()) ended = true;
        } catch (Exception ignored) { /* Nur strukturierte Statuswerte, keine fremden Fehlertexte. */ }
        finally {
            if (child != null) {
                try {
                    if (!ended && child.alive()) {
                        abort = true; child.stop();
                        long grace = clock.getAsLong();
                        ended = child.awaitEnd(remaining(grace, GRACE)) && !child.alive();
                        if (!ended && child.alive()) {
                            force = true; child.forceStop(); grace = clock.getAsLong();
                            ended = child.awaitEnd(remaining(grace, GRACE)) && !child.alive();
                        }
                    }
                    if (!child.alive()) { ended = true; exit = child.exitCode(); }
                } catch (Exception ignored) { ended = false; }
            }
            try {
                boolean ioClosed = io == null;
                if (io != null) { long grace = clock.getAsLong(); ioClosed = io.close(remaining(grace, GRACE)); }
                // Kein synchrones Schließen unter noch laufenden Reader-/Writercallbacks.
                if (ended && ioClosed) resources = child.closeResources();
            } catch (Exception ignored) { resources = false; }
        }
        boolean success = protocol && ended && Integer.valueOf(0).equals(exit) && resources && !abort;
        return new Result(protocol, ended, exit, resources, abort, force, !success);
    }

    private long remaining(long start, long budget) { return Math.max(0, budget - (clock.getAsLong() - start)); }
}
