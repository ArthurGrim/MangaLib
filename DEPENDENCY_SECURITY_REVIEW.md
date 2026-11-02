# T02 – Dependency-Abschlussprüfung, 02.11.2026

## Umfang und Entscheidung

Gezielte Prüfung der dokumentierten Tomcat-/jsoup-/Selenium-Befunde, kein
vollständiger CVE-Scan aller transitiven Bibliotheken. Keine Exploitversuche.
Spring Boot bleibt 4.1.1, Java 21 und Gradle 8.14.3 bleiben unverändert.

| Komponente | Tatsächlicher Ausgangsstand | Maßnahme |
|---|---|---|
| Tomcat embed core/el/websocket | 11.0.24 | Gemeinsamer `tomcat.version`-Override auf 11.0.27; kein gemischter Patchstand |
| jsoup | 1.17.2, keine Aufrufe/Imports im Quellcode | Ungenutzte direkte Abhängigkeit entfernen |
| WebDriverManager | 5.7.0, keine Aufrufe/Imports | Entfernen; explizite lokale Firefox-/Geckodriverpfade bleiben verbindlich |
| Selenium | Aggregator 4.18.1 mit alten DevTools-Modulen; tatsächlich genutzter Firefox-/Remote-/Supportkern bereits 4.43.0 | Nur Firefox-Driver und Support aus der vorhandenen Boot-BOM (4.43.0), ohne Aggregator/Chrome-/Edge-Module |
| JNA im produktiven Owner | 5.17.0 | Unverändert; keine native Ausführung in dieser Prüfung |

## Herstellerabgleich und lokale Erreichbarkeit

