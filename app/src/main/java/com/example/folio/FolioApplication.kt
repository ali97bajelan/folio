package com.example.folio

import android.app.Application
import androidx.room.Room
import com.example.folio.data.PortfolioRepository
import com.example.folio.data.local.PortfolioDatabase

class FolioApplication : Application() {
    // Both the UI and workers use this database for the lifetime of the process.
    val database: PortfolioDatabase by lazy {
        Room.databaseBuilder(this, PortfolioDatabase::class.java, "folio.db")
            .addMigrations(PortfolioDatabase.MIGRATION_1_2, PortfolioDatabase.MIGRATION_2_3)
            .build()
    }
    val repository: PortfolioRepository by lazy { PortfolioRepository(database) }
}
