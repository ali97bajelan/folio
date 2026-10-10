package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ProviderParsersTest {
    @Test
    fun market_pairs_normalize_separators_case_and_rial_aliases() {
        val pairs = listOf(
            " BTC/IRT " to ("btc" to "rls"),
            "BTC_IRT" to ("btc" to "rls"),
            "BTC-RLS" to ("btc" to "rls"),
            "BTCUSDT" to ("btc" to "usdt"),
            "USDTIRT" to ("usdt" to "rls"),
            "BTC-EUR" to ("btc" to "eur"),
            "BTC-USDT-extra" to ("btc" to "usdt-extra"),
        )
        pairs.forEach { (input, expected) ->
            assertEquals(input, expected, ProviderParsers.marketParts(input))
        }
    }

    @Test
    fun market_pairs_reject_missing_base_or_quote() {
        listOf("", "BTC", "USDT", "IRT", "-IRT", "BTC-").forEach { input ->
            assertThrows(input, PricingException.Unsupported::class.java) {
                ProviderParsers.marketParts(input)
            }
        }
    }

    @Test
    fun nobitex_rejects_missing_invalid_and_nonpositive_prices() {
        listOf("", "null", "invalid", "0", "-1").forEach { price ->
            assertThrows(PricingException.Invalid::class.java) {
                ProviderParsers.nobitex("BTCUSDT", """{"stats":{"btc-usdt":{"latest":"$price"}}}""")
            }
        }
        assertThrows(PricingException.Invalid::class.java) {
            ProviderParsers.nobitex("BTCUSDT", """{"stats":{}}""")
        }
    }

    @Test
    fun aban_catalogue_uses_first_active_duplicate_and_matches_case_insensitively() {
        val body = """{"data":[
            {"symbol":"BTC","is_active":false,"price_buy":"1","price_sell":"1"},
            {"symbol":"btc","is_active":true,"price_buy":"100","price_sell":"102"},
            {"symbol":"BTC","is_active":true,"price_buy":"200","price_sell":"202"}
        ]}"""
        val result = ProviderParsers.aban("bTc", body)
        assertEquals(BigDecimal("101"), result.price)
        assertEquals("BTC", result.providerSymbol)
        assertThrows(PricingException.Unsupported::class.java) {
            ProviderParsers.aban("ETH", body)
        }
    }

    @Test fun nobitex_rial_is_saved_as_toman() { val r = ProviderParsers.nobitex("BTCIRT", """{"stats":{"btc-rls":{"latest":"123450"}}}"""); assertEquals(BigDecimal("12345"), r.price); assertEquals(PortfolioCodes.IRT, r.currency) }
    @Test fun tsetmc_rial_is_saved_as_toman() { val r = ProviderParsers.tsetmc("35425587644337450", """{"closingPriceInfo":{"pClosing":123450}}"""); assertEquals(BigDecimal("12345"), r.price) }
    @Test fun aban_uses_buy_sell_midpoint() { val r = ProviderParsers.aban("BTC", """{"data":[{"symbol":"BTC","is_active":true,"price_buy":"100","price_sell":"102"}]}"""); assertEquals(BigDecimal("101"), r.price) }
    @Test fun rahavard_rial_close_is_saved_as_toman() { val r = ProviderParsers.rahavard("48179", """{"data":{"header_last_trade":{"real_close_price":240110000,"end_date_time":"2026-09-20T18:19:00.083+03:30"}}}"""); assertEquals(BigDecimal("24011000"), r.price); assertEquals(PortfolioCodes.IRT, r.currency); assertEquals("2026-09-20T14:49:00.083Z", r.fetchedAt.toString()) }
}
