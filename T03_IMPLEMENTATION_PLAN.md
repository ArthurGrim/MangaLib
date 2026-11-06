# T03 – Umsetzungsvorbereitung

Stand: 02.11.2026. T03-1/T03-2 implementiert und datenbankfrei geprüft;
maßgeblicher aktueller Nachtrag in Abschnitt 11. Abschnitte 1 und 7–10 beschreiben
die historische T03-0-Ausgangslage. T03-3 bis T03-6 bleiben offen.
T02-Abschlussentscheidung: siehe [T02_CLOSEOUT.md](T02_CLOSEOUT.md).
Keine DB-Verbindung oder Migration. Freigegebene fachliche Änderungen: Abschnitt 11.

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

Diese Ausgangsbefunde wurden zunächst statisch erhoben. Abschnitt 7 trennt die
inzwischen mit Mocks reproduzierten Abläufe von weiterhin fehlenden DB-Nachweisen.
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

Diese ursprüngliche Kurzliste wird durch die Beispiele, Varianten und Empfehlungen
in Abschnitt 9 konkretisiert. Historischer Entscheidungsstand; die inzwischen
verbindlichen D1–D6 und zusätzlichen Freigaben stehen in Abschnitt 11.

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

Kein produktiver T03-Code in diesem Auftrag. Keine Schemaänderung für die vorgeschlagenen
Service-/Lock-/Rechenkorrekturen zwingend; mögliche Mehrinstanzconstraints erst
nach Entscheidung und eigenem Migrationsplan. Keine neue DB-Struktur als Shortcut.
M06-Verhalten bleibt unverändert. Statistik-Cents/YearMonth und sonstige T04-Bugs,
Loggingarchitektur T06, große Bereinigung T05 und UI-Neugestaltung bleiben getrennt.

Grobe Größenordnung nach Fachentscheidungen: Charakterisierung 0,5–1 Tag;
Kauftransaktion/Locks 1–2 Tage; Identifikation/Aggregate 1–2 Tage; Geld-/Anzahlregeln
1–2 Tage; reale Integrations-/Browserabnahme 1–2 Tage. Keine Terminzusage;
Mehrinstanz-/Migrationsbedarf erhöht Aufwand und Risiko. Kleine, testbare
Implementierungsaufträge in genau dieser Reihenfolge freigeben.

## 7. T03-0 – tatsächlich charakterisierte Abläufe (02.11.2026)

### Ausgangspunkt und Nachweisumfang

Der Übergang wurde exakt geprüft: codex/t02-security-hardening, HEAD b7a2dbe,
keine Änderungen verfolgter Dateien, genau acht bekannte unversionierte
Prototypdateien. Danach ausdrücklich freigegebener Branch codex/t03-data-integrity
direkt auf b7a2dbe erstellt. T02-Branch und Prototypdateien unverändert erhalten.

Neue reine JUnit-Tests unter src/test/java/de/mangalib/service/:
- PurchaseFlowCharacterizationTest: echter Einkaufscontroller und echter
  EinkaufslisteService; Mock-Reihenservice und Repository-Mocks.
- BandIntegrityCharacterizationTest: echte Band-/Reihen-/Detailservices gegen
  Repository-Mocks; ein Wiederholungstest durchläuft den echten Kaufcontroller,
  Kaufmarkierungs-, Reihen- und Bandservice gemeinsam.
- T03Fixtures: ausschließlich synthetische Objektgraphen und Identitätsmap in RAM.
  Keine Datenbankkopie, Context-Erzeugung, Scraperinitialisierung oder externen Abrufe.

IST-Fehler werden mit erfolgreichen Assertions auf das tatsächlich vorhandene
Verhalten dokumentiert. Sie sind NICHT behoben. SOLL-Regeln stehen daneben im
Plan, keine roten/deaktivierten Tests. Mocks beweisen weder realen Teilcommit
noch Rollback, SQL-Locks, Isolation, Flush/Clear oder Fremdschlüsseldurchsetzung.
Die Annotationsprüfung belegt Konfiguration, keinen wirksamen Transaktionsproxy.

### Bestätigte IST-/SOLL-Matrix

| ID | Tatsächlicher Nachweis | Einordnung / empfohlene SOLL-Regel nach Freigabe |
|---|---|---|
| C01 | In beiden Kaufwegen wird der Reihenservice vor updateGekauft aufgerufen. Marker-Savefehler ergibt neutrales 500 nach bereits erfolgtem Bibliotheksaufruf. | Atomaritätsrisiko bestätigt, realer Teilcommit nicht gemessen. Gemeinsame Transaktion mit Rollback beider Änderungen. |
| C02 | Wiederholung derselben Kauf-ID ruft in beiden Wegen die Bibliotheksoperation und Markierung erneut auf, auch wenn gekauft bereits true ist. | Fehlender Wiederholungsschutz. In echtem Update-/Bandservicepfad wächst RAM-Bestand von 3 auf 4 auf 5 Bände; alte Bandwerte bleiben durch M06 erhalten. SOLL höchstens eine Übernahme. |
| C03 | Zwei Java-Aufrufer erreichen per deterministischer Barrier dieselbe Bibliotheksoperation, bevor einer gekauft markiert; beide Antworten 200. | Fehlende koordinierte Admission im Controller reproduziert, keine DB-Parallelität gemessen. Gemeinsame Sperr-/Transaktionsgrenze erforderlich. |
| C04 | Zwei Index- oder Titeltreffer: erster Repositorytreffer wird gewählt. Ein Indextreffer verhindert jede Titelprüfung. | Reihenfolge ist keine fachliche Identifikation. Mehrdeutigkeiten ohne Mutation zur Entscheidung vorlegen. |
| C05 | NULL-Index wird als 0 gesucht. Ein 0-Treffer mit anderem Titel wird verwendet; ohne 0-Treffer folgt exakte Titelsuche. | NULL ist kein gültiger Index 0. Fehlenden Index nicht erfinden; Titelmatch nur nach bestätigter Zuordnungsregel. |
| C06 | Optional.empty aus updateMangaReihe wird ignoriert: Markierung und 200 erfolgen trotzdem. | Verschwundene/nicht aktualisierte Zielreihe darf keinen Kaufabschluss ergeben. Ergebnis prüfen, gesamte Transaktion zurückrollen. |
| C07 | Gekauft false/true/NULL werden gleichermaßen übernommen; fehlender Kauf oder fehlende Kaufdetails beim Create ergibt 404 ohne Bibliothekswrite. | Fehlende Einträge korrekt behandelt; unbekanntes Kauf-Flag braucht Fachentscheidung. |
| C08 | Fehlende Reihendetails oder Anzahl führen im Updateweg zu 400, fehlender Verlag zu neutralem 500, jeweils vor Bibliotheks-/Markeraufruf. NULL-Einkaufspreis wird dagegen vom Controller weitergereicht. | Keine Ersatzbeziehungen/Nullpreise erfinden. Fachlich kontrollierte Bestandsablehnung vereinheitlichen; Mock-200 mit NULL-Preis ist kein Nachweis erfolgreicher realer Speicherung. |
| C09 | Neuanlage und M06-Erweiterung runden 0,01/−0,01/0,02 über drei neue Bände einzeln auf zwei Stellen: Summen 0,00/0,00/0,03. | Restcentverlust bzw. Überverteilung bestätigt. SOLL exakte Summe und deterministische Reihenfolge, ohne Altpreise nachträglich neu zu verteilen. |
| C10 | Preisänderung vorhandener drei Bände mit Anpassung 0,01/−0,01/0,02 wirft ArithmeticException (Division ohne Rundungsregel). Erster RAM-Preis ist vorher schon geändert, noch kein Band-save. | Nicht terminierende Division und Mutation vor Fehler bestätigt; kein DB-Teilstand behauptet. Preisplan erst vollständig validieren/berechnen, dann atomar speichern. |
| C11 | Reverse-Liste 3/2/1: Import "Band 1 href" landet auf stabiler ID 103/Bandnummer 3, Nummer selbst bleibt 3. | updateOrCreateBaende ordnet per Listenposition zu. SOLL unveränderliche Band-ID plus eindeutig validierte Nummer/Importzuordnung. |
| C12 | updateBand sucht dagegen stabil per ID auch bei Reverse-Liste. Fehlende Band-ID ist auf Serviceebene stiller No-op. | Stabile Einzelbandzuordnung korrekt; gewünschter Fehlervertrag bei verschwundener ID noch festlegen, nicht eigenmächtig ändern. |
| C13 | Unveränderte Anzahl mit reinem Metadatenpfad erhält individuelle Bandwerte ohne Bandsaves. M06-Append erhält sämtliche Alt-IDs, Preise, Lesestatus, NULL-Rereads und Flags und setzt neue Bände ungelesen. | Gewünschter Datenerhalt bestätigt. Keine neue Preis-/Reread- oder historische Reparaturregel einführen. |
| C14 | M06 weist Lücken, doppelte/NULL-Nummern, fremde Zuordnung und fehlende Details vor Mutation ab. | Korrekte Schutzgrenze beibehalten. Gesonderte Bestandskorrektur wäre eigenständiger Auftrag. |
| C15 | Reduktion von drei auf zwei deklarierten Bänden lässt alle drei Datensätze bestehen, berechnet 18,00 Gesamtpreis weiter aus drei Bänden und 9,00 Mittelwert aus deklarierter Anzahl 2. | Keine Löschung, aber inkonsistente Deklaration/Aggregate; Details-Lesestatus wird false. Reduktion bis Fachentscheidung vor Mutation ablehnen. |
| C16 | Reihenpreis 0 bedeutet unverändert, expliziter Einzelbandpreis 0,00 wird tatsächlich übernommen. | Uneinheitliche Null-/0-Semantik. Feldpräsenz, bewusstes Setzen und fehlender Preis getrennt behandeln. |
| C17 | Unbekannter Bandpreis erlaubt unveränderten Metadatenpfad, sperrt Finanzänderung/Append. Unbekannter Geschwisterpreis sperrt Einzelband-Finanzänderung vor Mutation. Direkte calculateGesamtpreis-Methode wirft bei NULL-Preis NPE; Anpassungssumme überspringt NULL. | T02-Schutz korrekt; direkte Summenmethoden kein allgemein nullsicherer Rechenkern. Keine NULL-als-0-Regel erfinden. |
| C18 | Reihen-/Detail-save wird vor späterem Detail-/Bandfehler erreicht. Bei Einzelbandoperation wird Band-save vor fehlgeschlagenem Reihen-save aufgerufen. Controller ohne gemeinsame Transaktionsannotation, updateBand ohne solche Annotation; Reihenoperationen und updateGekauft besitzen jeweils eigene Jakarta-Transaktionsannotation. | Reihenfolge und getrennte deklarierte Grenzen bestätigt. Transaktionsproxy, tatsächlicher Commit und Rollback müssen später separat geprüft werden. |

