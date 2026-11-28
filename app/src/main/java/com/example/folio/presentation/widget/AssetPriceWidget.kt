package com.example.folio.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.folio.FolioApplication
import com.example.folio.MainActivity
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.network.PriceRefreshWorker
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import com.example.folio.presentation.ThemePreferences
import com.example.folio.presentation.design.isolateFinancialText
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

private const val ASSET_PRICE_ROW_HEIGHT_DP = 72
private const val ASSET_PRICE_EXPANDED_ROW_MAX_HEIGHT_DP = 88
private const val ASSET_PRICE_WIDE_ROW_MIN_WIDTH_DP = 360

internal data class AssetPriceViewport(
    val visibleRows: Int,
    val heightDp: Int,
)

internal fun calculateAssetPriceViewport(
    rowCount: Int,
    widgetHeightDp: Float,
    compact: Boolean,
): AssetPriceViewport {
    if (rowCount <= 0) return AssetPriceViewport(visibleRows = 0, heightDp = 0)
    val fixedHeaderHeight = if (compact) 68f else 72f
    val verticalInsets = if (compact) 8f else FolioWidgetSpacing.compactContentVertical.value
    val availableHeight = (widgetHeightDp - fixedHeaderHeight - verticalInsets)
        .coerceAtLeast(ASSET_PRICE_ROW_HEIGHT_DP.toFloat())
    val fittingRows = (availableHeight / ASSET_PRICE_ROW_HEIGHT_DP).toInt().coerceAtLeast(1)
    val visibleRows = minOf(rowCount, fittingRows)
    return AssetPriceViewport(
        visibleRows = visibleRows,
        heightDp = visibleRows * ASSET_PRICE_ROW_HEIGHT_DP,
    )
}

internal fun calculateAssetPriceRowHeight(
    rowCount: Int,
    widgetHeightDp: Float,
    compact: Boolean,
): Int {
    if (rowCount <= 1) return ASSET_PRICE_ROW_HEIGHT_DP
    val fixedHeaderHeight = if (compact) 68f else 72f
    val verticalInsets = if (compact) 8f else FolioWidgetSpacing.compactContentVertical.value
    val availableHeight = (widgetHeightDp - fixedHeaderHeight - verticalInsets)
        .coerceAtLeast(ASSET_PRICE_ROW_HEIGHT_DP.toFloat())
    return (availableHeight / rowCount)
        .toInt()
        .coerceIn(ASSET_PRICE_ROW_HEIGHT_DP, ASSET_PRICE_EXPANDED_ROW_MAX_HEIGHT_DP)
}

class AssetPriceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? FolioApplication
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialState = loadAssetPriceWidgetRenderState(app, appWidgetId)

        provideContent {
            val revision by WidgetUpdateCoordinator.observeRequestedRevision(context)
                .collectAsState(initial = 0L)
            var state by remember(appWidgetId) { mutableStateOf(initialState) }
            LaunchedEffect(revision) {
                state = loadAssetPriceWidgetRenderState(app, appWidgetId)
            }
            val localizedContext = remember(revision) { widgetLocalizedContext(context) }
            val isUpdating by PriceRefreshWorker.observeRefreshInProgress(context)
                .collectAsState(initial = false)

            AssetPriceWidgetLayout(
                context = localizedContext,
                appWidgetId = appWidgetId,
                configuration = state.data?.configuration,
                quotes = state.data?.quotes.orEmpty(),
                isUpdating = isUpdating,
                loadFailed = state.loadFailed,
            )
        }
    }
}

