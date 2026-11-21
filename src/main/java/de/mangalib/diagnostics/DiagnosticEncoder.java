package de.mangalib.diagnostics;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Auch Framework-Exceptions dürfen keine Credentials, SQL-Werte oder URL-Querys ausschreiben. */
public class DiagnosticEncoder extends EncoderBase<ILoggingEvent> {
    @Override public byte[] headerBytes() { return new byte[0]; }
    @Override public byte[] footerBytes() { return new byte[0]; }
    @Override public byte[] encode(ILoggingEvent event) {
        String id = event.getMDCPropertyMap().getOrDefault("requestId", "-");
        if (!id.matches("[a-f0-9-]{36}")) id = "-";
        boolean own = event.getLoggerName().equals(RequestDiagnosticsFilter.class.getName())
                || event.getLoggerName().equals(DiagnosticEvents.class.getName());
        String message = own ? event.getFormattedMessage() : "event=framework_diagnostic";
        message = message.replaceAll("[\\r\\n\\p{Cntrl}]", "_");
        if (message.length() > 512) message = message.substring(0, 512);
        String logger = event.getLoggerName().replaceAll("[^a-zA-Z0-9_.$]", "_");
        var text = new StringBuilder().append(Instant.ofEpochMilli(event.getTimeStamp()))
                .append(' ').append(event.getLevel()).append(" logger=")
                .append(logger.substring(0, Math.min(160, logger.length())))
                .append(" requestId=").append(id)
                .append(' ').append(message).append('\n');
        var failure = event.getThrowableProxy();
        if (failure != null && failure.getStackTraceElementProxyArray() != null) {
            var frames = failure.getStackTraceElementProxyArray();
            for (int i = 0; i < Math.min(32, frames.length); i++) {
                var frame = frames[i].getStackTraceElement();
                // Keine Exceptionmeldung, Dateipfade, Ursachen oder suppressed Exceptions.
                String type = frame.getClassName().replaceAll("[^a-zA-Z0-9_.$]", "_");
                String method = frame.getMethodName().replaceAll("[^a-zA-Z0-9_$<>]", "_");
                text.append(" at ").append(type.substring(0, Math.min(type.length(), 160)))
                        .append('.').append(method.substring(0, Math.min(method.length(), 80)))
                        .append(':').append(frame.getLineNumber()).append('\n');
            }
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
}