### Nicht nachgewiesen oder bewusst nicht ausgeführt

- Keine Aussagen über vorhandene produktive Daten oder tatsächliche Altbestands-
  Häufigkeiten; nur synthetische Werte. Keine DB-Verbindung irgendeines Profils.
- Keine realen Teilcommits, Lost Updates, Deadlocks oder Sperrwartezeiten. Zwei
  Threads an Mocks ersetzen keine zwei unabhängigen Datenbanktransaktionen.
- Kein neu implementierter Mutex, Lockadapter, PurchaseService, Centverteiler,
  Schema-/Constraint- oder Reduktionsalgorithmus. Es gibt noch keinen Beweis der
  zukünftigen Lösung; die Tests charakterisieren ausschließlich den Ist-Zustand.
- Keine Änderung an M06, T02-Guards, bestehenden Assertions oder MVC-Verträgen.
  Keine Browser-/DOM-Tests erforderlich: kein Frontendverhalten geändert.

## 8. Präzisierung des technischen Lösungsvorschlags

### Gemeinsame Kauftransaktion und korrekter Mutexumfang

Für den bestätigten lokalen Einzelinstanzbetrieb ist ein gemeinsamer JVM-Mutex
als zusätzliche Koordinierung tragfähig, sofern ALLE konkurrierenden Kauf-/
Neuanlagepfade denselben Singleton verwenden. Ein zweiter Prozess, eine andere
Anwendung oder freie SQL-Schreiber werden dadurch nicht geschützt. Ein Windows-
Einzelbenutzer kann trotzdem zwei Anwendungsinstanzen starten; eine solche
Mehrinstanzgarantie ist nicht stillschweigend enthalten. Rowlocks in der DB
bleiben für vorhandene Kauf-/Reihenzeilen sinnvoll.

Ein synchronisierter @Transactional-Service allein ist nicht ausreichend:
Spring kann die Transaktion vor dem Methodeneintritt beginnen und erst nach
Rückkehr committen. Der Java-Monitor kann dann schon vor dem Commit frei sein.
Auch ein nur im finally der transaktionalen Methode freigegebener Lock ist zu früh.

Empfohlenes späteres Muster (Konzept, NICHT implementiert):
1. Nichttransaktionale Orchestrierungsfassade als Singleton; unerwartete bereits
   aktive äußere Transaktion kontrolliert ablehnen, nicht unbemerkt beitreten.
2. Gemeinsamen begrenzten JVM-Lock vor Transaktionsbeginn erwerben.
3. Innerhalb dieses Locks TransactionTemplate.execute mit passendem JPA-
   TransactionManager ausführen; darin Kaufzeile sperren, gekauft/Zuordnung erneut
   prüfen, Zielreihe sperren, vollständigen Änderungsplan validieren, dann speichern.
4. Erst nach Rückkehr/Ausnahme von execute ist dessen Commit/Rollback abgeschlossen.
   Danach Lock im äußeren finally freigeben. Commitfehler nicht als Erfolg melden.
5. Nicht ungeprüft REQUIRES_NEW benutzen, wenn ein Aufrufer bereits andere Locks/
   Transaktionen hält: Suspendierung kann neue Wartezyklen erzeugen. Explizite
   Admission ohne Außentransaktion ist hier leichter nachvollziehbar.

Separate proxied @Transactional-Fachbean innerhalb einer unannotierten
Lockfassade wäre ebenfalls möglich, erfordert aber konsequente Proxyaufrufe
und dieselbe Prüfung äußerer Transaktionen. Template ist für diese lokale
Grenze expliziter. Keine Transaktion über Selenium/HTTP, Dialogauswahl oder
Clientwartezeit halten. HTTP-Ergebnisse erst nach fachlichem Commit erzeugen;
417 bleibt ein nicht schreibender vorbereitender Auswahlzustand.

Lockreihenfolge: gemeinsamer JVM-Lock → Kaufzeile → Zielreihen nach stabiler ID →
Bände nach stabiler ID. Einzelbandänderungen dürfen nicht zuerst den Band und
anschließend die Reihe sperren. Normale manuelle Neuanlage, Indexänderung und
sonstige passende Bibliothekswrites dürfen den erforderlichen gemeinsamen
Koordinierungspunkt nicht umgehen. Reentrancy nicht als Ersatz expliziter
Transaktionspropagation verwenden. Lockwartezeit/Abbruch und Konfliktantwort
vor produktiver Umsetzung testen.

### Identifikation, Berechnung und unveränderter Altbestand

Reihenidentifikation vor jeder Mutation mit eindeutigem Ziel; Index und Titel
sind Suchhilfen, keine automatisch eindeutigen DB-Schlüssel. Erst nach Lock erneut
prüfen. Gleichnamige Ausgaben mit anderem Format/Verlag dürfen nicht versehentlich
verschmolzen werden. Die bestehenden IDs bleiben unverändert.

Bände über ID adressieren; eine Importnummer darf nur bei verifiziert eindeutiger
Nummerierung 1..N auf einen vorhandenen Band abgebildet werden. Die M06-Regeln
für neue Bände bleiben unverändert. Keine automatische Sortierung mit anschließender
stiller Reparatur doppelter oder lückenhafter historischer Nummern.

Preisplan als reine BigDecimal-/Centberechnung ohne Double und ohne DB: bekannte
Zielmenge, Quotient in ganzen Centbeträgen, Vorzeichen separat beachten, Restcents
deterministisch nach bestätigter Bandnummer/ID verteilen. Bei ±0,01 bzw. 0,02 auf
drei neue Bände beispielsweise 0,01/0/0, −0,01/0/0 bzw. 0,01/0,01/0. Keine Neuberechnung
bestehender individueller Preise bei bloßer Metadatenänderung. Neupreis, explizites
0,00 und unbekannter Wert benötigen getrennte Präsenz-/Änderungsinformationen.

