# Isolierte manuelle Browserabnahme unter Windows

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
Set-Location 'C:\Users\drewe\Documents\MangaLib'
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
