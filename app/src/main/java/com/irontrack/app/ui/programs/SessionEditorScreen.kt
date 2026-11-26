package com.irontrack.app.ui.programs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.PlannedSetEntity
import com.irontrack.app.data.db.SessionEntity
import com.irontrack.app.data.db.SessionExerciseEntity
import com.irontrack.app.data.db.SessionExerciseFull
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.DurationSheet
import com.irontrack.app.ui.components.RepsSheet
import com.irontrack.app.ui.components.RestSheet
import com.irontrack.app.ui.components.RirRangeSheet
import com.irontrack.app.ui.components.RpeSheet
import com.irontrack.app.ui.components.SectionTitle
import com.irontrack.app.ui.components.SettingRow
import com.irontrack.app.ui.components.TextInputDialog
import com.irontrack.app.ui.components.TimeOfDaySheet
import com.irontrack.app.ui.components.ValueCell
import com.irontrack.app.ui.components.WeightSheet
import com.irontrack.app.ui.exercises.ExercisePickerDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.weightText
import com.irontrack.core.util.FrenchFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionEditorViewModel(private val c: AppContainer, private val app: android.app.Application, val sessionId: Long) : ViewModel() {
    val session = c.programs.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateSession(f: (SessionEntity) -> SessionEntity) = viewModelScope.launch {
        val s = session.value?.session ?: return@launch
        c.programs.updateSession(f(s))
        ReminderScheduler.reschedule(app)
    }

    fun addExercise(exerciseId: Long) = viewModelScope.launch { c.programs.addExerciseToSession(sessionId, exerciseId) }
    fun removeExercise(id: Long) = viewModelScope.launch { c.programs.removeSessionExercise(id) }
    fun reorder(ids: List<Long>) = viewModelScope.launch { c.programs.reorderSessionExercises(sessionId, ids) }
    fun updateExercise(e: SessionExerciseEntity) = viewModelScope.launch { c.programs.updateSessionExercise(e) }
    fun addSet(seId: Long, copyOf: PlannedSetEntity? = null) = viewModelScope.launch { c.programs.addPlannedSet(seId, copyOf) }
    fun updateSet(s: PlannedSetEntity) = viewModelScope.launch { c.programs.updatePlannedSet(s) }
    fun removeSet(s: PlannedSetEntity) = viewModelScope.launch { c.programs.removePlannedSet(s) }

    val inProgress = c.workouts.inProgress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Lance cette séance maintenant, quel que soit le jour prévu (séance hors calendrier). */
    fun startNow(onStarted: (Long) -> Unit) = viewModelScope.launch {
        val s = session.value?.session ?: return@launch
        onStarted(c.workouts.startFromSession(s.id, null, s.week))
    }

    /** Crée N séries vides d'un coup (aucune valeur inventée). */
    fun addEmptySets(seId: Long, n: Int) = viewModelScope.launch { repeat(n) { c.programs.addPlannedSet(seId) } }
}

/** Champ en cours d'édition par molette. */
private sealed interface Editing {
    data class SetReps(val s: PlannedSetEntity) : Editing
    data class SetRir(val s: PlannedSetEntity) : Editing
    data class SetRpe(val s: PlannedSetEntity) : Editing
    data class SetWeight(val s: PlannedSetEntity) : Editing
    data class SetRest(val s: PlannedSetEntity) : Editing
    data class ExRest(val e: SessionExerciseEntity) : Editing
    data class ExReps(val e: SessionExerciseEntity) : Editing
    data class ExRir(val e: SessionExerciseEntity) : Editing
    data class ExWeight(val e: SessionExerciseEntity) : Editing
    data object Time : Editing
    data object Duration : Editing
}

