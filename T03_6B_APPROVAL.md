# T03-6B – konkrete Freigabevorlage, KEINE Ausführungsfreigabe

Stand 04.11.2026. Branch codex/t03-data-integrity, fachliche T03-Arbeiten uncommittet.
Inventar: T03_INTEGRATION_INVENTORY.md (13 Klassen, 75 Methoden).
Noch keine DB-Verbindung, kein Fixturecommit und kein Cleanup für T03 ausgeführt.

## 1. Getrennte Stufen

1. Vorbereitung/Compile/Unitprüfungen: T03-6A, datenbankfrei erlaubt.
2. Lesende DB-Vorprüfung: eigene Freigabe erforderlich. Feste Test-URL/Benutzer,
   Serverrechte und Schema prüfen; keine Schreibprobe und kein Seeding.
3. Direkte Rollback-Integration: eigene Freigabe. Nur geeignete R/F-Methoden aus
   Inventar; Lifecycle-Fixtures mitprüfen. Keine koordinierten Writes in äußerer TX.
4. Erste Commit-/Konkurrenzstufe: ausschließlich unten vorbereitete drei Methoden,
   gesonderte ausdrückliche Commit- UND Konkurrenzfreigabe erforderlich.
5. Restliche E1–E6-Matrix: zunächst Testcode/alte Assertions entsprechend übertragen,
   datenbankfrei kompilieren, dann erweiterten Umfang separat freigeben.
6. Cleanup: NICHT Bestandteil irgendeiner obigen Freigabe.
7. T03-6C-Browser: eigene Manual-DB-/Start-/Browser-/Schreibfreigabe.

## 2. Sicherheitsvoraussetzungen vor jeder DB-Stufe

- Branch und gesamten Arbeitsstand bestätigen. Keine konkurrierenden Tests/Anwendung
  oder fremden Schreiber auf mangalib_test; ein einziger Test-JVM-Prozess.
- Ausschließlich Profil test, JDBC jdbc:mysql://localhost:3306/mangalib_test?useSSL=false,
  Benutzer mangalib_test_user, Passwort nur MANGALIB_TEST_DB_PASSWORD. Vorhandensein
  prüfen, niemals Wert oder Umgebungsvariablenlisten ausgeben.
- Keine DataSource-/Profil-/JPA-/JNDI-/Pool-/JVM-Overrides. Bestehende automatische
  TestContext-Factory und GuardedTestDataSource unverändert vor Kontextinitialisierung
  und jeder Verbindung. Keine zweite Verbindungstechnik einführen.
- ddl-auto=validate, generate-ddl=false, sql.init.mode=never, Flyway/Liquibase=false.
  Schema/Tabellen müssen bereits vorhanden sein; zehn Tabellen, InnoDB/FKs wie im
  freigegebenen Schema. Vorprüfungsabweichung = STOP, niemals reparieren/seeden.
- Vor DB zuerst datenbankfreie TestDatabaseGuardTest, danach ausgewählte Unitregression.
  Alle Gradle-Aufrufe --offline --no-daemon und vorher identischer --dry-run.
- Opt-in-Klasse prüft DataSource-Typ, tatsächliches Schema/Benutzer über bestehende
  Snapshots und SHOW GRANTS FOR CURRENT_USER() ausschließlich über geschützten JPA-
  Kontext. Akzeptiert nur SELECT/INSERT/UPDATE/DELETE auf mangalib_test.* und USAGE
  global. Andere Schemas, DDL, Rollen/unerwartete Formate oder GRANT OPTION = STOP.
  Keine Rechte ändern. Diese Rechteprüfung wurde bisher nur mit synthetischen Strings
  getestet, keine reale Serverrechtebehauptung.
- ScraperGateway vollständig durch MockitoBean ersetzt; keine zulässigen Scraper-
  Aufrufe, kein HTTP-Listener (WebEnvironment.MOCK), kein Browser/Netzwerk.

## 3. Tatsächlich vorbereitete erste Commitstufe

Task: t03CommitTest, Source-Set src/t03Integration/java, Klasse
de.mangalib.T03CommitAcceptanceTest. Drei geordnete, fail-closed Stufen:

| Methode | Facharbeit / Abschluss | Erwartete dauerhafte Zeilen |
|---|---|---|
| committedSeriesReloadsExactMoneyAndOwnedIds | Neuer synthetischer 3-Band-Bestand, 0,02 Anpassung; eigener Commit und unabhängiger read-only Reload | 1 Reihe, 1 Details, 3 Bände |
| failureAfterBandsRollsBackOwnNewRows | Neue 3-Band-Reihe, Flush, registrierte IDs, definierte RuntimeException vor äußerem Abschluss; danach Abwesenheit prüfen | keine; ID-Verbrauch/Journal bleiben |
| sameConfirmedPurchaseInTwoThreadsAppendsOnlyOnce | Eigene neue 3-Band-Reihe + Einkaufsposition, bestätigter Band 4; zwei Threads hinter CountDownLatch, UPDATED und ALREADY_PURCHASED; unabhängiger Reload | 1 Reihe, 1 Details, 4 Bände, 1 Einkauf |

Maximal im erfolgreichen Lauf verbleibend: 12 synthetische Zeilen
(2 Reihen + 2 Reihendetails + 7 Bände + 1 Einkauf). Keine Einkaufsdetails,
keine neuen Stammdaten. Zusätzlich 5 zurückgerollte Fixture-ID-Versuche. Keine DELETEs,
keine Migrationen, kein Zurücksetzen von AUTO_INCREMENT. Fehler/Teilabbrüche können
weniger Zeilen hinterlassen; niemals einen automatischen zweiten Lauf starten.

Alle Titel beginnen T03_6B_<UUID>_. Neue positive Mangaindizes werden vor Anlage
auf Kollision geprüft; Kollision bedeutet STOP, keine automatische Alternativsuche.
IDs werden tabellengebunden unmittelbar nach Flush dauerhaft ins private Journal
.gradle/t03-integration/<UUID>/ids.txt geschrieben. ID-Einträge bedeuten ERZEUGUNGS-
VERSUCHE, nicht bereits bewiesene Commits. Auch zurückgerollte IDs bleiben protokolliert.
Bei nicht erfassten, später entdeckten Zeilen gilt STOP; sie werden nicht automatisch
als eigene Daten beansprucht oder gelöscht. Vor jedem Wiederaufnehmen neue Entscheidung.

Der Task reserviert eine bisher nicht vorhandene UUID-Verzeichniskennung atomar
vor Testworker-/Springstart. Wiederverwendung schlägt fehl. Kein Standardlaufwert,
kein Passwort im Argument, kein persistenter Freigabetoken. Die technische Property
verhindert versehentliche Ausführung und ersetzt keine Eigentümerfreigabe.

Nur NACH ausdrücklicher Freigabe (UUID vorher festlegen und dokumentieren):

```powershell
$t03RunId = [guid]::NewGuid().ToString() # Einmal festlegen und vor Ausführung dokumentieren.
.\gradlew.bat --offline --no-daemon t03CommitTest `
  -Pt03Approval=COMMIT_NEW_SYNTHETIC_ROWS_ONLY "-Pt03RunId=$t03RunId" --dry-run
.\gradlew.bat --offline --no-daemon t03CommitTest `
  -Pt03Approval=COMMIT_NEW_SYNTHETIC_ROWS_ONLY "-Pt03RunId=$t03RunId"
```

Diese Befehle wurden in 6A NICHT real ausgeführt. Nur der --dry-run ohne
Freigabeeigenschaften wurde auf fehlende automatische Taskkopplung geprüft.

## 4. Datenerhalt und Fehlerbehandlung

Vorherzustand in Speicher über vorhandenes TestDatabaseSnapshot.capture:
alle zehn Tabellen, Spalten, Indizes, FKs und Tabellenmetadaten; keine Ausgabe oder
Speicherung roher Datenbankinhalte. AUTO_INCREMENT ausgenommen.
T03Preservation vergleicht jede vorherige Geschäftszeile anhand ihrer aus den
Schema-Metadaten bestimmten PK-Spalte exakt; auch unbekannte Spalten enthalten.
Keine Annahme „Primärschlüssel ist immer Spalte 1“. Stammdaten und Schema müssen
exakt gleich bleiben. Neue Geschäftszeilen nur mit registrierten IDs zulässig;
vorher vorhandene IDs dürfen niemals als neue Fixtures registriert werden.

Absichtlich committete neue Zeilen verändern den Gesamtdatenfingerabdruck. Ein
identischer Gesamthash ist hier KEIN Erfolgskriterium. Journal enthält UUID, IDs
und feste Abschlussphasen, keine Secrets oder fachlichen Feldwerte. Journal wird
geflusht; eigener Ressourcenabschluss per close. Bei Journalausfall/Assertion/
unbekanntem Commitabschluss keine weitere Stufe. TestWatcher-/AfterEach-Sperre und
Gradle failFast verhindern beabsichtigte Fortsetzung, kein Cleanup im finally.

Grenzen: Prozessabsturz zwischen Commit und nachgelagerter ID-Erfassung kann eine
unregistrierte eigene Zeile hinterlassen. Die Snapshots erkennen das im laufenden
Prozess, sind aber kein crashfestes Originaldatenbackup. Nach Host-/JVM-Abbruch
keinen Datenerhalt ohne neue lesende Untersuchung behaupten und nicht automatisch
fortsetzen. Auch „unbekannter Commit“ darf nicht durch Retry aufgelöst werden.

Separates späteres Cleanup-Konzept: Journal-IDs gegen aktuellen Titelpräfix,
Foreign Keys, erwartete Referenzen und aktuellen Zustand lesen; nur eindeutig
eigene, konkret aufgelistete Zeilen freigeben lassen. Exakte IDs/Reihenfolge und
alle Referenzen vor Löschfreigabe dokumentieren. Keine Löschfunktion in 6A/6B,
kein pauschales Prefix-DELETE, TRUNCATE oder ID-Zählerreset.

## 5. Vollständige fachliche E1–E6-Matrix / begründete Restarbeiten

| Gruppe | Vorbereitet / vorhandene datenbankfreie Nachweise | Vor einer vollständigen Integrationsabnahme zusätzlich nötig |
|---|---|---|
| E1 Atomarität | Eigener Commit + Reload, definierter Rollback nach Bandanlage; PurchaseService-Mocks mit Phasenfehlern | Beide echten Kaufendpunkte und Kauf in neue Reihe, Fehler bei Markierung, Flush-/beforeCommit-Fehler; keine Netzwerk-/Verbindungsabbrüche improvisieren. |
| E2 Wiederholung/Konkurrenz | Zwei Threads gleicher bestätigter Kauf; Fake-Mutex bis Managercleanup | Unterschiedliche Kaufwege, zwei Einkäufe gleicher vorhandener/neuer Reihe, Kauf gegen Anlage/Bandänderung, reale Lockwartezeiten. |
| E3 Identifikation | BandInventory/PurchaseService-Mocks, IDs und neue Bandnummer in Opt-in-Stufe | NULL-/mehrdeutige Indizes, Editionen, unsortierte Listenadapter und veraltete Bestätigung mit realem Reload; keine Altzeilen dafür ändern. |
| E4 Preise | Reale DECIMAL-0,02-Verteilung + Kauf 0/-0,01 vorbereitet; MoneyTest-Breite | 20/3, beide Vorzeichen, Pflichtpreis-/Leerfälle, historische NULLs und individuelle Altpreise nach Commit/Reload. |
| E5 Anzahl/M06 | BandCount-/M06-Fakes, normale Appendtransaktion vorbereitet | Zu hohe/niedrige Deklaration korrigieren, reine Korrektur ohne Finanzmutation, Reduktion/Teilkorrektur, abweichende M06-Deklaration und konkurrierender Kauf. |
| E6 Integrität | Vorher-/Nachher-Vergleich aller alten Zeilen/Schema + registrierte neue IDs | Reale Ausführung der Helfer gegen MySQL und Nachkontrolle bei Teilabbruch; keine Dateninhalte als Logs persistieren. |

Alle Restfälle brauchen getrennten opt-in-Testcode mit denselben Schutzgrenzen,
Kompilierung und konkret ergänzter Freigabe. Die erste Stufe ist KEINE Abnahme
aller E1–E6-Anforderungen und KEINE Freigabe für eine ungefilterte Vollsuite.
Die historischen koordinierten Schreibtests bleiben bewusst unangetastet und
aktuell inkompatibel mit einer äußeren Testtransaktion (Inventarmatrix).

## 6. T03-6C-Browsercheckliste (nur vorbereitet)

Nur nach neuer Freigabe manual-test/mangalib_manual_test, eigener Benutzer,
127.0.0.1:18080 und aktueller geprüft gepackter Kandidat. In 6A kein neues JAR.

1. Neue eigene synthetische Datensätze mit Laufpräfix; Banner/Listener/Guards prüfen.
2. Bestehende Zielreihe: Vorschau ohne Write, Cancel, expliziter Nummernbereich,
   genau ein Speichervorgang, Reload mit unveränderten Altbandwerten.
3. Neue Reihe: Statusauswahl, Kauf, Reload. Bereits gekauft: feste 200-Meldung ohne
   neue Bände. Veraltete Bestätigung: fester 409-Text, kein Retry.
4. Preisoptionen aus, bekannte/NULL-Altpreise erhalten; explizite 0 und negative
   Anpassung, ungültige/geleerte Preise kontrolliert abweisen.
5. Korrektur auf tatsächlichen Bestand ohne Erweiterungs-/Preisoption, getrennt
   speichern. Ausdrückliche Erweiterung mit gültigen neuen Preisen; Reduktion und
   Teilkorrektur ohne Anlage ablehnen. IDs, Lesestatus/Rereads nach Reload prüfen.
6. Zwei Tabs: wiederholter Kauf bzw. veralteter Bereich; keine doppelten Writes.
   CSRF/Host/Origin aktiv; Sessionfehler behalten Dialogwerte, kein Auto-Replay.
7. Unklarer Erfolg/5xx/Bestandsabweichung: STOP, keine Wiederholung/Bereinigung.
   Nur eigene eindeutig zugeordnete Datensätze prüfen; synthetische Scraperdoubles.

## 7. Konkret vorbereitete lesende Vorprüfung (noch NICHT ausgeführt)

Eigener Task **t03ReadOnlyTest**, ausschließlich Klasse
de.mangalib.T03ReadOnlyPreflightTest im getrennten Source-Set t03Integration.
Der Task enthält keine Abhängigkeit zu t03CommitTest oder normalem test und ist
nicht an check/build gekoppelt. Beide Opt-in-Tasks besitzen jetzt eine feste,
gegenseitig getrennte Klassen-Allowlist. Der Commit-Test und sein ID-Journal werden
bei der lesenden Prüfung nicht initialisiert. Keine Lifecycle-Fixtures, kein
Coordinator, keine Schreibservices, keine HTTP-Anfragen und kein Listener.

Konfigurationsquellen: Spring lädt application.properties und für das feste Profil
test application-test.properties; höhere Umgebungs-/System-/ConfigData-Overrides
werden nicht als vertrauenswürdig angenommen. Der bestehende TestContext-Guard
prüft das gebundene effektive Environment vor Singleton/JPA-Initialisierung;
GuardedTestDataSource prüft erneut und übergibt dem Treiber ausschließlich seine
festen URL-/Benutzerkonstanten und MANGALIB_TEST_DB_PASSWORD. Alternative DataSources,
JNDI/Pool-Zusätze, direkte Hibernate-Properties, andere Profile und Initialisierung
bleiben gesperrt. Produktivzugangsdaten werden nicht benötigt.

