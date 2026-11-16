# S03_001 – Noch nicht ausführbarer Migrationsentwurf

Die manuelle Nachprüfung zeigte am 16.11.2026 fehlende SELECT-Rechte auf der
neuen Tabelle (1142). Der tatsächliche Laufzeitaccount wurde separat als
mangalib_manual_test_user@127.0.0.1 bestätigt. Nach Tabellenanlage braucht es
deshalb den separaten Admin-Schritt 03_manual_runtime_grants.sql. Dieser ergänzt
nur SELECT/INSERT/UPDATE auf der neuen manuellen Tabelle; keine DDL-Rechte,
keine schemaweiten Rechte und keine Veränderung von Daten. Noch nicht ausgeführt.

## Konkreter Testkandidat, 16.11.2026

Die lesende Vorprüfung vom 15.11.2026 bestätigte MySQL 8.0.36 und signed BIGINT.
01_mangalib_test.sql ist daraus als vollständig auf mangalib_test qualifiziertes
DDL vorbereitet. Noch keine Ausführungsgenehmigung und keine Migration erfolgt.
SHA-256 und Erhaltungsreferenzen stehen in S03_ACCEPTANCE.md.
Nur diese konkrete reine DDL-Datei ist zusätzlich in .gitignore freigegeben;
alle privaten SQL-Exporte bleiben ausgeschlossen.

initial_completion_date erhält den ursprünglichen Request für idempotente
Wiederholung trotz Datumskorrektur; revision schützt parallele Korrekturen.
volume_factor speichert den beim Abschluss bekannten Sammelbandfaktor, ohne
historische Metadaten nachträglich zu erfinden. Ein unbekannter Faktor bleibt
in der Statistik sichtbar. Keine neue JPA-Entity und keine automatische DDL:
der optionale native Store prüft beim expliziten Aktivieren das Schema.

Das konkrete Test-DDL enthält absichtlich keinen Reread-Reset. Vorhandene
positive synthetische Testzähler bleiben erhalten. Die Rollbackfixtures der
Abnahme starten eigenständig mit 0. Die restlichen Vorlage-/Aktivierungshinweise
unten sind Entwurfsstand und werden durch S03_ACCEPTANCE.md konkretisiert.

Die .sql.template-Dateien sind nicht an Spring, Gradle, Flyway oder Liquibase
angebunden. Nicht gegen eine Datenbank ausführen. __APPROVED_SCHEMA__ und
__VERIFIED_BAND_ID_TYPE__ sind absichtliche Platzhalter. Schema, ID-Typ,
Serverversion und Zeichensatz müssen vor konkreten Zieldateien geprüft werden.
Keine automatische Ersetzung aus Umgebungsvariablen oder Verbindungseinstellungen.

01 beschreibt die additive Struktur. 02 beschreibt die am 15.11.2026 vom
Eigentümer gewünschte einmalige Reread-Initialisierung auf 0, einschließlich
vorheriger NULL- oder positiver Zähler, in baende und mangadetails. Andere
Felder bleiben erhalten. Dies ist eine fachliche Datenänderung, keine technische
Bereinigung und kein Default für spätere Speicheroperationen.

Beide Schritte benötigen konkrete Zieldateien, getrennte SHA-256-Bindung,
Vorher-Snapshot/Backup und eine gesonderte Ausführungsfreigabe. Für die
Produktion bleibt ein getesteter Wiederherstellungsweg zwingend. Auf vorhandenen
Test-/Manual-Daten dürfen positive synthetische Rereads nicht nebenbei gelöscht
werden. Die Migrationsprobe benötigt ausdrücklich dafür bestimmte Fixtures.

MySQL-DDL und DML sind getrennt: CREATE TABLE ist nicht Teil eines verlässlichen
Rollbackplans. Nach 01 Struktur und unveränderte Altbestände prüfen, dann erst
02 unter einer eigenen Transaktion. Bei Abweichung STOP, kein automatisches
Cleanup, kein DROP, keine automatische Wiederholung. 02 ist nur bei leerer
neuer Historie und unter exklusivem Wartungsbetrieb zulässig. Erwartete
Zeilenzahlen vorab erfassen und vor COMMIT abgleichen; das Template führt
absichtlich kein COMMIT aus. Clientsitzung bei Fehlern explizit zurückrollen.

Nach Initialisierung gilt: Bekannte Zähler enthalten bereits die neuen
Reread-Ereignisse. Monatsstatistiken verwenden nur datierte Ereignisse und
addieren diese nicht noch einmal zum Gesamtzähler. Erstlektüren und Rereads
erhalten getrennte Ereignisarten. Datumskorrekturen ändern keinen Zähler;
Stornierung wird zunächst nicht angeboten. Mehrere bewusste Aktionen am selben
Tag sind zulässig, Wiederholung derselben Aktion nicht.

Vor tatsächlicher Umsetzung: Testsnapshots um neue Ereigniszeilen erweitern,
FK-Typ/Indexes prüfen, transaktionalen Schreibpfad und expliziten Löschworkflow
fertigstellen. Entity/Repository nicht vorher global aktivieren: Das bestehende
ddl-auto=validate würde sonst auf den unmigrierten Schemas den Start verhindern.
Keine Ausweitung von DDL-Rechten bestehender Laufzeitbenutzer.
