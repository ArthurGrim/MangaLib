# T04 – Konkrete bestehende Fehler

Stand: 21.11.2026, codex/t04-existing-bugs. Der Auftrag „S04“ wurde als T04
interpretiert und entsprechend fortgesetzt; S04 existiert nicht in der Roadmap.

Status: T04_IMPLEMENTED_AUTOMATED_VERIFIED. Die aufgelisteten Fehlerkorrekturen
und automatisierten Nachweise sind abgeschlossen. Eine neue visuelle Browser-
prüfung wird nicht behauptet; die Checkliste unten bleibt vor Veröffentlichung.

## Umsetzungs- und Nachweismatrix

| Roadmap-Anforderung | Ergebnis |
| --- | --- |
| Einkauf nach YearMonth | Jahr/Monat über YearMonth berechnet, Gruppenbeschriftung enthält das Jahr; gleichnamige Monate überschreiben sich nicht. Bestellzeitfenster -12/+6 Monate unverändert. |
| Monatsschaltfläche | Übernimmt Monat UND Jahr; Vorjahr auch in der Jahresauswahl vorhanden. Undatiertes Segment trägt keinen falschen Monat/Jahr ein. |
| Centstatistik | Bereits vorhanden: getGeldProMonat mit BigDecimal, Regression für 0,01 und -0,02 ergänzt; bestehende MVC-/Monatscharttests bleiben erhalten. |
| Diagramm-Listener | Bereits vorhanden: dauerhafte Listener, genau ein Initialrequest, Requestrevision gegen veraltete Antworten. Bestehender statistics.test.cjs prüft Modus-/Jahreswechsel und Fehlerzustände. |
| Fehlender Preis neuer Bände | Bereits vorhanden: vollständiger Preisplan vor Mutation, fehlend/ungültig kontrolliert abgelehnt. Neue Regression trennt NULL von ausdrücklich erlaubtem 0,00 und prüft deutsches Importpreisformat. |
| Import/Abbruch | Cache für Reihentitel/-preis wird beim Abbruch gelöscht. Indexänderung verwirft laufende Autofillantwort, Importdaten und Vorschaukennung. Einkauf setzt Lade-/Fehlerzustand beim Abbruch zurück. Kein Schreib-Replay entsperrt. |
| SQL-Vorlage | Fehlendes Komma korrigiert, FK-Namen in konsistenter Kleinschreibung, explizit InnoDB. Kein festes USE mangalib, kein Datenbank-Drop/-Create. Nur historische Strukturvorlage, keine Migration/Initialisierung. |

Neue Javafälle: ShoppingMonthRegressionTest (Kollision zweier Jahresmonate,
Centtotale und unbekannter Betrag) sowie T04RegressionTest (fehlender/0-Preis,
fehlerhafter/korrekter Importpreis, Centstatistik, statische SQL-Schutzprüfung).
Neue DOMfälle mit realen Templates/Skripten: Moduswechsel nach Abbruch, Monatsbutton
mit Vorjahr, verspätete Autofillantwort nach Indexwechsel. Keine Netzwerkanfragen.

SQLQuery.sql wurde ausschließlich als Text bearbeitet und statisch geprüft,
nicht durch MySQL ausgeführt. Keine Aussage über eine reale neue Schemaanlage.
Das Skript ist kein Backup-/Migrations-/Wiederherstellungswerkzeug und enthält
absichtlich nicht automatisch das optionale S03-Schema. S03 bleibt eine eigene,
explizite additive Migration. Inserts.sql und alle bestehenden Daten unverändert.

Keine Änderungen an T03/M06/S03-Fachregeln, Datenbankguards, Sicherheitsfiltern,
Scrapercontainment oder historischen Evidenzen. Kein T05-Refactoring oder T06-
Loggingumbau. Keine neuen Abhängigkeiten, keine echten Scraper-/Browser-/
Anwendungsstarts, keine SQL-/DDL-Ausführung aus der Strukturvorlage.

## Tests

- Erste gezielte datenbankfreie Javaauswahl erfolgreich.
- DOM-Suite: 257 bestanden, 0 Fehler, 0 übersprungen.
- Vollständige Java-Regression vom 16.11.2026: 1461 Fälle, davon 1459 bestanden,
  0 Fehler, 2 bestehende M01-/M02-Commit-Opt-ins übersprungen. Kein neuer Skip.
- Der erste Gesamtlauf scheiterte an einer zu breiten neuen statischen SQL-Prüfung,
  die auch ON UPDATE als Schreibstatement wertete. Nur den Test auf Anweisungs-
  anfänge eingegrenzt; gezielte Nachprüfung und zweiter Gesamtlauf erfolgreich.
- Die letzte Änderung der Monatsanker war noch nicht Teil dieses Gesamtlaufs.
  Deshalb am 21.11.2026 die betroffenen Templates/MVC-Verträge gezielt nachgeprüft:
  32 bestanden, 0 Fehler/übersprungen; 94 Manual-Schutztests ebenfalls bestanden.
  bootJar und getrenntes Manual-JAR offline erfolgreich gebaut. Kein Anwendungsstart.
