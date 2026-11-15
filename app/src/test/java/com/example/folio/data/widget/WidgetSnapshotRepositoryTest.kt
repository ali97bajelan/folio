package com.example.folio.data.widget

import com.example.folio.PortfolioCodes
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class WidgetSnapshotRepositoryTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")

    @Test
    fun snapshot_total_and_percentages_share_the_same_active_values() {
        val assets = listOf(
            asset(1, PortfolioCodes.CRYPTO),
            asset(2, PortfolioCodes.GOLD),
            asset(3, PortfolioCodes.SILVER),
        )
        val snapshot = calculateWidgetPortfolioSnapshot(
            assets = assets,
            transactionsByAssetId = assets.associate { it.id to listOf(transaction(it.id, it.id)) },
            pricesByAssetId = mapOf(
                1L to listOf(price(1, "60")),
                2L to listOf(price(2, "30")),
                3L to listOf(price(3, "10")),
            ),
            currency = PortfolioCodes.IRT,
            now = now,
        )

        assertDecimal("100", snapshot.total)
        assertEquals(listOf(PortfolioCodes.CRYPTO, PortfolioCodes.GOLD, PortfolioCodes.SILVER), snapshot.typeValues.keys.toList())
        val percentages = widgetAllocationPercentages(snapshot.typeValues)
        assertDecimal("60.0", percentages[PortfolioCodes.CRYPTO])
        assertDecimal("30.0", percentages[PortfolioCodes.GOLD])
        assertDecimal("10.0", percentages[PortfolioCodes.SILVER])
        assertEquals(now, snapshot.generatedAt)
        assertEquals(now.minusSeconds(60), snapshot.valuedAt)
    }

    @Test
    fun compact_type_values_keep_top_three_and_sum_the_rest_as_other() {
        val values = linkedMapOf(
            "E" to BigDecimal("10"),
            "D" to BigDecimal("30"),
            "C" to BigDecimal("30"),
            "B" to BigDecimal("40"),
            "A" to BigDecimal("50"),
        )

        val compact = compactWidgetTypeValues(values, otherLabel = "Other")

        assertEquals(listOf("A", "B", "C", "Other"), compact.keys.toList())
        assertDecimal("40", compact["Other"])
        assertDecimal("160", compact.values.fold(BigDecimal.ZERO, BigDecimal::add))
    }

    @Test
    fun allocation_percentages_are_empty_for_a_zero_denominator() {
        val percentages = widgetAllocationPercentages(
            mapOf("EMPTY" to BigDecimal.ZERO),
        )

        assertTrue(percentages.isEmpty())
    }

    @Test
    fun inactive_assets_do_not_contribute_to_snapshot() {
        val active = asset(1, PortfolioCodes.CRYPTO)
        val inactive = asset(2, PortfolioCodes.GOLD, isActive = false)
        val snapshot = calculateWidgetPortfolioSnapshot(
            assets = listOf(active, inactive),
            transactionsByAssetId = mapOf(
                active.id to listOf(transaction(active.id, 1, quantity = "2")),
                inactive.id to listOf(transaction(inactive.id, 2, quantity = "100")),
            ),
            pricesByAssetId = mapOf(
                active.id to listOf(price(active.id, "6")),
                inactive.id to listOf(price(inactive.id, "1000")),
            ),
            currency = PortfolioCodes.IRT,
            now = now,
        )

        assertDecimal("12", snapshot.total)
        assertEquals(mapOf(PortfolioCodes.CRYPTO to BigDecimal("12")), snapshot.typeValues)
        assertEquals(0, snapshot.missingCount)
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun missing_fx_keeps_known_subtotal_and_counts_unpriced_holding() {
        val known = asset(1, PortfolioCodes.GOLD)
        val needsFx = asset(2, PortfolioCodes.CRYPTO)
        val snapshot = calculateWidgetPortfolioSnapshot(
            assets = listOf(known, needsFx),
            transactionsByAssetId = mapOf(
                known.id to listOf(transaction(known.id, 1)),
                needsFx.id to listOf(transaction(needsFx.id, 2)),
            ),
            pricesByAssetId = mapOf(
                known.id to listOf(price(known.id, "40", PortfolioCodes.IRT)),
                needsFx.id to listOf(price(needsFx.id, "2", PortfolioCodes.USDT)),
            ),
            currency = PortfolioCodes.IRT,
            now = now,
        )

        assertDecimal("40", snapshot.total)
        assertEquals(1, snapshot.missingCount)
        assertFalse(snapshot.isEmpty)
        assertEquals(mapOf(PortfolioCodes.GOLD to BigDecimal("40")), snapshot.typeValues)
    }

    @Test
    fun all_nonzero_holdings_without_required_fx_make_value_unavailable() {
        val asset = asset(1, PortfolioCodes.CRYPTO)
        val snapshot = calculateWidgetPortfolioSnapshot(
            assets = listOf(asset),
            transactionsByAssetId = mapOf(asset.id to listOf(transaction(asset.id, 1))),
            pricesByAssetId = mapOf(asset.id to listOf(price(asset.id, "80", PortfolioCodes.IRT))),
            currency = PortfolioCodes.USD,
            now = now,
        )

        assertNull(snapshot.total)
        assertEquals(1, snapshot.missingCount)
        assertFalse(snapshot.isEmpty)
        assertTrue(snapshot.typeValues.isEmpty())
    }

    @Test
    fun zero_holdings_need_no_quote_and_produce_an_empty_zero_snapshot() {
        val asset = asset(1, PortfolioCodes.CRYPTO)
        val snapshot = calculateWidgetPortfolioSnapshot(
            assets = listOf(asset),
            transactionsByAssetId = mapOf(
                asset.id to listOf(
                    transaction(asset.id, 1, PortfolioCodes.BUY, "2"),
                    transaction(asset.id, 2, PortfolioCodes.SELL, "2"),
                ),
            ),
            pricesByAssetId = emptyMap(),
            currency = PortfolioCodes.IRT,
            now = now,
        )

        assertDecimal("0", snapshot.total)
        assertEquals(0, snapshot.missingCount)
        assertTrue(snapshot.isEmpty)
        assertTrue(snapshot.typeValues.isEmpty())
        assertNull(snapshot.valuedAt)
    }

    private fun asset(id: Long, type: String, isActive: Boolean = true) = AssetEntity(
        id = id,
        name = "Asset $id",
        symbol = "A$id",
        assetType = type,
        isActive = isActive,
    )

    private fun transaction(
        assetId: Long,
        id: Long,
        kind: String = PortfolioCodes.BUY,
        quantity: String = "1",
    ) = TransactionEntity(
        id = id,
        assetId = assetId,
        transactionType = kind,
        quantity = BigDecimal(quantity),
        executedAt = now.minusSeconds(120),
    )

    private fun price(
        assetId: Long,
        value: String,
        currency: String = PortfolioCodes.IRT,
    ) = AssetPriceEntity(
        assetId = assetId,
        price = BigDecimal(value),
        currency = currency,
        provider = PortfolioCodes.NOBITEX,
        providerSymbol = "A$assetId",
        capturedAt = now.minusSeconds(60),
    )

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        requireNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
