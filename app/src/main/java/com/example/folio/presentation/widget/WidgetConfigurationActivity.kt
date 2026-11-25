package com.example.folio.presentation.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.folio.FolioApplication
import com.example.folio.data.widget.WidgetKind
import com.example.folio.data.widget.WidgetRepository
import com.example.folio.data.network.PriceRefreshWorker
import com.example.folio.presentation.LanguagePreferences
import com.example.folio.presentation.ThemePreferences
import com.example.folio.presentation.design.FolioTheme
import kotlinx.coroutines.CancellationException
import com.example.folio.presentation.widget.WidgetRefreshScheduler
import com.example.folio.presentation.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : AppCompatActivity() {
    private lateinit var configurationViewModel: WidgetConfigurationViewModel
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetKind: WidgetKind? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val languagePreferences = LanguagePreferences(applicationContext)
        languagePreferences.set(languagePreferences.current())
        val themePreferences = ThemePreferences(applicationContext)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId <= 0) {
            finish()
            return
        }

        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider == null) {
            finish()
            return
        }
        widgetKind = resolveWidgetKind(packageName, provider.packageName, provider.className)
        val kind = widgetKind
        if (kind == null) {
            finish()
            return
        }

        val app = application as FolioApplication
        configurationViewModel = ViewModelProvider(
            this,
            WidgetConfigurationViewModelFactory(app.widgetRepository),
        )[WidgetConfigurationViewModel::class.java]

        setContent {
            FolioTheme(theme = themePreferences.current()) {
                val state by configurationViewModel.state.collectAsStateWithLifecycle()
                WidgetConfigurationScreen(
                    state = state,
                    onCurrencyChanged = configurationViewModel::updateCurrency,
                    onHideTotalChanged = configurationViewModel::updateHideTotal,
                    onAssetTypeChanged = configurationViewModel::updateAssetType,
                    onProviderChanged = configurationViewModel::updateProvider,
                    onQueryChanged = configurationViewModel::updateQuery,
                    onProviderSymbolChanged = configurationViewModel::updateProviderSymbol,
                    onMarketSelectionChanged = configurationViewModel::updateMarketSelection,
                    onAddMarket = configurationViewModel::addPendingMarket,
                    onRemoveMarket = configurationViewModel::removeMarket,
                    onMoveMarket = configurationViewModel::moveMarket,
                    onSave = ::saveConfiguration,
                    onCancel = ::cancelConfiguration,
                )
            }
        }

        lifecycleScope.launch {
            try {
                val existing = app.widgetRepository.configuration(appWidgetId)
                if (existing != null && existing.kind != kind) {
                    finish()
                    return@launch
                }
                configurationViewModel.initialize(appWidgetId, kind, existing)
            } catch (_: Exception) {
                configurationViewModel.markLoadFailed()
            }
        }
    }

    private fun saveConfiguration() {
        if (widgetKind == null) return
        lifecycleScope.launch {
            val saved = configurationViewModel.save() ?: return@launch
            try {
                WidgetRefreshScheduler.scheduleAndInvalidate(applicationContext)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                WidgetUpdateCoordinator.update(applicationContext, setOf(saved.appWidgetId))
            }
            try {
                // The queued follow-up captures the newly saved selections.
                PriceRefreshWorker.refreshAfterConfigurationSaved(applicationContext)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Saved configuration remains valid when background scheduling is unavailable.
            }
            try {
                WidgetUpdateCoordinator.update(applicationContext, setOf(saved.appWidgetId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The receiver and next reconciliation will retry rendering.
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, saved.appWidgetId),
            )
            finish()
        }
    }

    private fun cancelConfiguration() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

private class WidgetConfigurationViewModelFactory(
    private val repository: WidgetRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        createViewModel(modelClass, SavedStateHandle())

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T = createViewModel(modelClass, extras.createSavedStateHandle())

    private fun <T : ViewModel> createViewModel(
        modelClass: Class<T>,
        savedStateHandle: SavedStateHandle,
    ): T {
        require(modelClass.isAssignableFrom(WidgetConfigurationViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WidgetConfigurationViewModel(repository, savedStateHandle) as T
    }
}
