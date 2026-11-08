# T03-6C – Isolierte Browserabnahme

Stand 08.11.2026 · `T03_6C_READY_FOR_APPROVAL`

Der Plan ist ausführungsbereit zur gesonderten Freigabe. Kandidat, aktueller
manueller Snapshot und reale Browserergebnisse stehen aus. Ausgangspunkt ist
`codex/t03-data-integrity`, HEAD `ba18ffc0e7c9bd777247f6be0476a52d94974417`.
Der T03-6B-Abschluss und seine Daten auf `mangalib_test` sind ein separater
Nachweis. Die acht unversionierten Windows-Experimente gehören nicht zum Build.

## Isolierung und Artefakt

- `manualBootJar` packt den eigenen `ManualTestLauncher`, das Profil
  `manual-test` und den synthetischen Gateway. Der reguläre Launcher,
  `application.properties`, Live-Scraper, Selenium, Downloadmanager,
  Windows-Worker und DevTools sind vom manuellen Paket ausgeschlossen.
- Der Launcher prüft Argumente, Umgebung und Profil, registriert genau die
  manuelle DataSource und den synthetischen Gateway und sperrt Ersatzbeans.
  `ManualTestDataSource` prüft die Konfiguration erneut bei jedem
  Verbindungsaufbau, verwendet das feste Ziel
  `127.0.0.1:3306/mangalib_manual_test` und prüft Schema und Account
  über die Verbindung. Ausschließlicher Benutzer `mangalib_manual_test_user`.
  JPA validiert das Schema. SQL-Initialisierung, DDL-Erzeugung und Migrationen
  sind deaktiviert. Die JUnit-Guards für `mangalib_test` sind davon getrennt.
- Nur `127.0.0.1:18080` lauscht. Browserbasis ist
  `http://localhost:18080`. Host-, Origin- und Session-CSRF-Prüfungen bleiben
  aktiv. Das manuelle Banner und die restriktive CSP für Bilder, Verbindungen
  und Frames ergänzen die Startprüfung. Externe Links, Autofill und Scraping
  gehören nicht zu dieser Abnahme. Optionale URLs in neuen Fixtures bleiben leer.

Die drei bestehenden JARs wurden lesend geprüft und bleiben geschützt.

| Artefakt unter `build/libs` | SHA-256 |
| --- | --- |
| `mangalib-manual-test.jar` | `9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9` |
| `mangalib-manual-test-t02-closeout.jar` | `e89e4e8b2947082892ac847ae842793faa63c96f4422d55638508b1c4553fa1f` |
| `mangalib-manual-test-t02-closeout-tomcat-11.0.27.jar` | `2b4859de35e3437adf2cc59db8028043860c9b7606d40f0d0b3845e64d0d41e2` |

### Freigabe A – separaten Kandidaten paketieren

Vor dem Build Branch, vollständigen HEAD und Arbeitsbaum prüfen. Erwartet
werden der oben genannte Stand, die acht Experimente und dieses Planungsdokument.
Bei weiteren Änderungen Buildherkunft erneut prüfen. Das neue Ziel
`build/libs/mangalib-manual-test-t03-6c-ba18ffc.jar` muss frei sein.
Ein neues lokales Evidenzverzeichnis `.gradle/t03-6c-ba18ffc/` anlegen.
Dort nach Freigabe ein neues Init-Skript `manual-t03-6c.init.gradle` nach
dem bereits verwendeten Verfahren anlegen. Es ändert nur den Paketnamen.

```groovy
gradle.projectsEvaluated {
    gradle.rootProject.tasks.named('manualBootJar').configure {
        archiveFileName.set('mangalib-manual-test-t03-6c-ba18ffc.jar')
    }
}
```

```powershell
.\gradlew.bat --offline --no-daemon -I .gradle/t03-6c-ba18ffc/manual-t03-6c.init.gradle assembleManualTest --dry-run
.\gradlew.bat --offline --no-daemon -I .gradle/t03-6c-ba18ffc/manual-t03-6c.init.gradle assembleManualTest
```

Den zweiten Aufruf erst nach Prüfung auf reine Compile-/Resources-/Packaging-
Tasks ausführen. `manualSafetyTest` hängt ebenfalls von `manualBootJar` ab
und würde ohne den Override das historische Standardartefakt adressieren.
Dieser Test ist für die geplante Paketierung kein impliziter Begleittask.

