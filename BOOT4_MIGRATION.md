# MangaLib – Spring-Boot-4-Migrationsplan und validierter 4.1-Zwischenstand

Stand: **10.10.2026**. Ausgangspunkt: **Spring Boot 3.5.16**. Validierte Zwischenstände: **4.0.8 → 4.1.1**. Aktuelle Version: **4.1.1**.

Die Anwendung verwendet jetzt **4.1.1**, lokal technisch validiert, ohne produktive Freigabe oder Bereitstellung. Der ursprüngliche Analyseauftrag und die früheren Testaufträge sind in Abschnitten 10 und 11 dokumentiert. Abschnitt 12 dokumentiert den abgeschlossenen Major-Wechsel auf 4.0.8; Abschnitt 13 den aktuellen Wechsel auf 4.1.1. Abschnitte 2–9 sind die ursprüngliche Analyse, keine Liste weiterhin ausstehender Änderungen. Maßgeblich für tatsächlich aufgelöste Versionen und ausgeführte Prüfungen ist Abschnitt 13. AGENTS.md, ROADMAP.md und TESTING.md bleiben verbindlich.

## 1. Ergebnis und Entscheidungsgrundlage

Die Migration ist mit **Java 21 und Gradle 8.14.3** grundsätzlich möglich. Ein neues Frontend, eine neue Datenbank oder eine fachliche Neugestaltung sind dafür nicht erforderlich. Es gibt konkrete Konfigurationsänderungen sowie Prüfbedarf bei JSON-Verträgen, ORM-Verhalten und Testinitialisierung. Eine rein statische Untersuchung kann die Laufzeitkompatibilität und den Erhalt aller Bestandswerte nicht beweisen.

