package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.QuoteStatus
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import androidx.work.WorkInfo
import com.example.folio.presentation.widget.WidgetLifecycleAction
import com.example.folio.presentation.widget.requiresGlobalWidgetReconciliation
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRefreshOperationTest {
    private val attemptedAt = Instant.parse("2026-11-15T10:00:00Z")
    private val completedAt = attemptedAt.plusSeconds(3)

    @Test
    fun refresh_targets_deduplicate_owned_and_favorite_markets_but_keep_provider_identity() {
        val assets = listOf(
            marketAsset(
                id = 1,
                symbol = "BTC",
                provider = PortfolioCodes.NOBITEX,
                providerSymbol = "BTCUSDT",
            ),
            marketAsset(
                id = 2,
                symbol = "ETH",
                provider = PortfolioCodes.RAHAVARD,
                providerSymbol = "1024",
            ),
        )
        val configurations = listOf(
            priceWidget(11, PortfolioCodes.NOBITEX, "BTC-USDT"),
            priceWidget(12, PortfolioCodes.TSETMC, "1024"),
            priceWidget(13, PortfolioCodes.RAHAVARD, "1024"),
        )

        val targets = buildMarketRefreshTargets(assets, configurations, MarketQuoteClient())

        assertEquals(
            setOf(
                MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"),
                MarketKey(PortfolioCodes.RAHAVARD, "1024"),
                MarketKey(PortfolioCodes.TSETMC, "1024"),
            ),
            targets.requestedKeys,
        )
        assertEquals(
            MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"),
            targets.portfolioMarketsByAssetId[1],
        )
        assertEquals(
            setOf(11),
            targets.widgetSelectionsByKey
                .getValue(MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"))
                .mapTo(mutableSetOf()) { it.appWidgetId },
        )
        assertEquals(2, targets.widgetSelectionsByKey.keys.count { it.providerSymbol == "1024" })
        assertFalse(
            targets.portfolioMarketsByAssetId.values.contains(
                MarketKey(PortfolioCodes.TSETMC, "1024"),
            ),
        )
    }

    @Test
    fun refresh_targets_every_saved_selection_and_deduplicates_shared_markets() {
        val bitcoin = MarketSelection(
            key = MarketKey(PortfolioCodes.NOBITEX, "BTCUSDT"),
            displayName = "Bitcoin",
            symbol = "BTC",
            assetType = PortfolioCodes.CRYPTO,
        )
        val ethereum = MarketSelection(
            key = MarketKey(PortfolioCodes.NOBITEX, "ETH-USDT"),
            displayName = "Ethereum",
            symbol = "ETH",
            assetType = PortfolioCodes.CRYPTO,
        )
        val firstWidget = priceWidget(15, PortfolioCodes.NOBITEX, "BTC-USDT").copy(
            markets = listOf(bitcoin, ethereum),
        )
        val secondWidget = priceWidget(16, PortfolioCodes.NOBITEX, "BTC-USDT")
        val portfolioAsset = marketAsset(
            id = 9,
            symbol = "BTC",
            provider = PortfolioCodes.NOBITEX,
            providerSymbol = "BTC-USDT",
        )

        val targets = buildMarketRefreshTargets(
            activeAssets = listOf(portfolioAsset),
            configurations = listOf(firstWidget, secondWidget),
            quoteClient = MarketQuoteClient(),
        )

        assertEquals(
            setOf(
                MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"),
                MarketKey(PortfolioCodes.NOBITEX, "ETH-USDT"),
            ),
            targets.requestedKeys,
        )
        assertEquals(
            setOf(15, 16),
            targets.widgetSelectionsByKey
                .getValue(MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"))
                .mapTo(mutableSetOf()) { it.appWidgetId },
        )
        assertEquals(
            setOf(15),
            targets.widgetSelectionsByKey
                .getValue(MarketKey(PortfolioCodes.NOBITEX, "ETH-USDT"))
                .mapTo(mutableSetOf()) { it.appWidgetId },
        )
        val secondToken = targets.widgetSelectionsByKey
            .getValue(MarketKey(PortfolioCodes.NOBITEX, "ETH-USDT"))
            .single()
        assertTrue(isCurrentWidgetSelection(secondToken, listOf(firstWidget)))
        assertFalse(isCurrentWidgetSelection(secondToken, listOf(firstWidget.copy(markets = listOf(bitcoin)))))
    }

    @Test
    fun quote_cache_keeps_the_exact_saved_market_key_after_canonical_fetching() {
        val token = WidgetSelectionToken(
            appWidgetId = 14,
            revision = 3,
            key = MarketKey(PortfolioCodes.NOBITEX, "BTCUSDT"),
        )
        val canonicalQuote = WidgetQuote(
            key = MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"),
            price = BigDecimal("100"),
            currency = PortfolioCodes.USDT,
            quoteAt = attemptedAt,
            lastSuccessAt = completedAt,
            lastAttemptAt = attemptedAt,
            status = QuoteStatus.AVAILABLE,
        )

        assertEquals(token.key, quoteForWidgetSelection(token, canonicalQuote).key)
    }

    @Test
    fun outdated_widget_configuration_token_is_not_current_after_reconfiguration() {
        val saved = priceWidget(21, PortfolioCodes.NOBITEX, "BTC-USDT", revision = 4)
        val token = WidgetSelectionToken(
            appWidgetId = saved.appWidgetId,
            revision = saved.revision,
            key = requireNotNull(saved.market).key,
        )
        val changed = saved.copy(
            revision = 5,
            markets = listOf(
                MarketSelection(
                    key = MarketKey(PortfolioCodes.NOBITEX, "ETH-USDT"),
                    displayName = "Ethereum",
                    symbol = "ETH",
                    assetType = PortfolioCodes.CRYPTO,
                ),
            ),
        )

        assertFalse(isCurrentWidgetSelection(token, listOf(changed)))
        assertTrue(isCurrentWidgetSelection(token, listOf(saved)))
    }

    @Test
    fun successful_quote_keeps_quote_time_separate_from_fetch_success_time() {
        val quoteAt = Instant.parse("2026-11-15T09:58:00Z")
        val quote = widgetQuoteForAttempt(
            key = MarketKey(PortfolioCodes.NOBITEX, "BTC-USDT"),
            result = Result.success(
                PriceResult(
                    price = BigDecimal("12345.67"),
                    currency = PortfolioCodes.USDT,
                    provider = PortfolioCodes.NOBITEX,
                    providerSymbol = "BTC-USDT",
                    fetchedAt = quoteAt,
                ),
            ),
            attemptedAt = attemptedAt,
            completedAt = completedAt,
        )

        assertEquals(BigDecimal("12345.67"), quote.price)
        assertEquals(QuoteStatus.AVAILABLE, quote.status)
        assertEquals(quoteAt, quote.quoteAt)
        assertEquals(attemptedAt, quote.lastAttemptAt)
        assertEquals(completedAt, quote.lastSuccessAt)
    }

    @Test
    fun failed_or_unsupported_quote_attempt_never_creates_a_zero_price() {
        val key = MarketKey(PortfolioCodes.TSETMC, "123456")
        val failed = widgetQuoteForAttempt(
            key = key,
            result = Result.failure(PricingException.Unavailable("offline")),
            attemptedAt = attemptedAt,
            completedAt = completedAt,
        )
        val unsupported = widgetQuoteForAttempt(
            key = key,
            result = Result.failure(PricingException.Unsupported("delisted")),
            attemptedAt = attemptedAt,
            completedAt = completedAt,
        )

        assertEquals(QuoteStatus.FAILED, failed.status)
        assertEquals(QuoteStatus.UNSUPPORTED, unsupported.status)
        assertEquals(attemptedAt, failed.lastAttemptAt)
        assertNull(failed.price)
        assertNull(failed.currency)
    }

    @Test
    fun periodic_refresh_is_needed_only_while_an_instance_is_installed() {
        assertFalse(shouldSchedulePeriodicRefresh(emptySet()))
        assertTrue(shouldSchedulePeriodicRefresh(setOf(31)))
    }

    @Test
    fun refresh_indicator_tracks_queued_running_and_terminal_work_states() {
        assertTrue(isRefreshWorkInProgress(listOf(WorkInfo.State.ENQUEUED)))
        assertTrue(isRefreshWorkInProgress(listOf(WorkInfo.State.RUNNING)))
        assertFalse(isRefreshWorkInProgress(emptyList()))
        assertFalse(isRefreshWorkInProgress(listOf(WorkInfo.State.SUCCEEDED)))
        assertFalse(isRefreshWorkInProgress(listOf(WorkInfo.State.FAILED)))
        assertFalse(isRefreshWorkInProgress(listOf(WorkInfo.State.CANCELLED)))
    }

    @Test
    fun one_widget_restore_does_not_globally_purge_the_other_kind_before_its_mapping() {
        assertFalse(requiresGlobalWidgetReconciliation(WidgetLifecycleAction.RESTORE))
        assertFalse(requiresGlobalWidgetReconciliation(WidgetLifecycleAction.DELETE))
        assertTrue(requiresGlobalWidgetReconciliation(WidgetLifecycleAction.RECONCILE))
    }

    private fun marketAsset(
        id: Long,
        symbol: String,
        provider: String,
        providerSymbol: String,
    ) = AssetEntity(
        id = id,
        name = symbol,
        symbol = symbol,
        assetType = PortfolioCodes.CRYPTO,
        pricingMode = PortfolioCodes.MARKET,
        priceProvider = provider,
        providerSymbol = providerSymbol,
    )

    private fun priceWidget(
        id: Int,
        provider: String,
        providerSymbol: String,
        revision: Long = 1,
    ) = WidgetConfiguration(
        appWidgetId = id,
        kind = WidgetKind.ASSET_PRICE,
        currency = PortfolioCodes.IRT,
        hideTotal = false,
        market = MarketSelection(
            key = MarketKey(provider, providerSymbol),
            displayName = "Market $providerSymbol",
            symbol = providerSymbol,
            assetType = PortfolioCodes.CRYPTO,
        ),
        revision = revision,
    )
}