Aktuelle reine Prozessprüfung am 04.11.2026: Passwortvariable vorhanden/nicht leer;
keine relevanten SPRING-/JPA-/JVM-Overridevariablen vorhanden. Keine Werte ausgegeben.
Diese Prüfung vor späterer Ausführung wiederholen; sie ersetzt keinen Laufzeitguard.
Projekt-/Benutzer-gradle.properties auf relevante Overrides soweit vorhanden geprüft.
Die endgültigen Boot-Eigenschaften sind erst im freigegebenen Kontext verbindlich
gebunden; in der Vorbereitung wurde kein solcher Kontext erzeugt.

Die Klasse verwendet vorhandenes TestDatabaseSnapshot.capture und T03ServerRights,
jeweils in einer explizit read-only TransactionTemplate-Transaktion (20 s). Zwei
unabhängige Snapshots verhindern einen Vergleich innerhalb desselben gemeinsamen
Repeatable-Read-Snapshots. Hibernate startet nur im bestehenden validate-Modus;
Initialisierung/Migrationen bleiben durch den unveränderten Guard deaktiviert.
JPA-/JDBC-Transaktionssteuerung einschließlich Abschluss einer read-only Transaktion
ist notwendig; sie ist keine Freigabe für fachliche Datencommits.

Vorgesehene DB-Anweisungen: SHOW GRANTS FOR CURRENT_USER(), SELECT database(),
SELECT CURRENT_USER() (vollständiger geprüfter Testaccount einschließlich Hostanteil), SELECT * auf
zehn fest erlaubten Tabellen sowie SELECTs auf information_schema für Tabellen,
Spalten, Indizes, Fremdschlüssel und Schlüsselspalten. Keine dynamischen Ziele,
INSERT/UPDATE/DELETE/DDL, Locks für Fachwrites oder Dateninitialisierung.
Die tatsächliche Accountidentität wird nach fester Syntaxprüfung ausgegeben;
die Grantzeilen werden konservativ geprüft und nicht geloggt.

Erwartungsbindung an den letzten dokumentierten Zustand:

- Zehn fest benannte InnoDB-Tabellen, vorhandene FK-/Schlüsselmetadaten.
- 78 vorhandene Stammdatensätze, jede der fünf Stammdatentabellen nicht leer.
- Keine Geschäftsdatensätze in mangareihe/mangadetails/baende/einkaufsliste/
  einkaufslistedetails. Jede vorhandene Zeile wird als ungeklärt behandelt: STOP,
  keine automatische Zuordnung zu einem eigenen historischen Testlauf.
- Vollständiger Daten-/Schemahash
  ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
  AUTO_INCREMENT bleibt wie im vorhandenen Snapshot ausgenommen.

Diese Erwartungen wurden NICHT neu an der DB bestätigt. Jede Abweichung verlangt
eine getrennte lesende Klärung und gegebenenfalls neue Eigentümerentscheidung;
keine automatische Änderung der Erwartung oder der Daten. Ausgaben ausschließlich
geprüfter Account, Hash, feste Tabellennamen, Counts und neutrale STOP-Meldungen, keine Zeilenwerte,
Passwörter oder Grantrohdaten. Auch Snapshot-Assertions geben keine Rohzeilen aus.

### Exakte spätere Befehlsfolge – erst nach ausdrücklicher Freigabe

