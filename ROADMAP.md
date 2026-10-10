# MangaLib – Entwicklungsroadmap

> **Status:** Planungsgrundlage, Stand 10.10.2026
> **Geltungsbereich:** MangaLib (Java 21, Spring Boot 4.1.1, Spring Data JPA, MySQL, Thymeleaf, JavaScript, Gradle).
> **Verbindlichkeit:** Die Kategorien **Muss / Soll / Kann** geben die fachlichen Prioritäten des Projekteigentümers wieder. Technische Voraussetzungen dürfen in der Umsetzungsreihenfolge vorgezogen werden; eine fachliche Umpriorisierung erfolgt nur nach Rücksprache.

## 1. Grundsätze und Definition of Done


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
- **Verhalten erhalten:** Bestehende Funktionen und das aktuelle UI-Design standardmäßig beibehalten. Neue Ansichten und Interaktionen stilistisch integrieren.
- **Guter Code:** Lesbare, wartbare Lösungen; komplexe Entscheidungen, Geschäftsregeln, SQL-Abfragen und nicht offensichtliche Abläufe mit sinnvollen Kommentaren/Javadoc erklären, ohne jede triviale Zeile zu kommentieren.
- **Sicher ändern:** Änderungen in kleinen, prüfbaren Aufgaben und eigenen Git-Branches/Worktrees. Tests, Builds und Migrationen nur mit überprüfter Testkonfiguration. Keine automatische Übernahme in `main`, kein Produktiv-Deployment ohne Freigabe.
- **Definition of Done je Feature:** Implementierung + passende Tests + geprüfte Auswirkungen auf Altdaten/Schema + Dokumentation + konsistentes UI + kurze Beschreibung der manuellen Nachprüfung.

## 2. Muss – verbindliche Funktionsanforderungen

### M01 – Einkaufslisteneinträge nachträglich bearbeiten
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
- **Akzeptanz:** Beide Importarten sind klar unterscheidbar, liefern korrekt normalisierte Formulardaten und können gespeichert werden.

### M03 – „Days Read“ berechnen
- Einstellbare angenommene durchschnittliche Lesezeit **pro Band** in Minuten definieren (kein voreilig festgelegter Wert).
- Anzahl gelesener Bände in eine geschätzte Lesezeit umrechnen; Anzeige in Tagen/Stunden und konsistent mit weiteren Lesestatistiken.
- Klären, ob und wie Rereads zusätzlich zählen, bevor die Berechnung verbindlich wird.
- **Akzeptanz:** Ergebnis ist anhand einer dokumentierten Formel nachvollziehbar und ändert sich korrekt mit Lesezeit und Lesestatus.

### M04 – Bestehende Statistiken reparieren und ausbauen
- Fehler bei Jahreswechsel, Centbeträgen, Diagramm-Interaktionen und vorhandenen Statistikansichten beheben.
- **Pie Charts – erste Reihe:** Typ, Format, Status.
- **Pie Charts – zweite Reihe:** Herkunft, gelesen/ungelesen, vergriffen/nicht vergriffen.
- **Weitere Auswertungen:** Verlage und Format in einer Darstellung ähnlich AniLists Studio-/Genre-Statistik; Days Read; Anteil an Spezialbänden; gelesene Bände pro Monat; Chart für Erscheinungsjahr und Herkunftsland.
- Bestehende UI-Optik übernehmen und Diagramme konsistent gestalten.
- Fachlich festlegen, welche Größen sich auf *Reihen* und welche auf *Bände* beziehen; unbekannte Angaben separat behandeln statt zu raten.
- **Akzeptanz:** Die Statistiken stimmen bei Testdatensätzen einschließlich Jahreswechsel, Centbeträgen, unbekannten Merkmalen und mehrfacher Diagrammbedienung.

### M05 – Feld „Ebay-Preis“ sinnvoll allgemeiner benennen
- Begriff unabhängig von einer bestimmten Verkaufsplattform gestalten.
- **Kandidaten:** „Preisabweichung“ (bei Differenz zum Normalpreis) oder „Preisanpassung“ (bei Auf-/Abschlag).
- Vor Umbenennung Bedeutung, Vorzeichen, Berechnung, Datenbankspalte und alle UI-Verwendungen untersuchen; zunächst **kein** unkontrolliertes Umbenennen von DB-Spalten.
- **Akzeptanz:** Fachlich eindeutiger Begriff, unveränderte Altwerte und korrekte Berechnung.