@Composable
internal fun AssetPriceWidgetLayout(
    context: Context,
    appWidgetId: Int,
    configuration: WidgetConfiguration?,
    quotes: Map<MarketKey, WidgetQuote>,
    isUpdating: Boolean,
    loadFailed: Boolean,
) {
    val size = LocalSize.current
    val compact = !(size.width >= 280.dp && size.height >= 220.dp)
    val language = com.example.folio.presentation.LanguagePreferences(context).current()
    val direction = if (language == com.example.folio.presentation.AppLanguage.PERSIAN) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val title = context.getString(
        if (size.width >= 320.dp) R.string.widget_market_watch_title
        else R.string.widget_market_watch_compact_title,
    )
    val now = Instant.now()
    val quoteAge = configuration?.markets.orEmpty()
        .mapNotNull { selection ->
            quotes[selection.key]
                ?.takeIf { it.key == selection.key }
                ?.let { presentAssetPriceQuote(it, isUpdating, now).quoteAt }
        }
        .maxOrNull()
        ?.let { formatAssetPriceQuoteAge(context, it, now) }
    val headerSubtitle = quoteAge?.let {
        context.getString(R.string.widget_market_watch_updated, it)
    }
    val configureAction = configureWidgetAction(appWidgetId)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FolioWidgetTheme(theme = ThemePreferences(context).current()) {
            Scaffold(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp),
                backgroundColor = GlanceTheme.colors.surface,
                horizontalPadding = 12.dp,
                titleBar = {
                    MarketWatchHeader(
                        context = context,
                        appWidgetId = appWidgetId,
                        title = title,
                        subtitle = headerSubtitle.takeIf { size.width >= 360.dp },
                        compact = compact,
                    )
                },
            ) {
                AssetPriceWidgetBody(
                    context = context,
                    configuration = configuration,
                    quotes = quotes,
                    isUpdating = isUpdating,
                    loadFailed = loadFailed,
                    configureAction = configureAction,
                    compact = compact,
                    totalHeightDp = size.height.value,
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MarketWatchHeader(
    context: Context,
    appWidgetId: Int,
    title: String,
    subtitle: String?,
    compact: Boolean,
) {
    val openDashboard = actionStartActivity<MainActivity>()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = if (compact) 4.dp else 8.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .size(FolioWidgetSpacing.actionTarget)
                        .clickable(openDashboard),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = context.getString(R.string.nav_dashboard),
                        modifier = GlanceModifier.size(40.dp),
                    )
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = if (compact) 12.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = if (compact) 9.sp else 10.sp,
                            ),
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
                MarketWatchActionButton(
                    icon = R.drawable.widget_refresh,
                    description = context.getString(R.string.widget_asset_price_refresh),
                    action = refreshWidgetAction(appWidgetId),
                    highlighted = true,
                )
                MarketWatchActionButton(
                    icon = R.drawable.widget_configure,
                    description = context.getString(R.string.widget_asset_price_configure),
                    action = configureWidgetAction(appWidgetId),
                    highlighted = false,
                )
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LocalFolioWidgetSemanticColors.current.border),
            ) {}
        }
    }
}

