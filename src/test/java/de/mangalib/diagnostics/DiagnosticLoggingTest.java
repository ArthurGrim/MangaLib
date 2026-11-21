package de.mangalib.diagnostics;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticLoggingTest {
    @TempDir Path temporary;
    @Test void longUnicodeLoggerNamesRemainBoundedAndDoNotBreakLogging() {
        var context = new LoggerContext();
        var event = new LoggingEvent("test", context.getLogger("😀".repeat(200)), Level.ERROR, "SECRET", null, null);
        event.setMDCPropertyMap(Map.of());
        var bytes = assertDoesNotThrow(() -> new DiagnosticEncoder().encode(event));
        assertTrue(bytes.length < 16384); assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("SECRET"));
        context.stop();
    }
    private LoggingEvent event(LoggerContext context) {
        var error = new IllegalArgumentException("SECRET token SQL password", new Exception("SECRET cause"));
        var event = new LoggingEvent("test", context.getLogger("foreign.framework"), Level.ERROR,
                "SECRET\r\nforged", error, null);
        event.setMDCPropertyMap(Map.of("requestId", "SECRET\r\nforged"));
        return event;
    }
    @Test void frameworkMessagesCausesAndUntrustedContextAreNeverEncoded() {
        var context = new LoggerContext();
        try {
            var encoder = new DiagnosticEncoder(); encoder.setContext(context); encoder.start();
            String result = new String(encoder.encode(event(context)), StandardCharsets.UTF_8);
            assertFalse(result.contains("SECRET")); assertFalse(result.contains("forged"));
            assertTrue(result.contains("event=framework_diagnostic"));
            assertTrue(result.contains("DiagnosticLoggingTest.event:"));
            assertTrue(result.contains("requestId=-"));
        } finally { context.stop(); }
    }
    @Test void privateFileWritesAndExplicitRolloverPreserveOnlySafeDiagnostics() throws Exception {
        var context = new LoggerContext(); context.setName("isolated-test"); context.start();
        var appender = new PrivateRollingFileAppender();
        try {
            appender.setContext(context); appender.setName("TEST");
            Path active = temporary.resolve("private/active.log");
            appender.setFile(active.toString());
            var encoder = new DiagnosticEncoder(); encoder.setContext(context); encoder.start();
            appender.setEncoder(encoder);
            var policy = new SizeAndTimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent>();
            policy.setContext(context); policy.setParent(appender);
            policy.setFileNamePattern(temporary.resolve("private/archive.%d{yyyy-MM-dd}.%i.log").toString());
            policy.setMaxFileSize(FileSize.valueOf("1KB")); policy.setTotalSizeCap(FileSize.valueOf("5KB"));
            policy.setMaxHistory(14); policy.start(); appender.setRollingPolicy(policy);
            appender.start(); assertTrue(appender.isStarted());
            // Den echten Größen-Trigger durchlaufen, nicht rollover() ohne vorbereiteten Periodennamen aufrufen.
            for (int i = 0; i < 200; i++) {
                appender.doAppend(event(context));
                Thread.sleep(2);
            }
            appender.stop(); policy.stop();
            try (var files = Files.list(active.getParent())) {
                var logs = files.toList(); assertTrue(logs.size() >= 2);
                for (var log : logs) assertFalse(Files.readString(log).contains("SECRET"));
            }
            var acl = Files.getFileAttributeView(active.getParent(), java.nio.file.attribute.AclFileAttributeView.class);
            if (acl != null) {
                var owner = acl.getOwner();
                assertTrue(acl.getAcl().stream().allMatch(e -> e.principal().equals(owner)));
            }
            var posix = Files.getFileAttributeView(active.getParent(), java.nio.file.attribute.PosixFileAttributeView.class);
            if (posix != null) assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(posix.readAttributes().permissions()));
        } finally { appender.stop(); context.stop(); }
    }
    @Test void unusableDestinationDoesNotEscapeIntoBusinessCode() throws Exception {
        Path blocker = temporary.resolve("not-a-directory"); Files.writeString(blocker, "keep");
        var context = new LoggerContext();
        var appender = new PrivateRollingFileAppender(); appender.setContext(context);
        appender.setFile(blocker.resolve("active.log").toString());
        assertDoesNotThrow(appender::start); assertFalse(appender.isStarted());
        assertEquals("keep", Files.readString(blocker)); context.stop();
    }
    @Test void encoderBoundsEvenArtificiallyHugeStackFrames() {
        var context = new LoggerContext();
        var error = new Exception("SECRET");
        var frames = new StackTraceElement[200];
        Arrays.fill(frames, new StackTraceElement("A".repeat(2000), "B".repeat(2000), "SECRET", 12));
        error.setStackTrace(frames);
        var event = new LoggingEvent("test", context.getLogger("foreign.framework"), Level.ERROR, "SECRET", error, null);
        event.setMDCPropertyMap(Map.of());
        assertTrue(new DiagnosticEncoder().encode(event).length < 16384);
        context.stop();
    }
    @Test void exhaustedBudgetRejectsFurtherFileOutputWithoutDeletingAnything() throws Exception {
        var loggingContext = new LoggerContext();
        class Probe extends PrivateRollingFileAppender {
            @Override public String getFile() { return temporary.resolve("active.log").toString(); }
            void write() { subAppend(event(loggingContext)); }
        }
        Path occupied = temporary.resolve("existing.log");
        Files.writeString(occupied, "x".repeat(20000));
        var appender = new Probe(); appender.setContext(loggingContext);
        appender.setFile(temporary.resolve("active.log").toString()); appender.setTotalBudgetBytes(32768);
        assertDoesNotThrow(appender::write);
        assertFalse(Files.exists(temporary.resolve("active.log")));
        assertEquals(20000, Files.size(occupied));
        assertTrue(loggingContext.getStatusManager().getCopyOfStatusList().stream()
                .anyMatch(s -> s.getMessage().contains("budget exhausted")));
        loggingContext.stop();
    }
}
