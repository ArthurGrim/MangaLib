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
- Diagramme konsistent und als Teil von MangaLib gestalten; Verbesserungen der UI-/UX nach den obigen Modernisierungsregeln ermöglichen.
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
- Bei Umsetzung gemeinsam mit K04 konzipieren und umsetzen: echte Anmeldung mit Authentifizierung und Autorisierung sowie serverseitige Trennung aller Datenzugriffe nach Benutzerberechtigung sind verpflichtend. Eine bloße Profiltrennung im Frontend genügt nicht.

### K04 – Anmeldung
- Authentifizierung/Autorisierung passend zum späteren Betriebsmodell; nicht ohne Bedrohungs- und Berechtigungskonzept ergänzen.
- Für den aktuellen lokalen Einzelbenutzerbetrieb ist kein Login erforderlich; bei Umsetzung von K03 ist K04 verbindlicher Bestandteil derselben Umsetzung. Die Kann-Kategorie beider Erweiterungen bleibt unverändert.

### K05 – Einstellungen
- UI zur Verwaltung konfigurierbarer Optionen, insbesondere geschätzte Lesezeit pro Band; später gegebenenfalls profilspezifisch.

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
- **P2:** `URL.equals()` im Bandupdate durch einen Vergleich ohne DNS-Nebenwirkung ersetzen; URL-Syntaxprüfung um feldbezogene Protokoll-/Hostregeln ergänzen. Allgemeines SSRF ist nicht bewiesen; Weiterleitungen und Browser-Unterressourcen sowie parallele Browserstarts benötigen eigene Schutzgrenzen.
- **P2:** Keine internen Exception-Texte im Response und keine vollständigen Import-/Sammlungsdaten in Rohlogs; kontrollierte Fehlermeldungen und strukturierte, begrenzte Logs testen.
- **P2/P3:** Dokumentierter Tomcat-Stand 11.0.24 liegt in offiziellen betroffenen Sicherheitsbereichen; gepatchten konsistenten Stand gezielt validieren. HTTP/2 ist standardmäßig deaktiviert, ein konkreter Angriff nicht nachgewiesen. jsoup 1.17.2 fällt in einen Advisory-Bereich, dessen Safelist-Nutzungspfad hier nicht gefunden wurde. Gemischte Selenium-Versionen und offenbar ungenutzte jsoup-/WebDriverManager-Abhängigkeiten gezielt prüfen.
- **SQL-Prüfung:** Keine SQL-Injection in den bestehenden Repository-/HTTP-Pfaden gefunden; JPQL-Parameter sind gebunden und HTTP-Sortierung verwendet feste Comparators. Die derzeit ungenutzten `findAllSorted`-Service-Methoden vor künftiger Exposition mit einer Property-Allowlist versehen. Automatisierte Sicherheitstests bleiben offen.

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

Der SHA-256-Fingerabdruck bleibt `ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`. AUTO_INCREMENT-Zähler können durch zurückgerollte Fixtures steigen und werden weiterhin nicht zurückgesetzt. Ausschließlich `mangalib_test`/`mangalib_test_user` über den bestehenden Guard; keine produktive Verbindung, Migration, Schemaänderung, Dateninitialisierung oder echte Scraper-/Browserausführung. Testschutzdateien, Testprofil, Datenmodell und Geschäftsregeln unverändert. Berichte der 179-Test-Suite bleiben unter `build/test-results/test` und `build/reports/tests/test`; lokale Vorher-/Nachher-/Dependency-Artefakte liegen Git-ignoriert unter `.gradle/security-validation`. Sichere Reproduktion und Grenzen: [TESTING.md](TESTING.md), [DOM-Tests](src/test/js/README.md).

**Offene Abnahme und Grenzen:** Kein echter HTTP-Listener oder Browser wurde gestartet. Tatsächliche Loopback-Erreichbarkeit, Cookie-Übertragung, mehrere Tabs, Sessionablauf/Neustart, alle UI-Abläufe und Bilddarstellung sind später in isolierter Umgebung manuell zu prüfen. Lokale untrusted Programme und eigenes XSS werden durch anmeldungsfreien CSRF-Schutz nicht ausgeschlossen; andere Entwicklungslistener, Serverrechte und Windows-Konfiguration bleiben separat zu kontrollieren. Kein LAN-/IPv6-/Internetbetrieb freigegeben. Bestehende Temporal-/OSIV-/Mockito-/JVM-Warnungen sowie erwartete 405-Negativtest-Warnungen bleiben; allgemeine Eingabe-/URL-/Scraperlimits, Dependency-Befunde, Logging und fachliche Fehler gehören weiterhin zu separaten Aufgaben. Umsetzung ohne fachliche/UI-Modernisierung und ohne Commit/Push; Abnahme des Projekteigentümers ausstehend.

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
- SQL-Skript korrigieren; fachliche Fehlerfälle verbessern. Konsistente technische Fehlerdiagnose und Logging werden in T06 geplant.

