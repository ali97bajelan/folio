package com.example.folio.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.example.folio.presentation.icons.FolioIcons
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Navy = Color(0xFF0B1020)
private val Panel = Color(0xFF131B2B)
private val PanelHi = Color(0xFF172137)
private val Blue = Color(0xFF668FFC)
private val Aqua = Color(0xFF76D8B0)
private val Muted = Color(0xFF8F9CB1)
private val Danger = Color(0xFFF47783)
private val AppShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)
private val dateFormat = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withLocale(Locale.US)
    .withZone(ZoneId.systemDefault())
private val relativeLastUpdatedWindow = Duration.ofDays(3)

/** Keeps Latin financial notation in its natural order inside RTL Persian text. */
private fun ltrValue(value: String) = "\u2066$value\u2069"

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
        else -> return number(value, "IRT", 0)
    }
    // Financial notation stays identical in Persian and English.  Let the
    // formatter omit insignificant trailing zeros: 1.20 B -> 1.2 B, 1.00 B -> 1 B.
    return ltrValue("${formattedNumber(value.divide(divisor, places, RoundingMode.DOWN), "", places, 0)} $label IRT")
}

@Composable private fun money(value: BigDecimal?, currency: String) =
    if (currency == "IRT") compact(value) else number(value, currency)

@Composable private fun wholeUsd(value: BigDecimal?) = number(value?.setScale(0, RoundingMode.HALF_UP), "USD", places = 0)

@Composable private fun typeLabel(value: String) = when (value) {
    "FIXED_INCOME" -> stringResource(R.string.type_fixed_income)
    "USD" -> stringResource(R.string.type_usd)
    "CRYPTO" -> stringResource(R.string.type_crypto)
    "IRAN_STOCK" -> stringResource(R.string.type_iran_stock)
    "US_STOCK" -> stringResource(R.string.type_us_stock)
    "GOLD" -> stringResource(R.string.type_gold)
    "SILVER" -> stringResource(R.string.type_silver)
    "MANUAL" -> stringResource(R.string.type_manual)
    else -> value
}

private fun defaultProviderFor(assetType: String) = when (assetType) {
    "IRAN_STOCK" -> "TSETMC"
    "US_STOCK" -> "ABANTETHER"
    "GOLD", "SILVER" -> "RAHAVARD"
    else -> "NOBITEX"
}

private fun NavBackStackEntry.longArgument(name: String): Long? =
    arguments?.getString(name)?.toLongOrNull()

@Composable
fun PortfolioApp(
    factory: ViewModelProvider.Factory,
    languagePreferences: LanguagePreferences,
) {
    val viewModel: MainViewModel = viewModel(factory = factory)
    val navigationController = rememberNavController()
    val entry by navigationController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: "dashboard"

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            secondary = Aqua,
            surface = Panel,
            background = Navy,
            error = Danger,
        ),
        shapes = Shapes(
            extraSmall = AppShape,
            small = AppShape,
            medium = AppShape,
            large = AppShape,
            extraLarge = AppShape,
        ),
    ) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                AppNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navigationController.navigate(route) {
                            popUpTo("dashboard")
                            launchSingleTop = true
                        }
                    },
                )
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
                        open = { navigationController.navigate("asset/$it") },
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
                        tags = { navigationController.navigate("tags") },
                        locations = { navigationController.navigate("locations") },
                        languagePreferences = languagePreferences,
                    )
                }
                composable("tags") {
                    Manage(
                        stringResource(R.string.tags),
                        viewModel.tags.collectAsStateWithLifecycle().value.map { it.name },
                        viewModel::saveTag,
                    )
                }
                composable("locations") { Locations(viewModel) }
                composable("asset/new") { AssetForm(viewModel) { navigationController.popBackStack() } }
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
                        AssetForm(viewModel, id) { navigationController.popBackStack() }
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
private fun AppNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Triple("dashboard", stringResource(R.string.nav_dashboard), FolioIcons.Dashboard),
        Triple("assets", stringResource(R.string.nav_assets), FolioIcons.AccountBalanceWallet),
        Triple("transactions", stringResource(R.string.nav_transactions), FolioIcons.ReceiptLong),
        Triple("settings", stringResource(R.string.nav_more), Icons.Outlined.Settings),
    )

    NavigationBar(containerColor = Panel) {
        destinations.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Page(title:String,subtitle:String=stringResource(R.string.personal_wealth),actionAlignment: Alignment.Horizontal = Alignment.End,action:(@Composable () -> Unit)?=null,content:@Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=16.dp)) {
    var headerWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onSizeChanged { headerWidthPx = it.width }) {
        val heading: @Composable () -> Unit = {
            Column {
                Text(subtitle,color=Muted,style=MaterialTheme.typography.labelSmall)
                Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        // The dashboard has three fairly wide actions.  At medium widths, placing
        // them beside the title forces the primary button onto a second line where
        // it appears oversized and detached from the other actions.
        if (with(density) { headerWidthPx.toDp() } < 1_200.dp) {
            Column {
                heading()
                if (action != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,actionAlignment),verticalArrangement=Arrangement.spacedBy(10.dp)) { action() }
                }
            }
        } else {
            Row(verticalAlignment=Alignment.CenterVertically) {
                heading()
                if (action != null) FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp,actionAlignment),verticalArrangement=Arrangement.spacedBy(10.dp)) { action() }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    content()
}
@Composable private fun PanelCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit)=Card(modifier,colors=CardDefaults.cardColors(containerColor=PanelHi),shape=AppShape){Column(Modifier.padding(16.dp),content=content)}
@Composable private fun Tiny(t:String)=Text(t,color=Muted,style=MaterialTheme.typography.labelSmall)
@Composable private fun Pill(t:String,c:Color=Color(0xFFB8C7DF))=Surface(color=c.copy(alpha=.12f),shape=PillShape){Text(t,color=c,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp))}

