package com.example.folio.presentation.widget

import androidx.lifecycle.SavedStateHandle
import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetDao
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuoteEntity
import com.example.folio.data.widget.WidgetRepository
import com.example.folio.data.widget.WidgetConfigurationEntity
import com.example.folio.data.widget.WidgetConfigurationWithSelections
import com.example.folio.data.widget.WidgetMarketSelectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigurationViewModelTest {
    @Test
    fun portfolio_draft_restores_currency_and_visibility() {
        val savedState = SavedStateHandle()
        val viewModel = WidgetConfigurationViewModel(WidgetRepository(EmptyWidgetDao), savedState)

        viewModel.initialize(appWidgetId = 12, kind = WidgetKind.PORTFOLIO, existing = null)
        viewModel.updateCurrency(PortfolioCodes.USD)
        viewModel.updateHideTotal(true)

        val restored = WidgetConfigurationViewModel(WidgetRepository(EmptyWidgetDao), savedState)
        restored.initialize(appWidgetId = 12, kind = WidgetKind.PORTFOLIO, existing = null)

        assertEquals(PortfolioCodes.USD, restored.state.value.currency)
        assertTrue(restored.state.value.hideTotal)
        assertTrue(restored.state.value.canSave)
    }

    @Test
    fun price_draft_restores_provider_query_and_identifier_until_selection_is_validated() {
        val savedState = SavedStateHandle()
        val viewModel = WidgetConfigurationViewModel(WidgetRepository(EmptyWidgetDao), savedState)

        viewModel.initialize(appWidgetId = 19, kind = WidgetKind.ASSET_PRICE, existing = null)
        viewModel.updateAssetType(PortfolioCodes.GOLD)
        viewModel.updateProvider(PortfolioCodes.RAHAVARD)
        viewModel.updateQuery("gold")
        viewModel.updateProviderSymbol("123456")
        assertFalse(viewModel.state.value.canSave)

        val restored = WidgetConfigurationViewModel(WidgetRepository(EmptyWidgetDao), savedState)
        restored.initialize(appWidgetId = 19, kind = WidgetKind.ASSET_PRICE, existing = null)

        assertEquals(PortfolioCodes.RAHAVARD, restored.state.value.provider)
        assertEquals("gold", restored.state.value.query)
        assertEquals("123456", restored.state.value.providerSymbol)
        assertNull(restored.state.value.market)
        assertFalse(restored.state.value.canSave)

        restored.updateMarketSelection(
            MarketSelection(
                key = MarketKey(PortfolioCodes.RAHAVARD, "123456"),
                displayName = "Gold",
                symbol = "XAU",
                assetType = PortfolioCodes.GOLD,
            ),
        )
        assertFalse(restored.state.value.canSave)
        restored.addPendingMarket()
        assertTrue(restored.state.value.canSave)
        assertEquals(1, restored.state.value.markets.size)
    }

    @Test
    fun widget_kind_requires_a_known_receiver_owned_by_this_app() {
        assertEquals(
            WidgetKind.PORTFOLIO,
            resolveWidgetKind(
                appPackage = "com.example.folio",
                providerPackage = "com.example.folio",
                providerClass = "com.example.folio.presentation.widget.PortfolioWidgetReceiver",
            ),
        )
        assertEquals(
            WidgetKind.ASSET_PRICE,
            resolveWidgetKind(
                appPackage = "com.example.folio",
                providerPackage = "com.example.folio",
                providerClass = "com.example.folio.presentation.widget.AssetPriceWidgetReceiver",
            ),
        )
        assertNull(
            resolveWidgetKind(
                appPackage = "com.example.folio",
                providerPackage = "com.other.app",
                providerClass = "com.example.folio.presentation.widget.PortfolioWidgetReceiver",
            ),
        )
        assertNull(
            resolveWidgetKind(
                appPackage = "com.example.folio",
                providerPackage = "com.example.folio",
                providerClass = "com.example.folio.OtherReceiver",
            ),
        )
    }

    private object EmptyWidgetDao : WidgetDao {
        override suspend fun configuration(appWidgetId: Int): WidgetConfigurationWithSelections? = null
        override suspend fun configurations(): List<WidgetConfigurationWithSelections> = emptyList()
        override suspend fun selections(appWidgetId: Int): List<WidgetMarketSelectionEntity> = emptyList()
        override suspend fun insertConfiguration(configuration: WidgetConfigurationEntity) = Unit
        override suspend fun updateConfiguration(configuration: WidgetConfigurationEntity) = Unit
        override suspend fun insertSelections(selections: List<WidgetMarketSelectionEntity>) = Unit
        override suspend fun deleteSelections(appWidgetId: Int) = Unit
        override suspend fun deleteConfigurations(appWidgetIds: Set<Int>) = Unit
        override suspend fun updateConfigurationId(oldId: Int, newId: Int): Int = 0
        override suspend fun quote(provider: String, providerSymbol: String): WidgetQuoteEntity? = null
        override suspend fun putQuote(quote: WidgetQuoteEntity) = Unit
        override suspend fun deleteUnreferencedQuotes() = Unit
    }
}
