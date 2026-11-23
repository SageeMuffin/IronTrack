package com.irontrack.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.irontrack.app.R
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.unitLabel
import com.irontrack.core.util.FrenchFormat
import kotlin.math.roundToInt

/*
 * Feuilles de saisie à molette pour chaque type de valeur. Chacune propose « Effacer » pour revenir
 * à une valeur non renseignée : aucune valeur par défaut n'est imposée.
 */

const val NONE = "—"

/** Répétitions : valeur unique, ou plage min-max si [allowRange]. */
@Composable
fun RepsSheet(
    title: String,
    min: Int?,
    max: Int?,
    allowRange: Boolean,
    hint: Int? = null,
    onDismiss: () -> Unit,
    onResult: (Int?, Int?) -> Unit,
) {
    val values = (0..100).toList()
    val labels = values.map { it.toString() }
    val start = (min ?: hint ?: 8).coerceIn(0, 100)
    if (!allowRange) {
        WheelSheet(
            title = title,
            wheels = listOf(WheelSpec(labels, start)),
            onDismiss = onDismiss,
            onConfirm = { onResult(values[it[0]], values[it[0]]) },
            onClear = { onResult(null, null) },
        )
    } else {
        val maxLabels = listOf(NONE) + labels
        val maxStart = if (max != null && max != min) max + 1 else 0
        WheelSheet(
            title = title,
            wheels = listOf(
                WheelSpec(labels, start, caption = stringResource(R.string.label_min)),
                WheelSpec(maxLabels, maxStart, caption = stringResource(R.string.label_max_optional)),
            ),
            separator = "–",
            onDismiss = onDismiss,
            onConfirm = { idx ->
                val a = values[idx[0]]
                val b = if (idx[1] == 0) null else values[idx[1] - 1]
                if (b == null || b == a) onResult(a, a) else onResult(minOf(a, b), maxOf(a, b))
            },
            onClear = { onResult(null, null) },
        )
    }
}

