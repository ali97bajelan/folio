package com.example.folio.data.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import com.example.folio.FolioApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One-time app and widget refresh entry point. Refresh logic lives in the shared operation. */
internal fun isRefreshWorkInProgress(states: List<WorkInfo.State>): Boolean =
    states.any { !it.isFinished }

class PriceRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FolioApplication ?: return Result.failure()
        return try {
            app.marketRefreshOperation.refresh()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        internal const val IMMEDIATE_WORK_NAME = "folio-price-refresh-now"

        fun observeRefreshInProgress(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(IMMEDIATE_WORK_NAME)
                .map { workInfos -> isRefreshWorkInProgress(workInfos.map { it.state }) }

        /** Requests the app-open refresh without changing the widget periodic schedule. */
        fun refreshOnAppOpen(context: Context) {
            refreshNow(context)
        }

        /** KEEP coalesces repeated manual and widget Refresh actions into one request. */
        fun refreshNow(context: Context): Operation {
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Append a refresh after current work so it includes a newly saved widget selection. */
        fun refreshAfterConfigurationSaved(context: Context): Operation {
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
