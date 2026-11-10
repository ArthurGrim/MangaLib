# M01-0 – Einkaufslisteneinträge bearbeiten

## Ausgangspunkt und Umfang

Branch `codex/m01-shopping-edit`, angelegt auf
`3ad31dbeffebd3f88f035c04189798a5628d687d`. Direkt danach gleicher HEAD,
leerer Index, keine verfolgten Änderungen, acht historische unversionierte
Windows-Experimente. Lokaler `main` lag sieben Commits vor dem lokal bekannten
`origin/main`. M01 ergänzt die Einkaufsbearbeitung. T03 bleibt abgenommen,
M06 schützt vorhandene Bände, T02 bleibt Sicherheitsgrundlage. M05
(Umbenennung des eBay-Preises), T04 (Monatsgruppierung/Statistik), Importumbau
und produktive Datenpflege sind getrennt. Maßgeblich sind AGENTS, ROADMAP
M01, TESTING, MANUAL_TESTING sowie die Abschlussnachweise von T02 und T03.

## Nachgewiesener Datenfluss

Fundstellen beziehen sich auf den genannten HEAD.

| Pfad | Aktuelle Implementierung |
| --- | --- |
| Anlegen | `einkaufsliste.html:225–400` und `einkaufsliste.js:98–300` erfassen Formularwerte und optional Importmetadaten. POST `/addToEinkaufsliste`, `EinkaufslisteController.java:145–160`, bindet `WriteRequests.Shopping.from` (`WriteRequests.java:66–85`). `EinkaufslisteService.saveEinkaufslisteItem` (173–266) löst Stammdaten auf, berechnet die Summe und speichert Root und Details in einer koordinierten Transaktion. |
| Anzeigen und Verwalten | GET `/einkaufsliste`, Controller 68–127, lädt Monatsfenster über Service 65 und `EinkaufslisteRepository.java:22`. Das Template ab 118 stellt Datum, Stammdaten, Titel, Menge und Preise dar. `gekauft` steuert die CSS-Markierung. Anlegen, Löschen und Kaufen sind vorhanden. Ein Editor und ein JSON-Leseendpunkt für seine Feldwerte fehlen. Nullable Datum kann aus der datumsgefilterten Übersicht fallen. |
| Löschen | `einkaufsliste.js:4–36` bestätigt und sendet DELETE `/items/{id}`. Controller 162–173 und Service 99 prüfen die Einkaufs-ID und löschen über das Repository. Root/Details sind per Cascade gekoppelt. Bibliotheksbände sind nicht verknüpft und werden dabei nicht gelöscht. |
| Kauf in vorhandene Reihe | Beide POST-Routen `/buyItemUpdate` und `/buyItemCreate` führen im Controller ab 175 in dieselbe Kaufoperation. JS ab 415 und 531 zeigt die Vorschau und sendet Reihen-ID und Nummernbereich erst bei Bestätigung. `PurchaseService.java:53–79,108–131` sperrt zuerst den Einkauf, dann die eindeutig über positiven Index gefundene Reihe und prüft Verlag/Typ/Format, Inventar und aktuellen Bereich. Titel allein ist ein Kollisionshinweis. Neue Bände werden angehängt. |
| Kauf als neue Reihe | `PurchaseService.java:80–105` fordert Status an, prüft Detailbeziehung und erstellt den Graphen mit Titel, Index, Edition, Preisen und vorhandenen Details. Der Status stammt aus dem Dialog (`einkaufsliste.js:458–515`). Ohne Sammelbandreferenz greift der bestehende Standardwert 1. |
| Kaufabschluss und Anzeige | `PurchaseService.finish` (134–161) verteilt Anpassungen nur auf neue Bände, berechnet Reihenaggregate, setzt den Einkauf auf `gekauft=true` und flusht gemeinsam. Der Einkauf bleibt bestehen. `true` führt beim Replay zu `ALREADY_PURCHASED`, NULL zu `UNKNOWN_STATUS` (55–59). Die Monatsstatistik fragt gekaufte Zeilen nach Menge und Gesamtpreis ab (Repository 41–44). Der bestehende Löschweg kann auch diese Kaufaufzeichnung entfernen. |

`EinkaufslisteItemDetailsService.java:26–40` und das Detailrepository
speichern bzw. lesen Details. `security.js:68–125` verarbeitet feste
Ergebnisverträge, sperrt Schaltflächen bei ungewissem Ergebnis und sendet
über den CSRF-Helfer. T02-Requestgrenzen, Loopback, Host/Origin,
Session-CSRF und neutrale Fehlerantworten gelten für neue Routen ebenfalls.

## Vollständige Feldinventur

Mapping aus `EinkaufslisteItem.java:17–69` und
`EinkaufslisteItemDetails.java:15–52`. „Optional“ bezeichnet hier die
statisch sichtbare Modell-/Mapping-Semantik, keine Aussage über die
tatsächliche DDL aller Installationen. Abweichende Altwerte sind möglich.
Vorhandene Werte werden bei ausgelassenen Feldern nicht verändert.

