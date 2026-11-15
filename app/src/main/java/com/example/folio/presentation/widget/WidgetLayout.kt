package com.example.folio.presentation.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.folio.presentation.design.FolioSpacing
import com.example.folio.presentation.design.isolateFinancialText
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.folio.MainActivity
import com.example.folio.PortfolioCodes
import com.example.folio.R
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetPortfolioSnapshot
import com.example.folio.data.widget.compactWidgetTypeValues
import com.example.folio.data.widget.widgetAllocationPercentages
import com.example.folio.presentation.AppLanguage
import com.example.folio.presentation.LanguagePreferences
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal data class PortfolioWidgetAllocationRow(
    val assetType: String,
    val percentage: BigDecimal,
)

internal data class PortfolioWidgetTotalPresentation(
    val text: String,
    val contentDescription: String,
)

internal fun portfolioWidgetTotalPresentation(
    value: BigDecimal?,
    currency: String,
    hidden: Boolean,
    unavailableLabel: String,
): PortfolioWidgetTotalPresentation {
    val text = formatPortfolioWidgetTotal(value, currency, hidden, unavailableLabel)
    return PortfolioWidgetTotalPresentation(text = text, contentDescription = text)
}

internal fun formatPortfolioWidgetTotal(
    value: BigDecimal?,
    currency: String,
    hidden: Boolean,
    unavailableLabel: String,
): String {
    if (hidden) return MASKED_PORTFOLIO_TOTAL
    if (value == null) return unavailableLabel
    require(currency == PortfolioCodes.IRT || currency == PortfolioCodes.USD)

    val (displayValue, suffix, fractionDigits) = when {
        currency == PortfolioCodes.IRT && value.abs() >= BigDecimal("1000000000") ->
            Triple(
                value.divide(BigDecimal("1000000000"), 2, RoundingMode.DOWN),
                "B IRT",
                2,
            )
        currency == PortfolioCodes.IRT && value.abs() >= BigDecimal("1000000") ->
            Triple(
                value.divide(BigDecimal("1000000"), 1, RoundingMode.DOWN),
                "M IRT",
                1,
            )
        currency == PortfolioCodes.IRT -> Triple(value, PortfolioCodes.IRT, 0)
        else -> Triple(value, PortfolioCodes.USD, 2)
    }
    val number = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = fractionDigits
        minimumFractionDigits = 0
    }.format(displayValue)
    return isolateFinancialText("$number $suffix")
}

internal fun compactPortfolioWidgetAllocations(
    typeValues: Map<String, BigDecimal>,
    otherLabel: String,
): List<PortfolioWidgetAllocationRow> {
    val compactValues = compactWidgetTypeValues(typeValues, otherLabel)
    val percentages = widgetAllocationPercentages(compactValues)
    return compactValues.keys.mapNotNull { type ->
        percentages[type]?.let { PortfolioWidgetAllocationRow(type, it) }
    }
}

private fun portfolioWidgetAllocations(
    typeValues: Map<String, BigDecimal>,
    compact: Boolean,
    otherLabel: String,
): List<PortfolioWidgetAllocationRow> {
    if (compact) return compactPortfolioWidgetAllocations(typeValues, otherLabel)
    val percentages = widgetAllocationPercentages(typeValues)
    return percentages.map { (type, percentage) ->
        PortfolioWidgetAllocationRow(type, percentage)
    }
}

/** Builds a resource context from Folio's saved language choice for launcher-side rendering. */
internal fun widgetLocalizedContext(context: Context): Context {
    val language = LanguagePreferences(context).current()
    val locale = Locale.forLanguageTag(language.tag)
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return context.createConfigurationContext(configuration)
}

