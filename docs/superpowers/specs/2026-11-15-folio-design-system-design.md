# Folio Design System — Design

Date: 2026-11-15
Status: Proposed for review

## Intent

Unify Folio's existing Compose screens, instrument picker, widget configuration, and Glance widgets around a small, reusable design system. Keep the navy, blue, and aqua identity, improve consistency and accessibility, and follow the system light/dark setting. Preserve portfolio calculations, market behavior, current number/currency formatting, and Persian/English support.

## Goals

- Establish semantic color, typography, spacing, shape, elevation, icon, and motion tokens.
- Apply shared Material 3 foundations to dashboard, asset list/detail, transactions, forms, settings, instrument search, and widget configuration.
- Map the same semantic palette to Glance widgets within widget layout and sizing constraints.
- Standardize hierarchy, touch targets, feedback, validation presentation, destructive confirmation, refresh behavior, RTL, financial text direction, insets, and keyboard visibility.
- Keep abstractions small and avoid unrelated architecture changes.

## Non-goals

- Changing accounting, valuation, provider, data persistence, or financial-formatting rules.
- Replacing navigation libraries, introducing a new screen architecture, or adding dependencies.
- Redesigning widget behavior or adding features outside the design system.
- Rewriting charts or replacing Glance with Compose.

## Existing state and constraints

PortfolioApp.kt owns navigation and nearly all app screens, along with local colors and private card, field, selector, and empty-state helpers. The market instrument picker is already shared between Add Asset and widget setup. Widget configuration declares a duplicate dark Material theme. Glance screens use GlanceTheme and a separate hardcoded allocation palette. Android XML themes retain purple/teal defaults, while Compose forces dark mode. Financial LTR isolation logic is repeated in app/widget presentation code.

The project already uses Kotlin, Jetpack Compose Material 3, Glance, localized Persian and English resources, and existing navigation. Reuse those dependencies and preserve all finance/data workflows.

## Approaches

| Approach | Tradeoff |
| --- | --- |
| System-following light/dark semantic system, using the existing navy palette in dark mode and a coordinated light palette | Selected: serves system appearance, resolves the current Compose/XML/Glance mismatch, and preserves Folio's dark identity. Adds a light semantic palette and theme adapters. |
| Dark-only shared semantic system | Smaller visual change, but leaves the existing DayNight resource behavior inconsistent with Compose and does not meet the requested light/dark consistency. |

The selected approach follows the system setting with no new in-app theme preference. Colors remain brand-authored rather than device-dynamic to preserve Folio's identity.

## Proposed tokens

| Semantic role | Dark | Light |
| --- | --- | --- |
| Background | #0B1020 | #F5F7FB |
| Surface / raised surface | #131B2B / #172137 | #FFFFFF / #EEF2F9 |
| Main / muted text | #F4F6FC / #B9C4D6 | #172033 / #4B5B73 |
| Border | #2C3950 | #D4DCE8 |
| Primary / accent | #668FFC / #76D8B0 | #315FC8 / #087F5D |
| Gain / loss / warning | aqua / #F47783 / #FFC857 | green / #B42336 / #885D00 |
| Disabled | #788297 | #929BAD |

Other named tokens use the Material 3 typography roles; spacing 4, 8, 12, 16, 24, 32dp; corner radii 8, 12, 16dp plus pill; surface elevations 0, 1, 3dp; icon sizes 16, 20, 24dp; interactive targets at least 48dp; and restrained short/medium motion durations. Extra semantic gain/loss/warning colors and an eight-color allocation palette supplement ColorScheme.

These are proposed values. Contrast will be checked in both palettes, especially for text on cards, status colors, and widget compact layouts.

## Components and conventions

- FolioTheme: Material 3 color scheme, typography, and semantic status colors; selected from system light/dark mode.
- FolioPage / FolioTopBar: consistent title hierarchy, actions, back behavior, content width, scrolling, and insets.
- FolioButton, FolioIconAction: primary, secondary, destructive, and icon-only hierarchy with accessible targets.
- FolioCard, FolioListRow, FolioFinancialValue: shared container, row, and financial-value presentation. Financial values retain current notation and use one bidi-isolation helper.
- FolioField, FolioSelector: shared labeled inputs, supporting/error feedback, short option selectors, and dropdown selection.
- FolioDialog, FolioStateMessage: consistent confirmation and loading/error/empty feedback.
- Existing MarketInstrumentPicker: retain its state/search behavior and reuse shared fields, results, and status treatments.
- FolioGlanceColors / widget token mapping: adapt semantic colors and dimensions without trying to share Compose-only components.

Interaction conventions: one primary action per context; secondary actions use outlined/text/icon treatments; destructive work requires confirmation and visible progress; validation stays attached to the relevant form flow without changing existing business rules; refresh retains its cached-data behavior and shows a bounded updating/terminal state. Scrollable forms remain usable with the keyboard visible and preserve existing drafts.

Navigation proposal: retain the current NavHost, routes, and back stack. Keep bottom navigation on top-level destinations; nested detail/form/management routes use a contextual back bar and hide the bottom bar. Nested screens do not gain new destinations or navigation dependencies.

## Representative applications

- Dashboard: one clear primary add-asset action, secondary transaction/refresh actions, shared cards and financial-value type, consistent chart legend/status colors.
- Asset detail: contextual back bar; responsive metric cards; edit and transaction actions; delete separated and confirmed with the shared destructive dialog.
- Asset and transaction forms: shared field/selector styles, consistent save action and error presentation, scroll plus IME/system inset handling, unchanged validation and save logic.
- Instrument search and widget configuration: same provider/search field, selected result, loading/empty/error states, and consistent Material theme. Existing picker selection and provider validation remain unchanged.
- Glance widgets: shared semantic color mapping for light/dark, readable compact typography, existing 48dp action controls, and explicit Persian RTL/financial LTR isolation.

## Significant tradeoffs

- System-following light mode creates a new light presentation rather than keeping Folio dark-only. Dark colors retain the current brand; light colors are adjusted for readable text and semantic status contrast.
- Hiding bottom navigation in nested flows gives detail and form tasks more room and a clearer return path, but changes its current persistent visibility.
- Shared components centralize styling and interaction details. They must remain wrappers over existing screen/domain behavior so visual migration does not alter calculations or provider workflows.

## Review checklist

- [ ] Approve system-following light/dark palettes and brand-authored colors.
- [ ] Approve contextual navigation for nested routes (back bar, no bottom navigation).
- [ ] Approve token scales and shared component inventory.
- [ ] Confirm no financial calculations, provider capabilities, or number-formatting behavior changes.

After approval, prepare a staged implementation plan before editing production code. Verification will include relevant builds, existing tests, and representative English/Persian light/dark visual checks. Existing widget repository notes reserve emulator testing for the single final W08 QA pass; visual emulator checks will be included there.
