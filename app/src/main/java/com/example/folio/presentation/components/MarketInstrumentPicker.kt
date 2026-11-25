package com.example.folio.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.MarketSelection
import com.example.folio.presentation.design.FolioField
import com.example.folio.presentation.design.FolioListRow
import com.example.folio.presentation.design.FolioMessageKind
import com.example.folio.presentation.design.FolioSelector
import com.example.folio.presentation.design.FolioStateMessage
import com.example.folio.presentation.design.FolioFinancialValue
import java.util.UUID

/**
 * Shared market provider/search/identifier picker used by Add Asset and widget setup.
 *
 * [initialSelection] should be a saved or previously validated selection. Pass
 * [initialSelectionValidated] as false for a draft that still needs quote validation.
 */
@Composable
fun MarketInstrumentPicker(
    assetType: String,
    initialSelection: MarketSelection?,
    onSelectionChanged: (MarketSelection?) -> Unit,
    onProviderChanged: (String) -> Unit = {},
    onProviderSymbolChanged: (String) -> Unit = {},
    onManualSelectionValidated: (MarketSelection) -> Unit = { onSelectionChanged(it) },
    initialSelectionValidated: Boolean = true,
    initialProvider: String? = null,
    initialQuery: String? = null,
    initialProviderSymbol: String? = null,
    onQueryChanged: (String) -> Unit = {},
) {
    val pickerId = rememberSaveable { UUID.randomUUID().toString() }
    val pickerViewModel: MarketInstrumentPickerViewModel = viewModel(key = pickerId)
    val state by pickerViewModel.state.collectAsStateWithLifecycle()
    val currentSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val currentProviderChanged by rememberUpdatedState(onProviderChanged)
    val currentProviderSymbolChanged by rememberUpdatedState(onProviderSymbolChanged)
    val currentManualSelectionValidated by rememberUpdatedState(onManualSelectionValidated)
    val currentQueryChanged by rememberUpdatedState(onQueryChanged)

    LaunchedEffect(pickerViewModel, assetType) {
        val previousProvider = pickerViewModel.state.value.provider
        val typeChanged = pickerViewModel.initializeOrUpdate(
            assetType = assetType,
            initialSelection = initialSelection,
            initialSelectionValidated = initialSelectionValidated,
        )
        if (!typeChanged) {
            val current = pickerViewModel.state.value
            if (!initialProvider.isNullOrBlank() && current.provider != initialProvider) {
                pickerViewModel.changeProvider(initialProvider)
            }
            val afterProvider = pickerViewModel.state.value
            if (initialQuery != null && afterProvider.query != initialQuery) {
                pickerViewModel.updateQuery(initialQuery)
            }
            val afterQuery = pickerViewModel.state.value
            if (initialProviderSymbol != null && afterQuery.providerSymbol != initialProviderSymbol) {
                pickerViewModel.updateProviderSymbol(initialProviderSymbol)
            }
        }
        val selectedProvider = pickerViewModel.state.value.provider
        if (previousProvider != selectedProvider) currentProviderChanged(selectedProvider)
        if (typeChanged) {
            currentProviderSymbolChanged("")
            currentQueryChanged("")
        }
    }
    LaunchedEffect(pickerViewModel) {
        pickerViewModel.selectionChangeEvents.collect { change ->
            when {
                change.selection == null -> currentSelectionChanged(null)
                change.origin == MarketSelectionOrigin.MANUAL ->
                    currentManualSelectionValidated(requireNotNull(change.selection))
                else -> currentSelectionChanged(change.selection)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        ProviderDropdown(
            selected = state.provider,
            onSelected = { provider ->
                if (provider != state.provider) {
                    pickerViewModel.changeProvider(provider)
                    currentProviderChanged(provider)
                    currentProviderSymbolChanged("")
                    currentQueryChanged("")
                }
            },
        )
        FolioField(
            value = state.query,
            onValueChange = { query ->
                pickerViewModel.updateQuery(query)
                currentQueryChanged(query)
            },
            label = stringResource(R.string.market_search),
            placeholder = stringResource(R.string.market_search_hint),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true,
        )
        when (state.searchStatus) {
            InstrumentSearchStatus.IDLE -> FolioStateMessage(
                message = stringResource(R.string.market_search_prompt),
                kind = FolioMessageKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
            InstrumentSearchStatus.LOADING -> FolioStateMessage(
                message = stringResource(R.string.market_searching),
                kind = FolioMessageKind.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
            InstrumentSearchStatus.EMPTY -> FolioStateMessage(
                message = stringResource(R.string.market_search_empty),
                modifier = Modifier.fillMaxWidth(),
            )
            InstrumentSearchStatus.ERROR -> FolioStateMessage(
                message = stringResource(R.string.error_search),
                kind = FolioMessageKind.Error,
                modifier = Modifier.fillMaxWidth(),
            )
            InstrumentSearchStatus.RESULTS -> Unit
        }
        if (state.instruments.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
            ) {
                items(state.instruments) { instrument ->
                    FolioListRow(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pickerViewModel.selectInstrument(instrument) },
                    ) {
                        FolioFinancialValue(
                            value = instrument.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(instrument.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            instrument.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        MarketProviderIdentifierField(
            value = state.providerSymbol,
            status = state.validationStatus,
            onValueChange = { providerSymbol ->
                pickerViewModel.updateProviderSymbol(providerSymbol)
                currentProviderSymbolChanged(providerSymbol)
            },
        )
    }
}

@Composable
internal fun ProviderDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val providers = listOf(
        PortfolioCodes.NOBITEX,
        PortfolioCodes.ABANTETHER,
        PortfolioCodes.TSETMC,
        PortfolioCodes.RAHAVARD,
    )
    FolioSelector(
        options = providers,
        selected = selected,
        onSelected = onSelected,
        label = stringResource(R.string.provider),
        modifier = Modifier.padding(bottom = 8.dp),
        optionLabel = { providerLabel(it) },
    )
}

@Composable
internal fun MarketProviderIdentifierField(
    value: String,
    status: IdentifierValidationStatus,
    onValueChange: (String) -> Unit,
) {
    val identifierMessage = when (status) {
        IdentifierValidationStatus.LOADING -> stringResource(R.string.provider_identifier_checking)
        IdentifierValidationStatus.ERROR -> stringResource(R.string.provider_identifier_invalid)
        IdentifierValidationStatus.IDLE,
        IdentifierValidationStatus.VALID -> null
    }
    FolioField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.provider_symbol),
        placeholder = stringResource(R.string.provider_symbol_hint),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        supportingText = identifierMessage,
        isError = status == IdentifierValidationStatus.ERROR,
    )
}

private fun providerLabel(provider: String): String = when (provider) {
    PortfolioCodes.NOBITEX -> "Nobitex"
    PortfolioCodes.ABANTETHER -> "Aban Tether"
    PortfolioCodes.TSETMC -> "TSETMC"
    PortfolioCodes.RAHAVARD -> "Rahavard"
    else -> provider
}
