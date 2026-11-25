package com.example.folio.data.network

import androidx.room.withTransaction
import com.example.folio.PortfolioCodes
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.AssetValuationEntity
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.data.local.PortfolioSnapshotEntity
import com.example.folio.data.local.ProviderRefreshEntity
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.QuoteStatus
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import com.example.folio.data.widget.WidgetRepository
import com.example.folio.domain.HoldingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

internal data class WidgetSelectionToken(
    val appWidgetId: Int,
    val revision: Long,
    /** The exact provider identity saved in Room, before canonicalization for fetching. */
    val key: MarketKey,
)

internal data class MarketRefreshTargets(
    val requestedKeys: Set<MarketKey>,
    val portfolioMarketsByAssetId: Map<Long, MarketKey>,
    val portfolioKeyFailures: Map<Long, Exception>,
    val widgetSelectionsByKey: Map<MarketKey, List<WidgetSelectionToken>>,
    val unsupportedWidgetSelections: List<Pair<WidgetSelectionToken, Exception>>,
)

internal fun buildMarketRefreshTargets(
    activeAssets: List<AssetEntity>,
    configurations: List<WidgetConfiguration>,
    quoteClient: MarketQuoteClient,
): MarketRefreshTargets {
    val requestedKeys = linkedSetOf<MarketKey>()
    val portfolioMarketsByAssetId = linkedMapOf<Long, MarketKey>()
    val portfolioKeyFailures = linkedMapOf<Long, Exception>()
    val widgetSelectionsByKey = linkedMapOf<MarketKey, MutableList<WidgetSelectionToken>>()
    val unsupportedWidgetSelections = mutableListOf<Pair<WidgetSelectionToken, Exception>>()

    activeAssets
        .filter { it.pricingMode == PortfolioCodes.MARKET }
        .forEach { asset ->
            try {
                val key = quoteClient.canonicalKey(asset.priceProvider, asset.providerSymbol)
                portfolioMarketsByAssetId[asset.id] = key
                requestedKeys += key
            } catch (error: Exception) {
                portfolioKeyFailures[asset.id] = error
            }
        }

    configurations
        .filter { it.kind == WidgetKind.ASSET_PRICE }
        .forEach { configuration ->
            configuration.markets.forEach { market ->
                val savedKey = market.key
                val token = WidgetSelectionToken(configuration.appWidgetId, configuration.revision, savedKey)
                try {
                    val canonicalKey = quoteClient.canonicalKey(savedKey.provider, savedKey.providerSymbol)
                    requestedKeys += canonicalKey
                    widgetSelectionsByKey.getOrPut(canonicalKey) { mutableListOf() } += token
                } catch (error: Exception) {
                    unsupportedWidgetSelections += token to error
                }
            }
        }

    return MarketRefreshTargets(
        requestedKeys = requestedKeys,
        portfolioMarketsByAssetId = portfolioMarketsByAssetId,
        portfolioKeyFailures = portfolioKeyFailures,
        widgetSelectionsByKey = widgetSelectionsByKey.mapValues { (_, tokens) -> tokens.toList() },
        unsupportedWidgetSelections = unsupportedWidgetSelections,
    )
}

internal fun quoteForWidgetSelection(token: WidgetSelectionToken, quote: WidgetQuote): WidgetQuote =
    quote.copy(key = token.key)

internal fun isCurrentWidgetSelection(
    token: WidgetSelectionToken,
    currentConfigurations: List<WidgetConfiguration>,
): Boolean = currentConfigurations.any { configuration ->
    configuration.appWidgetId == token.appWidgetId &&
        configuration.revision == token.revision &&
        configuration.kind == WidgetKind.ASSET_PRICE &&
        configuration.markets.any { it.key == token.key }
}

