# S03 Schritt 2 – Additiver, versionierter Migrationsplan

## Konkretisierung, 16.11.2026

Die Vorprüfung wurde am 15.11.2026 ausschließlich lesend auf mangalib_test
ausgeführt. Signed BIGINT und MySQL 8.0.36 bestätigt. Ein konkretes, noch nicht
ausgeführtes CREATE TABLE liegt in 01_mangalib_test.sql. Originaldaten und
Originalschema besitzen getrennte Erhaltungshashes. Der optionale Store ist
implementiert; die Standardaktivierung bleibt false. S03_ACCEPTANCE.md nennt
die konkrete nächste Freigabe und die getrennten BEFORE-/AFTER-Prüfungen.
Die folgenden Entwurfsabschnitte sind historische Vorbereitungsstände.

Stand 15.11.2026. Dies ist ein Entwurf zur Umsetzung und Abnahme, kein
ausführbares SQL und keine Freigabe für irgendeine Datenbankänderung.
Bestehende T02-/T03-Guards und das Manual-Artefakt bleiben unverändert.

## Eigentümerentscheidung und Umsetzungsnachtrag, 15.11.2026

Der Eigentümer bestätigt, bisher keine Rereads zu haben, und wünscht die
einmalige Initialisierung aller Reread-Zähler auf 0. Das umfasst im geplanten
Zielschema baende.reread und mangadetails.reread, auch bisherige NULL-/positive
Werte. Dies ersetzt den unten noch historisch offenen NULL-Entscheidungspunkt.
Die Initialisierung ist ein expliziter Migrationsschritt, keine automatische
Speicherregel. Keine Datenbankänderung wurde ausgeführt; vorhandene synthetische
Testhistorien werden nicht ohne eigene Freigabe zurückgesetzt.

Ebenfalls bestätigt: Mehrere bewusste Abschlüsse am selben Tag sind zulässig;
dieselbe Aktion zählt nur einmal. Datumskorrekturen verändern keinen Zähler.
Stornierung bleibt vorerst gesperrt. Monatliche Abschlüsse werden getrennt
nach Erstlektüre/Reread gezählt, unabhängig von technischen Zeitstempeln.

ReadingHistoryStatistics implementiert diese reine Monatsberechnung mit
kalendertreuer Zuordnung und Konflikterkennung. Die Klasse ist noch an keinen
Controller, Spring-Service oder Datenbankzugriff angebunden. Neun neue
Unitfälle und 106 bestehende ausgewählte Regressionen bestanden (115/115).

Entwürfe unter database/migrations/S03_001_reading_events sind .sql.template
Dateien mit absichtlich unaufgelösten Platzhaltern, nicht automatisch geladen.
Sie sind kein freigegebener Migrationskandidat. Struktur und einmaliger
Zählerreset sind getrennt, MySQL-DDL besitzt keinen normalen DML-Rollback.
Vor konkreten Skripten tatsächlichen ID-Typ, Serverversion und Schema prüfen,
danach Zielskripte/hashgebundene Freigabe sowie Integration vorbereiten.
Die nachstehenden Modell- und Ablaufempfehlungen gelten mit diesen Ergänzungen.

## Modell und Kompatibilität

Empfohlen wird eine zusätzliche Tabelle `band_reading_events`. Ein einzelnes
`readAt` am Band würde durch einen Reread die frühere Monatszuordnung verlieren.
Vorhandene Band-IDs, Beziehungen, `ist_gelesen`, nullable `reread`, Preise und
technische Datumsfelder bleiben vollständig unverändert. Die neue Tabelle ist
nach der Migration leer. Kein Backfill aus technischen Zeitstempeln.

Vorgeschlagene Spalten, vor Erstellung des SQL gegen tatsächliche isolierte
Schema-Metadaten zu prüfen:

| Feld | Vorgeschlagener Vertrag |
|---|---|
| id | BIGINT, Primärschlüssel, generiert |
| band_id | NOT NULL, exakt derselbe SQL-Typ wie baende.id, FK RESTRICT |
| completion_date | DATE NOT NULL, fachlicher Kalendertag, keine UTC-Verschiebung |
| kind | begrenzter String FIRST_READ oder REREAD, serverseitig validiert |
| operation_id | eindeutige UUID pro Benutzeraktion, unverändert bei Wiederholung |
| created_at | technischer UTC-Erfassungszeitpunkt; kein Ersatz für completion_date |

Unique-Index auf (operation_id, band_id): eine bestätigte Sammelaktion kann
mehrere Bände enthalten, pro Band jedoch nur ein Ereignis. Gleicher Tag ist
kein geeigneter eindeutiger Schlüssel: Er würde eine bewusste zweite Lektüre
und eine technische Wiederholung gleichsetzen. Derselbe Schlüssel mit anderem
Payload muss als Konflikt abgewiesen werden. Monats-/Bandabfragen erhalten
Indizes (completion_date, kind) und (band_id, completion_date, id).
Keine Löschkaskade, die beim Löschen einer Reihe unbemerkt Historie entfernt.
Vor Aktivierung muss der bestehende explizite Löschworkflow diese Beziehung
berücksichtigen; bis dahin keine produktive Aktivierung des neuen Modells.

## Zähler und historische Daten – Entscheidung vor Implementierung

