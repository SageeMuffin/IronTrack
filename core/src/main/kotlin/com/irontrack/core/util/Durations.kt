package com.irontrack.core.util

import kotlin.math.abs

/** Formats de durée en français : « 1h15 », « 45 min », « +7 min ». */
object Durations {

    fun formatMinutes(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "${h}h"
            else -> "${h}h" + m.toString().padStart(2, '0')
        }
    }

    /** Durée écoulée (secondes) au format chronomètre « 1:02:05 » ou « 12:05 ». */
    fun formatClock(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Repos en secondes : « 90 s », « 2 min », « 2 min 30 ». */
    fun formatRest(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m == 0 -> "$s s"
            s == 0 -> "$m min"
            else -> "$m min ${s.toString().padStart(2, '0')}"
        }
    }

    /**
     * Différence entre durée réelle et estimée. Renvoie null si l'une des deux manque :
     * une valeur absente n'est jamais inventée.
     */
    fun difference(estimatedMinutes: Int?, actualMinutes: Int?): String? {
        if (estimatedMinutes == null || actualMinutes == null) return null
        val diff = actualMinutes - estimatedMinutes
        val sign = when {
            diff > 0 -> "+"
            diff < 0 -> "-"
            else -> "±"
        }
        return sign + formatMinutes(abs(diff))
    }
}
