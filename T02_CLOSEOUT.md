# T02 – Konsolidierte Abschlussmatrix

Stand: 02.11.2026. **T02 technisch abgeschlossen für das freigegebene lokale
Einzelbenutzermodell.**
Der produktive Scraperumfang ist innerhalb der unten genannten Grenzen technisch
validiert. Es sind keine weiteren allgemeinen synthetischen Windows-Experimente
geplant. Die Dependency-Korrektur und ihre erneute geschützte Regression sind
erfolgreich. Die echte 413-UI-/Reload-Prüfung wurde von Codex durchgeführt;
der HTTP-Status war dabei nicht unmittelbar beobachtbar. Die letzte 415-UI-Probe
wurde anschließend vom Projekteigentümer erfolgreich durchgeführt und bestätigt:
HTTP 415 Unsupported Media Type, verständliche Fehlermeldung, kein automatisches
Replay, unveränderter Daten-/Schema-Snapshot, kontrollierter Shutdown, Port frei.
Dies ist ein Eigentümernachweis, keine von Codex ausgeführte 415-Prüfung.
Diese Matrix ist der aktuelle Status und ersetzt widersprechende historische
Formulierungen in den ausführlichen Nachträgen als Planungsgrundlage. Historische
Versuchsresultate werden dadurch weder geändert noch rückwirkend aufgewertet.

## 1. Wesentliche Anforderungen und Nachweise

| Anforderung | Tatsächlicher Stand | Beleg / Grenze |
| --- | --- | --- |
| S01: gespeicherte Daten sicher im DOM ausgeben | Technisch geprüft | Sichere DOM-Methoden, URL-Prüfung, Listener; `home.js`, `security.js`, DOM-Tests. Kein generelles Versprechen für beliebige künftige Templates. |
| Lokaler Betrieb, Host/Origin/CSRF, Session | Technisch geprüft | Loopback, `LocalSecurityConfiguration`, `LocalRequestFilter`, Policy-/MVC-/DOM-Tests und historische Browserabnahme. Kein Login für das freigegebene lokale Einzelbenutzermodell; LAN/Internet nicht freigegeben. |
| Autofill nur POST, GET/HEAD ohne Aktion | Technisch geprüft | Bestehende Security-/MVC-Verträge; Session-CSRF, echte Scraper nur im isolierten Worker, manuelle Umgebung nur mit Doubles. |
| Serverseitige Eingaben, Referenzen und Sortierung | Technisch geprüft | `WriteRequests`/`WriteValidation`, explizite Sortierallowlist; gültige Grenzen, neutrale Fehler, keine SQL-Stringverkettung. |
| NULL-/Altbestandskompatibilität und M06 | Synthetisch/geschützt geprüft | 40 Altbestands- und 34 M06-Integrationsfälle; unveränderte Altwerte, explizite Massenoptionen, fehlende Beziehungen kontrolliert abweisen. Kein behaupteter Test aller produktiven Datensätze. |
| Request-/JSON-/Importgrenzen | Technisch geprüft | Byte-/Strukturgrenzen und 48 Integrationsfälle; vorhandene echte 19-Fälle-HTTP-Abnahme und nachfolgende negative UI-Abnahmen. Keine erneute Ausführung im Abschlussauftrag. |
| Rohe CL+TE-Ablehnung | Bewusste Eigentümerentscheidung | Strikte rohe 400-Ablehnung wurde am 16.10.2026 ausdrücklich zurückgestellt. Tomcat-Normalisierung und begrenzte HTTP-Messung dokumentiert; bei Proxy/LAN neu bewerten. Kein neuer Blocker für heutigen lokalen Betrieb. |
| Beide produktiven Importwege im Worker | Real geprüft | `LIVE_IMPORTS_V1`: Reihe 2502 und Band 15295 jeweils SCOPED_PASS. Keine direkte Selenium-Fallbackroute. |
| Jobzuordnung, Eigentum und regulärer Abschluss | Real geprüft | Atomare native Zuordnung, gehaltene Identitäten, eigene Gruppe vollständig leer, EOF-/Handle-/Ressourcenabschluss. Keine exakte allgemeine Prozessanzahl gefordert. |
| Timeout/Nachfahren und Slotfreigabe | Real geprüft | `BROWSER_TIMEOUT_V1`: Terminate nach 45,007 s, bestätigter Gruppenabschluss, Slot erst danach frei. Ungewisse Abschlüsse bleiben durch bestehende Fake-/Negativfälle quarantänisiert. |
| Redirects, Unterressourcen, WebSocket, Proxy-Ausfall | Endliche reale Matrix geprüft | `BROWSER_BOUNDARY_V1` SCOPED_PASS; Allowlist/DNS-/TLS-Transport im Worker. Keine globale OS-Netzwerkisolierung oder vollständige Prozesshistorie behauptet. |
| Trennung von Produkt-/Test-/Manual-Laufzeit | Technisch geprüft | Produkt-Boot-JAR ohne Selenium/Extraktoren; eigener Worker; Testprofil deaktiviert Live-Client; Manual-JAR ausschließlich synthetisch. |
| Daten- und Schemaintegrität | Geschützt geprüft | 1016 Javafälle, 0 Fehler/übersprungen; zehn Tabellen/78 Stammdaten und Gesamtfingerabdruck identisch. Keine Fixtures verblieben; AUTO_INCREMENT ausgenommen. |
| Fehlermeldungen im Dialog | Innerhalb der dokumentierten Grenzen abgenommen | 206 DOMfälle bestanden. Codex: echte Größenlimit-Meldung, Eingabenerhalt, Sperre bis Reload; Status nicht direkt gemessen. Eigentümer: HTTP 415 direkt beobachtet, erwarteter Text, kein Replay und Daten-/Schema-Snapshot identisch. |
| Dependency-Sicherheitsbefunde | Herstellerabgleich, Korrektur und Regression abgeschlossen | Tomcat core/el/websocket gemeinsam 11.0.27; ungenutztes jsoup/WebDriverManager entfernt, Selenium aus Boot-BOM 4.43.0. [Bewertung und Nachweise](DEPENDENCY_SECURITY_REVIEW.md). Kein vollständiger CVE-Scan oder allgemeines Sicherheitsprädikat. |
| Diagnostik/Logging | Abgegrenzt | HTTP-Fehler und Workerdiagnose begrenzt; umfassende lokale Debug-/Strukturlog-Bereinigung bleibt T06. Keine pauschale Behauptung, alle Altlogs seien bereinigt. |

