# Unified App and Widget Design Tokens Implementation Plan

> **For agentic workers:** Use the native execution workflow. Steps use checkbox syntax for tracking.

**Goal:** Keep app screens, widget configuration, and both Glance widgets aligned through one Folio design-token source.

**Architecture:** Extend the existing FolioTokens.kt source with the semantic fills and widget-size extensions that Glance needs. Keep Compose and Glance components separate, map their styles from those shared tokens, and align Android XML theme colors with the current Folio palette.

**Tech Stack:** Kotlin, Compose Material 3, Glance, Android color resources.

**Spec:** docs/design-system.md (update its token and theme notes to reflect current code).

## Global Constraints

- Preserve existing accounting, provider, widget actions, layout thresholds, Persian/English behavior, and financial formatting.
- Keep the user-selected Dark, Light, or System appearance behavior already used by the app and widgets.
- Keep Compose and Glance components native to their runtimes; add no dependencies.
- Do not add or run tests unless requested; verify changed files with editor analysis and app:assembleDebug.

## Review Focus

1. Dark, Light, and System selections produce the corresponding app and widget palette.
2. Gain, loss, warning, and muted/border colors stay legible in both palettes.
3. Compact and expanded widget layouts retain their current size thresholds and action targets.
4. Persian and English copy and financial bidi handling remain unchanged.
5. Widget picker previews and Android system bars do not retain the obsolete blue/teal palette.

---

### Task 1: Complete the shared Folio tokens

**Files:** app/src/main/java/com/example/folio/presentation/design/FolioTokens.kt

- [x] Add gain/loss/warning container colors to FolioSemanticColors for light and dark palettes.
- [x] Define named Glance-compatible widget layout, shape, and text-scale tokens in the design package, reusing FolioSpacing, FolioShapeTokens, FolioTypography, and FolioMinimumTouchTarget wherever their roles match.
- [x] Preserve existing public token names and the existing spacing/icon-size lists.

### Task 2: Make Glance a pure adapter over Folio tokens

**Files:** app/src/main/java/com/example/folio/presentation/widget/FolioWidgetTheme.kt

- [x] Remove duplicate semantic container RGB literals and map every FolioWidgetSemanticColors value from FolioLightSemanticColors/FolioDarkSemanticColors.
- [x] Replace local FolioWidgetSpacing values with the shared design-package widget tokens.
- [x] Preserve the app-selected Dark/Light/System palette behavior.

### Task 3: Apply shared tokens across widget surfaces

**Files:** AssetPriceWidget.kt, WidgetLayout.kt, Android widget preview layouts/drawables, values/colors.xml, and values-night/colors.xml.

- [x] Replace Glance visual spacing, radii, icon/action sizes, and text sizes with named design tokens; keep data thresholds and sizing calculations local.
- [x] Align XML system theme resources and widget picker previews with the same Folio light/dark and semantic colors.
- [x] Preserve provider states, row behavior, tap actions, and current localization.

### Task 4: Refresh the design-system reference and verify

**Files:** docs/design-system.md

- [x] Document the current Folio palette, app theme preference behavior, Compose tokens, and Glance token adapter.
- [x] Run editor analysis on changed Kotlin/XML files and app:assembleDebug; do not run tests or emulator QA.
