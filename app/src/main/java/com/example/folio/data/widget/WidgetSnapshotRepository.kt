package com.example.folio.data.widget

import androidx.room.withTransaction
import com.example.folio.PortfolioCodes
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.data.local.TransactionEntity
import com.example.folio.domain.CurrencyConversionService
import com.example.folio.domain.HoldingService
import com.example.folio.domain.PortfolioValuationService
import com.example.folio.domain.PricingService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class WidgetSnapshotRepository(
    private val database: PortfolioDatabase,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val dao = database.dao()

    /**
     * Reads a coherent snapshot of current portfolio rows. The dashboard headline intentionally
     * uses its last committed refresh snapshot, so these widget values can temporarily differ
     * during local edits or a refresh.
     */
    suspend fun portfolio(
        currency: String,
        now: Instant = Instant.now(),
    ): WidgetPortfolioSnapshot {
        require(currency == PortfolioCodes.IRT || currency == PortfolioCodes.USD) {
            "Widget portfolio currency must be IRT or USD."
        }

        return withContext(calculationDispatcher) {
            val inputs = database.withTransaction {
                val assets = dao.allAssets()
                val transactionsByAssetId = mutableMapOf<Long, List<TransactionEntity>>()
                for (asset in assets) {
                    if (asset.isActive) {
                        transactionsByAssetId[asset.id] = dao.transactions(asset.id)
                    }
                }
                val pricesByAssetId = mutableMapOf<Long, List<AssetPriceEntity>>()
                for (asset in assets) {
                    pricesByAssetId[asset.id] = dao.prices(asset.id)
                }
                SnapshotInputs(assets, transactionsByAssetId, pricesByAssetId)
            }

            calculateWidgetPortfolioSnapshot(
                assets = inputs.assets,
                transactionsByAssetId = inputs.transactionsByAssetId,
                pricesByAssetId = inputs.pricesByAssetId,
                currency = currency,
                now = now,
            )
        }
    }
}

private data class SnapshotInputs(
    val assets: List<AssetEntity>,
    val transactionsByAssetId: Map<Long, List<TransactionEntity>>,
    val pricesByAssetId: Map<Long, List<AssetPriceEntity>>,
)

internal fun calculateWidgetPortfolioSnapshot(
    assets: List<AssetEntity>,
    transactionsByAssetId: Map<Long, List<TransactionEntity>>,
    pricesByAssetId: Map<Long, List<AssetPriceEntity>>,
    currency: String,
    now: Instant,
): WidgetPortfolioSnapshot {
    require(currency == PortfolioCodes.IRT || currency == PortfolioCodes.USD) {
        "Widget portfolio currency must be IRT or USD."
    }

    val usdIrt = CurrencyConversionService.usdIrt(assets) { asset ->
        PricingService.latest(pricesByAssetId[asset.id].orEmpty(), PortfolioCodes.IRT)
    }

    val typeValues = mutableMapOf<String, BigDecimal>()
    val contributingQuoteTimes = mutableListOf<Instant>()
    var activeNonzeroHoldings = 0
    var valuedNonzeroHoldings = 0
    var missingCount = 0

    for (asset in assets) {
        if (!asset.isActive) continue

        val transactions = transactionsByAssetId[asset.id].orEmpty()
        if (HoldingService.quantityAt(transactions).signum() == 0) continue
        activeNonzeroHoldings++

        val prices = pricesByAssetId[asset.id].orEmpty()
        val value = PortfolioValuationService.assetValue(
            asset = asset,
            transactions = transactions,
            prices = prices,
            target = currency,
            moment = null,
            usdIrt = usdIrt,
        )
        if (value == null) {
            missingCount++
            continue
        }

        valuedNonzeroHoldings++
        PricingService.latest(prices)?.capturedAt?.let(contributingQuoteTimes::add)
        if (value.signum() > 0) {
            typeValues[asset.assetType] = (typeValues[asset.assetType] ?: BigDecimal.ZERO) + value
        }
    }

    val orderedTypeValues = sortedWidgetTypeValues(typeValues)
        .associateTo(linkedMapOf()) { (assetType, value) -> assetType to value }
    val total = when {
        activeNonzeroHoldings == 0 -> BigDecimal.ZERO
        valuedNonzeroHoldings == 0 -> null
        else -> orderedTypeValues.values.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    return WidgetPortfolioSnapshot(
        total = total,
        typeValues = orderedTypeValues,
        missingCount = missingCount,
        isEmpty = activeNonzeroHoldings == 0,
        valuedAt = contributingQuoteTimes.maxOrNull(),
        generatedAt = now,
    )
}

internal fun compactWidgetTypeValues(
    typeValues: Map<String, BigDecimal>,
    otherLabel: String,
    maxVisibleTypes: Int = 3,
): Map<String, BigDecimal> {
    require(maxVisibleTypes > 0) { "At least one asset type must remain visible." }
    val sortedValues = sortedWidgetTypeValues(typeValues)
    if (sortedValues.size <= maxVisibleTypes) {
        return sortedValues.associateTo(linkedMapOf()) { it }
    }

    val compacted = linkedMapOf<String, BigDecimal>()
    sortedValues.take(maxVisibleTypes).forEach { (assetType, value) ->
        compacted[assetType] = value
    }
    val otherValue = sortedValues.drop(maxVisibleTypes)
        .fold(BigDecimal.ZERO) { total, (_, value) -> total + value }
    compacted[otherLabel] = otherValue
    return compacted
}

internal fun widgetAllocationPercentages(
    typeValues: Map<String, BigDecimal>,
    decimalPlaces: Int = 1,
): Map<String, BigDecimal> {
    require(decimalPlaces >= 0) { "Decimal places cannot be negative." }
    val sortedValues = sortedWidgetTypeValues(typeValues)
    val denominator = sortedValues.fold(BigDecimal.ZERO) { total, (_, value) -> total + value }
    if (denominator.signum() == 0) return emptyMap()

    return sortedValues.associateTo(linkedMapOf()) { (assetType, value) ->
        assetType to value.multiply(HUNDRED).divide(denominator, decimalPlaces, RoundingMode.HALF_UP)
    }
}

private fun sortedWidgetTypeValues(typeValues: Map<String, BigDecimal>): List<Pair<String, BigDecimal>> =
    typeValues.entries
        .filter { (_, value) -> value.signum() > 0 }
        .sortedWith { left, right ->
            right.value.compareTo(left.value).takeIf { it != 0 }
                ?: left.key.compareTo(right.key)
        }
        .map { (assetType, value) -> assetType to value }

private val HUNDRED = BigDecimal("100")
