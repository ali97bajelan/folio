package com.example.folio.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Persists an invalidation ticket before asking Glance to render. If an update fails, the ticket
 * remains pending and a later refresh, app launch, or receiver callback retries it.
 */
object WidgetUpdateCoordinator {
    private const val PREFERENCES_NAME = "widget_update_state"
    private const val REQUESTED_REVISION = "requested_revision"
    private const val PUBLISHED_REVISION = "published_revision"
    private val revisionMutex = Mutex()

    fun observeRequestedRevision(context: Context): Flow<Long> = callbackFlow {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == REQUESTED_REVISION) {
                trySend(changed.getLong(REQUESTED_REVISION, 0L))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(preferences.getLong(REQUESTED_REVISION, 0L))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun invalidateAll(context: Context) {
        val ids = withContext(Dispatchers.IO) {
            installedWidgetIds(context.applicationContext)
        }
        update(context, ids)
    }

    suspend fun update(context: Context, appWidgetIds: Set<Int>) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val revision = requestRevision(appContext) ?: return@withContext
        val installedAtStart = installedWidgetIds(appContext)
        val requestedInstalled = appWidgetIds intersect installedAtStart
        val updated = updateInstalledInstances(appContext, requestedInstalled)
        val installedAfterUpdate = installedWidgetIds(appContext)
        val coversAllInstances = appWidgetIds.containsAll(installedAfterUpdate)

        if (updated && coversAllInstances) {
            markPublishedIfCurrent(appContext, revision)
        }
    }

    private suspend fun updateInstalledInstances(
        context: Context,
        appWidgetIds: Set<Int>,
    ): Boolean {
        if (appWidgetIds.isEmpty()) return true

        val manager = GlanceAppWidgetManager(context)
        val updatedIds = mutableSetOf<Int>()
        var success = true

        suspend fun updateWidget(
            widget: androidx.glance.appwidget.GlanceAppWidget,
            glanceIds: List<GlanceId>,
        ) {
            for (glanceId in glanceIds) {
                val appWidgetId = try {
                    manager.getAppWidgetId(glanceId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    success = false
                    continue
                }
                if (appWidgetId !in appWidgetIds) continue
                try {
                    widget.update(context, glanceId)
                    updatedIds.add(appWidgetId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    success = false
                }
            }
        }

        try {
            updateWidget(PortfolioWidget(), manager.getGlanceIds(PortfolioWidget::class.java))
            updateWidget(AssetPriceWidget(), manager.getGlanceIds(AssetPriceWidget::class.java))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return false
        }

        return success && updatedIds.containsAll(appWidgetIds)
    }

    private suspend fun requestRevision(context: Context): Long? = revisionMutex.withLock {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val next = preferences.getLong(REQUESTED_REVISION, 0L) + 1L
        if (preferences.edit().putLong(REQUESTED_REVISION, next).commit()) next else null
    }

    private suspend fun markPublishedIfCurrent(context: Context, revision: Long) =
        revisionMutex.withLock {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getLong(REQUESTED_REVISION, 0L) == revision) {
                preferences.edit().putLong(PUBLISHED_REVISION, revision).commit()
            }
        }

    private fun installedWidgetIds(context: Context): Set<Int> {
        val manager = AppWidgetManager.getInstance(context)
        val portfolioIds = manager.getAppWidgetIds(
            ComponentName(context, PortfolioWidgetReceiver::class.java),
        )
        val assetPriceIds = manager.getAppWidgetIds(
            ComponentName(context, AssetPriceWidgetReceiver::class.java),
        )
        return (portfolioIds.asList() + assetPriceIds.asList()).toSet()
    }
}