Neue Ereignisse dürfen nicht zusätzlich zu einem bereits fortgeschriebenen
Reread-Zähler auf die Gesamtlektüren addiert werden. Empfehlung für die erste
Ausbaustufe: bestehende Zähler bleiben die kompatible Gesamtsicht; Ereignisse
bilden nur den datierbaren Anteil ab. Eine neue Reread-Aktion schreibt Zähler
und Ereignis gemeinsam unter LibraryWriteCoordinator, genau einmal. Eine
rückwirkende Datierung einer bereits gezählten Lektüre erhöht den Zähler nicht.
Dieser Unterschied braucht eine ausdrückliche UI-Aktion und Validierung.

Offen zu bestätigen sind Korrektur/Stornierung, nachträgliche Zähleränderungen,
NULL-Zähler beim ersten neuen Reread und mehrere Abschlüsse am gleichen Tag.
Keine implizite Entscheidung im Schema: Vorläufig kein automatischer Trigger,
kein DB-Default, der historische Zähler auf 0 setzt, und kein Backfill.
Falls dauerhaft unveränderliche Ereignisse oder Lösch-Tombstones gewünscht
sind, deren Modell vor dem SQL festlegen statt später still zu ergänzen.

## Versionierung und Ablauf

Keine automatische Schemaerzeugung und kein neuer Migrations-Runner im
Anwendungsstart. Die vorhandenen Guards verbieten dort DDL zu Recht.
Empfohlener späterer Dateisatz außerhalb der automatisch geladenen Ressourcen:
`database/migrations/S03_001_reading_events/` mit README, getrenntem festem
Skript pro freigegebenem Ziel, SHA-256-Dateiliste und Verifikationsanleitung.
Diese Dateien werden erst nach Modellbestätigung und Typprüfung erstellt.

1. Vorprüfung: Serverversion, exakte DB-/Benutzeridentität, baende.id-Typ,
   InnoDB/FK-Eignung, vorhandene Constraints und keine gleichnamige neue Tabelle.
   Bei jeder Abweichung STOP. Kein IF NOT EXISTS, das Abweichungen verbirgt.
2. Einrichtungsbenutzer getrennt vom DML-Laufzeitbenutzer einsetzen. Keine
   DDL-Rechte an mangalib_test_user oder mangalib_manual_test_user vergeben.
3. Vorher-Snapshot aller alten Tabellen einschließlich NULL-Werten, IDs,
   Preisen, Lesestatus, Zählern, Beziehungen und Schema-Metadaten sichern.
4. Zuerst auf separat freigegebenem isoliertem Schema mit synthetischen
   Altbestandsfixtures prüfen. Vorhandene zehn Tabellen unverändert erhalten.
5. Ausschließlich die neue Tabelle und ihre Indizes/FK anlegen. Keine UPDATE-,
   DELETE-, ALTER- oder INSERT-Operation an bestehenden Geschäftstabellen.
6. Nachprüfung: neue Tabelle leer, ihre Struktur exakt, alle alten Daten- und
   Schemasnapshots identisch. Gesamtschemafingerabdruck ändert sich erwartbar;
   alte Referenzen nicht ersetzen oder nachträglich umdeuten.
7. Neue Guards/Snapshottests vor einem Anwendungslauf bewusst um die neue
   Struktur erweitern. Historische Zehn-Tabellen-Nachweise bleiben historisch
   erhalten. Test- und Manual-Schema getrennt und ausdrücklich freigeben.
8. Verifizierten Ausführungsbeleg mit Versionskennung und Hash aufbewahren.
   Wiederholung der Migration ist ein Fehler, kein automatischer Reparaturweg.

## Produktion und Wiederherstellung

Keine Produktionsverbindung in dieser Phase. Vor einer späteren Migration:
bestätigtes Backup, Wiederherstellung in isolierter Umgebung nachgewiesen,
Wartungsfenster ohne schreibende Anwendung, konkrete SQL-/Hashprüfung und
ausdrückliche Eigentümerfreigabe. MySQL-DDL ist nicht verlässlich durch eine
normale Anwendungstransaktion zurückrollbar.

Bei einem Fehler stoppen und Zustand dokumentieren. Keine automatische
Down-Migration oder DROP-Anweisung. Alte Anwendung nach isoliertem Test
gegebenenfalls wiederverwenden; neue Tabelle bleibt erhalten. Sobald echte
Ereignisse existieren, darf ein Rollback diese nicht löschen. Backup-Restore
kann neuere Daten verlieren und benötigt einen eigenen Wiederherstellungsplan.

## Abnahmekriterien vor Schritt 3 und produktivem Einsatz

- Fachentscheidungen oben bestätigt; endgültiges SQL reviewed und hashgebunden.
- Migration/zweiter Aufruf/abweichendes Schema im isolierten Test nachgewiesen.
- NULL-, 0-, positive Rereads und alle alten Preise/Beziehungen unverändert.
- FK-Verhalten und ausdrücklicher Löschworkflow geprüft.
- Ereignis und Zähler atomar, Wiederholungen und parallele Aktionen idempotent.
- Erstlektüre, Reread, Rückdatierung und Monatswechsel mit injizierbarer Clock
  getestet; kein Ereignis bei gewöhnlichem Metadatenspeichern.
- M06-Erweiterung/Kauf und T03-Transaktionsquarantäne unverändert abgesichert.
- Alte App mit additivem Schema und neue App mit exakt passendem Schema geprüft.
- Manual- und Testschutz vor erstmaliger Nutzung abgestimmt; Browserabnahme
  gesondert. Keine der realen DB-Nachprüfungen ist durch diesen Plan erfolgt.
