package com.example.folio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.folio.presentation.MainViewModel
import com.example.folio.presentation.PortfolioApp
import com.example.folio.presentation.LanguagePreferences
import com.example.folio.data.network.PriceRefreshWorker

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val languagePreferences = LanguagePreferences(applicationContext)
        // Apply the saved locale before Compose reads any string resources.
        languagePreferences.set(languagePreferences.current())
        PriceRefreshWorker.refreshOnAppOpen(applicationContext)
        val repository = (application as FolioApplication).repository
        val factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = MainViewModel(applicationContext, repository) as T }
        ViewModelProvider(this, factory)[MainViewModel::class.java].seedDefaultLocations(
            resources.getStringArray(R.array.default_location_names).toList(),
        )
        setContent { PortfolioApp(factory, languagePreferences) }
    }
}
