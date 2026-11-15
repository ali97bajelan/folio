package com.example.folio.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.folio.FolioApplication
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetQuote
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

class AssetPriceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? FolioApplication
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val localizedContext = widgetLocalizedContext(context)

        var loadFailed = app == null
        var configuration: WidgetConfiguration? = null
        var quote: WidgetQuote? = null
        try {
            if (app != null) {
                val state = loadAssetPriceWidgetState(app, appWidgetId)
                configuration = state.configuration
                quote = state.quote
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadFailed = true
        }

        val isUpdating = configuration != null && hasActiveWidgetRefresh(context)
        provideContent {
            AssetPriceWidgetLayout(
                context = localizedContext,
                appWidgetId = appWidgetId,
                configuration = configuration,
                quote = quote,
                isUpdating = isUpdating,
                loadFailed = loadFailed,
            )
        }
    }
}

@Composable
internal fun AssetPriceWidgetLayout(
    context: Context,
    appWidgetId: Int,
    configuration: WidgetConfiguration?,
    quote: WidgetQuote?,
    isUpdating: Boolean,
    loadFailed: Boolean,
) {
    val size = LocalSize.current
    val expanded = size.width >= 280.dp && size.height >= 220.dp
    val language = com.example.folio.presentation.LanguagePreferences(context).current()
    val direction = if (language == com.example.folio.presentation.AppLanguage.PERSIAN) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val selection = configuration?.market
    val title = selection?.displayName ?: context.getString(R.string.widget_asset_price_name)
    val configureAction = configureWidgetAction(appWidgetId)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        GlanceTheme {
            if (expanded) {
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
                            actions = {
                                AssetPriceWidgetActionButtons(context, appWidgetId)
                            },
                        )
                    },
                ) {
                    AssetPriceWidgetBody(
                        context = context,
                        configuration = configuration,
                        quote = quote,
                        isUpdating = isUpdating,
                        loadFailed = loadFailed,
                        configureAction = configureAction,
                        compact = false,
                        modifier = GlanceModifier
                            .fillMaxSize()
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
                        quote = quote,
                        isUpdating = isUpdating,
                        loadFailed = loadFailed,
                        configureAction = configureAction,
                        compact = true,
                        modifier = GlanceModifier
                            .fillMaxSize()
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
    quote: WidgetQuote?,
    isUpdating: Boolean,
    loadFailed: Boolean,
    configureAction: Action,
    compact: Boolean,
    modifier: GlanceModifier,
) {
    val selection = configuration?.market
    if (selection == null) {
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

    val presentation = presentAssetPriceQuote(
        quote = if (loadFailed) null else quote,
        isUpdating = isUpdating,
        now = Instant.now(),
    )
    val provider = assetPriceProviderLabel(context, selection.key.provider)
    val instrumentLabel = context.getString(
        R.string.widget_asset_price_instrument,
        isolateLtrText(selection.symbol),
        selection.displayName,
    )
    val instrumentDescription = context.getString(
        R.string.widget_asset_price_instrument,
        selection.symbol,
        selection.displayName,
    )
    val compactInstrumentLabel = context.getString(
        R.string.widget_asset_price_instrument,
        provider,
        instrumentLabel,
    )
    val compactInstrumentDescription = context.getString(
        R.string.widget_asset_price_instrument,
        provider,
        instrumentDescription,
    )
    val quoteAge = formatAssetPriceQuoteAge(context, presentation.quoteAt, Instant.now())
    val age = quoteAge?.let {
        context.getString(R.string.widget_asset_price_quote_age, it)
    }.orEmpty()
    val amount = presentation.price?.let { price ->
        formatAssetPriceAmount(
            price = price,
            currency = requireNotNull(presentation.currency),
            tomanCurrencyLabel = context.getString(R.string.widget_asset_price_currency_irt),
        )
    }
    val priceText = amount?.let(::isolateLtrText)
        ?: context.getString(R.string.widget_asset_price_unavailable)
    val priceDescription = amount ?: priceText
    val status = buildList {
        if (presentation.unsupported) add(context.getString(R.string.widget_asset_price_unsupported))
        if (presentation.isUpdating) add(context.getString(R.string.widget_asset_price_updating))
        if (presentation.refreshFailed) {
            add(
                context.getString(
                    if (presentation.price == null) {
                        R.string.widget_asset_price_refresh_failed_unavailable
                    } else {
                        R.string.widget_asset_price_refresh_failed
                    },
                ),
            )
        }
        if (presentation.freshness == AssetPriceQuoteFreshness.STALE) {
            add(context.getString(R.string.widget_asset_price_stale))
        }
        if (loadFailed) add(context.getString(R.string.widget_asset_price_load_error))
    }.joinToString(" · ")
    val compactStatus = compactAssetPriceStatus(context, presentation, loadFailed)
    val compactMetadata = listOf(compactStatus, age)
        .filter(String::isNotBlank)
        .joinToString(" · ")

    Column(
        modifier = modifier
            .clickable(configureAction)
            .padding(vertical = 2.dp),
        verticalArrangement = androidx.glance.layout.Arrangement.spacedBy(1.dp),
    ) {
        if (compact) {
            Text(
                text = compactInstrumentLabel,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .semantics { contentDescription = compactInstrumentDescription },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        } else {
            Text(
                text = provider,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = instrumentLabel,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .semantics { contentDescription = instrumentDescription },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (selection.displayName.length + selection.symbol.length > 20) 16.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Text(
            text = priceText,
            modifier = GlanceModifier
                .fillMaxWidth()
                .semantics { contentDescription = priceDescription },
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = when {
                    priceText.length > 23 -> 14.sp
                    priceText.length > 17 -> 17.sp
                    else -> 21.sp
                },
                fontWeight = FontWeight.Bold,
            ),
        )
        if (compact) {
            if (compactMetadata.isNotBlank()) {
                Text(
                    text = compactMetadata,
                    modifier = GlanceModifier.fillMaxWidth(),
                    maxLines = 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                )
            }
        } else {
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = GlanceModifier.fillMaxWidth(),
                    maxLines = 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
            if (age.isNotBlank()) {
                Text(
                    text = age,
                    modifier = GlanceModifier.fillMaxWidth(),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

private fun compactAssetPriceStatus(
    context: Context,
    presentation: AssetPriceWidgetPresentation,
    loadFailed: Boolean,
): String {
    val isStale = presentation.freshness == AssetPriceQuoteFreshness.STALE
    return when {
        loadFailed -> context.getString(R.string.widget_asset_price_load_error)
        presentation.unsupported -> context.getString(R.string.widget_asset_price_unsupported)
        presentation.isUpdating && presentation.refreshFailed && isStale ->
            context.getString(R.string.widget_asset_price_compact_updating_failed_stale)
        presentation.isUpdating && presentation.refreshFailed ->
            context.getString(R.string.widget_asset_price_compact_updating_failed)
        presentation.isUpdating && isStale ->
            context.getString(R.string.widget_asset_price_compact_updating_stale)
        presentation.refreshFailed && isStale ->
            context.getString(R.string.widget_asset_price_compact_failed_stale)
        presentation.refreshFailed && presentation.price == null ->
            context.getString(R.string.widget_asset_price_refresh_failed_unavailable)
        presentation.refreshFailed ->
            context.getString(R.string.widget_asset_price_compact_refresh_failed)
        presentation.isUpdating -> context.getString(R.string.widget_asset_price_updating)
        isStale -> context.getString(R.string.widget_asset_price_stale)
        else -> ""
    }
}

private data class LoadedAssetPriceWidgetState(
    val configuration: WidgetConfiguration?,
    val quote: WidgetQuote?,
)

private suspend fun loadAssetPriceWidgetState(
    app: FolioApplication,
    appWidgetId: Int,
): LoadedAssetPriceWidgetState {
    repeat(2) {
        val loaded = app.widgetRepository.configuration(appWidgetId)
            ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
        val key = loaded?.market?.key
        val quote = key?.let { marketKey ->
            app.widgetRepository.quote(marketKey)?.takeIf { it.key == marketKey }
        }
        val latest = app.widgetRepository.configuration(appWidgetId)
            ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
        if (loaded == null && latest == null) {
            return LoadedAssetPriceWidgetState(configuration = null, quote = null)
        }
        if (isCurrentAssetPriceConfiguration(loaded, latest)) {
            return LoadedAssetPriceWidgetState(configuration = loaded, quote = quote)
        }
    }

    val latest = app.widgetRepository.configuration(appWidgetId)
        ?.takeIf { it.kind == WidgetKind.ASSET_PRICE }
    return LoadedAssetPriceWidgetState(configuration = latest, quote = null)
}

private fun isolateLtrText(value: String): String = "\u2066$value\u2069"

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
