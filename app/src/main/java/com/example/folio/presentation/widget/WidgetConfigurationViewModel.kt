package com.example.folio.presentation.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.folio.PortfolioCodes
import com.example.folio.data.network.MarketQuoteClient
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WidgetConfigurationUiState(
    val appWidgetId: Int? = null,
    val kind: WidgetKind? = null,
    val initialized: Boolean = false,
    val isLoading: Boolean = true,
    val currency: String = PortfolioCodes.IRT,
    val hideTotal: Boolean = false,
    val markets: List<MarketSelection> = emptyList(),
    val assetType: String = PortfolioCodes.CRYPTO,
    val provider: String = "",
    val query: String = "",
    val providerSymbol: String = "",
    val market: MarketSelection? = null,
    val marketValidated: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
    val loadError: Boolean = false,
) {
    val canSave: Boolean
        get() = initialized && !isLoading && !isSaving && !loadError && (when (kind) {
            WidgetKind.PORTFOLIO -> true
            WidgetKind.ASSET_PRICE -> markets.isNotEmpty()
            null -> false
        })
}

internal class WidgetConfigurationViewModel(
    private val repository: WidgetRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(readSavedState())
    val state: StateFlow<WidgetConfigurationUiState> = _state.asStateFlow()

    fun initialize(appWidgetId: Int, kind: WidgetKind, existing: WidgetConfiguration?) {
        require(appWidgetId > 0)
        val current = _state.value
        if (current.initialized && current.appWidgetId == appWidgetId && current.kind == kind) {
            setState(current.copy(isLoading = false, isSaving = false))
            return
        }

        val hasMatchingRestoredDraft =
            savedStateHandle.get<Boolean>(KEY_INITIALIZED) == true &&
                savedStateHandle.get<Int>(KEY_WIDGET_ID) == appWidgetId &&
                savedStateHandle.get<String>(KEY_KIND) == kind.name
        if (hasMatchingRestoredDraft) {
            setState(current.copy(isLoading = false))
            return
        }

        val saved = existing?.takeIf { it.appWidgetId == appWidgetId && it.kind == kind }
        setState(
            WidgetConfigurationUiState(
                appWidgetId = appWidgetId,
                kind = kind,
                initialized = true,
                isLoading = false,
                currency = saved?.currency ?: PortfolioCodes.IRT,
                hideTotal = saved?.hideTotal ?: false,
                markets = saved?.markets.orEmpty(),
                assetType = saved?.market?.assetType ?: PortfolioCodes.CRYPTO,
                provider = "",
            ),
        )
    }

    fun markLoadFailed() {
        update { it.copy(isLoading = false, loadError = true) }
    }

    fun updateCurrency(currency: String) {
        require(currency == PortfolioCodes.IRT || currency == PortfolioCodes.USD)
        if (_state.value.kind != WidgetKind.PORTFOLIO) return
        update { it.copy(currency = currency, saveError = false) }
    }

    fun updateHideTotal(hidden: Boolean) {
        if (_state.value.kind != WidgetKind.PORTFOLIO) return
        update { it.copy(hideTotal = hidden, saveError = false) }
    }

    fun updateAssetType(assetType: String) {
        if (_state.value.kind != WidgetKind.ASSET_PRICE || _state.value.assetType == assetType) return
        update {
            it.copy(
                assetType = assetType,
                provider = "",
                query = "",
                providerSymbol = "",
                market = null,
                marketValidated = false,
                saveError = false,
            )
        }
    }

    fun updateProvider(provider: String) {
        if (_state.value.kind != WidgetKind.ASSET_PRICE || _state.value.provider == provider) return
        update {
            it.copy(
                provider = provider,
                query = "",
                providerSymbol = "",
                market = null,
                marketValidated = false,
                saveError = false,
            )
        }
    }

    fun updateQuery(query: String) {
        update { it.copy(query = query, saveError = false) }
    }

    fun updateProviderSymbol(providerSymbol: String) {
        update {
            it.copy(
                providerSymbol = providerSymbol,
                market = null,
                marketValidated = false,
                saveError = false,
            )
        }
    }

    fun updateMarketSelection(selection: MarketSelection?) {
        update {
            it.copy(
                assetType = selection?.assetType ?: it.assetType,
                provider = selection?.key?.provider ?: it.provider,
                providerSymbol = selection?.key?.providerSymbol ?: it.providerSymbol,
                market = selection,
                marketValidated = selection != null,
                saveError = false,
            )
        }
    }

    fun addPendingMarket() {
        val current = _state.value
        val selection = current.market?.takeIf { current.marketValidated } ?: return
        val canonicalKey = canonicalKey(selection.key)
        if (current.markets.any { canonicalKey(it.key) == canonicalKey }) return
        update {
            it.copy(
                markets = it.markets + selection.copy(key = canonicalKey),
                market = null,
                marketValidated = false,
                provider = "",
                query = "",
                providerSymbol = "",
                saveError = false,
            )
        }
    }

    fun removeMarket(key: MarketKey) {
        update {
            it.copy(
                markets = it.markets.filterNot { selection ->
                    canonicalKey(selection.key) == canonicalKey(key)
                },
                saveError = false,
            )
        }
    }

    private fun canonicalKey(key: MarketKey): MarketKey =
        runCatching {
            MarketQuoteClient().canonicalKey(key.provider, key.providerSymbol)
        }.getOrElse {
            MarketKey(key.provider.trim().uppercase(), key.providerSymbol.trim())
        }

    suspend fun save(): WidgetConfiguration? {
        val current = _state.value
        if (!current.canSave) return null

        setState(current.copy(isSaving = true, saveError = false))
        return try {
            val saved = repository.save(current.toConfiguration())
            update { it.copy(isSaving = false) }
            saved
        } catch (cancellation: CancellationException) {
            update { it.copy(isSaving = false) }
            throw cancellation
        } catch (_: Exception) {
            update { it.copy(isSaving = false, saveError = true) }
            null
        }
    }

    private fun update(transform: (WidgetConfigurationUiState) -> WidgetConfigurationUiState) {
        setState(transform(_state.value))
    }

    private fun setState(value: WidgetConfigurationUiState) {
        _state.value = value
        savedStateHandle[KEY_WIDGET_ID] = value.appWidgetId
        savedStateHandle[KEY_KIND] = value.kind?.name
        savedStateHandle[KEY_INITIALIZED] = value.initialized
        savedStateHandle[KEY_CURRENCY] = value.currency
        savedStateHandle[KEY_HIDE_TOTAL] = value.hideTotal
        savedStateHandle[KEY_ASSET_TYPE] = value.assetType
        savedStateHandle[KEY_PROVIDER] = value.provider
        savedStateHandle[KEY_QUERY] = value.query
        savedStateHandle[KEY_PROVIDER_SYMBOL] = value.providerSymbol
        savedStateHandle[KEY_MARKET_VALIDATED] = value.marketValidated
        savedStateHandle[KEY_LOAD_ERROR] = value.loadError
        savedStateHandle[KEY_MARKET_PROVIDER] = value.market?.key?.provider
        savedStateHandle[KEY_MARKET_PROVIDER_SYMBOL] = value.market?.key?.providerSymbol
        savedStateHandle[KEY_MARKET_DISPLAY_NAME] = value.market?.displayName
        savedStateHandle[KEY_MARKET_SYMBOL] = value.market?.symbol
        savedStateHandle[KEY_MARKET_ASSET_TYPE] = value.market?.assetType
        savedStateHandle[KEY_SELECTED_PROVIDERS] = ArrayList(value.markets.map { it.key.provider })
        savedStateHandle[KEY_SELECTED_SYMBOLS] = ArrayList(value.markets.map { it.key.providerSymbol })
        savedStateHandle[KEY_SELECTED_NAMES] = ArrayList(value.markets.map { it.displayName })
        savedStateHandle[KEY_SELECTED_ASSET_SYMBOLS] = ArrayList(value.markets.map { it.symbol })
        savedStateHandle[KEY_SELECTED_TYPES] = ArrayList(value.markets.map { it.assetType })
    }

    private fun readSavedState(): WidgetConfigurationUiState {
        val marketProvider = savedStateHandle.get<String>(KEY_MARKET_PROVIDER)
        val marketProviderSymbol = savedStateHandle.get<String>(KEY_MARKET_PROVIDER_SYMBOL)
        val displayName = savedStateHandle.get<String>(KEY_MARKET_DISPLAY_NAME)
        val symbol = savedStateHandle.get<String>(KEY_MARKET_SYMBOL)
        val assetType = savedStateHandle.get<String>(KEY_MARKET_ASSET_TYPE)
        val market = if (
            marketProvider != null && marketProviderSymbol != null &&
            displayName != null && symbol != null && assetType != null
        ) {
            MarketSelection(
                key = MarketKey(marketProvider, marketProviderSymbol),
                displayName = displayName,
                symbol = symbol,
                assetType = assetType,
            )
        } else {
            null
        }
        val providers = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_PROVIDERS).orEmpty()
        val symbols = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_SYMBOLS).orEmpty()
        val names = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_NAMES).orEmpty()
        val assetSymbols = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_ASSET_SYMBOLS).orEmpty()
        val types = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_TYPES).orEmpty()
        val markets = providers.indices.mapNotNull { index ->
            if (index >= symbols.size || index >= names.size || index >= assetSymbols.size || index >= types.size) {
                null
            } else {
                MarketSelection(
                    key = MarketKey(providers[index], symbols[index]),
                    displayName = names[index],
                    symbol = assetSymbols[index],
                    assetType = types[index],
                )
            }
        }

        return WidgetConfigurationUiState(
            appWidgetId = savedStateHandle.get<Int>(KEY_WIDGET_ID),
            kind = savedStateHandle.get<String>(KEY_KIND)?.let { runCatching { WidgetKind.valueOf(it) }.getOrNull() },
            initialized = savedStateHandle.get<Boolean>(KEY_INITIALIZED) ?: false,
            isLoading = savedStateHandle.get<Boolean>(KEY_INITIALIZED) != true,
            currency = savedStateHandle.get<String>(KEY_CURRENCY) ?: PortfolioCodes.IRT,
            hideTotal = savedStateHandle.get<Boolean>(KEY_HIDE_TOTAL) ?: false,
            markets = markets.ifEmpty { listOfNotNull(market) },
            assetType = savedStateHandle.get<String>(KEY_ASSET_TYPE) ?: market?.assetType ?: PortfolioCodes.CRYPTO,
            provider = savedStateHandle.get<String>(KEY_PROVIDER).orEmpty(),
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
            providerSymbol = savedStateHandle.get<String>(KEY_PROVIDER_SYMBOL).orEmpty(),
            market = market,
            marketValidated = savedStateHandle.get<Boolean>(KEY_MARKET_VALIDATED) ?: (market != null),
            loadError = savedStateHandle.get<Boolean>(KEY_LOAD_ERROR) ?: false,
        )
    }

    private fun WidgetConfigurationUiState.toConfiguration(): WidgetConfiguration =
        WidgetConfiguration(
            appWidgetId = requireNotNull(appWidgetId),
            kind = requireNotNull(kind),
            currency = currency,
            hideTotal = hideTotal,
            markets = if (kind == WidgetKind.ASSET_PRICE) markets else emptyList(),
            revision = 0,
        )

    private companion object {
        const val KEY_WIDGET_ID = "widget_configuration.widget_id"
        const val KEY_KIND = "widget_configuration.kind"
        const val KEY_INITIALIZED = "widget_configuration.initialized"
        const val KEY_CURRENCY = "widget_configuration.currency"
        const val KEY_HIDE_TOTAL = "widget_configuration.hide_total"
        const val KEY_ASSET_TYPE = "widget_configuration.asset_type"
        const val KEY_PROVIDER = "widget_configuration.provider"
        const val KEY_QUERY = "widget_configuration.query"
        const val KEY_PROVIDER_SYMBOL = "widget_configuration.provider_symbol"
        const val KEY_MARKET_PROVIDER = "widget_configuration.market.provider"
        const val KEY_MARKET_PROVIDER_SYMBOL = "widget_configuration.market.provider_symbol"
        const val KEY_MARKET_DISPLAY_NAME = "widget_configuration.market.display_name"
        const val KEY_MARKET_SYMBOL = "widget_configuration.market.symbol"
        const val KEY_MARKET_ASSET_TYPE = "widget_configuration.market.asset_type"
        const val KEY_MARKET_VALIDATED = "widget_configuration.market_validated"
        const val KEY_SELECTED_PROVIDERS = "widget_configuration.selected.providers"
        const val KEY_SELECTED_SYMBOLS = "widget_configuration.selected.symbols"
        const val KEY_SELECTED_NAMES = "widget_configuration.selected.names"
        const val KEY_SELECTED_ASSET_SYMBOLS = "widget_configuration.selected.asset_symbols"
        const val KEY_SELECTED_TYPES = "widget_configuration.selected.asset_types"
        const val KEY_LOAD_ERROR = "widget_configuration.load_error"
    }
}

internal fun resolveWidgetKind(
    appPackage: String,
    providerPackage: String,
    providerClass: String,
): WidgetKind? {
    if (providerPackage != appPackage) return null
    return when (providerClass) {
        "$appPackage.presentation.widget.PortfolioWidgetReceiver" -> WidgetKind.PORTFOLIO
        "$appPackage.presentation.widget.AssetPriceWidgetReceiver" -> WidgetKind.ASSET_PRICE
        else -> null
    }
}
