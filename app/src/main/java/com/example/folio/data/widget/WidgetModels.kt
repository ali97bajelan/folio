package com.example.folio.data.widget

import com.example.folio.PortfolioCodes
import java.math.BigDecimal
import java.time.Instant

enum class WidgetKind {
    PORTFOLIO,
    ASSET_PRICE,
}

data class MarketKey(
    val provider: String,
    val providerSymbol: String,
)

data class MarketSelection(
    val key: MarketKey,
    val displayName: String,
    val symbol: String,
    val assetType: String,
)

data class WidgetConfiguration(
    val appWidgetId: Int,
    val kind: WidgetKind,
    val currency: String,
    val hideTotal: Boolean,
    val market: MarketSelection?,
    val revision: Long,
)

enum class QuoteStatus {
    NEVER_LOADED,
    AVAILABLE,
    FAILED,
    UNSUPPORTED,
}

data class WidgetQuote(
    val key: MarketKey,
    val price: BigDecimal?,
    val currency: String?,
    val quoteAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastAttemptAt: Instant?,
    val status: QuoteStatus,
    val lastError: String? = null,
)

data class WidgetPortfolioSnapshot(
    val total: BigDecimal?,
    val typeValues: Map<String, BigDecimal>,
    val missingCount: Int,
    val isEmpty: Boolean,
    val valuedAt: Instant?,
    val generatedAt: Instant,
)

private val supportedMarketProviders = setOf(
    PortfolioCodes.NOBITEX,
    PortfolioCodes.ABANTETHER,
    PortfolioCodes.TSETMC,
    PortfolioCodes.RAHAVARD,
)

internal fun WidgetConfiguration.validate() {
    require(appWidgetId > 0) { "Widget id must be positive." }
    require(currency == PortfolioCodes.IRT || currency == PortfolioCodes.USD) {
        "Widget currency must be IRT or USD."
    }

    when (kind) {
        WidgetKind.PORTFOLIO -> {
            require(market == null) { "Portfolio widgets cannot select a market." }
        }

        WidgetKind.ASSET_PRICE -> {
            require(!hideTotal) { "Asset price widgets cannot hide a portfolio total." }
            val selection = requireNotNull(market) { "Asset price widgets require a market." }
            selection.key.validate()
            require(selection.displayName.isNotBlank()) { "Market display name cannot be blank." }
            require(selection.symbol.isNotBlank()) { "Market symbol cannot be blank." }
            require(selection.assetType.isNotBlank()) { "Market asset type cannot be blank." }
        }
    }
}

internal fun MarketKey.validate() {
    require(provider in supportedMarketProviders) { "Unsupported market provider: $provider" }
    require(providerSymbol.isNotBlank() && providerSymbol == providerSymbol.trim()) {
        "Provider symbol must be nonblank and trimmed."
    }
}

internal fun WidgetQuote.validate() {
    key.validate()
    when (status) {
        QuoteStatus.AVAILABLE -> {
            require(price != null && price.signum() > 0) { "Available quotes require a positive price." }
            require(!currency.isNullOrBlank() && currency == currency.trim()) {
                "Available quotes require a trimmed currency."
            }
            require(lastSuccessAt != null) { "Available quotes require a success timestamp." }
            require(lastAttemptAt != null) { "Available quotes require an attempt timestamp." }
        }

        QuoteStatus.FAILED, QuoteStatus.UNSUPPORTED ->
            require(lastAttemptAt != null) { "Failed attempts require an attempt timestamp." }

        QuoteStatus.NEVER_LOADED -> error("Never-loaded quotes cannot be persisted.")
    }
}

/**
 * Applies a quote attempt without allowing delayed responses to roll back fresher cache state.
 * A failed attempt updates status/error metadata while retaining the latest successful value.
 */
internal fun mergeWidgetQuote(previous: WidgetQuote?, incoming: WidgetQuote): WidgetQuote? {
    incoming.validate()
    require(previous == null || previous.key == incoming.key) {
        "Quotes with different market identities cannot be merged."
    }

    val previousAttempt = previous?.lastAttemptAt
    val incomingAttempt = incoming.lastAttemptAt
    if (previousAttempt != null && incomingAttempt != null && incomingAttempt.isBefore(previousAttempt)) {
        return null
    }

    if (incoming.status == QuoteStatus.AVAILABLE) {
        val incomingSuccess = requireNotNull(incoming.lastSuccessAt)
        val previousSuccess = previous?.lastSuccessAt
        if (previousSuccess != null && incomingSuccess.isBefore(previousSuccess)) return null
        return incoming.copy(lastError = null)
    }

    return incoming.copy(
        price = previous?.price,
        currency = previous?.currency,
        quoteAt = previous?.quoteAt,
        lastSuccessAt = previous?.lastSuccessAt,
        lastError = incoming.lastError ?: incoming.status.name,
    )
}
