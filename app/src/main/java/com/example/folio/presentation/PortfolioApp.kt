package com.example.folio.presentation

import com.example.folio.PortfolioCodes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.*
import com.example.folio.R
import com.example.folio.data.AssetDetail
import com.example.folio.data.AssetRow
import com.example.folio.data.AssetTypeHistoryPoint
import com.example.folio.data.DashboardData
import com.example.folio.data.PortfolioHistoryPoint
import com.example.folio.data.local.*
import com.example.folio.data.widget.MarketKey
import com.example.folio.data.widget.MarketSelection
import com.example.folio.presentation.components.MarketInstrumentPicker
import com.example.folio.presentation.design.*
import com.example.folio.presentation.icons.FolioIcons
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withLocale(Locale.US)
    .withZone(ZoneId.systemDefault())
private val relativeLastUpdatedWindow = Duration.ofDays(3)

/** Keeps Latin financial notation in its natural order inside RTL Persian text. */
private fun ltrValue(value: String) = isolateFinancialText(value)

private fun formattedNumber(value: BigDecimal, suffix: String, places: Int, minimumPlaces: Int): String =
    java.text.NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = places
        minimumFractionDigits = minimumPlaces
    }.format(value) + suffix.takeIf(String::isNotBlank)?.let { unit -> " $unit" }.orEmpty()

@Composable private fun number(value: BigDecimal?, suffix: String = "", places: Int = 2, minimumPlaces: Int = 0): String =
    value?.let {
        ltrValue(formattedNumber(it, suffix, places, minimumPlaces))
    } ?: stringResource(R.string.unavailable)

@Composable
private fun lastUpdatedText(updatedAt: Instant?, calculatedAt: Instant): String {
    if (updatedAt == null) return stringResource(R.string.empty_value)

    val elapsed = Duration.between(updatedAt, calculatedAt).coerceAtLeast(Duration.ZERO)

    return when {
        elapsed >= relativeLastUpdatedWindow -> dateFormat.format(updatedAt)
        elapsed < Duration.ofMinutes(1) -> stringResource(R.string.last_updated_just_now)
        elapsed < Duration.ofHours(1) -> pluralStringResource(
            R.plurals.last_updated_minutes_ago,
            elapsed.toMinutes().toInt(),
            elapsed.toMinutes().toInt(),
        )
        elapsed < Duration.ofDays(1) -> pluralStringResource(
            R.plurals.last_updated_hours_ago,
            elapsed.toHours().toInt(),
            elapsed.toHours().toInt(),
        )
        else -> pluralStringResource(
            R.plurals.last_updated_days_ago,
            elapsed.toDays().toInt(),
            elapsed.toDays().toInt(),
        )
    }
}

/** Truncates holdings without hiding their first meaningful fractional digit. */
@Composable private fun detailQuantity(value: BigDecimal): String {
    val places = if (value.abs() < BigDecimal.ONE) {
        val fraction = value.abs().toPlainString().substringAfter('.', "")
        fraction.indexOfFirst { it != '0' }.let { index -> if (index < 0) 0 else index + 1 }
    } else 2
    return number(value.setScale(places, RoundingMode.DOWN), places = places)
}

@Composable private fun compact(value: BigDecimal?): String {
    if (value == null) return stringResource(R.string.unavailable)

    val (divisor, label, places) = when {
        value.abs() >= BigDecimal("1000000000") -> Triple(BigDecimal("1000000000"), "B", 2)
        value.abs() >= BigDecimal("1000000") -> Triple(BigDecimal("1000000"), "M", 1)
        else -> return number(value, PortfolioCodes.IRT, 0)
    }
    // Financial notation stays identical in Persian and English.  Let the
    // formatter omit insignificant trailing zeros: 1.20 B -> 1.2 B, 1.00 B -> 1 B.
    return ltrValue("${formattedNumber(value.divide(divisor, places, RoundingMode.DOWN), "", places, 0)} $label IRT")
}

@Composable private fun money(value: BigDecimal?, currency: String) =
    if (currency == PortfolioCodes.IRT) compact(value) else number(value, currency)

@Composable
private fun compactUsd(value: BigDecimal?): String {
    if (value == null) return stringResource(R.string.unavailable)

    val (divisor, suffix) = when {
        value.abs() >= BigDecimal("1000000000") -> BigDecimal("1000000000") to "B"
        value.abs() >= BigDecimal("1000000") -> BigDecimal("1000000") to "M"
        value.abs() >= BigDecimal("1000") -> BigDecimal("1000") to "k"
        else -> BigDecimal.ONE to ""
    }
    val amount = value.divide(divisor, 2, RoundingMode.HALF_UP)
    return ltrValue('$' + formattedNumber(amount, "", 2, 0) + suffix)
}

@Composable private fun wholeUsd(value: BigDecimal?) = number(value?.setScale(0, RoundingMode.HALF_UP), PortfolioCodes.USD, places = 0)

@Composable private fun typeLabel(value: String) = when (value) {
    PortfolioCodes.FIXED_INCOME -> stringResource(R.string.type_fixed_income)
    PortfolioCodes.USD -> stringResource(R.string.type_usd)
    PortfolioCodes.CRYPTO -> stringResource(R.string.type_crypto)
    PortfolioCodes.IRAN_STOCK -> stringResource(R.string.type_iran_stock)
    PortfolioCodes.US_STOCK -> stringResource(R.string.type_us_stock)
    PortfolioCodes.GOLD -> stringResource(R.string.type_gold)
    PortfolioCodes.SILVER -> stringResource(R.string.type_silver)
    PortfolioCodes.MANUAL -> stringResource(R.string.type_manual)
    else -> value
}

private fun defaultProviderFor(assetType: String) = when (assetType) {
    PortfolioCodes.IRAN_STOCK -> PortfolioCodes.TSETMC
    PortfolioCodes.US_STOCK -> PortfolioCodes.ABANTETHER
    PortfolioCodes.GOLD, PortfolioCodes.SILVER -> PortfolioCodes.RAHAVARD
    else -> PortfolioCodes.NOBITEX
}

private fun NavBackStackEntry.longArgument(name: String): Long? =
    arguments?.getString(name)?.toLongOrNull()

private val FOLIO_TOP_LEVEL_ROUTES = setOf("dashboard", "assets", "transactions", "settings")

internal fun isFolioTopLevelRoute(route: String): Boolean =
    route in FOLIO_TOP_LEVEL_ROUTES

