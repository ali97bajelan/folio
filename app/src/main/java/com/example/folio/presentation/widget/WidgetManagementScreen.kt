package com.example.folio.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.folio.FolioApplication
import com.example.folio.R
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ManagedWidgetEntry(
    val appWidgetId: Int,
    val kind: WidgetKind,
    val configuration: WidgetConfiguration?,
)

@Composable
internal fun WidgetManagementScreen(
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as FolioApplication }
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var widgets by remember { mutableStateOf<List<ManagedWidgetEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    val reloadWidgets by rememberUpdatedState(newValue = {
        scope.launch {
            isLoading = true
            loadError = false
            try {
                val providerKinds = listOf(
                    WidgetKind.ASSET_PRICE to AssetPriceWidgetReceiver::class.java,
                    WidgetKind.PORTFOLIO to PortfolioWidgetReceiver::class.java,
                )
                val installed = providerKinds.flatMap { (kind, receiver) ->
                    appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
                        .map { appWidgetId -> appWidgetId to kind }
                }
                val configurations = app.widgetRepository.configurations().associateBy { it.appWidgetId }
                widgets = installed
                    .map { (appWidgetId, kind) ->
                        ManagedWidgetEntry(
                            appWidgetId = appWidgetId,
                            kind = kind,
                            configuration = configurations[appWidgetId]?.takeIf { it.kind == kind },
                        )
                    }
                    .sortedWith(compareBy<ManagedWidgetEntry> { it.kind.ordinal }.thenBy { it.appWidgetId })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                loadError = true
            } finally {
                isLoading = false
            }
        }
    })

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadWidgets()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        reloadWidgets()
    }

    fun requestWidgetPin(kind: WidgetKind) {
        val receiver = when (kind) {
            WidgetKind.ASSET_PRICE -> AssetPriceWidgetReceiver::class.java
            WidgetKind.PORTFOLIO -> PortfolioWidgetReceiver::class.java
        }
        val accepted = appWidgetManager.isRequestPinAppWidgetSupported &&
            appWidgetManager.requestPinAppWidget(ComponentName(context, receiver), null, null)
        if (!accepted) onMessage(context.getString(R.string.widget_pin_unsupported))
    }

    fun openConfiguration(entry: ManagedWidgetEntry) {
        val intent = Intent(context, WidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, entry.appWidgetId)
        configureLauncher.launch(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_management_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.widget_asset_price_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.widget_asset_price_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { requestWidgetPin(WidgetKind.ASSET_PRICE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.widget_add_price))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.widget_portfolio_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.widget_portfolio_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { requestWidgetPin(WidgetKind.PORTFOLIO) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.widget_add_portfolio))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.widget_installed_title),
            style = MaterialTheme.typography.titleLarge,
        )

        when {
            isLoading -> CircularProgressIndicator()
            loadError -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.widget_management_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { reloadWidgets() }) {
                    Text(stringResource(R.string.retry))
                }
            }
            widgets.isEmpty() -> Text(
                text = stringResource(R.string.widget_no_installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> widgets.forEach { entry ->
                ManagedWidgetCard(entry = entry, onConfigure = { openConfiguration(entry) })
            }
        }
    }
}

@Composable
private fun ManagedWidgetCard(
    entry: ManagedWidgetEntry,
    onConfigure: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (entry.kind == WidgetKind.ASSET_PRICE) {
                                R.string.widget_asset_price_name
                            } else {
                                R.string.widget_portfolio_name
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.widget_instance_label, entry.appWidgetId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onConfigure) {
                    Text(stringResource(R.string.widget_configure_existing))
                }
            }

            val configuration = entry.configuration
            if (configuration == null) {
                Text(
                    text = stringResource(R.string.widget_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (entry.kind == WidgetKind.ASSET_PRICE) {
                Text(
                    text = configuration.markets.joinToString(separator = " · ") {
                        "${it.displayName} (${it.symbol})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(
                        if (configuration.hideTotal) {
                            R.string.widget_portfolio_hide_total
                        } else {
                            R.string.widget_portfolio_show_total
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
