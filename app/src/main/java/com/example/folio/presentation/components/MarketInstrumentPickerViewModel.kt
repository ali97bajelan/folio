package com.example.folio.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.folio.PortfolioCodes
import com.example.folio.data.network.Instrument
import com.example.folio.data.network.InstrumentSearch
import com.example.folio.data.network.MarketQuoteClient
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class InstrumentSearchStatus {
    IDLE,
    LOADING,
    RESULTS,
    EMPTY,
    ERROR,
}

enum class IdentifierValidationStatus {
    IDLE,
    LOADING,
    VALID,
    ERROR,
}

enum class MarketSelectionOrigin {
    INITIAL,
    SEARCH,
    MANUAL,
}

data class MarketInstrumentPickerState(
    val provider: String = PortfolioCodes.NOBITEX,
    val assetType: String = PortfolioCodes.CRYPTO,
    val query: String = "",
    val providerSymbol: String = "",
    val instruments: List<Instrument> = emptyList(),
    val searchStatus: InstrumentSearchStatus = InstrumentSearchStatus.IDLE,
    val validationStatus: IdentifierValidationStatus = IdentifierValidationStatus.IDLE,
    val selection: MarketSelection? = null,
    val selectionOrigin: MarketSelectionOrigin? = null,
    val initialized: Boolean = false,
)

data class MarketInstrumentSelectionChange(
    val selection: MarketSelection?,
    val origin: MarketSelectionOrigin?,
)

internal class SearchRequestGuard {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun runIfCurrent(requestGeneration: Long, action: () -> Unit): Boolean {
        if (requestGeneration != generation) return false
        action()
        return true
    }
}

