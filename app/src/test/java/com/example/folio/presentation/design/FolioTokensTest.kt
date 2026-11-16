package com.example.folio.presentation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioTokensTest {
    @Test
    fun darkAndLightSchemesUseTheApprovedBrandPalettes() {
        assertEquals(Color(0xFF051424), FolioDarkColorScheme.background)
        assertEquals(Color(0xFF051424), FolioDarkColorScheme.surface)
        assertEquals(Color(0xFF122131), FolioDarkColorScheme.surfaceVariant)
        assertEquals(Color(0xFFFFC174), FolioDarkColorScheme.primary)
        assertEquals(Color(0xFF4EDEA3), FolioDarkColorScheme.secondary)
        assertEquals(Color(0xFFD4E4FA), FolioDarkColorScheme.onBackground)

        assertEquals(Color(0xFFF7F8FA), FolioLightColorScheme.background)
        assertEquals(Color.White, FolioLightColorScheme.surface)
        assertEquals(Color(0xFFF0F2F5), FolioLightColorScheme.surfaceVariant)
        assertEquals(Color(0xFF855300), FolioLightColorScheme.primary)
        assertEquals(Color(0xFF006B4F), FolioLightColorScheme.secondary)
        assertEquals(Color(0xFF171C25), FolioLightColorScheme.onBackground)
    }

    @Test
    fun semanticColorsIncludeStatusAndSharedAllocationSeries() {
        assertEquals(Color(0xFF4EDEA3), FolioDarkSemanticColors.gain)
        assertEquals(Color(0xFFFFB4AB), FolioDarkSemanticColors.loss)
        assertEquals(Color(0xFFFFC174), FolioDarkSemanticColors.warning)
        assertEquals(Color(0xFFA08E7A), FolioDarkSemanticColors.disabled)
        assertEquals(Color(0xFF273647), FolioDarkSemanticColors.border)

        assertEquals(Color(0xFF006B4F), FolioLightSemanticColors.gain)
        assertEquals(Color(0xFFB3261E), FolioLightSemanticColors.loss)
        assertEquals(Color(0xFF855300), FolioLightSemanticColors.warning)
        assertEquals(Color(0xFF79716A), FolioLightSemanticColors.disabled)
        assertEquals(Color(0xFFE3DAD0), FolioLightSemanticColors.border)

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
