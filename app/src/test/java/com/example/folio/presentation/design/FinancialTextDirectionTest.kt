package com.example.folio.presentation.design

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialTextDirectionTest {
    @Test
    fun isolateFinancialTextWrapsNotationWithoutChangingItsContent() {
        assertEquals("\u2066USD 1,234.56\u2069", isolateFinancialText("USD 1,234.56"))
        assertEquals("\u2066ABC/USDT:IRR\u2069", isolateFinancialText("ABC/USDT:IRR"))
    }

    @Test
    fun isolateFinancialTextPreservesEmptyAndPersianText() {
        assertEquals("\u2066\u2069", isolateFinancialText(""))
        assertEquals("\u2066ارزش پرتفوی\u2069", isolateFinancialText("ارزش پرتفوی"))
    }
}