Zunächst Umgebung/Branch/Overrides wie oben erneut ausschließlich lesend prüfen.
Keine andere Anwendung/Testausführung darf gleichzeitig mangalib_test ändern.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest --dry-run
.\gradlew.bat --offline --no-daemon test --tests de.mangalib.T03PreparationTest --tests de.mangalib.T03ReadOnlyPreparationTest
.\gradlew.bat --offline --no-daemon t03ReadOnlyTest -Pt03ReadOnlyApproval=READ_ONLY_MANGALIB_TEST --dry-run
.\gradlew.bat --offline --no-daemon t03ReadOnlyTest -Pt03ReadOnlyApproval=READ_ONLY_MANGALIB_TEST
```

Nur bei erfolgreicher jeweiliger Vorstufe fortsetzen. Die technische Property
ersetzt die ausdrücklich noch fehlende Eigentümerfreigabe nicht. In diesem Auftrag
wurde nur der Taskplan ohne Freigabeeigenschaft geprüft und Quellcode kompiliert;
keine obige Testausführung fand statt. 475 Javafälle bleiben historische Baseline.
Vier neue synthetische T03ReadOnlyPreparationTest-Fälle sind kompiliert, nicht gelaufen.

Beantragt werden kann ausschließlich: diese lesende Vorprüfung gegen mangalib_test /
mangalib_test_user / localhost:3306 / Profil test / Passwort aus Prozessvariable,
einschließlich unverändertem Guard, Schema-validierendem Mock-Testkontext und
lesender Transaktionssteuerung. Kein t03CommitTest, kein Cleanup und kein Manualzugriff.

Die nachfolgende erste Commit-/Konkurrenzstufe bleibt konzeptionell geeignet,
ist aber bis zum erfolgreichen realen Vorprüfungsbericht und neuer separater
Commit-/Konkurrenzfreigabe gesperrt. Ihre drei Methoden und Fixturemengen wurden
nicht erweitert. Der Snapshotvergleich meldet jetzt Fehler ohne rohe Feldwerte;
seine Gleichheitskriterien sind unverändert. Keine Abnahme daraus vorwegnehmen.

## 8. Tatsächliche Ausführung am 05.11.2026 – FAILED / keine Wiederholung

Die Eigentümerfreigabe für genau einen lesenden Lauf wurde genutzt. Alle drei
identischen Offline-Dry-Runs waren erfolgreich; 44 Guard- und 11 Hilfstests
bestanden (jeweils keine Fehler/Skips). t03ReadOnlyTest wurde danach genau einmal
ausgeführt und scheiterte mit einem fehlgeschlagenen Test beim Spring-Kontextaufbau:
NoClassDefFoundError / ClassNotFoundException für
de.mangalib.windowsadapter.ProductionScraperClient bei der Introspektion von
LiveScraperGateway. Kein JPA-Start, keine Datenbankverbindung oder Prüfanweisung.

DB-/Benutzeridentität, Serverrechte und historische Baseline wurden somit NICHT
real verifiziert. Kein neuer Fingerabdruck vorhanden. Keine Datenänderung,
Fixture, Bereinigung oder Migration. Keine Wiederholung oder Guardänderung.
Der dokumentierte Infrastrukturverdacht betrifft die früh gebundene Task-Classpath
gegenüber der später ersetzten t03Integration.runtimeClasspath; Details TESTING.md.
Erst gezielt datenbankfrei beheben/prüfen, danach neue lesende Ausführungsfreigabe
einholen. Commit- und Konkurrenzstufe unverändert gesperrt; kein t03CommitTest,
keine Vollsuite, kein Git-Commit/Push/Staging in dieser Abnahme.

## 9. Classpathkorrektur am 05.11.2026 – CLASSPATH_FIX_DB_FREE_VERIFIED

Der Fehler aus Abschnitt 8 wurde datenbankfrei reproduziert und behoben. Beide
Opt-in-Tasks hielten die frühere Runtime-FileCollection fest, obwohl später das
Source-Set-Attribut ersetzt wurde. Nun verzögerte gemeinsame Auflösung über
files(providers.provider { t03Integration.runtimeClasspath }); keine zweite
unabhängige Dependencyliste, keine Produktiv-/Profil-/Guardänderung.

Neuer Task t03ClasspathCheck nach identischem Offline-Dry-Run erfolgreich:
beide effektiven Task-Classpaths entsprechen exakt der abschließenden
t03Integration.runtimeClasspath (165 Einträge, missing=[]). Je neun erforderliche
Klassen samt Signaturen ohne Initialisierung auflösbar; Negativkontrollen ohne
Owner reproduzieren den ursprünglichen Klassenfehler. Getrennte Allowlists,
Standardtest-/Lifecycletrennung und aktuelle Manual-Classpath automatisiert geprüft.
compileT03IntegrationJava erfolgreich geprüft (UP-TO-DATE), 44 Guard- und
11 Hilfstests bestanden, keine Fehler oder Skips. Details/Nachweisgrenzen TESTING.md.

Abschnitt 8 bleibt FAILED, kein nachträglicher DB-Nachweis. Weder t03ReadOnlyTest
noch t03CommitTest erneut ausgeführt; keine DB-Verbindung. Vor einer neuen
lesenden Ausführungsfreigabe zusätzlich t03ClasspathCheck (stets offline/no-daemon
und zuerst identischer --dry-run) in die Vorstufen aufnehmen. Die technische
Freigabeproperty aus Abschnitt 7 ist keine fortbestehende Eigentümerfreigabe.
Commit-/Konkurrenztests weiterhin separat gesperrt. Historische Manual-Artefakte
unverändert; ausschließlich der bereits gepatchte Kandidat ist paketbereinigt,
zwei ältere JARs enthalten noch JNA 5.13.0 (keine Neuerzeugung in diesem Auftrag).

## 10. Erneuter freigegebener lesender Lauf am 05.11.2026 – FAILED

Nach neuer ausdrücklicher Freigabe 55 datenbankfreie Guard-/Hilfstests tatsächlich
neu ausgeführt (0 Fehler/Skips), t03ClasspathCheck erfolgreich. Jeder Aufruf offline/
no-daemon nach identischem Dry-Run. Anschließend t03ReadOnlyTest genau einmal.

Spring-/JPA-Kontext startet jetzt erfolgreich; CURRENT_USER() bestätigt
mangalib_test_user@localhost. Bestehender Guard erzwingt ausschließlich Profil test
und feste mangalib_test-URL. SHOW GRANTS wurde erreicht, aber der konservative
Rechteparser stoppt mit „Unbekannte Testrechte/Role: STOP.“ Keine Grantrohdaten
protokolliert; keine Behauptung, dass Rechte bereits als zulässig bewiesen seien.
Konkrete Syntax-/Rechteursache ohne weitere Evidenz offen.

Abbruch vor TestDatabaseSnapshot.capture: zusätzliche SELECT database()-Assertion,
zehn Tabellen/78 Stammdaten/leere Geschäftstabellen und historischer Gesamthash
noch NICHT bestätigt. Keine DML/DDL, Fixtures oder Bereinigung; keine Wiederholung,
Codekorrektur oder weitere DB-Verbindung. Nur Dokumentationsnachträge; Details und
Kontextwarnungen in TESTING.md. Status FAILED, Commit-/Konkurrenztests weiterhin
gesperrt. Eine erneute DB-Diagnose oder Vorprüfung benötigt eine neue Freigabe.

## 11. Rechteparseruntersuchung 05.11.2026 – DIAGNOSIS_REQUIRED

Datenbankfrei bestätigt: bisheriger Parser lehnt gültiges maskiertes
`mangalib\_test`.* ab und nimmt unmaskiertes mangalib_test.* ohne Nachweis seiner
Wildcard-Semantik an. Korrigiert ausschließlich in der Testinfrastruktur:
explizit maskiertes exaktes Schema zulassen, unmaskiertes Schema konservativ
ablehnen, gleiche vier DML-Rechte/global USAGE, sonst STOP. Feste Fehlerkategorien
ohne rohe Grants. Tatsächliche Serverdarstellung weiterhin unbekannt; keine neue
Abnahmebehauptung. Details, offizielle Quellen und Testgrenzen in TESTING.md.

178 ausgewählte datenbankfreie Tests (davon 40 neue Parser-/2 neue Bootstrapfälle)
bestanden; Classpath-/Tasktrennungsprüfung und Opt-in-Kompilierung erfolgreich.
Bootstrap-Fakes zeigen trotz derselben frühen BPP-Warnungen funktionierende
Serviceproxies, äußeren Writeadvisor vor Transaktionsadvisor und Commit/Rollback-
Cleanup. Vollständigen Boot-Proxy-Nachweis vor Schreibabnahme noch ergänzen;
keine Produktion/Security-Konfiguration nur zur Warnungsunterdrückung geändert.

### Vorschlag: einmalige kleine lesende Diagnose, NOCH NICHT freigegeben

Falls die tatsächliche Grantnotation vor der nächsten vollständigen Vorprüfung
geklärt werden soll: eigener ausdrücklich freizugebender Diagnoseumfang, weiterhin
ausschließlich existierender Guard/JPA-Pfad, Profil test, feste Test-URL/Benutzer,
Passwort nur aus Prozessvariable. Kein CLI-/zweiter JDBC-Zugriffsweg.

- Nur SELECT DATABASE(), SELECT CURRENT_USER(), SELECT @@GLOBAL.partial_revokes
  und SHOW GRANTS FOR CURRENT_USER(); keine Geschäftstabellen/Fixtures/Writes.
- Vor Ausgabe Identität exakt prüfen. partial_revokes lediglich als ON/OFF/UNKNOWN
  ausgeben; unbekannte/fehlende Variable bedeutet STOP, kein Konfigurationswechsel.
- Grantzeilen nur im Speicher auswerten. Ausgabe ausschließlich feste Kategorien:
  Zeilenanzahl, GLOBAL_USAGE/EXACT_ESCAPED_TEST_SCHEMA/PLAIN_TEST_SCHEMA/
  OTHER_OR_UNSUPPORTED_SCOPE, vorhandene DML-/unerlaubte-Privileg-Kategorien,
  Rollen-/GRANT-OPTION-Erkennung und Parser-Reason. Keine fremden Zielnamen,
  Accounts, SQL-Rohtexte, Hashes von Secrets oder unbeschränkte Exceptiontexte.
- Schon eine unklare Zeile bedeutet STOP; solche Zeilen nicht als harmlos umdeuten.
  Keine Rechteänderung, automatische Reparatur oder Wiederholung.

Dieser Diagnosecode/-task ist ein Vorschlag, noch nicht implementiert/ausgeführt.
Die zusätzliche Servermodusabfrage erweitert den bisherigen SQL-Umfang und braucht
deshalb explizite Freigabe. Alternativ kann der Eigentümer allein die redigierte
Schema-Notation und partial_revokes-Einstellung bereitstellen. Bis dahin ist die
Ursache des historischen Serverabbruchs nicht sicher bestimmt. Kein t03ReadOnlyTest,
t03CommitTest oder anderer DB-Test in diesem Korrekturauftrag. Neue reale Prüfung
weiterhin nur nach neuer ausdrücklicher Freigabe; keine automatische Fortsetzung.

## 12. Einmalige freigegebene Rechte-Diagnose – RIGHTS_CONFIGURATION_BLOCKED

05.11.2026: Vorschlag aus Abschnitt 11 nach gesonderter ausdrücklicher Freigabe
implementiert und genau einmal ausgeführt. Eigener Task t03RightsDiagnosisTest,
ausschließlich T03RightsDiagnosisTest, bestehender Guard/JPA-Zugriffsweg und
read-only Transaktion. Keine Tabelleninhalte; nur die vier freigegebenen Queries.
Vorher 107 datenbankfreie Guard-/Parser-/Diagnosefälle grün und Classpath-/
Trennungsprüfung aller drei Opt-in-Tasks erfolgreich, stets Offline-Dry-Run zuerst.

Ergebnis: DB und Account exakt bestätigt, partial_revokes OFF, zwei Grantzeilen
mit GLOBAL_USAGE und EXACT_ESCAPED_TEST_SCHEMA. Alle vier DML-Rechte vorhanden,
zusätzlich DDL und OTHER (nicht erlaubte Rechtekategorien). Keine Rollen oder
GRANT OPTION erkannt. Keine Rohtexte/Accountdetails/fremden Schemanamen geloggt.
Alter Parser REJECTED, korrigierter Parser FORBIDDEN_PRIVILEGE. Diagnosetest
folgerichtig 1 fehlgeschlagen/0 Skips; keine Wiederholung.

Historische Ablehnung durch maskierte Notation damit erklärt; außerdem liegt
ein tatsächlicher Rechteverstoß vor, der auch nach Formatkorrektur STOP erfordert.
Kein weiterer Parserfix nötig, keine Lockerung. Administratorentscheidung zur
Minimalberechtigung des Testaccounts erforderlich; keine Änderung durch Codex.
Erst danach und nach neuer Freigabe Rechte erneut verifizieren/volle lesende
Vorprüfung erwägen. Schema-/Stammdaten-/Geschäftsdatenfingerprint nicht erhoben.
Keine Datenänderung, keine andere DB-Abnahme oder Commit-/Konkurrenzausführung.
Details, sichere Ergebniskategorien und tatsächliche Befehle in TESTING.md.

## 13. Rechte nach Eigentümerkorrektur – RIGHTS_DIAGNOSIS_VERIFIED

05.11.2026: Eigentümer bestätigt administrative Änderung und erteilt Fortsetzung.
Nach 107 erneut bestandenen datenbankfreien Guard-/Parser-/Hilfstests und
erfolgreichem t03ClasspathCheck genau ein weiterer t03RightsDiagnosisTest.
Alle Aufrufe offline/no-daemon nach identischem Dry-Run. Diagnose: 1 bestanden,
0 Fehler/Skips. Datenbank/Account exakt erwartet, partial_revokes OFF, zwei Grants:
GLOBAL_USAGE und EXACT_ESCAPED_TEST_SCHEMA, ausschließlich USAGE und vier DML-
Rechte. Keine Rollen/unbekannten Anweisungen, DDL/OTHER oder GRANT OPTION.
Korrigierter Parser ACCEPTED; historischer Parser wegen Maskierung REJECTED.

Rechteblocker aus Abschnitt 12 damit im geprüften Umfang behoben, historischer
Befund unverändert erhalten. Keine Rechte-/Datenänderung durch Codex und keine
Abfragen von Geschäfts-/Stammdaten. Neue ausdrückliche Freigabe für die vollständige
lesende Vorprüfung kann jetzt sinnvoll erteilt werden; deren Fingerabdruck-/
Tabellenprüfung ist nicht Teil dieses Erfolgs. Commit-/Konkurrenzstufe bleibt
unverändert gesperrt. Keine automatische Fortsetzung mit anderen DB-Tasks.

## 14. Vollständige lesende Vorprüfung – T03_6B_READ_ONLY_VERIFIED

05.11.2026: Nach eigener neuer Freigabe und 107 erfolgreichen datenbankfreien
Guard-/Parser-/Vorbereitungstests sowie erfolgreichem t03ClasspathCheck genau ein
t03ReadOnlyTest ausgeführt. Alle Aufrufe offline/no-daemon nach identischem
Dry-Run. Lesender Test **1 bestanden, 0 Fehler, 0 Skips**. Kein anderer DB-Task.

Kontext-/JPA-Start, tatsächliche DB-/Accountidentität und konservative Rechteprüfung
in beiden read-only Transaktionen erfolgreich. Zehn erwartete InnoDB-Tabellen,
78 Stammdaten (3 Formate, 5 Sammelbandtypen, 4 Status, 4 Typen, 62 Verlage), fünf
leere Geschäftstabellen. Spalten/Indizes/Fremdschlüssel/Schlüsselmetadaten und alle
vorhandenen Zeilen durch Gesamtsnapshot erfasst. Vorher/Nachher direkt gleich und
jeweils historischer Fingerabdruck:

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

AUTO_INCREMENT ausgenommen. Keine Abweichung oder Datenänderung erkannt;
keine DML/DDL, Fixtures, Bereinigung, Rechteänderung oder Wiederholung. Historische
Fehlversuche bleiben unverändert dokumentiert. Details/Befehle in TESTING.md.
Die lesende Baseline ist damit bestätigt; dies ist weder eine Freigabe noch ein
Erfolgsnachweis für Commit-/Rollback-/Konkurrenzfälle oder die gesamte E1–E6-Matrix.
Vor Schreibabnahme tatsächliche Boot-Writer-Proxys/Advisorreihenfolge prüfen und
separate konkrete Freigabe einholen. Keine automatische weitere DB-Ausführung.

## 15. Review vor erster Schreibfreigabe (06.11.2026): CORRECTIONS_REQUIRED

Kein Datenbankzugriff in diesem Auftrag. Die erfolgreiche lesende Abnahme aus
Abschnitt 14 bleibt historischer Nachweis, keine fortbestehende Schreibfreigabe.
Erste Commit-/Konkurrenzstufe derzeit NICHT unverändert zur Ausführung empfehlen.

### Drei Methoden / Aussagekraft

| Methode | Belastbare Vorbereitung | Offene Grenze |
|---|---|---|
| committedSeriesReloadsExactMoneyAndOwnedIds | Coordinator kehrt erst nach Managerabschluss zurück; neue read-only TX, em.clear, Reload von Reihe/3 Bänden, 18,02 und Restcent 0,01/0,01/0,00 | Noch nicht real ausgeführt; keine simulierte JDBC-Commitstörung. |
| failureAfterBandsRollsBackOwnNewRows | Verschachtelte create-Arbeit bleibt in äußerer Coordinator-TX, Flush vor SyntheticFailure; unabhängiger Reload prüft Reihe, Details und Bände abwesend | Testet gezielten Work-Rollback, nicht Markierungs-/Commit-/Rollback-/Verbindungsfehler des echten Servers. |
| sameConfirmedPurchaseInTwoThreadsAppendsOnlyOnce | Eigene Reihe/Kaufposition, zwei Executor-Aufgaben, UPDATED/ALREADY_PURCHASED, Reload von Kaufmarkierung/4 Bänden/18,01 | Start-Latch beweist keine tatsächliche Überlappung. Alte drei Bände werden nicht vollständig auf unveränderte IDs/Felder geprüft. |

**P1 / verbleibender Korrekturbedarf – Konkurrenz:** Zwei Tasks können trotz
gemeinsamer Start-Latch vollständig nacheinander laufen. Nötig ist ein begrenzter
testlokaler Beobachtungspunkt bei einer bereits aktiven ersten Kauftransaktion,
danach nachweislicher Eintritt/Warten des zweiten Aufrufs am realen gemeinsamen
Coordinator, dann Freigabe der ersten Transaktion und Abschluss beider Threads.
Warten/Blockieren muss innerhalb der bestehenden 5-s-Mutexfrist bounded sein;
keine sleep-basierte Glücksprüfung und keine produktive Testhook-Architektur.
Ein gleichzeitig aktives zweites Schreibtransaction ist hier ausdrücklich NICHT
das Ziel: Der beschlossene JVM-Mutex soll genau das verhindern. Der Nachweis muss
überlappende Kaufanforderungen und zwei geordnete tatsächliche Transaktionen zeigen.
Für konkurrierende DB-Rowlocks/Mehrinstanzbetrieb wäre ein getrenntes Szenario nötig;
der vorhandene Einzel-JVM-Test darf diese Eigenschaft nicht behaupten.

**P1 / verbleibender Korrekturbedarf – Banddatenerhalt:** Vor dem Kauf eigene drei
persistierte Bände per ID mit allen Geschäfts-/Metadatenwerten aufnehmen. Nach
unabhängigem Reload identische IDs/Nummern/Preise/Anpassungen/Lesestatus/Rereads/
Flags/URLs/Beziehungen prüfen; ausschließlich neuen Band 4 zulassen. Der globale
Baselinevergleich reicht dafür nicht: Die ursprüngliche DB ist leer, und das
ID-Journal erlaubt Änderungen/Abwesenheit bereits registrierter eigener Zeilen
(sonst könnten die absichtlich zurückgerollten IDs nicht enthalten bleiben).
Vier beliebige neue Bände mit passender Summe wären derzeit kein ausreichender
Beweis für M06-/Identitätserhalt der zuvor angelegten drei Bände.

### Kleine Testkorrekturen in diesem Review bereits umgesetzt

- BeforeAll prüft vor Schreibarbeit keine äußere Transaktion/Synchronisation,
  genau einen Coordinator und alle fünf Writer als tatsächlich injizierte Advised-
  Proxys. Derselbe Writeadvisor muss vorhanden sein, vor einem vorhandenen
  Transaktionsadvisor liegen und deklarierte TX-Annotationen müssen einen solchen
  Advisor besitzen. Kein Fehler wird als harmlos ignoriert.
- Baseline vor erster Fixture an vollständige T03ReadOnlyPolicy gebunden: zehn
  InnoDB-Tabellen, 78 Stammdaten, leere Geschäftstabellen, historischer Fingerabdruck.
  Beide für neue Reihen verwendeten Mangaindizes auf Kollision prüfen.
- Rollback prüft nun auch MangaDetails explizit abwesend.
- IDs der nach dem Kauf beobachtbaren Bände werden nach bestätigtem Executorende
  auch im finally erfasst, also ebenfalls bei Future-/Outcome-Assertionsfehlern.
  Kein Read/ID-Anspruch bei nicht bestätigtem Threadende. Spätere gleiche
  Registrierungen sind wie bisher idempotent; keine Löschung/Retryfunktion.

Die neuen Boot-Proxysperren sind nur kompiliert, NICHT im echten Bootkontext
ausgeführt. Der volle Kontext verwendet JPA-Schemavalidierung und darf dafür in
diesem Auftrag nicht gestartet werden. Datenbankfreie LibraryWriteBootstrapTest-
Fälle reproduzieren BPP-Warnungen mit echter Advisor-/Guard-Konfiguration und
Fake-TX; das ist ein gezielter Teilnachweis, kein voller Boot-/JPA-Nachweis.
Keine produktive Konfiguration oder Sicherheitsregel zur Warnungsvermeidung ändern.

### Fixture-/Journal-/Abbruchbewertung

Erfolgreiche Menge bleibt maximal zwölf Zeilen: erste Reihe/Details/3 Bände = 5,
zweite Reihe/Details/4 Bände/Kauf = 7. Rollbackversuch hinterlässt keine Zeilen,
aber fünf versuchte IDs und verbrauchte AUTO_INCREMENT-Werte. Keine Stammdaten-
Schreiboperation, kein DELETE. Die maximal zwölf Zeilen sind derzeit statisch
hergeleitet, noch kein realer Erfolgsbefund.

Neue Lauf-UUID atomar reserviert; CREATE_NEW-Journal, synchronisierte tabellen-
gebundene ID-Mengen, force(true) pro Eintrag, keine Rohdaten/Secrets. Old-row-
Vergleich anhand Schema-PK-Ordinalität erhält sämtliche vorherigen Zeilen sowie
Schema/Stammdaten und weist nicht registrierte neue Zeilen ab. Kein Cleanup.
OutcomeObserver/BeforeEach/AfterEach und failFast stoppen Folgestufen bei Fehlern.
Die IDs bedeuten Versuche, nicht Commitbelege. Crash zwischen Kaufcommit und
nachgelagertem ID-Read bleibt eine dokumentierte Lücke; RAM-Snapshot geht bei
JVM-Abbruch verloren. Kein crashfestes Eigentums-/Backupversprechen oder
automatisches Wiederaufnehmen. Bei unbekanntem TX-/Threadabschluss ist außerdem
der Zustand laufender Arbeit offen; diese darf nicht durch Folgeaktionen als
beendet behandelt werden. Spätere Klärung nur separat lesend genehmigen lassen.

### Transaktionsarchitektur / Git-Abgrenzung / nächste Freigabe

Coordinator: tryLock vor TransactionTemplate, Sperre bis Manager-Rückkehr inklusive
Cleanup, Nested Calls nur bei eigener aktiver TX. Workfehler rollen zurück;
Begin-/Commit-/Rollback-/Cleanupfehler bzw. fehlende/UNKNOWN-Completion sperren
Folgewrites. Fake-/AOP-Tests prüfen diese Reihenfolge und konservative Sperre.
Pessimistische Einkauf-vor-Reihe-Lockreihenfolge im Kaufcode; gleiche JVM schützt
über gemeinsamen Mutex. Tatsächliches MySQL-Lockwarten ist noch nicht bewiesen.

Arbeitsstand gegenüber HEAD c20c213: 17 Produktiv-/Frontenddateien, 29 Test-/
Infrastrukturdateien, 6 Dokumente, build.gradle.kts sowie 8 historische unversionierte
Experimente (insgesamt 61 Pfade). SECURITY_AUDIT.md ignoriert/unverfolgt. Keine
Build-JARs/Logs/DB-Dumps in diesen Commitkandidaten. Acht Experimente ausschließen.
Ein gezielter lokaler Implementierungs-/Vorbereitungscommit vor DB-Schreibarbeit
ist zur Reproduzierbarkeit sinnvoll, aber kein T03-Abschlusscommit. Produktivcode/
Unit-/DOM-Verträge von Opt-in-Infrastruktur/Dokumentation sinnvoll abgrenzen,
Abhängigkeiten erhalten, nur explizite geprüfte Pfade stagen und vorher eigene
Commitfreigabe einholen. In diesem Auftrag weder Staging noch Commit.

Empfehlung: zuerst eng begrenzte Korrektur der beiden P1-Testlücken und ihre
datenbankfreie Absicherung. Danach neue konkrete Freigabe für genau diese drei
Methoden, UUID und maximal zwölf bleibende Fixturezeilen; Boot-Proxysperren vor
jedem ersten Write wirksam prüfen. Keine neue Baseline voraussetzen oder Daten
herstellen. Keine Ausführung in diesem Review und keine Freigabe der Gesamtmatrix.

## 16. Zwei Testlücken geschlossen (06.11.2026) – T03_6B_COMMIT_READY_FOR_APPROVAL

Die zwei P1-Vorbereitungslücken aus Abschnitt 15 sind jetzt durch gezielten
Opt-in-Testcode und datenbankfreie Regressionen geschlossen. Dies ist ausdrücklich
keine reale Schreibabnahme und keine Ausführungsfreigabe.

### Deterministischer Überlappungsnachweis

T03CommitAcceptanceTest verwendet einen MockitoSpyBean des bestehenden
PlatformTransactionManager, keinen Ersatzmanager und keine zusätzliche äußere
Testtransaktion. Vor jedem ersten Fixturewrite wird zusätzlich geprüft, dass der
Coordinator genau diesen injizierten Manager verwendet. Alle bisherigen fünf
Writer-Proxy-/Advisorprüfungen bleiben bestehen. Eine früh initialisierte, nicht
beobachtbare Managerinstanz führt deshalb schon vor Fixtures zu STOP.

Testhilfe T03PurchaseOverlap beobachtet ausschließlich die beiden eigenen Threads
und echte neue TransactionStatus-Instanzen; verschachtelte REQUIRED-Teilnahmen
werden nicht als neue Transaktion gezählt. Beide Callable-Aufrufe verwenden
unverändert PurchaseService.purchase, ohne vorgeschaltete Test-Coordinator-TX.
Erster Thread erreicht bei aktiver TX den tatsächlichen Manager-Commitaufruf und
hält dort noch den produktiven Mutex. Zweiter Thread startet erst danach; seine
Mitgliedschaft in der Warteschlange genau dieses ReentrantLock muss beobachtet
werden, während der erste Future unvollendet ist und nur eine äußere TX aktiv ist.
Polling verwendet Frist plus hasQueuedThread; Zeitablauf/Sleep gilt nie als PASS.

Danach erste Operation freigeben. Erwartete Reihenfolge exakt FIRST_BEGIN,
FIRST_COMMIT_CLEANED, SECOND_BEGIN, SECOND_COMMIT_CLEANED. Managerstatus completed,
keine aktive TX/Synchronisation nach Manager-Rückkehr, Mutex dabei weiterhin
gehalten, höchstens eine gleichzeitig aktive äußere TX. Beide Ergebnisse in
dieser Reihenfolge UPDATED/ALREADY_PURCHASED und bestätigtes Executorende.
Keine behauptete gleichzeitige MySQL-Writer-TX oder Mehrinstanz-/Rowlockwarteprüfung.

Fristen: erster Commitpunkt höchstens 15 s, Queue-Nachweis höchstens 2 s,
Beobachtergate höchstens 4 s (unter 5-s-Mutexwartefrist), Futures 45 s und
Executorende 50 s wie bisher. Produktive 5-/30-s-Grenzen nicht verändert.
Ein langsamer Host/DB-Commit kann kontrolliert STOP/BUSY statt Erfolg auslösen;
keine automatische Fristverlängerung oder Wiederholung.

Beobachterfehler vor Commit versucht Rollback derselben eigenen Transaktion.
Begin-/Commit-/Rollback-/Cleanupfehler oder offene aktive TX-Zähler gelten als
unbestätigt. Bei unbestätigtem Thread-/Transaktionsende keine nachfolgenden ID-
oder Snapshotabfragen, kein weiterer Schreibtest. Bei bestätigtem Abschluss werden
die beobachtbaren IDs auch nach Ergebnisfehlern wie bisher im finally registriert.
Keine Fremdprozessaktionen, SQL-Bereinigung oder automatische Wiederaufnahme.

### Vollständiger Erhalt der vorhandenen drei Bände

Nur die drei eigenen Fixturebände werden vor dem Kauf innerhalb der bestehenden
Coordinator-TX mit unterschiedlichen Preisen 5,01/6,02/6,97 und Anpassungen
0,03/-0,01/0,00 versehen. Summe bleibt 18,02; keine Änderung der Preisregeln.
Lesestatus true/false/true, Rereads NULL/0/3, Spezialflags NULL/false/true,
unterschiedliche synthetische externe Indizes, optionale URL-Werte und verschiedene
Erstelldaten. Kein URL-Aufruf. Hibernateverwaltetes Aktualisierungsdatum wird nach
Flush/Commit aus dem tatsächlichen unabhängigen Reload aufgenommen, nicht erfunden.

T03BandSnapshot ist eine unveränderliche Map nach persistierter ID und enthält
alle 13 persistenten Bandfelder: ID, Reihen-ID, Nummer, externer Index, Preis,
Anpassung, gelesen, Reread, Spezialflag, beide URLs, Erstell-/Aktualisierungsdatum.
URL.toExternalForm statt URL.equals/hashCode verhindert DNS beim Vergleich.
Nach beiden Käufen unabhängiger Reload: alle drei alten States exakt gleich,
genau eine weitere ID mit Bandnummer 4 und derselben Reihe. Löschen/Neuanlegen,
Umnummerierung oder stille Änderungen werden abgewiesen. Gesamtsumme 18,01 und
Kaufmarkierung bleiben zusätzlich geprüft. Alle gespeicherten Centwerte exakt,
keine double-/String-Rechenumstellung. Preis-/Status-/Metadatenregeln unverändert.

### Tatsächliche Prüfungen und Freigabegrenze

199 datenbankfreie Fälle bestanden (175 bisherige Auswahl + 16 Feldvergleichs-
und 8 Überlappungsfälle), 0 Fehler/Skips. Positive Überlappung mit echtem Coordinator
und vorhandenem FakeLibraryTransactionManager, einschließlich REQUIRED-Nesting;
Negativfälle für falschen Manager, umgangene Koordination, fehlenden Queue-Eintritt,
Workfehler und Commit-/Rollback-/Cleanupfehler. Bandtests erkennen Änderungen an
jedem persistenten Feld, andere Reihenfolge, Löschung/Ersetzung/doppelte IDs und
falsche neue Nummer. Persistenzfeldinventar schlägt bei ungedeckten neuen Feldern an.
t03ClasspathCheck und Opt-in-Kompilierung erfolgreich. Ausschließlich offline/
no-daemon, jeweils identischer Dry-Run vor Ausführung. Keine DB-Verbindung.

Maximal zwölf dauerhafte Zeilen unverändert; zusätzliche Datenvariation ist nur
UPDATE eigener drei Fixturebände, kein neuer Datensatz. Journal-/Crashgrenzen aus
Abschnitt 15 bleiben bestehen; IDs sind keine Commitbeweise. Gesamterhalt alter
DB-Zeilen/Stammdaten/Schema, atomare Laufreservation, Kollisions-/Baselineprüfungen,
Guards und failFast/STOP unverändert. Kein Cleanup/Retry. Manual-JARs und historische
Experimente unverändert. Keine produktive Änderung, neue Dependency oder Gitaktion.

Empfehlung: erste dreiteilige Stufe kann jetzt mit eigener frischer UUID und
konkret bestätigter INSERT-/UPDATE-/Rollback-/Konkurrenzfreigabe vorgeschlagen
werden. Reale Boot-Spy-/Proxyverdrahtung muss vor Fixtures bestehen; echter
MySQL-Commit/Reload/Rollback und parallele Kaufanforderungen bleiben erst durch
den gesonderten Lauf nachweisbar. Diese Vorbereitung ersetzt weder E1–E6 noch
die gesperrte Vollsuite. Keine zusätzliche DB-Ausführung aus diesem Nachtrag ableiten.

## 17. Erste reale dreiteilige Stufe (06.11.2026) – T03_6B_FIRST_COMMIT_ACCEPTED

Nach konkreter Eigentümerfreigabe genau ein t03CommitTest auf b09c6f5 ausgeführt.
UUID **264bc431-7c43-4fc2-b2d4-3efd35d8bb1c**, zuvor dokumentiert, im Dry-Run nicht
reserviert, erst im realen Task atomar angelegt. Keine Wiederholung/Einzelmethoden.
Vorher 199 datenbankfreie Fälle und t03ClasspathCheck erfolgreich, alle Gradle-
Aufrufe offline/no-daemon nach identischem Dry-Run. Initial sauberer versionierter
Stand und nur die acht historischen unversionierten Experimente.

Vor Writes Boot-Writer-Proxys/Advisorreihenfolge, tatsächliche Manager-Spy-
Verdrahtung, genau ein Coordinator, fehlende äußere TX, Rechte und historische
Leerbaseline (Hash ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570)
bestätigt. Bestehende Guards durchgehend aktiv. Drei Methoden bestanden, 0 Fehler,
0 Skips: Commit/Reload, vollständiger Work-Rollback, deterministisch überlappender
Kauf. FIRST_BEGIN → FIRST_COMMIT_CLEANED → SECOND_BEGIN → SECOND_COMMIT_CLEANED
und maximal eine aktive äußere TX durch Assertions nachgewiesen; beide Threads
beendet. Keine gesonderte Mehrinstanz-/DB-Lockwartebehauptung.

Alle 13 Felder der vorhandenen drei Fixturebände unverändert per ID und unabhängigen
Reloads bestätigt, allein Band 4 neu. UPDATED/ALREADY_PURCHASED, gekauft=true,
18,01 Endsumme. Dauerhaft 12 Zeilen aus bestätigten Commitgruppen: zwei Reihen,
zwei Details, sieben Bände, ein Einkauf. Fünf weitere ID-Versuche nachweislich
zurückgerollt, keine bleibenden Zeilen der Rollbackgruppe. Journal 17 ID-Einträge,
drei STAGE_CONFIRMED, kein STOP; privat unter .gradle/t03-integration/264bc431-7c43-4fc2-b2d4-3efd35d8bb1c/.
Keine automatischen ID-/Datenlöschungen und kein AUTO_INCREMENT-Reset.

Nach jeder Stufe Erhalt sämtlicher ursprünglicher Zeilen/78 Stammdaten sowie
Schema-/Spalten-/Index-/FK-Metadaten bestätigt; nur registrierte neue Zeilen.
Gesamthash nach verbleibenden Fixtures nicht identisch erwartet/nicht ausgegeben.
Keine zusätzliche DB-Nachabfrage. Details/Befehle/Berichte in TESTING.md.
Keine andere Datenbankstufe, keine Anwendung/Browser/Scraper/Worker oder Gitaktion.
Erste Stufe damit akzeptiert; volle E1–E6-Matrix und Browserabnahme weiterhin offen.
Die zwölf Zeilen verbleiben ausdrücklich, deshalb nicht erneut gegen die bisherige
Leerbaseline starten. Jede weitere Untersuchung, Abnahme oder Bereinigung braucht
eine neue eng begrenzte Freigabe; kein automatisches Fortsetzen.

## 18. Nachtrag 06.11.2026 – restliche E1–E6-Matrix vorbereitet

**T03_6B_E1_E6_READY_FOR_APPROVAL** bezeichnet ausschließlich datenbankfreie
Vorbereitung. Keine neue DB-Ausführung und keine Erweiterung einer früheren Freigabe.
Die erste reale Stufe (§17) bleibt akzeptiert; historische Leerbaselineprüfungen
und deren Referenzhash bleiben unverändert. Die frühen Standangaben dieses
Dokuments sind historisch, nicht die aktuelle Ausführungsempfehlung.

### 18.1 Aktuelle Baseline erst separat bestätigen

Neuer Task `t03ContinuationReadOnlyTest`, ausschließlich Klasse
`T03ContinuationReadOnlyTest`, MOCK-Kontext/Profil test, bestehender TestContext-
Guard und GuardedTestDataSource, Scraper-Mock. Zwei unabhängige Read-only-TXs:
jeweils Rechteprüfung und vollständiger Snapshot über den vorhandenen Helfer.
Keine Fixtures, Fachwrites, neuen Verbindungswege oder HTTP-Listener.

`T03ContinuationPolicy` bindet das historische private ID-Journal an UUID
264bc431-7c43-4fc2-b2d4-3efd35d8bb1c und SHA-256
8606d5080f4608c5e6a7cab0edf89258e0e4e677f5998b15434a38736b8edc97.
Nur bestätigte Commitgruppen 1/3 ergeben den erwarteten Vorbestand; die fünf
Rollback-IDs aus Gruppe 2 werden ausdrücklich nicht als vorhandene Zeilen erwartet.
Genau zwei Reihen, zwei Details, sieben Bände und ein Einkauf müssen vorhanden
sein; keine Einkaufsdetails oder zusätzlichen/fehlenden Geschäfts-IDs.

Der historische Schema-/78-Stammdatenhash ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570
wird auf einer ausschließlich im RAM um Geschäftszeilen bereinigten Snapshotkopie
geprüft. Das leert keine Tabelle und ersetzt keine historische Referenz. Der
unveränderte vollständige aktuelle Snapshot einschließlich zwölf Zeilen erhält
einen eigenen Hash, der zweimal identisch sein muss und erst bei realem Erfolg
als `T03_CONTINUATION_CURRENT_FINGERPRINT` ausgegeben wird. Dieser Hash ist jetzt
noch **nicht ermittelt/verifiziert** und darf nicht durch den Leerbaselinehash
ersetzt werden. Es gibt kein automatisches Rebaselining.

Nachweisgrenze: Der erste Lauf hat keinen vollständigen Abschluss-Snapshot der
zwölf Zeilen dauerhaft gespeichert. Das ID-Journal beweist deren Zuordnung,
nicht rückwirkend jeden heutigen Feldwert gegenüber dem damaligen Zustand.
Der neue freigegebene lesende Lauf bestätigt den tatsächlichen aktuellen Bestand.
Ab diesem Zustand schützt die Matrix sämtliche Zeilenwerte exakt. Diese Grenze
wird weder durch die erste noch durch die neue Prüfung verschwiegen.

### 18.2 Neue Integrationsfälle (nur kompiliert)

`t03MatrixTest` enthält ausschließlich `T03MatrixAcceptanceTest`: 17 Testfälle
(acht normale Methoden, acht Parameterfälle, ein abschließender Commitfehler).
Echte produktive Service-/Controllerpfade, MockMvc mit aktiven Security-Filtern,
gültigem CSRF und lokalem Host/Origin/Serverport; keine Netzwerklistener.
Repository-/Manager-Spies delegieren an echte Beans; injizierte Instanzidentität,
Writer-Proxys und Advisorreihenfolge werden vor Fixtures geprüft.

| Fälle | E-Gruppe / erwarteter zusätzlicher Nachweis | Maximal neue dauerhafte Zeilen |
|---|---|---:|
| Bestehende Reihe: Kaufmarkierungs-/Flushfehler, danach überlappende update/create-Routen desselben Kaufs und Replay | E1/E2: exakter Rollback; UPDATED/ALREADY_PURCHASED; alle 13 alten Bandfelder unverändert | 8 |
| Neue Reihe: 417-Vorschritt, Markierungs-/Flushfehler, create-Commit, update-Replay; ungültige Einkaufsanlage | E1/E2: keine Teilanlage; CREATED/ALREADY_PURCHASED; 400 ohne Mutation | 7 |
| Zwei Einkäufe derselben vorhandenen Reihe, bestätigte Bereiche 4 und 5 | E2/E3/E5: beide seriell, keine verlorenen Bände/Kaufmarkierungen | 11 |
| Zwei Einkäufe derselben neuen Identität, danach bestätigter Kauf gegen direkte Doppelanlage | E2/E3: erste Anlage; zweiter Kauf verlangt Bandbestätigung; nach Bestätigung UPDATED gegen kontrollierten Rollback der konkurrierenden Doppelanlage | 8 |
| Kauf gegen Einzelbandbearbeitung bei umgekehrter Repositoryliste | E1–E4: persistierte IDs, individuelle Altpreise 4/6/8 Euro; alle übrigen Bandfelder erhalten; Fehler nach Band-save komplett zurückgerollt; fehlende ID verändert nichts | 8 |
| Anpassungen 0,01 / −0,01 / 0,02 / 20 / −20 Euro auf drei Bände | E4: explizite erwartete Centvektoren und Summen nach jedem separaten Commit/Reload | 5 |
| Historisch unbekannte Band-/Reihenpreise, Metadatenedit und Deklarationen 2/5 → tatsächliche 3 | E4/E5: NULL bleibt NULL; Finanzedit abgewiesen; reine Korrektur ändert keinen Band | 5 |
| Abweichende Deklaration 5/2 bei tatsächlichen drei Bänden | E3/E5: Reduktion/Teilkorrektur abgewiesen, unveränderte 5 erzeugt nichts; explizite Erweiterung auf 4 erhält alle alten Status-/Reread-/Spezialwerte, neuer Band ungelesen | 6 |
| Acht negative Varianten: mehrdeutiger/NULL-Index, veralteter Bereich + NULL-Kaufstatus, fehlende Reihen-/Einkaufsdetails, doppelte Bandnummer, unbekannter Preis, inkompatibles Format | E3/E4/E6: kontrollierte Outcomes unter eigener äußerer Coordinator-TX; sämtliche eigenen Fixtures gezielt zurückrollen, exakten Vorher-/Nachher-Zustand prüfen | 0 |
| Letzter Fall: Fehler in beforeCommit | E1/E6: tatsächliches afterCompletion=ROLLED_BACK, Daten unverändert, danach Coordinatorquarantäne und kein weiterer Schreibzugriff | 2 |

Die beiden Einkaufsrouten bleiben reguläre HTTP-Handler, nicht direkte
Controlleraufrufe. Fehlersimulation bei save/flush delegiert zuerst an die echte
Repositoryfunktion; beforeCommit ist eine echte Spring-Synchronisation. Kein
behaupteter realer JDBC-Verbindungsabbruch. Die Überlappungsbeobachtung hält den
ersten Commit begrenzt an, weist den zweiten Thread in der echten Mutexqueue nach
und verlangt Managercleanup vor zweitem TX-Beginn sowie beide Threadenden.

Beim Editionskonflikt wird ein zweiter **vorhandener** Formatwert benötigt;
fehlt er, STOP, niemals Stammdaten anlegen. Die Matrix erzeugt ausschließlich
synthetische Titel `T03_MATRIX_<neue UUID>_<Sequenz>` und kollisionsgeprüfte neue
positive Indizes. Vorbestehende Zeilen werden niemals als eigene Fixtures registriert.

### 18.3 Commitbedarf, Umfang und Erhaltung

Echte Commit-/Reload-/Konkurrenznachweise benötigen eigene Transaktionsgrenzen,
keine äußere JUnit-Rollbacktransaktion. Bei vollständigem Erfolg verbleiben exakt
**60 zusätzliche Zeilen**: acht Reihen, acht Details, 28 Bände, acht Einkäufe und
acht Einkaufsdetails. Einschließlich der alten zwölf: 72 Geschäftszeilen.
Diese Obergrenzen werden nach jedem Fall geprüft, die exakten Endzahlen zuletzt.
Früher STOP kann eine Teilmenge hinterlassen; keine automatische Wiederholung.

Die acht reinen Ablehnungsvarianten verwenden vollständig zurückgerollte eigene
Graphen (geplant 51 ID-Versuche). Die sonstigen Markierungs-/Flush-/beforeCommit-
Fehler rollen weitere geplante 17 neue Zeilen zurück; keine zusätzliche dauerhafte
Fixture nur für einen negativen Fall. Insgesamt geplant 128 ID-Versuche,
darunter 68 zurückgerollte. Einzelbandfehler ändert nur bereits eigene Zeilen und
erzeugt keine neuen IDs. AUTO_INCREMENT kann steigen und wird nicht zurückgesetzt.

Nach jedem Fall: vollständiger Snapshot gegen den Fallbeginn (alle früheren
Fixturegruppen unverändert) und gegen den freigegebenen Laufbeginn; nur neue
journalisierte IDs erlaubt. Zehn Tabellen, sämtliche alten Zeilen/Beziehungen,
78 Stammdaten, Spalten/Indizes/FKs bleiben exakt. Neue IDs werden dauerhaft vor
dem Commit im eigenen CREATE_NEW-Journal registriert; alte Journale bleiben
unverändert. Die neue UUID darf nicht existieren/wiederverwendet werden.
Keine DELETE-Operation, kein Cleanup, keine SQL-/Schemainitialisierung.
Bei unbekanntem Manager-/Threadabschluss keine weitere Snapshotabfrage; STOP.
Der absichtliche letzte beforeCommit-Fehler erlaubt nach nachgewiesenem Rollback
nur lesende Endkontrollen, keine Aufhebung der Coordinatorquarantäne.

### 18.4 Getrennte spätere Freigaben und Befehle

Diese Vorlage erteilt **keine** Ausführungsfreigabe. Vor jeder Stufe §2 erneut
prüfen: Branch, gesamter Arbeitsstand, festes Testprofil, Passwort nur auf
Vorhandensein, keinerlei Overrides, minimale Serverrechte, keine Fremdschreiber,
unveränderte Guards, historische Journalbindung und Manual-Artefakte.
Zunächst die dokumentierten datenbankfreien Guard-/Hilfstests und Classpathprüfung
nach jeweils identischem Dry-Run. Nie die alte `t03CommitTest`-Leerbaseline erneut
starten. Kein ungefiltertes `test`, `clean test` oder Lifecyclebuild.

**Freigabe A:** genau eine lesende Fortsetzungsprüfung einschließlich guarded
Spring-/JPA-Initialisierung ausschließlich mangalib_test/mangalib_test_user:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --no-daemon t03ContinuationReadOnlyTest -Pt03ContinuationApproval=VERIFY_EXISTING_T03_FIXTURES --dry-run
# Erst nach Kontrolle und ausdrücklicher Freigabe A:
.\gradlew.bat --offline --no-daemon t03ContinuationReadOnlyTest -Pt03ContinuationApproval=VERIFY_EXISTING_T03_FIXTURES
```

