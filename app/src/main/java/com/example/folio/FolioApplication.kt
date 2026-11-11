package com.example.folio

import android.app.Application
import androidx.room.Room
import com.example.folio.data.PortfolioRepository
import com.example.folio.data.local.MIGRATION_3_4
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.data.widget.WidgetRepository

class FolioApplication : Application() {
    // Both the UI and workers use this database for the lifetime of the process.
    val database: PortfolioDatabase by lazy {
        Room.databaseBuilder(this, PortfolioDatabase::class.java, "folio.db")
            .addMigrations(MIGRATION_3_4)
            .build()
    }
    val repository: PortfolioRepository by lazy { PortfolioRepository(database) }
    val widgetRepository: WidgetRepository by lazy { WidgetRepository(database.widgetDao()) }
}
