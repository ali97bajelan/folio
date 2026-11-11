# Folio Home Screen Widgets — Draft Specification

Date: 2026-11-11
Status: Proposed design for review; app implementation is outside this request.
Companion documents: [implementation plan](../plans/2026-11-11-home-screen-widgets.md), [task checklist](../tasks/2026-11-11-home-screen-widgets.md).

## Intent and scope

Let users check portfolio composition and a chosen market price from the Android home screen without opening Folio.

User requirements:
1. A portfolio widget displays total portfolio value and allocation by asset type; the user can hide the total.
2. A favorite asset widget displays a selected instrument's price. Selection supports every existing price provider and provider symbol, using the same capabilities as Add Asset.
3. There is exactly one QA task involving emulator testing, after all implementation tasks finish.

Proposed defaults (design decisions, not previously confirmed user preferences):
- Two separately listed widgets: “Folio Portfolio” and “Folio Asset Price”.
- Each placed instance has independent configuration. One instrument per price widget; users may place multiple widgets.
- Portfolio currency is IRT by default, with USD selectable. Total is visible by default and can be hidden in setup or with an eye button.
- Hidden mode shows “••••”, retains allocation percentages, and displays no absolute portfolio/allocation values in text or accessibility descriptions.
- Favorite price displays the provider's native quote currency; no additional currency conversion setting.
- Best-effort six-hour background market refresh while at least one widget is installed, plus manual refresh and refresh after configuration.
- Current app language applies to widgets, including Persian/RTL.

## Existing implementation and constraints

The app is Kotlin/Compose, Room 2.7.0, WorkManager 2.10.0, minSdk 28, compileSdk/targetSdk 35, AGP 8.7.3. Keep these platform versions for this feature. Add stable Glance 1.2.0; check dependency compatibility during implementation instead of assuming a platform upgrade is required.

Relevant existing sources:
- `app/src/main/java/com/example/folio/data/PortfolioRepository.kt`: dashboard rows, totals and local writes.
- `domain/PortfolioServices.kt`: BigDecimal holdings, prices and currency conversion.
- `data/network/InstrumentSearch.kt`: Nobitex, Aban Tether, TSETMC, Rahavard search; results include providerSymbol and assetSymbol.
- `data/network/PriceRefreshWorker.kt`: provider requests/parsers, saved prices, conversion and valuation snapshots.
- `presentation/PortfolioApp.kt`: Add Asset provider/type search, manual provider-symbol entry, allocation presentation.
- `presentation/AppLanguage.kt`: persisted Persian/English selection.
- `data/local/PortfolioDatabase.kt`: schema version 3; current prices require a portfolio asset foreign key.
- `FolioApplication.kt`: application-scoped database and repository.

No widget receivers, configuration screens or widget dependencies currently exist. README describes a six-hour refresh, but the actual worker only enqueues one-time work and cancels the old periodic work on app open. Treat code as authoritative and correct this discrepancy when implementing scheduling.

Glance uses its own composables; existing Compose Canvas/chart components cannot be used directly in widget content. Use native Glance bars and labeled rows for allocation, avoiding a bitmap chart.

## Approaches

| Approach | Tradeoff |
| --- | --- |
| Glance widgets, Compose configuration activity, shared Room/provider services | Recommended: fits the Kotlin/Compose app; durable state and provider reuse. Requires a small independent quote cache. |
| Classic RemoteViews/XML widgets | Viable, but creates a second UI authoring style without a clear benefit for these text and allocation layouts. |
| Price widgets limited to saved portfolio assets | Smaller change, but fails selection of provider symbols the user does not own. Rejected. |

The plan uses the recommended approach as a proposal. No approach has been approved by the user yet.

## Functional requirements

### Portfolio widget

P01. Display selected-currency total for active portfolio assets, allocation grouped by assetType, currency label, data age/status, refresh, configure and hide/show actions. Body tap opens the existing dashboard.

P02. Reuse the existing holdings, latest-price and conversion services. Use BigDecimal for financial arithmetic. Compute a coherent widget total and allocation from the same active rows and one Room read transaction. Sum positive known values by type; percentages use the sum of those values as denominator. Zero holdings need no price. Do not turn missing prices/conversion into a confidently complete zero portfolio.

