package com.example.folio.presentation.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetKind
import com.example.folio.presentation.components.MarketInstrumentPicker

@Composable
internal fun WidgetConfigurationScreen(
    state: WidgetConfigurationUiState,
    onCurrencyChanged: (String) -> Unit,
    onHideTotalChanged: (Boolean) -> Unit,
    onAssetTypeChanged: (String) -> Unit,
    onProviderChanged: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
    onProviderSymbolChanged: (String) -> Unit,
    onMarketSelectionChanged: (MarketSelection?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = when (state.kind) {
                WidgetKind.PORTFOLIO -> stringResource(R.string.widget_configure_portfolio_title)
                WidgetKind.ASSET_PRICE -> stringResource(R.string.widget_configure_price_title)
                null -> stringResource(R.string.widget_config_loading)
            },
            style = MaterialTheme.typography.headlineSmall,
        )

        when {
            state.isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.loadError -> Text(
                text = stringResource(R.string.widget_config_load_error),
                color = MaterialTheme.colorScheme.error,
            )

            state.kind == WidgetKind.PORTFOLIO -> PortfolioOptions(
                state = state,
                onCurrencyChanged = onCurrencyChanged,
                onHideTotalChanged = onHideTotalChanged,
            )

            state.kind == WidgetKind.ASSET_PRICE -> PriceOptions(
                state = state,
                onAssetTypeChanged = onAssetTypeChanged,
                onProviderChanged = onProviderChanged,
                onQueryChanged = onQueryChanged,
                onProviderSymbolChanged = onProviderSymbolChanged,
                onMarketSelectionChanged = onMarketSelectionChanged,
            )
        }

        if (state.saveError) {
            Text(
                text = stringResource(R.string.widget_config_save_error),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun PortfolioOptions(
    state: WidgetConfigurationUiState,
    onCurrencyChanged: (String) -> Unit,
    onHideTotalChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.widget_currency_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.currency == PortfolioCodes.IRT,
                onClick = { onCurrencyChanged(PortfolioCodes.IRT) },
                label = { Text(stringResource(R.string.widget_currency_irt)) },
            )
            FilterChip(
                selected = state.currency == PortfolioCodes.USD,
                onClick = { onCurrencyChanged(PortfolioCodes.USD) },
                label = { Text(stringResource(R.string.widget_currency_usd)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_hide_total),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.hideTotal,
                onCheckedChange = onHideTotalChanged,
            )
        }
    }
}

@Composable
private fun PriceOptions(
    state: WidgetConfigurationUiState,
    onAssetTypeChanged: (String) -> Unit,
    onProviderChanged: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
    onProviderSymbolChanged: (String) -> Unit,
    onMarketSelectionChanged: (MarketSelection?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AssetTypeSelector(
            selected = state.assetType,
            onSelected = onAssetTypeChanged,
        )
        MarketInstrumentPicker(
            assetType = state.assetType,
            initialSelection = state.market,
            initialSelectionValidated = state.marketValidated,
            initialProvider = state.provider.takeIf(String::isNotBlank),
            initialQuery = state.query,
            initialProviderSymbol = state.providerSymbol,
            onSelectionChanged = onMarketSelectionChanged,
            onProviderChanged = onProviderChanged,
            onQueryChanged = onQueryChanged,
            onProviderSymbolChanged = onProviderSymbolChanged,
            onManualSelectionValidated = onMarketSelectionChanged,
        )
        if (state.market == null) {
            Text(
                text = stringResource(R.string.widget_market_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.widget_market_selected,
                    state.market.symbol,
                    state.market.displayName,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val assetTypes = listOf(
        PortfolioCodes.FIXED_INCOME,
        PortfolioCodes.USD,
        PortfolioCodes.CRYPTO,
        PortfolioCodes.IRAN_STOCK,
        PortfolioCodes.US_STOCK,
        PortfolioCodes.GOLD,
        PortfolioCodes.SILVER,
        PortfolioCodes.MANUAL,
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = assetTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.widget_asset_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            assetTypes.forEach { assetType ->
                DropdownMenuItem(
                    text = { Text(assetTypeLabel(assetType)) },
                    onClick = {
                        expanded = false
                        if (assetType != selected) onSelected(assetType)
                    },
                )
            }
        }
    }
}

@Composable
private fun assetTypeLabel(assetType: String): String = stringResource(
    when (assetType) {
        PortfolioCodes.FIXED_INCOME -> R.string.type_fixed_income
        PortfolioCodes.USD -> R.string.type_usd
        PortfolioCodes.CRYPTO -> R.string.type_crypto
        PortfolioCodes.IRAN_STOCK -> R.string.type_iran_stock
        PortfolioCodes.US_STOCK -> R.string.type_us_stock
        PortfolioCodes.GOLD -> R.string.type_gold
        PortfolioCodes.SILVER -> R.string.type_silver
        else -> R.string.type_manual
    },
)
