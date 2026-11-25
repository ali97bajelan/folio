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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.folio.FolioApplication
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.network.PriceRefreshWorker
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import com.example.folio.presentation.design.isolateFinancialText
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

private const val ASSET_PRICE_ROW_HEIGHT_DP = 72
private const val ASSET_PRICE_MAX_VISIBLE_ROWS = 3

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
    val fixedHeaderHeight = if (compact) 44f else 56f
    val verticalInsets = if (compact) 16f else 8f
    val availableHeight = (widgetHeightDp - fixedHeaderHeight - verticalInsets)
        .coerceAtLeast(ASSET_PRICE_ROW_HEIGHT_DP.toFloat())
    val fittingRows = (availableHeight / ASSET_PRICE_ROW_HEIGHT_DP).toInt().coerceAtLeast(1)
    val visibleRows = minOf(rowCount, fittingRows, ASSET_PRICE_MAX_VISIBLE_ROWS)
    return AssetPriceViewport(
        visibleRows = visibleRows,
        heightDp = visibleRows * ASSET_PRICE_ROW_HEIGHT_DP,
    )
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
    val title = context.getString(R.string.widget_asset_price_name)
    val configureAction = configureWidgetAction(appWidgetId)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FolioWidgetTheme {
            if (!compact) {
                Scaffold(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface),
                    horizontalPadding = 8.dp,
                    titleBar = {
                        TitleBar(
                            startIcon = ImageProvider(R.mipmap.ic_launcher),
                            title = title,
                            iconColor = GlanceTheme.colors.primary,
                            actions = { AssetPriceWidgetActionButtons(context, appWidgetId) },
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
                        compact = false,
                        totalHeightDp = size.height.value,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        AssetPriceWidgetActionButtons(context, appWidgetId)
                    }
                    AssetPriceWidgetBody(
                        context = context,
                        configuration = configuration,
                        quotes = quotes,
                        isUpdating = isUpdating,
                        loadFailed = loadFailed,
                        configureAction = configureAction,
                        compact = true,
                        totalHeightDp = size.height.value,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetPriceWidgetActionButtons(
    context: Context,
    appWidgetId: Int,
) {
    WidgetActionButton(
        icon = R.drawable.widget_refresh,
        description = context.getString(R.string.widget_asset_price_refresh),
        action = refreshWidgetAction(appWidgetId),
    )
    WidgetActionButton(
        icon = R.drawable.widget_configure,
        description = context.getString(R.string.widget_asset_price_configure),
        action = configureWidgetAction(appWidgetId),
    )
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
    val rows = assetPriceWidgetRows(markets, quotes)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(viewport.heightDp.dp),
    ) {
        items(
            items = rows,
            itemId = { row -> row.stableId },
        ) { row ->
            AssetPriceQuoteRow(
                context = context,
                row = row,
                isUpdating = isUpdating,
                loadFailed = loadFailed,
                compact = compact,
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

@Composable
private fun AssetPriceQuoteRow(
    context: Context,
    row: AssetPriceWidgetRow,
    isUpdating: Boolean,
    loadFailed: Boolean,
    compact: Boolean,
) {
    val selection = row.selection
    val presentation = presentAssetPriceQuote(
        quote = row.quote,
        isUpdating = isUpdating,
        now = Instant.now(),
    )
    val provider = assetPriceProviderLabel(context, selection.key.provider)
    val title = context.getString(
        R.string.widget_asset_price_row_title,
        provider,
        isolateFinancialText(selection.symbol),
        selection.displayName,
    )
    val titleDescription = context.getString(
        R.string.widget_asset_price_row_title,
        provider,
        selection.symbol,
        selection.displayName,
    )
    val amount = presentation.price?.let { price ->
        formatAssetPriceAmount(
            price = price,
            currency = requireNotNull(presentation.currency),
            tomanCurrencyLabel = context.getString(R.string.widget_asset_price_currency_irt),
        )
    }
    val priceText = amount?.let(::isolateFinancialText)
        ?: context.getString(R.string.widget_asset_price_unavailable)
    val quoteAge = formatAssetPriceQuoteAge(context, presentation.quoteAt, Instant.now())
    val metadata = listOf(
        assetPriceQuoteStatus(context, presentation, loadFailed),
        quoteAge?.let { context.getString(R.string.widget_asset_price_quote_age, it) },
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
    val rowDescription = listOf(titleDescription, amount ?: priceText, metadata)
        .filter(String::isNotBlank)
        .joinToString(". ")

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ASSET_PRICE_ROW_HEIGHT_DP.dp)
            .padding(vertical = 3.dp)
            .semantics { contentDescription = rowDescription },
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = priceText,
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                modifier = GlanceModifier.fillMaxWidth(),
                maxLines = 1,
                style = TextStyle(
                    color = if (presentation.refreshFailed || presentation.unsupported) {
                        LocalFolioWidgetSemanticColors.current.warning
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = if (compact) 9.sp else 10.sp,
                ),
            )
        }
    }
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
