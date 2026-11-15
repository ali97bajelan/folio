package com.example.folio.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.example.folio.FolioApplication
import com.example.folio.data.network.WidgetPeriodicRefreshWorker
import com.example.folio.data.network.shouldSchedulePeriodicRefresh
import com.example.folio.data.widget.WidgetKind
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object WidgetRefreshScheduler {
    const val PERIODIC_WORK_NAME = "folio-widget-periodic-refresh"

    /**
     * Full reconciliation is reserved for startup/recovery after queued receiver callbacks. Restore
     * callbacks only remap their own IDs and schedule work, so one widget kind cannot prune another
     * kind's not-yet-remapped backup rows.
     */
    suspend fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? FolioApplication ?: return
        val installedKinds = installedWidgetKinds(appContext)
        val savedConfigurations = app.widgetRepository.configurations()
        val staleOrMismatchedIds = savedConfigurations
            .filter { configuration -> installedKinds[configuration.appWidgetId] != configuration.kind }
            .mapTo(mutableSetOf()) { it.appWidgetId }
        if (staleOrMismatchedIds.isNotEmpty()) {
            app.widgetRepository.remove(staleOrMismatchedIds)
        }

        updatePeriodicSchedule(appContext, installedKinds.keys)
        WidgetUpdateCoordinator.invalidateAll(appContext)
    }

    /** Schedule/cancel periodic work and refresh UI without pruning saved configuration rows. */
    suspend fun scheduleAndInvalidate(context: Context) {
        val appContext = context.applicationContext
        updatePeriodicSchedule(appContext, installedWidgetKinds(appContext).keys)
        WidgetUpdateCoordinator.invalidateAll(appContext)
    }

    /** Lightweight callback hook; onUpdate must not enqueue one-time work. */
    fun ensurePeriodicWork(context: Context) {
        val appContext = context.applicationContext
        val installedIds = installedWidgetKinds(appContext).keys
        val workManager = WorkManager.getInstance(appContext)
        if (shouldSchedulePeriodicRefresh(installedIds)) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest(),
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }

    private suspend fun updatePeriodicSchedule(context: Context, installedIds: Set<Int>) {
        val workManager = WorkManager.getInstance(context)
        if (shouldSchedulePeriodicRefresh(installedIds)) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest(),
            ).await()
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
        }
    }

    private fun installedWidgetKinds(context: Context): Map<Int, WidgetKind> {
        val manager = AppWidgetManager.getInstance(context)
        val portfolioIds = manager.getAppWidgetIds(
            ComponentName(context, PortfolioWidgetReceiver::class.java),
        )
        val priceIds = manager.getAppWidgetIds(
            ComponentName(context, AssetPriceWidgetReceiver::class.java),
        )
        return buildMap {
            portfolioIds.forEach { put(it, WidgetKind.PORTFOLIO) }
            priceIds.forEach { put(it, WidgetKind.ASSET_PRICE) }
        }
    }

    private fun periodicRequest() =
        PeriodicWorkRequestBuilder<WidgetPeriodicRefreshWorker>(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(connectedNetworkConstraints())
            .build()

    private fun connectedNetworkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val REFRESH_INTERVAL_HOURS = 6L
}

internal enum class WidgetLifecycleAction {
    SYNC,
    DELETE,
    RESTORE,
    RECONCILE,
}

internal fun requiresGlobalWidgetReconciliation(action: WidgetLifecycleAction): Boolean =
    action == WidgetLifecycleAction.RECONCILE

class WidgetLifecycleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FolioApplication ?: return Result.failure()
        val action = lifecycleAction(inputData.getString(KEY_ACTION)) ?: return Result.failure()
        return try {
            when (action) {
                WidgetLifecycleAction.DELETE -> {
                    val ids = inputData.getIntArray(KEY_WIDGET_IDS)?.toSet() ?: emptySet()
                    app.widgetRepository.remove(ids)
                }
                WidgetLifecycleAction.RESTORE -> {
                    val oldIds = inputData.getIntArray(KEY_OLD_WIDGET_IDS) ?: intArrayOf()
                    val newIds = inputData.getIntArray(KEY_NEW_WIDGET_IDS) ?: intArrayOf()
                    app.widgetRepository.remapIds(oldIds.zip(newIds).toMap())
                }
                WidgetLifecycleAction.SYNC,
                WidgetLifecycleAction.RECONCILE -> Unit
            }

            if (requiresGlobalWidgetReconciliation(action)) {
                WidgetRefreshScheduler.reconcile(applicationContext)
            } else {
                WidgetRefreshScheduler.scheduleAndInvalidate(applicationContext)
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val LIFECYCLE_WORK_NAME = "folio-widget-lifecycle"
        private const val KEY_ACTION = "action"
        private const val KEY_WIDGET_IDS = "widget_ids"
        private const val KEY_OLD_WIDGET_IDS = "old_widget_ids"
        private const val KEY_NEW_WIDGET_IDS = "new_widget_ids"
        private const val ACTION_SYNC = "sync"
        private const val ACTION_DELETE = "delete"
        private const val ACTION_RESTORE = "restore"
        private const val ACTION_RECONCILE = "reconcile"

        fun enqueueSync(context: Context) {
            enqueue(context, workDataOf(KEY_ACTION to ACTION_SYNC))
        }

        fun enqueueReconcile(context: Context) {
            enqueue(context, workDataOf(KEY_ACTION to ACTION_RECONCILE))
        }

        fun enqueueDeleted(context: Context, widgetIds: IntArray) {
            enqueue(context, workDataOf(KEY_ACTION to ACTION_DELETE, KEY_WIDGET_IDS to widgetIds))
        }

        fun enqueueRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
            enqueue(
                context,
                workDataOf(
                    KEY_ACTION to ACTION_RESTORE,
                    KEY_OLD_WIDGET_IDS to oldWidgetIds,
                    KEY_NEW_WIDGET_IDS to newWidgetIds,
                ),
            )
        }

        private fun enqueue(context: Context, input: Data) {
            val request = OneTimeWorkRequestBuilder<WidgetLifecycleWorker>()
                .setInputData(input)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .beginUniqueWork(
                    LIFECYCLE_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request,
                )
                .enqueue()
        }

        private fun lifecycleAction(value: String?): WidgetLifecycleAction? = when (value) {
            ACTION_SYNC -> WidgetLifecycleAction.SYNC
            ACTION_DELETE -> WidgetLifecycleAction.DELETE
            ACTION_RESTORE -> WidgetLifecycleAction.RESTORE
            ACTION_RECONCILE -> WidgetLifecycleAction.RECONCILE
            else -> null
        }
    }
}