## 9. Gesammelte Fachentscheidungen vor T03-1 (noch NICHT beschlossen)

### D1 – Bereits gekauft und historisches gekauft=NULL

**Problem/Beispiel:** Ein Klick hat bereits Band 4 übernommen. Nach Reload oder
zweitem Tab führt dieselbe Einkaufs-ID heute zu Band 5. NULL könnte dagegen
unbekannten historischen Zustand bedeuten, nicht sicher „noch nicht gekauft“.

**Varianten:** (a) Wiederholung ohne Änderung neutral erfolgreich beantworten;
(b) kontrollierter Konflikt statt Erfolg; (c) erneut übernehmen (nicht empfohlen).
Für NULL: als offen behandeln oder bis bewusster fachlicher Klärung ablehnen.

**Empfehlung zur Freigabe:** true bleibt true und führt zu idempotentem HTTP 200
mit neutraler Meldung „Bereits übernommen“, ohne Bibliotheks-/Markerwrite.
Keinen nicht gespeicherten früheren Ziel-/Ergebnisdatensatz erfinden. NULL bis
expliziter Klärung kontrolliert ablehnen (Konfliktstatus, Vorschlag 409, benötigt
Vertragsfreigabe). false darf regulär gekauft werden. Falls eine bewusst erneute
Anschaffung gewünscht ist, neue Einkaufsposition anlegen. Vorhandene Daten werden
nicht automatisch ummarkiert oder als historisch nachweisbar rekonstruiert.

### D2 – Eindeutige Reihenidentifikation

**Problem/Beispiel:** Zwei gespeicherte Editionen heißen gleich oder tragen
denselben externen Index. Heute entscheidet der erste Repositorytreffer. NULL
kann außerdem einen unpassenden historischen 0-Index treffen.

**Varianten:** (a) immer explizite interne Ziel-ID wählen; (b) eindeutigen,
kompatiblen Indexmatch automatisch nutzen, sonst Auswahl; (c) auch exakten Titel
stets automatisch verwenden. (c) kann verschiedene Formate/Editionen vermischen.

**Empfehlung zur Freigabe:** Interne Ziel-ID ist maßgeblich, serverseitig unter
Lock erneut prüfen. Automatisch nur ein eindeutiger echter Indexmatch mit
übereinstimmenden Verlag-/Typ-/Formatreferenzen; sonst explizite Auswahl oder
kontrollierter Konflikt, KEIN erster Treffer. Ein Titelmatch ist nur ein Vorschlag,
insbesondere bei fehlendem Index. NULL nicht zu 0 umdeuten. Keine Bestandszeilen
zusammenführen oder neue Unique-Constraints über Titel/Index erfinden. Ein
Auswahldialog bzw. Konfliktpayload ist ein später ausdrücklich zu bestätigender
MVC-/UI-Vertrag; vorhandenes 417 bleibt Auswahl vor Neuanlage, ohne Schreiben.

### D3 – Garantiegrenze einer lokalen JVM

**Problem/Beispiel:** Zwei verschiedene Einkäufe möchten gleichzeitig dieselbe
noch nicht vorhandene Reihe anlegen. Kaufzeilenlocks sperren unterschiedliche
Zeilen; ein gemeinsamer Mutex in genau einer JVM kann diese Auswahl serialisieren.
Zwei gestartete MangaLib-Prozesse werden dadurch nicht gemeinsam gesperrt.

**Varianten:** (a) eine aktive Instanz als Betriebsgrenze plus gemeinsamer Mutex
und DB-Rowlocks; (b) echte Mehrinstanzgarantie mit persistentem Serialisierungspunkt
oder fachlich freigegebenem Eindeutigkeitsconstraint/Migration.

**Empfehlung zur Freigabe:** (a) für den vorgesehenen lokalen Betrieb. Mutex über
alle relevanten Kauf-/Neuanlagepfade vor Transaktionsbeginn bis nach Commit/
Rollback; vorhandene Kauf-/Reihenzeilen zusätzlich sperren. Keine Locks über
Scraper/Dialoge. Datenmodell bleibt erhalten. Mehrinstanz- und Fremdwriter-Schutz
nicht behaupten; (b) wäre ein eigener Architektur-/Migrationsauftrag. Der Mutex
ersetzt weder stabilen Identifikator noch atomaren Commit.

### D4 – Verringerung der Bandanzahl

**Problem/Beispiel:** Drei gespeicherte Bände, Formularanzahl 2: derzeit bleiben
alle drei bestehen, aber die deklarierte Anzahl und der Mittelwert wechseln.
Welche der vorhandenen IDs entfernt werden dürfte, ist fachlich nicht definiert.

**Varianten:** (a) Reduktion kontrolliert vor Änderungen ablehnen;
(b) ausdrückliche Bandauswahl und gesonderte Lösch-/Ausgliederungsfunktion;
(c) nur Zahl ändern und restliche Bände ignorieren (nicht empfohlen).

**Empfehlung zur Freigabe:** (a) in T03. Unverändert/Erweiterung bleiben zulässig
nach den bestehenden M06-Bedingungen. Bestehende inkonsistente Deklarationen nicht
automatisch bereinigen. (b) später mit eigener Datenerhaltungs-/Löschentscheidung;
niemals aufgrund von Listenposition oder „höchster Nummer“ automatisch löschen.

### D5 – Nullpreis, unbekannter Preis, explizites Leeren und Restcents

**Problem/Beispiel:** Ein Band kann tatsächlich gratis (0,00) oder preislich
unbekannt (NULL) sein. Reihenpreis 0 bedeutet heute unverändert, beim Einzelband
bedeutet 0 eine Änderung. Verteilung von 0,02 auf drei neue Bände ergibt heute
0,03, während 0,01 verloren geht.

**Varianten:** (a) Feldpräsenz/Änderungsabsicht trennen, bekannten 0-Wert zulassen;
(b) 0 weiterhin als generischen „unverändert“-Marker benutzen; (c) NULL pauschal
zu 0 normalisieren (nicht empfohlen). Restcents deterministisch nach bestätigter
Bandnummer/ID oder durch explizite manuelle Einzelverteilung vergeben.

**Empfehlung zur Freigabe:** (a); explizit gesetzte 0,00 dürfen den gewählten
Altpreis ersetzen. Fehlend/unberührt erhält den Altwert. Bewusstes Leeren eines
bekannten Preises vorerst ablehnen statt still NULL/0 zu speichern; unbekannte
Altpreise bei Metadatenänderungen erhalten. Finanzänderungen bei unvollständiger
Preisbasis kontrolliert sperren. Restcents nach aufsteigender bestätigter Bandnummer,
ID nur als stabiler Zusatzbeleg (Duplikate vorher ablehnen). Negative Beträge
symmetrisch verteilen; nur ausdrücklich betroffene/neue Bände, keine nachträgliche
Umverteilung alter individueller Preise. Summen exakt erhalten. Eingabevertrag
für „unverändert/0/leer“ und betroffene Zielmenge vor Umsetzung bestätigen.

### D6 – Spätere echte Commit-/Parallelitätsabnahme

**Problem/Beispiel:** Ein äußerer Test-Rollback kann verdecken, dass ein innerer
Kauf bereits separat committen würde. Zwei Mockthreads zeigen keine DB-Locks.

**Varianten:** (a) nur Mock-/Rollbacktests und verbleibende Nachweislücke;
(b) separate, ausdrücklich freigegebene Abnahme unabhängiger Transaktionen gegen
mangalib_test mit ausschließlich eigenen neuen synthetischen IDs und genau
begrenzter Entfernung dieser Zeilen; (c) eigene neue isolierte Abnahmedatenbank.

**Empfehlung zur späteren Freigabe:** (b), erst nach fertiger Implementierung und
konkretem Skript-/Testplan: unveränderte Guards, Vorher-/Nachher-Fingerabdrücke,
exakt verfolgte neu erzeugte IDs, keine Stammdaten-/Altzeilenänderung, keine
Schemaoperationen oder AUTO_INCREMENT-Resets. Cleanup muss separat und eng
freigegeben werden; eine bisherige Rollbackfreigabe reicht nicht. Falls diese
Entfernung nicht gewünscht ist, (c) gesondert vorbereiten. Dieser Plan enthält
KEINE neue Datenbank-/Cleanupfreigabe.

