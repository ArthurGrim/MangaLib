# T05 – Abschlussmatrix

Stand 21.11.2026, Branch `codex/t05-maintainability`, Basis `f40f043`.
**T05_TECHNICALLY_VERIFIED** im hier abgegrenzten Wartbarkeitsumfang.
S03/T04 einschließlich ihres Abschlusses auf origin/main veröffentlicht;
die neuen T05-Änderungen bleiben bis zum gesonderten Git-Abschluss lokal.

| Anforderung | Umsetzung/Nachweis | Status |
|---|---|---|
| Schichten/Controller entlasten | Monatsgruppen, Bandzahlen und Geldsummen in ShoppingOverview und lesendem Servicepfad; bestehende Modelnamen/JSON-Verträge erhalten | Bestanden |
| Typisierte Eingabe-/Importmodelle | Vorhandene WriteRequests.Series/Shopping und M02-Importverträge wiederverwendet; keine zweite Validierungsarchitektur | Vorhanden, regressionsgeprüft |
| Abfragen bündeln/messen | 19 Monatsaufrufe durch eine begrenzte Bereichsabfrage ersetzt, separate Undatiertabfrage; echte SQL-Anweisungen 36 → 18 am bestehenden Testbestand | Bestanden |
| Pagination bei Bedarf | Kein belegter Bedarf; Zeitraum bleibt begrenzt. Keine Änderung der Summen-/Filtersemantik durch künstliche Seitengrenzen | Nicht erforderlich |
| Gemeinsame UI-Routinen/Styles | Drei Duplikate des Eingaberesets in einem containergebundenen Helper; gemeinsame CSS-Datei für feste Aktionsspalten und mobile Zeilen | Bestanden |
| Keine Design-/Datenregression | DOM-Tests, responsive Browserprüfung, Dialogabbruch und vollständige Daten-/Schemafingerabdrücke | Bestanden |
| Nachweislich ungenutzte Elemente | Unbenutzte line-chart-wrap-Regel und nach Umstellung redundanter Monatsnamenhelfer entfernt; andere tatsächlich verwendete Helfer erhalten | Bestanden |
| Verständlichkeit/Dokumentation | Reine View-Aufbereitung benannt/dokumentiert, Besonderheiten gekaufter Monate/Replay-Sperren erläutert; keine neue generische Plattform | Bestanden |
| Konfigurierbare Treiberpfade | Bereits vorhandene gecko/firefox/worker-Pfade samt SHA-256-Pins erhalten; keine unsichere automatische Suche/Fallbacks | Vorhanden, unverändert |
| Dependency-/CI-Pflege | Offline-Runtime-Inventur, keine neuen Abhängigkeiten; manueller DOM-Workflow mit readonly GitHub-Rechten, Lockfile und deaktivierten Installskripten | Lokal geprüft; Remote-Erstausführung nach Veröffentlichung |
| Sicherheits-/Fachgrenzen | T02-Guards, T03-Coordinator/Preisregeln, M06/S03-Lesestatus, Manual-/Live-Trennung unverändert | Gesamtregression bestanden |

## Tatsächliche Ergebnisse

- 1.462 Java-Tests bestanden; 0 Fehler; 2 vorhandene Commit-Opt-ins übersprungen
  (ShoppingEditIntegrationTest/VolumeImportIntegrationTest), 1.464 Fälle insgesamt.
- 260 DOM-Tests und 94 Manual-Schutztests bestanden. Offline-Paketierung von
  Anwendung und getrenntem Manual-Kandidaten erfolgreich; vier JS-Syntaxprüfungen grün.
- Testdaten-/Schemafingerabdruck vor/nach Regression identisch:
  `77fce665515396821a2fb21f36f074a3abccdac48de04c09f1f065be1c9e814f`.
- Manueller vollständiger Vorher-/Nachher-Snapshot identisch:
  `f19146da1fbc0c7840242fee81ec12d87e6a8d90720ac5927298bf1596c42c33`.
- Browser: Einkaufsübersicht bei 390/768/1280 Pixeln ohne horizontalen Überlauf;
  bearbeitbare und gekaufte Zeilen, deutsche Preise, drei Formularpfade mit Cancel/
  Wiederöffnen geprüft. Keine Speicherung, Autofill- oder Kaufaktion.
- Eigene Testinstanzen geordnet beendet, Port 18080 frei. Sechs historische
  Manual-JARs hashgleich erhalten, historische Experimente unangetastet.

## Geänderte Projektdateien

- Backend: EinkaufslisteController.java, EinkaufslisteService.java,
  neu ShoppingOverview.java.
- Frontend: content-layout.css, statistik/st_overview.css, security.js,
  einkaufsliste.js, popup_add_reihe.js, popup_edit_reihe.js.
- Tests: ShoppingMonthRegressionTest.java, neu ShoppingOverviewTest.java und
  ShoppingOverviewIntegrationTest.java, content-layout.test.cjs, security.test.cjs.
- CI/Dokumentation: neu .github/workflows/dom.yml, T05_IMPLEMENTATION_PLAN.md,
  T05_CLOSEOUT.md; ROADMAP.md, TESTING.md, MANUAL_TESTING.md aktualisiert.

## Grenzen und nächste Schritte

Keine generelle Neuschreibung aller Controller, kein erzwungenes DTO für jede
interne Map. Bestehende Patch-/NULL-/Replay-Verträge bleiben maßgeblich. Weitere
kleine Refactorings gehören zur laufenden Pflege konkreter Anwendungsfälle.
Die gemessene Abfrageersparnis gilt für den Testbestand; EAGER-/inverse
Beziehungsladevorgänge bestehen weiterhin. Keine Behauptung konstanter SQL-Zahl
für beliebige Bestände oder umfassender Performance-/Lastabnahme.

CI ist lokal vorbereitet, nicht auf GitHub ausgeführt. Kein neuer Online-CVE-Scan,
keine Versionserhöhung oder Installation. Produktion/S03-Migration nicht ausgeführt;
T06-Logging und T07-Betrieb bleiben separate nächste Roadmap-Arbeiten.
Private Logs, Snapshots, Kandidaten und Screenshots bleiben unter .gradle ignoriert.
Kein neuer nativer Scraper-/Jobversuch und kein Zugriff auf mangalib.