Danach anhalten und tatsächlichen aktuellen Vollhash/Bestand berichten.
Keine automatische Verkettung zur Matrix. Abweichungen nicht reparieren.

**Freigabe B:** erst nach akzeptierter A, an genau deren bestätigten Vollhash und
eine zuvor im Chat festgelegte neue UUID gebunden. Ausschließlich die 17 Fälle,
INSERT/UPDATE eigener synthetischer Graphen, echte Commits/Rollbacks und begrenzte
Zweithreadkonkurrenz; maximal 60 zusätzliche dauerhafte Zeilen, kein Cleanup.
Platzhalter vor Freigabe konkret ersetzen, keine Zugangsdaten in Argumenten:

```powershell
.\gradlew.bat --offline --no-daemon t03MatrixTest -Pt03MatrixApproval=E1_E6_NEW_SYNTHETIC_ROWS_ONLY -Pt03MatrixRunId=<NEUE_UUID> -Pt03MatrixBaselineFingerprint=<BESTAETIGTER_A_HASH> --dry-run
# Erst nach Kontrolle und ausdrücklicher Freigabe B, identische Argumente:
.\gradlew.bat --offline --no-daemon t03MatrixTest -Pt03MatrixApproval=E1_E6_NEW_SYNTHETIC_ROWS_ONLY -Pt03MatrixRunId=<NEUE_UUID> -Pt03MatrixBaselineFingerprint=<BESTAETIGTER_A_HASH>
```

