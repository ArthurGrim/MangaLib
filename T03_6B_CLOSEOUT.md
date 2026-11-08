# T03-6B – Gesamtabnahme und Git-Vorbereitung

Stand 08.11.2026 · `T03_6B_CLOSEOUT_READY`

Die definierte MySQL-Abnahme ist vollständig. T03-6C und die spätere
Wiederherstellung eines verlässlichen Standard-Integrationslaufs bleiben offen.
Die früheren Statusangaben in ROADMAP, TESTING und der Freigabehistorie
beschreiben den jeweiligen damaligen Stand.

## Reale Nachweise

| Stufe | Tatsächlich bestandene Fälle | Historischer Gesamtstatus |
| --- | --- | --- |
| Erste Commit- und Konkurrenzstufe, §17 | `committedSeriesReloadsExactMoneyAndOwnedIds`, `failureAfterBandsRollsBackOwnNewRows`, `sameConfirmedPurchaseInTwoThreadsAppendsOnlyOnce` | 3/3, akzeptiert |
| Erster Matrixversuch, §20 | Kein fachlicher Matrixfall abgeschlossen. Mockito-Delegation scheiterte im Fixtureaufbau. | FAILED |
| Korrigierter Matrixversuch, §22 | `httpExistingPurchaseFaultsAndMixedRouteReplay`, `httpNewPurchaseFaultsAndReplay`, `twoDifferentItemsAppendToTheSameSeriesSerially`, `twoItemsForANewIdentityRequireASecondExplicitConfirmation`, `purchaseAndSingleBandEditKeepStableIdentitiesWithUnsortedLists`, `signedRestcentsSurviveSeparateCommitsAndReloads`, `legacyNullMoneyAndCountCorrectionsPreserveEveryBand`, `explicitMismatchExtensionAndReductionRejectionsKeepM06`, Parameter `ambiguous` | 9/17, FAILED nach NULL-Fixturefehler und STOP |
| Acht-Fälle-Fortsetzung, §25 | Parameter `null` | 1/8, FAILED nach `stale`-Fixturefehler und STOP |
| Sieben-Fälle-Fortsetzung, §27 | Parameter `stale`, `details`, `duplicate`, `unknownPrice`, `cartDetails`, `edition` sowie `beforeCommitFailureRollsBackAndQuarantinesFurtherWrites` | 7/7, akzeptiert |

Die letzten drei Matrixläufe ergeben zusammen 17 verschiedene akzeptierte Fälle.
Die drei Fälle der ersten Commit-Stufe liefern separate Vorabnachweise.
Frühere Fehler- und STOP-Einträge bleiben unverändert. Die folgenden
Zuordnungen beziehen sich auf die Assertions im tatsächlichen Testkörper.

