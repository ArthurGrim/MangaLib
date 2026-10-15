# Sichere Tests für MangaLib

## Voraussetzungen und Grenzen

- Java 21 und der mitgelieferte Gradle Wrapper.
- Bereits eingerichtetes MySQL auf `localhost:3306`, Datenbank `mangalib_test`.
- Ausschließlich Benutzer `mangalib_test_user`; Passwort nur über die Prozess-Umgebungsvariable `MANGALIB_TEST_DB_PASSWORD`.
- Bestehendes Schema und die fünf Stammdatentabellen bleiben erhalten. Der Sammelbandtyp mit ID 1 wird vom aktuellen Anwendungscode vorausgesetzt.
- Kein Produktivpasswort erforderlich. Keine produktiven Konfigurationsdateien importieren.
- Der Testbenutzer muss serverseitig ausschließlich auf `mangalib_test` berechtigt sein, ohne globale Rechte oder Rechte auf `mangalib`. Keine CREATE-, ALTER- oder DROP-Rechte für Tests vergeben. Diese Berechtigungen wurden durch die Codeänderung nicht geprüft oder verändert.

## Schutz vor dem ersten Datenbankzugriff

`src/test/resources/META-INF/spring.factories` registriert eine `ContextCustomizerFactory` für Spring-TestContext. Alle aktuellen datenbankabhängigen Tests verwenden zusätzlich `@ActiveProfiles("test")`.

Die Factory registriert vor dem Kontextstart eine ausschließlich für Tests vorhandene `GuardedTestDataSource`. Spring Boots DataSource-Autokonfiguration weicht dadurch dieser Bean. Ein BeanFactoryPostProcessor prüft die effektive Konfiguration vor der regulären Singleton-Erzeugung, also auch vor der Hibernate-Schemavalidierung. Ein fehlendes oder zusätzliches Profil führt zum Abbruch; neue Spring-Tests ohne Testprofil werden nicht stillschweigend gegen die Standarddatenbank gestartet.

