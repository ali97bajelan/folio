package com.example.folio.presentation

import android.content.Context
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
import java.math.BigDecimal
import java.time.Instant

class MainViewModel(
    context: Context,
    private val repo: PortfolioRepository,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    val assets = repo.assets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = repo.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val refreshes = repo.refreshes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
        refresh.map { Unit },
        repo.assets.map { Unit },
        repo.transactions.map { Unit },
        // Price quotes are persisted independently of valuation snapshots.  Observe
        // them directly so the last cached quote is used as soon as the app opens.
        repo.prices.map { Unit },
        repo.refreshes.map { Unit },
        repo.valuations.map { Unit },
    ).map { repo.dashboard() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardData(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, 0),
        )
    val portfolioHistory = merge(
        refresh.map { Unit },
        repo.valuations.map { Unit },
        repo.portfolioSnapshots.map { Unit },
    ).map { repo.portfolioHistory() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val assetTypeHistory = merge(
        refresh.map { Unit },
        repo.assets.map { Unit },
        repo.valuations.map { Unit },
    ).map { repo.assetTypeHistory() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun asset(id: Long) = repo.asset(id)
    fun assetTagIds(id: Long) = repo.assetTagIds(id)
    fun detail(id: Long) = repo.detail(id)
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
                onError(e.message ?: "Unable to save")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveAsset(asset: AssetEntity, tags: List<Long> = emptyList(), done: (Long) -> Unit = {}) =
        save({}, done) { repo.saveAsset(asset, tags).also { repo.saveSnapshots() } }
    fun saveAsset(
        asset: AssetEntity,
        tags: List<Long>,
        onError: (String) -> Unit = {},
        done: () -> Unit,
    ) = save(onError, { _: Long -> done() }) {
        repo.saveAsset(asset, tags).also { repo.saveSnapshots() }
    }
    fun saveAsset(asset: AssetEntity, tags: List<Long>, done: () -> Unit) =
        saveAsset(asset, tags, {}, done)
    fun saveAssetWithOpening(asset: AssetEntity, tags: List<Long>, quantity: String, averageCost: String, locationId: Long?, onError: (String) -> Unit = {}, done: (Long) -> Unit = {}) = save(onError, done) {
        val q = quantity.trim().takeIf { it.isNotEmpty() }?.let {
            requireNotNull(it.toBigDecimalOrNull()) { appContext.getString(R.string.error_quantity_positive) }
        }
        val average = averageCost.trim().takeIf { it.isNotEmpty() }?.let {
            requireNotNull(it.toBigDecimalOrNull()) { appContext.getString(R.string.error_price_invalid) }
        }
        require(q != null || (average == null && locationId == null)) { appContext.getString(R.string.error_opening_quantity_required) }
        if (q != null) require(q > BigDecimal.ZERO) { appContext.getString(R.string.error_quantity_positive) }
        require(average == null || average >= BigDecimal.ZERO) { appContext.getString(R.string.error_price_invalid) }
        val id = repo.saveAsset(asset, tags)
        if (q != null) repo.saveTransaction(TransactionEntity(assetId = id, transactionType = "BUY", quantity = q, pricePerUnit = average, transactionCurrency = asset.manualPriceCurrency.ifBlank { "IRT" }, locationId = locationId, executedAt = Instant.now(), notes = "Initial position"))
        repo.saveSnapshots()
        id
    }
    fun deleteAsset(
        asset: AssetEntity,
        onError: (String) -> Unit = {},
        done: () -> Unit = {},
    ) = viewModelScope.launch {
        runCatching { repo.deleteAsset(asset); repo.saveSnapshots() }
            .onSuccess { refresh.value++; done() }
            .onFailure { onError(it.message ?: "Unable to delete asset") }
    }
    fun saveTransaction(item: TransactionEntity, onError: (String) -> Unit = {}, done: () -> Unit = {}) =
        save(onError, { _: Unit -> done() }) { repo.saveTransaction(item); repo.saveSnapshots() }
    fun deleteTransaction(item: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(item); repo.saveSnapshots(); refresh.value++ }
    fun savePrice(assetId: Long, price: String, currency: String, onError: (String) -> Unit = {}, done: () -> Unit = {}) =
        save(onError, { _: Unit -> done() }) {
            repo.savePrice(AssetPriceEntity(assetId = assetId, price = BigDecimal(price), currency = currency, provider = "MANUAL", capturedAt = Instant.now()))
            repo.saveSnapshots()
        }
    fun saveTag(name: String) = viewModelScope.launch { runCatching { repo.saveTag(name) } }
    fun saveLocation(name: String, notes: String) = viewModelScope.launch { runCatching { repo.saveLocation(name, notes) } }
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
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    fun searchInstruments(provider: String, name: String, assetType: String) { instrumentSearchJob?.cancel(); instrumentSearchJob = viewModelScope.launch {
        delay(300)
        _instrumentSearchError.value = null
        runCatching { InstrumentSearch.search(provider, name, assetType) }
            .onSuccess { _instrumentResults.value = it }
            .onFailure { _instrumentResults.value = emptyList(); _instrumentSearchError.value = it.message ?: "Search is unavailable." }
    } }
    fun clearInstrumentSearch() { instrumentSearchJob?.cancel(); _instrumentResults.value = emptyList(); _instrumentSearchError.value = null }
}
