# T05 – Umsetzungsvorbereitung

**Aktueller Stand 21.11.2026: umgesetzt und geprüft.** Der folgende ursprüngliche
Vorbereitungsstand bleibt als historische Planungsgrundlage erhalten. S03/T04
inzwischen per Fast-Forward auf main veröffentlicht (`f40f043`), T05 separat
auf codex/t05-maintainability implementiert. Abschlussmatrix, Änderungen und
Nachweisgrenzen: `T05_CLOSEOUT.md`; konkrete Prüfläufe: `TESTING.md` und
`MANUAL_TESTING.md`. Noch kein T05-Commit/Push/Merge.

Stand 21.11.2026. Branch codex/t05-maintainability basiert auf dem lokalen
T04-Abschlusscommit f40f043. S03 liegt davor in f7e7ee2. Kein Push/Merge;
Produktionsausrollung und S03-Migration bleiben gesondert. Noch keine produktive
T05-Implementierung in diesem Vorbereitungsauftrag.

## Priorisierte kleine Arbeitspakete

1. **T05-0: Inventar und messbare Baseline.** Tatsächliche Controller-/Service-
   Zuständigkeiten, vorhandene WriteRequests/Importmodelle, Abfragepfade, CSS-
   und Template-Duplikate sowie verifizierbar ungenutzte Elemente erfassen.
   Vorhandene T02/T03/M06/M01/M02/S03-Verträge als unveränderliche Grenze nutzen.
   Keine Bereinigung allein aufgrund eines Suchtreffers.
2. **T05-1: Einkaufsliste ohne monatliche Einzelabfragen.** Im bestehenden
   showEinkaufsliste sind 19 Aufrufe von getItemsForMonth nachgewiesen. Zunächst
   Abfragezahl mit Mocks/SQL-Statistik messen; danach denselben Zeitraum als
   eine begrenzte Datumsbereichsabfrage laden und im Service nach YearMonth
   gruppieren. Anzeige vollständig gekaufter/undatierter Gruppen, Reihenfolge,
   NULL-Werte, exakte Geldsummen und Datumsgrenzen erhalten. Keine Pagination
   ohne belegten Bedarf und geklärte Auswirkungen auf Summen/Filter.
3. **T05-2: Controller gezielt entlasten.** Nur nach Inventar klar abgegrenzte
   View-Aufbereitung/DTOs extrahieren und vorhandene Requestvalidierung reuse.
   Keine neuen Transaktionsgrenzen, kein zweiter Schreibcoordinator, keine neue
   generische Serviceplattform. Ein Endpoint/Anwendungsfall je Änderung.
4. **T05-3: Gemeinsame UI-Bausteine.** Identische Dialog-/Formular-/Coverlogik und
   Styles nachweisen, dann in kleinen Schritten teilen. Bestehendes Design,
   Zugänglichkeit, schmale Ansichten, Cancel/Replay/CSRF-Verhalten erhalten.
   Die im Browser gedrängte Aktionsspalte einzelner Einkaufszeilen zunächst
   reproduzieren/messen; nicht durch pauschalen Tabellenumbau behandeln.
5. **T05-4: Dokumentation und tote Elemente.** Nachweislich ungenutzte Elemente
   entfernen, komplexe Regeln mit knappen deutschen Kommentaren/Javadoc erklären.
   Vorhandene Treiberkonfiguration und Build-/CI-Wege zuerst prüfen; keine neuen
   Abhängigkeiten oder Versionssprünge allein zum Refactoring. Historische
   Windows-Evidenzen und private Nachweise nicht löschen.

## Tests und Abnahmekriterien

- Bestehende grüne Baseline: 1459 Javafälle bestanden, 2 historische Commit-Opt-ins
  übersprungen; 257 DOM- und 94 Manual-Schutzfälle grün. T04-Template-Endprüfung
  32/32 bestanden. Ergebnisse nicht als neue T05-Ausführung ausgeben.
- Zuerst datenbankfreie Charakterisierung der konkret betroffenen Pfade.
  Für Abfragebündelung Anzahl/Argumente und fachlich identische Gruppen prüfen,
  einschließlich zweier gleichnamiger Monate verschiedener Jahre und NULL-Preis.
- Gradle nur offline/ohne Daemon nach identischem Dry-Run. Geschützte Tests nur
  auf mangalib_test; vorhandene Rechte-/Schema-/Verbindungs- und Snapshotguards.
  Kein produktiver Zugriff, keine Migration oder Bestandsnormalisierung.
- Bei verändertem GUI-Verhalten DOM- und isolierte Browsernachprüfung ohne
  unnötige Datenänderung. Bei reinem verhaltenswahrendem Umbau Vertrags-/Snapshot-
  und passende gezielte Tests. Abschließend Diff-/Dateiauswahlkontrolle.
- Kein T06-Loggingumbau, T07-Betrieb oder neue Kann-Funktion als Nebenarbeit.

## Abgrenzung

Dieser Plan trifft keine neuen Fachentscheidungen. Wird bei der Charakterisierung
statt einer technischen Duplikation eine fachliche Mehrdeutigkeit sichtbar,
diese vor einer Verhaltensänderung ausdrücklich klären. Kleine zusammenhängende
Änderungen bevorzugen, kein pauschales „alles modernisieren“.
