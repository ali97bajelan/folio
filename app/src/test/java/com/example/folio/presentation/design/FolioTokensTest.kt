package com.example.folio.presentation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioTokensTest {
    @Test
    fun darkAndLightSchemesUseTheApprovedBrandPalettes() {
        assertEquals(Color(0xFF0B1020), FolioDarkColorScheme.background)
        assertEquals(Color(0xFF131B2B), FolioDarkColorScheme.surface)
        assertEquals(Color(0xFF172137), FolioDarkColorScheme.surfaceVariant)
        assertEquals(Color(0xFF668FFC), FolioDarkColorScheme.primary)
        assertEquals(Color(0xFF76D8B0), FolioDarkColorScheme.secondary)
        assertEquals(Color(0xFFF4F6FC), FolioDarkColorScheme.onBackground)

        assertEquals(Color(0xFFF5F7FB), FolioLightColorScheme.background)
        assertEquals(Color.White, FolioLightColorScheme.surface)
        assertEquals(Color(0xFFEEF2F9), FolioLightColorScheme.surfaceVariant)
        assertEquals(Color(0xFF315FC8), FolioLightColorScheme.primary)
        assertEquals(Color(0xFF087F5D), FolioLightColorScheme.secondary)
        assertEquals(Color(0xFF172033), FolioLightColorScheme.onBackground)
    }

    @Test
    fun semanticColorsIncludeStatusAndSharedAllocationSeries() {
        assertEquals(Color(0xFF76D8B0), FolioDarkSemanticColors.gain)
        assertEquals(Color(0xFFF47783), FolioDarkSemanticColors.loss)
        assertEquals(Color(0xFFFFC857), FolioDarkSemanticColors.warning)
        assertEquals(Color(0xFF788297), FolioDarkSemanticColors.disabled)
        assertEquals(Color(0xFF2C3950), FolioDarkSemanticColors.border)

        assertEquals(Color(0xFF087F5D), FolioLightSemanticColors.gain)
        assertEquals(Color(0xFFB42336), FolioLightSemanticColors.loss)
        assertEquals(Color(0xFF885D00), FolioLightSemanticColors.warning)
        assertEquals(Color(0xFF929BAD), FolioLightSemanticColors.disabled)
        assertEquals(Color(0xFFD4DCE8), FolioLightSemanticColors.border)

        assertEquals(8, FolioDarkSemanticColors.allocationSeries.size)
        assertEquals(FolioDarkSemanticColors.allocationSeries, FolioLightSemanticColors.allocationSeries)
        assertTrue(FolioDarkSemanticColors.allocationSeries.distinct().size == 8)
    }

    @Test
    fun layoutTokensUseTheApprovedScales() {
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp), FolioSpacing.all)
        assertEquals(listOf(16.dp, 20.dp, 24.dp), FolioIconSize.all)
        assertEquals(48.dp, FolioMinimumTouchTarget)
        assertEquals(150, FolioMotion.SHORT_MILLIS)
        assertEquals(250, FolioMotion.MEDIUM_MILLIS)
    }
}
