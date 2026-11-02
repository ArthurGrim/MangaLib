package de.mangalib.windowsadapter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Nur vertrauenswürdige feste Startkonfiguration, kein HTTP-/Worker-Request. Kein Shellstart. */
record Win64LaunchSpec(String executable, List<String> arguments, String directory,
                       Map<String, String> environment, long startDeadline) {
    Win64LaunchSpec {
        requirePath(executable); requirePath(directory);
        arguments = List.copyOf(arguments);
        arguments.forEach(Win64LaunchSpec::requireText);
        var sorted = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        environment.forEach((key, value) -> {
            if (!List.of("SYSTEMROOT", "TEMP", "TMP").contains(key.toUpperCase(java.util.Locale.ROOT))
                    || sorted.containsKey(key)) throw new IllegalArgumentException("Unexpected environment key");
            requirePath(value); sorted.put(key, value);
        });
        if (sorted.size() != 3) throw new IllegalArgumentException("Explicit SystemRoot/TEMP/TMP required");
        environment = java.util.Collections.unmodifiableMap(sorted);
        if (startDeadline <= 0) throw new IllegalArgumentException("deadline");
        bounded(command(executable, arguments));
        bounded(environmentBlock(environment));
    }

    /** Keine UNC-/Device-/ADS-/relativen Pfade oder implizite Suchpfadauflösung. */
    static void requirePath(String value) {
        requireText(value);
        if (!value.matches("[A-Za-z]:\\\\[^:*?\"<>|]*") || value.contains("/")
                || java.util.Arrays.stream(value.substring(3).split("\\\\", -1))
                    .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")
                            || part.endsWith(".") || part.endsWith(" ")))
            throw new IllegalArgumentException("Expected canonical absolute Windows path");
    }

    private static void requireText(String text) {
        if (text == null || text.indexOf(0) >= 0) throw new IllegalArgumentException("NUL/null prohibited");
    }

    /** Windows-CRT-Regeln: Backslashes vor Quote verdoppeln, auch vor abschließender Quote. */
    static String quote(String text) {
        requireText(text);
        var result = new StringBuilder("\"");
        int slashes = 0;
        for (char c : text.toCharArray()) {
            if (c == '\\') { slashes++; continue; }
            result.append("\\".repeat(c == '"' ? slashes * 2 + 1 : slashes));
            result.append(c); slashes = 0;
        }
        return result.append("\\".repeat(slashes * 2)).append('"').toString();
    }

    private static String command(String exe, List<String> args) {
        return quote(exe) + args.stream().map(a -> " " + quote(a)).collect(java.util.stream.Collectors.joining()) + '\0';
    }
    private static String environmentBlock(Map<String, String> env) {
        return env.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue() + '\0')
                .collect(java.util.stream.Collectors.joining()) + '\0';
    }
    private static void bounded(String value) {
        if (value.length() > 32767) throw new IllegalArgumentException("UTF-16 start buffer too large");
    }
    byte[] commandBytes() { return command(executable, arguments).getBytes(StandardCharsets.UTF_16LE); }
    byte[] environmentBytes() { return environmentBlock(environment).getBytes(StandardCharsets.UTF_16LE); }
}