@Composable
internal fun PortfolioWidgetLayout(
    context: Context,
    appWidgetId: Int,
    configuration: WidgetConfiguration?,
    snapshot: WidgetPortfolioSnapshot?,
    isUpdating: Boolean,
    loadFailed: Boolean,
) {
    val size = LocalSize.current
    val expanded = size.width >= 280.dp && size.height >= 220.dp
    val language = LanguagePreferences(context).current()
    val direction = if (language == AppLanguage.PERSIAN) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val title = context.getString(R.string.widget_portfolio_name)
    val openDashboard = actionStartActivity<MainActivity>()

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FolioWidgetTheme {
            if (expanded) {
                Scaffold(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface),
                    horizontalPadding = FolioWidgetSpacing.contentHorizontal,
                    titleBar = {
                        TitleBar(
                            startIcon = ImageProvider(R.mipmap.ic_launcher),
                            title = title,
                            iconColor = GlanceTheme.colors.primary,
                            actions = {
                                PortfolioWidgetActionButtons(
                                    context = context,
                                    appWidgetId = appWidgetId,
                                    configuration = configuration,
                                )
                            },
                        )
                    },
                ) {
                    PortfolioWidgetBody(
                        context = context,
                        configuration = configuration,
                        snapshot = snapshot,
                        isUpdating = isUpdating,
                        loadFailed = loadFailed,
                        compact = false,
                        openDashboard = openDashboard,
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = FolioWidgetSpacing.contentHorizontal),
                    )
                }
            } else {
                Scaffold(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface),
                    horizontalPadding = 0.dp,
                ) {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = FolioWidgetSpacing.contentHorizontal),
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (configuration == null) {
                                Text(
                                    text = title,
                                    modifier = GlanceModifier.defaultWeight(),
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            } else {
                                Spacer(GlanceModifier.defaultWeight())
                            }
                            PortfolioWidgetActionButtons(
                                context = context,
                                appWidgetId = appWidgetId,
                                configuration = configuration,
                            )
                        }
                        PortfolioWidgetBody(
                            context = context,
                            configuration = configuration,
                            snapshot = snapshot,
                            isUpdating = isUpdating,
                            loadFailed = loadFailed,
                            compact = true,
                            openDashboard = openDashboard,
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .padding(horizontal = FolioWidgetSpacing.compactContentHorizontal),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioWidgetActionButtons(
    context: Context,
    appWidgetId: Int,
    configuration: WidgetConfiguration?,
) {
    if (configuration == null) {
        WidgetActionButton(
            icon = R.drawable.widget_configure,
            description = context.getString(R.string.widget_portfolio_configure),
            action = configureWidgetAction(appWidgetId),
        )
        return
    }

    WidgetActionButton(
        icon = if (configuration.hideTotal) {
            R.drawable.widget_visibility
        } else {
            R.drawable.widget_visibility_off
        },
        description = context.getString(
            if (configuration.hideTotal) {
                R.string.widget_portfolio_show_total
            } else {
                R.string.widget_portfolio_hide_total
            },
        ),
        action = togglePortfolioTotalAction(appWidgetId),
    )
    WidgetActionButton(
        icon = R.drawable.widget_refresh,
        description = context.getString(R.string.widget_portfolio_refresh),
        action = refreshWidgetAction(appWidgetId),
    )
    WidgetActionButton(
        icon = R.drawable.widget_configure,
        description = context.getString(R.string.widget_portfolio_configure),
        action = configureWidgetAction(appWidgetId),
    )
}

@Composable
internal fun WidgetActionButton(
    icon: Int,
    description: String,
    action: Action,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(FolioWidgetSpacing.actionTarget)
            .semantics { contentDescription = description }
            .clickable(action),
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun PortfolioWidgetBody(
    context: Context,
    configuration: WidgetConfiguration?,
    snapshot: WidgetPortfolioSnapshot?,
    isUpdating: Boolean,
    loadFailed: Boolean,
    compact: Boolean,
    openDashboard: Action,
    modifier: GlanceModifier,
) {
    when {
        configuration == null && !loadFailed -> {
            Column(
                modifier = modifier
                    .clickable(openDashboard)
                    .padding(vertical = FolioSpacing.extraSmall),
            ) {
                Text(
                    text = context.getString(R.string.widget_portfolio_setup),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(FolioSpacing.extraSmall))
                Text(
                    text = context.getString(R.string.widget_portfolio_setup_hint),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }

        loadFailed || snapshot == null -> {
            Column(
                modifier = modifier
                    .clickable(openDashboard)
                    .padding(vertical = FolioSpacing.extraSmall),
            ) {
                Text(
                    text = context.getString(R.string.widget_portfolio_load_error),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }

        else -> {
            val activeConfiguration = requireNotNull(configuration)
            val hidden = activeConfiguration.hideTotal
            val unavailable = context.getString(R.string.widget_portfolio_value_unavailable)
            val total = portfolioWidgetTotalPresentation(
                value = snapshot.total,
                currency = activeConfiguration.currency,
                hidden = hidden,
                unavailableLabel = unavailable,
            )
            val rows = portfolioWidgetAllocations(
                typeValues = snapshot.typeValues,
                compact = compact,
                otherLabel = context.getString(R.string.widget_portfolio_other),
            )
            val status = portfolioWidgetStatus(context, snapshot, isUpdating)
            val age = portfolioWidgetAge(context, snapshot.valuedAt, snapshot.generatedAt)
            val currency = context.getString(
                if (activeConfiguration.currency == PortfolioCodes.IRT) {
                    R.string.widget_currency_irt
                } else {
                    R.string.widget_currency_usd
                },
            )

            Column(
                modifier = modifier.clickable(openDashboard),
            ) {
                if (compact) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp)
                            .semantics { contentDescription = total.contentDescription },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (total.text.length < 15) {
                            Text(
                                text = context.getString(R.string.widget_portfolio_total),
                                modifier = GlanceModifier.width(42.dp),
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 9.sp,
                                ),
                            )
                        }
                        Text(
                            text = total.text,
                            modifier = GlanceModifier
                                .defaultWeight()
                                .semantics { contentDescription = total.contentDescription },
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = if (total.text.length < 15) 16.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                } else {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp)
                            .semantics { contentDescription = total.contentDescription },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = context.getString(R.string.widget_portfolio_total),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                            )
                            Text(
                                text = total.text,
                                modifier = GlanceModifier.semantics { contentDescription = total.contentDescription },
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        Text(
                            text = currency,
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                            ),
                        )
                    }
                }

                if (status != null || age.isNotBlank()) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        status?.let {
                            Text(
                                text = it,
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                                style = TextStyle(
                                    color = LocalFolioWidgetSemanticColors.current.warning,
                                    fontSize = if (compact) 9.sp else 11.sp,
                                ),
                            )
                        }
                        Text(
                            text = age,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = if (compact) 8.sp else 10.sp,
                                textAlign = TextAlign.End,
                            ),
                        )
                    }
                }

                if (rows.isNotEmpty()) {
                    rows.forEachIndexed { index, row ->
                        PortfolioAllocationRow(
                            context = context,
                            row = row,
                            compact = compact,
                            colorIndex = index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioAllocationRow(
    context: Context,
    row: PortfolioWidgetAllocationRow,
    compact: Boolean,
    colorIndex: Int,
) {
    val label = portfolioWidgetAssetTypeLabel(context, row.assetType)
    val percentage = formatPortfolioWidgetPercentage(row.percentage)
    val barWidth = if (compact) 36.dp else 48.dp
    val fillFraction = (row.percentage.toFloat() / 100f).coerceIn(0f, 1f)
    val fillWidth = max(1f, barWidth.value * fillFraction).coerceAtMost(barWidth.value).dp
    val colors = LocalFolioWidgetSemanticColors.current.allocationSeries
    val color = colors[colorIndex % colors.size]

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 1.dp)
            .height(14.dp)
            .semantics {
                contentDescription = context.getString(
                    R.string.widget_portfolio_allocation_description,
                    label,
                    percentage,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = GlanceModifier.width(if (compact) 68.dp else 84.dp),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 9.sp else 11.sp,
            ),
        )
        Box(
            modifier = GlanceModifier
                .width(barWidth)
                .height(4.dp)
                .background(GlanceTheme.colors.secondaryContainer),
        ) {
            Box(
                modifier = GlanceModifier
                    .width(fillWidth)
                    .height(4.dp)
                    .background(color),
            ) {}
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = percentage,
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compact) 9.sp else 11.sp,
                textAlign = TextAlign.End,
            ),
        )
    }
}

private fun portfolioWidgetAssetTypeLabel(context: Context, assetType: String): String =
    context.getString(
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

private fun portfolioWidgetStatus(
    context: Context,
    snapshot: WidgetPortfolioSnapshot,
    isUpdating: Boolean,
): String? {
    val status = when {
        snapshot.isEmpty -> context.getString(R.string.widget_portfolio_empty)
        snapshot.missingCount > 0 && snapshot.total != null -> context.getString(
            R.string.widget_portfolio_partial_value,
            context.resources.getQuantityString(
                R.plurals.widget_portfolio_missing_values,
                snapshot.missingCount,
                snapshot.missingCount,
            ),
        )
        snapshot.missingCount > 0 -> context.getString(
            R.string.widget_portfolio_unavailable_missing,
            context.getString(R.string.widget_portfolio_value_unavailable),
            context.resources.getQuantityString(
                R.plurals.widget_portfolio_missing_values,
                snapshot.missingCount,
                snapshot.missingCount,
            ),
        )
        snapshot.total == null -> context.getString(R.string.widget_portfolio_value_unavailable)
        else -> null
    }
    if (!isUpdating) return status
    val updating = context.getString(R.string.widget_portfolio_updating)
    return status?.let { context.getString(R.string.widget_portfolio_status_combined, updating, it) }
        ?: updating
}

private fun portfolioWidgetAge(
    context: Context,
    updatedAt: Instant?,
    now: Instant,
): String {
    if (updatedAt == null) return context.getString(R.string.widget_portfolio_no_prices)
    val elapsed = Duration.between(updatedAt, now).coerceAtLeast(Duration.ZERO)
    val age = when {
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
        else -> widgetDateFormat.format(updatedAt)
    }
    return context.getString(R.string.widget_portfolio_last_updated, age)
}

internal fun formatPortfolioWidgetPercentage(value: BigDecimal): String {
    val rounded = value.setScale(1, RoundingMode.HALF_UP)
    val number = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }.format(rounded)
    return isolateFinancialText("$number%")
}

private const val MASKED_PORTFOLIO_TOTAL = "••••"
private val widgetDateFormat = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    .withZone(ZoneId.systemDefault())
