package com.example.folio.presentation

import com.example.folio.PortfolioCodes
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.folio.data.*
import com.example.folio.data.local.*
import com.example.folio.data.network.Instrument
import com.example.folio.data.network.InstrumentSearch
import com.example.folio.data.network.PriceRefreshWorker
import androidx.work.WorkManager
import androidx.work.await
import com.example.folio.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    context: Context,
    private val repo: PortfolioRepository,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val languagePreferences = LanguagePreferences(appContext)
    private val locationPreferences = appContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private val locationSeedMutex = Mutex()
    private fun localizedString(resource: Int): String {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languagePreferences.current().tag))
        return appContext.createConfigurationContext(configuration).getString(resource)
    }
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var retryAction: (() -> Unit)? = null
    private var defaultLocations: List<String> = emptyList()
    private var locationsNeedRetry = false

    fun clearError() { _error.value = null; retryAction = null }
    private fun showError(message: String) { retryAction = null; _error.value = message }
    fun retry() {
        val action = retryAction
        clearError()
        if (action != null) action() else refreshDashboard()
        if (locationsNeedRetry) seedDefaultLocations(defaultLocations)
    }

    private fun errorMessage(e: Exception): String {
        return when (e) {
            is SQLiteConstraintException -> localizedString(R.string.error_duplicate_or_invalid_reference)
            is SQLiteException -> localizedString(R.string.error_storage)
            is IllegalArgumentException -> {
                val resource = when (e.message) {
                    "Name is required." -> R.string.error_name_required
                    "Symbol is required." -> R.string.error_symbol_required
                    "Choose a market-price provider.", "Choose a provider market." -> R.string.error_market_selection_required
                    "Quantity must be greater than zero." -> R.string.error_quantity_positive
                    "Price cannot be negative.", "Price must be greater than zero." -> R.string.error_price_invalid
                    "Fee cannot be negative." -> R.string.error_fee_invalid
                    "Asset no longer exists.", "Transaction no longer exists." -> R.string.error_record_missing
                    "An existing transaction cannot be moved to another asset." -> R.string.error_transaction_asset_locked
                    "This change would create a negative holding at some point in the timeline.",
                    "Deleting this transaction would create a negative holding in the timeline." -> R.string.error_negative_holding
                    else -> null
                }
                resource?.let(::localizedString) ?: e.message ?: localizedString(R.string.error_operation)
            }
            else -> localizedString(R.string.error_operation)
        }
    }

    private fun <T> Flow<T>.recoverErrors(): Flow<T> = retryWhen { cause, attempt ->
        if (cause is CancellationException) throw cause
        if (cause !is Exception) throw cause
        if (attempt == 0L) showError(localizedString(R.string.error_loading))
        delay(1.seconds)
        true
    }

    fun seedDefaultLocations(names: List<String>) {
        defaultLocations = names
        viewModelScope.launch {
            try {
                locationSeedMutex.withLock {
                    if (!locationPreferences.getBoolean("default_locations_seeded", false)) {
                        repo.seedDefaultLocations(names)
                        locationPreferences.edit { putBoolean("default_locations_seeded", true) }
                    }
                }
                locationsNeedRetry = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                locationsNeedRetry = true
                showError(errorMessage(e))
            }
        }
    }
    val assets = repo.assets.recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repo.tags.recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = repo.locations.recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repo.transactions.recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val refresh = MutableStateFlow(0)
    private val _instrumentResults = MutableStateFlow<List<Instrument>>(emptyList())
    val instrumentResults: StateFlow<List<Instrument>> = _instrumentResults
    private val _instrumentSearchError = MutableStateFlow<String?>(null)
    val instrumentSearchError: StateFlow<String?> = _instrumentSearchError
    private var instrumentSearchJob: Job? = null
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Refresh when local edits occur and when the worker writes refreshed prices/statuses.
    val dashboard = merge(
        refresh.map {},
        repo.assets.map {},
        repo.transactions.map {},
        // Price quotes are persisted independently of valuation snapshots.  Observe
        // them directly so the last cached quote is used as soon as the app opens.
        repo.prices.map {},
        repo.refreshes.map {},
        repo.valuations.map {},
    ).map { repo.dashboard() }
        .recoverErrors().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardData(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, 0),
        )
    val portfolioHistory = merge(
        refresh.map {},
        repo.valuations.map {},
        repo.portfolioSnapshots.map {},
    ).map { repo.portfolioHistory() }
        .recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val assetTypeHistory = merge(
        refresh.map {},
        repo.assets.map {},
        repo.valuations.map {},
    ).map { repo.assetTypeHistory() }
        .recoverErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun asset(id: Long) = repo.asset(id).recoverErrors()
    fun assetTagIds(id: Long) = repo.assetTagIds(id).recoverErrors()
    fun detail(id: Long) = repo.detail(id).recoverErrors()
    private fun <T> save(
        onError: (String) -> Unit,
        done: (T) -> Unit,
        action: suspend () -> T,
    ): Job? {
        // Claim the save before launching so another tap cannot enqueue a duplicate.
        if (_isSaving.value) return null
        _isSaving.value = true
        return viewModelScope.launch {
            try {
                val result = action()
                refresh.value++
                done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(errorMessage(e))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveAsset(
        asset: AssetEntity,
        tags: List<Long>,
        onError: (String) -> Unit = ::showError,
        done: () -> Unit,
    ) = save(onError, { _: Long -> done() }) {
        repo.saveAsset(asset, tags).also { repo.saveSnapshots() }
    }
    fun saveAssetWithOpening(
        asset: AssetEntity,
        tags: List<Long>,
        quantity: String,
        averageCost: String,
        locationId: Long?,
        onError: (String) -> Unit = ::showError,
        done: (Long) -> Unit = {},
    ) = save(onError, done) {
        val openingQuantity = quantity.trim().takeIf { it.isNotEmpty() }?.let {
            requireNotNull(it.toBigDecimalOrNull()) { localizedString(R.string.error_quantity_positive) }
        }
        val average = averageCost.trim().takeIf { it.isNotEmpty() }?.let {
            requireNotNull(it.toBigDecimalOrNull()) { localizedString(R.string.error_price_invalid) }
        }
        require(openingQuantity != null || (average == null && locationId == null)) {
            localizedString(R.string.error_opening_quantity_required)
        }
        if (openingQuantity != null) require(openingQuantity > BigDecimal.ZERO) {
            localizedString(R.string.error_quantity_positive)
        }
        require(average == null || average >= BigDecimal.ZERO) { localizedString(R.string.error_price_invalid) }
        val opening = openingQuantity?.let {
            TransactionEntity(
                assetId = 0,
                transactionType = PortfolioCodes.BUY,
                quantity = it,
                pricePerUnit = average,
                transactionCurrency = asset.manualPriceCurrency.ifBlank { PortfolioCodes.IRT },
                locationId = locationId,
                executedAt = Instant.now(),
                notes = "Initial position",
            )
        }
        repo.saveAssetWithOpening(asset, tags, opening)
    }
    fun deleteAsset(
        asset: AssetEntity,
        onError: (String) -> Unit = ::showError,
        done: () -> Unit = {},
    ) = save(onError, { _: Unit -> done() }) { repo.deleteAsset(asset); repo.saveSnapshots() }

    fun saveTransaction(item: TransactionEntity, onError: (String) -> Unit = ::showError, done: () -> Unit = {}) =
        save(onError, { _: Unit -> done() }) { repo.saveTransaction(item); repo.saveSnapshots() }
    fun savePrice(assetId: Long, price: String, currency: String, onError: (String) -> Unit = ::showError, done: () -> Unit = {}) =
        save(onError, { _: Unit -> done() }) {
            repo.savePrice(AssetPriceEntity(assetId = assetId, price = BigDecimal(price), currency = currency, provider = PortfolioCodes.MANUAL, capturedAt = Instant.now()))
            repo.saveSnapshots()
        }
    fun saveTag(name: String, onError: (String) -> Unit = ::showError, done: () -> Unit = {}) =
        save(onError, { _: Long -> done() }) { repo.saveTag(name) }
    fun saveLocation(name: String, notes: String, onError: (String) -> Unit = ::showError, done: () -> Unit = {}) =
        save(onError, { _: Long -> done() }) { repo.saveLocation(name, notes) }
    fun refreshDashboard() { refresh.value++ }
    fun refreshPrices() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // KEEP may retain a prior request. Wait for enqueueing before
                // observing the actual work, rather than the discarded request ID.
                PriceRefreshWorker.refreshNow(appContext).await()
                workManager.getWorkInfosForUniqueWorkFlow(PriceRefreshWorker.IMMEDIATE_WORK_NAME)
                    .first { work -> work.isNotEmpty() && work.all { it.state.isFinished } }
                refreshDashboard()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showError(localizedString(R.string.error_refresh))
                retryAction = ::refreshPrices
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    fun searchInstruments(provider: String, name: String, assetType: String) {
        instrumentSearchJob?.cancel()
        _instrumentResults.value = emptyList()
        _instrumentSearchError.value = null
        instrumentSearchJob = viewModelScope.launch {
            delay(300.milliseconds)
            try {
                _instrumentResults.value = InstrumentSearch.search(provider, name, assetType)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _instrumentResults.value = emptyList()
                _instrumentSearchError.value = localizedString(R.string.error_search)
            }
        }
    }
    fun clearInstrumentSearch() { instrumentSearchJob?.cancel(); _instrumentResults.value = emptyList(); _instrumentSearchError.value = null }
}
