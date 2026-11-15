package com.example.folio.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class PortfolioWidgetPresentationTest {
    @Test
    fun formatsVisibleTotalWithCurrencyAndLtrIsolation() {
        val total = portfolioWidgetTotalPresentation(
            value = BigDecimal("123456.7"),
            currency = "IRT",
            hidden = false,
            unavailableLabel = "Unavailable",
        )

        assertEquals("\u2066123,457 IRT\u2069", total.text)
        assertEquals(total.text, total.contentDescription)
    }

    @Test
    fun hidesTotalFromRenderedTextAndAccessibilityDescription() {
        val total = portfolioWidgetTotalPresentation(
            value = BigDecimal("123456.78"),
            currency = "IRT",
            hidden = true,
            unavailableLabel = "Unavailable",
        )

        assertEquals("••••", total.text)
        assertEquals(total.text, total.contentDescription)
        assertFalse(total.text.contains("123456"))
    }

    @Test
    fun compactAllocationKeepsTopThreeAndGroupsTheRest() {
        val rows = compactPortfolioWidgetAllocations(
            typeValues = linkedMapOf(
                "Crypto" to BigDecimal("30"),
                "Gold" to BigDecimal("25"),
                "USD" to BigDecimal("20"),
                "Stocks" to BigDecimal("15"),
                "Silver" to BigDecimal("10"),
            ),
            otherLabel = "Other",
        )

        assertEquals(listOf("Crypto", "Gold", "USD", "Other"), rows.map { it.assetType })
        assertEquals(
            listOf("30.0", "25.0", "20.0", "25.0"),
            rows.map { it.percentage.setScale(1).toPlainString() },
        )
        assertEquals(BigDecimal("100.0"), rows.map { it.percentage }.reduce { total, percentage -> total.add(percentage) })
    }
}
