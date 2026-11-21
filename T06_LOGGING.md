# T06 – Lokale Diagnose

Die Diagnose verwendet die vorhandenen SLF4J-/Logback-Abhängigkeiten. Kein neues
Audit-Datenmodell, keine Datenmigration und keine Änderung von Kauf-/Leseregeln.

## Ereignisse und Datenschutz

- RequestDiagnosticsFilter erzeugt für jede synchrone Anfrage eine neue UUID und
  liefert sie als `X-MangaLib-Request-ID`. Eingehende IDs werden nicht übernommen.
  MDC ist nur während der Bearbeitung gesetzt; der vorherige Threadkontext wird
  auch bei Ausnahmen wiederhergestellt. Aktuelle Controller sind synchron.
- Requestabschluss: DEBUG für erfolgreiche Antworten, WARN bei 4xx, ERROR bei
  5xx oder ungefangener Exception. `responseStatusAtFilterEnd` ist der am Filterende
  sichtbare Status, kein behaupteter späterer Containerstatus oder Commitnachweis.
- Behandelte unerwartete Controllerfehler: ERROR mit bereinigtem technischem
  Stack. Bestehende neutrale HTTP-Bodys und Fehlercodes bleiben bestehen.
- Importabschluss: INFO mit Importart und Dauer nach erfolgreicher Ownerprüfung;
  Abbruch: WARN ohne Index, URL oder Nutzdaten. Keine Wiederholung/Fallbacks.
  Der Requestkontext gilt auch an der synchronen Gatewaygrenze. Das bestehende
  Workerprotokoll und die separaten nativen Journale werden nicht verändert;
  keine neue durchgängige MDC-Übertragung in fremde JVMs behauptet.
- Anwendungsbereitschaft und Shutdown-Anforderung: INFO. Letztere ist ausdrücklich
  kein Nachweis eines abgeschlossenen Worker-/Job-Cleanups.
- Kein System.out-/printStackTrace-Rohdatenlogging in Anwendungsklassen; Browser-
  console.log-Ausgaben entfernt, verbleibende Fehlerausgaben auf feste Texte begrenzt.

DiagnosticEncoder übernimmt ausschließlich feste eigene Ereignisfelder. Fremde
Frameworkmeldungen werden durch Loggername/Level und ein neutrales Ereignis ersetzt.
Exceptionmeldungen, Ursachen und suppressed Exceptions werden nicht ausgegeben;
höchstens 32 technische Frames ohne Quelldateipfade. Dadurch gehen absichtlich
Fehlerdetails verloren, die SQL, URLs, Credentials oder importierte Werte enthalten
könnten. Keine Geheimnisdiagnose durch erneutes Aktivieren von Payload-Logging.
Jeder Datensatz ist auf weniger als 16 KiB begrenzt. Kein Querystring, Requestbody,
Cookie, CSRF-/Sessiontoken oder vollständiger Manga-Datensatz gehört in diese Logs.

## Dateilogging

Produktiver Standardpfad: `${user.home}/.mangalib/logs/mangalib.log`.
Nur ein ausschließlich für Diagnosen vorgesehenes Verzeichnis konfigurieren,
niemals ein vorhandenes Projekt-, Benutzerwurzel-, Backup- oder Journalverzeichnis.
PrivateRollingFileAppender beschränkt dieses Verzeichnis auf seinen Eigentümer
(Windows-ACL beziehungsweise POSIX-Rechte). Symlinks am Ziel werden abgewiesen.
Vorhandene aktive Logdateien werden ebenfalls beschränkt. Archive erben unter
Windows die privaten Verzeichnisrechte. Unzureichende Rechte verhindern den
Dateiappender, nicht die Fachoperation; die Konsole bleibt aktiv. Fehler der eigenen
Vorprüfung werden als Warnung gemeldet. Ungültige Logback-Konfiguration oder ein
nachfolgender Framework-Initialisierungsfehler können weiterhin den Start kontrolliert
verhindern; eine generelle Startgarantie bei beliebigen IO-/Konfigurationsfehlern
wird nicht behauptet.

| Spring-Property | Standard | Bedeutung |
|---|---|---|
| mangalib.diagnostics.directory | Benutzerprofil/.mangalib/logs | Eigener lokaler Diagnoseordner |
| mangalib.diagnostics.file-size | 10MB | Größen-Rotationsschwelle, zusätzlich tägliche Rotation |
| mangalib.diagnostics.archive-size | 90MB | Budget der archivierten Dateien |
| mangalib.diagnostics.history-days | 14 | Tägliche Archivperioden |
| mangalib.diagnostics.total-budget-bytes | 104857600 | Zusätzliches hartes Schreibbudget des Ordners |

Logback räumt Archive beim Start und bei Rotation auf. Die Größen-Rotationsschwelle
kann um einen begrenzten Datensatz überschritten werden; sie ist keine bytegenaue
Dateiquote. Vor jeder Ausgabe prüft der Dateiappender zusätzlich die gesamten
regulären Dateien im Diagnoseordner mit 16-KiB-Reserve. Bei ausgeschöpftem Budget
oder Prüffehler stoppt er die Dateiausgabe konservativ, ohne fremde Dateien zu
löschen oder Fachaktionen abzubrechen. Konsole bleibt verfügbar; Wiederaufnahme
des Dateiappenders erst nach geprüfter Ursache und regulärem Neustart.
Das Budget gilt für den vorgesehenen einzelnen Schreiber; parallele Anwendungen
mit demselben Logpfad werden nicht als unterstütztes Betriebsmodell behauptet.
Rotation unter einem temporären Pfad getestet; echte Datenträgerfüllung nicht
provoziert. Übergreifendes Betriebskonto/Startverzeichnis bleibt T07.

Die Profile `test` und `manual-test` aktivieren ausschließlich Konsolenlogging.
Guards werden nicht geändert; historische Manual-JARs/Journale bleiben unverändert.
Konfiguration über reguläre externe Einstellungen im produktiven Betrieb; keine
Ausnahme von den strengen Test-/Manual-Overrideprüfungen. Rootstandard INFO,
gezielte Level wie `logging.level.de.mangalib.diagnostics=DEBUG` bleiben möglich.

## Nachweise

Tests: Request-ID/Isolation/MDC-Cleanup, bereinigte Encoder-Ausgabe, begrenzte
Stackgröße, echte Größenrotation im temporären Verzeichnis, Windows-Eigentümer-ACL,
unbrauchbares Logziel, Budgeterschöpfung ohne Löschung sowie beide Importwege mit
Mocks und ohne Wiederholung bei Fehlern. Bestehende DOM- und Vertragsprüfungen
bleiben erhalten. Tatsächliche Laufzahlen und Abschlussstatus siehe TESTING.md.
Keine produktive Anwendung, Migration, native Importabnahme oder Browseraktion
ist erforderlich, um diese Implementierung zu paketieren. Reale Betriebsabnahme,
Logpfad-/Backup-/Autostartkonzept und Diagnose bei Bootfehlern folgen in T07.

Die getrennt gebauten Worker-/Anwendungspakete sind neue Artefakte. Bei einer
späteren Bereitstellung müssen ihre expliziten Pfad-/SHA-256-Pins kontrolliert
aktualisiert werden; keine automatische Pin-Neuberechnung oder Lockerung der
Startguards. Historische native SCOPED_PASS-Nachweise werden nicht rückwirkend
auf neue Hashes umgeschrieben.
