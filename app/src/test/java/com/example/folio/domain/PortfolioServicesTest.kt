package com.example.folio.domain

import com.example.folio.PortfolioCodes
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PortfolioServicesTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun tx(kind: String, quantity: String, price: String? = null, fee: String? = null, seconds: Long = 0) = TransactionEntity(id = seconds + 1, assetId = 1, transactionType = kind, quantity = BigDecimal(quantity), pricePerUnit = price?.let(::BigDecimal), fee = fee?.let(::BigDecimal), executedAt = now.plusSeconds(seconds))
    @Test fun holdings_are_buy_minus_sell_and_historical() { val items = listOf(tx(PortfolioCodes.BUY, "3"), tx(PortfolioCodes.SELL, "1", seconds = 10)); assertEquals(BigDecimal("2"), HoldingService.quantityAt(items)); assertEquals(BigDecimal("3"), HoldingService.quantityAt(items, now)) }
    @Test fun moving_average_includes_fee_and_sell_keeps_average() { val cost = CostBasisService.current(listOf(tx(PortfolioCodes.BUY, "2", "10", "2"), tx(PortfolioCodes.BUY, "2", "20"), tx(PortfolioCodes.SELL, "1", seconds = 10))); assertEquals(BigDecimal("3"), cost.quantity); assertEquals(0, cost.average!!.compareTo(BigDecimal("15.5"))) }
    @Test fun zero_holding_clears_average() { assertNull(CostBasisService.current(listOf(tx(PortfolioCodes.BUY, "1", "10"), tx(PortfolioCodes.SELL, "1", seconds = 1))).average) }
    @Test fun usd_and_usdt_are_equivalent_and_irt_converts() { assertEquals(BigDecimal("2"), CurrencyConversionService.convert(BigDecimal("2"), PortfolioCodes.USD, PortfolioCodes.USDT, null)); assertEquals(BigDecimal("160000"), CurrencyConversionService.convert(BigDecimal("2"), PortfolioCodes.USD, PortfolioCodes.IRT, BigDecimal("80000"))) }
    @Test fun usdtIrt_rate_values_irt_and_usdt_quoted_assets_in_both_currencies() {
        val fx = BigDecimal("80000")
        val bitcoin = AssetEntity(id = 1, name = "Bitcoin", symbol = "BTC", assetType = PortfolioCodes.CRYPTO)
        val holding = listOf(tx(PortfolioCodes.BUY, "2"))
        val btcIrtPrice = listOf(AssetPriceEntity(assetId = 1, price = BigDecimal("4000000000"), currency = PortfolioCodes.IRT, provider = PortfolioCodes.NOBITEX, capturedAt = now))
        val btcUsdtPrice = listOf(AssetPriceEntity(assetId = 1, price = BigDecimal("50000"), currency = PortfolioCodes.USDT, provider = PortfolioCodes.NOBITEX, capturedAt = now))

        assertEquals(BigDecimal("8000000000"), PortfolioValuationService.assetValue(bitcoin, holding, btcIrtPrice, PortfolioCodes.IRT, null, fx))
        assertEquals(0, BigDecimal("100000").compareTo(PortfolioValuationService.assetValue(bitcoin, holding, btcIrtPrice, PortfolioCodes.USD, null, fx)))
        assertEquals(BigDecimal("100000"), PortfolioValuationService.assetValue(bitcoin, holding, btcUsdtPrice, PortfolioCodes.USD, null, fx))
        assertEquals(BigDecimal("8000000000"), PortfolioValuationService.assetValue(bitcoin, holding, btcUsdtPrice, PortfolioCodes.IRT, null, fx))
    }
}
