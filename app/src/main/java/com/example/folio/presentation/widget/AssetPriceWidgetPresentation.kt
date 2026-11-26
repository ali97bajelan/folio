package com.example.folio.presentation.widget

import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.QuoteStatus
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale

internal enum class AssetPriceQuoteFreshness {
    UNAVAILABLE,
    FRESH,
    STALE,
}

internal data class AssetPriceWidgetPresentation(
    val price: BigDecimal?,
    val currency: String?,
    val quoteAt: Instant?,
    val freshness: AssetPriceQuoteFreshness,
    val isUpdating: Boolean,
    val refreshFailed: Boolean,
    val unsupported: Boolean,
    val changePercent: BigDecimal?,
)

internal fun isCurrentAssetPriceConfiguration(
    loaded: WidgetConfiguration?,
    latest: WidgetConfiguration?,
): Boolean =
    loaded != null &&
        latest?.kind == WidgetKind.ASSET_PRICE &&
        loaded.kind == WidgetKind.ASSET_PRICE &&
        loaded == latest

internal fun presentAssetPriceQuote(
    quote: WidgetQuote?,
    isUpdating: Boolean,
    now: Instant,
): AssetPriceWidgetPresentation {
    val unsupported = quote?.status == QuoteStatus.UNSUPPORTED
    val successfulQuote = quote?.takeIf {
        (it.status == QuoteStatus.AVAILABLE || it.status == QuoteStatus.FAILED) &&
            it.price?.signum() == 1 &&
            !it.currency.isNullOrBlank() &&
            it.lastSuccessAt != null
    }
    val quoteAt = successfulQuote?.quoteAt ?: successfulQuote?.lastSuccessAt
    val freshness = when {
        successfulQuote == null || quoteAt == null -> AssetPriceQuoteFreshness.UNAVAILABLE
        Duration.between(quoteAt, now).coerceAtLeast(Duration.ZERO) > MAX_QUOTE_AGE ->
            AssetPriceQuoteFreshness.STALE
        else -> AssetPriceQuoteFreshness.FRESH
    }

    return AssetPriceWidgetPresentation(
        price = successfulQuote?.price,
        currency = successfulQuote?.currency,
        quoteAt = quoteAt,
        freshness = freshness,
        isUpdating = isUpdating,
        refreshFailed = quote?.status == QuoteStatus.FAILED,
        unsupported = unsupported,
        changePercent = successfulQuote?.changePercent,
    )
}

/** Formats the provider's already-normalized quote without applying currency conversion. */
internal fun formatAssetPriceAmount(
    price: BigDecimal,
    currency: String,
    @Suppress("UNUSED_PARAMETER") tomanCurrencyLabel: String,
): String? {
    if (price.signum() <= 0 || currency.isBlank()) return null
    val normalizedCurrency = currency.uppercase(Locale.ROOT)
    val sourceScale = price.stripTrailingZeros().scale().coerceAtLeast(0)
    val fractionDigits = if (normalizedCurrency == PortfolioCodes.IRT) {
        sourceScale
    } else {
        maxOf(2, sourceScale).coerceAtMost(16)
    }
    val number = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = fractionDigits
        minimumFractionDigits = 0
        roundingMode = RoundingMode.DOWN
    }.format(price)
    val displayCurrency = normalizedCurrency
    return "$number $displayCurrency"
}

private val MAX_QUOTE_AGE = Duration.ofHours(6)