### Bestätigter letzter DB-Fingerabdruck

`ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570`

1016 Java- und 198 DOMfälle gehören zum bereits abgeschlossenen Regressionlauf.
In dieser Konsolidierung nur 206 DOMfälle erneut ausgeführt (198 bestehende + acht
neue 413-/415-Fälle), keine neue Java-/DB-Testfreigabe abgeleitet. Die zwei neuen
Texte verändern weder Requestpayloads noch serverseitige Geschäftsregeln.

**Späterer Abschlusslauf nach Dependency-Korrektur:** Nach neuer ausdrücklicher
Freigabe nochmals 44 DB-freie Guards, 1016 vollständige Javafälle (einschließlich
29 MVC-/JSON-Verträge), 23 separate Paket-/Policyfälle und 206 DOMfälle bestanden,
jeweils ohne Fehler/übersprungene Fälle. Vorher-/Nachher-Fingerabdruck erneut
identisch einschließlich Schema-Metadaten und 78 Stammdaten; keine Fixtures.
Details und getrennte Berichte im neuesten TESTING.md-Nachtrag.

## 2. Historische Hinweise richtig einordnen

- Aussagen wie „Workerintegration noch nicht implementiert“, „CIM-Registrierung
  erforderlich“, „Importpfad noch synthetisch“ und „Timeoutnachweis fehlt“ sind
  durch spätere Implementierung und reale Nachweise überholt. Sie gelten nur für
  den datierten historischen Stand, nicht als neue aktuelle Sperre.
- Alte PARTIAL/STOP-Versuche bleiben PARTIAL/STOP. Die verbrauchten Grants, Marker
  und Originalartefakte bleiben unverändert. Keine nachträglich erfundene Identität.
- Die historische Sitzung mit nicht rekonstruierbarem Ressourcenabschluss wird
  nicht als sauber beendet ausgegeben. Der Eigentümer hat unabhängig isolierte
  Folgetests ausdrücklich erlaubt; keine zusätzliche pauschale Aufräumaktion.