Anschließend Manifest, ZIP-Einträge und Runtime-Bibliotheken prüfen. Launcher,
Guard, DataSource, Profil und Double müssen vorhanden sein. Produktionslauncher,
Produktivkonfiguration, Selenium, Live-Scraper und native Worker müssen fehlen.
Ihr Auftreten führt zu STOP. Die Kauf-/Coordinator-Klassen und T03-JavaScript-
Ressourcen müssen aus dem geprüften Checkout stammen. Relevante Ressourcen
gegen die Arbeitsbaumdateien vergleichen und den Build mit Branch, vollständigem
HEAD, Arbeitsbaumstatus, Taskplan und SHA-256 des neuen JARs dokumentieren.
Alle drei Alt-JARs erneut hashen. Vor einer Startfreigabe den konkreten
Kandidatenhash und das lokale Manifest vorlegen.

### Freigabe B – gebundener Start und lesende Snapshots

1. Kandidatenhash unmittelbar vor `-ValidateOnly` und erneut vor dem echten
   Start prüfen. Java 21 verwenden. Passwortvariable ausschließlich auf
   Vorhandensein prüfen. Bei Fehlen STOP, damit die interaktive
   Passwortabfrage des Skripts nicht verwendet wird. JVM-/Spring-/JDBC-
   Overrides ausschließen und Port 18080 lesend auf freie Bindung prüfen.
2. Das dokumentierte Startskript zunächst mit dem expliziten Artefaktpfad
   verwenden. `-ValidateOnly` prüft das Artefakt und seine Konfiguration
   ohne JDBC, Spring-Kontext oder Listener. Die Ausführung startet eine JVM.

   ```powershell
   $jar = 'build/libs/mangalib-manual-test-t03-6c-ba18ffc.jar'
   $java21 = 'C:/Program Files/Java/jdk-21/bin/java.exe'
   .\scripts\Start-ManualTest.ps1 -ArtifactPath $jar -JavaPath $java21 -ValidateOnly
   ```

3. Vor Anwendungsstart den geschützten lesenden Ausgangssnapshot erfassen.
   Vorlage ist die lokale, bereits verwendete
   `.gradle/t02-ui-closeout-20261102/ReadManualSnapshot.ps1`.
   SHA-256 `1765cb3ffeffb22bb71a1759731a693bbc88c7d8225413c172897804d575db6a`.
   Die Vorlage stimmt mit der M06-Kopie überein. Vor Ausführung erneut
   prüfen, bytegleich in ein neues privates Verzeichnis kopieren und dort
   mit `-Phase before` aufrufen. Sie verwendet den festen manuellen
   MySQL-Zugang ohne Standard- und Login-Dateien, Read-only-Transaktion,
   feste SELECTs und abschließendes ROLLBACK. Sie prüft die manuelle
   Datenbank-/Benutzerkennung und erfasst zehn Tabellen sowie fünf
   Schemaabschnitte. Keine frei formulierten SQL-Abfragen.
4. Dasselbe Startskript ohne `-ValidateOnly` in einer steuerbaren
   Startsession genau einmal ausführen. PID, Java-Executable, JAR-Pfad,
   einzige Bindung `127.0.0.1:18080`, Banner und Profil bestätigen.
   Bei abweichender Identität keine Browseraktionen.
5. Nach den Browserfällen im selben Snapshotverzeichnis mit
   `-Phase after` erfassen. Für Zwischenprüfungen jeweils ein neues
   privates Verzeichnis und dieselbe bytegleiche Vorlage verwenden.
   Alte Snapshots bleiben erhalten. Die Vorher-/Nachher-Vollhashes dürfen
   durch autorisierte neue Zeilen abweichen. Maßgeblich sind vollständige
   Zeilenvergleiche per ID und identische Stamm-/Schemaabschnitte.
   Kein historischer manueller Hash wird als aktueller Ausgangswert übernommen.

## Freigabe C – Browserfälle und Schreibbudget

