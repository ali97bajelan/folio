# Configurable Asset Price Widget Implementation Plan

Date 2026-11-25 · PW01–PW05 implemented

Verification status: `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed. A host SQLite fixture verified migration 4→5 data preservation, selection cascades, shared quote retention, and final quote pruning. Emulator QA was not run.

[Specification](../specs/2026-11-25-asset-price-widget-selection-design.md) · [Task checklist](../tasks/2026-11-25-asset-price-widget-selection.md)

## Baseline

The current price widget already uses Glance 1.2.0 with `SizeMode.Exact`. Its Configure action and launcher metadata open `WidgetConfigurationActivity`. The activity and `WidgetConfigurationViewModel` store one `MarketSelection`. Room schema 4 puts that selection in five nullable columns on `widget_configuration`. `MarketRefreshOperation` builds one quote target per price widget, and `AssetPriceWidget` reads and renders one cached quote. Keep the existing provider, configuration activity, SDK levels, and Glance version. Use the ordinary Glance collection API for vertical swipes. [Android Developers’ Glance layout guide](https://developer.android.com/develop/ui/compose/glance/build-ui#use-scrollable-layouts)

Paths below are relative to `app/src/main/java/com/example/folio/` unless given in full.

## PW01 — Persist an ordered selection per widget

- Change `data/widget/WidgetModels.kt` and `WidgetRepository.kt` to represent a price widget as an ordered collection of validated selections. Require at least one selection for price widgets and an empty collection for portfolio widgets. Use canonical market keys for new selections. Preserve the exact migrated key for existing cache lookups.
- In `WidgetStorage.kt`, introduce a selection table with a generated stable row ID, widget ID, position, provider key, and display metadata. Add uniqueness for widget ID plus provider key. Read in position order. Use a foreign key with delete/update cascade for the existing widget removal and ID remapping paths. Index the market key for quote reference checks.
- Replace the parent and child selection set in one transaction from the caller’s perspective. Update existing parent rows in place to avoid delete cascades from Room’s current REPLACE strategy. Keep row IDs for retained selections, increment the widget revision once per save, and prune a cached quote only when no widget still references its exact key. Ensure portfolio hide-total writes preserve their existing behavior.
- In `data/local/PortfolioDatabase.kt`, add and export schema 5. In the 4→5 migration, stage the old rows, rebuild the parent table without the embedded selection columns, and copy each complete price selection at position 0. Preserve parent IDs, revisions, settings, and all quote rows. Register the migration in `FolioApplication.kt`; keep the 3→4 migration in the chain. Load configuration and its selections transactionally.

## PW02 — Build the selection menu

- Extend `presentation/widget/WidgetConfigurationScreen.kt` with the selected-assets list, per-row Remove, and Add asset. Integrate `presentation/components/MarketInstrumentPicker.kt` for each add flow. Confirm the pending choice with Add to widget. Keep pending picker input separate from the saved-list draft so that changing provider, type, query, or identifier cannot clear the draft or add a stale result.
- Extend `WidgetConfigurationViewModel.kt` to persist the list and pending picker fields through `SavedStateHandle` using supported Bundle values. Compare canonical keys for duplicate detection, including when a legacy raw key resolves to a canonical key. Preserve list order. Reopen the picker with a clean pending choice after confirmation. Use the existing Add Asset picker callbacks and validation semantics for search results and manual IDs.
- In `WidgetConfigurationActivity.kt`, keep provider ownership checks and the launcher result contract. Save the whole draft for the current widget ID. Keep input available after a save error and prevent duplicate in-flight saves. Cancel/Back leaves the persisted list intact. Add English and Persian labels and accessibility descriptions.

## PW03 — Refresh and publish all configured prices

- In `data/network/MarketRefreshOperation.kt`, build a selection token for every selected market. Keep the existing union of canonical fetch keys, including portfolio holdings, and copy results back under the exact persisted cache keys. Guard each write with current widget ID, configuration revision, and selection membership in the same transaction. Keep cache writes and cleanup safe when a selection is removed or when widgets share a market.
- Give successful price-widget saves a background refresh request that still runs if an existing immediate refresh has already captured its target list. The current `PriceRefreshWorker.refreshNow` uses KEEP and can coalesce away that request. Use a serialized follow-up on the existing work path for configuration saves and preserve the ordinary refresh coalescing behavior. Publish the saved selection and available cache immediately, without waiting for network results.
- Load the ordered rows and cached quotes as a coherent snapshot in `AssetPriceWidget.kt` and `WidgetRepository.kt`. Reuse `presentAssetPriceQuote` per row. Retain the existing revision invalidation path and make a missing quote local to its row.

## PW04 — Render the scrollable widget

- Keep the fixed Refresh and Configure header and use `androidx.glance.appwidget.lazy.LazyColumn` for all selected rows. Give each item its saved positive row ID. Reuse the current widget theme and quote presentation; include enough provider context to distinguish matching tickers.
- Use `LocalSize` to derive the available list height after the header and padding. Use a consistent effective row height for a given size, with spacing included in each row. Derive the finite viewport height as one, two, or three full rows based on that space and the selection count. Cap the viewport on taller widgets, and account for any collection insets or dividers in the height budget. Verify that the minimum dimensions fit the header and at least one readable row; update only the price widget’s dimension resources if necessary.
- Give each row an identifiable asset/provider, price with currency or unavailable text, and accessible status for cached, stale, failed, or unsupported data. Preserve the setup action for missing configuration. Update the fictional widget-picker preview to show the new list.

## PW05 — Verify with host tools

- Extend the existing JVM tests for widget configuration, quote presentation, and refresh targets. Cover ordering, canonical duplicates, draft restore, cancel semantics, different providers with the same ticker, transaction revision and membership decisions, mixed quote statuses, and the row budget for small and large sizes. Verify that all five selected rows are supplied to the list when only three fit in its viewport.
- Validate the migration SQL on host SQLite using a schema 4 fixture and compare the resulting shape against the exported schema 5. Check old price selection, existing cache, portfolio settings, foreign key behavior on remap/delete, and pruning when a market has multiple references.
- Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Review both locales, preview resources, Glance collection wiring, and the launcher result path in source. Host tests verify the list and size logic. Actual launcher scroll gestures and visual layout are outside this verification scope.