## 10. T03-0 – Prüfungsergebnis und nächster Implementierungsauftrag

Abschließend tatsächlich ausgeführt, alle Gradle-Aufrufe --offline --no-daemon
nach identischem vorherigem --dry-run:

| Testklasse | Bestandene Fälle |
|---|---:|
| PurchaseFlowCharacterizationTest (neu) | 24 |
| BandIntegrityCharacterizationTest (neu) | 29 |
| ReadingStatusExtensionTest (unverändert) | 40 |
| LegacyEditCharacterizationTest (unverändert) | 51 |
| WriteServiceSafetyTest (unverändert) | 38 |
| WriteMvcValidationTest (unveränderter Standalone-MockMvc mit T02-Filtern) | 98 |
| **Gesamt** | **280** |

0 Fehler, 0 übersprungen. 53 neue Fälle, 227 bestehende unverändert bestanden.
Offline-Kompilierung einschließlich der Testquellen erfolgreich. Frühere eigene
Testaufbaufehler (mehrdeutige JUnit-Typinferenz, seiteneffektbehaftete Neustubbung,
NULL-Zugriff im Fixturehelper) korrigiert, keine Produktionskorrektur und keine
Abschwächung von Assertions. Endberichte lokal ignoriert unter
.gradle/t03-characterization-20261102/. Kein clean, Buildartefakte/Manual-JAR
nicht ersetzt. Kein Spring-Boot-Vollkontext, keine DB, Anwendung, Browser,
Native-Probe, Scraper, Downloads/Installationen oder neue Abhängigkeiten.

**Noch vor einer späteren DB-Freigabe vorzubereiten (nach Entscheidung/Implementierung):**
- Fehler pro Persistenzstufe, einschließlich Fehler beim Commit selbst; kein
  gekauft=true bei fehlendem Updateergebnis; Wiederholungen nach 500 und 417.
- Neue Transaktions-/Mutexfassade: Lock vor Begin, Freigabe nach Commit UND Rollback,
  kein Beitritt zu Außentransaktion, keine Self-invocation-Lücke, Abbruch/Locktimeout.
- Gleiche/verschiedene Kauf-IDs, gleiche bestehende/neue Reihe, alle anderen
  relevanten Neuanlage-/Bandänderungspfade unter derselben Koordination.
- Entschiedene Idempotenz-/NULL-/Mehrdeutigkeits-/Reduktions- und Preisverträge als
  aktiv grüne SOLL-Tests ergänzen; vorhandene Charakterisierungsfehlerfälle dann
  ausdrücklich mit neuer Sollregel überführen, nicht still löschen/aufweichen.
- BigDecimal-Summenerhaltung einschließlich negativer Centbeträge, Feldpräsenz,
  unveränderlicher IDs und unveränderter Altbandwerte in allen Permutationen.
- Standalone-MockMvc-/DOM-Verträge nur soweit künftig Payload/UI geändert werden;
  vorhandene T02-Securityfilter aktiv lassen. Danach erst geschützte reale
  Commit-/Konkurrenz-/Flush-Clear-Abnahme mit eigener konkreter Freigabe.

Empfohlene Reihenfolge: D1–D6 bestätigen → T03-1 gemeinsame Kauftransaktion mit
Prüfung aller Serviceergebnisse → unmittelbar T03-2 Wiederholung/Koordination
(keinen ungeschützten Zwischenstand freigeben) → T03-3 stabile Zuordnung/atomare
Einzelbandaggregate → T03-4 Preisplan → T03-5 Reduktionsvertrag → T03-6 gestufte
Integration/Browserabnahme. Technische Voraussetzungen bei Bedarf gemeinsam
entwickeln, keine Schemaänderung ohne eigenständige Freigabe.
T03-0 abgeschlossen; T03-1 nicht begonnen. Kein Staging oder Commit.


## 11. Verbindlicher Umsetzungsnachtrag – T03-1/T03-2 (02.11.2026)

**Aktueller Status: IMPLEMENTED_DB_FREE_VERIFIED. Keine Datenbank-/Browserabnahme.**
Dieser Nachtrag ersetzt widersprechende historische Empfehlungen aus T03-0.
Er ist keine Freigabe zur Ausführung der unten geplanten Integrationstests.

### Fachentscheidungen und zusätzlich freigegebener Vertrag

- **D1:** `gekauft=true` ergibt 200 „Kauf bereits übernommen“, ohne Bibliotheks-
  oder Markierungsschreiben. `false` ist kaufbar; NULL ist 409 UNKNOWN_STATUS,
  ohne Normalisierung. Fehler führen nie zu einer bestätigten Übernahme.
- **D2:** Nur ein eindeutiger, kompatibler positiver Mangaindex erlaubt die
  automatische Zielzuordnung. Verlag/Typ/Format müssen übereinstimmen. NULL
  bleibt NULL, 0 wird nicht als Ersatz gesucht. Titel sind nur Kandidatenhinweis;
  Titelkonflikte und mehrere Indextreffer werden vor Änderungen abgewiesen.
  Keine neue umfangreiche Editionsauswahl. Normale Neuanlagen prüfen unter
  demselben Mutex Indexdubletten und gleichnamige gleiche Editionen erneut.
- **D3:** Genau eine aktive MangaLib-JVM. Gemeinsamer fairer JVM-Mutex vor
  Transaktionsbeginn, bis nach Commit/Rollback und Rückkehr des Managers gehalten.
  Kauf-Zeilensperre vor Zielreihen-Zeilensperre. Keine Netzwerk-/Scraperarbeit
  innerhalb der Grenze. Keine Behauptung einer Mehrinstanzgarantie.
- **D4:** Kauf hängt ausschließlich neue Bände an; keine Reduktion oder implizite
  Anzahlkorrektur. Inkonsistente Anzahl/Bestände blockieren den Kauf. Die bereits
  freigegebene direkte M06-Erweiterung bei eindeutigem tatsächlichem Präfix
  bleibt erhalten. Vollständige normale Reduktions-/Korrekturregeln bleiben T03-5.
- **D5 / Variante C:** Neue Kaufpreise und Anpassungen müssen bekannt und mit
  höchstens zwei relevanten Dezimalstellen gültig sein; explizit 0 ist erlaubt.
  Unbekannte Altbandpreise blockieren eine finanzielle Erweiterung, werden nicht
  auf 0 gesetzt. Kaufanpassungen werden exakt in ganzen Cent auf N neue Bände
  verteilt: ganzzahliger Quotient, vorzeichenrichtige Restcents nach aufsteigender
  bestätigter Bandnummer. Alte Einzelwerte bleiben erhalten. Globale Preis- und
  normale Bearbeitungspfade einschließlich ihrer bisherigen Charakterisierungen
  bleiben T03-3/4; keine allgemeine neue Pflichtpreissemantik im Frontend.
- **D6:** Spätere echte Commit-/Parallelitätstests nur mangalib_test, ausschließlich
  neue synthetische Zeilen, gesonderte ausdrückliche Freigabe. Keine automatische
  Bereinigung und keine dauerhaften Schreibtests im normalen Testtask.

Zusätzlich ausdrücklich freigegeben: Beide Kaufwege verwenden denselben 200-/409-
Vertrag. Fehlender Einkauf/Details bleiben 404, erforderliche Statusauswahl 417,
ungültige Eingaben 400. Konflikte liefern nur einen festen zulässigen Ergebniskürzel
im Header `X-MangaLib-Purchase-Outcome`; das Frontend ordnet feste Texte zu und
liest keine Fehlerbodys. Zulässige Konfliktcodes: UNKNOWN_STATUS,
AMBIGUOUS_SERIES, INCOMPATIBLE_SERIES, BAND_CONFLICT, INVALID_INVENTORY, BUSY.
Unbekannte Antwortcodes/Formate und 5xx halten den Kaufbutton bis Reload gesperrt;
keine automatischen Retries. Unklarer Transaktionsabschluss sperrt alle weiteren
koordinierten Schreiboperationen innerhalb dieser Coordinator-Instanz.