Laufpräfix `T03_6C_BROWSER_20261108_R01`. Vor dem ersten Save prüfen, dass
das Präfix und beide unten vorgesehenen externen Reihenindizes im
Ausgangssnapshot fehlen. Geplante positive synthetische Indizes
`1903600011` für A und `1903600012` für N. Bei Kollision STOP. Stammdaten
aus den bestehenden Dropdowns wählen, für identische Editionen jeweils
denselben Verlag, Typ und dasselbe Format. Alle Einkäufe mit einem im
UI-Filter sichtbaren Erscheinungsdatum erfassen. Die Indizes nur als
Formulardaten verwenden.

Eigene Fixtures

- Reihe **A** mit Titel `T03_6C_BROWSER_20261108_R01_A`, Index für A,
  drei Bänden zu je 6,00 € und Gesamtanpassung 0,00 €. Für einen
  aussagekräftigen Erhaltungsnachweis Band 1 einmal über den regulären
  Banddialog auf 7,00 €, gelesen, Reread 2 und ein abweichendes verfügbares
  Spezialflag setzen. Bände 2 und 3 bleiben unverändert. Danach den
  Referenzzustand aller drei Bandzeilen und des Reihenaggregats festhalten.
- **A1** und **A2** als zwei eigene Einkaufspositionen mit A-Titel, A-Index
  und passenden Stammdaten. Beide haben einen Band, A1 kostet 8,49 €
  mit −0,01 € Anpassung. A2 kostet 8,49 € mit 0,00 € Anpassung.
  Im Browser A1 und A2 anhand verschiedener Erscheinungstage und der
  über Reload stabilen Kaufbutton-ID eindeutig unterscheiden.
- **N** als eigene Einkaufsposition mit Titel
  `T03_6C_BROWSER_20261108_R01_N`, Index für N, drei Bänden zu je 2,00 €
  und +0,02 € Gesamtanpassung. Eine Bibliotheksreihe N entsteht erst
  beim Kauf.

Jeden gespeicherten Schritt nach vollständigem Reload prüfen. Die
Browseranzeige genügt für sichtbare Werte. Band-IDs und alle Spalten,
einschließlich nullable Werte, Beziehungen und Zeitstempel, mit privaten
Snapshots abgleichen. Statuscodes nur aus einer tatsächlichen
Responsebeobachtung berichten. Ausschließlich reguläre UI-Interaktionen.