P03. If some nonzero holdings cannot be valued in the selected currency, display the known subtotal labeled “Partial value” plus the missing count. Percentages describe valued assets only and have a visible partial indicator. If all nonzero holdings are unpriced, show “Value unavailable”; if no active nonzero holdings exist, show an empty portfolio with zero total and no allocation bars. If known values total zero, do not divide by zero.

P04. Sort types by value descending, then type code for ties. In compact presentation show the top three plus “Other” if needed; Other is the sum of remaining types. Expanded presentation shows all eight supported types that have positive value. Percentages use one decimal and may differ from 100% by normal display rounding. Never omit value from the denominator when grouping.

P05. The widget's total follows its coherent current allocation snapshot. The existing dashboard headline deliberately uses a historical committed total and can temporarily differ during edits/refresh; do not silently change dashboard semantics. After a complete refresh with fully valued unchanged holdings, values should agree. Preserve the last published widget snapshot during a network refresh; publish again at completion rather than after each provider response.

P06. Hide/show persists immediately for that widget only, works offline, and survives resize, refresh and process death. After hide completes every rendered monetary portfolio value and its content description is masked. Allocation type labels, percentages, currency and timestamps remain visible.

### Favorite asset price widget

F01. Configuration offers all four providers, an asset-type search context where required (especially Rahavard), query search and manual provider-symbol entry. “All symbols” means any supported market/identifier accepted by these existing providers, not just portfolio holdings, the first search results, or a built-in shortlist. Search can return a bounded result list as Add Asset currently does; users can refine a query or enter a provider ID directly.

F02. Reuse `InstrumentSearch.search(provider, query, assetType)` and extract its provider-selection UI/state into a reusable component shared with Add Asset. Preserve existing aliases, normalization, provider-specific IDs and Add Asset behavior. Debounce queries by 300 ms, cancel outdated searches and ignore late responses after provider/type/query changes. Distinguish loading, no results and provider error.

F03. Store provider + canonical providerSymbol as quote identity, with display name/symbol and asset type in instance configuration. TSETMC and Rahavard require numeric provider IDs; Nobitex accepts supported pair formats; Aban uses its supported symbol. Validate manual entries through the shared quote client before Save; retain draft input and show an error when validation cannot finish. A previously configured widget can retain its selection/cache offline.

F04. Selecting a favorite must never create a portfolio asset, transaction, holding or valuation. A quote remains usable after deletion of an unrelated/same-symbol portfolio asset. Widgets for identical markets share one cached quote; the same ticker on different providers remains independent.

F05. Show symbol/name, provider, latest successful price, explicit quote currency and quote age. Preserve existing rial-to-toman handling and label IRT as toman; do not divide again. Provider-reported capture time is displayed as quote time when available; track fetch success separately for refresh scheduling.

F06. On refresh failure retain last valid value and quote time, adding a localized failure/offline indicator. No successful quote means “Price unavailable”, never zero. Mark age beyond six hours as stale. Unsupported/delisted symbols show an unavailable indicator and Configure action. Cached price is never presented as current after failure.

F07. Configure changes only that instance. Replacing a market must not flash the old market's price under the new label. Ignore late in-flight results for a superseded configuration. Body tap opens that widget's configuration so the user can inspect/change its instrument; no portfolio asset detail is assumed.

### Configuration and lifecycle

L01. Both widget types use a configuration activity on initial placement. Validate appWidgetId and its provider ownership; start with RESULT_CANCELED. Save atomically persists the complete valid configuration, requests the first render, and returns RESULT_OK with the widget ID. Cancel creates no configured instance; canceled edits preserve saved settings. Portfolio Save does not need network.

L02. Configure is reachable from a widget action on API 28+; also expose launcher reconfiguration where supported. Draft fields survive activity recreation. A missing/invalid configuration shows “Set up widget”, never someone else's settings.

L03. State is durable, keyed by appWidgetId; widget rendering never depends on MainViewModel or an open Activity. Removal deletes only that instance. Prune unreferenced quote cache after removal; do not cancel shared work required by other instances. Handle restored ID mappings and reconcile with actual installed IDs; if configuration cannot be recovered, require setup.

