package com.irontrack.core.util

/**
 * Conversion RPE -> RIR, explicitement définie : RIR ≈ 10 - RPE (échelle de Tuchscherer / Helms).
 * Elle n'est utilisée que pour l'affichage indicatif ; la valeur RPE d'origine est toujours conservée.
 */
object Rpe {
    fun toApproxRir(rpe: Double): Double = (10.0 - rpe).coerceAtLeast(0.0)
}
