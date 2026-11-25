# Configurable Asset Price Widget Tasks

Date 2026-11-25 · PW01 and PW02 implemented

`:app:assembleDebug` passed and exported schema 5. Unit tests were not run. Emulator QA is not part of this request. PW03–PW05 remain open.

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

- [ ] Fetch every selected market through the shared refresh operation, deduplicating provider requests across price widgets and portfolio holdings.
- [ ] Preserve exact cache-key lookup and reject writes from stale selection tokens. Publish the saved list from cache and schedule a follow-up when an in-flight refresh has already captured the old list.
- [ ] Load quote data in saved order with per-market status. Confirm that a cache miss, provider failure, or unsupported market leaves the other prices visible.

## PW04 — Scrollable price list

Depends on PW03.

- [ ] Render the full selection in a Glance lazy list with stable item IDs and fixed Refresh and Configure actions.
- [ ] Size the viewport for at most three full rows. Check fixture cases for one item, three items, five items, and a widget height that fits only one or two rows.
- [ ] Update the fictional widget-picker preview and review the English and Persian layouts and accessible row labels.

## PW05 — Host verification

Depends on PW01–PW04.

- [ ] Run focused JVM coverage for draft edits and restore, duplicate identities, independent widgets, multi-market refresh, mixed quote states, and viewport calculations.
- [ ] Exercise migration SQL with host SQLite, check the exported Room schema, and cover widget ID remap, deletion, and shared cache retention.
- [ ] Pass `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and record the results and any gaps. No emulator QA is required.