@Composable
private fun MarketWatchActionButton(
    icon: Int,
    description: String,
    action: Action,
    highlighted: Boolean,
) {
    val semanticColors = LocalFolioWidgetSemanticColors.current
    val backgroundColor = if (highlighted) semanticColors.gainContainer else GlanceTheme.colors.surfaceVariant
    val iconColor = if (highlighted) semanticColors.gain else GlanceTheme.colors.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(FolioWidgetSpacing.actionTarget)
            .semantics { contentDescription = description }
            .clickable(action),
    ) {
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(backgroundColor)
                .cornerRadius(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconColor),
                modifier = GlanceModifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AssetPriceWidgetBody(
    context: Context,
    configuration: WidgetConfiguration?,
    quotes: Map<MarketKey, WidgetQuote>,
    isUpdating: Boolean,
    loadFailed: Boolean,
    configureAction: Action,
    compact: Boolean,
    totalHeightDp: Float,
    modifier: GlanceModifier,
) {
    val markets = configuration?.markets.orEmpty()
    if (markets.isEmpty()) {
        Column(
            modifier = modifier
                .clickable(configureAction)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = context.getString(
                    if (loadFailed) R.string.widget_asset_price_load_error
                    else R.string.widget_asset_price_setup,
                ),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (!loadFailed) {
                Text(
                    text = context.getString(R.string.widget_asset_price_setup_hint),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
        return
    }

    val viewport = calculateAssetPriceViewport(markets.size, totalHeightDp, compact)
    val rowHeightDp = if (markets.size <= viewport.visibleRows) {
        calculateAssetPriceRowHeight(markets.size, totalHeightDp, compact)
    } else {
        ASSET_PRICE_ROW_HEIGHT_DP
    }
    val listHeightDp = if (markets.size <= viewport.visibleRows) {
        rowHeightDp * markets.size
    } else {
        viewport.heightDp
    }
    val now = Instant.now()
    val displayRows = assetPriceWidgetRows(markets, quotes).map { row ->
        val presentation = presentAssetPriceQuote(row.quote, isUpdating, now)
        AssetPriceWidgetDisplayRow(
            row = row,
            presentation = presentation,
            quoteAge = formatAssetPriceQuoteAge(context, presentation.quoteAt, now),
            changeText = formatAssetPriceChange(context, presentation.changePercent),
        )
    }
    val sharedQuoteAt = displayRows.mapNotNull { it.presentation.quoteAt }.maxOrNull()
    val sharedQuoteAge = formatAssetPriceQuoteAge(context, sharedQuoteAt, now)
    val rowsWithIndividualAges = displayRows.map { displayRow ->
        displayRow.copy(
            quoteAge = displayRow.quoteAge.takeIf {
                it != null && it != sharedQuoteAge
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(listHeightDp.dp),
    ) {
        items(
            items = rowsWithIndividualAges,
            itemId = { displayRow -> displayRow.row.stableId },
        ) { displayRow ->
            AssetPriceQuoteRow(
                context = context,
                displayRow = displayRow,
                loadFailed = loadFailed,
                compact = compact,
                rowHeightDp = rowHeightDp,
            )
        }
    }
}

internal data class AssetPriceWidgetRow(
    val selection: MarketSelection,
    val quote: WidgetQuote?,
) {
    val stableId: Long
        get() = if (selection.selectionId > 0L) {
            selection.selectionId
        } else {
            val hash = 31L * selection.key.provider.hashCode() + selection.key.providerSymbol.hashCode()
            (hash and Long.MAX_VALUE).coerceAtLeast(1L)
        }
}

internal fun assetPriceWidgetRows(
    markets: List<MarketSelection>,
    quotes: Map<MarketKey, WidgetQuote>,
): List<AssetPriceWidgetRow> = markets.map { selection ->
    AssetPriceWidgetRow(
        selection = selection,
        quote = quotes[selection.key]?.takeIf { it.key == selection.key },
    )
}

private data class AssetPriceWidgetDisplayRow(
    val row: AssetPriceWidgetRow,
    val presentation: AssetPriceWidgetPresentation,
    val quoteAge: String?,
    val changeText: String?,
)

@Composable
private fun AssetPriceQuoteRow(
    context: Context,
    displayRow: AssetPriceWidgetDisplayRow,
    loadFailed: Boolean,
    compact: Boolean,
    rowHeightDp: Int,
) {
    val selection = displayRow.row.selection
    val presentation = displayRow.presentation
    val provider = assetPriceProviderLabel(context, selection.key.provider)
    val symbol = selection.symbol.trim().ifBlank { selection.key.providerSymbol }
    val displayName = selection.displayName.trim().takeUnless {
        it.equals(symbol, ignoreCase = true)
    }
    val amount = presentation.price?.let { price ->
        formatAssetPriceAmount(
            price = price,
            currency = requireNotNull(presentation.currency),
            tomanCurrencyLabel = context.getString(R.string.widget_asset_price_currency_irt),
        )
    }
    val amountParts = amount?.let { formatted ->
        val separator = formatted.lastIndexOf(' ')
        if (separator > 0) formatted.substring(0, separator) to formatted.substring(separator + 1)
        else formatted to null
    }
    val priceValue = amountParts?.first ?: context.getString(R.string.widget_asset_price_unavailable)
    val currency = amountParts?.second
    val status = assetPriceQuoteStatus(context, presentation, loadFailed)
    val cachedAge = displayRow.quoteAge?.let {
        context.getString(R.string.widget_market_watch_cached_age, it)
    }
    val stateText = listOf(status.takeIf(String::isNotBlank), cachedAge)
        .filterNotNull()
        .joinToString(" · ")
        .ifBlank { null }
    val isWarning = loadFailed || presentation.refreshFailed || presentation.unsupported ||
        presentation.freshness == AssetPriceQuoteFreshness.STALE
    val compactMeta = if (isWarning && status.isNotBlank()) status else provider
    val widgetWidth = LocalSize.current.width
    val wide = widgetWidth >= ASSET_PRICE_WIDE_ROW_MIN_WIDTH_DP.dp
    val accessibilityTitle = listOfNotNull(symbol, displayName).joinToString(" · ")
    val rowDescription = listOfNotNull(
        accessibilityTitle,
        provider,
        stateText,
        amount ?: context.getString(R.string.widget_asset_price_unavailable),
        displayRow.changeText,
    ).joinToString(". ")
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.surfaceVariant)
        .cornerRadius(18.dp)
        .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp)
        .semantics { contentDescription = rowDescription }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeightDp.dp)
            .padding(bottom = 4.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (wide) {
                Row(
                    modifier = cardModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarketWatchBadge(selection = selection, compact = false)
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        MarketWatchIdentity(symbol, displayName, compact = false)
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        MarketWatchProviderState(
                            provider = provider,
                            stateText = stateText,
                            highlighted = stateText != null,
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        MarketWatchPrice(priceValue, currency, compact = false)
                        displayRow.changeText?.let { changeText ->
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            MarketWatchChangeChip(
                                text = changeText,
                                change = presentation.changePercent,
                                compact = false,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = cardModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarketWatchBadge(selection = selection, compact = true)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        MarketWatchIdentity(symbol, displayName, compact = true)
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        MarketWatchPrice(priceValue, currency, compact = true)
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = compactMeta,
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                                style = TextStyle(
                                    color = if (isWarning) LocalFolioWidgetSemanticColors.current.warning
                                    else GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 10.sp,
                                ),
                            )
                            displayRow.changeText?.let { changeText ->
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                MarketWatchChangeChip(
                                    text = changeText,
                                    change = presentation.changePercent,
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun MarketWatchBadge(
    selection: MarketSelection,
    compact: Boolean,
) {
    val symbol = selection.symbol.uppercase(Locale.ROOT)
    val semanticColors = LocalFolioWidgetSemanticColors.current
    val (glyph, backgroundColor, foregroundColor) = when {
        symbol.contains("BTC") -> Triple(
            "₿",
            semanticColors.warningContainer,
            semanticColors.warning,
        )
        symbol.contains("USDT") -> Triple(
            "₮",
            semanticColors.gainContainer,
            semanticColors.gain,
        )
        selection.assetType == PortfolioCodes.USD -> Triple(
            "\$",
            GlanceTheme.colors.tertiaryContainer,
            GlanceTheme.colors.onTertiaryContainer,
        )
        selection.assetType == PortfolioCodes.GOLD -> Triple(
            "✦",
            semanticColors.warningContainer,
            semanticColors.warning,
        )
        selection.assetType == PortfolioCodes.SILVER -> Triple(
            "◇",
            GlanceTheme.colors.surfaceVariant,
            GlanceTheme.colors.onSurfaceVariant,
        )
        else -> Triple(
            selection.symbol.trim().firstOrNull()?.uppercase() ?: "•",
            GlanceTheme.colors.surfaceVariant,
            GlanceTheme.colors.onSurfaceVariant,
        )
    }
    Box(
        modifier = GlanceModifier
            .size(if (compact) 30.dp else 36.dp)
            .background(backgroundColor)
            .cornerRadius(if (compact) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = foregroundColor,
                fontSize = if (compact) 14.sp else 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun MarketWatchIdentity(
    symbol: String,
    displayName: String?,
    compact: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = isolateFinancialText(symbol),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        displayName?.let { name ->
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = name,
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 10.sp else 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun MarketWatchProviderState(
    provider: String,
    stateText: String?,
    highlighted: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = provider,
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
        )
        stateText?.let { text ->
            Spacer(modifier = GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .background(
                        if (highlighted) LocalFolioWidgetSemanticColors.current.warningContainer
                        else GlanceTheme.colors.surface,
                    )
                    .cornerRadius(6.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (highlighted) LocalFolioWidgetSemanticColors.current.warning
                        else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MarketWatchPrice(
    priceValue: String,
    currency: String?,
    compact: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = isolateFinancialText(priceValue),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 16.sp else 19.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        currency?.let {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = isolateFinancialText(it),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun MarketWatchChangeChip(
    text: String,
    change: java.math.BigDecimal?,
    compact: Boolean,
) {
    val amount = change ?: java.math.BigDecimal.ZERO
    val semanticColors = LocalFolioWidgetSemanticColors.current
    val foreground = when {
        amount.signum() > 0 -> semanticColors.gain
        amount.signum() < 0 -> semanticColors.loss
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    val backgroundColor = when {
        amount.signum() > 0 -> semanticColors.gainContainer
        amount.signum() < 0 -> semanticColors.lossContainer
        else -> GlanceTheme.colors.surface
    }
    Box(
        modifier = GlanceModifier
            .background(backgroundColor)
            .cornerRadius(7.dp)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = foreground,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private fun formatAssetPriceChange(context: Context, change: java.math.BigDecimal?): String? {
    if (change == null) return null
    val formatter = java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val sign = if (change.signum() > 0) "+" else ""
    return context.getString(
        R.string.widget_asset_price_change_percent,
        "$sign${formatter.format(change)}",
    )
}

private fun assetPriceQuoteStatus(
    context: Context,
    presentation: AssetPriceWidgetPresentation,
    loadFailed: Boolean,
): String = when {
    loadFailed -> context.getString(R.string.widget_asset_price_load_error)
    presentation.unsupported -> context.getString(R.string.widget_asset_price_unsupported)
    presentation.refreshFailed && presentation.price == null ->
        context.getString(R.string.widget_asset_price_refresh_failed_unavailable)
    presentation.refreshFailed -> context.getString(R.string.widget_asset_price_refresh_failed)
    presentation.isUpdating -> context.getString(R.string.widget_asset_price_updating)
    presentation.freshness == AssetPriceQuoteFreshness.STALE ->
        context.getString(R.string.widget_asset_price_stale)
    else -> ""
}

private data class LoadedAssetPriceWidgetState(
    val configuration: WidgetConfiguration?,
    val quotes: Map<MarketKey, WidgetQuote>,
)

private data class AssetPriceWidgetRenderState(
    val data: LoadedAssetPriceWidgetState?,
    val loadFailed: Boolean,
)

private suspend fun loadAssetPriceWidgetRenderState(
    app: FolioApplication?,
    appWidgetId: Int,
): AssetPriceWidgetRenderState {
    if (app == null) return AssetPriceWidgetRenderState(data = null, loadFailed = true)
    return try {
        AssetPriceWidgetRenderState(
            data = loadAssetPriceWidgetState(app, appWidgetId),
            loadFailed = false,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        AssetPriceWidgetRenderState(data = null, loadFailed = true)
    }
}

private suspend fun loadAssetPriceWidgetState(
    app: FolioApplication,
    appWidgetId: Int,
): LoadedAssetPriceWidgetState {
    repeat(2) {
        val loaded = app.widgetRepository.configuration(appWidgetId)
            ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
        val quotes = loaded?.markets.orEmpty().mapNotNull { selection ->
            val quote = try {
                app.widgetRepository.quote(selection.key)?.takeIf { it.key == selection.key }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            quote?.let { selection.key to it }
        }.toMap()
        val latest = app.widgetRepository.configuration(appWidgetId)
            ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
        if (loaded == null && latest == null) {
            return LoadedAssetPriceWidgetState(configuration = null, quotes = emptyMap())
        }
        if (isCurrentAssetPriceConfiguration(loaded, latest)) {
            return LoadedAssetPriceWidgetState(configuration = loaded, quotes = quotes)
        }
    }

    val latest = app.widgetRepository.configuration(appWidgetId)
        ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
    return LoadedAssetPriceWidgetState(configuration = latest, quotes = emptyMap())
}

private fun assetPriceProviderLabel(context: Context, provider: String): String =
    context.getString(
        when (provider) {
            PortfolioCodes.NOBITEX -> R.string.widget_asset_price_provider_nobitex
            PortfolioCodes.ABANTETHER -> R.string.widget_asset_price_provider_abantether
            PortfolioCodes.TSETMC -> R.string.widget_asset_price_provider_tsetmc
            PortfolioCodes.RAHAVARD -> R.string.widget_asset_price_provider_rahavard
            else -> R.string.widget_asset_price_provider_unknown
        },
    )

private fun formatAssetPriceQuoteAge(
    context: Context,
    quotedAt: Instant?,
    now: Instant,
): String? {
    if (quotedAt == null) return null
    val elapsed = Duration.between(quotedAt, now).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < Duration.ofMinutes(1) -> context.getString(R.string.last_updated_just_now)
        elapsed < Duration.ofHours(1) -> context.resources.getQuantityString(
            R.plurals.last_updated_minutes_ago,
            elapsed.toMinutes().toInt(),
            elapsed.toMinutes().toInt(),
        )
        elapsed < Duration.ofDays(1) -> context.resources.getQuantityString(
            R.plurals.last_updated_hours_ago,
            elapsed.toHours().toInt(),
            elapsed.toHours().toInt(),
        )
        elapsed < Duration.ofDays(3) -> context.resources.getQuantityString(
            R.plurals.last_updated_days_ago,
            elapsed.toDays().toInt(),
            elapsed.toDays().toInt(),
        )
        else -> assetPriceDateFormat.format(quotedAt)
    }
}

private val assetPriceDateFormat = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    .withZone(ZoneId.systemDefault())
