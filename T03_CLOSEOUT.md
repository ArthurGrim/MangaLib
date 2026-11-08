# T03 – Gesamtabnahme und Vorbereitung des lokalen Merge

Stand 08.11.2026 · **`T03_CLOSEOUT_READY`**

T03 ist für das vorgesehene lokale Einzelbenutzermodell fachlich und technisch
abgenommen. Die definierte Abnahme umfasst die geschützte MySQL-Matrix und
die freigegebenen Browserfälle H1–H7. Es gibt keinen neuen Blocker für den
lokalen Fast-Forward auf Basis des geprüften Git-Stands. Die Übernahme würde
auch den auf dem Branch liegenden T02-Abschluss und den undici-Fix enthalten.
Der Dokumentationscommit und der Merge benötigen getrennte ausdrückliche Freigaben.

## Abdeckungsmatrix

| Anforderung | Implementierung und datenbankfreie Prüfung | Reale MySQL-Abnahme | Browser und Datenerhalt |
| --- | --- | --- | --- |
| Atomare Übernahme und Rollback | Gemeinsamer `PurchaseService`, lokaler Coordinator über die tatsächliche Transaktionsgrenze, koordinierte Schreibservices. Unit-/Fake-TX- und Proxyfälle zu Fehlerpfaden, Locks und Abschluss. | Kaufendpunkte für bestehende/neue Reihen, Save-/Flush-Fehler, Work-Rollback und gezielter `beforeCommit`-Fehler mit `afterCompletion=ROLLED_BACK`. Quarantäne sperrte weitere Mutation. | H2 und H5 bestätigen die regulären UI-Wege. Die Aussage zum technischen Commitfehler stammt aus MySQL, der Browser erzeugte keinen solchen Fehler. |
| Wiederholung, konkurrierende oder veraltete Bestätigung | Kaufstatus und bestätigter Nummernbereich innerhalb der Schreibgrenze geprüft. Tests zu Replay, Fremd- und NULL-Zuordnung und der Mutex-Reihenfolge. | Realer gleichzeitiger Kauf, zwei Positionen einer Reihe, Konkurrenz mit Neuanlage und Bandedit. Veralteter Bereich und inkonsistenter Bestand wurden getrennt geprüft. | H1 Vorschau/Abbruch, H3 Zwei-Tab-Konflikt mit neuer Vorschau, H4 Wiederholungsmeldung. Snapshots bestätigten keine doppelte Anlage und keine Kaufmarkierung für A2. |
| Bandidentität, Altwerte und M06 | Zuordnung über persistierte IDs und eindeutige Bandnummern. Unit-Tests für unsortierte und widersprüchliche Bestände, NULL-Werte und M06-Erweiterung. | Vollständige Altband-Snapshots nach Einzelbandedit, Kauf und Erweiterung, einschließlich Lesestatus, Rereads, Spezialflags und Beziehungen. | R01 bestätigte den gezielten Band-1-Edit. R02 erhielt alle drei Bandzeilen aus R01 und bei der Erweiterung zusätzlich Band 4. Der neue Band 5 war ungelesen. |
| Preis- und Centregeln | BigDecimal, vorzeichenrichtige Restcentverteilung, NULL bleibt von 0 verschieden. Geld- und Kaufregressionen prüfen beide Vorzeichen und die Summen. | DECIMAL-Roundtrips einschließlich ±0,01 €, 0,02 € und ±20 €, Erhalt individueller Altpreise und Behandlung unbekannter Werte. | H2 Band 4 zu 8,49 € mit −0,01 € Anpassung, Reihe 27,48 €. H5 drei Bände zu 2,00 € mit +0,01/+0,01/0,00 €, Reihe 6,02 €. |
| Anzahlkorrektur, Reduktion und Erweiterung | Deklarationskorrektur, explizite Erweiterung und Ablehnung von Reduktion oder Teilkorrektur als getrennte Regeln. | Korrektur zu hoher und zu niedriger Deklaration, explizite M06-Erweiterung und abgewiesene Reduktion. | H6 Reduktion 4 → 3 abgewiesen, anschließend genau Band 5 angelegt. Eine echte Korrektur einer inkonsistenten Deklaration wurde im Browser nicht ausgeführt. |
| Fehlerfeedback, Session und Replay-Sperre | Statische Meldungen aus bekannten Ergebniscodes, Sperre bei ungewissem Ergebnis. Standalone-MockMvc und DOM-Fälle prüfen u. a. Fehlerfeedback und Replay. | Kontrollierte Konflikt- und Fehlerantworten in den definierten Fällen. Abschlussquarantäne deckt den gezielten TX-Fehler ab. | H3 sichtbarer Bereichskonflikt ohne beobachtete Wiederholung. H7 sichtbare Sicherheitsablehnung nach mehr als 160 Sekunden Inaktivität. Abschlusssnapshot unverändert gegenüber dem Stand vor H7. HTTP-Statuscodes wurden im Browser nicht direkt beobachtet. |
| Datenerhalt | Guards, Opt-in-Tasks, Rechteprüfung, ID-Journale und geschützte Snapshots. | 72 bestätigte Geschäftszeilen, 70 Vorzeilen beim letzten Lauf vollständig erhalten, 78 Stammdatensätze und erfasste Schema-Metadaten unverändert. | R01 und R02 zusammen genau 18 eigene Zeilen. Alle vor R01 vorhandenen Zeilen, Stammdaten und Schema-Metadaten erhalten. |