| Schritt | UI-Aktion und erwarteter Nachweis | Maximal neu / erlaubte Bestandsänderung |
| --- | --- | --- |
| 0. Fixtures | A über den Anlagedialog erstellen, Band 1 gezielt bearbeiten, A1/A2/N über die Einkaufserfassung anlegen. Nach Reload IDs, Mengen, Edition und Preise kontrollieren. | A: 1 Reihe, 1 Detail, 3 Bände. A1/A2/N: 3 Einkäufe, 3 Details. Die eigene Band-1-Zeile und das dazugehörige Aggregat dürfen durch den einen Bandedit geändert werden. |
| 1. Vorschau und Abbruch | A1 über den Kaufbutton öffnen. Bereich 4–4 muss angezeigt werden. Abbrechen, vollständig neu laden und Bestand vergleichen. Danach in Tab 1 erneut A1 öffnen. Parallel in Tab 2 A2 öffnen, dort ebenfalls 4–4 beobachten. | 0. Kein Kaufstatuswechsel und keine neue Bibliothekszeile. |
| 2. Bestehende Reihe | In Tab 1 für A1 einmal „Bände übernehmen“ klicken. Reload. Nur A1 ist gekauft, A hat nun Band 4 mit 8,49 € und −0,01 €, Reihe insgesamt 27,48 € bei bestätigtem Ausgangswert 19,00 €. Altbänder unverändert. | 1 Band für A. Eigene Reihe A und ihre Detailzeile dürfen nur die vorgesehenen Aggregate/Lesestatus aktualisieren, A1 nur seine Kaufmarkierung. |
| 3. Stale und zwei Tabs | Im seit Schritt 1 offenen Tab 2 die alte A2-Vorschau 4–4 bestätigen. Der aktuelle Bereich ist 5–5. Kontrollierten Bandkonflikt, kein automatisches Replay und keinen Kauf von A2 prüfen. Nach Reload darf eine neue Vorschau 5–5 zeigen. Abbrechen. | 0. Kein weiterer Bestätigungsversuch und kein Kauf von A2. |
| 4. Wiederholter Kauf | A1 nach Reload erneut per regulärem Kaufbutton aufrufen. Falls der Button nach Reload nicht bedienbar ist, nur den sichtbaren Kaufstatus und die Bestandserhaltung dokumentieren. | 0. Erwartet „Kauf bereits übernommen“, keine doppelten Bände. |
| 5. Neue Reihe und Restcents | N kaufen, im Statusdialog vorhandenen Status wählen, einmal bestätigen. Nach Reload gekauftes N, neue Reihe mit gewähltem Status und Bänden 1–3 prüfen. Anpassungen +0,01/+0,01/0,00 und Summe 6,02 €. | 1 Reihe, 1 Detail, 3 Bände. Nur eigene Position N erhält die Kaufmarkierung. |
| 6. Anzahl und Erweiterung | A bearbeiten. Zunächst Anzahl 3 ohne Erweiterungsoption versuchen, sofern der Dialog dies zulässt. Ablehnung auf UI- oder Serverseite dokumentieren. Dann mit „Neue Bände ausdrücklich ergänzen“ auf 5 erhöhen. Preisoption für 2,00 € aktivieren, Anpassungsoption auslassen, Lesestatus-/Reread-Massenoptionen ausschalten. Nach Reload ausschließlich Band 5 neu und ungelesen, alte Bandzeilen einschließlich Band 4 unverändert. | Reduktion 0. Erweiterung 1 Band für A, zulässige Aggregate/Lesestatus nur für A und ihre Details. |
| 7. Session und CSRF | Einen geladenen lokalen Tab mit der inzwischen gekauften A1-Position inaktiv lassen. Nach Ablauf der konfigurierten 2-Minuten-Session ohne Zwischenrequests genau einmal den regulären Kaufbutton betätigen. Sicherheitsfeedback und ausbleibenden Retry prüfen. Nach Reload neue Session und unveränderten Kaufzustand kontrollieren. Wenn der Connector keinen Status oder Requestcount zeigt, UI-Beobachtung entsprechend begrenzen. | 0. A1 ist bereits gekauft und bietet zusätzlich fachlichen Wiederholungsschutz. Unerwarteter Erfolg oder unklare Wirkung führt zu STOP. |
| 8. Host, Origin und unbekanntes Resultat | Reguläre Browsernavigation ändert die sicherheitsrelevanten Header nicht gezielt. Falscher Host, fremder Origin, separat fehlender oder falscher CSRF-Token sowie künstliche Antwortverluste bleiben als echte Einzelproben offen. Vorhandene gezielte Security- und DOM-Tests getrennt ausweisen. Tritt im freigegebenen UI-Lauf eine unklare Antwort auf, Meldung und Sperre beobachten, niemals nochmals absenden und ausschließlich den genehmigten lesenden Abgleich durchführen. | 0 geplante Writes. Kein Request-Replay, keine manipulierten Header, keine direkten HTTP-Requests und keine absichtlich herbeigeführte ungewisse Speicherung. |

Eine echte reine Anzahlkorrektur benötigt eine eigene Reihe mit bereits
abweichender Deklaration. Die reguläre UI soll solche Inkonsistenzen gerade
verhindern. Dieser Teilfall bleibt offen, solange keine getrennt genehmigte
eigene Fixture über einen sicheren Weg verfügbar ist. T03-6B hat beide
Korrekturrichtungen real abgenommen. Ein Speichern unveränderter Anzahl
gilt nicht als Ersatznachweis.

### Gesamtbudget

| Geschäftstabelle | Maximal neu | Zulässige Änderungen nach Anlage |
| --- | ---: | --- |
| `mangareihe` | 2 | Nur A durch den gezielten Bandedit, Kaufappend und die ausdrückliche Erweiterung. |
| `mangadetails` | 2 | Nur A bei den vorgesehenen Reihen-/Bandfolgen. Unbeteiligte Detailwerte bleiben erhalten. |
| `baende` | 8 | Nur Band 1 von A in der Fixturephase. Alle danach vorhandenen Bandzeilen bleiben bei weiteren Schritten unverändert. |
| `einkaufsliste` | 3 | Nur Kaufmarkierung von A1 und N. A2 bleibt ungekauft. |
| `einkaufslistedetails` | 3 | Keine geplante Änderung nach Anlage. |
| **Gesamt** | **18** | Kein DELETE und kein Schreibzugriff auf irgendeine Zeile des Ausgangssnapshots. |

