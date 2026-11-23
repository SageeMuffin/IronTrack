package com.irontrack.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.WorkoutExerciseFull
import com.irontrack.app.data.db.WorkoutSetEntity
import com.irontrack.app.data.repo.PreviousPerformance
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appContainer
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.RepsSheet
import com.irontrack.app.ui.components.RirSheet
import com.irontrack.app.ui.components.TextInputDialog
import com.irontrack.app.ui.components.ValueCell
import com.irontrack.app.ui.components.WeightSheet
import com.irontrack.app.ui.exercises.ExercisePickerDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.targetSummary
import com.irontrack.app.ui.theme.SuccessGreen
import com.irontrack.app.ui.weightText
import com.irontrack.core.util.Durations
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(private val c: AppContainer, val workoutId: Long) : ViewModel() {
    val workout = c.workouts.observeWorkout(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _previous = MutableStateFlow<Map<Long, PreviousPerformance?>>(emptyMap())
    val previous: StateFlow<Map<Long, PreviousPerformance?>> = _previous.asStateFlow()

    /** Fin du repos en cours (horodatage), ou null. */
    private val _restEndsAt = MutableStateFlow<Long?>(null)
    val restEndsAt: StateFlow<Long?> = _restEndsAt.asStateFlow()

    fun loadPrevious(exerciseIds: List<Long>) = viewModelScope.launch {
        val missing = exerciseIds.filter { it !in _previous.value }
        if (missing.isEmpty()) return@launch
        _previous.value = _previous.value + missing.associateWith { c.workouts.previousPerformance(it, workoutId) }
    }

    fun update(set: WorkoutSetEntity) = viewModelScope.launch { c.workouts.updateSet(set) }

    fun toggleCompleted(set: WorkoutSetEntity, restSeconds: Int?) = viewModelScope.launch {
        val done = !set.completed
        c.workouts.updateSet(set.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null))
        if (done && restSeconds != null && restSeconds > 0) _restEndsAt.value = System.currentTimeMillis() + restSeconds * 1000L
    }

    fun skipRest() { _restEndsAt.value = null }
    fun addRest(seconds: Int) { _restEndsAt.value = (_restEndsAt.value ?: System.currentTimeMillis()) + seconds * 1000L }

    fun addSet(weId: Long) = viewModelScope.launch { c.workouts.addSet(weId) }
    fun removeSet(set: WorkoutSetEntity) = viewModelScope.launch { c.workouts.removeSet(set) }
    fun addExercise(exerciseId: Long) = viewModelScope.launch { c.workouts.addExercise(workoutId, exerciseId) }
    fun removeExercise(weId: Long) = viewModelScope.launch { c.workouts.removeExercise(weId) }
    fun setNotes(notes: String) = viewModelScope.launch {
        workout.value?.workout?.let { c.workouts.updateWorkout(it.copy(notes = notes.ifBlank { null })) }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch { c.workouts.finish(workoutId); onDone() }
    fun discard(onDone: () -> Unit) = viewModelScope.launch { c.workouts.delete(workoutId); onDone() }
}

private sealed interface SetField {
    val set: WorkoutSetEntity
    data class Weight(override val set: WorkoutSetEntity, val hint: Double?) : SetField
    data class Reps(override val set: WorkoutSetEntity, val hint: Int?) : SetField
    data class Rir(override val set: WorkoutSetEntity, val hint: Int?) : SetField
}

@Composable
fun WorkoutScreen(nav: NavHostController, workoutId: Long) {
    val vm = appViewModel(key = "workout_$workoutId") { WorkoutViewModel(it, workoutId) }
    val full by vm.workout.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()
    val restEndsAt by vm.restEndsAt.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var editing by remember { mutableStateOf<SetField?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // L'écran reste allumé pendant la séance.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val w = full
    LaunchedEffect(w?.exercises?.map { it.exercise.id }) {
        w?.let { vm.loadPrevious(it.exercises.map { e -> e.exercise.id }) }
    }
    // Séance déjà terminée : afficher le détail.
    LaunchedEffect(w?.workout?.status) {
        if (w != null && w.workout.status != "IN_PROGRESS") {
            nav.navigate(Routes.workoutDetail(workoutId)) { popUpTo(Routes.WORKOUT) { inclusive = true } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(w?.workout?.name ?: "", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        if (w != null) ElapsedHeader(w.workout.startedAt, w.workout.estimatedDurationMinutes.takeIf { settings.showEstimatedDuringWorkout })
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) } },
                actions = {
                    TextButton(onClick = { confirmFinish = true }) { Text(stringResource(R.string.action_finish_workout)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.workout_notes)) }, onClick = { menu = false; editNotes = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_discard_workout)) }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                },
            )
        },
        bottomBar = { restEndsAt?.let { RestBanner(it, onSkip = vm::skipRest, onAdd = { vm.addRest(30) }) } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (w == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(w.sortedExercises, key = { it.item.id }) { ex ->
                ExerciseBlock(
                    ex = ex,
                    previous = previous[ex.exercise.id],
                    defaultRest = settings.defaultRestSeconds,
                    onEdit = { editing = it },
                    onToggle = { set -> vm.toggleCompleted(set, set.targetRestSeconds ?: ex.item.restSeconds ?: settings.defaultRestSeconds) },
                    onAddSet = { vm.addSet(ex.item.id) },
                    onRemoveSet = vm::removeSet,
                    onRemoveExercise = { vm.removeExercise(ex.item.id) },
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_add_exercise))
                }
            }
            item {
                Button(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.action_finish_workout), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (val e = editing) {
        is SetField.Weight -> WeightSheet(stringResource(R.string.actual_weight), e.set.actualWeightKg, hintKg = e.hint, onDismiss = { editing = null }) {
            vm.update(e.set.copy(actualWeightKg = it)); editing = null
        }
        is SetField.Reps -> RepsSheet(stringResource(R.string.actual_reps), e.set.actualReps, e.set.actualReps, allowRange = false, hint = e.hint, onDismiss = { editing = null }) { a, _ ->
            vm.update(e.set.copy(actualReps = a)); editing = null
        }
        is SetField.Rir -> RirSheet(stringResource(R.string.actual_rir), e.set.actualRir, hint = e.hint, onDismiss = { editing = null }) {
            vm.update(e.set.copy(actualRir = it)); editing = null
        }
        null -> {}
    }
    if (picking) ExercisePickerDialog(onDismiss = { picking = false }, onPicked = { picking = false; vm.addExercise(it) })
    if (editNotes) {
        TextInputDialog(stringResource(R.string.workout_notes), w?.workout?.notes.orEmpty(), stringResource(R.string.workout_notes),
            allowEmpty = true, singleLine = false, onConfirm = { vm.setNotes(it); editNotes = false }, onDismiss = { editNotes = false })
    }
    if (confirmFinish && w != null) {
        val planned = w.exercises.sumOf { e -> e.sets.count { it.planned } }
        val done = w.exercises.sumOf { e -> e.sets.count { it.completed } }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_workout_title)) },
            text = {
                Text(
                    if (done == 0) stringResource(R.string.finish_workout_nothing_done)
                    else stringResource(R.string.finish_workout_message, done, planned)
                )
            },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finish {} }) { Text(stringResource(R.string.action_finish_workout)) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_continue)) } },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(stringResource(R.string.discard_workout_title), stringResource(R.string.discard_workout_message),
            stringResource(R.string.action_discard_workout), destructive = true,
            onConfirm = { confirmDiscard = false; vm.discard { nav.popBackStack() } }, onDismiss = { confirmDiscard = false })
    }
}

