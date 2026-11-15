package com.example.folio.presentation.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val FolioDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF668FFC),
    onPrimary = Color(0xFF08142D),
    primaryContainer = Color(0xFF1D2E52),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFF76D8B0),
    onSecondary = Color(0xFF0A271E),
    secondaryContainer = Color(0xFF17392F),
    onSecondaryContainer = Color(0xFFC1F4DF),
    tertiary = Color(0xFF8B7CF6),
    onTertiary = Color(0xFF21174F),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFF4F6FC),
    surface = Color(0xFF131B2B),
    onSurface = Color(0xFFF4F6FC),
    surfaceVariant = Color(0xFF172137),
    onSurfaceVariant = Color(0xFFB9C4D6),
    error = Color(0xFFF47783),
    onError = Color(0xFF3A0710),
    errorContainer = Color(0xFF5B1721),
    onErrorContainer = Color(0xFFFFD9DE),
    outline = Color(0xFF788297),
    outlineVariant = Color(0xFF2C3950),
    inverseSurface = Color(0xFFE1E7F2),
    inverseOnSurface = Color(0xFF172033),
    inversePrimary = Color(0xFF315FC8),
    scrim = Color(0xFF000000),
)

val FolioLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF315FC8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF102B64),
    secondary = Color(0xFF087F5D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5F0DF),
    onSecondaryContainer = Color(0xFF063D2C),
    tertiary = Color(0xFF6852B8),
    onTertiary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF172033),
    surface = Color.White,
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFEEF2F9),
    onSurfaceVariant = Color(0xFF4B5B73),
    error = Color(0xFFB42336),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8EA),
    onErrorContainer = Color(0xFF60101D),
    outline = Color(0xFF929BAD),
    outlineVariant = Color(0xFFD4DCE8),
    inverseSurface = Color(0xFF2A3447),
    inverseOnSurface = Color(0xFFF5F7FB),
    inversePrimary = Color(0xFF9DB7FF),
    scrim = Color(0xFF000000),
)

data class FolioSemanticColors(
    val gain: Color,
    val loss: Color,
    val warning: Color,
    val disabled: Color,
    val border: Color,
    val allocationSeries: List<Color>,
)

private val FolioAllocationSeries = listOf(
    Color(0xFF76D8B0),
    Color(0xFF668FFC),
    Color(0xFFFFC857),
    Color(0xFFF47783),
    Color(0xFF8B7CF6),
    Color(0xFF5BC0EB),
    Color(0xFFE28F63),
    Color(0xFF5FADB7),
)

val FolioDarkSemanticColors = FolioSemanticColors(
    gain = Color(0xFF76D8B0),
    loss = Color(0xFFF47783),
    warning = Color(0xFFFFC857),
    disabled = Color(0xFF788297),
    border = Color(0xFF2C3950),
    allocationSeries = FolioAllocationSeries,
)

val FolioLightSemanticColors = FolioSemanticColors(
    gain = Color(0xFF087F5D),
    loss = Color(0xFFB42336),
    warning = Color(0xFF885D00),
    disabled = Color(0xFF929BAD),
    border = Color(0xFFD4DCE8),
    allocationSeries = FolioAllocationSeries,
)

object FolioSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val extraExtraLarge = 32.dp
    val all = listOf(extraSmall, small, medium, large, extraLarge, extraExtraLarge)
}

object FolioIconSize {
    val small = 16.dp
    val medium = 20.dp
    val large = 24.dp
    val all = listOf(small, medium, large)
}

object FolioShapeTokens {
    val small: CornerBasedShape = RoundedCornerShape(8.dp)
    val medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val large: CornerBasedShape = RoundedCornerShape(16.dp)
    val pill: CornerBasedShape = RoundedCornerShape(50)
}

object FolioElevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
}

object FolioMotion {
    const val SHORT_MILLIS = 150
    const val MEDIUM_MILLIS = 250
}

val FolioMinimumTouchTarget = 48.dp

val FolioTypography = Typography()

val FolioShapes = Shapes(
    extraSmall = FolioShapeTokens.small,
    small = FolioShapeTokens.medium,
    medium = FolioShapeTokens.medium,
    large = FolioShapeTokens.large,
    extraLarge = FolioShapeTokens.large,
)