Erlaubt sind exakt:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mangalib_test?useSSL=false
spring.datasource.username=mangalib_test_user
spring.datasource.password=${MANGALIB_TEST_DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.generate-ddl=false
spring.sql.init.mode=never
spring.flyway.enabled=false
spring.liquibase.enabled=false
```

Abweichende URLs einschließlich anderer Hosts, zusätzlicher JDBC-Parameter oder anderer Datenbanknamen werden abgewiesen. Zusätzliche Pool-/JNDI-/DataSource-Einstellungen und direkte `spring.jpa.properties` sind bewusst nicht erlaubt. Das betrifft auch Overrides aus Umgebungsvariablen, JVM-Properties oder Testkonfigurationen. Alternative DataSource-Beans werden abgewiesen.

Unmittelbar vor jedem Verbindungsaufbau wird erneut geprüft. An JDBC gehen ausschließlich die feste Test-URL, der feste Testbenutzer und das direkt aus `MANGALIB_TEST_DB_PASSWORD` gelesene Passwort. Die Überladung mit anderen Zugangsdaten ist gesperrt. Die ungepoolte DataSource vermeidet weitere überschreibbare Pool-Verbindungsparameter und genügt für die kleine Testsuite.

Diese Schutzschicht ist keine Sandbox für beliebigen Java-/SQL-Code. Neue Tests dürfen keine eigenen DriverManager-/DataSource-/JNDI-Verbindungen, manuelle SpringApplication-Starts, Konfigurationsimporte mit Nebenwirkungen oder qualifizierten Zugriffe auf andere Schemas hinzufügen. Die eingeschränkten MySQL-Rechte bleiben die unabhängige letzte Schutzgrenze. Eine lokale DNS-/Port-Weiterleitung oder falsch vergebene Serverrechte kann die Konfigurationsprüfung nicht erkennen.

## T02 – Schreibvalidierung: Analyse, Grenzen und datenbankfreie Prüfung

Stand 11.10.2026, Branch `codex/t02-input-validation`. Zunächst ausschließlich
datenbankfrei geprüft; anschließend hat der Projekteigentümer die gestufte
Datenbankvalidierung ausdrücklich freigegeben. Vor jedem Gradle-Testlauf wurde der
identische Aufruf mit `--dry-run` geprüft. Datenbankzugriffe ausschließlich über den
unveränderten Guard auf `mangalib_test` / `mangalib_test_user`; keine produktive oder
manuelle Datenbank, Migration, Schemaänderung, Dateninitialisierung, Anwendung,
HTTP-Listener, Browser oder echter Scraper. Bestehende Guards, Testprofil,
Entity-Mappings, Security-Konfiguration, MVC-Vertragsassertions und Abhängigkeiten
bleiben unverändert.

Die statische Ausgangsanalyse fand ungeprüfte Map-Casts/Parse-Aufrufe in
`MainController` und `EinkaufslisteController`, unbegrenzte Band-Schleifen in
`MangaReiheService`/`BandService`, ungeprüfte Importwerte und URL-Objektgleichheit.
Kleine Request-Records werden deshalb ausdrücklich aus typgeprüften Maps erzeugt;
eine automatische DTO-Deserialisierung könnte bestehende JSON-Typfehler still
koerzieren. Die bestehenden JavaScript-Payloads bleiben unverändert:

| Schreibpfad | Vertrag und Prüfung vor Servicearbeit |
|---|---|
| `/addMangaReihe` | String-IDs, numerische Ganzzahlen für Index/Anzahl/Reread, echte Boolean-Werte, String-Preise; Edit verlangt Reihen-ID und Lesestatus. Fehlender Preis/fehlende Anpassung bleiben 0 mit bisheriger Servicebedeutung; nullable Flags/optionaler Index bleiben erhalten. |
| `/editBand` | String-ID/-Nummer/-Index/-Reread/-Preise, echter Lesestatus-Boolean; fehlende/leere optionale Preise/URLs bleiben null, fehlender/leerer Reread bleibt 0. Erfolgreiches 204 bleibt leer. |
| `/addToEinkaufsliste` | String-Referenz-IDs/-Preise, numerische Bandanzahl, Titel; beide Preise erforderlich. Optionales Datum muss ISO-LocalDate sein. Vorhandener Double-Serviceweg bleibt erhalten. |
| `/buyItemUpdate`, `/buyItemCreate` | Kauf-IDs als Ganzzahl oder dezimaler String, keine Boolean-/Fließkomma-/Objekt-Koerzierung; Status-ID bei Create erforderlich/positiv. Nichtpositive Item-ID weiter 404, jetzt ohne Lookup. Bestehende 200-/404-/417-Payloads erhalten; kombinierte Bandanzahl vor Update/Kaufmarkierung begrenzt. |
| `/scrape`, `/autofillBandData/{bandIndex}` | Positive dezimale Integer-Indizes vor Gateway/Service. Import-Map vor Übernahme prüfen; fehlende optionale Metadaten lösen keine Null-Lookups/NPE aus. POST/CSRF und GET-/HEAD-405 bleiben erhalten; OPTIONS startet nichts. |

Zentrale technische Regeln in `validation/WriteValidation.java`:

- Bandanzahl **1–10.000**: großzügige Grenze für sehr lange Reihen, begrenzte synchrone
  INSERT-/Update-Schleifen. Auch Save/Update von Reihen und die beiden Band-Schleifen
  sowie der Einkaufsservice prüfen vor Repositoryarbeit. Kein massenhafter Grenzwert-
  Test schreibt Daten; positive Grenzwerte werden mit Mocks geprüft.
- Reread **0–10.000**, null bleibt erlaubt; keine neue fachliche Reread-Regel.
- Referenz-IDs positiv im Long-Bereich, externe Indizes positiv im Integer-Bereich
  (einschließlich großer bisheriger Testwerte). Referenzen weiterhin über bestehende
  Services/Repositories; unbekannte Referenzen werden nicht als null gespeichert.
- Titel nicht null/blank und maximal **255 Unicode-Codepoints**, passend zu den
  bestehenden JPA-String-Mappings. Keine Zeichenentfernung, kein Trimmen/HTML-Sanitizing.
- Preise: maximal 64 Eingabezeichen, BigDecimal-Scale **−8 bis 10**, Betrag **kleiner
  als 100.000.000** wegen vorhandener DECIMAL(10,2)-Summen-/Anpassungsspalten.
  Nicht-endliche Double-Werte und technisch extreme Exponenten werden abgewiesen.
  Negative Basispreise und Anpassungen werden **nicht pauschal verboten**; keine
  Rundung/Restcent-/Aggregationsregel geändert. Der Einkaufsservice prüft zusätzlich
  das Ergebnis seiner bereits bestehenden Multiplikation/Addition auf die Grenze.
- Optionale URLs behalten null/leer; ansonsten absolute HTTP(S)-URI, Host, kein
  Userinfo, zulässiger Port und maximal 255 Unicode-Codepoints. Internationale
  Domainnamen werden syntaktisch mit IDN geprüft, ohne gespeicherte Strings zu ändern.
  Keine Host-/AniList-Pfad-Allowlist und keine DNS-/HTTP-Prüfung. URL-Vergleich rein
  über `toExternalForm()`, absichtlich ohne DNS-basierte Hostgleichheit.
- Importmaps ausschließlich Stringwerte; null optionale Einträge werden in einer
  Kopie weggelassen. Schlüssel/Werte begrenzt, höchstens `3 * MAX_VOLUMES + 32`
  Einträge; bekannte Cover-/Bandbild-/Bandlink-/AniList-Felder und Bandpreise geprüft.
  Keine automatische Änderung historischer Datensätze.
- Erwartete Fehler neutral 400 (Bandedit weiterhin leer), bestehende fehlende Kauf-
  Datensätze 404; unerwartete interne Fehler neutral 500. Kein Exceptiontext/Rohpayload
  im Response. Direkt betroffene Controller-Rohlogs/Stacktraces entfernt; T06 bleibt offen.

Tatsächlich bestanden: **297 datenbankfreie Java-Tests** (44 Guard, 61 Policy,
93 neue MVC-, 38 neue Service-, 61 neue Validatorfälle), **85 bestehende manuelle
Mock-/Schutz-/Template-/Artefaktfälle** und **93 bestehende DOM-Tests**. Keine Fehler
oder übersprungenen Fälle in den abschließenden Läufen. Ein neuer Mockito-Typfehler
verhinderte zunächst die Kompilierung; ein neuer Negativfall enthielt zunächst eine
gültige String-Map. Beide Testfehler wurden korrigiert, keine Schutzregel abgeschwächt.
Bestehende HotSpot-CDS-Warnung und erwartete 405-Warnungen bleiben. Keine neuen
Abhängigkeiten/Downloads; Gradle lief offline. `ManualScraperResponseTest` unterscheidet
jetzt stärker zwischen syntaktisch ungültigem Input (kein Double/kein Rawlog) und
syntaktisch gültigem, nicht freigegebenem Double-Index (bestehender manueller 400-Vertrag).

Reproduktion ausschließlich datenbankfrei, jeweils Plan vor Ausführung lesen:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*'
.\gradlew.bat --offline --no-daemon manualSafetyTest --dry-run
.\gradlew.bat --offline --no-daemon manualSafetyTest
pnpm run test:dom
```

Nach ausdrücklicher Eigentümerfreigabe tatsächlich zusätzlich ausgeführt:

| Gestufte Datenbankvalidierung | Ergebnis |
|---|---|
| `TestDatabaseGuardTest` erneut vor dem ersten DB-Zugriff | **44 bestanden** |
| `DatabaseIntegrityBaselineTest`, `MangalibApplicationTests`, `DemoApplicationTests` | **3 bestanden**, Ausgangsfingerabdruck erfasst |
| `MvcContractTest`, `LocalSecurityMvcTest`, `ServiceTest`, `PersistenceCompatibilityTest`, `RepositoryCompatibilityTest`, `TestDataPreservationTest`, `TemplateRenderingTest` | **71 bestanden**, davon 29 unveränderte MVC-/JSON-Verträge |
| `clean test` | **371 bestanden** = 179 bisherige + 192 neue Tests; 0 Fehler, 0 übersprungene Fälle |
| `DatabaseIntegrityBaselineTest` nach Abschluss erneut | **1 bestanden**, Vorher-/Nachher-Fingerabdruck identisch |

Alle Gradle-Läufe offline; Aufgabenpläne enthalten nur Kompilierung/Ressourcen/Test,
bei `clean test` zusätzlich `clean`. Passwortverfügbarkeit ohne Wertausgabe geprüft.
Neue Schreibfixtures bleiben innerhalb bestehender `@Transactional`-/`@Rollback`-
Tests; Stammdaten werden nur gelesen. Vorher-/Nachher-Kontrollen und Scraper-Mocks
bleiben aktiv. Das getrennte manuelle Artefakt wurde nicht gestartet.

Fingerabdruck der zehn Tabellen und Schema-Metadaten vor/nach den DB-Läufen:
`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`.
**78 Stammdaten erhalten**: Verlage 62, Status 4, Typen 4, Formate 3, Sammelbände 5;
die fünf Geschäftstabellen bleiben leer. Geprüfte Schema-Metadaten unverändert:
10 Tabellen, 75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel und 26 Key-Column-Einträge.
AUTO_INCREMENT-Zähler sind bewusst ausgenommen und werden nicht zurückgesetzt.
Der vollständige `clean test`-Bericht ist lokal vor dem separaten Abschlusslauf unter
`.gradle/t02-validation/clean-test-report/index.html` gesichert (Git-ignoriert).

Reproduktion des abschließenden Volltests **nur mit entsprechender DB-Freigabe**:

```powershell
.\gradlew.bat --offline --no-daemon clean test --dry-run
.\gradlew.bat --offline --no-daemon clean test
```

Die neue DB-Freigabe umfasst diese automatisierte Validierung, keinen manuellen
Anwendungs-/Browserstart. Live-Scraper und Migrationen bleiben ausgeschlossen.
Für weitere DB-Läufe: konkrete Klassen/Taskplan, Testziel/-benutzer, erforderliche
INSERT/UPDATE/DELETE auf ausschließlich neuen Fixtures und Rollback-/Snapshot-Schutz
darlegen und **ausdrückliche Eigentümerfreigabe abwarten**. Erst dann Passwort ohne
Wertausgabe prüfen, Guard-Tests zuerst, geschützte Verträge/Persistenz und `clean test`.
Keine DB- oder manuelle Startfreigabe aus älteren Aufträgen ableiten.

Offen: produktive Altbestandsverträglichkeit der technischen Grenzen und manuelle
Formularabnahme. Die isolierte Testdatenbank hat keine bestehenden Geschäftsdaten;
ihre erfolgreichen Regressionstests ersetzen diese Prüfung nicht. Breite Request-/
Importgrößen-, Scraper-Zeit-/
Parallelitäts-/Redirect-/Unterressourcen- und SSRF-Grenzen. HTTP(S)-Syntax ist keine
SSRF-Abwehr. Unveränderte Alt-URLs werden nicht umgeschrieben; ihre spätere erneute
Übernahme kann bei unzulässiger Syntax abgewiesen werden. Fehlende Referenzen/NULL-
Altbestände benötigen gegebenenfalls eine eigene Fach-/Datenentscheidung. M06,
T03 (einschließlich Restcent, Idempotenz, Bandidentifikation/-reduktion) und T06 nicht
vorgezogen. Spätere manuelle Prüfpunkte: [MANUAL_TESTING.md](MANUAL_TESTING.md).

## Testklassen und Nebenwirkungen

| Test | Verhalten |
|---|---|
| `TestDatabaseGuardTest` | Reine Konfigurations- und Lifecycle-Prüfungen mit synthetischen Werten; keine JDBC-Verbindung, kein Spring-Boot-Start, kein Passwort nötig. |
| `MangalibApplicationTests` | Testprofil, Kontext-/Hibernate-Start mit Schemavalidierung; prüft den geschützten DataSource-Typ. |
| `DemoApplicationTests` | Testprofil, Kontext-/Hibernate-Start; prüft Repository-Verdrahtung. |
| `ServiceTest` | Liest bestehende Stammdaten, legt ausschließlich neue Reihe/Details/Bände in einer Testtransaktion an; expliziter Rollback. Flush/Clear erzwingt einen echten Reload. Kein Seeding, Cleanup oder Tabellenreset. |
| `MangaScraperTest`, `BandScraperTest` | Reguläre JUnit-Methoden mit Assertions, aber per `@Disabled` deaktiviert und zusätzlich durch Gradle-Tag `external-network` ausgeschlossen. Kein Spring-Kontext. |

Rollback setzt eine transaktionale Storage Engine (InnoDB) voraus. AUTO_INCREMENT-Zähler können trotz Rollback steigen; vorhandene Datensätze werden dadurch nicht verändert. Neue Tests dürfen keine bestehenden Stammdaten verändern, kein `@Commit`, `REQUIRES_NEW`, asynchrones Schreiben oder unkontrollierte HTTP-Schreibtests verwenden. Solche Erweiterungen benötigen ein gesondertes Isolationskonzept.

## Erster kontrollierter Lauf – erst nach Prüfung und Freigabe

Historischer Ablauf für den ersten freigegebenen Lauf. Inzwischen hat Codex die abgesicherte Suite unter Spring Boot 3.5.16, **4.0.8** und **4.1.1** erfolgreich ausgeführt: jeweils **91 Tests, keine Fehler** (10.10.2026). Unter 4.1.1 bestanden zuerst 44 datenbankfreie Schutztests, danach alle 47 geschützten Integrations-/Regressionstests und anschließend `clean test`; Daten-/Schema-Fingerabdruck vor/nach dem Wechsel identisch, `assemble` erfolgreich. Aktueller Stand und verbleibende Grenzen: [BOOT4_MIGRATION.md, Abschnitt 13](BOOT4_MIGRATION.md#13-tatsächlicher-fortschritt-validierter-411-zwischenstand). Die technische Testfreigabe ersetzt keine Produktions-/Altbestands- oder Browserabnahme.

1. Diff prüfen; ein Administrator bestätigt Zielinstanz, ausschließlich eingeschränkte Testrechte und InnoDB. Keine Schema-/Seed-Skripte ausführen.
2. Zunächst nur die Schutzprüfungen ohne Datenbank ausführen:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
   ```

3. Das Testpasswort in derselben PowerShell-Sitzung ohne Klartexteingabe in der Befehlshistorie setzen:

   ```powershell
   $testCredential = Get-Credential -UserName mangalib_test_user -Message 'Passwort der MangaLib-Testdatenbank'
   $env:MANGALIB_TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
   ```

4. Nur den Kontext-/Schemavalidierungstest starten:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.MangalibApplicationTests
   ```

5. Erst nach dessen Prüfung den transaktionalen Service-Test und anschließend die automatische Suite ausführen:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.ServiceTest
   .\gradlew.bat --no-daemon test
   ```

6. Testberichte unter `build/reports/tests/test/index.html` prüfen und die Variable anschließend entfernen:

   ```powershell
   Remove-Item Env:MANGALIB_TEST_DB_PASSWORD
   Remove-Variable testCredential
   ```

Gradle kann beim ersten Lauf Dependencies herunterladen. Das ist vom gesperrten automatischen Scraping zu unterscheiden. Die Scraper bleiben auch bei IDE-Ausführung deaktiviert; ihre manuelle Aktivierung erfordert eine separate Freigabe und eine bewusste Code-/Task-Anpassung.

`check` und `build` können ebenfalls Tests starten. `bootRun` startet hingegen die normale Anwendung und verwendet diese ausschließlich im Test-Classpath liegende Schutzschicht **nicht**. Es ist kein Befehl zur Testvalidierung.

## Lokale Zugriffssicherheit S02/S03

Die explizite Security-Filterkette ist auch in Tests aktiv. `LocalSecurityPolicyTest`
prüft Authority-/Origin-Regeln, Konfigurationsüberschreibungen und die konfigurierte
Server-Factory rein datenbankfrei. Zuerst wie oben die 44 `TestDatabaseGuardTest`-Fälle
ausführen, danach diese Policy-Tests. Anschließend die geschützten MVC-/Regressionstests
und `clean test` nach Prüfung des Aufgabenplans; DOM-Tests separat mit `pnpm run test:dom`.

MockMvc-Requests benötigen einen expliziten lokalen Host samt konfiguriertem Port
(Standard `localhost:8080`) und denselben Servlet-Server-Port. Schreibrequests brauchen
einen gültigen CSRF-Token; niemals `addFilters=false` einsetzen. Die bestehenden
MVC-Vertragsfälle verwenden Spring Securitys Test-Postprocessor für gültige Tokens;
`LocalSecurityMvcTest` prüft dagegen echte, über `/home` gerenderte Tokens mit derselben
Session, falsche Tokens und fremde Sessions. Beide Scraper werden dort statisch ersetzt,
alle Daten-/Schema-Fingerabdrücke vor/nach jedem Fall verglichen. Positive Scraperfälle
führen ausschließlich diese Doubles aus und schreiben keine Fixtures. Die bisherigen
transaktionalen MVC-/Persistenztests behalten Rollback und ihre Assertions.

Für den normalen lokalen Betrieb gelten `http://localhost:8080/home` oder
`http://127.0.0.1:8080/home`. Ein anderer gültiger `server.port` gilt gleichermaßen
für Host und Origin; `server.address` bleibt zwingend `127.0.0.1`. Unsichere Overrides,
Proxy-Header-Verarbeitung, separate Management-Ports und erweiterte Health-Ausgabe
führen zum Startabbruch. Die fertig angepasste Server-Factory wird zusätzlich auf
Loopback-Adresse, erwarteten Port und deaktiviertes TLS geprüft. Vorhandene Origin-
Header müssen zur Request-Authority passen; widersprechende Referer-/Sec-Fetch-Site-
Header verhindern Schreibaktionen, fehlende Header ersetzen niemals den CSRF-Token.
Kein Login oder Standardbenutzer, kein LAN-/IPv6-Betrieb.
Die Session bleibt per HttpOnly-/SameSite=Strict-Cookie erhalten, ohne URL-Rewriting;
Secure wird im aktuellen HTTP-Betrieb nicht gesetzt. `localhost` und `127.0.0.1` sind
verschiedene Origins/Sessions: innerhalb einer geöffneten Seite dieselbe Adresse verwenden.
Fehlender/abgelaufener Token oder 403 erfordert manuelles Neuladen und erneute bewusste
Aktion; kein automatischer Kauf-/Schreib-Replay. Alte Autofill-GET-/HEAD-Aufrufe liefern
405, OPTIONS startet keinen Scraper; Autofill und `/scrape` benötigen POST mit Session-CSRF.

Diese Prüfungen starten keinen echten HTTP-Listener. Tatsächliche Cookie-Übertragung,
Browsernavigation, mehrere Tabs/Neustart und Loopback-Erreichbarkeit müssen später
in einer gesondert freigegebenen isolierten Umgebung manuell geprüft werden. Ein lokales
untrusted Programm oder eigenes XSS wird durch den anmeldungsfreien CSRF-Schutz nicht
abgewehrt. Kein produktiver Start zur Testvalidierung.

Bei einem Schutzfehler nicht die Schutzregeln lockern: widersprüchliche `SPRING_*`-/JPA-/DataSource-Einstellungen entfernen beziehungsweise das freigegebene Testziel wiederherstellen. Passwörter und vollständige Environment-Dumps gehören weder in Logs noch in Git.

## Getrennte manuelle Browserumgebung

Die freigegebene Vorbereitung verwendet ein eigenes Abnahme-JAR und `manual-test`-Profil,
`mangalib_manual_test` / `mangalib_manual_test_user`, `MANGALIB_MANUAL_DB_PASSWORD` und
`127.0.0.1:18080`. Sie verwendet **nicht** die JUnit-Guard-Registrierung. Diese Dateien
und das automatisierte Testprofil bleiben unverändert. Manuelle HTTP-Aktionen committen
in der dritten Datenbank und dürfen niemals gegen `mangalib_test` ausgeführt werden.

`manualSafetyTest` führt ausschließlich datenbankfreie Negativprüfungen, JDBC-Mocks,
Archivkontrollen und `--validate-only`-Unterprozesse mit synthetischem Passwort aus.
`scripts/Test-ManualScripts.ps1` prüft synthetische SQL-Texte ohne SQL-Ausführung.
`scripts/Start-ManualTest.ps1 -ValidateOnly` öffnet weder DB-Verbindung noch Listener;
ohne diesen Schalter startet das Skript die Anwendung und benötigt weitere Freigabe.
Historischer Vorbereitungsstand: Konten, Schemaexport/-import, Fixtures und realer
Kontext-/Browserstart waren zunächst offen. Der Eigentümer hat Einrichtung und
Browserabnahme am 11.10.2026 durchgeführt; dies sind manuelle Beobachtungen,
keine erneute Codex-Integrationstestausführung. Die anschließende Fehlernachbereitung
verwendet ausschließlich DB-freie Mock-/Template-/DOM-Tests; sämtliche Guards bleiben aktiv.
Einrichtung, genaue Rechte/Befehle und Checkliste: [MANUAL_TESTING.md](MANUAL_TESTING.md).


## T02-Nachbesserung – nicht blockierende Schreibfehler (11.10.2026)

Historische Prüfläufe bleiben oben erhalten. Dieser Folgeauftrag war vollständig
DB-frei: **158 DOM-Fälle (93 bestehende + 65 neue)**, **297 Java-Fälle**
(44 Guard, 61 Policy, 192 Unit-/Service-/Standalone-MockMvc) und **85 manuelle
Schutz-/Template-/Artefaktfälle** erfolgreich, jeweils 0 Fehler/Skipped.

Ausgeführte Befehle, jeweils Aufgabenplan vor Gradle-Ausführung:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' manualSafetyTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' manualSafetyTest
node --test src/test/js/*.test.cjs
```

`manualSafetyTest` wurde nach den letzten Frontendänderungen mit eigenem `--dry-run`
vorher erneut ausgeführt; Wiederholungen sind nicht zusätzliche Testfälle. Paketierung
aktualisiert das lokale manuelle JAR; Artefakt-Unterprozesse benutzen ausschließlich
`--validate-only` und kehren vor SpringApplication/JDBC/HTTP zurück. Keine Anwendung,
DB-/SQL-/HTTP-/Browserverbindung, echten Scraper, neuen Dependencies oder Schemaänderung.
Bestehende HotSpot-CDS-Warnung unverändert. Die 371-Test-Vollsuite, `clean test` und
DB-/Vollkontext-MVC-Tests **nicht** ausgeführt, da keine neue DB-Freigabe vorlag.

`write-feedback.test.cjs` prüft die tatsächlichen Formularhandler mit Response-Doubles
und ohne Netzwerk: 400 JSON/Text, 403, 404, Kauf-417, 500 HTML, Band-204, Delete-200,
JSON-Erfolg/Formatfehler/fehlenden Body, Netzwerk-/AbortError, Werterhalt, sichere
Inline-Meldungen/Fokus, keine Schreibwiederholung, Cancel/erneutes Öffnen, ID-Label,
Cover-Fallback und Lazy Loading. jsdom-Reloads werden als erwartete nicht unterstützte
Navigation erfasst; echte UI-/Session-/Bildprüfung bleibt manuell. Alte Sicherheits-
und Payloadassertions bleiben erhalten; gewünschte Präsentation wird jetzt inline
statt über native Alerts geprüft. Keine serverseitige Idempotenz oder T03/M06/T06.

400/404 erlauben korrigierte ausdrückliche Klicks. 403/5xx/unklare Schreibantworten
sperren den Button bis Reload; gespeicherten Stand vor erneutem Schreiben kontrollieren.
Fehlerbodys werden nie als UI-Text gelesen. 403 beweist keinen Sessionablauf.
Ursachen, vier UI-Korrekturen und Abnahmecheckliste:
[MANUAL_TESTING.md, Abschnitt 12](MANUAL_TESTING.md#12-t02-nachbesserung-dialogfehler-und-vier-ui-befunde-11102026).

## T02-Nachbesserung – Reread, Einkaufspreise und lange Titel (14.10.2026)

Die früheren Prüfergebnisse bleiben historische Nachweise. Dieser Auftrag wurde
ausschließlich datenbankfrei geprüft: **308 Java-Tests**, **32 ausgewählte manuelle
Template-/Mock-Tests** und **164 DOM-Tests**, jeweils ohne Fehler oder übersprungene
Fälle. Darin neu: fünf Controller-Fälle, sechs Anlage-Service-Fälle, acht Preisfälle
und sechs CSS-/DOM-Fälle. Die vollständige Spring-/Integrationstestsuite wurde nicht
ausgeführt; es gab keine Datenbankfreigabe für diesen Auftrag.

Vor jedem Gradle-Testlauf wurde folgender Aufgabenplan geprüft; danach derselbe
Befehl ohne `--dry-run` ausgeführt:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' manualSafetyTest --tests de.mangalibmanual.ManualBannerTemplateTest --tests de.mangalibmanual.ShoppingPriceTemplateTest --tests de.mangalibmanual.SeriesEditRegressionTest -x manualBootJar --dry-run
node --test src/test/js/*.test.cjs
```

`-x manualBootJar` und die explizite Auswahl der drei manuellen Testklassen verhindern
hier eine Neupaketierung des laufenden manuellen JARs und Artefakt-Unterprozesse.
Ausgeführt wurden nur Unit-/Standalone-MockMvc-/Mock-/Template-Tests, kein Boot-
Anwendungsstart, JDBC, HTTP-Listener oder Browser. Die 44 Guard- und 61 Policy-Fälle
sind enthalten. Bestehende Sicherheitsfilter und Assertions bleiben aktiv.

- **Reread:** Der Anlage-Controller verwarf bisher den bereits gebundenen und
  validierten Wert. Er übergibt ihn jetzt an den Anlage-Service, der ihn vor dem
  ersten Details-Speicheraufruf setzt. Controller-Tests prüfen fehlend/null/0/1/10000
  bis zum echten Serviceaufruf mit Mock; Service-Tests prüfen den tatsächlichen
  Speicheraufruf und die Ablehnung von -1/10001 vor Repositoryzugriffen.
  Fehlend/null im HTTP-Anlagevertrag bleibt 0; bisherige Serviceaufrufer ohne
  Reread behalten null. Bearbeitung und Band-/Lesestatusregeln bleiben unverändert.
- **Preise:** Die echten Thymeleaf-Ausdrücke werden mit BigDecimal-Werten und
  absichtlich nicht deutscher Kontext-Locale gerendert. Einzelpreis, Positionssumme
  und Monatssumme haben deutsche Trennzeichen, zwei Nachkommastellen und genau ein
  Währungssymbol, einschließlich Nullbetrag, Tausendergruppen und Unicode-Minus.
  Numerische Werte und vorhandene Rundung/Berechnung bleiben unverändert.
- **Layout:** CSS-/DOM-Tests prüfen schrumpfbare Textspalten, Titelumbruch,
  getrennte Aktionsspalten, Stylesheet-Reihenfolge und schmale Dialogregeln.
  jsdom prüft keine Pixelgeometrie oder reale Mausklicks; dies bleibt Browserabnahme.

Erstläufe fanden eine falsche Stylesheet-Reihenfolge, einen CSSOM-Erwartungswert
(`0px` statt `0`) und bestehende Fehlerantwort-Mocks mit der alten Service-Signatur.
Diese wurden korrigiert; Fehlerantwort-Assertions wurden nicht abgeschwächt.
Die abschließenden Läufe bestanden. Die bestehende HotSpot-CDS-Warnung bleibt.
Keine Änderungen an Guards, Schema, Daten oder Abhängigkeiten; M06/T03/T06 wurden
nicht vorgezogen. Die laufende Eigentümerinstanz verwendet weiterhin ihr bisheriges
JAR. Browsernachprüfung erst nach gesondert freigegebener Paketierung/Neustart,
siehe [MANUAL_TESTING.md, Abschnitt 13](MANUAL_TESTING.md#13-t02-nachbesserung-reread-preisdarstellung-und-titel-layout-14102026).

## T02 – Abschließende geschützte Integration und Datenerhalt (15.10.2026)

**Historischer Zwischenstand:** Dieser Abschnitt beschreibt den ersten Lauf mit
gesperrtem manuellem JAR. Der unten dokumentierte abschließende Nachtrag bestätigt
inzwischen den erfolgreichen `clean test`; die früheren Ergebnisse bleiben erhalten.

Ausdrückliche Eigentümerfreigabe für die vorhandenen automatisierten Testabläufe
ausschließlich auf `mangalib_test`. Branch `codex/t02-input-validation`, vorhandenes
Testpasswort ohne Wertausgabe und Abwesenheit externer Framework-/JVM-Overrides
geprüft. Alle DB-Kontexte verwenden ausschließlich `test`; unveränderter Guard
erzwingt vor Initialisierung und jeder Verbindung feste URL/Benutzer, keine
alternativen DataSources/JPA-/JNDI-/Pool-Overrides sowie `ddl-auto=validate`,
`generate-ddl=false`, SQL-Initialisierung `never`, Flyway/Liquibase deaktiviert.
Live-Scraper bleiben per Tag ausgeschlossen; Kontexttests verwenden Scraper-Mocks.
Keine zusätzlichen Zugriffswege oder Test-/Produktionscodeänderungen.

Vor jedem Gradle-Lauf wurde derselbe Aufruf mit `--dry-run` erfolgreich geprüft:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
.\gradlew.bat --offline --no-daemon clean test
.\gradlew.bat --offline --no-daemon test
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
pnpm run test:dom
```

| Tatsächlich ausgeführte Prüfung | Ergebnis |
|---|---|
| Guard vor erstem DB-Zugriff | 44 bestanden, 0 Fehler/Skipped |
| Lesender Vorher-Fingerabdruck | 1 bestanden |
| `clean test` | **Buildschritt `clean` fehlgeschlagen**, noch keine Tests gestartet: laufendes `build/libs/mangalib-manual-test.jar` unter Windows gesperrt |
| Vollständiges `test` ohne erneutes `clean` | **382 bestanden, 0 Fehler, 0 Skipped**; alle fünf Kompilierungs-/Ressourcen-/Testaufgaben ausgeführt |
| Gesonderter lesender Nachher-Fingerabdruck | 1 bestanden; identisch zum Vorher-Zustand |
| DOM-Suite | **164 bestanden, 0 Fehler/Skipped/Cancelled** |

Die 382 Fälle umfassen 308 DB-freie Fälle und 74 geschützte Kontext-/Integrationsfälle,
darunter unverändert 29 MVC-/JSON-Verträge. Die 44 Guard-Fälle sind in 382 enthalten;
separate Läufe sind keine zusätzlichen unterschiedlichen Testfälle. Externe
Selenium-Tests bleiben bewusst von der Suite ausgeschlossen, keine unerwarteten
Skipped-Fälle. `manualSafetyTest` wurde hier nicht ausgeführt; seine zuvor geprüften
32 ausgewählten Template-/Mock-Fälle bleiben ein historischer separater Nachweis.

Der erste pnpm-Aufruf scheiterte vor Testbeginn an einer Sandbox-`realpath`-Sperre;
derselbe vorhandene Befehl wurde mit freigegebenem Dateizugriff erfolgreich ausgeführt.
Keine Installation und kein Download. Kompilierung meldete bestehende veraltete APIs;
die bekannte HotSpot-CDS-Warnung bleibt. Keine Assertions abgeschwächt oder Tests geändert.

**Vorher und nachher identischer SHA-256-Fingerabdruck:**

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

Erfasst durch `DatabaseIntegrityBaselineTest` / `TestDatabaseSnapshot` über den
geschützten JPA-Zugriff: alle Werte der zehn Tabellen, Tabellen/Engine/Collation,
75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel und 26 Key-Column-Einträge.
78 Stammdaten unverändert (62 Verlage, 4 Status, 4 Typen, 3 Formate, 5 Sammelbände).
Alle fünf Geschäftstabellen vor/nach leer; keine dauerhaft verbliebenen Fixtures.
Transaktionale Fixtures und bestehende Nach-Rollback-Assertions erfolgreich;
AUTO_INCREMENT-Zähler bewusst ausgenommen und nicht zurückgesetzt.

Vollständiger Testbericht vor dem separaten Abschlusslauf lokal gesichert unter
`.gradle/t02-final-20261015/full-test-report/index.html`, XMLs unter
`full-test-results`, Fingerabdrücke in `before.xml` und `after.xml` desselben
ignorierten Verzeichnisses. Der reguläre aktuelle `build/reports/tests/test`-Bericht
zeigt nach dem letzten Lauf nur den einen abschließenden Baseline-Test.

Keine Verbindungen zur produktiven/manuellen Datenbank, keine direkten SQL-/JDBC-
Werkzeuge, Migrationen, Schemaänderungen, Seeding oder Datenbereinigungen. Die
manuelle Anwendung wurde weder gestartet noch gestoppt; ihr gesperrtes JAR blieb
erhalten. Damals musste `clean test` noch in einem ausdrücklich freigegebenen
Wartungsfenster ohne Artefaktsperre bestätigt werden; dies ist inzwischen erfolgt
(siehe abschließenden Nachtrag). Im damaligen Lauf kein automatischer Stop/Rerun.

Der Projekteigentümer bestätigt die Browsernachprüfung: Reread 3 nach Anlage und
Neuladen erhalten, deutsche Einkaufspreise, Titelumbruch im schmalen Fenster und
sichtbare/bedienbare Buttons. Keine Codex-Browserausführung in diesem Auftrag.
Altbestandsverträglichkeit, Requestgrößen-/Scraper-/SSRF-Grenzen bleiben T02-Folgearbeit;
M06/T03/T06 wurden nicht vorgezogen. Historische Ergebnisse bleiben unverändert.

### Abschließender Nachtrag: Clean-Test und Code-Review (15.10.2026)

Nach eindeutiger Prozess-/Artefakt-/Listenerprüfung wurde die manuelle Instanz
**PID 106732 kontrolliert per Ctrl+C in ihrer ursprünglichen Startsession beendet**.
Anschließend waren Prozess und manuelle Instanzen beendet, Port **18080 frei** und
das JAR nicht mehr gesperrt. Kein erzwungener Abbruch und kein erneuter Anwendungsstart.

Branch, Passwortverfügbarkeit ohne Wertausgabe und fehlende Framework-/JVM-Overrides
erneut geprüft; unveränderte Guards erzwingen ausschließlich `test`,
`localhost:3306/mangalib_test` und `mangalib_test_user`. Vor jedem Gradle-Lauf den
identischen Taskplan mit `--dry-run` geprüft. Danach tatsächlich ausgeführt:

| Prüfung | Bestanden | Fehler | Übersprungen |
|---|---:|---:|---:|
| Datenbankfreier `TestDatabaseGuardTest` vor DB-Zugriff | 44 | 0 | 0 |
| Lesender Vorher-Fingerabdruck | 1 | 0 | 0 |
| `.\gradlew.bat --offline --no-daemon clean test`, ohne Testfilter | **382** | **0** | **0** |
| Gesonderter lesender Nachher-Fingerabdruck | 1 | 0 | 0 |

Alle **29 MVC-/JSON-Vertragstests** bestanden. Die 44 Guard-Fälle sind auch in den
382 enthalten; separate Prüfläufe erhöhen nicht die Zahl unterschiedlicher Fälle.
Selenium-/Live-Scraper-Tests bleiben ausgeschlossen, keine unerwarteten Skipped-Fälle.
Die **164 DOM-Tests** waren bereits im vorherigen Prüflauf erfolgreich; sie wurden
für den Clean-Abschluss nicht erneut ausgeführt. Bestehende Deprecation-/CDS-Hinweise
unverändert, keine Assertions abgeschwächt oder Schutzmechanismen geändert.

Vorher und nachher identisch:
`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`.
Zehn Tabellen und 78 Stammdatensätze unverändert; Geschäftstabellen weiterhin leer,
keine verbliebenen Fixtures. Auch 75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel
und 26 Key-Column-Einträge unverändert. AUTO_INCREMENT ausgenommen und nicht zurückgesetzt.
Bericht: `.gradle/t02-clean-final-20261015/clean-test-report/index.html`, XMLs in
`clean-test-results`, Fingerabdrücke in `before.xml`/`after.xml` desselben ignorierten
Verzeichnisses. Keine Verbindung zur produktiven oder manuellen Datenbank im Testlauf.

Code-Review gegenüber `main` einschließlich aller unversionierten Dateien ohne neue
blockierende Befunde. Der bisherige T02-Implementierungsumfang ist technisch
commitfähig; keine Aussage über einen vollständigen T02-Abschluss oder produktive
Freigabe. Altbestandsverträglichkeit sowie SSRF-/Scraper-/Requestgrößengrenzen bleiben
offen. T03, M06 und T06 bleiben eigenständige Folgearbeiten. Keine Migrationen,
Schemaänderungen, Bereinigungen, echten Scraper, Commits, Pushes oder Merges erfolgt.
