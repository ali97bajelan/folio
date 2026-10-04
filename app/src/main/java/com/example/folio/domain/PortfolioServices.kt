package com.example.folio.domain

import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

private val ZERO = BigDecimal.ZERO
private val DOLLAR_CURRENCIES = setOf("USD", "USDT")

data class CostBasis(val quantity: BigDecimal, val average: BigDecimal?)
data class PortfolioTotal(val value: BigDecimal, val missingPrices: Int)

object HoldingService {
    fun quantityAt(transactions: List<TransactionEntity>, moment: Instant? = null): BigDecimal =
        transactions.asSequence()
            .filter { moment == null || !it.executedAt.isAfter(moment) }
            .fold(ZERO) { total, transaction -> total + signedQuantity(transaction) }

    fun byLocation(transactions: List<TransactionEntity>): Map<Long?, BigDecimal> =
        transactions.groupBy(TransactionEntity::locationId)
            .mapValues { (_, items) -> quantityAt(items) }
            .filterValues { it.signum() != 0 }

    fun timelineIsValid(transactions: List<TransactionEntity>): Boolean {
        var quantity = ZERO
        return transactions.sortedWith(compareBy<TransactionEntity> { it.executedAt }.thenBy { it.id })
            .all { transaction ->
                quantity += signedQuantity(transaction)
                quantity >= ZERO
            }
    }

    private fun signedQuantity(transaction: TransactionEntity): BigDecimal =
        if (transaction.transactionType == "BUY") transaction.quantity else transaction.quantity.negate()
}

object CostBasisService {
    fun current(transactions: List<TransactionEntity>): CostBasis {
        var quantity = ZERO
        var average: BigDecimal? = null
        transactions.sortedWith(compareBy<TransactionEntity> { it.executedAt }.thenBy { it.id }).forEach { transaction ->
            if (transaction.transactionType == "BUY") {
                val cost = transaction.quantity * (transaction.pricePerUnit ?: ZERO) + (transaction.fee ?: ZERO)
                val newQuantity = quantity + transaction.quantity
                average = (quantity * (average ?: ZERO) + cost).divide(newQuantity, 20, RoundingMode.HALF_UP)
                quantity = newQuantity
            } else {
                quantity -= transaction.quantity
                if (quantity.signum() == 0) average = null
            }
        }
        return CostBasis(quantity, average)
    }
}

object PricingService {
    fun latest(prices: List<AssetPriceEntity>): AssetPriceEntity? = prices.maxByOrNull(AssetPriceEntity::capturedAt)

    /** Returns the newest quote in one currency, avoiding ambiguity when both IRT and USDT are stored. */
    fun latest(prices: List<AssetPriceEntity>, currency: String): AssetPriceEntity? =
        prices.asSequence()
            .filter { it.currency.equals(currency, ignoreCase = true) }
            .maxByOrNull(AssetPriceEntity::capturedAt)

    fun at(prices: List<AssetPriceEntity>, moment: Instant): AssetPriceEntity? =
        prices.filter { !it.capturedAt.isAfter(moment) }.maxByOrNull(AssetPriceEntity::capturedAt)
            ?: prices.minByOrNull(AssetPriceEntity::capturedAt)
}

object CurrencyConversionService {
    fun usdIrt(assets: List<AssetEntity>, priceFor: (AssetEntity) -> AssetPriceEntity?): BigDecimal? {
        val aliases = setOf("USD_IRT", "USDTIRT", "USDT_IRT", "USDT")
        val fxAsset = assets.firstOrNull {
            it.symbol.uppercase() in aliases || it.providerSymbol.uppercase() in setOf("USDTIRT", "USDT-RLS")
        } ?: return null
        // A USDT/IRT asset now deliberately has two saved quotes.  The exchange
        // rate must always be read from its IRT quote rather than the derived
        // 1-USDT quote.
        return priceFor(fxAsset)?.takeIf { it.currency.equals("IRT", ignoreCase = true) }?.price
    }

    fun convert(amount: BigDecimal, source: String, target: String, rate: BigDecimal?): BigDecimal? = when {
        source == target || (source in DOLLAR_CURRENCIES && target in DOLLAR_CURRENCIES) -> amount
        rate == null -> null
        source == "IRT" && target in DOLLAR_CURRENCIES -> amount.divide(rate, 20, RoundingMode.HALF_UP)
        source in DOLLAR_CURRENCIES && target == "IRT" -> amount * rate
        else -> null
    }
}

object PortfolioValuationService {
    fun assetValue(
        asset: AssetEntity,
        transactions: List<TransactionEntity>,
        prices: List<AssetPriceEntity>,
        target: String,
        moment: Instant?,
        usdIrt: BigDecimal?,
    ): BigDecimal? {
        val quantity = HoldingService.quantityAt(transactions, moment)
        if (quantity.signum() == 0) return ZERO
        val price = (if (moment == null) PricingService.latest(prices) else PricingService.at(prices, moment))
            ?: return null
        return CurrencyConversionService.convert(quantity * price.price, price.currency, target, usdIrt)
    }
}
