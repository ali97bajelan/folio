package com.example.folio.presentation.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetKind
import com.example.folio.presentation.components.MarketInstrumentPicker
import com.example.folio.presentation.design.FolioCard
import com.example.folio.presentation.design.FolioChoiceChips
import com.example.folio.presentation.design.FolioListRow
import com.example.folio.presentation.design.FolioMessageKind
import com.example.folio.presentation.design.FolioPage
import com.example.folio.presentation.design.FolioPrimaryButton
import com.example.folio.presentation.design.FolioSecondaryButton
import com.example.folio.presentation.design.FolioSelector
import com.example.folio.presentation.design.FolioStateMessage
import com.example.folio.presentation.design.isolateFinancialText

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
    val title = when (state.kind) {
        WidgetKind.PORTFOLIO -> stringResource(R.string.widget_configure_portfolio_title)
        WidgetKind.ASSET_PRICE -> stringResource(R.string.widget_configure_price_title)
        null -> stringResource(R.string.widget_config_loading)
    }
    FolioPage(
        title = title,
        modifier = Modifier.imePadding(),
    ) {
        when {
            state.isLoading -> FolioStateMessage(
                message = stringResource(R.string.widget_config_loading),
                kind = FolioMessageKind.Loading,
                modifier = Modifier.fillMaxWidth(),
            )

            state.loadError -> FolioStateMessage(
                message = stringResource(R.string.widget_config_load_error),
                kind = FolioMessageKind.Error,
                modifier = Modifier.fillMaxWidth(),
            )

            state.kind == WidgetKind.PORTFOLIO -> FolioCard(Modifier.fillMaxWidth()) {
                PortfolioOptions(
                    state = state,
                    onCurrencyChanged = onCurrencyChanged,
                    onHideTotalChanged = onHideTotalChanged,
                )
            }

            state.kind == WidgetKind.ASSET_PRICE -> FolioCard(Modifier.fillMaxWidth()) {
                PriceOptions(
                    state = state,
                    onAssetTypeChanged = onAssetTypeChanged,
                    onProviderChanged = onProviderChanged,
                    onQueryChanged = onQueryChanged,
                    onProviderSymbolChanged = onProviderSymbolChanged,
                    onMarketSelectionChanged = onMarketSelectionChanged,
                )
            }
        }

        if (state.saveError) {
            FolioStateMessage(
                message = stringResource(R.string.widget_config_save_error),
                kind = FolioMessageKind.Error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))
        FolioPrimaryButton(
            text = stringResource(R.string.save),
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        FolioSecondaryButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PortfolioOptions(
    state: WidgetConfigurationUiState,
    onCurrencyChanged: (String) -> Unit,
    onHideTotalChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FolioChoiceChips(
            options = listOf(PortfolioCodes.IRT, PortfolioCodes.USD),
            selected = state.currency,
            onSelected = onCurrencyChanged,
            label = stringResource(R.string.widget_currency_label),
            optionLabel = { currency ->
                when (currency) {
                    PortfolioCodes.IRT -> stringResource(R.string.widget_currency_irt)
                    else -> stringResource(R.string.widget_currency_usd)
                }
            },
        )

        FolioListRow(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Text(
                    text = stringResource(R.string.widget_hide_total),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            trailing = {
                Switch(
                    checked = state.hideTotal,
                    onCheckedChange = onHideTotalChanged,
                )
            },
        )
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
            FolioStateMessage(
                message = stringResource(R.string.widget_market_required),
                kind = FolioMessageKind.Empty,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FolioStateMessage(
                message = stringResource(
                    R.string.widget_market_selected,
                    isolateFinancialText(state.market.symbol),
                    state.market.displayName,
                ),
                kind = FolioMessageKind.Empty,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AssetTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
) {
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
    FolioSelector(
        options = assetTypes,
        selected = selected,
        onSelected = { assetType ->
            if (assetType != selected) onSelected(assetType)
        },
        label = stringResource(R.string.widget_asset_type_label),
        optionLabel = { assetTypeLabel(it) },
    )
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
