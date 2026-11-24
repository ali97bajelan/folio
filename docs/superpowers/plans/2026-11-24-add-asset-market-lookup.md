# Add Asset Market Lookup Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task. Steps use checkboxes for tracking.

**Goal** Merge Symbol and Search markets on Add Asset and show at most three results at a time in a scrollable dropdown. Typing an asset name starts the default market lookup.

**Architecture** Hoist the existing picker ViewModel into the new asset form. The form owns the editable name, optional query override, and saveable draft. The ViewModel owns searches and valid market links. A Material 3 popup presents suggestions from outside the form’s layout.

**Tech stack** Kotlin, Compose Material 3, Lifecycle ViewModel, coroutines, Android string resources, JUnit 4.

**Spec** [Add Asset market lookup design](../specs/2026-11-24-add-asset-market-lookup-design.md)

## Global constraints

- Keep manual pricing, Edit Asset, and widget configuration behavior as they are.
- Use the existing provider search, quote validation, and save paths. Keep the current provider matching capabilities.
- Keep the existing dependencies and persistence schema.
- Localize new copy in English and Persian and preserve RTL layout and financial text direction.
- Verification is host-side only. Emulator QA is out of scope.

## Review focus

- Fast edits, provider changes, and mode changes must invalidate stale responses and old links. Cover these in Task 1.
- A Persian or multiword query must reach the provider with original case and internal spaces. Cover request input in Task 1 and check the form binding in Task 2.
- A typed provider ID can finish validating after the user starts a new lookup. Cover that race and save eligibility in Tasks 1 and 2.
- Rotation or process recreation must keep a chosen market and restore an unvalidated ID as a draft. Cover the restoration inputs in Task 1 and check draft wiring in Task 2.
- Four or more matches, compact windows, scaled text, and the keyboard can expose bad popup constraints. Review scroll bounds and the layout tree in Task 3.

---

## Task 1 — Add Asset lookup operations

**Modify** `app/src/main/java/com/example/folio/presentation/components/MarketInstrumentPickerViewModel.kt`

**Test** `app/src/test/java/com/example/folio/presentation/components/MarketInstrumentPickerViewModelTest.kt`

**Interfaces** Add `fun updateAddAssetQuery(query: String): Unit` and `fun updateAddAssetProviderSymbol(providerSymbol: String): Unit`. Continue to use `state: StateFlow<MarketInstrumentPickerState>`, `selectInstrument(instrument: Instrument)`, `changeProvider(provider: String)`, `updateAssetType(assetType: String)`, and `initializeOrUpdate(assetType: String, initialSelection: MarketSelection?, initialSelectionValidated: Boolean = true): Boolean`.

- [x] Extend the existing JUnit fixture with these cases. Use injected search and validation functions, with no provider network calls.
  - `add_asset_query_preserves_provider_type_and_name_text` passes `"  طلای آب شده  "` and verifies the search receives `(RAHAVARD, "طلای آب شده", GOLD)`. A blank or one-character query leaves status `IDLE` without a request.
  - `add_asset_search_keeps_all_returned_matches_in_order` returns six results and asserts an exact list match in state.
  - `new_add_asset_query_clears_selected_market_and_ignores_late_validation` checks that a new query immediately clears the linked market and provider ID, including when a superseded quote response subsequently succeeds.
  - `direct_identifier_edit_cancels_search_and_requires_validation` checks that a late search cannot populate suggestions while an ID draft is active, and that `state.selection` is null until validation succeeds.
  - `add_asset_provider_switch_uses_new_provider_and_rejects_old_response` switches providers with the same query and completes requests in reverse order. Key the fixture by provider and query for this case.
  - `clearing_add_asset_lookup_ignores_pending_search` starts an eligible search, resets using `updateAddAssetQuery("")`, and completes the old request. Assert `IDLE`, no results, and no selection.
- [x] Run the focused suite once to confirm the new cases require the new methods. Run `./gradlew :app:testDebugUnitTest --tests 'com.example.folio.presentation.components.MarketInstrumentPickerViewModelTest'`.
- [x] Implement `updateAddAssetQuery` by cancelling pending quote validation, clearing the provider ID and any selection, and delegating to the existing debounced `updateQuery`. Treat an empty query as a reset and cancel any in-flight lookup. Implement `updateAddAssetProviderSymbol` by cancelling search, clearing results and status, and delegating to the existing `updateProviderSymbol` validator. Keep `updateQuery` and `updateProviderSymbol` as the legacy entry points. Preserve request generation guards and selection notifications.
- [x] Rerun the focused suite and confirm the new cases plus the existing saved-selection, draft-validation, and asset-type cancellation cases pass.

**Status: Complete.** The focused ViewModel suite passed 11 tests with 0 failures using `./gradlew :app:testDebugUnitTest --tests 'com.example.folio.presentation.components.MarketInstrumentPickerViewModelTest'`.

## Task 2 — Wire the Add Asset form

**Modify** `app/src/main/java/com/example/folio/presentation/PortfolioApp.kt` and `app/src/main/java/com/example/folio/presentation/components/MarketInstrumentPicker.kt`

