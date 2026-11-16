# S03 – Lesedatum und Reread-Historie

## Aktueller Stand, konsolidiert 16.11.2026

Die Implementierung ist über die reine Monatsberechnung hinaus vorbereitet:
ReadingHistoryService/Store mit nativem JPA-Zugriff innerhalb des vorhandenen
LibraryWriteCoordinator, explizite Erstlektüre/Reread-/Datumskorrektur-Endpunkte,
Hooks in bestehenden Band-/Reihenoperationen, Monatsansicht und historische
Lesezeitschätzung ohne Doppelzählung. Historie bleibt nach Statusreset erhalten.
Bei aktivierter Historie sind direkte Reread-Zähler-/Massenänderungen gesperrt;
Rereads entstehen ausschließlich durch ausdrückliche Leseaktionen.

Standardmäßig deaktiviert, keine automatische Schemaerzeugung und keine neuen
JPA-Entities. Alte Schemas bleiben startfähig. Aktivierung validiert Spalten,
InnoDB, Unique-Key und RESTRICT-FK; fehlendes Schema führt zum Abbruch.
Die UI verwendet vorhandene Sicherheits-/Feedbackhelfer und bestehende Farben.
Ein unbestätigter Schreibvorgang bleibt über Dialogwechsel gesperrt.

Prüfstand 15.11.2026: 234 ausgewählte Java-Tests, 94 datenbankfreie Manual-Tests,
250 DOM-Tests erfolgreich. Zwei rein lesende Schemavorprüfungen bestanden,
Altdaten unverändert. Produktion/Manual-Datenbanken nicht verbunden.
Keine Migration, kein Anwendungslistener, keine echte Browser-/Scraperausführung,
kein Commit. Die folgenden Planungs-/Zwischenstände bleiben historisch erhalten.
Konkrete Hashes, nächste Freigabe und Nachweisgrenzen: S03_ACCEPTANCE.md.

## Aktueller Nachtrag (15.11.2026)

Fachlich bestätigt: einmalig Rereads auf 0 initialisieren, da bisher keine
Rereads vorliegen; dies wird separat und ausdrücklich migriert, nicht bei
normalen Schreibvorgängen. Gleicher Tag darf mehrere bewusste Lektüren haben,
gleiche Aktion zählt einmal, Datumskorrektur ändert keinen Zähler,
Stornierungen zunächst gesperrt. Die unten offenen Punkte sind insoweit
historisch entschieden. Geplante Zählerupdates bleiben atomar mit Ereignissen.

Neue reine ReadingHistoryStatistics berechnet Monatszahlen ohne Repository,
Zeitstempelinterpretation oder Mutation von Altbeständen. Erstlektüren und
Rereads werden getrennt gezählt; identische Aktions-/Bandschlüssel dedupliziert,
widersprüchliche Daten abgewiesen. Keine aktive Historienoberfläche oder
Persistenzanbindung. Neun neue Unitfälle plus bisherige 106 Fälle bestanden,
gesamt 115/115, ohne Fehler oder übersprungene Fälle, offline nach Dry-Run.

Zwei Migrationsvorlagen und Ablaufanleitung sind außerhalb des Klassenpfads
vorbereitet. Keine ausführbaren Zielskripte, keine Hashfreigabe und keine
Schemaänderung. Nächste Ausführungsgrenze: geschützte lesende Typ-/Schema-
Vorprüfung für konkrete Skripte, danach gesonderte Migrationsprobe. Neue
JPA-Entities vor der Migration würden bestehende ddl-auto=validate-Starts
gegen unmigrierte Schemas verhindern und wurden deshalb noch nicht aktiviert.

## Schritt 1: Datenbankfreie Charakterisierung, 15.11.2026

Branch codex/s03-reading-history basiert auf dem S02-Abschluss 8683065.
Dieser Schritt ändert ausschließlich Tests und Dokumentation. Keine Migration,
neue Entity, UI-Änderung oder produktive Fachregel ist damit implementiert.

## Bestätigter Ist-Zustand

