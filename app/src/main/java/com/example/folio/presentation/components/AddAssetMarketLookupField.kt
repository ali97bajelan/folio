package com.example.folio.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.folio.R
import com.example.folio.data.network.Instrument
import com.example.folio.presentation.design.FolioFinancialValue

internal enum class AddAssetMarketLookupAnchor {
    NAME,
    SYMBOL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAssetMarketLookupMenu(
    state: MarketInstrumentPickerState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInstrumentSelected: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ExposedDropdownMenuBoxScope.() -> Unit,
) {
    val hasEligibleQuery = state.query.trim().length >= MIN_QUERY_LENGTH
    val menuExpanded = expanded &&
        hasEligibleQuery &&
        state.searchStatus != InstrumentSearchStatus.IDLE

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { requested ->
            onExpandedChange(requested && hasEligibleQuery)
        },
        modifier = modifier,
    ) {
        content()

        if (menuExpanded) {
            key(state.provider, state.assetType, state.query) {
                ExposedDropdownMenu(
                    expanded = true,
                    onDismissRequest = { onExpandedChange(false) },
                    scrollState = rememberScrollState(),
                    modifier = Modifier.heightIn(max = MAX_MENU_HEIGHT),
                ) {
                    when (state.searchStatus) {
                        InstrumentSearchStatus.IDLE -> Unit
                        InstrumentSearchStatus.LOADING -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.market_searching)) },
                            enabled = false,
                            onClick = {},
                        )
                        InstrumentSearchStatus.EMPTY -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.market_search_empty)) },
                            enabled = false,
                            onClick = {},
                        )
                        InstrumentSearchStatus.ERROR -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.error_search)) },
                            enabled = false,
                            onClick = {},
                        )
                        InstrumentSearchStatus.RESULTS -> state.instruments.forEach { instrument ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = RESULT_ROW_HEIGHT),
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        FolioFinancialValue(
                                            value = instrument.symbol,
                                            modifier = Modifier.widthIn(max = MAX_TICKER_WIDTH),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = instrument.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = instrument.meta,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    onExpandedChange(false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val RESULT_ROW_HEIGHT = 64.dp
private val MAX_TICKER_WIDTH = 96.dp
private const val MAX_VISIBLE_RESULTS = 3
private val MENU_VERTICAL_PADDING = 16.dp
private val MAX_MENU_HEIGHT = RESULT_ROW_HEIGHT * MAX_VISIBLE_RESULTS + MENU_VERTICAL_PADDING
private const val MIN_QUERY_LENGTH = 2
