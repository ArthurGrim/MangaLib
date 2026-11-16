# S03 – Stand und nächste Abnahmefreigabe

## Aktueller Nachtrag 16.11.2026 – Browserfunktionen abgenommen

Der Eigentümer bestätigte die separate manuelle Tabellenanlage und den engen
Runtime-GRANT. Der anschließende lesende Snapshot bestand: elf InnoDB-Tabellen,
neue Ereignistabelle leer, alle bisherigen Zeilen und die alten Schema-Metadaten
exakt erhalten. Konto: mangalib_manual_test_user@127.0.0.1. Keine DDL durch Codex.

Kandidat build/libs/mangalib-manual-test-s03.jar mit SHA-256
2dfeddab263e54a1c2f1b95a24166bff62440ab98bd1e901166c6fabe9625fe0
bestand erneut ValidateOnly. Start als PID 25536, ausschließlich manual-test,
mangalib_manual_test und 127.0.0.1:18080, synthetische Scraper, Banner bestätigt.
Bedienung ausschließlich über den vorhandenen Codex In-app Browser.

Neue eigene Daten: S03_BROWSER_20261116_R01, Reihe/Details 17, Bände 43/44,
je 5,00 EUR, keine Einkäufe oder externen Abrufe. Keine Bestandsbereinigung.

| Prüfung | Tatsächlich beobachtetes Ergebnis |
| --- | --- |
| Ungelesene Neuanlage | Zwei neue Bände, Reread 0, Historie leer |
| Normaler Bandeditor: gelesen markieren | Genau eine Erstlektüre vom 16.11., Reread bleibt 0 |
| Unverändertes Metadatenspeichern | Kein weiteres Ereignis |
| Erstlektüre auf 31.10. korrigieren | Datum nach Reload erhalten, kein Zählerwechsel |
| Ausdrücklicher Reread | Neues Ereignis, Reread 1 |
| Auf ungelesen zurücksetzen | Historie und Reread 1 bleiben erhalten |
| Bewusster Doppelklick auf Reread | Genau ein weiterer Reread, insgesamt 2; gelesen=true |
| Explizite Erstlektüre bei Band 44 | Ein Ereignis, gelesen=true, Reread bleibt 0 |
| Monatswechsel | Oktober: 1 Erstlektüre/0 Rereads/45 Minuten; November: 1/2/135 |
| Ungültige Reread-Datumskorrektur vor Erstlektüre | Nachprüfung: fester Fachtext, kein Reload/Replay; alte Daten nach bewusstem Reload unverändert |
| Konsolenprüfung | Keine erfassten Fehler/Warnungen, keine nativen Alerts |

Ein erster negativer Datumsversuch zeigte die unveränderte Sicherheitsablehnung.
Die Zuordnung des Frontendtexts ist 403; der Connector lieferte keinen direkt
beobachtbaren HTTP-Status. Die Manual-Session hat 2 Minuten Timeout; ein Ablauf
ist plausibel, aber nicht separat bewiesen. Dieser Versuch belegt keinen
fachlichen Datumsfehler. Erst die spätere Prüfung mit frischer Sitzung bestätigte
die Fachkonfliktbehandlung. Auch dabei keinen HTTP-Status aus der UI ableiten;
der 409-Vertrag ist separat durch MockMvc/DOM abgesichert.

Zwei kleine UI-Nachbesserungen: Die Historiensektion setzt ausdrücklich die
vorhandenen hellen Schrift-/dunklen Hintergrundfarben. Fachliche 409-Antworten
bekommen einen festen S03-Text; Fehlerbodys bleiben ungelesen, 403 und alle
Sicherheitsregeln unverändert. Jede unbestätigte Aktion bleibt bis Reload gesperrt.