/** Owns debounced provider searches and validates manually entered market identifiers. */
class MarketInstrumentPickerViewModel internal constructor(
    private val searchInstruments: suspend (provider: String, query: String, assetType: String) -> List<Instrument>,
    private val quoteClient: MarketQuoteClient,
    private val validateMarketOverride: (suspend (MarketKey) -> Unit)? = null,
    coroutineScopeOverride: CoroutineScope? = null,
    private val awaitDebounce: suspend () -> Unit = { delay(DEBOUNCE_MILLIS) },
) : ViewModel() {
    constructor() : this(
        searchInstruments = { provider, query, assetType ->
            InstrumentSearch.search(provider, query, assetType)
        },
        quoteClient = MarketQuoteClient(),
    )

    private val _state = MutableStateFlow(MarketInstrumentPickerState())
    val state: StateFlow<MarketInstrumentPickerState> = _state.asStateFlow()
    private val selectionChanges = Channel<MarketInstrumentSelectionChange>(Channel.CONFLATED)
    val selectionChangeEvents = selectionChanges.receiveAsFlow()
    private val requestScope = coroutineScopeOverride ?: viewModelScope

    private val searchGuard = SearchRequestGuard()
    private val validationGuard = SearchRequestGuard()
    private var searchJob: Job? = null
    private var validationJob: Job? = null
    private var initialized = false

    /** Returns true only when an already-visible picker was reset for a new asset type. */
    fun initializeOrUpdate(
        assetType: String,
        initialSelection: MarketSelection?,
        initialSelectionValidated: Boolean = true,
    ): Boolean {
        if (!initialized) {
            val normalizedSelection = initialSelection?.let { selection ->
                runCatching {
                    val key = quoteClient.canonicalKey(selection.key.provider, selection.key.providerSymbol)
                    selection.copy(key = key, assetType = assetType)
                }.getOrNull()
            }
            val selectedMarket = if (initialSelectionValidated) normalizedSelection else null
            _state.value = MarketInstrumentPickerState(
                provider = normalizedSelection?.key?.provider
                    ?: initialSelection?.key?.provider
                    ?: defaultProviderFor(assetType),
                assetType = assetType,
                providerSymbol = normalizedSelection?.key?.providerSymbol
                    ?: initialSelection?.key?.providerSymbol.orEmpty(),
                validationStatus = when {
                    initialSelection == null -> IdentifierValidationStatus.IDLE
                    normalizedSelection == null -> IdentifierValidationStatus.ERROR
                    initialSelectionValidated -> IdentifierValidationStatus.VALID
                    else -> IdentifierValidationStatus.LOADING
                },
                selection = selectedMarket,
                selectionOrigin = if (selectedMarket != null) MarketSelectionOrigin.INITIAL else null,
                initialized = true,
            )
            initialized = true
            if (!initialSelectionValidated && normalizedSelection != null) {
                updateProviderSymbol(normalizedSelection.key.providerSymbol)
            }
            return false
        }

        if (_state.value.assetType == assetType) return false
        updateAssetType(assetType)
        return true
    }

    fun changeProvider(provider: String) {
        val current = _state.value
        if (current.provider == provider) return
        cancelSearch()
        cancelValidation()
        _state.update {
            it.copy(
                provider = provider,
                query = "",
                providerSymbol = "",
                instruments = emptyList(),
                searchStatus = InstrumentSearchStatus.IDLE,
                validationStatus = IdentifierValidationStatus.IDLE,
                selection = null,
                selectionOrigin = null,
            )
        }
        if (current.selection != null) publishSelectionChange(null, null)
    }

    fun updateQuery(query: String) {
        val current = _state.value
        _state.update { it.copy(query = query) }
        search(current.provider, query, current.assetType)
    }

    fun updateAssetType(assetType: String) {
        if (_state.value.assetType == assetType) return
        cancelSearch()
        cancelValidation()
        val hadSelection = _state.value.selection != null
        _state.update {
            it.copy(
                assetType = assetType,
                provider = defaultProviderFor(assetType),
                query = "",
                providerSymbol = "",
                instruments = emptyList(),
                searchStatus = InstrumentSearchStatus.IDLE,
                validationStatus = IdentifierValidationStatus.IDLE,
                selection = null,
                selectionOrigin = null,
            )
        }
        if (hadSelection) publishSelectionChange(null, null)
    }

    fun selectInstrument(instrument: Instrument) {
        cancelSearch()
        cancelValidation()
        val current = _state.value
        val key = quoteClient.canonicalKey(current.provider, instrument.providerSymbol)
        val selection = MarketSelection(
            key = key,
            displayName = instrument.name.ifBlank { instrument.symbol },
            symbol = instrument.assetSymbol,
            assetType = current.assetType,
        )
        _state.update {
            it.copy(
                providerSymbol = key.providerSymbol,
                instruments = emptyList(),
                searchStatus = InstrumentSearchStatus.IDLE,
                validationStatus = IdentifierValidationStatus.VALID,
                selection = selection,
                selectionOrigin = MarketSelectionOrigin.SEARCH,
            )
        }
        publishSelectionChange(selection, MarketSelectionOrigin.SEARCH)
    }

    fun updateProviderSymbol(providerSymbol: String) {
        cancelValidation()
        val current = _state.value
        _state.update {
            it.copy(
                providerSymbol = providerSymbol,
                validationStatus = if (providerSymbol.isBlank()) {
                    IdentifierValidationStatus.IDLE
                } else {
                    IdentifierValidationStatus.LOADING
                },
                selection = null,
                selectionOrigin = null,
            )
        }
        if (current.selection != null) publishSelectionChange(null, null)
        if (providerSymbol.isBlank()) return

        val requestGeneration = validationGuard.next()
        validationJob = requestScope.launch {
            awaitDebounce()
            try {
                val key = quoteClient.canonicalKey(current.provider, providerSymbol)
                validateMarket(key)
                val selection = MarketSelection(
                    key = key,
                    displayName = key.providerSymbol,
                    symbol = key.providerSymbol,
                    assetType = current.assetType,
                )
                validationGuard.runIfCurrent(requestGeneration) {
                    _state.update {
                        it.copy(
                            providerSymbol = key.providerSymbol,
                            validationStatus = IdentifierValidationStatus.VALID,
                            selection = selection,
                            selectionOrigin = MarketSelectionOrigin.MANUAL,
                        )
                    }
                    publishSelectionChange(selection, MarketSelectionOrigin.MANUAL)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                validationGuard.runIfCurrent(requestGeneration) {
                    _state.update {
                        it.copy(
                            validationStatus = IdentifierValidationStatus.ERROR,
                            selection = null,
                            selectionOrigin = null,
                        )
                    }
                }
            }
        }
    }

    private fun search(provider: String, query: String, assetType: String) {
        cancelSearch()
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) {
            _state.update {
                it.copy(instruments = emptyList(), searchStatus = InstrumentSearchStatus.IDLE)
            }
            return
        }

        val requestGeneration = searchGuard.next()
        _state.update { it.copy(instruments = emptyList(), searchStatus = InstrumentSearchStatus.LOADING) }
        searchJob = requestScope.launch {
            awaitDebounce()
            try {
                val instruments = searchInstruments(provider, term, assetType)
                searchGuard.runIfCurrent(requestGeneration) {
                    _state.update {
                        it.copy(
                            instruments = instruments,
                            searchStatus = if (instruments.isEmpty()) {
                                InstrumentSearchStatus.EMPTY
                            } else {
                                InstrumentSearchStatus.RESULTS
                            },
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                searchGuard.runIfCurrent(requestGeneration) {
                    _state.update {
                        it.copy(instruments = emptyList(), searchStatus = InstrumentSearchStatus.ERROR)
                    }
                }
            }
        }
    }

    private suspend fun validateMarket(key: MarketKey) {
        val override = validateMarketOverride
        if (override == null) quoteClient.fetch(key) else override(key)
    }

    private fun publishSelectionChange(
        selection: MarketSelection?,
        origin: MarketSelectionOrigin?,
    ) {
        selectionChanges.trySend(MarketInstrumentSelectionChange(selection, origin))
    }

    private fun cancelSearch() {
        searchGuard.next()
        searchJob?.cancel()
        searchJob = null
    }

    private fun cancelValidation() {
        validationGuard.next()
        validationJob?.cancel()
        validationJob = null
    }

    private fun defaultProviderFor(assetType: String): String = when (assetType) {
        PortfolioCodes.IRAN_STOCK -> PortfolioCodes.TSETMC
        PortfolioCodes.US_STOCK -> PortfolioCodes.ABANTETHER
        PortfolioCodes.GOLD, PortfolioCodes.SILVER -> PortfolioCodes.RAHAVARD
        else -> PortfolioCodes.NOBITEX
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private val DEBOUNCE_MILLIS = 300.milliseconds
    }
}