| Root-Feld · Typ · Spalte | Ist, Validierung und Verwendung | Empfehlung für ungekauft |
| --- | --- | --- |
| `id` · Long · id | Generierter Primärschlüssel, nicht NULL, Grundlage für UI-Aktionen, Locks und Detail-ID. | Unveränderlich. |
| `mangaIndex` · Integer · manga_index | Optional, neuer Wert positiv. Formularfeld, separat vom Titel gespeichert. Primärer Kauf-Zuordnungsschlüssel. | Bedingt editierbar, Änderung oder explizites Leeren sichtbar machen. |
| `verlagId` · Verlag · verlag_id | Dropdown, Anlage verlangt vorhandene Referenz. Editionsprüfung und Kopie bei Neuanlage. | Bedingt, gültige Referenz und Hinweis auf geänderte Kaufzuordnung. |
| `typId` · Typ · typ_id | Wie Verlag, eigener Fremdschlüssel. | Wie Verlag. |
| `formatId` · Format · format_id | Wie Verlag, eigener Fremdschlüssel. | Wie Verlag. |
| `titel` · String · titel | Sichtbar, bei Anlage nicht leer und höchstens 255 Codepoints. Titelkollision verhindert automatische Zuordnung ohne eindeutigen Index. | Editierbar mit Prüfung. |
| `anzahlBaende` · Integer · anzahl_baende | Sichtbar, Anlage 1–10.000. Deklarierte Kaufmenge ohne eigene Einkaufs-Bandzeilen. | Editierbar; gemeinsame Geldprüfung. |
| `preis` · BigDecimal · preis | Pflichtwert bei Anlage, DECIMAL-Mapping 10,2, sichtbarer Basispreis je Band. NULL im Altbestand bedeutet unbekannt. | Editierbar; explizites Leeren gesondert entscheiden. |
| `gesamtpreis` · BigDecimal · gesamtpreis | Abgeleitet, 10,2, angezeigt und statistisch genutzt. Nicht als Basis in die Bibliothek kopiert. | Nur berechnet. |
| `aenderungGesamtpreis` · BigDecimal · aenderung_gesamtpreis | 10,2, signierte Gesamtdifferenz. Formular zeigt Feld bei eBay-Flag, sonst sendet es 0. Anlage-Service behandelt fehlende Anpassung als 0, Kauf lehnt historischen NULL-Betrag ab. | Bedingt. Flag-Umschaltung und Leeren ausdrücklich entscheiden. |
| `erscheinungsdatum` · LocalDate · erscheinungsdatum | Optionale Datumswahl, sichtbar als Tag/Monat, bestimmt Gruppierung und Statistik. Kein Kauf- oder Transaktionsdatum. | Editierbar; Sichtbarkeit bei bewusstem Leeren klären. |
| `istEbayPreis` · Boolean · ist_ebay_preis | Checkbox bei Anlage, optional im Mapping. Steuert Preisanpassungsfeld. Bei Neuanlage übernommen; Kaufabschluss setzt Reihenflag bei nichtnullwertiger Anpassung ungleich 0. | Bedingt, keine Umbenennung oder implizite Löschung. |
| `istVergriffen` · Boolean · ist_vergriffen | Checkbox, optional im Mapping, bei Kauf-Neuanlage übernommen. | Optional editierbar, NULL und false getrennt. |
| `gekauft` · Boolean · gekauft | Initial false; NULL ist unbekannt. Gekauft-Markierung und Statistik, Kauf setzt true. | Schreibgeschützt. Keine Rücknahme durch Edit. |
| `einkaufslisteItemDetails` · Detailentity · inverse Beziehung | Lazy One-to-one, Cascade und orphanRemoval. Detail fehlt möglicherweise historisch. | Bestehenden Graph gezielt ändern; keinen Ersatzgraphen automatisch anlegen. |

| Detailfeld · Typ · Spalte | Ist, Validierung und Verwendung | Empfehlung |
| --- | --- | --- |
| `id` · Long · id | Shared Primary Key mit Einkauf, durch `@MapsId` gebunden. | Unveränderlich. |
| `einkaufslisteItem` · EinkaufslisteItem · id | Besitzende 1:1-Beziehung, beim Kauf als neue Reihe auf passende IDs geprüft. | Unveränderlich. |
| `statusDe` · String · status_de | Optionaler Importstatus der deutschen Ausgabe. | Zunächst lesen/erhalten. |
| `anzahlBaendeDe` · Integer · anzahl_baende_de | Optionaler Importwert, Service entfernt „+“ und parst Integer. Keine Kaufmenge. | Zunächst lesen/erhalten. |
| `statusErstv` · String · status_erstv | Optionaler Importstatus der Erstveröffentlichung. | Zunächst lesen/erhalten. |
| `herkunft` · String · herkunft | Optionaler Importtext. | Zunächst lesen/erhalten. |
| `startJahr` · Integer · start_jahr | Optionaler geparster Importwert. | Zunächst lesen/erhalten. |
| `anzahlBaendeErstv` · Integer · anzahl_baende_erstv | Optionaler geparster Importwert, unabhängig von der Kaufmenge. | Zunächst lesen/erhalten. |
| `anilistUrl` · String · anilist_url | Optionales Anlagefeld, URL-Validierung. | Bedingt, nur neue URL validieren; unsicheren Altwert nicht als Link ausgeben. |
| `coverUrl` · String · cover_url | Optionaler Importwert „Band 1 Bild Url“ über validierte Importdaten. Kein reguläres Preis-/Titelfeld. | Zunächst erhalten. Spätere freie URL-Eingabe gesondert entscheiden. |
| `sammelbaendeId` · Sammelband · sammelbaende_id | Optionale Stammdatenreferenz. Anlage-Checkbox und Typauswahl, Referenzprüfung bei Anlage. | Bedingt editierbar oder erste Stufe ohne Detailänderung. |

