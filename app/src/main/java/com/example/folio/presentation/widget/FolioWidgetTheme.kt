package com.example.folio.presentation.widget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.glance.GlanceComposable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.example.folio.presentation.design.FolioDarkColorScheme
import com.example.folio.presentation.design.FolioDarkSemanticColors
import com.example.folio.presentation.design.FolioLightColorScheme
import com.example.folio.presentation.design.FolioLightSemanticColors
import com.example.folio.presentation.design.FolioMinimumTouchTarget
import com.example.folio.presentation.design.FolioSpacing
import androidx.compose.ui.graphics.Color

internal data class FolioWidgetSemanticColors(
    val gain: ColorProvider,
    val loss: ColorProvider,
    val warning: ColorProvider,
    val disabled: ColorProvider,
    val border: ColorProvider,
    val allocationSeries: List<Color>,
)

internal val LocalFolioWidgetSemanticColors = compositionLocalOf<FolioWidgetSemanticColors> {
    error("Folio widget colors are only available inside FolioWidgetTheme.")
}

private val folioGlanceSemanticColors = FolioWidgetSemanticColors(
    gain = DayNightColorProvider(
        day = FolioLightSemanticColors.gain,
        night = FolioDarkSemanticColors.gain,
    ),
    loss = DayNightColorProvider(
        day = FolioLightSemanticColors.loss,
        night = FolioDarkSemanticColors.loss,
    ),
    warning = DayNightColorProvider(
        day = FolioLightSemanticColors.warning,
        night = FolioDarkSemanticColors.warning,
    ),
    disabled = DayNightColorProvider(
        day = FolioLightSemanticColors.disabled,
        night = FolioDarkSemanticColors.disabled,
    ),
    border = DayNightColorProvider(
        day = FolioLightSemanticColors.border,
        night = FolioDarkSemanticColors.border,
    ),
    allocationSeries = FolioDarkSemanticColors.allocationSeries,
)

private val folioGlanceColorProviders = ColorProviders(
    light = FolioLightColorScheme,
    dark = FolioDarkColorScheme,
)

@Composable
@GlanceComposable
internal fun FolioWidgetTheme(content: @Composable @GlanceComposable () -> Unit) {
    GlanceTheme(colors = folioGlanceColorProviders) {
        CompositionLocalProvider(
            LocalFolioWidgetSemanticColors provides folioGlanceSemanticColors,
            content = content,
        )
    }
}

internal object FolioWidgetSpacing {
    val contentHorizontal = FolioSpacing.small
    val compactContentHorizontal = FolioSpacing.extraSmall
    val compactContentVertical = FolioSpacing.extraSmall
    val actionTarget = FolioMinimumTouchTarget
}