- „Integration der Requestlimits offen“ ist durch die späteren 48 Integrationsfälle
  und die echte HTTP-Abnahme überholt. Offen ist die abschließende Browser-UX.
- Leere Geschäftstabellen in mangalib_test und synthetische Altbestandsfixtures
  erlauben keine Aussage über jede einzelne historische Produktionszeile. Keine
  produktive Bestandskopie oder automatische Normalisierung daraus ableiten.
- Keine globale Firewall-/OS-Netzwerkgarantie aus Proxymessungen ableiten. Diese
  bekannte Nachweisgrenze wird nicht als Anlass für immer neue Prozessharnesses
  oder einen unangekündigten Architekturwechsel verwendet.

## 3. Historischer Vorbereitungsstand der inzwischen abgeschlossenen Blöcke

Die folgenden Vorbereitungs-/Freigabehinweise dokumentieren den damaligen Stand.
Sie sind keine aktuellen offenen Anforderungen. Maßgeblich sind Abschnitt 1 und
die abschließende Eigentümerbestätigung oben. Alte Artefakte und Ergebnisse
bleiben unverändert; kein historisches PARTIAL/STOP wird zu PASS umgedeutet.

### A. Echte UI-Nachabnahme der Requestlimit-/Replay-Behandlung

**Aktueller Kandidat nach Tomcat-Sicherheitspatch (noch nicht gestartet):**
`build/libs/mangalib-manual-test-t02-closeout-tomcat-11.0.27.jar`, SHA256
`2b4859de35e3437adf2cc59db8028043860c9b7606d40f0d0b3845e64d0d41e2`.
Der nachfolgend beschriebene frühere e89-Kandidat bleibt unverändert erhalten,
enthält jedoch Tomcat 11.0.24. Für die einzige noch erforderliche UI-Abnahme
den gepatchten Kandidaten verwenden, damit keine doppelte Abnahme notwendig wird.
Offline gebaut mit vorhandenem manualBootJar und lokalem Archivnamen-Override.
Alle neun bisherigen manuellen Guard-/Double-/Profileinträge bytegleich,
aktuelle security.js, drei Tomcat-11.0.27-Module, keine Live-/Selenium-/Ownerpakete.
Die bestehende gesonderte Start-/Manual-DB-/Browserfreigabe ist noch einzuholen.

Kleiner Codebefund korrigiert: `MangaLibFeedback` hatte keinen eigenen 413-/415-
Text. Jetzt feste Texte ohne Auswertung fremder Fehlerbodys. 413 bleibt bewusst
bis Reload gesperrt; 415 folgt der vorhandenen kontrollierten Korrektursemantik.
Kein automatisches Replay und keine neue Geschäftsregel.

Ein separat benanntes Artefakt ist gebaut, NICHT gestartet:
- Datei: `build/libs/mangalib-manual-test-t02-closeout.jar`.
- SHA256: `e89e4e8b2947082892ac847ae842793faa63c96f4422d55638508b1c4553fa1f`.
- Statische Prüfung: aktuelle `security.js`, erforderliche Doubles/Guards vorhanden;
  sämtliche manuellen Guard-/Doubleklassen bytegleich zum bisherigen Artefakt;
  keine Live-Scraper/Selenium/Worker-Ownerklassen enthalten.
- Original `build/libs/mangalib-manual-test.jar` bleibt unverändert:
  `9ae1b14c46f874a7c32ddb5bb6bddc3d46559f1f69852fc85aad7e82b904faa9`.
- Reproduzierbarer reiner Offline-Bau: vorhandenes `manualBootJar` wird über einen
  lokalen Gradle-Init-Override nur auf den anderen Archivnamen eingestellt.
  `.gradle/t02-consolidation-20261102/manual-closeout.init.gradle` und Prüfbericht
  sind lokale Buildhilfen, keine neue Anwendungskomponente und kein Commitinhalt.
  Beide Gradle-Aufrufe --offline/--no-daemon, identischer vorheriger Dry-Run.