**Interfaces** Reuse Task 1 methods and use `id == null && pricingMode == PortfolioCodes.MARKET` for new behavior. Reuse the existing localized provider selector and extract the existing provider-ID field for reuse as `internal fun MarketProviderIdentifierField(value: String, status: IdentifierValidationStatus, onValueChange: (String) -> Unit)`. Give `ProviderDropdown` internal visibility. Keep `MarketInstrumentPicker`’s call signature and legacy behavior.

- [ ] Create one picker ViewModel scoped to the Add Asset back-stack entry. Collect state once at the form level. Reuse the form’s saveable provider and raw ID fields. Add a nullable saveable query override and a nullable search-picked ticker. Use the ViewModel state as the authoritative in-session market selection.
- [ ] Initialize the picker once per ViewModel instance in the create flow. Restore a search-picked market from the saved provider, canonical ID, and ticker as a validated initial selection. Restore a typed ID as a draft and validate it through `updateAddAssetProviderSymbol`. With neither present, run the restored query if Market pricing is active. Avoid reinitializing or restarting a completed search on recomposition or rotation.
- [ ] Render a plain `FolioField` in place of Symbol in the new Market branch. Bind it to the search-picked ticker, otherwise the explicit override, otherwise Name. On a merged-field edit, update the override and clear saved market fields before calling `updateAddAssetQuery`. On Name edit with no linked market or raw ID, clear the override and search the new Name. Keep the query text out of the stored symbol until save resolves a ticker. Keep the legacy Symbol field in Manual and Edit Asset.
- [ ] In the new Market pricing section, render the reused provider selector and direct-ID field. Invalidate the saved ID and ticker when the provider or type changes, then rerun the current query with the new provider and type. For a raw ID edit, clear the saved search ticker, close suggestions, and update the ID through the new ViewModel entry point. On result selection, copy the provider, canonical ID, and ticker from the selected market and retain Name. When entering Manual or using Quick fill, clear market draft inputs and call `updateAddAssetQuery("")` to cancel pending market work. Retain a picked ticker as the manual symbol when following the standard pricing toggle. When returning to Market, set the query from Name and keep the currently chosen provider unless the type changed.
- [ ] Gate Add Asset Market save on `pickerViewModel.state.value.selection` at click time. Use the saved search-picked ticker for a result. For a validated direct ID, use the nonblank explicit query trimmed and uppercased, or derive the symbol from Name using `trim().uppercase().replace(Regex("\\s+"), "-")`. Persist the provider and its canonical ID from the selection. Reuse the existing Name and Symbol errors and the market-required error as applicable. Save through `saveAssetWithOpening`.
- [ ] Compile with `./gradlew :app:assembleDebug`. Review the create, edit, manual, and widget call sites. Trace Save immediately after query or ID edits and after recreation to confirm the previous market cannot be saved, restored search picks remain valid, and typed IDs require successful validation.

## Task 3 — Add the bounded dropdown

**Create** `app/src/main/java/com/example/folio/presentation/components/AddAssetMarketLookupField.kt`

**Modify** `app/src/main/java/com/example/folio/presentation/PortfolioApp.kt`, `app/src/main/res/values/strings.xml`, and `app/src/main/res/values-fa/strings.xml`

**Interface** `@Composable internal fun AddAssetMarketLookupField(value: String, state: MarketInstrumentPickerState, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onValueChange: (String) -> Unit, onInstrumentSelected: (Instrument) -> Unit, modifier: Modifier = Modifier)`.

- [ ] Add `asset_symbol_or_market` with English **Symbol or market** and Persian **نماد یا بازار**. Reuse `market_search_hint`, `market_searching`, `market_search_empty`, and `error_search`.
- [ ] Use `FolioField` as an editable anchor in `ExposedDropdownMenuBox` with the project’s `MenuAnchorType.PrimaryEditable` API. Keep expansion intent in the form and gate popup visibility on active search status. Open on eligible edits or an explicit field tap with an active query. Close on outside tap, Back, picking a result, entering a direct ID, or leaving Market mode. Keep focus in the active input when results arrive, including during Name editing.
- [ ] Use the menu’s own scroll state keyed by provider, asset type, and query. Size each match row uniformly with a 64 dp baseline and room for scaled text. Render an isolated ticker alongside the name and show the market detail on a secondary line. Cap popup height to three rows plus the menu’s vertical content padding, within the space available above or below the input. Use the full `state.instruments` list in order. Render loading, empty, and error states inside the same overlay.
- [ ] Replace only the new Market branch’s plain lookup field from Task 2 with the popup field, still sharing the row with Accounting unit.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Expect all three Gradle tasks to succeed. Review the layout statically with zero, one, three, and six example results. Confirm the popup occupies no form layout space, the viewport cap accounts for menu padding, and the scroll content includes the sixth result. Review keyboard constraints, scaled text, and RTL from the component code. This verifies the layout structure. Live rendering and gesture behavior remain unverified by these host-side checks.