Importdetails werden beim Kauf als neue Reihe übernommen, siehe
`PurchaseService.java:84–105`. Die normale Einkaufstabelle ist kein
Detail-Editor. Es gibt keinen gespeicherten Bibliotheksreihen-FK,
keine Liste konkreter gekaufter Band-IDs, keinen Kaufzeitpunkt, keine
Version und keine eigene Edition-Entity. Edition ergibt sich aus den
Stammdaten, der Index kann sich historisch wiederholen. Die Root-ID
identifiziert ausschließlich den Einkauf.

## Fachregeln und Entscheidungen

**Ungekauft.** Preis, Anpassung und Menge gemeinsam prüfen. Summe =
Basispreis × Kaufmenge + Gesamtanpassung, mit BigDecimal und der bestehenden
Cent-/Größenprüfung aus `Money.java:13–28` und
`WriteValidation.java:26–55`. Den abgeleiteten Betrag niemals aus dem
Client übernehmen. Anzahl rauf oder runter ändert keine Bibliothekszeile
und erzeugt keine Einkaufs-Bandobjekte. Beim späteren Kauf gelten weiterhin
die T03-Obergrenze des Gesamtbestands und die Restcentverteilung auf neue
Bände. Änderungen an Titel, Index oder Edition können die Zielzuordnung
ändern. Mehrere Einkaufspositionen für dieselbe Edition sind fachlich
möglich, daher keine neue globale Einkaufsdublettenregel. T03 lehnt
mehrdeutige oder inkompatible Zuordnung weiterhin beim Kauf ab.

**Gekauft.** Der Kauf ändert den Status der Einkaufsposition und den
Bibliotheksgraphen atomar, speichert aber keine dauerhafte Herkunftszuordnung
zu den konkreten Band-IDs. Titel, Index, Preis, Zeitstempel oder Nummernbereich
reichen bei historischen Käufen und späteren Erweiterungen nicht als
eindeutiger Herkunftsnachweis. Eine spätere Mengenänderung im Einkauf
könnte die historische Statistik verfälschen. Sie darf niemals automatisch
Bände nachkaufen, löschen, umnummerieren oder bestehende Banddaten ändern.

| Variante für bereits gekaufte Positionen | Vorteil | Nachteil und Konsequenz |
| --- | --- | --- |
| A · Nur Kaufaufzeichnung bearbeiten | Ohne Schemaänderung möglich; korrigiert historische Einkaufswerte. | Bibliothek bleibt absichtlich abweichend. Einkaufsausgaben und ggf. Mengenauswertung ändern sich. Klare Kennzeichnung als historische Korrektur nötig. |
| B · Einkauf und zugehörige Bibliotheksbände synchronisieren | Gemeinsame Preiswahrheit, sofern die Zuordnung eindeutig ist. | Aktuell keine sichere Zuordnung. Hoher Aufwand, neue Herkunftsbeziehung und Regeln für Altbestände sowie gemeinsame TX und Preisverteilung nötig. Keine Zuordnung erraten. |
| C · Kaufdaten schützen, Bibliothek separat bearbeiten | Kleiner Umfang, klare Datenhoheit, T03-/M06-Bandgarantien bleiben erhalten. | Historische Preisfehler bleiben im Einkauf, solange keine gesonderte Korrekturfunktion vereinbart ist. |

Empfehlung: M01-1 auf explizit ungekaufte Zeilen beschränken. Für gekaufte
Positionen zunächst C. Einkaufsposition als Kaufaufzeichnung, Bibliothek
als Quelle für aktuelle Bestands- und Bandwerte. Variante A nur nach
ausdrücklicher Entscheidung über Statistik und unveränderliche Felder.
Variante B wäre ein eigener migrationspflichtiger Entwurf.

**Entscheidungen vor Implementierung**

1. Soll M01-1 neben Titel, Menge und Geld auch Index, Edition, Datum und
   vergriffen bearbeiten? Empfehlung: ja, aber Identitätswechsel deutlich
   markieren und Detailmetadaten zunächst überwiegend lesen.
2. Darf ein bekannter Preis aktiv geleert werden? Empfehlung: in M01-1
   ablehnen, historisches NULL bei unabhängigen Edits erhalten, neue
   explizite 0 zulassen. Finanzänderung bei unbekannter Basis kontrolliert
   ablehnen, bis der Benutzer die fehlenden Werte bewusst setzt.
3. Darf das Datum geleert werden? Empfehlung: nur wenn undatierte Positionen
   erreichbar bleiben; andernfalls fürs erste Edit nur Datumsänderung.
   Was passiert beim Abschalten des eBay-Flags mit bekannter Anpassung?
   Empfehlung: Wert erhalten oder explizite Nullsetzung bestätigen lassen.
4. Sind für gekaufte Positionen C oder A gewünscht, und welche historischen
   Felder dürfen dabei Statistik ändern? Empfehlung: C, Menge und Geld
   der Kaufaufzeichnung geschützt lassen.
5. Soll die Kaufvorschau nach einem zwischenzeitlichen Edit ausdrücklich
   neu bestätigt werden? Empfehlung: ja, auch wenn nur der Preis wechselt.
   Den dafür nötigen additiven Bestätigungsvertrag vor Umsetzung freigeben.

## Technischer Entwurf

M01-1 kann ohne Schemaänderung umgesetzt werden. Vorgeschlagen ist ein
gezielter Editor-DTO und ein Vergleichstoken aus kanonisch serialisierten
bearbeitungs- und kaufrelevanten Werten von Root und Detail, mit expliziten
NULL-Markern und deterministischer Geldrepräsentation. Das Token dient dem
Vergleich, nicht der Autorisierung. Keinen Zeitstempel als vorhandene Version
voraussetzen. Vergleichstoken erkennen den aktuellen Zustandsunterschied,
aber keine ABA-Historie. Persistiertes `@Version` würde eine Migration und
eine Strategie für Detailänderungen erfordern. Für den lokalen Umfang reicht
der Vergleich unter derselben Sperre, solange ABA-Historie keine Anforderung ist.

