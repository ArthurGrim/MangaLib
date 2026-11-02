package de.mangalib.watchdogmodel;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Ausführbarer Versuch ausschließlich durch die getrennte Einmal-Testtask. */
final class CommunicationProbe {
    record Evidence(OwnedHostAttempt.Result result, long pid, int stderrBytes, boolean stderrEnded,
                    String session, String javaHash, String classHash, Path marker) { }

    static Evidence run(CommunicationLaunchPlan.Plan plan) throws Exception {
        Path marker = CommunicationLaunchPlan.consume(plan);
        final RealChild[] owned = {null};
        final RealExchange[] exchange = {null};
        OwnedHostAttempt.Result result = new OwnedHostAttempt(System::nanoTime).run(new OwnedHostAttempt.Start() {
            public OwnedHostAttempt.Child start() throws Exception {
                owned[0] = new RealChild(CommunicationLaunchPlan.start(plan));
                return owned[0];
            }
            public OwnedHostAttempt.Exchange exchange(OwnedHostAttempt.Child child) {
                exchange[0] = new RealExchange(owned[0].process, plan);
                return exchange[0];
            }
        });
        Evidence evidence = new Evidence(result, owned[0] == null ? -1 : owned[0].process.pid(),
                exchange[0] == null ? 0 : exchange[0].stderrBytes,
                exchange[0] != null && exchange[0].stderrEnded,
                plan.session().toString(), plan.javaHash(), plan.classHash(), marker);
        // Separates Ergebnis, Marker niemals löschen oder für Wiederholung freigeben.
        String report = "status=" + (result.stopRequired() ? "STOP" : "PASS") + "\n" + evidence + "\n";
        try (var file = FileChannel.open(plan.directory().resolve("result.txt"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var bytes = StandardCharsets.UTF_8.encode(report);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
        return evidence;
    }

    private static final class RealChild implements OwnedHostAttempt.Child {
        final Process process;
        RealChild(Process process) { this.process = process; }
        public boolean alive() { return process.isAlive(); }
        public boolean awaitEnd(long nanos) throws InterruptedException {
            return process.waitFor(Math.max(0, nanos), TimeUnit.NANOSECONDS);
        }
        public int exitCode() { return process.exitValue(); }
        // Ausschließlich das direkt vom eigenen Start zurückgegebene Process-Objekt.
        public void stop() { process.destroy(); }
        public void forceStop() { process.destroyForcibly(); }
        public boolean closeResources() {
            boolean closed = true;
            for (AutoCloseable stream : new AutoCloseable[]{process.getOutputStream(), process.getInputStream(), process.getErrorStream()}) {
                try { stream.close(); } catch (Exception ignored) { closed = false; }
            }
            return closed;
        }
    }

    private static final class RealExchange implements OwnedHostAttempt.Exchange {
        final CompletableFuture<Boolean> handshake = new CompletableFuture<>();
        final FutureTask<Boolean> io;
        final Thread ioThread, stderrThread;
        volatile int stderrBytes;
        volatile boolean stderrEnded;

        RealExchange(Process process, CommunicationLaunchPlan.Plan plan) {
            io = new FutureTask<>(() -> {
                try {
                    SyntheticProtocol.parent(process.getInputStream(), process.getOutputStream(),
                            new BinaryIpcCodec.Id(plan.session().getMostSignificantBits(), plan.session().getLeastSignificantBits()),
                            () -> handshake.complete(true));
                    return true;
                } finally { handshake.complete(false); }
            });
            ioThread = new Thread(io, "synthetic-parent-io"); ioThread.setDaemon(true);
            stderrThread = new Thread(() -> {
                try {
                    // Nur zählen, nie Rohdiagnosen speichern/ausgeben. Bei Limit Ende des Lesens.
                    while (stderrBytes <= 256) {
                        int value = process.getErrorStream().read();
                        if (value < 0) { stderrEnded = true; break; }
                        stderrBytes++;
                    }
                } catch (IOException ignored) { /* Ohne EOF kein vollständiger Ressourcenbeleg. */ }
            }, "synthetic-parent-stderr"); stderrThread.setDaemon(true);
            stderrThread.start(); ioThread.start();
        }
        public boolean handshake(long nanos) throws Exception {
            return nanos > 0 && handshake.get(nanos, TimeUnit.NANOSECONDS);
        }
        public boolean completed(long nanos) throws Exception {
            return nanos > 0 && io.get(nanos, TimeUnit.NANOSECONDS);
        }
        public boolean close(long nanos) throws InterruptedException {
            long start = System.nanoTime();
            join(ioThread, nanos);
            join(stderrThread, Math.max(0, nanos - (System.nanoTime() - start)));
            return !ioThread.isAlive() && !stderrThread.isAlive() && stderrEnded && stderrBytes <= 256;
        }
        private static void join(Thread thread, long nanos) throws InterruptedException {
            if (nanos > 0) TimeUnit.NANOSECONDS.timedJoin(thread, nanos);
        }
    }
}