@Composable
fun SessionEditorScreen(nav: NavHostController, sessionId: Long) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm = appViewModel(key = "session_$sessionId") { SessionEditorViewModel(it, app, sessionId) }
    val full by vm.session.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf<SessionExerciseEntity?>(null) }
    var removing by remember { mutableStateOf<SessionExerciseFull?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }
    val inProgress by vm.inProgress.collectAsStateWithLifecycle()
    val startNow = { vm.startNow { id -> nav.navigate(Routes.workout(id)) } }

    val s = full
    AppScaffold(
        title = s?.session?.name ?: "",
        onBack = { nav.popBackStack() },
        actions = {
            if (s != null && s.exercises.isNotEmpty()) {
                IconButton(onClick = { if (inProgress != null) confirmStart = true else startNow() }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_start_this_session))
                }
            }
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename)) }
        },
    ) { padding ->
        if (s == null) return@AppScaffold
        val days = stringArrayResource(R.array.weekdays)
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box {
                    SettingRow(
                        title = stringResource(R.string.session_day),
                        value = s.session.dayOfWeek?.let { days[it - 1] } ?: stringResource(R.string.not_scheduled),
                        onClick = { dayMenu = true },
                    )
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        days.forEachIndexed { i, d ->
                            DropdownMenuItem(text = { Text(d) }, onClick = { dayMenu = false; vm.updateSession { it.copy(dayOfWeek = i + 1) } })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.not_scheduled)) }, onClick = { dayMenu = false; vm.updateSession { it.copy(dayOfWeek = null) } })
                    }
                }
                SettingRow(
                    title = stringResource(R.string.session_time),
                    value = s.session.timeOfDayMinutes?.let { FrenchFormat.time(it) } ?: stringResource(R.string.not_set),
                    onClick = { editing = Editing.Time },
                )
                SettingRow(
                    title = stringResource(R.string.estimated_duration),
                    subtitle = stringResource(R.string.estimated_duration_hint),
                    value = s.session.estimatedDurationMinutes?.let { Fmt.minutes(it) } ?: stringResource(R.string.not_set),
                    onClick = { editing = Editing.Duration },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.session_enabled), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = s.session.enabled, onCheckedChange = { v -> vm.updateSession { it.copy(enabled = v) } })
                }
            }

            SectionTitle(stringResource(R.string.exercises_title))
            if (s.exercises.isEmpty()) {
                Text(stringResource(R.string.session_no_exercises), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReorderableExercises(
                items = s.sortedExercises,
                onReorder = vm::reorder,
            ) { ex, dragHandle ->
                SessionExerciseCard(
                    ex, dragHandle,
                    onEdit = { editing = it },
                    onAddSet = { vm.addSet(ex.item.id) },
                    onAddSets = { n -> vm.addEmptySets(ex.item.id, n) },
                    onDuplicateSet = { vm.addSet(ex.item.id, it) },
                    onRemoveSet = { vm.removeSet(it) },
                    onNotes = { editingNotes = ex.item },
                    onRemove = { removing = ex },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_exercise))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmStart) {
        ConfirmDialog(
            title = stringResource(R.string.home_already_in_progress_title),
            text = stringResource(R.string.home_already_in_progress_message),
            confirmLabel = stringResource(R.string.action_start_anyway),
            onConfirm = { confirmStart = false; startNow() },
            onDismiss = { confirmStart = false },
        )
    }
    if (picking) {
        ExercisePickerDialog(onDismiss = { picking = false }, onPicked = { id -> picking = false; vm.addExercise(id) })
    }
    if (renaming && s != null) {
        TextInputDialog(stringResource(R.string.action_rename), s.session.name, stringResource(R.string.session_name),
            onConfirm = { n -> vm.updateSession { it.copy(name = n) }; renaming = false }, onDismiss = { renaming = false })
    }
    editingNotes?.let { e ->
        TextInputDialog(stringResource(R.string.exercise_notes), e.notes.orEmpty(), stringResource(R.string.exercise_notes),
            allowEmpty = true, singleLine = false,
            onConfirm = { vm.updateExercise(e.copy(notes = it.ifBlank { null })); editingNotes = null }, onDismiss = { editingNotes = null })
    }
    removing?.let { e ->
        ConfirmDialog(stringResource(R.string.remove_exercise_title), stringResource(R.string.remove_exercise_message, e.exercise.name),
            stringResource(R.string.action_remove), destructive = true,
            onConfirm = { vm.removeExercise(e.item.id); removing = null }, onDismiss = { removing = null })
    }

    val close = { editing = null }
    when (val e = editing) {
        is Editing.SetReps -> RepsSheet(stringResource(R.string.target_reps), e.s.repsMin, e.s.repsMax, allowRange = true, onDismiss = close) { a, b ->
            vm.updateSet(e.s.copy(repsMin = a, repsMax = b)); close()
        }
        is Editing.SetRir -> RirRangeSheet(stringResource(R.string.target_rir), e.s.rir, e.s.rirMax, onDismiss = close) { a, b ->
            vm.updateSet(e.s.copy(rir = a, rirMax = b)); close()
        }
        is Editing.SetRpe -> RpeSheet(stringResource(R.string.target_rpe), e.s.rpe, onDismiss = close) { vm.updateSet(e.s.copy(rpe = it)); close() }
        is Editing.SetWeight -> WeightSheet(stringResource(R.string.target_weight), e.s.weightKg, onDismiss = close) { vm.updateSet(e.s.copy(weightKg = it)); close() }
        is Editing.SetRest -> RestSheet(stringResource(R.string.rest_time), e.s.restSeconds, onDismiss = close) { vm.updateSet(e.s.copy(restSeconds = it)); close() }
        is Editing.ExRest -> RestSheet(stringResource(R.string.exercise_rest), e.e.restSeconds, onDismiss = close) { vm.updateExercise(e.e.copy(restSeconds = it)); close() }
        is Editing.ExReps -> RepsSheet(stringResource(R.string.target_reps), e.e.targetRepsMin, e.e.targetRepsMax, allowRange = true, onDismiss = close) { a, b ->
            vm.updateExercise(e.e.copy(targetRepsMin = a, targetRepsMax = b)); close()
        }
        is Editing.ExRir -> RirRangeSheet(stringResource(R.string.target_rir), e.e.targetRir, e.e.targetRirMax, onDismiss = close) { a, b ->
            vm.updateExercise(e.e.copy(targetRir = a, targetRirMax = b)); close()
        }
        is Editing.ExWeight -> WeightSheet(stringResource(R.string.target_weight), e.e.targetWeightKg, onDismiss = close) { vm.updateExercise(e.e.copy(targetWeightKg = it)); close() }
        Editing.Time -> TimeOfDaySheet(stringResource(R.string.session_time), s?.session?.timeOfDayMinutes, onDismiss = close) { v ->
            vm.updateSession { it.copy(timeOfDayMinutes = v) }; close()
        }
        Editing.Duration -> DurationSheet(stringResource(R.string.estimated_duration), s?.session?.estimatedDurationMinutes, onDismiss = close) { v ->
            vm.updateSession { it.copy(estimatedDurationMinutes = v) }; close()
        }
        null -> {}
    }
}