Edit im vorhandenen `LibraryWriteCoordinator` (siehe
`LibraryWriteCoordinator.java:35–76` und Advisor in
`LibraryWriteConfiguration.java:21–60`). Coordinator vor dem äußeren
Transaktionsbeginn, Sperre bis zum tatsächlichen Abschluss. Dieselbe
pessimistische Einkaufszeilensperre wie beim Kauf
(`EinkaufslisteRepository.java:16–20`), anschließend Zustand und Token
erneut prüfen. Ein Detail-Write bleibt in derselben TX. Edit sperrt und
ändert keine Bibliotheksreihe. Beim Rennen Kauf zuerst → Edit-Konflikt.
Edit zuerst → Kauf muss seinen bestätigten Stand erneut prüfen. Zwei
Editoren → zweiter abweichender Save liefert Konflikt. Kein automatisches
Replay, keine Rücksetzung der vorhandenen Coordinatorquarantäne.

**Integrationsrisiko mit Kaufvorschauen.** T03 bindet derzeit Zielreihe und
Nummernbereich, aber keinen Preis-/Einkaufsfingerabdruck. Ein Preis-Edit
kann bei gleichem Bereich sonst nach alter Vorschau übernommen werden.
Vorschlag: Kaufvorschau und Statusdialog an denselben Einkaufszustand
binden und beim endgültigen Kauf unter Lock erneut prüfen. Der Additivvertrag
muss für beide UI-Wege und ältere Clients ausdrücklich entschieden werden.
Ein optionales Token allein schützt keine alten tokenlosen Aufrufe.
T03-Ergebnisverträge und Konkurrenzgarantien bleiben erhalten. Kein zweiter
Kaufservice. Falls diese Bindung nicht freigegeben wird, die Kombination
bearbeitbarer kaufrelevanter Felder mit offenen Kaufdialogen vor Freigabe
fachlich klären.

| Vorgeschlagener HTTP-Vertrag | Verhalten |
| --- | --- |
| GET `/items/{id}/edit` | 200 mit flachem DTO, editierbaren Werten, abgeleiteter Summe, Status und Vergleichstoken. 404 bei fehlender Position. Keine zyklischen Entities. |
| PATCH `/items/{id}` mit JSON | Eng erlaubte Felder und erwarteter Stand. Ausgelassen bleibt erhalten, explizites NULL nur für fachlich freigegebene Felder. 200 mit gespeichertem DTO, danach Liste vollständig neu laden. |
| Fehler | 400 für Typen, Pflichtfelder, ungültige IDs und Feldregeln mit festen Feldcodes. 404 unbekannt. 409 für veralteten Stand, Kaufstatuskonflikt oder unklare Detailbeziehung. 403, 413, 415 nach T02. Technischer Fehler neutral, kein Retry. |

UI: Eigene Edit-Aktion im Template und eigener Dialogzustand im
`einkaufsliste.js`, vorhandene CSS- und Feedback-Helfer wiederverwenden.
Felder nach Identität/Edition, Datum und Preis gruppieren. Preisvorschau
als unverbindliche Anzeige, Server bleibt maßgeblich. Abbruch verwirft
Entwurf. Lade-/Speicherzustand pro Eintrag, feste Feldmeldungen, 409
fordert bewusstes Neuladen. Bei unbekanntem Ergebnis Speichern sperren
und Bestand nach Reload prüfen. Text per DOM-Textausgabe; optionale
URLs nur nach bestehender Validierung. Kein Scraperaufruf beim Öffnen.
Preisoptionen niemals still umschalten. Read-only-Ansicht für gekauft
oder unbekannten Status, sofern später gewünscht.

## Umsetzung und Abnahme

1. **M01-1** nach Fachfreigabe: DTO und Feldregeln in der Validation,
   separater Controllerpfad, koordinierter Service mit gesperrtem
   In-place-Update, DOM-Dialog und datenbankfreie Tests. Bei freigegebener
   Kaufzustandsbindung beide vorhandenen Bestätigungswege ergänzen.
2. **M01-2** nur nach Entscheidung zu gekauften Positionen. Bei C genügt
   die explizite Schutzregel. A erfordert historische Änderungsregeln,
   B einen gesonderten Migrationsvorschlag mit Bestandsschutz.
3. **M01-3** geschützte Opt-in-Integration nach Freigabe, anschließend
   **M01-4** hashgebundene manuelle Browserabnahme und **M01-5** Review,
   Dokumentation und getrennte Git-Freigabe.

