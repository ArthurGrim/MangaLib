package de.mangalib;

import java.util.*;
import java.util.regex.Pattern;

/** Fail-closed Prüfung fester SHOW-GRANTS-Ergebnisse; keine Account-/DDL-Operation. */
final class T03ServerRights {
    private static final Set<String> DML = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
    static final Pattern GRANT = Pattern.compile(
            "^GRANT ([A-Z_, ]+) ON ([^\\s]+) TO (?:'[A-Za-z0-9_.-]+'|`[A-Za-z0-9_.-]+`)@(?:'[A-Za-z0-9_.:%-]+'|`[A-Za-z0-9_.:%-]+`)$");

    enum Reason { UNSUPPORTED_FORMAT, FORBIDDEN_PRIVILEGE, FORBIDDEN_SCOPE, AMBIGUOUS_SCHEMA_PATTERN, MISSING_DML }

    /** Nur feste Kategorien, niemals Granttext, Accountdetails oder unbekannte Rohdaten ausgeben. */
    static final class Rejected extends IllegalStateException {
        private final Reason reason;
        Rejected(Reason reason) { super("Testrechte: " + reason + ": STOP."); this.reason = reason; }
        Reason reason() { return reason; }
    }

    static void verify(List<String> grants) {
        var effective = new HashSet<String>();
        if (grants == null) throw new Rejected(Reason.UNSUPPORTED_FORMAT);
        for (String raw : grants) {
            if (raw == null) throw new Rejected(Reason.UNSUPPORTED_FORMAT);
            if (raw.toUpperCase(Locale.ROOT).contains("WITH GRANT OPTION")) throw new Rejected(Reason.FORBIDDEN_PRIVILEGE);
            var match = GRANT.matcher(raw);
            if (!match.matches()) throw new Rejected(Reason.UNSUPPORTED_FORMAT);
            var privileges = new HashSet<String>();
            for (String privilege : match.group(1).split(",", -1)) {
                if (!privileges.add(privilege.strip()) || privilege.isBlank()) throw new Rejected(Reason.UNSUPPORTED_FORMAT);
            }
            String scope = match.group(2);
            if (scope.equals("*.*")) {
                if (!privileges.equals(Set.of("USAGE"))) throw new Rejected(Reason.FORBIDDEN_PRIVILEGE);
            } else {
                // MySQL-Schema-Grants behandeln _ als Wildcard, außer bei partial_revokes=ON.
                // Ohne separat bewiesenen Servermodus nur die explizit maskierte Form zulassen.
                if (scope.equals("mangalib_test.*") || scope.equals("`mangalib_test`.*"))
                    throw new Rejected(Reason.AMBIGUOUS_SCHEMA_PATTERN);
                if (!scope.equals("mangalib\\_test.*") && !scope.equals("`mangalib\\_test`.*"))
                    throw new Rejected(Reason.FORBIDDEN_SCOPE);
                if (!DML.containsAll(privileges)) throw new Rejected(Reason.FORBIDDEN_PRIVILEGE);
                effective.addAll(privileges);
            }
        }
        if (!effective.containsAll(DML)) throw new Rejected(Reason.MISSING_DML);
    }
}