Beide Tasks: eine feste Klassen-Allowlist, maxParallelForks=1, failFast,
keine Up-to-date-Abkürzung, keine Kopplung an test/check/build oder untereinander.
Bei Abbruch keine Einzelmethoden-/Laufwiederholung; Journal und Teilbestand erst
separat lesend klären, nichts bereinigen oder automatisch neu baselinen.

### 18.5 Bewusst außerhalb / Nachweisgrenzen

- Keine Mehrinstanz-/Fremdschreiberfreigabe; JVM-Mutex ist kein verteilter Lock.
  Die beobachtete reale Mutexwartezeit ist kein separater DB-Lock-/Deadlocktest.
- Keine improvisierten Netz-/Serverausfälle oder ungewissen echten JDBC-Commits.
  Ein beforeCommit-Rollback beweist keinen verlorenen Commit-ACK.
- Keine pauschale Übertragung/Abnahme aller 75 historischen Methoden aus dem
  Integrationsinventar. Alte C-Tests mit äußerer Testtransaktion bleiben separat
  anzupassen; keine Freigabe der Vollsuite oder historischer Delete-Fälle.
- Kein Browser-, Scraper-, Worker-, Manual-DB- oder Produktivzugriff. T03-6C,
  zusätzliche historische HTTP-Verträge und gegebenenfalls weitere R/F-Abnahmen
  brauchen eigene Freigaben. T03 insgesamt ist noch nicht abgeschlossen.

Datenbankfrei bestätigt: 217 Javafälle (44 Guards, bisherige Hilfs-/Servicefälle,
16 neue Fortsetzungspolicyfälle und zwei neue gemischte Überlappungsfälle), alle
0 Fehler/Skips. Integrationsquellen kompiliert; t03ClasspathCheck prüft alle fünf
Opt-in-Tasks inklusive Negativkontrolle ohne Owner-Classpath, ohne Initialisierung
der neun geprüften Klassen und ohne irgendeinen DB-Test zu starten. Details TESTING.md.

## 19. Nachtrag 06.11.2026 – aktuelle Fortsetzungsbaseline lesend bestätigt

**T03_6B_CONTINUATION_READ_ONLY_VERIFIED**. Die ausdrücklich freigegebene Stufe A
aus §18.4 wurde genau einmal ausgeführt. Stufe B bleibt gesperrt; keine automatische
Fortsetzung, keine Commit-/Rollback-/Konkurrenztests oder Bereinigung.

Branch codex/t03-data-integrity, HEAD unverändert b09c6f5.
Vorher Passwortvariable vorhanden/nicht leer (kein Wert ausgegeben), keine
relevanten Umgebungs-/Gradle-Overrides, keine laufenden Java-Prozesse und keine
etablierten MySQL-Clientverbindungen beobachtet. Das ist eine Momentaufnahme,
kein globaler Ausschluss anderer Schreiber. Guards/Testprofil unverändert.

Nach identischen erfolgreichen Offline-Dry-Runs: 217 ausgewählte datenbankfreie
Tests bestanden (44 Guardfälle), 0 Fehler/Skips; t03ClasspathCheck erfolgreich
für alle fünf isolierten Tasks. Danach ausschließlich:

```powershell
.\gradlew.bat --offline --no-daemon t03ContinuationReadOnlyTest -Pt03ContinuationApproval=VERIFY_EXISTING_T03_FIXTURES --dry-run
.\gradlew.bat --offline --no-daemon t03ContinuationReadOnlyTest -Pt03ContinuationApproval=VERIFY_EXISTING_T03_FIXTURES
```

Java 21, offline/no-daemon. Geschützter Spring-/JPA-MOCK-Kontext erfolgreich;
**1 Test bestanden, 0 Fehler, 0 übersprungen**. Profil ausschließlich test;
fester Guard-Zugang localhost:3306/mangalib_test, Benutzer mangalib_test_user
auch durch CURRENT_USER()-Benutzernamenprüfung bestätigt. SHOW GRANTS bestand:
global nur USAGE, ausschließlich SELECT/INSERT/UPDATE/DELETE auf dem literal
maskierten mangalib_test-Schema; keine zusätzlichen Rechte/Rollen/GRANT OPTION.

Bestätigt: genau zehn InnoDB-Tabellen, alle Schema-/Spalten-/Index-/FK-Metadaten
und 78 Stammdatensätze historisch unverändert. Die rein im RAM um Geschäftszeilen
bereinigte Snapshotkopie stimmt mit der historischen Referenz
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570 überein.
Keine Tabellen oder echten Daten wurden dafür geleert/verändert.

Genau zwölf Geschäftszeilen: 2 mangareihe, 2 mangadetails, 7 baende,
1 einkaufsliste, 0 einkaufslistedetails. Ihre ID-Mengen entsprechen exakt den
bestätigten Commitgruppen 1/3 des historischen Laufs
264bc431-7c43-4fc2-b2d4-3efd35d8bb1c. Die fünf Rollback-IDs aus Gruppe 2 fehlen;
keine zusätzlichen/unbekannten Geschäftszeilen. UUID-/Phasen-/Hashprüfung des
unveränderten Journals erfolgreich, SHA-256 weiterhin
8606d5080f4608c5e6a7cab0edf89258e0e4e677f5998b15434a38736b8edc97.

Zwei unabhängige Read-only-Transaktionen lieferten exakt gleiche vollständige
Snapshots. Der jetzt tatsächlich verifizierte aktuelle Vollfingerabdruck lautet:

`T03_CONTINUATION_CURRENT_FINGERPRINT=b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a`

Dieser Hash ersetzt nicht die historische Leerbaseline. Er bestätigt den aktuellen
Zustand einschließlich der zwölf Zeilen; deren sämtliche damaligen Feldwerte aus
dem ersten Commitlauf werden dadurch nicht rückwirkend bewiesen (§18.1).
Keine unabhängige serverweite SQL-Auditierung: Seiteneffektfreiheit stützt sich
auf geprüften SELECT-/SHOW-only-Testcode, readOnly-Transaktionen, deaktivierte
Initialisierung/Migration/DDL-Erzeugung und die identischen Vollsnapshots.

JUnit-Evidenz: build/test-results/t03ContinuationReadOnlyTest/TEST-de.mangalib.T03ContinuationReadOnlyTest.xml,
SHA-256 16ae7bf13e9b467a7ffd01ba5e93e3801dfe231f5672b9298f583e7a7c6e441b.
Nur feste Status-/Hashzeilen aus dem Bericht ausgewertet, keine Rohdaten/Secrets
in die Dokumentation übernommen. Historische Experimente, Manual-JARs und Journal
unverändert. Nur diese Dokumentation und TESTING.md ergänzt; Staging leer.
Keine Schreiboperation, Wiederholung, andere DB, Anwendung/Listener, Browser,
Scraper, Worker oder Git-Schreibaktion. Weiterhin eigene ausdrückliche Freigabe
für die 17 Matrixfälle/maximal 60 zusätzlichen dauerhaften Zeilen erforderlich.

## 20. Nachtrag 06.11.2026 – einmaliger Matrixlauf fehlgeschlagen

**T03_6B_E1_E6_FAILED**. Eigentümerfreigabe für genau einen Lauf verwendet;
keine Wiederholung oder Ersatz-UUID. Branch codex/t03-data-integrity, HEAD b09c6f5.
UUID **7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e** vor/nach Dry-Run nicht vorhanden,
erst der tatsächliche Task reservierte sie. Der entstandene Laufpfad bleibt
erhalten und darf nicht wiederverwendet werden.

217 datenbankfreie Vorprüfungen (inklusive 44 Guards), 0 Fehler/Skips, und
t03ClasspathCheck für alle fünf getrennten Tasks erfolgreich. Offline/no-daemon,
Java 21, jeweils identischer Dry-Run. Keine Overrides, Passwortvariable vorhanden
ohne Wertausgabe; keine Java-/etablierten MySQL-Clientprozesse vor Start beobachtet.
Fachcode, Testguards, Profil und historische Artefakte unverändert.

Genau einmal ausgeführter tatsächlicher Datenbanktask, nach identischem Dry-Run:

```powershell
.\gradlew.bat --offline --no-daemon t03MatrixTest -Pt03MatrixApproval=E1_E6_NEW_SYNTHETIC_ROWS_ONLY -Pt03MatrixRunId=7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e -Pt03MatrixBaselineFingerprint=b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a
```

Der geschützte MOCK-Kontext startete im Profil test. Vor dem ersten Fixtureaufbau
bestanden feste DB-/Benutzeridentität, Minimalrechte, historische Journalbindung,
historischer Schema-/78-Stammdatenvergleich und der exakte freigegebene aktuelle
Vollhash b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a.
Auch Writer-Proxys, Advisorreihenfolge und tatsächliche Manager-/Repository-
Verdrahtung waren vor Beginn erfolgreich geprüft.

Ergebnis des Tasks: **6 erfasste Tests, 0 bestanden, 5 fehlgeschlagen,
1 übersprungen**; BUILD FAILED in 27s. Von den 17 geplanten Fällen sind elf
nicht mehr im Bericht gestartet/erfasst. Die erste Methode scheiterte im
Fixtureaufbau; vier weitere wurden durch die BeforeEach-STOP-Barriere ohne
Testkörper blockiert. Ein weiterer Fall wurde von der Ausführung übersprungen.
Keine eigenständigen Filter, Wiederholungen oder nachträglichen Deaktivierungen.

