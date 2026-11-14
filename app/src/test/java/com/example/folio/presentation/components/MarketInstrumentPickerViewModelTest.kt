package com.example.folio.presentation.components

import com.example.folio.PortfolioCodes
import com.example.folio.data.network.Instrument
import com.example.folio.data.network.MarketQuoteClient
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MarketInstrumentPickerViewModelTest {
    @Test
    fun late_query_result_does_not_replace_newer_results() {
        val pending = PendingSearches()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = pickerViewModel(
            scope = scope,
            search = { _, query, _ -> pending.search(query) },
        )

        try {
            viewModel.initializeOrUpdate(PortfolioCodes.CRYPTO, null)
            viewModel.updateQuery("bitcoin")
            viewModel.updateQuery("ethereum")
            assertEquals(InstrumentSearchStatus.LOADING, viewModel.state.value.searchStatus)

            pending.complete("bitcoin", listOf(instrument("OLD", "Old result")))
            assertTrue(viewModel.state.value.instruments.isEmpty())
            assertEquals(InstrumentSearchStatus.LOADING, viewModel.state.value.searchStatus)

            val current = instrument("ETH", "Ethereum")
            pending.complete("ethereum", listOf(current))

            assertEquals(listOf(current), viewModel.state.value.instruments)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun late_search_result_is_ignored_after_asset_type_change() {
        val pending = PendingSearches()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = pickerViewModel(
            scope = scope,
            search = { _, query, _ -> pending.search(query) },
        )

        try {
            viewModel.initializeOrUpdate(PortfolioCodes.CRYPTO, null)
            viewModel.updateQuery("bitcoin")
            viewModel.updateAssetType(PortfolioCodes.IRAN_STOCK)
            pending.complete("bitcoin", listOf(instrument("OLD", "Old result")))

            assertEquals(PortfolioCodes.TSETMC, viewModel.state.value.provider)
            assertEquals(PortfolioCodes.IRAN_STOCK, viewModel.state.value.assetType)
            assertEquals(InstrumentSearchStatus.IDLE, viewModel.state.value.searchStatus)
            assertTrue(viewModel.state.value.instruments.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun late_identifier_validation_does_not_publish_after_identifier_edit() {
        val pending = PendingValidations()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = pickerViewModel(
            scope = scope,
            validate = pending::validate,
        )

        try {
            viewModel.initializeOrUpdate(PortfolioCodes.IRAN_STOCK, null)
            viewModel.changeProvider(PortfolioCodes.TSETMC)
            viewModel.updateProviderSymbol("0001")
            viewModel.updateProviderSymbol("0002")

            pending.complete("0001")
            assertNull(viewModel.state.value.selection)
            assertEquals("0002", viewModel.state.value.providerSymbol)

            pending.complete("0002")
            assertEquals("0002", viewModel.state.value.selection?.key?.providerSymbol)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun previously_validated_initial_selection_survives_offline_recreation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val selection = MarketSelection(
            key = MarketKey(PortfolioCodes.TSETMC, "0001"),
            displayName = "Example",
            symbol = "EX",
            assetType = PortfolioCodes.IRAN_STOCK,
        )
        val viewModel = pickerViewModel(
            scope = scope,
            validate = { error("A saved selection must remain usable offline.") },
        )

        try {
            viewModel.initializeOrUpdate(PortfolioCodes.IRAN_STOCK, selection)

            assertEquals(selection.key, viewModel.state.value.selection?.key)
            assertEquals(IdentifierValidationStatus.VALID, viewModel.state.value.validationStatus)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unvalidated_initial_selection_is_not_save_ready_until_fetched() {
        val pending = PendingValidations()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = pickerViewModel(
            scope = scope,
            validate = pending::validate,
        )
        val draft = MarketSelection(
            key = MarketKey(PortfolioCodes.TSETMC, "0001"),
            displayName = "Example",
            symbol = "EX",
            assetType = PortfolioCodes.IRAN_STOCK,
        )

        try {
            viewModel.initializeOrUpdate(
                assetType = PortfolioCodes.IRAN_STOCK,
                initialSelection = draft,
                initialSelectionValidated = false,
            )

            assertNull(viewModel.state.value.selection)
            pending.complete("0001")
            assertEquals(draft.key, viewModel.state.value.selection?.key)
        } finally {
            scope.cancel()
        }
    }

    private fun pickerViewModel(
        scope: CoroutineScope,
        search: suspend (provider: String, query: String, assetType: String) -> List<Instrument> = { _, _, _ -> emptyList() },
        validate: suspend (MarketKey) -> Unit = {},
    ) = MarketInstrumentPickerViewModel(
        searchInstruments = search,
        quoteClient = MarketQuoteClient { _, _ -> error("Network is not used by this test.") },
        validateMarketOverride = validate,
        coroutineScopeOverride = scope,
        awaitDebounce = {},
    )

    private fun instrument(providerSymbol: String, name: String) = Instrument(
        providerSymbol = providerSymbol,
        assetSymbol = providerSymbol,
        symbol = providerSymbol,
        name = name,
        meta = "Fixture",
    )

    private class PendingSearches {
        private val requests = mutableMapOf<String, Continuation<List<Instrument>>>()

        // This fixture models a provider that can finish after its caller is cancelled.
        suspend fun search(query: String): List<Instrument> {
            var result: List<Instrument>? = null
            try {
                withContext(NonCancellable) {
                    result = suspendCoroutine { continuation -> requests[query] = continuation }
                }
            } catch (cancellation: CancellationException) {
                if (result == null) throw cancellation
            }
            return requireNotNull(result)
        }

        fun complete(query: String, result: List<Instrument>) {
            requests.getValue(query).resume(result)
        }
    }

    private class PendingValidations {
        private val requests = mutableMapOf<String, Continuation<Unit>>()

        // This fixture models a quote response that arrives after an identifier edit.
        suspend fun validate(key: MarketKey) {
            var completed = false
            try {
                withContext(NonCancellable) {
                    suspendCoroutine<Unit> { continuation ->
                        requests[key.providerSymbol] = continuation
                    }
                    completed = true
                }
            } catch (cancellation: CancellationException) {
                if (!completed) throw cancellation
            }
        }

        fun complete(providerSymbol: String) {
            requests.getValue(providerSymbol).resume(Unit)
        }
    }
}