### M06 – Lesestatus beim Hinzufügen von Bänden korrekt erhalten
- Beim Hinzufügen eines neuen Bandes dürfen vorhandene Bände **nicht** erneut auf ungelesen gesetzt werden.
- Ein neuer Band erhält seinen eigenen initialen Lesestatus (standardmäßig ungelesen, sofern nichts anderes ausdrücklich gewählt wird).
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

### K04 – Anmeldung
- Authentifizierung/Autorisierung passend zum späteren Betriebsmodell; nicht ohne Bedrohungs- und Berechtigungskonzept ergänzen.

### K05 – Einstellungen
- UI zur Verwaltung konfigurierbarer Optionen, insbesondere geschätzte Lesezeit pro Band; später gegebenenfalls profilspezifisch.

## 5. Technische Grundlagen aus der initialen Codex-Analyse

Die folgenden Punkte sind technische Voraussetzungen oder Qualitätsprobleme, **keine Änderung der fachlichen Muss-/Soll-/Kann-Kategorien**.

### T01 – Isolierte MySQL-Testumgebung und verlässliche Tests (zuerst)
- **Erledigt:** eigenes `test`-Profil und separate MySQL-Datenbank `mangalib_test` mit Testbenutzer `mangalib_test_user`; Passwort über `MANGALIB_TEST_DB_PASSWORD`, kein produktiver Fallback.
- **Erledigt:** automatischer Schutz vor Kontext-/JDBC-Zugriffen, datenbankfreie Schutzprüfungen und transaktionaler Integrationstest; 34 Tests durch Codex erfolgreich ausgeführt. Selenium-/Netzwerktests sind deaktiviert und zusätzlich per Tag ausgeschlossen. Sicherheitsregeln: [TESTING.md](TESTING.md).
- **Offen:** fachliche Regressionstests und zusätzliche Migrationsprüfungen gemäß [BOOT4_MIGRATION.md](BOOT4_MIGRATION.md). Testcontainers ist keine Voraussetzung für die bestehende funktionsfähige Testumgebung.
- Vor dem ersten `gradlew test`, `check`, `build` und `bootRun` Konfiguration und potentielle Schreibzugriffe prüfen.
- SQL-Bootstrap-Skript reparieren/validieren; Migrationen an echten Altbeständen testen.

### T02 – Sicherheit und Eingabevalidierung
- Stored-XSS-Risiko durch ungefiltertes `innerHTML` und Inline-Handler beseitigen.
- [x] **S01 umgesetzt (11.10.2026):** Dynamische Bandanzeige verwendet DOM-Knoten und Textausgabe statt HTML-Interpolation sowie direkt gebundene Klickereignisse. Absolute HTTP-/HTTPS-URLs ohne Zugangsdaten werden vor Navigation/Bildzuweisung geprüft; ungültige Cover verwenden den Platzhalter, ungültige Links bleiben deaktiviert. DOM-Struktur, CSS-Klassen und Titel-Sonderzeichen bleiben erhalten. **33 jsdom-DOM-Tests bestanden**, ohne Browser, Datenbank oder externe Requests. Reproduzierbarer separater Testlauf: `pnpm run test:dom`; Voraussetzungen und Grenzen stehen in `src/test/js/README.md`. jsdom ist ausschließlich eine freigegebene Testabhängigkeit. Die übrigen T02-Maßnahmen und die manuelle UI-Abnahme bleiben offen.
- S01-Validierung: Zuerst **44 datenbankfreie Guard-Tests**, anschließend `clean test` mit **91 erfolgreichen Tests**, einschließlich der unveränderten **29 MVC-/JSON-Vertragstests**; keine Fehler oder übersprungenen Fälle. Die geschützten Vorher-/Nachher-Messungen bestätigen identische Daten-/Schema-Fingerabdrücke aller zehn Tabellen und unveränderte 78 Stammdatensätze. Testdaten wurden zurückgerollt; ausschließlich `mangalib_test` / `mangalib_test_user`, keine Migrationen oder Scraperstarts. Die bekannten Deprecated-API-/HotSpot-CDS-Warnungen bleiben bestehen.
- Externe URLs/Importfelder validieren; geeignete DTOs und serverseitige Prüfungen etablieren.
- Erreichbarkeit der Anwendung klären; bei Netzbetrieb Zugriffsschutz priorisieren.

**Präzisierung durch den statischen Security-Audit vom 10.10.2026:**
Der interne, ausschließlich lokal aufbewahrte Sicherheitsbericht trennt belegte Codeprobleme von bedingten Risiken. Keine Tests, DB-Verbindungen oder Scraper wurden dafür ausgeführt; die 91 erfolgreichen Regressionstests sind keine Sicherheitsabnahme.