## Getrennte Abnahmestufen

1. **Produktivimplementierung** in `8c165f0` und getrennte
   Testinfrastruktur in `b09c6f5` und `e97e8f9`. Die T03-Fachänderungen
   umfassen Services, Repository-Locks, Validierung und Browserdialoge.
   Es gibt keine T03-Schemamigration oder Änderung an Entity-Mappings.
2. **Datenbankfreie Prüfungen**. Die letzten 265 gefilterten Vorprüfungen
   und Classpath-/Historienprüfungen bestanden vor der finalen Matrix.
   Mock-, Unit-, DOM- und Guard-Evidenz ergänzt die Integrationsfälle.
   Die Zähler werden nicht über wiederholte Läufe addiert.
3. **Erste reale Commit-/Konkurrenzstufe**. Drei Fälle bestanden, separat
   von den 17 Matrixfällen. Commit und unabhängiger Reload, vollständiger
   Work-Rollback und einmaliger Append bei zwei überlappenden Aufrufen.
4. **E1–E6**. Neun akzeptierte Fälle im teilweise bestandenen Matrixlauf,
   ein akzeptierter `null`-Fall im späteren Acht-Fälle-Lauf und sieben
   akzeptierte Restfälle. Der erste Matrixversuch scheiterte an der
   Mockito-Repositorydelegation, der nächste am Unboxing der NULL-Fixture
   und die Acht-Fälle-Fortsetzung an der inversen JPA-Detailreferenz der
   Stale-Fixture. Die später bestätigten Korrekturen ändern den
   historischen FAILED-Status dieser Läufe nicht. Vollständige
   Methodenzuordnung und Nachweisgrenzen stehen in
   [T03-6B-Abschluss](T03_6B_CLOSEOUT.md#reale-nachweise).
5. **Browser R01**. Teilnachweis für Anlage von A und gezielten Band-1-Edit.
   Die Einkaufsanlage wurde am erforderlichen, damals nicht festgelegten
   eBay-Preis-Flag angehalten.
6. **Browser R02**. Mit unverändertem Kandidaten und bestätigtem R01-Endstand
   wurden A1, A2 und N angelegt und H1–H7 abgenommen. Beide Instanzen wurden
   geordnet beendet. Die Ergebnisse und die in R02 bestätigte Portfreigabe
   stehen in [T03-6C](T03_6C_APPROVAL.md).

## Daten- und Evidenzbindungen

| Umgebung | Bestätigter Stand | Fingerabdruck |
| --- | --- | --- |
| `mangalib_test` | 72 synthetische Geschäftszeilen nach der Abnahme. 70 Vorzeilen, 78 Stammdatensätze und Schema unverändert. | `0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991` gilt für die unabhängig geprüfte **70-Zeilen-Baseline**. Für 72 Zeilen gibt es keinen neu gemessenen Vollhash. |
| `mangalib_manual_test` | 13 Reihen, 13 Reihendetails, 36 Bände, 8 Einkaufspositionen, 8 Einkaufsdetails. Gegenüber vor R01 hinzugekommen 2 / 2 / 8 / 3 / 3. | `980f117a8d1e69a09e0ebd72f8fdb9ffea4795093f9d3a8591ceb5e798b83c5b` für den tatsächlich gemessenen R02-Endstand. |

Die Verfahren der beiden Umgebungen sind unterschiedlich. Die lokalen
SHA-256-Bindungen der fünf privaten Write-Journale, vier archivierten
Matrix-/Restberichte, drei historischen Manual-JARs und acht Experimente
stimmen mit dem zuletzt geprüften Stand überein. Das lokale Commit-XML
und der 70-Zeilen-Lesebericht stimmen ebenfalls mit den dokumentierten
Hashes überein. Historische Ablageorte und Bindungen stehen in
[T03-6B-Freigabe](T03_6B_APPROVAL.md) und [T03-6B-Abschluss](T03_6B_CLOSEOUT.md).

Der manuelle Kandidat hat SHA-256
`0139557bb0395cd35fa8c38dbf025f7f76dd8023cb3a6c8c4718fa090bcc60a9`,
das Kandidatenmanifest
`cb2e27e2617f86e11ea048b2267ce6907b990b72446029d4520cfa86f92036fa`.
R01-Ende und R02-Beginn stimmen hashgleich überein. Ein erneuter lokaler
Vergleich der gespeicherten Snapshots bestätigte die vollständigen Werte
aller Zeilen vor R01, aller drei R01-Bände und sämtlicher Stamm- und
Schemaabschnitte. Private Originale verbleiben unter `.gradle/`.
Es wurde keine Datenbank erneut angesprochen.

## Grenzen und Folgearbeiten

| Grenze | Einordnung |
| --- | --- |
| Manipulierte Host-/Origin-Header und separat falsche oder fehlende CSRF-Tokens im Browser | Bewusst außerhalb von H1–H7. Bestehende Security- und Filtertests decken die Verträge ab. H7 belegt die sichtbare Sessionablehnung mit unverändertem Bestand. |
| Absichtlich verlorene Commitantwort | Außerhalb der realen Abnahme. DOM-Tests decken die Sperre nach ungewisser Antwort ab. Der reale `beforeCommit`-Test prüft einen anderen Fehlerpfad. |
| Inkonsistente Bandanzahldeklaration im Browser | Beide Korrekturrichtungen sind in MySQL nachgewiesen. Die reguläre UI erzeugt dafür keine künstliche Inkonsistenz. |
| Mehrere Instanzen/JVMs | Nicht abgenommen. Der gemeinsame Mutex und die reale Konkurrenzabnahme gelten pro JVM. Mehrinstanzbetrieb wäre eine eigene Anforderung. |
| 75 historische Integrationsmethoden | Getrennter Auftrag. 34 koordinierte Methoden benötigen kompatible TX-Grenzen und kontrollierte Fixtures. Weitere Methoden haben teilweise alte Baseline-Annahmen. Ein ungefilterter Standardlauf ist für diesen Branchstand nicht als bestanden ausgewiesen. Falls später ein solcher Lauf als Mergegate verlangt wird, muss dieses Gate zuerst gezielt wiederhergestellt werden. |
| Produktive Altbestände | Synthetische Fixtures decken ausgewählte Altwerte und Inkonsistenzen ab. Für reale Bestandsdaten liegt keine produktive Abnahme vor. Die spätere Betriebsfreigabe bleibt eigenständig. |

Aus diesen Grenzen ergibt sich für die definierte lokale T03-Abnahme
kein zusätzlicher Testbedarf. Vor einem späteren weiteren Write-Lauf
auf der automatisierten Testdatenbank ist eine getrennt freigegebene
72-Zeilen-Leseprüfung sinnvoll. Ein produktiver Start oder eine Migration
ist kein Bestandteil dieses Mergeplans.

## Git-Stand und Mergeplan

- Branch `codex/t03-data-integrity`, HEAD
  `ba18ffc0e7c9bd777247f6be0476a52d94974417`. Lokaler `main`, lokale
  Tracking-Referenz `origin/main` und gemeinsame Basis stehen auf
  `865e56ac94ee633d129c91009f83c46ef454bf31`. Der Remote-Server
  wurde nicht abgefragt.
- Divergenz vor dem Dokumentationscommit **0 lokale main-eigene / 6
  branch-eigene Commits**. In Reihenfolge `b7a2dbe` T02,
  `c20c213` undici, `8c165f0` T03-Fachlogik, `b09c6f5`
  Integrationsvorbereitung, `e97e8f9` E1–E6-Tests und `ba18ffc`
  MySQL-Abnahmedokumentation.
- Der verfolgte Diff gegen `main` umfasst 246 Dateien (216 neu, 30
  geändert). 192 Dateien entfallen auf die bereits abgenommene T02-Basis.
  Der Diff ab T02 umfasst 72 Dateien, einschließlich Dependency-Fix
  und gemeinsamer Dateipfade. Der T03-Anteil enthält die vorgesehenen
  Schreib-, Preis-, Validierungs- und Frontendänderungen und die
  Opt-in-Tests. Kein neuer T03-Eingriff in DB-Guards, Manual-Guards,
  Entity-Mappings oder Migrationen. Die größeren Sicherheitsänderungen
  gegenüber `main` gehören zum T02-Vorfahren. Im Branch-Diff wurden
  keine verfolgten privaten Journale, Dumps, Buildausgaben, JARs oder
  historischen unversionierten Experimente gefunden.
- Fast-Forward ist auf diesem lokalen Stand möglich. Es gibt keinen
  parallelen lokalen main-Diff und daher keinen Drei-Wege-Konflikt.
  Der geprüfte Arbeitsbaum enthält ausschließlich die unten benannte
  Abschlussdokumentation und die acht unversionierten Experimente.
  Die bekannten ignorierten Artefakte sind auszuschließen. Es gab keine
  unbekannten ignorierten Quellen in den geprüften Buildpfaden.

Vorgesehener lokaler Commit
`docs(t03): close browser acceptance and record final verification`.
Exakter Indexumfang: `T03_6C_APPROVAL.md`, `T03_CLOSEOUT.md`,
`TESTING.md`, `ROADMAP.md`. Keine dieser Dateien wird ignoriert.
`MANUAL_TESTING.md` enthält das bereits gültige Start- und
Snapshotverfahren. Der R02-Nachtrag dokumentiert den spezifischen
Abnahmelauf, daher ist hier keine weitere Änderung erforderlich.
JARs, Berichte, Snapshots, Logs, Dumps, Secrets und private Journale
sowie die acht Experimente bleiben außerhalb des Index.

Vor einer später separat freigegebenen Übernahme zuerst diesen
Dokumentationscommit anhand der vier Pfade und des vollständigen
Indexdiffs prüfen. Danach lokalen `main`, gemeinsamen Vorfahren,
Arbeitsbaum und den vollständigen Übernahmeumfang einschließlich T02 und
undici erneut bestätigen. Nur bei unverändert passender Abstammung
einen lokalen Fast-Forward mit `--ff-only` durchführen. Bei Divergenz
anhalten und die Kombination separat beurteilen. Dieser Auftrag hat
weder Git-Historie noch Branchzuständigkeit verändert.
