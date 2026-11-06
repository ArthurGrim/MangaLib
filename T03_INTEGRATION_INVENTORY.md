# T03-6A – Integrationsinventar (04.11.2026)

Statische Bestandsaufnahme; KEINE Datenbankausführung oder neue Freigabe.
13 Spring-Boot-Testklassen. Jede Zeile bezeichnet eine Testmethode einschließlich
aller ihrer Parameterfälle. Lifecyclemethoden sind in der gemeinsamen Fixture-
strategie aufgeführt, keine zusätzlichen Tests. Keine Klasse deaktiviert.

## Strategien / vollständige gemeinsame Zellbedeutung

- L: Nur SELECT/Kontext/Rendering bzw. synthetischer Scraper-Double ohne Fachwrites.
  Kein Coordinator, kein fachlicher Commitbedarf. Bestehende Guard-Konfiguration
  beibehalten. Spätere ausdrückliche lesende DB-Freigabe (auch Boot initialisiert JPA).
- R: Direkte EntityManager-/Repositoryfixtures mit @Transactional/@Rollback,
  INSERT/UPDATE ausschließlich neuer synthetischer Graphen. Vorher-/Nachher-SELECTs,
  Flush/Clear und AfterTransaction bleiben erhalten. Keine Servicewrites ergänzen.
  Spätere ausdrückliche Rollback-Integrationsfreigabe.
- F: Filter-/Binding-/negative-ID-Ablehnung vor koordiniertem Fachwrite; bei äußerer
  Transaktion dennoch Fixture-INSERTs wie R. GET/Session/Mock-Scraper sind keine
  dauerhaften Fachwrites. Gleiche Rollback-/Lesefreigabe gemäß Fixture, kein Commit.
- C: INSERT/UPDATE bzw. expliziter Delete der EIGENEN Fixture über koordinierte
  Services/PurchaseService; eigener tatsächlicher Commit/Rollback erforderlich.
  Coordinator trifft aktuell auf fremde Testtransaktion: nicht unverändert starten.
  Alle fachlichen Assertions in getrennte Opt-in-Fälle mit registrierten neuen IDs,
  unabhängigem Reload und Scraper-Doubles übertragen. Keine Annotation einfach
  entfernen. Separate Commitfreigabe, bei Parallelität zusätzlich Konkurrenzfreigabe.
  Historische Delete-Fälle brauchen zusätzlich exakt genehmigte Fixture-Löschung;
  sie sind nicht von der vorbereiteten INSERT-/UPDATE-Stufe abgedeckt.

Fixturegruppen: G = PersistenceTestFixtures.persistGraph (Reihe, Details, zwei
Bände, Einkauf, Einkaufsdetails; Stammdaten nur referenziert); V = zusätzliche
nur transaktionale Varianten/Altwerte aus G; Q = neue Repository-Grenzfixtures;
N = keine Geschäftsfixtures. Alle Gruppen verwenden ausschließlich bestehenden
TestContext-Guard + GuardedTestDataSource, Profil test, mangalib_test_user und
mangalib_test. Es wird keine historische Stammdaten-ID automatisch angelegt.

MvcContractTest, ReadingStatusIntegrationTest und LegacyEditIntegrationTest:
BeforeTransaction-Snapshot, BeforeEach G/V, Scraper-Sperren, AfterTransaction-
Vergleich. ServiceTest: Referenzen lesen, Anlage erst im eigentlichen Test (C).
RequestLimitsIntegrationTest: Snapshot-/Rollbackrahmen, G nur im positiven Bandedit.
Repository-/Persistenz-/Datenerhaltungstests: Q/G und vollständiger Rollback.
TemplateRenderingTest/LocalSecurityMvcTest: Vorher-/Nachher-Snapshot ohne Writes.
DatabaseIntegrityBaselineTest: nur Hash/Counts; ContextLoads: JPA-Initialisierung.

## Methodenmatrix

