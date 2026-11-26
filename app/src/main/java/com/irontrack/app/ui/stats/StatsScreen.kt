package com.irontrack.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.CompletedSetRow
import com.irontrack.app.data.db.WorkoutSummary
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.EmptyState
import com.irontrack.app.ui.components.StatusPill
import com.irontrack.app.ui.components.WeekPill
import com.irontrack.app.ui.components.workoutStatusToEntry
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.weightText
import com.irontrack.core.util.Durations
import com.irontrack.core.util.Units
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Record personnel d'un exercice. */
data class ExerciseRecord(
    val exerciseId: Long,
    val name: String,
    val maxWeightKg: Double?,
    val repsAtMaxWeight: Int?,
    val best1rmKg: Double?,
    val maxReps: Int?,
    val lastAt: Long,
)

fun computeRecords(rows: List<CompletedSetRow>): List<ExerciseRecord> =
    rows.groupBy { it.exerciseId }.map { (id, list) ->
        val heaviest = list.filter { it.actualWeightKg != null }.maxWithOrNull(compareBy({ it.actualWeightKg }, { it.actualReps ?: 0 }))
        val best1rm = list.mapNotNull { r -> if (r.actualWeightKg != null && r.actualReps != null && r.actualReps > 0) Units.epley1rm(r.actualWeightKg, r.actualReps) else null }.maxOrNull()
        ExerciseRecord(
            exerciseId = id,
            name = list.first().exerciseName,
            maxWeightKg = heaviest?.actualWeightKg,
            repsAtMaxWeight = heaviest?.actualReps,
            best1rmKg = best1rm,
            maxReps = list.mapNotNull { it.actualReps }.maxOrNull(),
            lastAt = list.maxOf { it.startedAt },
        )
    }.sortedBy { it.name.lowercase() }

class StatsViewModel(c: AppContainer) : ViewModel() {
    val history = c.workouts.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val exercises = c.workouts.trainedExercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val records = c.workouts.allCompletedSets.map(::computeRecords).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun StatsScreen(nav: NavHostController) {
    val vm = appViewModel { StatsViewModel(it) }
    val history by vm.history.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AppScaffold(title = stringResource(R.string.nav_stats)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tab_history)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_exercises)) })
                Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.tab_records)) })
                Tab(tab == 3, { tab = 3 }, text = { Text(stringResource(R.string.tab_body)) })
            }
            when (tab) {
                0 -> HistoryList(history, onOpen = { nav.navigate(Routes.workoutDetail(it)) })
                1 -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (exercises.isEmpty()) item { EmptyState(stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_message)) }
                    items(exercises, key = { it.id }) { e ->
                        Column(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.exerciseStats(e.id)) }.padding(16.dp)) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            e.muscleGroup?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider()
                    }
                }
                3 -> BodyTab(nav)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (records.isEmpty()) item { EmptyState(stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_message)) }
                    items(records, key = { it.exerciseId }) { r -> RecordCard(r) { nav.navigate(Routes.exerciseStats(r.exerciseId)) } }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(history: List<WorkoutSummary>?, onOpen: (Long) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (history != null && history.isEmpty()) {
            item { EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message)) }
        }
        items(history.orEmpty(), key = { it.id }) { w ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(w.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusPill(workoutStatusToEntry(w.status))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.date(w.startedAt) + " · " + Fmt.time(w.startedAt), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WeekPill(w.week)
                    }
                    if (w.status != "NOT_DONE") {
                        Text(
                            pluralStringResource(R.plurals.exercises_count, w.exerciseCount, w.exerciseCount) + " · " +
                                pluralStringResource(R.plurals.completed_sets_count, w.completedSets, w.completedSets),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        DurationLine(w.estimatedDurationMinutes, w.startedAt, w.endedAt)
                    }
                }
            }
        }
    }
}

/** « Estimé : 1h15 · Réel : 1h22 · Écart : +7 min » — seules les valeurs connues sont affichées. */
@Composable
fun DurationLine(estimatedMinutes: Int?, startedAt: Long, endedAt: Long?) {
    val actual = endedAt?.let { ((it - startedAt) / 60000).toInt() }
    val parts = buildList {
        estimatedMinutes?.let { add(stringResource(R.string.duration_estimated, Fmt.minutes(it))) }
        actual?.let { add(stringResource(R.string.duration_actual, Fmt.minutes(it))) }
        Durations.difference(estimatedMinutes, actual)?.let { add(stringResource(R.string.duration_difference, it)) }
    }
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecordCard(r: ExerciseRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            weightText(r.maxWeightKg)?.let { w ->
                Text(stringResource(R.string.record_max_weight, w + (r.repsAtMaxWeight?.let { " × $it" } ?: "")), style = MaterialTheme.typography.bodyMedium)
            }
            weightText(r.best1rmKg)?.let { Text(stringResource(R.string.record_e1rm, it), style = MaterialTheme.typography.bodyMedium) }
            r.maxReps?.let { Text(stringResource(R.string.record_max_reps, it), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.last_done_on, Fmt.date(r.lastAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
