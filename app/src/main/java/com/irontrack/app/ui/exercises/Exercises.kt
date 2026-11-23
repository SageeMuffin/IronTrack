package com.irontrack.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.ExerciseEntity
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.core.catalog.ExerciseCatalog
import com.irontrack.core.util.TextNormalizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel(private val c: AppContainer) : ViewModel() {
    val exercises = c.exercises.activeExercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(d: ExerciseDraft, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        onCreated(c.exercises.create(d.name, d.description, d.muscle, d.equipment, d.notes))
    }

    fun update(e: ExerciseEntity, d: ExerciseDraft) = viewModelScope.launch {
        c.exercises.update(e.copy(
            name = d.name.trim(),
            description = d.description.trim().ifBlank { null },
            muscleGroup = d.muscle.trim().ifBlank { null },
            equipment = d.equipment.trim().ifBlank { null },
            notes = d.notes.trim().ifBlank { null },
        ))
    }

    fun delete(e: ExerciseEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(c.exercises.deleteOrArchive(e)) }
}

data class ExerciseDraft(
    val name: String = "",
    val description: String = "",
    val muscle: String = "",
    val equipment: String = "",
    val notes: String = "",
)

private val muscleGroups = listOf(
    ExerciseCatalog.PECS, ExerciseCatalog.DOS, ExerciseCatalog.EPAULES, ExerciseCatalog.BICEPS, ExerciseCatalog.TRICEPS,
    ExerciseCatalog.QUADRICEPS, ExerciseCatalog.ISCHIOS, ExerciseCatalog.FESSIERS, ExerciseCatalog.MOLLETS, ExerciseCatalog.ABDOS,
    ExerciseCatalog.TRAPEZES, ExerciseCatalog.AVANT_BRAS, ExerciseCatalog.LOMBAIRES, ExerciseCatalog.ADDUCTEURS,
)

private fun filter(list: List<ExerciseEntity>, query: String, muscle: String?): List<ExerciseEntity> {
    val q = TextNormalizer.normalizeName(query)
    return list.filter { e ->
        (muscle == null || e.muscleGroup == muscle) &&
            (q.isBlank() || TextNormalizer.normalizeName(e.name).contains(q) ||
                e.catalogKey?.let { k -> ExerciseCatalog.byKey(k)?.let { ExerciseCatalog.aliases(it).any { a -> a.contains(q) } } } == true)
    }
}

/** Liste filtrable : recherche + filtre par groupe musculaire. */
@Composable
private fun ExerciseList(
    all: List<ExerciseEntity>,
    onClick: (ExerciseEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_exercise)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(muscleGroups) { m ->
                FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m) })
            }
        }
        val filtered = filter(all, query, muscle)
        LazyColumn(contentPadding = contentPadding) {
            if (filtered.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_exercise_found), modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(filtered, key = { it.id }) { e ->
                Column(Modifier.fillMaxWidth().clickable { onClick(e) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOfNotNull(e.muscleGroup, e.equipment, if (e.isCustom) stringResource(R.string.custom_exercise) else null)
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ExercisePickerDialog(onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val vm = appViewModel { ExercisesViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.choose_exercise)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) } },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.action_create_exercise)) })
            },
        ) { padding ->
            ExerciseList(all, onClick = { onPicked(it.id) }, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
    if (creating) {
        ExerciseEditDialog(
            title = stringResource(R.string.action_create_exercise),
            initial = ExerciseDraft(),
            onDismiss = { creating = false },
            onSave = { d -> creating = false; vm.create(d) { onPicked(it) } },
        )
    }
}

@Composable
fun ExerciseLibraryScreen(nav: NavHostController) {
    val vm = appViewModel { ExercisesViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var deleting by remember { mutableStateOf<ExerciseEntity?>(null) }
    var info by remember { mutableStateOf<Boolean?>(null) }

    AppScaffold(
        title = stringResource(R.string.exercise_library),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_create_exercise)) })
        },
    ) { padding ->
        ExerciseList(all, onClick = { editing = it }, modifier = Modifier.fillMaxSize().padding(padding))
    }

    if (creating) {
        ExerciseEditDialog(stringResource(R.string.action_create_exercise), ExerciseDraft(), onDismiss = { creating = false },
            onSave = { vm.create(it); creating = false })
    }
    editing?.let { e ->
        ExerciseEditDialog(
            title = stringResource(R.string.edit_exercise),
            initial = ExerciseDraft(e.name, e.description.orEmpty(), e.muscleGroup.orEmpty(), e.equipment.orEmpty(), e.notes.orEmpty()),
            onDismiss = { editing = null },
            onSave = { vm.update(e, it); editing = null },
            onDelete = { deleting = e; editing = null },
            onHistory = { editing = null; nav.navigate(Routes.exerciseStats(e.id)) },
        )
    }
    deleting?.let { e ->
        ConfirmDialog(stringResource(R.string.delete_exercise_title), stringResource(R.string.delete_exercise_message, e.name),
            stringResource(R.string.action_delete), destructive = true,
            onConfirm = { vm.delete(e) { deleted -> info = deleted }; deleting = null }, onDismiss = { deleting = null })
    }
    info?.let { deleted ->
        AlertDialog(
            onDismissRequest = { info = null },
            text = { Text(stringResource(if (deleted) R.string.exercise_deleted else R.string.exercise_archived)) },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
fun ExerciseEditDialog(
    title: String,
    initial: ExerciseDraft,
    onDismiss: () -> Unit,
    onSave: (ExerciseDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
) {
    var d by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text(stringResource(R.string.exercise_name)) }, singleLine = true)
                OutlinedTextField(d.muscle, { d = d.copy(muscle = it) }, label = { Text(stringResource(R.string.muscle_group_optional)) }, singleLine = true)
                OutlinedTextField(d.equipment, { d = d.copy(equipment = it) }, label = { Text(stringResource(R.string.equipment_optional)) }, singleLine = true)
                OutlinedTextField(d.description, { d = d.copy(description = it) }, label = { Text(stringResource(R.string.description_optional)) })
                OutlinedTextField(d.notes, { d = d.copy(notes = it) }, label = { Text(stringResource(R.string.notes_optional)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    onHistory?.let { TextButton(onClick = it) { Text(stringResource(R.string.view_history)) } }
                    onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(d) }, enabled = d.name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