/** Liste réordonnable par glisser-déposer (poignée à gauche de chaque exercice). */
@Composable
private fun ReorderableExercises(
    items: List<SessionExerciseFull>,
    onReorder: (List<Long>) -> Unit,
    content: @Composable (SessionExerciseFull, Modifier) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items.map { it.item.id }) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val byId = items.associateBy { it.item.id }

    Column {
        order.forEach { id ->
            val item = byId[id] ?: return@forEach
            val isDragging = dragging == id
            Box(
                Modifier
                    .onSizeChanged { heights[id] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f; shadowElevation = if (isDragging) 12f else 0f }
                    .padding(vertical = 4.dp)
            ) {
                val handle = Modifier.pointerInput(id, items) {
                    detectDragGestures(
                        onDragStart = { dragging = id; offset = 0f },
                        onDragEnd = { dragging = null; offset = 0f; onReorder(order) },
                        onDragCancel = { dragging = null; offset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            val idx = order.indexOf(id)
                            if (offset > 0 && idx < order.lastIndex) {
                                val h = heights[order[idx + 1]] ?: return@detectDragGestures
                                if (offset > h / 2f) {
                                    order = order.toMutableList().also { l -> l.removeAt(idx); l.add(idx + 1, id) }
                                    offset -= h
                                }
                            } else if (offset < 0 && idx > 0) {
                                val h = heights[order[idx - 1]] ?: return@detectDragGestures
                                if (-offset > h / 2f) {
                                    order = order.toMutableList().also { l -> l.removeAt(idx); l.add(idx - 1, id) }
                                    offset += h
                                }
                            }
                        },
                    )
                }
                content(item, handle)
            }
        }
    }
}