| Stufe | Geplante Prüfungen |
| --- | --- |
| Unit/Standalone-MVC | Pflichtfelder, strenge Typen, ungültige/unbekannte IDs, Betragsskala und Summenüberlauf, −0,01/+0,02, NULL gegenüber 0/false, 0/1/10.000/10.001 Bände, Datum, Referenzen, fehlendes Detail, unbekannter/gekaufter Status, stale Token und unveränderte Felder. Fehler vor Mutation, neutrale 5xx und keine Bibliothekswrites. |
| Koordination/Security/DOM | Edit gegen Edit und Kauf in beiden Reihenfolgen, Preisänderung bei gleicher Vorschau, Commit-/Rollbackgrenze und Fehler ohne Replay. Bestehende T03-/M06-Assertions, Host/Origin/CSRF, Limits, sichere Texte, Feldfeedback, Abbruch, Reload und Doppelklick. Ausgangspunkte: `PurchaseServiceTest.java:46–186` und `write-feedback.test.cjs`. |
| MySQL | Nur separat freigegeben gegen geschütztes `mangalib_test`. Eigene journalisierte Fixtures, echte Commit-/Rollbackgrenzen ohne fremde äußere Test-TX, Flush/Clear und unabhängiger Reload. Snapshot aller Altzeilen und Schema, stabile IDs/Detailbeziehung, Centwerte und Konflikte. Keine ungefilterte Suite mit historischen TX-Annahmen. |
| Browser | Separat gegen `mangalib_manual_test`, Profil `manual-test`, `127.0.0.1:18080`, synthetische Fixtures und Vorher-/Nachher-Snapshots. Einzel-/Mehrfeldedit, Abbruch, Validierung, Reload, stale Dialog, Kauf/Edit-Konflikt und eine gezielte Kaufregression. Gekauft-Edit nur bei Freigabe. Keine pauschale Wiederholung von H1–H7. |

Tests später offline ohne Daemon mit identischem vorherigem Dry-Run.
Produktive Datenbank bleibt tabu. Testzugänge und Passwortvariablen folgen
TESTING und MANUAL_TESTING. STOP bei unklarer Datenhoheit, erforderlicher
Schemaänderung, unbestätigter Mutation oder drohender Abschwächung von
T02/T03/M06. Empfehlung für den ersten Auftrag: ungekaufte Positionen,
gezielte Feldupdates, no-op-fähiger Save und stale Schutz unter derselben
Schreibkoordination, nach Entscheidung über die Kaufvorschau.

M01-0 umfasste lesende Analyse, freigegebene Branchanlage und dieses Dokument.
Keine Implementierung, Gradle-Ausführung, Tests oder Datenbankzugriffe.

## M01-0B – Verbindliche Fachentscheidungen und Implementierungsvorbereitung

Dieser Nachtrag ist für M01 maßgeblich. Die vorstehenden Ist-Befunde und
Varianten bleiben als Analyse erhalten. Früher offene Fragen sind durch
F01–F06 entschieden, soweit unten ausdrücklich festgelegt.

### F01–F05 · Feldregeln

| Entscheidung | Verbindliche Umsetzung |
| --- | --- |
| F01 | Ausschließlich bei `gekauft=false`: Titel, Manga-Passion-Index, Verlag, Typ, Format, Bandanzahl, Einzelpreis, Preisanpassung, Erscheinungsdatum, eBay-Flag, Vergriffen und vorhandene reguläre Sammelbandreferenz. Importzusatzdaten und URLs nur anzeigen, sofern sicher, bzw. erhalten. IDs, Kaufstatus und Gesamtpreis sind keine Eingabefelder. Stammdatenreferenzen auflösen und bestehende T03-Zuordnung erhalten. |
| F02 | Fehlender Schlüssel bewahrt den gespeicherten Wert. Explizite 0 ist ein bekannter Betrag. Einzelpreis-NULL im Request ablehnen, historischen NULL-Wert bei unabhängigen Änderungen beibehalten. Kein Nullersatz im Editor, DTO oder Service. |
| F03 | Datum ändern oder mit explizitem NULL leeren. Gleichzeitig eine dauerhaft erreichbare Gruppe „Ohne Erscheinungsdatum“ bereitstellen. |
| F04 | Flag und Anpassungsbetrag unabhängig speichern. Flagwechsel allein verändert weder Betrag noch Summe. Anpassung immer anzeigen und nur durch eine explizite Betragseingabe ändern. M05 bleibt separat. |
| F05 | Variante C. `true` und NULL sind im Editor schreibgeschützt. Keine Bibliotheksänderung oder Rekonstruktion von Herkunfts-IDs. Der bestehende DELETE-Pfad prüft derzeit keinen Kaufstatus. M01 ändert ihn nicht. Schutz vor Löschung gekaufter Positionen bleibt eine getrennte Fachfrage. |

PATCH verwendet eine Feld-Allowlist. Titel, Referenzen und Bandanzahl dürfen
bei Übermittlung nicht NULL sein. Titel, positive IDs und Index,
Datumsformat, Menge 1–10.000 und Cent-/Größengrenzen werden wie bei der
Anlage geprüft. Index und Sammelbandreferenz sind nach Modell optional.
Für beide ist explizites NULL als Entfernen der Referenz vorgesehen.
Datum akzeptiert ISO LocalDate oder NULL. Boolean-Eingaben müssen true
oder false sein, ausgelassene historische NULL-Flags bleiben NULL.
Unbekannte Felder und Typen ablehnen. Ein vorhandenes Detail in-place
ändern. Bei fehlender oder widersprüchlicher Detailbeziehung keinen
Ersatzgraphen erzeugen. Root-only-Änderungen bei fehlendem Detail nur
zulassen, wenn sie unabhängig sind und die gewählte Altbestandsregel
bestätigt ist.