**4.1.1 ist die aktuell offiziell ausgewiesene stabile Patchversion der 4.1-Reihe.** Die Veröffentlichung erfolgte am 20.08.2026; die Referenzdokumentation führt 4.1.1 unter „Stable“ und 4.1.2-SNAPSHOT separat. Vor Beginn der Umsetzung erneut prüfen und das Ziel fest pinnen, keine dynamische Versionsangabe verwenden. Quellen: [offizielle Veröffentlichung](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/), [Versionsübersicht/Systemanforderungen](https://docs.spring.io/spring-boot/system-requirements.html).

Für den Sprung sind sowohl der [Boot-4.0-Migrationsleitfaden](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) als auch die [Boot-4.1-Release-Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) relevant. Empfohlen wird ein lokal validierter Zwischenstand auf der dann aktuellen stabilen 4.0-Patchversion (derzeit 4.0.8), anschließend 4.1.1. Das trennt den Major-Wechsel von zusätzlichen 4.1-Änderungen; ein Deployment des Zwischenstands ist nicht vorgesehen.

Kennzeichnung in diesem Dokument:

- **Belegt:** im Quellcode, vorhandenen Testergebnis oder in offizieller Dokumentation nachgewiesen.
- **Risiko:** konkrete betroffene Stelle, deren Fehlverhalten unter Boot 4 noch nicht reproduziert wurde.
- **Offen:** erfordert spätere Kompilierung, Testausführung oder administrative Bestätigung.

## 2. Untersuchungsumfang und Architektur

Erfasst wurden Build-/Wrapper-Konfiguration, alle 36 Java-Dateien unter `src/main/java`, sämtliche Testklassen einschließlich vier Hilfsklassen, beide Anwendungskonfigurationen, Testregistrierung, Templates, JavaScript/CSS, SQL-Skripte und Projektdokumentation. Schwerpunkt der Einzelprüfung waren Framework-Imports, Konfigurationsschlüssel, Datenmodelle, Repository-Abfragen, Schreibpfade, HTTP-/JSON-Verträge und Schutzmechanismen. Dies ist kein vollständiges Security-Audit und keine gemessene Performanceanalyse.

| Bereich | Dateien/Komponenten | Datenfluss und migrationsrelevante Funktion |
|---|---|---|
| Einstieg/Build | `MangalibApplication.java`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` | Eine Spring-Boot-Anwendung, ausführbares JAR, kein Multi-Modul-Projekt. Kotlin nur für Gradle DSL. |
| Web | `controller/MainController.java`, `EinkaufslisteController.java`, `StatistikController.java` | HTML-Ansichten und JSON-Endpunkte für Suchen/Filtern/Sortieren, Anlegen/Bearbeiten, Banddaten, Einkauf/Kaufübernahme und Statistiken. |
| Fachlogik | zwölf Dateien unter `service/`, davon zwei Scraper | Controller → Services → Repositories; teilweise mehrere Service-Aufrufe pro HTTP-Schreibaktion. Lookup-Services für Status, Verlag, Typ, Format und Sammelband. |
| Persistenz | zehn Entities und zehn Repository-Interfaces | JPA/Hibernate → JDBC → MySQL. `MangaReihe` und `EinkaufslisteItem` besitzen Details mit gemeinsamer ID; Bände gehören zu Reihen; fünf Stammdatentabellen. |
| Darstellung | `templates/home.html`, `einkaufsliste.html`, `statistik/*.html`, `static/js/*.js`, `static/css/**` | Thymeleaf rendert Daten; JavaScript sendet gemischte String-/Zahl-/Boolean-Payloads und verarbeitet JSON. Vier Statistik-Unterseiten sind derzeit leere Platzhalter. |
| Import | `service/MangaScraper.java`, `BandScraper.java` | Statische Methoden starten Firefox und rufen externe Seiten ab. Keine automatischen Scraper beim normalen Spring-Kontextstart gefunden. |
| Tests | `src/test/java/de/mangalib/**`, Testprofil, `META-INF/spring.factories` | Schutz vor Bean-Initialisierung und vor jedem JDBC-Verbindungsaufbau; automatische Netzwerktests ausgeschlossen. |

Java-Dateipfade in Tabellen ohne Präfix beziehen sich auf `src/main/java/de/mangalib/`. Nachfolgende Zeilenangaben beziehen sich auf den untersuchten Stand, nicht auf zukünftige Änderungen.

## 3. Versions- und Abhängigkeitsmatrix

Die Zielwerte stammen aus der offiziellen [Dependency-Tabelle für Boot 4.1.1](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html). Sie beschreiben das BOM, noch keinen für MangaLib aufgelösten Dependency-Graphen. Die genannten aktuellen Kernbibliotheken wurden im vorausgegangenen 3.5.16-Update geprüft.

| Komponente | Ausgangspunkt | Ziel/Entscheidung |
|---|---|---|
| Java | 21 | Beibehalten; Boot 4.1.1 unterstützt Java 17–26. |
| Gradle Wrapper | 8.14.3 | Beibehalten; unterstützt sind 8.x ab 8.14 und 9.x. |
| Boot-Plugin/BOM | 3.5.16 | Am Ende 4.1.1. |
| Spring Framework | 6.2.19 | 7.0.9. |
| Spring Data JPA | Boot-3.5-Verwaltung | 4.1.1; Jakarta Persistence 3.2. |
| Hibernate ORM | 6.6.53.Final | 7.4.5.Final. |
| MySQL Connector/J | 9.7.0 | BOM ebenfalls 9.7.0; kein zusätzlicher Treiberwechsel erforderlich. |
| Thymeleaf | 3.1.5.RELEASE | Ebenfalls 3.1.5.RELEASE, einschließlich `thymeleaf-spring6`. Keinen erfundenen `spring7`-Artefaktnamen einsetzen. |
| Jackson Databind | 2.21.4 | Standardmapper aus Jackson 3.1.5; Annotationen bleiben aus der bisherigen Paketfamilie. |
| Tomcat | 10.1.55 | 11.0.x / Servlet 6.1. |
| JUnit Jupiter/Platform | Boot-3.5-Verwaltung | 6.0.3; gemeinsame BOM-Verwaltung erhalten. |
| Dependency-Management-Plugin | explizit 1.1.4 | Kein nachgewiesener Blocker. Zunächst prüfen; Boot-Dokumentation verwendet 1.1.7. Nur bei belegtem Bedarf anpassen. |
| Selenium | explizit `selenium-java:4.18.1` | Boot-4.1.1-BOM verwaltet 4.43.0. Effektiv aufgelöste Module prüfen; siehe Abschnitt 4.7. |
| jsoup / WebDriverManager | 1.17.2 / 5.7.0 | Nicht pauschal aktualisieren; im Anwendungscode keine Verwendung ihrer APIs gefunden. |

Quellen für Java/Gradle/Servlet: [Systemanforderungen](https://docs.spring.io/spring-boot/system-requirements.html). Das Dependency-Management-Plugin und BOM-Overrides erläutert die [Gradle-Dokumentation von Spring Boot](https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html). Keine Notwendigkeit für Gradle 9 oder Java 25 aus Hibernate-internen Build-Anforderungen ableiten: MangaLib verwendet veröffentlichte Hibernate-Artefakte und baut Hibernate nicht selbst.

## 4. Konkrete Befunde und Anpassungen

### 4.1 Gradle, Starter und Boot-APIs – P1

**Belegt:** `build.gradle.kts:28` verwendet `spring-boot-starter-web`. Der Boot-4-Leitfaden nennt `spring-boot-starter-webmvc` als Nachfolger; der alte Starter ist eine veraltete Kompatibilitätsoption. Die fachlichen Starter für JPA, Thymeleaf, Validation und Actuator bleiben sinnvoll. Boot 4 modularisiert auch Testunterstützung. Quelle: [Modul-/Starter-Übersicht](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#module-dependencies).

**Umsetzung:** Boot-Version ändern, Web-Starter ersetzen, für neue MockMvc-Tests `spring-boot-starter-webmvc-test` verwenden. `spring-boot-starter-data-jpa-test` nur ergänzen, wenn JPA-Test-Slices tatsächlich verwendet werden. Der vorhandene `spring-boot-starter-test` ist nicht pauschal unbrauchbar: Die aktuellen Tests verwenden Vollkontexte und keine Slice-Annotationen. Keine Classic-Starter oder Properties-Migrator als dauerhafte Ausweichlösung einführen.

Die main-Imports aus Spring Boot beschränken sich auf den Programmeinstieg; es wurden keine eigenen Auto-Konfigurationen oder direkten Imports verschobener JDBC-/MVC-Auto-Konfigurationsklassen gefunden. Im Testschutz bleiben die Binder-APIs ein gezielter Prüfpunkt. Standardtypen `javax.sql.DataSource` und `java.sql.*` sind Java-SE-APIs und dürfen nicht nach `jakarta.*` umbenannt werden.

**Offen:** tatsächliche Plugin-Auflösung, Lombok-Annotation-Processing sowie Compile-/Runtime-Graph unter 4.1.1. Keine Einzelversionen für Hibernate/Spring/Jackson als Reparatur eines noch unbekannten Problems festschreiben. `settings.gradle.kts` und Wrapper benötigen nach dieser Analyse keine Änderung.

### 4.2 JSON-Konfiguration und Jackson 3 – P1, konkrete Anpassung

**Belegt:** `src/main/resources/application.properties:8–10` enthält drei JSON-Einstellungen. Für den Zielstand ist folgende Zuordnung vorgesehen:

| Bestehender Schlüssel | Ziel |
|---|---|
| `spring.mvc.converters.preferred-json-mapper=jackson` | `spring.http.converters.preferred-json-mapper=jackson` |
| `spring.jackson.default-property-inclusion=NON_NULL` | Erhalten. |
| `spring.jackson.serialization.write-dates-as-timestamps=false` | `spring.jackson.datatype.datetime.write-dates-as-timestamps=false` |

Der erste Zielschlüssel und der `datatype.datetime`-Bereich stehen in der [Boot-Property-Referenz](https://docs.spring.io/spring-boot/appendix/application-properties/index.html). `WRITE_DATES_AS_TIMESTAMPS` ist in Jackson 3 eine `DateTimeFeature`, keine `SerializationFeature` mehr: [Quellcode der Zielversion 3.1.5](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.5/src/main/java/tools/jackson/databind/cfg/DateTimeFeature.java). Das Beibehalten des alten Feature-Schlüssels birgt daher ein konkretes Binding-/Startfehlerrisiko; unter Boot 4 hier noch nicht ausgeführt.

**Kein pauschaler Importwechsel:** Die Entities verwenden ausschließlich `com.fasterxml.jackson.annotation.JsonBackReference` und `JsonManagedReference`; diese Annotationen bleiben in diesem Paket. Eigene ObjectMapper, Serializer oder Jackson-2-Databind-Aufrufe wurden nicht gefunden. Jackson 3 ändert unter anderem Nullbehandlung primitiver Werte und Prüfungen auf zusätzliche JSON-Tokens. Quelle: [Jackson-3-Release-Notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0).

**Risiken im Projekt:**

- `MainController.java:229–284` und `EinkaufslisteController.java:142–194` casten `Map<String,Object>` direkt und liefern gespeicherte Entities zurück. Fehler können erst beim Serialisieren der Antwort entstehen, nachdem die Service-Transaktion bereits abgeschlossen ist. Das ist ein bestehender Architekturpfad, kein nachgewiesener neuer Boot-4-Fehler.
- `MangaReihe.java:32–49`, `MangaDetails.java:39–47` und weitere Entities haben mehrere unbenannte Back-References. Zyklische Referenzen, ausgelassene Beziehungen und Lazy-Proxies gezielt prüfen; nicht vorsorglich alle Annotationen umschreiben.
- `popup_add_reihe.js:244–272`, `popup_edit_reihe.js:316–343`, `popup_edit_baende.js:100–117`, `einkaufsliste.js:270–296` definieren die realen Payload-Typen. IDs als Strings versus Zahlen, `null`, fehlende Felder, boolesche Werte, Preise und Datumsstrings müssen identisch verarbeitet werden.
- Listen-/Detailantworten enthalten Geldbeträge, URLs und Lesestatus; Statistikantworten Monatsnummern als JSON-Objektschlüssel. Tests sollen Werte und Typen prüfen, nicht die Reihenfolge von JSON-Feldern.

**Entscheidung:** Jackson 3 gezielt konfigurieren. Eine globale Rückkehr zu Jackson-2-Defaults nur bei konkreter, getesteter Vertragsabweichung erwägen; kein vorsorglicher Jackson-2-Fallback. Transitive Jackson-2-Bibliotheken dürfen existieren, aber nicht versehentlich den MVC-Mapper bestimmen.

### 4.3 MVC, Controller und Oberfläche – P1/P2

Die drei Controller verwenden reguläre MVC-Annotationen und `ResponseEntity`; keine eigenen PathMatcher, Servlet-Filter, WebMvcConfigurer, HttpHeaders-Implementierungen oder entfernten `javax.annotation`-/`javax.inject`-Imports wurden gefunden. Ein zwingender Controller-Umbau ist statisch nicht belegt. Die [Framework-7-Release-Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) nennen unter anderem entfernte alte Pfadoptionen und geänderte HTTP-Mocks; diese Erweiterungspunkte verwendet MangaLib derzeit nicht direkt.

**Prüfen:** `/home`, `/resetFilters`, `/einkaufsliste`, `/statistik/st_overview`, alle JSON-Endpunkte sowie 400/404/204-Antworten und Content-Types. Besonders `MainController.java:449–525` gibt beim Bandupdate 204 zurück. Baseline erfassen, damit eine bereits vorhandene frontendseitige JSON-Erwartung nicht fälschlich als Frameworkregression behandelt wird.

Thymeleaf bleibt durch den bestehenden Starter integriert. `home.html:430` verarbeitet Reihen und Beziehungen; `einkaufsliste.html:129` verwendet `#temporals`, `:165` Zahlenformatierung und `:335` einen statischen Java-Zugriff. Die drei tatsächlich ausgefüllten Ansichten müssen mit Nullwerten, Umlauten und realistischen Listen rendern. Der strengere SpEL-Operationsgrenzwert von Framework 7 ist bei komplexen Ausdrücken ein möglicher Prüfpunkt, kein belegter Fehler dieser Templates. Bestehende CSS-Klassen, DOM-IDs, Scriptpfade und Layouts erhalten.

Keine OSIV-Umstellung im Framework-Upgrade verstecken: Das aktuelle Projekt verwendet den Standard von Open EntityManager in View; dessen Warnung war schon im erfolgreichen 3.5.16-Testlauf vorhanden. Erst Rendering außerhalb einer umschließenden Testtransaktion zeigt, ob Lazy-Zugriffe im tatsächlichen Webpfad funktionieren. Keine automatischen Browserstarts für diesen Nachweis nötig: MockMvc kann Templates rendern, ohne externe Bilder oder Scripts nachzuladen.

Actuator ist eingebunden, eigene Actuator-Endpunkte oder Exposure-Regeln wurden nicht gefunden. Boot 4 aktiviert Liveness-/Readiness-Gruppen standardmäßig. Bei der späteren Abnahme Health-Verhalten und tatsächlich veröffentlichte Endpunkte prüfen, ohne zusätzliche Diagnoseendpunkte freizuschalten. Quelle: [Boot-4-Migrationsleitfaden, Actuator](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#upgrading-actuator-and-production-ready-features).

### 4.4 JPA/Hibernate: Mapping, Abfragen und Bestandswerte – P1

**Belegt:** Bereits `jakarta.persistence.*`, `JpaRepository`, IDENTITY-IDs und überwiegend feldbasierter Zugriff. Keine eigenen Hibernate-UserTypes, Dialekte, Integratoren, Criteria-Implementierungen, nativen Repository-SQL-Abfragen oder Hibernate-Session-Aufrufe gefunden. Änderungen an solchen APIs sind daher kein unmittelbarer Umbauauftrag.

| Stelle | Befund/Risiko | Gezielte Maßnahme |
|---|---|---|
| `entity/MangaReihe.java:73`, `entity/Band.java:58–66` | `@Temporal(DATE)` auf `LocalDate`; diese Kombination entspricht nicht dem vorgesehenen Einsatz von `@Temporal`. | Drei redundante Temporal-Annotationen im späteren Upgrade entfernen; DATE-Mapping, Altwerte und Timestamp-Erzeugung testen. Noch kein nachgewiesener Hibernate-7-Startfehler. |
| `entity/MangaReihe.java:78–80` | `java.sql.Timestamp` für Aktualisierung; von modernen Datums-APIs abweichend. | SQL-/JSON-Darstellung, Zeitzone und Präzision testen. Keinen gleichzeitigen Wechsel zu Instant/LocalDateTime ohne gesonderten Grund. |
| `entity/MangaDetails.java:39–47`, `EinkaufslisteItemDetails.java:48–52` | Gemeinsame Primärschlüssel mit `@MapsId`; dort kein explizites Persist-Cascade. Hibernate 7 entfernt zuvor implizites Persist-Cascading. | Reihenfolge parent → details und Reload für beide Aggregate testen. Hauptpfade speichern Eltern explizit (`MangaReiheService.java:177`, `EinkaufslisteService.java:203`); daher kein pauschal notwendiges Cascade-ALL-Upgrade. |
| `entity/Band.java:40–55`, `MangaReihe.java:58–71` | Teilweise BigDecimal ohne explizite Präzision; URLs als `java.net.URL`; gemischte primitive/nullable Booleans. | Spaltentypen gegen existierendes Testschema validieren; Centbeträge, Nullwerte, URL-Strings und Reload prüfen. Nur bei nachgewiesener Abweichung Mapping an Bestand anpassen. |
| `MangaReihe.java:88–89`, `EinkaufslisteItem.java:67–68` und Lookup-Entities | Cascade ALL/orphanRemoval und bidirektionale Beziehungen. | Update/Flush/Reload darf vorhandene Kinder oder Stammdaten nicht löschen/ersetzen. Keine Cascade-Änderung auf Verdacht. |
| `repository/MangaReiheRepository.java:31–78` | YEAR/MONTH, SUM, COUNT, COALESCE und `m.status.id`; Status-Feld heißt `statusId`. | Jede Query ausführen, Ergebniswerte und numerische Typen prüfen. `.id` kann Hibernate-ID-Kurzform sein; nicht als bewiesener Syntaxfehler deklarieren. Bei Inkompatibilität explizites `statusId` testen. |
| `repository/EinkaufslisteRepository.java:18–39`, `BandRepository.java` | FUNCTION/YEAR/MONTH und Object[]-Aggregate; abgeleitete Methoden. | Monats-/Jahresgrenzen, gekaufte/ungekaufte Positionen, Summen mit Centbeträgen, leere Ergebnisse sowie Sortierung testen. |

Quellen: [Jakarta Persistence 3.2 – Deprecations](https://jakarta.ee/specifications/persistence/3.2/), [zulässige Temporal-Feldtypen](https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/temporal), [Hibernate 7.0 – insbesondere MapsId-Cascades](https://docs.hibernate.org/orm/7.0/migration-guide/).

Spring Data 4.0 stellt abgeleitete Queries auf JPQL-basierte Erzeugung um; daher verdienen `findByTitelContainingIgnoreCase`, Filter, Datumsintervalle und Sortierung eigene Regressionstests. Das ist kein Grund, die Repository-Schicht zu ersetzen. Quellen: [Spring Data 2025.1](https://github.com/spring-projects/spring-data-commons/wiki/Spring-Data-2025.1-Release-Notes), [Spring Data 2026.0](https://github.com/spring-projects/spring-data-commons/wiki/Spring-Data-2026.0-Release-Notes).

Die Hibernate-Leitfäden [7.1](https://docs.hibernate.org/orm/7.1/migration-guide/), [7.2](https://docs.hibernate.org/orm/7.2/migration-guide/), [7.3](https://docs.hibernate.org/orm/7.3/migration-guide/) und [7.4](https://docs.hibernate.org/orm/7.4/migration-guide/) wurden ebenfalls abgeglichen. Versionszweig-Dokumentation kann schon spätere Patches beschreiben; Einzelbefunde vor einer Anpassung gegen 7.4.5 bestätigen. Insbesondere nennt 7.4 einen veränderten MySQL-Default für Fetch-Tiefe: Bei den vielen Beziehungen mögliche zusätzliche SELECTs messen. Im Testprofil keine direkten Hibernate-Overrides als vermeintliche Optimierung hinzufügen; der Guard verbietet sie bewusst. Nicht verwendete Features wie Envers, Arrays/JSON-Spalten, Vererbung, Child-Sessions und eigene Enhancement-SPI erfordern hier keine Migration.

Boot 4.1 verändert die Behandlung von `deferred`/`lazy` beim JPA-Bootstrap. MangaLib setzt diese Option nicht. Den bisherigen Standard erhalten, damit Repository-/Schemavalidierung früh fehlschlagen; kein Lazy-Bootstrap zum Verbergen eines Fehlers. Quelle: [Boot-4.1-Release-Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).

### 4.5 MySQL und Datenintegrität – P0

Das Framework-Update begründet **keine nachgewiesene Notwendigkeit einer Schemaänderung**. Es ist aber erst freigabefähig, wenn das vorhandene Schema mit dem neuen Stack validiert wurde und repräsentative Altwerte unverändert lesbar bleiben. `ddl-auto=validate` verhindert keine fachlich falschen UPDATEs; dafür sind Regressionstests und Datenvergleiche notwendig.

Verbindliche Grenzen für die Umsetzung:

1. Ausschließlich `mangalib_test` / `mangalib_test_user`, Passwort nur aus `MANGALIB_TEST_DB_PASSWORD`; keine produktiven Zugangsdaten und keine direkten Produktivverbindungen.
2. `ddl-auto=validate`, `generate-ddl=false`, SQL-Initialisierung `never`, Flyway/Liquibase deaktiviert. Keine neuen Migrationsabhängigkeiten, kein `create`, `update`, `create-drop`, `import.sql`, Seeding oder Tabellenreset.
3. Bei Schemaabweichung abbrechen und Soll-/Ist-Mapping untersuchen. Weder Schema noch Schutzregeln automatisch anpassen. Wenn ein kompatibles Mapping nicht genügt, ist eine gesondert autorisierte Datenmigration mit Backup und Rückweg erforderlich.
4. Bestehende fünf Stammdatentabellen nur lesen. Ausschließlich neue, eindeutig identifizierbare Testdaten innerhalb zurückgerollter Transaktionen schreiben. InnoDB und eingeschränkte Rechte administrativ bestätigen. AUTO_INCREMENT kann trotz Rollback steigen.
5. Vor späteren Prüfungen mit repräsentativen Altbeständen muss der Eigentümer einen isolierten, freigegebenen Datenbestand bereitstellen. Die bestehende Testdatenbank dafür nicht überschreiben oder zurücksetzen; Bereitstellung/Import sind ein separater Auftrag. Kein automatisches Kopieren aus Produktion durch Codex.
6. Vorher/nachher Schema-Metadaten, Zeilenzahlen und kanonische Werte ausgewählter Datensätze vergleichen: IDs/FKs, Titel/URLs, Preise/Anpassungen, Datumswerte, Lesestatus/Rereads, Kaufstatus und Detailbeziehungen. Vorhandene Stammdaten vollständig auf unveränderte Werte prüfen. Hibernate-Validierung allein bestätigt weder alle Constraints noch die Vollständigkeit der Daten.

`SQLQuery.sql:3` enthält ausdrücklich `USE mangalib`; das Skript ist kein Testsetup und keine versionierte Migration. Außerdem fehlt vor dem Primärschlüssel von `mangadetails` ein Komma. `Inserts.sql` ist ein Seed-Skript. Beide **nicht ausführen** und nicht als verlässlichen Nachweis des tatsächlich bestehenden Schemas verwenden. Die alte Installationsanleitung im `readme` ist für diesen Upgrade-Plan nicht anwendbar.

Der Connector wird nach aktuellem BOM nicht gewechselt. MySQL-Serverversion, SQL-Mode, Collation, Zeitzone und Treiberverhalten trotzdem in der isolierten Umgebung prüfen; Produktionskompatibilität lässt sich aus einem einzelnen Testserver nicht vollständig ableiten. Keine Änderung der festen Test-JDBC-URL zur Umgehung eines Treiberfehlers.

### 4.6 Testinfrastruktur und Datenbankschutz – P0

**Bestehender Schutz:**

- `src/test/resources/META-INF/spring.factories:2` registriert `TestDatabaseContextCustomizerFactory` für Spring TestContext.
- `TestDatabaseContextCustomizerFactory.java:27–44` registriert die DataSource vor dem Refresh und prüft Konfiguration/Bean-Identität vor regulärer Singleton-Erzeugung. Hibernate-Schemavalidierung findet erst danach statt.
- `TestDatabaseGuard.java:21–49` erzwingt ausschließlich das Testprofil, feste URL/Benutzer, das Testpasswort, deaktivierte Initialisierung/Migrationen und verbietet zusätzliche DataSource-/direkte JPA-Properties.
- `GuardedTestDataSource.java:20–31` prüft erneut vor JDBC und verwendet nur feste Testparameter; alternative Zugangsdaten sind gesperrt.

Die [Framework-7-Dokumentation](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/context-customizers.html) unterstützt weiterhin die Registrierung von ContextCustomizerFactory über `spring.factories`; nicht mit entfernten Boot-Auto-Konfigurationsregistrierungen verwechseln. Auch [Binder](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/context/properties/bind/Binder.html) existiert im verwendeten Paket. Dennoch muss die tatsächliche Reihenfolge unter dem neuen Stack erneut nachgewiesen werden.

**Zusätzliche Schutztests vor jedem DB-Test:** alternative zweite DataSource; Ersetzung der registrierten Bean unter demselben Namen; sichere Konfiguration mit späterem verbotenen Override; JVM-/Test-Properties und `SPRING_APPLICATION_JSON`; Erkennung vor Hibernate-Initialisierung. Ausschließlich synthetische Konfiguration und ein JDBC-Aufrufzähler/Stub verwenden: Auch Negativtests mit dem String `mangalib` dürfen niemals eine echte Verbindung versuchen. Die bestehenden 31 Prüfungen nicht durch neue ersetzen oder abschwächen.

**JUnit-/Slice-Risiken:** Aktuelle Methoden sind gültige nichtstatische `void`-Tests mit Assertions. Es gibt keine `@MockBean`-/`@SpyBean`-Migration im vorhandenen Code. Neue [AutoConfigureMockMvc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.html)-Imports liegen unter `org.springframework.boot.webmvc.test.autoconfigure`. Ein neues [DataJpaTest](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/data/jpa/test/autoconfigure/DataJpaTest.html) würde standardmäßig die DataSource ersetzen; für MangaLib zunächst Vollkontexte mit bestehendem Guard bevorzugen. Bei späteren Slices wäre `replace=NONE` plus Nachweis derselben GuardedTestDataSource erforderlich.

Der globale Customizer verlangt auch bei neuen Spring-MVC-Slices Testprofil/Passwort. Ihn nicht mit `REPLACE_DEFAULTS`, ausgeschalteter Registrierung oder gemockter ungeschützter DataSource umgehen. Datenbankfreie Tests können reines JUnit verwenden; MVC-Integrationstests sollen auf dem geschützten Vollkontext aufbauen. MockMvc führt synchrone Aufrufe im Testthread aus und lässt sich in die Testtransaktion einbinden. Keine HTTP-Schreibtests gegen einen separaten Server, keine asynchronen Schreibpfade, kein REQUIRES_NEW und kein Commit ohne eigenes Isolationskonzept.

**Restgrenzen:** Der Guard ist keine Sandbox für beliebigen Java-Code. Direkte DriverManager-Verbindungen, manuelle SpringApplication-Starts und Nebenwirkungen eigener Initialisierer bleiben unzulässig. Serverrechte und lokale Weiterleitungen werden damit nicht geprüft. Die erfolgreiche Testausführung belegt das sichere konfigurierte Ziel dieses Laufs, nicht eine vollständige externe Rechte-/Netzwerkauditierung.

### 4.7 Selenium, Scraper und weitere Abhängigkeiten – P1/P2

`build.gradle.kts:35–37` pinnt jsoup, Selenium und WebDriverManager. Die Scraper importieren Selenium direkt, nicht Spring-Webclients. Ein Framework-7-HTTP-Clientwechsel ersetzt daher keinen dieser Browserzugriffe.

**Risiko:** Die explizite alte `selenium-java`-Version und das neue BOM können unterschiedliche Versionen einzelner Selenium-Module ergeben. Das ist ohne Dependency-Auflösung noch kein bewiesener Konflikt. Später `runtimeClasspath` und `testRuntimeClasspath` prüfen, einschließlich Selenium, Jackson 2/3, HTTP-Client, Guava, Byte Buddy und Logging. Falls gemischt: vorzugsweise den Selenium-Pin entfernen und einheitlich das BOM nutzen; nur als begründete Kompatibilitätskorrektur. WebDriverManager/jsoup nicht nebenbei entfernen oder aktualisieren.

`MangaScraper.java:274–281` und `BandScraper.java:243–247` enthalten einen lokalen GeckoDriver-Pfad. Browser-/Treiberkompatibilität bleibt unabhängig von Boot offen. Während der Migration keine Scraper ausführen: bestehendes `@Disabled` und Tag `external-network` sowie Gradle-Ausschluss erhalten. Auch indirekte Aufrufe von `/scrape` und `/autofillBandData/...` in neuen Controllertests müssen durch feste Antworten ersetzt werden, ohne Firefox zu starten. Dafür möglichst vorhandenes Mockito verwenden; keine zusätzliche Browser-Testplattform einführen.

Ein späterer realer Scraper-Smoke-Test wäre ein gesondert freizugebender manueller Auftrag. Kompilierung und stubbasierte Tests beweisen die Kompatibilität mit externem HTML und installiertem Browser nicht.

### 4.8 Bekannte Schulden und Warnungen abgrenzen – P2

Im erfolgreichen 3.5.16-Lauf waren OSIV-, Mockito-/Byte-Buddy-Selbstanbindungs- und JVM-Class-Sharing-Warnungen vorhanden. Diese als Baseline erfassen. Im Upgrade neue Compiler-Deprecations, Property-Binding-Warnungen, HQL-Fehler und Dependency-Konflikte separat behandeln; erwartete negative Guard-Tests sind kein Integrationsfehler.

Bestehende Probleme wie Centverlust durch `EinkaufslisteService.java:145`, Lesestatusänderungen in `MangaReiheService.java:661`, Monatsgruppierung, breite Cascades und mehrteilige Kaufübernahme sind bereits Gegenstand der Roadmap. Sie dürfen durch das Upgrade nicht verschlechtert oder stillschweigend fachlich geändert werden. Neue Tests müssen bestehende Fehler von Migrationsregressionen unterscheiden; erforderliche fachliche Korrekturen als separate Aufgaben behandeln. Keine Sicherheitseinstellungen, Grenzen oder Assertions lockern, um einen grünen Lauf zu erzielen.

## 5. Aussagekraft der vorhandenen 34 Tests

Die vorhandenen XML-Berichte vom **10.10.2026, 11:29 UTC** wurden gelesen. Ergebnis des zuvor eigenständig durch Codex ausgeführten `.\gradlew.bat --no-daemon clean test` unter 3.5.16:

| Klasse | Bestanden | Tatsächliche Absicherung |
|---|---:|---|
| `TestDatabaseGuardTest` | 31 | Konfigurations-/Profilfehler, Registrierungsnachweis, Abbruch vor Singleton-Erzeugung/JDBC; ohne Datenbank. |
| `MangalibApplicationTests` | 1 | Kontextstart, Schema-Validierung und GuardedTestDataSource-Typ. |
| `DemoApplicationTests` | 1 | Kontextstart und Repository-Verdrahtung. |
| `ServiceTest` | 1 | Neue Reihe mit Details und zehn Bänden; Preis 5,99 €, Gesamtpreis 59,90 €, Beziehungen, Reload und initialer Lesestatus; transaktionaler Rollback. |
| **Gesamt** | **34** | **0 Failures, 0 Errors, 0 Skipped in diesen Berichten.** |

Die beiden deaktivierten/taggefilterten Scraperklassen sind nicht Bestandteil dieser 34 Fälle. Die Zahl ist kein Coverage-Prozentsatz. **31 von 34 Fällen betreffen den Schutz, nur einer einen fachlichen Speicherablauf.** Es fehlen MVC/JSON, Templates, Einkauf/Kaufübernahme, Änderungen vorhandener Datensätze, Query-Ergebnisse, Null-/Randfälle, Datenvergleich nach Rollback und Performance-Baselines. Die Suite ist eine notwendige Grundlage, aber keine ausreichende Freigabe für Boot 4.

`TESTING.md` enthält noch die historische Aussage aus dem damaligen Implementierungsauftrag, es seien keine Tests ausgeführt worden. Die obigen 34 Fälle beschreiben die ursprüngliche Baseline. Den erweiterten Laufstatus dokumentiert Abschnitt 10; die Sicherheitsregeln aus TESTING.md bleiben verbindlich.

## 6. Zusätzliche Tests mit Prioritäten und Akzeptanzkriterien

Alle fachlichen Tests zunächst gegen **3.5.16** etablieren und danach gegen den Zielstack ausführen; fachliche Assertions unverändert erhalten, notwendige Framework-/Mapper-Testimports gezielt anpassen. Nur neue Testdatensätze verwenden; bestehende Stammdaten nicht ändern. Die drei P0-Zeilen sind implementiert (Abschnitt 10), die MVC-/JSON-Baseline ist ergänzt (Abschnitt 11). Weitere P1/P2-Arbeiten bleiben offen.

| Priorität | Vorgeschlagene Dateien unter `src/test/java/de/mangalib/` | Mindestinhalt / Akzeptanz |
|---|---|---|
| P0 | `testsupport/TestDatabaseGuardTest.java`, ggf. separate Lifecycle-Prüfung | Alle Schutzfälle aus 4.6; bei unsicherer Konfiguration **0 JDBC-Aufrufe**, keine vorzeitig erzeugte ORM-Bean. |
| P0 | `PersistenceCompatibilityTest.java` | Schema nur validieren; beide MapsId-Aggregate anlegen/ändern/flushen/reloaden; DATE/Timestamp, URL, nullable Boolean, BigDecimal und FK-Werte erhalten. |
| P0 | `TestDataPreservationTest.java` | Vorher-/Nachher-Vergleich vorhandener Stammdaten; neue Test-ID nach Rollback nicht vorhanden, vorhandene Datensätze unverändert. Nachprüfung etwa über `@AfterTransaction` lesend; keine eigenen ungeschützten JDBC-Verbindungen. |
| P1 | `MvcContractTest.java` | Echte vom JavaScript verwendete Payloads; Antworten von Reihe/Band/Einkauf/Statistik; JSON-Typen, NON_NULL, Daten, Preise, URLs, Lesestatus, Referenzen; Status/Content-Type bei Erfolg/Fehler. Scraper stubs. |
| P1 | `RepositoryCompatibilityTest.java` | Alle eigenen Queries tatsächlich ausführen; Summen mit Cent, Null/leer, Jahreswechsel, Statusfilter, Multiplikator, Bandkategorien, case-insensitive Suche und Sortierung. |
| P1 | `EinkaufslisteServiceTest.java`, Erweiterung `ServiceTest.java` | Hinzufügen/Bearbeiten und Kaufübernahme in neue/existierende Testreihe; Bestand an Testbänden und Details bleibt konsistent; Lesestatus/Preisanpassung/Cover dürfen nicht zusätzlich regressieren. |
| P1 | `TemplateRenderingTest.java` | Home/Einkauf/Statistik rendern; relevante DOM-IDs, Formularnamen, lokale Assets, Nullfälle und deutsche Zahlen-/Datumsformate erhalten; kein Browser/externes Netzwerk. Lazy-Rendering nicht nur unter Testtransaktion prüfen. |
| P2 | getrennte Performance-Messung | Gleicher isolierter Datenbestand vor/nach Upgrade: SQL-Anzahl und Antwortzeiten von Home, Detail und Statistik; zusätzliche SELECTs/Joins untersuchen. Keine neue Benchmark-Abhängigkeit nötig. |

Vor Vergleichen gemeinsame Testdaten/Baselines definieren; Laufzeit-IDs und aktuelle Zeitstempel nicht blind als Golden Master vergleichen. Für reale Altbestände insbesondere unvollständige Details, NULL-Werte, negative Preisanpassungen, bereits gelesene Bände und alte Datumswerte berücksichtigen. Fehlen solche Daten, als verbleibende Lücke dokumentieren.

## 7. Priorisierter Umsetzungsplan

Schätzung in konzentrierten Entwicklungstagen, ohne Bereitstellung von Altdaten, administrative Wartezeiten oder fachliche Bugfixes. Die grobe Gesamtschätzung von **5–9 Tagen** ist keine Zusage; ORM-/JSON-Abweichungen können zusätzliche Arbeit verursachen.

| Schritt | Dateien/Arbeit | Nutzen und Abschlusskriterium | Aufwand / Risiko |
|---|---|---|---|
| 1 – P0: Baseline sichern | Git-Status, separater Migrationsbranch gemäß AGENTS.md; Build-/Testreports, Dependency-Graph und sichere Testvoraussetzungen erfassen. | Reproduzierbarer 3.5.16-Ausgangspunkt, Zielversion erneut verifiziert, bestätigte DB-Rechte. | 0,5 Tag; gering. |
| 2 – P0/P1: Schutz und Verträge ergänzen | Testdateien aus Abschnitt 6, ausschließlich unter 3.5.16. | Guard, wichtigste HTTP-/JSON-Verträge, Query- und Datenintegritätstests funktionieren vor dem Versionswechsel. Bestehende Fehler getrennt dokumentiert. | 2–3 Tage; mittel wegen fehlender Fachtests. |
| 3 – P1: Boot-4.0-Zwischenstand | `build.gradle.kts`, JSON-Schlüssel in `application.properties`, erforderliche Testimports; gezielte Temporal-Bereinigung in zwei Entities. | Kompilation, datenbankfreie Guard-Prüfungen und dann geschützte Suite erfolgreich; keine Schemaänderung. | 0,5–1 Tag; mittel. |
| 4 – P1: Ziel 4.1.1 | Boot-Plugin final setzen; tatsächlichen Graphen prüfen, ggf. Selenium-Module ausrichten; weitere Codeänderungen nur für belegte Inkompatibilitäten. | Gleiche Verträge und Datenmodelle unter Ziel-BOM; keine unbegründeten Pins/Fallbacks. | 0,5–1 Tag; mittel. |
| 5 – P0/P1: Bestand und Laufzeit validieren | Geschützte Integrationstests, bereitgestellter repräsentativer Altbestand, Query-/Rendering-Vergleich, anschließend `assemble`. | Kein DDL, keine Änderung vorhandener Werte/Stammdaten, keine neuen fachlichen Regressionen; Performance-Abweichungen geklärt. | 1–2 Tage; höchstes Risiko bei unvollständigem Altbestand. |
| 6 – P1: Abnahme dokumentieren | `BOOT4_MIGRATION.md`, später autorisierte Anpassung `TESTING.md`, `ROADMAP.md`; finaler Diff/Report. | Erfüllte Freigabekriterien, verbleibende Browser-/Scrapergrenze sichtbar, Rückweg beschrieben. | 0,5–1 Tag; gering. |

Kein Schritt umfasst neue Fachfunktionen, UI-Redesign, Authentifizierung, Transaktionsrefactoring oder Produktivdeployment. Änderungen an Repository-/Service-/Entity-Dateien zusätzlich zu den beschriebenen Kandidaten nur nach reproduzierbarem Migrationsfehler und mit passendem Regressionstest. Kein Commit ohne ausdrücklichen Auftrag.

## 8. Spätere Validierungsstrategie und Abbruchregeln

Diese Befehle sind **nur für einen später autorisierten Implementierungsauftrag** vorgesehen und wurden hier nicht ausgeführt. Vorher Aufgabenabhängigkeiten kontrollieren; Wrapper/Buildskript dürfen keine neuen Anwendungsstarts, Scraper oder Dateninitialisierer enthalten.

1. Ohne Datenbank: Dependency-Berichte für `runtimeClasspath`/`testRuntimeClasspath` und gezieltes `dependencyInsight` für Hibernate, Jackson und Selenium erstellen. `compileJava compileTestJava` ausführen und Warnungen mit der 3.5.16-Baseline vergleichen. Gradle-Downloads sind von gesperrtem Scraping zu unterscheiden.
2. Zuerst datenbankfreie Schutztests:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
   ```

3. Nur bei nachgewiesen intaktem Guard und administrativ bestätigtem Testziel: `MANGALIB_TEST_DB_PASSWORD` sicher in derselben Prozessumgebung bereitstellen, nur Vorhandensein prüfen. Kontextstart und Service-Test gestuft nach TESTING.md ausführen. Keine vollständigen Environment-Dumps oder Passwörter ausgeben.
4. Die erweiterten Fach-/Persistenztests auf ausschließlich neuen Testdatensätzen ausführen; Rollback und Vorher-/Nachher-Vergleiche kontrollieren. Danach die vollständige automatische Suite:

   ```powershell
   .\gradlew.bat --no-daemon clean test
   ```

5. XML-/HTML-Berichte auf Zahl, Failures/Errors, unerwartete Skips und Zielstack prüfen. Mindestens die bisherigen 34 Fälle müssen weiterhin enthalten sein; die neuen Fälle erhöhen die erwartete Zahl. Absichtlich deaktivierte Scraper nicht als erfolgreich getestet zählen.
6. Nach Kontrolle des Task-Graphen Paketierung ohne zusätzliche Tests:

   ```powershell
   .\gradlew.bat --no-daemon assemble --dry-run
   .\gradlew.bat --no-daemon assemble
   ```

7. JAR-Inhalt/Dependency-Versionen und finalen Diff prüfen. **Kein `bootRun` oder `java -jar` zur Testvalidierung:** Der bestehende Guard lebt nur im Test-Classpath. Ein späterer manueller UI-Test benötigt einen separat überprüften, ausdrücklich freigegebenen Startweg mit Testzugang und unveränderlichem Testziel. UI-Freigabe anhand von MockMvc-Rendering plus späterem kontrolliertem Sichtvergleich; externe Bilder/CDNs bei Bedarf sperren, Scraping bleibt ausgeschlossen.

**Sofort abbrechen bei:** fehlendem Guard, unerwartetem Profil/Zielbenutzer/Schema, ungeprüfter DataSource, DDL-/Seed-Versuch, geändertem Altbestand, nicht zurückgerollten Testdaten oder einem nicht abgefangenen Browser-/Scraperstart. Schutzregeln und `validate` niemals zum Fortsetzen lockern.

**Freigabekriterien:** vollständige Suite und Build erfolgreich; alle P0-/P1-Verträge geprüft; Schema unverändert; vorhandene Test-/Altbestandswerte und Stammdaten unverändert; keine ungeklärten neuen Warnungen oder wesentlichen Performanceverluste; UI unverändert; Test-/Betriebsstartwege getrennt dokumentiert. Ein ausschließlich grüner Kontexttest reicht nicht.

**Rückweg:** geprüften 3.5.16-Stand und Artefakt separat erhalten. Solange Schema und Datenformat unverändert sind, ist ein Code-/Artefakt-Rückwechsel grundsätzlich möglich; Rückwärtslesen der Daten auf der isolierten Kopie prüfen. Ein Rückwechsel des JARs repariert keine bereits falsch geschriebenen Daten. Vor einem späteren, separat freizugebenden produktiven Wechsel daher Backup und Wiederherstellbarkeit bestätigen. Weder Deployment noch Restore werden durch diesen Plan autorisiert.

## 9. Offene Fragen vor der Freigabe

- Sind Testrechte ohne globale/produktive sowie CREATE-/ALTER-/DROP-Rechte und InnoDB aktuell administrativ bestätigt?
- Wie repräsentativ ist die bestehende Teststruktur für den Altbestand, und wie werden zulässige repräsentative Datensätze bereitgestellt, ohne vorhandene Daten zu überschreiben?
- Welche effektiven Selenium-/Jackson-/HTTP-Client-Versionen werden nach dem Upgrade tatsächlich aufgelöst?
- Funktionieren MapsId-Updates, sämtliche Aggregate und JSON-Antworten unter dem Zielstack? Das ist derzeit ein Testauftrag, kein bereits bewiesener Fehler.
- Welche bekannten fachlichen Fehler werden als separate Roadmap-Aufgaben behandelt, und welche wären ein Hindernis für die Abnahme unveränderter Funktionen?
- Wann wird die separat freizugebende Browser-/Scraperkompatibilität geprüft? Bis dahin bleibt sie ausdrücklich unbestätigt.

Die Analyse liefert damit einen begrenzten Upgrade-Pfad mit konkreten Prüfpunkten. Eine Zusicherung „ohne Datenverlust“ setzt das Bestehen der beschriebenen Daten- und Regressionstests voraus; sie wird nicht aus der Versionsnummer oder den bisherigen 34 Tests abgeleitet.

## 10. Tatsächlicher Fortschritt: P0-Persistenztests unter 3.5.16

Stand **10.10.2026**: Die P0-Tests aus Abschnitt 6 sind implementiert und durch Codex eigenständig gestuft ausgeführt. Spring Boot bleibt **3.5.16**, Java 21 und Gradle 8.14.3 bleiben unverändert. Keine Migration, Schemaänderung, produktive Verbindung oder Scraper-Ausführung; kein Git-Commit.

### Implementierte Dateien und Sicherheitsnachweise

- `src/test/java/de/mangalib/testsupport/TestDatabaseGuardTest.java`: bisherige 31 Fälle erhalten, 13 Fälle ergänzt (insgesamt 44). Zweite DataSource, Ersetzung unter demselben Bean-Namen sogar bei gleichem Typ, spätere URL-/Benutzer-/DDL-Overrides, höher priorisierte JVM-/Test-PropertySources und tatsächlich geparstes `SPRING_APPLICATION_JSON`. Lifecycle-Negativfälle brechen vor einer ORM-Probe-Bean ab; statischer DriverManager-Mock verhindert echte JDBC-Verbindungen.
- `src/test/java/de/mangalib/testsupport/TestDatabaseContextCustomizerFactory.java`: bisherige Registrierung und Prüfungen in eine package-private Methode mit Passwort-Supplier verschoben, damit Lifecycle-Tests rein synthetisch funktionieren. Der automatisch registrierte Customizer liest weiterhin ausschließlich `MANGALIB_TEST_DB_PASSWORD`; die echte `GuardedTestDataSource` wird unverändert registriert und liest/prüft die Prozessvariable erneut vor jeder Verbindung. Bean-Anzahl und Definition-Identität werden unverändert vor regulärer Singleton-Erzeugung geprüft. Die Prüfstelle ist kein neuer Verbindungsweg.
- `src/test/java/de/mangalib/PersistenceTestFixtures.java`: ausschließlich neue, eindeutig benannte Aggregate und Referenzen auf vorhandene Stammdaten. Vor dem ersten INSERT werden aktive Rollback-Testtransaktion, tatsächliches Schema `mangalib_test`, effektiver Benutzer `mangalib_test_user` und InnoDB für alle fünf beschriebenen Geschäftstabellen geprüft. Alle SELECTs und Schreibzugriffe laufen über den bestehenden geschützten EntityManager; keine eigenen JDBC-Verbindungen. URLs werden nur geparst und als Text verglichen, ohne HTTP-/DNS-Abfragen.
- `src/test/java/de/mangalib/PersistenceCompatibilityTest.java`: fünf Fälle für Reihen/Details/Bände, Einkaufslisten/Details und Boolean-Werte `null`, `false`, `true`. Persistieren, Flush, Clear, Reload und Änderungen ausschließlich eigener Testdatensätze; beide `@MapsId`-Beziehungen, IDs/FK-Referenzen, positive/negative Centbeträge, DATE/Schalttag, Timestamp, URLs und Lesewerte werden geprüft. `@AfterTransaction` bestätigt für jeden Fall die Abwesenheit sämtlicher neu angelegter Aggregate nach dem tatsächlichen Rollback.
- `src/test/java/de/mangalib/TestDataPreservationTest.java`: ein Fall mit skalaren, nach Primärschlüssel geordneten SQL-Snapshots sämtlicher Spalten aller zehn Anwendungstabellen. Vorher-/Nachher-Snapshots liegen außerhalb der Schreibtransaktion. Während der Transaktion müssen alle vorherigen Zeilen unverändert enthalten sein und exakt sechs neue Zeilen hinzukommen; nach Rollback müssen sämtliche Tabellen wieder identische Zeilen/Werte enthalten. Zusätzlich werden die neuen IDs auf Abwesenheit geprüft. Die fünf Stammdatentabellen werden weder angelegt noch verändert.

Der zunächst fehlgeschlagene datenbankfreie Test war ein Fehler der Mock-Abgrenzung: Bereits der Konstruktor der erwarteten `SQLException` ruft `DriverManager.getLogWriter()` auf. Der Mock erlaubt nun ausschließlich diesen verbindungsfreien Aufruf und prüft, dass keine weiteren Aufrufe erfolgten. Der Guard wurde zur Behebung nicht gelockert. Kein fachlicher Produktionsfehler wurde durch diese P0-Fälle neu nachgewiesen.

### Tatsächlich ausgeführte Validierung

Vor Datenbanktests wurden Konfiguration und Aufgaben geprüft: `clean test --dry-run` enthielt nur Clean-, Compile-, Ressourcen- und Testaufgaben; `external-network` bleibt ausgeschlossen, beide Scraperklassen bleiben zusätzlich `@Disabled`. Das Testpasswort war in der Prozessumgebung vorhanden und nicht leer; sein Wert wurde nicht ausgegeben.

```powershell
.\gradlew.bat --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --no-daemon test --tests de.mangalib.PersistenceCompatibilityTest --tests de.mangalib.TestDataPreservationTest
.\gradlew.bat --no-daemon clean test
```

Die Integrationstests wurden erst nach allen bestandenen Schutztests gestartet. Nach Ergänzung der Rollback-Nachprüfung pro Persistenzfall wurden die sechs Integrationstestfälle erneut erfolgreich ausgeführt; danach folgte die vollständige Suite.

| Prüfung / Klasse im abschließenden Lauf | Bestanden | Fehlgeschlagen / Errors | Skipped |
|---|---:|---:|---:|
| Datenbankfreier Schutzlauf: `TestDatabaseGuardTest` | 44 | 0 | 0 |
| Gezielter Integrationslauf: beide neuen Testklassen | 6 | 0 | 0 |
| `clean test`: `TestDatabaseGuardTest` | 44 | 0 | 0 |
| `clean test`: `PersistenceCompatibilityTest` | 5 | 0 | 0 |
| `clean test`: `TestDataPreservationTest` | 1 | 0 | 0 |
| `clean test`: bestehende Kontext-/Service-Tests | 3 | 0 | 0 |
| **Vollständige Suite (keine Addition der gestuften Läufe)** | **53** | **0** | **0** |

Nachweis: `build/test-results/test/TEST-*.xml` und `build/reports/tests/test/index.html`. Der abschließende Lauf kompilierte Anwendung und Tests neu. Die ausgeschlossenen Scraper sind nicht Teil dieser 53 Fälle. Keine neuen Compilerwarnungen beobachtet; bekannte OSIV-, Mockito-/Byte-Buddy-Selbstanbindungs- und JVM-Class-Sharing-Warnungen bleiben bestehen. Erwartete Warnungen über abgebrochene Negativtest-Kontexte sind kein Integrationsfehler.

### Verbleibende Grenzen und nächste Arbeiten

- P0 ist für den aktuellen **3.5.16-Teststack** nachgewiesen; dieselben Tests müssen später unter dem Zielstack erneut bestehen. Keine Boot-4-Kompatibilitätsfreigabe durch diesen Auftrag.
- P1-Verträge aus Abschnitt 6 (MVC/JSON, Repository-Abfragen, fachliche Kauf-/Service-Workflows, Template-Rendering) und P2-Performance bleiben offen. Die bereits bekannten fachlichen Roadmap-Probleme wurden nicht verändert.
- Der aktuelle Testbestand wurde auf unveränderte Werte geprüft. Repräsentative historische Bestände, alle Null-/Randfälle und Zeitzonen-/Timestamp-Präzisionsvarianten sind damit nicht vollständig abgedeckt. FK-Referenzen und Reloads sind geprüft; gezielte Constraint-Verletzungs-/Löschtests sind noch nicht enthalten.
- InnoDB wurde für die fünf beschriebenen Geschäftstabellen tatsächlich geprüft. Serverrechte, lokale Weiterleitungen und Gleichheit aller Schema-Metadaten mit Produktion wurden nicht administrativ auditiert. Die eingeschränkten Testrechte bleiben die unabhängige Schutzgrenze.
- Die Vorher-/Nachher-Vergleiche benötigen eine während des Laufs exklusiv genutzte Testdatenbank. Gleichzeitige fremde Änderungen führen bewusst zu einem Fehler. AUTO_INCREMENT-Zähler können durch zurückgerollte Inserts steigen; sie sind keine vorhandenen Zeilenwerte und werden nicht zurückgesetzt.
- Der vollständige Git-Diff einschließlich neuer Testdateien wurde auf diesen Testauftrag begrenzt geprüft; `git diff --check` meldete keine Whitespace-Fehler. Anwendungscode, Build-/Testkonfiguration, Abhängigkeiten und Scraper bleiben unverändert.

## 11. Tatsächlicher Fortschritt: MVC-/JSON-Baseline unter 3.5.16

Stand **10.10.2026**: `src/test/java/de/mangalib/MvcContractTest.java` enthält **29 erfolgreiche Testfälle**, einschließlich parametrisierter Fälle. Echte Controller, Services, Repositories, Thymeleaf und der konfigurierte MVC-Mapper laufen im geschützten `@SpringBootTest` mit `@AutoConfigureMockMvc`, ausschließlich Profil `test` und unverändertem TestDatabaseGuard. Keine neuen Abhängigkeiten, keine fachlichen Änderungen und weiterhin keine Boot-4-Migration. In diesem Auftrag wurden nur diese Dokumentation und die neue Testklasse geändert.

### Abgesicherte Verträge

| Bereich | Nachgewiesene Baseline |
|---|---|
| HTML | `/home`, `/einkaufsliste` und alle fünf `/statistik/st_*`-Ansichten: 200, `text/html`, View-Namen; eigene Daten mit Umlauten, relevante Popup-/Script-Verweise und Statistiklayout. `/resetFilters`: 302 mit Weiterleitung nach Home. MockMvc lädt keine externen Assets nach. |
| Reihen abrufen | `/getMangaReiheData/{id}`: IDs/Index/Anzahl/Reread als JSON-Ganzzahlen, Preise und Anpassungen als deutsche Dezimalstrings, echte Booleans, AniList-URL; fehlende Reihe: leere 404-Antwort. |
| Bände abrufen | `/getBand/{id}` und `/mangaReihe/{id}/bands`: flache Objekte/Listen, numerische Preise mit Centbeträgen, boolesche Lesewerte, URL-Strings und formatierte Preisstrings. Kein serialisiertes Elternobjekt. Fehlender Einzelband: leere 404-Antwort. |
| Reihen schreiben | `/addMangaReihe`: Anlegen und Bearbeiten gemäß `popup_add_reihe.js:244` und `popup_edit_reihe.js:316`; Lookup-/Edit-IDs als Strings, Index/Anzahl als Ganzzahlen, Booleans, Preisstrings mit Punkt. Edit-Payload lässt Preise tatsächlich weg. Rückgabe: 200 JSON, numerische Entity-Preise, DATE als ISO-String, Timestamp als parsebarer ISO-Zeitstring mit Offset. Persistenz und Detail-ID nach Flush/Clear/Reload geprüft. |
| Null/fehlende Angaben | Bei Anlage sind Index, URLs, Sammelbandauswahl, Reread und nullable Booleans sowohl explizit `null` als auch weglassbar. `NON_NULL` entfernt entsprechende JSON-Felder. Einzelbandantworten verwenden stattdessen leere Strings für fehlende URL/Index, `false` für Special und `0` für Reread. |
| Beziehungen | Edit-Antwort enthält Details mit derselben ID; deren Eltern-/Sammelband-Backreferences und Lookup-Backreferences fehlen. Keine rekursive Elternserialisierung. Bei Anlage fehlt die inverse Detailreferenz im Antwortobjekt, obwohl die Details nach Reload existieren; dieses bestehende Verhalten ist ausdrücklich erfasst. |
| Band schreiben | `/editBand` gemäß `popup_edit_baende.js:100`: ID/Bandnummer/Index/Preise/Reread als Strings, Booleans als Booleans; 204 ohne Body/Content-Type. Reload bestätigt Preise, Anpassung, Special, Reread und Lesestatus. |
| Einkauf | `/addToEinkaufsliste` gemäß `einkaufsliste.js:270`: 200 JSON mit numerischen Preisen, booleschem Kaufstatus und ISO-Schalttagsdatum beziehungsweise ausgelassenem Datum. String-Lookup-IDs werden verarbeitet, Backreferences nicht ausgegeben, Details sind nach Reload vorhanden. `DELETE /items/{id}` nur für eigenes neues Fixture: leere 200-Antwort. |
| Kauf | `/buyItemUpdate`: fehlendes Item 404 Text; eindeutig noch nicht vorhandene Testreihe 417 JSON mit Statusauswahl-Nachricht. `/buyItemCreate`: String-IDs wie im JavaScript werden zu Long konvertiert, 200 JSON mit Erfolgsnachricht; neue Reihe/Details und Kaufstatus nach Reload geprüft. Vorab wird ausgeschlossen, dass Index/Titel Bestandsreihen treffen. |
| Statistik | `/statistik/bandKategorien`: exakt die sieben vom JavaScript gelesenen Schlüssel, ganzzahlige Werte. `/statistik/monatsdaten`: Monatsnummer als JSON-Stringschlüssel, Ganzzahlen für Bände und derzeit auch Geld; unbekannter Typ/fehlendes Jahr: 400. Geld-Centverlust ausdrücklich als bestehender Fehler charakterisiert. |
| Fehler/Content-Type | Malformed JSON, fehlende Pflichtfelder und falsche ID-Typen: 400; falscher Request-Content-Type: 415. Vom Controller erzeugte Fehlertexte bleiben `text/plain`; erfolgreiche JSON-Antworten `application/json`, leere 204/404-Antworten ohne Content-Type. |

Die Tests vergleichen JSON-Werte und Typen, keine Feldreihenfolge. Für Null-/Einkaufsvarianten wird der vorhandene Boot-3-ObjectMapper zur Payload-Erzeugung/Antwortanalyse verwendet. Beim späteren Upgrade sind dessen Databind-Imports und gegebenenfalls Tree-APIs sowie der AutoConfigureMockMvc-Import anzupassen; die fachlichen Assertions dürfen dadurch nicht aufgeweicht werden.

### Isolation und tatsächliche Validierung

- Alle Fixtures und HTTP-Schreibaktionen liegen in einer aktiven, für Rollback markierten Testtransaktion. Die untersuchten Services/Repositories verwenden keinen `REQUIRES_NEW`- oder asynchronen Schreibpfad. MockMvc ruft synchron im Testthread auf; jedes Ergebnis wird auf fehlenden Async-Start geprüft. Kein echter HTTP-Server wird gestartet.
- Die vorhandenen Fixtures prüfen vor INSERT Schema `mangalib_test`, effektiven Benutzer `mangalib_test_user` und InnoDB. Vor **jedem** MVC-Fall erfassen `@BeforeTransaction` und nach tatsächlichem Rollback `@AfterTransaction` alle Spalten/Zeilen aller zehn Tabellen über den vorhandenen geschützten EntityManager. Identische Vorher-/Nachher-Werte bestätigen vollständigen Rollback und unveränderte Stammdaten, auch nach Kauf/Löschen.
- Statische Mockito-Mocks beider Scraper werfen bei jedem unerwarteten Aufruf einen AssertionError; zusätzlich wird fehlende Interaktion geprüft. `/scrape` und `/autofillBandData` werden nicht aufgerufen. Kein Firefox/Selenium/externes Scraping.
- Der bestehende BandService verwendet `URL.equals`, das DNS auslösen kann. Der Band-Edit-Test verwendet für neue URLs numerische Loopback-Adressen und gegenüber den Fixtures ein anderes Protokoll, sodass der Vergleich vor Hostauflösung endet. Es wird keine Loopback-Verbindung geöffnet. Gleiches-Protokoll-/DNS-Verhalten bleibt ungetestet; keine Produktionslogik wurde hierfür verändert.

Codex führte zuerst 44 datenbankfreie Schutzfälle erfolgreich aus, danach die MVC-Tests und abschließend einen vollständigen Neuaufbau der Testsuite:

```powershell
.\gradlew.bat --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
.\gradlew.bat --no-daemon test --tests de.mangalib.MvcContractTest
.\gradlew.bat --no-daemon clean test
```

| Lauf | Bestanden | Failures / Errors | Skipped |
|---|---:|---:|---:|
| Datenbankfreie Schutztests | 44 | 0 | 0 |
| Abschließender gezielter MVC-Lauf | 29 | 0 | 0 |
| Vollständiges `clean test`: bisherige Baseline + MVC | **82 (53 + 29)** | **0** | **0** |

XML-/HTML-Nachweise unter `build/test-results/test/` und `build/reports/tests/test/index.html`. Der erste MVC-Lauf hatte vier Fehler durch eine falsche Testannahme über vollständig leere Statistik-Unterseiten; tatsächlich existiert dort bereits Layout/Navigation. Nur die Testannahme wurde korrigiert. Die abschließenden Läufe einschließlich aller Rollback-Nachprüfungen sind erfolgreich. Bekannte OSIV-/Mockito-/Byte-Buddy-/JVM-Warnungen bleiben; zusätzliche erwartete MVC-Warnungen betreffen die getesteten 400/415-Fälle. Keine neuen Compilerwarnungen beobachtet.

### Bestehende Probleme und offene Testlücken

- **Nachgewiesener bestehender Fehler:** `EinkaufslisteService.java:145` verwendet `Number.longValue()`. Bei 14,98 EUR liefert `/statistik/monatsdaten?typ=geld` die Zahl 14. Der entsprechende Test heißt ausdrücklich `monthlyStatisticsKeepStringKeysAndExistingCentTruncation`; er beschreibt die Baseline, keine fachlich korrekte Geldberechnung. Die spätere separate Fehlerbehebung muss diesen Vertrag gezielt aktualisieren (Roadmap M04/T04).
- **Beobachtetes Verhalten:** Detaildaten fehlen in Neuanlage-Antworten wegen nicht gesetzter inverser Referenzen, sind aber nach Reload vorhanden. Entity-/Backreference-Verträge unterscheiden sich deshalb von flachen GET-Antworten. Kein stillschweigender DTO-/Annotationsumbau.
- **Statisch weiterhin erkennbare Risiken, nicht neu reproduziert:** null-sensitive Details-/Sammelbandzugriffe beim Reihenabruf und nullable Boolean-Vergleiche beim Bearbeiten; URL.equals-DNS im BandService. Diese Fehlerpfade sind keine freigegebenen Korrekturen dieses Auftrags.
- Kaufübernahme in eine bereits vorhandene eigene Testreihe, Wiederholungs-/Parallelkauf, Fehler zwischen mehreren Service-Aufrufen, Bandanzahländerungen und sämtliche Such-/Sortier-/Filterkombinationen sind noch nicht vollständig geprüft. Erfolgreicher Rollback unter Testtransaktion belegt nicht die Atomarität des Produktions-HTTP-Pfads ohne diese umschließende Transaktion.
- Tiefere Template-/DOM-Verträge, tatsächliche Browserinteraktionen, Lazy-Rendering außerhalb der Testtransaktion, exakte Timestamp-Präzision/Zeitzonen, repräsentative Altbestände und umfassende Repository-/Performance-Baselines bleiben offen. Die vier Statistik-Unterseiten besitzen weiterhin keine fachlichen Auswertungen.
- Der Snapshot-Vergleich erfordert eine exklusiv genutzte Testdatenbank; AUTO_INCREMENT kann trotz Rollback steigen. Serverrechte bleiben administrativ zu bestätigen. Die 82 Fälle sind weiterhin keine Boot-4-Freigabe.

Vollständiger Diff einschließlich neuer Testdatei geprüft; keine Anwendungscode-, Konfigurations-, Schema- oder Abhängigkeitsänderungen. Kein Git-Commit.

## 12. Tatsächlicher Fortschritt: validierter 4.0.8-Zwischenstand

### Ziel und behobenes Abrufproblem

Am **10.10.2026** ist **4.0.8** die offiziell ausgewiesene stabile Patchversion der 4.0-Reihe. Veröffentlicht am 20.08.2026; 4.0.9 ist separat als Snapshot gelistet. Java 21 und Gradle 8.14.3 erfüllen die Systemanforderungen. Quellen: [offizielle Veröffentlichung](https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/), [versionsbezogene Systemanforderungen](https://docs.spring.io/spring-boot/4.0/system-requirements.html).

Nach erfolgreicher 3.5.16-Baseline wurden lokal das Boot-Plugin auf 4.0.8, die Web-/MockMvc-Starter, die beiden geänderten JSON-Konfigurationsschlüssel und die Testimports angepasst. Der anschließende Befehl

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava --warning-mode all
```

scheiterte beim ersten Versuch **bereits bei der Gradle-Konfiguration**, vor Java-Kompilierung oder Testausführung. Maven Central lieferte HTTP **403 Forbidden** für `spring-boot-buildpack-platform:4.0.8`, `spring-boot-loader-tools:4.0.8` und `spring-core:7.0.9`. Direkte lesende Anfragen an beide Central-Hosts und auch an ein 3.5.16-POM lieferten ebenfalls 403, einschließlich außerhalb der Sandbox. Die benötigten Boot-4-Artefakte waren noch nicht im lokalen Gradle-Cache vorhanden.

**Behoben:** Nach Angaben des Projekteigentümers verursachte sein VPN die Sperre. Nach dessen Deaktivierung konnten die offiziellen Artefakte geladen werden. Die zwischenzeitlich zurückgenommenen ungeprüften Versionsänderungen wurden wieder angewandt; alle ergänzten Tests blieben erhalten. Keine Mirror-/Repository-Änderung und keine neuen Credentials. Ein erneuter Compilerlauf belegte zwei Test-API-Änderungen: verschobener Boot-EnvironmentPostProcessor und entfernte Jackson-Tree-Methode `fieldNames()`. Nur diese APIs sowie die neuen Jackson-String-Methoden wurden angepasst; keine Assertions entfernt oder aufgeweicht.

### Umgesetzte Framework-Anpassungen

| Datei | Änderung |
|---|---|
| `build.gradle.kts` | Boot-Plugin 4.0.8; `starter-web` → `starter-webmvc`, `starter-test` → `starter-webmvc-test`. Java 21, Wrapper 8.14.3 und Dependency-Management-Plugin 1.1.4 unverändert. Kein JPA-Slice-Teststarter oder Jackson-2-Fallback. |
| `src/main/resources/application.properties` | `spring.http.converters.preferred-json-mapper=jackson` und `spring.jackson.datatype.datetime.write-dates-as-timestamps=false`; NON_NULL unverändert. Keine DataSource-/DDL-Änderung. |
| `src/test/java/de/mangalib/MvcContractTest.java` | Databind/Tree-Imports nach `tools.jackson`, MockMvc-Import nach `org.springframework.boot.webmvc.test.autoconfigure`; `propertyNames()`, `isString()`, `stringValue()` statt alter Tree-APIs. Zusätzlich wird der zuerst für JSON-Map-Eingaben verwendete HTTP-Converter als Jackson-3-Converter mit derselben getesteten Mapper-Bean nachgewiesen. |
| `src/test/java/de/mangalib/testsupport/TestDatabaseGuardTest.java` | Ausschließlich Import des `SpringApplicationJsonEnvironmentPostProcessor` nach `org.springframework.boot.support`; alle 44 Schutzfälle und ihre Assertions erhalten. |

**Kein notwendiger Entity-Umbau:** Hibernate 7.2.24.Final akzeptiert die bestehenden drei LocalDate-`@Temporal`-Annotationen. `-Xlint:deprecation` und Hibernate melden dafür Deprecated-API-/`HHH90000033`-Warnungen. Sie wurden gemäß „nur soweit erforderlich“ nicht vorsorglich entfernt. DATE-/Timestamp-Roundtrips und unveränderte Schemavalidierung bestehen. Kein Anwendungscode, Controller, Service, Entity, Template, CSS oder JavaScript wurde fachlich verändert. Die Schutzimplementierungen `TestDatabaseGuard`, `GuardedTestDataSource` und `TestDatabaseContextCustomizerFactory` sowie die Testprofil-Konfiguration sind unverändert.

### Ergänzte Baseline und Sicherheitsnachweise

| Neue Datei unter `src/test/java/de/mangalib/` | Fälle | Nachweis |
|---|---:|---|
| `RepositoryCompatibilityTest.java` | 5 | Alle eigenen JPQL-Abfragen sowie abgeleitete Selektoren: YEAR/MONTH, erster Band, Lookup-Namen, Titel/Index/Lesestatus, Status-4-Ausschluss, Cent-Summen, COALESCE ohne Details, sieben Bandkategorien an den Grenzen, Monatsaggregation, Kaufstatus, Sortierung/Datumsintervalle, Jahreswechsel und nullable Datum. |
| `TemplateRenderingTest.java` | 3 | `/home`, `/einkaufsliste`, `/statistik/st_overview` außerhalb einer Testtransaktion: echte Views, 200, HTML, View-Namen, synchroner Request; Scraper gesperrt und unveränderte Daten/Schema. |
| `DatabaseIntegrityBaselineTest.java` | 1 | Ausschließlich lesender Fingerabdruck und Zeilenzahlen aller zehn Tabellen und der Schema-Metadaten; keine Ausgabe vorhandener Datensatzwerte oder Zugangsdaten. |
| `TestDatabaseSnapshot.java` | Hilfsklasse | Gemeinsamer Snapshot über den vorhandenen geschützten EntityManager, einschließlich Spalten, Engines, Indizes und FK-Metadaten; keine alternative DataSource oder direkte JDBC-Verbindung. |

Repository-Fixtures verwenden die vorhandene `PersistenceTestFixtures`-Prüfung für aktive Rollback-Transaktion, Datenbank/Benutzer und InnoDB. Eigene generierte Erstellungsdaten werden für YEAR/MONTH ausschließlich anhand der neu angelegten IDs in derselben Transaktion gesetzt. Stammdaten werden nur referenziert. `@BeforeTransaction`/`@AfterTransaction` vergleichen alle Zeilen und Schema-Metadaten nach tatsächlichem Rollback, nicht nur vor dessen Ausführung. Die Rendering- und Fingerabdrucktests legen keine Datensätze an.

Die Testdatenbank enthält unverändert **78 Stammdatensätze**: Status 4, Verlage 62, Typen 4, Formate 3, Sammelbände 5. Alle fünf Geschäftstabellen haben 0 vorhandene Zeilen. Metadaten: 10 Tabellen, 75 Spalten, 24 Indexspalteneinträge, 12 Fremdschlüssel und 26 Schlüsselspalteneinträge. SHA-256 des vollständigen Snapshots:

```text
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570
```

AUTO_INCREMENT-Zähler sind bewusst kein Bestandteil des Fingerabdrucks: Sie können bei Rollback steigen und werden nicht zurückgesetzt. Die Vorher-/Nachher-Prüfungen erfordern weiterhin exklusive Nutzung der Testdatenbank. Die eingeschränkten Serverrechte bleiben separat administrativ zu bestätigen.

### Validierung und JSON-Vergleich

| Stufe | Stand |
|---|---|
| Datenbankfreie Schutztests unter 3.5.16 | 44 erfolgreich, keine Fehler. |
| Neue Repository-/Rendering-/Integritätstests unter 3.5.16 | 9 erfolgreich, keine Fehler. |
| Vollständige neue 3.5.16-Baseline mit `clean test` | **91 erfolgreich (82 + 9), 0 Failures, 0 Errors, 0 Skipped**. |
| Boot-4-Kompilierung/Abhängigkeitsprüfung | Nach VPN-Behebung und gezielten Test-API-Anpassungen erfolgreich; Runtime- und TestRuntime-Graph ohne unaufgelöste Module. |
| Boot-4-Schutztests ohne Datenbank | **44 erfolgreich**, 0 Fehler. |
| Boot-4-Persistenz-/Repository-/MVC-/Rendering-/Integritätstests einschließlich Kontext | **45 erfolgreich**, 0 Fehler. |
| Zusätzlicher MVC-Lauf mit Nachweis des tatsächlichen Jackson-3-Converters | **29 erfolgreich**, 0 Fehler. |
| Boot-4: vollständiges `clean test` | **91 erfolgreich, 0 Failures, 0 Errors, 0 Skipped**. Gestufte Läufe werden nicht addiert. |
| Boot-4: `assemble --dry-run`, anschließend `assemble` | Erfolgreich; Aufgabenplan ausschließlich Kompilierung, Ressourcen, Main-Class-Auflösung, bootJar und jar; keine erneuten Tests oder Datenbank-/Scraping-Aufgaben. |

XML-/HTML-Ergebnisse liegen wie bisher unter `build/test-results/test/` und `build/reports/tests/test/index.html`. Die erfolgreiche Vorwechsel-Baseline und ihr tatsächlich aufgelöster Runtime-Graph sind zusätzlich unter `.gradle/boot4-validation/baseline-3.5.16/` lokal gesichert, damit `clean` sie nicht löscht; der 4.0.8-Stand unter `.gradle/boot4-validation/target-4.0.8/`. Diese Verzeichnisse und das temporäre Compiler-Lint-Init-Skript sind Git-ignoriert und enthalten keine neuen Zugangsdaten. Bekannte OSIV-, Mockito-/Agent-/JVM-Warnungen sowie erwartete Warnungen der HTTP-/Schutz-Negativtests bleiben bestehen. Neu sind die oben genannten JPA-3.2-`@Temporal`-Warnungen; die Jackson-Tree-Deprecations wurden gezielt durch ihre Nachfolger beseitigt.

Die bestehenden **29 MVC-/JSON-Fälle** aus Abschnitt 11 bestehen mit denselben fachlichen Assertions unter 3.5.16 und 4.0.8. Numeric-/String-Preise einschließlich Centbeträgen, IDs, Booleans, Null-/Missing-Felder, Beziehungen ohne Elternzyklen, ISO-Datumswerte, HTTP-Status und Content-Types entsprechen damit der getesteten Baseline. Der neue Converter-Nachweis verhindert, dass transitive Jackson-2-JARs diesen Vergleich unbemerkt übernehmen. Keine globale Jackson-2-Default-Konfiguration. Kein byteweiser Vergleich beliebiger JSON-Antworten: Feldreihenfolge und exakte Timestamp-Zeitzonen-/Präzisionsdarstellung sind nicht vollständig abgesichert. Der bestehende Centverlust in der Statistik und die inverse Detailreferenz bei Neuanlage sind weiterhin bewusst charakterisiert, nicht behoben.

### Tatsächlich aufgelöste Abhängigkeiten

Runtime-/TestRuntime-Berichte und die Bibliotheken im erzeugten Boot-JAR bestätigen den aktuellen Graphen; die 4.1-BOM-Werte aus Abschnitt 3 wurden nicht als Ist-Versionen übernommen.

| Komponente | 3.5.16-Baseline | 4.0.8-Zwischenstand |
|---|---|---|
| Spring Framework | 6.2.19 | 7.0.9 |
| Spring Data JPA | 3.5.13 | 4.0.7 |
| Hibernate ORM | 6.6.53.Final | 7.2.24.Final |
| Jakarta Persistence | 3.1.0 | 3.2.0 |
| MySQL Connector/J | 9.7.0 | 9.7.0 |
| MVC Jackson Databind | 2.21.4 | 3.1.5 (`tools.jackson`) |
| Thymeleaf/Spring6 | 3.1.5.RELEASE | 3.1.5.RELEASE |
| Tomcat | 10.1.55 | 11.0.24 |
| JUnit Jupiter/Platform | Vorwechsel-Tests bestanden | 6.0.3 |
| Mockito / effektives Byte Buddy | Vorwechsel-Tests bestanden | 5.20.0 / 1.17.8 |
| Selenium API/Firefox-Modul (BOM) | 4.31.0 | 4.37.0 |

Transitives Jackson 2.21.5 bleibt unter anderem über WebDriverManager vorhanden; kein Boot-Jackson-2-MVC-Modul oder Fallback eingebunden. Selenium ist weiterhin gemischt: expliziter Aggregator und ältere Devtools-Artefakte 4.18.1, BOM-verwaltete Kernmodule jetzt 4.37.0. Das ist ein belegter Versionsmix, bisher kein reproduzierter Laufzeitfehler. Ohne Scraper-/Browserausführung gibt es keine Browser-Kompatibilitätsfreigabe. Die expliziten Versionen Selenium 4.18.1, jsoup 1.17.2 und WebDriverManager 5.7.0 wurden mangels nachgewiesenen Kompatibilitätsfehlers nicht verändert. Keine zusätzlichen Abhängigkeiten oder manuellen Hibernate-/Jackson-/JUnit-Pins.

### Verbleibende Grenzen und spätere Fortsetzung

1. Die nichttransaktionalen Rendering-Tests lesen derzeit leere Geschäftslisten und **beweisen keine befüllten Lazy-Beziehungen**. Dafür sind repräsentative, separat freigegebene isolierte Testbestände nötig; keine Fixtures committen, um den Nachweis zu erzwingen.
2. Historische Daten, Zeitzonen-/Timestamp-Präzision, parallele Käufe, HTTP-Atomarität ohne Testtransaktion, Performance und manuelle Browserabnahme bleiben offen. Der bestehende Statistik-Centverlust ist weiterhin eine separate fachliche Aufgabe.
3. Deprecated-LocalDate-`@Temporal` und Selenium-Versionsmix bleiben bekannte technische Schulden; kein stillschweigender Mapping-/Browserumbau. Serverseitige Testrechte und Produktions-Backup/Wiederherstellung sind nicht durch diese Tests administrativ bewiesen.
4. Der 4.0.8-Zwischenstand ist lokal technisch validiert, **kein Deployment oder vollständige Produktions-/Altbestandsfreigabe**. Für den späteren ausdrücklich beauftragten Wechsel auf 4.1.1 Version/Release-Notes und den Graphen erneut prüfen und alle Validierungsstufen samt Daten-/Schema-/JSON-Vergleich wiederholen.

Keine produktive Verbindung, Schemaänderung, Migration, Dateninitialisierung, fachliche Korrektur oder UI-Änderung; kein Git-Commit und kein Wechsel auf 4.1.1.

Abschließender vollständiger Diff einschließlich der vier neuen Testdateien geprüft; `git diff --check` und deren `--no-index --check` ohne Whitespace-Fehler. Das erzeugte JAR bestätigt `Spring-Boot-Version: 4.0.8` und `Build-Jdk-Spec: 21`; der Anwendungscode hat Classfile-Version 65 (Java 21). Die Änderungen beschränken sich auf die elf oben genannten Build-/Konfigurations-/Test-/Dokumentationsdateien; lokale Validierungsartefakte liegen ausschließlich im Git-ignorierten Build-/Gradle-Bereich.

## 13. Tatsächlicher Fortschritt: validierter 4.1.1-Zwischenstand

### Zielversion, Release Notes und Änderungsumfang

Am **10.10.2026** erneut verifiziert: **4.1.1** ist die offiziell ausgewiesene stabile Patchversion der 4.1-Reihe; veröffentlicht am 20.08.2026, 4.1.2-SNAPSHOT separat gelistet. Java 21 und Gradle 8.14.3 werden unterstützt. Quellen: [Veröffentlichung](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/), [Systemanforderungen und stabile Versionen](https://docs.spring.io/spring-boot/system-requirements.html).

Die [offiziellen 4.1-Release-Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) wurden gegen die tatsächliche Konfiguration und API-Nutzung geprüft:

- Entfernte 4.0-Deprecations und Layertools verursachen keine Compile-/Build-Fehler. MangaLib verwendet bereits WebMVC-Starter und Jackson-3-APIs.
- Änderungen für JPA-Bootstrap-Modi `deferred`/`lazy` treffen keine eigene Einstellung. Der neue optionale JDBC-Lazy-Verbindungsabruf ist nicht aktiviert; die bestehende geschützte DataSource bleibt unverändert. Keine zusätzliche DataSource oder Lockerung der Property-Allowlist.
- Neue Jackson-Read-/Write-/Factory-Einstellungen und Handler-Bean-Unterstützung benötigen hier keine Konfigurationsänderung. Der tatsächliche MVC-Converter bleibt Jackson 3; NON_NULL und Datumsdarstellung bestehen die vorhandenen Verträge.
- Tomcat-Verzeichnisbereinigung, Reactor-HTTP-Client-Defaults und Gradle-BuildInfo-Änderungen betreffen keine eigenen Erweiterungen dieser Anwendung. Keine zusätzlichen Actuator-Endpunkte freigeschaltet; kein produktiver Anwendungsstart zum Prüfen dieser Defaults.
- Der ORM-/Spring-Data-Wechsel wurde durch Mapping-, Repository-, Persistenz-, Rollback- und MVC-Tests geprüft, nicht allein aus dem BOM als kompatibel angenommen.

**Geänderte versionierte Dateien:** `build.gradle.kts` (ausschließlich Boot-Plugin 4.0.8 → 4.1.1), `BOOT4_MIGRATION.md`, `ROADMAP.md` und `TESTING.md` (aktueller Validierungsstand). Keine zusätzlichen Abhängigkeiten, manuellen Framework-Pins oder Änderungen an Wrapper, Anwendungscode, Entity-Mappings, Templates, JavaScript, Testfällen oder Assertions. `TestDatabaseGuard`, `GuardedTestDataSource`, ContextCustomizer und Testkonfiguration unverändert.

### Tatsächlich aufgelöste Versionen

Die Runtime-/TestRuntime-Dependency-Berichte enthalten keine unaufgelösten Module. Die Laufzeitbibliotheken wurden zusätzlich im erzeugten `build/libs/demo-0.0.1-SNAPSHOT.jar` kontrolliert; reine BOM-Sollwerte sind damit nicht die Grundlage dieser Tabelle.

| Komponente | Validierte 4.0.8-Baseline | Validierter 4.1.1-Stand |
|---|---|---|
| Spring Boot Plugin / Runtime | 4.0.8 | 4.1.1 |
| Spring Framework | 7.0.9 | 7.0.9 |
| Spring Data JPA | 4.0.7 | 4.1.1 |
| Hibernate ORM | 7.2.24.Final | 7.4.5.Final |
| Jakarta Persistence | 3.2.0 | 3.2.0 |
| MySQL Connector/J | 9.7.0 | 9.7.0 |
| MVC Jackson Databind (`tools.jackson`) | 3.1.5 | 3.1.5 |
| Transitives Jackson-2-Databind | 2.21.5 | 2.21.5; kein MVC-Fallback |
| Thymeleaf / Thymeleaf-Spring6 | 3.1.5.RELEASE | 3.1.5.RELEASE |
| Tomcat | 11.0.24 | 11.0.24 |
| JUnit Jupiter / Platform | 6.0.3 | 6.0.3 |
| Mockito | 5.20.0 | 5.23.0 |
| Effektives Byte Buddy / Test-Agent | 1.17.8 | 1.18.11 |
| Selenium API / Firefox-Modul | 4.37.0 | 4.43.0 |
| Selenium Aggregator / Devtools v120, v121, v122, v85 | 4.18.1 | 4.18.1 |
| jsoup / WebDriverManager | 1.17.2 / 5.7.0 | 1.17.2 / 5.7.0 |

Java 21, Gradle 8.14.3 und Dependency-Management-Plugin 1.1.4 bleiben unverändert. Das JAR bestätigt `Spring-Boot-Version: 4.1.1`, `Build-Jdk-Spec: 21` und Classfile-Version 65 der Anwendung.

### Gestufte Validierung und Datenintegrität

Vor der Änderung wurde die bestehende erfolgreiche 91-Test-Baseline lokal gesichert. Zusätzlich wurden unter 4.0.8 zunächst die 44 datenbankfreien Schutztests und danach der rein lesende Integritäts-Fingerabdrucktest erfolgreich ausgeführt.

| Prüfung unter 4.1.1 | Ergebnis |
|---|---|
| `compileJava compileTestJava --init-script .gradle/boot4-validation/deprecation.init.gradle --warning-mode all` | Erfolgreich; bekannte Temporal-Deprecations bestätigt. |
| `dependencies --configuration runtimeClasspath` und `testRuntimeClasspath` | Erfolgreich; keine `FAILED`-Module. |
| `test --tests de.mangalib.testsupport.TestDatabaseGuardTest` | **44 bestanden**, 0 Fehler. |
| Alle neun geschützten Integrations-/Regressionsklassen gezielt | **47 bestanden**, 0 Fehler, einschließlich 29 MVC-Verträgen. |
| `.\gradlew.bat --no-daemon clean test` | **91 bestanden, 0 Failures, 0 Errors, 0 Skipped**. Stufen nicht addieren. |
| `assemble --dry-run`, anschließend `assemble` | Erfolgreich; Plan nur compileJava, processResources, classes, resolveMainClassName, bootJar, jar, assemble. Keine erneuten Tests, Datenbank- oder Scraping-Aufgaben. |
| Rein lesender Integritäts-Fingerabdruck nach Suite und Build | **1 bestanden**; alle 16 Fingerabdruck-/Zählzeilen identisch zur frischen 4.0.8-Baseline. |

Vorher-/Nachher-SHA-256 über sämtliche Zeilen der zehn Tabellen und die Schema-Metadaten:

```text
ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570
```

Unverändert: **78 Stammdaten** (Status 4, Verlage 62, Typen 4, Formate 3, Sammelbände 5), fünf leere Geschäftstabellen, 10 Tabellen, 75 Spalten, 24 Indexspalteneinträge, 12 Fremdschlüssel, 26 Schlüsselspalteneinträge. Die bereits vorhandenen Vorher-/Nachher-Prüfungen nach tatsächlichem Transaktions-Rollback bestanden ebenfalls. AUTO_INCREMENT-Zähler bleiben bewusst ausgenommen; mögliche Lücken nach Rollback werden nicht zurückgesetzt.

Alle Datenbankprüfungen verwenden ausschließlich den vorhandenen geschützten Spring-Testkontext und EntityManager für `mangalib_test` / `mangalib_test_user`. Keine produktive Verbindung, Migration, Schemaänderung, Dateninitialisierung, Stammdatenänderung oder Scraper-Ausführung. Zugangsdaten ausschließlich aus der bereits vorhandenen Umgebungsvariable, nicht ausgegeben oder neu gespeichert.

Die **29 MVC-/JSON-Vertragsfälle sind in Testmenge und Assertions gegenüber 4.0.8 unverändert** und alle bestanden. Abgesichert bleiben Preise mit Centbeträgen, IDs, Boolean-/Null-/Missing-Werte, ISO-Datumsangaben, Entity-Beziehungen ohne Elternzyklen, Statuscodes, Content-Types und der tatsächliche Jackson-3-MVC-Converter. Das ist ein semantischer Vergleich der vorhandenen Verträge, kein byteweiser Vergleich beliebiger JSON-Antworten. Der bestehende Centverlust der Monatsstatistik wird weiterhin als Baseline geprüft, nicht korrigiert.

Die vollständigen 91-Test-Berichte bleiben unter `build/test-results/test/` und `build/reports/tests/test/index.html` erhalten. Baseline und Zielgraphen liegen lokal Git-ignoriert unter `.gradle/boot41-validation/baseline-4.0.8/` und `target-4.1.1/`. Die zusätzliche Abschlussmessung verwendet unter `post-run/` separate Berichte; ihr ignoriertes Init-Skript ändert ausschließlich Berichtspfade, keine Schutzregeln oder Datenbankkonfiguration.

### Warnungen, offene Grenzen und Freigabe

- Keine nachgewiesene Framework-Inkompatibilität und keine fehlgeschlagene Test-/Build-Stufe. Hibernate 7.4.5.Final akzeptiert die drei bestehenden LocalDate-Temporal-Mappings weiterhin. Die bekannten Compiler-Deprecations sowie `HHH90000033` bleiben; DATE-/Timestamp-Roundtrips und Schemavalidierung bestanden, daher keine Mapping-Änderung.
- Bekannte OSIV-, Mockito-/dynamische-Agent-/Class-Sharing-Warnungen und erwartete Schutz-/HTTP-Negativtest-Warnungen bleiben. Kein neuer erforderlicher API-Umbau festgestellt.
- Der bestehende Selenium-Versionsmix ist jetzt 4.18.1 / 4.43.0. Ohne freigegebenen Browserlauf ist seine Laufzeitkompatibilität **nicht nachgewiesen**; kein vorsorgliches Dependency-Upgrade außerhalb dieses Auftrags.
- Nichttransaktionales Rendering prüft weiterhin leere Geschäftslisten. Befüllte Lazy-Beziehungen, historische Bestandsdaten, exakte Timestamp-Zeitzone/-Präzision, konkurrierende Käufe, HTTP-Atomarität außerhalb der Testtransaktion, Performance und manuelle UI-Abnahme bleiben offen. Die Rechteprüfung auf dem Datenbankserver bleibt administrativ separat.
- **Technisch validierter 4.1.1-Zwischenstand**, keine produktive Freigabe oder Bereitstellung. Der vollständige Git-Diff wurde geprüft; ausschließlich die vier genannten Dateien geändert, `git diff --check` erfolgreich. Kein Git-Commit.