| Pfad | Tatsächliches Verhalten | Konsequenz für S03 |
|---|---|---|
| Band, Entity | boolean istGelesen, nullable Integer reread; erstelltAm und aktualisiertAm sind technische Hibernate-Daten | Kein belastbares Lesedatum vorhanden |
| BandService.updateBandIstGelesen | Setzt Status und speichert bei jedem Aufruf, auch unverändert; keine Reihenaggregation oder Zählererhöhung | Separater Servicepfad, nicht der reguläre Editor; keine automatische Reread-Erkennung |
| MainController.updateBand → BandService.updateBand | Validierter Editierpfad; geänderter Status aggregiert die Reihe, geänderter Reread setzt Reihenwert auf Minimum der Bände (NULL dabei als 0) | Beide Änderungspfade bei späterer Historienintegration berücksichtigen |
| Reihenbearbeitung → MangaReiheService.updateMangaReihe | Explizite applyReadToVolumes/applyRereadToVolumes steuern Sammeländerungen; unverändertes NULL wird erhalten | Gewöhnliches Speichern darf keine Leseereignisse erzeugen |
| updateMangaReiheReread | Hebt Bandzähler mindestens auf Reihenwert an, höhere Werte bleiben erhalten; NULL wird dabei zu 0 | Zählerkorrektur beweist keine datierbare Lektüre |
| M06-Erweiterung und Kauf | Individuelle Altbandzustände bleiben erhalten, neue Einkaufsbände starten ungelesen | Historie bestehender Bände darf durch Erweiterung nicht geändert werden |
| ReadingTimeService / BandRepository.readingEntries | Nur aktuell gelesene Bände; 45 Minuten × Faktor, optional × (1 + Rereads); unbekannte Faktoren ausgelassen, unbekannte Rereads separat gezählt | Bisherige Schätzung ist keine zeitlich zuordenbare Historie; Zurücksetzen auf ungelesen entfernt den Band aus dieser Projektion |
| Banddialog | rereadNull wird als unbekannt dargestellt; leere unbekannte Eingabe sendet null | Bestehende NULL-/0-Unterscheidung erhalten |

Quellen: src/main/java/de/mangalib/entity/Band.java;
service/BandService.java (updateBandIstGelesen, updateBand,
updateMangaDetailsIstGelesenStatus, updateMangaReiheRereadOnBandChange);
service/MangaReiheService.java (updateMangaReihe, updateBaendeIstGelesenStatus,
updateMangaReiheReread); service/ReadingTimeService.java;
repository/BandRepository.java; static/js/popup_edit_baende.js und popup_edit_reihe.js.
Service-/Repositorypfade sind relativ zu src/main/java/de/mangalib,
JS-Pfade relativ zu src/main/resources angegeben.

## Neue ausführbare Ist-Regressionen

ReadingHistoryCharacterizationTest nutzt echte BandService-Methoden mit einem
Mockito-BandRepository, ohne Spring-Kontext, Transaktionsmanager oder JDBC.

- Markieren und Zurücksetzen bei Reread NULL, 0 und 2 erhalten Zähler und
  technische Datumswerte im Java-Objekt (drei Fälle).
- Erneutes Markieren speichert erneut, erhöht aber keinen Zähler.
- Separater Status-Service aggregiert den Reihenstatus nicht.
- Fehlender Band speichert nichts; fehlende Parameter scheitern vor Repozugriff.
- Reihenaggregation erhält individuelle Bandzustände und NULL-Rereads.

Diese acht Fälle charakterisieren den Bestand. Sie beweisen weder Persistenz,
Hibernate-Zeitstempelverhalten, Transaktionsrollback noch eine vorhandene
Lesehistorie. Bestehende ReadingTimeServiceTest-, ReadingStatusExtensionTest-
und LegacyEditCharacterizationTest-Fälle sichern Schätzung, M06-Erweiterung,
Kauf und reguläre Editor-/NULL-Verträge zusätzlich datenbankfrei ab.

## Zielregeln und noch ausstehende Fachbestätigung

Die folgenden Empfehlungen sind die vorgeschlagene Grundlage für Schritt 2,
keine bereits implementierten Regeln:

1. Erstlektüre mit korrigierbarem Abschlussdatum, Standard heute.
2. Reread nur über ausdrückliche Abschlussaktion mit eigenem Datum.
3. Monatsstatistik zählt abgeschlossene Lektüren nach Abschlussmonat;
   Erstlektüren und Rereads getrennt ausweisen.
4. Alte Status-/Zählerwerte bleiben erhalten, unbekannte Zeitpunkte unbekannt.
5. Zurücksetzen auf ungelesen löscht keine Historie. Fehlerhafte Ereignisse
   benötigen einen ausdrücklich bestätigten Korrekturweg.
6. Sammelaktionen bestätigen betroffene Bände und Datum.

Zusätzlich vor Schemafreigabe entscheiden: Verhältnis zwischen altem Zähler und
neuen Ereignissen (Empfehlung: historischer, undatierter Anteil plus neue
Ereignisse, ohne Doppelzählung); rückdatierte Ereignisse; mehrere Abschlüsse
am gleichen Tag; Korrektur/Löschung eines Ereignisses. Ein wiederholter Request
ist kein neuer Reread. Kalendertage und Monatsgrenzen müssen explizit sein.

## Abgegrenzte nächste Schritte

1. Fachregeln und Ereignismodell freigeben. Separate Historie empfohlen, da
   ein einzelnes readAt mehrere Lektüren nicht abbildet.