| Gruppe | Nachgewiesenes Verhalten | Ergänzende datenbankfreie Abdeckung und Grenze |
| --- | --- | --- |
| E1 Atomarität | Beide Kauf-HTTP-Wege, Rollback nach Bandanlage sowie bei Save-/Flush-Fehlern. Abschließend echte Spring-Synchronisation, absichtlicher `beforeCommit`-Fehler, `afterCompletion=ROLLED_BACK`, Quarantäne und abgewiesener Folgeschreibversuch. | `PurchaseServiceTest`, `LibraryWriteCoordinatorTest`, Proxy- und Transaktions-Hooktests. Kein Serverabsturz oder Verbindungsverlust injiziert. |
| E2 Wiederholung und Konkurrenz | Wiederholter Kauf über beide Wege, einmaliger Append trotz zweier Threads. Getrennte Positionen derselben Reihe, konkurrierende Neuanlage/Append sowie Kauf gegen Einzelbandänderung. Reihenfolge bis TX-Cleanup, keine gleichzeitig aktiven äußeren TXs, beendete Threads und kontrollierte Abweisung der Doppelanlage. | `T03PurchaseOverlapTest`, Coordinator- und Kaufservice-Regressionsfälle. Nachweis für eine JVM, keine unabhängige Zweitinstanz oder isolierte Messung von DB-Lockwartezeiten. |
| E3 Identifikation | Altbände mit stabilen IDs bei umgekehrter Abfragereihenfolge. Mehrdeutiger/NULL-Reihenindex, ungültige Beziehungen, Dubletten, falsche Edition und alte Bestätigung führen zu kontrollierten Ergebnissen. Realer Stale-Reload nach Flush/Clear mit drei intakten Bänden, danach `BAND_CONFLICT` und `UNKNOWN_STATUS`. | `BandInventoryTest`, `PurchaseServiceTest`, `T03BandSnapshotTest`. Der bestätigte Nummernbereich identifiziert keine externen Bandindizes. |
| E4 Preise und NULL | Persistierte, vorzeichenrichtige Centvektoren für 0,01, −0,01, 0,02, 20 und −20 auf drei Bände, Summen nach unabhängigen Reloads, Erhalt individueller Altpreise, unbekannte Preise ohne stillen Ersatz. | `MoneyTest`, Kauf- und Schreibservice-Tests. Statistikdarstellung und historische Abfrageverträge bleiben eigene Regressionsthemen. |
| E5 Anzahl und M06 | Zu hohe/zu niedrige Deklaration auf den tatsächlichen Bestand korrigiert, ohne Bandwerte zu ändern. Keine implizite Anlage oder Reduktion. Explizite Erweiterung erzeugt nur Band 4; alter Lesestatus, Rereads und nullable Flags bleiben erhalten. | `BandCountTest`, `ReadingStatusExtensionTest` und Kaufservice-Tests. UI-Bedienung und fachfremde Altwertvarianten sind separat zu prüfen. |
| E6 Integrität | Geschützte Baseline, registrierte neue IDs, vollständige Feld- und Schema-Vergleiche nach den Fällen, Rollbacks und durables Budget. Die Kette bestätigt den Erhalt des jeweiligen Vorbestands auch beim kontrollierten Teilabbruch. | Guard-, Rechte-, Historien-, Fixture- und Snapshotregressionen. Keine serverweite SQL-Auditierung und keine globale Sperre externer Schreiber. |

## Datenstand und lokale Belege

Der Bestand wuchs durch die erste Commit-Stufe auf 12, durch die neun
bestätigten Matrixgruppen auf 70 und durch den letzten Abschlussfall auf 72
synthetische Geschäftszeilen. Der erfolgreiche NULL-Fall erzeugte keine
dauerhafte Zeile. Zuletzt kamen genau eine Einkaufsposition und ihr Detail
hinzu. Von 39 ID-Versuchen rollten 37 zurück. Alle 70 Vorzeilen, 78
Stammdatensätze und die erfassten Metadaten der zehn InnoDB-Tabellen blieben
in den laufinternen Vergleichen unverändert. AUTO_INCREMENT ist von diesen
Vergleichen ausgenommen.

Der letzte unabhängig gemessene Vollhash gilt für **70** Geschäftszeilen
und lautet `0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991`.
Für 72 Zeilen liegt der laufinterne Erhaltungs- und Mengennachweis vor.
Ein neuer Vollhash wurde nicht gemessen.

Die fünf privaten Write-Journale und die vier archivierten Matrix-/Rest-XMLs
stimmen lokal mit den historischen SHA-256-Angaben überein. Auch die aktuellen
12- und 70-Zeilen-Leseberichte stimmen mit den dokumentierten Bindungen
überein. Die erste Commit-Stufe ist im lokalen Gradle-XML mit drei Erfolgen
belegt. Dessen aktuell berechneter SHA-256
`ad2b9bc65a6c444f1f9e772d7bf1963c8a8535acc72a1c7a8cc51343650ddfb3`
ist eine heutige Bestandsaufnahme ohne frühere unabhängige XML-Hashbindung.
Sein Journal ist historisch hashgebunden. Private IDs und Reportinhalte
bleiben im lokalen Evidenzbestand.

Eine separat freizugebende 72-Zeilen-Leseprüfung ist für den lokalen
Git-Abschluss und die getrennte manuelle Browserabnahme entbehrlich. Sie ist
sinnvoll vor einem weiteren schreibenden Lauf auf `mangalib_test` oder wenn ein
neuer unabhängiger Übergabehash benötigt wird. Dafür die beiden dauerhaft
bestätigten IDs aus der letzten Abschlussgruppe und ihre Beziehung prüfen,
alle 37 Rollback-IDs ausschließen und die Projektion ohne genau diese zwei
Zeilen gegen den 70-Zeilen-Hash vergleichen. Danach zwei konsistente,
geschützte Read-only-Snapshots über den vollständigen 72-Zeilen-Stand
vergleichen. Der bestehende 70-Zeilen-Task erwartet absichtlich eine andere
Baseline und ist dafür ungeeignet. Keine Ausführung oder neue Baseline in
diesem Auftrag.

