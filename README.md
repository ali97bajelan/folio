# Folio — local-first Android portfolio

This is a native Kotlin/Jetpack Compose application. Its data lives only in the Room/SQLite database on the device; it has no Django dependency, API, WebView, browser UI, account, or cloud database.

## Build and install

Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on an Android 9+ device/emulator. From a terminal: `./gradlew :app:assembleDebug`.

## Django audit and migration map

| Django source | Native destination | Preserved behavior |
|---|---|---|
| `models.py` Tag, Location, Asset, Transaction | `data/local/PortfolioDatabase.kt` Room entities | unique names/symbol; enums as stable string values; timestamps UTC; FK deletion rules |
| AssetPrice, AssetValuation, ProviderRefresh | same Room schema | captured snapshots and provider outcome/error history |
| `services.py` HoldingService | `domain/PortfolioServices.kt` | BUY minus SELL, optional historical moment, per-location grouping |
| CostBasisService | `domain/PortfolioServices.kt` | chronological weighted average including BUY fee; SELL does not change average; zero clears it |
| PricingService | `domain/PortfolioServices.kt` | latest price and historical price at-or-earliest fallback |
| Currency/Portfolio valuation services | `domain/PortfolioServices.kt` | USD/USDT equivalence, USD/IRT proxy aliases, missing-price reporting, toman semantics |
| `pricing.py` parsers | `data/network/PriceProviders.kt` | Nobitex/TSETMC rial ÷ 10; Aban midpoint; malformed/unsupported response errors |
| `views.py`, templates and URLs | `presentation/PortfolioApp.kt` Navigation Compose | Dashboard, assets, detail, transactions, manual price, tag/location, settings destinations |
| `forms.py` and `check_timeline` | repository validation | market-provider requirements, required names/symbols, positive quantities and chronological no-negative holdings |
| Django migrations | Room schema v1 | complete released schema, no server migration necessary |

## Provider limitations

Network price refresh is deliberately optional; saved prices and all valuations work offline. Nobitex, Aban Tether and TSETMC parsing is implemented around their public response shapes and must tolerate provider changes/timeouts. Rahavard remains selectable but should use manual prices until a stable, permitted public quote endpoint can be maintained. No credentials are embedded.

## Backup/restore

The Room schema holds all exportable relationships and timestamps. Backup/restore is intentionally scoped to Android's system backup until the release JSON document-picker flow is completed; direct database-file copying is not presented as a user workflow. JSON import must run as one Room transaction and validate unique asset symbols and all foreign-key references before it replaces rows.