### T05 – Wartbarkeit, Architektur und Performance
- Schichten entflechten; Controller entlasten, typisierte Requests und Importmodelle einführen.
- Datenbankabfragen bündeln und messen; bei Bedarf Pagination.
- Gemeinsame CSS-/Template-Bestandteile wiederverwenden, ohne visuelles Redesign.
- Treiberpfade konfigurierbar machen; Dokumentation, Dependency-Prüfung und CI verbessern.

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
- Security S02/S03: Lokaler Betrieb und gemeinsame Autofill-POST-Umstellung sind freigegeben. Manuelle Browser-/Listenerabnahme in isolierter Umgebung bleibt offen; IPv6, externe Clients, Anmeldung und LAN werden derzeit nicht umgesetzt.
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
- [x] Erste abgesicherte Fehlerbehebung umgesetzt (S01; S02/S03 Teil A zusätzlich technisch geprüft, manuelle Abnahme offen).

## 9. Abschlussziele und optionale Smartphone-/LAN-Erweiterung

Diese Reihenfolge ergänzt Phase 7; die fachlichen Muss-/Soll-/Kann-Prioritäten bleiben unverändert.

1. **Verbindlich: reguläre Windows-Bereitstellung und automatischer Betrieb (T07).** Nach wesentlichen Projektzielen lokale Einzelbenutzeranwendung ohne VS Code/Gradle bedienen; sichere Konfiguration, Diagnose, Updates und überprüfte Backup-/Wiederherstellung gewährleisten.
2. **Optional danach: GitHub-Release für Selbstbetrieb (T07).** Andere Nutzer verwenden eigene Datenbanken/Secrets; dokumentierte sichere Installation, Release-/Lizenzprüfung und Updates, keine privaten Bestände oder Sicherheitsberichte veröffentlichen. Keine Hosting-Infrastruktur voraussetzen.
3. **Optional nach Bereitstellung/Release, vor K06: Smartphone-/LAN-Zugriff im eigenen Heimnetz.** Erst nach den übrigen Projektzielen, regulärer Bereitstellung und dem optionalen GitHub-Release gesondert entscheiden und beauftragen. Die optionalen Ziele sind keine Pflichtvoraussetzung für den lokalen Betrieb; bei Verzicht auf einen GitHub-Release ist dessen Überspringen ausdrücklich zu entscheiden. Dann explizites LAN-Opt-in mit begrenzter Bind-Adresse/Firewall, Anmeldung und Zugriffskontrolle auch für lesende Daten-/Statistik-/Scraperpfade, HTTPS direkt in der Anwendung mit geklärtem Zertifikatsvertrauen auf dem Smartphone sowie fortbestehender Host-/Origin-/CSRF-Abwehr planen. Ein gemeinsamer Eigentümerzugang genügt gegebenenfalls; Mehrbenutzerprofile nicht ungefragt voraussetzen. Lokaler Default und Datenmodell bleiben erhalten; keine vorsorgliche LAN-Komplexität heute. Abnahme später: lokaler Modus bleibt aus dem LAN unerreichbar, LAN-Modus nur von freigegebenen Geräten/mit gültiger Sitzung, sichere Anmeldung/Abmeldung, Smartphone-Fetch-/Tokenabläufe, gesperrte Managementdiagnose und unbeeinträchtigte lokale Funktionen. **Kein öffentlicher Internetzugriff, keine Router-Portfreigaben, keine zusätzliche Serverinfrastruktur.**

## 10. Allerletzte optionale Erweiterung: Browser-Integration (K06)

Nach sämtlichen zuvor vorgesehenen Projektzielen einschließlich der optionalen Smartphone-/LAN-Erweiterung folgt K06 als letzter optionaler Roadmap-Punkt. Entfallen frühere optionale Ziele, deren Überspringen ausdrücklich festhalten. Zunächst Firefox-Erweiterung und alternative Übergabelösungen, optional Chrome, technisch und sicherheitlich bewerten. Manga-Passion-Reihen-/Banddaten nur als untrusted Vorschlag übernehmen: vorbereiteter Einkaufskorb-Eintrag, Datenvorschau, Dublettenprüfung und ausdrückliche Bestätigung. Sichere lokale Backend-Kommunikation mit geeigneten Berechtigungen und einem geprüften Übergabevertrag ist Voraussetzung; bestehende Host-/Origin-/CSRF-Regeln nicht pauschal öffnen. Keine Implementierung im aktuellen Auftrag.

---

*Diese Roadmap ist ein lebendes Planungsdokument. Fachliche Prioritäten ändern sich nur nach ausdrücklicher Entscheidung des Projekteigentümers.*
