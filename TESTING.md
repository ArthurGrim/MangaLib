# Sichere Tests für MangaLib

> Aktueller T02-Abschlussstand (02.11.2026): [T02_CLOSEOUT.md](T02_CLOSEOUT.md). Technisch abgeschlossen für das lokale Einzelbenutzermodell. Dependency-Korrektur, Regression und negative UI-Abnahmen erfolgreich; 415 durch den Projekteigentümer bestätigt. Historische Nachträge und Nachweisgrenzen bleiben erhalten.


## T02 – Altbestands-Charakterisierung, ausschließlich datenbankfrei (15.10.2026)

Branch `codex/t02-security-hardening`, nach M06-Merge. **Keine Produktionskorrektur**:
36 neue Java-Fälle in `validation/LegacyEditCharacterizationTest` und 21 neue
jsdom-Fälle in `legacy-edit-characterization.test.cjs` dokumentieren den Ist-Zustand.
Grüne Charakterisierung bedeutet bei einem Fehler ausdrücklich **reproduziert**,
nicht fachlich abgenommen. Die folgenden SOLL-Assertions sind dokumentierte Ziele
für einen gesonderten Korrekturauftrag; keine absichtlich roten oder deaktivierten
Tests im regulären Lauf. Bestehende Assertions/Guards/M06-Verträge unverändert.

### Getestete Pfade und Bewertung

| Fall | Reproduzierter Ist-Zustand | Bewertung / dokumentierte SOLL-Assertion |
|---|---|---|
| Band-Reread NULL/0/3: echter GET → Formular-Payload → echter Controller/Service → Repository-Mock | NULL wird als 0 ausgegeben und mit 0 gespeichert; dabei wird Reihen-Reread neu aggregiert. 0/3 bleiben erhalten. | Abweichung für NULL: ohne bewusste Reread-Änderung `assertNull(stored.reread)` und unveränderten Reihen-Reread fordern; bekannte Werte bereits erfüllt. |
| Band-Spezialflag NULL/false/true | GET liefert für NULL false; Speichern schreibt false. Bekannte Flags bleiben erhalten. | Abweichung für NULL: unberührtes NULL erhalten, bewusst gesetzte Boolean-Werte weiterhin ermöglichen. |
| Reihenflags NULL/false/true | Echter Speicherpfad und Dialogskript erhalten beide Flags; vorhandene DOM-Fälle prüfen auch bewusstes Ein-/Zurückschalten. | Gewünschtes Verhalten bereits erfüllt, nicht erneut umbauen. |
| Normale Titeländerung ohne Erweiterung | Reihen-Reread NULL wird 0; alter Band-Reread NULL wird 0 bzw. auf vorhandenen Reihen-Reread angehoben. Bei zwei teilweise gelesenen Bänden wird das mitgesendete false der Reihe auf den gelesenen Band übertragen. | Abweichung bei beiläufiger Bearbeitung; bewussten Reihen-Massenauftrag davon fachlich abgrenzen. Keine neue Reread-/Lesehistorienregel einführen. |
| Unveränderte alte Reihen-/Band-URLs | Relative/file-URL scheitert schon im Dialog; Userinfo-URL passiert dessen Regex, scheitert aber serverseitig mit 400 vor Repositoryarbeit. | Abweichung: fremde Feldänderung soll Altwert erhalten können; neue ungültige URL weiterhin abweisen. Server muss Änderung gegen gespeicherten Wert prüfen. |
| Historische Bild-URL beim Öffnen des Banddialogs | Tatsächliches Skript weist file-/Userinfo-URL ungeprüft dem Bild-src zu. | Sicherheitsabweichung an der DOM-Zuweisung. SOLL: sicherer lokaler Platzhalter statt ungeprüftem Abrufziel, gespeicherten Wert nicht verändern. **Kein realer Datei-/Netzwerkabruf nachgewiesen**. |
| Unberührte/leere/neue optionale URL | Reihenformular unterscheidet NULL/Leertext und gültige Werte; explizites Leeren/Ersetzen wird im Reihenservice übernommen. Bandservice erhält bei Leertext dagegen die alte Bild-URL. | Erhalt/Ersetzen erfüllt; beabsichtigte Löschsemantik bei Bänden noch festzulegen, nicht still ändern. |
| Bandpreis NULL/fehlend | Einzel-GET funktioniert; Bandliste wirft bei NULL-Preis oder NULL-Anpassung NPE. Dialog zeigt bei NULL/fehlendem Preis NaN und blockiert Speichern. | Abweichung: kontrollierte Darstellung/Behandlung ohne erfundenen 0-Euro-Preis. Fachliche Semantik von unbekanntem Preis separat entscheiden. |
| NULL-Preisanpassung im Dialog | String `NaN` wird gesendet, serverseitige bestehende Preisvalidierung weist ihn ab. | Abweichung im Roundtrip; unberührtes NULL erhalten, kein NaN senden. |
| Vorhandene Preise 0.00/-0.02/6.99, negative Anpassung | Bei anderer Bandänderung erhalten; Leeren des optionalen Preises bedeutet im Service bisher unverändert. | Erhalt bereits erfüllt; kein neues Vorzeichen-/Rundungs-/Preisverteilungskonzept. Löschsemantik offen. |
| Fehlende Details/Sammelbandbeziehung | Reihen-GET wirft NPE. Normales Edit mit zuvor gültigem Payload versucht bei fehlenden Details neue Defaultdetails zu speichern. | Abweichung: vor Mutation kontrolliert behandeln, keine automatischen Ersatzdetails. Mock-Speicherversuch ist kein JPA-Commitnachweis. |
| Fehlende Status-/Verlag-/Typ-/Formatbeziehung | GET zeigt fehlende ID; Formular blockiert fehlenden Verlag. Bei mitgesendeter gültiger ID scheitert Service an alter NULL-Beziehung, Controller antwortet neutral 500. | Informationsschutz erfüllt, kontrollierte Bestandsbehandlung nicht erfüllt. Reparatur/Neuzuordnung braucht gesonderte Entscheidung. |

M06-Abgrenzung: unveränderte 40 `ReadingStatusExtensionTest`-Fälle mitgeprüft.
Die neue Charakterisierung normaler Bearbeitung ohne zusätzliche Bände ändert
weder M06-Erweiterungsregeln noch Kaufpfade. T03, T04 und S03 werden durch diese
Nachweise nicht implementiert. Primitive Lesestatusfelder können in Java kein
SQL-NULL darstellen: keine Aussage über das Laden entsprechender Datenbankzeilen.

### Ausführung, Reproduktion und Grenzen

Ausgewählt: alle fünf Klassen unter `de.mangalib.validation` (einschließlich der
neuen Charakterisierung), `TestDatabaseGuardTest` und `ReadingStatusExtensionTest`.
Kein Boot-/JPA-Kontext. Echte Host-/Origin-/Session-CSRF-Filter in Standalone-MockMvc,
Repositories ausschließlich Mockito, Scraper fail-fast ersetzt. jsdom verwendet
echte Templates/Dialogskripte, Fetch-Doubles und gesperrte externe Ressourcen.
Der bestehende Inline-Save-Handler des Banddialogs wird im Test explizit angebunden,
weil `runScripts: outside-only` Inline-Skripte absichtlich nicht ausführt.

Vor jedem Gradle-Testlauf den identischen Aufruf mit `--dry-run` geprüft: nur
Kompilierung/Ressourcen/Test, kein manueller Launcher oder anderer Testtask.

```powershell
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.validation.*' --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.service.ReadingStatusExtensionTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.validation.*' --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.service.ReadingStatusExtensionTest
node --test src/test/js/legacy-edit-characterization.test.cjs src/test/js/manual-acceptance.test.cjs
```

Abschließender Prüfstand: **323 Java-Fälle**, davon 36 neu, 44 Guard und 40 M06;
**45 DOM-Fälle**, davon 21 neu; jeweils 0 Fehler und 0 übersprungen. Die ersten
Testentwürfe wurden korrigiert: maskierter CSRF-Token, explizites Inline-Handler-
Binding, syntaktische URL-Darstellung und tatsächliches Zwei-Nachkommastellen-
Formularmapping. Keine Produktionsänderung oder abgeschwächte Schutzprüfung.

Kein vollständiger HTTP-Server-Fehlerdispatch in Standalone-MockMvc: bei den
GET-NPEs wird die ServletException samt Ursache geprüft, **kein tatsächlich
beobachteter HTTP-500-Response behauptet**. Mocks beweisen keine Persistenz,
Transaktionsatomarität oder Rollbacks. Ein tatsächlicher Browserabruf unsicherer
Bild-URLs wird ausdrücklich nicht ausgelöst. Reale Bestandsverteilung unbekannt.

Keine Datenbankverbindung, SQL-Ausführung, Anwendung, HTTP-Listener, echter Browser,
Scraper, Migration, Installation oder neue Dependency. Keine Vollsuite/Integration;
die historischen 456 erfolgreichen Java-Fälle und die M06-Browserabnahme bleiben
unverändert historische Nachweise. Weitere Prüfungen mit Datenbank oder Browser
benötigen eine neue ausdrückliche Freigabe. Keine Commits/Pushes/Merges/Staging.

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

## M06 – Datenbankfreier Implementierungszwischenstand (15.10.2026)

**Historischer Stand vor der Integrationsfreigabe:** Die folgenden Ergebnisse und
damals offenen Integrationsprüfungen bleiben erhalten. Der anschließende Abschnitt
„M06 – Geschützte Integration und Datenerhaltung“ dokumentiert die inzwischen
ausgeführten DB-Prüfungen; die manuelle Browserabnahme ist weiterhin offen.

Branch `codex/m06-reading-status`, Ausgangsbasis `a6f5e3c`. Keine neue DB-Freigabe.
Erweiterungen überspringen die pauschale Reihenstatus-/Reread-Verteilung und die
Importaktualisierung alter Bände. Nur zusätzliche Bände werden erzeugt und explizit
ungelesen initialisiert. Anschließend verwendet der Reihenservice dieselbe lesende
Bandstatus-Aggregation wie die Einzelbandbearbeitung: alle gelesen → Reihe gelesen,
mindestens ein ungelesener Band → Reihe nicht vollständig gelesen. Vorhandene
Reihen- und Band-Rereads bleiben beim Ergänzen einschließlich null unverändert.

Vor einer Mutation wird die Erweiterbarkeit geprüft: eindeutige positive Band-IDs,
passende Beziehungen und lückenlose eindeutige Nummern 1..N. Bei Ziel Z>N dürfen
N+1..Z angehängt werden, auch wenn die bisher deklarierte Anzahl abweicht (beispielsweise
deklarierte Anzahl 5, tatsächlich 2 Bände, gewünschter neuer Bestand 4). Die neue
deklarierte Anzahl entspricht dem angeforderten Ziel; Altbände werden nicht repariert,
umnummeriert, überschrieben oder gelöscht. Die Repositoryreihenfolge ist unerheblich.
Lücken, doppelte/fehlende Nummern/IDs und fremde Beziehungen werden vor Änderungen
abgewiesen. Eine deklarierte Erhöhung, deren Ziel keinen neuen Band erfordert,
wird ebenfalls abgewiesen. Fehlende Details und widersprüchliche Details-Zuordnung
sind im Erweiterungspfad gesperrt; beide Kaufzuordnungen prüfen fehlende Details
bereits vor der Dereferenzierung und liefern kontrolliert 400 ohne Kaufmarkierung.

Gleichzeitige Änderungen von Titel, Reihen-URLs und anderen ausdrücklich bearbeiteten
Reihenmetadaten bleiben möglich. Beim Ergänzen wird auch aus abweichend mitgesendeten
Lesestatus-/Reread-Werten kein Auftrag zur Änderung historischer Bandwerte abgeleitet.
Diese Änderungen müssen separat über die bestehenden Bearbeitungsfunktionen erfolgen.
Importwerte für schon vorhandene Bände werden beim Anhängen nicht übernommen.
Ohne tatsächliche Erweiterung bleibt das bisherige Bearbeitungsverhalten erhalten.
Neuanlage bleibt unverändert: normal ungelesen, ausdrücklich gewähltes Gelesen bei
vollständiger Reihenneuanlage weiterhin wirksam; Kaufneuanlage beginnt ungelesen.
Keine neuen Requestfelder, UI-Felder, Datenstrukturen, Spalten oder Migrationen.

### Tatsächlich ausgeführte Prüfung

Vor jedem Gradle-Testlauf wurde derselbe Aufruf mit `--dry-run` geprüft. Abschließender
Aufruf (zunächst mit zusätzlichem `--dry-run`, danach ohne):

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.service.ReadingStatusExtensionTest --tests 'de.mangalib.validation.*' --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest manualSafetyTest --tests de.mangalibmanual.SeriesEditRegressionTest -x manualBootJar
```

**348 Fälle im ausgewählten `test`-Task bestanden**: 40 neue M06-Fälle plus 308
bestehende Unit-/Standalone-MockMvc-/Guard-/Policy-Fälle (einschließlich 44 Guard
und 61 Policy). **17 bestehende SeriesEditRegressionTest-Fälle bestanden** im
ausgewählten `manualSafetyTest`; insgesamt 365 Fälle, 0 Fehler/Errors/Skipped.
Ein vorheriger engerer datenbankfreier Lauf bestand ebenfalls; Wiederholungen
sind keine zusätzlichen unterschiedlichen Testfälle. Bekannte Deprecation-/CDS-
Hinweise bleiben. Keine Assertions abgeschwächt. Keine zusätzlichen Dependencies.

`ReadingStatusExtensionTest` verwendet echte Main-/Einkaufscontroller sowie echte
Reihen-, Band-, Details- und Einkaufsservices mit Repository-Mocks. Es startet
keinen Spring-Kontext, keine DataSource, keinen Listener und keinen Scraper.
Geprüft werden vollständige/teilweise/ungelesene sowie widersprüchliche Reihen,
mehrere neue Bände, beide Kaufzuordnungen, nullable Reihen-/Band-Rereads,
unveränderte Altbandwerte anhand ihrer IDs (inklusive Preise, Anpassungen, URLs,
Sonderflags, Zeitwerte und Beziehungen), gleichzeitige Reihenmetadatenänderung,
freigegebene Anzahlabweichungen, nebenwirkungsfreie Ablehnung unklarer Bestände,
fehlende Details sowie bestehende Bearbeitungs- und Neuanlage-/Kaufverträge
(Controllerantworten 200/400/417). Bestehende Standalone-MockMvc-Fälle prüfen die
unveränderten Sicherheitsfilter und Eingabeverträge; keine Filter deaktiviert.
`-x manualBootJar` vermeidet Paketierung und Artefaktstarts; nur die ausdrücklich
ausgewählte datenbankfreie manuelle Testklasse wurde ausgeführt.

### Noch nicht geprüft / bewusste Grenzen

Die Repository-Mock-Identitätsmap ist kein Persistenznachweis. Nach ausdrücklicher
Freigabe fehlen geschützte Integrationstests mit Flush/Clear und erneutem Laden,
Nach-Rollback-Vergleich, Daten-/Schemafingerabdrücken sowie die vollständige Suite
inklusive der 29 MVC-/JSON-Verträge. Die historischen 382 Volltests wurden nicht
ausgeführt. Es wurde keinerlei DB-Zugriff oder SQL ausgeführt; daher keine neue
Daten-/Schema-Unverändertheitsmessung behaupten. DOM-/Browsertests nicht ausgeführt;
Frontend/Requeststruktur unverändert. Historische 164 DOM-Fälle bleiben historisch.

Manuell später prüfen: teilweise/vollständig gelesene eigene synthetische Reihe
ergänzen, alte Einzelbandwerte und Reihen-Reread nach Reload vergleichen, neue
Bände ungelesen; beide Kaufzuordnungen, Titel-/Metadatenänderung und anschließende
Einzelbandbearbeitung. Ausschließlich nach neuer Start-/Schreibfreigabe.

Nicht vorgezogen: T03-Kaufatomarität/Parallelität/Idempotenz, allgemeine stabile
Bandidentifikation und Reduktion, Preis-/Restcent-Verteilung; S03-Rereaddefinition
und Lesehistorie; T04, T05, T06 sowie verbleibende T02-Scraper-/SSRF-Arbeiten.
SQL-NULL-Lesestatus und produktive Altbestandsverträglichkeit bleiben gesondert
zu prüfen. Keine automatische Reparatur; M06 bleibt bis zur Integration und
Browserabnahme ein Implementierungszwischenstand.

## M06 – Geschützte Integration und Datenerhaltung (15.10.2026)

Ausdrückliche Freigabe ausschließlich für die vorhandene isolierte automatisierte
Testumgebung. Branch `codex/m06-reading-status`, Prozesspasswort verfügbar ohne
Wertausgabe, keine externen Framework-/JVM-Overrides. Unveränderte TestContext-
Factory und GuardedTestDataSource prüfen vor Initialisierung und jeder Verbindung
ausschließlich Profil `test`, `localhost:3306/mangalib_test`, `mangalib_test_user`
und Passwort aus `MANGALIB_TEST_DB_PASSWORD`. DDL nur `validate`, SQL-Initialisierung
und Migrationen deaktiviert, keine alternativen DataSources/JPA-/Pool-/JNDI-Wege.

### Neue echte Persistenzprüfung

`ReadingStatusIntegrationTest` verwendet `@SpringBootTest`, `@ActiveProfiles("test")`,
`@Transactional`, `@Rollback` und MockMvc mit den bestehenden Sicherheitsfiltern.
Kein HTTP-Listener. Ausschließlich neu erzeugte Fixtures aus dem bestehenden
`PersistenceTestFixtures`-Weg (inklusive DB-/Benutzer-/InnoDB-Prüfung vor INSERT);
Stammdaten werden nur referenziert. Keine eigene JDBC-Verbindung, kein `@Commit`,
`REQUIRES_NEW`, asynchroner Schreibweg, Cleanup, Seeding oder Constraint-Eingriff.
Beide Live-Scraper werden statisch durch fail-fast Mocks gesperrt und auf fehlende
Aufrufe geprüft. Testtransaktion und Rollback-Markierung bleiben bis zum Ende aktiv.

Nach fachlichen Schreibaktionen ausdrücklich `flush()`, `clear()` und neue
Repositoryabfragen. Altbandwerte werden in skalaren Snapshots anhand persistierter
IDs verglichen: Nummer, externer Index, Reihen-FK, Lesestatus, nullable Reread,
Preis/Anpassung, URLs als Strings ohne URL.equals/DNS, Sonderflag und Zeitwerte.
Anzahl/Nummern/Beziehungen neuer Bände sowie Reihenaggregate werden separat geprüft.

| Neue Integrationsgruppe | Fälle |
|---|---:|
| Direkte Erweiterung: vollständig/teilweise/ungelesen, beide widersprüchlichen Reihenflags, gleichzeitige Reihenmetadaten | 5 |
| Band-Rereads null/0/2/5 bei Reihen-Reread null/0/3/10000 nach Reload | 4 |
| Kauf über Index und Titel, jeweils vollständig/teilweise/ungelesen, Kaufmarkierung | 6 |
| Deklarierte Anzahl 1/3/5 bei zwei tatsächlichen Bänden, Erweiterung auf vier | 3 |
| Lücken, doppelte Nummern, keine tatsächliche Ergänzung, fehlende Details; jeweils direkt und Kauf, identischer Zustand nach Ablehnung | 8 |
| Zweite Reihe mit gültigen FKs bleibt unverändert und beeinflusst Aggregation nicht | 1 |
| Einzelbandstatusänderung und unveränderter Nachbarband nach Reload | 1 |
| Normale Reihenbearbeitung ohne Erweiterung, beide Lesestatuswerte | 2 |
| Reihenneuanlage mit Reread 0/3/10000, einschließlich T02-Fix | 3 |
| Kauf als neue Reihe mit unverändertem 417-/200-Vertrag und ungelesenem Default | 1 |
| **Summe** | **34** |

Doppelte Bandnummern und fehlende Details waren im bestehenden Schema ohne
Constraint-Abschaltung darstellbar. Fehlende Details wurden durch eine eigene neue
Reihe ohne Details angelegt, nicht durch Löschen vorhandener Daten. Die abgewiesenen
Aktionen vergleichen jeweils die vollständigen Daten-/Schemasnapshots vor/nach
dem Aufruf und prüfen fehlende Kaufmarkierung. Eine fremde Beziehung innerhalb
eines per Reihen-FK geladenen Bestands lässt sich mit korrektem Mapping nicht
künstlich herstellen; die Integration prüft stattdessen die Isolation einer zweiten
gültigen Reihe. Widersprüchliche Objektbeziehungen/IDs bleiben zusätzlich durch
die datenbankfreien M06-Fälle abgesichert. SQL-NULL-Lesestatus wurde nicht erzwungen.

`@BeforeTransaction` erfasst vor jedem Fall sämtliche bestehenden Tabellenwerte
und Schema-Metadaten, `@AfterTransaction` vergleicht nach tatsächlichem Rollback
erneut denselben Snapshot. Alle 34 Nach-Rollback-Prüfungen bestanden, auch im Volltest.
Dies ergänzt den globalen Vorher-/Nachher-Vergleich; kein automatisches DELETE-Cleanup.

### Ausgeführte Stufen und genaue Befehle

Vor **jedem** nachstehenden Gradle-Aufruf wurde derselbe Aufruf mit zusätzlichem
`--dry-run` erfolgreich geprüft. Reihenfolge:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.ReadingStatusIntegrationTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.MvcContractTest --tests de.mangalib.LocalSecurityMvcTest --tests de.mangalib.ServiceTest --tests de.mangalib.PersistenceCompatibilityTest --tests de.mangalib.RepositoryCompatibilityTest --tests de.mangalib.TestDataPreservationTest --tests de.mangalib.TemplateRenderingTest --tests de.mangalib.MangalibApplicationTests --tests de.mangalib.DemoApplicationTests
.\gradlew.bat --offline --no-daemon clean test
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
```

| Stufe | Bestanden | Fehler/Errors | Skipped |
|---|---:|---:|---:|
| Guard vor erstem DB-Zugriff | 44 | 0 | 0 |
| Lesender Ausgangsfingerabdruck | 1 | 0 | 0 |
| Neue M06-Integration gezielt | 34 | 0 | 0 |
| Bestehende geschützte Regressionen | 73 | 0 | 0 |
| Vollständiger ungefilterter `clean test` | **456** | **0** | **0** |
| Gesonderter lesender Abschlussfingerabdruck | 1 | 0 | 0 |

456 unterschiedliche Fälle = historische 382 + 40 M06-Unitfälle + 34 neue
Integrationsfälle. Wiederholte gezielte Läufe sind keine zusätzlichen Fälle.
Alle **29 MVC-/JSON-Vertragsfälle** bestanden in der gezielten Stufe und im Volltest,
keine Assertions abgeschwächt. Die Live-Scraper bleiben durch bestehende Tags/
Deaktivierung ausgeschlossen, keine unerwarteten Skipped-Fälle. Alle Stufen waren
im ersten Lauf erfolgreich; keine Anwendungscodekorrektur in diesem Integrationsauftrag.
Bekannte Hinweise zu veralteten APIs und HotSpot-CDS bleiben. Keine neuen Dependencies.

### Daten-/Schema-Nachweis und verbleibende Abnahme

Unmittelbarer Ausgangs- und Abschlussfingerabdruck identisch (auch gleich dem
historischen Referenzwert, ohne Herstellung eines erwarteten Zustands):

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

Zehn Tabellen unverändert; 78 Stammdaten (62 Verlage, 4 Status, 4 Typen, 3 Formate,
5 Sammelbände). Alle fünf Geschäftstabellen vor/nach leer, keine verbliebenen Fixtures.
Schema: 75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel und 26 Key-Column-Einträge
unverändert. AUTO_INCREMENT bewusst ausgenommen und nicht zurückgesetzt.
Lokale ignorierte Nachweise unter `.gradle/m06-integration-20261015/`:
`before.xml`, `after.xml`, `targeted-integration.xml`, `regression-results`,
`clean-test-results`, `clean-test-counts.json` und `clean-test-report/index.html`.
Der reguläre `build/reports/tests/test`-Bericht zeigt nach dem letzten separaten
Lauf nur den Abschluss-Baseline-Test; der vollständige Clean-Bericht wurde vorher gesichert.

Kein Zugriff auf `mangalib` oder `mangalib_manual_test`, keine direkte SQL-/JDBC-
Administration, Schemaänderung, Migration, Bereinigung oder Initialisierung. Kein
manuelles JAR im Buildverzeichnis vorhanden; kein Anwendungsprozess gestoppt oder
gestartet, kein Browser, HTTP-Listener oder echter Scraper. Kein `manualSafetyTest`, DOM-Lauf oder
`assemble` in diesem Auftrag. Die früheren 17 manuellen Mock-Fälle und 164 DOM-Fälle
bleiben historische separate Nachweise.

**Technisch bereit für eine gesondert freizugebende isolierte manuelle Browserabnahme,
M06 noch nicht vollständig abgeschlossen.** Dort direkte Erweiterung und Kauf über
beide Zuordnungen, individuelle alte Lesestatus/Rereads nach Reload, ungelesene neue
Bände, Reihenaggregation und gleichzeitige Metadatenänderung prüfen. Keine manuelle
Start-/Schreibfreigabe aus diesem Integrationsauftrag ableiten.
T03-Kaufatomarität/Parallelität/Idempotenz, allgemeine Bandidentifikation/Reduktion
und Preis-/Restcentregeln bleiben offen; ebenso S03-Lesehistorie, produktive
Altbestandsverträglichkeit, SQL-NULL-Lesestatus und andere abgegrenzte Roadmap-Themen.

### M06 – Manuelle Vorbereitung durchgeführt, Browserabnahme blockiert (15.10.2026)

Dieser Folgeauftrag wiederholte keine Java-/DOM-Suite und führte keine Verbindung
zu `mangalib_test` aus. Die oben dokumentierten 456 erfolgreichen Java-Fälle und
29 MVC-/JSON-Verträge bleiben der technische Nachweis; die Browserabnahme ist offen.

Vor dem Build den identischen Aufgabenplan geprüft:

```powershell
.\gradlew.bat --offline --no-daemon assembleManualTest --dry-run
.\gradlew.bat --offline --no-daemon assembleManualTest
.\scripts\Start-ManualTest.ps1 -ValidateOnly -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
.\scripts\Start-ManualTest.ps1 -JavaPath 'C:/Program Files/Java/jdk-21/bin/java.exe'
```

Build und ValidateOnly erfolgreich. Die drei betroffenen M06-Klassen im JAR sind
bytegleich zum aktuellen Kompilat (SHA-256-Vergleich). Bestehende Artefaktprüfung
bestätigt manuellen Launcher/Double und schließt Standardstartkonfiguration,
Live-Scraper, Selenium und DevTools aus. Passwortvariable verfügbar und nicht leer,
keine unsicheren Framework-/JVM-Overrides; keine Secretwerte ausgegeben/gespeichert.
Eigene PID 1632 und alleiniger Listener `127.0.0.1:18080` eindeutig bestätigt,
Startlog ausschließlich Profil `manual-test` und manuelle Datenbank.

Kein steuerbarer DOM-Browser-Connector vorhanden. Das vorhandene Computer-Use-
Werkzeug initialisierte sich und listete Firefox-Fenster, brach beim Fensterzugriff
jedoch mit **`Computer Use app approval timed out`** ab. Keine Browsernavigation,
keine Formularbedienung, keine direkten HTTP-Schreibrequests und keine SQL-Schreib-
abkürzung. Sichtbares Testbanner und fachliche Browserfälle deshalb nicht bestätigt.
Das ist eine Werkzeugblockade, kein nachgewiesener M06-Anwendungsfehler.

Die ausdrücklich freigegebenen rein lesenden manuellen Snapshots wurden über den
bereits installierten MySQL-Client mit festen manuellen Zugangsdaten, ohne Options-
dateien und mit abwesender Login-Datei ausgeführt. Passwort nur im Kindprozess-
Environment aus `MANGALIB_MANUAL_DB_PASSWORD`, nie in Argumenten/Dateien. Fester Host,
Port, Benutzer und Datenbank; Identität im Ergebnis geprüft. SQL nur SELECTs in
einer `READ ONLY`-Transaktion, abschließend ROLLBACK; kein DML/DDL oder Export/Import.
Der Anwendungsstartweg und seine Guards wurden nicht verändert.

Alle 15 Snapshotabschnitte (zehn Tabellen plus fünf Metadatengruppen) identisch.
Sechs Reihen, zehn Bände, drei Einkaufspositionen sowie sämtliche Details und
17 manuelle Stammdatensätze unverändert. Schema: zehn Tabellen, 75 Spalten,
24 Indexeinträge, zwölf Fremdschlüssel und 26 Key-Column-Einträge. AUTO_INCREMENT
ausgenommen. SHA-256 der manuellen JSON-Snapshots vor/nach identisch:
`ce4a6db574a05f4d14052e3a189e6a616da9d627102bf28e7c6ede32ab96ebf1`.
Dieses manuelle JSON-Prüfformat ist nicht mit dem JUnit-Fingerabdruck gleichzusetzen.
Lokale ignorierte Nachweise: `.gradle/m06-browser-20261015/before.json`, `after.json`
und das ausschließlich lesende Hilfsskript `ReadManualSnapshot.ps1` (ohne Secrets).

Eigene Startsession kontrolliert per Ctrl+C beendet; PID 1632 nicht mehr vorhanden,
Port 18080 frei. Null neue Reihen/Bände/Einkaufspositionen, keine Bereinigung oder
Änderung vorhandener Daten. Keine Produktiv-/automatisierten DB-Zugriffe, echten
Scraper, externen Navigationen, neuen Abhängigkeiten oder Git-Abschlussaktionen.
Fortsetzung der sieben Browsergruppen erst mit funktionierender UI-Appfreigabe;
keine vollständige M06-Abnahme behaupten. Siehe MANUAL_TESTING.md, Abschnitt 16.

### M06 – Sieben UI-Szenarien erfolgreich abgenommen (15.10.2026)

Die vorangehende Werkzeugblockade ist historisch. Mit dem vorhandenen Codex
In-app Browser wurden nun sämtliche sieben freigegebenen Szenarien A–G über
reguläre Formulare/Kaufbuttons erfolgreich ausgeführt und nach vollständigem
Neuladen geprüft: vollständige/teilweise gelesene Reihe erweitern, unterschiedliche
Rereads erhalten, Kaufzuordnung über Index/Titel, gleichzeitige Titeländerung und
nachträgliche Einzelbandmarkierung. **7 erfolgreich, 0 fachliche Fehler, 0 blockiert,
0 nicht durchgeführt. M06 technisch und manuell abgenommen, noch nicht gemergt.**

Keine Gradle-/JUnit-/DOM-Suite wiederholt; die vorhandenen 456 erfolgreichen Java-
Tests einschließlich 34 M06-Integrationsfällen und 29 MVC-/JSON-Verträgen bleiben
der gesonderte Automatennachweis. Kein neuer Build erforderlich: drei betroffene
M06-Klassen im vorhandenen manuellen JAR bytegleich zum aktuellen Kompilat.

Branch, Passwortverfügbarkeit und unveränderte Schutzkonfiguration geprüft;
`Start-ManualTest.ps1 -ValidateOnly` erfolgreich. Eigene Instanz PID 90716 nur mit
`manual-test`, `mangalib_manual_test_user` / `mangalib_manual_test` und einzigem
Listener `127.0.0.1:18080`. Testbanner im Browser bestätigt. Keine nativen Firefox-
Aktionen, Autofill-/Live-Scraperaufrufe, externen Navigationen, Ersatz-HTTP-
Schreibrequests oder SQL-Schreibbefehle. Browserkonsole ohne beobachtete Warnungen/
Fehler; kein vollständiger Netzwerk-Trace über die Schnittstelle verfügbar.

Neue synthetische Daten mit Präfix `M06_TEST_20261015_IAB_`: vier Reihen (7–10),
14 Bände (11–24), zwei Einkaufspositionen (5/6, beide gekauft), zugehörige Details.
B/C/F teilten eine Reihe; G folgte direkt auf A. Keine Bereinigung.
Lesende READ-ONLY-Snapshots vergleichen alle zuvor vorhandenen Zeilen unverändert:
sechs Reihen/Details, zehn Bände, drei Einkaufspositionen/Details (1/2/4),
17 Stammdatensätze und sämtliche Schemaabschnitte (zehn Tabellen, 75 Spalten,
24 Indexeinträge, zwölf FKs, 26 Key-Column-Einträge). AUTO_INCREMENT ausgenommen.
Die alten BCF-Bandzeilen 14–16 sind auch zum Zwischensnapshot exakt identisch.

Manuelle JSON-Hashes, durch neue Testdaten erwartungsgemäß unterschiedlich:
vorher `ce4a6db574a05f4d14052e3a189e6a616da9d627102bf28e7c6ede32ab96ebf1`,
nachher `edffee9430083f993a55a91865a48dc1804a61837e32f61597de4f1d9923adc7`.
Keine Verwechslung mit dem automatisierten JUnit-Datenbankfingerabdruck.
Lokale Nachweise in `.gradle/m06-iab-20261015/` (Git-ignoriert); vorherige Berichte
erhalten. Szenarientabelle, endgültige Zustände, Screenshot und UI-/SQL-NULL-
Nachweisgrenzen in MANUAL_TESTING.md, Abschnitt 17.

Eigene Startsession per Ctrl+C beendet; PID 90716 nicht mehr vorhanden, Port 18080
frei. Kein Zugriff auf `mangalib`/`mangalib_test`, keine Schemaänderung, Migration,
neue Abhängigkeit oder Anwendungscodeänderung. T03/S03 nicht vorgezogen; keine
Commits, Pushes oder Merges.

## T02 – Gezielte Altbestandskorrekturen, DB-freier Nachtrag (16.10.2026)

Dieser Nachtrag überführt die Charakterisierung vom 15.10.2026 in freigegebene
Soll-Regressionen. Deren historische Befunde und Testergebnisse oben bleiben
erhalten; sie beschreiben nicht den aktuellen Korrekturstand.

### Umgesetzte Semantik

- Normale Reihen-Metadatenänderungen verteilen Lesestatus und Reread nicht mehr
  beiläufig auf vorhandene Bände. Nach ausdrücklicher Eigentümerentscheidung gibt
  es zwei standardmäßig ausgeschaltete Optionen: `applyReadToVolumes` und
  `applyRereadToVolumes`. Direkte Reihenwertänderungen betreffen ohne Option nur
  die Reihe. Der bisherige Reread-Massenauftrag bleibt eine Mindestwertanwendung:
  höhere vorhandene Band-Rereads werden nicht abgesenkt. Optionen werden bei jedem
  Öffnen zurückgesetzt und explizit typgeprüft; gleichnamige Importfelder entfernt.
- Der Server vergleicht Werte mit dem geladenen Bestand. Insbesondere ist der
  bisherige GET-Wert 0 bei historischem Reihen-Reread NULL ohne bewusste Eingabe
  kein Normalisierungsauftrag. `rereadEdited` ermöglicht ausdrücklich NULL → 0.
  M06-Erweiterungen ignorieren Massenoptionen weiterhin und erhalten alte Bandwerte.
- Bestehende GET-Werte/Typen (`reread: 0`, `istSpecial: false` als bisherige Anzeige)
  bleiben erhalten; additive `rereadNull`-/`specialNull`-Felder machen die Rohsemantik
  für den Dialog erkennbar. Unbekannter Reread erscheint leer/„Unbekannt“, unbekanntes
  Band-Spezialflag als unbestimmte Checkbox. Unberührte Werte werden als NULL gesendet.
  Beim Band bedeutet explizites JSON-NULL für Reread nun „erhalten“; fehlender/leerer
  Legacy-Reread behält seine bisherige 0-Semantik. Bewusstes 0/false bleibt möglich.
- Historische URLs werden bei exakter Übereinstimmung mit dem serverseitigen Altwert
  erhalten, auch bei heute ungültiger Syntax. Der Controller benötigt dafür nur im
  ungültigen Fall einen lesenden Lookup; vor der Mutation prüft der echte Service
  erneut gegen seinen Bestand. Keine Clientbehauptung ersetzt diesen Vergleich.
  Neue abweichende URLs bleiben strikt validiert. Leere Band-URLs behalten den alten
  Wert; Reihen-URLs behalten ihre bisherige explizite Leertext-Semantik.
- Gemeinsame Bildzuweisung akzeptiert nur absolute HTTP(S)-URLs ohne Userinfo,
  Kontrollzeichen oder Backslashes sowie zwei feste lokale Platzhalter. Historische
  unsichere Werte bleiben im Eingabefeld, gelangen aber nicht in img.src. Keine DNS-
  Prüfung, URL-Normalisierung gespeicherter Daten oder umfassende SSRF-Abnahme.
- Unbekannte Bandpreise/-anpassungen erscheinen leer beziehungsweise als „Unbekannt“,
  niemals als NaN oder automatisch 0 €. Reine Metadatenänderungen erhalten diese
  Werte und vorhandene Summen. Finanzielle Änderungen bei weiterhin unvollständiger
  Berechnungsbasis werden vor Mutation kontrolliert abgewiesen; das gilt auch für
  Preisimporte/Erweiterungen mit unbekannten Altpreisen. Keine neue Summen-/NULL-
  Preisregel eingeführt. Numerisch gleiche Werte wie 0 und 0.00 lösen keine unnötige
  Neuberechnung aus; gespeicherte Scale und bestehende Rundung bleiben unangetastet.
  Bei gültiger finanzieller Änderung/Preisimport greifen die bisherigen Berechnungen.
- Fehlende Details, Sammelband-/Stammdatenbeziehungen oder widersprüchliche Details-
  Zuordnung werden vor Mutation abgewiesen; keine Ersatzdetails. Der Reihen-Leseweg
  liefert einen neutralen 400-Fehler statt NPE. Dialog-Ladefehler zeigen eine feste
  Meldung und sperren Speichern bis zum Neuladen, ohne Rohfehler oder automatische
  Wiederholung. Schema, Entities und DB-Guards unverändert.

### Tests und Nachweisgrenzen

Die 36 Java- und 21 DOM-Charakterisierungen wurden gezielt auf Soll-Verhalten
überführt und ergänzt: nun **51 Java-Fälle** und **34 DOM-Fälle** in den beiden
Legacy-Dateien. Zusätzliche Fälle prüfen explizite Massenoptionen auch bei identischem
Reihenwert, gefälschte Importoptionen, NULL → 0/false, erhaltene unbekannte Preise
und Summen, Ablehnung vor Mutation, URL-Altwertvergleich, sichere Bildsinks,
neutrale Ladefehler und weiter funktionierende Preisimporte.

Abschließend **338 Java-Tests bestanden, 0 Fehler, 0 übersprungen**:
51 Legacy-, 98 bestehende MockMvc-Validierungs-, 38 Service-Safety-, 61 Validator-,
6 Reihenanlage-Reread-, 44 Guard- und 40 M06-Unit-Fälle. Die Assertions des bisherigen
M06-Massenänderungstests bleiben vollständig erhalten; sein Input enthält jetzt
ausdrücklich die freigegebenen Massenoptionen. Die URL-Negativtests erlauben nur den
neu notwendigen `findById`-Lookup, weiterhin keinerlei Schreib-/Scraperaufrufe.
Ein bestehendes DNS-Verbotsfixture wurde mit vollständigen Beziehungen ausgestattet;
alle ursprünglichen DNS-/URL-Gleichheits- und Speicherassertions bleiben erhalten.

**198 DOM-Tests bestanden, 0 Fehler/Skipped**, einschließlich sämtlicher vorhandener
DOM-Tests. Initiale rote Ergebnisse betrafen noch die bewusst alten Ist-Assertions,
neue Beziehungsanforderungen an Mocks sowie den echten Scale-Randfall 0/0.00;
abschließend behoben, keine Schutzmechanismen abgeschwächt.

Vor jedem tatsächlichen Gradle-Lauf denselben Taskplan erfolgreich geprüft:

```powershell
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.validation.*' --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.service.ReadingStatusExtensionTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.validation.*' --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.service.ReadingStatusExtensionTest
node --test src/test/js/*.test.cjs
```

Nur Kompilierung/Ressourcen/gezielte Tests, kein Vollkontext oder Listener. Bekannte
HotSpot-CDS-Warnung; keine neue Compilerwarnung im Abschlusslauf. Kein Datenbankzugriff,
SQL, Browser, Scraper, Migration, Installation oder neue Dependency. Vorhandene
Sicherheitsfilter und Datenbankguards unverändert; alle Schreibaktionen nur an
synthetischen Mock-Objekten. Keine neuen Daten-/Schemafingerabdrücke behauptet.

**Ausstehend, gesondert freizugeben:** geschützte JPA-Integration, alle 29 echten
MVC-/JSON-Vertragstests, ungefiltertes `clean test` mit Vorher-/Nachher-Fingerabdruck
und manuelle Browsernachprüfung. Insbesondere additive GET-Felder, NULL-Persistenz,
fehlende Beziehungen und M06-Kaufzuordnung erneut prüfen. Die letzte historische
456-Vollsuite ist keine Validierung dieses neuen Codes. Manuelle Schritte siehe
MANUAL_TESTING.md, Abschnitt 18. Kein Commit/Push/Merge/Staging. T02 bleibt insgesamt
offen; SSRF-/Scraper-/Requestgrößengrenzen und T03/T04/T06 bleiben getrennt.

## T02 – Geschützte Altbestandsintegration und Vollregression (16.10.2026)

Der vorangehende DB-freie Zwischenstand bleibt historisch erhalten. Nach ausdrücklicher
Eigentümerfreigabe auf `codex/t02-security-hardening` wurde nun ausschließlich gegen
`mangalib_test` / `mangalib_test_user` geprüft. Testpasswort aus der Prozessvariable
`MANGALIB_TEST_DB_PASSWORD` vorhanden, ohne Wertausgabe; keine relevanten Spring-/JDBC-/
JPA-/Hibernate-/Pool-/JNDI-/JVM-Overrides. Unveränderte TestContext-Registrierung und
GuardedTestDataSource erzwingen vor Initialisierung und jeder Verbindung ausschließlich
Profil `test`, feste URL `jdbc:mysql://localhost:3306/mangalib_test?useSSL=false`,
DDL `validate`, keine SQL-Initialisierung, Flyway oder Liquibase. Sicherheitsfilter
bleiben aktiv. Kein eigener JDBC-Zugriffsweg oder direkter HTTP-Server-Schreibtest.

### Neue Integrationsfälle

`LegacyEditIntegrationTest` verwendet echte Controller, Services, Repositories und
Jackson-3-Responses mit MockMvc, `@Transactional` und `@Rollback`. Ausschließlich neue
synthetische Fixtures über `PersistenceTestFixtures`; Stammdaten werden nur referenziert.
Beide Live-Scraper sind fail-fast statisch ersetzt und werden auf fehlende Aufrufe
geprüft. Schreibrequests bleiben synchron innerhalb der aktiven Rollback-Transaktion.
Nach Änderungen ausdrücklich `flush()`, `clear()` und erneute Repositoryabfragen.
Jeder Fall vergleicht außerdem außerhalb der Testtransaktion vor/nach tatsächlichem
Rollback sämtliche Tabellenwerte und Schema-Metadaten über `TestDatabaseSnapshot`.

| Neue Fallgruppe | Fälle | Nachgewiesenes Ergebnis |
|---|---:|---|
| Band-Reread NULL/0/3, nullable Spezialflag und echte additive JSON-Felder | 3 | Unveränderter Roundtrip erhält Rohwerte und Reihen-Reread; bisherige JSON-Typen bleiben erhalten. |
| Reihen-/Bandflags NULL/false/true | 3 | Unabhängige Bearbeitungen erhalten alle drei Zustände. |
| Reihenmetadaten bei teilweise gelesenen Bänden und Reihen-Reread NULL/0/3 | 3 | Sämtliche Bandspalten bleiben unverändert; kein impliziter Massenauftrag. |
| Explizite Lesestatus- bzw. Reread-Massenoption bei bereits gleichem Reihenwert | 5 | Beide Lesestatuswerte und Mindestwerte 0/3/10000; höhere Band-Rereads werden nicht abgesenkt. |
| Bewusst NULL → 0/false, Reihenwertänderung/gefälschte Importoptionen, fehlender/leerer Legacy-Band-Reread | 4 | Ausdrückliche Änderungen wirksam, Importdaten erzwingen keine Massenaktion; fehlend/leer bleibt der bisherige 0-Vertrag. |
| Fehlender CSRF-Token und fremder Host | 1 | 403 ohne Änderung des vollständigen Snapshots. |
| Historische Reihen-/Band-URLs sowie bewusstes Leeren/Ersetzen | 7 | Exakt gleicher Altwert bleibt erhalten; neuer abweichender unsicherer Wert 400 ohne Teilmutation. Reihen-Leertext leert, Band-Leertext erhält weiterhin. |
| Unbekannte Bandpreise/-anpassungen bzw. Reihenpreis und vorhandene Centbeträge/Summen | 4 | NULL bleibt unbekannt, Darstellung kontrolliert; keine erfundene 0 oder beiläufige Neuberechnung. |
| Finanzielle Band-/Reihenänderung, Preisimport und Erweiterung bei unvollständigen Preisen | 4 | 400 vor jeder Teilmutation, vollständiger Snapshot unverändert. |
| Fehlende Details bzw. Sammelband-/Status-/Verlag-/Typ-/Formatbeziehung | 6 | Kontrollierter Lese-/Schreibfehler, keine Ersatzdetails oder Teilmutation. |
| **Summe** | **40** | **Alle bestanden, einschließlich aller Nach-Rollback-Kontrollen.** |

Fehlende Beziehungen wurden nur an neuen eigenen Fixtures dargestellt; fehlende
Details durch eine zusätzliche neue Reihe ohne Details. Keine vorhandenen Zeilen
beschädigt, keine Constraints deaktiviert, keine SQL-NULL-Werte für primitive
Lesestatusfelder erzwungen. Widersprüchliche Objekt-IDs/MapsId-Zuordnungen sind
mit intakten Datenbank-FKs nicht beliebig persistierbar und werden nicht künstlich
hergestellt; die bestehenden datenbankfreien Prüfungen sowie M06-Fälle für tatsächlich
darstellbare Anzahl-/Nummerninkonsistenzen bleiben der ergänzende Nachweis.

Der erste Entwurfslauf hatte 27 fehlgeschlagene und 9 erfolgreiche Fälle aufgrund
der neuen Test-Helfer: fehlende JSON-Felder unter dem bestehenden `NON_NULL`-Response-
Vertrag wurden nicht berücksichtigt; derselbe Response-Mapper entfernte außerdem
explizite NULL-Werte aus Testrequests. Korrigiert wurde ausschließlich die
Testabbildung: Requests senden NULL wie `JSON.stringify`, Responses verwenden
weiterhin den echten konfigurierten MVC-Mapper. Keine produktive Serialisierungs-
änderung oder abgeschwächte Assertion. Danach 36 Fälle erfolgreich, abschließend
auf die obigen 40 erweitert und erfolgreich geprüft. Die zusätzlichen Synchronitäts-
Assertions wurden im finalen Volltest mitgeprüft. Veraltete `JsonNode.asText()`-
Verwendung im neuen Test entfernt; keine neue Test-Compilerwarnung im Abschlusslauf.

### Gestufte Ausführung und Ergebnisse

Vor jedem tatsächlichen Gradle-Aufruf denselben Aufgabenplan mit zusätzlichem
`--dry-run` geprüft. Ausschließlich Offline-Auflösung; keine neuen Dependencies.
Kein manueller Launcherprozess oder Listener auf 18080 vorhanden; vorhandenes
manuelles JAR vor `clean` lesend auf exklusive Öffnung geprüft und nicht gesperrt.
Kein Prozess gestartet oder beendet. Aufgabenpläne enthalten nur Kompilierung,
Ressourcen und `test`, beim Volltest zusätzlich `clean`.

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.LegacyEditIntegrationTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.MvcContractTest --tests de.mangalib.LocalSecurityMvcTest --tests de.mangalib.ServiceTest --tests de.mangalib.PersistenceCompatibilityTest --tests de.mangalib.RepositoryCompatibilityTest --tests de.mangalib.TestDataPreservationTest --tests de.mangalib.TemplateRenderingTest --tests de.mangalib.MangalibApplicationTests --tests de.mangalib.DemoApplicationTests --tests de.mangalib.ReadingStatusIntegrationTest
.\gradlew.bat --offline --no-daemon clean test
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
```

| Stufe | Bestanden | Fehler/Errors | Übersprungen |
|---|---:|---:|---:|
| Datenbankfreier Guard vor erstem DB-Zugriff | 44 | 0 | 0 |
| Lesender Ausgangsfingerabdruck | 1 | 0 | 0 |
| Neue Altbestandsintegration, abschließender gezielter Lauf | 40 | 0 | 0 |
| Bestehende geschützte Regressionen einschließlich M06 | 107 | 0 | 0 |
| Vollständiger ungefilterter `clean test` | **547** | **0** | **0** |
| Gesonderter lesender Abschlussfingerabdruck | 1 | 0 | 0 |

547 unterschiedliche Fälle = historische 456 + 51 neue DB-freie Legacy-Fälle +
40 neue Integrationsfälle. Gezielte Wiederholungen sind nicht zusätzlich zu zählen.
Alle **29 MVC-/JSON-Verträge** und **34 M06-Integrationsfälle** bestanden gezielt und
im Volltest. Die bestehenden Vertragsassertions blieben unverändert. Im M06-Test
für normale Massenbearbeitung enthält der Input nun die ausdrücklich freigegebenen
Massenoptionen; sämtliche bisherigen fachlichen Assertions bleiben erhalten.
Direkte M06-Erweiterungen werden zusätzlich mit eingeschalteten Massenoptionen
geprüft: alte Bände bleiben trotzdem unverändert. Beide Kaufzuordnungen über Index
und Titel, Kaufneuanlage, individuelle Rereads und Rollbacks bleiben erfolgreich.
Selenium-/Live-Scraper-Tests weiterhin per bestehendem Tag ausgeschlossen; keine
unerwartet übersprungenen Fälle. Bestehende Deprecation-/HotSpot-CDS-Hinweise bleiben.

Keine weitere Produktionscodekorrektur in diesem Integrationsauftrag erforderlich.
Die vorhandenen Altbestandskorrekturen aus dem vorherigen Auftrag bleiben erhalten.
Keine Frontendänderung in diesem Auftrag; die **198 bereits bestandenen DOM-Tests**
wurden deshalb nicht wiederholt. Keine manuelle Browserabnahme, Artefaktpaketierung
oder `manualSafetyTest`; MANUAL_TESTING.md, Abschnitt 18 bleibt separat abzuarbeiten.

### Daten-/Schemafingerabdruck und verbleibende Grenzen

Vorher und nachher exakt identisch:

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

Zehn Tabellen und **78 Stammdatensätze** unverändert (62 Verlage, 4 Status,
4 Typen, 3 Formate, 5 Sammelbände). Alle fünf Geschäftstabellen vor/nach leer,
keine verbliebenen Testfixtures. Unverändert auch 75 Spalten, 24 Indexeinträge,
12 Fremdschlüssel und 26 Key-Column-Einträge. AUTO_INCREMENT bewusst ausgenommen
und nicht zurückgesetzt. Die erste lokale PowerShell-Auswertung behandelte den
XML-System-out-Knoten fälschlich als Text und fand keine Hashes; die erneute rein
lokale Auswertung der bereits vorhandenen XML-Dateien bestätigt beide vollständig
vorhandenen identischen Hashes und Zählzeilen. Kein Datenbankfehler oder erneuter
Datenbankzugriff zur Korrektur dieser Berichtsauswertung.

Lokale ignorierte Nachweise unter `.gradle/t02-legacy-integration-20261016/`:
`baseline-before.xml`, `baseline-after.xml`, `new-integration.xml`,
`regression-results/`, `clean-test-results/`, `clean-test-counts.json` und
`clean-test-report/index.html`. Der reguläre Bericht unter `build/reports/tests/test`
zeigt nach dem separaten Abschlusslauf nur den Baseline-Test; der vollständige
547-Fälle-Bericht wurde vorher gesichert. `git diff --check` ohne Fehler.

Die synthetischen Integrationsfälle beweisen keine vollständige Kompatibilität
mit unbekannten produktiven Altbeständen. Primitive SQL-NULL-Lesestatuswerte,
unmögliche FK-/MapsId-Widersprüche, befülltes Lazy-Rendering außerhalb einer
Testtransaktion, echte Browserdarstellung sowie konkurrierende Writes bleiben
gesonderte Nachweisgrenzen. Manuelle Nachprüfung der neuen Optionen und NULL-
Darstellung erst nach gesonderter Start-/Browserfreigabe. T02 insgesamt bleibt
offen, insbesondere SSRF-/Scraper-/Requestgrößengrenzen; T03-Atomarität/Idempotenz/
Preisverteilung, T04 und T06 sowie S03-Historie wurden nicht vorgezogen.

Keine Zugriffe auf `mangalib` oder `mangalib_manual_test`, keine Migrationen,
Schemaänderungen, Initialisierung, Datenbereinigung oder neue Abhängigkeiten.
Kein Anwendungs-/HTTP-Listener-/Browser-/Live-Scraperstart, kein Prozessstopp,
Git-Staging, Commit, Push oder Merge. Testguards, Testprofil, Security-Konfiguration,
Gradle-Konfiguration und Datenmodell unverändert.

## T02 – Isolierte Browsernachprüfung der Altbestandskorrekturen (16.10.2026)

Nach gesonderter Freigabe manuelles Artefakt offline mit geprüftem Taskplan gebaut,
ValidateOnly erfolgreich, eigene PID 21952 ausschließlich mit `manual-test` und
`mangalib_manual_test` auf 127.0.0.1:18080 betrieben. Keine Verbindung zur produktiven
oder automatisierten Datenbank, keine neue Java-/DOM-Testausführung. Die 547 Java-
und 198 DOM-Erfolge bleiben getrennte Nachweise.

Codex In-app Browser: fünf vollständig bestandene und zwei teilweise prüfbare
Szenariengruppen A–G. Metadaten erhalten individuelle Bandwerte; ausdrückliche
Massenoptionen einschließlich Mindestwert 3/5/3 und Zurücksetzen funktionieren;
M06-Erweiterung ignoriert Massenoptionen und erhält alte Bände. NULL-Reread und
NULL-Spezialflag blieben bei unabhängiger Bearbeitung erhalten, bewusste 0/false-
Eingaben funktionierten. Neue ungültige URLs kontrolliert abgewiesen, keine
beobachteten Konsolenfehler/Alerts; schmale Ansicht und Tastatur geprüft. Nicht
verfügbare NULL-Preise und historisch unsichere URLs nicht künstlich hergestellt.
Kein vollständiger HTTP-/Netzwerk-Trace; keine Statuscodes aus UI-Meldungen abgeleitet.

Eine neue eigene Reihe/Details 11 und vier Bände 25–28 verbleiben bewusst in der
manuellen DB, keine neue Einkaufsposition. Vollständiger ID-/Spaltenvergleich aller
ursprünglichen Zeilen, 17 Stammdaten und Schema-Metadaten unverändert. Manuelle
JSON-Hashes durch neue Daten erwartungsgemäß verschieden; Details und Nachweisdateien
in MANUAL_TESTING.md, Abschnitt 18. Keine Änderung am JUnit-Snapshotformat oder Guard.

Bekannter **T04-Preisfehler erneut beobachtet**: Normaler Erweiterungs-Payload ohne
Bandpreis erzeugt neuen Band mit 0,00 statt einer fachlich geklärten Preisübernahme.
Kein alter Preis verändert und keine eigenständige Korrektur; Reproduktion und
Codepfad in Abschnitt 18. Keine neue T02-Lesestatus-/Altbestandsregression festgestellt.

Eigene Instanz per Ctrl+C geordnet beendet (Tomcat- und JPA-Shutdown bestätigt),
PID nicht mehr vorhanden, Port 18080 frei. Keine Bereinigung, Migration, neuen
Dependencies, Live-Scraper oder Git-Abschlussaktionen. T02 insgesamt bleibt offen;
SSRF-/Scraper-/Requestgrößengrenzen und T03/T04/T06 bleiben getrennte Folgearbeiten.

## T02 – HTTP-/JSON-Größenbegrenzung, datenbankfreier Zwischenstand (16.10.2026)

Branch `codex/t02-security-hardening`. Implementierung und ausschließlich gezielte
datenbankfreie Prüfungen freigegeben. Historische 547 Java-/198 DOM-Erfolge und die
Browserabnahme bleiben erhalten; sie gelten **nicht** als Vollintegration dieses
neuen Filters. Keine Verbindung zu einer der drei MangaLib-Datenbanken, kein SQL,
Boot-Vollkontext, Listener, Browser, Scraper oder externer Abruf in diesem Auftrag.

### Statisch belegter Ausgangszustand und Risikoeinordnung

Alle acht bestehenden Schreibpfade wurden geprüft: `/addMangaReihe`, `/editBand`,
`/addToEinkaufsliste`, `/buyItemUpdate`, `/buyItemCreate`, `/scrape`,
`/autofillBandData/{bandIndex}` (POST) und `/items/{id}` (DELETE). Fachliche Eingaben
sind JSON-Maps, Autofill/Delete benötigen keinen Body. Keine Uploadschnittstelle
oder URL-encoded Schreibformular; HTML-Dialoge verwenden Fetch mit JSON.

Die bisherigen Titel-/URL-/Preis-/Referenz-/Importmap-Grenzen in WriteValidation
greifen **nach** Jacksons Deserialisierung. Es gab keine eigene allgemeine Body-
Streaminggrenze, keine projektspezifische JSON-Strukturgrenze und keine explizite
Duplikaterkennung. Das ist eine belegte Schutzlücke vor der Fachvalidierung, kein
durchgeführter Netzwerkangriff. Jackson hat bereits eigene Defaultconstraints;
es wird ausdrücklich nicht behauptet, zuvor seien alle Parsergrenzen unbegrenzt.

Die Anwendungskonfigurationen setzen bislang keine Tomcat-/Multipart-Größenwerte.
Die lokal vorhandenen offiziellen Boot-4.1.1-Quellen zeigen standardmäßig 2 MiB
`maxHttpFormPostSize`, 2 MiB `maxSwallowSize`, 1000 Parameter und 50 Multipartteile.
Tomcats Formparserlimit ist **kein allgemeines JSON-Bodylimit**. Multipart-
Autokonfiguration kann unabhängig von einem passenden Uploadcontroller Parserarbeit
auslösen; keine unbegrenzte Dateiannahme oder konkrete Tempdateiattacke nachgewiesen.
Loopback-/Host-/Origin-/CSRF-Grenzen bleiben relevant, ersetzen aber keinen
Ressourcenrahmen. Risiko vor allem bei untrusted lokalen Anfragen oder gültiger
Sitzung; keine öffentliche Erreichbarkeit oder erfolgreiche Ausnutzung behauptet.

### Gewählte Schutzschichten und Grenzen

`security/RequestLimitsConfiguration` registriert den neuen Filter auf `/*` für
REQUEST-Dispatches, vor FormContentFilter und Spring Security, nach dem üblichen
CharacterEncodingFilter. CSRF-/Host-/Origin-Implementierung bleibt unverändert.
Für akzeptierte Requests läuft dieselbe Sicherheitskette; neue Größen-/Formatfehler
können bewusst schon vor einer sonst folgenden 403-Antwort abgewiesen werden.
Kein `addFilters=false`, kein Security-Bypass und keine profilabhängige Ausnahme.

| Grenze | Wert | Begründung |
|---|---:|---|
| Tatsächlicher Request-Body | **32 MiB / 33.554.432 Byte** | Auch 10.000 Bände mit 30.000 Importwerten und langen Unicode-URLs haben bei normalem Browser-JSON ausreichend Platz. Keine Reduktion der fachlichen Band-/Reread-Grenze. |
| JSON-Verschachtelung | **16** | Aktueller Payload benötigt nur Wurzel und Importmap; großzügiger Abstand zum realen Vertrag. |
| JSON-String / Propertyname | **4096 / 512** decodierte UTF-16-Einheiten | Bestehende Felder maximal 255 Unicode-Codepoints bzw. Importkeys 255 Java-Zeichen; keine zulässigen Normalwerte verkleinert. |
| JSON-Zahlenliteral | **64** Parserzeichen | Bestehende IDs/Anzahlen/Rereads weit darunter; Preisstrings behalten ihre eigene 64-Zeichen-/BigDecimal-Prüfung. |
| Propertynamen insgesamt | **30.064** | Bestehende Importmap bis 30.032 Einträge plus Raum für äußere Requestfelder. |
| Arraywerte insgesamt | **30.064** | Keine fachliche Array-Schnittstelle vorhanden; trotzdem begrenzter technischer Spielraum vor Typvalidierung. |
| JSON-Tokens | **100.000** | Vollständiger 10.000-Bände-Import benötigt ungefähr 60.000 Schlüssel-/Werttokens plus Rahmen. |
| Doppelte JSON-Schlüssel | **abgewiesen** | Jackson STRICT_DUPLICATE_DETECTION, auch verschachtelt und bei Unicode-escaped Schlüsseln; keine mehrdeutige Last-value-wins-Eingabe. |

Der feste Bodyrahmen gilt für alle potentiell schreibenden Methoden, insbesondere
POST/PUT/PATCH/DELETE, unabhängig von URL und Content-Type. GET/HEAD/OPTIONS/TRACE
bleiben unverändert; deren Header-/Queryverarbeitung bleibt bei bestehenden Server-
grenzen. Normale JSON-Bodies mit `application/json` und `*+json` werden vor MVC
tokenweise geprüft, ohne Map-/Entity-Deserialisierung. Lazy Strings werden tatsächlich
gelesen. Wie Springs Jackson-Converter: Unicode per Byte-Erkennung, andere explizite
Charsets per Reader; damit prüfen Vorprüfung und Binding dieselben Zeichen.

Content-Length oberhalb des Limits führt ohne Öffnen des Bodys zu 413. Unabhängig
von diesem Header werden höchstens **Limit+1 Byte** aus dem Servletstream gelesen,
auch bei fehlender Länge oder bereits dekodiertem chunked Body. Kleine Reads werden
in Blöcke bis 8192 Byte zusammengefasst; keine Millionen gespeicherter Einbyteblöcke,
keine vollständige unbeschränkte Kopie und kein unbeschränktes readAllBytes vor
dem Vergleich. Akzeptierte Blöcke werden unverändert für MVC wiedergegeben.
Vollständiges begrenztes Vorlesen ist nötig, weil Body-ignorierende Handler oder
ein früh beendeter JSON-Parser sonst Facharbeit vor Erkennung eines übergroßen
Rests starten könnten. Der einzige Schutz ist also ausdrücklich **nicht** eine
nachträgliche Prüfung eines bereits unbeschränkt eingelesenen Bodys.

Fehlformatierte/mehrfache Content-Length, Content-Length zusammen mit Transfer-
Encoding, widersprüchliche Content-Type-Header, nicht unterstütztes Transfer-Encoding
und vom Double tatsächlich gelieferte Längenabweichungen werden abgewiesen. Ein
Content-Type-Wechsel umgeht das Bytebudget nicht; unterstützte JSON-Suffixe erhalten
auch die Strukturprüfung. Andere Typen durchlaufen anschließend die bisherigen MVC-
Medientypverträge. URL-encoded und sämtliche Multipart-Schreibbodies werden früh
mit **415** abgewiesen, vor getParameter/getParts und MultipartResolver. Komprimierte
Request-Bodies ebenfalls 415; `identity` bleibt erlaubt. Es gibt dafür keinen
benötigten bestehenden Schreibvertrag. Keine Uploaddateien oder Entpackungswege.

Tomcat wird zusätzlich nach den Boot-Property-Customizern auf das bisherige **2-MiB-
Formparserlimit** festgelegt, also nicht zugunsten des größeren JSON-Limits gelockert.
`maxSwallowSize=0` und `Connection: close` bei Filterablehnung verhindern das geplante
Nachlesen großer abgewiesener HTTP/1.1-Bodies für Keep-alive. Die Wirkung auf einer
echten Verbindung wurde hier **nicht** ausgeführt. Vorhandene Multipartdefaults
werden nicht verändert; frühere Ablehnung macht diesen Verarbeitungspfad unnötig.

### HTTP-Verträge und Nachweisgrenzen

- Neue technische Größen-/Strukturüberschreitung: **413**, fester neutraler Text.
- Mehrdeutiges/malformes JSON oder Framing: **400**, fester neutraler Text; kein
  Parsertext, Payloadausschnitt, Dateiname oder Stacktrace. Mehrere JSON-Wurzeln abgewiesen.
- Nicht vorgesehenes Formular-/Upload-/Kompressionsformat: **415**, fester neutraler Text.
- Normale Erfolgspayloads und fachliche 400-/404-/204-Verträge bleiben in den
  geprüften Standalone-Fällen erhalten. Bestehende Nullable-/Reread-/Preis-/URL-
  und M06-Regeln unverändert. Keine Änderung am Datenmodell oder an fachlichen Berechnungen.
- Die vorhandene Frontend-Fehlerhilfe behandelt 413 konservativ als unbekannten
  Schreibfehler und verlangt Reload; keine automatische Wiederholung. Kein neuer
  spezifischer 413-Dialogtext, keine Frontendänderung, deshalb keine DOM-Wiederholung.
- Vorpufferung begrenzt die Bodybytes, **nicht den gesamten JVM-Heap auf 32 MiB**.
  Hinzu kommen Parser-/Objektkosten und je eine Budgetinstanz pro parallelem Request;
  viele gleichzeitige Anfragen oder langsame Sender sind damit nicht vollständig
  abgewehrt. Keine neue Rate-/Parallelitäts-/Zeitlimitarchitektur in diesem Teilauftrag.
- Extreme Whitespace-/Escape-Aufblähung kann auch bei semantisch identischem Inhalt
  das Bytebudget überschreiten. Das ist die beabsichtigte Transportgrenze; normale
  Browser-Serialisierung und der große Import wurden ausdrücklich geprüft.
- Der Replaystream und die Registrierung sind für die vorhandene synchrone Servlet-
  Verarbeitung ausgelegt. Künftige asynchrone/nonblocking Bodyhandler brauchen eine
  eigene Anpassung; solche Handler existieren derzeit nicht.
- Mock-Header beweisen **kein** reales HTTP-Framing oder Request-Smuggling-Verhalten.
  Tomcat kann Header bereits vorher abweisen/normalisieren und bei falscher Länge
  andere Bytes als Body sichtbar machen. TCP-Mehrdaten außerhalb des vom Container
  abgegrenzten Bodys sieht ein Servletfilter nicht. Keine gegenteilige Zusicherung.

### Ausgeführte Tests, Dateien und nächster Freigabeschritt

**482 datenbankfreie Java-Tests bestanden, 0 Fehler/Errors, 0 Skipped.** Davon neu:
64 `RequestBodyLimitFilterTest`- und 19 `RequestLimitsMvcTest`-Fälle (**83 neue**).
Zusätzlich unverändert 44 Guard-, 61 LocalSecurityPolicy-, 40 M06-Unit-, 51 Legacy-,
98 WriteMvcValidation-, 38 WriteServiceSafety-, 61 WriteValidation- und 6 Anlage-
Reread-Fälle. Die 98 Schreib-/51 Altbestandsfälle laufen nun mit dem neuen Filter
vor den unveränderten bestehenden Sicherheitsfiltern; keine Assertion abgeschwächt.

Prüfungen umfassen reale Produktionsgrenze minus ein Byte/exakt/plus ein Byte mit
generiertem Stream, kleine Einbyte-Reads, Abbruch nach Limit+1 ohne Drain, frühe
Headerablehnung ohne Streamöffnung, fehlende/falsche Header, UTF-8-Bytezählung,
Strings/Namen/Tiefe/Zahlen/Felder/Arrays jeweils an und über der Grenze, exakt
100.000/100.001 Tokens, Duplikate/Unicode-Escapes, Zeichensatzvergleich, gebrochene
Streams, alternative Typen sowie alle acht Schreibpfade ohne Downstream-/Service-
arbeit bei Filterablehnung. Reine Factory-/Connectorobjekte prüfen Registrierung
und Einstellungen; kein getWebServer/init/start oder Socket. Ein gültiger Import
mit 10.000 Bänden, 30.001 Importmap-Einträgen, langen Emoji-URLs und Reread 10.000
erreicht genau einmal ausschließlich den Speicherdienst-Mock. Keine Bandanlage.

Vor jedem tatsächlichen Gradle-Testlauf den identischen Plan mit `--dry-run` geprüft:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.security.RequestBodyLimitFilterTest --tests de.mangalib.security.RequestLimitsMvcTest --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' --tests de.mangalib.service.ReadingStatusExtensionTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.security.RequestBodyLimitFilterTest --tests de.mangalib.security.RequestLimitsMvcTest --tests de.mangalib.testsupport.TestDatabaseGuardTest --tests de.mangalib.security.LocalSecurityPolicyTest --tests 'de.mangalib.validation.*' --tests de.mangalib.service.ReadingStatusExtensionTest
```

Aufgaben ausschließlich compileJava/processResources/compileTestJava/processTestResources/
test; kein manualSafetyTest oder Boot-Start. Erste neue Test-Kompilierungsfehler
(Boot-Gettername, Mockito-Generics) korrigiert. Ein späterer neuer Headerfall hatte
400 statt 415 erwartet, obwohl MockHttpServletRequest Content-Type ersetzt; der
Test bildet mehrere Rohheader nun explizit nach. Alle Endläufe erfolgreich, keine
Schutzprüfung gelockert. Veralteten neuen Parseraufruf vor Abschluss ersetzt;
abschließend keine neue Compilerwarnung, bekannte HotSpot-CDS-Warnung bleibt.

In diesem Auftrag neu: `security/RequestBodyLimitFilter.java`,
`security/RequestLimitsConfiguration.java`, `security/RequestBodyLimitFilterTest.java`
und `security/RequestLimitsMvcTest.java`. Geändert: Filtereinbindung in
`validation/WriteMvcValidationTest.java` und `validation/LegacyEditCharacterizationTest.java`,
dieser Nachtrag sowie T02-Fortschritt in ROADMAP.md. Vorhandene sonstige Änderungen
erhalten. Keine Abhängigkeiten, Testguards, Testprofile, CSRF-/Host-/Origin-Klassen,
manuelle Konfigurationen, Entities oder Geschäftsregeln geändert.

**Nicht ausgeführt, gesonderte Freigabe erforderlich:** geschützte JPA-/Boot-
Integration, 29 vollständige MVC-/JSON-Verträge, 34 M06-Integrationsfälle und
ungefiltertes clean test mit Vorher-/Nachher-Fingerabdruck. Danach echte isolierte
Tomcat-HTTP-Prüfung mit fester/fehlender Länge, chunked, Framingkonflikten,
Keep-alive/Verbindungsende und frühem Multipart-/Form-Abbruch. Browsernachprüfung
normaler Speicherung, Import, CSRF und verständlicher 413-/Reload-Anzeige erst nach
Neubau/ValidateOnly des getrennten manuellen Artefakts; kein Start in diesem Auftrag.
Keine neuen Daten-/Schemafingerabdrücke behauptet. T04-Nullpreisfehler, T03 und T06
unverändert; Scraper-/SSRF-Schutz und allgemeine Lastbegrenzung bleiben offen.

## T02 – HTTP-/JSON-Begrenzung: geschützte Vollintegration (16.10.2026)

Dieser Nachtrag schließt die im vorstehenden datenbankfreien Zwischenstand noch
ausstehende Boot-/JPA-Integration ab. Historische Ergebnisse bleiben erhalten.
Ausdrücklich freigegeben waren ausschließlich die bestehenden geschützten
Testabläufe auf `mangalib_test`; kein echter HTTP-Listener oder Browser.

### Sicherheitsvoraussetzungen und tatsächliche Filterkette

Branch `codex/t02-security-hardening` bestätigt, vorhandene Änderungen erhalten.
`MANGALIB_TEST_DB_PASSWORD` in der Prozessumgebung vorhanden und nicht leer; nur
diesen Sachverhalt geprüft, keinen Wert ausgegeben oder gespeichert. Keine
einschlägigen Spring-/Server-/Management-/JDBC-/JPA-/Hibernate-/JNDI-/Pool-/JVM-
Overrides und keine zusätzlichen Gradle-Init-Skripte festgestellt. Guards,
Testprofil, bestehende Sicherheitsfilter und Abhängigkeiten unverändert.

Vor dem ersten DB-Zugriff **44 datenbankfreie Guard-Fälle erfolgreich**. Der
unveränderte TestContext-Schutz erzwingt vor Singleton-Erzeugung und jeder
Verbindung ausschließlich Profil `test`,
`jdbc:mysql://localhost:3306/mangalib_test?useSSL=false`, `mangalib_test_user`
und das Passwort aus der genannten Prozessvariable. Alternative DataSources,
Pool-/JNDI-/direkte JPA-Verbindungswege bleiben gesperrt. `ddl-auto=validate`,
`generate-ddl=false`, SQL-Initialisierung `never`, Flyway/Liquibase deaktiviert.
Bestehender Ausschluss `external-network` und deaktivierte Live-Scrapertests
beibehalten. Serverrechte bleiben eine unabhängig administrierte Schutzgrenze.

`RequestLimitsIntegrationTest` verwendet `@SpringBootTest` mit dem standardmäßigen
MOCK-Webkontext und `@AutoConfigureMockMvc(print=NONE)`: kein Socket und kein
manuelles Hinzufügen oder Abschalten von Filtern. Boots Registrierung auf `/*`,
Order `HIGHEST_PRECEDENCE + 1` und synchrone Verarbeitung werden geprüft.
Die reale SecurityFilterChain enthält weiterhin LocalRequestFilter vor CsrfFilter.
Malformes JSON/überhöhte deklarierte Länge ohne CSRF-Token werden bereits mit
400/413 abgewiesen; URL-encoded PUT mit 415 vor der Parameterverarbeitung.
Zulässig große JSON-Anfragen erreichen dagegen weiterhin die Sicherheitsprüfung.
Damit ist die frühe Reihenfolge im Boot-MockMvc-Verbund funktional nachgewiesen;
die tatsächliche Connector-/TCP-Verarbeitung ist ausdrücklich nicht Bestandteil.

### 48 neue Integrationsfälle

| Fallgruppe | Anzahl | Nachweis |
|---|---:|---|
| Acht Schreibpfade jeweils mit überlangem JSON-String, malformem JSON, doppeltem Schlüssel, URL-encoded und Multipart | 40 | Strikte 400/413/415, fester neutraler Text/Content-Type, keine internen Details; weder Controller-, Service-, beobachtete Geschäftsrepository- noch Gatewayarbeit. Vollständiger Snapshot vor/nach Ablehnung identisch. |
| Tatsächliche Boot-Registrierung und frühe Reihenfolge | 1 | Keine zweite Registrierung, erwarteter URL-Bereich/Order, aktive Security-Kette; frühe Ablehnung auch ohne CSRF und bei PUT-Formulardaten. |
| Fehlender/falscher/fremder Sessiontoken sowie fremder Host/Origin bei zulässigem JSON | 5 | Weiterhin 403 vor Facharbeit; Sessiontoken aus echtem GET `/home`, kein CSRF-Testpostprozessor. |
| Fachlich ungültiges JSON und nichtpositive Kauf-ID | 1 | Bisheriger neutraler fachlicher 400-Text und 404 erhalten, keine Service-/Repositoryarbeit. |
| Normaler Band-JSON-Roundtrip mit explizitem NULL | 1 | Echter Speicherdienst/JPA, leerer 204 ohne Content-Type; Reread/Spezialflag NULL und sämtliche Tabellenwerte nach flush/clear/Reload erhalten. |

Alle acht Pfade: `/addMangaReihe`, `/editBand`, `/addToEinkaufsliste`,
`/buyItemUpdate`, `/buyItemCreate`, `/scrape`, `/autofillBandData/900001` und
DELETE `/items/1`. Die Scraperpfade werden hier nur abgewiesen; normale zulässige
Scraperantworten prüfen weiterhin die bestehenden Tests mit statischen Doubles.

Spies beobachten echte Controller, Services, Gateway und die fünf
Geschäftsrepositories; keine fachliche Methode wird im neuen Test gestubbt.
Beide statischen Live-Scraper sind zusätzlich fail-fast ersetzt. Nur der positive
JPA-Fall legt über `PersistenceTestFixtures` neue synthetische Daten an. Alle Fälle
laufen mit `@Transactional`/`@Rollback`, prüfen synchrone Requests und aktive
Rollback-Markierung. Vorher-/Nachher-Snapshots außerhalb der Testtransaktion
belegen den tatsächlichen Rollback. Stammdaten ausschließlich gelesen/referenziert;
kein Commit, REQUIRES_NEW, asynchrones Schreiben oder nachträgliches DELETE-Cleanup.

### Ausführung, Zwischenfehler und Abschluss

Vor **jedem** tatsächlichen Gradle-Aufruf denselben Plan mit `--dry-run` gelesen.
Die Pläne enthielten nur Kompilierung/Ressourcen/Test, im Volltest zusätzlich
`clean`; keine weiteren Testtasks oder Launcher. Ausschließlich Offline-Auflösung.
Das manuelle JAR war lesend exklusiv zu öffnen, kein manueller Launcherprozess
und kein Listener auf 18080 vorhanden; kein Anwendungsprozess gestartet oder beendet.
Die zunächst in der Sandbox nicht verfügbare CIM-Prozessabfrage wurde mit
freigegebener rein lesender Betriebssystemabfrage erfolgreich wiederholt.

| Stufe | Bestanden | Fehlgeschlagen/Errors | Übersprungen |
|---|---:|---:|---:|
| Guard vor erstem DB-Zugriff | 44 | 0 | 0 |
| Rein lesender Ausgangsfingerabdruck | 1 | 0 | 0 |
| Neue Filterintegration, erster Lauf | 48 | 0 | 0 |
| Bestehende geschützte Regressionen, erster Lauf | 146 | 1 | 0 |
| Wiederholung: Filterintegration und bestehende geschützte Regressionen | 195 | 0 | 0 |
| **Ungefilterter `clean test`** | **678** | **0** | **0** |
| Gesamte DOM-Suite `pnpm run test:dom` | 198 | 0 | 0 |
| Separater rein lesender Abschlussfingerabdruck | 1 | 0 | 0 |

Der einzige fehlgeschlagene Bestandsfall erwartete für URL-encoded POST `/scrape`
noch **403**. Der neue Filter liefert wie zuvor dokumentiert **415**, bevor CSRF
Parameter lesen kann. `LocalSecurityMvcTest` erwartet deshalb nun ausdrücklich
415 und prüft weiterhin fehlende Controller-/Scraperarbeit. Die bestehenden
403-Prüfungen für zulässige Formate, Host, Origin, Forwarded-Header und Sessiontoken
bleiben erhalten; keine Testdeaktivierung oder pauschal erlaubte Statusmenge.
Dies aktualisiert genau den beabsichtigten technischen Formatvertrag und schwächt
keinen Schutz ab. Alle **29 MVC-/JSON-Vertragsassertions** unverändert bestanden.
Ein neuer Raw-Type-Compilerhinweis wurde durch den passenden Wildcard-Typ im
Integrationstest beseitigt. Keine Produktionscodekorrektur in diesem Auftrag.

678 unterschiedliche Java-Fälle = vorherige 547 + 83 neue datenbankfreie
Filter-/MVC-Fälle + 48 neue Integrationsfälle. Gezielte Wiederholungen nicht
zusätzlich zählen. Alle **34 M06-Integrationsfälle**, **40 Altbestandsfälle** und
**29 MVC-/JSON-Fälle** bestanden gezielt und im ungefilterten Volltest. Dazu gehören
Kauf in bestehende/neue Reihen einschließlich 417-Statusauswahl, unveränderte alte
Bandstatus/Rereads bei Erweiterungen, explizite Massenoptionen, historische
NULL-/URL-/Preiswerte und Beziehungen. Bestehende 200/204/400/404/417-Fachverträge
erhalten. Keine unerwartet übersprungenen Fälle; Live-Tests weiterhin per Tag
ausgeschlossen. Bekannte Deprecation- und HotSpot-CDS-Hinweise bleiben bestehen.

Wesentliche Aufrufe, jeweils nach dem entsprechenden `--dry-run`:

```powershell
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.RequestLimitsIntegrationTest
# Anschließend bestehende geschützte Klassen und neue Filterklasse gemeinsam;
# genaue Klassenauswahl entspricht dem vorigen Integrationsnachtrag plus
# LegacyEditIntegrationTest und RequestLimitsIntegrationTest.
.\gradlew.bat --offline --no-daemon clean test
pnpm run test:dom
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.DatabaseIntegrityBaselineTest
```

### Daten-/Schemaintegrität und Nachweisgrenzen

Separater Ausgangs- und Abschlussfingerabdruck exakt identisch:

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

Zehn Tabellen, **78 Stammdatensätze** (62 Verlage, 4 Status, 4 Typen, 3 Formate,
5 Sammelbände) und alle fünf leeren Geschäftstabellen unverändert. Keine dauerhaft
verbliebenen Fixtures. Identisch auch 75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel
und 26 Key-Column-Einträge. AUTO_INCREMENT bewusst ausgenommen und nicht zurückgesetzt.
Snapshots ausschließlich über das bestehende geschützte JPA-Prüfverfahren,
keine direkten JDBC-/SQL-Werkzeuge und keine Verbindung zu `mangalib` oder
`mangalib_manual_test`.

Lokale ignorierte Nachweise: `.gradle/t02-request-limits-integration-20261016/`
mit `before.xml`, `after.xml`, `comparison.txt`, `new-integration-first.xml`,
`regression-first/`, `regression-final/`, `clean-test-results/`,
`clean-test-counts.json`, `clean-test-report/index.html` und `dom.txt`.
Nach dem separaten Abschlusslauf zeigt `build/reports/tests/test` nur den
Baseline-Fall; der vollständige 678-Fälle-Bericht ist zuvor gesichert worden.

In diesem Auftrag neu: `src/test/java/de/mangalib/RequestLimitsIntegrationTest.java`.
Geändert: `LocalSecurityMvcTest.java` und dieser Nachtrag. Sämtliche vorherigen
Änderungen erhalten; keine Anwendungscode-, Konfigurations-, Guard-, Profil-,
Dependency- oder Datenmodelländerung. `git diff --check` ohne Befund; nichts gestagt.

**Weiter offen, getrennt freizugeben:** echte Tomcat-/TCP-Prüfung von Framing,
chunked Bodies, Keep-alive/Verbindungsende und frühem Multipart-/Form-Abbruch;
Browserprüfung normaler Speicherung/Import/CSRF und verständlicher 413-/Reload-
Behandlung nach Neubau/ValidateOnly des manuellen Artefakts. MockMvc beweist keine
echte HTTP/1.1-Verbindungsbehandlung oder Tomcat-Headernormalisierung. Per-Request-
Bytebudget ist kein gesamtes Heap-/Parallelitäts-/Slow-Sender-Limit; kein Lasttest.
Unbekannte produktive Altbestände sind nicht vollständig durch synthetische
Fixtures repräsentiert. Scraper-/SSRF-Schutz bleibt offen, ebenso T03/T04/T06.
Keine Migration, Schemaänderung, Initialisierung, Bereinigung, Installation,
Listener-/Browser-/Live-Scraperausführung oder Git-Abschlussaktion. Weitere
T02-Arbeiten oder Browserabnahme erst nach neuer Eigentümerfreigabe.

## T02 – Reale Tomcat-/HTTP-Teilabnahme, Verbindungsende offen (16.10.2026)

Nach ausdrücklicher Freigabe ausschließlich `manual-test` / `mangalib_manual_test`
auf 127.0.0.1:18080 geprüft. Vollständiger Ablauf, Status-/Headernachweise und
Nachweisgrenzen in MANUAL_TESTING.md, Abschnitt 19. Historische **678 Java-/198
DOM-Erfolge** unverändert; in diesem Auftrag keine Test-Suite wiederholt.

`assembleManualTest --dry-run` geprüft, danach Offline-Build erfolgreich;
`Start-ManualTest.ps1 -ValidateOnly` mit Java 21 bestanden. Vorher kein manueller
Prozess/Listener; feste manuelle Guards, Konfiguration und synthetische Doubles
unverändert. Eigene PID 38212 mit Tomcat 11.0.24 und ausschließlich Loopback bestätigt.

Fünf reale HTTP/1.1-Anfragen: zwei vollständig ausgelieferte HTML-Views mit 200
und manuellem Marker auf derselben wiederverwendeten TCP-Verbindung; zwei
syntaktisch harmlose POST-Probes ohne CSRF mit 403; eine angekündigte Bodygröße
von 33.554.433 Byte ohne gesendeten Body mit früher vollständiger **413**.
POST ausschließlich auf nicht vorhandenem Probe-Pfad, keine Geschäftsoperation.
413 aus dem Anwendungsfilter mit exakt neutralem Text, `Content-Type:
text/plain;charset=UTF-8`, `Content-Length: 60`, `Connection: close`.

**Abbruchbefund:** Nach vollständig gelesener 413-Antwort weder EOF noch Reset
innerhalb des festgelegten 1-Sekunden-Fensters. Client schließt selbst; keine
weitere Probe, Wiederholung oder Folgeanfrage. Keine Aussage, dass die Verbindung
dauerhaft offen bleibt; Ursache nicht abschließend bestimmt. Ein Warten auf noch
angekündigte Bodybytes ist lediglich eine Hypothese. Tatsächliche Wirkung von
`maxSwallowSize=0` auf prompte Trennung damit nicht bestätigt. Keine eigenständige
Korrektur oder Lockerung der Schutzvorgaben.

Offen: tatsächlich empfangene Bytes unter/an/über 32 MiB, chunked Request-Bodies,
Chunk-Grenzen, CL-Duplikate/Konflikte und CL+TE, reale 400/415 und Erreichbarkeit
nach Ablehnung. HTML-Antworten waren chunked; das beweist keine chunked Requests.
Keine Laufzeitinstrumentierung für Aufrufzähler; fehlende Geschäftsänderung durch
vollständigen Snapshot nachgewiesen. Die geplante Browserabnahme war zusätzlich
blockiert, da kein In-app-Browser-Connector angeboten wird: keine UI-Speicherung,
413-Darstellung, Eingabenerhalt-/Replay-/Save-Sperrenprüfung oder Screenshots.
Keine Browserinstallation/native Ausweichlösung und keine positiven HTTP-Schreib-
Ersatzaktionen. Cookieattribute HttpOnly/SameSite=Strict und manueller Marker nur
auf HTTP-/HTML-Ebene bestätigt, nicht visuell oder durch echte Browserinteraktionen.

Rein lesende manuelle Vorher-/Nachher-Snapshots vollständig identisch:

`2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226`

Elf Reihen/Details, 28 Bände, fünf Einkaufspositionen/Details, 17 Stammdaten,
zehn Tabellen und alle Schema-Metadaten unverändert; keine neuen Geschäftsdaten,
kein Cleanup, AUTO_INCREMENT weiter ausgenommen. Keine Verbindung zu Produktiv-
oder automatisierter Testdatenbank. Eigene Instanz kontrolliert per Ctrl+C in
ursprünglicher PTY-Session 45472 beendet, geordneter Shutdown und freier Port 18080
bestätigt. Belege ignoriert unter `.gradle/t02-request-limit-http-20261016/`.

In diesem Auftrag nur TESTING.md und MANUAL_TESTING.md ergänzt; keine Änderungen
an Anwendungscode, Sicherheitsmechanismen, Limits, Abhängigkeiten oder Roadmap-
Prioritäten. Keine Live-Scraper/externe Abrufe, Migrationen, SQL-Schreibbefehle,
Staging-Aktionen, Commits, Pushes oder Merges. Fortsetzung und gegebenenfalls
gezielte Korrektur des Verbindungsverhaltens erst nach neuer Eigentümerfreigabe.

## T02 – Diagnose: frühe 413 und TCP-Verbindungsende statisch erklärt (16.10.2026)

Neuer Diagnoseauftrag ohne Produktionscodefreigabe. Die lokale **Tomcat-11.0.24-
Implementierung** erklärt die Beobachtung aus MANUAL_TESTING.md, Abschnitt 19;
vollständige Analyse und Quellenfundstellen in dessen neuem Abschnitt 20.
Keine neue Laufzeitmessung, kein Anwendungsstart, DB-Zugriff, Build oder Testlauf.
Die nur bei unzureichender statischer Erklärung erlaubten Varianten A/B/C wurden
nicht ausgeführt. Historische 678 Java-/198 DOM-Erfolge unverändert.

Nachgewiesener statischer Ablauf:

1. `RequestBodyLimitFilter` erkennt Content-Length 33.554.433 vor Öffnen des
   Servlet-InputStreams, schreibt neutralen 413-Text/Connection-close und kehrt
   zurück. Keine Wrapper oder Downstream-Controller auf diesem Pfad.
2. Tomcat kann die kleine Antwort bereits vollständig committen/ausliefern,
   bevor `Http11Processor.endRequest()` die Input-Seite abschließt.
   Der 413-Sonderpfad `Request.finishRequest()` deaktiviert Swallowing nur bei
   `swallowAbortedUploads=false`; Context-Default true, kein MangaLib-Override
   gefunden. Die alternative Option wäre allenfalls nach gesonderter Entscheidung
   über schnelleres Abbrechen zu bewerten, nicht in diesem Auftrag zu setzen.
3. `Connection: close`/`checkMaxSwallowSize()` setzen Keep-alive false, schließen
   den Socket aber nicht unmittelbar. HTTP-Status 413 und interner ErrorState
   sind unterschiedliche Zustände; Keep-alive false allein deaktiviert Swallowing
   im hier untersuchten `endRequest()` nicht.
4. `IdentityInputFilter.end()` versucht bei verbleibendem Body zunächst
   `buffer.doRead(this)`. Erst nach positiven Bytes prüft er die Swallow-Grenze.
   Der lokale Quellkommentar begründet den nicht sofortigen Abbruch ausdrücklich
   damit, dass der Client die Antwort empfangen können soll.
5. Ohne ReadListener kann dieser Zugriff bis Daten, EOF oder Read-Timeout warten.
   `maxSwallowSize=0` begrenzt das Swallowing, ist aber keine Null-Leseversuch-
   oder Zeitgarantie. Ein erster Pufferblock kann vor dem Abbruch gelesen werden.

Sources-JAR und tatsächlicher Bytecode lokal untersucht; `javap -c` bestätigt
die Reihenfolge in `IdentityInputFilter.end()`. Die Tomcat-Core-Bibliothek im
manuellen Artefakt ist bytegleich zur untersuchten Cache-Bibliothek (SHA-256
in MANUAL_TESTING.md). Ignorierte Belege unter
`.gradle/t02-connection-diagnosis-20261016/`; keine Downloads/Installationen.

Der vorherige Client misst mit `recv(MSG_PEEK)` grundsätzlich korrekt getrennt
von der HTTP-Antwort. `HTTPResponse.isclosed()` allein würde kein TCP-EOF beweisen.
Beim konkreten Response stimmen 60 empfangene Bytes und Content-Length überein.
Der Timeout bedeutet nur: kein EOF/Reset im 1-Sekunden-Fenster. Eine verpflichtende
sofortige TCP-Schließung bei angekündigtem, gar nicht übertragenem Body war eine
zu strenge Erwartung. Spätere Schließung, fünf Sekunden oder SHUT_WR nicht gemessen.

**Kein MangaLib-Produktionscodefehler nachgewiesen; Empfehlung: keine Änderung
allein aufgrund dieses Befunds.** Ein wartender Restbody-Abschluss kann Socket
und Connector-Worker binden; effektive Laufzeit/Parallelitätswirkung nicht gemessen.
Die konkrete Boot-Timeoutkonfiguration wurde nicht zur Laufzeit bestimmt.
Zeit-/Ressourcenbudgets bleiben separater T02-Auftrag; keine vorschnelle
Timeoutverkürzung, kein flushBuffer-/Socket-Fix ohne Nachweis und Freigabe.

Weiter offen: tatsächliche 32-MiB-Grenzbytes, chunked Requests/Chunk-Grenzen,
Headerkonflikte, reale 400/415, Erreichbarkeit nach Ablehnung und Browserabnahme
einschließlich 413-Formularverhalten. Die technische Teilabnahme ist nicht pauschal
abgeschlossen. Nur TESTING.md und MANUAL_TESTING.md ergänzt; keine Änderungen an
Anwendungscode, Konfigurationen, Guards, Limits, Datenmodell, Geschäftsregeln oder
Abhängigkeiten. Keine Datenbank-/SQL-Aktionen, Migrationen, Browser-/Scraperstarts,
Staging-Aktionen, Commits, Pushes oder Merges.

## T02 – Echte HTTP-Protokollabnahme: Bytegrenzen, chunked und Headerkonflikte (16.10.2026)

Neuer ausdrücklich freigegebener Lauf ausschließlich auf `manual-test` /
`mangalib_manual_test` mit manuellem Konto und 127.0.0.1:18080. Vollständige
19-Fälle-Tabelle mit Sendezählern, Schichten, Response-Headern, Verbindungsverhalten
und Grenzen: MANUAL_TESTING.md, Abschnitt 21. Historische 678 Java-/198 DOM-Erfolge
nicht erneut ausgeführt oder überschrieben; kein Produktionscode geändert.

Vorher Branch, Passwortpräsenz ohne Wertausgabe, fehlende Overrides, freie Ports/
Prozesse, Schutzkonfiguration und Artefakt geprüft. Vorhandenes manuelles JAR
aktuell und nicht gesperrt, Filter-/Konfigurationskompilate bytegleich zum JAR;
kein Neubau/Gradle-Aufruf erforderlich. Bestehendes Startskript `-ValidateOnly`
mit Java 21 erfolgreich. Lesender Ausgangssnapshot vor Start. Eigene PID 32320
in PTY-Session 17252 gestartet; Profil, Tomcat 11.0.24 und einziger Loopback-Listener
unabhängig bestätigt. Kein echter Scraper im Artefakt.

Python-Standardbibliothek-Client, feste lokale Zieladresse, begrenzter Speicher,
3-s-Connect-/5-s-I/O-Timeouts, sequenziell und ohne Retry. POST ausschließlich auf
nicht vorhandenem Probe-Pfad ohne gültigen CSRF-Token. Keine positive Geschäfts-
operation oder neue Fixture. 64-KiB-Produzentenblöcke und `{}` plus Whitespace
ermöglichen den Bytegrenztest ohne String-/Token-/Tiefenüberschreitung.

| Nachweisgruppe | Tatsächlich bestätigt |
|---|---|
| Vollständig gesendetes fest gerahmtes JSON | 2, 33.554.431 und 33.554.432 Byte passieren den Bodyfilter; danach erwartete CSRF-403. Kein fachlicher Erfolg behauptet. |
| Deklarierte 33.554.433 Byte | Frühe Filter-413 schon vor Payload; 0 Bodybytes gesendet, danach SHUT_WR und EOF. **Kein Nachweis eines vollständig übertragenen festen Limit+1-Bodys.** |
| Chunked Requests | Kleiner 16-Byte-Body mit Einbyte-Chunks innerhalb UTF-8/JSON-Tokens und exakter 32-MiB-Body passieren den Filter; danach 403. Finaler Null-Chunk jeweils gesendet. |
| Tatsächlich übergroßer chunked Request | 33.554.433 Payloadbytes, 33.559.050 Bytes einschließlich Chunk-Framing; vollständige Filter-413 und EOF. Keine Content-Length und keine Strukturüberschreitung als Ersatznachweis. |
| Strukturgrenze | String mit 4097 Zeichen bei insgesamt 4105 Bodybytes ergibt eigenständige Filter-413. |
| Transportformate | URL-encoded, Multipart und Content-Encoding gzip jeweils Filter-415; keine Entpackung. |
| Malformes JSON | Vollständige neutrale Filter-400 und EOF. |
| CL-Duplikate | Gleiche 0/0 und widersprüchliche 0/2 jeweils generische Tomcat-400 vor Security, vollständige HTML-Antwort/EOF. |
| CL+TE | Tomcat normalisiert auf chunked und deaktiviert Keep-alive; beobachtet **CSRF-403**, Connection: close und EOF. **Keine rohe Headerkonflikt-400 nachgewiesen.** |
| Keep-alive und Listener danach | Nach Negativfällen Home/Einkaufsliste vollständig mit 200 auf derselben Verbindung; abschließender GET Home ebenfalls 200, Marker und EOF bei angefordertem close. |

19 HTTP-Fälle: **17 bestanden, 1 Teilnachweis (frühe feste Limit+1-Ablehnung),
1 erklärte Abweichung (CL+TE gegenüber strikter 400-Annahme)**. Statusverteilung
4 × 200, 3 × 400, 6 × 403, 3 × 413, 3 × 415. Alle Antworten vollständig;
keine 500, unerklärlichen Reaktionen oder Timeouts. Filter-400/413/415 jeweils
exakter neutraler Text, UTF-8 text/plain, Content-Length 19/60/35 und Connection:
close; EOF tatsächlich beobachtet. Tomcat-400 separat: generische HTML-Antwort
mit 435 Byte/Connection-close. Keine erkannten internen Exception-/Stacktrace-
Indikatoren. Keine Sessions/CSRF-/Passwortwerte in Nachweisen gespeichert.

CL+TE war durch die vorab gelesene lokale `Http11Processor.prepareInputFilters()`-
Implementierung erklärbar: Der entfernte CL-Header erreicht den Servletfilter
nicht. Die bestehende Mock-Prüfung gleichzeitig sichtbarer Header ist kein
Nachweis einer strikten rohen Connectorablehnung. Mit gültigem CSRF nicht
getestet, kein Smuggling oder Geschäftsaufruf bewiesen. Falls pauschal 400 für jede
rohe CL+TE-Kombination verlangt wird, Anforderung und geeignete Connectorgrenze
separat bewerten; keine Korrektur/Override jetzt. Keine Erweiterung zu Proxy-/
Pipelining-Angriffen. Die gemessene Schließung nach SHUT_WR ersetzt keine Aussage
über spontan fehlendes EOF bei offen gehaltener unvollständiger Sendeseite.

Vollständige lesende Daten-/Schemasnapshots vorher/nachher identisch:

`2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226`

Elf Reihen/Details, 28 Bände, fünf Einkaufspositionen/Details, 17 Stammdaten,
zehn Tabellen, 75 Spalten, 24 Indexeinträge, zwölf FKs und 26 Key-Column-Einträge
unverändert, keine neuen Geschäftszeilen. AUTO_INCREMENT ausgenommen; keine
Bereinigung/SQL-Schreibaktion. Eigene PID 32320 kontrolliert per Ctrl+C in
ursprünglicher Session beendet, geordneter Shutdown, Prozessende und freier
Port 18080 bestätigt. Belege ignoriert unter `.gradle/t02-http-protocol-20261016/`.

In-app-Browser-Connector zu Beginn nicht angeboten: Browsernachprüfung ausdrücklich
blockiert, keine neuen Tools oder HTTP-Ersatzwrites. Offen: 413-UX, Dialoge,
Eingabenerhalt, Save-Sperre/kein Replay; ferner Entscheidung zu strikter CL+TE-
Ablehnung, Ressourcen-/Zeit-/Parallelitätsbudgets und Scraper-/SSRF-Schutz.
T02 insgesamt nicht abgeschlossen. Nur TESTING.md/MANUAL_TESTING.md ergänzt,
vorhandene Änderungen erhalten; keine Anwendungscode-/Guard-/Limitänderung,
Produktiv-/mangalib_test-Verbindung, Migrationen, externe Abrufe, echten Scraper,
Installationen, Staging-Aktionen, Commits, Pushes oder Merges.

## T02 – Scraper-Parallelität/Lifecycle: Analyse und Architektur-Stopp (16.10.2026)

**Keine Implementierung und keine neue Testausführung.** Die verbindliche
Eigentümer-Stoppregel lautet: „Falls sich die zentrale Parallelitätsgrenze nicht
zuverlässig umsetzen lässt, ohne eine möglicherweise noch aktive Firefox-Instanz
fälschlich als beendet zu betrachten, stoppe vor dieser Änderung und stelle
konkrete Lösungsalternativen zur Entscheidung vor.“ Dieser Punkt ist erreicht:
ein Java-Semaphore allein ist klein, der geforderte tatsächliche Ressourcen-
Endnachweis ist mit dem bisherigen Rückgabevertrag der Scraper nicht vorhanden.
Es wird deshalb keine vermeintlich sichere automatische Freigabe implementiert.
Historische 678 Java-/198 DOM-Erfolge und die HTTP-/Browserberichte bleiben erhalten.

### Tatsächlich vorgefundene Pfade und belegte Lücken

| Pfad / konkrete Fundstelle | Bestehender Schutz und Lücke |
|---|---|
| `MainController.scrapeManga`, Zeilen 259–303 | POST/Host/Origin/CSRF und positive Indexprüfung; Gateway vor Referenzlookups. Unerwartete Exceptions als neutraler leerer 500-Response. Kein gemeinsamer Live-Auftragsslot. |
| `MainController.autofillBandData`, Zeilen 444–458; `BandService.autofillBandData`, Zeilen 598–619 | Index-/Importvalidierung, bestehender Payload einschließlich Preis-/mpUrl-Aufbereitung. Delegation über denselben Gatewaytyp, aber unabhängiger tatsächlicher Browserstart. |
| `LiveScraperGateway`, Zeilen 8–16 | Reguläres `!manual-test`-Bean; delegiert unmittelbar an beide statischen Scraper. Keine Synchronisation, Warteschlangen-/Ressourcensteuerung oder Shutdownverwaltung. |
| `MangaScraper.scrapeMangaData`, Zeilen 22–35,250–263,274–281 | `setupWebDriver()` vor dem try/finally. Bei folgenden Navigations-/Extraktionsfehlern wird quit versucht; Startfehler liegen außerhalb dieses finally. Quitfehler werden verschluckt, danach Rückgabe auch partieller Daten. Stacktrace, Datenfelder/URLs und vollständige Ergebnismap werden roh ausgegeben. |
| `BandScraper.scrapeBandData`, Zeilen 24–35,103–110,243–247 | Ebenfalls Konstruktor vor try/finally und verschluckte Quitfehler. Stacktrace/Exceptiontexte und extrahierte Daten in Rohlogs. Keine verlässliche Cleanup-Ergebnisinformation für einen äußeren Slotverwalter. |
| Beide öffentlichen statischen Scraper-Einstiege und jeweilige `main` | Direkte Aufrufe möglich. Eine allein am Spring-Gateway angebrachte Sperre würde diese Pfade nicht schützen. Keine vorhandenen eigenen Lifecycle-Shutdownhooks gefunden. |
| `MangaScraper`, Zeilen 139–141,190–193 | 150-ms-Pause je Band; InterruptedException fällt in breiten Exception-Catch. Unterbrechung ist kein zuverlässiger Auftragsschluss und kann hier als Einzelbandfehler behandelt werden. Keine neue Interrupt-/Abbruchregel umgesetzt. |

Beide Scraper nutzen einen festen Geckodriverpfad, Headless-Firefox und feste
Manga-Passion-Pfadpräfixe. Das ist keine Prozessgruppenverwaltung oder SSRF-Grenze.
Die manuelle Variante bleibt ein synthetisches Gateway ohne Live-Fallback; kein
Anlass, Test-/Manual-Guards oder Profile für diesen Auftrag zu verändern.
Frontend-Scrape/Autofill verwenden `persist:false`, keine automatische Wiederholung
im gemeinsamen Fetch-Helfer. Rohes Frontend-Autofill-Logging existiert weiterhin;
ein neuer Busy-Vertrag würde gezielte Feedback-/DOM-Prüfungen benötigen.

### Lokale Selenium-Nachweise und die entscheidende Grenze

Kein Download oder Dependency-Update. Die lokal vorhandene Boot-4.1.1-BOM verwaltet
Selenium-Kernmodule 4.43.0; der bestehende explizite Aggregator 4.18.1 bleibt der
bereits dokumentierte Versionsmix. Lokale 4.43.0-Sources von Firefox-, Remote-,
OS-, HTTP- und Supportmodulen wurden gelesen. Dies ist keine neue Gradle-Auflösung
oder Live-Kompatibilitätsprüfung. Extrahierte Quellen ausschließlich ignoriert
unter `.gradle/t02-scraper-lifecycle-analysis-20261016/`.

- `FirefoxDriver` startet über DriverCommandExecutor eine Session; nach dem
  RemoteWebDriver-Superkonstruktor folgen weitere Initialisierungen. Ein fehlender
  Rückgabewert des Java-Konstruktors beweist nicht, dass kein Browser gestartet ist.
- `DriverService.start()` enthält bereits eigene Start-/Fehlerbereinigung;
  `DriverCommandExecutor` versucht nach bestimmten NEW_SESSION-Fehlern ebenfalls
  `service.stop()`. Es wird **nicht** behauptet, Selenium habe keinerlei Schutz.
  Dieser Schutz meldet jedoch kein vollständiges Firefox-Prozessgruppenende.
- `RemoteWebDriver` versucht bei bestimmten Konstruktor-RuntimeExceptions quit;
  ohne Session-ID ist quit ein No-op. Die zusätzliche Bibliotheksbereinigung
  ersetzt daher nicht den geforderten sicheren Nachweis bei allen Startfehlern.
- `DriverService.isRunning()` prüft nur das gehaltene ExternalProcess des
  **Driverservers**. `DriverService.stop()` setzt seine Prozessreferenz im finally
  auf null, auch wenn Beendigung scheitert. Ein anschließendes false ist kein
  unabhängiger Nachweis, dass Firefox einschließlich Kinder beendet ist.
- `ExternalProcess.shutdown()` beendet/erzwingt den eigenen Root-Prozess, wartet
  begrenzt und behandelt InterruptedException intern. Der Rückgabewert des letzten
  waitFor wird nicht als vollständiger Erfolgsbeleg ausgewertet. Keine vollständige
  Firefox-Kindprozesszuordnung/-beendigung in diesem Pfad gefunden.
- `RemoteWebDriver.quit()` löscht die Session-ID im finally. Eine verschwundene
  Session-ID ist deshalb ebenfalls kein tatsächlicher Prozess-Endnachweis.

Ein `finally { semaphore.release(); }` wäre folglich falsch. Auch
`quit() erfolgreich && !service.isRunning()` oder eine momentane
`ProcessHandle.descendants()`-Liste schließen unzugeordnete/verwaiste Kinder nach
Startfehlern nicht zuverlässig aus. Eine nachträgliche PID-Momentaufnahme hat
Rennen mit Start/Exit/Umhängung; keine fremden Prozesse aufgrund Namenssuche beenden.
Synthetische Lifecycle-Fakes könnten eine Slot-Zustandsmaschine beweisen, aber
keine fehlende Produktionsgarantie über echte Prozesse ersetzen.

### Zeitlimits: Bestand und mögliche nächste Änderungen

| Phase | Nachgewiesener Ist-Stand / Reichweite | Erst nach Entscheidung zu prüfen |
|---|---|---|
| Driverstart | Eigene Anwendung konfiguriert keine Startfrist. Lokaler DriverService-Builder default 20 s; Start verwendet interne Futures mit teils doppelter Frist plus Cleanup. Kein harter 20-s-Gesamtprozessdeckel. | Explizite Service-Startfrist, Fehlerpfad ohne verloren gegangenen Owner; keine Browserstartgarantie durch bloßes Timeout. |
| Navigation | `driver.get()` ohne eigenes pageLoadTimeout. Ein erst danach angelegter WebDriverWait begrenzt die Navigation nicht. | Beispielsweise explizites pageLoadTimeout 30 s; Protokollfrist, keine garantierte Prozessbeendigung. |
| DOM/Extraktion | WebDriverWait je 20 s, mehrfach verwendbar; FluentWait prüft erst **nach** Rückkehr der Bedingung. Einzelne blockierte WebDriver-Commands können länger dauern; Band-Schleifen ohne eigene Gesamtdauergrenze. | Wait 20 s beibehalten, HTTP-/Command-Readfristen explizit bewerten, keine impliziten Warteketten hinzufügen. |
| Transport | Lokaler ClientConfig default 10-s-Connect/180-s-Read, über JVM-Properties beeinflussbar. Dies sind Bibliotheksdefaults, keine live gemessenen Projektgarantien. | Etwa 10-s-Connect/45-s-Read und ggf. 10-s-Scriptfrist als Entwurf; Tests müssen Konfiguration/Reichweite unterscheiden. |
| Quit/Cleanup | DriverCommandExecutor nutzt Servicefrist für QUIT-/Prozess-Futures. ExternalProcess wartet/beendet den Root-Prozess; weder Kinderende noch harte gesamte Cleanupdauer damit belegt. | Nur eigener Owner; Quarantäne bei fehlender Bestätigung. Keine Freigabe aus Cancel/Interrupt ableiten. |
| Gesamtdauer/Shutdown | Keine Anwendungsgesamtfrist oder eigene geordnete Scraper-Shutdownsteuerung. | Harte Frist benötigt verlässliche OS-Prozesszuordnung; kein `Future.cancel(true)` als angeblicher Browserkiller. |

Alle genannten neuen Zahlen sind **Vorschläge, nicht implementierte Limits**.
Ein kurzer HTTP-Readtimeout begrenzt nicht automatisch Firefox-Netzwerkzugriffe,
Redirects, DNS oder Unterressourcen. SSRF bleibt ausdrücklich ein anderer Auftrag.

### Konkrete Entscheidungsalternativen

**A – Kleine In-Prozess-Begrenzung mit dauerhaft konservativer Quarantäne.**
Gemeinsamer nicht wartender Owner für beide statischen Einstiege; Zustände
IDLE/RUNNING/QUARANTINED/CLOSING. Keine pauschale finally-Freigabe. Bei fehlender
vollständiger Cleanupbestätigung bleibt der Owner gesperrt, auch wenn quit keinen
Fehler geworfen hat. Im vorhandenen Stack kann das bereits nach einem ansonsten
erfolgreichen Import zur dauerhaften Sperre weiterer Imports führen. Keine
automatische Entsperrung oder Reset-HTTP-API. Wiederaufnahme erst nach gesondert
bestätigter Bereinigung; ein App-Neustart allein ist kein Nachweis über Altprozesse.
Grob 1–2 Entwicklungstage einschließlich deterministischer Tests, keine neue
Dependency notwendig. Vorteil: kleiner sicherer Zwischenstand; Nachteil: reduzierte
Verfügbarkeit, keine harte Gesamtfrist und weiterhin mögliche Prozessreste beim
Anwendungsende. Diese Einschränkung braucht eine ausdrückliche Produktentscheidung.

**B – Eigener Scraper-Worker mit OS-seitig kontrollierter Prozessgruppe.**
Für zuverlässige automatische Wiederaufnahme/harten Abbruch empfohlen, wenn die
strenge Ressourcen-Endbedingung unverändert gelten soll. Ein Worker-JVM-Prozess
allein genügt nicht: unter Windows beispielsweise Job Object mit Zuordnung **vor**
Kindprozesserzeugung und ohne unkontrolliertes Breakaway. Nur eigene Gruppe
beenden; Slot erst bei bestätigtem Gruppenende und abgeschlossenem Worker freigeben.
Pro Auftrag eigenes Temp-Profil und nachvollziehbarer Ressourcenbesitz; begrenzte
IPC-/Ergebnismengen, keine automatische Wiederholung. Native Anbindung/Launcher,
Fehlerpfade, Paketierung und Prozessidentität vorher entwerfen und freigeben;
keine unbekannte Bibliothek installieren. Grob 3–5 Entwicklungstage plus gesonderte
isolierte Prozessabnahme, abhängig von verfügbarer Windows-Anbindung. Vorteil:
belastbare Ownership und durchsetzbare Gesamtfrist; Nachteil: größerer Architektur-
und Testumfang. Weder Worker noch OS-Anbindung in diesem Auftrag implementiert.

### HTTP-/Frontend-Entwurf und Testplan nach Freigabe

Vorgeschlagener kleiner Vertrag: 429 für bereits laufenden Import, fester neutraler
Text ohne Request-/Exceptiondetails, keine erfundene Retry-After-Zeit; kein Queueing.
Quarantäne/Shutdown getrennt mit neutralem 503 behandeln. Keine Überladung der
vorhandenen manuellen `ScraperRequestRejectedException`/400-Semantik. Zwei gezielte
Controller-Catches vor allgemeinen Catchblöcken und passende Frontend-Meldung nötig;
429 ist derzeit unbekannt und würde irreführend die generische unbestätigte Aktion
anzeigen. Keine neuen Statuscodes/Frontendänderungen jetzt umgesetzt. Bestehende
Erfolgspayloads/Preisaufbereitung, POST/CSRF und Fehlerverträge bleiben unverändert.

Erforderliche datenbankfreie Nachweise nach der Entscheidung:

1. Erfolgreicher Einzelauftrag und Freigabe ausschließlich mit vollständig
   bestätigtem Cleanup; alle vorhandenen Manga-/Band-Erfolgspayloads erhalten.
2. Zwei konkurrierende Manga-Aufträge und Manga/Band-Konkurrenz mit Latches statt
   Sleeps: genau ein Start, zweiter sofort abgewiesen, keine Repositoryarbeit.
3. Fehler vor möglichem Start versus unklarer teilweise gestarteter Auftrag;
   unbekannte Ressourcen führen zur Quarantäne und nicht zum zweiten Driverstart.
4. Navigation/Extraktion/quit/Cleanupfehler; keine Rohlogs, partiellen Fehler-
   Erfolgsantworten oder Auto-Replays. Bestehende fachliche Extraktionsfallbacks
   von echten Auftragsfehlern gezielt abgrenzen, nicht nebenbei umdefinieren.
5. Blockierter Auftrag/Interrupt/Timeout/Shutdown: Slot bleibt belegt, keine
   doppelte Freigabe oder parallele Quit-Aktion; keine fremde Prozessbeendigung.
6. Standalone-MockMvc mit aktiven Sicherheitsfiltern: kontrollierte 429/503 vor
   Referenz-/Facharbeit, neutrale Texte, gültige JSON-Verträge; DOM mit erhaltenen
   Eingaben, klarer Busy-/Quarantänemeldung und ohne automatische Wiederholung.
7. Manuelles Artefakt weiterhin frei von Live-Scraperbestandteilen; keine neuen
   Beans/Typreferenzen, die fehlendes Selenium im manuellen Profil nachladen.

Vor jedem zukünftigen Gradle-Testlauf konkreten --dry-run prüfen. Keine neuen Tests
hinzugefügt oder ausgeführt, weil die produktive Cleanup-Evidenz und damit das
Freigabekriterium vor Implementierung entschieden werden müssen. Kein Test wird
als Ersatz für diesen fehlenden Nachweis ausgegeben. Die 29 MVC-/JSON-Verträge,
M06- und Altbestandsregeln wurden weder geändert noch erneut geprüft.

### Arbeitsstand und Grenzen

Nur TESTING.md und ROADMAP.md ergänzt. Zentraler Gate-/Lifecycle-Code, neue
HTTP-Statuscodes und Timeouts ausdrücklich **nicht implementiert**. Keine
DB-Verbindung/SQL-Aktion zu irgendeiner MangaLib-Umgebung, kein Build/Test,
Anwendungs-/Browser-/Firefox-/Geckodriver-/Live-Scraperstart, kein Download oder
neue Dependency. Keine Guards, Profile, Sicherheitsfilter, Fachregeln, T03/T04/T06
oder Daten verändert. Keine Git-Staging-Aktionen, Commits, Pushes oder Merges.
Weitere Arbeit erst nach Eigentümerentscheidung zu A oder B.

Aktualisierte Eigentümerentscheidung: **strikte rohe CL+TE-Ablehnung wird vorerst
nicht als eigene Implementierungsaufgabe verfolgt**. Lokale Einschränkung weiter
dokumentiert; bei späterem Proxy-/LAN-Betrieb neu bewerten. Frühere offene Hinweise
in den historischen Nachträgen bleiben erhalten, sind keine aktuelle Anweisung
zur vorgezogenen CL+TE-Implementierung.

## T02 – Variante B: Windows-Worker, Machbarkeit und Architekturplan (16.10.2026)

**Eigentümerentscheidung:** isolierter Scraper-Worker mit kontrollierter
OS-Prozessgruppe; keine dauerhaft blockierte In-Prozess-Lösung als Normalbetrieb.
Die Architekturentscheidung ist freigegeben, ihre vollständige Implementierung
ausdrücklich **noch nicht**. Der vorige Stopp-/Alternativenbericht bleibt historisch.
Dieser Auftrag ändert nur Dokumentation und führt zwei eng begrenzte native
Machbarkeitsproben ohne Prozessstart/-zuordnung aus. Kein Scraper-/Workerproduktcode.

### 1. Nachgewiesen, plausibel und noch offen

Branch aus `.git/HEAD` lesend bestätigt: `codex/t02-security-hardening`.
Keine Git-Befehle/-Operationen ausgeführt. Bestehender Arbeitsstand erhalten.
Anwendungspfad unverändert: MainController `/scrape` → ScraperGateway;
`/autofillBandData/{bandIndex}` → BandService → ScraperGateway. LiveScraperGateway
delegiert bisher direkt an die statischen Scraper; manuell eigener synthetischer
Gateway ohne Live-Fallback. Relevante Stellen: `LiveScraperGateway.java:8–16`,
`MainController.java:259–303,444–458`, `BandService.java:598–619`,
`build.gradle.kts:46–95`, `ManualTestLauncher.java:68–112`.

Lokale Sichtung von Java-21-src.zip/javap, vorhandenen Selenium-Quellen, gecachten
JNA-Bibliotheken/POMs und installierten Werkzeugpfaden; keine Onlineabrufe oder
Downloads. An üblichem Pfad kein Windows-SDK-Include-Verzeichnis gefunden;
cl/clang/gcc nicht im aktuellen PATH, dotnet vorhanden. Das beweist nicht, dass
auf dem Rechner keinerlei C-Toolchain installiert ist. Windowsversion der Probe
10.0.26200.0, 64-Bit-Prozess.

**Zwei tatsächlich erfolgreiche technische Proben, keine JUnit-/Gradle-Suite:**

1. `empty_job_probe.py`: eigenes unbenanntes leeres Job Object erzeugt,
   `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` gesetzt und zurückgelesen, beide Breakaway-
   Flags aus, ActiveProcesses und TotalProcesses jeweils 0. Handle geschlossen.
   Die benötigten Kernel32-Exporte sind erreichbar. Der Probeprozess selbst ist
   bereits Mitglied eines bestehenden Jobs: Nested-Job-Kompatibilität ist relevant.
2. `job_attribute_probe.py`: Attributspeicher initialisiert,
   `PROC_THREAD_ATTRIBUTE_JOB_LIST` mit genau einem eigenen leeren Jobhandle über
   UpdateProcThreadAttribute vorbereitet, Attributliste gelöscht/Handle geschlossen.
   **Kein CreateProcess/AssignProcess/ResumeThread/TerminateJobObject aufgerufen.**

Belege ausschließlich ignoriert unter `.gradle/t02-worker-feasibility-20261016/`,
einschließlich beider Skripte/JSON-Ergebnisse. Kill-on-close wurde als Flag geprüft,
**nicht** durch Töten eines Prozesses. Akzeptierte JOB_LIST-Metadaten beweisen
**noch keine** erfolgreiche atomare Prozesserzeugung, Nested-Zuordnung oder
Firefox-Kompatibilität. Keine tatsächliche Prozessgruppenbereinigung gemessen.
Die folgenden Win32-Abläufe sind der zu prüfende Implementierungsentwurf;
lokal fehlen vollständige SDK-/Microsoft-Referenzen. Vor produktiver Freigabe
API-Verträge gegen offizielle Windows-Referenzen und einen isolierten nativen
Prototyp bestätigen, ohne die jetzige Freigabe auf externe Abrufe auszudehnen.

### 2. Windows-Integrationswege und Empfehlung

| Weg | Eignung | Kosten/Risiken |
|---|---|---|
| **JNA direkt im MangaLib-Supervisor, empfohlen** | Eigener kleiner Win32-Adapter; kein zusätzliches Brokerprogramm, kein Preview-Java. Job/Prozess/Pipe-Handles bleiben beim Supervisor. | Explizite direkte Dependency muss erst freigegeben werden; 64-Bit-ABI-/Struct-/Handle-Mapping korrekt testen. Native Fehler können den Host betreffen. Kein fertiger vollständiger Jobadapter in den geprüften Kernel32-Mappings. |
| Kleiner eigener nativer C/C++-Launcher | Win32-Header/Compiler prüfen Typen; native Details aus Java ausgelagert, kein JNA-JAR. | Eigenes reproduzierbares Windows-Binary/Build-/Signatur-/Updateverfahren. Zusätzlicher Launcher-Lebenszyklus und Parent-Tod-Watchdog mit echtem Parenthandle, sonst neue Orphan-Lücke. Compiler/SDK noch zu klären. |
| Java 21 Foreign Function & Memory | Keine zusätzliche JNI/JNA-Bibliothek; System-Kernel32 direkt erreichbar. | Unter Java 21 Preview-API und Native-Access-/Start-/Compileflags, selbst gepflegte ABI-Layouts. Passt schlechter zur unveränderten stabilen Java-21-/Paketierungsbasis. Nicht empfohlen. |
| PowerShell/C# PInvoke pro Auftrag | Theoretisch ohne zusätzliches JAR, vorhandene Windows/.NET-Werkzeuge. | Zusätzliches Skript-/Host-/IPC-/Elternüberwachungsproblem, Quoting, Policies und Kompilierung. Als lokale API-Probe geeignet, als dauerhafter Produktstartweg weniger übersichtlich. |

JNA 5.17.0 und jna-platform 5.17.0 liegen im Cache. Ein vorhandener
WebDriverManager→docker-java-transport-httpclient5-POM-Pfad referenziert JNA 5.13.0;
das ist **keine neue vollständige Dependency-Auflösung und kein Beleg für eine
direkte deklarierte Projekt-Dependency**. Nicht auf einen zufälligen Transitiven
vertrauen. Bei Freigabe direkte, kompatibel aufgelöste JNA-Version deklarieren;
keine ungeprüfte zweite JNA-Version danebenlegen. Die geprüfte jna-platform-
Kernel32-Schnittstelle enthält die Job-/Startattributfunktionen nicht vollständig;
ein kleiner eigener Unicode-/Win64-Adapter bleibt nötig. jna-platform ist dafür
nicht zwingend nötig; JNA-Kern plus wenige eigene geprüfte Typen bevorzugen.
Keine Bibliothek in diesem Auftrag hinzugefügt oder installiert.

### 3. Race-armer Start und Job-Eigentum

Bevorzugtes Ziel für den Windows-10/11-Betrieb:

1. Globalen Auftrags-Owner atomar belegen, ohne Warteschlange. Pro Auftrag neues
   unbenanntes Job Object, nicht vererbbares Handle, Kill-on-close; eigene Flags
   BREAKAWAY_OK und SILENT_BREAKAWAY_OK **nicht** setzen.
2. Private Standard-I/O-Pipes und STARTUPINFOEX mit expliziter Handle-Allowlist
   vorbereiten. Nur die benötigten Child-Pipe-Enden vererben, niemals Jobhandle,
   Prozess-/Threadhandles oder beliebige offene Apphandles.
3. Worker über **CreateProcessW mit JOB_LIST-Attribut**, EXTENDED_STARTUPINFO_PRESENT,
   zusätzlichem CREATE_SUSPENDED und eigenem eingeschränktem Environment erzeugen.
   Jobzuordnung soll damit Bestandteil des Erzeugungsaufrufs sein, bevor Worker-
   Code laufen kann. Keine ProcessBuilder-Erzeugung mit nachträglichem PID-Scan.
4. Nur nach erfolgreichem Erzeugungs-/Zuordnungsnachweis und gültigen eigenen
   Handles den Primärthread fortsetzen. Fehlgeschlagene Zuordnung bedeutet
   Abbruch, niemals einen ungeschützten Fallbackstart.
5. Der Worker startet Geckodriver und Firefox als normale Kinder. Sie sollen den
   eigenen Job erben; der Supervisor bleibt außerhalb dieses neuen Jobs und wird
   nie zusammen mit seiner eigenen Jobgruppe beendet.

Alternative CREATE_SUSPENDED → AssignProcessToJobObject → ResumeThread verhindert
ausführbare Kindstarts vor der Zuordnung, hat aber eine **Crashlücke zwischen
Erzeugung und Zuordnung**: ein suspendierter Prozess kann zurückbleiben. Deshalb
kein stiller Fallback für die strengere Zielgarantie; fehlendes JOB_LIST oder
inkompatible Umgebung zunächst kontrolliert ablehnen. Nicht nur Namen/PIDs merken:
echte eigene Handles und Lifecycle-Ticket bis zum bestätigten Ende halten.

Nested Jobs sind auf modernen Windows-Versionen grundsätzlich ein möglicher
Mechanismus, aber keine uneingeschränkte Kompatibilitätszusage. Bestehende
Ancestor-Limits/UI-Restriktionen, fehlende Rechte, Breakaway-Politik, Security-
Software und Firefox-eigene Sandboxjobs können Zuordnungen oder Starts scheitern
lassen. Die jetzige Probe beweist nur leere Jobverwaltung im vorhandenen Kontext.
Vor Integration zwingend Dummy-Kind/Enkel unter bereits vorhandenem Job testen;
Firefox später gesondert mit lokalen Seiten und echter Freigabe prüfen.
**Keine Sandboxabschaltung und kein Breakaway als vermeintlicher Reparaturweg.**

### 4. Kleine Komponenten und Paketierung

| Geplante Komponente | Verantwortung / Schnittstelle |
|---|---|
| `LiveScraperGateway` | Öffentliche bisherigen Methoden/Map-Rückgaben erhalten; delegiert an genau einen Supervisor. Keine Worker- oder Seleniumarbeit im Bean-Konstruktor. |
| `ScraperSupervisor` | Zustandsmaschine IDLE/STARTING/RUNNING/CLEANING/CLOSING/QUARANTINED; ein Ticket für Reihen und Bände; Start-/Gesamtdeadline und vollständiger Cleanup vor Ergebnisrückgabe/Slotfreigabe. |
| `WindowsJobProcess` / `WindowsNativeApi` | Schmaler austauschbarer Adapter für CreateProcess/Job/Pipes/Wait/Terminate/Close; Fake-Schnittstelle ohne native Nebenwirkungen für Unit-Tests. |
| `ScraperProtocol` | Versionierte strikt begrenzte Request-/Responseframes; keine Entities, Spring-Kontexte, Java-Serialisierung oder polymorphe Typnamen. |
| `ScraperWorkerMain` | Ein Auftrag pro Prozess, keine Wiederverwendung; kein SpringApplication, JDBC, JPA, HTTP-Listener oder Standardkonfigurationsladen. |
| Worker-Extraktion/Lifecycle | Vorhandene Manga-/Band-Selektoren, Map-Schlüssel, Preisstrings und fachliche optionale Fallbacks möglichst unverändert nutzen; eigener Driver-Lifecycle mit Fehler-/Quitbehandlung. |

Empfohlen ist ein separates Gradle-SourceSet/kleines Worker-Artefakt mit expliziter
Laufzeit-Allowlist und Main-Class. Nicht das normale Spring-Boot-JAR mit freiem
`--worker`-Profil starten: das würde DB-/Autokonfiguration unnötig mitnehmen.
Worker benötigt nur Extraktion, Selenium und notwendiges JSON/Protokoll; weder
Anwendungs-properties noch Repositories, DB-Treiber oder WebDriverManager als
automatischer Downloadpfad. Vorhandene lokale geprüfte Java-21-/Firefox-/Gecko-
Programme verwenden, keine über HTTP gelieferten Executable-/Classpath-/Pfadwerte.

Die beiden heutigen Scraperklassen gehören ausschließlich in das Worker-Artefakt;
keine weiterhin öffentlich ausführbaren Live-`main`-/Static-Bypasspfade im Web-JAR.
Gleiche interne Namen/Extraktionslogik sind weiter nutzbar. Test-Compilepfade für
bestehende statische Mockito-Mocks müssen gezielt angepasst werden; deren heutige
Gateway-Delegationsannahme ist eine Implementierungsannahme, kein JSON-Vertrag.
Tests auf den Supervisor-/Gateway-Seam umstellen, **Assertions über Daten/HTTP
nicht abschwächen**. Keine fachlichen Controller-/Service-Refactorings.

Manual: Shared-ScraperGateway bleibt ohne native/Worker-Typreferenzen. Das manuelle
JAR enthält nur SyntheticScraperGateway und die vorhandenen manual-test-Komponenten;
Workerklassen, nativer Adapter, Workerartefakt, Selenium und erforderlichenfalls
JNA-JARs/Launcherdateien ausdrücklich aus seiner Paketierung ausschließen. Ein
Profil allein genügt nicht. Vorhandene Artefakt-/Startschutztests erhalten und um
negative Worker-/Native-Ressourcenprüfungen ergänzen; DB-Guard unverändert.
Kein Dummy-Worker wird beim manuellen Anwendungsstart benötigt oder gestartet.

### 5. IPC und Verträge

Nur private Pipes, kein zusätzlicher TCP-Port/Broker/Hintergrunddienst/DB.
Request z. B. `{version:1, requestId, operation:SERIES|VOLUME, index}`: Index wie
bisher positiver Integer, Typenum und feste Version; keine URL, Pfade, Javaargs,
Workerklasse oder Timeoutwerte aus dem HTTP-Request übernehmen. Request-ID dient
Zuordnung, ist kein Ersatz für eine private Transportgrenze.

Ein Worker liefert höchstens einen längengerahmten UTF-8-Response mit passender
ID/Version, SUCCESS plus `Map<String,String>` oder festem Fehlercode. Bounds vor
Allokation/Parsing: Request etwa 1 KiB, Ergebnisrahmen zunächst **32 MiB als
Prüfkandidat**, begrenzte Tiefe/Strings/Einträge, keine Duplikate oder Zusatzframes.
Diese Werte sind noch keine implementierten Regeln. Ergebnisgrenze gegen bisherige
10.000-Bände-/Unicode-/Escape-Fälle prüfen, nicht blind mit dem HTTP-Bodylimit
gleichsetzen. Parent prüft anschließend weiter `WriteValidation.scrapedData`.
Keine automatisch erfundenen NULL-/Preis-/Metadatenwerte.

Da Native-CreateProcess kein Java-ProcessBuilder-Process zurückgibt, die Pipe-
Handles über den kleinen nativen Adapter lesen/schreiben, **keine reflektiven
FileDescriptor-Hacks oder neuen --add-opens-Flags**. Kleine Requestgröße passt in
bewusst dimensionierten Pipepuffer. Ergebnis und stderr kontinuierlich getrennt
in höchstens zwei begrenzten I/O-Readern pro Auftrag bearbeiten; keine blockierende
Wait-before-drain-Kette. Nach Kill/Jobende müssen alle Child-Schreibhandles geschlossen
sein, damit Reader EOF erreichen. Alle unnötigen Parent-Pipe-Enden früh schließen.
Kein Thread-Cancel als Ersatz für Beenden der Worker-Prozessgruppe.

stdout ausschließlich Protokoll. Die bisherigen System.out-/Stacktrace-/vollständigen
Map-Rohlogs in den beiden Scraperpfaden gezielt entfernen oder vom Protokoll
fern halten; stderr nur begrenzt drainen, keine fremden Rohtexte in App-/HTTP-Logs.
Beispielbudget 64 KiB Aufbewahrung, danach Verwerfen; zusätzlich Gesamtoutputbudget
mit Abbruch gegen endlosen Output prüfen. Keine unbeschränkten Dateien/Bytearrays.

Erfolgsverträge bleiben außen unverändert: `/scrape` fügt weiterhin die bestehenden
Referenz-IDs hinzu; BandService entfernt das Eurozeichen und ergänzt mpUrl wie bisher.
Fehlerhafte Indizes/Importdaten bleiben 400, unerwartete Start-/Timeout-/Protokoll-
fehler weiter neutraler bestehender 500-Vertrag; kein zwingender neuer 504 nötig.
Nur Busy benötigt vorgeschlagen gezielt 429 mit verständlicher Frontendmeldung,
ohne automatisches Retry/erfundene Retry-After-Zeit. Ein eigener 503-Vertrag ist
optional und nicht Voraussetzung; bisheriger 500 bleibt der konservative Vorschlag.
Erfolgsmap und Payload nach Fehlern niemals aus Rohlogs rekonstruieren.

Die heutigen Scraper verschlucken auch Haupt-Navigationsfehler und können dann
partielle Maps liefern. Für den Worker sind solche technischen Auftragsfehler
explizit zu melden, während optionale Feld-/Layoutfallbacks erhalten bleiben.
Das kann bisherige scheinbare 200-Teilerfolge in neutrale 500 überführen und muss
als gewollte technische Fehlerkorrektur mit Tests freigegeben werden; keine
stillschweigende neue fachliche Mussfeldregel.

### 6. Lebenszyklus, Deadlines und Sicherheitsinvarianten

1. Genau ein Prozessgruppen-Owner pro Anwendung; Try-acquire statt Queue. Alle
   Manga-/Band-Einstiege laufen darüber, keine zweite Drivererzeugung bei Busy.
2. Niemals unzugeordnete ausführbare Worker/Browser zulassen. Nur selbst erzeugte
   Handles/Gruppe beenden; kein taskkill nach Namen, keine PID-Suchbereinigung.
3. Erfolg erst nach validem vollständigem SUCCESS-Frame, Worker-Exitcode 0 und
   bestätigtem Cleanup an Controller geben. Workerexit oder quit allein genügt
   nicht; Fehler-/Timeout-/Protokollausgänge nicht als Teilerfolg ausliefern.
4. Auf Erfolg, Fehler, Ausgabeverletzung oder Timeout denselben idempotenten
   Cleanup-Pfad nutzen: gegebenenfalls nur eigenes Job Object terminieren,
   Workerhandle auf Exit prüfen, Gruppen-Accounting ActiveProcesses=0 abfragen,
   I/O-Reader beenden, eigene Handles/Pipes/Tempressourcen schließen. Completion-
   Port-Meldungen höchstens als Signal, nicht als alleinigen Endbeweis verwenden.
5. Freigabe genau einmal mit Owner-Ticket; Jobhandle bis zum Endnachweis behalten.
   Keine Freigabe, solange ein nativer Start-/Cleanup-Aufruf noch aussteht: ein
   später zurückkehrendes CreateProcess darf keinen Browser nach Slotfreigabe erzeugen.
6. Normaler Browser-/Driverfehler kann nach bestätigtem Gruppen-Kill wieder zu
   IDLE führen. Bei nicht bestätigbarem Jobende/fehlgeschlagenen Win32-Aufrufen
   bleibt auch Variante B konservativ gesperrt. **Keine uneingeschränkte automatische
   Wiederherstellung bei OS-/Supervisorversagen versprechen.**
7. Shutdown setzt zuerst CLOSING, lehnt neue Aufträge ab, beendet nur die eigene
   aktive Gruppe und bestätigt deren Ende. Bei Host-Crash soll das letzte private
   Jobhandle mit Kill-on-close die Gruppe beenden; kein vererbtes Jobhandle darf
   das verhindern. Crash-/Handle-Leak-Verhalten braucht einen echten Prototypnachweis.
8. Worker erhält eigenes Temp-/Firefoxprofil, keine vorhandenen Benutzerprofile.
   Nichtverbindung mit schon laufendem Firefox sicherstellen; keine fremden
   Firefoxprozesse unterstellen. Cleanup nur in nachgewiesen eigener Verzeichniswurzel,
   keine Reparse-/Junction-Verfolgung oder Pfade aus Workerantworten verwenden.
9. Keine DB-/MANGALIB-/Spring-/Cloud-Credentials erben: Environment aus kleiner
   Allowlist bauen, eigener Temp/PATH für feste lokale Executables. JVM-Optionen
   wie JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS nicht übernehmen.

Supervisor verwendet monotone Fristen ab Slotbelegung: eigener Start-/Handshake-
Anteil, Gesamtbudget je Reihen-/Bandauftrag und separates Cleanupbudget. Harte
Beendigung wirkt über die Jobgruppe, nicht über Future.cancel(true). OS-API-Hänger
oder verlorene Cleanup-Evidenz bleiben eine Grenze der Verfügbarkeit.

**Offene Budgetentscheidung:** Vorschlagsrahmen Start/Handshake 30 s, Cleanup 15 s,
Band gesamt 120 s; Reihenbudget erst nach synthetischem Leistungs-/Fachabgleich
festlegen. Schon vorhandene 150 ms pro Band ergeben bei 10.000 Bänden mindestens
25 Minuten ohne WebDriver-Roundtrips. Kein stiller 120-/300-Sekunden-Reihendeckel,
wenn reguläre lange Importe erhalten bleiben sollen. Alle Werte sind Entwurf.
Job-Memory-/ActiveProcess-Limits sind grundsätzlich ergänzend planbar, aber ohne
Firefox-Baseline keine vermeintlich sicheren kleinen Zahlen festschreiben;
Firefox-Sandbox- oder legitime Contentprozesse nicht dadurch unbemerkt brechen.

### 7. Teststrategie und abgrenzbare Umsetzungsschritte

| Schritt | Geplanter Umfang / Abnahme | Aufwand grob |
|---|---|---|
| 1. Isolierter Windows-Prototyp | Nach separater Freigabe JNA-Version/Bindings festlegen, Dummy-Worker mit Kind/Enkel ohne Browser/DB. Atomare Jobzuordnung, Handlevererbung, bestehende Nested Jobs, Parent-Crash, Start vor Resume, Kill/ActiveProcesses=0, außerhalb bleibender Kontrollprozess. Kein Gate im Produkt vor bestandenem Nachweis. | 1–2 Tage |
| 2. Supervisor/Protokoll | Reine Java-State-/IPC-Fakes: Reihen/Reihen und Reihen/Band-Konkurrenz, keine Queue, verzögerter Start, blockierte Aufrufe, doppelte Callbacks, Timeout/Interrupt, falsche ID/Version, überlange/kaputte Frames/stderr, Owner bis Cleanup, Shutdownrennen. Kein nativer Worker in normalen Unit-Tests. | 1–2 Tage |
| 3. Worker-/Artefaktgrenze | Kleines SourceSet/Artefakt, Extraktion erhalten, DB-/Springfreiheit, explizite Programme und eigene Profile, kein statischer Live-Bypass. Lifecycle-Fakes für Konstruktor/get/Extraktion/quit; Grenzen bei vollständigen synthetischen 10.000-Bände-Maps prüfen. | 1–2 Tage |
| 4. Gateway/HTTP/Frontend | Bestehende Erfolgspayloads per Standalone-MockMvc; 429/neutraler 500 ohne Referenz-/Repositoryarbeit; CSRF/Host/Origin erhalten. DOM: Busy-Meldung, Felder erhalten, keine automatische Wiederholung. Test-Doubles am neuen Seam, alle bisherigen Vertragsassertions erhalten. | 1–2 Tage |
| 5. Paketierung und Freigabeprüfung | Manual-Artefakt nachweislich ohne Worker/Native/Live-Abhängigkeiten; Boot-/M06-/Altbestandsregression erst separat geschützt freigeben. Echte Firefox-/Gecko-Sandboxkompatibilität später nur isoliert/offline mit lokalen Seiten und neuer Browserfreigabe. | 1–2 Tage plus Abnahme |

Gesamt vorläufig **5–10 Entwicklungstage**, stark abhängig von Nested-Job-/Firefox-
Sandboxkompatibilität und JNA-/Paketierungsnachweis; keine Zusage, dass der frühere
3–5-Tage-Rahmen bereits alle nachgewiesenen OS-Risiken abdeckt. Erst Prototyp
abnehmen, danach Produktionsintegration freigeben. Bei nicht beherrschbarer
Native-Anbindung den kleinen eigenen Launcher erneut vergleichen, nicht heimlich
Sicherheitsinvarianten lockern.

Vor jedem späteren Gradle-Testlauf konkreten --dry-run prüfen. Bestehende 29
MVC-/JSON-, M06-, Legacy- und DB-Guard-Nachweise unverändert lassen; echte
DB-/Browser-/Workerstarts nur im passenden gesonderten Freigaberahmen.

### 8. SSRF-/Sandboxgrenzen und Abschluss dieses Auftrags

Ein Job Object steuert Prozesslebenszyklus, ist **keine Netzwerk-, Benutzer- oder
Dateisystem-Sandbox**. Worker läuft zunächst unter demselben Windowsbenutzer.
Keine verlässliche Sperre privater Netze, Loopback, DNS-Rebinding, Weiterleitungen,
Browser-Unterressourcen, Downloads oder durch externe OS-Broker erzeugter Prozesse
allein aus Jobzugehörigkeit ableiten. Für späteren SSRF-Auftrag ist der separate
Worker ein geeigneter Ansatzpunkt für dedizierten Egress-/Proxy-/Identitätsentwurf;
der lokale Geckodriver↔Firefox-Steuerkanal muss dabei weiter funktionieren.
Eine Firewallregel auf java.exe könnte auch die Webanwendung treffen; Isolation
nicht vorschnell als reine URL- oder Prozessnamensregel implementieren.

Jetzt geändert: nur TESTING.md und ROADMAP.md. Zwei oben begrenzte technische
Proben bestanden; keine Prozesszuordnung/-erzeugung oder Job-Terminierung getestet.
Keine Gradle-/JUnit-/DOM-/Integrationstests, Builds, Anwendung, HTTP-Listener,
Worker-/Firefox-/Geckodriver-/Browserstarts, Datenbankverbindungen, SQL, externe
Abrufe, Downloads, Installationen oder neue Dependencies. Keine Git-Operationen.
154 vorhandene Quell-/Test-/Skriptdateien per SHA-256 gegen den zu Beginn dieses
Auftrags erfassten Stand unverändert; MANUAL_TESTING.md/build.gradle.kts ebenfalls
unverändert. Historische Ergebnisse/Änderungen erhalten, keine allgemeine
T03/T04/T06- oder weitere T02-Implementierung begonnen.

Vor nächstem Auftrag freizugeben: der eng begrenzte native Dummy-Prototyp und die
dafür erforderliche explizite JNA-Declaration/Version; später die Budget-/Fehler-
Semantik und erst nach positivem Gruppennachweis die Produktintegration.

## T02 – Windows-Prozessprototyp mit JNA: Teilnachweis und Sicherheitsstopp (16.10.2026)

**Gesondert freigegebener Experimentauftrag, keine Produktionsimplementierung.**
Variante B bleibt gewählt. Der vollständige Prozessgruppen-/Cleanupnachweis ist
noch nicht erbracht. Beim ersten Baumversuch wurde eine Abweichung der Job-PID-Liste
festgestellt; gemäß Stoppregel keine weiteren synthetischen Prozesse gestartet.
Keine Schutzbedingungen gelockert und keine Wiederholungsschleife durchgeführt.
Der vorige Machbarkeitsbericht bleibt als historischer Stand unverändert erhalten.

### Umgebung, Dateien und Ausführung

- Branch ausschließlich über `.git/HEAD` gelesen: `codex/t02-security-hardening`.
  Keine Git-Operationen, auch kein git diff/status/check-ignore.
- Windows NT **10.0.26200.0**, Win64; Java **21+35-LTS-2513**, 64-Bit Server VM.
- Ausschließlich vorhandenes lokales **JNA 5.17.0**, Kern-JAR aus Gradle-Cache.
  Keine neue Gradle-Dependency, keine Installation/Downloads/externen Abrufe.
- Eigenständige Quellen unter `experiments/windows-job-prototype/`:
  `JobPrototype.java`, `SyntheticProcess.java`, `Run-Prototype.ps1`, `README.md`.
  Keine Einbindung in Gradle oder normale Tests; kein Spring-/DB-/Scraper-Code.
- Kompilate/Native-Extraktion, Marker und Evidenz ausschließlich unter dem bereits
  ignorierten `.gradle/t02-job-prototype-20261016/`. Persistenter `STOP`-Marker bleibt
  erhalten. `start-failures.result`, rollenbezogene `evidence.tsv`/PID-Dateien und
  `remaining-process-check.json` enthalten nur synthetische Prozessinformationen.
- Direkte `javac`-Kompilierung nur der Prototypquellen. Vier anfängliche
  Namensauflösungsfehler bei `close(Pointer)` im inneren Adapter wurden vor dem
  ersten Kindprozessstart korrigiert; keine Änderung des MangaLib-Quellcodes.
- Tatsächlich ausgeführte Modi: `layout`, `start-failures`, `normal-all-exit`.
  Letzterer wurde vor dem vorgesehenen natürlichen Abschluss abgebrochen.
  Ein Fall pro Aufruf, keine parallelen Belastungstests oder Wiederholung.

Das Skript prüft Branch, vorhandene Java-/JNA-Pfade und JVM-Overridevariablen ohne
Wertausgabe. Jeder Native-Start protokolliert vor dem Aufruf ausschließlich feste
synthetische Executable-/Klassen-/Argument-/Arbeitsverzeichniswerte. Workerumgebung
nur SystemRoot und eigene TEMP/TMP; keine geerbten DB-/Spring-/Cloud-/JVM-Optionen.
Native bInheritHandles=false, nicht vererbbare Job-/Prozess-/Threadhandles.
ProcessBuilder-Kind/Enkel nur feste eigene Klasse und Klassenpfad, stdout/stderr
verworfen. Kein MangaLib-JAR und kein manuelles Artefakt gestartet.

### Win64-ABI, Lebensdauer und Handle-Regeln

Vor Kindstarts geprüft: Pointer/SIZE_T/ULONG_PTR 8 Byte, DWORD/BOOL 4 Byte,
WCHAR 2 Byte; JNA `StdCallLibrary`, Unicode `CreateProcessW`, veränderbarer
NUL-terminierter `char[]`-Commandlinepuffer, UTF-16LE-Environmentblock mit Doppel-NUL.
Kein Windows-`long` als Java-`long` abgebildet; pointerbreite Felder hier bewusst
als 64-Bit-Werte und Ausführung auf Win64 begrenzt.

| Struktur/Feld | Tatsächlich geprüfte Größe bzw. Offset |
|---|---|
| STARTUPINFO / STARTUPINFOEX | 104 / 112 Byte |
| PROCESS_INFORMATION | 24 Byte |
| JOBOBJECT_BASIC_LIMIT_INFORMATION / IO_COUNTERS / EXTENDED_LIMIT_INFORMATION | 64 / 48 / 144 Byte |
| STARTUPINFO.lpReserved / lpReserved2 / hStdInput | 8 / 72 / 80 |
| STARTUPINFOEX.lpAttributeList | 104 |
| BasicLimit.LimitFlags / MinimumWorkingSetSize / ActiveProcessLimit / Affinity | 16 / 24 / 40 / 48 |

Verwendete Win32-Signaturen im isolierten Adapter: CreateJobObjectW,
SetInformationJobObject, QueryInformationJobObject, GetHandleInformation,
InitializeProcThreadAttributeList, UpdateProcThreadAttribute,
DeleteProcThreadAttributeList, CreateProcessW, IsProcessInJob, ResumeThread,
GetProcessId, OpenProcess, QueryFullProcessImageNameW, WaitForSingleObject,
GetExitCodeProcess, TerminateJobObject und CloseHandle. DuplicateHandle ist für
einen späteren, hier nicht ausgeführten Fall vorbereitet.

Private unbenannte Jobs, KILL_ON_JOB_CLOSE und ActiveProcessLimit gesetzt und
zurückgelesen; BREAKAWAY_OK/SILENT_BREAKAWAY_OK aus. Attributgröße über erwarteten
ERROR_INSUFFICIENT_BUFFER=122 ermittelt. Attributliste UND pointerbreites
Jobhandlearray bleiben bis zur Rückkehr aus CreateProcessW stark referenziert und
allokiert. Erst danach DeleteProcThreadAttributeList und Speicherfreigabe.
Prozess-/Threadhandles separat verwaltet, Primärthread nach Resume bzw. Abbruch
geschlossen; Jobhandle bleibt bis zum bestätigten Cleanup offen. Kein nachträgliches
AssignProcessToJobObject und kein ungeschützter Fallbackstart.

Diese Layoutchecks, bisherige lokale Referenzsichtung und erfolgreiche Teilaufrufe
beweisen nicht die Fehlerfreiheit aller Bindings. Insbesondere die Interpretation
von JobObjectBasicProcessIdList muss wegen der beobachteten Abweichung unabhängig
nachgeprüft werden. Vollständige aktuelle SDK-Referenzen wurden nicht nachgeladen.

### Testmatrix: tatsächlich nachgewiesen und offen

| Fall | Bewertung | Beobachtung / Grenze |
|---|---|---|
| Win64-Layouts, leeres privates Job, Flags, Accounting | **Bestanden** | ABI-Checks und Limit-Readback erfolgreich, bestehende übergeordnete Jobmitgliedschaft des Probeprozesses erneut true. |
| Fehlende Executable vor Erzeugung | **Bestanden** | CreateProcessW false, Win32 **2**, keine zurückgegebenen Prozess-/Threadhandles, Job aktiv 0, kein Fallback. |
| Atomare JOB_LIST-Zuordnung vor Worker-Ausführung | **Bestanden, lokal begrenzt** | PID 42288 suspendiert bereits Mitglied des eigenen Jobs, kein Worker-Marker. Auch PID 42264 vor Resume zugeordnet. Keine pauschale Zusage für andere Jobs/Firefox. |
| Simulierter Fehler nach Zuordnung, vor Resume | **Bestanden** | PID 42288 über eigenes Job beendet; ActiveProcesses=0, leere Jobliste, Prozesshandle signalisiert, kein Worker-Code. Getrackte Handles/Attributlisten am Ende 0. |
| CREATE_SUSPENDED / ResumeThread | **Bestanden** | PID 42264 schrieb während zusätzlicher 150 ms suspendierter Wartezeit keinen Marker; ResumeThread lieferte vorherigen Suspendcount 1; Worker lief danach. |
| Kind-/Enkel-Mitgliedschaft als Einzelprüfung | **Teilnachweis** | Worker 42264, Kind 43676 und Enkel 38016 meldeten Marker; Imagepfade und IsProcessInJob für alle gehaltenen Handles wurden vor der fehlgeschlagenen Listenassertion erfolgreich geprüft. Vollständige Population nicht bestätigt. |
| Abgleich vollständige Jobliste ↔ erfasste Prozess-IDs | **Fehlgeschlagen – Stopp** | `IllegalStateException: unexpected job membership` im ersten Baumversuch. Ist-Liste nicht protokolliert; Ursache nicht nachgewiesen. |
| Cleanup beim Versuchsabbruch | **Bestanden für ausgeführten Abbruchpfad** | Beide eigenen Jobs durch Ressourcenabschluss bereinigt; TerminateJobObject, ActiveProcesses=0, leere Listen und signalisierte gehaltene Prozesshandles ohne weitere Exception. Alle fünf bekannten PIDs danach nicht vorhanden. |
| Kontrollprozess bleibt über Zielgruppen-Cleanup unberührt | **Nicht nachweisbar** | Kontrollprozess 3504 wurde in eigener Jobgruppe gestartet; der vorgesehene Vorher-/Nachher-Isolationsvergleich wurde wegen des früheren Stopps nicht erreicht. Er wurde beim Abschluss ebenfalls als eigener Prozess bereinigt. Kein Nachweis über tatsächliche fremde Prozesse. |
| Natürlicher Worker-/Gesamtabschluss und überlebende Nachfahren | **Nicht ausgeführt** | Baumfall bereits vor Steuerung des normalen Abschlusses gestoppt. |
| Workerfehler, unerwartetes Worker-Ende, blockierter Worker/Deadline | **Nicht ausgeführt** | Vorbereitete Modi nach Stopp nicht gestartet. |
| Schließen letztes Jobhandle / dupliziertes Handle | **Nicht ausgeführt** | Kill-on-close nur als Flag, nicht durch Prozessbeendigung dieses Pfads nachgewiesen. |
| Inkompatible eigene Joblimits, Breakaway-Ablehnung/Fehlercodes | **Nicht ausgeführt** | Keine Änderung/Umgehung bestehender übergeordneter Limits. |
| Vorhandenes Ancestor-Job + neue eigene Jobzuordnung | **Teilnachweis** | Suspendierter Worker und Baumrollen hatten eigene Jobmitgliedschaft im bereits vorhandenen Jobkontext. Vollständige Nested-/UI-/Crashkompatibilität nicht nachgewiesen. |
| Unerwartetes Ende synthetischer Supervisor / Kill-on-close | **Nicht ausgeführt** | Vorbereiteter Helper-Crash nicht gestartet. |
| Konservative Slotfreigabe bei fehlender Cleanup-Evidenz | **Teilnachweis** | Baumassertion brach den Ablauf vor Freigabe ab; persistenter STOP blockiert weitere Modi. Vorbereiteter Policy-Double-Fall nicht ausgeführt, kein produktiver Supervisor implementiert. |

### Stopp, Restprozesskontrolle und Ursache

Fünf insgesamt tatsächlich erzeugte synthetische Prozesse: suspendierter Fehlerfall
**42288**, Kontrollprozess **3504**, Worker **42264**, Kind **43676**, Enkel **38016**.
Der Start mit fehlender EXE erzeugte keinen Prozess. Nach Abbruch ausschließlich
diese bekannten IDs lesend mit Get-Process geprüft: **alle nicht mehr vorhanden**.
Keine globalen Namens-Kills, PID-Suchbereinigung oder Beendigung fremder Prozesse.
Keine unbestätigten bekannten Restprozesse; unbekannte zusätzliche Gruppenmitglieder
lassen sich historisch wegen der fehlenden Ist-Liste nicht identifizieren. Der
Cleanup-Pfad prüfte die eigenen Jobs vor Handlefreigabe auf leeren Zustand.

**Diagnoselücke des Prototyps:** `capture` schreibt die vollständige Gruppenliste
erst nach erfolgreichem Gleichheitsvergleich. Deshalb fehlen beim Fehler die
tatsächlichen Ist-/Soll-Listen im Trace. Mapping, Timing und unerwartete
Gruppenbeobachtung können anhand dieser Daten nicht sicher unterschieden werden.
Kein belegter Fehler in Windows, JNA oder MangaLib; keine Spekulation als Befund.
Die vorbereiteten weiteren Szenarien wurden weder grün erklärt noch nachgestartet.

### Konsequenz und nächster Freigabeschritt

JNA bleibt als Ansatz plausibel: native Erzeugung mit atomarer Zuordnung und
SUSPENDED/Resume funktionierte im geprüften Kontext. Noch keine belastbare
Entscheidung, ob ein eigener C/C++-Launcher den Befund besser lösen würde; dieselben
Win32-Populations-/Cleanupfragen müssten auch dort geklärt werden. **Produktive
Workerintegration weiterhin nicht freigabereif.**

Nach erneuter Freigabe zuerst begrenzte Diagnose VOR Assertions ergänzen,
Ist-/Soll-PIDs und Accounting zeitlich zugeordnet sichern, PID-Listenlayout unabhängig
gegen Win32-Vertrag verifizieren. Keine Assertion entfernen oder Erfolg aus
Workerexit ableiten. Anschließend genau einen Baumversuch in neuem eigenen
Evidenzverzeichnis; vorhandenen STOP/Altbelege erhalten. Erst bei geklärtem Befund
die übrigen Gruppen-, Handle-, Nested-Limit- und Crashfälle stufenweise ausführen.

Für Produktcleanup weiterhin alle Bedingungen erforderlich: kein Startaufruf mehr
ausstehend, eigenes Job aktiv 0, Jobliste leer, Worker/alle gehaltenen Prozesshandles
signalisiert, Reader und eigene Handles vollständig abgeschlossen. Kill-on-close
ohne abfragbaren letzten Jobhandle benötigt einen gesonderten, tatsächlich
vollständig erfassten Populationsnachweis; das Schließen allein genügt nicht.
Poll-/Wait-Fristen 1–10 Sekunden und synthetische Rollen mit 25-Sekunden-Selbstende
sowie Prozesscaps 1/3/4 sind nur Versuchsbudgets. Ein synchroner Kernelaufruf kann
damit nicht gegen jeden theoretischen OS-Hänger abgesichert werden.

Erst echte später freigegebene Offline-Firefox-/Geckodriver-Versuche klären deren
Sandboxjobs, Profilsperren, externe Broker und tatsächliche Prozess-/Ressourcenlimits.
Job Objects lösen weiterhin weder SSRF noch Netzwerk-/Dateisystemisolation.

**Unverändertheitsprüfung:** 157 bestehende Dateien aus src/scripts sowie
build.gradle.kts, MANUAL_TESTING.md und AGENTS.md per SHA-256 unverändert bestätigt.
Nur neuer Experimentbereich und Nachträge in TESTING.md/ROADMAP.md. Keine Gradle-,
JUnit-, DOM-, Integrations- oder MangaLib-Buildläufe, Datenbank-/SQL-Verbindungen,
Spring-/HTTP-/manuelle Anwendungsstarts, Browser/Firefox/Geckodriver, Live-Scraper,
Installationen, Downloads oder Git-Operationen. Keine T03/T04/T06-Änderungen.

## T02 – Einmalige Joblisten-Diagnose 42273e86: zusätzliche Mitglieder (16.10.2026)

**Freigaberahmen:** gezielte Diagnose und höchstens ein neuer synthetischer
Worker-/Kind-/Enkelversuch. Keine anderen Prozessszenarien/Produktintegration.
Der Versuch wurde einmal ausgeführt und am unverändert strengen Populationsvergleich
erneut gestoppt. Die vorherigen Berichte sind historische Ergebnisse und bleiben
unverändert. Keine nachträgliche Interpretation als erfolgreicher Baumtest.

### Aufbau, statische Prüfung und Einmalfreigabe

Neue reine Experimentdateien `JobListDiagnosis.java` und
`Run-JobListDiagnosis.ps1`; begrenzte persistente Diagnosehooks in
`JobPrototype.java`, zusätzliche Compile-Datei im bisherigen `Run-Prototype.ps1`.
Dessen STOP-Prüfung bleibt wirksam. `SyntheticProcess.java` unverändert.
Keine Anwendungs-/Gradle-/Guard-/Security-/Importänderungen.

Eigenes Verzeichnis `.gradle/t02-job-prototype-20261016/diagnosis-42273e86/`.
Freigabekennung **T02_JOBLIST_DIAG_20261016_42273e86** vor dem JVM-Start atomar mit
FileMode.CreateNew in `authorization.consumed` geschrieben und Flush(true)
persistiert. Java erzeugt zusätzlich `attempt.started` mit CREATE_NEW; gültig nur
für dieses Verzeichnis und diesen einzelnen Ablauf. Der Native-Adapter erlaubt
danach genau einen Worker/tree-Start ohne Zusatzflags/Helper/Fallback. Die Kennung
bleibt auch bei Fehlschlag verbraucht; keine erneute Ausführung zur Gegenprobe.
`attempt.finished` enthält **STOP**. Alter STOP-Marker wurde weder gelöscht noch
umbenannt oder überschrieben. Sämtliche **32 bisherigen Evidenzdateien hashidentisch**.

Direkte javac-Kompilierung der drei separaten Experiment-Java-Dateien mit lokalem
JNA 5.17.0; anschließend Modus `Static` ohne Kindprozessstart erfolgreich:

| Win64-Feld/Layout | Prüfung |
|---|---|
| NumberOfAssignedProcesses | DWORD, 4 Byte, Offset 0 |
| NumberOfProcessIdsInList | DWORD, 4 Byte, Offset 4 |
| ProcessIdList | ULONG_PTR, 8 Byte je Eintrag, Beginn Offset 8 |
| Layout mit einem Arrayeintrag | 16 Byte, JNA-Structure-Layout geprüft |
| Puffer für acht Einträge | 8 + 8×8 = 72 Byte |
| Bisheriger `getLong` vs. pointerbasierter Leser | Drei synthetische PIDs exakt gleich zurückgelesen |

Lokale JNA-Layoutmetadaten, vorhandene Prototypquellen und Win64-Grundtypen geprüft.
Vollständige SDK-Header/aktuelle offizielle Vertragsdokumentation wurden an den
geprüften lokalen SDK-/Paketpfaden nicht gefunden; keine externen Abrufe. Daher
keinen zusätzlichen unabhängigen offiziellen Referenznachweis behaupten. Keine
statisch eindeutig fehlerhafte Offset-/Schrittweitenstelle gefunden, entsprechend
keine behauptete Layoutkorrektur. Konkrete vollständige Windows-Rückgaben liefern
im Versuch zusätzliche Evidenz für die untersuchte Listenauswertung.

Diagnose fragt QueryInformationJobObject mit ReturnLength-DWORD ab, protokolliert
Boolean-Rückgabe und unmittelbar erfassten Fehlercode, beide unsigned Anzahlfelder,
Puffergröße, ReturnLength und sämtliche numerischen PID-Einträge VOR Assertions.
Verwendete Erfolgsabfragen sind strikt erforderlich; unzureichende Puffer oder
ungültige Zähler können nicht als vollständige Liste oder Cleanup gelten.
Ein kleiner-Puffer-Vergleich wäre nur zusätzliche Diagnose nach erfolgreicher
Populationsprüfung gewesen; er wurde wegen des Stopps **nicht erreicht**.
Kein ausgeführter Nachweis des ERROR_MORE_DATA-Verhaltens.

Jeder Diagnoseeintrag enthält Reihenfolge, UTC-Epoch-Millis und monotone Zeit;
Marker-Zeitstempel und Rollen werden gesichert. FileChannel.force(true) vor
nachfolgenden Assertions. Maximal 512 Ereignisse/4 KiB je Ereignis/1 MiB pro
Diagnose-JVM; tatsächlicher gemeinsamer Static-/Run-Trace **8049 Byte**.
Keine Environment-/Secret-/Nutzerdatenausgaben. Private Startgruppe mit bekannten
Java-/Klassen-/Arbeitsverzeichnispfaden, bestehende suspendierte JOB_LIST-Zuordnung,
kleine Environment-Allowlist und synthetischer Worker unverändert. Keine weiteren
Kontrollprozesse in diesem enger freigegebenen Versuch gestartet.

### Vollständige Beobachtungen des einen Baumversuchs

| Rolle | Erwartete PID | Handleprüfung vor Assertion |
|---|---:|---|
| Worker | 19136 | Image/PID geprüft, WAIT_TIMEOUT=258, STILL_ACTIVE=259, IsProcessInJob=true |
| Kind | 43620 | Image/PID geprüft, WAIT_TIMEOUT=258, STILL_ACTIVE=259, IsProcessInJob=true |
| Enkel | 44700 | Image/PID geprüft, WAIT_TIMEOUT=258, STILL_ACTIVE=259, IsProcessInJob=true |

Vor Resume: Jobliste **[19136]**, assigned=1, listed=1, Accounting total=1/active=1,
ReturnLength=16 bei 72-Byte-Puffer. Anschließend alle drei Rollenmarker erzeugt;
ready-Zeitstempel in UTC: Worker 20:51:26.2096823, Kind 20:51:26.3943435,
Enkel 20:51:26.5487778. Kein 25-Sekunden-Selbsttimeout erreicht.

Beim strengen Vergleich:

- **Soll vollständig sortiert:** `[19136, 43620, 44700]`.
- **Ist vollständig in Windows-Reihenfolge:**
  `[19136, 21308, 43620, 15980, 44700, 8480]`.
- **Ist sortiert:** `[8480, 15980, 19136, 21308, 43620, 44700]`.
- **Zusätzliche IDs:** `[8480, 15980, 21308]`.
- QueryInformationJobObject **true**, Fehlerdiagnose **0**, assigned=**6**,
  listed=**6**, Puffer=**72 Byte**, ReturnLength=**56 Byte** (=8+6×8).
- Beide PID-Leser liefern identische vollständige Werte. Accounting zugleich
  TotalProcesses=**6**, ActiveProcesses=**6**.
- Zweiter rein lesender Snapshot im Fehler-/Cleanup-Pfad liefert denselben
  vollständigen Sechs-PID-Satz; alle drei gehaltenen erwarteten Handles weiter
  nicht signalisiert, Exitcode 259 und eigene Jobmitgliedschaft bestätigt.

Der ursprüngliche vollständige Gleichheitsvergleich bleibt unverändert streng
(`actual.equals(ids)`), **keine Teilmengenprüfung**. Er schlug fehl, bevor die
normale Abschlusssteuerung oder weitere Diagnosephasen erreicht wurden.

### Einordnung A–D und Grenzen der Ursachenfeststellung

| Kategorie | Bewertung |
|---|---|
| A – Auslese-/Interpretationsfehler | Für diesen Befund nicht belegt: vollständige erfolgreiche Abfrage, konsistente ReturnLength, identische Long-/Pointer-Leser und unabhängiges Accounting mit sechs Aktiven. Kein gefundener Layoutfehler. |
| B – Population zeitlich verändert | Erklärt den beobachteten Vergleich nicht: erwartete drei Prozesse noch aktiv; derselbe zusätzliche Satz in zwei aufeinanderfolgenden Snapshots. Keine erwartete PID als bereinigt unterstellt. |
| C – Tatsächlich zusätzliche Jobmitglieder | **Nachgewiesen:** drei zusätzliche numerische IDs plus Accounting active=6. Ihre Identitäten/Entstehung nicht nachgewiesen. |
| D – Weiterhin ungeklärte Abweichung | **Herkunft ungeklärt:** Für Zusatzmitglieder fehlen Image-/Eltern-/Erzeugungszeitbelege. Kein pauschaler Vorwurf gegen Windows/JNA und keine Klassifikation als harmlose Konsolenhelfer oder fremde Benutzerprogramme. |

Die ursprüngliche Assertion wird reproduziert; wegen der fehlenden damaligen
Ist-Liste ist nicht bewiesen, dass genau dieselbe Population oder Ursache vorlag.
Eine mangelhafte ursprüngliche Protokollierung ist nachgewiesen und nun korrigiert.

**Zusätzlicher Blocker:** Der bestehende Adapter setzt ActiveProcessLimit=3 und
liest die Einstellung vor Erzeugung zurück; trotzdem melden Liste und Accounting
sechs aktive Mitglieder. Bedeutung/Wirksamkeit dieser Grenze für die zusätzlichen
Prozesse ist offen. Keine Behauptung einer wirksamen Drei-Prozess-Obergrenze auf
Basis des Flag-Readbacks; kein blindes Anheben oder Abschalten des Limits.

Bei manchen erfolgreichen Nicht-Listen-APIs blieb raw_error=183 im Threadzustand.
Dieser LastError ist bei erfolgreicher BOOL-Rückgabe kein nachgewiesener Fehler
des Aufrufs. Die relevanten PID-Listenabfragen hatten true/0; Fehlercodes wurden
nicht unabhängig vom jeweiligen Rückgabevertrag als Fehlschlag gewertet.

### Cleanup und Restprozessprüfung

Nach der Assertion nur noch Diagnose und Cleanup der eigenen privaten Jobgruppe:

1. TerminateJobObject true; danach Accounting **active=0** (total weiterhin 6).
2. Vollständige Liste **[]**, assigned/listed=**0/0**, ReturnLength=**8 Byte**.
3. Alle drei gehaltenen Handles WAIT_OBJECT_0=**0**, Exitcode **91**.
4. Leerer Zustand vor Handlefreigabe nochmals bestätigt; finales Ledger:
   **0 Handles, 0 Attributlisten, genau 1 nativer Workerstart**.
5. Alle **sechs** beobachteten IDs anschließend ausschließlich lesend mit
   Get-Process geprüft: **keine vorhanden**, Beleg `remaining-all-job-pids.json`.

Keine bekannten unbestätigten Restprozesse. Für die drei Zusatz-IDs wurden vor
Cleanup keine eigenen Prozesshandles/Imageidentitäten erfasst; ihre Zuordnung
stützt sich auf die vollständige eigene Jobliste, ihr Gruppenende auf Liste und
Accounting. Keine Beendigung anhand bloßer IDs/Namen und kein fremder Prozesskill.
Ein erfolgreicher TerminateJobObject-Aufruf allein wurde nicht als Cleanupbeweis
verwendet. Cleanupdauer vom cleanup_begin bis cleanup_confirmed etwa **38 ms**
in diesem einzelnen Lauf; keine allgemeine Zeitgarantie oder Lastmessung.

### Ergebnis und nächste Grenze

**Vollständig identifizierte/kontrollierte erwartete Population nicht nachgewiesen.**
Containment der drei Rollen, strikter Stopp und bestätigter leerer Gruppenabschluss
sind belegt; unbekannte zusätzliche Mitglieder verhindern die Freigabe. JNA bleibt
technisch plausibel, aber weder Produktintegration noch nächste ausführende
Prototypstufe sind mit diesem Befund sicher freigabereif.

Vorschlag für einen neuen Auftrag: zuerst rein statisch Herkunft möglicher
zusätzlicher Prozesse und ActiveProcessLimit-Semantik anhand verfügbarer Quellen
untersuchen; fehlende offizielle Referenzen gegebenenfalls ausdrücklich freigeben.
Eine spätere laufende Identitätsdiagnose müsste zusätzliche eigene Jobmitglieder
über früh gehaltene Handles, Imagepfade und Erzeugungszeiten nachvollziehen, ohne
Namen-Allowlist oder PID-basierte Fremdbeendigung. Dafür neue begrenzte Freigabe;
jetzige Kennung verbraucht lassen. Keine Wiederholung jetzt.

Weiterhin offen/nicht gestartet: kleiner Abfragepuffer, natürlicher Gesamtabschluss,
Workerexit mit verbleibenden Nachfahren, Fehler/Timeout, Kill-on-close, duplizierte
Handles, Supervisor-Crash, inkompatible Nested-Limits, Breakaway und Firefox/Gecko.
Keine IPC-/Workerprodukt-, SSRF-, T03/T04/T06-Arbeiten.

157 geschützte Bestandsdateien (src/scripts, AGENTS.md, MANUAL_TESTING.md,
build.gradle.kts) per Hash unverändert; alle 32 alten Evidenzdateien einschließlich
STOP unverändert. Keine DB-/SQL-Zugriffe auf irgendeine Umgebung, Gradle-/JUnit-/
DOM-/MangaLib-Buildläufe, Anwendungs-/HTTP-/Browser-/Scraperstarts, Downloads,
Installationen, neuen Abhängigkeiten oder Git-Operationen. Direkte javac-Kompilierung
und genau ein synthetischer Prozessbaum sind die einzigen ausführenden Proben.

## T02 – Statische Ursachenanalyse der Jobgruppe und Limit-Semantik (19.10.2026)

**Nur Analyse und Dokumentation.** Kein weiterer Prototyp-/JVM-/Workerstart,
keine Kompilierung und keine Tests. Bestehende Assertions, Quellen, STOP,
Einmalfreigaben und Evidenz unverändert. Branch vom Eigentümer vorgegeben:
codex/t02-security-hardening; in diesem Auftrag keine Git-/Branchabfrage, auch
kein Lesen von .git/HEAD. Öffentlich verfügbare offizielle Microsoft-/OpenJDK-
Referenzen nur textuell gelesen; keine ausführbaren Downloads/Installationen,
keine lokalen Browser oder externen Scraperaufrufe.

### 1. Gesicherter Ist-Zustand und Fundstellen

Ausgewertet: vorheriger Trace diagnosis-42273e86/diagnostic.tsv, Rollenmarker,
tree/evidence.tsv, Restprozesskontrollen sowie ursprünglicher STOP/Ergebnisse.
Diese Daten beschreiben den Lauf vom 16.10., keine heutige Prozesskontrolle.

| Aussage | Beleg / Einschränkung |
|---|---|
| Ein expliziter nativer Workerstart | JobPrototype.java:202–246; JobListDiagnosis.java:125–132; Native-Startledger=1. Absolutes java.exe, feste SyntheticProcess-Klasse, private JOB_LIST-Zuordnung, SUSPENDED/NO_WINDOW, kein Shellaufruf/Fallback. |
| Genau zwei ausdrücklich programmierte Nachfahren | SyntheticProcess.java:28–30,45–60: Worker startet Kind, Kind startet Enkel über ProcessBuilder; Enkel startet nichts. Kein cmd.exe/PowerShell/Browser/Downloadprogramm in den Argumenten. |
| Drei erwartete Rollen | Worker 19136, Kind 43620, Enkel 44700; Marker und gehaltene Prozesshandles mit PID-/Imageprüfung und eigener Jobmitgliedschaft. |
| Sechs zurückgegebene Mitglieder | Vollständig: [19136,21308,43620,15980,44700,8480]; Zusatz-IDs [8480,15980,21308]. assigned/listed=6/6, ReturnLength=56, Puffer=72, beide Leser gleich, Accounting active=6/total=6. |
| Tatsächlich laufend nachgewiesen | Nur für die drei gehaltenen erwarteten Handles: WAIT_TIMEOUT und STILL_ACTIVE vor Cleanup. Für Zusatz-IDs gibt es keine individuellen Wait-/Exit-/Imagebelege. |
| Population zeitlich beobachtet | Suspended-Snapshot zunächst nur Worker, rund 0,55 s später sechs Mitglieder; zwei Sechser-Snapshots etwa 54 ms auseinander. Markerreihenfolge Worker → Kind → Enkel; kein 25-s-Selbstende erreicht. |
| Nicht vorhanden | Keine Parent-PIDs, Kernel-Erzeugungszeiten oder Imagepfade/Handles der Zusatzmitglieder; keine gesicherten Handles/Identitäten der übergeordneten Jobs. Paarweise Reihenfolge ist kein Abstammungsbeweis. |
| Historischer Cleanup | Private Gruppe leer, erwartete Handles signalisiert, alle sechs IDs danach nicht vorhanden. Kein Beweis ihrer vorher fehlenden Identität und keine erneute heutige PID-Prüfung. |

### 2. Java-/Konsolenpfad und plausible Herkunft

Der native Windows-Startpfad des offiziellen OpenJDK-Tags **jdk-21+35** verwendet
in ProcessImpl_md.c:297–325 unmittelbar CreateProcessW. Standardflags sind
CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT; bei bestimmten geerbten Standard-
Handles wird NO_WINDOW entfernt. Kein vorgeschaltetes jspawnhelper-/cmd-Programm
in diesem Pfad. Dies ist die passende OpenJDK-Referenz zum dokumentierten Oracle
Java 21+35, kein neu ausgeführter Binär- oder Disassemblierungsnachweis des lokal
installierten Oracle-Builds.
[OpenJDK 21+35, Windows ProcessImpl](https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/windows/native/libjava/ProcessImpl_md.c).

Der Prototyp setzt NO_WINDOW für den Worker explizit; seine Nachfahren verwenden
ProcessBuilder mit umgeleiteter Ausgabe. Das widerlegt die Erwartung, allein aus
dem Java-Quellcode exakt drei OS-Prozesse garantieren zu können. NO_WINDOW ist eine
Konsolen-/Fenstereinstellung, keine allgemeine Verbotsregel für Hilfsprozesse.
[Microsoft: Prozess-Erzeugungsflags](https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags).

Windows besitzt mit conhost.exe einen eigenen Konsolenhost. Daraus entsteht die
**plausible Hypothese**, dass jedem der drei Konsolenclients ein zusätzlicher
Hostprozess zugeordnet worden sein könnte. Die Zahl und Paarreihenfolge passen,
beweisen es aber nicht. Die offizielle Konsolendokumentation garantiert hier weder
genau einen Host je Java-Prozess noch identifiziert sie unsere drei Zusatz-PIDs.
Keine Einstufung als harmlos, keine conhost-Namens-Allowlist und keine Behauptung
einer dokumentierten conhost-Ausnahme vom ActiveProcessLimit.
[Microsoft: Konsolenhost und Begriffe](https://learn.microsoft.com/en-us/windows/console/definitions).

Andere denkbare Herkunft (plattformseitige Hilfsprozesse oder weitere verschachtelte
Jobs) ist aus den gespeicherten Daten ebenfalls nicht identifizierbar. Im
synthetischen Java-Code selbst werden keine weiteren Jobs angelegt. Die alleinige
Mitgliedschaft des Supervisors in einem vorhandenen Ancestor-Job beweist keine
solche Entstehung. Alte PIDs jetzt erneut zu untersuchen würde ihre damalige
Identität wegen möglicher Wiederverwendung nicht rekonstruieren.

### 3. Limitkonfiguration: Flag, Zahl, Layout und konkreter Job

**Es wurde nicht nur die Zahl 3 abgelegt.** Group-Konstruktor in
JobPrototype.java:113–121 setzt flags=0x2000|8 (=0x2008), activeLimit=cap,
schreibt die Extended-Struktur, setzt Informationsklasse 9 und liest ebenfalls
Klasse 9 in eine neue Struktur zurück. Danach werden BEIDE Felder exakt geprüft:
actual.basic.flags==0x2008 UND actual.basic.activeLimit==cap.
JobListDiagnosis.java:125 erzeugt genau Group(3,0). Der nachfolgende Start wurde
erreicht; die Assertions sind gewöhnliche check-Aufrufe, nicht abschaltbare
Java-assert-Anweisungen. 0x8 ist das dokumentierte ACTIVE_PROCESS-Flag,
0x2000 KILL_ON_JOB_CLOSE. **Konfiguration inklusive Aktivierungsflag durch den
vorhandenen Kontrollfluss bestätigt; Wirksamkeit als Gesamtgrenze nicht bewiesen.**
[Microsoft: BasicLimit, Flags und ActiveProcessLimit](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_basic_limit_information).

Die offiziellen Feldtypen passen zur geprüften Win64-Abbildung: zwei LARGE_INTEGER,
DWORD-Flags, pointerbreite WorkingSet-Felder, DWORD-Prozesslimit, ULONG_PTR-Affinity,
zwei DWORD-Felder. Layout Basic=64 Byte, Flags bei 16, Prozesslimit bei 40;
Extended=144 Byte mit Basic als erstem Teil. Keine neue Ausführung dieser Checks,
sondern Abgleich mit Quellcode/historischem ABI-Nachweis. Extended enthält zusätzlich
IO_COUNTERS und vier SIZE_T-Felder; kein belegter Alignmentfehler.
[Microsoft: ExtendedLimit-Struktur](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information).

Setzen und Rücklesen erfolgen über dasselbe Group.job-Feld aus dem privaten
unbenannten CreateJobObjectW. Die späteren Accounting-/Listenaufrufe verwenden
dieses Feld, im Trace **native@0x4b0**. Im ausgeführten Diagnosemodus gibt es vor
Cleanup keinen Handlewechsel, Close, zweites Job oder erneutes Setzen der Limits.
Keine NULL-Abfrage, die versehentlich den unmittelbaren Ancestor auswählen würde.
Die Ziffern der Limitflags und der Set-/Readback-Handle sind allerdings nicht als
eigenständiges Rohdatenereignis gespeichert: dieser Teil ist ein Kontrollflussbeleg,
kein zusätzlich vorhandener Binärsnapshot während der Sechserpopulation.
[Microsoft: SetInformationJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-setinformationjobobject),
[Microsoft: QueryInformationJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-queryinformationjobobject).

| Nachweisebene | Ergebnis |
|---|---|
| API-Ausführung | Erfolgsrückgaben im Konfigurationspfad geprüft; Start erreicht. BOOL-Erfolg nicht durch beliebigen verbliebenen LastError widerlegt. |
| Korrekte Konfiguration | Flag 0x8 und Limit 3 zusammen mit Kill-on-close zurückgelesen und geprüft; kein bloßer Zahlenwert ohne Flag. |
| Konfiguration später unverändert | Kein entsprechender Schreibpfad im untersuchten Modus; eigener Limitsnapshot zur Messzeit fehlt trotzdem. |
| Tatsächlich wirksame Grenze für sämtliche gezählten Mitglieder | Nicht nachgewiesen; active=6 kann nicht als erfolgreiche Drei-Prozess-Abnahme gelten. |

### 4. Dokumentierte Zähler- und Nested-Job-Semantik

Die offiziellen Verträge präzisieren zwei bisher offen gebliebene Punkte:

1. **ActiveProcesses ist kein unabhängiger Lebendnachweis jeder PID.** Bei einer
   durch ein Limit fehlgeschlagenen Zuordnung kann der Zähler vorübergehend erhöht
   bleiben, bis der beendete Prozess ausgelaufen ist und alle Referenzen freigegeben
   sind. TotalProcesses umfasst auch beendete/fehlgeschlagene Zuordnungen;
   TotalTerminatedProcesses zählt Limitbeendigungen. Damit ist ein zeitweise
   überhöhter Zähler dokumentiert, nicht zwingend ein deaktiviertes Limit.
   Im vorliegenden Trace fehlt der individuelle Zusatzprozesszustand; der
   Terminated-Zähler bleibt 0. Diese Sonderregel ist deshalb eine mögliche
   Erklärungsrichtung, **keine festgestellte Ursache**. Zusatzmitglieder dürfen
   weder als lebend noch als erfolgreich beendet erfunden werden.
   [Microsoft: BasicAccounting](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_basic_accounting_information).

2. **Nested Jobs unterscheiden Limitvererbung und Ressourcenaggregation.**
   Für bestimmte ausdrücklich genannte Ressourcen gilt die strengste Grenze der
   Kette; bei anderen geerbten Ressourcenlimits nennt Microsoft den unmittelbaren
   Job. ACTIVE_PROCESS gehört nicht zur dort aufgezählten Most-restrictive-Liste.
   Eine unbelegte pauschale Min-Regel für alle Limits oder eine garantiert identische
   Zähl-/Durchsetzungsdomäne ist deshalb unzulässig.
   [Microsoft: AssignProcessToJobObject, Remarks](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-assignprocesstojobobject).

Kind-Jobs enthalten Teilmengen des Elternjobs; gewöhnliche CreateProcess-Kinder
erben ohne Breakaway die Kette. Accounting wird nach oben aggregiert. Fremde
Ancestor-/Geschwisterprozesse werden dadurch nicht automatisch in unser privates
Kind-Job aufgenommen. UI-Limits können die zulässige Verschachtelung verhindern.
Eine existierende übergeordnete Jobumgebung allein erklärt die drei Extra-PIDs
nicht. Zusätzliche untergeordnete Jobs könnten andere Zähl-/Limitdomänen relevant
machen, wurden aber für diese Mitglieder nicht nachgewiesen.
[Microsoft: Nested Jobs](https://learn.microsoft.com/en-us/windows/win32/procthread/nested-jobs).

Auch die PID-Liste umfasst den abgefragten Job einschließlich seiner Kind-Jobs.
Beide DWORD-Zähler und die ULONG_PTR-Einträge stimmen mit dem Leser überein;
bei unvollständiger Liste wäre sie nicht als vollständiger Nachweis verwendbar.
Im Trace sind sechs Einträge vollständig enthalten, kein abgeschnittener Puffer.
[Microsoft: BasicProcessIdList](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_basic_process_id_list).

**Keine eindeutig dokumentierte, auf diese drei PIDs nachgewiesene Ausnahme
erklärt den Gesamtbefund.** Aus den geprüften offiziellen Referenzen folgt weder
ein Nachweis eines Windows-Limitfehlers noch eine universelle Zusage, dass jeder
aggregierte ActiveProcesses-Snapshot stets <=ActiveProcessLimit sein muss.
Konfiguration, Durchsetzung und tatsächlicher Einzelprozesszustand getrennt halten.

### 5. Bewertung von Variante B und geeignete Abnahmekriterien

**Variante B bleibt empfohlen, aber nicht produktionsreif.** Ein neuer nativer
Launcher würde dieselben Windows-Semantiken verwenden und fehlende Identitätsdaten
nicht automatisch ersetzen. Es gibt keinen belegten JNA-spezifischen Grund für
einen Architekturwechsel. Private Jobhandles, atomarer Start, ein Auftrags-Slot
und Deadline mit kontrolliertem Gruppen-Cleanup passen weiter zum Zuverlässigkeitsziel.

JOB_LIST ist ein dokumentiertes Erzeugungsattribut; die referenzierten Werte
müssen bis zum Löschen der Attributliste gültig bleiben. Im untersuchten Startpfad
bleiben Liste und Handlearray entsprechend allokiert. Keine nachträgliche
Zuordnungslücke oder ungeschützter Fallback als Lösung einführen.
[Microsoft: UpdateProcThreadAttribute](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute).

Die **bestehende exakte Drei-PID-Assertion bleibt unverändert** und der Befund rot.
Für einen späteren Firefox-Supervisor ist ein vorab vollständig bekannter PID-Satz
kein geeignetes generelles Fachziel: Firefox/Geckodriver dürfen legitime dynamische
Nachfahren erzeugen. Vorgeschlagenes später separat freizugebendes Ziel ist eine
vollständig eigene, überwachte Jobgruppe mit begrenzten Ressourcen und nachvollzieh-
barer Populationsentwicklung; unbekannte Identitäten nicht nach Namen ausblenden.

| Erwartung | Bewertung / spätere Konsequenz |
|---|---|
| Worker vor Resume im privaten Job | Beibehalten. |
| Ein aktiver Scraperauftrag | Beibehalten; bedeutet nicht nur ein OS-Prozess. |
| Synthetischer Test exakt drei bekannte PIDs | Jetzt unverändert; über eine Änderung erst nach Identitätsklärung entscheiden. |
| Produktiv alle PIDs vor Entstehung kennen | Kein notwendiges Ziel; Gruppe, Eigentum, Verlauf und vollständigen Abschluss nachweisen. |
| Hardcap=3 aus Flag-Readback ableiten | Unzulässig; geeignete Firefox-Budgets und tatsächliche Durchsetzungsdomäne später separat abnehmen. |
| Slot frei nach Workerexit/Terminate-Rückgabe | Weiterhin unzulässig. |
| Cleanup nur des privaten eigenen Jobs | Beibehalten; niemals Ancestor-Job oder Namens-/PID-Suchmenge beenden. |

TerminateJobObject erfasst bei einem verschachtelten Zieljob auch dessen Kind-Jobs,
nicht dadurch dessen Eltern/Geschwister. Das ist die passende Kontrollgrenze.
Anschließend weiterhin leeres Accounting UND vollständige leere Jobliste,
signalisierte gehaltene Handles, abgeschlossene I/O-Verarbeitung, geschlossene
eigene Handles und kein ausstehender Startaufruf verlangen. Fehlende Nachweise
lassen den Slot gesperrt. Native OS-Hänger und unzureichende Rechte bleiben Grenzen,
die ein Java-Interrupt nicht beseitigt.
[Microsoft: TerminateJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-terminatejobobject).

### 6. Genau ein minimaler Folgeversuch – nur Vorschlag, nicht freigegeben/ausgeführt

Zweck: **Identität sämtlicher Mitglieder und tatsächliche Limitfelder während
derselben Population erfassen**, keine neue Timeout-/Crash-/Breakaway-Matrix.

1. Neue separate Einmalfreigabe/Evidenz, atomare Verbrauchsdatei vor Start,
   alte STOPs/Kennungen erhalten. Nur vorhandene feste Java-/JNA-Programme.
   Ein nativer Workerstart; unveränderte zwei synthetische Nachfahren, kein
   zusätzlicher Kontrollprozess oder Launcher, kein Browser/Netzwerk/DB.
2. Private Gruppe wie bisher; keine Änderung von Limit 3, Breakaway, Konsolenflags
   oder bestehenden Assertions, um Ursache und Beobachtung nicht zu vermischen.
   Beide Limitsichten abfragen: Basic (Klasse 2) und Extended (Klasse 9),
   Flags dezimal/hex, cap und begrenzte rohe Strukturbytes mit Jobhandle sichern:
   vor Resume, nach Ready-Markern und unmittelbar vor Cleanup.
3. Vor der bisherigen strengen Populationsassertion genau einen Identitätssnapshot
   aller zurückgelieferten Job-PIDs nehmen. Höchstens acht Einträge; Überlauf,
   unvollständige Liste oder unklare Identität => nur noch eigener Gruppen-Cleanup,
   keine weitere ausführende Aktion und kein Retry.
4. Nur für aus der privaten Jobliste stammende IDs Handles mit QUERY_LIMITED_INFORMATION
   und SYNCHRONIZE öffnen; PID, Imagepfad, Erzeugungszeit, Wait-/Exitstatus und
   IsProcessInJob(am selben privaten Handle) sofort korrelieren. Gehaltenes Handle
   plus PID/Erzeugungszeit verhindert, dass ein später wiederverwendeter PID-Wert
   als dieselbe Instanz behandelt wird. Keine zusätzlichen Terminate-Rechte nötig.
   [Microsoft: GetProcessTimes](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getprocesstimes).
5. Parent-PIDs aus einem Toolhelp-Prozesssnapshot erfassen, sofort auf die eigenen
   Jobmitglieder/den bekannten Supervisor filtern, übrige Einträge nicht speichern.
   Parent-PID nur zusammen mit Erzeugungszeiten/gegebenenfalls bereits gehaltenem
   Elternhandle bewerten, nie als alleinigen Eigentumsnachweis.
   [Microsoft: PROCESSENTRY32W](https://learn.microsoft.com/en-us/windows/win32/api/tlhelp32/ns-tlhelp32-processentry32w).
6. Private Mitgliedschaft ist die Voraussetzung; IsProcessInJob(...,NULL) beweist
   lediglich irgendeine Jobmitgliedschaft. Einen bereits sicher bekannten eigenen
   Ancestor-Handle nur lesend vergleichen, falls verfügbar. Kein Ancestor suchen,
   öffnen, ändern oder beenden, um eine vollständige Hierarchie zu erzwingen;
   fehlende konkrete Ancestor-/Immediate-Zuordnung ausdrücklich offenlassen.
7. Höchstens zwei vollständige Populationssnapshots plus Abschluss-Snapshot,
   maximal acht gehaltene Mitgliederhandles und z.B. 256 KiB Diagnose. Technisches
   Beobachtungsbudget 10 s, Cleanupbudget 5 s als Vorschlag; vorhandene 25-s-
   Selbstendegrenze erhalten. Diese Grenzen garantieren keine Beendigung eines
   im Kernel hängenden API-Aufrufs. Die beobachtete Limitdiskrepanz verbietet die
   Zusage einer harten OS-Prozesszahl allein aus cap=3; bei mehr als acht sofort
   konservativ abbrechen, niemals unvollständig Erfolg erklären.
8. Erst nach persistierter Identitätsevidenz die unveränderte Assertion ausführen.
   Bei Zusatzmitgliedern wird sie weiterhin fehlschlagen. Keine weiteren Starts,
   nur kontrolliertes TerminateJobObject des eigenen Jobs, Accounting und vollständige
   Liste bis zum begrenzten Endnachweis; für gehaltene Handles tatsächliches Ende
   verlangen. Ein bereits beendetes/unerreichbares Mitglied nicht still als
   identifiziert oder erfolgreich beendet zählen. Unbestätigte Enden melden.
9. Alle selbst beobachteten IDs danach rein lesend kontrollieren; eine eventuell
   wiederverwendete PID nicht beenden. Eigene Handle-/Listenbilanz dokumentieren.
   Keine automatische Wiederholung, kein Kill nach Namen, keine Bereinigung fremder
   Prozesse. Das letzte Jobhandle erst nach dem Endnachweis schließen.

Dieser Einzelversuch könnte die Zusatz-PIDs identifizieren, beweist aber selbst
bei geklärten Konsolenhosts **noch nicht** alle ActiveProcessLimit-/Nested-/Firefox-
Fälle. Eine absichtliche zusätzliche normale JVM zur Quotenprüfung wäre ein weiterer
Auftrag und gehört nicht in diesen minimalen Versuch.

### 7. Entscheidung, Aufwand und Grenzen dieses Auftrags

Statischer Nachweis reicht **nicht** für eine eindeutige Herkunfts- oder
Durchsetzungserklärung. Empfehlung: nächste Freigabe ausschließlich für die
vorstehende Identitätsdiagnose, nicht für allgemeine Prototypszenarien oder
Produktintegration. Die separate Datenbank-/manual-test-/Scrapertrennung bleibt
unberührt; keine neue Live-Komponente im manuellen Artefakt.

Grobe Zusatzplanung: 0,5–1,5 Entwicklungstage für begrenzte Diagnoseanpassung,
statische Prüfung, einen freigegebenen Versuch und Auswertung. Die bisherigen
5–10 Tage für die spätere Gesamtimplementierung bleiben eine grobe Planungsgröße,
keine bestätigte Restdauer. Ungeklärte Limitdurchsetzung und reale Firefox-Sandbox-
Jobs können zusätzliche Arbeit auslösen. Kein dauerhaft laufender Dienst/Broker
oder Architekturwechsel allein aufgrund der bisher unbekannten PID-Identitäten.

Jetzt nur TESTING.md und ROADMAP.md ergänzt. Bestehende Prototypquellen,
Sicherheitsassertions, STOP-/Einmaldateien und historische Evidenz unverändert.
Keine Gradle-/Java-/JUnit-/DOM-/Prototypausführung, keine neuen synthetischen oder
Anwendungs-/HTTP-/Browser-/Scraperprozesse, keine DB-/SQL-Verbindungen, Migrationen,
neuen Dependencies/Installationen oder Git-Operationen. Normale Dateiprüfung und
textuelle offizielle Referenzlektüre sind keine neue technische Versuchsausführung.


## T02 – Einmalige Job-Identitätsdiagnose c59d32a9: drei Konsolenhosts identifiziert (19.10.2026)

**Genau ein freigegebener synthetischer Baumversuch; danach Sicherheitsstopp.**
Keine Produktintegration und keine zusätzlichen Timeout-/Crash-/Breakaway-/
Browser-/Lastszenarien. Historische Ergebnisse bleiben unverändert. Die damals
unbekannten alten PIDs werden nicht rückwirkend mit neuen Identitäten belegt.

### Vorbereitung, Quellen und einmalige Ausführung

Neu, nur im Experimentbereich: JobIdentityDiagnosis.java und
Run-JobIdentityDiagnosis.ps1. Alle bisherigen Experimentquellen einschließlich
JobPrototype.java, SyntheticProcess.java, JobListDiagnosis.java und Starter bleiben
unverändert. Der neue Ablauf verwendet deren geprüften atomaren Start und
Group-Cleanup sowie die ursprüngliche exakte Gleichheitsassertion.

Eigenes Verzeichnis:
.gradle/t02-job-prototype-20261016/identity-c59d32a9/.
Freigabekennung **T02_JOB_IDENTITY_20261019_c59d32a9** vor dem Start atomar per
FileMode.CreateNew reserviert, Flush(true) persistiert; Java legt attempt.started
mit CREATE_NEW an. Danach nicht wiederverwendbar, auch bei Fehler. Alte STOPs,
Einmalfreigaben und Evidenzdateien bleiben unverändert. attempt.finished lautet STOP.

Direkte javac-Kompilierung nur der vier Experiment-Java-Dateien mit vorhandenem
JNA 5.17.0; keine Gradle-Dependency. Prepare-JVM ohne synthetischen Worker:
Win64-PID-Layout, zusätzliche Kernel32-Exporte, GetProcessTimes am eigenen Prozess
und eigener gefilterter Toolhelp-Eintrag erfolgreich. PROCESSENTRY32W=568 Byte,
PID-Offset 8, ULONG_PTR-Offset 16, Parent-PID 32, Image-Array 44. PowerShell-Parser
und Vorhandensein der unveränderten Drei-PID-Assertion statisch geprüft.
Kein Browser-/Netzwerk-/DB-/Spring-/HTTP-Code. Java 21+35-LTS-2513/Win64 und JNA
5.17.0 geprüft, bekannte absolute Executable/Classpath-/Arbeitsverzeichnispfade.
Keine Git-/HEAD-Abfrage; Workspace vom Eigentümer vorgegeben.

Workerstart unverändert: private unbenannte, nicht vererbbare Jobgruppe,
JOB_LIST + CREATE_SUSPENDED + CREATE_NO_WINDOW, Resume erst nach Zuordnung,
kein Fallback. Der Aufruf verwendet genau einen nativen Workerstart, die
synthetische Klasse erzeugt weiterhin genau Kind und Enkel. cap=3 und Flags
unverändert. Abbruchgrenze bei mehr als acht beobachteten Gruppenmitgliedern,
zehn Sekunden Beobachtungsbudget, bestehende 25-Sekunden-Rollenfristen und
begrenztes Polling. Die Acht-Mitglieder-Grenze ist eine Beobachtungs-/Speichergrenze,
keine nachgewiesene zusätzliche OS-Zulassungsschranke.

Toolhelp-Snapshot nur lesend; unmittelbar auf private Gruppen-PIDs gefiltert,
fremde Prozesse nicht protokolliert oder geöffnet. Zusätzliche Handles nur aus
der privaten PID-Liste mit **0x101000 = QUERY_LIMITED_INFORMATION | SYNCHRONIZE**,
nicht vererbbar, ohne PROCESS_TERMINATE/SET_QUOTA/VM_WRITE. Parent-PID, Imagepfad,
FILETIME-Erzeugungszeit, PID am gehaltenen Handle, Wait-/Exitstatus und individuelle
private Jobmitgliedschaft wurden vor der ursprünglichen Assertion dauerhaft
gesichert. Sechs separate Mitgliederhandles bis nach Cleanup gehalten; damit
keine nachträgliche Identitätszuordnung anhand bloßer PID-Gleichheit.

### Vollständige Soll-/Ist-Population und Identitäten

Soll: Worker **20140**, Kind **69392**, Enkel **76348**.
Ist in Windows-Reihenfolge:
**[20140,40400,69392,30416,76348,20944]**.
Ist sortiert:
**[20140,20944,30416,40400,69392,76348]**.
Zusätzlich: **[20944,30416,40400]**.

Alle Zeitangaben der folgenden Tabelle UTC am 19.10.2026; volle
FILETIME-Ticks stehen zusätzlich im lokalen Trace.

| Rolle | PID | Imagepfad | Parent-PID | Erzeugungszeit UTC |
|---|---:|---|---:|---|
| Worker | 20140 | C:\Program Files\Java\jdk-21\bin\java.exe | 5932 (Diagnose-Supervisor) | 15:14:48.961801200 |
| Kind | 69392 | C:\Program Files\Java\jdk-21\bin\java.exe | 20140 | 15:14:49.198270100 |
| Enkel | 76348 | C:\Program Files\Java\jdk-21\bin\java.exe | 69392 | 15:14:49.366807700 |
| zusätzlicher Konsolenhost | 40400 | C:\Windows\System32\conhost.exe | 20140 | 15:14:49.052883100 |
| zusätzlicher Konsolenhost | 30416 | C:\Windows\System32\conhost.exe | 69392 | 15:14:49.220926600 |
| zusätzlicher Konsolenhost | 20944 | C:\Windows\System32\conhost.exe | 76348 | 15:14:49.383938200 |

Für **alle sechs Instanzen**: Imageabfrage erfolgreich, GetProcessId stimmt,
Erzeugungszeit verfügbar, IsProcessInJob(privater Handle)=true, WAIT_TIMEOUT=258,
Exitcode STILL_ACTIVE=259 vor Abbruch. Parent-PIDs stammen aus dem
Toolhelp-Snapshot; die jeweils zugehörigen Elterninstanzen sind gleichzeitig
gehaltene Gruppenmitglieder mit passender Erzeugungsreihenfolge. Der Workerparent
5932 ist der bekannte Supervisor außerhalb des privaten Jobs.

Damit ist die Konsolenhost-Hypothese **für diesen Lauf empirisch bestätigt**:
jedem Java-Prozess ist nach Parent-Metadaten und Erzeugungsreihenfolge ein conhost
zugeordnet. Das ist kein bloßer Schluss aus der Reihenfolge der PID-Liste.
Vollständiger Pfad/Instanz wurden identifiziert; keine zusätzliche Authenticode-/
Dateiintegritätsprüfung behaupten. Prozessnamen allein werden weder zum
Ignorieren von Gruppenmitgliedern noch zum Beenden verwendet. Die drei alten
Zusatz-PIDs vom 16.10. bleiben mangels damaliger Identitätsdaten historisch unaufgelöst.

### Limits und Zähler während derselben stabilen Population

Ziel bei allen privaten Messungen: **Group.job = native@0x4c8**, im aktuellen
Supervisor erzeugt, unbenannt/nicht vererbbar; Supervisor selbst nicht Mitglied.
Die beiden vollständigen PID-Snapshots vor und nach der Identitätsmessung sind
identisch; sämtliche sechs gehaltenen Handles waren während der Identitätsabfrage
aktiv. Abfragen erfolgen nacheinander, nicht als ein atomarer OS-Gesamtsnapshot.

| Messung | Ergebnis |
|---|---|
| BasicLimit, Klasse 2 | true/Fehlerdiagnose 0, 64 Byte/ReturnLength 64, flags=8200=0x2008, cap=3 |
| ExtendedLimit, Klasse 9 | true/Fehlerdiagnose 0, 144 Byte/ReturnLength 144, flags=8200=0x2008, cap=3 |
| Feldpositionen | Flags bei Offset 16, ActiveProcessLimit bei 40; begrenzte rohe Strukturdaten gesichert |
| Zeitpunkte | vor Start, vor Resume, vor/nach Identitätsmessung und vor Cleanup jeweils gleich |
| PID-Liste während Identitätsmessung | true/0, assigned=6, listed=6, ReturnLength=56 bei 72-Byte-Puffer, beide Leser gleich |
| BasicAccounting während Identitätsmessung | total=6, active=6, wegen Limitverletzung terminiert=0 |
| Suspended-Snapshot | nur Worker 20140; active=1, ReturnLength=16 |
| Supervisor-Job, nur lesend via NULL | eigener unmittelbarer Job, nicht privates Ziel: Basic/Extended flags=0x2000, cap=0 (ACTIVE_PROCESS nicht gesetzt) |
| Hierarchienachweis | Supervisor any_job=true, private_job=false; konkrete Ancestorhandles und unmittelbare Jobebene jedes Mitglieds unbekannt |

**Die sechs lebenden Instanzen und das aktivierte Limit-Flag mit cap=3 bestanden
in derselben beobachteten Messphase.** Fehlendes Flag, nur gespeicherter Zahlenwert,
falscher NULL-Zieljob, Trunkierung oder bereits beendete Zusatz-PIDs erklären
diesen neuen Befund nicht. Es sind drei lebende Java-Prozesse plus drei lebende
Konsolenhosts, keine sechs unbekannten Java-Worker.

Das grenzt die bisherige Diskrepanz entscheidend ein, erklärt jedoch nicht
abschließend die Windows-interne Quotenbehandlung. Aus diesem einen Versuch folgt
**keine normative conhost-Ausnahme** vom ActiveProcessLimit und kein Beweis, dass
eine vierte gewöhnliche Java-JVM tatsächlich abgewiesen würde. Es wurden weder
weitere Quotenstarts noch ein zweites Job-/Breakaway-Szenario ausgeführt.
Eigene private Zähler können Kindjobs aggregieren; ihre genaue Hierarchie wurde
nicht enumeriert. Die mögliche Wirkung verschachtelter Jobs bleibt von der nun
geklärten Image-/Parent-Identität getrennt. Kein Windows-/JNA-Bug pauschal behauptet.
Der statisch dokumentierte Referenzvertrag wurde in diesem Auftrag nicht erneut
online abgerufen: externe Netzwerkzugriffe waren nicht freigegeben.

### Assertion, Sicherheitsstopp und vollständiger Endnachweis

Nach persistierter Identitäts-/Limitdiagnose wurde unverändert
Group.capture(tree,worker,child,grandchild) aufgerufen. Dessen ursprüngliches
**actual.equals(ids)** erwartete die drei Rollen und lieferte wie vorgegeben
IllegalStateException: unexpected job membership. Keine Teilmengenprüfung,
keine Namensausnahme, keine zweite Ausführung und kein nachträgliches Grünreden.

Danach nur Cleanup des nachweislich eigenen privaten Jobs native@0x4c8:
TerminateJobObject true, vollständige Liste leer (assigned/listed=0/0,
ReturnLength=8), Accounting active=0, total bleibt 6. Alle sechs separat
gehaltenen Instanzen WAIT_OBJECT_0=0, Exitcode 91; GetProcessTimes bestätigt
für jede exakt dieselbe Erzeugungszeit wie vor Cleanup. Damit tatsächliches Ende
aller bekannten Instanzen einschließlich Konsolenhosts belegt, nicht bloß
Workerende oder ein erfolgreicher Terminate-Rückgabewert.

Endgültig: vollständige Liste erneut leer, active=0, alle Mitgliederhandles
geschlossen, **0 getrackte Handles, 0 Attributlisten, 1 nativer Workerstart**.
Alle sechs beobachteten PIDs danach ausschließlich lesend kontrolliert:
keine vorhanden (remaining-process-check.json). Keine unbekannten/restlichen
Instanzen oder nicht bestätigten Enden in diesem Lauf. Kein einzelner PID-Kill,
keine globale Suche/Beendigung nach Namen, kein Ancestor-Job beendet.

Cleanup vom ersten cleanup_begin bis zum Abschluss aller Identitäts-/Endprüfungen
und FINAL_GROUP etwa **236 ms**, kein Last-/Worst-Case-Nachweis. Präzisierung des
Prototypbudgets: Die protokollierten fünf Sekunden beziehen sich auf das
ActiveProcesses-Polling; zusätzliche sequenzielle Handle-Waits besitzen eigene
begrenzte Fristen. Noch kein durchgängiges globales Fünf-Sekunden-Cleanupbudget
implementiert oder nachgewiesen. Native Kernelhänger bleiben eine technische
Grenze; spätere Produktintegration benötigt einen gemeinsamen monotonen
Restzeitrahmen. Keine Reparatur dieser Implementierungsfrage nach dem Versuch.

Evidenz: diagnostic.tsv **159 Zeilen/26.046 Byte** inklusive Prepare und Run,
vor Assertions mit force(true) geschrieben; begrenzt auf 512 Ereignisse/
4 KiB je Ereignis/1 MiB je JVM. Die zusätzliche rohe Limitkonfiguration ist
enthalten, keine Secret-/Environment-/Nutzerdatenausgaben.

### Konsequenz für Variante B und nächste Entscheidung

**Kein Architekturwechsel begründet:** private Jobgruppe umfasst in diesem Lauf
sämtliche sechs identifizierten Nachfahren, kontrollierte Beendigung einschließlich
OS-Konsolenhosts erfolgreich. JNA kann Identität, Limits und vollständigen
Endzustand messen. Eine exakt drei OS-Prozesse umfassende Population war die
unbestätigte Erwartung; die echte Gruppe enthält hier legitime zusätzliche
Konsolenprozesse. Das erklärt die Assertion, nicht automatisch alle Quotenregeln.

Grundsätzliche Vorbereitung von Variante B ist jetzt sinnvoll: Komponenten-/
Testdesign, explizite Job-Eigentümerschaft und separate manual-test-Paketgrenze.
**Keine breite produktive Workerimplementierung auf diesem Nachweis freigeben.**
Vor einer Implementierungsfreigabe sind mindestens erforderlich:

- Eigentümerentscheidung zur künftigen synthetischen Erwartung: drei bekannte
  Java-Rollen plus nachgewiesene zugehörige OS-Prozesse bzw. vollständig
  identifizierte eigene Gruppe. Bestehende Assertion bis dahin unverändert lassen.
  Keine allgemeine Namens-Allowlist oder tolerierte unbekannte PID.
- Begrenzte Prüfung der ActiveProcessLimit-Durchsetzung für gewöhnliche
  Nachfahren und ihrer Rolle bei verschachtelten Jobs; eigene Freigabe nötig.
  Keine harte Gesamtprozessgarantie allein aus cap-Readback versprechen.
- Ausstehende Lifecycle-Prototypfälle (Kill-on-close/Handlevererbung,
  Supervisor-Crash, blockierter Worker/Timeout, Nested-Limits) einzeln freigeben,
  striktes Slot-/Cleanup-Design mit gemeinsamem Deadlinebudget anschließend abnehmen.
- Echte Firefox-/Geckodriver-Sandbox-/Profil-/Nachfahrenprüfung erst separat
  freigeben. Kein heutiger Lauf belegt Browserkompatibilität.
- Keine Live-Worker-/JNA-Startpfade in manual-test einführen; synthetische
  Gateway-Doubles und DB-Guards unverändert erhalten.

### Geänderte Dateien und Grenzen

Neu: experiments/windows-job-prototype/JobIdentityDiagnosis.java und
Run-JobIdentityDiagnosis.ps1. Ergänzt: README.md im selben Bereich, TESTING.md
und ROADMAP.md. Alle vorbestehenden Java-/PowerShell-Prototypquellen einschließlich
Sicherheitsassertions/Limits unverändert; Historie/STOP-/Freigabedateien erhalten.
Kein weiterer Prozessversuch nach diesem Stopp.

Keine MangaLib-Produktions-/Gradle-/Guard-/Security-/Importänderungen,
keine DB-/SQL-Verbindung, Migration, Spring-/HTTP-/manuelle Anwendung,
Firefox/Geckodriver/Selenium oder Live-Scraper. Keine externen Netzwerkaufrufe,
neuen Abhängigkeiten, Installationen oder Git-Operationen. Nur direkte
Experiment-Kompilierung, Prepare ohne Worker und genau ein freigegebener Baum.


## T02 – Sicherheits- und Testspezifikation Variante B, Entwurf B-SPEC-1 (19.10.2026)

**Abnahmeentwurf, keine Implementierungs- oder Ausführungsfreigabe.** Dieser
Nachtrag konsolidiert die bisherigen Nachweise und definiert überprüfbare Ziele.
MUSS bezeichnet eine vorgeschlagene Abnahmebedingung, nicht bereits vorhandenen
Produktcode. Keine historischen Assertions/STOPs/Freigaben/Evidenzen ändern.

### 1. Gesicherter Umfang und offene Zusagen

| Eigenschaft | Nachweisstand |
|---|---|
| Atomare Worker-Zuordnung mit JOB_LIST vor Resume | Im bisherigen Win64-/Java-21-/JNA-5.17.0-Kontext synthetisch belegt; keine allgemeine Umgebungs-/Browsergarantie. |
| Dynamische Zusatzprozesse im eigenen Job | Im letzten Lauf drei Java-Rollen plus drei individuell identifizierte conhost-Instanzen; Parent-/Erzeugungsdaten und private Mitgliedschaft bestätigt. |
| Kontrollierter Gruppenabbruch | Für genau diese sechs Instanzen belegt: eigene Gruppe leer, gehaltene Handles signalisiert, gleiche Erzeugungszeiten, Ressourcenbilanz 0. |
| Exakte Drei-PID-Assertion | Unverändert historisch rot; keine neue Akzeptanzregel in den Prototyp eingebaut. |
| Harte maximale Gesamtprozesszahl | **Nicht nachgewiesen; Freigabeblocker für diese Zusage.** Aktiviertes cap=3 koexistierte mit sechs individuell aktiven Mitgliedern. |
| Kill-on-close, Handlevererbung/-duplikate, Supervisor-Crash | Nicht als vollständiger Lebenszyklusnachweis abgenommen. |
| Ein Slot, späte Callbacks, Shutdown-Rennen, gemeinsame Deadline, IPC | Nur spezifiziert, kein produktiver Supervisor und keine neue Testausführung. |
| Gesamtspeicher-/CPU-Limits, echte Quotenwirkung | Noch nicht für das geplante Laufzeitmodell abgenommen. |
| Firefox-/Geckodriver-Kompatibilität | Nicht geprüft; spätere ausdrücklich getrennte Offline-Abnahme erforderlich. |

Die Identifikation der Konsolenhosts erklärt die zusätzliche Population dieses
Laufs, nicht normativ ihre Quotenbehandlung. Konfiguration, erfasste Gruppen-
mitgliedschaft, Einzelprozesszustand und Limitdurchsetzung bleiben getrennte
Nachweise. Die dokumentierten 236 ms Cleanup sind kein Worst-Case-Budget.

Geltungsbereich: **eine MangaLib-Anwendungsinstanz**, Windows/Java 21. Eine
rechnerweite Ein-Scraper-Garantie über mehrere unabhängig gestartete MangaLib-
Instanzen folgt daraus nicht; Eininstanzbetrieb ist gegebenenfalls T07.
Job Objects sind keine Netzwerk-/Benutzer-/Dateisystem-Sandbox. SSRF, Redirects
und Browser-Unterressourcen bleiben ein eigener T02-Auftrag.

### 2. Sicherheitsinvarianten mit überprüfbarem Nachweis

U = mit reinen Unit-/Adapter-Fakes prüfbar; O = späterer OS-Nachweis zusätzlich
zwingend; A = Artefakt-/Vertragsprüfung. Alle künftigen Tests einzeln freizugeben.

| ID | Vorgeschlagene MUSS-Invariante | Akzeptanz / benötigter Nachweis |
|---|---|---|
| B01 Admission | Genau ein Live-Auftrag für Reihe ODER Band einschließlich Start, I/O und Cleanup zugelassen. Keine Warteschlange. | U: Gleichzeitige Aufrufer erhalten höchstens ein Owner-Ticket; der zweite erzeugt keinen nativen Aufruf. Slot bleibt bis B07 belegt. |
| B02 Eigentum | Neues unbenanntes privates Job je Ticket, nicht vererbbares Jobhandle, Kill-on-close gesetzt, Breakaway aus, Supervisor außerhalb dieser Gruppe. | U+O: ABI/Flags/Rücklesen, Mitgliedschaft und Verhalten bei vorhandenem Ancestor-Job; kein Job-Handle über Kind-Handle-Allowlist. |
| B03 Atomarer Start | Feste vertrauenswürdige Java-/Artefaktpfade und reduzierte Umgebung; CreateProcessW mit JOB_LIST und SUSPENDED. Resume erst nach bestätigter Zuordnung. Kein ungeschützter Fallback. | U+O: Vor Resume kein Worker-Code; Fehler vor/nach Zuordnung hinterlässt keine unkontrollierte Instanz. Attributspeicher bleibt bis nativer Rückkehr/Listenlöschung gültig. |
| B04 Nachfahren | Unterstützte Worker-/Gecko-/Firefox-Startwege halten sämtliche zugehörigen Nachfahren in der privaten Gruppe einschließlich eventueller Kindjobs. | O: Variable Population, Parent-Ende und Kindjobfälle; keine Sandboxabschaltung/Breakaway-Reparatur. Nicht eingegrenzte OS-Broker außerhalb der Gruppe sind ein Abnahmeblocker dieses Startwegs. |
| B05 Identität | Ein beobachteter unbekannter PID wird zunächst PENDING, nicht automatisch harmlos. Native Mitgliedschaft und gehaltene Instanzidentität klären. | U+O: PID/Handle/Erzeugungszeit und soweit für Rollenzuordnung nötig Image; Namen oder Parent-PID allein genügen nie. Identitätsfehler => Stopp/Cleanup und Quarantäne. |
| B06 Gruppenkontrolle | Beenden ausschließlich über den gültigen, ticketgebundenen privaten Jobhandle. Keine Namen-, PID- oder Ancestor-Kills. | U+O: Fremder Kontrollprozess bleibt aktiv; numerische Handle-/PID-Wiederverwendung und alte Callbacks können keine neue Gruppe treffen. |
| B07 Freigabebarriere | Neuer Auftrag erst nach vollständiger Endkonjunktion C unten. Workerexit, leere Liste oder erfolgreicher Terminate-Aufruf einzeln reichen nicht. | U: jede falsche C-Komponente verhindert Freigabe; O: echte End-/I/O-/Handlebelege. |
| B08 Quarantäne | Unklare Identität, ungeklärter Gruppenabschluss, ungültiges Eigentum oder ausstehender nativer Start nach Ende der Cleanupfrist sperren Live-Starts. | U+O: kein geplanter Retry, kein spontaner zweiter Worker, kein Reset durch HTTP-Aufruf. Späte Meldungen bleiben demselben Ticket zugeordnet. |
| B09 Erfolg | Payload nur nach gültigem Protokoll-SUCCESS, Workerexit 0 und C liefern. | U+A: Ungültige/partielle Daten oder unbestätigter Cleanup erzeugen keinen scheinbaren Importerfolg und keine nachgelagerten DB-Referenzabfragen. |
| B10 Shutdown | Erst Admission schließen, dann eigenen aktiven Auftrag kontrolliert bereinigen; kein Resume/Neustart nach Shutdown-Entscheidung. | U+O: Rennen zwischen Start/Resume/Shutdown; fehlender Endnachweis bleibt sichtbar QUARANTINED, nicht still CLOSED. |
| B11 Budgets | Alle Fristen und Speicher-/Ausgabemengen explizit, messbar und nach Zusagestärke klassifiziert. | U+O: gemeinsame monotone Deadline, Bytes vor Allokation prüfen; keine harte Prozess-/RAM-/Wallclock-Zusage aus bloßer Konfiguration ableiten. |
| B12 Artefaktgrenzen | Worker ohne Spring/DB/HTTP-Server; manual-test ohne Live-Worker-/Adapter-/Selenium-Startpfad. | A+U: Archiv-/Abhängigkeits- und negative Aufrufprüfungen, vorhandene DB-Guards/Security/M06-/Legacy-/JSON-Assertions erhalten. |

**Endkonjunktion C, ticketgebunden:** kein nativer Start/Resume mehr ausstehend;
alle Launchressourcen eindeutig verbucht; am eigenen noch offenen Job
ActiveProcesses=0 UND vollständige PID-Liste leer; alle bereits gehaltenen
Prozessinstanzen signalisiert; keine ungeklärten Identitäts-/Containmentbefunde;
stdout/stderr-Reader beendet, Requestwriter fertig, Pipe-Enden geschlossen;
keine ausstehenden Callbacks, die noch Ressourcen oder Prozesse erzeugen können;
eigene Prozess-/Thread-/Attribut-/Jobhandles ordnungsgemäß abgeschlossen bzw.
deren Abschluss vor Slotfreigabe bestätigt; eigene temporäre Ressourcen sicher
abgeschlossen. Der Gruppenendnachweis erfolgt vor Schließen des letzten Jobhandles.
Nicht jeder jemals kurzlebige Nachfahre muss einen im Voraus bekannten PID besitzen.

Wenn eine erforderliche Abfrage fehlschlägt, ist C **unbekannt**, nicht true.
Ein Terminate-Erfolg, Threadinterrupt, zukünftiger Reader-EOF oder geschlossener
Handle ersetzt keinen Gruppenendnachweis. Completion-Port-Nachrichten können
Beobachtungen anstoßen, sind kein alleiniger Vollständigkeits-/Endbeweis.

### 3. Zustandsautomat und Freigaberegeln

Vorgeschlagene kleine Zustandsmaschine, zentral serialisiert/atomar geschützt:
UNAVAILABLE → IDLE → STARTING → RUNNING → CLEANING → IDLE.
Fehler dürfen RUNNING überspringen, niemals CLEANING/Endnachweis.
QUARANTINED ist eine gesperrte Fehlerlage; shutdownRequested ist zusätzlich
monoton und verhindert Admission in jedem Zustand.

| Zustand | Erlaubt / Übergang |
|---|---|
| UNAVAILABLE | Fehlende unterstützte Plattform/JNA/Artefakte/abgenommene Pflichtbudgets: keine Live-Starts. manual-test benötigt den Adapter grundsätzlich nicht. |
| IDLE | Genau ein tryAcquire kann neues Ticket/Generation und STARTING setzen. Kein hypothetischer Prozessstart außerhalb des Owners. |
| STARTING | Owner hält Job/Attribute/Pipes; native Rückkehr und Zuordnung prüfen. Deadline/Shutdown erlaubt keinen Ersatzworker. |
| RUNNING | Ein laufender Auftrag, kontinuierlich begrenzte I/O und Gruppenüberwachung. Ein zweiter Gateway-Aufruf wird ohne native Arbeit abgewiesen. |
| CLEANING | Normales Ende, Fehler, Abbruch oder Deadline: idempotent denselben Cleanup abarbeiten; nur Owner darf freigeben. |
| QUARANTINED | Neue Live-Aufträge abweisen; vorhandene eigene Ressourcen weiter sicher verwalten, keine fremde Ersatz-Terminierungsstrategie und kein automatischer Neustart. |
| CLOSING/CLOSED | shutdownRequested zuerst setzen. CLOSED erst nach C bzw. ohne je gestarteten Auftrag und leerer Ressourcenbilanz; bei fehlendem Nachweis bleibt Quarantäne sichtbar. |

Ein gewöhnlicher Workerfehler/Timeout kann nach **bestätigtem** C zu IDLE führen;
es wird kein Auftrag automatisch erneut gesendet. Erst ein neuer expliziter
Nutzerrequest kann einen neuen Auftrag auslösen. Ein Identitäts-/Eigentumsincident
bleibt dagegen auch nach bestätigter Beendigung gesperrt, bis ein ausdrücklich
festgelegter Wiederfreigabeschritt erfolgt. Das vermeidet die Verwechslung von
fehlgeschlagener Extraktion mit ungeklärter Sicherheitslage.

Jede Nachricht/Ressource hat Ticket+Generation. Auch bei gleicher numerischer
Win32-Handleadresse darf ein Callback eines alten Tickets keinen neuen Owner
schließen/freigeben. Native CreateProcess-Aufrufe lassen sich nicht durch
Future.cancel zuverlässig abbrechen: bei ausstehender Rückkehr bleiben
Attributspeicher/Owner gültig, der Slot gesperrt, und ein verspätet zurückkehrender
Prozess wird über denselben Owner kontrolliert bereinigt. Keine zweite Native-
Startoperation parallel als Verfügbarkeitsbehelf.

Quarantäne-Wiederfreigabe ist eine offene Betriebsentscheidung: keine automatische
Entsperrung durch Timer oder bloßen Appneustart mit behauptetem Altgruppenende.
Erforderliche lokale Persistenz/Operatorprüfung eines ungeklärten Vorauftrags
vor Implementierung festlegen; keine neue Datenbank oder Hintergrunddienst dafür.

### 4. Dynamische Population: vorgeschlagene Akzeptanz ohne Änderung der Historie

Die ursprüngliche exakte Drei-PID-Assertion in JobPrototype.java:160 bleibt
unangetastet. Der folgende produktive Vertrag ist ein neuer Entwurf:

- Native Mitgliedschaft im **eigenen konkreten Job** bestimmt die Cleanup-Gruppe,
  nicht ein PID-Präfix, Programmname oder Parent-Snapshot. Any-job-Mitgliedschaft
  allein genügt nicht. Handle-, PID- und Erzeugungszeitdaten gegen Wiederverwendung
  zusammenhalten.
- Neue Mitglieder können erwartete Browser-Unterprozesse oder zusätzliche Rollen
  sein. Beobachtete unbekannte Instanzen werden innerhalb eines begrenzten
  Diagnosefensters identifiziert; keine conhost-/firefox-Namensausnahme.
  Mitgliedschaft belegt Eigentums-/Containmentbereich, nicht ungefährlichen Code.
- Imagepfad/Rollenzuordnung ist eine gesonderte Prüfung. Parent-PID und Reihenfolge
  dienen Diagnose, nicht als alleiniger Eigentumsbeweis. Bei Elternende kann
  Parent-Identität fehlen; eine über gehaltenen Handle sicher bestätigte private
  Mitgliedschaft wird deshalb nicht in eine fremde Gruppe umgedeutet.
- Kann eine beobachtete Instanz nicht eindeutig identifiziert werden (auch wegen
  schnellem Ende zwischen Snapshot und OpenProcess), dies nicht als Identitätserfolg
  ausgeben. Nach jetzigem konservativem Vorschlag: eigener Gruppen-Cleanup und
  Quarantäne, kein automatischer Retry. Eine spätere Ausnahme für beweisbar
  kurzlebige Instanzen braucht eine eigene fachliche Freigabe/OS-Evidenz.
- Snapshotliste ist ein Zeitpunkt, die Instanzregistratur ein Verlauf. Aggregiertes
  TotalProcesses zählt Historie, ActiveProcesses kann besondere Übergangszustände
  haben; weder ersetzt die held-handle-Liste die native Gruppe noch belegen
  mehrere getrennte API-Aufrufe einen atomaren Snapshot.
- Normale dynamische Änderung ist nicht automatisch ein Fehler. Listen können sich
  zwischen zwei Abfragen ändern; neu Beobachtetes muss verbucht und Grenzen
  dürfen nicht übersehen werden. Keine unbeschränkte Retry-bis-stabil-Schleife.
  Abfragepuffer, Beobachtungsfrist und maximale Registratur explizit begrenzen;
  Trunkierung oder erschöpfte Diagnosefähigkeit => Abbruch, kein Teilmengen-Erfolg.
- Nicht jeder kurzlebige Nachfahre ist per Polling historisch lückenlos erfassbar.
  Die Gesamtzuordnung muss daher durch den abgenommenen Kernel-Start-/Vererbungsweg
  getragen werden. Eine Zusage, jede jemals gestartete Instanz individuell
  identifiziert zu haben, wird nicht aus Stichproben abgeleitet.
- Erfolg erst bei C. Vorher darf ein leeres Job keinen neuen Slot zulassen, solange
  z.B. CreateProcess, Pipes, Reader oder Ressourcenabschluss ausstehen.

Die vorgeschlagenen produktiven Akzeptanztests müssen variable Anzahlen mit
identifizierten Rollen akzeptieren und unaufgelöste Mitglieder abweisen. Das ist
keine heutige Lockerung des historischen Experiments und keine allgemeine
Vertrauensliste für beliebige gleichnamige Programme.

### 5. Ressourcenbudgets und ausdrücklich blockierte Sicherheitszusagen

| Budget | Durchsetzung / Grenze | Vor Freigabe erforderlich |
|---|---|---|
| Ein Auftrag | Supervisor-Admission pro Appinstanz, nicht OS-Prozesszahl. | Unit-Interleavings und Gateway-Vertrag. |
| Start-/Gesamtlaufzeit | Gemeinsame monotone Frist ab Slotbelegung; bei responsivem Supervisor eigener Gruppenabbruch, dann eigenes Cleanupbudget. | OS-Timeouttest und Late-start-Fakes; keine absolute Wallclock-Garantie bei pausiertem/hängendem Supervisor oder Kernel. |
| Cleanupzeit | Eine globale Deadline mit Restzeit für alle Waits, Reader und Ressourcen; kein neues Vollbudget je Handle. | Neue reine Clock-/Wait-Fakes und OS-Abnahme. Die bisherigen fünf Sekunden Polling plus Einzelwaits sind noch kein solcher Nachweis. |
| Gesamtprozesszahl | ActiveProcessLimit nur als konfigurierte OS-Quote bekannt. Beobachtungsgrenze der PID-Liste kann Überschreitung nachträglich erkennen. | **Blocker:** harte maximale Anzahl aller OS-Mitglieder einschließlich Konsolen-/Browserhelfer derzeit nicht zusagbar. Polling/Anzahl von Workerstarts ersetzen keine präventive Quote. |
| Commit-/Prozessspeicher | Win32-Job-/Process-Memory-Limits als Kandidaten; nicht mit physischem RAM/RSS/GPU/Dateicache oder fremden Brokern gleichsetzen. | Begrenzte synthetische Zuteilungs-/Fehler-/Nested-Tests; verwendete Windows-Semantik verifizieren, konkrete Werte freigeben. |
| Worker-Java-Heap | Xmx begrenzt nur Java-Heap dieses Workers. | Kein Gesamtbudget für Firefox, Gecko, Metaspace, native/Direktspeicher. |
| CPU | Job-CPU-Rate/-Zeit als optionaler eigener Kandidat; CPU-Zeit ist keine Wallclock. | Explizite Anforderung und eigener Wirksamkeits-/Umgebungstest, keine Ersatzgarantie für Prozessquote. |
| IPC-/Parent-Speicher | Frame-/Gesamtbyte-/Parser-/Queue-/Readergrenzen vor Allokation und gleichzeitigem Puffern erzwingen. | Unit-Negativfälle inkl. Unicode/10.000-Bände-Vertrag; End-to-end-OS-Pipes später. |
| Temporäre Dateien/Profile | Eigene kanonische Wurzel und sichere begrenzte Ablage; Job Objects sind keine allgemeine Datenträgerquote. | Kein Löschen außerhalb eigener Instanzwurzel, keine Junction-/Reparse-Verfolgung; spätere Browserabnahme. |

Für eine **harte Zusage Prozessanzahl<=N für die gesamte Gruppe** ist zuerst ein
entsprechender Nachweis nötig. Gelingt er im vorgesehenen Jobmodell nicht, bleibt
genau diese Zusage blockiert. Sie darf weder durch größere Zahlen noch durch
Herausrechnen von conhost nach Namen scheinbar erfüllt werden. Über ein anderes,
ehrlich begrenztes Ressourcenversprechen muss der Eigentümer ausdrücklich
entscheiden; dieser Entwurf setzt keine schwächere Zusage eigenständig in Kraft.

Geplante separate Quotenprüfung: frisches privates Testjob je erlaubtem Fall,
kleine freigegebene Quote, begrenzte gewöhnliche synthetische Prozesse bis zur
Quote und genau ein zusätzlich versuchter Start. Erzeugungs-/Zuordnungsfehler,
Ausführungsmaker, native Limits, alle Instanzidentitäten, vollständige Liste und
Accounting einschließlich Limitbeendigungen erfassen. Die überzählige gewöhnliche
Instanz darf nicht unkontrolliert Worker-Code ausführen. Konsolenhostzahlen
gesondert beobachten, keine Ausnahme voraussetzen. Danach erst separat direkte
und verschachtelte eigene Jobebenen vergleichen, ohne vorhandene Ancestorlimits
zu ändern. Jede Prüfung neue Freigabe, feste Startzahl, unabhängiger Cleanup und
unveränderte historische Evidenz; kein Sammelstart aus diesem Konzept.

Numerische Produktionsbudgets noch offen: Start/Handshake, Bandlaufzeit,
Reihenlaufzeit, Cleanup, Commit/CPU, Prozessquote sowie Ergebnis-/Diagnosegrenzen.
Bisherige Vorschläge (z.B. 32 MiB Ergebnis, 64 KiB Diagnosepuffer) sind Prüfkandidaten.
MangaScraper.java:141 wartet schon 150 ms je Band; 10.000 Bände bedeuten mindestens
25 Minuten allein für diese Pausen. Keine stille kurze Reihenfrist, die bisher
zulässige Importe abschneidet. Deadline-Verlängerung durch wiederholte Frames/
Progressmeldungen ist ebenfalls nicht zulässig.

### 6. Kleine Komponenten und vorhandene Integrationspunkte

Nur Entwurf; keine der folgenden Klassen/Schnittstellen wurde jetzt angelegt.

| Baustein | Verantwortung / schmaler Vertrag |
|---|---|
| WindowsJobApi / WindowsJobProcess | Private Job-/Pipe-/Prozesshandles und Attributspeicher; create, suspended spawn, resume, bounded snapshot, terminate, wait, close mit Win32-Ergebnis/Fehlercode. Identität und Eigentums-Ticket explizit; austauschbarer Fake, kein Spring-/HTTP-Wissen. |
| ScraperSupervisor | Ein gemeinsamer Reihen-/Band-Slot, Ticket-/Zustandsautomat, gemeinsame Fristen, Ressourcenregister, Ergebnis erst nach C, Quarantäne/Shutdown. Keine Extraktionsselektoren oder DB-Zugriffe. |
| ScraperProtocol | Versions-/Request-ID-/Operation-/Index-/Ergebnisrahmen; begrenzte UTF-8-Kommunikation über private Pipes, keine Netzwerkports. Keine beliebigen Programm-, Klassen-, URL- oder Timeoutwerte aus HTTP. |
| ScraperWorkerMain / Worker-Artefakt | Genau ein Auftrag je JVM; kein SpringApplication, JDBC/JPA, HTTP-Server oder Laden normaler application.properties. Nur benötigte Extraktion/Selenium/JSON mit expliziten Abhängigkeiten. |
| Manga-/Bandextraktion | Vorhandene Selektoren, Schlüssel, optionalen Feldfallbacks und Preisstrings erhalten; Drivererzeugung/-verwendung/-quit an testbarer Lifecycle-Grenze im Worker. Keine fachlichen Preis-/Lesestatusänderungen. |
| LiveScraperGateway | Bisherige scrapeSeries(String)/scrapeVolume(String) → Map<String,String> erhalten, nur Delegation zum Supervisor. Kein Workerstart im Bean-Konstruktor, kein zweiter statischer Bypass. |
| manual-test | Nur Shared-Gateway und vorhandene SyntheticScraperGateway-Doubles; native Live-Adapter/Workerartefakt/Launcher/Selenium strukturell ausgeschlossen. |

Aktuelle Fundstellen: ScraperGateway.java:6–8 und LiveScraperGateway.java:10–16
delegieren statisch an MangaScraper/BandScraper. MainController.java:259–303
validiert den Import und ergänzt Referenz-IDs; BandService.java:598–619 behält
BildUrl, Preis ohne Eurozeichen und mpUrl. Diese Maps/JSON-Verträge bleiben die
Regressionserwartung. Die beiden heutigen Drivererzeugungen stehen vor dem try,
Quitfehler werden abgefangen; ein technischer Workerfehler darf künftig nicht
ungeprüft als SUCCESS-Map erscheinen. Umgang mit bisher verschluckten technischen
Teilerfolgen vor Implementierung ausdrücklich entscheiden; optionale fachliche
Feldfallbacks nicht stillschweigend zu neuen Mussfeldern machen.

Private IPC: explizite vererbbare Pipe-Enden über Handle-Allowlist, Jobhandle
niemals vererben. Request klein/längengerahmt; ein Ergebnis mit passender Version,
ID, operation und festem SUCCESS/ERROR-Status. Kein Java-Object-Deserialisieren,
keine polymorphen Typnamen, doppelten JSON-Schlüssel oder zusätzlichen Frames.
stdout nur Protokoll, stderr separat begrenzt drainen; kleines Speicherlog allein
begrenzt nicht die insgesamt ausgegebenen Bytes. Gesamtausgabebudget mit
Auftragsabbruch und begrenzte Parserstrukturen erforderlich. Stderr nicht als
ungefilterten HTTP-/Appfehler ausgeben. Abbruch muss auch blockierte Pipe-Leser/
Writer nach Gruppenende beenden; Threadinterrupt alleine genügt nicht.
Parent prüft Map weiter über WriteValidation.scrapedData.

32-MiB-Ergebniskandidat gegen synthetische maximale 10.000-Bände-Maps mit Unicode/
Escape-Zeichen prüfen; Bodylimit, Protokollframe und tatsächlicher gleichzeitiger
Parent-Heapbedarf sind verschiedene Größen. Keine zweite unbeschränkte Kopie,
Ausgabequeue oder Readererzeugung je abgewiesenem Request.

build.gradle.kts:46–95 trennt bereits manualTest/manualCheck/manualBootJar,
entfernt LiveScraperGateway/MangaScraper/BandScraper sowie Selenium/WebDriverManager.
Es gibt dort noch keine künftige Worker-/JNA-Exclusion: diese muss bei späterer
Integration explizit ergänzt und im **gebauten Archiv inklusive nested JARs**
negativ geprüft werden. Keine Abwesenheit aller JNA-Transitiven aus heutiger
Quelltextfilterung behaupten. Der gemeinsame Gateway darf keine Typreferenz/
Initialisierung des nativen Supervisors benötigen. Fehlende manuelle Doubles
bleiben fail-closed, niemals Live-Fallback.

### 7. Priorisierte, begrenzte Testmatrix und getrennte Freigaben

Jede Zeile ist ein eigener vorgeschlagener Abnahmeumfang; **kein Test jetzt
ausgeführt**. Betriebssystem-/Browserstufen erst nach eigener expliziter Freigabe,
ein Fall nach dem anderen, eigene Evidenz/Einmalfreigabe, sofortiger Stopp bei
unklarer Identität/Containment/Cleanup. Keine unbegrenzten Stress-/Retry-Schleifen.

| Priorität / ID | Begrenzter Umfang | Typ / eindeutiger Passnachweis |
|---|---|---|
| P0 U-ADMIT | Reihen/Reihen und Reihen/Band gleichzeitig, Busy, Start-/Cleanupfenster | Rein Unit: maximal ein Native-Start, keine Queue/zweite Instanz, keine Freigabe vor C. |
| P0 U-OWNER | Altes Ticket, doppelte Callbacks, numerisch wiederverwendete Handles, späte Native-Rückkehr | Rein Unit-Fakes: kein fremdes/neues Ticket betroffen, kein Freigeben bei ausstehendem Start. |
| P0 U-END | Jede Komponente von C einzeln false/unknown; leerer Job mit blockiertem Reader | Rein Unit: stets gesperrt/quarantiniert, keine Erfolgspayload oder unberechtigte Freigabe. |
| P0 U-ID | Unbekanntes Image, AccessDenied, PID-Reuse, Parent beendet, unvollständige Liste/volle Registratur | Rein Unit: kein Name genügt; Identitätsunsicherheit kontrolliert beenden, kein automatischer Neustart. |
| P0 U-STOP | Startfehler, Timeout, Shutdown während Start/Resume/Cleanup, Deadline pro Gesamtphase | Rein Unit mit Fake-Clock: keine neuen Vollfristen pro Ressource, late completions bleiben owned, Shutdown sperrt Admission zuerst. |
| P0 U-IPC | Falsche ID/Version/Typ, Trunkierung, Zusatzframe, zu große Länge/Tiefe/Map, stderr-Flut, Blockaden | Rein Unit/Fake-Pipes: Grenzen vor Allokation; kein Ergebnis/Slot vor Reader-/Gruppenende; Maximalmap-Vertrag erhalten. |
| P0 O-QUOTA | Gewöhnliche Prozesse an/über kleiner Quote; danach getrennte eigene Nested-Ebene | OS, synthetisch: konkrete Zurückweisung/Ende überzähliger Instanz und vollständiger Gruppencleanup; Hilfsprozesse separat identifiziert. Raw-Gesamtcap nur bei entsprechendem Beleg bestanden. |
| P0 O-START | Gültiger atomarer Start sowie fehlende EXE/Zuordnungs-/Resume-Fehler | OS, synthetisch: keine Marker vor Resume, kein Fallback, keine Restgruppe; inkompatible vorhandene Jobs sicher ablehnen. |
| P0 O-CLEAN | Variable identifizierte Nachfahren, Worker zuerst beendet, eigener Kontrollprozess außerhalb Zielgruppe | OS, synthetisch: gesamte eigene Gruppe leer/Instanzen beendet, Kontrollinstanz lebt; keine PID-/Namens-Kills. |
| P0 O-CLOSE | Letztes Jobhandle, bewusstes eigenes Duplikat, verweigerte Vererbung | OS, synthetisch: Gruppe bleibt bei letztem gültigem Duplikat korrekt kontrolliert; danach vollständiger unabhängiger Endbeleg, nicht nur Close-Erfolg. |
| P0 O-CRASH | Unerwartetes Ende ausschließlich synthetischen Supervisors | OS: unabhängiger eigener Beobachter hält Instanzhandles und nachweisbare äußere private Kontrollgruppe; keine Orphans trotz Supervisorverlust. Kein geschlossener innerer Handle als abfragbar angenommen. |
| P0 O-TIME | Blockierter synthetischer Worker oder Pipe bei bekannter Startzuordnung | OS: globale Lauf-/Cleanupfristen, bestätigte Gruppen-/I/O-Beendigung; sonst Quarantäne. Kein absichtliches Hängen fremder Kernel-/Dienstprozesse. |
| P0/P1 O-MEM | Kleine kontrollierte Commit-/Heapallokation, optional CPU-Rate im eigenen Job | Vor jeweiliger Ressourcen-Zusage zwingend: konfigurierte konkrete Limitwirkung/Fehler/Cleanup belegen; kein Rechner-OOM oder Lasttest. Ohne CPU-Zusage CPU-Stufe optional. |
| P0 A-MANUAL | Gebautes manuelles Archiv und Gateway-Seam | Artefakt-/Mockprüfung: kein Worker/nativer Launcher/Selenium, keine Live-Startreferenz/Autokonfiguration, Doubles allein. Bestehende Assertions erhalten. |
| P0 A-CONTRACT | Bestehende Maps, Band-204/Import-/Fehler-/Security-Verträge, M06/Legacy | Zunächst Standalone-/Mock-/DOM-Fälle ohne DB/Browser. Kein Referenzservice bei technischem Scraperfehler; Busy-/Quarantäneantwort gezielt abnehmen. |
| P0 F-OFFLINE | Erst danach echte Firefox-/Geckodriver-Kombination mit lokalen synthetischen Seiten/Profilen | Gesonderte Browserfreigabe: gesamte dynamische Gruppe, Sandboxjobs, End-/Deadline-/Ressourcenpfade, keine echten Nutzerdaten/externen Quellen. |

Vor Quoten-, Close-, Crash-, Timeout- und Speicherstufen muss jeweils eine feste
zulässige Start-/Speicher-/Zeit-/Outputobergrenze und sicherer Cleanup dokumentiert
sein. Die heutige unbewiesene raw-Gesamtprozessquote darf nicht als bereits
erfüllte Sicherheitsgrenze dieser künftigen Versuche vorgespiegelt werden.
Bleibt der erforderliche Schutz für einen Testaufbau unklar, diesen Test nicht
starten und den Versuchsaufbau separat entscheiden.

O-CLOSE/O-CRASH benötigen eine unabhängige synthetische Beobachtungsgrenze:
letzten inneren Jobhandle zu schließen entfernt die Möglichkeit, ihn anschließend
abzufragen. Ein vorheriger Snapshot allein beweist keine vollständige spätere
Population. Endnachweis z.B. durch klar eigene äußere Jobgruppe plus gehaltene
Instanzen abnehmen, ohne unbesessene Ancestorjobs zu verändern.

Reine Unit-Fakes beweisen Logik/Fehlerpolitik, nicht ABI, Kernelzuordnung,
Jobvererbung, tatsächliche Quoten, native Pipeauflösung oder Firefox-Sandbox.
Archivprüfung beweist Paketinhalt, nicht automatisch fehlerfreien Betrieb.
Bestehender manualSafetyTest enthält laut ManualArtifactTest auch
ValidateOnly-ProcessBuilder-Unterprozesse: **datenbankfrei ist nicht prozessfrei**.
Keinen solchen Gesamttask ungeprüft als reine Unit-Stufe ausführen.

### 8. Begrenzte Implementierungsaufträge nach Freigabe

1. **Vertrag/Budgets abnehmen, OS-Blocker planen:** B01–B12/C, dynamische
   Population, Quarantänerecovery und ehrliche Ressourcen-Zusagen entscheiden.
   Dann nur gesondert freigegebene synthetische Pflichtnachweise; Prototyp-Historie
   nicht überschreiben. Kein Scraperproduktcode vor dieser Entscheidung.
2. **Adapter + Zustandsautomat:** Explizit freigegebene direkte JNA-Version/
   Auflösung und Windows-only-Adapter; zuerst Unit-Fakes U-ADMIT/OWNER/END/STOP.
   Keine Controller-/Extraktionsänderung und kein Live-Start im Konstruktor.
3. **Private IPC + Worker-Artefakt:** Kleines eigenes SourceSet/Runtime-Allowlist,
   String-Map-Protokoll und Byte-/Parser-/I/O-Budgets mit Fake-Worker testen.
   Keine normale Boot-JAR als --worker-Profil, keine DB-/Spring-Abhängigkeiten.
4. **Extraktion übernehmen:** Manga-/Bandpfade mit Driver-Lifecycle-Seam im
   Worker verwenden, vorhandene Selektoren/Importfelder/Fallbackverträge sichern.
   Keine T03/T04/T06-Refactorings; nur Protokollstörer/technische Fehlerweitergabe
   unmittelbar passend zum freigegebenen Workerpfad anfassen.
5. **Gateway und Paketierung:** LiveGateway delegieren, bestehende POST/CSRF/
   Host-/Origin-/JSON-/Referenzverträge erhalten; manual-test strukturell ohne
   Live-Worker-/native Adapterklassen und unnötige Live-Abhängigkeiten verifizieren.
   Busy-/Quarantänefehler und UI-Werterhalt gezielt testen, keine Autofill-Wiederholung.
6. **Gestufte Endabnahme:** OS-Pflichtmatrix bestehen, anschließend gesondert
   Firefox/Gecko offline freigeben. DB-Regression nur mit neuer ausdrücklicher
   Freigabe, Taskplan/Guard zuerst, bestehende Fingerabdruck-/Rollbackregeln.
   Browserabnahme im geschützten manual-test bleibt synthetisch; sie ersetzt
   keinen Live-Worker-/Firefox-OS-Nachweis.

Keine dieser Stufen ist mit diesem Dokument automatisch beauftragt. Keine
Teilfreigabe mit fehlgeschlagener harter Zusage als vollständige T02-Sicherheit
oder produktive Bereitstellung deklarieren.

### 9. Entscheidungen vor Implementierungsfreigabe

| Entscheidung | Empfohlener Ausgangspunkt / offen |
|---|---|
| Dynamische Akzeptanz | B01–B12/C als Entwurf abnehmen; historische Drei-PID-Assertion unverändert, neue Produktregel separat. |
| Ressourcenversprechen | Harte Gesamtprozesszahl ist derzeit blockiert. Eigentümer muss Nachweis abwarten oder ausdrücklich eine anders begrenzte Zusage wählen; keine automatische Herabstufung. |
| Zahlenwerte | Fristen je Operation inkl. langer Reihen, gemeinsames Cleanupbudget, Commit/CPU, gesamte IPC-/Diagnose-/Registraturgrenzen festlegen und testen. |
| Identitätsfehler/Recovery | Konservative Quarantäne bestätigen; Umgang mit bereits beendeten beobachteten Instanzen und eventuelle Persistenz/Operatorfreigabe vor Appneustart entscheiden. |
| Technische Verträge | Busy z.B. gezielter 429-Kandidat, neutraler 500 für Technik/Quarantäne; keine Rohdaten/Retry. Wechsel verschluckter technischer Teilmaps zu Fehlerstatus gesondert abnehmen. |
| Native Dependency/Packaging | Direkte JNA-Version/-Deklaration und Worker-/manual-Artefaktgrenzen explizit freigeben, nicht zufällig transitive JARs als Vertrag nutzen. |
| Testfreigaben | Jede OS-Stufe und später echte Offline-Firefox-Abnahme separat; heutige verbrauchte Kennungen niemals reaktivieren. |

**Aktuelle Empfehlung:** Variante B weiterverfolgen, nächste Entscheidung über
diese Spezifikation und die begrenzte Quoten-/Lifecycle-Testreihenfolge einholen.
Kein neues native-execution Experiment, keine produktive Umsetzung und keine
SSRF-Arbeit aus diesem Plan eigenständig beginnen.

Jetzt ausschließlich TESTING.md/ROADMAP.md ergänzt. Alle bestehenden Prototyp-/
Anwendungs-/Testdateien, STOPs, Einmalfreigaben und Evidenz unverändert. Keine
Builds, Tests, Java-/Worker-/Browser-/HTTP-/Scraperstarts, DB-/SQL-Verbindungen,
Downloads/Installationen, neuen Dependencies oder Git-Operationen. Verifikation
nur durch Dateihashes, Dokument-Prefixvergleich und Whitespace-Prüfung.


### 10. Präzisierung der Rollenentscheidung für dynamische Mitglieder

Für die spätere Produktabnahme sind zwei Entscheidungen strikt zu trennen:
(1) gehört eine konkrete Instanz zur eigenen kontrollierten Cleanup-Gruppe,
(2) entspricht ihre beobachtete Rolle der freigegebenen Worker-/Browserkonfiguration?
Aus einer positiven Antwort auf (1) folgt keine Harmlosigkeitszusage für (2).

Vorgeschlagene konservative Produktregel: Instanz zunächst PENDING; eigener
Jobhandle + gehaltenes Prozesshandle/PID/Erzeugungszeit belegen Zugehörigkeit.
Image/Rollenmetadaten gegen eine vorab freigegebene **lokale** Startkonfiguration
bewerten (Worker-Java, Geckodriver, Firefox und ausdrücklich abgenommene
OS-Hilfsrollen). Ein bloßer Dateiname, gleicher Pfad oder scheinbar passender
Parent-PID allein reicht nicht. Noch nicht abgenommene Rolle oder fehlende
Instanzidentität => kontrollierter Gruppenabbruch/Quarantäne, kein Importerfolg.
Der bereits bekannte conhost-Nachweis ist ein Prüfbeispiel, keine automatisch
übernommene Namensausnahme. Kurzlebige Prozesse und wechselnde Firefox-Rollen
müssen in F-OFFLINE zeigen, ob diese konservative Regel praktisch tragfähig ist;
eine Lockerung benötigt eine neue begründete Entscheidung.

Vor Implementierung zusätzlich entscheiden: Welche konkreten lokalen Executables/
OS-Rollen sind zugelassen, welcher Integritäts-/Pfadschutz wird vorausgesetzt,
und welche Metadaten sind für ihre Abnahme nötig? Keine globale Windows-
Programmkontrolle oder neue Systemrichtlinie hier vorwegnehmen. Fehlende Parent-
Historie allein entkräftet eine anderweitig bestätigte native Gruppenzugehörigkeit
nicht; ungeklärte Rollenzulässigkeit bleibt trotzdem ein eigener Sperrgrund.
Diese Präzisierung ist Teil des Entwurfs, keine heute umgesetzte Allowlist.


## T02 – B-MODEL-1: freigegebenes reines Vertragsmodell (19.10.2026)

B-SPEC-1 ist grundsätzlich als Architektur-/Abnahmegrundlage angenommen. Dieser
Nachtrag dokumentiert ausschließlich den separat freigegebenen Zustands-/Unit-
Teilauftrag. Die historische Entwurfsaussage „keine Implementierungsfreigabe“
bleibt als damaliger Stand erhalten; neu freigegeben war nur das folgende
Testmodell. Vollständige Variante B, native Integration und Live-Scraper bleiben
gesperrt. Keine Neubewertung historischer STOPs oder Quotenassertions.

### Umfang, Dateien und Vertrauensgrenze

Vier neue Dateien ausschließlich unter src/test/java/de/mangalib/scrapermodel:

- SupervisorModel.java: synchronisierter, instanzgebundener Admission-/Owner-
  Automat; monotone synthetische Zeit; explizite Freigabebarriere.
- SupervisorModelTest.java: deterministische Callback-/Fehler-/Shutdownfolgen,
  Fake-Uhr und synthetische Adapter-Nachweissnapshots.
- IpcBudgetModel.java: reines Bytezähler-/Envelope-Vertragsmodell ohne Puffer,
  Streams, Parser, Pipes oder I/O.
- IpcBudgetModelTest.java: kleine synthetische Längen, Grenzüberschreitungen,
  Korrelation und vergifteter Kanal nach Fehlern.

Keine Bean, keine Referenz aus main/manualTest, kein Gateway und keine neuen
Dependencies/Buildkonfigurationen. Modellzähler START/STOP beschreiben nur
angeforderte Aktionen, keine wirklichen Starts oder bestätigten Beendigungen.
Kein Modell konstruiert Prozesse, öffnet Handles oder lädt Win32/JNA.

Nachweise sind bewusst **vertrauenswürdige Eingaben eines Fake-Adapters**. Das
Modell prüft ihre Vollständigkeit/Korrelation, nicht die Wahrheit eines OS-
Snapshots. Auch initialize(CONFIRMED) ist kein echtes Recoveryverfahren:
ein späterer Adapter muss Startup-/Altgruppen-/Plattformfreigabe nachweisen.
Ein bloßer Programmneustart darf diesen Wert nicht erzeugen. Rollen-/Image-
Authentizität, nativer Ressourcenbestand und tatsächliche Prozessidentitäten
werden in dieser Stufe nicht nachgebildet oder als geprüft ausgegeben.

### Zustände und Übergänge

Alle Zustandsänderungen sind auf derselben Modellinstanz synchronisiert.
Ticket-Objektidentität UND fortlaufende Generation gehören zum Owner; gleiche
Generationsnummer einer anderen Instanz verleiht keine Berechtigung. Es gibt
keine globale PID-/Handle-Tabelle, Warteschlange, Retry- oder Resetfunktion.

| Auslöser | Ausgang → Ziel | Ticket / Ressourcenverantwortung / Fehler und Nachweis |
|---|---|---|
| Konstruktion | → UNAVAILABLE | Noch kein Owner, keine Startanforderung; Konstruktion allein erlaubt keinen Import. |
| initialize(CONFIRMED) | UNAVAILABLE → IDLE | Vertrauenswürdiger Startup-/Recovery-Fake bestätigt Voraussetzung; noch kein Auftrag. UNKNOWN/FAILED führt zu QUARANTINED. Wiederholte Initialisierung und Reset aus Quarantäne werden abgewiesen. |
| admit(Reihe oder Band) | IDLE → STARTING | Genau ein neues instanzgebundenes Ticket; ab jetzt diesem Owner sämtliche späteren Start-/Cleanupressourcen zuordnen. Ein Start angefordert, Startabschluss offen. Jede andere Admission sofort leer/Busy, ohne Queue oder Aktion. |
| startReturned(successful=true) | STARTING → RUNNING | Nur aktueller Owner und genau eine ausstehende Start-/Resume-Rückkehr; innerhalb Startfrist und ohne Shutdown. Startabschluss bestätigt, nicht Prozessende. Doppelte Rückkehr wirkungslos. |
| startReturned(false) | STARTING → VERIFYING | Owner behält alle möglicherweise angelegten Ressourcen, auch bei Fehler vor Prozesserzeugung; eine Cleanupanforderung, globale Cleanupfrist. Keine automatische Wiederholung. |
| finished / Laufzeitfrist | RUNNING → STOPPING | Owner fordert eigenen Gruppenstopp an; Rootende oder technische Rückmeldung liefert keinen Endnachweis. |
| Startfrist / Shutdown | STARTING → STOPPING | Owner und ausstehender Start bleiben bestehen. Keine Freigabe, kein Resume-/RUNNING-Übergang nach Stoppentscheidung. |
| verificationStarted | STOPPING → VERIFYING | Derselbe Owner, keine Fristverlängerung und keine Bestätigung eines erfolgreichen Kill allein. |
| späte startReturned-Rückkehr | STOPPING/VERIFYING → VERIFYING | Abschluss des alten Start-/Resumevorgangs verbuchen und denselben Owner bereinigen; niemals erneut RUNNING. In QUARANTINED bleibt Zustand QUARANTINED. |
| unvollständiger Endnachweis | VERIFYING → VERIFYING | Slot bleibt belegt; UNKNOWN/FAILED/fehlender Beleg nie als true auslegen. Vorhandener Cleanupzeitraum bleibt unverändert. |
| Cleanupfrist erreicht | STOPPING/VERIFYING → QUARANTINED | Owner und ungeklärte Ressourcen bleiben zugeordnet; kein neuer Start. Auch vollständiger verspäteter Nachweis entsperrt nicht automatisch. |
| ungeklärte Identität/Mitgliedschaft | jeder aktive Zustand → QUARANTINED | Ownergebundene Stoppanforderung höchstens einmal; auch nach später bestätigtem Ressourcenende konservativ gesperrt. Keine Namens-/PID-Kills. |
| vollständige Barriere | VERIFYING → IDLE bzw. CLOSED | Nur aktueller Owner, alle Nachweise gemeinsam und rechtzeitig; genau eine Freigabe. CLOSED bei vorher monoton gesetztem Shutdown. |
| Shutdown ohne aktiven Owner | UNAVAILABLE/IDLE → CLOSED | Admission zuerst schließen, keine Start-/Stoppaktion; bereits bestehende QUARANTINED-Lage bleibt sichtbar. |
| Shutdown mit Owner | STARTING/RUNNING → STOPPING; VERIFYING bleibt; QUARANTINED bleibt | Erst monotone Admissionsperre; dann dieselbe idempotente Cleanupanforderung, niemals neue Vollfrist. CLOSED erst nach bestätigter Barriere, nicht bei Unsicherheit. |
| fremde/alte Tickets, unzulässige oder doppelte Callbacks | unverändert | Keine Ressourcenzuordnung, Zähleränderung oder Slotfreigabe des aktuellen Owners. Alte positive Nachweissnapshots werden über Revision abgewiesen. |

Die Modelluhr ist ein monotoner Fake; es gibt weder Timerthreads noch Sleep.
Start- und operationsspezifische Gesamtfrist beginnen bei Admission, nicht
erneut bei RUNNING oder einer Fortschrittsmeldung. Eine globale Cleanupfrist
beginnt bei der ersten Stopp-/Cleanupanforderung; weitere Ereignisse verlängern
sie nicht. tick() modelliert Überwachung; Start-/Endcallbacks prüfen die Frist
zusätzlich. Ein realer Adapter muss diese Beobachtung und alle blockierenden
Operationen gewährleisten. Keine harte Echtzeitgarantie durch diesen Test.

### Vollständige Freigabebarriere

Das Modell verlangt: intern kein ausstehender Start/Resumeabschluss **und**
einen einzigen aktuellen, unveränderlichen, ticketgebundenen Evidence-Snapshot
mit streng steigender Revision. Darin müssen alle elf Checks CONFIRMED sein:

1. Keine aktive eigene Population.
2. Vollständige leere Jobliste.
3. Konsistentes Accounting mit null aktiven Mitgliedern.
4. Alle gehaltenen eigenen Instanzen beendet.
5. Sicherheitsrelevante Identitäten geklärt.
6. IPC-Reader beendet.
7. Requestwriter beendet.
8. Handles geschlossen.
9. Native Ressourcen abgeschlossen.
10. Keine weiteren ressourcenerzeugenden Callbacks.
11. Eigene temporäre Ressourcen abgeschlossen.

Jeder Check einzeln UNKNOWN, FAILED oder fehlend verhindert Freigabe.
Teilergebnisse verschiedener Revisionen werden nicht zusammengefügt.
Die realen Endbelege müssen am noch offenen eigenen Job gewonnen und vor dem
letzten Close gesichert werden; anschließend muss Ressourcenerzeugung
ausgeschlossen sein. Eine gemeinsame Map ersetzt diesen späteren Sequenz-/
Containmentnachweis nicht. Vollständige Belege können nach Quarantäne zwar
Ressourcenabschluss dokumentieren, aber keine automatische Entsperrung auslösen.

Kein Ergebnis-/Payloaddispatcher implementiert. B09 (validiertes SUCCESS plus
Exitcode 0 plus Barriere vor Auslieferung) bleibt eine spätere explizite
Integrations-/Vertragsprüfung; dieses Modell gibt gar keine Importdaten aus.

### IPC-Vertrag im geprüften Modell

Genau ein Request und ein Resultat, passende Modell-Ticketidentität, Operation,
Version 1 und Status SUCCESS oder ERROR. Positive deklarierte Bytezahlen werden
vor jeglicher hypothetischer Allokation geprüft. Kumulative Chunks dürfen die
deklarierte Größe nicht übersteigen; Trunkierung/zweiter Frame/falsche
Korrelation/Version/Status/Entryzahl/Tiefe vergiften den Kanal. Stderr besitzt
ein separates Gesamtausgabebudget und kleineres Aufbewahrungsbudget; ein kleiner
Tail begrenzt ausdrücklich nicht die gesamten ausgegebenen Bytes. Überlauf wird
durch Subtraktion vor Addition vermieden. Ein korrekt abgeschlossenes ERROR-
Envelope ist kein Importerfolg und löst keinen Retry aus.

Grenzen: Ticketobjekte sind hier ein Korrelations-Fake, kein Wireformat. Entryzahl
und Tiefe sind übergebene Testmetadaten, keine aus Bytes ermittelten Parserwerte.
Ein echter Decoder muss selbst UTF-8, JSON-Typen, doppelte Schlüssel, Stringgrößen,
Tiefe/Entries, zusätzliche Bytes und tatsächliche Transport-EOFs prüfen. Es gibt
hier keinen JSON-Parser, keine Map-Erzeugung, keinen echten Drain, blockierten
Reader oder erfolgreichen Nachweis für eine maximale 10.000-Bände-Payload.

### Vorläufige Budgetvorschläge – noch nicht freigegebene Produktgrenzen

| Bereich | Diskussionswert | Begründung / ausstehender Nachweis |
|---|---|---|
| Admission | 1 Live-Auftrag pro Appinstanz für beide Operationen | Im Modell geprüft; produktives gemeinsames Gateway und mehrere Appinstanzen noch nicht abgenommen. Keine rechnerweite Garantie. |
| Start inklusive Handshake | 30 s | Kandidat für JVM-/Firefoxstart; OS-/Handshake-/Late-return-Test erforderlich. Innerhalb Gesamtfrist. |
| Band gesamt ab Admission | 120 s | Kandidat für einzelnen Abruf, kein Retry/Progressreset; Offline-/Netzwerkrealität später prüfen. |
| Reihe gesamt ab Admission | 60 min | Heutige 150 ms pro Band ergeben bei 10.000 Bänden bereits mindestens 25 min ohne Navigation; keinesfalls dieselbe kurze Bandfrist still übernehmen. Messung maximaler synthetischer Importfälle fehlt. |
| Cleanup gesamt | 15 s | Eine gemeinsame Restfrist für Startabschluss, Gruppenende, I/O und Ressourcen; kein Vollbudget pro Handle. Überschreitung sperrt, garantiert kein OS-Ende. |
| Job-Commit / Workerheap | 2 GiB Commit für Job als Kandidat, Worker -Xmx256m | Native JVM-/Firefox-/Sandboxverteilung messen; Commit nicht RAM/RSS/GPU/Cache, Xmx nicht Jobgesamtbudget. Keine OOM-/Limitwirkungszusage. |
| CPU | Kandidat 50 % Job-CPU-Rate; zunächst keine separate CPU-Zeitquote | Ein UI-freundlicher Ausgangspunkt, Win32-/Nested-Unterstützung und Auswirkung auf Walltime offen. Prozentwert nicht einfach als CPU-Zeit oder Jobgesamtprozessgrenze ausgeben. |
| OS-Gesamtprozessquote | Kein produktiver N-Wert abgenommen | Frühere cap=3-Evidenz mit sechs Mitgliedern bleibt Blocker einer harten N-Zusage. Keine Namensausnahme, kein Ersatz durch höhere Zahl. |
| Beobachtungsregister | Kandidat 256 gehaltene Instanzen bzw. 256 Snapshot-Einträge | Nur begrenzter Diagnose-/Speicheraufwand; bei Erschöpfung Abbruch/Quarantäne. Keine präventive OS-Prozessanzahlgarantie. Kurzlebige historische Population beachten. |
| IPC-Request | 4 KiB gesamt, genau ein Auftrag | Operation/Index/Version/ID benötigen keinen Importbody; Transport-/Encodingbeleg fehlt. |
| IPC-Ergebnis | 32 MiB gesamt, genau ein Frame | Unicode-/Escapes-/10.000-Bände-Maximalmap und gleichzeitiger Parentheapbedarf noch nachzuweisen. |
| IPC-Struktur | Tiefe 4, höchstens 200.100 Map-Einträge, höchstens 64 KiB pro String | Kandidaten für flache Stringmaps mit ca. 20 Feldern je 10.000 Bände; tatsächliche Feldzahl/Stringverträge/Maximaldaten vor Freigabe ermitteln, keine heutigen Payloads unbesehen abschneiden. |
| Diagnoseausgabe | 1 MiB stderr gesamt, nur 64 KiB Tail behalten | Flut führt zu kontrolliertem Abbruch; keine Rohtexte im HTTP-Fehler. Echte Pipes/Drain noch offen. |

Zahlen nur Spezifikationskandidaten, nicht in die Anwendung eingebaut. Die
Unit-Tests nutzen absichtlich viel kleinere eigene Budgets (z.B. 8/32 Byte), um
Grenzen ohne große Allokation zu prüfen. Keine neue native Quotenprüfung
beauftragt. Datei-/Download-/Profil-/Netzwerkgrenzen bleiben spätere Themen.

### Neutrale HTTP-Fehlersemantik – Vorschlag, nicht implementiert

Erfolgsstatus, Maps/JSON-Felder und fachliche Importverträge bleiben erhalten.

| Lage | Vorgeschlagener Status / neutraler Text | Verhalten |
|---|---|---|
| Aktuell belegt | 429 – „Ein Import läuft bereits. Bitte später erneut versuchen.“ | Keine Queue, kein Retry-After-Versprechen, keine automatische Wiederholung. |
| Kontrollierter Workerfehler | 500 – „Import konnte nicht abgeschlossen werden.“ | Keine technischen Details/Teilmap als Erfolg. |
| Zeitüberschreitung | 500 – „Zeitlimit des Imports überschritten.“ | Gleicher technischer Statusbereich wie heute; keine Behauptung bereits beendeter Prozesse. |
| Cleanup unbestätigt | 500 – „Import wurde abgebrochen. Weitere Importe sind gesperrt.“ | Fehlgeschlagener Auftrag, Quarantäne bis gesonderte Recovery. |
| Bereits in Quarantäne | 503 – „Import ist derzeit nicht verfügbar.“ | Keine neue Startaktion; normale Bibliotheksfunktionen sollen weiterlaufen. |

429/503 und die Texte benötigen vor Controllerintegration eigene Freigabe und
MockMvc-/Frontend-Vertragstests. 500 für technische Fehler minimiert Änderungen
an den heutigen Fehlerverträgen; 504 wäre eine mögliche, aber zusätzliche
HTTP-Änderung und ist hier nicht gewählt. Kein Rohstderr, Exceptiontext, Pfad,
Handle oder URL in Antworten. Host-/Origin-/CSRF-Filter bleiben unverändert.

### Tatsächlich ausgeführte Prüfungen

Vor jedem echten Testlauf identischer gefilterter Dry-Run; Java 21, Gradle 8.14.3,
ausschließlich --offline --no-daemon:

~~~powershell
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

Geprüfter Graph: compileJava, processResources, classes, compileTestJava,
processTestResources, testClasses, test. Kein manualSafetyTest/manualBootJar,
kein Boot-/JPA-Kontext, kein JDBC und kein Prozessstarter in den ausgewählten
JUnit-Klassen; Imports ausschließlich JDK/JUnit. Der vorhandene globale
external-network-Ausschluss und alle Testguards bleiben unangetastet.

Erster Sandbox-Dry-Run am Cache-Lockzugriff verweigert, noch ohne Gradleaufgaben.
Danach derselbe Offline-Dry-Run und Testlauf mit Zugriff auf vorhandenen Cache
erfolgreich: zunächst 68 Tests. Anschließend Startupbarriere und gezielte
Startfehlerfälle ergänzt; erneuter identischer Dry-Run erfolgreich. Abschließender
Lauf laut XML-Berichten ausschließlich der zwei Modellklassen:

| Klasse | Bestanden | Fehler | Übersprungen |
|---|---:|---:|---:|
| SupervisorModelTest | 35 | 0 | 0 |
| IpcBudgetModelTest | 39 | 0 | 0 |
| Gesamt des finalen Laufs | **74** | **0** | **0** |

68 und 74 sind zwei unterschiedliche Entwicklungsstände, keine 142 verschiedenen
Tests. Gradle meldete BUILD SUCCESSFUL; nur compileTestJava/test waren neu
auszuführen, main war unverändert/up-to-date. Keine Vollsuite und keine alten
Integrationsergebnisse neu behauptet. Gradle verwendet dabei reguläre eigene
JVM-/JUnit-Prozesse; es wurden keine experimentellen Java-Unterprozesse,
Worker-, Win32-, Browser-, Gecko-, Scraper-, Anwendungs- oder HTTP-Starts ausgelöst.
Keine DB/SQL-/Netzwerkverbindung, Installation oder Dependencyänderung.

### Grenzen und nächste getrennte Freigabestufen

1. Zahlenbudgets, zulässige lokale Rollen, ehrliche Ressourcenversprechen und
   Recovery-/Altgruppenprüfung entscheiden. Harte OS-Gesamtprozessanzahl bleibt
   unbewiesen; neues Modell hebt historische STOPs nicht auf.
2. Separat Windows-/JNA-Adapter beauftragen, konkrete Dependency ausdrücklich
   freigeben. Kein produktiver Start vor Quoten-/Zuordnungs-/Cleanupkonzept.
3. Einzeln genehmigte synthetische OS-Fälle: atomare Zuordnung vor Ausführung,
   verschachtelte Jobs/Breakaway, variable Population, Fremdschutz, echte Quoten,
   gemeinsame Cleanupfrist, Handlevererbung, Kill-on-close und Supervisor-Crash.
   Keine dieser Stufen wurde jetzt gestartet.
4. Separat echtes IPC/Wireformat und Workerartefakt entwickeln; Parser-/Byte-/
   Reader-/Ressourcenregister mit Fake-I/O erweitern. Danach Extraktion unter
   Erhalt der Maps/Selektoren übernehmen, keine DB-/Springabhängigkeit im Worker.
5. Erst gesondert Gateway-/HTTP-/Paketintegration: SUCCESS+Exit0+Barriere,
   neutrale Fehler, manuelles gebautes Archiv inklusive nested JARs ohne
   Worker/Native-Livepfad prüfen. Heutiger Testcode wird nicht paketiert.
6. Echte Offline-Firefox-/Gecko-Abnahme und später gegebenenfalls geschützte
   DB-/Browserregression jeweils neu freigeben. SSRF/Netzwerkisolation sowie
   T03/T04/T06 bleiben eigenständig; ein Job Object ist keine Netzwerk-Sandbox.

Keine Produktionsimplementierung aus diesem Modell eigenständig fortsetzen.

Abschließende Modellprüfung: Ein Protokollfehler nach zuvor vollständigem Rahmen
entwertet auch finished(); zusätzliche Assertion ergänzt. Nach erneutem
identischem Dry-Run derselbe gefilterte Offline-Testlauf nochmals erfolgreich:
74 bestanden, 0 Fehler, 0 übersprungen. Insgesamt drei erfolgreiche Modellläufe
(68, 74, 74), keine zusätzlichen unterschiedlichen Testfälle dadurch.

## T02 – B-MODEL-2: Budget-, Recovery- und Fehlerverträge (23.10.2026)

**Statische Spezifikation, keine Produktions- oder OS-Ausführungsfreigabe.**
Grundlage sind B-SPEC-1 und die 74 historisch erfolgreichen B-MODEL-1-Tests.
B-MODEL-1 bleibt ein unabhängiges Testmodell; weder sein SourceSet noch sein
initialize(CONFIRMED), Fake-Ticket oder Evidence-Objekt ist eine produktive
Recovery-/Supervisorarchitektur. Dieser Nachtrag präzisiert die spätere Abnahme,
implementiert sie aber nicht. Historische Aussagen behalten ihren Zeitbezug.

### 1. Statisch bestätigte Ausgangspunkte

- SupervisorModel startet UNAVAILABLE, bindet Owner an Objektidentität und
  Generation, prüft komplette Snapshots und hat keinen Quarantänereset. Es
  berechnet Fristen mit synthetischer Uhr; ein persistentes Journal, Bootidentität,
  echte Handles, Handshake oder Transportdecoder existieren dort nicht.
- IpcBudgetModel zählt deklarierte Bytes/Metadaten ohne echte I/O oder Parser.
  Seine positiven Belege sagen nichts über native Wirkung oder maximalen Heap.
- MangaScraper.java:141 wartet 150 ms je Band; bei 10.000 Bänden entstehen allein
  25 Minuten Pausen. Zeilen 183–185 erzeugen drei Felder je Band.
- WriteValidation.java:127–157 erlaubt höchstens 30.032 Importeinträge, Schlüssel
  mit höchstens 255 Java-Stringeinheiten und Werte mit höchstens 255 Codepoints.
  Die bisher vorgeschlagenen 200.100 IPC-Einträge sind daher unnötig weit.
- MainController.java:259–303 und :444–458 liefern technische Fehler derzeit
  als leere 500-Antworten; ungültige Eingaben als 400 mit bestehender error-Map.
  Erfolg und Referenzauflösung bleiben nach erfolgreichem Scrape unverändert.
- security.js:31–107 liest Fehlerbodys absichtlich nicht. Es kennt bisher keine
  spezifischen Texte für 429/503; 503 fällt auf den allgemeinen 5xx-Text zurück,
  429 auf die unbekannte Antwort. Scrape/Autofill verwenden persist:false.
  Servertexte allein verbessern deshalb noch keine sichtbare Fehlermeldung.
- build.gradle.kts:49–80 übernimmt für manual-test zunächst main/output und
  gefilterten allgemeinen runtimeClasspath. Die heutigen Excludes enthalten
  noch keine künftigen Worker-/Adapter-/JNA-Klassen. ManualArtifactTest prüft
  bisher bekannte Scraper/Selenium, nicht die erst künftig hinzukommenden Typen.
- Lokal liegen JNA-Core 5.13.0/5.17.0 und jna-platform 5.17.0 im Gradle-Cache.
  Der historische Prototyp nutzt eigene Kernel32-Deklarationen über JNA-Core;
  JobIdentityDiagnosis.java:140 verlangt 5.17.0. build.gradle.kts deklariert
  JNA nicht direkt. Cachebestand ist weder deklarierte Projektabhängigkeit noch
  Nachweis einer aktuellen, konsistenten oder sicherheitsgeprüften Auflösung.

Keine aktuelle Dependencyauflösung, DLL-Ladung oder Betriebssystemmessung aus
diesen statischen Befunden ableiten. Offizielle Versions-/Security-/ABIprüfung
bleibt vor einer späteren Dependency-/OS-Freigabe erforderlich; jetzt keine
externen Abrufe.

### 2. Zeitvertrag: gemeinsame monotone Gesamtfristen

Empfehlung als nächster abzuzeichnender Konfigurationsvertrag:

- t0 = atomare Slotvergabe, VOR Journal-/Pfad-/Job-/Startvorbereitung.
- Dstart = t0 + 30 s. Startup umfasst Vorbereitung, native Rückkehr,
  suspendierte Zuordnung, Resume und validierten korrelierten Worker-Handshake.
  RUNNING erst nach diesem Handshake. B-MODEL-1s startReturned ist dafür nur
  eine abstrakte Rückmeldung, kein implementierter Handshakebeleg.
- Dop = t0 + 120 s (Band) bzw. t0 + 3.600 s (Reihe). Startzeit ist enthalten.
  Seiten-, Selektor-, Scroll-, Ergebnis- und Protokolloperationen verlängern
  Dop nicht. Fortschritt, neue Frames und HTTP-Neuversuche verlängern nichts.
- tc = erste endgültige Abschluss-/Abbruchentscheidung: z.B. Rootende,
  terminales Ergebnis/EOF, Start-/Protokollfehler, Budgetüberschreitung,
  Abbruch/Shutdown. Dcleanup = tc + 15 s, genau einmal festgelegt.
- Jede Teiloperation bekommt höchstens die verbleibende Zeit ihrer Phase;
  lokale kleinere Wartefristen ersetzen oder erneuern die Gesamtfrist nicht.
  Ein optionales normales Exit-Gracefenster muss innerhalb dieser 15 s liegen.
- Eine rechtzeitige erfolgreiche Extraktion bleibt zunächst intern gepuffert.
  HTTP-Erfolg erst nach gültigem SUCCESS, Rootexit 0, kompletter Barriere C und
  bestätigtem Abschluss der Recoverybuchführung. Ergebnis nach Dop oder Ende
  nach Dcleanup nicht nachträglich als rechtzeitigen Erfolg ausliefern.
- Zum Grenzzeitpunkt now >= deadline gilt die Frist als abgelaufen. Ein später
  eintreffender Start-/I/O-Callback bleibt owned, darf nicht Resume/neuen Start
  oder Freigabe bewirken. Native Daten erst nach tatsächlicher Rückkehr freigeben.
- Später monotone Differenzmessung (z.B. System.nanoTime), validierte kleine
  Dauern und überlaufbewusste Vergleiche; keine persistierten nanoTime-Werte,
  keine Instant-/Wanduhrvergleiche und keine unübernommene Math.addExact-
  Fake-Uhrlogik als Beweis eines produktiven Langzeit-Timers verwenden.

Bei responsivem Supervisor ergibt sich eine vorgesehene Antwort-/Abbruchphase
bis etwa Operationsbudget plus 15 s (Band 135 s, Reihe 3.615 s), keine harte
Wallclockgarantie. Kernelhänger, Prozesspause, Scheduler, Suspend/Resume und
blockiertes natives I/O sind separat zu prüfen. Ein ausstehender nativer Aufruf
kann über die Frist hinaus Ressourcen halten: dann Quarantäne, kein Ersatzworker.
Nicht unbounded auf Cleanup warten, bevor ein bereits entscheidbarer HTTP-Fehler
zurückgegeben wird; die owned Cleanupverantwortung bleibt davon unabhängig.
HTTP-Container-/Clienttimeout und Verbindungsabbruch müssen später passend
abgenommen werden; dieser Nachtrag führt keine Async-Webarchitektur ein.

### 3. Konkrete vorläufige Budgets und Überschreitungsverhalten

Alle Werte sind Vorschläge für spätere validierte, lokal administrierte
Konfiguration, NICHT bereits wirksame Grenzen. Keine beliebigen Werte aus
HTTP-Payloads oder Workerframes übernehmen; fehlende/ungültige Pflichtbudgets
sperren den Live-Start. Reihen-/Bandfachgrenzen werden nicht verändert.

| Budget / Ausgangswert | Start → Ende / vorgesehene Durchsetzung | Noch nötiger Nachweis / Überschreitung |
|---|---|---|
| 1 Auftrag, gemeinsam Reihe/Band | Admission bis C plus bestätigtem Recoveryabschluss | Modell belegt Logik, spätere produktive gemeinsame Admission prüfen. Zweiter Request sofort 429, kein zusätzlicher Worker, Reader oder Warteschlangeneintrag. Keine rechnerweite Garantie. |
| Start 30 s | t0 bis validiertem Handshake, innerhalb Dop | Sinnvoller konfigurierbarer Kandidat. OS-/JVM-/Handshake-Latenz, Late-return und Resume-Rennen messen. Überschreitung → Stop/Cleanup; ungeklärtes Ende → Quarantäne. |
| Band 120 s | t0 bis rechtzeitiger Extraktion/Protokollabschluss; C danach im Cleanupbudget | Kandidat, Navigation/Firefox unter repräsentativen Offlinebedingungen messen; reale Abrufe später nur separat. Überschreitung → auslösender Request 500, keine Teilmap, Cleanup. |
| Reihe 60 min | t0 bis rechtzeitigem Resultat, Start/alle Bände enthalten | Vorläufig wegen mindestens 25 min Pausen bei 10.000 Bänden; Worst-Case-Struktur/Selektorzeiten noch offen. Kein Nachweis, dass alle zulässigen Reihen passen. Abbruch wie Band, kein stilles Teilergebnis. |
| Cleanup 15 s gesamt | tc bis C UND dauerhaftem Recoveryabschluss | Vernünftige Beobachtungs-/Eskalationsfrist, kein garantiertes OS-Ende. Eigene native Restzeitwaits/Pipeauflösung testen. Bei Fristende Quarantäne; Verantwortung/Handles nicht verwerfen. |
| Job-Commit 2 GiB | Vor Workerstart konfigurieren, bis Gruppenende/Close | Vorläufige Testhypothese, kein ohne Messung abgenommener Default. Win32-/Nested-Limitwirkung und Java/Gecko/Firefox-Maximalfall messen. Commit ist nicht RSS/RAM/GPU/Cache. Setz-/Rücklesefehler → kein Resume; Zuteilungs-/Workerfehler → Cleanup, unklarer Zustand → Quarantäne. |
| Workerheap 256 MiB | Festes Xmx beim Workerstart, gesamte JVM-Laufzeit | Als Messkandidat sinnvoll, noch kein Produktionswert: Maximalmap, JSON-Encoding und GC zusammen messen. Begrenzt nicht Metaspace/native/Browser. OOM/abgebrochener Frame → technischer Fehler, niemals SUCCESS aus Teilmap. |
| Job-CPU-Rate 50 % | Falls verpflichtend abgenommen: vor Resume bis Gruppenende | Optionaler Versuchswert, nicht automatisch aktivieren. Win32-Unterstützung, Ancestor-/Nestedlimits, Kernanzahl und Walltimeauswirkung prüfen. Kein CPU-Zeitbudget und kein Abbruch nach „50 % Verbrauch“. Wird eine verpflichtende Rate nicht gesetzt, Live-Start sperren statt still ungedrosselt starten. |
| Gesamte OS-Prozesszahl | Kein N-Wert verantwortbar freigegeben | ActiveProcessLimit=3 bei sechs Mitgliedern bleibt ungeklärt. Kein Name ausnehmen, keine höhere Zahl als scheinbare Reparatur. Eigene Quotenabnahme nötig; keine harte Gesamtprozesszusage. |
| Register/Snapshot je 256 Instanzen | Ab erstem eigenen Start bis Ende; begrenzter Puffer/Verlauf | Nur Diagnosekapazität, keine präventive Prozessgrenze. Reale kurzfristige/dynamische Population messen. Erschöpft/trunkiert/unidentifizierbar → Stop/Quarantäne, niemals Einträge still verwerfen. |
| Request 4 KiB insgesamt, ein Frame | Vor Längenallokation, über alle Chunks bis EOF | Plausibler Ausgangswert für Version/Ticket/Operation/positiven Index. UTF-8-Envelope und Blockaden mit späterem Codec prüfen. Zu groß/falsch → Protokollfehler/Cleanup; keine freie URL oder Timeoutvorgabe. |
| Resultat 32 MiB insgesamt, ein Frame | Deklaration vor Allokation, tatsächliche kumulative Bytes bis EOF | Nur Messkandidat, siehe Kompatibilitätsblocker unten. Integerüberlauf/zweiter Frame/Trunkierung → technischer Fehler und Cleanup, keine teilweise Feldübernahme. |
| IPC-Map 30.032 Einträge, Tiefe höchstens 4 | Streamingdecoder und nachfolgende bestehende Validierung | Präzisiert B-MODEL-1s pauschale 200.100 anhand des heutigen Validators. Flache String-/null-Map, begrenztes Envelope; keine allgemeine Objekt-/Typdeserialisierung. Nicht neue fachliche Pflichtfelder einführen. |
| IPC-Strings | Semantisch bisherige 255 Schlüssel-Stringeinheiten / 255 Werte-Codepoints; Roh-Tokenkandidat 4 KiB | Ersetzt den unnötig weiten 64-KiB-Stringvorschlag für heutige Importwerte. Unescaping/Unicode-/Schlüssel-/Nullverträge separat testen; nicht UTF-8-Bytes mit Codepoints gleichsetzen. 4 KiB ist Codecgrenze, keine neue Titellänge. |
| Stderr 1 MiB gesamt, 64 KiB Tail | Erstes bis letztes Byte des Auftrags, immer getrennt von stdout | Vorläufige Zahlen; echter begrenzter Drain/OS-Pipes fehlt. Gesamtbudget überschritten → Stop/Cleanup, keine stumme unendliche Ausgabe trotz kleinem Tail. Tail niemals als HTTP-Fehler ausgeben. |

**Neuer statischer Kompatibilitätshinweis:** 32 MiB ist nicht bereits als
ausreichend für jede vom heutigen Validator grundsätzlich akzeptierbare Map
bewiesen. Bei 30.032 Einträgen und einem Encoder mit Unicode-Escapes ergibt eine
konservative obere Größenabschätzung
30.032 × (255×6 Schlüsselbytes + 255×12 Wertebytes + 8 Strukturbytes) + 4.096
= **138.091.232 Byte, etwa 131,7 MiB**. Das ist eine Abschätzung für allgemein
zulässige Maximalstrings, kein gemessener Scraperoutput und kein Beleg, dass
jede Kombination fachlich von den heutigen Extraktoren erzeugt wird.
MangaScraper liefert aktuell drei kurze Schlüssel je Band und zusätzliche
Metadaten; seine tatsächliche Maximalpayload separat synthetisch konstruieren.

Vor Resultatlimit-Freigabe entscheiden: (a) 32 MiB anhand ALLER unterstützten
Produzentpayloads/Encoder belegen, ohne reguläre Importfelder zu beschneiden,
oder (b) falls auch sämtliche theoretischen Validator-Maximalmaps übertragen
werden müssen, größeren Ausgangswert z.B. 160 MiB samt Parent-/Worker-
Speicherbudget ausdrücklich freigeben und messen. Keine automatische Erhöhung
oder heimliche Reduktion akzeptierter Daten. 256 MiB Workerheap/2 GiB Jobbudget
sind mit Variante (b) besonders neu zu bewerten. Streaming, keine unbeschränkte
Zwischenkopie und gleichzeitig belegter Parentheap müssen separat nachgewiesen
werden. Der alte 32-MiB-Vorschlag bleibt historisch, keine Kompatibilitätszusage.

Datei-/Download-/Profilgrößen und SSRF/Netzwerk bleiben offen; Job Objects sind
keine Netzwerk-/Dateisystem-Sandbox. Diese Budgets ersetzen solche Grenzen nicht.

### 4. Recoveryvertrag R1: Beweis vor Wiederfreigabe

**Empfohlener Minimalweg ohne Dienst/Broker:** private unbenannte Jobs wie
B-SPEC-1; lokales dauerhaftes, begrenztes Recoveryjournal als Sperr- und
Zuordnungsinformation; kein nachträgliches Kill anhand gespeicherter PIDs.
Nach Verlust der kontrollierenden Handles im selben Kernelboot ist bei
unvollständiger Evidenz keine allgemeine automatische Recovery möglich.
Ein gesondert gehaltener unabhängiger eigener Beobachter wäre eine alternative
Architekturerweiterung, hier ausdrücklich NICHT eingeführt.

Konservative Zustände außerhalb des unveränderten Testmodells:

| Situation | Sicherer Folgezustand / Freigabebedingung |
|---|---|
| Appstart | UNAVAILABLE, Live-Admission geschlossen; Bibliotheksbetrieb soll unabhängig bleiben. Plattform, Budgets, Artefakte und Recoveryzustand prüfen, keine DLL im Shared-Gateway initialisieren. |
| Normal belegtes sauberes Ende | IDLE nur mit C und bestätigtem dauerhaftem Abschluss des betreffenden Journal-Intents. Native C allein ist noch kein nach Neustart gültiger Recoverybeleg. |
| Startfehler ohne je eingetretenen nativen Start | Nur dann normales Cleanup/IDLE, wenn Owner-/Ressourcenledger die Nichterzeugung und vollständige Bereinigung wirklich belegt; Fehlermeldung allein genügt nicht. |
| Start fehlgeschlagen nach möglicher Erzeugung / native Rückkehr offen | Owner behält Verantwortung; solange unklar STOPPING/VERIFYING, spätestens nach Cleanupfrist QUARANTINED. Kein Future.cancel als Beendigungsbeleg. |
| Gruppenende unbekannt / Identitätsfehler / Cleanupfrist abgelaufen | QUARANTINED, kein neuer Live-Auftrag, auch bei späterem Rootexit. Weiter nur eigene bereits kontrollierte Ressourcen bereinigen; keine fremde Ersatz-Killstrategie. |
| Supervisor abgestürzt, möglicherweise letztes Jobhandle geschlossen | Beim Neustart QUARANTINED, sofern kein belastbarer vollständiger End-/Recoverybeleg vorliegt. Kill-on-close ist Schutzmaßnahme, kein selbst beweiskräftiger Auditabschluss. |
| Appneustart mit schmutzigem Intent | QUARANTINED unabhängig von neuer Ticketnummer und unabhängig davon, ob bekannte PIDs fehlen. Keine unbenannte alte Gruppe durch Namen/PIDs rekonstruieren. |
| Journal fehlt, korrupt, Versions-/Scope-/Boot-/Generationbezug widersprüchlich oder alter CLEAN-Stand nicht nachweislich aktuell | UNKNOWN → QUARANTINED. Keine automatische Anlage eines „sauberen“ Ersatzjournals. Auch Erstinstallation braucht explizite kontrollierte Initialisierung. |
| Später Callback alter Instanz / alter Boot / alter Owner | Kein aktuelles Ticket freigeben, kein Ressourcenhandle aus Zahlenwerten übernehmen. Allenfalls nachweisbar noch gehaltene alte Ressourcen beim alten Owner verbuchen. |
| C erst nach Quarantäne erreicht | Ressourcenende dokumentieren; Quarantäne bleibt bis kontrollierter separater Recoveryprüfung bestehen. Kein automatischer Reset im bestehenden Modell. |

Ein Dateieintrag CLEAN allein, fehlende PIDs, leerer Namensscan oder vergangene
Zeit erfüllt R1 nie. Insbesondere können vor dem Rootexit unbekannte Nachfahren
entstanden sein. Gespeicherte PID+Erzeugungszeit identifiziert höchstens eine
bekannte Instanz, nicht die vollständige Gruppe. Zahlenwerte alter Handles
besitzen nach Prozessende keinen übertragbaren Besitznachweis.

#### R1-Daten und geordnete Persistenz – noch zu implementierender Vertrag

Journal außerhalb temporärer Build-/Projektartefakte, in fester lokal
kontrollierter Benutzer-/Installationsablage; kein DB-Schema, keine Zugangsdaten
oder Scrapeinhalte. Empfehlung für bounded Records (z.B. höchstens 1 MiB aktueller
Recoverydatensatz, keine unbegrenzte Historie als Voraussetzung); vollständiger
Diagnosetext nicht nötig. Konkreter Pfad/ACL und Crashfestigkeit später abnehmen.

Erforderlich: Schema-/Protokollversion, stabile Installations-/RecoveryScope-ID,
vertrauenswürdiger Kernelbootbezug, zufällige Supervisor-Inkarnation, eindeutige
Auftrags-ID plus Generation, Phase/Revision, Konfigurations-/Artefaktbezug,
INTENT/UNCERTAIN/COMPLETED-Bezug und Endbeleg-Zusammenfassung. Für bekannte
Instanzen PID, Erzeugungszeit, geprüfte private Mitgliedschaft und Rolle als
Diagnosezuordnung, niemals persistierter Handlewert als neue Berechtigung.
Wanduhrzeiten sind Diagnose, keine monotone Deadline oder sichere Bootidentität.
Monotone Zeitwerte nicht zwischen JVMs/Boots vergleichen.

Vor jeder möglichen nativen Erzeugung: exklusiver Journalowner, eindeutiges
Intent dauerhaft bestätigt. Scheitert oder hängt dieser Schritt, kein Start.
Während des Auftrags bleibt Intent offen; jede Ressource gehört diesem Owner.
Nach C: Abschlussrecord dauerhaft bestätigen, erst dann Slotfreigabe. Fehler beim
Abschlussrecord → Quarantäne trotz möglicherweise bereits leerer Gruppe.
Ein Prozessabsturz zwischen diesen Schritten muss eine Sperre, keinen falschen
CLEAN-Stand hinterlassen. Durability, Flush/Replace-Reihenfolge, kurze Writes,
Stromausfall und beschädigte/alte Datensätze benötigen eigene Abnahme;
„atomarer Rename“ oder FileChannel.force allein ist noch kein hier bewiesener
Windows-Crash-/Rollbackschutz. Die Recoveryabschlussoperation gehört zeitlich
zum gemeinsamen Cleanupbudget.

Nur ein Writer je RecoveryScope; keine vererbte Schreib-/Sperrberechtigung zum
Worker, feste geprüfte Pfade, keine Reparse-/Junctionumleitung oder alternative
Ablage zum Umgehen eines schmutzigen Journals. Dies ist Journalkonsistenz, keine
vorgezogene globale Eininstanzfunktion aus T07. Fehlende exklusive Ownership →
Live-Funktion nicht freigeben, keine konkurrierende Recovery.

Prüfsumme/Hash entdeckt Korruption, beweist keine Authentizität/Aktualität.
HMAC/Signatur helfen nur bei tatsächlich getrennt geschütztem Schlüssel und
geeigneter Wiederholungs-/Rollbackabwehr. Eine PID, ein Imagepfad, eine UUID oder
ein neu signierter alter CLEAN-Stand beweist kein Gruppenende. Benutzer-ACLs und
benutzerbezogener Schlüsselschutz isolieren einen kompromittierten Worker unter
derselben Windows-Identität nicht automatisch. Journal darf daher nicht ohne
weiteren Nachweis als manipulationssichere alleinige Freigabequelle gelten.
Schreibschutz gegenüber Worker, eindeutige Instanz-/Bootbindung und belastbare
Aktualitätsverankerung sind offene Live-Freigabebedingungen; kein neues
Token-/Benutzermodell in diesem Auftrag eingeführt.

#### Kontrollierte Wiederfreigabe – kein Resetknopf

1. Live-Admission geschlossen halten; incident und RecoveryScope eindeutig
   zuordnen. Keine automatische Bereinigung, kein Löschen/Umbenennen von Markern.
2. Ist derselbe kontrollierende Owner mit gültigen eigenen Handles noch
   vorhanden, C vollständig und erneut belegen, ausstehende Callbacks ausschließen
   und Recoveryabschluss dauerhaft bestätigen. Eigentümer muss nach
   Sicherheitsincident die Ursache beseitigen und die Freigabe ausdrücklich
   bestätigen; das aktuelle B-MODEL-1 bietet dafür absichtlich keine Reset-API.
3. Ist der Owner/Jobhandle verloren, genügt im selben Boot kein Scan bekannter
   PIDs. Ohne separat vorab abgenommenen unabhängigen Gruppen-Endbeobachter
   gesperrt bleiben. Keine nachträglich erdachte fremde Kontrollgruppe verwenden.
4. Minimaler vorgeschlagener Notweg: ausdrücklich vom Eigentümer durchgeführter
   vollständiger Windows-Kernelneustart mit vertrauenswürdig bestätigt geändertem
   Bootbezug, alle betroffenen eigenen Hosts einbezogen, anschließend kontrollierte
   Recovery-Neubaseline. Nur Appneustart, Abmelden, Ruhezustand, Fast-Startup-
   Wiederaufnahme oder VM-Snapshot-Rollback reichen als pauschaler Beleg nicht.
   Kernelneustart beendet alte lokale Userprozesse, ersetzt aber nicht die
   Reparatur eines Sicherheitsincidents oder den Schutz vor erneutem Autostart.
5. Bootbeleg, Scope, exklusiver Owner, Konfiguration/Artefakte und Ursache
   prüfen; neue Baseline erst nach dokumentiertem Eigentümerentscheid. Fehlende/
   verlorene Journalinformationen nicht als „es gab nie einen Worker“ auslegen.
   Wenn kein verlässlicher Bootwechsel/Endbeleg verfügbar ist: weiterhin gesperrt.

**Noch freizugeben:** Akzeptanz dieses konservativen Notwegs und die konkrete
vertrauenswürdige Windows-Boot-/Aktualitätsquelle samt Tests. Uptime oder
LastBootUpTime/Wanduhr allein wird nicht als fertiger Beweis festgelegt.
Ein neues Journal ohne Baselinebeleg ist keine Recovery. Wer Recovery nach
Supervisorverlust ohne Kernelneustart verlangt, muss vorher einen eigenen
unabhängigen kurzlebigen Kontroll-/Beobachterweg freigeben; kein Hintergrunddienst
wird hier vorgeschlagen oder implementiert. Reguläre Workerfehler mit C bleiben
ohne solchen Notweg weiter nutzbar; Crash-Recovery ist der konservative Sonderfall.

### 5. HTTP-Fehlervertrag E1 und spätere Frontendabnahme

Empfohlene Statuszuordnung für BEIDE bisherigen POST-Endpunkte; noch keine
Controller-/JS-Änderung und keine Zusage, dass diese Status heute geliefert werden.

| Lage | Statusvorschlag | Neutrale künftige UI-Meldung / Bedeutung |
|---|---:|---|
| Slot durch laufenden Start/Import/Cleanup belegt | 429 | „Ein Import läuft bereits. Bitte später erneut versuchen.“ Kein Retry-After-/Endzeitversprechen. |
| Kontrollierter Worker-/Protokoll-/Ressourcenfehler, C bestätigt | 500 | „Import konnte nicht abgeschlossen werden.“ Neuer expliziter Request erst nach IDLE, kein automatischer Retry. |
| Zeitlimit erreicht, Cleanup bestätigt oder noch innerhalb seiner Frist | 500 | „Zeitlimit des Imports überschritten.“ Das behauptet keinen bereits abgeschlossenen Prozessabbruch. |
| Cleanup des auslösenden Auftrags unbestätigt/über Frist | 500 | „Der Import ist fehlgeschlagen. Weitere Importe sind derzeit gesperrt.“ Nicht „alle Prozesse beendet“. |
| Neuer Request bei Quarantäne/ungeklärter Recovery/nicht verfügbarem Adapter | 503 | „Import ist derzeit nicht verfügbar.“ Kein Start, kein Fallback und kein Wiederholungsversprechen. |

Bereits ausgelieferte 500-Antwort nicht erneut senden; tritt Quarantäne erst
später ein, gilt 503 für nachfolgende Importrequests. Quarantäne/Shutdown-
Admissionsperre hat Vorrang vor Busy; fehlerhafte Eingaben bleiben bestehende
400-Verträge, Host/Origin/CSRF und 405/OPTIONS bleiben vor Gatewayarbeit wirksam.
Bei gültigem Ergebnis und technischer Störung niemals Teilpayload plus 200.

**Empfehlung zur Body-Kompatibilität:** Technische 429/500/503 zunächst leer
wie die bisherigen technischen 500; keine neue globale Fehler-JSON-Struktur,
kein Exceptiontext und kein Rohstderr. Die obigen Texte sind feste lokale
Frontendtexte, kein Serverdetailtransport. Erfolgspayloads/Content-Types,
bestehende error-Maps für Eingabefehler und Session-/CSRF-Verträge unverändert.
Unterscheidung Workerfehler/Timeout bei identischem 500 wird nicht durch
erfundene JSON-Felder versprochen: ohne separat freigegebene feste Fehlercodes
zeigt das Frontend einen gemeinsamen neutralen 500-Text. 429/503 können später
allein anhand des Status spezifisch erklärt werden. Kein 504-Wechsel erforderlich.

Vor Integration freizugeben und gezielt zu prüfen:

- MockMvc mit aktivem Securityschutz und Gateway-Fakes: beide Operationen 429/
  500/503, Content-Type/Body gemäß E1; keinerlei Referenzservice-/Repositoryarbeit
  nach technischem Fehlschlag; keine Rohdaten/Stacks/URLs/Pfade.
- Erfolg unverändert, einschließlich optionaler Mapfelder/Preisstrings,
  Nachvalidierung, Band-Autofill, GET/HEAD 405 und OPTIONS ohne Aktion.
- Admission-Rennen Reihe/Band, Stopp-/Cleanupfenster, Quarantänevorrang,
  Fristablauf trotz gültiger später Map, Clientdisconnect ohne Retry oder
  Aufgabe des Ressourcenowners. Keine neuen DB-Zugriffe in diesen Mockfällen.
- jsdom: feste 429/503-Meldungen, 500 neutral; keine Fehlerbodys lesen, keine
  nativen Alerts, kein automatisches Fetch-Replay, Formularwerte erhalten,
  Ladeindikator/Buttons korrekt zurücksetzen. Bestehende Sperrsemantik für
  unklare tatsächliche Speichervorgänge unverändert; persist:false beim Import
  ist kein Beleg, dass der technische Live-Slot schon wieder frei ist.
- Spätere synthetische Browserabnahme im geschützten manual-test prüft UI/Session,
  niemals Live-Worker/Firefox-Containment. Keine neue Browserfreigabe jetzt.

E1 ist in dieser Spezifikation empfohlen. Einführung von 429/503, feste UI-Texte
und etwaige spätere Fehlercodes benötigen den ausdrücklichen Integrationsauftrag.

### 6. JNA und Artefaktvertrag A1

**Konkrete Empfehlung für eine nächste Adapterfreigabe:**
net.java.dev.jna:jna:5.17.0 als explizit gepinnte direkte Abhängigkeit nur der
isolierten Windows-Adapterkonfiguration. Grund: dieselbe Version wie der lokale
historische Win64-Nachweis, nicht die Behauptung der neuesten/sichersten Version.
jna-platform zunächst NICHT hinzufügen; die bisher benötigten Kernel32-
Schnittstellen wurden im Prototyp über Core abgebildet. Falls Platform später
nachweislich Wartungsvorteile bringt, beide Artefakte exakt gleich versionieren
und Änderung separat prüfen. Bestehenden Prototyp nicht ungeprüft übernehmen.

Vor Übernahme: offizielle Artefaktherkunft/Hashes und aktuelle Sicherheitshinweise
prüfen, tatsächlich gewählte Compile-/Runtimegraphen einschließlich Selenium-
Transitiven und konkurrierender 5.13.0/5.17.0-Versionen auswerten, Zielversion
eindeutig festsetzen, Verification-/Lockinformationen nach Freigabe erzeugen.
Cachekopie allein ist kein Auflösungsvertrag; kein local-files-JAR oder
dynamisches „latest“ in die Anwendung einbauen. Jetzt weder aufgelöst noch geladen.

| Artefakt | Empfohlene Verantwortungs-/Dependencygrenze |
|---|---|
| Gemeinsamer Gateway/Vertrag | Nur Java-Daten/Interfaces; keine JNA-Typen, statische Native.load-Initialisierung, Workerpfade oder Supervisorerzeugung. |
| Windows-Adapter / späterer Supervisor | Explizite eigene Konfiguration/SourceSet oder kleines Modul; nur dort Job-API/JNA. Windows/Architektur/ABI-Voraussetzungen prüfen, bevor Lazy-Factory DLL lädt. Andere Plattform oder Ladefehler → Live nicht verfügbar, kein ProcessBuilder-Fallback. |
| Workerartefakt | Eigenes minimales Runtime-Set mit benötigter Extraktion/Selenium/Jsoup/Codec, ohne Boot/JPA/JDBC/HTTP-Server, Supervisor oder Jobkontrolle. Falls Selenium selbst JNA benötigt, transitive Notwendigkeit separat belegen; das ist keine Berechtigung zur Parent-Jobkontrolle. |
| manual-test | Nur vorhandene synthetische Doubles. Kein Worker-/Adapter-/Supervisorcode/-JAR, keine JNA-/jna-platform-/jnidispatch-Liveabhängigkeit; positive Runtime-Allowlist statt Vertrauen auf drei bisherige Scraper-Excludes. Fehlendes Double weiterhin fail-closed. |

Paketierung nicht nur nach Dateinamen beurteilen: gebautes manuelles Archiv,
nested JARs, native Ressourcen, Manifest/Start-Class, Services/Autokonfiguration,
Klassenreferenzen und versehentliche Workerressourcen später negativ prüfen.
Der heutige allgemeine Runtimefilter ist kein hinreichender Nachweis für diese
künftige Grenze. B-MODEL-1s Testdateien gelangen ohnehin nicht in Laufzeitartefakte;
das löst die spätere Produktionspaketierung nicht.

Native Ressourcenverträge vor Implementierung:
ein Owner je Handle/Attributspeicher/Struktur/Pipe/Thread; Close idempotent,
richtige Win32-Sentinels und Pointer-/DWORD-/BOOL-/SIZE_T-Breiten, richtige
Unicode-/Aufrufkonvention und Layouts, gültige Rückgabewerte prüfen.
GetLastError unmittelbar und nur mit korrekter Fehlersemantik auswerten.
Keine numerischen Handles über Ticketwechsel wiederverwenden; keine
Finalizer/GC-Freigabe als Sicherheitsbarriere. Start-/Resume-/Cancelrennen
besitzen Attribut-/Environment-/Commandlinepuffer bis zur echten nativen Rückkehr.
Keine überfällige Future darf diese Puffer freigeben. Native DLL-/Extraktions-
pfade und erlaubte JVM-/JNA-Overrides explizit begrenzen und prüfen; kein
ungeprüftes Laden aus Arbeitsverzeichnis/TEMP/Suchpfad.
Jobhandle nicht vererben, nur benötigte Pipe-Enden über explizite Handleliste.
Close-/Query-/Waitfehler bleiben offen/UNKNOWN und sperren Wiederfreigabe.

### 7. Eng abgegrenzter Folgeauftrag B-ADAPTER-1 – noch freizugeben

1. Isolierten Windows-Adapterbaustein mit KernelCalls-Port und Fake schaffen,
   getrennt von main/Gateway/Worker/manual-test. B-MODEL-1 nicht in produktive
   Quellen verschieben und nicht durch Namensänderung zur Runtime erklären.
2. Explizit genehmigte JNA-Core-Version 5.17.0 und getrennte Buildkonfiguration
   hinzufügen; keine zusätzliche Platform-Abhängigkeit ohne Begründung.
   Auflösung offline soweit vorhanden; erforderlicher Download nur nach Freigabe.
   Auch diese Build-/Dependencyänderungen sind heute nicht beauftragt.
3. Schmale Schnittstellen für private Jobanlage/Limits, suspendierten atomaren
   Start, Resume, vollständigen bounded Snapshot, gehaltene Instanzidentität,
   owned Terminate/Wait/Close und Ressourcenledger ausarbeiten. Keine Resume-
   oder Terminateberechtigung anhand eines persistenten numerischen Handles.
4. Ausschließlich injizierte Fake-Kernelaufrufe testen: kein statisches
   Native.load und kein echter Prozessstart in Unit-Tests. Fehler an jeder
   Akquisitions-/Closekante, pending Start mit lebendem Speicher, fremdes Ticket,
   Handle-Reuse, Quoten-/Queryfehler und gemeinsame Restfrist prüfen.
5. Fertigkriterium: keine Produktionsverdrahtung, keine Worker/IPC/Extraktion/
   HTTPänderung, keine DLL beim Laden gemeinsamer Typen; Source-/Dependency-
   Prüfung und gezielte Fake-Tests nach Offline-Dry-Run. Archiv-/OS-Behauptungen
   erst nach eigener späterer Prüfung, keine nativen Tests mitfreigeben.

Danach gesondert je synthetischen OS-Fall freigeben: ABI/atomare Zuordnung,
Quotendiskrepanz/Nestedjobs, Membership, Gruppen-/Pipe-/Handleabschluss,
Kill-on-close und Supervisor-Crash; feste Mengen, Fristen und unabhängige
Cleanupgrenze vor jedem Fall. Keine Firefox-/Geckodriverabnahme vor diesen
Nachweisen. Recoveryjournal/Bootquelle und echte IPC/Workerimplementierung
benötigen eigene anschließende Aufträge; kein Dienst/Broker/DB neu vorgesehen.

### 8. Nachweisstand dieses Auftrags und offene Live-Blocker

Nur statische Datei-/Konfigurations-/Cacheinventur und eine arithmetische
Größenabschätzung, keine Gradle-/Build-/Test-/Dependencyauflösung. Keine neuen
Modelltests: die entscheidenden neuen Pflichten sind echte Durability,
Boot-/Instanzidentität, Quoten und tatsächlicher Speicherverbrauch; ein weiterer
Fake-Wahrheitswert würde deren Umsetzung nicht belegen. Die 74 erfolgreichen
B-MODEL-1-Tests bleiben historischer Stand und wurden hier NICHT erneut ausgeführt.
Modellquellen/Assertions unangetastet, keine neue Testanzahl behauptet.

Live-Freigabe weiterhin blockiert durch ungeklärte Gesamtprozessquote,
noch nicht abgenommene Crash-/Kill-on-close-/Nested-/Cleanup-/Firefoxwirkung,
fehlende vertrauenswürdige Recovery-/Boot-/Aktualitätsquelle und Workertrennung,
offene Speicher-/Maximalpayloadmessung sowie fehlende JNA-/Paketintegration.
Diese Blocker werden weder durch neue Ticketnummern noch durch diese
Dokumentation gelöst. SSRF/Netzwerkisolation und T03/T04/T06 bleiben separate
Aufträge. Nur TESTING.md/ROADMAP.md ergänzt; Historie, STOPs, Prototypen,
Schutzmechanismen und Bestandsdateien bleiben erhalten. Keine Git-Operationen,
DB/SQL, Prozessexperimente, Anwendungen, Listener, Browser, Scraper, Downloads
oder Installationen. Nächste Umsetzung erst nach ausdrücklicher Freigabe.



## T02 – B-ADAPTER-1: isolierter Jobadapter mit Fake-Kernel (23.10.2026)

Freigegeben und umgesetzt ist ausschließlich der erste Ressourcen-/Kernel-
Baustein für deterministische Unit-Tests. Keine produktive Win32-Implementierung,
kein Supervisor-/Gateway-/Workeranschluss und keine OS-Ausführungsfreigabe.
B-SPEC-1, B-MODEL-1 und B-MODEL-2 einschließlich aller historischen STOPs bleiben
unverändert; die vier B-MODEL-1-Dateien wurden nicht bearbeitet.

### Dateien, Verantwortlichkeiten und Buildgrenze

Alle vier neuen Dateien liegen unter src/test/java/de/mangalib/windowsadapter:

| Datei | Verantwortung |
|---|---|
| KernelCalls.java | Schmaler injizierter Kernel-Port: Job/Limits/Readback, Startvorbereitung, atomarer suspendierter Startvertrag, Resume, bounded Snapshot und Gruppenendbeobachtung, Prozessidentität/Membership, owned Terminate/Wait und typisiertes Close. Keine native Implementierung. |
| OwnedJobAdapter.java | Einmaliger Ressourcenlebenszyklus einer Gruppe mit externem Owner-Ticket; eigenes Scope, Ressourcenledger, Startversuchs-Permit, gehaltene Instanzen und Fehler-/Endbericht. |
| FakeKernelCalls.java | Vollständig steuerbare In-Memory-Simulation: Fehlerstellen, variable Population, PID-/Instanzdaten, unvollständige Snapshots, fake Zeitverbrauch und unklare Close-/Waitergebnisse. |
| OwnedJobAdapterTest.java | 45 deterministische JUnit-Fälle inklusive parametrisierter Varianten, ausschließlich synthetische Objekte. |

Keine neue Dependency, kein SourceSet und keine Buildskriptänderung erforderlich.
Die neuen Klassen importieren nur JDK und für Tests JUnit. Kein JNA-Typ,
Native.load, ProcessBuilder, Spring-Kontext, JDBC, Netzwerk oder Datei-I/O im
Adapter-/Fake-Testpfad. Das Test-SourceSet wird weder in bootJar noch in die
vorhandene manuelle main/manualTest-Paketierung übernommen. Produktion und
manueller Launcher können diesen Baustein nicht über ihre bestehenden
Quell-/Laufzeitpfade laden. Gebaute Archive wurden hier nicht erneut erzeugt
oder geprüft; Nachweis ist unveränderte Quell-/Buildkonfiguration.

JNA-Core 5.17.0 bleibt die genehmigte Option für einen später gesonderten
nativen Implementierungsschritt. Für die Fake-Stufe nicht benötigt und deshalb
NICHT deklariert, aufgelöst oder geladen; jna-platform ebenfalls nicht ergänzt.
Keine Aussage, dass der bestehende allgemeine Testclasspath keine zufälligen
JNA-Transitiven enthält. Der neue Code benötigt sie nicht und ist nicht an eine
verfügbare native DLL gebunden.

Aus dem Prototyp gezielt übernommene SEMANTIK: private Gruppe, Kill-on-close
ohne Breakaway, konfigurierte Limits mit Readback, getrennte Prozess-/Thread-
Ressourcen, JOB_LIST-/SUSPENDED-Startvertrag, vollständige Listen/Accounting,
instanzbezogene Mitgliedschaft und Abschluss vor letztem Job-Close. Keine
ungeprüfte Kopie seiner JNA-Strukturen, FieldOffsets, statischen DLL-Initialisierung
oder finally-Cleanupstrategie. Limits/Observation/Identity sind ABI-neutrale
Java-Daten, KEIN neuer Nachweis für STARTUPINFOEX, PROCESS_INFORMATION oder
JOBOBJECT_EXTENDED_LIMIT_INFORMATION. Native Marshalling-/Layoutarbeit bleibt offen.

### Eigentum und Kernelvertrag

- Der Supervisor würde das Owner-Ticket als eindeutige Objektfähigkeit liefern.
  Pro Adapter entsteht zusätzlich ein frisches Scope; Ressourcen werden nur
  dieses Scope angenommen. Numerische debugValue-Werte sind Fake-Kennungen,
  ausdrücklich KEINE Win32-Handleberechtigung oder native Sentineldefinition.
- Job, Prozess, Thread, Attributliste und Backing Memory besitzen getrennte
  Ledger-Einträge OPEN/CLOSED/UNCERTAIN. Ressourcen sofort nach Erwerb registrieren,
  bevor ein weiterer Vorbereitungsschritt fehlschlagen kann. Auch Teilhandles
  eines fehlgeschlagenen Starts bleiben vollständig zugeordnet.
- KernelCalls.prepareLaunch und startSuspended erhalten einen Erwerbs-Callback.
  Ein späterer echter Port muss sämtliche Teilallokationen darüber sicher
  übertragen und bekannte API-Fehler von unbekanntem Ausgang unterscheiden.
  Keine Ressource außerhalb des Ledgers verlieren oder nach PID rekonstruieren.
- Atomare Zuordnung und suspendierter Zustand sind eine VORAUSSETZUNG des
  Kernel-Ports, jetzt nur vom Fake geliefert. Resume ist eine separate explizite
  Owneraktion nach Membership-/Identitätsprüfung; kein automatisches Resume
  aus einer Rückmeldung und kein zweiter Resumeversuch bei unklarem Ergebnis.
- PENDING simuliert eine ausstehende native Rückkehr. Job/Attributliste/Memory
  bleiben lebendig. Eine spätere Rückmeldung benötigt genau Owner plus
  StartPermit; alte/fremde/doppelte Rückmeldungen bewirken keine Freigabe.
  Nach bereits angefordertem Cleanup ist Resume ausgeschlossen.
- Identität umfasst PID, Erzeugungszeit und Image sowie native private
  Mitgliedschaft. Ein Name wie conhost.exe allein reicht nicht. Rollenvertrauen
  ist weiterhin Supervisorsache; Membership ist kein Beweis harmlosen Codes.
- Beim Capture wird jede neue beobachtete Instanz über ein gehaltenes Handle
  geprüft. Root- und Teilhandles werden ebenfalls berücksichtigt. Vollständigkeit,
  positive/duplikatfreie PIDs, Kapazität, stable-Erhebungsbeleg und Listen-/
  Accountingkonsistenz werden geprüft. Keine willkürliche Subtraktion von Helfern.
- Der Gruppenendversuch verlangt eigene Endbeobachtung am noch offenen Job,
  null aktive Mitglieder, vollständige leere Liste und Signalisierung sämtlicher
  gehaltenen Instanzen mit unveränderter Identität. Ein Terminate-Erfolg reicht
  ausdrücklich nicht. Schlechte Listen/Membership verhindern Endbestätigung;
  sie verhindern nicht die ausdrücklich verlangte Terminierungsanforderung
  an den bereits eindeutig besessenen eigenen Job. Kein PID-/Namens-/Fremd-Kill.
- Erst nach Endbeleg schließen: Thread-/Prozesshandles, Attributliste, Backing
  Memory, zuletzt Job. Scheitert Attributfreigabe, bleibt abhängiger Speicher
  erhalten; bei anderem offenen/unklaren Handle bleibt der Job offen.
- Erfolgreiche Closes werden nicht wiederholt. Unklarer Close-Ausgang wird
  UNCERTAIN, niemals durch einen zweiten Close auf denselben Zahlenwert
  „getestet“. Kein AutoCloseable/finally, das trotz fehlenden Endbelegs Handles
  wegwirft oder Primärfehler durch sekundäre Cleanup-Exceptions verdeckt.
- CleanupReport enthält getrennt groupEndConfirmed, resourcesClosed,
  quarantineRequired und eine unveränderliche Liste der Probleme mit intern
  aufbewahrter Ursache. Bekannte Initialisierungs-/Startfehler können nach
  sicherem Ende vollständig bereinigt sein; unbekannte Identität/Close-/Endlage
  erfordert Quarantäne. Eine bereits beobachtete Cleanupunsicherheit bleibt
  auch nach späterem Ressourcenende sichtbar. Keine HTTP-/Loggingintegration.

### Frist und Abgrenzung zum Supervisor

Der Aufrufer gibt eine absolute Cleanupdeadline und monotone Testuhr vor.
Erster Wert wird festgehalten; spätere Aufrufe verlängern ihn nicht.
Prozess-Waits, awaitEmptyJob und Close verwenden dieselbe Restfrist. Ein
erfolgreicher Close nach Fristende ist kein rechtzeitiger Abschluss. Bei
abgelaufener Frist keine weiteren Wait-/Close-/Terminateversuche beginnen.
Kein neues Vollbudget je Mitglied, kein Sleep, Hintergrundthread oder Timer.

Wiederholter Cleanup liefert denselben Bericht und keine erneuten Kernel-
Freigabeversuche. Nur echte späte Rückkehr eines noch offenen Starts macht eine
weitere explizite Bereinigung eigener neu verbuchter Ressourcen möglich, unter
DERSELBEN Deadline und unter Erhalt des Unsicherheitsbefunds. Ist die Frist dann
abgelaufen, bleibt der Ressourcenbestand owned/gesperrt; kein automatischer
Neustart oder nachträgliches unbegrenztes Cleanup wird versprochen.

Dies ist kein konkurrierender Supervisor: keine gemeinsame Admission, Queue,
Retry-, Shutdown-, HTTP-, Recovery- oder Ergebnisfreigabelogik. Ein Adapterbericht
gibt niemals einen Anwendungsslot frei. B-MODEL-1s vollständige C-Barriere,
B-MODEL-2s Journal-/Recoveryabschluss, IPC und weitere Supervisorpflichten bleiben
zusätzlich erforderlich. Ein neuer Adapter pro Ticket ist auch keine Erlaubnis,
einen alten unbestätigten Auftrag zu vergessen.

**Grenze zum echten Port:** Alle Fake-Operationen sind synchron deterministisch
und kehren unmittelbar zurück oder simulieren PENDING. Die Synchronisierung
dieses Testbausteins beweist keine Reaktionsfähigkeit bei einem blockierenden
echten Win32-Aufruf. Vor nativer Integration müssen kontrollierte native
Ausführung/Rückmeldung, Lockgrenzen und Lebensdauer von Attribut-/Commandline-/
Environmentpuffern so getrennt werden, dass Cleanup nicht an einem gehaltenen
Java-Monitor auf eine hängende Startoperation warten muss. Keine Future-
Cancellation oder Threadinterrupt als Freigabebeleg voraussetzen.
Auch stable-Snapshots sind keine neue atomare Windows-API; der echte Port muss
ihren bounded Erhebungs-/Endbeleg erst implementieren und gesondert validieren.

### Tatsächlich geprüfte Fälle und Ergebnisse

Vor jedem tatsächlichen Lauf identischer gefilterter Offline-Dry-Run:

~~~powershell
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

Geprüfter Graph ausschließlich compileJava, processResources, classes,
compileTestJava, processTestResources, testClasses und gefilterter test.
Keine manualSafetyTest-Unterprozesse, Boot-/JPA-Kontexte, Integrationstests,
Archivaufgaben oder neue nativen Aufrufe. Imports/ausgewählte Klassen zuvor
auf Nebenwirkungen geprüft. Java 21, Gradle 8.14.3, vorhandener lokaler Cache;
kein Download. Reguläre Gradle-/JUnit-JVMs sind die einzigen Testprozesse.

Ausführungshistorie dieses Auftrags:
1. Erster Sandbox-Dry-Run am Gradle-Cache-Lockzugriff verweigert; keine Gradletasks.
2. Derselbe Dry-Run mit Cachezugriff erfolgreich. Erster echter Lauf endete bei
   compileTestJava: drei kanonische Record-Konstruktoren im Interface mussten
   public sein. Korrigiert, keine Tests aufgrund dieses Compilefehlers ausgeführt.
3. Neuer identischer Dry-Run und Testlauf erfolgreich: 44 neue + 74 bestehende,
   insgesamt 118 Tests. Kein bestehender Test oder Assert geändert.
4. Eigenen bounded Gruppenend-Wait und Restfristfall ergänzt, Primär-/Sekundär-
   ursachen explizit aufbewahrt. Wieder identischer Dry-Run erfolgreich.
   Abschließender Lauf laut XML ausschließlich dieser drei Klassen:

| Klasse | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| OwnedJobAdapterTest | 45 | 0 | 0 | 0 |
| SupervisorModelTest | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest | 39 | 0 | 0 | 0 |
| Finale Auswahl | **119** | **0** | **0** | **0** |

118 und 119 sind Entwicklungsstände, nicht zu addieren. BUILD SUCCESSFUL;
main-Kompilierung/Ressourcen up-to-date, nur compileTestJava/test neu ausgeführt.
Keine vollständige Suite behauptet. Die bisherigen 74 Modellfälle wurden
unverändert erneut ausgeführt; Ergebnisse anderer Integrationstests nicht erneuert.

Neue Fake-Fälle: private Initialisierung/Limits-Readback; Jobanlage-/Configure-/
Read-/Mismatchfehler; partielle Memory-/Attributinitialisierung; Startfehler
vor/nach Zuordnung; explizites Resume mit Fehler/doppeltem Aufruf; sechs eigene
dynamische Mitglieder einschließlich zusätzlicher Konsolenhosts; unvollständige/
instabile/duplizierte/negative/zu große/widersprüchliche Listen; Query-/Open-/
Identitäts-/Membershipfehler; Terminatefehler und Terminate ohne Wirkung;
ausbleibendes Prozesssignal trotz leer werdender Gruppe; vollständiges Ende;
Prozess-/Attribut-/Memory-/Jobclosefehler; unverdeckte Primär- und Sekundärfehler;
doppelter Cleanup; numerische Handlewert-Wiederverwendung; fremde Tickets,
Gruppen und gleichnamige Prozesse; PID-/Erzeugungszeitwiderspruch; gemeinsame
Prozess-/Gruppen-/Closefrist; Deadline schon abgelaufen; pending Start und
verspätete/fremde/doppelte StartPermits. Keine Testprozesse oder Betriebssystem-
Handles dafür erzeugt. Die Simulation von sechs Mitgliedern bei Limitkandidat 3
belegt KEINE Windowsquotenwirkung und ändert keine historische Drei-PID-Assertion.

### Nächste OS-Stufe – Empfehlung, NICHT freigegeben

Zuerst native Bindings isoliert ergänzen und dafür explizit die getrennte
JNA-Core-/Buildintegration freigeben; Fake-Port dabei erhalten. Keine produktive
Verdrahtung und keine automatische native Testsuite. Die hier nicht benötigte
JNA-Deklaration nicht nebenbei in main oder manual-test nachholen.

Danach als kleinste gesondert genehmigte Windows-Abnahme:
**Win64-Layout plus genau eine leere eigene Jobgruppe, null Prozessstarts.**
Feste geprüfte Java-/JNA-Version, Größen/Offsets/Pointer-/BOOL-/SIZE_T-/Unicode-
Mapping, privates nicht vererbbares Jobhandle, Kill-on-close/Breakaway-
Konfiguration und effektiver Readback, vollständige leere Liste/Accounting,
geordnetes eigenes Close und Ressourcenbilanz prüfen. Kleine vorher festgelegte
Puffer-/Aufruf-/Zeitobergrenzen; bei unbekanntem Ausgang STOP und keine Wiederholung.
Keine CreateProcessW-/Resume-/Worker-/Firefox-/Geckodriveraufrufe, kein fremder
Job und kein behaupteter Wirksamkeitsnachweis von Kill-on-close oder Prozessquote
aus dem bloßen Setzen/Rücklesen einer Konfiguration. Historische Prototypen/STOPs
nicht umschreiben oder reaktivieren; neue Freigabe/Evidenz erforderlich.

Erst danach neue gesonderte Entscheidungen über atomaren suspendierten Start,
Quoten-/Nested-Diskrepanz, dynamische Mitgliedschaft, echte Gruppen-/I/O-Ende,
Kill-on-close, Supervisorcrash und Offline-Firefox. Für Prozessstufen zuerst feste
zulässige Startzahlen und unabhängige Cleanup-/Abbruchgrenze abnehmen; das
ungeklärte ActiveProcessLimit=3 nicht als bereits wirksame Absicherung verwenden.

### Fortbestehende Grenzen

Keine echte Windows-Implementierung, ABI-Prüfung, Quotenwirkung, Crash-/Recovery-
Garantie, Jobvererbungs-/Breakawayprüfung, native Pipeauflösung oder tatsächliche
Firefoxkompatibilität nachgewiesen. Rollenvertrauen, persistente Recovery,
Ergebnis-/IPC-/Speicherbudgets und SSRF bleiben separate Arbeiten.
45 Fake-Tests sind keine OS-Abnahme. Keine Produktivänderung, Git-Operation,
DB/SQL/Migration, Anwendungs-/HTTP-/Browser-/Worker-/Scraper-/Win32-Versuche,
Installation oder Dependencyänderung. Nur vier neue Testbereichdateien und
Nachträge in TESTING.md/ROADMAP.md. Nächste Stufe erst nach ausdrücklicher Freigabe.


## T02 – B-ADAPTER-2: isolierte Win64-/JNA-Bindung und einmalige Leergruppe (23.10.2026)

Freigegeben und abgeschlossen: native TEILIMPLEMENTIERUNG für private leere
Job Objects, 28 neue Layout-/Fake-API-Tests und genau EIN nativer Leergruppen-
Versuch. Keine Worker-/Produktintegration und keine Freigabe weiterer OS-Fälle.
B-SPEC-1/B-MODEL-1/B-MODEL-2/B-ADAPTER-1 sowie historische STOPs bleiben erhalten.

### Dateien und isolierte Abhängigkeiten

Neue Dateien:
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64Layouts.java
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64KernelCalls.java
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64EmptyJobProbe.java
- src/jobAdapterTest/java/de/mangalib/windowsadapter/Win64BindingsTest.java

build.gradle.kts erhielt nur einen MessageDigest-Import und einen angehängten
Experimentblock: Source Sets jobAdapter/jobAdapterTest, explizite Tasks
jobAdapterUnitTest, jobAdapterDependencyReport und jobAdapterEmptyJob.
Der bisherige Buildskriptkörper einschließlich main/manualTest-Paketierung
wurde bytegenau erhalten. Keine neuen Tasks an assemble/check/test angehängt.

jobAdapter kompiliert die unveränderten KernelCalls/OwnedJobAdapter-Quellen
gezielt zusätzlich für das Experiment; jobAdapterTest verwendet den unveränderten
FakeKernelCalls. Keine Umdeklaration dieser Teststruktur zur Produktarchitektur.
Keine Änderung der bisherigen Klassen, Modelle, Assertions oder DB-Guards.
Keine main/manualTest-Vererbung oder Aufnahme der Experimentausgaben in deren
Classpath/JAR. Das Source Set jobAdapterTest hat nur lokale JNA-Core- und
JUnit-Abhängigkeiten (JUnit Jupiter/Launcher 6.0.3); keine Spring-/JDBC-Integration.

**Tatsächliche JNA-Einbindung:** Das vorhandene lokale jna-5.17.0.jar wurde
mit Manifest Implementation-Version 5.17.0 (b0), Bundle-Version 5.17.0 und
SHA-256 b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0 geprüft.
Die erste reguläre koordinatenbasierte Offlineauflösung von
net.java.dev.jna:jna:5.17.0 scheiterte VOR der Java-Kompilierung: lokal liegt
das JAR, aber keine für Gradle auflösbare Modulmetadatenversion vor.
Kein Netzwerkzugriff/Download oder Installieren von Metadaten als Umgehung.

Daraufhin ausschließlich im Experiment: lazy auf genau eine vorhandene Datei
im Gradle-Cache unter der festen Version 5.17.0 zugreifen, SHA-256 zwingend
prüfen und als FileCollection für jobAdapter/jobAdapterTest verwenden. Fehlende/
mehrere/abweichende Dateien sperren den Build. Keine Repositoryänderung und
keine dynamische Auswahl/Upgradefallback. Dies ist ein transparent dokumentierter
lokaler Experimentpfad, KEINE bereits funktionierende Maven-Modulauflösung und
keine Empfehlung für ein produktives local-files-JAR. Vor Produktübernahme
weiterhin offizielle Herkunft/Metadaten/Verification und normale reproduzierbare
Dependencyauflösung gesondert freigeben; ein lokaler Hash allein beweist keine
offizielle Herkunft oder Sicherheitsaktualität.

Der ausgeführte jobAdapterDependencyReport löste die isolierte Runtime konkret
auf genau dieses JAR auf, bestätigte den Hash und leere Konfigurationsvererbung.
Kein jna-platform in diesem Adapterpfad. Bereits mögliche transitive JNA-
Abhängigkeiten bestehender Selenium-Konfigurationen wurden nicht umgeschrieben.
Normale und manuelle Anwendung erhalten KEINE zusätzliche neue Laufzeit-
abhängigkeit oder nativen Startpfade durch diesen Experimentblock.
Keine normalen/manuellen Archive neu gebaut; diese Aussage beruht auf erhaltenem
Buildkörper und getrennten Source-/Classpathpfaden, nicht auf einer neuen
Paketierungs-/Browserabnahme.

### Native Bindings und ABI

Win64KernelCalls implementiert KernelCalls mit Lazy-Factory für Windows x64,
Java 21 und JNA 5.17.0. Erst diese explizite Factory lädt Kernel32.
Unterstützte echte Bindings: CreateJobObjectW, SetInformationJobObject,
QueryInformationJobObject, CloseHandle und GetLastError-Schnittstelle.
Fehlercodes werden sofort über JNAs gesicherten Thread-GetLastError gelesen;
kein Verlassen auf einen möglicherweise alten Fehlercode nach API-Erfolg.

Keine Bindung für CreateProcessW, ResumeThread, AssignProcessToJobObject,
TerminateJobObject oder Prozess-Waits vorhanden. Die entsprechenden KernelCalls-
Operationen werden mit eindeutigem nicht unterstütztem Fehler abgewiesen,
ohne OS-Aufruf. Native Limits sind auf Kill-on-close OHNE Breakaway, Prozess-,
Commit- oder CPU-Quote beschränkt. Andere Kandidaten werden vor Set abgewiesen.
Ein voller zukünftiger Kernelport benötigt keine jetzige Schnittstellenänderung,
aber zusätzliche separat zu validierende Implementierungen. Nicht als vollständigen
Workeradapter ausgeben.

Win64-Strukturen und geprüfte Größen/Offsets:

| Struktur | Größe / wichtige Offsets in Byte |
|---|---|
| JOBOBJECT_BASIC_LIMIT_INFORMATION | 64; LimitFlags 16, ActiveProcessLimit 40, Affinity 48 |
| IO_COUNTERS | Sechs 64-Bit-Zähler; eingebettet ab Offset 64 |
| JOBOBJECT_EXTENDED_LIMIT_INFORMATION | 144; JobMemoryLimit 120 |
| JOBOBJECT_BASIC_ACCOUNTING_INFORMATION | 48; TotalProcesses 36, ActiveProcesses 40, TotalTerminatedProcesses 44 |
| JOBOBJECT_BASIC_PROCESS_ID_LIST | C-Minimalstruktur 16; zwei DWORDs als 8-Byte-Header, erster ULONG_PTR/PID ab Offset 8; dynamischer Puffer 8 + 8 × Kapazität |
| Plattform | Pointer/SIZE_T-Abbildung 8, Windows LONG 4, WCHAR 2; DWORD und ReturnLength 4 |

Keine Annahme, dass C-long auf Win64 acht Byte besitzt. Native LPReturnLength
ist IntByReference, nicht LongByReference. Layoutklassen laden Kernel32 nicht.
Sie prüfen JNA-Größen/Offsets unabhängig von den alten Prototypdateien; keine
historische Assertion ersetzt. Die ursprünglichen Prototypgrößen dienten dem
Abgleich, nicht einer neuen pauschalen ABI-Freigabe.

Native Puffer sind nullinitialisiert, strukturelle ReturnLengths streng geprüft.
PID-Decoder prüft vollständige Header/Zahlen, Kapazität, tatsächliche Rückgabegröße,
positive DWORD-PIDs und Duplikate. Allgemeiner Fake-Testpfad besitzt höchstens
einen begrenzten Grow bei ERROR_MORE_DATA/INSUFFICIENT_BUFFER; kein Retry-bis-stabil.
Für die echte einmalige Leergruppenprüfung ist auch dieser Grow deaktiviert:
ein unerwarteter API-Fehler beendet den Versuch, ohne Query-Wiederholung.
Der Leergruppenport verweigert nichtleere/inkonsistente Gruppen und erhebt
Accounting → PID-Liste → Accounting, ohne eine allgemeine atomare Snapshot-API
vorzutäuschen. Beobachtung laufender Gruppen ist noch nicht implementiert.

### Besitz, Fehler und Ressourcen

Ein erfolgreicher Create liefert eine neue Java-Resource-Fähigkeit mit eigenem
Scope; die reale Pointeradresse bleibt intern einer Identitätsmap zugeordnet.
NULL/INVALID_HANDLE_VALUE werden abgewiesen. Forge durch gleiche Nummer,
fremde Ressource, Nutzung nach Close und Doppelclose werden vor nativem Aufruf
abgewiesen. Je Port maximal ein Create-Versuch, auch nach einem Createfehler.

Close markiert zunächst UNCERTAIN; nur API-Erfolg wird CLOSED. Fehler führt
nicht zu einem blinden zweiten Close. Interne Fehler enthalten Operation und
Win32-Code. Der bestehende OwnedJobAdapter konnte mit dem Fake-RawApi-Port
unverändert einen vollständigen Leergruppenlebenszyklus bestätigen.

Payloadpuffer werden explizit geschlossen und gezählt; DWORD-Rückgabepuffer
ebenfalls explizit geschlossen. JNA-Layoutspeicher wird nach Layoutprüfung
explizit abgeschlossen. JNI-/DLL-Ladung der isolierten Host-JVM ist von
eigenen Jobhandles/Payloadpuffern zu unterscheiden; deren Zähler behaupten
keine rechnerweite oder alle JNA-internen Ressourcen umfassende Bilanz.
Keine verändernden Zugriffe auf fremde/übergeordnete Jobs.

Der echte Prüfhost bewahrt Primär- und Cleanupfehler separat und meldet FAILED,
auch wenn nach einem Primärfehler der eigene Close gelingt. Cleanup im finally
betrifft nur den tatsächlich selbst erworbenen privaten Handle, kein
nachträglich rekonstruiertes Objekt. Endnachweis und Slotfreigabe bleiben
getrennt; der Prüfhost hat keinen Anwendungsslot. Der Port ist für Leergruppen
ohne jeglichen Prozesszuordnungspfad begrenzt. Kein Kill-on-close-Wirkungsbeweis
aus erfolgreichem Close ableiten.

### Tatsächlich ausgeführte datenbankfreie Prüfungen

Vor jedem tatsächlichen Gradle-Aufruf identischer Dry-Run, ausschließlich
--offline --no-daemon. Source-/Taskprüfung vor Ausführung. Ausgeführt wurden:

1. jobAdapterUnitTest --tests de.mangalib.windowsadapter.Win64BindingsTest:
   28 neue Tests, Kernel32-RawApi vollständig simuliert. JNA-jnidispatch wird
   für Struktur-/Memoryfunktionen geladen; KEINE echten Kernel32-Jobaufrufe.
   Zweimal erfolgreicher Unit-Lauf auf den jeweiligen Entwicklungsständen,
   nicht addieren. Keine JVM-/JNA-Systembibliotheksfallbacks im Testtask
   (jna.nosys=true).
2. jobAdapterDependencyReport: tatsächliche isolierte Datei-/Hashauflösung
   wie oben. Mit zuvor geprüftem gefilterten Testtask kombiniert ausgeführt.
   Die 119 alten Tests waren dabei zuerst UP-TO-DATE, NICHT als frisch gelaufen
   ausgegeben.
3. Daraufhin test --rerun mit exakt den bisherigen drei Klassen
   OwnedJobAdapterTest, SupervisorModelTest, IpcBudgetModelTest: tatsächlich
   erneut ausgeführt, keine Main-/Manual-Anwendungsstarts.
4. Erst danach jobAdapterEmptyJob nach eigenem identischen Dry-Run einmal
   ausgeführt. Graph ausschließlich compileJobAdapterJava, Resources/Classes
   und JavaExec-Prüfhost. Kein Worker-/synthetischer Kindprozess im Job.

| Klasse / finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Win64BindingsTest (jobAdapterUnitTest) | 28 | 0 | 0 | 0 |
| OwnedJobAdapterTest | 45 | 0 | 0 | 0 |
| SupervisorModelTest | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest | 39 | 0 | 0 | 0 |
| Summe der ausgewählten unterschiedlichen Unit-Fälle | **147** | **0** | **0** | **0** |

Erhaltene 119 Assertions/Quellen unverändert. Neue Fälle: LLP64-Layouts und
Flagwerte, leerer Lifecycle durch bisherigen Adapter, NULL/ungültige/fremde/
geschlossene/gefälschte Handlefähigkeiten, einmaliger Create, erfolgloses Close
ohne Wiederholung, native Fehlercodes, Set-/Queryfehler mit Puffercleanup,
falsche Rückgabelängen und Flags, Ablehnung von Quoten/Nichtleergruppen,
bounded PID-Grow/zweite unvollständige Liste, PID-Trunkierung/Duplikate/Counts,
fehlende Prozessoperationen und Ablehnung nicht unterstützter Plattformen.

Vor dem Erfolg trat neben der dokumentierten fehlenden Offline-Modulauflösung
ein Kotlin-Scriptfehler auf: java.security wurde vom java-DSL-Namen überlagert.
Expliziter MessageDigest-Import behob dies. Kein Download und kein nativer
Jobversuch in diesen fehlgeschlagenen Vorstufen. Keine Vollsuite, check/build,
Spring-/JPA-/DB-/Browser-/Scraperprüfungen ausgeführt.

### Genau eine native OS-Prüfung – verbraucht und abgeschlossen

Kennung: B_ADAPTER2_EMPTY_20261023_77808c3d.
Neue lokale Evidenz: .gradle/t02-adapter2-empty-77808c3d/consumed, trace.txt, STOP.
Keine alte Freigabe oder historischen STOP-Marker bearbeitet.
Der Prüfhost erstellt consumed per CREATE_NEW und force(true) VOR dem
ersten CreateJobObjectW. Bestehendes consumed/STOP verhindert weiteren Eintritt.
Die Dateien sind danach erhalten geblieben; KEIN zweiter Versuch gestartet.

Prüfvoraussetzungen: Java 21, JNA 5.17.0/Hashpin, Windows/Pointerbreite und
Layouts bestätigt; keine JVM-Overrides in JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS,
_JAVA_OPTIONS oder GRADLE_OPTS vorhanden. JNA-Ladepfad-/Unpack-Overrides werden
vor Probe abgewiesen, jna.nosys=true verwendet das gebündelte Dispatchmodul.
Fester kanonischer Evidenzpfad, keine externe Navigation oder Datenbank.

Gemessener Lauf:
- genau ein CreateJobObjectW(NULL,NULL): privater, nicht vererbbarer Job;
- SetInformationJobObject Klasse 9 mit 144 Byte, nur Flags 0x2000;
- Readback Klasse 9: ReturnLength 144, Flags 0x2000,
  ActiveProcessLimit=0, JobMemoryLimit=0; KEINE Quotenexperimente;
- Accounting Klasse 1 vor/nach Liste: ReturnLength jeweils 48,
  TotalProcesses=0, ActiveProcesses=0, TotalTerminatedProcesses=0;
- PID-Liste Klasse 3: Kapazität 4, ReturnLength 8, vollständige leere Liste [];
- genau ein erfolgreiches CloseHandle auf den eigenen Job;
- emptyConfirmed=true, outstandingHandles=0, outstandingBuffers=0;
- primary=none, cleanup=none, result=PASS; Gradle BUILD SUCCESSFUL.

Create/Set/vier Querys/Close, kein Prozessstart, keine Prozesszuordnung und
keine Prozessbeendigung. Der separate JavaExec-Host außerhalb des Jobs war nur
der ausdrücklich ausgeführte Prüfharness, keine Worker-JVM und kein
synthetischer Testnachfahre. Reguläre Gradle-/JUnit-JVMs sind separat davon
zu zählen; „null Workerstarts“ behauptet nicht „keine Java-Prüfhosts“.

Gemessene Create-bis-Close-/Prüfphase etwa 31,172 ms (31.172.000 ns).
Die kooperative Beobachtungs-/Closefrist betrug 15 s und wurde eingehalten.
Das ist kein Worst-Case- oder harter Echtzeitnachweis bei Kernel-/Hostblockade.
Keine Timer-/Watchdogprozesse gestartet. consumed/force(true) ist hier der
persistente Einmalversuchsschutz, kein bereits abgenommenes produktives
Crash-/Rollback-/Recoveryjournal gemäß B-MODEL-2.

Trace-SHA-256: 7d6fa9c2c4ea58b456c82fe5442dbec0cecc8ddc10195dad4c7acc7862905f4a.
consumed-SHA-256: 62456ea64c6e3dbe5b42ba5dc64cb073d1185feb505d83a6c25e933b0609983e.
STOP-SHA-256: 21e6366dc7e5430e6a0265c09b6d753890c9fbf560de29dbf2301b5e039f6228.

### Aussagekraft und nächster separat freizugebender Auftrag

Belegt ausschließlich für diese Umgebung: leere private Gruppe erstellen,
konfigurierte Kill-on-close-Eigenschaft und leere Gruppeninformationen lesen,
eigenen Handle kontrolliert schließen. Nicht belegt: Kill-on-close bei aktiven
Nachfahren, Prozess-/Commit-/CPU-Quoten, atomarer Workerstart, Supervisorcrash,
Nestedjobs/Breakaway, Prozessidentitäten laufender Gruppen oder Firefox/Gecko.
Die frühere ActiveProcessLimit=3-/Sechs-Mitglieder-Diskrepanz bleibt vollständig
offen und unverändert dokumentiert; der neue Versuch setzte keine Prozessquote.

Nächste Empfehlung: zunächst isoliert die fehlenden Start-/Attributlisten-/
Prozessidentitätsbindings samt Fakefehlern und nicht blockierender
Owner-/Callback-Lebensdauer vorbereiten, ohne Kernelstart im regulären Test.
Danach nur nach neuer Einzelfreigabe ein suspendierter synthetischer Start
mit JOB_LIST, Marker-vor-Resume-Prüfung, eigenen gehaltenen Instanzen und
unabhängig abgesichertem Cleanup. Vor diesem Prozessfall konkrete zulässige
Startzahl, Host-/Hilfsprozessbehandlung, Zeit-/Outputgrenzen und Abbruchweg
festlegen; ungeklärte ActiveProcessLimit-Quote nicht als Schutz versprechen.
Keine weiteren nativen Szenarien aus B-ADAPTER-2 eigenständig durchführen.

Keine Git-Operation, Datenbankverbindung, SQL/Migration, Produktintegration,
HTTP-Listener, echter Scraper-/Browserstart, Download oder Installation.
SSRF sowie T03/T04/T06 nicht bearbeitet. Nur neuer Experimentcode, isolierter
Buildblock, neue Evidenz und Dokumentnachträge; bestehende Nachweise erhalten.



## T02 – B-ADAPTER-3: Win64-Prozessbindungen ausschließlich mit Fakes (23.10.2026)

### Freigabe, Ergebnis und Trennung

Implementiert ist ausschließlich die isolierte Start-/Identitätsbindung mit
deterministischen Win32-Fakes. KEIN neuer Job-Object-Versuch, keine echte
CreateProcessW-/Resume-/Terminate-/Wait-Operation und keine Produktintegration.
Der B-ADAPTER-2-Leergruppenversuch bleibt verbraucht; consumed, trace, STOP und
sämtliche historischen Prototypen/Assertions wurden nicht verändert.

Neue Dateien:
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64ProcessLayouts.java
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64LaunchSpec.java
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64ProcessKernelCalls.java
- src/jobAdapterTest/java/de/mangalib/windowsadapter/Win64ProcessBindingsTest.java

Win64KernelCalls wurde nur als Basisklasse freigegeben (final entfernt) und
owned/accounting/pidList paketintern für den isolierten Prozessport sichtbar
gemacht. Der bestehende nativeEmptyJobPort bleibt bei seiner restriktiven
Leere-Job-Implementierung: keine ausführenden Prozessoperationen hinzugefügt.
KernelCalls, OwnedJobAdapter, FakeKernelCalls und sämtliche bisherigen
Modell-/Adapter-/Bindingtests unverändert; keine neue konkurrierende Admission,
Slotfreigabe, Retry-, Recovery- oder Shutdownpolitik.

build.gradle.kts erhielt genau einen Include für Win64ProcessBindingsTest im
vorhandenen jobAdapterTest-Source-Set. Die neuen Win64*-Dateien werden vom
vorhandenen isolierten Include erfasst. Keine neue Abhängigkeit, kein zusätzlicher
Task und keine Änderung von main/manualTest oder deren Paketierungsregeln.
Der erneut ausgeführte jobAdapterDependencyReport bestätigt ausschließlich
das vorhandene SHA-gepinnte jna-5.17.0.jar und leere Konfigurationsvererbung.

Zusätzlich das bereits vorhandene, NICHT neu gebaute manuelle JAR lesend geprüft:
ManualTestLauncher vorhanden; keine windowsadapter-/scrapermodel-Klassen,
kein JNA 5.17.0, kein LiveScraperGateway/MangaScraper/BandScraper und keine
Selenium-/WebDriverManager-JARs. Es enthält allerdings bereits
BOOT-INF/lib/jna-5.13.0.jar aus dem bisherigen Runtimeumfang. Deshalb KEINE
Behauptung, das heutige Manual-Artefakt sei vollständig JNA-frei. Die früher
geplante positive Runtime-Allowlist bleibt eine offene separate Härtungsarbeit.
Diese Stufe fügt dem Produkt-/Manual-Artefakt keine Adapterklasse, neue
JNA-Version oder Startverdrahtung hinzu; kein neues Artefakt gebaut.

### ABI und deklarierte Win32-Funktionen

Neue ProcessFunctions-Deklarationen: InitializeProcThreadAttributeList,
UpdateProcThreadAttribute, DeleteProcThreadAttributeList, CreateProcessW,
ResumeThread, OpenProcess, GetProcessId, QueryFullProcessImageNameW,
GetProcessTimes, GetExitCodeProcess, WaitForSingleObject, IsProcessInJob und
TerminateJobObject. Weiterverwendet werden die bisherigen Job-/Query-/Close-
Abstraktionen. Diese Deklarationen laden selbst keine DLL.

WICHTIG: Win64ProcessKernelCalls hat KEINE native Factory, keinen main-Einstieg
und kein Native.load. Sein ausführender Pfad erhält ausschließlich die explizit
injizierte ProcessRawApi; die Tests liefern eine lokale Java-Fakeimplementierung.
Die vorhandene Native-Factory des Leere-Job-Ports aktiviert diesen Pfad nicht.
ProcessBridge delegiert sämtliche deklarierten Prozessoperationen und die
bisherigen Joboperationen an explizit injizierte Bindings; error() verwendet
unmittelbar den gesicherten Thread-LastError des Jobbindings. Auch diese
vollständige Brücke wurde ausschließlich mit Fakes geprüft. Es gibt weiterhin
keinen Native.load-/Factorypfad für die Prozessfunktionen. Der kontrollierte
native Lade-/Prüfhost und die Laufzeitgrenzen sind vor einer OS-Abnahme separat
vorzubereiten. Keine Aussage, dass neue Funktionen bereits am Betriebssystem liefen.

| Struktur/Konstante | Geprüfter Win64-Vertrag |
|---|---|
| STARTUPINFOW | 104 Byte; reserved 8, flags 60, show 64, reservedPointer 72, stdin 80 |
| STARTUPINFOEXW | 112 Byte; Attributlistenpointer 104 |
| PROCESS_INFORMATION | 24 Byte; Prozess 0, Thread 8, PID 16, TID 20 |
| FILETIME | 8 Byte, zwei DWORDs, nicht Windows-C-long |
| SIZE_T / HANDLE / ULONG_PTR | 8 Byte im hier begrenzten Win64-Pfad |
| JOB_LIST | 0x2000D, Wert: eigenes Jobhandlearray mit 8 Byte |
| Startflags | CREATE_SUSPENDED 4, EXTENDED_STARTUPINFO_PRESENT 0x80000, UNICODE_ENVIRONMENT 0x400, NO_WINDOW 0x08000000 |
| OpenProcess-Rechte | nur QUERY_LIMITED_INFORMATION + SYNCHRONIZE = 0x101000 |
| Wait | WAIT_OBJECT_0 0, TIMEOUT 258, FAILED 0xffffffff; niemals INFINITE |

JNA-Memory/Strukturlayouts wurden im Test geprüft; der Dispatch-Unterbau für
Speicher/ABI ist kein realer Kernel32-Prozessversuch. Größen/Offsets stimmen
mit den vorhandenen historischen LLP64-Nachweisen überein; kein neuer SDK-
Header-/OS-/plattformübergreifender Nachweis behauptet.

### Vorbereiteter atomarer Startpfad und Ressourcenvertrag

1. Eine vertrauenswürdige feste Win64LaunchSpec gibt absolute ausführbare Datei,
   Argumente, CWD, explizite Environment-Allowlist und absolute Startdeadline vor.
   Keine HTTP-Eingabe, PATH-Suche, Shell, ProcessBuilder- oder Assign-Fallbackroute.
2. Lexikalisch eindeutige absolute Laufwerkspfade verlangen; UNC/Device/ADS,
   relative Pfade, Punktsegmente, NUL und zweideutige Endpunkte ablehnen.
   Physische Kanonisierung, Existenz, Dateirechte und vertrauter Pfadinhaber sind
   damit NICHT nachgewiesen; sie gehören vor einen späteren realen Start.
3. Windows-CRT-Quoting umfasst leere/Unicode-/Whitespaceargumente, Quotes und
   Backslashes einschließlich Schlussbackslashes. Expliziter applicationName
   plus mutable UTF-16LE-Commandline. Commandline und Environment jeweils
   höchstens 32767 UTF-16-Codeeinheiten einschließlich Terminierung.
4. Environment explizit, case-insensitiv sortiert, genau SystemRoot/TEMP/TMP,
   keine doppelten Schlüssel, doppelte NUL-Terminierung. Keine geerbten JVM-,
   Spring-, DB- oder Passwortvariablen. Kein IPC-/Standardhandle-Setup vorgezogen.
5. Attributgröße über erwarteten Fehler 122 bestimmen, höchstens 4096 Byte als
   historisches lokales Prüfmaximum; dies ist noch kein produktives OS-Budget.
   Backing-Memory vor weiteren Allokationen im bestehenden Ownerledger verbuchen.
   Initialisierte Attributliste separat verbuchen. Update JOB_LIST muss
   erfolgreich ausschließlich auf den eigenen Job zeigen, bevor Create möglich ist.
6. STARTUPINFOEX.cb=112, Attributpointer gesetzt, Standardhandlefelder null,
   SECURITY_ATTRIBUTES null, bInheritHandles=false, Flags fest inklusive suspended.
   Kein Pfad für normalen Start mit nachträglicher Zuordnung.
7. Nach tatsächlicher Rückkehr alle gelieferten Teilhandles vor Folgeprüfungen
   übernehmen, auch bei false/fehlendem Thread/Identitätsfehler/abgelaufener Frist.
   FAILED ist niemals SUSPENDED. startFailure erhält interne Operation/Fehlercode;
   bestehender Ownerbericht dokumentiert zusätzlich start-failed. Keine HTTP-Ausgabe.
8. PID aus PROCESS_INFORMATION gegen GetProcessId am gehaltenen Handle prüfen,
   Erzeugungszeit und vollständiges Image lesen, eigene Mitgliedschaft nachweisen.
   Rootimage muss dem festen Executable entsprechen. Wiederholte Identitätslesung
   darf PID, Erzeugungszeit und Image nicht wechseln.
9. Resume nur ausdrücklich über den bestehenden Owner, nach erneuter Identität/
   Membership und vor Startdeadline. Genau vorheriger Suspendcount 1 ist Erfolg;
   -1/0/>1 sind Fehler, kein zweiter Resumeversuch und kein automatisches Resume.
10. Commandline, Environment, Startup-/Process-Information, Jobhandlearray und
    Attribute bleiben bis tatsächlicher nativer Rückkehr owned. OUTSTANDING und
    eine Exception mit unbekanntem Rückkehrausgang erlauben keine Speicherfreigabe.
    Späte Rückmeldung benötigt dieselbe Speicherfähigkeit sowie anschließend
    genau Owner-Ticket und StartPermit im unveränderten OwnedJobAdapter.
    Nach Cleanupanforderung kein Resume; fremde/doppelte Rückmeldungen abgewiesen.

Die zusätzliche Frameverwaltung beschreibt ausschließlich native Ressourcen und
Aufrufzustände; der bestehende OwnedJobAdapter entscheidet weiterhin über
Cleanupbericht/Quarantäne. Ein Portbericht erteilt niemals Slotfreigabe.

### Identitäts-, Populations- und Endbelege

OpenProcess erzeugt nur eigene gehaltene Les-/Synchronisationsfähigkeiten; es
wird nicht nach Image/Name gesucht oder nach PID beendet. Numerische Handlewerte
und PIDs sind keine rekonstruierbaren Berechtigungen. Beobachtete dynamische
Mitglieder einschließlich conhost.exe werden vollständig erfasst und benötigen
denselben Identitäts-/Membershipnachweis. Kein Namensbonus und keine pauschale
Subtraktion von Hosts; historische exakte Drei-PID-Assertion bleibt unverändert.

Bounded vollständige PID-Liste zwischen zwei Accountingabfragen; höchstens ein
begrenztes Grow wie bisher, Kapazität maximal 256. Widerspruch, übergroße oder
unvollständige Liste und instabiles Accounting ergeben keinen Endbeleg.
Dieser Sandwich-Beleg ist KEINE atomare Windows-Snapshot-API.

Terminate ausschließlich über eigenen gehaltenen Jobhandle. Endbeweis weiter:
alle gehaltenen Instanzen mit unveränderter Identität signalisiert, konsistente
leere Jobliste und ActiveProcesses=0 am noch offenen Job. Erfolgreiches
Terminate oder eine bloß leere Liste reichen nicht. Signalisierter Prozesshandle
plus erfolgreicher Exitcodeabruf zählen; ein tatsächlicher Exitcode 259 ist
zulässig und darf den Wait-Beleg nicht mit STILL_ACTIVE verwechseln.

Nanosekunden-Restfrist -> begrenztes DWORD-Millisekundenbudget, abrunden statt
Verlängern, niemals INFINITE. Der unveränderte Owner hält genau die erste
Cleanupdeadline für alle Wait-/Query-/Closeoperationen fest und kontrolliert
auch die Rückkehrzeit. Keine neue Frist pro Handle, kein Sleep-/Retryloop.
Bei Fristende bleibt die Gruppe gesperrt; keine automatisch verlängerte
nachträgliche Bereinigung behaupten.

Erst nach Endbelegen: Thread/Prozess, Attribute, Backing-Memory, zuletzt Job.
Unklarer Close wird UNCERTAIN und nicht erneut versucht; unklare Attributfreigabe
pinnt den Speicher, verbleibende Handles/Memory pinnen das eigene Jobhandle.
Bereits beobachtete Unsicherheit bleibt im bestehenden Owner quarantänerelevant,
selbst wenn ein späterer Abschluss Ressourcen noch nachweislich schließen kann.

### Tatsächlich ausgeführte Prüfungen

Vor jedem tatsächlichen Gradle-Lauf identischer --dry-run mit --offline
--no-daemon; kein Netzwerk und keine neue Installation. Die erste automatische
Cachezugriffsfreigabe lief vor dem Dry-Run ab; die ausdrücklich erlaubte einmalige
Wiederholung gelang. Kein Test-/Nativeaufruf aus diesem Timeout.

Finaler isolierter Taskgraph:
jobAdapterDependencyReport, compileJobAdapterJava, processJobAdapterResources,
jobAdapterClasses, compileJobAdapterTestJava, processJobAdapterTestResources,
jobAdapterTestClasses, gefilterter jobAdapterUnitTest.
Kein jobAdapterEmptyJob, JavaExec-Probe, manualSafetyTest oder Anwendungsstart.

~~~powershell
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest' --dry-run
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest'
~~~

Alte Modelle/Adapter gesondert mit identischem Dry-Run und genau diesen Filtern:
~~~powershell
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

Dieser zweite Graph enthält nur normale Compile-/Resource-/Testaufgaben und
den gefilterten test; Main/Testkompilate waren UP-TO-DATE, test wurde durch
--rerun tatsächlich erneut ausgeführt. Keine Spring-Boot-Vollkontexte,
Datenbanktests, Vollsuite/check/build/assemble oder Browser-/DOM-Tests.
Java 21, Gradle 8.14.3, keine vorhandenen JVM-Overridevariablen.

| Klasse, finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Win64ProcessBindingsTest, neu | 61 | 0 | 0 | 0 |
| Win64BindingsTest, unverändert | 28 | 0 | 0 | 0 |
| OwnedJobAdapterTest, unverändert | 45 | 0 | 0 | 0 |
| SupervisorModelTest, unverändert | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest, unverändert | 39 | 0 | 0 | 0 |
| Unterschiedliche Fälle gesamt | **208** | **0** | **0** | **0** |

Beide finalen Gradle-Läufe BUILD SUCCESSFUL. Entwicklungszwischenstände mit
45, 55 bzw. 60 neuen Fällen waren ebenfalls grün; diese NICHT addieren.
Die bisherigen 147 Tests/Assertions wurden nicht verändert und frisch geprüft.

Neue Fälle: vollständige Brückendelegation an Fakes, ABI/Konstanten, exakte Startargumente/Environment/CWD/Handlevererbung,
Quote-/Unicode-/Slashregeln, ungültige Pfade/Environment/Größen, Atomaritäts-
voraussetzung, Sizing/Init/Updatefehler, fehlgeschlagene und partielle Create-
Ergebnisse einschließlich fehlendem Thread, kein Retry/Fallback/automatisches
Resume, Startfrist vor Vorbereitung/zwischen Vorbereitung und Create/nach Create/
vor Resume, Resume -1/0/>1, fehlende/falsche Identität/Image/Membership,
PID-Wiederverwendung vor Open sowie an gehaltenem Handle, dynamische Hosts,
Population über Kapazität/Accountingdrift/Query-/Open-/Terminatefehler,
natürliches Ende, leere Liste ohne Prozesssignal, Wait-/Exitcodefehler,
Restfrist über mehrere Waits und verspäteter Wait, unklarer Close/Delete,
ausstehende/unerwartet abgebrochene Create-Rückkehr, fremde Tickets/Gruppen/
Speicherfähigkeiten und doppelte Rückmeldungen. Ausschließlich lokale Fakes.

### Nachweisgrenzen und nächster genau einmaliger Vorschlag (NICHT ausgeführt)

Nicht nachgewiesen: reale Create-/Resume-/Terminate-/Wait-/Identitätswirkung,
physisch vertrauenswürdige EXE/CWD, Nestedjobs/Breakaway, vollständige
Ressourcenkontrolle bei blockierendem Kernelaufruf, Crash/Kill-on-close-
Wirksamkeit auf Prozesse, Quoten, IPC, Firefox/Gecko, Recovery oder SSRF.
Die historische ActiveProcessLimit=3-/Sechs-Mitglieder-Diskrepanz bleibt offen.

Der Prozessport und der unveränderte Owner sind derzeit synchronisiert.
Ein echter blockierender DLL-Aufruf könnte den Monitor halten. Fake-PENDING
belegt nur die Ressourcen-/Callbackregeln, KEINE nicht blockierende native
Ausführung und keine harte Timeoutgarantie. Vor echtem Start müssen die
native Ladeverdrahtung, Lock-/Rückmeldegrenzen und unabhängig bedienbare Cleanuphoheit
separat geprüft/freigegeben werden. Kein Future.cancel/Interrupt als Endbeleg.

Vorschlag für GENAU EINEN später separat freizugebenden Versuch:
1. Neue feste Kennung/neuer Evidenzordner; consumed atomar CREATE_NEW und
   force(true) VOR jedem Create. Alte STOPs unverändert; vorhandenes consumed/
   STOP oder unbestätigtes altes Cleanup sperrt den Eintritt, keine Wiederholung.
2. Statisch geprüfte native Ladeverdrahtung/Prüfhost, Java 21/JNA-Hash/ABI und feste
   physisch kanonisierte vertrauenswürdige EXE/Klasse/CWD/Environment.
   Eigene neue private Gruppe mit Kill-on-close-Readback, ohne Quotenexperimente.
   Einziger zulässiger expliziter Start: genau ein synthetischer Rootprozess,
   keine Worker-Nachfahren und kein Browser/Netzwerk/DB.
3. Vor Start unabhängig bedienbare Cleanuphoheit im Prüfhost sichern, die
   ausschließlich dessen eigenen Jobhandle kennt (ggf. explizit gezählter eigener
   DuplicateHandle). Start-/Beobachtungsbudget 5 s und EINE globale Cleanupfrist
   5 s als zunächst kooperative Prüfbudgets, keine harte Kernelhängerzusage.
   Nicht gewährleistete unabhängige Bedienbarkeit => STOP vor Create.
4. Genau ein CreateProcessW mit JOB_LIST/CREATE_SUSPENDED/Extended/Unicode/
   NO_WINDOW, inherit=false. In diesem kleinsten Versuch KEIN ResumeThread:
   Marker vor und nach kurzer begrenzter Beobachtung abwesend; gespeicherte
   PID + gehaltenes Handle + Erzeugungszeit + Rootimage + Mitgliedschaft prüfen.
5. Vollständige Liste/Accounting und dynamische Zusatzmitglieder erfassen,
   Kapazität höchstens 8 als Beobachtungsgrenze, KEINE präventive Prozessquote.
   Erwartung Root allein; unerwartete Population/Identität/Frist/Tracefehler
   bedeutet STOP, keine Toleranz über Namen oder Abschwächung alter Assertions.
6. Suspendierten Root über ausschließlich eigene Gruppe terminieren, alle
   erfassten eigenen Instanzen identifizieren/Signale prüfen, leere Liste und
   ActiveProcesses=0 am offenen Job bestätigen. Danach Handle/Attribute/Memory/
   Job (einschließlich gezähltem Cleanup-Duplikat) schließen, Bilanz null prüfen.
   Bei unbestätigtem Ausgang STOP/Quarantäne/Evidenz erhalten, kein neuer Versuch
   und keine PID-/Namensbeendigung als Ersatz.
7. Trace begrenzt und persistiert (z.B. 256 Ereignisse/256 KiB), Primär- und
   Cleanupfehler getrennt, consumed und finaler PASS/STOP bleiben erhalten.
   Kein automatischer Übergang zu Resume-/Baum-/Crash-/Firefoxversuchen.

Diese Planung ist KEINE Ausführungsfreigabe. Prozess-DLL-Ladeverdrahtung,
Einmal-Prüfhost und unabhängig ausführender Cleanup sind in B-ADAPTER-3 nicht
implementiert oder gestartet.
Keine Git-Operationen (auch kein status/diff/Branchlesen), DB/SQL/Migration,
Spring-/HTTP-/Worker-/Browser-/Scraperstarts, Downloads oder Installationen.
Reguläre Gradle-/JUnit-JVMs sind Prüfharnessprozesse, keine synthetischen Worker.


## T02 – B-ADAPTER-4: Ladeverdrahtung vorbereitet, OS-Versuch gesperrt (24.10.2026)

### Umfang und Ergebnis

Ausschließlich statische Prüfung, isolierte Loader-/Adaptervorbereitung und
deterministische Fakes. Kein neues Job Object, keine echte Prozessanlage,
keine reale Kernel32-Ladeoperation und kein nativer Resume-/Terminate-/Wait-
Versuch. JNA-Struktur-/Memoryprüfungen verwenden wie bisher den Dispatch-
Unterbau; dies ist kein Kernel32-Prozessnachweis.

Neue Dateien:
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64NativeBindings.java
- src/jobAdapterTest/java/de/mangalib/windowsadapter/Win64NativeBindingsTest.java

build.gradle.kts erhielt nur einen zusätzlichen Include der neuen Testklasse
im bestehenden jobAdapterTest. Kein neuer Task, Startskript, JavaExec-Prüfhost,
synthetisches Programm oder Cleanup-Hilfsprozess. AGENTS, main/manualTest,
Services, Frontend, DB-Guards, KernelCalls, OwnedJobAdapter und sämtliche
bisherigen Binding-/Modell-/Prototypquellen und Assertions unverändert.
Historische STOPs, consumed/trace und Einmalfreigaben nicht geändert.

**Teilnachweis, kein startfähiger OS-Versuch:** Der Ladepfad ist technisch
vorbereitet und mit Fakes geprüft. Die zentrale Anforderung eines vorab
nachgewiesenen unabhängigen, begrenzten Cleanup ist NICHT erfüllt. Daher kein
Prozess-Prüfhost als unsichere Ersatzlösung implementiert und keine Freigabe
eines suspendierten Starts aus diesem Ergebnis ableiten.

### Expliziter Ladevertrag

- Win64NativeBindings hat keine main-Methode und keine DLL-/Prozessoperation im
  statischen Initialisierer. Auch Konstruktion von JnaLoader lädt keine DLL.
  prepare erhält explizit Hostfakten und Loader; die Tests liefern ausschließlich
  lokale Loader-/Module-/Win32-Fakes.
- Host.detect prüft zunächst OS/Architektur, Java, SystemRoot und JNA-Overrides
  VOR dem Zugriff auf die injizierte ABI-Erhebung bzw. Native-Klasseninitialisierung.
  Diese Reihenfolge vermeidet, dass ein abgelehnter Bootpfad bereits beim
  Abfragen von Native.POINTER_SIZE den Dispatch von dort laden könnte.
- Unterstützt wird für diesen isolierten Prüfhost Windows x64 (amd64/x86_64),
  Java 21, Pointer 8 / Windows LONG 4 / WCHAR 2 und JNA-Core exakt 5.17.0.
  ARM64/32-Bit/abweichende Plattformen oder Versionen werden vor DLL-Laden abgewiesen.
- jna.nosys muss true sein; gesetzte Boot-/Library-/Platform-Library-/Temp-/
  NoUnpack-/NoClasspath-Overrides werden auch mit leerem Wert abgewiesen.
  Der reale Loader erhebt die aktuellen Fakten selbst nochmals und prüft die
  vorgegebenen Optionen. Ein synthetisches Hostobjekt allein schaltet ihn nicht frei.
- Für diesen ausdrücklich begrenzten bisherigen C:\Windows-Experimenthost ist
  die DLL fest C:\Windows\System32\kernel32.dll. Andere SystemRoot-Werte werden
  abgewiesen statt eine DLL im vom Environment bezeichneten Ersatzverzeichnis
  zu akzeptieren. Kein Bare-name-/CWD-/PATH-/UNC-Fallback.
  Vor realem Laden muss sie eine reguläre, physisch kanonische Datei sein.
  Eine anders installierte Windows-Umgebung benötigt eine neue bewusste Anpassung;
  keine automatische Pfadumleitung. Windows-/Dateirechte/ACL-Vertrauen werden
  dadurch nicht neu OS-seitig bewiesen.
- Calling Convention explizit ALT_CONVENTION/StdCallLibrary mit vorhandenem
  Win64-ABI. Derselbe explizite ClassLoader und dieselben Optionen für
  NativeLibrary.getInstance und Native.load; keine neue Symbol-/Unicode-Aliasroute.
  Bestehende WString-/Pointer-/DWORD-/SIZE_T-Deklarationen werden wiederverwendet.
- Alle 18 deklarierten Exporte werden vollständig vor Proxyfreigabe aufgelöst:
  die fünf vorhandenen Jobfunktionen und die 13 Prozessfunktionen aus B-ADAPTER-3.
  Auflösen ruft CreateProcessW, CreateJobObjectW, Resume oder andere Job-/
  Prozessfunktionen nicht auf.
- Danach vorhandene ProcessBridge plus JobCalls für OwnedJobAdapter verwenden.
  JobCalls hält das Modul stark referenziert. JNAs gesicherten Thread-LastError
  unmittelbar lesen; GetLastError nicht als zusätzliche DLL-Funktion aufrufen.
- Bei Lade-/Symbol-/Bindefehler: neutraler LoadFailure mit Phase und interner
  Originalursache; erworbenes Modul einmal schließen. Cleanupfehler als
  suppressed behalten, kein Retry und keine Erfolgsrückgabe.
  Fehler bei der Dispatch-/ABI-Initialisierung ebenfalls mit eigener Phase erhalten.
- Erfolgreich vorbereitete DLL absichtlich für die Lebenszeit des isolierten
  Prüfhosts behalten; keine öffentliche Unloadfunktion unter eventuell noch
  laufenden Aufrufen. Diese Host-Runtime-Lebensdauer ist kein Job-Handle-Endbeleg.
  Kein behaupteter kompletter nativer Host-Lifecycle oder Crashnachweis.

Die tatsächlichen DLL-/Export-/Aufrufverträge wurden NICHT am OS ausgeführt.
prepare(Host, FakeLoader) prüft Ablauf/ABI/Fehlerpfade. JnaLoader.open wurde in
keinem Test aufgerufen; dessen physische Pfadprüfung, echte DLL-Auflösung und
nativer LastError bleiben spätere OS-Abnahmepunkte. Die bisherigen bewährten
B-ADAPTER-2-Bindings und deren verbrauchte Probe blieben unverändert.

### Funktionsverbindung und bestätigte Cleanupblocker

Ein neuer Fake-Ende-zu-Ende-Fall durchläuft Loader -> ProcessBridge ->
Win64ProcessKernelCalls -> bestehenden OwnedJobAdapter: private Gruppe,
Kill-on-close setzen/readback, atomarer suspendierter Fake-Start, Identität und
Membership, eigene Gruppenterminierung, gehaltene Prozesssignale, leere Liste/
Accounting und Ressourcenabschluss. Null Resume. Das belegt die tatsächliche
Java-Verbindung, nicht deren OS-Wirkung.

Nachgewiesene Einschränkungen der vorhandenen Implementierung:
- OwnedJobAdapter.start/cleanup und Win64ProcessKernelCalls.startSuspended/
  terminate/close sind synchronisiert und halten ihre jeweiligen Monitore über
  die Aufrufe hinweg. Ein hängender Start kann die normale Cleanupanforderung
  vor deren Eintritt blockieren; ein hängendes Terminate/Wait/Close kehrt durch
  die Deadlineprüfung davor/danach nicht von selbst zurück.
- Fake-PENDING und verspätete Rückmeldung sichern Eigentum/Quarantäne, sind aber
  kein echter asynchroner Native-Executor und keine OS-Cancellation.
- Es gibt keinen unabhängig vom Supervisor bedienbaren Kontrollhost, kein dafür
  ausgehandeltes privates Jobhandle und keinen abgenommenen Watchdog-Endnachweis.
- Kill-on-close allein schützt nicht vor einem lebenden, festhängenden Owner mit
  noch offenem Jobhandle. Ein weiterer Handle macht sein Schließen auch nicht
  zum letzten Close. Suspendierte Prozesse verbrauchen keine verlässliche
  CPUzeit für ein als Wallclock-Ersatz missverstandenes CPU-Joblimit.
- Eine Deadlineüberschreitung beweist fehlenden rechtzeitigen Abschluss, erzwingt
  jedoch keine begrenzte Rückkehr aus einem blockierenden Kernelaufruf.
  Bei unbekanntem Abschluss bleiben Ressourcen/Slot gesperrt; kein Resume,
  PID-/Namenskill oder neuer Auftrag als vermeintliche Reparatur.

**P0-Blocker für jede OS-Freigabe:** unabhängiger, vor Create geprüfter Cleanup
mit eigener Ausführungshoheit und sicherer Kontrolle ausschließlich des privaten
Jobs. Innerhalb dieser rein statischen/Fake-Stufe kann seine reale Wirkung nicht
zuverlässig bereitgestellt und nachgewiesen werden. Bewusst keine neue Thread-/
Helper-/IPC-/Recoveryarchitektur als ungeprüfte Zusage eingebaut.

Vor einer Umsetzung separat entscheiden und spezifizieren: Reichweite der
Unabhängigkeit (gegen blockierten Thread/Monitor, ganze JVM oder Hostabsturz),
Jobhandle-Übergabe/Minimalrechte/Eigentum, rechtzeitige Bereitschaft, Beendigung
bei ausstehendem Create und späten Rückmeldungen, End-/Ressourcenbelege sowie
Abschluss des Kontrollhosts selbst. Ein externer begrenzter Prüf-Kontrollhost
ist eine mögliche Richtung, keine bereits implementierte oder geprüfte Lösung.
Kein dauerhaft laufender Dienst/Broker/Datenbank notwendig oder vorgesehen.
Keine pauschale harte Kernel-/OS-Hängergrenze aus einem Java-Timer ableiten.

### JNA- und Artefaktprüfung ohne Paketierungsänderung

jobAdapterDependencyReport erneut offline erfolgreich: ausschließlich vorhandenes
jna-5.17.0.jar, SHA-256
b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0,
keine Konfigurationsvererbung in main/manualTest und kein jna-platform.
Kein Upgrade, Download oder neue Dependency.

Lokale Cache-POMs belegen den bestehenden Pfad:
WebDriverManager 5.7.0 -> docker-java-transport-httpclient5 3.3.5 -> JNA 5.13.0.
Auch docker-java-transport 3.3.5 nennt JNA 5.13.0 dort lediglich als provided;
die direkte normale Abhängigkeit des HTTP-Clienttransports genügt für den
Runtimeeintrag. Keine Netzwerk-/Mavenabfrage dafür durchgeführt.

Das vorhandene manual-test-JAR enthält die Docker-Java-JARs 3.3.5 und
BOOT-INF/lib/jna-5.13.0.jar. Grund: manualBootJar filtert aus dem allgemeinen
runtimeClasspath nur Selenium/WebDriverManager/DevTools, nicht deren sämtliche
Transitiven. Das ist der bereits vorhandene Paketierungsrest, kein neuer
B-ADAPTER-3-Eintrag. Keine Bereinigung oder Änderung der bisherigen Filter.

Adapter-/Modellklassen, neue JNA 5.17.0 und LiveScraperGateway/MangaScraper/
BandScraper fehlen im vorhandenen Manual-Archiv weiterhin. Positive Runtime-
Allowlist bleibt separat offen. Das Archiv wurde nicht neu gebaut und blieb
gegenüber dem Eingangs-Hash unverändert. Reguläres Anwendungsarchiv nicht neu
gebaut; dessen Grenzaussage beruht auf den unveränderten main/manual-Source-Set-/
Paketierungsregeln, nicht auf einer frischen vollständigen Paketierungsabnahme.
B-ADAPTER-3 änderte dort keine Dependency oder Classpathvererbung; diese Stufe
ändert ebenfalls ausschließlich einen isolierten Testinclude.
Keine rückwirkende Dependency-Auflösung vor B-ADAPTER-3 behaupten.

### Tatsächlich ausgeführte Tests

Vor JEDEM echten Gradle-Lauf identischer --dry-run; ausschließlich --offline
--no-daemon und Java 21. Keine JVM-Overridevariablen vorhanden.
Isolierter Graph: jobAdapterDependencyReport, compileJobAdapterJava,
processJobAdapterResources, jobAdapterClasses, compileJobAdapterTestJava,
processJobAdapterTestResources, jobAdapterTestClasses, jobAdapterUnitTest.
Kein JavaExec/jobAdapterEmptyJob/manualSafetyTest/check/assemble/Boot-Start.

~~~powershell
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest' --tests 'de.mangalib.windowsadapter.Win64NativeBindingsTest' --dry-run
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest' --tests 'de.mangalib.windowsadapter.Win64NativeBindingsTest'
~~~

Alte Modelle/Adapter ebenfalls erneut nach identischem Dry-Run ausgeführt:
~~~powershell
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

Zweiter Graph nur normale Compile-/Resource-/Testaufgaben mit genau diesen drei
datenbankfreien Klassen; Kompilate/Ressourcen UP-TO-DATE, gefiltertes test durch
--rerun tatsächlich ausgeführt. Keine Spring-/JPA-/Guard-/Integrationsvollsuite.

| Klasse, finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Win64NativeBindingsTest, neu | 44 | 0 | 0 | 0 |
| Win64ProcessBindingsTest, unverändert | 61 | 0 | 0 | 0 |
| Win64BindingsTest, unverändert | 28 | 0 | 0 | 0 |
| OwnedJobAdapterTest, unverändert | 45 | 0 | 0 | 0 |
| SupervisorModelTest, unverändert | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest, unverändert | 39 | 0 | 0 | 0 |
| Unterschiedliche Fälle gesamt | **252** | **0** | **0** | **0** |

Beide finalen Läufe BUILD SUCCESSFUL. Zwischenstände mit 42 neuen Fällen
ebenfalls grün, nicht addieren. Sämtliche bisherigen 208 Fälle/Assertions
unverändert und frisch ausgeführt. Die rein lesende XML-Auswertung hatte einmal
einen PowerShell-Pipe-Syntaxfehler; korrigiert, anschließend finale Berichte
ausgelesen. Kein Testfehlschlag oder nativer Aufruf daraus.

Neue Fälle umfassen Klasseninitialisierung ohne Laden, Plattform/Architektur/
ABI/Java/JNA/SystemRoot, Overrideablehnung vor Dispatch, Dispatchladefehler,
vollständige Exportprüfung, Lade-/Symbol-/Proxyfehler und Unsicherheit beim
Modulabschluss, neutrale Fehler samt Ursachen, gespeicherten LastError,
suspendierten Fake-Lifecycle ohne Resume, partielle Initialisierung,
Identitäts-/Handlefehler, fehlende Endsignale trotz Terminateerfolg, gemeinsame
Cleanupdeadline, späte/fremde Rückmeldung und unklaren Close ohne Retry.
Blockierendes Cleanup wird über ausbleibende Bestätigung/Rückkehrereignisse
modelliert; keine echten blockierenden Aufrufe/Threads oder Hängerversuche.

### Bedingungen des späteren genau einmaligen suspendierten Versuchs

Der B-ADAPTER-3-Vorschlag bleibt ein Plan. Aktuell insbesondere NICHT ausführbar.
Erst nach separater Freigabe und geprüftem unabhängigen Cleanup:
1. Neue Kennung/neuer Evidenzordner, consumed mit CREATE_NEW/force(true) vor
   jedem Create, historische STOPs unverändert; kein Retry nach Verbrauch.
2. Fester separater Prüfhost mit geprüfter nativer Ladeverdrahtung, Java/JNA/ABI,
   vertrauenswürdigen physischen EXE-/Klassen-/CWD-Pfaden und fester Umgebung.
   Keine unsicheren JVM-/JNA-Overrides, kein Spring/JDBC/Netzwerk/Browser.
3. Private Gruppe mit Kill-on-close/Breakaway-aus-Readback; ausschließlich
   eigener Handle. Unabhängiger Kontrollpfad muss VOR dem synthetischen Start
   betriebsbereit und seine Sicherheits-/Cleanupwirkung nachgewiesen sein.
4. Genau ein expliziter synthetischer Rootstart per JOB_LIST/SUSPENDED/Extended/
   Unicode/NO_WINDOW, inherit=false. KEIN Resume, keine Worker-/Kind-/Enkelstarts.
   Vorher keinen Marker, nach Start unveränderte Markerfreiheit, gehaltene
   PID-/Image-/Erzeugungszeitidentität und eigene Membership nachweisen.
5. Vollständige Population/Accounting; dynamische Helfer nicht über Namen
   ignorieren. Root allein ist Erwartung, unerwartete Population => STOP und
   eigener Cleanup. Kapazität 8 nur Beobachtungsgrenze, keine Quotenbehauptung.
   Historische exakte Drei-PID-Assertion nicht verändern.
6. Eine gemeinsame vorab bestätigte Cleanupdeadline, alle Endsignale plus
   ActiveProcesses=0 und vollständige leere Jobliste am offenen Job, danach
   sämtliche eigenen Handles/Attribute/Memory abschließen. Der alte Kandidat
   5 s Start/5 s Cleanup ist nur ein kooperatives Einzelversuchsbudget und vor
   Freigabe zu bestätigen, keine neue produktive Frist und keine Hängergarantie.
7. Primär-/Cleanupfehler getrennt, bounded persistente Evidenz; PASS nur nach
   vollständigem End- und Ressourcenbeleg. Sonst STOP/Quarantäne, keine
   Wiederholung, kein Resume und keine fremden PID-/Namens-/Jobaktionen.

Kein OS-Versuch aus diesem Auftrag. Native Quote/ActiveProcessLimit-Diskrepanz,
Nestedjobs, Crash/Kill-on-close, IPC/Recovery, Firefox und SSRF bleiben offen.
Keine Produkt-/Manual-/Gateway-/Workerintegration, DB-Verbindung/SQL/Migration,
Anwendungs-/HTTP-/Browser-/Live-Scraperstarts, Downloads oder Installationen.
Keine Git-Operationen, auch kein status/diff/Branchlesen. Nur reguläre
Gradle-/JUnit-Prüfharness-JVMs liefen; keine synthetischen Prozesse.
T03/T04/T06 nicht bearbeitet; fachliche Prioritäten unverändert.


## T02 – B-ADAPTER-5: monitorfreier Kontrollpfad auf Komponentenebene (24.10.2026)

### Ergebnis und Grenzen

Umgesetzt ausschließlich im isolierten Adapter: kurze Zustandsreservierungen,
außerhalb der Monitore ausgeführte Kernelaufrufe, jobgebundene CleanupControl-
Berechtigung, Nutzungstoken für Jobhandles und deterministische Parallelitäts-
tests. **278 unterschiedliche Unit-Fälle bestanden, 0 Fehler, 0 übersprungen**;
sämtliche bisherigen 252 Tests/Assertions unverändert und erneut ausgeführt.

Der Java-Monitorblocker aus B-ADAPTER-4 ist für den getrennten Kontrollpfad
beseitigt. Eine blockierte reguläre Fake-Operation verhindert weder Annahme/
Beobachtung einer Cleanupanforderung noch Eintritt in den getrennten
Terminierungspfad. Das ist KEIN OS-Nachweis begrenzter Terminierung, keine
Absturzgarantie und kein produktiver Supervisor.

**Der erste suspendierte OS-Versuch bleibt ausdrücklich GESPERRT.**
Native Nebenläufigkeit, späte Create-Ergebnisse, ein selbst blockierender
Terminierungsaufruf sowie JVM-/Hostausfälle sind damit nicht sicher begrenzt.
Kein neuer Prüfhost, externer Watchdog, Worker, Starttask oder Prozessversuch.

### Nachgewiesene Ausgangsursache und betroffene Pfade

Bisher hielten OwnedJobAdapter.initialize/start/inspect/resume/cleanup und die
Win64-Ports ihre Monitore über delegierte Aufrufe. Das betraf auch indirekte
Aufrufe aus configure/readLimits, Accounting/PID-Queries, member/identity,
GetProcessTimes/Imageabfrage, Wait, Terminate, Close und Erwerbscallbacks.
Selbst ein separater Java-Cleanup-Aufrufer konnte am selben Monitor hängen.
Deadlinechecks vor/nach dem Aufruf beendeten weder den Aufruf noch diesen
Monitorbesitz. Kill-on-close half nicht bei weiterhin offenem Jobhandle.

Die historischen Prototypen arbeiten ebenfalls synchron; deren finally-
Handlefreigaben und Einzelwaits wurden NICHT als sichere neue Lifecyclelogik
übernommen. Historische Drei-PID-Assertion, STOPs und Einmalfreigaben unverändert.

### Kurze Locks und klare Reservierungen

OwnedJobAdapter:
- stateLock schützt ausschließlich Operationsreservierung, Auftragsticket,
  Cleanupanforderung/erste Deadline, Kontrollphase, Callback-Erwerb und Snapshots.
  Kein Kernelaufruf und keine blockierende Kontrolloperation unter diesem Lock.
- Höchstens eine reguläre Adapteroperation; konkurrierende reguläre Anfragen
  werden kontrolliert abgewiesen statt eine Warteschlange aufzubauen.
  Der Zustand bleibt während ihres Aufrufs reserviert, der Monitor bleibt frei.
- cleanup nimmt die Anforderung und erste absolute Deadline kurz auf. Ist eine
  reguläre Operation aktiv, bedient es den separaten Kontrollpfad. Ist der
  Kontrollaufruf selbst aktiv, können weitere Aufrufer weiterhin Fortschritt und
  unbestätigten Bericht erhalten, aber keinen zweiten nativen Kill/Close auslösen.
- requestControlCleanup ist der ausdrücklich separat bedienbare Kontroll-
  einstieg, auch für einen bereits zurückgemeldeten synthetischen PENDING-Fall.
  Der Aufrufer stellt die unabhängige Ausführungshoheit; kein eingebauter
  Executor, Hintergrunddienst, Watchdog oder automatischer Scraper-Retry.
- Die bisherige cleanup-Behandlung eines zurückgegebenen PENDING meldet weiter
  unbekannten Abschluss und hält Ressourcen fest; sie ist kein automatischer
  Native-Executor. Ein künftiger Supervisor muss für diesen Zustand explizit
  den Kontrollpfad bedienen. Die bisherigen PENDING-Assertions bleiben erhalten.
- startReturned benötigt weiterhin exakt Owner und StartPermit. Während einer
  anderen Operation/Kontrollausführung wird die Übernahme abgewiesen; der
  Zusteller muss das Ergebnis weiterhin halten und nach deren Rückkehr gezielt
  zustellen. Das ist keine erneute Prozessanlage und kein neuer Scraperauftrag.
- Späte Prozess-/Thread-Erwerbscallbacks bleiben im eigenen Ledger. Nach
  Cleanupanforderung kein Resume/Neustart; fremde Tickets/Permits/Scopes bleiben
  ohne Ressourcenwirkung. Ein abgeschlossener Owner wird nicht wiederverwendet.

Win64ProcessKernelCalls:
- Flight reserviert einen regulären Aufruf kurz unter dem Registrylock und
  gibt diesen vor Native-/Callbackarbeit frei. Reentrante eigene Hilfsaufrufe
  sind möglich; ein konkurrierender regulärer Aufruf scheitert sofort.
  Kontrollterminierung verwendet diese reguläre Reservierung ausdrücklich NICHT.
- Ein belegtes Flight ist keine wartende Sperre des Kontrollpfads. Es verhindert
  allerdings absichtlich unsichere reguläre Freigabe während eines Aufrufs.
  Ein verschachtelter Close wird ebenfalls abgewiesen.
- Frame/Teilhandles und Commandline-/Environment-/Attributpuffer bleiben bis
  tatsächlicher Rückkehr owned. PENDING oder unbekannter Aufrufausgang ist kein
  Freigabebeleg. Nach Stop während nachgelagerter Identitätsprüfung wird auch
  ein suspendierter Start nicht nachträglich als erfolgreicher Start gemeldet.

Win64KernelCalls:
- Registryzustand und Nutzungscounter kurz sperren, Aufruf außerhalb des Locks.
  configure/readLimits/observe und Prozessstart/Abfragen halten JobUse-Token.
- Close reserviert UNCERTAIN vor dem nativen Close, und nur bei Nutzungscounter
  null. Ein fehlgeschlagener/unklarer Close wird nie auf derselben Fähigkeit
  wiederholt; neue Nutzungen einer geschlossenen/unklaren Fähigkeit abgewiesen.
- Audit-/Pufferzähler nur unter kurzen Registrylocks aktualisieren. Der noch
  vorhandene private Buffer.close-Monitor schützt ausschließlich den jeweiligen
  lokalen Memory-Puffer, nicht einen vom Job-Kontrollpfad benötigten Monitor.
  JNA-/Kernelinterne Synchronisation ist damit nicht OS-seitig untersucht.

| Potentiell blockierender Abschnitt | Neue Absicherung | Grenze |
|---|---|---|
| CreateJobObjectW vor Handle-Rückkehr | Ownerreservierung ohne Monitor; Anforderung/Snapshot bedienbar | Noch kein bekannter Job: keine Handle-/PIDsuche, Quarantäne bis tatsächlicher Rückkehr; noch kein Prozessstart |
| Set/Readback/Accounting/PID-Query | JobUse, kurze Registrylocks, Kontrollberechtigung schon vor configure/readLimits | Daten-/Schema-/OS-Wirkung nur gefakt |
| Attributaufbau/CreateProcessW | Flight/JobUse, Puffer owned, Stoppsignal unabhängig | Laufender Create nicht per Interrupt/Cancel als beendet betrachten |
| Membership/Identität/Wait | Flight, unveränderliche gehaltene Identität, kein paralleler Close | Abschluss dieser Nutzung muss für vollständigen Ressourcenabschluss zurückkehren |
| Kontroll-Terminate | Separater Aufruf, eigener Nutzungscounter, kein regulärer Monitor/Flight | Kann selbst im Kernel/JNI blockieren |
| Thread-/Prozess-/Attribut-/Memoryclose | Nur nach Endbelegen, reguläre Einzelreservierung | Unklarer Close bleibt quarantänerelevant |
| Jobclose | Erst nach allen anderen Ressourcen und Kontrolltoken, Nutzungscounter null | Kein Kontrollaufruf auf schon schließendem/geschlossenem Handle |

### Kontrollberechtigung und Handlelebensdauer

KernelCalls.CleanupControl wird nach Erwerb/Verbuchen des privaten Jobs und VOR
configure/readLimits sowie gefährlicher Startvorbereitung bereitgestellt.
Die native Prozesskomponente erlaubt genau eine solche Berechtigung je Port.
Unmittelbar vor Create wird eine fehlende Kontrollreservierung abgewiesen;
eine freigegebene Reservierung kann weder neu vorbereitet noch zum Start
verwendet werden. release setzt ebenfalls den lokalen endgültigen Startstopp.
Alte reine Modellports und der restriktive Leere-Job-Port liefern standardmäßig
keinen Kontrollpfad; das ist keine heimliche neue Nativefreigabe dieser Ports.

Die Berechtigung hält einen dauerhaften JobUse auf exakt dem eigenen privaten
Jobhandle. Jeder laufende Kontrollaufruf hält zusätzlich einen JobUse.
Es wird KEIN neues OS-Handle dupliziert, kein PID-/Namensscan eingeführt und
kein numerischer Handlewert als Berechtigung angenommen. Der Counter bildet
Java-Nutzungen desselben offenen OS-Handles ab; er ist kein OS-Referenzzähler
und kein nachgewiesener DuplicateHandle-Vertrag.

Getrennte OS-Duplikate werden für diesen In-JVM-Komponentenfall nicht benötigt,
weil kein CloseHandle erlaubt ist, solange irgendeine Nutzung/Reservierung
offen ist. Ein externer Kontrollhost würde dagegen eine gesondert abgesicherte
Handleübertragung/-duplizierung und deren eigene Schließregeln benötigen.
Diese Fähigkeit wurde weder deklariert, geladen noch ausprobiert.

cancelStarts setzt nur lokal und nicht blockierend ein Stoppsignal, auch wenn
kein natives Zeitbudget mehr übrig ist. Noch nicht eingetretene Starts/Resume-
Aufrufe werden abgewehrt. Ein bereits in ResumeThread eingetretener Aufruf kann
nicht zurückgeholt werden: seine späte Rückkehr meldet keinen Wiederanlauf/
Erfolg und erlaubt keinen zweiten Resume. Der Kontrollpfad muss dann weiterhin
das vollständige eigene Gruppenende nachweisen.

Terminierung wird unter Ownerlock genau einmal reserviert, anschließend ohne
diesen Lock ausgeführt. release löst nur den lokalen dauerhaften Nutzungstoken,
nicht das OS-Handle. Ein laufender Kontrollaufruf verbietet release; dessen
eigener JobUse schützt zusätzlich vor überlappendem Close. Keine Ressource
schließen, um einen blockierten Aufruf vermeintlich zu befreien.

### Nachweiszustände, 15-Sekunden-Kandidat und späte Ergebnisse

CleanupProgress unterscheidet Anforderung, noch ausstehende Operation,
Kontrollphase READY/RUNNING/SUCCEEDED/FAILED/OVERDUE/UNAVAILABLE, nachgewiesenes
Gruppenende, Ressourcenabschluss und Quarantäne. Es ist Diagnose, keine
Admission-/Slotfreigabeberechtigung.

SUCCEEDED bedeutet nur erfolgreiche Rückkehr der Terminierungsanforderung.
Es ersetzt weder gehaltene Prozesssignale noch vollständige leere PID-Liste,
konsistentes Accounting am offenen Job oder Ressourcenabschluss. OVERDUE ist
keine erzwungene Rückkehr; FAILED/fehlende Rückkehr bleiben unbekannt/gesperrt.

Die erste absolute Cleanupdeadline ist unveränderlich. Der spätere Supervisor
soll den bestehenden Kandidaten tc + 15 s vorgeben, mit konsistenter monotoner
Nanosekundenuhr im Win64-Port. Alte reine Modelltests verwenden weiterhin ihre
synthetischen konsistenten Einheiten. Neuer Test springt ohne reales Warten
genau über den 15-Sekunden-Kandidaten und weist nach, dass ein nachfolgend
größerer Deadlinewert weder neue Terminierung noch Ressourcenfreigabe bewirkt.
Waits/Queries/Closes verbrauchen dieselbe Restfrist; keine neue Graceperiod.

Solange ein regulärer Aufruf noch läuft, bleiben dessen Handles/Puffer owned.
Kein Close anstelle eines Endbelegs. Liefert ein alter Start nach bereits
erfolgreicher Kontrollterminierung noch einen Prozess, wird er nicht fortgesetzt.
Eine weiterhin nichtleere Gruppe verhindert Abschluss; es gibt keinen zweiten
automatischen Kill als vermeintliche Reparatur und keinen neuen Auftrag.

Vor Ressourcenfreigabe wird die Freigabephase atomar reserviert: kein neuer
Kontrollaufruf darf anschließend mit Close konkurrieren. Solange ein bereits
reservierter Kontrollaufruf läuft, werden auch bei bewiesenem Gruppenende keine
Handles freigegeben. Späterer expliziter eigener Abschluss unter DERSELBEN Frist
ist nur bei vollständigen Belegen möglich; bereits beobachtete Unsicherheit
bleibt quarantänerelevant. Primär- und Kontrollfehler werden gemeinsam erhalten.
Geschlossene/unklare Handles werden nicht erneut angefasst.

Die vier Stufen bleiben getrennt:
1. Cleanup angefordert.
2. Kontrolloperation tatsächlich zurückgekehrt.
3. Gruppe nachweislich beendet.
4. Sämtliche Ressourcen/Nutzungen bestätigt abgeschlossen.

Erst Stufe 4 kann zusammen mit ALLEN übrigen B-SPEC-1-C-Bedingungen relevant
werden. IPC-/Writer-/Reader-/Recoverybarrieren und produktive Slotverwaltung
werden hier weiterhin nicht implementiert oder ersetzt.

### Deterministische Tests und tatsächliche Ausführung

Neue Win64ConcurrencyTest verwendet CountDownLatch statt Sleeps/Zufallsrennen.
Gateeintritt ist bestätigt, bevor der konkurrierende Aufruf beginnt. Der Gate-
Wait selbst ist auf 6 s begrenzt, Ereignis-/Future-Waits auf 3 s.
finally/AutoCloseable löst ALLE Gates, shutdown/awaitTermination und notfalls
shutdownNow betreffen nur die kontrollierten Testthreads; alle Testthreads sind
zusätzlich daemon. Kein nicht abbrechbarer Fake-Thread und kein echter nativer
Hängerversuch. Interrupt/Futuretimeout wird NICHT als Ressourcenendbeleg benutzt.

Der vorhandene Raw-Fake aus Win64ProcessBindingsTest wird unverändert durch
einen lokalen Java-Proxy aufrufweise serialisiert; Gates liegen VOR diesem
kurzen Raw-Lock. Kein Warten unter dem Raw-Lock, das den Kontrollpfad künstlich
blockiert. Ereigniszähler sind atomar; LastError wird pro Fake-Thread gesichert.
Echte Native.load-/Kernel32-/JnaLoader.open-Pfade werden nicht ausgeführt.
Wie bisher sind nur JNA-Memory-/Strukturlayoutprüfungen mit Dispatch-Unterbau
enthalten, keine Win32-Prozess-/Jobfunktionen.

Final vor jedem tatsächlichen Lauf identischer --dry-run; alle Gradle-Aufrufe
--offline --no-daemon, Java 21, keine JVM-Overridevariablen vorhanden.

~~~powershell
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64ConcurrencyTest' --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest' --tests 'de.mangalib.windowsadapter.Win64NativeBindingsTest' --dry-run
.\gradlew.bat --offline --no-daemon jobAdapterDependencyReport jobAdapterUnitTest --rerun --tests 'de.mangalib.windowsadapter.Win64ConcurrencyTest' --tests 'de.mangalib.windowsadapter.Win64BindingsTest' --tests 'de.mangalib.windowsadapter.Win64ProcessBindingsTest' --tests 'de.mangalib.windowsadapter.Win64NativeBindingsTest'
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

Graph 1 nur Dependencyreport/isolierte Adapter-Compile-/Resource-/Testtasks;
Graph 2 normale Compile-/Resourceaufgaben und exakt die drei gefilterten
datenbankfreien Klassen. Kein JavaExec, jobAdapterEmptyJob, manualSafetyTest,
clean, assemble, check oder Spring-/JPA-/Integrationsvolltest. Reguläre Gradle-/
JUnit-Prüfharness-JVMs und begrenzte Java-Testthreads sind keine Workerstarts.

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Win64ConcurrencyTest, neu | 26 | 0 | 0 | 0 |
| Win64BindingsTest, unverändert | 28 | 0 | 0 | 0 |
| Win64ProcessBindingsTest, unverändert | 61 | 0 | 0 | 0 |
| Win64NativeBindingsTest, unverändert | 44 | 0 | 0 | 0 |
| OwnedJobAdapterTest, unverändert | 45 | 0 | 0 | 0 |
| SupervisorModelTest, unverändert | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest, unverändert | 39 | 0 | 0 | 0 |
| Unterschiedliche Fälle gesamt | **278** | **0** | **0** | **0** |

Beide finalen Läufe BUILD SUCCESSFUL; Vorläufe mit 17, 22 bzw. 24 neuen Fällen
ebenfalls grün, nicht addieren. Keine bestehende Assertion abgeschwächt.
Die 26 neuen Fälle decken insbesondere ab:
- blockierten Query-/Start-/Vorbereitungs-/Identitäts-/Wait-/Closeaufruf;
- fehlende bzw. bereits freigegebene Kontrollreservierung vor Native-Start;
- Kontrollzugriff bereits während Initialisierungs-Set/Readback und fehlende
  Kontrollfähigkeit solange ein Job-Create noch keinen Handle geliefert hat;
- unabhängige Terminateanforderung und lesbaren Fortschritt bei selbst
  blockiertem Kontrollaufruf, ohne Doppelterminierung/Doppelfreigabe;
- Stop vor Resume, bereits eingetretenen späten Resume, späte Create-/Handle-
  callbacks und zurückgestellte Zustellung bis Owner/Permit wieder passen;
- unveränderliche 15-s-Deadline, abgelaufenes Budget mit lokalem Startstopp,
  Kontrollfehler, fehlende Gruppenendbelege und konservative Quarantäne;
- fremde/alte Tickets und neue unabhängige Gruppe ohne Ressourcenwirkung;
- Lebensdauerpin, release während Kontrollaufruf, Close bei noch aktivem
  Basisquery, keine Ressourcenfreigabe vor Kontrollrückkehr und keine neue
  Terminierung nach reservierter Freigabephase.

### Dateien, Artefaktgrenzen und Entscheidung

Geändert: KernelCalls.java, OwnedJobAdapter.java im bisherigen Test-/Adapter-
bereich; Win64KernelCalls.java und Win64ProcessKernelCalls.java im isolierten
Source Set; build.gradle.kts nur um Win64ConcurrencyTest-Include ergänzt.
Neu: src/jobAdapterTest/java/de/mangalib/windowsadapter/Win64ConcurrencyTest.java.
TESTING.md/ROADMAP.md ausschließlich um diesen Nachtrag ergänzt.

FakeKernelCalls, sämtliche alten Testquellen/Modelle, Native-Ladeverdrahtung,
Layouts, Prototypen, STOPs, consumed/trace, main/manual-Quellen und Sicherheits-
guards bleiben unverändert. JNA-Core 5.17.0 unverändert im isolierten Adapter,
Dependencyreport bestätigt denselben SHA-Pin und keine Runtimevererbung.
Keine neue Dependency oder neues Source Set/Task. Produkt-/Manual-Paketierung
unverändert; vorhandenes Manual-JAR nicht neu gebaut. Die bekannte transitive
JNA 5.13.0 darin und die offene Runtime-Allowlist werden nicht nebenbei verändert.

**Bewertung der verbleibenden Risiken:**
- Ein blockierter regulärer Aufruf hält keinen vom Kontrollpfad benötigten
  Java-Monitor mehr. Er kann aber weiterhin vollständigen Abschluss verhindern,
  weil benutzte Handles/Puffer nicht geschlossen werden dürfen.
- JNA oder Windows können eigene interne Sperren halten; concurrent Query/
  Terminate am selben privaten Jobhandle ist hier nur auf Komponentenebene
  gefakt, nicht OS-seitig abgenommen. Terminate/Wait/Close können selbst blockieren.
- Eine späte native Create-Rückkehr nach Terminate benötigt weiterhin
  OS-seitigen Nachweis; keine Annahme, eine alte Terminate-Rückkehr beseitige
  auch jede spätere Prozessanlage.
- Callerthread, JVM-Deadlock/-Stillstand/-Absturz und Hostausfall sind nicht
  gelöst. Ein weiterer Java-Thread bzw. lokale Counter sind keine OS-Watchdog-
  oder Crashgarantie. Ohne laufenden unabhängigen Aufrufer wird kein API-Kill
  magisch ausgelöst.
- Für eine belastbare Zusage über JVM-/Supervisor-Ausfall oder begrenztes
  Stoppen trotz blockierendem Kontrollaufruf ist ein gesondert abgesicherter
  externer Kontrollhost/OS-Mechanismus samt Handleübertragung, Ausfall- und
  Endnachweisen zu entscheiden. Auch daraus keine absolute Garantie gegen
  globale OS-/Kernelblockade ableiten.

Daher erster suspendierter OS-Versuch weiterhin gesperrt. Erst getrennt
Unabhängigkeits-/Ausfallmodell festlegen, native Handle-/Nebenläufigkeits-/
Spätstartverträge prüfen und unabhängigen begrenzten Endnachweis abnehmen;
danach neue ausdrückliche einmalige Prozessfreigabe. Keine Prozessfreigabe,
kein Wiederanlauf alter Prototypen und keine weitere Stufe aus diesen Tests.

Keine Git-Operationen, Datenbankverbindungen/SQL/Migrationen, Spring-/HTTP-/
Browser-/Selenium-/Gecko-/Live-Scraperstarts, Downloads oder Installationen.
SSRF/T03/T04/T06 unverändert offen, fachliche Prioritäten nicht verändert.


## T02 – B-WATCHDOG-1: unabhängiger Kontrollhost, Architekturentwurf (24.10.2026)

### 1. Auftrag, Nachweisstand und bestehende Fundstellen

Ausschließlich statische Analyse und Entwurf; KEINE neue Implementierung oder
Ausführungsfreigabe. B-SPEC-1, B-MODEL-1/2 und B-ADAPTER-1 bis -5 bleiben
Grundlage. Die 278 zuletzt erfolgreichen ausgewählten Fälle sind historischer
Prüfstand, in diesem Auftrag NICHT erneut ausgeführt. Der erste suspendierte
OS-Prozessversuch bleibt GESPERRT; auch kein leerer Jobversuch wird wiederholt.

Statisch bestätigte Ausgangspunkte:
- src/test/java/de/mangalib/windowsadapter/OwnedJobAdapter.java:
  requestControlCleanup reserviert einmalig eine Kontrollaktion, hält beim
  terminate-Aufruf keinen Owner-Monitor, und trennt CleanupProgress vom Endbeleg.
  Ein synchroner nativer Aufruf kann trotzdem seinen aufrufenden Thread halten.
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64ProcessKernelCalls.java:
  prepareCleanupControl hält JobUse auf DEMSELBEN OS-Handle, kein DuplicateHandle.
  Flight sperrt reguläre Konkurrenz logisch; CreateProcessW benutzt JOB_LIST,
  CREATE_SUSPENDED, feste Startkonfiguration und bislang keine Handlevererbung.
- src/jobAdapter/java/de/mangalib/windowsadapter/Win64KernelCalls.java:
  JobUse schützt Handlelebensdauer innerhalb einer JVM. Counter sind keine
  prozessübergreifenden Kernelhandle-Kopien und überleben keinen JVM-Absturz.
- src/test/java/de/mangalib/scrapermodel/SupervisorModel.java:
  verify verlangt alle C-Komponenten und keinen ausstehenden Start. Weder
  Instanzneustart noch Cleanupanforderung sind ein Recovery-/Gruppenendbeleg.
- IpcBudgetModel im selben Verzeichnis ist ein reines Envelope-/Budgetmodell;
  es implementiert weder einen authentisierten Kanal noch OS-Pipes.
- build.gradle.kts trennt jobAdapter/jobAdapterTest von main/manualTest.
  manualBootJar entfernt heute LiveScraperGateway und beide Live-Scraperklassen.
  SyntheticScraperGateway bleibt alleinige manuelle Implementierung.

Diese Dateien und die bisherigen Tests wurden NICHT verändert. Kein bestehender
Adapter wird durch den folgenden Entwurf rückwirkend als crashsicher eingestuft.

### 2. Offizielle Windows-Verträge und ihre Grenzen

Am 24.10.2026 anhand Microsoft Learn geprüft; Links bezeichnen Dokumentations-
verträge, keine auf diesem Rechner durchgeführten Messungen:

- [CreateJobObjectW](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-createjobobjectw):
  unbenannte Erstellung möglich; Kill-on-close greift beim LETZTEN offenen
  Jobhandle. Ein weiteres gültiges Duplikat verhindert diesen letzten Close.
- [TerminateJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-terminatejobobject):
  betrifft aktuell zugeordnete Prozesse und Kindjobs. Daraus wird hier weder
  eine feste Rückkehrfrist noch eine dauerhafte Startversiegelung abgeleitet.
- [UpdateProcThreadAttribute](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute):
  JOB_LIST ordnet beim Start zu; HANDLE_LIST begrenzt Vererbung und verlangt
  bInheritHandles=TRUE. Attributwerte bis Listenabbau gültig halten. JOB_LIST
  ist ab Windows 10/Server 2016 dokumentiert.
- [Nested Jobs](https://learn.microsoft.com/en-us/windows/win32/procthread/nested-jobs):
  Hierarchie, vererbte Mitgliedschaft, aggregiertes Accounting und Ancestor-
  Limits beachten. Nur eigene Untergruppe beenden, niemals fremde Ancestors.
- [DuplicateHandle](https://learn.microsoft.com/windows/win32/api/handleapi/nf-handleapi-duplicatehandle):
  Duplikate referenzieren dasselbe Objekt und sind im jeweiligen Zielprozess
  gültig. Transfer braucht kontrollierte Prozesshandles; Fehler bei
  DUPLICATE_CLOSE_SOURCE können die Quelle trotzdem schließen.
- [Jobrechte](https://learn.microsoft.com/en-us/windows/win32/procthread/job-object-security-and-access-rights):
  Erzeuger erhält ALL_ACCESS; Query, Set, Assign und Terminate sind verschiedene
  Befugnisse. Handlemitgliedschaft und Zugriffsbefugnis sind getrennte Dinge.
- [Prozessrechte](https://learn.microsoft.com/en-us/windows/win32/procthread/process-security-and-access-rights):
  insbesondere DUP_HANDLE/VM_WRITE/CREATE_THREAD sind sicherheitsrelevant.
  Zugriff auf Hostprozess kann die bloße Geheimhaltung eines Pipe-Tokens umgehen.
- [Named-Pipe-Sicherheit](https://learn.microsoft.com/en-us/windows/win32/ipc/named-pipe-security-and-access-rights):
  Benutzer-ACL allein authentisiert keinen bestimmten Prozess derselben
  Identität; Default-ACL ist ungeeignet. Eigene Kanalsicherheit nötig.
- [Job-Commit](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information)
  und [CPU-Rate](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_cpu_rate_control_information):
  Commit ist kein RSS-/Gesamtsystembudget; CPU-Hardcap betrifft Scheduling-
  Intervalle, Nested-Raten beziehen sich auf die Elternzuteilung.
- [Restricted Tokens](https://learn.microsoft.com/en-us/windows/win32/secauthz/restricted-tokens):
  möglicher Baustein für spätere Worker-Isolation, keine automatisch sichere
  Firefox-/Desktop-/Dateisystem-/Netzwerksandbox.

Die folgenden Zustands-, IPC- und Ownershipregeln sind eigene Entwurfsfolgerungen,
keine pauschal durch diese Quellen bewiesenen Windows-Sicherheitsgarantien.

### 3. Architekturvarianten

S = MangaLib-Supervisorprozess; H = kurzlebiger Kontrollhostprozess;
W = Worker mit Gecko/Firefox-Nachfahren; J = privater Workerjob.

| Variante | Blockierter nativer Aufruf | S-Absturz | H-Absturz / atomarer Start | Handles, Rennen, Recovery | Aufwand / Bewertung |
|---|---|---|---|---|---|
| A: S besitzt J, separater Java-Kontrollthread | B-ADAPTER-5 umgeht eigene Monitore; gemeinsamer JVM-/JNI-/Kernelstillstand bleibt | Letzter Handle kann Kill-on-close auslösen; kein verbleibender eigener Beobachter | Kein H; JOB_LIST-Start möglich | Keine OS-Isolation; offene Startaufrufe/Handles können Cleanup verhindern, Neustart bleibt gesperrt | Niedrig, als Komponentenbaustein nützlich, als unabhängiger Ausfallschutz unzureichend |
| B: S startet W und besitzt J, H erhält eigenes J-Duplikat | H kann J unabhängig terminieren, sofern eigener Aufruf reagiert | H kann weiter kontrollieren; S-Close allein löst wegen H-Kopie kein Kill-on-close aus | S kontrolliert nach H-Crash weiter, solange S lebt; atomare Duplikation/Bereitschaft vor Start nötig | Zwei Handleowner, H-Tod lässt S-Kopie offen; S-Create kann nach H-Terminierung zurückkehren. Gegenseitiger Crash-/Transfernachweis aufwendiger | Mittel bis hoch; verbessert Verfügbarkeit, beseitigt Late-create-Verantwortung nicht |
| C: H besitzt J und gesamten W-Lifecycle | S bleibt als eigener Prozess ansprechbar; separater Kontrollpfad in H plus äußerer Abbruch nötig | H erkennt Kanal-/Leaseverlust und bereinigt; allein von responsivem H abhängig | H-Crash schließt seine J-Kopien, wenn keine Kopie entkommen ist; W per JOB_LIST/SUSPENDED | Ein J-Owner, keine Übertragung an S/W; ohne äußere Beobachtungsgruppe ist Crash-Endbeleg schwächer | Mittel; empfohlene Basis, ergänzt um eigene äußere Gruppe K unten |
| D: reine Kernelquoten/Timer, Debugger oder Dienst | Commit/CPU-Limits lösen kein allgemeines Wallclock-/I/O-Problem; Waitable Timer benötigt ausführenden Empfänger | Debugger-/Dienstlösungen haben eigene Lebensdauer und Rechte | Zusätzliche Bootstrap-/Crashkomplexität; Prozessbaumscans sind kein Ersatz | Debugger-Kill deckt nicht beliebige Nicht-Debuggee-Nachfahren; Dienst/Broker oder VM wären größere Infrastruktur | Als Ersatz nicht empfohlen; Kerneljob als klar begrenzte Ergänzung zu C sinnvoll |

Ein Thread ist kein Betriebssystemprozess. Auch zwei Prozesse sind keine
unabhängigen Kernel oder Schutz gegen Administrator, Rechnerausfall bzw.
beliebigen kompromittierten Code mit identischen Zugriffsrechten.

### 4. Empfehlung C mit einer äußeren eigenen Ausfallgruppe K

Empfohlener, noch freizugebender konkreter Aufbau:

~~~text
S: MangaLib (außerhalb K und J)
  besitzt exklusiv das nicht vererbbare K-Jobhandle und gehaltene H-Instanz
  |
  K: private unbenannte Ausfallgruppe, Kill-on-close, kein Breakaway
     H: kurzlebiger Java-21-Kontrollhost mit isoliertem Adapter
       besitzt exklusiv J-Jobhandle, W-Start/IPC/Ergebnisse
       |
       J: private unbenannte Workergruppe, Kill-on-close, kein Breakaway
          W -> Gecko -> Firefox -> abgenommene Kindjobs/Nachfahren
~~~

H ist außerhalb des zu terminierenden inneren Workerjobs J, aber innerhalb K.
S gehört weder K noch J an. Beide Gruppen werden ausschließlich für diesen
Auftrag neu erstellt; K enthält niemals weitere MangaLib-/Fremdprozesse.
Mitgliedschaft vererbt keinen Jobhandle. Gemeinsame bereits vorhandene IDE-/
Terminal-/PCA-Jobs können Ancestors bleiben; ihre Limits werden weder geändert
noch ihre Handles für Cleanup verwendet.

Die zweite eigene Gruppe ist begründet: S kann bei blockiertem/abgestürztem H
über K auch H und sämtliche enthaltenen Nachfahren kontrollieren und K danach
noch abfragen. Nur H zu töten und anschließend verschwundene bekannte PIDs zu
suchen wäre kein vollständiger Gruppenendbeleg. K ist kein zweiter dauerhafter
Überwachungsdienst, kein Broker und keine rekursive Watchdogkette.

H läuft nur für einen Auftrag; keine Wiederverwendung nach Fehlern. Nach
belegtem normalem Ende kann ein NEUER expliziter Nutzerauftrag einen neuen H
erhalten. Kein automatisches Wiederholen eines fehlgeschlagenen Imports.

Umsetzungskandidat: Java 21 + bestehender isolierter JNA-Core, eigener kleiner
Entry Point ohne Spring/DB/HTTP. Das vermeidet zunächst einen zweiten C++-/C#-
Build. Die bestehenden Ports/Ownershipkonzepte bleiben wiederverwendbar.
Zusätzliche K-/IPC-/Bootstrap-Bindings müssten getrennt entwickelt und geprüft
werden; heutiger Adapter ist dafür nicht schon fertig. GC/JVM-Ausfall betrifft
H weiterhin und wird durch S/K behandelt, nicht durch Java als harte Echtzeit.

Ein kleiner nativer Host kann später sinnvoll sein, wenn Java-/Pipe-Bootstrap
zu komplex wird; eigenes Binary, Toolchain, ABI, Verteilung und Nachweispflege
sind zusätzliche Kosten. Keine solche Toolchain/Dependency wird jetzt eingeführt.

### 5. Eigentums-, Rechte- und Bootstrapvertrag

| Ressource | Erzeuger / Halter | Befugnis und Abschluss |
|---|---|---|
| K | S; genau ein OS-Handle, keine Kopie nach H/W | Set/Readback/Assign/Terminate/Query für eigene Gruppe; letzter Close nur geordnet nach Endbeleg oder implizit bei S-Crash |
| H-Prozess / Primärthread | S aus eigener Create-Rückgabe | Gehaltene Instanz mit PID+Erzeugungszeit+Image+K-Mitgliedschaft; Threadresume nur nach Bootstrapnachweis, Threadhandle danach schließen; Prozesshandle bis Signal/Abschluss halten |
| J | H; ein OS-Handle | Set/Readback/Assign/Terminate/Query; lokale JobUse für jeden laufenden Aufruf. Keine Kopie an S oder W |
| W-Prozess / Primärthread / weitere gehaltene Instanzen | H | Identität, J/K-Zuordnung und Endsignal prüfen; kein Resume nach Stop; keine PID-basierten Ersatzkills |
| S-Beobachtung in H | Nur explizit bereitgestellter read-only Prozesshandle | SYNCHRONIZE und erforderlichenfalls QUERY_LIMITED_INFORMATION, kein TERMINATE/DUP_HANDLE/VM-Zugriff; Ende ist Anlass für Cleanup, kein Gruppenendbeleg |
| Kontroll-IPC S–H | S erzeugt, nur adressierte Enden in H | Handle-Allowlist, nicht an W; begrenzter authentisierter Kontrollkanal |
| Daten-IPC H–W | H erzeugt, nur notwendige Enden in W | Kein Kontrollkanal/Journal-/Job-/Supervisorhandle; stdin/stdout/stderr strikt getrennt |
| Journal / Scope-Lock | ausschließlich S | Kein vererbter Dateihandle, kein Pfad aus W/HTTP; Abschluss erst nach H und K beendet und übriger C-Barriere |

Create liefert zunächst breite Rechte; keine unbelegte Behauptung, ein frisch
erzeugter Handle sei schon least-privilege. Erzeugungsbefugnisse bleiben beim
Owner. Eventuelle rein lokale Rechtsreduktion durch DuplicateHandle benötigt
eigene Bilanz und Originalclose; nicht für die Erststufe erforderlich.

Zielvertrag für Kontrollhandle-Kopien:
- Jobhandle nie über Prozessgrenzen duplizieren, Namen nie veröffentlichen.
- Lokale JobUse sind Zähler, KEINE zusätzlichen OS-Handles.
- Jede tatsächlich erzeugte lokale OS-Kopie separat registrieren; kein
  Kill-on-close-Beleg, bis alle Kopien weg sind.
- Kein DUPLICATE_CLOSE_SOURCE über fremde Prozesse, kein Remoteclose als Rettung
  eines laufenden Aufrufs, kein Handlezahlwert aus Journal/IPC als Capability.
- Keine extra Querykopie von J an S: sie würde gerade H-Crash-Kill-on-close
  verhindern. S beobachtet seine äußere Gruppe K.
- Handleclose nur nach Rückkehr aller Nutzungen, einmaliger Closeversuch;
  unklarer Close bleibt unsicher. Keine Freigabe durch Timeout eines Future.

Bootstrapsequenz, noch unimplementiert:
1. S schließt Admission atomar; Ticket/Generation und ausschließlich eigenen
   RecoveryScope reservieren, bisherigen R1-Zustand prüfen. Dauerhaftes INTENT
   VOR irgendeiner nativen Ressourcenerzeugung bestätigen.
2. S erstellt/configuriert K: Kill-on-close, Breakaway und Silent-Breakaway aus,
   Limits/Readback; zunächst kein Prozess. Unsicherer Nativeausgang => keine
   Folgestarts, Ownership und Sperre behalten.
3. S bereitet ausschließlich feste H-EXE/JAR/CWD/Umgebung vor. H atomar mit
   JOB_LIST(K)+SUSPENDED erzeugen. Nicht erst starten und später zuweisen.
   Liste, Puffer und Handlewerte bleiben bis nativer Rückkehr owned.
4. H-Instanz und K-Zuordnung prüfen, kontrollierte Kanalenden/Beobachtung
   vorbereiten, dann H einmalig fortsetzen. H darf vor authentisiertem
   Handshake, gültigem Intentbezug und vereinbartem Budget KEIN J/W starten.
5. H authentisiert S über geerbte dedizierte Kanal-Capability, validiert seinen
   Bootstrap, erstellt J und liest Schutzflags/Limits zurück.
6. H erzeugt W atomar JOB_LIST(J)+SUSPENDED. Als Kind von H muss W zusätzlich
   in K bleiben. H hält KEIN K-Handle; K wird über vererbte Mitgliedschaft
   getragen. Diese K/J-Kette ist OS-seitig ausdrücklich noch zu belegen.
7. H prüft eigene W-Identität/Zuordnung; nur bei unverändertem Ticket, Stop=false,
   intakter Lease, gültigen Fristen einmal Resume. RUNNING erst nach gültigem
   W-Handshake. S hält weiterhin exklusiv K und die H-Instanz.

Für Vererbung später explizites HANDLE_LIST mit bInheritHandles=TRUE, nur
minimal nötige Pipe-/Beobachtungsenden. K/J/Primärthread-/Journalhandles sind
nie vererbbar und fehlen zwingend in jeder Liste. Worker-Datenenden dürfen
Gecko/Firefox nicht ungewollt offen halten. Enden ohne erlaubte Kindvererbung,
keine ambienten Standardhandles, keine unbegrenzte systemweite Vererbung.
Dieser künftige IPC-Startvertrag erweitert den heutigen inherit=false-Pfad;
nicht heimlich die aktuelle getestete Bindung umstellen.

Fehler beim H-Start: S besitzt weiterhin K auch bei ausstehender Create-Rückkehr.
Fehler beim W-Start: H besitzt J, S besitzt K. Kein Resume und kein Fallback
ohne JOB_LIST; keine nachträgliche Assign-Reparatur. Fehler vor Handle-Rückgabe
nicht als Nichterzeugung interpretieren.

### 6. Fehler- und Cleanupmatrix: Entscheidung ist kein Endnachweis

| Ereignis | Aktion / verantwortlicher Owner | Was weiterhin bewiesen werden muss |
|---|---|---|
| Hs regulärer Kernelaufruf blockiert | S kann separat Stop senden und bei fehlender sicherer Rückmeldung K terminieren; Hs unabhängiger lokaler Kontrollpfad bleibt zusätzlich verfügbar | W-/H-Ende, K leer und Accounting konsistent, keine ausstehende Startwirkung, I/O und Ressourcenabschluss; Terminaterückkehr allein reicht nicht |
| H bleibt vollständig stehen oder stürzt ab | S schließt Admission, verwendet ausschließlich eigenes K. H-Crash schließt dessen J-Kopien und kann Kill-on-close auslösen | K-Endbeleg über S; kein Beweis nur aus H-Ende oder fehlenden PIDs |
| S stürzt ab | Letztes K-Handle wird vom OS geschlossen, Kill-on-close soll H+W treffen; Hs eigener S-Wait/Kanalverlust kann zusätzlich reagieren | Tatsächliche Crash-/Nestedwirkung separat prüfen. Nach Neustart ist INTENT schmutzig: kein automatisches IDLE |
| S lebt, ist aber pausiert/blockiert | H überwacht lokalen Leaseablauf und S-Endsignal unabhängig von Workerarbeit, stoppt J und beendet sich geordnet, sofern möglich | Bleibt H selbst blockiert, ist kein dritter reaktionsfähiger Owner garantiert. Gesperrter ungeklärter Zustand; keine weitere Watchdogebene behaupten |
| Hs Kontroll-Terminate blockiert | S kann äußeres K terminieren, ohne Hs Monitore/IPC zu benötigen | Der eigene K-Aufruf von S kann ebenfalls blockieren. Kein harter 15-s-Endbeleg ableitbar |
| S-Kontrollpfad oder Kernel hängt ebenfalls | Quarantäne, keine neuen H/W, kein Retry/zweiter Killer, keine Handlefreigabe unter Nutzung | Kein endlicher Cleanup garantiert; kontrollierte Recovery/OS-Neustart bleibt gesonderte Entscheidung |
| Create kehrt nach Stop erfolgreich zurück | Dasselbe Ticket behält Rückgabe und Ressourcen; nie Resume, SUCCESS oder neuen Auftrag; laufende Startseite explizit abschließen | Ein früherer Terminate versiegelt den Job nicht nachgewiesen. Frische Gruppenprüfung nach Ende aller Erzeuger nötig; falls H hängt, K-Eskalation |
| Close hängt/Ergebnis unklar | Reservation und Ressource behalten, keine Wiederholung auf möglicherweise wiederverwendetem Wert | Kein CLOSED/CLEAN; Fristablauf sperrt dauerhaft |
| Kommunikationsabbruch/ungültiger Frame | H stoppt J, S behandelt H als unsicher und kann K beenden; keine Resultatübernahme | Vollständiger eigener Abschluss und R1; ein neuer Kanal heilt kein altes Ticket |
| Absturz beider Prozesse | Kernel-Kill-on-close als letzte Schutzmaßnahme, sofern kein Jobhandle entkommen ist | Kein lebender eigener Vollständigkeitsbeobachter, kein fester Zeitbeleg; Recovery nach Neustart konservativ gesperrt |

S-Eskalation ist EIN terminaler Abbruch der gesamten eigenen K, einschließlich H,
kein zweiter Versuch desselben Imports. In H bleibt pro J die bisherige
einmalige Terminierungsreservation erhalten. K-Eskalation beansprucht kein
neues Cleanupbudget. Kein „noch einmal J terminieren“, um späten Start
unbeobachtet wegzuerklären. Wenn Endbelege nicht in der ursprünglichen Frist
erreichbar sind, bleibt Quarantäne auch bei späterer Ressourcenbeendigung.

Spätes Create darf insbesondere nicht durch gleichzeitiges Close des im
Create genutzten Jobhandles „abgebrochen“ werden. Hs Erzeugerthreads und
B-SPEC-Ressourcen müssen wirklich abgeschlossen sein. Bei K-Abbruch kann H
zwar nicht mehr im Userspace weiterstarten; ein noch im Kernel befindlicher
Start ist damit nicht als innerhalb einer festen Frist erledigt bewiesen.
K während dieser Unsicherheit offen halten, keine Momentaufnahme als
Endbeweis. Genau diese Interleavings brauchen eine gesonderte OS-Abnahme.

Reguläres Erfolgsende:
1. Gültiges vollständiges W-Ergebnis wird intern gehalten.
2. H stoppt jeden weiteren Start/Resume und erbringt C für J (Gruppenende
   VOR letztem J-Close), beendet alle Datenreader/-writer und gibt Ressourcen
   frei. Kein Resultat allein aufgrund erfolgreicher Extraktion.
3. H sendet gebundene Abschluss-Evidenz, schließt Kontrollkanal und endet.
4. S bestätigt H-Endsignal, K leer/Accounting konsistent, I/O abgeschlossen,
   keine eigene H-Create-/Callbackwirkung mehr, Ressourcen einschließlich
   letztem K-Handle geordnet abgeschlossen. Erst dauerhaftes R1-COMPLETED
   erlaubt Freigabe und Ergebnis nach bestehendem Erfolgsvertrag.
5. Bei widersprüchlicher Evidenz kein Erfolg, keine stillschweigende Reparatur.

Das ist eine additive zweistufige Nachweispflicht, keine Abschwächung des
bestehenden C. Nach H-Crash kann K das vollständige OS-Gruppenende beobachten,
aber Hs verlorene Detailbuchführung nicht nachträglich rekonstruieren.
Incident und Recovery bleiben dann getrennt freigabepflichtig.

### 7. Enger IPC-Vertrag und gleichberechtigte Windows-Identität

Bevorzugt zwei geerbte, dedizierte anonyme Pipekanäle S–H plus separate
H–W-Datenkanäle. Keine TCP-Ports, kein bekanntes Named-Pipe-Reconnect, kein
HTTP-Workerbackend. Kanalenden nur explizit an die gehaltene H-Instanz geben.
Anonyme Pipes können synchron blockieren: eigener begrenzter Reader/Writer,
keine Queueausweitung, Stop-/Deadlinepfad nicht von deren Rückkehr abhängig.
Overlapped-I/O-fähige lokale Named Pipes wären eine spätere Alternative;
dann lokale Remote-Ablehnung, explizite ACL, first-instance-Schutz und
gegenseitige Authentisierung nötig, nicht nur SameUser.

Kandidaten für V1, noch keine produktiven Grenzwerte:
- S–H feste Steuerframes maximal 4 KiB; eigener Ergebnisstrom getrennt.
  Header: Version, Scope, S-Inkarnation, H-Inkarnation, zufällige Auftrags-ID
  (mindestens 128 Bit), Generation, Richtung, Sequenz und Typ.
- Typen: HELLO, READY, START, STARTED, LEASE, STOP, STOP_ACCEPTED,
  GROUP_END_EVIDENCE, RESOURCES_CLOSED, RESULT_AVAILABLE, ERROR.
  Genau ein START pro Kanal/Ticket, keine generische RUN/SHELL/EXEC-Anweisung.
- HELLO/READY enthalten Challenge/Response, Artefakt-/Konfigurationsbindung
  und festes Protokoll. Zufälliges Bootstrapgeheimnis nur über dedizierten
  geerbten Kanal, nie Argumente/Umgebung/Logs/Journale/Workerframes.
  Authentisierung an exklusive Start-/Kanal-Capability binden; ein Hash oder
  eine behauptete PID/Imagezeichenkette ist kein authentisierter Absender.
- Streng längengeprüft vor Allokation, begrenzte UTF-8-/JSON-Struktur, keine
  polymorphe Deserialisierung/serialisierten Javaobjekte. Sequenzlücken,
  doppelte STARTs, falsche Richtung/Generation, Überlänge, Korruption,
  unbekannte Typen, Trunkierung/EOF und fehlender Handshake => Stop/Quarantäne.
  Keine beschädigte Session durch später „guten“ Frame heilen.
- Worker kennt nur eigenes Datenticket. Worker-SUCCESS/EOF ist untrusted,
  niemals GROUP_END_EVIDENCE oder Berechtigung zur Slot-/Journalfreigabe.
- GROUP_END_EVIDENCE ist Hs Behauptung mit Revision für genau diesen Auftrag;
  S prüft die äußere Endkonjunktion selbst. STOP_ACCEPTED bedeutet nur
  angenommen, niemals Terminate zurückgekehrt, J leer oder Ressourcen geschlossen.
- Lease als Vorschlag: S sendet alle 1 s monotone Sequenz; H erklärt nach
  5 s ohne gültigen neuen Lease den Kanal für verloren. Keine Verlängerung
  von Start-/Operations-/Cleanupfristen. GC/Pausen können Fehlabbrüche
  erzeugen: Zahlen vor späterer Umsetzung messen/freigeben.
- Keine nackten System.nanoTime-Werte zwischen JVMs vergleichen. S behält
  t0/Dstart/Dop und harte Admissionhoheit; H verwendet eigene monotone Uhr,
  lokal empfangene Restdauern als konservative Zusatzgrenze. Verzögerte IPC
  darf S-Fristen nicht verlängern. STOP setzt bei S tc; Hs lokale 15-s-Frist
  ist höchstens zusätzliche Grenze, NIE ein zweites Gesamtbudget. Späte
  Meldungen erlauben weder Resume noch Erfolgsannahme.
- Vor Handshakeabbruch kein W. Wenn H schon läuft, K bleibt owned bis
  H-Ende/Population/Ressourcen geprüft. Verlorenes ACK führt nicht zu erneutem
  START. Doppelte STOP-Anforderung idempotent, kein neuer nativer Kill.

WICHTIGE verbleibende Vertrauensgrenze:
Ein Worker mit identischem uneingeschränktem Windows-Token kann gegebenenfalls
S/H öffnen, Speicher lesen, Handles duplizieren oder Journaldateien verändern.
Unbenannter Job, User-ACL und geheimes Ticket allein verhindern das nicht.
Der Capabilitykanal schützt gegen falsch adressierte/replayed Nachrichten und
versehentliche Handlevererbung; er beweist KEINE Isolation gegen beliebigen
kompromittierten Same-Token-Code.

Vor einer solchen Sicherheitszusage separat Worker-Token/Integritätsniveau,
Prozess-/Thread-DACLs, Ownership/WRITE_DAC, Desktop, Artefakt-/Journalrechte und
Firefox-Kompatibilität festlegen und prüfen. Restricted Token/AppContainer/
separate Identität sind Kandidaten, keine hier freigegebene Architekturänderung.
Bloß Gruppenrechte entfernen oder DACL nachträglich setzen ist noch kein
lückenloser Pre-Resume-Isolationsnachweis. Ohne diese Entscheidung bleibt die
Gegnerannahme „kompromittierter Worker kann Kontrollhost nicht beeinflussen“
offener Live-Blocker, statt Worker implizit zu vertrauen. Kein Schutzversprechen
gegen Administrator/SeDebugPrivilege/kompromittierten Kernel.

### 8. Recovery, Journal und Aktualität

R1 aus B-MODEL-2 unverändert: genau ein exklusiver Scopeowner, INTENT dauerhaft
VOR K/H-Erzeugung, COMPLETED erst nach gesamter Endbarriere. H schreibt kein
eigenes konkurrierendes Recoveryjournal. Ergänzende vorgeschlagene Felder:
H-Inkarnation und gehaltene H-Instanzidentität, K/J-Phasen, erlaubte Artefakt-
Hashes, letzte bestätigte Protokollrevision, Startaufruf ausstehend/beendet,
Quelle jedes Teilbelegs. Keine exportierten Handlewerte oder Secrets.

Eine einzelne dateibasierte „atomare Startbestätigung“ von OS-Prozessstart plus
durable Journal existiert hier nicht. Das Zweiphasenprinzip ist absichtlich
konservativ: dauerhaftes Intent vor Start, korrelierter Startbeleg danach;
Crash dazwischen hinterlässt UNCERTAIN, niemals fälschliches CLEAN. IPC und
Dateisystem sind keine gemeinsame Transaktion. Journalgröße höchstens bisheriger
1-MiB-Kandidat, feste Pfade, keine Worker-/HTTP-Pfadwahl, keine Rohpayloads.

Fehlendes/korruptes/trunkiertes/rollbackverdächtiges Journal, unklare Boot-/
Scope-/Revisionbindung oder verlorene aktuelle Handles => Quarantäne.
Prüfsumme erkennt Beschädigung, nicht autorisierte Manipulation oder Replay.
Flush/Replace, Speicher-/Stromausfall und vertrauenswürdige Aktualitätsquelle
müssen getrennt nachgewiesen werden. Same-Token-Dateirechte ersetzen dies nicht.

Kein Wiederanschluss an eine alte Gruppe per PID, Jobname, Image oder altem
Handlewert; kein Scannen/Beenden fremder Kontrollhosts. Nach S-Neustart weder
neuen H starten noch frisches CLEAN anlegen. Solange derselbe S mit gültigem K
lebt, kann er eigene Endbelege prüfen. Bei verlorenem Owner bleibt R1s noch
gesondert zu bestätigender vollständiger Kernelneustart-/Bootbeleg-/Operatorweg
oder ein separat vorher abgenommener Recoverymechanismus nötig. Fast Startup,
Appneustart oder vergangene Zeit sind keine pauschale Entsperrung.
Auch später erreichter C hebt eine Sicherheitsquarantäne nicht eigenmächtig auf.

### 9. Ressourcen und Durchsetzbarkeit

| Bisheriger Kandidat | Entwurf / Messung | Grenze / offener Nachweis |
|---|---|---|
| Start 30 s | Ab S-Slotvergabe einschließlich Journal, K/H-Bootstrap und W-Handshake | Kein zweites 30-s-Budget für H oder W; Java-Hoststartup passt erst nach Messung nachweislich hinein |
| Band 120 s / Reihe 60 min | S-Dop ab gleichem t0; H überwacht eigene konservative Restdauer | Keine Verlängerung durch Fortschritt/Lease/IPC, keine Wallclockgarantie bei Scheduler-/OS-Stillstand |
| Cleanup 15 s | S setzt tc einmal; STOP, J-Cleanup, gegebenenfalls K-Eskalation, I/O, alle Closes und Journal teilen EINE Restfrist | Bereits S-Stoppentscheidung darf nicht erst 15 s auf H warten und dann neues K-Budget beginnen. Exakter Eskalationszeitpunkt später konfigurierbar innerhalb Frist abnehmen |
| J-Commit 2 GiB | Nur W/Gecko/Firefox im inneren J; vor Resume setzen/readback/messen | H liegt außerhalb J und braucht separat begrenzten Commit. K-Gesamtbudget > J plus Hostreserve ausdrücklich entscheiden, keine versteckte Aufweichung oder Ressourcenzusage |
| Workerheap 256 MiB | Feste W-Startoption, keine Requestüberschreibung | H-Heap/Metaspace/native/JNA und Decoder-Peaks zusätzlich messen; gleiche Identität ist keine Speichersandbox |
| CPU optional 50 % | Auf J, H bleibt für Cleanup ungedrosselt durch diese innere Rate | K/fremde Ancestors können H trotzdem drosseln; Nestedrate ist relativ. Kein Wallclocktimer |
| Prozesszahl / Register | Weiter maximal ein H und ein W-Start pro Ticket; Populationsregister weiterhin bounded | H und Helfer sind zusätzliche echte Prozesse. Historische ActiveProcessLimit=3/6-Diskrepanz bleibt Blocker; nicht umdefinieren oder Namen ausnehmen |
| W-Request / Resultat | 4-KiB-Request-, 32-MiB-Resultatkandidat, 30.032 Einträge/Tiefe 4; streamen statt mehrfache Vollkopie | Theoretische 131,7-MiB-Abschätzung bleibt offen. H als Zwischenstation darf S-/H-/W-Heaps nicht vervielfachen; kein neues stilles Fachlimit |
| Diagnosen | Bisher 1 MiB Stderr, 64 KiB Tail; kleines eigenes H-Steuerfehlerbudget festlegen, keine Rohlogs in HTTP | JEDEN Stream kumulativ zählen, vor Allokation, keine unbounded Queue; Doppelpuffer-/IPC-Flut kann Kontrollpfad nicht beanspruchen |
| Profil/Downloads/Dateien | Eigene ticketgebundene Tempwurzel und spätere sichere Cleanupregeln | Job liefert keine Dateiquote/SSRF-Sandbox; bei Reparse-/Ownershipunsicherheit kein Löschen fremder Pfade |

Kernelquoten haben eine andere Zusagestärke als beobachtete monotone Fristen.
Ein blockierter Kontrollhost kann durch äußere K-Kontrolle abgesichert werden,
WENN S und dessen eigener Kernelpfad reagieren. Sind beide nicht reaktionsfähig,
wird kein dritter Host vorgeschlagen. Harte begrenzte Beendigung unter beliebigem
Kernelhänger ist technisch nicht zugesichert und bleibt Freigabegrenze.

### 10. Wiederverwendung, Artefakte und spätere SSRF-Arbeiten

ScraperGateway.scrapeSeries/scrapeVolume bleiben spätere Produktgrenze.
MangaScraper.scrapeMangaData/BandScraper.scrapeBandData sollen im W weitgehend
wiederverwendet werden, nicht im H. Ergebnis weiter Map<String,String> mit
bisherigen Feldnamen/null-/Unicodeverträgen; bestehende Importvalidierung nach
Transport zusätzlich beibehalten. Keine neue Businesslogik, Referenz-/DB-
Services im W oder H. E1-Fehler-/Band-204-/Reihen-/JSON-Verträge nicht über
neue technische IPC-Statuscodes unbemerkt verändern. RESULT erst nach C/R1 an
das Gateway weiterreichen; keine Fachservices bei technischem Scraperfehler.

H benötigt keine Selenium-/WebDriverManagerklassen und keinen Browser.
W hat keine DB-Passwörter, Springkonfiguration, HTTP-Listener oder Journalrechte.
Nur reduzierte ausdrücklich geprüfte Umgebung weitergeben, keine geerbten
MANGALIB_*_DB_PASSWORD- oder JVM-Overridevariablen.

manual-test bleibt vollständig außerhalb des Livepfads: allein
SyntheticScraperGateway, kein H-/W-Start, keine neue Adapter-Initialisierung,
keine Native.load-Aufrufe und kein „Live-Fallback“. Künftige Artefakttests
müssen H-/W-/Launcherklassen und Startreferenzen im Manual-JAR negativ prüfen;
positive Runtime-Allowlist bleibt separat offen (historische transitive JNA
5.13.0 nicht als bereinigte Abhängigkeit darstellen). Bestehende Guards und
JUnit-Testumgebung bleiben unverändert. Jetzt keinerlei Paketierungsänderung.

SSRF-/Netzwerkisolation später an W+Browsernachfahren ansetzen: initiale Ziele,
Redirects, DNS/Rebinding, IPv4/IPv6, Proxys, Unterressourcen/WebSockets/Downloads
und Browserbroker berücksichtigen. K/J begrenzen Lifecycle, NICHT Netzwerk.
Eine reine URL-/Indexallowlist im Gateway verhindert keine Browserunterressource.
Ein späterer Egress-/Token-/Sandboxentwurf muss lokale H-IPC-Funktion und
Firefox-Sandbox erhalten; nicht durch Abschalten der Sandbox kompatibel machen.
Jetzt keine SSRF-Implementierung, T03/T04/T06 unverändert.

### 11. Priorisierte Freigabe- und Nachweismatrix

KEINE der folgenden Stufen ist durch B-WATCHDOG-1 ausführend freigegeben.
Vor jedem späteren Gradlelauf identischen --dry-run prüfen; nur ausdrücklich
freigegebene Aufgaben, bestehende Assertions und Datenbankguards erhalten.

| Stufe / Freigabe | Exakte Grenzen | Erwartete Nachweise | STOP / Cleanup / Fremdschutz |
|---|---|---|---|
| 1: gesonderter Auftrag B-WATCHDOG-2, nur reine Fakes | Keine FFI/Prozesse/DB/Netzwerk; neue Verträge isoliert, vorhandene 278 Fälle unverändert | K/J-Ownership, keine Jobkopie, Handshake/Lease, Version/Ticket/Sequenz, Late-create vor/nach Stop, erste Deadline, C/R1, korrupte Journale, S/H/Both-Fault-Fälle mit Fakeuhr | Jede unklare Transition bleibt gesperrt; kein Native-Lader/Starttask, kein automatischer Retry |
| 2: ausdrückliche IPC-/Harnessfreigabe | Erst Codec/Streams in-process; danach nur feste synthetische S/H-JVMs, keine nativen Workerstarts, Browser oder DB. Prozessanzahl, Laufzeit/Bytes und sicherer H-Endweg VOR Start festlegen | Echtes Framing/Backpressure/EOF, Challengebindung, Nichtvererbung ins Dummy-W-Contract, Halbhandshake, Prozessende vs. Kanalende, keine Fremdverbindung | Kann der reguläre Harness einen hängenden H nicht sicher beenden, nur in-process testen; nicht vermeintlich „DB-frei“ als beliebige Prozessfreigabe auslegen |
| 3: neue einmalige OS-Freigabe, weiterhin gesperrt | Ein eigener H als getrennt freizugebender Prüfhost; genau EIN synthetischer Root W suspendiert, KEIN Resume, keine Nachfahren/Dateiwirkung. Neue persistente Einmalfreigabe, keine Wiederverwendung alter Marker | H in K vor Ausführung, W atomar in J und K, kein W-Ausführungsmarker; vollständige gehaltene Identität/Listen/Accounting, kontrolliertes J-Ende, H-Ende/K-Ende, alle Ressourcen/Journale | Nativefehler/unerwartete Population/Frist/Identität => STOP, kein Nachstart, ausschließlich eigene gültige K/J. Unabhängige getestete begrenzte Kontrolle muss vorher vorhanden sein |
| 4: je Fehlerklasse eigene OS-Freigabe | Je ein kleiner synthetischer Aufbau, feste Maximalstartzahl/-dauer/-bytes, nur eigene Instanzen. Separat: kontrollierte Late-return-Gates, Closekopien, S-Crash, H-Crash, H-Pause, Timeout, K-Terminate, Nested-Fehler | Kein Resume nach Stop, K vollständig leer, Kopienbilanz, Kill-on-close bei wirklich letztem Handle; C/R1 und Crashsperre. Einzelne Fake-Blockade nicht als echter Kernelhang ausgeben | Keine absichtlich nicht beendbaren Kernelhänger. Bei fehlendem Endbeleg Quarantäne, kein Timer-/PID-/Namenskill. Alte STOPs und exakte Drei-PID-Assertion unverändert |
| 5: gesonderte Offline-Firefoxfreigabe | Erst nach Stufen 1–4; synthetische lokale Inhalte, frische eigene Profile, feste Browser-/Geckoversion und Population-/Ressourcenobergrenzen | Firefox-Nestedjobs, alle Nachfahren/IPC/Profilreste, Timeout/Absturz, keine externe Navigation und kein Kontrollhandle im Browser | Broker außerhalb K/J, unaufgelöste Identität, Downloads/Containmentfehler => STOP; keine Sandboxabschaltung/Fremdprozessbereinigung |

Die neue Architektur benötigt einen laufenden H. Der historische Vorschlag
„genau ein suspendierter Root, kein Resume“ autorisiert NICHT stillschweigend
einen zusätzlichen ausführenden Kontrollhost. H-Start und dessen eigene
Bootstrap-/Abbruchnachweise brauchen eigene vorausgehende Freigabe.
Keine zirkuläre Begründung „Host ist sicher, weil er im erst zu prüfenden Job
läuft“. Wenn der Versuchsaufbau ohne ungeprüfte Schutzannahme nicht sicher
begrenzbar ist, Stufe 3 nicht starten, sondern Versuchsdesign erneut vorlegen.

### 12. Kleine nächste Implementierungsstufe, Aufwand und Entscheidungen

Vorschlag B-WATCHDOG-2 (erst nach gesonderter Freigabe):
- Neues reines K/H/J-Lifecycle-Vertragsmodell und ControlEnvelope-Validator im
  isolierten Testbereich, Fakeclock, Fakekernel, Fakekanal und In-Memory-Journal.
- Opaque Capability-/Ticketobjekte, Ownershiptabelle, stop-monotone Reservationen,
  C-Erweiterung für H/K, keine persistenten nativen Handlewerte.
- Deterministische Race-/Fault-Tests: Stop vor/nach Eintritt, späte Rückgabe,
  S/H-Ausfall, fehlender Lease, unklare Journalaktualität, zwei gültige Kopien,
  Close während Nutzung, fremde Tickets, doppelte/abgeschnittene Frames.
- Keine Änderung vorhandener Modelle/Tests/Adapter, keine Main-/Manual-
  Integration, kein Hostmain, kein Native.load, kein JavaExec/Prozessstart,
  keine neuen Abhängigkeiten. Tests nur einzeln nach Taskplankontrolle.
- Ergebnis nur ausführbarer Vertrag samt Blockern; nicht behaupten, damit sei
  die neue Windows-Architektur implementiert oder ein OS-Start freigegeben.

Grobe Aufwandsspanne, keine Terminzusage:
Vertragsmodell 1–2 Entwicklungstage; IPC/Bootstrap und isolierter H 3–5;
K-Integration/Ownership/Endbelege 3–5; gestufte Windows-Crash-/Nested-Abnahme
2–4 plus Fehlerbehebung. Insgesamt eher 2–4 Wochen inklusive Review, ohne
Firefox-/SSRF-/Tokenisolation. Betriebssystembefunde können Umfang erhöhen.

Vor nachfolgenden Stufen ausdrücklich entscheiden:
1. C plus K und abrupten eigenen K-Abbruch von H als Eskalation akzeptieren?
   Im regulären Betrieb bleibt geordnetes Ende bevorzugt; native Eskalation
   betrifft nur vorab registrierte eigene Gruppe, keine beliebigen Prozesse.
2. Genügt best effort mit konservativer Quarantäne bei OS-/Doppelausfall?
   Eine absolute 15-s-Garantie unter Kernelblockade ist keine erfüllbare Zusage.
3. Gegnermodell und nötige Worker-Token-/Prozess-/Journalisolation entscheiden;
   Same-Token-Kompromittierung nicht als bereits abgewehrt behandeln.
4. H-Heap/K-Commitreserve, Lease-/Eskalationskandidaten und Resultatobergrenze
   anhand späterer Messungen abnehmen. Keine Fachpayloads still kürzen.
5. R1-Boot-/Aktualitätsquelle und kontrollierte Incident-Recovery abnehmen.

Entscheidung heute: **Suspendierter OS-Versuch weiterhin GESPERRT.**
Später nur nach freigegebenem Vertrag, implementierter unabhängiger Kontrolle,
nachgewiesenem Hostbootstrap/Handlebesitz/Nestedcontainment, klarer
Late-create-Behandlung und begrenztem eigenen Cleanup-/Endnachweis sowie neuer
ausdrücklicher Einmalfreigabe. Kann eine dieser Voraussetzungen nicht sicher
belegt werden, keine Prozessfreigabe aus diesem Dokument ableiten.

### 13. Tatsächliche Prüfungen dieses Auftrags

Nur lokale Quell-/Konfigurations-/Dokumentationslektüre und offizielle
Microsoft-Dokumentation recherchiert; kein Browserprozess gestartet.
Ausschließlich TESTING.md/ROADMAP.md um diesen Nachtrag erweitert.
Historische Dokumentpräfixe und geschützte Quell-/Build-/Test-/Evidenzdateien
vorher/nachher bytebezogen per SHA-256 verglichen; Ergebnis im Abschlussbericht.
Neue Abschnitte auf Whitespace-/Konfliktmarker und Konsistenz geprüft.

Keine Tests/Builds ausgeführt, auch nicht die historischen 278 Fälle;
keine Win32-/JNA-Aufrufe, Job-/Prozess-/OS-Versuche, DB/SQL/Migrationen,
Anwendungen/HTTP/Browser/Live-Scraper, Downloads/Installationen oder Git-
Operationen. Keine neuen Abhängigkeiten, Build-/Adapter-/Modell-/Teständerungen.
Historische STOPs/Freigaben/Evidenzen und fachliche Prioritäten bleiben erhalten.


## T02 – B-WATCHDOG-2: reines K/H/J-, IPC- und Recoverymodell (24.10.2026)

### Umfang und harte Ausführungsgrenze

B-WATCHDOG-1 ist jetzt ausschließlich als neues, datenbankfreies Vertragsmodell
im Testbereich konkretisiert. Kein ausführbarer Kontrollhost, keine
Workerimplementierung, keine IPC-Verbindung oder Win32-Bindung. Der erste
suspendierte Windows-Prozessversuch bleibt **GESPERRT**.

Neue Dateien, ausschließlich src/test/java/de/mangalib/watchdogmodel/:
- WatchdogModel.java: Zustandsautomat, Admission, Kanal-/Nachrichtenvertrag,
  Fake-Uhr, Deadlines, J-/K-Anforderungen und Abschlussbarriere.
- FakeJobKernel.java: symbolische Gruppen, Startaufrufe, Instanzen,
  Mitgliedschaften, Accounting, Jobhandle-Kopien und deren Lebensdauer.
- FakeRecoveryJournal.java: ausschließlich Speicherzustand, exklusiver Writer,
  revisionierte Intent-/Gruppenbindung und Abschlussprüfung.
- WatchdogModelTest.java: 85 deterministische Fälle einschließlich
  parametrisierter Varianten; keine Threads, Sleeps, realen Timer oder I/O.

Keine vorhandene Java-Datei, kein bestehender Test, kein Build-/Source-Set-/
Dependencyvertrag verändert. Es werden die vorhandenen normalen Testquellen
verwendet; kein neuer Task nötig. Die expliziten Windows-Adapter-Includes
ziehen das neue Paket NICHT in das isolierte Adapterartefakt. main und
manualTest beziehen kein test.output. Die neuen Quellen enthalten nur Java-
Speicherlogik und JUnit-Tests, keine Spring-/JNA-/Selenium-/Netzwerk-/Datei-/
Prozessstartreferenzen. Kompilierung ist keine Anwendungsausführung.

### Zustände, Tickets und Eigentum

UNAVAILABLE -> IDLE -> PREPARING -> HOST_STARTING -> HANDSHAKE ->
HOST_READY -> WORKER_STARTING -> RUNNING -> CLEANING -> IDLE.
QUARANTINED sperrt bei Protokoll-/Identitäts-/Journalincident oder
unbestätigtem Cleanupende dauerhaft; initialize ist kein Reset.

Genau ein Ticket für Band ODER Reihe; keine Queue, kein Retry oder Fallback.
Jedes Ticket ist eine neue opaque Objektidentität. Die numerische Generation
ist nur Diagnose: dieselbe Zahl in einer anderen Modellinstanz berechtigt nicht.
Die Ereignisverarbeitung ist sequenziell; dieses Modell ist KEINE produktive
threadsafe Admissionimplementierung und kein OS-Authentisierungsmechanismus.

| Ressource / Rolle | Modellregel |
|---|---|
| S | Außerhalb K/J, hält nur K-Kontrollhandle; kein J-Handle |
| H | Atomar in K, vor eigener J-Erzeugung fortgesetzt; außerhalb J, hält nur J-Kontrollhandle |
| W und dynamische Nachfahren | Atomar in J mit J.parent=K; keine K/J-Kontrollhandles |
| Jobkopien | Nur lokal innerhalb derselben Besitzerrolle; Cross-Role-Duplikation abgewiesen |
| Instanz | Gehaltenes Objekt plus PID, Erzeugungswert und Image; PID allein reicht nicht |
| Alter Owner | Kann neues Ticket weder stoppen noch eskalieren oder durch Nachricht freigeben |

Atomare Gruppenzuordnung, Nested-Mitgliedschaft und implizite Handlefreigabe
beim Crash sind ANNAHMEN des Fakes, keine echte Windows-Evidenz.
S wird im Fake nicht als Gruppenmitglied angelegt. J kann nur durch H mit
laufender eigener H-Instanz in konfigurierter, nicht gestoppter K erzeugt
werden. Das Fake-API übergibt dafür H KEINEN K-Handle, sondern die gehaltene
H-Instanz. Konfiguration bedeutet symbolisch erfolgreich geprüfte Schutzflags,
keine echte SetInformationJobObject-Ausführung oder gemessene Quotenwirkung.

begin/returnStart sind getrennt: der Start hält bis Rückkehr einen
Nutzungscounter. Eine Stop-/Terminateanforderung beendet weder den Aufruf noch
die Gruppe. Späterer Starterfolg wird weiter verbucht, bleibt suspendiert und
reaktiviert den Auftrag nicht. Ein bereits angeforderter Kill verhindert im
Fake bewusst NICHT jede spätere erfolgreiche Erzeugung; neue Endbelege sind
erforderlich. Kein Close bei ausstehender Nutzung, kein erneuter Close nach
UNCERTAIN/CLOSED. Kill-on-close ist nur eine gesonderte Anforderung bei wirklich
allen bestätigten Closes; ein unklarer Handle zählt nicht als geschlossen.

### IPC-Vertrag und Größenmodell

Opaque Kanäle S->H, H->S, H->W, W->H binden Ticket, Richtung und konkrete
Senderinstanz. accept prüft die registrierte Kanalidentität UND den berechtigten,
lebenden, identifizierten Sender, nicht nur eine Ticketnummer. HELLO/READY
müssen dem START vorausgehen; W gilt erst nach seinem STARTED als RUNNING.

Signaltypen: HELLO, READY, START, STARTED, STATUS, LEASE, STOP, STOP_ACCEPTED,
RESULT_END, RESULT_AVAILABLE, RESOURCES_CLOSED, ERROR.
Zusätzlich kleine typisierte Bodies ResultBegin, ResultChunk und GroupEnd.
GroupEnd ist der typisierte GROUP_END_EVIDENCE-Vertrag aus B-WATCHDOG-1.

Version 1, strikte Sequenz pro Kanal ab 1, richtige Phase/Richtung und vollständige
Längen prüfen. Alte Tickets werden ohne Wirkung auf einen neuen Auftrag
abgewiesen. Fremder Kanal trotz aktuellem Ticket, falscher Sender/Version,
Lücke/Replay, falsche Reihenfolge, unerlaubter Body/Typ, Teilframe und
unerwartetes EOF vergiften den aktuellen Auftrag: Stop plus Quarantäne.
Doppelte STOP-Anforderung mit NEUER gültiger Sequenz ist absichtlich idempotent;
derselbe Frame mit derselben Sequenz ist ein Replay. STOP_ACCEPTED ist kein
Gruppenende und keine Ressourcenfreigabe.

| Grenze im Modell | Bedeutung / Einschränkung |
|---|---|
| Steuerframe 4 KiB | Deklarierte positive vollständige Länge vor Bodywirkung |
| Datenchunk 64 KiB | Modellkandidat; tatsächliche Chunkbytes dürfen weder Envelope noch Restbudget übersteigen |
| Resultat 32 MiB insgesamt | BISHERIGER MESSKANDIDAT, keine als kompatibel abgenommene fachliche Grenze |
| Felder 30.032 / Tiefe 4 | Übernommener struktureller Vertragskandidat |
| Fake-Ergebnis | Nur Byte-/Feld-/Tiefenzähler; keine Payloadallokation, kein Parser oder Dateispeicher |

Unvollständige, übergroße oder widersprüchliche Resultate werden insgesamt
abgewiesen, nie gekürzt oder als Teilmap übernommen. Ergebnis nach Cleanup,
zweiter Header, vorzeitiges Ende oder falscher Sender liefern keinen Erfolg.
Ein vollständig gezähltes Resultat bleibt bis C und Journalabschluss intern.

Der Maximaltest zählt 32 MiB in 512 symbolischen Chunks ohne 32 MiB zu
allokieren. Das belegt Budgetarithmetik, NICHT Speicherbedarf, tatsächliche
UTF-8-/JSON-Längen, Streamingdecoder, Backpressure oder Kompatibilität aller
zulässigen Importmaps. Die historische theoretische Abschätzung von 131,7 MiB
und die Entscheidung über 32/160 MiB bleiben offen; keine neue produktive
Beschränkung oder stillschweigende Datenkürzung.

Der Fake vertraut einer injizierten vollständigen Envelope und ihren
Längenangaben; echte Fragmentreassemblierung, unbekannte rohe Typcodes,
Unicode-/Escapeencoding und Kanal-Capabilities müssen B-WATCHDOG-3 erst prüfen.
Die erfolgreiche Objekt-/Senderprüfung schützt NICHT nachgewiesen gegen
kompromittierten Same-Token-Code, Process-VM-/DUP_HANDLE-Zugriffe oder
Journalmanipulation. Windows-Token-/Prozess-/Dateirechte weiterhin offen.

### Endbarriere, Absturz und Quarantäne

Erfolg benötigt vollständiges Resultat, RESULT_AVAILABLE, rechtzeitiges
Workerende mit Exit 0, innere Endbelege am offenen J, dessen Ressourcenabschluss,
H-Ende, frischen äußeren Endbeleg am offenen K, sämtliche I/O-/Callback-/Native-/
Temporärressourcenbelege, alle Jobhandle-Closes und Journalabschluss.

Der Kernel-Fake hält reale Symbolzustände getrennt von API-Antworten:
- terminate zählt genau eine Anfrage pro Job; Prozesse bleiben bis zum separat
  injizierten end/endGroup-Ereignis aktiv. K-Eskalation ist niemals schon Ende.
- Beobachtung enthält Gruppencapability, Revision, vollständige Mitgliedsliste,
  Active- und Accountingzahlen. Falsche Gruppe, alte Revision, unvollständige/
  instabile Liste, widersprüchliches Accounting, aktive/unidentifizierte
  Instanzen oder ausstehendes Create verhindern Endbeweis.
- Rootende reicht bei noch lebenden dynamischen Nachfahren nicht.
- Fehler/Exit !=0 gibt selbst mit vorherigem Resultat keinen Erfolg.
  Bei vollständigem regulärem Cleanup ist IDLE für einen neuen expliziten
  Auftrag möglich, ohne automatischen Retry des fehlgeschlagenen Imports.
- H-Absturz schließt symbolisch Hs Handlekopien und fordert Kill-on-close an,
  beweist aber kein W-Ende. S kann K anfordern; Incident bleibt Quarantäne.
- Bei blockiertem H wird keine J-Kontrollausführung erfunden. Handlungsfähiges
  S darf unabhängig K anfordern; auch diese Anfrage kann ohne Endereignis bleiben.
- S-Absturz wird zuerst als Ausfall markiert, nicht als geordneter Stop.
  Kein erfundener J-Terminate vor Crash. Impliziter K-Close fordert Kill-on-close
  an, beendet im Fake aber nicht automatisch H/W. Zeitablauf allein garantiert
  nichts. Kein weiterer Kill aus der ausgefallenen S-Instanz.
- retireAfterIncident erlaubt bei frischem eigenen K-Endbeleg, beendeten
  Erzeugern/Instanzen und bestätigten übrigen Ressourcen nur das spätere
  Schließen eigener K-Ressourcen. Zustand und Journal bleiben unsicher;
  kein Resultat, kein neuer Slot und keine eigenmächtige Recovery.

Die I/O-Endkonjunktion ist ein ticketgebundener, vom Test injizierter
Observerbeleg mit einzelnen Komponenten: Reader, Writer, Kanäle, Callbacks,
native Ressourcen und temporäre Ressourcen. Keine Komponente darf fehlen.
Die konkrete Lebensdauer von Prozess-/Threadhandles, Attributspeichern und
echten Pipes wird hier nicht erneut nativ implementiert; sie bleibt zusätzlich
Gegenstand der unveränderten Adapterverträge und späterer OS-Abnahme.

Bei Abbruch noch VOR K-Erzeugung entsteht kein falscher Gruppenendbeleg:
das kleine Modell hat hierfür keinen eigenen No-resource-Abschlussweg.
Ohne solchen Beleg bleibt es konservativ gesperrt statt allein wegen fehlender
PIDs CLEAN zu behaupten. Ein späterer Verfügbarkeitsweg müsste die Nichterzeugung
aus der vollständigen eigenen Ressourcenbilanz explizit absichern.

### Recoveryjournal und Zeitvertrag

FakeRecoveryJournal hat UNKNOWN, CORRUPT, STALE, CLEAN, INTENT, UNCERTAIN,
COMPLETED. Nur ausdrücklich injizierte aktuelle CLEAN-Baseline desselben
Speichers und exklusiver Writer erlauben initialize. Ein neuer Modellkonstruktor,
bloßes COMPLETED, fehlende PIDs oder verlorene Handles erzeugen keine Baseline.

Intent muss bestätigt sein, bevor K oder H erzeugt werden. Gruppenschlüssel
K/J werden demselben Ticket gebunden. Abschluss prüft Writer, Ticket, exakte
Gruppen und aktuelle Revision. Fremde/späte Belege, unbestätigtes Intent oder
fehlerhafter Abschluss führen nicht zu IDLE. Der S-Ausfall verliert lediglich
den Writer und hinterlässt unsicheren Zustand; kein neuer sauberer Store als
automatischer Ersatz.

Der Journal-Fake schreibt keine Datei. Baseline, Revision und erfolgreiche
Speicheroperation sind Testannahmen, keine Durability-/Boot-/ACL-/Antireplay-
Garantie. Es gibt KEINE Reset-/manuelle Freigabe-API im Modell. Eine künftige
manuelle Recovery benötigt separat belastbaren Nachweis und Eigentümerentscheid.

Einzige Uhr: injizierte monotone Clock mit Sekunden und überlaufgeprüftem
advance. Keine Wanduhr, Sleeps oder realen Timer.
- t0 bei Slot-/Intentanforderung; Startdeadline t0+30 s einschließlich
  Vorbereitung, H-/W-Start und beider Handshakes.
- Banddeadline t0+120 s; Reihendeadline t0+3600 s, Start enthalten.
- Cleanupdeadline tc+15 s, bei erster endgültiger Entscheidung einmalig gesetzt.
  J-Anfrage, K-Eskalation und Abschluss teilen diese Frist. Kein neues Budget
  durch Stop, Lease, STATUS, Teilresultat oder späte Rückmeldung.
- Grenzwert now >= deadline gilt als abgelaufen.
- Leaseverlust nach 5 Fake-Sekunden ist ein zusätzlicher vorläufiger
  Abbruchkandidat aus B-WATCHDOG-1, keine neue Produktkonfiguration.
- Nach Cleanupfrist ohne C: Quarantäne. Ein späterer Endbeleg hebt sie nicht auf.

Alle Akteure verwenden im sequenziellen Test dieselbe injizierte Fake-Uhr.
Das ist ausdrücklich KEIN Entwurf, System.nanoTime-Werte echter JVMs direkt
zu vergleichen. Verzögerte IPC und unabhängige reale Uhren benötigen eigene
Nachweise. Ein hängender Kernelaufruf wird als ausbleibendes Ereignis modelliert,
nicht mit einem wirklich nicht abbrechbaren Thread nachgestellt.

### Tatsächlich ausgeführte Prüfungen

Java 21, Gradle 8.14.3, ausschließlich --offline --no-daemon; die geprüften
JVM-Overridevariablen JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/GRADLE_OPTS
waren nicht gesetzt. Keine Werte anderer Umgebungsvariablen ausgegeben.

Vor JEDEM tatsächlichen Testlauf identischer --dry-run mit denselben Filtern.
Graph ausschließlich compileJava/processResources/classes/compileTestJava/
processTestResources/testClasses/test. Kein JavaExec, Jobprobe-, Manual-,
Boot-/JPA-/Integrations- oder Volltesttask.

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.watchdogmodel.WatchdogModelTest' --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.watchdogmodel.WatchdogModelTest' --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| WatchdogModelTest, neu | 85 | 0 | 0 | 0 |
| OwnedJobAdapterTest, unverändert | 45 | 0 | 0 | 0 |
| SupervisorModelTest, unverändert | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest, unverändert | 39 | 0 | 0 | 0 |
| Tatsächlich final ausgeführt | **204** | **0** | **0** | **0** |

Finaler Lauf BUILD SUCCESSFUL. Frühere Entwicklungsstände nicht addieren:
zunächst 71 neue +119 alte grün; ein Zwischenlauf mit 83 neuen +119 alten
hatte EINEN Testhelferfehler. Nach absichtlich unklarem K-Close wollte der
Testhelfer erneut observe auf diesem Handle ausführen, was der Fake korrekt
verbot. Test prüft diese Abweisung jetzt ausdrücklich und verwendet für den
danach ohnehin unzulässigen Abschluss keinen neuen Query. Keine Sicherheits-
assertion gelockert. Erweiterter 85+119-Lauf und finaler Lauf nach Präzisierung
der S-Crash-Semantik grün.

Erster Dry-Run scheiterte vor Gradle am Cache-Lockzugriff der Sandbox.
Wiederholung mit freigegebenem Zugriff auf denselben vorhandenen lokalen Cache
erfolgreich; keine Downloads/Cacheverlagerung oder neue Dependency.
Keine Datenbank-, Spring-, Browser-, Worker- oder Kontrollhostinstanz gestartet;
nur normale Gradle-/JUnit-Prüfharness-JVMs.

Die bisherigen **278 Testfälle und sämtliche Assertions bleiben unverändert**:
119 in diesem Auftrag erneut ausgeführt. Die übrigen 159 Fälle
(Win64BindingsTest 28, Win64ProcessBindingsTest 61, Win64NativeBindingsTest 44,
Win64ConcurrencyTest 26) bewusst NICHT erneut ausgeführt: ihre vorhandenen
Layout-/Memoryprüfungen verwenden JNA samt nativen Dispatchoperationen; der
aktuelle Auftrag verbietet echte JNA-Aktionen. Ihr historischer grüner Stand
wird nicht als neuer Lauf ausgegeben. Gemeinsame spätere Ausführung bleibt mit
vorhandenem jobAdapterUnitTest plus dem obigen Modellfilter möglich, benötigt
aber eine dazu passende Freigabe der unveränderten JNA-Memory-/Layoutprüfungen.
Keine Behauptung „363 Tests frisch bestanden“ aus 85 neuen plus 278 historischen.

Vorher-/Nachher-SHA-Prüfung umfasst bestehende Quellen/Tests, Builddatei,
Manual-Quellen und vorhandenes Manual-JAR, Skripte/Prototypen und historische
STOP-/consumed-/trace-Dateien. Historische Dokumentpräfixe bytegleich erhalten;
nur vier neue Testdateien und Nachträge TESTING.md/ROADMAP.md. Finale Details
der Integritäts-/Whitespaceprüfung im Abschlussbericht. Keine Git-Befehle.

### Offene Nachweise und eng begrenzter B-WATCHDOG-3-Vorschlag

Keine OS-Nesting-/Handle-/Kill-on-close-/Quoten-/Crash-/Kernelzeitgarantie,
keine authentisierte Windows-IPC, keine Journal-Durability-/Boot-/Aktualitäts-
quelle und keine Same-Token-Workerisolation bewiesen. Endnachweise bleiben
Modellannahmen. Keine Freigabe des suspendierten Einzelversuchs, keine
Produktintegration, keine SSRF-/T03-/T04-/T06-Arbeiten.

Nächster Vorschlag nur nach gesonderter Freigabe:
**B-WATCHDOG-3A: in-process Codec-/Kommunikations-Harness mit begrenzten
Bytepuffern**, ohne zusätzliche JVM, Win32, echte Pipes oder Dateien.
Feste V1-Wire-Repräsentation, Richtungs-/Instanzbindung, Byte-vor-Allokation,
fragmentierte Frames, EOF/Replay/Unicode/Überläufe, Ergebnis-/Diagnosebudgets
und deterministischer Backpressure-/Abbruchscheduler. Bestehende Modelle
unverändert als Orakel verwenden. Keine neuen Dependencies oder Produktpfade.
Erst separat B-WATCHDOG-3B über echte lokale IPC/Host-JVMs entscheiden, mit
festen Prozess-/Zeit-/Outputgrenzen und vorher nachgewiesenem eigenem Shutdown.
Ein solcher Prozessstart ist NICHT durch 3A oder diesen Auftrag freigegeben.

Historische Freigaben/STOPs/Evidenzen unverändert. Keine Win32-/JNA-/Job-/
synthetischen OS-Prozessversuche, Worker-/Host-/HTTP-/Browser-/Selenium-/
Geckostarts, DB/SQL/Migrationen, Installationen/Downloads oder Git-Operationen.


## T02 – B-WATCHDOG-3A: binärer Bytecodec und Speicherharness (24.10.2026)

### Umfang, unveränderte Grundlage und Dateien

Ausschließlich prozessinterner Testcode, kein ausführbarer H, kein echter
IPC-Kanal und keine produktive Integration. B-WATCHDOG-2 bleibt unverändert
das Lifecycle-/Admission-/Recoveryorakel. Erster suspendierter OS-Versuch
weiterhin GESPERRT.

Sieben neue Dateien in src/test/java/de/mangalib/watchdogmodel:
- BinaryIpcCodec.java: expliziter V1-Codec, inkrementeller Decoder, UTF-8-Prüfung,
  begrenzter Encoder und explizite Streamingempfänger.
- BufferBudget.java: globale und kanalbezogene Reservierungen mit Peakzählern.
- MemoryIpcLink.java: gerichteter fester Ring, ein Encoder, gesteuerte Schritte,
  Half-close, EOF und vollständiger Abbruch.
- WireIdentityRegistry.java: begrenzte eindeutige Wire-/Capabilityzuordnung.
- WatchdogWireBridge.java: Zuordnung dekodierter Nachrichten zum vorhandenen
  WatchdogModel.accept; keine zweite Lifecyclezustandsmaschine.
- BinaryIpcCodecTest.java und MemoryIpcLinkTest.java: kleine deterministische
  Codec-/Transport-/Modellkopplungstests.

Keine vorhandenen Java-/Modell-/Testquellen, Builddateien oder Dependencies
geändert. Insbesondere die 85 Watchdog- und 119 bisherigen Modell-/Adapter-
Fakefälle samt Assertions unverändert; alle 204 erneut ausgeführt.
Die 159 JNA-Fälle bleiben unverändert und wurden nicht ausgeführt.

### Tatsächliches V1-Wireformat

Feste Byte-Reihenfolge BIG_ENDIAN, keine Java-Serialisierung, Klassennamen-
auflösung, JSON-Objektdeserialisierung oder impliziten Enum-Ordinals als Typcode.

| Offset | Bytes | Inhalt |
|---:|---:|---|
| 0 | 4 | Magic 0x4D4C4950, ASCII MLIP |
| 4 | 2 | Unsigned Protokollversion, aktuell 1 |
| 6 | 2 | Unsigned expliziter Nachrichtencode |
| 8 | 8 | Signed Payload-Bytezahl, negative/zu große Werte verboten |
| 16 | 8 | Positive Sequenznummer |
| 24 | 16 | Vollständige 128-Bit-Sessionidentität |
| 40 | 16 | Vollständige 128-Bit-Ticketidentität |
| 56 | 16 | Vollständige 128-Bit-Kanalidentität |
| 72 | 16 | Vollständige 128-Bit-Senderinstanzidentität |
| 88 | 1 | Richtung: S_H=1, H_S=2, H_W=3, W_H=4 |
| 89 | 7 | Reserviert, zwingend null |
| 96 | variabel | Typspezifische Payload |

Header immer 96 Byte. Nullidentitäten und ungültige Richtungen/Sequenzen
werden abgewiesen. Framegröße = 96 + geprüfte Payloadlänge. Vor einer
Längenumwandlung gilt payload <= konfiguriertes Frame-Limit - 96; keine
ungeprüfte Addition oder nachträgliche große Payloadallokation.

Codes 1..12 sind ausdrücklich HELLO, READY, START, STARTED, STATUS, LEASE,
STOP, STOP_ACCEPTED, RESULT_END, RESULT_AVAILABLE, RESOURCES_CLOSED, ERROR.
Deren Payload ist ein begrenzter UTF-8-Text (auch leer); die Bridge übernimmt
nur den bisherigen Signaltyp. Text ist keine zusätzliche Autorisierung oder
Shell-/Geschäftsanweisung und wird nicht als interne Fehlermeldung ausgegeben.

Zusätzliche bestehende Body-Verträge:
- 20 RESULT_BEGIN: genau 16 Byte, Ergebnisbytes als int64, Felder int32,
  Tiefe int32; gegen injizierte Ergebnis-/Feld-/Tiefengrenzen prüfen.
- 21 RESULT_DATA: positiver UTF-8-Bytechunk. Encoderquelle ist ein unveränderlicher
  String; Decoder liefert die tatsächlichen Bytes inkrementell an den expliziten
  Sink und nach vollständigem gültigem Frame DataCount an die Modellbridge.
- 22 GROUP_END_EVIDENCE: genau 37 Byte; Gruppen-ID 16, Revision 8, Flags 1
  (complete/stable), Active 4, Accounting 4, Membercount 4. Als Endbehauptung
  ist nur Membercount=0 vorgesehen; nichtleere Diagnosepopulationen sind KEIN
  Endframe. Flags-/Zahlen-/Identitätsfehler kontrolliert ablehnen. Das Modell
  prüft zusätzlich gegen seine wirkliche symbolische Gruppenpopulation.

Keine vollständigen Gruppen-/Prozessobjekte werden deserialisiert.
Die Gruppen-ID wird nur auf die vorher gebundene eigene J-Capability abgebildet;
falsche Gruppe, Revision oder aktive Population kann dadurch nicht als Ende gelten.

### UTF-8, Fragmentierung und dauerhaft fehlerhafte Sitzungen

Der Decoder verarbeitet Header und Payload über beliebige feed-Grenzen:
einzelne Bytes, mitten in UTF-8-Sequenzen, mehrere Frames im Block und
Framegrenzen innerhalb eines Blocks. Leere Zuführung verändert nichts.

UTF-8 ist strikt: keine Overlongformen, isolierten Continuations, Surrogate,
Werte über U+10FFFF oder unvollständigen Sequenzen. Erst danach darf ein
Kontrolltext als String entstehen. Der Encoder weist ungültige UTF-16-
Surrogatfolgen vor eigener Allokation ab. Datenframes müssen jeweils selbst
gültige UTF-8-Segmente bilden; nur TRANSPORTfragmente dürfen Zeichen teilen.
Ein späterer Workerencoder muss Chunkgrenzen daher an UTF-8-Grenzen wählen.

Streaming ist explizit, kein stiller Datenverlust:
- Ohne implementierten Sink.dataByte wird ein Datenframe abgewiesen.
- Bytes vor vollständiger UTF-8-/Frame-/Lifecycleprüfung sind vorläufig.
  Sink.accept bestätigt den Frame; Fehler/Abbruch löst discard aus.
- Ein Consumer darf vorläufige Daten nicht als fachlich erfolgreich übernehmen.
  Ein fehlgeschlagener Consumer führt zu terminalem Fehler, keinem Replay.
- Die Bytecodec-Tests prüfen tatsächlich identische empfangene UTF-8-Bytes.
  Die Lifecyclefixtures verwenden dagegen ausdrücklich einen Test-Drain.
  Dieser ist KEIN produktiver Resultatspeicher, Importparser oder Datenimport.

EOF mitten im Header/Payload erzeugt TRUNCATED. Unknown Type, Version, Header,
UTF-8, Längenfehler oder Übergabefehler vergiften den Decoder dauerhaft;
weitere Bytes konsumiert er nicht. Kein Reset durch später gültige Frames.
Diagnosen bestehen aus festen Fehlercodes, nicht Rohpayload, Exceptiontexten
oder unbegrenzter Fehlerhistorie. Auch Sink-/Endcallbackfehler geben Reserven frei.

### Speichergrenzen und Backpressure

BufferBudget begrenzt pro Account und global. Alle Reservierungen verwenden
Subtraktion vor Addition, Freigaben sind idempotent. Konfiguration ist lokal
vertrauenswürdig, niemals eine unkontrollierte Grenze aus einem Peerframe.

Für jeden gerichteten MemoryIpcLink:
- P = fester Ringpuffer, vor Allokation reserviert.
- C = controlFrameLimit - 96.
- D = 96 + 3*C: Decoderreserve für Header, Kontrollbytearray und konservativ
  bis 2*C gehaltene Kontrolltextzeichenbytes. DATA bekommt KEIN Vollframearray.
- E = 96 + F + 2*L: höchstens ein Encoder, F=0/16/37 feste Binärpayloadbytes,
  L=UTF-16-Zeichenanzahl der während des Sendens gehaltenen Stringquelle.
  Der Encoder produziert UTF-8 direkt in den Ring, kein vollständiges
  UTF-8-Zwischenarray. Der String wird nicht nochmals kopiert.
- Reservierte Transportbytes pro Kanal <= D+P+E <= Kanalaccount-Limit.
  Summe aller Accounts <= globale Grenze G. Ein leerer Sender gibt E frei;
  EOF/Abbruch/Close geben auch D/P frei.

Die Tests verwenden überwiegend controlFrame=160, dataFrame=512, Ringgrößen
3/7/11/17 Byte und kleine globale Grenzen; die integrierte Vier-Richtungs-
Fixture hat G=40.000 Byte, Kanalaccount jeweils 10.000 Byte und P=17.
Ihr D+P beträgt 305 Byte je Richtung. Ein 300-Byte-Datenframe wird nachweislich
durch einen Ring von 3 Byte geleitet, ohne Decoder-Vollframepuffer.
Die globalen und lokalen Peak-/Freigabezähler werden unabhängig geprüft.

Backpressure:
- send behält höchstens einen Encoder; zweiter Sendewunsch liefert false,
  ohne Queueeintrag oder automatische Wiederholung.
- Volle Ringe und fehlende Reserve stoppen Fortschritt sofort und nicht
  blockierend. Nur explizite writeStep/readStep bewegen Bytes.
- Neue Frames können nur die bereits begrenzten Ringbytes und einen Sender
  belegen; es gibt keine unbeschränkte Nachrichtenliste.
- Half-close verbietet neue Sends, lässt den bestehenden Sender/Ring auslaufen.
  Die Gegenrichtung hat ihren eigenen Link und wird nicht implizit geschlossen.
- Voller Abbruch verwirft vorläufige Daten, löst die Reserven und meldet genau
  einmal Kanalende. Kein dauerhaft blockierender Testthread vorhanden.

WICHTIGE Nachweisgrenzen:
Das ist ein Budget eigener gleichzeitig gehaltener Bytepuffer/Zeichenquellen,
KEINE harte JVM-Heap-/GC-/Metaspace-/Objektoverheadgarantie. Der Registryumfang
ist separat begrenzt (Fixture: höchstens 64 Einträge); keine unbegrenzte
Kanal-/Ticketzuordnung. Caller-eigene Eingabestrings existieren bereits vor
Encoder.open und werden dort nur für ihre Haltezeit berechnet. Ein externer
Sink kann Speicher halten; dessen fachlicher Resultatspeicher muss später
EIGENSTÄNDIG budgetiert werden. Die Testbeobachter sind klein und begrenzt,
der gekoppelte Test-Drain hält keine Payload.

Ein Größenlimit allein garantiert nicht, dass ein Frame in JEDE kleinere
Speicherkonfiguration passt: Der Encoder hält die vollständige unveränderliche
Stringquelle, jedoch kein zweites großes Bytearray. Ist D+P+E dauerhaft größer
als die Kanalquote, bleibt send=false. Es gibt keinen blockierenden Retry.
Für garantierte Annahme maximaler Chunks müssen Quote und G passend gewählt
oder kleinere UTF-8-Chunks erzeugt werden; G muss außerdem die Fixreserven
aller gleichzeitigen Links berücksichtigen. Ein noch weitergehender pull-
basierter Eingang ohne gehaltenen Vollchunk ist nicht implementiert.
Keine Quotaerhöhung oder Produktgrenze wurde deswegen eigenmächtig vorgenommen.

### Ergebnisgrößen und unveränderte Lifecyclebindung

Limits konfiguriert Kontroll-/Datenframe, Ergebnisgesamtgröße, Felder und Tiefe.
Defaults entsprechen nur den bisherigen Kandidaten 4 KiB / 64 KiB / 32 MiB /
30.032 / 4. Rahmenheader zählt bei den Framegrenzen mit; netto bleiben bei
64 KiB maximal 65.440 Datenbytes. Keine 32-MiB-Testallokation.

Ein Test kodiert/dekodiert nur die 112-Byte-Deklaration eines 64-MiB-Ergebnisses
bei injizierter 160-MiB-Ergebnisgrenze. Das belegt konfigurierbare Arithmetik,
NICHT Kompatibilität oder tatsächlichen Heapbedarf eines solchen Imports.
Das unveränderte WatchdogModel behält weiterhin seinen 32-MiB-Kandidaten;
allein die Codec-Konfiguration hebt diesen nicht auf. 131,7-MiB-Abschätzung,
endgültige Ergebnisgrenze und ein budgetierter Mapdecoder bleiben offen.

WatchdogWireBridge ist nur Zuordnung und Delegation:
- Session-/Ticket-/Kanal-/Sender-IDs werden vorab auf opaque Modellcapabilities
  registriert, nicht aus Peerbehauptungen als neue Berechtigung rekonstruiert.
  Die begrenzte Registry verhindert Wiederbindung desselben Werts an eine
  andere Capability und einer Capability an mehrere IDs innerhalb ihrer Domäne.
- Der lokale tatsächliche Sender stammt vom Endpunkt, nicht aus der Nachricht.
  Korrekte Wire-Sender-ID allein ersetzt diesen nicht.
- Modell prüft wie zuvor Version/Ticket/Kanal/Sender/Sequenz/Richtung/Zustand.
  Codec formal gültig bedeutet ausdrücklich NICHT fachlich zulässig.
- RESULT_BEGIN-Grenze plus bisheriges Modell-Restbudget verhindern kumulative
  Datenüberschreitung. Keine zweite Resultat-/Admission-/Recoveryzustandsmaschine.
- Alte, doppelte, übersprungene oder zur Phase unpassende Nachrichten,
  Erfolgsantwort nach Stop und Resultate nach Gruppenabbruch geben keine Gruppe frei.
- Über Codec-/Sinkfehler geschlossener aktiver Link meldet ein EOF-Ereignis an
  dasselbe Modell. Ein ungültiges Ticket allein bewirkt dort keine Freigabe.

Echte Peer-Authentisierung, Zufalls-/Bootstrapgeheimnisverteilung und sichere
Windows-Prozess-/Tokenbindung sind NICHT implementiert. Registry und IDs sind
injizierte Testzuordnungen, keine kryptografische Sicherheitsgrenze.
Keine Absicherung gegen kompromittierten uneingeschränkten Same-Token-Worker
aus der erfolgreichen Fakebindung ableiten.

**Offene geordnete EOF-Semantik:** B-WATCHDOG-2 behandelt EOF eines aktiven
Kanals immer konservativ als Protokollfehler. Das wurde nicht geändert und
auch nicht durch eine zweite EOF-Lifecycleentscheidung in der Bridge umgangen.
Geordnete HELLO/READY/START/Resultat-/Gruppenend-/Ressourcennachrichten sind
gekoppelt geprüft. Ein kompletter realer Erfolg MIT geschlossenen aktiven
Bytekanälen ist damit noch nicht abgenommen: Kanäle offen -> I/O-Barriere nicht
vollständig; EOF -> Quarantäne. Die Tests erfinden deshalb KEINEN positiven
Gesamtabschluss. Vor ausführbarer Hostkommunikation muss ein enger Vertrag für
erwartetes vs. unerwartetes EOF samt eigenem Endbeleg ausdrücklich entschieden,
separat umgesetzt und getestet werden. Sauberes Byte-EOF, Workerexit oder
dekodiertes SUCCESS allein sind weiterhin niemals C oder Recoveryfreigabe.

### Tatsächlich ausgeführte Tests und Integrität

Ausschließlich Java 21 / Gradle 8.14.3, --offline --no-daemon. Vor jedem
Testlauf identischer --dry-run. Graph nur compileJava/processResources/classes/
compileTestJava/processTestResources/testClasses/test. Keine JavaExec-/Probe-/
Boot-/Manual-/JPA-/Integrationstasks. JVM-Overridevariablen
JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/GRADLE_OPTS waren nicht gesetzt.

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.watchdogmodel.BinaryIpcCodecTest' --tests 'de.mangalib.watchdogmodel.MemoryIpcLinkTest' --tests 'de.mangalib.watchdogmodel.WatchdogModelTest' --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest' --dry-run
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.watchdogmodel.BinaryIpcCodecTest' --tests 'de.mangalib.watchdogmodel.MemoryIpcLinkTest' --tests 'de.mangalib.watchdogmodel.WatchdogModelTest' --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| BinaryIpcCodecTest, neu | 63 | 0 | 0 | 0 |
| MemoryIpcLinkTest, neu | 29 | 0 | 0 | 0 |
| WatchdogModelTest, unverändert | 85 | 0 | 0 | 0 |
| OwnedJobAdapterTest, unverändert | 45 | 0 | 0 | 0 |
| SupervisorModelTest, unverändert | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest, unverändert | 39 | 0 | 0 | 0 |
| Final gesamt | **296** | **0** | **0** | **0** |

92 neue + sämtliche 204 zuvor freigegebenen Fälle frisch erfolgreich.
Entwicklungsläufe mit zunächst 83 und danach 90 neuen Fällen ebenfalls grün;
nicht addieren. Keine bestehende Assertion angepasst oder abgeschwächt.
159 JNA-Binding-/Memory-/Layoutfälle nicht ausgeführt, Quellen unverändert.
Normale Gradle-/JUnit-Prüfharness-JVMs sind die einzigen ausgeführten JVMs;
kein zusätzlicher Kommunikationshost oder Worker gestartet.

Vorher-/Nachher-Hashes erfassen bestehende Quellen/Tests, Build-/Manual-Dateien,
Manual-JAR, Skripte/Prototypen und STOP-/consumed-/trace-Evidenzen.
Nur sieben neue Testdateien sowie dieser Nachtrag und der Roadmapnachtrag.
Historische Dokumentpräfixe bytegleich erhalten. Haupt-/Manual-Quellen,
Paketierung und Artefakt unverändert; test.output gelangt nicht in main oder
manual-test. Kein neuer produktiver/nativer/Live-Startpfad, keine Buildänderung
erforderlich. Whitespace-/Konfliktmarkerprüfung ohne Git; Abschluss im Bericht.

### Eng begrenzter B-WATCHDOG-3B-Vorschlag – nicht freigegeben

Zuerst die oben genannte EOF-/Abschlussentscheidung als kleine reine
Vertragspräzisierung vorlegen; nicht beim Hoststart improvisieren.

Danach separat freizugebender Kommunikationshost:
- Genau eine kleine synthetische Java-21-Echo-/Protokollhost-JVM aus festem
  lokalem Artefakt, keine weiteren Kinder, keine Win32-/JNA-/Job-/Workerstarts.
- Parent hält genau das selbst erzeugte Process-Objekt; kein PID-/Namensscan.
  Reduzierte Umgebung ohne DB-Passwörter/JVM-Overrides, feste EXE/CWD, keine Shell.
- Nur dedizierte stdin/stdout/stderr-Kanäle, dieselben Wire-/Framing-/Budgetregeln.
  Feste geringe Start-/Gesamtzeit-, Byte- und Heap-Testobergrenzen VOR Freigabe
  vereinbaren. Kein Socket/HTTP/Browser/DB/Scraper, keine neue Dependency.
- Geordneter Exit/EOF und Cleanupnachweis müssen vor dem Start technisch
  reviewbar sein. Kein unfreigegebenes destroyForcibly/Prozesskill als
  nachträgliche Rettung und keine absichtlich nicht beendbare Nativeblockade.
  Ist sicherer begrenzter Endweg unklar, Host nicht starten.
- Prozess-/Peerbindung und Pipeverhalten separat messen; Same-Token-Isolation,
  Journaldurability, K/J-Nesting und spätere Workerarchitektur bleiben offen.

Dieser Auftrag autorisiert KEINE dieser Prozess-/IPC-Stufen. Keine
Win32-/JNA-Aufrufe, DB/SQL/Migrationen, Anwendung/HTTP, Browser/Selenium/Gecko/
Scraper, Downloads/Installationen oder Git-Operationen. SSRF/T03/T04/T06 und
fachliche Muss-/Soll-/Kann-Prioritäten unverändert. Suspendierter OS-Versuch
weiterhin ausdrücklich GESPERRT.

## T02 – B-WATCHDOG-3B-1: Geordneter Kanalabschluss im Speichermodell (24.10.2026)

Dieser Nachtrag präzisiert ausschließlich den zuvor offenen EOF-Vertrag aus
B-WATCHDOG-3A. Die historischen Berichte darüber bleiben unverändert gültige
Beschreibungen ihres damaligen Stands. Kein ausführbarer Kommunikationshost,
keine echte IPC, kein Prozess-/Windows-Versuch und keine Produktivänderung.

### Abschlussvertrag und Reihenfolge

Im vorhandenen WatchdogModel gibt es pro Richtung die Kommunikationsphasen
OPEN → END_EXPECTED → EOF_CONFIRMED. Sie ersetzen keinen Lifecyclezustand.
CHANNEL_END kündigt das Ende genau dieser Richtung an; erst ein danach vom
Decoder bestätigtes EOF an einer Framegrenze bestätigt deren Kanalende.

1. W→H: RESULT_BEGIN, alle Ergebnisbytes und RESULT_END müssen vollständig und
   innerhalb der vorhandenen Budgets angenommen sein. CHANNEL_END darf erst
   danach im RUNNING-Zustand vom weiterhin identifizierten lebenden Worker
   kommen. Sein EOF beweist keine Prozessbeendigung.
2. H→W: CHANNEL_END erst nach bestätigtem W→H-EOF, ebenfalls noch RUNNING;
   anschließend eigenes EOF. Ein offener Gegenkanal bleibt offen, kein
   implizites Schließen beider Richtungen.
3. H→S: RESULT_AVAILABLE muss noch vor Workerexit/Beginnen des Cleanup angenommen
   sein. Workerexit 0 ist nur mit vollständig angenommenem Ergebnis und dieser
   Bestätigung erfolgreich; der erste Exitcallback bleibt maßgeblich.
4. Im CLEANING-Zustand folgen der unabhängig überprüfte J-Endbeleg und
   RESOURCES_CLOSED. Beide Workerkanäle müssen bereits ihr eigenes EOF haben.
5. S→H darf jetzt CHANNEL_END und EOF liefern. Danach darf H→S CHANNEL_END und
   EOF liefern. Diese Reihenfolge ist der explizite Normalabschlussvertrag,
   kein zusätzlicher Job-End-ACK und keine zweite Lifecyclemaschine.
6. H-Ende, vollständiger unabhängiger K-Endbeleg, sämtliche IoEnd-Komponenten,
   geschlossene owned Handles und erfolgreicher Journalabschluss bleiben
   zusätzlich erforderlich. Erst dann IDLE und Ergebnisfreigabe.

Ein an die WireBridge angeschlossenes Modell verlangt alle vier Kanalbelege
zusätzlich zum bestehenden vollständigen IoEnd-Observer. Ein vom Test gesetztes
IoEnd.channels=true ersetzt keine fehlenden Wire-Belege. Die älteren reinen
Lifecycletests verwenden weiterhin ihren expliziten abstrakten IoEnd-Observer;
deren Assertions und Quellen wurden nicht geändert. Schon ein direkt angenommener
CHANNEL_END aktiviert ebenfalls die stärkere Kanalbarriere.

Keine neue Cleanupfrist: Die bisherigen 15 Sekunden ab erstem Cleanupbeginn
bleiben unverändert, ebenso Start-, Operations- und Leasegrenzen. Frühes EOF,
trunkierter Frame, Codec-/Sinkfehler oder Abbruch sind kein Normalabschluss.
Quarantäne wird durch spätere EOFs niemals aufgehoben. Doppelte Abschlussframes,
weitere Nachrichten nach CHANNEL_END, falsche Bindungen oder Sequenzen werden
abgewiesen. Nach IDLE werden alte Ereignisse ignoriert; ein neues Ticket erbt
keine Kanalbelege. Keine automatische Wiederholung oder Recovery.

### Wireprofil und Transportende

96-Byte-V1-Header und Codes 1–12/20–22 bleiben unverändert. Explizite Erweiterung
des V1-Abschlussprofils: **Code 23 = CHANNEL_END, Payloadlänge exakt 0**.
Encoder und Decoder lehnen jede nichtleere Payload dieses Typs ab.
Das ist KEINE behauptete Mischkompatibilität mit dem früheren V1-Entwurf:
alte V1-Peers lehnen Code 23 kontrolliert ab. Ein späterer ausführbarer Host
muss identische Abschlussprofile voraussetzen oder vor Admission eine separat
geprüfte Capability-/Versionsvereinbarung erhalten. Die Header-Versionszahl
allein genügt nicht zur Erkennung dieses Profils; derzeit existiert kein
ausführbarer Peer. Keine stille produktive Wireformatumstellung.

MemoryIpcLink meldet seinen terminalen Decoder-/Abbruchgrund typisiert an die
Bridge. Nur ein fehlerfreies Boundary-EOF hat keinen Fehlergrund. TRUNCATED,
ABORTED und andere Fehler dürfen nicht als reguläres EOF umgedeutet werden.
Die Entscheidung bleibt im WatchdogModel. Fehler sind terminal, nach geschlossenem
Link werden zusätzliche Bytes und send abgelehnt; Reservierungen werden freigegeben.
UTF-8-Prüfung, inkrementeller Decoder, feste Ringe und Budgets/Backpressure bleiben
erhalten. Ergebnisbytes sind weiterhin nur ein begrenzter Transportnachweis;
ein fachlicher Importmap-Parser/Consumer ist dadurch nicht implementiert.

### Tests und minimale Anpassungen historischer Testhilfen

Neue WatchdogClosureTest: 41 deterministische Fälle für vollständigen Erfolg,
noch lebende Gruppen trotz Kanalende, fehlende J-/K-Belege, fehlendes EOF,
alle sechs IoEnd-Komponenten, Handle-/Journalfehler, frühes EOF während Handshake,
Betrieb und Ergebnisübertragung, partielle Header/Payloads nach Abschlussmarker,
Duplikate, späte Frames, falsche Identitäten/Sequenzen, Fehler/Abbruch plus EOF,
Cleanupfrist, Ownerverlust, unveränderlichen Exitbefund und alte Tickets.

Bestehende Tests wurden nicht abgeschwächt:
- BinaryIpcCodecTest prüft alle bisherigen zwölf Signaltypen weiterhin mit
  demselben Unicode-Inhalt. Der neue dreizehnte Signaltyp bekommt vertragsgemäß
  leere Payload: ein zusätzlicher Testfall.
- Der Unbekannt-Typ-Vektor **23 → 24** ist die einzig erforderliche Änderung
  einer historischen Ablehnungseingabe: 23 ist jetzt ausdrücklich belegt;
  die TYPE-Ablehnungsassertion bleibt unverändert, ebenso die übrigen Vektoren.
- MemoryIpcLinkTest: ausschließlich Anpassung der Rückrufsignaturen/Adapter an
  den typisierten Endgrund. Sämtliche bisherigen Assertions bleiben erhalten,
  insbesondere führen Boundary-EOFs OHNE CHANNEL_END weiterhin zur Quarantäne.
- WatchdogModelTest und die drei früheren Modell-/Adaptertestklassen bytegleich.
  Die 159 älteren JNA-Tests unverändert und nicht ausgeführt.

Nur Java 21, Gradle 8.14.3, --offline --no-daemon. Vor JEDEM tatsächlichen
Testlauf derselbe unten stehende Aufruf zusätzlich mit --dry-run. Geprüfter Graph:
compileJava/processResources/classes/compileTestJava/processTestResources/
testClasses/test; keine Boot-/Manual-/Native-/JavaExec-/Integrationstasks.
JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/GRADLE_OPTS nicht gesetzt.

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon test --rerun --tests 'de.mangalib.watchdogmodel.BinaryIpcCodecTest' --tests 'de.mangalib.watchdogmodel.MemoryIpcLinkTest' --tests 'de.mangalib.watchdogmodel.WatchdogClosureTest' --tests 'de.mangalib.watchdogmodel.WatchdogModelTest' --tests 'de.mangalib.windowsadapter.OwnedJobAdapterTest' --tests 'de.mangalib.scrapermodel.SupervisorModelTest' --tests 'de.mangalib.scrapermodel.IpcBudgetModelTest'
~~~

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| BinaryIpcCodecTest | 64 | 0 | 0 | 0 |
| MemoryIpcLinkTest | 29 | 0 | 0 | 0 |
| WatchdogClosureTest, neu | 41 | 0 | 0 | 0 |
| WatchdogModelTest | 85 | 0 | 0 | 0 |
| OwnedJobAdapterTest | 45 | 0 | 0 | 0 |
| SupervisorModelTest | 35 | 0 | 0 | 0 |
| IpcBudgetModelTest | 39 | 0 | 0 | 0 |
| **Gesamt** | **338** | **0** | **0** | **0** |

Alle bisherigen 296 Fälle erneut geprüft, mit der oben begründeten Änderung des
Unbekannt-Typ-Vektors; 42 zusätzliche Fälle. Entwicklungsläufe nicht addieren:
zunächst Kompilierungsabbruch wegen einer falsch angepassten Lambda-Testhilfe,
danach 338 Fälle mit einem fehlerhaften neuen Crash-Test. Dieser versuchte nach
simuliertem Supervisorverlust über das verlorene Handle zu beobachten. Korrektur:
vorher erfassten Beleg verwenden und unverändert die verweigerte Freigabe prüfen.
Der abschließende vollständige gefilterte Wiederholungslauf war erfolgreich.

Vorher-/Nachher-Hashes und Dokumentpräfixprüfung ersetzen die ausdrücklich
verbotenen Git-Prüfungen. Nur sechs vorhandene Modell-/Codec-/Harnesstestdateien,
die neue WatchdogClosureTest und zwei Dokumentnachträge betroffen. Haupt-/Manual-
Quellen, Build-/Paketierung, vorhandenes Manual-JAR und historische STOP-/Trace-/
Freigabeevidenzen bleiben unverändert. Keine Guards oder Securityfilter geändert.

### Nächster, noch nicht freigegebener Block: B-WATCHDOG-3B-2

Eng begrenzt: genau ein isolierter synthetischer Java-Kommunikationshost mit
festem lokalem Artefakt, dedizierten stdin/stdout/stderr-Kanälen und demselben
expliziten Abschlussprofil. Keine Jobs, Worker, Win32/JNA, Browser oder Scraper.
Parent bindet nur sein selbst erzeugtes Process-Objekt, feste EXE/CWD und eine
reduzierte Umgebung ohne DB-Passwörter/JVM-Overrides. Vor Ausführung Start-/Gesamt-
und Cleanupzeiten, Heap-/Bytebudgets sowie einen sicher begrenzten geordneten
Shutdown und Fehlerendweg verbindlich festlegen; keine nachträgliche Kill-
Improvisation. EOF, Prozessende, Reader-/Writer-/Callback-Ende getrennt prüfen.
Kein Socket/HTTP, kein echter Import und keine zusätzliche Dependency.

Nicht bewiesen bleiben reale IPC-/Peer-Authentisierung, Windows-Prozess-/Token-
bindung, Same-Token-Isolation, Journaldurability, verschachtelte Jobs und echtes
Prozess-/Handlecleanup. Fakes sind keine OS-Evidenz. Produkt-/Manual-Artefakte
bleiben getrennt. Keine Git-/DB-/SQL-/Anwendungs-/HTTP-/Browser-/Scraper-/Native-
Operationen, keine zusätzlichen Host-/Worker-JVMs, Downloads oder Installationen.
SSRF/T03/T04/T06 unverändert offen; kein T02-Gesamtabschluss.
**Suspendierter Windows-Prozessversuch weiterhin GESPERRT.**

## T02 – B-WATCHDOG-3B-2: Einmaliger synthetischer stdio-Host (25.10.2026)

Separat freigegebene Stufe nach B-WATCHDOG-3B-1. Historische Berichte,
Freigaben und STOP-/Trace-Evidenzen unverändert. Genau EIN zusätzlicher
synthetischer Java-Host wurde nach bestandenen Fake-/Unit-Tests gestartet;
keine weitere Hostinstanz und kein Wiederholungsversuch.

### Implementierung und Artefaktgrenzen

- Neues Source Set communicationHost: SyntheticHostMain und SyntheticProtocol
  sowie eine explizite Quellenallowlist der unveränderten BinaryIpcCodec,
  BufferBudget, WatchdogModel, FakeJobKernel und FakeRecoveryJournal.
  Die letzten drei sind wegen der bestehenden Codec-Typreferenzen erforderlich;
  der reale Host instanziiert keinen Fake-Watchdog/Job und behauptet keine C-Barriere.
- Neues Source Set communicationCheck: CommunicationLaunchPlan, OwnedHostAttempt,
  CommunicationProbe und vier Testklassen. Nur dieses Set enthält ProcessBuilder
  und den tatsächlichen Startpfad; diese Klassen sind NICHT im Hostklassenpfad.
- Kein extendsFrom zu main/manualTest/jobAdapter, keine neue Bibliothek.
  Wiederverwendet werden ausschließlich lokal vorhandene JUnit-6.0.3-Module
  der bisherigen Testinfrastruktur. Host selbst: JDK-only, keine Runtime-JARs.
- communicationHostUnitTest wählt nur SyntheticProtocolTest,
  OwnedHostAttemptTest und CommunicationLaunchPlanTest. Kein Prozessstart.
- communicationProbeOnce wählt ausschließlich CommunicationProbeOnceTest.
  Keine Abhängigkeit von test/check/build/assembleManualTest auf diese Task;
  kein stiller Start aus regulären Tests. Eigener normaler JUnit-Prüfprozess
  ist das Parent-Harness. Genau ein zusätzlicher Nicht-JUnit-Hostprozess.
- Hostklassen liegen ausschließlich in build/classes/java/communicationHost;
  52 freigegebene .class-Dateien einschließlich unveränderter verschachtelter
  Modell-/Codectypen. Unerwartete Dateien/Klassen und abweichende Klassenpfade
  werden vor dem Start abgewiesen. Keine JNA-/Spring-/JDBC-/Scraperklassen.
  Normale und manuelle Anwendungspaketierung unverändert; vorhandenes Manual-JAR
  und main/manualTest-Quellen per Vorher-/Nachher-Hash unverändert.

### Enges synthetisches Wireprofil

Bestehender V1-Binärcodec, 96-Byte-Header und Abschlussprofil END23.
HELLO(PROFILE) → READY(PROFILE), genau START(feste synthetische Nachricht)
→ RESULT_AVAILABLE(feste synthetische Antwort). Anschließend S→H CHANNEL_END
und Schließen von stdin; H bestätigt dieses EOF vor seinem CHANNEL_END und
stdout-EOF. Gegenrichtung und Sequenz sind separat geprüft.

Beide Peers prüfen sämtliche Envelopefelder auf exakte Übereinstimmung:
frische Session-ID, synthetisches Ticket (2,1), Kanäle (3,1)/(3,2), Sender
(4,1)/(4,2), S_H/H_S und fortlaufende Sequenzen 1–3. PROFILE ist ausdrücklich
--synthetic-stdio-v1-end23, nicht nur die Header-Versionszahl. Neue/alte V1-
Profile werden nicht als mischkompatibel behandelt. Frühzeitiges/partielles EOF,
falsche Identität, andere Typen/Texte, doppelte/späte Frames und zusätzliche Bytes
nach CHANNEL_END bleiben Fehler. Kein ObjectInputStream, kein generisches Echo,
keine Dateipfade/Befehle aus Eingangsdaten, keine ungeprüften Importmaps.

Das ist ausschließlich ein synthetisches S–H-Kommunikationsprofil. Keine Worker-
Ergebnisübertragung, J-/K-Endbelege oder Watchdog-Slotfreigabe werden daraus
abgeleitet. WatchdogWireBridge bleibt unverändert; künstliche Jobbelege würden
gerade keinen realen Endnachweis liefern. Die vollständige Lifecyclebarriere
bleibt separat durch die bisherigen 338 Modelltests gesichert.

Grenzen vor dem Start festgelegt: 160 Bytes je Frame, 512 Gesamtbytes je
Richtung, höchstens 7 Bytes je Streamfragment, 2048 Bytes Codec-Reserve pro Peer.
Kein Queuewachstum; fehlende Encoderreserve ist Backpressure. stderr wird nur
bis zum Überschreiten von 256 Bytes gelesen/gezählt, niemals als Rohtext gespeichert.
stdout ausschließlich binär. Der Host kann genau eine feste begrenzte Fehler-
meldung auf stderr schreiben, ohne Argumente, Stacktrace oder Exceptiontext.

### Start, Fristen und eigener Endweg

Festes Java-21-Executable C:\Program Files\Java\jdk-21\bin\java.exe,
genau ein geprüfter Klassenordner, feste Hauptklasse und Argumentliste,
direkter ProcessBuilder-Start ohne Shell. Laufzeitpfad/Java-Version,
Klassenallowlist und SHA-256 werden vor Start geprüft und unmittelbar vor
ProcessBuilder.start erneut verglichen. Keine beliebigen Hostargumente.

Privates lokales CWD: .gradle/t02-watchdog-3b2-2e4b0772, eigene tmp-/user.home-
Pfade. Kindumgebung komplett geleert, danach ausschließlich SystemRoot, WINDIR,
TEMP und TMP aus festen lokalen Pfaden gesetzt. Keine DB-Passwörter,
MangaLib-/JVM-/Agentoptionen oder geerbter PATH. Heap 16–32 MiB, Metaspace 32 MiB,
Codecache 16 MiB, SerialGC, ein aktiver Prozessor; kein Attach-/PerfData-Pfad.

OwnedHostAttempt besitzt nur das direkt zurückgegebene Process-Objekt:
keine PID-Suche, keine globale Java-Bereinigung und keine Nachfahrenbeendigung.
Monotone nanoTime-Restfristen: 2 s Handshake, 6 s Gesamtfrist einschließlich
normalem Exit. Bei fehlendem Abschluss einmal destroy, höchstens 1 s warten,
dann nötigenfalls einmal destroyForcibly ausschließlich dieses eigenen Objekts,
wieder höchstens 1 s warten. Unter Windows sind diese Methoden Abbruchversuche,
kein behaupteter geordneter Ctrl+C-Shutdown. Danach maximal 1 s gemeinsamer
Reader-/Writer-/stderr-Threadabschluss. Keine Synchron-Streamschließung unter
noch laufenden eigenen I/O-Callbacks. Prozessende, I/O-Ende und Streamschluss
werden getrennt geprüft; unbestätigtes Ende/Ressourcenende bedeutet STOP.

Der normale Endweg ist Protokollabschluss → EOF → eigener System.exit(0).
Zusätzlich läuft im Host ein eigener monotoner 8-s-Deadline-Thread, der bei
ausbleibendem Normalende ausschließlich seine eigene synthetische JVM mit
halt(124) beendet. Keine Nachfahren, Shutdownhooks oder externen Aktionen im
Hostcode. Der Erststart und die Abbruchaufrufe der JDK-Prozess-API sind keine
hart zeitbeweisbaren Windows-Kerneloperationen; die Grenzen gelten für die
Java-Warte-/Protokollphasen, kein OS-Hard-Realtime-Nachweis.

Keine Windows-Jobgruppe: kein Schutzbeweis gegen kompromittierte Same-Token-
Peers, beliebige Nachfahren, vollständigen Harnessausfall oder Hänger vor
Beginn des Host-main. Parent-PID-Prüfung und frische Wire-IDs sind Zuordnung,
keine kryptografische Peer-Authentisierung. Native Prozesshandle-Freigabe bleibt
JDK-intern; resourcesClosed belegt eigene Streams/I/O-Threads, nicht Windows C.

### Tatsächliche Prüfungen und Einmalergebnis

Java 21 / Gradle 8.14.3, ausschließlich --offline --no-daemon. Vor jedem
tatsächlichen Gradle-Lauf identischer --dry-run; JVM-Overridevariablen
JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/GRADLE_OPTS nicht gesetzt.

1. communicationHostUnitTest --rerun: zunächst 43 Fälle erfolgreich, nach
   zusätzlichem Backpressurefall final 44 erfolgreich. Keine Entwicklungsläufe
   addieren. Graph ausschließlich compileCommunicationHostJava/
   processCommunicationHostResources/communicationHostClasses/
   compileCommunicationCheckJava/processCommunicationCheckResources/
   communicationCheckClasses/communicationHostUnitTest; keine Hostinstanz.
2. test --rerun mit denselben sieben --tests-Filtern wie B-WATCHDOG-3B-1:
   sämtliche bisherigen 338 Fälle erneut erfolgreich. Regulärer Graph nur
   compileJava/processResources/classes/compileTestJava/processTestResources/
   testClasses/test. Kein Spring-Vollkontext, keine DB und keine JNA-Tests.
3. communicationProbeOnce --rerun: genau einmal nach erfolgreichem Dry-Run
   ausgeführt. Derselbe isolierte Kompiliergraph, ausschließlich die Einmal-Task
   statt Unit-Task. Keine weiteren Versuche und keine automatische Wiederholung.

| Finaler Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Bisherige sieben Modell-/Codec-/Harness-/Adapterklassen | 338 | 0 | 0 | 0 |
| SyntheticProtocolTest | 26 | 0 | 0 | 0 |
| OwnedHostAttemptTest | 14 | 0 | 0 | 0 |
| CommunicationLaunchPlanTest | 4 | 0 | 0 | 0 |
| CommunicationProbeOnceTest, separater Realversuch | 1 | 0 | 0 | 0 |
| **Gesamt dieser isolierten Auswahl** | **383** | **0** | **0** | **0** |

Unitabdeckung: Handshake, alle Identitätsfelder/Version, Sequenzen/Duplikate/
späte Frames, Fragmentierung, Überlänge, Backpressure, EOF im Header/Payload,
Endmarker/EOF/zusätzliche Bytes, Hostabbruch, Handshake-/Ergebniszeitüberschreitung,
Ressourcenfehler, verspäteter Start, unbestätigtes Ende und Wiederanlaufsperre.
Die 159 früheren JNA-Fälle bytegleich und nicht ausgeführt. Vorhandene
Sicherheitsassertions und alle bisherigen 338 Testquellen unverändert.

Einmalmarker: .gradle/t02-watchdog-3b2-2e4b0772/consumed. CREATE_NEW und
FileChannel.force(true) VOR Start, danach nie gelöscht/zurückgesetzt. Jeder
vorhandene Marker sperrt einen weiteren Versuch. Keine behauptete Garantie
der Verzeichnis-Durability bei Stromausfall oder gegen manuelle Markerentfernung.
Das Verzeichnis liegt unter der bestehenden lokalen .gradle-Ignore-Regel;
keine Git-Operation zur Prüfung oder Veröffentlichung vorgenommen.

Ergebnis in .gradle/t02-watchdog-3b2-2e4b0772/result.txt und JUnit-XML:
- 24.10.2026 22:08:48 UTC / 25.10.2026 00:08:48 MESZ, Testdauer 0,372 s.
- Session 2f9bb7d6-4158-48df-b99a-70ee5831c7b1, eigene Host-PID 54764.
- Handshake, eine feste Nachricht/Antwort, beidseitiger CHANNEL_END-/EOF-
  Abschluss bestätigt (protocolComplete=true).
- Prozessende bestätigt, Exitcode 0. Eigene I/O-Threads beendet und alle drei
  Prozessstreams geschlossen, stderr EOF bestätigt mit 0 Bytes.
- abortRequested=false, forceRequested=false, stopRequired=false; Status PASS.
- Java-SHA-256: 7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8.
- Hostklassen-SHA-256: 050ec7502bc4d4a3f72fa6eb043766b8d83d12c957060afdf3848d28d5f200ea.

Vorher-/Nachher-Hashes über 306 bestehende Quellen/Artefakte/Evidenzen sowie
Dokumentpräfixprüfung statt der ausdrücklich verbotenen Git-Prüfungen.
Nur build.gradle.kts erweitert, neun neue isolierte Quellen und zwei
Dokumentnachträge. Historische STOPs/Freigaben/Evidenzen und Dokumentpräfixe
erhalten. Whitespace-/Konfliktmarkerprüfung ohne Git; Ergebnis im Abschlussbericht.

### Nächste begrenzte Stufe – NICHT freigegeben

Zunächst Zuordnung des jetzt belegten stdio-Transports zu den bestehenden
S/H-/Ticket-/Deadline-Ports mit Fake-K/H/J-Verträgen prüfen, ohne den
Watchdog-Lifecycle oder C-Endbelege durch das synthetische Profil zu ersetzen.
Für einen späteren nativen Versuch: eigene K-Gruppe des Supervisors,
atomar vor Resume zugeordneter H, H-eigene zunächst leere J-Gruppe, genau
gebundene Handles/Kanäle und unabhängig geprüfter Abbruch-/Endpfad.
Kein ProcessBuilder-Fallback für den produktiven Worker. Reales K/H/J-Nesting,
Durability, Peerbindung und OS-Endbelege benötigen einen eigenen eng begrenzten
Auftrag samt neuer expliziter Freigabe und unveränderter historischen Evidenz.
Erst danach separat über synthetische Worker ohne Firefox entscheiden.

Keine Produktintegration, Anwendung/HTTP, DB/SQL, Migration, echte Scraper,
Firefox/Gecko/Selenium, Win32/JNA, externe Netzwerkzugriffe, Downloads oder
Installationen. Keine Git-Operationen. SSRF/T03/T04/T06 unverändert offen,
kein T02-Gesamtabschluss. **Suspendierter Windows-Prozessversuch weiterhin
GESPERRT; dieser Kommunikationserfolg hebt die Sperre nicht auf.**

## T02 – B-WATCHDOG-4A: K/H/J und Hostkommunikation mit Fakes verbunden (25.10.2026)

Ausschließlich prozessinterne Integration. Der historische stdio-Hostversuch
aus B-WATCHDOG-3B-2 wurde NICHT wiederholt; consumed/result.txt und alle früheren
STOP-/Trace-/Freigabeevidenzen bleiben unverändert. Keine neue Hostinstanz,
OS-IPC, Win32-/JNA-Operation oder Produktintegration.

### Komponenten und Datenfluss

Neues isoliertes Source Set lifecycleCheck mit LifecycleCoordinator und
LifecycleCoordinatorTest. Es kompiliert die unveränderten WatchdogWireBridge,
MemoryIpcLink und WireIdentityRegistry aus der bisherigen Testquellenallowlist
gegen communicationHost.output. Keine main/manualTest/jobAdapter-Vererbung,
keine CommunicationProbe-/CommunicationLaunchPlan-/OwnedHostAttempt-Klassen
und keine native Startimplementierung im neuen Prüfsatz. Vorhandene JUnit-
6.0.3-Module wiederverwendet, keine neue Bibliothek/Version oder Downloads.
Nur die explizite Testtask watchdogLifecycleTest verwendet diesen Prüfsatz;
keine Kopplung an test/check/build/assembleManualTest oder communicationProbeOnce.

LifecycleCoordinator ist eine Verdrahtung, keine zweite Admission-/Cleanup-/
Recoverymaschine. Er besitzt kein eigenes Lifecycleenum, keine Ersatzdeadline,
keinen Timer und keinen Startport für echte Prozesse. initialize/request,
Startschritte, Exitereignisse, stop/escalate/finish/retire delegieren an das
bestehende WatchdogModel. Erst dessen Admission erlaubt das Binden eines neuen
Kanal-/Registry-Satzes. Fehler der lokalen Bindung gehen über controlFailed
in dieselbe konservative Modellbehandlung. Schließen des Koordinators löst
nur Speicherressourcen und behauptet weder Prozessende noch Slotfreigabe.

Geprüfter Ablauf:
1. WatchdogModel nimmt den Auftrag an und bestätigt den Fake-Journalintent.
2. S erzeugt/configuriert K, beginnt den atomar K-gebundenen simulierten H-Start
   und verbucht den Rückgabebeleg. Erst danach Resume und S–H-Kanalbindung.
3. HELLO/READY werden als echte Speicherbytes fragmentiert durch den bestehenden
   Binärcodec und die WatchdogWireBridge an das Modell geliefert.
4. H muss lebend, identifiziert, nicht suspendiert und Mitglied der eigenen K
   sein, bevor die H-eigene J angelegt wird. S erhält keine J-Handlekopie.
5. START, atomarer J-gebundener W-Start und Workerhandshake. W gehört durch
   den unveränderten simulierten Gruppenbaum sowohl J als auch K; H bleibt
   außerhalb J und S wird nicht als Gruppenmitglied gestartet.
6. RESULT_BEGIN/DATA/RESULT_END transportieren die feste synthetische Antwort
   des bisherigen Hostprofils. Ein begrenzter Consumer vergleicht vorläufige
   Bytes und bestätigt nur den vollständigen passenden Datenframe. Keine
   Importmap, Geschäftslogik oder Datenbank wird dadurch implementiert.
7. W→H CHANNEL_END/EOF, H→W CHANNEL_END/EOF und H→S RESULT_AVAILABLE, anschließend
   bestätigter Workerexit. Die Terminierungsanforderung von J ist kein Ende.
8. Separat bestätigte J-Leere einschließlich dynamischer Nachfahren,
   GROUP-Endbeleg und RESOURCES_CLOSED; danach S→H und H→S CHANNEL_END/EOF.
9. H-Ende, frischer vollständiger K-Endbeleg, sämtliche IoEnd-Komponenten,
   vollständiger Handleabschluss und Journalabschluss. Erst WatchdogModel.finish
   darf Slot/Ergebnis freigeben. Incident-retire kann eigene Ressourcen lösen,
   bleibt aber immer QUARANTINED und erlaubt keinen neuen Auftrag.

Bewusst keine Real-Process-Adapterintegration über OwnedHostAttempt: Dessen
ProcessBuilder-Endweg ersetzt weder atomare K/J-Zuordnung noch die bestehende
C-Barriere. SyntheticHostMain und SyntheticProtocol bleiben quelltextlich
unverändert; ihre echten stdin/stdout-Methoden werden hier nicht aufgerufen.
Die Fakeintegration verwendet dieselben Codec-/Signal-/Textbausteine, ergänzt
um die schon vorhandenen Worker-/Gruppen-/Ressourcennachrichten des Modells.

### Minimale Vertragspräzisierungen

Keine neuen Wiretypen und kein neues Lifecycleenum. 96-Byte-Header, V1 und
END23 bleiben unverändert. Das erweiterte Fakeprofil trägt ausdrücklich den
Namen --synthetic-stdio-v1-end23:K-H-J: Das historische reine stdio-Probeskript
hat keine J-/Worker-/Gruppenmeldungen und ist kein kompatibler Lifecyclepeer.
Sein nacktes Profil wird in der neuen Integration kontrolliert abgewiesen.
Keine stillschweigende Mischkompatibilität oder reale Hostfreigabe behauptet.

Die Bootstrap-Session-ID wird explizit injiziert, nicht aus lokaler Generation
als vermeintlich globale Identität abgeleitet. Tests verwenden deterministische
verschiedene Sessions für verschiedene Supervisoren; Session, Ticket, Richtung,
Kanal, Sender und Sequenz gehen durch die bestehende Bridgeprüfung. Frische
authentische Bootstrapverteilung bleibt eine spätere reale Voraussetzung;
die Injektion erzeugt keine kryptografische Authentisierung/Same-Token-Isolation.
Ringe, Fragmentierung und feste Budgets des vorhandenen Speichertransports
bleiben erhalten. Kein unbeschränkter Ergebnis-/Nachrichtenpuffer.

Drei bestehende Modellquellen gezielt ergänzt, vorhandene Testquellen unverändert:
- FakeRecoveryJournal.confirmsIntent prüft Writer, Zustand, Ticket und exakte
  K-/J-Bindung vor weiteren Startschritten. Verlust des Intents verhindert schon
  die Gruppenanlage oder hält eine spät erworbene Instanz ohne Resume fest.
- WatchdogModel prüft den Intent auch vor Start-Resume, lokale offene Handles
  vor weiteren Startschritten und H-Identität/K-Mitgliedschaft vor J-Anlage.
  controlFailed ist nur ein Eingang in den vorhandenen Fehlerpfad. Fehlgeschlagene
  J-/K-Terminierungsanforderungen werden sticky protokolliert und führen zur
  Quarantäne. Nach fehlgeschlagener J-Anforderung bleibt explizite K-Eskalation
  über das Modell möglich; derselbe fehlgeschlagene Port wird nicht wiederholt.
  Erfolg einer Eskalationsanforderung hebt Quarantäne nicht auf.
- FakeJobKernel kann J-/K-Terminierungsfehler getrennt injizieren und zählt
  Anforderungen unabhängig von Erfolgen. H muss für J-Anlage/-Start identifiziert
  sein. Resume erfordert die direkte owned Gruppe statt bloßer Mitgliedschaft
  in deren äußerer Gruppe; ein gestopptes K verhindert auch Begin/Resume unter J.
  Adversarielle späte Start-Rückgaben und dynamische Nachfahren bleiben weiterhin
  separate Fakeereignisse und werden nicht durch einen angenommenen Kill ersetzt.

Keine Handlekopien über S/H/W-Grenzen, keine PID-basierte Übernahme fremder
Instanzen und kein erneuter Gebrauch unklar geschlossener Handles. Start-Intent,
Start-/Lease-/Operationsfristen und die einmalige 15-s-Cleanupdeadline bleiben
im Modell; kein Coordinator-Recovery/Retry und keine neue Auftragsannahme nach
unbestätigtem Abschluss. Normale, vollständig bestätigte C erlaubt weiterhin
ein neues Ticket; alte Kanalobjekte können dieses nicht reaktivieren.

### Tatsächlich ausgeführte datenbankfreie Tests

Java 21 / Gradle 8.14.3; alle Läufe --offline --no-daemon und jeweils VORHER
derselbe Taskplan zusätzlich mit --dry-run. JVM-Overridevariablen
JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/GRADLE_OPTS nicht gesetzt.

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon watchdogLifecycleTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon watchdogLifecycleTest --rerun
.\gradlew.bat --offline --no-daemon communicationHostUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon communicationHostUnitTest --rerun
~~~

Zusätzlich der unveränderte siebenfach gefilterte test-Aufruf aus B-WATCHDOG-3B-1,
ebenfalls mit vorherigem identischem Dry-Run: BinaryIpcCodecTest,
MemoryIpcLinkTest, WatchdogClosureTest, WatchdogModelTest, OwnedJobAdapterTest,
SupervisorModelTest und IpcBudgetModelTest. Keine Vollsuite/DB-Integration.

Der neue Taskgraph enthält ausschließlich compileCommunicationHostJava/
processCommunicationHostResources/communicationHostClasses/compileLifecycleCheckJava/
processLifecycleCheckResources/lifecycleCheckClasses/watchdogLifecycleTest.
Die bisherigen Graphen entsprechen B-WATCHDOG-3B-2 (ohne Einmal-Task).
communicationProbeOnce, JavaExec-/Job-/Probe-/Boot-/Manual-/Integrationstasks
waren in keinem ausgeführten Graph. Nur reguläre Gradle-/JUnit-JVMs.

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Bisherige sieben Modell-/Codec-/Harness-/Adapterklassen | 338 | 0 | 0 | 0 |
| Bisherige drei Host-/Harness-Unitklassen | 44 | 0 | 0 | 0 |
| LifecycleCoordinatorTest, neu | 60 | 0 | 0 | 0 |
| **Gesamt dieser isolierten Auswahl** | **442** | **0** | **0** | **0** |

Alle bisherigen 382 Unitfälle erneut bestanden, ohne geänderte Testquellen
oder abgeschwächte Assertions. Neue Fälle decken insbesondere alle 16 geforderten
Fehlergruppen ab: Handshake, späte H-/W-Rückgabe, fehlender K-/J-Nachweis,
H-Crash während Ergebnisübertragung, Nachfahren und getrennte J-/K-/Kanalenden,
J-/K-Terminierungsfehler, Cleanupfrist, Recoveryintent, späte/doppelte Frames,
Identitäts-/Session-/Ticketfehler und unvollständige Ressourcenbelege.
Zusätzlich fremde Instanz, Rollen-/Handlekopien, unklarer Handleabschluss,
gestoppte äußere Gruppe, Incident-retire und neue Admission nach echter C.
Frühere Entwicklungsläufe mit 55, 57 und 59 neuen Fällen ebenfalls grün;
nicht addieren. Die 159 älteren JNA-Tests unverändert und nicht ausgeführt.
Der frühere einzelne Realversuch bleibt ausschließlich historische Evidenz.

Vorher-/Nachher-Hashes über 317 bestehende Dateien, Dokumentpräfix-/Whitespace-
und Artefaktprüfung ohne Git. Geändert: drei isolierte Modellquellen,
build.gradle.kts, zwei neue Lifecyclequellen und diese zwei Dokumentnachträge.
Haupt-/Manual-Quellen, bestehende Testguards, native Adapter und Manual-JAR
bleiben unverändert. Keine neuen Lifecycle-/Hoststartklassen im Manual-JAR.
Die gemeinsam kompilierten Modellklassen im communicationHost-Ausgabeordner
ändern sich durch diese Modellergänzungen; der historische Klassenhash im
verbrauchten Probebericht wird ausdrücklich NICHT ersetzt oder neu abgenommen.

### Kleinste nächste ausführende Stufe – nur Vorschlag

Vor neuer Ausführung einen separat reviewbaren nativen K→H-Startadapter mit
Fake-Nachweisen fertigstellen: genau S-eigene K, vorbereiteter unabhängiger
Abbruchport, atomare K-Zuordnung bei suspendiertem H-Start, Identität und
Mitgliedschaft VOR Resume. H zunächst nur synthetisch, ohne J/Worker/Firefox;
kein ProcessBuilder-Fallback und keine Aufnahme nachträglich gesuchter PIDs.
Keine K-Handlekopie an H. Gruppen-/Prozessende, alle retained Ressourcen,
Callback-/IPC-Ende und Journal getrennt nachweisen. Nur eine eigene Instanz,
neuer privater Einmalmarker, feste Bounds und STOP bei unbestätigtem Ende.
Konkreten OS-Endweg und Freigabebedingungen VOR dem Start gesondert vorlegen.
H-eigene leere J und danach synthetischer W sind weitere getrennte Stufen.

Fakes beweisen weiterhin weder Windows-Nesting/Atomicität, Breakaway-/Token-
verhalten, reale IPC-/Peer-Authentisierung, Journaldurability noch Cleanup bei
Kernelhänger, vollständigem Harnessausfall oder konkurrierenden OS-Ereignissen.
Keine neuen echten Prozesse/Hoststarts, nativen Terminierungen, OS-IPC,
DB/SQL/Migrationen, Anwendung/HTTP, Browser/Scraper, externen Abrufe,
Installationen/Downloads oder Git-Operationen. Keine SSRF-/T03-/T04-/T06-
Umsetzung und kein T02-Gesamtabschluss. **Suspendierter Windows-Prozessversuch
weiterhin ausdrücklich GESPERRT; auch der vorgeschlagene K→H-Versuch ist nicht
freigegeben und wird durch diesen Fakeerfolg nicht automatisch erlaubt.**

## T02 – B-WATCHDOG-4B-1: H-in-K-Startadapter ausschließlich mit Fakes (25.10.2026)

Keine neue OS-Ausführung. Die bestehenden STOP-/Trace-/consumed-/Ergebnisevidenzen
bleiben unverändert. Der frühere einzelne stdio-Hostversuch wurde nicht wiederholt.
Auch dieser Vorbereitungsschritt erteilt keine Freigabe für CreateProcessW,
ResumeThread, TerminateJobObject oder einen anderen ausführenden Win32-Versuch.

### Schnittstellenbefund und vorhandene Bindings

Statische Prüfung der tatsächlichen Quellen unter src/jobAdapter/java:
- Win64KernelCalls enthält private K-Anlage, Kill-on-close-Konfiguration und
  Readback, Joblisten/Accounting sowie gehaltene JobUse-Nutzungsreservierungen.
- Win64ProcessKernelCalls bereitet PROC_THREAD_ATTRIBUTE_JOB_LIST (0x2000d)
  bereits vor CreateProcessW vor. START_FLAGS enthalten CREATE_SUSPENDED,
  EXTENDED_STARTUPINFO_PRESENT, CREATE_UNICODE_ENVIRONMENT und NO_WINDOW.
  Keine nachträgliche AssignProcessToJobObject-Fallbackroute. GetProcessId,
  GetProcessTimes und QueryFullProcessImageNameW binden die gehaltene Instanz;
  IsProcessInJob prüft Mitgliedschaft vor ResumeThread. WaitForSingleObject
  und GetExitCodeProcess sowie Listen-/Accountingnachweise sind vorhanden.
- prepareCleanupControl reserviert denselben einzigen privaten K-Handle über
  JobUse. Kein DuplicateHandle und keine Handlekopie an H. Kontrollterminierung
  verwendet TerminateJobObject außerhalb des regulären Flight-/Operationspfads;
  cancelStarts ist ein lokales Stoppsignal. Close bleibt während Nutzungen gesperrt.
- Win64NativeBindings bietet vorbereitete, explizit zu ladende System32-Bindings,
  keine automatische Startfreigabe. OwnedHostAttempt bleibt ausschließlich der
  historische ProcessBuilder-Probe-Endweg und ersetzt diesen K-Adapter nicht.

Wichtige nachgewiesene Lücke: Die bestehende native Startvorbereitung enthält
nur ein Attribut (JOB_LIST); CreateProcessW wird mit inherit=false aufgerufen.
Keine stdio-Pipeanlage, restriktive PROC_THREAD_ATTRIBUTE_HANDLE_LIST und
STARTF_USESTDHANDLES-/Streamanbindung sind dort implementiert. Die vorhandene
waitEnded-Methode nutzt den Exitcode intern, reicht ihn aber nicht als eigenen
Host-Ergebnisbeleg weiter. Ein echter stdio-H-in-K-Start ist deshalb noch nicht
fertig verdrahtet. Neue Bindings wurden in diesem Auftrag NICHT ergänzt/geladen.

**Tatsächlich ausgeführt wurden ausschließlich KernelCalls-Fakes.** Keine
JNA-Klasse im neuen Prüfpfad, keine Native.load-/DLL-/Win32-Operation und keine
Änderung der vorhandenen nativen Bindings oder JNA-Versionen. Der vorhandene
OwnedJobAdapter wird unverändert als Handleledger/Cleanupbaustein wiederverwendet.

### Implementierter Startadapter und enge Modellergänzung

Neuer ControlHostStartAdapter im isolierten Source Set hostStartCheck:
- Authority liefert ausschließlich bestehende Lifecycleentscheidungen, keine
  eigene Admission-/Cleanup-/Recovery-Zustandsmaschine im Adapter.
- Eine im WatchdogModel einmalig reservierte H-Probe-Startberechtigung verhindert
  zwei K-Anlagen verschiedener Adapter zum selben Intent. Intent wird nochmals
  vor KernelCalls.createJob geprüft; K-Konfiguration und Readback übernimmt
  unverändert OwnedJobAdapter.initialize.
- Der injizierte HostCalls-Port muss einen atomaren JOB_LIST-/Suspended-Vertrag
  und eine restriktive Liste aus genau drei unterschiedlichen symbolischen
  stdio-Endpunkten bestätigen. Allgemeine Handlevererbung wird abgewiesen;
  K-/Anwendungshandles gehören nicht zu diesem Endpunkttyp. Diese Fakezusage ist
  KEIN Nachweis einer tatsächlich aufgebauten nativen HANDLE_LIST.
- Ein fehlender vorbereiteter CleanupControl blockiert bereits die Initialisierung.
  Erst danach wird der gehaltene Create-/Startversuch über OwnedJobAdapter begonnen.
- Rootcapability, PID, Erstellungszeit und erwarteter Imagepfad werden gemeinsam
  gebunden. PID allein, andere Mitgliedschaft, geänderte Erstellungszeit oder
  anderer Imagepfad erlauben keinen Resume. Eine späte Teil-/Startrückgabe bleibt
  im eigenen Ledger; unklare Ergebnisse werden nicht versteckt oder wiederholt.
- Start-/Resumefreigabe wird vor und nach dem Portaufruf geprüft. cancelStarts
  macht späte Rückgaben dauerhaft unzulässig. Ein bereits in den Kernel
  eingetretener Resumeaufruf ist dadurch nicht nachträglich ungeschehen gemacht;
  der reale Race-/Endnachweis bleibt offen.
- Normales Ergebnis erfordert zusätzlich zum Kommunikationsabschluss den
  vollständigen unveränderten OwnedJobAdapter-Cleanupbericht, Root-Exitcode und
  Modell-/Journalabschluss. Completion.resourcesClosed bezeichnet dabei den
  Kernelport-Ledgerabschluss; IPC und Slotfreigabe werden zusätzlich getrennt
  geprüft. Ein vollständig abgeschlossener Bericht bleibt bei Wiederholung
  unveränderlich, ohne erneutes Close oder Rücknahme einer erfolgreichen Freigabe.

HostProbeLifecycle verbindet den Adapter mit LifecycleCoordinator, FakeJobKernel,
FakeRecoveryJournal und demselben WatchdogModel. Seine kurzen synchronisierten
Methoden enthalten ausschließlich Modell-/Speicheroperationen, keinen nativen
Aufruf. Der Adapter hält keinen Java-Monitor über die KernelCalls-Aufrufe.

Minimal notwendige Ergänzung statt zweitem Lifecycle: expliziter H-only-Prüfumfang
im vorhandenen Modell. Bisher waren RUNNING/RESULT_AVAILABLE und Kanalabschluss
an W/J und vier Richtungen gebunden. admitHostProbe und dessen einmalige
Startreservierung erlauben jetzt ausschließlich diese synthetische Prüfung:
K-Anlage → H-startend/suspendiert → bestätigter Resume → HELLO/READY/START →
feste RESULT_AVAILABLE-Antwort → S→H CHANNEL_END/EOF → H→S CHANNEL_END/EOF →
bestätigter H-Exit und K-/Ressourcen-/Journalende. Keine neue Zustandsenum und
keine neuen Wiretypen; bestehender V1-/96-Byte-/END23-Vertrag wiederverwendet.
J-Anlage ist in diesem Umfang ausdrücklich verboten, W kann nicht gestartet werden.
Ein erfolgreicher Prüfslotabschluss ist keine produktive Importlieferung:
WatchdogModel.delivered bleibt unverändert an den normalen Workererfolg gebunden.

Die normalen K/H/J-Aufträge behalten ihre vier Kanalbelege und vollständige
Barriere. Mode, Reservierung und Ergebnisse werden nur bei einer neuen, vom
Modell angenommenen Admission zurückgesetzt. Abgewiesene weitere Admission
verliert nicht das Ticket der noch benötigten Cleanupberechtigung.
Die injizierte Uhr wird vor Modellentscheidungen auf denselben monotonen
Zeitbezug abgebildet; keine neue Cleanupfrist im Adapter. Testidentitäten,
Intent und Gruppenereignisse sind Fakes, keine reale Bootstrap-/Durabilitygarantie.

### Unabhängiger Cleanup: belegt und weiterhin blockiert

Belegt mit endlichen Java-Fakes:
- Ein blockierter Start hält keinen Monitor, den der Cleanup-Kontrollpfad benötigt.
- Der Kontrollpfad verwendet nur die vorbereitete Berechtigung auf das eigene K.
- Handles/Startpuffer bleiben während ausstehender Start-/Kontrollaufrufe gehalten.
- Blockierte Testaufrufe werden über Latches im finally freigegeben; Wartezeiten
  sind gedeckelt. Keine absichtlich unaufhebbare Nativeblockade.
- Wiederholte Cleanupanforderungen erzeugen keinen zweiten Kontrollterminate
  und erneuern nicht die absolute Cleanupdeadline. Fehler/unklares Ende bleiben Q.
- Signal des gehaltenen Rootprozesses, vollständige stabile Listen/Accounting,
  alle weiteren eigenen Instanzen, Handle-/Attribut-/Speicherabschluss und IPC
  werden nicht durch den Rückgabewert einer Terminierungsanforderung ersetzt.

Der bekannte Blocker wird ausdrücklich NICHT als gelöst bezeichnet: Im Fake ist
K-Terminierung während eines blockierten Starts erreichbar, der Start kann aber
danach noch eine gehaltene Instanz liefern. Diese wird niemals resumed; ohne
ihren Endnachweis bleiben Ressourcen und Slot gesperrt. Ebenso kann die
Kontrollterminierung selbst blockieren oder fehlschlagen. Ein eigener Thread
beweist weder bounded Windows-Rückkehr noch Bereinigung bei Harnessausfall.
Ein vollständig abgestürzter Harness, laufende Kernelaufrufe, Nested-/fremde
Jobzuordnungen und Kill-on-close-Wirkung erfordern echte separate OS-Nachweise.

### Tatsächlich ausgeführte Tests

Nur Java 21 / Gradle 8.14.3, --offline --no-daemon; vor jedem tatsächlichen Lauf
derselbe Taskplan mit --dry-run. JVM-Overrides JAVA_TOOL_OPTIONS,
JDK_JAVA_OPTIONS, _JAVA_OPTIONS und GRADLE_OPTS waren nicht gesetzt.

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon controlHostStartUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon controlHostStartUnitTest --rerun
~~~

Neuer Graph: compileCommunicationHostJava/processCommunicationHostResources/
communicationHostClasses/compileLifecycleCheckJava/processLifecycleCheckResources/
lifecycleCheckClasses/compileHostStartCheckJava/processHostStartCheckResources/
hostStartCheckClasses/controlHostStartUnitTest. Kein jobAdapter-/Native-/
JavaExec-/Probe-/Boot-/DB-Task. Lediglich normale Gradle-/JUnit-Prüfprozesse.

Die bisherigen Läufe wurden ebenfalls mit identischem vorherigem Dry-Run
wiederholt: siebenfach gefiltertes test (338 Fälle, Filter unverändert wie
B-WATCHDOG-3B-1), communicationHostUnitTest (44) und watchdogLifecycleTest (60).
Keine allgemeine Spring-/Integrationstestsuite, kein clean und keine ProbeOnce.

| Finaler XML-Bericht | Bestanden | Failures | Errors | Übersprungen |
|---|---:|---:|---:|---:|
| Bisherige Modell-/Codec-/Adapterklassen | 338 | 0 | 0 | 0 |
| Bisherige Host-/Harness-Unitklassen | 44 | 0 | 0 | 0 |
| Bisherige K/H/J-Lifecycleklasse | 60 | 0 | 0 | 0 |
| ControlHostStartAdapterTest, neu | 41 | 0 | 0 | 0 |
| **Gesamt der isolierten Auswahl** | **483** | **0** | **0** | **0** |

Neue Fälle: K-Aufbau/readback und suspendierter H, fehlender Intent/Control,
atomare Zuordnungs-/Suspend-/Vererbungszusagen, Fehler vor/nach Start,
Mitgliedschaft/Image/Instanzbindung, Resume-/Deadlinefehler, späte Rückgabe,
erreichbarer Cleanup bei blockiertem Start, blockierter/fehlgeschlagener Control,
konkurrierende Adapter zu demselben Intent, idempotenter Abschluss, bestehende
Cleanupauthority nach abgewiesener Admission, Handshake/EOF/CHANNEL_END,
fehlende Root-/Gruppen-/Nachfahrenendbelege, Teil-Close, J-Verbot und Exitcode.
Frühere Entwicklungsläufe mit 34 und 39 Fällen ebenfalls grün; nicht addieren.
Alle bisherigen 442 Testquellen/Assertions unverändert. 159 historische JNA-
Tests unverändert/nicht ausgeführt; echte Einmalversuche nicht wiederholt.

Vorher-/Nachher-Hashes über 319 vorhandene Dateien und Dokumentpräfixprüfung
statt verbotener Git-Operationen. Drei neue isolierte Quellen, notwendige
Ergänzungen von WatchdogModel/LifecycleCoordinator/build.gradle.kts und zwei
Dokumentnachträge. Native Bindings/OwnedJobAdapter, Produkt-/Manual-Quellen,
Guards und vorhandenes Manual-JAR unverändert. Keine neuen Startadapterklassen
im Manual-JAR. Historische Marker/Evidenzen und Dokumentpräfixe unverändert.
Kein neuer Host-/OS-Ausführungstask; Source Set nutzt nur bereits vorhandene JUnit-
Module und enthält keinen Win64-Port, Native-Loader oder ProcessBuilder-Harness.

### Freigabeentscheidung und notwendiger nächster Vorbereitungsschritt

**Ein echter K/H-Einmalversuch ist derzeit NICHT verantwortlich freigabereif.**
Ein erfolgreicher Fake beweist hier lediglich die erreichbare Kontrolloperation
und die konservative Behandlung. Kein ausführbarer B-WATCHDOG-4B-2-Ablauf wird
als bereits sicher dargestellt und kein neuer Einmalmarker wird verbraucht.

Vor einem Ausführungsauftrag mindestens separat vorbereiten/reviewen:
1. Konkreter NativeHostCalls-Port mit fest geprüftem Java-/Klassen-/Argument-/
   Umgebungsmanifest, echter stdio-Pipe-/HANDLE_LIST-Vorbereitung, exakter
   Vererbung nur der drei Child-Enden, EOF-/Ressourcenabschluss und weitergereichtem
   Exitcode. JOB_LIST und Suspended-Start unverändert zwingend, kein Fallback.
2. Reale dauerhafte Intent-/Ticket-/Jobbindung VOR K-Anlage; FakeRecoveryJournal
   ist nicht persistent. Historische Marker dürfen dafür nicht umgeschrieben werden.
3. Konkreter ausreichend unabhängiger Endweg für blockierten Start/Resume/Control,
   Nachweis der gehaltenen K-Nutzungen und später Start-Rückgaben. Begrenzter
   Java-Wait allein reicht nicht. Restrisiken bei Harness-/Kernelversagen vorher
   ausdrücklich entscheiden; kein PID-/Namenskill oder automatischer Retry.
4. Erst danach neue explizite OS-Einmalfreigabe, privater neuer Marker, feste
   Zeit-/Ressourcengrenzen und überprüfbare Prozess-/Gruppen-/Accounting-/IPC-/
   Handleendbelege samt STOP-Bedingungen. Kein Worker, J oder Firefox dabei.

Keine ausführenden Win32-/Joboperationen, Host-/Worker-/Browser-/Scraperstarts,
DB/SQL/Migrationen, Anwendung/HTTP, externen Netzwerkzugriffe, neuen Dependencies,
Downloads/Installationen oder Git-Operationen. Kein T02-Gesamtabschluss und keine
SSRF-/T03-/T04-/T06-Arbeiten. **Früherer suspendierter Workerprozessversuch und
kommender eigenständiger K/H-Versuch bleiben GESPERRT.**

## Nachtrag 25.10.2026 – B-WATCHDOG-4B-PREP: stdio, Intent und Cleanup-Grenze

Dieser Nachtrag ergänzt den historischen Stand; er ersetzt keine frühere Evidenz
oder Freigabe. Ausschließlich isolierte Implementierung und deterministische
Fakes, keine OS-/K/H-/Workerprobe und keine Produktintegration.

### 1. Implementierte Vorbereitung und überprüfte Verträge

- `Win64StdioChannels` ergänzt injizierbare CreatePipe-/Handleflags-/ReadFile-/
  WriteFile-Bindungen. Drei anonyme Pipes, sechs einzeln verbuchte Enden; nur
  Child-stdin/-stdout/-stderr sind vererbbar und in HANDLE_LIST enthalten.
  K ist ausdrücklich ausgeschlossen und sein nicht vererbbarer Zustand wird
  geprüft. Parent-Enden bleiben nicht vererbbar. JOB_LIST bleibt zwingend.
- Optionaler stdio-Pfad in `Win64ProcessKernelCalls`: zwei Attribute statt eines,
  STARTF_USESTDHANDLES, exakte STARTUPINFOEXW-Offsets (Flags 60, Handles 80/88/96,
  Attributliste 104, Gesamtgröße 112). bInheritHandles=true ausschließlich zusammen
  mit dieser expliziten Liste. Alter 18-Symbol-/No-stdio-Vertrag bleibt erhalten.
  Neuer Opt-in bindet zusätzlich fünf stdio-Exports, insgesamt 23; kein Such- oder
  ungeschützter Startfallback. Die explizite reale Ladeimplementierung ist kalt
  und wurde nicht aufgerufen. JNA 5.17.0 und bestehender JAR-Hashpin unverändert.
- Eigenes Pipeledger getrennt vom Prozess-/Thread-/Jobledger. Bei Teilfehlern
  bleiben erworbene Enden sichtbar. UNKNOWN/fehlgeschlagenes Close wird nicht
  erneut versucht. Child-Kopien werden erst nach tatsächlicher Create-Rückkehr
  geschlossen. HANDLE_LIST-Backing bleibt bis zur Attributlöschung und bis zum
  Ende einer laufenden Startnutzung erhalten; ein bloßes Close der Pipe-Enden
  ist keine Gesamtfreigabe.
- Bounded stdio-Prüfprofil: pro Aufruf maximal 512 Byte, insgesamt maximal 512
  für stdin/stdout und 256 für stderr; keine unbegrenzten Diagnoseausgaben.
  stdout bleibt Protokollkanal, keine Vermischung mit stderr/Logs. Der bestehende
  Codec bleibt für die spätere Protokollprüfung zuständig; keine neue produktive
  Payloadgrenze. EOF ist ausschließlich Kanalende, kein Gruppenendbeleg.
- Blockierendes Lesen/Schreiben oder Pipevorbereitung hält keinen gemeinsamen
  Java-Monitor über den injizierten API-Aufruf. Nutzungsreservierungen und Puffer
  bleiben bis zur tatsächlichen Rückkehr bestehen. WICHTIG: Vor-/Nachprüfungen
  einer Deadline garantieren KEINE fristgerechte Rückkehr synchroner Win32-I/O.
- Echten Exitcode nur am gehaltenen, signalisierten Root-Handle abfragen; kein
  PID-Lookup. Fehler/fehlendes Signal blockieren den Nachweis. Auch 259 ist nach
  bestätigtem Signal ein möglicher Exitcode, kein alleiniger STILL_ACTIVE-Beweis.

### 2. Intentdateien: real implementiert, Powerloss-Durability nicht behauptet

`src/hostPrepCheck/.../StartIntentStore.java` verwendet ausschließlich einen neu
angelegten privaten Laufordner, CREATE_NEW, eine Owner-Dateisperre und immutable
Revisionen. Bindung: Ticket-, Session- und K-Gruppen-UUID, Revision, vorheriger
SHA-256 und Prüfsumme; feste 132 Byte je Record. PREPARED ist Revision 1, ABORTED
oder COMPLETED die einzige Terminalrevision 2. Kein Überschreiben/Übernehmen
bestehender Laufordner und kein automatischer Neustart aus Recoverydaten.
Keine Passwörter, Prozessausgaben oder Importinhalte werden gespeichert.

Veröffentlichung: pending schreiben, FileChannel.force(true), ATOMIC_MOVE ohne
Fallback, begrenzter Readback. Simulierte Abstürze nach Schreiben, force und
Veröffentlichung hinterlassen keine neue Startcapability. Fehlende, fremde,
beschädigte, zusätzliche oder veraltete Daten werden kontrolliert abgewiesen.
Recovery verlangt zusätzlich einen extern vertrauenswürdig gehaltenen Anchor;
eine konsistente Datei allein erteilt niemals Admission. Ein Proof ist nur im
ursprünglichen offenen Store für dessen aktuell bestätigtes PREPARED gültig.
COMPLETED ist hier ein Speicherzustand, kein selbst erhobener OS-Endbeleg; spätere
Verdrahtung darf ihn nur nach der unveränderten vollständigen Endbarriere setzen.

Grenze: force(true), atomarer Rename und erfolgreicher Readback belegen unter
Windows weder persistierte Verzeichnismetadaten nach Stromausfall noch einen
unabhängigen Hardware-/Dateisystem-Durabilitynachweis. Kein nativer Directory-
Flush oder Powerlossversuch wurde vorgenommen. SHA-256 bindet Inhalte, ersetzt
keine ACL/Authentisierung oder vertrauenswürdige Ablage des externen Anchors.
Die Testordner sind lokale synthetische TempDirs, keine historischen Journale.
Dateiprüfungen sind keine Absicherung gegen einen gleichzeitig schreibenden
lokalen Angreifer mit denselben Dateirechten. Fail-closed-Recovery bleibt Pflicht.

### 3. Start-Lease und unabhängiger Cleanup: Grenze ausdrücklich offen

`StartLeaseGuard` bereitet die Schnittstelle zum unabhängigen Custodian vor,
keine konkurrierende Lifecyclemaschine. Der neue stdio-Startpfad verlangt eine
StartAuthorization vor Create und prüft sie vor/nach Resume. Der Guard bindet
Store-Proof/Revision und gehaltenes K; Root-Capability muss vom selben Scope sein.
Fremde Fähigkeiten gehen nie in einen Kill-/Cleanup-Pfad. Start/Resume sind
jeweils einmalig. Widerruf bleibt gesetzt, auch bei fehlender Kontrollbestätigung.
Ein später erfolgreicher Start wird an den Custodian gemeldet, niemals resumed.
Doppelte lokale Cleanupanforderung erneuert weder Frist noch Freigabe.

Widerruf setzt zuerst die lokale Sperre; Kontrollaufrufe laufen ohne gemeinsamen
Startmonitor und vor einem möglicherweise blockierenden Store-Abbruch. Ein
blockierender Retain-Aufruf kann vom Cleanup überholt werden; seine verspätete
Receipt wird ebenfalls widerrufen. Fehler in Retain/Live/Record/Revoke oder im
lokalen Quarantänecallback eröffnen keinen Retry oder neue Admission. Ausnahme
bei verspätetem Return lässt Fähigkeiten im nativen Ledger, behauptet kein Ende.

Der konkrete unabhängige Custodian ist NICHT implementiert. ownerLost() ist ein
Testereignis in einem noch lebenden Java-Harness und kein Crashdetektor. Eine
vorgezogene K-Terminierung ist weiterhin KEINE Sperre gegen Late-create. Ein
Custodian müsste selbst eigene K-/Root-Capabilities halten, Ownerende unabhängig
beobachten und späte Rückgaben/endgültigen Gruppenabschluss nachweisen. Ein
Java-Fake beweist weder dies noch das Ende eines blockierten Kernelaufrufs.
Es gibt hier keinen zusätzlichen ausführbaren Watchdog oder Starttask. Bestehende
OwnedJobAdapter-/WatchdogModel-Endbarrieren entscheiden weiterhin allein über
Quarantäne und Freigabe; der Lease-Guard erzeugt selbst keine Endbelege.

### 4. Tatsächlich ausgeführte Prüfungen

Alle nachstehenden Läufe am 25.10.2026 mit Java 21 und vor jedem tatsächlichen
Gradlelauf identischem `--offline --no-daemon ... --dry-run`. Keine Probetask,
JavaExec-/Hostausführung oder DB-/Spring-Vollkontexttests im ausgewählten Graphen.
JUnit-/Gradle-JVMs sind Testwerkzeuge, keine synthetischen Kommunikationshosts.

| Task / Umfang | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: unveränderte 7 Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest: drei neue Klassen | 56 | 0 | 0 |
| **Gesamt** | **539** | **0** | **0** |

Die 338 Fälle wurden ausschließlich mit diesen --tests-Filtern gewählt:
BinaryIpcCodecTest, MemoryIpcLinkTest, WatchdogClosureTest, WatchdogModelTest,
OwnedJobAdapterTest, SupervisorModelTest und IpcBudgetModelTest (jeweils mit dem
bestehenden vollständigen Paketnamen). Alle Tasks mit `--rerun`, keine clean-
oder Vollsuite. Die bisherigen 483 Testquellen und Assertions bleiben unverändert.
159 historische JNA-Tests NICHT erneut ausgeführt; historische Einzelversuche
NICHT wiederholt, STOP-/Verbrauchsmarker und Evidenzen unverändert.

Neue Fälle prüfen Pipepartialfehler, Allowlist/Vererbung, Standardhandles,
blockierte Vorbereitung/I/O, EOF, Byte-/stderrgrenzen, unbekanntes Close,
Exitcodefehler mit fortbestehender Quarantäne, fehlende Exports ausschließlich
am Loader-Fake, Intent-/Crash-/Recoveryfälle, fremde Bindungen/Capabilities,
Widerruf/verspätete Rückgabe und ausfallende Kontrollschnittstellen.
JNA wird nur für begrenzten eigenen Speicher/Layout und die eigenen API-Fakes
benutzt; kein Kernel32-Lader und keine echte Job-/Prozessoperation ausgeführt.

Entwicklungsläufe nicht zusätzlich zählen: zunächst 37 Fälle mit zwei Fehlern
im Test-Cleanup (Attributlistenspeicher vor Delete fälschlich als frei erwartet),
danach 56 Fälle mit einem Fehler im neuen Exitcodefehler-Test (Q korrekt, aber
Cleanup fälschlich als vollständig erwartet). Testreihenfolge/-Assertions auf die
strengere tatsächliche Ressourcenbarriere korrigiert, keine Sicherheitsprüfung
abgeschwächt. Abschließender neuer Prüfsatz 56/0/0 wie oben.

### 5. Isolation und Freigabeentscheidung

Neuer Source Set hostPrepCheck und explizite Testtask hostPreparationUnitTest;
kein dependsOn aus build/check/test/manualTest und keine Produktionsverdrahtung.
Nur schon vorhandene lokal gepinnte JNA- und JUnit-Module verwendet, keine neue
Dependency. Produkt-/Manual-Quellen, Datenbankguards, historische Modelle und
Prototypen unverändert. Vorhandenes Manual-JAR nicht gebaut/gestartet/verändert;
Hash- und ZIP-Inventarprüfung statt Git. Historische Dokumentpräfixe erhalten.

**Ein echter atomarer K/H-Versuch bleibt NICHT freigabereif.** stdio ist konkret
implementiert/fakegeprüft, aber nicht OS-abgenommen; Intent ist persistent im
Java-Dateivertrag implementiert, ohne behauptete Windows-Powerlossgarantie;
unabhängiger Cleanup ist nur als widerrufbarer Vertrag/Fake vorbereitet.

**Genau kleinste nächste Maßnahme:** Den vorbereiteten Custodian-Vertrag für
GENAU eine private K/H-Sitzung konkret unabhängig vom startenden Harness
implementieren und zunächst fakeprüfen: eigener K-Kontrollbesitz, unabhängiges
Ownerende, Übernahme spät zurückkehrender eigener Root-Handles und vollständige
Endbarriere auch bei blockiertem Start/Control/I/O. Kein PID-/Namenskill, kein
Restart und keine Annahme, vorgezogene Terminate sperre spätere Starts. Dies ist
keine Freigabe, einen Watchdog/Host auszuführen. Ohne diese konkrete Endautorität
bleibt der OS-Versuch gesperrt; Dateisystem-Durability und feste Manifest-/
Adapterverdrahtung werden dadurch nicht stillschweigend als abgenommen erklärt.

Keine Git-Operationen (auch kein git diff/check), DB/SQL, echten OS-/Host-/Worker-/
Browser-/Scraperstarts, Anwendung/HTTP, Netzwerkzugriffe, Installationen oder
Downloads. Keine SSRF-/T03-/T04-/T06-Arbeiten, keine fachlichen Prioritätsänderungen.
## Nachtrag 25.10.2026 – B-WATCHDOG-4C: isolierter Custodian-Kern

Dieser Nachtrag ergänzt B-WATCHDOG-1 bis 4B-PREP. Historische Inhalte, STOPs,
Einmalfreigaben und Nachweise bleiben unverändert. Freigabe ausschließlich für
isolierten Code und deterministische datenbankfreie Fakes, kein OS-Versuch.

### Explizite minimale Eigentumserweiterung gegenüber B-WATCHDOG-1

Das bisherige logische Modell S -> K und H -> J bleibt als historischer sowie
unveränderter Modell-/Regressionstest erhalten. Für den neuen Custodian-Vertrag
ist physischer Alleinbesitz von K durch S ausdrücklich NICHT mehr vorgeschrieben:
S erzeugt/configuriert K wie bisher und besitzt den Launch-/Ledgerhandle für
atomare JOB_LIST-Zuordnung; C hält eine getrennte, bestätigte Kontrollkopie.
Keine Übertragung der Admission-/Quarantäne-/Slotentscheidung an C. C erzeugt
weder weitere K-Gruppen noch J, H oder Worker. Kein zweiter Import-Supervisor.

Die Kontrollkopie muss genau denselben privaten Job, Ticket/Session/K-ID und
Intentrevision bezeichnen. C verlangt exakt JOB_OBJECT_QUERY (0x4) und
JOB_OBJECT_TERMINATE (0x8), nicht ASSIGN/SET. Owner-/Root-Prozesskopien verlangen
SYNCHRONIZE und PROCESS_QUERY_LIMITED_INFORMATION. Owner ist eine gehaltene,
vorab gebundene konkrete Instanz (Handle, PID, Erstellungszeit und Image);
PID bleibt Diagnose/Identitätsvergleich und ist niemals ein Öffnungs-/Killauftrag.
Das echte OS-Handle ist durch die opaque Fake-Capability noch nicht umgesetzt.

Spätere native Duplikation muss Quelle, Zielprozessinstanz, Objektgleichheit,
Zugriffsrechte und nicht vererbbare Zielkopie bestätigen. Kein
DUPLICATE_CLOSE_SOURCE und keine aus einer PID rekonstruierte Berechtigung.
Die dafür vorübergehend benötigten PROCESS_DUP_HANDLE-Bootstraprechte sind ein
separat zu prüfender Transferweg, keine implizite Berechtigung des hier schmalen
Owner-Beobachtungshandles. Ein bloßer IPC-Zahlenwert ist kein übernommener Handle.
Unklare Teilübertragungen bleiben im Portledger und verhindern Abschluss.
S schließt nur die eigene Kopie; C schließt nur seine bestätigten Kopien.
C darf seine letzte K-Kopie erst nach der vollständigen End-/Nutzungsbarriere
schließen. KILL_ON_JOB_CLOSE wirkt erst beim letzten OS-Handle insgesamt;
weder S- noch C-Einzelclose wird als Terminierungsbeleg gewertet. H/W erhalten
weiterhin keine K-Kontrollhandles; der vorhandene stdio-HANDLE_LIST-Vertrag bleibt.

Bootstrap für die spätere Prozessstufe: C außerhalb von K vorbereiten und seine
Instanz binden, dann S-K-Erstellung und bestätigte C-Kopie, erst danach Create-
Lease für H. C startet kein MangaLib/S und wartet nicht auf einen von H erzeugten
Job; damit kein zyklischer Startauftrag. Kein permanenter Dienst/Broker.
C darf erst nach Abschluss seiner einen Sitzung geordnet enden. S muss später
auch die konkrete C-Lebensdauer überwachen. C-Absturz bei noch offenem S-Handle
löst KILL_ON_JOB_CLOSE nicht zuverlässig aus. Bei zugleich blockiertem S ist dies
weiter ein echtes OS-Risiko, keine durch eine weitere Watchdogebene gelöste Zusage.

### Neue Komponenten und Aufrufverträge

`src/custodianCheck/java/de/mangalib/windowsadapter/OuterJobCustodian.java`:
JDK-Kern für genau eine Sitzung, implementiert den bestehenden
StartLeaseGuard.Custodian-Vertrag. Injizierter Port mit getrennten Methoden für
Kontrollduplikation, Owner-Signal, Root-Duplikation, Terminierung, Enderhebung und
Close. Kein main, Native-Loader, Prozessstart, Executor, Netzwerk oder JDBC.
Der Port ist eine Vertrauensgrenze, keine bereits vorhandene Win32-Implementierung.

Ownerprüfung verlangt dieselbe gehaltene Capability UND unveränderte Identität.
ALIVE/ENDED/UNKNOWN bleiben unterscheidbar. IPC-Abbruch und fehlender Fortschritt
eines lebenden Owners widerrufen die Lease/fordern konservativen Cleanup an,
behaupten aber kein Ownerende. Ownerende ist nur ein Port-Endbeleg der gehaltenen
Instanz. Keine PID-Suche und kein Heartbeat als OS-Endnachweis.

Es gibt keine gemeinsam mit dem regulären Start geteilte Aufrufsperre. Kurze
Kernmonitore reservieren lokale Zustände; alle potenziell blockierenden Port-
Aufrufe erfolgen außerhalb. Query-/Transfer-/Terminierungs-/Close-Spuren und
laufende Nutzungen bleiben getrennt sichtbar. Ein echter Prozessbootstrap muss
später die unabhängige Ausführung dieser Spuren bereitstellen; der Kern startet
keine Threads. Reachability, Terminierungsantwort, Gruppenende und Ressourcenende
sind getrennte Berichtsfelder. Kein Bericht enthält Slotfreigabe/Admission.

Einmaliger Retain/Startbezug, unveränderliche erste Cleanupdeadline und maximal
zwei ereignisgebundene K-Terminierungsanforderungen: die erste für Cleanup; eine
zweite nur bei tatsächlich neu zurückgemeldeter Start-Epoche. Keine Wiederholung
desselben unbekannten/fehlgeschlagenen Calls und keine neue Frist. Eine frühe
Terminierung sperrt Late-create ausdrücklich NICHT. Eine späte Root-Rückgabe
benötigt eine bestätigte eigene Kopie mit Identität/Mitgliedschaft; sie macht
vorherige Endbelege ungültig und kann nie resumed werden. Fremde oder unklare
Transfers hinterlassen eine dauerhafte Abschlussblockade. Kein PID-Kill.

Endbarriere: tatsächlicher Startausgang bekannt, keine laufende Übertragung,
eigenes offenes K mit vollständiger/stabiler leerer Liste und konsistentem
Accounting, signalisiertes unverändertes gehaltenes Root (falls vorhanden),
zusätzlich eine unabhängig zu belegende Fence: S-Handlekopien geschlossen,
ausstehende native Starts geklärt, Launch-/I/O-Ressourcen geschlossen und Kanäle
beendet. Ein bloßes S-Ack, Owner-Signal oder Job-Empty-Sample ersetzt diese Fence
nicht. Kann der spätere Port sie nicht beweisen, bleibt der Abschluss gesperrt.
Insbesondere Ownerende während Create berechtigt nicht zu erfundenem Late-create-
Cleanup. Der Kern erhebt diese OS-Fence noch nicht selbst.

Close nur nach Endbarriere und Ende aller eigenen Nutzungen, Reihenfolge
Root -> C-K-Kopie -> C-Owner-Kopie. Zwischen den Calls erneut Deadline/Autorität
prüfen; nach unbekanntem Close, Custodian-Ausfall oder abgelaufener Frist keinen
weiteren Close beginnen. Kein Retry. Quarantäne bleibt auch bei später tatsächlich
bestätigtem Ressourcenende bestehen. Bestehender WatchdogModel-/HostProbeLifecycle-
Pfad entscheidet weiterhin allein über die gesperrte Admission; neuer Test zeigt,
dass Custodian-Endbelege ihn nicht automatisch zurücksetzen.

### IPC-, Recovery- und Authentisierungsgrenze

Fester In-Memory-Kontrollvertrag: nur PROGRESS/ABORT, gebundener Peer, Channel-ID,
Ticket-/Session-/K-Binding, Intentanchor und strikt nächste Sequenz. Keine freien
Befehle, Scraperfelder, akkumulierten Nutzdaten oder Rohdiagnosen. Bericht enthält
nur feste Status-/Bool-/Zähler-/Deadlinefelder. Falsche/alte/duplizierte/vertauschte
Nachrichten widerrufen ausschließlich die eigene Sitzung. Progress hebt Widerruf
nie auf. Kein Bytekanal oder neuer Kommunikationshost implementiert.

Peer-Objektidentität ist ausschließlich eine Same-VM-Fakeannahme, KEINE
kryptografische Authentisierung oder Same-Token-Isolation. Ein echter separater
Prozess braucht gebundenes OS-Handle-Bootstrap, authentisierten Peer und bounded
Byteframing vor Allocation. Dafür bestehende Ticket-/Session-/Kanal-/Sequenz-
Konzepte wiederverwenden; kein lokaler HTTP-Port/öffentlicher Steuerkanal.
StartIntentStore/Proof unverändert: Retain nur bei weiterhin gültigem PREPARED;
Recoverydatei allein erzeugt keine neue Berechtigung. Beschädigte Altdateien oder
Restart erlauben keine neue C-Übernahme. Keine historischen Journale überschrieben.
Windows-Dateisystem-Durabilitygrenzen aus 4B-PREP bleiben ausdrücklich offen.

### Tatsächlich ausgeführte Tests

Alle Läufe mit Java 21, `--offline --no-daemon`, vor JEDEM tatsächlichen Lauf
identischem `--dry-run`. Keine DB-/Spring-Vollkontext-/OS-Probetask. Testwerkzeug-
JVMs von Gradle/JUnit sind keine gestarteten Custodian-/H-/Workerprozesse.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Klassen aus 4B-PREP, explizite --tests-Filter | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest: OuterJobCustodianTest | 63 | 0 | 0 |
| **Gesamt, unterschiedliche Fälle** | **602** | **0** | **0** |

539 bisherige Testquellen/Assertions unverändert und erneut ausgeführt. Die
56 Speicher-/Layoutfälle erst nach ausdrücklicher zusätzlicher Eigentümerfreigabe
für ausschließlich vorhandene JNA-Speicheroperationen, weiterhin OHNE Kernel32-
Laden/Win32-Prozess-/Jobaufrufe. 159 historische JNA-Tests NICHT erneut ausgeführt,
keine historische Einmalprobe wiederholt. Neuer Custodian-Laufzeitpfad OHNE
JNA-JAR; eigener Test weist ClassNotFound für com.sun.jna.Native nach.

Neue 63 Fälle: eigene Kontrollkopie und letzter Close; neun fehlerhafte Control-
Transfers, acht unklare Root-Transfers, 13 fehlende/fremde Endbelege; Ownerende
während Start und späte Root; Kommunikations-/Fortschrittsverlust; fremder Owner;
blockierter Start-/Adopt-/Identitäts-/Accounting-/Transfer-/Terminate-/Closepfad;
fehlgeschlagene Terminierung; alle drei unbekannten Teilcloses; gemeinsame Frist;
Custodian-Ausfall vor/nach Cleanup und während Transfers; Replay/Peer/Ticket/
Kanal/Revision/Sequenz; defekter Intent; Integration in den bestehenden Lease-/
Quarantänepfad. Gates warten maximal drei Sekunden, finally gibt sie frei und
Threads werden begrenzt gejoint; gesamter neuer Testfall maximal zehn Sekunden.
Keine Sleep-/offenen Wiederholungsschleifen oder realen Prozesse.

Entwicklungsläufe 52 und 58 Fälle ebenfalls erfolgreich, nicht zusätzlich zählen.
Im kombinierten alten Tasklauf wurden zunächst drei Tasks als UP-TO-DATE behandelt;
diese Ergebnisse NICHT als frische Wiederholung gezählt. Danach alle drei mit
jeweils eigenem `--rerun` tatsächlich ausgeführt (44/60/41). Finale 602/0/0 sind
XML-geprüft, keine abgeschwächten oder übersprungenen Assertions.

### Isolation, verbleibende Voraussetzung und genau eine Folgestufe

Zwei neue isolierte Quellen, eigener Source Set custodianCheck/Testtask, nur
bestehende JUnit-Module; bestehende Outputs lediglich als Testclasspath. Kein
JNA-JAR im neuen Runtimepfad, keine neue Dependency, kein JavaExec, keine Kopplung
an build/check/test/main/manualTest. Keine Änderung von Produkt-/Manualquellen,
DB-/Securityguards, bestehenden Ports/Modellen/Tests oder historischen Evidenzen.
Dokumente ausschließlich angehängt; Hash-/Präfix-/ZIP-Prüfung statt verbotener
Git-Befehle. Vorhandenes Manual-JAR unverändert, nicht neu gebaut/gestartet.

Der Kern ist für die nächste isolierte Bootstrap-/Portimplementierung vorbereitet,
aber KEIN ausführbarer unabhängiger Custodian. Same-JVM-Fakes beweisen weder
vollständigen JVM-Crashschutz noch tatsächliche Windows-Handleübernahme. Native
Duplikation, unabhängige S-/C-Lebensdauer, sichere Peer-/Manifestbindung und reale
Startfence fehlen; Kernelhänger und Same-Token-Angreifer bleiben offene Risiken.
Die Quarantänemarkierung ist keine Behauptung, alle OS-Prozesse seien beendet.
**Der atomare K/H- und der suspendierte Worker-Versuch bleiben GESPERRT.**

**Genau eine kleine Folgestufe zur gesonderten Freigabe:** B-WATCHDOG-4D-EMPTY:
einen festen isolierten Bootstrap für genau ein synthetisches S/C-Paar und eine
LEERE private K-Gruppe implementieren, zunächst fakeprüfen und dann ausschließlich
nach ausdrücklicher OS-Einmalfreigabe einmal ausführen. Held-Owner-/C-Instanzbindung,
bestätigte reduzierte K-Kopie, kontrolliertes Ownerende, unabhängige C-Terminierung
und leeres Accounting/Handle-Ende nachweisen. Kein H, J, Worker, Browser oder Import.
Voraussetzung vor Ausführung: konkreter eigener DuplicateHandle-/Wait-/Query-/
Close-Port, festes Manifest/Peerbootstrap sowie neue STOP-/Endbelege; aktuelle
Fakes ersetzen diese Voraussetzung nicht. Keine automatische Wiederholung und
keine Übertragung alter Einmalfreigaben. Ein solcher Leerversuch wäre lediglich
ein enger Unabhängigkeitsnachweis, noch keine Late-create-/K/H-Freigabe.

Keine Git-, DB-/SQL-, Anwendungs-/Listener-/Browser-/Scraperoperationen, echten
Win32-/Job-/Versuchsprozessstarts, Netzwerkzugriffe, Downloads oder Installationen.
Keine SSRF-/T03-/T04-/T06-Arbeiten und keine Änderung fachlicher Prioritäten.
Verdrahtungsgrenze: Das bisherige OwnedJobAdapter.resourcesClosed belegt weiterhin
nur S-eigene Ressourcen, nicht die neue C-Kopie. Vor einem späteren OS-H-Start muss
der bestehende zentrale Endpfad zusätzlich authentische C-End-/Closebelege binden,
bevor Journalabschluss/Slotentscheidung überhaupt möglich sind. Diese produktive
oder ausführbare H-Verdrahtung ist hier ausdrücklich nicht hinzugefügt worden;
ein Custodianbericht allein ist weder Journalabschluss noch Slotfreigabe.
## Nachtrag 25.10.2026 – B-WATCHDOG-4D-1: leerer S/C-Bootstrap mit Fakes

Ausschließlich isolierte Implementierung und deterministische Prüfungen; keine
C-/S-/H-Prozessstarts, Job Objects, Named Pipes, Netzwerke oder Win32-Aufrufe.
Historische Inhalte, STOP-Marker und verbrauchte Einmalfreigaben bleiben erhalten.
Der synthetische stdio-Host aus 3B-2 ist weiterhin KEIN unabhängiger Custodian.

### Komponenten, Reihenfolge und bestehende Ports

Neuer package-private EmptyCustodianBootstrap im bestehenden custodianCheck-
Bereich. Kein main/JavaExec oder Boot-/Produkt-/Manual-Aufruf. Bestehende
KernelCalls für K-Erstellung, Konfiguration, Readback, leere Beobachtung und
S-Handle-Close wiederverwendet; keine duplizierte Win32-Bindung. Der zusätzliche
Connection-Port betrifft nur C-Instanzprüfung, Übernahme, Kontrollzustellung und
Abschlussbelege. Er ist KEINE reale IPC-/DuplicateHandle-Implementierung.
OuterJobCustodian bleibt die einzige C-Ressourcenkomponente. Ergänzt wurde dort
nur stopBy(deadline): externe erste Cleanupfrist kann verkürzt, nie verlängert
werden. OwnedJobAdapter, StartLeaseGuard, WatchdogModel und alle alten Tests
bleiben unverändert. Kein zweiter Import-Supervisor/Admissionautomat.

Einmalige Reihenfolge im Koordinator:
1. Ticket/Session/K-Binding aus dem vorhandenen neu angelegten Intentstore sowie
   fest gebundene S-/C-Instanzen, Peer und Kanal zuordnen. C nur als injizierter
   Fake vorhanden; seine gehaltene Prozess-Capability muss ALIVE/identisch sein.
2. StartIntentStore.prepare(), bestätigte Veröffentlichung und gültigen Proof
   prüfen. Erst danach K-Erstellungsversuch; kein Lesen als neue Admission.
3. Privates K im eigenen Scope erwerben und sofort verbuchen. Fremde Scopes/Jobs
   werden nie übernommen/geschlossen. Ein unbekannter Create-Ausgang bleibt
   unbestätigt, auch ohne bereits zurückgegebenen Handle.
4. Kill-on-last-close ohne Breakaway konfigurieren und exakt zurücklesen;
   vollständiges/stabiles leeres Accounting verlangen, einschließlich total=0.
5. Übernahme einer separaten C-Kopie an genau die gebundene C-Instanz zustellen.
   C prüft den gehaltenen S und nutzt seinen bestehenden Retain-/Kontrollport.
6. Einmaliges Ack mit Attempt-Capability, Ticket/Session/K-ID, Intentanchor,
   C-/S-Instanz, Kanal, Sequenz 1, exakten QUERY-/TERMINATE-Rechten und zwei
   kontrollierten Jobkopien ohne Vererbung prüfen. Zusätzliche Kopien verhindern
   Bereitschaft/Abschluss. Ack verlangt frischen ALIVE-S-Beleg und aktive
   Überwachung, nicht bloß stattgefundenen Handletransfer.
7. C-Instanz nochmals prüfen, erst danach simulierten READY-Checkpoint setzen.
   pollPeer() widerruft diesen bei später beobachtetem C-Ende oder Identitätsfehler.
   READY ist ausschließlich Diagnose dieser LEER-Probe, KEIN H-Startpermit.
8. Normalen Abschluss oder Abbruch/Ownerende mit unveränderlicher gemeinsamer
   Cleanupfrist behandeln; C-Kontrollspur bleibt getrennt von S-Kernelcalls.
9. S-Kopie, C-Gruppen-/Ressourcenende, S-Kontrollressourcen, gehaltenes C-Ende und
   Journalabschluss getrennt belegen. Keine Berechtigung für H/J/W vorhanden.

### Eigentum, Endbelege und Failure-Verhalten

S hält den Launch-/Ledgerhandle; C nur seine bestätigte Kontrollkopie mit
JOB_OBJECT_QUERY | JOB_OBJECT_TERMINATE. C-Ownerbeobachtung verwendet den gehaltenen
S-Prozess, S-C-Beobachtung den gehaltenen C-Prozess, jeweils Identität mit PID,
Erstellungszeit und Image. Keine Öffnung/Terminierung nach PID. Beide Kopien
werden im Fake getrennt gezählt. Close einer Kopie beweist weder Kill-on-close
noch Gruppenende, solange die andere offen ist. Auch der umgekehrte Fall ist
getestet. Ein gezählter letzter Close ersetzt weiterhin keinen Accountingbeleg.
H/J/W sind nicht beteiligt und können keine Kopien erhalten.

Inventarbelege sind an Attempt/Source gebunden und müssen keine Extrakopien/
Vererbung bestätigen. Das ist eine injizierte Fake-/Portannahme, KEINE Behauptung,
unter Windows den globalen OS-Referenzzähler oder fremde Handleduplikationen
vollständig auslesen zu können. Für den echten Port braucht es nachvollziehbare
kontrollierte Duplikations-/Besitzledger und eine begründete Vertrauensgrenze;
Same-Token-Fremdzugriff bleibt ausdrücklich ungelöst.

Beim Cleanup zuerst Widerruf/Kontrollzustellung. Bei noch laufendem Bootstrap
keine S-Ressource schließen. Jeder tatsächliche Port-/Kernelcall liegt außerhalb
der kurzen Reservierungsmonitore. Vor C-Übernahme kann der Kontrollpfad noch
keinen unbekannten K-Handle terminieren; das wird nicht als unabhängiger Kill
behauptet. Sobald C eine eigene bestätigte Kopie hält, ist der Fake-Kontrollpfad
auch bei blockierter Übergaberückmeldung bedienbar. Verspätete K-/Ack-Rückgaben
bleiben im Ledger, erzeugen nach Abbruch niemals READY.

Nach Rückkehr: bei gehaltenem S-Handle K nochmals leer beobachten und eigenen
Handle einmal schließen. Unbekannter Close -> UNCERTAIN, kein Retry/Journal-
Erfolg. Ist S tatsächlich beendet, darf eine bereits geschlossene S-Kopie nur
über unabhängiges gebundenes Inventar UND gehaltenen Owner-Endbeleg festgestellt
werden; Kommunikationsverlust reicht nicht. Danach C-Endbarriere aus 4C, alle
Kopien weg, C-eigene Ressourcen geschlossen; anschließend gehaltenes C-Ende,
S-seitige C-Beobachtungskopie und Kontrollkanäle abschließen. Fehlende/fremde
Endbelege, unklare Übernahme oder abgelaufene Frist verhindern technischen Erfolg.

Die normale LEER-Probe schreibt COMPLETED erst nach vollständigem technischem
Abschluss; fehlgeschlagener Bootstrap kann nach bestätigtem Cleanup nur ABORTED
schreiben. technicalCompletion ist bei einem Fehler immer false. Diese Zustände
sind KEINE Import-/Slotentscheidung. Quarantäne/Widerruf bleibt beim Stop gesetzt;
keine automatische Wiederfreigabe und keine neue Sitzung desselben Koordinators.
Eine leer bewiesene K-Gruppe sagt nichts über spätere Nachfahren, Nested Jobs,
blockierte CreateProcess-Aufrufe oder sichere H-Resume-Rennen aus.

### Reproduzierbare Tests und Ausführungsnachweis

Vor jedem tatsächlichen Gradlelauf identischer --dry-run; immer Java 21,
--offline --no-daemon. Keine clean-/Vollsuite oder Spring-/DB-Kontexte.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben 7 expliziten Unit-/Modellklassen wie 4C | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest: bestehende OuterJobCustodianTest | 63 | 0 | 0 |
| custodianUnitTest: neue EmptyCustodianBootstrapTest | 44 | 0 | 0 |
| **Unterschiedliche Fälle gesamt** | **646** | **0** | **0** |

602 alte Testfälle/Assertions erhalten und erneut ausgeführt. Neuer Filter ergänzt
nur die zweite Klasse im vorhandenen custodianUnitTest. Dessen 107 Fälle laufen
weiter ohne JNA-JAR (bestehender ClassNotFound-Test erneut erfolgreich).

Die erlaubten 56 Speicher-/Layoutfälle vorher statisch geprüft: identische
Testquellen/Raw-Fakes, Proxy-ProcessRawApi, eigener Speicher und ausschließlich
injizierter StdioLoader in prepareStdio; kein JnaLoader/JnaStdioLoader.open,
Host.current, Kernel32-Native.load oder echter ProcessBuilder-/Win32-Aufruf.
Nur vorhandene lokale JNA-Speicher-/Layoutoperationen im freigegebenen Umfang.
159 ältere JNA-Tests und alle historischen Einzelversuche NICHT ausgeführt.

Neue Fälle decken Reihenfolge, beide Closekopien, Intent-/Kernel-/Readbackfehler,
Owner/C-Instanzfehler, Transferverlust, fehlende/fremde/alte/revidierte Acks,
Replays/Sequenz, zusätzliche/vererbbare Kopien, Owner-/C-Ende während Transfer,
C-Ausfall nach READY, Kommunikationsverlust, blockierte Kernel-/Transfercalls,
Terminierungsfehler, Fristverkürzung, unbestätigte Gruppen-/Inventar-/Close-/
C-Endbelege und keinen automatischen Reset ab. Kein H-/Root-Aufruf ist erlaubt;
die Fakeports schlagen bei jedem solchen Methodenaufruf ausdrücklich fehl.
Gates maximal drei Sekunden, finally löst sie und bounded join/Interrupt;
jeder neue Testfall hat zehn Sekunden JUnit-Limit, keine unbegrenzte Schleife.

Erster Entwicklungslauf: 102 Fälle, ein Fehler im NEUEN Fakeadapter, der seine
Leermeldung nach bereits geschlossenem C nochmals zustellte. Bestehende C-Barriere
wies das korrekt ab. Fakeadapter auf einmalige Zustellung korrigiert, keine alte
Assertion oder C-Endbarriere abgeschwächt. Danach 102, 106 und abschließend 107 Fälle
erfolgreich; Entwicklungsläufe nicht zusätzlich zur obigen Summe zählen.

### Konkrete offene native Fragen und kleinster Folgeschritt

C soll künftig ein eigener kurzlebiger Java-21-Prozess außerhalb von K/S sein,
mit eigenem unveränderlichem Klassen-/Argument-/Umgebungsmanifest, ohne Boot,
Browser oder Worker. Der historische stdio-SyntheticHost ist dafür nicht geeignet
und seine Einmalfreigabe darf nicht übertragen werden. Es gibt jetzt weiterhin
keinen ausführbaren C-Einstieg, echten C-Bootstrap oder IPC-/Native-Prozessport.

Die gegenseitige Instanzbindung muss künftig beim expliziten Prozessbootstrap
gehaltene Handles und bestätigte Geburts-/Imagewerte liefern; eine PID- oder
Pfadangabe im Ack genügt nicht. K-Duplizierung muss Quelle und gehaltenen
C-Zielprozess, minimierte Rechte, Nichtvererbung sowie C-lokale Übernahme mit
einmaligem gebundenem Ack bestätigen. Unklare Übergabe darf keine zusätzliche
Kopie beim Retry erzeugen. Kein DUPLICATE_CLOSE_SOURCE-Fallback. Noch kein
DuplicateHandle-Export/Port oder realer Peer-Bytekanal implementiert.

Bei S-Vollausfall muss ein tatsächlich separater C weiterlaufen und ausschließlich
über eigene gehaltene Handles beobachten/kontrollieren. Same-JVM-Fakes beweisen
das NICHT. Bei C-Vollausfall bleibt eine S-Kopie möglicherweise offen; deshalb
ist Kill-on-close nicht zugesagt. S muss C-Ende unabhängig erkennen, Starts
verweigern und ausschließlich seine eigene Cleanupauthority verwenden. Zugleich
blockierte S-/C-/Kernelpfade bleiben ein reales Risiko ohne harte Killgarantie.
Windows-Dateisystem-Durability und Same-Token-Isolation unverändert offen.

**Echter einmaliger S/C-LEER-Versuch weiterhin NICHT ausführungsreif.** Die
Fake-Reihenfolge ist vorbereitet; konkrete native Prozess-/Handleübertragung und
reale Bootstrap-/Peer-Endbelege fehlen. Kleinster nächster Implementierungsauftrag:
den injizierbaren nativen S/C-Handletransferport mit gehaltenem Zielprozess und
instanzgebundenem Übernahme-Ack konkret implementieren und zuerst fakeprüfen.
Keine weitere breite Modellphase und kein jetzt freigegebener OS-Versuch.
Ein ausführbarer fester C-Einstieg/Manifest und echte gebundene IPC-Endbelege
bleiben vor einer späteren OS-Freigabe zusätzlich ausdrücklich nachzuweisen.
**K/H- und früherer suspendierter Workerprozessversuch bleiben GESPERRT.**

Zwei neue isolierte Dateien, minimaler stopBy-Zusatz im C-Kern und Testfilter-
Ergänzung; Produkt-/Manual-/Guard-/alte Testquellen unverändert. Kein neues
Source-Set, keine Dependency, kein Start-/JavaExec-Task. Dokumente nur angehängt;
Hash-/Präfix-/ZIP-Kontrolle ohne Git. Keine DB/SQL, Migrationen, Anwendungen,
HTTP-Listener, echten C/S/H-/Worker-/Browser-/Scraper-/Win32-Starts, Netzwerke,
Downloads oder Installationen. Nur Gradle-/JUnit-Testwerkzeug-JVMs ausgeführt.
Keine SSRF-/T03-/T04-/T06-Arbeiten und keine Prioritätsänderung.
Zusätzlicher Bootstrap-Regressionsfall: S und C müssen verschiedene lebende Prozessinstanzen sein; zwei Handlekopien derselben PID sind kein unabhängiger Custodian.

## Nachtrag 25.10.2026 – B-WATCHDOG-4D-2: nativer Handletransferport, nur Fakes

Freigabe ausschließlich für isolierten Code, statische Prüfung und datenbankfreie
Fakes. Keine neuen nativen Tests oder OS-Ausführung. Historische Inhalte, STOPs,
Einmalfreigaben und Evidenzen bleiben unverändert.

### Implementierte Komponenten und native API-Grenze

- Win64TransferContract: reine JDK-Porttypen für geliehene Prozess-/Job-
  Capabilities, lokale Prüfkopien und injizierte Raw-Funktionen. Keine JNA-
  Initialisierung durch diesen Vertrag. Prozess-Capabilities kommen ausschließlich
  aus einem vertrauenswürdigen ProcessLedger, nicht aus eingehenden Zahlenwerten.
- Win64TransferBindings: kalte Win64-ABI-Brücke für DuplicateHandle,
  CompareObjectHandles und GetCurrentProcessId. Identitätsprüfung verwendet
  bestehende GetProcessId/GetProcessTimes/QueryFullProcessImageNameW/
  WaitForSingleObject- und GetHandleInformation-Verträge. Keine PID-Suche.
- Win64NativeBindings: separates explizites 26-Export-Profil samt kaltem Loader
  für den fest geprüften System32/kernel32.dll-Pfad. Vorhandene 18-/23-Export-
  Profile unverändert. Fehlende Symbole/ungültige Host-/Ladeparameter bleiben
  gesperrt; kein Suchpfad- oder Startfallback. Profil/Loader NICHT aufgerufen.
- Win64KernelCalls implementiert SourceLedger. Die Quellreservierung verwendet
  das vorhandene JobUse und dieselbe Handle-Registry. Auch lokale Rückkopien
  werden als eigene Resource im vorhandenen Ledger registriert und geschlossen;
  UNKNOWN wird nicht erneut geschlossen. Kein zweites numerisches Handlemap.
- HandleTransferPort koordiniert genau einen Transfer als Ressourcenvertrag,
  keine zweite Lifecycle-/Timeout-/Recoverypolitik. Externe Freigabe und
  StartIntentStore-Proof werden geprüft, kein neuer Timer oder Restart.
- TransferCheckedConnection dekoriert den bestehenden Kanal. EmptyCustodianBootstrap
  verlangt bei einem SourceLedger-Kernel diesen Bestätigungsvertrag. Historische
  reine Fake-Kernel bleiben unverändert; ein Native-Source-Kernel kann nicht mit
  der alten ungeprüften Connection instanziiert werden. Kein H/J/W-Aufrufpfad.

Neue Brücke und neues natives Ladeprofil wurden nur kompiliert/statisch geprüft,
NICHT mittels JNA ausgeführt, auch nicht mit zusätzlichem Memory-/Layouttest.
Der neue Transfertest verwendet ausschließlich JDK-Raw-Fakes. Das ist kein
Nachweis der realen Windows-ABI, Exportverfügbarkeit oder Crashwirkung.

### Berechtigungen, Prozesskontext und K-Objektidentität

Der Port prüft zunächst, dass er im S-Prozesskontext arbeitet (GetCurrentProcessId),
und bindet zusätzlich reale gehaltene Prozess-Capabilities: PID allein genügt nie.
S und C müssen verschiedene Instanzen sein. Die Prozessreservierungen verlangen
SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_DUP_HANDLE (0x101040).
GetHandleInformation prüft Nichtvererbung, nicht die effektive Accessmaske.
Letztere ist Provenienz des vertrauenswürdigen Capabilities-Ledgers; hier wird
KEIN nicht vorhandener GetGrantedAccess-Vertrag erfunden. Der echte Bootstrap
muss diese Prozesshandles nachvollziehbar erzeugen/reduzieren und registrieren.
Ein operativer nativer ProcessLedger-Provider ist noch nicht hinzugefügt worden.

K muss eigene Resource vom Typ JOB sein, aus dem bestehenden Kernelledger
stammen und das erwartete private Kill-on-last-close-/No-breakaway-Readback
liefern. GetHandleInformation prüft den S-lokalen K-Handle. Quell-/Zielprozess
werden über gehaltene Handles, PID/Geburtszeit/Image und Nichtvererbung vor und
nach dem Transfer geprüft. Kein OpenProcess anhand einer empfangenen PID.

1. DuplicateHandle(S-Prozess, S-K, gehaltenes C, access=QUERY|TERMINATE=12,
   inherit=false, options=0). Kein DUPLICATE_SAME_ACCESS/CLOSE_SOURCE.
2. Der zurückgegebene Zahlenwert gehört zur C-Tabelle. Er bleibt privates
   Transfermetadatum und wird niemals in S als lokaler Closehandle verwendet.
3. Kontrollierter Rücktransfer aus genau diesem C-Prozess nach S mit access=QUERY=4,
   inherit=false, options=0. Dadurch existiert kurzzeitig eine zusätzliche,
   ausdrücklich erfasste S-lokale Prüfkopie.
4. Prüfkopie im vorhandenen S-Ledger registrieren, Nichtvererbung prüfen und mit
   CompareObjectHandles gegen das weiterhin reservierte ursprüngliche K vergleichen.
   Gleiches Limit-/Accountingmuster allein würde dieselbe K-Identität nicht beweisen.
5. S-lokale Prüfkopie genau einmal schließen. Erst nach bestätigtem Close,
   erneut unveränderter lebender C-Instanz und weiterhin gültigem Intent ist
   die Zustellung an C zulässig. Bei unbekanntem Close kein Ack/Ready.

Aliaswerte bekannter S-/C-Prozesshandles oder des S-Originaljobs werden niemals
als Prüfkopien geschlossen. Gleiche Zahlen in unterschiedlichen Prozesstabellen
sind dagegen zulässig: sowohl C-Wert gleich S-Original als auch C-Wert gleich
legitimem S-Rückkopiewert werden getestet. Entscheidend sind Prozesskontext,
Native-Provenienz und Ledger-Capability, keine globale Zahlenwert-Blacklist.

### Übergabe, Ack und Cleanup während des Transfers

Eine Delivery wird nur aus dem bestätigten Native-Rückgabewert erzeugt. Sie
bindet frische Transfer-ID, Bootstrap-Attempt, Ticket/Session/K, Intentrevision,
S-Resource und den gehaltenen C-Peer. Eingehende Acks enthalten keinen frei
übernehmbaren nativen Handlewert. C-Zustellung/Claim ist einmalig; das Ack muss
genau diese Delivery, Revision, Attempt-/Peer-Capability, Zielinstanz, Sequenz 1
und den daraus erzeugten Kontrolltoken bestätigen. Vor Annahme wird C erneut
über seinen gehaltenen Handle geprüft. DuplicateHandle=true allein bedeutet
PRESENT, ausdrücklich NICHT ADOPTED oder READY.

Der Dekorator fordert diesen Ack-Nachweis zusätzlich zum vorhandenen Bootstrap-
Ack, bevor die bestehende Reihenfolge READY erreichen darf. C-Retain verwendet
in diesem Profil die bereits übertragene Kontrollkopie, keine weitere Duplikation.
Keine fachliche Import-/Admission-/Slotentscheidung im Transferport.

Native Rückgabe, Ack und C-Close bleiben getrennte Fakten. Fehlendes/fremdes/
altes/doppeltes Ack, Kommunikationseinbruch, C-Ende oder unbekannter Close führen
nicht zur Freigabe. S schließt niemals den C-seitigen Handle. Ein bestätigter
C-Close muss aus dessen gebundenem Ledger/Peer zurückgemeldet werden. Unbestätigte
C-Kopie oder lokale Prüfkopie verhindert den Inventar-/Ressourcenabschluss im
Bootstrap. Extrakopien aus dem bestehenden Inventar werden nicht überschrieben
oder durch eine normalisierte Transfer-ID verborgen. Keine automatische Reparatur.

Native Calls erfolgen außerhalb der kurzen Transfermonitore. Cleanup kann die
Zustimmung widerrufen, während ein Call blockiert. Bei späterer Rückkehr bleiben
entstandene Kopien erfasst, aber es gibt kein Ack/Ready nach Widerruf. Wird die
Rückkehr einer Duplikation durch eine Exception unbestätigt, bleiben die drei
Nutzungsreservierungen für S/C/K im UseScope gehalten. Kein angenommener Abschluss
und kein Lösen durch Timeout. Auch der neue JNA-Duplicate-Out-Puffer bleibt bei
unbestätigter Rückkehr referenziert; kein zweiter Call über diesen Pufferzustand.
Private Teilrückgabewerte bleiben erhalten, sind jedoch keine Closeberechtigung.
Es gibt weder Retry noch neues Zeitbudget oder automatische Slotfreigabe.

### Tatsächlich ausgeführte Prüfungen (25.10.2026)

Vor JEDEM tatsächlichen Gradlelauf identischer --dry-run, immer Java 21,
--offline --no-daemon. Keine Vollsuite/clean-/Spring-/DB- oder OS-Probetask.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Unit-/Modellklassen wie 4D-1 | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest: vorhandene OuterJobCustodianTest | 63 | 0 | 0 |
| custodianUnitTest: vorhandene EmptyCustodianBootstrapTest | 44 | 0 | 0 |
| custodianUnitTest: neue HandleTransferPortTest | 38 | 0 | 0 |
| **Gesamt unterschiedliche Fälle** | **684** | **0** | **0** |

Alle 646 bisherigen Assertions/Testquellen erhalten und erneut ausgeführt.
Der bestehende JNA-ClassNotFound-Test im Custodianlauf ist weiterhin erfolgreich:
auch neue Transfer-/Bootstrapfälle laufen ohne JNA-JAR. Native Brücke/Loader
werden dort nicht instanziiert/aufgerufen. Kein neuer Memory-/ABI-Test.

Die 56 vorhandenen Speicher-/Layoutfälle vorher erneut statisch kontrolliert:
unveränderte Raw-/Process-Proxyfakes und injizierte StdioLoader; kein Aufruf von
JnaTransferLoader, JnaLoader, JnaStdioLoader oder Kernel32-Native.load. Nur der
bereits erlaubte lokale JNA-Speicher-/Layoutumfang. Die 159 historischen JNA-
Bindingtests und alle Einmalversuche NICHT wiederholt.

Neue 38 Fälle: Erfolg mit gesondertem Ack, minimale Masken/options=0,
Source-Kontext/Prozessrechte/Identität/Vererbung/Quellkind, Duplicate-Fehler und
Teilerfolg, Targetende vor/während Übergabe, falsche Gruppe, Rücktransferfehler,
fehlgeschlagene Registrierung/unbekannter Close, fehlende/fremde/alte/doppelte
Acks, unbekannter C-Close, blockierte Duplikation/Identitätsabfrage und Cleanup,
späte Rückgabe, numerische Alias-/Adressraumfälle, zusätzliche Kopie und
Bootstrapintegration samt Ablehnung einer ungeprüften Native-Connection.
Alle Blockaden im finally aufgelöst, Gates maximal drei Sekunden, begrenzte
joins/Interrupt, zehn Sekunden JUnit-Limit je neuem Fall. Keine realen Prozesse.
Entwicklungsläufe mit 138/140/141/142 Fällen ebenfalls erfolgreich; nicht addieren.
Abschließend custodianUnitTest=145 (63+44+38), alle anderen Bestandsfälle ebenfalls
frisch mit explizitem --rerun je Task ausgeführt. Keine geschwächten Assertions.

### Grenzen und kleinste verbleibende Voraussetzung

Die Rücktransfer-/Vergleichslogik beweist im Fake unter dem injizierten Raw-
Vertrag dieselbe K-Objektidentität. Sie beweist weder echte Windows-Marshalling-
Korrektheit noch sichere Kommunikation zwischen real getrennten Prozessen.
Delivery-/Peer-Objektidentität ist nur die heutige Same-JVM-Vertrauensgrenze,
keine kryptografische Authentisierung oder Same-Token-Isolation. Ein realer
C-Empfänger muss den übertragenen Wert in SEINER Tabelle kontrolliert registrieren
und ausschließlich über sein eigenes Ledger schließen; der heutige Claimtoken
ist noch kein tatsächlich registrierter C-Kernelhandle.

Alle kontrollierten Zusatzkopien sind erfasst. Ein fremder privilegierter/
Same-Token-Prozess könnte trotzdem Handles duplizieren oder schließen; es wird
kein vollständiger globaler OS-Handlezensus behauptet. Symbolverfügbarkeit,
CompareObjectHandles-/DuplicateHandle-Verhalten, Rollen-/C-Endpunkt und Crash-
semantik sind nicht OS-abgenommen. Kernel-/JNI-Hänger, verlorene Acks/unklare
Kopien, vollständiger S-/C-Ausfall und Windows-Journal-Durability bleiben Risiken.
Ein in derselben JVM bedienbarer Monitor beweist keine externe Unabhängigkeit.

**Kein echter S/C-Leerversuch jetzt zulässig oder ausführungsreif.** Der native
Transferpfad samt kaltem Binding ist vorbereitet; kleinste nächste Voraussetzung
ist ein ausführbarer, instanzgebundener S/C-Bootstrap mit authentisiertem
C-Übernahme-/Endkanal und registrierten gehaltenen Prozess-Capabilities
(ProcessLedger). Ein fester C-Einstieg/Manifest und seine C-lokale Ledgerübernahme
sind noch nicht implementiert. Diese konkrete Bootstrap-/Peergrenze ist zuerst
isoliert umzusetzen/fakezuprüfen; keine erneute breite Transfer-Modellphase.
Erst danach neuen OS-Einmalauftrag mit eigenen STOP-/Endbelegen ausdrücklich
freigeben lassen. Keine historischen Freigaben/Marker übernehmen.
**S/C-, K/H- und Workerprozessversuche bleiben bis zu eigener Freigabe GESPERRT.**

Keine neuen Dependencies/Source-Sets oder ausführenden Tasks. Produkt-/Manual-
Quellen, Datenbank-/Securityguards und alte Tests unverändert. Dokumente nur
angehängt; Hash-/Präfix-/ZIP-Prüfung ohne Git. Native Porttypen bleiben jobAdapter/
custodianCheck, kein Produkt-/Manual-Classpath. Keine DB/SQL, Schemaänderungen,
Migrationen, Anwendung/HTTP, echten Job-/Prozess-/Handle-/Browser-/Scraperaktionen,
externen Netzwerke, Downloads oder Installationen. Nur Gradle-/JUnit-Testwerkzeug-
JVMs. Keine SSRF-/T03-/T04-/T06-Arbeiten oder Prioritätsänderung.
## Nachtrag 26.10.2026 – B-WATCHDOG-4D-3: isolierte S/C-Einstiege, nur Fake-Abnahme

### Erreichter Stand und klare Freigabegrenze

739 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen: alle bisherigen 684
mit unveränderten Testquellen/Assertions erneut ausgeführt und 55 neue Fälle.
Dies ist KEIN realer S/C-, Win32-, Handletransfer- oder Crashnachweis.
Einstufung: **BLOCKED**. Die Rollenlaufzeit und feste Java-main-Klassen sind
implementiert und kompiliert; die tatsächlich startfähige native Verdrahtung
ist noch nicht vollständig implementiert. Insbesondere ist nativeDriver()
absichtlich leer (null), das feste Profil liefert deshalb intern 78; main
bricht neutral ab. Kein Fake-Erfolg, kein ProcessBuilder-/PID-/Suchpfadfallback.
Die main-Klassen wurden NICHT gestartet, auch nicht zur Prüfung dieses Abbruchs.

### Komponenten und technische Reihenfolge

Neues isoliertes Source Set custodianExecutable, eigener Prüfsatz
custodianExecutableCheck/custodianBootstrapUnitTest. Keine Dependency ergänzt,
kein JavaExec, keine Verknüpfung mit test/check/build/assembleManualTest.
Der ausführbare Kern übernimmt vorhandene Custodian-/Intentquellen ausdrücklich
ohne *Test.java; seine Runtime enthält weder JUnit noch JNA. Native Klassen werden
nur als kalte Porttypen referenziert, nicht geladen/initialisiert oder ausgeführt.
Die Prüfruntime darf zusätzlich bestehende Fake-Fixtures verwenden; JNA bleibt
auch dort ausgeschlossen (ClassNotFound-Assertion erfolgreich).

Neue Dateien unter src/custodianExecutable/java:

- windowsadapter/ScSupervisorMain und ScCustodianMain: getrennte feste Rollen,
  einziges Argument --sc-empty-v1. Keine frei gewählten Befehle, PIDs, Pfade oder
  Providerklassen. Keine Produkt-/Manual-/Gateway-/Controllerintegration.
- ScExecutablePlatform: explizites geschlossenes Native-Gate und injizierbarer
  Driver für prozessinterne Tests. Der reale Driver fehlt noch, siehe BLOCKED.
- ScSupervisor: veröffentlicht StartIntentStore-Proof, ruft erst dann den eigenen
  StartPort zur gehaltenen C-/Kanalbindung auf und übergibt denselben Proof an den
  vorhandenen EmptyCustodianBootstrap. Erst danach K-create/configure/readback,
  Leerheitsprüfung, bestehender Transfer, Ack und READY. Kein zweites prepare().
- ScCustodian: bindet den gehaltenen Owner vor BOUND; ADOPT genau einmal nur mit
  separat im C-lokalen DeliveryPort bestätigter Kontrollkopie. ACK erst nach
  erfolgreichem retain/Live-Prüfung des vorhandenen OuterJobCustodian.
  Ownerabfrage, Terminierung und Endkontrolle bleiben außerhalb blockierender
  Eingaben gesondert aufrufbar. Eine reale unabhängige Treiberschleife ist Teil
  der noch fehlenden nativen Driver-Verdrahtung, nicht durch die Fakes bewiesen.
- ScChannel: feste S/C-Nachrichten BOUND/ADOPT/ACK/STOP/END/CLOSED auf stdio,
  kein Socket, TCP oder HTTP. BOUND bindet die Session, noch keine Transfer-ID:
  die Delivery-ID wird erst nach K-Erstellung/Duplizierung erzeugt. ADOPT darf
  die ID ankündigen, diese allein verleiht aber KEINE Handleberechtigung.
- watchdogmodel/ScFrameCodec: verwendet BinaryIpcCodec/BufferBudget unverändert.
  SC1 ist ein eigenes Profil; die vorhandenen S_H/H_S-Richtungsbytes sind hier
  nur Transportcodes für S/C und berechtigen zu keinem H-Start. max. 512 Bytes
  pro Frame, vorangestellte feste Vier-Byte-Länge vor Allokation geprüft,
  höchstens vier Frames/2048 Bytes je Richtung, lokale Codecreserve 4096 Bytes,
  keine Ergebnisdaten, Nachrichtenqueues oder beliebigen Kommandos. Session,
  Ticket, Kanal, Gruppen-ID, Anchor-Hash, Richtung, Sequenz und Transfer gebunden.
- ScTransferReceiver: verdrahtet den vorhandenen TransferCheckedConnection mit
  dem begrenzten Bytekanal und einem explizit vertrauenswürdigen RemoteLedger.
  Native Transferbestätigung, Wire-ACK und C-lokaler Besitzbeleg sind getrennt.
  Kein Claim/READY aus einem nackten ACK. STOP einmal auf der Kontrollspur,
  Endbelege nur mit END, CLOSED, echtem EOF und anschließend eigenem Kanalclose.
  Ein defekter Kanal liefert UNKNOWN-Peerbeleg, nicht ALIVE oder Owner-Tod.
- HeldProcessLedger: registriert gehaltene lokale Erwerbsergebnisse statt
  eingehender PID-/Handlezahlen; Identität, Nichtvererbung und ALIVE prüfen,
  Nutzungen pinnen, Close erst ohne Nutzer, UNKNOWN niemals erneut schließen.
  Acquisition-Rechte sind Create-/Duplicate-Provenienz, keine behauptete
  GetGrantedAccess-Abfrage. Fehlgeschlagene Registrierungen bleiben im Ledger.
- ScNativeCustodianPort: kalte Zusammensetzung von KernelCalls und gehaltenem
  Prozessledger für C. Übernimmt genau eine bereits registrierte lokale K-Kopie
  mit QUERY|TERMINATE, prüft Limits, fragt Owner über gehaltene Capability ab,
  terminiert/beobachtet nur dieses K und schließt nur eigene Ressourcen.
  Der AcquiredControl-Vertrag muss vom nativen Erwerbspfad belegt werden;
  seine Flags sind kein unabhängiger Kernelbeweis. Keine Root-/H-/J-/W-Operation.

Minimal geändert: EmptyCustodianBootstrap.begin(preparedProof), altes begin()
und alte Prüfungen unverändert erhalten. build.gradle.kts ergänzt nur die
isolierten Source Sets und den expliziten Fake-Testtask.
Die bestehenden Win64TransferBindings/Win64KernelCalls/Win64NativeBindings,
StartLeaseGuard, OwnedJobAdapter und bisherigen Binär-/Syntheticprotokolle
wurden nicht geändert. Keine neuen ABI-/Exportdeklarationen oder JNA-Tests.

### Besitz, Crash, Cleanup und Nachweisgrenzen

S-Launchhandle, temporäre QUERY-Rückkopie und C-Kontrollkopie bleiben getrennt.
Der vorhandene HandleTransferPort bleibt der einzige S-Transfervertrag; kein
S-Close eines C-Rohwerts. Der neue Wirekanal enthält überhaupt keine Handlewerte,
PIDs, Imagepfade oder frei ausführbaren Befehle. RemoteLedger/DeliveryPort sind
lokale vertrauenswürdige Integrationsports, heute nur fakeimplementiert, kein
bereits realisierter zusätzlicher authentisierter Interprozesskanal.

Normales Ende: STOP -> Starts/Kopien nachweislich beendet -> K-Kontrolle und
Gruppenende -> C-Handles/Ownerkopie geschlossen -> END/CLOSED/EOF -> gehaltenes
C-Ende und S-Kanal-/Ressourcenabschluss -> terminaler Intent im vorhandenen Kern.
Terminate=true oder Close einer einzelnen Kopie genügt nicht. Zusätzliche oder
unklare Kopien, unvollständiger Fence, Gruppenquery oder Close sperren Enderfolg.
Blockierte Operationen halten ihre bestehenden Besitz-/Nutzungsbelege; kein
Timeout behauptet Native-Rückkehr. Kein Retry, Reset oder automatische Freigabe.
Late-create/late-ack nach STOP kann READY nicht herstellen. Cleanupfrist wird
nicht erneuert. Nachrichten-/Decoderfehler führen zu STOP/Quarantäne.

S lebt, Kanal weg: Owner bleibt ALIVE bzw. UNKNOWN, nie erfunden ENDED.
S hängt: bestehende Progress-/Operationsfrist widerruft; kein Owner-Tod daraus.
S stirbt nach Adoption: C beobachtet den gehaltenen S, nicht Heartbeats/PID.
S stirbt vor Adoption: kein bestätigter K-Besitz/Endbeleg; C muss über seine
unabhängige Kontrollspur eigenes Ende verlangen, statt unbekannte Handles zu
suchen. Erst eigenes OS-Prozessende könnte unzugängliche Teilkopien schließen.
C stirbt vor READY: Bootstrap gesperrt; nach READY: S muss gehaltenes C-Ende
prüfen und READY widerrufen. Eine verbleibende S-Kopie verhindert weiterhin
Kill-on-last-close. Unbestätigter Cleanup bleibt gesperrt.

Wichtige Startlücke: Vor bestätigtem C-BOUND darf K nicht existieren. Zwischen
C-Create und dessen laufender Ownerüberwachung ist noch kein abgesicherter
Betrieb behauptbar; S-Crash kann einen unbestätigten/suspendierten C-Teilstart
hinterlassen. Nach K-Erzeugung, aber vor C-ACK ist das ebenfalls KEIN READY.
Ein spätes K-/Transferergebnis bleibt im bestehenden Ledger und öffnet nichts.
Für einen späteren realen Lauf muss der native Startprovider diese Teilstarts
behalten und ihre Endbelege liefern; andernfalls STOP/UNKNOWN. Hier kein
hartes Windows-Crash-/Killversprechen oder Wiederanlaufbeleg.

Same-JVM-Objektidentität, zufällige Tickets und eine Pipekennung schützen nicht
vor kompromittierten Prozessen mit denselben Windows-Rechten. Der native Provider
muss die stdio-Enden auf den selbst gestarteten, gehaltenen C zurückführen und
in C das gehaltene S samt lokaler K-Erwerbsprovenienz registrieren. Kein globaler
OS-Handlezensus, keine sichere Same-Token-Isolation, Windows-Powerloss-Durability
oder Rückkehrgarantie für Kernel/JNI-Hänger aus Fakeprüfungen ableitbar.

### Tatsächlich ausgeführte Prüfungen (26.10.2026)

Java 21, vor JEDEM tatsächlichen Gradlelauf identischer --dry-run, immer
--offline --no-daemon; jeweils --rerun an jeder auszuführenden Testtask.
Der erste Dry-Run scheiterte nur am Sandboxzugriff auf die bestehende globale
Gradle-Wrapper-Lockdatei; danach Cachezugriff mit genehmigter Eskalation. Keine
Downloads und kein Test lief in diesem fehlgeschlagenen Dry-Run.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben ausgewählten Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest (63 + 44 + 38 Bestand) | 145 | 0 | 0 |
| custodianBootstrapUnitTest / neue ScBootstrapTest | 55 | 0 | 0 |
| **Gesamt unterschiedliche Fälle** | **739** | **0** | **0** |

Neue Fälle: S-Intent/C-Bindung/K/Ack-Reihenfolge, vorveröffentlichter Proof ohne
Neupublikation, Instanz-/Rollen-/Kanalbindung, verschiedene S/C, falsche/fehlende/
doppelte Acks, Byte-/Sequenz-/Längengrenzen, EOF, Transferport-Wire-Zusammenspiel,
getrennte Endbelege, Prozessledger-Pins/UNKNOWN-Close, fremde Rechte, fehlende
C-lokale Delivery, Ownerende vor/nach Adoption, lebender hängender Owner,
Kommunikationsverlust, C-Ende vor/nach READY, Terminierungs-/Query-/Closefehler,
Extrakopien, blockierte/späte Adoption/C-Bindung und Deadline/Quarantäne.
Vorhandene Bestandsfälle decken zusätzlich late-create und weitere Blockaden ab.
Alle eigenen Thread-Gates im finally freigegeben, begrenzte joins/Interrupt und
zehn Sekunden JUnit-Limit; kein Betriebssystemprozess zu Versuchszwecken.
Entwicklungsläufe 48/53/54 Fälle erfolgreich, nicht zum Endstand addieren.

Die 56 vorhandenen lokalen Speicher-/Layoutfälle vorher erneut statisch geprüft:
Raw-/Process-Proxyfakes, injizierter StdioLoader, kein realer DLL-/Kernel32-Aufruf.
Nur die ausdrücklich fortgeltende JNA-Memory-Ausnahme genutzt. Die 159 alten
JNA-Bindingtests und sämtliche historischen OS-Einmalversuche NICHT ausgeführt.
Kein clean, assemble, Bootkontext, DB, SQL, Listener, Scraper oder echter Browser.

### BLOCKED – kleinste noch notwendige Korrektur und enges späteres OS-Gate

**Einzige verbleibende Implementierungseinheit vor einer OS-Freigabeprüfung:**
den geschlossenen ScExecutablePlatform.nativeDriver durch einen konkreten
instanzgebundenen nativen S/C-Start-/Acquisitionprovider ersetzen. Er muss C
außerhalb von K starten, die ausschließlich diesem Start zugeordneten stdio-Enden
und gehaltenen S/C-Prozesshandles in die neuen Ledger einspeisen, die nachträglich
übertragene K-Kopie tatsächlich im C-Kernelledger registrieren und die unabhängige
C-Owner-/Kontrollspur einschließlich Endbelegen treiben. Diese Erwerbs-/Kanal-
provenienz fehlt heute, nicht das nochmalige Modellieren eines Ack-Automaten.
Der vorhandene Win64ProcessKernelCalls-Start setzt bereits ein Job-/JOB_LIST-
Attribut voraus; ihn für C innerhalb von K wiederzuverwenden würde die notwendige
Unabhängigkeit verletzen. Deshalb kein ungeprüfter Startfallback implementiert.
Auch reduzierte Prozess-Handle-Erwerbe brauchen im Provider nachvollziehbare
Rechteprovenienz; das existierende Transferbinding dupliziert bisher nur Jobrechte.

Danach, ausschließlich nach neuer ausdrücklicher Einmalfreigabe, kleinster Versuch:

1. Frischen fest begrenzten Laufordner und CREATE_NEW-Verbrauchsmarker in einem
   neuen, ausdrücklich benannten .gradle/t02-watchdog-4d-3-<Freigabe>-Bereich
   reservieren, VOR nativer Initialisierung. Heute NICHT angelegt. Historische
   Ordner/Marker/Evidenzen niemals übernehmen, löschen oder wiederverwenden.
2. Feste Java-21-/Classpath-/Rollen-/JNA-Hash-/System32-DLL-Manifeste prüfen,
   Overrides ablehnen. Genau S und ein gehaltenes C, kein H/J/W oder Anwendungsstart.
3. Intent -> eigener C-Start und gehaltene Peer-/stdio-Bindung -> C-BOUND mit
   laufender Ownerüberwachung -> genau ein privates leeres K samt Limits-/Accounting-
   Readback -> kontrollierter Transfer/Rückvergleich/Prüfkopieclose -> C-ACK -> READY.
4. Ein normaler kontrollierter Leerabschluss, keine Crashvariation in derselben
   Freigabe. K active/assigned/total=0, PID-Liste leer, alle bekannten Kopien,
   in-flight Calls, C-/S-Ressourcen, Kanäle/EOF und gehaltenes C-Ende einzeln belegen;
   Intent terminal, unveränderliche neutrale Enddatei. Kein Handle-Rohwert im Log.
5. Sofort STOP/Quarantäne bei fremder Identität/Provenienz, ungeplantem Prozess,
   unerwarteten Handles/K-Mitgliedern, falschen Limits, fehlendem/doppeltem/spätem
   Ack, Deadline, Kanalfehler oder unvollständigem Close/Endbeleg. UNKNOWN niemals
   als Erfolg oder neue Freigabe interpretieren. Keine Wiederholung, kein fremder
   Prozesskill oder automatische Bereinigung. Operator erhält den Restzustand.

ABI/Exportverfügbarkeit, reale Cross-process-Provenienz, Kernelhängerverhalten und
Crashwirkung bleiben danach reale Abnahmethemen, keine behaupteten Vorabnachweise.
**Kein S/C-Versuch jetzt; K/H- und Worker-Versuche bleiben ebenfalls GESPERRT.**

Produkt-/Manualquellen, DB-Guards und alte Tests unverändert. Historische
Dokumentpräfixe werden nur ergänzt. Hash-/ZIP-/Whitespacekontrolle statt Git.
Keine Gitbefehle, DB/SQL/Migration, Anwendungen/Listener, nativen Windowsaufrufe,
Versuchsprozesse, Browser/Scraper, Netzwerke, Installationen oder Dependencies.
Nur Gradle-/JUnit-Werkzeug-JVMs sowie die ausdrücklich erlaubten bestehenden
JNA-Speicherprüfungen. Keine SSRF-/T03-/T04-/T06-Arbeiten oder Prioritätsänderung.
## Nachtrag 26.10.2026 – B-WATCHDOG-4D-4: konkreter nativer S/C-Provider, nur Fakes ausgeführt

### Ergebnis und Geltungsbereich

**READY_FOR_SEPARATE_OS_APPROVAL ausschließlich für einen einzelnen normalen
S/C-Leerversuch.** Der in 4D-3 gemeldete native Start-/Acquisitionprovider ist
jetzt implementiert und kalt kompiliert. Kein echter S/C-/Win32-Versuch wurde
unternommen. Dies ist weder eine OS-Abnahme noch eine K/H-/Worker-/Importfreigabe.
Die historische 4D-3-BLOCKED-Einstufung bleibt als damaliger Stand unverändert.

767 unterschiedliche ausgewählte Tests bestanden, 0 Fehler, 0 übersprungen:
alle bisherigen 739 unverändert erneut ausgeführt und 28 neue datenbankfreie
Provider-/Integrationsfälle. Native Packing-/Loader-/Driverimplementierungen
wurden ausschließlich kompiliert und statisch geprüft, NICHT mittels JNA ausgeführt.

### Konkrete Start- und Acquisitionimplementierung

- ScStartProvider in custodianExecutable implementiert den bisherigen StartPort.
  Genau ein Versuch; gültiger StartIntentStore-Proof und Session vor Erwerb/Start,
  eigener Selfhandle, reduzierte Ownerkopie, suspendierter eigener C, unmittelbare
  Prozess-/Threadregistrierung, gehaltene Identitätsprüfung (PID/Geburt/Image),
  reduzierte C-DUP_HANDLE-Capability, Resume, gebundener BOUND-Austausch und erneute
  Proof-/Instanzprüfung. Kein READY aus CreateProcessW=true oder bloßer PID.
- Die originale selbst durch CreateProcess erworbene C-Prozess-Capability bleibt
  ausschließlich als S-Cleanupauthority bis zum bestätigten C-Ende gehalten.
  Sie besitzt TERMINATE; der zusätzlich reduzierte C-Prozesshandle mit
  SYNCHRONIZE|QUERY_LIMITED_INFORMATION|DUP_HANDLE ausdrücklich NICHT. Rücknahme
  nutzt nur die originale gehaltene Instanz, nie PID-/Namenssuche oder den
  reduzierten Handle. Kein TerminateProcess für S oder fremde Prozesse.
- Win64ScCalls im neuen isolierten scNative-Source-Set implementiert die Calls
  konkret mittels vorhandener Process-/Stdio-/Transferfunktionen. Prozesskopien
  verwenden das bereits gebundene DuplicateHandle mit expliziten Prozessrechten,
  false/0; die bestehenden restriktiven QUERY-/TERMINATE-Jobduplikate unverändert.
  Ein Selfhandle entsteht aus dem vertrauenswürdigen Current-Process-Pseudohandle,
  nicht aus OpenProcess(PID). Neue Exporte ausschließlich TerminateProcess für
  eigene C-Rücknahme und GetStdHandle für C-lokale stdio-Acquisition. Neues kaltes
  Profil löst 28 Symbole auf; alte 18-/23-/26-Symbolprofile unverändert.
- Win64ProcessLayouts/Win64ProcessKernelCalls/Win64NativeBindings unverändert.
  Das neue CreateProcessW verwendet STARTUPINFOEXW (112), PROCESS_INFORMATION (24),
  CREATE_SUSPENDED|EXTENDED_STARTUPINFO_PRESENT|CREATE_UNICODE_ENVIRONMENT|CREATE_NO_WINDOW,
  exakt EIN Attribut HANDLE_LIST, KEIN JOB_LIST und KEIN BREAKAWAY-Fallback.
  lpApplicationName ist der feste lokale Java-21-Pfad, keine Shell oder PATH-Suche.
- Win64StdioChannels ergänzt einen getrennten prepareCustodian-Pfad. Drei eigene
  Parent-/Child-Pipepaare plus genau eine reduzierte Ownerkopie in HANDLE_LIST
  (4*8=32 Byte). Workerpfad bleibt bei seinen drei stdio-Enden/24 Byte und
  unverändert erforderlicher atomarer JOB_LIST-Zuordnung. K existiert beim
  C-Prozessstart noch nicht und wird nicht vererbt. Alle Pipeenden und Teilstarts
  bleiben in ihren bestehenden Ledgers; Ownerkopie bleibt im Provider erfasst.
- Nach Create-Rückkehr werden Child-Pipeenden in S geschlossen, Attributliste
  gelöscht und deren Puffer erst danach freigegeben. Bei unbestätigtem Return
  bleiben Puffer, Kopier-Outparameter und Nutzungen erhalten. Keine globale
  Synchronisierung über CreateProcessW, ReadFile, WriteFile, Wait/Query oder Kill.
- Parent-stdin bekommt einen echten gesonderten Halbabschluss: CHANNEL_END senden,
  dann nur diese Pipe schließen; stdout/stderr bleiben für Endbelege offen. Kein
  vorgezogenes closeAll. stdio maximal 4096 Byte je Hauptstrom im C-Profil,
  Einzelaufruf maximal 512 Byte. stderr eigene begrenzte Lesespur (256 Byte),
  Rohdaten verwerfen, kein Log; unbekannte/überschrittene Diagnose verhindert Erfolg.
  Der unveränderte Workerpfad behält seine bisherigen 512-Byte-Gesamtgrenzen.

### Feste Manifest-, Rollen- und Kanalanbindung

ScSupervisorMain/ScCustodianMain erhalten den gesonderten expliziten Modus
--sc-native-empty-v1. Die alte --sc-empty-v1-Fake-/Vorbereitungsgrenze bleibt
absichtlich geschlossen, einschließlich der bisherigen null-Factory-Assertion.
Der neue Einstieg lädt ausschließlich die fest benannte ScNativeDriver-Klasse
über das isolierte scNative-Classpath, keine Providerklasse aus IPC/Umgebung.
Fehlt dieses isolierte Modul, Abbruch 78; kein Fallback oder Fake-READY.
Keinen dieser main-Einstiege in diesem Auftrag aufgerufen.

ScNativeManifest prüft VOR nativer Initialisierung und VOR dem Start:

- Java 21 unter C:\Program Files\Java\jdk-21\bin\java.exe und kanonischen Workspace.
- Exakt geordnete Classpath-Einträge: build/classes/java/scNative,
  custodianExecutable, jobAdapter, communicationHost, danach genau das bereits
  lokal vorhandene JNA-Core-5.17.0-JAR mit dem bestehenden SHA-256-Pin.
- Keine main/manual/test-/zusätzlichen JAR-/Wildcard-Classpaths, kanonische Pfade.
- Hashfreigabe für Java-Executable, sortierte Klasseninhalte aller vier Verzeichnisse
  und das gepinnte JNA-JAR. Nicht die Integrität der gesamten JDK-Installation
  behaupten: diese bleibt eine vertrauenswürdige lokale Voraussetzung.
- JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/CLASSPATH-Overrides ablehnen;
  jna.nosys=true erforderlich, danach bestehende Host-/ABI-/Ladepfadprüfungen.
- Frische lokale .gradle/t02-sc-native-approval.txt mit genau Session-UUID und
  passendem SHA-256 in zwei Zeilen. Heute NICHT angelegt oder verändert.
  Vor dem DLL-Laden CREATE_NEW-Laufordner .gradle/t02-sc-native-<Session> samt
  consumed-Marker; vorhandenen Ordner nie übernehmen. C prüft denselben Marker.

C-Befehlszeile ist fest (Java, -Djna.nosys=true, geprüfter Classpath, fester
ScCustodianMain, festes Profil), keine Handles/PIDs in Startargumenten.
Explizite C-Umgebung ausschließlich SystemRoot/TEMP/TMP, keine DB-Zugangsdaten
oder sonstige Elternumgebung übernehmen. Keine Passwörter oder Geheimnisse im
Manifest, CLI oder Ergebnislog. Freigabe-/Sessionkennung ist KEIN Same-Token-Schutz.

ScNativeWire ergänzt das native SCN1-Profil auf ScFrameCodec/BinaryIpcCodec und
deren BufferBudget. SC1 unverändert kompatibel halten. Begrenztes Boot-Prelude
(maximal 1536 Byte) bindet Session/Ticket/Gruppe/Anchor/Kanal, tatsächliche S-/C-
Identitäten und die explizit geerbte reduzierte Ownerkopie. C prüft eigenes Image,
PID/Geburt über einen echten Selfhandle, den gehaltenen S, die feste Session und
die tatsächlich eigenen Standardhandles; in C alle diese Handles nicht vererbbar
setzen. Kein PID-only-Watching oder ungebundener zweiter Verbindungskanal.

Frames maximal 512 Byte, maximal sechs je Richtung/2048 Framebytes, getrennte
Richtung/Sequenz, Gruppen-/Anchorbindung und strikte Opcode-Erwartung. Geordneter
Abschluss nutzt den bereits bestehenden binären CHANNEL_END-Typ UND echtes EOF.
Kommunikationsabschluss ist ausdrücklich kein Prozessende. Keine TCP-/HTTP-Sockets.
Neue Windows-Stdiobrücke bleibt kalt, die Fakeprüfung verwendet Speicherpipes.

### Kontrollierter K-Transfer und C-lokaler Besitz

ScNativeConnection implementiert den bestehenden TransferConfirmation-Vertrag.
HandleTransferPort bleibt für Transfer, Rückkopie, Objektvergleich und native
Ack-Bindung zuständig. Vor Transfer wird zusätzlich über gehaltene C-/K-Handles
IsProcessInJob(C,K)=false verlangt. Einmaliger NativeDelivery-Export nur aus der
exakten bestätigten Delivery und Peerbindung, nicht aus irgendeiner Zahl im Ack.

Wichtige Präzisierung zum historischen SC1-Profil: Das neue native SCN1-GRANT
übermittelt den tatsächlichen C-lokalen Jobwert als streng begrenztes Datenfeld.
Das ist für die reale C-Registrierung erforderlich; er stammt ausschließlich aus
S-eigenem bestätigtem DuplicateHandle samt bestehendem QUERY-Rückvergleich. Keine
frei wählbare Handle-/Prozessoperation und niemals Diagnoseausgabe dieses Werts.
C akzeptiert ihn nur vom eigenen gebundenen Startkanal, für dieselbe Session,
Revision und einmalige Transfer-ID, nach Ownerprüfung und ohne Alias auf seine
noch gehaltenen Owner-/Self-/stdio-Capabilities. Alle anderen Opcodes unverändert
ohne native Werte. Ein nacktes Ticket oder Ack berechtigt nicht zum Handleimport.

Win64ScJobCalls nutzt dasselbe Win64KernelCalls-Ledger: tatsächliche lokale Kopie
registrieren, Nichtvererbung, private Limits und leeres vollständiges Accounting
prüfen. Erst dann übernimmt ScNativeCustodianPort genau diese lokale QUERY|TERMINATE-
Kopie. Kein C-CreateJob, keine weitere Kontrollkopie und kein S-Close des C-Werts.
C-Ack erst nach erfolgreichem retain im bestehenden OuterJobCustodian. S prüft
Transfer-ID, Proof erneut unmittelbar bei Ackannahme, gehaltene Instanzen und
bestehenden Ackvertrag. Erst dann bestehendes EmptyCustodianBootstrap-READY.
Bekannte offene zusätzliche S-Kopien aus dem Kernelledger verhindern Inventar-
abschluss; unklare Transfer-/Closezustände bleiben gesperrt. Kein globaler Handlezensus.

### Unabhängige Kontrolle, Endbarrieren und Crashgrenzen

ScNativeDriver ist die konkrete Rollenverdrahtung, kein weiterer allgemeiner
Lifecycleautomat. S verwendet ScSupervisor/EmptyCustodianBootstrap. C verwendet
OuterJobCustodian/ScNativeCustodianPort und den gehaltenen S. C-Eingaben laufen
auf eigener Spur, Ownerquery/Terminations-/Endkontrolle unabhängig davon. S hat
lokale Deadline-/Widerrufsspur; keine davon wartet auf einen vom I/O gehaltenen
Java-Monitor. JVM-Threads sind keine unabhängige OS-Garantie gegen Kernelhänger.

Normal: Intent -> eigener C suspendiert/registriert/gehalten -> Resume und
gebundener BOUND -> genau ein privates leeres K samt Limits/Accounting ->
Duplikation/Rückvergleich/Prüfkopieclose -> C-lokale Adoption/ACK -> READY -> STOP ->
S-originalen K-Handle bestätigt schließen -> FENCE/CHANNEL_END/S-stdin-Halbclose ->
C-Gruppenende und eigene K-/Owner-/Selfressourcen schließen -> GROUP_END/CLOSED/
CHANNEL_END/EOF -> gehaltenes C-Ende und Exitcode -> S-Ledger/Pipes schließen ->
terminaler Intent und neue neutrale result.txt (ready/complete, keine Rohwerte).
Ein Exitcode ungleich null verhindert technischen Erfolg, auch nach Endnachrichten.

Create-/I/O-/Kopier-Return, Besitz-Ack, einzelner Close, Terminate=true, Gruppenende,
Kanalende und tatsächliches Prozessende sind getrennte Tatsachen. Zusätzliche
Kopien, offene Nutzungen, unklare Closes oder unvollständige Endbelege erzeugen
keinen Abschluss. Keine automatische Wiederholung und keine Quarantäneauflösung.
Originale C-Cleanupauthority und reduzierte Dup-/Query-Capability bleiben getrennt;
physischer Close nie während Start-/Kontrolloperationen. Native Puffer bei
unbestätigtem Create/Copy erhalten; spätes Resultat wird erfasst, nie resumed/READY
nach Widerruf. Kontrollierte Rücknahme nur eigener tatsächlich gehaltener C.

Kommunikationsverlust ist kein S-Ende. Bei lebendem hängendem S widerruft der
bestehende Progress-/Deadlinevertrag, ohne ENDED zu erfinden. Bei bestätigtem
S-Ende entscheidet C unabhängig vom S-Kanal über K-Cleanup. C-Crash vor Ack
verhindert READY; nach Ack verweigern gehaltenes C-Ende/fehlende Endbelege einen
Erfolgsabschluss. Erfolgreiches Terminate ohne Gruppenende bleibt unvollständig.
Ein synchron hängender Kernel-/JNI-Aufruf hat keine behauptete harte Rückkehrfrist.

Startlücke ausdrücklich erhalten: S-Ausfall zwischen C-Create und laufender
Ownerüberwachung kann einen unbestätigten/suspendierten C hinterlassen; noch kein
K existiert. Zwischen K-Create und Adoption ist K ausschließlich leer und es gibt
statisch keinen H-/Workerstartpfad. Nicht als abgesicherter Betrieb bezeichnen.
S-/C-Prozessende kann OS-eigene Kopien schließen, ersetzt aber keinen positiven
Endbeleg. Bei UNKNOWN bleibt der Verbrauchsmarker erhalten. Kein späterer H-/Worker-
start vor unabhängig bestätigtem Custodian-READY; solche Starts sind hier überhaupt
nicht implementiert. Ein H-/Workerfall würde neue Schutz-/Crashnachweise benötigen.

### Tatsächlich ausgeführte Prüfungen

Java 21, ausschließlich --offline --no-daemon. Vor JEDEM tatsächlichen Gradlelauf
identischer --dry-run; --rerun pro Testtask. Zunächst kalte Native-Kompilierung und
selektierte S/C-Fakes, anschließend vollständige Wiederholung der 739 Bestandsfälle.
Ein erster Compilelauf scheiterte an einem fehlenden BoundChild-Import, korrigiert;
dabei keine JUnit-Ausführung und keine Sicherheitsassertion abgeschwächt.
Weitere erfolgreiche Entwicklungsläufe nicht zum Endstand addieren.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest (63+44+38) | 145 | 0 | 0 |
| custodianBootstrapUnitTest: bestehende ScBootstrapTest | 55 | 0 | 0 |
| custodianBootstrapUnitTest: neue ScStartProviderTest | 28 | 0 | 0 |
| **Gesamt unterschiedliche Fälle** | **767** | **0** | **0** |

Neue 28 Fälle prüfen tatsächliche injizierte Provider-/Transfer-/Wirepfade:
Registrierung vor Query, feste Herkunft/Rechte, komplette Acquisition/Abschluss,
falsche/wechselnde Instanz, gleiches S/C, falsches Image, Vererbung/Aliase,
fehlender/abgebrochener Intent, Create-/Resume-/Closefehler, fehlende BOUND-Bindung,
verspäteter Start nach STOP/Deadline samt Rücknahme nur mit eigener Full-Capability,
blockierte Handshake-I/O ohne Kontrollmonitor, unbekannter Kill/Close/Preparation,
begrenztes Boot-Prelude, Manifest-/Freigabepolitik und echtes CHANNEL_END/EOF.
Sieben zusammenhängende Transfer-/Speicherpipe-Szenarien: normaler Abschluss,
falsches/doppeltes Ack, fehlender Endbeleg, C-Ende während Duplikation, falsches K,
bekannte Zusatzkopie. Keine redundanten Einzeltests für bloße Zählererhöhung.

Alle bisherigen Assertions/Testquellen unverändert. Die 56 bestehenden JNA-
Memory-/Layoutfälle erneut statisch geprüft: nur Raw-/Proxy-/Loader-Fakes und
bekannter lokaler Speicherumfang. Kein Kernel32-Loader/Win32 aufgerufen.
Die neuen Fälle laufen ohne JNA-JAR und ohne scNative-Runtime; entsprechende
ClassNotFound-Assertions erfolgreich. Win64ScCalls/ScNativeDriver/Win64ScJobCalls
wurden nur kompiliert/statisch geprüft. Keine neuen JNA-Memory-/ABI-Laufzeittests.
159 historische JNA-Tests und sämtliche OS-Einmalversuche NICHT wiederholt.

Zusätzlicher REINER Dry-Run für test/check/build/assembleManualTest bestätigt:
keine scNative-/Custodian-/Probe-/JavaExec-Task im normalen Produkt-/Manualplan.
Diese Build-/Manual-/Volltestaufgaben dabei NICHT ausgeführt. Kein clean/assemble.

### Änderungen, Artefakte und OS-Gate

Neu: ScStartProvider, ScNativeConnection, ScNativeWire, ScNativeManifest,
ScStartProviderTest sowie im isolierten scNative-Set Win64ScCalls,
Win64ScJobCalls und ScNativeDriver. Geändert: build.gradle.kts,
HandleTransferPort (gebundener Export), ScFrameCodec (existing CHANNEL_END),
ScExecutablePlatform/ScSupervisorMain/ScCustodianMain (expliziter neuer Modus),
Win64KernelCalls (lokale Acquisition/Kopieninventar) und Win64StdioChannels
(getrenntes C-Profil/Halbclose). Dokumente nur angehängt. Keine neue Bibliothek,
Version oder Installation; scNative verwendet das vorhandene gepinnte JNA-JAR.
Keine Produkt-/Manual-/DB-/Sicherheitsguard- oder alte Testquellen geändert.

**READY_FOR_SEPARATE_OS_APPROVAL**, beschränkt auf den normalen einmaligen
S/C-Leerversuch. Vor dessen Ausführung sind eine neue ausdrückliche Freigabe,
die kontrollierte Bereitstellung der beschriebenen lokalen Hashfreigabe und ein
frischer Session-/Verbrauchsordner erforderlich. Heute weder Freigabedatei noch
Laufordner/Marker erzeugt, kein main gestartet. Bestehende STOP-/Verbrauchsmarker
und Evidenzen niemals übernehmen, löschen oder umschreiben. Eine neue Freigabe
darf unaufgeklärte frühere Restprozesse/UNKNOWN-Zustände nicht übergehen.

Der spätere Versuch verwendet ausschließlich die festen vier Klassenverzeichnisse
in obiger Reihenfolge und das vorhandene gepinnte JNA-JAR, Java 21, den festen
ScSupervisorMain und das explizite --sc-native-empty-v1-Profil. Kein Gradle-
JavaExec-Starttask und keine Verwendung von --sc-empty-v1 als Native-Fallback.
Manifestfreigabe bindet den dann tatsächlich kompilierten Stand; jede Änderung
an Klassen/JDK-Executable/JAR erfordert neue ausdrücklich geprüfte Hashfreigabe.

Minimaler OS-Nachweis: BOUND/Identitäten/Pipeherkunft, C außerhalb K, genau ein
leeres K (active/assigned/total=0), minimale Rechte/Nichtvererbung, Transfer samt
Prüfkopieclose, einmaliges Ack, normaler kompletter Gruppen-/Ressourcen-/Kanal-
und gehaltenem C-Endabschluss mit Exitcode 0 und terminaler Evidenz. Sofort STOP
bei ungebundener Instanz, fremder Ressource, unbekannter Kopie, falschem Readback,
unerwartetem Prozess/Mitglied, spätem/doppeltem Ack, I/O-/Deadline-/Endbelegfehler.
Keine Wiederholung, keine automatische Bereinigung und kein Prozesskill außerhalb
der konkret eigenen gehaltenen C-Cleanupauthority. H-/Worker-/Crashvariationen
sind NICHT Bestandteil dieses kleinsten normalen Leerversuchs.

Vorbehalte des künftigen OS-Gates: reale ABI/Export-/HANDLE_LIST-/Vererbungs- und
Exit-/EOF-Semantik, geerbte übergeordnete Windows-Jobrichtlinien, Prozesscrash und
Kernelhänger sind durch Fakes nicht bewiesen. Keine Same-Token-Sandbox: ein gleich
privilegierter kompromittierter Prozess kann Code/Dateien/Handles beeinflussen;
Session und Pipegeheimnis sind keine unabhängige Authentisierung dagegen. Kein
globaler Handlezensus oder Windows-Powerloss-Durability-Nachweis. Kein Produkt- oder
Workerrelease aus einem erfolgreichen Leerabschluss ableitbar.

Produkt-/Manual-Artefakte und historische Marker werden per Hash/ZIP geprüft,
Dokumentpräfixe bytegleich erhalten; Whitespace-/Konfliktprüfung ohne Git.
Keine Gitbefehle, DB/SQL/Migration, Anwendung/Listener, echten Windows-/Job-/
Prozess-/Transfer-/Browser-/Scraperstarts, Netzwerke, Downloads oder Installationen.
Nur Gradle-/JUnit-Werkzeug-JVMs und die bekannten erlaubten JNA-Speicherfälle.
Keine SSRF-/T03-/T04-/T06-Implementierung oder fachliche Prioritätsänderung.
Präzisierung der abschließenden Dateiprüfung: Vorher-/Nachher-SHA-256 erfasst
227 vorhandene Quell-/Konfigurations-/Dokument-/Artefaktdateien, davon nur die zehn
oben benannten vorhandenen Dateien geändert; acht neue Quellen. Alle alten
Testquellen und das vorhandene Manual-JAR unverändert, beide historischen
Dokumentpräfixe bytegleich. Die erste Wildcard-Erfassung hatte historische
.gradle/t02-Verzeichnisse nicht eingeschlossen. Diese anschließend ausdrücklich
nur lesend geprüft: alte Schreibzeiten, STOP-/Verbrauchs-/Evidenzdateien vorhanden;
3B2-consumed/result.txt stimmen mit den bereits bekannten historischen SHA-256
überein. Kein vollständiger neu erhobener Vorher-/Nachher-Hashvergleich sämtlicher
historischer Evidenzdateien behauptet. Kein Befehl dieses Auftrags hat dort
Dateien geschrieben oder einen alten Versuch gestartet. Neue native Freigabedatei
und Laufordner fehlen weiterhin. Statische Whitespace-/Konfliktprüfung aller
16 betroffenen Code-/Builddateien und der Dokumentnachträge: 0 Befunde.
## Nachtrag 26.10.2026 – B-WATCHDOG-4D-5: einmaliger OS-Anlauf, PARTIAL/STOP

### Verbindliches Ergebnis

**PARTIAL/STOP, keine erfolgreiche S/C-Leerabnahme. Aktuelles OS-Gate: BLOCKED.**
Genau ein S-Harness wurde gestartet. Der äußere Beobachter brach nach 409 ms
wegen eines unerwarteten Windows-Konsolenhosts ab. Kein zweiter S-/C-Anlauf,
keine Reparatur, Crashsimulation, K/H-/Worker- oder frühere OS-Probe ausgeführt.
Die historische 4D-4-READY-Einstufung bleibt als damaliger Bericht erhalten,
ist für einen weiteren OS-Anlauf aber überholt.

Die Vorprüfung war hinsichtlich der DLL-Zuständigkeit unvollständig: Der lokale
Exportvergleich hätte vor dem Start zeigen müssen, dass CompareObjectHandles
nicht aus kernel32.dll auflösbar ist. Die frühere READY-Einstufung war insofern
zu weitgehend. Keine nachträgliche Lockerung des Gates oder der Prozessallowlist.

### Versuchkennung und unveränderliche Integritätsbindung

- Kennung: B_WATCHDOG_4D_5_20261026_85287824-ae3a-41b6-9cd4-8a944832f352.
- Session: 85287824-ae3a-41b6-9cd4-8a944832f352.
- Lokale, durch die bestehende .gradle/-Regel ignorierte Ablage:
  .gradle/t02-watchdog-4d5-85287824-ae3a-41b6-9cd4-8a944832f352/.
- Artefakt-SHA-256 (Java-Executable + geordnete Klasseninhalte + JNA):
  1d36744fc0ca4396c46572d6077be05762718c615aedb991c8b376b8f8b07bd4.
- manifest.json-SHA-256:
  7f9c8c8ed476375ee66eabba6ef209e9d4c248fc9eb7a21a380dabb8257d0b5e.
- Der externe consumed-Marker verknüpft genau diese Kennung, Session, Manifest-
  und Artefakthashes und NoRetry=true. Er wurde mit CREATE_NEW, Flush(true) und
  SHA-256-Readback dauerhaft VOR dem einzigen S-Prozessstart geschrieben.
- Frische .gradle/t02-sc-native-approval.txt mit Session/Artefakthash vor Start
  angelegt. Der von S danach erzeugte interne native Laufordner
  .gradle/t02-sc-native-85287824-ae3a-41b6-9cd4-8a944832f352/ enthält ausschließlich
  seinen consumed-Marker; dessen Inhalt stimmt mit dem Artefakthash überein.
- Beide Verbrauchsmarker und die verbrauchte Hashfreigabe bleiben erhalten.
  Diese Kennung/Freigabe darf nicht wiederverwendet oder überschrieben werden.
  Flush/Readback ist kein zusätzlich durchgeführter Windows-Powerlossversuch.

manifest.json dokumentiert den damaligen statischen Precheckstatus. Der danach
festgestellte Exportfehler wird in postcheck.json und hier ergänzt, nicht durch
Umschreiben des ursprünglichen Manifests verborgen.

### Vorprüfung, exakter Startweg und Grenzen

AGENTS.md, relevante ROADMAP-/TESTING-Abschnitte und S/C-/Win64-Quellen gelesen;
keine Gitbefehle. Vor dem tatsächlichen Gradlelauf identischer --dry-run:
compileScNativeJava und custodianBootstrapUnitTest mit CLI-Filter ausschließlich
ScStartProviderTest, jeweils --offline --no-daemon. Ergebnis: **28 bestanden,
0 Fehler, 0 übersprungen**. Keine JNA-Runtime im gefilterten Testpfad, kein echter
Vorab-S-/C-Prozess. Die 767 erfolgreichen Fälle aus 4D-4 bleiben historisch;
keine Vollsuite/DB-Tests und keine 159 historischen JNA-Tests wiederholt.

Windows: Microsoft Windows 10.0.26200, OS und Shell X64. Java-Executable:
C:\Program Files\Java\jdk-21\bin\java.exe, Oracle Java 21 / x86_64.
Java-SHA-256: 7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8.
Exakter absoluter Classpath im lokalen manifest.json: vier geordnete Ausgaben
build/classes/java/scNative (10 Dateien), custodianExecutable (80), jobAdapter
(96), communicationHost (52), danach das vorhandene JNA-5.17.0-JAR mit dem Pin
b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0.
Keine Testklassen in diesen vier Ausgaben, kein Produkt-/Manual-/Spring-Classpath.
S-Einstieg ScSupervisorMain --sc-native-empty-v1, geplanter C-Einstieg
ScCustodianMain --sc-native-empty-v1. Keine Shell-/freien IPC-Befehle und kein
H-/J-/Workerstartpfad; C-Start einmalig geguarded, K nur privat/neu/leer vorgesehen.

Statisch vorgesehene LLP64-ABI: Pointer/SIZE_T 8, LONG/DWORD/BOOL 4, WCHAR 2;
STARTUPINFOW 104, STARTUPINFOEXW 112, PROCESS_INFORMATION 24, FILETIME 8;
HANDLE_LIST vier Pointer = 32 Byte. Geplante C-Allowlist ausschließlich
stdin-read, stdout-write, stderr-write und S-Ownerkopie QUERY_LIMITED_INFORMATION |
SYNCHRONIZE (0x101000). Geplante K-Kopie QUERY|TERMINATE (0x000c), Rückkopie QUERY
(0x0004), inherit=false/options=0. Diese C-/K-Operationen wurden NICHT erreicht.
S hätte eine originale gehaltene CreateProcess-Cleanupauthority getrennt vom
reduzierten C-Prozesshandle (0x101040); kein PID-only-Kill und kein S-Close eines
C-lokalen Werts. Keine native Operation unter den eigenen gemeinsamen Java-Locks.

Vorab festgelegte monotone äußere Gesamtfrist 25.000 ms. Im geprüften Harness:
15.000 ms Operationsfrist und bestehendes einmaliges Cleanupbudget 2.000 ms
(unter der freigegebenen Obergrenze 15 Sekunden), kein erneuertes Budget.
Der Abbruch erfolgte wegen unerwartetem Prozess, NICHT wegen Fristablauf.

### Tatsächliche Prozess- und Phasenbeobachtung

| Gegenstand | Tatsächlich beobachtet / nachgewiesen |
| --- | --- |
| S | Genau einmal gestartet, PID 24996, Java-Executable wie oben; Startinstanz über den vom eigenen Process.Start gelieferten SafeHandle festgehalten. |
| S-Geburt | 2026-10-26T19:49:37.1766606Z; FILETIME 134375177771766606. |
| Unerwartetes Kind | conhost.exe, PID 72972, Parent 24996, Image C:\WINDOWS\system32\conhost.exe, Geburt 2026-10-26T19:49:37.1993780Z. Kein Custodian C. |
| Äußerer STOP | Nach 409 ms, UnexpectedChild=true, keine weiteren äußeren normalen Phasen. Kein Ignorieren des Helpers oder erweitertes Allowlisting. |
| C | 0 gestartete Custodian-Instanzen aus dem geprüften Ablauf. Kein C-Identitäts-, Handle- oder Übernahmebeleg. |
| K | 0 erstellte private K-Gruppen aus dem geprüften Ablauf. Kein Readback-/Mitgliedschafts-/Terminierungsbefund. |
| Start-Intent | Native intent-Ablage fehlt. C- und K-Start liegen im geprüften Code zwingend hinter erfolgreicher Intentpublikation; somit nicht erreicht. |
| Handshake/ACK/READY | Nicht erreicht; kein positiver Nachweis. |
| CHANNEL_END/EOF | S/C-Protokoll nicht erreicht. Die äußeren stdout-/stderr-Reads waren beim STOP ebenfalls noch unbestätigt. |
| S-Exitcode/Ende | Beim äußeren STOP war S noch nicht als beendet über den gehaltenen Handle beobachtet; kein Exitcode erfasst. Keinen Code 78 als beobachtet behaupten. |
| Nachcheck | Erhöhte rein lesende Prozessprüfung: weder die aufgezeichneten PIDs noch Kinder von Parent 24996 noch vorhanden. Kein Ersatz für den fehlenden gehaltenen End-/Exitcodebeleg. |
| Prozessbehandlung | Keine externe Termination, kein PID-only-Kill, kein fremder Prozess-/Jobzugriff zur Bereinigung. Der äußere eigene S-Beobachterhandle wurde geschlossen. |

Gezählt werden damit ein synthetischer S und ein unerwartet beobachteter
Windows-Hilfsprozess; kein C und kein K. Die Zählung C=0/K=0 beruht zusätzlich
auf dem nachgewiesen nicht erreichten Intent-/Providerpfad, nicht allein auf
einer möglicherweise zu langsamen Prozessliste. Die lokalen CIM-Abfragen waren
ausschließlich Beobachtung, niemals Managementautorität. Ein erster sandbox-
beschränkter lesender CIM-Nachcheck war nicht verfügbar; der anschließende
rein lesende Nachcheck mit gleichen erhöhten Rechten wie der Launcher gelang.
Keine zusätzliche Start-/Absturz-/Cleanupvariante wurde dadurch ausgeführt.

### Statisch nachgewiesener DLL-Zuordnungsfehler

Nach STOP nur die vorhandenen PE-Exporttabellen als Dateien ausgewertet:
KEIN LoadLibrary/GetProcAddress-Test, KEINE zusätzliche native Probe und kein
Fallback-/zweiter S-Anlauf.

- kernel32.dll-SHA-256:
  e132eba4166256e2f414b91fe7d5386590bc5ea8626aee5af9c9738383cf2ae1.
  Von 28 erwarteten Symbolen fehlt exakt CompareObjectHandles.
- KernelBase.dll-SHA-256:
  5c29b73622ce0976e1ada566f31f2197ba0f8beaea89c3ec033366126172c3d0.
  CompareObjectHandles ist vorhanden. Die Jobfunktionen CreateJobObjectW,
  SetInformationJobObject, QueryInformationJobObject und TerminateJobObject
  fehlen dort: kein pauschaler Wechsel aller Bindings auf KernelBase.
- Win64ScCalls.load verlangt alle Transferfunktionen einschließlich
  CompareObjectHandles vom einzigen kernel32-Modul. Diese konkrete Zuordnung
  ist auf dem geprüften Host inkonsistent und hätte das Vorstart-Gate sperren
  müssen. Sie passt zum Abbruch vor intent/C/K. Die konkrete geworfene Java-
  Exception wurde vom bestehenden stillen Fehlerpfad nicht exportiert und ist
  NICHT als Original-Stacktrace belegt.

Auch die Herkunft/Notwendigkeit des unerwarteten conhost ist noch nicht
abschließend erklärt. Keine zusätzliche OS-Reproduktion, kein Entfernen der
Prozessprüfung und keine pauschale Freigabe beliebiger Konsolenhosts.

### Reststatus, Evidenzen und unveränderte Dateien

Kein positiver vollständiger Ressourcenendbeleg und kein PASS. Beim Nachcheck
keine bekannte laufende S-/Helper-/C-Instanz gefunden; ein gehaltener Wait-/Exit-
Nachweis fehlt. JNA hat im lokalen Versuchordner jna16984566327774069184.dll
(273408 Byte) und die leere .dll.x-Markierung hinterlassen. Beides als Evidenz
bewahrt, nicht gelöscht; keine automatische Bereinigung. Das JNI-Initialisieren
im einzigen echten S-Anlauf ist von der späteren nur statischen DLL-Dateianalyse
zu unterscheiden.

Neue lokale ignorierte Evidenzen: manifest.json, run-once.ps1, consumed,
start-requested, S-identity.json, outer-result.json, postcheck.json,
artifact-check.json und die genannten JNA-Dateien im äußeren Versuchordner;
zusätzlich die neue verbrauchte .gradle/t02-sc-native-approval.txt und der interne
consumed-Marker im nativen Sessionordner. Keine historische Freigabe überschrieben.

343 vorhandene Dateien vor/nach dem Versuch verglichen: vor Dokumentnachtrag
keine Änderungen, insbesondere alle 107 erfassten historischen Marker-/Evidenz-
dateien, sämtliche Quellen und das vorhandene Manual-JAR bytegleich.
Manual-JAR-SHA-256 weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Nur TESTING.md und ROADMAP.md um diesen neuen Stand ergänzt; historische Präfixe
erhalten. Keine Anwendungscode-, Binding-, Guard-, Build- oder Teständerung.
Kein Gitstatus/diff/Commit/Push/Merge, keine Datenbank/SQL/Migration, keine
Produktiv-/Manualanwendung, HTTP-Listener, Browser/Scraper oder externe Abrufe.

### Nächster notwendiger Schritt – nicht in diesem Auftrag umgesetzt

Aktuelles Gate BLOCKED. Zuerst gezielte feste DLL-Zuordnung für den Objektvergleich
in Win64ScCalls.load/Transferbindings und zugehörige Fake-Exportprüfungen korrigieren,
ohne die Jobfunktionen aus kernel32 zu verlagern oder beliebige DLL-Fallbacks
zuzulassen. Den Windows-Konsolenhostbefund statisch einordnen und die enge
Prozess-/Abbruchregel beibehalten. Erst nach erneuter Vorprüfung kann der
Projekteigentümer einen ANDEREN einmaligen OS-Versuch gesondert freigeben.
Kein solcher Folgeversuch heute, keine Reparatur oder automatische Wiederholung.

Der Einzelanlauf bestätigt ausschließlich den festen Java-/Classpath-/Hash-
Freigabeweg bis zum nativen Verbrauchsmarker und die äußere Erkennung eines
unerwarteten Prozesses. Er bestätigt weder unabhängigen Custodianbetrieb noch
K-Erstellung, Transfer, Crashschutz, Ressourcenabschluss, globale Handlezählung,
Same-Token-Isolation oder Durability bei Stromverlust. Voraussetzungen für K/H
wurden funktional NICHT abgenommen; nur ein konkreter vorgelagerter Blocker ist
nun bekannt. **K/H, J und Worker bleiben vollständig GESPERRT.** Keine SSRF-/
T03-/T04-/T06-Arbeit oder Änderung fachlicher Prioritäten.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-6: feste DLL-Trennung, nur Vorbereitung

**Gate: BLOCKED. Kein neuer OS-Versuch und keine neue Einmalfreigabe.** Der
belegte DLL-Blocker ist gezielt korrigiert und fakegeprüft; die Konsolenhost-
Entstehung und die Einbindung des äußeren Endbeobachters sind vor einer neuen
separaten Freigabe noch zu klären. Die frühere 4D-5-Freigabe bleibt verbraucht.
K/H, J und Worker bleiben gesperrt. Keine Änderung fachlicher Prioritäten.

### Historische Evidenz und Ursachenaussage

Manifest, run-once.ps1, outer-result.json, S-identity.json, postcheck.json und
lokale JNI-Reste aus dem 4D-5-Ordner ausschließlich gelesen, nicht ausgeführt
oder verändert. Manifest-SHA-256 weiterhin
7f9c8c8ed476375ee66eabba6ef209e9d4c248fc9eb7a21a380dabb8257d0b5e;
damaliger Artefaktfingerabdruck weiterhin als historische Identität dokumentiert:
1d36744fc0ca4396c46572d6077be05762718c615aedb991c8b376b8f8b07bd4.
Neu kompilierte Klassen sind ausdrücklich NICHT mehr das damals freigegebene
Artefakt; eine spätere Ausführung benötigt eine neue Identitätsprüfung/Freigabe.

Tatsächlich bekannt bleiben S PID 24996, conhost PID 72972 mit Parent 24996,
STOP nach 409 ms, C=0/K=0 und kein Start-Intent. Kein nachträglich behaupteter
Exitcode 78, gehaltenes S-Ende, EOF oder Ressourcenende. Die Originalexception
fehlt weiterhin. Der nachgewiesene Loaderfehler ist eine gut begründete Ursache
des frühen Bootstrapabbruchs, nicht eine als Originaltrace bewiesene Ausnahme.
Primäre S-identity.json und outer-result.json nennen FILETIME
134375177771766606, passend zu 2026-10-26T19:49:37.1766606Z; der sekundäre
postcheck.json enthält abweichend 134375177771766610. Diese bereits archivierte
Präzisionsabweichung wird hier sichtbar gemacht, keine Evidenzdatei korrigiert.

Die System-DLLs erneut nur als PE-Dateien gelesen, ohne LoadLibrary/GetProcAddress:
Kernel32-SHA e132eba4166256e2f414b91fe7d5386590bc5ea8626aee5af9c9738383cf2ae1,
KernelBase-SHA 5c29b73622ce0976e1ada566f31f2197ba0f8beaea89c3ec033366126172c3d0.
CompareObjectHandles fehlt unverändert in Kernel32 und ist in KernelBase vorhanden.
Die vier Jobfunktionen CreateJobObjectW, SetInformationJobObject,
QueryInformationJobObject und TerminateJobObject fehlen in KernelBase.

### Korrektur und unveränderte Sicherheitsgrenzen

Win64DllPlan spezifiziert die exakten absoluten System32-DLLs und Exports in
reinem JDK-Code, ohne JNA-Initialisierung. S/C-Profil: 27 Kernel32-Exporte plus
CompareObjectHandles ausschließlich aus KernelBase; Transferprofil: 25+1.
Die bestehenden 18-/23-Exportprofile behalten ihre Namen, Reihenfolge und Verträge.
Alle Pflichtsymbole beider Module werden vor Bind/Prozessoperationen geprüft.
Fehlender Export: Abbruch, kein anderer DLL-Pfad und kein Retry. Der Plan wird
vom tatsächlichen Transfer-/S/C-Loader verwendet, nicht nur von Testduplikaten.
Dies ist eine feste Vorabspezifikation; die echte JNA-Auflösung erfolgt erst im
später autorisierten nativen Lauf und wurde heute NICHT ausgeführt.

Win64NativeBindings trennt TransferSymbols (Kernel32-Kopierfunktionen) und
ComparisonSymbols (KernelBase). JnaComparisonLoader akzeptiert ausschließlich
C:\Windows\System32\KernelBase.dll, den bisherigen Optionsvertrag und einen
kanonischen regulären Dateipfad. Kein PATH-/Bare-name-/DLL-Fallback. Auch der
bisher unbenutzte JnaTransferLoader verlangt den Vergleich nicht mehr aus Kernel32.
Win64TransferBindings erhält Kopier- und Vergleichsinterface getrennt; der alte
vereinte Functions-Vertrag bleibt nur als injizierbarer Fakevertrag erhalten.
Win64ScCalls und beide nativen Rollen verwenden die getrennten Bindings.

S lädt beide Module weiterhin vor Intent, C-Acquisition und K-Erstellung.
Fehlgeschlagene Vorbereitung schließt nur eigene DLL-Referenzen, ohne einen
globalen Ressourcenabschluss zu behaupten. Handle-/Prozess-/Transferrechte,
STOP-/UNKNOWN-/Cleanupbarrieren, Fristen, historische Drei-PID-Assertion und
sämtliche alten Testassertions bleiben unverändert. Keine Änderungen an Gateway,
Controller, Service, Frontend, Datenbankguards oder Manualprofil.

### Konsolenbefund: geklärte Fakten und verbleibende Grenze

Das feste Java-Image hat PE32+/AMD64 und Subsystem 3 (WINDOWS_CUI), SHA-256
7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8.
Der historische äußere .NET-Start setzte bereits UseShellExecute=false,
CreateNoWindow=true, WindowStyle=Hidden und stdout/stderr-Umleitung. Ein
angeforderter CREATE_NEW_CONSOLE-Start ist daraus nicht belegt; der tatsächliche
native .NET-CreateProcess-Aufruf wurde damals nicht aufgezeichnet. Hidden allein
wäre kein Beweis für Konsolenlosigkeit, CreateNoWindow ist die relevante Option.
Der C-Vertrag enthält bereits CREATE_SUSPENDED | EXTENDED_STARTUPINFO_PRESENT |
CREATE_UNICODE_ENVIRONMENT | CREATE_NO_WINDOW (0x08080404), keine NEW_CONSOLE-,
DETACHED_PROCESS- oder BREAKAWAY-Flags. C wurde historisch nicht gestartet.

Explizite Pipe-Standardhandles sind mit einem konsolenlosen Start vereinbar;
sie benötigen keine Console-Screenbuffer. Keine Umstellung auf javaw.exe,
neue Flags oder größere Prozessallowlist ohne belegte Notwendigkeit vorgenommen.
conhost als Windows-Konsolenhilfe ist plausibel und nicht automatisch ein
Fremdprozessangriff. Die protokollierte Parent-/Image-/Geburtszuordnung belegt
jedoch weder den konkreten AllocConsole-/Terminal-Auslöser noch dessen erwartete
Lebensdauer. Deshalb keine Wiederholung und keine pauschale Freigabe des Helpers.

Offizielle Referenzen:
- [CompareObjectHandles: Kernelbase.lib / Kernelbase.dll](https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-compareobjecthandles)
- [Process creation flags](https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags)
- [Creation of a console](https://learn.microsoft.com/en-us/windows/console/creation-of-a-console)
- [Redirected standard handles](https://learn.microsoft.com/en-us/windows/console/console-handles)

### Begrenzte Diagnose und vorbereitete Besitzerbeobachtung

ScExecutablePlatform fängt Reflection-/Linkage-/Runtimefehler des festen nativen
Einstiegs. ScNativeDriver unterscheidet MANIFEST, NATIVE_LOAD, INTENT, BOOTSTRAP
und FINISH. ScBootstrapDiagnostics gibt nur eine feste SC_STOP_V1-Zeile mit Rolle,
Phase, Kategorie, vorgesehenem exit=78 und end/resources=UNCONFIRMED aus.
Keine Exceptiontexte, Stacks, Umgebungswerte, Rohhandles oder IPC-Inhalte. exit=78
ist ein vorgesehener Rückgabecode, KEIN durch den Besitzer beobachteter Exitcode.
Die Tests begrenzen jede Diagnose auf maximal 192 Zeichen. Sinkausfall eröffnet
keinen Retry. C-Binärstdout bleibt frei von diesen S-Diagnosen.

src/scNative/powershell/ScOwnedObservation.ps1 enthält nur eine nicht automatisch
eingebundene Funktion für einen späteren Besitzerlauf. Sie arbeitet ausschließlich
mit dem übergebenen eigenen Process.Start-Objekt und dessen noch gehaltenem
SafeHandle, wartet begrenzt bis zur bestehenden absoluten Frist und liest erst
nach beobachtetem Ende den tatsächlichen ExitCode. STOP bleibt STOP, auch bei 0.
Kein Start, PID-Lookup, Kill oder Dispose; bei unbestätigtem Ende behält der
Aufrufer seine Beobachtungsautorität. Ressourcenende wird niemals daraus abgeleitet.
Fehlender Exitcode bleibt NULL, insbesondere kein impliziter Cast zu Erfolgscode 0.
Der spätere äußere Einmalharness muss diese Beobachtung vor Handlefreigabe und
separat vom begrenzten stderr-/EOF-Lesen einbinden. Der historische Harness wird
nicht verändert oder wiederverwendet. Die Funktion ersetzt keine Startprovenienz-
prüfung oder neue Freigabe und ist kein selbstständig ausführbarer Versuch.

### Tatsächlich ausgeführte Tests

Java 21; vor jedem tatsächlichen Gradlelauf identischer --dry-run, ausschließlich
--offline --no-daemon, --rerun je Testtask. Zuerst compileScNativeJava und nur
ScLoadPreparationTest: kalte Kompilierung erfolgreich, 28/0/0. Anschließend alle
767 Bestandsfälle und die 28 neuen Fälle vollständig wiederholt, ohne Doppeltzählung:

| Task | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest | 145 | 0 | 0 |
| custodianBootstrapUnitTest (55+28+28) | 111 | 0 | 0 |
| **Java gesamt** | **795** | **0** | **0** |

Neue 28 Fälle: feste Modul-/Exportzuordnung, zwölf fehlende Pflichtsymbole,
Vergleich nur in falscher DLL, Modul-/Bind-/Closefehler, STOP ohne nachfolgenden
Fake-C-/K-Start, begrenzte redigierte Einstiegs-/Phasendiagnosen und Ausfall des
Diagnosesinks. Ergänzende Quelltextassertions prüfen reale Loader-/Phasen-/
Konsolenverdrahtung; ausdrücklich kein nativer Laufzeitnachweis.
Die 56 bestehenden lokalen JNA-Speicher-/Layoutfälle vorher erneut als unveränderte
Raw-/Proxy-/Loader-Fakes statisch geprüft und nur im freigegebenen Umfang ausgeführt.
Keine Kernel32-/KernelBase-Funktion und keine 159 historischen JNA-Tests aufgerufen.

Zusätzlich ScOwnedObservation.Check.ps1: **6 PowerShell-Fakefälle bestanden**
(bekannter Exit, begrenztes Nachbeobachten, Fristablauf, fehlender Exitcode,
geschlossener Handle, Beobachtungsfehler). Beim ersten Testentwurf wurde die
PowerShell-NULL-zu-0-Konvertierung entdeckt und vor dem erfolgreichen Abschluss
korrigiert; keine Testassertion abgeschwächt. Nur synthetische PSCustomObjects,
kein echter Process-/Job-/Handleaufruf. Keine Modell-/Java-Tests doppelt gezählt.

Zusätzlicher reiner Dry-Run test/check/build/assembleManualTest bestätigt weiterhin
keine scNative-/Custodian-/Probe-Task im Produkt-/Manualgraphen. Kein Volltest,
clean, assemble, Boot-/Manualstart oder OS-Einmalversuch ausgeführt.

### Integrität und nächstes Gate

355 definierte Bestandsdateien vorab per Länge/SHA-256 erfasst, einschließlich
119 Evidenz-/Markerdateien aus den drei früheren Experimentablagen, dem 4D-5-
Versuchsordner, dessen nativem Sessionordner und der verbrauchten Hashfreigabe.
Alle 119 bytegleich erhalten. Alte Testquellen/Sicherheitsassertions unverändert;
Manual-JAR weiterhin SHA-256
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Dokumente ausschließlich um neue Nachträge ergänzt, historische Präfixe erhalten.
Quellen-/Whitespace-/Artefaktprüfung ohne Git. Keine neuen Freigabe-/Einmalmarker.

**BLOCKED:** Vor READY_FOR_NEW_OS_APPROVAL muss ein neuer eigener äußerer
Beobachtungsplan die Konsolenfrage unter unverändert strenger Prozesskontrolle
klären sowie gehaltene S-End-/Exitcode- und begrenzte Diagnose-/EOF-Beobachtung
vor Handlefreigabe konkret einbinden. Eine neue pauschale conhost-Allowlist ist
keine Lösung. ScNativeManifest verwendet noch den festen, bereits verbrauchten
Pfad .gradle/t02-sc-native-approval.txt. Dessen alte Freigabe passt außerdem nicht
zum neu kompilierten Artefakt. Vor einer neuen Ausführung muss ein separat
geprüfter frischer Freigabeweg vorgesehen werden, ohne diese Datei zu ändern
oder wiederzuverwenden; heute absichtlich keine Marker-/Manifeständerung.
Historisch fehlende End-/Ressourcenbelege sind nicht nachträglich
heilbar und bleiben ausdrücklich offen. Reale DLL-/ABI-/Pipe-/Transfer-/C-/K-
Funktionsfähigkeit ist trotz grüner Fakes nicht nachgewiesen. Keine neue Ausführung
in diesem Auftrag; weiterer Versuch ausschließlich nach separater Freigabe.

Keine Gitoperation, Datenbank/SQL/Migration, MangaLib-/HTTP-/Browser-/Scraperstart,
S/C/H-/Job-/Workerstart oder native Prozessoperation; keine neuen Dependencies,
Downloads/Installationen. Nur offizielle Dokumentation gelesen, lokale Dateien
statisch geprüft und die genannten freigegebenen Testwerkzeuge ausgeführt.
Keine Arbeiten an SSRF, T03, T04 oder T06. Nach diesem Auftrag anhalten.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-7: Einmalharness nach PC-Neustart abgeschlossen

**READY_FOR_NEW_OS_APPROVAL**, ausschließlich für einen neuen normalen einzelnen
S/C-Leerversuch. Dies ist KEINE Ausführungsfreigabe und KEIN erfolgreicher OS-
Nachweis. Heute weder einen echten Versuch freigegeben/verbraucht noch S/C/H,
Job, Worker, Anwendung oder Browser gestartet. K/H, J und Worker bleiben gesperrt.
Die historische 4D-5-Freigabe bleibt endgültig verbraucht; deren fehlende gehaltene
S-End-/Exitcode-/Ressourcenbelege werden nicht nachträglich als erbracht behandelt.

### Tatsächlicher Wiederaufnahmestand

Nach Neustart AGENTS, relevante Dokumentnachträge und gespeicherte Quellen ohne
Git geprüft. Die Implementierung und Tests waren bereits vollständig gespeichert:
ScAttemptFiles, ScRunEvidence, ScAttemptPreparationTest, ScAttempt.ps1,
ScAttempt.Check.ps1 und Start-ScEmptyOnce.ps1 neu; ScNativeManifest,
ScStartProvider, ScNativeDriver und der bestehende Testfilter in build.gradle.kts
angepasst. Keine Neuimplementierung und keine weiteren Quellcodeänderungen nach
dem Neustart erforderlich. Nur dieser Nachtrag und der Roadmap-Nachtrag ergänzt.

Der vor dem Neustart kommunizierte Zwischenstand 42+6 PowerShell-Fälle bleibt
historisch korrekt. Auf der Festplatte waren zusätzlich sechs Stream-/EOF-Fälle
vorhanden: jetzt 48 neue plus 6 bestehende Fälle, alle nochmals ausgeführt.
Die vorhandenen Java-XML-Berichte enthielten bereits 22 neue Vertragsfälle;
auch die gesamte ausgewählte Java-Prüfung nach Neustart tatsächlich wiederholt.
Keine vor dem Neustart lediglich begonnene Ausführung als Endergebnis übernommen.

### Konservative Konsolenhostklassifikation

Get-ScProcessFinding unterscheidet unerwartete Prozesse, mögliche unbewiesene
Konsolenbegleiter, C-Kandidaten und mit der gehaltenen nativen C-Instanz korrelierte
Beobachtungen. Erforderliche Hinweise: gehaltene S-Identität, PID, Geburtszeit,
vollständiger Imagepfad, Parentbezug und für C die exakte feste Kommandozeile.
C wird erst durch übereinstimmende PID/Geburt/Image mit dem nativen gehaltenen
Erwerbsbericht als eigener C korreliert. Parentbezug beweist keine Jobmitgliedschaft;
der äußere Befund meldet diese ausdrücklich als UNKNOWN, nicht als K-Zugehörigkeit.

Ein passender System32-conhost mit plausibler zeitlicher/Parentzuordnung bleibt
POSSIBLE_CONSOLE_COMPANION_UNPROVEN, Owned=false und RequiresStop=true. Ähnlicher
Name, anderer Pfad, alte/fehlende Geburt oder anderer Parent sind keine Ausnahme.
Mehr als ein beobachtetes Kind verhindert ebenfalls PASS. Keine pauschale
conhost-Allowlist, keine Änderung der historischen exakten Drei-PID-Assertion.
Historischer conhost PID 72972 bleibt ungeklärt; Name/Parent reichen nicht zur
Aufklärung seiner genauen Erzeugung oder Lebensdauer. Die bisherigen NoWindow-
Flags bleiben unverändert, keine neue Start-/Konsolenstrategie eingeführt.

### Integrierte äußere Beobachtung und Ergebnisvertrag

Start-ScEmptyOnce.ps1 verdrahtet den vorhandenen Complete-ScOwnedObservation mit
Invoke-ScAttemptCore. Genau ein Startaufruf hinter erfolgreichem Einmalverbrauch;
auch Start-/Beobachter-/Kanalfehler eröffnen keinen zweiten Versuch. Der Beobachter
verwendet das eigene Process.Start-Objekt/SafeHandle, beobachtetes Image und Geburt.
PID-Abwesenheit wird nie als gehaltener Endnachweis ausgegeben. Die gemeinsame
monotone äußere Frist beträgt unverändert 25 Sekunden, kein erneuertes Budget.

Nach STOP nur begrenztes Nachbeobachten desselben Besitzobjekts. Exitcode nur
nach tatsächlich gehalten beobachtetem Ende lesen; fehlender Code bleibt unbekannt.
Danach begrenzte Reads/EOF auswerten, ERST danach eigenes Dispose: Dispose darf
die noch ausstehende Diagnose nicht selbst abschneiden. Bei unvollständigem Ende,
Reads oder unbekanntem Close bleiben Ergebnis und RetainedOwner unbestätigt;
kein PID-Lookup zur Übernahme, kein Kill oder automatisches Cleanup. Der Aufrufer
muss die bestehende PowerShell-Besitzsitzung und RetainedOwner bei UNKNOWN bewahren;
dies ist keine Zusage gegen den Crash des äußeren Beobachters.

Maximal 1024 Diagnosezeichen, jede freigegebene SC_STOP_V1-Zeile höchstens 192
Zeichen. Freie Rohtexte, unbekannte Phasen/Kategorien und Übergrößen werden
verworfen und verhindern PASS. Ein fester Diagnosecode exit=78 bleibt von dem
tatsächlich beobachteten S-Exitcode getrennt. stdout muss leer und EOF bestätigt
sein. Die Endbeobachtung läuft im bestehenden PowerShell-Prozess, ohne Start-
Process, Shellhelper, Runspace oder separaten Beobachterprozess. CIM fragt lokal
nur lesend nach; seine Parentbefunde sind keine Prozessmanagementautorität.
Die bestehende Beobachter-PID wird separat dokumentiert und nicht als S/C gezählt.

ScRunEvidence publiziert einen begrenzten lokalen SCN2-Endbericht mit Sitzung,
erreichter Bereitschaft, Gruppenende, getrennten C-/S-Ressourcen, terminalem
Journal, ausstehenden Operationen, gehaltenem C-Ende, Close-/Exitbeleg und der
bereits gehalten erworbenen C-Identität. Keine Rohhandles, freien Pfade oder IPC-
Nutzdaten. Werte stammen aus den bisherigen Endbarrieren; keine neuen Endbelege
aus PID-Abwesenheit oder bloßem Close konstruiert. Ergebnisdatei weiterhin CREATE_NEW.

PASS erfordert gemeinsam: gehaltenes S-Ende/Exit 0, geschlossener eigener
Beobachterhandle, saubere begrenzte EOF-Spuren, kein äußerer STOP/Fehler,
korrelierter C und alle vorgesehenen nativen Gruppen-/Ressourcen-/Journal-/C-End-
Belege ohne ausstehende Operation. Start oder EOF allein genügt ausdrücklich nicht.
Bestätigtes S-Fehlerende wird FAIL/STOP, unvollständige Beobachtung oder Endbelege
PARTIAL/STOP. Ein Fehlerbericht behauptet dadurch keinen globalen Ressourcenabschluss.
Frühe feste ENTRY-/MANIFEST-/NATIVE_LOAD-Diagnosen kennzeichnen C/K/ACK/READY als
nicht erreicht; ohne solche Evidenz werden diese Phasen nicht als erledigt erfunden.

### Neuer Einmalfreigabeweg – heute nur Fake-Dateien

Neuer fester Namensraum .gradle/t02-sc-attempts/<kanonische UUID>/ mit extern vorab
zu autorisierender approval.txt. Kein Lesen oder Überschreiben des alten globalen
Hashfreigabepfads. Eine UUID allein berechtigt nicht zur Ausführung: zusätzlich
explizites ExecuteOnce, vorhandene passende Freigabe und beide erwarteten Hashes.
Dieses Skript erstellt weder eine Freigabe noch automatisch eine neue Sitzung.

Der Integritätshash bindet Java-Image, die vier geordneten isolierten Classroots,
das unverändert gepinnte JNA-JAR und die drei tatsächlichen PowerShell-Laufzeit-
skripte. Java und Besitzer verwenden denselben Namens-/Byte-/Reihenfolgevertrag.
Pfade/Reparsepoints, JAR-Pin, Klassenzahl und Größen werden geprüft. Vor jedem
späteren echten Versuch sind die dann kompilierten Artefakte neu zu prüfen;
die alten Hashes autorisieren diesen Stand NICHT.

Der äußere consumed-Marker bindet SC_CONSUMED_V2, UUID, Artefakthash und Hash der
Freigabedatei. CREATE_NEW + Flush(true) + Hashreadback vor dem einzigen Start;
danach erneuter Artefaktvergleich. Vorhandener Marker, alter Markerinhalt,
abweichende Freigabe, vorhandenes native-Verzeichnis oder geänderte Artefakte:
STOP ohne Start/Retry, auch bei bereits verbrauchter, aber fehlgeschlagener Anlage.
S und C erhalten nur die kanonische Sitzungskennung als feste JVM-Property und
prüfen den konsumierten Vertrag nochmals. S legt seinen nativen Unterordner
einmalig an, C prüft dessen Bindung. Kein alter Marker wird übernommen.

Heute existiert im Workspace weiterhin KEIN .gradle/t02-sc-attempts-Verzeichnis.
Alle neuen Freigabe-/Kollisionsprüfungen nutzten temporäre synthetische Fake-
Wurzeln mit nicht ausführbaren Fake-Artefakthashes; keine echte Freigabe erzeugt.

### Tatsächlich nach Neustart ausgeführte Prüfungen

Vor JEDEM tatsächlichen Gradlelauf identischer --dry-run, Java 21 und ausschließlich
--offline --no-daemon. compileScNativeJava nur kalte Kompilierungsprüfung (UP-TO-DATE),
je Testtask --rerun. Keine Vollsuite, Integration, JavaExec-/Probe- oder OS-Aufgabe.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest | 145 | 0 | 0 |
| custodianBootstrapUnitTest: 55+28+28 Bestand und 22 neue | 133 | 0 | 0 |
| **Java gesamt** | **817** | **0** | **0** |

Zusätzlich **48 neue PowerShell-Fakefälle und alle 6 bestehenden erfolgreich**.
Neue Java-Fälle prüfen Sitzungs-/Pfad-/Hash-/Markerbindung, historische falsche
Marker und konservative Endberichtwerte. PowerShell prüft den tatsächlichen
Harnesskern mit Fakeports, Einmalverbrauch/Kollision/fehlender Freigabe,
Hashänderung, Loaderabbruch, fehlendes ACK/Ende/Exit, Beobachter-/Closefehler,
Kommunikationsverlust, Deadline, Konsolenbefunde und begrenzte Stream-/EOF-Reads.
Der echte Launcher wird ergänzend als AST geprüft und ohne ExecuteOnce am
frühen geschlossenen Gate abgewiesen; sein realer Startpfad wird NICHT aufgerufen.

Die 56 lokalen JNA-Speicher-/Layoutfälle nach Neustart erneut statisch geprüft:
unveränderte Raw-/Proxy-/Loader-Fakes, ausschließlich bisher freigegebener eigener
Speicherumfang. Keine Kernel32-/KernelBase-Funktion und keine 159 historischen
JNA-Bindingfälle. Alte Assertions nicht abgeschwächt. Historische früher grüne
Läufe nicht zu den hier angegebenen Zahlen addiert.

### Integritätsprüfung und verbleibende Nachweisgrenzen

Nach Neustart 366 definierte Dateien per SHA-256/Länge erfasst. Bis zu diesem
Dokumentnachtrag ALLE unverändert; danach ausschließlich die beiden Dokumente
ergänzt. Darunter 119 historische Evidenz-/Markerdateien, vorhandene Guards,
alte Testquellen und das Manual-JAR. Die frühere nur im Arbeitsspeicher gehaltene
4D-7-Hashliste war nach Neustart nicht mehr verfügbar: der vollständige Vergleich
belegt deshalb den Wiederaufnahmezeitraum, keinen erfundenen lückenlosen
366-Dateien-Vergleich über den Neustart hinweg. Zusätzlich bekannte historische
Hashanker erneut passend: 3B2 consumed/result.txt, 4D5 manifest.json und Manual-JAR.
4D5-Freigabe und interner consumed-Inhalt entsprechen weiterhin der archivierten
Sitzung 85287824-ae3a-41b6-9cd4-8a944832f352 und deren altem Artefakthash.

Manual-JAR-SHA-256 unverändert
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
In den vier nativen Laufzeit-Classroots keine Testklassen. Reiner Dry-Run für
test/check/build/assembleManualTest bestätigt keine S/C-/Probe-/Custodian-
Verknüpfung zum normalen Produkt-/Manualgraphen; diese Aufgaben dabei nicht
tatsächlich ausgeführt. Historische Dokumentpräfixe erhalten, Whitespace- und
Konfliktmarkerprüfung ohne Git. Keine Produkt-/Manual-/Guardänderung.

Alle drei vorab prüfbaren Voraussetzungen sind damit umgesetzt. Reale DLL-/ABI-,
stdio-/C-/K-Funktion und Windows-Verhalten bleiben Gegenstand einer späteren
separaten OS-Freigabe. Same-Token-Manipulation, Kernel-/CIM-Hänger, Crash des
äußeren Besitzers und Powerloss-Durability sind keine durch Fakes bewiesenen
Garantien. Ein erneut auftretender unbewiesener conhost führt weiterhin STOP
statt stiller Ausnahme. Ein sehr kurzlebiger nicht beobachteter C führt ebenfalls
keinen künstlichen äußeren Identitätsnachweis herbei. Keine automatische Wiederholung.

Keine Gitoperation, Netzwerkzugriffe, Downloads/Dependencies/Installationen,
Datenbank/SQL/Migration, Anwendung/HTTP, S/C/H-/Job-/Worker-/Browser-/Scraperstarts
oder native Prozess-/Joboperationen. Nur erlaubte Testwerkzeuge und lokale
Dateiprüfungen. Keine SSRF-/T03-/T04-/T06-Arbeit oder fachliche Prioritätsänderung.
Nach diesem Bericht anhalten; keine eigene Windows-Abnahme beginnen.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-8: Vorprüfungs-STOP vor erstem Start

**BLOCKED / PRESTART_STOP. Kein echter OS-Versuch gestartet.** Die neue begrenzte
Einmalfreigabe wurde nicht verbraucht. Kein Sitzungsverzeichnis, keine neue UUID,
kein ausführbares Manifest, keine approval.txt und kein consumed-Marker erzeugt.
Das neue .gradle/t02-sc-attempts-Verzeichnis existiert weiterhin nicht.
4D-5 und alle früheren Freigaben/Evidenzen bleiben unverändert verbraucht.

Die verbindliche Vorprüfung des Auftrags verlangt vor dem ersten Start den
Nachweis aller Schutzbedingungen. Dabei wurde ein konkreter Implementierungs-
blocker festgestellt: Der äußere STOP ist nur eine Beobachtungs-/Ergebnisentscheidung
im PowerShell-Prozess, kein an S gebundener Widerruf der Ausführungsberechtigungen.
Deshalb keine Ausführung, kein Reparatur-/Vorversuch und kein Retry.

### Belegter Pfad und Risiko

- ScAttempt.ps1, Invoke-ScAttemptCore: Bei RequiresStop/zusätzlichem Kind oder
  Beobachterfehler wird nur $stop=true gesetzt und die äußere Schleife verlassen
  (aktuell Zeilen 152–158). Danach ruft finally Complete-ScOwnedObservation auf
  (Zeile 161), das ausschließlich wartet/Exitcode beobachtet. StopRequested wird
  im Bericht gespeichert, aber nicht an S übertragen.
- Start-ScEmptyOnce.ps1 verdrahtet Start, Identity, BeginReads, Children, Reads und
  Evidence. Es gibt keinen äußeren Revokationsport und keinen von S empfangenen
  STOP-/Startfence-Vertrag. Kein solcher Kanal wird durch das Berichtsflag erzeugt.
- ScNativeDriver.runSupervisor läuft davon unabhängig: Die eigene Deadline ruft
  live.stop()/starts.rollbackSuspended auf; begin/finish und lokale Fehlerpfade
  kennen jedoch keinen äußeren Konsolenhost-/Beobachter-STOP.
- ScStartProvider.allowed prüft ausschließlich den lokalen revoked-Zustand und
  seine Frist. cancel() setzt diesen lokalen Zustand. Der PowerShell-Beobachter
  erreicht diese Methode nicht. Auch ScSupervisor.stop ist nur lokal verdrahtet.

Somit könnte S nach äußerem STOP noch C erstellen/resumen, K erstellen oder
Transfer-/READY-Phasen ausführen, solange kein interner Fehler/Timeout eintritt.
Dies ist ein statisch belegter fehlender Kontrollpfad, kein heute beobachteter
OS-Ablauf. Die korrekte spätere Bewertung als PARTIAL/STOP würde ein unerlaubtes
Fortschreiten nicht verhindern. Ein bloßes Nachbeobachten oder Abwarten der
internen Deadline erfüllt die geforderte unmittelbare Start-/Resume-Revokation nicht.

Die 4D-7-READY-Einstufung war hier zu weitgehend und wird für eine weitere
Ausführung durch BLOCKED ersetzt. Historische Berichte und grüne Fakeergebnisse
bleiben erhalten. Die Tests belegten STOP-Klassifikation, Einmalverbrauch und
Endberichtbarrieren, aber keine prozessübergreifende äußere Revokation.

### Tatsächlicher Ausführungs- und Nachweisstand

| Gegenstand | Ergebnis dieses Auftrags |
| --- | --- |
| Ausgeführte S-/C-Prozesse / erstellte K-Gruppen | 0 / 0 / 0 |
| Einmalverbrauch / neuer Sitzungsordner | Nicht erfolgt / nicht angelegt |
| Bootstrap, C-Bindung, K-Konfiguration, Transfer, ACK, READY | Nicht ausgeführt |
| Native Gruppen-/Handle-/Ressourcen-/Journalbelege | Keine neuen Belege |
| S-/C-Ende, Exitcodes, EOF, native Diagnosen | Nicht anwendbar: keine gestarteten Instanzen |
| Beobachtete neue Konsolenbegleiter | Keine Prozessbeobachtung ausgeführt |
| Bereinigung | Keine erforderlich oder versucht: keine Versuchsressourcen angelegt |
| Windows-Eigenschaften neu nachgewiesen | Keine; ausschließlich statische Quellen-/Dateiprüfung |

Keine Gradle-/JUnit-/PowerShell-Faketests oder zusätzlichen Testprozesse in 4D-8
gestartet. Die 817 Java-Fälle und 48+6 PowerShell-Fakes bleiben Nachweise aus
4D-7, keine heute erneut behauptete Ausführung. Vollständige neue Artefaktbindung
und Freigabebereitstellung wegen des früheren Vorprüfungs-STOP nicht vorgenommen.
Keine DLL geladen, keine Win32-Prozess-/Job-/Handleoperation oder CIM-Prozessabfrage.

366 definierte Bestandsdateien vor dem Nachtrag per Länge/SHA-256 erfasst,
einschließlich 119 historischer Evidenz-/Markerdateien und des Manual-JAR.
Nur TESTING.md/ROADMAP.md ergänzt; Quellen, Guards, Builddatei, alte Marker und
Produkt-/Manualartefakte erhalten. Manual-JAR-SHA-256 weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Historische Dokumentpräfixe und Whitespace/Konfliktmarker ohne Git geprüft.

### Kleinste notwendige Korrektur vor einer weiteren Freigabe

Eine sitzungs-/instanzgebundene äußere STOP-/Revokationsspur an den vorhandenen
S-Kontrollkern anbinden, unabhängig von dessen normalen blockierenden Ablauf.
Sie muss bestehende Start-/Resume-/Bootstrapberechtigungen widerrufen und späte
Erfolge als unbestätigt behandeln, ohne erneuerte Frist, Ersatzstart oder PID-Kill.
Bereits laufende native Operationen bleiben mit ihren Besitz-/Endbelegen getrennt.
Vor einem OS-Gate zunächst Fakes für äußeren STOP vor Create/Resume/K-Anlage,
während Übergabe/ACK und bei Beobachterfehler sowie ausstehender Rückgabe ergänzen.
Keine dieser Korrekturen in diesem reinen Einmalversuchsauftrag umgesetzt.

Kein automatisches Fortsetzen nach Korrektur; weiterer Ausführungsauftrag nur
nach erneuter Vorprüfung und ausdrücklicher Bestätigung. Kein S/C-Erfolgsnachweis,
keine Freigabe für K/H, J, Worker oder Firefox. Keine Git-/Netzwerk-/DB-/SQL-/
Migrations-/Anwendungs-/Browser-/Scraperoperation oder Dependencyänderung.
Nach Dokumentation anhalten.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-9: äußere STOP-Spur und gemeinsame Startlease

**READY_FOR_NEW_OS_APPROVAL** auf Implementierungs-/Fakeebene, ausschließlich für
einen neuen normalen einzelnen S/C-Leerversuch. Keine Windows-Ausführung heute.
Die 4D-8-Freigabe ist nicht automatisch wieder aktiviert. Jeder spätere reale
Versuch benötigt eine neue ausdrückliche Freigabe und erneute Vorprüfung.
K/H, J, Worker und Firefox bleiben unverändert GESPERRT.

### Getrennter gebundener Kontrollpfad

ScOuterStop und ScOuterControl.ps1 ergänzen im bereits privaten, sitzungsgebundenen
Versuchsordner einen kleinen Kontrollbriefkasten. Wiederverwendet werden die
vorhandene StartIntentStore.Binding/Anchor-Identität und die 512-Byte-Grenze des
engen S/C-Protokollprofils. Kein Netzwerk, Named-Pipe-Server, Dienst oder allgemeiner
Kommandokanal. Der normale ScChannel/ScFrameCodec-/ScNativeWire-Kanal bleibt für
S/C-Bootstrap und Cleanup zuständig und ist vom Kontrollthread getrennt.

Feste Nachrichten: BIND/ADMIT/BOUND für die anfängliche geschlossene Startlease,
STOP/REVOKED für den endgültigen Widerruf und CLOSED für das Ende der Kontrollspur.
Feste Dateien outer-binding, outer-admit, outer-bound, outer-stop, outer-ack und
outer-end; CREATE_NEW statt Überschreiben. Jede Nachricht höchstens 512 ASCII-
Bytes, begrenztes Lesen von maximal 513 Bytes. Eine unvollständige Publikation
ohne abschließendes LF ist noch keine Nachricht. Kein freier Befehl/Dateipfad
und kein Prozesshandle im Protokoll. Vollständige Kontrollinhalte werden nicht
in Berichte oder Exceptiontexte übernommen.

Bindung: Versuch-/Session-UUID, Intentticket, K-Gruppenkennung, Startrevision 1,
Anchorhash und PID/Geburtszeit der bereits gehaltenen eigenen S-Instanz. S erwirbt
seine bestehende eigene Prozess-Capability vor Veröffentlichung. Der Besitzer
vergleicht diese Daten mit seinem eigenen Process.Start-/SafeHandle-Identitätsbeleg.
PID/Geburt werden als Dezimaltext ohne JSON-Fließkommarundung übertragen. Keine
PID-only-Prozessöffnung oder Name/Parent-basierte Managementautorität.

Alte Sitzungsverzeichnisse werden nicht gelesen; der Cross-session-Fall ist
explizit geprüft. Eine falsche vollständige Nachricht im eigenen gebundenen
Briefkasten gilt als Kanalkorruption: lokale Startlease fail-closed widerrufen,
kein ACK für einen vermeintlich gültigen fremden STOP. Neue Sitzung/Instanz oder
veraltete Revision können dadurch keine eigene Berechtigung erhalten. Beschädigte
oder fremde Capabilities werden besitzerseitig vor dem Senden verworfen.

### Eine gemeinsame atomare Revokationsbarriere

ScRevocationFence ist die gemeinsame Autorität für S-Startlease, Resume,
Bootstrapbindung, K-create/configure, Handlekopien, GRANT, ACK-Annahme und READY.
Sie startet geschlossen. Erst das passende ADMIT öffnet sie; STOP oder Fristablauf
widerruft endgültig. Wiederholte Admission/STOP öffnen nichts neu.

Eine Reservierung ist noch KEIN Native-Beginn. Permit.commit prüft dieselbe Lease
nochmals atomar und erfasst den verbindlich begonnenen Aufruf als outstanding.
STOP und commit werden unter demselben kurzen lokalen Monitor linearisiert.
Nach wirksamem Widerruf kann kein neuer commit oder READY-Commit erfolgen.
Kein Monitor wird über delegierte Native-/Kanaloperationen gehalten.

**Garantiegrenze:** Commit liegt vor dem delegierten Aufruf, nicht beweisbar
genau am Windows-Kerneintritt. Ein bereits verbindlich committeter Aufruf kann
nach STOP physisch in die DLL gelangen oder spät zurückkehren; er wird dann als
bereits laufend behandelt. Keine Behauptung, Windows-Aufrufe würden rückwirkend
verhindert. Nicht committete Reservierungen scheitern nach STOP. Eine bestätigte
Rückgabe löst die Nutzungsreservierung; Exception/unklare Rückkehr behält sie als
UNKNOWN. Keine Timeout-bedingte Freigabe und kein Reset.

ScFencedCalls verdrahtet dieselbe Lease mit dem tatsächlichen ScStartProvider-
Port; ScFencedKernel mit dem tatsächlichen Leer-K-Port. Erhalt/Registrierung
später Ressourcen erfolgt weiterhin im bisherigen Ledger, bevor eine nächste
Phase erwogen wird. ScNativeConnection prüft/gatet GRANT, ACK und Übernahme gegen
dieselbe Autorität. ScSupervisor.allowed und der abschließende READY-Commit
verhindern späte Bereitschaft nach STOP. NativeDriver.finish(false) verwendet
die vorhandenen Cleanupbarrieren; keine neue Recovery-/Restartlogik.

Der bestehende S-Start-/Resume-Leasepfad ist ScStartProvider. StartLeaseGuard
gehört zum weiterhin gesperrten H-in-K-/Workerpfad und wird nicht irreführend
als verwendete C-Startautorität bezeichnet oder für diese Stufe umgebaut.
ScChannel, ScFrameCodec und die alten Worker-/Modellassertions bleiben erhalten.

Direkt zugehöriger Teilstart-Race korrigiert: rollbackSuspended verbraucht seine
einzige native Terminierungsberechtigung erst, wenn die gehaltene C-Instanz
identifiziert ist. Ein frühes Verlangen während nur teilweise registrierter
Rückgabe erfindet keinen Identitätsbeleg und zählt nicht als ausgeführter Kill.
Die spätere bestätigte eigene Rückgabe kann einmalig konservativ behandelt
werden; kein zweiter nativer Terminate-Aufruf oder blindes Close eingeführt.

### Unabhängigkeit, ACK und Endnachweise

Ein eigener begrenzter S-Kontrollthread pollt den getrennten Briefkasten ohne
Abhängigkeit vom normalen stdio-Reader oder einer blockierten Create-/Resume-/K-/
Transfer-/ACK-Operation. Zuerst wird die gemeinsame Lease widerrufen, anschließend
REVOKED publiziert und die vorhandene lokale provider.cancel()/supervisor.stop()-
Spur einmalig benachrichtigt. Ein Callbackfehler eröffnet keine Wiederholung und
verhindert einen vollständigen Abschluss. Alle tatsächlichen Normalberechtigungen
hängen bereits am gemeinsamen Fence; das ACK benötigt keinen Rücklauf des
blockierten Normalaufrufs.

Das ACK bestätigt ausschließlich den wirksamen Leasewiderruf. Sein outstanding-
Wert zählt committete Normalphasen/Leasenutzungen, einschließlich gebundener
Bootstrap-I/O, NICHT alle OS-Handles oder Cleanupaufrufe. ACK mit outstanding=0
beweist weder C-/S-Ende noch Gruppenende, Kernelrückkehr oder Ressourcenabschluss.
ScRunEvidence.complete bleibt bei ausstehenden Leasenutzungen oder unbestätigtem
Kontrollabschluss false; die übrigen vorhandenen Endbelege bleiben getrennt.

Start-ScEmptyOnce setzt RequireOuterControl zwingend. Die optionale alte
Core-Signatur ohne diesen Schalter bleibt ausschließlich für die unveränderten
historischen Fake-Fixtures; der native S beginnt unabhängig davon geschlossen
und benötigt immer das passende ADMIT. Kein realer permissiver Fallback.
ScAttempt übermittelt STOP höchstens einmal auch bei Beobachter-/End-/Diagnose-
Fehlern. Fehlender Port, NULL-Antwort, S-/Bindungsfehler oder fehlendes ACK ergeben
UNKNOWN/STOP. Ein STOP-ACK ersetzt weiterhin keinen vollständigen Endnachweis.
Berichte enthalten nur gebundene Identitätsmetadaten und Zustandsflags, keine
vollständige Capability oder Nachrichtenkopie.

Der Besitzer wartet auf Bindung höchstens fünf Sekunden und auf STOP-ACK höchstens
eine Sekunde, jeweils verkürzt durch dieselbe bestehende absolute äußere
25-Sekundenfrist. Kein neues Gesamtbudget. S verwendet seine einmalige Frist
bereits vor DLL-Laden; die vorhandene Cleanupfrist wird nicht erneuert.
Kontrollthread-Joins verwenden nur Restzeit, niemals join(0) als unbegrenztes Warten.
Native Gruppen-/Peer-/Kanal-/Journalbarrieren und UNKNOWN-Regeln bleiben wirksam.

### Beobachterausfall und ausdrücklich nicht bewiesene Garantien

Vor ADMIT bleibt S geschlossen. Ein normal gefangener Beobachterfehler versucht
die gebundene Revokation einmalig und kann nicht PASS werden. Nach ADMIT ist der
harte Absturz des äußeren PowerShell-Prozesses ohne aktive STOP-Publikation NICHT
garantiert sofort erkennbar; eine dafür unabhängige OS-/Ownerleasebarriere ist
nicht Bestandteil dieses Kontrollkanals. S hat weiter seine interne Frist,
aber daraus wird keine garantierte Crashrevokation konstruiert.

Bei vollständig eingefrorener JVM, blockiertem Dateisystem oder nicht mehr
erreichbarem Kontrollthread bleibt die STOP-Bestätigung unbewiesen. Dateilese-/
Schreibgrößen und lokale Wartebudgets sind begrenzt, OS-I/O ist dadurch nicht
hart präemptierbar. Kein Erfolg aus Prozessabwesenheit, kein fremder PID-Kill.
Same-Token-Manipulationsschutz und Powerloss-Durability werden nicht behauptet.
Diese Nachweisgrenzen sperren Crash-/H-/Workerfreigaben und bleiben für die spätere
Windows-Abnahme sichtbar; sie sind kein durch Fakes ersetzter nativer Erfolgsbeleg.

### Tatsächlich ausgeführte Prüfungen

Java 21, vor jedem tatsächlichen Gradlelauf identischer --dry-run, ausschließlich
--offline --no-daemon. Neue Native-Verdrahtung nur kompiliert, niemals ausgeführt.
Nach Korrekturen gezielte Races und anschließend abschließenden Gesamtprüfsatz
erneut ausgeführt; Entwicklungsläufe nicht zu den unterschiedlichen Fällen addiert.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest | 145 | 0 | 0 |
| custodianBootstrapUnitTest: 133 Bestand + 40 neue | 173 | 0 | 0 |
| **Java gesamt: 817 Bestand + 40 neue** | **857** | **0** | **0** |

PowerShell: **78 Fälle bestanden**, davon alle 54 bisherigen (48+6) und 24 neue.
Die alten Testdateien und Assertions nicht geändert oder abgeschwächt. 56 lokale
JNA-Memory-/Layoutfälle vor Ausführung statisch erneut als unveränderte Raw-/Proxy-/
Loader-Fakes geprüft; ausschließlich der bekannte erlaubte Speicherumfang.
159 historische JNA-Bindingfälle und alle OS-Einmalversuche NICHT wiederholt.

Neue Java-Fälle prüfen vor/nach Reserve/Commit, vor und während Create/Resume/K-
Anlage/Konfiguration/Transfer/Bootstrap-I/O, echte Speicherwire-GRANT-/ACK-Blockaden,
spätes ACK/EOF, READY-Sperre, laufenden Cleanup, doppelte und fremde Nachrichten,
falsche Revision/Instanz, alte Sitzungsverzeichnisse, Teilpublikation, Fristablauf,
Callback-/ACK-Publikationsfehler, unklare Native-Rückgabe sowie teilweise und spät
registrierte eigene C-Ressourcen. Der tatsächlich eingesetzte Fence-/Port-/
Supervisor-/Wirepfad wird mit Fakes verwendet, nicht nur Quelltextmuster geprüft.
Ergänzende Verdrahtungsassertions ersetzen ausdrücklich keinen Windows-Test.

Neue PowerShell-Fälle prüfen echte begrenzte Datei-/Nachrichtenfunktionen mit
temporären synthetischen Daten und den integrierten Harness mit Fakeports:
Admission, Session/Revision/S-Identität, idempotentes STOP, ACK mit weiterhin
laufenden Operationen, fehlendes/falsches ACK, fehlender S, Beobachter-/Sendefehler,
NULL-Antwort, ausbleibender Kontrollabschluss und unveränderte Einmalstartsperre.
Kein echter Prozess oder Windows-Kontrollaufruf zur Demonstration gestartet.

### Dateien, Integrität und Gate

Neu: ScRevocationFence.java, ScFencedCalls.java, ScFencedKernel.java,
ScOuterStop.java im custodianExecutable-Bereich; ScOuterStopTest.java im
custodianExecutableCheck-Bereich; ScOuterControl.ps1 und ScOuterControl.Check.ps1.
Geändert: ScStartProvider, ScSupervisor, ScNativeConnection, ScRunEvidence,
ScNativeManifest, der isolierte ScNativeDriver, ScAttempt.ps1,
Start-ScEmptyOnce.ps1 und der bestehende Testfilter in build.gradle.kts.
TESTING/ROADMAP ausschließlich um Nachträge ergänzt. Keine neue Bibliothek,
Version, Starttask oder Produkt-/Manualintegration. Der Artefakthash bindet nun
auch das vierte Laufzeitskript ScOuterControl.ps1 auf beiden Seiten in derselben
Reihenfolge; frühere Hashfreigaben autorisieren diesen Stand nicht.

366 vorhandene definierte Dateien vorab per Länge/SHA-256 erfasst, darunter 119
historische Evidenz-/Markerdateien. Historische Präfixe/Marker/Freigaben,
Produkt-/Manual-/Guarddateien und alte Testquellen erhalten. Manual-JAR weiterhin
SHA-256 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Normale test/check/build/assembleManualTest nur per Dry-Run auf unveränderte
Trennung geprüft; keine Vollsuite/Integration oder Artefakt-Neubauten.
In den vier Laufzeit-Classroots keine Testklassen. Hash-/Präfix-/Whitespace-
Prüfung ohne Git. Kein realer .gradle/t02-sc-attempts-Ordner angelegt.

Der konkrete äußere STOP-Implementierungsblocker ist geschlossen; die neuen
Race-/Negativnachweise sind ausschließlich Fakes. READY_FOR_NEW_OS_APPROVAL
bedeutet nur Bereitschaft für die separat zu autorisierende normale Leerabnahme,
keine Zusage für Owner-/JVM-Crash, Kernelhänger oder spätere Workerstarts.
Keine echte Freigabe/Verbrauchsdatei, S/C/H-/Job-/Worker-/Prozessabfrage/-Termination,
DB/SQL/Migration, Anwendung/HTTP, Browser/Scraper, Netzwerk, Installation oder
Gitoperation in diesem Auftrag. Keine SSRF-/T03-/T04-/T06-Umsetzung.
Anschließend stoppen; keine eigene Windows-Abnahme und kein Aktivieren von 4D-8.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-10: OS-Ausführung vor Launcherstart blockiert

**BLOCKED / APPROVAL_REJECTED_BEFORE_START.** Die statische Vorprüfung wurde
abgeschlossen. Die automatische Ausführungsfreigabeprüfung hat anschließend den
Toolaufruf für den echten Einmalversuch bereits vor Start seines PowerShell-
Launchers abgelehnt. Begründung der Prüfung: keine ausdrückliche Freigabe in den
vertrauenswürdigen Benutzervorgaben; der angehängte Auftrag enthält diese Freigabe,
sie wurde vom Reviewer jedoch nicht als solche anerkannt. Keine Umgehung, keine
zweite Ausführungsanforderung und kein Wechsel auf einen indirekten Startweg.
Dies ist kein beobachteter nativer Bootstrapfehler und kein durchgeführter OS-Test.

### Vorprüfung und vorbereitete Einmalidentität

AGENTS und die einschlägigen TESTING-/ROADMAP-Nachträge sowie die tatsächlichen
S/C-, Win64-, Kanal-, Transfer-, PowerShell- und Fencingkomponenten gelesen.
Kernel32 bleibt für die 27 vorgesehenen Exporte zuständig, CompareObjectHandles
ausschließlich für KernelBase. Beide DLL-Dateihashes entsprechen dem 4D-6-Stand;
keine DLL geladen und keine Exportfunktion ausgeführt. Java-Image SHA-256
7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8;
JNA 5.17.0 weiterhin
b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0.

Vier Laufzeit-Classroots: scNative 10, custodianExecutable 92, jobAdapter 110,
communicationHost 52 Dateien, zusammen 264; keine Testklassen. Für sämtliche
zugehörigen Java-Quellen einschließlich der eingebundenen Custodian-/HostPrep-
Kerne existiert eine mindestens ebenso neue Hauptklasse. Das ist eine statische
Aktualitätsprüfung, kein neuer Compile-/ABI-Nachweis. Keine JVM-/Classpath-
Overridevariablen aus der geprüften engen Namensliste vorhanden; der reale
Launcher leert zusätzlich sein Kindprozess-Environment.

Gehaltene S-/C-Identitäten, unveränderte vier C-Vererbungsziele (stdin/stdout/
stderr und reduzierte S-Kopie), exakte HANDLE_LIST, suspendierter C-Start ohne
JOB_LIST/BREAKAWAY und begrenzter K-Transfer QUERY|TERMINATE mit QUERY-Rückkopie
im realen Code geprüft. K-Limits, leere Mitgliedschaft und C außerhalb K werden
im nativen Pfad abgefragt, heute jedoch NICHT als OS-Eigenschaft nachgewiesen.

Unabhängiger S-Kontrollthread und PowerShell-STOP sind über den sitzungsgebundenen
Dateibriefkasten mit derselben ScRevocationFence verbunden. Create/Resume,
K-create/configure, Transfer, Bootstrap-I/O, ACK-Annahme und READY hängen an
dieser Autorität. Bereits committete Aufrufe bleiben bei STOP outstanding;
späte Rückgaben erlauben kein erneutes READY. STOP-ACK beweist nur Widerruf.
CHANNEL_END/EOF, gehaltenes Ende/Exitcodes, K-Ende, Ledger-/Handle-/Ressourcen-
abschluss und äußere Diagnosebeobachtung bleiben gesonderte Kriterien.
Konservative conhost-STOP-Klassifikation unverändert. Äußere Gesamtfrist 25 s,
S-Frist einmalig 15 s, vorhandenes engeres Bootstrap-Cleanupbudget 2 s;
keine Frist verlängert. JVM-/Filesystem-/Kernelhänger und harter äußerer
Beobachtercrash bleiben die dokumentierten Grenzen, keine Crashgarantie.

Nach bestandener statischer Vorprüfung wurden ausschließlich neue vorbereitende
Dateien unter .gradle/t02-sc-attempts/ba92a0e4-3857-4394-804f-b7ef1eef46e1 angelegt:
approval.txt, manifest.json und run-once.ps1. Der Wrapper verwendet den vorhandenen
Launcher und bewahrt bei UNKNOWN dessen originale Besitzsitzung; kein Ersatz-
Prozessstarter. Parserprüfung des Wrappers: 0 Fehler. Historische Dateien nicht
übernommen oder geändert.

- Versuch: ba92a0e4-3857-4394-804f-b7ef1eef46e1
- Artefakt-SHA-256: 6c0a994d9418331b6f5c4103a553dbdaa9b51e94642459f401d846e800d0dda3
- Freigabe-SHA-256: 4b8d8e79150e99203bd93e45627fa0145f96691c52b11ffbe51859d52ce99d3b
- Manifest-SHA-256: 316ff277a5f99d87dd1aa59a5e9e61184de089263b2c1e39a236a0fc884664e6
- Wrapper-SHA-256: 95c25bf78750f336d736263d343abf9e2c47b518d687e8a45c33e6770ef63b06

### Tatsächlicher Ablauf und fehlende Evidenz

Genau eine Tool-Ausführungsanforderung, vor CreateProcess des Launchers abgelehnt.
Der vorgesehene atomare Einmalverbrauch wurde NICHT ausgeführt: consumed fehlt,
native/ fehlt, outer-result.json fehlt. Kein S gestartet, kein C gestartet,
kein K erstellt (jeweils 0); kein OS-Handshake, Übernahme-ACK, READY, conhost-
Befund, STOP-Versand oder STOP-ACK. S-/C-Exitcodes und CHANNEL_END/EOF sind
nicht anwendbar beziehungsweise nicht beobachtet. Kein Cleanup eigener nativer
Ressourcen erforderlich, aber auch kein nativer Ressourcenendnachweis erbracht.
Die vorbereitete Freigabe bleibt unbenutzt; daraus folgt KEINE automatische
Erlaubnis für einen späteren Start. Historische 4D-5-Freigabe bleibt verbraucht.

Keine Tests oder Builds wiederholt. Vorhandene XML-Berichte der 857 ausgewählten
Javafälle gelesen: 0 Fehler/0 übersprungen; 78 PowerShell-Fakes bleiben das
historische Ergebnis von 4D-9, heute nicht erneut ausgeführt. Kein neuer nativer
Funktionsnachweis. 159 historische JNA-Fälle unverändert unberührt.

### Integrität und weiteres Gate

Vorher-/Nachher-SHA-256-Aggregate über Pfad, Länge und Dateihash identisch:
251 Quell-/AGENTS-/Build-/Ignoredateien, 264 Laufzeitdateien, 119 historische
Evidenz-/Markerdateien sowie das Manual-JAR. Historisches Manifest weiterhin
7f9c8c8ed476375ee66eabba6ef209e9d4c248fc9eb7a21a380dabb8257d0b5e;
Manual-JAR weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Produkt-/Manual-/Guardquellen nicht verändert. TESTING und ROADMAP ausschließlich
um diesen neuen Status ergänzt, historische Texte erhalten. Kein Git verwendet.

Weiterhin BLOCKED durch die automatische Ausführungsfreigabeprüfung; keine
Reparatur oder OS-Wiederholung. Vor einer späteren Ausführung ist die Freigabe
direkt im Chat zu bestätigen und erneut zu prüfen; keine bestehende Zustimmung
oder vorbereitete Datei eigenständig reaktivieren. K/H, Worker, Browser und
Scraperintegration bleiben gesperrt. Keine DB/SQL/Migration, Anwendung/HTTP,
Netzwerk, Installation oder Arbeit an SSRF/T03/T04/T06. Nach Bericht anhalten.

## Nachtrag 28.10.2026 – B-WATCHDOG-4D-10: einmaliger OS-Lauf nach direkter Freigabe

**PARTIAL/STOP; weitere OS-Ausführung GESPERRT.** Nach dem zuvor dokumentierten
Prestart-Block hat der Projekteigentümer direkt im Chat genau die vorbereitete
Sitzung ba92a0e4-3857-4394-804f-b7ef1eef46e1 und den unveränderten Manifeststand
ausdrücklich freigegeben. Erneute Integritäts-/Berechtigungsprüfung erfolgreich;
die neue Toolfreigabe wurde erteilt. Genau EIN echter Leerversuch ausgeführt,
kein zweiter Start, keine Reparatur und keine Änderung von PASS-/STOP-Kriterien.
Der vorherige Nachtrag bleibt als historische Prestart-Evidenz erhalten.

### Identität und Einmalverbrauch

Vor dem Aufruf fehlen consumed, native/, äußere Ergebnisse und Kontrollnachrichten.
Manifest, Wrapper und Freigabedatei hashgleich zum vorbereiteten Stand. Java/JNA,
vier Classroots und vier Laufzeitskripte erneut mit dem bestehenden Algorithmus
geprüft: Artefakt-SHA-256 weiterhin
6c0a994d9418331b6f5c4103a553dbdaa9b51e94642459f401d846e800d0dda3.
Keine gefundenen JVM-/Classpath-Overrides aus der engen Prüfliste. Der Launcher
prüft die Fingerprints zusätzlich unmittelbar vor und nach Einmalverbrauch.

- Sitzung: ba92a0e4-3857-4394-804f-b7ef1eef46e1
- Freigabe-SHA-256: 4b8d8e79150e99203bd93e45627fa0145f96691c52b11ffbe51859d52ce99d3b
- Manifest-SHA-256: 316ff277a5f99d87dd1aa59a5e9e61184de089263b2c1e39a236a0fc884664e6
- Wrapper-SHA-256: 95c25bf78750f336d736263d343abf9e2c47b518d687e8a45c33e6770ef63b06
- consumed-SHA-256: a3ea943faaf9b65ea01be25f711c50ba9bcdc4133510e90e29365cf5c91c3974
- outer-result.json-SHA-256: 8e5273383c80c87eea8cf7200e975873d468290026cee9d7f2978792e10ce4d3
- native/result.txt-SHA-256: dd02e16118af70347b7e0507286e27d3c591d88bfe3b67db27652e6a2898919

Atomarer CREATE_NEW-/Flush-Verbrauch vor dem einzigen S-Start im vorhandenen
Launcherpfad; exakte consumed-Tupelbindung anschließend erneut geprüft. Nativer
Verbrauchsmarker vorhanden und inhaltlich gleich dem Artefakthash. Diese Freigabe
ist jetzt endgültig VERBRAUCHT, auch wenn das Ergebnis nicht PASS lautet.

### Tatsächlich beobachtete Prozesse und STOP

Äußerer PowerShell-Beobachter PID 20592, nicht als S/C gezählt. Genau ein S durch
das eigene Process.Start-Objekt gestartet: PID 33932, festes Java-21-Image,
gehaltene Geburtszeit FILETIME 134376986430902825
(2026-10-28T22:04:03.0902825Z). Ein C durch den nativen Provider gestartet:
PID 32356, im S gehalten erfasste Geburtszeit 134376986442231462
(2026-10-28T22:04:04.2231462Z), gleiches festes Java-Image.

Zusätzlicher beobachteter Prozess C:\WINDOWS\system32\conhost.exe, PID 38036,
Parent 33932, CIM-FILETIME 134376986431590780. Klassifikation unverändert
POSSIBLE_CONSOLE_COMPANION_UNPROVEN, Owned=false, JobMembership=UNKNOWN,
RequiresStop=true. Name/Parent/Beginn reichen nicht zum Besitznachweis.
Auch mehr als ein beobachtetes Kind verhindert PASS. Keine conhost-Ausnahme,
keine fremde Terminierung und keine Änderung der historischen Drei-PID-Assertion.

Die CIM-Beobachtung des C enthält FILETIME 134376986442231460, der gehaltene
native C-Beleg dagegen 134376986442231462: Abweichung 2 Ticks (=200 ns).
Die exakte bestehende Korrelationsregel akzeptiert diese Werte nicht; C bleibt
im äußeren Befund CUSTODIAN_CANDIDATE/Owned=false, nicht HELD_CUSTODIAN_CORRELATED.
Kein Verlust von Präzision des nativen Textwertes behauptet und keine Toleranz
eingeführt. Eine Auflösungs-/Konvertierungsabweichung der CIM-Seite ist plausibel,
ihre genaue Ursache wurde hier nicht mit einem weiteren OS-Versuch untersucht.

Äußere Kontrollbindung tatsächlich erfolgreich, ADMIT und BOUND vorhanden.
Konservativer STOP wurde einmalig übermittelt. REVOKED-ACK mit outstanding=1
beweist den Widerruf der gemeinsamen Lease; Session-/Revision-/S-Identitätsbindung
aller Kontrollnachrichten nachträglich geprüft, ohne ihre Rohinhalte zu publizieren.
Kein späteres READY: native ready=false. outstanding=1 bezeichnet zum ACK-Zeitpunkt
eine committete Leasenutzung, NICHT ein nachgewiesen hängendes Kernelkommando
oder eine Handleanzahl. Aus der Evidenz lässt sich die konkrete laufende Phase
nicht eindeutig bestimmen. Exceptions werden weiterhin konservativ UNKNOWN.

### Erreichte Phasen und getrennte Endbelege

| Bereich | Tatsächlicher Nachweis |
| --- | --- |
| S-/C-Start | Je ein Start; gehaltene S- und native C-Erwerbsidentität vorhanden |
| DLL-/S-Vorbereitung | S gelangte über den festen nativen Loader zu Intent und eigener Identität; kein erneuter Kernel32-CompareObjectHandles-Ladefehler beobachtet |
| Intent | native/intent/r1.bin und owner.lock vorhanden; kein terminaler Journalbeleg |
| Äußere Startlease/STOP | ControlBound=true, STOP gesendet, ACK bestätigt, Effective=true, Outstanding=1 |
| S/C-Bootstrap-BOUND | Nicht gesondert belegt; äußeres BOUND ist kein S/C-Handshake-ACK |
| K-Anlage/-Anzahl/Limits/leere Mitgliedschaft | Nicht positiv belegt; der knappe Endbericht erlaubt keine verlässliche Rekonstruktion der erreichten K-Phase und keinen erfundenen K=0-Nachweis |
| C außerhalb K / Handletransfer / QUERY-Rückkopie / Übernahme-ACK | Nicht nachgewiesen |
| READY | Nicht erreicht: ready=false |
| Gehaltenes S-Ende | HeldEndObserved=true, ExitCodeObserved=true, tatsächlicher Exitcode 78 |
| Äußere stdout/stderr | StdoutEof=true, Diagnose-EOF=true; begrenzte gültige FINISH/INCOMPLETE-Diagnose |
| Äußerer eigener Beobachterhandle | Nach Endcode und Reads geschlossen: OwnerHandleClosed=true |
| Kontrollspur-Ende | outer-end gebunden bestätigt; ersetzt keinen S/C-Kanal-/Ressourcenabschluss |
| C-Ende/Exitcode | cEnd=false, cExit=unknown; nicht gehalten nachgewiesen |
| S/C-CHANNEL_END und Protokoll-EOF | Kein vollständiger Nachweis; äußeres EOF ist davon getrennt |
| Gruppe und Ressourcen | groupEnded=false, cResources=false, sResources=false, cHandlesClosed=false |
| Journal / offene Operationen | journalTerminal=false, operationOutstanding=true, complete=false |

Der PowerShell-Toolaufruf selbst endete mit 0, während der gehaltene native
S-Prozess mit 78 endete. Diese unterschiedlichen Exitcodes nicht als Erfolg
verwechseln. OwnerHandleDisposed=false im frühen HeldEnd-Teilbericht bezeichnet
die Beobachtungsphase vor Dispose; der spätere OwnerHandleClosed=true belegt
nur den anschließend geschlossenen äußeren Besitzhandle, keinen globalen Cleanup.

Nach Laufende genau eine ausschließlich lesende lokale CIM-Nachprüfung der
bekannten PIDs 33932/32356/38036 und Kinder von S/C: keine Treffer. Kein neuer
Prozess per PID geöffnet oder übernommen und keiner extern terminiert.
PID-Abwesenheit ist KEIN Ersatz für C-Ende, Exitcode oder Ressourcenabschluss.
Der vorhandene native eigene Rollbackpfad durfte laufen; sein vollständiger
Terminierungs-/Cleanup-Erfolg ist durch die verfügbaren Endflags nicht belegt.
Keine weiteren Cleanup-/Killversuche nachträglich erfunden oder ausgeführt.

### Evidenz, Integrität und nächste Grenzen

Alle Versuchdateien im bestehenden lokalen Ordner belassen. Neu hinzugekommen:
äußerer/nativer consumed, feste Kontrollnachrichten, outer-result.json,
native/result.txt, Intentdateien und die vom gepinnten JNA lokal extrahierten
DLL-/Markerrückstände. Zwei lokale JNA-DLLs jeweils 273408 Bytes, SHA-256
5a7ff949f6d93d86491eb5b26b1cfc60051168a60622650224b89995ac420023;
keine Downloads/Installationen. Nicht bereinigt oder erneut geladen.
postcheck.json und verification.json zusätzlich mit CREATE_NEW als ergänzende
lesende Auswertung gespeichert; primäre Berichte/Marker nicht verändert.

Vorher-/Nachher-Hashaggregate identisch: 251 Quell-/AGENTS-/Build-/Ignoredateien,
264 Laufzeitdateien, 119 historische Evidenz-/Markerdateien und Manual-JAR.
Manual-JAR unverändert
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Nur Dokumentnachträge ergänzt, kein Produktions-/Testcode geändert. Keine Tests
oder Builds wiederholt; 857 Java-/78 PowerShell-Fakefälle bleiben die historischen
Ergebnisse von 4D-9 und sind keine Belege für diesen nativen Teilerfolg.

Real belegt sind jetzt unter anderem einmaliger Verbrauch, S-/C-Prozessstart,
gehaltene S-Beobachtung samt Exitcode/äußerem EOF sowie der gebundene äußere
STOP-/Revokationspfad unter einer konservativ erkannten Abweichung. Ungeklärt
bleiben conhost-Ursprung/Besitz/Lebensdauer, exakte CIM-/Native-C-Korrelation,
vollständiger C-/K-/Handle-/Kanal-/Journalabschluss und normaler Transfer/READY.
Keine Crash-, Kernelhänger-, Same-Token- oder allgemeine Scrapergarantie.

Kein zweiter Versuch, keine Reparatur, H/J/Worker, Firefox/Selenium/Geckodriver,
Scraper, MangaLib-/HTTP-Start, Datenbank/SQL/Migration, Netzwerk, Installation
oder Gitoperation. K/H und Scraperintegration bleiben GESPERRT. Der Lauf ist
abgeschlossen; weitere Untersuchung/Implementierung erst mit neuem Auftrag.

## Nachtrag 29.10.2026 – T02: Architekturprüfung nach 4D-10 und Entscheidungsstopp

**ARCHITECTURE_DECISION_REQUIRED; keine neue OS-Ausführung freigegeben.**
Empfehlung: Variante B auf einen Job-Owner und einen isolierten Worker mit
konservativer Quarantäne vereinfachen, ohne zusätzlichen C-Handletransfer und
H/K/J-Kontrollhierarchie. Dies ist ein grundlegender Architekturwechsel gegenüber
dem aktuellen Watchdogentwurf. Gemäß ausdrücklicher Stoppregel des Auftrags
hier vor Umsetzung angehalten. Keine Produktions-, Experiment- oder Testquellen
geändert. Der bestehende S/C-Stand bleibt vollständig erhalten, nicht ersetzt.
Der dokumentierte Status ist ausdrücklich NICHT READY_FOR_NEW_OS_APPROVAL.

### 1. Quellen und erhaltene Tatsachen

AGENTS, relevante Planungs-/Sicherheits-/Prozessnachträge in ROADMAP und TESTING,
ScNativeDriver, ScStartProvider, ScSupervisor, ScNativeConnection, ScNativeWire,
ScRevocationFence, ScRunEvidence, Win64NativeBindings/Win64StdioChannels,
EmptyCustodianBootstrap sowie die PowerShell-Start-/Kontroll-/Beobachtungs- und
Fakepfade geprüft. Source-Set-/Manual-Paketierung und bestehendes SupervisorModel
berücksichtigt. Keine Gitabfrage; Branchbezeichnung bleibt die vom Eigentümer
vorgegebene codex/t02-security-hardening, nicht unabhängig per Git verifiziert.

Primäre 4D-10-Evidenz weiterhin unverändert: outer-result.json und native/result.txt
der Sitzung ba92a0e4-3857-4394-804f-b7ef1eef46e1. Keine nachträglichen Phasen,
Exitcodes oder K-Anzahlen konstruiert. Ein S und ein C, konservativer conhost-STOP,
wirksames Revokations-ACK, gehaltenes S-Ende/78 und äußeres EOF bleiben belegt;
C-Ende, vollständiger Ressourcenabschluss, K-Übernahme und READY bleiben offen.
Die Einmalfreigabe ist und bleibt verbraucht. Keine neue UUID/Freigabe angelegt.

### 2. Ursachenanalyse: Beleg, Reichweite und konkrete Korrekturrichtung

**A – Ungeeignete Auflösungsmischung bei der äußeren C-Korrelation.**
Start-ScEmptyOnce.ps1:96–98 gewinnt CreationDate über CIM und konvertiert nach
FILETIME. ScAttempt.ps1:58 fordert danach exakte Gleichheit mit der nativen
100-ns-Zeit. Der lokale CIM-MOF-Vertrag beschreibt CreationDate als datetime;
die historischen Werte zeigen 134376986442231460 versus 134376986442231462.
Eine reine .NET-Rechenprobe mit sechs Nachkommastellen reproduziert exakt den
Verlust von zwei Ticks. Eine zweite Probe zeigt eine Kollision verschiedener
nativer Zeiten nach dieser Darstellung. Damit ist eine Genauigkeitsmischung
als ausreichende Erklärung reproduziert; der konkrete interne CIM-Provider-
Konversionsschritt wurde ohne erlaubten OS-Aufruf nicht erneut untersucht.

Korrekturrichtung: für Management ausschließlich gehaltene native Instanzbelege
als Primärautorität; CIM nur als Diagnose/Kandidat. Keine allgemeine +/-Toleranz,
keine Zeitrundung als Besitzbeweis und keine PID-only-Öffnung zum nachträglichen
Ersetzen eines verlorenen Handles. Für jede neue Beobachtung Provenienz und
Zeitauflösung kenntlich machen. Zwei auf dieselbe Mikrosekunde fallende Zeiten
dürfen keine Managementberechtigung erzeugen.

**B – Outstanding vermischt native Ungewissheit und zurückgekehrte Javafehler.**
ScRevocationFence.java:27–30 zählt die ganze Supplier-Ausführung; returned() wird
nur nach normaler Rückgabe aufgerufen. ScStartProvider.java:94 umfasst dabei
auch den zusammengesetzten Handshake. Eine zurückgekehrte RuntimeException aus
Java-/Protokoll-/Adaptercode bleibt daher genauso outstanding wie ein tatsächlich
noch nicht zurückgekehrter nativer Aufruf. Das ist konservativ, liefert aber
keinen präzisen Native-Endnachweis und kann Cleanup dauerhaft sperren:
ScFencedCalls.preparationSettled verlangt outstanding==0.

Zusätzlich setzt ScRunEvidence.java:21 operationOutstanding bereits bei fehlendem
Bootstrapresultat auf true. Dieses Flag bezeichnet also nicht ausschließlich
ausstehende Native-Aufrufe. Das ACK outstanding=1 ist ein echter damaliger
Leasestand; die genaue Operation oder ein Kernelhänger sind dadurch nicht belegt.

Korrekturrichtung: Revokationsentscheidung, laufende Javaarbeit und native
Acquisition-/In-flight-Bilanz getrennt erfassen; bestehende Ressourcenledger
verwenden. Eine durch Java-Exception beendete Phase darf nicht als laufender
Javacall gelten, aber unbekannte native Teilressourcen bleiben ausdrücklich
UNKNOWN. Kein pauschales finally-Decrement, das unbekannte Native-Outcomes
versteckt, und keine Freigabe allein wegen outstanding==0.

**C – C-Abbruch wird nicht bis zum gehaltenen Ende nachbeobachtet.**
ScStartProvider.java:131–162: rollbackSuspended führt nach eigener Identitätsprüfung
höchstens einen TerminateProcess-Versuch und unmittelbar eine Endabfrage aus.
releaseEnded prüft wiederum einmal und gibt bei noch lebendem C sofort false
zurück. ScNativeDriver.java:92 besitzt danach keine begrenzte C-End-Warteschleife.
Terminate-Annahme und Prozessende sind getrennte Ereignisse; dieser Ablauf kann
bei noch nicht beendeter eigener Instanz die vorhandene Restfrist ungenutzt lassen.
Das erklärt eine konkrete Nachweislücke im Code. Ob dies im 4D-10-Lauf die einzige
Ursache von cEnd=false war, ist ohne Phasenspur nicht beweisbar; die Outstanding-
Barriere kann zusätzlich verhindern, dass Ressourcen freigegeben werden.

Korrekturrichtung bei Beibehaltung S/C: dieselbe gehaltene C-Capability innerhalb
der einmaligen Restfrist nachbeobachten, Exitcode erst nach Ende erfassen,
keinen Terminate-Retry, keine neue Deadline und kein Close bei laufenden Nutzungen.
Bei unklarer nativer Rückkehr weiterhin Quarantäne statt spekulativer Bereinigung.

**D – Race im normalen Abschluss nach C-Abschlussnachrichten.**
ScNativeDriver.java:196–197 lässt C nach GROUP_END/CLOSED/CHANNEL_END zurückkehren
und regulär aussteigen. EmptyCustodianBootstrap.java:180–190 verlangt nach
finishEmpty trotzdem nochmals peerAlive(), bevor releaseSupervisor beginnt.
ScNativeConnection.releaseSupervisor wartet dagegen gerade auf C-Ende.
Ein korrekt bereits beendeter C kann daher an der vorgeschalteten Alive-Bedingung
scheitern. Das ist ein statisch belegter Lifecycle-Race, kein belegter Auslöser
des abgebrochenen 4D-10-Laufs. Die Fakefälle ersetzen keine reale Ablaufprüfung.

Korrekturrichtung: lebender Peer als Voraussetzung für neue Arbeit; nach gebundenem
Protokollabschluss die gehaltene Endbeobachtung verlangen. Niemals ENDED schon
vor Übergabe/ACK als gültigen Normalpfad akzeptieren und keine Identitätsprüfung
entfernen. Die Phase bestimmt, welcher Lebenszustand erwartet wird.

**E – conhost-Ursprung weiterhin nicht aufgeklärt.**
Der zusätzliche Prozess ist in zwei historischen Versuchen beobachtet; der
aktuelle Start setzt bereits CreateNoWindow=true, C verwendet CREATE_NO_WINDOW.
Aus Flags, Image, Parent oder Birth allein folgt weder Zugehörigkeit noch die
konkrete Erzeugungsursache. Statische Suche nach AllocConsole/AttachConsole/
FreeConsole im Projekt und als ASCII-Namen in den geprüften festen Java-/JLI-/
Java-DLL-/historischen JNA-Dateien ohne Treffer. Das beweist nicht, dass kein
indirekter Konsolenaufruf stattfindet, und ersetzt keine Import-/Calltraceanalyse.
Keine pauschale conhost-Ausnahme, kein Wechsel auf javaw.exe oder geänderte
Flags ohne späteren getrennten Nachweis. Ein vereinfachtes Design löst diese
Windows-Frage nicht automatisch. Nächste Diagnose muss sie ausdrücklich isolieren.

### 3. Architekturvergleich und Empfehlung

| Kriterium | Aktueller S/C-/später H/K/J-Entwurf | Ein Job-Owner und isolierter Worker |
| --- | --- | --- |
| Normale Scraperfehler/Timeouts | Kontrollierte Gruppenbeendigung möglich, aber zahlreiche zusätzliche Owner-/Transfer-/Kanal-Endbarrieren | Kontrollierte Beendigung des eigenen Workerjobs; Wiederaufnahme erst nach vollständigem Ende |
| Ressourcenbesitz | S-/C-Prozesskopien, reduzierte Jobkopie, Rückkopie und mehrere Kanäle/Journale | Ein Besitzer des privaten Jobhandles, Worker ohne Job-/Owner-Kontrollhandle |
| Worker vor erster Ausführung enthalten | Zusätzliche vorgelagerte C-/H-/K-Bootstrapnachweise nötig | Atomare JOB_LIST-Zuordnung bei CreateProcess, zunächst suspendiert; kein unsicheres Create-then-Assign-Fallback |
| Owner lebt, ist aber blockiert | C soll unabhängig weiter terminieren/beobachten; konkrete OS-Garantie noch nicht abgenommen | Kontrollspur außerhalb des normalen Readers; bei vollständig blockiertem Owner keine unabhängige Cleanupzusage |
| Owner stürzt ab | C-/Last-handle-/Ownerende- und weitere Crashpfade separat abnehmen | Kill-on-last-close als zusätzliche OS-Schadensbegrenzung; keine automatische Importfreigabe aus Close/Crash |
| Kernelcall kehrt unklar/spät zurück | Weiterhin Quarantäne; C hebt Kernelungewissheit nicht automatisch auf | Ebenfalls Quarantäne, keine neue Instanz, späte Rückgabe bleibt demselben Auftrag zugeordnet |
| Wartung und Integration | Hoher Aufwand für Transfer, Rollenbootstrap, doppelte Beobachtung und Tests ohne bisherigen Normal-PASS | Weniger Ownershipübergänge und Endrennen; vorhandene Job-/IPC-/Admissionregeln wiederverwendbar |
| Reale Reife | Teilnachweise S/C/STOP; kein normaler S/C-Leere-K-Abschluss | Noch nicht implementierte Alternative, keine daraus abgeleiteten OS-Garantien |

**Empfohlen: die einfachere Worker-/Job-Variante B.** Das ist keine Rückkehr zur
früheren In-Prozess-Variante A, die Selenium direkt in MangaLib nur durch quit/
Future und dauerhafte Sperre begrenzt. Selenium bleibt in einer getrennten JVM,
vor Ausführung in einem privaten Job; Fehlercleanup erfolgt OS-seitig über die
eigene Gruppe. Bei gewöhnlichem Workerfehler ist nach nachgewiesenem Gruppenende
ein neuer Import möglich. Bei echter OS-/Ownership-Ungewissheit muss jede hier
vertretbare Architektur gesperrt bleiben. Kein automatischer Quarantänereset.

Der Zusatz-C verspricht einen Vorteil bei lebendem, blockiertem Owner, benötigt
dafür aber selbst beweisbaren Bootstrap, Transfer, unabhängige Überwachung und
Endbeobachtung. Der aktuelle Stand belegt diese Gesamtgarantie nicht. Für die
lokale Anwendung erscheint dieser Zusatzaufwand gegenüber dem Integrationsnutzen
unverhältnismäßig. Das ist eine begründete technische Empfehlung, keine mit einem
neuen Windows-Versuch nachgewiesene Überlegenheit.

**Entscheidung erforderlich:** Soll die unabhängige Cleanupinstanz bei einem
lebenden, vollständig blockierten Owner weiterhin verbindliches Architekturziel
sein? Dann S/C behalten und A–D gezielt korrigieren; der zusätzliche unabhängige
OS-Nachweis bleibt Pflicht. Wird stattdessen konservative Quarantäne bei einem
solchen Ownerausfall akzeptiert, vereinfacht die empfohlene Variante B wesentlich.
Die aufgelisteten Sicherheitsinvarianten werden nicht aufgegeben; die Verfügbarkeits-
und Crashzusage muss jedoch ausdrücklich abgegrenzt werden. Deshalb keine
eigenmächtige Änderung des bisherigen Custodian-Entscheids.

### 4. Konkreter Migrationsplan nach Architekturfreigabe

1. Sicherheitsvertrag knapp festlegen: ein Reihen-/Bandslot, eigener unbenannter
   Job, keine Breakawayrechte, keine Job-/Prozesskontrollhandles im Worker, feste
   Executables/Umgebung, unveränderte JSON-/Import-/Fehlerverträge. Vorhandene
   SupervisorModel-/Budget-/IPC-Assertions als Mindestverträge erhalten. Keine
   bereits erreichten OS-Nachweise in neue Architektur übertragen.
2. Einen isolierten Laufzeitpfad auf Basis bestehender KernelCalls-/Win64-/
   IPC-Ports aufbauen. Keine neuen Dependencies, kein Brokerdienst, keine DB.
   Job erstellen/konfigurieren/rücklesen, Worker per JOB_LIST atomar enthalten
   und suspendiert halten; Identität und Mitgliedschaft vor Resume bestätigen.
   Inkompatibler Ancestorjob/Teilstart: kontrollierter Abbruch, kein Fallback.
3. Ownership und Fristen konsolidieren: gemeinsame monotone Auftragsfrist,
   separates einmaliges Cleanupbudget, Revokation vor jedem Normalstart/Resume,
   keine Locks über Native-/Pipearbeit. Zurückgekehrte Javaarbeit und ungeklärte
   native Erwerbe unterscheidbar machen. Unbekannte Teilhandles nicht verlieren.
   Ein spät zurückkehrender Create darf nie resumed oder als neuer Auftrag gelten.
4. Einen Cleanupweg für Erfolg, Fehler, Timeout und Shutdown implementieren:
   STOP setzt zuerst Admission zu; gegebenenfalls eigene Gruppe terminieren;
   Gruppenliste/Accounting und gehaltene Instanzen prüfen; Exitcode, Kanalende,
   Reader-/Writerende, Native-Nutzungen, Handles und eigene Tempdateien getrennt
   abschließen. Workerexit allein genügt nicht. Jobhandle für Endprüfung behalten,
   nicht vorzeitig schließen und anschließend Ende aus Kill-on-close unterstellen.
5. Quarantäne sichtbar, konservativ und über einen Neustart hinweg eindeutig
   behandeln. App-Neustart darf einen ungeklärten Altauftrag nicht automatisch
   freigeben. Persistierter STOP-/Auftragszustand ohne Secrets; keine PID-only-
   Wiederübernahme und kein automatisches Löschen. Konkrete Operatorfreigabe/
   Recoveryregeln vor deren Umsetzung festlegen; kein Resetendpunkt nebenbei.
6. S/C/H-Altpfade und ihre Tests zunächst vollständig erhalten. Neue reine Tests
   gegen den tatsächlich vorgesehenen Portpfad, dann separat autorisierte OS-
   Abnahme. Erst nach belegter Gleichwertigkeit über nachweislich ungenutzte
   Experimentpfade entscheiden; historische Evidenzen/Marker niemals bereinigen.
7. Produktintegration bleibt ein eigener späterer Auftrag. ScraperGateway-
   Methoden/Map-Payloads, Importfelder und M06-/Legacy-/MVC-Verträge beibehalten.
   Main-/Manual-Classpaths bleiben getrennt; Manual enthält weiterhin nur
   SyntheticScraperGateway und keine Worker-/JNA-/Live-Startreferenzen.

Aufwand als grobe Planungsgröße, keine Zusage: vereinfachter isolierter Pfad etwa
5–8 Entwicklungstage für Konsolidierung, Tests und Paketierungsprüfung plus
gesonderte Windows-Abnahmen; aktuelle S/C-Hierarchie etwa 10–15 Tage oder mehr
bis einschließlich noch fehlender H-/Worker-/Crash-/Transferabnahmen. Beide
Schätzungen haben hohe Unsicherheit durch conhost, Nestedjobs, echte Firefox-
Nachfahren und ungeprüfte Kernel-/Cleanupausgänge; reine Testanzahl ist kein
Fortschritts- oder Fertigstellungsmaß.

### 5. Tests und nächste separat zu genehmigende OS-Abnahme

Nach Architekturentscheid zunächst deterministisch und DB-/JNA-frei:
- Timeout/Shutdown vor Commit, zwischen Create/Resume und bei verspäteter Rückgabe;
  kein spätes READY, kein zweiter Worker, keine Freigabe mit offenen Erwerbsvorgängen.
- Eigene Instanzbelege, PID-Reuse, gleiche PID/gerundete Zeit, fremde Capabilities;
  fehlende Identität nie durch Namen/Parent/zeitliche Toleranz ersetzen.
- Native Operation noch laufend versus Java-Exception nach bestätigter Rückgabe;
  unbekannte Teilressource trotz beendeter Javaarbeit verhindert Freigabe.
- Terminate-Annahme mit erst späterem gehaltenem Ende, fehlender Exitcode,
  bereits regulär beendeter Peer nach gültigem CHANNEL_END, EOF ohne Protokollende.
- Gruppenende ohne Readerende, Erfolgspayload vor Cleanup, zusätzliche Handlekopie,
  fehlgeschlagener Close und gemeinsames statt erneuertem Cleanupbudget.
- Parallelität Reihen/Band, doppelte Antworten, Read-/Writeblockade und resultierende
  Quarantäne; keine Wiederholung, kein Reset, Startup mit ungeklärtem Vorauftrag.
- Statische/Artefaktprüfung: keine Laufzeit-Testklassen, keine Main-/Manual-
  Startintegration. Existing Assertions unverändert mitprüfen.

Nächster echter Windows-Auftrag erst nach neuer Freigabe und entsprechendem
Fake-Nachweis: zunächst Konsolen-/Startprovenienzdiagnose mit festem Image,
exakter Startobergrenze und eigenem gehaltenem Endbeleg; kein allgemeiner
conhost-Sonderfall. Anschließend höchstens ein eigener synthetischer Worker,
zunächst suspendiert in genau einem privaten Job, ohne Browser/Nachfahren oder
Netzwerk. Gegenstand: tatsächliche JOB_LIST-Zuordnung vor Ausführung, Rechte,
Nichtvererbung, readback, eigene Instanz/Gruppe und vollständiger Abschluss.
Der Versuchsaufbau muss vorab auch für Teilstart/Beobachterausfall sicher begrenzt
sein; andernfalls nicht ausführen. Keine technische Beherrschung allein aus dem
neuen Diagramm oder einer Sandboxannahme ableiten.

Normaler Abschluss, Timeout und Crash werden danach getrennt freigegeben;
Firefox erst nach diesen synthetischen Nachweisen mit eigenen lokalen Inhalten.
Offene conhost-Fragen, Populations-/Nestedjob- und Windows-Crashsemantik bleiben
ausdrückliche Gates. Kein OS-Versuch, Worker oder Browser durch diesen Plan erlaubt.

### 6. In diesem Auftrag tatsächlich ausgeführte Prüfungen und Integrität

Nach statischer Prüfung nur ScOuterControl.Check.ps1 (führt die bestehenden
48 ScAttempt-Fakes und 24 Kontrollfakes aus) sowie ScOwnedObservation.Check.ps1
(6 Fakes) ausgeführt: **78 bestanden, 0 Fehler**. Nur temporäre synthetische
Objekte/Dateien, keine Prozesse/Win32-/DB-Aufrufe. Der Testaufruf des echten
Launchers ohne ExecuteOnce stoppt vor jeder Startarbeit; keine echte Freigabe
erzeugt oder verbraucht. Zwei zusätzliche reine .NET-Präzisionsproben erfolgreich;
keine neuen installierten Testtools oder dauerhaften Testquellen.

Keine Gradle-/Java-Tests oder Builds ausgeführt: der grundlegende
Architekturentscheid löst die vorgeschriebene Stoppregel vor Implementierung aus.
Die 857 Javafälle bleiben der historische erfolgreiche Stand von 4D-9; kein
neuer Java-/Windows-Erfolg behauptet. Keine Ausweitung der früheren JNA-Ausnahme.
Kein trockener Taskplan als ausgeführter Test und keine Fakezahl als OS-Beweis.

Baseline vor Änderungen: 251 Quell-/AGENTS-/Build-/Ignoredateien, 264 kompilierte
Laufzeitdateien, 140 historische Evidenz-/Markerdateien (119 ältere plus 21 aus
4D-10) und Manual-JAR. Quellen/Artefakte/Marker unverändert belassen. TESTING und
ROADMAP nur durch neue Nachträge ergänzt. Manual-JAR-SHA-256 weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Keine DB-/SQL-/Migrations-, echten Windows-Prozess-/Job-/Handle-, Netzwerk-,
Browser-/Scraper-, Installations- oder Gitoperationen; keine Produktintegration.
Vorliegende Empfehlung wartet auf Architekturentscheidung, nicht auf eine
pauschale neue OS-Freigabe. Danach nur den gewählten abgegrenzten Pfad umsetzen.

## Nachtrag 29.10.2026 – Vereinfachte Variante B implementiert und fakegeprüft

**Implementiert und fakegeprüft; NICHT für echte OS-Ausführung oder
MangaLib-Produktintegration freigegeben.** Der Eigentümer hat den Wechsel auf
einen Job-Owner, einen isolierten Worker und eine private Jobgruppe pro Auftrag
ausdrücklich genehmigt. Die zusätzliche unabhängige Cleanupinstanz bei einem
lebenden, vollständig blockierten Owner ist kein verbindliches Ziel mehr.
Unbestätigtes Ende bleibt eine Importsperre, keine automatische Wiederaufnahme.
Diese Entscheidung ersetzt die vorherige Entscheidungssperre, nicht die
historischen Sicherheitsnachweise. Kein echter neuer Versuch in diesem Auftrag.

### 1. Tatsächlicher Implementierungsumfang und Trennung

Neue isolierte Source-Sets, ohne Verknüpfung mit main, manualTest, build oder check:

| Komponente | Zuständigkeit |
| --- | --- |
| workerOwner/WorkerJobOwner.java | Ein gemeinsamer Reihen-/Bandslot, atomare lokale Normalphasen-Commits, monotone Budgets, eine eigene Kontrollspur, Ressourcen-/Instanzledger, vollständige Cleanupbarriere und Quarantäne |
| workerOwner/WorkerJournal.java | Flush-bestätigtes CREATE_NEW-pending vor möglichem Erwerb; persistente Quarantäne; keine automatische Recovery bei Neustart |
| workerOwner/WorkerWire.java | Gebundener begrenzter Request, HELLO, ein String/null-Map-Resultat, explizites CHANNEL_END und physisches EOF; keine Objekt-/Typdeserialisierung |
| workerOwner/SyntheticWorkerMain.java | Fester synthetischer Java-Worker-Einstieg für spätere Abnahme; dieselbe serve-Funktion jetzt nur in-memory getestet; kein Scraper, Spring, DB oder Netzwerk |
| workerOwnerNative/SimpleWorkerNative.java | Kalte Verbindung zu den vorhandenen vorbereiteten Win64-Bindings, fester gepinnter Java-/Klassenpfad, minimale Umgebung, Native-In-flight-/Ungewissheitsbilanz |
| workerOwnerNative/WorkerNativePipes.java | Nur drei stdio-Vererbungsziele, getrennte begrenzte stdout/stderr-Verarbeitung und eigener Pipe-/Memoryledger |
| workerOwnerCheck/WorkerOwnerTest.java | Derselbe Owner mit injizierten Kernel-/Session-Fakes, Speicher-IPC und deterministischen Latches/Clock |
| workerOwnerCheck/WorkerWireTest.java | Tatsächlicher Codec/Worker-serve-Pfad mit Speicherstreams und korrupten Frames; zusätzliche statische Native-Verdrahtungsassertions |

build.gradle.kts ergänzt nur diese Source-Sets und workerOwnerUnitTest. Kein
JavaExec-/Probe-/Starttask und kein neuer Owner-Hintergrunddienst. Vorhandene
JUnit-6.0.3- und lokal gepinnte JNA-5.17-Konfigurationen wiederverwendet, keine
neue Bibliothek/Version und kein Download. Der neue reine Testklassenpfad enthält
kein JNA und kann SimpleWorkerNative nicht laden. Die nativen Quellen wurden
kompiliert, aber keine ihrer JNA-/Win32-Operationen heute ausgeführt.

Wiederverwendet: KernelCalls als Capability-Port, Win64ProcessKernelCalls mit
JOB_LIST/CreateProcessW und gehaltenen Identitäts-/Mitgliedschaftsabfragen,
Win64LaunchSpec, feste Win64-Bindings und die bestehende Hashpin-Konfiguration.
Kein PID-Kill, AssignProcessToJobObject-Fallback oder DuplicateHandle-Transfer.
OwnedJobAdapter/SupervisorModel/BinaryIpcCodec dienen weiterhin als historische
Vertragsregressionen. Ihr alter Rollen-/Fakeaufbau wurde nicht in ein weiteres
allgemeines Framework umgebaut. Das neue kleine Wireprofil braucht keine
S/C/H-Richtungen oder fremden Handlewerte und bewahrt flache String/null-Maps.

Sämtliche alten S/C/H-/Custodianquellen, Testassertions, Versuchsskripte und
verbrauchten Marker bleiben erhalten. Sie sind keine Abnahme des neuen Pfads.
Keine Änderungen an ScraperGateway, Controller, Services, Frontend, DB-Guards
oder Sicherheitsprofilen. Die bestehenden Live-Scraper sind noch NICHT an den
neuen Worker angebunden. Es gibt bewusst nur den synthetischen Native-Factorypfad,
keinen ungeschützten In-Prozess-Selenium-Fallback und keine neue HTTP-Schnittstelle.

### 2. Ablauf, Eigentum und Sicherheitsinvarianten

1. Requestindex syntaktisch positiv und int-beschränkt prüfen, bevor Factory,
   Journal oder native Ressourcen erreicht werden. Keine freie Ziel-URL,
   Executable, Deadline oder Workerklasse im Request.
2. Reihen und Bände belegen denselben Owner-Slot. Konkurrierender Auftrag erhält
   BUSY, Shutdown/Quarantäne UNAVAILABLE; kein Queueing oder Ersatzworker.
   Statuswerte sind hier interne Ergebnisse, noch keine neuen HTTP-Verträge.
3. Frische Auftragsidentität und private Scope-Capability; pending mit CREATE_NEW,
   vollständigem Write und force(true) vor möglicher Ressourcenanlage. Ein
   vorhandenes pending oder quarantine sperrt auch eine neue Ownerinstanz.
4. Eine Session pro Auftrag. Private Jobgruppe erzeugen, Kill-on-close und kein
   Breakaway konfigurieren/rücklesen. Der neue synthetische Profilpfad verwendet
   die bisherigen quota-freien Adapterlimits; keine RAM-/CPU-/Prozessquotenzusage.
5. Attribute/Backing Memory und alle Teilhandles sofort dem Ledger zuordnen.
   Vorhandenes JOB_LIST plus HANDLE_LIST mit exakt stdin/stdout/stderr; Start
   suspendiert. Kein erst laufender Worker mit nachträglicher Zuordnung.
   Native Identität, erwartetes Root-Image und private Jobmitgliedschaft vor Resume.
6. HELLO/Resultat an UUID und Operation binden. Eigenes stderr wird gleichzeitig
   begrenzt gelesen, nie als HTTP-Exceptiontext oder Importfeld übernommen.
   Erfolgspayload bleibt vorläufig, bis Cleanup vollständig bestätigt ist.
7. STOP schließt weitere Normalphasen; Kontrollspur arbeitet ohne Normal-I/O-Lock
   mit vorbereiteter Capability auf demselben eigenen Job. Keine weitere Rolle,
   kein Remotehandletransfer, keine Fremdprozess-Terminierung.
8. Normaler Abschluss wartet auf gehaltenes Workerende, statt danach nochmals
   einen lebenden Worker zu verlangen. Verbliebene bestätigte eigene Nachfahren
   werden über den eigenen Job beendet. Fehler/Timeout/Shutdown verwenden
   denselben begrenzten Cleanupweg und höchstens einen Terminierungsversuch.
9. Gruppenlisten/Accounting kohärent prüfen, gehaltene Instanzen erneut vergleichen
   und Ende/Exitcode erfassen. Ein angenommener Terminierungsaufruf wird innerhalb
   derselben Restfrist nachbeobachtet; solange er läuft, keine Handles schließen.
   Kein Erfolg aus EOF, Terminate-Annahme oder späterer PID-Abwesenheit.
10. Nach Gruppenende Reader/Writer/Pipes, Prozess-/Threadhandles, Attribute vor
    Memory, lokale Kontrollreservierung und zuletzt Jobhandle abschließen.
    Der tatsächliche Adapter muss zusätzlich seine vollständige Bilanz bestätigen;
    ein nur im Ownerledger fehlender Handle wird nicht als erledigt übergangen.
    Das neue Tempverzeichnis wird nur leer und nach kanonischer Prüfung entfernt,
    niemals rekursiv oder anhand eines Workerpfads bereinigt.
11. Erst danach pending derselben UUID entfernen und genau einmal neue Admission
    erlauben. Fehler oder Fristüberschreitung bleiben QUARANTINED und halten den
    Ressourcenbesitzer erreichbar. Keine Reset-API. Eine gültige spätere Meldung
    entfernt keinen Quarantänemarker; Neustart ist kein Recoverybeweis.

Die Kurzlock-Commits autorisieren verbindlich begonnene Phasen, nicht den exakt
zeitgleichen Windows-Kerneintritt. Ein bereits committeter Nativecall kann nach
STOP physisch eintreten oder spät zurückkehren. Seine Teilressourcen bleiben
owned/unknown, nie automatisch resumed oder als neuer Auftrag ausgegeben.
Die zusätzliche native StartAuthorization prüft dieselbe Ownerberechtigung vor
Create und Resume. Unbekannte native Erwerbe/Exceptions bleiben separat erfasst.
Eine normale zurückgekehrte Java-/Protokollexception lässt dagegen keine fiktiv
ewig laufende Java-Lease zurück. Kein pauschales UNKNOWN-Löschen im finally.

Die Kontrollspur ist ein lokaler Daemonthread je aktivem Auftrag, kein zusätzlicher
Prozess und kein dauerhaft laufender Dienst. Verbindungs-/Kernelhänger sind nicht
hart präemptierbar. Bleiben Owner/Kontrollspur vollständig blockiert, gibt es keine
unabhängige Custodianzusage. Eine neue Anfrage darf auch nach überschrittenem
Cleanupbudget keine Admission erhalten; pending schützt den Neustartfall.
Bei unbestätigtem Ressourcenende bleiben gegebenenfalls eigene Handles/Threads
bewusst erhalten, statt blind geschlossen oder für einen Retry verworfen zu werden.
Keine Same-Token-, Powerloss- oder universelle Crashbereinigungsgarantie.

### 3. Budgets, Payloads und Grenzen des aktuellen Profils

Nur bisherige Ausgangskandidaten übernommen, nicht als produktive Abnahme verkauft:
Start 30 s, Band 120 s, Reihe 60 min und Cleanup einmalig 15 s. Nanosekunden aus
einer monotone Clock, keine neue Frist pro Feld/Band/Ressource. Der Startanteil
endet erst bei gültigem HELLO, nicht schon bei Create. Cleanup darf nach einem
rechtzeitigen Resultat das eigene einmalige Budget nutzen. Joins verwenden
positive Rest-Durations, kein unbegrenztes join(0) und kein Millisekunden-Aufrunden
als zusätzliche Graceperiod. Vor Freigabe auch Journal-/Restfrist erneut prüfen.

Request maximal 4 KiB; ein Resultat maximal 32 MiB/30.032 Einträge. Flache
String/null-Map ohne verschachtelte Typen, Schlüssel höchstens 255 UTF-16-Einheiten,
Werte höchstens 255 Unicode-Codepoints, Token höchstens 4 KiB. Striktes UTF-8,
gebundene Version/UUID/Operation, keine doppelten Schlüssel, Zusatzframes oder
Erfolgs-Teilmaps. Längen vor der Payloadallokation prüfen. Stderr maximal 1 MiB,
nur 64 KiB im Ring behalten; keine Veröffentlichung des Roh-Tails. Der bisherige
offene Worst-Case-Kompatibilitätsnachweis für 32 MiB ist damit NICHT geschlossen.
Keine fachliche maximale Reihengröße reduziert und keine Produktionspayloads getestet.

Das Nativeprofil startet ausschließlich das feste Java-21-Image mit bisherigem
SHA-256-Pin und -Xmx256m, festem workerOwner-Klassenpfad und SyntheticWorkerMain.
Nur SystemRoot/TEMP/TMP im Kind-Environment; keine Spring-/DB-/JVM-Overridewerte.
JNA wird nur im Owner verwendet, nicht in den neuen reinen Worker-Classroot
eingemischt. Der Native-Factoryaufruf verlangt bereits vorbereitete feste
Bindings; es gibt keinen stillen DLL-Lader/OS-Probestart als Beaninitialisierung.

### 4. Bezug zu den realen 4D-10-Befunden

- Kein CIM-/Native-Zeitvergleich als Managementautorität: direkte gehaltene
  100-ns-Identitäten. Ein Fake mit um zwei Ticks veränderter Identität bleibt
  gesperrt; keine bequemere Toleranz eingeführt.
- Eine zurückgekehrte Protokollexception kann nach nachgewiesenem eigenen
  Gruppencleanup wieder einen freien Slot ergeben; native Ungewissheit nicht.
- Verspätete Create-/Resume-/Kontrollrückgaben werden mit offenen Ressourcen
  verbunden; kein spätes SUCCESS/Resume, keine erneuerte Cleanupfrist.
- Normaler vollständiger Abschluss akzeptiert das bereits gehalten belegte Ende
  des Workers. Ein nur geschlossenes Protokoll bei weiter lebendem Worker genügt
  nicht. Timeout, Shutdown und fehlender Exitcode bleiben getrennt von Erfolg.
- conhost ist weiterhin NICHT aufgeklärt. Der neue Fake eines nachweislich eigenen
  Jobmitglieds mit diesem Namen erhält seine Berechtigung ausschließlich aus
  Mitgliedschaft und gehaltener Instanz. Das ist KEINE pauschale conhost-Ausnahme
  und ändert keinen historischen Befund zu externen/unzugeordneten Prozessen.
  Ein unzugeordnetes zusätzliches Image im nächsten OS-Harness muss weiterhin STOP
  auslösen; keine Namens-/Parent-PID-only-Erlaubnis oder fremde Terminierung.

### 5. Tatsächlich ausgeführte Validierung

Java 21; vor JEDEM ausgeführten Gradlelauf identischer --dry-run, ausschließlich
--offline --no-daemon. Zuerst neue Kompilierung/Tests, danach breiter historischer
Prüfsatz plus neue Fälle; nach letzter Admission-/Deadlinekorrektur abschließend
nur betroffene neue Suite wiederholt. Wiederholungsläufe nicht addiert.

| Prüfsatz | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| test: dieselben sieben reinen Unit-/Modellklassen | 338 | 0 | 0 |
| communicationHostUnitTest | 44 | 0 | 0 |
| watchdogLifecycleTest | 60 | 0 | 0 |
| controlHostStartUnitTest | 41 | 0 | 0 |
| hostPreparationUnitTest | 56 | 0 | 0 |
| custodianUnitTest | 145 | 0 | 0 |
| custodianBootstrapUnitTest | 173 | 0 | 0 |
| workerOwnerUnitTest: 40 Owner-/Journal-/Racefälle, 16 Codec-/Speicherfälle | 56 | 0 | 0 |
| **Java gesamt: 857 historisch + 56 neu** | **913** | **0** | **0** |

Die enge Ausnahme der 56 alten JNA-Speicher-/Layoutfälle vor Ausführung erneut
statisch als Raw-/Proxy-/Loader-Fakes geprüft. Keine ausführenden Kernel32- oder
KernelBase-Funktionen und keine 159 alten JNA-Bindingtests ausgeführt. Neue
Worker-/Native-Pipetests erhalten KEINE zusätzliche JNA-Speicherausnahme:
Win32-/JNA-Nativebridge nur kompiliert und statisch geprüft, nicht ausgeführt.

Wesentliche neue Fälle: normale Reihen-/Bandroundtrips mit Unicode/null/Preisen,
Workerfehler, ungültige Indizes ohne Factory/Journal, parallele Admission,
Startabbruch vor/nach Erwerb, PENDING und unklare Nativebilanz, Shutdown/Timeout,
STOP während Create/Resume/Kommunikation, verspätete Terminierung innerhalb bzw.
außerhalb derselben Restfrist, native Zeitänderung, fremde Mitgliedschaft,
instabile Listen, verborgen gemeldeter Leak, fehlender Pipeclose, EOF ohne
Workerende, persistentes pending/quarantine und gesperrte späte Journalmeldung.
Codec: tatsächlicher synthetischer serve-Pfad in Speicherstreams, UTF-8-/Längen-/
UUID-/Operationsfehler, Duplikate, Trunkierung, Zusatzdaten und Eintragsgrenzen.
Ergänzende Quelltextassertions sind ausdrücklich kein vollständiger Native-Test.

Ein Zwischenlauf enthielt einen Fehler im neuen Nachfahren-Testdouble: es hatte
einen noch nicht geöffneten Kindprozess zusätzlich als bereits erworbenen Owner-
Handle modelliert. Das Double wurde korrigiert; keine Sicherheitsassertion
abgeschwächt. Alle oben genannten Abschlussberichte sind erfolgreich. Weitere
Reviewkorrekturen betrafen getrennte Native-/Java-Bilanz, kontrollierte Cancellations,
späte Kontrollrückkehr und Admission bei abgelaufener Cleanupfrist.

Zusätzlich ScOuterControl.Check.ps1 und ScOwnedObservation.Check.ps1 tatsächlich
ausgeführt: **78 bestehende PowerShell-Fakes bestanden (48+24+6)**. Nur temporäre
synthetische Dateien/Objekte. Keine echte Freigabe erzeugt oder verbraucht.

Reproduzierbare isolierte neue Prüfung (vor Ausführung jeweils identischer Plan):

```powershell
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun
```

Breiter Prüfsatz: dieselben sieben test-Filter wie 4D-9/4D-10-Vorprüfung und die
sieben expliziten isolierten Testtasks einschließlich workerOwnerUnitTest;
jeweils --rerun am Testtask. Keine Vollsuite, clean, Boot-/DB-Integration oder
normaler Build. Native Kompilierung erfolgreich, keine neuen Compilerwarnungen
in diesen Ausgaben beobachtet; nicht neu kompilierte Anwendungsteile dadurch
nicht als warnungsfrei oder fachlich regressionsgeprüft behauptet.

### 6. Source-Set-, Archiv- und Dateiintegrität

test/check/build/assembleManualTest ausschließlich per Offline-Dry-Run geprüft:
keine neue workerOwner-/Native-/Probe-Task im Produkt-/Manualgraphen. Neue
Laufzeit-Classroots: workerOwner 26 und workerOwnerNative 6 Klassen, jeweils
keine Testklassen. Kein Build des Manual-JAR und keine Produktintegration.

Manual-JAR hashgleich
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
ZIP-Inventar: keine neuen Owner-/Native-/SyntheticWorker-Einstiege, keine alten
S/C-Nativestarts und keine LiveScraperGateway-/MangaScraper-/BandScraperklassen.
**Präzisierter Altbefund:** BOOT-INF/lib/jna-5.13.0.jar ist im unveränderten
historischen Manual-JAR bereits vorhanden. Keine JNA-Freiheit dieses Archivs
behaupten; dieses JAR wurde weder hinzugefügt noch geladen. Der neue Nativepfad
nutzt separat den bestehenden 5.17-Pin und ist nicht mit dem Manualprofil verbunden.
Abhängigkeitshygiene des alten Pakets bleibt ein Folgepunkt, keine beiläufige
Artefaktbereinigung oder Abschwächung seiner Guardregeln.

Vorher-/Nachher-Prüfung über die definierten 250 bisherigen Quell-/AGENTS-/
Ignoredateien, 140 historischen Evidenz-/Markerdateien und Manual-JAR. Alte
Quellen und Tests nicht geändert; nur acht neue Javaquellen in den neuen
Source-Sets, append-only Gradlekonfiguration und neue Dokumentnachträge.
Keine Gitoperation; Hash-/Präfix-/Whitespace-/Konfliktmarkerprüfung stattdessen.

### 7. Nächster eng begrenzter OS-Nachweis – noch NICHT genehmigt

Die neue Architektur erbt weder den alten Einzelversuch noch dessen Freigabe.
Noch fehlt ein neuer, separat geprüfter realer Einmalharness für diesen Pfad;
es gibt absichtlich keinen frei ausführbaren Owner-/JavaExec-Probetask. Vor
Ausführung müssen dessen neue Hashbindung, einmaliger Verbrauch, äußere
gehaltene Beobachtung und tatsächliche STOP-Verdrahtung erneut geprüft sein.
Keine Wiederverwendung von 4D-10/ba92a0e4 oder alten Markerdateien.

**Empfohlene Reihenfolge:**
1. Zunächst nur eine eigene synthetische Owner-Prüf-JVM mit festem Image und
   neuen gepinnten Classroots, 0 Worker, 0 Jobs. Konsolen-/Startprovenienz und
   gehaltene Identität/Exitcode/stdout/stderr-Ende prüfen. Auftretender ungeklärter
   conhost: STOP, keine pauschale Ausnahme. Vorher konkreten geordneten eigenen
   Beendigungsweg und äußeren STOP-Kanal vorbereiten; sonst nicht starten.
2. Danach separat genau ein Job-Owner, ein synthetischer zunächst suspendierter
   Worker und eine private Jobgruppe. Keine Browser/Nachfahren oder Nutzdaten.
   JOB_LIST/Handle-Allowlist, gehaltene volle Identität, Limits/Mitgliedschaft
   vor erster Ausführung und kontrollierter Abbruch/Endnachweis abnehmen.
   Keine Ausführung bei ungeklärter Konsolen-/Ancestorjobzuordnung.
3. Erst danach ein regulärer synthetischer HELLO/Map/CHANNEL_END/EOF-Roundtrip
   mit Workerexit, eigener Gruppenbilanz und geschlossenem Journal/allen Ressourcen.
   Separate Freigaben für Timeout, Shutdown, Ownercrash/Last-handle und Nestedjobs;
   später erst Firefox-/Geckodriver mit lokalen Inhalten und variablen Nachfahren.

Je Versuch braucht es neue explizite Rollen-/Startobergrenzen, Budget-/Hashfreigabe
und atomaren Einmalverbrauch. Ausgangspunkt für äußere Begrenzung bleibt höchstens
25 s, synthetischer innerer Lauf einschließlich Cleanup höchstens 15 s; konkreten
Probeplan vor Start festlegen, keine Businessbudgets heimlich verlängern und keine
neue Graceperiod nach jeder Phase. Nach genau einem Versuch stoppen, kein Retry.

STOP bei Identitäts-/Handle-/Mitgliedschafts-/Limitfehler, ungeklärtem Zusatzprozess,
unbestätigtem Zugriff auf eigene Ressourcen, Timeout, falschem Protokoll/Exitcode,
unvollständigem Gruppen-/I/O-/Ressourcenabschluss oder unbekannter Native-Rückkehr.
Keine PID-only-Bereinigung, unbekannten Handles oder fremden Prozesse anfassen.
Kill-on-close und leere Listen allein ersetzen keinen vollständigen Nachweis.

### 8. Bewusste Restgrenzen und Abschluss

Die neue Owner-/IPC-/Journalimplementierung ist fakegeprüft; die neue native
Pipe-/Factoryverdrahtung ist lediglich kompiliert. Echte Windows-ABI-/Pipe-/
JOB_LIST-/Nichtvererbungs-/Nestedjob-/Cleanupsemantik bleibt ungeprüft.
conhost-Provenienz, kurzfristige Nachfahren, Job-/Heapquoten und maximale reale
Importpayloads sind weiterhin eigene Gates. Kein garantierter Cleanup bei
vollständig blockiertem Owner/Kernel/Dateisystem; kein automatischer Quarantänereset.
Normale Bibliotheksfunktionen werden hier nicht angebunden oder verändert.

Es wurden keine echten S/C/H-/Worker-/Firefox-/Geckodriver-/Seleniumprozesse,
Jobs oder Win32-Handleoperationen gestartet, keine DB/SQL/Migration, Anwendung/
HTTP, Netzwerk, Downloads/Installationen oder Gitoperationen ausgeführt. Keine
Arbeit an SSRF/T03/T04/T06. Historische OS-Freigaben unverändert verbraucht.
Nach diesem Bericht anhalten; weitere OS-Abnahme und produktive Scraperanbindung
bleiben gesondert freizugeben.

## Nachtrag 29.10.2026 – Einmaliger Owner-Provenienzversuch vorbereitet

**Status: READY_FOR_OWNER_OS_APPROVAL. Keine OS-Ausführung freigegeben oder erfolgt.**
Der frühere Hinweis auf einen noch fehlenden Owner-Einmalharness bleibt historische
Evidenz. Die Vorbereitung ist jetzt vorhanden; eine reale Windows-Abnahme steht aus.
Die historischen 913 Java-/78 PowerShell-Ergebnisse werden dadurch nicht ersetzt.

### Abgegrenzter Versuch und Komponenten

Neue isolierte Source-Sets `ownerProbe` und `ownerProbeCheck`, expliziter Testtask
`ownerProbeUnitTest`. Drei kleine JDK-only Laufzeitklassen unter
`src/ownerProbe/java/de/mangalib/ownerprobe`: OwnerProvenanceMain,
OwnerProbeFiles und OwnerProbeLifecycle. Keine Spring-, Worker-, Job-, JNA-,
Selenium- oder Datenbankklassen auf ihrem Klassenpfad; kein JavaExec-Task.
Dies ist eine eigenständige synthetische Owner-Prüf-JVM, noch nicht der spätere
Job-Owner und kein Nachweis seines Worker-/Jobverhaltens.

Vier PowerShell-Dateien unter `src/ownerProbe/powershell`: OwnerProbe.ps1
(Dateipreflight und injizierter Kontrollablauf), OwnerNativeInspection.ps1
(kalte C#-Deklarationen), Start-OwnerProvenance.ps1 (explizite Startbarriere)
und OwnerProbe.Check.ps1 (ausschließlich Fakes/Metadaten).
Die sechs Win32-Importe sind auf GetProcessId, GetProcessTimes,
QueryFullProcessImageNameW, WaitForSingleObject, GetExitCodeProcess und CloseHandle
beschränkt. In dieser Vorbereitung wurden nur C#-Kompilierung und reflektierte
Signatur-/DLL-Pfadprüfungen ausgeführt, KEIN Import und keine Inspection-Methode.

Der spätere Versuch darf genau EINEN Start über ein ursprüngliches
System.Diagnostics.Process-Objekt anfordern: Java 21, feste java.exe,
UseShellExecute=false, CreateNoWindow=true, Hidden, drei umgeleitete Streams.
Feste Argumentfolge: `-Xmx256m -cp <ownerProbe-Classroot>` gefolgt von
`de.mangalib.ownerprobe.OwnerProvenanceMain --owner-provenance-v1 <UUID> <Artefakthash>`.
Arbeitsverzeichnis ist das gebundene Projektverzeichnis. Keine Agenten, JVM-
Overrides, Shellbefehle oder fremden Classroots. Die Kindumgebung wird geleert;
nur SystemRoot und versuchseigene TEMP/TMP-Verzeichnisse werden übergeben.
Keine Datenbankpasswörter, kein PATH-basierter Java-/Toolstart.

### Integritätsbindung und noch nicht erteilte Freigabe

Neuer Kandidat: `07574fb1-9fc5-41de-8a9b-d77c8841edf2`.
Verzeichnis: `.gradle/t02-owner-provenance/07574fb1-9fc5-41de-8a9b-d77c8841edf2`.
Es enthält aktuell ausschließlich `manifest.txt`; KEINE approval.txt, KEIN
consumed und KEINE Lauf-/Ergebnisdatei. Keine historische Freigabe gilt hierfür.

- Artefakt-SHA256: `f2b5924d03f2459fefd114bf0b64fe27a385d6d3fd72d0bdb2df75fb28afa9c4`
- Manifest-SHA256: `122cae0ac17c1131f50d9e802d8f7d13ff03623685c4be4f72c819c401d54315`
- java.exe-SHA256: `7272cc277abdafc7fa9fdfad2ca04639824a2d31338cd7010cbcb4006ee7fba8`
- kernel32.dll-SHA256: `e132eba4166256e2f414b91fe7d5386590bc5ea8626aee5af9c9738383cf2ae1`

Rein lesende PE-Prüfung: AMD64 (8664), PE32+ (020B), Konsolensubsystem 3;
JDK-release meldet Java 21/x86_64. java.exe ist daher kein Beweis für ausbleibende
Konsolenbegleiter; CreateNoWindow bleibt eine zu überprüfende Startvorgabe.
Der gemeinsame PowerShell-/Java-Fingerprint bindet absoluten Projektpfad,
java.exe und kernel32.dll, SHA256 von jli.dll/jvm.dll/JDK-release, sämtliche
erlaubten kompilierten Probe-Klassen sowie die drei Laufzeitskripte in fester
Ordnung. Klassenmajor 65, Größen-/Anzahlgrenzen, keine Reparse-Pfade.
Kein vollständiger Hash sämtlicher JDK-/Windows-Komponenten; die Installation
und der lokale Benutzerkontext bleiben Vertrauensvoraussetzungen.

Manifestformat (UTF-8 ohne BOM, LF, abschließendes LF): OWNER_PROBE_V1,
UUID, Artefakthash, Java-Hash, kernel32-Hash, absoluter Projektpfad – je eine Zeile.
Erst NACH einer neuen ausdrücklichen OS-Freigabe darf approval.txt mit
OWNER_APPROVED_V1/UUID/Artefakthash/Manifesthash erzeugt und deren Hash gebunden
werden. Vor Native-Initialisierung, Beobachtungsregistrierung oder Start wird
der Verbrauch per FileMode.CreateNew atomar reserviert und dauerhaft geflusht:
OWNER_CONSUMED_V1/UUID/Artefakthash/Approvalhash. Schon ein Vorstartfehler nach
diesem Punkt verbraucht den Versuch. Keine Wiederholung und keine Markerreparatur.
Kind und Elternharness prüfen die Bindung erneut. Änderungen/Neukompilierung
oder JDK-/Systemupdates machen den Kandidaten ungültig; kein stilles Neubinden.
Hashprüfungen sind keine Schutzgrenze gegen einen gleichzeitig böswillig
schreibenden Benutzer mit denselben Dateirechten; keine Sandboxzusage.

Tatsächlich kalt ausgeführt und erfolgreich:

```powershell
& .\src\ownerProbe\powershell\Start-OwnerProvenance.ps1 -AttemptId 07574fb1-9fc5-41de-8a9b-d77c8841edf2 -ValidateOnly
```

Ergebnis: Approved=false, Consumed=false, NativeCalls=false, OwnerStarts=0,
WorkerStarts=0, JobCreates=0; Inspection-Typ danach weiterhin nicht geladen.
`-ExecuteOnce` wurde NICHT verwendet. Ohne exakt einen expliziten Modusschalter
bricht das Skript schon vor dem Preflight ab.

### Identität, Beobachtung, STOP und Abschluss des späteren Versuchs

Verbindlich ist ausschließlich der ursprüngliche SafeProcessHandle aus dem
eigenen Process.Start. PID, vollständiger Imagepfad und native Erstellungszeit
werden darüber abgefragt und bei Endbeobachtung/Close exakt erneut geprüft.
Kein OpenProcess, kein PID-Reopen, keine CIM-Zeitannäherung, keine 200-ns-Toleranz.
Ein natives Wait-Signal UND erfolgreicher Exitcodeabruf sind erforderlich;
Exitcode 259 wird bei signalisiertem Handle nicht als noch laufend umgedeutet.
Für PASS ist tatsächlicher Exitcode 0 erforderlich.

Vor dem einzigen Start wird eine lokale Win32_ProcessStartTrace-Subscription
registriert. Der eigene Root-Start muss ergänzend darin erscheinen. Maximal 256
Traceereignisse; Pollfehler, fehlender Rootnachweis oder Überlauf verhindern PASS.
Eine lokale CIM-Parent-Momentaufnahme ergänzt die Trace. Zusatzprozesse bleiben
UNPROVEN/Owned=false. Nachfahren, weitere Kinder des Beobachters sowie jeder neu
beobachtete conhost.exe/OpenConsole.exe führen konservativ zu STOP – auch ohne
passenden Parent. Das kann einen unabhängigen Konsolenstart als Fehlalarm
einordnen, legitimiert aber niemals einen Fremdkill. Weitere Traceereignisse
werden nur ergänzend protokolliert. Name, Parent und zeitliche Nähe sind KEINE
Besitz- oder native Identitätsbeweise. Verlorene Providerereignisse und global
lückenlose Prozessenumeration sind damit ausdrücklich NICHT ausgeschlossen.

Dateibasierte, begrenzte IPC bindet UUID/Artefakthash/PID/native Geburt. Der Owner
hat fünf Sekunden Bindefrist und insgesamt höchstens 15 Sekunden im kooperativen
Kontrollablauf, einschließlich eigener Vorprüfung. STOP wird vor READY/FINISH
ausgewertet. FINISH erst bei passender READY und ergänzendem Root-Trace.
Äußerer Beobachtungs-/Abschlussablauf: EIN 25-s-Budget, keine erneuerte Graceperiod.
Bei jedem Fehler: PARTIAL/STOP, kein zweiter Start, keine PID-/Namensbereinigung.

PASS erfordert native Endbeobachtung/Exitcode 0, passendes CLOSED|NORMAL,
stdout-EOF ohne Inhalt, stderr-EOF ohne Diagnose, keine Zusatzbefunde sowie
bestätigten Abschluss eigener Ressourcen. Ausgaben sind eng begrenzt (stdout
ein Zeichen als Verletzungsdetektor, stderr maximal 128 Zeichen); rohe interne
Fehlertexte werden verworfen. STOP darf nur den festen Marker OWNER_STOP_V1
liefern. Ein Protokoll-CLOSED oder EOF allein beweist kein Prozessende.
Vor dem Schließen des eigenen Handles nochmals native Instanz-/Endprüfung;
CloseHandle muss Erfolg melden, danach SafeHandle invalidieren und eigenen
Process/Streams entsorgen. Eigene Subscription referenzgebunden abmelden;
keine fremden Subscriber/Events entfernen. Ergebnisdatei CreateNew, kein Überschreiben.

Bei fehlendem End-/EOF-/Close-/Subscription-Nachweis bleibt die ursprüngliche
Besitzsitzung mit ihren Referenzen offen. Nur passives Warten, keine neue
Beobachtungsfrist, keine Hintergrund-Recovery oder fremde Terminierung.
Das ist kein erfolgreiches Cleanup. Blockierte JVM-/Kernel-/Dateisystem-/WMI-
Aufrufe haben keinen hier bewiesenen harten Abbruch; ein solcher Zustand braucht
erneute Eigentümerentscheidung. Keine globale Handle-/Thread-/OS-Ressourcenbilanz.
Lokale WMI-Rechte und reales CreateNoWindow-/SafeHandle-/CIM-/EOF-Verhalten bleiben
ungetestete OS-Voraussetzungen. Keine Rechteerhöhung oder neue Ausnahme automatisch.

### Tatsächlich ausgeführte datenbankfreie Prüfungen

- **26 neue Javafälle** in OwnerProbeTest: Lebenszyklus, gebundener STOP vor READY,
  STOP vor FINISH, Timeout, falsche Identitäten/200-ns-Abweichung, Hash-/Manifest-/
  Approval-/Verbrauchsbindung, ungültige UUIDs, Größen/ASCII/Overwrite und Classpathtrennung.
- **56 bestehende Worker-Owner-/Wire-Fälle** erneut bestanden, ausschließlich Fakes
  und Speicherstreams. Zusammen **82 Javafälle, 0 Fehler, 0 übersprungen**.
- **44 neue PowerShell-Fake-/Metadatenfälle**: Einmalverbrauch/Replays,
  Manipulationen vor/nach Verbrauch, Vorstartbudget, Identitäts-/Beobachtungs-/
  STOP-/End-/EOF-/Close-/Subscriptionfehler, fehlendes READY, falscher CLOSED,
  konservative Zusatzprozessbehandlung und kalter Launcher. Vier Fälle führen
  den tatsächlichen Pollblock mit vollständig ersetzten Event-/CIM-Kommandos
  aus: conhost/OpenConsole außerhalb des Ownerbaums, weiteres Beobachterkind
  und ein nur ergänzend erfasster unabhängiger Prozess. C# nur kompiliert/
  reflektiert. Fake-Porttests sind KEINE Abnahme der realen CIM-/Win32-Verdrahtung.
- **78 bestehende PowerShell-Fakes** erneut bestanden (48+24+6).

Gradle ausschließlich `--offline --no-daemon`; jeder tatsächliche Lauf hatte
vorher einen identischen `--dry-run`. Neuer isolierter Java-Prüflauf und danach
gezielter bestehender Owner-Prüflauf erfolgreich. Keine Wiederholung aller
historischen 913 Javafälle, keine Vollsuite, keine JNA-Speicher-/Bindingtests.
Ein anfänglich vom Dateisandbox begrenzter Cachezugriff wurde über die genehmigte
Gradle-Cacheberechtigung wiederholt, ohne Downloads oder Repositoryänderungen.
Während der Testentwicklung Parser-/Closurefehler im neuen PowerShell-Fakecode
korrigiert; keine alten Tests/Assertions abgeschwächt.

```powershell
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest --rerun
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest workerOwnerUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest workerOwnerUnitTest --rerun
& .\src\ownerProbe\powershell\OwnerProbe.Check.ps1
& .\src\scNative\powershell\ScOuterControl.Check.ps1
& .\src\scNative\powershell\ScOwnedObservation.Check.ps1
```

### Trennung, Unverändertheit und nächstes Gate

Produkt-/Manualgraph (`test check build assembleManualTest`) nur per Offline-
Dry-Run geprüft: keine ownerProbe-/Worker-/Native-Probetasks eingebunden.
Vorhandene 258 Quell-/AGENTS-/Ignoredateien unverändert, aggregierter SHA256
1709c96e9f6cc3cdb6dbc469f121dd488d6293f799ce59f5d262f31aff925969.
140 historische Evidenz-/Markerdateien unverändert, aggregierter SHA256
84d90c6dc26d89b3c8a83defd9badb80a05ba8dbe1612f2e9e951e945afa13c3.
Manual-JAR weiterhin SHA256
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Historisches jna-5.13.0.jar darin bleibt unverändert; keine JNA-Freiheit behauptet.
Gradlekonfiguration und Dokumente nur durch neue Anhänge ergänzt.

Keine reale Owner-/S/C/H-/Worker-/Job-/Firefox-/Geckodriver-/Seleniumausführung,
keine ausführenden Win32-Aufrufe, Datenbank/SQL, Anwendung/HTTP, Netzwerk,
Downloads/Installationen oder Gitoperationen. Nur zugelassene Gradle-Testprozesse.
Keine historische oder neue echte Freigabe erzeugt/verbraucht. Alle bisherigen
DB-Guards und der Manualpfad unberührt. Nach diesem Vorbereitungsauftrag stoppen.

Nächstes Gate ist eine ausdrückliche, einmalige OS-Freigabe nur für den genannten
Owner-Kandidaten und unveränderten Hashstand. Dann erneut kalte Integritäts- und
Umgebungsprüfung, danach genau ein synthetischer Owner mit gehaltener äußerer
Beobachtung. KEIN Worker, Job Object, Anwendungs-/Browser-/DB-/Netzwerkstart und
kein Retry. Ein auftretender Konsolenbegleiter wird dokumentiert, nicht durch
Namensausnahmen oder eine zweite automatische Ausführung übergangen.

## Nachtrag 29.10.2026 – Owner-Einmalversuch vor Start abgebrochen

**Versuchstatus PARTIAL/STOP; weitere OS-Ausführung BLOCKED.** Die ausdrückliche
Eigentümerfreigabe galt nur für 07574fb1-9fc5-41de-8a9b-d77c8841edf2 und den
vorbereiteten Artefakthash. Der unveränderte Harness wurde genau einmal mit
ExecuteOnce aufgerufen. Er scheiterte in Phase SUBSCRIBE, VOR jeder Anforderung
eines Owner-Starts. Die Freigabe ist dennoch bestimmungsgemäß dauerhaft verbraucht.
Der vorherige READY-Vorbereitungsstatus ist historisch; keine Windows-Abnahme
der Owner-Provenienz, kein erfolgreicher Ressourcenabschluss und kein neues READY.

### Erneute Vorprüfung und atomarer Verbrauch

Branch ausschließlich durch Lesen von .git/HEAD bestätigt, kein Gitkommando.
ValidateOnly erfolgreich, kein vorab geladener Inspection-Typ, zunächst nur das
unveränderte Manifest im Versuchverzeichnis. Java-/JDK-/kernel32-/Klassen-/
Skripthash und Manifest stimmen mit der genehmigten Bindung überein. Java-PE
erneut statisch AMD64/PE32+/Konsolensubsystem geprüft. Manual-JAR hashgleich.
Lokale lesende CIM-Prozessabfrage erfolgreich; keine laufende Owner-Probe und
bei der ersten Vorprüfung keine Java-Instanz. Keine Commandlines ausgegeben
oder in Evidenzdateien gespeichert. Keine zusätzlichen Builds oder Tests.

Vorhandenes manifest.txt unverändert:
`122cae0ac17c1131f50d9e802d8f7d13ff03623685c4be4f72c819c401d54315`.
Artefakthash weiterhin:
`f2b5924d03f2459fefd114bf0b64fe27a385d6d3fd72d0bdb2df75fb28afa9c4`.

Gebundene approval.txt neu erstellt, CreateNew/Flush(true)/Hashreadback:
`2f73a1455d358a56bafcacea1897a38a5407f3c0484c4c1352c2fe6e07609424`.
Der Harness hat consumed atomar und dauerhaft VOR Native-Deklaration,
Beobachtungsregistrierung und möglichem Start erzeugt:
`5b734268904db0cfae041ff837b2ed03b728eb263d5977a47106f725053815da`.
Beide Dateien bleiben unverändert. KEIN Wiederholungsrecht wegen fehlendem Start.

### Tatsächlicher Ablauf und getrennte Endnachweise

| Nachweis | Beobachtung |
|---|---|
| Beobachtersitzung | Eigene PowerShell-PID 17608 laut eigener Sitzung; Toolsession 69334. Keine native Owner-Identität daraus ableiten. |
| Vorprüfung | 2026-10-29T17:48:12.7188269Z, in preflight.json gespeichert. |
| Verbrauchsdatei | LastWriteUtc 2026-10-29T17:48:13.2257838Z; Dateizeit ist kein nativer Prozesszeitnachweis. |
| Fehlerphase | SUBSCRIBE, ObserverFailed=true; der Block konnte keinen bestätigten Subscriber zurückgeben. |
| Owner-Start | StartRequested=false, StartConfirmed=false: kein Aufruf des einzigen Process.Start-Pfades. |
| Owner-PID/Image/native Geburt | Nicht vorhanden: kein gestarteter Owner, kein erworbener ursprünglicher Owner-Handle. |
| Owner-Ende/Exitcode | Nicht anwendbar/nicht erhoben; Ended=false, ExitObserved=false, ExitCode=null sind Initialwerte, KEIN Beleg eines lebenden Owners. |
| stdout/stderr-EOF | Nicht erhoben, keine Owner-Pipes erworben; beide EOF-Felder false, kein erfolgreicher I/O-Abschluss behauptet. |
| READY/FINISH/STOP-Datei/CLOSED | Nicht vorhanden. StopRequested=true ist der Harness-Fehlerstatus; StopSent=false. |
| Owner-Handleabschluss | OwnerHandleClosed=false als nicht erbrachter Nachweis; kein erworbener Owner-Handle und kein Owner-Leak nachgewiesen. |
| Subscriptionabschluss | SubscriptionClosed=false. Registrierung/Restressourcen nicht abschließend geklärt. |
| Ergebnisdatei | result.json dauerhaft geschrieben, LastWriteUtc 2026-10-29T17:48:14.1368566Z. |
| Weitere Rollen | WorkerStarts=0, JobCreates=0; keine S/C/H-, Browser- oder Scraperrolle angefordert. |

Der Fehler wurde vom vorbereiteten Harness nur als FailurePhase=SUBSCRIBE
persistiert, ohne Exceptiontyp, HRESULT oder konkrete Unteroperation. Nicht
nachgewiesen ist daher, ob die lokale Registrierung, der folgende Subscriber-
Abruf oder eine dortige Prüfung scheiterte. Ein Berechtigungs-/Providerproblem
ist eine mögliche Ursache, aber NICHT belegt. Die erfolgreiche vorherige
CIM-Leseabfrage beweist keine Berechtigung für Win32_ProcessStartTrace.
Keine erneute Eventregistrierung oder zusätzliche OS-Diagnoseprobe durchgeführt.
Die sechs Inspection-Win32-Funktionen liegen hinter der fehlgeschlagenen Phase
und wurden in diesem Ablauf nicht aufgerufen; Add-Type wurde zuvor erreicht.

### Konsolenprovenienz und verbleibende eigene Sitzung

preflight.json erfasst 21 bereits vor dem Harness beobachtete conhost.exe-
Prozesse mit PID/Parent, soweit verfügbar Imagepfad und ergänzender CIM-Geburt.
Alle sind Owned=false. Unter anderem PID 24740 mit Parent 17608, Image
C:\\WINDOWS\\system32\\conhost.exe, CIM-FileTime 134377696911322870.
Dieser Konsolenhost war bereits im Ausgangsbestand der PowerShell-Sitzung;
kein Owner wurde gestartet. Name/Parent/Zeit liefern keine native Besitzzuordnung.
Keine Konzession für spätere Owner-Konsolenhosts und keine globale Aussage,
dass während der fehlgeschlagenen Registrierung keine Zusatzprozesse entstanden.
RootTraceSeen=false, Findings/SupplementalTrace leer: kein funktionierender
Owner-Trace-Nachweis, nicht etwa erfolgreich bewiesene Abwesenheit von Begleitern.

Der kalte Launcher hatte vor der Registrierung eine eigene Subscription-
Referenzhalterung angelegt. Weil Subscribe nicht bestätigend zurückkehrte,
behält sein finally diese ursprüngliche Sitzung mit OWNER_RETAINED_SESSION_OPEN
passiv offen. Das ist die vorbereitete konservative STOP-Regel, KEIN Cleanup.
Eine aktive oder fehlgeschlagene Teilregistrierung ist aus result.json allein
nicht unterscheidbar. Keine Sitzung beendet, keine Subscriber nachträglich
entfernt und keine fremden Prozesse/Handles berührt. observer-completed.json
wurde folgerichtig nicht erreicht. Der noch offene Sitzungsabschluss benötigt
einen gesonderten, eng begrenzten Diagnose-/Bereinigungsauftrag; keine Befugnis
für erneuten Ownerstart, neue Freigabe oder selbstständige OS-Recovery ableiten.

### Gesicherte Dateien und Abschluss

Eigene neue Evidenzen ausschließlich im bereits gebundenen Versuchverzeichnis:
preflight.json, approval.txt, consumed und result.json. SHA256 result.json:
`4ccdce6cc71cbd165ef11052d19f7fad44eac3859da96f22726b5f4a3a6764e8`;
SHA256 preflight.json:
`d932cbd5bb52dcd95bb1f5cdd0703eeeb7866f69fd2afdc5dc93de3b8986db73`.
Keine alte Evidenz oder bestehende verbrauchte Freigabe geändert.

Nach dem Versuch nur Dateievidenzen gelesen und Dokumentnachträge ergänzt.
Vorbereiteter Artefakthash unverändert; alte 258 Quelldateien und 140 historische
Evidenz-/Markerdateien weiterhin mit denselben aggregierten Hashes wie im
vorigen Nachtrag. Manual-JAR weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Keine Source-/Guard-/Assertionänderung, Tests/Gradle, Wiederholung, Worker,
Job Object, fremde Terminierung, DB/SQL, Anwendung/HTTP, Browser/Scraper,
Netzwerk/Installation oder Gitoperation. Keine produktive Freigabe.
Nach Dokumentation angehalten; neue Diagnose und jeder weitere Versuch erfordern
erneute ausdrückliche Freigabe. Dieser Kandidat bleibt verbraucht.

## Nachtrag 29.10.2026 – SUBSCRIBE analysiert, Erwerbsbelege und Diagnose verbessert

Dieser Auftrag ist ausschließlich Quellen-/Evidenzanalyse und Fakevalidierung.
Kein weiterer Owner-/OS-Versuch und KEINE aktive Prüfung oder Veränderung der
historischen Sitzung 17608/69334. Deren gegenwärtiger Lebenszustand wurde nicht
abgefragt. Der vorherige PARTIAL/STOP und die verbrauchte Freigabe bleiben gültig.
Die vereinfachte Worker-/Job-Architektur wird dadurch weder ersetzt noch angebunden.

### 1. Rekonstruktion: belegt, möglich und nicht rekonstruierbar

Der historische SUBSCRIBE-Block in Start-OwnerProvenance.ps1 konnte an mehreren
Stellen scheitern: Bestandsabfrage mit Get-EventSubscriber, bestehender gleicher
SourceIdentifier, Erzeugung der Referenzhalterung, Register-CimIndicationEvent,
anschließender Subscriberabruf oder Prüfung auf genau einen Subscriber.
Danach lag im Kontrollkern noch eine Null-/Fristprüfung unter demselben
Phasenbezeichner. Diese Möglichkeiten lassen sich aus result.json nicht weiter
eingrenzen. Die kurzen Dateizeitabstände sprechen gegen einen normalen 25-s-
Fristablauf, ersetzen aber keine gespeicherte monotone Laufzeitmessung.

Belegt sind StartRequested=false, StartConfirmed=false, FailurePhase=SUBSCRIBE,
ObserverFailed=true und das Fehlen von Owner-IPC-Dateien. Native Owneridentität,
Process.Start, Pipeerwerb und die sechs Inspection-Win32-Funktionen waren nicht
erreicht. Add-Type lag vor der fehlgeschlagenen Phase. Kein tatsächlicher
Win32-/CIM-Fehlercode ist in der historischen Ergebnisdatei vorhanden.

**Nachgewiesener Diagnosefehler:** Der äußere catch speicherte nur den
Phasenstring. Exceptionkette, HRESULT, Fehlerkategorie und Unteroperation wurden
verworfen. Außerdem verschluckte der alte Startport seine Startausnahme vollständig.
SilentlyContinue bei lokalen Bestands-/Queueabfragen konnte unerwartete Fehler
verdecken. Diese Codebefunde sind belegt; dass gerade einer davon die historische
WMI-Registrierung scheitern ließ, ist NICHT belegt. Berechtigungen, Provider,
Subscriberzustand und Registrierungsrückgabe bleiben Hypothesen. Keine Ableitung
einer notwendigen Administratorausführung und keine automatische Rechteerhöhung.

**Nachgewiesenes Erwerbsproblem:** Der alte Port hielt eine Referenz zunächst nur
in seiner Skriptscope. Bei einer Ausnahme kam kein Wert im Kontrollkern an.
Damit konnte dieser nicht unterscheiden, ob gar kein Erwerb begonnen hatte,
eine unbekannte Teilregistrierung existierte oder eine eigene Referenz bekannt
war. Der Skript-finally behielt vorsichtshalber die Sitzung offen. Die möglichen
Ressourcen des konkreten Altversuchs bleiben unbekannt; kein Leak bewiesen.

### 2. Umfang und technische Entscheidung

Ein ursprünglicher, gehaltener nativer Owner-Handle reicht für eigene PID,
exakte native Geburt/Imageidentität, Wait-Signal und tatsächlichen Exitcode.
Mit getrennten Streamtasks/EOF und eigenem Handleclose lässt sich der eigene
Ownerabschluss unabhängig von WMI bestätigen. Er beweist keine Abwesenheit
anderer Prozesse. Name, Parent-PID und eine Konsole sind keine Besitzbelege.

Der zusätzliche Provenienzversuch soll auch kurzlebige Konsolenbegleiter
erfassen. Reine Vorher-/Nachher-Snapshots würden sie übersehen. Deshalb bleibt
die lokale Ereignisbeobachtung für diesen gesonderten Provenienznachweis
erhalten. Sie ist KEINE notwendige Voraussetzung für das technische eigene
Endsignal und kein neuer verpflichtender Dienst der Worker-/Job-Architektur.
Kein automatischer Snapshot-Fallback, der fehlenden Ereignisnachweis als Erfolg
ausgibt. Bei fehlender Beobachtung weiterhin PARTIAL/STOP. Keine Behauptung einer
global lückenlosen Trace: verlorene Providerereignisse sind weiterhin möglich.
Auch ein unabhängiger neuer Konsolenhost kann einen konservativen Fehlalarm
auslösen; das rechtfertigt niemals eine fremde Prozessbeendigung.

Vereinfachung ohne zusätzliche Laufzeitkomponente: ein einziger Erwerbsbeleg
wird jetzt VOR dem Subscribe-Port erstellt und als dasselbe Objekt durchgereicht.
Kein Rückgabewert muss Teilressourcen nach einer Ausnahme rekonstruieren.
New-OwnerObservationReceipt unterscheidet NOT_ATTEMPTED, UNCONFIRMED und
CONFIRMED. Unteroperationen: CHECK_EXISTING, REGISTER_CIM, VERIFY_SUBSCRIBER,
BOUND sowie CHECK_ORIGINAL, UNREGISTER, REMOVE_EVENTS und VERIFY_RELEASE.

- Vor Register-CimIndicationEvent auf UNCONFIRMED setzen; erst nach bestätigter
  Originalreferenz auf CONFIRMED. Keine Ressourcenfreiheit aus einer Exception
  des Registrierungsaufrufs folgern.
- NOT_ATTEMPTED benötigt keinen erfundenen Close. Kein vorsorgliches Abmelden
  und keine bloß wegen einer leeren Referenzhalterung dauerhaft offene Sitzung.
- Bei bekannter eigener Originalreferenz kann der zukünftige freigegebene
  Harness innerhalb derselben Restfrist referenzgebundenen Abschluss versuchen.
  Bei unbekannter Teilregistrierung niemals nach Name/Source/PID blind entfernen.
- Fehlende Abschlussbestätigung hält den eigenen Erwerbsbeleg offen.
  Verspätetes Ack erneuert kein Budget und erlaubt kein PASS. Ein belegter,
  aber verspäteter Close darf getrennt als Ack erscheinen, nicht als rechtzeitiger Erfolg.
- Lokale Subscriber-/Eventlisten jetzt mit ErrorAction Stop abfragen und danach
  auf den exakten SourceIdentifier filtern. Leere Listen sind regulär; andere
  Abfragefehler werden nicht mehr durch SilentlyContinue verborgen. Poll und
  Unsubscribe vergleichen zusätzlich die tatsächliche Originalreferenz. Nach
  dem Abmelden zusätzlich die eigene Queue erneut auf Leere prüfen; ein noch
  vorhandenes eigenes Event verhindert die Abschlussbestätigung.

Report trennt OwnerCompletionConfirmed, OwnerExitAccepted, OwnerHandleAcquired,
ObservationComplete, ProvenanceAccepted und ResourcesUnconfirmed. Vollständige
Beobachtung mit Zusatzbefund bedeutet nicht akzeptierte Provenienz. Ein Ende
mit Fehlerexit kann bestätigt sein, ohne einen akzeptierten Exitcode zu liefern.
Ein korrekt geschlossener Owner bei Beobachterfehler bleibt Gesamt-PARTIAL/STOP.
Alle bisherigen PASS-Bedingungen, STOP-Prioritäten, Hash-/Einmalbarrieren,
native 200-ns-Exaktheit und Größen-/Zeitgrenzen bleiben wirksam.

### 3. Begrenzte technische Diagnose

Get-OwnerFailure/Add-OwnerFailure speichern Phase und feste Unteroperation,
Fehlerkategorie, bis zu vier Exceptiontypen/HRESULTs, native Win32-Fehlercodes
und bei CimException den numerischen CIM-Status. Kontrollierte OWNER-Codes nur
aus einer festen exakten Allowlist. Weitere Fehler beim Cleanup ersetzen nicht
die erste Ursache. Bis zu acht Fehlerbelege; Grenzerreichung wird markiert.
Exceptionketten- und Error-ID-Trunkierung werden ausdrücklich angezeigt.

Keine freien Message-, ErrorDetails-, Stack-, TargetObject-, Commandline- oder
Umgebungsinhalte. Freie Error-ID nur als SHA256 der maximal ersten 4096 Zeichen;
Typnamen begrenzt und auf bekannte System-/CIM-Namensräume beschränkt. Damit
bleiben numerische technische Ursachen und Zuordnung erhalten, nicht sämtliche
Rohinformationen. Es wird keine verlustfreie Speicherung beliebiger Secrets
oder unbegrenzter Exceptionketten versprochen. Tests prüfen dies mit synthetischen
Secret-Markern und verschachtelten Win32Exceptions, ohne Win32-Aufruf.

Kalter Preflightfehler gibt vor jedem möglichen Start einen begrenzten BLOCKED-
Beleg aus. Startausnahme behält Originalobjekt und Diagnose. Unerwarteter äußerer
Harnessfehler ist konservativ PARTIAL/STOP mit UNCONFIRMED-Startzustand, kein
fälschlich behaupteter sicherer Vorstartabbruch. Reportwritefehler ebenfalls
begrenzt diagnostiziert. Keine automatische Reparatur oder zweite Ausführung.

### 4. Historische Sitzung: nur statischer Befund und gesonderter Plan

Aus dem alten Code möglich: PowerShell-Runspace, Skript-/ErrorRecordreferenzen,
dynamisch kompilierter Inspection-Typ, Stoppuhr und eine möglicherweise ganz
oder teilweise erworbene temporäre CIM-Ereignisquelle samt Subscriber und Queue.
Keine Owner-JVM, Owner-Pipes oder Owner-Prozesshandles erworben. Keine Jobgruppe
oder Workerrolle. Register-CimIndicationEvent ist hier der temporäre Sitzungsweg;
der Quellcode erstellt keine permanenten WMI-Filter/Consumer. Keine globale
WMI-Reparatur oder Suche/Löschung von __EventFilter-Objekten begründbar.
Der alte $Error-Puffer könnte zusätzliche Diagnose enthalten; ob er sie noch
enthält, wurde nicht geprüft. Neue Skripte verändern die bereits laufende alte
Kontrollschleife nicht rückwirkend.

**Benötigte separate Freigabe, derzeit NICHT erteilt:**

1. Ausschließlich die ursprüngliche Tool-/Startsession 69334 identitätsgebunden
   read-only prüfen, ggf. deren ursprünglichen gehaltenen Prozessbezug und
   vorhandenen Runspacezugang. PID 17608, Parent, CIM-Zeit oder Commandline
   allein reichen nicht zum Wiedererkennen für eine Beendigung. Historisch fehlt
   eine gespeicherte native Beobachter-Geburtszeit; keine Zeittoleranz erfinden.
2. Nur bei belegtem Zugang zum ursprünglichen Runspace dessen genaue eigene
   SourceIdentifier-Bindung `MangaLib.OwnerProvenance.07574fb1-9fc5-41de-8a9b-d77c8841edf2`,
   vorhandene Original-Subscriberreferenz und begrenzte Fehler-/Queuemetadaten
   erfassen. Keine freien $Error-/Commandline-/Message-Dumps, keine neue Registrierung.
3. Bei bestätigter eigener Originalreferenz ausschließlich diese abmelden,
   deren eigene Queue abschließen und Abwesenheit/Dispose im selben Runspace
   nachweisen. Keine globale Wildcard-Abmeldung und kein nachträglich erratener
   SubscriptionId. Unbekannte Teilregistrierung bleibt STOP.
4. Die ursprüngliche passiv wartende Toolsession nur über einen dokumentierten,
   identitätsgebundenen geordneten Unterbrechungs-/Abschlussweg beenden und deren
   Ende belegen. Keine Annahme, dass Ctrl+C-Bytes in eine Nicht-PTY-stdin-Pipe
   tatsächlich ein Konsolensignal oder geordnetes Cleanup erzeugen.

Der ursprüngliche Aufruf war nicht interaktiv/TTY-gebunden und seine finally-
Schleife hat keinen vorbereiteten Befehls-/Stopdateieingang. Ein kooperativer
Zugriff ist deshalb NICHT zugesagt. Falls kein sicherer originaler Sitzungszugang
oder Abschlussweg verfügbar ist: BLOCKED berichten und erneut entscheiden lassen.
Keine PID-only-Terminierung, Prozessnamenkills, Debugger-/Codeinjektion, fremde
Handles oder eigenständige erzwungene Beendigung als Ersatz. Keine Schritte
dieses Plans in diesem Auftrag ausgeführt. Cleanup benötigt eigene Freigabe;
sie wäre keine neue Owner-/Worker-/Jobfreigabe.

### 5. Tatsächliche Validierung, Integrität und weiteres Gate

**63 PowerShell-Fake-/Metadatenfälle bestanden:** 44 bisherige und 19 zusätzliche.
Zusätzliche Fälle umfassen vor/nach/teilweise Registrierung, echte Subscribe-/
Unsubscribe-Skriptblöcke mit ausschließlich lokalen Cmdlet-Doubles, falsche
Originalreferenz trotz gleicher ID, fehlende/verzögerte Acks, numerische
Win32-Diagnose, Secret-/Längen-/Exceptionkettengrenzen, kalten Preflight sowie
unabhängigen Ownerabschluss. Keine reale Subscription registriert, gelesen,
verändert oder entfernt. C# nur wie zuvor kompiliert/reflektiert; keine Imports
oder Inspection-Methoden aufgerufen. Eine leere Pipeline war im neuen Release-
Testdouble anfangs falsch modelliert; Double korrigiert, keine Assertion abgeschwächt.

**78 bestehende PowerShell-Fakes** (48+24+6) erneut bestanden.
**26 isolierte Javafälle**, 0 Fehler/Errors/übersprungen, erneut bestanden:

```powershell
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon ownerProbeUnitTest --rerun
& .\src\ownerProbe\powershell\OwnerProbe.Check.ps1
& .\src\scNative\powershell\ScOuterControl.Check.ps1
& .\src\scNative\powershell\ScOwnedObservation.Check.ps1
```

Kein weiterer Gradlelauf, keine 913er-/Spring-/DB-Vollsuite und keine JNA-Tests.
PowerShell-Syntax und Whitespace-/Konfliktmarkerprüfung erfolgreich; keine
Git-Diffprüfung wegen ausdrücklichem Gitverbot. Nur drei Harness-/Testskripte
geändert und Dokumente durch neue Nachträge ergänzt. Buildkonfiguration,
Javaquellen, Native-Deklarationen, Guards und Manualpfad unverändert.

145 historische Dateien (140 ältere plus fünf des verbrauchten Owner-Versuchs)
vorher/nachher identisch, aggregierter SHA256:
`03880e5eea7552df153c16c003514dda7ceb4405b1284877f8e6a3fc580ead55`.
Manual-JAR unverändert:
`9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9`.
Historische Dokumentabschnitte bleiben unverändert. Kein neuer realer Versuch,
kein Manifest/Approval/Verbrauchsmarker erzeugt; synthetische Fakefreigaben nur
in nicht ausführbaren Testfixtures mit falschem Java-/Artefaktziel im Tempbereich.

Aktueller, NICHT freigegebener Harnessfingerprint:
`004efcc5f5a213b14b1ff89cb5ba61244ff18adf03b7215094d4ee9cf90a8f38`.
Der alte Manifest-/Freigabehash passt nach der ausdrücklich erlaubten
Harnessänderung nicht mehr; alte Evidenzen werden nicht neu gebunden.
Ein neuer Owner-Versuch kann nach Klärung der historischen Sitzung und neuer
Integritätsvorbereitung technisch vorbereitet werden, braucht aber neue UUID,
festes Manifest, getrennte ausdrückliche OS-Freigabe und Einmalverbrauch.
Kein READY_FOR_OWNER_OS_APPROVAL für einen neuen Kandidaten in diesem Auftrag.
Reales WMI-/CIM-/Win32-/EOF-/Closeverhalten weiterhin ungetestet, kein harter
Abbruch vollständig blockierter JVM-/Kernel-/Dateisystemaufrufe zugesagt.

Keine aktive historische Sitzungsprüfung, Prozess-/Handle-/Subscriptionbeendigung,
Owner-/Worker-/Job-/Browser-/Scraperstarts, Datenbank/SQL, Anwendung/HTTP,
Netzwerk/Installation oder Gitoperation. Architektur und Prioritäten unverändert.
Nach Dokumentation stoppen; jede aktive Bereinigung und OS-Ausführung separat.

## Nachtrag 29.10.2026 – Autonome Fortführung bis zum Windows-Ausführungsgate

Der Eigentümer hat sichere Implementierung/Reviews und datenbankfreie Prüfungen
innerhalb der vereinfachten Variante B zusammenhängend freigegeben. Echte OS-,
Bereinigungs-, Browser-, DB-, Netzwerk- und Produktintegrationsschritte benötigen
weiterhin vorherige ausdrückliche Freigabe. Kein Architekturwechsel oder neuer
Kontrollprozess; keine selbstständige Wiederverwendung verbrauchter Versuche.

### Erledigt: Ressourcen-/Nebenläufigkeitsreview und reproduzierte Korrekturen

Vier neue Unitfälle zunächst gezielt ausgeführt: alle vier reproduzierten Fehler.
Drei Factoryfälle (Exception, null, temporäre Teilressource ohne Sessionrückgabe)
meldeten bisher irreführend bestätigtes Gruppenende. Außerdem führte ein
konkurrierender WorkerJobOwner mit nicht erworbenem pending zur gemeinsamen
Quarantäne des gültigen Auftrags einer anderen Instanz. Der zweite Fall wurde
mit zwei vorab konstruierten Ownern, gemeinsamer Journalwurzel und einem per
Latch laufenden ersten Auftrag reproduziert – keine echte Prozessinstanz.

WorkerJobOwner hält jetzt journalOwned erst nach erfolgreichem begin. Kein
complete oder neuer gemeinsamer Quarantänemarker ohne diesen Besitzbeleg.
Der abgewiesene lokale Owner bleibt gesperrt; fremdes pending und vorhandene
Quarantänemarker bleiben unangetastet. Ein partiell geschriebenes eigenes
pending wird ebenfalls nicht entfernt. Der ursprüngliche gültige Owner kann
seinen belegten Abschluss normal durchführen; keine automatische lokale Recovery.

Factory-Aufruf vor möglichem Ressourcenerwerb als unbestätigt markieren; erst eine
nicht-null zurückgegebene Session hebt diesen Zustand auf. Unbestätigte/ungesunde
Sessions dürfen nicht einmal einen Job anlegen. Kein Gruppen-/Ressourcenabschluss
aus fehlender Rückgabe erfinden; persistentes pending/quarantine erhalten.
Known-no-session-Abschluss ohne NullPointerException behandelt. Keine fremden
Prozesse, PID-Kills oder Änderung der nativen Gruppen-/Identitätsbedingungen.

### Erledigt: gemeinsamer, noch nicht live angebundener Importablauf

Neuer JDK-only WorkerImportRunner mit zwei injizierten Reihen-/Bandoperationen.
Vollständiger Request inklusive EOF muss vor Arbeit gültig sein. Danach gebundenes
HELLO sofort flushen, erst dann Scraperdouble ausführen. Das ist notwendig,
damit die separate Startfrist später nicht den gesamten langen Reihenimport
erfasst. Flache Map mit vorhandenen Feldnamen, Unicode, Preisen und null bleibt
im bestehenden WorkerWire-Binärformat; keine Änderung der MVC-/JSON-Endpunkte.

WorkerWire trennt HELLO, vollständige begrenzte Ergebniskodierung und einmaligen
Ergebnisversand. Bestehendes reply validiert weiterhin vor erster Ausgabe.
Der neue Dispatcher wandelt bekannte beendete Handler-/Payloadfehler in genau
eine gebundene leere Fehlerantwort um. Nach einem Outputfehler keine zweite
Antwort/kein erneuter Handleraufruf, weil bereits Teilbytes angekommen sein können.
SyntheticWorkerMain benutzt denselben Dispatcher mit synthetischen Maps.

KEINE tatsächliche MangaScraper-/BandScraper-/LiveScraperGateway-Anbindung.
Diese bleibt genehmigungspflichtige produktive Integration. Befund für später:
Die alten Scraper schreiben Logs auf System.out. Der tatsächliche zukünftige
Worker muss Protokoll-stdout exklusiv halten und Legacyausgaben separat begrenzen;
anderenfalls korruptes IPC. Keine globale System.setOut-Änderung in der Anwendung.
Keine Änderungen ihrer Geckodriverpfade, URLs, Importfelder oder Browserlogik.
Prozessisolation ersetzt weder SSRF-/Redirect-/Unterressourcenschutz noch sichere
Profile/Downloads. Diese realen Sicherheits-/Kompatibilitätsnachweise stehen aus.

### Ausgeführte Prüfungen und tatsächliche Grenzen

Nach Korrekturen **69 Worker-Javafälle bestanden** (56 bisherige +4 Besitz-/Factory-
Regressionen +9 Dispatcherfälle), außerdem **26 Owner-Probe-Javafälle bestanden**:
insgesamt **95 bestanden, 0 Fehler, 0 übersprungen**. Native Workerquellen nur
kompiliert, keine JNA-/Win32-Methoden ausgeführt. Bestehende Assertions erhalten;
der unknown-Session-Fall prüft zusätzlich null Job-Erzeugungen.

**63 PowerShell-Harnessfälle und 78 ältere PowerShell-Fakes bestanden (141).**
Keine echte lokale Event-/CIM-Abfrage oder Registrierung und keine Prüfung der
historischen Sitzung in diesem Auftrag. C# nur wie bisher kompiliert/reflektiert.

```powershell
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun
```

Jeder tatsächliche Gradlelauf hatte identischen vorherigen Offline-Dry-Run,
einschließlich des anfänglich absichtlich roten vierteiligen Reproduktionslaufs.
Keine ausgeschlossenen regulären Assertions oder dauerhaft roten/disabled Tests.
Keine Wiederholung der gesamten historischen 913er-/DB-Suite. Review umfasste
Admission, fremden Journalbesitz, STOP/Shutdown, Factory-Ungewissheit, frühen
HELLO, leere Fehlerframes und Teilwrite ohne Retry. Fakes beweisen keine reale
ABI-, Nestedjob-, Pipe-, Firefox- oder Geckodriverzuordnung.

### Neuer kalter Kandidat und notwendige konkrete Ausführungsfreigabe

Neues reines Owner-Manifest vorbereitet, KEINE ausführbare Freigabe:
`983900f4-de55-45b3-a144-54a5e5f547dd` unter
`.gradle/t02-owner-provenance/983900f4-de55-45b3-a144-54a5e5f547dd`.
Artefakt-SHA256 `004efcc5f5a213b14b1ff89cb5ba61244ff18adf03b7215094d4ee9cf90a8f38`,
Manifest-SHA256 `41b18e214e099a636b813ff1f88a33f92d6c4b41e6a0d868fd4cb13c57edef02`.
ValidateOnly erfolgreich: nur manifest.txt, Approved=false, Consumed=false,
NativeCalls=false, OwnerStarts/WorkerStarts/JobCreates=0; nativer Typ nicht geladen.
Dieses Manifest enthält weiterhin KEINE Worker-/Jobklassen. Workeränderungen
erteilen keine neue Ownerfreigabe; das alte 07574fb1-Manifest bleibt verbraucht.

**Nächste unvermeidbare Grenze, Freigabevorschlag:**
1. Zweck: reale Beobachtung/Abschluss belegen, bevor darauf Worker-/Browserbetrieb
   aufgebaut wird. Nur ursprüngliche Toolsession 69334 identitätsgebunden prüfen
   und bei belegtem geordnetem Zugang ihre eigenen Ressourcen abschließen.
   SourceIdentifier und enger Ablauf wie im vorherigen Nachtrag. Keine PID-only-
   Beendigung, kein blindes Unregister, kein unsicheres Ctrl+C in eine stdin-Pipe.
2. Erst NACH belegtem historischen Sitzungsabschluss und erneuter vollständiger
   Hash-/Umgebungsprüfung den genannten frischen Kandidaten ausdrücklich genau
   einmal freigeben. Approval neu erstellen, vor jedem möglichen Erwerb atomar
   verbrauchen, genau einen synthetischen Owner anfordern. Null Worker, null Jobs,
   keine Browser/DB/Anwendung/Remoteverbindungen oder Netzwerkabrufe.
3. Risiken: unzugängliche alte nichtinteraktive Sitzung; unbekannte WMI-Rechte/
   Providerfehler; neue Konsolenbegleiter; unvollständige EOF-/Closebelege oder
   blockierter OS-Aufruf. Feste bisherige 25-s-Außen-/15-s-Innenbudgets, kein Retry.
4. STOP: historische Zuordnung/Abschluss nicht belegt, Hash-/Profilabweichung,
   Registrierungsfehler, ungeklärter Zusatzprozess, Identitätsfehler, falscher
   Exitcode oder fehlender End-/EOF-/Closebeleg. Kein Ownerstart, wenn schon die
   Vorbedingungen scheitern. Keine Erweiterung auf Worker-/Jobtests ableiten.

Nur diese genehmigungspflichtige Ausführung steht jetzt an; nicht bloß ein
weiterer Fake-Meilenstein. Danach zielgerichtet privaten synthetischen Worker-/
Jobpfad real abnehmen, dann Firefox/Geckodriver mit lokalen Inhalten und erst
danach separat freizugebende produktive Gatewayanbindung. Kein Broker/Custodian
oder unabhängige Recovery für einen vollständig blockierten lebenden Owner.
Weitere echte Ausführungen erhalten jeweils die notwendige konkrete Freigabe.

145 historische Evidenz-/Markerdateien weiterhin aggregierter SHA256
03880e5eea7552df153c16c003514dda7ceb4405b1284877f8e6a3fc580ead55.
Manual-JAR weiterhin SHA256
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Gradlekonfiguration, Native-Sessionquellen, DB-Guards, main/manual-Anbindung
unverändert. Nur sechs isolierte Workerquellen/-tests und Dokumentnachträge
geändert/ergänzt, dazu das neue nicht freigegebene Manifest. Keine Gitoperationen,
Downloads/Installationen, DB/SQL, Anwendung, Owner-/Worker-/Job-/Browser-/Scraper-
oder externe Netzwerkstarts. Kein aktueller Lebenszustand der alten Sitzung behauptet.

## Nachtrag 30.10.2026 – Freigegebene Sitzungsprüfung: STOP vor Owner-Versuch

Der Eigentümer genehmigte die identitätsgebundene Prüfung/Bereinigung der
ursprünglichen Toolsession 69334 und bedingt danach genau einen Owner-Versuch
983900f4-de55-45b3-a144-54a5e5f547dd. Schritt 2 setzte den vollständig belegten
Abschluss von Schritt 1 voraus. Keine Erweiterung auf Worker-/Job-/Browsertests.

AGENTS.md, letzte Dokumentnachträge, historische Ergebnisdatei und Launcher-
Abschlussweg erneut gelesen. Branch durch Lesen von .git/HEAD bestätigt,
kein Gitkommando. Danach genau einmal den ursprünglichen Toolbezug 69334 mit
write_stdin und leerer Eingabe abgefragt, ohne Ctrl+C oder Befehlsübermittlung.
Die Toolschicht antwortete: `write_stdin failed: Unknown process id 69334`.

**STOP/BLOCKED in Schritt 1.** Die Meldung betrifft die interne Toolreferenz,
nicht einen nativen Endnachweis für Windows-PID 17608. Nicht unterschieden
werden können beendeter Prozess, verlorene Toolregistrierung oder anderer
Verlust des Sitzungszugangs. Keine dieser Ursachen ist zusätzlich belegt.
Keine native Erstellungszeit, kein gehaltener ursprünglicher Prozesshandle,
kein Exitcode oder Subscriber-/Queueabschluss konnten darüber erhoben werden.
Keine Behauptung, dass die historische Sitzung noch läuft oder sicher beendet ist.

Gemäß ausdrücklicher STOP-Regel keine Wiederholung, keine PID-basierte Ersatz-
zuordnung, keine CIM-/WMI-/Prozessabfrage zur Umgehung des fehlenden Toolbezugs,
keine Subscriber-/Handleentfernung und keine Prozess-/Sitzungsbeendigung.
Schritt 2 vollständig ausgelassen: kein Launcher-/ValidateOnly-Aufruf, keine
neue approval.txt und kein consumed, null Owner-/Worker-/Jobstarts. Die bedingte
Ownergenehmigung wurde nicht als ausführbare Freigabe erstellt oder verbraucht.
Für Fortsetzung fehlt ein belastbarer Identitäts-/Abschlussnachweis der
ursprünglichen Sitzung; die Genehmigung erlaubt keinen PID-only-Fallback.

Nach STOP ausschließlich Dateien gelesen und diese Dokumentnachträge ergänzt.
Der frische Kandidat enthält weiterhin nur manifest.txt, SHA256
41b18e214e099a636b813ff1f88a33f92d6c4b41e6a0d868fd4cb13c57edef02.
Keine erneute vollständige Artefaktvalidierung oder Windows-Provenienzprüfung
behauptet. 145 historische Evidenz-/Markerdateien hashgleich, aggregiert
03880e5eea7552df153c16c003514dda7ceb4405b1284877f8e6a3fc580ead55;
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Historische Dokumentpräfixe erhalten. Keine Codeänderungen, Tests, Builds,
Win32-Experimente, DB/SQL, Anwendung, Browser/Scraper, Netzwerk, Installation
oder Gitoperation. Weitere native Ausführungen angehalten.

## Nachtrag 30.10.2026 – Unabhängige Probe statt globaler historischer Sitzungssperre

### Neubewertung und unveränderte Garantien

Die alte Bereinigungspflicht war eine Verfahrensvorgabe der bedingten Freigabe
für 983900f4. Der damalige STOP war korrekt. Sie ist nach der jetzt ausdrücklich
verlangten Neubewertung keine notwendige globale Voraussetzung für eine neue,
unabhängig isolierte Probe. Die alte Freigabe wird NICHT umgedeutet oder genutzt.

- Historischer Abschluss: weiterhin UNKNOWN; keine Exit-/Subscriber-/Queue-
  oder Prozessfreiheit nachträglich behauptet und keine Sitzung erneut abgefragt.
- Historischer Importbesitz: gepinntes Ergebnis 07574fb1 dokumentiert Vorstart-
  abbruch SUBSCRIBE, StartRequested/StartConfirmed=false, Identity=null,
  WorkerStarts/JobCreates=0. Kein eigener Import-/Jobpfad war erreicht.
- Möglicher Restbestand: PowerShell-/CIM-Beobachtung und Queue/Runspacereferenzen.
  Das ist kein Besitz des neuen Prozesses oder eines neuen Importjobs. Allgemeiner
  Speicher-/CPU-/Providerdruck bleibt möglich; neue Fehler führen weiterhin STOP.
- Neue Grenze: eigene Kennung/Verzeichnisse/IPC-/Eventquelle, ursprünglicher
  eigener Prozesshandle, keine Übertragung alter Ressourcen und keine Öffnung
  der alten PID zur Bereinigung. Name/Parent/PID bleiben kein Eigentumsbeweis.
- Tatsächlich zugeordneter unbestätigter Importjob: weiterhin Startsperre im
  WorkerJournal. Kein Reset, keine Ausnahme und kein Ausweichen auf einen anderen
  Journalpfad. Ein neuer Scopeabschluss besagt nichts über historischen Cleanup.

Eine sichere Bereinigung der alten Sitzung bleibt eine getrennte Aufgabe, aber
keine globale Sperre jeder neuen Probe. Die alte Beobachtung wurde ohne Action-
Skript registriert; daraus folgt keine Verwaltungsbefugnis über neue Ressourcen.
Die Grundarchitektur Worker/Windows Job Objects bleibt unverändert.

### Eng begrenzte technische Abgrenzung

Get-OwnerHistoryClassification ist reine Fallklassifikation für genau dieses
alte Profil, Outcome/Phase und boolesche falsche Startwerte, keine Owneridentität,
ganzzahlige Nullwerte für Worker/Jobs sowie eine neue kanonische Kennung.
Strings wie "false"/"0" sind kein Ersatz. Diese Funktion liefert ausdrücklich
EvidenceValidated=false und erteilt keine Ausführungserlaubnis.

Get-OwnerHistoricalBoundary akzeptiert dagegen nur die gepinnten Originalbytes
von result.json, SHA256
4ccdce6cc71cbd165ef11052d19f7fad44eac3859da96f22726b5f4a3a6764e8.
Begrenzt 8192 Byte einmal lesen, dieselben Bytes hashen/parsen, Reparsepfade
ablehnen; keine ungebundene zweite Lesung. Erst danach EvidenceValidated=true.
Fehlende/veränderte Evidenz oder gestartete Rollen sind kein Freigabegrund.
Die echte Vorprüfung und Reports halten die Abgrenzung sichtbar fest:
HistoricalSessionState=UNKNOWN, HistoricalCleanupConfirmed=false,
RequiresHistoricalCleanup=false, GrantsExecution=false,
MayClearImportQuarantine=false, MayTerminateHistoricalProcess=false.
Nur der experimentelle Ownerharness verwendet diese konkrete historische
Abgrenzung; die spätere Anwendung hängt nicht von privaten .gradle-Evidenzen ab.
Keine neue Recoveryhierarchie oder allgemeine Prozessmanagementplattform.

### Zusätzlich zwei Worker-Fristfehler reproduziert und korrigiert

Beide neuen Regressionen zunächst einzeln datenbankfrei rot, nach Korrektur grün:
1. Eine Factory konnte vor dem Start des Kontrollthreads blockieren, ohne dass
   Status/Admission den abgelaufenen Start erkannten. state() erkennt jetzt die
   Frist und setzt nur lokale STOP-/Cleanupzustände, anschließend QUARANTINED.
   Keine Nativecalls/zweite Factory. Durables pending sperrt den Neustart;
   späte Factoryrückgabe kann weder freigeben noch einen Job erzeugen.
2. Bei Fristablauf vor jedem Factoryerwerb dereferenzierte der Abschluss trotz
   fehlender Session channelEnded. Die NullPointerException erzeugte falsche
   Quarantäne. Keine Session bedeutet jetzt keinen Kanalabschluss. Belegter
   Nichterwerb darf innerhalb der Frist nur das eigene Journal abschließen;
   vorhandene Quarantäne wird niemals gelöscht. Unbestätigter Erwerb bleibt gesperrt.

Kein unabhängiger Recoveryprozess für einen vollständig blockierten lebenden
Owner und keine harte Abbruchzusage für blockierte OS-/Dateisystemaufrufe.

### Ausgeführte Prüfungen und Integrität

**97 Javafälle bestanden (71 Worker +26 Owner-Probe), 0 Fehler/übersprungen.**
**77 PowerShell-Harnessfälle +78 bestehende Fakes =155 bestanden.** Die 14 neuen
PowerShellfälle prüfen Abgrenzung, falsche Start-/Jobdaten/Typen, getrennte
Namespaces, ungepinnte/fehlende Evidenz, unveränderte Journale und Scopeabschlüsse.
Reguläre Fakefixtures vollständig synthetisch, ohne private .gradle-Abhängigkeit
oder Kopieren privater Berichte in öffentliche Quellen. Die reale Pinprüfung
wurde gesondert durch den kalten Vorbereitungsweg belegt.

Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run, einschließlich
der zwei zunächst roten Reproduktionsläufe. Abschließender Prüfsatz:

```powershell
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun
& .\src\ownerProbe\powershell\OwnerProbe.Check.ps1
& .\src\scNative\powershell\ScOuterControl.Check.ps1
& .\src\scNative\powershell\ScOwnedObservation.Check.ps1
```

Native Quellen nur kompiliert, C# nur kompiliert/reflektiert; keine JNA-/Win32-
Funktion, echte Event-/CIM-Abfrage oder Registrierung. Keine bestehende Assertion
abgeschwächt. Keine DB-/913er-Vollsuite, produktiver/manual Build oder zusätzliche
Dependency. Syntax/Whitespace/Konfliktmarker und historische Dokumentpräfixe
geprüft; kein Git-Diff wegen Gitverbot.

### Nächste tatsächlich notwendige Freigabe

Endgültiger frischer Kandidat: f4d9665f-b554-4dd4-a305-84759b832c73.
Artefakt-SHA256: f4dc585238534c40f7fd91257bcffb407a8213dc6c29a7340ff1aaa3b176cf5a.
Manifest-SHA256: 992fa6015615b29f72b9809130e6ed0e1c84b079efe69bc0269fdc68e9203e3c.
ValidateOnly erfolgreich: EvidenceValidated=true bei ausdrücklich unbekanntem
historischem Cleanup, Approved/Consumed/NativeCalls=false, alle Startzähler=0,
nativer Typ nicht geladen. Nur manifest.txt, keine Approval-/Verbrauchsdatei.
Zwischenkandidat eda75495 aus diesem Review blieb unverändert unfreigegeben;
seinen inzwischen überholten Hashstand nicht verwenden. 07574fb1 bleibt
verbraucht; 983900f4 und die alte bedingte Genehmigung werden nicht neu gebunden.

Neue konkrete Freigabe erforderlich: genau ein unabhängiger synthetischer Owner
für den endgültigen Hashstand, ausdrücklich OHNE vorgelagerten historischen
Cleanupnachweis. Keine alte PID/Session/Subscription anfassen. Null Worker,
null Job Objects, keine Firefox-/Geckodriver-/Selenium-, DB-, Anwendungs- oder
externen Netzwerkstarts. Eigene ursprüngliche Prozess-/Pipe-/Observerreferenzen,
erneute Vorprüfung und atomarer Einmalverbrauch vor Erwerb/Start. Bestehende
25-s-/15-s-Fristen, kein Retry; neue ungeklärte Begleiter, Providerfehler oder
fehlende Identitäts-/Root-/Exit-/EOF-/Closebelege bleiben STOP.

Das ist jetzt eine echte Ausführungsfreigabegrenze, keine erneute Entscheidung
über die Workergrundarchitektur und keine alte Sitzungssperre. Reale ABI-/CIM-/
Start-/Job-/Browsernachweise lassen sich nicht durch weitere Fakes ersetzen.
Anschließend synthetischen Job-/Workerpfad und lokale Browserfälle real abnehmen,
bevor produktive Gatewayanbindung oder Einsatzreife behauptet werden. SSRF,
Redirects/Unterressourcen, Profile/Downloads und reale Ressourcenquoten bleiben
durch Job Objects allein ungesichert. Keine ungeschützten Selenium-Fallbacks.

146 vor diesem Auftrag vorhandene Evidenz-/Manifestdateien hashgleich,
aggregiert A956AD9EFFDE21C8F0DE2E22883656871990C1ADE93848ADCFC7919A12EE3FCB.
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Sieben Projektdateien geändert (drei Owner-PowerShellskripte,
WorkerJobOwner/WorkerOwnerTest, zwei Dokumentnachträge), zusätzliche Manifeste
ausschließlich unfreigegeben. Keine Testguard-/Buildkonfigurations-/main-/manual-
Änderung, aktive Sitzungsprüfung/Bereinigung, Owner-/Worker-/Job-/Browser-/
Scraperstarts, DB/SQL, Anwendung, Netzwerk, Installation oder Gitoperation.
Historische Evidenzen und Prioritäten erhalten; Scraperabsicherung bleibt bis
zu realen Nachweisen und genehmigter Integration ausdrücklich unvollständig.

## Nachtrag 30.10.2026 – Unabhängiger Owner-Versuch: REGISTER_CIM PermissionDenied

Die ausdrückliche neue Einmalfreigabe für f4d9665f-b554-4dd4-a305-84759b832c73
wurde nach erfolgreicher erneuter Integritätsprüfung genau einmal verwendet.
**PARTIAL/STOP vor Ownerstart, Freigabe verbraucht, kein Retry.** Die historische
Sitzung wurde wie freigegeben weder abgefragt noch bereinigt. Ihre Ungewissheit
war nicht der Abbruchgrund. Keine neuen Worker-/Job-/Browserfreigaben ableiten.

### Erhobene Evidenz und Ursache

ValidateOnly, Manifest/Artefakt, kanonischer Namespace, Java-PE AMD64/PE32+/
Konsolensubsystem, Skriptparser und Manual-JAR erneut erfolgreich geprüft.
Frische PowerShellsitzung, Inspection-Typ zuvor nicht geladen, zunächst nur
manifest.txt. Lokale lesende Prozessvorprüfung erfolgreich, kein eigener
Owner-Kandidat gefunden; keine Commandlines ausgegeben/gespeichert.

approval.txt neu gebunden erzeugt, SHA256
bf771ca5e15e7d431b98388f64ef9f85ae6d4bf0c7353d216da842f366c3f534.
consumed vor Native-Deklaration/Registrierung/Start atomar dauerhaft erzeugt,
SHA256 e0ba97f4146270dbac9758c9af52ac4fef2a15006d61af1b61c1588d654dc140.
Artefakt weiterhin f4dc585238534c40f7fd91257bcffb407a8213dc6c29a7340ff1aaa3b176cf5a,
Manifest weiterhin 992fa6015615b29f72b9809130e6ed0e1c84b079efe69bc0269fdc68e9203e3c.

Der unveränderte Harness meldet konkret:

| Feld | Tatsächlicher Nachweis |
|---|---|
| Phase/Unteroperation | SUBSCRIBE / REGISTER_CIM |
| Kategorie | PermissionDenied |
| Exceptiontyp | Microsoft.Management.Infrastructure.CimException |
| HRESULT | 0x80131500; für sich allein keine konkrete Win32-Zugriffsursache |
| CIM-Status | 2 |
| Fristablauf | DeadlineExpired=false |
| Registrierung | ObservationAcquisition=UNCONFIRMED, keine ursprüngliche Subscriberreferenz bestätigt |
| Ownerstart | StartRequested=false, StartConfirmed=false, OwnerHandleAcquired=false |
| Owner-PID/native Geburt/Exitcode | Nicht erhoben: kein Owner gestartet und kein Ownerhandle erworben |
| stdout/stderr-EOF | Nicht erhoben, keine Owner-Pipes erworben; false-Felder sind kein Beleg eines lebenden Owners |
| Ressourcenabschluss | SubscriptionClosed=false, ResourcesUnconfirmed=true; kein erfolgreiches Cleanup behauptet |
| Weitere Rollen | WorkerStarts=0, JobCreates=0; keine Browser-/Scraperrolle angefordert |

Damit ist für DIESEN Versuch die verweigerte Prozessereignisregistrierung im
Ausführungskontext nachgewiesen. Nicht festgestellt: welches konkrete Recht,
welche UAC-/Konten-/Provider-/Namespace-Richtlinie verantwortlich ist. Keine
nachträgliche Gleichsetzung mit der unbekannten Ursache des alten 07574fb1-
Versuchs. Insbesondere keine pauschale Empfehlung, MangaLib/Worker erhöht
auszuführen oder WMI-/Windows-Berechtigungen zu verändern.

Get-EventSubscriber-Bestandsprüfung war passiert; VERIFY_SUBSCRIBER und der
einzige Process.Start-Pfad wurden nicht erreicht. Add-Type wurde erreicht,
die sechs Owner-Inspection-Importfunktionen nicht aufgerufen. Die ausgegebene
Ausnahme enthält keine freien Rohtexte oder Secrets; die neue Diagnose lieferte
die vorher fehlende Unteroperation und die numerischen Metadaten.

### Zeiten, Konsolen und offene neue Beobachtersitzung

preflight.json: 2026-10-30T14:34:43.8835042Z, ObserverPid=28324.
consumed LastWriteUtc: 2026-10-30T14:34:44.2976744Z.
result.json LastWriteUtc: 2026-10-30T14:34:45.0730638Z.
Dateizeiten sind keine nativen Owner-Start-/Endzeiten. Keine solchen erfunden.

22 vorhandene Konsolenhosts im Ausgangsbestand, alle Owned=false. Darunter
conhost PID 37432, Parent 28324, Image C:\\WINDOWS\\system32\\conhost.exe,
CIM-FileTime 134378444824360510. Bereits vor Registrierung vorhandener
Beobachterbegleiter, kein gestarteter Owner und kein Owner-Konsolennachweis.
Leere Findings/RootTraceSeen=false sind kein Beweis globaler Prozessabwesenheit.

Der Harness meldete OWNER_RETAINED_SESSION_OPEN, Toolreferenz 51102. Er behält
seine ursprüngliche Beobachtersitzung gemäß bestehender STOP-Regel passiv offen,
weil ein Teilregistrierungsabschluss nicht bestätigt ist. Eine PermissionDenied-
Ausnahme allein beweist nicht die Freiheit sämtlicher lokaler Teilressourcen.
Keine blinde Abmeldung, kein Prozesskill, keine nachträgliche aktive Prüfung der
neuen oder alten Sitzung. observer-completed.json wurde nicht erreicht.
Der gegenwärtige Lebenszustand wird nach dem Versuch nicht erneut abgefragt.

### Sichere Nachbereitung und nächster zielführender Weg

Nach diesem Versuch keine weitere native Ausführung/Registrierung. Ergänzter
synthetischer Regressionstest: PermissionDenied verbraucht einmalig, startet
keinen Owner, hält unbekannten Erwerb unbestätigt, versucht kein blindes Cleanup,
gibt keine Secret-Marker aus und weist dieselbe Freigabe bei erneuter Benutzung ab.
Alle **78 PowerShell-Harness-Fake-/Metadatenfälle bestanden** (77 bestehende +1).
Das Double verwendet eine verwaltete Win32Exception mit expliziter Testmessage
und PermissionDenied-ErrorRecord, keine echten Win32-/CIM-Fehleraufrufe.
Die echte CimException-Diagnose stammt allein aus dem genehmigten Einzelversuch.
Keine Java-/Gradle-/DB-Suite wiederholt; frühere 97 Java-/78 ältere PS-Fakes
bleiben historische Ergebnisse, nicht als heute neu ausgeführt ausgeben.

Der aktuelle Full-Provenance-Harness ist in diesem Kontext durch seine globale
CIM-Ereignisregistrierung blockiert. Der produktive WorkerJobOwner selbst nutzt
keine WMI-Ereignisregistrierung: verpflichtende Prozesssicherheit basiert auf
privatem Job, atomarer Zuordnung vor Ausführung, nativer Identität/Mitgliedschaft,
Gruppenende, I/O-/Handlebilanz und persistenter Admission. Diesen Kern nicht
durch eine globale Windows-Prozessüberwachung ersetzen oder an Administrator-
rechte koppeln. Keine neue Hierarchie oder ungeschützter Selenium-Fallback.

**Vorbereiteter nächster Entwicklungsplan, noch kein neuer Ausführungskandidat:**
1. Eigenen eng benannten Owner-Abschlussnachweis ohne globale WMI-Subscription
   entwickeln. Bestehende gehaltene native Identität, tatsächlicher Exitcode,
   getrennte EOFs und eigene Closebelege bleiben Pflicht. Neue Messgrenze und
   Scope müssen kryptographisch an Manifest/Freigabe gebunden werden; keine
   alte Full-Provenance-Freigabe auf einen schwächeren Messmodus umschalten.
2. Zusätzliche Prozessinformationen nur ergänzend und konservativ auswerten.
   Ohne Ereignisstrom keine Aussage über ausgeschlossene kurzlebige Begleiter
   oder vollständige Konsolenprovenienz. Owner-Abschluss nicht als globalen
   Provenienz-PASS ausgeben. Alte Assertions des Full-Provenance-Profils nicht
   abschwächen; neue Tests für diese klare Ergebnisabgrenzung.
3. Danach den vorhandenen synthetischen Worker-/privaten Jobpfad separat real
   abnehmen: Kernelmitgliedschaft vor Resume, gehaltene Instanz, tatsächliches
   Ende/Exitcode, leere Gruppe und Ressourcenabschluss. Erst danach Browser-
   nachfahren, private Profile/Downloads, Timeouts/Shutdown und Integration.

Das ist eine Änderung des experimentellen Messumfangs, kein Wechsel der
beschlossenen Worker-/Job-Architektur und kein heute erteilter Sicherheits-
nachweis. Noch nicht implementiert oder freigabereif. Keine weitere Owner-
Freigabedatei/Versuchkennung angelegt und keine sofortige neue OS-Probe vorbereitet.
Vor erneuter realer Ausführung ist eine neue konkrete Freigabe erforderlich.

### Unverändertheit und Dateien

Neue Evidenzen nur im genehmigten Versuchverzeichnis: preflight.json,
approval.txt, consumed, result.json; bestehendes Manifest unverändert.
result.json SHA256 edd2efc7cbac80412308de9e44b9f6fc8b02a753201659f0d8e9115b420a5059.
preflight.json SHA256 d9a5fd9544e8501acc77cab4730ec64cf6ca7b5db6c86260c8e11eb3af427edc.
146 ältere geschützte Dateien weiterhin aggregierter Hash
A956AD9EFFDE21C8F0DE2E22883656871990C1ADE93848ADCFC7919A12EE3FCB;
Zwischenmanifest eda75495 ebenfalls unverändert. Manual-JAR weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Laufzeit-Artefakthash nach dem Versuch weiterhin der genehmigte f4dc5852-Stand.
Nur OwnerProbe.Check.ps1 und neue Dokumentnachträge verändert. Kein Produktcode,
Guard, Build/Dependency, DB/SQL, Anwendung, Browser/Scraper, Job Object, fremde
Prozessbeendigung, externe Netzwerktätigkeit, Installation oder Gitoperation.
Weitere native Ausführungen gestoppt; Scraperabsicherung nicht einsatzreif.

## Nachtrag 30.10.2026 – Enger Owner-Abschlussnachweis ohne globale CIM-Registrierung

### Messgrenze und technische Fortsetzung

Die bestätigte REGISTER_CIM-/PermissionDenied-Ursache wird nicht durch weitere
WMI-Registrierungsversuche oder erhöhte Windows-Rechte umgangen. Ein separater
OWNER_COMPLETION_V1-Harness ist implementiert. Der historische Full-Provenance-
Harness und seine strengeren Assertions bleiben bestehen. Sein heutiger
Artefakthash ändert sich durch die gemeinsam gebundenen Quellen; historische
Manifeste/Freigaben werden ausdrücklich NICHT aktualisiert oder wiederverwendet.

Neuer Einstieg: src/ownerProbe/powershell/Start-OwnerCompletion.ps1 und
de.mangalib.ownerprobe.OwnerCompletionMain. Wiederverwendung der bestehenden
Datei-/Hash-/Lifecycle-Helfer und der kalten nativen Inspection-Deklaration,
keine zusätzliche Bibliothek, kein Dienst und keine neue Kontrollhierarchie.

Pflichtbelege des neuen Profils:
- ursprüngliches gehaltenes Process-/SafeHandle, native PID, exakte native
  Erstellungszeit und erwarteter Java-Imagepfad; kein OpenProcess nach PID;
- erneute Instanzprüfung bei Endabfragen; signalisiertes Ende und tatsächlicher
  Exitcode getrennt, insbesondere Exitcode 259 nicht als Lebensbeweis verwenden;
- getrennte stdout-/stderr-EOFs; stdout leer, stderr höchstens 128 Zeichen,
  striktes UTF-8 und ausschließlich feste Diagnosemarker, keine freien Rohtexte;
- stdin-Abschluss, beide Reader abgeschlossen, eigene Streams geschlossen,
  eigener beendeter Prozesshandle nativ geschlossen und Process.Dispose;
- exakte READY-/FINISH-/CLOSED-Dateibindung, Exitcode 0 und gesamte Abwicklung
  innerhalb eines gemeinsamen 25-s-Budgets; Owner hat eigene 15-s-Lifecyclefrist;
- Einmalverbrauch vor NativeInit/Start, keine automatische Wiederholung,
  kein Kill, keine PID-basierte Reparatur und kein Cleanup historischer Sitzungen.

OWNER_COMPLETION_PASS bedeutet ausschließlich diese eigene Abschlusskette.
GlobalProcessProvenance und ConsoleProvenance sind IMMER NOT_MEASURED;
ProvenanceAccepted=false. Es gibt keine globale Ereignisregistrierung oder
Prozessinventur; auch kurzlebige Begleiter werden damit nicht ausgeschlossen.
Keine Behauptung, dass Konsolenhosts fehlen oder fremde Ressourcen beendet sind.
Bei unbekanntem eigenem Erwerb/Ende/EOF/Close bleibt PARTIAL/STOP und die eigene
ursprüngliche Besitzreferenz wird passiv gehalten. Fehler schon vor Process.Start
geben nur bei bestätigtem Nichterwerb und eigener Dispose-Bestätigung Ressourcen
frei. Physischer Abschluss mit fehlendem CLOSED-Protokoll bleibt ebenfalls STOP.

Scope ist auf beiden Seiten gebunden: eigenes Verzeichnis t02-owner-completion,
Manifest OWNER_COMPLETION_V1, separate APPROVED-/CONSUMED-Präfixe und eigener
Main-/Argumenteinstieg. Alte Provenienzfreigaben werden abgewiesen. Integrität
umfasst Java-Executable, jli.dll/jvm.dll/release, kernel32.dll, begrenzte JDK-21-
Ownerklassen, fünf fest benannte Skripte und Workspacepfad. CreateNew/Flush und
Rehash schützen den Einmalmarker; keine heute erstellte ausführbare Freigabe.
Lokaler böswilliger Zugriff mit denselben Benutzerrechten ist kein abgedecktes
Isolationsmodell; Reparsepfade/Overrides und unpassende Dateien werden abgewiesen.

### Tatsächlich ausgeführte datenbankfreie Prüfungen

Vor dem finalen tatsächlichen Gradle-Aufruf identischen Offline-Dry-Run geprüft:

```powershell
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun
```

JAVA_HOME war ausschließlich für diese Aufrufe auf das bestehende JDK 21 gesetzt.
Taskplan enthält nur isolierte Adapter-/Worker-/Owner-Quellen und deren Faketests,
keinen Spring-Kontext, JavaExec, Datenbank- oder Manual-Starttask. BUILD SUCCESSFUL.
XML-Nachweise: **105 Javafälle bestanden, 0 Fehler, 0 übersprungen**: Worker 71,
Owner bisher 26 plus acht neue Profil-/Tuple-/Namespacefälle. Native Quellen nur
kompiliert; keine Kernel32-/JNA-Ausführung durch diese Tests.

PowerShell: **200 Fälle bestanden**: neue Completion-Suite 44,
unveränderte bisherige Provenienzassertions 78, SC/Outer-STOP 48+24,
OwnedObservation 6. Die 44 neuen Fälle umfassen Einmal-/Scope-/Hashfehler,
fehlgeschlagenen Start und Nichterwerb, Identitäts-/Zeitabweichungen, fehlende
oder verspätete Abschlussbelege, Exitcode 259, getrennte EOF-/Closefehler,
gebundene Diagnosecodes ohne Secrets sowie sechs Tests der echten verwalteten
Stream-Ports mit MemoryStreams (leer, Marker, freier stderr-Text, stdout-Daten,
Überlänge, ungültiges UTF-8). Keine echten Pipes oder nativen Methoden aufgerufen.
Neue PowerShell-Dateien außerdem erfolgreich statisch geparst.
Die historische 913er-/Spring-/DB-/DOM-Suite wurde heute nicht wiederholt.

### Neuer Kandidat – nur vorbereitet, keine Ausführungsgenehmigung

- Versuchkennung: 58195e20-5465-4330-871b-5df814cbf11a.
- Scope: OWNER_COMPLETION_V1.
- Artefakt-SHA256: 7d90ec26f64a18538bdf4787248bba038efa96ef1b9ab9cf255c0ffa40ee5ad4.
- Manifest-SHA256: 281f1d6d6e8de95c339d45b504b66fa1c57c7e338d91ec94d0d71358d3ae35bd.
- Verzeichnis: .gradle/t02-owner-completion/58195e20-5465-4330-871b-5df814cbf11a.
- Ausschließlich manifest.txt vorhanden. ValidateOnly erfolgreich: Approved=false,
  Consumed=false, NativeCalls=false, OwnerStarts=0, WorkerStarts=0, JobCreates=0.

Für genau einen realen Abschlussversuch ist eine neue ausdrückliche Freigabe
dieser Kennung, dieses Artefakthashes und dieser engeren Messgrenze erforderlich.
Erst danach neue Approval-Datei erstellen, erneut alles prüfen und vor dem
einzigen Start dauerhaft atomar verbrauchen. Keine alten Grants verwenden.
Zulässiger beantragter Umfang: genau eine synthetische Owner-JVM, eigene native
Identitäts-/End-/Exit-/Closeabfragen, getrennte I/O-Abschlüsse, kein Retry.
Null Worker/Jobs/Browser/DB/Anwendung/Netzwerk und keine historischen Ressourcen
anfassen. Dieser Kandidat ist keine Freigabe für den späteren Worker-/Jobversuch.

### Integrität, Dateien und verbleibende Einsatzreife

146 ältere geschützte Dateien weiterhin aggregierter Hash
A956AD9EFFDE21C8F0DE2E22883656871990C1ADE93848ADCFC7919A12EE3FCB.
Zwischenmanifest eda75495 und sämtliche fünf f4d9665f-Dateien einzeln gegen die
historischen Hashes geprüft und unverändert: insgesamt 152 geschützte Dateien.
Manual-JAR unverändert 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9;
Builddatei unverändert 4bbaca691a25a6240e03ac34f7d2e32a2afed15473a8f51cb744832fe23ac466.
Beide bisherigen Dokumentbytepräfixe erhalten, ausschließlich neue Nachträge.
Keine Gitoperation und deshalb kein behaupteter Git-Diff-/Statusnachweis.

Bearbeitet: OwnerProbeFiles.java, OwnerProvenanceMain.java, OwnerProbe.ps1.
Neu: OwnerCompletionMain.java, OwnerCompletion.ps1, Start-OwnerCompletion.ps1,
OwnerCompletion.Check.ps1, OwnerCompletionProfileTest.java; Dokumentnachträge
in TESTING.md und ROADMAP.md sowie genau ein neues lokales Kandidatenmanifest.
Bestehende Worker-/Job-, Main-/Manual- und Guard-Quellen nicht geändert.
Source Sets bleiben isoliert; keinerlei Einbindung des neuen Einstiegs in das
Manual-JAR oder die Anwendung. Historische Sitzungen 69334/51102 nicht abgefragt
oder bereinigt; ihr Ressourcenstatus bleibt unbekannt, keine Quarantäne aufgehoben.

Reale Einsatzreife bleibt unbewiesen: nach der eigenen Abschlussmessung muss ein
separat freigegebener synthetischer Job-/Workerlauf die atomare Mitgliedschaft vor
Resume, echten Exit, leere private Gruppe, EOF und vollständigen Ressourcenabschluss
belegen. Anschließend Browsernachfahren, private Profile/Downloads, SSRF-/Netzwerk-
grenzen, reale Timeout-/Shutdownfälle und erst ausdrücklich genehmigte Integration.
Globale WMI-Provenienz ist dafür keine Sicherheitsvoraussetzung. Keine neue
OS-Probe, DB-Verbindung, Anwendung, Browser/Scraper, Installation oder Gitoperation
in diesem Entwicklungsauftrag. Status: bereit für separate Owner-Abschlussfreigabe,
Scraperabsicherung weiterhin nicht als einsatzreif freigegeben.

## Nachtrag 30.10.2026 – Einmaliger Ownerabschluss erfolgreich; synthetischer Jobnachweis vorbereitet

### Tatsächlicher Windows-Versuch 58195e20

Ausdrückliche Eigentümerfreigabe ausschließlich für 58195e20-5465-4330-871b-5df814cbf11a,
Artefakt 7d90ec26f64a18538bdf4787248bba038efa96ef1b9ab9cf255c0ffa40ee5ad4.
Branch per HEAD-Datei, Manifest, sämtliche gebundenen Artefakte, Java-Pin,
fehlende JVM-Overrides, frischer Versuchszustand und Manual-JAR erneut geprüft.
ValidateOnly vorab erfolgreich. Keine Artefaktkorrektur oder Neukompilierung vor
diesem Start. Neue Approval-Datei erst danach angelegt; CreateNew, Flush(true)
und Rehash des Verbrauchsmarkers VOR NativeInit und dem einzigen Process.Start.

**Ergebnis: OWNER_COMPLETION_PASS. Genau ein synthetischer Ownerstart.**

| Beleg | Tatsächlicher Wert |
|---|---|
| Profil | OWNER_COMPLETION_V1 |
| ursprüngliche Beobachtersitzung dieses Versuchs | PowerShell PID 4964; regulärer Abschluss der Toolsession mit Exitcode 0 |
| ursprünglicher Owner-Prozesshandle | behalten, nativ identifiziert, identische SafeHandle-Referenz weiterverwendet |
| native Owner-PID | 10812 |
| native Erstellungszeit / FILETIME | 134378472263984761 |
| daraus exakt umgerechnete UTC-Zeit | 2026-10-30T15:20:26.3984761Z |
| Image | C:\\Program Files\\Java\\jdk-21\\bin\\java.exe |
| gehaltenes Ende / tatsächlicher Exitcode | Ended=true, ExitObserved=true, ExitCode=0 |
| stdout | EOF=true, leer |
| stderr | EOF=true, keine Diagnosemarker; serialisiertes Diagnostics=null entspricht hier null Einträgen |
| Dateiprotokoll | READY, FINISH und CLOSED/NORMAL vollständig gebunden |
| eigene Streams / Prozesshandle | jeweils bestätigter Abschluss; Process.Dispose und SafeHandle geschlossen |
| Frist | 1494 ms Harnesszeit, DeadlineExpired=false |
| offene eigene Ressourcen | ResourcesUnconfirmed=false; keine Retained-Sitzung nötig |
| Worker / Jobs / Browser / Datenbank | jeweils null |

Preflight-UTC 2026-10-30T15:20:25.1453160Z; Verbrauchsdatei LastWriteUtc
15:20:25.5343106Z; result.json LastWriteUtc 15:20:27.0072760Z. Dateizeiten sind
keine zusätzlichen nativen Exitzeitmessungen. Keine exakte native Exitzeit erhoben.
GlobalProcessProvenance und ConsoleProvenance bleiben NOT_MEASURED,
ProvenanceAccepted=false. Kein Beweis globaler Ressourcenfreiheit oder fehlender
Konsolenbegleiter; keine WMI-Registrierung, Inventur oder historische Sitzungsprüfung.
Insbesondere Alt-Sitzungen 69334 und 51102 nicht angesprochen oder bereinigt.

Neue unveränderliche Evidenzdateien im bestehenden Versuchverzeichnis:
preflight.json, approval.txt, consumed, binding, ready, finish, closed, result.json.
Manifest blieb unverändert. SHA256:
- Approval: 1e7aa1b417f54906e825a88bd47647e4a8da222bbe79b33a99c08afa05ea2035.
- Verbrauch: e6ca0de94a60e2aef230c19150e8b6f75598e2b735ea2475853bb783f47b59f7.
- Ergebnis: 0c741fa6febe7e838649060d3b50d9db6ae60f87bc52ade26d56b650d9aa3f90.
- Alle neun Dateien zusammen, bisherige FullName|Length|SHA256-Systematik:
  AFFDCC8882D5F566D716F8D06394AD7D95D408FB45DC1313805DF0AC2CCC25D7.

Freigabe verbraucht; nach genau diesem Versuch keine weitere native Ausführung.
Das Ergebnis belegt den eigenen Ownerabschluss ohne globale CIM-Abhängigkeit,
noch keine private Jobzuordnung oder Scrapersicherheit.

### Anschließende Entwicklung ohne weitere OS-Ausführung

Neuer kleiner isolierter Einstieg WorkerJobProofMain nutzt die vorhandenen
WorkerJobOwner/WorkerJournal, SimpleWorkerNative, Win64ProcessKernelCalls und
SyntheticWorkerMain. Keine neue Owner-/Custodianhierarchie, kein Hintergrunddienst,
Broker oder produktiver Scraperanschluss. Keine bestehenden Laufzeitquellen geändert.
Neu sind ausschließlich WorkerJobProof.java, WorkerJobProofMain.java,
WorkerJobProofTest.java, Start-WorkerJobProof.ps1 und WorkerJobProof.Check.ps1.
TESTING.md und ROADMAP.md nur durch diesen Nachtrag ergänzt.

Vorgesehener separater realer Messumfang:
- eine äußere gehaltene Owner-JVM; erst nach deren nativer Identität eine gebundene
  Startdatei, davor kein JNA-/Job-/Workerstart;
- genau ein synthetischer SERIES-Request 900001 an genau einen Worker, Ergebnis
  ausschließlich synthetic=true, keine tatsächliche Import-/Scraperfunktion;
- genau eine frische private Jobgruppe über den bestehenden nativen Adapter,
  JOB_LIST bei CreateProcessW, CREATE_SUSPENDED/EXTENDED/UNICODE_ENV/NO_WINDOW,
  bestätigte native Identität und Jobmitgliedschaft vor Resume, kein Fallback;
- native Joblimits mit kill-on-close und ohne Breakaway, bestehende Kernledger,
  tatsächlicher Worker-Exitcode 0, gesamtes Jobzuordnungstotal exakt 1, Gruppe leer,
  Channel-Ende/EOFs, Pipe-/Thread-/Prozess-/Job-/Puffer- und Tempabschluss;
- separater äußerer Owner-Exit-/EOF-/Closebeleg, kein Erfolg allein aus EOF;
- gemeinsamer persistenter import-slot über ALLE Kennungen dieses synthetischen
  Profils. Pending/Quarantäne verhindert neue Jobs auch bei anderer Versuchkennung;
  keine Reset-/Recoveryfunktion und keine automatische Markerbereinigung;
- unbekannter Abschluss hält ursprüngliche Ownerreferenz/JVM passiv offen;
  kein PID-Kill, keine Terminierung außerhalb der eigenen Jobfähigkeit.

Fristen: eigene Startberechtigung spätestens 12 s nach Main-Eintritt beendet,
zusätzlich Kern-Startbudget 10 s; Operationsbudget 15 s, Cleanup 8 s. Main wartet
höchstens bis zu seiner 37-s-Gesamtgrenze auf den normalen Abschluss und hält bei
Ungewissheit weiter passiv. Äußerer Harness hat ein gemeinsames 45-s-Budget.
Auch nach verzögerter DLL-Vorbereitung darf keine späte Worker-Ausführung beginnen.
Ein hängender nativer Aufruf wird nicht als zwangsweise abbrechbar behauptet.
Ergebnis-/Fehlerdateien sind begrenzt; feste Phase, begrenzter Typ, numerischer
Kernelcode statt Messages/Stacks. Startfehler des vorhandenen Adapters werden
zusätzlich aus startFailure erfasst, ohne dessen Sicherheitsvertrag zu ändern.

Eigener Scope WORKER_JOB_PROOF_V1, eigenes Manifest, separate APPROVED-/CONSUMED-
Präfixe und neues Verzeichnis. Hashplan bindet Java/VM/Release/kernel32, das bereits
vorhandene gepinnte JNA-5.17-JAR, drei isolierte Klassenverzeichnisse und zwei feste
Skripte. Der synthetische Worker erhält nur workerOwner-Klassen, keine JNA-, Spring-,
Selenium-, Browser- oder DB-Abhängigkeit. Native Adapter nur im Owner. JNA-Dispatch-
Modullebenszeit endet mit dem nachzuweisenden Ownerende, kein riskantes Unload unter
laufenden Aufrufen. Private temporäre JNA-Dateien sind keine Manga-Daten.
Ungeprüft bleiben echte verschachtelte Jobs und Hostrestriktionen: keine Rechte-
oder Breakawayausnahme bei Ablehnung. Keine globale Konsolenprovenienz behauptet.

### Tatsächlich ausgeführte anschließende Prüfungen

Finaler Offline-Aufruf einschließlich identischem vorherigem --dry-run:

```powershell
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun --dry-run
.\gradlew.bat --offline --no-daemon compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun ownerProbeUnitTest --rerun
```

Taskplan nur Compile und isolierte JDK-/Fake-Testsource-Sets, keine Spring-/DB- oder
JavaExec-Aufgabe. **BUILD SUCCESSFUL; 132 Javafälle bestanden, 0 Fehler/übersprungen**:
71 bisherige Worker, 34 Owner, 27 neue Job-Proof-Tuple-/Namespace-/Ergebnis-/Datei- und
Diagnosefälle. Hashabgleich liest ausschließlich kompilierte Bytes und lokale Dateien;
kein nativer Lader aufgerufen. Java und PowerShell berechnen unabhängig identischen
Fingerabdruck. Beim Auslesen des XML zunächst XmlElement statt InnerText verwendet;
Prüfskript korrigiert, kein tatsächlicher Artefakthashunterschied.

**227 PowerShellfälle bestanden**: 27 neue Job-Proof-Fake-/Statikfälle,
44 Completion, 78 bisherige Provenienz, 48 SC, 24 Outer-STOP, 6 OwnedObservation.
Neue Fälle prüfen tatsächlich aus dem AST entnommene Akzeptanzbedingungen und
CreateNew-/Flush-Dateischreibport sowie kalten Einstieg ohne expliziten Modus.
Eine Parameterdeklaration beim AST-Extrahieren anfangs ausgelassen; Testhilfe
korrigiert und grün wiederholt. Das war ein Testhilfefehler, kein nativer Versuch.
Syntaxfehler im neuen Testskript während der Entwicklung ebenfalls vor Ausführung
korrigiert. Falsch angenommene Dateipfade für zwei ältere Skripte korrigiert und
die tatsächlichen Skripte erfolgreich ausgeführt. Keine dauerhaft roten Tests.
Diese Prüfungen ersetzen NICHT die noch fehlende reale Job-/Workerabnahme.
Keine 913er-/Spring-/DB-/DOM-Vollsuite neu ausgeführt, keine Assertions abgeschwächt.

### Nächster gebundener Kandidat, ausdrücklich noch nicht ausgeführt

- Kennung: b6f62923-d20b-4873-ac12-154d2bd1d3ed.
- Profil: WORKER_JOB_PROOF_V1.
- Artefakt-SHA256: 618379385093568e7e6a597b5d0e5c02154b43c458e1ac27c7421ce44e9f6bce.
- Manifest-SHA256: e6a76ecd072ff58a709403fe8e5f41b7bb0c0e24d65514dcf33ecb0b9e626491.
- Verzeichnis: .gradle/t02-worker-job-proof/b6f62923-d20b-4873-ac12-154d2bd1d3ed.
- Nur manifest.txt angelegt. ValidateOnly erfolgreich: keine Approval-Datei,
  kein Verbrauch, keine Nativecalls, null Owner/Worker/Jobs gestartet.

Benötigte neue, separate Freigabe: genau eine isolierte Owner-JVM, genau ein
synthetischer Worker und genau eine private Windows-Jobgruppe dieses Artefakts;
notwendige native Laden-/Identitäts-/Mitgliedschafts-/Resume-/End-/Cleanupoperationen
einschließlich ausschließlich eigener Jobterminierung im Fehlerpfad. Atomarer
Einmalverbrauch vor Start, keine Wiederholung. Kein Browser, Scraper, DB, Anwendung,
Netzwerk oder historische Bereinigung. Die erfolgreiche Ownerfreigabe berechtigt
zu keiner dieser zusätzlichen Operationen. Bis zur neuen Freigabe keine Ausführung.

Frühere 152 geschützte Evidenz-/Manifestdateien und die neun neuen Ownerdateien
unverändert nachgeprüft. Ausgeführter Ownerartefakthash weiterhin 7d90ec26.
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9,
Builddatei 4bbaca691a25a6240e03ac34f7d2e32a2afed15473a8f51cb744832fe23ac466.
Keine Installation, Gitoperation, Datenbank-/Anwendungs-/Browser-/Scraperausführung,
keine aktive historische Bereinigung oder Änderung bestehender Testguards.
Neue Job-Proof-Quellen bleiben in isolierten Source Sets; Manual-JAR unverändert.
Vorherige Dokumentbytes erhalten. Statische Whitespace-/Konfliktprüfung statt
verbotenem Git-Diff. T02 und produktive Scraperintegration weiterhin offen:
realer Job-/Workerbeleg, Browsernachfahren, Timeout-/Shutdownfehler, Profile/
Downloads, SSRF-/Netzwerkgrenzen und erst gesondert genehmigte Integration folgen.

## Nachtrag 30.10.2026 – Einzelversuch b6f62923: Quarantäne und Diagnosekorrekturen

### Tatsächlich ausgeführter, anschließend gesperrter OS-Versuch

Explizite Freigabe für genau einen synthetischen Owner/Worker/privaten Job.
Branch per HEAD-Datei, frisches Manifestverzeichnis, Java-/JNA-Pins, alle gebundenen
Artefakte, Overrides, dauerhafte Importsperre und Manual-JAR erneut geprüft.
ValidateOnly lieferte unverändert Artefakt
618379385093568e7e6a597b5d0e5c02154b43c458e1ac27c7421ce44e9f6bce und
Manifest e6a76ecd072ff58a709403fe8e5f41b7bb0c0e24d65514dcf33ecb0b9e626491.
Neue Approval-Datei erst danach erstellt. Der vorgeprüfte Launcher hat vor
NativeInit und Start CreateNew/Flush(true)/Hashbindung des Verbrauchsmarkers
durchgeführt. Genau ein Aufruf von ExecuteOnce, keine Wiederholung.

**Ergebnis: PARTIAL/STOP, QUARANTINED. Kein erfolgreicher Jobabschluss.**

| Beleg | Tatsächlich erhoben |
|---|---|
| Versuch | b6f62923-d20b-4873-ac12-154d2bd1d3ed / WORKER_JOB_PROOF_V1 |
| äußere PowerShell | ObserverPid=22308, ursprüngliche Toolsession 86300 |
| Owner | PID 15044, native Geburt 134378485858303646, Java-21-Image bestätigt |
| Ownergeburt UTC | 2026-10-30T15:43:05.8303646Z |
| synthetischer Worker | PID 42196, native Geburt 134378485914679271 |
| Workergeburt UTC | 2026-10-30T15:43:11.4679271Z |
| Importticket | cf728f97-7fbc-4ec6-8220-a27d9734f078 |
| Factoryaufrufe | 1 |
| Jobabschluss / Workerexit | GroupEnded=false, WorkerExit=UNKNOWN |
| Ressourcen / Kanal | ResourcesClosed=false, ChannelEnded=false |
| Jobtotal | AssignedTotal=0: KEIN Beweis einer leeren/nie benutzten Gruppe; kein akzeptierter Snapshot gespeichert |
| Admission | pending und quarantine vorhanden, JournalClear=false |
| Owner-Endbeobachtung | Ended=false, ExitObserved=false |
| eigene äußere EOF-/Closebelege | beide EOFs=false, StreamsClosed=false, OwnerHandleClosed=false |
| äußeres Budget | 45027 ms; kein erneutes Budget oder Cleanupversuch danach |

Das rohe C#-OwnerEnd enthält in der historischen result.json zusätzlich ExitCode=0.
**Wegen ExitObserved=false ist das ausschließlich ein Defaultfeld und KEIN
erhobener Exitcode.** Unveränderliche Evidenz nicht nachträglich korrigiert.
Der neue Bericht normalisiert in diesem Fall auf null, mit drei Regressionstests.
Ebenso beweist ChannelEnded=false hier keine bestimmte I/O-Ursache: der alte
Owner setzt sein Ergebnisfeld erst nach erfolgreicher Gruppenbereinigung.

Die Workeridentität wurde im eingefrorenen Ablauf erst nach erfolgreicher nativer
Start-/Mitgliedschaftsprüfung gespeichert. Damit wurde dieser Pfad erreicht;
das ist kein vollständiger Gruppenend-/Exit-/Ressourcenbeleg. Keine unabhängige
globale Prozess-/Konsolenprovenienz erhoben. Kein Browser-/Scraper-/DB-/Appstart.

Vorprüfzeit UTC 15:43:03.6501531Z; Verbrauchsdatei LastWriteUtc 15:43:04.5100870Z;
job-result.txt 15:43:11.7067148Z; result.json 15:43:49.5482104Z. Dateizeiten
sind keine nativen Exitzeiten. Frühes inneres STOP kurz nach Workerstart;
die äußere Frist lief erst anschließend ohne bestätigtes Ownerende aus.

Der Launcher meldete WORKER_JOB_RETAINED_SESSION_OPEN. Ursprüngliche Referenzen
bleiben in dieser Sitzung; keine nachträgliche native Lebenszustandsabfrage,
kein Ctrl+C, Kill, Jobzugriff oder Cleanup nach Ablauf. Lebenszustände danach
nicht erneut erhoben. Das ist ein REALER unbestätigter Importjob, ausdrücklich
keine Ausnahme für rein historische Beobachtersitzungen. Andere Versuchkennungen
dürfen die gemeinsame Sperre nicht umgehen. Kein neuer Kandidat angelegt.

### Evidenz- und Artefakterhalt

SHA256 approval.txt 12032e4695d8ba53122a440311a845ba65df02ca8b82fa007def13db44cd33e;
consumed 58b3480b9c832be1ac9053b56113e1e8444366e4e44cafb16fd50bd0ec485bfe;
job-result.txt a2f2000b0582d14c57284dbb572586cdc6926999bff818de34e223a6b0aab20c;
result.json aed992547d3d2a03638af79c62d4c10c4961ba2aa24138f3a36ca8f6866634fe.
Vor Neukompilierung 156 ursprüngliche Klassen-/Skript-/Quellcodedateien in
artifact-snapshot gesichert, CreateNew und identische Quell-/Zielhashes geprüft.
Ursprünglichen Artefakthash 61837938 aus archivierten Klassen/Skripten und
unveränderten festen Runtime-Dateien unabhängig rekonstruiert: exakt identisch.
163 feste Snapshot-/Versuchsdateien aggregiert, FullName|Length|SHA256:
727ABF21ABB3F218BFC7080C36190961521685A916F41F987CABDD7790B7D09B.
Laufende JDK-Perfdata-/Temporärdateien ausdrücklich NICHT als unveränderliche
Evidenz behandelt; eine gesperrte hsperfdata-Datei wurde nicht geöffnet/umgangen.
Keine solchen Live-Dateien gelöscht oder korrigiert.

pending SHA256 5c345dc37876ac72ca9b9a64564f6ffb1f604e9de3e1f3d7a18d7617591e086f;
quarantine 5ba83dc787a930aa23431b6b5c0948a9536777f0c04d6e89c0f4e6fa05286fff.
Beide unverändert. Tatsächlichen reinen Slot-Prüfblock separat ohne Start
ausgewertet: JOB_PRIOR_IMPORT_UNCONFIRMED; Marker vor/nach hashgleich.

### Gesicherte Diagnosebefunde, Hypothesen und Korrekturen

Nachgewiesen: alte Run-/Cleanup-Catches verwerfen Details. Der Harness beobachtet
Kernel-Exceptions, aber nicht alle internen Prüfablehnungen oder I/O-Fehler.
Weder job-failure.txt noch job-native-failure.txt entstand. Der konkrete früheste
Windows-/I/O-Abbruchgrund bleibt deshalb unbewiesen. Mögliche Pfade sind ein
unbestätigter Session-/Pipezustand oder eine verworfene instabile Gruppenbeobachtung;
kein Rechtedefekt, kein konkreter JNA-Fehler und kein bestimmter Pipefehler behauptet.
Keine Lockerung bestehender Snapshot-/Identitätsassertions und keine neuen Retries.

Implementierte rein passive Diagnose: WorkerJobOwner bewahrt Phase, maximal acht
Fehlerkategorien, begrenzten Exceptiontyp und numerischen Kernelcode sowie letzte
bereits erhobene Beobachtung. Keine Exceptionmessages, Stacks oder Payloads.
Sessiondiagnostik liest nur gecachte Flags/Zähler: ausstehende Nativecalls,
Ungewissheit, Methode, Pipebenutzer, Bytezähler, getrennte EOFs, Reader-/Protokoll-
zustand und letzter numerischer I/O-Code. Keine neuen Nativeabfragen oder Freigaben
durch diese Diagnose. Nicht atomare Diagnosewerte sind kein Sicherheitsnachweis.
Künftiger Harness schreibt job-diagnostic.txt auch bei intern abgefangenem Fehler.

Zusätzlich reproduzierbarer separater Cleanupfehler: flaches Files.delete(temp)
scheitert bei zurückgebliebenem leerem hsperfdata_<user>-Unterverzeichnis. JDK-
Perfdata im realen privaten TEMP wurde als Dateibestand beobachtet. Ob dies den
konkreten inneren STOP verursacht hat, ist NICHT belegt; GroupEnded=false spricht
für einen früheren Abbruch. Neuer WorkerTemporaryDirectory behandelt ausschließlich
dieses leere Unterverzeichnis nach bestätigtem Gruppenende. Keine rekursive
Löschung, keine PID-Dateien oder fremden Inhalte, keine Bereinigung des Liveversuchs.
Ursprünglich selbst erzeugter kanonischer Privatpfad und exakte Dateierstellungszeit
werden geprüft, fileKey zusätzlich sofern vorhanden. Das Windows-JDK lieferte in
den Tests null für fileKey; die zunächst unzutreffende Pflichtannahme wurde
korrigiert. Kein nativer Prozessbesitz wird aus diesen Dateimetadaten abgeleitet.
Nichtleere/unbekannte Inhalte und unbestätigtes Gruppenende bleiben ablehnend.

### Tatsächlich ausgeführte anschließende datenbankfreie Tests

Jeder Gradle-Aufruf ausschließlich --offline --no-daemon nach identischem
--dry-run des gleichen Plans: compileWorkerOwnerNativeJava workerOwnerUnitTest
--rerun ownerProbeUnitTest --rerun. Native Quellen nur kompiliert; keine zweite
Windows-Probe. Anfangs ein falscher Test-Zählertyp und die fileKey-Annahme rot,
gezielt korrigiert und vollständig erneut geprüft; keine Assertion abgeschwächt.

Final **BUILD SUCCESSFUL; 142 Javafälle bestanden, 0 Fehler/übersprungen**:
76 Workerowner/-wire/-import, 27 Job-Proof, 5 Tempverzeichnis, 34 Owner-Probe.
Die fünf neuen Diagnosefälle sichern unveränderte Admission, gespeicherte
statt neuer Kernelabfragen, I/O-Abbruchphase ohne Rohtexte, numerische
Kontrollfehler und verworfene instabile Beobachtung. Ein Fake reproduziert
das reale Ergebnismuster mit unbekannter Session; kein Beweis derselben Ursache.
Fünf Dateifixturetests prüfen leeres TEMP, leeres Perfdata-Verzeichnis,
Erhalt von Perfdateien/unbekannten Dateien und Ablehnung relativer Pfade.

**233 PowerShellfälle bestanden**: Job-Proof 33, Completion 44, Provenienz 78,
SC 48, Outer-STOP 24, OwnedObservation 6. Sechs neue Fälle: tatsächliche
Report-AST mit unbekanntem/bestätigtem/fehlendem Exit und actual Slot-Guard
gegen klare/pending/quarantine-Dateifixtures. Keine nativen Aufrufe.
Keine DB-/Spring-/913er-/DOM-Vollsuite wiederholt.

### Nächster notwendiger Schritt: bestehende Quarantäne, kein weiterer Jobstart

Status BLOCKED für weitere reale Importversuche. Zuerst gesonderte, eng begrenzte
Freigabe zur identitätsgebundenen Bestandsprüfung und gegebenenfalls Bereinigung
DIESES Versuchs erforderlich. Vorgeschlagener Umfang:
1. Nur Owner 15044 / native Geburt 134378485858303646 und Worker 42196 /
   native Geburt 134378485914679271 über gehaltene Prüfhandles, exakte native
   Erstellungszeiten und festes Java-Image abgleichen. Kein PID-only-Eigentum.
2. Ursprüngliche Toolsession 86300 nur über ihre tatsächliche Toolreferenz
   prüfen. Keine anderen/historischen Sitzungen verändern; kein bloßer PID-Kill.
3. Der eingefrorene Owner besitzt nach STOP KEINEN kooperativen Cleanupkanal;
   Quarantäne/Referenzen werden nur gehalten. Quellcodeänderungen können ihn
   nicht nachträglich umprogrammieren. Daher würde eine Beendigung der eindeutig
   identifizierten eigenen Owner-JVM gegebenenfalls eine ausdrücklich erlaubte
   erzwungene Beendigung über den identitätsgeprüften Prozesshandle benötigen.
   Nicht stillschweigend als geordneten Shutdown bezeichnen. Nur dieser Owner;
   keine direkte Terminierung anhand einer Worker-PID. Alternative: Eigentümer-
   entscheidung für Windows-Neustart statt gezielter Prozessbereinigung.
4. Falls genehmigt: Kill-on-job-close nur aus dem eingefrorenen privaten,
   nicht vererbten Jobvertrag ableiten; tatsächliche Owner-/Workerenden und
   Exitcodes separat nachweisen. Fehlende direkte Jobleer-/Ressourcennachweise
   ausdrücklich benennen; niemals aus bloßem Prozessnamen/EOF schließen.
5. Sicheren Abschlussweg der ursprünglichen Toolreferenz prüfen; falls nicht
   verfügbar oder Identität unklar, STOP statt blindem Kill. Sämtliche neuen
   Prüfhandles schließen und separate Bereinigungsevidenz schreiben.
6. pending/quarantine NICHT automatisch löschen. Erst bestätigten Abschluss
   bewerten und eine ausdrückliche Freigabe für genau dieses Ticket dokumentieren,
   bevor ein neuer Jobversuch überhaupt geplant/ausgeführt werden darf.

Hierfür keine neue ausführbare Approval-Datei, kein Freigabeverbrauch, keine
native Bestandsaufnahme oder Bereinigung vorgenommen. Keine Übertragung dieser
einmaligen Versuchsgenehmigung auf eine zweite Probe oder Zwangsbeendigung.

Geändert: WorkerJobOwner.java, WorkerJobProof.java, SimpleWorkerNative.java,
WorkerNativePipes.java, WorkerJobProofMain.java, Start-WorkerJobProof.ps1,
WorkerOwnerTest.java, WorkerJobProof.Check.ps1. Neu: WorkerTemporaryDirectory.java
und WorkerTemporaryDirectoryTest.java. TESTING.md/ROADMAP.md nur neue Nachträge.
Vorherige Dokumentbytes, historische Evidenzen, DB-Guards, Builddatei und
Manual-JAR erhalten. Keine Gitoperation, Installation, DB, Anwendung, Browser,
Selenium oder echtes Scraping. Scraperabsicherung bleibt ohne Einsatzfreigabe.

## Nachtrag 30.10.2026 – Identitätsgebundene Bereinigung b6f62923 abgeschlossen, Sperre erhalten

### Umfang und tatsächlich bestätigte Endzustände

Neue ausdrückliche Eigentümerfreigabe ausschließlich für Untersuchung/Bereinigung
des vorhandenen Versuchs. Kein neuer Owner, Worker oder Job angefordert.
Vor jeder Aktion gepinnte ursprüngliche Ergebnis-/Verbrauchsdateien, alle 163
eingefrorenen Snapshot-/Versuchsdateien (727ABF21...), Quarantänemarker,
Inspection-Quellcode, Branch-HEAD und Manual-JAR erneut geprüft.

Die ursprüngliche Toolsession 86300 war über ihre originale Toolreferenz noch
erreichbar. Ihre intern gehaltenen .NET-Objekte lassen sich über diesen nicht
interaktiven Toolkanal nicht auswerten. Deshalb zusätzliche eigene Prüfhandles
auf die zwei dokumentierten Prozessinstanzen geöffnet, anschließend native PID,
exakte Erstellungszeit und soweit verfügbar Image geprüft. Keine Besitzannahme
allein aus PID/Name/Parent-PID. Nur Owner erhielt potenzielle Terminate-Rechte;
Workerhandles waren ausschließlich lesend/synchronisierend.

Erster Zugriff in der Sandbox scheiterte vor Identifikation/Beendigung. Verbesserte
Fehlererfassung bewies OpenProcess-Fehler 5 bereits bei QUERY_LIMITED_INFORMATION.
Danach im freigegebenen Ausführungskontext außerhalb der Sandbox ausschließlich
lesend fortgesetzt; keine ACL-/Windows-Rechteänderung oder globale WMI-Abfrage.
Keine automatische Wiederholung eines synthetischen Experiments oder einer
gescheiterten Terminierung: insgesamt genau EIN tatsächlicher TerminateProcess.

| Beleg | Tatsächliches Ergebnis |
|---|---|
| Ownerinstanz | PID 15044 / native Geburt 134378485858303646 / festes Java-21-Image |
| Workerinstanz | PID 42196 / native Geburt 134378485914679271 |
| Worker vor Bereinigung | gehaltene Instanz bereits signalisiert, tatsächlicher Exitcode 0 |
| Worker-Image | aktuelle QueryFullProcessImageNameW fehlgeschlagen, nativer Fehler 31; historische Imageprüfung nicht als neue Messung ausgeben |
| Worker-Zuordnung | exakte native PID/Geburt auf gehaltenem Prüfhandle plus bestätigtes Ende, keine Zeittoleranz |
| Owner vor Bereinigung | lebend, ExitObserved=false; Image und native Geburt erneut exakt bestätigt |
| geordneter Ownerabschluss | eingefrorener STOP-Loop hat keinen kooperativen Abschlusskanal |
| erlaubte Zwangsbeendigung | genau einmal nur eigene Ownerinstanz, erneute Prüfung auf DEMSELBEN Handle direkt vor TerminateProcess |
| Owner nach Bereinigung | signalisierter Prozess, tatsächlicher Exitcode 1296826369 / 0x4D4C0001 |
| Worker nach Bereinigung | weiterhin tatsächlicher Exitcode 0, nicht direkt terminiert |
| neue Prüfhandles | beide mit nativ bestätigtem CloseHandle geschlossen |
| ursprüngliche Toolsession | per Ctrl+C über originale Referenz 86300 beendet; Tool meldete Abschluss, Exitcode 1 |
| neue Prozess-/Jobstarts | null |
| Quarantänefreigabe | keine |

Die ausdrückliche Inspektion 8f3eeaa2-2e90-44e4-9a82-448596cbb1ce bestätigte
den bereits beendeten Worker um 16:20:07 UTC. Erfolgreicher Cleanup-Lauf
7ac770d5-b743-4d1c-b7cc-bdd957bed8f6 ab 16:20:46.4534209Z UTC, Ergebnis
OWN_PROCESS_ENDS_CONFIRMED. Zuvor fehlgeschlagene/lesende Untersuchungen separat
in 2f4798b4-, 3de5f29f-, 7fc1334c-Verzeichnissen dokumentiert, nicht überschrieben.
Erste Fehlerdiagnose hatte Win32-Code noch nicht unmittelbar in C# gesichert;
diesen Verlust behoben und nur bounded Typ-/HRESULT-/Win32-/feste Fehlercodes
ergänzt. Keine freien Exceptiontexte oder Umgebungswerte protokolliert.

### Messgrenze und unveränderte Quarantäne

Bestätigt sind die beiden ursprünglichen Prozessenden, tatsächliche Exitcodes,
Abschluss neuer Prüfhandles und Ende der ursprünglichen Toolsession. Kill-on-job-
close / privater nicht vererbter Job stammen aus dem eingefrorenen geprüften
Startvertrag. KEIN Jobhandle aus fremden Prozessen gesucht/dupliziert und kein
neuer direkter Jobleerstand gemessen. Ursprüngliche einzelne EOF-/Dispose-ACKs
wurden vor Ctrl+C nicht nachträglich ausgelesen. Prozessbeendigung/OS-Reklamation
nicht als regulär vollendeten internen Workerledger oder erfolgreichen Import
ausgeben. Der ursprüngliche PARTIAL/STOP-Bericht bleibt unverändert gültig.

pending und quarantine unverändert mit bisherigen Hashes 5C345DC3... / 5BA83DC7...;
keine Freigabe, Löschung, Verschiebung oder Umgehung durch neue Versuchkennung.
Keine andere/historische Sitzung angesprochen. Keine PID-only-Terminierung,
kein Worker-Kill, kein Shutdown fremder Prozesse und keine Datenbereinigung.

Neue separate Cleanup-Evidenz: .gradle/t02-worker-job-cleanup/ mit insgesamt
neun Dateien, aggregierter FullName|Length|SHA256-Hash
F50AAB2A132C1E35D05BF2EC32516BDDFEF60E9A4AA1BADC7FFF0DEF89DE827D.
Erfolgreiche result.json SHA256
9c1069f361d51a6aec9c096e369d5fa0be4ca4238a4424ee686e1b898dd07ed3.
Tatsächlich ausgeführtes Cleanupskript vor weiteren Änderungen archiviert,
SHA256 d544f71f3168ce8c1ae12455b931961e243020d92b9bf96b52effbd03b674d95.
original-session-close.json SHA256
58b1341071d2fcaa6a8d2e0be1c8baf3469bfb2c3969c64e0e262284e922ea8d.
Keine Wiederverwendung einer historischen Owner-/Job-Ausführungsfreigabe.

### Anschließende Korrekturen ohne neuen nativen Versuch

1. Nachgewiesene Windows-Eigenschaft: bei dem exakt identifizierten beendeten
   Worker liefert die Bildpfadabfrage Fehler 31, während GetProcessId,
   GetProcessTimes, WaitForSingleObject und GetExitCodeProcess funktionieren.
   Der bisherige Port verlangte das Image auch dann erneut und konnte so eine
   bekannte, beendete Instanz nicht bestätigen. Enger Fix: nur bei Fehler 31,
   vorher gemessenem Image, erneut exakt gleicher PID/Geburt auf demselben
   gehaltenen Handle, signalisiertem Ende UND erfolgreicher Exitcodeabfrage
   das ursprünglich gemessene Image erhalten. Kein Fallback für erste
   Identifikation, lebende Prozesse, fehlende Endbelege oder andere Fehler.
   Lebende Imageabweichungen und alle bisherigen strikten Assertions bleiben.
2. Im normalen Cleanup zuerst gehaltenes Root-Ende bestätigen, dann den ersten
   Gruppensnapshot nehmen. Der alte Ablauf konnte schon den normalen Root-Exit
   mitten zwischen Accounting-/PID-Abfragen als Instabilität erhalten. Reihenfolge
   korrigiert, KEINE Snapshot-Wiederholung oder Toleranz hinzugefügt. Anschließend
   instabile Beobachtung bleibt STOP. Verbliebene eigene Kinder weiterhin nur
   über private Jobfähigkeit behandeln. Gleiche gemeinsame Cleanupfrist.
3. Neuer fester Cleanup-Einstieg mit expliziter Moduswahl, unmittelbarem Win32-
   Fehlercapture, getrennten Identitäts-/End-/Imagebefunden, Vorher-/Nachher-
   Evidenz und Sperre gegen erneute aktive Bereinigung bei bereits dokumentierter
   Ownerterminierung. Kein allgemeiner PID-Kill-/Jobstartdienst.

Diese Befunde machen die frühere primäre STOP-Ursache NICHT nachträglich eindeutig:
im alten Joblauf fehlen die entscheidenden Diagnosewerte. Bildpfadproblem real
nachgewiesen; Snapshot-Rennen per Fake modelliert. Keine falsche Rückdatierung
neuer Diagnosen und kein behaupteter neuer Job-PASS.

### Tatsächlich ausgeführte datenbankfreie Prüfungen

Finaler Gradle-Lauf ausschließlich --offline --no-daemon, identischer --dry-run
zuvor erfolgreich: compileWorkerOwnerNativeJava workerOwnerUnitTest --rerun
ownerProbeUnitTest --rerun. Taskplan nur isolierte Kompilierung/JDK-Fakes;
keine Spring-Kontexte, Datenbank oder JavaExec-Probe. Ein lokaler Variablen-
Namenskonflikt zunächst bei Kompilierung erkannt, korrigiert und erneut geprüft.

BUILD SUCCESSFUL: **153 Javafälle, 0 Fehler, 0 übersprungen**. Zehn neue reine
JDK-Entscheidungsfälle zur gehaltenen beendeten Identität, ein neuer Ablauf-Fake
für Root-Ende vor Snapshot; alle 142 bisherigen Assertions unverändert erhalten.
**254 PowerShellfälle bestanden**: 233 bisherige plus 21 Cleanup-Fake-/Parse-/
C#-Compilefälle. Identitätsabweichungen, Bildpfad nur bei beendeter bekannter
Instanz, Replaybarriere und kalter Einstieg geprüft. C# nur kompiliert/reflektiert,
in diesen Tests KEINE nativen Methoden aufgerufen. Native Java-Verdrahtung
ausschließlich kompiliert; neuer Image-Fallback noch nicht in einem echten Joblauf
validiert. Keine Erweiterung der historischen JNA-Speicherfall-Ausnahme benötigt.
Keine DB-/Spring-/DOM-/913er-Vollsuite ausgeführt und keine neue Abhängigkeit.

Dateien dieses Auftrags: neu Invoke-ExistingWorkerCleanup.ps1,
ExistingWorkerCleanup.Check.ps1 und WorkerHeldIdentityTest.java; geändert
KernelCalls.java (rein experimenteller Port), Win64ProcessKernelCalls.java,
WorkerJobOwner.java und WorkerOwnerTest.java. TESTING.md/ROADMAP.md nur Nachträge.
Historische Evidenzen/Verbrauchsdateien, beide Sperrmarker, DB-Guards, Manual-JAR
(9AE1B14C...) und Builddatei (4BBACA69...) erhalten. Keine Gitoperation; statische
Whitespace-/Konfliktprüfung statt Git-Diff. Keine DB, Anwendung, Browser, Scraper,
Netzwerkoperation oder Installation.

### Nächste tatsächliche Freigabegrenze

Weitere reale Job-/Importversuche bleiben BLOCKED durch den unveränderten Slot.
Die aktuelle Freigabe erlaubt Cleanup, ausdrücklich KEINE Quarantäneaufhebung.
Erforderliche Eigentümerentscheidung: Genügen die nun bewiesenen Prozessenden,
neuen Handleabschlüsse, Original-Toolabschluss und OS-seitige Reklamation für eine
explizite Freigabe ausschließlich des Tickets cf728f97-7fbc-4ec6-8220-a27d9734f078?
Empfehlung: als dokumentierten erzwungenen Cleanup akzeptieren, historische STOP-
und Quarantäneevidenz vollständig erhalten; KEIN ursprünglicher Importerfolg.
Das ist eine explizite Verfahrensentscheidung, keine noch beobachtete lebende
Owner-/Workerinstanz. Direkte historische EOF-/Jobleer-/Ledger-ACKs sind nicht
rekonstruierbar und dürfen nicht erfunden werden. Keine pauschale Recoveryregel.
Ohne diese Entscheidung keinerlei Markeränderung oder neuer Jobkandidat. Danach
erst separaten frischen synthetischen Joblauf mit neuer Artefaktbindung vorbereiten
und ausdrücklich freigeben lassen. Browser-/SSRF-/Timeout-/Integrationsnachweise
bleiben danach eigenständig erforderlich; T02 noch nicht einsatzreif.

## Nachtrag 30.10.2026 – Einmalige administrative Freigabe cf728f97

### Autorisierung und strikt begrenzte Bedeutung

Der Projekteigentümer hat die administrative Aufhebung ausschließlich des Tickets
cf728f97-7fbc-4ec6-8220-a27d9734f078 ausdrücklich genehmigt. Grundlage sind die
bestätigten nativen Enden von Owner 15044 und Worker 42196 sowie der Abschluss der
originalen Toolsession 86300. Die verbleibende historische Ungewissheit wurde in
dieser Entscheidung ausdrücklich akzeptiert, nicht durch neue Messungen ersetzt.
Originalversuch b6f62923-d20b-4873-ac12-154d2bd1d3ed bleibt PARTIAL/STOP.
Ursprünglicher Jobleerstand und vollständige einzelne EOF-/Dispose-/Ledger-ACKs
sind NICHT rekonstruierbar. Administrative Freigabe ist weder Import-PASS noch
Erfolg der ursprünglichen Gruppenbereinigung und keine neue OS-Ausführungsfreigabe.

### Vorprüfung und tatsächlich vorgenommene Markeränderung

Neuer fester Einstieg Release-ExistingWorkerQuarantine.ps1, Modi ValidateOnly
und ApplyRelease ausdrücklich getrennt. Kein Ticketparameter, keine Prozess-
oder Job-API, keine Runtime-Recovery. WorkerJournal und seine Admissionregeln
unverändert. Readonly-Vorprüfung belegte:
- Branch per HEAD-Datei codex/t02-security-hardening;
- einziger Versuchsordner im gemeinsamen Worker-/Jobbereich b6f62923 und genau
  ein import-slot, darin ausschließlich pending und quarantine;
- pending exakt das genehmigte Ticket, Hash 5C345DC37876AC72CA9B9A64564F6FFB1F604E9DE3E1F3D7A18D7617591E086F;
- quarantine unverändert UNCONFIRMED, Hash 5BA83DC787A930AA23431B6B5C0948A9536777F0C04D6E89C0F4E6FA05286FFF;
- alle 163 eingefrorenen Versuchs-/Snapshotdateien hashgleich 727ABF21...;
- gepinnte Cleanup- und Original-Sessionabschlussdateien hashgleich 9C1069F3...
  und 58B13410..., exakte native Identitäten/Exitcodes/Closebelege geprüft;
- keine Hinweise im zugehörigen Registry-/Markerbestand auf ein anderes Ticket
  oder weitere dieser Sperre zugeordnete Versuche. Keine globale aktuelle
  Windows-Bestandsmessung behauptet; keine weiteren nativen Abfragen durchgeführt.

Nach erfolgreichem ValidateOnly genau ein realer ApplyRelease-Aufruf.
CreateNew und Flush(true) einer permanenten consumed-Datei VOR jeder Markeränderung;
durable decision.json mit ausdrücklicher Eigentümerentscheidung, Beleg-/Markerhashes,
Skripthash und den nicht rekonstruierbaren ursprünglichen Nachweisen.
Dann EIN gleichvolumiges Directory.Move des gesamten import-slot mit genau zwei
Markern in das feste ticketgebundene Archiv:

```text
.gradle/t02-worker-job-release/cf728f97-7fbc-4ec6-8220-a27d9734f078/markers/
```

Beide Marker bytegleich erhalten; nicht einzeln gelöscht oder umgeschrieben.
Der alte import-slot-Pfad ist danach nicht vorhanden. Kein leerer Slot automatisch
neu erzeugt; erst ein später separat genehmigter Owner kann seinen regulären
WorkerJournal-Konstruktor verwenden. Kein Zwischenzustand zwischen zwei einzelnen
Markerentfernungen: die Quarantäne bleibt bis zur einen Verzeichnisumbenennung
vorhanden. Source-/Archivpfade vorher absolut auf Workspace, Reparsefreiheit und
gleiches Laufwerk geprüft. Keine rekursive Datenbereinigung oder Shellverkettung.

Markerstreams während Belegvorbereitung gegen Schreiben gesperrt; vor Rename
geschlossen und Marker erneut geprüft. Ein synthetischer Dateisystemtest zeigte,
dass Windows das Directory-Rename bei offenen Kinderdateien trotz SHARE_DELETE
verweigert. Sandbox-TEMP verweigerte zusätzlich den getesteten Verzeichnis-Move;
Faketests daher im explizit schreibbaren Workspace unter quarantine-release-fakes.
Reale Marker blieben während dieser Entwicklungsfehler unangetastet. Kein Wechsel
auf erzwungene Löschung oder Lockerung der Ticketprüfungen. Der Einmalclaim
verhindert parallele Ausführung desselben Freigabewegs; reguläre Admission war
während der Vorbereitung durch die bestehenden Marker blockiert. Keine Isolation
gegen einen böswilligen anderen Prozess mit denselben Benutzerrechten behauptet.

Ergebnis um 2026-10-30T18:37:13.2081801Z UTC: ADMINISTRATIVELY_RELEASED,
MarkerPairArchived=true, SourceSlotAbsent=true, OriginalJobPass=false,
NewNativeExecutionApproved=false. Readonly-Wiederholungsprüfung danach:
RELEASE_ALREADY_USED. Keine zweite Apply-Ausführung oder automatische Wiederholung.
Fehlerfälle vor dem Move behalten beide Marker; ein verbrauchter Claim erlaubt
keine automatische Fortsetzung. Die Ausnahme kann kein nachfolgendes Ticket lösen.

### Evidenzen und Tests

Sechs neue administrative Evidenzdateien: consumed, decision.json, result.json,
executed-script.ps1 und die zwei archivierten Originalmarker. Aggregierter
FullName|Length|SHA256-Hash:
FFDCCB52F4A9C2748F66316EC0043C3DD70FB57A5205EBB3A1D8F0D9508DE048.
result.json SHA256 4973a5dcdfe817b839b19cad45502858525d3a2951d0456c6f49430dcebc0903.
decision.json SHA256 097b51a4b1cde19dc3ee28e450ea85256cd1bb1d44106d3ac636f536446d5490.
Ausgeführtes Skript vor Dokumentation bytegleich archiviert, SHA256
4675104079516f3d773ad01c103c4171e42ede44dcf595a2efd2867b06afeb15.
Alle historischen Versuch-/Cleanup-Evidenzen und verbrauchten OS-Freigaben
unverändert; eigene neue administrative Evidenz ausschließlich im obigen Archiv.

**289 PowerShellfälle tatsächlich erfolgreich ausgeführt:** 35 neue synthetische
Entscheidungs-/Dateisystemfälle, 33 Job-Proof, 44 Completion, 78 Provenienz,
48 SC, 24 Outer-STOP, 6 OwnedObservation und 21 Cleanup-Fake-/Compilefälle.
Neue Fälle prüfen insbesondere falsche Tickets/Identitäten, nicht bestätigte
End-/Exit-/Handle-/Sessionbelege, historische PASS-Umdeutung, veränderte/zusätzliche
Marker, Pfadwechsel, Movefehler mit verbleibender Sperre, Einmalverbrauch und
erneut belegten Slot eines ANDEREN synthetischen Tickets. Kein solches Ticket
wurde freigegeben. Faketests benutzen ausschließlich eigene synthetische
Dateisystemfixtures; keine Datenbank, Anwendung oder Prozess-/Job-API.
Keine bestehenden Assertions geändert oder abgeschwächt.

Keine neuen Java-/Gradle-Läufe in diesem rein administrativen Dateisystemauftrag.
Der vorherige 153er-Java-Prüfstand bleibt historischer Baseline-Nachweis.
Keine Java-/Anwendungscodeänderungen. Aktuell kompilierte Artefakte erneut per
unabhängiger Java-/PowerShell-Fingerprintgleichheit geprüft; keine Neukompilierung,
kein Download und keine Installation. Neue Dateien ausschließlich
Release-ExistingWorkerQuarantine.ps1 und WorkerQuarantineRelease.Check.ps1;
TESTING.md/ROADMAP.md nur neue Nachträge. Kein Git, DB, Browser, Scraper,
Anwendungsstart oder neuer Windows-Prozess-/Jobversuch.

### Nächster Schritt nur vorbereitet, keine native Ausführung

Nach erfolgreicher Freigabe neuen eigenständigen Kandidaten für den bereits
entwickelten synthetischen Worker-/Jobnachweis erzeugt:
- Kennung c21e147e-aa8d-4235-84d9-ff65c96f7e3b, Profil WORKER_JOB_PROOF_V1.
- Artefakt-SHA256 b66bfe3822bb2151b515254bf00ca531d07ac55816d7f54804dd8b369ffdbe85.
- Manifest-SHA256 82a072d0924476a3c82a50ed928a043bb9668110090c856e91a39fcf62c276f8.
- Nur .gradle/t02-worker-job-proof/c21e147e-aa8d-4235-84d9-ff65c96f7e3b/manifest.txt
  angelegt; keine Approval-/Verbrauchsdatei.
- ValidateOnly erfolgreich: Approved=false, Consumed=false, NativeCalls=false,
  OwnerStarts=0, WorkerStarts=0, JobCreates=0. Keine neue Prozessbestandsmessung.

Dieser Kandidat bindet die seit dem alten STOP entwickelten Diagnose-, Temp-,
Ended-Identity- und Root-/Snapshot-Reihenfolgekorrekturen. Ihre reale gemeinsame
Wirkung bleibt unbewiesen. Neue ausdrückliche Freigabe benötigt: genau eine
synthetische Owner-JVM, ein synthetischer Worker und eine private Jobgruppe
einschließlich eigener sicherer Cleanupoperationen; erneute Integritätsprüfung,
frischer atomarer Einmalverbrauch, gleiche Fristen/STOP-Regeln, kein Retry.
Keine Browser/Live-Scraper/DB/Anwendung/Netzwerk und keine historische Bereinigung.
Administrative Ticketfreigabe berechtigt zu KEINER dieser nativen Operationen.
Danach bleiben weitere Fehler-/Timeout-, Browser-/SSRF- und Integrationsnachweise
für tatsächliche Einsatzreife nötig. T02 und fachliche Prioritäten unverändert.

## Nachtrag – Synthetischer Jobversuch c21e147e

Einmalig nach erfolgreichem ValidateOnly und atomarem Verbrauch ausgeführt.
PARTIAL/STOP: Owner PID 36800, Worker PID 42168; Worker-Exitcode 0, Gruppe,
Ressourcen und Kanal geschlossen, Journal leer. Owner-Exitcode 78, beide
Owner-EOFs sowie Stream- und Handleabschluss bestätigt. Jobzuordnungstotal 2
bei gefordertem Wert 1; kein PASS. Stabiler leerer Snapshot active=0, total=2;
keine I/O- oder Nativefehler, beide Worker-EOFs bestätigt. Identität und Ursache
des zweiten zugeordneten Prozesses unbekannt; Konsolenbegleiter nur Hypothese.
Keine globale Prozess-/Konsolenprovenienz, Wiederholung oder weitere native
Ausführung. Vor Änderung der Erfolgskriterien Zuordnung und Auswirkungen klären.
Keine DB-, Browser-, Scraper-, Anwendungs-, Netzwerk- oder Gitoperationen.
## Nachtrag – Gruppennachweis statt fester historischer Prozesszahl

Der Versuch c21e147e bleibt PARTIAL/STOP; die zweite historische Identität und
ihre Ursache sind weiterhin unbekannt. Der Jobzähler total erfasst historische
Zuordnungen, nicht aktuell lebende Mitglieder. Für neue synthetische Versuche
muss total mindestens 1 sein; mehrere historische Zuordnungen sind zulässig.
Gleichzeitig bleiben native Jobzuordnung vor Resume, gehaltene Workeridentität,
stabiler vollständiger leerer Gruppensnapshot, tatsächlicher Workerexit 0,
Kanalende, Ressourcenabschluss, Journalfreigabe, Ownerende und EOFs erforderlich.
Unbekannte historische Identitäten werden dadurch weder identifiziert noch deren
globale Provenienz bewiesen. Bei unbestätigtem Gruppen-/Ressourcenende bleibt
Quarantäne bestehen. Kein erfolgreicher Nachweis allein aus einem Zähler.

154 Java-Faketests bestanden, 0 Fehler/übersprungen; 34 PowerShell-Fälle bestanden.
Gradle ausschließlich offline nach identischem Dry-Run; keine DB oder native
Prozess-/Jobausführung. Die konkrete Windows-Wirkung ist noch nicht belegt.
Neuer Kandidat 7af1c3c7-0eaa-4ff6-a218-33950de4af14, Artefakt-SHA256
544ef1fd16006fc17172356328993f5b6fdbc53132ef1c896a9ccaa8ef8b9ad0;
Manifest-SHA256 9b80a24c6c64b24ef7396712fc2d34f030183e36a23c66e470db6a59a17c1cf8.
Nur Manifest, ValidateOnly erfolgreich, keine Approval-/Verbrauchsdatei.
Separat freizugeben: genau ein synthetischer Owner, ein synthetischer Worker,
eine private Jobgruppe und ausschließlich eigene Cleanupoperationen. Danach sind
insbesondere reale Timeout-/Nachfahrenfälle und eine separat freigegebene
Firefox-/Geckodriver-Abnahme vor Produktintegration erforderlich.
## Nachtrag – Synthetischer Windows-Jobversuch 7af1c3c7

Nach erfolgreicher erneuter Manifest-/Artefaktprüfung wurde die ausdrücklich
freigegebene Einmalberechtigung atomar vor dem Start verbraucht. Genau ein
synthetischer Owner und Worker: WORKER_JOB_PROOF_PASS. Owner PID 19268,
Worker PID 41588; beide tatsächlichen Exitcodes 0. Stabile vollständige
Gruppenbeobachtung active=0, total=2; Kanal und Ressourcen geschlossen,
Journal leer, Owner-stdout/-stderr EOF und Handleabschluss bestätigt.
Gesamtzeit 8536 ms, keine unbestätigten eigenen Ressourcen. Historische
Identitäten zusätzlicher Zuordnungen und globale Konsolenprovenienz nicht
gemessen. Der frühere Versuch c21e147e bleibt PARTIAL/STOP.

Keine Wiederholung, kein Browser, Scraper, Datenbank-, Anwendungs- oder
Netzwerkzugriff, keine Gitoperation. Manual-JAR hashgleich 9AE1B14C...
Reale Timeout-/Nachfahrenfälle, Browser-/Geckodriver-Verhalten, SSRF-Schutz
und Integration beider Scraper bleiben offen. WorkerImportRunner hat derzeit
nur eine Scrapers-Schnittstelle; SyntheticWorkerMain verwendet Doubles.
Dieser Erfolg belegt deshalb keine einsatzreife Scraperintegration.
## Nachtrag 30.10.2026 – Produktive Worker-Anbindung und SSRF-Grenze (ohne OS-Abnahme)

Die Freigabe zur Implementierung betrifft Quellcode und datenbankfreie Prüfungen.
AGENTS.md verbietet diese Integration nicht; Verbote realer Prozess-/Browser-/
Netzwerkstarts sind keine Verbote entsprechender Quellcodeänderungen. Die früher
berichtete Integrationslücke war tatsächlich vorhanden, kein belegter Regelkonflikt.

Nun implementiert:
- LiveScraperGateway delegiert beide Operationen an denselben ProductionScraperClient.
  Keine direkten Selenium-Aufrufe und kein Fallback. Der Client verwendet WorkerJobOwner,
  WorkerJournal, den vorhandenen nativen Job-/Stdioadapter und WorkerImportRunner im Kind.
- Separater Gradle-Source-Set productionOwner enthält ausschließlich wiederverwendete
  Job-/IPC-Komponenten; keine synthetischen Startmains. liveScraperWorkerJar baut
  build/libs/mangalib-scraper-worker.jar mit beiden bisherigen Extraktoren, Selenium
  und begrenztem Binärprotokoll, ohne Spring, JPA, DB-Konfiguration oder Geschäftsentities.
  Das normale bootJar enthält Owner/JNA 5.17.0, aber keine Selenium-JARs/Extraktoren.
  JNA 5.17.0 wird aus dem bereits vorhandenen hashgeprüften Cache wiederverwendet,
  nicht installiert oder heruntergeladen. Selenium bleibt 4.18.1.
- Konstruktion/Boot-Kontext starten keinen Worker. Fehlende Konfiguration führt zu
  kontrollierter Ablehnung. Im Spring-Profil test ist der echte Client zusätzlich
  deaktiviert; positive HTTP-Vertragstests ersetzen ihn durch ein Mock.
- Gemeinsamer persistenter Slot: Benutzerverzeichnis/.mangalib/scraper/import-slot.
  pending/quarantine blockieren auch nach Neustart. Keine Recovery-API. Der produktive
  Slot ist vom historischen Versuchsjournal getrennt; historische Sperren/Evidenzen
  werden nicht entfernt oder als erfolgreicher Import umgedeutet.
- Vor jedem Workerstart erneut kanonische Artefaktpfade und SHA-256 prüfen. Start über
  denselben atomaren Jobzuordnungspfad vor Resume, geerbte Umgebung ausschließlich
  SystemRoot/TEMP/TMP. Keine Datenbankpasswörter oder JVM-Optionsvariablen im Worker.
- Kandidatenfristen des bestehenden Owners: Start 30 s, Reihe 60 min, Band 120 s,
  Cleanup 15 s. Firefox-Seitenladen 40 s, Skripte 15 s. Root-/Nachfahrenende,
  Gruppenleerstand, Exitcode, getrennte Kanalenden und Ressourcenbilanz bleiben
  verpflichtend. Unsicherheit führt zur bisherigen Quarantäne, nicht zu einem Retry.
- Ein privater HTTPS-CONNECT-Proxy pro Worker erlaubt ausschließlich Port 443 auf
  www.manga-passion.de und media.manga-passion.de. Alle DNS-Antworten müssen öffentlich
  sein; connect verwendet die geprüften IPs ohne erneute Namensauflösung. Redirects
  und neue Unterressourcenziele benötigen ebenfalls erlaubte CONNECT-Ziele. Kein HTTP-
  oder Direct-Fallback. Firefox wird einschließlich Loopback über diesen Proxy geführt;
  WebRTC, HTTP/3, WebTransport, DoH, Prefetch, Serviceworker und externe Protokollhandler
  werden eingeschränkt/deaktiviert. Frisches Selenium-Profil, feste Gecko-/Firefoxpfade,
  keine Selenium-Manager-Downloads. TLS-Zertifikatsprüfung bleibt aktiv.
- Transportbudgets pro Worker: 8 parallele Tunnel, 256 angenommene Verbindungen,
  8 KiB Header, 64 MiB bidirektionale Tunnelbytes, Connect insgesamt 10 s je Tunnel,
  Lese-Idlelimit 15 s. Wirelimits unverändert (u.a. Ergebnis maximal 32 MiB).
- stdout gehört ausschließlich dem Binärprotokoll; Legacy-Scraper-/Driverausgaben
  werden verworfen. Fehler beim eigentlichen Laden liefern keine erfolgreiche Teilmap.
  Erfolgreiche Importfelder, String-/Null-Werte und JSON-Schlüssel bleiben erhalten.
- Nur nach nativ bestätigtem Gruppenende: begrenzte Bereinigung des selbst erzeugten
  Worker-Tempbaums. Vorher gesamter Baum auf kanonische Pfade/Links prüfen; höchstens
  10000 Einträge/256 MiB. Kein Löschen fremder Pfade. Unbestätigter Abschluss sperrt.
  Der bisherige strengere synthetische Temp-Cleanup bleibt unverändert verfügbar.

### Konfiguration für eine erst separat freizugebende reale Abnahme

Erforderliche JVM-Systemproperties (keine Passwörter): jna.nosys=true sowie
mangalib.scraper.worker.path / worker.sha256, gecko.path / gecko.sha256 und
firefox.path / firefox.sha256, jeweils mit Präfix mangalib.scraper.
Absolute kanonische Pfade; Hashes 64-stellig klein geschrieben. Der Java-21-Pin des
bestehenden nativen Launchers bleibt verbindlich. Keine beliebigen Mainklassen,
Workerargumente, zusätzlichen Classpaths oder geerbten Credentials.

Nächste reale Abnahme benötigt neue ausdrückliche Freigabe: isolierte Ausführung
beider Importarten sequenziell über den produktiven Client, feste vorher geprüfte
Manga-Passion-Indizes/Artefakte, privater Job und notwendige eigene Cleanupoperationen,
Firefox/Geckodriver, privater Loopback-Proxy und HTTPS/DNS nur für erlaubte Abrufziele.
Keine MangaLib-Anwendung und keine Datenbank dafür erforderlich. Zunächst begrenzter
Umfang, dann ein gesondert gebundener Timeout-/Nachfahrenfall. Keine alte Freigabe
wiederverwenden. Ein konkreter nativer Einmalharness/Grant für diesen Live-Test wurde
in diesem Auftrag weder erzeugt noch ausgeführt.

### Grenzen der bisherigen Nachweise

Parser-/Konfigurations-/Mocktests sind kein realer Firefox-Netzwerknachweis.
Die Proxy-Pinentscheidung ist implementiert; ob die installierte Firefox-Version
sämtliche einschlägigen Kanäle wie konfiguriert führt, muss noch real geprüft werden.
Dies ist keine OS-Firewall oder Absicherung gegen einen kompromittierten Browser/
lokalen Benutzer. Allowlist und Transportbudgets können reale Seitenfunktion oder
große Reihen einschränken und dürfen erst nach gemessener Abnahme gezielt angepasst
werden. Das Windows-Jobprofil hat weiterhin keine zusätzlichen CPU-/RAM-Quoten.
Installationsdateien hinter Firefox/Geckodriver werden als lokal vertrauenswürdig
vorausgesetzt; die Pins ersetzen keine vollständige signierte Browserdistribution.
Live-Importe bleiben bis zu diesen Nachweisen nicht produktiv freigegeben.

Keine DB-/Browser-/Scraper-/Anwendungs-/Netzwerkausführung, keine Installation oder
Gitoperation. Keine Testguards geändert und kein Manual-JAR-Neubau. Historische
Ergebnisse bleiben erhalten; T02 ist weiterhin nicht vollständig abgeschlossen.

### Tatsächlicher Abschlusslauf dieses Implementierungsstands

Offline-Dry-Run jeweils mit identischen Argumenten vor der Ausführung geprüft:
`./gradlew.bat --offline --no-daemon compileJava compileTestJava compileManualCheckJava bootJar liveScraperWorkerJar scraperSecurityUnitTest productionScraperUnitTest workerOwnerUnitTest ownerProbeUnitTest --rerun-tasks`

Ergebnis: BUILD SUCCESSFUL; alle 21 Aufgaben tatsächlich ausgeführt, kein bloßes
UP-TO-DATE als neuer Testnachweis. 176 Fälle bestanden, 0 Fehler, 0 übersprungen:
3 scraperSecurityUnitTest, 17 productionScraperUnitTest, 122 workerOwnerUnitTest,
34 ownerProbeUnitTest. Neue Prüfungen umfassen Ziel-/DNS-Entscheidungen mit synthetischen
Adressen (kein DNS), CONNECT-Parser, Firefox-Konfiguration ohne Driverinstanz,
Gateway-Delegation ohne direkten Fallback, beide produktiven Worker-Bindungen über
Speicher-IPC, generische Fehlerantworten, Artefaktänderungen, JAR-Trennung und eigenen
Tempbaum-Cleanup. Die 122 Ownerfälle enthalten zwei neue Dateisystemfälle.

Ein Zwischenlauf hatte einen fehlgeschlagenen Quellencheck: Das Wort ProcessBuilder
stand ausschließlich im neu ergänzten Kommentar, nicht in einem Aufruf. Kommentar
präzisiert; bestehende Assertion unverändert erhalten. Abschließender Wiederholungslauf
vollständig erfolgreich. Keine Tests ausgeschlossen oder Assertions abgeschwächt.
Bekannte Compilerhinweise zu veralteten APIs und Mockito-/CDS-Bootclasspath-Warnung
weiter vorhanden; kein neuer Laufzeitnachweis daraus abgeleitet.

Die kompletten Spring-/JPA-Suiten und manualSafetyTest wurden NICHT ausgeführt:
keine DB-Freigabe, und manualSafetyTest hängt am verbotenen Manual-JAR-Neubau.
Die zugehörigen Testquellen wurden lediglich erfolgreich kompiliert. Positive
LocalSecurityMvcTest-Doubles und der bisherige direkte Gateway-Mocktest sind auf die
neue Clientgrenze umgestellt, mit zusätzlichem Nachweis ausbleibender direkter Aufrufe.

Worker-JAR SHA-256:
ff6b8fb10eb9670856f8b686c3d49b89efb023e858c7cacb948c5662cab5cc7b
Manual-JAR unverändert:
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9
Historisches 7af1c3c7-Manifest weiter 9b80a24c6c64b24ef7396712fc2d34f030183e36a23c66e470db6a59a17c1cf8,
result.json weiter ae233d49ec1362e3996b1e7561683e92172c6549afb81676b66cbd5cb694a1c4.
Kein nativer Versuch und keine neue ausführbare Einmalfreigabe. Reale Timeout-/
Nachfahren-/Firefox-/SSRF- und nachgelagerte geschützte HTTP-/DB-Abnahme bleiben offen.

## Nachtrag 30.10.2026 – Gebundene reale Scraper-Abnahme vorbereitet, nicht ausgeführt

Zwei ausdrücklich getrennte Profile statt eines pauschalen Import-PASS:

1. BROWSER_BOUNDARY_V1 zuerst: genau eine Owner-JVM, ein eigener Job mit einem
   Abnahmeworker und einer Firefox-/Geckodriver-Sitzung samt deren Nachfahren.
   Ausschließlich eigene Loopback-Kommunikation: Gecko/Marionette, privater
   Proxy und kurzlebiger TCP-Canary. Kein externer Abruf/DNS vorgesehen.
2. LIVE_IMPORTS_V1 erst nach ausgewertetem Grenztest und gesonderter Freigabe:
   eine Owner-JVM, höchstens zwei sequenzielle produktive Worker/Jobs/Browser-
   Sitzungen. Feste Anfangsziele /editions/2502 und /volumes/15295 auf
   https://www.manga-passion.de:443, Unterressourcen/Weiterleitungen ausschließlich
   innerhalb der bestehenden www-/media.manga-passion.de-Allowlist. Diese Indizes
   stammen aus vorhandenen Scraperbeispielen; aktuelle Seitenverfügbarkeit wurde
   mangels Netzwerkfreigabe nicht vorab behauptet.

### Artefakte und Aufrufweg

- scripts/New-ScraperAcceptanceCandidate.ps1 erzeugt je expliziter Kennung nur ein
  manifest.txt, eine NICHT ausführbare approval-request.txt und den kalten
  preflight.json. Keine approval.txt, kein consumed und kein Prozessstart.
- scripts/Start-ScraperAcceptance.ps1 bietet ausschließlich -ValidateOnly oder
  -ExecuteOnce. PowerShell 7/x64, Branchdatei, kanonische/reparsefreie Pfade,
  Manifest-/Dateihashes, JVM-Overrides und gemeinsamer produktiver Importslot
  werden vor dem Start geprüft. -ValidateOnly kehrt vor nativen Deklarationen zurück.
- Eigene mangalib-scraper-acceptance.jar ohne Spring/DB/Selenium startet den bestehenden
  produktiven Client mit dem gleichen Live-Factory-/Jobpfad und einem begrenzten
  nativen Beobachtungsdekorator. Kein MangaLib-Anwendungsstart.
- mangalib-scraper-boundary-worker.jar enthält dieselben Produktklassen wie der
  Live-Worker; Bytegleichheit sämtlicher bestehender Klassen wird getestet. Nur
  feste Probe-Entry-/Fixtureklassen kommen hinzu. Der normale Produktworker kennt
  keine aktivierbare Testoption und übergibt keinen Fixture-Responder.
- Der Manifest-SHA256 bindet Profil, Kennung, feste Indizes, Start-/Netzwerkumfang,
  Frist, gemeinsame Slotadresse sowie konkrete Pfade/Hashes der Owner-/Worker-JARs,
  Java/JVM/JLI/release, JNA, Gecko, Firefox, kernel32, Launcher/Inspektionsskript,
  Manual-JAR und der bereits vorhandenen 7af1c3c7-Manifest-/Ergebnis-/Verbrauchsevidenz.
- Eine echte approval.txt darf erst NACH ausdrücklicher Eigentümerfreigabe entstehen.
  Exakter Text (LF): SCRAPER_ACCEPTANCE_APPROVED_V1, Kennung, Manifest-SHA256,
  jeweils eine Zeile und abschließendes LF. Ihr SHA256 wird als ApprovalHash geprüft.
  consumed wird per CREATE_NEW und Flush(true) dauerhaft VOR dem einzigen Ownerstart
  geschrieben. Wiederverwendung, Hashabweichung oder belegter Slot: STOP.
- Ausführung erst nach dieser Freigabe: Start-ScraperAcceptance.ps1 -AttemptId <ID>
  -ManifestHash <SHA256> -ApprovalHash <APPROVAL_SHA256> -ExecuteOnce. Nicht über Gradle
  ausführen und keines der Worker-JARs direkt starten. Aktive Kennungen stehen im
  anschließenden Einfrierungsnachtrag; keine automatische Auswahl des neuesten Ordners.

### Was wirklich gemessen werden soll

Der eigene gehaltene Ownerhandle liefert native PID, exakte Erstellungszeit,
Image, Ende und tatsächlichen Exitcode; stdout-/stderr-EOF, Stream- und Handle-
abschluss werden getrennt protokolliert. Keine CIM-/WMI-Subscription. Im eigenen
Job werden während des Imports bounded Snapshots erhoben. Ein PID-/Namensfund allein
zählt nicht: eigener Readhandle, native Erstellungszeit/Image und IsProcessInJob
müssen zusammenpassen. Nach bestätigter Beobachtung beider gewünschten Images
entfallen weitere Zusatzabfragen. Der reguläre Owner behält seine unveränderten
strikten Abschluss-/Quarantäneregeln. Keine Terminierung über beobachtete PIDs.

Pro Import: titelhaltiger nichtleerer Ergebnisvertrag, echte Proxy-Upstream-
Verbindung (nicht bloß vorhandene Proxykonfiguration), Ticket, Root-Exitcode 0,
stabiler leerer Job, Kanal-/Ressourcenabschluss und freies Journal. Keine feste
historische Prozesszahl. Unvollständige native Zusatzmessung wird nicht zu einer
bewiesenen Firefox-/Geckomitgliedschaft hochgestuft. samplingFailures und die Grenzen
historischer Identitäten werden protokolliert. Bei unbestätigtem Ressourcenabschluss
bleibt die Originalsession mit Referenzen passiv erhalten; kein implizites Exit-Cleanup
als Erfolg und keine automatische Wiederholung/Bereinigung.

Fristen: je Start 30 s, Reihe 180 s, Band bzw. lokaler Probeauftrag 120 s,
Jobcleanup 15 s; Ownerüberwachung 450 s plus begrenzter Abschluss, äußerer Launcher
höchstens 480 s. STOP-Datei kann den eigenen Auftrag abbrechen. Erst vollständig
bestätigte Abschlüsse erlauben einen weiteren Import. Kein zweiter Import nach einem
fehlgeschlagenen oder unzureichend belegten ersten Fall.

### Browser-/SSRF-Matrix des lokalen Profils

Feste HTTP-Antworten für mangalib-acceptance.invalid werden im Probe-Proxy lokal
beantwortet, ohne DNS oder Upstream. Die produktive Zielprüfung wird ausschließlich
für diesen nicht extern abrufbaren Fixtureursprung umgangen; jedes Folge-/Canaryziel
läuft unverändert durch den Produktfilter. Kein TLS-MITM, kein Zertifikatsimport,
keine Hostsdateiänderung, keine Erweiterung der produktiven Domain-/IP-Allowlist.

- img, iframe, fetch und WebSocket auf den eigenen Loopback-Canary;
- echter HTTP-302 auf den Canary und separat auf ein verbotenes .invalid-HTTPS-Ziel;
- clientseitige Meta-Weiterleitung und direkte HTTPS-Loopback-Navigation;
- API-Exposition im Fixturekontext für WebRTC/WebTransport;
- Ausfall des eigenen Proxys bei weiter laufendem Browser: Request muss scheitern,
  kein direkter Canarykontakt.

Je Fall: getrennte Pfade und SHA256 des erwarteten Proxy-Requestkopfs statt Roh-URL-
oder Headerlogs. Nur korrelierte Proxyablehnung bei null Canarykontakten gilt als
Server-/Proxybeleg. Ein vorab durch Firefox verhinderter oder nicht beobachtbarer
Request bleibt NOT_MEASURED; keine Interpretation einer UI-Meldung als Statuscode.
Kein PASS bei upstream DNS/TCP im lokalen Profil. Bei Canarykontakt sofort weitere
Fälle stoppen, nur eigene Ressourcen schließen, Befund erhalten. Ausgaben heißen
SCOPED_PASS bzw. PARTIAL/STOP, niemals pauschal vollständige SSRF-Sicherheit.

### Vorab gefundener und korrigierter TLS-Fehler

Der vorhandene Selenium-4.18-FirefoxOptions-Default setzt acceptInsecureCerts=true;
dies wurde im datenbankfreien Test tatsächlich beobachtet. ScraperBrowser setzt
nun explizit false, mit neuer Regressionassertion. Die frühere allgemeine Aussage,
Zertifikatsprüfung sei bereits aktiv, war damit nicht ausreichend abgesichert.
Keine Zertifikatsausnahme für die lokalen Fixtures erforderlich (nur HTTP, kein TLS).

Für zuordenbare Fehler vor der realen Abnahme ergänzt: begrenzte Proxyzähler sowie
höchstens vier Exception-Klassennamen in einer eigenen temporären Diagnose. Keine
Messages, Stacktraces, HTML-/Responseinhalte oder Credentials. Diagnosefehler dürfen
den kontrollierten Binärfehlervertrag nicht ersetzen. Controllerantworten bleiben
von diesen internen Typketten getrennt.

### Aussagegrenzen vor einer T02-Freigabe

Die Matrix ist keine globale OS-Netzwerkaufzeichnung oder Firewall. Null Kontakte
an einem eigenen Canary beweisen keine Abwesenheit jeder denkbaren Umgehung. API-
Exposition im HTTP-Fixturekontext ersetzt insbesondere keinen sicheren Ursprungs-
Nachweis für alle browserinternen Kanäle. TLS- und andere interne Helferkanäle,
Crash-/Updatehelfer und historische kurzlebige Identitäten sind nicht global vermessen.
Echte private LAN-/Metadatenziele werden nicht als Canary missbraucht; die zugehörigen
Adressentscheidungen bleiben zunächst datenbank-/DNS-freie Policyfälle.

Firefoxdateimetadaten zeigen 159.0; Gecko liegt als gepinntes Executable vor, seine
Laufzeitversion wurde nicht durch einen verbotenen Prozessstart ermittelt. Kompatibilität
mit Selenium 4.18.1 und Windows-Nesting/Sandbox ist noch real nachzuweisen. Firefox-
Installationsdateien außerhalb des gepinnten EXE sowie OS/JDK-Distribution bleiben
lokal vertrauenswürdige Voraussetzungen; dies ist kein vollständiges Supply-Chain-Audit.
Eine gezielte reale Timeout-/Nachfahrenabnahme bleibt nach den beiden Profilen separat
zu planen/freizugeben. Keine T02-Erledigt-Markierung aus diesen Vorbereitungstests.

### Eingefrorene aktive Abnahmekandidaten und tatsächlich ausgeführte Vorprüfungen

BROWSER_BOUNDARY_V1:
- Kennung f78ac96a-3f23-476c-83dd-ee417eb0f8e6
- Manifest .gradle/t02-scraper-acceptance/f78ac96a-3f23-476c-83dd-ee417eb0f8e6/manifest.txt
- Manifest-/Bundle-SHA256 5024bf75ad70f45fcdc991908bcdfd03c652fdca0b5af516d627ebe41a7cc671

LIVE_IMPORTS_V1:
- Kennung 881e7aab-f5a9-4b5d-a728-a7e7b48780e9
- Manifest .gradle/t02-scraper-acceptance/881e7aab-f5a9-4b5d-a728-a7e7b48780e9/manifest.txt
- Manifest-/Bundle-SHA256 ca0f024755f14d676bc03e94f1ae42f2f7a06e0ea9bf75e9065228f7e2e5cc1a

Der SHA des Manifests ist die Integritätsbindung des gesamten aufgeführten
Artefakt-/Konfigurationsstands, nicht nur der Hash eines einzelnen JARs.
Einzelartefakte SHA256:
- Owner-Abnahme-JAR 5ee59dc46faa93c0671b8f66dedf8b844e9b0fd264176e441204c10aed2c48af
- Produktworker 5c3aaad17f5a588d520ad1a0efaeab93c53d66845e7a3ae06505a22cf4b80485
- Grenztestworker c6f4f6c404a6419b95bd3f96a41f92af4acf41158ea844c6fa18cebfa17ff6b4

Beide ValidateOnly-Prüfungen tatsächlich erfolgreich: Approved=false,
Consumed=false, NativeCalls=false, OwnerStarts=0, WorkerStarts=0, NetworkCalls=0.
preflight.json liegt jeweils im eigenen Kandidatenordner. Keine approval.txt,
consumed-, binding- oder Ergebnisdatei eines realen Laufs vorhanden.

Die vorbereitenden Draftkennungen 04637182-1ed6-4f42-b447-4dbe183fc01d und
0d39f271-d4b6-4bae-9bf9-bd19af43e260 sind NICHT aktiv; maßgeblich sind nur die
oben genannten endgültigen IDs. Ihre Dateien bleiben unverändert erhalten;
nach den abschließenden Codekorrekturen scheitert die alte Artefaktbindung. Dies
waren ausschließlich kalte Vorbereitungen, keine wiederholten nativen Versuche.

Abschließender Gradle-Lauf nach identischem Offline-Dry-Run:
`./gradlew.bat --offline --no-daemon compileJava compileTestJava bootJar liveScraperWorkerJar scraperAcceptanceJar scraperBoundaryWorkerJar scraperSecurityUnitTest productionScraperUnitTest workerOwnerUnitTest ownerProbeUnitTest scraperAcceptanceUnitTest --rerun-tasks`
BUILD SUCCESSFUL, 24 Aufgaben tatsächlich ausgeführt. 197 Tests bestanden,
0 Fehler/übersprungen: 3 Zielpolicy, 18 Produktworker/Gateway, 122 Owner,
34 Owner-Protokoll, 20 neue Abnahme-Fake-/Datei-/Verpackungsfälle. Keine
Spring-Boot-Vollkontexte, DB- oder nativen Abnahmetests gestartet. Ein anfänglicher
Test-Kompilierungsfehler (Selenium-Gettername) wurde korrigiert; keine Assertion
entfernt oder abgeschwächt. Alte Testergebnisse bleiben historische Nachweise.

Zusätzlich vier kalte PowerShell-Negativprüfungen bestanden: Nullkennung, falscher
Manifesthash, profilfremder Manifesthash und nicht mehr passende Draftartefakte.
Alle ausschließlich über ValidateOnly, vor jeglicher Ausführung abgewiesen.
Beide neuen Skripte syntaktisch geprüft und beide endgültigen Manifeste kalt
validiert. Vorhandene Compiler-/Mockito-CDS-Hinweise bleiben bekannt.

Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
7af1c3c7-Manifest weiterhin 9b80a24c6c64b24ef7396712fc2d34f030183e36a23c66e470db6a59a17c1cf8;
result.json weiterhin ae233d49ec1362e3996b1e7561683e92172c6549afb81676b66cbd5cb694a1c4.
Verbrauchsevidenz zusätzlich im jeweiligen Manifest hashgebunden. Historische
Evidenzen und Grants nicht bearbeitet, kein neues reales Grant erzeugt/verbraucht.

Die 30-s-Startfrist endet mit gebundenem Worker-HELLO; Firefoxinitialisierung
bleibt zusätzlich durch das Gesamtbudget des Imports begrenzt. Proxy und Canary
binden ausdrücklich 127.0.0.1. Interne Gecko-/Firefox-Loopbackkanäle sind Teil der
noch ausstehenden realen Abnahme, keine behauptete vollständige Listenerinventur.

Freigabevorschlag: zunächst nur BROWSER_BOUNDARY_V1 für genau diesen eingefrorenen
Stand. Erst nach Auswertung eines vollständigen SCOPED_PASS die eigenständige
Freigabe für LIVE_IMPORTS_V1 erteilen. Bei jeder Abweichung keine Wiederholung,
keine weitere Stufe, keine pauschale Quarantänefreigabe und keine Bereinigung
historischer Sitzungen. Dieser Nachtrag erteilt KEINE native Ausführungsfreigabe.

Abschließende Dateiprüfung: 24 betroffene Code-/Test-/Skript-/Dokumentdateien auf
Konfliktmarker und nachgestellte Leerzeichen geprüft, ohne Befund. Beide Skripte
syntaktisch geprüft; endgültiges ValidateOnly nach Dokumentationsabschluss erneut
erfolgreich. Keine Git-Befehle einschließlich git diff/check/status ausgeführt.
Die expliziten Markerprüfungen bestätigten für beide aktiven Kennungen: keine
approval.txt, consumed, binding oder result.json eines echten Versuchs vorhanden.

## Nachtrag 30.10.2026 – Einmaliger Browsergrenztest f78ac96a tatsächlich ausgeführt

Ausdrückliche Eigentümerfreigabe ausschließlich BROWSER_BOUNDARY_V1,
Kennung f78ac96a-3f23-476c-83dd-ee417eb0f8e6, Manifest-/Bundle-SHA256
5024bf75ad70f45fcdc991908bcdfd03c652fdca0b5af516d627ebe41a7cc671.
Erneute kalte Prüfung erfolgreich; neue gebundene approval.txt erstellt,
consumed vor dem ersten nativen Schritt atomar per CREATE_NEW/Flush(true)
geschrieben. Genau ein Owner und ein Worker mit privatem Job und einer
Firefox-/Geckodriver-Sitzung gestartet. Keine automatische Wiederholung.

Ergebnis dauerhaft PARTIAL/STOP, Gesamtdauer 29141 ms. Rohbelege unter
.gradle/t02-scraper-acceptance/f78ac96a-3f23-476c-83dd-ee417eb0f8e6/:
- Owner PID 38620, native Erstellung 134378685730645387, tatsächlicher Exitcode 78.
- Worker PID 19220, native Erstellung 134378685741071840, tatsächlicher Exitcode 0.
- Nativ mit eigenem Readhandle, Erstellungszeit/Image und Jobmitgliedschaft beobachtet:
  Geckodriver PID 5516 / 134378685748147517;
  Firefox PID 41372 / 134378685750634785 und 34368 / 134378685750869662.
  Außerdem eigene Jobmitglieder conhost 23448 / 134378685741409696 und
  25520 / 134378685748398584. Kein Besitzbeleg allein aus Name/Parent-PID.
- Keine Fehler in der Zusatzmessung (samplingFailures=0); spätere/kurzlebige
  historische Identitäten und globale Konsolenprovenienz nicht vollständig vermessen.
- Stabiler Jobleerstand active=0, groupEnded/resourcesClosed/channelEnded=true.
  Owner-stdout und -stderr EOF, Streams und originaler Handle geschlossen;
  ursprüngliche Toolsession 39362 regulär abgeschlossen. Keine offenen eigenen
  Job-/Ownerressourcen gemeldet. Journal ohne pending/quarantine; keine
  administrative Quarantänefreigabe oder manuelle Markerbereinigung vorgenommen.

### Tatsächlich beobachtete Browserfälle

| Fall | Beobachtung | Bewertung |
| --- | --- | --- |
| Bild-Unterressource | DENIED_AT_PROXY | Proxyablehnung nachgewiesen |
| iframe-Unterressource | DENIED_AT_PROXY | Proxyablehnung nachgewiesen |
| fetch | DENIED_AT_PROXY | Proxyablehnung nachgewiesen |
| WebSocket | NOT_MEASURED_CLIENT_BLOCK_OR_NO_REQUEST | Nicht nachgewiesen |
| HTTP-302 auf Canary | NOT_MEASURED_CLIENT_BLOCK_OR_NO_REQUEST | Nicht nachgewiesen |
| HTTP-302 auf fremdes .invalid-Ziel | NOT_MEASURED_CLIENT_BLOCK_OR_NO_REQUEST | Nicht nachgewiesen |
| Meta-Navigation | NOT_MEASURED_CLIENT_BLOCK_OR_NO_REQUEST | Nicht nachgewiesen |
| HTTPS-Loopback | NOT_MEASURED | Nicht nachgewiesen |
| API-Exposition im Fixturekontext | PASS | Nur dieser Kontext, kein globaler Kanalbeleg |
| Proxy-Ausfall | Request scheiterte, kein Canarykontakt | Begrenzter Fallbeleg |

Am Proxy: 775 angenommene TCP-Verbindungen, 250 protokollierte Ablehnungen,
0 Upstream-DNS-Auflösungen, 0 Upstream-TCP-Verbindungen. Canarykontakte 0.
Dies beweist keinen globalen Ausschluss anderer Firefox-/OS-Netzwerkkanäle.
Insbesondere keine globale Netzwerkaufzeichnung oder OS-Firewallmessung durchgeführt.
Kein bloßer Import-PASS und keine nachträgliche Umdeutung des Grenztests.

### Ursache und nachgelagerte Offlinekorrekturen

Bestätigt: Verbindungszähler überschritt das bestehende Bearbeitungsbudget 256;
weitere Verbindungen wurden vor der Auswertung geschlossen. Dies verhindert
spätere korrelierte Fallbelege. Der alte Proxy schloss bei Policyablehnung nur
TCP, ohne terminale HTTP-Antwort. Browser-Retries sind eine plausible Ursache des
hohen Aufkommens; ihr Anteil gegenüber anderen Browseraktivitäten ist mangels
persistierter Einzelrequestklassifikation nicht nachträglich bewiesen.

Ebenfalls statisch bestätigt: WebSocket-Korrelation erkannte nur GET mit http://,
nicht mögliche ws://-Upgrade- oder CONNECT-Darstellungen. Der konkrete alte
WebSockettransport bleibt unbewiesen; kein nachträglicher PASS aus dieser Korrektur.

Gezielt behoben, ohne erneute native Ausführung:
- Feste begrenzte HTTP-403-Antwort auf Ziel-/Policyablehnung, HTTP 429 bei
  erschöpftem Bearbeitungsbudget und HTTP 502 bei Fehler vor Tunnelaufbau.
  Content-Length 0, Connection close, Cache-Control no-store, keine internen
  Daten. Nach etabliertem TLS-Tunnel keine HTTP-Fehlerbytes injizieren.
- Schreiben vor dem finalen Clientclose; Fehler eines bereits geschlossenen
  einzelnen Clients schließen nicht den ganzen Proxylistener.
- Korrelierbare WebSocket-GET(http/ws)- und CONNECT-Formen. Eigenes localhost-
  Ziel trennt den WebSocketfall von der späteren numerischen HTTPS-Loopbackprobe.
- Domain-/IP-/Portallowlist, TLS-Prüfung, 8 parallele Handler, Budget 256 und
  Byte-/Zeitlimits unverändert. Keine Freigabe von Direct-Fallbacks oder neuen Zielen.

Offline nach identischem Dry-Run erneut kompiliert/gepackt und geprüft:
200 Javafälle bestanden, 0 Fehler/übersprungen: 3 Policy, 20 Produktgateway/-worker,
122 Owner, 34 Owner-Protokoll, 21 Abnahme-Fake-/Dateifälle. Drei neue Prüfungen für
terminale Fehlerantworten, unzulässigen Erfolgsstatus und getrennte WebSocket-
Korrelation. Keine Spring-/DB-Kontexte, keine zusätzliche Browser-/OS-Probe.
Ob diese Korrekturen reale Wiederholungen reduzieren und die fehlenden Browserfälle
messbar machen, ist NICHT durch den Offline-Lauf bewiesen.

### Unveränderliche Evidenz und nächste Freigabegrenze

Ausgeführte Owner-/Produktworker-/Grenztestworker-JARs sowie Launcher/Inspektor vor
Neubau bytegleich nach executed-artifacts/ kopiert und gegen die ursprünglichen
Manifestpins geprüft; index.txt hält die Zuordnung. Alter Proxyquellstand zusätzlich
gesichert. Originalmanifest, approval, consumed, binding, acceptance-result.txt und
result.json unverändert. Kein Restore alter Freigaben und keine zweite Ausführung.
Originale Ergebnis-SHA256:
- acceptance-result.txt 3664339dfa2d0a3e08635deb79744c6442154f6beabe82fcc6ee60e88ae2ba84
- result.json 29f770cdfd69b9d5233834addfe9c83e8191e98a66faf342c9c61feccf1ea0e9
- consumed 8c2ea22c4fae39000ca1462d139415679604aad095f14f9e5797296257bbda3a
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.

LIVE_IMPORTS_V1 derzeit NICHT als nächster Versuch empfohlen und nicht ausgeführt.
Seine alte Artefaktbindung ist nach dem korrigierten Neubau ungültig; eine rein kalte
Prüfung weist dies zurück. Zuerst fehlende Grenzfälle mit eigenständiger neuer
Eigentümerfreigabe nachweisen. Neuer Kandidat nur vorbereitet:
- BROWSER_BOUNDARY_V1, Kennung 18896811-5e95-41df-af61-25120b484c40
- Manifest-/Bundle-SHA256 e0e069c4bc09560987b19cc609b2730a850ced96f829ba765c0884983af69c24
- Manifest .gradle/t02-scraper-acceptance/18896811-5e95-41df-af61-25120b484c40/manifest.txt
- ValidateOnly erfolgreich: Approved=false, Consumed=false, NativeCalls=false,
  OwnerStarts=0, WorkerStarts=0, NetworkCalls=0. Keine neue echte Einmalfreigabe,
  kein Verbrauchsmarker und kein erneuter nativer Start.

Keine Datenbankzugriffe, MangaLib-Anwendungsstarts, echten Manga-Passion-Abrufe,
Installationen oder Gitoperationen. Globale Netzwerkisolierung, vollständige
Browsermatrix und spätere Timeout-/Nachfahrenabnahme bleiben offene Nachweise.
T02 nicht abgeschlossen, T03 nicht vorgezogen.

## Nachtrag 31.10.2026 – Korrigierter Browsergrenztest 18896811: SCOPED_PASS

Ausdrückliche einmalige Eigentümerfreigabe für BROWSER_BOUNDARY_V1, Versuchkennung
18896811-5e95-41df-af61-25120b484c40, Manifest-SHA256
e0e069c4bc09560987b19cc609b2730a850ced96f829ba765c0884983af69c24.
Vorprüfung erneut erfolgreich: Branch aus HEAD gelesen, sämtliche Manifestpins,
Java-/Browser-/Gecko-/Launcher-/Inspektoridentitäten, Overrides und freier Import-Slot
geprüft. Neue gebundene approval.txt per CREATE_NEW/Flush(true) erstellt; consumed
vom unveränderten Launcher atomar vor nativer Ausführung geschrieben. Genau ein
Versuch, ursprüngliche Toolsession 55974 regulär mit Exitcode 0 abgeschlossen.
Keine Wiederholung, keine administrativen Marker- oder Quarantäneänderungen.

### Tatsächliche native Ergebnisse

SCOPED_PASS in 19018 ms. Owner PID 4196, exakte native Erstellungszeit
134379423636530171, gebunden an den ursprünglichen gehaltenen Prozesshandle;
tatsächlicher Exitcode 0. Worker PID 28120 / 134379423647204997, Exitcode 0.
Mit eigenem Readhandle, nativer Identität und IsProcessInJob nachgewiesene Mitglieder:
- Geckodriver 12420 / 134379423654163710.
- Firefox 6344 / 134379423657589966 und 36456 / 134379423657772383.
- Firefox-crashhelper 25480 / 134379423658414546.
- Job-conhost 31756 / 134379423647543443 und 29332 / 134379423655698979.
Zusatzmessung samplingFailures=0. Kein Eigentumsbeleg allein aus PID, Parent-PID oder
Prozessname; keine Behauptung vollständiger historischer/globaler Prozessprovenienz.

Stabiler Jobleerstand active=0; groupEnded, resourcesClosed und channelEnded=true.
Owner-Ende/Exitcode, stdout-EOF, stderr-EOF, Streamabschluss und Schließen des
ursprünglichen Handles einzeln bestätigt. CLEANUP|true|CLOSED|true. Keine
unbestätigten Owner-/Jobressourcen; Import-Slot nachher ohne pending/quarantine.
Ticket 51c0343c-a87e-4638-8023-ff94e9ac3e5c. Keine fremden Prozesse beendet.

### Endliche Browser-/Proxy-Matrix

| Szenario | Tatsächliche Beobachtung |
| --- | --- |
| Bild | DENIED_AT_PROXY |
| iframe | DENIED_AT_PROXY |
| fetch | DENIED_AT_PROXY |
| WebSocket, separates localhost-Ziel | DENIED_AT_PROXY |
| HTTP-302 auf eigenen Loopback-Canary | DENIED_AT_PROXY |
| HTTP-302 auf fremdes .invalid-Ziel | DENIED_AT_PROXY |
| Meta-Navigation auf Loopback | DENIED_AT_PROXY |
| HTTPS auf numerischen Loopback | DENIED_AT_PROXY |
| RTCPeerConnection/WebTransport im geprüften Kontext | nicht exponiert, PASS |
| Ausfall des eigenen Proxys | Request gescheitert, kein Canarykontakt |

Proxy: 21 angenommene TCP-Verbindungen, 13 protokollierte Ablehnungen,
0 Upstream-DNS-Auflösungen, 0 Upstream-Verbindungen. Canarykontakte 0.
Die Korrelation beruht auf gehashten Requestzeilen, nicht bloß UI-Fehlern.
Das feste lokale Warmup-Fixture wurde nachweislich im echten Browser geladen.
Die korrigierten terminalen Proxyantworten und die WebSocket-Korrelation sind Teil
des hashgebundenen ausgeführten Standes. Gegenüber 775 Annahmen im alten Versuch
jetzt 21, ohne Budgeterschöpfung. Dies ist ein Vergleichsbefund, kein Beweis für den
Anteil einzelner Retryursachen. Tatsächliche HTTP-Fehlerstatusbytes wurden nicht
zusätzlich per Paketmitschnitt vermessen.

SCOPED_PASS gilt ausschließlich für diese aufgezählten Fälle. Keine OS-Firewall,
globale Paketaufzeichnung oder vollständige Netzwerkisolierung nachgewiesen;
GlobalNetworkProvenance und GlobalConsoleProvenance bleiben NOT_MEASURED.
Nicht sämtliche Browser-APIs/Origins/Versionen oder Netzwerknebenkanäle geprüft.
Proxy-DNS-/Connectzähler sind keine globale Beobachtung aller OS-Netzwerkzugriffe.
Kein echter Import, keine allgemeine Einsatzreife und kein T02-Gesamtabschluss.

### Unveränderliche Belege und Prüfungen

Originale Dateien unter .gradle/t02-scraper-acceptance/18896811-5e95-41df-af61-25120b484c40/:
- acceptance-result.txt SHA256 3eaeefd04900757191edcdfad8419f2f6ab179272a075df394bdb15e0109ef09
- result.json SHA256 feff60740920c7f5bb8ae74ee52671e0d9305fcc702af868f13c52c6747860ee
- consumed SHA256 8605344f82460165f8214b9a2f3f587043d778b226cf29a0e249ac7d4fa457cb
- binding SHA256 4825ab852aee04eaa0712584f5da7111a396b8af150d9518e9241178faf2e6e0
- approval.txt SHA256 91123fa683859179bf76f85313a4cef4c5308c9dc0afca0f8e96d9a6c809b70d
Ausgeführte Owner-, Worker-, Produktworker-JARs und Launcher/Inspektor zusätzlich
bytegleich nach executed-artifacts/ archiviert; index.txt gegen Manifestpins
geprüft. Keine historischen Dateien verändert. Der alte Versuch f78ac96a bleibt
unverändert PARTIAL/STOP. Manual-JAR weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.

Keine Produktionscodekorrektur erforderlich; keine Gradle-Tests/Builds in diesem
Auftrag erneut ausgeführt. Der vorherige Offline-Stand mit 200 erfolgreichen
Tests bleibt historisch erhalten. In diesem Auftrag: kaltes ValidateOnly, genau
ein realer Grenztest, Evidenz-/Archiv-/Integritätsprüfungen und kalte Vorbereitung
des nachfolgenden Kandidaten. Keine DB, MangaLib-Anwendung, echten Manga-Passion-
Abrufe, Installationen oder Gitoperationen. Keine neue native Ausführung nach
dem abgeschlossenen Versuch.

### Nächste echte Freigabegrenze

LIVE_IMPORTS_V1 ist jetzt als nächster begrenzter Kompatibilitäts-/Funktionsnachweis
empfohlen, weiterhin NICHT ausgeführt oder genehmigt. Alte Live-Manifeste bleiben
historisch; neue Artefaktbindung kalt vorbereitet und erfolgreich validiert:
- Kennung 9fecde5b-05c6-4058-8ec5-8d733b4c3915.
- Manifest-SHA256 8a5ddc993b2f98b53025bf9d6285212d20db6cec110d8d06eeb860443637d03c.
- Ein Owner, höchstens zwei sequenzielle Worker/Jobs/Firefox-Gecko-Sitzungen.
- Ausschließlich Reihe 2502 und Band 15295; DNS und HTTPS-Port 443 nur für
  www.manga-passion.de und media.manga-passion.de gemäß Produktallowlist,
  keine direkten Browser-Fallbacks, keine DB und kein MangaLib-Anwendungsstart.
- Nur nach erfolgreichem ersten Import inklusive Mitgliedschaft/Ende/Cleanup
  darf der zweite beginnen. Bestehende Fristen/Quarantäne/STOP-Regeln unverändert.
- ValidateOnly: Approved=false, Consumed=false, NativeCalls=false,
  OwnerStarts=0, WorkerStarts=0, NetworkCalls=0. Keine ausführbare Freigabe erzeugt.
Separate ausdrückliche Eigentümerfreigabe an Kennung/Hash erforderlich. Auch ein
späterer Live-PASS wäre kein globaler SSRF-/OS-Netzwerkisolationsnachweis.
Timeout-/Nachfahrenverhalten und produktive Betriebsabnahme bleiben gesondert zu
bewerten. T02 bleibt offen, T03 unverändert nachgeordnet.

## Nachtrag 31.10.2026 – LIVE_IMPORTS_V1 tatsächlich ausgeführt: beide Wege SCOPED_PASS

Einmalige ausdrückliche Eigentümerfreigabe, Versuchkennung
9fecde5b-05c6-4058-8ec5-8d733b4c3915, Manifest-SHA256
8a5ddc993b2f98b53025bf9d6285212d20db6cec110d8d06eeb860443637d03c.
AGENTS.md, aktuelle Nachträge, Manifest, Launcher und Produktpfad erneut gelesen.
ValidateOnly erfolgreich: Branch aus HEAD, sämtliche Artefaktpins/Identitäten,
Reparse-/Overridekontrollen und freier Import-Slot geprüft. HTTPS-CONNECT nur für
www.manga-passion.de und media.manga-passion.de:443, öffentliche DNS-Antworten und
Connect auf die geprüfte IP; TLS-Prüfung aktiv, kein Direct-Fallback. Keine neue
Zieladresse durch Vorbereitung oder Versuch freigegeben. Grant per CREATE_NEW und
Flush(true) angelegt, consumed vom gebundenen Launcher atomar vor Ausführung.

Genau ein Owner und zwei strikt sequenzielle produktive Worker-/Job-/Firefox-
Sitzungen; keine automatische Wiederholung. Der zweite Import begann erst nach
SUCCESS, nachgewiesenem Browser-/Geckojobbesitz, Workerexit 0, vollständigem Gruppen-,
Kanal- und Ressourcenabschluss, freiem Journal und IDLE des ersten Imports.
Fristen unverändert: 30 s Start/HELLO, 180 s Import, 120 s Cleanup, 15 s Stabilität;
450 s inneres und 480 s äußeres Gesamtbudget. Kein Timeout in diesem Lauf.

### Beobachtete Ergebnisse

| Fall | Ergebnis | Payload | Proxy angenommen / DNS / Upstream-TCP / abgewiesen |
| --- | --- | --- | --- |
| Reihenindex 2502 | PASS | Map<String,String>, 18 Felder | 21 / 5 / 5 / 16 |
| Bandindex 15295 | PASS | Map<String,String>, 11 Felder | 22 / 5 / 5 / 17 |

Beide Titel waren vorhanden, nicht leer und nicht gleich Unbekannt; diese
Funktionsprüfung und die Feldanzahl sind im eingefrorenen Harness nachgewiesen.
Die Rückgabemaps wurden nach erfolgreichem begrenztem Binär-IPC im Owner ausgewertet.
Der Harness hat konkrete Feldnamen/Werte und Nullverteilung NICHT persistiert;
kein nachträglich behaupteter vollständiger JSON-/Importfeldvergleich oder
Beleg für sämtliche fachlichen Inhalte. Statisch liefert der Reihenextraktor
Titel, block-/labelabhängige Metadaten und gegebenenfalls Band-href/Bild/Preis oder
Cover; der Bandextraktor Titel, BildUrl, Labelwerte und gegebenenfalls Preis.
Dies beschreibt den Codevertrag, nicht nachträglich beobachtete einzelne Werte.
Keine Nutzdaten aus einer DB gelesen oder dort gespeichert.

Gesamtlaufzeit 20874 ms. Separate Import-/Cleanupdauern wurden vom gebundenen
Harness NICHT gespeichert und sind nicht rekonstruierbar. Native Erstellungszeiten
sind Identitätsbelege und kein Ersatz für vollständige Einzellaufzeiten.

Owner PID 2236 / native Erstellung 134379439132195075, ursprünglicher gehaltener
Handle, tatsächlicher Exitcode 0. Ursprüngliche Toolsession 41152 regulär beendet.
Reihenjob, Ticket 86cd338d-adb0-4ab1-976a-e8e72daa18cd:
- Worker 39312 / 134379439144671183.
- Geckodriver 44600 / 134379439151972872.
- Firefox 39788 / 134379439154029077 und 44184 / 134379439154204010.
- Crashhelper 15156 / 134379439154886111.
- Conhost 35188 / 134379439145008219 und 8976 / 134379439152143530.
Bandjob, Ticket a49a0f70-7a5a-471a-b2e5-2491b39f1a4d:
- Worker 38760 / 134379439238403599.
- Geckodriver 23384 / 134379439244496033.
- Firefox 31060 / 134379439246865726 und 8504 / 134379439247081748.
- Conhost 37584 / 134379439238751395 und 11196 / 134379439244716790.
Jeweils eigener Readhandle, exakte native Identität und IsProcessInJob; kein
Besitzschluss aus PID, Parent-PID oder Name. ZusatzsamplingFailures jeweils 0;
nicht alle kurzlebigen historischen Mitglieder/globalen Begleiter vollständig erfasst.

Beide Workerexitcodes 0; groupEnded/resourcesClosed/channelEnded=true, stabiler
Jobleerstand active=0. Owner stdout-EOF, stderr-EOF, Stream- und originaler
Handleabschluss einzeln bestätigt. CLEANUP|true|CLOSED|true. Nachher weder pending
noch quarantine vorhanden; keine administrativen Freigaben, keine fremden Prozesse
beendet und keine unbestätigten eigenen Ressourcen gemeldet.

### Aussagegrenzen und unveränderliche Belege

Outcome SCOPED_PASS ausschließlich für diese zwei echten Imports. Der historische
Browsergrenztest 18896811 bleibt unverändert SCOPED_PASS, f78ac96a PARTIAL/STOP.
Proxyzahlen beweisen keine globale OS-Netzwerkisolierung oder vollständige
Paket-/DNS-/Konsolenprovenienz. Die Policy gilt für Proxytransport; keine behauptete
OS-Firewall oder globale Browserkanalbeobachtung. Konkrete abgewiesene Fremdziele
wurden nicht im Live-Bericht aufgelöst; kein belegter unerwarteter erfolgreicher
Proxyzugriff und kein allgemeiner Nachweis der Abwesenheit anderer OS-Kanäle.
Reale Fehler-/Timeout-/Nachfahrenabnahme wird durch zwei normale Erfolgsfälle
nicht ersetzt. Kein T02-Gesamtabschluss und keine produktive Freigabe.

Evidenzen unter .gradle/t02-scraper-acceptance/9fecde5b-05c6-4058-8ec5-8d733b4c3915/:
- result.json SHA256 59169f4c20bb1c7544bccf3b8afeaea4dd9ef99934b66710e59aa8b94f34c3bf
- acceptance-result.txt SHA256 0aeb64143c65dcf0c991e3628b75816e6dc939d8d0d938732400b5ea1c8452d5
- consumed SHA256 8b8d53076c2731bb247cc219ba99381ffadbaa61b2b4cae30f0a0c6b8701320b
- binding SHA256 618bca85a2e91f97d7990391f21f0a0905d3c7d1ae96643d9b0e02d6f99c0bfe
- approval.txt SHA256 62a560989584d5f0c8c728e9a361be9a877ebacdb136f9bd0b4c8524396efe2c
Ausgeführte Owner-/Worker-/Produktworker-JARs und Launcher/Inspektor zusätzlich
bytegleich nach executed-artifacts/ gesichert, index.txt gegen Manifestpins geprüft.
Historische Evidenzen und Verbrauchsmarker nicht verändert. Manual-JAR unverändert
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.

Kein gewöhnlicher Implementierungsfehler im Erfolgsversuch nachgewiesen, daher
keine Codeänderung und keine neue Gradle-/JUnit-/Buildausführung. Frühere 200
Offline-Testfälle bleiben historische Ergebnisse. Tatsächlich ausgeführt: kalte
Vorprüfung, genau ein LIVE_IMPORTS_V1-Versuch mit zwei sequenziellen Imports,
anschließend ausschließlich Datei-/Archiv-/Marker-/Integritätsprüfungen und
Dokumentation. Keine weiteren OS-/Browser-/Netzwerkversuche, DB, MangaLib-
Anwendungsstarts, Installationen oder Gitoperationen.

### Noch notwendige Abschlussarbeiten

Nächster sicherheitsrelevanter Nachweis ist ein eng begrenzter, separat zu
freizugebender Timeout-/Nachfahrenfall: bestehender Owner muss die eigene private
Gruppe nach Frist vollständig beenden, eigene Ressourcen bestätigen und bei
Ungewissheit die Importsperre/Quarantäne erhalten. Kein zusätzlicher Versuch in
diesem Auftrag. Normale Fehler-/Admission-/Parallelitätsregeln anschließend anhand
der aktuellen Fake-/Unit-Abdeckung gegen die realen Abschlussbelege prüfen.
Vor allgemeiner MangaLib-Nutzung außerdem produktive Paket-/Konfigurationsabnahme
und erforderliche geschützte Regressionstests separat freigeben; Manual-JAR bleibt
synthetisch und unverändert. Der Live-Harness liefert noch keinen DB-/MVC-/UI-
End-to-End-Nachweis. Offene historische T02-Punkte sind nach tatsächlichem Ticketstand
abzugleichen; T03/T04/T06 nicht vorziehen und T02 nicht pauschal schließen.

## Nachtrag 31.10.2026 – Lokaler Timeout-/Nachfahrenkandidat vorbereitet, NICHT ausgeführt

Ziel: die nach regulären SCOPED_PASS-Imports noch offene Fristüberschreitung mit
realem Firefox/Geckodriver innerhalb derselben produktiven Jobarchitektur prüfen.
In diesem Auftrag ausschließlich Quellcode, kalte Datei-/Skriptprüfungen,
datenbankfreie Tests und Offline-Builds; keine native Abnahme/Browser/Netzwerk/DB.

### Eingefrorener Kandidat zur separaten einmaligen Freigabe

- Profil BROWSER_TIMEOUT_V1.
- Versuchkennung 8a142689-7f61-47e1-b3f1-c680281a4e7c.
- Manifest-SHA256 98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc.
- Manifest .gradle/t02-scraper-acceptance/8a142689-7f61-47e1-b3f1-c680281a4e7c/manifest.txt.
- Owner-JAR 683e6acf13056a3d2457c44302638ae82b3dedd89b8e9dab8bd57e4c5413c59f.
- Abnahmeworker-JAR a11d726f0d0227b538bf02bf487964c06d5873f1aa0631a27cff0d2f7f19b84c.
- Unveränderter Produktworker 56647015763c5d9a445387fd850e7abf9d2068a6949bbf7f30efcd113a7a9268.
- Genau ein Owner, ein Worker, eine private Jobgruppe und eine Firefox-/Geckositzung.
- Keine zweite Importsitzung, keine Live-Indizes/Seitenabrufe; IPC VOLUME/2 ist hier
  ausschließlich der feste synthetische Fehlerfall des separaten Abnahmeworkers.
  Die vorhandenen Manifestfelder series=2502/volume=15295 werden in diesem Profil
  nicht verwendet und erteilen keine Live-Abrufberechtigung.
- Einzige Navigation about:blank. Proxy nur 127.0.0.1, Gecko/Firefox-Steuerkanäle
  lokal; die Abnahme-Fixturefunktion verweigert JEDEN Proxyrequest vor DNS/Connect.
  Keine Canary-/Fixture-Webserver erforderlich, kein Internetzugriff vorgesehen.
  Dies ist keine OS-Firewall und kein globaler Netzwerkisolationsnachweis.

Fristen ausdrücklich manifestgebunden und in beiden Parsern fest validiert:
30 s Start/HELLO, 45 s Import ab Admission, 30 s Cleanup ab STOP, 100 s interne
äußere Beobachtungsfrist; unveränderte Launchergrenze 480 s. Die 45 s schließen
Browserinitialisierung ein. Beginnt der Browser nicht rechtzeitig oder fehlen
Mitgliedschaftsbelege, ist der Versuch STOP, nicht bestanden. Kein Produktions-
Timeout geändert: Budgets.candidates bleibt Start 30 s, Reihe 60 min, Band 120 s,
Cleanup 15 s. Frühere Formulierung im Live-Nachtrag zu den Versuchsbudgets präzisiert:
Budgets(start,series,volume,cleanup) war dort 30/180/120/15 s, nicht 120 s Cleanup
oder eine zusätzliche 15-s-Stabilitätsfrist. Die damaligen tatsächlichen
Endbeobachtungen und historischen Ergebnisse bleiben unverändert.

Kalt validiert: Approved=false, Consumed=false, NativeCalls=false, OwnerStarts=0,
WorkerStarts=0, NetworkCalls=0. Keine approval.txt, consumed, binding oder result.json.
Der frühere kalte Draft d8b23ed8-fefd-43d6-883b-a8ab2caf4f83 ist nach abschließendem
Code-Review/Neubau überholt, nicht ausgeführt und nicht zur Freigabe vorgesehen.

Erst nach separater ausdrücklicher Eigentümerfreigabe für obige Kennung/Hash:
1. Unverändertes Manifest, alle Datei-/Java-/Gecko-/Firefoxpins, Branch, fehlende
   JVM-Overrides und freien Import-Slot mit Start-ScraperAcceptance.ps1 -ValidateOnly
   erneut prüfen. Bei jeder Abweichung vor Start BLOCKED, keine Reparatur/Neuversuche.
2. Genau gebundene approval.txt neu erstellen; ExecuteOnce muss consumed vor dem
   ersten nativen Schritt atomar/durabel schreiben. Keine frühere Freigabe nutzen.
3. Genau einmal denselben Launcher mit -ExecuteOnce und ApprovalHash verwenden.
   Der Launcher bindet Ownerhandle/native Erstellungszeit und übernimmt die
   bestehenden Fristen, EOF-/Exit-/Disposeprüfungen und Retention bei Ungewissheit.
4. Nach Versuch keine Wiederholung, keine automatischen Markerfreigaben oder
   fremde Prozessbereinigung. Bei STOP eigene unbestätigte Ressourcen erreichbar
   halten; weitere Schritte erfordern eine konkrete neue Freigabe.

### Implementierung und Erfolgskriterien

Keine Änderung der produktiven WorkerJobOwner-/Journal-/Native-/Gatewaylogik.
TimeoutAcceptance verwendet exakt WorkerJobOwner und SimpleWorkerNative.live.
Der separat verpackte BrowserTimeoutProbe öffnet den Produktbrowser mit denselben
Proxy-/TLS-/Sicherheitsoptionen, lädt about:blank und hält die Sitzung bewusst ohne
Ergebnis und ohne driver.quit offen. Kein produktiver Fallback; der Produktworker
enthält diesen Einstieg nicht. Der natürliche Timeout der Owner-Kontrollspur muss
TerminateJobObject ausschließlich am eigenen gehaltenen Job auslösen.

AcceptanceWitness misst konservativ mit gehaltenen Readhandles, exakter nativer
Erstellungszeit/Image und IsProcessInJob. Im Timeoutprofil fortlaufende begrenzte
Messung bis zum Cleanupbeginn; vor Terminate müssen aktuell mindestens zwei
verschiedene Firefox-PIDs (Browser plus Nachfahre) und Geckodriver im selben Job
belegt sein. Keine exakt vorgegebene Gesamtprozesszahl, keine globale Inventur.
Letzter positiver Live-Snapshot muss weniger als 2 s vor Terminate liegen.
Fehlende native Cleanupunterstützung bleibt null und kann nicht von Diagnose-
Wrapping verdeckt werden. Eigene Readhandles bleiben im Ressourcenabschluss bilanziert.

Für PASS erforderlich: genau ein Start/ein Cleanup-Terminierungsaufruf, Terminate
nicht vor 45 s und innerhalb desselben Cleanupbudgets, Slot während der laufenden
Browserphase belegt und Owner ACTIVE, keine frühe Slotfreigabe. Danach ERROR mit
leerer Nutzdatenmap und NICHT erfolgreichem Importprotokoll, tatsächlicher
nichtnull/nichtnullwertiger Fehlerexitcode, bestätigter stabiler Jobleerstand,
resourcesClosed=true, getrennte native stdout-/stderr-EOFs, Owner IDLE und freies
Journal. Anschließend Owner CLOSED sowie vom Launcher bestätigtes Owner-Ende,
tatsächlicher Exitcode, getrennte Owner-EOFs und Handle-/Streamabschluss.
Ein erwarteter Workerfehler ist somit nur zusammen mit positiven Endbelegen ein
bestandener Grenzfall. Fehlende CHANNEL_END-Antwort beim Kill wird nicht als
Protokollerfolg umgedeutet. Nachfahren werden durch die ganze private Gruppe,
nicht durch PID-/Namenslisten terminiert. Keine zweite echte Importprobe zum
Testen der Wiederfreigabe; die Admissionnegativfälle bleiben datenbankfreie Fakes.

Bei ungeklärtem Abschluss bleiben bestehende pending/quarantine-Sperren erhalten,
kein späteres automatisches IDLE, kein zweiter Worker und keine administrative
Freigabe. Der reale Kandidat provoziert einen Timeout, aber keine absichtlich
unaufklärbare Ressourcenlage. Solche Negativfälle sind weiterhin mit Fakes geprüft.
Samplingfehler, unerwartete Zustände, fehlende Browsernachfahren, frühe/fehlende
Terminierung, unbestätigte EOFs oder Ressourcen führen zu PARTIAL/STOP.
Bericht enthält Lauf-/Terminierungsdauer, Prozessidentitäten, Gruppen-/Pipebelege,
Issues und Slot-/Startzähler. GlobalNetworkProvenance bleibt NOT_MEASURED.

### Tatsächlich ausgeführte Offline-Prüfungen

Vor JEDEM Gradle-Aufruf identischer --offline --no-daemon --dry-run kontrolliert.
Taskpläne enthielten ausschließlich Compile/Packaging und die fünf isolierten
Fake-/Unit-Sätze, kein test/clean/Boot-/JPA-/Native-/JavaExec-Abnahmetask und kein
Manual-JAR-Neubau. Sandbox konnte zuerst die vorhandene Gradle-Wrapper-Lockdatei
nicht öffnen; danach Zugriff auf vorhandenen Cache für Offline-Prüfungen erlaubt,
keine Downloads oder neue Abhängigkeiten.

Befehlssatz:
./gradlew.bat --offline --no-daemon compileJava compileTestJava bootJar liveScraperWorkerJar scraperAcceptanceJar scraperBoundaryWorkerJar scraperSecurityUnitTest productionScraperUnitTest workerOwnerUnitTest ownerProbeUnitTest scraperAcceptanceUnitTest --rerun-tasks
Nach finalen Reviewkorrekturen zusätzlich denselben isolierten
scraperAcceptanceUnitTest --rerun-tasks mit vorherigem identischem Dry-Run erneut
komplett kompiliert/gepackt und erfolgreich ausgeführt.

Abschließende XML-Berichte: 220 Tests bestanden, 0 Fehler, 0 übersprungen:
3 scraperSecurityUnitTest, 20 productionScraperUnitTest, 124 workerOwnerUnitTest,
34 ownerProbeUnitTest, 39 scraperAcceptanceUnitTest. Davon 20 neue Fälle gegenüber
200: erwarteter Timeout vs fehlende Endbelege, abweichende Frist/Mitgliedschaft,
EOFs, Quarantäne, Slot, Anzahl eigener Starts, Timeoutmanifest und zwei
Owner-/Browsernachfahren-Fixturefälle. Bestehende Assertions nicht abgeschwächt.
Pakete prüfen Bytegleichheit produktiver Browser-/Proxyklassen im Abnahmeworker,
Abwesenheit der Timeoutprobe im Produktworker und Trennung von Spring/JPA/DB.
Konfigurations-/Hash-/Index-/Disabled-Clientprüfungen bleiben aktiv.

Zwischenfehler transparent erhalten: zunächst Typfehler long/int im neuen
Testbeobachtungszähler korrigiert. Ein bestehender startSuspended/STOP-Faketest
überschritt einmal seinen 3-s-Join; anschließend gezielt alle drei Varianten und
vollständige 124 Ownerfälle erfolgreich. Nur Stackdiagnose ergänzt; Ursache des
einmaligen Hängers nicht bewiesen, keine Produktionskorrektur oder gelockerte
Assertion daraus abgeleitet. Ein neuer Null-Guard-Test lief während des Reviews
noch gegen den zuvor kompilierten Stand und schlug fehl; finaler vollständiger
Neubau des Abnahmesatzes danach erfolgreich. Historische Fehlversuche nicht
als PASS umgedeutet. Compiler-Deprecation-/Mockito-CDS-Hinweise weiterhin bekannt.

Beide PowerShellskripte geparst. Zwei zusätzliche kalte Negativfälle mit korrekt
gehashten synthetischen Manifesten bestanden: 60-s- statt 45-s-Budget vor Ausführung
abgewiesen (ACCEPTANCE_TIMEOUT_BUDGETS), erweitertes DNS-/HTTPS-Netzwerkprofil
abgewiesen (ACCEPTANCE_SCOPE). Keine Grants/Verbrauchsmarker/Starts dabei erzeugt.

### Integrität und T02-Abschlussgrenze

Historische SCOPED_PASS-Ergebnisse 18896811 und 9fecde5b, PARTIAL/STOP f78ac96a
sowie deren executed-artifacts/index.txt gegen SHA256 geprüft, unverändert.
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Produktworker weiterhin hashgleich zur realen Live-Abnahme. Keine historischen
Originale/Grants/Marker geändert, keine DB-Guards oder Sicherheitsfilter bearbeitet.
Geändert ausschließlich zwei Abnahmeskripte, Abnahme-Owner/Manifest/Witness,
separate Browserprobe/-dispatch, zugehörige Fake-/Pakettests und diese Dokumentation.
Keine Gitoperationen; Dateiformat-/Konfliktmarkerprüfungen stattdessen lokal.

Nachgewiesen bleiben die endliche Proxy-/Browsermatrix und die beiden normalen
Live-Imports mit regulärem Gruppenabschluss. Der neue reale Timeout-/Nachfahren-
abschluss ist nur vorbereitet, noch NICHT nachgewiesen. Anschließend gezielte
Paket-/Konfigurationsabnahme im späteren Anwendungskontext und geschützte
Regressionstests mit separater DB-Freigabe erforderlich. Kein DB-/MVC-/UI-End-to-End-
Nachweis aus diesen DB-freien Prüfungen. Globale OS-Netzwerkisolierung nicht
behauptet; Restgrenzen und weitere T02-Altanforderungen vor Ticketabschluss prüfen.
Keine allgemeine produktive Freigabe, keine T03-/T04-/T06-Arbeiten vorgezogen.
Status: READY_FOR_TIMEOUT_OS_APPROVAL, keine native Ausführungsfreigabe erzeugt.

## Nachtrag 02.11.2026 – Timeoutstart vor Ausführung durch Freigabeprüfung blockiert

Kandidat 8a142689-7f61-47e1-b3f1-c680281a4e7c, BROWSER_TIMEOUT_V1,
Manifest 98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc:
erneute kalte Vorprüfung erfolgreich, alle gebundenen Artefakte unverändert.
Die allgemeine Eigentümeranweisung zur Fertigstellung wurde zunächst als Auftrag
für diesen vorbereiteten Einzelversuch interpretiert; entsprechend wurde eine
gebundene approval.txt per CREATE_NEW/Flush(true) vorbereitet. SHA256
c8bce191b0c5eac517170aaa5798d9823d4e11abcb372412d4fb1ee6c70c3188.
Dies ersetzt KEINE bestätigte konkrete native Ausführungsfreigabe.

Die automatische Freigabeprüfung lehnte ExecuteOnce vor Ausführung des Kommandos
ab: ausdrückliche Nutzerfreigabe für genau diese Versuchkennung fehlt angesichts
der vorher verbindlich verlangten separaten Einmalfreigabe. Keine Umgehung,
kein erneuter Ausführungsversuch. Danach ausschließlich kalt nachgeprüft:
consumed, binding, result.json und acceptance-result.txt fehlen. Kein Owner-,
Worker-, Firefox-/Geckodriver- oder Jobstart; keine Freigabe verbraucht. Die
vorbereitete approval.txt bleibt als transparent dokumentierte Datei erhalten,
berechtigt ohne konkrete Eigentümerfreigabe nicht zur Ausführung.

Zweites ValidateOnly erfolgreich; Approved=false/Consumed=false/NativeCalls=false,
OwnerStarts=0, WorkerStarts=0, NetworkCalls=0 sind die kalten Prüffelder und keine
Prüfung einer tatsächlichen Eigentümerfreigabe. Keine Tests/Builds erneut gestartet,
keine Datenbank-/Anwendungs-/Netzwerk-/Gitoperationen. Manual-JAR und beide
historischen SCOPED_PASS-Ergebnisdateien erneut hashgleich bestätigt.

Status BLOCKED_AT_EXECUTION_APPROVAL, kein fehlgeschlagener nativer Versuch und
kein neuer PASS. Technischer Kandidat bleibt vorbereitet. Erforderlich ist die
ausdrückliche einmalige Freigabe von BROWSER_TIMEOUT_V1 mit obiger Kennung und
Manifesthash, genau einem Owner/Worker/Job/Firefox-Gecko einschließlich sicherem
Cleanup eigener Ressourcen, nur about:blank/lokale Kanäle, ohne DB/externe Abrufe.
Erst danach erneute Vorprüfung und einmaliger atomarer Verbrauch vor Ausführung.
T02 bleibt offen; historische Evidenzen und Prioritäten unverändert.

## Nachtrag 02.11.2026 – BROWSER_TIMEOUT_V1 einmalig real ausgeführt: SCOPED_PASS

Nach der dokumentierten Vorabblockierung erteilte der Eigentümer ausdrücklich die
konkrete Einmalfreigabe für 8a142689-7f61-47e1-b3f1-c680281a4e7c, Manifest-SHA256
98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc.
AGENTS.md und aktuelle Nachträge erneut gelesen. Kalte Prüfung sämtlicher Pins,
Branch-/JVM-Override-/Slot-/Pfad- und Manifestbedingungen erfolgreich. Vorbereitete
approval.txt hashgleich, jetzt durch die konkrete Eigentümerfreigabe autorisiert.
Launcher verbrauchte die Einmalfreigabe atomar vor der nativen Ausführung.
Genau ein Versuch, keine Wiederholung; ursprüngliche Toolsession 8893 regulär
beendet. Keine zweite Importsitzung oder fremde Prozessbereinigung.

### Tatsächlich bestätigte Timeout-/Endnachweise

SCOPED_PASS. Gesamte Launcherbeobachtung 48731 ms. Terminierung der eigenen
Jobgruppe genau einmal nach 45007 ms; Rückkehr des Owner.execute nach 45851 ms,
also rund 844 ms später. Keine vorzeitige Slotfreigabe: pendingWhileReady=true,
earlySlotLoss=false. Frist und aktuelle Browser-/Geckomitgliedschaft unmittelbar
vor Terminierung bestätigt (deadline=true, liveBeforeTermination=true).

Erwartetes Workergebnis ERROR, leere Nutzdaten, tatsächlicher Workerexitcode 1.
protocol=false ist beim absichtlich unterbrochenen Import korrekt; kein erfundener
CHANNEL_END-/Importerfolg. Getrennte native Worker-stdout-/stderr-EOFs true,
groupEnded=true, resourcesClosed=true, stabiler vollständiger Gruppenleerstand:
Observation[complete=true, stable=true, assigned=0, pids=[], active=0, total=15].
Erst nach positivem Ressourcenabschluss Owner IDLE und Journal frei; anschließend
CLEANUP|true|CLOSED|true. Nachher pending=false und quarantine=false. Keine
administrative Quarantänefreigabe oder automatische Wiederfreigabe unklarer Ressourcen.

Owner PID 22148 / exakte native Erstellung 134381005417897151, ursprünglich
gehaltener Handle, tatsächlicher Exitcode 0. Owner stdout-EOF, stderr-EOF,
StreamsClosed und HandleClosed einzeln true. OwnerResourcesUnconfirmed=false,
JobResourcesUnconfirmed=false. Keine eigenen Ressourcen als unbestätigt gemeldet.

Mit nativem Readhandle, Erstellungszeit/Image und IsProcessInJob beobachtet:
- Worker 25256 / 134381005428770469.
- Geckodriver 21892 / 134381005435667842.
- Firefox 43144 / 134381005439887108 und neun weitere Firefoxinstanzen gemäß
  unveränderlichem MEMBER-Bericht (38788, 32736, 39104, 8232, 3968, 29820,
  20140, 41804, 44884); vollständige native Zeit-/Imagebelege im Originalbericht.
- Firefox-crashhelper 45200 / 134381005440804940.
- Job-conhost 26432 / 134381005429072660 und 39868 / 134381005437904276.
Die beobachteten 14 Identitäten sind nicht als vollständige historische Liste
aller 15 zugeordneten Prozesse ausgegeben. Historischer Gesamtzähler ist keine
verbliebene Prozessanzahl. Der native Abschlussbeleg gilt der gesamten privaten
Jobgruppe. Keine erfundene weitere Identität und keine PID-/Namensbesitzannahme.

Erwartete Diagnose ISSUE|EXCHANGE|RUN_EXCEPTION|java.io.UncheckedIOException|0:
die gewaltsam beendete Workerantwort endet mit EOF statt vollständigem Protokoll.
Dies ist in diesem absichtlich provozierten Timeout kein zusätzlicher Beleg für
einen Fehler im regulären Importpfad. Kein Produktionscodefehler aus dem Lauf
nachgewiesen, deshalb keine Codekorrektur und kein weiterer nativer Versuch.

### Evidenzen und Integrität

Originale unter .gradle/t02-scraper-acceptance/8a142689-7f61-47e1-b3f1-c680281a4e7c/:
- result.json SHA256 a6fa1c67009919bf279e9dd858dbe3384104bdb5f77042154bdd78fb6628bff1
- acceptance-result.txt SHA256 4f638c76eb386f5067b7d3fe8f567c88cf0cf62fb137ff4b204ca06c4fc37c8a
- consumed SHA256 70629907b652b466a69fcc06c9345b4346140ea708d26a10caabb8c89588b9ed
- binding SHA256 8d8f309d8b2fbe10b26d7301290652e3416fdb8d3726dee855706b858437afa8
- approval.txt SHA256 c8bce191b0c5eac517170aaa5798d9823d4e11abcb372412d4fb1ee6c70c3188
Ausgeführte Owner-/Worker-/Produktworker-JARs und Launcher/Inspektor bytegleich
nach executed-artifacts/ gesichert; index.txt gegen die Manifestpins geprüft.
Vorherige SCOPED_PASS-Ergebnisse 18896811/9fecde5b sowie PARTIAL/STOP f78ac96a
unverändert hashgeprüft. Manual-JAR weiterhin
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Keine historischen Evidenzen/Grants überschrieben oder Ergebnisse umgedeutet.

### Prüfungsumfang und verbleibende Freigabegrenze

Dieser Auftrag: erneutes kaltes ValidateOnly, genau ein nativer Timeout-/Nachfahren-
versuch, lesende Slot-/Dateiprüfungen, Archivierung und Dokumentationsnachträge.
Keine Gradle-/JUnit-/DOM-Tests erneut ausgeführt; 220 erfolgreiche Offlinefälle
bleiben der historische Vorbereitungslauf. Kein MangaLib-Anwendungsstart,
keine DB-Verbindung, Migration, Datensatzänderung, Installation oder Gitoperation.
Keine externen Abrufziele im Harness; Browser nur about:blank, Abnahmeproxy verweigert
alle Requests vor DNS/Connect. Der gekillte Worker liefert keinen finalen Proxy-
Zählerbericht; kein nachträglich erfundener DNS-/Connectzähler. Globale OS-Netzwerk-
und Konsolenprovenienz bleiben NOT_MEASURED, keine universelle Isolation behauptet.

Damit real nachgewiesen: reguläre produktive Reihen-/Bandimports, endliche
Browser-/Proxynegativmatrix UND gezielter Fristablauf mit Firefoxnachfahren,
gesamtem Jobabschluss und anschließender sicherer Slotfreigabe. Unaufklärbare
Cleanupfälle und persistente Quarantäne bleiben Fake-/Negativtestnachweise;
keine absichtlich offene reale Ressourcenlage erzeugt.

Nächste echte Grenze ist die geschützte Spring-/JPA-/MVC-Gesamtregression und
Konfigurationsabnahme, keine weitere synthetische OS-Testreihe erforderlich.
Diese benötigen eine gesonderte konkrete Freigabe ausschließlich für mangalib_test.
Vorher Guards/Taskplan, vor/nach derselbe bestehende geschützte Daten-/Schemasnapshot;
nur transaktionale synthetische Fixtures mit Rollback, keine Stammdaten-/Schema-
änderung, kein Live-Scraper. Profil test deaktiviert ProductionScraperClient;
application-test.properties setzt validate, init=never, generate-ddl=false und
deaktiviert Flyway/Liquibase. Diese Konfiguration wurde hier ausschließlich gelesen,
nicht durch einen DB-Lauf erneut bestätigt. Keine Verbindung zu mangalib oder
mangalib_manual_test zulassen. T02 nicht pauschal geschlossen, T03 nicht begonnen.

## Nachtrag 02.11.2026 – Abschließende geschützte Regression nach Scraperintegration

Eigentümerfreigabe ausdrücklich ausschließlich für mangalib_test. Vor Beginn:
AGENTS.md, TESTING.md, ROADMAP.md, Guard/Customizer/DataSource, Snapshotverfahren,
Gradle-Aufgaben und Testprofil erneut gelesen. Branch aus HEAD bestätigt:
codex/t02-security-hardening. MANGALIB_TEST_DB_PASSWORD vorhanden/nicht leer,
kein Wert ausgegeben. Keine SPRING-/JPA-/Hibernate-/JVM-/Gradle-Overrides in der
Prozessumgebung gefunden; keine Konfiguration oder Guards geändert.

TestContext-Customizer registriert unverändert vor Singleton-/Hibernateerzeugung
exakt die GuardedTestDataSource; alternative Beans/Overrides werden abgewiesen.
Vor JEDER Verbindung erneute effektive Prüfung und ausschließlich festes Ziel
jdbc:mysql://localhost:3306/mangalib_test?useSSL=false, mangalib_test_user,
Passwort aus MANGALIB_TEST_DB_PASSWORD. Profil ausschließlich test. ddl-auto=validate,
generate-ddl=false, init=never, Flyway/Liquibase deaktiviert. Baselinesnapshot
prüft zusätzlich database() und current_user() im geschützten Kontext.
Live-Scraper per external-network-Tag ausgeschlossen/disabled; produktiver Client
im Profil test deaktiviert. Kein nativer Owner-/Worker-/Browserstart in diesem Auftrag.

### Tatsächlich ausgeführte Schritte und Ergebnisse

Vor JEDEM Gradle-Aufruf identischer --offline --no-daemon --dry-run; Taskpläne
enthielten nur erwartete Compile/Resources/Test- beziehungsweise Assemble-Aufgaben,
keine Probes/JavaExec/bootRun/Manual-Artefakte oder Migrationen.

1. test --tests de.mangalib.testsupport.TestDatabaseGuardTest --rerun-tasks:
   44 rein datenbankfreie Guardfälle bestanden, 0 Fehler/übersprungen.
2. test --tests de.mangalib.DatabaseIntegrityBaselineTest --rerun-tasks:
   ein geschützter rein lesender Ausgangssnapshot erfolgreich.
3. test --rerun-tasks: gesamte reguläre Suite ohne Testfilter tatsächlich erneut
   ausgeführt, BUILD SUCCESSFUL in 1m 37s; 1016 Tests bestanden, 0 Fehler,
   0 übersprungen. Bestehende externe Live-Tests weiterhin per Tag ausgeschlossen;
   das ist keine zusätzliche Testfilterung oder abgeschwächte Assertion.
4. pnpm run test:dom: 198 bestanden, 0 Fehler/übersprungen/cancelled/todo,
   vorhandenes jsdom verwendet, keine Installation/Browser/externen Requests.
5. derselbe DatabaseIntegrityBaselineTest erneut mit --rerun-tasks erfolgreich;
   vorab vollständige XML-/HTML-Berichte der 1016 Fälle gesichert.
6. assemble nach eigenem identischem Offline-Dry-Run erfolgreich; keine weitere
   Testausführung/DB-Verbindung. bootJar war in aktuellem Stand UP-TO-DATE,
   jar ausgeführt. Keine Anwendung gestartet und kein Manual-JAR gebaut.

Absichtlich test --rerun-tasks STATT clean test: clean würde das verbindlich zu
erhaltende build/libs/mangalib-manual-test.jar und eingefrorene Abnahmeartefakte
löschen. Dies ist ein ungefilterter erneuter Volltest mit erneutem Compile/Test,
aber KEIN behaupteter clean-test-Lauf. Frühere Clean-Test-Nachweise bleiben
historisch erhalten; Assertions/Tests/Guards nicht geändert.

Im Volltest unter anderem: 29 MvcContractTest, 34 ReadingStatusIntegrationTest,
40 LegacyEditIntegrationTest, 48 RequestLimitsIntegrationTest und der tatsächliche
Rollback-/Datenerhaltungsnachweis TestDataPreservationTest erfolgreich. Guard44
auch im Volltest enthalten; separate Wiederholungen nicht zur Fallzahl addiert.
Die früheren 220 isolierten Worker-/Abnahme-/Paketfälle nicht erneut ausgeführt
oder zu 1016 addiert; ihr erfolgreicher Vorbereitungslauf bleibt historisch.
Keine neuen Testfehler. Bekannte Compiler-Deprecation- und HotSpot-CDS-Hinweise.

### Positiver Daten-/Schemaintegritätsnachweis

Vorher und nachher exakt:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570

Beide separaten Snapshotläufe sowie alle ausgegebenen Hash-/Zählzeilen identisch:
zehn Tabellen, 78 Stammdatensätze (62 Verlage, je 4 Status/Typen, 3 Formate,
5 Sammelbände); fünf Geschäftstabellen mangareihe/mangadetails/baende/einkaufsliste/
einkaufslistedetails weiterhin leer. Keine dauerhaft verbliebenen Fixtures.
75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel, 26 Key-Column-Einträge unverändert.
Der Gesamtfingerabdruck umfasst Werte und Metadaten, nicht nur Zeilenzahlen.
AUTO_INCREMENT wie dokumentiert ausgenommen, nicht zurückgesetzt.

Schreiboperationen ausschließlich vorhandene transaktionale synthetische Fixtures
mit Rollback und bestehenden @AfterTransaction-Snapshotprüfungen. Keine
Stammdatenänderung, Datenbereinigung, Schemaerzeugung, Migration oder Seeding.
Keine Verbindung zu mangalib oder mangalib_manual_test. Keine direkten SQL-/JDBC-
Werkzeuge: Snapshots ausschließlich über den bestehenden geschützten JPA-Pfad.

### Pakete, Evidenzen und Abschlussgrenzen

Boot-JAR zusätzlich statisch geprüft: ProductionScraperClient/WorkerJobOwner und
JNA 5.17.0 vorhanden; Selenium-/WebDriverManager-/jna-platform-Runtimebibliotheken
sowie direkte Live-Extraktoren/Browser-/Proxyklassen im Anwendungs-Boot-JAR nicht
vorhanden. Produktworker hashgleich zum erfolgreichen Live-Versuch:
56647015763c5d9a445387fd850e7abf9d2068a6949bbf7f30efcd113a7a9268.
Manual-JAR weiterhin 9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Ergebnisdateien der drei SCOPED_PASS-OS-Abnahmen erneut unverändert hashgeprüft.
Keine historischen Evidenzen/Grants oder Verbrauchsmarker verändert.

Lokale Belege .gradle/t02-final-integration-20261102/: guard.xml, before.xml,
after.xml, comparison.txt, full-test-results/, full-test-report/,
full-test-counts.json, dom.txt, artifact-integrity.txt. Nach separatem Abschlusslauf
zeigt build/reports/tests/test nur den Baselinefall; Vollbericht im obigen Archiv.
Geändert nur Dokumentationsnachträge in TESTING.md, ROADMAP.md und MANUAL_TESTING.md.
Keine Anwendungscode-, Dependency-, Guard-, Sicherheitsprofil- oder Modelländerung.
Keine Gitoperationen; lokale Whitespace-/Konfliktmarkerprüfung der Nachträge.

Der aktuelle Scraperimplementierungsumfang ist damit im geprüften Umfang real
und automatisiert regressionsvalidiert. Keine weitere synthetische Prozessprobe
allein zur Wiederholung bestehender Nachweise erforderlich. Testprofil schützt
bewusst vor Live-Ausführung: kein behaupteter produktiver Spring-/HTTP-zu-Live-
End-to-End-Start. Produktive Startkonfiguration/Deployment bleiben separate Aktionen.
Globale OS-Netzwerkisolierung nicht nachgewiesen und nicht behauptet.

T02 insgesamt nicht pauschal schließen: bestehende offene Browser-/UX-Abnahme
(insbesondere Requestlimit-/413-Anzeige/Eingabenerhalt/Replay-Sperre), Reichweite
synthetischer Altbestandsnachweise und dokumentierte Dependency-/Diagnosebefunde
im Abschlussreview abgleichen. Diese Punkte wurden durch einen erfolgreichen
Scraper-/JUnitlauf nicht automatisch erledigt. Keine neuen Browseraktionen oder
produktiven Bestandsprüfungen in diesem Auftrag. T03/T04/T06 nicht vorgezogen.

## Nachtrag 02.11.2026 – T02 konsolidiert, gezielte Feedbackkorrektur und T03 vorbereitet

Verbindlicher aktueller Status: T02_CLOSEOUT.md. Technisch validierte Teilbereiche,
historisch überholte Sperren, bewusst begrenzte Nachweise und noch offene echte
Abnahmekriterien dort getrennt. Kein pauschaler T02-Abschluss und deshalb kein
Staging/Abschlusscommit. T03_IMPLEMENTATION_PLAN.md ist nur statische Vorbereitung.

Kleiner belegter Codebefund: MangaLibFeedback fehlten eigene feste Texte für HTTP
413 und 415. Zwei neutrale Texte ergänzt; keine Auswertung von Responsebodys,
keine Änderung von Payloads, CSRF/Host/Origin, Replay- oder Speichersperrsemantik.
413 bleibt bis Reload gesperrt, 415 bleibt kontrolliert korrigierbar. Acht neue
Fälle in write-feedback.test.cjs (beide Statuscodes in vier Dialogpfaden) prüfen
Text, Eingabenerhalt, Dialogzustand, keine alerts, keine internen Inhalte,
CSRF-Übertragung und bei Sperre keinen zweiten Request. Bestehende Assertions
nicht abgeschwächt. node --check für beide Dateien erfolgreich; pnpm run test:dom:
206 bestanden, 0 Fehler/skipped/cancelled/todo. Keine echte Browserausführung.

Kein Java-/DB-Test wiederholt: die letzte ausdrücklich freigegebene Regression
mit 1016 Java-/198 DOMfällen und identischem Gesamtfingerabdruck bleibt historisch
unverändert. Die erneute DB-Freigabe war ausdrücklich NICHT Teil dieses Auftrags.
Keine Stammdaten-/Bestands-, Schema-, SQL-, Migrations- oder Bereinigungsaktion.

Für die offene UI-Abnahme separat benanntes Artefakt gebaut, NICHT gestartet:
build/libs/mangalib-manual-test-t02-closeout.jar, SHA256
e89e4e8b2947082892ac847ae842793faa63c96f4422d55638508b1c4553fa1f.
Reiner vorhandener manualBootJar-Bau mit lokalem Init-Override des Archivnamens;
Gradle --offline --no-daemon und identischer vorheriger --dry-run. Taskplan nur
Compile/Resources/manualBootJar, keine Tests/JavaExec/Anwendung/DB. BUILD SUCCESSFUL.
Init-Datei und DOM-Ausgabe lokal ignoriert unter .gradle/t02-consolidation-20261102/.
Keine zusätzliche Anwendungskomponente, Abhängigkeit oder Framework eingeführt.
Kein manualSafetyTest ausgeführt: dessen Artefaktprüfung enthält einen JVM-Start;
solche Ausführung nicht aus dieser Konsolidierung abgeleitet.

Statisch im neuen JAR bestätigt: aktuelle security.js, erforderliche manuelle
Doubles/Guards, keine Live-Scraper/Selenium/Worker-Ownerklassen. Alle bisherigen
manuellen Guard-/Doubleklassen bytegleich. Original-Manual-JAR unverändert
9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9.
Neue negative UI-Abnahme sowie Start/ValidateOnly und Zugriff auf mangalib_manual_test
bedürfen separater Freigabe; Start-ManualTest.ps1 unterstützt den neuen Artefaktpfad.
Details/Abbruchregeln in T02_CLOSEOUT.md und MANUAL_TESTING.md.

Gitprüfung jetzt ausdrücklich erlaubt: Branch korrekt, anfangs 23 geänderte und
173 unversionierte Dateien, Index leer. Vollständige Statusliste lokal gesichert;
keine Staging-/Commit-/Push-/Merge-/Branchwechseloperation. git diff --check
bisher erfolgreich; reine LF/CRLF-Hinweise sind keine Whitespacefehler.
Ignore-Regeln für SECURITY_AUDIT.md/.env/.gradle/build geprüft; Sicherheitsbericht
nicht verfolgt und nicht verändert. Gitleaks nicht vorhanden; begrenzte lokale
Secret-Mustersuche der anfänglichen 196 Kandidaten ohne Fund, kein behaupteter
vollständiger Secret-/Historien-Scan. Keine Sensitivwerte ausgegeben. Vollständige
Commit-Auswahl-/Dokumentationshygieneprüfung bleibt vor späterem Staging nötig.

Offene Abschlusskriterien nicht versteckt verschoben: echte 413-/Replay-UX und
Bewertung der bereits dokumentierten Dependency-Sicherheitsbefunde. Das vorhandene
Boot-JAR enthält Tomcat 11.0.24 und jsoup 1.17.2; keine aktuelle externe Advisory-
prüfung/Downloads oder neue Exploitbehauptung. T03-/T04-/T05-/T06-Abgrenzungen und
M06-Regeln bleiben erhalten. Keine zusätzlichen Windows-/Browser-/Live-Scraper-
Versuche, Anwendung, Installationen oder Datenbankverbindungen durchgeführt.

## Nachtrag 02.11.2026 – Dependency-Abschluss und erneute geschützte Regression

Der Eigentümer gab offizielle Herstellerabfragen, genau drei Tomcat-11.0.27-
Module von Maven Central und anschließend eine erneute mangalib_test-Regression
getrennt frei. Aktuelle Bewertung: DEPENDENCY_SECURITY_REVIEW.md. Historische
Aussagen zur noch nicht erfolgten Advisoryprüfung gelten nur für ihren damaligen
Stand. Boot 4.1.1, Java 21 und Gradle 8.14.3 bleiben unverändert.

Gemeinsamer tomcat.version-Override auf 11.0.27; ungenutztes jsoup 1.17.2 und
WebDriverManager 5.7.0 entfernt. Selenium nur Firefox/Support aus Boot-BOM 4.43.0,
keine alten Aggregator-/DevTools-Browsermodule. Alle 6784 erhaltenen Workerklassen
bytegleich, keine neue/geänderte Klasse. Pakettests sichern unerwünschte Pakete
und genau die drei Tomcat-11.0.27-Module ab. Produktive Import-/Proxy-/Joblogik,
DB-Guards und Sicherheitsprofile unverändert.

Tatsächlich ausgeführt, jeweils nach identischem vorherigem Gradle-Dry-Run:
- Offline-Kompilierung und 23 datenbankfreie Paket-/Scraperpolicytests bestanden.
- Zuerst 44 datenbankfreie TestDatabaseGuardTest-Fälle bestanden.
- Separater lesender Vorher-Fingerabdruck über den geschützten Testkontext.
- Vollständiges ungefiltertes test --rerun-tasks: 1016 bestanden, 0 Fehler,
  0 übersprungen; darunter alle 29 MVC-/JSON-Verträge.
- Separater lesender Nachher-Fingerabdruck identisch, alle 16 Ausgabezeilen gleich.
- pnpm run test:dom: 206 bestanden, 0 Fehler/skipped/cancelled/todo.

Fingerabdruck vor/nach:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Zehn Tabellen, 78 Stammdaten, fünf leere Geschäftstabellen; 75 Spalten, 24
Indexeinträge, 12 Fremdschlüssel und 26 Schlüsselspalten unverändert. Keine
Fixtures verblieben, AUTO_INCREMENT weiterhin ausgenommen. Kein SQL-/JDBC-
Werkzeug; ausschließlich vorhandenes TestDatabaseSnapshot über JPA und Guards.
Passwort vorhanden/nicht leer geprüft, nie ausgegeben; keine verdächtigen
Framework-/JVM-Overrides. Keine Produktiv-/Manual-DB, Migration, Initialisierung,
Bestandskorrektur oder Datenbereinigung.

Kein clean, damit eingefrorene JARs erhalten bleiben; alle Tests tatsächlich
mit --rerun-tasks erneut ausgeführt. Offline/no-daemon für sämtliche Builds/
Tests; ausdrücklich genehmigte Ausnahme nur reiner Tomcat-Nachladetask mit drei
nichttransitiven Koordinaten, ausschließlich repo.maven.apache.org. Bekannte
Deprecation-/HotSpot-CDS-Hinweise bleiben; keine Kompatibilitätsfehler beobachtet.
Die erste lokale XML-Vergleichsprüfung las CDATA falsch als Objekt; nach Korrektur
auf InnerText war die bereits korrekte Baseline bestätigt, keine DB-Abweichung.

Berichte dieser neuen Runde separat unter dem lokal ignorierten Verzeichnis
.gradle/t02-dependency-integration-20261102/ (Guard, vollständige XML/HTML-Berichte,
Vorher/Nachher, Vergleich, DOM); Paket-/Dependency-Nachweise unter
.gradle/t02-dependency-closeout-20261102/. Alte Evidenzen nicht überschrieben.
Kein neuer nativer Versuch, Browser, Live-Scraper oder Anwendungslistener.
T02 bleibt bis zur echten negativen 413-/415-/Replay-UI-Abnahme offen; kein
Abschlusscommit/Staging. T03_IMPLEMENTATION_PLAN.md unverändert, keine T03-Arbeit.

## Nachtrag 02.11.2026 – Freigegebene echte 413-UI-Probe; 415-Werkzeuggrenze

Nach zusätzlicher ausdrücklicher Eigentümerfreigabe den gepatchten Kandidaten
2b4859de35e3437adf2cc59db8028043860c9b7606d40f0d0b3845e64d0d41e2
mit dem vorhandenen Startskript erst ValidateOnly geprüft, dann genau einmal
als manual-test gestartet. PID 34056, Java 21, gebundenes JAR und ausschließlich
127.0.0.1:18080 bestätigt; sichtbares Testbanner. Kein weiterer Listener dieses
Prozesses. CIM-Lesen war in der Sandbox gesperrt, mit genehmigter lesender
Berechtigung erfolgreich geprüft; keine Prozess-/Sicherheitseinstellungen geändert.

Vor/nach über das ausdrücklich mitfreigegebene vorhandene manuelle Snapshot-
verfahren: identischer SHA256
2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226.
Elf Reihen, 28 Bände, fünf Einkaufspositionen samt Details sowie sämtliche
Stamm-/Schemaabschnitte unverändert. Dieses vorhandene Skript verwendet den
festen manuellen Benutzer/Zielhost und eine READ-ONLY-Transaktion mit SELECTs
und abschließendem ROLLBACK über mysql.exe; keine freien SQL-Werkzeuge/Befehle,
keine Datenänderung. Skript nur in neues ignoriertes Evidenzverzeichnis kopiert,
keine historischen Evidenzen verändert. Kein Produktiv-/mangalib_test-Zugriff
während dieser manuellen Abnahme, keine Normalisierung/Seeding/Migration.

Realer In-app-Browser, regulärer Anlagedialog: genau eine Save-Bedienung mit
4097 Zeichen Titel, zusätzlich unabhängig ungültiger Bandanzahl 0 und ansonsten
synthetischen Werten. Technische Größenlimit-Meldung sichtbar, Titel/Anzahl/Preis
unverändert im Dialog, Save deaktiviert; Cancel und Wiederöffnung funktionieren,
Sperre bleibt erhalten. Bewusster Reload gibt Save wieder frei. Kein zweiter
Schreibversuch, kein nativer JS-Dialog, keine error/warn-Konsoleneinträge, keine
neue Reihe sichtbar. Snapshotvergleich bestätigt sämtliche Daten unverändert.
Keine HTTP-Status-/Requestanzahl-Messung über diesen Connector verfügbar;
413 nicht allein aus der Meldung als gemessener Status ausgeben. Serverstatus-
Verträge bleiben separat durch die erfolgreiche MockMvc-Regression abgesichert.
Screenshot lokal ignoriert unter .gradle/t02-ui-closeout-20261102/ui-413.jpg.

415 ist im echten Browser NICHT geprüft: reguläre UI sendet nur JSON, und die
angebotene Connector-evaluate-Schnittstelle ist ausdrücklich lesend. Kein
Request-Interception-/Headerwechsel-API vorhanden. Keine Umgehung über fremde
Werkzeuge, DevTools-Injektion oder neue Testseite. Eigentümerprobe erforderlich;
konkreter sicherer Ablauf im neuen MANUAL_TESTING.md-Nachtrag. Das ist eine
Werkzeuggrenze, kein nachgewiesener Anwendungsfehler. T02 noch offen, kein Commit.

Anschließend Testtab geschlossen und ausschließlich eigene Startsession per
Ctrl+C beendet: Tomcat meldete Graceful shutdown complete, JPA geschlossen,
PID 34056 nicht mehr vorhanden, Port 18080 frei. Kein erzwungenes Beenden.
Alle drei manuellen JARs hashgleich erhalten. Keine Live-Scraper/Autofill-/Kauf-/
Löschaktionen oder gültigen Speicheraktionen. Keine zusätzliche Java-/DOM-Suite
nach diesen reinen Browser-/Dokumentationsschritten erforderlich/ausgeführt.

## Abschlussnachtrag 02.11.2026 – T02 lokal technisch abgeschlossen

Der Projekteigentümer bestätigte die letzte manuelle HTTP-415-Abnahme:
415 Unsupported Media Type direkt beobachtet, erwartete verständliche Meldung,
kein automatisches Replay, Daten-/Schema-Snapshot vor/nach identisch, Instanz
kontrolliert beendet, Port 18080 frei. Dies sind ausdrücklich Beobachtungen des
Projekteigentümers; Codex hat diesen 415-Browserfall NICHT ausgeführt. Kein neuer
Snapshot-Hash für diesen Eigentümerlauf übermittelt oder von Codex erfunden.

Die zuvor von Codex ausgeführte echte 413-UI-Probe bleibt innerhalb ihrer Grenze
bestätigt: verständlicher Text, Eingaben erhalten, Sperre bis Reload. Der
HTTP-Status wurde nicht unmittelbar beobachtet und wird nicht aus dem UI-Text
als gemessen ausgegeben. Die historischen Serverstatus-Vertragstests bleiben
separat zugeordnet.

T02 ist für das freigegebene lokale Windows-Einzelbenutzermodell technisch
abgeschlossen. Aktuelle Matrix: T02_CLOSEOUT.md. Bestätigter automatisierter
Stand: 1016 Javafälle einschließlich aller 29 MVC-/JSON-Verträge und 206 DOMfälle;
keine Fehler/übersprungenen Fälle. Vorgeschaltete 44 Guards und separate 23 Paket-/
Policyfälle erfolgreich. mangalib_test-Daten-/Schemafingerabdruck vor/nach:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Zehn Tabellen und 78 Stammdatensätze unverändert, keine Fixtures; AUTO_INCREMENT
ausgenommen. Diese Ergebnisse sind vorhandene Nachweise, keine neuen Testläufe.

Im abschließenden Auftrag nur statischer Repository-/Diff-/Dateiauswahlreview,
Dokumentation und ausdrücklich freigegebener lokaler Git-Abschluss. Keine neuen
Tests/Builds, DB-Verbindungen, Anwendungen, Browser, Native-/Scraperläufe,
Installationen oder Netzwerkzugriffe. Private Benutzerpfade in vier Hilfsquellen
auf Benutzerprofilableitung umgestellt; identisches lokales JNA-Ziel und feste
Hash-/Manifestprüfungen erhalten. Alle 25 neuen PowerShellquellen nur statisch
geparst, null Syntaxfehler. Kein Hilfsskript ausgeführt; historische Originale
lokal ignoriert gesichert. Produktive Quelllogik unverändert im Abschlussauftrag.

Original-Manual-JAR, beide separaten Manual-Kandidaten und die drei maßgeblichen
Boundary-/Live-/Timeout-Ergebnisdateien mit vorhandenen Hashes unverändert
bestätigt. DB-Guards/-Profile und manualTest-Quellen gegenüber Ausgangsbasis
unverändert. Keine historischen PARTIAL/STOP-Nachweise aufgewertet.
Begrenzte lokale Secret-/Privatpfad-Mustersuche statt vollständigem Gitleaks-
Scan (Tool nicht vorhanden); keine neue Installation. Kein Klartextfund im
Commitumfang; synthetische example.invalid-URL-Credentials sind Testeingaben.

Keine globale OS-Netzwerkisolierung, vollständige Prozessprovenienz, allgemeine
Schwachstellenfreiheit oder produktive Bestandsvollprüfung behaupten. Rohe
CL+TE-Ablehnung zurückgestellt, Logging T06, LAN separat. M06 erhalten; T03-Plan
nur Vorbereitung. Nächster Auftrag T03-0: datenbankfreie Vertrags-/Fehlerfälle
charakterisieren, offene Kauf-/Idempotenz-/Reduktionsregeln vor Umsetzung klären.

Abschließende statische Dateiauswahl: 192 Commitdateien (24 geändert, 168 neu),
darunter 157 Java-, 22 PowerShell-, sechs Markdown-, drei JavaScript-, zwei
CJS-, eine HTML- und eine Kotlin-DSL-Datei. Alle vorhandenen produktiven
Source-Set-Abhängigkeiten samt zugehörigen Tests bleiben enthalten. Acht ältere
nicht angebundene Prototypdateien bleiben unversioniert; nichts gelöscht.

Gesamter gestagter Diff lokal geprüft: keine Binärartefakte, Datenbankinhalte,
Logs, Grants/Marker oder privaten Sicherheitsberichte aufgenommen; begrenzte
Mustersuche sämtlicher ausgewählter Dateiinhalte ohne Secret-/Privatpfadfund.
Die entfernte private Pfadzeile in MANUAL_TESTING.md war bereits in der
Ausgangsbasis versioniert; ihre Entfernung bereinigt den aktuellen Baum, keine
History-Umschreibung. Indexprüfung und git diff --cached --check erfolgreich.
Die lokale Musterprüfung ersetzt keinen vollständigen Historien-Secret-Scanner.

## Nachtrag 02.11.2026 – T03-0 ausschließlich datenbankfreie Charakterisierung

Ausgangszustand exakt bestätigt: Branch codex/t02-security-hardening, HEAD
b7a2dbe, kein geänderter verfolgter Inhalt, acht bekannte unversionierte historische
Prototypdateien. Danach ausdrücklich freigegebenen Branch codex/t03-data-integrity
direkt ab b7a2dbe erstellt. Kein Reset/Stash/Clean, keine Änderung des T02-Branches.

Neue Tests: service/PurchaseFlowCharacterizationTest (24 Fälle) und
service/BandIntegrityCharacterizationTest (29 Fälle), gemeinsame synthetische
T03Fixtures. Echte Controller/Services, ausschließlich Repository-/Servicemocks
und begrenzte zwei Java-Threads mit deterministischer Barrier. Keine Spring-
Kontexte, Proxys, JDBC oder Netzwerkfunktion. Fake-Mutationen sind kein Nachweis
von DB-Commits/Rollbacks oder tatsächlich wirksamen SQL-Locks.

53 neue Fälle dokumentieren Ist-Abweichungen als erfolgreiche Charakterisierung:
Bibliotheksaufruf vor fehlerhafter Kaufmarkierung, erneute/gleichzeitige Übernahme,
erster mehrdeutiger Index-/Titeltreffer, NULL-Index als 0, ignoriertes leeres
Updateergebnis, Rundung 0,01/−0,01/0,02 auf drei Bände, ArithmeticException im
Bestandsupdate, Listenpositionszuordnung, Reduktion ohne Löschung mit unpassendem
Aggregat und unterschiedliche NULL-/0-Semantik. Bestehende M06-/T02-Grenzen und
korrekte Pfade separat positiv geprüft. Kein Fehlverhalten dadurch behoben;
keine deaktivierten/absichtlich roten Tests. Fachentscheidungen im T03-Plan.

Abschließender tatsächlicher Lauf: 280 bestanden, 0 Fehler, 0 übersprungen:
24 Kauf- + 29 Bandcharakterisierungen, 40 ReadingStatusExtensionTest,
51 LegacyEditCharacterizationTest, 38 WriteServiceSafetyTest und
98 WriteMvcValidationTest (Standalone-MockMvc mit aktiven T02-Filtern).
Vor jedem Gradle-Aufruf identischer Taskplan mit --dry-run, stets --offline
--no-daemon. Nur Compile/Resources/testClasses/test im Taskplan, kein BootRun,
JavaExec, manuelles JAR oder Integrationsfilter. Erfolgreiche Offline-Kompilierung.

Reproduzierbare Auswahl (erst identisch mit zusätzlichem --dry-run prüfen):

```powershell
.\gradlew.bat --offline --no-daemon test `
  --tests de.mangalib.service.PurchaseFlowCharacterizationTest `
  --tests de.mangalib.service.BandIntegrityCharacterizationTest `
  --tests de.mangalib.service.ReadingStatusExtensionTest `
  --tests de.mangalib.validation.LegacyEditCharacterizationTest `
  --tests de.mangalib.validation.WriteServiceSafetyTest `
  --tests de.mangalib.validation.WriteMvcValidationTest
```

Frühere eigene Testaufbaufehler korrigiert: expliziter Long-Typ für JUnit-
Argumentassertions; Mockito doThrow statt Aufruf eines bestehenden Answers beim
Neustubben; Fixturehelper sendet auch bei fehlenden Details ein gültiges
synthetisches Requestmodell statt selbst NPE zu werfen. Keine Produktionslogik,
bestehenden Assertions oder Sicherheitskonfigurationen dafür verändert. Nach
Korrekturen vollständige obige Auswahl grün. Berichte lokal ignoriert unter
.gradle/t03-characterization-20261102/; keine historischen Evidenzen überschrieben.
Bekannter HotSpot-CDS-Hinweis, keine Kompilierungswarnung neu verursacht.

Keine DB-/Spring-Boot-Integration, Vollsuite, clean, Anwendung, Listener, Browser,
Native-Probe oder Live-Scraper; keine Downloads/Installationen. Keine Fingerabdrücke
oder DB-Zustände neu erhoben. Vorhandener Manual-JAR und historische unversionierte
Dateien unverändert. Kein Staging, Commit, Push oder Merge. Produktion vollständig
unverändert, T03-1 nicht begonnen. Vor echter DB-/Parallelitätsabnahme neue,
konkrete Freigabe erforderlich, insbesondere für committed synthetische Fixtures
und deren eng begrenzte Entfernung. Bestehende Rollbackfreigaben nicht ausweiten.


## Nachtrag 02.11.2026 – T03-1/T03-2 datenbankfrei implementiert

Status `IMPLEMENTED_DB_FREE_VERIFIED`; keine echte Commit-/JPA-/Browserabnahme.
Maßgebliche Regeln/Schreibpfadinventar: T03_IMPLEMENTATION_PLAN.md, Abschnitt 11.
D1–D6 wurden vom Eigentümer entschieden; zusätzlich fester 200-/409-Vertrag und
explizite Bestätigung des neuen Bandnummernbereichs freigegeben.

Beide Kaufwege delegieren an PurchaseService. Gemeinsamer JVM-Mutex VOR eigener
TransactionTemplate-Transaktion; Einkaufslock vor Reihenlock. Bestehende öffentliche
Schreibmethoden der fünf Bibliotheks-/Einkaufsservices werden über einen äußeren
Spring-Infrastruktur-Advisor koordiniert. Normale Neuanlage darf den Mutex und die
Dublettenprüfung nicht umgehen. Keine Netzwerkarbeit im Mutex. Unbestätigtes
Transaktionsende sperrt weitere koordinierte Writes; keine automatische Wiederholung.

Endgültiger selektiver Lauf: **462 bestanden, 0 Fehler, 0 übersprungen**.

| Klasse | Bestanden | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| LocalSecurityPolicyTest | 61 | 0 | 0 |
| BandIntegrityCharacterizationTest | 29 | 0 | 0 |
| LibraryWriteConfigurationTest | 8 | 0 | 0 |
| LibraryWriteCoordinatorTest | 12 | 0 | 0 |
| PurchaseFlowCharacterizationTest | 27 | 0 | 0 |
| PurchaseServiceTest | 46 | 0 | 0 |
| ReadingStatusExtensionTest | 40 | 0 | 0 |
| TestDatabaseGuardTest | 44 | 0 | 0 |
| LegacyEditCharacterizationTest | 51 | 0 | 0 |
| WriteMvcValidationTest | 106 | 0 | 0 |
| WriteServiceSafetyTest | 38 | 0 | 0 |

Ausgeführt nach identischem erfolgreichem Offline-Dry-Run und statischer Prüfung
aller ausgewählten Klassen/Tasks. JAVA_HOME nur für Java 21 gesetzt, kein Passwort
benötigt/verwendet. Keine Boot-Testkontexte oder DataSource-Beans in dieser Auswahl.
Der kleine AOP-Test verwendet GenericApplicationContext, echte Spring-Proxys und
einen Fake-Transaktionsmanager, ohne Autokonfiguration, JPA oder JDBC.

```powershell
.\gradlew.bat --offline --no-daemon test `
  --tests de.mangalib.service.LibraryWriteCoordinatorTest `
  --tests de.mangalib.service.LibraryWriteConfigurationTest `
  --tests de.mangalib.service.PurchaseServiceTest `
  --tests de.mangalib.service.PurchaseFlowCharacterizationTest `
  --tests de.mangalib.service.BandIntegrityCharacterizationTest `
  --tests de.mangalib.service.ReadingStatusExtensionTest `
  --tests de.mangalib.validation.WriteMvcValidationTest `
  --tests de.mangalib.validation.WriteServiceSafetyTest `
  --tests de.mangalib.validation.LegacyEditCharacterizationTest `
  --tests de.mangalib.security.LocalSecurityPolicyTest `
  --tests de.mangalib.testsupport.TestDatabaseGuardTest
```

Vor jedem tatsächlichen Gradle-Aufruf exakt dieser Aufruf mit angehängtem
`--dry-run`. Plan: compileProductionOwnerJava, processProductionOwnerResources,
productionOwnerClasses, compileJava, processResources, classes, compileTestJava,
processTestResources, testClasses, test. Keine JAR-, Manual-, Host-, Windows-,
Scraper- oder Integrationstesttasks. Compile/Test erfolgreich. Nur die bekannte
Mockito/HotSpot-CDS-Warnung; zwischenzeitlich im neuen Test verwendeten veralteten
TransactionInterceptor-Konstruktor ersetzt, im endgültigen Lauf kein entsprechender
Deprecation-Hinweis mehr.

Zusätzlich **225 DOM-Tests bestanden**, 0 Fehler/übersprungen, mit
`pnpm run test:dom` (bestehende jsdom-Abhängigkeit, keine Installation).
19 neue DOM-Fälle: sechs feste 409-Kürzel, unbekannte/maliziöse Header, Vorschau
und Cancel, explizite Bestätigung mit CSRF und Doubleclick-Sperre, veraltete Bereiche,
unbekannter 500-Erfolg mit Sperre, bereits gekauft, 417->Bestätigung und ungültige
Antwortfelder. Fehlerbodys werden nicht gelesen. Beide geänderten JS-Dateien
zusätzlich `node --check` erfolgreich. Keine echten Browser-/Netzwerkzugriffe.

Die anfänglichen Fehlläufe wurden untersucht: zwei neue Neuanlage-Fixtures ohne
Status-/Verlags-/Typ-/Format-Repository-Stubs (danach vollständig ergänzt), eine
JUnit-Generik-Kompilierung korrigiert sowie zwei neue DOM-Erwartungen an Fixture-ID
und absolute Fetch-URL korrigiert. Zusätzlich einen deterministischen Konkurrenzfall zweier unterschiedlicher Käufe
derselben neuen Reihe ergänzt: genau eine Neuanlage, zweiter Kauf erst nach Commit
als ausdrückliche Append-Vorschau. Sein anfänglich fehlerhaftes Mockito-Restubbing
und die synthetische MapsId-/Inverse-Ladung korrigiert; endgültig erfolgreich.
Keine fachlichen Assertions geschwächt und keine
Fehler übersprungen. Historische T03-0-Reports unverändert; frühere IST-Kauffehler
zu expliziten Sollfällen weiterentwickelt, globale Band-/Preisfehler weiterhin
als IST dokumentiert. Lokale aktuelle Reports unter
`.gradle/t03-purchase-20261102/`, nicht für Git bestimmt.

**Noch nicht ausgeführt / zwingende Nachweisgrenzen:**

- Keine vollständige `test`-/`clean test`-/Buildsuite, keine Datenbank-Fingerabdrücke
  und keine Verbindung zu irgendeiner Datenbank. Frühere Integrationszahlen sind
  keine erneute Bestätigung dieses Arbeitsstands.
- Mutex-/Transaktions-Fakes beweisen Aufruf- und Completionreihenfolge, nicht die
  Wiederherstellung von RAM-Objekten oder echte DB-Commits/Rowlocks/Rollbacks.
- Der Coordinator weist fremde äußere Transaktionen vor Writes ab. Bestehende
  `@Transactional`-Schreibtests (ServiceTest, Kauf-/M06-MVC-Fälle) müssen VOR der
  nächsten Vollsuite auf die echte eigene Commit-Grenze abgestimmt werden.
  Nicht einfach die Annotation entfernen: dauerhafte Fixtures wären die Folge.
  Kein Guard-/Testprofil-Sonderbypass und keine Abschwächung vorhandener Assertions.
- T03-6 erhält einen gesonderten Opt-in-Plan mit wenigen neuen synthetischen IDs,
  echten unabhängigen Transaktionen, Flush/Clear/Reload und Parallelität. Gesonderte
  Eigentümerfreigabe ausschließlich mangalib_test erforderlich; keine automatische
  Bereinigung/Entfernung oder Änderungen vorhandener Zeilen.
- Keine Browser-/Anwendungs-/HTTP-/Scraper-/nativen Windowsstarts, keine Downloads,
  Migrationen, neuen Abhängigkeiten, Staging- oder Commitaktionen.
- Acht historische unversionierte Experimente und alle drei bestehenden Manual-JAR-
  Hashes unverändert überprüft. T02-Guards/Filter und historische Evidenzen erhalten.

Für die neue manuelle Bedienabnahme siehe MANUAL_TESTING.md, T03-1/T03-2-Nachtrag.
T03-3/4/5 und T04/T05/T06 nicht als abgeschlossen darstellen.


### Dateiumfang und Abschlusskontrolle dieses Arbeitsstands

Vollständige Liste der 23 T03-Projektdateien gegenüber T02-HEAD b7a2dbe
(einschließlich der erhaltenen uncommitteten T03-0-Arbeiten):

- `MANUAL_TESTING.md`
- `ROADMAP.md`
- `T03_IMPLEMENTATION_PLAN.md`
- `TESTING.md`
- `src/main/java/de/mangalib/controller/EinkaufslisteController.java`
- `src/main/java/de/mangalib/repository/EinkaufslisteRepository.java`
- `src/main/java/de/mangalib/repository/MangaReiheRepository.java`
- `src/main/java/de/mangalib/service/MangaReiheService.java`
- `src/main/resources/static/js/einkaufsliste.js`
- `src/main/resources/static/js/security.js`
- `src/test/java/de/mangalib/service/ReadingStatusExtensionTest.java`
- `src/test/java/de/mangalib/validation/WriteMvcValidationTest.java`
- `src/test/js/write-feedback.test.cjs`
- `src/main/java/de/mangalib/service/LibraryWriteConfiguration.java`
- `src/main/java/de/mangalib/service/LibraryWriteCoordinator.java`
- `src/main/java/de/mangalib/service/PurchaseService.java`
- `src/test/java/de/mangalib/service/BandIntegrityCharacterizationTest.java`
- `src/test/java/de/mangalib/service/FakeLibraryTransactionManager.java`
- `src/test/java/de/mangalib/service/LibraryWriteConfigurationTest.java`
- `src/test/java/de/mangalib/service/LibraryWriteCoordinatorTest.java`
- `src/test/java/de/mangalib/service/PurchaseFlowCharacterizationTest.java`
- `src/test/java/de/mangalib/service/PurchaseServiceTest.java`
- `src/test/java/de/mangalib/service/T03Fixtures.java`


`git diff --check` erfolgreich, Staging-Bereich leer. Git meldet lediglich die
bestehende Windows-LF/CRLF-Konvertierung, keinen Diff-Whitespacefehler. Zusätzlich
alle neuen unversionierten Java-Dateien auf UTF-8-BOM und Zeilenend-Whitespace
geprüft: keine neuen Befunde. Historisch vorhandene Whitespacezeilen im unveränderten
Umfeld von MangaReiheService nicht nebenbei bereinigt. Acht historische Dateien
unter experiments/windows-job-prototype weiterhin unverändert/unversioniert.
Branch codex/t03-data-integrity, HEAD b7a2dbe; kein Staging, Commit, Push oder Merge.


## Nachtrag 03.11.2026 – gezieltes undici-Sicherheitsupdate

Status **SECURITY_FIX_VERIFIED** für die npm-Projektauflösung. Eigenständiger
Sicherheitsfix zwischen T03-1/T03-2 und T03-3; keine fachlichen Änderungen.

- Ursache: reine Entwicklungs-/DOM-Testabhängigkeit
  `mangalib-dom-tests -> jsdom 30.1.2 -> undici 8.11.2`, genau eine Paketversion.
  jsdom verlangt `^8.11.2`; der vorhandene Bereich erlaubt das Sicherheitsupdate.
- Regulär und ausschließlich undici auf **8.12.3** aktualisiert. Kein Override,
  kein jsdom-/Major-/Java-/Spring-Update. package.json unverändert; im Lockfile
  nur Version, Integrität und zugehörige undici-Verweise verändert (4/4 Zeilen).
  Patch 8.12.3 enthält die Korrekturen aus 8.12.2 und zusätzliche HTTP/2-Timeout-/
  Pool-Kontextkorrekturen laut offizieller Releasebeschreibung.
- Offizielle Registry `https://registry.npmjs.org/` vorab bestätigt. Zuerst rein
  offline geprüft: alter Metadatencache lieferte noch 8.11.2, neuer Tarball fehlte.
  Anschließend gezielte Metadatenauflösung und Frozen-Installation: 35 vorhandene
  Pakete aus Cache wiederverwendet, genau ein Paket (undici) heruntergeladen.
  Lifecycle-Skripte durchgehend mit `--ignore-scripts` deaktiviert.
- Node v24.22.1 und pnpm 11.25.0 unverändert. undici verlangt >=22.19.0;
  Projekt/jsdom verlangen ^22.22.2 || ^24.15.0 || >=26.0.0. Aktuelle Laufzeit passt.
  Node-internes undici separat 7.30.1, nicht durch diesen Lockfile geändert.
  Keine Behauptung eines allgemeinen Node-/System-Sicherheitsaudits.

Offizielle Herstelleradvisories, am 03.11.2026 abgeglichen:

| CVE | Hersteller-Advisory | Betroffene 8.x-Versionen / erste Korrektur |
| --- | --- | --- |
| CVE-2026-109869 | [GHSA-xjj6-f33q-929c](https://github.com/nodejs/undici/security/advisories/GHSA-xjj6-f33q-929c) | >=8.0.0,<8.12.2 / 8.12.2 |
| CVE-2026-109383 | [GHSA-2m5v-pch6-74hx](https://github.com/nodejs/undici/security/advisories/GHSA-2m5v-pch6-74hx) | >=8.0.0,<8.12.2 / 8.12.2 |
| CVE-2026-87018 | [GHSA-qrxm-fqhp-jwvm](https://github.com/nodejs/undici/security/advisories/GHSA-qrxm-fqhp-jwvm) | >=8.0.0,<8.12.2 / 8.12.2 |
| CVE-2026-109932 | [GHSA-9fm3-97px-rjf6](https://github.com/nodejs/undici/security/advisories/GHSA-9fm3-97px-rjf6) | >=8.0.0,<8.12.2 / 8.12.2 |

Offizielle Releases: [8.12.2](https://github.com/nodejs/undici/releases/tag/v8.12.2),
[8.12.3](https://github.com/nodejs/undici/releases/tag/v8.12.3).
Die neue Paketauflösung liegt außerhalb aller vier betroffenen Bereiche.

Tatsächlich ausgeführte Validierung:

1. `pnpm why undici`: vorher 8.11.2, nachher ausschließlich 8.12.3 über unverändertes
   jsdom 30.1.2; auch aus dem installierten jsdom-Modulpfad geprüft.
2. `pnpm install --offline --frozen-lockfile --lockfile-only --ignore-scripts`:
   Manifest-/Lockfile-Konsistenz erfolgreich, vor Änderung installierter Pakete.
3. Nach dem notwendigen gezielten Download erneut
   `pnpm install --offline --frozen-lockfile --ignore-scripts --store-dir .gradle/s01-validation/pnpm-store`:
   erfolgreich, Already up to date, ohne Netzwerk und ohne erneute Auflösung.
4. `pnpm audit --json --registry https://registry.npmjs.org/`: Exit 0,
   leere Advisories, **0 info/low/moderate/high/critical**, 36 Entwicklungs-
   und 0 produktive npm-Abhängigkeiten. Keine der vier CVEs mehr gemeldet.
   Dies ist ein aktueller Registry-Abgleich, keine Garantie zukünftiger Lückenfreiheit.
5. Paket-Testskript und Testquellen vorab geprüft: ausschließlich
   `node --test src/test/js/*.test.cjs`, Mock-Fetch und gesperrte externe Ressourcen;
   keine Browser-/Listener-/Datenbank-/Scraperstarts. `pnpm run test:dom`:
   **225 bestanden, 0 Fehler, 0 übersprungen**, sämtliche Assertions unverändert.
6. `git diff --check` erfolgreich. Keine erneuten Java-/Gradle-/Integrationstests;
   die bisherigen 462 Java-Ergebnisse sind historische T03-Nachweise, nicht neu ausgeführt.

Nur pnpm-lock.yaml und dieser angehängte TESTING-Nachtrag wurden für diesen Auftrag
verändert. Vorher-/Nachher-Dateihashes bestätigen alle übrigen bestehenden
versionierten/unversionierten T03-Arbeiten, acht historischen Experimente und
Manual-JARs unverändert. Alter TESTING-Inhalt bleibt byteidentischer Präfix.
Branch weiterhin codex/t03-data-integrity, HEAD b7a2dbe; nichts gestagt/committet,
kein Push/Merge. Keine Datenbankverbindung, Migration, Anwendung oder echte
Browser-/Scraperausführung. Keine weiteren Auditbefunde im geprüften npm-Graphen.
Lokale Prüfprotokolle unter .gradle/undici-security-20261103, nicht für Git bestimmt.

## Nachtrag 04.11.2026 – T03-3 stabile Bandidentitäten und Aggregate

`IMPLEMENTED_DB_FREE_VERIFIED`: 417 Java-Tests bestanden, 0 Fehler, 0 übersprungen.
Branch codex/t03-data-integrity, Ausgangs-HEAD c20c213. Bestehende T03-Arbeiten
erhalten; kein Staging/Commit/Push. Keine Datenbank, Anwendung, Browser, Scraper,
Migration oder Downloads. Historische Ergebnisse sind keine neue DB-Abnahme.

Auswahl: BandInventoryTest (16), BandIntegrityCharacterizationTest (29),
ReadingStatusExtensionTest (40), PurchaseServiceTest (46),
PurchaseFlowCharacterizationTest (27), LibraryWriteCoordinatorTest (12),
LibraryWriteConfigurationTest (8), LegacyEditCharacterizationTest (51),
WriteServiceSafetyTest (38), WriteMvcValidationTest (106), TestDatabaseGuardTest (44).
Alle ausschließlich Unit-/Mock-/Fake-/Standalone-Kontexte, keine Boot-/JPA-Kontexte.

Vor jedem Lauf identischer Aufruf mit --dry-run, stets --offline --no-daemon.
Plan ausschließlich compileProductionOwnerJava/processProductionOwnerResources,
productionOwnerClasses, compileJava/processResources/classes, compileTestJava,
processTestResources/testClasses/test. Keine JAR-/JavaExec-/Integrationstasks.
Java 21; Kompilierung und finale Auswahl erfolgreich. Nur bekannte HotSpot-CDS-
Warnung. Sandbox-Cachezugriff erforderte erhöhte Dateiberechtigung, keine Downloads.

16 neue Fälle prüfen sämtliche sechs Listenpermutationen, doppelte/fehlende
Nummern oder IDs, fremde Zuordnung, unzulässige Umnummerierung, tatsächliche
Bestandsgröße im Mittelwert, veraltetes Reihenpreisaggregat und Finanzänderung
bei gleicher Anzahl. Bestehende falsche Positionszuordnung zu positiver Soll-
Assertion weiterentwickelt. Unbekannte Anpassungen jetzt kontrolliert abgewiesen;
keine Nullpreise erfunden. Übrige IST-Cent-/Reduktionsfälle bleiben bewusst grün.
Erster Lauf: 415 Fälle, ein Fixturefehler im URL-/DNS-Test (fehlende Bandnummer).
Gültige Nummer ergänzt, sämtliche Sicherheitsassertions erhalten. Anschließend
416 erfolgreich; nach zusätzlicher Mittelwertkorrektur final 417 erfolgreich.

M06- und Kaufregressionen prüfen unveränderte Altwerte, Bestätigungen und Mutex- /
Transaktionsgrenzen auf Fakeebene. Das ist kein echter Commit-/Rollbacknachweis.
DOM-Suite nicht erneut ausgeführt: keine Frontendänderung. Integrationstests nicht
ausgeführt: Coordinator lehnt deren äußere Schreibtransaktionen bewusst ab.
Konkrete Anpassungsmatrix in T03_IMPLEMENTATION_PLAN.md Abschnitt 12; keine
Rollback-Annotation entfernt und keine Assertions abgeschwächt. T03-4/5/6 offen.
Acht historische Dateien gegen vorhandene Hashliste unverändert; drei Manual-JARs
entsprechen weiterhin ihren dokumentierten SHA-256-Werten. git diff --check
erfolgreich; bestehende Windows-LF/CRLF-Hinweise sind keine Whitespacefehler.

## Nachtrag 04.11.2026 – T03-4 / exakte Geldberechnung

D5 Variante C auf Implementierungsebene umgesetzt: bekannter Pflichtpreis bei
Neuanlage, ausdrückliche 0 als Preis, keine neue NULL-Erzeugung durch Leeren,
historische NULL-Werte bei unbeteiligten Änderungen erhalten. Rechenkern Money
gemeinsam für Anlage, Erweiterung und PurchaseService; Finanzgrundlagen und
Preispläne vor Mutation geprüft. Verträge/Inventar: T03_IMPLEMENTATION_PLAN.md §13.

Auswahl ausschließlich datenbankfreier Klassen: MoneyTest,
BandInventoryTest, BandIntegrityCharacterizationTest, ReadingStatusExtensionTest,
PurchaseServiceTest, PurchaseFlowCharacterizationTest, LibraryWriteCoordinatorTest,
LibraryWriteConfigurationTest, LegacyEditCharacterizationTest,
WriteServiceSafetyTest, WriteMvcValidationTest, TestDatabaseGuardTest.
Vor jedem Lauf identischer --dry-run, stets --offline --no-daemon, Java 21.
Taskplan nur Compile/Resources/Klassen und gefiltertes test; kein JAR-, JavaExec-,
Boot-/JPA-/DB-/Browser-/Scrapertask. Vorhandener Gradle-Cache erforderte erhöhte
Dateiberechtigung. Keine Downloads/Installationen. Bekannter HotSpot-CDS-Hinweis.

Historische Centverlust-/ArithmeticException-Fälle wurden zu positiven
Summenerhaltungsfällen weiterentwickelt, nicht entfernt. Leeren eines bekannten
Bandpreises jetzt als 400 statt stillem Keep abgesichert. Pflichtpreis statt
fehlender Preis=0; unveränderte optionale Preise bei Bearbeitung jetzt Java-null.
Ein erster Lauf hatte 11 erwartete Abweichungen von bisherigen IST-Assertions;
danach Sollassertions und BigDecimal-Scale-Vergleich gezielt angepasst. Nach
weiteren Ergänzungen Zwischenlauf 445 bestanden/0 Fehler/0 übersprungen.

Geldtests prüfen signierte Restcents, 20/3, 1 bis 10.000 Zielbände, technische
Werte-/Skalengrenzen, Determinismus, vollständige Summe, ungültigen letzten Import
vor erstem Save, ausdrückliche Nullpreise, individuelle Altpreise und Setter-
Aggregate. Standalone-MockMvc ergänzt ungültige Centpreise vor Serviceaufruf;
bisherige Guard-, M06-, Mutex-, Kaufkonflikt- und Sicherheitsassertions erhalten.

DOM-Suite: **231 bestanden, 0 Fehler, 0 übersprungen** (pnpm run test:dom), sechs
neue Fälle für ausgeschaltete Preisoptionen, explizite 0/negative Anpassung,
Dialogreset, leere/ungültige Beträge und fehlende clientseitige Rundung. jsdom
ohne Browser, mit gesperrten Ressourcen/Netzwerk-Doubles. Keine Installation.
Lokales aktuelles Protokoll .gradle/t03-money-dom.txt, nicht für Git vorgesehen.

Keine Datenbankverbindung, kein clean test/Integrationstest, keine Migration,
Anwendung, echter Browser/Scraper oder native Probe. T03-6 bleibt gesondert
freizugeben; Fake-Transaktionen beweisen keinen echten Datenbankrollback.
Acht historische Experimente anhand vorhandener Hashliste unverändert; alle drei
Manual-JAR-Hashes unverändert. undici-Sicherheitsfix unangetastet. Kein Staging,
Commit, Push oder sonstige Änderung an der Historie. T03-5 nicht implementiert.

Abschließender Lauf nach Ergänzung der Neuanlageguards:
**446 bestanden, 0 Fehler, 0 übersprungen**, Kompilierung erfolgreich.
25 MoneyTest, 16 BandInventoryTest, 29 BandIntegrityCharacterizationTest,
40 ReadingStatusExtensionTest, 46 PurchaseServiceTest,
27 PurchaseFlowCharacterizationTest, 12 LibraryWriteCoordinatorTest,
8 LibraryWriteConfigurationTest, 51 LegacyEditCharacterizationTest,
38 WriteServiceSafetyTest, 110 WriteMvcValidationTest, 44 TestDatabaseGuardTest.
JavaScript-Syntaxprüfungen beider geänderter Dialogskripte und git diff --check
erfolgreich. Status `IMPLEMENTED_DB_FREE_VERIFIED` für T03-4, keine DB-Abnahme.

Dateiumfang dieses Teilauftrags (bestehende übrige T03-Arbeiten erhalten):
Money.java (neu), BandService.java, MangaReiheService.java,
EinkaufslisteService.java, PurchaseService.java, EinkaufslisteController.java,
WriteRequests.java, home.html, popup_edit_reihe.js, popup_edit_baende.js,
MoneyTest.java (neu), BandIntegrityCharacterizationTest.java,
LegacyEditCharacterizationTest.java, WriteMvcValidationTest.java,
WriteServiceSafetyTest.java, legacy-edit-characterization.test.cjs,
T03_IMPLEMENTATION_PLAN.md, TESTING.md, ROADMAP.md.

## Nachtrag 04.11.2026 – T03-5 sichere Bandanzahloperationen

BandInventory entscheidet anhand geprüfter tatsächlicher Bände zwischen Keep,
reiner Korrektur und Erweiterung. Reduktion unter N oder Teilkorrektur ohne Anlage
wird vor Mutation abgewiesen. Reine Korrektur speichert nur die Deklaration;
Bandwerte, Details, Timestamps, gespeicherte Preisaggregate und NULLs bleiben erhalten.
Verträge und Ist-/Soll-Matrix: T03_IMPLEMENTATION_PLAN.md Abschnitt 14.

Neue BandCountTest-Fälle verwenden echte Services mit synthetischen Repository-
Fakes; keine Spring-Boot-/JPA-/JDBC-Kontexte. Zusätzliche Standalone-MockMvc-Fälle
in LegacyEditCharacterizationTest prüfen echte Controller/Services mit aktiven
CSRF-, Host-/Origin- und Größenfiltern. Keine Netzwerk-/Scraperfunktion.
C15 von falschem Mittelwert/Reduktion zu aktiver Ablehnungsassertion überführt.
M06-Erweiterungsfixtures erhalten extendVolumes=true; normale Massenbearbeitung
false. Keine fachliche Assertion entfernt oder abgeschwächt.

Erster Java-Lauf: 463 Fälle, zwei fehlerhafte Fixtures (normale Bearbeitung mit
ausdrücklicher Anlageabsicht). Korrigiert und erneut geprüft; weitere Fälle für
abwesende optionale Sammelbandwerte, unveränderte zu hohe Deklaration und rohe
Entity-Speicherwege ergänzt. Erster neuer DOM-Fall scheiterte an seinem eigenen
Ein-Request-Payloadhelper nach zwei bewussten Klicks; zweite Antwort nun ausdrücklich
aus dem zweiten Mockrequest geprüft, keine automatische Wiederholung eingeführt.

DOM-Suite **232 bestanden, 0 Fehler, 0 übersprungen**; Protokoll lokal unter
.gradle/t03-count-dom.txt. Neuer Fall: Korrektur ohne Bandanlage, ausdrückliche
Erweiterung, Preisfelder bleiben unberührt, Optionsreset. JavaScript-Syntaxprüfung
erfolgreich. Kein echter Browser oder HTTP-Listener.

Vor jedem Gradle-Lauf identischer --dry-run, ausschließlich --offline --no-daemon
mit Java 21. Geprüfter Plan nur Compile/Resources/Klassen und gefiltertes test;
keine JAR-, Boot-, JavaExec-, DB- oder Live-Scrapertasks. Vorhandener Cache erforderte
erhöhte Dateiberechtigung. Nur bekannter HotSpot-CDS-Hinweis, keine Downloads.
Keine Datenbankverbindung, Migration, Anwendung, Browser oder echte Scraper;
kein clean test und keine Integrationstests. Echte Commit-/Rollback-/Konkurrenz-
und Browsernachweise bleiben T03-6 und benötigen eigene ausdrückliche Freigabe.
Acht historische Dateihashes und drei Manual-JAR-Hashes unverändert überprüft;
undici-Fix unangetastet. Keine Staging-/Commit-/Push-Aktion.

Abschließender Lauf: **468 Java-Tests bestanden, 0 Fehler, 0 übersprungen**.
Enthalten: 20 BandCountTest, 29 BandIntegrityCharacterizationTest,
16 BandInventoryTest, 25 MoneyTest, 40 ReadingStatusExtensionTest,
46 PurchaseServiceTest, 27 PurchaseFlowCharacterizationTest,
12 LibraryWriteCoordinatorTest, 8 LibraryWriteConfigurationTest,
53 LegacyEditCharacterizationTest, 110 WriteMvcValidationTest,
38 WriteServiceSafetyTest und 44 TestDatabaseGuardTest.
Vor dem endgültigen Lauf wurde noch eine falsche neue Testannahme korrigiert:
unveränderte Metadatenbearbeitung darf Details speichern, reine Anzahlkorrektur
dagegen nicht. Sämtliche Band-Nebenwirkungsassertions bleiben erhalten. Finaler
Offline-Lauf einschließlich Kompilierung erfolgreich, JavaScript-Syntax und
git diff --check erfolgreich. `IMPLEMENTED_DB_FREE_VERIFIED`, keine DB-Abnahme.

Dateiumfang dieses Teilauftrags: BandInventory.java, BandService.java,
MangaReiheService.java, WriteRequests.java, home.html, popup_edit_reihe.js,
BandCountTest.java (neu), BandIntegrityCharacterizationTest.java,
ReadingStatusExtensionTest.java, LegacyEditCharacterizationTest.java,
legacy-edit-characterization.test.cjs, T03_IMPLEMENTATION_PLAN.md,
TESTING.md und ROADMAP.md. Übrige uncommittete T03-Arbeiten erhalten.

## Nachtrag 04.11.2026 – T03-6A / Integrationsvorbereitung

Keine DB-/Boot-/Browserausführung. Inventar und spätere Freigaben in
T03_INTEGRATION_INVENTORY.md und T03_6B_APPROVAL.md. 13 Bootklassen/75 Methoden;
auch LegacyEditIntegrationTest und positiver RequestLimits-Bandedit mit äußerer
Testtransaktion kollidieren mit dem Coordinator. Keine bestehende Assertion oder
Annotation entfernt; keine Tests zum Grünmachen ausgeschlossen.

Neue Infrastruktur: separater Source-Set t03Integration, Task t03CommitTest.
Runtime übernimmt bestehende test-Ressourcen/Guardfactory, keine Profilvariante
oder alternative DataSource. Task nur mit ausdrücklicher Property und neuer
UUID-Laufkennung; atomare Verzeichnisreservierung im doFirst vor Testworkerstart.
Nicht an test/check/build gekoppelt, eigene testClassesDirs. maxParallelForks=1,
failFast, keine Up-to-date-Wiederverwendung. Testklassen ohne äußere @Transactional.
Journal hat keine Cleanupfunktion und persistiert keine Datenbankfeldwerte.

Nur KOMPILIERT, nicht ausgeführt: T03CommitAcceptanceTest mit drei geordneten
Fällen (Commit/Reload, Rollback nach Bandsave, bestätigter Kauf parallel zweimal).
Die vollständige E1–E6-Matrix ist dadurch noch nicht real implementiert/abgenommen;
Restfälle und Altvertragsanpassungen bleiben ausdrücklich in der Freigabevorlage.
SHOW GRANTS/alle SELECT-/DML-Anweisungen der neuen Klasse sind ausschließlich
vorbereiteter Quellcode und wurden in 6A nicht ausgeführt.

Aktuelle datenbankfreie Auswahl: bisherige 468 Klassenfälle plus T03PreparationTest
(Journalexklusivität und Rechte, alte/neue Zeilen, unregistrierte/gelöschte/veränderte
Zeilen, Fremd-ID-Registrierung, Schema/Stammdaten und Tasktrennung). Kein Spring-
Kontext, JPA-/JDBC-/DNS-/Netzwerkobjekt in diesen neuen Unitfällen.
Erster Lauf mit sechs neuen Fällen erfolgreich; danach Serverrechteparser und
Tabellenzahl-Vorprüfung ergänzt und finale Auswahl erneut geprüft.

Vor jedem realen Gradle-Aufruf identischer --dry-run; ausschließlich --offline
--no-daemon, Java 21. Geprüfter Taskplan: Compile/Resources/Klassen, insbesondere
compileT03IntegrationJava, und ausschließlich gefiltertes test. t03CommitTest ist
darin NICHT enthalten. Separater check --dry-run zeigte keine Opt-in-Tasks;
t03CommitTest --dry-run zeigte nur seine Compile-/Resources-/Klassenkette und den
übersprungenen Task, kein normales test. Ein Dry-Run allein ersetzt nicht die
zusätzlich durchgeführte statische Klassen-/Lifecycle-/Guardprüfung.
DOM-Suite nicht erneut ausgeführt: keine Frontendänderung, 232 bleibt historisch.
Keine Integration, kein clean test/ungefiltertes test/check/build ausgeführt.
Keine Downloads/Installationen, kein neuer Manual-JAR, kein Git-Commit/Push/Staging.

Endgültiger Lauf: **475 bestanden, 0 Fehler, 0 übersprungen** (468 bestehende
ausgewählte Fälle + 7 neue T03PreparationTest). compileT03IntegrationJava erfolgreich;
die drei dortigen Commitfälle NICHT ausgeführt. Nur vorhandene HotSpot-CDS-Warnung.
git diff --check erfolgreich; bestehende Windows-LF/CRLF-Hinweise unverändert.
Alle drei Manual-JAR-Hashes und acht historische Dateihashes identisch überprüft.
Status `T03_6A_READY_FOR_APPROVAL` für die ausdrücklich abgegrenzte erste Stufe;
keine vollständige T03-6B-/T03-Abnahme oder Freigabe einer ungefilterten Suite.

Dateiumfang ausschließlich dieser Vorbereitung: build.gradle.kts,
src/t03Integration/java/de/mangalib/T03CommitAcceptanceTest.java,
src/test/java/de/mangalib/T03PreparationTest.java, T03Preservation.java,
T03RunJournal.java, T03ServerRights.java (alle vier im gleichen Testpackage),
T03_INTEGRATION_INVENTORY.md, T03_6B_APPROVAL.md, T03_IMPLEMENTATION_PLAN.md,
TESTING.md und ROADMAP.md. Keine Produktivcodeänderung in T03-6A.

## Nachtrag 04.11.2026 – lesende T03-6B-Vorprüfung ausschließlich vorbereitet

Eigener Opt-in-Task t03ReadOnlyTest mit fester Allowlist ausschließlich
T03ReadOnlyPreflightTest; t03CommitTest ebenfalls auf seine bisherige einzelne
Klasse begrenzt. Keine Kopplung oder gemischte Ausführung. Bestehende Guards und
Konfigurationsdateien unverändert. Neue reine T03ReadOnlyPolicy bindet den Zustand
an zehn InnoDB-Tabellen, 78 Stammdaten, leere Geschäftstabellen und den historischen
Fingerprint ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Abweichung bedeutet STOP, keine automatische Neubasierung/Korrektur.

Geplante Laufzeit: ausschließlich SELECT/SHOW über vorhandenen geschützten EM,
zwei unabhängige read-only Transaktionen, keine Fixtures oder Servicewrites.
Ausgaben nur Hash/Counts/neutraler Status. T03Preservation-Gleichheitsassertions
ebenfalls auf neutrale Fehler umgestellt, damit keine Fremdzeilen in Reports
erscheinen; Prüfkriterien unverändert. Keine Änderungen an Produktivcode.
Prozessvariable MANGALIB_TEST_DB_PASSWORD vorhanden/nicht leer geprüft; kein Wert
ausgegeben. Relevante Overridevariablen derzeit nicht vorhanden. Spätere effektive
Konfiguration weiterhin vor jeder Verbindung zwingend durch bestehenden Guard prüfen.

Tatsächlich geprüft: t03ReadOnlyTest --dry-run (Task ausschließlich SKIPPED),
compileT03IntegrationJava nach identischem --dry-run, stets offline/no-daemon,
keine Testausführung oder Verbindung. Neue vier synthetische Policy-/Trennungsfälle
T03ReadOnlyPreparationTest nur vorbereitet/kompiliert. Keine erneuten Guard-,
Java-, DOM- oder Integrationstestläufe; 475/232 sind historische Ergebnisse.
Freigabevorlage und vollständige spätere Befehle: T03_6B_APPROVAL.md Abschnitt 7.
Keine neue DB-Freigabe aus dieser Vorbereitung ableiten.

Abschluss der Vorbereitung: reine Offline-Kompilierung erfolgreich (einschließlich
der neuen Policy-/Vorprüfungsquellen), git diff --check erfolgreich. Kein Testworker
gestartet. Vorhandene acht historische Experimente und drei Manual-JARs erneut
hashgleich; Guardquellen, application-test.properties und undici-Lockfile unverändert.
Geänderte Dateien dieses Auftrags: build.gradle.kts, T03_6B_APPROVAL.md, TESTING.md,
T03Preservation.java (nur neutrale Fehlerdarstellung), T03ReadOnlyPolicy.java (neu),
T03ReadOnlyPreparationTest.java (neu), T03ReadOnlyPreflightTest.java (neu).
Branch codex/t03-data-integrity, HEAD c20c213, Staging leer. Keine Datenbankverbindung,
SQL-Ausführung, Fixture, Bereinigung, Anwendung, Browser oder Scraper ausgeführt.

## Nachtrag 05.11.2026 – freigegebene lesende T03-6B-Vorprüfung fehlgeschlagen

Status **FAILED**, keine Wiederholung. Branch codex/t03-data-integrity / HEAD
c20c213 unverändert. Passwortvariable vorhanden/nicht leer; keine relevanten
Profil-/Datasource-/JPA-/JVM-Overridevariablen gefunden, keine Werte ausgegeben.
Bestehende Guards, Testprofil und Klassen-Allowlist statisch unverändert geprüft.

Tatsächliche Befehlsfolge wie T03_6B_APPROVAL.md Abschnitt 7, Java 21, immer
--offline --no-daemon mit jeweils identischem vorherigem --dry-run:

- test --tests de.mangalib.testsupport.TestDatabaseGuardTest: 44 bestanden,
  0 Fehler, 0 übersprungen.
- test --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest:
  7 + 4 bestanden, 0 Fehler, 0 übersprungen.
- t03ReadOnlyTest -Pt03ReadOnlyApproval=READ_ONLY_MANGALIB_TEST: genau einmal
  ausgeführt; 1 Test fehlgeschlagen, 0 übersprungen. Taskplan enthielt ausschließlich
  die Compile-/Ressourcenfolge und den lesenden Task, weder test noch t03CommitTest.

Der erste Guard-Dry-Run konnte wegen Sandboxzugriff auf die vorhandene externe
Gradle-Cache-Lockdatei nicht starten; derselbe Dry-Run war mit erweitertem
Cachezugriff erfolgreich. Kein Download, keine zusätzliche DB-Ausführung.

Nachgewiesener Fehler: Spring konnte LiveScraperGateway bei der Verarbeitung
von Konfigurationsbedingungen nicht introspektieren; NoClassDefFoundError /
ClassNotFoundException für de.mangalib.windowsadapter.ProductionScraperClient.
Die Klasse gehört zum productionOwner-Source-Set, fehlt aber auf der wirksamen
Laufzeit-Classpath dieses Tasks. Compile war UP-TO-DATE. Die Task-Classpath wird
vor der späteren Neuzuweisung von t03Integration.runtimeClasspath gesetzt;
diese Bindungsreihenfolge ist der gezielt zu prüfende Infrastrukturverdacht.
Keine Reparatur oder erneute Ausführung in dieser begrenzten Abnahme vorgenommen.

Abbruch vor Singleton-/JPA-Initialisierung und vor der Prüfmethode; keine
T03_READ_ONLY-Marker oder JPA-Initialisierungsmarker im Bericht. Keine
Datenbankverbindung und keine SELECT-/SHOW-/DML-/DDL-Ausführung durch diesen Lauf.
Tatsächliche DB-/Accountidentität, Rechte, Tabellen und Fingerabdruck bleiben
UNGEPRÜFT. Der historische Hash ist kein neu gemessener Befund.
Keine Fixtures, Bereinigung, Migration, Anwendung, Listener, Browser oder Scraper.

Bericht: build/reports/tests/t03ReadOnlyTest/index.html sowie zugehöriges lokales
JUnit-XML. Die 55 datenbankfreien Fälle sind erfolgreich; daraus keine reale
DB-Freigabeprüfung ableiten. Commit-/Konkurrenztests bleiben gesperrt. Nach einer
gezielten datenbankfreien Classpathkorrektur ist eine neue ausdrückliche Freigabe
für einen weiteren lesenden Lauf erforderlich. Keine Git-Schreiboperationen.

## Nachtrag 05.11.2026 – gezielte Classpathkorrektur, datenbankfrei verifiziert

Status **CLASSPATH_FIX_DB_FREE_VERIFIED**. Der vorangegangene FAILED-Lauf bleibt
historisch unverändert; keine Wiederholung der Datenbankvorprüfung.

Ursache bestätigt: Beide früh registrierten Testtasks übernahmen die damalige
FileCollection von t03Integration.runtimeClasspath. Die spätere Neuzuweisung des
Source-Set-Attributs ersetzt diese Referenz nicht rückwirkend in bereits
konfigurierten Tasks. Vor der Korrektur meldete die effektive lesende Task-Classpath
168 Einträge und fehlenden productionOwner-Klassen-/Ressourcenoutput sowie
jna-5.17.0.jar gegenüber der abschließenden Source-Set-Classpath. Beide Tasks
verwendeten dieselbe fehlerhafte Zuweisung. Kompilierung war nicht betroffen,
weil deren Classpath separat um productionOwner ergänzt war.

Minimale Korrektur: Beide Task-Classpaths verwenden jetzt
files(providers.provider { t03Integration.runtimeClasspath }). Damit wird die
abschließende gemeinsame Runtime-Classpath erst bei Auflösung gelesen. Diese
besteht unverändert aus der regulären Test-Runtime (main/test, productionOwner,
vorhandene Runtime-Dependencies einschließlich gepinntem JNA) und ausschließlich
zusätzlichem t03Integration-Output. Keine neue Dependency, kein neues Profil,
keine Produktiv-/Guard-/Manual-Paketierungsänderung.

Neuer expliziter Regressionstask t03ClasspathCheck: abhängig ausschließlich von
compileT03IntegrationJava samt Compile-/Ressourcenfolge, nicht von Testtasks.
Er prüft die tatsächlich aufgelösten Classpaths beider Opt-in-Tasks auf exakte
Dateimengengleichheit, die festen getrennten Klassen-Allowlists und Testklassen-
Verzeichnisse. Pro Task isolierter URLClassLoader mit Plattform-Elternloader:
neun Klassen (Gateway, Client, Owner/Wire/native Adapter, JNA Native/Pointer,
GuardedTestDataSource) samt Methodensignaturen/Konstruktoren/Feldern auflösbar.
Class.forName(..., false, ...) verhindert Klasseninitialisierung. Kein Bean,
kein disabled()/Konstruktor-/Native-/Workeraufruf. Zwei Negativkontrollen ohne
Owner-Output reproduzieren gezielt NoClassDefFoundError für ProductionScraperClient.
Eine Klasse für die Spring-Typauflösung verfügbar zu machen aktiviert keinen Import;
die unveränderte test-Profil-Factory bleibt deaktiviert und die Opt-in-Klassen
ersetzen ScraperGateway weiterhin durch MockitoBean.

Weitere automatisierte Assertions: kein Opt-in-Output im regulären test, keine
Opt-in-Abhängigkeit/Finalisierung von test/check/build, getrennte main-/Opt-in-
Outputs, aktuelle manualBootJar-Classpath ohne Owner-Output/JNA/Selenium.
Vor Korrektur schlug die neue Diagnose wie erwartet mit fehlendem Owner fehl;
nach Korrektur beide Tasks exakt 165 Runtime-Einträge, missing=[], alle positiven
und negativen Checks erfolgreich. Ein anfänglicher Kotlin-Skriptfehler im neuen
Diagnosetask (URLClassLoader-Import) wurde vor der Diagnoseausführung korrigiert.

Tatsächlich ausgeführt, jeweils Java 21, --offline --no-daemon und identischer
erfolgreicher --dry-run vor Ausführung:

- t03ClasspathCheck: endgültig BUILD SUCCESSFUL, einschließlich erfolgreicher
  compileT03IntegrationJava-Prüfung (UP-TO-DATE); keine Opt-in-Testausführung.
- test --tests de.mangalib.testsupport.TestDatabaseGuardTest
  --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest:
  **55 bestanden (44 + 7 + 4), 0 Fehler, 0 übersprungen**. Nur bekannte CDS-Warnung.
- git diff --check erfolgreich; bekannte LF/CRLF-Hinweise bleiben bestehen.

Vorhandene Manual-JARs nur als ZIP gelesen und gehasht, nicht neu gebaut:
der gepatchte Tomcat-11.0.27-Abnahmekandidat ohne Live-Gateway/Scraper/Windowsadapter,
Selenium oder JNA. Zwei ältere historische JARs enthalten unverändert
BOOT-INF/lib/jna-5.13.0.jar, jedoch keine Live-Scraper-/Windowsadapterklassen.
Diese Altartefakte nicht als aktuellen paketbereinigten Kandidaten verwenden.
Keine Änderung durch diese Korrektur; die aktuelle Paketkonfiguration unverändert.

Geänderte Dateien dieses Auftrags: build.gradle.kts, TESTING.md,
T03_6B_APPROVAL.md. Keine Datenbank-/Spring-Kontext-/HTTP-/Browser-/Scraper-/
Worker-/native Windows-Ausführung. Guards/Testprofil/Factory und historische
Experimente unverändert; Manual-JAR-Hashes unverändert nachgeprüft.
Keine Staging-/Commit-/Pushaktionen. Verbleibende Nachweisgrenze: kaltes Laden
beweist weder Spring-Kontextstart noch reale DB-Prüfung. Neue ausdrückliche
Freigabe für genau einen weiteren lesenden Lauf erforderlich; Commit- und
Konkurrenzstufe unverändert gesperrt.

## Nachtrag 05.11.2026 – erneuter einmalig freigegebener lesender Lauf: FAILED

Nach separater erneuter Eigentümerfreigabe genau ein t03ReadOnlyTest ausgeführt.
Branch codex/t03-data-integrity / HEAD c20c213, Staging leer. Passwortvariable
vorhanden/nicht leer, keine relevanten Profil-/Datasource-/JPA-/JVM-Overrides
gefunden. Guards und test-Konfiguration unverändert; keine Werte ausgegeben.

Tatsächliche Befehle unter Java 21, jeweils --offline --no-daemon und identischer
erfolgreicher --dry-run vor Ausführung:

- test --tests de.mangalib.testsupport.TestDatabaseGuardTest
  --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest:
  zunächst UP-TO-DATE, daher kein neuer Testnachweis aus diesem ersten Aufruf.
- Derselbe gefilterte test-Aufruf mit zusätzlichem Taskargument --rerun, nach
  erneutem identischem --dry-run: 44 + 7 + 4 = **55 bestanden, 0 Fehler, 0 Skips**.
- t03ClasspathCheck: erfolgreich, beide Tasks 165 Runtime-Einträge, missing=[],
  je neun Klassen ohne Initialisierung auflösbar; Trennung/Negativkontrollen grün.
- t03ReadOnlyTest -Pt03ReadOnlyApproval=READ_ONLY_MANGALIB_TEST: genau einmal,
  **1 fehlgeschlagen, 0 Skips**; keine Wiederholung.

Die Classpathkorrektur wirkt: Spring-/JPA-Kontext erfolgreich aufgebaut, Hibernate
im bestehenden validate-Modus, GuardedTestDataSource vor Verbindung aktiv.
Tatsächlich ermittelter CURRENT_USER(): mangalib_test_user@localhost. Anschließend
SHOW GRANTS FOR CURRENT_USER() ausgeführt; T03ServerRights.verify verweigert eine
nicht unterstützte Grantdarstellung mit „Unbekannte Testrechte/Role: STOP.“
(T03ServerRights.java:15, T03ReadOnlyPreflightTest.java:60). Dies beweist weder
zulässige noch tatsächlich überhöhte Rechte; möglich sind unbekannte Syntax,
Rollen oder abweichende Zielnotation. Grantrohdaten werden absichtlich nicht
geloggt; die konkrete Serverdarstellung ist daher nicht rekonstruierbar.
Keine weitere Verbindung oder Diagnoseabfrage nach dem Abbruch.

Das feste JDBC-Ziel mangalib_test/localhost:3306 ist durch den unveränderten
Guard vor und bei jeder Verbindung erzwungen. Die zusätzliche SELECT database()-
Assertion im Snapshot wurde wegen der vorgeschalteten Rechteprüfung NICHT erreicht.
Ebenso keine vollständige Tabellen-/FK-/Stammdatenprüfung und kein Vorher-/Nachher-
Fingerprint: null T03_READ_ONLY_BEFORE/AFTER_FINGERPRINT-Marker. Historische zehn
Tabellen/78 Stammdaten/Hash bleiben in diesem Lauf nicht als Gesamtzustand bestätigt.
Hibernate-Schemavalidierung ersetzt den vollständigen Snapshot ausdrücklich nicht.

Keine fachliche DML, DDL, Fixtures, Migrationen, Bereinigung oder Schreibservices.
Nur freigegebener Mock-Testkontext, Verbindungs-/Transaktionssteuerung, Metadaten
und SELECT/SHOW; keine Anwendung/Listener/Browser/Scraper/Worker gestartet.
Keine Produktiv-/Manual-DB-Verbindung. Kontext wurde am Laufende geschlossen.
Beobachtete Warnungen: bekannte CDS-/Open-in-View-Hinweise sowie
BeanPostProcessorChecker zu früh erzeugten Beans im Umfeld localWebServerFactoryGuard
(auch Coordinator/Transaktionsmanager). Der lesende Lauf belegt deshalb keine
korrekte Schreibservice-Advisorreihenfolge; vor Commitabnahme gezielt klären.

Status **FAILED**, technische Ausführungserlaubnis für diesen einen Lauf genutzt.
Kein Parser-/Guard-/Konfigurationsfix und kein weiterer DB-Lauf in diesem Auftrag.
Bericht build/reports/tests/t03ReadOnlyTest/index.html, lokales JUnit-XML daneben.
Nächster Schritt: Rechteprüfer/zulässige Grantnotation gezielt datenbankfrei
untersuchen; bei fehlender Evidenz separate eng begrenzte lesende Diagnose
freigeben lassen. Keine automatische Zulassung unbekannter Grants oder Änderung
der DB-Rechte. Commit-/Konkurrenztests und Vollsuite bleiben gesperrt.

## Nachtrag 05.11.2026 – Rechteparser/AOP-Warnungen datenbankfrei untersucht

Gesamtstatus **DIAGNOSIS_REQUIRED**: Parserkorrektur und ihre Sicherheitsgrenzen
sind datenbankfrei geprüft; der tatsächliche Granttext des fehlgeschlagenen
Serverlaufs ist nicht vorhanden. Deshalb weder Serverrechte als sicher noch
Maskierung als bewiesene reale Abbruchursache ausgeben. Kein neuer DB-Lauf.

Nachgewiesene Parserprobleme: Die bisherige Regex verweigert die gültige
MySQL-Darstellung `mangalib\_test`.*. Gleichzeitig akzeptierte sie unmaskiertes
`mangalib_test`.*, obwohl _ bei Schema-Grants ohne partial_revokes=ON eine
Wildcard ist. Backticks heben diese Grantmuster-Semantik nicht auf. Der reale
Fehler ist mit diesem Befund vereinbar, aber nicht abschließend erklärt.
Offizielle Quellen (nur Dokumentationsabrufe, keine Projektdaten übertragen):
https://dev.mysql.com/doc/refman/8.4/en/grant.html und
https://dev.mysql.com/doc/refman/8.4/en/partial-revokes.html.

T03ServerRights akzeptiert jetzt ausschließlich ausdrücklich maskiertes
mangalib\_test.* (mit oder ohne Backticks), genau SELECT/INSERT/UPDATE/DELETE
und global ausschließlich USAGE. Teilgrants können nur diese vier Rechte
ergänzen. Kein Schema-Casefolding, kein allgemeines Backslash-Unescaping,
kein Entfernen beliebiger Endklauseln. Zusätzliche Schemas, Wildcards, Tabellen-
Grants, DDL/Adminrechte, Rollen/REVOKE, GRANT OPTION und unbekannte Syntax bleiben
STOP. Unmaskiertes mangalib_test.* ist ohne separat bewiesenen Servermodus
AMBIGUOUS_SCHEMA_PATTERN, nicht automatisch sicher. Auch bei tatsächlich sicherem
partial_revokes=ON wird diese Darstellung vorerst konservativ abgelehnt.
Keine Servermodusabfrage oder Änderung der Serverrechte implementiert/ausgeführt.

Fehler enthalten nur feste Reason-Kategorien (UNSUPPORTED_FORMAT,
FORBIDDEN_PRIVILEGE, FORBIDDEN_SCOPE, AMBIGUOUS_SCHEMA_PATTERN, MISSING_DML),
keinen Granttext/Accountsuffix/Secret und keine Rohdaten als Ursache. Die bestehende
positive T03PreparationTest-Probe verwendet nun das explizit maskierte Zielschema;
negative Assertions bleiben erhalten. T03ServerRightsTest ergänzt 40 synthetische
positive/negative Fälle, einschließlich Endklauseln, doppelten/fehlenden Rechten,
NULL, fremden Schemas, maskierten/unmaskierten Wildcards und Ausgaberestriktion.

Spring-Warnungen: LibraryWriteBootstrapTest verwendet die echte
LibraryWriteConfiguration mit @EnableTransactionManagement und die echte statische
Factory localWebServerFactoryGuard, jedoch keine vollständige Security-/Boot-
Konfiguration. Ausschließlich RAM-Entities, Mockito-Repositories/Kollaboratoren,
FakeLibraryTransactionManager; explizit keine DataSource. Der erste neue Lauf
hatte zwei fehlgeschlagene Fälle wegen fehlender Test-Doubles für Feldinjektion;
diese Testverdrahtung korrigiert, keine Produktivänderung/Assertionsabschwächung.

Die beiden finalen Bootstrapfälle reproduzieren vier BeanPostProcessorChecker-
Warnungen: frühe Erzeugung von Advisor-Konfiguration/Coordinator/Manager im Umfeld
des Guard-BPP ist möglich. Die getesteten Reihen-/Einkaufsservices werden dennoch
zu AOP-Proxys; LibraryWriteAdvisor liegt vor dem Transaktionsadvisor. Zwei äußere
Schreibaufrufe ergeben genau zwei Transaktionsbeginne, Commit bzw. Rollback und
Cleanup bleiben aktiv. Früh erzeugte Infrastrukturtypen liegen außerhalb des
Writer-Pointcuts; sie benötigen nicht selbst diesen Schreibadvisor. Kein belegter
Grund für eine Produktiv-/Architekturkorrektur oder bloßes Unterdrücken der Warnung.
Relevante Spring-Dokumentation:
https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html.
Nachweisgrenze: keine Aussage über sämtliche Beans/Autokonfiguration im vollständigen
Boot-/JPA-Kontext. Vor echten Commitfällen dort die tatsächlich injizierten Writer-
Proxys und Advisorreihenfolge prüfen; Warnungsfreiheit allein wäre kein Nachweis.

Finale reale Testauswahl, Java 21, --offline --no-daemon, jeweils identischer
erfolgreicher --dry-run vor Ausführung:
test --rerun mit Filtern auf T03ServerRightsTest (40), T03PreparationTest (7),
T03ReadOnlyPreparationTest (4), TestDatabaseGuardTest (44),
LibraryWriteBootstrapTest (2), LibraryWriteConfigurationTest (8),
LibraryWriteCoordinatorTest (12), LocalSecurityPolicyTest (61):
**178 bestanden, 0 Fehler, 0 übersprungen**. Bekannte HotSpot-CDS-Warnung.
t03ClasspathCheck ebenfalls erfolgreich: beide effektiven Opt-in-Classpaths
165 Einträge/missing=[], je neun Klassen ohne Initialisierung auflösbar,
Allowlist-/Lifecycle-/Manual-Classpathtrennung unverändert. Dabei opt-in-Quellen
erfolgreich neu kompiliert, keine opt-in-Tests ausgeführt. git diff --check grün.

Dateien dieses Auftrags: T03ServerRights.java, T03PreparationTest.java,
T03ServerRightsTest.java (neu), service/LibraryWriteBootstrapTest.java (neu),
TESTING.md und T03_6B_APPROVAL.md. Keine T03-Fachlogik-/Gradle-/Guardänderung,
keine Datenbank-, SQL-, Anwendungs-, Listener-, Browser-, Scraper- oder Worker-
Ausführung. Historische Datenbankfehlläufe bleiben FAILED. Keine Git-Schreibaktion.

Datensparsamer Diagnosevorschlag und nötige neue Freigabe: siehe
T03_6B_APPROVAL.md Abschnitt 11. Unbekannte Grants bleiben bis dahin STOP.

## Nachtrag 05.11.2026 – einmalige reale Rechte-Diagnose: RIGHTS_CONFIGURATION_BLOCKED

Nach ausdrücklicher Freigabe eigener isolierter Task t03RightsDiagnosisTest mit
fester einzelner Klassen-Allowlist, verzögerter bestehender T03-Runtime-Classpath,
maxParallelForks=1/failFast und expliziter Approval-Property plus Kontextinitializer.
Nicht an test/check/build oder andere Opt-in-Tasks gekoppelt. Vorhandene Guard-
Factory/DataSource/test-Profil unverändert, ScraperGateway als MockitoBean.
WebEnvironment.MOCK, read-only TransactionTemplate, kein HTTP-Listener.

Diagnoseklasse enthält exakt SELECT DATABASE(), SELECT CURRENT_USER(),
SELECT @@GLOBAL.partial_revokes und SHOW GRANTS FOR CURRENT_USER(). Keine Geschäfts-
oder Stammdatenabfragen/Fixtures/Snapshots. Boot/JPA benötigt weiterhin seine
vorhandene Schema-Metadatenvalidierung vor dem Test; keine Schemaerzeugung.
Identität wird vor Ergebnisprotokollierung exakt geprüft, nur EXPECTED-Kategorien
ausgegeben. T03RightsDiagnosis speichert ausschließlich abgeleitete feste Enums,
Counts und Parser-Reason im Ergebnis; keine Grantzeilen/Accountnamen/fremde
Schemanamen in Ergebnisobjekt oder Logs. Historischer Parser nur zum Vergleich,
niemals als Admissionprüfung. T03ServerRights.GRANT ist dafür paketintern wieder-
verwendbar; zulässige Rechte und Ablehnungslogik unverändert.

Vorprüfungen, jeweils Java 21, --offline --no-daemon, identischer erfolgreicher
--dry-run vor Ausführung:
- test --rerun mit TestDatabaseGuardTest (44), T03PreparationTest (7),
  T03ReadOnlyPreparationTest (4), T03ServerRightsTest (40),
  T03RightsDiagnosisPreparationTest (12 neu): **107 bestanden, 0 Fehler, 0 Skips**.
- t03ClasspathCheck: alle drei Opt-in-Tasks exakt 165 Runtime-Einträge/missing=[],
  Klassenauflösung ohne Initialisierung, Allowlists/Tasktrennung grün; neue
  Diagnosequelle erfolgreich kompiliert. Keine DB-Ausführung dieser Vorstufen.
- t03RightsDiagnosisTest -Pt03RightsApproval=DIAGNOSE_TEST_RIGHTS_ONCE nach
  identischem --dry-run: **genau ein realer Lauf**, 1 fehlgeschlagen, 0 Skips.

Tatsächlicher sicher kategorisierter Befund:
- Profil test; Spring-/JPA-Kontext erfolgreich aufgebaut und anschließend geschlossen.
- Datenbank exakt mangalib_test, CURRENT_USER exakt mangalib_test_user@localhost.
- partial_revokes=OFF; zwei Grantzeilen.
- Geltungsbereiche GLOBAL_USAGE und EXACT_ESCAPED_TEST_SCHEMA.
- Erkannte Privilegkategorien SELECT, INSERT, UPDATE, DELETE, USAGE, **DDL, OTHER**.
- Keine Rollen/unerkannten Anweisungsformen und kein GRANT OPTION erkannt.
- Historischer Parser: abgewiesen; korrigierter Parser: **FORBIDDEN_PRIVILEGE**.

Damit liegt NICHT nur ein Formatproblem vor: Neben der vom historischen Parser
nicht unterstützten maskierten Schemanotation existieren tatsächlich Rechte
außerhalb der zulässigen vier DML-Rechte. DDL/OTHER-Kategorien beweisen einen
Konfigurationsblocker; die Diagnose nennt absichtlich keine konkreten fremden
Namen oder vollständigen Granttexte. Die Parserkorrektur funktioniert fail-closed.
Eine weitere Parserlockerung wäre keine Lösung. Keine Rechte-/Serveränderung.

Genau vier fachliche Diagnoseanweisungen ausgeführt, keine DML/DDL/Fixtures,
Migrationen, Bereinigung oder Dateninhalte. Kein t03ReadOnlyTest/t03CommitTest,
keine Vollsuite, kein weiterer DB-Lauf, keine Anwendung/Browser/Worker/Scraper.
Passwort ausschließlich Prozessvariable; relevante Overrides nicht vorhanden.
Reale Baseline (zehn Tabellen/78 Stammdaten/Fingerprint) bleibt ungeprüft, da
keine Geschäfts-/Stammdaten gelesen wurden. Keine automatische Fortsetzung.
Bekannte BPP-/Open-in-View-/CDS-Warnungen ohne Änderung der Produktivarchitektur.

Erforderlich: Eigentümer/DB-Administrator muss den Testaccount separat prüfen und
auf die dokumentierten minimalen Rechte begrenzen; keine automatische Rechte-
korrektur oder SQL-Empfehlung ohne Prüfung des administrativen Kontexts. Danach
neue ausdrückliche Freigabe für Rechteprüfung/vollständige lesende Vorprüfung.
Vor dieser bestätigten Korrektur keine neue vollständige Vorprüfung empfehlen.
Commit-/Konkurrenzstufe bleibt gesperrt.

Dateien dieses Auftrags: build.gradle.kts, T03ServerRights.java (nur Sichtbarkeit
des Grammatikmusters), T03RightsDiagnosis.java (neu),
T03RightsDiagnosisPreparationTest.java (neu),
src/t03Integration/java/de/mangalib/T03RightsDiagnosisTest.java (neu), TESTING.md,
T03_6B_APPROVAL.md. Keine T03-Fachlogik-/Guard-/Profiländerung, kein Git-Staging,
Commit/Push/Merge. Historische fehlgeschlagene Läufe bleiben unverändert FAILED.

## Nachtrag 05.11.2026 – Rechte nach administrativer Korrektur bestätigt

Status **RIGHTS_DIAGNOSIS_VERIFIED**. Der Eigentümer meldete die separat von ihm
ausgeführte und geprüfte Rechtebegrenzung und beauftragte die Fortsetzung. Codex
führte daraufhin ausschließlich die bestehende vierteilige Rechte-Diagnose erneut
einmal aus, keine vollständige Datenbankvorprüfung und keine Schreibtests.

Branch codex/t03-data-integrity / HEAD c20c213; Guards/Testprofil unverändert,
Passwortvariable vorhanden/nicht leer, keine relevanten Overrides. Java 21,
alle Gradle-Aufrufe --offline --no-daemon mit identischem erfolgreichem --dry-run:
- test --rerun mit TestDatabaseGuardTest, T03PreparationTest,
  T03ReadOnlyPreparationTest, T03ServerRightsTest, T03RightsDiagnosisPreparationTest:
  **107 bestanden, 0 Fehler, 0 übersprungen**.
- t03ClasspathCheck: erfolgreich, alle drei Opt-in-Tasks getrennt und vollständig.
- t03RightsDiagnosisTest -Pt03RightsApproval=DIAGNOSE_TEST_RIGHTS_ONCE:
  **1 bestanden, 0 Fehler, 0 übersprungen**, genau ein realer Diagnoselauf.

Spring-/JPA-Kontext erfolgreich gestartet/geschlossen. Datenbank mangalib_test und
Account mangalib_test_user@localhost exakt bestätigt. partial_revokes OFF, zwei
Grantzeilen, GLOBAL_USAGE und EXACT_ESCAPED_TEST_SCHEMA; ausschließlich SELECT,
INSERT, UPDATE, DELETE und USAGE. Keine DDL-/OTHER-Rechtekategorie, Rollen,
unbekannte Grantdarstellung oder GRANT OPTION erkannt. Historischer Parser lehnt
die maskierte Notation weiterhin ab; korrigierter Parser ACCEPTED.
Keine Grantrohtexte, Passwörter oder fremden Namen ausgegeben.

Der zuvor nachgewiesene Rechteblocker ist damit im geprüften Umfang behoben;
die historischen Fehlläufe bleiben unverändert. Keine Rechteänderung durch Codex,
keine DML/DDL, Fixtures, Geschäftsdaten-/Stammdatenabfragen oder Bereinigung.
Schema-/Datenfingerabdruck nicht erhoben. Vollständige lesende Vorprüfung sowie
Commit-/Konkurrenztests bleiben getrennte, neu freizugebende Schritte. Keine
Anwendungs-/Listener-/Browser-/Worker-/Scraperstarts, keine Git-Schreibaktionen.
Nur TESTING.md und T03_6B_APPROVAL.md durch Nachträge geändert.

## Nachtrag 05.11.2026 – vollständige lesende T03-6B-Vorprüfung bestanden

Status **T03_6B_READ_ONLY_VERIFIED**. Nach neuer ausdrücklicher Eigentümerfreigabe
genau ein t03ReadOnlyTest ausgeführt, keine anderen Datenbanktests. Historische
FAILED-/BLOCKED-Befunde bleiben als damalige Ergebnisse erhalten.

Voraussetzungen erneut bestätigt: Branch codex/t03-data-integrity / HEAD c20c213,
Staging leer; bestehende Guards/test-Profil unverändert, Passwortvariable vorhanden
und nicht leer, keine relevanten Overridevariablen. Java 21; ausschließlich
--offline --no-daemon, jeder tatsächliche Gradle-Aufruf nach identischem
erfolgreichem --dry-run. Tatsächliche Auswahl:

- test --rerun --tests de.mangalib.testsupport.TestDatabaseGuardTest
  --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest
  --tests de.mangalib.T03ServerRightsTest --tests de.mangalib.T03RightsDiagnosisPreparationTest:
  **107 bestanden, 0 Fehler, 0 übersprungen**.
- t03ClasspathCheck: erfolgreich, vollständige Classpaths und getrennte Allowlists
  aller drei Opt-in-Tasks, keine Kopplung an Standardtest/Build.
- t03ReadOnlyTest -Pt03ReadOnlyApproval=READ_ONLY_MANGALIB_TEST:
  **1 bestanden, 0 Fehler, 0 übersprungen**, genau ein realer Lauf.

Geschützter Spring-/JPA-Kontext erfolgreich aufgebaut und geschlossen. Über feste
Guard-URL localhost:3306/mangalib_test und tatsächliche SELECT-Identitätsprüfungen
mangalib_test sowie mangalib_test_user@localhost bestätigt. SHOW-GRANTS-Prüfung in
beiden unabhängigen read-only Transaktionen bestanden: nur eigene maskierte
Schema-DML-Rechte und global USAGE; keine weiteren Rechte/Rollen/GRANT OPTION
akzeptiert. Keine Rohgrants oder Datenzeilen ausgegeben.

Zehn erwartete InnoDB-Tabellen. Fünf Stammdatentabellen: formate=3, sammelbaende=5,
status=4, typen=4, verlage=62, insgesamt **78**. Fünf Geschäftstabellen baende,
einkaufsliste, einkaufslistedetails, mangadetails und mangareihe jeweils **0**.
Vollständige Snapshot-Metadaten umfassen Tabellen, Spalten, Indizes,
Fremdschlüssel und Schlüsselspalten. Historischer Gesamthash ist in beiden
unabhängigen Transaktionen exakt bestätigt:

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

Vorher-/Nachher-Snapshots außerdem direkt gleich verglichen. AUTO_INCREMENT wie
bisher ausgenommen. Keine Schemaabweichung, fremden Geschäftszeilen oder
Datenerhaltungsabweichung erkannt. Kein langfristiger Schutz gegen spätere externe
Schreiber und kein Beleg für zukünftige Commit-/Konkurrenzsemantik aus diesem Lauf.

Nur SELECT/SHOW, Hibernate-Schemavalidierung und read-only Transaktionssteuerung.
Keine DML/DDL, Fixtures, Migrationen, Seeding, Bereinigung oder Rechteänderung.
Kein Zugriff auf Produktiv-/Manual-DB, keine Anwendung/Listener/Browser/Scraper/
Worker gestartet. Keine zweite DB-Ausführung. Bekannte BPP-/Open-in-View-/CDS-
Warnungen ohne Eingriff; die gesonderte Boot-Writer-Proxyprüfung bleibt vor
späterer Commitabnahme erforderlich. Keine abgeschlossene T03-Gesamtabnahme.

Bericht build/reports/tests/t03ReadOnlyTest/index.html und zugehöriges JUnit-XML;
persistierte Dokumentation nur Counts/Hash, keine Zeileninhalte. git diff --check
erfolgreich. Nur TESTING.md/T03_6B_APPROVAL.md ergänzt. Keine Git-Schreibaktionen;
bestehende T03-Arbeiten erhalten. Commit-, Rollback- und Konkurrenztests bleiben
bis zu neuer ausdrücklicher Freigabe gesperrt.

## Nachtrag 06.11.2026 – Abschlussreview vor erster T03-Schreibstufe

Status **CORRECTIONS_REQUIRED**, keine DB-Verbindung. Detaillierte Bewertung der
drei Methoden, verbleibende P1-Lücken, Fixturemengen, Crash-/Abbruchgrenzen,
Boot-Proxynachweis und Git-Abgrenzung: T03_6B_APPROVAL.md Abschnitt 15.

Kleine eindeutig begrenzte Änderungen ausschließlich T03CommitAcceptanceTest:
vor Schreibarbeit vollständige historische Baseline, beide Indexkollisionen,
keine äußere TX/Synchronisation, ein gemeinsamer Coordinator, tatsächliche Proxys
und passende Advisorreihenfolge aller fünf Writer prüfen. Rollback auch auf Details
prüfen. Kaufband-IDs nach bestätigtem Threadende auch bei Future-/Outcomefehlern
erfassen. Kein produktiver Code, Guard oder bestehende Assertion abgeschwächt.
Neue Bootprüfungen nur kompiliert; Kontextstart erfordert eine neue DB-Freigabe.

Verbleibend: Gemeinsame Start-Latch beweist keine zeitliche Kaufüberlappung.
Deterministisch überlappende Aufrufe mit Serialisierung am echten Coordinator
nachweisen; keine zwei gleichzeitig aktiven Writer-TX bei dieser Architektur
erzwingen. Alte eigene drei Bände vor/nach Kauf vollständig per ID/Feldern prüfen;
globaler Leerbaselinevergleich plus registrierte IDs/Summe genügt hierfür nicht.
Keinen realen Konkurrenztest mit diesen Nachweislücken als bereit behaupten.

Tatsächlich ausgeführt, Java 21, jeweils --offline --no-daemon und vorher
identischer erfolgreicher --dry-run:
- test --rerun mit TestDatabaseGuardTest, T03PreparationTest,
  T03ReadOnlyPreparationTest, T03ServerRightsTest, T03RightsDiagnosisPreparationTest,
  LibraryWriteBootstrapTest, LibraryWriteConfigurationTest,
  LibraryWriteCoordinatorTest und PurchaseServiceTest:
  **175 bestanden, 0 Fehler, 0 übersprungen**.
- t03ClasspathCheck: erfolgreich, neue Opt-in-Quellen kompiliert, alle drei Tasks
  vollständig/getrennt; keine Opt-in-Testausführung.
- git diff --check erfolgreich; bekannte LF/CRLF-/HotSpot-CDS-Hinweise.

Kein t03ReadOnlyTest/t03RightsDiagnosisTest/t03CommitTest, Bootkontext, SQL, neue
Fixtures, Cleanup, Migration, Anwendungs-/HTTP-/Browser-/Scraper-/Workerstart.
Keine neue Abhängigkeit, kein Staging/Commit/Push. Manual-JARs und historische
Experimente unverändert. Geänderte Dateien dieses Auftrags: ausschließlich
T03CommitAcceptanceTest.java, TESTING.md, T03_6B_APPROVAL.md.

## Nachtrag 06.11.2026 – zwei verbleibende Integrationslücken geschlossen

Status **T03_6B_COMMIT_READY_FOR_APPROVAL**, ausschließlich Vorbereitung.
Aktueller verbindlicher Nachtrag T03_6B_APPROVAL.md Abschnitt 16; Abschnitt 15
bleibt als historisches Review mit damaligen Lücken erhalten.

Testlokaler Manager-Spy beobachtet echten Coordinator-Mutex/äußere Transaktionen.
Erste TX am Commitpunkt aktiv halten, zweiten Thread in tatsächlicher Mutexqueue
nachweisen, dann erster Commit/Cleanup vor zweitem Begin und zwei geordnete
Threadergebnisse verlangen. REQUIRED-Nesting zählt nicht als zusätzliche äußere TX.
Keine produktive Hook-/Sperr-/Timeoutänderung und keine zusätzliche Testtransaktion.
Beobachtungsgate begrenzt auf 4 s, Queue-Nachweis 2 s. Bei eigener Beobachterstörung
Rollbackversuch über denselben Manager; bei unbestätigtem Abschluss STOP ohne
nachgelagerte DB-Abfragen. Kein automatischer Retry oder Fixturecleanup.

Eigene drei Fixturebände vor Kauf mit unterschiedlichen Centpreisen/Anpassungen,
Lesestatus, NULL-/0-/positiven Rereads, nullable Spezialflags, Indizes, optionalen
URLs und Zeitwerten versehen. Keine zusätzlichen Zeilen; erwartete Summe 18,02
vor/18,01 nach Kauf unverändert. Alle 13 persistenten Felder aus unabhängigem
Reload als unveränderliche ID-Map aufnehmen und nach Kauf exakt vergleichen;
ausschließlich Band 4 neu. URL-Vergleich als externe Zeichenkette, ohne DNS.

Final tatsächlich ausgeführt: Java 21, stets --offline --no-daemon, vor jedem
Aufruf identischer erfolgreicher --dry-run. test --rerun mit T03BandSnapshotTest,
T03PurchaseOverlapTest, TestDatabaseGuardTest, T03PreparationTest,
T03ReadOnlyPreparationTest, T03ServerRightsTest, T03RightsDiagnosisPreparationTest,
LibraryWriteBootstrapTest, LibraryWriteConfigurationTest,
LibraryWriteCoordinatorTest und PurchaseServiceTest:
**199 bestanden, 0 Fehler, 0 übersprungen**. Davon 24 neue Fälle (16 Bandfelder/
Inventar, 8 Überlappungs-/Fehlerfälle). Frühere Zwischenläufe ebenfalls erfolgreich;
final um Fail-closed-/Timeoutprüfungen ergänzt, keine Assertions abgeschwächt.
t03ClasspathCheck einschließlich compileT03IntegrationJava erfolgreich;
alle drei Opt-in-Tasks weiterhin vollständig/getrennt. Bekannte CDS-Warnung.

Keine Datenbankverbindung, SQL, Boot-/HTTP-/Browser-/Scraper-/Workerstarts,
Migrationen, Schema-/Rechteänderungen, Fixtures oder Bereinigung ausgeführt.
Kein t03CommitTest/t03ReadOnlyTest/t03RightsDiagnosisTest. Produktionsquellen,
Guards und Manual-JARs unverändert. Geänderte/neue Dateien ausschließlich
T03CommitAcceptanceTest.java, T03PurchaseOverlap.java, T03BandSnapshot.java,
service/T03PurchaseOverlapTest.java, T03BandSnapshotTest.java, TESTING.md und
T03_6B_APPROVAL.md. Historische Experimente erhalten; kein Git-Staging/Commit/Push.
Echte Boot-Spy-/Proxyverdrahtung, MySQL-Abschlüsse und unabhängige Persistenz-
Reloads sind weiterhin nicht real ausgeführt. Neue ausdrückliche Schreibfreigabe
bleibt erforderlich; keine vollständige T03-Abnahme behaupten.
