# Folio Home Screen Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver configurable portfolio/allocation and favorite-market-price home screen widgets.

**Architecture:** Two Jetpack Glance providers render durable local state through a shared Room widget repository. A Compose configuration activity shares the Add Asset instrument picker; a common quote client and refresh operation serve portfolio and widget-only markets. An update coordinator publishes after local writes and complete refreshes.

**Tech Stack:** Kotlin, Compose configuration UI, Glance 1.2.0, Room 2.7.0, WorkManager 2.10.0, existing OkHttp/provider parsers.

**Spec:** [2026-11-11-home-screen-widgets-design.md](../specs/2026-11-11-home-screen-widgets-design.md)
**Task checklist:** [2026-11-11-home-screen-widgets.md](../tasks/2026-11-11-home-screen-widgets.md)
**Status:** W01 code is present; Gradle/schema verification remains pending. W02 implementation is complete; its unit-test verification is blocked before compilation by missing `androidx.lifecycle:lifecycle-common-java8:2.8.7`.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 35 and AGP 8.7.3.
- Use stable Glance 1.2.0; verify compatibility without assuming a toolchain upgrade.
- Exactly one QA/emulator task: W08, after W01–W07 are complete.
- No emulator checks inside W01–W07; focused JVM tests and compilation may accompany implementation.
- All financial arithmetic uses BigDecimal; IRT means toman.
- All four existing providers and supported provider identifiers must be selectable; ownership is not required.
- Favorite selection must not create portfolio assets, transactions, holdings or valuations.
- Hide-total is independent per instance and suppresses monetary accessibility content too.
- Best-effort periodic refresh is six hours, network constrained, while any widget is installed.
- Persian/English and RTL must work with the app closed.
- No destructive database migration, provider additions or unrelated architecture refactor.
- App writes use Android Studio file tools; builds and commands use studio tools.

## Review Focus

1. Missing FX or an unpriced nonzero holding must produce partial/unavailable value, never a complete zero (W03 unit coverage, W08 device check).
2. A late response after a market/provider change must not display a mismatched quote or search result (W02/W04 guards, W08 race scenario).
3. Hide-total must survive concurrent refresh and process recreation without monetary text leaking through descriptions (W05 behavior, W08 inspection).
4. Multiple widgets for identical tickers on different providers must keep independent labels/configuration and correctly keyed cache (W01/W06 unit coverage, W08 multi-instance scenario).
5. Removing the last widget must stop only widget periodic work; app manual refresh and shared remaining instances must continue (W07 lifecycle logic, W08 removal scenario).

## File and ownership map

Paths below are relative to repository root; main Kotlin paths are under `app/src/main/java/com/example/folio/`.

| File(s) | Responsibility |
| --- | --- |
| `data/widget/WidgetModels.kt` | Shared model and status types |
| `data/widget/WidgetStorage.kt` | Two Room entities and WidgetDao |
| `data/widget/WidgetRepository.kt` | Configuration/cache CRUD, revisions and ID remap |
| `data/widget/WidgetSnapshotRepository.kt` | Coherent portfolio calculation for widgets |
| `data/network/MarketQuoteClient.kt` | Extracted provider requests, normalization and parsing |
| `presentation/components/MarketInstrumentPicker.kt` | Reusable provider/type/query/identifier UI |
| `presentation/components/MarketInstrumentPickerViewModel.kt` | Search debounce, cancellation, selection validation |
| `presentation/widget/WidgetConfigurationActivity.kt` | Placement/edit result contract |
| `presentation/widget/WidgetConfigurationScreen.kt` | Portfolio and price setup forms |
| `presentation/widget/WidgetConfigurationViewModel.kt` | Saved draft and durable Save |
| `presentation/widget/WidgetLayout.kt` | Glance scaffold, theme, formatting and actions |
| `presentation/widget/PortfolioWidget.kt` and `PortfolioWidgetReceiver.kt` | Portfolio Glance UI and host lifecycle |
| `presentation/widget/AssetPriceWidget.kt` and `AssetPriceWidgetReceiver.kt` | Market price Glance UI and host lifecycle |
| `presentation/widget/WidgetActions.kt` | Hide, refresh and configure actions |
| `presentation/widget/WidgetUpdateCoordinator.kt` | Revision invalidation, explicit Glance updates and reconciliation |
| `data/network/MarketRefreshOperation.kt` | Shared serialized portfolio/widget refresh |
| `data/network/WidgetRefreshScheduler.kt` and `WidgetPeriodicRefreshWorker.kt` | Unique six-hour scheduling |
| `res/xml/portfolio_widget_info.xml`, `asset_price_widget_info.xml` | Host metadata |
| `res/values/widget_dimensions.xml`, `res/drawable/widget_*_preview.xml` | Size hints and fictional previews |
| Existing database, application, repository, worker, language, Add Asset, manifest and Gradle files | Integration only |

