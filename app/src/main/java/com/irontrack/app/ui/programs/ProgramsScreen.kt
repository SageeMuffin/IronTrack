package com.irontrack.app.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.irontrack.app.share.shareProgram
import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.EmptyState
import com.irontrack.app.ui.components.Pill
import com.irontrack.app.ui.components.TextInputDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramsViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    val programs = c.programs.programs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun create(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(c.programs.createProgram(name)) }
    fun activate(id: Long) = viewModelScope.launch { c.programs.setActive(id); ReminderScheduler.reschedule(app) }
    fun deactivate(id: Long) = viewModelScope.launch { c.programs.deactivate(id); ReminderScheduler.reschedule(app) }
    fun rename(p: ProgramFull, name: String) = viewModelScope.launch { c.programs.updateProgram(p.program.copy(name = name)) }
    fun duplicate(p: ProgramFull, name: String) = viewModelScope.launch { c.programs.duplicate(p.program.id, name) }
    fun delete(p: ProgramFull) = viewModelScope.launch { c.programs.deleteProgram(p.program.id); ReminderScheduler.reschedule(app) }
}

@Composable
fun ProgramsScreen(nav: NavHostController) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val vm = appViewModel { ProgramsViewModel(it, app) }
    val programs by vm.programs.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProgramFull?>(null) }
    var duplicating by remember { mutableStateOf<ProgramFull?>(null) }
    var deleting by remember { mutableStateOf<ProgramFull?>(null) }

    AppScaffold(
        title = stringResource(R.string.nav_programs),
        actions = {
            IconButton(onClick = { nav.navigate(Routes.EXERCISES) }) {
                Icon(Icons.Filled.List, contentDescription = stringResource(R.string.exercise_library))
            }
            IconButton(onClick = { nav.navigate(Routes.IMPORT) }) {
                Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.import_program))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_program)) },
            )
        },
    ) { padding ->
        val shareContext = androidx.compose.ui.platform.LocalContext.current
        val shareContainer = com.irontrack.app.ui.appContainer()
        val shareScope = androidx.compose.runtime.rememberCoroutineScope()
        val list = programs
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(onClick = { nav.navigate(Routes.IMPORT) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_program))
                }
            }
            if (list != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.programs_empty_title), stringResource(R.string.programs_empty_message)) }
            }
            items(list.orEmpty(), key = { it.program.id }) { p ->
                ProgramCard(
                    p,
                    onOpen = { nav.navigate(Routes.program(p.program.id)) },
                    onActivate = { vm.activate(p.program.id) },
                    onDeactivate = { vm.deactivate(p.program.id) },
                    onRename = { renaming = p },
                    onDuplicate = { duplicating = p },
                    onShare = { shareScope.launch { shareProgram(shareContext, shareContainer, p.program.id) } },
                    onDelete = { deleting = p },
                )
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.action_new_program),
            initial = "",
            label = stringResource(R.string.program_name),
            onConfirm = { name -> showCreate = false; vm.create(name) { nav.navigate(Routes.program(it)) } },
            onDismiss = { showCreate = false },
        )
    }
    renaming?.let { p ->
        TextInputDialog(stringResource(R.string.action_rename), p.program.name, stringResource(R.string.program_name),
            onConfirm = { vm.rename(p, it); renaming = null }, onDismiss = { renaming = null })
    }
    duplicating?.let { p ->
        TextInputDialog(stringResource(R.string.action_duplicate), stringResource(R.string.copy_of, p.program.name), stringResource(R.string.program_name),
            onConfirm = { vm.duplicate(p, it); duplicating = null }, onDismiss = { duplicating = null })
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_program_title),
            text = stringResource(R.string.delete_program_message, p.program.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.delete(p); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProgramCard(
    p: ProgramFull,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.program.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.program.isActive) Pill(stringResource(R.string.program_active), SuccessGreen)
                    if (p.program.abEnabled) Pill(stringResource(R.string.program_ab), MaterialTheme.colorScheme.secondary)
                }
                val sessions = p.sessions.count { it.session.enabled }
                val exercises = p.sessions.sumOf { it.exercises.size }
                Text(
                    pluralStringResource(R.plurals.sessions_count, sessions, sessions) + " · " +
                        pluralStringResource(R.plurals.exercises_count, exercises, exercises),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.program.isActive) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_deactivate)) }, onClick = { menu = false; onDeactivate() })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_activate)) }, onClick = { menu = false; onActivate() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_program)) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
