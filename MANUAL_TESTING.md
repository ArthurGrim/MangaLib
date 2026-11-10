# Isolierte manuelle Browserabnahme unter Windows

## M03 – Lesezeitschätzung, lesend abgenommen am 10.11.2026

Auf der Statistikübersicht der isolierten manual-test-Instanz wurde die
45-Minuten-Basis angezeigt. Der vorhandene synthetische Bestand ergab
9 Stunden ohne Rereads und 24 Stunden bei eingeschalteter Option für bekannte
Rereads. Nach Ausschalten wurden wieder 9 Stunden angezeigt. Der Hinweis auf
zwei unbekannte/ungültige Reread-Werte blieb sichtbar. Keine erfassten
Konsolenfehler und keine Browser-Schreibaktion. Eigener Launcher anschließend
per Ctrl+C geordnet beendet. Bei späteren Änderungen Anzeige, Sammelbandfaktor,
Hinweise und beide Checkboxzustände erneut prüfen.

> Aktueller T02-Abschlussstand (02.11.2026): [T02_CLOSEOUT.md](T02_CLOSEOUT.md). Technisch abgeschlossen für das lokale Einzelbenutzermodell. Codex bestätigte die 413-UI-/Reload-Behandlung mit dokumentierter Statusgrenze; der Projekteigentümer bestätigte die letzte echte HTTP-415-Abnahme. Historische Nachträge bleiben erhalten.


Stand: 11.10.2026. **Einrichtung und erste Browserabnahme durch den Projekteigentümer abgeschlossen.** Ergebnisse und Fehlernachbereitung stehen in Abschnitt 10. Die Abschnitte 2–6 beschreiben den historischen Vorbereitungs-/Ersteinrichtungsweg und bleiben für zukünftige **neue, leere** Umgebungen erhalten. Die bestehende manuelle DB nicht neu einrichten, überschreiben oder erneut mit Fixtures befüllen. Die 179 bisherigen Java-/Spring-Tests bleiben eine getrennte historische Baseline; die Nachbereitung erlaubt ausschließlich datenbankfreie Prüfungen, keinen DB-/Anwendungs-/Browserstart.

## 1. Betriebsgrenzen

| Bestandteil | Festlegung |
|---|---|
| Datenbank / Laufzeitkonto | `mangalib_manual_test` / `mangalib_manual_test_user` |
| Passwort | ausschließlich Prozessvariable `MANGALIB_MANUAL_DB_PASSWORD` oder sichere interaktive Abfrage im Startskript |
| Profil / HTTP | nur `manual-test`, `127.0.0.1:18080`, `http://localhost:18080/home` |
| Artefakt | `build/libs/mangalib-manual-test.jar`, eigener Launcher |
| Initialisierung | ausschließlich manuelle einmalige Einrichtung; Anwendung validiert nur |
| Scraper | synthetische Indizes **900001 / 900002**; andere Indizes scheitern |
| Session | HttpOnly, SameSite=Strict, Cookie-Tracking; für Ablaufprüfung 2 Minuten Inaktivität |

Der Launcher ist kein normaler `bootRun` und kein JUnit-Test. `GuardedTestDataSource`, Testprofil und Spring-TestContext-Registrierung bleiben unverändert. Das normale JAR enthält keine manuellen Klassen/Fixtures. Das manuelle JAR enthält keine Standard-`application.properties`, keinen normalen Main-Einstieg, keine Live-Scraper, Selenium, WebDriverManager oder DevTools. Beide normalen Scraperpfade delegieren über `ScraperGateway`; im regulären Kontext bleibt das bisherige Verhalten erhalten.

Das Startskript und der Launcher weisen externe `SPRING_*`-/Server-/Management-/Hibernate-/Logging-Konfiguration und JVM-Optionsvariablen ab. Auch direkt übergebene Launcher-Konfigurationsargumente sind gesperrt. Der feste Konfigurationsort ersetzt Boots Standardsuche. Profil, JDBC-Ziel/Benutzer/Passwortquelle, DDL-/Init-/Migrationseinstellungen und Konfigurationsteilbäume werden vor dem Kontext und nochmals vor regulären Beans geprüft. Alternative, ersetzte oder vorzeitig erzeugte DataSources und fehlende/ersetzte Doubles führen zum Abbruch. Vor jeder Verbindung erneute Prüfung; an JDBC gehen ausschließlich feste manuelle Parameter. Nach Anmeldung wird die tatsächliche DB-/Benutzeridentität lesend geprüft, bevor Hibernate die Verbindung bekommt.

`-ValidateOnly` lädt nur Konfiguration und kontrolliert das Artefakt: **kein Spring-Kontext, kein JDBC und kein HTTP-Listener**. Das Passwort muss vorhanden sein, wird aber nicht auf Gültigkeit geprüft. Ebenso bestätigt ValidateOnly weder Serverrechte, Schemakompatibilität noch freie Ports. Bei echtem Start kontrolliert das Skript den Port; die vorhandene Sicherheitskonfiguration prüft zusätzlich die fertige Server-Factory.

Manuelle HTTP-Schreibaktionen committen bewusst in der dritten Datenbank; kein JUnit-Rollback. MySQL-Rechte sind eine unabhängige Schutzgrenze und administrativ zu bestätigen. Dies ist keine Sandbox gegen beliebigen geänderten Java-Code, manipulierte Artefakte, JVM-Agenten oder andere lokale Programme.

## 2. Nur nach Freigabe: Administrator

Historischer Ersteinrichtungsweg, **keine Anweisung zur Wiederholung in der bestehenden DB**. MySQL-Client/Serverversion und Zielinstanz kontrollieren. Ein aktueller kompatibler MySQL-8.x-Client wird für den Export vorausgesetzt. Codex hat keine Serverversion abgefragt; der Eigentümer meldet MySQL 8. Keine globalen Servereinstellungen ändern.

```sql
SELECT VERSION();
SELECT SCHEMA_NAME FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'mangalib_manual_test';
```

Bei vorhandenem Ziel abbrechen und prüfen; nicht löschen oder überspielen. Zeichensatz/Collation der Quelle **mangalib_test** lesend feststellen:

```sql
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'mangalib_test';
```

Dann ausschließlich die neue DB erstellen, beispielsweise **nur wenn die Abfrage utf8mb4/utf8mb4_0900_ai_ci bestätigt**:

```sql
CREATE DATABASE mangalib_manual_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

In MySQL Workbench unter Users and Privileges zwei **neue** Konten mit eigenen sicheren Passwörtern und Host **127.0.0.1** anlegen: `mangalib_manual_setup` und `mangalib_manual_test_user`. Keine Rollen oder globalen Rechte auswählen; insbesondere kein `%`-Host. Passwörter nur im vorgesehenen verdeckten Feld eingeben, nicht in SQL-Editor/History/Dateien oder an Codex übermitteln. Keine Passwortliterale oder generierten Passwortausgaben in diese Anleitung aufnehmen. Die Konten werden durch GRANT nicht automatisch erstellt.

Einrichtungskonto: exakt zehn Tabellenobjekte, keine Schema-Wildcard nötig. CREATE erlaubt Grants schon vor Erstellung der Tabellen. [MySQL GRANT](https://dev.mysql.com/doc/refman/8.4/en/grant.html).

```sql
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.verlage TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.status TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.typen TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.formate TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.sammelbaende TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.mangareihe TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.mangadetails TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.baende TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.einkaufsliste TO 'mangalib_manual_setup'@'127.0.0.1';
GRANT CREATE, REFERENCES, SELECT, INSERT ON mangalib_manual_test.einkaufslistedetails TO 'mangalib_manual_setup'@'127.0.0.1';
```

Keine Rechte auf `mangalib`, `mangalib_test`, `*.*`, keine DDL-Löschrechte, kein GRANT OPTION. `SHOW GRANTS FOR 'mangalib_manual_setup'@'127.0.0.1';` lokal prüfen. Vorhandene Rechte/Accounts nicht nebenbei verändern. In den folgenden Client-Verbindungen muss CURRENT_USER das erwartete Konto bestätigen; bei anderer Konto-/Hostauflösung nicht durch breitere Rechte reparieren.

## 3. Nur nach Freigabe: Struktur übernehmen, keine Daten

Die alten `SQLQuery.sql` / `Inserts.sql` **nicht ausführen**: fester Produktivname und ungeprüftes Bootstrap-Schema. Stattdessen rein lesender Export aus dem bereits validierten **mangalib_test**, ohne Datensätze, Trigger, Routinen oder Events. Keine Produktionsverbindung. Testkonto braucht die bestehenden SELECT-Rechte; keine zusätzlichen globalen Rechte für den Dump vergeben.

In PowerShell, Clientpfad gegebenenfalls vollständig angeben:

```powershell
Set-Location '<lokales MangaLib-Projektverzeichnis>'
$manualDirectory = Join-Path $env:LOCALAPPDATA 'MangaLib/manual-test'
New-Item -ItemType Directory -Path $manualDirectory -Force | Out-Null
$rawSchema = Join-Path $manualDirectory 'schema-source.sql'
if (Test-Path -LiteralPath $rawSchema) { throw 'Exportdatei existiert bereits.' }
mysqldump.exe --no-defaults --no-login-paths --host=127.0.0.1 --port=3306 --protocol=TCP `
  --user=mangalib_test_user --password --default-character-set=utf8mb4 `
  --no-data --no-tablespaces --skip-lock-tables --skip-triggers `
  --skip-add-drop-table --skip-comments --set-gtid-purged=OFF `
  --result-file="$rawSchema" mangalib_test
if ($LASTEXITCODE -ne 0) { throw 'Export fehlgeschlagen; Datei nicht verwenden.' }
$preparedSchema = Join-Path $manualDirectory 'schema-manual.sql'
.\scripts\Prepare-ManualSchema.ps1 -SourceFile $rawSchema -OutputFile $preparedSchema
```

Das Hilfsskript führt keinen Client/SQL aus: exakt zehn bekannte InnoDB-Tabellen, keine Daten-/Zusatzanweisungen, bekannte FK-Ziele; vollständige Qualifizierung aller Tabellen und FK-Referenzen auf `mangalib_manual_test`, Eltern vor Kindern, keine übernommenen AUTO_INCREMENT-Stände, kein Überschreiben. Nicht unterstützte DDL-Klauseln werden bewusst abgewiesen. Es ist ein enger Prüfer für vertrauenswürdig erzeugte Schemaexports, kein allgemeiner SQL-Parser. Bei einer neuen Einrichtung den echten Export zusätzlich vollständig prüfen und Schema-Metadaten mit der Quelle vergleichen. Codex hat keine DB-Verbindung ausgeführt; ein vollständiger dokumentierter Metadatenvergleich wurde auch mit dem Abnahmebericht nicht geliefert. Kein automatischer Export/Import beim Anwendungsstart.

[MySQL empfiehlt `--result-file` für Windows](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html); keine PowerShell-Umleitung `> schema.sql` verwenden.

## 4. Nur nach Freigabe: Einrichtungszugang, Import, Fixtures

Mit dem eingeschränkten Setup-Konto interaktiv verbinden; Passwort nur am Prompt:

```powershell
mysql.exe --no-defaults --no-login-paths --skip-force --host=127.0.0.1 --port=3306 --protocol=TCP `
  --user=mangalib_manual_setup --password --default-character-set=utf8mb4 mangalib_manual_test
```

Zuerst rein lesend:

```sql
SELECT DATABASE(), CURRENT_USER();
SELECT COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'mangalib_manual_test';
```

Erwartet: manuelles Konto, manuelle DB, **0 Tabellen**. Client mit `quit` verlassen. Danach Import getrennt im Batchmodus, **ohne --force**, bei Fehler sofort anhalten. SOURCE ist ein lokaler mysql-Clientbefehl; absolute Pfade mit `/` verwenden. Beispiel PowerShell:

```powershell
$sourceCommand = 'source ' + $preparedSchema.Replace('\', '/')
mysql.exe --no-defaults --no-login-paths --skip-force --host=127.0.0.1 --port=3306 --protocol=TCP `
  --user=mangalib_manual_setup --password --default-character-set=utf8mb4 `
  --database=mangalib_manual_test --execute="$sourceCommand"
if ($LASTEXITCODE -ne 0) { throw 'Schemaimport fehlgeschlagen; keine Fixtures ausführen.' }
$fixtures = (Resolve-Path 'src/manualTest/setup/fixtures.sql').Path.Replace('\', '/')
mysql.exe --no-defaults --no-login-paths --skip-force --host=127.0.0.1 --port=3306 --protocol=TCP `
  --user=mangalib_manual_setup --password --default-character-set=utf8mb4 `
  --database=mangalib_manual_test --execute="source $fixtures"
if ($LASTEXITCODE -ne 0) { throw 'Fixtureimport fehlgeschlagen; nicht starten.' }
```

Fixtures einmalig in der frisch angelegten leeren Struktur: **17 Stammdaten**, **14 Geschäftssätze** (2 Reihen, 2 Details, 4 Bände, 3 Einkaufseinträge, 3 Einkaufsdetails). Feste IDs, keine UPDATE/DELETE/REPLACE/Upserts; erneuter Import scheitert statt zu überschreiben. DML läuft in einer Transaktion, MySQL-Batchfehler dürfen nicht ignoriert werden. DDL selbst ist nicht durch Rollback abgesichert: nach Teilfehler stoppen und untersuchen, kein automatischer Reset.

Vor dem Fixtureimport müssen **alle zehn Tabellen leer** sein; nicht nur deren Existenz prüfen. Danach erwartete Zeilenzahlen: verlage 1, status 4, typen 4, formate 3, sammelbaende 5, mangareihe 2, mangadetails 2, baende 4, einkaufsliste 3, einkaufslistedetails 3. Beispielsweise `SELECT COUNT(*) FROM mangalib_manual_test.baende;` ausschließlich mit dem Setup-/Laufzeitkonto ausführen. Schemavergleich: Spalten, Typen, Nullability, Defaults, Indizes und FK-Regeln zwischen Quelle/Ziel vergleichen; Datenbankname und AUTO_INCREMENT-Laufstand bewusst ausnehmen. Stammdaten vor/nach Abnahme jeweils nach PK sortiert lokal vergleichen. Es werden keine Prüfabfragen gegen mangalib benötigt.

Nach Import InnoDB, zehn Tabellen, FK-/Index-/Spaltenmetadaten, Preise, Beziehungen und Stammdaten überprüfen. Historisch war die reale Fixture-Kompatibilität noch offen; inzwischen meldet der Eigentümer erfolgreichen Kontextstart und funktionierende Datenpfade. Das ist keine erneute SQL-/Metadatenprüfung durch Codex. Setupkonto danach als Administrator sperren:

```sql
ALTER USER 'mangalib_manual_setup'@'127.0.0.1' ACCOUNT LOCK;
```

Laufzeit-Grants **erst nachdem die Tabellen existieren**, als Administrator:

```sql
GRANT SELECT ON mangalib_manual_test.verlage TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT ON mangalib_manual_test.status TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT ON mangalib_manual_test.typen TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT ON mangalib_manual_test.formate TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT ON mangalib_manual_test.sammelbaende TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON mangalib_manual_test.mangareihe TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON mangalib_manual_test.mangadetails TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON mangalib_manual_test.baende TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON mangalib_manual_test.einkaufsliste TO 'mangalib_manual_test_user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON mangalib_manual_test.einkaufslistedetails TO 'mangalib_manual_test_user'@'127.0.0.1';
SHOW GRANTS FOR 'mangalib_manual_test_user'@'127.0.0.1';
```

Globales USAGE bedeutet keine globalen Datenrechte. Alle übrigen Grants/Rollen müssen exakt obigem Umfang entsprechen. Kein Laufzeit-CREATE/ALTER/DROP/REFERENCES, keine Änderung von Stammdaten. Kein Probezugriff auf die Produktivdatenbank zur Rechteprüfung.

## 5. Datenbankfreie technische Vorbereitung

Keine neuen Abhängigkeiten. Lokaler Cache genügte; Offline-Prüfungen:

Historischer Vorbereitungsstand: **44 DB-Guard + 61 lokale Policy + 52 neue manuelle Java-Tests** erfolgreich, zusätzlich **25 PowerShell-Prüfungen und 65 DOM-Tests**, keine Fehler im finalen Lauf. Separates JAR erfolgreich paketiert und geprüft. Frühe Fehler der neuen BootJar-Zielversion, JUnit-Parametersignatur und Java-Versionserkennung wurden korrigiert; keine Schutzregeln abgeschwächt. Vorhandene HotSpot-CDS-Warnung bleibt. Kein voller Integrationslauf und keine neue Daten-/Schema-Messung. Die zusätzlichen Prüfungen der Nachbereitung stehen in Abschnitt 10.

```powershell
.\gradlew.bat --no-daemon --offline test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --no-daemon --offline test --tests de.mangalib.security.LocalSecurityPolicyTest
.\gradlew.bat --no-daemon --offline manualSafetyTest assembleManualTest --dry-run
.\gradlew.bat --no-daemon --offline manualSafetyTest assembleManualTest
.\scripts\Test-ManualScripts.ps1 -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
pnpm run test:dom
```

`manualSafetyTest` verwendet reine Environment-/BeanFactory-/JDBC-Mocks und prüft das echte JAR. Unterprozesse führen ausschließlich `--validate-only` mit synthetischem Passwort aus; kein SpringApplication.run, Kontext-refresh, JDBC oder Listener. PowerShell-Tests erzeugen nur temporäre synthetische SQL-Texte; niemals SQL-Ausführung. JUnit-Test- und manuelle Source-Sets bleiben getrennt.

## 6. Vor erstem Start

1. Diff abnehmen, Artefakt/Prüfberichte kontrollieren; DB-Schritte gesondert freigeben.
2. Einrichtung und Rechte nach Abschnitten 2–4 durchführen/bestätigen. Schema-/Stammdatenausgangszustand lokal dokumentieren.
3. Separates PowerShell-Fenster ohne externe Spring-/Server-/JVM-Overrides. Kein Produktivpasswort erforderlich. Keine Environment-Dumps oder Transcripts mit Secrets.
4. ValidateOnly ausführen: Passwort sicher abfragen lassen oder eigene temporäre Prozessvariable setzen. Es wird weder ins Repository noch in Startargumente geschrieben. Das Skript übergibt ausschließlich den manuellen Zugang an den Kindprozess; andere `MANGALIB_*`-Werte werden dort entfernt. Umgebungswerte sind kein verschlüsselter Secret-Speicher.