## Shared contracts

Define these in W01 and keep later tasks consistent. These are proposed public interfaces, not complete implementation bodies.

- `enum class WidgetKind { PORTFOLIO, ASSET_PRICE }`
- `data class MarketKey(val provider: String, val providerSymbol: String)`
- `data class MarketSelection(val key: MarketKey, val displayName: String, val symbol: String, val assetType: String)`
- `data class WidgetConfiguration(val appWidgetId: Int, val kind: WidgetKind, val currency: String, val hideTotal: Boolean, val market: MarketSelection?, val revision: Long)`
- `enum class QuoteStatus { NEVER_LOADED, AVAILABLE, FAILED, UNSUPPORTED }`
- `data class WidgetQuote(val key: MarketKey, val price: BigDecimal?, val currency: String?, val quoteAt: Instant?, val lastSuccessAt: Instant?, val lastAttemptAt: Instant?, val status: QuoteStatus)`
- `data class WidgetPortfolioSnapshot(val total: BigDecimal?, val typeValues: Map<String, BigDecimal>, val missingCount: Int, val isEmpty: Boolean, val valuedAt: Instant?, val generatedAt: Instant)`
- `WidgetRepository.suspend fun configuration(appWidgetId: Int): WidgetConfiguration?`
- `WidgetRepository.suspend fun configurations(): List<WidgetConfiguration>`
- `WidgetRepository.suspend fun save(configuration: WidgetConfiguration): WidgetConfiguration` validates and increments revision atomically.
- `WidgetRepository.suspend fun setTotalHidden(appWidgetId: Int, hidden: Boolean): WidgetConfiguration` updates the latest persisted row without overwriting other settings.
- `WidgetRepository.suspend fun remove(appWidgetIds: Set<Int>): Unit`
- `WidgetRepository.suspend fun remapIds(oldToNew: Map<Int, Int>): Unit` atomically remaps existing records.
- `WidgetRepository.suspend fun quote(key: MarketKey): WidgetQuote?`
- `WidgetRepository.suspend fun storeQuote(quote: WidgetQuote): Unit` preserves successful price/time on failed attempts and rejects an older success overwriting a newer success.
- `WidgetRepository.suspend fun pruneQuotes(): Unit` deletes only keys unused by any saved widget.

## W01 — Durable widget foundation

**Depends on:** none.
**Coverage:** persistence section, L03, F04.

**Files:** Create WidgetModels.kt, WidgetStorage.kt, WidgetRepository.kt. Modify `data/local/PortfolioDatabase.kt`, `FolioApplication.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`. Export `app/schemas/com.example.folio.data.local.PortfolioDatabase/4.json`.

**Consumes:** Room database version 3, application-scoped dependency construction.
**Produces:** all shared contracts above and `FolioApplication.widgetRepository: WidgetRepository`.