## Inventar der 75 älteren Methoden

Die folgenden Zuordnungen verwenden für jede Methode den unveränderten
Strategiecode aus T03_INTEGRATION_INVENTORY.md. Methoden mit Teilüberschneidung
behalten ihren zusätzlichen Vertrag. „Geeignet“ setzt eine separate
Freigabe für den jeweiligen lesenden oder Rollback-Lifecycle voraus.

| Klasse | Methoden | Einordnung für eine spätere Auswahl |
| --- | ---: | --- |
| DatabaseIntegrityBaselineTest | 1 L | Geeigneter lesender Hash-/Counts-Bericht. Allein kein Sollvergleich oder journalgebundener 72-Zeilen-Nachweis. |
| DemoApplicationTests | 1 L | Historischer Kontext-Smoke, durch den realen Boot-/JPA-Aufbau für 6B redundant. |
| MangalibApplicationTests | 1 L | Wie der zweite reine Kontext-Smoke, kein zusätzlicher 6B-Nachweis. |
| TemplateRenderingTest | 1 L | Zusätzlich relevant für Thymeleaf/Lazy-Rendering außerhalb einer TX. Vor Auswahl prüfen, welche echten Fixtureansichten im aktuellen Bestand tatsächlich erreicht werden. |
| LocalSecurityMvcTest | 12 F | T02-Sicherheitsverträge und Scraper-Doubles. Statisch kein Fachwrite; ausgewählt sinnvoll bei einer Security-Regression. Keine Voraussetzung für die 6B-Matrix. |
| LegacyEditIntegrationTest | 15 C, 1 F | C braucht angepasste eigene TX-Grenzen. E3–E5 decken Teile zu Altwerten, Preis-NULL und Beziehungsfehlern ab. Explizite Bulk-Aktionen, Alt-URL, JSON-Rundlauf und Nullable-Flags bleiben eigene Verträge. F prüft CSRF/Host mit G/V-Rollbackfixtures und ist separat auswählbar. |
| MvcContractTest | 7 C, 3 F, 8 R | C braucht angepasste Commitfixtures und aktualisierte Kauf-/Preisverträge. Kaufstatus/Neuanlage überlappen E1/E2, der Delete-Fall braucht eine eigene Freigabe. F und die sieben R-Methoden ohne Monatsstatistik sind als geschützte Vertragsregressionen separat auswählbar. `monthlyStatisticsKeepStringKeysAndExistingCentTruncation` braucht eine nachgewiesen freie Jahresfixture oder baselinebezogene Assertions. |
| PersistenceCompatibilityTest | 3 R | Ergänzend geeignet für Mapping, Datum, NULL und Roundtrip mit eigenen Rollbackfixtures. Überschneidungen mit E3/E4 ersetzen die spezifischen Mappingassertions nicht. |
| ReadingStatusIntegrationTest | 10 C | Alle brauchen angepasste TX-Grenzen. Kaufpfade, explizite Erweiterung, Anzahlabweichung und Einzelbanderhalt sind durch E1/E3/E5 in den dortigen Varianten abgedeckt. Weitere M06-, Neuanlage- und Legacyvarianten separat erhalten. |
| RepositoryCompatibilityTest | 5 R | Vier Selektor-, Jahres-/Monatsfilter-, Multiplikator- und Kategorieprüfungen sind separate geschützte Regressionen. `executesMonthlyAggregatesIntervalsSortingAndPurchasedFiltersAcrossYearBoundary` setzt freie Zeitfenster voraus. Vor Ausführung absichern oder auf baselinebezogene Fixtures umstellen. |
| RequestLimitsIntegrationTest | 1 C, 4 F | Die vier Filter-/Bindingfälle bleiben T02-Regressionen. Positiver Bandedit mit explizitem NULL, Replay und 204 braucht eigene Commitfixtures. E3 prüft stabile Bandänderung, der HTTP-Replayvertrag ist separat. |
| ServiceTest | 1 C | Anlage/Reload fachlich durch erste Commit-Stufe und E1 abgedeckt. Als Altmethodik vor einer späteren Suite an eigene TX-Grenze anpassen. |
| TestDataPreservationTest | 1 R | Für E6 redundant, weiterhin sinnvoll als generische, gezielt freigegebene Guard-/Rollbackregression. |