Die drei Einkaufsdetails gehören jeweils zu genau einer eigenen
Einkaufsposition. Zwei Reihendetails gehören zu genau zwei eigenen Reihen.
Nach jedem Schritt nur erwartete neue oder geänderte IDs akzeptieren.
Vorhandene T02-/M06-Zeilen einschließlich ihrer Spaltenwerte und Beziehungen
bleiben vollständig geschützt. Private Rohsnapshots bleiben unter
`.gradle/`. Öffentlich dokumentierbar sind Hashes, Mengen und Prüfergebnisse.

## STOP und Abschlussfreigabe

Abbrechen bei fehlender SHA-Bindung, abweichendem Checkout oder Artefakt,
fehlendem Schutz oder Banner, falscher Datenbank-/Benutzeridentität,
unerwartetem Listener oder Prozess, fremdem Datensatz, unklarem ID-Mapping,
abweichendem Vorbestand, überschrittenem Budget, ungeplantem Netzwerkzugriff,
5xx oder unbekanntem Schreibresultat. Keine automatische Wiederholung,
Reparatur, Bereinigung oder Übernahme unklarer Änderungen. Bei STOP nur
den genehmigten Read-only-Abgleich nutzen, sofern Identität und Ziel
weiterhin eindeutig bestätigt sind.

Die Start-/Browserfreigabe soll außerdem den lesenden Abschlussvergleich
und das geordnete Beenden ausschließlich der selbst gestarteten Instanz
in ihrer Startsession umfassen. PID-Ende und freien Port prüfen. Bei fehlender
sicherer Sitzungszuordnung anhalten. Kein erzwungener Prozessabbruch.

**Ausstehende Freigaben** sind A für die getrennte Offline-Paketierung und
anschließend B/C für den konkret hashgebundenen Kandidaten einschließlich
ValidateOnly, Start, manuellem Datenbankzugriff, den beschriebenen UI-Aktionen,
lesenden Vergleichen und Shutdown. Sonderfixtures oder zusätzliche
Negativmethoden benötigen eine eigene Entscheidung. In dieser Vorbereitung
wurden nur lokale Dateien und Git gelesen. Kein Gradle-Lauf, keine
Paketierung, keine Datenbankverbindung und keine Browseraktion.

## Nachtrag 08.11.2026 – Freigabe A ausgeführt

**`T03_6C_CANDIDATE_PACKAGED`**. Der neue Kandidat
`build/libs/mangalib-manual-test-t03-6c-ba18ffc.jar` umfasst **66.523.398 Bytes**.
Seine verbindliche SHA-256-Bindung für B/C lautet
`0139557bb0395cd35fa8c38dbf025f7f76dd8023cb3a6c8c4718fa090bcc60a9`.

Branch `codex/t03-data-integrity`, HEAD
`ba18ffc0e7c9bd777247f6be0476a52d94974417`. Beim Build gab es keine
Änderungen an verfolgten Dateien. Das Planungsdokument und acht historische
Experimente waren unversioniert. Unter den geprüften Quell- und Gradlepfaden
waren keine ignorierten Zusatzdateien vorhanden. Das Ziel-JAR war frei.

Das lokale Init-Skript aus Freigabe A änderte ausschließlich den Namen von
`manualBootJar`. Skript-SHA-256
`5049831090e912e69cf8b54e96f5806808160c6311bf7e5f7f089f9c4173532c`.
Der oben dokumentierte Offline-Aufruf mit `--no-daemon` wurde einmal als
identischer Dry-Run und anschließend einmal real ausgeführt. Im Aufgabenplan
lagen ausschließlich Compile-, Ressourcen- und Klassenaufgaben für
`productionOwner`, `main` und `manualTest` sowie `manualBootJar` und
`assembleManualTest`. Der reale Lauf meldete `BUILD SUCCESSFUL`.
Die vorhandenen Kompilierungs- und Ressourcenausgaben waren aktuell,
`manualBootJar` wurde ausgeführt.