@Composable
private fun ElapsedHeader(startedAt: Long, estimatedMinutes: Int?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val elapsed = Durations.formatClock(((now - startedAt) / 1000).coerceAtLeast(0))
    val text = if (estimatedMinutes != null) stringResource(R.string.elapsed_with_estimate, elapsed, Fmt.minutes(estimatedMinutes))
    else stringResource(R.string.elapsed, elapsed)
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RestBanner(endsAt: Long, onSkip: () -> Unit, onAdd: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val remaining = ((endsAt - now) / 1000).coerceAtLeast(0)
    LaunchedEffect(remaining == 0L) { if (remaining == 0L) { delay(3000); onSkip() } }
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (remaining == 0L) R.string.rest_over else R.string.rest_in_progress), style = MaterialTheme.typography.labelLarge)
                Text(Durations.formatClock(remaining), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.rest_add_30)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.rest_skip)) }
        }
    }
}

@Composable
private fun ExerciseBlock(
    ex: WorkoutExerciseFull,
    previous: PreviousPerformance?,
    defaultRest: Int?,
    onEdit: (SetField) -> Unit,
    onToggle: (WorkoutSetEntity) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (WorkoutSetEntity) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ex.item.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_exercise)) }, onClick = { menu = false; onRemoveExercise() })
                    }
                }
            }
            PreviousSummary(previous)
            val restTxt = (ex.item.restSeconds ?: defaultRest)?.let { stringResource(R.string.fmt_rest, Fmt.rest(it)) }
            restTxt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.col_set), Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.col_weight), Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.col_reps), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.col_rir), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(48.dp))
            }
            val sets = ex.sortedSets
            if (sets.isEmpty()) {
                Text(stringResource(R.string.workout_no_sets_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            sets.forEach { set ->
                val prev = previous?.sets?.firstOrNull { it.setIndex == set.setIndex }
                SetRow(set, prev, onEdit, onToggle, onRemoveSet)
            }
            TextButton(onClick = onAddSet) { Icon(Icons.Filled.Add, null); Text(stringResource(R.string.action_add_set)) }
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSetEntity,
    prev: WorkoutSetEntity?,
    onEdit: (SetField) -> Unit,
    onToggle: (WorkoutSetEntity) -> Unit,
    onRemove: (WorkoutSetEntity) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val target = targetSummary(set.targetRepsMin, set.targetRepsMax, set.targetRir, set.targetRirMax, set.targetRpe, set.targetWeightKg, null)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.width(32.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(set.setIndex.toString(), style = MaterialTheme.typography.titleSmall)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_set)) }, onClick = { menu = false; onRemove(set) })
                }
            }
            ValueCell(weightText(set.actualWeightKg), { onEdit(SetField.Weight(set, set.targetWeightKg ?: prev?.actualWeightKg)) }, Modifier.weight(1.3f), highlighted = set.completed)
            ValueCell(set.actualReps?.toString(), { onEdit(SetField.Reps(set, set.targetRepsMax ?: set.targetRepsMin ?: prev?.actualReps)) }, Modifier.weight(1f), highlighted = set.completed)
            ValueCell(set.actualRir?.toString(), { onEdit(SetField.Rir(set, set.targetRir ?: prev?.actualRir)) }, Modifier.weight(1f), highlighted = set.completed)
            IconButton(onClick = { onToggle(set) }, modifier = Modifier.size(44.dp)) {
                Box(
                    Modifier.size(32.dp).background(if (set.completed) SuccessGreen else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_validate_set),
                        tint = if (set.completed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val info = buildList {
            target?.let { add(stringResource(R.string.target_prefix, it)) }
            prev?.let { p -> previousSetText(p)?.let { add(stringResource(R.string.previous_prefix, it)) } }
            if (!set.planned) add(stringResource(R.string.extra_set))
        }
        if (info.isNotEmpty()) {
            Text(info.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp))
        }
    }
}