@Composable private fun Dashboard(vm:MainViewModel,open:(Long)->Unit,addAsset:()->Unit,addTx:()->Unit){val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle();val dash by vm.dashboard.collectAsStateWithLifecycle();val history by vm.portfolioHistory.collectAsStateWithLifecycle();val dashboardLoadedAt=remember(dash){Instant.now()};var displayCurrency by rememberSaveable{mutableStateOf("IRT")};Page(stringResource(R.string.dashboard_title),action={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(vm::refreshPrices,Modifier.weight(1f),enabled=!isRefreshing,contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Outlined.Refresh,contentDescription=null,modifier=Modifier.size(15.dp));Spacer(Modifier.width(4.dp));Text(stringResource(R.string.refresh_prices),maxLines=1,overflow=TextOverflow.Ellipsis)};OutlinedButton(addTx,Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp)){Text("+ ${stringResource(R.string.transaction)}",maxLines=1,overflow=TextOverflow.Ellipsis)};OutlinedButton(addAsset,Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp)){Text("+ ${stringResource(R.string.asset)}",maxLines=1,overflow=TextOverflow.Ellipsis)}}}){PanelCard(Modifier.fillMaxWidth()){PortfolioValue(dash,displayCurrency,dashboardLoadedAt){displayCurrency=it}};Spacer(Modifier.height(14.dp));PanelCard(Modifier.fillMaxWidth()){PortfolioHistoryChart(history,displayCurrency)};Spacer(Modifier.height(14.dp));PanelCard(Modifier.fillMaxWidth()){AllocationCakeChart(dash.rows,displayCurrency)}}}

private data class AllocationSlice(val assetType: String, val value: BigDecimal)

