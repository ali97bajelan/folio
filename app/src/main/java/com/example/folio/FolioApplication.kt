package com.example.folio

import android.app.Application
import androidx.room.Room
import com.example.folio.data.PortfolioRepository
import com.example.folio.data.local.MIGRATION_3_4
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.data.network.MarketRefreshOperation
import com.example.folio.data.widget.WidgetRepository
import com.example.folio.data.widget.WidgetSnapshotRepository
import com.example.folio.presentation.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FolioApplication : Application() {
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Both the UI and workers use this database for the lifetime of the process.
    val database: PortfolioDatabase by lazy {
        Room.databaseBuilder(this, PortfolioDatabase::class.java, "folio.db")
            .addMigrations(MIGRATION_3_4)
            .build()
    }

    val repository: PortfolioRepository by lazy {
        PortfolioRepository(
            database = database,
            onWidgetsInvalidated = { WidgetUpdateCoordinator.invalidateAll(this@FolioApplication) },
        )
    }
    val widgetRepository: WidgetRepository by lazy { WidgetRepository(database.widgetDao()) }
    val widgetSnapshotRepository: WidgetSnapshotRepository by lazy { WidgetSnapshotRepository(database) }
    val marketRefreshOperation: MarketRefreshOperation by lazy {
        MarketRefreshOperation(
            database = database,
            widgetRepository = widgetRepository,
            invalidateWidgets = { WidgetUpdateCoordinator.invalidateAll(this@FolioApplication) },
        )
    }

    fun requestWidgetInvalidation() {
        applicationScope.launch {
            WidgetUpdateCoordinator.invalidateAll(this@FolioApplication)
        }
    }
}
