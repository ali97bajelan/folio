package com.example.folio.presentation.widget

import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.QuoteStatus
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPriceWidgetPresentationTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val market = MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT")

    @Test
    fun native_currency_format_keeps_irt_amount_in_toman_units() {
        assertEquals(
            "1,234,567 toman (IRT)",
            formatAssetPriceAmount(BigDecimal("1234567"), PortfolioCodes.IRT, "toman (IRT)"),
        )
    }

    @Test
    fun non_irt_price_keeps_its_native_currency_without_conversion() {
        assertEquals(
            "65,000.25 USDT",
            formatAssetPriceAmount(BigDecimal("65000.25"), PortfolioCodes.USDT, "toman (IRT)"),
        )
    }

    @Test
    fun quote_is_stale_only_after_six_hours() {
        val quote = availableQuote(quoteAt = now.minusSeconds(6 * 60 * 60))

        val atBoundary = presentAssetPriceQuote(quote, isUpdating = false, now = now)
        val beyondBoundary = presentAssetPriceQuote(
            quote.copy(quoteAt = now.minusSeconds(6 * 60 * 60 + 1)),
            isUpdating = false,
            now = now,
        )

        assertEquals(AssetPriceQuoteFreshness.FRESH, atBoundary.freshness)
        assertEquals(AssetPriceQuoteFreshness.STALE, beyondBoundary.freshness)
    }

    @Test
    fun missing_successful_quote_is_unavailable_and_never_zero() {
        val failed = WidgetQuote(
            key = market,
            price = null,
            currency = null,
            quoteAt = null,
            lastSuccessAt = null,
            lastAttemptAt = now,
            status = QuoteStatus.FAILED,
            lastError = "offline",
        )

        val presentation = presentAssetPriceQuote(failed, isUpdating = false, now = now)

        assertEquals(AssetPriceQuoteFreshness.UNAVAILABLE, presentation.freshness)
        assertNull(presentation.price)
        assertTrue(presentation.refreshFailed)
    }

    @Test
    fun failed_refresh_keeps_cached_value_and_reports_failure() {
        val quote = availableQuote(quoteAt = now.minusSeconds(30)).copy(
            lastAttemptAt = now,
            status = QuoteStatus.FAILED,
            lastError = "offline",
        )

        val presentation = presentAssetPriceQuote(quote, isUpdating = true, now = now)

        assertEquals(BigDecimal("65000.25"), presentation.price)
        assertEquals(PortfolioCodes.USDT, presentation.currency)
        assertTrue(presentation.refreshFailed)
        assertTrue(presentation.isUpdating)
        assertEquals(AssetPriceQuoteFreshness.FRESH, presentation.freshness)
    }

    @Test
    fun unsupported_market_does_not_expose_its_retained_quote_as_current() {
        val unsupported = availableQuote(now).copy(
            lastAttemptAt = now,
            status = QuoteStatus.UNSUPPORTED,
        )

        val presentation = presentAssetPriceQuote(unsupported, isUpdating = false, now = now)

        assertEquals(AssetPriceQuoteFreshness.UNAVAILABLE, presentation.freshness)
        assertNull(presentation.price)
        assertTrue(presentation.unsupported)
    }

    @Test
    fun changed_market_configuration_is_not_accepted_for_old_quote_publication() {
        val previousMarket = MarketSelection(
            key = market,
            displayName = "Bitcoin",
            symbol = "BTC",
            assetType = PortfolioCodes.CRYPTO,
        )
        val changedMarket = previousMarket.copy(
            key = MarketKey(PortfolioCodes.TSETMC, "123456789"),
        )
        val loaded = WidgetConfiguration(
            appWidgetId = 7,
            kind = WidgetKind.ASSET_PRICE,
            currency = PortfolioCodes.IRT,
            hideTotal = false,
            market = previousMarket,
            revision = 2,
        )
        val current = loaded.copy(markets = listOf(changedMarket), revision = 3)

        assertFalse(isCurrentAssetPriceConfiguration(loaded, current))
    }

    private fun availableQuote(quoteAt: Instant) = WidgetQuote(
        key = market,
        price = BigDecimal("65000.25"),
        currency = PortfolioCodes.USDT,
        quoteAt = quoteAt,
        lastSuccessAt = quoteAt,
        lastAttemptAt = quoteAt,
        status = QuoteStatus.AVAILABLE,
    )
}
