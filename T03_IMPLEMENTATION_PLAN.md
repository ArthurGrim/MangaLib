# T03 – Umsetzungsvorbereitung

Stand: 02.11.2026. Ausschließlich statisch vorbereitet, NICHT implementiert.
T02-Abschlussentscheidung: siehe [T02_CLOSEOUT.md](T02_CLOSEOUT.md).
Keine DB-Verbindung, Migration oder Änderung fachlicher Regeln für diesen Plan.

## 1. Verifizierte Ausgangslage

| Pfad | Ist-Verhalten / Nachweis | Konsequenz für T03 |
| --- | --- | --- |
| `EinkaufslisteController.buyItemUpdate`, ab Zeile 172 | Findet Einkauf, sucht über Mangaindex (NULL wird 0), nimmt ersten Treffer, sonst ersten Titelmatch; ruft Reihenupdate und danach `updateGekauft` separat auf. | Keine gemeinsame Controllertransaktion; Reihencommit und Kaufmarkierung können auseinanderfallen. Mehrdeutige Identifikation. |
| `EinkaufslisteController.buyItemCreate`, ab Zeile 250 | Legt Reihe über Service an und markiert Einkauf danach über zweiten Serviceaufruf gekauft. | Dasselbe Atomaritäts-/Wiederholungsrisiko; vor Anlage keine wirksame atomare gekauft-Prüfung. |
| `MangaReiheService.saveMangaReihe` und `updateMangaReihe`, ab Zeilen 126/326 | Transaktionale Reihenoperationen; M06-Validierung und Appendpfad bereits vorhanden. | Wiederverwenden, aber in gemeinsame Kauftransaktion einbetten. |
| `EinkaufslisteService.updateGekauft`, Zeile 271 | Eigene `@Transactional`-Methode, lädt und speichert nur Flag. | Zusammen mit Bibliotheksänderung im gleichen Commit sichern. |
| `BandService.updateBand`, ab Zeile 480 | Speichert Band, anschließend Reihenaggregate/Details; keine umspannende Service-Transaktion im aktuellen Code. | Teilcommits und konkurrierende Summenänderungen möglich; gemeinsamer Aggregat-Lock/Transaktion. |
| `BandService.updateOrCreateBaende`, Zeile 355; `BandRepository.findByMangaReiheId` | Bestehender Band aus `get(i-1)` einer nicht geordneten Repositoryliste. | Listenposition ist kein fachlicher Identifikator. |
| `BandService`, Zeilen 261/363/441 | Verteilte Einzelrundung ohne Restausgleich; eine Division ohne Rundungsregel. | 0,01 auf 3 Bände kann verloren gehen bzw. nicht terminierende Division werfen. |
| `EinkaufslisteController.addToEinkaufsliste` / `EinkaufslisteService.saveEinkaufslisteItem` | Request-BigDecimal wird zwischenzeitlich in Double konvertiert und zurückkonvertiert. | BigDecimal durchgängig verwenden; daraus allein wird kein konkreter historischer Centverlust behauptet. |
| Entities/Repositories | Kein `@Version` oder Kauf-Schreiblock gefunden; Statusflag und Mangaindex sind keine Idempotenz-/Eindeutigkeitsgarantie. | Konkurrenzschutz explizit entwerfen. Keine unbekannten DB-Constraints voraussetzen. |

Dies sind statische Codebefunde, keine neu durchgeführten Fehler-/Datenbanktests.
Die Centabschneidung der Monatsstatistik (`mapResultsToLong`) bleibt T04/M04.

## 2. Verbindliche Dateninvarianten

- Bestehende IDs, Beziehungen, Bandnummern, Preise, Lesestatus, Rereads und Metadaten
  nicht implizit ersetzen/normalisieren. Primärschlüssel nie neu vergeben.
- Ein Einkauf wird höchstens einmal übernommen. Bibliotheksänderung und gekauft-
  Markierung sind genau eine fachliche Transaktion.
- M06 unverändert: Altbände individuell erhalten; neue Bände ungelesen bzw. explizit
  initial gesetzt; Reihenstatus danach aus tatsächlichen Bandzuständen aggregieren.
  Bestätigter Bestand 1..N darf nur um N+1..Z ergänzt werden; inkonsistente Bestände
  oder fehlende Details bleiben vor Mutation gesperrt.