Offline-Nachprüfung: 253 DOM-Tests, 94 manualSafetyTest-Fälle, jeweils 0 Fehler/
übersprungen; 9 der DOM-Fälle prüfen die Historienoberfläche. Identischer Gradle-
Dry-Run vor dem Lauf. pnpm run test:dom scheiterte zuvor an der lokalen
Sandbox-/Projektregistrierung (EPERM), bevor Tests liefen; deshalb denselben
package.json-Testbefehl direkt als node --test src/test/js/*.test.cjs ausgeführt.
Keine Abhängigkeiten installiert oder heruntergeladen.

Die korrigierte Paketierung liegt separat unter
.gradle/s03-ui-recheck-20261116/mangalib-manual-test-s03.jar, SHA-256
a9f79022e7a16542b0f44ce50e06417dc9bf4aef566945afef5941fc363bcde3.
Ein lokales Init-Skript ändert nur das Ausgabeverzeichnis des bestehenden
manualBootJar; kein altes JAR ersetzt. ValidateOnly, Schutztests und Browser-
Nachprüfung als PID 3444 bestanden. Kontrast und Fachkonflikt im Browser bestätigt.
Beide eigenen Starts anschließend per Ctrl+C geordnet beendet; Tomcat-/JPA-
Shutdown bestätigt, Prozess-/Listenerfreiheit separat geprüft.

Datenerhaltung (private ignorierte JSON-Nachweise, keine Rohdaten im Git):

```text
Vor Browser: e870d15c370561df7c79d0c1bcc4892ed5dd1576f872277eb3d4bb239e1b654e
Nach Browser: f19146da1fbc0c7840242fee81ec12d87e6a8d90720ac5927298bf1596c42c33
Nach UI-Nachprüfung: identisch mit Nach Browser
```

Vollständiger zeilenweiser Vergleich: alle bisherigen 16 Reihen/Details, 42 Bände,
11 Einkäufe/Details, 17 Stammdaten und alle Schemaabschnitte exakt unverändert.
Ergänzt ausschließlich Reihe/Details 17, Bände 43/44 und vier Ereignisse für diese
Bände. Die zwei historischen NULL-Rereads und positive Altzähler bleiben erhalten.
Die neuen Testdaten verbleiben bewusst. Kein Produktionszugriff und kein Reset.

### Weiterhin offen vor vollständigem S03-Abschluss

- Echte parallele S03-Transaktionen mit anschließendem erneutem Laden; bisher
  Fake-TM-Konkurrenzprüfung, reale Rollbackfälle und sequentielle Browsercommits.
  Doppelklickschutz ersetzt keinen unabhängigen parallelen Servernachweis.
- Abschließende umfassende Regression unter Berücksichtigung des additiven Schemas;
  keine unveränderten historischen T03-Opt-in-Matrizen mit alten Baselines starten.
- Produktionsmigration, Backup/Wiederherstellungsprobe, dortiger ausdrücklicher
  einmaliger Reread-Reset und Aktivierung bleiben gesonderte Eigentümerschritte.
- Git-Abschluss erst gesondert. S03 noch nicht pauschal als abgeschlossen markieren.

Die folgenden Abschnitte bleiben historische Zwischenstände; Rechteblocker und
damals ausstehende Schema-/Rollback-/Browserprüfungen wurden wie oben beschrieben
überwunden. Andere dort genannte offene Punkte bleiben gemäß aktueller Liste offen.

## Manuelle Abnahme 16.11.2026 – blockiert durch fehlende Tabellenrechte

Der Eigentümer bestätigte die manuelle Tabellenanlage. Vor Anwendungsstart wurde
das bestehende READ-ONLY-Snapshotverfahren in einem separaten Helfer um die neue
Tabelle ergänzt (elf Tabellen, 16 Abschnitte; Originalhelfer unverändert).
Der Lauf scheiterte ohne gespeicherten Snapshot und ohne Schreiboperation.
Eine begrenzte Tabellen-Lesediagnose ergab MySQL-Fehlercode 1142 (SELECT verweigert).
Eine davon getrennte reine Identitätsabfrage bestätigte mangalib_manual_test und
den tatsächlichen Account mangalib_manual_test_user@127.0.0.1. Keine Rohfehler,
Passwörter oder Datensätze ausgegeben, keine Anwendung oder Browser gestartet.
Die fehlgeschlagene Snapshotprüfung wurde nicht automatisch wiederholt.

Die dokumentierte manuelle Rechtevergabe ist tabellenweise. Für die neue Tabelle
fehlt die DML-Freigabe. Separater minimaler Admin-Schritt vorbereitet:
database/migrations/S03_001_reading_events/03_manual_runtime_grants.sql.
Er gewährt nur SELECT/INSERT/UPDATE auf der exakt genannten neuen Tabelle für
das bestätigte Konto. Kein DELETE, keine DDL-/globalen Rechte, keine Wildcards
oder GRANT OPTION. Nicht von Codex ausgeführt. Nach Eigentümer-Ausführung erst
Nachprüfung/Erhaltung und danach Browserstart. Noch kein Nachweis der neuen
manuellen Tabellenstruktur, da SELECT zuvor verweigert wurde.

## Aktueller Stand 16.11.2026 – Testmigration und Rollbackabnahme bestanden

Der Eigentümer hat 01_mangalib_test.sql ausgeführt. Die anschließende geschützte
AFTER-Prüfung bestand: elf InnoDB-Tabellen, neue Ereignistabelle leer, Unique-Key
und RESTRICT-FK korrekt, 78 Stammdatensätze und beide alten Teilfingerabdrücke
identisch. Neuer vollständiger Nachmigrationsfingerabdruck:
77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f.
Er unterscheidet sich wegen der zusätzlichen Struktur erwartungsgemäß vom alten
Gesamtfingerabdruck; die Erhaltung wurde über die getrennten Hashes nachgewiesen.

s03HistoryTest anschließend tatsächlich ausgeführt: zwei bestanden, 0 Fehler,
0 übersprungen. Native Ereignis-INSERT/UPDATEs, Band-/Reihenänderung,
Wiederholung mit gleicher UUID, Datumskorrektur inklusive Revision,
Lesezeitprojektion nach Flush/Clear, Statusreset ohne Historieverlust geprüft.
Beide Fälle vergleichen nach erzwungenem Rollback alle alten Zeilen, sämtliche
Schema-Metadaten und die neue Ereignistabelle vollständig. Keine verbliebenen
Fixtures. AUTO_INCREMENT-Verbrauch ist zulässig und nicht zurückgesetzt.
Eine zusätzliche unabhängige lesende Abschlusskontrolle nach beiden Fällen
bestätigte erneut exakt den Nachmigrations-Gesamtfingerabdruck und beide alten
Teilfingerabdrücke; die Ereignistabelle ist weiterhin leer.

Vor dem Lauf wurde die testlokale Fixture korrigiert: Der historische
PersistenceTestFixtures-Helfer erfordert eine Spring-Testtransaktion und passt
nicht in den hier geprüften Coordinator. Die neue kleine S03-Fixture prüft
aktive Transaktion, exakte Testidentität und InnoDB für sämtliche betroffenen
Tabellen und referenziert Stammdaten nur lesend. Gemeinsame Guards unverändert.
Diese Korrektur erfolgte vor dem ersten Schreibtest, kein fehlgeschlagener
Datenbanktest und keine automatische Bereinigung.

## Nächste Admin-Aktion für die Browserabnahme

Das bestehende READ-ONLY-Snapshotverfahren wurde in einem neuen ignorierten
Nachweisverzeichnis .gradle/s03-manual-preflight-20261116 verwendet, historische
Dateien unverändert. Ausschließlich 127.0.0.1:3306/mangalib_manual_test mit
mangalib_manual_test_user, Passwort im Kindprozess, READ ONLY und ROLLBACK.
Zehn InnoDB-Tabellen und signed BIGINT für baende.id bestätigt. Ausgangsbestand:
16 Reihen/Details, 42 Bände, 11 Einkaufspositionen/Details und 17 Stammdaten.
Keine Schreiboperation oder Anwendung gestartet.

Separater manueller Snapshot SHA-256:
d04fa75ecaa4f88291c9a57295e4b3f080ff7718e6264fc090d50247b57e5639.
Dieses JSON-Prüfformat ist nicht mit dem JUnit-Fingerabdruck gleichzusetzen.

Bereit für die vom Eigentümer gewählte eigene Admin-Ausführung:
database/migrations/S03_001_reading_events/01_mangalib_manual_test.sql
SHA-256 1d869637abf4c6138b1c359592e58da694ec432d951c328fe6fc98d62835fb64.
Genau ein voll qualifiziertes CREATE TABLE in mangalib_manual_test. Keine
Bestandsänderung, kein Reset, keine Initialisierung, keine Rechteänderung.
Noch nicht ausgeführt. Vor dem Anwendungsstart folgen Schema-/Erhaltungsvergleich
und die unveränderten Laufzeitguards. Der unten dokumentierte frühere Blocker
für mangalib_test ist inzwischen erledigt. Reale Commit-/Konkurrenz- und
Browserabnahme bleiben bis zu ihren tatsächlichen Nachweisen offen.

Konsolidiert am 16.11.2026. Branch codex/s03-reading-history, noch uncommittet.
Die nachstehenden Prüfungen wurden am 15.11.2026 tatsächlich ausgeführt.

## Abnahmestart 16.11.2026 – wartet auf Admin-Ausführung

Der Eigentümer hat die S03-Abnahme freigegeben und übernimmt das geprüfte
Test-DDL selbst in seiner Admin-Sitzung. Codex hat zuvor die unveränderten
Guards/Konfigurationen, Passwortpräsenz ohne Wertausgabe, Artefakthash und den
identischen Offline-Dry-Run geprüft. s03SchemaTest bestand anschließend mit
1 Test, 0 Fehlern/übersprungenen Fällen. Gesamt-, Altdaten- und Altschemahash
entsprechen weiterhin exakt den unten dokumentierten Referenzen.
Keine Schreiboperation, kein Anwendungslistener oder Browserstart.

Noch keine Tabellenanlage durch Codex, keine AFTER-Prüfung und kein s03HistoryTest.
Weiterarbeit nach Bestätigung der Admin-Ausführung ausschließlich des
hashgebundenen 01_mangalib_test.sql. Testkontorechte bleiben unverändert.

## Erreicht

| Anforderung | Implementierung / Nachweis |
|---|---|
| Erstlektüre | Echte Statusänderungen in Band-/Reihenbearbeitung erzeugen genau eine datierte Erstlektüre; Metadatenspeichern und Statusreset erzeugen keinen Reread |
| Expliziter Reread | Eigene Aktion, Datum und UUID; Zähler/Ereignis gemeinsam im vorhandenen LibraryWriteCoordinator |
| Wiederholung/Konkurrenz | Gesamte Sammelaktionszuordnung und ursprünglicher Payload geprüft, Datenbank-Unique-Key vorgesehen; Fake-Transaktions-/Konkurrenztests bestanden |
| Datumskorrektur | Revisionsprüfung, ursprüngliches Aktionsdatum bleibt für Replays erhalten, keine Zähleränderung, keine Stornierung |
| Monatszahlen / Lesezeit | Getrennte Erstlektüren/Rereads, Faktor beim Abschluss gespeichert, 45 Minuten × Faktor; unbekannte Altdaten nicht datiert, Gesamtzähler nicht doppelt addiert |
| Bestandskompatibilität | Standardmäßig deaktiviert; keine neue JPA-Entityregistrierung, keine automatische DDL oder Zählerinitialisierung |
| Oberfläche | Zusätzlicher Bereich im Banddialog und Monatsübersicht im vorhandenen Design; unsichere Antwortinhalte bleiben Text; unbestätigte Aktion bleibt über Dialogwechsel gesperrt |
| Vorprüfung | Reine SELECT/SHOW-Prüfung gegen mangalib_test erfolgreich, aktuelle Identität/Rechte/Struktur und Vorher-/Nachher-Erhaltung bestätigt |
| Reale S03-Persistenz | Zwei Opt-in-Rollbackfälle vorbereitet und kompiliert, noch nicht ausgeführt |
| Migration / echte Browserabnahme | Noch nicht ausgeführt; S03 ist insgesamt noch nicht abgenommen |

234 ausgewählte Java-Tests, 94 datenbankfreie Manual-Prüfungen und 250 DOM-Tests
bestanden, keine Fehler/übersprungenen Fälle in diesen Abschlussläufen.
Offline bootJar und Opt-in-Kompilierung erfolgreich. Manual-Kandidat separat
gebaut, alle vorher bestehenden Manual-JARs unverändert erhalten.
Bei Fortsetzung am 16.11.2026 neue Opt-in-Lesezeitassertions nur kompiliert,
keine Datenbanktests wiederholt. 92 Kandidatenklassen bytegleich zur aktuellen
Kompilierung. Das DDL ist gezielt versionierbar und mit eol=lf hashstabil.
Startskript mit -ReadingHistory -ValidateOnly bestanden: keine Datenbank und
kein HTTP-Listener. Dasselbe Flag weist ein altes Artefakt ohne S03 ab.

## Tatsächliche lesende Schemabefunde

- Ausschließlich localhost:3306/mangalib_test, Account mangalib_test_user@localhost.
- Profil test, GuardedTestDataSource und bestehende Konfigurations-/Verbindungs-Guards.
- Bestehende serverseitige Minimalrechteprüfung bestanden.
- MySQL 8.0.36; baende.id ist signed BIGINT; zehn InnoDB-Tabellen, 78 Stammdatensätze.
- Vorhandene Geschäftszeilen wurden vollständig gelesen und unverändert verglichen,
  weder als leer vorausgesetzt noch bereinigt. Keine INSERT/UPDATE/DELETE/DDL.

Referenzen für diese konkrete Aufnahme, keine Umdeutung früherer T03-Nachweise:

```text
Gesamt:    0330d003b812c6cfa4d676ab4898f4a49c17c8020a77cc40f4852c2aeac285b0
Altdaten:  3b629c3faeac198c4d83cf8df033257724a289eca71b46d17d344fc5d523d0b9
Altschema: 9ca0a1402263a0830ed9df7ffbd50edbee22f50f02ad357bf91ed468c166a6e0
```

Der erste lesende Lauf erfasste die aktuelle Referenz. Ein zweiter, um getrennte
Erhaltungshashes ergänzter Lauf bestätigte denselben Gesamtfingerabdruck.
Beide Läufe bestanden; keine Fehlerwiederholung, keine Schreiboperation.

## Konkretes Test-DDL – noch gesperrt

Datei: database/migrations/S03_001_reading_events/01_mangalib_test.sql

```text
SHA-256: a949e0d93acf87b499ed0813d6ce2ed73912c8d0e6e78c13ab91704da982567e
```

Genau ein vollständig qualifiziertes CREATE TABLE für
mangalib_test.band_reading_events, mit Unique-Index und RESTRICT-Fremdschlüssel.
Keine Veränderung bestehender Tabellen/Zeilen, keine Zählerinitialisierung,
kein DROP und kein IF NOT EXISTS. MySQL-DDL ist kein normaler Rollbackvorgang.
Das Skript benötigt eine ausdrücklich freigegebene Admin-Ausführung; die
Laufzeitkonten erhalten keine DDL-Rechte. Kein administrativer Zugang wird
aus Testpasswörtern abgeleitet oder im Projekt gespeichert.

Nächste Freigabe sollte ausschließlich umfassen:

1. Erneute lesende Baseline-/Hashprüfung vor der Änderung.
2. Genau dieses CREATE TABLE ausschließlich in mangalib_test, über einen
   vom Eigentümer benannten sicheren Adminweg oder durch dessen eigene Sitzung.
3. Danach die lesende AFTER-Prüfung: elf Tabellen, neue Tabelle leer,
   vorgeschriebene Indizes/FK, beide alten Teilfingerabdrücke identisch.
4. Nur nach erfolgreicher AFTER-Prüfung die zwei vorbereiteten S03-Rollbackfälle.
   Nur neue synthetische Graphen/Ereignisse; bestehende Daten und Schema vor/nach
   jeder Prüfung identisch. AUTO_INCREMENT-Verbrauch bleibt möglich.

Alle Gradle-Aufrufe offline/ohne Daemon und vorab mit identischem --dry-run.
Die folgenden Befehle sind vorbereitet, keine Ausführungsfreigabe:

```powershell
.\gradlew.bat --offline --no-daemon -Ps03SchemaApproval=READ_ONLY_CURRENT_TEST_SCHEMA -Ps03ExpectedFingerprint=0330d003b812c6cfa4d676ab4898f4a49c17c8020a77cc40f4852c2aeac285b0 s03SchemaTest

# Erst NACH gesondert genehmigtem DDL, ohne Gesamtfingerabdruck des alten Schemas:
.\gradlew.bat --offline --no-daemon -Ps03SchemaApproval=READ_ONLY_CURRENT_TEST_SCHEMA -Ps03SchemaPhase=AFTER -Ps03ExpectedRows=3b629c3faeac198c4d83cf8df033257724a289eca71b46d17d344fc5d523d0b9 -Ps03ExpectedOldSchema=9ca0a1402263a0830ed9df7ffbd50edbee22f50f02ad357bf91ed468c166a6e0 s03SchemaTest

.\gradlew.bat --offline --no-daemon -Ps03Approval=ROLLBACK_ONLY_MIGRATED_TEST_SCHEMA s03HistoryTest
```

Bei Identitäts-, Rechte-, Schema- oder Fingerabdruckabweichung STOP, keine
Bereinigung und keine automatische Wiederholung. Keine alten Opt-in-T03-Matrizen
ausführen: deren historische Ausgangsbedingungen bleiben unverändert.

## Noch ausstehende Schritte bis S03-Abschluss

- Reale Migration und Rollback-Abnahme wie oben; MySQL-Abfragen, JDBC-Bindung und
  JPA-Projektion sind durch Mocktests allein nicht bewiesen.
- Reale Commit-/Konkurrenzprüfung nach Migrationsabnahme konkretisieren; bisher
  S03-Konkurrenznachweis mit Fake-TM, vorhandene T03-Grenzen wiederverwendet.
- Manual-Schema separat prüfen/migrieren; keine Übernahme der Test-DDL auf Verdacht.
- Echte isolierte Browserabnahme ausschließlich eigener synthetischer S03-Bände.
- Gewünschter einmaliger Reread-Reset auf 0 in der später ausdrücklich genannten
  Zielumgebung, bei Produktion erst nach Backup/Wiederherstellungsnachweis und
  separater Freigabe. Positive bestehende Test-/Manual-Zähler bleiben erhalten.
- Anschließend Gesamtregression und Git-Abschluss gesondert. Keine Commits/Pushes
  oder Produktions-/Manual-DB-Zugriffe sind durch diese Vorbereitung erfolgt.

## Separater Browserkandidat

build/libs/mangalib-manual-test-s03.jar, SHA-256:
2dfeddab263e54a1c2f1b95a24166bff62440ab98bd1e901166c6fabe9625fe0

Offline erstellt über -Ps03ManualCandidate=true manualSafetyTest. Dieses Flag
ändert ausschließlich den festen Ausgabedateinamen des bestehenden isolierten
Paketierungswegs. Der Start verwendet weiter nur den Manual-Launcher, das feste
Profil/Ziel und synthetische Scraper. Der zusätzliche Skriptschalter
-ReadingHistory setzt nur den fachlichen Aktivierungswert im Kindprozess;
keine freien Spring-/JVM-/Datasource-Overrides und keine Änderung der DB-Guards.
ValidateOnly ist keine Schemaabnahme und kein Browserstart.

## S03 – Layout, echte Konkurrenz und Gesamtregression (16.11.2026)

Aktueller Abschlussstatus: REGRESSION_BLOCKED, kein Git-Abschluss.

- Historie in denselben Statistikcontainer verschoben. Unter 1000px reserviert
  die Sidebar keine linke Inhaltsbreite. Monats-SVG mit fester Höhe 300px und
  verfügbarer Breite; keine 650px-Mindestbreite für das Monatsdiagramm mehr.
- Reale rein lesende Browserprüfung bei 1280x900 und 600x900 bestanden. Diagramm
  und Historie jeweils gleich breit (945px bzw. 561px), kein horizontaler
  Seitenüberlauf, keine erfassten Konsolenfehler. Kandidat separat unter
  .gradle/s03-final-20261116, SHA-256
  097f9c54836397d7c649a802bbaa7349806f0ee24cba46667ccefe0b1452a2c5.
  ValidateOnly und eigene PID 24272/127.0.0.1:18080 geprüft; anschließend Ctrl+C,
  Tomcat/JPA geordnet geschlossen, Prozess beendet, Port frei. Nur lesende UI.
- Neuer strikt getrennter s03ConcurrencyTest, Freigabewert
  -Ps03ConcurrencyApproval=OWN_SYNTHETIC_COMMIT_AND_CLEANUP. Ein realer Test mit
  zwei Threads und echten Transaktionen bestanden: identische FIRST_READ-UUID
  erzeugt genau ein Ereignis; zwei unterschiedliche REREAD-UUIDs erzeugen zwei
  weitere Ereignisse und Reread 2 nach Clear/Reload. Nur ein eigener neuer Graph
  (Reihe/Details/zwei Bände) wird committed und nach Threadabschluss anhand
  Titel und exakter Band-IDs wieder entfernt. Vorher/Nachher aller Altzeilen,
  Schema-Metadaten und Ereigniszeilen identisch. Kein crashfestes Cleanupversprechen;
  bei fehlendem Threadabschluss keine Entfernung. AUTO_INCREMENT nicht zurückgesetzt.
- s03HistoryTest bleibt ausdrücklich auf die zwei Rollbackfälle beschränkt und
  bestanden. Neuer Commitfall durch eigene Filter-/Freigabebarriere getrennt.
- Vorher und nach Gesamtregression geschützter s03SchemaTest bestanden:
  mangalib_test/mangalib_test_user, Profil test, Minimalrechte, elf InnoDB-Tabellen,
  78 Stammdaten, Gesamtfingerabdruck unverändert
  77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f.
  Beide früheren Altdaten-/Altschemahashes identisch. Keine Migration/DDL.
- Gesamtaufuf test manualSafetyTest bootJar: test meldete 1456 Fälle,
  1369 bestanden, 85 fehlgeschlagen, 2 übersprungen. bootJar wurde vorher gebaut;
  manualSafetyTest danach separat mit 94/94 bestanden. Alle Gradle-Aufrufe
  offline/ohne Daemon, vorab jeweils identischer Dry-Run; kein clean.
- 84 Fehler betreffen ältere Integrationstests mit äußeren Spring-Testtransaktionen:
  LegacyEditIntegrationTest 39, MvcContractTest 9, ReadingStatusIntegrationTest 34,
  RequestLimitsIntegrationTest 1, ServiceTest 1. Der Coordinator verbietet eine
  äußere Transaktion ausdrücklich; ServiceTest zeigt genau diese Exception.
  HTTP-500 statt erwartetem Erfolgs-/Validierungsstatus ist damit nicht als grün
  umzudeuten. Keine Produktionsbarriere und keine Assertions abgeschwächt.
- Ein weiterer alter Unitfall BandIntegrityCharacterizationTest verwendete eine
  Kaufbestätigung ohne den seit M01 erforderlichen purchaseToken. Nur Fixture
  ergänzt; alle 29 Klassenfälle anschließend datenbankfrei bestanden. Der obige
  Gesamtlauf bleibt historisch fehlgeschlagen; keine Behauptung einer grünen
  Gesamtwiederholung. Beide übersprungenen Fälle liegen in ShoppingEditIntegrationTest
  und VolumeImportIntegrationTest; bestehende Platzhalter, nicht neu deaktiviert.
- DOM-Suite 254/254 bestanden, einschließlich Layout-/Strukturregression.
- Manueller Endsnapshot unverändert
  f19146da1fbc0c7840242fee81ec12d87e6a8d90720ac5927298bf1596c42c33.
  Keine manuellen Schreibaktionen in der Layoutnachprüfung, keine Produktions-
  verbindung. Alle alten Manual-JARs erhalten, neue Ausgaben separat.

Nächster notwendiger Arbeitsschritt: ältere Integrationstests auf die tatsächliche
T03-Transaktionsgrenze portieren, mit gleichwertigen echten Commit-/Rollback- und
Bestandserhaltungsnachweisen. Keine testlokale Umgehung des Coordinators, kein
Ignorieren der 84 Fehler. Danach Gesamtregression erneut ausführen. Git-Staging,
Commit, Push und Merge sind bis dahin nicht vorbereitet/freigegeben; Arbeitsstand
bleibt uncommittet. Produktionsmigration/-reset/-aktivierung weiterhin gesondert.

## S03 – Abschlussnachtrag 16.11.2026
S03_TECHNICALLY_ACCEPTED: Die älteren Integrationstests sind auf den echten Coordinator mit erzwungenem Rollback portiert. Vollständiger Java-Lauf: 1454 bestanden, 0 Fehler, 2 bestehende M01-/M02-Commit-Opt-ins übersprungen (keine Platzhalter). Alle 29 MVC-Verträge grün; 254 DOM-Fälle erfolgreich. S03-Konkurrenzfall und zwei reale Rollbackfälle bestanden; abschließender lesender Testschemafingerabdruck 77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f unverändert. Frühere REGRESSION_BLOCKED-Nachträge sind damit historische Zwischenstände. Keine Produktionsmigration/-aktivierung oder Reread-Initialisierung; diese bleiben separat abgesicherte Ausrollschritte. Keine Git-Schreibaktionen. Vollständige Abschlussmatrix und Nachweisgrenzen: S03_CLOSEOUT.md.
