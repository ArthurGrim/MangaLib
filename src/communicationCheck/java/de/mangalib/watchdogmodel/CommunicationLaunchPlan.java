package de.mangalib.watchdogmodel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Feste lokale Startparameter; keine Pfade/Befehle aus empfangenen Frames. */
final class CommunicationLaunchPlan {
    static final Path JAVA = Path.of("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe");
    static final String HOST = "de.mangalib.watchdogmodel.SyntheticHostMain";
    static final String ATTEMPT = "t02-watchdog-3b2-2e4b0772";
    static final List<String> CLASS_ROOTS = List.of("BinaryIpcCodec", "BufferBudget", "WatchdogModel",
            "FakeJobKernel", "FakeRecoveryJournal", "SyntheticProtocol", "SyntheticHostMain");

    record Plan(Path java, Path classes, Path directory, UUID session, List<String> command,
                Map<String, String> environment, String javaHash, String classHash) { }

    static Plan inspect(Path workspace, Path configuredClasses) throws Exception {
        Path root = workspace.toRealPath();
        Path classes = root.resolve("build/classes/java/communicationHost");
        rejectLinks(root, classes);
        if (!classes.toRealPath().equals(configuredClasses.toRealPath())) throw new IOException("classpath");
        if (!Files.isRegularFile(JAVA, LinkOption.NOFOLLOW_LINKS) || Runtime.version().feature() != 21
                || !Path.of(System.getProperty("java.home")).toRealPath().equals(JAVA.getParent().getParent().toRealPath()))
            throw new IOException("java-runtime");
        String classesHash = classHash(classes);
        Path directory = root.resolve(".gradle").resolve(ATTEMPT);
        rejectLinks(root, directory);
        UUID session = UUID.randomUUID();
        var command = List.of(JAVA.toRealPath().toString(), "-Xms16m", "-Xmx32m", "-XX:MaxMetaspaceSize=32m",
                "-XX:ReservedCodeCacheSize=16m", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC",
                "-XX:-UsePerfData", "-XX:+DisableAttachMechanism", "-XX:+ExitOnOutOfMemoryError",
                "-XX:-CreateCoredumpOnCrash", "-Dfile.encoding=UTF-8", "-Duser.home=" + directory,
                "-Djava.io.tmpdir=" + directory.resolve("tmp"), "-cp", classes.toString(), HOST,
                SyntheticProtocol.PROFILE, session.toString(), Long.toString(ProcessHandle.current().pid()));
        return new Plan(JAVA.toRealPath(), classes, directory, session, command,
                Map.of("SystemRoot", "C:\\Windows", "WINDIR", "C:\\Windows",
                        "TEMP", directory.resolve("tmp").toString(), "TMP", directory.resolve("tmp").toString()),
                hash(JAVA), classesHash);
    }

    static String classHash(Path classes) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var names = new ArrayList<String>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("classpath-link");
                if (Files.isDirectory(file)) continue;
                String name = classes.relativize(file).toString().replace('\\', '/');
                String prefix = "de/mangalib/watchdogmodel/";
                if (!name.startsWith(prefix) || !name.endsWith(".class")) throw new IOException("classpath-file");
                String simple = name.substring(prefix.length(), name.length() - 6);
                if (CLASS_ROOTS.stream().noneMatch(allowed -> simple.equals(allowed) || simple.startsWith(allowed + "$")))
                    throw new IOException("classpath-class");
                names.add(simple); digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        if (!names.containsAll(CLASS_ROOTS)) throw new IOException("classpath-incomplete");
        return HexFormat.of().formatHex(digest.digest());
    }

    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void rejectLinks(Path root, Path target) throws IOException {
        if (!target.normalize().startsWith(root)) throw new IOException("workspace");
        for (Path at = target; at != null && at.startsWith(root); at = at.getParent())
            if (Files.isSymbolicLink(at)) throw new IOException("path-link");
    }

    /** CREATE_NEW + force VOR Start. Jeder vorhandene Marker verbraucht die Freigabe. */
    static Path consume(Plan plan) throws Exception {
        Files.createDirectories(plan.directory().resolve("tmp"));
        Path marker = plan.directory().resolve("consumed");
        String evidence = "B-WATCHDOG-3B-2 CONSUMED BEFORE START\nprofile=V1-END23\nsession=" + plan.session()
                + "\njava.sha256=" + plan.javaHash() + "\nclasses.sha256=" + plan.classHash() + "\n";
        try (var channel = FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(evidence);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        return marker;
    }

    static Process start(Plan plan) throws Exception {
        // Zweite Prüfung direkt vor dem EINEN Start, kein Fallback bei Abweichung.
        if (!hash(plan.java()).equals(plan.javaHash()) || !classHash(plan.classes()).equals(plan.classHash()))
            throw new IOException("launch-changed");
        ProcessBuilder builder = new ProcessBuilder(plan.command()).directory(plan.directory().toFile());
        builder.environment().clear(); builder.environment().putAll(plan.environment());
        builder.redirectErrorStream(false);
        return builder.start();
    }
}