- [ ] Add catalog aliases for Glance appwidget, material3 and preview artifacts at 1.2.0, then sync. Preview dependencies are development tooling; no platform upgrade.
- [ ] Implement the two tables and DAO in the spec; store decimal values as text using existing converters. Include optional market fields and quote failure metadata.
- [ ] Register migration 3→4 in FolioApplication; retain all existing financial tables/data. Set Room version 4 and retain exported schemas.
- [ ] Implement repository methods above, atomic revision updates, quote identity isolation and restored-ID remapping with collision-safe temporary IDs.
- [ ] Add focused JVM coverage for configuration validation/key identity and failed-attempt cache retention. Write migration/device test coverage as part of W08, where it will be executed.
- [ ] Verify `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds; inspect the exported schema for only intended additions.

- **Ruling:** Kept the spec-pinned Glance 1.2.0 dependencies. Google Maven returned HTTP 404 for Glance and known-existing Room/Material artifacts; a retry with Glance excluded also failed resolving existing Android dependencies before compilation. This is a repository-access blocker, not evidence to change the specified version. Verification and schema inspection remain pending.

**Done when:** durable widget settings and independent cached market quotes exist without modifying portfolio records.

## W02 — Reuse provider quotes and the Add Asset picker

**Depends on:** W01.
**Coverage:** F01–F05.

**Files:** Create MarketQuoteClient.kt and both presentation/components picker files. Modify `data/network/PriceRefreshWorker.kt`, `presentation/PortfolioApp.kt` and `presentation/MainViewModel.kt` only where extracting existing picker/search responsibilities. Reuse `InstrumentSearch.kt` and `PriceProviders.kt`.

**Consumes:** MarketKey/MarketSelection and existing `InstrumentSearch.search(provider: String, query: String, assetType: String): List<Instrument>`.
**Produces:**
- `MarketQuoteClient.canonicalKey(provider: String, providerSymbol: String): MarketKey`
- `MarketQuoteClient.suspend fun fetch(key: MarketKey): PriceResult`
- `MarketQuoteClient.suspend fun fetchAll(keys: Set<MarketKey>): Map<MarketKey, kotlin.Result<PriceResult>>`
- `@Composable fun MarketInstrumentPicker(assetType: String, initialSelection: MarketSelection?, onSelectionChanged: (MarketSelection?) -> Unit)`

- [x] Extract provider HTTP request construction/parsing into MarketQuoteClient; preserve endpoint headers, timeouts, Aban midpoint, numeric IDs and all existing currency units. Normalize Nobitex aliases using marketParts; preserve numeric IDs without losing digits. Batch Aban catalogue once in fetchAll, propagate coroutine cancellation.
- [x] Change the existing worker to consume the client while retaining its current asset configuration guards and conversion behavior.
- [x] Implement shared picker state with four providers, asset-type search context, query field, results and manual provider identifier. Use 300 ms debounce plus generation checks/cancellation so stale results cannot change current selection.
- [x] Validate manual selection via fetch; Save readiness requires a valid selection. Provider/type/identifier edits invalidate the previous selection until validated.
- [x] Replace Add Asset's duplicated picker with the shared component without changing opening-holding or save behavior. Search errors, empty state and loading must be distinct.
- [x] Add fixture-based JVM tests for canonical pairs, numeric ID preservation, provider units, one Aban fetch per batch and late-search suppression, plus delayed ViewModel search/validation response coverage.
- [ ] Run the focused tests; the final attempt stopped before compilation because Google Maven could not provide the existing androidx.lifecycle:lifecycle-common-java8:2.8.7 artifact.

**Done when:** the same provider selection and fetch capabilities can be used by Add Asset and widget setup, including unowned markets.

## W03 — Portfolio widget snapshot calculation

**Depends on:** W01.
**Coverage:** P02–P05, P03 failure states.

**Files:** Create `data/widget/WidgetSnapshotRepository.kt`; expose it from FolioApplication. Reuse `domain/PortfolioServices.kt` and DAO queries; change domain helpers only if a shared pure calculation is necessary.

**Consumes:** WidgetPortfolioSnapshot, active assets, holdings, cached prices and existing conversion services.
**Produces:** `WidgetSnapshotRepository.suspend fun portfolio(currency: String, now: Instant = Instant.now()): WidgetPortfolioSnapshot`.

- [ ] Read the necessary assets, transactions, prices and available FX in one Room read transaction; perform expensive aggregation off the main thread. Reuse the existing currency-price selection rules rather than inventing widget conversions.
- [ ] Derive total and grouped type values from exactly the same rows in the requested currency. Count only unvalued nonzero active holdings as missing. Distinguish empty zero portfolio, partial subtotal and all-unpriced unavailable.
- [ ] Keep underlying grouped values unrounded; round only displayed percentages. Implement stable sorting and compact top-three-plus-Other as a pure presentation helper.
- [ ] Add JVM assertions: values 60+30+10 yield 60/30/10%; five types preserve the sum when grouped; zero denominator is safe; inactive assets excluded; missing FX is partial/unavailable; zero holdings need no quote.
- [ ] Run focused tests. Document the spec P05 distinction between current widget rows and the dashboard's committed headline.

**Done when:** one coherent snapshot drives both total and allocation without missing-data deception.

## W04 — Widget placement and configuration

**Depends on:** W01, W02.
**Coverage:** L01–L03, F01–F04, P01/P06 settings.

**Files:** Create the three WidgetConfiguration files. Modify AndroidManifest.xml and localized strings. Coordinator updates are connected fully in W07.

**Consumes:** WidgetRepository, MarketInstrumentPicker, AppLanguage/LanguagePreferences.
**Produces:** an activity accepting ACTION_APPWIDGET_CONFIGURE and a validated appWidgetId for either Folio provider; durable WidgetConfiguration after Save.

- [ ] Declare the configuration activity and validate widget ID against AppWidgetManager provider ownership. Unknown IDs or widget kinds exit canceled; do not write arbitrary IDs.
- [ ] Default to RESULT_CANCELED. Build portfolio form with IRT/USD and hide-total; build price form with shared provider/type/instrument picker.
- [ ] Hold unsaved fields in SavedStateHandle, including selected validated market and validation status. Rotation preserves input; revalidate if required after process recreation.
- [ ] On Save persist configuration, increment revision, trigger that instance's initial update, reconcile scheduling and return RESULT_OK with the ID. Do not depend on a host onUpdate broadcast for the first render.
- [ ] Cancel placement without durable configuration; cancel edit preserves prior settings and cache. For reconfiguration display saved settings and refresh validation only when the market changes.
- [ ] Replacing a selection clears its displayed draft quote; successful selection validation can seed the correctly keyed widget cache. Store no portfolio entity.
- [ ] Verify compilation and focused configuration validation tests; defer launcher interaction to W08.

**Done when:** both widget kinds can be configured through the Android result contract and edited independently.

## W05 — Portfolio widget layout and privacy actions

**Depends on:** W01, W03, W04.
**Coverage:** P01–P06, L04–L05.

**Files:** Create PortfolioWidget.kt, PortfolioWidgetReceiver.kt, WidgetLayout.kt, WidgetActions.kt and `res/xml/portfolio_widget_info.xml`; create portfolio preview and shared dimension resources. Modify manifest/strings.

**Consumes:** saved configuration and WidgetSnapshotRepository.portfolio.
**Produces:** `PortfolioWidget : GlanceAppWidget`, `PortfolioWidgetReceiver : GlanceAppWidgetReceiver`, shared Glance layout helpers and hide/configure actions.

- [ ] Register receiver metadata after the activity, with APPWIDGET_UPDATE, explicit exported value and matching XML resource. Restrict widgetCategory to home_screen.
- [ ] Use SizeMode.Exact. Adapt Glance canonical standard-toolbar scaffold/title bar/action sizing to financial content; remove sample media buttons. Use dimension resources and the specified cell targets/minimums rather than copying toolbar sample dimensions that do not fit this product.
- [ ] Render selected-currency total, labeled allocation bars, age/partial state and action buttons. Compact shows three types plus Other; >=280×220 dp shows all positive types.
- [ ] Implement per-instance persisted hide/show. Every render must read latest hideTotal; refresh publication must never reuse an outdated visible flag. Mask money in text and content descriptions; keep labels/percentages accessible.
- [ ] Implement empty, partial, unavailable, unconfigured and updating states. Body opens dashboard; Configure opens this ID. Use localized content descriptions and minimum 48 dp action targets.
- [ ] Add fictional preview resources and light/dark styling. Extract shared number formatting from PortfolioApp only if needed; do not change app financial notation.
- [ ] Compile and run pure formatting/allocation/hidden-output tests. No emulator check yet.

**Done when:** a portfolio widget renders every required data/privacy state with bounded, readable layout.

## W06 — Favorite asset price widget

**Depends on:** W01, W02, W04, W05 shared layout.
**Coverage:** F04–F07, L04–L05.

**Files:** Create AssetPriceWidget.kt, AssetPriceWidgetReceiver.kt, `res/xml/asset_price_widget_info.xml` and price preview; extend WidgetActions.kt/shared layout, manifest/strings.

**Consumes:** saved MarketSelection, WidgetRepository.quote(key), shared Glance layout helpers.
**Produces:** `AssetPriceWidget : GlanceAppWidget` and `AssetPriceWidgetReceiver : GlanceAppWidgetReceiver`.

- [ ] Register receiver/XML and 2×2 initial size hint with horizontal/vertical resize and SizeMode.Exact.
- [ ] Render provider, symbol/name, native-currency price and quote time from the configured MarketKey only. Do not look up a portfolio asset by symbol.
- [ ] Show stale when quoteAt is more than six hours old; preserve a cached value with failure/offline indicator. No cached success renders “Price unavailable”, not 0.
- [ ] Resolve queued/running status without claiming cached data is fresh. Unsupported state preserves selection with Configure available.
- [ ] Body/Configure open the selected widget's configuration; Refresh delegates to the shared action. Re-read saved market/revision before publication to prevent new labels showing old prices.
- [ ] Add focused JVM cases for same ticker/different providers, exact native currency/IRT units, stale boundary and no-cache failure; compile.

**Done when:** any supported owned or unowned market can be displayed independently and truthfully offline.

## W07 — Refresh, lifecycle and app integration

**Depends on:** W01–W06.
**Coverage:** U01–U05, L03, F04/F06, README accuracy.

**Files:** Create MarketRefreshOperation.kt, WidgetRefreshScheduler.kt, WidgetPeriodicRefreshWorker.kt, WidgetUpdateCoordinator.kt. Modify PriceRefreshWorker.kt, PortfolioRepository.kt, FolioApplication.kt, AppLanguage.kt, both widget receivers, WidgetActions.kt, configuration integration and README.md.

**Consumes:** shared MarketQuoteClient.fetchAll, WidgetRepository and WidgetSnapshotRepository.
**Produces:**
- `MarketRefreshOperation.suspend fun refresh(): Unit`
- `WidgetRefreshScheduler.suspend fun reconcile(context: Context): Unit`
- `WidgetUpdateCoordinator.suspend fun invalidateAll(context: Context): Unit`
- `WidgetUpdateCoordinator.suspend fun update(context: Context, appWidgetIds: Set<Int>): Unit`

- [ ] Move refresh orchestration behind one application-scoped operation and Mutex shared by one-time and periodic workers. Network is outside Room transactions; preserve current portfolio configuration guards and snapshot transaction.
- [ ] Refresh the deduplicated union of active portfolio markets and widget-selected keys. Persist each consumer's results independently, retaining last-good quotes on failures. Continue across provider errors and retain cancellation semantics.
- [ ] Use a durable invalidation marker/revision so failures to update Glance can retry. Publish portfolio widget snapshots after a complete refresh, not each quote insertion; always load current visibility/configuration before rendering.
- [ ] Add unique six-hour CONNECTED periodic work named folio-widget-periodic-refresh while installed instances exist. Both workers call the shared serialized operation. Keep folio-price-refresh-now with KEEP for explicit refresh; app-open refresh must no longer cancel widget periodic work.
- [ ] Connect creation, completed local financial writes, configuration Save, hide/show and language change to invalidation. Update widgets without an Activity collector. If Glance state is changed, await updateAppWidgetState then explicit update/updateAll.
- [ ] Receivers reconcile installed IDs, clean deleted configuration/unreferenced cache, remap restored IDs and schedule an update. Reconcile after restore/app start; only cancel widget periodic work when no widgets remain.
- [ ] Refresh action enqueues one-time work and renders pending status; terminal failures clear busy status and retain data. Do not let widget update failure undo a valid portfolio mutation.
- [ ] Update README with widget setup, privacy behavior, selectable providers, native currency, cache/stale behavior and the real schedule.
- [ ] Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` and fix failures. Keep all emulator scenarios for W08.