/** « 60 kg × 10 · RIR 2 » ; ne montre que les valeurs réellement enregistrées. */
@Composable
fun previousSetText(s: WorkoutSetEntity): String? {
    val w = weightText(s.actualWeightKg)
    val parts = buildList {
        when {
            w != null && s.actualReps != null -> add("$w × ${s.actualReps}")
            w != null -> add(w)
            s.actualReps != null -> add(stringResource(R.string.fmt_reps, s.actualReps.toString()))
        }
        s.actualRir?.let { add(stringResource(R.string.fmt_rir, it.toString())) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun PreviousSummary(p: PreviousPerformance?) {
    if (p == null || p.sets.isEmpty()) {
        Text(stringResource(R.string.no_previous_performance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val txt = p.sets.sortedBy { it.setIndex }.mapNotNull { previousSetText(it) }.joinToString("  |  ")
    Text(
        stringResource(R.string.previous_performance_on, Fmt.date(p.startedAt)) + if (txt.isNotEmpty()) " : $txt" else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
    )
}

/** Performance précédente d'un exercice, chargée à la demande (écran d'accueil). */
@Composable
fun PreviousPerformanceText(exerciseId: Long, excludeWorkoutId: Long) {
    val c = appContainer()
    val prev by produceState<PreviousPerformance?>(null, exerciseId) { value = c.workouts.previousPerformance(exerciseId, excludeWorkoutId) }
    prev?.let { PreviousSummary(it) }
}
