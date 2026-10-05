package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ProviderParsersTest {
    @Test fun nobitex_rial_is_saved_as_toman() { val r = ProviderParsers.nobitex("BTCIRT", """{"stats":{"btc-rls":{"latest":"123450"}}}"""); assertEquals(BigDecimal("12345"), r.price); assertEquals(PortfolioCodes.IRT, r.currency) }
    @Test fun tsetmc_rial_is_saved_as_toman() { val r = ProviderParsers.tsetmc("35425587644337450", """{"closingPriceInfo":{"pClosing":123450}}"""); assertEquals(BigDecimal("12345"), r.price) }
    @Test fun aban_uses_buy_sell_midpoint() { val r = ProviderParsers.aban("BTC", """{"data":[{"symbol":"BTC","is_active":true,"price_buy":"100","price_sell":"102"}]}"""); assertEquals(BigDecimal("101"), r.price) }
    @Test fun rahavard_rial_close_is_saved_as_toman() { val r = ProviderParsers.rahavard("48179", """{"data":{"header_last_trade":{"real_close_price":240110000,"end_date_time":"2026-09-20T18:19:00.083+03:30"}}}"""); assertEquals(BigDecimal("24011000"), r.price); assertEquals(PortfolioCodes.IRT, r.currency); assertEquals("2026-09-20T14:49:00.083Z", r.fetchedAt.toString()) }
}
