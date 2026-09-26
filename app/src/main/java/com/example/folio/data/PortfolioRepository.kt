package com.example.folio.data

import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.AssetTagCrossRef
import com.example.folio.data.local.AssetValuationEntity
import com.example.folio.data.local.PortfolioDao
import com.example.folio.data.local.TagEntity
import com.example.folio.data.local.LocationEntity
import com.example.folio.data.local.PortfolioSnapshotEntity
import com.example.folio.data.local.TransactionEntity
import com.example.folio.domain.CostBasis
import com.example.folio.domain.CostBasisService
import com.example.folio.domain.CurrencyConversionService
import com.example.folio.domain.HoldingService
import com.example.folio.domain.PortfolioValuationService
import com.example.folio.domain.PricingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.Instant

class PortfolioRepository(private val dao: PortfolioDao) {
    val assets: Flow<List<AssetEntity>> = dao.observeAssets()
    val tags = dao.observeTags()
    val locations = dao.observeLocations()
    val transactions = dao.observeTransactions()
    val prices = dao.observeAllPrices()
    val refreshes = dao.observeRefreshes()
    val valuations = dao.observeValuations()
    val portfolioSnapshots = dao.observePortfolioSnapshots()

    fun asset(id: Long) = dao.observeAsset(id)
    fun assetTagIds(id: Long) = dao.observeAssetTagIds(id)

    suspend fun saveAsset(asset: AssetEntity, tagIds: List<Long> = emptyList()): Long {
        validateAsset(asset)
        val assetId = if (asset.id == 0L) {
            dao.insertAsset(asset)
        } else {
            val existing = requireNotNull(dao.asset(asset.id)) { "Asset no longer exists." }
            dao.updateAsset(asset.copy(createdAt = existing.createdAt, updatedAt = Instant.now()))
            asset.id
        }
        dao.clearAssetTags(assetId)
        dao.insertAssetTags(tagIds.distinct().map { AssetTagCrossRef(assetId = assetId, tagId = it) })
        return assetId
    }

    suspend fun deleteAsset(asset: AssetEntity) = dao.deleteAssetAndTransactions(asset)

    suspend fun saveTag(name: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Name is required." }
        return dao.insertTag(TagEntity(name = trimmedName))
    }

    suspend fun saveLocation(name: String, notes: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Name is required." }
        return dao.insertLocation(LocationEntity(name = trimmedName, notes = notes.trim()))
    }

    /** Adds the built-in places once; user-created locations are never changed. */
    suspend fun seedDefaultLocations(names: List<String>) {
        dao.insertLocations(
            names.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .map { name -> LocationEntity(name = name) }
                .toList(),
        )
    }

    suspend fun saveTransaction(item: TransactionEntity) {
        validateTransaction(item)
        val existing = dao.transactions(item.assetId).filter { it.id != item.id }
        require(HoldingService.timelineIsValid(existing + item)) {
            "This change would create a negative holding at some point in the timeline."
        }
        if (item.id == 0L) {
            dao.insertTransaction(item)
        } else {
            val existing = requireNotNull(dao.transaction(item.id)) { "Transaction no longer exists." }
            dao.updateTransaction(item.copy(createdAt = existing.createdAt, updatedAt = Instant.now()))
        }
    }

    suspend fun deleteTransaction(item: TransactionEntity) = dao.deleteTransaction(item)

    suspend fun savePrice(item: AssetPriceEntity): Long {
        require(item.price > BigDecimal.ZERO) { "Price must be greater than zero." }
        require(item.currency.isNotBlank()) { "Currency is required." }
        return dao.insertPrice(item)
    }

    fun detail(id: Long): Flow<AssetDetail?> = combine(
        dao.observeAsset(id),
        dao.observeTransactionsForAsset(id),
        dao.observePrices(id),
    ) { asset, items, prices ->
        asset?.let {
            AssetDetail(it, items, prices, CostBasisService.current(items), HoldingService.byLocation(items))
        }
    }

    suspend fun dashboard(): DashboardData {
        val allAssets = dao.allAssets()
        val pricesByAsset = allAssets.associate { it.id to dao.prices(it.id) }
        val latestPrices = pricesByAsset.mapValues { (_, prices) -> PricingService.latest(prices) }
        val usdIrt = CurrencyConversionService.usdIrt(allAssets) {
            PricingService.latest(pricesByAsset[it.id].orEmpty(), "IRT")
        }
        val rows = dao.activeAssets().map { asset ->
            val items = dao.transactions(asset.id)
            val prices = pricesByAsset[asset.id].orEmpty()
            AssetRow(
                asset = asset,
                quantity = HoldingService.quantityAt(items),
                price = latestPrices[asset.id],
                // A transaction changes the holding immediately.  Calculate the
                // live values from that holding and the newest stored quote
                // rather than showing an older valuation snapshot.
                tomanValue = PortfolioValuationService.assetValue(asset, items, prices, "IRT", null, usdIrt),
                usdValue = PortfolioValuationService.assetValue(asset, items, prices, "USD", null, usdIrt),
            )
        }.sortedWith(compareByDescending<AssetRow> { it.tomanValue != null }.thenByDescending { it.tomanValue })

        return DashboardData(
            rows = rows,
            tomanTotal = rows.mapNotNull(AssetRow::tomanValue).fold(BigDecimal.ZERO, BigDecimal::add),
            missing = rows.count { it.tomanValue == null },
            updatedAt = latestPrices.values.filterNotNull().maxByOrNull(AssetPriceEntity::capturedAt)?.capturedAt,
        )
    }