**Ausdrückliche Bandbereichsbestätigung:** Einkaufspositionen besitzen weiterhin
keine konkrete externe Bandidentität. Ein eindeutiges Ziel liefert zunächst
200 mit `message=Bitte Bände bestätigen`, `seriesId` als String und `firstBand`/
`lastBand` als JSON-Zahlen. Erst der separate ausdrückliche Bestätigungsklick
sendet diese drei Werte zurück. Unter dem Lock werden Reihe, lückenloser Bestand
1..N und exakter neuer Bereich N+1..Z erneut geprüft; vorhandene Nummern, andere
Serien-ID oder veralteter Bereich ergeben 409 BAND_CONFLICT. Ohne Bestätigung
keine Übernahme. Dies bestätigt die gewünschte fachliche Nummerierung, beweist
KEINE unbekannten externen Bandindices oder globale Editions-/Banddublettenfreiheit.

### Komponenten, Schreibpfade und Reihenfolge

`PurchaseService` bindet beide Controllerwege an dieselbe Operation:

1. `LibraryWriteCoordinator` erwirbt den Mutex (maximal 5 Sekunden Wartezeit).
2. Spring `TransactionTemplate` eröffnet eine REQUIRED-Transaktion (Timeout 30 s).
3. `EinkaufslisteRepository.findByIdForPurchase` lädt mit PESSIMISTIC_WRITE;
   D1-Prüfung erfolgt vor Bibliothekslesezugriffen oder weiteren Änderungen.
4. Eindeutige Zielprüfung; bei vorhandener Reihe zusätzlich
   `MangaReiheRepository.findByIdForPurchase`, erst danach Bandbestand lesen.
5. Vorschau/Konflikt/417 sind ohne Schreiboperationen. Bestätigter Kauf verwendet
   bestehende M06-/Anlageservices; alte Bände werden nicht positionsbasiert importiert.
6. Neue Band-IDs/Nummern und Beziehungen erneut prüfen, exakte Kauf-Cents verteilen,
   Reihenaggregate aus tatsächlichen bekannten Bandwerten berechnen.
7. Einkauf markieren und gemeinsamen JPA-Kontext flushen. Fehlende Serviceergebnisse
   oder Fehler nach Änderungen werden geworfen, nicht als Rückgabefehler committet.
8. Erst nach Rückkehr des Transaktionsmanagers einschließlich Completion/Cleanup
   gibt der Coordinator den Mutex frei und der Controller kann Erfolg liefern.

`LibraryWriteConfiguration` stellt einen Spring-Infrastruktur-Advisor VOR die
vorhandene Jakarta-Transaktionsberatung. Alle öffentlichen save/create/update/
delete-Methoden dieser bestehenden Services teilen dieselbe Grenze:

| Service | Erfasste relevante Pfade |
| --- | --- |
| MangaReiheService | beide save-Overloads, rohe save/create-Anlage, updateMangaReihe und einzelne Attributupdates |
| BandService | saveBand, createAndSaveBaende, updateOrCreateBaende, updateBand, Preis-/Lesestatusupdate, deleteById |
| MangaDetailsService | save/create/update/delete einschließlich Lesestatus |
| EinkaufslisteService | normale Anlage, save/delete und bestehende updateGekauft-Methode |
| EinkaufslisteItemDetailsService | save/delete der Einkaufsdetails |

Interne Selbstaufrufe liegen innerhalb des äußeren Aufrufs; aufgerufene andere
Service-Proxys treten in die bereits eigene Transaktion ein. `PurchaseService`
verwendet denselben Coordinator ausdrücklich. Reads und Autofill/Scraper bleiben
außerhalb des Mutex. Stammdatenverwaltungsservices erzeugen keine Bibliotheks-
Reihen/Bände und sind nicht Teil der neuen fachlichen Schreibkoordination.
Direkte Repositoryverwendung durch zukünftigen Anwendungscode würde diesen
JVM-Schutz umgehen und ist bei weiteren Schreibpfaden ausdrücklich zu prüfen.

Lock-/Querytimeout-Hints von 5000 ms sind gesetzt. Tatsächliches MySQL-/Hibernate-
Verhalten und die Wirksamkeit der Transaktionsfrist sind noch nicht real gemessen.
Keine neue Abhängigkeit, Tabelle, Spalte oder Dateninitialisierung.

### Wichtige Integrationsgrenze: bestehende äußere Transaktionen

Der Coordinator lehnt fremde aktive Transaktionen/Synchronisierungen vor Änderungen
ab. Sonst könnte der Controller Erfolg melden und der Mutex freigegeben werden,
bevor die äußere Transaktion beendet ist. Dies ist auch keine testabhängige Ausnahme.

Die bestehenden `ServiceTest`, `MvcContractTest` und `ReadingStatusIntegrationTest`
verwenden teilweise äußere `@Transactional`-Testgrenzen. Diese Schreibfälle sind
mit der neuen eigenen Commit-Grenze **nicht unverändert ausführbar**; ihre historischen
Ergebnisse sind kein aktueller Nachweis. Zusätzlich muss der frühere automatische
Titel-Fallback jetzt als freigegebener Konflikt und Indexkäufe mit separater
Bestätigung geprüft werden. Keine Integrationstests deaktiviert oder Assertions
abgeschwächt; diese Klassen wurden in diesem Auftrag nicht ausgeführt/geändert.
Vor einer Vollsuite muss T03-6 diese Teststruktur sicher anpassen. Nicht einfach
`@Transactional` entfernen: Das würde dauerhafte Schreibwirkungen erzeugen.

### Charakterisierung und datenbankfreie Nachweise

Historische T03-0-Reports bleiben unverändert. Die damaligen Controllerfälle
(Teilfehler, Wiederholung, Konkurrenz, erster Treffer, NULL->0, ignoriertes
Optional.empty, fehlende Details/Preise) wurden in `PurchaseServiceTest` als neue
Sollfälle mit denselben Fehlerauslösern weitergeführt. `PurchaseFlowCharacterizationTest`
prüft jetzt beide dünnen Controller, feste Ergebnisverträge und fehlerhafte
Bestätigungen; die alte doppelte echte Bandanhängung prüft nun genau eine
Übernahme. Die übrigen globalen Band-/Preis-Istfälle bleiben in
`BandIntegrityCharacterizationTest` erfolgreich reproduzierbar und sind NICHT
als behoben deklariert. M06-Altbandwertassertions und T02-Validierung bleiben erhalten.

Konkrete endgültige Testzahlen und ausgeführte Befehle: aktueller Nachtrag in
TESTING.md. Fake-Transaktionsmanager und echte Spring-AOP-Proxys beweisen die
Reihenfolge und Fehlerbehandlung, NICHT echte DB-Commits, Rollbacks, Rowlocks oder
persistierte Konkurrenzfreiheit. DOM-Tests führen ausschließlich jsdom/Fetch-Doubles
aus, keinen Browser und keinen HTTP-Listener.

### T03-6 – konkret noch freizugebender Prüfauftrag

1. Teststruktur vorab prüfen: bestehende rollbackbasierte Persistenz-/Lesetests
   erhalten. Käufe separat und ausdrücklich opt-in mit echten eigenen Transaktionen
   prüfen; nicht heimlich im Standardtestlauf committen. Reale Schreibservice-
   Transaktionen ohne äußere Testtransaktion erfordern dieselbe Freigabe.
2. Guards zuerst, ausschließlich mangalib_test/mangalib_test_user mit Passwort aus
   MANGALIB_TEST_DB_PASSWORD, SQL-init/DDL/Migrationen deaktiviert; vorher/nachher
   Daten-/Schemafingerabdruck. Keine manuellen/produktiven DB-Zugriffe.
3. Einmalige Laufkennung, neue synthetische Reihe(n), Bände, Einkauf und Details;
   IDs jeder tatsächlich angelegten Zeile erfassen, vorhandene Stammdaten nur lesen.
   Keine automatische Entfernung. AUTO_INCREMENT kann auch bei Rollback steigen.
4. Erfolg committen und außerhalb aller alten Persistence-Kontexte erneut laden:
   Zeilen, Summen, Beziehungen, alte Bandwerte, gekauft-Markierung gemeinsam prüfen.
5. Fehler nach Reihen-/Bandschreiben, bei Markierung und Flush gezielt injizieren;
   echten Rollback und unveränderte Ausgangszeilen aus unabhängiger Transaktion prüfen.
   Bei Commitverbindungsfehlern keinen Erfolg/Retry; Sperrverhalten nachweisen.
