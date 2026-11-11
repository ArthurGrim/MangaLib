# MangaLib – Entwicklungsroadmap

> **Status:** Planungsgrundlage, Stand 11.10.2026
> **Geltungsbereich:** MangaLib (Java 21, Spring Boot 4.1.1, Spring Data JPA, MySQL, Thymeleaf, JavaScript, Gradle).
> **Verbindlichkeit:** Die Kategorien **Muss / Soll / Kann** geben die fachlichen Prioritäten des Projekteigentümers wieder. Technische Voraussetzungen dürfen in der Umsetzungsreihenfolge vorgezogen werden; eine fachliche Umpriorisierung erfolgt nur nach Rücksprache.

## 1. Grundsätze und Definition of Done

### Technologieoffene Weiterentwicklung

- Die heutige technische Implementierung ist keine dauerhafte Vorgabe. Insbesondere Backend-Abläufe für Hinzufügen, Bearbeiten und Importieren von Reihen/Bänden sowie Autofill-/Scraper-Architektur dürfen durch effizientere, wartbarere, robustere und flexiblere Lösungen ersetzt werden.
- Kernfunktionen und sämtliche vorhandenen Daten bleiben erhalten; Verbesserungen der Funktionalität sind ausdrücklich erwünscht. Bei größeren Änderungen zunächst technische Alternativen, Vor-/Nachteile und Auswirkungen bewerten und vor Umsetzung Rücksprache halten. Kein allgemeines Refactoring im aktuellen Sicherheitsauftrag.

### UI-/UX-Modernisierung

- Die ursprüngliche optische Orientierung an Manga-Passion ist keine dauerhafte Designvorgabe. Benutzerfreundlichkeit, Performance, Bedienbarkeit, Navigation und visuelle Gestaltung dürfen modernisiert werden; MangaLib soll wiedererkennbar bleiben, einzelne heutige Layoutdetails, CSS-Klassen oder Interaktionen sind nicht unveränderlich.
- Vor größeren UI-Änderungen geeignete Vorschläge vorstellen. Künftige UI-Überarbeitungen berücksichtigen unterschiedliche Bildschirmgrößen; spätestens für Smartphone-Unterstützung ist ein konsequentes Responsive-Konzept nötig, unabhängig von der technischen LAN-Freigabe. Keine UI-/Responsive-Modernisierung im aktuellen Sicherheitsauftrag.

### Spring Boot und Dependency-Modernisierung

- [x] Spring Boot von 3.5.8 auf 3.5.16 aktualisieren.
- [x] Technische Kompatibilität unter 3.5.16 durch vorhandene Tests und `assemble` prüfen.
- [x] Vorhandene Integrationstests gegen isolierte MySQL-Testdatenbank durchführen.
- [x] Migrationsanalyse von Spring Boot 3.5 auf 4.1.x erstellen.
- [x] Abhängigkeiten und Konfigurationsänderungen für den Migrationsplan statisch prüfen.
- [x] 3.5.16-Baseline um Repository-/Rendering-/Integritätsprüfungen erweitern: 91 erfolgreiche Tests.
- [x] Zwischenstand auf Spring Boot 4.0.8 validieren: 91 erfolgreiche Tests, Daten-/Schema-Vergleich und `assemble`.
- [x] Spring-Boot-4.1.1-Zwischenstand technisch validieren: 91 Tests, unveränderte MVC-/JSON-Verträge, Daten-/Schema-Vergleich und `assemble`; keine produktive Freigabe.
- [ ] Regressionstests für zentrale MangaLib-Funktionen ausführen.
- [ ] Regelmäßige Überprüfung von Framework- und Dependency-Updates etablieren.

Validierungsstand des Updates: Codex hat am 10.10.2026 eigenständig
`.\gradlew.bat --no-daemon clean test` unter Spring Boot 3.5.16 erfolgreich
ausgeführt: **34 Tests bestanden, 0 Fehler, 0 fehlgeschlagene Tests**.
Davon entfallen 31 auf `TestDatabaseGuardTest`, zwei auf die Kontextprüfungen
und einer auf den transaktionalen `ServiceTest`. Die XML-Berichte unter
`build/test-results/test/` bestätigen diese Verteilung. `assemble` war bereits
im Update-Auftrag erfolgreich.

Die Datenbankisolierung wurde für diesen Lauf erfolgreich bestätigt:
Testprofil, `GuardedTestDataSource`, ausschließlich `mangalib_test` und
`mangalib_test_user`; kein produktiver Datenbankzugriff. Der Service-Test
verwendet Rollback, die Stammdaten werden nur gelesen. Migrationen,
Schemaänderungen und Selenium-/Scraping-Tests wurden nicht ausgeführt.
Die eingeschränkten MySQL-Serverrechte bleiben eine separat administrativ
zu bestätigende Schutzgrenze; die Tests ersetzen keine Rechteprüfung.
Java 21, Gradle 8.14.3 und die Testschutzmechanismen bleiben unverändert.

