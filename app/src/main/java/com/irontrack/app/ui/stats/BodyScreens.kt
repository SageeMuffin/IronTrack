package com.irontrack.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.BodyMeasurementEntity
import com.irontrack.app.data.repo.BodyRepository
import com.irontrack.app.data.repo.WeightUnitPref
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.EmptyState
import com.irontrack.app.ui.components.WheelSheet
import com.irontrack.app.ui.components.WheelSpec
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.unitLabel
import com.irontrack.core.body.BodyMetric
import com.irontrack.core.body.BodyStats
import com.irontrack.core.body.MeasureUnit
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.Units
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Libellés et unités
// ---------------------------------------------------------------------------------------------

@Composable
fun metricLabel(m: BodyMetric): String = stringResource(
    when (m) {
        BodyMetric.WEIGHT -> R.string.body_weight
        BodyMetric.BODY_FAT -> R.string.body_fat
        BodyMetric.NECK -> R.string.body_neck
        BodyMetric.SHOULDERS -> R.string.body_shoulders
        BodyMetric.CHEST -> R.string.body_chest
        BodyMetric.WAIST -> R.string.body_waist
        BodyMetric.HIPS -> R.string.body_hips
        BodyMetric.ARM -> R.string.body_arm
        BodyMetric.FOREARM -> R.string.body_forearm
        BodyMetric.THIGH -> R.string.body_thigh
        BodyMetric.CALF -> R.string.body_calf
    }
)

@Composable
fun metricUnit(m: BodyMetric): String = when (m.unit) {
    MeasureUnit.KG -> unitLabel()
    MeasureUnit.PERCENT -> "%"
    MeasureUnit.CM -> stringResource(R.string.unit_cm)
}

/** Valeur stockée (kg, %, cm) → valeur affichée (le poids suit l'unité choisie dans les paramètres). */
private fun toDisplay(m: BodyMetric, v: Double, unit: WeightUnitPref): Double =
    if (m.unit == MeasureUnit.KG) Fmt.toDisplayWeight(v, unit) else v

private fun fromDisplay(m: BodyMetric, v: Double, unit: WeightUnitPref): Double =
    if (m.unit == MeasureUnit.KG) Fmt.fromDisplayWeight(v, unit) else v

@Composable
private fun valueText(m: BodyMetric, v: Double): String {
    val unit = LocalAppSettings.current.weightUnit
    return "${FrenchFormat.number(toDisplay(m, v, unit), 1)} ${metricUnit(m)}"
}

/** Écart signé : « +1,2 kg », « −0,5 cm », « = » si identique. */
@Composable
private fun changeText(m: BodyMetric, delta: Double): String {
    val unit = LocalAppSettings.current.weightUnit
    val d = if (m.unit == MeasureUnit.KG) Fmt.toDisplayWeight(delta, unit) else delta
    val rounded = (d * 10).roundToInt() / 10.0
    return when {
        rounded > 0 -> "+${FrenchFormat.number(rounded, 1)} ${metricUnit(m)}"
        rounded < 0 -> "−${FrenchFormat.number(-rounded, 1)} ${metricUnit(m)}"
        else -> stringResource(R.string.body_no_change)
    }
}

private fun dayMillis(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

// ---------------------------------------------------------------------------------------------
// Onglet « Corps » de l'écran Performances
// ---------------------------------------------------------------------------------------------

class BodyViewModel(private val c: AppContainer) : ViewModel() {
    val all = c.body.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(m: BodyMetric, date: LocalDate, value: Double) = viewModelScope.launch { c.body.save(m, date, value) }
}

@Composable
fun BodyTab(nav: NavHostController) {
    val vm = appViewModel { BodyViewModel(it) }
    val all by vm.all.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<BodyMetric?>(null) }
    val data = all

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.body_add))
            }
        }
        if (data != null && data.isEmpty()) {
            item { EmptyState(stringResource(R.string.body_empty_title), stringResource(R.string.body_empty_message)) }
        }
        val tracked = BodyMetric.entries.filter { !data?.get(it).isNullOrEmpty() }
        items(tracked, key = { it.key }) { m ->
            MetricCard(m, data!![m].orEmpty()) { nav.navigate(Routes.bodyMetric(m.key)) }
        }
    }

    if (choosing) {
        MetricPickerDialog(onPick = { choosing = false; adding = it }, onDismiss = { choosing = false })
    }
    adding?.let { m ->
        val last = data?.get(m)?.maxByOrNull { it.epochDay }?.value
        MeasureValueSheet(m, last, onDismiss = { adding = null }) { date, v -> vm.save(m, date, v); adding = null }
    }
}

