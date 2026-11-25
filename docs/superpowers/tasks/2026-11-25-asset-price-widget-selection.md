# Configurable Asset Price Widget Tasks

Date 2026-11-25 · PW01–PW05 implemented

`:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed. A host SQLite fixture verified migration 4→5 data preservation, cascades, and quote pruning. Emulator QA was not run.

[Specification](../specs/2026-11-25-asset-price-widget-selection-design.md) · [Implementation plan](../plans/2026-11-25-asset-price-widget-selection.md)

## PW01 — Ordered configuration and migration

- [x] Add ordered market selections to the widget model and Room storage. Persist the full list and its revision atomically and keep stable row IDs for unchanged selections.
- [x] Register and export schema 5. Convert a complete pre-upgrade price selection into one row and preserve quote cache, widget IDs, and portfolio settings.
- [x] Update remove, restore/remap, and pruning queries for multiple selections per widget. Keep a cached quote while any widget references it.

## PW02 — Asset selection menu

Depends on PW01.

- [x] Use the current placement and Configure entry points to show selected assets with provider labels and an action to remove each one.
- [x] Use the shared picker to add assets individually. Reject duplicates by canonical market key, preserve add order, and keep edits in a restorable draft until Save.
- [x] Implement zero-selection Save gating, draft restoration, saved-selection reload, cancel behavior, and English/Persian labels. UI behavior has not been exercised in a running app.

## PW03 — Quote refresh and loading

Depends on PW01 and PW02.

- [x] Fetch every selected market through the shared refresh operation, deduplicating provider requests across price widgets and portfolio holdings.
- [x] Preserve exact cache-key lookup and reject writes from stale selection tokens. Publish the saved list from cache and enqueue a follow-up refresh when an in-flight refresh may have captured an older list.
- [x] Load quote data in saved order with per-market status. A cache miss, provider failure, or unsupported market remains local to its row.

## PW04 — Scrollable price list

Depends on PW03.

- [x] Render all selected assets in a Glance lazy list with persisted stable item IDs and fixed Refresh and Configure actions.
- [x] Size the viewport for at most three full rows. JVM fixtures cover one, three, and five items and compact and expanded widget heights.
- [x] Update the fictional widget-picker preview and provide localized, accessible row labels in English and Persian.

## PW05 — Host verification

Depends on PW01–PW04.

- [x] Run focused JVM coverage for draft restoration, canonical duplicates, multi-market refresh, quote states, and viewport calculations.
- [x] Exercise migration SQL with host SQLite. Verify schema 5, quote and settings preservation, widget ID remap/delete cascades, shared quote retention, and final quote pruning.
- [x] Pass `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. No emulator QA was run. Launcher gesture and visual behavior remain unverified.