- Vor jedem tatsächlichen Gradle-Aufruf identischer geprüfter Dry-Run, stets
  --offline --no-daemon. Keine erneute Gesamtsuite beim Wiederaufnehmen.
- JavaScript-Syntaxchecks erfolgreich. Historische Manual-JARs unverändert;
  neue Paketierung ausschließlich unter .gradle/t04-closeout-20261116.
- Abschließende rein lesende Testschemaabnahme am 21.11.2026 bestanden: elf
  InnoDB-Tabellen, 78 Stammdaten, bestehende Geschäftszeilen unverändert,
  Ereignistabelle leer. Vollfingerabdruck identisch mit der bestätigten Referenz:
  77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f.
  Altdatenhash 3b629c3faeac198c4d83cf8df033257724a289eca71b46d17d344fc5d523d0b9
  und Altschemahash 9ca0a1402263a0830ed9df7ffbd50edbee22f50f02ad357bf91ed468c166a6e0
  ebenfalls identisch. Keine Bestandsnormalisierung, Schemaänderung oder Migration.
- Die Fortsetzung vom 16.11. scheiterte ausschließlich am Nutzungslimit der
  automatischen Ausführungsprüfung; der letzte tatsächliche Nachprüfungslauf
  wurde damals nicht gestartet. Am 21.11. nach erneutem identischem Dry-Run
  erfolgreich ausgeführt. Keine Umgehung und keine erfundene frühere Ausführung.
- git diff --check erfolgreich, nichts gestagt oder committed.

## Manuelle Nachprüfung

Bisher keine neue echte Browserabnahme in diesem Auftrag. Vor späterer
Veröffentlichung die Einkaufsliste mit gleichen Monatsnamen unterschiedlicher
Jahre und die Jahresvorbelegung visuell prüfen. Das Dialog-/Replayverhalten ist
hier durch jsdom und die bestehende MVC-Regression abgesichert, nicht durch eine
neue Browsermessung. Layout/Design wurde nicht neu gestaltet.

Git-Abschluss nicht beauftragt; keine Staging-/Commit-/Push-/Mergeaktionen.

## T04 – Lesende Browserabnahme und lokaler Git-Abschluss (21.11.2026)

T04_SCOPED_BROWSER_ACCEPTED. Freigegebener Kandidat
.gradle/t04-closeout-20261116/mangalib-manual-test.jar, SHA-256
4885530467715bcabae40b3bedd5e5b740af6c711e76f3abe06dfa14f9d3dc9a.
ValidateOnly bestanden; eigene PID 29212 mit Profil manual-test und ausschließlich
127.0.0.1:18080, Banner und ausschließlich synthetische Scraper bestätigt.

Im vorhandenen Codex In-app Browser bestanden: Navigation November 2026,
Vorbelegung Monat 11/Jahr 2026, ausdrückliche Auswahl des Vorjahrs 2025,
undatierte Gruppe mit leeren Monat-/Jahresfeldern, Abbrechen/Wiederöffnen sowie
Reihe/Einzelband-Moduswechsel mit ungespeichertem Titel/Preis. Nach Abbruch kehren
keine alten Werte zurück, Ladeanzeige verborgen. Keine erfassten Konsolenfehler.
Keine Speicherung, Kauf-/Löschaktion, Autofill oder externe Navigation.

Nachweisgrenze: Der vorhandene Bestand enthält nur eine sichtbare datierte
Monatsgruppe. Zwei gleichnamige Gruppen verschiedener Jahre wurden daher nicht
real dargestellt; diese Kollision ist durch ShoppingMonthRegressionTest abgedeckt.
Keine Fixtures allein für diesen Screenshot angelegt. Die bestehenden gedrängten
Aktionsspalten einzelner Einkaufszeilen bleiben ein möglicher T05-Layoutprüfpunkt;
kein neuer Funktionsfehler der geprüften T04-Aktionen festgestellt.

Vollständige READ-ONLY-Snapshots vorher/nachher identisch:
f19146da1fbc0c7840242fee81ec12d87e6a8d90720ac5927298bf1596c42c33.
17 Reihen/Details, 44 Bände, 11 Einkäufe/Details, vier Leseereignisse,
17 Stammdaten und sämtliche Schema-Metadaten unverändert. Eigene Sitzung per
Ctrl+C beendet; Tomcat/JPA-Shutdown bestätigt, PID beendet und Port frei.
Private Screenshots/JSON-Nachweise unter .gradle/t04-browser-20261121 erhalten
und ignoriert. Keine Verbindung zu mangalib, keine Migration/Schemaänderung.

Die zuvor offene visuelle Checkliste ist im beschriebenen Umfang abgearbeitet.
Frühere Hinweise „keine Browserprüfung/Git-Abschluss nicht beauftragt“ sind
historische Stände. Lokaler T04-Commit jetzt beauftragt; kein Push oder Merge.
Keine erneuten Tests bei ausschließlich dokumentarischem Abschluss.
