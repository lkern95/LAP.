package de.run.app.presentation.strength

import de.run.app.R

object StrengthIllustrationAssets {
    fun drawableFor(imageKey: String): Int? = when (imageKey) {
        "glute_bridge" -> R.drawable.strength_glute_bridge
        "wandsitz_90_90" -> R.drawable.strength_wandsitz_90_90
        "step_up_bergauf" -> R.drawable.strength_step_up_bergauf
        "step_down_bergab" -> R.drawable.strength_step_down_bergab
        "pnf_sprinter_d1_band" -> R.drawable.strength_pnf_sprinter_d1_band
        "miniband_seitschritte" -> R.drawable.strength_miniband_seitschritte
        else -> null
    }
}
