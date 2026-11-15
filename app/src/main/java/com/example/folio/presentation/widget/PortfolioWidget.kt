package com.example.folio.presentation.widget

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.example.folio.FolioApplication
import com.example.folio.data.network.PriceRefreshWorker
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetPortfolioSnapshot
import kotlinx.coroutines.CancellationException

private data class PortfolioWidgetRenderState(
    val configuration: WidgetConfiguration?,
    val snapshot: WidgetPortfolioSnapshot?,
    val loadFailed: Boolean,
)

class PortfolioWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? FolioApplication
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialState = loadPortfolioWidgetState(app, appWidgetId)

        provideContent {
            val revision by WidgetUpdateCoordinator.observeRequestedRevision(context)
                .collectAsState(initial = 0L)
            var state by remember(appWidgetId) { mutableStateOf(initialState) }
            LaunchedEffect(revision) {
                state = loadPortfolioWidgetState(app, appWidgetId)
            }
            val localizedContext = remember(revision) { widgetLocalizedContext(context) }
            val isUpdating by PriceRefreshWorker.observeRefreshInProgress(context)
                .collectAsState(initial = false)

            PortfolioWidgetLayout(
                context = localizedContext,
                appWidgetId = appWidgetId,
                configuration = state.configuration,
                snapshot = state.snapshot,
                isUpdating = isUpdating,
                loadFailed = state.loadFailed,
            )
        }
    }
}

private suspend fun loadPortfolioWidgetState(
    app: FolioApplication?,
    appWidgetId: Int,
): PortfolioWidgetRenderState {
    if (app == null) return PortfolioWidgetRenderState(null, null, loadFailed = true)

    return try {
        val configuration = app.widgetRepository.configuration(appWidgetId)
            ?.takeIf { it.kind == WidgetKind.PORTFOLIO }
        val snapshot = configuration?.let {
            app.widgetSnapshotRepository.portfolio(it.currency)
        }
        PortfolioWidgetRenderState(configuration, snapshot, loadFailed = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        PortfolioWidgetRenderState(null, null, loadFailed = true)
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetLifecycleWorker.enqueueSync(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshScheduler.ensurePeriodicWork(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetLifecycleWorker.enqueueDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetLifecycleWorker.enqueueRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetLifecycleWorker.enqueueSync(context)
    }
}
