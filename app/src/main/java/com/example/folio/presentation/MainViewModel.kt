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
    fun saveAsset(asset: AssetEntity, tags: List<Long> = emptyList(), done: (Long) -> Unit = {}) = viewModelScope.launch { runCatching { repo.saveAsset(asset, tags).also { repo.saveSnapshots() } }.onSuccess { refresh.value++; done(it) } }
    fun saveAsset(
        asset: AssetEntity,
        tags: List<Long>,
        onError: (String) -> Unit = {},
        done: () -> Unit,
    ) = viewModelScope.launch {
        runCatching { repo.saveAsset(asset, tags).also { repo.saveSnapshots() } }
            .onSuccess { refresh.value++; done() }
            .onFailure { onError(it.message ?: "Unable to save asset") }
    }
    fun saveAsset(asset: AssetEntity, tags: List<Long>, done: () -> Unit) =
        saveAsset(asset, tags, {}, done)
    fun saveAssetWithOpening(asset: AssetEntity, tags: List<Long>, quantity: String, averageCost: String, locationId: Long?, onError: (String) -> Unit = {}, done: (Long) -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val q = quantity.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
            val average = averageCost.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
            require(q != null || (average == null && locationId == null)) { "برای قیمت خرید اولیه، تعداد را وارد کنید." }
            if (q != null) require(q > BigDecimal.ZERO) { "تعداد اولیه باید بزرگ‌تر از صفر باشد." }
            val id = repo.saveAsset(asset, tags)
            if (q != null) repo.saveTransaction(TransactionEntity(assetId = id, transactionType = "BUY", quantity = q, pricePerUnit = average, transactionCurrency = asset.manualPriceCurrency.ifBlank { "IRT" }, locationId = locationId, executedAt = Instant.now(), notes = "Initial position"))
            repo.saveSnapshots(); id
        }.onSuccess { refresh.value++; done(it) }.onFailure { onError(it.message ?: "Unable to save opening holding") }
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
    fun saveTransaction(item: TransactionEntity, onError: (String) -> Unit = {}, done: () -> Unit = {}) = viewModelScope.launch { runCatching { repo.saveTransaction(item); repo.saveSnapshots() }.onSuccess { refresh.value++; done() }.onFailure { onError(it.message ?: "Unable to save") } }
    fun deleteTransaction(item: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(item); repo.saveSnapshots(); refresh.value++ }
    fun savePrice(assetId: Long, price: String, currency: String, onError: (String) -> Unit = {}, done: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            repo.savePrice(AssetPriceEntity(assetId = assetId, price = BigDecimal(price), currency = currency, provider = "MANUAL", capturedAt = Instant.now()))
            repo.saveSnapshots()
        }.onSuccess {
            refresh.value++
            done()
        }.onFailure {
            onError(it.message ?: "Invalid price")
        }
    }
    fun saveTag(name: String) = viewModelScope.launch { runCatching { repo.saveTag(name) } }
    fun saveLocation(name: String, notes: String) = viewModelScope.launch { runCatching { repo.saveLocation(name, notes) } }
    fun refreshDashboard() { refresh.value++ }
    fun refreshPrices() {
        val workId = PriceRefreshWorker.refreshNow(appContext)
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId)
                .filter { it?.state?.isFinished == true }
                .first()
            // The worker owns a different Room instance, so its writes do not
            // reliably invalidate this instance's observed queries. Re-read
            // after this exact refresh has finished to update all value views.
            refreshDashboard()
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