Die ZIP-Prüfung bestätigte Java 21, Spring Boot 4.1.1 und
`de.mangalibmanual.ManualTestLauncher` als Startklasse. Alle 29 explizit
geprüften Pflichtdateien waren vorhanden. Alle 71 Anwendungsklassen
stimmten bytegleich mit den aktuellen Gradle-Klassenausgaben überein.
Alle 53 Anwendungsressourcen stimmten bytegleich mit ihrem jeweiligen
Quellpfad im Checkout überein. Geprüft wurden damit unter anderem
Kaufservice, Coordinator und Advisor, Kaufcontroller, Reihen-/Bandservices,
Repositories, Preis- und Requestlogik, T03-JavaScript, Templates, Banner,
manuelle DataSource, Guards und synthetischer Gateway.
Die Klassenherkunft stützt sich auf den unveränderten Checkout, die
Gradle-Up-to-date-Prüfung und den Vergleich der Klassenbytes. Eine
Laufzeitprüfung fand nicht statt.

Die 118 Bibliothekseinträge enthielten die drei Tomcat-Module 11.0.27.
Die ZIP-Prüfung fand weder Produktivlauncher noch Produktivkonfiguration,
Live-Scraper, Selenium, WebDriverManager, Windows-Worker, JNA oder DevTools.
`application-manual-test.properties` war die einzige Anwendungskonfiguration
im Archiv und entsprach der Quellressource mit Passwortplatzhalter.
Private Journale, Dumps, Schlüsseldateien und lokale Berichte waren
keine Paketbestandteile. Die drei historischen Manual-JARs stimmen nach
dem Build weiterhin mit den SHA-256-Referenzen oben überein.

Das lokale Manifest liegt unter
`.gradle/t03-6c-ba18ffc/candidate-manifest.json`, SHA-256
`cb2e27e2617f86e11ea048b2267ce6907b990b72446029d4520cfa86f92036fa`.
Freigaben B/C bleiben ausstehend. Sie müssen diesen Kandidatenhash
und den begrenzten Start-, Snapshot-, UI- und Shutdown-Umfang binden.
`-ValidateOnly`, Startskript, Datenbank, Browser und Scraper wurden nicht
ausgeführt. Kein neuer manueller Ausgangssnapshot und keine Nutzung
des Budgets von höchstens 18 neuen Zeilen.

## Nachtrag 08.11.2026 – Einmalige Browserabnahme B/C

**`T03_6C_BROWSER_PARTIAL`**. Kandidat und Manifest entsprachen den oben
gebundenen Hashes. Branch `codex/t03-data-integrity`, HEAD
`ba18ffc0e7c9bd777247f6be0476a52d94974417`. Java 21 und Passwortvariable
verfügbar, keine relevanten Overrides oder bestehende Instanz erkannt.
`-ValidateOnly` bestand ohne JDBC und HTTP-Listener. Der einzige reale
Start nutzte den gebundenen Kandidaten unter PID 23144, Profil `manual-test`,
den manuellen DB-Account und ausschließlich `127.0.0.1:18080`.
Das Banner war im Codex In-app Browser sichtbar. Die Guards blieben aktiv.
Ein separater Negativnachweis der HTTP-Sicherheitsfilter wurde nicht erreicht.

Der geschützte Ausgangssnapshot bestätigte das manuelle Ziel, zehn
InnoDB-Tabellen und die Abwesenheit von Präfix und beiden geplanten Indizes.
Der frisch gemessene SHA-256 war
`2eb8d9ed03cd73a66ace123f4a3f60507f2de3d48c31450a9aacc96b8732c226`.

