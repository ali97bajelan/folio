package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.MarketKey
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarketQuoteClientTest {
    @Test
    fun canonical_key_normalizes_nobitex_aliases_and_preserves_numeric_ids() {
        val client = MarketQuoteClient()

        assertEquals(
            MarketKey(PortfolioCodes.NOBITEX, "BTC-RLS"),
            client.canonicalKey(PortfolioCodes.NOBITEX, " btc/IRT "),
        )
        assertEquals(
            client.canonicalKey(PortfolioCodes.NOBITEX, "BTC_IRT"),
            client.canonicalKey(PortfolioCodes.NOBITEX, "BTC-RLS"),
        )
        assertEquals(
            MarketKey(PortfolioCodes.TSETMC, "00035425587644337450"),
            client.canonicalKey(PortfolioCodes.TSETMC, "00035425587644337450"),
        )
        assertEquals(
            MarketKey(PortfolioCodes.RAHAVARD, "00048179"),
            client.canonicalKey(PortfolioCodes.RAHAVARD, "00048179"),
        )
    }

    @Test
    fun fetch_keeps_provider_units_and_numeric_identifiers() = kotlinx.coroutines.runBlocking {
        val client = MarketQuoteClient { provider, url ->
            when (provider) {
                PortfolioCodes.NOBITEX -> if (url.contains("dstCurrency=usdt")) {
                    """{"stats":{"btc-usdt":{"latest":"68000"}}}"""
                } else {
                    """{"stats":{"btc-rls":{"latest":"123450"}}}"""
                }
                PortfolioCodes.ABANTETHER ->
                    """{"data":[{"symbol":"BTC","is_active":true,"price_buy":"100","price_sell":"102"}]}"""
                PortfolioCodes.TSETMC ->
                    """{"closingPriceInfo":{"pClosing":123450}}"""
                PortfolioCodes.RAHAVARD ->
                    """{"data":{"header_last_trade":{"real_close_price":240110000,"end_date_time":"2026-09-20T18:19:00.083+03:30"}}}"""
                else -> error("Unexpected provider: $provider")
            }
        }

        val cases = listOf(
            client.canonicalKey(PortfolioCodes.NOBITEX, "BTCIRT") to ("12345" to PortfolioCodes.IRT),
            client.canonicalKey(PortfolioCodes.NOBITEX, "BTCUSDT") to ("68000" to PortfolioCodes.USDT),
            client.canonicalKey(PortfolioCodes.ABANTETHER, "btc") to ("101" to PortfolioCodes.IRT),
            client.canonicalKey(PortfolioCodes.TSETMC, "00035425587644337450") to ("12345" to PortfolioCodes.IRT),
            client.canonicalKey(PortfolioCodes.RAHAVARD, "00048179") to ("24011000" to PortfolioCodes.IRT),
        )

        cases.forEach { (key, expected) ->
            val result = client.fetch(key)
            assertEquals(BigDecimal(expected.first), result.price)
            assertEquals(expected.second, result.currency)
            assertEquals(key.providerSymbol, result.providerSymbol)
        }
    }

    @Test
    fun fetch_all_downloads_aban_catalogue_once_for_multiple_symbols() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val client = MarketQuoteClient { _, url ->
            requests += url
            """{"data":[
                {"symbol":"BTC","is_active":true,"price_buy":"100","price_sell":"102"},
                {"symbol":"ETH","is_active":true,"price_buy":"20","price_sell":"22"}
            ]}"""
        }
        val bitcoin = client.canonicalKey(PortfolioCodes.ABANTETHER, "btc")
        val ethereum = client.canonicalKey(PortfolioCodes.ABANTETHER, "eth")

        val results = client.fetchAll(setOf(bitcoin, ethereum))

        assertEquals(1, requests.size)
        assertEquals(BigDecimal("101"), results.getValue(bitcoin).getOrThrow().price)
        assertEquals(BigDecimal("21"), results.getValue(ethereum).getOrThrow().price)
    }

    @Test
    fun fetch_all_keeps_invalid_key_failure_and_fetches_other_markets() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val client = MarketQuoteClient { _, url ->
            requests += url
            """{"stats":{"btc-rls":{"latest":"123450"}}}"""
        }
        val valid = client.canonicalKey(PortfolioCodes.NOBITEX, "BTCIRT")
        val invalid = MarketKey("UNKNOWN", "BTC")

        val results = client.fetchAll(setOf(valid, invalid))

        assertTrue(results.getValue(invalid).isFailure)
        assertEquals(BigDecimal("12345"), results.getValue(valid).getOrThrow().price)
        assertEquals(1, requests.size)
    }

    @Test
    fun fetch_all_propagates_coroutine_cancellation() = kotlinx.coroutines.runBlocking {
        val key = MarketKey(PortfolioCodes.NOBITEX, "BTC-RLS")
        val client = MarketQuoteClient { _, _ -> throw CancellationException("cancelled") }

        try {
            client.fetchAll(setOf(key))
            fail("fetchAll should propagate cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation must not be converted into a per-market failure.
        }
    }

    @Test
    fun unsupported_numeric_provider_symbol_is_rejected_without_losing_digits() {
        val client = MarketQuoteClient()
        assertTrue(runCatching { client.canonicalKey(PortfolioCodes.TSETMC, "12A34") }.isFailure)
        assertEquals("000123", client.canonicalKey(PortfolioCodes.RAHAVARD, "000123").providerSymbol)
    }
}