internal fun widgetQuoteForAttempt(
    key: MarketKey,
    result: Result<PriceResult>,
    attemptedAt: Instant,
    completedAt: Instant,
): WidgetQuote {
    val quote = result.getOrNull()
    val error = result.exceptionOrNull()
    if (error is CancellationException) throw error
    return if (quote != null) {
        WidgetQuote(
            key = key,
            price = quote.price,
            currency = quote.currency,
            quoteAt = quote.fetchedAt,
            lastSuccessAt = completedAt,
            lastAttemptAt = attemptedAt,
            status = QuoteStatus.AVAILABLE,
        )
    } else {
        WidgetQuote(
            key = key,
            price = null,
            currency = null,
            quoteAt = null,
            lastSuccessAt = null,
            lastAttemptAt = attemptedAt,
            status = if (error is PricingException.Unsupported) QuoteStatus.UNSUPPORTED else QuoteStatus.FAILED,
            lastError = error?.message ?: "Market quote was unavailable.",
        )
    }
}

internal fun shouldSchedulePeriodicRefresh(installedWidgetIds: Set<Int>): Boolean =
    installedWidgetIds.isNotEmpty()

/**
 * One process-wide refresh path for app refresh, widget actions, and periodic work.
 * Network requests finish before any portfolio transaction starts.
 */
