# Folio Home Screen Widgets — Tasks

Date: 2026-11-11
Status: W01 code is present; its Gradle/schema verification remains pending. W02 implementation is complete; unit-test verification is blocked because Google Maven could not provide the existing androidx.lifecycle:lifecycle-common-java8:2.8.7 artifact. W03 implementation and JVM coverage are present; verification is pending because the same artifact prevented compilation. W04 implementation and focused configuration tests are present; its post-implementation Gradle test attempt was blocked before compilation by the same missing artifact. W05 implementation and focused presentation tests are present; its post-implementation Gradle test attempt also stopped before compilation because Google Maven could not provide that artifact. W06 implementation and focused JVM coverage are present; the final post-implementation :app:testDebugUnitTest attempt stopped before compilation because Google Maven could not provide androidx.lifecycle:lifecycle-common-java8:2.8.7; no assertions ran. W07 implementation and focused JVM coverage are present; its final post-implementation `:app:testDebugUnitTest` attempt stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.

- [Specification](../specs/2026-11-11-home-screen-widgets-design.md)
- [Detailed implementation plan](../plans/2026-11-11-home-screen-widgets.md)

## Scope

1. Portfolio total and allocation by asset type, with a persistent per-widget hide-total control.
2. One selected favorite market price per widget, chosen from all four existing providers and their supported identifiers, including instruments not in the portfolio.
3. Independent instances, durable configuration/cache, offline states, Persian/English and best-effort background updates.

## Current execution status

- W02 implementation and JVM fixtures are present in the working tree.
- The W02 `:app:testDebugUnitTest` attempt stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.
- W03 implementation and focused JVM coverage are present in the working tree.
- The W03 `:app:testDebugUnitTest` attempt also stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.
- W04 adds the two configuration forms, validated provider ownership, SavedStateHandle drafts, result handling, direct first-update request, and focused JVM coverage.
- The W04 `:app:testDebugUnitTest` attempt ran after implementation and stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.
- W05 adds the Glance portfolio receiver/layout, localized totals and allocations, persisted per-instance hide/show, refresh/configure controls, widget metadata/previews/dimensions and focused presentation tests.
- The W05 `:app:testDebugUnitTest` attempt stopped before compilation because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; no assertions ran.
- W06 adds the favorite asset Glance receiver/layout, native-currency quote formatting, provider labels, quote age, cached failure/stale/unsupported states, configuration revision guards, a compact 2×2 layout, resizable metadata, localized fictional previews and focused JVM coverage.
- The final post-implementation W06 `:app:testDebugUnitTest` attempt stopped during dependency resolution because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; compilation and assertions did not start.
- W06 review findings about minimum-size metadata and no-cache failure wording were addressed with combined compact metadata and separate localized failure copy. Launcher rendering, resize behavior, action dispatch and theme checks remain in W08.
- W06 uses the shared immediate refresh callback; W07 connects that worker to configured favorite markets and periodic scheduling.
- W07 implementation is present: the app-scoped refresh operation unions portfolio and widget markets; six-hour connected work runs only while an instance exists; local saves and language/visibility changes invalidate widgets; lifecycle events are serialized and restore mappings are preserved until reconciliation; README is updated.
- W07 adds JVM coverage for deduplicated/provider-isolated targets, exact saved cache keys, stale configuration guards, quote timestamps/failure states, periodic scheduling, refresh indicator terminal states and restore cleanup ordering.
- The independent W07 review findings were addressed: active Glance sessions now observe durable invalidation revisions and WorkInfo; lifecycle work uses an ordered unique chain and restore actions do not prune another widget kind; successful configuration Save requests an immediate coalesced refresh.
- W07's final `:app:testDebugUnitTest` attempt stopped during dependency resolution because Google Maven could not provide `androidx.lifecycle:lifecycle-common-java8:2.8.7`; compilation and assertions did not start. No emulator QA was run; W08 remains the sole emulator QA task. The latest integrated `:app:assembleDebug :app:testDebugUnitTest` run passed after correcting the `actionRunCallback` import in `WidgetActions.kt`. `adb devices -l` still returns no devices, so W08 visual/runtime QA remains pending and the built APK was not deployed.
- Ruling: Runtime rendering, resize behavior, action dispatch and light/dark appearance are deferred to W08 per the plan — cost if wrong: launcher-specific issues remain undiscovered until the sole emulator QA task.
- Ruling: Continued in the existing workspace because the user asked to continue its in-progress W02 edits — cost if wrong: unrelated local work would share this tree; no changes were committed.
- Ruling: Deferred Gradle verification until implementation was complete, as requested — cost if wrong: earlier test feedback was unavailable.
- Ruling: W07 runs only `:app:testDebugUnitTest`; a separate assemble task is omitted per the user's explicit constraint — cost if wrong: packaging-specific errors remain unverified.
- Ruling: Active Glance sessions observe durable widget revisions and WorkInfo state, then reload Room-backed values. Portfolio snapshots are computed at render after a complete refresh instead of stored redundantly — cost if wrong: a missed revision can leave an old render until a later invalidation.
- Ruling: Lifecycle events use an ordered unique WorkManager chain; restore actions remap without global pruning and startup reconciliation handles stale IDs — cost if wrong: a system restore callback arriving only after startup reconciliation may leave its old row until the next reconciliation.
- Ruling: W04 first added the receiver update on Save; W07 now schedules periodic work and requests an immediate quote refresh after configuration Save — cost if wrong: an uncached favorite may remain unavailable until another refresh.
- Ruling: W06 routes Refresh through the shared immediate worker; W07 now includes configured favorite markets in that serialized fetch — cost if wrong: widget-only selections could otherwise remain stale.
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
- [ ] W03 — Portfolio snapshot calculation (implementation and JVM coverage added; Gradle verification blocked before compilation by the missing `androidx.lifecycle:lifecycle-common-java8:2.8.7` artifact).
- [ ] W04 — Placement and configuration (implementation and focused tests are present; Gradle verification is blocked before compilation by the missing lifecycle artifact).
- [ ] W05 — Portfolio widget (implementation complete; Gradle verification blocked before compilation by the missing `androidx.lifecycle:lifecycle-common-java8:2.8.7` artifact).
- [ ] W06 — Favorite asset price widget (implementation and focused JVM coverage are present; Gradle verification is blocked before compilation by the missing `androidx.lifecycle:lifecycle-common-java8:2.8.7` artifact; no assertions ran).
- [ ] W07 — Refresh and lifecycle integration (implementation and focused JVM coverage are present; the final post-implementation test attempt was blocked before compilation by the missing lifecycle artifact; no assertions ran).
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