**Noch offen:** fachliche Regressionstests für Bearbeiten, Einkauf/Kaufübernahme,
Lesestatus, Preise, Statistiken und die Oberfläche. Die 34 Tests sind keine
vollständige fachliche oder Spring-Boot-4-Kompatibilitätsabnahme.
Der konkrete Plan einschließlich zusätzlicher Tests und Freigabekriterien steht
in [BOOT4_MIGRATION.md](BOOT4_MIGRATION.md); recherchiertes Ziel ist Spring Boot
4.1.1 (Stand 10.10.2026). Nach dem validierten Zwischenstand **4.0.8** ist jetzt auch **4.1.1 technisch validiert**: Codex führte eigenständig zunächst 44 datenbankfreie Schutztests, anschließend 47 geschützte Integrations-/Regressionstests und schließlich `clean test` mit **91 erfolgreichen Tests, 0 Fehlern und 0 übersprungenen Fällen** aus. Die 29 MVC-/JSON-Verträge blieben unverändert. `assemble --dry-run` und `assemble` erfolgreich. Der frische 4.0.8-Fingerabdruck und die Abschlussmessung unter 4.1.1 bestätigen identische Daten in allen zehn Tabellen und identische Schema-Metadaten; 78 Stammdaten erhalten, neue Testdaten zurückgerollt. Ausschließlich `mangalib_test` / `mangalib_test_user`, keine Migrationen, Scraper oder produktiven Starts. Befülltes Lazy-Rendering außerhalb einer Testtransaktion, weitere fachliche Abnahme sowie bekannte Temporal-/Selenium-Risiken bleiben offen. Tatsächlich aufgelöste Versionen, Prüfungen und Grenzen: [BOOT4_MIGRATION.md, Abschnitt 13](BOOT4_MIGRATION.md#13-tatsächlicher-fortschritt-validierter-411-zwischenstand). Java 21, Gradle 8.14.3 und Testschutz bleiben erhalten. Keine produktive Freigabe oder Bereitstellung.

Wichtig:
Datenbankstruktur und bestehende Bestandsdaten müssen während
aller Updates vollständig erhalten bleiben.

### P1 – Anwendungssicherheit

- [ ] Sämtliche Datenbankzugriffe auf SQL-Injection-Schwachstellen prüfen.
  - Native SQL-Abfragen und dynamisch erzeugte Queries untersuchen.
  - Ausschließlich parametrisierte Abfragen und Prepared Statements verwenden.
  - Keine ungeprüfte Verkettung von Benutzereingaben in SQL-Statements.
  - Dynamische Sortierfelder und Spaltennamen über Allowlisten absichern.
  - Automatisierte Sicherheitstests für kritische Eingaben ergänzen.

- [ ] XSS-Schwachstellen im Frontend beseitigen.
  - Unsichere innerHTML-Verwendungen überprüfen und ersetzen.
  - Benutzereingaben sicher ausgeben.
  - Externe URLs und Bild-URLs validieren.
  - Inline-Eventhandler nach Möglichkeit entfernen.

- [ ] Sämtliche API-Endpunkte auf serverseitige Eingabevalidierung prüfen.

- [ ] CSRF-Schutz und Zugriffssicherheit abhängig vom
      vorgesehenen Betriebsmodell überprüfen.

- [ ] Fehlermeldungen und Logging auf die Offenlegung
      sensibler Informationen untersuchen.

- [ ] Automatisierte Sicherheitstests dauerhaft in die
      Test-Suite integrieren.


- **Vorhandene Daten erhalten:** Schemaänderungen müssen mit allen bestehenden produktiven MangaLib-Datensätzen kompatibel sein. Bestehende Reihen, Bände, Einkaufspositionen, Preise, Lesestatus, Verknüpfungen und Metadaten dürfen nicht verloren gehen oder stillschweigend verändert werden.
- **Migration statt Neuanlage:** Änderungen des produktiven Datenmodells erfolgen durch versionierte, nachvollziehbare Vorwärtsmigrationen (beispielsweise Flyway oder Liquibase nach gesonderter Entscheidung). Ein frisches Schema allein ist kein Ersatz für eine Migration bestehender Daten.
- **Expand → Migrate → Contract:** Wo erforderlich, zunächst neue Spalten/Tabellen ergänzen, Altdaten überführen und validieren, Anwendung anpassen und alte Strukturen erst nach expliziter Freigabe entfernen. Bei neuen Pflichtfeldern zunächst mit bestehenden Daten kompatible Defaults/Nullable-Phasen einplanen.
- **Sicherung und Rückweg:** Vor jeder produktiven Migration ein aktuelles MySQL-Backup einschließlich Schema und Daten, einen geprüften Wiederherstellungsweg und einen Migrationsplan vorsehen. Destruktive Migrationen und Löschungen benötigen explizite Zustimmung.
- **Realer Altbestand als Testfall:** Migrationen zunächst auf einer *isolierten Kopie* des aktuellen Datenbestands testen; bei sensiblen Daten möglichst anonymisieren. Vorher/nachher Zeilenzahlen, Fremdschlüssel, Summen, Lesestatus und relevante Stichproben vergleichen. Keine Tests, Scraper oder Experimente gegen die produktive Datenbank.
- **Verhalten und Daten erhalten:** Bestehende Kernfunktionen und Bestandsdaten sichern. Technische und UI-Modernisierung nach den obigen Regeln ermöglichen; in begrenzten Aufgaben ohne ausdrücklichen Modernisierungsauftrag die vorhandene Oberfläche beibehalten.
- **Guter Code:** Lesbare, wartbare Lösungen; komplexe Entscheidungen, Geschäftsregeln, SQL-Abfragen und nicht offensichtliche Abläufe mit sinnvollen Kommentaren/Javadoc erklären, ohne jede triviale Zeile zu kommentieren.
- **Sicher ändern:** Änderungen in kleinen, prüfbaren Aufgaben und eigenen Git-Branches/Worktrees. Tests, Builds und Migrationen nur mit überprüfter Testkonfiguration. Keine automatische Übernahme in `main`, kein Produktiv-Deployment ohne Freigabe.
- **Definition of Done je Feature:** Implementierung + passende Tests + geprüfte Auswirkungen auf Altdaten/Schema + Dokumentation + konsistentes UI + kurze Beschreibung der manuellen Nachprüfung.

## 2. Muss – verbindliche Funktionsanforderungen

### M01 – Einkaufslisteneinträge nachträglich bearbeiten
- **Status:** Implementiert und fachlich abgenommen. Die Nachweise zu Bearbeitung, Kaufkonflikten und schmaler Ansicht stehen in `M01_IMPLEMENTATION_PLAN.md`.
- In der Einkaufsliste bereits angezeigte/eingetragene Titel bearbeiten können.
- Insbesondere Preis und Bandanzahl korrigieren; auch weitere vorhandene sinnvolle Felder einbeziehen.
- Bereits vorhandene Daten und gegebenenfalls verknüpfte Bände konsistent aktualisieren; fachliche Auswirkungen vor Implementierung prüfen.
- **Akzeptanz:** Eintrag öffnen, Werte ändern, speichern und nach Neuladen korrekt wiederfinden; ungültige Eingaben werden verständlich abgewiesen.

### M02 – Beim Hinzufügen gezielt Reihe oder Einzelband von Manga-Passion abrufen
- Über dem Index-Feld eine Auswahl **„Reihe“ / „Einzelband“** anbieten (alternative UX nach Rücksprache möglich).
- Reihenindex verwendet bisherigen Reihenimport; Einzelbandindex verwendet die direkte Bandseite, z. B. `https://www.manga-passion.de/volumes/32290`.
- Bei „Einzelband“ Bandanzahl automatisch auf **1** setzen und nicht editierbar machen.
- Beim Einzelband möglichst Titel, Preis, Cover und **Veröffentlichungsdatum** auslesen und übernehmen.
- Zugehörigkeit zur Reihe und Verhalten bei schon vorhandener Reihe/Bandnummer festlegen; keine unbeabsichtigten Duplikate.
- Einzelbandbestand darf bei einer späteren Nummer beginnen und Lücken enthalten. Bandanzahl zählt vorhandene Bände. Fehlende Nummern erzeugen keine Platzhalter und können später gezielt ergänzt werden. Automatische Mengenerweiterungen müssen lückenhafte Bestände bis zu einem expliziten Zuordnungspfad kontrolliert ablehnen.
- **Umsetzungsstand:** Einzelbandvorschau und transaktionale Übernahme mit ausdrücklich gewählter Zielreihe oder Neuanlage sind implementiert. Das Veröffentlichungsdatum erscheint in der Vorschau, da das Bandmodell dafür kein Feld besitzt. Geschützte MySQL-Tests bestätigten Rollback und Datenerhalt sowie HTTP 200/409 bei nachgewiesener Überlappung zweier Importe desselben Bandes. Im isolierten Browser wurden synthetischer Reihenimport mit Speicherung und Reload, Einzelband-Neuanlage, Duplikatmeldung und Ergänzung von Band 2 zu Band 31 bestätigt. Ein echter Manga-Passion-Abruf war nicht Teil dieser Abnahme.
- **Akzeptanz:** Beide Importarten sind klar unterscheidbar, liefern korrekt normalisierte Formulardaten und können gespeichert werden.

### M03 – „Days Read“ berechnen
- Globaler Standardwert für die geschätzte Lesezeit: **45 Minuten pro Standardband** (Eigentümerentscheidung vom 10.11.2026). Sammelbände entsprechend gewichten, z. B. 3-in-1 = 135 Minuten. Die spätere Anpassung des globalen Werts erfolgt über K05 – Einstellungen.
- Anzahl gelesener Bände in eine geschätzte Lesezeit umrechnen; Anzeige in Tagen/Stunden und konsistent mit weiteren Lesestatistiken.
- Bekannte Rereads können vorläufig per Checkbox dazugerechnet werden. Die endgültige Reread-Regel wird in S03 festgelegt.
- **Implementiert und gezielt abgenommen (10.11.2026):** Statistikübersicht zeigt die geschätzte Lesezeit auf Basis gelesener Einzelbände und ihres Sammelbandfaktors. Bekannte Rereads können ausdrücklich per Checkbox zusätzlich gezählt werden; standardmäßig ist sie ausgeschaltet. Fehlende Faktoren werden sichtbar ausgelassen, unbekannte oder negative Rereads liefern keine zusätzlichen Lektüren. MySQL-Projektion, Rollback/Datenerhalt und drei echte Template-Endpunkte bestanden. Der lesende isolierte Browser bestätigte Anzeige und Umschaltung (9 Stunden ohne, 24 Stunden mit bekannten Rereads im synthetischen Bestand). Globale Einstellbarkeit bleibt K05.
- **Akzeptanz:** Ergebnis ist anhand einer dokumentierten Formel nachvollziehbar und ändert sich korrekt mit Lesezeit und Lesestatus.

### M04 – Bestehende Statistiken reparieren und ausbauen
- **Abschluss des umgesetzten Umfangs (11.11.2026):** Centgenaue Monatswerte, wiederholte Diagrammbedienung, sechs Verteilungen, Verlags-/Formatranglisten, Spezialbandanteil und Reihen-Startjahr technisch und visuell abgenommen. Abschließende gemeinsame Regression: 14 Java-Tests und 241 DOM-Tests bestanden, keine Fehler/übersprungenen Fälle. Geschützte Integrationsfixtures zurückgerollt und Snapshot-Erhaltung bestätigt. Die folgenden ausstehenden Abnahmehinweise sind historische Zwischenstände.
- **Offener Restumfang:** Gelesene Bände pro Monat benötigt verlässliche Lesedaten aus S03. Dieser Teil bleibt bis zur S03-Umsetzung offen; die aktuelle Monatsachse bezieht sich auf Erscheinungsdaten gekaufter Einkaufspositionen.
- **Visuelle Abnahme (11.11.2026):** Desktopansicht, sechs Kreisdiagramme, vier ergänzende Übersichten und wiederholte Monatsdiagrammwechsel im isolierten Browser bestätigt. Ein bei 390 Pixeln gefundener Seitenüberlauf wurde korrigiert und erneut geprüft; Achsendiagramme sind lokal horizontal scrollbar. Keine erfassten Konsolenfehler. Monatliche Lesestatistik bleibt als S03-Abhängigkeit offen.
- **Erster Implementierungsblock (11.11.2026):** Monatsbeträge verwenden BigDecimal statt ganzzahliger Kürzung. Wiederholte Jahres-/Typwechsel, genau ein initialer Monatsabruf und Vorrang der neuesten Antwort sind datenbankfrei abgesichert. Deutsche Geldanzeige und negative Beträge berücksichtigt. Die bestehende Monatsstatistik ist nach Erscheinungsmonat gekaufter Einkaufspositionen beschriftet. Echte MySQL-/Browserabnahme und weitere M04-Auswertungen stehen aus.
- Fehler bei Jahreswechsel, Centbeträgen, Diagramm-Interaktionen und vorhandenen Statistikansichten beheben.
- **Pie Charts – erste Reihe:** Typ, Format, Status.
- **Pie Charts – zweite Reihe:** Herkunft, gelesen/ungelesen, vergriffen/nicht vergriffen.
- **Verteilungen implementiert (11.11.2026):** Sechs responsive Kreisdiagramme mit textuellen Anzahlen und Prozentanteilen. Gruppierte Repository-Abfragen und Endpunkt /statistik/verteilungen; nullable/leere Angaben werden als Unbekannt zusammengefasst. Lesestatus zählt Bände, die übrigen fünf Verteilungen Reihen. Datenbankfreie Service-/DOM-Prüfungen bestanden; reale Abfrage- und Browserabnahme stehen aus.
- **Weitere Auswertungen:** Verlage und Format in einer Darstellung ähnlich AniLists Studio-/Genre-Statistik; Days Read; Anteil an Spezialbänden; gelesene Bände pro Monat; Chart für Erscheinungsjahr und Herkunftsland.
- **Weitere Auswertungen implementiert (11.11.2026):** Verlags-/Formatranglisten, Reihen-Startjahr und Spezialbandanteil mit Textwerten und Balken; fehlende Werte als Unbekannt. Herkunftsverteilung und M03-Lesezeit sind bereits eingebunden. Geschützte MySQL-/Monatsvertrags-/Renderingprüfung bestand mit fünf Fällen einschließlich Jahreswechsel, Centbeträgen und Snapshotvergleich nach Rollback. Die visuelle Abnahme dieser Oberfläche steht aus. Monatliche Lesestatistik bleibt von S03 abhängig.
- Diagramme konsistent und als Teil von MangaLib gestalten; Verbesserungen der UI-/UX nach den obigen Modernisierungsregeln ermöglichen.
- Fachlich festlegen, welche Größen sich auf *Reihen* und welche auf *Bände* beziehen; unbekannte Angaben separat behandeln statt zu raten.
- Geplante Zählbasis gemäß abgestimmtem Vorgehen: Typ, Format, Status, Herkunft, Verlag und Vergriffenheit zählen Reihen; Lesestatus und Spezialbandanteil zählen vorhandene Einzelbände. Jahresauswertung verwendet zunächst das Reihen-Startjahr. Monatliche Lesestatistik benötigt die Lesedaten aus S03.
- **Akzeptanz:** Die Statistiken stimmen bei Testdatensätzen einschließlich Jahreswechsel, Centbeträgen, unbekannten Merkmalen und mehrfacher Diagrammbedienung.

### M05 – Feld „Ebay-Preis“ sinnvoll allgemeiner benennen
- Begriff unabhängig von einer bestimmten Verkaufsplattform gestalten.
- **Kandidaten:** „Preisabweichung“ (bei Differenz zum Normalpreis) oder „Preisanpassung“ (bei Auf-/Abschlag).
- Vor Umbenennung Bedeutung, Vorzeichen, Berechnung, Datenbankspalte und alle UI-Verwendungen untersuchen; zunächst **kein** unkontrolliertes Umbenennen von DB-Spalten.
- **Akzeptanz:** Fachlich eindeutiger Begriff, unveränderte Altwerte und korrekte Berechnung.

### M06 – Lesestatus beim Hinzufügen von Bänden korrekt erhalten
- **Technisch und manuell abgenommen, noch nicht nach `main` gemergt (15.10.2026):** Alle sieben freigegebenen UI-Szenarien A–G im Codex In-app Browser erfolgreich, jeweils mit erneutem Laden: vollständige/teilweise gelesene Reihe erweitern, individuelle Rereads erhalten, Kauf über Index und Titel-Fallback, gleichzeitige Titeländerung sowie anschließende Einzelbandmarkierung. Vier neue synthetische M06-Reihen (IDs 7–10), 14 Bände (11–24) und zwei gekaufte Einkaufspositionen (5/6) verbleiben bewusst in `mangalib_manual_test`. Sämtliche vorbestehenden Zeilen, 17 Stammdatensätze und Schema-Metadaten unverändert; keine Bereinigung. Ausschließlich geschützter manueller Launcher, `manual-test`, `127.0.0.1:18080`; eigene PID 90716 anschließend per Ctrl+C beendet, Port frei. Keine neuen Java-/DOM-Läufe; die 456 erfolgreichen automatisierten Tests bleiben der gesonderte technische Nachweis. Die folgenden offenen Abnahmehinweise beschreiben historische Zwischenstände; ihre Browserblockade wurde mit der vorhandenen Browserintegration überwunden. Details: MANUAL_TESTING.md, Abschnitt 17; T03/S03 und übrige Prioritäten unverändert.
- **Manuelle Abnahmevorbereitung, Browserzugriff blockiert (15.10.2026):** Aktuelles isoliertes M06-JAR nach Taskplanprüfung offline gebaut und mit `Start-ManualTest.ps1 -ValidateOnly` erfolgreich geprüft. Eigene Instanz PID 1632 ausschließlich als `manual-test` / `mangalib_manual_test` auf `127.0.0.1:18080` gestartet, danach kontrolliert per Ctrl+C beendet; Port wieder frei. Browserwerkzeug meldete `Computer Use app approval timed out`; daher **keine Browser-Schreibaktionen, keine neuen Testdaten und keine fachlichen Browserszenarien durchgeführt**. Rein lesende Vorher-/Nachher-Snapshots bestätigen unveränderte vorhandene Reihen/Bände/Einkäufe, Stammdaten und Schema. Die technische Integration mit 456 erfolgreichen Tests bleibt der letzte fachliche Automatennachweis; **M06 ist weiterhin nicht manuell abgenommen**. Vor Fortsetzung muss die Browser-/Computer-Use-Appfreigabe funktionieren; keine Ersatz-Schreibrequests oder SQL-Schreibabkürzungen. Details in MANUAL_TESTING.md, Abschnitt 16.
- **Geschützte Integration bestätigt (15.10.2026):** 34 neue transaktionale JPA-/MockMvc-Integrationsfälle mit echten Repositories, `flush()`/`clear()` und erneutem Laden bestanden. Vorab 44 datenbankfreie Guard-Fälle, danach 73 bestehende geschützte Regressionen einschließlich aller 29 MVC-/JSON-Verträge erfolgreich. Abschließend ungefiltertes `clean test`: **456 bestanden, 0 Fehler, 0 übersprungen** = bisherige 382 + 40 M06-Unitfälle + 34 neue Integrationsfälle. Unmittelbarer Vorher-/Nachher-Fingerabdruck identisch: `ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`; zehn Tabellen und 78 Stammdatensätze unverändert, Geschäftstabellen leer, keine verbliebenen Fixtures, Schema-/Index-/FK-Metadaten unverändert; AUTO_INCREMENT ausgenommen. Jeder neue Fall prüft zusätzlich den tatsächlichen Rollback außerhalb der Testtransaktion. Keine weiteren M06-Anwendungscodekorrekturen erforderlich. **Technisch bereit für die gesondert freizugebende manuelle Browserabnahme; M06 bleibt bis dahin nicht vollständig abgeschlossen.** T03/S03 und andere abgegrenzte Folgearbeiten unverändert; keine produktiven/manuellen DB-Zugriffe oder Anwendungsstarts. Befehle und Nachweisgrenzen im M06-Integrationsnachtrag von TESTING.md.
- **Implementierter Zwischenstand (15.10.2026), noch nicht vollständig abgenommen:** Erweiterungen bearbeiten ausschließlich neue Bände; alte IDs, Nummern, Beziehungen, Lesestatus, nullable Rereads und Bandmetadaten bleiben erhalten. Neue zusätzliche Bände starten ungelesen, ohne neue Checkbox oder Requestfelder. Reihenstatus wird anschließend über sämtliche tatsächlich zugehörigen Bände aggregiert; Reihen-Reread bleibt erhalten. Titel-/Reihenmetadaten können gleichzeitig geändert werden; mitgesendete Lese-/Reread-Werte sind bei Erweiterung kein Massenänderungsauftrag. Beide Kaufzuordnungen (Index/Titel) verwenden dieselbe Regel; Kaufneuanlage und normale Bearbeitung bleiben getrennt.
- **Freigegebene Bestandsgrenzen:** Ein eindeutig identifizierbarer Bestand mit Nummern 1..N darf durch N+1..Z bei Z>N ergänzt werden, auch bei abweichender deklarierter Anzahl. Keine Verwendung unsortierter Listenpositionen zur Identifikation alter Bände. Lücken, doppelte/fehlende IDs oder Nummern, widersprüchliche Beziehungen und eine vermeintliche Erweiterung ohne tatsächlich neue Bände werden vor Änderungen kontrolliert abgewiesen. Fehlende MangaDetails werden bei Erweiterung/Kaufzuordnung kontrolliert abgewiesen; keine historischen false/null-Werte erfunden, keine SQL-NULL-Normalisierung oder Bestandsreparatur.
- **Historischer datenbankfreier Prüfstand vor der Integrationsfreigabe:** 40 neue datenbankfreie M06-Fälle mit echten Controllern/Services und Repository-Mocks, 308 bestehende DB-freie Unit-/Guard-/Policy-Fälle und 17 bestehende Reihenbearbeitungs-Regressionsfälle bestanden, jeweils 0 Fehler/Skipped. Damals keine Datenbank-, Browser- oder Scraperausführung; Vollsuite und geschützte Integration waren noch offen. Der obige Nachtrag ergänzt den inzwischen erreichten Integrationsnachweis; die manuelle Browserabnahme bleibt ausstehend. Historische 164 DOM-Fälle nicht erneut ausgeführt. Details: TESTING.md, M06-Zwischenstand und Integrationsnachtrag.
- Beim Hinzufügen eines neuen Bandes dürfen vorhandene Bände **nicht** erneut auf ungelesen gesetzt werden.
- Ein zusätzlicher Band startet im freigegebenen M06-Umfang immer ungelesen; eine neue Initialstatus-Auswahl wird nicht eingeführt. Eine spätere Änderung erfolgt über die vorhandene Bandbearbeitung. Die bestehende ausdrückliche Gelesen-Auswahl bei vollständiger Reihenneuanlage bleibt unverändert.
- War die Reihe vollständig gelesen und kommt ein ungelesener Band hinzu, wird der **Reihenstatus** automatisch auf nicht vollständig gelesen/ungelesen gesetzt; bestehende Bandzustände bleiben unverändert.
- Keine pauschale Übertragung des Reihenlesestatus auf vorhandene Bände beim Ergänzen oder Kaufen.
- **Akzeptanz:** Tests für vollständig gelesene, teilweise gelesene und ungelesene Reihen sowie Hinzufügen über die Einkaufsliste.

## 3. Soll – wichtige Komfort- und Datenfunktionen

### S01 – Neue Verlage über die Oberfläche anlegen
- Neue Verlage direkt im UI verwalten, ohne Backend-Eingriff.
- Eingabevalidierung, Dublettenvermeidung und korrektes Aktualisieren vorhandener Auswahllisten.

### S02 – Coverübernahme aus der Einkaufsliste prüfen und ggf. korrigieren
- Prüfen, ob das durch Autofill geladene Bandcover beim Kauf tatsächlich in die Bibliothek übernommen wird.
- Bestehenden Unterschied zwischen Scraper-Feldnamen und Zielmodell bereinigen und mit Tests absichern.
- Keine vorhandenen Coverdaten durch leere Importfelder überschreiben.

### S03 – Lesedatum `readAt` und Rereads
- Beim Markieren eines Bandes als gelesen automatisch das Lese-/Abschlussdatum erfassen; bestehende Daten weiterhin korrekt interpretieren.
- Rereads fachlich definieren. Prüfen, ob eine separate Leseereignis-/Leseverlaufstabelle statt eines einzelnen Datums sinnvoller ist, insbesondere für monatliche Statistiken.
- Bestand ohne historisches Lesedatum nicht mit erfundenen Zeitstempeln auffüllen. Rückwirkende Datumsangaben optional ermöglichen.

## 4. Kann – spätere Erweiterungen

### K01 – Aktivitätenübersicht
- Chronologische Übersicht jüngster Aktionen ähnlich AniList; vorab Ereignisarten, Datenschutz und benötigte Historisierung festlegen.

### K02 – Favoriten
- Reihen als Favorit markieren und gezielt filtern/anzeigen.

### K03 – Mehrere Profile
- Datenzuordnung, Berechtigungen und Migrationsstrategie für bisherigen Einzelprofil-Bestand zuerst festlegen.
- Bei Umsetzung gemeinsam mit K04 konzipieren und umsetzen: echte Anmeldung mit Authentifizierung und Autorisierung sowie serverseitige Trennung aller Datenzugriffe nach Benutzerberechtigung sind verpflichtend. Eine bloße Profiltrennung im Frontend genügt nicht.

### K04 – Anmeldung
- Authentifizierung/Autorisierung passend zum späteren Betriebsmodell; nicht ohne Bedrohungs- und Berechtigungskonzept ergänzen.
- Für den aktuellen lokalen Einzelbenutzerbetrieb ist kein Login erforderlich; bei Umsetzung von K03 ist K04 verbindlicher Bestandteil derselben Umsetzung. Die Kann-Kategorie beider Erweiterungen bleibt unverändert.

### K05 – Einstellungen
- UI zur Verwaltung konfigurierbarer Optionen, insbesondere die globale geschätzte Lesezeit pro Standardband (M03, Standard 45 Minuten). Eine Änderung soll die Lesezeitschätzung einschließlich der Sammelbandfaktoren neu berechnen; später gegebenenfalls profilspezifisch.

### K06 – Browser-Integration für Manga-Passion (allerletzte optionale Erweiterung)
- Beim Besuch einer Manga-Passion-Reihen-/Bandseite langfristig die direkte Übergabe an MangaLib über eine Browser-Erweiterung oder vergleichbare geeignete Lösung ermöglichen; Firefox, optional Chrome berücksichtigen.
- Bevorzugt einen vorbereiteten Einkaufskorb-Eintrag mit Datenvorschau, Dublettenprüfung und ausdrücklicher Bestätigung anbieten. Sichere Kommunikation mit dem lokalen Backend ist zwingend; Berechtigungen und dedizierten Übergabevertrag vor Umsetzung bewerten, keine pauschale CORS-/CSRF-Ausnahme.
- Erst nach sämtlichen übrigen Projektzielen einschließlich optionalem Smartphone-/LAN-Zugriff (Abschnitt 10). Keine Implementierung im aktuellen Auftrag.

## 5. Technische Grundlagen aus der initialen Codex-Analyse

Die folgenden Punkte sind technische Voraussetzungen oder Qualitätsprobleme, **keine Änderung der fachlichen Muss-/Soll-/Kann-Kategorien**.

### T01 – Isolierte MySQL-Testumgebung und verlässliche Tests (zuerst)
- **Erledigt:** eigenes `test`-Profil und separate MySQL-Datenbank `mangalib_test` mit Testbenutzer `mangalib_test_user`; Passwort über `MANGALIB_TEST_DB_PASSWORD`, kein produktiver Fallback.
- **Erledigt:** automatischer Schutz vor Kontext-/JDBC-Zugriffen, datenbankfreie Schutzprüfungen und transaktionaler Integrationstest; 34 Tests durch Codex erfolgreich ausgeführt. Selenium-/Netzwerktests sind deaktiviert und zusätzlich per Tag ausgeschlossen. Sicherheitsregeln: [TESTING.md](TESTING.md).
- **Offen:** fachliche Regressionstests und zusätzliche Migrationsprüfungen gemäß [BOOT4_MIGRATION.md](BOOT4_MIGRATION.md). Testcontainers ist keine Voraussetzung für die bestehende funktionsfähige Testumgebung.
- Vor dem ersten `gradlew test`, `check`, `build` und `bootRun` Konfiguration und potentielle Schreibzugriffe prüfen.
- SQL-Bootstrap-Skript reparieren/validieren; Migrationen an echten Altbeständen testen.

### T02 – Sicherheit und Eingabevalidierung

**Aktueller Abschlussstand (02.11.2026): T02 technisch abgeschlossen für das freigegebene lokale Einzelbenutzermodell.** [Abschlussmatrix T02](T02_CLOSEOUT.md): Scraper-/Job-/Proxy-/Timeoutnachweise, Dependency-Korrekturen einschließlich Tomcat 11.0.27 und geschützte Regression erfolgreich (1016 Java-/206 DOMfälle). Codex bestätigte die echte 413-UI-/Reload-Behandlung ohne unmittelbar beobachtbaren HTTP-Status. Der Projekteigentümer bestätigte anschließend HTTP 415, verständliche Fehlermeldung, kein Replay, identischen Daten-/Schema-Snapshot, geordneten Shutdown und freien Port 18080. Keine erneute Ausführung im Abschlussauftrag. Die folgenden datierten Einträge sind historische Nachweise; alte PARTIAL/STOP-Ergebnisse und bekannten Aussagegrenzen bleiben erhalten. Lokaler Abschlusscommit freigegeben; kein Push/Merge. T03 nur vorbereitet.

- **Einmalige Joblisten-Diagnose 42273e86: zusätzliche Mitglieder nachgewiesen, weiter gesperrt (16.10.2026):** Ein ausdrücklich freigegebener synthetischer Baumversuch mit vorab dauerhaft verbrauchter Einmalfreigabe; alte Evidenz/STOP unverändert. Strenger Ist-/Soll-Vergleich erneut fehlgeschlagen: drei erwartete Rollen, aber sechs aktive Jobmitglieder bei erfolgreicher vollständiger Win32-Abfrage und übereinstimmenden PID-Lesern. Zusätzliche Prozessidentitäten und Diskrepanz zum konfigurierten ActiveProcessLimit=3 offen; kein belegter Layoutfehler und keine Abschwächung der Assertion. Eigene Jobgruppe nach Abbruch nachweislich leer, gehaltene Handles signalisiert, alle sechs beobachteten PIDs danach nicht vorhanden. Keine Wiederholung oder weiteren Szenarien. Nächste ausführende Prototypstufe/Produktintegration nicht freigabereif; zunächst Herkunft und Limit-Semantik klären. Vollständige Listen und Nachweisgrenzen im neuesten TESTING.md-Nachtrag. Keine DB-/Browser-/Scraper-/Git-Operationen; Prioritäten unverändert.
- **Windows-Job-Prototyp: Teilnachweis, Sicherheitsstopp (16.10.2026):** Separater Experimentbereich mit lokal vorhandenem JNA 5.17.0, ohne Produktivänderung/Dependency-Declaration. Atomare JOB_LIST-Zuordnung bei suspendiertem CreateProcessW, Resume und Startfehler-Cleanup im vorhandenen Ancestor-Jobkontext erfolgreich. Erster Worker-/Kind-/Enkel-Versuch scheiterte am Abgleich der Job-PID-Liste; gemäß Stoppregel keine weiteren Versuche. Diagnose-Ist-Liste fehlt, Ursache offen. Abbruch-Cleanup prüfte eigene Jobs leer und gehaltene Prozesshandles beendet; alle fünf bekannten synthetischen PIDs anschließend nicht mehr vorhanden. Kill-on-close mit laufenden Nachfahren, vollständige Fremdprozessisolation, inkompatible Nested-Limits und Supervisor-Crash noch nicht nachgewiesen. Produktionsintegration weiter gesperrt; zunächst gesondert freizugebende Diagnose und einzelner erneuter Baumnachweis. Quellen unter experiments/windows-job-prototype, Ergebnismatrix und Grenzen im neuesten TESTING.md-Nachtrag. Keine DB-/Browser-/Scraperausführung oder Git-Operationen; fachliche Prioritäten unverändert.
- **Variante B gewählt – Worker-Architektur, noch keine Implementierungsfreigabe (16.10.2026):** Isolierter Scraper-Worker mit kontrollierter Windows-Job-Object-Gruppe ist die beschlossene Richtung. Begrenzte datenbankfreie Win32-Proben bestätigen leeres Job Object/Kill-on-close und vorbereitbares JOB_LIST-Startattribut; noch kein Prozess zugeordnet oder gestartet. Empfehlung: kleiner direkter Win32-Adapter über explizit freizugebende JNA-Abhängigkeit, atomare Jobzuordnung bei CreateProcess, ein Worker pro Auftrag und Freigabe erst nach bestätigtem Gruppenende. Verschachtelte Jobs und Firefox-Sandbox benötigen vor Integration einen eigenen synthetischen Prozessnachweis bzw. später gesonderte Browserfreigabe. Details, Alternativen, IPC-/Timeout-/Paketierungsplan im neuesten Nachtrag von TESTING.md; keine neuen Abhängigkeiten oder Produktionsänderungen jetzt.
- **Scraper-Lifecycle: Analyse mit Architektur-Stopp (16.10.2026):** Keine zentrale Slotfreigabe auf Basis von `quit()`/`DriverService.isRunning()` implementiert: der bestehende Selenium-Pfad liefert keinen vollständigen Nachweis über verbliebene Firefox-Kindprozesse, insbesondere nach Konstruktor-/Cleanupfehlern. Gemäß Eigentümer-Stoppregel zunächst zwischen konservativer In-Prozess-Quarantäne mit möglicher dauerhafter Importsperre und eigener OS-seitig zugeordneter Scraper-Prozessgruppe entscheiden. Keine neuen Tests/Produktionsänderungen oder Live-Läufe in diesem Analyseschritt; Befunde, Alternativen, HTTP-/Timeout-Entwurf und Testplan im neuesten Nachtrag von TESTING.md. Harte Gesamtzeit-/Prozessgruppenbegrenzung nicht stillschweigend vorgezogen; SSRF separat.
- **CL+TE-Entscheidung des Eigentümers (16.10.2026):** Strikte rohe CL+TE-Ablehnung wird vorerst nicht als eigene Implementierungsaufgabe verfolgt. Tomcats Normalisierung und die Nachweisgrenze der 19-Fälle-HTTP-Abnahme bleiben für den aktuellen lokalen Betrieb dokumentiert; bei späterem Proxy-/LAN-Betrieb neu bewerten. Keine allgemeine Protokoll- oder T02-Vollfreigabe und keine Prioritätsänderung.
- **HTTP-/JSON-Größenbegrenzung datenbankfrei umgesetzt (16.10.2026), Integration offen:** Früher zentraler Servlet-Filter liest höchstens 32 MiB + 1 Byte, begrenzt JSON-Strukturen vor MVC-Binding und weist doppelte Schlüssel sowie nicht vorgesehene Formular-/Multipart-/Kompressionsbodies ab. Bestehende fachliche 10.000-Bände-/Reread-Grenzen, Altbestands- und M06-Regeln bleiben erhalten. **482 datenbankfreie Tests erfolgreich, davon 83 neue**, einschließlich großem 10.000-Bände-Import mit Service-Mock; keine DB-/Listener-/Browser-/Scraperausführung. Geschützte Vollintegration, tatsächliches Tomcat-Framing/chunked/Keep-alive und Browsernachprüfung sind gesondert freizugeben; noch kein pauschaler T02-Abschluss. Vorherige 547 Java-/198 DOM- und Browsernachweise bleiben historisch. Grenzen, Auswirkungen auf 400/413/415 und offene Last-/Scraperrisiken in TESTING.md. T03/T04/T06 und fachliche Prioritäten unverändert.
- **Abschließender Nachtrag (15.10.2026):** Manuelle Testinstanz PID 106732 nach eindeutiger Zuordnung kontrolliert per Ctrl+C beendet; Port 18080 anschließend frei. Vollständiger `clean test` ohne Testfilter erfolgreich: **382 bestanden, 0 Fehler, 0 übersprungene Fälle**, einschließlich aller **29 MVC-/JSON-Vertragstests**. Vorher-/Nachher-Fingerabdruck identisch: `ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`; zehn Tabellen und 78 Stammdatensätze unverändert, keine verbliebenen Fixtures, AUTO_INCREMENT ausgenommen. **164 DOM-Tests bereits erfolgreich**. Abschließendes Code-Review einschließlich unversionierter Dateien ohne neue blockierende Befunde; bisheriger T02-Implementierungsumfang technisch commitfähig. **T02 insgesamt bleibt offen:** Altbestandsverträglichkeit und SSRF-/Scraper-/Requestgrößengrenzen; T03, M06 und T06 bleiben eigenständige Folgearbeiten. Keine Änderung fachlicher Prioritäten und kein Commit/Merge erfolgt.
- **Historischer Zwischenstand vor dem erfolgreichen Clean-Abschluss (15.10.2026):** Nach den Reread-/Preis-/Titel-Layoutkorrekturen vollständige geschützte Suite mit `test`: **382 bestanden, 0 Fehler, 0 übersprungene Fälle**, einschließlich 44 Guard-, 61 Policy- und 29 MVC-/JSON-Vertragsfällen. Vorab 44 Guard-Tests und lesender Ausgangsfingerabdruck, danach gesonderter lesender Abschlussfingerabdruck: Daten und Schema identisch, zehn Tabellen/78 Stammdaten erhalten, keine Geschäftsdatensätze oder verbliebenen Fixtures; AUTO_INCREMENT ausgenommen. **164 DOM-Tests bestanden**. `clean test` scheiterte vor Testbeginn ausschließlich an der Windows-Sperre des laufenden manuellen JARs; kein Stop, kein Löschungsbehelf. Der damals noch ausstehende erfolgreiche `clean test` ist durch den obigen Nachtrag bestätigt. Historische Nachweise unten bleiben erhalten; Details und Berichte in TESTING.md.
- **Browsernachprüfung durch den Projekteigentümer bestätigt (15.10.2026):** Reread 3 nach Anlage/Neuladen erhalten, Einkaufspreise deutsch formatiert, lange Titel auch im schmalen Fenster umgebrochen, Schaltflächen sichtbar und bedienbar. Dies aktualisiert den früher offenen Nachtest der drei Korrekturen, ist keine neue Browserausführung durch Codex und keine pauschale Abnahme sämtlicher T02-Risiken. Altbestandsverträglichkeit, allgemeine Requestgrößen und Scraper-/SSRF-Grenzen bleiben offen; M06/T03/T06 unverändert separate Aufgaben.
- **Gezielte Frontend-Nachbesserung (11.10.2026), DB-frei geprüft:** Einheitliche status-/formatabhängige Inline-Fehler statt nativer Schreib-/Validierungsalerts; 403 ohne pauschale Sessiondiagnose, keine Fehlerbodys/automatischen Replays, gesperrter Button bei unbestätigter Speicherung. JSON-/Band-204-/Delete-200-/Kauf-417-Erfolge und Payloads erhalten. Obsolete Close-/MutationObserver-Initialisierungen entfernt, lokaler manueller Cover-Fallback ohne DB-/Entityänderung und sichtbare Band-ID-Beschriftung korrigiert. Ladeindikatoren dialogbezogen. **158 DOM-Fälle (65 neue), 297 DB-freie Java- und 85 manuelle Schutz-/Template-/Artefaktfälle bestanden**; historische 371-Vollsuite nicht erneut ausgeführt, keine DB-Verbindung oder neue Daten-/Schema-Messung. Sicherheitsfilter/Guards, Fachregeln und Prioritäten unverändert; M06/T03/T06 nicht vorgezogen. Erneute Browserabnahme bleibt offen, keine Anwendung gestartet. Details/Nachtestliste: [MANUAL_TESTING.md, Abschnitt 12](MANUAL_TESTING.md#12-t02-nachbesserung-dialogfehler-und-vier-ui-befunde-11102026).
- **Teilauftrag Schreibvalidierung umgesetzt und geschützt validiert (11.10.2026):** Kleine typgeprüfte Requestmodelle für Reihen-/Band-/Einkaufsanlage; IDs/JSON-Typen, Pflichtfelder, Titel ohne Zeichenverlust, Preise und optionale URLs vor Servicearbeit prüfen. Zentrale Bandanzahl **1–10.000** auch an Speicher-Schleifen, Reread **0–10.000** bei erhaltener null-/0-Semantik. Preisgrenzen sind technische Format-/Speichergrenzen, kein Verbot negativer Anpassungen oder neue Preislogik. Bestehende Referenzdienste weisen unbekannte IDs kontrolliert ab. Importmaps/Autofill-Daten bleiben untrusted; fehlende optionale Felder werden kontrolliert behandelt. HTTP(S)-URL-Syntax ohne Userinfo/DNS, URL-Vergleiche ohne `URL.equals()`, explizite skalare Sortier-Allowlist für beide `findAllSorted`-Methoden, neutrale 400-/500-Fehler ohne interne Exceptiontexte. Erfolgs- und bestehende Kauf-404-/417-/Band-204-Verträge beibehalten; keine HTTP-Exposition der Sortiermethoden.
- **Prüfstand dieses Teilauftrags:** Zunächst **297 DB-freie Java-Fälle** (44 Guard + 61 Policy + **192 neue** Unit-/Service-/MockMvc-Fälle), **85 manuelle Mock-/Schutz-/Artefaktfälle** und **93 unveränderte DOM-Fälle** bestanden. Nach ausdrücklicher Eigentümerfreigabe erneut 44 Guard-Tests, 3 Kontext-/Ausgangszustandstests, 71 geschützte Integrations-/Regressionstests und anschließend `clean test`: **371 bestanden, 0 Fehler, 0 übersprungene Fälle**, einschließlich der bisherigen 179 Tests und 29 unveränderten MVC-/JSON-Verträge. Abschließender Daten-/Schema-Fingerabdruck exakt identisch zum Ausgangszustand: zehn Tabellen und 78 Stammdaten erhalten, neue Fixtures zurückgerollt; AUTO_INCREMENT-Zähler bewusst ausgenommen. Ausschließlich `mangalib_test` / `mangalib_test_user`; Security-Filter und DB-Guards vollständig erhalten. Keine Abhängigkeiten ergänzt, keine produktiven/manuellen DB-Zugriffe, Migrationen, Schemaänderungen, Initialisierungen, Anwendungs-/Listener-/Browser-/Live-Scraperstarts. Grenzen, Analyse, Befehle und Nachweisgrenzen: [TESTING.md](TESTING.md#t02--schreibvalidierung-analyse-grenzen-und-datenbankfreie-prüfung).
- **Bewusst offen:** manuelle Formularabnahme und Verträglichkeit der Grenzen mit produktiven Altbeständen; die isolierte Testdatenbank hat keine bestehenden Geschäftsdaten. Keine Altwerte automatisch umgeschrieben. Allgemeine Requestgrößen, Scraper-Parallelität/Zeitlimits, Redirects/Unterressourcen und SSRF bleiben weitere T02-Arbeit. M06-Lesestatus, T03-Kaufatomarität/Idempotenz/Bandidentifikation/Restcent/Bandanzahlreduktion und T06-Logging nicht vorgezogen; Muss-/Soll-/Kann-Prioritäten unverändert.
- Stored-XSS-Risiko durch ungefiltertes `innerHTML` und Inline-Handler beseitigen.
- [x] **S01 umgesetzt (11.10.2026):** Dynamische Bandanzeige verwendet DOM-Knoten und Textausgabe statt HTML-Interpolation sowie direkt gebundene Klickereignisse. Absolute HTTP-/HTTPS-URLs ohne Zugangsdaten werden vor Navigation/Bildzuweisung geprüft; ungültige Cover verwenden den Platzhalter, ungültige Links bleiben deaktiviert. DOM-Struktur, CSS-Klassen und Titel-Sonderzeichen bleiben erhalten. **33 jsdom-DOM-Tests bestanden**, ohne Browser, Datenbank oder externe Requests. Reproduzierbarer separater Testlauf: `pnpm run test:dom`; Voraussetzungen und Grenzen stehen in `src/test/js/README.md`. jsdom ist ausschließlich eine freigegebene Testabhängigkeit. Die übrigen T02-Maßnahmen und die manuelle UI-Abnahme bleiben offen.
- S01-Validierung: Zuerst **44 datenbankfreie Guard-Tests**, anschließend `clean test` mit **91 erfolgreichen Tests**, einschließlich der unveränderten **29 MVC-/JSON-Vertragstests**; keine Fehler oder übersprungenen Fälle. Die geschützten Vorher-/Nachher-Messungen bestätigen identische Daten-/Schema-Fingerabdrücke aller zehn Tabellen und unveränderte 78 Stammdatensätze. Testdaten wurden zurückgerollt; ausschließlich `mangalib_test` / `mangalib_test_user`, keine Migrationen oder Scraperstarts. Die bekannten Deprecated-API-/HotSpot-CDS-Warnungen bleiben bestehen.
- Externe URLs/Importfelder validieren; geeignete DTOs und serverseitige Prüfungen etablieren.
- Erreichbarkeit der Anwendung klären; bei Netzbetrieb Zugriffsschutz priorisieren.

**Präzisierung durch den statischen Security-Audit vom 10.10.2026:**
Der interne, ausschließlich lokal aufbewahrte Sicherheitsbericht trennt belegte Codeprobleme von bedingten Risiken. Keine Tests, DB-Verbindungen oder Scraper wurden dafür ausgeführt; die 91 erfolgreichen Regressionstests sind keine Sicherheitsabnahme.

- **P1 – S01 behoben:** Der dokumentierte Stored-XSS-Datenpfad in der dynamischen Bandanzeige wurde mit sicherer DOM-Erzeugung, Textausgabe und Ereignisbindung geschlossen; DOM-Regressionstests ergänzen die bestehende Suite. Übergreifende URL-Regeln, Zugriffsschutz und weitere T02-Anforderungen bleiben eigenständige Aufgaben.
- **P1 – S02/S03:** Der ursprüngliche Befund fehlender Loopback-/Host-/Origin-/CSRF-Grenzen wird im freigegebenen lokalen Einzelbenutzerauftrag einschließlich Autofill-POST geschlossen (Umsetzungs-/Prüfstand unten). Anmeldung bleibt für diesen Betrieb nicht erforderlich; tatsächliche Listener-/Browserabnahme und späterer LAN-Betrieb sind gesondert zu prüfen.
- **P1/P2:** Bandanzahl vor Speicher-Schleifen begrenzen, Importdaten als untrusted Client-Eingabe behandeln, serverseitige Feld-/Referenz-/URL-Regeln vervollständigen. Erlaubte Bestandswerte und negative Preisänderungen nicht pauschal verwerfen.
- **P2 – teilweise umgesetzt:** `URL.equals()` im Bandupdate wurde im aktuellen T02-Teilauftrag durch syntaktischen Vergleich ersetzt; gemeinsame HTTP(S)-Syntax-/Userinfo-Prüfung ohne DNS ergänzt. Keine unbelegt restriktive Host-Allowlist. Allgemeines SSRF ist nicht bewiesen; Weiterleitungen und Browser-Unterressourcen sowie parallele Browserstarts benötigen weiterhin eigene Schutzgrenzen.
- **P2:** Keine internen Exception-Texte im Response und keine vollständigen Import-/Sammlungsdaten in Rohlogs; kontrollierte Fehlermeldungen und strukturierte, begrenzte Logs testen.
- **P2/P3:** Dokumentierter Tomcat-Stand 11.0.24 liegt in offiziellen betroffenen Sicherheitsbereichen; gepatchten konsistenten Stand gezielt validieren. HTTP/2 ist standardmäßig deaktiviert, ein konkreter Angriff nicht nachgewiesen. jsoup 1.17.2 fällt in einen Advisory-Bereich, dessen Safelist-Nutzungspfad hier nicht gefunden wurde. Gemischte Selenium-Versionen und offenbar ungenutzte jsoup-/WebDriverManager-Abhängigkeiten gezielt prüfen.
- **SQL-Prüfung:** Keine SQL-Injection in den bestehenden Repository-/HTTP-Pfaden gefunden; JPQL-Parameter sind gebunden und HTTP-Sortierung verwendet feste Comparators. Die derzeit nicht über HTTP verwendeten `findAllSorted`-Service-Methoden haben jetzt eine explizite Property-Allowlist mit datenbankfreien Positiv-/Negativtests. Keine SQL-Stringverkettung oder zusätzliche HTTP-Exposition eingeführt.

#### T02 / Security S02/S03 – Betriebsmodell und Zugriffsschutz: Analyse und Plan

**Ausgangsanalyse und Plan vom 11.10.2026; aktueller Umsetzungs-/Prüfstand weiter unten.** Die Security-Kennungen S02/S03 beziehen sich auf den internen lokalen Bericht, nicht auf die gleichnamigen fachlichen Soll-Tickets in Abschnitt 3. Der vertrauliche Bericht bleibt unverändert und unversioniert; hier stehen die öffentlich geeigneten Betriebsanforderungen und Umsetzungsschritte. Die folgenden Bestandsbefunde und das Inventar beschreiben den Stand vor der Absicherung; insbesondere Autofill verwendet inzwischen POST.

**Entscheidung des Projekteigentümers:** Primär ausschließlich lokale Einzelbenutzeranwendung auf dem eigenen Windows-PC (`localhost`/Loopback). Automatischer Start ohne VS Code ist ein verbindliches Abschlussziel (T07). Smartphone-/Heimnetzzugriff folgt optional nach den übrigen Projektzielen, der regulären Bereitstellung und dem optionalen GitHub-Release (Abschnitt 9); nur K06 folgt noch später (Abschnitt 10). Kein öffentlicher Internetzugriff, keine Router-Portfreigaben und keine zusätzliche Serverinfrastruktur. Heute keine LAN-Anmeldung, Profile oder Proxy-Infrastruktur vorwegnehmen; die spätere Erweiterung darf nur bewusst und mit eigenen Schutzregeln aktiviert werden.

**Repository-Stand vor Umsetzung und ursprüngliche Nachweisgrenzen:**

- **Belegt:** `src/main/resources/application.properties:1–11` enthält weder `server.address` noch `server.port`, TLS- oder Management-Port-Einstellungen. Der unveränderte Boot-Standard ist HTTP **8080**; Tomcat bindet ohne abweichende Einstellung an alle lokalen Adressen. Damit ist LAN-Erreichbarkeit konfigurationsabhängig möglich. Welche IPv4-/IPv6-Sockets tatsächlich offen sind, externe `SERVER_*`-/JVM-/Startparameter, Firewall und laufende Instanzen wurden nicht gemessen. Die Dokumentation `readme` nennt `http://localhost:8080`, erzwingt diese Begrenzung jedoch nicht. Quellen: [Boot-Serverproperties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html), [Tomcat-Bind-Adresse](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html).
- **Belegt:** MySQL wird laut Konfiguration über `localhost:3306` angesprochen; das ist eine ausgehende DB-Verbindung und keine Beschränkung des HTTP-Servers. MySQL-Listener und Serverrechte bleiben administrativ zu prüfen, ohne in diesem Auftrag eine Verbindung aufzubauen.
- **Belegt:** `build.gradle.kts:24–37` enthält Actuator, aber keinen Security-Starter; unter `src/main/java` fehlen eigene Security-/Host-/Origin-/CSRF-Filter und CORS-Freigaben. Actuator nutzt ohne Override denselben HTTP-Port, standardmäßig nur Health-Exposition; offene `/env`-/Dump-Endpunkte sind damit nicht nachgewiesen. [Actuator-Dokumentation](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
- **Bestehender Schutz:** S01 verwendet sichere DOM-Ausgabe; Thymeleaf-Textausgabe, JSON-Content-Type-Prüfungen an einigen Schreibendpunkten und fehlende CORS-Freigaben begrenzen einzelne Angriffspfade. Der TestDatabaseGuard schützt ausschließlich Tests, nicht den normalen Anwendungsbetrieb. Keine dieser Grenzen ersetzt eine Zugriffskontrolle. DevTools sind `developmentOnly`; zusätzliche Entwicklungslistener durch externe Einstellungen bleiben ungeprüft und gehören nicht in das Betriebspaket. [Boot-Paketierung](https://docs.spring.io/spring-boot/gradle-plugin/packaging.html#packaging-executable.configuring.including-development-only-dependencies).
- **Belegt im Code, Auswirkung bedingt:** Jeder Client, der die Anwendung erreicht, wird ohne Anwendungsauthentifizierung verarbeitet. Bei erlaubter LAN-Verbindung wären Lesen, Schreiben und Scraperaktionen möglich. Ein erfolgreicher fremder Browserangriff, DNS-Rebinding oder eine Internetexposition wurde nicht praktisch nachgewiesen. JSON-Preflight und Browserbeschränkungen lokaler Netzwerke sind keine verlässliche Anwendungsschutzgrenze. **Loopback allein schützt nicht vollständig vor browserbasierten lokalen Angriffen.** Auch andere lokale Programme/Windows-Benutzer bleiben ohne Anmeldung innerhalb der Vertrauensgrenze.

**HTTP-Inventar (alle 21 expliziten Controller-Mappings):** Java-Fundstellen beziehen sich auf `src/main/java/de/mangalib/`; JavaScript auf `src/main/resources/static/js/`. Lesende GETs erzeugen teilweise Logs oder bereiten Anzeigewerte auf, ohne im untersuchten Pfad Datenbankänderungen anzustoßen.

| Art | Bestehende Endpunkte | Code / Frontend und relevante Wirkung |
|---|---|---|
| HTML lesen / Navigation | GET `/home`, `/resetFilters`, `/einkaufsliste`; GET `/statistik/st_overview`, `/statistik/st_status`, `/statistik/st_verlag`, `/statistik/st_typ`, `/statistik/st_format` | `controller/MainController.java:70,213`, `EinkaufslisteController.java:65`, `StatistikController.java:28–66`; Templates/GET-Formulare. Reset leitet nur weiter, kein Bestandsreset. |
| JSON lesen | GET `/getMangaReiheData/{id}`, `/mangaReihe/{mangaReiheId}/bands`, `/getBand/{bandId}`, `/statistik/bandKategorien`, `/statistik/monatsdaten` | `MainController.java:331,388,418`, `StatistikController.java:72,89`; `home.js:136`, `popup_edit_reihe.js:93,392,412`, `popup_edit_baende.js:16`, `statistik.js:5,113`. Zugriff auf den gemeinsamen Bestand, keine Eigentümerprüfung. |
| Bibliothek schreiben | POST `/addMangaReihe`, `/editBand` | `MainController.java:229,449`; `popup_add_reihe.js:267`, `popup_edit_reihe.js:338`, `popup_edit_baende.js:111`. Anlegen/Ändern einschließlich Beziehungen und abgeleiteter Werte. |
| Einkauf schreiben | POST `/addToEinkaufsliste`, `/buyItemUpdate`, `/buyItemCreate`; DELETE `/items/{id}` | `EinkaufslisteController.java:142,213,274,202`; `einkaufsliste.js:291,435,452,20`. Kauf verändert Einkauf und Bibliothek; Löschen verändert Bestand. Atomarität/Wiederholung bleibt T03. |
| Externe, ressourcenintensive Aktionen | POST `/scrape`; **GET `/autofillBandData/{bandIndex}`** | `MainController.java:292,532` → `MangaScraper.java:22` bzw. `BandService.java:550` → `BandScraper.java:24`; `popup_add_reihe.js:57`, `popup_edit_reihe.js:178`, `einkaufsliste.js:348`, `popup_edit_baende.js:173`. Startet jeweils headless Firefox und externe Navigation, keine Bestandsanlage im Autofill selbst. |

Zusätzlich sind statische Assets, Boots Fehlerverarbeitung und Actuator zu berücksichtigen. MVC behandelt `@GetMapping` auch für HEAD; daher gehört HEAD beim Band-Autofill ausdrücklich zur späteren Negativprüfung. Dies ist aus Mapping und Frameworkverhalten abgeleitet, nicht durch einen echten Scraperaufruf geprüft. OPTIONS darf ebenfalls keine Aktion starten. [Spring-MVC-Methodenverhalten](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#ann-requestmapping-head-options).

**Freigegebenes Zielkonzept für den lokalen Modus:**

1. **Loopback und eindeutige Adresse:** Explizite IPv4-Loopback-Bindung und fester, konfigurierbarer Port; empfohlener Einstieg `http://localhost:8080/home`, unterstützte Alternative `http://127.0.0.1:8080/home`. IPv6 `::1` nicht pauschal als aktiv annehmen: Bindung und Namensauflösung testen, weitere Connectoren nur bei belegtem Bedarf. Keine Wildcard-Bindung im lokalen Betriebsmodus. Overrides, Portkonflikte und Management-Listener bei Start prüfen; bei widersprüchlicher lokaler Konfiguration verständlich abbrechen statt unbemerkt extern zu binden.
2. **Host-Prüfung vor Daten-/Scraperarbeit:** Erlaubte HTTP-Authorities exakt aus expliziter lokaler Konfiguration bestimmen (Name/IP und Port), keine Teilstring-/Suffix- oder beliebige `*.localhost`-Freigabe. Unbekannte, mehrdeutige und ungültige Hosts ablehnen, ebenso nicht konfigurierte IPv6-Adressen. Die Liste nicht aus einem ungeprüften Host-/Origin-Header ableiten. Ohne freigegebenen Proxy keine `Forwarded`-/`X-Forwarded-*`-Header als vertrauenswürdige Quelle verwenden. Dies begrenzt DNS-Rebinding, ersetzt aber keine Identität lokaler Clients.
3. **CSRF und Origin gemeinsam:** Vorzugsweise Spring Security mit expliziter, für den lokalen Modus zunächst anmeldungsfreier Filterkette und standardmäßigem Session-CSRF-Verfahren statt eigener Token-Kryptografie planen. Kein JWT, Benutzer-/Rollenschema oder zusätzliche Infrastruktur nötig. Alle POST-/DELETE-Aktionen einschließlich Scraper benötigen einen gültigen, sitzungsgebundenen Token. Vorhandenen Origin nach Schema/Host/Port exakt prüfen; fremden oder `null`-Origin abweisen. Fehlender Origin darf kein Bypass sein: mindestens gültiger CSRF-Token und keine widersprechenden Referer-/Fetch-Metadata-Angaben. HTML-Navigation/lesende GETs ohne Origin ausdrücklich berücksichtigen; `Sec-Fetch-*` nur ergänzend verwenden. Kein pauschales CORS-Öffnen, keine Endpoint-Ausnahmen zum Reparieren des Frontends. [Spring Security: CSRF und Mehrseiten-JavaScript](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
4. **Frontend gezielt anbinden:** CSRF-Token und Headername über Thymeleaf-Metatags in `home.html` und `einkaufsliste.html` bereitstellen; kleine gemeinsame Fetch-Hilfsfunktion für die vier schreibenden Dialog-/Einkaufsskripte planen. Token nur an denselben Origin senden, nicht in URL/Logs. Relative Requestpfade und bisherige JSON-Bodies beibehalten. Session-Cookie mit HttpOnly/geeignetem SameSite; Secure bei künftigem HTTPS. Mehrere Tabs, Neustart/abgelaufene Session und verständliche 403-Meldung mit manuellem Neuladen berücksichtigen. Keine automatische Wiederholung von Kauf-/Schreibaktionen; vorhandene 204/404/417-Verträge erhalten. Ein bloßer Token im HTML-Formular erreicht die heutigen Fetch-Requests nicht automatisch.
5. **Autofill atomar mit Client umstellen:** Im späteren Auftrag denselben Pfad `/autofillBandData/{bandIndex}` bewusst auf geschütztes POST ändern und `popup_edit_baende.js:173` gleichzeitig anpassen. Antwortpayload unverändert; GET und HEAD müssen danach ohne Browser-/Servicearbeit 405 liefern, keine Weiterleitung und keinen weiterhin ausführbaren GET-Kompatibilitätspfad. OPTIONS muss nebenwirkungsfrei bleiben. Link-/Bookmark-Aufrufe starten dann absichtlich keinen Import. `/scrape` ebenfalls vollständig schützen; Mengen-/Index-/Parallelitäts- und Zeitlimits separat mit T02/S04–S06 abstimmen.
6. **Ergänzende Grenzen:** Alle APIs/Views/Assets in die Hostpolitik aufnehmen; Actuator auf minimale lokale Health-Ausgabe begrenzen, keine Diagnose-Exposition aus Bequemlichkeit. Frame-Schutz und `nosniff` ergänzend prüfen. Eine strenge CSP erst nach Inventur vorhandener Inline-Skripte/-Handler separat planen. Authentifizierung ist für den beschlossenen lokalen Vertrauensbereich zunächst kein Pflichtbestandteil; bei geteiltem Windows-PC wäre sie erneut zu entscheiden. CSRF kann eigenes XSS oder untrusted lokale Programme nicht abwehren.

**Implementierungsreihenfolge und betroffene Dateien:**

| Schritt | Dateien / Komponenten im Folgeauftrag | Abschlusskriterium und Risiko |
|---|---|---|
| 1: Regeln als Tests festhalten | Neue reine Authority-/Origin-Tests und neue geschützte MVC-Sicherheitsklasse unter `src/test/java/de/mangalib/`; vorhandene Guard-/Vertragstests erhalten | Ablehnung vor Service-/Scraperaufruf beweisen; kein echter Browser oder Netzwerk. Regeln für fehlende Header und erlaubte Adresse eindeutig. |
| 2: Lokalen Rahmen + Client gemeinsam sichern | `build.gradle.kts` nur nach Freigabe einer Security-Abhängigkeit; `application.properties`; kleine neue Sicherheitskonfiguration/Filter unter `src/main/java/de/mangalib/`; `templates/home.html`, `einkaufsliste.html`; gemeinsame JS-Hilfe und `popup_add_reihe.js`, `popup_edit_reihe.js`, `popup_edit_baende.js`, `einkaufsliste.js` | Host/Origin/CSRF und UI-Anpassung zusammen liefern. Neue 403/Session-Abhängigkeit ist die größte Kompatibilitätsänderung; weder Datenmodell noch Geschäftsregeln ändern. Kein Default-Login unabsichtlich aktivieren. |
| 3: Scraper-Methodenvertrag sichern | `controller/MainController.java:532`, `popup_edit_baende.js:173`, neue Sicherheits-/DOM-Tests | Geschützter POST funktioniert nur mit Scraper-Double; GET/HEAD/OPTIONS starten nichts. Keine pauschalen CSRF-Ausnahmen. |
| 4: Regression und Abnahme | Bestehende MVC-/JSON-, DOM-, Persistenz-/Datenerhaltungstests; danach TESTING-/Startdokumentation im freigegebenen Implementierungsauftrag | Erst 44 datenbankfreie Guard-Tests, dann geschützte Fälle und volle Suite nach TESTING.md; unveränderte Payloads, Layout, Daten und Schema. Erwartete Methoden-/Ablehnungsverträge gezielt ergänzen, Assertions nicht abschwächen. |

**Notwendige automatisierte Sicherheitstests:** Exakte lokale Hosts/Ports, IPv6- und ungültige Authority-Fälle, fremder Host trotz lokaler Verbindung und manipulierte Forwarded-Header; gleiche/fremde/fehlende/`null`-Origins; Token fehlt/falsch/fremde Session/korrekt, auch beim DELETE und Scrapen; Cross-Site-Formular-Content-Types und OPTIONS; nebenwirkungsfreie GET-/HEAD-Autofill-Ablehnung; minimaler Actuator-Zugriff. Negative Fälle müssen **keinen Service-/Repository-/Scraperaufruf** auslösen. Positive Scraperfälle ausschließlich mit statischen Doubles. jsdom-Fetch-Spies prüfen Tokenheader, keine Tokenweitergabe an fremde Ziele und keine automatischen Schreib-Replays; sie ersetzen keine Browserprüfung. Spring-Tests verwenden den bestehenden geschützten Vollkontext, niemals `addFilters=false` oder eine ausgeschaltete Guard-Registrierung. Nur neue transaktionale Fixtures, Rollback und Daten-/Schema-Vergleich; keine echten HTTP-Schreibtests gegen einen getrennten Server.

**Manuelle Abnahme erst gesondert freigeben:** In einer sicher konfigurierten isolierten Umgebung alle lokalen HTML-/JSON-Lesepfade, Suche/Filter, Anlegen/Bearbeiten/Löschen/Kauf mit eigenen Testfixtures und mehrere Tabs prüfen; Schreibpfade außerhalb MockMvc benötigen vorher ein eigenes sicheres Isolationskonzept. Browserkonsole und Netzwerkpanel müssen Session-/Tokenfehler verständlich erklären. Autofill zunächst mit Double; ein echter Scraper-Smoke-Test ist ein eigener Auftrag. Listener/IPv4/IPv6 und Firewall nur lesend kontrollieren, LAN-Zugriff muss im lokalen Modus scheitern; fremde Host-/Origin-Fälle nur kontrolliert mit nebenwirkungsfreien Antworten prüfen. Keine produktiven Bestände für Sicherheitsproben verwenden. Der jetzige Auftrag hat weder Listener noch Browserantworten gemessen.

**Für Teil A entschieden (11.10.2026):** Die offizielle Boot-kompatible Spring-Security-Abhängigkeit ist freigegeben. Explizite anmeldungsfreie Filterkette ohne Standardbenutzer/Loginformular; ausschließlich Loopback-Bindung an `127.0.0.1`, unterstützte HTTP-Hosts `localhost` und `127.0.0.1` mit dem konfigurierten Port. IPv6 und externe API-/CLI-Clients sind derzeit nicht erforderlich. Keine neuen Profile oder LAN-Freigabe. Bei späterer Umsetzung von K03 ist K04 gemeinsam verpflichtend (siehe oben). Der automatische Start steht fest, die Wahl Anmeldung/Hochfahren und Aufgabenplanung/Dienst erfolgt in T07. Grobe Schätzung S02/S03 lokal: **2–4 Entwicklungstage** einschließlich gezielter Sicherheits-/Frontendtests und Abnahme, ohne Windows-Paketierung oder LAN-Erweiterung. Keine Sicherheitsmaßnahme ist durch dieses Planungsdokument bereits aktiviert.

**Teil-A-Vorprüfung – Umsetzung angehalten (11.10.2026):** Der bestehende GET `/autofillBandData/{bandIndex}` (`MainController.java:532` → `BandService.java:550` → `BandScraper.java:24`) startet unmittelbar einen Scraper. Spring MVC unterstützt für GET-Mappings auch HEAD; der übliche CSRF-Matcher schützt GET/HEAD nicht. Ein gültiger lokaler Host beweist nicht die Herkunft des Aufrufs, und Browseranfragen können bei GET/HEAD ohne Origin-Header eintreffen. Deshalb reichen Loopback, Host-Prüfung und Ablehnung *vorhandener* fremder Origins für diesen Pfad nicht als vollständiger Schutz. Dies ist eine statisch belegte Schutzlücke des vorgesehenen Ansatzes, kein durchgeführter Browser-Angriff. Quellen: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Spring MVC HEAD/OPTIONS](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#ann-requestmapping-head-options), [Fetch Origin-Header](https://fetch.spec.whatwg.org/#origin-header).

Gemäß Abschnitt E wurde zunächst angehalten. Der Projekteigentümer hat anschließend die gemeinsame POST-Umstellung ausdrücklich freigegeben und die Stoppregel genau dafür aufgehoben. Schritt 3 wird daher gemeinsam mit Schritt 2 umgesetzt, ohne zeitweilige CSRF-Ausnahme für GET/HEAD. Die übrigen Sicherheits- und Testvorgaben bleiben erhalten.

**S02/S03 Teil A einschließlich freigegebener Autofill-POST-Umstellung umgesetzt und technisch geprüft (11.10.2026):**

- Explizite Bindung `127.0.0.1`, Standardport 8080. Frühe Prüfung effektiver Konfiguration verhindert Wildcard-/LAN-/Proxy-Overrides, abweichende Cookie-Regeln, separate Management-Ports und erweiterte Health-Ausgabe. Eine zusätzliche Prüfung der fertig konfigurierten Server-Factory verhindert abweichende Bind-Adresse, Port oder TLS-Aktivierung durch Factory-Customizer. Kein neuer Betriebsmodus oder Profil.
- `LocalRequestFilter` prüft alle Requests einschließlich Views, JSON, Assets, Fehlerpfaden und Actuator vor Controller-/Servicearbeit. Nur `localhost`/`127.0.0.1` mit konfiguriertem Port; fehlende, mehrfache, manipulierte Hosts und Forwarded-/X-Forwarded-Header werden abgewiesen. Vorhandene Origins müssen exakt demselben lokalen Origin entsprechen; fremde, mehrfache und `null`-Origins sind gesperrt. Bei Schreibrequests widersprechende Referer-/Fetch-Metadata-Header ebenfalls abweisen; fehlende Browserheader ersetzen keinen CSRF-Token.
- Offizieller Boot-Security-Starter mit expliziter anmeldungsfreier Filterkette, Session-CSRF ohne Endpoint-Ausnahmen, kein Login/HTTP-Basic/Logout und kein generierter Standardbenutzer. HttpOnly-/SameSite=Strict-Sessioncookie, ausschließlich Cookie-Tracking; im lokalen HTTP-Betrieb kein Secure-Flag. Actuator exponiert ausschließlich Health mit Status, keine Details/Probes/Discovery. Runtime-Auflösung und Boot-JAR bestätigen Spring Security **7.1.1**, Boot **4.1.1**; Java 21 und Gradle 8.14.3 unverändert.
- `home.html`/`einkaufsliste.html` stellen maskierte CSRF-Tokens und Headername als Metatags bereit. `security.js` sendet Schreibrequests ausschließlich an den eigenen Origin und die tatsächlich geprüfte absolute URL, ohne Token in URLs/Logs oder Weiterleitungen. Alle zehn bisherigen Schreib-Fetch-Aufrufe und Band-Autofill verwenden die gemeinsame Hilfe. Fehlende Tokens/403 werden verständlich mit manuellem Neuladen behandelt; keine automatischen Schreib-/Kauf-Replays.
- `/autofillBandData/{bandIndex}` und Client gemeinsam auf geschütztes POST umgestellt. GET und HEAD liefern für gültige lokale Requests **405 ohne Controller-/Service-/Scraperaufruf**, OPTIONS bleibt nebenwirkungsfrei; unzulässige Hosts/Origins werden bereits mit 403 abgewehrt. `/scrape` unterliegt denselben CSRF-/Host-/Origin-Grenzen. Autofill-Feldnamen, Preisstring, Bild-/Linkübernahme und die bestehende Oberfläche bleiben erhalten. Die 29 bestehenden MVC-/JSON-Vertragsfälle behalten sämtliche fachlichen Assertions; nur gültige lokale Request-/Tokendaten wurden ergänzt.

| Tatsächlich ausgeführte Validierung | Ergebnis |
|---|---|
| Datenbankfreie `TestDatabaseGuardTest` | **44 bestanden**, unverändert. Vor Integrationstests ausgeführt. |
| Neue `LocalSecurityPolicyTest` | **61 bestanden**, ohne Boot/JDBC/DNS/Browser; Authority, Origin, Referer/Fetch-Metadata, Overrides und Factory-Grenzen. |
| Neue geschützte `LocalSecurityMvcTest`, bestehende MVC-/Rendering-Fälle | **27 + 29 + 3 bestanden**; Sessionbindung einschließlich fremder/falscher Tokens, Ablehnung vor Controllerarbeit, Scraper ausschließlich statisch ersetzt. |
| Finale vollständige Suite `clean test` | **179 bestanden = 91 bestehende + 88 neue**, 0 Failures, 0 Errors, 0 Skipped; Teil-/Wiederholungsläufe nicht addieren. |
| `pnpm run test:dom` | **65 bestanden = 33 bestehende + 32 neue**, keine Fehler; echte Formular-DOMs und Dialog-/Kauf-/Löschlistener mit Fetch-Spies, ohne Browser/Netzwerk. |
| `assemble --dry-run`, anschließend `assemble` | Erfolgreich; Aufgabenplan nur Kompilierung/Ressourcen/Main-Class/bootJar/jar, keine erneuten Tests oder Scraper. |
| Rein lesender Daten-/Schema-Fingerabdruck vor/nach Suite und Build | Identische 16 Hash-/Zählzeilen über zehn Tabellen und Schema-Metadaten; **78 Stammdaten erhalten**, fünf Geschäftstabellen unverändert leer. Rollback-/Datenerhaltungstests bestanden. |

Der SHA-256-Fingerabdruck bleibt `ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`. AUTO_INCREMENT-Zähler können durch zurückgerollte Fixtures steigen und werden weiterhin nicht zurückgesetzt. Ausschließlich `mangalib_test`/`mangalib_test_user` über den bestehenden Guard; keine produktive Verbindung, Migration, Schemaänderung, Dateninitialisierung oder echte Scraper-/Browserausführung. Testschutzdateien, Testprofil, Datenmodell und Geschäftsregeln unverändert. Berichte der damaligen 179-Test-Suite lagen unter `build/test-results/test` und `build/reports/tests/test`; spätere gefilterte Testläufe ersetzen diese Ausgabe (siehe MANUAL_TESTING.md, Abschnitt 10); lokale Vorher-/Nachher-/Dependency-Artefakte liegen Git-ignoriert unter `.gradle/security-validation`. Sichere Reproduktion und Grenzen: [TESTING.md](TESTING.md), [DOM-Tests](src/test/js/README.md).

**Historischer technischer Prüfstand vor der Browserabnahme:** In diesem Implementierungsauftrag wurde kein echter HTTP-Listener oder Browser gestartet. Die spätere Abnahme des Eigentümers und die aktuelle Nachbereitung sind unten dokumentiert. Tatsächliche Loopback-Erreichbarkeit, Cookie-Übertragung, mehrere Tabs, Sessionablauf/Neustart, alle UI-Abläufe und Bilddarstellung sind später in isolierter Umgebung manuell zu prüfen. Lokale untrusted Programme und eigenes XSS werden durch anmeldungsfreien CSRF-Schutz nicht ausgeschlossen; andere Entwicklungslistener, Serverrechte und Windows-Konfiguration bleiben separat zu kontrollieren. Kein LAN-/IPv6-/Internetbetrieb freigegeben. Bestehende Temporal-/OSIV-/Mockito-/JVM-Warnungen sowie erwartete 405-Negativtest-Warnungen bleiben; allgemeine Eingabe-/URL-/Scraperlimits, Dependency-Befunde, Logging und fachliche Fehler gehören weiterhin zu separaten Aufgaben. Umsetzung ohne fachliche/UI-Modernisierung und ohne Commit/Push; Abnahme des Projekteigentümers ausstehend.

**Historischer Vorbereitungsstand der isolierten Browserabnahme (11.10.2026):**

- Eigener Launcher und Abnahme-JAR für `manual-test`, ausschließlich `mangalib_manual_test` / `mangalib_manual_test_user`, eigene Prozess-Passwortvariable und `127.0.0.1:18080`. Konfigurations-/JDBC-/Bean-Prüfungen vor Kontext und Verbindung; kein produktiver Fallback. `Start-ManualTest.ps1 -ValidateOnly` prüft ohne Kontext/JDBC/Listener. Bestehender JUnit-Guard und Testprofil unverändert.
- Kleine gemeinsame Scraper-Aufrufgrenze, unveränderte Live-Delegation im regulären Betrieb. Das manuelle JAR enthält ausschließlich synthetische Doubles, keine Live-Scraper-/Selenium-/DevTools-Klassen. Sichtbarer manueller Banner; lokale Cover und manuelle Bild-/Fetch-Policy, ohne Änderung der regulären Security-Filterkette oder Fachregeln.
- Schemaübernahme ausschließlich aus einem separat freizugebenden, rein lesenden `mangalib_test`-Export vorbereitet. Lokaler Prüfer qualifiziert zehn bekannte InnoDB-Tabellen/FKs auf die neue DB, übernimmt keine Daten/Zähler und führt kein SQL aus. Synthetische einmalige Fixtures, getrennte Administrator-/Einrichtungs-/Laufzeitrechte und Browsercheckliste in [MANUAL_TESTING.md](MANUAL_TESTING.md).
- Tatsächlich erfolgreich: **44 bestehende DB-Guard-Tests**, **61 bestehende lokale Policy-Tests**, **52 neue datenbankfreie manuelle Schutz-/Artefakttests**, **25 PowerShell-Prüfungen**, **65 bestehende DOM-Tests**; Aufgabenplan und `assembleManualTest` erfolgreich. Keine neuen Abhängigkeiten/Downloads, alle Gradle-Prüfungen offline. Die vollständigen 179 Spring-/Integrationstests wurden hier wegen des DB-Zugriffsverbots nicht erneut ausgeführt; ihre bisherige Baseline bleibt davon getrennt. HotSpot-CDS-Warnung unverändert.
- **Im damaligen Vorbereitungsauftrag ausstehend:** tatsächlicher Schemaexport und Schema-/Fixture-Kompatibilität, Konten-/Rechteprüfung, DB-Einrichtung, erster Kontext-/Listenerstart und Browserabnahme. Keine DB-Verbindung, SQL-Ausführung, Produktivdaten, echten Scraper/Browser, Commits oder Pushes in diesem Vorbereitungsauftrag. Kein automatischer Start/T07-Deployment eingerichtet. Daten-/Schema-Unverändertheit folgt hier aus dem vollständigen Zugriffsverzicht, nicht aus einer neuen DB-Messung.

**Erste manuelle Browserabnahme durch den Eigentümer und gezielte Nachbereitung (11.10.2026):**

- Einrichtung/Start der dritten DB-Umgebung und ausschließlich `127.0.0.1:18080` vom Eigentümer bestätigt. Anzeige, Filter/Sortierung, reguläres Bearbeiten/Abbrechen, Lesestatus, Einkauf/Kauf/Löschen und Statistiknavigation erfolgreich beobachtet; außerdem XSS-/URL-Proben, Sessioncookies/-timeout, zwei Tabs, Host-/Origin-Abwehr und `/scrape`-Methodenabwehr. Synthetische 900001/900002 funktionieren, 900005 wird gesperrt. Dies sind **manuelle Beobachtungen**, keine neuen Codex-Integrationstests.
- Gemeldeter Abschluss: zehn Tabellen, 17 inhaltlich unveränderte Stammdaten, 3 Reihen/3 Details, 6 Bände, 2 Einkaufspositionen/2 Details; Setupkonto gesperrt, Server beendet, Port frei. Geschäftsdaten entsprechen bewussten manuellen Aktionen; kein unveränderter Gesamtdatenfingerabdruck behauptet.
- **Gezielt korrigiert:** nullable Vergriffen-/Preis-Flags im Update null-sicher vergleichen und beim unberührten Dialogspeichern NULL erhalten (einschließlich optionaler unveränderter URLs); manuelle Banner-Abstände zentral regeln, ohne die normale Oberfläche zu ändern; Edit-Speicherfehler anhand Status/Content-Type kontrolliert behandeln, bestehende CSRF-Meldungen erhalten und kein Replay; erwartete Double-Ablehnungen ausschließlich im manuellen Betrieb als neutrales 400/JSON mit festem WARN ohne Stacktrace. Whitelist, Live-Ausschluss, Datenbank- und Zugriffsschutz unverändert.
- **Tatsächlich datenbankfrei durch Codex geprüft:** 44 Guard-, 61 Policy- und 85 manuelle Java-/Mock-/Template-/Artefaktfälle (**33 neue**), 93 DOM-Fälle (**28 neue**) und 25 PowerShell-Prüfungen; alle bestanden. Aktualisiertes `assembleManualTest` erfolgreich, ausschließlich Offline-Gradle/ValidateOnly mit synthetischen Werten. Kein DB-/SQL-/Listener-/Browser-/Scraperzugriff, kein `clean test` oder Vollkontextlauf. Die historische 179-Test-Baseline wurde nicht erneut ausgeführt.
- **Nachabnahme offen:** echtes Bannerlayout bei Scrollen/Zoom/Dialogen, Speichern der betroffenen NULL-Reihe und Fehler-/Sessionanzeige sowie kontrollierte 400-Ablehnungen mit dem aktualisierten JAR. Vollständige Schema-/Rechteprüfung und neue DB-Fingerabdrücke nicht von Codex erhoben. Monatsstatistik 2025/2026 bleibt M04/T04; Status/Verlag/Typ/Format sind bekannte unvollständige Auswertungen. Einkaufsfixtures im alten Datumsfenster waren unsichtbar und wurden nur durch den Eigentümer in der manuellen DB auf November 2026 verschoben. Für zukünftige leere Einrichtungen einen explizit protokollierten Monatsanker vorsehen; **kein Reset/Re-Import bestehender Fixtures**. Nullable Vergriffen-Filter außerhalb des Update-Pfads sowie allgemeine Rohfehler-/Logging-Befunde bleiben separate T02/T04/T06-Arbeit.

Ausführliche Beobachtungen, Ursachen, Testbefehle, Dateiliste und manuelle Nachprüfung: [MANUAL_TESTING.md, Abschnitt 10](MANUAL_TESTING.md#10-ergebnisse-der-manuellen-browserabnahme-vom-11102026). Die historische Vorbereitung bleibt dort erhalten. Fachliche Muss-/Soll-/Kann-Prioritäten unverändert; keine produktive Freigabe oder allgemeine Modernisierung.

### T03 – Atomare, wiederholungssichere Datenänderungen

**Abnahmestand 08.11.2026:** T03 ist für das lokale Einzelbenutzermodell
fachlich und technisch abgenommen. Drei reale Commit-/Konkurrenzfälle,
17 E1–E6-Matrixfälle über getrennte Läufe und die freigegebenen Browserfälle
H1–H7 sind bestätigt. [T03-Gesamtabnahme](T03_CLOSEOUT.md) trennt die
Nachweisgrenzen und dokumentiert den vorbereiteten lokalen Fast-Forward.
Dokumentationscommit und Merge stehen aus. Historische Integrationstests
mit abweichender Transaktionsgrenze bleiben eine getrennte Testaufgabe.
Die folgenden datierten Einträge erhalten ihren historischen Status.

**T03-0 datenbankfrei abgeschlossen (02.11.2026):** [T03-Plan mit Ist-/Soll-Matrix und Fachentscheidungen](T03_IMPLEMENTATION_PLAN.md). Auf neuem Branch `codex/t03-data-integrity` direkt ab T02-Commit `b7a2dbe`: 53 neue Charakterisierungsfälle und 227 unveränderte M06-/T02-/Standalone-MockMvc-Fälle bestanden, insgesamt 280, keine Fehler/übersprungen. Wiederholungs-/Konkurrenz-, Zuordnungs-, Cent- und Reduktionsprobleme reproduziert, NICHT behoben. Keine DB-Verbindung oder produktive T03-Implementierung; D1–D6 vor T03-1 bestätigen. Historische Prototypdateien und T02-Branch unverändert.

- Einkauf → Bibliothek in einer konsistenten Transaktion; erneute und parallele Kaufübernahme absichern.
- Einzelbandänderung und abgeleitete Reihensummen gemeinsam sichern.
- Stabile Bandidentifikation statt unsortierter Listenpositionen; definierte Behandlung von Bandanzahländerungen.
- `BigDecimal`, Restcent-Verteilung und Bedeutung fehlender/0-Euro-Preise vereinheitlichen.

### T04 – Konkrete bestehende Bugs
- Einkaufsliste nach `YearMonth` statt bloßem Monatsnamen gruppieren.
- Centbeträge in Statistiken erhalten.
- Diagramm-Listener dauerhaft funktionsfähig machen; doppelte Initialabfragen vermeiden.
- Neu angelegte Bände dürfen bei fehlendem Preis nicht stillschweigend 0 € erhalten.
- Scraper-/Formulardaten normalisieren, Formularzustand nach Abbruch zurücksetzen.
- SQL-Skript korrigieren; fachliche Fehlerfälle verbessern. Konsistente technische Fehlerdiagnose und Logging werden in T06 geplant.

### T05 – Wartbarkeit, Architektur und Performance
- Schichten entflechten; Controller entlasten, typisierte Requests und Importmodelle einführen.
- Datenbankabfragen bündeln und messen; bei Bedarf Pagination.
- Gemeinsame CSS-/Template-Bestandteile wiederverwenden, ohne visuelles Redesign.
- Treiberpfade konfigurierbar machen; Dokumentation, Dependency-Prüfung und CI verbessern.

**Späterer Arbeitsblock – systematische Codebereinigung:** Größere Bereinigungen erst nach den wesentlichen Funktions- und Datenintegritätsarbeiten und vor T07 durchführen. Nachweislich ungenutzte Elemente systematisch identifizieren und entfernen; komplexe Methoden und Abhängigkeiten entflechten, eine einheitliche Architektur und verständliche Benennungen herstellen. Nicht offensichtliche Regeln und Zusammenhänge gezielt durch Kommentare und Javadoc erklären. Die Umsetzung erfolgt in kleinen, testgesicherten, verhaltenswahrenden Refactorings; bestehende Daten und Funktionen erhalten. Keine Vorziehung dieses Arbeitsblocks durch die aktuelle Browserabnahme und keine Änderung der übrigen Prioritäten.

### T06 – Logging, Fehlerdiagnose und Nachvollziehbarkeit

**Nur geplant, noch nicht implementiert.** Querschnittsaufgabe für T02/T04 und den späteren Hintergrundbetrieb T07; keine Änderung der fachlichen Prioritäten.

- Bestehende `System.out.println`-/`printStackTrace`- und Browser-Rohdatenausgaben gezielt inventarisieren (beispielsweise `MainController.java:233,300,326`, `EinkaufslisteController.java:95`, beide Scraper). Vorhandene Logging-Fassade verwenden; keine zweite Logging-Plattform ohne Bedarf einführen.
- Log-Level konsistent definieren: TRACE für eng begrenzte Ablaufdetails, DEBUG für gezielte Diagnose, INFO für wichtige Start-/Abschlussereignisse, WARN für erwartete problematische Situationen, ERROR für unerwartete Fehler/fehlgeschlagene Aktionen. Keine vollständigen Request-/Response-Maps auch auf niedrigeren Leveln. Level extern konfigurierbar; Dauerbetrieb standardmäßig INFO.
- Einheitliche möglichst strukturierte Ereignisse mit Zeitpunkt, Aktion, Ergebnis, Dauer und begrenzten technischen Kennungen; Import-/Scraperstart, Abschluss/Abbruch, Bearbeitungen und fehlgeschlagene Speicherung nachvollziehbar machen. HTTP-Erfolg nicht als Beweis atomarer Speicherung behandeln (T03).
- Servergenerierte Korrelations-ID für zusammengehörige Request-/Service-/Scraperlogs; eingehende IDs nur begrenzt und validiert verwenden. Bei späteren Hintergrundaufgaben bewusst weitergeben, Request-Kontext anschließend sauber entfernen. Eine neutrale Fehlerantwort kann die ID enthalten, niemals interne Exception-Texte.
- Unerwartete Fehler einmal mit Stacktrace im geschützten technischen Log diagnostizieren; erwartete Eingabe-/Schutzfehler passend mit 4xx und freigegebener Meldung behandeln. Klassifikation von fachlichen Fehlern bleibt T04, Abwehr von Informationspreisgabe/Log-Manipulation bleibt T02.
- Technische Logs, fachliche Ereignisse und ein gegebenenfalls später benötigtes Audit-Logging trennen. Fachliche Historie für K01/S03 nicht aus rotierenden Textlogs rekonstruieren; keinen neuen Audit-Datenbestand oder Schemaumbau vorwegnehmen.
- Keine Passwörter, DB-Zugangsdaten, Tokens, Session-/CSRF-Werte, vollständigen Umgebungen, unnötigen personenbezogenen Daten oder vollständigen Manga-Sammlungen loggen. URL-Querys, Kontrollzeichen/CRLF, sensible Exception-Inhalte und Browserkonsole berücksichtigen; lokale Logs nicht ins Repository oder Release übernehmen.
- Rotierende lokale Logdateien mit konfigurierbaren Größen-/Zeitgrenzen, Aufbewahrungsfrist und Gesamtspeicherlimit; beschränkte Windows-Dateirechte, zuverlässiger Logpfad außerhalb des Release-Verzeichnisses, Verhalten bei vollem Datenträger/fehlendem Schreibrecht festlegen. Konkrete Fristen und Budget vor Umsetzung entscheiden.
- Tests mit Logger-Appender-/Fehler-Doubles: Level, Ereignisfelder, Korrelations-ID über Schichten, Kontextbereinigung, Stacktrace bei unerwartetem Fehler, keine Secret-/Rohdaten-/CRLF-Ausgabe, neutraler HTTP-Fehler und Rotation unter temporärem Testpfad. Keine echten DB-Fehler oder Scraperstarts provozieren.
- Hintergrundbetrieb vorbereiten: Startversion/Modus und sichere Bind-Adresse, Start-/Stop-/DB-Verfügbarkeitsfehler sowie Wiederanlauf ohne Geheimnisse nachvollziehbar; keine zusätzliche produktive Diagnoseinfrastruktur. T07 legt Betriebskonto, Pfade und Prozessverwaltung fest.

### T07 – Bereitstellung, Installation und automatischer Betrieb

**Verbindliches Abschlussziel:** Dauerhaft auf dem eigenen Windows-PC ohne VS Code und ohne Gradle zum täglichen Start nutzbar. Erst nach wesentlichen Funktions-, Sicherheits- und Datenintegritätsarbeiten einschließlich S02/S03, fachlicher/UI-Abnahme sowie geprüftem Backup-/Wiederherstellungsweg umsetzen. Kein Windows-Dienst, Autostart, Docker oder Startkonfigurationswechsel wurde in diesem Auftrag eingerichtet.

**Primärziel Windows – spätere Optionen und Entscheidungskriterien:**

| Option | Eignung | Zu klärende Grenzen |
|---|---|---|
| Aufgabenplanung bei Anmeldung | Zunächst schlankster Kandidat für den persönlichen Einzelbenutzer-PC; vorhandene Windows-Komponente startet die JAR im Benutzerkontext | Nur nach Anmeldung verfügbar; versteckter Start, eine Instanz, Neustartregeln, Arbeitsverzeichnis und geordnetes Stoppen eigenständig absichern. Browser nicht ungefragt bei jedem Start öffnen. |
| Aufgabenplanung beim Hochfahren | Ohne interaktive Anmeldung verfügbar, weiterhin kein zusätzlicher Dienst-Wrapper | Betriebskonto/Anmelderechte und Secretzugriff, DB-Startreihenfolge, Wiederholungen und Shutdown kontrollieren; keine pauschale Ausführung mit höchsten Rechten. |
| Windows-Dienst mit geprüftem Wrapper | Sinnvoll, wenn echte Dienststeuerung, Betrieb vor Anmeldung und verwalteter Stop/Wiederanlauf gewünscht sind | Eine normale `java -jar`-Anwendung ist kein SCM-Dienstprogramm; Wrapper benötigt separate Auswahl/Freigabe, Aktualisierung und eingeschränktes Konto. Firefox/Geckodriver und Benutzerprofil müssen im Dienstkontext gesondert geprüft werden. |

Die **endgültige Betriebsform bleibt offen**. Aufgabenplanung bei Anmeldung ist eine Empfehlung zur Evaluierung, kein bereits gewählter Startweg. Start bei Anmeldung versus Hochfahren anhand des tatsächlichen Nutzungsbedarfs entscheiden. Herstellergrundlagen: [Windows-Trigger](https://learn.microsoft.com/en-us/windows/win32/taskschd/trigger-types), [Dienstschnittstellen](https://learn.microsoft.com/en-us/windows/win32/services/service-programs), [Dienstkonten](https://learn.microsoft.com/en-us/windows/win32/services/service-user-accounts).

- Ausführbare, versionierte Spring-Boot-JAR über den bestehenden Wrapper erzeugen und archivieren; `build.gradle.kts` verwendet bislang `0.0.1-SNAPSHOT`, eine belastbare Release-Versionierung steht aus. Java-21-Laufzeit und eigene MySQL-Instanz dokumentieren; mitgelieferte Laufzeit nur nach Lizenz-/Updateentscheidung. Reproduzierbarkeit mit fixierten Versionen, geprüftem Abhängigkeitsgraphen und Artefakt-Prüfsummen absichern. DevTools, Tests, jsdom/node_modules, private Berichte, private Konfiguration und Sammlungsdaten gehören nicht ins Betriebspaket; geheimnisfreie Standard-/Beispielkonfiguration ist zulässig.
- Installation, externe Konfiguration, Log-/Treiber-/Temporärpfade und Datenhaltung sauber trennen. Versionierte Programmverzeichnisse statt Überschreiben privater Konfiguration; absolute freigegebene Pfade und eine Instanz. Im Betrieb weiterhin ausschließlich geprüfter lokaler HTTP-Zugriff, beispielsweise `http://localhost:8080/home`; kein LAN-Default oder offener Port als Fehlerbehelf.
- DB-Secrets außerhalb Repository/JAR/Startargumenten verwalten: geschützter Windows-Credential-/DPAPI-Ansatz unter dem tatsächlichen Betriebskonto evaluieren; Umgebungswerte sind keine verschlüsselte dauerhafte Speicherung. Ein Dateiansatz benötigt explizite Risikoentscheidung und restriktive ACLs, nicht nur `.gitignore`. Keine Passwörter in Startskripten, Task-XML, Logs oder Release-Beispielen. Test- und Betriebszugänge vollständig trennen.
- MySQL-Verfügbarkeit bei Start zuverlässig berücksichtigen: begrenzte Wiederanläufe/Backoff und verständliche Meldungen statt endloser Neustartschleife; vorhandenes Schema validieren, niemals bei Verbindungsfehler neu erzeugen oder seeden. Portkonflikt/ungültige Konfiguration früh erkennen. Logging/Rotation nach T06.
- Kontrolliertes Beenden beim Herunterfahren, geordneter HTTP-/DB-Abschluss und zuverlässige Freigabe gestarteter Scraperprozesse planen. Noch laufende Scraper und Datenoperationen berücksichtigen (T02/T03), nicht blind Prozesse beenden. Firefox/Geckodriver bleiben gesondert konfigurier- und prüfbar; heute kein Browserlauf.
- Updates als geprüfter Artefaktwechsel mit vorherigem Backup, Stop/Start, Zustandskontrolle und Rückweg dokumentieren. Kein automatisches Überschreiben von Daten/Konfiguration, keine DB-Neuanlage bei Update/Neuinstallation, keine ungeprüfte automatische Schemaaktualisierung. Code-Rollback allein ersetzt keine Datenwiederherstellung.
- MySQL-Backup einschließlich Daten/Schema, sichere lokale Ablage, Aufbewahrung und tatsächlich geprüfte Wiederherstellung auf isolierter Instanz planen. Backup-Secret/-Rechte von Anwendung trennen; keine Dumps in Git/JAR/Release. Produktive Migrationen weiterhin nur gesondert autorisiert mit versioniertem Plan, Altdatenprüfung und Rückweg (AGENTS.md).
- Abnahme: Neustart/Anmeldung, fehlende DB, Portkonflikt, falsche Konfiguration, Mehrfachstart, kontrollierter Shutdown, Update/Rückwechsel und Neuinstallation ohne Datenverlust prüfen. Tests nur in freigegebener isolierter Umgebung; produktiver Dienst-/Installationsstart erst gesondert autorisieren.

**Optional danach – Selbstbetrieb durch andere Nutzer / GitHub-Release:**

- `readme` zu einer nachvollziehbaren Einrichtungsanleitung ausbauen: Windows/Java/MySQL-Voraussetzungen, ausführbare JAR, sichere lokale Beispielkonfiguration ohne Geheimnisse, Start/Stop/Fehlerdiagnose, Entwicklungs-/Test-/Betriebswege sowie Updates/Backup/Restore. Der aktuelle Hinweis auf `SQLQuery.sql` und `Inserts.sql` ist kein geprüftes Installationsverfahren; Skripte enthalten einen festen Datenbanknamen bzw. benötigen Korrektur/Validierung. Sie dürfen nie unbesehen gegen Bestandsdaten laufen.
- Neue Nutzer richten **ihre eigene leere MySQL-Datenbank** und eingeschränkte eigene Zugangsdaten ein. Initiale Struktur/Stammdaten reproduzierbar und getrennt von späteren versionierten Migrationen bereitstellen; leere Erstinstallation gegen Bestandsupdate eindeutig unterscheiden. Keine Sammlung, Zugänge oder Backups des Projekteigentümers übernehmen, keine Produktivdaten zum Pakettest verwenden.
- Reproduzierbare Build-/Release-Anleitung, feste Versionsnamen, geprüfte Artefakte/Prüfsummen und GitHub-Release-Pakete planen; Veröffentlichung separat freigeben. Lizenzierung des Projekts, Drittanbieter-/Browser-/Treiber-Abhängigkeiten und Importquellen vor öffentlicher Verteilung klären. Private Auditberichte/Logs müssen vom Paket ausgeschlossen bleiben.
- Docker Compose oder ein anderes Paket nur bei nachgewiesenem Installationsnutzen separat evaluieren, nicht als Voraussetzung für den Windows-Einzelbenutzerbetrieb. Datenvolumes, sichere Bindung, eigene Secrets, Restore und nicht destruktive Updates wären Pflicht; heute keine Docker-Dateien oder zusätzlichen Dienste anlegen.
- Smartphone-/LAN-Erweiterung folgt erst nach diesen Zielen in Abschnitt 9; Browser-Integration K06 folgt danach als allerletzte optionale Erweiterung in Abschnitt 10. Kein Internetbetrieb und keine zusätzliche Serverinfrastruktur vorsehen.

## 6. Empfohlene Ausführungsreihenfolge

| Phase | Schwerpunkt | Haupttickets / Hinweise |
|---|---|---|
| 0 | Sicherer Entwicklungsunterbau | T01; Testdatenbank, Tests, Backup-/Migrationsregeln |
| 1 | Datenintegrität und Sicherheit | T02, T03, M06; dazu Preis-/Bandanzahlfehler |
| 2 | Einkaufsliste und Import | M01, M02, S02; normalisiertes Scraper-Modell |
| 3 | Datenmodell und Lesehistorie | M05, S03, Vorbereitung M03/M04; Migration bestehender Datensätze |
| 4 | Statistik | T04 (statistikrelevante Fehler), M03, M04 |
| 5 | Verwaltung und Komfort | S01, K01, K02, K05 nach Bedarf |
| 6 | Profile und Zugriff | K03 und K04 gemeinsam nach separater Architekturentscheidung |
| 7 | Bereitstellung und Betrieb (abschließende Phase) | T07 nach wesentlichen Funktions-, Sicherheits- und Datenintegritätsarbeiten: zuerst verbindlicher Windows-Betrieb, danach optional GitHub-Release, danach optional Smartphone/LAN (Abschnitt 9), allerzuletzt optional K06 (Abschnitt 10); T06 vorher als Betriebsgrundlage |

**Umsetzungsregel:** Jedes Ticket wird vor der Implementierung mit konkreten Akzeptanzkriterien, betroffenen Dateien, Datenmigration, Tests und gegebenenfalls offenen Fachentscheidungen geplant. Die Tabelle ist eine Arbeitsreihenfolge, keine Zusage, alle Punkte automatisch umzusetzen.

## 7. Offene Fachentscheidungen

- Was genau speichert „Ebay-Preis“ derzeit: Auf-/Abschlag, absoluter Preis oder Differenz?
- Zählt ein Reread erneut zu „Days Read“ und „gelesene Bände pro Monat“?
- Welches Datum soll für eine frühere erste Lektüre ohne bekannten Zeitpunkt gelten? (Standard: **unbekannt**, kein erfundenes Datum.)
- Soll eine Reihe nur bei **allen** gelesenen Bänden als gelesen gelten? Wie soll ein teilweise gelesener Status dargestellt werden?
- Welche Statistiken zählen Reihen, welche einzelne Bände? Wie wird „Herkunft“ im Datenmodell definiert?
- Wie verhält sich eine reduzierte Bandanzahl bei schon angelegten Bänden?
- Wie erfolgt die Zuordnung eines importierten Einzelbandes zu einer vorhandenen Reihe?
- Wie werden Monatsausgaben fachlich abgegrenzt: Erscheinungsdatum oder Kaufdatum?
- Welche Daten dürfen für Entwickler-/Migrationstests in eine isolierte Kopie übernommen werden?
- Security S02/S03: Lokaler Betrieb und Autofill-POST einschließlich Nachabnahmen sind im T02-Abschluss nachgewiesen (T02_CLOSEOUT.md). IPv6, externe Clients, Anmeldung und LAN werden derzeit nicht umgesetzt und benötigen bei späterer Erweiterung eine eigene Sicherheitsabnahme.
- T06: Welche Aufbewahrungsfrist und Speicherobergrenze sollen lokale Diagnose-Logs haben? Ist später fachliches Audit-Logging erforderlich?
- T07: Soll Autostart bei Anmeldung genügen oder bereits vor Anmeldung beim Hochfahren erfolgen? Betriebskonto, Secretablage und Aufgabenplanung/Dienst erst anhand dieser Entscheidung festlegen.

## 8. Fortschrittsstatus

- [x] Produktanforderungen initial dokumentiert.
- [x] MySQL-Backup vor Beginn der Codex-Arbeiten erstellt (Wiederherstellung noch zu prüfen).
- [x] Separater Git-Branch für erste Arbeiten angelegt.
- [x] Isolierte Testdatenbank und `test`-Profil eingerichtet.
- [x] Testlauf ohne Zugriff auf produktive Daten bestätigt (Codex, 10.10.2026, 34 Tests).
- [x] Spring-Boot-3.5.16-Update technisch validiert.
- [x] Spring-Boot-4.0.8-Zwischenstand mit 91 Tests und Daten-/Schema-Vergleich technisch validiert.
- [x] Spring-Boot-4.1-Migrationsanalyse dokumentiert und 4.1.1 mit 91 Tests, Daten-/Schema-/JSON-Vergleich und Build technisch validiert.
- [ ] Fachliche Regressionstests für zentrale Abläufe und UI abgeschlossen.
- [ ] Technische Befunde und Feature-Tickets in ausführbare Codex-Aufgaben zerlegt.
- [x] Erste abgesicherte Fehlerbehebung umgesetzt (S01; S02/S03 Teil A zusätzlich technisch geprüft; erste isolierte Browserabnahme erfolgt, Fehlernachabnahme offen).

## 9. Abschlussziele und optionale Smartphone-/LAN-Erweiterung

Diese Reihenfolge ergänzt Phase 7; die fachlichen Muss-/Soll-/Kann-Prioritäten bleiben unverändert.

1. **Verbindlich: reguläre Windows-Bereitstellung und automatischer Betrieb (T07).** Nach wesentlichen Projektzielen lokale Einzelbenutzeranwendung ohne VS Code/Gradle bedienen; sichere Konfiguration, Diagnose, Updates und überprüfte Backup-/Wiederherstellung gewährleisten.
2. **Optional danach: GitHub-Release für Selbstbetrieb (T07).** Andere Nutzer verwenden eigene Datenbanken/Secrets; dokumentierte sichere Installation, Release-/Lizenzprüfung und Updates, keine privaten Bestände oder Sicherheitsberichte veröffentlichen. Keine Hosting-Infrastruktur voraussetzen.
3. **Optional nach Bereitstellung/Release, vor K06: Smartphone-/LAN-Zugriff im eigenen Heimnetz.** Erst nach den übrigen Projektzielen, regulärer Bereitstellung und dem optionalen GitHub-Release gesondert entscheiden und beauftragen. Die optionalen Ziele sind keine Pflichtvoraussetzung für den lokalen Betrieb; bei Verzicht auf einen GitHub-Release ist dessen Überspringen ausdrücklich zu entscheiden. Dann explizites LAN-Opt-in mit begrenzter Bind-Adresse/Firewall, Anmeldung und Zugriffskontrolle auch für lesende Daten-/Statistik-/Scraperpfade, HTTPS direkt in der Anwendung mit geklärtem Zertifikatsvertrauen auf dem Smartphone sowie fortbestehender Host-/Origin-/CSRF-Abwehr planen. Ein gemeinsamer Eigentümerzugang genügt gegebenenfalls; Mehrbenutzerprofile nicht ungefragt voraussetzen. Lokaler Default und Datenmodell bleiben erhalten; keine vorsorgliche LAN-Komplexität heute. Abnahme später: lokaler Modus bleibt aus dem LAN unerreichbar, LAN-Modus nur von freigegebenen Geräten/mit gültiger Sitzung, sichere Anmeldung/Abmeldung, Smartphone-Fetch-/Tokenabläufe, gesperrte Managementdiagnose und unbeeinträchtigte lokale Funktionen. **Kein öffentlicher Internetzugriff, keine Router-Portfreigaben, keine zusätzliche Serverinfrastruktur.**

## 10. Allerletzte optionale Erweiterung: Browser-Integration (K06)

Nach sämtlichen zuvor vorgesehenen Projektzielen einschließlich der optionalen Smartphone-/LAN-Erweiterung folgt K06 als letzter optionaler Roadmap-Punkt. Entfallen frühere optionale Ziele, deren Überspringen ausdrücklich festhalten. Zunächst Firefox-Erweiterung und alternative Übergabelösungen, optional Chrome, technisch und sicherheitlich bewerten. Manga-Passion-Reihen-/Banddaten nur als untrusted Vorschlag übernehmen: vorbereiteter Einkaufskorb-Eintrag, Datenvorschau, Dublettenprüfung und ausdrückliche Bestätigung. Sichere lokale Backend-Kommunikation mit geeigneten Berechtigungen und einem geprüften Übergabevertrag ist Voraussetzung; bestehende Host-/Origin-/CSRF-Regeln nicht pauschal öffnen. Keine Implementierung im aktuellen Auftrag.

---

*Diese Roadmap ist ein lebendes Planungsdokument. Fachliche Prioritäten ändern sich nur nach ausdrücklicher Entscheidung des Projekteigentümers.*

### T02 – Ergänzender Entscheidungsstand zur Jobgruppe (19.10.2026)

Nur statisch geprüft; die bisherigen T02-Prioritäten und historischen Nachweise
bleiben unverändert. Das ACTIVE_PROCESS-Flag 0x8 und ActiveProcessLimit=3 werden
im Prototyp gemeinsam zurückgelesen und geprüft; Konfiguration ist daher mehr
als ein bloßer Zahlenwert, eine wirksame Gesamtobergrenze bleibt aber unbewiesen.
Offizielle Windows-Verträge unterscheiden aggregiertes Nested-Accounting,
Limitvererbung und vorübergehend erhöhte Zähler bei fehlgeschlagener Zuordnung.
Die Ancestor-Mitgliedschaft allein erklärt keine fremden Mitglieder im privaten
Kind-Job. Konsolenhosts sind wegen NO_WINDOW/ProcessBuilder eine plausible
Herkunftshypothese für die drei zusätzlichen PIDs, jedoch nicht identifiziert.

Variante B (private Jobgruppe/eine Worker-JVM je Auftrag) bleibt empfohlen;
Produktintegration weiterhin gesperrt. Die exakte Drei-PID-Assertion bleibt
unverändert. Produktiv wäre später dynamisches, vollständig eigenes Gruppen-
Containment mit bestätigtem Cleanup statt einer vorab bekannten Firefox-PID-Liste
abzunehmen; keine solche Änderung jetzt umgesetzt. Nächster Vorschlag: genau ein
separat freizugebender Identitätssnapshot-Versuch mit allen Mitgliederhandles,
Image-/Parent-/Erzeugungsdaten und tatsächlichen Limits während derselben
Population. Dieser Versuch wurde nicht ausgeführt. Plan, Quellen, Unsicherheiten
und Zusatzaufwand im neuesten TESTING.md-Nachtrag. Keine Quell-/Guard-/Limitänderung,
keine neuen Prozesse/Tests/DB-Zugriffe und keine Git-Operationen in diesem Auftrag.


### T02 – Identitätsnachweis der Jobgruppe, weiterhin keine Produktionsfreigabe (19.10.2026)

Ein weiterer ausdrücklich freigegebener einzelner Prozessbaumversuch hat die drei
Zusatzmitglieder dieses Laufs über gehaltene Lesehandles, vollständige Imagepfade,
Parent-PIDs und Erzeugungszeiten identifiziert: je ein
C:\Windows\System32\conhost.exe zu Worker, Kind und Enkel. Alle sechs Instanzen
gleichzeitig in derselben beobachteten privaten Gruppe aktiv; Basic- und Extended-
Limits zeigten dabei unverändert 0x2008/ActiveProcessLimit=3. Die konkrete
Windows-interne Quotenbehandlung bleibt ungeklärt; keine normative conhost-Ausnahme
oder wirksame harte Gesamtprozessobergrenze behaupten.

Die unveränderte exakte Drei-PID-Assertion schlug nach gesicherter Diagnose
erwartungsgemäß fehl. Danach eigene Jobgruppe vollständig leer, alle sechs
gehaltenen Instanzen mit identischer Erzeugungszeit signalisiert beendet,
Handles/Attributlisten geschlossen und alle sechs PIDs anschließend nicht vorhanden.
Alte STOPs, Einmalfreigaben und Evidenz unverändert; neue Kennung verbraucht,
keine Wiederholung/weiteren Szenarien. Details: neuester TESTING.md-Nachtrag.

Variante B bleibt geeignet, kein Architekturwechsel aus diesem Befund nötig.
Komponenten-/Testplanung grundsätzlich vorbereitbar; produktive Workerintegration
weiterhin gesperrt. Zuerst Eigentümerentscheidung zur synthetischen Gruppen-
Erwartung und getrennte Freigaben für Quoten-/Nested-/Kill-on-close-/Crash-/
Timeoutnachweise; gemeinsames Cleanup-Deadlinebudget und echte Firefox-Kompatibilität
noch nicht abgenommen. Keine neue Implementierung oder Änderung der bestehenden
Assertion jetzt. Fachliche Muss-/Soll-/Kann-Prioritäten und andere Tickets unverändert.


### T02 – Sicherheits-/Testspezifikation Variante B, Entwurf B-SPEC-1 (19.10.2026)

Reiner Dokumentations-/Komponentenentwurf, keine Implementierungs-/Testfreigabe.
Der letzte Identitäts-/Cleanupnachweis für drei Java- und drei Konsolenhost-
Instanzen bleibt bestätigt; wirksame Gesamtprozessquote, Kill-on-close,
Supervisor-Crash, globale Deadlines und Firefox-Kompatibilität bleiben getrennte
offene Abnahmen. Keine normative conhost-Ausnahme aus dem Einzelversuch ableiten.

TESTING.md definiert B01–B12 und eine vollständige Freigabekonjunktion:
kein ausstehender Start, eigener Job leer in Accounting und vollständiger Liste,
gehaltene Instanzen beendet, Identitätsbefunde geklärt, I/O und Handle-Cleanup fertig.
Ein Live-Auftrag pro Anwendungsinstanz über beide Gateway-Methoden; keine Queue,
kein ungeschützter Fallback, kein automatischer Retry. Ungeklärte Identität oder
Ende führt zur Quarantäne. Dynamische Nachfahren werden über native Zugehörigkeit
und Instanzidentität bewertet, niemals nur über Namen. Die historische exakte
Drei-PID-Assertion bleibt unverändert; neue produktive Akzeptanz benötigt Freigabe.

**Freigabeblocker für die entsprechende Zusage:** Eine harte Maximalzahl aller
OS-Prozesse der Gruppe ist gegenwärtig nicht nachgewiesen. Quote separat mit
gewöhnlichen und danach verschachtelten synthetischen Nachfahren prüfen; alternative
Commit-/CPU-/Outputbudgets ersetzen diese Zusage nicht automatisch. Budgetwerte,
Recoverypolitik, technische Fehlerverträge und direkte JNA-/Artefaktentscheidung
vor Umsetzung festlegen. Priorisierte Unit-/OS-/Artefakt-/Offline-Firefox-Matrix
und sechs begrenzte Umsetzungsaufträge stehen im neuen TESTING.md-Nachtrag.
Keine neuen Prozessversuche, Produktintegration oder Änderung fachlicher
Muss-/Soll-/Kann-Prioritäten. manual-test bleibt strukturell ohne Live-Scraper;
SSRF sowie T03/T04/T06 bleiben separate Aufträge.


### T02 – B-MODEL-1: isoliertes Zustands-/P0-Testmodell (19.10.2026)

B-SPEC-1 grundsätzlich angenommen; neu freigegeben und umgesetzt ausschließlich
ein reines Java-Vertragsmodell unter src/test, ohne Produktionsverdrahtung,
JNA, Worker oder echte OS-Aktionen. Vollständige Variante B bleibt gesperrt.

Ein instanzübergreifend **nicht** garantierter, pro MangaLib-Instanz gemeinsamer
Reihen-/Band-Slot; keine Queue/Retry. Zustände UNAVAILABLE, IDLE, STARTING,
RUNNING, STOPPING, VERIFYING, QUARANTINED, CLOSED; Startupnachweis erforderlich,
Ticketbindung und monotone Shutdownsperre. Vollständige Freigabebarriere mit
Startabschluss, Gruppen-/Accounting-/Instanz-/Identitäts-/I/O-/Ressourcenende;
Timeout, Rootende und ein einzelner Cleanup-Erfolg geben nicht frei.
Quarantäne besitzt keinen automatischen Reset, auch keinen durch Neustart.

Abschließend **74 datenbankfreie Modelltests bestanden, 0 Fehler, 0 übersprungen**
(35 Supervisor, 39 IPC), nach identischen Offline-Dry-Runs. Nur die zwei neuen
JUnit-Klassen ausgeführt; keine Vollsuite/DB-/Native-/Browser-/Scraperabnahme.
Bestehende Assertions, Guards, manual-test, Gateway-/HTTP-/JSON-Verträge und
historische STOP-/Prototyp-/Evidenzdateien unverändert.

TESTING.md dokumentiert Übergänge, vollständige Barriere, Grenzen des Fake-
Nachweises sowie ausdrücklich vorläufige Zahlen-/HTTP-Vorschläge. Keine harte
OS-Gesamtprozesszahl zugesagt. Reale Quoten, native Ressourcen-/Pipebeendigung,
verschachtelte Jobs, Kill-on-close, Crash, Recovery über Appneustarts und
Firefox-Kompatibilität bleiben offen. Nächste Stufen nur einzeln nach Freigabe:
Budgets/Recovery, nativer Adapter mit eigener Dependencyfreigabe, begrenzte
synthetische OS-Abnahmen, IPC/Worker, Gateway-/Artefakt-/Vertragsintegration,
danach Offline-Firefox. SSRF und T03/T04/T06 nicht vorgezogen; fachliche
Muss-/Soll-/Kann-Prioritäten unverändert.


### T02 – B-MODEL-2: Budget-, Recovery- und Fehlervertrag vorbereitet (23.10.2026)

Ausschließlich statische Spezifikation in TESTING.md, kein weiterer Produktcode,
kein Windows-Adapter/Worker und keine neue Dependency-/Ausführungsfreigabe.
Das isolierte B-MODEL-1 mit historisch 74 erfolgreichen Tests bleibt unverändert;
jetzt keine Tests ausgeführt und keine zusätzliche Testanzahl behauptet.

Präzisiert: Start 30 s sowie Band/Reihe 120 s/60 min ab Admission, Startzeit
enthalten; Cleanup einmalig insgesamt 15 s. Speicher-/CPU-/IPC-Kandidaten noch
messpflichtig. Keine harte Windows-Gesamtprozesszahl, ActiveProcessLimit=3-
Diskrepanz weiterhin offen. Neuer statischer Kompatibilitätspunkt: bestehender
Validator erlaubt 30.032 Importeinträge, nicht 200.100; 32 MiB Ergebnislimit
deckt nicht nachgewiesen jede theoretische Maximalmap ab. Produzent-/Encoder-
Maximalpayload und Parent-/Workerheap vor Limitfreigabe separat prüfen.

Recovery bleibt fail-closed: verlorener Owner/Jobhandle, schmutzige/fehlende/
beschädigte/veraltete Recoverydaten oder unbestätigtes Gruppenende → gesperrt.
Neustart/neues Ticket/PIDscan ist kein Endbeleg. Vorgeschlagen sind bounded
lokale Recoveryinformationen mit bestätigtem Intent vor Start und Abschluss
nach C; Identität, Crashfestigkeit, Aktualität und Schutz gegenüber Worker noch
abzunehmen. Kontrollierte Wiederfreigabe nur mit vollständigem eigenen Endbeleg
oder separat bestätigter neuer Kernelboot-Baseline plus Eigentümerentscheidung;
kein automatischer Reset und kein neuer Hintergrunddienst.

HTTP-Vorschlag 429/500/503 konkretisiert, technische Bodys zunächst leer,
spätere feste lokale UI-Texte ohne Rohfehler und ohne Retry. Bestehende Erfolgs-
/JSON-/Security-/Sessionverträge unverändert. JNA-Core 5.17.0 als expliziter
reproduzierbarer Adapterkandidat empfohlen, keine neueste-/Sicherheitszusage;
Cachebestand ersetzt keine deklarierte Dependency. Separate Adapter-/Worker-/
manual-Runtimes und spätere Archivprüfung erforderlich.

Nächster eng begrenzter Auftrag nur nach Freigabe: isolierter Windows-Adapter
mit expliziter Core-Dependency und KernelCalls-Fakes, keine Produktanbindung
und keine echten nativen Starts. Quoten-/Crash-/Cleanup-/Firefox-/Recovery-/
IPC-Abnahmen weiterhin getrennt. Kein T02-Gesamtabschluss, SSRF und T03/T04/T06
nicht vorgezogen; sämtliche Muss-/Soll-/Kann-Prioritäten erhalten.



### T02 – B-ADAPTER-1: Fake-basierter Ressourcenadapter geprüft (23.10.2026)

Freigegebene erste Adapterstufe ausschließlich unter
src/test/java/de/mangalib/windowsadapter umgesetzt: KernelCalls-Port,
OwnedJobAdapter mit ticket-/scopegebundenem Ressourcenledger, FakeKernelCalls
und 45 deterministische Tests. Kein nativer Port und keine Produktverdrahtung.

Eindeutiger Besitz für Job/Prozess/Thread/Attributliste/Memory; Teilinitialisierung
bleibt verbucht, alte/fremde Permits greifen nicht auf aktuelle Ressourcen zu.
Endnachweis getrennt von Terminate-Erfolg, Fremdgruppen bleiben unberührt.
Alle Waits/Endbelege/Closes teilen die externe Cleanupdeadline; unklarer Close
wird nicht wiederholt, Primär-/Sekundärfehler bleiben erhalten. Adapter liefert
nur Bericht, keine eigene Admission-/Retry-/Shutdown-/Recoverypolitik.
PENDING ist eine Simulation, kein Beweis nativer Abbrechbarkeit.

Final **119 ausgewählte datenbankfreie Tests bestanden, 0 Fehler, 0 übersprungen**:
45 neue Adapterfälle und 74 unveränderte B-MODEL-1-Fälle. Identischer
Offline-Dry-Run vor jedem tatsächlichen Lauf; frühere Record-Compilefehler
korrigiert. Keine Vollsuite/DB-/Browser-/OS-Abnahme. Einzelheiten und Grenzen
einschließlich synchroner Fake-/späterer nativer Lockgrenzen stehen in TESTING.md.

JNA für diese Stufe nicht benötigt und nicht ergänzt; Build, Hauptanwendung,
manual-test, bestehende Modelle/Guards, Prototypen und historische Evidenzen
unverändert. Keine Aussage einer wirksamen Gesamtprozessquote: sechs simulierte
eigene Mitglieder bei Kandidat 3 sind lediglich ein Datenfall; die historische
ActiveProcessLimit-Diskrepanz bleibt offen.

Empfohlene nächste OS-Stufe nach gesonderter Bindings-/Dependency- und
Ausführungsfreigabe: Win64-Layout und genau eine leere private Jobgruppe,
null Prozessstarts, readback/empty Accounting und owned Close. Kein automatischer
Start eines vorhandenen Prototyps. Atomarer Prozessstart, Quoten/Nestedjobs,
Crash/Kill-on-close/Cleanup, Recovery, Worker/IPC und Offline-Firefox bleiben
eigene Freigaben. Kein T02-Gesamtabschluss; SSRF und T03/T04/T06 nicht vorgezogen,
fachliche Muss-/Soll-/Kann-Prioritäten unverändert.


### T02 – B-ADAPTER-2: native Leergruppenprüfung abgeschlossen (23.10.2026)

Isolierte Win64-Teilimplementierung unter src/jobAdapter und Tests unter
src/jobAdapterTest; keine Produkt-/Gateway-/Workeranbindung. Minimaler
Buildblock mit getrennten Source Sets/Tasks, keine Vererbung in main/manualTest.
Bisheriger Buildkörper und sämtliche Modelle/Fakes/Guards/Prototypen unverändert.

JNA-Core 5.17.0 aus vorhandener lokaler Datei mit festem SHA-256 gepinnt.
Koordinatenbasierte Offlineauflösung scheiterte an fehlenden Cachemetadaten;
deshalb transparenter lokaler Experimentpfad, keine Downloads/Repositoryänderung
und kein jna-platform. Keine zusätzliche neue Runtimeabhängigkeit der normalen
oder manuellen Anwendung. Reguläre Maven-/Verifikationsintegration vor späterem
Produktauftrag weiterhin gesondert zu lösen.

**147 ausgewählte Unit-Tests bestanden, 0 Fehler, 0 übersprungen:** 28 neue
Layout-/Fake-RawApi-Fälle plus 119 unveränderte tatsächlich erneut ausgeführte
Modell-/Adapterfälle. Keine vollständige Spring-/Integrationstestsuite.

Genau eine neue native Einmalfreigabe verbraucht:
B_ADAPTER2_EMPTY_20261023_77808c3d. Eine private leere Jobgruppe erstellt,
Kill-on-close 0x2000 zurückgelesen, Accounting total/active/terminated jeweils 0,
PID-Liste [], eigenes Close erfolgreich, Handles/Puffer jeweils 0,
Primär-/Cleanupfehler keine. Lokale consumed/trace/STOP-Evidenz bleibt erhalten,
kein zweiter Versuch. Null Workerstarts, Prozesszuordnungen oder Terminierungen.

Nachweis gilt nur für leere Gruppe/ABI/Readback/owned Close, nicht für
Kill-on-close-Wirkung auf aktive Nachfahren, Quoten, Workerstart, Crash,
Nestedjobs/Breakaway oder Firefox. Historische ActiveProcessLimit=3-Diskrepanz
weiterhin offen; neue Leergruppenprüfung setzte keine Prozessquote.

Nächster begrenzter Auftrag nur nach Freigabe: zusätzliche Start-/Identitäts-/
Attributbindings zuerst mit Fakes; anschließend separat abgesicherter einzelner
suspendierter synthetischer Prozessfall mit JOB_LIST und eindeutigem Cleanup.
Keine neue OS-/Worker-/Live-Freigabe aus diesem Ergebnis ableiten. T02 insgesamt
offen, SSRF und T03/T04/T06 nicht vorgezogen, Muss-/Soll-/Kann-Prioritäten erhalten.



### T02 – B-ADAPTER-3: Prozess-/Identitätsbindungen mit Fakes geprüft (23.10.2026)

Ausschließlich isolierte Win64-Adapterstufe: STARTUPINFOEX/PROCESS_INFORMATION/
FILETIME, Attributlisten/JOB_LIST, CreateProcessW/Resume und lesende
Prozessidentität/Wait-/Membership-/Accountingoperationen deklariert und über
injizierte Fakes geprüft. Fester atomarer suspendierter Startvertrag mit
explizitem Executable/CWD/Environment, Windows-Quoting und nicht vererbbaren
Handles; keine ProcessBuilder-/nachträgliche Zuordnungsfallbackroute.

Bestehenden OwnedJobAdapter für Tickets, Fähigkeiten, gemeinsame Cleanupfrist,
Teilhandles, späte Rückkehr und Quarantäne weiterverwendet. Vollständige
dynamische Mitglieder einschließlich Konsolenhosts bleiben identitätspflichtig.
Unklare Handles/Endbelege/Attributfreigaben sperren Abschluss; keine Slotfreigabe
aus PID, Namen, Cancel oder erfolgreichem Terminate allein.

**208 unterschiedliche ausgewählte Unit-Fälle bestanden, 0 Fehler, 0
übersprungen:** 61 neue Prozessbinding-Fälle plus sämtliche bisherigen
147 unveränderten Modell-/Adapter-/Layoutfälle frisch ausgeführt. Identischer
Offline-Dry-Run vor jedem echten Gradle-Lauf; kein nativer Prozessaufruf,
keine neue Jobgruppe, kein Worker und keine Spring-/DB-/Browserprüfung.

Keine native Prozessfactory oder Produkt-/Manual-Integration aktiviert.
Buildänderung nur ein Testinclude im isolierten Source Set, keine neue
Abhängigkeit; Adapter-Runtime weiterhin nur gepinnte lokale JNA-Core 5.17.0.
Main-/Manual-Quellen und Paketierung unverändert. Lesend geprüftes vorhandenes
Manual-JAR enthält keine neuen Adapterklassen/JNA 5.17.0, allerdings schon
transitive JNA 5.13.0. Vollständige JNA-freie positive Manual-Runtime-Allowlist
bleibt offen; in diesem Auftrag nicht nebenbei geändert.

Historische STOPs, verbrauchte Leergruppenfreigabe und exakte Drei-PID-Assertion
unverändert. Keine Quoten-/Nestedjobs-/Breakaway-/Crash-/Firefox-/SSRF-Wirkung
nachgewiesen; ActiveProcessLimit=3-Diskrepanz bleibt ungeklärt. Synchronisierte
Fake-Operationen/PENDING sind kein Nachweis nicht blockierender echter
Kernelaufrufe oder harter Timeouts. Native Ladeverdrahtung, Lock-/Rückmeldegrenzen und
unabhängig bedienbare Cleanuphoheit sind vor einem Prozessversuch abzusichern.

TESTING.md dokumentiert genau EINEN vorgeschlagenen späteren Versuch:
neue persistente Einmalfreigabe, ein synthetischer Root per atomarem JOB_LIST-
Start suspendiert, KEIN Resume und keine Nachfahren; Marker-/Instanznachweis,
bounded vollständige Population, eigene Gruppenterminierung und vollständiger
Cleanup unter gemeinsamer Frist, STOP bei Abweichung/Unklarheit. Nur geplant,
nicht implementiert/ausgeführt. Jede weitere Stufe benötigt ausdrückliche
Freigabe. T02 insgesamt offen; keine Arbeiten an SSRF/T03/T04/T06 und keine
Änderung fachlicher Muss-/Soll-/Kann-Prioritäten. Keine Git-Operationen.


### T02 – B-ADAPTER-4: Ladepfad vorbereitet, unabhängiger Cleanup blockiert OS-Start (24.10.2026)

Isolierte Win64NativeBindings-Vorbereitung und 44 neue deterministische
Loader-/Adapterfälle implementiert. Expliziter Windows-x64-/Java-21-/ABI-/
JNA-5.17.0-Vertrag, Overrideprüfung vor Dispatchinitialisierung, fester
C:\Windows\System32\kernel32.dll-Pfad für den bisherigen Experimenthost,
vollständige Prüfung der 18 Exporte, gemeinsame Calling-Convention-/
ClassLoader-Optionen und erhaltene Lade-/Cleanupfehler. Keine native
Lade-/Prozessoperation beim bloßen Initialisieren einer Klasse.

**252 unterschiedliche ausgewählte Unit-Fälle bestanden, 0 Fehler, 0
übersprungen:** 44 neue plus sämtliche 208 bisherigen unveränderten
Binding-/Adapter-/Modellfälle erneut ausgeführt. Identischer Offline-Dry-Run
vor jedem echten Gradle-Lauf. Reale JnaLoader.open-Methode nicht ausgeführt;
keine echte Kernel32-Ladeoperation, kein Job-/Prozess-/Resumeversuch.

**P0-Blocker für den geplanten suspendierten Einzelversuch bleibt:** unabhängiger,
vor Start nachgewiesener begrenzter Cleanup fehlt. Der bestehende Owner/Port
hält Monitore über nativen Aufrufen; Deadlinechecks und Kill-on-close bei
lebendem blockiertem Owner ersetzen keine unabhängige Ausführungshoheit.
Keine unsichere Watchdog-/Helperersatzlösung und kein startfähiger Prüfhost
implementiert. Einmalversuch ohne Resume weiterhin nur geplant und gesperrt.
Zuerst separat Reichweite/Architektur der unabhängigen Kontrolle,
Jobhandle-Eigentum, Bereitschaft, späte Starts und vollständigen Cleanup
einschließlich Kontrollhost bewerten/freigeben.

Bestehende Bindings/Modelle/Assertions/Guards, historische STOP-/consumed-/
Evidenzdateien und main/manual-Quellen unverändert. Buildänderung nur ein
Testinclude; keine neue Dependency, Versions-/Paketierungsänderung oder Task.
Adapter-Runtime weiterhin nur gepinntes lokales JNA-Core 5.17.0.
Vorhandenes Manual-JAR hashidentisch; transitive JNA 5.13.0 stammt laut lokalen
POMs aus WebDriverManager 5.7.0 -> docker-java-transport-httpclient5 3.3.5.
Keine Adapterklassen/JNA 5.17.0 darin. Positive Manual-Runtime-Allowlist bleibt
separat offen; keine eigenmächtige Bereinigung. Details, Testbefehle,
Nachweisgrenzen und Bedingungen der späteren Einzelfreigabe in TESTING.md.

T02 insgesamt offen; Prozessquote/ActiveProcessLimit-Diskrepanz, Nestedjobs,
Crash/Recovery, IPC, Firefox und SSRF nicht abgenommen. Kein Vorziehen von
T03/T04/T06, keine Änderung der Muss-/Soll-/Kann-Prioritäten. Keine
Git-/DB-/Anwendungs-/Browser-/Live-Scraperoperationen oder Downloads.


### T02 – B-ADAPTER-5: unabhängiger Kontrollpfad auf Komponentenebene (24.10.2026)

Der bisherige Java-Monitorblocker ist im isolierten Adapter entkoppelt:
kurze Zustands-/Aufrufreservierungen statt gehaltener Monitore über
Kerneloperationen. Vor configure/readLimits/Start bereitgestellte CleanupControl
auf den eigenen privaten Job, Nutzungstoken bis tatsächlicher Rückkehr,
nicht blockierendes Startstoppsignal und getrennt bedienbare Terminierung.
Keine PID-/Namenssuche, Handleduplikation oder konkurrierende Slotverwaltung.

Anforderung, Kontrollrückkehr, Gruppenende und Ressourcenabschluss bleiben
getrennt; Deadline/Cancel/Terminateerfolg allein geben nicht frei. Kontrollaufruf
und Handlefreigabe werden gegeneinander reserviert. Späte Ressourcen bleiben
owned, alte Tickets bleiben wirkungslos, keine automatischen Scraper-Retries,
keine Deadlineverlängerung. 15-s-Cleanupkandidat bleibt monotones gemeinsames
Callerbudget, kein hartes Abbrechen eines hängenden Kernelaufrufs.

**278 unterschiedliche ausgewählte Unit-Fälle bestanden, 0 Fehler, 0
übersprungen:** 26 neue begrenzte CountDownLatch-/Parallelitätsfälle und sämtliche
252 bisherigen unveränderten Tests/Assertions frisch ausgeführt. Identischer
Offline-Dry-Run vor jedem echten Gradle-Lauf. Keine echten Kernel32-/Prozess-/
Jobversuche. Bestehende Modelle, alte Tests, historische STOP-/consumed-/
Evidenzdateien, Produkt-/Manual-Quellen und Sicherheitsguards unverändert.
JNA 5.17.0, Source-Set-/Artefakttrennung und Paketierung unverändert;
Buildänderung nur zusätzlicher Testinclude.

**Erster suspendierter OS-Versuch weiterhin GESPERRT.** Komponentenunabhängigkeit
ist belegt, begrenzter vollständiger nativer Cleanup nicht: benutzte Handles
müssen offen bleiben, Kontrollaufruf kann selbst hängen, JNA-/Windows-interne
Sperren und späte Create-Ergebnisse sind nicht OS-seitig validiert. Ein lokaler
Kontrollpfad ersetzt keinen externen Watchdog bei JVM-/Supervisor-Ausfall.
Vor weiterer OS-Freigabe getrennt Ausfall-/Unabhängigkeitsmodell, gegebenenfalls
externen Kontrollhost/OS-Mechanismus, Handleübertragung und vollständige
begrenzte Endbelege absichern. Kein externer Dienst/Watchdog oder produktiver
Supervisor in dieser Stufe implementiert.

TESTING.md dokumentiert Lock-/Ownershipregeln, kontrollierte Fehlerfälle,
Befehle/Ergebnisse und verbleibende Grenzen. Kein T02-Gesamtabschluss,
Prozessquoten-/ActiveProcessLimit-Diskrepanz, Crash/Recovery, IPC und Firefox
weiter offen; keine SSRF-/T03-/T04-/T06-Arbeiten und keine Prioritätsänderung.
Keine Git-/DB-/Anwendungs-/Browser-/Live-Scraperoperationen oder Downloads.


### T02 – B-WATCHDOG-1: Kontrollhost-Entwurf, OS-Freigabe bleibt gesperrt (24.10.2026)

Ausschließlich statisch untersucht, keine Implementierung und keine Tests:
Die 278 erfolgreichen Fälle aus B-ADAPTER-5 bleiben historischer Prüfstand.
Java-Kontrollthread, geteilter Jobbesitz, vollständiger Kontrollhostbesitz und
alternative Kernel-/Debugger-/Dienstwege verglichen; ausführlicher Entwurf
im Nachtrag B-WATCHDOG-1 von TESTING.md.

Empfehlung zur gesonderten Freigabe: kurzlebiger Java-21-Kontrollhost H besitzt
privaten Workerjob J und dessen gesamten atomaren Start-/Cleanup-Lifecycle.
MangaLib S besitzt eine zusätzliche private äußere Jobgruppe K, die H und
sämtliche J-Nachfahren umfasst; S selbst bleibt außerhalb. H bleibt außerhalb
J. Keine J-Jobhandle-Kopie in S/Worker, keine K-Kopie in H/Worker; Kill-on-close
nur beim letzten gültigen OS-Handle. K erlaubt bei blockiertem H eine eigene
Gruppenterminierung und anschließende Endbeobachtung durch S. Kein Dienst,
Broker, fremder Ancestor-Kill oder PID-/Namens-Recovery vorgesehen.

Grenzen bleiben ausdrücklich offen: native Start-/Terminierungs-/Closehänger,
Late-create und Nestedjobs, vollständig unreaktive S/H/Kernelpfade, Verlust der
aktuellen Handles und authentischer Recovery-/Boot-/Aktualitätsnachweis.
Worker mit identischem uneingeschränktem Windows-Token ist keine vertrauenswürdige
Instanz: Capability-IPC/User-ACL allein verhindern keinen Host-/Journalzugriff.
Token-/Prozess-/Journalisolation vor entsprechender Sicherheitszusage separat
entscheiden. Harte 15-s-Beendigung unter beliebigem Kernelhänger nicht zusagbar;
keine rekursive weitere Watchdogebene und keine Recovery durch Appneustart.

Geplanter enger IPC-/Journalvertrag, gemeinsame vorläufige Fristen, K/J-
Eigentum, Hostausfälle, Handle-Allowlist und Endkonjunktion dokumentiert.
Zusätzliche Host-/K-Budgets und 32-MiB-Resultatkompatibilität noch abnehmen;
ActiveProcessLimit-Diskrepanz bleibt unverändert offen. manual-test bleibt
ausschließlich synthetisch, ohne H-/W-/Live-/Native-Startpfad.

Kleiner nächster Auftrag nach Freigabe: B-WATCHDOG-2 nur als reines
K/H/J-Vertragsmodell mit Fakekernel/-kanal/-journal und deterministischen
Fault-/Deadline-/Ownershiptests; keine Hostimplementierung/OS-/Produktintegration.
Danach separat IPC-Harness, Hostbootstrap, genau einmaliger suspendierter Root
ohne Resume, einzelne Crash-/Kill-on-close-/Timeoutprüfungen und zuletzt
Offline-Firefox abnehmen. Ein ausführender H ist NICHT durch die alte
Ein-Root-Planung mitfreigegeben.

**Erster suspendierter OS-Versuch weiterhin GESPERRT.** Erst unabhängigen
Kontrollweg und begrenzten eigenen Endnachweis implementieren/abnehmen,
offene Schutzannahmen entscheiden und neue ausdrückliche Einmalfreigabe
erteilen. Keine Änderungen an Adapter/Modellen/Tests/Build, historischen
STOPs/Freigaben/Evidenzen oder Produktcode. Kein T02-Gesamtabschluss, keine
SSRF-/T03-/T04-/T06-Umsetzung oder Änderung der Muss-/Soll-/Kann-Prioritäten.
Keine Git-/DB-/SQL-/OS-/Anwendungs-/Browser-/Scraperoperationen oder Installationen.


### T02 – B-WATCHDOG-2: isoliertes Vertragsmodell datenbankfrei geprüft (24.10.2026)

Neues sequenzielles K/H/J-Lifecycle-, Kanal-/IPC- und Recoverymodell ausschließlich
unter src/test/java/de/mangalib/watchdogmodel. Keine ausführbare H-/W-Implementierung,
keine Win32-Bindung oder Produktintegration. S besitzt K, H besitzt J; kein
Cross-Role-Jobhandletransfer, H in K/außerhalb J, W samt Nachfahren in J/K als
ausdrückliche Fake-Annahme. Ein Auftrag, opaque Tickets/Kanäle, kein Retry.

85 neue deterministische Fälle prüfen Startreihenfolge, Handshake, konkrete
Kanal-/Senderbindung zusätzlich zum Ticket, Version/Sequenz/Replays, Byte-/
Feld-/Tiefenbudgets, dynamische Population, Teil-/fremde Endbelege, Late-create,
H-/S-Ausfälle, J->K-Eskalation, gemeinsame Fristen, Closekopien/UNCERTAIN,
Intent/Revision/Journalfehler und Restart-Sperre. Terminierungsantwort ist
kein Ende. Später belegter Ressourcenabschluss heilt keine Quarantäne.
32-MiB-Resultat bleibt Messkandidat, theoretische Größenkompatibilität offen.

**Final 204 Fälle bestanden, 0 Fehler, 0 übersprungen:** 85 neue plus 119
unveränderte bisherige reine Modell-/Adapter-Fakefälle. Vor jedem Testlauf
identischer Offline-Dry-Run. Sämtliche 278 bisherigen Tests/Assertions
unverändert; die 159 bisherigen JNA-Binding-/Memory-/Layoutfälle wegen des
aktuellen Verbots echter JNA-Aktionen nicht erneut ausgeführt und nur als
historischer Prüfstand geführt. Keine addierte Behauptung frischer 363 Tests.
Ein Zwischenlauf hatte einen korrigierten neuen Testhelferfehler, Details und
finale XML-Zahlen in TESTING.md; keine alte Assertion geändert.

Keine Build-/Source-Set-/Dependencyänderung: neues Paket bleibt ausschließlich
test, gelangt weder in Hauptanwendung noch manual-test. Historische Quellen/
Tests/Evidenzen, STOPs, verbrauchte Freigaben und Manual-JAR per SHA erhalten;
Dokumentation nur ergänzt. Kein neuer nativer oder Live-Startpfad.

**Suspendierter Windows-Prozessversuch weiterhin GESPERRT.** Native
Gruppenvererbung, Late-create-/Crash-/Cleanupwirkung, unabhängige reale Kontrolle,
Jobkopien/Kernelhänger, Same-Token-Isolation und authentische Recovery sind
weiter separat nachzuweisen. Quarantäne-/Baselinebelege im Fake sind keine
Windows-/Durabilitygarantie.

Vorgeschlagener nächster Auftrag B-WATCHDOG-3A nach Freigabe: ausschließlich
in-process Bytecodec-/Kommunikations-Harness mit deterministischer
Fragmentierung/Backpressure, ohne zusätzliche JVM, echte IPC/Dateien/Win32.
Echte lokale H-/S-Prozesse erst später separat samt sicherem Endweg freigeben.
Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung und keine
Änderung der fachlichen Muss-/Soll-/Kann-Prioritäten. Keine Git-/DB-/SQL-/
OS-Prozess-/Anwendungs-/Browser-/Scraperoperationen oder Installationen.


### T02 – B-WATCHDOG-3A: Bytecodec und Speicherkommunikation geprüft (24.10.2026)

Binären V1-Codec und deterministischen In-Memory-Harness ausschließlich im
Testpaket ergänzt: fester 96-Byte-Big-Endian-Header, explizite Typcodes,
vollständige 128-Bit-Session-/Ticket-/Kanal-/Senderidentitäten, striktes UTF-8,
fragmentiertes Decoding und dauerhaft fehlerhafte Sitzungen bei Protokollbruch.
Kein Java-Objektdecoder, keine dynamische Klassenauflösung oder neue Dependency.

Feste Ringe, höchstens ein reservierter Encoder pro Richtung und gemeinsame
globale/kanalbezogene Pufferbudgets verhindern unbeschränkte Warteschlangen.
Datenbytes werden an einen expliziten Streamingempfänger gegeben; nach Fehlern
vorläufige Bytes verwerfen, kein stiller Datenverlust oder automatischer Replay.
Frame-Limit und Speicherquote müssen zusammenpassen; gehaltene Stringquelle und
späterer fachlicher Payloadspeicher sind keine vollständige JVM-Heapgarantie.
32 MiB bleiben konfigurierbarer Codec-Messkandidat, keine bewiesene allgemeine
Importgrenze. Vorhandenes WatchdogModel behält seinen Kandidaten unverändert.

WatchdogWireBridge delegiert Lifecycle-/Sequenz-/Sender-/Zustandsprüfung an das
unveränderte B-WATCHDOG-2-Modell. Registry und Kanalobjekte sind Fakebindungen,
KEINE reale Windows-Peer-Authentisierung. Same-Token-Schutz weiter offen.

**296 Tests bestanden, 0 Fehler, 0 übersprungen:** 92 neue (63 Codec, 29 Harness)
und alle 204 bisherigen reinen Watchdog-/Modell-/Adapter-Fakefälle unverändert
erneut ausgeführt. Vor jedem Test identischer Offline-Dry-Run. 159 JNA-Fälle
nicht ausgeführt; Quellen/Assertions erhalten. Sieben neue Testdateien, keine
Build-/Produkt-/Manual-Quellen- oder Artefaktänderung. Historische Inhalte,
STOPs, Freigaben und Evidenzen unverändert; Details/Hashes in TESTING.md.

Offener Anschlussblock: Das bestehende Modell behandelt aktives EOF weiter
konservativ als Fehler. Deshalb KEINE erfolgreiche vollständige Host-/I/O-
Endbarriere aus sauberem Byte-EOF abgeleitet. Vor einem ausführbaren Host
erwartetes/unerwartetes EOF und dessen Endnachweise separat entscheiden.
Auch ein echter budgetierter Payload-/Mapconsumer, reale IPC-/Peerbindung,
Recovery-Durability, Windows-Nesting und Crash-/Cleanupwirkung bleiben offen.

B-WATCHDOG-3B nur nach gesonderter Freigabe: zunächst EOF-Vertragspräzisierung,
danach höchstens ein isolierter synthetischer Java-Kommunikationshost ohne
Win32-/Job-/Workerstarts, mit festen Grenzen und VORHER geprüftem geordnetem
Shutdown. Jetzt kein Host, echte IPC oder OS-Versuch implementiert/ausgeführt.
**Suspendierter Windows-Prozessversuch weiterhin GESPERRT.**
Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung oder Änderung
der Muss-/Soll-/Kann-Prioritäten. Keine Git-/DB-/SQL-/Anwendungs-/Browser-/
Scraperoperationen, Downloads oder Installationen.

### T02 – B-WATCHDOG-3B-1: Abschluss-/EOF-Vertrag geprüft (24.10.2026)

Der zuvor unter B-WATCHDOG-3A offene Kommunikationsabschluss ist jetzt im
isolierten Modell und Speicherharness definiert und geprüft. Historische
Berichte bleiben erhalten. Kein ausführbarer Host oder echter IPC-/OS-Versuch.

Explizites V1-Abschlussprofil: Code 23 CHANNEL_END mit exakt leerer Payload,
96-Byte-Header und bisherige Typcodes unverändert. Alte V1-Peers lehnen den
neuen Typ ab; keine Mischkompatibilität behauptet. Ein späterer Host muss
identische Profile verlangen oder eine separat geprüfte Vereinbarung nutzen.

Vier gerichtete Kanalbelege: W→H nach vollständigem Resultat, H→W nach dessen
EOF; nach Workerexit, J-End-/Ressourcenbelegen S→H und zuletzt H→S. Jeweils erst
CHANNEL_END, dann fehlerfreies Boundary-EOF. EOF ist ausschließlich ein
Kanalbeleg; unabhängig erforderliche J-/K-Endbelege, Handles, alle IoEnd-
Komponenten und Journalabschluss bleiben verbindlich. Der Wirepfad fordert
alle vier EOFs zusätzlich zum abstrakten IoEnd-Observer. Kein zweiter Lifecycle.

Frühes/trunkiertes EOF, Abbruch, Codecfehler, Duplikate und späte Frames bleiben
konservativ; keine Quarantäneauflösung/automatische Wiederholung. Die erste
Cleanupdeadline bleibt unverändert. Typisierter Transportendgrund verhindert
Verwechslung von sauberem EOF und Fehler/Abbruch. Erster Workerexitbefund ist
unveränderlich. Ergebnisfreigabe erst bei erfüllter Gesamtbarriere.

**338 Tests bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen 296 Fälle
erneut geprüft plus 41 Abschlussfälle und ein zusätzlicher Codec-Signalfall.
Einziger angepasster historischer Ablehnungstestvektor: unbekannter Typ 23
(jetzt belegt) → 24, mit identischer Sicherheitsassertion. Alle übrigen
historischen Assertions erhalten; Rückruf-Testhilfen typisiert angepasst.
159 JNA-Tests unverändert/nicht ausgeführt. Jeder Lauf mit identischem Offline-
Dry-Run, nur normale Gradle-/JUnit-JVMs. Details und Entwicklungsläufe: TESTING.md.

Nur Testmodell-/Codec-/Harnessquellen und Dokumentnachträge geändert. Produkt-/
Manual-Quellen, Artefakttrennung, bestehendes Manual-JAR, Guards, historische
Evidenzen und Sperrmarker unverändert. Kein Git-Zugriff; Hash-/Whitespaceprüfung.

B-WATCHDOG-3B-2 bleibt separat freizugeben: erster isolierter synthetischer
Java-Kommunikationshost mit dedizierten Standardkanälen, identischem Profil,
festen Ressourcen-/Zeitgrenzen und vorab überprüftem geordnetem Endweg; noch
ohne Win32/Jobs/Worker/Firefox. Keine Implementierung/Prozessausführung jetzt.
Reale IPC-/Peer-/Tokenbindung, Journaldurability und echtes Prozesscleanup offen.
**Suspendierter Windows-Prozessversuch weiterhin GESPERRT.** Kein T02-
Gesamtabschluss; SSRF/T03/T04/T06 und Muss-/Soll-/Kann-Prioritäten unverändert.

### T02 – B-WATCHDOG-3B-2: Einmaliger stdio-Kommunikationshost geprüft (25.10.2026)

Separat freigegebener synthetischer Java-21-Host mit unverändertem 96-Byte-
V1-Codec und explizitem END23-Profil. Fester Handshake, genau eine feste kleine
Nachricht/Antwort und beidseitiger CHANNEL_END-/EOF-Abschluss über eigene
Standardstreams; keine Listener, Named Pipes oder externen Abrufe.

Eigene Source Sets communicationHost/communicationCheck, explizite Codec-
Quellenallowlist, keine Abhängigkeit zu main/manualTest/jobAdapter. Host ohne
Spring/JPA/JDBC/JNA/Selenium und ohne Prozessstartcode. Getrennte Task
communicationProbeOnce, niemals automatisch unter test/check/build oder
assembleManualTest. Kein allgemeiner Remote-Execution-/Worker-/Importpfad.

Festes Java-Executable und geprüfter Klassenpfad mit Hashmanifest, geleerte
Kindumgebung mit vier lokalen Basiseinträgen, feste Argumente/CWD, Heap-/Byte-
und monotone Fristen. Endweg zuerst mit Fakes geprüft: eigenes Process-Objekt,
normaler Protokoll-/Exitabschluss, begrenzte eigene Abbruchversuche und STOP bei
unbestätigtem Ende. Host besitzt zusätzlich einen eigenen 8-s-Endweg.
Keine PID-Suche, globale Bereinigung, zweite Hostinstanz oder Wiederholung.

**338 bisherige Tests unverändert erneut bestanden, 44 neue Unit-/Fakefälle
und genau ein realer Kommunikationsfall bestanden: 383, 0 Fehler, 0 übersprungen.**
Jeder tatsächliche Lauf mit identischem Offline-Dry-Run. 159 JNA-Tests
unverändert/nicht ausgeführt. Keine vollständige DB-Integrationstestsuite.

Einmalversuch 25.10.2026, Host-PID 54764: Handshake/Nachricht/Antwort/Endmarker/
EOF erfolgreich, Prozessende mit Exitcode 0 bestätigt, eigene I/O-Threads und
Streams abgeschlossen, stderr 0 Bytes. Kein Abbruch erforderlich, Status PASS.
Neuer lokaler Marker .gradle/t02-watchdog-3b2-2e4b0772/consumed vor Start mit
CREATE_NEW/force verbraucht und dauerhaft gesperrt; result.txt dokumentiert
den Befund. Details/Identitäten/Hashes und Grenzen in TESTING.md.

Kein Nachweis nativer Prozesshandle-/Job-Ownership, beliebiger Nachfahren,
Same-Token-Isolation, Peer-Authentisierung, OS-Hard-Realtime oder vollständigen
Harnessausfalls. EOF/Exit ersetzen niemals die vollständige Watchdog-C-Barriere.
Produkt-/Manual-Quellen, bestehendes Manual-JAR, Testguards und historische
Evidenzen unverändert. Dokumentpräfixe per Hash erhalten, kein Git-Zugriff.

Nächste Stufe nur nach gesonderter Freigabe: stdio an vorhandene K/H/J-Ports
binden, zunächst mit Fakes; später eng begrenzter atomarer nativer H-Start in
eigener K und H-eigene leere J, noch ohne Firefox. Kein produktiver Workerstart
über ProcessBuilder. **Suspendierter Windows-Prozessversuch bleibt GESPERRT.**
Kein T02-Gesamtabschluss; SSRF/T03/T04/T06 und Muss-/Soll-/Kann-Prioritäten
unverändert. Keine DB-/SQL-/Anwendungs-/Browser-/Scraper-/Win32-/Git-Operationen,
Downloads oder Installationen. Keine weiteren Hoststarts nach diesem Versuch.

### T02 – B-WATCHDOG-4A: Kommunikations-/K/H/J-Fakeintegration geprüft (25.10.2026)

LifecycleCoordinator verbindet den vorhandenen Codec-/WatchdogWireBridge-/
MemoryIpcLink-Pfad mit WatchdogModel, FakeJobKernel und FakeRecoveryJournal.
Keine zweite Admission-, Cleanup-, Frist- oder Recoverymaschine. Alle Starts,
Endereignisse und Belege sind ausschließlich prozessinterne Fakes.

Intent vor Gruppenanlage/Resume, atomare simulierte K-/J-Zuordnung, bestätigte
H-Mitgliedschaft vor J-Anlage, getrennte S-/H-Handleownership und dynamische
J-Nachfahren in K testgesichert. Nachrichten-, Kanal-, Prozess-/Gruppen-,
Ressourcen- und Journalende bleiben getrennt; Slotfreigabe ausschließlich durch
die vorhandene vollständige Modellbarriere. Incident-retire ist keine Recovery.

Minimale Ergänzungen: aktueller Intent-/Handlecheck vor Startschritten,
identifizierter H, direkte Gruppenberechtigung und Prüfung gestoppter Vorfahren
beim Resume; injizierbare J-/K-Terminierungsfehler mit sticky Quarantäne und
ohne Wiederholung desselben fehlgeschlagenen Ports. Cleanupdeadline unverändert.
Separates Fakeprofil --synthetic-stdio-v1-end23:K-H-J kennzeichnet die zusätzlichen
Gruppenmeldungen; Header/Typcodes unverändert. Historischer stdio-Host bleibt
unverändert und wird nicht stillschweigend als kompatibler Lifecyclepeer verwendet.
Bootstrap-Session explizit injiziert, keine behauptete reale Peer-Authentisierung.

**442 ausgewählte datenbankfreie Tests bestanden, 0 Fehler, 0 übersprungen:**
alle bisherigen 382 Unitfälle mit unveränderten Testquellen/Assertions sowie
60 neue gekoppelte Lifecycle-/Ownership-/Fehlerfälle. Jeder Lauf mit identischem
Offline-Dry-Run. 159 JNA-Tests unverändert/nicht ausgeführt. Keine Vollsuite/DB-
Integration und kein weiterer echter Kommunikationsversuch.

Eigener Source Set lifecycleCheck und explizite Task watchdogLifecycleTest,
ohne nativen/realen Startadapter oder Probe-Harness. Keine neuen Libraries,
Produkt-/Manual-Integration oder Paketierungsänderungen. Vorhandenes Manual-JAR,
Guards, native Adapter und historische Evidenzen/consumed-Marker unverändert.
Dokumente nur ergänzt; Hash-/Artefakt-/Whitespaceprüfung ohne Git. TESTING.md
enthält Ablauf, Vertragspräzisierungen, Tests und verbleibende Grenzen.

Kleinste spätere ausführende Stufe nur als Vorschlag: S-eigene K und exakt
ein synthetischer H mit atomarer K-Zuordnung vor Resume, ohne J/Worker/Firefox,
vorbereitetem unabhängigem Endweg, festem Einmalmarker und strikter Endbarriere.
Zuerst nativen Adapter und Endweg reviewen/fakeprüfen, dann neue ausdrückliche
Einmalfreigabe einholen. H-eigene leere J und W erst in weiteren Stufen.
Kein ProcessBuilder-Fallback. Reale OS-/IPC-/Crash-/Durabilitybeweise stehen aus.

**Suspendierter Windows-Prozessversuch bleibt GESPERRT.** Auch der neue K→H-
Vorschlag ist nicht freigegeben. Keine Host-/Win32-/OS-IPC-/DB-/SQL-/Anwendungs-/
Browser-/Scraper-/Git-Operationen, Downloads oder Installationen. Kein T02-
Gesamtabschluss; SSRF/T03/T04/T06 und Muss-/Soll-/Kann-Prioritäten unverändert.


### T02 – B-WATCHDOG-4B-1: H-in-K-Startadapter mit Fakes vorbereitet (25.10.2026)

Neuer isolierter ControlHostStartAdapter nutzt den unveränderten OwnedJobAdapter
für Handleledger und Cleanup; HostProbeLifecycle bindet dieselben zentralen
Lifecycle-/Intent-/Deadlineentscheidungen an. H-only-Prüfumfang im bestehenden
Modell statt zweiter Zustandsmaschine: einmalige Intent-Startreservierung,
K-Aufbau/readback, suspendierter/identifizierter H, Resumefreigabe, stdio-
Profil/CHANNEL_END, unabhängige Root-/K-/IPC-/Ressourcen-/Journalendbarriere.
Keine J-Anlage/W-Starts; Prüfslotabschluss ist kein produktiver Importerfolg.

Fakeport verlangt atomare JOB_LIST-Zuordnung, Suspended-Start, vorbereiteten
unabhängigen Cleanup und eine restriktive symbolische stdio-Liste ohne K-Kopie.
Instanzbindung umfasst gehaltene Capability, PID, Erstellungszeit und Image;
späte Rückgaben werden nie resumed. Kein zweiter K-Aufbau zum selben Intent,
keine Handlefreigabe unter laufenden Calls, kein Cleanupretry/neue Frist und
keine Freigabe bei ungeklärtem Ende. Normale abgeschlossene Berichte idempotent.

**483 ausgewählte Tests bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen
442 Fälle mit unveränderten Testquellen/Assertions plus 41 neue Adapterfälle.
Jeder Lauf mit identischem Offline-Dry-Run. 159 JNA-Tests unverändert/nicht
ausgeführt, keine echte Probe wiederholt. Details und Grenzen in TESTING.md.
Eigener Source Set hostStartCheck und reine Testtask controlHostStartUnitTest;
keine native Factory/Probe-/Produktintegration, neue Bibliothek oder JNA-Änderung.
Produkt-/Manual-Quellen, vorhandenes Manual-JAR, Guards und historische Evidenzen
unverändert. Dokumente nur ergänzt, Hash-/Artefaktprüfung ohne Git.

**Echter K/H-Versuch noch NICHT freigabereif.** Vorhandene Win64-Bindings haben
JOB_LIST/Suspended/Identitäts-/Kontrolloperationen, aber keinen fertigen nativen
stdio-/HANDLE_LIST-Startpfad. Zusätzlich bleiben reale Intent-Durability und
ein hinreichend unabhängiger Endweg bei blockiertem Start/Control/Harnessausfall
offen. Der Fake reproduziert: Terminate während blockiertem Start kann einer
späten Prozessanlage vorausgehen und beweist deren Ende nicht. Slot bleibt dann Q.
Ein Kontrollthread allein löst diesen Blocker nicht.

Nächster Schritt ausschließlich nach neuer Freigabe: native stdio-/Vererbungs-/
Exitcode- und feste Manifestbindung sowie Intent-/Cleanup-Endweg weiter technisch
vorbereiten und fakeprüfen. Erst bei geschlossenen Voraussetzungen einen exakt
begrenzten OS-Einmalauftrag mit neuem Marker/STOP-/Endbelegen vorschlagen.
Kein ausführbarer B-WATCHDOG-4B-2-Versuch jetzt vorbereitet oder autorisiert.
**Früherer suspendierter Worker- und künftiger K/H-Versuch bleiben GESPERRT.**
Kein T02-Gesamtabschluss; SSRF/T03/T04/T06 und Muss-/Soll-/Kann-Prioritäten
unverändert. Keine ausführenden Win32-, Host-, DB-/SQL-, Anwendungs-/Browser-/
Scraper-/Git-Operationen, Downloads oder Installationen.

### T02-Nachtrag 25.10.2026 – B-WATCHDOG-4B-PREP

Historische Stände oben bleiben erhalten. Neue isolierte Vorbereitung für die
weiter gesperrte atomare K/H-Probe; keine Produkt-/Manual-Integration und keine
Änderung der Muss-/Soll-/Kann-Prioritäten.

- Native stdio-Vorbereitung implementiert und ausschließlich fakegeprüft:
  drei anonyme Pipes, exakte Child-HANDLE_LIST ohne K, nicht vererbbare Parents,
  zwei STARTUPINFOEXW-Attribute (JOB_LIST und HANDLE_LIST), Standardhandles,
  getrennte Ressourcenledger, bounded stdin/stdout/stderr und echter Exitcode am
  gehaltenen signalisierten Root-Handle. Kein ungeschützter Startfallback.
  Blockierte I/O hält keinen Cleanupmonitor; tatsächliche synchrone Native-
  Rückkehr ist damit ausdrücklich NICHT zeitlich garantiert. EOF ist kein Gruppenende.
- Neuer isolierter Intentstore: neue private Laufordner, immutable Revisionen,
  Ticket/Session/K-Gruppen-ID/Revision/Hashkette, force(true), ATOMIC_MOVE ohne
  Fallback und Readback. Crash-/Recoveryfehler bleiben gesperrt; ein gelesener
  Record erteilt nie Startfreigabe. Windows-Powerloss-/Directory-Durability NICHT
  nachgewiesen; keine historischen Journale übernommen/überschrieben.
- StartLeaseGuard bereitet Custodian-Schnittstelle vor: einmalige Create-/Resume-
  Berechtigung, Widerruf vor Storearbeit, keine Freigabe spät zurückkehrender
  Starts, nur eigene Capabilities und keine automatische Heilung nach Fehlern.
  Kein zweiter Lifecycleautomat und keine selbst erfundenen Endbelege.
  Konkreter unabhängiger Cleanup-/Ownercrashpfad weiterhin NICHT implementiert;
  der Java-Fake ersetzt weder Harnesscrashschutz noch OS-Endnachweis.

**539 ausgewählte datenbankfreie Tests bestanden, 0 Fehler, 0 übersprungen:**
483 bisherige unveränderte Unit-/Fakefälle erneut erfolgreich plus 56 neue
stdio-/Intent-/Leasefälle. Vor jedem tatsächlichen Gradlelauf identischer
Offline-Dry-Run. 159 historische JNA-Tests NICHT erneut ausgeführt, keine echte
Probe wiederholt. Entwicklungstestkorrekturen und Detailmatrix in TESTING.md.
Eigener hostPrepCheck-Source-Set/Testtask, nur vorhandene lokal gepinnte JNA-/
JUnit-Module; kein zusätzlicher OS-Ausführungstask oder neue Dependency.
Produkt-/Manual-Artefaktgrenzen, Guards, historische Marker/Evidenzen und
Dokumentpräfixe unverändert; Hash-/Artefaktprüfung ohne verbotene Git-Operationen.

**Echter K/H-Versuch weiter NICHT freigabereif.** stdio-Code ist vorbereitet,
Intent-Persistenz im Java-Dateivertrag umgesetzt, aber keine reale Windows-
Durabilitygarantie; unabhängiger Cleanup nur als Schnittstelle/Fake vorbereitet.
**Kleinste nächste Maßnahme:** Den vorbereiteten Custodian für genau eine private
K/H-Sitzung konkret unabhängig vom startenden Harness implementieren und zunächst
fakeprüfen: eigener K-Kontrollbesitz, unabhängige Ownerende-Erkennung, sichere
Übernahme spät zurückkehrender Root-Handles und Endbarriere bei blockiertem
Start/Control/I/O. Kein Namens-/PIDkill, kein Restart, keine angenommene Sperrwirkung
vorgezogener K-Terminierung. Keine neue breite Architekturphase oder Ausführungs-
freigabe daraus ableiten. Weitere offene OS-/Durability-/Manifestnachweise bleiben
sichtbar; Details in TESTING.md.

**Suspendierter Worker- und K/H-Versuch bleiben bis zur gesonderten Freigabe
GESPERRT.** Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung.
Keine ausführenden Win32-/Job-/Host-/Worker-/Browser-/Scraperoperationen,
DB/SQL, Anwendung/HTTP, Netzwerkzugriffe, Downloads/Installationen oder Git.
### T02-Nachtrag 25.10.2026 – B-WATCHDOG-4C: Custodian-Kern fakegeprüft

Neuer isolierter OuterJobCustodian für genau eine private K/H-Sitzung; kein main,
Prozess-/Native-Port oder produktiver Supervisor. Bestehende Admission, Quarantäne
und Slotfreigabe bleiben ausschließlich beim WatchdogModel. Produkt-/Manual-
Integration und sämtliche echten OS-/Host-/Workerstarts unverändert gesperrt.

**Explizite minimale Vertragserweiterung zu B-WATCHDOG-1:** S erstellt K und hält
weiter seinen Launch-/Ledgerhandle; C erhält zusätzlich eine separat bestätigte,
nicht vererbbare Kontrollkopie desselben K (QUERY | TERMINATE, kein ASSIGN/SET).
C beobachtet eine vorab gehaltene konkrete Owner-Prozessinstanz, keine PID-Suche.
H/Worker bekommen keine K-Kopie. S/C schließen nur eigene Kopien. Kill-on-close
wird erst am letzten gültigen OS-Handle wirksam und ersetzt keinen Endbeleg.
Historische S-Ownership-/K/H/J-Modelle und ihre Assertions bleiben unverändert;
die neue physische Kontrollkopie ist ein eigener isolierter Vertrag, keine
heimliche Änderung der alten Modelle oder ihrer Freigaben.

Lease-/Intentbindung, einmalige Übernahme, Owner-Signal, IPC-/Progressverlust,
Late-create und Cleanup koordiniert: Widerruf bleibt gesetzt; Terminierungs-
antwort, vollständiges Gruppenende und Ressourcenabschluss getrennt. Eine frühe
Terminierung sperrt spätere Prozessanlage NICHT. Späte eigene Root-Handles nur
mit bestätigter Instanz-/Rechte-/Jobbindung; unklare Transfers bleiben gesperrt.
Gemeinsame unveränderliche Cleanupfrist; kein automatischer Retry oder Reset.
Ende verlangt insbesondere geklärte Starts, geschlossene S-Kopien und Launch-/
I/O-/Kanalressourcen. Ownerende allein reicht dafür ausdrücklich nicht.
Alle Port-Aufrufe außerhalb kurzer Java-Monitore; keine Slots im Custodianbericht.

**602 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen:** 539 bisherige
unveränderte Unit-/Fakefälle erneut ausgeführt plus 63 neue Custodianfälle.
Identischer Offline-Dry-Run vor jedem tatsächlichen Gradlelauf. Die 56 bestehenden
JNA-Speicher-/Layoutfälle mit ausdrücklicher zusätzlicher Eigentümerausnahme,
kein Kernel32-/Win32-Aufruf. Neuer Custodian-Testpfad nachgewiesen ohne JNA-JAR.
159 historische JNA-Tests NICHT wiederholt, keine historische Einmalprobe.
Details, Kommandoumfang und Grenzen im neuen TESTING.md-Nachtrag.

Eigener custodianCheck-Source-Set/Testtask, zwei neue isolierte Quellen und nur
bestehende JUnit-Abhängigkeiten. Keine Produkt-/Manual-/Guard-/Modelländerungen;
Dokumente ausschließlich ergänzt und historische Evidenzen erhalten. Hash-/
Präfix-/Artefaktprüfung ohne Git; vorhandenes Manual-JAR unverändert.

**Kern für die nächste isolierte Port-/Bootstrapimplementierung vorbereitet,
aber kein ausführbarer unabhängiger Prozess. K/H- und Worker-Versuch GESPERRT.**
Same-JVM-Fakes beweisen keinen vollständigen JVM-Crashschutz. Reale reduzierte
Handleduplikation, S-/C-Lebensdauer/Peerbootstrap und sichere native Startfence
bleiben kleinste konkrete Ausführungsvoraussetzungen. Bei C-Ausfall und noch
gehaltenem S-K-Handle ist Kill-on-close nicht zugesagt. Native Hänger,
Windows-Durability und Same-Token-Isolation bleiben sichtbare Restrisiken.

**Genau eine vorgeschlagene Folgestufe, nicht freigegeben:** B-WATCHDOG-4D-EMPTY:
fester isolierter Bootstrap für ein synthetisches S/C-Paar und eine LEERE private
K-Gruppe; zuerst konkreten Transfer-/Wait-/Query-/Close-Port und festen Peer-Handshake
fakeprüfen, danach nur mit neuer ausdrücklicher OS-Einmalfreigabe kontrolliertes
Ownerende und unabhängige C-Kontrolle samt Accounting-/Handleende nachweisen.
Kein H, J, Worker, Browser oder Import; keine Übertragung historischer Marker.
Ein Erfolg wäre noch keine Late-create-/K/H-Abnahme. Keine weitere Umsetzung jetzt.

Keine Git-/DB-/SQL-/Anwendungs-/Listener-/Scraper-/Browseroperationen, echten
Win32-/Versuchsprozessstarts, Downloads/Installationen oder Netzwerkzugriffe.
Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung und keine Änderung
der Muss-/Soll-/Kann-Prioritäten.
### T02-Nachtrag 25.10.2026 – B-WATCHDOG-4D-1: S/C-Bootstrap für leeres K

Isolierten EmptyCustodianBootstrap im vorhandenen custodianCheck-Bereich ergänzt:
eine synthetische Sitzung, kein main/Prozessstart oder H-/J-/W-Pfad. Bestehende
KernelCalls und OuterJobCustodian wiederverwendet, keine neue Win32-Bindung.
Ticket/Session/Intent-Publikation -> privates K -> Konfiguration/Readback/leeres
Accounting -> reduzierte eigene C-Kopie -> instanz-/revisionsgebundenes einmaliges
Ack mit gehaltenem lebendem S und C -> simulierter READY-Checkpoint. READY ist
keine H-Startberechtigung und keine Admission/Slotentscheidung.

S-Launchhandle und C-Kontrollkopie bleiben getrennt. Close einer beliebigen Kopie
beweist bei noch offener anderer Kopie weder Kill-on-close noch K-Ende; beide
Richtungen fakegeprüft. Extrakopien/Vererbung oder unklare Inventare verhindern
Abschluss. Gruppenende, S-/C-Kopien, Kontrollressourcen, gehaltenes C-Ende und
Journalterminal werden getrennt bestätigt. Kein stiller Cleanupretry oder Reset.
Unbekannter S-Close bleibt UNCERTAIN; bestätigtes S-Ende ist etwas anderes als
Kommunikationsverlust. Erste gemeinsame Cleanupfrist wird nur verkürzt (minimaler
stopBy-Zusatz im C-Kern), nie erneuert. Kein fremder PID-/Jobzugriff.

**646 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen:** alle 602 bisherigen
Fälle mit unveränderten Assertions erneut ausgeführt plus 44 neue Bootstrapfälle.
Vor jedem Gradlelauf identischer Offline-Dry-Run. custodianUnitTest jetzt 107
Fälle (63 + 44), weiterhin ohne JNA-JAR. Die 56 vorhandenen Speicher-/Layoutfälle
vorher statisch als unveränderte Raw-/Loader-Fakes geprüft und nur im ausdrücklich
erlaubten lokalen JNA-Speicherumfang ausgeführt, keine Kernel32-/Win32-Aufrufe.
159 historische JNA-Tests und alle OS-Einmalversuche NICHT erneut ausgeführt.
Entwicklungsfehler des neuen Fake-Endadapters und Detailmatrix in TESTING.md.

Produkt-/Manual-/Guard-/alte Testquellen unverändert. Zwei neue isolierte Quellen,
vorhandenen Testfilter erweitert; keine Dependency, Source-Set-/Produktintegration
oder ausführende Task hinzugefügt. Historische Dokumentpräfixe/Marker/Evidenzen
und bestehendes Manual-JAR erhalten; Hash-/Artefaktprüfung statt Git.

**Echter S/C-LEER-Versuch weiterhin NICHT ausführungsreif.** Ein eigener fester
C-Prozessbootstrap, native gehaltene S-/C-Instanzen, DuplicateHandle-Übernahme und
reale gebundene IPC-/Endbelege sind noch nicht implementiert. Der historische
stdio-SyntheticHost ist kein unabhängiger C. Leeres K belegt keine Nachfahren,
Nestedjobs oder Late-create-Sicherheit. S-/C-Vollausfall, blockierte Kernelcalls,
Windows-Durability und Same-Token-Zugriffe bleiben reale Grenzen der Fakeaussagen.

Kleinster nächster Implementierungsauftrag: konkreten injizierbaren nativen
S/C-Handletransferport mit gehaltenem C-Zielprozess und einmaligem instanzgebundenem
Übernahme-Ack implementieren und zuerst fakeprüfen. Kein OS-Versuch jetzt; fester
C-Einstieg/Manifest sowie reale Peer-/Endbelege bleiben vor OS-Freigabe zusätzlich
nachzuweisen. Keine automatische Weiterentwicklung oder neue breite Modellphase.
**K/H- und früherer Workerprozessversuch bleiben ausdrücklich GESPERRT.**

Keine Git-/DB-/SQL-/Migrations-/Anwendungs-/HTTP-/Scraper-/Browseroperationen,
echten S/C/H-/Job-/Worker-/Win32-Versuche, Netzwerkzugriffe oder Installationen.
Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung und keine Änderung
der Muss-/Soll-/Kann-Prioritäten.
Zusätzlicher Bootstrap-Regressionsfall: S und C müssen verschiedene lebende Prozessinstanzen sein; zwei Handlekopien derselben PID sind kein unabhängiger Custodian.

### T02-Nachtrag 25.10.2026 – B-WATCHDOG-4D-2: instanzgebundener Handletransfer

Isolierten HandleTransferPort und TransferCheckedConnection ergänzt. Native
DuplicateHandle-/CompareObjectHandles-/GetCurrentProcessId-Deklarationen samt
kalter Win64TransferBindings-Brücke und getrenntem 26-Export-Ladeprofil vorbereitet;
keine DLL oder neue Native-Brücke aufgerufen. Vorhandene 18-/23-Exportprofile,
JNA-Version und Hashpin unverändert. Reine JDK-Fakes prüfen den neuen Vertrag.

S-/C-Prozess-Capabilities über vertrauenswürdige Ledger geliehen, aktueller
S-Prozesskontext und gehaltene Instanzen (PID/Geburt/Image/ALIVE/Nichtvererbung)
geprüft. Kein PID-Öffnen. K nur aus dem bestehenden Win64KernelCalls-Jobledger,
mit privatem Kill-on-last-close-/No-breakaway-Readback. Zielkopie exakt QUERY |
TERMINATE, inherit=false/options=0; weder SAME_ACCESS noch CLOSE_SOURCE.

Zusätzliche kontrollierte QUERY-Prüfkopie aus C zurück nach S, dort im vorhandenen
Ledger registriert und mit CompareObjectHandles gegen das reservierte ursprüngliche
K geprüft. Prüfkopie genau einmal schließen; C-seitigen Zahlenwert NIEMALS als
ungeprüften lokalen S-Handle schließen. Gleiche Zahlen in verschiedenen Tabellen
sind zulässig; entscheidend sind Prozesskontext/Provenienz/Capability. Unbekannte
Teilkopien oder Closes verhindern Abschluss und werden nicht erneut versucht.

Native Erfolgsmeldung ist noch kein C-Besitz-Ack: einmalige Delivery/Transfer-ID,
Ticket/Session/K/Revision, Attempt und konkrete C-Instanz zusätzlich binden.
Native-Source-Bootstrap verlangt die geprüfte Connection; kein READY ohne beide
Bestätigungen und kein H/J/W-Startpfad. Prozess-/Jobnutzungen bleiben während
blockierter oder unbestätigt zurückgekehrter Duplikation erhalten; Cleanup kann
lokal widerrufen, späte Rückgabe eröffnet keinen Start. Keine neue Deadline-,
Recovery-, Import- oder Slotfreigabelogik.

**684 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen
646 mit unveränderten Assertions erneut ausgeführt plus 38 neue Transferfälle.
Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run. custodianUnitTest
jetzt 145 (63+44+38), ohne JNA-JAR. Neue native Brücke nur kompiliert/statisch
geprüft; keine neuen JNA-Tests. Die 56 bestehenden Speicher-/Layoutfälle nach
statischer Kontrolle nur im erlaubten lokalen Memory-Umfang ausgeführt. 159 alte
JNA-Bindingtests und alle historischen OS-Einmalversuche NICHT wiederholt.

Produkt-/Manual-Quellen, Guards und alte Tests unverändert; Native-/Transfer-
Quellen bleiben isoliert in jobAdapter/custodianCheck. Keine neue Dependency,
Source-Set- oder ausführende Task. Historische Dokumentpräfixe/Marker/Evidenzen
und bestehendes Manual-JAR erhalten; Hash-/Präfix-/ZIP-Prüfung ohne Git.

**S/C-Leerversuch weiterhin NICHT ausführungsreif.** Realer ProcessLedger-Provider,
fester C-Einstieg/Manifest und authentisierte C-lokale Übernahme-/Endbestätigung
fehlen noch. Peer-/Delivery-Objektidentität ist heute nur eine Same-JVM-Fakegrenze;
kein Same-Token-/kryptografischer Schutz und keine globale OS-Handlezählgarantie.
Reale ABI/Exports, Crash-/Kernelhängerverhalten und Windows-Durability nicht
nachgewiesen. KILL_ON_JOB_CLOSE ist weiterhin erst beim letzten OS-Handle wirksam;
einzelne Close-/Duplicate-Antworten beweisen kein vollständiges Gruppenende.

Kleinste nächste Voraussetzung: ausführbaren instanzgebundenen S/C-Bootstrap mit
registrierten gehaltenen Prozess-Capabilities und authentisiertem C-Übernahme- /
Endkanal isoliert implementieren und zunächst fakeprüfen. Kein weiterer Transfer-
Modellentwurf und kein OS-Versuch jetzt. Erst anschließend gesonderte Einmalfreigabe
mit neuen STOP-/Endbelegen prüfen. **S/C-, K/H- und Worker-Versuche bleiben GESPERRT.**

Keine Git-/DB-/SQL-/Migrations-/Anwendungs-/Listener-/Browser-/Scraperoperationen,
echten Win32-/Job-/Prozess-/Transferaufrufe, externen Netzwerkzugriffe, Downloads
oder Installationen. Nur Gradle-/JUnit-Testwerkzeuge und erlaubte lokale
Speicherprüfungen. Kein T02-Gesamtabschluss, keine SSRF-/T03-/T04-/T06-Umsetzung
oder Änderung der Muss-/Soll-/Kann-Prioritäten. Details in TESTING.md.
### T02-Nachtrag 26.10.2026 – B-WATCHDOG-4D-3: getrennte S/C-Einstiege, Fakeprüfung

Isolierte ScSupervisorMain/ScCustodianMain und S-/C-Rollenlaufzeit ergänzt,
getrenntes custodianExecutable-Source-Set und expliziter datenbankfreier Prüftask.
Kein JavaExec, keine Verknüpfung zu test/check/build/assembleManualTest, keine
Dependency oder Produkt-/Manualintegration. Runtimekern ohne JUnit/JNA und alte
Testklassen; native Verträge bleiben kalt. Ausführbare Klassen sind kompiliert,
aber ihr konkreter nativer Driver fehlt und bleibt ausdrücklich geschlossen.

Session/Intent -> gehaltene eigene C-/Kanalbindung -> K-create/configure/readback
und leeres Accounting -> vorhandener kontrollierter Transfer -> C-lokale Adoption
und einmaliges Wire-/Transfer-Ack -> READY. Bestehender EmptyCustodianBootstrap
übernimmt optional den vor C-Start publizierten Proof; alter Einstieg bleibt.
BOUND bindet die Session bereits vor Existenz der Transfer-ID. Angekündigte
Transfer-ID allein berechtigt zu keiner Handleübernahme. Fester begrenzter
stdio-Binärkanal nutzt den bestehenden Codec, keine TCP-/HTTP-Schnittstelle,
keine Handle-/PID-/Pfadwerte oder beliebigen Befehle im Wireprofil.

HeldProcessLedger und ScNativeCustodianPort verdrahten gehaltene Prozessinstanzen
und C-lokale eingeschränkte Kontrollkopien mit vorhandenen Ports, ohne Win32
aufzurufen. Gruppenende, einzelne Kopien, Ownerende, Ressourcenabschluss,
END/CLOSED/EOF und gehaltenes C-Ende bleiben getrennte Belege. Kein READY aus
nacktem Ack, kein Owner-Tod aus Kommunikationsverlust, kein automatischer Retry,
Reset oder Freigabe bei UNKNOWN. Vor bestätigter Übernahme keine Crashgarantie.
Die neuen Integrationsports sind im Moment nur fakeverdrahtet; keine behauptete
Same-Token-Authentisierung oder echte Cross-process-Kanalprovenienz.

**739 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen
684 mit unveränderten Assertions erneut ausgeführt plus 55 neue S/C-Fälle.
Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run. 56 bestehende
lokale JNA-Memory-/Layoutfälle nach statischer Kontrolle im erneut erlaubten
Umfang ausgeführt. Neue Rollen-/Kanaltests ohne JNA. 159 historische JNA-Tests
und sämtliche OS-Einmalversuche NICHT wiederholt. Details/Matrix in TESTING.md.

**Einstufung BLOCKED.** Kleinste noch notwendige Implementierungseinheit:
konkreter nativer S/C-Start-/Acquisitionprovider für das geschlossene nativeDriver-
Gate: C außerhalb von K, gehaltene Startinstanzen samt nachweislich zugehörigen
stdio-Enden, C-lokale Registrierung der nachträglich übertragenen K-Kopie und
unabhängige Owner-/Kontroll-/Endspur. Der bisherige H-Startadapter setzt schon
ein Job-/JOB_LIST-Attribut voraus und darf C nicht unbelegt in K starten.
Keine weitere Modellphase oder OS-Versuch jetzt. Engster späterer Einmal-Leer-
versuch mit frischem Verbrauchsmarker, festen Manifesten, STOP-Kriterien und
separaten Endbelegen in TESTING.md beschrieben; noch kein ausführbarer Freigabelauf.
**S/C-, K/H- und Worker-Versuche bleiben GESPERRT.**

Dokumente ausschließlich ergänzt, historische Evidenzen und bestehende Artefakte
bewahrt; Hash-/Präfix-/ZIP-/Whitespaceprüfung ohne Git. Keine DB/SQL/Migration,
Anwendung/HTTP-Listener, echten Win32-/Job-/Prozess-/Transfer-/Browser-/Scraper-
Starts, Netzwerke, Installationen oder Gitoperationen. Nur freigegebene lokale
Testwerkzeuge. Kein T02-Gesamtabschluss und keine SSRF-/T03-/T04-/T06-Umsetzung
oder Änderung der Muss-/Soll-/Kann-Prioritäten.
### T02-Nachtrag 26.10.2026 – B-WATCHDOG-4D-4: nativer C-Start-/Acquisitionprovider

Den konkreten 4D-3-Implementierungsblocker beseitigt: ScStartProvider,
Win64ScCalls und ScNativeDriver verdrahten den eigenen suspendierten C außerhalb
von K, feste Java-/Classpath-/Hashmanifeste, genau drei stdio-Enden plus reduzierte
Ownerkopie in HANDLE_LIST, sofort registrierte Prozess-/Threadhandles und gehaltene
Instanzen. Kein JOB_LIST-/Worker-Fallback. Originale selbst erworbene C-Cleanup-
Capability mit TERMINATE bleibt getrennt vom reduzierten DUP_HANDLE-Prozesshandle.
Bestehender produktiver Workerstartvertrag und Sicherheitsguards unverändert.

Konkreter nativer Modus --sc-native-empty-v1 nur in isolierten Source Sets,
keine Produkt-/Manual-/Gateway-/Controllerintegration oder automatische Starttask.
Altes Vorbereitungsprofil bewusst weiter geschlossen. Frische lokale Hashfreigabe
und CREATE_NEW-Verbrauchsmarker vor jedem späteren nativen Start erforderlich;
heute keine Freigabedatei/Marker angelegt und keinen main-Einstieg aufgerufen.
Keine neue Bibliothek/Version, nur das bereits vorhandene gepinnte JNA-JAR.

ScNativeConnection/ScNativeWire verwenden bestehende Custodian-/Transfer-/Codec-
verträge. K erst nach gebundenem C-BOUND; native Kontrollkopie tatsächlich in C
registrieren/readbackprüfen, QUERY-Rückvergleich erhalten, einmaliges gebundenes
Ack vor READY. Native GRANT trägt den benötigten C-lokalen Wert ausschließlich
auf dem gebundenen Startkanal, nie im Log oder als frei ausführbaren Befehl.
S-stdin echter Halbabschluss nach CHANNEL_END, stdout/stderr für Endbelege offen.
Ownerkontrolle unabhängig von blockierter S-I/O; vollständige Gruppen-, Kopien-,
Kanal-/EOF-, Ressourcen- und gehaltene C-End-/Exitcodebelege getrennt.

**767 ausgewählte Fälle bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen
739 mit unveränderten Assertions erneut ausgeführt plus 28 neue Provider-/
Acquisition-/Speicherpipe-Integrationsfälle. Native Brücken/Driver ausschließlich
kompiliert/statisch geprüft. Vor jedem tatsächlichen Gradlelauf identischer
Offline-Dry-Run. 56 bestehende erlaubte JNA-Memory-/Layoutfälle nach statischer
Kontrolle; keine neue JNA-Ausführung. 159 historische JNA-Tests und alle
OS-Einmalversuche NICHT ausgeführt. Taskgraph bestätigt keine Native-/Probe-
Anbindung an test/check/build/assembleManualTest. Details in TESTING.md.

**READY_FOR_SEPARATE_OS_APPROVAL ausschließlich für einen normalen einzelnen
S/C-Leerversuch.** Noch keine OS-Ausführung freigegeben oder erfolgt. Reale ABI,
Vererbung/Handlebindung, Windows-Jobrichtlinien und Crash-/Kernelhängerverhalten
bleiben zu prüfen; keine Same-Token-Sandbox oder globale Handlezählgarantie.
S-Ausfall vor bestätigter C-Kontrollübernahme wird ausdrücklich nicht als
abgesicherter Betrieb bezeichnet. K ist dann ausschließlich leer; H-/Workerstarts
sind in diesem Pfad überhaupt nicht implementiert. UNKNOWN bleibt gesperrt,
kein Retry/Reset und keine automatische Quarantäneauflösung. **Historischer
K/H- und suspendierter Workerprozessversuch bleiben weiterhin GESPERRT.**

Historische Berichte/Marker/Evidenzen und Produkt-/Manualartefakte erhalten.
Hash-/Präfix-/ZIP-/Whitespaceprüfung statt Git. Keine DB/SQL/Migration,
Anwendung/HTTP, echten nativen Prozess-/Job-/Handle-/Browser-/Scraperoperationen,
Netzwerke, Installationen oder Gitbefehle. Kein T02-Gesamtabschluss, keine SSRF-/
T03-/T04-/T06-Arbeit und keine Änderung der Muss-/Soll-/Kann-Prioritäten.
### T02-Nachtrag 26.10.2026 – B-WATCHDOG-4D-5: OS-Anlauf PARTIAL/STOP

Einmalige Freigabe unter Kennung
B_WATCHDOG_4D_5_20261026_85287824-ae3a-41b6-9cd4-8a944832f352 verbraucht.
Genau ein S gestartet (PID 24996); unerwartetes Windows-Kind conhost.exe
(PID 72972) nachgewiesen. Äußerer Beobachter nach 409 ms STOP, kein zweiter
Anlauf oder erweiterte Prozessallowlist. Kein C und kein K erstellt: Der native
Sessionordner enthält nur consumed, keinen Start-Intent; C-/K-Pfade wurden
nicht erreicht. Kein Handshake-/Ack-/READY-/Gruppen-/Ressourcenendnachweis.

Statische PE-Dateianalyse nach STOP zeigt einen konkreten Bindungsfehler:
CompareObjectHandles fehlt in der lokalen kernel32.dll, ist in KernelBase.dll
exportiert. Win64ScCalls.load verlangt es aus kernel32. Kein zusätzlicher
DLL-Lade-/OS-Versuch und kein Fallback. Die konkrete Java-Exception ist wegen
des stillen Fehlerpfads nicht als Originaltrace verfügbar. Die zuvor gemeldete
READY-Einstufung war bei der DLL-Zuordnung zu weitgehend; historisch erhalten,
für weitere OS-Anläufe jetzt durch **BLOCKED** ersetzt. Herkunft/Notwendigkeit
des unerwarteten Konsolenhosts zusätzlich vor neuer Freigabe klären.

S-Exitcode und gehaltenes S-Ende beim frühen STOP nicht erfasst. Spätere rein
lesende Nachprüfung findet weder die bekannten PIDs noch Kinder von S; kein
vollständiger Endbeleg daraus erfunden. Zwei lokale JNA-Temporärdateien bleiben
als Evidenz erhalten. Kein Prozess extern beendet und keine automatische
Bereinigung oder Wiederholung. Verbrauchsmarker/Hashfreigabe bleiben bestehen.

Vorprüfung: 28 ausgewählte datenbankfreie Provider-Fakefälle erfolgreich,
0 Fehler/übersprungen; identischer Offline-Dry-Run vor Compile/Test. Historische
767 Fälle nicht als heute vollständig wiederholt ausgeben. Keine 159 alten
JNA-Tests oder früheren OS-Proben. Dokumente ausschließlich um Nachtrag ergänzt;
alle Quellen, das Manual-JAR und 107 erfasste historische Evidenzdateien bytegleich.
Versuchsmanifest/Hashes, Prozessdaten, Reststatus und Nachweisgrenzen in TESTING.md.

Nächster gesonderter Auftrag: feste DLL-Zuordnung des Vergleichs samt Fake-
Exportprüfungen korrigieren und Konsolenhostbefund einordnen; keine pauschalen
DLL-/Prozessfallbacks. Danach erst neue ausdrückliche Einmalfreigabe erwägen.
Dieser Anlauf verbessert die K/H-Voraussetzungen nur durch Blockerdiagnose,
NICHT durch erfolgreiche Custodian-/Jobabnahme. **K/H, J und Worker bleiben
GESPERRT.** Kein Produkt-/Manualstart, keine Datenbank/SQL/Migration, Browser,
Scraper, externe Netzwerkabrufe, Gitoperationen oder sonstige Ticketumsetzung.
Keine Änderung der Muss-/Soll-/Kann-Prioritäten. Nach dem Einzelanlauf beendet.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-6: DLL-Korrektur, OS-Gate bleibt BLOCKED

Statisch bestätigte Fehlzuordnung gezielt korrigiert: CompareObjectHandles wird
ausschließlich aus der festen System32-KernelBase.dll gebunden. Kernel32 behält
alle bisherigen Job-/Prozess-/Pipefunktionen. JDK-only DLL-/Exportplan prüft beide
Module vor Bind/Prozessoperationen; kein PATH-/DLL-Fallback oder abgeschwächter
Exportvertrag. Neue getrennte Vergleichsbindung auch im S/C-Loader verwendet.
Originalexception aus 4D-5 weiterhin unbekannt: Loaderfehler gut begründete Ursache,
kein nachträglich erfundener Trace oder Exitcode.

Begrenzte feste Bootstrap-STOP-Diagnosen mit Phase/Kategorie statt Rohfehlern
ergänzt. Nicht startende PowerShell-Besitzerfunktion für gehaltenes Ende/Exitcode
bis zur bestehenden absoluten Frist vorbereitet; unbekannter Zustand bleibt STOP,
kein PID-Cleanup oder Ressourcenendversprechen. Späterer äußerer Einmalharness
muss diese Beobachtung noch konkret vor Handlefreigabe einbinden.

Java-Image als Konsolenprogramm statisch bestätigt. Historischer S-Launcher hatte
bereits CreateNoWindow=true und umgeleitete Pipes, C-Vertrag bereits NO_WINDOW.
conhost als Windows-Konsolenhilfe plausibel, konkreter Auslöser und Lebensdauer
damals nicht abschließend belegt. Keine spekulative Launcheränderung oder
größere Prozessallowlist; historische Drei-PID-Assertion unverändert.

**795 Java-Fälle bestanden, 0 Fehler, 0 übersprungen:** alle 767 Bestandsfälle
mit unveränderten Assertions erneut ausgeführt plus 28 neue JNA-freie Loader-/
Diagnosefakes. Zusätzlich 6 PowerShell-Beobachterfakes erfolgreich. Vor jedem
tatsächlichen Gradlelauf identischer Offline-Dry-Run; 56 erlaubte bestehende
JNA-Memory-/Layoutfälle vorher statisch geprüft. Keine Kernel32-/KernelBase-
Funktionsaufrufe, 159 historischen JNA-Tests oder OS-Proben ausgeführt.

Alle 119 erfassten historischen Evidenz-/Markerdateien einschließlich 4D-5 und
verbrauchter Freigabe bytegleich, ebenso Manual-JAR und alte Testquellen.
Dokumente nur ergänzt. Keine neue Freigabe/Marker angelegt, kein S/C/H/K/J/Worker,
Browser, Scraper, Anwendung, HTTP oder Datenbank/SQL gestartet. Keine Gitbefehle,
Dependencies, Installationen, Produkt-/Manual-/Guardänderungen oder fremde Tickets.

**BLOCKED, noch nicht READY_FOR_NEW_OS_APPROVAL:** Konsolenhostfrage und konkrete
äußere End-/Exitcode-/Diagnosebeobachtung vor neuem Einmallauf klären. Die
Freigabevorbereitung muss zudem den noch fest adressierten verbrauchten
Hashfreigabepfad durch einen getrennten frischen Weg ersetzen, ohne historische
Freigaben zu überschreiben; heute keine solche Änderung oder neue Marker.
Verbesserte Diagnosen ersetzen die weiterhin fehlenden historischen S-End-/Ressourcenbelege
nicht. Kein erneuter Versuch freigegeben, K/H, J und Worker weiter GESPERRT.
Details und Nachweisgrenzen in TESTING.md. T02 nicht insgesamt abgeschlossen;
Muss-/Soll-/Kann-Prioritäten unverändert. Nach Vorbereitung angehalten.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-7: S/C-Einmalharness vorbereitet

Nach PC-Neustart tatsächlichen gespeicherten Stand ohne Git aufgenommen und
erhalten; keine Neuimplementierung oder zusätzliche Quellkorrektur erforderlich.
Die drei 4D-6-Voraussetzungen sind jetzt umgesetzt: konservative Prozessbefunde,
integrierte äußere End-/Exitcode-/Diagnosebeobachtung und unabhängiger sitzungs-
gebundener Einmalfreigabeweg. Dokumentation erstmals mit diesem Abschluss ergänzt.

Möglicher System32-conhost bleibt unbewiesener Konsolenbegleiter, nicht allein
aufgrund Name/Parent ein eigener Prozess. Image/Geburt/Instanzhinweise getrennt
von Besitz und Jobmitgliedschaft; C erst mit gehaltenem nativem Erwerbsbeleg
korrelieren. Unbewiesener Begleiter führt STOP, keine pauschale Ausnahme. Historische
Drei-PID-Assertion/Startflags unverändert, früherer conhost-Auslöser weiterhin offen.

Neuer Launcher bindet den vorhandenen PowerShell-Endbeobachter tatsächlich ein:
genau eine eigene S-Startinstanz, begrenzte Diagnosen/EOF, echter gehaltener
Exitcode und kein Ende aus PID-Abwesenheit. Dispose erst nach Ende/Reads; UNKNOWN
bleibt unbestätigt und RetainedOwner erhalten. Native Gruppen-/Ressourcen-/Journal-
und C-Endbelege getrennt berichten. Start oder EOF allein ergibt keinen PASS;
FAIL/STOP und PARTIAL/STOP bleiben von vollständigem PASS unterschieden.

Neuer Namensraum .gradle/t02-sc-attempts/<UUID>/, externe explizite Hashfreigabe
und ExecuteOnce erforderlich. Atomarer CREATE_NEW-Verbrauch samt Flush/Readback
vor Start; Sitzungs-/Artefakt-/Freigabehashbindung auch in S/C geprüft. Hash umfasst
zusätzlich die drei Laufzeitskripte. Kein alter Marker/Hashfreigabepfad übernommen;
kein Retry bei Start-/Beobachterfehler. Heute keine echte Freigabe oder Marker
angelegt, ausschließlich temporäre nicht ausführbare Fake-Daten.

**Nach Neustart 817 Java-Fälle bestanden, 0 Fehler, 0 übersprungen:** alle 795
bisherigen plus 22 neue Verträge. Zusätzlich **48 neue und 6 bestehende PowerShell-
Fakefälle erfolgreich**; der früher kommunizierte Zwischenstand 42+6 bleibt
historisch erhalten. Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run.
56 lokale JNA-Memory-/Layoutfälle nach erneuter statischer Prüfung im bekannten
engen Umfang; keine Win32-Funktion, 159 historischen JNA-Tests oder OS-Proben.

366 definierte Dateien seit Wiederaufnahme geprüft; bis zum Nachtrag alle
unverändert, darunter 119 historische Evidenzen/Marker, Guards und Manual-JAR.
Bekannte historische Hashanker separat bestätigt. Die flüchtige Hashliste von
vor dem Neustart fehlt; deshalb keine unbewiesene vollständige Vor-Neustart-
Bytevergleichsaussage. Produkt-/Manual-Classpaths und normaler Taskgraph weiterhin
ohne S/C-Experimentintegration. Dokumente nur ergänzt, keine Prioritäten geändert.

**READY_FOR_NEW_OS_APPROVAL** ausschließlich für einen neuen normalen einzelnen
S/C-Leerversuch. Noch KEINE Ausführungsfreigabe und KEIN nativer Erfolgsnachweis.
4D-5 bleibt verbraucht; historische End-/Ressourcenlücken bleiben offen. Reale
Windows-/Kernel-/CIM-Hänger, Same-Token-/Besitzercrash-/Durabilitygrenzen nicht
durch Fakes bewiesen. K/H, J und Worker weiter GESPERRT; kein T02-Gesamtabschluss.
Keine Git-, Netzwerk-, DB/SQL-, Anwendungs-, Browser- oder Scraperoperationen.
Nach Abschluss auf neue ausdrückliche Freigabe warten. Details in TESTING.md.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-8: vor OS-Start BLOCKED

Die neue S/C-Einmalfreigabe nach verbindlicher statischer Vorprüfung NICHT
ausgeführt oder verbraucht: S=0, C=0, K=0. Kein neuer Sitzungsordner, keine UUID,
Freigabe-/Manifest-/Verbrauchsdatei oder Versuchsressource angelegt. Keine
Vorversuche, Tests, Reparatur oder Wiederholung. Historische 4D-5-Evidenz bewahrt.

Konkreter Blocker: PowerShell setzt beim äußeren STOP nur ein lokales Flag und
beobachtet anschließend das gehaltene S-Ende. Es fehlt die Weitergabe an den
nativen S-Kontrollkern und dessen Start-/Resume-Revokation. S könnte dadurch nach
einem unbewiesenen Konsolenbegleiter oder Beobachterfehler noch C/K-/Transfer-/
READY-Phasen fortsetzen. Eine negative Ergebnisbewertung verhindert dies nicht.
Die frühere 4D-7-READY-Einstufung war insofern zu weitgehend, bleibt historisch
erhalten und wird für eine Ausführung jetzt durch **BLOCKED** ersetzt.

Kleinste notwendige Korrektur: sitzungs-/instanzgebundene äußere STOP-Spur an
vorhandene native Revokations-/Startbarrieren anbinden und zuerst mit Fakes
einschließlich laufender/später Operationen prüfen. Kein Retry, neues Budget,
PID-Cleanup oder pauschale Konsolenhostausnahme. Heute keine Umsetzung.

Keine neuen Windows-/End-/Handle-/Gruppenbelege. 817 Java- und 48+6 PowerShell-
Fälle bleiben historische 4D-7-Ergebnisse, nicht erneut ausgeführt. Nur Dokument-
nachträge; Quellen, Guards, Produkt-/Manualartefakte und 119 erfasste historische
Evidenz-/Markerdateien erhalten. Keine Git-/Netzwerk-/DB-/Anwendungs-/Browser-/
Scraperoperationen, keine Prioritätsänderung. K/H, J und Worker weiter GESPERRT.
Nach Vorprüfungs-STOP anhalten; keine eigenständige spätere OS-Ausführung.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-9: äußere Revokation implementiert

Den konkreten 4D-8-Blocker geschlossen: separater gebundener lokaler ADMIT/STOP-
Briefkasten zwischen Besitzer und unabhängigem S-Kontrollthread, getrennt vom
normalen S/C-Bootstrapkanal. Bindung an Sitzung, Intentticket/-gruppe/-revision/
Anchorhash sowie gehaltene S-Instanz (PID/Geburt). Nur feste begrenzte Nachrichten,
keine frei verwendbaren Prozessbefehle oder zusätzlichen Listener.

Gemeinsame ScRevocationFence als atomare Start-/Resume-/K-/Transfer-/Bootstrap-/
READY-Autorität. STOP widerruft endgültig; vor Commit reservierte Aufrufe können
nicht mehr beginnen. Bereits verbindlich committete Aufrufe bleiben outstanding
und dürfen spät zurückkehren; kein stärkeres Versprechen über den exakten
Windows-Kerneintritt. Native-/Kanaloperationen außerhalb gemeinsamer Monitore.
Vorhandene provider.cancel/supervisor.stop- und Cleanupbarrieren angeschlossen.
Keine neue Recovery-/Restartpolitik und kein Umbau des gesperrten H-/Workerpfads.

PowerShell verwendet den Kontrollpfad im realen Launcher zwingend und übermittelt
STOP höchstens einmal. Begrenztes ACK bestätigt nur Leasewiderruf, niemals Prozess-,
Gruppen- oder Ressourcenende. Fehlende Bindung/Antwort und Beobachterfehler bleiben
UNKNOWN/STOP und können kein PASS ergeben. Teilregistrierte C-Rückgabe verbraucht
ohne Instanzbeleg noch keinen nativen Terminierungsversuch; spätere eigene
Rückgabe wird konservativ mit der bisherigen einmaligen Cleanupauthority behandelt.

**857 Java-Fälle bestanden, 0 Fehler, 0 übersprungen:** alle bisherigen 817 plus
40 neue deterministische Kontroll-/Racefälle. **78 PowerShell-Fakes bestanden:**
alle bisherigen 54 plus 24 neue. Vor jedem tatsächlichen Gradlelauf identischer
Offline-Dry-Run. 56 erlaubte lokale JNA-Speicher-/Layoutfälle statisch erneut
geprüft, keine Win32-Prozess-/Jobausführung. 159 alte JNA-Tests und alle OS-Proben
unberührt. Alte Assertions nicht abgeschwächt; Produkt-/Manual-/Guardgrenzen,
historische Nachweise und Manual-JAR erhalten. Dokumente ausschließlich ergänzt.

**READY_FOR_NEW_OS_APPROVAL** auf Implementierungs-/Fakeebene für genau einen
neuen normalen S/C-Leerversuch. Kein nativer Erfolgsnachweis und keine heutige
Ausführungsfreigabe. 4D-8 wird nicht automatisch aktiviert. Neue separate Freigabe
und Vorprüfung bleiben zwingend. K/H, J, Worker und Firefox weiter GESPERRT.

Grenzen: harter Beobachtercrash nach ADMIT ohne STOP ist durch den Nachrichtenkanal
allein nicht als sofortige Revokation garantiert; eine unabhängige OS-/Ownerlease-
Garantie ist hier nicht implementiert. Vollständig hängende JVM/Dateisystem/Kernel-
Operationen bleiben ohne ACK/Endbeleg UNKNOWN. Keine Same-Token-/Powerlossgarantie.
Diese Risiken werden nicht durch grüne Fakes ersetzt oder für Crash-/Workerfreigaben
übergangen. Details, Matrix, Commitgrenze und geänderte Dateien in TESTING.md.
Keine realen Prozesse/Jobs, Datenbank/SQL, Netzwerk-, Browser-/Scraper- oder
Gitoperationen, neuen Dependencies oder fremden Tickets. Prioritäten unverändert.
Nach Abschluss anhalten und auf neue ausdrückliche Freigabe warten.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-10: vor OS-Start blockiert

**BLOCKED / APPROVAL_REJECTED_BEFORE_START.** Statische Vorprüfung der realen
S/C-/Win64-/Revokationsverdrahtung, der festen DLL-Zuordnung, Handle-Allowlist,
Identitäten, Transfer-/Endbarrieren, Fristen und gepinnten Artefakte abgeschlossen.
Die automatische Ausführungsfreigabeprüfung hat die einzige Startanforderung
vor Ausführung des PowerShell-Launchers abgelehnt: Die ausdrückliche Freigabe im
angehängten Auftrag wurde nicht als vertrauenswürdige Ausführungsfreigabe erkannt.
Keine Umgehung und kein zweiter Startversuch.

Neue Sitzung ba92a0e4-3857-4394-804f-b7ef1eef46e1 mit eigenem Manifest und
artefaktgebundener approval.txt vorbereitet. Atomarer Verbrauch NICHT erreicht:
kein consumed, kein native-Verzeichnis, kein äußerer Laufbericht. Tatsächliche
Starts S=0, C=0, K=0. Keine nativen Handle-/Job-/Handshake-/ACK-/READY-/Exit-/
CHANNEL_END-/EOF- oder Cleanupnachweise. Dies ist kein fehlgeschlagener nativer
Bootstrap und kein erfolgreicher Windows-Test. Hashes und Details in TESTING.md.
Vorbereitete unbenutzte Dateien erteilen keine eigenständige spätere Startfreigabe.

Keine Tests/Builds wiederholt; 857 Java- und 78 PowerShell-Fakefälle bleiben
historische 4D-9-Ergebnisse. 119 historische Evidenz-/Markerdateien, 251 Quell-/
Build-/Guarddateien, 264 Laufzeitdateien und Manual-JAR durch identische Vorher-/
Nachher-Hashaggregate geschützt. Nur neue Dokumentnachträge und lokale
Vorbereitungsdateien, keine Änderung an Produktcode oder Prioritäten.

Weitere OS-Ausführung erst nach direkt bestätigter Freigabe und erneuter Prüfung.
Keine automatische Reaktivierung von 4D-8/4D-10; 4D-5 bleibt verbraucht. K/H,
Worker und Scraperintegration weiter gesperrt. Keine Datenbank, Anwendung,
Browser, Netzwerk, Installation oder Gitoperation. Nach Bericht anhalten.

### T02-Nachtrag 28.10.2026 – B-WATCHDOG-4D-10: echter Einmallauf PARTIAL/STOP

Nach direkter ausdrücklicher Chatfreigabe erneute Integritätsprüfung bestanden
und genau die vorbereitete Sitzung ba92a0e4-3857-4394-804f-b7ef1eef46e1 einmalig
ausgeführt. Freigabe jetzt atomar VERBRAUCHT; keine Wiederholung. Der vorherige
Prestart-Block bleibt als historischer Stand erhalten.

Ein S (PID 33932) und ein nativer C (PID 32356) gestartet. Zusätzlicher conhost
PID 38036 bleibt POSSIBLE_CONSOLE_COMPANION_UNPROVEN/Owned=false und löste
konservativen STOP aus. Keine Namens-/Parent-Ausnahme oder fremde Terminierung.
Äußerer gebundener STOP-Pfad real bestätigt: ControlBound=true, STOP/REVOKED-ACK,
Effective=true bei einer outstanding Leasenutzung. READY=false. ACK beweist
ausschließlich Revokation, keinen Cleanup und keinen Kernelhänger.

Gehaltenes S-Ende mit tatsächlichem Exitcode 78 und äußeres stdout/stderr-EOF
beobachtet; eigener äußerer Beobachterhandle anschließend geschlossen. C-Ende/
Exitcode, K-Anlage/-Anzahl/-Leere, Übernahme-ACK, vollständiger S/C-CHANNEL_END-,
Handle-/Ressourcen-/Journalabschluss NICHT nachgewiesen. Ergebnis **PARTIAL/STOP**,
kein normaler S/C-/Leere-K-Erfolg. Die spätere lesende PID-Abwesenheit heilt keine
fehlenden Endbelege. C-Geburt aus CIM weicht 2 FILETIME-Ticks vom nativen Wert ab;
die exakte Zuordnung blieb unverändert streng und daher erfolglos.

Alle 119 historischen Evidenz-/Markerdateien sowie Quellen, Laufzeitklassen und
Manual-JAR hashgleich erhalten. Keine Quellcodeänderung, Tests/Builds oder weitere
Ausführung. Neue lokale Laufberichte/Marker und ausschließlich Dokumentnachträge;
Hashes, Prozessidentitäten und getrennte Nachweise in TESTING.md.

Weitere Arbeit benötigt neuen Auftrag: conhost-/Beobachterklärung, präzise
C-Identitätskorrelation und fehlende native End-/Cleanupnachweise. Keine pauschale
conhost-Freigabe, keine erleichterten PASS-Kriterien. K/H, Worker und allgemeine
Scraperintegration weiter GESPERRT; keine SSRF-/T03-/T04-/T06-Umsetzung oder
Änderung fachlicher Prioritäten. Keine DB/SQL, Anwendung/HTTP, Browser/Scraper,
Netzwerk, Installation oder Gitoperation. Nach diesem Einmallauf vollständig stoppen.

### T02-Nachtrag 29.10.2026 – Architekturprüfung nach 4D-10: Entscheidung erforderlich

**ARCHITECTURE_DECISION_REQUIRED, keine Implementierung/OS-Freigabe.** Aktuellen
S/C-Stand und historische Evidenz analysiert. Empfehlung ist eine vereinfachte
Variante B mit einem Job-Owner, isoliertem Worker und konservativer Quarantäne,
ohne zusätzliche Custodian-Transfer-/H/K/J-Hierarchie. Dies ist ausdrücklich
kein eigenmächtig umgesetzter Architekturwechsel und keine Rückkehr zum früheren
In-Prozess-Seleniumansatz. Gemäß Stoppregel vor Umsetzung angehalten.

Belegte Codeprobleme: gemischte CIM-/Native-Zeitauflösung bei C-Korrelation;
Outstanding-Zähler auch nach zurückgekehrten Java-Exceptions und unpräzise
Verdichtung im Endbericht; fehlende begrenzte C-Endnachbeobachtung nach
Terminierungsannahme; Alive-Race nach regulärer C-Abschlussnachricht. Welche
davon genau im historischen Lauf wirkten, ist nicht vollständig rekonstruierbar.
Die 200-ns-Differenz durch reine sechsstellige Zeitkonvertierung reproduziert;
keine allgemeine Toleranz als Ersatz für gehaltene Identität. conhost-Ursprung
bleibt ungeklärt, keine Namens-/Parent-Ausnahme oder neue Windows-Ausführung.

Beide Varianten müssen bei unbestätigtem Gruppen-/Ressourcenende gesperrt bleiben.
Normale Scraperfehler dürfen erst nach belegtem eigenen Gruppenende einen neuen
Import zulassen. Entscheidungsbedarf betrifft die zusätzliche unabhängige
Cleanupinstanz bei einem lebenden, vollständig blockierten Owner: aktueller
Entwurf verfolgt dieses noch nicht vollständig nachgewiesene Ziel; der einfachere
Pfad würde dort konservative Quarantäne statt unabhängiger Recovery zusagen.
Kein automatischer Reset, PID-only-Kill oder READY nach Widerruf wird erlaubt.

Konkreter Komponenten-/Migrationsplan, Aufwandsschätzungen, Testmatrix und eng
gestufte spätere OS-Abnahme in TESTING.md. Historische Tests und Experimentpfade
bis zur Entscheidung vollständig erhalten. Produkt-/Gateway-/Service-/Frontend-
integration, SSRF und fremde Tickets nicht umgesetzt; Prioritäten unverändert.

Aktuell nur 78 vorhandene PowerShell-Fakes erfolgreich wiederholt und zwei reine
Zeitpräzisionsproben ausgeführt. Keine Java-/Gradle-/DB-/Browser-/OS-Tests; 857
Javafälle bleiben historischer 4D-9-Stand. Quellen, 264 Laufzeitdateien, Manual-JAR
und 140 historische Evidenz-/Markerdateien unverändert. Nur Dokumentnachträge,
keine Freigabe/Verbrauchsmarker, Dependencies, Netzwerk- oder Gitoperationen.
Weitere Umsetzung erst nach Architekturentscheidung; kein READY- oder T02-
Gesamtabschluss behauptet.

### T02-Nachtrag 29.10.2026 – Vereinfachte Variante B umgesetzt, nur fakegeprüft

Die vereinfachte Architektur wurde vom Eigentümer ausdrücklich zur Implementierung
freigegeben. Zusätzlicher Custodian und S/C/H/K/J-Hierarchie sind kein verbindliches
Ziel mehr. Bewusst akzeptiert: Bei einem lebenden, vollständig blockierten Owner
kann eine dauerhafte Importsperre bis zur sicheren Klärung bestehen bleiben.
Keine automatische Freigabe bei unbekanntem Zustand und keine Erlaubnis für
unkontrollierte Scraperreste. Historische Quellen/Nachweise bleiben erhalten.

Neue isolierte Source-Sets workerOwner, workerOwnerNative und workerOwnerCheck:
ein gemeinsamer Reihen-/Bandslot, private Gruppe pro Auftrag, feste synthetische
Worker-JVM, atomarer JOB_LIST-/Suspend-/Mitgliedschafts-/Resume-Pfad über den
vorhandenen Win64-Adapter, begrenzte gebundene IPC-Maps und vollständiger
Gruppen-/Instanz-/I/O-/Handle-/Journalabschluss vor neuer Admission.
Persistentes pending/quarantine verhindert automatische Recovery durch Neustart,
neuen Request oder späte Rückgabe. Zurückgekehrte Javafehler und native
Ungewissheit getrennt; STOP, Shutdown und Fristen erlauben keine späte Erfolgsmeldung.

Kein Remotehandletransfer, zusätzlicher Kontrollprozess, Brokerdienst oder
ungeschützter In-Prozess-Selenium-Fallback. Bestehende Budgets als Kandidaten
beibehalten; keine produktive RAM-/CPU-/Gesamtprozessquotenzusage. Die neue native
Factory ist ausschließlich synthetisch; bestehende Manga-Passion-Scraper,
Gateway, Controller, Services und Frontend wurden NICHT angebunden/verändert.

**913 Javafälle bestanden, 0 Fehler, 0 übersprungen:** 857 historische Regressionen
und 56 neue Owner-/Journal-/Race-/Speicher-IPC-Fälle. **78 PowerShell-Fakes bestanden.**
Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run. Enge alte Ausnahme
für 56 lokale JNA-Speicherfälle statisch erneut geprüft; keine neuen JNA-Tests,
159 alte Bindingtests nicht ausgeführt. Neue Nativequellen nur kompiliert.
Fakes und Quelltextassertions sind keine Windows-Abnahme.

Taskgraph bestätigt weiterhin keine Worker-/Native-/Probestarts in Produkt-/
Manualbuild. Manual-JAR und 140 historische Evidenz-/Markerdateien hashgleich;
keine Live-/Worker-Einstiege im Manualarchiv. Präzisierter Altbefund: Das bereits
vorhandene Manual-JAR enthält jna-5.13.0.jar, wurde aber weder geändert noch
geladen; keine JNA-Freiheit behaupten. Neue native 5.17-Anbindung bleibt isoliert.

**Implementiert und fakegeprüft, NICHT für echte OS-Ausführung oder Produktintegration
freigegeben.** Als nächster gesonderter Auftrag zunächst neue Einmalharness-/STOP-
Vorbereitung und Konsolen-/Startprovenienzprüfung, danach atomarer synthetischer
Workerstart im privaten Job, regulärer Abschluss, Timeout/Shutdown/Crash und
Nestedjobs jeweils gestuft abnehmen. Erst später lokale Firefox-/Geckodriverfälle.
conhost bleibt ungeklärt, keine Namens-/Parent-Ausnahme oder übernommene 4D-10-Freigabe.
Komponenten, Testbefehle, Restgrenzen und konkreter Abnahmeplan in TESTING.md.

Keine DB/SQL, Migration, Anwendung/HTTP, echten Windows-Prozess-/Job-/Handle-,
Browser-/Scraper-, Netzwerk-, Installations- oder Gitoperationen. Keine fremden
Tickets oder Prioritäten verändert; T02 insgesamt nicht abgeschlossen.

### T02-Nachtrag 29.10.2026 – Owner-Provenienz-Einmalharness vorbereitet

**READY_FOR_OWNER_OS_APPROVAL**, ausschließlich Vorbereitungsstatus. Noch keine
reale Owner-Ausführung oder Windows-Abnahme. Die vereinfachte Variante B und
ihre bisherigen Fakeergebnisse bleiben erhalten; der frühere Hinweis auf einen
fehlenden neuen Owner-Harness ist historisch überholt. T02 bleibt insgesamt offen.

Neue isolierte JDK-only Probe ohne Worker-/Job-/JNA-/Spring-Klassen, kalter
PowerShell-Launcher und native Inspektionsdeklarationen. Einziger späterer Start:
eine synthetische Owner-JVM mit gehaltener Originalhandle-Identität und exakter
nativer Erstellungszeit. Native End-/Exitcodeprüfung, begrenzte stdout/stderr-
EOF-Nachweise und eigene Ressourcen-/Subscriptionabschlüsse erforderlich.
CIM nur ergänzend; Zusatzprozesse/conhost konservativ ungeklärt und STOP, keine
Parent-/Namens-Besitzbehauptung oder Toleranz der historischen 200-ns-Abweichung.

Neue Versuchkennung `07574fb1-9fc5-41de-8a9b-d77c8841edf2`; festes Manifest und
Java-/JDK-/kernel32-/Klassen-/Skripthashes vorbereitet. Artefakthash
`f2b5924d03f2459fefd114bf0b64fe27a385d6d3fd72d0bdb2df75fb28afa9c4`.
ValidateOnly erfolgreich, nativer Typ nicht geladen. KEINE echte Freigabe und
KEIN Verbrauchsmarker erzeugt. Frühere Einmalfreigaben bleiben verbraucht und
unverändert. Atomarer Verbrauch erst nach gesonderter OS-Freigabe; kein Retry.

Tatsächlich **26 neue und 56 bestehende Java-Fakefälle bestanden**, 0 Fehler,
0 übersprungen; **44 neue und 78 bestehende PowerShell-Fake-/Metadatenfälle
bestanden**. Keine Wiederholung aller historischen 913 Javafälle und keine
JNA-Ausführung. Vor jedem tatsächlichen Gradlelauf identischer Offline-Dry-Run.
Produkt-/Manualtaskgraph weiterhin getrennt, Manual-JAR und 140 historische
Evidenz-/Markerdateien hashgleich; bisherige Quellen und Testguards unverändert.

Verbleibend: reale Windows-/WMI-/Konsolen-/EOF-/Handleclose-Abnahme. Keine
globale Prozess-/Handlebilanz oder harte Recovery bei blockierter JVM/OS/WMI
behauptet. Ungeklärter Abschluss hält die ursprüngliche Besitzsitzung offen,
keine Fremdbeendigung und keine automatische erneute Freigabe. Konkrete Bindung,
Fristen, STOP-Regeln, Befehle und Restgrenzen im neuen TESTING.md-Nachtrag.

Keine Owner-/Worker-/Job-/Browser-/Scraperstarts, Win32-Ausführung, DB/SQL,
Anwendung/HTTP, Netzwerk, Installation oder Gitoperation. Keine Produktanbindung,
SSRF-Umsetzung oder fremde Tickets vorgezogen; Prioritäten unverändert.
Nach Vorbereitung stoppen und neue ausdrückliche Owner-OS-Freigabe abwarten.

### T02-Nachtrag 29.10.2026 – Owner-Einmalversuch PARTIAL/STOP vor Prozessstart

Die anschließende ausdrückliche Eigentümerfreigabe für
07574fb1-9fc5-41de-8a9b-d77c8841edf2 wurde nach erneut erfolgreicher statischer
Hash-/Manifest-/Launcherprüfung genau einmal verwendet. Freigabedatei und
atomarer dauerhafter Verbrauchsmarker angelegt. Der unveränderte Harness brach
in Phase SUBSCRIBE ab: **StartRequested=false, StartConfirmed=false**.
KEIN Owner gestartet, keine Worker und keine Job Objects. Freigabe trotzdem
verbraucht; keine Wiederholung oder Neubindung vorgenommen.

**PARTIAL/STOP; weitere OS-Ausführung BLOCKED.** Native Owner-Identität,
Exitcode, EOF und Owner-Ressourcenabschluss konnten mangels Start nicht geprüft
werden. Subscriptionabschluss bleibt unbestätigt; ursprüngliche PowerShell-
Beobachtersitzung PID 17608/Toolsession 69334 bleibt gemäß STOP-Regel passiv
offen. Kein erfolgreicher Cleanup behauptet und keine eigenständige Beendigung.
Der genaue Registrierungsfehler ist nicht persistiert; Berechtigungs-/Provider-
ursachen sind bislang Vermutungen. Keine weitere OS-Probe zur Diagnose.

21 vorhandene Konsolenhosts nur als ergänzender Ausgangsbestand dokumentiert,
keine Owner-Zuordnung oder neue Konsolenfreigabe. Neue Evidenzen im bestehenden
Owner-Versuchverzeichnis, detaillierte UTC-Zeiten, Hashes und getrennte
Endnachweise im neuen TESTING.md-Nachtrag. Voriger READY-Status ist historisch.
Historische Marker, gebundener Artefaktstand, Bestandsquellen und Manual-JAR
unverändert. Nur Dokumentnachträge und neue versuchseigene Evidenzen.

Nächster möglicher Auftrag: gesondert freizugebende Diagnose und sicherer
Abschluss ausschließlich der ursprünglichen Beobachtersitzung. Kein neuer
Owner-/Worker-/Job-Versuch und keine automatische Reparatur freigegeben.
Keine Tests/Builds, DB/SQL, Anwendung/HTTP, Browser/Scraper, Netzwerk,
Installation oder Gitoperation. T02 und reale Owner-Abnahme bleiben offen;
Prioritäten und fremde Tickets unverändert. Nach Dokumentation stoppen.

### T02-Nachtrag 29.10.2026 – SUBSCRIBE-Diagnose und Erwerbsbelege fakegeprüft

Der genaue historische Registrierungsfehler bleibt mangels gespeicherter
Exception/HRESULT/Unteroperation ungewiss. Belegt: Diagnoseverlust im catch,
verschluckte Startausnahme und fehlender im Kontrollkern gehaltener Teil-
Erwerbsbeleg. Keine belegte WMI-Berechtigungsursache und kein Architekturwechsel.

Owner-Prozesshandle genügt für eigene Identität/Ende/Exitcode; Eventbeobachtung
bleibt im gesonderten Provenienzharness für kurzlebige Zusatzprozesse erhalten.
Snapshot-Fallback ohne gleichwertigen Nachweis würde Sicherheit abschwächen.
Neuer gemeinsamer Erwerbsbeleg vor der Registrierung trennt NOT_ATTEMPTED,
UNCONFIRMED und CONFIRMED. Unteroperationen, begrenzte Exceptionkette/HRESULTs,
Win32-/CIM-Codes und feste interne Fehlercodes diagnostiziert; keine freien
Message-/Stack-/Secretinhalte. Erste Ursache bleibt neben Cleanupfehlern erhalten.
Unerwartete lokale Queue-/Subscriberabfragefehler werden nicht mehr unterdrückt.

Ownerabschluss, akzeptierter Exitcode, vollständige Beobachtung, akzeptierte
Provenienz und unbestätigte Ressourcen getrennt ausgewiesen. Keine fremde
Bereinigung; nur bestätigte eigene Originalreferenzen. Fehlende/verspätete Acks,
Beobachterfehler und Zusatzprozesse verhindern weiterhin Gesamt-PASS.
Native Owneridentität, Hash-/Einmalschutz und sämtliche bisherigen Assertions
erhalten. Keine Worker-/Produkt-/Manualintegration oder neue Abhängigkeit.

**63 PowerShell-Harnessfälle (44 bisherige +19 neue), 78 bestehende PowerShell-
Fakes und 26 Javafälle bestanden; Java 0 Fehler/übersprungen.** Nur Fakes,
Metadaten und JDK-Dateiprüfungen; identischer Offline-Dry-Run vor Gradleausführung.
Keine Wiederholung der historischen 913er-/DB-Suite oder echte WMI-/Win32-Abnahme.
145 historische Evidenz-/Markerdateien einschließlich verbrauchtem Owner-Versuch
und Manual-JAR unverändert. Dokumente ausschließlich um Nachträge ergänzt.

Historische Sitzung 17608/69334 NICHT aktiv geprüft oder verändert. Mögliche
temporäre CIM-Registrierung/Queue und Runspacereferenzen aus Quellen beschrieben,
kein Ownerhandle. Konkreter stufenweiser Plan in TESTING.md: gesonderte Freigabe
für original-sessiongebundene Prüfung, nur nachgewiesen eigene Referenzen
bereinigen und einen belegten geordneten Sitzungsabschluss durchführen.
Kein garantierter Zugriff auf die alte nichtinteraktive Warteschleife;
bei fehlendem sicheren Zugang STOP statt PID-only-/Blindbereinigung.

Neuer realer Owner-Versuch erst nach Klärung, frischer Integritätsvorbereitung,
neuer Kennung und separater Einmalfreigabe. Alte Freigabe bleibt verbraucht,
kein neuer realer Marker/Kandidat und keine OS-Ausführung freigegeben.
Keine aktive Prozess-/Subscription-/Handleoperation, Datenbank, Anwendung,
Browser/Scraper, Netzwerk, Installation oder Gitoperation. T02 bleibt offen;
vereinfachte Worker-/Job-Architektur, übrige Tickets und Prioritäten unverändert.

### T02-Nachtrag 29.10.2026 – Workerbesitz und gemeinsamer Importablauf gehärtet

Innerhalb der zusammenhängenden autonomen Entwicklungsfreigabe zwei Fehler im
isolierten Worker-Kern reproduziert und behoben: kein gemeinsamer Quarantäne-
marker ohne eigenen Journalerwerb; kein behauptetes Gruppenende bei nicht
zurückgegebener Factory-Session. Unbestätigte Sessions vor Jobanlage sperren.
Vorhandene pending/quarantine erhalten; keine fremden Ressourcen verändern.

JDK-only WorkerImportRunner dispatcht beide Importarten. Gebundenes HELLO vor
langer Arbeit, bestehendes Wireformat/Map-Felder erhalten, leere begrenzte
Fehlerantwort statt Rohtext/Teilmap, kein Retry nach Teilwrite. Synthetischer
Worker nutzt denselben Ablauf. Keine tatsächliche Gateway-/Selenium-Anbindung;
keine Änderung fachlicher Regeln, UI oder Datenmodelle. Legacy-stdout muss beim
späteren Worker sauber vom Protokoll getrennt werden; SSRF-/Browsernachweise offen.

**95 Javafälle bestanden (69 Worker +26 Owner-Probe), 0 Fehler/übersprungen;
141 PowerShell-Fakes/Metadatenfälle bestanden (63+78).** Vier neue Fälle vorher
rot reproduziert, nach Korrektur grün; insgesamt 13 zusätzliche Workerfälle.
Native Quellen nur kompiliert. Offline und identischer Dry-Run vor jedem Lauf.
Keine historische Vollsuite oder echte Windows-/Browserprüfung behauptet.

Frisches reines Owner-Manifest 983900f4-de55-45b3-a144-54a5e5f547dd kalt erfolgreich
validiert, Hash-/Freigabevorschlag in TESTING.md. Nur manifest.txt; keine Approval-
oder Verbrauchsdatei. Altes Manifest und verbrauchte Freigabe unverändert.
Historische Sitzung NICHT aktiv geprüft/bereinigt. Nächste unvermeidbare Grenze:
ausdrückliche Freigabe für sichere original-sessiongebundene Prüfung/Abschluss,
danach bedingt genau einen neuen synthetischen Owner-Versuch. Bei fehlendem
sicheren Zugang oder Nachweis STOP, kein PID-only-Fallback und kein Ownerstart.
Keine Genehmigung für Worker-/Job-/Browser-/Produktstarts daraus ableiten.

145 historische Evidenzdateien und Manual-JAR hashgleich, DB-Guards und
produktiver/manual Startweg unverändert. Keine Git-/DB-/Anwendungs-/Netzwerk-
oder OS-Experimente. Weiterer sicherer Codeausbau ist kein Ersatz für das reale
Windows-Gate; Scraperabsicherung und T02 bleiben bis zu diesen Nachweisen offen.
Keine neue Kontrollhierarchie und keine Änderung der Muss-/Soll-/Kann-Prioritäten.

### T02-Nachtrag 30.10.2026 – STOP bei ursprünglicher Toolsession

Die begrenzte Windows-Freigabe wurde zunächst nur zur identitätsgebundenen
Statusprüfung der ursprünglichen Toolsession 69334 verwendet. Einmalige leere
write_stdin-Abfrage lieferte `Unknown process id 69334`. Damit ist der originale
Toolzugang nicht verfügbar; Ende und Ressourcenabschluss von Windows-PID 17608
sind dadurch NICHT belegt. Keine PID-only-Ersatzzuteilung oder Bereinigung.

**STOP/BLOCKED in Schritt 1.** Der ausdrücklich vorausgesetzte vollständige
Sitzungsabschluss fehlt; deshalb kein neuer Owner-Versuch, keine aktive WMI-/
Handle-/Prozessoperation und keine Wiederholung. Kandidat 983900f4 bleibt nur
als Manifest vorhanden, keine ausführbare Approval- oder Verbrauchsdatei erzeugt.
Für Fortsetzung ist zunächst ein belastbarer ursprünglicher Sitzungs-/
Abschlussnachweis erforderlich. Keine neue Windows-Eigenschaft bestätigt.

Nur neue Dokumentnachträge; 145 historische Evidenzdateien und Manual-JAR
hashgleich. Keine Codeänderungen, Tests/Builds, DB, Anwendung, Browser/Scraper,
Netzwerk, Installationen oder Gitoperationen. Architektur, Prioritäten und
historische Prüfstände unverändert; weitere native Ausführung angehalten.

### T02-Nachtrag 30.10.2026 – Neue Probe ohne globale Alt-Sitzungssperre

Die ausdrücklich verlangte Neubewertung trennt historische Ungewissheit von
tatsächlich zugeordneten Importjobs. Gepinnte Evidenz des Altversuchs 07574fb1:
Abbruch vor Owner/Worker/Jobstart. Dessen PowerShell-/CIM-Abschluss bleibt UNKNOWN;
keine Ressourcenfreiheit oder Bereinigung behauptet. Diese reine Beobachtung
ist keine notwendige globale Sperre jeder neuen unabhängigen Probe. Der frühere
STOP unter der damaligen bedingten Freigabe bleibt korrekt/historisch.

Enge Abgrenzung im experimentellen Ownerharness: neue Kennung/Namespace und
eigene ursprüngliche Ressourcen. Reine Klassifikation ist kein Evidenz- oder
Ausführungsnachweis; echter Preflight prüft gepinnte Originalbytes. Falsche
Start-/Jobdaten und Typen werden abgewiesen. Keine Ausnahme für tatsächlich
gestartete unbestätigte Importjobs, kein Reset von pending/quarantine oder
Zugriff auf alte Prozesse. Alte bedingte Ausführungsgenehmigung nicht umgedeutet.

Zwei weitere Worker-Fristfehler zunächst rot reproduziert und behoben: Status
erkennt blockierte Factory vor Kontrollthreadstart; Nichterwerb vor Factory
führt nicht mehr durch Nullzugriff zu falscher Quarantäne. Späte unbekannte
Ressourcen bleiben gesperrt; belegter Nichterwerb schließt nur das eigene Journal.

**97 Javafälle (71+26), 0 Fehler/übersprungen; 155 PowerShellfälle (77+78)
bestanden.** Native Quellen nur kompiliert. Keine DB-/913er-Vollsuite oder echte
Windows-/CIM-/Browserabnahme. Bisherige Assertions und Schutzmechanismen erhalten.

Endgültiger Kandidat f4d9665f-b554-4dd4-a305-84759b832c73 kalt validiert,
Hash/Manifest und Begründung in TESTING.md. Nur Manifest, keine ausführbare
Freigabe/Verbrauchsdatei. Nächste echte Grenze: neue ausdrückliche Freigabe für
genau einen unabhängigen Owner OHNE historische Bereinigungsvoraussetzung,
null Worker/Jobs/Browser/DB/Anwendung, unveränderte Fristen und kein Retry.
Die alte Genehmigung für 983900f4 wird nicht genutzt oder neu gebunden.

146 frühere Evidenz-/Manifestdateien und Manual-JAR hashgleich. Dokumenthistorie,
Prioritäten und Grundarchitektur erhalten. Keine alte Sitzung abgefragt/beendet,
keine Git-/Datenbank-/Netzwerkoperation oder Produktintegration. Reale Job-/
Browser-/SSRF-/Quoten- und Integrationsnachweise für Einsatzreife bleiben offen.

### T02-Nachtrag 30.10.2026 – Unabhängiger Owner: konkrete CIM-Zugriffsablehnung

Einmalfreigabe f4d9665f nach erneuter erfolgreicher Integritätsprüfung genau einmal
verwendet und vor jedem Erwerb/Start atomar verbraucht. PARTIAL/STOP VOR Owner-
Start: REGISTER_CIM / PermissionDenied, Microsoft.Management.Infrastructure.
CimException, CIM-Status 2, HRESULT 0x80131500. Keine Fristüberschreitung und
keine Sperre durch historischen Cleanup. Konkrete fehlende Rechte/Richtlinien
nicht festgestellt; Ursache des alten 07574fb1-Versuchs weiterhin unbewiesen.

Null Owner-/Worker-/Jobstarts; keine nativen Owneridentitäts-/Exit-/EOF-Belege.
Neue Beobachtersitzung 28324 / Toolreferenz 51102 zuletzt passiv offen gemeldet,
Teilregistrierungsabschluss unbestätigt. Keine nachträgliche Prüfung/Bereinigung
oder Wiederholung. Alte Sitzung und Quarantänen bleiben unberührt. Freigabe
verbraucht, nicht erneut verwendbar. Historische Ungewissheit separat erhalten.

78 Harness-Fake-/Metadatenfälle bestanden, darunter neue PermissionDenied-
Regression ohne Ownerstart, blinden Cleanup, Secrets oder Replay. Keine erneute
Java-/Gradle-/DB-Suite. Runtimeartefakte, alte Evidenzen, Manifeste und Manual-JAR
hashgleich; nur ein Testskript, neue Versuchsevidenzen und Dokumentnachträge.

Nächster sinnvoller Entwicklungsweg ohne Rechteausweitung: eigener eng benannter
Owner-Abschlussnachweis ohne globale WMI-Subscription, Messprofil explizit an
neue Freigabe binden. Native gehaltene Identität, Exitcode, getrennte EOF-/Close-
Belege bleiben Pflicht; ohne Ereignisstrom KEIN vollständiger Konsolen-/Global-
provenienznachweis. Danach private Job-/Workerzuordnung und Gruppencleanup real
belegen, bevor Browser und Produktintegration erfolgen. Workergrundarchitektur
nutzt native Jobmitgliedschaft und benötigt keine globale WMI-Überwachung.
Plan in TESTING.md, noch kein neuer freigabereifer Kandidat oder OS-Versuch.
Keine pauschale Administratorausführung oder Windows-/WMI-Rechteänderung.
Weitere native Ausführungen angehalten; Ziel und Prioritäten unverändert.

### T02-Nachtrag 30.10.2026 – Ownerabschluss ohne globale WMI-Abhängigkeit vorbereitet

Separater OWNER_COMPLETION_V1-Harness implementiert. Keine CIM-Registrierung,
globale Prozessinventur oder zusätzlichen Windows-Rechte erforderlich. Eigene
gehaltene native Identität, tatsächlicher Exitcode, getrennte stdout-/stderr-EOFs,
Stream-/Prozesshandleabschluss und feste Deadline bleiben verbindlich. Erfolg
heißt eng OWNER_COMPLETION_PASS; Konsolen-/Globalprovenienz ausdrücklich
NOT_MEASURED und ProvenanceAccepted=false. Alte Provenienzassertions bleiben
erhalten, historische Sitzungen weiterhin unbekannt und unverändert.

Eigener Namespace, Main-Einstieg, Manifest- und Approval-/Verbrauchspräfixe
verhindern Nutzung alter Full-Provenance-Freigaben. Diagnose begrenzt ohne freie
Rohdaten; Start-/Identitäts-/EOF-/Cleanupfehler bleiben STOP, keine automatische
Wiederholung oder Quarantäneaufhebung. Kein Architekturwechsel: die spätere
Workerabsicherung beruht weiterhin auf privatem Windows Job und gemeinsamem Slot.

Tatsächlich geprüft: 105 isolierte Javafälle (71 Worker +34 Owner), 0 Fehler/
übersprungen; 200 PowerShellfälle (44 neue +156 bisherige). Offline-Gradle nach
identischem Dry-Run erfolgreich; native Quellen ausschließlich kompiliert.
152 bisherige Evidenz-/Manifestdateien, Manual-JAR, Builddatei und historische
Dokumentpräfixe unverändert. Keine Datenbank/Anwendung/Browser/echte OS-Probe,
Installation oder Gitoperation. Kein neuer produktiver Sicherheitspass behauptet.

Kandidat 58195e20-5465-4330-871b-5df814cbf11a nur als Manifest kalt validiert:
Artefakt 7d90ec26f64a18538bdf4787248bba038efa96ef1b9ab9cf255c0ffa40ee5ad4.
Keine Approval-/Verbrauchsdatei. Nächste notwendige Freigabe: genau ein synthetischer
Owner-Abschlussversuch dieses Scopes ohne Worker, Job, Browser oder historische
Bereinigung; Details und Manifesthash in TESTING.md. Danach separat native
Job-/Workerzuordnung, Gruppenende, Browser-/SSRF-/Ressourcenbegrenzungen und
Integration real nachweisen. T02 bleibt offen, übrige Prioritäten unverändert.

### T02-Nachtrag 30.10.2026 – Ownerabschluss real belegt, privater Jobversuch vorbereitet

Einmaliger freigegebener Owner-Abschlussversuch 58195e20 erfolgreich:
OWNER_COMPLETION_PASS, genau ein Start, native gehaltene PID 10812 mit exakter
Erstellungszeit 134378472263984761, Exitcode 0, getrennte stdout-/stderr-EOFs,
vollständige eigene Stream-/Prozesshandleabschlüsse, 1494 ms, keine offenen eigenen
Ressourcen. Einmalfreigabe vorher atomar verbraucht. Null Worker-/Jobstarts.
Globale Prozess-/Konsolenprovenienz nicht gemessen; alte Sitzungen unberührt und
weiter unbekannt. Keine weitere native Ausführung nach dem Einzelversuch.

Anschließend kleinen synthetischen Jobnachweis über vorhandenen WorkerJobOwner,
Journal und nativen Adapter vorbereitet: ein Owner, ein Worker, eine private
Gruppe; atomare Jobzuordnung vor Resume, gehaltene Identitäten, echte Exit-/EOF-
und vollständige Gruppen-/Ressourcenbelege. Gemeinsamer dauerhafter Slot verhindert
Umgehung einer unbestätigten Jobquarantäne durch neue Versuchkennungen. Kein
Architekturwechsel, Dienst oder produktiver Seleniumanschluss. Keine alten
Evidenzen/Freigaben überschrieben, keine Schutzgarantie gelockert.

Nach Entwicklung 132 ausgewählte Javafälle und 227 PowerShellfälle bestanden,
keine Javafehler/übersprungenen Fälle. Offline-Gradle nach identischem Dry-Run;
native Quellen ausschließlich kompiliert. Neue Java-/PS-Hashberechnung identisch.
Historische 152 Dateien plus neun neue Owner-Evidenzen, Manual-JAR und Builddatei
unverändert. Prüfdetails und Messgrenzen im neuen TESTING.md-Nachtrag.

Kandidat b6f62923-d20b-4873-ac12-154d2bd1d3ed, WORKER_JOB_PROOF_V1,
Artefakt 618379385093568e7e6a597b5d0e5c02154b43c458e1ac27c7421ce44e9f6bce,
nur Manifest und kalte ValidateOnly-Prüfung. Keine Approval-/Verbrauchsdatei und
kein echter Job-/Workerstart. Neue konkrete OS-Freigabe hierfür erforderlich;
bisheriger Owner-Erfolg ist keine Worker-/Jobgenehmigung und keine Scraperfreigabe.
Danach weiterhin reale Fehler-/Timeoutfälle, Browser-/SSRF-/Ressourcengrenzen und
gesonderte Integration erforderlich. T02 nicht einsatzreif abgeschlossen;
fachliche Prioritäten und andere Tickets unverändert. Keine DB-/Anwendungs-/
Browser-/Scraperstarts, historischen Bereinigungen, Installationen oder Gitaktionen.

### T02-Nachtrag 30.10.2026 – Synthetischer Jobversuch: STOP, echte Quarantäne erhalten

Einmalig genehmigter Versuch b6f62923 ausgeführt, Freigabe vorher atomar
verbraucht. Owner 15044 und Worker 42196 mit exakten nativen Geburtszeiten
identifiziert, aber kein bestätigtes Jobende/Workerexit/Ressourcenende.
PARTIAL/STOP, QUARANTINED; gemeinsamer pending-/quarantine-Marker bleibt
unverändert. Äußere Sitzung 86300 zuletzt passiv offen; keine spätere native
Abfrage, Bereinigung oder Wiederholung. Kein weiterer Jobstart zulässig.
Roher Owner-ExitCode=0 bei ExitObserved=false bedeutet UNKNOWN, keinen Erfolg.

Konkreter primärer Abbruchgrund mangels alter Diagnose nicht nachgewiesen.
Interne I/O-/Session-/Snapshot-Ablehnungen waren nicht vollständig aufgezeichnet.
Passive begrenzte Phasen-/Fehler-/Zählerdiagnose ergänzt, unbekannten Exit im neuen
Report auf null abgebildet. Bestehende Sicherheitsbedingungen nicht gelockert.
Separaten flachen Temp-Cleanupfehler mit leerem JDK-Perfdata-Verzeichnis per
Dateifixtures reproduziert und eng korrigiert; nicht als Ursache dieses STOP
ausgeben. Keine Löschung von Live-Dateien, PID-Dateien oder Bestandsdaten.

142 ausgewählte Javafälle und 233 PowerShellfälle erfolgreich, keine finalen
Javafehler/übersprungenen Fälle. Offline-Gradle nach identischem Dry-Run;
native Quellen ausschließlich kompiliert. Ausgeführte Klassen/Skripte vor
Neukompilierung archiviert und Originalhash 61837938 exakt rekonstruiert.
Historische Evidenzen, Manual-JAR, Builddatei und Dokumenthistorie erhalten.
Keine weitere OS-Probe, DB, Anwendung, Browser/Scraper, Installation oder Gitaktion.

Nächster zwingender Schritt ist eine gesondert freigegebene identitätsgebundene
Bestandsprüfung/Bereinigung dieses unbestätigten Jobs. Im gehaltenen alten Owner
fehlt ein kooperativer Nach-STOP-Kanal; eine gegebenenfalls notwendige erzwungene
Beendigung seiner exakt identifizierten eigenen Prozessinstanz bedarf ausdrücklicher
Freigabe. Keine PID-only-Terminierung oder automatische Quarantäneaufhebung.
Konkreter begrenzter Ablauf in TESTING.md. Kein neuer Jobkandidat hinter der
Sperre vorbereitet. Status BLOCKED für neue echte Importe, T02 nicht abgeschlossen;
beschlossene Architektur, andere Tickets und fachliche Prioritäten bleiben erhalten.

### T02-Nachtrag 30.10.2026 – Bestehende Prozesse identitätsgebunden beendet

Separat freigegebener Cleanup des b6f62923-Versuchs: exakte native PID/Geburt
bestätigt, Worker 42196 bereits mit Exitcode 0 beendet; Owner 15044 nach erneuter
nativer Prüfung auf demselben gehaltenen Handle genau einmal erzwungen beendet,
Exitcode 0x4D4C0001. Beide zusätzlichen Prüfhandles nativ geschlossen.
Original-Toolsession 86300 über ihre Referenz per Ctrl+C beendet, Tool-Exitcode 1.
Kein neuer Owner/Worker/Job und keine andere/historische Sitzung verändert.

Sandbox verweigerte selbst lesenden Zugriff mit Fehler 5; freigegebene Prüfung
außerhalb der Sandbox erfolgreich. Am beendeten Worker real nachgewiesen:
Imageabfrage Fehler 31 trotz gültiger nativer PID/Geburt und Exitcode. Port eng
korrigiert: vorher gemessenes Image nur nach exakten Instanz-/End-/Exitbelegen
erhalten. Normales Root-Ende jetzt vor erstem Gruppensnapshot, keine neuen Retries,
keine Toleranz für instabile Beobachtungen, keine abgeschwächten Assertions.

153 Javafälle und 254 PowerShellfälle bestanden, keine finalen Javafehler oder
übersprungenen Fälle; Offline-Gradle nach identischem Dry-Run. Neue native
Verdrahtung nur kompiliert, noch kein neuer realer Job-PASS. Cleanup-Evidenz,
ausgeführtes Skript und Toolabschluss separat archiviert; Details in TESTING.md.
Historische Evidenzen, Guards, Manual-JAR und Builddatei unverändert.

Direkter Jobleerstand und ursprüngliche einzelne EOF-/Ledgerabschlüsse nachträglich
nicht gemessen. Historischer STOP bleibt gültig. pending/quarantine unverändert,
keine automatische Freigabe. Nächste echte Eigentümerentscheidung: OS-seitig
nachgewiesenen erzwungenen Cleanup als Grundlage für ausdrückliche Freigabe NUR
des Tickets cf728f97-7fbc-4ec6-8220-a27d9734f078 akzeptieren; keine allgemeine
Recoveryausnahme. Bis dahin kein neuer Jobkandidat/Import. Danach neuer gebundener
synthetischer Jobtest nur mit eigener OS-Freigabe; Browser-/SSRF-/Integration
weiter offen. Keine DB-/Browser-/Scraper-/Anwendungs-/Netzwerk-/Gitoperation,
keine Installation oder Änderung fachlicher Prioritäten. T02 bleibt offen.

### T02-Nachtrag 30.10.2026 – Ticket cf728f97 administrativ freigegeben

Ausdrückliche einmalige Eigentümerentscheidung nach bestätigten nativen Owner-/
Workerenden und Abschluss der Original-Toolsession umgesetzt. Ausschließlich
Ticket cf728f97-7fbc-4ec6-8220-a27d9734f078: Zuordnung, beide Markerhashes und
unveränderte Versuchs-/Cleanup-Nachweise geprüft. Beide Marker gemeinsam in
ticketgebundenes Archiv verschoben, bytegleich erhalten; Source-Slot nicht mehr
vorhanden. Entscheidung, Einmalverbrauch und tatsächliche Änderung separat
dokumentiert. Keine allgemeine Recovery, keine Änderung der Admissionregeln
und keine Freigabe anderer/zukünftiger Tickets. Wiederholung dauerhaft gesperrt.

Historischer Versuch b6f62923 bleibt PARTIAL/STOP. Ursprünglicher Jobleerstand
und vollständige ursprüngliche EOF-/Dispose-Nachweise nicht rekonstruierbar;
diese Einschränkung dauerhaft erhalten. ADMINISTRATIVELY_RELEASED bedeutet
keinen Import-PASS und keine nachträgliche erfolgreiche Gruppenbereinigung.

289 PowerShell-Fake-/Dateisystemfälle bestanden, davon 35 neue Releasefälle.
Keine neuen Java-/Gradle-Läufe; bisherige 153 Javafälle bleiben Baseline.
Keine neuen Owner-/Worker-/Job-, Browser-/Scraper-/Anwendungsstarts, DB-/Netzwerk-
oder Gitoperationen. Historische Evidenzen, Guards, Manual-JAR und Builddatei
unverändert. Neue Skripte und neue Dokumentnachträge, keine Produktcodeänderung.

Nach Freigabe frischen Kandidaten c21e147e-aa8d-4235-84d9-ff65c96f7e3b vorbereitet,
Profil WORKER_JOB_PROOF_V1, Artefakt
b66bfe3822bb2151b515254bf00ca531d07ac55816d7f54804dd8b369ffdbe85.
Nur Manifest und erfolgreiche kalte ValidateOnly-Prüfung, keine native Freigabe
oder Ausführung. Nächste tatsächliche Grenze: separate ausdrückliche Genehmigung
für genau einen synthetischen Owner/Worker/private Jobgruppe dieses Artefakts.
Keine alte OS-Freigabe wiederverwendet. Reale Validierung der Korrekturen sowie
Browser-/SSRF-/Timeout-/Integrationsnachweise weiterhin offen, T02 nicht erledigt.

### T02-Nachtrag – Jobversuch c21e147e

PARTIAL/STOP: Worker Exit 0, Gruppe und Ressourcen abgeschlossen, Journal leer;
Owner Exit 78 und eigene EOF-/Handleabschlüsse bestätigt. Jobtotal 2 statt 1.
Identität und Ursache der zweiten Zuordnung unbekannt; Konsolenbegleiter nur
Hypothese. Kein PASS und keine Wiederholung. T02 bleibt offen.
### T02-Nachtrag – Gruppenabschluss als synthetisches Kriterium

Historischer Versuch c21e147e bleibt PARTIAL/STOP; zweite Identität unbekannt.
Für künftige synthetische Versuche ist eine positive historische Zuordnungszahl
ausreichend, zusätzlich zu unveränderten Identitäts-, Gruppenende-, Kanal-,
Ressourcen- und Quarantänenachweisen. 154 Java- und 34 PowerShell-Fakefälle
bestanden. Kandidat 7af1c3c7-0eaa-4ff6-a218-33950de4af14 kalt validiert;
Artefakt 544ef1fd16006fc17172356328993f5b6fdbc53132ef1c896a9ccaa8ef8b9ad0.
Keine OS-Freigabe oder nativer Start. Reale Job-, Firefox-/Geckodriver- und
Integrationsnachweise bleiben offen.
### T02-Nachtrag – Synthetischer Jobabschluss real bestätigt

Einmaliger Versuch 7af1c3c7 WORKER_JOB_PROOF_PASS: Worker und Owner Exit 0,
stabil leerer Job, Kanal-/Ressourcen-/EOF-Abschluss, Journal frei. Historisches
Jobtotal 2, Identität weiterer ehemaliger Mitglieder nicht gemessen.
Früherer Versuch c21e147e unverändert PARTIAL/STOP. Kein Browser-/Scraperstart.
Reale Timeout-/Nachfahren-, Firefox-/Geckodriver-, SSRF- und Integrationsprüfungen
bleiben offen; T02 ist nicht einsatzreif abgeschlossen.
### T02-Nachtrag 30.10.2026 – Beide produktiven Importwege an Job-Worker angebunden

LiveScraperGateway führt Reihen- und Bandimporte jetzt ausschließlich über den
ProductionScraperClient, den gemeinsamen WorkerJobOwner und den privaten Windows-Job
zum isoliert verpackten LiveScraperWorkerMain/WorkerImportRunner. Kein direkter
Selenium-Fallback. Anwendung und Worker werden separat gebaut; das Manual-JAR
bleibt unverändert und ohne Live-Komponenten. Bestehende Job-/Quarantäneregeln,
DB-Guards, historische Evidenzen und fachliche Prioritäten bleiben erhalten.

SSRF-Grenze implementiert: fester HTTPS-CONNECT-Proxy mit enger Domain-/Portallowlist,
Prüfung aller DNS-Antworten und IP-gebundenem connect, auch für Redirect- und
Unterressourcenziele; Firefox-Proxy ohne Loopback-/Direct-Ausnahme sowie Begrenzung
weiterer Netzwerkkanäle. Gemeinsamer Import-Slot, bestehende Timeouts, erneute
Artefaktpins vor jedem Start, begrenzte IPC-/Transportdaten und sicherer Abschluss
eigener Prozess-/Tempressourcen. Erfolg nur bei vollständig bestätigtem Abschluss.

Offline gebaut und datenbankfrei geprüft: 176 Fälle bestanden (3 Zielpolicy,
17 produktive Gateway-/Worker-/Verpackungs-/Konfigurationsfälle, 122 Ownerfälle,
34 Owner-Protokollfälle), 0 Fehler/übersprungen im abschließenden Wiederholungslauf.
Spring-/JPA-/Manual-Vollsuiten nicht ausgeführt; nur Testquellen kompiliert.
Details und Konfigurationsvoraussetzungen im neuen TESTING.md-Nachtrag.

Noch keine Einsatzreife oder produktive Freigabe: Firefox-/Geckodriver-Verhalten,
reale Proxy-/Redirect-/Unterressourcengrenze, Timeout/Nachfahren und anschließend
geschützte Anwendungsregressionen benötigen gesonderte Ausführungsfreigaben.
Browserpräferenzen/Mocktests sind kein OS-Netzwerkisolationsnachweis. Enge Allowlist
und Budgets müssen gegen reale Importfunktionen geprüft werden. Keine neue native
Freigabe erzeugt/verbraucht; keine echten Prozesse, DB, Anwendung, Browser oder
Netzwerk gestartet. T02 bleibt offen; T03 und weitere Tickets nicht vorgezogen.

### T02-Nachtrag 30.10.2026 – Reale Scraperabnahme konkret vorbereitet

Zwei getrennte, noch nicht ausgeführte und nicht genehmigte Kandidaten:
- Zuerst BROWSER_BOUNDARY_V1, Kennung f78ac96a-3f23-476c-83dd-ee417eb0f8e6,
  Manifest-/Bundle-SHA256 5024bf75ad70f45fcdc991908bcdfd03c652fdca0b5af516d627ebe41a7cc671.
  Ein Owner/Worker/privater Job und eine Firefox-/Geckodriver-Sitzung: feste lokale
  Fixtures, echte HTTP-302-, Unterressourcen-, Navigations- und Proxy-Ausfallfälle,
  erwartete Proxyablehnung ohne eigenen Canarykontakt oder Upstream-DNS/-TCP.
- Danach nur mit eigenständiger Freigabe LIVE_IMPORTS_V1, Kennung
  881e7aab-f5a9-4b5d-a728-a7e7b48780e9, Manifest-/Bundle-SHA256
  ca0f024755f14d676bc03e94f1ae42f2f7a06e0ea9bf75e9065228f7e2e5cc1a.
  Ein Owner und höchstens zwei sequenzielle produktive Worker/Jobs/Browser-Sitzungen
  für die vorhandenen Beispielindizes 2502 (Reihe) und 15295 (Band).

Eigene Wrapper mit kaltem ValidateOnly, hashgebundenem Manifest, dauerhaft atomarem
Einmalverbrauch vor Ownerstart, ursprünglichem gehaltenem Ownerhandle/native Zeit,
begrenztem Jobmitgliedschaftsnachweis der tatsächlichen Firefox-/Geckoimages und
getrennten Ende-/Exit-/EOF-/Ressourcenbelegen. Keine globale WMI-Registrierung,
PID-only-Besitzannahme, fremde Prozessbereinigung oder pauschale Quarantänefreigabe.
Keine ausgeführten nativen Versuche; keine echte Approval-/Verbrauchsdatei angelegt.

Vorbereitungsfehler korrigiert: Selenium 4.18 setzt acceptInsecureCerts standardmäßig
auf true. Produktive Firefoxoption jetzt ausdrücklich false, Regressionassertion
bestanden. Begrenzte Proxy-/Fehlertypdiagnose ergänzt, ohne sensible Rohdaten oder
Änderung des öffentlichen Fehlerpayloads. Produktive Allowlist unverändert.

197 datenbankfreie Javafälle bestanden, 0 Fehler/übersprungen; Offline-Builds
beider Abnahmeartefakte und des Produktworkers erfolgreich, Taskplan jeweils vorher
identisch offline geprüft. Zusätzlich vier kalte PowerShell-Negativprüfungen und
beide endgültigen ValidateOnly-Läufe erfolgreich. Manual-JAR und geprüfte
historische Nachweise hashgleich. Keine DB, Anwendung, Browser, Scraper oder
nativen Windows-Prozess-/Jobversuche gestartet, keine Netzwerk-/Gitoperationen.

Details, aktive Manifeste, Freigabeverfahren, Fristen und Aussagegrenzen im neuen
TESTING.md-Nachtrag. Noch keine Einsatzreife: reale Kompatibilität, endliche
Browser-/SSRF-Matrix und später gezielte Timeout-/Nachfahrenabnahme bleiben offen.
Ein SCOPED_PASS wäre kein universeller OS-Netzwerkisolationsnachweis. T02 weiterhin
offen; T03 und andere fachliche Prioritäten unverändert.

### T02-Nachtrag 30.10.2026 – Reale Firefox-/Jobgrenze teilweise bestätigt

Einmalig genehmigter BROWSER_BOUNDARY_V1-Versuch f78ac96a ausgeführt:
PARTIAL/STOP, nicht PASS. Native Jobmitgliedschaft von Worker, Geckodriver und
Firefox tatsächlich gemessen. Vollständiger Job-/Worker-/Ownerabschluss,
Exitcodes, beide Owner-EOFs sowie Stream-/Handleabschluss bestätigt; keine
unbestätigten eigenen Ressourcen. Keine Quarantäne administrativ aufgehoben.

Bild-, iframe- und Fetchanfragen am Proxy abgewiesen; Canarykontakte und am Proxy
gemessene Upstream-DNS-/TCP-Verbindungen jeweils 0. Globale Netzwerkisolierung
weiterhin nicht bewiesen. WebSocket-, Redirect-, Navigations- und HTTPS-Fälle
teilweise ungemessen: Bearbeitungsbudget 256 bei 775 Verbindungsannahmen erschöpft;
Proxy lieferte bisher bei Ablehnung keine terminale HTTP-Antwort. Retries als
Ursache plausibel, nicht vollständig rekonstruiert. WebSocketkorrelation war
zusätzlich zu eng. Ausgeführtes Ergebnis und Artefakte unverändert archiviert.

Anschließend ausschließlich offline korrigiert: feste 403/429/502-Fehlerantworten
und präzisierte WebSocket-Korrelation, ohne Allowlist-/TLS-/Budgetlockerung.
200 datenbankfreie Javafälle bestanden, 0 Fehler/übersprungen. Kein zweiter
nativer Versuch und keine Live-Imports/DB-/MangaLib-/Gitoperationen.

LIVE_IMPORTS_V1 noch nicht empfohlen; alte Binding nach Neubau ungültig. Zuerst
fehlende Browserfälle nachweisen. Neuer Grenztest nur vorbereitet und kalt validiert:
Kennung 18896811-5e95-41df-af61-25120b484c40, Manifest-/Bundle-SHA256
e0e069c4bc09560987b19cc609b2730a850ced96f829ba765c0884983af69c24.
Keine neue Freigabe/Verbrauchsdatei; gesonderte Eigentümerfreigabe erforderlich.
Details und verbleibende Sicherheitsgrenzen im neuen TESTING.md-Nachtrag.
T02 bleibt offen; funktionale Prioritäten und T03 unverändert.

### T02-Nachtrag 31.10.2026 – Korrigierter lokaler Browsergrenztest bestanden

Einmalig freigegebener BROWSER_BOUNDARY_V1-Versuch 18896811-5e95-41df-af61-25120b484c40
mit Manifest e0e069c4bc09560987b19cc609b2730a850ced96f829ba765c0884983af69c24:
SCOPED_PASS. Bild, iframe, Fetch, WebSocket, beide HTTP-302-Ziele, Navigation und
HTTPS-Loopback am Proxy abgewiesen. Proxy-Ausfall ohne Canarykontakt; insgesamt
Canarykontakte 0, Proxy-Upstream-DNS/Connects 0, 21 TCP-Annahmen/13 Ablehnungen.
Native Jobmitgliedschaft von Worker, Geckodriver, Firefox und beobachteten Begleitern
bestätigt. Stabiler Jobleerstand, Worker-/Owner-Exitcode 0, getrennte EOFs sowie
Ressourcen-/Handleabschluss nachgewiesen; keine verbleibende pending/quarantine.
Historischer Vorversuch bleibt PARTIAL/STOP; Evidenzen und Artefakte erhalten.

Dies ist eine endliche Browsermatrix, keine globale Netzwerk-/Konsolenprovenienz
oder OS-Netzwerkisolierung. Keine echte Import-/DB-/Anwendungsausführung, keine
Gitoperationen. Kein neuer Code-/Build-/JUnit-Lauf erforderlich. Dokumentation
und kalte Integritäts-/Folgekandidatenprüfungen, kein zweiter nativer Versuch.

Als nächster Schritt sind zwei echte Imports mit separater Freigabe empfohlen:
LIVE_IMPORTS_V1, neue Kennung 9fecde5b-05c6-4058-8ec5-8d733b4c3915, Manifest-SHA256
8a5ddc993b2f98b53025bf9d6285212d20db6cec110d8d06eeb860443637d03c.
Kalt validiert, nicht genehmigt/ausgeführt: ein Owner, höchstens zwei sequenzielle
Worker/Jobs/Browser für Reihe 2502 und Band 15295; nur Produktallowlist über den
Proxy, vollständiger Abschluss des ersten Imports vor dem zweiten. Details im
neuen TESTING.md-Nachtrag. Timeout-/Nachfahren- und Betriebsnachweise bleiben offen.
T02 weiterhin nicht insgesamt abgeschlossen; Prioritäten und T03 unverändert.

### T02-Nachtrag 31.10.2026 – Beide produktiven Importwege real bestätigt

LIVE_IMPORTS_V1, Kennung 9fecde5b-05c6-4058-8ec5-8d733b4c3915, gebundenes Manifest
8a5ddc993b2f98b53025bf9d6285212d20db6cec110d8d06eeb860443637d03c:
einmalig nach ausdrücklicher Freigabe ausgeführt, SCOPED_PASS in 20874 ms.
Reihe 2502 lieferte 18 Felder, danach Band 15295 11 Felder, jeweils gültiger
nichtleerer Titel gemäß Harnessprüfung. Zweiter Import erst nach bestätigtem
vollständigem Abschluss/IDLE/freiem Slot des ersten. Native Jobmitgliedschaft von
Worker, Geckodriver und Firefox beider Sitzungen gemessen; beide Worker und Owner
Exitcode 0, stabiler Jobleerstand, getrennte EOFs und vollständiger Ressourcenabschluss.
Proxy pro Import: 21/22 Annahmen, jeweils 5 DNS/5 Upstreamverbindungen, 16/17
Ablehnungen. Kein verbleibendes pending/quarantine, keine administrative Aufhebung.

Originale Evidenzen und ausgeführte Artefakte gesichert, Manual-JAR unverändert.
Browsergrenztest bleibt SCOPED_PASS; keine globale OS-Netzwerkisolierung bewiesen.
Konkrete Nutzdaten/Feldschlüssel und Einzellaufzeiten wurden im eingefrorenen
Live-Harness nicht persistiert; keine vollständige Inhalts-/JSON-Abnahme behauptet.
Keine DB-/MangaLib-Anwendungsstarts, Installationen, Gitoperationen oder zusätzliche
reale Versuche. Kein Codefehler nachgewiesen; keine neuen Builds/Unit-Testläufe nötig.

Für T02 noch offen: gezielter realer Timeout-/Nachfahrenabschluss mit konservativer
Quarantäne bei Ungewissheit sowie abschließende Paket-/Konfigurations-/Regressions-
abnahme vor allgemeiner Nutzung. Dafür separate konkrete Ausführungsfreigaben,
keine automatische Wiederholung der jetzt verbrauchten Freigabe. Einzelheiten und
Aussagegrenzen im neuen TESTING.md-Nachtrag. T02 nicht pauschal abgeschlossen;
Prioritäten und T03/T04/T06 unverändert.

### T02-Nachtrag 31.10.2026 – Timeout-/Nachfahrenabnahme vorbereitet

BROWSER_TIMEOUT_V1, Kennung 8a142689-7f61-47e1-b3f1-c680281a4e7c,
Manifest-SHA256 98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc.
Kalt validierter Einmal-Kandidat, NICHT genehmigt/ausgeführt. Genau ein
Owner/Worker/privater Job/Firefox-Gecko, nur about:blank und lokale Steuerkanäle;
Abnahmeproxy weist alle Requests vor DNS/Connect ab. Unveränderte produktive
Owner-/Native-/Journalarchitektur, keine produktiven Imports oder neue Infrastruktur.
Manifestfristen: Start 30 s, Import-Timeout 45 s, Cleanup 30 s, innere äußere
Beobachtung 100 s. Produktionsbudgets unverändert.

Vor Frist aktive Jobmitgliedschaft von Geckodriver und mindestens zwei verschiedenen
Firefoxinstanzen; nach Frist gesamter eigener Jobabschluss, Fehlerexit, getrennte
EOFs und Ressourcenabschluss erforderlich. Slot vor Abschluss gesperrt; unbestätigter
Abschluss bleibt quarantänisiert. Kein zweiter nativer Start zum Slotcheck,
entsprechende Admission-/Quarantänenegativfälle mit Fakes abgesichert. Keine
exakte Gesamtprozesszahl oder globale OS-Provenienz als Erfolgskriterium.

220 datenbankfreie Javafälle bestanden, 0 Fehler/übersprungen, einschließlich
20 neuer Fälle; Offline-Kompilierung/Paketierung erfolgreich. Zwei kalte
PowerShell-Negativfälle und Skriptparser erfolgreich. Zwischenfehler im neuen
Testcode sowie einmaliger bestehender Fake-Nebenläufigkeitsfehler transparent im
TESTING.md-Nachtrag dokumentiert; letzterer danach gezielt und vollständig
bestanden, Ursache des einmaligen Hängers nicht bewiesen. Keine Assertion gelockert.
Originalevidenzen/SCOPED_PASS/Archivartefakte und Manual-JAR hashgleich erhalten,
Produktworker gegenüber Live-Abnahme unverändert. Keine DB-/Anwendungs-/Browser-/
Netzwerk-/nativen Abnahme- oder Gitoperationen, keine neuen Abhängigkeiten.

READY_FOR_TIMEOUT_OS_APPROVAL: separate einmalige Eigentümerfreigabe an obige
Kennung/Hash erforderlich. Danach bleiben abschließende Paket-/Konfigurations-
und geschützte Regressionsabnahmen sowie bekannte Aussagegrenzen vor vollständiger
T02-Freigabe zu bewerten. T02 nicht pauschal abgeschlossen, Prioritäten und
T03/T04/T06 unverändert. Details/STOP-Regeln im neuen TESTING.md-Nachtrag.

### T02-Nachtrag 02.11.2026 – Konkrete Timeout-Ausführungsfreigabe noch erforderlich

Kalter Kandidat 8a142689-7f61-47e1-b3f1-c680281a4e7c / Manifest
98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc erneut
integritätsgeprüft. Automatische Freigabeprüfung lehnte den geplanten Einmalstart
vor Kommandostart wegen fehlender konkreter Eigentümerfreigabe ab. Keine Umgehung;
kein nativer Start/Verbrauch, keine DB-Verbindung. Vorbereitete approval.txt ist
vorhanden, aber kein consumed/binding/Ergebnis; Einzelheiten im TESTING.md-Nachtrag.
Kein neuer Test-PASS oder nativer Fehlversuch. Manual-JAR und historische
SCOPED_PASS-Ergebnisse unverändert. BLOCKED_AT_EXECUTION_APPROVAL bis zur
konkreten separaten Einmalfreigabe. Keine Änderung fachlicher Prioritäten/T03.

### T02-Nachtrag 02.11.2026 – Realer Timeout-/Nachfahrennachweis bestanden

Nach ausdrücklicher konkreter Einmalfreigabe BROWSER_TIMEOUT_V1 / Versuch
8a142689-7f61-47e1-b3f1-c680281a4e7c / Manifest
98ada2e611702c39c9a6b8f34b3544b0a78df9313e517825b3f8ba0db683fadc
exakt einmal ausgeführt: SCOPED_PASS. Terminierung der eigenen Jobgruppe nach
45007 ms, vollständige Rückkehr nach 45851 ms; Gesamtlauf inklusive äußerem
Ownerabschluss 48731 ms. Firefox/Gecko/Nachfahren nativ als Jobmitglieder bestätigt.
Keine frühe Slotfreigabe; Workerexit 1 als erwarteter Timeout, beide Worker-EOFs,
stabiler Jobleerstand und vollständiger Ressourcenabschluss bestätigt. Danach
Owner IDLE/freies Journal und CLOSED; Ownerexit 0, getrennte EOFs und ursprünglicher
Handleabschluss bestätigt. Kein verbleibendes pending/quarantine, keine
administrative Aufhebung oder zweite Ausführung.

Originalevidenzen/ausgeführte Artefakte archiviert, frühere SCOPED_PASS/PARTIAL-
Ergebnisse und Manual-JAR hashgleich erhalten. Keine Produktionscodekorrektur
nötig. Kein DB-/MangaLib-Anwendungsstart, keine externen Abrufe oder Gitoperationen.
Keine globale OS-Netzwerkisolierung behauptet; Details im neuen TESTING.md-Nachtrag.

Die zentralen realen Scraper-/Prozessnachweise liegen damit vor. Nächster Schritt
ist die gesondert freizugebende geschützte Gesamtregression/Konfigurationsabnahme
gegen mangalib_test mit Guards, transaktionalen Fixtures/Rollback und unverändertem
Vorher-/Nachher-Daten-/Schemasnapshot. Keine weitere synthetische OS-Testreihe
vorgesehen. T02 bis Abschluss dieser Prüfung und Abgleich der Restpunkte offen;
Prioritäten sowie T03/T04/T06 unverändert.

### T02-Nachtrag 02.11.2026 – Scraperintegration vollständig geschützt regressionsgeprüft

Nach ausdrücklicher Freigabe ausschließlich mangalib_test: zuerst 44 datenbankfreie
Guards bestanden, dann separater lesender Ausgangssnapshot, ungefilterter erneuter
Volltest und separater Abschlusssnapshot. 1016 Javafälle bestanden, 0 Fehler/
übersprungen; darunter 29 MVC-/JSON-, 34 M06-, 40 Altbestands- und 48 Requestlimit-
Integrationsfälle. Zusätzlich 198 DOM-Fälle bestanden, 0 Fehler/übersprungen.
assemble nach geprüftem Offline-Dry-Run erfolgreich. Alle Gradle-Aufrufe offline,
no-daemon und mit vorherigem identischem Dry-Run; keine Test-/Guardänderungen.

Vorher-/Nachher-Daten-/Schemafingerabdruck exakt identisch:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Zehn Tabellen/78 Stammdaten unverändert, fünf Geschäftstabellen weiterhin leer,
keine Fixtures verblieben. Nur bestehende transaktionale Fixtures mit Rollback;
keine Produktiv-/Manual-DB, Schemaänderung, Migration, Bereinigung oder Seeding.

Volltest mit test --rerun-tasks statt clean test zum Erhalt des eingefrorenen
Manual-JARs/der Abnahmeartefakte; kein neuer Clean-Test behauptet. Boot-Pakettrennung
statisch bestätigt, Manual-JAR/Produktworker und drei SCOPED_PASS-Evidenzen
hashgleich erhalten. Keine Anwendung/Browser/Live-Scraper/Gitoperationen.
Details, Zählungen und gesicherte Vollberichte im neuen TESTING.md-Nachtrag.

Scraperumfang im nachgewiesenen Umfang technisch validiert; keine zusätzliche
synthetische Prozessprobe nötig. Keine globale OS-Netzwerkisolierung/Produktiv-
bereitstellung behauptet. T02 insgesamt erst nach Abgleich der übrigen offenen
Browser-/UX-/Dependency-/Diagnose- und Bestandsnachweisgrenzen abschließen;
keine automatische Erledigung dieser Punkte durch den Volltest. T03/T04/T06 und
fachliche Prioritäten unverändert, keine vorgezogene Implementierung.

## Nachtrag 02.11.2026 – T02-Dependency-Befunde gezielt korrigiert

Aktuelle Bewertung mit offiziellen Herstellerquellen: DEPENDENCY_SECURITY_REVIEW.md.
Tomcat core/el/websocket gemeinsam von 11.0.24 auf 11.0.27; keine Änderung an
Boot 4.1.1/Java 21/Gradle 8.14.3. Ungenutztes jsoup und WebDriverManager entfernt,
Selenium-Module ohne alten Aggregator aus vorhandener Boot-BOM (4.43.0).
Keine Änderung fachlicher Regeln, M06 oder produktiver Import-/Job-/Proxylogik.

Neue ausdrücklich freigegebene geschützte Regression: zuerst 44 DB-freie Guards,
danach 1016 Javafälle einschließlich 29 MVC-/JSON-Verträge bestanden; 23 separate
Paket-/Policyfälle und 206 DOMfälle bestanden, jeweils keine Fehler/übersprungen.
Daten-/Schemafingerabdruck vor/nach erneut identisch:
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570.
Zehn Tabellen, 78 Stammdaten, keine verbliebenen Fixtures; AUTO_INCREMENT ausgenommen.
Nur mangalib_test, keine Produktiv-/Manual-DB. Ausführungsdetails in TESTING.md.

Dependency-Block technisch geprüft; echte negative UI-Abnahme (413/415, Erhalt
von Eingaben und Replay-Sperre) noch offen. Deshalb T02 NICHT insgesamt als
abgeschlossen markieren und noch kein Abschlusscommit. Aktuelle Abschlussmatrix:
T02_CLOSEOUT.md. Historische Hinweise auf fehlenden Herstellerabgleich sind
überholt, historische Ergebnisse/Evidenzen bleiben erhalten. Kein Anspruch
allgemeiner Schwachstellenfreiheit oder globaler OS-Netzwerkisolierung.
Prioritäten unverändert; T03/T04/T05/T06 bleiben abgegrenzt, T03 nicht begonnen.

## Nachtrag 02.11.2026 – Echte 413-Dialogabnahme erfolgreich; 415 noch offen

Gepatchten hashgebundenen manuellen Kandidaten nach gesonderter Freigabe genau
 einmal auf IPv4-Loopback gestartet, Testbanner/Profil/Identität bestätigt.
In-app-Browser: negativer 4097-Zeichen-Titel mit zusätzlicher Bandanzahl 0;
Größenlimit-Text, Eingabenerhalt, bedienbares Cancel/Wiederöffnen, Save-Sperre
bis Reload und erneute Freigabe nach Reload bestätigt. Keine Alerts oder
error/warn-Konsolenmeldungen. HTTP-Status nicht direkt beobachtbar; nicht allein
aus UI-Meldung abgeleitet. Keine weitere Schreibaktion.

Manueller Daten-/Schema-Snapshot vor/nach identisch (elf Reihen, 28 Bände, fünf
Einkaufspositionen, keine Änderung). Instanz anschließend geordnet per Ctrl+C
beendet, Graceful shutdown bestätigt, Port 18080 frei. Historische JARs/Evidenzen
unverändert. Produktive Datenbank nicht angesprochen.

Letzter T02-Abschlussblock: echte 415-UI-Probe. Die reguläre UI sendet nur JSON,
der vorhandene Browserconnector gestattet weder veränderndes evaluate noch
Request-Interception. Deshalb Eigentümerprobe statt Umgehung/neuer Infrastruktur;
konkreter Ablauf in MANUAL_TESTING.md. Automatisierte 415-Verträge bestehen,
aber ersetzen die ausdrücklich geforderte echte UI-Abnahme nicht. T02 daher
noch nicht abgeschlossen, kein Staging/Abschlusscommit; T03 nicht begonnen.

## Abschlussnachtrag 02.11.2026 – T02 abgeschlossen, T03 als nächster Auftrag

T02 technisch abgeschlossen für das freigegebene lokale Einzelbenutzermodell;
keine Änderung der Muss-/Soll-/Kann-Prioritäten. Maßgeblich T02_CLOSEOUT.md.
Letzter offener Fall durch Projekteigentümer selbst bestätigt: HTTP 415,
verständliche Fehlermeldung, kein Replay, Daten-/Schema-Snapshot identisch,
kontrollierter Shutdown, Port 18080 frei. Nicht als Codex-Test ausgeben.
Codex-413-UI-Probe erfolgreich hinsichtlich Text/Eingabenerhalt/Sperre bis Reload;
HTTP-Status dabei nicht unmittelbar beobachtet. Historische Grenzen erhalten.

Abgeschlossener automatisierter Prüfstand: 1016 Java-/206 DOMfälle, einschließlich
29 MVC-/JSON-Verträge, jeweils ohne Fehler/übersprungene Fälle; unveränderter
Daten-/Schemafingerabdruck auf mangalib_test, zehn Tabellen und 78 Stammdaten.
Produktive isolierte Importe beider Wege, Job-/Timeout-/Nachfahren-/Proxygrenzen
real im dokumentierten Umfang nachgewiesen; Tomcat 11.0.27 und Dependency-
Bereinigung geschützt regressionsgeprüft. Keine neuen Ausführungsversuche im
Abschlussauftrag und keine Änderung bestehender Daten oder Sicherheitsguards.

Lokaler Abschlusscommit nach statischem Review ausdrücklich freigegeben, kein
Push/Pull/Merge/Rebase/Tag/Branchwechsel. Private Pfade aus vier Hilfsquellen
profilbezogen abgeleitet, feste Hashpins unverändert; keine produktive Logik-
änderung, kein Lauf dieser Hilfen. Historische Originale/Artefakte/Evidenzen
bleiben lokal. Acht nicht an Gradle angebundene Prototypdateien unter experiments/
windows-job-prototype bleiben bewusst unversioniert, unverändert und ungestartet.

Restrisiken/Aussagegrenzen bleiben: keine globale OS-Netzwerkisolierung,
vollständige Prozessprovenienz, allgemeine Schwachstellenfreiheit oder Prüfung
jeder produktiven Altzeile. Rohe CL+TE-Ablehnung zurückgestellt; Logging T06,
späteres LAN separat. Nächster Schritt T03-0 gemäß T03_IMPLEMENTATION_PLAN.md:
zuerst datenbankfreie Charakterisierung der Kauf-/Fehler-/Wiederholungsfälle und
Klärung der Fachentscheidungen, noch keine T03-Implementierung.


## Nachtrag 02.11.2026 – T03-1/T03-2 implementiert, datenbankfrei geprüft

`IMPLEMENTED_DB_FREE_VERIFIED`: gemeinsame Kaufoperation, eigener Transaktionsrahmen,
Mutex bis nach Managerabschluss, Einkaufs-/Reihenlocks, Wiederholungsschutz und
konservative Zielidentifikation. Normale Bibliotheks-/Band-/Einkaufswrites teilen
innerhalb einer JVM die Schreibgrenze. Eigentümerfreigabe für feste 200-/409-
Ergebnisse und ausdrückliche Bestätigung des neuen Bandnummernbereichs umgesetzt.
Alte Bandwerte/M06 erhalten; exakte Restcents nur für neue Kaufbände, keine
allgemeine Preis-/Bestandskorrektur. 462 ausgewählte datenbankfreie Java- und
225 DOM-Tests bestanden, jeweils 0 Fehler/übersprungen. Kein DB-/Browserlauf.

**T03 insgesamt weiterhin offen.** Echte Commit-/Rollback-/Lock-/Parallelitäts-
nachweise fehlen. Vor der nächsten geschützten Vollsuite müssen äußere
@Transactional-Schreibtests sicher an die neue eigene Kauftransaktion angepasst
werden, ohne unbemerkt dauerhafte Fixtures zu erzeugen. T03-6 benötigt gesonderte
mangalib_test-Freigabe; keine automatische Bereinigung. Bandbereichsbestätigung
beweist keine unbekannte externe Bandidentität. T03-3 (globale Zuordnung/Aggregate),
T03-4 (globale Preise/Double), T03-5 (normale Anzahlkorrektur/-reduktion) bleiben
abgegrenzt. Bestehende T02-/M06-Abnahmen und Muss-/Soll-/Kann-Prioritäten unverändert.
Details, D1–D6, Schreibpfade und Abnahmeplan: T03_IMPLEMENTATION_PLAN.md Abschnitt 11;
ausgeführte Prüfungen und Grenzen: TESTING.md. Keine DB-Verbindung oder Datenänderung.

### Nachtrag 04.11.2026 – T03-3 datenbankfrei umgesetzt

`IMPLEMENTED_DB_FREE_VERIFIED`: gemeinsame geprüfte Bandidentitäten aus ID,
Bandnummer und Reihenbeziehung statt Repositoryposition; kontrollierte Ablehnung
mehrdeutiger Bestände. M06-Append und PurchaseService verwenden dieselbe Prüfung.
Summen aus tatsächlichen Bandpreisen/-anpassungen; keine NULL-als-Nullpreis-Regel.
Mittelwert bei finanzieller Einzelbandänderung aus tatsächlicher Bandanzahl und
bei Reihenpreisänderung auch ohne Anzahländerung aktualisiert. Altbände/M06 und
Kaufbestätigung erhalten. 417 datenbankfreie Java-Tests erfolgreich, keine DB-Abnahme.
Integrationsanpassungsmatrix in T03_IMPLEMENTATION_PLAN.md Abschnitt 12 vorbereitet;
keine automatischen Commit-Tests, keine Änderung der Rollback-/Datenbankguards.
T03 insgesamt offen: globale Preise/Restcents (T03-4), Anzahlkorrektur/-reduktion
(T03-5), echte Commit-/Rollback-/Konkurrenz- und Browserabnahme (T03-6).
Prioritäten und historische Nachweise unverändert.

### Nachtrag 04.11.2026 – T03-4 Preisregeln / D5 Variante C

Gemeinsame reine Centarithmetik für Anlage, Erweiterung und Kauf; deterministische
signierte Restcentverteilung nach geprüfter Bandnummer. Pflichtpreise bei Anlage,
explizite 0 erlaubt, keine neue Preisunbekanntheit durch Leeren und keine
Normalisierung historischer NULL-Werte. Ausdrückliche Preisoptionen im Reihen-
formular, abwesende Preisfelder bleiben unverändert. Einkauf durchgängig BigDecimal;
gemeinsame T03-Koordination, M06-Altbanderhaltung und Kaufbestätigung erhalten.
Details und aktuelle Prüfergebnisse: T03_IMPLEMENTATION_PLAN.md §13 / TESTING.md.
Echte DECIMAL-/Commit-/Rollback-/Konkurrenz- und Browsernachweise weiterhin offen;
T03-5 und T03-6 bleiben Folgearbeiten. Keine Datenbankabnahme oder vollständigen
T03-Abschluss behaupten; Muss-/Soll-/Kann-Prioritäten unverändert.

Implementierungsstatus `IMPLEMENTED_DB_FREE_VERIFIED`: final 446 datenbankfreie
Java-Tests und 231 DOM-Tests bestanden, jeweils 0 Fehler/übersprungen.

### Nachtrag 04.11.2026 – T03-5 / sichere Anzahlkorrekturen

D4 Variante A implementiert: Reduktionen unter tatsächlichen Bestand und
Teilkorrekturen ohne Anlage kontrolliert gesperrt. Reine Korrektur auf den
konsistenten tatsächlichen Bestand ändert nur die Deklaration; keine Preis-,
Lese-/Reread- oder Bandmutation. Ausdrückliche Erweiterungsoption erhält M06 auch
bei abweichender Deklaration; keine Kaufnebenkorrektur. Historisch abweichende
Preisaggregate bleiben bei reiner Korrektur erhalten, keine automatische Reparatur.
Aktueller Nachweis und Grenzen: TESTING.md / T03_IMPLEMENTATION_PLAN.md §14.
T03 insgesamt weiterhin offen: echte Commit-/Rollback-/Konkurrenz-/Browserabnahme
in T03-6 steht aus und benötigt gesonderte Freigaben. Prioritäten unverändert.

`IMPLEMENTED_DB_FREE_VERIFIED`: 468 datenbankfreie Java-Tests und 232 DOM-Tests
bestanden, jeweils 0 Fehler/übersprungen. Keine vollständige T03-/DB-Abnahme.

### Nachtrag 04.11.2026 – T03-6A Integrationsvorbereitung

13 DB-abhängige Bootklassen/75 Testmethoden samt Fixture-/Transaktionsstrategie
inventarisiert. Getrennte Opt-in-Commitstruktur mit vorhandenen DB-Guards, eindeutiger
Laufkennung, dauerhaftem ID-Journal und Fremddatenerhaltungsprüfung vorbereitet.
Keine automatische Bereinigung. Erste drei Commit-/Rollback-/Konkurrenzfälle nur
kompiliert; keine DB-Abnahme. Erweiterte E1–E6- und bestehende HTTP-Schreibtests
müssen vor der vollständigen Integrationsfreigabe weiter übertragen werden.
Freigabestufen und spätere Browsercheckliste: T03_6B_APPROVAL.md.
T03 bleibt offen, keine Änderung fachlicher Prioritäten oder bestehender Guards.

Vorbereitungsstatus `T03_6A_READY_FOR_APPROVAL` ausschließlich für die dokumentierte
erste Opt-in-Stufe. 475 datenbankfreie Javafälle erfolgreich, 0 Fehler/übersprungen;
Commit-Testquellen nur kompiliert. Keine DB-/Browserausführung, keine Bereinigung.