6. Zwei unabhängige Threads/EntityManager: gleicher Einkauf, beide Endpunkte,
   verschiedene Einkäufe in eine Reihe und Konkurrenz mit normaler Neuanlage.
   Mutex bis nach realem Commit/Rollback, korrekte Lockreihenfolge, genau eine Anlage,
   stale Bestätigung 409 und anschließend bewusste aktuelle Bestätigung prüfen.
7. Opt-in-Task und normalen Regressionstask getrennt prüfen. Erst nach Testanpassung
   und ausdrücklich begrenzter DB-Freigabe die regulären unveränderten Sicherheits-,
   Persistenz- und MVC-Assertions vollständig erneut ausführen.
8. Separate Browserfreigabe für wenige neue synthetische Daten: Vorschau, Cancel,
   Bestätigung, bereits gekauft, feste 409-Texte und unbekannten Erfolgszustand prüfen.

Offen bleiben T03-3 (globale stabile Bandzuordnung/Aggregatregeln), T03-4 (globale
Preisregeln und Double-Zwischenstufe der Einkaufserfassung), T03-5 (normale
Anzahlreduktion/-korrektur) und T03-6 (reale Abnahme). Keine automatische
Bereinigung/Normalisierung und kein Vorziehen von T04/T05/T06.

## 12. Nachtrag 04.11.2026 – T03-3 datenbankfrei umgesetzt

Status `IMPLEMENTED_DB_FREE_VERIFIED` im beschriebenen Implementierungsumfang;
keine echte Datenbankabnahme. Historische IST-Befunde bleiben oben erhalten.

| Pfad | Bisheriger Fehler / heutige Regel |
| --- | --- |
| BandService.updateOrCreateBaende | Repositoryposition durch BandInventory ersetzt: gespeicherte positive eindeutige ID, eindeutige Nummer 1..N, passende Reihen-ID; Importordinal wird ausschließlich über die geprüfte Nummer zugeordnet. |
| MangaReiheService.updateMangaReihe | Bei Bandimport/Finanzänderung Struktur vor Reihen-/Detailmutation prüfen. Reine Metadaten bleiben ohne automatische Bestandskorrektur möglich. |
| M06 appendBaende / validateExtension | Gleiche Strukturprüfung wiederverwendet; nur N+1..Z erzeugen, kein Speichern/Überschreiben vorhandener Bände. |
| BandService.updateBand | Gespeicherten Band über ID laden, Mitgliedschaft im geprüften Reihenbestand bestätigen; abweichende Nummer kontrolliert ablehnen. Kein implizites Umnummerieren. |
| PurchaseService.consistent | Gemeinsame Strukturprüfung statt separater Identitätsregeln; bestehende Preis-/Bestätigungs-/Konfliktprüfungen bleiben zusätzlich wirksam. |

BandInventory ist eine reine kleine Prüffunktion ohne Repository oder Transaktion.
Sie sortiert nur ihre eigene Map, niemals die Repositoryliste. Fehlende/mehrdeutige
IDs, Nummern oder Beziehungen werden nicht repariert. Neue Bände bekommen ihre IDs
weiterhin ausschließlich durch Persistierung; Create-Pfad ohne Altbestand unverändert.

Aggregate: Summe = tatsächliche Einzelpreise plus tatsächliche Einzelanpassungen;
gespeicherte Reihenanpassung ist keine Summenquelle mehr. Unbekannte Einzelwerte
werden bei verlangter Berechnung kontrolliert abgewiesen, nicht als Nullpreis
interpretiert. Metadatenbearbeitung löst keine Preisneuberechnung aus. Mittelwert
bei Einzelband-Finanzänderung verwendet tatsächliche Bestandsgröße; Reihenpreis-
änderungen berechnen ihn auch bei unveränderter Anzahl neu (2 Stellen HALF_UP).
Keine Änderung der Restcentverteilung. Deklarationsreduktion und ihre bisherigen
Inkonsistenzen bleiben ausdrücklich T03-5; keine historische Anzahlreparatur.
Lesestatusaggregation und explizite Massenoptionen bleiben wie M06/T02 erhalten.
Die bisherige Reread-Aggregation NULL->0 bei bewusster Einzelband-Rereadänderung
bleibt vorerst unverändert; keine neue fachliche NULL-Regel eigenständig gesetzt.

### Vorbereitung T03-6: Integrationsanpassungsmatrix

| Bestehende Klasse/Fälle | Sichere Vorbereitung / spätere Anpassung |
| --- | --- |
| ServiceTest.savesSeriesWithDetailsAndVolumes | Äußere Testtransaktion kollidiert mit Coordinator. Eigener Opt-in-Commitfall mit neuen synthetischen IDs und separat genehmigter Entfernung nötig. |
| MvcContractTest: HTML/JSON/Statistik, ungültige Requests | Lesende und vor Servicewrites abgewiesene Fälle können Rollback-Fixtures behalten. Assertions/Filter/Guards unverändert. |
| MvcContractTest: Anlage, Bearbeitung, Löschen, Kauf | Eigene Commit-Grenze erfordert getrennte Opt-in-Fälle; 417/Vorschau/Bestätigung/200/409 aktuell nachweisen. Nicht einfach @Transactional entfernen. |
| ReadingStatusIntegrationTest: Erweiterung, Einzelband-/Reihenänderung, Kauf, Anlage | Ebenfalls eigener Opt-in-Commitrahmen; Altband-Snapshots um URLs/Preise/IDs erweitern, unabhängiges Reload nach Completion. Historische Titel-Fallback-Kauffälle an freigegebene eindeutige Zuordnung anpassen. |
| PersistenceCompatibilityTest, RepositoryCompatibilityTest, TestDataPreservationTest | Direkte EntityManager-/Repository-Fixtures können weiterhin transaktional zurückrollen; keine koordinierten Servicewrites unter äußerer Testtransaktion ergänzen. |
| TemplateRenderingTest | Bisherige lesende Renderingprüfung ohne eigene Schreibserviceaufrufe beibehalten. |

Keine Integrationstestannotation entfernt, kein produktiver Testbypass und keine
automatisch dauerhaft schreibenden Tests hinzugefügt. Umsetzung der Commitfälle
erst nach eigener Teststrategie und mangalib_test-Freigabe. Echte Rowlocks,
Commit/Rollback und Konkurrenz sind weiterhin nicht nachgewiesen. T03-4: globale
Preisregeln/Double/Restcents; T03-5: Reduktion und Anzahlkorrektur; T03-6: echte
Integration und Browserabnahme. Ausgeführte Auswahl und Zahlen siehe TESTING.md.

## 13. Nachtrag 04.11.2026 – T03-4 / D5 Variante C

Aktueller Implementierungsstand ersetzt die historischen IST-Preisregeln aus
T03-0 und die ausdrücklich auf T03-4 vertagten Teile von Abschnitt 12.
Keine Datenbankabnahme, keine Bestandsnormalisierung.

### Inventar und umgesetzte Preisverträge

| Pfad | Historisches Problem | Aktuelle Regel |
| --- | --- | --- |
| Neue Reihe / createAndSaveBaende | Fehlender Preis wurde zu 0; Drittel separat gerundet | Pflichtpreis, Importpreise vollständig vorab validiert, Anpassung exakt verteilt. |
| M06-Erweiterung / appendBaende | Restcentverlust bei neuen Bänden | Nur neue Nummern N+1..Z bekommen die neue Verteilung; Altpreise, IDs, Lesestatus und Rereads bleiben erhalten. |
| Normale Reihenbearbeitung | 0 als Keep-Sentinel, Mittelwert als möglicher Massenpreis | Abwesende Felder bleiben unverändert; explizite 0 ist Preis. Nur ausdrücklich ausgewählte Preis-/Anpassungsaktion verteilt auf vorhandene Bände. |
| Einzelband / updateBand | Leeres Feld bedeutete Keep; Browser rundete Eingaben | Abwesend bleibt Keep; Leeren eines bekannten Preises wird abgewiesen. Identischer historischer Wert wird nicht neu gesetzt. Neue Beträge müssen centgenau sein. |
| Einkaufserfassung | BigDecimal -> Double -> BigDecimal | Controller und Service reichen BigDecimal direkt weiter; kein Double-Zwischenschritt. |
| PurchaseService.finish | Eigener Restcentalgorithmus | Gemeinsamer Money-Rechenkern, Zielmenge ausschließlich neue bestätigte Kaufbände. |
| Direkte Geldsetter | Einzelne Methoden änderten nur Reihenaggregate bzw. Bandanpassung | Preis-/Anpassungssetter koordinieren Bandwerte und Aggregate gemeinsam; Gesamtpreis-Setter darf nur die tatsächliche Summe bestätigen, keine historischen Preise erfinden. |