@Composable
private fun AllocationCakeChart(rows: List<AssetRow>, currency: String) {
    val slices = rows.groupBy { it.asset.assetType }
        .map { (assetType, typeRows) ->
            val value = if (currency == "USD") {
                typeRows.mapNotNull { it.usdValue }.fold(BigDecimal.ZERO, BigDecimal::add)
            } else {
                typeRows.mapNotNull { it.tomanValue }.fold(BigDecimal.ZERO, BigDecimal::add)
            }
            AllocationSlice(assetType, value)
        }
        .filter { it.value.signum() > 0 }
        .sortedByDescending { it.value }
    val total = slices.fold(BigDecimal.ZERO) { sum, slice -> sum.add(slice.value) }
    val colors = listOf(Aqua, Blue, Color(0xFFF6B36A), Color(0xFFF47783), Color(0xFF64B8DF), Color(0xFFD6C46B))

    Text(stringResource(R.string.asset_allocation_by_class), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    if (slices.isEmpty() || total.signum() == 0) {
        Tiny(stringResource(R.string.unavailable))
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(148.dp)) {
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                // Do not shrink the arcs to create spacing: the panel colour then shows
                // through as dark cracks, which makes the pie look like it contains extra
                // slices.  Let the final segment absorb rounding so the circle closes cleanly.
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
                    useCenter = true,
                )
                startAngle += sweepAngle
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slices.forEachIndexed { index, slice ->
                val percentage = slice.value.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = colors[index % colors.size], shape = PillShape) {
                        Spacer(Modifier.size(10.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(typeLabel(slice.assetType), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val allocationValue = if (currency == "USD") {
                            formattedNumber(slice.value, "USD", 0, 0)
                        } else {
                            compact(slice.value)
                        }
                        Tiny(ltrValue("${formattedNumber(percentage, "%", 1, 0)} · $allocationValue"))
                    }
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
        Text(stringResource(R.string.total_portfolio_value), fontWeight = FontWeight.SemiBold)
        CurrencySelector(currency, onCurrencyChange)
    }

    val total = if (currency == "USD") dash.usdTotal else dash.tomanTotal
    val alternateTotal = if (currency == "USD") compact(dash.tomanTotal) else wholeUsd(dash.usdTotal)
    Spacer(Modifier.height(14.dp))
    Text(
        if (currency == "USD") wholeUsd(total) else compact(total),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "≈ $alternateTotal",
        color = Muted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Spacer(Modifier.height(16.dp))
    Surface(color = Panel.copy(alpha = .72f), shape = AppShape) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (dash.missing > 0) {
        Spacer(Modifier.height(12.dp))
        Surface(color = Danger.copy(alpha = .14f), shape = AppShape) {
            Text(
                stringResource(R.string.assets_waiting_for_price, dash.missing),
                color = Danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CurrencySelector(currency: String, onCurrencyChange: (String) -> Unit) {
    Surface(color = Panel.copy(alpha = .72f), shape = AppShape) {
        Row(Modifier.padding(3.dp)) {
            listOf("IRT", "USD").forEach { option ->
                val selected = currency == option
                Surface(
                    color = if (selected) Aqua.copy(alpha = .18f) else Color.Transparent,
                    shape = AppShape,
                    modifier = Modifier.clickable { onCurrencyChange(option) },
                ) {
                    Text(
                        option,
                        color = if (selected) Aqua else Muted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

private enum class HistoryRange(val duration: Duration?) { DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30)), ALL(null) }
@Composable private fun chartValue(value: BigDecimal, currency: String) =
    if (currency == "USD") wholeUsd(value) else compact(value)
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PortfolioHistoryChart(history:List<PortfolioHistoryPoint>,currency:String) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.DAY) }
    var selectedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = remember(history) { Instant.now() }
    val points = remember(history, range, currency) {
        history.filter { range.duration?.let { duration -> !it.capturedAt.isBefore(now.minus(duration)) } ?: true }
            .map { it.capturedAt to if (currency == "USD") it.usdValue else it.tomanValue }
    }
    Text(stringResource(R.string.portfolio_value_history), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HistoryRange.entries.forEach { item ->
            FilterChip(selected = range == item, onClick = { range = item }, label = { Text(when (item) {
                HistoryRange.DAY -> stringResource(R.string.chart_1_day); HistoryRange.WEEK -> stringResource(R.string.chart_1_week)
                HistoryRange.MONTH -> stringResource(R.string.chart_1_month); HistoryRange.ALL -> stringResource(R.string.chart_all_time)
            }) })
        }
    }
    Spacer(Modifier.height(12.dp))
    if (points.size < 2) { Tiny(stringResource(R.string.portfolio_history_empty)); return }
    val first = points.first().second
    val last = points.last().second
    val change = last.subtract(first)
    val percentChange = first.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }?.let { change.divide(it, 6, RoundingMode.HALF_UP).multiply(BigDecimal("100")) }
    val values = points.map { it.second }
    val low = values.minOrNull()!!
    val high = values.maxOrNull()!!
    val midpoint = low.add(high).divide(BigDecimal("2"))
    val selected = points.firstOrNull { it.first.toEpochMilli() == selectedAt } ?: points.last()
    val selectedIndex = points.indexOf(selected)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((if (change.signum() >= 0) "+" else "") + chartValue(change, currency), color = if (change.signum() >= 0) Aqua else Danger, fontWeight = FontWeight.SemiBold)
        percentChange?.let { Text("(${if (it.signum() >= 0) "+" else ""}${formattedNumber(it, "%", 1, 1)})", color = if (change.signum() >= 0) Aqua else Danger, style = MaterialTheme.typography.labelMedium) }
    }
    Row(Modifier.fillMaxWidth().height(156.dp)) {
        Column(Modifier.width(72.dp).fillMaxHeight().padding(end=6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Tiny(chartValue(high, currency)); Tiny(chartValue(midpoint, currency)); Tiny(chartValue(low, currency))
        }
        var chartWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { chartWidthPx = it.width }) {
            val tooltipWidth = 96.dp
            val selectedFraction = selectedIndex.toFloat() / (points.size - 1).toFloat()
            val tooltipX = with(density) {
                ((chartWidthPx - tooltipWidth.roundToPx()).coerceAtLeast(0) * selectedFraction).toDp()
            }
            Canvas(Modifier.fillMaxSize().padding(vertical=10.dp).pointerInput(points) {
                detectTapGestures { tap ->
                    val fraction = (tap.x / size.width).coerceIn(0f, 1f)
                    selectedAt = points[(fraction * (points.size - 1) + .5f).toInt()].first.toEpochMilli()
                }
            }) {
            val span = high.subtract(low).takeIf { it.compareTo(BigDecimal.ZERO) > 0 } ?: BigDecimal.ONE
            val left = 4.dp.toPx(); val right = size.width - 4.dp.toPx(); val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx(); val middle = (top + bottom) / 2
            drawLine(Muted.copy(alpha=.2f), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(right, top)); drawLine(Muted.copy(alpha=.2f), androidx.compose.ui.geometry.Offset(left, middle), androidx.compose.ui.geometry.Offset(right, middle)); drawLine(Muted.copy(alpha=.2f), androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom))
            fun xAt(index: Int) = left + (right - left) * index / (points.size - 1).toFloat()
            fun yAt(value: BigDecimal) = bottom - (value.subtract(low).toFloat() / span.toFloat()) * (bottom - top)
            val path = Path(); points.forEachIndexed { index, (_, value) -> if (index == 0) path.moveTo(xAt(index), yAt(value)) else path.lineTo(xAt(index), yAt(value)) }
            drawPath(path, Blue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val selectedX = xAt(selectedIndex); val selectedY = yAt(selected.second)
            drawCircle(Aqua, 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(selectedX, selectedY))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).offset(x = tooltipX).padding(top = 2.dp),
                color = Panel,
                shape = AppShape,
            ) { Text(chartValue(selected.second, currency), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal=6.dp, vertical=3.dp), maxLines = 1) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start=72.dp), horizontalArrangement=Arrangement.SpaceBetween) { Tiny(dateFormat.format(points.first().first)); Tiny(dateFormat.format(points.last().first)) }
}
@Composable private fun Metric(a:String,b:String,c:String,m:Modifier)=PanelCard(m){Tiny(a);Text(b,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold);Tiny(c)}
@Composable private fun AssetRowItem(r:AssetRow,open:(Long)->Unit){val values=if(r.tomanValue==null||r.usdValue==null)stringResource(R.string.asset_values_unavailable) else "${compact(r.tomanValue)} · ${wholeUsd(r.usdValue)}";ListItem(modifier=Modifier.fillMaxWidth().clickable{open(r.asset.id)},headlineContent={Text("${r.asset.symbol} · ${r.asset.name}",fontWeight=FontWeight.SemiBold)},supportingContent={Text("${number(r.quantity, places=4)}${if(r.asset.unit=="GRAM")" g" else ""}  ·  $values")},trailingContent={Pill(typeLabel(r.asset.assetType))})}
@Composable private fun Assets(vm:MainViewModel,open:(Long)->Unit,add:()->Unit){val data by vm.dashboard.collectAsStateWithLifecycle();val history by vm.assetTypeHistory.collectAsStateWithLifecycle();val valueOrder=stringResource(R.string.sort_value);val nameOrder=stringResource(R.string.sort_name);val quantityOrder=stringResource(R.string.sort_quantity);var order by remember{mutableStateOf(valueOrder)};val rows=remember(data.rows,order){when(order){nameOrder->data.rows.sortedBy{it.asset.name.lowercase()};quantityOrder->data.rows.sortedByDescending{it.quantity};else->data.rows}};Page(stringResource(R.string.nav_assets),actionAlignment=Alignment.Start,action={Button(add){Text(stringResource(R.string.add_asset))}}){SingleChoiceRow(order,listOf(valueOrder,nameOrder,quantityOrder)){order=it};Spacer(Modifier.height(10.dp));if(rows.isEmpty())Empty(title=stringResource(R.string.assets_empty_title),copy=stringResource(R.string.assets_empty_copy),action=add) else {PanelCard(Modifier.fillMaxWidth()){rows.forEachIndexed{index,item->AssetRowItem(item,open);if(index<rows.lastIndex)HorizontalDivider(color=Color.White.copy(.06f))}};Spacer(Modifier.height(14.dp));PanelCard(Modifier.fillMaxWidth()){AssetTypeHistoryChart(history)}}}}

private data class PercentageAxis(val lower: Float, val upper: Float, val ticks: List<Float>)

private fun percentageAxis(percentages: List<BigDecimal>): PercentageAxis {
    val minimum = percentages.minOf(BigDecimal::toFloat)
    val maximum = percentages.maxOf(BigDecimal::toFloat)
    val padding = if (minimum == maximum) (minimum * .1f).coerceAtLeast(5f) else 0f
    val rawLower = (minimum - padding).coerceAtLeast(0f)
    val rawUpper = (maximum + padding).coerceAtMost(100f)
    val roughStep = ((rawUpper - rawLower) / 6f).coerceAtLeast(.1f)
    val magnitude = java.lang.Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(roughStep.toDouble()))).toFloat()
    val step = when (roughStep / magnitude) {
        in 0f..1f -> magnitude
        in 1f..2f -> 2f * magnitude
        in 2f..5f -> 5f * magnitude
        else -> 10f * magnitude
    }
    val lower = (kotlin.math.floor(rawLower / step) * step).toFloat().coerceAtLeast(0f)
    val upper = (kotlin.math.ceil(rawUpper / step) * step).toFloat().coerceAtMost(100f)
    val count = ((upper - lower) / step).toInt().coerceAtLeast(1)
    return PercentageAxis(lower, upper, (0..count).map { lower + it * step })
}

private fun percentageLabel(value: Float): String =
    formattedNumber(BigDecimal.valueOf(value.toDouble()), "%", if (value % 1f == 0f) 0 else 1, 0)

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HistoryRange.entries.forEach { item ->
            FilterChip(selected = range == item, onClick = { range = item }, label = { Text(when (item) {
                HistoryRange.DAY -> stringResource(R.string.chart_1_day); HistoryRange.WEEK -> stringResource(R.string.chart_1_week)
                HistoryRange.MONTH -> stringResource(R.string.chart_1_month); HistoryRange.ALL -> stringResource(R.string.chart_all_time)
            }) })
        }
    }
    Spacer(Modifier.height(12.dp))
    if (points.size < 2) {
        Tiny(stringResource(R.string.asset_type_history_empty))
        return
    }
    val assetTypes = points.flatMap { it.values.keys }.distinct()
        .sortedByDescending { assetType -> points.last().values[assetType] ?: BigDecimal.ZERO }
    val selected = points.firstOrNull { it.capturedAt.toEpochMilli() == selectedAt } ?: points.last()
    val selectedIndex = points.indexOf(selected)
    val colors = listOf(Aqua, Blue, Color(0xFFF6B36A), Color(0xFFF47783), Color(0xFF64B8DF), Color(0xFFD6C46B), Color(0xFFC58CF0), Color(0xFF80C4A0))
    val percentages = points.map { point ->
        val total = point.values.values.fold(BigDecimal.ZERO, BigDecimal::add)
        assetTypes.associateWith { assetType ->
            if (total.signum() == 0) BigDecimal.ZERO else (point.values[assetType] ?: BigDecimal.ZERO)
                .divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        }
    }
    val axis = percentageAxis(percentages.flatMap { it.values })
    val selectedPercentages = percentages[selectedIndex]
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        assetTypes.forEachIndexed { index, assetType ->
            val percentage = selectedPercentages.getValue(assetType)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors[index % colors.size], shape = PillShape) { Spacer(Modifier.size(10.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(typeLabel(assetType), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ltrValue(formattedNumber(percentage, "%", 1, 0)), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().height(190.dp)) {
        Column(Modifier.width(44.dp).fillMaxHeight().padding(end=6.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            axis.ticks.asReversed().forEach { tick -> Tiny(ltrValue(percentageLabel(tick))) }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp).pointerInput(points) {
            detectTapGestures { tap ->
                val fraction = (tap.x / size.width).coerceIn(0f, 1f)
                selectedAt = points[(fraction * (points.size - 1) + .5f).toInt()].capturedAt.toEpochMilli()
            }
        }) {
            val left = 4.dp.toPx(); val right = size.width - 4.dp.toPx(); val top = 4.dp.toPx(); val bottom = size.height - 4.dp.toPx()
            fun xAt(index: Int) = left + (right - left) * index / (points.size - 1).toFloat()
            fun yAt(percentage: BigDecimal) = bottom - (percentage.toFloat() - axis.lower) / (axis.upper - axis.lower) * (bottom - top)
            axis.ticks.forEach { tick ->
                val y = yAt(BigDecimal.valueOf(tick.toDouble()))
                drawLine(Muted.copy(alpha = .2f), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y))
            }
            assetTypes.forEachIndexed { typeIndex, assetType ->
                val path = Path()
                percentages.forEachIndexed { index, pointPercentages ->
                    val percentage = pointPercentages.getValue(assetType)
                    if (index == 0) path.moveTo(xAt(index), yAt(percentage)) else path.lineTo(xAt(index), yAt(percentage))
                }
                drawPath(path, colors[typeIndex % colors.size], style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                val percentage = selectedPercentages.getValue(assetType)
                val center = androidx.compose.ui.geometry.Offset(xAt(selectedIndex), yAt(percentage))
                drawCircle(colors[typeIndex % colors.size].copy(alpha = .22f), 12.dp.toPx(), center)
                drawCircle(colors[typeIndex % colors.size], 5.dp.toPx(), center)
            }
        }
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

    Page(
        stringResource(R.string.nav_transactions),
        actionAlignment = Alignment.Start,
        action = { Button(add) { Text(stringResource(R.string.add_transaction)) } },
    ) {
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
                    val isPurchase = transaction.transactionType == "BUY"
                    val transactionLabel = stringResource(if (isPurchase) R.string.buy else R.string.sell)
                    val assetSymbol = assetsById[transaction.assetId]?.symbol
                        ?: stringResource(R.string.empty_value)

                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { edit(transaction.id) },
                        headlineContent = { Text("$transactionLabel · $assetSymbol") },
                        supportingContent = {
                            Text("${number(transaction.quantity)} · ${dateFormat.format(transaction.executedAt)}")
                        },
                        trailingContent = {
                            Pill(transactionLabel, if (isPurchase) Aqua else Danger)
                        },
                    )
                    if (index < transactions.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = .06f))
                    }
                }
            }
        }
    }
}

