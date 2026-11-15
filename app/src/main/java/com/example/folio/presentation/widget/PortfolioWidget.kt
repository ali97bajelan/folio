package com.example.folio.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.work.WorkManager
import com.example.folio.FolioApplication
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetConfiguration
import com.example.folio.data.widget.WidgetPortfolioSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PortfolioWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? FolioApplication
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val localizedContext = widgetLocalizedContext(context)

        var loadFailed = app == null
        var configuration: WidgetConfiguration? = null
        try {
            configuration = app?.widgetRepository?.configuration(appWidgetId)
                ?.takeIf { it.kind == WidgetKind.PORTFOLIO }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadFailed = true
        }

        var snapshot: WidgetPortfolioSnapshot? = null
        if (configuration != null) {
            try {
                snapshot = app?.widgetSnapshotRepository?.portfolio(configuration.currency)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                loadFailed = true
            }
        }
        val isUpdating = configuration != null && hasActivePortfolioRefresh(context)

        provideContent {
            PortfolioWidgetLayout(
                context = localizedContext,
                appWidgetId = appWidgetId,
                configuration = configuration,
                snapshot = snapshot,
                isUpdating = isUpdating,
                loadFailed = loadFailed,
            )
        }
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()
}

private suspend fun hasActivePortfolioRefresh(context: Context): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWork(com.example.folio.data.network.PriceRefreshWorker.IMMEDIATE_WORK_NAME)
                .get()
                .any { !it.state.isFinished }
        }.getOrDefault(false)
    }
