package de.mangalib;

import java.util.*;
import java.util.regex.Pattern;

/** Datensparsame Klassifikation im RAM: Ergebnisse enthalten ausschließlich feste Kategorien. */
final class T03RightsDiagnosis {
    enum Mode { ON, OFF, UNKNOWN }
    enum Scope { GLOBAL_USAGE, GLOBAL_OTHER, EXACT_ESCAPED_TEST_SCHEMA, PLAIN_TEST_SCHEMA, OTHER_OR_UNSUPPORTED_SCOPE }
    enum Privilege { SELECT, INSERT, UPDATE, DELETE, USAGE, DDL, OTHER }
    record Result(Mode partialRevokes, int rows, Set<Scope> scopes, Set<Privilege> privileges,
                  boolean roleOrUnsupported, boolean grantOption, boolean legacyAccepted, String corrected) {
        boolean verified() { return partialRevokes != Mode.UNKNOWN && corrected.equals("ACCEPTED"); }
    }
    static Mode mode(Object value) {
        if (!(value instanceof Number || value instanceof String || value instanceof Boolean)) return Mode.UNKNOWN;
        return switch (value.toString()) {
            case "1", "ON", "true" -> Mode.ON;
            case "0", "OFF", "false" -> Mode.OFF;
            default -> Mode.UNKNOWN;
        };
    }
    static Result analyze(Object mode, List<String> grants) {
        var scopes = EnumSet.noneOf(Scope.class);
        var privileges = EnumSet.noneOf(Privilege.class);
        boolean unsupported = false, option = false;
        for (String raw : grants) {
            if (raw == null) { unsupported = true; scopes.add(Scope.OTHER_OR_UNSUPPORTED_SCOPE); continue; }
            option |= raw.toUpperCase(Locale.ROOT).contains("WITH GRANT OPTION");
            var match = T03ServerRights.GRANT.matcher(raw);
            if (!match.matches()) { unsupported = true; scopes.add(Scope.OTHER_OR_UNSUPPORTED_SCOPE); continue; }
            String scope = match.group(2);
            scopes.add(switch (scope) {
                case "*.*" -> match.group(1).equals("USAGE") ? Scope.GLOBAL_USAGE : Scope.GLOBAL_OTHER;
                case "`mangalib\\_test`.*", "mangalib\\_test.*" -> Scope.EXACT_ESCAPED_TEST_SCHEMA;
                case "`mangalib_test`.*", "mangalib_test.*" -> Scope.PLAIN_TEST_SCHEMA;
                default -> Scope.OTHER_OR_UNSUPPORTED_SCOPE;
            });
            for (String name : match.group(1).split(",", -1)) {
                privileges.add(switch (name.strip()) {
                    case "SELECT" -> Privilege.SELECT;
                    case "INSERT" -> Privilege.INSERT;
                    case "UPDATE" -> Privilege.UPDATE;
                    case "DELETE" -> Privilege.DELETE;
                    case "USAGE" -> Privilege.USAGE;
                    case "CREATE", "ALTER", "DROP", "INDEX", "TRIGGER", "EVENT", "CREATE TEMPORARY TABLES" -> Privilege.DDL;
                    default -> Privilege.OTHER;
                });
            }
        }
        String corrected;
        try { T03ServerRights.verify(grants); corrected = "ACCEPTED"; }
        catch (T03ServerRights.Rejected failure) { corrected = failure.reason().name(); }
        return new Result(mode(mode), grants.size(), Collections.unmodifiableSet(scopes),
                Collections.unmodifiableSet(privileges), unsupported, option, legacyAccepts(grants), corrected);
    }
    /** Historische Prüflogik nur zum Vergleich, niemals als Freigabeentscheidung. */
    private static boolean legacyAccepts(List<String> grants) {
        var effective = new HashSet<String>();
        var dml = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
        for (String raw : grants) {
            if (raw == null) return false;
            String grant = raw.toUpperCase(Locale.ROOT);
            if (grant.contains("WITH GRANT OPTION")) return false;
            var match = Pattern.compile("^GRANT (.+) ON (`?MANGALIB_TEST`?\\.\\*|\\*\\.\\*) TO .+$").matcher(grant);
            if (!match.matches()) return false;
            Set<String> values;
            try { values = Set.of(match.group(1).split(",\\s*")); }
            catch (IllegalArgumentException malformed) { return false; }
            if (match.group(2).equals("*.*")) { if (!values.equals(Set.of("USAGE"))) return false; }
            else { if (!dml.containsAll(values)) return false; effective.addAll(values); }
        }
        return effective.containsAll(dml);
    }
}
