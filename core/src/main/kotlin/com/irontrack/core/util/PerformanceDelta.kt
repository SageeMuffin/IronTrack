package com.irontrack.core.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Évolution d'une série par rapport à la même série de la séance précédente.
 * - charge et répétitions connues des deux côtés : volume (charge × répétitions) ;
 * - sinon répétitions seules (exercices au poids du corps), sinon charge seule.
 * Retourne null si la comparaison n'a pas de sens (valeur absente, base nulle). Jamais de valeur inventée.
 */
object PerformanceDelta {
    fun percent(prevWeight: Double?, prevReps: Int?, curWeight: Double?, curReps: Int?): Double? {
        val pw = prevWeight?.takeIf { it > 0 }
        val cw = curWeight?.takeIf { it > 0 }
        val base: Double
        val now: Double
        when {
            pw != null && cw != null && prevReps != null && curReps != null && prevReps > 0 -> { base = pw * prevReps; now = cw * curReps }
            prevReps != null && curReps != null && prevReps > 0 && pw == null && cw == null -> { base = prevReps.toDouble(); now = curReps.toDouble() }
            pw != null && cw != null && (prevReps == null || curReps == null) -> { base = pw; now = cw }
            else -> return null
        }
        return (now / base - 1.0) * 100.0
    }

    /** « +5 % », « −2,5 % », « = » (format français, espace insécable). */
    fun format(percent: Double): String {
        val rounded = if (abs(percent) >= 10) percent.roundToInt().toDouble() else (percent * 10).roundToInt() / 10.0
        if (rounded == 0.0) return "="
        val sign = if (rounded > 0) "+" else "−"
        return sign + FrenchFormat.number(abs(rounded), 1) + " %"
    }
}
