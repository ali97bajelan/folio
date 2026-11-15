# Folio Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Apply a reusable, accessible design system across Folio's Compose screens, widget configuration, and Glance widgets.

**Architecture:** Add semantic Compose tokens and FolioTheme without changing the app's existing navigation library or adding dependencies. Introduce small reusable visual components, migrate existing screens to them, and map the same palette into Glance while preserving existing screen state, domain calculations, provider behavior, and formatting.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android resources, Glance, existing Room/WorkManager and test setup.

**Spec:** docs/superpowers/specs/2026-11-15-folio-design-system-design.md

## Global Constraints

- Preserve financial calculations, data formatting, provider capabilities, and Persian/English support.
- Follow system light/dark appearance using brand-authored palettes; add no theme preference.
- Use existing Material 3 and Glance dependencies; add no dependencies and do not migrate navigation architecture.
- Keep Glance composables native and adapt shared tokens to widget constraints.
- Keep all user-facing copy localized in values/strings.xml and values-fa/strings.xml.
- Do not run emulator QA until the single final widget QA task, W08.
- Preserve unrelated work; do not commit changes unless explicitly requested.

## Review Focus

1. **System appearance changes while app and widgets are active:** Compose, configuration, status bars, and Glance should use the matching semantic palette. Verify in W08 with both light and dark system settings.
2. **Persian RTL with mixed Latin financial notation:** labels and layout mirror while numbers, provider IDs, symbols, and currencies stay ordered. Verify with existing formatting tests and W08 screenshots.
3. **Large font, narrow width, and keyboard visibility:** titles, values, selectors, and save actions remain reachable without overlapping or clipping. Verify W08 on compact viewport/font scale and while the IME is open.
4. **Compact Glance widget sizes:** actions remain reachable and labels/values readable with text and color redundancy. Verify W08 with both widget kinds at supported compact and expanded sizes.
5. **Semantic status and destructive actions:** gain/loss/warning remain distinguishable; errors, loading, and destructive confirmation announce meaningful labels. Verify semantic descriptions and contrast during W08.

---

## File Map

- Create app/src/main/java/com/example/folio/presentation/design/FolioTokens.kt for light/dark Material 3 schemes, semantic status colors, typography, spacing, shape, elevation, icon, touch-target, and motion tokens.
- Create app/src/main/java/com/example/folio/presentation/design/FolioTheme.kt for system theme selection, MaterialTheme provision, and access to semantic non-ColorScheme colors.
- Create app/src/main/java/com/example/folio/presentation/design/FolioComponents.kt for shared page/top bar, button/action, card/row, field/selector, financial value, dialog, and feedback/empty-state composables.
- Create app/src/main/java/com/example/folio/presentation/design/FinancialTextDirection.kt for shared bidi isolation helpers; keep formatter behavior and output unchanged.
- Create app/src/main/java/com/example/folio/presentation/widget/FolioWidgetTheme.kt to map shared palette roles to Glance color providers and common widget sizing values.
- Modify app/src/main/java/com/example/folio/presentation/PortfolioApp.kt to use shared theme/components and apply top-level versus nested navigation behavior; retain screen and data flow.
- Modify app/src/main/java/com/example/folio/presentation/components/MarketInstrumentPicker.kt to use shared fields, rows, selectors, and state messages without changing search/state behavior.
- Modify app/src/main/java/com/example/folio/presentation/widget/WidgetConfigurationActivity.kt and WidgetConfigurationScreen.kt to use FolioTheme and shared Compose components.
- Modify app/src/main/java/com/example/folio/presentation/widget/WidgetLayout.kt, AssetPriceWidget.kt, and AssetPriceWidgetPresentation.kt to use shared Glance tokens and bidi helper while preserving widget data and actions.
- Modify app/src/main/res/values/colors.xml, app/src/main/res/values/themes.xml, app/src/main/res/values-night/themes.xml, and app/src/main/res/values-v31/themes.xml to remove stale purple/teal theme values and align system bars/splash with Folio palettes.
- Modify both localized string resources only for any missing back/action accessibility labels.
- Create docs/design-system.md with token reference and Kotlin usage examples.

## Task 1: Semantic tokens and system-following theme

**Files:**
- Create: FolioTokens.kt, FolioTheme.kt
- Modify: colors.xml, values/themes.xml, values-night/themes.xml, values-v31/themes.xml
- Modify: PortfolioApp.kt and WidgetConfigurationActivity.kt only to replace local theme declarations with FolioTheme

**Interfaces:**
- Produce FolioTheme(content: @Composable () -> Unit) using system appearance.
- Produce FolioSemanticColors with gain, loss, warning, disabled, border, and allocation-series colors.
- Produce named FolioSpacing, FolioIconSize, FolioMotion, and FolioShapeTokens.
- Use the exact palette and dimension values in the approved design note; keep Material 3 typography roles.

- [x] Define dark/light Material 3 ColorSchemes and semantic extras in FolioTokens.kt.
- [x] Define FolioTheme and a CompositionLocal for semantic colors in FolioTheme.kt; select by isSystemInDarkTheme().
- [x] Align Android XML theme, night theme, splash, and system bar colors with the same brand-authored palette.
- [x] Replace the two inline Compose theme declarations with FolioTheme.
- [x] Inspect changed Kotlin files with Android Studio editor analysis and resolve all errors.

## Task 2: Shared components and bidi helper

