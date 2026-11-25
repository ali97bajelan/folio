# Configurable Asset Price Widget

Date 2026-11-25 · Proposed

[Implementation plan](../plans/2026-11-25-asset-price-widget-selection.md) · [Task checklist](../tasks/2026-11-25-asset-price-widget-selection.md)

## User flow

The user adds a Folio Asset Price widget or taps its Configure action on the home screen. The configuration menu shows which assets this widget will display. The user can add or remove individual assets and save the list. Launcher reconfiguration opens the same menu where supported.

- A **Selected assets** section shows saved selections with their provider and an accessible Remove action. **Add asset** opens the existing market picker with asset type, provider, search, and manual provider identifier entry. Users can choose any instrument supported by that picker, including instruments outside their portfolio.
- An explicit **Add to widget** action adds the chosen instrument to the draft. The same provider and canonical market identifier can appear once per widget. The same ticker from two distinct providers can appear twice.
- The widget lists assets in the order they were added. Removing and adding an asset again puts it at the end. Saving requires at least one selected asset. Cancel or Back discards unsaved changes, and the draft survives activity recreation.
- Each placed widget has its own selection. The existing portfolio widget and Add Asset flow keep their current behavior.

## Widget display

- Size the viewport for up to three complete price rows. All saved selections are available in the vertical list. For example, a widget that fits three rows shows the first three of five selections initially, and the user can swipe to see the other two.
- Smaller widget sizes can show one or two rows if that is what fits. Larger sizes keep the three-row cap. When the list is scrolled between rows, a row can be partially visible at the edge.
- Refresh and Configure stay reachable in a fixed header. The list handles vertical swipes independently of the header.
- Each row identifies the asset and its provider and shows the latest successful price in its native quote currency. Preserve the existing toman formatting. Use the existing per-quote stale, refresh-failure, and unsupported rules. When an asset has no usable quote, show its unavailable state in that row, and continue showing the other assets.
- Use Folio’s English and Persian strings, RTL layout, and financial text formatting. Provide a setup prompt and Configure action if a widget has no saved configuration.

## Saving and refreshing

- An app update carries each existing price widget’s single selection into the first row of its list and preserves its cached quote. The user can continue using it without reconfiguration.
- Save publishes the new list with available cached prices and requests background work for current selections. Previously saved selections can be retained or removed offline. Adding an instrument follows the shared picker’s existing validation rules.
- Remove a saved asset from this widget only. Widgets that select the same market share cached quotes. Refresh includes all selected markets, deduplicates provider requests across widgets and portfolio holdings, and preserves other widgets’ access to shared prices.
- Key every rendered quote to its own market. Late responses from an earlier configuration must never display a removed asset or put one asset’s price on another row.

The per-widget menu, add order, one-asset minimum, and three-row viewport are design assumptions for implementation. The verification scope is Gradle build, lint, JVM tests, and host-side database migration checks. Launcher gesture and rendering behavior remains unverified in this scope.
