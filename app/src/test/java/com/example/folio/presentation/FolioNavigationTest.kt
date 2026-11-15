package com.example.folio.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioNavigationTest {
    @Test
    fun onlyTopLevelDestinationsShowBottomNavigation() {
        assertTrue(isFolioTopLevelRoute("dashboard"))
        assertTrue(isFolioTopLevelRoute("assets"))
        assertTrue(isFolioTopLevelRoute("transactions"))
        assertTrue(isFolioTopLevelRoute("settings"))

        assertFalse(isFolioTopLevelRoute("tags"))
        assertFalse(isFolioTopLevelRoute("locations"))
        assertFalse(isFolioTopLevelRoute("asset/{id}"))
        assertFalse(isFolioTopLevelRoute("asset/edit/{id}"))
        assertFalse(isFolioTopLevelRoute("transaction/new?asset={asset}"))
    }
}
