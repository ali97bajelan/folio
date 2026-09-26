package com.example.folio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.folio.R
import com.example.folio.data.PortfolioRepository
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.presentation.MainViewModel
import com.example.folio.presentation.PortfolioApp
import com.example.folio.presentation.LanguagePreferences
import com.example.folio.data.network.PriceRefreshWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val languagePreferences = LanguagePreferences(applicationContext)
        // Apply the saved locale before Compose reads any string resources.
        languagePreferences.set(languagePreferences.current())
        PriceRefreshWorker.schedule(applicationContext)
        val db = Room.databaseBuilder(applicationContext, PortfolioDatabase::class.java, "folio.db")
            .addMigrations(PortfolioDatabase.MIGRATION_1_2, PortfolioDatabase.MIGRATION_2_3)
            // PriceRefreshWorker opens the same file separately.  Notify this
            // instance when it writes the cached quotes.
            .enableMultiInstanceInvalidation()
            .build()
        val repository = PortfolioRepository(db.dao())
        // Populate the picker before the UI is shown. Existing user locations
        // are preserved, and repeat launches ignore matching defaults.
        lifecycleScope.launch {
            repository.seedDefaultLocations(
                resources.getStringArray(R.array.default_location_names).toList(),
            )
        }
        val factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = MainViewModel(applicationContext, repository) as T }
        setContent { PortfolioApp(factory, languagePreferences) }
    }
}
