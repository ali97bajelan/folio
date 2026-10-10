# Refactor and bug review — 2026-10-10

## Scope and repository inspection

The repository contains one Android `:app` module. The UI uses Jetpack Compose and Navigation Compose, with lifecycle-aware Flow collection. `MainViewModel` coordinates the repository, domain calculations, and WorkManager. Room owns local persistence; OkHttp provides cancellable provider requests. Application-owned lazy database/repository instances are shared by the activity and workers. There is no DI framework. The build uses AGP 8.7.3, Kotlin 2.1.0, SDK 35, minimum SDK 28, Java/Kotlin bytecode target 11, and a JDK 24 Gradle daemon.

Read the README, Gradle configuration, manifest, backup and keep rules, Room schema, production sources, and test setup. No AGENTS.md or CLAUDE.md repository instructions were found in the repository or checked ancestor directories. The README describes migrations, but the implementation does not register any (finding 1).

Existing working-tree changes were retained: IDE device configuration, release baseline profiles/metadata, the untracked `.kotlin/` directory, and decimal-keyboard changes in `PortfolioApp.kt`. No dependencies, SDK/Gradle settings, resources, schema, queries, preferences, network contracts, or public APIs were changed. No code/resources were removed. No existing bug was fixed.

## Refactoring

- Expanded the dense asset form and renamed ambiguous local state (`old`, `type`, `mode`, `selected`) to describe its purpose. The form remains in its existing composable, with the same state ownership, declaration order, saveable inputs/saver, effect keys, collection calls, default values, callbacks, and UI call order. Added an initialization-intent comment.
- Expanded opening-holding parsing in the ViewModel and replaced `q` with `openingQuantity`. Validation order, messages, coroutine scope, defaults, and repository call remain unchanged.
- Extracted compact market-pair parsing into a focused function with explicit loop/control flow; preserved suffix priority, permissive delimiter behavior, normalization, and exception behavior. Named quote-result constructor arguments without changing their evaluation order.
- Extracted worker source-quote selection and counterpart persistence/valuation into private helpers. These still run sequentially inside the original Room transaction and IO context, with the same DAO order, null exits, arithmetic, timestamps, and cancellation handling.
- Added four parser characterization tests covering market normalization, missing pair components, invalid/nonpositive prices, and first-active duplicate catalogue entries. They passed against the original parser before refactoring.

## Validation

| Check | Before | After |
| --- | --- | --- |
| `:app:assembleDebug --offline` | Passed | Passed |
| `:app:testDebugUnitTest --offline` | 10 tests passed | 14 tests passed |
| `:app:lintDebug --offline` | Passed; 13 warnings, 2 informational findings | Passed; identical findings |
| Android test APK assembly | Passed during baseline instrumentation attempt | `:app:assembleDebugAndroidTest --offline` passed |
| Existing instrumented test on emulator-5554 | 1 test passed via ADB | 1 test passed via ADB |
| Final diff whitespace check | — | `git diff --check` passed |

Gradle's `:app:connectedDebugAndroidTest` could not run: offline mode lacked `com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`, and an online attempt could not resolve it from the configured repositories. Built the APKs, installed them with `adb install -r`, and invoked `am instrument -w -r com.example.folio.test/androidx.test.runner.AndroidJUnitRunner` directly instead. Both runs reported `OK (1 test)`.

The first sandboxed Gradle invocation could not write its external wrapper-cache lock; the authorized invocation using the existing cache succeeded. Existing Gradle deprecation warnings remain. Lint issue IDs, severities, and messages were compared before/after and are unchanged.

Reviewed the final diff for lifecycle, threading, state restoration, and side effects. A token-order comparison of the asset form, allowing local renames, formatting, separators, and block braces, matched the initial working copy; the rest of `PortfolioApp.kt` is identical to that copy. A separate read-only reviewer found no introduced behavior changes.

Limitations: the instrumented suite only checks the application package/context. It does not establish UI appearance, accessibility, navigation, process restoration, worker integration, or provider failure equivalence. Those were inspected in source, not exercised through end-to-end scenarios. Release/R8 builds and live provider requests were not run. Passing checks do not prove complete behavioral equivalence.

## Existing bugs and suspected issues (ordered by severity)

“Confirmed” below means the conditional defect follows directly from the code. These scenarios were not reproduced end to end; production exposure depends on the stated trigger. Proposed fixes are intentionally unimplemented.

### 1. Missing Room upgrade paths

**High severity; high confidence; confirmed from code.**

**Location:** `app/src/main/java/com/example/folio/FolioApplication.kt:11`; `data/local/PortfolioDatabase.kt:109`.

**Trigger/impact:** Opening an existing version 1 or 2 database after installing this version requires an upgrade to version 3. Room cannot find a migration, so database access fails and portfolio data becomes inaccessible in the app. This does not itself erase the database.

**Evidence:** The database annotation declares version 3; the builder only calls `.build()`. There are no migration registrations or automatic migrations. Generated `PortfolioDatabase_Impl.createAutoMigrations` returns an empty list. Only schema 3 is exported.

**Suggested fix:** Recover the historical schemas and register data-preserving 1→2→3 (or equivalent direct) migrations. Avoid destructive fallback for portfolio records.

**Verify:** Upgrade actual version 1 and 2 database fixtures through the application builder; assert assets, transactions, tags, quotes, and historical snapshots survive and schema validation succeeds.

### 2. Worker exchange-rate knowledge is lost during local recalculation

**Medium severity; high confidence; confirmed from code.**

