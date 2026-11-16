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
    primary = Color(0xFFFFC174),
    onPrimary = Color(0xFF472A00),
    primaryContainer = Color(0xFFF59E0B),
    onPrimaryContainer = Color(0xFF613B00),
    secondary = Color(0xFF4EDEA3),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF00A572),
    onSecondaryContainer = Color(0xFF00311F),
    tertiary = Color(0xFFC7C8FF),
    onTertiary = Color(0xFF1000A9),
    background = Color(0xFF051424),
    onBackground = Color(0xFFD4E4FA),
    surface = Color(0xFF051424),
    onSurface = Color(0xFFD4E4FA),
    surfaceVariant = Color(0xFF122131),
    onSurfaceVariant = Color(0xFFD8C3AD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08E7A),
    outlineVariant = Color(0xFF273647),
    inverseSurface = Color(0xFFD4E4FA),
    inverseOnSurface = Color(0xFF233143),
    inversePrimary = Color(0xFF855300),
    scrim = Color(0xFF000000),
)

val FolioLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF855300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2A1700),
    secondary = Color(0xFF006B4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB5F1D5),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFF4B4F9B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEDEFF),
    onTertiaryContainer = Color(0xFF17174F),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF171C25),
    surface = Color.White,
    onSurface = Color(0xFF171C25),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF555D67),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF79716A),
    outlineVariant = Color(0xFFE3DAD0),
    inverseSurface = Color(0xFF30323A),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFFFC174),
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
    Color(0xFF4EDEA3),
    Color(0xFFFFC174),
    Color(0xFFC7C8FF),
    Color(0xFFD8C3AD),
    Color(0xFF5BC0EB),
    Color(0xFFE28F63),
    Color(0xFF5FADB7),
    Color(0xFF9B8CFF),
)

val FolioDarkSemanticColors = FolioSemanticColors(
    gain = Color(0xFF4EDEA3),
    loss = Color(0xFFFFB4AB),
    warning = Color(0xFFFFC174),
    disabled = Color(0xFFA08E7A),
    border = Color(0xFF273647),
    allocationSeries = FolioAllocationSeries,
)

val FolioLightSemanticColors = FolioSemanticColors(
    gain = Color(0xFF006B4F),
    loss = Color(0xFFB3261E),
    warning = Color(0xFF855300),
    disabled = Color(0xFF79716A),
    border = Color(0xFFE3DAD0),
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