L04. Support horizontal/vertical resize using SizeMode.Exact and actual available dp, not assumed launcher cell sizes. Portfolio initial target 4×2, compact minimum target 3×2; price initial target 2×2. Cell counts are launcher hints. Keep refresh/configure controls reachable at supported minimums and use at least 48 dp action targets. Larger portfolio content switches at width >= 280 dp and height >= 220 dp; smaller sizes use compact allocation.

L05. Provide localized widget names/descriptions, static previews with fictional values, light/dark theme support, RTL layout and readable large numbers. Preview assets must never use personal portfolio data. Use text labels/percentages as well as allocation colors.

### Updates and background work

U01. Local portfolio writes, widget settings, language changes, completed market refresh and widget creation trigger explicit widget updates. This must work with the app UI closed. Rendering reads cache/local data only; network runs in WorkManager.

U02. One unique periodic job named `folio-widget-periodic-refresh` runs every six hours with CONNECTED constraint while any widget exists. Keep existing one-time name `folio-price-refresh-now` and KEEP deduplication. Periodic and one-time requests invoke one shared refresh operation with cross-path serialization so overlapping work cannot publish out of order.

U03. Refresh the union of active portfolio markets and configured widget markets; deduplicate by canonical provider/market. Download Aban's catalogue once per run. Persist portfolio prices through existing configuration guards and widget quotes independently. A failure for one provider/market must not prevent the other results or widget publication. Never derive portfolio holdings/conversion from favorite selections.

U04. A widget Refresh action immediately shows a queued/updating indication and enqueues network-constrained work. Offline queued work preserves cached data and can run after connectivity returns. Repeated taps do not add duplicate requests. Show unavailable/offline state without leaving an indefinite loading spinner on terminal failure; use work status when rendering pending work.

U05. After durable widget state/revision changes, explicitly call Glance update/updateAll. When using per-instance Glance state, updateAppWidgetState must finish before update. Widget refresh exceptions must not roll back valid financial writes; leave a retryable invalidation for the next render/work run.

## Persistence and implementation boundaries

Room migration 3→4 adds only:
- `widget_configuration`: appWidgetId primary key, widget kind, currency, hideTotal, optional selected market/display fields and configuration revision.
- `widget_quote`: composite primary key (provider, canonical providerSymbol), decimal price text, currency, quoteAt, lastSuccessAt, lastAttemptAt and last error/status.

Do not attach widget_quote to portfolio_asset. Configuration has no required FK to widget_quote so a missing cache is representable. Use transactional configuration writes and stale-response guards. Register the migration in FolioApplication and export schema 4; no destructive migration.

Widget-only visibility/revision state can use Glance preferences; Room configuration remains the source of truth for hidden mode. Restored widget IDs must be remapped, never trusted as portable across installations.

Use `presentation/widget/` for providers/layouts/actions/configuration and `data/widget/` for persistence/snapshot models. Extract a `MarketQuoteClient` from the existing worker for both consumers; retain current HTTP headers, timeouts, parsers and pricing units.

## Non-goals

No new market provider, real-time streaming, price alert, trading, cloud sync, app-wide favorites feature, historical widget chart, profit/loss metric, lock-screen widget category, Wear OS widget or platform/AGP upgrade. Manual portfolio prices contribute to portfolio totals but Manual is not a remote provider in the favorite picker.

## Acceptance and QA policy

The checklist and plan define W01–W07 implementation tasks and W08 as the only QA/emulator task. No emulator testing starts until all seven implementation deliverables are complete. Ordinary compile/static checks and focused JVM tests can accompany development; they are not additional QA tasks.

W08 must cover placement/cancel/edit, independently configured instances, visibility persistence/accessibility, active/missing/zero allocation, all four providers including an unowned symbol, offline/cache behavior, late-result races, background/manual refresh, locale/RTL, resize, process recreation, reboot/restore mapping, removal and database upgrade. External endpoint failures must be recorded separately from app defects; deterministic fake/fixture coverage remains required for failure paths.

## Platform references

- [Glance releases](https://developer.android.com/jetpack/androidx/releases/glance) — stable 1.2.0 checked on 2026-11-11.
- [Widget configuration](https://developer.android.com/develop/ui/compose/glance/configuration) — activity result and first update responsibilities.
- [Widget state and updates](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — durable state and explicit updates.
- Android Glance canonical-layout skill, standard-toolbar references: use the scaffold/title-bar/action sizing as a starting point and customize content for these two widgets. Sample-only media actions are outside scope.