/** RIR de 0 à 10 avec l'explication en français de la valeur choisie. */
@Composable
fun RirSheet(
    title: String,
    value: Int?,
    hint: Int? = null,
    onDismiss: () -> Unit,
    onResult: (Int?) -> Unit,
) {
    val values = (0..10).toList()
    val meanings = stringArrayResource(R.array.rir_meanings)
    WheelSheet(
        title = title,
        wheels = listOf(WheelSpec(values.map { it.toString() }, (value ?: hint ?: 2).coerceIn(0, 10))),
        onDismiss = onDismiss,
        onConfirm = { onResult(values[it[0]]) },
        onClear = { onResult(null) },
        footer = { idx ->
            val v = values[idx[0]]
            Text(
                meanings[v.coerceAtMost(meanings.lastIndex)],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Plage de RIR (cible de programme) : min et max facultatif. */
@Composable
fun RirRangeSheet(
    title: String,
    min: Int?,
    max: Int?,
    onDismiss: () -> Unit,
    onResult: (Int?, Int?) -> Unit,
) {
    val values = (0..10).toList()
    val meanings = stringArrayResource(R.array.rir_meanings)
    WheelSheet(
        title = title,
        wheels = listOf(
            WheelSpec(values.map { it.toString() }, (min ?: 2).coerceIn(0, 10), caption = stringResource(R.string.label_min)),
            WheelSpec(listOf(NONE) + values.map { it.toString() }, if (max != null && max != min) max + 1 else 0, caption = stringResource(R.string.label_max_optional)),
        ),
        separator = "–",
        onDismiss = onDismiss,
        onConfirm = { idx ->
            val a = values[idx[0]]
            val b = if (idx[1] == 0) null else values[idx[1] - 1]
            if (b == null || b == a) onResult(a, a) else onResult(minOf(a, b), maxOf(a, b))
        },
        onClear = { onResult(null, null) },
        footer = { idx ->
            Text(meanings[values[idx[0]].coerceAtMost(meanings.lastIndex)], style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

/** RPE de 1 à 10 par pas de 0,5 (valeur d'origine conservée). */
@Composable
fun RpeSheet(title: String, value: Double?, onDismiss: () -> Unit, onResult: (Double?) -> Unit) {
    val values = (2..20).map { it / 2.0 }
    val start = value?.let { v -> values.indexOfFirst { it >= v }.coerceAtLeast(0) } ?: values.indexOf(8.0)
    WheelSheet(
        title = title,
        wheels = listOf(WheelSpec(values.map { FrenchFormat.number(it, 1) }, start)),
        onDismiss = onDismiss,
        onConfirm = { onResult(values[it[0]]) },
        onClear = { onResult(null) },
        footer = { idx ->
            Text(stringResource(R.string.rpe_explanation, Fmt.approxRirFromRpe(values[idx[0]])),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

/** Charge dans l'unité choisie, par pas réglable dans les paramètres. Résultat en kg. */
@Composable
fun WeightSheet(title: String, valueKg: Double?, hintKg: Double? = null, onDismiss: () -> Unit, onResult: (Double?) -> Unit) {
    val s = LocalAppSettings.current
    val step = s.weightIncrement.takeIf { it > 0 } ?: 2.5
    val maxDisplay = if (s.weightUnit.name == "LB") 1000.0 else 500.0
    val count = (maxDisplay / step).roundToInt()
    val values = (0..count).map { it * step }
    val startDisplay = (valueKg ?: hintKg)?.let { Fmt.toDisplayWeight(it, s.weightUnit) } ?: 20.0
    val start = values.indexOfFirst { it >= startDisplay - step / 2 }.let { if (it < 0) 0 else it }
    val unit = unitLabel()
    WheelSheet(
        title = title,
        wheels = listOf(WheelSpec(values.map { "${FrenchFormat.number(it, 2)} $unit" }, start)),
        onDismiss = onDismiss,
        onConfirm = { onResult(Fmt.fromDisplayWeight(values[it[0]], s.weightUnit)) },
        onClear = { onResult(null) },
    )
}

/** Durée en minutes et secondes (repos). Résultat en secondes, null si effacé. */
@Composable
fun RestSheet(title: String, seconds: Int?, onDismiss: () -> Unit, onResult: (Int?) -> Unit) {
    val mins = (0..15).toList()
    val secs = (0..55 step 5).toList()
    val v = seconds ?: 90
    WheelSheet(
        title = title,
        wheels = listOf(
            WheelSpec(mins.map { "$it min" }, (v / 60).coerceIn(0, 15)),
            WheelSpec(secs.map { "${it.toString().padStart(2, '0')} s" }, ((v % 60) / 5).coerceIn(0, secs.lastIndex)),
        ),
        onDismiss = onDismiss,
        onConfirm = { idx -> (mins[idx[0]] * 60 + secs[idx[1]]).let { onResult(if (it == 0) null else it) } },
        onClear = { onResult(null) },
    )
}

/** Durée estimée de séance : heures et minutes. Résultat en minutes, null si effacé. */
@Composable
fun DurationSheet(title: String, minutes: Int?, onDismiss: () -> Unit, onResult: (Int?) -> Unit) {
    val hours = (0..5).toList()
    val mins = (0..55 step 5).toList()
    val v = minutes ?: 60
    WheelSheet(
        title = title,
        wheels = listOf(
            WheelSpec(hours.map { "$it h" }, (v / 60).coerceIn(0, 5)),
            WheelSpec(mins.map { "${it.toString().padStart(2, '0')} min" }, ((v % 60) / 5).coerceIn(0, mins.lastIndex)),
        ),
        onDismiss = onDismiss,
        onConfirm = { idx -> (hours[idx[0]] * 60 + mins[idx[1]]).let { onResult(if (it == 0) null else it) } },
        onClear = { onResult(null) },
    )
}

/** Heure de la journée au format 24 h. Résultat en minutes depuis minuit. */
@Composable
fun TimeOfDaySheet(title: String, minutes: Int?, onDismiss: () -> Unit, onResult: (Int?) -> Unit) {
    val hours = (0..23).toList()
    val mins = (0..55 step 5).toList()
    val v = minutes ?: (18 * 60)
    WheelSheet(
        title = title,
        wheels = listOf(
            WheelSpec(hours.map { it.toString().padStart(2, '0') }, v / 60),
            WheelSpec(mins.map { it.toString().padStart(2, '0') }, ((v % 60) / 5).coerceIn(0, mins.lastIndex)),
        ),
        separator = ":",
        onDismiss = onDismiss,
        onConfirm = { idx -> onResult(hours[idx[0]] * 60 + mins[idx[1]]) },
        onClear = { onResult(null) },
    )
}

/** Nombre entier simple (ex. minutes avant un rappel). */
@Composable
fun IntSheet(title: String, values: List<Int>, value: Int, suffix: String, onDismiss: () -> Unit, onResult: (Int) -> Unit) {
    WheelSheet(
        title = title,
        wheels = listOf(WheelSpec(values.map { "$it $suffix" }, values.indexOf(value).coerceAtLeast(0))),
        onDismiss = onDismiss,
        onConfirm = { onResult(values[it[0]]) },
    )
}
