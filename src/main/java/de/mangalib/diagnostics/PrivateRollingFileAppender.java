package de.mangalib.diagnostics;

import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Dateilog startet nur nach Beschränkung seines eigenen Verzeichnisses auf den Eigentümer. */
public class PrivateRollingFileAppender extends RollingFileAppender<ILoggingEvent> {
    private long totalBudgetBytes = 100L * 1024 * 1024;
    public void setTotalBudgetBytes(long bytes) {
        if (bytes < 32768) throw new IllegalArgumentException("Diagnostic budget too small");
        totalBudgetBytes = bytes;
    }
    @Override protected synchronized void subAppend(ILoggingEvent event) {
        try (var files = Files.list(Path.of(getFile()).toAbsolutePath().getParent())) {
            long used = 0;
            for (Path path : files.toList()) {
                try {
                    if (Files.isRegularFile(path)) used = Math.addExact(used, Files.size(path));
                } catch (NoSuchFileException removedArchive) {
                    // Die reguläre Logback-Aufräumung darf ein gerade gelistetes Archiv entfernen.
                }
            }
            // Encoder begrenzt jeden Datensatz auf weniger als 16 KiB. Keine fremden Dateien löschen.
            if (used > totalBudgetBytes - 16384) {
                addWarn("Diagnostic file budget exhausted; console logging remains active.");
                stop();
                return;
            }
            super.subAppend(event);
        } catch (Exception failure) {
            addError("Diagnostic file write unavailable; console logging remains active.");
            stop();
        }
    }
    @Override public void start() {
        try {
            Path file = Path.of(getFile()).toAbsolutePath().normalize();
            Path directory = file.getParent();
            if (directory == null || Files.isSymbolicLink(directory) || Files.isSymbolicLink(file))
                throw new IllegalArgumentException("Invalid diagnostic destination");
            Files.createDirectories(directory);
            restrict(directory, true);
            if (Files.exists(file)) restrict(file, false);
            super.start();
        } catch (Exception failure) {
            // Konsolenappender bleibt verfügbar. Keine Pfade oder fremden IO-Fehlertexte ausgeben.
            // Ein eigener Vorprüfungsfehler soll Boots Logback-ERROR-Startbarriere nicht auslösen.
            addWarn("Private diagnostic file unavailable; console logging remains active.");
        }
    }
    static void restrict(Path path, boolean directory) throws java.io.IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new java.io.IOException("Private file permissions unavailable");
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        acl.setAcl(List.of(builder.build()));
    }
}
