package de.mangalib.watchdogmodel;

import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

/** Nur dedizierter JDK-stdio-Host. Kein Prozessstart, Netzwerk, Dateizugriff oder Framework. */
public final class SyntheticHostMain {
    private SyntheticHostMain() { }

    public static void main(String[] args) {
        // Unabhängiger eigener Endweg auch bei blockiertem stdin/verschwundenem Parent.
        // halt ist ausschließlich auf diese eigene synthetische JVM beschränkt.
        Thread watchdog = new Thread(() -> {
            long started = System.nanoTime();
            long limit = java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() - started < limit) LockSupport.parkNanos(limit - (System.nanoTime() - started));
            Runtime.getRuntime().halt(124);
        }, "synthetic-host-deadline");
        watchdog.setDaemon(true); watchdog.start();
        int exit = 2;
        try {
            if (Runtime.version().feature() != 21 || args.length != 3 || !SyntheticProtocol.PROFILE.equals(args[0]))
                throw new IllegalArgumentException();
            UUID session = UUID.fromString(args[1]);
            long parent = Long.parseLong(args[2]);
            if (ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L) != parent)
                throw new IllegalArgumentException();
            SyntheticProtocol.host(System.in, System.out,
                    new BinaryIpcCodec.Id(session.getMostSignificantBits(), session.getLeastSignificantBits()));
            exit = 0;
        } catch (Exception failure) {
            // Keine Rohdaten, Argumente, Stacktraces oder Exceptiontexte ausgeben.
            System.err.print("SYNTHETIC_PROTOCOL_FAILED\n"); System.err.flush();
        } finally {
            try { System.in.close(); } catch (Exception ignored) { }
            System.out.close(); System.err.close();
        }
        System.exit(exit);
    }
}