Summe 34 C, 20 F, 17 R und 4 L. Bei C lehnt der Coordinator eine fremde
äußere Testtransaktion absichtlich ab. Die Annotation bloß zu entfernen,
könnte dauerhafte unjournalisierte Fixtures erzeugen. Ältere Annahmen
über leere Geschäftstabellen oder freie Zeitfenster sind vor späterer
Ausführung zu prüfen. Die ehemalige Leerbaseline ist mit 72 Zeilen
unvereinbar. Keine ungefilterte Suite für den Git-Abschluss.

## Technische Schlussprüfung und Git-Plan

Der gemeinsame Coordinator hält den lokalen Mutex über
`TransactionTemplate.execute` bis einschließlich Commit-/Rollback-Cleanup.
Pessimistische Locks laufen in der Reihenfolge Einkaufsposition, dann
Zielreihe. Beide HTTP-Kaufwege führen in dieselbe Fachoperation. Status und
bestätigter Bereich werden innerhalb der Schreibgrenze erneut geprüft.
Frontend-Konflikte verwenden freigegebene Ergebniscodes. Bei unbekanntem
Schreibresultat bleibt ein manueller Bestandsabgleich erforderlich. E3–E5
stützen stabile Band-IDs, BigDecimal-Berechnung und nullable Altwerte.
Es gibt seit HEAD keine Produktions- oder Frontendänderung. Die
Datenbankguards und Security-Filter sind im uncommitteten Diff unverändert.
Der Standardlauf bleibt wegen der C-Methoden ein getrennter
Nachbesserungspunkt. Ein Mehrinstanzbetrieb würde zusätzliche Prüfung
brauchen. Für die definierte 6B-Abnahme ergab sich kein neuer Blocker.

Branch `codex/t03-data-integrity`, HEAD `b09c6f5`, Staging leer.
`c20c213` enthält den separaten undici-Fix, `8c165f0` die Fach- und
Frontendänderungen samt Regressionen, `b09c6f5` die erste
Integrationsvorbereitung. Für die noch uncommitteten Arbeiten nach
ausdrücklicher Git-Freigabe zwei gezielte Commits vorsehen.

1. `test(t03): complete guarded E1-E6 acceptance coverage`
   - `build.gradle.kts`.
   - Unter `src/t03Integration/java/de/mangalib/` die vier Klassen
     `T03ContinuationReadOnlyTest`, `T03MatrixAcceptanceTest`,
     `T03RemainingAcceptanceTest`, `T03SeventyReadOnlyTest`.
   - Unter `src/test/java/de/mangalib/` die Klassen `T03Preservation`,
     `T03PurchaseOverlap`, `T03CartFixture`, `T03ContinuationPolicy`,
     `T03MatrixHistory`, `T03RepositoryHook` und die vier zugehörigen
     `*Test`-Klassen für CartFixture, ContinuationPolicy, MatrixHistory und
     RepositoryHook.
   - Unter `src/test/java/de/mangalib/service/` die Klassen
     `PurchaseServiceTest`, `T03PurchaseOverlapTest` und
     `T03RepositoryHookTransactionTest`.
2. `docs(t03): record MySQL acceptance and prepare browser verification`
   - `TESTING.md`, `T03_6B_APPROVAL.md`, `T03_6B_CLOSEOUT.md`.

Vor jedem Commit die exakte Auswahl, den vollständigen
Indexdiff und `git diff --cached --check` prüfen. Die acht unversionierten
Windows-Experimente explizit ausschließen. Die drei Manual-JARs und
Build-Berichte fallen unter `build/`, private Journale und Archive unter
`.gradle/`. Auch Dumps, lokale Konfiguration, Secrets und private
Sicherheitsberichte ausschließen. Die Stichproben der Ignore-Regeln und
die Liste bereits verfolgter ignorierter Dateien ergaben keinen
Kandidaten für die Aufnahme dieser Artefakte.