Money ist eine reine Java-Komponente ohne Spring/Repository/HTTP. Sie validiert
bekannte Beträge centgenau mit UNNECESSARY, technische Grenze abs(Betrag) <
100.000.000 Euro, maximale Verteilungszahl 10.000. Zusätzliche ausschließlich
nachgestellte Nullen sind zulässig, echte Subcentwerte nicht. Verteilung über
ganzzahlige Cents: Quotient plus signierter Rest auf aufsteigende geprüfte
Bandnummern. 20/3 = 6,67/6,67/6,66; -0,02/3 = -0,01/-0,01/0,00. Mittelwert
bleibt ausdrücklich 2 Stellen HALF_UP; er rekonstruiert keine Altpreise.

BandService plant neue Preise und Verteilung vor dem ersten Bandsave;
MangaReiheService prüft bei Finanzänderung/Erweiterung zusätzlich die spätere
Gesamtsumme vor Reihen-/Detailmutation. Einzelbandänderungen prüfen vollständige
Geschwistergrundlagen und Aggregatsumme vor ihrer Mutation. Ungültige Importpreise
sind kein Auftrag für einen stillen Ersatzpreis. NULL-Grundlagen sperren finanzielle
Operationen; reine Metadaten-/Status-/Rereadänderungen bleiben möglich.

### Kompatible HTTP-Erweiterung und bewusst geänderte Eingaberegeln

- Reihenbearbeitung: fehlende Geldfelder -> Java-null für unverändert. Vorhandene
  Geldfelder, einschließlich "0", sind ausdrückliche Änderungen. Die Requestschicht
  erzeugt priceEdited/adjustmentEdited ausschließlich aus diesen JSON-Feldern und
  entfernt gleichnamige importierte Steuerwerte. Kein numerischer Keep-Sentinel.
- Zwei standardmäßig ausgeschaltete Checkboxen im Reihenformular übertragen
  Preis bzw. Anpassung nur nach bewusster Auswahl. Bei Erweiterung muss der Preis
  für neue Bände ausdrücklich gesetzt oder durch vollständige gültige Importpreise
  bekannt sein. Ein angezeigter Durchschnitt allein ist kein Preisauftrag.
- Neuer Reihenpreis ist verpflichtend; fehlend/null/leer wird vor Servicewrites
  abgewiesen. Optionale neue Gesamtanpassung bleibt ohne Angabe 0.
- Bandbearbeitung: abwesend = unverändert; null/leer nur bei bereits unbekanntem
  Wert zulässig. Bekannten Preis bewusst zu leeren ergibt 400. Historisch identische
  bekannte Werte werden unverändert gelassen. Browser sendet Dezimalstrings ohne
  Number.toFixed-Rundung, auch bei Anzeige historischer Genauigkeit.
- HTTP-Erfolgspayloads unverändert. Preisfehler laufen über die bestehende neutrale
  400-/Dialogbehandlung; keine Rohfehler, kein Retry, CSRF/Origin/Host unverändert.
- Interne Geldsignaturen EinkaufslisteService.saveEinkaufslisteItem und der bislang
  ungenutzte Reihen-Einzelpreissetter verwenden jetzt BigDecimal statt Double.
  Aufrufstellen/Mockverträge angepasst, keine neue API-Endpunktfreigabe.

Die gemeinsame JVM-Koordination bleibt außerhalb der Transaktion bis zu deren
Abschluss bestehen. Keine neue unabhängige Schreibtransaktion, kein Testbypass.
Alle neuen Finanzpfade liegen in den bereits koordinierten save/update-Methoden.

### Abnahmegrenzen und nächster Schritt

Reale DECIMAL-Speicherung, Flush/Clear/Reload, Commit/Rollback nach Bandsave oder
Aggregatfehler, parallele Einzelbandpreise und Kauf-/Bandkonkurrenz fehlen weiterhin.
T03-6 muss die Integrationsanpassungsmatrix aus Abschnitt 12 beibehalten und gezielt
um diese Preisfälle sowie historische NULL-Werte erweitern. Keine äußere
@Transactional-Annotation entfernen. Echte Commitfixtures, ihre Entfernung und
Browserabnahme brauchen eigene Freigaben. Spätere manuelle Prüfung: unverändertes
Formular, explizite 0, Leeren, Centfehler, Anpassung negativ, Erweiterung mit
ausdrücklich bestätigtem Preis, erneutes Laden und unveränderte Altbände.
T03-5 Anzahlkorrektur/-reduktion, T04-Statistik und Reread-/Historienregeln bleiben
unverändert offen. Historische Evidenzen, Manual-JARs und undici-Fix erhalten.

## 14. Nachtrag 04.11.2026 – T03-5 / D4 Variante A

### Ist-/Soll-Matrix und Operationsentscheidung

| Pfad | Früherer Fehler / neue Regel |
| --- | --- |
| Neuanlage | Weiter 1..Z mit Pflichtpreisen und exakten Restcents; keine Bestandskorrektur. |
| Unveränderte Anzahl | Geprüfter tatsächlicher Bestand; Metadaten ohne Bandanlage/-speicherung. Eine unveränderte zu hohe Deklaration erzeugt keine fehlenden Bände. Finanz-/Bandimporte sind dabei bis zur ausdrücklichen Korrektur oder Erweiterung gesperrt. |
| Ziel unter tatsächlicher Anzahl | Vor Änderungen kontrolliert ablehnen. C15 erzeugt keine falsche Deklaration oder Mittelwert mehr. |
| Ziel exakt tatsächliche Anzahl, Deklaration abweichend | Reine Korrektur: ausschließlich anzahlBaende speichern, ohne Bandsaves, Detailsave, Timestamp-, Preis-, Lese- oder Rereadänderung. |
| Ziel zwischen tatsächlicher und alter deklarierter Anzahl | Ohne ausdrückliche Bandanlage ablehnen; keine Teilkorrektur. |
| Ausdrückliche Erweiterung | Nur N+1..Z über M06-Append; auch bei abweichender Deklaration erlaubt. |
| Direkter Anzahlsetter | Nur unveränderte Anzahl oder reine Korrektur, keine implizite Bandanlage. |
| Rohe Entity-Speicherwege | Vorhandene Reihe nur mit passender tatsächlicher Anzahl speichern; keine Umgehung über save/create(entity). |
| Kauf | Bestehende bestätigte Bereichslogik unverändert; Deklarationsabweichung bleibt Konflikt, keine Korrektur als Kaufnebenwirkung. |

BandInventory.operation verwendet dieselbe geprüfte ID-/Nummern-/Reihenmap wie
T03-3. Ziele außerhalb 1..10.000, leere/inkonsistente Bestände und Reduktionen
unter tatsächlichen Bestand werden abgewiesen. Kein Umnummerieren, Reparieren,
Löschen oder Ausgliedern. BandService weist auch direkte verkleinernde Zielmengen
vor jeder Mutation ab. Alle Schreibwege bleiben im vorhandenen Coordinator.

### Kleine kompatible Requestergänzung

Optionales JSON-Boolean extendVolumes unterscheidet ausdrücklich gewünschte Anlage
von einer Deklarationskorrektur. Importierte gleichnamige Steuerwerte werden
entfernt; nur das geprüfte Top-Level-Feld erzeugt die interne Absicht.
Im Formular ist „Neue Bände ausdrücklich ergänzen“ standardmäßig ausgeschaltet
und beim Öffnen zurückgesetzt. Es sendet false ohne, true mit Auswahl. Die
vorhandenen Preisoptionen bleiben unverändert; Erweiterungen brauchen weiterhin
bekannte neue Preise. Keine neue HTTP-Erfolgspayload oder Fehlerbody-Auswertung.
Fehler verwenden die vorhandene neutrale 400-Dialogbehandlung ohne Retry.

