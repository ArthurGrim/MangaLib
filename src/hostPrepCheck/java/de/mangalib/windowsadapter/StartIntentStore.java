package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ausschließlich neuer privater Laufordner, immutable Revisionen und Fail-closed-
 * Recovery. force + ATOMIC_MOVE sind prüfbare Schritte, keine Windows-Powerloss-
 * Garantie. Lesen erzeugt NIE eine neue Startberechtigung.
 */
final class StartIntentStore implements AutoCloseable {
    enum Kind { PREPARED, ABORTED, COMPLETED }
    enum Point { WRITTEN, FORCED, PUBLISHED }
    interface Crash { void at(Point point) throws IOException; }
    record Binding(UUID ticket, UUID session, UUID group) {
        Binding { for (UUID id : List.of(ticket, session, group)) if (id.equals(new UUID(0, 0))) throw new IllegalArgumentException("zero-binding"); }
    }
    record Anchor(long revision, String hash) { }
    record Recovery(boolean verified, Kind kind, String reason) { }
    static final class Proof {
        private final StartIntentStore owner;
        private final Anchor anchor;
        private Proof(StartIntentStore owner, Anchor anchor) { this.owner = owner; this.anchor = anchor; }
        Anchor anchor() { return anchor; }
    }
    private static final int MAGIC = 0x4d4c494e, SIZE = 132;
    private final Path directory;
    private final Binding binding;
    private final Crash crash;
    private final FileChannel lockFile;
    private final FileLock lock;
    private Anchor current;
    private Kind kind;
    private boolean failed, closed;

    static StartIntentStore create(Path directory, Binding binding, Crash crash) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        if (!absolute.getParent().toRealPath().equals(absolute.getParent())) throw new IOException("noncanonical-parent");
        Files.createDirectory(absolute); // Bestehende/historische Ordner werden niemals übernommen.
        FileChannel file = FileChannel.open(absolute.resolve("owner.lock"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try { return new StartIntentStore(absolute, binding, crash, file, file.lock()); }
        catch (IOException | RuntimeException failure) { file.close(); throw failure; }
    }
    private StartIntentStore(Path directory, Binding binding, Crash crash, FileChannel file, FileLock lock) {
        this.directory = directory; this.binding = Objects.requireNonNull(binding); this.crash = Objects.requireNonNull(crash);
        lockFile = file; this.lock = lock;
    }
    synchronized Proof prepare() throws IOException {
        if (current != null) throw new IOException("intent-already-published");
        publish(Kind.PREPARED); return new Proof(this, current);
    }
    synchronized Anchor abort() throws IOException { publish(Kind.ABORTED); return current; }
    synchronized Anchor complete() throws IOException { publish(Kind.COMPLETED); return current; }
    Binding binding() { return binding; }
    Path directory() { return directory; }
    synchronized boolean valid(Proof proof) {
        return !closed && !failed && kind == Kind.PREPARED && proof != null && proof.owner == this
                && proof.anchor.equals(current) && inspect(directory, binding, current).verified();
    }
    private void publish(Kind next) throws IOException {
        if (closed || failed || (current == null ? next != Kind.PREPARED : kind != Kind.PREPARED || next == Kind.PREPARED))
            throw new IOException("intent-transition-blocked");
        long revision = current == null ? 1 : current.revision() + 1;
        byte[] previous = current == null ? new byte[32] : HexFormat.of().parseHex(current.hash());
        ByteBuffer data = ByteBuffer.allocate(SIZE);
        data.putInt(MAGIC).putInt(1).putInt(next.ordinal() + 1).putLong(revision);
        for (UUID id : List.of(binding.ticket(), binding.session(), binding.group())) data.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        data.put(previous); data.put(hash(Arrays.copyOf(data.array(), SIZE - 32)));
        Path pending = directory.resolve(name(revision) + ".pending"), published = directory.resolve(name(revision));
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(data.array()); while (bytes.hasRemaining()) channel.write(bytes);
                crash.at(Point.WRITTEN); channel.force(true); crash.at(Point.FORCED);
            }
            if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) throw new IOException("revision-exists");
            Files.move(pending, published, StandardCopyOption.ATOMIC_MOVE); // Kein unsicherer Renamefallback.
            crash.at(Point.PUBLISHED);
            Anchor candidate = new Anchor(revision, HexFormat.of().formatHex(hash(data.array())));
            if (!inspect(directory, binding, candidate).verified()) throw new IOException("intent-readback");
            current = candidate; kind = next;
        } catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }
    static Recovery inspect(Path directory, Binding expected, Anchor externalAnchor) {
        try {
            if (externalAnchor == null || externalAnchor.revision() < 1 || externalAnchor.revision() > 2
                    || Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) return new Recovery(false, null, "untrusted-baseline");
            try (var files = Files.list(directory)) {
                List<Path> entries = files.limit(5).toList();
                if (entries.size() != externalAnchor.revision() + 1 || entries.stream().anyMatch(Files::isSymbolicLink))
                    return new Recovery(false, null, "incomplete-or-extra-files");
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (!name.equals("owner.lock") && !name.equals(name(1))
                            && !(externalAnchor.revision() == 2 && name.equals(name(2)))) return new Recovery(false, null, "unexpected-file");
                }
                if (!Files.isRegularFile(directory.resolve("owner.lock"), LinkOption.NOFOLLOW_LINKS)) return new Recovery(false, null, "missing-owner-record");
            }
            byte[] previous = new byte[32]; Kind latest = null;
            for (long revision = 1; revision <= externalAnchor.revision(); revision++) {
                Path file = directory.resolve(name(revision));
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != SIZE) return new Recovery(false, null, "missing-or-size");
                byte[] data = Files.readAllBytes(file); ByteBuffer bytes = ByteBuffer.wrap(data);
                if (bytes.getInt() != MAGIC || bytes.getInt() != 1) return new Recovery(false, null, "format");
                int state = bytes.getInt(); if (state < 1 || state > 3 || bytes.getLong() != revision) return new Recovery(false, null, "revision");
                latest = Kind.values()[state - 1];
                if (revision == 1 ? latest != Kind.PREPARED : latest == Kind.PREPARED) return new Recovery(false, null, "transition");
                Binding read = new Binding(new UUID(bytes.getLong(), bytes.getLong()), new UUID(bytes.getLong(), bytes.getLong()), new UUID(bytes.getLong(), bytes.getLong()));
                byte[] chain = new byte[32], checksum = new byte[32]; bytes.get(chain).get(checksum);
                if (!read.equals(expected) || !Arrays.equals(chain, previous)
                        || !Arrays.equals(checksum, hash(Arrays.copyOf(data, SIZE - 32)))) return new Recovery(false, null, "binding-or-checksum");
                previous = hash(data);
            }
            if (!HexFormat.of().formatHex(previous).equals(externalAnchor.hash())) return new Recovery(false, null, "stale-anchor");
            return new Recovery(true, latest, "verified-record-only-no-admission");
        } catch (IOException | RuntimeException failure) { return new Recovery(false, null, "unreadable-or-invalid"); }
    }
    private static String name(long revision) { return "r" + revision + ".bin"; }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; try { lock.release(); } finally { lockFile.close(); }
    }
}