Bestätigte Ursache: MockitoException `Cannot call abstract real method on java
object!` im Band-save-Hook von T03MatrixAcceptanceTest.java:124. Ein Repository-
Interface-/Proxymethodenaufruf ist hier kein aufrufbarer konkreter Mockito-
Methodenkörper. Stack: BandService.createAndSaveBaende → Test-Hook, noch vor
erfolgreichem Abschluss der ersten Reihenfixture. Dies ist ein Infrastrukturfehler,
kein Nachweis eines Fehlers der T03-Fachregeln. Andere gleichartige callRealMethod-
Hooks müssen vor neuer Freigabe ebenfalls geprüft werden; sie sind nicht als
erfolgreich funktionierende Delegation zu betrachten. Kein Quellcode repariert.

Die im ersten Fall bereits vorbereitete AfterEach-Erhaltungskontrolle lief ohne
zusätzlichen Fehler durch: vollständige Vergleiche mit Fall- und Laufbaseline,
keine unbestätigte Coordinatortransaktion, keine unregistrierten neuen Zeilen.
Danach keine zusätzliche Datenbankdiagnose. Damit im Umfang dieser Kontrolle:
**0 neue dauerhafte Zeilen je Geschäftstabelle**, alle zwölf alten Geschäftszeilen,
78 Stammdatensätze und Schema-Metadaten unverändert. Keine erfolgreiche neue
Commitgruppe oder Konkurrenz-/Kaufabnahme. Die 60 geplanten neuen Zeilen und die
geplanten 128/68 ID-/Rollbackzahlen wurden ausdrücklich nicht erreicht.

Neues Journal: 8 Zeilen = PREPARED + 7 STOP, **0 ID-Einträge,
0 STAGE_CONFIRMED**. STOP-Einträge sind Lifecyclemeldungen, keine Commitanzahl.
SHA-256 7319c4ee391210058cc91109f38477b22dec2d9f885c05219b802347b90226c1.
Null registrierte IDs bedeutet nicht null SQL-INSERT-Versuche: der erste
Graphaufbau wurde vor registerGraph abgebrochen. Exakte zugeteilte/rollbackte IDs
sind im Journal nicht belegt; die nachfolgende interne Vollsnapshotkontrolle
belegte jedoch, dass keine neuen Zeilen verblieben. AUTO_INCREMENT nicht geprüft
oder zurückgesetzt. Kein neuer Gesamthash ausgegeben/zusätzlich abgefragt.

Privater Bericht build/test-results/t03MatrixTest/TEST-de.mangalib.T03MatrixAcceptanceTest.xml,
SHA-256 57ffd47fce7d4fd953d9ccca54643905d855d5e442ffe6b05de4181a50568423.
Erster Fall enthält genau einen Fehler, keinen zusätzlichen/supprimierten
AfterEach-Fehler. Nach dem Fehlschlag nur lokale Berichte/Quellen/Journale und
Dateiintegrität geprüft, keine weitere DB-Verbindung oder Datenreparatur.

Nur TESTING.md und dieser Nachtrag geändert. Staging leer, vorhandene lokale
Arbeiten erhalten; historische acht Experimente, drei Manual-JARs und altes
Journal hashidentisch. Kein Commit/Push oder sonstiger Git-Schreibvorgang.
Keine andere DB, Anwendung/Listener, Browser, Scraper, Worker, Migration oder
Bereinigung. E1–E6 insgesamt nicht abgenommen. Nächster notwendiger Auftrag:
eng begrenzte datenbankfreie Korrektur und Proxy-Delegationsregression der
Test-Hooks, danach neue gesonderte Freigabe mit neuer UUID für reale Ausführung.
Die gegenwärtige Freigabe berechtigt zu keinem zweiten Lauf.

## 21. Nachtrag 06.11.2026 – Repositorydelegation datenbankfrei korrigiert

Status **T03_6B_MATRIX_RETRY_READY_FOR_APPROVAL** ausschließlich für die erneute
Vorschlagbarkeit, keine Datenbankfreigabe oder nachträgliche Abnahme von §20.
Die verbrauchte UUID 7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e bleibt gesperrt;
Journal und fehlgeschlagener Bericht unverändert. Keine Ersatz-UUID reserviert.
Letzte bestätigte Vollreferenz bleibt unverändert
b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a.

Ursache präzisiert: Spring MockitoSpyBean behandelt JDK-Proxys mit einem
delegierenden Mockito-DefaultAnswer. Ein eigenes doAnswer mit callRealMethod
ersetzt diese Delegation und versucht den abstrakten Interfacekörper aufzurufen.
Dieser Mechanismus wurde an lokalem Spring-Test-Bytecode nachvollzogen und mit
echter RepositoryFactorySupport-Proxykette plus delegatesTo-Mock reproduziert.
Betroffen waren alle vier Hooks: Einkauf-save, Einkauf-flush, Band-save und
findByMangaReiheId. Auch die bislang unerreichten Hooks waren nicht sicher.

Korrektur ausschließlich testlokal: unveränderte echte Repositorybeans per
Autowired statt Repository-Mockito-Spies. Kleine T03RepositoryHook-Hilfe setzt
einen eigenen Advisor vor die vorhandene Spring-Data-Kette. MethodInvocation.proceed
führt diese Kette genau einmal fort; kein erneuter Aufruf der Repositorybean,
kein Aufruf eines abstrakten Java-Methodenkörpers und kein Mock-Erfolgswert.
Vorhandene Transactions-/Repository-Advices bleiben erhalten. Identitätsprüfungen
gegen die tatsächlich injizierten Servicefelder bestehen fort/werden erweitert.
Der konkrete Manager-Spy und sein vorhandener Abschlussbeobachter bleiben erhalten.

Pro Fall werden Hooks neu installiert und nach bestätigtem Threadende entfernt;
nur eigene Advisor, idempotentes close, bei Zuordnungsfehler STOP. Fault-/Reverse-
Flags werden weiter zurückgesetzt. Kein Hookabbau bei noch laufender Konkurrenz.
Save-/Flush-/Band-save-Fehler kommen weiterhin nach der delegierten Operation,
beforeCommit-Synchronisation und Coordinatorquarantäne bleiben unverändert.
Alle 17 fachlichen Fallkörper und Assertions, Budget 60, Vollsnapshots, Mutex-
Nachweise, alte zwölf Zeilen, Rechte-/Baseline-/UUID-Guards bleiben erhalten.

Frühere ID-Erfassung: erfolgreiche save-/saveAndFlush-Rückgaben von Reihen,
Details und Bänden werden unmittelbar journalisiert, ohne zusätzliche Query,
Flush oder Verbindung. Kinder müssen zum bereits registrierten eigenen Root
gehören; neue Roots zum bekannten Lauftitel. Bestehende IDs sind weiterhin
gesperrt, wiederholte Registrierung bleibt idempotent. Die bisherige vollständige
Graphregistrierung bleibt zusätzlich erhalten. Grenzen: IDs vor fehlgeschlagener
Repositoryrückkehr, JDBC-/Flushfehler, Prozessabbruch oder Journal-I/O-Ausfall
werden nicht lückenlos erfasst. Keine Crashfestigkeit, Wiederaufnahme oder neue
Cleanupfunktion. Historische fehlende IDs werden nicht nachträglich erfunden.

Nach identischen Offline/no-daemon-Dry-Runs: **238 datenbankfreie Javafälle
bestanden, 0 Fehler/Skips** (217 bisherige + 21 neue). Davon 18 Proxy-/Hookfälle
und drei Hook-/Coordinatorfälle mit dem vorhandenen Fake-Transaktionsmanager.
Die historische Fehlermeldung wird in einem grünen Reproduktionstest erwartet,
nicht unterdrückt/deaktiviert. Echte Spring-Data-Factory, JDK-/CGLIB-Proxys,
konkreter Mockito-Spy, einmalige Delegation, originale Ausnahmen, Fehler vor/nach
Delegation, wiederholte Scopes, Advisorzuordnung, Parallelität, frühes Journal und
unveränderte Rollback-/Quarantänesteuerung geprüft. Compile-/Classpathcheck aller
fünf Opt-in-Tasks erfolgreich. Kein Boot-/JPA-Kontext oder DB-Test ausgeführt.

Nachweisgrenze: konkrete RAM-Implementierung hinter echten Dispatch-Proxys,
keine Hibernate-/SimpleJpaRepository-/MySQL-Persistenz. Deren tatsächlicher
Laufzeitnachweis bleibt offen. Neue reale Matrix erst nach separater ausdrücklicher
Freigabe, neuer UUID und erneuten Vorprüfungen; keine automatische Wiederholung.
Details, Dateiliste und Befehle im neuen TESTING.md-Nachtrag. Fachcode, Guards,
Gradle-Taskdefinitionen, historische Experimente/Journale/Manual-JARs unverändert.
Keine DB-Verbindung/SQL, Anwendung/Listener, Browser, Worker/Scraper, Installation
oder Git-Schreibaktion. Nur Testkorrektur und neue Dokumentationsnachträge.

## 22. Nachtrag 06.11.2026 – korrigierter Matrixlauf teilweise bestanden, STOP

Status **T03_6B_E1_E6_FAILED**. Genau ein neuer freigegebener Lauf mit UUID
**6db42e71-8fc3-4a96-b2d5-0e719c43f8ab**, keine Wiederholung, Ersatz-UUID,
Zusatzdiagnose an der Datenbank oder Reparatur. Die UUID ist verbraucht.
Branch codex/t03-data-integrity und HEAD b09c6f5 unverändert.

Vorbereitung: 238 datenbankfreie Javafälle bestanden (0 Fehler/Skips, 44 Guards),
t03ClasspathCheck aller fünf isolierten Tasks erfolgreich. Passwortvariable nur
auf Vorhandensein geprüft, keine Overrides/Java-Prozesse/etablierten MySQL-
Clientverbindungen beobachtet. Fachcode, Guards und korrigierte Hooks unverändert.
Alle Gradle-Befehle Java 21, offline/no-daemon und nach identischem Dry-Run.
Neue UUID vor/nach Dry-Run nicht vorhanden; erst realer Task reservierte sie.

Einmalig ausgeführt:

```powershell
.\gradlew.bat --offline --no-daemon t03MatrixTest -Pt03MatrixApproval=E1_E6_NEW_SYNTHETIC_ROWS_ONLY -Pt03MatrixRunId=6db42e71-8fc3-4a96-b2d5-0e719c43f8ab -Pt03MatrixBaselineFingerprint=b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a
```

Vor Writes bestanden feste Identität mangalib_test/mangalib_test_user, Profil test,
Minimalrechte, Proxy-/Manager-/Advisorprüfungen, historische Journalbindung und
Schema-/78-Stammdatenreferenz sowie exakter freigegebener Vollhash
b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a.
Kein paralleler lesender Vorprüfungstask und keine andere DB-Stufe.

**17 JUnit-Fälle erfasst: 9 bestanden, 8 fehlgeschlagen, 0 übersprungen**,
BUILD FAILED in 27s. Erste acht normale Methoden und Parameterfall ambiguous
bestanden. Parameterfall null scheiterte im Fixtureaufbau. Sechs weitere
Parameterfälle und der letzte beforeCommit-Test scheiterten ausschließlich an
BeforeEach-STOP; diese sieben Testkörper wurden nicht ausgeführt, sind aber im
JUnit-Bericht Fehler und keine Skips. Einzelheiten/Fallliste in TESTING.md.

Die korrigierte Repositorydelegation lief tatsächlich durch Spring Data/JPA:
kein erneuter abstrakter Mockito-Methodenfehler. Beide Kaufwege, Save-/Flush-
Fehlerrollbacks, Replay, fünf beobachtete Konkurrenzpaare, Einzelbandfehler,
unsortierte Identifikation, individuelle Preise, Centvektoren, NULL-Preise und
Anzahl-/M06-Fälle bestanden in ihrem jeweiligen Umfang. Fünf Konkurrenzpaare:
vier mit zwei äußeren Commits, eines mit Commit und kontrolliertem Work-Rollback
der Doppelanlage; Queue-, Cleanup- und Threadendassertions bestanden. Kein
Mehrinstanz-/globaler DB-Locknachweis. beforeCommit-Quarantäne nicht neu abgenommen.

Neuer bestätigter Infrastrukturfehler: cart-Fixture in
T03MatrixAcceptanceTest.java:378, `r==null?index():r.getMangaIndex()`.
index() liefert int; Java entpackt deshalb auch den nullable Integer des anderen
Zweigs. Die absichtlich auf NULL gesetzte Reihenkennung verursacht intValue()-NPE
vor Einkaufsanlage und vor der fachlichen NULL-Zuordnungsprüfung. Erwartet war
erst später die eigene RollbackFixture-Ausnahme. Kein nachgewiesener Fachfehler
des PurchaseService. Keine Quellcode-/Assertionänderung während/nach diesem Lauf.

Nach dem NULL-Fehler bestand die interne AfterEach-Erhaltung ohne weiteren Fehler:
Fall-/Laufbaseline, keine unbestätigte Coordinatortransaktion, keine unbekannten
Zeilen, Budgetgrenzen und Scraper-Sperre. Alle ursprünglichen zwölf Geschäftszeilen,
78 Stammdatensätze und Schema-/Index-/FK-Metadaten unverändert. Aus bestandenen
Commit-/Reload-/Rollbackassertions und Journalzuordnung abgeleitet (keine zusätzliche
DB-Abfrage nach Taskende): **58 neue dauerhafte Zeilen**, nämlich 8 mangareihe,
8 mangadetails, 28 baende, 7 einkaufsliste und 7 einkaufslistedetails. Mit bisherigem
Bestand 70 Geschäftszeilen. Die zwei Einkaufszeilen des letzten Falls entstanden
nicht. Neuer Gesamthash nicht ermittelt; Ausgangshash nicht als unverändert
aktueller Vollhash behaupten oder weiterverwenden.

Neues Journal: **87 registrierte ID-Versuche, 9 STAGE_CONFIRMED, 9 STOP**, insgesamt
106 Zeilen einschließlich PREPARED. 58 dauerhaft bestätigte Zeilen und
29 durch die geprüften Fehler-/Rollbackpfade zurückgerollte IDs. Aufteilung
ID-Versuche: Reihen 13, Details 13, Bände 45, Einkäufe 8, Einkaufsdetails 8.
Die neun bestätigten Fallgruppen schließen den vollständig zurückgerollten
ambiguous-Fall ein und sind keine Anzahl tatsächlicher TX-Commits. Frühere
ID-Erfassung erfasste auch die fünf später zurückgerollten IDs der NULL-Fixture.
STOP-Meldungen sind Lifecyclemeldungen; AUTO_INCREMENT nicht geprüft/zurückgesetzt.
Journal-SHA-256 50cd7c13f7acc9911fe4dcf474711ed5406c7c68bc63bf9c5d23f21d1d843efe.

Bericht-SHA-256 52b4255157894d8bc56d376e73e81c1f6d19671c90899d70291eecd6e663bef7;
privat dauerhaft kopiert unter .gradle/t03-integration/6db42e71-8fc3-4a96-b2d5-0e719c43f8ab/matrix-report/.
Der vorherige Fehlerbericht wurde vor Gradle-Neuverwendung des Standardpfads
byteidentisch unter .gradle/t03-integration/7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e/matrix-report-original/
gesichert, SHA-256 unverändert 57ffd47fce7d4fd953d9ccca54643905d855d5e442ffe6b05de4181a50568423.
Alte Journaldateien bleiben unverändert. Keine historische Umdeutung.

