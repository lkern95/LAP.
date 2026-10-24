package de.run.app.presentation.strength

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthIllustrationAssetsTest {
    @Test
    fun usesOnlyMappedLocalIllustrationsAndFallsBackForMissingImageKeys() {
        assertNotNull(StrengthIllustrationAssets.drawableFor("glute_bridge"))
        assertNull(StrengthIllustrationAssets.drawableFor("kniebeuge_miniband"))
        assertNull(StrengthIllustrationAssets.drawableFor("unbekannter_image_key"))
    }
}