Erforderliche gesonderte Freigabe: dieses Artefakt über das vorhandene
`Start-ManualTest.ps1 -ArtifactPath ...` zunächst mit `-ValidateOnly` prüfen und
anschließend ausschließlich manual-test / mangalib_manual_test / manuellen Benutzer /
127.0.0.1:18080 starten; Passwort nur aus MANGALIB_MANUAL_DB_PASSWORD. Kein Seeding,
Reset, Bestandswrite oder Live-Scraper. Vorher-/Nachher-Zustand über das bestehende
manuelle Schutzverfahren; kein direkter SQL-Zugriff in dieser Konsolidierung.

Abnahmeumfang ausschließlich negative Requests, deren Ablehnung vor Facharbeit
feststeht, sowie lesende Dialogbedienung. Im bestehenden Browserkontext mit gültigem
CSRF/Origin eine vorab festgelegte überlange JSON-Zeichenfolge über 4096 Zeichen
gegen einen vorhandenen Schreibpfad senden: 413 vor MVC/Service. Keine 10000 Bände,
keine gültige Speicherung. Nur wenn das vorhandene Browserwerkzeug eine solche
kontrollierte Sessionprobe sicher unterstützt; andernfalls Eigentümerabnahme,
keine Browserinstallation oder Sicherheitsumgehung.

Prüfen: eigener verständlicher Text, keine alerts/Exceptiontexte, Eingaben erhalten,
Dialog/Cancel bedienbar, kein Replay/zweiter Request, 413-Sperre auch nach erneutem
Öffnen, bewusste neue Aktion erst nach Reload. 415 mit ungültigem Anfrageformat
nur kontrolliert und ohne Speicherung prüfen. Bei unerwartetem 2xx oder unklarer
Speicherung STOP, keine automatische Bereinigung. Gegebene weitere Restpunkte
(Altbestands-Massenoptionen, NULL-Werte) an eigenen synthetischen Daten lesen;
Schreibabnahmen dafür separat freigeben.

Dieser Plan ist KEINE Start-/DB-/Browserfreigabe. Es wurde weder ValidateOnly des
Launchers (dies startet eine JVM) noch die Anwendung oder ein Browser ausgeführt.

### B. Dokumentierte Dependency-Befunde abschließen

**Aktualisierung 02.11.2026:** Die folgende frühere Beschreibung ist historisch.
Der genehmigte Herstellerabgleich und der gezielte Tomcat-Download wurden inzwischen
durchgeführt. Aktuelle Bewertung: [DEPENDENCY_SECURITY_REVIEW.md](DEPENDENCY_SECURITY_REVIEW.md).
Boot bleibt 4.1.1. Die entfernten Bibliotheken besitzen keine Quellcode-Aufrufpfade;
beide produktiven Extraktoren bleiben bytegleich im separaten Worker. Kein neuer
Live-Scraperstart. Die beiden bisherigen manuellen JARs bleiben unverändert.

Keine Sicherheitsbewertung aus einem erfolgreichen Funktionstest ableiten.
Gezielt die tatsächlich aufgelösten Versionen und betroffenen Nutzungspfade mit
aktuellen offiziellen Advisories abgleichen; ggf. abgestimmte Updates oder
Entfernung nachweislich ungenutzter Bibliotheken vorschlagen. Die bestehenden
P2/P3-Befunde dürfen nicht stillschweigend unter T05/T06 verschwinden.

Für diesen Schritt keine neue Bibliothek installiert/geladen oder externe
Advisoryabfrage durchgeführt. Neue externe Prüfung/Downloads und eine daraus
folgende erneute geschützte DB-Regression benötigen den jeweils abgegrenzten
Auftrag/die Freigabe. Kein ungeprüftes Vulnerability-Free-Prädikat.

## 4. Historische Git-Zurückstellung vor der letzten Eigentümerabnahme

**Kein Abschlusscommit und kein Staging**, da die oben dokumentierten Kriterien
noch nicht vollständig erfüllt/nachgewiesen sind. Die Commitfreigabe war daran
gebunden. Kein Push, Pull, Merge, Rebase, Tag oder Branchwechsel.

Anfangsbestand: 196 Dateien (23 geändert, 173 neu), Index leer. Darunter der
produktive Pfad und zahlreiche historische experimentelle Source-Sets. Sie werden
nicht pauschal gestagt oder als lokale Logs/Evidenzen übernommen. Keine automatische
Löschung vermeintlich alter Infrastruktur; Bereinigung bleibt T05.

