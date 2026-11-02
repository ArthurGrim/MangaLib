package de.mangalib.windowsadapter;

import com.sun.jna.Native;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Eine einzige leere Jobgruppe, feste verbrauchte Kennung, niemals Prozessstarts. */
public final class Win64EmptyJobProbe {
    private static final String RUN = "B_ADAPTER2_EMPTY_20261023_77808c3d";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected fixed evidence directory");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!root.getFileName().toString().equals("t02-adapter2-empty-77808c3d")) {
            throw new IllegalArgumentException("Unexpected evidence directory");
        }
        for (Path current = root; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalStateException("Evidence path must not be redirected");
            }
        }
        Files.createDirectories(root);
        if (!root.toRealPath().equals(root)) throw new IllegalStateException("Noncanonical evidence path");
        if (Files.exists(root.resolve("STOP")) || Files.exists(root.resolve("consumed"))) {
            throw new IllegalStateException("One-use authorization already consumed; no retry");
        }
        for (String property : List.of("jna.boot.library.path", "jna.library.path", "jna.tmpdir",
                "jna.nounpack", "jna.noclasspath")) {
            if (System.getProperty(property) != null) throw new IllegalStateException("Unexpected JNA override");
        }
        if (!"true".equals(System.getProperty("jna.nosys"))) throw new IllegalStateException("Expected bundled JNA dispatch");
        Win64KernelCalls.requirePlatform(System.getProperty("os.name"), Native.POINTER_SIZE);
        Win64Layouts.verify();
        Win64KernelCalls port = Win64KernelCalls.nativeEmptyJobPort();
        // CREATE_NEW und force VOR CreateJobObjectW: Absturz erlaubt keine Wiederholung.
        durableNew(root.resolve("consumed"), RUN + "\nconsumed=" + Instant.now() + "\n");
        var trace = new ArrayList<String>();
        trace.add("run=" + RUN);
        trace.add("Java=" + System.getProperty("java.specification.version") + " JNA=" + Native.VERSION);
        trace.add("Win64 sizes basic=64 extended=144 accounting=48 pidStruct=16 pidHeader=8 pointer=8");
        trace.add("workerStarts=0 processAssignments=0 processTerminations=0 quotas=none");
        KernelCalls.Resource job = null;
        Throwable primary = null;
        Throwable cleanup = null;
        boolean empty = false;
        long started = System.nanoTime();
        try {
            job = port.createJob(new KernelCalls.Scope());
            var limits = new KernelCalls.Limits(true, false, 0, 0, 0);
            port.configure(job, limits);
            if (!limits.equals(port.readLimits(job))) throw new IllegalStateException("Unexpected readback");
            long remaining = 15_000_000_000L - (System.nanoTime() - started);
            var observation = port.observe(job, 8, remaining);
            empty = observation.complete() && observation.stable() && observation.active() == 0
                    && observation.total() == 0 && observation.pids().isEmpty();
            if (!empty) throw new IllegalStateException("Empty group not confirmed");
            if (System.nanoTime() - started >= 15_000_000_000L) throw new IllegalStateException("Observation deadline exceeded");
        } catch (Throwable failure) {
            primary = failure;
        } finally {
            // Nur der hier tatsächlich erworbene private Job, genau ein Close.
            // Bei fehlendem Endbeleg bleibt der Versuch FAILED, auch falls Close gelingt.
            if (job != null) {
                try { port.close(job); } catch (Throwable failure) { cleanup = failure; }
            }
            if (primary == null && System.nanoTime() - started >= 15_000_000_000L) {
                primary = new IllegalStateException("Native observation/close budget exceeded");
            }
            trace.addAll(port.audit());
            trace.add("emptyConfirmed=" + empty);
            trace.add("outstandingHandles=" + port.outstandingHandles());
            trace.add("outstandingBuffers=" + port.outstandingBuffers());
            trace.add("primary=" + describe(primary));
            trace.add("cleanup=" + describe(cleanup));
            trace.add("elapsedNanos=" + (System.nanoTime() - started));
            boolean passed = primary == null && cleanup == null && empty
                    && port.outstandingHandles() == 0 && port.outstandingBuffers() == 0;
            trace.add("result=" + (passed ? "PASS" : "FAILED"));
            durableNew(root.resolve("trace.txt"), String.join("\n", trace) + "\n");
            durableNew(root.resolve("STOP"), RUN + "\nconsumed; no further scenario or retry\n");
            System.out.println("B-ADAPTER-2 empty job: " + (passed ? "PASS" : "FAILED")
                    + "; workerStarts=0; handles=" + port.outstandingHandles()
                    + "; buffers=" + port.outstandingBuffers());
            if (!passed) {
                var failure = new IllegalStateException("STOP: empty-job validation failed", primary);
                if (cleanup != null) failure.addSuppressed(cleanup);
                throw failure;
            }
        }
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "none";
        if (failure instanceof KernelCalls.Failure nativeFailure) {
            return nativeFailure.operation + "; code=" + nativeFailure.code;
        }
        return failure.getClass().getName();
    }

    private static void durableNew(Path path, String text) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
    }
}