@Composable private fun AssetDetailScreen(vm:MainViewModel,id:Long,edit:()->Unit,price:()->Unit,tx:()->Unit,back:()->Unit){val x by remember(vm,id){vm.detail(id)}.collectAsStateWithLifecycle(initialValue=null);val locs by vm.locations.collectAsStateWithLifecycle();val dashboard by vm.dashboard.collectAsStateWithLifecycle();var confirm by remember{mutableStateOf(false)};
    var deleting by remember(vm,id){mutableStateOf(false)}
    var deleteError by remember(vm,id){mutableStateOf("")}
    val d=x?:return Page(stringResource(R.string.asset)){Text(stringResource(R.string.loading))};val row=dashboard.rows.firstOrNull{it.asset.id==id};Page(d.asset.name,action={TextButton(edit){Text(stringResource(R.string.edit))};if(d.asset.pricingMode=="MANUAL")Button(price){Text(stringResource(R.string.update_price))}}){if(confirm)AlertDialog(
        onDismissRequest={if(!deleting)confirm=false},
        title={Text(stringResource(R.string.delete_asset_title))},
        text={Column{Text(stringResource(R.string.delete_asset_message));if(deleteError.isNotBlank())Text(deleteError,color=Danger)}},
        confirmButton={Button(
            onClick={
                if(!deleting){
                    deleting=true
                    deleteError=""
                    vm.deleteAsset(d.asset,onError={deleteError=it;deleting=false},done={deleting=false;confirm=false;back()})
                }
            },
            enabled=!deleting,
            colors=ButtonDefaults.buttonColors(containerColor=Danger),
        ){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton(onClick={confirm=false},enabled=!deleting){Text(stringResource(R.string.cancel))}},
    );Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(tx){Text("+ ${stringResource(R.string.transaction)}")};OutlinedButton({confirm=true}){Text(stringResource(R.string.delete),color=Danger)}};Spacer(Modifier.height(14.dp));AssetMetrics(d,row?.tomanValue,row?.usdValue);Spacer(Modifier.height(14.dp));PanelCard(Modifier.fillMaxWidth()){Text(stringResource(R.string.asset_details),fontWeight=FontWeight.Bold);DetailLine(stringResource(R.string.symbol),d.asset.symbol);DetailLine(stringResource(R.string.type),typeLabel(d.asset.assetType));DetailLine(stringResource(R.string.pricing),if(d.asset.pricingMode=="MANUAL")stringResource(R.string.manual) else stringResource(R.string.market_provider,d.asset.priceProvider));DetailLine(stringResource(R.string.last_price),d.prices.firstOrNull()?.let{money(it.price,it.currency)}?:stringResource(R.string.unavailable))};Spacer(Modifier.height(14.dp));PanelCard(Modifier.fillMaxWidth()){Text(stringResource(R.string.holdings_by_location),fontWeight=FontWeight.Bold);d.byLocation.forEach{(loc,q)->DetailLine(loc?.let{key->locs.firstOrNull{it.id==key}?.name}?:stringResource(R.string.no_location),detailQuantity(q))};if(d.byLocation.isEmpty())Tiny(stringResource(R.string.no_holdings))};Spacer(Modifier.height(14.dp));Text(stringResource(R.string.nav_transactions),fontWeight=FontWeight.Bold);d.transactions.forEach{t->ListItem(headlineContent={Text(if(t.transactionType=="BUY")stringResource(R.string.buy) else stringResource(R.string.sell))},supportingContent={Text("${number(t.quantity)} · ${dateFormat.format(t.executedAt)}")},trailingContent={Pill(t.transactionCurrency)});HorizontalDivider(color=Color.White.copy(.06f))}}}
