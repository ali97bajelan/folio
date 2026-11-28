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
import com.example.folio.presentation.AppTheme

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
        day = Color(0xFFD8F3E8),
        night = Color(0xFF123F38),
    ),
    lossContainer = DayNightColorProvider(
        day = Color(0xFFF9E1E4),
        night = Color(0xFF4B2634),
    ),
    warningContainer = DayNightColorProvider(
        day = Color(0xFFFFEAC2),
        night = Color(0xFF44371F),
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

private fun fixedColorProvider(color: Color): ColorProvider =
    DayNightColorProvider(day = color, night = color)

private fun widgetColorProviders(theme: AppTheme) = when (theme) {
    AppTheme.DARK -> ColorProviders(light = FolioDarkColorScheme, dark = FolioDarkColorScheme)
    AppTheme.LIGHT -> ColorProviders(light = FolioLightColorScheme, dark = FolioLightColorScheme)
    AppTheme.SYSTEM -> folioGlanceColorProviders
}

private fun widgetSemanticColors(theme: AppTheme): FolioWidgetSemanticColors = when (theme) {
    AppTheme.SYSTEM -> folioGlanceSemanticColors
    AppTheme.DARK -> FolioWidgetSemanticColors(
        gain = fixedColorProvider(FolioDarkSemanticColors.gain),
        loss = fixedColorProvider(FolioDarkSemanticColors.loss),
        warning = fixedColorProvider(FolioDarkSemanticColors.warning),
        gainContainer = fixedColorProvider(Color(0xFF123F38)),
        lossContainer = fixedColorProvider(Color(0xFF4B2634)),
        warningContainer = fixedColorProvider(Color(0xFF44371F)),
        disabled = fixedColorProvider(FolioDarkSemanticColors.disabled),
        border = fixedColorProvider(FolioDarkSemanticColors.border),
        allocationSeries = FolioDarkSemanticColors.allocationSeries,
    )
    AppTheme.LIGHT -> FolioWidgetSemanticColors(
        gain = fixedColorProvider(FolioLightSemanticColors.gain),
        loss = fixedColorProvider(FolioLightSemanticColors.loss),
        warning = fixedColorProvider(FolioLightSemanticColors.warning),
        gainContainer = fixedColorProvider(Color(0xFFD8F3E8)),
        lossContainer = fixedColorProvider(Color(0xFFF9E1E4)),
        warningContainer = fixedColorProvider(Color(0xFFFFEAC2)),
        disabled = fixedColorProvider(FolioLightSemanticColors.disabled),
        border = fixedColorProvider(FolioLightSemanticColors.border),
        allocationSeries = FolioLightSemanticColors.allocationSeries,
    )
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

internal object FolioWidgetSpacing {
    val contentHorizontal = FolioSpacing.small
    val compactContentHorizontal = FolioSpacing.extraSmall
    val compactContentVertical = FolioSpacing.extraSmall
    val actionTarget = FolioMinimumTouchTarget
}