@Composable
fun PortfolioApp(
    factory: ViewModelProvider.Factory,
    languagePreferences: LanguagePreferences,
    themePreferences: ThemePreferences,
) {
    var theme by remember { mutableStateOf(themePreferences.current()) }
    val viewModel: MainViewModel = viewModel(factory = factory)
    val navigationController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val retryLabel = stringResource(R.string.retry)
    LaunchedEffect(error) {
        error?.let {
            if (snackbarHost.showSnackbar(it, actionLabel = retryLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.retry()
            } else viewModel.clearError()
        }
    }
    val entry by navigationController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: "dashboard"
    val isTopLevelRoute = isFolioTopLevelRoute(currentRoute)
    val contextualTitle = when (currentRoute) {
        "tags" -> stringResource(R.string.tags)
        "locations" -> stringResource(R.string.locations)
        "appearance-language" -> stringResource(R.string.appearance_language)
        "asset/new" -> stringResource(R.string.nav_assets)
        "asset/{id}" -> stringResource(R.string.asset_details)
        "asset/edit/{id}" -> stringResource(R.string.nav_assets)
        "price/{id}" -> stringResource(R.string.update_price)
        "transaction/edit/{id}" -> stringResource(R.string.edit_transaction_title)
        "transaction/new?asset={asset}" -> stringResource(R.string.add_transaction_title)
        else -> stringResource(R.string.personal_wealth)
    }
    val backDescription = stringResource(R.string.back)

    FolioTheme(theme = theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                if (isTopLevelRoute) {
                    FolioBrandTopBar(
                        sectionTitle = when (currentRoute) {
                            "dashboard" -> stringResource(R.string.dashboard_title)
                            "assets" -> stringResource(R.string.nav_assets)
                            "transactions" -> stringResource(R.string.nav_transactions)
                            "settings" -> stringResource(R.string.nav_more)
                            else -> stringResource(R.string.dashboard_title)
                        },
                    )
                } else {
                    FolioTopBar(
                        title = contextualTitle,
                        onBack = { navigationController.popBackStack() },
                        backDescription = backDescription,
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute) {
                    AppNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navigationController.navigate(route) {
                                popUpTo("dashboard")
                                launchSingleTop = true
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            NavHost(
                navController = navigationController,
                startDestination = "dashboard",
                modifier = Modifier.padding(paddingValues),
            ) {
                composable("dashboard") {
                    Dashboard(
                        viewModel,
                        addAsset = { navigationController.navigate("asset/new") },
                        addTx = { navigationController.navigate("transaction/new") },
                    )
                }
                composable("assets") {
                    Assets(
                        viewModel,
                        open = { navigationController.navigate("asset/$it") },
                        add = { navigationController.navigate("asset/new") },
                    )
                }
                composable("transactions") {
                    Transactions(
                        viewModel,
                        add = { navigationController.navigate("transaction/new") },
                        edit = { navigationController.navigate("transaction/edit/$it") },
                    )
                }
                composable("settings") {
                    Settings(
                        appearanceLanguage = { navigationController.navigate("appearance-language") },
                        tags = { navigationController.navigate("tags") },
                        locations = { navigationController.navigate("locations") },
                    )
                }
                composable("appearance-language") {
                    AppearanceLanguageSettings(
                        languagePreferences = languagePreferences,
                        theme = theme,
                        onThemeSelected = { selected ->
                            theme = selected
                            themePreferences.set(selected)
                        },
                    )
                }
                composable("tags") {
                    Manage(
                        stringResource(R.string.tags),
                        viewModel.tags.collectAsStateWithLifecycle().value.map { it.name },
                        save = { name, onError, done -> viewModel.saveTag(name, onError, done) },
                        enabled = !viewModel.isSaving.collectAsStateWithLifecycle().value,
                    )
                }
                composable("locations") { Locations(viewModel) }
                composable("asset/new") {
                    AssetForm(
                        vm = viewModel,
                        onDone = { navigationController.popBackStack() },
                        onCancel = { navigationController.popBackStack() },
                    )
                }
                composable("asset/{id}") { backStackEntry ->
                    backStackEntry.longArgument("id")?.let { id ->
                        AssetDetailScreen(
                            viewModel,
                            id,
                            edit = { navigationController.navigate("asset/edit/$id") },
                            price = { navigationController.navigate("price/$id") },
                            tx = { navigationController.navigate("transaction/new?asset=$id") },
                            back = { navigationController.popBackStack() },
                        )
                    }
                }
                composable("asset/edit/{id}") { backStackEntry ->
                    backStackEntry.longArgument("id")?.let { id ->
                        AssetForm(
                            vm = viewModel,
                            id = id,
                            onDone = { navigationController.popBackStack() },
                            onCancel = { navigationController.popBackStack() },
                        )
                    }
                }
                composable("price/{id}") { backStackEntry ->
                    backStackEntry.longArgument("id")?.let { id ->
                        PriceForm(viewModel, id) { navigationController.popBackStack() }
                    }
                }
                composable("transaction/new?asset={asset}") { backStackEntry ->
                    TransactionForm(viewModel, backStackEntry.longArgument("asset")) {
                        navigationController.popBackStack()
                    }
                }
                composable("transaction/edit/{id}") { backStackEntry ->
                    backStackEntry.longArgument("id")?.let { id ->
                        TransactionForm(viewModel, fixedAsset = null, editId = id) {
                            navigationController.popBackStack()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolioBrandTopBar(sectionTitle: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = FolioElevation.level1,
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = FolioSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(
                modifier = Modifier.size(40.dp),
                factory = { context ->
                    android.widget.ImageView(context).apply {
                        setImageResource(R.mipmap.ic_launcher)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                    }
                },
            )
            Spacer(Modifier.width(FolioSpacing.medium))
            Text(
                text = sectionTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Triple("dashboard", stringResource(R.string.nav_dashboard), FolioIcons.Dashboard),
        Triple("assets", stringResource(R.string.nav_assets), FolioIcons.AccountBalanceWallet),
        Triple("transactions", stringResource(R.string.nav_transactions), FolioIcons.ReceiptLong),
        Triple("settings", stringResource(R.string.nav_more), Icons.Outlined.Settings),
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = FolioElevation.level1,
    ) {
        destinations.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.tertiary,
                    selectedTextColor = MaterialTheme.colorScheme.tertiary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
@Composable
private fun Page(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = stringResource(R.string.personal_wealth),
    actionAlignment: Alignment.Horizontal = Alignment.End,
    action: (@Composable () -> Unit)? = null,
    showHeading: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) = FolioPage(
    title = title,
    modifier = modifier,
    subtitle = subtitle.takeIf { showHeading },
    showHeading = showHeading,
    actionAlignment = actionAlignment,
    actions = action,
    content = content,
)

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = FolioCard(modifier, content)

@Composable
private fun Tiny(text: String) = Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelSmall,
)

@Composable
private fun Pill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Surface(
    color = color.copy(alpha = .12f),
    shape = FolioShapeTokens.pill,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = FolioSpacing.small, vertical = FolioSpacing.extraSmall),
    )
}

@Composable
private fun Dashboard(
    vm: MainViewModel,
    addAsset: () -> Unit,
    addTx: () -> Unit,
) {
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val history by vm.portfolioHistory.collectAsStateWithLifecycle()
    val dashboardLoadedAt = remember(dash) { Instant.now() }
    var displayCurrency by rememberSaveable { mutableStateOf(PortfolioCodes.IRT) }

    Page(
        title = stringResource(R.string.dashboard_title),
        showHeading = false,
        action = {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            ) {
                DashboardAction(
                    text = stringResource(R.string.sync_prices),
                    icon = Icons.Outlined.Refresh,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = vm::refreshPrices,
                    enabled = !isRefreshing,
                    isLoading = isRefreshing,
                )
                DashboardAction(
                    text = stringResource(R.string.transaction),
                    icon = FolioIcons.ReceiptLong,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = addTx,
                )
                DashboardAction(
                    text = stringResource(R.string.asset),
                    icon = FolioIcons.AccountBalanceWallet,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = addAsset,
                )
            }
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FolioShapeTokens.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = FolioElevation.level1),
        ) {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
                )
                Column(Modifier.padding(FolioSpacing.large)) {
                    PortfolioValue(dash, displayCurrency, dashboardLoadedAt) {
                        displayCurrency = it
                    }
                }
            }
        }
        Spacer(Modifier.height(FolioSpacing.medium))
        PanelCard(Modifier.fillMaxWidth()) { PortfolioHistoryChart(history, displayCurrency) }
        Spacer(Modifier.height(FolioSpacing.medium))
        PanelCard(Modifier.fillMaxWidth()) { AllocationCakeChart(dash.rows, displayCurrency) }

    }
}

@Composable
private fun DashboardAction(
    text: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = FolioShapeTokens.pill,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(FolioIconSize.small),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(FolioIconSize.small))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private data class AllocationSlice(val assetType: String, val value: BigDecimal)

private fun allocationSlices(rows: List<AssetRow>, currency: String): List<AllocationSlice> =
    rows.groupBy { it.asset.assetType }
        .map { (assetType, typeRows) ->
            val value = typeRows.asSequence()
                .mapNotNull { if (currency == PortfolioCodes.USD) it.usdValue else it.tomanValue }
                .fold(BigDecimal.ZERO, BigDecimal::add)
            AllocationSlice(assetType, value)
        }
        .filter { it.value.signum() > 0 }
        .sortedByDescending { it.value }

@Composable
private fun AllocationCakeChart(rows: List<AssetRow>, currency: String) {
    val slices = allocationSlices(rows, currency)
    val total = slices.fold(BigDecimal.ZERO) { sum, slice -> sum.add(slice.value) }
    val colors = LocalFolioSemanticColors.current.allocationSeries
    val trackColor = MaterialTheme.colorScheme.surface

    Text(stringResource(R.string.asset_allocation_by_class), fontWeight = FontWeight.Bold)
    Tiny(stringResource(R.string.entire_portfolio))
    Spacer(Modifier.height(FolioSpacing.medium))
    if (slices.isEmpty() || (total.signum() == 0)) {
        Tiny(stringResource(R.string.unavailable))
        return
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                val inset = strokeWidth / 2
                val diameter = size.minDimension - strokeWidth
                val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(strokeWidth),
                )
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweepAngle = if (index == slices.lastIndex) {
                        270f - startAngle
                    } else {
                        slice.value.divide(total, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal("360")).toFloat()
                    }
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(strokeWidth),
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.total_assets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FolioFinancialValue(
                    value = if (currency == PortfolioCodes.USD) compactUsd(total) else money(total, currency),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    isolateDirection = false,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FolioSpacing.small),
        ) {
            slices.forEachIndexed { index, slice ->
                val percentage = slice.value.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                ) {
                    Surface(
                        color = colors[index % colors.size],
                        shape = FolioShapeTokens.pill,
                    ) { Spacer(Modifier.size(9.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeLabel(slice.assetType),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Tiny(ltrValue(formattedNumber(percentage, "%", 1, 0)))
                    }
                    FolioFinancialValue(
                        value = money(slice.value, currency),
                        color = colors[index % colors.size],
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(max = 88.dp),
                        isolateDirection = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioValue(
    dash: DashboardData,
    currency: String,
    dashboardLoadedAt: Instant,
    onCurrencyChange: (String) -> Unit,
) = Column {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.total_portfolio_value),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (dash.updatedAt != null) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = FolioShapeTokens.pill,
                ) { Spacer(Modifier.size(6.dp)) }
                Text(
                    lastUpdatedText(dash.updatedAt, dashboardLoadedAt),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CurrencySelector(currency, onCurrencyChange)
    }

    val total = if (currency == PortfolioCodes.USD) dash.usdTotal else dash.tomanTotal
    val alternateTotal = if (currency == PortfolioCodes.USD) compact(dash.tomanTotal) else wholeUsd(dash.usdTotal)
    Spacer(Modifier.height(FolioSpacing.small))
    FolioFinancialValue(
        value = if (currency == PortfolioCodes.USD) wholeUsd(total) else compact(total),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        isolateDirection = false,
    )
    Spacer(Modifier.height(FolioSpacing.extraSmall))
    FolioFinancialValue(
        value = "≈ $alternateTotal",
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        isolateDirection = false,
    )

    Spacer(Modifier.height(FolioSpacing.large))
    Surface(color = MaterialTheme.colorScheme.surface, shape = FolioShapeTokens.medium) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = FolioSpacing.medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Tiny(stringResource(R.string.active_assets))
                Text(dash.rows.size.toString(), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Tiny(stringResource(R.string.last_updated))
                Text(
                    lastUpdatedText(dash.updatedAt, dashboardLoadedAt),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (dash.missing > 0) {
        Spacer(Modifier.height(FolioSpacing.medium))
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = FolioShapeTokens.medium) {
            Text(
                pluralStringResource(R.plurals.assets_waiting_for_price, dash.missing, dash.missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = FolioSpacing.small, vertical = FolioSpacing.extraSmall),
            )
        }
    }
}

@Composable
private fun CurrencySelector(currency: String, onCurrencyChange: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = FolioShapeTokens.pill) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(PortfolioCodes.USD, PortfolioCodes.IRT).forEach { item ->
                val selected = item == currency
                Surface(
                    onClick = { onCurrencyChange(item) },
                    shape = FolioShapeTokens.pill,
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                ) {
                    Text(
                        text = isolateFinancialText(item),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun timelineFractions(times: List<Instant>): List<Float> {
    val start = times.first().toEpochMilli()
    val span = times.last().toEpochMilli() - start
    if (span <= 0L) return List(times.size) { .5f }
    return times.map { ((it.toEpochMilli() - start).toDouble() / span).toFloat() }
}

/** Preserve each series' highs and lows in every horizontal pixel bucket. */
private fun plotIndices(series: List<List<Float>>, fractions: List<Float>, width: Int): List<Int> {
    if (fractions.size <= width * 2) return fractions.indices.toList()
    val buckets = fractions.indices.groupBy { (fractions[it] * width).toInt() }
    return buildSet {
        add(0); add(fractions.lastIndex)
        buckets.values.forEach { indices ->
            series.forEach { values ->
                add(indices.minBy { values[it] })
                add(indices.maxBy { values[it] })
            }
        }
    }.sorted()
}

@Composable
private fun TimeSeriesCanvas(
    series: List<List<Float>>,
    fractions: List<Float>,
    selectedIndex: Int,
    lower: Float,
    upper: Float,
    ticks: List<Float>,
    colors: List<Color>,
    modifier: Modifier,
) {
    val selection = rememberUpdatedState(selectedIndex)
    val pinColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    val drawing = remember(series, fractions, lower, upper, ticks, colors, gridColor, pinColor) {
        Modifier.drawWithCache {
            val left = 4.dp.toPx()
            val right = size.width - left
            val top = 6.dp.toPx()
            val bottom = size.height - top
            val span = (upper - lower).coerceAtLeast(.0001f)
            fun x(index: Int) = left + (right - left) * fractions[index]
            fun y(value: Float) = bottom - (value - lower) / span * (bottom - top)
            val indices = plotIndices(series, fractions, (right - left).toInt().coerceAtLeast(1))
            val paths = series.map { values ->
                Path().apply {
                    indices.forEachIndexed { index, point ->
                        if (index == 0) moveTo(x(point), y(values[point]))
                        else lineTo(x(point), y(values[point]))
                    }
                }
            }
            onDrawBehind {
                ticks.forEach { tick ->
                    drawLine(gridColor, Offset(left, y(tick)), Offset(right, y(tick)))
                }
                paths.forEachIndexed { index, path ->
                    val color = colors[index % colors.size]
                    drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    val point = selection.value
                    val center = Offset(x(point), y(series[index][point]))
                    drawCircle(pinColor.copy(alpha = .22f), 12.dp.toPx(), center)
                    drawCircle(pinColor, 5.dp.toPx(), center)
                }
            }
        }
    }
    Spacer(modifier.then(drawing))
}

@Composable
private fun Modifier.chartNavigation(
    summary: String,
    times: List<Instant>,
    selectedIndex: Int,
    select: (Long) -> Unit,
): Modifier {
    val previous = stringResource(R.string.chart_previous_point)
    val next = stringResource(R.string.chart_next_point)
    fun move(index: Int): Boolean {
        if (index !in times.indices) return false
        select(times[index].toEpochMilli())
        return true
    }
    return semantics {
        contentDescription = summary
        customActions = buildList {
            if (selectedIndex > 0) add(CustomAccessibilityAction(previous) { move(selectedIndex - 1) })
            if (selectedIndex < times.lastIndex) add(CustomAccessibilityAction(next) { move(selectedIndex + 1) })
        }
    }.onKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.DirectionLeft -> move(selectedIndex - 1)
            Key.DirectionRight -> move(selectedIndex + 1)
            Key.MoveHome -> move(0)
            Key.MoveEnd -> move(times.lastIndex)
            else -> false
        }
    }.focusable()
}

private enum class HistoryRange(val duration: Duration?) { DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30)), ALL(null) }
@Composable private fun chartValue(value: BigDecimal, currency: String) =
    if (currency == PortfolioCodes.USD) wholeUsd(value) else compact(value)
@Composable
private fun HistoryRangeSelector(selected: HistoryRange, onSelected: (HistoryRange) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = FolioShapeTokens.medium) {
        Row(Modifier.padding(3.dp)) {
            HistoryRange.entries.forEach { item ->
                val selectedItem = item == selected
                Surface(
                    onClick = { onSelected(item) },
                    color = if (selectedItem) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    shape = FolioShapeTokens.small,
                ) {
                    Text(
                        text = stringResource(
                            when (item) {
                                HistoryRange.DAY -> R.string.chart_1_day
                                HistoryRange.WEEK -> R.string.chart_1_week
                                HistoryRange.MONTH -> R.string.chart_1_month
                                HistoryRange.ALL -> R.string.chart_all_time
                            },
                        ),
                        color = if (selectedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selectedItem) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioHistoryChart(history: List<PortfolioHistoryPoint>, currency: String) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.MONTH) }
    var selectedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = remember(history) { Instant.now() }
    val points = remember(history, range, currency) {
        history.filter { range.duration?.let { duration -> !it.capturedAt.isBefore(now.minus(duration)) } ?: true }
            .map { it.capturedAt to if (currency == PortfolioCodes.USD) it.usdValue else it.tomanValue }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                stringResource(R.string.portfolio_trajectory),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Tiny(stringResource(R.string.portfolio_performance_hint))
        }
        HistoryRangeSelector(range) { range = it }
    }
    Spacer(Modifier.height(FolioSpacing.medium))
    if (points.size < 2) { Tiny(stringResource(R.string.portfolio_history_empty)); return }
    val first = points.first().second
    val last = points.last().second
    val change = remember(first, last) { last.subtract(first) }
    val percentChange = remember(first, change) { first.takeIf { it.signum() != 0 }?.let { change.divide(it, 6, RoundingMode.HALF_UP).multiply(BigDecimal("100")) } }
    val values = remember(points) { points.map { it.second } }
    val low = remember(values) { values.minOrNull()!! }
    val high = remember(values) { values.maxOrNull()!! }
    val midpoint = remember(low, high) { low.add(high).divide(BigDecimal("2")) }
    val series = remember(values, low, high) {
        val span = high.subtract(low).takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        listOf(values.map { it.subtract(low).divide(span, 8, RoundingMode.HALF_UP).toFloat() })
    }
    val selected = points.firstOrNull { it.first.toEpochMilli() == selectedAt } ?: points.last()
    val selectedIndex = points.indexOf(selected)
    val times = remember(points) { points.map { it.first } }
    val timeFractions = remember(times) { timelineFractions(times) }
    val chartInsetPx = with(LocalDensity.current) { 4.dp.toPx() }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (change.signum() >= 0) "+" else "") + chartValue(change, currency),
                color = if (change.signum() >= 0) LocalFolioSemanticColors.current.gain else LocalFolioSemanticColors.current.loss,
                fontWeight = FontWeight.SemiBold,
            )
            percentChange?.let {
                Text(
                    "(${if (it.signum() >= 0) "+" else ""}${formattedNumber(it, "%", 1, 1)})",
                    color = if (change.signum() >= 0) LocalFolioSemanticColors.current.gain else LocalFolioSemanticColors.current.loss,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Tiny("${stringResource(R.string.chart_low)} ${chartValue(low, currency)} · ${stringResource(R.string.chart_peak)} ${chartValue(high, currency)}")
    }
    Row(Modifier.fillMaxWidth().height(156.dp)) {
        Column(Modifier.width(72.dp).fillMaxHeight().padding(end=6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Tiny(chartValue(high, currency)); Tiny(chartValue(midpoint, currency)); Tiny(chartValue(low, currency))
        }
        var chartWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { chartWidthPx = it.width }) {
            val tooltipWidth = 96.dp
            val selectedFraction = timeFractions[selectedIndex]
            val tooltipX = with(density) {
                ((chartWidthPx - tooltipWidth.roundToPx()).coerceAtLeast(0) * selectedFraction).toDp()
            }
            val summary = "${stringResource(R.string.portfolio_value_history)} · ${dateFormat.format(selected.first)} · ${chartValue(selected.second, currency)}"
            TimeSeriesCanvas(
                series = series,
                fractions = timeFractions,
                selectedIndex = selectedIndex,
                lower = 0f,
                upper = 1f,
                ticks = listOf(0f, .5f, 1f),
                colors = listOf(MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxSize().padding(vertical = 10.dp)
                    .chartNavigation(summary, times, selectedIndex) { selectedAt = it }
                    .pointerInput(points) {
                        detectTapGestures { tap ->
                            val fraction = ((tap.x - chartInsetPx) / (size.width - 2 * chartInsetPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val nearest = timeFractions.indices.minByOrNull { kotlin.math.abs(timeFractions[it] - fraction) }!!
                            selectedAt = points[nearest].first.toEpochMilli()
                        }
                    },
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).offset(x = tooltipX).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) { Text(chartValue(selected.second, currency), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal=6.dp, vertical=3.dp), maxLines = 1) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start=72.dp), horizontalArrangement=Arrangement.SpaceBetween) { Tiny(dateFormat.format(points.first().first)); Tiny(dateFormat.format(points.last().first)) }
}
@Composable
private fun Metric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier,
) = FolioCard(modifier) {
    Tiny(label)
    FolioFinancialValue(
        value = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        isolateDirection = false,
    )
    if (detail.isNotBlank()) Tiny(detail)
}
@Composable
private fun AssetStatusSelector(
    selected: AssetFilter,
    onSelected: (AssetFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(FolioShapeTokens.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { selectableGroup() }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetFilter.entries.forEach { option ->
            val isSelected = selected == option
            val segmentWeight = when (option) {
                AssetFilter.ALL -> 0.8f
                AssetFilter.ACTIVE -> 1f
                AssetFilter.INACTIVE -> 1.3f
            }
            Surface(
                modifier = Modifier
                    .weight(segmentWeight)
                    .heightIn(min = FolioMinimumTouchTarget)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option) },
                        role = Role.RadioButton,
                    ),
                shape = FolioShapeTokens.pill,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = FolioMinimumTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            when (option) {
                                AssetFilter.ALL -> R.string.assets_filter_all
                                AssetFilter.ACTIVE -> R.string.assets_filter_active
                                AssetFilter.INACTIVE -> R.string.assets_filter_inactive
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetRowItem(row: AssetRow, open: (Long) -> Unit) {
    val quantity = "${number(row.quantity, places = 4)}${if (row.asset.unit == PortfolioCodes.GRAM) " g" else ""}"
    val semanticColors = LocalFolioSemanticColors.current
    val accent = when (row.asset.assetType) {
        PortfolioCodes.USD -> semanticColors.gain
        PortfolioCodes.GOLD -> semanticColors.warning
        PortfolioCodes.SILVER -> MaterialTheme.colorScheme.onSurfaceVariant
        PortfolioCodes.CRYPTO -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { open(row.asset.id) },
        shape = FolioShapeTokens.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(
            defaultElevation = FolioElevation.level0,
            pressedElevation = FolioElevation.level1,
        ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = .2f)))),
            )
            Column(
                modifier = Modifier.padding(FolioSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall)) {
                        Text(
                            text = isolateFinancialText(row.asset.symbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = row.asset.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(FolioSpacing.small))
                    Pill(typeLabel(row.asset.assetType), accent)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                ) {
                    AssetRowMetric(
                        label = stringResource(R.string.quantity),
                        value = quantity,
                        modifier = Modifier.weight(1.15f),
                    )
                    AssetRowMetric(
                        label = "IRT",
                        value = compact(row.tomanValue),
                        modifier = Modifier.weight(1f),
                    )
                    AssetRowMetric(
                        label = "USD",
                        value = wholeUsd(row.usdValue),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!row.asset.isActive) {
                    Tiny(stringResource(R.string.assets_filter_inactive))
                }
            }
        }
    }
}

@Composable
private fun AssetRowMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
    ) {
        Tiny(label)
        FolioFinancialValue(
            value = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private enum class AssetFilter { ALL, ACTIVE, INACTIVE }
private enum class AssetSort { VALUE, NAME, QUANTITY }

private fun assetSortLabelResource(sort: AssetSort) = when (sort) {
    AssetSort.VALUE -> R.string.sort_value
    AssetSort.NAME -> R.string.sort_name
    AssetSort.QUANTITY -> R.string.sort_quantity
}

@Composable
private fun Assets(vm: MainViewModel, open: (Long) -> Unit, add: () -> Unit) {
    val data by vm.dashboard.collectAsStateWithLifecycle()
    val history by vm.assetTypeHistory.collectAsStateWithLifecycle()
    var order by rememberSaveable { mutableStateOf(AssetSort.VALUE) }
    var filter by rememberSaveable { mutableStateOf(AssetFilter.ALL) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val rows = remember(data.assetRows, order, filter) {
        val visible = data.assetRows.filter {
            when (filter) {
                AssetFilter.ALL -> true
                AssetFilter.ACTIVE -> it.asset.isActive
                AssetFilter.INACTIVE -> !it.asset.isActive
            }
        }
        when (order) {
            AssetSort.NAME -> visible.sortedBy { it.asset.name.lowercase() }
            AssetSort.QUANTITY -> visible.sortedByDescending { it.quantity }
            AssetSort.VALUE -> visible
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FolioSpacing.large, vertical = FolioSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = FolioShapeTokens.pill) {
                Text(
                    text = data.assetRows.size.toString(),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            FolioPrimaryButton(stringResource(R.string.add_asset), add)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssetStatusSelector(
                selected = filter,
                onSelected = { filter = it },
                modifier = Modifier.weight(1f),
            )

            Box {
                TextButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.widthIn(min = 96.dp, max = 140.dp)
                        .heightIn(min = FolioMinimumTouchTarget),
                    contentPadding = PaddingValues(horizontal = FolioSpacing.small, vertical = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.assets_sort_by),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(assetSortLabelResource(order)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    AssetSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(assetSortLabelResource(option))) },
                            onClick = {
                                order = option
                                sortMenuExpanded = false
                            },
                            trailingIcon = {
                                if (order == option) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            Empty(
                title = stringResource(R.string.assets_empty_title),
                copy = stringResource(R.string.assets_empty_copy),
                action = add,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
                rows.forEach { item -> AssetRowItem(item, open) }
            }
        }

        PanelCard(Modifier.fillMaxWidth()) { AssetTypeHistoryChart(history) }
    }
}

private data class PercentageAxis(val lower: Float, val upper: Float, val ticks: List<Float>)

private fun percentageAxis(percentages: List<BigDecimal>): PercentageAxis {
    val minimum = percentages.minOf(BigDecimal::toFloat)
    val maximum = percentages.maxOf(BigDecimal::toFloat)
    val padding = if (minimum == maximum) (minimum * .1f).coerceAtLeast(5f) else 0f
    val rawLower = (minimum - padding).coerceAtLeast(0f)
    val rawUpper = (maximum + padding).coerceAtMost(100f)
    val roughStep = ((rawUpper - rawLower) / 6f).coerceAtLeast(.1f)
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(roughStep.toDouble()))).toFloat()
    val step = when (roughStep / magnitude) {
        in 0f..1f -> magnitude
        in 1f..2f -> 2f * magnitude
        in 2f..5f -> 5f * magnitude
        else -> 10f * magnitude
    }
    val lower = (kotlin.math.floor(rawLower / step) * step).coerceAtLeast(0f)
    val upper = (kotlin.math.ceil(rawUpper / step) * step).coerceAtMost(100f)
    val count = ((upper - lower) / step).toInt().coerceAtLeast(1)
    return PercentageAxis(lower, upper, (0..count).map { lower + it * step })
}

private fun percentageLabel(value: Float): String =
    formattedNumber(BigDecimal.valueOf(value.toDouble()), "%", if (value % 1f == 0f) 0 else 1, 0)

@Composable
private fun AssetTypeHistoryChart(history: List<AssetTypeHistoryPoint>) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.ALL) }
    var selectedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = remember(history) { Instant.now() }
    val points = remember(history, range) {
        history.filter { range.duration?.let { duration -> !it.capturedAt.isBefore(now.minus(duration)) } ?: true }
    }
    Text(stringResource(R.string.asset_type_percentage_history), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    FolioChoiceChips(
        options = HistoryRange.entries,
        selected = range,
        onSelected = { range = it },
        optionLabel = { item ->
            stringResource(
                when (item) {
                    HistoryRange.DAY -> R.string.chart_1_day
                    HistoryRange.WEEK -> R.string.chart_1_week
                    HistoryRange.MONTH -> R.string.chart_1_month
                    HistoryRange.ALL -> R.string.chart_all_time
                },
            )
        },
    )
    Spacer(Modifier.height(12.dp))
    if (points.size < 2) {
        Tiny(stringResource(R.string.asset_type_history_empty))
        return
    }
    val assetTypes = remember(points) {
        points.flatMap { it.values.keys }.distinct()
            .sortedByDescending { assetType -> points.last().values[assetType] ?: BigDecimal.ZERO }
    }
    val selected = points.firstOrNull { it.capturedAt.toEpochMilli() == selectedAt } ?: points.last()
    val selectedIndex = points.indexOf(selected)
    val times = remember(points) { points.map { it.capturedAt } }
    val timeFractions = remember(times) { timelineFractions(times) }
    val chartInsetPx = with(LocalDensity.current) { 4.dp.toPx() }
    val colors = LocalFolioSemanticColors.current.allocationSeries
    val percentages = remember(points, assetTypes) { points.map { point ->
        val total = point.values.values.fold(BigDecimal.ZERO, BigDecimal::add)
        assetTypes.associateWith { assetType ->
            if (total.signum() == 0) BigDecimal.ZERO else (point.values[assetType] ?: BigDecimal.ZERO)
                .divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        }
    }
    }
    val axis = remember(percentages) { percentageAxis(percentages.flatMap { it.values }) }
    val axisLabels = remember(axis) { axis.ticks.asReversed().map { ltrValue(percentageLabel(it)) } }
    val series = remember(percentages, assetTypes) {
        assetTypes.map { type -> percentages.map { it.getValue(type).toFloat() } }
    }
    val selectedPercentages = percentages[selectedIndex]
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        assetTypes.forEachIndexed { index, assetType ->
            val percentage = selectedPercentages.getValue(assetType)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors[index % colors.size], shape = FolioShapeTokens.pill) { Spacer(Modifier.size(10.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(typeLabel(assetType), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ltrValue(formattedNumber(percentage, "%", 1, 0)), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().height(190.dp)) {
        Column(Modifier.width(44.dp).fillMaxHeight().padding(end=6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            axisLabels.forEach { Tiny(it) }
        }
        val labels = assetTypes.map { type -> "${typeLabel(type)} ${percentageLabel(selectedPercentages.getValue(type).toFloat())}" }
        val summary = "${stringResource(R.string.asset_type_percentage_history)} · ${dateFormat.format(selected.capturedAt)} · ${labels.joinToString(", ") }"
        TimeSeriesCanvas(series, timeFractions, selectedIndex, axis.lower, axis.upper, axis.ticks, colors,
            Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp)
                .chartNavigation(summary, times, selectedIndex) { selectedAt = it }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val fraction = ((tap.x - chartInsetPx) / (size.width - 2 * chartInsetPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val nearest = timeFractions.indices.minByOrNull { kotlin.math.abs(timeFractions[it] - fraction) }!!
                        selectedAt = points[nearest].capturedAt.toEpochMilli()
                    }
                })
    }
    Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Tiny(dateFormat.format(points.first().capturedAt)); Tiny(dateFormat.format(points.last().capturedAt))
    }
}
@Composable
private fun Transactions(vm: MainViewModel, add: () -> Unit, edit: (Long) -> Unit) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val assetsById = assets.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FolioSpacing.large, vertical = FolioSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            FolioPrimaryButton(stringResource(R.string.add_transaction), add)
        }
        if (transactions.isEmpty()) {
            Empty(
                title = stringResource(R.string.transactions_empty_title),
                copy = stringResource(R.string.transactions_empty_copy),
                actionLabel = stringResource(R.string.add_transaction),
                action = add,
            )
        }

        if (transactions.isNotEmpty()) {
            PanelCard(Modifier.fillMaxWidth()) {
                transactions.forEachIndexed { index, transaction ->
                    val isPurchase = transaction.transactionType == PortfolioCodes.BUY
                    val transactionLabel = stringResource(if (isPurchase) R.string.buy else R.string.sell)
                    val assetSymbol = assetsById[transaction.assetId]?.symbol
                        ?: stringResource(R.string.empty_value)

                    FolioListRow(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { edit(transaction.id) },
                        trailing = {
                            Pill(
                                transactionLabel,
                                if (isPurchase) LocalFolioSemanticColors.current.gain
                                else LocalFolioSemanticColors.current.loss,
                            )
                        },
                    ) {
                        Text(
                            "$transactionLabel · ${isolateFinancialText(assetSymbol)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        FolioFinancialValue(
                            value = "${number(transaction.quantity)} · ${dateFormat.format(transaction.executedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            isolateDirection = false,
                        )
                    }
                    if (index < transactions.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetDetailScreen(
    vm: MainViewModel,
    id: Long,
    edit: () -> Unit,
    price: () -> Unit,
    tx: () -> Unit,
    back: () -> Unit,
) {
    val detail by remember(vm, id) { vm.detail(id) }.collectAsStateWithLifecycle(initialValue = null)
    val locations by vm.locations.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var deleting by remember(vm, id) { mutableStateOf(false) }
    var deleteError by remember(vm, id) { mutableStateOf("") }
    val assetDetail = detail ?: return Page(
        title = stringResource(R.string.asset_details),
        showHeading = false,
    ) {
        FolioStateMessage(
            message = stringResource(R.string.loading),
            kind = FolioMessageKind.Loading,
        )
    }
    val assetRow = dashboard.assetRows.firstOrNull { it.asset.id == id }

    if (confirm) {
        FolioDialog(
            title = stringResource(R.string.delete_asset_title),
            text = stringResource(R.string.delete_asset_message),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                if (!deleting) {
                    deleting = true
                    deleteError = ""
                    vm.deleteAsset(
                        assetDetail.asset,
                        onError = {
                            deleteError = it
                            deleting = false
                        },
                        done = {
                            deleting = false
                            confirm = false
                            back()
                        },
                    )
                }
            },
            onDismiss = { if (!deleting) confirm = false },
            destructive = true,
            supportingError = deleteError.takeIf(String::isNotBlank),
            working = deleting,
        )
    }

    Page(
        title = assetDetail.asset.name,
        showHeading = false,
    ) {
        Text(
            text = assetDetail.asset.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(FolioSpacing.medium))
        FolioPrimaryButton(
            text = "+ ${stringResource(R.string.transaction)}",
            onClick = tx,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FolioSpacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
            FolioSecondaryButton(
                text = stringResource(R.string.edit),
                onClick = edit,
                modifier = Modifier.weight(1f),
            )
            if (assetDetail.asset.pricingMode == PortfolioCodes.MANUAL) {
                FolioSecondaryButton(
                    text = stringResource(R.string.update_price),
                    onClick = price,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(FolioSpacing.small))
        FolioDestructiveButton(
            text = stringResource(R.string.delete),
            onClick = { confirm = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FolioSpacing.large))
        AssetMetrics(assetDetail, assetRow?.tomanValue, assetRow?.usdValue)
        Spacer(Modifier.height(FolioSpacing.large))
        PanelCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.asset_details), fontWeight = FontWeight.Bold)
            DetailLine(stringResource(R.string.symbol), isolateFinancialText(assetDetail.asset.symbol))
            DetailLine(stringResource(R.string.type), typeLabel(assetDetail.asset.assetType))
            DetailLine(
                stringResource(R.string.pricing),
                if (assetDetail.asset.pricingMode == PortfolioCodes.MANUAL) {
                    stringResource(R.string.manual)
                } else {
                    stringResource(R.string.market_provider, assetDetail.asset.priceProvider)
                },
            )
            DetailLine(
                stringResource(R.string.last_price),
                assetDetail.prices.firstOrNull()?.let { money(it.price, it.currency) }
                    ?: stringResource(R.string.unavailable),
            )
        }
        Spacer(Modifier.height(FolioSpacing.medium))
        PanelCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.holdings_by_location), fontWeight = FontWeight.Bold)
            assetDetail.byLocation.forEach { (locationId, quantity) ->
                val locationName = locationId?.let { selectedId ->
                    locations.firstOrNull { it.id == selectedId }?.name
                } ?: stringResource(R.string.no_location)
                DetailLine(locationName, detailQuantity(quantity))
            }
            if (assetDetail.byLocation.isEmpty()) {
                Tiny(stringResource(R.string.no_holdings))
            }
        }
        Spacer(Modifier.height(FolioSpacing.medium))
        Text(stringResource(R.string.nav_transactions), fontWeight = FontWeight.Bold)
        assetDetail.transactions.forEachIndexed { index, transaction ->
            val transactionLabel = stringResource(
                if (transaction.transactionType == PortfolioCodes.BUY) R.string.buy else R.string.sell,
            )
            FolioListRow(
                trailing = { Pill(isolateFinancialText(transaction.transactionCurrency)) },
            ) {
                Text(transactionLabel)
                FolioFinancialValue(
                    value = "${number(transaction.quantity)} · ${dateFormat.format(transaction.executedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    isolateDirection = false,
                )
            }
            if (index < assetDetail.transactions.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}
@Composable private fun AssetMetrics(d:AssetDetail,tomanValue:BigDecimal?,usdtValue:BigDecimal?) {
    var metricsWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onSizeChanged { metricsWidthPx = it.width }) {
        val cards:@Composable (Modifier)->Unit={modifier->Metric(stringResource(R.string.quantity),detailQuantity(d.costBasis.quantity),choiceLabel(d.asset.unit),modifier);Metric(stringResource(R.string.value_irt),compact(tomanValue),"",modifier);Metric(stringResource(R.string.value_usdt),number(usdtValue,PortfolioCodes.USDT),"",modifier);Metric(stringResource(R.string.average_cost),number(d.costBasis.average),"",modifier)}
        if (with(density) { metricsWidthPx.toDp() } < 480.dp) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { cards(Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { cards(Modifier.weight(1f)) }
    }
}
@Composable
private fun DetailLine(label: String, value: String) {
    FolioListRow {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FolioFinancialValue(
            value = value,
            style = MaterialTheme.typography.bodyMedium,
            isolateDirection = false,
            maxLines = 2,
        )
    }
}

private data class AssetCategoryOption(
    val label: Int,
    val marker: String,
    val types: List<String>,
)

private val ASSET_CATEGORY_OPTIONS = listOf(
    AssetCategoryOption(R.string.category_crypto, "₿", listOf(PortfolioCodes.CRYPTO)),
    AssetCategoryOption(R.string.category_metals, "◆", listOf(PortfolioCodes.GOLD, PortfolioCodes.SILVER)),
    AssetCategoryOption(R.string.category_fiat, "¤", listOf(PortfolioCodes.USD, PortfolioCodes.FIXED_INCOME, PortfolioCodes.MANUAL)),
    AssetCategoryOption(R.string.category_equities, "↗", listOf(PortfolioCodes.IRAN_STOCK, PortfolioCodes.US_STOCK)),
)

@Composable
private fun AssetFormSection(
    title: String,
    step: Int,
    totalSteps: Int,
    marker: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    FolioCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
        ) {
            Surface(
                shape = FolioShapeTokens.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = marker,
                    modifier = Modifier.padding(horizontal = FolioSpacing.small, vertical = FolioSpacing.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.step_progress, step, totalSteps),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(FolioSpacing.medium))
        Column(
            verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetForm(
    vm: MainViewModel,
    id: Long? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val isSaving by vm.isSaving.collectAsStateWithLifecycle()
    val existingAsset by (if (id == null) remember { mutableStateOf<AssetEntity?>(null) }
        else remember(vm, id) { vm.asset(id) }.collectAsStateWithLifecycle(initialValue = null))
    val tagIds by (if (id == null) remember { mutableStateOf<List<Long>?>(emptyList()) }
        else remember(vm, id) { vm.assetTagIds(id) }.collectAsStateWithLifecycle(initialValue = null))
    val tags by vm.tags.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()

    var name by rememberSaveable(id) { mutableStateOf("") }
    var symbol by rememberSaveable(id) { mutableStateOf("") }
    var assetType by rememberSaveable(id) { mutableStateOf(PortfolioCodes.CRYPTO) }
    var unit by rememberSaveable(id) { mutableStateOf(PortfolioCodes.UNIT) }
    var pricingMode by rememberSaveable(id) { mutableStateOf(PortfolioCodes.MARKET) }
    var provider by rememberSaveable(id) { mutableStateOf(PortfolioCodes.NOBITEX) }
    var providerSymbol by rememberSaveable(id) { mutableStateOf("") }
    var currency by rememberSaveable(id) { mutableStateOf(PortfolioCodes.IRT) }
    var active by rememberSaveable(id) { mutableStateOf(true) }
    var selectedTagIds by rememberSaveable(id, stateSaver = listSaver<Set<Long>, Long>(
        save = { it.toList() }, restore = { it.toSet() },
    )) { mutableStateOf(setOf<Long>()) }
    var openingLocation by rememberSaveable(id) { mutableStateOf(0L) }
    var openingQuantity by rememberSaveable(id) { mutableStateOf("") }
    var openingAverage by rememberSaveable(id) { mutableStateOf("") }
    var error by rememberSaveable(id) { mutableStateOf("") }
    var showLocationSheet by rememberSaveable(id) { mutableStateOf(false) }
    var locationName by rememberSaveable(id) { mutableStateOf("") }
    var locationNotes by rememberSaveable(id) { mutableStateOf("") }
    var locationError by rememberSaveable(id) { mutableStateOf("") }

    val noLocation = stringResource(R.string.no_location)
    val nameRequired = stringResource(R.string.error_name_required)
    val symbolRequired = stringResource(R.string.error_symbol_required)
    val marketRequired = stringResource(R.string.error_market_selection_required)
    var assetInitialized by rememberSaveable(id) { mutableStateOf(id == null) }
    var tagsInitialized by rememberSaveable(id) { mutableStateOf(id == null) }

    // Initialize once so later database emissions cannot overwrite unsaved edits.
    LaunchedEffect(existingAsset) {
        if (!assetInitialized) existingAsset?.let { asset ->
            name = asset.name
            symbol = asset.symbol
            assetType = asset.assetType
            unit = asset.unit
            pricingMode = asset.pricingMode
            provider = asset.priceProvider
            providerSymbol = asset.providerSymbol
            currency = asset.manualPriceCurrency
            active = asset.isActive
            assetInitialized = true
        }
    }
    LaunchedEffect(tagIds) {
        if (!tagsInitialized) tagIds?.let {
            selectedTagIds = it.toSet()
            tagsInitialized = true
        }
    }
    if (!assetInitialized || !tagsInitialized) return Page(
        title = stringResource(R.string.edit_asset_title),
        showHeading = false,
        modifier = Modifier.imePadding(),
    ) {
        FolioStateMessage(
            message = stringResource(R.string.loading),
            kind = FolioMessageKind.Loading,
        )
    }

    val formTitle = stringResource(
        if (id == null) R.string.add_asset_title else R.string.edit_asset_title,
    )
    val totalSteps = if (id == null) 4 else 3
    val selectedCategory = ASSET_CATEGORY_OPTIONS.firstOrNull { assetType in it.types }
        ?: ASSET_CATEGORY_OPTIONS.first()
    val openingBookValue = (openingQuantity.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        .multiply(openingAverage.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    val saveEnabled = !isSaving && (id == null || existingAsset != null)
    val accentChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
        selectedLabelColor = MaterialTheme.colorScheme.tertiary,
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = FolioSpacing.large, vertical = FolioSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(FolioShapeTokens.pill)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                        Text(
                            text = stringResource(R.string.asset_registry_label).uppercase(),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        text = formTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.asset_form_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (id == null) {
                    Surface(
                        shape = FolioShapeTokens.pill,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        TextButton(
                            onClick = {
                                name = "Ethereum"
                                symbol = "ETH"
                                assetType = PortfolioCodes.CRYPTO
                                unit = PortfolioCodes.UNIT
                                pricingMode = PortfolioCodes.MANUAL
                                provider = PortfolioCodes.NOBITEX
                                providerSymbol = ""
                                currency = PortfolioCodes.USD
                                openingQuantity = "1.75"
                                openingAverage = "3450"
                                error = ""
                            },
                        ) {
                            Text(stringResource(R.string.quick_fill))
                        }
                    }
                }
            }

            AssetFormSection(
                title = stringResource(R.string.asset_identity),
                step = 1,
                totalSteps = totalSteps,
                marker = "◈",
            ) {
                Text(
                    text = stringResource(R.string.category_classification),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FolioShapeTokens.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(FolioSpacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                ) {
                    ASSET_CATEGORY_OPTIONS.forEach { category ->
                        val selected = category == selectedCategory
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FolioShapeTokens.small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent,
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        val newType = category.types.first()
                                        assetType = newType
                                        if (id == null) {
                                            provider = defaultProviderFor(newType)
                                            providerSymbol = ""
                                        }
                                    },
                                )
                                .padding(horizontal = FolioSpacing.extraSmall, vertical = FolioSpacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                        ) {
                            Text(
                                text = category.marker,
                                color = if (selected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(category.label).uppercase(),
                                color = if (selected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (selectedCategory.types.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                    ) {
                        selectedCategory.types.forEach { type ->
                            FilterChip(
                                selected = assetType == type,
                                onClick = {
                                    assetType = type
                                    if (id == null) {
                                        provider = defaultProviderFor(type)
                                        providerSymbol = ""
                                    }
                                },
                                label = { Text(typeLabel(type)) },
                                modifier = Modifier.heightIn(min = FolioMinimumTouchTarget),
                                colors = accentChipColors,
                            )
                        }
                    }
                }
                FolioField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (symbol.isBlank()) symbol = it.uppercase().replace(' ', '-')
                    },
                    label = stringResource(R.string.name),
                    placeholder = stringResource(R.string.asset_name_hint),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
                    FolioField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = stringResource(R.string.symbol),
                        placeholder = stringResource(R.string.symbol_hint),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                    )
                    FolioSelector(
                        options = listOf(PortfolioCodes.UNIT, PortfolioCodes.GRAM),
                        selected = unit,
                        onSelected = { unit = it },
                        label = stringResource(R.string.accounting_unit),
                        modifier = Modifier.weight(1f),
                        optionLabel = { choiceLabel(it) },
                    )
                }
            }

            AssetFormSection(
                title = stringResource(R.string.pricing_and_market_sync),
                step = 2,
                totalSteps = totalSteps,
                marker = "↗",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FolioShapeTokens.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(FolioSpacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                ) {
                    listOf(
                        PortfolioCodes.MARKET to stringResource(R.string.pricing_market),
                        PortfolioCodes.MANUAL to stringResource(R.string.manual),
                    ).forEach { (mode, label) ->
                        val selected = pricingMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = FolioMinimumTouchTarget)
                                .clip(FolioShapeTokens.small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent,
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { pricingMode = mode },
                                )
                                .padding(horizontal = FolioSpacing.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                if (pricingMode == PortfolioCodes.MARKET) {
                    val initialMarketSelection = remember(provider, providerSymbol, symbol, name, assetType) {
                        providerSymbol.takeIf(String::isNotBlank)?.let { identifier ->
                            MarketSelection(
                                key = MarketKey(provider, identifier),
                                displayName = name.ifBlank { symbol.ifBlank { identifier } },
                                symbol = symbol.ifBlank { identifier },
                                assetType = assetType,
                            )
                        }
                    }
                    MarketInstrumentPicker(
                        assetType = assetType,
                        initialSelection = initialMarketSelection,
                        onSelectionChanged = { selection ->
                            if (selection != null) {
                                provider = selection.key.provider
                                providerSymbol = selection.key.providerSymbol
                                symbol = selection.symbol
                            }
                        },
                        onProviderChanged = { provider = it },
                        onProviderSymbolChanged = { providerSymbol = it },
                        onManualSelectionValidated = { selection ->
                            provider = selection.key.provider
                            providerSymbol = selection.key.providerSymbol
                        },
                        initialSelectionValidated = id != null,
                    )
                } else {
                    Choice(
                        stringResource(R.string.manual_price_currency),
                        currency,
                        listOf(PortfolioCodes.IRT, PortfolioCodes.USD, PortfolioCodes.USDT),
                    ) { currency = it }
                }
            }

            if (id == null) {
                AssetFormSection(
                    title = stringResource(R.string.custody_opening_balance),
                    step = 3,
                    totalSteps = totalSteps,
                    marker = "▣",
                ) {
                    Text(
                        text = stringResource(R.string.opening_holding_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FolioSelector(
                            options = listOf("0") + locations.map { it.id.toString() },
                            selected = openingLocation.toString(),
                            onSelected = { openingLocation = it.toLongOrNull() ?: 0L },
                            label = stringResource(R.string.location),
                            modifier = Modifier.weight(1f),
                            optionLabel = { value ->
                                if (value == "0") noLocation
                                else locations.firstOrNull { it.id.toString() == value }?.name ?: noLocation
                            },
                        )
                        TextButton(onClick = {
                            locationError = ""
                            showLocationSheet = true
                        }) {
                            Text(stringResource(R.string.new_location))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
                        FolioField(
                            value = openingQuantity,
                            onValueChange = { openingQuantity = it },
                            label = stringResource(R.string.opening_quantity),
                            placeholder = stringResource(R.string.optional),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        FolioField(
                            value = openingAverage,
                            onValueChange = { openingAverage = it },
                            label = stringResource(R.string.average_cost),
                            placeholder = stringResource(R.string.optional),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    if (pricingMode == PortfolioCodes.MARKET) {
                        Choice(
                            stringResource(R.string.opening_cost_currency),
                            currency,
                            listOf(PortfolioCodes.IRT, PortfolioCodes.USD, PortfolioCodes.USDT),
                        ) { currency = it }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = FolioShapeTokens.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(FolioSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                        ) {
                            Text(
                                text = stringResource(R.string.initial_book_value),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            FolioFinancialValue(
                                value = "${formattedNumber(openingBookValue, "", 2, 0)} $currency",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.zero_fees),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            AssetFormSection(
                title = stringResource(R.string.portfolio_integration),
                step = if (id == null) 4 else 3,
                totalSteps = totalSteps,
                marker = "✓",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FolioShapeTokens.medium)
                        .clickable { active = !active }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = FolioSpacing.medium, vertical = FolioSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
                    ) {
                        Text(
                            text = stringResource(R.string.asset_is_active),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.include_in_net_worth),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Text(
                    text = stringResource(R.string.tags),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_tags_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTagIds,
                                onClick = {
                                    selectedTagIds = if (tag.id in selectedTagIds) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text(tag.name) },
                                modifier = Modifier.heightIn(min = FolioMinimumTouchTarget),
                                colors = accentChipColors,
                            )
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                FolioStateMessage(
                    message = error,
                    kind = FolioMessageKind.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FolioPrimaryButton(
                text = if (id == null) stringResource(R.string.save_asset_to_portfolio)
                else stringResource(R.string.save),
                onClick = {
                    error = ""
                    val asset = AssetEntity(
                        id ?: 0,
                        name,
                        symbol.uppercase(),
                        assetType,
                        unit,
                        pricingMode,
                        if (pricingMode == PortfolioCodes.MANUAL) PortfolioCodes.MANUAL else provider,
                        providerSymbol,
                        currency,
                        active,
                    )
                    when {
                        name.isBlank() -> error = nameRequired
                        symbol.isBlank() -> error = symbolRequired
                        pricingMode == PortfolioCodes.MARKET &&
                            (provider.isBlank() || providerSymbol.isBlank()) -> error = marketRequired
                        id == null -> vm.saveAssetWithOpening(
                            asset = asset,
                            tags = selectedTagIds.toList(),
                            quantity = openingQuantity,
                            averageCost = openingAverage,
                            locationId = openingLocation.takeIf { it != 0L },
                            onError = { error = it },
                            done = { onDone() },
                        )
                        else -> vm.saveAsset(
                            asset,
                            selectedTagIds.toList(),
                            onError = { error = it },
                            done = onDone,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = saveEnabled,
                leadingIcon = FolioIcons.AccountBalanceWallet,
            )
            FolioSecondaryButton(
                text = stringResource(R.string.discard_changes),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = FolioShapeTokens.medium,
            ) {
                Row(
                    modifier = Modifier.padding(FolioSpacing.medium),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                ) {
                    Icon(
                        imageVector = FolioIcons.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(FolioIconSize.medium),
                    )
                    Text(
                        text = stringResource(R.string.form_local_storage_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showLocationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLocationSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FolioSpacing.large)
                    .padding(bottom = FolioSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                ) {
                    Icon(
                        imageVector = FolioIcons.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.new_location),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (locations.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.existing_storage_pools),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
                    ) {
                        locations.forEach { location ->
                            AssistChip(
                                onClick = {
                                    openingLocation = location.id
                                    showLocationSheet = false
                                },
                                label = { Text(location.name) },
                            )
                        }
                    }
                }
                FolioField(
                    value = locationName,
                    onValueChange = {
                        locationName = it
                        locationError = ""
                    },
                    label = stringResource(R.string.location_name),
                    placeholder = stringResource(R.string.name),
                    modifier = Modifier.fillMaxWidth(),
                    isError = locationError.isNotBlank(),
                    supportingText = locationError.takeIf(String::isNotBlank),
                    singleLine = true,
                )
                FolioField(
                    value = locationNotes,
                    onValueChange = { locationNotes = it },
                    label = stringResource(R.string.private_notes_optional),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
                    FolioPrimaryButton(
                        text = stringResource(R.string.save_location),
                        onClick = {
                            if (locationName.isBlank()) {
                                locationError = nameRequired
                            } else {
                                vm.saveLocationWithId(
                                    name = locationName,
                                    notes = locationNotes,
                                    onError = { locationError = it },
                                ) { savedId ->
                                    openingLocation = savedId
                                    locationName = ""
                                    locationNotes = ""
                                    locationError = ""
                                    showLocationSheet = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                    )
                    FolioSecondaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showLocationSheet = false },
                        enabled = !isSaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionForm(
    vm: MainViewModel,
    fixedAsset: Long?,
    editId: Long? = null,
    done: () -> Unit,
) {
    val assets by vm.assets.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val existingTransaction = transactions.firstOrNull { it.id == editId }
    val isSaving by vm.isSaving.collectAsStateWithLifecycle()

    var assetId by rememberSaveable(editId, fixedAsset) { mutableLongStateOf(fixedAsset ?: 0L) }
    var transactionType by rememberSaveable(editId, fixedAsset) { mutableStateOf(PortfolioCodes.BUY) }
    var quantity by rememberSaveable(editId, fixedAsset) { mutableStateOf("") }
    var price by rememberSaveable(editId, fixedAsset) { mutableStateOf("") }
    var fee by rememberSaveable(editId, fixedAsset) { mutableStateOf("") }
    var currency by rememberSaveable(editId, fixedAsset) { mutableStateOf(PortfolioCodes.IRT) }
    var locationId by rememberSaveable(editId, fixedAsset) { mutableStateOf(0L) }
    var notes by rememberSaveable(editId, fixedAsset) { mutableStateOf("") }
    var error by rememberSaveable(editId, fixedAsset) { mutableStateOf("") }

    val noLocation = stringResource(R.string.no_location)
    val selectAsset = stringResource(R.string.select_asset)
    val assetRequiredError = stringResource(R.string.error_asset_required)
    val quantityPositiveError = stringResource(R.string.error_quantity_positive)
    val invalidPriceError = stringResource(R.string.error_price_invalid)
    val invalidFeeError = stringResource(R.string.error_fee_invalid)
    val buyLabel = stringResource(R.string.buy)
    val sellLabel = stringResource(R.string.sell)

    var initialized by rememberSaveable(editId, fixedAsset) { mutableStateOf(editId == null) }
    LaunchedEffect(existingTransaction) {
        if (!initialized && existingTransaction != null) {
            val transaction = existingTransaction
            assetId = transaction.assetId
            transactionType = transaction.transactionType
            quantity = transaction.quantity.toPlainString()
            price = transaction.pricePerUnit?.toPlainString().orEmpty()
            fee = transaction.fee?.toPlainString().orEmpty()
            currency = transaction.transactionCurrency
            locationId = transaction.locationId ?: 0L
            notes = transaction.notes
            initialized = true
        }
    }

    if (!initialized) return Page(
        title = stringResource(R.string.edit_transaction_title),
        showHeading = false,
        modifier = Modifier.imePadding(),
    ) {
        FolioStateMessage(
            message = stringResource(R.string.loading),
            kind = FolioMessageKind.Loading,
        )
    }

    FormPage(
        title = stringResource(
            if (editId == null) R.string.add_transaction_title else R.string.edit_transaction_title,
        ),
        error = error,
        enabled = !isSaving && (editId == null || existingTransaction != null),
        done = {
            val parsedQuantity = quantity.trim().toBigDecimalOrNull()
            val parsedPrice = price.trim().toBigDecimalOrNull()
            val parsedFee = fee.trim().toBigDecimalOrNull()
            when {
                assetId == 0L -> error = assetRequiredError
                parsedQuantity == null || parsedQuantity <= BigDecimal.ZERO -> {
                    error = quantityPositiveError
                }
                price.isNotBlank() && (parsedPrice == null || parsedPrice < BigDecimal.ZERO) -> {
                    error = invalidPriceError
                }
                fee.isNotBlank() && (parsedFee == null || parsedFee < BigDecimal.ZERO) -> {
                    error = invalidFeeError
                }
                else -> vm.saveTransaction(
                    TransactionEntity(
                        id = editId ?: 0,
                        assetId = assetId,
                        transactionType = transactionType,
                        quantity = parsedQuantity,
                        pricePerUnit = parsedPrice,
                        fee = parsedFee,
                        transactionCurrency = currency,
                        locationId = locationId.takeIf { it != 0L },
                        executedAt = existingTransaction?.executedAt ?: Instant.now(),
                        notes = notes,
                    ),
                    onError = { error = it },
                    done = done,
                )
            }
        },
    ) {
        if (fixedAsset == null && editId == null) {
            Choice(
                label = stringResource(R.string.asset),
                selected = assetId.toString(),
                options = assets.map { it.id.toString() },
                render = { value ->
                    assets.firstOrNull { it.id.toString() == value }
                        ?.let { "${it.symbol} · ${it.name}" }
                        ?: selectAsset
                },
                select = { assetId = it.toLong() },
            )
        } else {
            DetailLine(
                stringResource(R.string.asset),
                assets.firstOrNull { it.id == assetId }?.let { "${it.symbol} · ${it.name}" } ?: selectAsset,
            )
        }
        Choice(
            label = stringResource(R.string.type),
            selected = transactionType,
            options = listOf(PortfolioCodes.BUY, PortfolioCodes.SELL),
            render = { if (it == PortfolioCodes.BUY) buyLabel else sellLabel },
            select = { transactionType = it },
        )
        Field(stringResource(R.string.quantity), quantity, keyboardType = KeyboardType.Decimal) { quantity = it }
        Field(stringResource(R.string.price_per_unit), price, keyboardType = KeyboardType.Decimal) { price = it }
        Field(stringResource(R.string.fee), fee, keyboardType = KeyboardType.Decimal) { fee = it }
        Choice(
            label = stringResource(R.string.transaction_currency),
            selected = currency,
            options = listOf(PortfolioCodes.IRT, PortfolioCodes.USD, PortfolioCodes.USDT),
            select = { currency = it },
        )
        Choice(
            label = stringResource(R.string.location),
            selected = locationId.toString(),
            options = listOf("0") + locations.map { it.id.toString() },
            render = { value ->
                if (value == "0") noLocation
                else locations.firstOrNull { it.id.toString() == value }?.name ?: noLocation
            },
            select = { locationId = it.toLong() },
        )
        Field(stringResource(R.string.notes), notes) { notes = it }
    }
}
@Composable private fun PriceForm(vm:MainViewModel,id:Long,done:()->Unit){val isSaving by vm.isSaving.collectAsStateWithLifecycle();var price by rememberSaveable(id){mutableStateOf("")};var currency by rememberSaveable(id){mutableStateOf(PortfolioCodes.IRT)};var error by rememberSaveable(id){mutableStateOf("")};val invalidPrice=stringResource(R.string.error_price_invalid);FormPage(stringResource(R.string.update_price),error,enabled=!isSaving,done={if(price.toBigDecimalOrNull()==null)error=invalidPrice else vm.savePrice(id,price,currency,{error=it}){done()}}){Field(stringResource(R.string.price),price){price=it};Choice(stringResource(R.string.currency),currency,listOf(PortfolioCodes.IRT,PortfolioCodes.USD,PortfolioCodes.USDT)){currency=it};Tiny(stringResource(R.string.price_snapshot_hint))}}
@Composable
private fun Settings(
    appearanceLanguage: () -> Unit,
    tags: () -> Unit,
    locations: () -> Unit,
) {
    Page(stringResource(R.string.nav_more), subtitle = null, showHeading = false) {
        PanelCard(Modifier.fillMaxWidth()) {
            FolioListRow(
                modifier = Modifier.fillMaxWidth(),
                onClick = appearanceLanguage,
            ) {
                Text(stringResource(R.string.appearance_language), fontWeight = FontWeight.Bold)
                Tiny(stringResource(R.string.appearance_language_hint))
            }
        }
        Spacer(Modifier.height(10.dp))
        PanelCard(Modifier.fillMaxWidth()) {
            FolioListRow(
                modifier = Modifier.fillMaxWidth(),
                onClick = tags,
            ) {
                Text(stringResource(R.string.tags), fontWeight = FontWeight.Bold)
                Tiny(stringResource(R.string.tags_hint))
            }
        }
        Spacer(Modifier.height(10.dp))
        PanelCard(Modifier.fillMaxWidth()) {
            FolioListRow(
                modifier = Modifier.fillMaxWidth(),
                onClick = locations,
            ) {
                Text(stringResource(R.string.locations), fontWeight = FontWeight.Bold)
                Tiny(stringResource(R.string.locations_hint))
            }
        }
        Spacer(Modifier.height(20.dp));Tiny(stringResource(R.string.local_data_hint))
    }
}

@Composable
private fun AppearanceLanguageSettings(
    languagePreferences: LanguagePreferences,
    theme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    var language by remember { mutableStateOf(languagePreferences.current()) }
    Page(stringResource(R.string.appearance_language), subtitle = null, showHeading = false) {
        PanelCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.appearance), fontWeight = FontWeight.Bold)
            Tiny(stringResource(R.string.appearance_hint))
            Spacer(Modifier.height(8.dp))
            SingleChoiceRow(
                theme,
                listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.SYSTEM),
                render = { item ->
                    stringResource(
                        when (item) {
                            AppTheme.DARK -> R.string.theme_dark
                            AppTheme.LIGHT -> R.string.theme_light
                            AppTheme.SYSTEM -> R.string.theme_system
                        },
                    )
                },
                select = onThemeSelected,
            )

            Spacer(Modifier.height(FolioSpacing.medium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            Spacer(Modifier.height(FolioSpacing.medium))

            Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
            Tiny(stringResource(R.string.language_hint))
            Spacer(Modifier.height(8.dp))
            SingleChoiceRow(language, AppLanguage.entries.toList(), render = { item ->
                stringResource(if (item == AppLanguage.PERSIAN) R.string.language_persian else R.string.language_english)
            }) { selected ->
                language = selected
                languagePreferences.set(selected)
            }
        }
    }
}
@Composable
private fun Manage(
    title: String,
    entries: List<String>,
    save: (String, (String) -> Unit, () -> Unit) -> Unit,
    enabled: Boolean,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    FormPage(title, error, button = stringResource(R.string.add), enabled = enabled, done = {
        if (text.isNotBlank()) {
            error = ""
            save(text, { error = it }) { text = "" }
        }
    }) {
        Field(stringResource(R.string.name), text) { text = it }
        entries.forEach { DetailLine(it, "") }
        if (entries.isEmpty()) Tiny(stringResource(R.string.no_entries))
    }
}

@Composable
private fun Locations(vm: MainViewModel) {
    val entries by vm.locations.collectAsStateWithLifecycle()
    val isSaving by vm.isSaving.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    FormPage(stringResource(R.string.locations), error, button = stringResource(R.string.add), enabled = !isSaving, done = {
        if (name.isNotBlank()) {
            error = ""
            vm.saveLocation(name, notes, onError = { error = it }) { name = ""; notes = "" }
        }
    }) {
        Field(stringResource(R.string.name), name) { name = it }
        Field(stringResource(R.string.notes), notes) { notes = it }
        entries.forEach { DetailLine(it.name, it.notes) }
    }
}
@Composable
private fun FormPage(
    title: String,
    error: String,
    button: String? = null,
    enabled: Boolean = true,
    done: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = Page(
    title = title,
    showHeading = false,
    modifier = Modifier.imePadding(),
) {
    PanelCard(Modifier.fillMaxWidth()) {
        content()
        if (error.isNotBlank()) {
            FolioStateMessage(
                message = error,
                kind = FolioMessageKind.Error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(FolioSpacing.medium))
        FolioPrimaryButton(
            text = button ?: stringResource(R.string.save),
            onClick = done,
            modifier = Modifier.align(Alignment.End),
            enabled = enabled,
        )
    }
}
@Composable
private fun Field(
    label: String,
    value: String,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    change: (String) -> Unit,
) {
    FolioField(
        value = value,
        onValueChange = change,
        label = label,
        modifier = Modifier.fillMaxWidth().padding(bottom = FolioSpacing.small),
        placeholder = hint.takeIf(String::isNotBlank),
        singleLine = label != stringResource(R.string.notes),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
@Composable
private fun choiceLabel(value: String): String = when (value) {
    PortfolioCodes.CRYPTO, PortfolioCodes.USD, PortfolioCodes.IRAN_STOCK, PortfolioCodes.US_STOCK, PortfolioCodes.GOLD, PortfolioCodes.SILVER, PortfolioCodes.FIXED_INCOME, PortfolioCodes.MANUAL -> typeLabel(value)
    PortfolioCodes.UNIT -> stringResource(R.string.unit_count)
    PortfolioCodes.GRAM -> stringResource(R.string.unit_gram)
    PortfolioCodes.MARKET -> stringResource(R.string.pricing_market)
    PortfolioCodes.ABANTETHER -> "Aban Tether"
    else -> value
}

@Composable
private fun Choice(
    label: String,
    selected: String,
    options: List<String>,
    render: @Composable (String) -> String = { value ->
        if (value in setOf(PortfolioCodes.IRT, PortfolioCodes.USD, PortfolioCodes.USDT)) {
            isolateFinancialText(value)
        } else {
            value
        }
    },
    select: (String) -> Unit,
) = FolioSelector(
    options = options,
    selected = selected,
    onSelected = select,
    label = label,
    modifier = Modifier.fillMaxWidth().padding(bottom = FolioSpacing.small),
    optionLabel = render,
)

@Composable
private fun <T> SingleChoiceRow(
    selected: T,
    values: List<T>,
    render: @Composable (T) -> String,
    select: (T) -> Unit,
) = FolioChoiceChips(
    options = values,
    selected = selected,
    onSelected = select,
    optionLabel = render,
)
@Composable
private fun Empty(
    title: String,
    copy: String,
    actionLabel: String = stringResource(R.string.add_asset),
    action: () -> Unit,
) = PanelCard(Modifier.fillMaxWidth()) {
    FolioStateMessage(message = copy, title = title, kind = FolioMessageKind.Empty)
    Spacer(Modifier.height(FolioSpacing.small))
    FolioPrimaryButton(actionLabel, action)
}