**Location:** `app/src/main/java/com/example/folio/data/network/PriceRefreshWorker.kt:148`; `data/PortfolioRepository.kt:241`; `domain/PortfolioServices.kt:72`.

**Trigger/impact:** A portfolio has no dedicated USDT/IRT alias asset. The worker fetches FX and saves a newer calculated IRT counterpart for a USD/USDT source quote. A subsequent transaction edit calls repository snapshot calculation. USD valuations can become unavailable and the USD headline/history total can become zero despite holdings and quotes still existing.

**Evidence:** The worker's fetched FX rate is a local variable, not a persisted standalone rate. Repository FX lookup searches only alias assets. `assetValue` selects the newest quote across currencies; converting a newer IRT counterpart back to USD returns null without FX. Snapshot summation omits null values and starts at zero.

**Suggested fix:** Persist canonical FX independently and share that lookup between worker and repository; prefer a target-currency quote when appropriate. Define how unavailable conversion should appear in totals.

**Verify:** With only a BTC/USDT asset, refresh at a controlled FX rate, then edit its quantity. Both currency totals should change proportionally and remain consistent with the stored quote values. Repeat with IRT source quotes, unavailable FX, and process recreation.

### 3. A reported save failure can leave the ledger mutation committed

**Medium severity; high confidence; confirmed from code.**

**Location:** `app/src/main/java/com/example/folio/presentation/MainViewModel.kt:234`; `data/PortfolioRepository.kt:104`.

**Trigger/impact:** A transaction insert succeeds, then snapshot creation fails (for example, storage exhaustion). The form receives a save error even though the transaction is committed. Retrying a new transaction with ID zero can insert it again and inflate holdings. Deletes and manual-price saves also have separate mutation/snapshot boundaries.

**Evidence:** `repo.saveTransaction(item)` commits its own transaction before `repo.saveSnapshots()` begins. The shared `save` wrapper only calls `done` after both complete, catches the later exception, and re-enables saving. `saveAssetWithOpening` already demonstrates an outer transaction around related writes.

**Suggested fix:** Make mutation and snapshot creation atomic at the repository boundary, or explicitly distinguish successful mutation from failed derived-data refresh and make retries idempotent.

**Verify:** Inject a snapshot-write failure after a new ledger insert; assert either complete rollback or an explicit successful-save state. Retry and assert exactly one transaction and the correct holding.

### 4. Cost basis adds amounts in incompatible currencies

**Medium severity; high confidence; confirmed from code.**

**Location:** `app/src/main/java/com/example/folio/domain/PortfolioServices.kt:47`; `presentation/PortfolioApp.kt:944`.

**Trigger/impact:** Purchases for one asset use different transaction currencies. The displayed average cost becomes numerically misleading because the raw amounts are combined without conversion or a currency label.

**Evidence:** `TransactionEntity` stores `transactionCurrency` and the form lets users select IRT, USD, or USDT. `CostBasisService.current` never reads it. One unit bought for 10 USD and another for 800,000 IRT yields an average of 400,005, although both purchases equal 10 USD at 80,000 IRT/USD.

**Suggested fix:** Define an explicit cost-basis currency and historical FX policy, convert price/fee amounts before averaging, and display the currency. Alternatively show separate currency-specific bases when conversion is unavailable.

**Verify:** Test mixed-currency purchases with controlled historical FX, fees, partial sales, and closing/reopening a holding; assert values in the chosen currency and missing-rate behavior.

### 5. Refresh failures can finish without user-visible feedback

**Medium severity; high confidence; confirmed from code.**

**Location:** `app/src/main/java/com/example/folio/data/network/PriceRefreshWorker.kt:44`; `presentation/MainViewModel.kt:256`.

**Trigger/impact:** All provider calls fail, or WorkManager marks work FAILED/CANCELLED. The refresh indicator stops without an error/retry prompt; cached prices remain, and the user may not know the refresh failed. If FX cannot be fetched or found, snapshot publication is silently skipped as well.

**Evidence:** Per-provider failures are persisted but the worker returns `Result.success()` after handling them. `refreshPrices` accepts any finished WorkInfo state without checking success. No composable consumes `vm.refreshes` or renders provider failures. The error handler only handles exceptions thrown by enqueueing/collection.

**Suggested fix:** Preserve best-effort cached prices while exposing complete/partial refresh outcomes and FX failures to the UI. Inspect WorkInfo failure/cancellation states explicitly; keep cancellation distinct from provider failure.

**Verify:** Use controlled provider failures and failed/cancelled work; assert feedback, retry behavior, retained prices, and correct refresh-indicator reset. Include partial-success cases.

### 6. Language switching may fail for language-split bundle installs

**Medium severity; medium confidence; suspected deployment issue.**

**Location:** `app/build.gradle.kts:8`; `app/src/main/java/com/example/folio/presentation/AppLanguage.kt:28`.

**Trigger/impact:** If distributed as an Android App Bundle with only the device's initial language split installed, choosing the other in-app language may fall back to existing resources because that language was not delivered. APK distribution is unaffected; bundle distribution was not established here.

**Evidence:** Existing lint reports `AppBundleLocaleChanges`. The app exposes runtime language switching, but Gradle does not disable language splitting and there is no language-download integration.

**Suggested fix:** For bundle distribution, include both supported languages by disabling language splitting or implement on-demand language delivery. This would be a separate build/product behavior change.

**Verify:** Install device-specific split APKs generated from a release bundle for each initial locale, switch to the other language offline, and check all UI and ViewModel error strings.
