package com.example.folio.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.await
import com.example.folio.FolioApplication
import com.example.folio.data.network.PriceRefreshWorker
import com.example.folio.data.widget.WidgetKind
import kotlinx.coroutines.CancellationException

internal val WIDGET_ID_ACTION_PARAMETER = ActionParameters.Key<Int>("appWidgetId")

internal fun togglePortfolioTotalAction(appWidgetId: Int): Action =
    actionRunCallback<TogglePortfolioTotalAction>(
        actionParametersOf(WIDGET_ID_ACTION_PARAMETER to appWidgetId),
    )

internal fun refreshWidgetAction(appWidgetId: Int): Action =
    actionRunCallback<RefreshWidgetAction>(
        actionParametersOf(WIDGET_ID_ACTION_PARAMETER to appWidgetId),
    )

@Composable
internal fun configureWidgetAction(appWidgetId: Int): Action =
    actionStartActivity<WidgetConfigurationActivity>(
        actionParametersOf(WIDGET_ID_ACTION_PARAMETER to appWidgetId),
    )

class TogglePortfolioTotalAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext as? FolioApplication ?: return
        val appWidgetId = widgetId(context, glanceId, parameters) ?: return
        val configuration = try {
            app.widgetRepository.configuration(appWidgetId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        } ?: return
        if (configuration.kind != WidgetKind.PORTFOLIO) return

        try {
            app.widgetRepository.setTotalHidden(appWidgetId, !configuration.hideTotal)
            WidgetUpdateCoordinator.update(context, setOf(appWidgetId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Keep the last rendered widget if durable state could not be updated.
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext as? FolioApplication ?: return
        val appWidgetId = widgetId(context, glanceId, parameters) ?: return
        val isConfigured = try {
            app.widgetRepository.configuration(appWidgetId) != null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
        if (!isConfigured) return

        try {
            PriceRefreshWorker.refreshNow(context).await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            WidgetUpdateCoordinator.update(context, setOf(appWidgetId))
            return
        }
        WidgetUpdateCoordinator.update(context, setOf(appWidgetId))
    }
}

private suspend fun widgetId(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
): Int? = parameters[WIDGET_ID_ACTION_PARAMETER]
    ?: runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