@Composable private fun AssetMetrics(d:AssetDetail,tomanValue:BigDecimal?,usdtValue:BigDecimal?) {
    var metricsWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onSizeChanged { metricsWidthPx = it.width }) {
        val cards:@Composable (Modifier)->Unit={modifier->Metric(stringResource(R.string.quantity),detailQuantity(d.costBasis.quantity),d.asset.unit,modifier);Metric(stringResource(R.string.value_irt),compact(tomanValue),"",modifier);Metric(stringResource(R.string.value_usdt),number(usdtValue,"USDT"),"",modifier);Metric(stringResource(R.string.average_cost),number(d.costBasis.average),"",modifier)}
        if (with(density) { metricsWidthPx.toDp() } < 480.dp) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { cards(Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { cards(Modifier.weight(1f)) }
    }
}
@Composable private fun DetailLine(a:String,b:String)=Row(Modifier.fillMaxWidth().padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(a,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));Text(b,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))}

@Composable private fun AssetForm(vm:MainViewModel,id:Long?=null,done:()->Unit){val isSaving by vm.isSaving.collectAsStateWithLifecycle();val old by (if(id==null) remember{mutableStateOf<AssetEntity?>(null)} else remember(vm,id){vm.asset(id)}.collectAsStateWithLifecycle(initialValue=null));val tagIds by (if(id==null) remember{mutableStateOf(emptyList<Long>())} else remember(vm,id){vm.assetTagIds(id)}.collectAsStateWithLifecycle(initialValue=emptyList()));val tags by vm.tags.collectAsStateWithLifecycle();val locations by vm.locations.collectAsStateWithLifecycle();val results by vm.instrumentResults.collectAsStateWithLifecycle();val searchError by vm.instrumentSearchError.collectAsStateWithLifecycle();var name by remember{mutableStateOf("")};var symbol by remember{mutableStateOf("")};var type by remember{mutableStateOf("CRYPTO")};var unit by remember{mutableStateOf("UNIT")};var mode by remember{mutableStateOf("MARKET")};var provider by remember{mutableStateOf("NOBITEX")};var providerSymbol by remember{mutableStateOf("")};var currency by remember{mutableStateOf("IRT")};var active by remember{mutableStateOf(true)};var selected by remember{mutableStateOf(setOf<Long>())};var openingLocation by remember{mutableStateOf(0L)};var openingQuantity by remember{mutableStateOf("")};var openingAverage by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val noLocation=stringResource(R.string.no_location);val nameRequired=stringResource(R.string.error_name_required);val symbolRequired=stringResource(R.string.error_symbol_required);val marketRequired=stringResource(R.string.error_market_selection_required);LaunchedEffect(old,tagIds){old?.let{a->name=a.name;symbol=a.symbol;type=a.assetType;unit=a.unit;mode=a.pricingMode;provider=a.priceProvider;providerSymbol=a.providerSymbol;currency=a.manualPriceCurrency;active=a.isActive;selected=tagIds.toSet()}};LaunchedEffect(id,type){if(id==null){provider=defaultProviderFor(type);providerSymbol=""}};LaunchedEffect(mode,provider,name,type){if(mode=="MARKET"&&name.trim().length>=2)vm.searchInstruments(provider,name,type) else vm.clearInstrumentSearch()};FormPage(if(id==null)stringResource(R.string.add_asset_title) else stringResource(R.string.edit_asset_title),error,enabled=!isSaving,done={val asset=AssetEntity(id?:0,name,symbol.uppercase(),type,unit,mode,if(mode=="MANUAL")"MANUAL" else provider,providerSymbol,currency,active);if(name.isBlank())error=nameRequired else if(symbol.isBlank())error=symbolRequired else if(mode=="MARKET"&&(provider.isBlank()||providerSymbol.isBlank()))error=marketRequired else if(id==null)vm.saveAssetWithOpening(asset,selected.toList(),openingQuantity,openingAverage,openingLocation.takeIf{it!=0L},{error=it}){done()} else vm.saveAsset(asset,selected.toList(),done)}){Field(stringResource(R.string.name),name){name=it;if(symbol.isBlank())symbol=it.uppercase().replace(' ','-')};Field(stringResource(R.string.symbol),symbol){symbol=it};Choice(stringResource(R.string.asset_type),type,listOf("CRYPTO","USD","IRAN_STOCK","US_STOCK","GOLD","SILVER","FIXED_INCOME","MANUAL")){type=it};Choice(stringResource(R.string.unit),unit,listOf("UNIT","GRAM")){unit=it};Choice(stringResource(R.string.pricing_method),mode,listOf("MANUAL","MARKET")){mode=it};if(mode=="MARKET"){Choice(stringResource(R.string.provider),provider,listOf("NOBITEX","ABANTETHER","TSETMC","RAHAVARD")){provider=it};Text(stringResource(R.string.suggested_markets),fontWeight=FontWeight.SemiBold);if(searchError!=null)Text(searchError!!,color=Danger);if(results.isEmpty()&&name.trim().length>=2&&searchError==null)Tiny(stringResource(R.string.searching_or_empty));results.forEach{item->ListItem(modifier=Modifier.fillMaxWidth().clickable{providerSymbol=item.providerSymbol;symbol=item.assetSymbol;vm.clearInstrumentSearch()},headlineContent={Text("${item.symbol} · ${item.name}")},supportingContent={Text(item.meta)});HorizontalDivider(color=Color.White.copy(.06f))};Field(stringResource(R.string.provider_symbol),providerSymbol,stringResource(R.string.provider_symbol_hint)){providerSymbol=it}}else Choice(stringResource(R.string.manual_price_currency),currency,listOf("IRT","USD","USDT")){currency=it};if(id==null){Text(stringResource(R.string.opening_holding),fontWeight=FontWeight.SemiBold);Tiny(stringResource(R.string.opening_holding_hint));Choice(stringResource(R.string.location),openingLocation.toString(),listOf("0")+locations.map{it.id.toString()},render={ v->if(v=="0")noLocation else locations.firstOrNull{it.id.toString()==v}?.name?:noLocation}){openingLocation=it.toLong()};Field(stringResource(R.string.opening_quantity),openingQuantity,stringResource(R.string.optional)){openingQuantity=it};Field(stringResource(R.string.average_purchase_price),openingAverage,stringResource(R.string.optional)){openingAverage=it}};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(active,{active=it});Text(stringResource(R.string.asset_is_active))};Text(stringResource(R.string.tags),fontWeight=FontWeight.SemiBold);tags.forEach{ tag->Row(Modifier.fillMaxWidth().toggleable(tag.id in selected){selected=if(tag.id in selected)selected-tag.id else selected+tag.id},verticalAlignment=Alignment.CenterVertically){Checkbox(tag.id in selected,null);Text(tag.name)}}}}

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

    var assetId by remember { mutableLongStateOf(fixedAsset ?: 0L) }
    var transactionType by remember { mutableStateOf("BUY") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("IRT") }
    var locationId by remember { mutableStateOf(0L) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val noLocation = stringResource(R.string.no_location)
    val selectAsset = stringResource(R.string.select_asset)
    val assetRequiredError = stringResource(R.string.error_asset_required)
    val quantityPositiveError = stringResource(R.string.error_quantity_positive)
    val invalidPriceError = stringResource(R.string.error_price_invalid)
    val invalidFeeError = stringResource(R.string.error_fee_invalid)
    val buyLabel = stringResource(R.string.buy)
    val sellLabel = stringResource(R.string.sell)

    LaunchedEffect(existingTransaction, fixedAsset) {
        if (existingTransaction != null) {
            val transaction = existingTransaction
            assetId = transaction.assetId
            transactionType = transaction.transactionType
            quantity = transaction.quantity.toPlainString()
            price = transaction.pricePerUnit?.toPlainString().orEmpty()
            fee = transaction.fee?.toPlainString().orEmpty()
            currency = transaction.transactionCurrency
            locationId = transaction.locationId ?: 0L
            notes = transaction.notes
        } else if (editId == null) {
            assetId = fixedAsset ?: 0L
        }
    }

    FormPage(
        title = stringResource(
            if (editId == null) R.string.add_transaction_title else R.string.edit_transaction_title,
        ),
        error = error,
        enabled = !isSaving,
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
            options = listOf("BUY", "SELL"),
            render = { if (it == "BUY") buyLabel else sellLabel },
            select = { transactionType = it },
        )
        Field(stringResource(R.string.quantity), quantity) { quantity = it }
        Field(stringResource(R.string.price_per_unit), price) { price = it }
        Field(stringResource(R.string.fee), fee) { fee = it }
        Choice(
            label = stringResource(R.string.transaction_currency),
            selected = currency,
            options = listOf("IRT", "USD", "USDT"),
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
@Composable private fun PriceForm(vm:MainViewModel,id:Long,done:()->Unit){val isSaving by vm.isSaving.collectAsStateWithLifecycle();var price by remember{mutableStateOf("")};var currency by remember{mutableStateOf("IRT")};var error by remember{mutableStateOf("")};val invalidPrice=stringResource(R.string.error_price_invalid);FormPage(stringResource(R.string.update_price),error,enabled=!isSaving,done={if(price.toBigDecimalOrNull()==null)error=invalidPrice else vm.savePrice(id,price,currency,{error=it}){done()}}){Field(stringResource(R.string.price),price){price=it};Choice(stringResource(R.string.currency),currency,listOf("IRT","USD","USDT")){currency=it};Tiny(stringResource(R.string.price_snapshot_hint))}}
@Composable private fun Settings(tags:()->Unit,locations:()->Unit,languagePreferences: LanguagePreferences) {
    var language by remember { mutableStateOf(languagePreferences.current()) }
    Page(stringResource(R.string.nav_more)) {
        PanelCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.language), fontWeight=FontWeight.Bold)
            Tiny(stringResource(R.string.language_hint))
            Spacer(Modifier.height(8.dp))
            SingleChoiceRow(language, AppLanguage.entries.toList(), render = { item ->
                stringResource(if (item == AppLanguage.PERSIAN) R.string.language_persian else R.string.language_english)
            }) { selected ->
                language = selected
                languagePreferences.set(selected)
            }
        }
        Spacer(Modifier.height(10.dp))
        PanelCard(Modifier.fillMaxWidth().clickable(onClick=tags)){Text(stringResource(R.string.tags),fontWeight=FontWeight.Bold);Tiny(stringResource(R.string.tags_hint))}
        Spacer(Modifier.height(10.dp))
        PanelCard(Modifier.fillMaxWidth().clickable(onClick=locations)){Text(stringResource(R.string.locations),fontWeight=FontWeight.Bold);Tiny(stringResource(R.string.locations_hint))}
        Spacer(Modifier.height(20.dp));Tiny(stringResource(R.string.local_data_hint))
    }
}
@Composable private fun Manage(title:String,entries:List<String>,save:(String)->Unit){var text by remember{mutableStateOf("")};FormPage(title,"",button=stringResource(R.string.add),done={if(text.isNotBlank()){save(text);text=""}}){Field(stringResource(R.string.name),text){text=it};entries.forEach{DetailLine(it,"")};if(entries.isEmpty())Tiny(stringResource(R.string.no_entries))}}
@Composable private fun Locations(vm:MainViewModel){val entries by vm.locations.collectAsStateWithLifecycle();var name by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};FormPage(stringResource(R.string.locations),"",button=stringResource(R.string.add),done={if(name.isNotBlank()){vm.saveLocation(name,notes);name="";notes=""}}){Field(stringResource(R.string.name),name){name=it};Field(stringResource(R.string.notes),notes){notes=it};entries.forEach{DetailLine(it.name,it.notes)}}}
@Composable private fun FormPage(title:String,error:String,button:String?=null,enabled:Boolean=true,done:()->Unit,content:@Composable ColumnScope.()->Unit)=Page(title){PanelCard(Modifier.fillMaxWidth()){content();if(error.isNotBlank())Text(error,color=Danger);Spacer(Modifier.height(14.dp));Button(done,Modifier.align(Alignment.End),enabled=enabled){Text(button ?: stringResource(R.string.save))}}}
@Composable private fun Field(label:String,value:String,hint:String="",change:(String)->Unit){OutlinedTextField(value,change,Modifier.fillMaxWidth().padding(bottom=10.dp),label={Text(label)},placeholder={if(hint.isNotBlank())Text(hint)},singleLine=label != stringResource(R.string.notes))}
@OptIn(ExperimentalMaterial3Api::class) @Composable private fun Choice(label:String,selected:String,options:List<String>,render:(String)->String={it},select:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=it},Modifier.fillMaxWidth().padding(bottom=10.dp)){OutlinedTextField(render(selected),{},Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});ExposedDropdownMenu(expanded,{expanded=false}){options.forEach{o->DropdownMenuItem({Text(render(o))},{select(o);expanded=false})}}}}
@Composable private fun SingleChoiceRow(selected:String,values:List<String>,select:(String)->Unit)=Row{values.forEach{v->FilterChip(selected==v,{select(v)},{Text(v)},Modifier.padding(end=6.dp))}}
@Composable private fun <T> SingleChoiceRow(selected:T,values:List<T>,render:@Composable (T)->String,select:(T)->Unit)=Row{values.forEach{v->FilterChip(selected==v,{select(v)},{Text(render(v))},Modifier.padding(end=6.dp))}}
@Composable
private fun Empty(
    title: String,
    copy: String,
    actionLabel: String = stringResource(R.string.add_asset),
    action: () -> Unit,
) = PanelCard(Modifier.fillMaxWidth()) {
    Text(title, fontWeight = FontWeight.Bold)
    Tiny(copy)
    Spacer(Modifier.height(10.dp))
    Button(action) { Text(actionLabel) }
}
