package com.example.folio.presentation.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.folio.presentation.AppTheme

val LocalFolioSemanticColors = staticCompositionLocalOf { FolioDarkSemanticColors }

@Composable
fun FolioTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) FolioDarkColorScheme else FolioLightColorScheme
    val semanticColors = if (darkTheme) FolioDarkSemanticColors else FolioLightSemanticColors

    CompositionLocalProvider(LocalFolioSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FolioTypography,
            shapes = FolioShapes,
            content = content,
        )
    }
}