Nächste Entscheidung/Arbeit nur separat: nullable Fixtureauswahl datenbankfrei
korrigieren und regressionsprüfen; Fortsetzungsplan für die acht nicht fachlich
abgeschlossenen Fälle vorbereiten. Die 58 bestätigten neuen Zeilen schützen,
nicht löschen und nicht erneut gegen die alte zwölfzeilige Policy starten.
Eine neue aktuelle Baseline benötigt eine separat genehmigte tatsächliche
lesende Prüfung; kein automatisches Rebaselining oder Resume.
Nur neue Nachträge in TESTING.md und diesem Dokument; Staging leer, bestehende
lokale Änderungen erhalten. Historische Experimente/Manual-JARs unverändert.
Keine andere DB, weitere Tests, Migration/DELETE/Cleanup, Anwendung/Listener,
Browser/Scraper/Worker oder Git-Schreibaktion nach/außerhalb des erlaubten Laufs.
T03 und E1–E6 insgesamt nicht abgenommen.

## 23. Nachtrag 06.11.2026 – NULL-Fixture und gezielte Fortsetzung vorbereitet

Status **T03_6B_CONTINUATION_PREPARED**, ausschließlich datenbankfrei. Keine neue
UUID reserviert, keine reale Lese-/Schreibfreigabe erzeugt oder verwendet.
Historische Ergebnisse aus §17–22 bleiben unverändert, insbesondere der letzte
Teilversuch FAILED mit neun bestandenen Fällen. Die neun werden nicht wiederholt.

### 23.1 Fixturekorrektur und historische ID-Zuordnung

T03CartFixture.identity verwendet explizite Zweige statt int/Integer-Ternärlogik.
Bei vorhandener Reihe bleiben Mangaindex NULL, 0 und positive Werte unverändert;
neue Titel/Indizes nur bei ausdrücklich fehlender optionaler Reihenreferenz.
Eine angeforderte, aber fehlende/widersprüchliche Reihenreferenz führt kontrolliert
zum Fixturefehler statt stiller Neuanlage. Keine produktive NULL-Regel geändert.
Weitere gleichartige Fehler nicht nachgewiesen: null-Literal-Ternäre für Reread,
Flags/URLs und optionale Requestfelder sind typisiert/boxing-sicher; PK-/Ordinal-
Zugriffe betreffen erforderliche Identitäts-/Schemadaten, keine optionalen Werte.

T03MatrixHistory bindet den Lauf 6db42e71-8fc3-4a96-b2d5-0e719c43f8ab an Journalhash
50cd7c13f7acc9911fe4dcf474711ed5406c7c68bc63bf9c5d23f21d1d843efe und archivierten
Berichthash 52b4255157894d8bc56d376e73e81c1f6d19671c90899d70291eecd6e663bef7.
Die neun erfolgreichen Methodengruppen wurden im tatsächlichen Bericht erneut
gelesen; private konkrete ID-Mengen sind reproduzierbar als committedGroups,
committed und rolledBack verfügbar. Keine Rohdaten in Git-Dokumente kopieren.

| Historische Phase / Fall | Journal-IDs | Dauerhaft | Auswahl der dauerhaften IDs |
|---|---:|---:|---|
| 1 Bestehender Kauf/Fehler/Replay | 10 | 8 | Reihe/Details/Einkauf/Einkaufsdetails; erste drei und letzter der sechs Band-IDs |
| 2 Neuer Kauf/Fehler/Replay | 17 | 7 | Letzte Reihen-/Details-ID, letzte drei der neun Band-IDs; Einkauf/Einkaufsdetails |
| 3 Zwei Einkäufe vorhandene Reihe | 11 | 11 | Alle IDs dieser Phase |
| 4 Neue Identität/Doppelanlage | 8 | 8 | Alle IDs dieser Phase |
| 5 Kauf gegen Bandedit | 8 | 8 | Alle IDs dieser Phase |
| 6 Centverteilung | 5 | 5 | Alle IDs dieser Phase |
| 7 NULL-Preise/Anzahlkorrektur | 5 | 5 | Alle IDs dieser Phase |
| 8 M06-Erweiterung | 6 | 6 | Alle IDs dieser Phase |
| 9 ambiguous, erfolgreich zurückgerollt | 12 | 0 | Keine; STAGE_CONFIRMED bedeutet hier Testabschluss, keinen Commit |
| 10 NULL-Fixture, NPE/STOP | 5 | 0 | Keine; fehlgeschlagener Testkörper, Rollbackerhaltung bestand |

Sechs folgende Parameter und beforeCommit waren durch STOP blockiert und haben
keine eigenen Fixturegruppen. Phasen-/Tabellenbudgets, doppelte IDs, neun
Bestätigungen/neun STOPs und Hashbindungen werden strikt geprüft. Keine heuristische
Zuordnung beliebiger Journale. Summe 58 dauerhaft / 29 zurückgerollt bleibt fest.

### 23.2 Neue Leseprüfung – eigene Freigabe A erforderlich

Task `t03SeventyReadOnlyTest`, nur T03SeventyReadOnlyTest. MOCK-Kontext, Profil
test und unveränderte Guards; keine Fixtures/Fachwrites. Zwei unabhängige
readOnly-TXs mit bestehenden SHOW-GRANTS-/Snapshothelfern.

Die Policy prüft feste Identität/Rechte, historische zehn InnoDB-Tabellen,
78 Stammdaten und Schema über die unveränderte historische Policy. Die originalen
zwölf Zeilen werden im RAM aus dem aktuellen Snapshot anhand der gepinnten
264bc431-7c43-4fc2-b2d4-3efd35d8bb1c-ID-Gruppe projiziert: vollständige Projektion
inklusive Schema/Stammdaten MUSS dem bestätigten Hash
b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a entsprechen.
Damit sind ihre Feldwerte geschützt, nicht nur ihre IDs. RAM-Projektion verändert
keine Daten und behält Zeilenreihenfolge/Metadaten unverändert.

Alle Geschäfts-IDs müssen exakt der disjunkten Vereinigung 12+58 entsprechen;
ursprüngliche fünf sowie spätere 29 Rollback-IDs, fremde/fehlende Zeilen = STOP.
Die 58 neueren Zeilen besitzen keinen historischen Feldwerthash; aktuelle Werte
erst beim separat genehmigten tatsächlichen Lesen bestätigen, keine rückwirkende
Feldgleichheit erfinden. Beide Vollsnapshots müssen identisch sein. Dann erst
`T03_SEVENTY_CURRENT_FINGERPRINT` ausgeben. Dieser aktuelle Hash ist **noch unbekannt**.
Der historische b223-Hash bleibt unverändert eine Zwölfzeilenreferenz.

Vorab wie bisher Guards, Arbeitsstand, Konfiguration, keine Fremdschreiber,
Passwort nur auf Vorhandensein, Integrität der archivierten Evidenzen prüfen.
Alle Befehle offline/no-daemon, jeweils vorher identischer --dry-run. Vorlage:

```powershell
.\gradlew.bat --offline --no-daemon t03SeventyReadOnlyTest -Pt03SeventyApproval=READ_ONLY_SEVENTY_ROWS --dry-run
# Nur nach gesonderter ausdrücklicher Freigabe A und erfolgreichem Dry-Run:
.\gradlew.bat --offline --no-daemon t03SeventyReadOnlyTest -Pt03SeventyApproval=READ_ONLY_SEVENTY_ROWS
```

Danach anhalten, tatsächlichen Hash/Bestand berichten. Keine Verkettung zu B.

### 23.3 Acht offene Fälle – separate Schreibfreigabe B erforderlich

Task `t03RemainingTest` enthält ausschließlich T03RemainingAcceptanceTest mit
festem Methodenfilter für controlledRejectionsUseFullyRolledBackFixtures und
beforeCommitFailureRollsBackAndQuarantinesFurtherWrites. Der erste überschriebene
ParameterizedTest enthält nur null/stale/details/duplicate/unknownPrice/cartDetails/
edition in dieser Reihenfolge (Order 9); letzter beforeCommit-Fall Order 10.
Die neun früher erfolgreichen Fälle sind durch den Taskfilter ausgeschlossen,
ambiguous auch aus der Parameterquelle entfernt. Taskprüfungen sperren eine
geänderte Klassen-/Methoden-Allowlist vor Ausführung. Keine CLI-Testfilter ergänzen.

Gemeinsame bestehende Matriximplementierung wiederverwendet, keine duplizierten
Fachassertions. Default-Baseline und Default-Budget des alten Tasks bleiben
unverändert. Neue Unterklasse überschreibt nur Baselineprüfung (70), Budget (2)
und Parameterquelle (7); sämtliche sonstigen Assertions/Coordinator-/Hook-/
Snapshot-/Quarantäneregeln bleiben erhalten. Neue JVM/eigener Kontext, frische
Zähler/Laufpräfixe/Coordinatorinstanz; keine Abhängigkeit zu alten In-Memory-Zuständen.
Keine alte Fixture für Fachwrites wiederverwenden: alle sieben negativen Graphen
werden neu und vollständig zurückgerollt. Vorherige 70 Zeilen sind strikt fremder
Vorbestand und nach jedem Fall vollständig gegen Lauf- und Fallbeginn geschützt.

Budget der Fortsetzung: **maximal/exakt zwei neue dauerhafte Zeilen** im Erfolgsfall,
0 Reihen, 0 Manga-Details, 0 Bände, 1 Einkauf und 1 Einkaufsdetails. Nur diese beiden
sind die bereits ursprünglich geplante Vorbereitung des letzten beforeCommit-
Falls; die 58 bestehenden Zeilen werden nicht erneut erzeugt. Gesamt dann 72.
Geplante Rollback-IDs je negativem Fall 7/7/3/7/7/1/7 = 39; letzter beforeCommit-
Graph weitere 5 Rollback-IDs. Insgesamt 46 neue ID-Versuche, 44 zurückgerollt,
2 dauerhaft. AUTO_INCREMENT kann steigen, wird nicht zurückgesetzt.
Konkret nach Tabellen: 7 Reihen, 6 Details, 18 Bände, 8 Einkäufe, 7 Einkaufsdetails
als Versuche; davon nur letzter Einkauf/Details dauerhaft. Kein DELETE/Cleanup.
beforeCommit bleibt letzter Schreibtest; danach nur dokumentierte lesende
Endkontrollen und gesperrter Schreibversuch, keine Quarantäneaufhebung.

Vorlage B erst nach akzeptierter A, neuer im Chat freizugebender UUID und deren
bestätigtem tatsächlichen 70-Zeilen-Vollhash; Platzhalter nicht unverändert ausführen:

```powershell
.\gradlew.bat --offline --no-daemon t03RemainingTest -Pt03RemainingApproval=EIGHT_OPEN_CASES_TWO_DURABLE_ROWS -Pt03RemainingRunId=<NEUE_UUID> -Pt03RemainingBaselineFingerprint=<BESTAETIGTER_70_ZEILEN_HASH> --dry-run
# Nur nach eigener ausdrücklicher Schreibfreigabe B, identische Argumente:
.\gradlew.bat --offline --no-daemon t03RemainingTest -Pt03RemainingApproval=EIGHT_OPEN_CASES_TWO_DURABLE_ROWS -Pt03RemainingRunId=<NEUE_UUID> -Pt03RemainingBaselineFingerprint=<BESTAETIGTER_70_ZEILEN_HASH>
```

Historische UUIDs explizit gesperrt, Verzeichnis CREATE_NEW, keine Marker durch
Dry-Run. Alter b223-Hash als aktueller Vollhash ausdrücklich abgewiesen; neue
Baseline zusätzlich im Testkontext gegen den tatsächlichen Snapshot geprüft.
Jeder Fehler = STOP; kein Retry, Resume, Ersatz-UUID, Cleanup, Rebaselining oder
Zusatzdiagnose. Keine neun bereits erfolgreichen Fälle neu starten.

### 23.4 Tatsächliche datenbankfreie Nachweise und Grenzen

**263 Javafälle bestanden, 0 Fehler/Skips**: 238 bisherige + 7 NULL-/Identitäts-
Fixturefälle, 17 Historien-/Projektions-/Whitelistfälle und ein neuer Test der
ursprünglichen fünf Rollback-IDs. Historischer Unboxingfehler reproduziert und
neuer Objektpfad für NULL/0/positiven Index/optionale und ungültige Referenzen
geprüft. Projektionsänderung an alter Zeile/Schema/Stammdaten wird abgewiesen;
neue 58 werden ausdrücklich nicht gegen erfundene historische Feldwerte geprüft.
Fremde/fehlende/Rollback-IDs, beschädigte Hash-/Phasenbindung und Übernahme alter
IDs als neue Fixtures werden abgewiesen.

t03ClasspathCheck mit -Pt03HistoryCheck=true erfolgreich: sieben isolierte Tasks,
gepinntes lokales Journal/Report wirklich gelesen (58/29 bestätigt), feste
Methodenfilter und tatsächliche sieben Parameter + geerbter Order-10-Fall anhand
kompilierter Metadaten geprüft, vollständige Classpaths/Lifecycle-/Manual-Trennung.
Keine Testmethode im Opt-in-Source-Set ausgeführt. Damit keine Behauptung eines
neuen JUnit-/Spring-/JPA-/MySQL-Laufnachweises. Erst spätere echte Stufen prüfen
Provider-/Transaktionsverhalten; alle Ausführungsfreigaben bleiben gesondert nötig.
Historische Dateien/Artefakte/Journale unverändert, keine Datenbankverbindung,
Anwendung/Listener, Browser, Worker/Scraper, Installation oder Git-Schreibaktion.

## 24. Nachtrag 06.11.2026 – aktueller 70-Zeilen-Bestand lesend verifiziert

**T03_6B_SEVENTY_READ_ONLY_VERIFIED**. Ausschließlich Freigabe A aus §23.2 genau
einmal ausgeführt. Schreibfreigabe B nicht erteilt/ausgeführt; t03RemainingTest
bleibt gesperrt. Historische Teilversuche werden nicht nachträglich umgedeutet.
Branch codex/t03-data-integrity, HEAD b09c6f5 unverändert.

Vorher: Passwortvariable vorhanden/nicht leer, kein Wert ausgegeben; keine
relevanten Env-/Gradle-Overrides, keine Java-Prozesse/etablierten MySQL-Client-
verbindungen beobachtet. Unveränderte Guards und Profilkonfiguration, ausschließlich
GuardedTestDataSource. 263 datenbankfreie Javafälle bestanden, 0 Fehler/Skips,
inklusive 44 Guards; Classpath-/Tasktrennung aller sieben Tasks und gepinnte
lokale Historienauswertung (58/29) erfolgreich. Java 21, alle Gradle-Aufrufe
offline/no-daemon nach jeweils identischem erfolgreichem Dry-Run.

Genau einmal tatsächlich ausgeführt:

```powershell
.\gradlew.bat --offline --no-daemon t03SeventyReadOnlyTest -Pt03SeventyApproval=READ_ONLY_SEVENTY_ROWS --dry-run
.\gradlew.bat --offline --no-daemon t03SeventyReadOnlyTest -Pt03SeventyApproval=READ_ONLY_SEVENTY_ROWS
```