    /**
     * Returns complete portfolio snapshots rather than per-asset rows.  All
     * valuations saved in one refresh share a capture time, which makes that
     * timestamp the natural point for the portfolio performance chart.
     */
    suspend fun portfolioHistory(): List<PortfolioHistoryPoint> {
        val snapshots = dao.portfolioSnapshots()
        // Preserve chart history captured by app versions before the total
        // snapshot table existed. New total snapshots override a legacy point
        // recorded at the exact same instant.
        val legacy = dao.valuations()
            .groupBy(AssetValuationEntity::capturedAt)
            .map { (capturedAt, valuations) ->
            PortfolioHistoryPoint(
                capturedAt = capturedAt,
                tomanValue = valuations.mapNotNull(AssetValuationEntity::tomanValue)
                    .fold(BigDecimal.ZERO, BigDecimal::add),
                usdValue = valuations.mapNotNull(AssetValuationEntity::usdtValue)
                    .fold(BigDecimal.ZERO, BigDecimal::add),
            )
        }
        return (legacy + snapshots.map { PortfolioHistoryPoint(it.capturedAt, it.tomanValue, it.usdtValue) })
            .associateBy(PortfolioHistoryPoint::capturedAt)
            .values
            .sortedBy(PortfolioHistoryPoint::capturedAt)
    }

    suspend fun saveSnapshots(now: Instant = Instant.now()) {
        val allAssets = dao.allAssets()
        val pricesByAsset = allAssets.associate { it.id to dao.prices(it.id) }
        val usdIrt = CurrencyConversionService.usdIrt(allAssets) {
            PricingService.latest(pricesByAsset[it.id].orEmpty(), "IRT")
        }
        val valuations = dao.activeAssets().map { asset ->
            val items = dao.transactions(asset.id)
            val prices = pricesByAsset[asset.id].orEmpty()
            AssetValuationEntity(assetId = asset.id, quantity = HoldingService.quantityAt(items), usdtValue = PortfolioValuationService.assetValue(asset, items, prices, "USD", null, usdIrt), tomanValue = PortfolioValuationService.assetValue(asset, items, prices, "IRT", null, usdIrt), capturedAt = now)
        }
        for (valuation in valuations) dao.insertValuation(valuation)
        dao.insertPortfolioSnapshot(PortfolioSnapshotEntity(
            usdtValue = valuations.mapNotNull(AssetValuationEntity::usdtValue).fold(BigDecimal.ZERO, BigDecimal::add),
            tomanValue = valuations.mapNotNull(AssetValuationEntity::tomanValue).fold(BigDecimal.ZERO, BigDecimal::add),
            capturedAt = now,
        ))
    }

    private fun validateAsset(asset: AssetEntity) {
        require(asset.name.isNotBlank()) { "Name is required." }
        require(asset.symbol.isNotBlank()) { "Symbol is required." }
        require(asset.pricingMode in setOf("MANUAL", "MARKET")) { "Pricing mode must be manual or market." }
        if (asset.pricingMode == "MARKET") {
            require(asset.priceProvider.isNotBlank() && asset.priceProvider != "MANUAL") {
                "Choose a market-price provider."
            }
            require(asset.providerSymbol.isNotBlank()) { "Choose a provider market." }
        }
    }

    private fun validateTransaction(item: TransactionEntity) {
        require(item.transactionType in setOf("BUY", "SELL")) { "Transaction type must be buy or sell." }
        require(item.quantity > BigDecimal.ZERO) { "Quantity must be greater than zero." }
        require(item.pricePerUnit == null || item.pricePerUnit >= BigDecimal.ZERO) { "Price cannot be negative." }
        require(item.fee == null || item.fee >= BigDecimal.ZERO) { "Fee cannot be negative." }
        require(item.transactionCurrency.isNotBlank()) { "Transaction currency is required." }
    }
}

data class AssetRow(
    val asset: AssetEntity,
    val quantity: BigDecimal,
    val price: AssetPriceEntity?,
    val tomanValue: BigDecimal?,
    val usdValue: BigDecimal?,
)

data class DashboardData(
    val rows: List<AssetRow>,
    val tomanTotal: BigDecimal,
    val missing: Int,
    val updatedAt: Instant? = null,
)

data class PortfolioHistoryPoint(
    val capturedAt: Instant,
    val tomanValue: BigDecimal,
    val usdValue: BigDecimal,
)

data class AssetDetail(
    val asset: AssetEntity,
    val transactions: List<TransactionEntity>,
    val prices: List<AssetPriceEntity>,
    val costBasis: CostBasis,
    val byLocation: Map<Long?, BigDecimal>,
)