```powershell
.\scripts\Start-ManualTest.ps1 -ValidateOnly -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
```

5. **Erst nach gesonderter Startfreigabe**, Vordergrundbetrieb:

```powershell
.\scripts\Start-ManualTest.ps1 -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
```

6. Zweite PowerShell: `Get-NetTCPConnection -State Listen -LocalPort 18080 | Select-Object LocalAddress,LocalPort,OwningProcess`. Ausschließlich `127.0.0.1` mit angezeigter Java-PID. Bei anderer Bindung/Instanz sofort stoppen. Keine Firewall-Freigaben.
7. Neues Browserprofil ohne Erweiterungen, `http://localhost:18080/home`, Banner kontrollieren. Ein Ursprung pro Sitzung; `127.0.0.1` separat prüfen. Keine IPv6-/LAN-/Routerfreigaben. Kontrolliert mit Ctrl+C beenden, anschließend Port prüfen; niemals fremde Prozesse automatisch beenden.

Die lokal gefundene Java-Laufzeit meldet `21` / `21+35` (2023). Die Versionsprüfung akzeptiert sie; ein aktueller Java-21-Sicherheitspatchstand bleibt separat zu klären. Es wurde kein JDK installiert/geändert.

## 7. Browsercheckliste und erwartete Ergebnisse

| Schritt | Erwartung |
|---|---|
| /home, IP-Alternative, IPv4-Listener | 200, Banner, kein Login, ausschließlich Loopback; localhost-Auflösungsprobleme separat melden, nicht Bindung öffnen |
| Cover, Suche/Filter, Navigation | lokale SVG-Cover, keine externen Bildrequests; manueller CSP-Header sperrt externe Bilder/Fetches, nicht bestehende Inline-Skripte |
| Bände ein-/ausklappen | unveränderte Reihenfolge/CSS/Preise und einmalige Klickaktionen |
| S01-Reihe 900001 | vollständiger Titel als Text, kein injiziertes img/event; `window.__manualXss` bleibt undefiniert |
| Sonderzeichen, lokale URL mit %27/%22 | unveränderter Text/URL, keine neuen Attribute; ungültige URL-Proben zusätzlich datenbankfrei über DOM-Tests |
| Anlegen/Bearbeiten/Abbrechen | Abbrechen ohne Speicherung; bewusstes Speichern nach Reload vorhanden, nur manuelle DB |
| Einkauf, einmalige Kaufübernahme neu/bestehend | erwartete Zuordnung und Werte; fachliche Abweichungen protokollieren |
| Einkaufsposition 3 löschen | nur ausgewählte Position gelöscht, Stammdaten unverändert |
| Lesestatus | Band-/Reihenstatus nach Reload prüfen, bekannte M06-Probleme getrennt erfassen |
| Statistik, Jahreswechsel/Cents/Bedienung | Ansichten bedienbar; bekannten Centverlust separat erfassen, keine Nebenbei-Korrektur |
| Schreibrequest | CSRF-Header und Sessioncookie am gleichen Origin; kein Token in URL, keine automatische Wiederholung |
| Cookies / zwei Tabs | HttpOnly, SameSite=Strict, kein Domain-/URL-Tracking; beide Tabs derselben Sitzung funktionieren |
| >2 Minuten vollständig inaktiv / Neustart | alte Seite erhält bei Schreiben 403 und Reload-Hinweis, kein Replay; nach Reload bewusst erneut handeln |
| Host/Origin/Forwarded negativ | 403, kein Gateway-/Datenaufruf; Origin-Fall auch mit gültiger CSRF/Session prüfen, Tokens nur im RAM halten |
| Autofill GET / HEAD / OPTIONS | 405 / 405 / nebenwirkungsfreie OPTIONS; kein Gateway-/Scraperaufruf |
| Autofill POST 900001 / 900002 | synthetische Daten, Preis 6.99, lokales Cover, bisheriges mpUrl-Feld; keine Firefox-/Geckodriverprozesse |
| Abschluss | Schema und 17 Stammdaten unverändert; Geschäftsdaten entsprechen bewusst protokollierten Aktionen |

Der bestehende Autofill erzeugt weiterhin eine echte Manga-Passion-Linkadresse im Response. Nur Ziel kontrollieren, nicht öffnen. Auch statische Footerlinks führen extern; nicht anklicken. Die manuelle Bild-Policy blockiert gegebenenfalls den bestehenden externen Default-Cover; dann fehlt das Bild, ohne externen Request. Keine echte Scraper-Kompatibilitätsabnahme durch Doubles.

Die beiden Double-Indizes existieren bereits als Fixtures. Für eine zusätzliche reine Formulareingabe einen neuen synthetischen Index, beispielsweise 900005, verwenden; diesen nicht an Autofill senden. Statistiken für Fixturejahre 2024/2025 auswählen. Ungültige Band-URLs zusätzlich im Browser rein lokal mit `getSafeBandUrl('javascript:window.__manualXss=1')` beziehungsweise `getSafeBandUrl('not-a-url')` prüfen: jeweils `null`, keine Navigation/Bildzuweisung; nicht als ungültige java.net.URL in die Datenbank schreiben.

Bekannte fachliche Fehler, Browser-/OSIV-Probleme und allgemeine Eingabe-/URL-/Logging-Befunde getrennt dokumentieren. Keine vollen Daten-Fingerabdrücke als unverändert erwarten: manuelle Aktionen verändern die Geschäftstabellen ausdrücklich. Schema und Stammdaten hingegen müssen unverändert bleiben. Kein automatischer Reset/Re-Seeding; Testaktionen bei Wiederverwendung protokollieren.

## 8. Zuständigkeit und aktueller Prüfstand

Codex darf nach aktueller Freigabe Dateien, datenbankfreie Tests, statische Prüfungen und das Abnahme-Artefakt vorbereiten. Der Eigentümer führt nach gesonderter Freigabe Konten-/DB-Einrichtung und erste Browserabnahme durch. Codex führt Datenbankzugriffe und Anwendungsstarts nur nach weiterer ausdrücklicher Freigabe aus. Kein Commit/Push.

Der **historische Vorbereitungsauftrag** prüfte keine echten DB-/Listener-/Browserabläufe. Diese wurden inzwischen durch den Eigentümer durchgeführt (Abschnitt 10), nicht durch Codex wiederholt. Der aktuelle Nachbereitungsauftrag erlaubt weiterhin keinen Anwendungsstart und keine Verbindung zu irgendeiner DB. Serverrechte und vollständige Schema-Metadaten wurden von Codex nicht administrativ nachgeprüft; der Guard ersetzt diese Kontrolle nicht.

## 9. Vollständige Dateiliste dieses Auftrags

Geändert:

- `.gitignore`
- `build.gradle.kts`
- `ROADMAP.md`
- `TESTING.md`
- `src/main/java/de/mangalib/controller/MainController.java`
- `src/main/java/de/mangalib/service/BandService.java`
- `src/main/resources/templates/home.html`
- `src/main/resources/templates/einkaufsliste.html`
- `src/main/resources/templates/statistik/st_overview.html`
- `src/main/resources/templates/statistik/st_status.html`
- `src/main/resources/templates/statistik/st_verlag.html`
- `src/main/resources/templates/statistik/st_typ.html`
- `src/main/resources/templates/statistik/st_format.html`

Neu:

- `MANUAL_TESTING.md`
- `scripts/Start-ManualTest.ps1`
- `scripts/Prepare-ManualSchema.ps1`
- `scripts/Test-ManualScripts.ps1`
- `src/main/java/de/mangalib/service/ScraperGateway.java`
- `src/main/java/de/mangalib/service/LiveScraperGateway.java`
- `src/manualTest/java/de/mangalibmanual/ManualTestLauncher.java`
- `src/manualTest/java/de/mangalibmanual/ManualTestGuard.java`
- `src/manualTest/java/de/mangalibmanual/ManualTestDataSource.java`
- `src/manualTest/java/de/mangalibmanual/SyntheticScraperGateway.java`
- `src/manualTest/java/de/mangalibmanual/ManualBannerAdvice.java`
- `src/manualTest/java/de/mangalibmanual/ManualBrowserPolicy.java`
- `src/manualTest/resources/application-manual-test.properties`
- `src/manualTest/resources/static/manual-test/cover.svg`
- `src/manualTest/setup/fixtures.sql`
- `src/manualCheck/java/de/mangalibmanual/ManualTestSafetyTest.java`
- `src/manualCheck/java/de/mangalibmanual/ManualArtifactTest.java`

Zusätzlich lokal erzeugt, Git-ignoriert: Abnahme-JAR unter `build/libs/`, Gradle-/JUnit-Berichte unter `build/` und Prüfcaches unter `.gradle/`. Temporäre Schema-Unit-Testdateien wurden entfernt. Codex erzeugte keine reale Exportdatei. Im historischen Vorbereitungsauftrag blieben `AGENTS.md`, SECURITY_AUDIT.md, JUnit-Guard, Anwendungskonfiguration, Datenmodell und JavaScript-Dateien unverändert. Die nachfolgenden JavaScript-/Fehlerkorrekturen stehen in Abschnitt 10.

## 10. Ergebnisse der manuellen Browserabnahme vom 11.10.2026

### Herkunft und abgeschlossene Einrichtung

Der **Projekteigentümer** meldet die abgeschlossene Einrichtung unter MySQL 8, den
erfolgreichen Start des separaten `manual-test`-Artefakts ausschließlich mit
`mangalib_manual_test_user` auf `mangalib_manual_test` und Listener `127.0.0.1:18080`.
Das sind manuelle Beobachtungen; Codex hat sie in der Nachbereitung **nicht** durch
DB-/SQL-/Browser-/Serverzugriffe wiederholt. Das Einrichtungskonto ist laut Bericht
gesperrt. Der Server wurde kontrolliert beendet und Port 18080 wieder freigegeben.

| Vom Eigentümer geprüft | Ergebnis |
|---|---|
| Reihen/Bände/Cover, Auf-/Zuklappen, Filter/Sortierung | Funktionieren; bekannte Bannerüberdeckung siehe B |
| Reguläre Reihe bearbeiten, Abbrechen, Lesestatus nach Reload | Funktionieren; Reihe mit nullable Flag siehe A |
| Kauf in bestehende/neue Reihe einschließlich Statusdialog, Einkauf löschen | Funktionieren |
| Statistikübersicht und Navigation | Grundkennzahlen plausibel, Navigation funktioniert; Auswertungen teilweise offen |
| Cookie / zwei Tabs / Timeout | HttpOnly=true, SameSite=Strict, Secure=false bei lokalem HTTP; Tabs funktionieren, Schreiben nach Inaktivität abgewiesen |
| S01-Titel und URL-Proben | HTML als Text; `window.__manualXss` undefiniert; gefährliche/ungültige URLs ergeben `null` |
| GET/HEAD/OPTIONS `/scrape` | 405 / 405 / 200; keine Scraperaktion |
| Unzulässiger Host / manipulierter Origin | Abgewiesen; betreffender Datensatz unverändert |
| Synthetische Indizes 900001/900002 bzw. 900005 | Zulässige Befüllung funktioniert; 900005 abgewehrt, ursprünglicher Stacktrace siehe D |

Abschließend vom Eigentümer bestätigt: **zehn Tabellen**, **17 inhaltlich unveränderte
Stammdaten**, **3 Reihen + 3 Details**, **6 Bände**, **2 Einkaufspositionen + 2 Einkaufsdetails**.
Die bewusst ausgeführten manuellen Aktionen verändern Geschäftsdaten. Es wird kein
unveränderter Gesamt-Datenfingerabdruck behauptet. Eine vollständige neue, archivierte
Schema-Metadatenmessung oder administrative Prüfung aller effektiven Rechte durch
Codex liegt nicht vor.

### Gefundene Fehler und gezielte Korrekturen

| Befund | Ursache / Korrektur | Nachweis und Grenze |
|---|---|---|
| **A: Titelbearbeitung bei `istVergriffen=NULL` ergibt 400/NPE** | `MangaReiheService.updateMangaReiheAttributes` dereferenzierte beide Boolean-Werte. `Objects.equals` erhält die drei Werte NULL/false/true. Der echte Bearbeitungsdialog sendet unberührte unbekannte Flags als NULL; eine bewusste Checkboxänderung setzt true/false. Unveränderte nullable AniList-/Cover-URLs bleiben ebenfalls NULL. | 17 DB-freie Controller-/Service-Mockfälle mit realer Reihen-/Details-/Bandlogik, plus DOM-Payloadfälle. Die bestehende Ableitung `istEbayPreis=true` bei Preisänderung bleibt erhalten. Kein Schema-/Entity-Umbau. |
| **B: manueller Banner vom Header verdeckt** | Alle sieben Seiten verwenden `.header { position:fixed; top:0 }`; ein normales Banner vor dem Header reservierte dafür keinen Platz. Gemeinsame ausschließlich manuelle CSS-/JS-Assets reservieren die tatsächliche Bannerhöhe für Body, Header, absolute Sidebar und Dialoge; ResizeObserver berücksichtigt Umbruch/Zoom. Kein extrem hoher z-index. | Sieben echte Thymeleaf-Template-Ausschnitte prüfen die bedingte Ausgabe; sieben jsdom-Fälle prüfen DOM/CSS-Regeln/Höhenänderung. Kein reales Pixel-/Zoom-/Scrollrendering durch jsdom. Regulär werden Banner und beide Assets nicht gerendert. |
| **C: 400/Text trotzdem als JSON gelesen** | Edit-Speichern prüft Erfolg, Content-Type und JSON-Objekt, bevor der Dialog geschlossen wird. Fehler lassen Eingaben stehen und zeigen eine feste neutrale Meldung; Rohtext/Stacktrace wird nicht ausgegeben. Sicherheitsmeldungen der Fetch-Hilfe werden einmal angezeigt. Kein Retry. | DOM-Fälle für JSON-Erfolg, 400/500 Text, 403, HTML statt JSON, defektes/null JSON und Netzwerkfehler. Ein 400 wird nicht pauschal als Benutzer-Validierungsfehler dargestellt; bei unklarer Antwort kann eine Speicherung erfolgt sein, daher Stand manuell prüfen. |
| **D: erwartete Double-Ablehnung als Stacktrace/500** | Typisierte `ScraperRequestRejectedException`, unveränderte Whitelist; nur der im manuellen Launcher registrierte Advice antwortet mit neutralem 400/JSON. Festes WARN-Ereignis ohne Nutzereingabe oder Throwable. `/scrape` reicht genau diesen Typ an den Advice weiter; Band-Autofill erreicht ihn direkt. Fehlender Index ebenfalls kontrolliert. | Neun DB-freie Standalone-MockMvc-Fälle mit echten LocalRequestFilter-/Session-CSRF-Filtern, maskierten Tokens, Service-Mocks und synthetischem Gateway. GET/HEAD=405, OPTIONS=200; fremder Host/Origin, fehlender/falscher/fremdsitzungsgebundener Token erreichen keinen Scraper. Kein Spring-Boot-/JPA-Kontext oder HTTP-Listener. |

API-Erfolgspayloads und Methoden bleiben erhalten. Beabsichtigte Unterschiede:
NULL-Felder werden bei unveränderter Bearbeitung bewahrt, und **ausschließlich im
manuellen Betrieb** liefern erwartete Double-Ablehnungen jetzt kontrolliertes 400/JSON.
Die 29 bestehenden Vollkontext-MVC-/JSON-Verträge wurden nicht erneut ausgeführt.
Die vier vorhandenen Reihen-/Band-Autofill-Handler prüfen 400/JSON vor der
Datenübernahme; Ablehnungen lassen Eingaben unverändert. Vier zusätzliche DOM-
Fälle sichern diesen direkten Folgepfad der kontrollierten Double-Antwort ab.
Die allgemeine Neutralisierung aller Controller-Fehlertexte/Rohlogs bleibt T02/T06;
dieser Auftrag behebt die Frontend-Fehlerbehandlung und erwartete manuelle Ablehnung.

### Tatsächlich durch Codex ausgeführte Prüfungen der Nachbereitung

Alle Kandidaten und der Gradle-Taskgraph wurden vorab auf DB-Freiheit geprüft:
Guard-/Policytests verwenden synthetische Konfigurationen und Mocks; neue Service-
tests erzeugen ausschließlich Java-Objekte mit Repository-Mocks; MockMvc nutzt
keinen Boot-/EntityManager-Kontext. Artefakttests starten nur `--validate-only` mit
synthetischem Passwort, dessen Rückkehr **vor** SpringApplication/JDBC/HTTP liegt.

| Befehl | Tatsächliches Ergebnis |
|---|---|
| `.\gradlew.bat --no-daemon --offline test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest manualSafetyTest assembleManualTest --dry-run` | Aufgabenplan geprüft, keine Ausführung; nur Compile/Resources/Paketierung und explizit begrenzte Tests |
| `.\gradlew.bat --no-daemon --offline test --tests de.mangalib.testsupport.TestDatabaseGuardTest` | **44 bestanden**, 0 Failures/Errors/Skipped |
| `.\gradlew.bat --no-daemon --offline test --tests de.mangalib.security.LocalSecurityPolicyTest manualSafetyTest assembleManualTest` | **61 Policy + 85 manuelle Java-Tests bestanden**, JAR erfolgreich paketiert |
| `.\gradlew.bat --no-daemon --offline manualSafetyTest assembleManualTest` (finale Wiederholung) | **85 bestanden = 52 bestehende + 33 neue**, 0 Failures/Errors/Skipped; kein zusätzlicher Testsummand |
| `pnpm run test:dom` (finaler Lauf) | **93 bestanden = 65 bestehende + 28 neue**, 0 Fehler/Skipped; keine Installation/Requests |
| `.\scripts\Test-ManualScripts.ps1 -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'` | **25 bestanden**, nur temporäre synthetische SQL-Texte und ValidateOnly, keine SQL-Ausführung |

Damit **190 verschiedene Java-Prüffälle**, 93 DOM-Fälle und 25 PowerShell-Prüfungen
erfolgreich. XML-Berichte liegen unter `build/test-results/test` (letzter gefilterter
Lauf: Policy) und `build/test-results/manualSafetyTest`; ein gefilterter `test`-Lauf
ersetzt dort frühere XML-Dateien, die Guard-Zahl wurde unmittelbar nach dessen Lauf
geprüft. Vorhandene HotSpot-CDS-Warnung bleibt; MockMvc protokolliert erwartete
405-Ablehnungen. Keine neue Abhängigkeit, alle Gradle-Aufrufe offline. Sandbox-
Zugriffssperren auf vorhandene Gradle-/Node-Caches wurden durch freigegebene
Cachezugriffe überwunden; das waren keine fehlgeschlagenen Testfälle.