@Composable
private fun MetricCard(m: BodyMetric, list: List<BodyMeasurementEntity>, onClick: () -> Unit) {
    val s = BodyStats.summary(BodyRepository.points(list)) ?: return
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metricLabel(m), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueText(m, s.latest.value), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.body_measured_on, Fmt.date(LocalDate.ofEpochDay(s.latest.epochDay))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.changeSincePrevious?.let {
                Text(stringResource(R.string.body_change_previous, changeText(m, it)), style = MaterialTheme.typography.bodyMedium)
            }
            s.changeSinceFirst?.let {
                Text(stringResource(R.string.body_change_first, changeText(m, it), Fmt.date(LocalDate.ofEpochDay(s.first.epochDay))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricPickerDialog(onPick: (BodyMetric) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_pick_metric)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(BodyMetric.entries.toList(), key = { it.key }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(metricLabel(m), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(metricUnit(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Saisie d'une valeur par molettes (partie entière + dixième), avec la date (aujourd'hui par défaut,
 * modifiable pour saisir une mesure passée). [lastValue] (stockée) sert de point de départ.
 */
@Composable
fun MeasureValueSheet(m: BodyMetric, lastValue: Double?, onDismiss: () -> Unit, onSave: (LocalDate, Double) -> Unit) {
    val unit = LocalAppSettings.current.weightUnit
    val lb = m.unit == MeasureUnit.KG && unit == WeightUnitPref.LB
    val lo = if (lb) Units.kgToLb(m.min.toDouble()).toInt() else m.min
    val hi = if (lb) Units.kgToLb(m.max.toDouble()).roundToInt() else m.max
    val ints = (lo..hi).toList()
    val start = toDisplay(m, lastValue ?: m.default, unit)
    val startInt = start.toInt().coerceIn(lo, hi)
    val startDec = ((start - start.toInt()) * 10).roundToInt().coerceIn(0, 9)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }

    WheelSheet(
        title = metricLabel(m),
        wheels = listOf(
            WheelSpec(ints.map { it.toString() }, ints.indexOf(startInt), weight = 1.4f),
            WheelSpec((0..9).map { "$it ${metricUnit(m)}" }, startDec),
        ),
        separator = ",",
        onDismiss = onDismiss,
        onConfirm = { idx -> onSave(date, fromDisplay(m, ints[idx[0]] + idx[1] / 10.0, unit)) },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.body_date_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingDate = true }) {
                    Text(if (date == LocalDate.now()) stringResource(R.string.today) else Fmt.date(date))
                }
            }
        },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dayMillis(date.toEpochDay()))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        date = if (d.isAfter(LocalDate.now())) LocalDate.now() else d
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_validate)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

// ---------------------------------------------------------------------------------------------
// Détail d'une mesure : graphique et historique
// ---------------------------------------------------------------------------------------------

class BodyMetricViewModel(private val c: AppContainer, val metric: BodyMetric) : ViewModel() {
    val entries = c.body.observe(metric).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(date: LocalDate, value: Double) = viewModelScope.launch { c.body.save(metric, date, value) }
    fun delete(id: Long) = viewModelScope.launch { c.body.delete(id) }
}

@Composable
fun BodyMetricScreen(nav: NavHostController, key: String) {
    val metric = BodyMetric.fromKey(key) ?: return
    val vm = appViewModel(key = "body_$key") { BodyMetricViewModel(it, metric) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
    val unit = LocalAppSettings.current.weightUnit
    val list = entries.orEmpty().sortedBy { it.epochDay }

    AppScaffold(
        title = metricLabel(metric),
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.body_add)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.body_empty_title), stringResource(R.string.body_empty_message)) }
            }
            if (list.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        LineChart(
                            points = list.map { ChartPoint(dayMillis(it.epochDay), toDisplay(metric, it.value, unit)) },
                            title = stringResource(R.string.body_chart_title),
                            unit = metricUnit(metric),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item { Text(stringResource(R.string.body_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                val reversed = list.reversed()
                items(reversed, key = { it.id }) { e ->
                    val prev = list.getOrNull(list.indexOf(e) - 1)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(valueText(metric, e.value), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                Fmt.date(LocalDate.ofEpochDay(e.epochDay)) + (prev?.let { " · " + changeText(metric, e.value - it.value) } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deleting = e }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        MeasureValueSheet(metric, list.lastOrNull()?.value, onDismiss = { adding = false }) { d, v -> vm.save(d, v); adding = false }
    }
    deleting?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.body_delete_title),
            text = stringResource(R.string.body_delete_message, valueText(metric, e.value), Fmt.date(LocalDate.ofEpochDay(e.epochDay))),
            confirmLabel = stringResource(R.string.action_delete), destructive = true,
            onConfirm = { vm.delete(e.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
}
