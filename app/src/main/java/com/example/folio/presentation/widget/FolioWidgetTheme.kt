package com.example.folio.presentation.widget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceComposable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.example.folio.presentation.AppTheme
import com.example.folio.presentation.design.FolioDarkColorScheme
import com.example.folio.presentation.design.FolioDarkSemanticColors
import com.example.folio.presentation.design.FolioLightColorScheme
import com.example.folio.presentation.design.FolioLightSemanticColors
import com.example.folio.presentation.design.FolioSemanticColors

internal data class FolioWidgetSemanticColors(
    val gain: ColorProvider,
    val loss: ColorProvider,
    val warning: ColorProvider,
    val gainContainer: ColorProvider,
    val lossContainer: ColorProvider,
    val warningContainer: ColorProvider,
    val disabled: ColorProvider,
    val border: ColorProvider,
    val allocationSeries: List<Color>,
)

internal val LocalFolioWidgetSemanticColors = compositionLocalOf<FolioWidgetSemanticColors> {
    error("Folio widget colors are only available inside FolioWidgetTheme.")
}

private fun fixedColorProvider(color: Color): ColorProvider =
    DayNightColorProvider(day = color, night = color)

private fun fixedWidgetSemanticColors(colors: FolioSemanticColors) =
    FolioWidgetSemanticColors(
        gain = fixedColorProvider(colors.gain),
        loss = fixedColorProvider(colors.loss),
        warning = fixedColorProvider(colors.warning),
        gainContainer = fixedColorProvider(colors.gainContainer),
        lossContainer = fixedColorProvider(colors.lossContainer),
        warningContainer = fixedColorProvider(colors.warningContainer),
        disabled = fixedColorProvider(colors.disabled),
        border = fixedColorProvider(colors.border),
        allocationSeries = colors.allocationSeries,
    )

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
    gainContainer = DayNightColorProvider(
        day = FolioLightSemanticColors.gainContainer,
        night = FolioDarkSemanticColors.gainContainer,
    ),
    lossContainer = DayNightColorProvider(
        day = FolioLightSemanticColors.lossContainer,
        night = FolioDarkSemanticColors.lossContainer,
    ),
    warningContainer = DayNightColorProvider(
        day = FolioLightSemanticColors.warningContainer,
        night = FolioDarkSemanticColors.warningContainer,
    ),
    disabled = DayNightColorProvider(
        day = FolioLightSemanticColors.disabled,
        night = FolioDarkSemanticColors.disabled,
    ),
    border = DayNightColorProvider(
        day = FolioLightSemanticColors.border,
        night = FolioDarkSemanticColors.border,
    ),
    allocationSeries = FolioLightSemanticColors.allocationSeries,
)

private val folioGlanceColorProviders = ColorProviders(
    light = FolioLightColorScheme,
    dark = FolioDarkColorScheme,
)

private fun widgetColorProviders(theme: AppTheme) = when (theme) {
    AppTheme.DARK -> ColorProviders(light = FolioDarkColorScheme, dark = FolioDarkColorScheme)
    AppTheme.LIGHT -> ColorProviders(light = FolioLightColorScheme, dark = FolioLightColorScheme)
    AppTheme.SYSTEM -> folioGlanceColorProviders
}

private fun widgetSemanticColors(theme: AppTheme): FolioWidgetSemanticColors = when (theme) {
    AppTheme.SYSTEM -> folioGlanceSemanticColors
    AppTheme.DARK -> fixedWidgetSemanticColors(FolioDarkSemanticColors)
    AppTheme.LIGHT -> fixedWidgetSemanticColors(FolioLightSemanticColors)
}

@Composable
@GlanceComposable
internal fun FolioWidgetTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable @GlanceComposable () -> Unit,
) {
    GlanceTheme(colors = widgetColorProviders(theme)) {
        CompositionLocalProvider(
            LocalFolioWidgetSemanticColors provides widgetSemanticColors(theme),
            content = content,
        )
    }
}