`SECURITY_AUDIT.md`, .env-Dateien, .gradle-Evidenzen und build-Artefakte sind ignoriert;
Sicherheitsbericht nicht verfolgt. Bekannte versionierte SQL-Skripte sind unveränderte
Bestandsdateien und keine neuen Commitkandidaten. Begrenzte lokale Musterprüfung
aller zu Beginn geänderten/neuen Textdateien fand keine Secret-Muster; Gitleaks
ist nicht verfügbar. Dies ist kein vollständiger Secret-/Historien-Scan.

Vor späterem Commit: erneuter vollständiger Status-/Diff-/Untracked-Review,
public-taugliche Dokumentations-/Pfadhygiene, eindeutige Dateiauswahl und
`git diff --check`. Eingebettete historische Diagnoseabschnitte sind vor Staging
auf private lokale Details zu prüfen; kein ungeprüftes Gesamtstaging der langen
Nachträge. Die vertraulichen Originale und ignorierten Evidenzen bleiben lokal.

T03 ist ausschließlich [vorbereitet](T03_IMPLEMENTATION_PLAN.md), nicht begonnen.

## 5. Endgültige Abschlussentscheidung vom 02.11.2026

Beide Abschlussblöcke sind erledigt. Kein neuer blockierender T02-Widerspruch in
der statischen Abschlussprüfung. Lokaler Abschlusscommit ausdrücklich freigegeben;
kein Push, Merge, Rebase, Tag, Branchwechsel oder Produktivstart.

Gesicherter Prüfstand: 1016 Javafälle einschließlich 29 MVC-/JSON-Verträge und
206 DOMfälle erfolgreich; 44 vorgeschaltete Guard- und 23 separate Paket-/Policy-
Fälle bestanden. Keine erneuten Tests/Builds/DB-/Browser-/Windowsausführungen
im Abschlussauftrag. Historische Originale und Manual-JAR unverändert.

Vertretbare Grenzen des Abschlusses: kein Internet-/LAN-Betrieb, keine globale
OS-Netzwerkisolierung oder vollständige Prozessprovenienz, keine allgemeine
Schwachstellenfreiheit und keine Vollprüfung jeder produktiven Altbestandszeile.
Rohe CL+TE-Ablehnung bleibt gemäß Eigentümerentscheidung zurückgestellt; Logging-
Bereinigung T06 und spätere LAN-Anforderungen bleiben getrennt. Kein produktiver
Deployment-/Release-Nachweis aus diesem technischen Abschluss ableiten.

Commitumfang: produktive Implementierung, vorhandene Gradle-Source-Sets mit
ihren reproduzierbaren Fake-/Regressions-/Abnahmequellen, zugehörige Skripte und
Dokumentation einschließlich der reinen T03-Vorbereitung. Die acht historischen,
nicht an Gradle oder Produkt angebundenen Dateien unter
`experiments/windows-job-prototype/` bleiben bewusst lokal und unversioniert;
keine Löschung. Lokale Evidenzen, Grants/Marker, JARs, Builds, Snapshots,
SECURITY_AUDIT.md und Secrets werden nicht aufgenommen.

Vier Hilfsquellen enthielten einen fest ausgeschriebenen privaten Benutzerpfad
zum bereits hashgepinnten lokalen JNA-JAR. Für die öffentliche Dateiauswahl wird
nur der Benutzerprofilanteil abgeleitet; fester Cache-Unterpfad und Hash-/Manifest-
prüfungen bleiben unverändert. Auf diesem Rechner identisches Ziel statisch
bestätigt, PowerShell nur geparst, keine neue Laufzeitprüfung behauptet. Originale
dieser Hilfsquellen lokal ignoriert gesichert; historische Ausführungsartefakte
und Evidenzen unverändert. Kein produktiver Scraper-/Datenpfad geändert.

Nächster Auftrag: T03-0 gemäß bestehendem Plan, zuerst datenbankfreie
Charakterisierung der Kauf-/Fehler-/Wiederholungsverträge und explizite
Fachentscheidungen zu Idempotenz, Mehrdeutigkeit und Bandanzahlreduktion.
Keine T03-Implementierung oder Datenoperation in diesem Abschluss.