**Done when:** both widgets update without the UI open, share requests safely and survive lifecycle changes.

## W08 — Single final QA task: emulator acceptance

**Depends on:** every W01–W07 checkbox complete.
**This is the only QA task and the only task that runs emulator testing.** Add any discovered fixes to their owning implementation files, then rerun affected scenarios within W08; do not create another QA task.

**Files:** Create `docs/qa/2026-11-11-home-screen-widgets.md`. Add focused instrumentation fixtures/tests under `app/src/androidTest/java/com/example/folio/widget/`, including migration 3→4. Fixtures/test hooks must not enter release behavior.

- [ ] Preflight: run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Record results and relevant existing failures separately.
- [ ] Use an API 35 phone emulator as the primary launcher; repeat minimum-platform placement/configuration/resize smoke cases on API 28. Record emulator API, launcher, density, font scale and app build.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` for deterministic migration, cache/key isolation and configuration tests on a connected emulator. Verify upgrading a populated version-3 database preserves all financial rows.
- [ ] Execute the acceptance matrix below from the actual launcher. Capture screenshots and pass/fail evidence; inspect rendered accessibility descriptions for hidden monetary values.
- [ ] Run a live provider smoke selection/price fetch for each of Nobitex, Aban Tether, TSETMC and Rahavard, including at least one symbol not owned in the portfolio and a manually entered numeric ID. Record actual provider/market/currency/time. Provider outages are external blockers for that live check, not proof of correct UI; use deterministic responses for the error paths.
- [ ] Verify reboot/process recreation and host restore ID remapping where supported; simulate remap through instrumentation if the launcher cannot provide a restore flow. Do not use force-stop as evidence that Android should keep running background jobs.
- [ ] Finish the QA report with the matrix, evidence paths, defects fixed, remaining limitations and pass/fail outcome. Do not claim a full pass for blocked/untested scenarios.

| Scenario | Required result |
| --- | --- |
| Add/cancel/edit both widget kinds | First render after Save; canceled setup leaves no config; canceled edit preserves previous config |
| Two portfolio + three price instances | Different currencies/visibility/markets stay independent; duplicate key uses shared quote cache |
| Total hide/show, then refresh/resize/process recreation | Only target instance changes; no absolute monetary text/descriptions while hidden |
| Empty/zero/inactive/unpriced/missing FX | Correct zero vs partial vs unavailable; active-only denominator; no crash/division by zero |
| More than three asset types | Compact Other preserves total; expanded labels/percentages show all positive groups |
| All providers + unowned/manual symbol | Same selection capabilities as Add Asset; no extra portfolio asset/transaction appears |
| Same ticker on two providers; selection changed during request | Correct provider/key; late result cannot overwrite current label or selection |
| Offline before first quote; offline after cached quote; provider error | Unavailable vs cached/stale/failure correctly differentiated; reconnect completes queued refresh |
| Manual taps during periodic work | Serialized operation, deduplicated work, no double network batch/out-of-order publication |
| Closed app/local edits/locale change | Cached rendering independent of Activity; updates after edits and worker completion |
| English/Persian, RTL, dark/light, large font/long values | Legible values and controls at supported minimums; correct currency units |
| Remove one widget, then last widget | Other instances/cache survive as needed; only last removal cancels widget periodic schedule |
| Reboot/restored IDs/schema upgrade | Configuration retained/remapped or explicit setup state; portfolio data preserved |

## Plan self-review and handoff

- Spec P requirements map to W03/W05; F to W01/W02/W04/W06; L to W01/W04/W05/W06/W07; U to W07.
- W08 is the sole emulator QA gate, after the complete implementation.
- Shared contracts are defined once and consumed by later tasks.
- The design received a separate builder document check: no important unanswered planning questions were found.
- Proposed defaults remain reviewable: per-instance settings, one market per price widget, native quote currency, visible total default and six-hour widget background refresh.
- App implementation begins only under a subsequent implementation request; no source changes, dependency installation or emulator execution are part of generating these documents.
