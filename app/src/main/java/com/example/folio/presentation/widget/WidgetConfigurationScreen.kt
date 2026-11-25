package com.example.folio.presentation.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Close
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
    onAddMarket: () -> Unit,
    onRemoveMarket: (com.example.folio.data.widget.MarketKey) -> Unit,
    onMoveMarket: (Int, Int) -> Unit,
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
                    onAddMarket = onAddMarket,
                    onRemoveMarket = onRemoveMarket,
                    onMoveMarket = onMoveMarket,
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
    onAddMarket: () -> Unit,
    onRemoveMarket: (com.example.folio.data.widget.MarketKey) -> Unit,
    onMoveMarket: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.widget_selected_assets),
            style = MaterialTheme.typography.titleSmall,
        )
        if (state.markets.isEmpty()) {
            FolioStateMessage(
                message = stringResource(R.string.widget_market_required),
                kind = FolioMessageKind.Empty,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.widget_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReorderableMarketList(
                markets = state.markets,
                onMove = onMoveMarket,
                onRemove = onRemoveMarket,
            )
        }

        Text(
            text = stringResource(R.string.widget_add_asset),
            style = MaterialTheme.typography.titleSmall,
        )
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
        val pending = state.market?.takeIf { state.marketValidated }
        if (pending == null) {
            FolioStateMessage(
                message = stringResource(R.string.widget_market_add_prompt),
                kind = FolioMessageKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val alreadyAdded = state.markets.any { it.key == pending.key }
            val pendingMessage = if (alreadyAdded) {
                stringResource(R.string.widget_market_duplicate, pending.displayName)
            } else {
                stringResource(
                    R.string.widget_market_selected,
                    isolateFinancialText(pending.symbol),
                    pending.displayName,
                )
            }
            FolioStateMessage(
                message = pendingMessage,
                kind = if (alreadyAdded) FolioMessageKind.Info else FolioMessageKind.Empty,
                modifier = Modifier.fillMaxWidth(),
            )
            FolioSecondaryButton(
                text = stringResource(R.string.widget_add_selected_asset),
                onClick = onAddMarket,
                enabled = !alreadyAdded,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableMarketList(
    markets: List<MarketSelection>,
    onMove: (Int, Int) -> Unit,
    onRemove: (com.example.folio.data.widget.MarketKey) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggedCenterY by remember { mutableFloatStateOf(0f) }
    val currentMarkets by rememberUpdatedState(markets)
    val currentOnMove by rememberUpdatedState(onMove)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        userScrollEnabled = draggedKey == null,
    ) {
        itemsIndexed(
            items = markets,
            key = { _, selected -> marketReorderKey(selected) },
        ) { _, selected ->
            val key = marketReorderKey(selected)
            val isDragged = draggedKey == key
            FolioListRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                        if (isDragged && itemInfo != null) {
                            translationY = draggedCenterY - (itemInfo.offset + itemInfo.size / 2f)
                            shadowElevation = 8.dp.toPx()
                        } else {
                            translationY = 0f
                            shadowElevation = 0f
                        }
                    }
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                if (info != null) {
                                    draggedKey = key
                                    draggedCenterY = info.offset + info.size / 2f
                                }
                            },
                            onDragEnd = { draggedKey = null },
                            onDragCancel = { draggedKey = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggedKey == key) {
                                    draggedCenterY += dragAmount.y
                                    val targetIndex = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info ->
                                            draggedCenterY >= info.offset &&
                                                draggedCenterY <= info.offset + info.size
                                        }
                                        ?.index
                                    val currentIndex = currentMarkets.indexOfFirst {
                                        marketReorderKey(it) == key
                                    }
                                    if (targetIndex != null && currentIndex >= 0 && targetIndex != currentIndex) {
                                        currentOnMove(currentIndex, targetIndex)
                                    }
                                }
                            },
                        )
                    },
                leading = {
                    val handleDescription = stringResource(
                        R.string.widget_reorder_asset,
                        selected.displayName,
                    )
                    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = handleDescription },
                    ) {
                        val radius = 1.5.dp.toPx()
                        val xLeft = size.width * 0.35f
                        val xRight = size.width * 0.65f
                        for (row in 0..2) {
                            val y = size.height * (0.25f + row * 0.25f)
                            drawCircle(
                                color = handleColor,
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(xLeft, y),
                            )
                            drawCircle(
                                color = handleColor,
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(xRight, y),
                            )
                        }
                    }
                },
                trailing = {
                    val removeDescription = stringResource(
                        R.string.widget_remove_asset,
                        selected.displayName,
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { onRemove(selected.key) },
                        modifier = Modifier.semantics {
                            contentDescription = removeDescription
                        },
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            R.string.widget_market_selected,
                            isolateFinancialText(selected.symbol),
                            selected.displayName,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(
                            when (selected.key.provider) {
                                PortfolioCodes.NOBITEX -> R.string.widget_asset_price_provider_nobitex
                                PortfolioCodes.ABANTETHER -> R.string.widget_asset_price_provider_abantether
                                PortfolioCodes.TSETMC -> R.string.widget_asset_price_provider_tsetmc
                                PortfolioCodes.RAHAVARD -> R.string.widget_asset_price_provider_rahavard
                                else -> R.string.widget_asset_price_provider_unknown
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun marketReorderKey(selection: MarketSelection): String =
    "${selection.key.provider}\u0000${selection.key.providerSymbol}"

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
