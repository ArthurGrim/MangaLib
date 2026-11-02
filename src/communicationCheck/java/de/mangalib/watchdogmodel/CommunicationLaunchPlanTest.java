package de.mangalib.watchdogmodel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CommunicationLaunchPlanTest {
    @TempDir Path directory;

    @Test void fixedHostClasspathAndEnvironmentContainNoApplicationOrInheritedSecrets() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        var plan = CommunicationLaunchPlan.inspect(root, root.resolve("build/classes/java/communicationHost"));
        assertEquals(CommunicationLaunchPlan.JAVA.toRealPath(), plan.java());
        assertEquals(4, plan.environment().size());
        assertEquals(java.util.Set.of("SystemRoot", "WINDIR", "TEMP", "TMP"), plan.environment().keySet());
        assertEquals(1, plan.command().stream().filter(CommunicationLaunchPlan.HOST::equals).count());
        assertTrue(plan.command().contains("-Xmx32m"));
        assertFalse(plan.command().stream().anyMatch(s -> s.endsWith(".jar") || s.contains("Spring")));
        assertEquals(64, plan.classHash().length()); assertEquals(64, plan.javaHash().length());
        assertFalse(CommunicationLaunchPlan.CLASS_ROOTS.contains("CommunicationProbe"));
    }

    @Test void aDifferentClasspathIsRejectedBeforeAnyStart() {
        assertThrows(Exception.class, () -> CommunicationLaunchPlan.inspect(Path.of(""), directory));
    }

    @Test void unknownAndMissingClassesAreRejected() throws Exception {
        assertThrows(Exception.class, () -> CommunicationLaunchPlan.classHash(directory));
        Path pkg = Files.createDirectories(directory.resolve("de/mangalib/watchdogmodel"));
        for (String name : CommunicationLaunchPlan.CLASS_ROOTS) Files.write(pkg.resolve(name + ".class"), new byte[]{1});
        assertEquals(64, CommunicationLaunchPlan.classHash(directory).length());
        Files.write(pkg.resolve("Unexpected.class"), new byte[]{1});
        assertThrows(Exception.class, () -> CommunicationLaunchPlan.classHash(directory));
    }

    @Test void markerIsCreatedNewForcedBeforeStartAndCanNeverBeConsumedAgain() throws Exception {
        var plan = new CommunicationLaunchPlan.Plan(directory, directory, directory.resolve("private"),
                UUID.randomUUID(), List.of(), Map.of(), "synthetic-java-hash", "synthetic-class-hash");
        Path marker = CommunicationLaunchPlan.consume(plan);
        String evidence = Files.readString(marker);
        assertTrue(evidence.contains("CONSUMED BEFORE START"));
        assertThrows(Exception.class, () -> CommunicationLaunchPlan.consume(plan));
        assertEquals(evidence, Files.readString(marker));
    }
}