**Files:**
- Create: FolioComponents.kt, FinancialTextDirection.kt
- Modify: FolioTokens.kt only if component API needs a token already specified in the design
- Verification: Android Studio editor analysis on new files

**Interfaces:**
- Produce small composables for FolioPage, FolioTopBar, FolioPrimaryButton, FolioSecondaryButton, FolioDestructiveButton, FolioIconAction, FolioCard, FolioListRow, FolioFinancialValue, FolioField, FolioSelector, FolioDialog, and FolioStateMessage.
- Produce isolateFinancialText(value: String): String for existing Latin financial/provider values.
- Keep layout/presentation-only wrappers; callbacks and business validation remain owned by current screens.

- [x] Implement the shared components using Material 3 defaults plus approved tokens and a minimum 48dp action target.
- [x] Keep component APIs small; expose style/semantics customization only where existing screens require it.
- [x] Implement the bidi helper using the existing directional-isolate characters.
- [x] Inspect changed files with Android Studio editor analysis.

## Task 3: App shell, navigation, dashboard, lists, and detail

**Files:**
- Modify: PortfolioApp.kt
- Consume: FolioTheme and FolioComponents

- [x] Wrap the app shell with FolioTheme and replace local Page/Card/Tiny/Pill styling with shared components.
- [x] Keep existing routes/back stack; show bottom navigation only for dashboard, assets, transactions, and settings.
- [x] Add contextual back bars for nested routes, including detail and management pages, and provide localized action descriptions where needed.
- [x] Migrate dashboard, chart legends, portfolio values, asset list, transaction list, and asset detail to shared color/type/spacing/action hierarchy.
- [x] Replace duplicate chart/allocation palettes with named semantic chart colors; retain chart calculations and series order.
- [x] Keep values and provider symbols visually LTR through the shared helper; do not change number or currency formatting.
- [x] Inspect PortfolioApp.kt with Android Studio editor analysis and compare each route against the design note.

## Task 4: Forms, validation, settings, and management screens

**Files:**
- Modify: PortfolioApp.kt
- Consume: FolioComponents, FolioTokens

- [x] Migrate asset, transaction, manual-price, tag, and location forms to FolioField, FolioSelector, and shared action/state components.
- [x] Use a consistent primary Save/Add action and shared inline error presentation without changing validation conditions, strings, or save callbacks.
- [x] Keep form contents scrollable, preserve rememberSaveable drafts, and account for system and IME insets so controls remain reachable.
- [x] Migrate settings and empty/loading states to shared cards, rows, and state messages.
- [x] Inspect changed routes with Android Studio editor analysis; verify no persistence or calculation code changed.

## Task 5: Instrument picker and widget configuration

**Files:**
- Modify: MarketInstrumentPicker.kt, WidgetConfigurationActivity.kt, WidgetConfigurationScreen.kt
- Modify localized string resources only for missing semantics/accessibility copy
- Consume: FolioTheme, FolioComponents

- [x] Replace picker text fields, provider selector, results, dividers, and loading/error/empty messages with shared components.
- [x] Preserve provider selection, debounce, identifier validation, stale-result protection, and selected market behavior.
- [x] Use FolioTheme in WidgetConfigurationActivity and migrate configuration forms, currency chips, Save/Cancel, loading, and error states.
- [x] Preserve SavedStateHandle drafts, result contract, and Save/Cancel behavior.
- [x] Inspect all changed files with Android Studio editor analysis.

## Task 6: Glance widgets and widget token adapter

**Files:**
- Create: FolioWidgetTheme.kt
- Modify: WidgetLayout.kt, AssetPriceWidget.kt, AssetPriceWidgetPresentation.kt
- Consume: FolioTokens and FinancialTextDirection

- [x] Map the same semantic light/dark colors to GlanceTheme and replace the hardcoded allocation palette with the shared allocation tokens.
- [x] Replace repeated dimensions with shared widget token values where they represent the same roles; retain compact/expanded thresholds and current widget sizing.
- [x] Preserve 48dp widget actions, data/status copy, masked-total semantics, action intents, and offline/stale behavior.
- [x] Use shared financial bidi isolation while preserving current IRT/USD/USDT formatting and provider labels.
- [x] Inspect changed files with Android Studio editor analysis.

## Task 7: Design-system reference and final verification

**Files:**
- Create: docs/design-system.md
- Verification: existing project build and unit-test tasks; W08 is the sole emulator QA task.

- [x] Write the short reference with token table and Compose examples for FolioTheme, FolioCard, FolioField, and FolioFinancialValue, plus Glance color mapping guidance.
- [x] Run ./gradlew :app:assembleDebug :app:testDebugUnitTest from the project root; record dependency/build failures separately from code failures.
- [ ] Run W08 visual QA only after Tasks 1–6 are complete: inspect dashboard, asset detail, form, instrument picker, widget configuration, and both widget types in Persian/English and light/dark modes.
- [ ] In W08, inspect one compact layout, large font scale, IME visibility, navigation/back behavior, contrast, and accessibility descriptions; capture screenshots/evidence as required by the existing widget QA checklist.
- [x] Update docs/superpowers/tasks/2026-11-11-home-screen-widgets.md with W08 status/evidence without creating another emulator QA task.
- [x] Review git diff for accidental finance/provider changes and list any remaining inconsistencies in the final report.

**Completion:** all existing screens/flows use the shared visual foundations; builds/tests and requested visual checks are reported with exact outcomes; docs/design-system.md contains usage examples; any remaining inconsistency is identified.