Für bisherige interne Aufrufer ohne Feld bleibt eindeutig neues Wachstum über
sowohl tatsächliche als auch deklarierte Anzahl kompatibel. Bei abweichender
Deklaration und mehrdeutigem Ziel ist ausdrücklich true erforderlich; false kann
nie eine Anlage auslösen. M06-Tests kennzeichnen Erweiterungsabsicht ausdrücklich.
Ein true ohne tatsächlich neue Bände wird abgewiesen. Kaufbestätigung bleibt die
vorhandene eigene fachliche Absicht und benötigt keinen neuen Kaufdialog.

Eine reine Korrektur wird getrennt von weiteren Änderungen gespeichert: andere
Titel/Referenzen, Flags, Lese-/Rereadwerte, neue URLs, Preisoptionen, Massenoptionen
oder Bandimportdaten werden dabei nicht stillschweigend mitübernommen. Solche
Kombinationen sind kontrolliert gesperrt. Fehlende optionale URL-/Sammelbandwerte
behalten ihre vorhandene Keep-Semantik. Das Formular erläutert die getrennte Aktion.

### Aggregate und verbleibende Nachweise

Eine reine Korrektur berechnet keine finanziellen Aggregate neu, auch nicht bei
historischen NULL-Preisen. Gespeicherte Gesamt-/Mittelwerte werden dabei unverändert
erhalten. Ein bereits historisch falscher Mittelwert ist deshalb kein Nachweis für
eine erfolgreiche finanzielle Reparatur: diese muss separat mit vollständigen
Preisgrundlagen beauftragt werden. Alle regulären Finanzberechnungen verwenden
weiter die tatsächlichen Bände nach T03-3/4. Kein Ersatzpreis wird erfunden.

T03-6 ergänzt die Matrix aus Abschnitt 12 um:

1. Korrektur zu hoher/zu niedriger Deklaration und Reload nach tatsächlichem Commit;
   identische IDs, Fremdschlüssel, URLs, Zeitwerte, Preise, NULLs, Lesestatus und Rereads.
2. Reduktions-/Teilkorrekturablehnung bei zusätzlicher Preis-/Metadatenabsicht ohne
   dauerhafte Änderungen; keine zusätzlichen Bandsaves oder Deletes.
3. M06-Anlage bei abweichender Deklaration mit ausdrücklicher Erweiterung und
   unveränderten Altbänden, exakten Restcents und konsistenten neuen Aggregaten.
4. Konkurrenz Korrektur/Erweiterung/Kauf unter gemeinsamem Mutex und Commitrahmen;
   veraltete Kaufbereiche und Deklarationskonflikte ohne Bibliothekswrites.
5. Fehler vor Reihensave bzw. beim Commit: echten Rollback außerhalb der Transaktion
   nachweisen. Die neue eigene Commitgrenze nicht durch äußere Testtransaktionen umgehen.
6. Browser: Korrektur ohne Erweiterungs-/Preisoption, bewusstes Anhängen, negative
   Fälle, klare Dialogmeldung und kein automatischer Retry; Bestände neu laden.

Keine Integrationstestannotation entfernt, kein neuer automatisch committender
Test hinzugefügt. Diese Nachweise brauchen separate mangalib_test- bzw. manuelle
Freigabe mit nachverfolgbaren neuen synthetischen Daten, keine automatische Bereinigung.

## 15. T03-6A – geschützte Integration vorbereitet (04.11.2026)

Methodenvollinventar: [T03_INTEGRATION_INVENTORY.md](T03_INTEGRATION_INVENTORY.md),
13 Boot-Testklassen / 75 Testmethoden (Parameterfälle zusammengefasst), inklusive
Lifecycle-Fixtures, Coordinator-Konflikten, erlaubten Rollbackfällen und jeweiliger
späterer Freigabe. LegacyEditIntegrationTest und der positive Bandfall in
RequestLimitsIntegrationTest benötigen ebenfalls eine eigene Commitstruktur.
Bestehende Tests wurden weder deaktiviert noch ihre Transaktionsannotationen entfernt.

### Transaktionsbefunde / Nachweisgrenzen

- LibraryWriteCoordinator: fairer JVM-Mutex vor TransactionTemplate (30 s), keine
  fremde aktive TX/Synchronisation, Nested Calls nur bei selbst gehaltener aktiver TX.
  tryLock 5 s; Freigabe erst nach Rückkehr des Managers einschließlich Cleanup.
- LibraryWriteConfiguration: Infrastrukturadvisor HIGHEST_PRECEDENCE+10 vor
  Standard-Transaktionsadvisor (LOWEST_PRECEDENCE). Öffentliche save/create/update/
  delete-Methoden der fünf Schreibservices werden koordiniert; interne Selbstaufrufe
  bleiben im äußeren Rahmen. Controller enthalten keine Repository-/EM-Writewege.
- Jakarta-Annotationen verwenden bestehendes REQUIRED-Verhalten innerhalb der
  Coordinator-TX. PurchaseService delegiert beide Käufe in denselben Rahmen;
  Einkauf-Rowlock vor Zielreihen-Rowlock, gebundene Queryparameter, 5000-ms-Hints.
  BandRepository besitzt KEINEN zusätzlich bewiesenen Bandrowlock. Einzelbandwrites
  werden innerhalb derselben JVM über den gemeinsamen Mutex serialisiert.
- Checked/runtime Workfehler verhindern Commit; afterCompletion und Managerfehler
  werden konservativ behandelt. UNKNOWN/fehlender Completion oder Begin-/Commit-/
  Rollback-/Cleanupfehler sperren weitere Writes, keine automatische Wiederfreigabe.
  HTTP-Erfolg erst nach Service-Rückkehr. Kein Versprechen für zweite JVM/externe
  Repositoryschreiber; keine Locks/Netzwerkarbeit als Ersatz für diese Begrenzung.
- Vorhandene Fake-/AOP-Tests beweisen Aufruf-/Proxy-/Completionreihenfolge und
  Sperrverhalten, NICHT MySQL-Locks, echte Commitdauer, DECIMAL-Reload oder realen
  Rollback. Diese Nachweise sind erst nach neuer DB-/Commitfreigabe möglich.

### Getrennte erste Opt-in-Stufe

Source-Set t03Integration / Task t03CommitTest, keine Kopplung an test/check/build.
Vorhandene test-Ressourcen und TestContext-Factory liegen auf seiner Runtime-
Classpath; ausschließlich Profil test, keine Guardänderung oder zweite DataSource.
Zwei explizite Taskproperties, neue UUID-Verzeichnisreservierung vor Testworkerstart,
zusätzliche Kontextinitialisierungssperre und vorhandener DB-Guard vor JPA schützen
vor versehentlichem Start. Kein gespeicherter Freigabetoken als Ersatz für die
noch erforderliche Eigentümerfreigabe.

T03RunJournal persistiert nur UUID, feste Phasen und tabellengebundene ID-Versuche,
T03Preservation vergleicht alle alten Zeilen und Schema ohne Gleichheitsforderung
für absichtlich neue Zeilen. T03ServerRights prüft SHOW-GRANTS-Ergebnisse konservativ
auf ausschließlich eigene Schema-DML-Rechte; Rollen/DDL/GRANT OPTION = STOP.
Kein Cleanup. Drei vorbereitete Fälle in T03CommitAcceptanceTest: Commit/Reload,
Rollback nach Bandanlage, gleicher bestätigter Kauf in zwei Threads. Nur kompiliert.

Vollständige fachliche E1–E6-Matrix, Grenzen der Journal-/Snapshotrekonstruktion,
exakte dauerhafte Fixturemengen, sichere Befehle, gestufte Freigaben und T03-6C-
Browsercheckliste: [T03_6B_APPROVAL.md](T03_6B_APPROVAL.md).
Die erste Stufe ersetzt ausdrücklich NICHT alle alten HTTP-Schreibtests oder die
vollständige T03-6B-Matrix. Deren konkrete Restfälle sind dort benannt und vor
erweiterter Freigabe als zusätzliche Opt-in-Tests vorzubereiten.
Eine ungefilterte Suite bleibt bis zur sicheren Anpassung der C-Methoden gesperrt.
DOM-Verhalten in 6A unverändert; keine neue manuelle Paketierung/Browserausführung.