Bei einer tatsächlichen Änderung von Preis, Anpassung oder Menge den
effektiven Gesamtpreis aus Anzahl × Einzelpreis + Gesamtanpassung
serverseitig mit BigDecimal und bestehenden Geldgrenzen berechnen.
Wenn eine erforderliche Komponente historisch unbekannt bleibt,
Finanzänderung kontrolliert ablehnen. Metadatenänderungen und reiner
Flagwechsel erhalten Preis, Anpassung und gespeicherte Summe byte- bzw.
wertgetreu, auch bei Inkonsistenzen. Ein bewusster Wert 0 setzt eine
bekannte Anpassung auf null Euro. Ob explizites NULL für die Anpassung
als eigener fachlicher Zustand angeboten wird, bleibt offen. Bis zu
einer Entscheidung im Editor nicht anbieten. Der spätere Kauf nutzt
weiter die vorhandene Restcentverteilung ausschließlich für neue Bände.

### Undatierte Positionen

Der heutige Controller lädt Datumsintervalle über
`findByErscheinungsdatumBetween` (Controller 68–127, Repository 22).
NULL fällt aus allen Intervallen heraus. Für M01 eine gesonderte
Repository-Abfrage `findByErscheinungsdatumIsNull` mit stabiler
ID-Sortierung und ein separates Model-Attribut vorsehen. Die Gruppe
„Ohne Erscheinungsdatum“ im gleichen Template rendern, mit identischen
Zeilenaktionen, ausdrücklichem Navigationsanker und direkter
Filterauswahl „Ohne Datum“. Auch beim Reload nach Leeren muss der
Eintrag erreichbar sein. Datierte Gruppen und Statistikabfragen bleiben
unverändert. Keine fiktiven Datumswerte und kein YearMonth-Umbau.

### Editorvertrag und Ablauf

- GET `/items/{id}/edit`: flacher DTO mit Einkaufs-ID, nullable
  Kaufstatus, Bearbeitbarkeitskennzeichen, allen F01-Feldern, Gesamtpreis,
  lesbaren Importdetails und `editToken`. Referenzen als IDs,
  Geld als Cent-genaue Dezimalstrings. Optionales NULL ausdrücklich
  darstellen, statt es mit der globalen JSON-Nullauslassung zu verlieren.
  DTO innerhalb einer konsistenten Lesegrenze materialisieren.
- PATCH `/items/{id}`: JSON-Objekt aus `editToken` und ausschließlich
  tatsächlich beabsichtigten Feldänderungen. Keine abgeleitete Summe,
  Entity oder ID-Überschreibung. Erfolg 200 mit aktualisiertem DTO und
  Token nach bestätigtem TX-Abschluss. Unveränderter Save darf ohne
  Mutation enden.
- 400 für ungültige IDs, Requestform, fehlendes oder syntaktisch ungültiges
  Edit-Token und Feldfehler. Feste Feldcodes statt interner Texte.
  404 für fehlende Position. 409 mit festen Codes für veralteten Stand,
  geschützten Kaufstatus und betroffene inkonsistente Details. 403/413/415
  gemäß bestehenden Filtern. 5xx neutral; bei unbekannter Antwort
  Schaltfläche sperren, kein Replay, erst Bestand neu laden.
- Dialog nutzt vorhandene CSS-/DOM- und CSRF-/Feedback-Helfer.
  Edit-Schaltfläche für eindeutig ungekauft, bei true/NULL allenfalls
  beschriftete Lesedarstellung. Daten frisch laden, Felder in Identität,
  Datum, Preise und Zusatzinformationen gruppieren. „Unbekannt“ und 0
  sichtbar unterscheiden. Anpassung auch bei ausgeschaltetem Flag zeigen.
  Preisvorschau unverbindlich, keine Zahl aus unbekannten Komponenten
  erzeugen. Feldfeedback, Ladezustand, Einzelrequest-Sperre, Abbrechen
  ohne Write und vollständiger Reload nach Erfolg. Stale erfordert
  Neuöffnung, keine automatische Übernahme des alten Entwurfs.

### F06 · Verpflichtender Einkaufszustand beim Kauf

Ein kanonischer Snapshot-Encoder, aber getrennte Tokenbereiche
`edit:v1` und `purchase:v1`. So kann eine Editorantwort keine
Kaufvorschau ersetzen. Edit umfasst alle editierbaren und angezeigten
persistierten Felder einschließlich Datum, Status, abgeleiteter Summe
und Details. Kauf umfasst ID, Kaufstatus, Titel, Index, Verlag, Typ,
Format, Menge, Einzelpreis, Gesamtanpassung, Gesamtpreis, eBay und
Vergriffen sowie Detailpräsenz, Detail-/Eltern-ID, Sammelband-ID und
sämtliche beim Neuanlagekauf übernommenen Importwerte und URLs.
Siehe `PurchaseService.java:53–105,108–161`. Das Erscheinungsdatum
wird im aktuellen Kaufpfad nicht übertragen und gehört im vorgeschlagenen
Minimalvertrag nur zum Edit-Token. Referenzen anhand persistierter IDs
kodieren. Zielreihen-ID und Bereich bleiben separat verpflichtend
beim Append, der gewählte Bibliotheksstatus bleibt eine separate
validierte Eingabe beim Neuanlagekauf.

Feste Feldreihenfolge und typisierte, längenkodierte UTF-8-Darstellung,
Domänentrennung und SHA-256 als versionierter Hexwert. NULL besitzt
einen eigenen Marker, false und true eigene Werte. Keine implizite
Konvertierung durch Locale oder JSON-Property-Reihenfolge. Geldwerte
deterministisch als numerisch kanonischer Dezimalstring, ohne Rundung
oder Verlust historischer Präzision, 0 getrennt von NULL. Textwerte
unverändert, Details fehlend getrennt von vorhandenen Details mit
NULL-Feldern. Berechnung und Vergleich aus dem unter Lock geladenen
aktuellen Graphen. Kein Authentifizierungsmerkmal, CSRF bleibt
unabhängig erforderlich. Derselbe Zustand ergibt dasselbe Token,
Änderungen mit anschließend exakt wiederhergestelltem Zustand (ABA)
werden nicht erkannt. Echte Versionierung wäre eine eigene Migration.