Das geprüfte neue `build/libs/mangalib-manual-test.jar` hat SHA-256
`3A5C66E86C3A76BA7A5086836A023D4AC9959054E3B9F35877D1E15A1D1AE65E`.
Es wurde nur paketiert/mit ValidateOnly geprüft, nicht als Anwendung gestartet.

**Nicht ausgeführt:** vollständige 179-Test-Suite/`clean test`, geschützte
Persistenz-/Vollkontext-MVC-Tests, reale SQL-/Schema-/Stammdatenprüfung, echter
Kontextstart und erneute Browserabnahme. Der Auftrag verbietet alle DB-Verbindungen,
Listener und Browserstarts. Die DB-Daten wurden deshalb von Codex nicht berührt;
es gibt keine neue Vorher-/Nachher-DB-Messung. Datenbank-/Bean-/Konfigurationsguards,
Testprofile, Loopback-/Origin-/CSRF-Regeln und Scraper-Trennung bleiben erhalten.

### Offene Befunde und zukünftige Fixture-Strategie

- Monatsstatistik 2026 sichtbar, 2025 unerwartet leer: **manueller Befund**, Ursache
  nicht vollständig nachgewiesen. `/statistik/monatsdaten` wertet Einkaufsdaten aus,
  nicht unmittelbar die historischen Banddaten. Der Eigentümer verschob die
  ursprünglichen Einkaufs-Erscheinungsdaten von 2024/2025 auf November 2026, um sie
  im Fenster des Einkaufscontrollers (−12/+6 Monate) sichtbar zu machen. Das ist
  ein möglicher Einfluss auf 2025, kein Beweis für die gesamte Statistikabweichung.
  Jahreswechsel/Centverlust/Diagrammlogik bleiben **M04/T04**, keine Reparatur hier.
- Status-/Verlag-/Typ-/Formatstatistiken sind bekannte unvollständige Ansichten;
  Navigationserfolg belegt keine vollständige fachliche Auswertung. Kein neues Dashboard.
- Für **zukünftige Neueinrichtungen** einen expliziten, dokumentierten `YearMonth`-
  Anker wählen und Einkaufsfixtures z. B. in den Folgemonat setzen. Generierte
  Datumsliterale vor einmaligem Import anzeigen/prüfen und Anker lokal festhalten;
  feste IDs und historische Bibliotheks-/Jahreswechsel-Daten erhalten. Nicht heimlich
  `CURRENT_DATE` einbauen (zeitabhängige Ergebnisse) und keine bestehenden Datensätze
  ändern. `fixtures.sql` bleibt in diesem Auftrag unverändert; vor einer neuen
  Einrichtung seine alten Einkaufsdaten bewusst prüfen. Kein Re-Import/Reset.
- Außerhalb des Update-Pfads bleiben nullable Vergriffen-Filter in `MainController.home`
  potenziell fehlerhaft (Boolean-Unboxing von NULL); die neuen Vergleiche betreffen
  gezielt das beauftragte Speichern. Allgemeine Nullable-/Eingabevalidierung separat.
- Bestehende M06-/T03-Regeln, einschließlich Weitergabe des Reihenlesestatus an
  Bände und Transaktions-/Kaufwiederholungsrisiken, wurden nicht geändert. Die
  Mockfälle sichern Felderhalt bei konsistentem bestehenden Lesestatus, nicht jeden
  fachlich problematischen Altzustand. Kein DB-Persistenznachweis in diesem Auftrag.
- Vollständige Schema-/Rechtemetadaten, Java-21-Patchstand, OSIV und allgemeine
  Logging-/Dependency-Befunde bleiben separat zu prüfen. Kein produktiver Betrieb freigegeben.

### Gezielte manuelle Nachprüfung durch den Eigentümer

1. Aktualisiertes JAR abnehmen, mit `-ValidateOnly` kontrollieren; **kein** SQL/Fixture-
   Re-Import. Erst nach eigener bewusster Startentscheidung das vorhandene manuelle
   Profil starten, Listener/PID ausschließlich `127.0.0.1:18080` bestätigen.
2. Seiten mit Strg+Umschalt+R neu laden, damit keine alten JavaScript-/CSS-Dateien
   aus dem Browsercache benutzt werden. Banner auf Home, Einkauf und allen fünf Statistikseiten bei Scrollen, veränderter
   Fensterbreite und Zoom prüfen. Header/Filter bleiben bedienbar; Hinzufügen-,
   Bearbeiten-, Band- und Kaufstatusdialog dürfen den Banner nicht verdecken.
3. Reihe 900002 nur im Titel ändern, speichern und neu öffnen. Beide Flags,
   AniList-/Coverwerte, Preise, Beziehungen und Lesestatus kontrollieren. Soweit
   der NULL-Testwert noch besteht: unbetätigte Checkbox bleibt unbekannt; bewusstes
   Ein-/Zurückschalten setzt false. Änderungen dokumentieren, nichts zurückseeden.
4. Speichern mit abgelaufener Sitzung: einmaliger Reload-Hinweis, Dialog/Eingaben
   erhalten, kein Replay. In DevTools synthetisch unterbrochene/ungültige Antwort
   prüfen, keine internen Texte im UI; vor erneutem Schreiben gespeicherten Stand
   nach Reload prüfen. Keine realen DB-Fehler absichtlich provozieren.
5. Autofill 900001/900002 positiv; 900005 und fehlender Index kontrolliert 400/JSON,
   genau ein neutrales WARN ohne Stacktrace. GET/HEAD bleiben 405, OPTIONS bleibt
   nebenwirkungsfrei. Formular-/Coverfelder bei Ablehnung unverändert, Ladeanzeige
   endet und bestehender Fehlerindikator erscheint. Keine echten Scraperprozesse, kein Live-Fallback.
6. Gezielte Aktionen und Schema/Stammdaten durch den Eigentümer kontrollieren,
   Anwendung beenden und freien Port bestätigen. Das ist die noch ausstehende
   Browsernachabnahme der Korrekturen, keine von Codex durchgeführte Prüfung.

### Dateiübersicht der Nachbereitung (29 Dateien, bestehende lokale Arbeit erhalten)

Geändert in diesem Folgeauftrag:

- `MANUAL_TESTING.md`, `ROADMAP.md`, `TESTING.md`, `src/test/js/README.md`
- `src/main/java/de/mangalib/service/MangaReiheService.java`
- `src/main/java/de/mangalib/controller/MainController.java`
- `src/main/resources/static/js/popup_edit_reihe.js`, `src/main/resources/static/js/security.js`
- `src/main/resources/static/js/popup_add_reihe.js`, `src/main/resources/static/js/popup_edit_baende.js`, `src/main/resources/static/js/einkaufsliste.js` (nur Autofill-Erfolgsprüfung vor Datenübernahme)
- `src/main/resources/templates/home.html`, `src/main/resources/templates/einkaufsliste.html`
- `src/main/resources/templates/statistik/st_overview.html`, `st_status.html`, `st_verlag.html`, `st_typ.html`, `st_format.html`
- `src/manualTest/java/de/mangalibmanual/ManualBannerAdvice.java`, `SyntheticScraperGateway.java`
- `src/manualCheck/java/de/mangalibmanual/ManualArtifactTest.java`
- `src/test/js/security.test.cjs` (Fetch-Double bekommt reale Content-Type-Metadaten, vier neue Autofill-400-Fälle; keine abgeschwächte Assertion)

Neu in diesem Folgeauftrag:

- `src/main/java/de/mangalib/service/ScraperRequestRejectedException.java`
- `src/manualTest/resources/static/manual-test/banner.css`, `banner.js`
- `src/manualCheck/java/de/mangalibmanual/SeriesEditRegressionTest.java`
- `src/manualCheck/java/de/mangalibmanual/ManualScraperResponseTest.java`
- `src/manualCheck/java/de/mangalibmanual/ManualBannerTemplateTest.java`
- `src/test/js/manual-acceptance.test.cjs`

Die übrigen bereits vorhandenen lokalen Vorbereitungsänderungen aus Abschnitt 9
bleiben erhalten. Kein Commit/Push, keine neuen Dependencies oder Entity-/Schema-
Änderungen. Das manuelle JAR und die Testberichte wurden lokal aktualisiert (Git-ignoriert).

Abschlussprüfung: gesamter versionierter Git-Diff und neue Dateien kontrolliert,
`git diff --check` sowie Whitespace-Prüfung aller unversionierten Dateien bestanden.
Staging-Bereich leer. Der Gesamt-Workspace enthält auch den erhaltenen älteren
Vorbereitungsstand; diese Dateiübersicht zählt ausschließlich die Nachbereitung.

## 11. Nachprüfung T02-Schreibvalidierung (11.10.2026)

Erst nach gesonderter Start-/DB-Freigabe in der isolierten manuellen Umgebung,
mit aktualisiertem Abnahme-JAR und ausschließlich synthetischen Scraper-Doubles.
In diesem Auftrag wurde keine Anwendung oder Browserabnahme gestartet. Die später
ausdrücklich freigegebene automatisierte DB-Validierung verwendet ausschließlich
`mangalib_test`; die manuelle Datenbank wurde von Codex nicht angesprochen.

- Reihen/Bände/Einkaufspositionen mit normalen Werten, Unicode/Sonderzeichen und
  negativen Preisanpassungen anlegen/bearbeiten. Erwartung: bisherige Datenübernahme,
  Preise und Erfolgsantworten; unveränderte unbekannte Flags/optionale Felder erhalten.
- Ungültige/leere Pflichtfelder, falsche IDs, Bandanzahl 0/10.001, Reread −1/10.001
  und ungültige Preise kontrolliert senden. Erwartung: neutraler 400-Fehler, keine
  Speicherung. Grenzwerte 10.000 **nicht manuell massenhaft speichern**; dafür Mocks nutzen.
- Neue URL-Werte mit relativen Pfaden, fremden Schemes oder Userinfo prüfen.
  Erwartung: 400 ohne Speicherung; null/leere optionale Werte und gültige HTTP(S)-
  URLs weiter möglich. Keine historische URL automatisch umschreiben.
- Autofill: 900001/900002 weiter synthetisch erfolgreich; 900005 weiterhin kontrollierte
  Double-Ablehnung. Syntaktisch ungültige Indizes nun neutral 400 schon vor dem Double.
  Vorhandene Formulardaten bleiben bei Fehlern erhalten; keine echte Scraperaktion.
- Speichern/Kauf mit abgelaufener Session weiter 403, kein Replay. Ungültige Kauf-IDs
  kontrolliert 400, fehlende Item-IDs weiter 404, Statusabfrage bei neuer Reihe weiter 417.
  Fehlermeldungen dürfen keine internen Exceptiontexte oder Rohdaten zeigen.

