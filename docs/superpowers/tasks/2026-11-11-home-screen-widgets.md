# Folio Home Screen Widgets — Tasks

Date: 2026-11-11
Status: W01 code is present; its Gradle/schema verification remains pending. W02 implementation is complete; final unit-test verification is blocked because Google Maven could not provide the existing androidx.lifecycle:lifecycle-common-java8:2.8.7 artifact.

- [Specification](../specs/2026-11-11-home-screen-widgets-design.md)
- [Detailed implementation plan](../plans/2026-11-11-home-screen-widgets.md)

## Scope

1. Portfolio total and allocation by asset type, with a persistent per-widget hide-total control.
2. One selected favorite market price per widget, chosen from all four existing providers and their supported identifiers, including instruments not in the portfolio.
3. Independent instances, durable configuration/cache, offline states, Persian/English and best-effort background updates.

## Current execution status

- W02 implementation and JVM fixtures are present in the working tree.
- The final `./gradlew :app:testDebugUnitTest` attempt stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.
- Emulator QA was not run; W08 remains the only emulator QA task.
- Ruling: Continued in the existing workspace because the user asked to continue its in-progress W02 edits — cost if wrong: unrelated local work would share this tree; no changes were committed.
- Ruling: Deferred Gradle verification until implementation was complete, as requested — cost if wrong: earlier test feedback was unavailable.
- Final: minor (deferred): the picker ViewModel remains in the navigation entry until that entry is removed after the picker is toggled away; this can leave temporary unused ViewModels until the screen exits.

## Ordered backlog

| ID | Task | Depends on | Deliverable |
| --- | --- | --- | --- |
| W01 | Durable widget foundation | — | Glance dependencies, Room 3→4 migration, instance configuration, independent quote cache and repository |
| W02 | Shared provider client and instrument picker | W01 | All four providers, shared Add Asset selection, manual IDs, debounced search and stale-response guards |
| W03 | Portfolio snapshot calculation | W01 | Coherent BigDecimal total/allocation, partial/unavailable/empty handling |
| W04 | Placement and configuration | W01, W02 | Configuration activity, independent settings, cancellation and first-render result contract |
| W05 | Portfolio widget | W01, W03, W04 | Total, percentage allocation, hide/show, actions, resize, localization and preview |
| W06 | Favorite asset price widget | W01, W02, W04, W05 | Native-currency market quote, provider label, quote age, offline/stale states and independent instances |
| W07 | Refresh and lifecycle integration | W01–W06 | Shared serialized refresh, six-hour periodic work while installed, local invalidation, cleanup/restore and README |
| W08 | **Single final QA — emulator acceptance** | **All W01–W07 complete** | Emulator acceptance evidence, migration verification and final QA report |

## Completion checklist

- [ ] W01 — Durable widget foundation.
- [ ] W02 — Shared provider client and instrument picker (implementation complete; Gradle verification blocked).
- [ ] W03 — Portfolio snapshot calculation.
- [ ] W04 — Placement and configuration.
- [ ] W05 — Portfolio widget.
- [ ] W06 — Favorite asset price widget.
- [ ] W07 — Refresh and lifecycle integration.
- [ ] W08 — Single final QA on emulator, after all implementation is complete.

## QA constraint

W08 is the only QA task. Do not add per-feature emulator QA tasks or run emulator checks before W01–W07 finish. Compile checks and focused JVM tests may run during implementation; they do not create additional QA tasks. All emulator scenarios, discovered-fix retests and acceptance evidence stay inside W08.

W08 covers real launcher placement/cancel/edit, both layouts, independent instances, visibility and accessibility, all provider selections, unowned/manual identifiers, partial valuation, cached/offline/error states, background/manual refresh races, locale/RTL, theme/font/resize, process/reboot/restore behavior, removal and a populated database upgrade.

## Proposed defaults to review

- Two widgets; each instance is configured independently.
- Portfolio: IRT or USD, IRT default; total initially visible; hidden total retains allocation percentages.
- Price: one provider/market per widget, provider-native quote currency.
- Six-hour network-constrained refresh while widgets exist, plus manual refresh.
- No portfolio records are created when selecting a favorite market.