**1 Test bestanden, 0 Fehler, 0 übersprungen**, BUILD SUCCESSFUL in 25s.
Geschützter Spring-/JPA-MOCK-Kontext startete im Profil test; EntityManagerFactory
nach Ende geschlossen. Kein Listener/Scraper/Worker oder fachlicher Schreibpfad.
Identität mangalib_test/mangalib_test_user bestätigt; fester Guard-Zugang
localhost:3306. SHOW GRANTS bestand in beiden readOnly-TXs: global nur USAGE,
nur SELECT/INSERT/UPDATE/DELETE auf literal abgegrenztem mangalib_test-Schema,
keine zusätzlichen Rechte/Rollen/GRANT OPTION.

Zehn erwartete InnoDB-Tabellen, sämtliche erfassten Schema-/Spalten-/Index-/FK-
Metadaten und alle 78 historischen Stammdatensätze unverändert. Tatsächliche
ID-Mengen/Verteilung der Geschäftszeilen exakt bestätigt:
10 mangareihe, 10 mangadetails, 35 baende, 8 einkaufsliste, 7 einkaufslistedetails,
gesamt **70**. Disjunkte Zuordnung 12 aus dem ersten Commitlauf und 58 aus den
bestätigten dauerhaften Gruppen des Teilversuchs. Alle ursprünglichen fünf und
späteren 29 Rollback-IDs abwesend, keine fremden/fehlenden/zusätzlichen IDs.

Die vollständige RAM-Projektion der zwölf Originalzeilen inklusive unveränderter
Schema-/Stammdaten stimmt exakt mit
b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a überein:
damit auch ihre vollständigen Feldwerte geschützt, nicht nur IDs. Historische
Leerbaseline ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570
ebenfalls über die unveränderte Policy bestätigt. Keine Tabellen tatsächlich geleert.

Zwei unabhängige vollständige Snapshots exakt identisch. Der erstmals tatsächlich
bestätigte aktuelle 70-Zeilen-Vollfingerabdruck ist:

`T03_SEVENTY_CURRENT_FINGERPRINT=0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991`

Die heutigen Werte der 58 späteren Zeilen sind damit vollständig im aktuellen
Snapshot gebunden. Keine rückwirkende Feldgleichheit mit deren Commitzeitpunkt
behaupten; b223 bleibt separat historische Zwölfzeilenreferenz. Keine automatische
Referenzanpassung: alle Erwartungen bestanden unverändert.

UUID-/Journal-/Reportbindungen gemäß §23 erfolgreich; auch der gescheiterte leere
Infrastrukturversuch 7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e bleibt unverändert FAILED.
Ambiguous-Erfolg des Teilversuchs bleibt vollständiger Rollback ohne dauerhafte
Zeilen. Aktueller JUnit-Bericht-SHA-256
e548c11c51c799ac03095c3795c942ec905451606eed46164b876a5cd8878fe8;
XML/HTML byteidentisch privat unter .gradle/t03-continuation-preparation/seventy-read-only-20261106/ gesichert.
Keine Rohdaten/Secrets in öffentliche Dokumente übernommen.

Nachweisgrenzen: geprüfte SELECT-/SHOW-only-Quellen, readOnly-TXs, deaktivierte
Initialisierung/DDL/Migrationen und identische Snapshots; keine zusätzliche
serverweite SQL-Auditierung oder globale Sperre gegen externe Schreiber.
Keine INSERT/UPDATE/DELETE/DDL, Fixtures, Rechteänderungen, Cleanup oder Reset.
Nur neue Nachträge in TESTING.md und diesem Dokument; übrige lokale Arbeiten,
historische Experimente/Journale/Reports/Manual-JARs erhalten, Staging leer.
Keine Git-Schreibaktion und kein weiterer DB-/Anwendungs-/Browser-/Scraperstart.
Nach dem einen lesenden Lauf gestoppt. Die acht offenen Fälle benötigen weiter
eine neue ausdrückliche Schreibfreigabe, frische einmalige UUID und Bindung an
den oben tatsächlich bestätigten Vollhash; keine automatische Fortsetzung.

## 25. Einmaliger Restmatrixlauf vom 08.11.2026

- Branch `codex/t03-data-integrity`, HEAD `b09c6f5`. Die 263 gefilterten
  datenbankfreien Tests, der Classpathnachweis und die Historienprüfung
  bestanden. Der identische Offline-Dry-Run des Resttasks war erfolgreich.
- `t03RemainingTest` wurde genau einmal mit Freigabewert
  `EIGHT_OPEN_CASES_TWO_DURABLE_ROWS` und UUID
  `11a8a5f8-82a4-4655-98eb-c15e8edfa224` ausgeführt. Der geschützte Start
  bestätigte Identität, Minimalrechte, Schema und den Ausgangsfingerabdruck
  `0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991`
  mit 70 Geschäftszeilen und 78 Stammdatensätzen.
- `null` bestand mit `AMBIGUOUS_SERIES` und Rollback. `stale` brach an der
  ersten fachlichen Assertion ab. Erwartet war `BAND_CONFLICT`, tatsächlich
  kam `INVALID_INVENTORY`. Die nachgelagerte `UNKNOWN_STATUS`-Assertion
  wurde nicht erreicht. Der Service prüft die Bestandskonsistenz vor dem
  bestätigten Nummernbereich. Welche konkrete Bestandsbedingung zur
  Ablehnung führte, ist durch den Bericht nicht belegt.
- Die STOP-Barriere blockierte `details`, `duplicate`, `unknownPrice`,
  `cartDetails`, `edition` und den abschließenden
  `beforeCommitFailureRollsBackAndQuarantinesFurtherWrites`-Test vor ihrem
  jeweiligen Testkörper. JUnit meldete 8 Fälle, 1 bestanden, 7 fehlgeschlagen,
  0 übersprungen. Der gezielte `beforeCommit`-Fehler, dessen
  `afterCompletion`-Nachweis und die abschließende Coordinatorquarantäne
  bleiben offen.
- Beide gestarteten Fälle wurden zurückgerollt. Die laufinternen
  Erhaltungskontrollen meldeten keinen zusätzlichen Befund für den
  geschützten Bestand, die Stammdaten oder das Schema. Keine neuen dauerhaft
  gespeicherten Geschäftszeilen. Kein separater Nachlauf zur Hashmessung,
  keine Bereinigung und keine Wiederholung.
- Das Journal enthält 1 `PREPARED`, 14 `ID`, 1 `STAGE_CONFIRMED` und
  8 `STOP`-Einträge. Journal-SHA-256
  `45af531482ea24a11ed4f8eecf79473c21434b93b0588a9c8cb335a85e0106ce`.
  Der XML-Bericht ist unter `.gradle/t03-integration/<UUID>/matrix-report/test-results/`
  archiviert. XML-SHA-256
  `dff9746737fb3c320101b5000ab874f5445334e493269fe729ea6d3acc6593c8`.

**Status `T03_6B_REMAINING_FAILED`.** Die Lauf-UUID ist verbraucht.

## 26. Datenbankfreie Stale-Korrektur und Fortsetzung mit sieben Fällen

Die Fixture legt drei Bände (1–3) für die neu erzeugte Reihe an. Die
Einkaufsposition ist ungekauft und enthält einen weiteren Band. Damit
ist 4–4 aktuell und der tatsächlich übergebene Bereich 2–2 veraltet.
Die Detailanlage speichert die besitzende Beziehung
`MangaDetails.mangaReihe`, setzt aber das inverse Feld der bereits
verwalteten Reiheninstanz nicht. `flush()` schreibt die Fixture, lädt
diese Instanz jedoch nicht neu. Die Kaufabfrage mit Lock kann die bereits
verwaltete Reihe wiederverwenden. `editableRelations` weist deren
fehlende inverse Detailreferenz als `INVALID_INVENTORY` ab, bevor die
Bandliste oder der bestätigte Nummernbereich geprüft werden.

Die Matrix lädt den Fixturebestand nach Flush aus einem geleerten
Persistenzkontext innerhalb derselben Rollback-Transaktion neu. Der
Stale-Fall prüft vor dem Kauf die Beziehungen, den ungekauften Status,
die deklarierte und tatsächliche Bandanzahl, die Nummern 1–3,
unterschiedliche positive IDs und vorhandene Preiswerte. Die
fachlichen Ergebnisassertions bleiben unverändert. `UNKNOWN_STATUS`
hat bei NULL-Kaufstatus weiterhin Vorrang vor der Bestandsprüfung.
Die Mockregression reproduziert den Unterschied zwischen fehlender und
materialisierter inverser Beziehung. Das Hibernate-Reload-Verhalten
der korrigierten Fixture ist erst im nächsten realen Lauf nachgewiesen.

### Opt-in und Budget

Erlaubt sind ausschließlich die Klasse `de.mangalib.T03RemainingAcceptanceTest`
und ihre beiden Methodenfilter:

- `controlledRejectionsUseFullyRolledBackFixtures` mit den Parametern
  `stale`, `details`, `duplicate`, `unknownPrice`, `cartDetails`, `edition`.
- `beforeCommitFailureRollsBackAndQuarantinesFurtherWrites` als letzter
  Test (Order 10).

Die neun früheren Matrixerfolge und der erfolgreiche NULL-Fall sind
ausgeschlossen. Die sechs Negativfälle versuchen in der genannten
Reihenfolge 7, 3, 7, 7, 1 und 7 IDs. Der Abschlussfall versucht fünf
Rollback-IDs und bereitet zwei dauerhafte Einkaufszeilen vor.

| Tabelle | ID-Versuche | Erwartete Rollback-IDs | Maximal dauerhaft |
| --- | ---: | ---: | ---: |
| `mangareihe` | 6 | 6 | 0 |
| `mangadetails` | 5 | 5 | 0 |
| `baende` | 15 | 15 | 0 |
| `einkaufsliste` | 7 | 6 | 1 |
| `einkaufslistedetails` | 6 | 5 | 1 |
| **Gesamt** | **39** | **37** | **2** |

Der bestehende Snapshot prüft vor dem ersten Write erneut den
Vollfingerabdruck, die historischen ID-Gruppen und den geschützten
Bestand. Eine gesonderte Lesestufe ist dafür nicht nötig. Der Task
akzeptiert ausschließlich die letzte unabhängig gemessene Referenz
`0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991`.
Der fehlgeschlagene Lauf hat keinen neuen Vollfingerabdruck geliefert.
Seine UUID ist zusätzlich explizit gesperrt. Die atomare
Verzeichnisreservierung bleibt verpflichtend.

Erst nach gesonderter Freigabe und mit einer frischen genehmigten UUID
kommt folgender einmaliger Aufruf infrage. Zuerst denselben Aufruf
mit zusätzlichem `--dry-run` prüfen.

```powershell
.\gradlew.bat --offline --no-daemon t03RemainingTest '-Pt03RemainingApproval=SEVEN_OPEN_CASES_TWO_DURABLE_ROWS' '-Pt03RemainingRunId=<NEUE-FREIGEGEBENE-UUID>' '-Pt03RemainingBaselineFingerprint=0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991'
```

265 datenbankfreie Tests, Opt-in-Kompilierung, Classpath- und
Historienprüfung erfolgreich. Kein Datenbanklauf. Journal und archivierter
Bericht aus Abschnitt 25 stimmen bytegenau mit ihren SHA-256-Bindungen
überein. **Status `T03_6B_SEVEN_REMAINING_READY_FOR_APPROVAL`.**

## 27. Reale Abnahme der sieben Restfälle am 08.11.2026

**Status `T03_6B_SEVEN_REMAINING_ACCEPTED`.** Der in Abschnitt 26
dokumentierte Aufruf lief mit UUID `f91b7d46-ec82-4a50-9e71-26c40bd57a83`
genau einmal. Freigabewert `SEVEN_OPEN_CASES_TWO_DURABLE_ROWS`.
Die UUID war vor der Ausführung unreserviert und ist jetzt verbraucht.
265 datenbankfreie Tests und die Opt-in-Kompilierung, Classpath-,
Tasktrennungs-, Methodenfilter- und Historienprüfungen bestanden.
Beide Offline-Aufrufe ohne Daemon wurden zuvor mit identischem
`--dry-run` geprüft.

Die bestehenden Guards und Vorprüfungen bestätigten vor dem ersten
Write `localhost:3306/mangalib_test`, `mangalib_test_user`, Profil `test`,
Minimalrechte, Proxy- und Transaktionsarchitektur und den Vollfingerabdruck
`0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991`.
Die Baseline umfasste zehn InnoDB-Tabellen, 78 Stammdatensätze und
70 vorbestehende Geschäftszeilen.

| Fall in Ausführungsreihenfolge | Bestätigtes Ergebnis |
| --- | --- |
| `stale` | Realer JPA-Reload nach Flush und Clear in derselben Transaktion. Details, drei Bände 1–3 mit eindeutigen persistierten IDs, Preise, Beziehungen und ungekaufter Status bestätigt. `2–2` war gegenüber dem aktuellen Bereich `4–4` veraltet. `BAND_CONFLICT`, anschließend `UNKNOWN_STATUS`, Fixture zurückgerollt. |
| `details` | `INVALID_INVENTORY`, Fixture zurückgerollt. |
| `duplicate` | `INVALID_INVENTORY`, Fixture zurückgerollt. |
| `unknownPrice` | `INVALID_INVENTORY`, Fixture zurückgerollt. |
| `cartDetails` | `DETAILS_NOT_FOUND`, Fixture zurückgerollt. |
| `edition` | `INCOMPATIBLE_SERIES`, Fixture zurückgerollt. |
| `beforeCommitFailureRollsBackAndQuarantinesFurtherWrites` | Zwei eigene Einkaufszeilen vorab committet. Gezielter `beforeCommit`-Fehler, `afterCompletion=ROLLED_BACK`, keine aktive Transaktion oder Synchronisation, Coordinatorquarantäne und Abweisung eines weiteren Schreibversuchs vor Ausführung seines Testkörpers bestätigt. Keine Hintergrundarbeit in diesem Restlauf. |

JUnit meldete sieben bestanden, null fehlgeschlagen, null übersprungen.
Das Journal enthält 1 `PREPARED`, 7 `STAGE_CONFIRMED`, 0 `STOP`
und 39 ID-Einträge. Die Zuordnung zu den 37 Rollback-IDs und zwei
dauerhaften IDs ergibt sich aus den geprüften Transaktionen und Snapshots.

| Tabelle | ID-Versuche | Rollback | Dauerhaft neu |
| --- | ---: | ---: | ---: |
| `mangareihe` | 6 | 6 | 0 |
| `mangadetails` | 5 | 5 | 0 |
| `baende` | 15 | 15 | 0 |
| `einkaufsliste` | 7 | 6 | 1 |
| `einkaufslistedetails` | 6 | 5 | 1 |
| **Gesamt** | **39** | **37** | **2** |

Die laufinternen lesenden Endkontrollen bestätigten 72 Geschäftszeilen
und den vollständigen Erhalt der 70 ursprünglichen Geschäftszeilen,
der 78 Stammdatensätze und der erfassten Schema-Metadaten. Ein neuer
Vollfingerabdruck des 72-Zeilen-Stands wurde nicht ermittelt.

Journal-SHA-256
`1b7f966bbedb7c01aee8df117dbd1fbb3bf822883acfcad01d68407ae6a1cc69`.
Der XML-Bericht wurde im neuen Laufverzeichnis archiviert. XML-SHA-256
`448f63fdac9664943acc0fc0b916cc28330b7a4bcb7000184b28af55ba64cd56`.
Historische Evidenzen bleiben erhalten. Keine Wiederholung, zusätzliche
Datenbankdiagnose, Bereinigung, Aufhebung der Quarantäne oder Fortsetzung
zur Browserabnahme.