- Die expliziten Massenoptionen und die Unterschiede NULL/0/false aus T02 bleiben
  erhalten. Fehlende Preise sind kein Auftrag, 0 Euro zu erfinden.
- Gesamtpreis = Summe tatsächlicher Bandpreise und ihrer zugeordneten Anpassungen.
  Verteilte Centbeträge ergeben exakt die vorgegebene Anpassung, auch bei negativem
  Vorzeichen. Reihenmittelwerte sind Darstellung/Aggregat, keine Quelle zur
  nachträglichen Rekonstruktion individueller Bandpreise.
- Sicherheitsfilter, MVC-Erfolgspayloads, 404/417-Fachablauf und DB-Guards erhalten.
  Neue Konfliktstatus nur nach ausdrücklicher Vertragsentscheidung.

## 3. Priorisierte kleine Arbeitspakete

### P0 / T03-0 – Verträge und Fehlerfälle zuerst charakterisieren

Mit reinen Unit-/Service-Mocks: fehlgeschlagene Kaufmarkierung nach erfolgreichem
Reihenspeichern, zweimalige Übernahme derselben ID, mehrdeutiger Index/Titel,
NULL-Mangaindex, unsortierte Bandlisten, 0,01/-0,01/0,02 auf 3 Bände,
Bandanzahlreduktion, fehlende Preise. Erwartete Sollregeln neben Ist-Befunden
festhalten; keine dauerhaft roten Tests und keine Änderung am Bestand.

Vor produktiver Umsetzung Entscheidungen aus Abschnitt 5 bestätigen.

### P0 / T03-1 – Atomare Kaufoperation im Service

Kleiner `PurchaseService` als neue fachliche Transaktionsgrenze; Controller nur
Binding/Validierung und HTTP-Ergebnisabbildung. Beide Kaufwege delegieren dorthin.
Einkauf/Referenzen/Ziel eindeutig vor Mutation prüfen, dann innerhalb derselben
Transaktion Reihe/Details/Bände und gekauft-Flag ändern. Bestehende Services mit
REQUIRED-Propagation wiederverwenden. Fehler bis außerhalb der Transaktionsgrenze
werfen; im Service kein catch-und-200/500 mit unbeabsichtigtem Commit. Erforderliche
Rollbackregel auch für vorgesehene checked Exceptions explizit testen.

417 darf weiterhin ausschließlich eine noch nicht schreibende Statusauswahl sein.
`buyItemCreate` muss erneut unter Lock prüfen, ob derselbe Kauf inzwischen bereits
übernommen wurde; das vorgelagerte 417 ist keine Sperre oder Kaufreservierung.

### P0 / T03-2 – Wiederholung und Konkurrenz

Pessimistischer Schreiblock auf Einkaufsposition innerhalb der Kauftransaktion;
gekauft-Zustand unter demselben Lock prüfen. Für bestehende Zielreihe zusätzlich
Reihenlock, auch für Einzelband-/Aggregatänderungen. Eine dokumentierte Lockreihenfolge
(Einkauf, danach Zielreihen nach ID; Bandupdates zuerst Zielreihenlock) verhindert
unnötige zyklische Erwerbe. Keine Locks über Scraper-/Netzwerkaufrufe halten.

Wichtig: Zwei verschiedene Einkaufspositionen können gleichzeitig dieselbe noch
NICHT vorhandene Reihe anlegen. Einkauf-Rowlocks allein lösen diesen Fall nicht.
Empfohlen für das heutige Einzelinstanzmodell: schmaler gemeinsamer Kauf-/Anlage-
Mutex vor Beginn der Spring-Transaktion, bis nach Commit/Rollback gehalten; nicht
nur im Controller und nicht als Ersatz des DB-Rowlocks. Das schützt nur eine JVM.
Alternativ für echte Mehrinstanzgarantie: freigegebene stabile fachliche
Eindeutigkeit plus DB-Constraint/Migrationsplan oder eigener persistenter
Serialisierungspunkt. Ohne entsprechende Entscheidung keine Mehrinstanz-
Wiederholungssicherheit behaupten. Kein solcher Schemaeingriff jetzt.

