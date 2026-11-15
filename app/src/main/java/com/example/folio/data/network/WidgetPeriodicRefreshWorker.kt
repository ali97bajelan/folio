package com.example.folio.data.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.folio.FolioApplication
import kotlinx.coroutines.CancellationException

class WidgetPeriodicRefreshWorker(
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
}