Die [Tomcat-Advisories](https://tomcat.apache.org/security-11.html) enthalten
Korrekturen in 11.0.25 bis 11.0.27 für den vorherigen Stand. Betroffen sind unter
anderem HTTP/2, AJP, WebSocket, Container-Authentifizierung, TLS und HTTP/1.0.
Der Patch beseitigt die Versionsbetroffenheit der dort für diese Patchstände
aufgeführten Befunde; eine erfolgreiche Funktionsprüfung ersetzt diesen Abgleich nicht.

Lokaler Codebefund: Keine AJP-/WebSocket-Endpunkte, Realms, FORM-/DIGEST-
Containeranmeldung, RewriteValve, Jasper-/JSP- oder Examples-Webapp konfiguriert.
`LocalSecurityConfiguration` erzwingt IPv4-Loopback, HTTP ohne TLS und keine
Forwarded-Header-Verarbeitung. HTTP/2 ist nicht eingeschaltet
([offizieller Default: false](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)).
Diese Pfade sind im dokumentierten Betriebsmodell nicht exponiert; beliebige
zukünftige externe Konfigurationen sind damit nicht geprüft. Der HTTP/1-Parser
bleibt tatsächlich im Einsatz. Deshalb Update statt pauschaler Entwarnung.
Insbesondere CVE-2026-77756 betrifft HTTP/1.0-Transfer-Encoding; der beschriebene
Reverse-Proxy-Kontext ist hier nicht eingerichtet.

Der [Changelog](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) dokumentiert
auch Änderungen der HTTP-Verarbeitung. Requestlimit-, Statuscode- und
MVC-Verträge müssen daher erneut regressionsgeprüft werden. Der gemeinsame
Override ist ein [offiziell unterstützter Mechanismus](https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html),
jedoch keine automatische Kompatibilitätsgarantie für Boot 4.1.1.

[jsoup GHSA-pmhh-3w7g-xqp8 / CVE-2026-71497](https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8)
betrifft 1.14.3 bis 1.22.2 bei bestimmten benutzerdefinierten Cleaner-Safelists;
1.23.1 ist korrigiert. Solche Nutzung ist in MangaLib nicht vorhanden. Damit ist
keine ausnutzbare jsoup-XSS in MangaLib nachgewiesen. Entfernen ist hier kleiner
und sicherer als das Aktualisieren einer unbenutzten Bibliothek.

`ScraperBrowser.open` verwendet weiterhin explizite Binär-/Servicepfade
([Selenium-Service-Dokumentation](https://www.selenium.dev/documentation/webdriver/drivers/)).
Die Entfernung des Downloadmanagers ändert diesen Pfad nicht. Allgemeine
DevTools-Hilfsklassen des Remote-Drivers bleiben enthalten; entfernt sind nur
ungenutzte Browser-/versionierte DevTools-Artefakte. Keine Behauptung vollständiger
Schwachstellenfreiheit aus einer Versionsbereinigung.

## Liefer- und Nachweisgrenzen

Der Eigentümer hat ausschließlich drei Tomcat-11.0.27-Module samt erforderlichen
Metadaten von `repo.maven.apache.org` zum Download freigegeben. Der reine
Nachladetask ist eine dokumentierte Ausnahme vom Offline-Modus; Kompilierung,
Paketprüfungen und Regression laufen danach offline mit vorherigem Taskplan.
Keine Installer und kein Boot-/Java-/Gradle-Upgrade.

Historische Live-/Boundary-/Timeout-Nachweise bleiben gültige historische
SCOPED_PASS-Ergebnisse ihres damaligen Artefaktstands. Sie werden nicht als neu
ausgeführt dargestellt. Die Entfernung ungenutzter Pakete ist keine neue reale
Firefox-Abnahme. Der Vergleich vor dem Tomcat-Patch zeigte 6784 erhaltene
Worker-Klassen bytegleich, keine geänderte oder neue Klasse.

Die eingefrorenen manuellen JARs und historischen Evidenzen bleiben erhalten.
Eine echte UI-Abnahme nach dem Patch soll ein separat benanntes, neu hashgebundenes
Artefakt verwenden; der ursprüngliche Kandidat wird nicht überschrieben.
Start, manuelle DB und Browser bedürfen weiterhin gesonderter Freigabe.

## Tatsächlich ausgeführte Prüfungen

- Offline-Kompilierung einschließlich Testquellen erfolgreich.
- 20 produktive Scraper-/Pakettests und drei Zielpolicytests erfolgreich.
  Paketassertions prüfen jetzt die einheitlichen drei Tomcat-11.0.27-JARs und
  die Abwesenheit ungenutzter Bibliotheken/Browsermodule.
- 44 datenbankfreie Guards erfolgreich, vor jedem DB-Lauf wirksam.
- Nach gesonderter Eigentümerfreigabe vollständige geschützte Suite:
  1016 bestanden, 0 Fehler, 0 übersprungen; alle 29 MVC-/JSON-Verträge bestanden.
- 206 DOM-Fälle bestanden, 0 Fehler/übersprungen/cancelled/todo.
- Alle Gradle-Ausführungen mit identischem vorherigem Dry-Run; nur der gezielte
  Downloadtask war online, alle Compile-/Build-/Testläufe offline/no-daemon.
- Kein `clean`: eingefrorene Originale bewusst nicht gelöscht. Die vollständige
  Testsuite lief ungefiltert mit `--rerun-tasks`, Assertions unverändert.

Aus dem tatsächlich gebauten Boot-JAR außerdem bestätigt: Spring Data JPA 4.1.1,
Hibernate 7.4.5.Final, MySQL Connector/J 9.7.0, Thymeleaf 3.1.5.RELEASE,
Jackson core/databind 3.1.5 und annotations 2.21. Diese Versionen wurden nicht
aktualisiert. Dies ist keine zusätzliche vollständige Advisoryprüfung dieser
unveränderten Komponenten.

Vorher-/Nachher-Fingerabdruck identisch:
`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`.
Zehn Tabellen, 78 Stammdatensätze, fünf leere Geschäftstabellen; keine Fixtures.
Schema-Metadaten (75 Spalten, 24 Indexeinträge, 12 Fremdschlüssel,
26 Schlüsselspalten) unverändert, AUTO_INCREMENT ausgenommen.

Separater gepatchter manueller Kandidat offline gebaut und ZIP-statisch geprüft:
`build/libs/mangalib-manual-test-t02-closeout-tomcat-11.0.27.jar`, SHA256
`2b4859de35e3437adf2cc59db8028043860c9b7606d40f0d0b3845e64d0d41e2`.
Alle neun bisherigen manuellen Guard-/Double-/Profileinträge bytegleich,
Feedbackdatei aktuell, keine Live-/Selenium-/Ownerpakete. Nicht gestartet.
Die eingefrorenen ursprünglichen manuellen Artefakte sind unverändert.

Abschließende Einordnung 02.11.2026: Die damalige Angabe „nicht gestartet“ ist
historischer Paketprüfstand. Danach erfolgte die freigegebene 413-UI-Probe durch
Codex und die vom Projekteigentümer bestätigte HTTP-415-Probe mit verständlichem
Text, ohne Replay, Daten-/Schemaänderung oder verbliebener Instanz. Damit kein
offener Dependency-/UI-Abschlussblock mehr; Grenzen gemäß T02_CLOSEOUT.md.