Beide vorhandenen Kaufwege nutzen die gemeinsame T03-Operation. Die
Vorschau für bestehende Reihen liefert Bereich und Kauf-Token. Die
Statusanforderung für neue Reihen liefert Kauf-Token und den für die
bewusste Bestätigung benötigten Einkaufsstand. Die UI zeigt insbesondere
Titel, Anzahl und Preisbasis/Summe. Der finale Request führt das Token
mit. Der Server unterscheidet Vorschau und mutierende Bestätigung
explizit. Ein alter finaler Aufruf mit Bereich oder Status ohne Token
darf keinen Kauf auslösen. Für fehlendes oder veraltetes Kauf-Token
einen festen Konfliktcode und Aufforderung zur neuen Vorschau verwenden
(vorgeschlagen 409). Syntaktisch ungültige Tokens mit 400 abweisen.
Bestehende 417-Statusauswahl, 200-Vorschau und mutierungsfreier
Already-Purchased-Ausgang bleiben erhalten. Kein Replay nach Konflikt.
Der konkrete Konfliktcode und das additive JSON-Feld werden mit M01-1A
als Vertrag festgeschrieben.

### Koordination und Freigabereihenfolge

Editor öffnen → Lesesnapshot. Speichern → äußerer
`LibraryWriteCoordinator` → Transaktion → Einkaufszeile pessimistisch
sperren (`findByIdForPurchase`) → aktuellen Root-/Detailstand und
`gekauft=false` prüfen → Token vergleichen → vollständig validieren →
in-place ändern und flushen → Commit oder Rollback vor Mutexfreigabe.
Kein zusätzlicher Lockmanager. Kein Bibliothekslock beim Editor.
Kaufvorschau und endgültige Kaufbestätigung verwenden dieselbe
Coordinator-Grenze. Finale Bestätigung prüft unter Einkaufslock das
Token vor jeder Mutation, danach ggf. Zielreihenlock und T03-Inventar-
und Bereichsprüfung. Kein DB-Lock über den Dialog hinweg. Kauf zuerst
sperrt späteren Edit durch Status. Edit zuerst entwertet eine ältere
kaufrelevante Vorschau, auch bei identischem Bereich. Zwei Edit-Tabs
mit abweichendem Ergebnis: der zweite Save trifft auf 409. Quarantäne
und Abschlussprüfung bleiben unverändert.

| Stufe | Auftrag und zugeordnete geplante Tests |
| --- | --- |
| M01-1A | DTO, kanonischer Encoder, Validierung und Verträge. `ShoppingEditRequestTest`, `ShoppingStateTokenTest`, Standalone-MockMvc: omitted/NULL/0, Boolean-Tri-State, Token-Determinismus, Domänentrennung, unabhängige Änderungen, fehlerhafte Tokens und IDs. |
| M01-1B | Koordinierter Edit-Service. `ShoppingEditServiceTest`: Preis ±0,01/+0,02, Gesamtpreis, Überlauf, Menge 1/10.000 und 0/10.001, Leeren des Datums, Flag aus mit unveränderter Anpassung, unbekannte Berechnungsbasis, true/NULL, fehlendes Detail, stale Edit, zwei Edits und Fehler-/Rollbackpfad im Fake. Keine Bibliotheksmutation. |
| M01-1C | Verpflichtende Kaufbindung und atomare Umstellung beider Bestätigungswege. `PurchaseServiceTest` und Kauf-Controller-Vertrag: tokenloser finaler Request, stale Preis bei gleichem Bereich, beide Lockreihenfolgen, erneute explizite Bestätigung, Replay, Restcents auf neuen Bänden und unveränderte Altbände. |
| M01-1D | Dialog und undatierte Gruppe. jsdom unter `src/test/js` plus isolierter Template-/Controller-Test: explizites Datum-NULL, Auffindbarkeit nach Reload, sichtbare Anpassung bei Flag aus, Abbruch, gekaufte/NULL-Zeile, Fokus, Fehlermeldung und Doppelklicksperre. |
| M01-1E | Zusammenhängende datenbankfreie Auswahl aus neuen Tests und T02/T03/M06-Regressionen. Host/Origin/CSRF, PATCH-Medientyp/Requestgrenzen, kein Replay bei 5xx/unklarem Ergebnis, unveränderte Altverträge außerhalb der verpflichtenden Kaufbindung. |

Die fünf Stufen benötigen spätere Implementierungs-/Testfreigabe und
laufen zunächst ohne Datenbank. Kein produktiv nutzbarer Editor vor
vollständigem M01-1C. Integrationsfreigabe danach separat: isoliertes
`mangalib_test`, profilgebundener Testbenutzer, journalisierte eigene
Fixtures, echter Flush/Clear/Reload, Commit/Rollback und Edit/Kauf-Rennen
mit vollständigen Vorher-/Nachher-Snapshots. Browserfreigabe danach
separat: hashgebundener Manual-Kandidat, `mangalib_manual_test`,
`127.0.0.1:18080`, begrenzte synthetische Writes, Reload, zwei Tabs,
Sicherheitsfeedback, Datenerhalt und geordneter Shutdown. Keine
ungeprüfte Vollsuite. Gradle später offline ohne Daemon, identischer
Dry-Run vor jedem tatsächlichen Aufruf. `mangalib` bleibt tabu.

