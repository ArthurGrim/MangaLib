# MangaLib – Migrationsplan für Spring Boot 4.1

Stand: **10.10.2026**. Ausgangspunkt: **Spring Boot 3.5.16**. Recherchiertes Ziel: **4.1.1**.

Dies ist eine statische Analyse und ein Umsetzungsplan, keine durchgeführte Migration. In diesem Auftrag wurden nur diese Datei und die ROADMAP.md bearbeitet. Keine Builds, Tests, Anwendungsstarts, Datenbankverbindungen, Schemaänderungen oder Scraper wurden ausgeführt. Die vorhandenen Testberichte wurden ausschließlich gelesen. AGENTS.md, ROADMAP.md und TESTING.md wurden vollständig berücksichtigt.

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

`TESTING.md` enthält noch die historische Aussage aus dem damaligen Implementierungsauftrag, es seien keine Tests ausgeführt worden. Der aktuelle Laufstatus ist hier und in ROADMAP.md dokumentiert; die Sicherheitsregeln aus TESTING.md bleiben verbindlich. Eine sprachliche Aktualisierung dieser dritten Datei ist nicht Teil dieses Auftrags.

## 6. Zusätzliche Tests mit Prioritäten und Akzeptanzkriterien

Alle fachlichen Tests zunächst gegen **3.5.16** etablieren und danach unverändert gegen den Zielstack ausführen. Nur neue Testdatensätze verwenden; bestehende Stammdaten nicht ändern. Die genannten neuen Testdateien sind Vorschläge, noch nicht angelegt.

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