| Klasse.Methode | Äußere TX | Facharbeit / Fixture | Coordinator | Commitbedarf | Anpassung / Freigabe |
|---|---|---|---|---|---|
| DatabaseIntegrityBaselineTest.recordsOnlyHashesAndCountsOfExistingRowsAndSchema | nein | L / N | nein | kein Fachcommit | Strategie L |
| DemoApplicationTests.contextLoads | nein | L / N | nein | kein Fachcommit | Strategie L |
| LegacyEditIntegrationTest.bandRereadAndNullableSpecialSurviveJsonRoundTrip | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.knownAndUnknownFlagsSurviveIndependentEdits | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.metadataEditPreservesPartialReadAndEveryBandValue | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.explicitReadBulkWorksEvenWhenSeriesValueAlreadyMatches | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.explicitRereadBulkUsesMinimumAndDoesNotLowerHigherBand | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.explicitZeroAndFalseReplaceUnknownValuesWithoutImplicitBulk | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.changedSeriesValuesAloneAndForgedImportOptionsNeverOverwriteBands | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.legacyMissingAndEmptyBandRereadStillMeanZero | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.missingCsrfAndForeignHostCannotMutateLegacyFixtures | ja / Rollback | F / G/V | nein | kein Fachcommit | Strategie F |
| LegacyEditIntegrationTest.historicalUrlSurvivesButDifferentUnsafeUrlIsRejected | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.intentionalUrlClearAndReplacementRetainExistingSemantics | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.unknownMoneyRemainsUnknownWhileMetadataCanBeEdited | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.incompleteFinancialBasisRejectsBeforeAnyPartialMutation | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.unchangedCentAmountsAndSumsAreNotRecomputed | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.missingNullableRelationIsRejectedWithoutReplacementOrPartialMutation | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LegacyEditIntegrationTest.missingDetailsOnNewBareSeriesNeverCreatesReplacement | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| LocalSecurityMvcTest.rendersSessionTokenAndHelperWithoutLogin | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsForeignHostAcrossViewsAssetsActuatorAndScraping | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsMissingTokenEvenWithoutOriginBeforeController | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsDeleteWithoutToken | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsForeignOriginDespiteCorrectSessionToken | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsInvalidTokenAndTokenFromDifferentSession | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.rejectsUnsupportedFormBeforeCsrfAndForwardedHeadersWithValidToken | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.oldAutofillMethodsReturn405WithoutControllerOrScraper | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.optionsIsSideEffectFree | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.validAutofillPostPreservesPayloadWithOrWithoutOrigin | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.validScrapePostUsesOnlyDoubleAndPreservesImportFields | nein | F / N | nein | kein Fachcommit | Strategie F |
| LocalSecurityMvcTest.acceptsIpv4AndExposesOnlyMinimalHealth | nein | F / N | nein | kein Fachcommit | Strategie F |
| MangalibApplicationTests.contextLoads | nein | L / N | nein | kein Fachcommit | Strategie L |
| MvcContractTest.servesExistingHtmlViews | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.redirectsResetFiltersToHome | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.returnsSeriesEditorFieldsWithTheirFrontendTypes | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.omitsNullSeriesFieldsAndUsesBandFallbacks | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.returnsBandAndListAsFlatJsonWithoutParentCycles | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.missingRowsReturnEmpty404 | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.createsSeriesFromAddPopupPayloadAndReloadsIt | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.editsSeriesUsingPopupPayloadWithOmittedPricesAndSerializesDetailsWithoutCycles | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.acceptsNullOrAbsentOptionalFieldsOnCreation | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.editsBandFromJavascriptStringsAndReturnsEmpty204 | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.malformedOrWronglyTypedSeriesInputsReturn400 | ja / Rollback | F / G | nein | kein Fachcommit | Strategie F |
| MvcContractTest.rejectsWrongContentTypeAndWrongBandIdType | ja / Rollback | F / G | nein | kein Fachcommit | Strategie F |
| MvcContractTest.createsShoppingItemsWithDateOrNullFromJavascriptPayload | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.shoppingErrorsAndMissingPurchaseHaveTheirExistingStatusAndContentType | ja / Rollback | F / G | nein | kein Fachcommit | Strategie F |
| MvcContractTest.requestsStatusThenCreatesANewSeriesFromOnlyTheOwnedShoppingItem | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.deletesOnlyTheNewFixtureShoppingItemAndReturnsEmpty200 | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| MvcContractTest.returnsCategoryCountsAsIntegralJsonValues | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| MvcContractTest.monthlyStatisticsKeepStringKeysAndExistingCentTruncation | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| PersistenceCompatibilityTest.roundTripsSeriesDetailsVolumesAndUpdatesWithoutChangingIdentities | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| PersistenceCompatibilityTest.roundTripsShoppingDetailsAndLeapDayThenUpdatesTheSameRows | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| PersistenceCompatibilityTest.preservesNullableBooleansWithoutCoercingNullToFalse | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |
| ReadingStatusIntegrationTest.directExtensionPreservesIndividualStatus | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.allRereadsIncludingNullSurviveRealPersistence | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.bothPurchasePathsPreservePersistedOldVolumes | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.declaredCountMismatchAppendsOnlyBeyondActualPrefix | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.rejectsRepresentableAmbiguitiesWithoutPersistedSideEffects | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.foreignSeriesVolumesAreNeitherAggregatedNorModified | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.changingOneVolumeStillAggregatesWithoutTouchingItsSiblings | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.ordinarySeriesEditRetainsExistingReadSelection | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.creationStillPersistsValidatedReread | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| ReadingStatusIntegrationTest.purchaseAsNewSeriesRetainsStatusSelectionAndUnreadDefault | ja / Rollback | C / G/V | ja | eigener Abschluss | Strategie C |
| RepositoryCompatibilityTest.executesAllDerivedSeriesSelectorsAndLookupNameQueries | ja / Rollback | R / Q | nein | kein Fachcommit | Strategie R |
| RepositoryCompatibilityTest.executesSeriesAndBandYearMonthQueriesAndFirstBandSelection | ja / Rollback | R / Q | nein | kein Fachcommit | Strategie R |
| RepositoryCompatibilityTest.preservesCentSumsStatusExclusionAndMultiplierCoalesce | ja / Rollback | R / Q | nein | kein Fachcommit | Strategie R |
| RepositoryCompatibilityTest.executesEveryBandCategoryAtItsBoundaries | ja / Rollback | R / Q | nein | kein Fachcommit | Strategie R |
| RepositoryCompatibilityTest.executesMonthlyAggregatesIntervalsSortingAndPurchasedFiltersAcrossYearBoundary | ja / Rollback | R / Q | nein | kein Fachcommit | Strategie R |
| RequestLimitsIntegrationTest.registeredFilterRejectsBeforeAllBusinessWork | ja / Rollback | F / N | nein | kein Fachcommit | Strategie F |
| RequestLimitsIntegrationTest.registrationAndEffectivePrecedenceComeFromBoot | ja / Rollback | F / N | nein | kein Fachcommit | Strategie F |
| RequestLimitsIntegrationTest.acceptableJsonStillRequiresLocalHostOriginAndOwnSession | ja / Rollback | F / N | nein | kein Fachcommit | Strategie F |
| RequestLimitsIntegrationTest.ordinaryJsonKeepsBusiness400AndMissingPurchase404 | ja / Rollback | F / N | nein | kein Fachcommit | Strategie F |
| RequestLimitsIntegrationTest.ordinaryBandEditReplaysExplicitNullAndRetains204AfterReload | ja / Rollback | C / G | ja | eigener Abschluss | Strategie C |
| ServiceTest.savesSeriesWithDetailsAndVolumes | ja / Rollback | C / N → neue Reihe | ja | eigener Abschluss | Strategie C |
| TemplateRenderingTest.rendersCommittedTestDataWithoutAnEnclosingTransaction | nein | L / N | nein | kein Fachcommit | Strategie L |
| TestDataPreservationTest.preservesExistingRowsAndRollsBackOnlyNewTestAggregates | ja / Rollback | R / G | nein | kein Fachcommit | Strategie R |