Erneuter bereits erfolgreicher Kauf: empfohlen bestehendes Ergebnis neutral als
bereits übernommen bestätigen, ohne weiteres Schreiben. Details/Statuscode und
gekauft=NULL-Behandlung vor Umsetzung festlegen.

### P1 / T03-3 – Stabile Bandzuordnung und gemeinsame Aggregate

Vor jedem Import/Update Bestand nach unveränderter Band-ID erfassen; vorhandene
Bandnummern dienen nur als zusätzliche validierte Zuordnung, nicht als Listenindex.
Duplicate/NULL-Nummern, widersprüchliche Fremdschlüssel oder Mehrdeutigkeiten vor
Änderungen kontrolliert ablehnen. Kein automatisches Umnummerieren/Lückenfüllen.
Metadatenänderungen ohne finanzielle Absicht dürfen alte Summen/NULL-Werte nicht
neu berechnen. Finanzänderung und betroffene Aggregat-/Detailänderungen innerhalb
einer Transaktion unter gemeinsamem Reihenlock durchführen.

Import-Ordinal N wird nur dann einem bestehenden Band zugeordnet, wenn dies nach
festgelegtem Vertrag eindeutig ist. Externer Manga-Passion-Index ist Zusatzbeleg,
kein automatisch global eindeutiger Schlüssel. Fehlender Index ist nicht 0.

### P1 / T03-4 – Geld und Restcent-Verteilung

Eine kleine reine Funktion mit BigDecimal/ganzen Centbeträgen, ohne DB. Eingaben
vorher gegen technische Skalierungs-/Wertebereichsgrenzen prüfen. Betrag in Cent
zerlegen, Quotient auf Zielbände verteilen und signierte Restcents deterministisch
nach bestätigter Bandreihenfolge vergeben. Beispiel 0,01 auf 3: 0,01/0,00/0,00;
-0,02 auf 3: -0,01/-0,01/0,00. Summenerhaltung als zentrale Assertion.
Keine Double-Stufe und keine formatierte Zeichenkette im Rechenkern. Neue Bände
und bewusste Massenänderungen getrennt von unveränderten Altbandpreisen behandeln.
Unbekannte Preise weiterhin kontrolliert sperren; Null-als-0-Regel nicht erfinden.

### P1 / T03-5 – Bandanzahländerungen

Erweiterung über vorhandenen M06-Appendpfad. Unveränderte Anzahl darf keine
Bandidentitäten/individuellen Werte ersetzen. Bis zur Fachfreigabe Reduktion vor
Mutation ablehnen statt Bände zu löschen, unsichtbar zu machen oder Deklaration
und Bestand auseinanderlaufen zu lassen. Danach gezielte Umsetzung der gewählten
Reduktionsregel mit expliziter betroffener Bandauswahl, Datenerhalt und Tests.

### P1 / T03-6 – Geschützte Integration und Abnahme

Zuerst Mocktests ohne DB, dann Guards; nach gesonderter DB-Freigabe kontrollierte
Integrationsstufen, Vorher-/Nachher-Snapshots und vollständige Regression. Vor
jedem Gradle-Aufruf identischer Offline-Dry-Run. Anschließend separat freizugebende
Browserprüfung ausschließlich eigener synthetischer Kaufdaten in der manuellen
Umgebung. Kein Scraper nötig, vorhandene Doubles verwenden.

## 4. Testmatrix / erforderliche Nachweise

