package com.example.folio.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.MarketSelection
import java.util.UUID
import kotlinx.coroutines.flow.collect

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
) {
    val pickerId = rememberSaveable { UUID.randomUUID().toString() }
    val pickerViewModel: MarketInstrumentPickerViewModel = viewModel(key = pickerId)
    val state by pickerViewModel.state.collectAsStateWithLifecycle()
    val currentSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val currentProviderChanged by rememberUpdatedState(onProviderChanged)
    val currentProviderSymbolChanged by rememberUpdatedState(onProviderSymbolChanged)
    val currentManualSelectionValidated by rememberUpdatedState(onManualSelectionValidated)

    LaunchedEffect(pickerViewModel, assetType) {
        val previousProvider = pickerViewModel.state.value.provider
        val typeChanged = pickerViewModel.initializeOrUpdate(
            assetType = assetType,
            initialSelection = initialSelection,
            initialSelectionValidated = initialSelectionValidated,
        )
        val selectedProvider = pickerViewModel.state.value.provider
        if (previousProvider != selectedProvider) currentProviderChanged(selectedProvider)
        if (typeChanged) currentProviderSymbolChanged("")
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
                }
            },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = pickerViewModel::updateQuery,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            label = { Text(stringResource(R.string.market_search)) },
            placeholder = { Text(stringResource(R.string.market_search_hint)) },
            singleLine = true,
        )
        when (state.searchStatus) {
            InstrumentSearchStatus.IDLE ->
                Text(stringResource(R.string.market_search_prompt), style = MaterialTheme.typography.bodySmall)
            InstrumentSearchStatus.LOADING ->
                Text(stringResource(R.string.market_searching), style = MaterialTheme.typography.bodySmall)
            InstrumentSearchStatus.EMPTY ->
                Text(stringResource(R.string.market_search_empty), style = MaterialTheme.typography.bodySmall)
            InstrumentSearchStatus.ERROR ->
                Text(
                    stringResource(R.string.error_search),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            InstrumentSearchStatus.RESULTS -> Unit
        }
        state.instruments.forEach { instrument ->
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable {
                    pickerViewModel.selectInstrument(instrument)
                },
                headlineContent = { Text("${instrument.symbol} · ${instrument.name}") },
                supportingContent = { Text(instrument.meta) },
            )
            HorizontalDivider()
        }
        OutlinedTextField(
            value = state.providerSymbol,
            onValueChange = { providerSymbol ->
                pickerViewModel.updateProviderSymbol(providerSymbol)
                currentProviderSymbolChanged(providerSymbol)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(stringResource(R.string.provider_symbol)) },
            placeholder = { Text(stringResource(R.string.provider_symbol_hint)) },
            singleLine = true,
            supportingText = {
                when (state.validationStatus) {
                    IdentifierValidationStatus.IDLE -> Unit
                    IdentifierValidationStatus.LOADING ->
                        Text(stringResource(R.string.provider_identifier_checking))
                    IdentifierValidationStatus.VALID -> Unit
                    IdentifierValidationStatus.ERROR ->
                        Text(
                            stringResource(R.string.provider_identifier_invalid),
                            color = MaterialTheme.colorScheme.error,
                        )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val providers = listOf(
        PortfolioCodes.NOBITEX,
        PortfolioCodes.ABANTETHER,
        PortfolioCodes.TSETMC,
        PortfolioCodes.RAHAVARD,
    )
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        OutlinedTextField(
            value = providerLabel(selected),
            onValueChange = {},
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            readOnly = true,
            label = { Text(stringResource(R.string.provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(providerLabel(provider)) },
                    onClick = {
                        expanded = false
                        onSelected(provider)
                    },
                )
            }
        }
    }
}

private fun providerLabel(provider: String): String = when (provider) {
    PortfolioCodes.NOBITEX -> "Nobitex"
    PortfolioCodes.ABANTETHER -> "Aban Tether"
    PortfolioCodes.TSETMC -> "TSETMC"
    PortfolioCodes.RAHAVARD -> "Rahavard"
    else -> provider
}
