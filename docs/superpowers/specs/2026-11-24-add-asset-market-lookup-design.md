# Add Asset market lookup design

Date: 2026-11-24

Status: Proposed

## User flow

In **Add asset** with **Market** pricing, show a single **Symbol or market** field alongside **Accounting unit**. Keep **Name** as the portfolio display name. The pricing section still exposes the market provider and the **Provider symbol or ID** field for direct entry.

- Typing in **Name** automatically searches the selected provider while the form has no linked market or manually entered provider ID. Mirror the name in **Symbol or market** as the default query. Typing in **Symbol or market** uses that text directly and leaves **Name** alone. Editing **Name** again resumes the default name search when neither a link nor a provider ID is present. Clearing the merged field cancels lookup and clears its suggestions.
- Trim only for the request. Keep spaces, case, and Persian text in the editable query. Reuse the existing two-character minimum and 300 ms debounce. Search the current provider only. The existing defaults are Nobitex for crypto and other types without a specific default, TSETMC for Iranian stocks, Aban Tether for US stocks, and Rahavard for gold and silver.
- Choosing a result closes the dropdown, puts its asset ticker in the merged field, and records the provider and its canonical market ID. Leave the user’s display name alone. Editing the display name after linking a market keeps that link. Editing the merged field clears the old link and starts a fresh lookup.
- Changing provider or asset type clears the prior provider ID and link and reruns the current query, using the name when there is no explicit query. A type change resets the provider to the new type’s default. Switching to **Manual** closes the dropdown and cancels market work. A switch back to **Market** starts a fresh lookup using the current name. **Quick fill** still creates the same manual example.
- Direct provider ID entry remains available after an empty result or a provider error. While a provider ID is being edited or validated, the dropdown closes. Market saving requires either a chosen result or a successfully validated provider ID. For a validated direct ID, trim and uppercase an explicit symbol if supplied. Otherwise, derive the asset symbol from the current name by trimming, uppercasing, and replacing whitespace runs with hyphens. Persist the portfolio ticker and the canonical provider ID in their existing separate fields.
- Retain the form draft across recreation. Restore a market chosen from search without an extra lookup. Revalidate a directly typed ID after process recreation before allowing market save.

## Dropdown and form height

Use a field-anchored Material 3 popup. The popup has its own scroll area and initially shows up to three result rows, with fewer when space above or below the field is limited by the keyboard or viewport. Keep the full ordered list returned by the provider search and make every returned result reachable by scrolling inside the popup. The current provider adapters return up to 12 items.

Rows show the ticker, name, and market detail so users can distinguish listings. Use consistently sized rows that account for scaled text and use ellipsis for overflow. Keep typing focus in the active field. Dismiss the popup on outside tap, Back, or result selection. A new eligible edit opens it again. Start a new query at the top of the list.

Place loading, no-match, and search-error messages in the same popup. Blank and one-character queries have no popup. Opening or closing the popup, completing a search, and adding more matches leave the measured form content height and downstream field positions the same.

A bounded inline list would need to reserve space in the form or move the fields beneath it as it opens. An anchored popup meets the height requirement and keeps results next to the merged field. [Android Developers editable menu example](https://developer.android.com/reference/kotlin/androidx/compose/material3/ExposedDropdownMenuBox.composable)

## Scope

- Keep manual pricing, Edit Asset, and widget configuration behavior as they are.
- Use the existing provider search, quote validation, and save paths. Keep the current provider matching capabilities.
- Keep the existing dependencies and persistence schema.
- Localize new copy in English and Persian and preserve RTL layout and financial text direction.
- Verification is host-side only. Emulator QA is out of scope.
