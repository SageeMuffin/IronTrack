package com.irontrack.core.util

object Units {
    const val LB_IN_KG = 0.45359237
    fun kgToLb(kg: Double) = kg / LB_IN_KG
    fun lbToKg(lb: Double) = lb * LB_IN_KG

    /** Estimation 1RM (Epley). Utilisée uniquement pour les records estimés. */
    fun epley1rm(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)
}