**Gate für M01-1A:** ausdrückliche Implementierungsfreigabe auf diesem
Branch, bestätigte DTO-/Token-Semantik und klare Behandlung der
verbleibenden Punkte: explizites Anpassungs-NULL, Root-only-Edit bei
fehlenden Details und endgültige Kauf-Konfliktcodes. Kein Schema- oder
Produktionsblocker für den hier vorgeschlagenen Weg erkannt. STOP bei
umgehbarer Kaufbindung, fehlender T03-Koordination, schwächeren T02-Guards,
Änderung gekaufter Positionen, notwendiger Migration oder automatischer
Altbestandskorrektur. M01-0B endet mit PLAN_ONLY.

## Umsetzung 10.11.2026

Der Editor nutzt GET /items/{id}/edit und PATCH /items/{id}, den bestehenden
Schreib-Advisor, die pessimistische Einkaufssperre und die gemeinsame
Feedback-/CSRF-Schicht. Undatierte Zeilen erscheinen als zusätzliche
Gruppe der bestehenden Navigation. Der Dialog verwendet die vorhandenen
Popup- und Formularstile. Es gibt keine Schemaänderung.

Explizites Anpassungs-NULL wird abgelehnt, historisches NULL bei unabhängigen
Änderungen erhalten. Root-Änderungen bei fehlenden Details sind möglich,
eine Sammelbandänderung ohne konsistent verknüpftes Detail ergibt 409.
Veraltete oder fehlende Kauf-Tokens ergeben beim ansonsten zulässigen
finalen Kauf 409 mit PURCHASE_STATE_CONFLICT. Syntaktisch ungültige
Tokens ergeben 400. Vorschau und Statusdialog zeigen die bestätigten
Einkaufswerte. Der Bearbeitungsdialog setzt den Anpassungswert beim
Flagwechsel nicht zurück.

Historische Kauf-Integrationsläufe sind Evidenz des alten Stands. Ihre
tokenlosen finalen Requests sind seit M01 absichtlich nicht mehr gültig.
Die Browserabnahme und eine Aktualisierung der historischen Opt-in-
Aufrufer für spätere erneute Läufe wurden im folgenden Nachtrag behandelt.

### Abnahmenachtrag

Die T03-Opt-in-Kaufaufrufer beziehen ihren Bestätigungswert aus der
Vorschau. Bei Konkurrenzfällen geschieht dies vor den konkurrierenden
Transaktionen. Die Opt-in-Quellen kompilierten. Die historischen
DB-Matrizen wurden nicht erneut ausgeführt.

Zwei gezielte Tests auf `mangalib_test` bestanden. Der Rollback-Test
prüfte den vollständigen Snapshot. Der ausdrücklich aktivierte
Commit-Test prüfte einen tatsächlich konkurrierenden Edit und Kauf,
den nach Commit neu geladenen Preis und die abgelehnte alte
Kaufbestätigung. Eine eigene Einkaufsposition mit Detailzeile bleibt
auf `mangalib_test`. Der übrige Bestand und das Schema sind unverändert.

Der getrennte Manual-Kandidat hatte SHA-256
`2e29a790fa956410fac4263cce8b2dc1505528f9dce7bdde3da8efd52fc5fd5e`.
`-ValidateOnly` bestand. Der Browser bestätigte auf der isolierten
Manual-Instanz das Leeren des Datums, die undatierte Gruppe nach Reload,
die Summe 8,99 €, die erhaltene Anpassung −0,01 € bei ausgeschaltetem
eBay-Flag und die abgewiesene alte Kaufbestätigung im zweiten Tab.
Der Dialog war bei der tatsächlich beobachteten Desktopbreite bedienbar.
Die angeforderte 390-Pixel-Ansicht wurde vom Connector nicht angewendet
und bleibt als visuelle Nachprüfung offen. HTTP-Statuscodes wurden im
Browser nicht direkt beobachtet.

Der manuelle Abschlussvergleich ergab genau zwei neue Zeilen, eine
eigene Einkaufsposition und ihr Detail. Keine neue Bibliothekszeile,
alle Vorzeilen und Schema-Metadaten erhalten. Abschluss-SHA-256
`64c5c3a07bb93d0c3e57dd8e8179d3ca59f2fd66ff4ee4b62bb11d1e22d7ae0c`.
Private Evidenz unter `.gradle/m01-acceptance/`. Die eigene Anwendung
wurde über die Startsession geordnet beendet. Kein Produktivzugriff,
kein Commit, Merge oder Push.

### Abschluss der schmalen Browseransicht

Die lesende Nachprüfung auf `mangalib_manual_test` bestätigte bei
390 CSS-Pixeln den vorhandenen Testeintrag, den geöffneten Dialog ohne
horizontalen Überlauf und das Schließen über die per Scrollen erreichbare
Schaltfläche „Abbrechen“. Der dabei aufgefallene schwache Kontrast wurde
auf den M01-Dialog begrenzt mit der vorhandenen Textfarbe korrigiert.
Der Offline-Paketbuild und `-ValidateOnly` bestanden. Im neu gebauten
Kandidaten bestätigte der Browser bei 1280 Pixeln die helle Textfarbe
für Überschrift und Feldbeschriftung sowie das Schließen ohne Speichern.
Die 390-Pixel-Geometrie wurde vor der reinen Farbänderung gemessen.
Die Nachprüfung führte keine Schreibaktion über die Anwendung aus.
