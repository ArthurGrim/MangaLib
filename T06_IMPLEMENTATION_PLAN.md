# T06 – Logging und Fehlerdiagnose

**Aktueller Stand 21.11.2026: T06_TECHNICALLY_VERIFIED.**
Die nachfolgend dokumentierte Freigabegrenze wurde durch erneute ausdrückliche
Eigentümerfreigabe überwunden. Finale Suite: 1.474 bestanden, 0 Fehler, 2 bestehende
Opt-in-Skips; Daten-/Schemafingerabdruck vorher/nachher identisch. Abschlussmatrix:
T06_CLOSEOUT.md. Noch kein Commit oder Push.
Die folgenden ursprünglichen Planungs-/Zwischenstände sind historisch.
Implementierung und Betriebshinweise stehen in T06_LOGGING.md, tatsächliche
Testergebnisse im neuen TESTING.md-Nachtrag. Dateilogging mit den vorgeschlagenen
konfigurierbaren Standardgrenzen umgesetzt. Keine neue Fachentscheidung/Audit-Historie.
Automatische Freigabeprüfung verlangt für die finale vollständige Regression samt
globalem Datenvergleich eine erneute ausdrückliche Testdatenbankfreigabe. Bis dahin
keine weitere Datenbankausführung, kein Abschlusscommit und kein Push.

Vorbereitung vom 21.11.2026 auf `codex/t06-logging`, Basis T05-Commit `1129e70`.
Noch keine produktive T06-Implementierung. T05 ist lokal gesichert; Push/Merge
dieses neuen Abschlusses sind in diesem Auftrag nicht erfolgt.

## Bestätigte Ausgangslage

- Bestehende Spring-Boot-Logging-Fassade/Logback verwenden, keine neue Plattform.
- home.js protokolliert in Zeile 139 vollständige geladene Banddaten;
  einkaufsliste.js enthält ab Zeile 225 einzelne Formular-/Preis-/Titelausgaben.
  Diese Diagnoseausgaben sind Kandidaten für Entfernung, nicht für serverseitige
  Übernahme vollständiger Payloads. Genaue Stellen vor Änderungen erneut prüfen.
- Sicherheitsguards, Importjournal/Quarantäne und fachliche S03-Lesehistorie sind
  keine ersetzbaren Diagnose-Logs und bleiben erhalten.

## Empfohlene Reihenfolge

1. **Inventar und Datenschutzregeln:** Ausgaben in Controllern, Services, Worker,
   Launcher und JavaScript erfassen; technische Diagnose von fachlicher Historie
   und unveränderlicher Versuchsevidenz trennen. Keine Rohrequests, SQL-Werte,
   Titelbestände, Secrets, Cookies oder URL-Querys in neue Logs übernehmen.
2. **Begrenzte Serverereignisse:** Vorhandenen SLF4J-Logger verwenden. Aktion,
   Ergebnis, Dauer und notwendige technische Kennungen; INFO als Standard,
   DEBUG für gezielte Diagnose, WARN für problematische erwartete Zustände,
   ERROR für unerwartete Fehler. Unerwartete Fehler einmal diagnostizieren;
   HTTP-Verträge und neutrale Fehlermeldungen erhalten.
3. **Request-Korrelation:** Servergenerierte ID, begrenztes Format und saubere
   MDC-Bereinigung im finally-Block. Keine ungeprüften Client-IDs übernehmen.
   Workerübergabe nur im bestehenden kontrollierten IPC-Vertrag, ohne neue
   Prozessstarts oder Lockerung von SSRF-/Jobgrenzen.
4. **Lokale Rotation:** Pfad außerhalb Release/Git, konfigurierbare Größen-/Zeit-
   und Gesamtgrenzen, beschränkter Zugriff. Verhalten bei fehlendem Schreibrecht
   und vollem Speicher definieren; keine Fachtransaktion durch Logfehler verändern.
5. **Abnahme:** Appender-/MockMvc-/DOM-Tests für Level, Korrelation und Cleanup,
   Secret-/CRLF-Schutz, keine Browserrohwerte und unveränderte Fehlerverträge.
   Rotation unter temporärem Testpfad; keine echten Scraper- oder DB-Fehler erzeugen.
   Gradle offline/ohne Daemon nach identischem Dry-Run, danach passende Regression.

## Vor verbindlicher Dateilog-Konfiguration zu entscheiden

Empfehlung zur Eigentümerentscheidung: 14 Tage Aufbewahrung, maximal 10 MiB je
Datei und 100 MiB insgesamt, konfigurierbarer lokaler Pfad unter dem Benutzerprofil.
Dies ist ein Vorschlag, keine bereits beschlossene oder aktivierte Einstellung.
Ein fachliches Audit-Log ist nicht Bestandteil von T06 ohne eigenen Bedarf.
T07 entscheidet später über Betriebskonto, Autostart und reguläre Installation.

## Abnahmekriterien und Grenzen

Keine sensitiven Daten oder ungeprüften Kontrollzeichen in Diagnoseausgaben;
vollständige Kontextbereinigung auch bei Exceptions, kontrollierte Rotation,
neutrale HTTP-Antworten und unveränderte Fach-/Sicherheitsregeln. Dateirechte und
tatsächlicher Windows-Betrieb werden ausdrücklich getrennt von Mocknachweisen
bewertet. Keine Datenmigration oder Veränderung produktiver Daten erforderlich.
Vorbereitung ausschließlich lesend plus diese Planungsdatei; keine neuen Tests,
Builds, Datenbankverbindungen, Anwendungen oder Browser gestartet.
## Implementierungsnachtrag 21.11.2026

Erster Kern umgesetzt: RequestDiagnosticsFilter mit servergenerierter Korrelation,
MDC-Aufräumen, begrenzten Request-Abschlussdaten und bereinigter Exceptiondiagnose.
Sechs Frontendskripte ohne console.log-Rohdatenausgaben; 260 DOM-Tests grün.
Noch keine technische Gesamtabnahme. Offen: Bestätigung der Dateiloggrenzen,
Rotation/Fehlerfälle/Dateirechte, gezielte Backend-/Workerdiagnose einschließlich
behandelter Fehler und abschließende Regression. Vorhandene Guards und historischen
Evidenzen unverändert; kein Datenbankzugriff, Commit oder Push in dieser Umsetzung.