Die Browseranzeige dieser Fehler und die manuelle Abnahme bleiben offen. Die
automatisierten Rollback-/Datenerhaltungsprüfungen und `clean test` sind nach
Eigentümerfreigabe bestanden (371 Tests; zehn Tabellen und Schema-Metadaten erhalten).
M06/T03/T06 bleiben separate Aufträge; diese Checkliste ist keine Freigabe
zum Starten oder Ändern einer Datenbank. Technische Regeln und ausgeführte Prüfungen:
[TESTING.md](TESTING.md#t02--schreibvalidierung-analyse-grenzen-und-datenbankfreie-prüfung).


## 12. T02-Nachbesserung: Dialogfehler und vier UI-Befunde (11.10.2026)

Dieser Abschnitt ergänzt die historischen Berichte. Die manuelle Anwendung war vom
Eigentümer beendet; Codex hat keine DB-Verbindung, SQL-Ausführung, Anwendung,
HTTP-Listener oder Browser gestartet. Keine echten Scraper, Migrationen, neuen
Dependencies, Commits oder Pushes. Die vorherigen T02-Backendänderungen bleiben
unverändert erhalten; M06/T03/T06 wurden nicht vorgezogen.

### Ursachen und gezielte Korrekturen

- **Fehlerdarstellung:** Add-/Einkaufs-/Kaufhandler interpretierten Fehlerantworten
  ungeprüft als JSON; Edit hatte bereits eine Formatprüfung, aber eine pauschale
  native Alert-Meldung. Die gemeinsame Fetch-Hilfe zeigte ebenfalls native Alerts.
  Ihre Ablehnung kann von fehlendem Token, unzulässigem Ziel oder HTTP 403 stammen:
  **kein Beweis für Sessionablauf**, auch nicht bei der früheren Browserbeobachtung.
- **Einheitliche Rückmeldung:** `MangaLibFeedback` in `security.js` liest bei 4xx/5xx
  keine Fehlerbodys, unterscheidet 400/403/404/5xx und prüft bei JSON-Erfolg Content-Type
  und Objektform. Feste Texte via `textContent`, fokussierbare `role=alert`-/Live-Region
  im jeweiligen Dialog; Kauf-/Löschfehler erscheinen sichtbar im Hauptbereich.
  Eingaben bleiben bei Fehlern erhalten. CSRF-, Origin-, Redirect- und Cookie-Regeln
  bleiben unverändert; nur die clientseitige Präsentation der Ablehnung wurde ersetzt.
- **Unbestätigte Speicherung:** Button während Request sperren; bei 403, 5xx,
  Netzwerk-/Abbruch-/Formatfehlern bis zum Neuladen gesperrt halten. Auch Abbrechen
  und Wiederöffnen hebt die Sperre nicht auf. Vor erneutem Schreiben gespeicherten
  Stand nach Neuladen kontrollieren. Kontrollierte 400/404 ermöglichen bewusste
  Korrektur. Kein automatischer Retry und keine neue serverseitige Idempotenz (T03).
- **Erfolgsverträge:** JSON-200, Band-204 ohne Body, Delete-200 ohne Body und
  Kauf-417 mit Statusauswahl bleiben erhalten. Der Kaufstatusdialog bleibt bei
  Create-Fehlern offen und behält seine Auswahl; nach 400 ist eine bewusste Korrektur
  möglich. Cancel vor Create startet keinen weiteren Request. Der gemeinsame
  Statusdialog wird jeweils nur einem laufenden Kauf zugeordnet.
- **A – Close-Button:** Überflüssige Suche nach `editBandCloseButton` entfernt.
  Der bestehende Cancel-/`closeEditBandPopup()`-Mechanismus bleibt erhalten.
- **B – Observer:** Nicht vorhandenes Ziel `bands-container` und überflüssigen
  MutationObserver entfernt. Lazy Loading bleibt bei Seiteninitialisierung und
  unmittelbar nach `toggleBands()` aktiv; der Fallback startet auch ohne Scrollen.
- **C – Cover:** Template legt normalen/manuellen lokalen Ersatzcoverpfad getrennt
  fest. Dynamische Cover/Preview in der manuellen Umgebung verwenden bei externen
  URLs den lokalen `/manual-test/cover.svg`, passend zur unveränderten CSP; reguläre
  gültige Cover bleiben nutzbar. Ladefehler fallen einmalig auf lokale Cover zurück.
  Keine gespeicherte Bild-URL, Band-Konstruktor oder Entity-Zuordnung verändert.
- **D – ID:** Nur sichtbare Beschriftung in „Band-ID“ geändert; `manga_reihe_id`,
  Payload und Zuordnung bleiben unverändert.
- Lade-/Fehlerindikatoren sind auf ihren tatsächlichen Dialog begrenzt, statt bei
  Band-/Reihenbearbeitung versehentlich den ersten Add-Dialog zu treffen.

### Prüfnachweise und Grenzen

- **158 DOM-Tests bestanden = 93 bestehende + 65 neue**, 0 Fehler/Skipped,
  mit `node --test src/test/js/*.test.cjs`, vorhandenes jsdom, gesperrtes Netzwerk.
  Matrix für vier echte Schreibhandler: 400 JSON/Text, 403, 404, unerwartetes 417,
  500 HTML, falsches Format, defektes/null/leeres JSON, NetworkError und AbortError.
  Zusätzlich Werterhalt, Fokus/Inline-Text, keine Rohfehler/Alerts/automatischen Replays,
  Doppelclick-Sperre, Kauf-417/Cancel/Create-Fehler, JSON-/204-/Delete-Erfolg,
  Dialoge, manuelles Ersatzcover und Lazy Loading. Bestehende Payload-/CSRF-/XSS-
  Assertions erhalten; Präsentationsassertions gezielt von Alert auf Inline geändert.
- **297 DB-freie Java-Tests bestanden**: 44 Guard + 61 Policy + 192 bestehende
  Validator-/Service-/Standalone-MockMvc-Fälle. **85 manuelle Schutz-/Template-/
  Artefaktfälle bestanden**; echte Thymeleaf-Auswahl des Ersatzcovers ergänzt.
  Vor jedem Gradle-Lauf Taskplan geprüft, ausschließlich offline.
- Erstlauf eines neuen Tests scheiterte an jsdoms nicht unterstütztem Reload;
  erwartete Reloads werden jetzt explizit erfasst, andere DOM-Fehler bleiben Fehler.
  Ein weiterer neuer Cancel-Test hatte fälschlich schon einen Status vorausgewählt;
  Fixture korrigiert. Abschließende Läufe erfolgreich, keine Assertions abgeschwächt.
- Historische **371 vollständige Spring-/Integrationstests** wurden in diesem Auftrag
  nicht wiederholt. Keine neue DB-/Schema-/Stammdatenmessung; Unverändertheit folgt
  hier aus vollständigem Zugriffsverzicht, nicht aus einer gemessenen DB-Prüfung.
  Bestehende MVC-/JSON-Backendverträge und ihre Java-Assertions unverändert.
- jsdom prüft kein reales Layout, Bildrendering oder echten Sessionablauf. AbortError
  ist synthetisch simuliert; keine neue allgemeine Timeout-/Scraperarchitektur.
  Frontend-Sperren gelten pro Seite und ersetzen keine Atomarität/Mehrtab-Idempotenz.
  Allgemeine Read-/Linkfehler und Debug-Rohlogs bleiben separate T02/T06-Arbeit.

### Spätere Browser-Nachprüfung – erst nach gesonderter Startfreigabe

1. Aktualisiertes manuelles JAR/ValidateOnly prüfen, bestehenden DB-Bestand nicht
   neu importieren. Nach bewusstem Start Loopback/PID/Banner bestätigen und hart neu laden.
2. Add/Reihen-/Band-/Einkaufsdialog öffnen, ohne Speichern schließen und wieder öffnen;
   Werte, Band-ID-Beschriftung, lokale Cover, Aufklappen und Scrollen kontrollieren.
   Keine alten Close-/Observer-/externen Ersatzcoverfehler erwarten.
3. Ausschließlich freigegebene negative Schreibfälle erneut abnehmen: leerer Titel
   ohne Alert/Request; ungültige Bandanzahl/Reread mit sichtbarem neutralen 400-Hinweis,
   erhaltenen Werten und beobachtetem HTTP-Status. Vorhandenen Bestand kontrollieren.
4. 403/Sitzung, unterbrochene/unklare Antwort gezielt beobachten: keine pauschale
   Sessiondiagnose, kein Replay, gesperrter Schreibbutton. Nach Reload zuerst Stand lesen.
   Keine echten DB-Fehler provozieren; sensible Inhalte dürfen nicht erscheinen.
5. Positive Speicherung/Band-204, Kauf-417/Status/Cancel und synthetischen Autofill
   ausschließlich nach zusätzlicher ausdrücklicher Schreibfreigabe prüfen. Tastatur,
   Fokus, Meldungskontrast, Dialogscrollen und Smartphone-Layout sind kein jsdom-Nachweis.

## 13. T02-Nachbesserung: Reread, Preisdarstellung und Titel-Layout (14.10.2026)

### Bestätigte Ursachen und Korrekturen

- **Reread bei Reihenanlage:** Formular und Requestvalidierung übergaben den Wert
  korrekt; der Anlagezweig des Controllers ließ ihn beim Serviceaufruf weg. Die
  neu erzeugten Details hatten damit keinen Reread, der Lesevertrag zeigte 0.
  Jetzt wird der validierte Wert übergeben und vor dem ersten Details-Speichern
  gesetzt. Zulässig bleiben 0 bis 10000; fehlend/null im Anlage-Request bleibt 0.
  Alte Serviceaufrufe ohne Reread behalten ihre null-Semantik. Die bereits
  funktionierende Bearbeitung und die fachlichen Band-/Lesestatusregeln bleiben
  unverändert. Bestehende Datensätze werden nicht nachträglich korrigiert.
- **Einkaufspreise:** Der Templateausdruck verlangte zwei Vorkommastellen und
  vertauschte deutsche Dezimal-/Tausendertrennzeichen. Jetzt werden mindestens
  eine Vorkommastelle, deutsche Trennzeichen und zwei Nachkommastellen dargestellt:
  `8,49 €`, `13,98 €`, `0,00 €`, gegebenenfalls `−0,02 €`.
  Das Minuszeichen wird ausschließlich zur Anzeige vereinheitlicht. Keine Änderung
  gespeicherter Preise, numerischer Aggregation oder bestehender Rundungsverträge.
- **Lange Titel:** Implizite Grid-/Flex-Mindestbreiten drängten Text und Buttons
  über den verfügbaren Platz. Das ergänzende Stylesheet erlaubt schrumpfbare
  Textspalten und Zeilenumbruch, reserviert Aktionsspalten und ordnet auf schmalen
  Seiten Sidebar/Navigation sowie Dialog-Speicherbuttons ohne Überlagerung an.
  Dialoge sind in der Höhe begrenzt und scrollbar; Titel werden nicht verändert.

### Tatsächliche datenbankfreie Prüfung

308 Java-Fälle (einschließlich 44 Guard-/61 Policy-Fällen), 32 ausgewählte
Template-/Mock-Fälle und 164 DOM-Fälle bestanden, jeweils ohne Fehler/Skipped.
Neu: fünf Controller-, sechs Service-, acht Preis- und sechs CSS-/DOM-Fälle.
Der echte Details-Speicheraufruf wird mit Repository-Mocks auf Reread geprüft;
die Preisfälle rendern die tatsächlichen Templateausdrücke. Keine vollständige
Integrationstestsuite, DB-Verbindung, Anwendung, Browser oder Scraper ausgeführt.
Das laufende manuelle JAR wurde ausdrücklich nicht neu gebaut (`-x manualBootJar`).
Historische Browser-/Integrationsergebnisse sind damit nicht erneut bestätigt.
DB-Unverändertheit wurde nicht neu gemessen; dieser Auftrag hatte keinerlei DB-Zugriff.

### Noch erforderliche Browsernachprüfung – gesonderte Freigabe notwendig

1. Erst nach freigegebener Paketierung und bewusstem Neustart das neue manuelle
   JAR per ValidateOnly, Loopback/PID und Banner bestätigen; Browser hart neu laden.
   Kein Reset/Fixture-Reimport. Die Datensätze von
   `T02_BROWSER_ACCEPTANCE_20261014_R01` und der ursprüngliche Bestand bleiben erhalten.
2. Nur neue, ausdrücklich freigegebene synthetische Reihen mit Reread 0 und 1
   anlegen, neu laden und den gespeicherten Wert kontrollieren. 10000 bezeichnet
   hier Reread, keinesfalls eine Massenerzeugung von Bänden. Fehlend/null und
   ungültige Grenzen sind bereits Mock-getestet; reale Schreibprüfung separat freigeben.
   Die bestehende Reihenbearbeitung erneut auf unverändertes Verhalten prüfen.
3. Einzel-/Positions-/Monatspreise auf konsistente deutsche Darstellung prüfen;
   keine Preise vorhandener Einträge ändern. Negative Anzeige nur in fachlich
   zulässigen Kontexten prüfen, keine neue negative Einkaufspreisregel einführen.
4. Lange Unicode-Titel und ungetrennte Titel bei etwa 320/480/768/1100 Pixeln
   kontrollieren: lesbarer Umbruch, keine horizontalen Überläufe oder überlagerten
   Buttons; Maus und Tastatur in Reihen-/Band-/Einkaufsdialogen, Speichern/Schließen,
   Dialogscrollen und Abstand zum manuellen Banner prüfen. Erst nach Schreibfreigabe
   tatsächlich speichern. jsdom liefert hierfür keinen visuellen Nachweis.
5. Nach erlaubten Schreibprüfungen neu laden und ursprüngliche Reihen/Bände sowie
   R01-Datensätze vergleichen. Keine automatische Bereinigung oder Reparatur.

Keine allgemeine UI-Neugestaltung oder Umsetzung von M06/T03/T06 in diesem Auftrag.

## 14. Bestätigte Browsernachprüfung und Integrationsabschluss (15.10.2026)

Der Projekteigentümer hat die drei zuletzt korrigierten Befunde im Browser erfolgreich
nachgeprüft: **Reread 3 bleibt nach Reihenanlage und Neuladen erhalten**, Einkaufspreise
erscheinen im deutschen Format, lange Titel umbrechen im schmalen Fenster und die
Schaltflächen bleiben sichtbar/bedienbar. Dies aktualisiert den offenen Nachtest aus
Abschnitt 13; keine erneute Browserausführung durch Codex und keine Behauptung einer
vollständigen visuellen Abnahme aller Bildschirmbreiten/Sonderfälle.

Nach ausdrücklicher Freigabe ausschließlich für `mangalib_test` hat Codex erneut
44 datenbankfreie Guard-Fälle, einen lesenden Ausgangstest, die vollständige Suite
mit **382 erfolgreichen Tests (0 Fehler/Skipped)**, einen lesenden Abschlusstest und
**164 DOM-Fälle (0 Fehler/Skipped)** ausgeführt. Alle 29 MVC-/JSON-Verträge bestanden.
Vorher-/Nachher-Fingerabdruck einschließlich Schema identisch:
`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`.
Zehn Tabellen und 78 Stammdaten unverändert, Geschäftstabellen vor/nach leer,
transaktionale Fixtures zurückgerollt; AUTO_INCREMENT bewusst ausgenommen.

**Historischer betrieblicher Restpunkt (inzwischen abgeschlossen, siehe Abschnitt 15):** Der angeforderte `clean test`-Aufruf scheiterte vor
Testbeginn an der Windows-Dateisperre des laufenden manuellen JARs. Die vollständige
Suite wurde danach mit `test` erfolgreich ausgeführt, ohne Tests auszuwählen oder
abzuschwächen. Kein Stop/Neustart und kein Löschungsbehelf. Ein erfolgreicher
`clean test` erforderte damals ein ausdrücklich freigegebenes Wartungsfenster ohne
laufende Instanz aus `build/libs`; im damaligen Auftrag keine Bereinigung oder Wiederholung.

Kein Zugriff auf `mangalib_manual_test` oder produktive Daten, kein Browser-/Scraper-
aufruf und keine Änderung der synthetischen R01-Daten in diesem Auftrag. Die manuelle
Anwendung bleibt unberührt. Historische Testberichte bleiben erhalten; genaue
Befehle, Berichtsablage und verbleibende T02-Grenzen stehen in TESTING.md.

## 15. Abschließender Nachtrag: kontrollierter Stop und erfolgreicher Clean-Test (15.10.2026)

Die eindeutig zugeordnete manuelle Testinstanz **PID 106732 wurde kontrolliert per
Ctrl+C in ihrer ursprünglichen Startsession beendet**. Port **18080 ist frei**;
kein erzwungener Prozessabbruch und kein anschließender Neustart. Die Hinweise auf
eine weiterlaufende Instanz und einen ausstehenden Clean-Test in den früheren
Abschnitten beschreiben ausschließlich den damaligen Stand.

Nach erneuter Schutzprüfung und 44 erfolgreichen datenbankfreien Guard-Tests:
**`clean test` vollständig erfolgreich – 382 bestanden, 0 Fehler, 0 übersprungen**,
ohne Testfilter. Alle **29 MVC-/JSON-Vertragstests** bestanden. Lesende Fingerabdrücke
vor und nach der Suite identisch:
`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`.
Zehn Tabellen und 78 Stammdatensätze samt Schema-Metadaten unverändert, keine
verbliebenen Fixtures; AUTO_INCREMENT bewusst ausgenommen. Ausschließlich
`mangalib_test` verwendet, keine Testzugriffe auf die manuelle oder produktive Datenbank.
Die **164 DOM-Tests** waren bereits erfolgreich; keine erneute Browser-/DOM-Prüfung
für diesen Abschluss behauptet. Die bestätigte Eigentümer-Browsernachprüfung aus
Abschnitt 14 bleibt erhalten.

Abschließendes Code-Review einschließlich unversionierter Dateien ohne neue
blockierende Befunde; bisheriger T02-Implementierungsumfang technisch commitfähig.
**Kein pauschaler T02-Abschluss:** Altbestandsverträglichkeit sowie
SSRF-/Scraper-/Requestgrößengrenzen bleiben offen; T03, M06 und T06 eigenständige
Folgearbeiten. Keine Änderung der synthetischen Testdaten, keine Migration oder
Bereinigung und keine Commits, Pushes oder Merges. Genaue Prüfnachweise und lokale
Berichtsablage im abschließenden Nachtrag von TESTING.md.

## 16. M06 – Vorbereitung erfolgreich, Browserabnahme technisch blockiert (15.10.2026)

Freigegeben war eine dauerhafte Schreibabnahme ausschließlich an neu anzulegenden
synthetischen M06-Datensätzen in der vorhandenen manuellen Datenbank. Tatsächlich
wurden **keine Browser-Schreibaktionen durchgeführt und keine Testdatensätze angelegt**.

### Erfolgreiche Vorbereitung

- Branch `codex/m06-reading-status`, vorhandene Änderungen erhalten.
- `assembleManualTest --dry-run`, danach Offline-Build erfolgreich. M06-Klassen im
  Artefakt entsprechen bytegenau dem aktuellen Kompilat. Keine Live-Scraper-/Selenium-
  oder Standardstartbestandteile; ausschließlich vorhandene synthetische Doubles.
- Vorhandenes Startskript mit `-ValidateOnly` und Java 21 erfolgreich. Passwort nur
  aus `MANGALIB_MANUAL_DB_PASSWORD`, keine unsicheren Overrides und keine Secretdateien.
- Vor Start Port frei, keine vorhandene manuelle Instanz. Eigene PID **1632** mit
  erwartetem manuellem JAR gestartet; Profil `manual-test`, ausschließlich
  `127.0.0.1:18080` und `mangalib_manual_test` / `mangalib_manual_test_user` bestätigt.
  Sichtbares Banner konnte wegen der folgenden UI-Blockade nicht geprüft werden.

### Blockade und tatsächlich nicht ausgeführte Fälle

Der DOM-Browser-Connector stand nicht zur Verfügung. Das vorhandene Windows-
Computer-Use-Werkzeug konnte Firefox-Fenster auflisten, meldete beim Zugriff auf
das ausgewählte Fenster jedoch **`Computer Use app approval timed out`**. Keine
Navigation und keine Eingabe-/Speicheraktion ausgeführt. Die technische UI-Freigabe
wurde nicht umgangen; keine direkten Schreibrequests oder SQL-Schreibbefehle benutzt.

| Geplantes Browserszenario | Tatsächlicher Stand |
|---|---|
| Vollständig gelesene Reihe um ungelesenen Band ergänzen | Nicht durchgeführt |
| Teilweise gelesene Reihe um zwei Bände ergänzen | Nicht durchgeführt |
| Unterschiedliche Rereads vor/nach Erweiterung und Reload | Nicht durchgeführt; SQL-NULL nicht künstlich herstellen |
| Kaufzuordnung über Manga-Index | Nicht durchgeführt |
| Kaufzuordnung über Titel-Fallback | Nicht durchgeführt |
| Erweiterung mit gleichzeitiger Reihenmetadatenänderung | Nicht durchgeführt |
| Nachträgliche Einzelbandmarkierung und Aggregation | Nicht durchgeführt |

Null fachliche Browserfälle ausgeführt; daraus keine Aussage über Erfolg/Fehlschlag
der M06-Funktionen ableiten. Die 456 automatisierten Tests einschließlich 34 JPA-
Integrationsfällen bleiben separat dokumentiert und ersetzen die Browserabnahme nicht.

### Lesender Bestandsvergleich und Abschluss

Vor Start und nach der blockierten Bedienung ausschließlich lesende Snapshots mit
festem manuellem MySQL-Konto und einer READ-ONLY-Transaktion erfasst. Keine produktive
oder automatisierte Datenbank, kein Schemaexport/-import und kein Seeding. Verglichen
wurden sämtliche Tabellenzeilen einschließlich Status/Rereads/Beziehungen und alle
relevanten Schema-Metadaten; nicht nur Zeilenzahlen.

- Reihen-IDs **1–6**, Band-IDs **1–10**, Einkaufspositions-IDs **1, 2, 4** vollständig
  unverändert, einschließlich der vorhandenen T02-/Eigentümer-Abnahmedaten.
- 17 Stammdaten unverändert: ein Verlag, vier Status, vier Typen, drei Formate,
  fünf Sammelbände. Sechs Reihen-/drei Einkaufsdetails unverändert.
- Zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf Fremdschlüssel und 26
  Key-Column-Einträge unverändert; AUTO_INCREMENT ausgenommen.
- SHA-256 der manuellen JSON-Snapshots vor/nach identisch:
  `ce4a6db574a05f4d14052e3a189e6a616da9d627102bf28e7c6ede32ab96ebf1`.
  Alle 15 Snapshotabschnitte identisch, **null neue Reihen, Bände oder Einkaufspositionen**.
- Private lokale Nachweise in `.gradle/m06-browser-20261015/` (Git-ignoriert),
  keine Passwörter enthalten. Das manuelle JSON-Hashformat ist vom JUnit-Format getrennt.
- Eigene PID 1632 über Ctrl+C der Startsession beendet, Prozessende bestätigt,
  Port **18080 anschließend frei**. Kein erzwungener Abbruch oder fremder Prozessstop.

**M06 bleibt manuell nicht abgenommen.** Vor einer Fortsetzung muss die technische
Browser-/Computer-Use-Appfreigabe verfügbar sein. Danach Sicherheitsprüfung und
aktuellen Ausgangssnapshot erneut durchführen und ausschließlich neue eindeutig
gekennzeichnete M06-Testdaten verwenden. Keine Wiederherstellung/Bereinigung nötig.
Keine M06-Codekorrektur in diesem Auftrag; T03/S03 und andere Folgearbeiten unverändert.

## 17. M06 – Browserabnahme mit integriertem Browser erfolgreich (15.10.2026)

**M06 technisch und manuell abgenommen; noch nicht nach `main` gemergt.** Die
Werkzeugblockade aus Abschnitt 16 bleibt historisch erhalten. Die spätere native
Firefox-Prüfung wurde zusätzlich wegen fehlender URL-Policy-Unterstützung beendet.
Für diesen Abnahmelauf wurde ausschließlich der vorhandene Codex In-app Browser
verwendet; keine native Firefox-Steuerung und keine Sicherheitsausnahme.

### Startschutz und Nachweisgrenzen

Branch `codex/m06-reading-status` bestätigt, vorhandene Änderungen erhalten. Das
vorhandene manuelle JAR war aktuell: die drei betroffenen M06-Klassen bytegleich
zum Kompilat, deshalb kein erneuter Build. Passwortvariable vorhanden, keine
unsicheren Framework-/JVM-Overrides; keine Secretwerte ausgegeben oder gespeichert.
`Start-ManualTest.ps1 -ValidateOnly` mit Java 21 erfolgreich. Anschließend über
dasselbe Skript eigene PID **90716** gestartet: ausschließlich Profil `manual-test`,
manuelle Datenbank/Konto und einziger Listener `127.0.0.1:18080` bestätigt.

`http://127.0.0.1:18080/home` erreichbar, Testbanner sichtbar, sechs bestehende
Reihen dargestellt. Ausschließlich reguläre UI-Aktionen mit unverändertem Session-/
CSRF-Schutz; keine direkten HTTP-Schreibrequests oder SQL-Schreibbefehle.
Jeder Speicher-/Kaufvorgang einmalig ausgelöst und anschließend neu geladen.
Keine Autofill-/Scraperaktionen oder externen Navigationen. Geprüfte Bild-/Script-/
Stylesheet-Attribute waren lokal, bestehende manuelle CSP sperrt externe Bilder
und fremde Fetch-Ziele. Keine Warnungen/Fehler in der abgefragten Browserkonsole.
Ein vollständiger Netzwerk-Mitschnitt war über diese Browserschnittstelle nicht
verfügbar; die Beobachtung ersetzt keinen paketweisen Netzwerk-Nachweis.

### Tatsächlich ausgeführte Szenarien

Gemeinsamer Präfix `M06_TEST_20261015_IAB_`; G = gelesen, U = ungelesen.
B, C und F verwenden bewusst dieselbe neue Reihe zur Begrenzung der Testdaten.

| Fall | Neue Testdaten / UI-Aktion | Nach vollständigem Neuladen bestätigt | Ergebnis |
|---|---|---|---|
| A | Reihe 7 (`A`), zwei gelesen angelegte Bände; im Reihendialog auf drei erweitern | Alte IDs 11/12 bleiben G; neuer Band 13 U; Reihencheckbox aus | Erfolgreich durchgeführt |
| B | Reihe 8 (`BCF`), drei Bände individuell auf G/U/G setzen; auf fünf erweitern | IDs 14/15/16 unverändert G/U/G; 17/18 U; Reihe nicht vollständig gelesen | Erfolgreich durchgeführt |
| C | An derselben Reihe individuelle Rereads 0/2/5 über Banddialoge setzen; erweitern | Alte Rereads 0/2/5 und unmittelbar vor Erweiterung gespeicherter Reihen-Reread 0 erhalten; neue Banddialoge zeigen 0 | Erfolgreich durchgeführt |
| D | Reihe 9 (`D_INDEX`), Index 96001501, Band 19 G/Reread 2 und 20 U; neue Position 5 mit demselben Index kaufen | Reihe 9 auf drei Bände erweitert, 19 G/Reread 2 und 20 U erhalten; 21 U; Position gekauft; keine doppelte Reihe/Bände | Erfolgreich durchgeführt |
| E | Reihe 10 (`E_TITLE`), Index 96001502, Band 22 G/Reread 3 und 23 U; Position 6 mit gleichem Titel, aber Index 96001503 kaufen | Titel-Fallback erweitert Reihe 10; 22 G/Reread 3 und 23 U erhalten; 24 U; Position gekauft; keine neue Dublettenreihe | Erfolgreich durchgeführt |
| F | Gleichzeitig mit B/C Titel in `M06_TEST_20261015_IAB_BCF_ÄÖÜ geändert` ändern | Neuer Titel und fünf Bände gespeichert; alte Bandzeilen 14–16 gegenüber Zwischensnapshot exakt unverändert | Erfolgreich durchgeführt |
| G | Nach A ausschließlich neuen Band 13 im Banddialog gelesen markieren | 11/12 weiterhin G, 13 nun G; Reihencheckbox wird wahr | Erfolgreich durchgeführt |

**7 erfolgreich, 0 fachlich fehlgeschlagen, 0 technisch blockiert, 0 nicht durchgeführt.**
Bei bestehender Kaufzuordnung führt bereits der Einkaufswagen-Klick die Übernahme
aus; ein zusätzlicher Statusdialog wird regulär nur für eine neue Reihe benötigt.
Kein Kauf wurde wiederholt. G wurde direkt nach A geprüft; der Endzustand von A
ist deshalb G/G/G und nicht mehr der zuvor nachgewiesene Zwischenzustand G/G/U.

Reread-Grenze: Die vorbereitenden Einzelbandänderungen aggregierten den zunächst
bei Anlage eingegebenen Reihen-Reread 2 regulär auf 0. Maßgeblich für C ist der
unmittelbar vor der Erweiterung erneut im Reihendialog beobachtete Wert 0.
Die Erweiterung selbst verändert ihn nicht. Neue Band-Rereads können gespeichert
SQL-NULL sein und werden im Dialog als 0 angezeigt; keine SQL-NULL-Zustände erzeugt
oder normalisiert. Die getrennten JPA-Tests decken diese Unterscheidung ab.

### Bestands- und Schemaschutz

Ausschließlich lesende Snapshots mit dem bereits geprüften Hilfsskript und festem
manuellem Konto in READ-ONLY-Transaktionen erstellt. Sämtliche vorher vorhandenen
Datensätze anhand ID und vollständiger Zeilenwerte verglichen:

- Reihen 1–6, Details 1–6, Bände 1–10 und Einkaufspositionen/Details 1/2/4 unverändert.
- Alle 17 Stammdatensätze unverändert.
- Zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf Fremdschlüssel und 26
  Key-Column-Einträge unverändert; AUTO_INCREMENT weiterhin ausgenommen.
- Ausschließlich neu: vier Reihen/Details (7–10), 14 Bände (11–24), zwei
  Einkaufspositionen/Details (5/6). Beide neuen Positionen als gekauft gespeichert.
- Endbestand: zehn Reihen, 24 Bände und fünf Einkaufspositionen. Alle neuen Daten
  bleiben bewusst erhalten, kein Cleanup/Reset/Seeding.

Manuelle JSON-Snapshot-Hashes (wegen ausdrücklich freigegebener neuer Daten
erwartungsgemäß verschieden, kein JUnit-Hashformat):

```text
vorher: ce4a6db574a05f4d14052e3a189e6a616da9d627102bf28e7c6ede32ab96ebf1
nachher: edffee9430083f993a55a91865a48dc1804a61837e32f61597de4f1d9923adc7
```

Lokale ignorierte Nachweise: `.gradle/m06-iab-20261015/`, mit `before.json`,
`bcf-before-extension.json`, `d-after-purchase.json`, `after.json`, `comparison.txt`
und `bcf-final.png`. Vorherige Nachweise in Abschnitt 16 nicht überschrieben.

Eigene PID 90716 über Ctrl+C der ursprünglichen Startsession beendet. Prozessende
und freier Port 18080 bestätigt; kein erzwungener Stop oder Neustart. Keine
Verbindungen zur produktiven/automatisierten Datenbank, keine Migrationen oder
Schemaänderungen, keine neuen Abhängigkeiten und keine Codekorrekturen in diesem
Auftrag. Die 456 erfolgreichen Java-Tests wurden nicht erneut ausgeführt.
T03 (unter anderem Kaufatomarität/Identifikation/Reduktion) und S03
(Lesedatum/Reread-Historie) bleiben eigenständige Folgearbeiten.

## 18. T02 – Nachprüfung der Altbestandskorrekturen (16.10.2026)

**Historischer Vorbereitungsstand vor der unten dokumentierten Browserabnahme.** Geschützte Integration am 16.10.2026
erfolgreich: 40 neue Legacy-Fälle, 107 bestehende geschützte Regressionen und
ungefilterter `clean test` mit 547 bestandenen Fällen, 0 Fehlern/Skipped; Daten- und
Schemafingerabdruck unverändert. Details und Nachweisgrenzen im neuen Nachtrag von
TESTING.md. Keine manuelle DB-Verbindung oder Browserausführung in diesem Auftrag.
Erst nach neuer Freigabe aktuelles manuelles Artefakt bauen/validieren/starten.
Ausschließlich `manual-test`,
`mangalib_manual_test`, Loopback und synthetische Doubles; vorhandene T02-/M06-
Datensätze unverändert lassen. Neue eindeutig markierte synthetische Datensätze
verwenden, Bestands-/Schema-Snapshots erhalten; keine SQL-Fixtures improvisieren.

1. Teilweise gelesene neue Reihe nur im Titel/URL/Metadatum ändern: individuelle
   Bandstatus und Rereads nach Reload unverändert, beide Massenoptionen zunächst aus.
2. Lesestatus-Massenoption ausdrücklich aktivieren: vorhandene Bände übernehmen den
   Wert auch dann, wenn der Reihenwert schon gleich ist. Erneutes Öffnen setzt die
   Option zurück. Reihenwertänderung ohne Option verändert keine Bandwerte.
3. Reread-Massenoption prüfen: bisheriger Mindestwertvertrag, höhere Bandwerte
   bleiben erhalten. M06-Erweiterung bleibt unabhängig davon erhalten: alte Bände
   unverändert, neue Bände ungelesen; neue Bände später einzeln bearbeitbar.
4. Unbekannte Rereads/Spezialflags und Preise nur bei bereits passend vorhandenen,
   freigegebenen synthetischen Datensätzen prüfen: unbekannt statt 0/false/NaN,
   unberührte Werte erhalten; bewusstes 0/false separat prüfen. Kein SQL-NULL-Seeding
   allein für Browsertests; nicht verfügbare Zustände als nicht prüfbar kennzeichnen.
5. Historische unsichere Bild-URL zeigt lokalen Platzhalter. Andere Feldänderung
   erhält den URL-Altwert; neue ungültige URL bleibt abgewiesen. Band-URL leeren
   bedeutet weiterhin behalten. Keine externen Links öffnen.
6. Kontrollierte Lade-/Beziehungsfehler zeigen feste Meldung und verhindern das
   Speichern alter Dialogwerte. Keine automatische Reparatur oder Wiederholung.
7. Neue Checkboxen/Labels bei schmalem Fenster, Tastatur und Dialogscrollen prüfen.

Die vorstehende Checkliste beschrieb den Plan, keinen damals bereits erbrachten
Browsernachweis. Die inzwischen durchgeführten Fälle und Grenzen folgen hier;
automatisierte Nachweise stehen weiterhin getrennt im Nachtrag von TESTING.md.

### Tatsächliche isolierte Browserabnahme (16.10.2026)

Nach ausdrücklicher Eigentümerfreigabe auf `codex/t02-security-hardening` mit dem
Codex In-app Browser durchgeführt. Kein Firefox-/Selenium-/Live-Scraperstart.
Passwort ausschließlich aus der vorhandenen Prozessvariable
`MANGALIB_MANUAL_DB_PASSWORD`, ohne Ausgabe oder Speicherung. Kein externer
Framework-/JVM-Override, vor Start kein MangaLib-Prozess und Port 18080 frei.

Taskplan `assembleManualTest --dry-run` geprüft, danach
`.\gradlew.bat --offline --no-daemon assembleManualTest` erfolgreich. Bestehendes
`Start-ManualTest.ps1 -ValidateOnly -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'`
erfolgreich ohne DB/Listener. Derselbe Startweg anschließend ohne ValidateOnly:
eigene PID **21952**, Java 21, ausschließlich `manual-test`, feste manuelle
DataSource/Benutzeridentität und synthetisches Gateway. Artefaktprüfung schließt
Standardstartkonfiguration, Live-Scraper, Selenium und DevTools aus. Listener
ausschließlich **127.0.0.1:18080**, sichtbares manuelles Testbanner bestätigt.
Die vorhandenen Guards, Sicherheitsfilter, Konfigurationen und Anwendungscode
wurden nicht geändert.

Ausgangsbestand: zehn Reihen/Details, 24 Bände, fünf Einkaufspositionen/Details,
17 Stammdatensätze. Alle Schreibaktionen ausschließlich über normale UI-Dialoge
an einer neuen eigenen Reihe mit Präfix `T02_LEGACY_BROWSER_20261016_`.
Keine direkten HTTP-Schreibrequests, SQL-Fixtures, Bereinigung oder Wiederholung
einer unklaren Schreibaktion. Nach jedem gespeicherten Szenario vollständiger
Reload und erneutes Öffnen/Lesen der betroffenen Anzeige beziehungsweise Dialoge.

| Szenario | Tatsächliche Browserbeobachtung nach Reload | Bewertung |
|---|---|---|
| **A – unabhängige Metadaten** | Neue Reihe mit drei Bänden: teilweise gelesen, individuelle Rereads 1/5/0, Preise 8,49/6,99/0,00 und Anpassung −0,02/0/0. Nur Titel auf Unicode-/Sonderzeichenvariante geändert. Beide Massenoptionen zunächst aus; alle Bandspalten im ergänzenden Vorher-/Nachher-Snapshot identisch. | **Bestanden** |
| **B – expliziter Lesestatus** | Reihenwert bereits false, Massenoption ausdrücklich aktiviert: alle drei Bände anschließend ungelesen. Erneutes Öffnen setzt beide Optionen aus. Danach nur Reihenwert true ohne Option gespeichert: alle Bandwerte bleiben false. | **Bestanden** |
| **C – Reread-Mindestwert** | Explizite Massenaktion mit Wert 3 ergibt 3/5/3; höherer Wert 5 erhalten. Danach erneute reine Titeländerung ohne Optionen: Dialogwerte einschließlich Preise/Lesestatus nach Reload identisch. | **Bestanden** |
| **D – M06-Erweiterung** | Eigene Reihe wieder teilweise gelesen vorbereitet, von drei auf vier erweitert. Beide Massenoptionen aktiviert, gelesen=true und Reread=9 mitgesendet. Alte Bände einschließlich sämtlicher Spalten unverändert; neuer Band ungelesen, Reihenstatus false und Reihen-Reread weiterhin 3. Optionen nach erneutem Öffnen wieder aus. | **Bestanden für M06/T02**, bekannter Preisbefund siehe unten |
| **E – unbekannt gegenüber bewusstem Wert** | Neuer Band hatte von selbst Reread NULL (leeres Feld) und Spezialflag NULL (unbestimmte Checkbox). Unabhängige lokale Bildänderung erhielt beide nach Reload; anschließend bewusst 1/false gespeichert. An anderem eigenen Band bewusst Reread 0 und Preis 0,00 gespeichert und wieder angezeigt. Neuer Erweiterungsband zeigt wieder unbekannten Reread/Spezialflag. | **Teilweise bestanden**; unbekannter Preis nicht verfügbar |
| **F – URLs und Fehleranzeige** | Neue file-URL clientseitig mit „Bitte geben Sie eine gültige Bild-URL ein.“ abgewiesen. Neue HTTP(S)-Userinfo-URL über regulären Speicherhandler mit „Einige Angaben sind ungültig. Bitte überprüfe deine Eingaben.“ abgewiesen. Dialog/Eingaben bleiben erhalten, Save bedienbar, kein Alert oder interner Exceptiontext; nach Cancel/Reload Original-URL erhalten. Bild-src bleibt lokaler Platzhalter. | **Teilweise bestanden**; historisch ungültiger URL-Altwert nicht verfügbar |
| **G – schmale Ansicht/Tastatur** | Bei 640 × 800 Pixeln beide neuen Labels und Checkboxen eindeutig zugeordnet und ohne Überlagerung bedienbar. Tab von Gelesen fokussiert Lesestatus-Massenoption, nächster Tab die Reread-Option; Leertaste schaltet jeweils gezielt. Dialog scrollt zum fokussierten Feld; nach erneutem Öffnen beide aus. Temporäre Viewport-Vorgabe zurückgesetzt. | **Bestanden** |

Ergebnis auf Gruppenebene: **5 vollständig bestandene, 2 teilweise prüfbare Gruppen,
0 neue T02-Fehler**. Zwei Teilfälle ausdrücklich **nicht prüfbar**: unbekannter
Bandpreis und Erhalt einer bereits historisch unsicheren URL. Im vorhandenen
synthetischen Bestand fehlen diese Zustände; keine künstliche Erzeugung per SQL
oder neuen ungültigen URL-Schreibweg. Fehlende Details/Beziehungen und gesperrte
Ladefehler wurden ebenfalls nicht künstlich in der Browserumgebung hergestellt;
hierfür gelten die getrennten JPA-/Mock-/DOM-Nachweise. Keine pauschale Abnahme
aller möglichen Altbestandszustände oder von T02 insgesamt.

Keine beobachteten Warnungen/Fehler in der Browserkonsole, keine nativen Alerts,
unhandled Promise Rejections oder sichtbaren Schreibwiederholungen. Der Connector
liefert hier keinen vollständigen HTTP-/Netzwerk-Trace: **keine HTTP-Statuscodes
aus der Fehlermeldung abgeleitet**, keine exakte Requestzählung behauptet. Die
automatisierten MockMvc-Verträge bleiben der separate Statuscode-Nachweis.
Kein Autofill, externer Link oder echter Scraper wurde ausgelöst. Externe Default-
Cover wurden in der manuellen Oberfläche auf `/manual-test/cover.svg` abgebildet;
die manuelle Bild-CSP bleibt zusätzlich aktiv. Ein vollständiger Nachweis sämtlicher
Unterressourcen-/SSRF-Pfade ist diese Browserabnahme nicht.

### Erneut beobachteter bestehender T04-Preisfehler

Beim normalen Erhöhen von drei auf vier Bände erhielt der neue Band **28 den Preis
0,00**, obwohl vorher ein Reihenpreis von 5,15 angezeigt wurde. Gesamtsumme blieb
15,46; angezeigter Reihenpreis danach 3,87. Alte Bandpreise und Anpassungen blieben
unverändert. Reproduktion: Reihendialog einer eigenen Reihe öffnen, Bandanzahl
erhöhen, ohne Preisimport speichern, neu laden und neuen Band öffnen.

Der Edit-Payload enthält keinen `preisProBand`; `WriteRequests.Series.from`
verwendet dafür `absentZeroMoney` (WriteRequests.java:42,157). Der Anhängepfad
übernimmt diesen 0-Standardpreis (BandService.java:430). Auch das Startsession-Log
meldet für den neuen Band „Setze Standardpreis: 0“. Dies entspricht dem bereits
geplanten T04-Punkt „Neu angelegte Bände dürfen bei fehlendem Preis nicht
stillschweigend 0 € erhalten“, keiner neuen Lesestatus-/Altbestandsregression.
**Nicht korrigiert**, keine Preise oder Testdaten nachträglich bereinigt. Fachliche
Preissemantik/Verteilung bleibt T03/T04 und benötigt einen gesonderten Auftrag.

### Neue Daten und Bestandsvergleich

Ausschließlich neu, bewusst dauerhaft erhalten:

- Reihe und Details **11**, Endtitel
  `T02_LEGACY_BROWSER_20261016_R01_ÄÖÜ 漫画 & Metadaten unverändert erhalten`.
- Vier Bände **25–28**, zugehörige Nummern 1–4; keine neue Einkaufsposition.

| Band-ID | Gelesen | Reread | Preis | Anpassung |
|---|---|---:|---:|---:|
| 25 | ja | 3 | 8,49 | −0,02 |
| 26 | nein | 5 | 6,99 | 0,00 |
| 27 | nein | 3 | 0,00 | 0,00 |
| 28 | nein | unbekannt/NULL | 0,00 (T04) | 0,00 |

Rein lesende Snapshots mit einer unveränderten Kopie des etablierten Skripts:
fester MySQL-Client, keine Optionsdateien, abwesende Login-Datei, ausschließlich
manuelles Konto/Ziel, Passwort nur im Kindprozess-Environment, Identitätsprüfung,
READ-ONLY-Transaktion und abschließendes ROLLBACK. Keine SQL-Schreibbefehle.

Alle ursprünglichen Zeilen anhand ID und vollständiger Spaltenwerte unverändert:
zehn Reihen/Details, 24 Bände, fünf Einkaufspositionen/Details sowie sämtliche
17 Stammdaten. Zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf Fremdschlüssel
und 26 Key-Column-Einträge identisch; AUTO_INCREMENT weiterhin ausgenommen.
Zwischensnapshots bestätigen sämtliche Bandspalten bei A und sämtliche alten
Bandspalten bei D unverändert. Nur sechs neue Geschäftszeilen (1 Reihe, 1 Details,
4 Bände); Endbestand elf Reihen/Details, 28 Bände, fünf Einkaufspositionen/Details.

Manuelle JSON-Snapshot-Hashes, durch freigegebene Neuanlage erwartungsgemäß verschieden:

```text
vorher: edffee9430083f993a55a91865a48dc1804a61837e32f61597de4f1d9923adc7
nachher: 2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226
```

Keine Verwechslung mit dem JUnit-Fingerabdruck. Lokale ignorierte Nachweise in
`.gradle/t02-legacy-browser-20261016/`: `before.json`, `after.json`, `comparison.txt`,
`state-a/`, `state-d/`, `options-640.png`, `invalid-url-640.png`, `final-series.png`
und `shutdown.txt`. Frühere Nachweise unverändert erhalten.

### Kontrollierter Abschluss

Eigene PID **21952** vor Beendigung erneut als manuelles JAR mit einzigem
Loopback-Listener identifiziert, anschließend **Ctrl+C in ursprünglicher
PTY-Startsession 8051**. Tomcat meldet „Graceful shutdown complete“, danach JPA-
EntityManagerFactory geschlossen. PID nicht mehr vorhanden, **Port 18080 frei**;
kein Force-Stop, kein fremder Prozess beendet, kein Neustart. Der unterbrochene
Vordergrundprozess liefert dabei Exitcode 1; geordneter Java-Shutdown und tatsächliches
Prozess-/Listenerende wurden unabhängig bestätigt.

Nur MANUAL_TESTING.md und TESTING.md in diesem Browserauftrag bearbeitet. Keine
Verbindung zu `mangalib` oder `mangalib_test`, keine Migrationen, Schemaänderungen,
Initialisierung, Bereinigung, neuen Dependencies oder Anwendungscodekorrekturen.
Keine Java-/DOM-Suite erneut ausgeführt; 547 Java- und 198 DOM-Erfolge bleiben
getrennte technische Nachweise. Kein Git-Staging/Commit/Push/Merge. Weitere Arbeit
erst nach Eigentümerfreigabe; T02-Scraper-/SSRF-/Requestgrößengrenzen bleiben offen.

## 19. T02 – Echte HTTP-Abnahme der Requestgrenzen: kontrollierter Abbruch (16.10.2026)

**Teilnachweis, keine abgeschlossene HTTP-/Browserabnahme.** Die freigegebene
HTTP-Prüfung wurde bei nicht eindeutig bestätigtem Verbindungsende sofort gestoppt.
Keine eigenständige Anwendungscodekorrektur oder automatische Wiederholung.
Die 678 Java-/198 DOM-Erfolge bleiben getrennte historische Nachweise.

### Vorbereitung und sichere Ausführung

Branch `codex/t02-security-hardening`, keine relevanten Framework-/JVM-Overrides;
manuelle Passwortvariable vorhanden/nicht leer, ohne Wertausgabe. Zunächst kein
manueller Launcher/Listener auf 18080; das durch `clean` entfernte JAR fehlte.
Aufgabenplan `.\gradlew.bat --offline --no-daemon assembleManualTest --dry-run`
geprüft: nur Haupt-/Manual-Kompilierung, Ressourcen und manuelle Paketierung.
Danach `assembleManualTest` erfolgreich. Anschließend vorhandenes Skript:

```powershell
.\scripts\Start-ManualTest.ps1 -ValidateOnly -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
```

ValidateOnly erfolgreich ohne DB/Listener. Unveränderte manuelle Guards erzwingen
`manual-test`, `mangalib_manual_test` / `mangalib_manual_test_user`, feste JDBC-URL
auf 127.0.0.1:3306, Passwort aus `MANGALIB_MANUAL_DB_PASSWORD`, nur Schema-Validierung,
keine SQL-Initialisierung/Migrationen und keine alternativen DataSources.
Artefaktprüfung schließt Standardstart, Live-Scraper, Selenium, WebDriverManager
und DevTools aus; ausschließlich synthetisches Scraper-Gateway vorhanden.

Vor Start vollständiger lesender Snapshot mit bytegleicher Kopie des etablierten
Hilfsskripts: READ-ONLY-Transaktion, feste manuelle DB-/Benutzeridentität, Passwort
nur im Kindprozess-Environment. Danach Start über dasselbe Skript ohne ValidateOnly
in PTY-Session **45472**: Java 21, eigene PID **38212**, ausschließlich Profil
`manual-test`, Tomcat **11.0.24**, einziger zugehöriger Listener **127.0.0.1:18080**.
Prozessidentität und Listener wurden unabhängig lesend bestätigt.

### Fünf tatsächlich ausgeführte HTTP/1.1-Fälle

Eng begrenzter Python-Standardbibliothek-Client, ausschließlich TCP-Ziel
127.0.0.1:18080, Host `localhost:18080`, Connect-Timeout 3 s / I/O-Timeout 5 s,
sequenziell, begrenzte Antwortgröße. Alle drei POST-Probes ausschließlich auf
dem statisch geprüften, nicht vorhandenen `/__t02_request_limit_probe__`, ohne
gültigen CSRF-Token. Keine Geschäftsroute, kein HTTP-Ersatz positiver UI-Aktionen.

| Fall | Erwartung | Tatsächlich beobachtet | Bewertung / Schicht |
|---|---|---|---|
| GET `/home` | 200 und manueller Marker im HTML | 200, vollständige 71.305-Byte-Antwort, Marker vorhanden | Bestanden für HTTP-Auslieferung; keine visuelle Browserprüfung |
| GET `/einkaufsliste` auf derselben Verbindung nach vollständig gelesener erster Antwort | 200 und Wiederverwendung | 200, vollständige 15.197-Byte-Antwort, Marker vorhanden | Bestanden; echte sequenzielle Keep-alive-Wiederverwendung, kein Pipelining |
| Syntaktisch gültiges JSON `{}`, Content-Length 2, ohne CSRF | 403 ohne Fachaktion | 403, vollständige JSON-Antwort | Bestanden; bestehende Security-Kette, kein positiver Fachvertrag |
| POST ohne Content-Length/Transfer-Encoding und ohne Body | 403 ohne Fachaktion | 403, vollständige JSON-Antwort | Bestanden; nur leerer Request, kein Test zusätzlicher ungerahmter Datenbytes |
| Content-Length **33.554.433**, angekündigter Body nicht gesendet | Früh 413; Verbindungsende beobachten | Vollständige 413, aber kein EOF/Reset innerhalb 1 s nach Antwortende | Größenablehnung bestanden; Verbindungstrennung unklar, **Abbruch** |

Home/Einkaufsliste: `Content-Type: text/html;charset=UTF-8`, `Transfer-Encoding:
chunked`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` und manuelle
CSP vorhanden. Sessioncookies mit `Path=/`, `HttpOnly`, `SameSite=Strict`
beobachtet; keine Cookie-/Tokenwerte protokolliert. Die 403-Antworten hatten
`Content-Type: application/json`, chunked und DENY/nosniff. Dies ersetzt keine
browserseitige Cookie-/Session-/CSRF-Abnahme.

Die 413 stammt anhand des exakt passenden festen Texts aus dem neuen
RequestBodyLimitFilter, **nicht aus Tomcats Headerparser**:

```text
HTTP/1.1 413
Connection: close
Content-Type: text/plain;charset=UTF-8
Content-Length: 60

Anfrage überschreitet die technischen Größenbegrenzungen.
```

Alle 60 UTF-8-Bytes vollständig gelesen. Danach ergab Peek auf derselben Verbindung
im festgelegten **1-Sekunden-Fenster** weder EOF noch Reset; die Wartefrist lief ab.
Der Client schloss seine Verbindung und stoppte ohne Wiederholung. Keine Folgeanfrage
angehängt. **Nicht nachgewiesen:** dauerhaft offene Verbindung, Request-Smuggling,
Umgehung der Größenbegrenzung oder Datenmutation. Die konkrete Ursache bleibt offen;
Warten auf angekündigte Bodybytes ist eine mögliche, nicht bewiesene Erklärung.
`maxSwallowSize=0` und `Connection: close` allein bestätigen die tatsächliche
prompte Trennung bei nicht übertragenem angekündigtem Body ausdrücklich nicht.

Nach dem Abbruch **nicht ausgeführt**: tatsächlich empfangene 32-MiB-Grenzbytes,
chunked Request-Bodies/Chunk-Grenzen/Übergröße, doppelte/widersprüchliche
Content-Length, CL+TE, echte 400/415 und HTTP-Wiedererreichbarkeit nach Ablehnung.
Chunked **Antworten** beweisen keine chunked **Requests**. Kein instrumentierter
Nachweis von Controller-/Service-Aufrufzählern; keine Fachroute aufgerufen,
Nichtmutation separat durch vollständige Snapshots bestätigt. Vorbereitete weitere
Clientfälle wurden nicht ausgeführt.

### Browserblocker und offener nächster Schritt

In dieser Sitzung wird **kein Codex-In-app-Browser-Connector** als Werkzeug
angeboten. Keine native Firefox-Ausweichlösung, Browserinstallation oder positive
HTTP-Schreibaktion als Ersatz. Marker nur im gelieferten HTML nachgewiesen.
Nicht ausgeführt: synthetische Reihenanlage, browserseitiges Session-/CSRF-/
Host-/Origin-Verhalten, sichtbare 413-Meldung, Formularderhalt, Save-Sperre,
ausbleibender Replay und neue bewusste Aktion nach Reload. Kein Datensatz mit
Präfix `T02_REQUEST_LIMIT_BROWSER_20261016_` angelegt. Die statisch vorhandene
generische 413-Fehlerhilfe ist **kein beobachteter UX-Befund**.

Vor Fortsetzung gesonderte Freigabe für die eng begrenzte Untersuchung des
Verbindungsendes einholen; keine automatische Korrektur. Danach übrige HTTP-Fälle
und bei tatsächlich verfügbarem Connector die regulären UI-Fälle prüfen.

### Datenerhaltung und Shutdown

Vollständiger manueller JSON-Snapshot vorher/nachher identisch:

`2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226`

Alle IDs und vollständigen Zeilenwerte unverändert: elf Reihen/Details, 28 Bände,
fünf Einkaufspositionen/Details und 17 Stammdatensätze. **Keine neuen Geschäftszeilen.**
Identisch auch zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf Fremdschlüssel,
26 Key-Column-Einträge; AUTO_INCREMENT ausgenommen. Keine SQL-Schreibbefehle,
Bereinigung, Migration, Initialisierung oder Seeding.

Eigene PID/Listener vor Shutdown erneut bestätigt; **Ctrl+C in ursprünglicher
Session 45472**, „Graceful shutdown complete“ und geschlossene EntityManagerFactory
beobachtet. PID 38212 nicht mehr vorhanden, **Port 18080 frei**, kein Force-Stop.
Exitcode 1 der unterbrochenen Vordergrundsession ist vom beobachteten erfolgreichen
Java-Shutdown zu unterscheiden. Keine fremden Prozesse beendet, kein Neustart.

Ignorierte lokale Nachweise unter `.gradle/t02-request-limit-http-20261016/`:
`ReadManualSnapshot.ps1`, `before.json`, `after.json`, `comparison.txt`,
`http_probe.py`, `http-results.json`, `shutdown.txt`. HTTP-Nachweise enthalten nur
neutrale Header, Status, Größen/Hashes und feste Filtertexte; keine Session-/
CSRF-/Passwortwerte oder vollständigen HTML-Seiten.

Nur MANUAL_TESTING.md und TESTING.md in diesem Auftrag bearbeitet. Keine Verbindung
zu `mangalib` oder `mangalib_test`, keine Anwendungscode-/Guard-/Profil-/Limitänderung,
Live-Scraper, externen Abrufe, Installationen, Browserstarts oder Java-/DOM-Suiten.
Keine Staging-Aktionen, Commits, Pushes, Merges oder weitere T02-/T03-/T04-/T06-Arbeit.

## 20. T02 – Statische Diagnose des verzögerten TCP-Verbindungsendes (16.10.2026)

**Der Befund aus Abschnitt 19 ist mit der lokalen Tomcat-Implementierung erklärbar;
ein Produktionscodefehler in MangaLib ist nicht nachgewiesen.** Die dortige
1-Sekunden-Beobachtung bleibt unverändert historisch erhalten. Sie beweist weder
eine dauerhaft offene Verbindung noch eine Umgehung des Bodylimits. Für den
künstlich unvollständigen Request war ein verpflichtendes EOF innerhalb einer
Sekunde kein sachgerechtes Erfolgskriterium.

### Quellenbasis und getrennte Nachweise

Ausschließlich lokal vorhandene Quellen/Bibliotheken von **Tomcat 11.0.24** und
Python 3.12 untersucht, keine Downloads oder neue Dependencies. Die Bibliothek
`BOOT-INF/lib/tomcat-embed-core-11.0.24.jar` im zuletzt gebauten manuellen Artefakt
ist bytegleich zur untersuchten Gradle-Cache-Bibliothek, SHA-256:

`e7b966dcaac8c5ffa4f10e44031ebf5b71f2123560c08ac8b7120028615aea08`

`javap -c` bestätigt im tatsächlichen Bytecode von `IdentityInputFilter.end()`
den `InputBuffer.doRead`-Aufruf **vor** dem Vergleich der geschluckten Bytes mit
der Grenze. Quellen und Bytecodeauszug liegen ignoriert unter
`.gradle/t02-connection-diagnosis-20261016/`; keine Frameworkquellen ins Repository
oder öffentliche Dokumente kopiert. Folgende Zeilen beziehen sich auf die dort
unverändert aus dem Sources-JAR entnommenen Dateien:

| Komponente / Fundstelle | Statisch belegte Bedeutung |
|---|---|
| MangaLib `RequestBodyLimitFilter.java:46–48,64–65,137–145` | Deklarierte Übergröße wird vor `getInputStream()` abgewiesen. Nur Status, Content-Type, Connection-Header und fester Text werden geschrieben; danach Rückkehr ohne Downstream-Filter. Keine Replay-Request-/Response-Wrapper auf diesem Pfad, kein eigener Socketzugriff. |
| `CoyoteAdapter.java:375–376`, `OutputBuffer.java:209–244` | Tomcat beendet/committet die Servletantwort nach Rückkehr. Der kleine Writerinhalt wird in Bytes umgewandelt und Content-Length berechnet. Eine vollständig ausgelieferte Antwort kann der späteren Input-Abschlussverarbeitung vorausgehen. |
| `Request.java:811–815,2491–2495`; `StandardContext.java:179` | Der 413-Sonderpfad `finishRequest()` deaktiviert Swallowing nur bei `swallowAbortedUploads=false`. Tomcats Context-Default ist true; kein entsprechender Override im MangaLib-Code gefunden. Dieser Sonderpfad erzwingt hier also nicht den sofortigen Abbruch. |
| `Http11Processor.java:945–997,1003–1015` | `Connection: close` setzt `keepAlive=false`; `checkMaxSwallowSize()` deaktiviert Keep-alive ebenfalls bei zu viel Restbody. Das ist noch keine unmittelbare Socket-Schließung. |
| `Http11Processor.java:406–415,446,1200–1238` | Nach Adapter/Response folgt `endRequest()`. Ein gesetzter HTTP-Status ist nicht automatisch Tomcats interner ErrorState. Der nachträgliche Status-Drop-Check greift nur bei noch aktivem Keep-alive; nach dem Response-Commit ist das hier bereits false. Swallowing bleibt deshalb möglich. |
| `Http11InputBuffer.java:626–631` | Bei aktivem `swallowInput` wird der aktive Inputfilter mit `end()` abgeschlossen. |
| `IdentityInputFilter.java:149–174` | Für verbleibenden Content-Length-Body erst `buffer.doRead(this)`, dann Prüfung `swallowed > maxSwallowSize`. Der Quellkommentar begründet den absichtlich nicht vorgezogenen Abbruch mit der Möglichkeit für den Client, die Antwort zu empfangen. |
| `Http11InputBuffer.java:831–840,753–778`; `NioEndpoint.java:1520–1564` | Ohne ReadListener ist der Lesezugriff blockierend. Fehlen Bodybytes, kann der NIO-Worker auf Daten, EOF oder den Read-Timeout warten. EOF/Timeout führen über IOException im Requestabschluss zur Schließung. |

### Was `maxSwallowSize=0` tatsächlich bedeutet

MangaLib setzt die Grenze auf **0 Byte**. Das ist kein unbegrenztes Swallowing
(negative Werte wären die unbegrenzte Variante), aber auch **kein Verbot jedes
weiteren Leseversuchs** und keine zeitliche Socket-Frist. Bei Restbody ist die
Grenze schon logisch überschritten; die Ausnahme wird dennoch erst ausgelöst,
wenn ein Lesezugriff positive Bytes geliefert hat. Ein erster Netzwerk-/Pufferblock
kann daher noch gelesen werden. Treffen überhaupt keine Bodybytes ein, kann schon
dieser erste Zugriff warten. Die Anzahl 0 bedeutet somit nicht „sofortiges EOF“.

`Connection: close` kennzeichnet die Verbindung als nicht wiederzuverwenden.
Der Connector schließt sie nach seiner Abschluss-/Fehlerverarbeitung. Ein
Anwendungs-`flushBuffer()` wäre allein kein belegter Fix: früherer Commit beendet
nicht die nachfolgende Input-Verarbeitung. Ein erzwungenes Socket-Schließen oder
Tomcat-interne Action-Aufrufe wären zusätzliche Architekturentscheidungen und
werden nicht implementiert.

Tomcats lokale Quellen haben standardmäßig `disableUploadTimeout=true` und einen
regulären Connector-Default von 60.000 ms; der NIO-Lesepfad verwendet den effektiven
Socket-Read-Timeout. **Kein gemessener Timeout der gestarteten Boot-Instanz**:
Boot-Customizer können Defaults beeinflussen. Weder eine tatsächlich gemessene
60-Sekunden-Laufzeit noch eine vollständig zeitunbegrenzte Bindung wird behauptet.
Bei clientseitigem FIN durch `shutdown(SHUT_WR)` wäre aus dem Code eine EOF-/
Fehlerbehandlung plausibel; diese Variante wurde nicht ausgeführt.

### Prüfung des bisherigen Clients

Der Client liest die Antwort anhand der HTTP-Längengrenze. Beim konkreten Fall
stimmen empfangene **60 Byte** mit `Content-Length: 60` überein. Python
`HTTPResponse.isclosed()` allein bedeutet lediglich, dass dessen Reader geschlossen
ist; es beweist kein TCP-EOF. Die zusätzliche bisherige Prüfung
`sock.recv(1, MSG_PEEK)` ist dagegen für die getrennte Beobachtung geeignet:
leere Bytes = EOF, ConnectionResetError = Reset, Timeout = keine Bytes/kein EOF
im gewählten Fenster. Schließen des Response-Readers schließt den weiterhin
referenzierten ursprünglichen Socket nicht automatisch.

Es war daher kein offensichtlicher Fehler der EOF-Messung, sondern eine zu strenge
Erwartung an deren **1-Sekunden-Fenster**. Der Client schloss danach selbst;
der tatsächliche spätere Server-Schließzeitpunkt wurde damals nicht gemessen.
Keine Aussage über das Verhalten nach fünf Sekunden oder nach SHUT_WR ableiten.

### Bewertung, Empfehlung und Grenzen

- **HTTP-Ablehnung:** vollständige frühe 413 mit festem Text historisch gemessen.
- **Nichtwiederverwendung:** Connection-close-Header historisch gemessen und
  Keep-alive-Deaktivierung statisch belegt.
- **TCP-Schließung:** im alten 1-Sekunden-Fenster nicht beobachtet; das ist mit
  dem belegten blockierenden Restbody-Abschluss vereinbar, kein isolierter Bugbeweis.
- **Ressourcen:** ein solcher unvollständiger Request kann trotz bereits gesendeter
  413 einen Socket und einen wartenden Connector-Worker bis zu Daten/EOF/Timeout
  binden. Keine CPU-Dauerlast, tatsächliche Bindungsdauer oder Mehrverbindungswirkung
  gemessen. Read-Timeouts und Parallelitäts-/Ressourcenbudgets bleiben ein eigener
  T02-Härtungspunkt; keine Last-/Slowloris-Tests in diesem Auftrag.

**Empfehlung: keine Produktionscodeänderung allein aufgrund des bisherigen Befunds.**
Die statische Erklärung reicht aus; die nur bedingt freigegebenen Varianten A/B/C
wurden nicht benötigt und **nicht ausgeführt**. Keine Anwendung gestartet, keine
DB-Verbindung/Snapshots, Builds, Tests, Browser oder Scraper ausgeführt. Der frühere
geordnete Shutdown und Datenvergleich aus Abschnitt 19 bleiben historische Nachweise,
keine neuen Messungen. Für einen separaten Ressourcenhärtungsauftrag zuerst den
effektiven Connector-Timeout und gewünschte Budgetgrenzen klären, dann eng begrenzte
Verbindungstests mit neuem Freigaberahmen planen; keine pauschale Timeout-Verkürzung.

Falls künftig ausdrücklich ein schnellerer Abbruch verlangt wird, ist Tomcats
vorhandene Context-Option `swallowAbortedUploads=false` ein gezielt zu bewertender
Ansatz für den 413-Pfad, keine jetzt empfohlene oder implementierte Änderung.
Zu prüfen wären vollständiger Antwortempfang, EOF/Reset bei ausstehendem Body,
bereits gepufferte Bytes, SHUT_WR sowie unveränderte 400/413/415-/Normalverträge.
Die Option ersetzt keine allgemeine Zeit-/Parallelitätsbegrenzung und wirkt nicht
pauschal auf jeden Statuscode; keine Tomcat-internen Socket-Hacks vorwegnehmen.

Weiter offen bleiben die tatsächlichen 32-MiB-Grenzpayloads, chunked Request-Bodies,
Chunk-Grenzen, CL-Duplikate/Konflikte und CL+TE, reale 400/415, Wiedererreichbarkeit
nach Ablehnung sowie die vollständige Browserabnahme einschließlich 413-UX.
Ein zukünftig vollständig gesendeter/gerahmter Request und ein absichtlich
unvollständiger Request benötigen unterschiedliche Schließerwartungen.

Nur MANUAL_TESTING.md und TESTING.md ergänzt; vorhandene Änderungen erhalten.
Kein Produktionscode, keine Konfiguration, Limits, Guards, Entities, Geschäftsregeln,
Dependencies oder Roadmap-Prioritäten geändert. Keine Datenbank-/Git-Schreibaktionen.

## 21. T02 – Verbleibende echte HTTP-/Tomcat-Protokollabnahme (16.10.2026)

Neuer ausdrücklich freigegebener Lauf nach der Diagnose aus Abschnitt 20.
**19 HTTP-Fälle abgeschlossen:** 17 bestanden, ein Teilnachweis wegen früher
Headerablehnung und eine erklärte Abweichung bei CL+TE; keine Blockade oder
unerklärliche Antwort. Keine Produktionscode-/Konfigurationsänderung. Historische
678 Java-/198 DOM-Erfolge sowie Abschnitte 19/20 unverändert erhalten.

### Vorbereitung

Branch `codex/t02-security-hardening`, manuelle Passwortvariable vorhanden/nicht
leer, keine relevanten Framework-/JVM-Overrides. Vorher kein manueller Prozess
und Port 18080 frei. Vorhandenes JAR exklusiv lesbar, Quellen älter als Artefakt;
Filter-/Konfigurationsklassen im JAR bytegleich zu vorhandenen Kompilaten.
Kein Neubau erforderlich, daher kein Gradle-/Testlauf. Das vorhandene
`Start-ManualTest.ps1 -ValidateOnly -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'`
war erfolgreich ohne DB/Listener. Guards und feste manuelle Konfiguration unverändert:
nur `manual-test`, `mangalib_manual_test` / `mangalib_manual_test_user`, Passwort
aus `MANGALIB_MANUAL_DB_PASSWORD`, Schema nur validate, keine Initialisierung/
Migrationen oder alternativen DataSources; Live-Scraper/Selenium aus Artefakt entfernt.

Vor Start vollständiger lesender Snapshot über unveränderte Kopie des etablierten
Skripts. Anschließend Start über vorhandenes Skript in eigener PTY-Session
**17252**, Java 21, PID **32320**, Tomcat **11.0.24**. Prozessidentität und einziger
zugehöriger Listener **127.0.0.1:18080** unabhängig bestätigt.

### Client, Grenzen und Zuordnung

Python-Standardbibliothek, festes TCP-Ziel 127.0.0.1:18080, Host localhost:18080,
Connect-Timeout 3 s / I/O-Timeout 5 s, sequenziell, keine automatischen Wiederholungen.
Neue Verbindung je Fall außer ausdrücklich geprüftem Keep-alive-Paar. Erzeugung
großer Bodies in höchstens 64-KiB-Blöcken, begrenzte Responseaufnahme; keine
vollständigen 32-MiB-Clientstrings oder Parallel-/Last-/Slowloris-Serie.

Alle POSTs ausschließlich auf dem zuvor gegen sämtliche Controller-Mappings
geprüften **nicht existierenden** `/__t02_request_limit_probe__`, ohne gültigen
CSRF-Token. Nur GET Home/Einkaufsliste zum Marker-/Listener-/Keep-alive-Nachweis.
Keine Geschäfts-/Scraper-Schreibroute, kein positiver HTTP-Schreibersatz für UI.
Keine instrumentierten Aufrufzähler: der Ausschluss von Geschäftsschreibpfaden
folgt aus Routing/Security und wird durch vollständige Snapshots ergänzt.
Die gewollt lesenden Viewcontroller und mögliche Sicherheits-Fehlerdispatches
sind davon zu unterscheiden.

Große gültige JSON-Bodies bestehen aus `{}` plus nachfolgendem JSON-Whitespace:
nur zwei Struktur-Tokens, keine langen Strings oder tiefen Objekte. So ist die
Bytegrenze unabhängig von den JSON-Strukturgrenzen prüfbar. Das nominelle
32-MiB-Limit ist daher erreichbar, auch wenn heutige normale Fachpayloads meist
kleiner sind. Der zusätzliche 4097-Zeichen-String prüft ausdrücklich eine andere
Grenze und liefert ebenfalls 413, nicht den Nachweis des Bytebudgets.

P = über die Socket-Sendeoperationen übertragene Payloadbytes; W = Bodybytes
einschließlich Chunk-Framing, ohne Requestzeile/Header. Kein Paketmitschnitt:
Sendezähler allein beweisen keine vollständige serverseitige Verarbeitung.
Bei den akzeptierten großen Bodies zeigt die nachgelagerte Security-403 zusammen
mit dem Filterpfad, dass das vollständige begrenzte Vorlesen/JSON-Prüfen passiert ist.
403 ist hier **kein positiver fachlicher Speichervertrag**.

| Fall | Framing; tatsächlich gesendet | Status / verantwortliche Schicht | Verbindung / Bewertung |
|---|---|---|---|
| Vorab GET Home | kein Body, Connection: close | 200; View, manueller Marker | vollständiges HTML, EOF; bestanden |
| A: kleines JSON `{}` | CL 2; P/W 2 | 403; Security nach Filter | vollständig; Client schließt; bestanden |
| A: knapp unter Grenze | CL 33.554.431; P/W 33.554.431 | 403; Security nach Filter | vollständig; Client schließt; bestanden |
| A: exakt Grenze | CL 33.554.432; P/W 33.554.432 | 403; Security nach Filter | vollständig; Client schließt; bestanden |
| A: deklarierte Übergröße | CL 33.554.433; **P/W 0** | 413; Anwendungsfilter vor Bodyübertragung | vollständig, nach Client-SHUT_WR EOF; **Teilnachweis** |
| B: kleines JSON mit ä und Unicode-Escape | chunked in Einbyte-Chunks; P 16 / W 101, Null-Chunk gesendet | 403; Security nach Filter | vollständig; Client schließt; bestanden |
| B: exakt Grenze | chunked; P 33.554.432 / W 33.559.049, Null-Chunk gesendet | 403; Security nach Filter | vollständig; Client schließt; bestanden |
| B: tatsächlich über Grenze | chunked; **P 33.554.433 / W 33.559.050**, Null-Chunk gesendet | **413; Anwendungsfilter** | vollständig, EOF; bestanden |
| C: URL-encoded Form | CL 3; P/W 3 | 415; Anwendungsfilter | vollständig, EOF; bestanden |
| C: Multipart-Abschluss | CL 11; P/W 11 | 415; Anwendungsfilter | vollständig, EOF; bestanden |
| C: nicht unterstütztes Content-Encoding gzip | CL 2; P/W 2, harmlose unkomprimierte Bytes | 415; Anwendungsfilter, keine Entpackung | vollständig, EOF; bestanden |
| C: syntaktisch ungültiges JSON `{` | CL 1; P/W 1 | 400; Anwendungsfilter | vollständig, EOF; bestanden |
| C: JSON-String mit 4097 Zeichen | CL 4105; P/W 4105 | 413; Anwendungsfilter, Strukturgrenze | vollständig, EOF; bestanden |
| C: doppelte gleiche CL | zwei Header 0/0; P/W 0 | **400; Tomcat vor Security** | vollständig, EOF; bestanden |
| C: widersprüchliche CL | zwei Header 0/2; P/W 0 | **400; Tomcat vor Security** | vollständig, EOF; bestanden |
| C: CL zusammen mit TE | CL 0 plus chunked; P 0 / W 5, Null-Chunk gesendet | **403; Security nach Tomcat-Normalisierung** | Connection: close, EOF; **Abweichung zur strikten Header-400-Erwartung** |
| D: GET Home | kein Body, neuer Socket | 200; View, Marker | vollständige Antwort; Verbindung wiederverwendet; bestanden |
| D: GET Einkaufsliste | kein Body, derselbe Socket erst nach vollständiger erster Antwort | 200; View, Marker | vollständige Antwort; danach Client schließt; bestanden |
| D: abschließender GET Home | kein Body, Connection: close | 200; View, Marker | vollständiges HTML, EOF; bestanden |

Statusverteilung: **4 × 200, 3 × 400, 6 × 403, 3 × 413, 3 × 415**. Keine 500,
Timeouts oder unvollständigen Antworten. Bei allen beobachteten EOF-Prüfungen war
EOF beim anschließenden Peek bereits verfügbar; keine verallgemeinerte
Millisekunden-Schließgarantie. Auf weiter nutzbaren Verbindungen wurde kein
verpflichtendes Server-EOF verlangt. Keine Pipelining-Angriffsserie.

### Frühe Ablehnung und tatsächliche Bytegrenze

Bei CL 33.554.433 antwortete der Filter schon vor der ersten Payloadübertragung.
Der Client respektierte diese Ablehnung, beendete seine Sendeseite mit SHUT_WR
und las die vollständige Antwort; danach EOF. Es wurden **nicht** 33.554.433 Byte
mit festem Content-Length vollständig übertragen. Eine solche Behauptung wäre
falsch. Kein Erzwingen des Uploads auf einer bereits abgewiesenen Verbindung.
Der tatsächliche Limit+1-Lesenachweis ohne deklarierte Länge kommt stattdessen
aus dem vollständig gesendeten chunked Fall. Dessen 413 basiert nicht auf einem
überlangen String oder Content-Length. Keine Schwächung von Grenzwerten.
Der SHUT_WR-Fall bestätigt keine spontane Schließung bei dauerhaft fehlendem Body;
die Erklärung aus Abschnitt 20 bleibt davon unberührt.

### Header- und Fehlerschichten

Die Filterantworten stimmen exakt mit den festen Meldungen überein:

| Status | Text | Relevante gemessene Header |
|---|---|---|
| 400 | `Ungültige Anfrage.` | text/plain;charset=UTF-8, Content-Length 19, Connection: close |
| 413 | `Anfrage überschreitet die technischen Größenbegrenzungen.` | text/plain;charset=UTF-8, Content-Length 60, Connection: close |
| 415 | `Nicht unterstütztes Anfrageformat.` | text/plain;charset=UTF-8, Content-Length 35, Connection: close |

Tomcats beide CL-400 dagegen: generische **435-Byte-HTML-Antwort**, Content-Type
`text/html;charset=utf-8`, Content-Length 435, Connection: close; kein
Anwendungsfiltertext und keine Security-Header. Lokaler Parserpfad erklärt die
frühe Ablehnung, daher nicht als Filter-400 ausgeben. Keine erkannten Exception-/
Stacktrace-Indikatoren in den geprüften Antworten. Security-403: JSON/chunked mit
DENY/nosniff; View-200: HTML UTF-8/chunked mit DENY/nosniff. Session-/CSRF-Werte und
vollständige HTML-/Fehlerbodys nicht in den HTTP-Nachweisen gespeichert.

**CL+TE-Besonderheit:** Der schon vor dem Lauf lokal geprüfte
`Http11Processor.prepareInputFilters()` entfernt Content-Length, setzt die
Requestlänge auf −1, verwendet chunked und deaktiviert Keep-alive. Deshalb kann
der Servletfilter die ursprüngliche Headerkombination nicht mehr erkennen.
Gemessen: CSRF-403 und EOF, **keine rohe CL+TE-400**. Dies war anhand des lokalen
Codes vorhersehbar, kein unerklärlicher Laufabbruch. Es ist aber auch **kein
Nachweis einer bedingungslosen Headerkonflikt-Ablehnung**: mit gültigem CSRF wurde
nicht getestet. Die Mock-Assertion für gleichzeitig sichtbare Servletheader
beweist diesen rohen Tomcat-Fall nicht. Kein Smuggling oder Datenzugriff bewiesen;
keine Proxy-/Pipelining-/gültigen Geschäftsrequest-Varianten nachgeschoben.
Falls strikt jede rohe CL+TE-Kombination 400 liefern soll, ist diese Anforderung
mit Connector-Möglichkeiten separat zu bewerten; keine Produktionsänderung jetzt.

### Daten, Browsergrenze und Abschluss

Vorher-/Nachher-Snapshot mit sämtlichen IDs/Spaltenwerten und Schema identisch:

`2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226`

Elf Reihen/Details, 28 Bände, fünf Einkaufspositionen/Details, 17 Stammdaten,
zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf FKs, 26 Key-Column-Einträge
unverändert; AUTO_INCREMENT ausgenommen. Keine neuen Geschäftszeilen oder
Schreib-SQL-/Fixture-/Bereinigungsaktion. Ein lokaler PowerShell-Parserfehler im
ersten Abschlussaufruf verhinderte dessen Ausführung; nach Syntaxkorrektur genau
ein erfolgreicher lesender Abschlusssnapshot, kein DB-Fehler oder Protokoll-Retry.

Zu Beginn verfügbare Werkzeuge geprüft: **kein Codex-In-app-Browser-Connector**.
Browsernachprüfung daher blockiert, HTTP-Abnahme davon unabhängig durchgeführt.
Keine Browserinstallation/native Ausweichlösung, UI-Aktionen oder positiven
HTTP-Ersatzwrites. Weiter offen: echte Dialogbedienung, 413-Anzeige, Eingabenerhalt,
Save-Sperre/kein Replay und bewusste Wiederholung nach Reload.

Eigene PID/Listener vor Stop erneut identifiziert, Ctrl+C in ursprünglicher Session
17252; „Graceful shutdown complete“ und geschlossene EntityManagerFactory beobachtet.
**PID 32320 beendet, Port 18080 frei** unabhängig bestätigt. Kein Force-Stop,
fremder Prozessstopp oder Neustart. Lokale ignorierte Belege:
`.gradle/t02-http-protocol-20261016/` mit `ReadManualSnapshot.ps1`, `before.json`,
`after.json`, `comparison.txt`, `protocol_probe.py`, `results.json`, `shutdown.txt`.

Nur MANUAL_TESTING.md und TESTING.md ergänzt; keine Anwendungscode-/Guard-/Profil-/
Limitänderung, neue Dependency, Produktiv-/mangalib_test-Verbindung, Live-Scraper,
externe Abrufe, Migrationen, SQL-Schreibbefehle, Staging/Commits/Pushes/Merges.
Keine Java-/DOM-Suite wiederholt. T02 insgesamt bleibt offen: Browser-/UX-Abnahme,
Entscheidung über strikte CL+TE-Ablehnung, Ressourcen-/Zeit-/Parallelitätsbudgets,
Scraper-/SSRF-Schutz und Grenzen der synthetischen Altbestandsnachweise.

## Nachtrag 02.11.2026 – Automatisierte Abschlussregression des Scraperumfangs

Die anschließend ausdrücklich freigegebene automatisierte Regression lief
AUSSCHLIESSLICH gegen mangalib_test, nicht gegen die manuelle Testdatenbank:
1016 Javafälle, darunter 29 MVC-/JSON-Verträge, sowie 198 DOM-Fälle bestanden,
jeweils 0 Fehler/übersprungen. Guards zuerst bestanden, separate geschützte
Vorher-/Nachher-Fingerabdrücke identisch:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Zehn Tabellen und 78 Stammdaten unverändert, keine Fixtures verblieben.

Keine manuelle Datenbankverbindung, Browserbedienung oder Anwendung gestartet.
Manual-JAR und synthetische manuelle Umgebung unverändert; kein Seeding/Reset.
Dies ersetzt keine noch offene echte Dialog-/413-/Eingabenerhalt-/Replay-Abnahme.
Die gesonderten nativen Scraper-/Timeoutnachweise sind keine UI-Abnahme des
manual-test-Profils; dieses bleibt ausschließlich mit synthetischen Doubles.

Geschützte Vollsuite ausdrücklich test --rerun-tasks, kein neuer clean test,
um das eingefrorene Manual-JAR zu erhalten. assemble ohne zusätzlichen Testlauf
nach Taskplanprüfung erfolgreich. Vorherige Ergebnisse bleiben historisch.
Aktuelle Vollberichte und Aussagegrenzen: neuer TESTING.md-Nachtrag; keine
Produktivfreigabe, Gitaktionen oder Änderungen fachlicher Prioritäten.

## Nachtrag 02.11.2026 – Echte T02-UI-Abnahme weiterhin offen, neuer Kandidat vorbereitet

Aktueller Gesamtstatus: T02_CLOSEOUT.md. Die frühere 19-Fälle-HTTP-Abnahme und
1016-Java-/198-DOM-Regression bleiben erhalten; sie ersetzen keine echte
Dialogabnahme. Die strikte rohe CL+TE-Ablehnung ist gemäß Eigentümerentscheidung
zurückgestellt, nicht erneut als ungeklärter Implementierungsauftrag anzusehen.

Kleine aktuelle Korrektur: eigene feste 413-/415-Texte in security.js. 413-Sperre
bis Reload und bisherige 415-Korrekturmöglichkeit unverändert, kein automatischer
Retry. 206 datenbankfreie DOMfälle erfolgreich, einschließlich acht neuer Fälle;
keine Browser- oder manuelle Datenbankausführung in diesem Auftrag.

Neues separates UI-Artefakt für die spätere Freigabe:
- build/libs/mangalib-manual-test-t02-closeout.jar
- SHA256 e89e4e8b2947082892ac847ae842793faa63c96f4422d55638508b1c4553fa1f
- Statisch geprüft: aktuelle Feedbackdatei, unveränderte Guard-/Doubleklassen,
  keine Live-Scraper/Selenium/Worker-Ownerklassen. Nicht gestartet.
- Das bisherige mangalib-manual-test.jar bleibt hashgleich erhalten und enthält
  weiterhin den historischen Feedbackstand vor diesen beiden neuen Texten.

Nach gesonderter Freigabe: vorhandenes Start-ManualTest.ps1 mit explizitem
-ArtifactPath auf das neue JAR, zuerst -ValidateOnly, dann manual-test auf
127.0.0.1:18080. Passwort ausschließlich MANGALIB_MANUAL_DB_PASSWORD. Vorher-/
Nachher-Nachweise über das bestehende geschützte Verfahren, keine produktiven
oder automatisierten Testdatenbanken und kein Seeding/Reset.

Begrenzte Abnahme ausschließlich negativer, vor Servicearbeit sicher abgewiesener
Requests: reale 413-/415-Anzeige, Eingabenerhalt, weiterhin bedienbare Dialoge,
keine alerts/Exceptiontexte und kein Replay; 413 auch nach erneutem Öffnen gesperrt,
bewusster erneuter Versuch erst nach Reload. Kein gültiger Speichervorgang,
keine Kauf-/Lösch-/Autofillaktion. Bei 2xx/unklarer Speicherung STOP und keine
Bereinigung. Die genaue sichere Browserprobe ist in T02_CLOSEOUT.md beschrieben;
falls vorhandener Connector sie nicht unterstützt, Eigentümerabnahme statt
Installation/Umgehung. Diese Dokumentation erteilt KEINE Ausführungsfreigabe.

## Nachtrag 02.11.2026 – Gepatchter Kandidat für die letzte negative UI-Abnahme

Die gesondert genehmigte automatische Regression gegen mangalib_test nach dem
Tomcat-Patch war erfolgreich: 1016 Javafälle, alle 29 MVC-/JSON-Verträge, 0 Fehler/
übersprungen; zuerst 44 DB-freie Guards bestanden. 206 DOMfälle ebenfalls bestanden.
Vorher-/Nachher-Fingerabdruck wieder identisch (zehn Tabellen, 78 Stammdaten,
keine Fixtures). Dies ist KEINE Freigabe und kein Zugriff auf die manuelle DB.

Der bisherige e89-Kandidat und das ursprüngliche manuelle JAR sind unverändert.
Damit die verbleibende echte UI-Abnahme bereits den gepatchten Container prüft,
ist ein weiterer separat benannter Kandidat offline gebaut, nicht gestartet:

build/libs/mangalib-manual-test-t02-closeout-tomcat-11.0.27.jar
SHA256: 2b4859de35e3437adf2cc59db8028043860c9b7606d40f0d0b3845e64d0d41e2

Alle neun manuellen Guard-/Double-/Profileinträge bytegleich zum Original,
aktuelle security.js enthalten; drei Tomcatmodule 11.0.27. Keine Selenium-,
Live-Scraper-, Worker-/Owner- oder Downloadmanagerbestandteile. Init-Hilfe nur
lokal ignoriert; keine neue Startkomponente. Boot bleibt 4.1.1.

Erst nach neuer ausdrücklicher Start-/Manual-DB-/Browserfreigabe:
1. Hash, Umgebung, freien Port und geschützten manuellen Datenbankpfad prüfen.
2. Start-ManualTest.ps1 mit -ArtifactPath auf obiges JAR zuerst -ValidateOnly,
   danach tatsächlicher Start ausschließlich manual-test auf 127.0.0.1:18080,
   mangalib_manual_test_user, Passwort nur MANGALIB_MANUAL_DB_PASSWORD.
3. Vorher-/Nachher-Vergleich über das vorhandene geschützte manuelle Verfahren;
   kein Seeding, Reset, direkte SQL-Werkzeuge oder gültiger Geschäftsdatenwrite.
4. Sichtbares Testbanner/Prozesszuordnung prüfen; lesende Dialogaktionen und
   kontrollierte ungültige 413-/415-Requests mit gültiger Session/CSRF/Origin.
   Als zusätzliche Sicherheit immer auch einen unabhängig ungültigen Fachwert
   (z.B. leerer Titel) verwenden, falls ein Filter unerwartet nicht greift.
5. Fehlertexte, Eingabenerhalt, keine alerts/Exceptiontexte, genau einen Request,
   413-Sperre über Dialog-Wiederöffnung bis Reload und 415-Korrektur prüfen.
   Kein automatisches Replay. Bei 2xx/unklarer Speicherung sofort STOP.
6. Nur die eindeutig selbst gestartete Instanz geordnet beenden, sofern im
   gesonderten Auftrag freigegeben; keine fremden Prozesse beenden.

Keine Autofill-, Kauf-, Lösch- oder gültigen Speicheraktionen, keine externen
Navigationen/Live-Scraper. Unterstützt der vorhandene Browserconnector die
kontrollierte Probe nicht sicher, Eigentümerabnahme statt Umgehung/Installation.
ValidateOnly startet selbst eine JVM und wurde in dieser Runde NICHT ausgeführt.
Keine Anwendung/Browser/manual-test-DB gestartet oder angesprochen. Die echte
UI-Abnahme bleibt der letzte dokumentierte T02-Abschlussblock; kein Commit.

## Nachtrag 02.11.2026 – Ergebnis der echten UI-Probe und verbleibender 415-Schritt

Gepatchter Kandidat gemäß obigem SHA gestartet: ValidateOnly erfolgreich,
PID 34056, manual-test, nur 127.0.0.1:18080, Banner bestätigt. Im In-app-Browser
regulären Anlagedialog mit 4097 Zeichen Titel und zusätzlich Bandanzahl 0 einmal
abgesendet. Sichtbarer eigener Größenlimit-Text, sämtliche Eingaben erhalten,
kein Alert und keine error/warn-Konsolenmeldungen. Save bleibt bis Reload gesperrt,
auch nach Cancel/Wiederöffnung. Nach Reload wieder freigegeben, kein weiterer
Speicherversuch. Ein HTTP-Status wurde nicht unmittelbar über den Connector
beobachtet; die UI-Meldung ist kein eigenständiger Statuscode-Mitschnitt.

Die mitfreigegebenen unveränderten manuellen READ-ONLY-Snapshots vor/nach waren
identisch: 2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226.
Elf Reihen, 28 Bände, fünf Einkaufspositionen und alle Stamm-/Schemaabschnitte
unverändert. Das vorhandene, vorher statisch geprüfte Snapshotverfahren verwendet
feste manuelle mysql.exe-Argumente, nur SELECTs in READ ONLY und ROLLBACK. Damit
ist ausschließlich dieses vorhandene Verfahren gemeint; keine frei formulierten
SQL-Befehle oder Datenadministration. Evidenzen in neuem lokal ignoriertem
Verzeichnis .gradle/t02-ui-closeout-20261102/, keine alten Evidenzen überschrieben.

Screenshot: ui-413.jpg im genannten lokalen Verzeichnis. Testtab geschlossen,
Instanz über ihre ursprüngliche Startsession per Ctrl+C beendet. Tomcat meldete
Graceful shutdown complete; JPA geschlossen, PID nicht mehr vorhanden und Port
18080 frei. Kein Force-Kill, keine weitere Instanz. Original- und beide separaten
Manual-JARs unverändert. Keine Autofill-/Kauf-/Lösch-/gültigen Speicheraktionen.

### Noch erforderliche Eigentümerprobe: 415-Fehlerdarstellung

Der derzeitige Connector bietet nur lesendes evaluate und keine Request-
Interception. Die reguläre UI erzeugt immer JSON. Daher konnte Codex keinen
kontrollierten 415-Request mit dieser Oberfläche auslösen. Keine Browser-
Sicherheitsumgehung oder neue Testseite eingeführt. T02 bleibt deshalb offen;
keine pauschale Abschluss-/Commitbehauptung aus grünen MockMvc-/DOMfällen.

Für eine gezielte Eigentümerabnahme (kein weiterer Codex-Start freigegeben):
1. Hash des oben genannten gepatchten Kandidaten erneut prüfen; Startskript mit
   diesem ArtifactPath zunächst -ValidateOnly, dann regulär starten. Ausschließlich
   manuelle Umgebung, keine parallele Instanz. Vorherzustand mit vorhandenem
   Snapshotverfahren in einem NEUEN Evidenzverzeichnis sichern.
2. http://localhost:18080/home öffnen, Testbanner prüfen, Anlagedialog öffnen.
   Nur ungespeicherte synthetische Formulareingaben verwenden.
3. Browser-Entwicklerwerkzeuge öffnen, Network-Aufzeichnung aktivieren. In der
   Konsole EINMAL folgende bestehende Anwendungshilfe aufrufen (CSRF/Host/Origin
   bleiben aktiv; Form-Content-Type führt vor Facharbeit zu 415; zusätzlich sind
   Titel/Anzahl unabhängig ungültig):

```javascript
MangaLibFeedback.request('/addMangaReihe', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: 'titel=&anzahlBaende=0'
}, document.getElementById('popupContainer')).catch(() => {});
```

4. Network: genau diese Anfrage mit HTTP 415, kein automatisches Replay.
   Dialog: „Das Anfrageformat wird nicht unterstützt. Bitte überprüfe deine
   Eingaben.“; Werte erhalten, Save wieder freigegeben, Cancel bedienbar, keine
   nativen Alerts/ungefangenen Promises/internen Fehlertexte. KEIN zweiter
   Speicherversuch erforderlich. Bei 2xx/unklarer Speicherung sofort STOP.
5. Dialog ohne Speichern schließen, Übersicht neu laden. Nachher-Snapshot im
   selben neuen Evidenzverzeichnis vergleichen. Nur eigene Instanz per Ctrl+C
   geordnet beenden. Status, UI-Beobachtung und Datenvergleich zurückmelden.

Keine externen Abrufe, Autofill, gültige Einträge, Kauf-/Löschaktionen, Resets oder
Migrationen. Diese Anleitung ist kein zusätzlicher Codex-Ausführungsauftrag.

## Abschlussnachtrag 02.11.2026 – Eigentümerabnahme HTTP 415 bestätigt

Der Projekteigentümer hat die oben vorbereitete letzte 415-Probe selbst
 erfolgreich durchgeführt und bestätigt:
- HTTP 415 Unsupported Media Type unmittelbar beobachtet.
- Erwartete verständliche Fehlermeldung angezeigt, kein automatisches Replay.
- Daten- und Schema-Snapshot vor/nach identisch.
- Manuelle Instanz kontrolliert beendet, Port 18080 anschließend frei.

Diese Angaben sind Eigentümerbeobachtungen, KEINE von Codex ausgeführte 415-
Abnahme. Kein neuer Snapshot-Hash übermittelt oder ergänzt. Die bisherige
Anleitung und damalige Werkzeuggrenze bleiben als Historie erhalten; der
Schritt ist jetzt abgeschlossen, kein weiterer Ausführungsauftrag daraus.

Codex hatte zuvor die echte 413-UI-Probe mit Eingabenerhalt, verständlichem Text
und Speichersperre bis Reload bestätigt; Status nicht unmittelbar beobachtbar.
Keine nachträgliche Behauptung eines gemessenen 413-Codes. T02 ist damit für das
freigegebene lokale Einzelbenutzermodell technisch abgeschlossen; maßgeblich ist
T02_CLOSEOUT.md. Keine erneuten Browser-/DB-/Anwendungs-/Scraperprüfungen oder
Datenänderungen im Abschlussauftrag. Lokaler Abschlusscommit freigegeben, kein
Push/Merge; T03 weiterhin nur vorbereitet. LAN/Internet, globale OS-Isolierung,
vollständige Prozessprovenienz und allgemeine Schwachstellenfreiheit sind nicht
Teil dieser Abnahme. Historische JARs/Evidenzen und Sicherheitsguards erhalten.


## T03-1/T03-2 – neue Kaufbereichsbestätigung (Vorbereitung, nicht ausgeführt)

Diese Anleitung ist KEINE Start-/Schreibfreigabe. Nur nach gesonderter Freigabe die
abgesicherte manual-test-Umgebung verwenden; alle bisherigen Launcher-/DB-/Proxy-
Schutzbedingungen gelten unverändert. Nie mangalib oder mangalib_test im Browser.
Nur eigens neu angelegte synthetische T03-Datensätze verändern; Altbestände erhalten.

1. Einen neuen synthetischen Einkauf mit eindeutiger kompatibler Reihenidentität
   und bekanntem Preis vorbereiten (separate Schreibfreigabe). Bestehende Band-IDs,
   Nummern, Preise, Flags, Lesestatus und Rereads der eigenen Zielreihe erfassen.
2. Kaufen klicken: Bei vorhandener eindeutiger Reihe erscheint die neue kleine
   Bestätigung „Bände N+1–Z ... übernehmen?“. Noch keine neuen Bände, keine
   Kaufmarkierung. Abbrechen bleibt schreibfrei; bewusster neuer Klick zeigt erneut
   die Vorschau. Kein automatischer zweiter Request.
3. Nur für eigene neue Daten ausdrücklich „Bände übernehmen“ klicken. Nach Reload
   exakt die bestätigten neuen Nummern, gekauft=true und unveränderte Altbandwerte.
   Neue Bände ungelesen; Reihen-Reread erhalten, Reihenlesestatus neu abgeleitet.
   Preise/Summen und positive/negative Restcents prüfen. Band-ID ist keine Reihen-ID.
4. Denselben bereits gekauften Einkauf erneut bewusst aufrufen: feste Meldung
   „Kauf bereits übernommen“, keine weitere Bibliotheks-/Markierungsänderung.
5. Ohne Zielreihe bleibt die bestehende Statusauswahl (417). Auswahl erzeugt nur
   eine neue Reihe. Wurde inzwischen eine eindeutige Zielreihe angelegt, muss auch
   dieser Weg vor Append die ausdrückliche Bandbereichsbestätigung verlangen.
6. Stale Bereich gezielt nur mit eigenen Daten/gesonderter Freigabe erzeugen:
   409 BAND_CONFLICT, feste verständliche Meldung, keine Speicherung/Autowiederholung.
   Auch unbekannter Kaufstatus, unklare Edition und inkonsistenter Bestand sind
   feste 409-Konflikte. Keine internen Fehlertexte oder Fehlerbodys anzeigen.
7. Unklarer 5xx-/Transport-/Antwortfehler: Dialog/Werte erhalten, Speicherklick bis
   Reload gesperrt; auch Cancel darf keinen automatischen neuen Kauf auslösen.
   Nach unbekanntem Transaktionsende sperrt der Coordinator weitere Writes.
   Keine eigenmächtige Wiederholung oder Bereinigung; Zustand getrennt prüfen.
8. Abschließend nur über freigegebene geschützte Verfahren Vorher/Nachher vergleichen.
   Keine Bestandsdaten löschen, keine automatische Entfernung der synthetischen Daten.

DOM-Prüfungen für diese Bediensemantik bestanden (225 gesamte DOM-Suite);
keine echte Browser-/Datenbankprüfung in diesem Auftrag. Neue externe Bandindices
werden weder erfunden noch durch die Bestätigung bewiesen. Vollständige reale
Abnahme und sichere Anpassung der Integrationstest-Transaktionen bleiben T03-6.
