package com.irontrack.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.irontrack.app.data.db.ExerciseSetHistoryRow
import com.irontrack.app.data.db.WorkoutExerciseFull
import com.irontrack.app.data.repo.PreviousPerformance
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appContainer
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.SectionTitle
import com.irontrack.app.ui.components.StatusPill
import com.irontrack.app.ui.components.WeekPill
import com.irontrack.app.ui.components.workoutStatusToEntry
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.targetSummary
import com.irontrack.app.ui.unitLabel
import com.irontrack.app.ui.weightText
import com.irontrack.app.ui.workout.previousSetText
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.Units
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------- détail d'une séance

class WorkoutDetailViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    val workout = c.workouts.observeWorkout(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.workouts.delete(id); onDone() }
    fun reopen(onDone: () -> Unit) = viewModelScope.launch { c.workouts.reopen(id); onDone() }
}

@Composable
fun WorkoutDetailScreen(nav: NavHostController, workoutId: Long) {
    val vm = appViewModel(key = "detail_$workoutId") { WorkoutDetailViewModel(it, workoutId) }
    val full by vm.workout.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    val w = full
    AppScaffold(
        title = w?.workout?.name ?: "",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { vm.reopen { nav.navigate(Routes.workout(workoutId)) { popUpTo(Routes.WORKOUT_DETAIL) { inclusive = true } } } }) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit_workout))
            }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
        },
    ) { padding ->
        if (w == null) return@AppScaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(workoutStatusToEntry(w.workout.status))
                    WeekPill(w.workout.week)
                }
                Text(Fmt.date(w.workout.startedAt) + " · " + Fmt.time(w.workout.startedAt), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp))
                DurationLine(w.workout.estimatedDurationMinutes, w.workout.startedAt, w.workout.endedAt)
                w.workout.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
            }
            items(w.sortedExercises, key = { it.item.id }) { ex -> ExerciseResultCard(ex, w.workout.id, w.workout.startedAt, onOpenStats = { nav.navigate(Routes.exerciseStats(ex.exercise.id)) }) }
        }
    }
    if (deleting) {
        ConfirmDialog(stringResource(R.string.delete_workout_title), stringResource(R.string.delete_workout_message),
            stringResource(R.string.action_delete), destructive = true,
            onConfirm = { deleting = false; vm.delete { nav.popBackStack() } }, onDismiss = { deleting = false })
    }
}

