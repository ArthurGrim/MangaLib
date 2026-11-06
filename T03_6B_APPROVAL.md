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