| Fall | Zuerst DB-frei | Später echter Nachweis |
| --- | --- | --- |
| Kauf in bestehende/neue Reihe | Orchestrierungs-/Fehlerinjektions-Mocks | atomarer Commit bzw. vollständiger Rollback beider Aggregate |
| Fehler vor/nach Bandsave und vor Kaufmarkierung | jede Stufe injizieren | Zustand außerhalb der fehlgeschlagenen Transaktion unverändert |
| Gleiche Kauf-ID wiederholt/parallel | Zustands-/Lockadapter-Fakes | genau eine Übernahme in unabhängigen Transaktionen |
| Verschiedene Käufe, gleiche bestehende Reihe | Lockreihenfolge/Addition | keine verlorenen Updates, unveränderte Altbände |
| Verschiedene Käufe, gleiche neue Reihe | serialisierte Neuanlage | definierte Eindeutigkeit/Instanzgrenze wirklich nachweisen |
| Bandlisten in beliebiger Reihenfolge | Permutationen, IDs/Nummern | nach Flush/Clear unveränderte IDs und Beziehungen |
| Betrag +/-0,01/0,02, 0,00; technische Grenzen | Summenerhaltung, deterministische Restverteilung | exakte DECIMAL-Werte nach erneutem Laden |
| Anzahl gleich/größer/kleiner; Lücken/Duplikate | M06-Regeln und Reduktionsvertrag | keine ungewollte Löschung/Metadatenänderung |
| NULL-/Altpreise, Flags/Reread/URLs | Preserve-/Reject-Verträge | geschützte synthetische Altbestandsfixtures |
| 200/204/400/404/417, CSRF/Host/Origin | bestehende MockMvc-/DOM-Verträge | reguläre geschützte Gesamtsuite |

Ein mit @Transactional umhüllter Test allein beweist weder realen Commit noch
Parallelität/Atomarität zwischen unabhängigen Transaktionen. Die bestehende
Rollbackfreigabe berechtigt NICHT zu committed Fixtures: Solche Tests benötigen
eine neue ausdrücklich genehmigte Strategie für wenige ausschließlich neue
synthetische Zeilen mit exakter ID-Zuordnung und separat erlaubter Entfernung.
Ohne diese Freigabe nur Rollback-/Mocknachweise ausführen und die fehlende
Commit-/Parallelitätsabnahme offen kennzeichnen. Keine Altzeile bereinigen.

## 5. Konkrete Entscheidungen vor Umsetzung

1. Verhalten bei bereits gekauftem Eintrag und historischem gekauft=NULL;
   Empfehlung: idempotente Antwort für true, NULL vorerst kontrolliert behandeln.
2. Mehrdeutige Reihe: keine erste Trefferwahl; explizite Ziel-ID/Bestätigung oder
   kontrollierter Konflikt. Bestehende legal doppelte Titel/Editionen berücksichtigen.
3. Reicht die heutige eine JVM als Garantiegrenze der Neuanlagenserialisierung?
   Andernfalls persistente Eindeutigkeit separat fachlich und migrationssicher planen.
4. Reduktion: Welche Bände dürfen auf ausdrücklichen Auftrag entfernt/ausgegliedert
   werden? Empfehlung bis Entscheidung: ablehnen; keine automatische Löschung.
5. Darf ein expliziter 0-Euro-Wert einen Altpreis ersetzen und wie werden fehlende
   Preise behandelt? Empfehlung Feldpräsenz getrennt von 0/NULL modellieren.
6. Freigabegrenzen für echte Commit-/Parallelitätsnachweise entsprechend Abschnitt 4.

## 6. Abgrenzung und Aufwand

Kein T03-Code in diesem Auftrag. Keine Schemaänderung für die vorgeschlagenen
Service-/Lock-/Rechenkorrekturen zwingend; mögliche Mehrinstanzconstraints erst
nach Entscheidung und eigenem Migrationsplan. Keine neue DB-Struktur als Shortcut.
M06-Verhalten bleibt unverändert. Statistik-Cents/YearMonth und sonstige T04-Bugs,
Loggingarchitektur T06, große Bereinigung T05 und UI-Neugestaltung bleiben getrennt.

Grobe Größenordnung nach Fachentscheidungen: Charakterisierung 0,5–1 Tag;
Kauftransaktion/Locks 1–2 Tage; Identifikation/Aggregate 1–2 Tage; Geld-/Anzahlregeln
1–2 Tage; reale Integrations-/Browserabnahme 1–2 Tage. Keine Terminzusage;
Mehrinstanz-/Migrationsbedarf erhöht Aufwand und Risiko. Kleine, testbare
Implementierungsaufträge in genau dieser Reihenfolge freigeben.