| Szenario | Beobachtung |
| --- | --- |
| Fixture A | Über die normale UI angelegt. Drei Bände mit unterschiedlichen IDs und korrekter Reihenbeziehung. Band 1 danach gezielt gespeichert und nach Reload im Snapshot mit 7,00 €, gelesen, Reread 2 und aktivem Spezialflag bestätigt. Bände 2/3 mit 6,00 €, ungelesen und NULL bei Reread und Spezialflag. Reihensumme 19,00 €. |
| A1/A2/N | Einkaufsformular geöffnet und ohne Speichern geschlossen. Das Feld „Änderung Preis in €“ ist standardmäßig verborgen. Der Quellabgleich zeigt, dass es nur bei gesetztem `eBay-Preis` erscheint und sonst der Betrag 0 übertragen wird. Das zusätzliche Flag für A1/N war im Fixtureplan nicht festgelegt. Deshalb STOP vor Einkaufsanlage. |
| H1, H2, H3, H4, H5 | Blockiert durch die ausstehende Einkaufsanlage. Keine Vorschau, Übernahme, Wiederholung oder Konfliktprüfung ausgeführt. |
| H6, H7 | Nach STOP nicht ausgeführt. Keine Erweiterung, Reduktion, Anzahlkorrektur oder Sessionprobe. |
| H8 | Host-/Origin-/Token-Manipulation und künstlicher Antwortverlust blieben außerhalb des Browserumfangs. |

Keine HTTP-Statuscodes direkt beobachtet. Die abgeschlossene Fixturephase
verwendete fünf von höchstens 18 neuen Geschäftszeilen. Neu sind eine
Reihe, ein Reihendetail und drei Bände. Einkaufspositionen und Einkaufsdetails
blieben bei null neuen Zeilen. An den neuen eigenen Daten erfolgte nach
Anlage ausschließlich der Band-1-Edit mit zugehöriger Aggregataktualisierung.

Der Zwischen- und Abschlusssnapshot sind bytegleich. Abschluss-SHA-256
`92512a2cdb091d4b128b08a0c847ce4e1d626858f5ac1cabe4e0dfc3a5f03581`.
Alle Ausgangszeilen blieben mit sämtlichen Feldwerten erhalten. Die
Stammdaten und fünf erfassten Schemaabschnitte blieben identisch.
Endzahlen für die Geschäftstabellen in Budgetreihenfolge 12, 12, 31, 5, 5.
Private Snapshots liegen ausschließlich unter
`.gradle/t03-6c-ba18ffc/browser-r01/`.

Die eigene Sitzung wurde per Ctrl+C beendet. Tomcat bestätigte den
geordneten Shutdown, JPA meldete das Schließen der EntityManagerFactory.
PID 23144 beendet und Port 18080 frei. Kein erzwungener Abbruch, keine
Wiederholung, Bereinigung, zusätzliche SQL-Probe oder Git-Schreibaktion.
Alle drei historischen Manual-JARs stimmen weiterhin mit ihren Hashes überein.

Für eine Fortsetzung sind eine neue Freigabe, ein frisch bestätigter
manueller Ausgangssnapshot und eine ausdrückliche Festlegung des
`eBay-Preis`-Flags für A1/N erforderlich, sofern die centgenauen Anpassungen
über das bestehende Formular geprüft werden sollen. A und ihre drei
Bände sind bereits vorhanden und dürfen nicht erneut angelegt werden.

## Nachtrag 08.11.2026 – R02 mit bestehender Reihe A

**`T03_6C_BROWSER_ACCEPTED`** für den freigegebenen Umfang H1–H7.
Branch `codex/t03-data-integrity`, HEAD
`ba18ffc0e7c9bd777247f6be0476a52d94974417`. Kandidaten-SHA-256
`0139557bb0395cd35fa8c38dbf025f7f76dd8023cb3a6c8c4718fa090bcc60a9`
und Manifest-SHA-256
`cb2e27e2617f86e11ea048b2267ce6907b990b72446029d4520cfa86f92036fa`
bestätigt. `-ValidateOnly` bestand. Genau ein Start mit Java 21, PID 38244,
Profil `manual-test`, DB `mangalib_manual_test` und Benutzer
`mangalib_manual_test_user`. Einziger Listener `127.0.0.1:18080`.
Manuelles Testbanner im Codex In-app Browser sichtbar. Guards aktiv.

Der neue Ausgangssnapshot ergab
`92512a2cdb091d4b128b08a0c847ce4e1d626858f5ac1cabe4e0dfc3a5f03581`.
Damit war der vollständige R01-Endbestand einschließlich A und ihrer
drei Bandzeilen bestätigt. Ausgangszahlen 12 / 12 / 31 / 5 / 5.
N-Index und Einkaufsfixtures waren frei. A wurde weder neu angelegt
noch wurde Band 1 erneut bearbeitet.