@Composable
private fun SessionExerciseCard(
    ex: SessionExerciseFull,
    dragHandle: Modifier,
    onEdit: (Editing) -> Unit,
    onAddSet: () -> Unit,
    onAddSets: (Int) -> Unit,
    onDuplicateSet: (PlannedSetEntity) -> Unit,
    onRemoveSet: (PlannedSetEntity) -> Unit,
    onNotes: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sets = ex.sortedSets
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }.padding(end = 8.dp)) {
                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.drag_to_reorder),
                    modifier = dragHandle.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (sets.isEmpty()) stringResource(R.string.no_planned_sets_short)
                        else pluralStringResource(R.plurals.sets_count, sets.size, sets.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SettingRow(stringResource(R.string.exercise_rest), ex.item.restSeconds?.let { Fmt.rest(it) } ?: stringResource(R.string.not_set),
                        onClick = { onEdit(Editing.ExRest(ex.item)) })
                    SettingRow(stringResource(R.string.exercise_notes), ex.item.notes ?: stringResource(R.string.not_set), onClick = onNotes)

                    if (sets.isEmpty()) {
                        Text(stringResource(R.string.no_planned_sets_explanation), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.default_targets), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            LabeledCell(stringResource(R.string.col_reps), Fmt.reps(ex.item.targetRepsMin, ex.item.targetRepsMax), Modifier.weight(1f)) { onEdit(Editing.ExReps(ex.item)) }
                            LabeledCell(stringResource(R.string.col_rir), Fmt.rir(ex.item.targetRir, ex.item.targetRirMax), Modifier.weight(1f)) { onEdit(Editing.ExRir(ex.item)) }
                            LabeledCell(stringResource(R.string.col_weight), weightText(ex.item.targetWeightKg), Modifier.weight(1.3f)) { onEdit(Editing.ExWeight(ex.item)) }
                        }
                        ex.item.targetRpe?.let {
                            Text(stringResource(R.string.rpe_original_value, Fmt.rpe(it), Fmt.approxRirFromRpe(it)), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4).forEach { n ->
                                OutlinedButton(onClick = { onAddSets(n) }, modifier = Modifier.weight(1f)) {
                                    Text(pluralStringResource(R.plurals.sets_count, n, n), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Row(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                            Text("#", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.col_reps), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.col_rir), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.col_weight), Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.col_rest), Modifier.weight(1.1f), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(40.dp))
                        }
                        sets.forEach { set ->
                            PlannedSetRow(set, onEdit, onDuplicateSet, onRemoveSet)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = onAddSet) {
                            Icon(Icons.Filled.Add, contentDescription = null); Text(stringResource(R.string.action_add_set))
                        }
                        if (sets.isNotEmpty()) {
                            TextButton(onClick = { onDuplicateSet(sets.last()) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null); Text(stringResource(R.string.action_duplicate_last_set))
                            }
                        }
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.action_remove_exercise), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedSetRow(
    set: PlannedSetEntity,
    onEdit: (Editing) -> Unit,
    onDuplicate: (PlannedSetEntity) -> Unit,
    onRemove: (PlannedSetEntity) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(set.setIndex.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.titleSmall)
            ValueCell(Fmt.reps(set.repsMin, set.repsMax), { onEdit(Editing.SetReps(set)) }, Modifier.weight(1f))
            ValueCell(Fmt.rir(set.rir, set.rirMax), { onEdit(Editing.SetRir(set)) }, Modifier.weight(1f))
            ValueCell(weightText(set.weightKg), { onEdit(Editing.SetWeight(set)) }, Modifier.weight(1.3f))
            ValueCell(set.restSeconds?.let { Fmt.rest(it) }, { onEdit(Editing.SetRest(set)) }, Modifier.weight(1.1f))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.target_rpe)) }, onClick = { menu = false; onEdit(Editing.SetRpe(set)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate(set) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onRemove(set) })
                }
            }
        }
        set.rpe?.let {
            Text(stringResource(R.string.rpe_original_value, Fmt.rpe(it), Fmt.approxRirFromRpe(it)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
        }
    }
}

@Composable
private fun LabeledCell(label: String, value: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ValueCell(value, onClick, Modifier.fillMaxWidth())
    }
}