2. Additiven, versionierten Migrationsplan erstellen; historische Zeitpunkte
   nicht aus erstelltAm/aktualisiertAm ableiten. Keine Produktionsmigration
   ohne Backup, Wiederherstellungsnachweis und gesonderte Freigabe.
3. Gemeinsamen transaktionalen Schreibpfad unter bestehender T03-Koordination
   implementieren. Alle Band-/Reihen-/Kaufpfade inventarisieren; M06 erhalten.
4. Fake-/MockMvc-Tests für Datum, Wiederholung, Zählerabgleich, Sammeländerung
   und Fehlerrollback; bestehende Antwortverträge erhalten.
5. Gesondert freizugebende geschützte Integration: Flush/Clear, Migration,
   Altbestandsvergleich, Commit/Rollback und konkurrierende Wiederholungen.
6. Isolierte Browserabnahme der neuen Aktionen im bisherigen Design.

Schritt 1 beansprucht keine reale Datenbank- oder Browserabnahme für S03.

## Tatsächliche Prüfungen

Alle Gradle-Aufrufe offline, ohne Daemon und nach identischem erfolgreichem
Dry-Run. Erster Lauf: acht neue Charakterisierungen und fünf Lesezeittests
bestanden (13/13). Erweiterter Lauf: 106 Fälle, 102 bestanden, vier fehlgeschlagen,
keine übersprungenen Fälle. Aufteilung: neue Klasse 8/8, ReadingTimeServiceTest
5/5, LegacyEditCharacterizationTest 53/53, ReadingStatusExtensionTest 36/40.

Die vier Fehler betreffen veraltete M06-Testrequests bzw. Antworterwartungen:
bothPurchaseMatchesUseRealServicesAndPreserveOldBands sendet in drei
Index-Zuordnungsfällen den bestätigten Bereich ohne purchaseToken und erhält
korrekt 409 statt des erwarteten 200. purchaseOfNewSeriesKeeps417And200ContractsAndStartsUnread
vergleicht eine reine message-Map mit der inzwischen um purchaseToken und
purchaseSummary ergänzten 417-Antwort; der anschließende Request besitzt
ebenfalls keinen Token. Diese Bestandsfälle wurden nicht abgeschwächt oder
übersprungen. Vor produktiver S03-Implementierung die Testfixtures an den
aktuellen Kaufvertrag anpassen und die tatsächlichen M06-Assertions erneut
ausführen. Dieser Lauf belegt die betroffenen Kaufpfade nicht erfolgreich.

## Nachtrag: Fixturekorrektur und Schritt 2 (15.11.2026)

Die vier alten Kauf-Testfälle sind an den aktuellen Vertrag angepasst:
Bestandskauf übernimmt den geprüften Vorschautoken, Neuanlage prüft die
vollständige 417-Antwort einschließlich Token und Kaufzusammenfassung und
sendet den Token bei der Bestätigung. Die inverse Detailbeziehung der
Neuanlage-Fixture ist vollständig gesetzt. Status-, Speicher- und sämtliche
M06-Altbandassertions bleiben erhalten. Ein erster Korrekturlauf scheiterte
noch an dieser inversen Beziehung (105/106); der abschließende Lauf bestand
mit 106/106, ohne Fehler oder übersprungene Fälle. Jeweils identischer
vorheriger Dry-Run, offline und ohne Daemon. Keine Datenbankverbindung.

Schritt 2 ist als additiver, versionierter Migrationsplan in
S03_MIGRATION_PLAN.md ausgearbeitet. Kein SQL ausgeführt oder automatisch
geladen. Der Plan behandelt Tabellenentwurf, FK-/Idempotenzregeln, getrennte
Einrichtungsrechte, Vorher-/Nachher-Nachweise, isolierte Abnahme und
Wiederherstellung. Vor endgültigem SQL sind Zählerabgleich und Korrekturregeln
zu bestätigen; die bestehenden Datenbankguards bleiben unverändert.

## S03 – Abschlussnachtrag 16.11.2026
S03_TECHNICALLY_ACCEPTED: Die älteren Integrationstests sind auf den echten Coordinator mit erzwungenem Rollback portiert. Vollständiger Java-Lauf: 1454 bestanden, 0 Fehler, 2 bestehende M01-/M02-Commit-Opt-ins übersprungen (keine Platzhalter). Alle 29 MVC-Verträge grün; 254 DOM-Fälle erfolgreich. S03-Konkurrenzfall und zwei reale Rollbackfälle bestanden; abschließender lesender Testschemafingerabdruck 77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f unverändert. Frühere REGRESSION_BLOCKED-Nachträge sind damit historische Zwischenstände. Keine Produktionsmigration/-aktivierung oder Reread-Initialisierung; diese bleiben separat abgesicherte Ausrollschritte. Keine Git-Schreibaktionen. Vollständige Abschlussmatrix und Nachweisgrenzen: S03_CLOSEOUT.md.