`eBay-Preis` blendete das Anpassungsfeld ohne zusätzliche Eingabefelder
ein. A1, A2 und N waren nach Reload anhand verschiedener Erscheinungstage
und ihrer persistierten IDs eindeutig zugeordnet. Die Snapshots
bestätigten für A1 8,49 € und −0,01 €, für A2 8,49 € und 0,00 € bei
ausgeschaltetem Flag und für N 2,00 € je Band und +0,02 €. Bei A1 und N
war das Flag aktiviert.

| Fall | Beobachtung |
| --- | --- |
| H1 | Vorschau 4–4 und Abbruch bestätigt. Reload und Snapshot zeigten weder einen Kauf noch einen neuen Band. Beide lokalen Tabs zeigten anschließend 4–4. |
| H2 | A1 einmal übernommen. Band 4 mit 8,49 € und −0,01 €, A mit 27,48 €. A2 blieb ungekauft. Die drei ursprünglichen Bandzeilen von A blieben vollständig gleich. |
| H3 | Alte A2-Vorschau im zweiten Tab einmal bestätigt. UI meldete, dass der Bandbereich nicht mehr zum Bestand passt. Kein sichtbarer automatischer Retry und keine Mutation. Nach Reload neue Vorschau 5–5 angezeigt und abgebrochen. |
| H4 | Regulärer Kaufbutton nach Reload bedienbar. „Kauf bereits übernommen“ sichtbar, keine doppelte Bandanlage. |
| H5 | N mit Status „Aktuell“ übernommen. Drei Bände zu 2,00 €, Anpassungen +0,01/+0,01/0,00 €, Gesamt 6,02 €. Status und Bandwerte im Snapshot bestätigt. |
| H6 | Reduktion 4 → 3 mit Validierungsmeldung abgewiesen. Explizite Erweiterung erzeugte nur Band 5 zu 2,00 €, ungelesen und Reread NULL. Reihensumme 29,48 €. Alle vorherigen Bandzeilen einschließlich Band 4 unverändert. |
| H7 | Nach mehr als 160 Sekunden Inaktivität A1 einmal regulär aufgerufen. Sichtbare Sicherheitsablehnung mit Aufforderung zum Reload. Kein erneutes Absenden. Nach Reload Kaufmarkierung vorhanden. Snapshot bytegleich zum Zustand vor H7. |

HTTP-Statuscodes waren im Connector nicht direkt beobachtbar. H8 und
eine echte reine Anzahlkorrektur bei inkonsistenter Deklaration waren
nicht Teil des freigegebenen Browserlaufs.

R02 erzeugte genau 13 Zeilen, davon 1 Reihe, 1 Reihendetail, 5 Bände,
3 Einkaufspositionen und 3 Einkaufsdetails. Kumulativ mit R01 18 Zeilen.
Endzahlen 13 / 13 / 36 / 8 / 8. An A änderten sich nur Anzahl, Preise und
Preisanpassung, eBay-Preis-Kennzeichen und Aktualisierungszeit. Ihre
Detailzeile blieb bytegleich. Bei den Einkaufsfixtures änderten sich nach
Anlage ausschließlich die Kaufmarkierungen von A1 und N. A2 und alle
Einkaufsdetails blieben unverändert. Neue Details und Bände wurden ihren
jeweiligen Reihen bzw. Einkaufspositionen zugeordnet.

Alle ursprünglich vor R01 vorhandenen Geschäftszeilen, alle drei
R01-Bandzeilen, Stammdaten und die fünf erfassten Schemaabschnitte
blieben mit ihren vollständigen Werten erhalten. Abschluss-SHA-256
`980f117a8d1e69a09e0ebd72f8fdb9ffea4795093f9d3a8591ceb5e798b83c5b`.
Private Snapshots unter `.gradle/t03-6c-ba18ffc/browser-r02/`.

Eigene Startsession geordnet per Ctrl+C beendet. Tomcat bestätigte den
Shutdown und JPA meldete das Schließen der EntityManagerFactory.
PID 38244 beendet, Port 18080 frei. Historische Experimente, geprüfte
Journale, Berichte und Manual-JARs hashgleich. Keine Git-Schreibaktion,
neue Paketierung, Vollsuite oder Fortsetzung zum Merge.