## T03-6C – isolierte Browserabnahme vorbereiten

1. Freigabe an einen separaten neuen Manual-Kandidaten mit festem SHA-256
   binden. Den Offline-Packaging-Taskplan vorab kontrollieren und die drei
   vorhandenen JARs schützen. Nach der Startfreigabe mit dem dokumentierten
   Skript und explizitem Artefaktpfad zunächst `-ValidateOnly` ausführen.
   Port und Prozessidentität prüfen. Start ausschließlich mit
   `manual-test`, `mangalib_manual_test_user` und
   `MANGALIB_MANUAL_DB_PASSWORD`, auf `127.0.0.1:18080`. Banner und
   synthetische Scraper-Doubles verifizieren.
2. Vor Schreibaktionen den vorhandenen manuellen Bestand mit dem
   freigegebenen geschützten Snapshotverfahren erfassen. Frühere T02- und
   M06-Fixtures als Vorbestand schützen. Eine kleine Liste eigener
   synthetischer Einträge mit eindeutigem Laufpräfix und Schreibbudget
   vorab definieren. Ohne eindeutige Zuordnung abbrechen.
3. Für eine eigene bestehende Reihe Vorschau und Abbruch prüfen,
   anschließend genau einmal den vorgeschlagenen Bereich bestätigen.
   Nach Reload IDs, Preise, Lesestatus, Rereads und Metadaten der Altbände
   vergleichen. Für eine eigene neue Reihe Statusauswahl, Kauf und
   Reload prüfen. Wiederholung muss ohne neue Bände als bereits gekauft
   enden.
4. In zwei Tabs Vorschauen für zwei eigene Positionen derselben Reihe
   öffnen. Einen Kauf bestätigen, dann die alte Vorschau absenden.
   Kontrollierten Konflikt und unveränderten Bestand prüfen. Eine
   erneute Übernahme setzt einen neu angezeigten Bereich und ausdrückliche
   Bestätigung voraus. Die Browserbeobachtung ergänzt den vorhandenen
   deterministischen Threadnachweis.
5. Synthetische Geldwerte einschließlich positiver und negativer
   Restcents verwenden und die Bandwerte sowie die Summe nach Reload
   vergleichen. Reine Anzahlkorrektur ohne Preisänderung getrennt von
   einer expliziten Erweiterung prüfen. Eine abweichende Deklaration
   nur über bereits genehmigte synthetische Fixtures verwenden. Falls
   keine passende eigene Fixture verfügbar ist, diesen UI-Fall offen
   lassen und keinen vorhandenen Datensatz verändern.
6. CSRF-, Host-, Origin- und Sessionablehnung mit eng begrenzten
   lokalen Negativanfragen prüfen, ohne Schutzmechanismen zu ändern.
   Reaktion, Dialogwerte und ausbleibende Wiederholung beobachten.
   Bei 5xx, unbekanntem Schreibresultat oder Abweichung sofort STOP.
   Kein erneutes Absenden oder Bereinigen.
7. Freigegebenen Nachher-Snapshot mit dem manuellen Ausgangsbestand
   vergleichen, nur genehmigte eigene Änderungen akzeptieren und
   ausschließlich die selbst gestartete Instanz geordnet beenden.

T03-6C braucht eine getrennte Freigabe für den gebundenen Kandidaten,
Anwendungsstart, die manuelle Datenbank, den Browser, die definierten
Schreib- und Negativfälle, lesende Snapshots und den geordneten Shutdown.
Ein aktueller manueller Fingerabdruck oder ein neues Manual-JAR liegt
aus dieser Prüfung nicht vor.

## Umfang dieser Konsolidierung

Lokale Quellen, Git-Diffs, historische Commits, XMLs, Journalhashes und
Ignore-Regeln ausgewertet. Bestehende 265 datenbankfreie Vorprüfungen
als Evidenz herangezogen. Keine neue Testausführung, DB-Verbindung,
SQL-Ausführung, Migration, Anwendung, Browser, Scraper, Worker oder
Git-Schreibaktion. Geändert wurde ausschließlich dieses Dokument.
