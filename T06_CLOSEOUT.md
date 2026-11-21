# T06 – Abschluss

Stand 21.11.2026, `codex/t06-logging`, **T06_TECHNICALLY_VERIFIED**.
Implementierung und automatisierte Regression abgeschlossen; Git-Abschluss noch offen.

| Anforderung | Ergebnis |
|---|---|
| Bestehende Logging-Fassade verwenden | SLF4J/Logback, keine neue Abhängigkeit |
| Keine Backend-/Browser-Rohdaten | System.out/printStackTrace aus Anwendung entfernt, Browserausgaben entfernt oder feste Fehlertexte |
| Request-Korrelation | Server-UUID im Header, MDC bis zur synchronen Importgrenze, Cleanup auch bei Fehlern |
| HTTP-/Fehlerverträge erhalten | Neutrale bestehende Bodies/Codes; serverseitige bereinigte Stackdiagnose |
| Aussagekräftige Ereignisse | Methode und MVC-Routenmuster statt tatsächlicher URI, Dauer und Status am Filterende, Importstart/-abschluss/-abbruch, Betriebsereignisse |
| Datenschutz und Grenzen | Keine Exceptionmeldungen/Ursachen/Payloads, CRLF-Schutz, begrenzte technische Frames/Datensätze |
| Rotation und Speicherbudget | Tägliche/10-MiB-Größenrotation, 14 Archivperioden, 90-MiB-Archivbudget, zusätzliche 100-MiB-Schreibbudgetprüfung |
| Rechte/Ausfälle | Eigentümerrechte im eigenen Diagnoseordner; bei eigenem Prüf-/Schreibfehler konservativ keine Dateiausgabe, Konsole bleibt verfügbar |
| Fach-/Sicherheitsregeln | DB-Guards, T03/M06/S03, Workerprotokolle, Slot-/Quarantäne-/Cleanupregeln unverändert |
| Regression | 1.474 Javafälle bestanden, 2 bisherige Opt-ins übersprungen, 0 Fehler; 260 DOM-, 94 Manual-Schutz-, 20 Scraper-Pakettests bestanden |
| Datenintegrität | Globaler Vorher-/Nachher-Fingerabdruck identisch, keine Schema-/Bestandsänderung; AUTO_INCREMENT ausgenommen |

## Dateigruppen

- Neu: diagnostics/DiagnosticEvents, DiagnosticEncoder, PrivateRollingFileAppender,
  RequestDiagnosticsFilter und LifecycleDiagnostics sowie logback-spring.xml.
- Controller: MainController, EinkaufslisteController, ReadingHistoryController –
  Diagnose in bereits vorhandenen Catch-Blöcken, keine Änderung der Antwortverträge.
- Entity/Services: Band, BandService, EinkaufslisteService, MangaDetailsService,
  MangaReiheService, TypService, VerlagService, MangaScraper/BandScraper –
  Rohdatenausgaben entfernt beziehungsweise bereinigte Fehlerdiagnose; Parsing,
  Daten-/Transaktionsregeln unverändert. LiveScraperGateway mit festen Importereignissen.
- JavaScript: home, einkaufsliste, popup_add_reihe, popup_edit_reihe,
  popup_edit_baende und statistik; ausschließlich Diagnoseausgaben bereinigt.
- Neu: RequestDiagnosticsFilterTest, DiagnosticLoggingTest, ImportDiagnosticsTest.
- Dokumentation: ROADMAP.md, TESTING.md, T06_IMPLEMENTATION_PLAN.md,
  T06_LOGGING.md und dieser Abschlussbericht.

## Nachweisgrenzen

Konfiguration, Datenschutz, Größenrotation und private Rechte wurden unter temporärem
Testpfad geprüft. Keine echte volle Festplatte und kein produktiver Appstart erzeugt.
Keine Behauptung eines vollständig getesteten Windows-Dauerbetriebs: Betriebskonto,
Autostart, Logpfad und Wiederanlauf gehören weiterhin zu T07. Die Dateiquote setzt
den vorgesehenen einzelnen Schreiber voraus; Einzeldateien können ihre Rotations-
schwelle um einen begrenzten Datensatz überschreiten. Details: T06_LOGGING.md.

Eine HTTP-Erfolgsmeldung oder Shutdown-Anforderung ist kein zusätzlicher Commit-/
Jobabschlussnachweis. Native historische Evidenzen behalten ihre ursprünglichen
Artefaktbindungen. Keine Produktiv-/Manual-DB, Schemaänderung oder neue Live-Abnahme.
Sechs historische Manual-JARs erhalten. Keine privaten Logs/Snapshots/JARs in Git.
