package com.example.folio.data.widget

import com.example.folio.PortfolioCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class WidgetRepositoryTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")

    @Test
    fun asset_price_configuration_requires_a_supported_market() {
        val valid = WidgetConfiguration(
            appWidgetId = 12,
            kind = WidgetKind.ASSET_PRICE,
            currency = PortfolioCodes.IRT,
            hideTotal = false,
            market = MarketSelection(
                key = MarketKey(PortfolioCodes.NOBITEX, "BTCUSDT"),
                displayName = "Bitcoin",
                symbol = "BTC",
                assetType = PortfolioCodes.CRYPTO,
            ),
            revision = 0,
        )
        valid.validate()

        val invalid = valid.copy(market = null)
        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    @Test
    fun market_identity_includes_provider() {
        val nobitex = MarketKey(PortfolioCodes.NOBITEX, "12345")
        val tsetmc = MarketKey(PortfolioCodes.TSETMC, "12345")

        assertNotEquals(nobitex, tsetmc)
        assertEquals(2, setOf(nobitex, tsetmc).size)
    }

    @Test
    fun failed_attempt_keeps_last_successful_quote_and_marks_status() {
        val key = MarketKey(PortfolioCodes.NOBITEX, "BTCUSDT")
        val previous = WidgetQuote(
            key = key,
            price = BigDecimal("64000.25"),
            currency = PortfolioCodes.USDT,
            quoteAt = now.minusSeconds(60),
            lastSuccessAt = now.minusSeconds(30),
            lastAttemptAt = now.minusSeconds(30),
            status = QuoteStatus.AVAILABLE,
        )
        val failed = WidgetQuote(
            key = key,
            price = null,
            currency = null,
            quoteAt = null,
            lastSuccessAt = null,
            lastAttemptAt = now,
            status = QuoteStatus.FAILED,
            lastError = "offline",
        )

        val merged = mergeWidgetQuote(previous, failed)

        assertEquals(previous.price, merged?.price)
        assertEquals(previous.currency, merged?.currency)
        assertEquals(previous.quoteAt, merged?.quoteAt)
        assertEquals(previous.lastSuccessAt, merged?.lastSuccessAt)
        assertEquals(now, merged?.lastAttemptAt)
        assertEquals(QuoteStatus.FAILED, merged?.status)
        assertEquals("offline", merged?.lastError)
    }

    @Test
    fun older_quote_response_is_discarded() {
        val key = MarketKey(PortfolioCodes.TSETMC, "35425587644337450")
        val latest = WidgetQuote(
            key = key,
            price = BigDecimal("1250"),
            currency = PortfolioCodes.IRT,
            quoteAt = now,
            lastSuccessAt = now,
            lastAttemptAt = now,
            status = QuoteStatus.AVAILABLE,
        )
        val late = latest.copy(
            price = BigDecimal("1200"),
            quoteAt = now.minusSeconds(10),
            lastSuccessAt = now.minusSeconds(10),
            lastAttemptAt = now.minusSeconds(10),
        )

        assertNull(mergeWidgetQuote(latest, late))
    }
}