@Composable
private fun ExerciseResultCard(ex: WorkoutExerciseFull, workoutId: Long, startedAt: Long, onOpenStats: () -> Unit) {
    val c = appContainer()
    val prev by produceState<PreviousPerformance?>(null, ex.exercise.id, workoutId) {
        value = c.workouts.previousPerformance(ex.exercise.id, workoutId, before = startedAt)
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenStats)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val sets = ex.sortedSets
            if (sets.isEmpty()) Text(stringResource(R.string.no_sets_recorded), style = MaterialTheme.typography.bodySmall)
            sets.forEach { s ->
                Row {
                    Text("${s.setIndex}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (s.completed) previousSetText(s) ?: stringResource(R.string.set_done_no_values) else stringResource(R.string.set_not_done),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s.completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        targetSummary(s.targetRepsMin, s.targetRepsMax, s.targetRir, s.targetRirMax, s.targetRpe, s.targetWeightKg, null)?.let {
                            Text(stringResource(R.string.target_prefix, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Comparaison avec la séance précédente.
            val p = prev
            if (p != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.compare_previous, Fmt.date(p.startedAt)), style = MaterialTheme.typography.labelLarge)
                val curMax = sets.filter { it.completed }.mapNotNull { it.actualWeightKg }.maxOrNull()
                val prevMax = p.sets.mapNotNull { it.actualWeightKg }.maxOrNull()
                val curReps = sets.filter { it.completed }.sumOf { it.actualReps ?: 0 }
                val prevReps = p.sets.sumOf { it.actualReps ?: 0 }
                if (curMax != null && prevMax != null) {
                    val unit = LocalAppSettings.current.weightUnit
                    val diff = Fmt.toDisplayWeight(curMax, unit) - Fmt.toDisplayWeight(prevMax, unit)
                    Text(stringResource(R.string.compare_max_weight, signed(diff), unitLabel()), style = MaterialTheme.typography.bodySmall)
                }
                if (curReps > 0 && prevReps > 0) {
                    Text(stringResource(R.string.compare_total_reps, signed((curReps - prevReps).toDouble())), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun signed(v: Double): String = (if (v > 0) "+" else if (v < 0) "-" else "±") + FrenchFormat.number(kotlin.math.abs(v), 1)

// ------------------------------------------------------------------------- historique d'un exercice

class ExerciseStatsViewModel(c: AppContainer, id: Long) : ViewModel() {
    val exercise = c.exercises.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val rows = c.workouts.observeExerciseHistory(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private data class SessionAgg(
    val workoutId: Long,
    val name: String,
    val at: Long,
    val sets: List<ExerciseSetHistoryRow>,
) {
    val maxWeight = sets.mapNotNull { it.actualWeightKg }.maxOrNull()
    val totalReps = sets.mapNotNull { it.actualReps }.takeIf { it.isNotEmpty() }?.sum()
    val avgRir = sets.mapNotNull { it.actualRir }.takeIf { it.isNotEmpty() }?.average()
    val best1rm = sets.mapNotNull { s -> if (s.actualWeightKg != null && s.actualReps != null && s.actualReps > 0) Units.epley1rm(s.actualWeightKg, s.actualReps) else null }.maxOrNull()
}

@Composable
fun ExerciseStatsScreen(nav: NavHostController, exerciseId: Long) {
    val vm = appViewModel(key = "exstats_$exerciseId") { ExerciseStatsViewModel(it, exerciseId) }
    val exercise by vm.exercise.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.weightUnit
    val ul = unitLabel()
    val sessions = rows.groupBy { it.workoutId }.map { (id, l) -> SessionAgg(id, l.first().workoutName, l.first().startedAt, l.sortedBy { it.setIndex }) }.sortedBy { it.at }

    AppScaffold(title = exercise?.name ?: "", onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sessions.isEmpty()) {
                item { Text(stringResource(R.string.no_history_for_exercise), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }
            item {
                val maxW = sessions.mapNotNull { it.maxWeight }.maxOrNull()
                val best = sessions.mapNotNull { it.best1rm }.maxOrNull()
                val maxReps = rows.mapNotNull { it.actualReps }.maxOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.personal_records), style = MaterialTheme.typography.titleMedium)
                        weightText(maxW)?.let { Text(stringResource(R.string.record_max_weight, it)) }
                        weightText(best)?.let { Text(stringResource(R.string.record_e1rm, it)) }
                        maxReps?.let { Text(stringResource(R.string.record_max_reps, it)) }
                        Text(stringResource(R.string.sessions_done, sessions.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LineChart(sessions.mapNotNull { s -> s.maxWeight?.let { ChartPoint(s.at, Fmt.toDisplayWeight(it, unit)) } },
                            stringResource(R.string.chart_max_weight), ul)
                        LineChart(sessions.mapNotNull { s -> s.best1rm?.let { ChartPoint(s.at, Fmt.toDisplayWeight(it, unit)) } },
                            stringResource(R.string.chart_e1rm), ul)
                        LineChart(sessions.mapNotNull { s -> s.totalReps?.let { ChartPoint(s.at, it.toDouble()) } },
                            stringResource(R.string.chart_total_reps), stringResource(R.string.unit_reps))
                        LineChart(sessions.mapNotNull { s -> s.avgRir?.let { ChartPoint(s.at, it) } },
                            stringResource(R.string.chart_avg_rir), stringResource(R.string.unit_rir))
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.tab_history)) }
            items(sessions.reversed(), key = { it.workoutId }) { s ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.workoutDetail(s.workoutId)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(Fmt.date(s.at) + " · " + s.name, style = MaterialTheme.typography.titleSmall)
                        s.sets.forEach { r ->
                            val parts = buildList {
                                val w = weightText(r.actualWeightKg)
                                when {
                                    w != null && r.actualReps != null -> add("$w × ${r.actualReps}")
                                    w != null -> add(w)
                                    r.actualReps != null -> add(stringResource(R.string.fmt_reps, r.actualReps.toString()))
                                }
                                r.actualRir?.let { add(stringResource(R.string.fmt_rir, it.toString())) }
                            }
                            Text("${r.setIndex}. " + (parts.joinToString(" · ").ifEmpty { stringResource(R.string.set_done_no_values) }), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
