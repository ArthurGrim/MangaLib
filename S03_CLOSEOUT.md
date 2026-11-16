# S03 – Technischer Abschluss, 16.11.2026

Status: S03_TECHNICALLY_ACCEPTED. Implementierung und isolierte Abnahme sind
abgeschlossen. Produktionsausrollung und Git-Veröffentlichung sind getrennt.

| Anforderung | Tatsächlicher Nachweis |
| --- | --- |
| Erstlektüre, explizite Rereads, Datumskorrektur | Unit/MVC, echte JPA-Rollbackfälle und isolierter Browser |
| Mehrere bewusste Abschlüsse am selben Tag | Ereignisse bleiben getrennt; Browser und Java |
| Wiederholung derselben Aktion | UUID-Deduplizierung, Browser-Doppelklickschutz, reale Paralleltransaktionen |
| Ereignis und Reread atomar | Echter Coordinator, MySQL-Commit/Konkurrenz und Rollback |
| Historie bei Statusreset erhalten | Browser, Unit- und reale Rollbackprüfung |
| Monatsstatistik und 45-Minuten-Schätzung | Monatswechsel im Browser, reine Berechnungstests, JPA-Reload |
| Altbestände, NULL und Beziehungen erhalten | Zeilen-/Schema-Snapshots; kein impliziter Reset |
| Schema/Profil-/DB-/CSRF-/Host-/Origin-Grenzen | Bestehende Guards unverändert, MVC-/Manual-Schutzprüfungen |
| Dunkles Design und responsives Layout | Echte Browseransichten 600/1280px, gemeinsame Inhaltsbreite |
| Gesamtregression | 1456 Javafälle: 1454 bestanden, 0 Fehler, 2 bestehende Opt-in-Skips |
| HTTP-/JSON-Verträge | Alle 29 MvcContractTest-Fälle bestanden |
| DOM | 254 bestanden, 0 Fehler/übersprungen |
| Manual-Schutz | 94 erfolgreiche Fälle unverändert bestätigt; letzte Abschlussprüfung UP-TO-DATE |
| Opt-in S03 | 1 echter Commit-/Parallelfall, 2 erneut ausgeführte Rollbackfälle erfolgreich |
| Testschema nach Abschluss | Lesende S03-Prüfung erfolgreich; Fingerabdruck exakt unverändert |

Vollfingerabdruck auf mangalib_test:
77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f

Altzeilen:
3b629c3faeac198c4d83cf8df033257724a289eca71b46d17d344fc5d523d0b9

Altschema:
9ca0a1402263a0830ed9df7ffbd50edbee22f50f02ad357bf91ed468c166a6e0

Elf InnoDB-Tabellen, 78 Stammdaten, alle bestehenden Geschäftszeilen unverändert;
Ereignistabelle nach Tests leer. AUTO_INCREMENT wird weder verglichen noch
zurückgesetzt. Manuelle Browserdaten bleiben unverändert zum letzten bestätigten
Browser-Endbestand; diese Phase führte keine Manual-/Produktionsverbindungen aus.

## Beseitigte Regressionsblocker

Die fünf alten Testklassen LegacyEditIntegrationTest, ReadingStatusIntegrationTest,
MvcContractTest, RequestLimitsIntegrationTest und ServiceTest verwenden nun einen
kleinen gemeinsamen JUnit-Interceptor CoordinatorRollback. Er führt Setup, Test und
Teardown innerhalb des echten LibraryWriteCoordinator aus und erzwingt anschließend
Rollback durch ein privates Signal. Selbst ein erfolgreicher Test darf nicht committen.
Fehler werden nach echtem Rollback erneut ausgelöst; die bisherigen Daten-/Schema-
Assertions bleiben erhalten und werden um unabhängige Ereignisvergleiche ergänzt.
Der alte Spring-Testtransaktions-Fixtureweg bleibt mit seinen bisherigen Guards
unverändert verfügbar; der neue Weg verlangt den erzwungenen Coordinatorrahmen
und dieselben DB-/Benutzer-/InnoDB-Prüfungen. Keine Produktionsbarriere umgangen.

Überholte Requestfixtures an bestehende Verträge angepasst: unbearbeitete Preise
weglassen, unbekannte Bandpreise nicht als bewusstes Leeren senden, Erweiterung
explizit anfordern, Status-/Bandbereichsvorschau einschließlich purchaseToken
bestätigen. Reiner Titelhinweis ist nach bestehendem T03-Vertrag AMBIGUOUS_SERIES
(409), keine automatische Übernahme; die Testfälle prüfen jetzt festen Konfliktcode
und kompletten Bestandserhalt. Neue MVC-Reihe ohne doppelten externen Index.
Keine fachliche Kauf-/Preis-/M06-Produktionsregel für diese Portierung verändert.

Die zwei regulär übersprungenen Fälle sind bestehende echte Commit-Opt-ins aus M01
und M02 (ShoppingEditIntegrationTest, VolumeImportIntegrationTest), keine Platzhalter
und keine neuen Deaktivierungen. Ihre besonderen historischen Freigaben/Fixtures
werden hier nicht erneut ausgeführt. Die zuvor protokollierten Fehlversuche bleiben
historisch fehlgeschlagen; erst der abschließende vollständige Lauf ist grün.

Alle Gradle-Aufrufe offline/ohne Daemon, mit identischem vorher geprüftem Dry-Run.
Kein clean, keine Installation, kein Live-Scraper, keine Sicherheitsguardänderung.
Vollständige XML-Ergebnisse privat unter .gradle/s03-final-20261116/green-full-regression;
keine Daten-/Log-/Artefaktaufnahme in Git. Historische Manual-JARs unverändert.

## Separat vor produktiver Nutzung

Die Funktion bleibt standardmäßig deaktiviert. Produktionsmigration und gewünschter
Reread-Reset auf 0 erfordern weiterhin Backup/Wiederherstellungsprobe, konkrete
SQL-/Zielprüfung und eigene Eigentümerfreigabe. Keine historischen Lesedaten erfinden;
keine Normalisierung positiver synthetischer Test-/Manual-Zähler. Erst danach bewusst
aktivieren. Diese Ausrollschritte sind keine behauptete abgeschlossene Migration.

Arbeitsstand auf codex/s03-reading-history ist technisch für den Git-Abschluss
vorbereitet; nichts gestagt/committed/gepusht/gemergt. Keine neue Git-Schreibfreigabe
für diesen Abschlussauftrag abgeleitet. Private Nachweise und Artefakte bleiben ignoriert.