75 Methoden inventarisiert. R/F bedeutet nicht „jetzt ausführbar“: Auch lesende
Boot-Kontexte benötigen die ausdrücklich noch fehlende DB-Freigabe. Vor späterer
Auswahl von Methodenfiltern zuerst die gesamte Lifecycle-Fixture prüfen.

## Konkreter Anpassungsbedarf der alten Verträge

- MvcContractTest: fehlender Anlagepreis ist jetzt 400 statt implizitem Nullpreis;
  neue Kaufvorschau/Bestätigung und 409 statt früherem Titel-/Index-Fallback;
  fehlende Edit-Geldfelder sind unverändert statt numerischem Nullsentinel.
- ReadingStatusIntegrationTest: echte Erweiterungen mit extendVolumes=true;
  normale Metadatenbearbeitung false; Titel-Fallback-Kauf nicht mehr zulässig;
  Korrektur und neue Anlageabsicht getrennt. Band-Snapshots/Assertions erhalten.
- LegacyEditIntegrationTest: Leeren eines bekannten Preises ergibt 400; NULL bleibt
  bei unbeteiligter Änderung bestehen. Bestehende Read-/Reread-/URL-Assertions behalten.
- RequestLimitsIntegrationTest: positiver Bandedit braucht eigene Commitfixtures;
  Filter- und negative-ID-Fälle dürfen ihre Rollbackstruktur behalten.
- Ein erwarteter 4xx-Vertrag ist kein Beweis für einen frühen Abbruch: C-Negativfälle
  können den Coordinator vor ihrer fachlichen Validierung betreten. Deshalb keinen
  Testschutz durch Default-Transaktionsausnahmen einbauen.
- ServiceTest: tatsächlicher Commit + unabhängiger Reload, ohne äußere TX; neue
  Fixtures registrieren. Nicht bloß @Transactional entfernen.

Für den Standardlauf sind diese Anpassungen VOR einer ungefilterten Suite nötig.
Die neuen Commit-Tests bleiben ausschließlich im separaten Source-Set; bestehende
Tests/Assertions werden in 6A weder entfernt noch als erneut bestanden ausgegeben.