- **P1 – S01 behoben:** Der dokumentierte Stored-XSS-Datenpfad in der dynamischen Bandanzeige wurde mit sicherer DOM-Erzeugung, Textausgabe und Ereignisbindung geschlossen; DOM-Regressionstests ergänzen die bestehende Suite. Übergreifende URL-Regeln, Zugriffsschutz und weitere T02-Anforderungen bleiben eigenständige Aufgaben.
- **P1:** Lokalen Betrieb explizit absichern: Im Repository fehlen Loopback-Bindung, Authentifizierung sowie Host-/Origin-/CSRF-Prüfungen. Tatsächliche Netzwerkerreichbarkeit ist ungeprüft; gewöhnliche JSON-Preflight-Hürden sind kein Zugriffsschutz. Scraperstart über GET `/autofillBandData/{bandIndex}` als geschützte Aktion behandeln; gewollten LAN-Betrieb separat absichern.
- **P1/P2:** Bandanzahl vor Speicher-Schleifen begrenzen, Importdaten als untrusted Client-Eingabe behandeln, serverseitige Feld-/Referenz-/URL-Regeln vervollständigen. Erlaubte Bestandswerte und negative Preisänderungen nicht pauschal verwerfen.
- **P2:** `URL.equals()` im Bandupdate durch einen Vergleich ohne DNS-Nebenwirkung ersetzen; URL-Syntaxprüfung um feldbezogene Protokoll-/Hostregeln ergänzen. Allgemeines SSRF ist nicht bewiesen; Weiterleitungen und Browser-Unterressourcen sowie parallele Browserstarts benötigen eigene Schutzgrenzen.
- **P2:** Keine internen Exception-Texte im Response und keine vollständigen Import-/Sammlungsdaten in Rohlogs; kontrollierte Fehlermeldungen und strukturierte, begrenzte Logs testen.
- **P2/P3:** Dokumentierter Tomcat-Stand 11.0.24 liegt in offiziellen betroffenen Sicherheitsbereichen; gepatchten konsistenten Stand gezielt validieren. HTTP/2 ist standardmäßig deaktiviert, ein konkreter Angriff nicht nachgewiesen. jsoup 1.17.2 fällt in einen Advisory-Bereich, dessen Safelist-Nutzungspfad hier nicht gefunden wurde. Gemischte Selenium-Versionen und offenbar ungenutzte jsoup-/WebDriverManager-Abhängigkeiten gezielt prüfen.
- **SQL-Prüfung:** Keine SQL-Injection in den bestehenden Repository-/HTTP-Pfaden gefunden; JPQL-Parameter sind gebunden und HTTP-Sortierung verwendet feste Comparators. Die derzeit ungenutzten `findAllSorted`-Service-Methoden vor künftiger Exposition mit einer Property-Allowlist versehen. Automatisierte Sicherheitstests bleiben offen.

### T03 – Atomare, wiederholungssichere Datenänderungen
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
- SQL-Skript korrigieren; Fehlerfälle und Logging verbessern.

### T05 – Wartbarkeit, Architektur und Performance
- Schichten entflechten; Controller entlasten, typisierte Requests und Importmodelle einführen.
- Datenbankabfragen bündeln und messen; bei Bedarf Pagination.
- Gemeinsame CSS-/Template-Bestandteile wiederverwenden, ohne visuelles Redesign.
- Treiberpfade konfigurierbar machen; Dokumentation, Dependency-Prüfung und CI verbessern.

## 6. Empfohlene Ausführungsreihenfolge

| Phase | Schwerpunkt | Haupttickets / Hinweise |
|---|---|---|
| 0 | Sicherer Entwicklungsunterbau | T01; Testdatenbank, Tests, Backup-/Migrationsregeln |
| 1 | Datenintegrität und Sicherheit | T02, T03, M06; dazu Preis-/Bandanzahlfehler |
| 2 | Einkaufsliste und Import | M01, M02, S02; normalisiertes Scraper-Modell |
| 3 | Datenmodell und Lesehistorie | M05, S03, Vorbereitung M03/M04; Migration bestehender Datensätze |
| 4 | Statistik | T04 (statistikrelevante Fehler), M03, M04 |
| 5 | Verwaltung und Komfort | S01, K01, K02, K05 nach Bedarf |
| 6 | Profile und Zugriff | K03, K04 nach separater Architekturentscheidung |

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
- [ ] Erste abgesicherte Fehlerbehebung umgesetzt.

---

*Diese Roadmap ist ein lebendes Planungsdokument. Fachliche Prioritäten ändern sich nur nach ausdrücklicher Entscheidung des Projekteigentümers.*