class MarketRefreshOperation(
    private val database: PortfolioDatabase,
    private val widgetRepository: WidgetRepository,
    private val quoteClient: MarketQuoteClient = MarketQuoteClient(),
    private val invalidateWidgets: suspend () -> Unit = {},
) {
    private val dao = database.dao()
    private val refreshMutex = Mutex()

    suspend fun refresh() = refreshMutex.withLock {
        val activeAssets = dao.activeAssets()
        val marketAssets = activeAssets.filter {
            it.pricingMode == PortfolioCodes.MARKET &&
                it.priceProvider.uppercase() in PROVIDERS
        }
        val allAssetsAtStart = dao.allAssets()
        val configurationsAtStart = try {
            widgetRepository.configurations()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }

        val targets = buildMarketRefreshTargets(marketAssets, configurationsAtStart, quoteClient)
        val conversionKey = if (allAssetsAtStart.isNotEmpty()) {
            quoteClient.canonicalKey(PortfolioCodes.NOBITEX, usdtIrtAsset().providerSymbol)
        } else {
            null
        }
        val requestedKeys = buildSet {
            addAll(targets.requestedKeys)
            conversionKey?.let(::add)
        }

        val refreshStartedAt = Instant.now()
        val refreshRecords = PROVIDERS.associateWith { provider ->
            val record = ProviderRefreshEntity(
                provider = provider,
                startedAt = refreshStartedAt,
                status = PortfolioCodes.SUCCESS,
            )
            record.copy(id = dao.insertRefresh(record))
        }
        val attemptedAt = Instant.now()
        val quoteResults = quoteClient.fetchAll(requestedKeys)
        val completedAt = Instant.now()

        val failuresByProvider = persistPortfolioQuotes(
            assets = marketAssets,
            targets = targets,
            quoteResults = quoteResults,
        )
        persistWidgetQuotes(
            targets = targets,
            quoteResults = quoteResults,
            attemptedAt = attemptedAt,
            completedAt = completedAt,
        )
        finishProviderRefreshes(
            assets = marketAssets,
            failuresByProvider = failuresByProvider,
            refreshRecords = refreshRecords,
            completedAt = completedAt,
        )

        val fetchedUsdIrt = conversionKey
            ?.let { quoteResults[it]?.getOrNull()?.price }
        saveBothCurrencyPricesAndValuations(fetchedUsdIrt)

        try {
            invalidateWidgets()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A failed Glance update must not roll back saved market data.
        }
    }

    private suspend fun persistPortfolioQuotes(
        assets: List<AssetEntity>,
        targets: MarketRefreshTargets,
        quoteResults: Map<MarketKey, Result<PriceResult>>,
    ): Map<String, MutableList<String>> {
        val failuresByProvider = PROVIDERS.associateWith { mutableListOf<String>() }.toMutableMap()
        assets.forEach { asset ->
            val provider = asset.priceProvider.uppercase()
            try {
                targets.portfolioKeyFailures[asset.id]?.let { throw it }
                val key = targets.portfolioMarketsByAssetId[asset.id]
                    ?: error("No market key was resolved for ${asset.symbol}.")
                val result = quoteResults[key]?.getOrThrow()
                    ?: error("The provider returned no result for ${asset.symbol}.")
                dao.insertPriceForConfiguration(
                    asset,
                    AssetPriceEntity(
                        assetId = asset.id,
                        price = result.price,
                        currency = result.currency,
                        provider = result.provider,
                        // Keep the exact saved provider symbol for the existing configuration guard.
                        providerSymbol = asset.providerSymbol,
                        capturedAt = result.fetchedAt,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failuresByProvider.getOrPut(provider) { mutableListOf() } +=
                    "${asset.symbol}: ${error.message ?: "unavailable"}"
            }
        }
        return failuresByProvider
    }

    private suspend fun persistWidgetQuotes(
        targets: MarketRefreshTargets,
        quoteResults: Map<MarketKey, Result<PriceResult>>,
        attemptedAt: Instant,
        completedAt: Instant,
    ) {
        val currentConfigurations = try {
            widgetRepository.configurations()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return
        }

        for ((key, capturedSelections) in targets.widgetSelectionsByKey) {
            val stillConfigured = capturedSelections.filter { token ->
                isCurrentWidgetSelection(token, currentConfigurations)
            }
            if (stillConfigured.isEmpty()) continue

            val result = quoteResults[key]
                ?: Result.failure(IllegalStateException("No provider result was returned."))
            val canonicalQuote = widgetQuoteForAttempt(key, result, attemptedAt, completedAt)
            for (token in stillConfigured) {
                try {
                    widgetRepository.storeQuoteIfConfigurationCurrent(
                        appWidgetId = token.appWidgetId,
                        revision = token.revision,
                        savedKey = token.key,
                        quote = quoteForWidgetSelection(token, canonicalQuote),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Continue with other widget instances and markets after one cache failure.
                }
            }
        }

        for ((token, error) in targets.unsupportedWidgetSelections) {
            if (!isCurrentWidgetSelection(token, currentConfigurations)) continue
            try {
                val quote = widgetQuoteForAttempt(
                    key = token.key,
                    result = Result.failure(error),
                    attemptedAt = attemptedAt,
                    completedAt = completedAt,
                )
                widgetRepository.storeQuoteIfConfigurationCurrent(
                    appWidgetId = token.appWidgetId,
                    revision = token.revision,
                    savedKey = token.key,
                    quote = quote,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Unsupported state remains local to this market and does not stop other refreshes.
            }
        }
    }

    private suspend fun finishProviderRefreshes(
        assets: List<AssetEntity>,
        failuresByProvider: Map<String, List<String>>,
        refreshRecords: Map<String, ProviderRefreshEntity>,
        completedAt: Instant,
    ) {
        for (provider in PROVIDERS) {
            val providerAssets = assets.filter { it.priceProvider.equals(provider, ignoreCase = true) }
            val failures = failuresByProvider[provider].orEmpty()
            val status = when {
                failures.isEmpty() -> PortfolioCodes.SUCCESS
                failures.size == providerAssets.size -> PortfolioCodes.FAILED
                else -> PortfolioCodes.PARTIAL
            }
            val refreshRecord = requireNotNull(refreshRecords[provider])
            dao.updateRefresh(
                refreshRecord.copy(
                    finishedAt = completedAt,
                    status = status,
                    errorMessage = failures.joinToString(" | "),
                ),
            )
        }
    }

    /**
     * Store derived currency prices and valuation history in one transaction. The FX quote itself
     * was fetched with the shared key batch, outside this transaction.
     */
    private suspend fun saveBothCurrencyPricesAndValuations(fetchedUsdIrt: BigDecimal?) {
        val assets = dao.allAssets()
        if (assets.isEmpty()) return

        database.withTransaction {
            val usdIrt = fetchedUsdIrt ?: storedUsdIrt(assets) ?: return@withTransaction
            val capturedAt = Instant.now()

            val valuations = dao.activeAssets().mapNotNull { asset ->
                saveCounterpartAndValueAsset(asset, usdIrt, capturedAt)
            }
            dao.insertValuationSnapshot(
                valuations,
                PortfolioSnapshotEntity(
                    usdtValue = valuations.map { it.usdtValue ?: BigDecimal.ZERO }
                        .fold(BigDecimal.ZERO, BigDecimal::add),
                    tomanValue = valuations.map { it.tomanValue ?: BigDecimal.ZERO }
                        .fold(BigDecimal.ZERO, BigDecimal::add),
                    capturedAt = capturedAt,
                ),
            )
        }
    }

    private suspend fun saveCounterpartAndValueAsset(
        asset: AssetEntity,
        usdIrt: BigDecimal,
        capturedAt: Instant,
    ): AssetValuationEntity? {
        val source = dao.prices(asset.id).firstOrNull { it.isSourcePriceFor(asset) } ?: return null
        val counterpart = convertedPrice(source, usdIrt, capturedAt) ?: return null
        dao.insertPriceForConfiguration(asset, counterpart.copy(assetId = asset.id)) ?: return null
        val (usdtPrice, tomanPrice) = valuationPrices(source, usdIrt)
        val quantity = HoldingService.quantityAt(dao.transactions(asset.id))
        return AssetValuationEntity(
            assetId = asset.id,
            quantity = quantity,
            usdtValue = quantity.multiply(usdtPrice),
            tomanValue = quantity.multiply(tomanPrice),
            capturedAt = capturedAt,
        )
    }

    // Derived quotes must never become the source of a later FX conversion.
    private fun AssetPriceEntity.isSourcePriceFor(asset: AssetEntity): Boolean =
        if (asset.pricingMode == PortfolioCodes.MANUAL) {
            provider.equals(PortfolioCodes.MANUAL, ignoreCase = true)
        } else {
            provider.equals(asset.priceProvider, ignoreCase = true) &&
                providerSymbol.equals(asset.providerSymbol, ignoreCase = true) &&
                !provider.equals(PortfolioCodes.CALCULATED, ignoreCase = true)
        }

    private fun usdtIrtAsset() = AssetEntity(
        name = "USDT / IRT",
        symbol = "USDTIRT",
        assetType = PortfolioCodes.USD,
        priceProvider = PortfolioCodes.NOBITEX,
        providerSymbol = "USDTIRT",
    )

    private suspend fun storedUsdIrt(assets: List<AssetEntity>): BigDecimal? {
        val aliases = setOf("USD_IRT", "USDTIRT", "USDT_IRT", PortfolioCodes.USDT)
        val fxAsset = assets.firstOrNull {
            it.symbol.uppercase() in aliases ||
                it.providerSymbol.uppercase() in setOf("USDTIRT", "USDT-RLS")
        } ?: return null
        return dao.prices(fxAsset.id).firstOrNull {
            it.currency.equals(PortfolioCodes.IRT, ignoreCase = true) &&
                !it.provider.equals(PortfolioCodes.CALCULATED, ignoreCase = true)
        }?.price
    }

    private fun convertedPrice(
        source: AssetPriceEntity,
        usdIrt: BigDecimal,
        capturedAt: Instant,
    ): AssetPriceEntity? {
        val sourceCurrency = source.currency.uppercase()
        val targetCurrency: String
        val converted: BigDecimal
        when (sourceCurrency) {
            PortfolioCodes.IRT -> {
                targetCurrency = PortfolioCodes.USDT
                converted = source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP)
            }
            PortfolioCodes.USD, PortfolioCodes.USDT -> {
                targetCurrency = PortfolioCodes.IRT
                converted = source.price.multiply(usdIrt)
            }
            else -> return null
        }
        return AssetPriceEntity(
            assetId = 0,
            price = converted,
            currency = targetCurrency,
            provider = PortfolioCodes.CALCULATED,
            providerSymbol = source.providerSymbol,
            capturedAt = capturedAt,
        )
    }

    /** Returns the USDT and toman unit prices from either side of a market pair. */
    private fun valuationPrices(source: AssetPriceEntity, usdIrt: BigDecimal): Pair<BigDecimal, BigDecimal> =
        when (source.currency.uppercase()) {
            PortfolioCodes.IRT -> source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP) to source.price
            PortfolioCodes.USD, PortfolioCodes.USDT -> source.price to source.price.multiply(usdIrt)
            else -> error("Unsupported valuation currency: ${source.currency}")
        }

    companion object {
        private val PROVIDERS = listOf(
            PortfolioCodes.NOBITEX,
            PortfolioCodes.ABANTETHER,
            PortfolioCodes.TSETMC,
            PortfolioCodes.RAHAVARD,
        )
        private const val CONVERSION_SCALE = 20
    }
}
