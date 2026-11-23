package com.irontrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.irontrack.app.R
import com.irontrack.app.data.repo.AppSettings
import com.irontrack.app.data.repo.WeightUnitPref
import com.irontrack.core.util.Durations
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.Rpe
import com.irontrack.core.util.Units
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Mise en forme des valeurs selon les préférences (unité de charge, format français). */
object Fmt {
    fun toDisplayWeight(kg: Double, unit: WeightUnitPref): Double = if (unit == WeightUnitPref.LB) Units.kgToLb(kg) else kg
    fun fromDisplayWeight(v: Double, unit: WeightUnitPref): Double = if (unit == WeightUnitPref.LB) Units.lbToKg(v) else v

    fun weightNumber(kg: Double, unit: WeightUnitPref): String = FrenchFormat.number(toDisplayWeight(kg, unit), 1)

    fun reps(min: Int?, max: Int?): String? = when {
        min == null && max == null -> null
        min != null && max != null && min != max -> "$min-$max"
        else -> (min ?: max).toString()
    }

    fun rir(min: Int?, max: Int?): String? = when {
        min == null && max == null -> null
        min != null && max != null && min != max -> "$min-$max"
        else -> (min ?: max).toString()
    }

    fun rpe(v: Double): String = FrenchFormat.number(v, 1)
    fun approxRirFromRpe(v: Double): String = FrenchFormat.number(Rpe.toApproxRir(v), 1)

    fun date(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(FrenchFormat.date)
    fun date(d: LocalDate): String = d.format(FrenchFormat.date)
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().let { FrenchFormat.time(it.hour * 60 + it.minute) }
    fun minutes(m: Int): String = Durations.formatMinutes(m)
    fun rest(s: Int): String = Durations.formatRest(s)
}

@Composable
fun unitLabel(): String = stringResource(if (LocalAppSettings.current.weightUnit == WeightUnitPref.LB) R.string.unit_lb else R.string.unit_kg)

@Composable
fun weightText(kg: Double?): String? = kg?.let { "${Fmt.weightNumber(it, LocalAppSettings.current.weightUnit)} ${unitLabel()}" }

/** Résumé d'une cible de série : « 8-10 reps · RIR 2 · 60 kg · 2 min ». null si rien n'est renseigné. */
@Composable
fun targetSummary(
    repsMin: Int?, repsMax: Int?, rir: Int?, rirMax: Int?, rpe: Double?, weightKg: Double?, restSeconds: Int?,
): String? {
    val parts = buildList {
        Fmt.reps(repsMin, repsMax)?.let { add(stringResource(R.string.fmt_reps, it)) }
        Fmt.rir(rir, rirMax)?.let { add(stringResource(R.string.fmt_rir, it)) }
        rpe?.let { add(stringResource(R.string.fmt_rpe, Fmt.rpe(it))) }
        weightText(weightKg)?.let { add(it) }
        restSeconds?.let { add(stringResource(R.string.fmt_rest, Fmt.rest(it))) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
