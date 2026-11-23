package com.irontrack.app.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.R
import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.data.db.SessionFull
import com.irontrack.app.data.repo.ScheduleService
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.SectionTitle
import com.irontrack.app.ui.components.TextInputDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.WeekSlot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProgramEditorViewModel(private val c: AppContainer, private val app: android.app.Application, val programId: Long) : ViewModel() {
    val program = c.programs.observeProgram(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun changed() = viewModelScope.launch { ReminderScheduler.reschedule(app) }

    fun rename(name: String) = update { it.copy(name = name) }
    fun setAb(enabled: Boolean) = update { it.copy(abEnabled = enabled) }
    fun setActive(active: Boolean) = viewModelScope.launch {
        if (active) c.programs.setActive(programId) else c.programs.deactivate(programId)
        changed()
    }

    /** Indique quelle semaine est en cours ; l'ancre de l'alternance est ajustée en conséquence. */
    fun setCurrentWeek(slot: WeekSlot) = update {
        val today = LocalDate.now()
        it.copy(anchorEpochDay = (if (slot == WeekSlot.A) today else today.minusWeeks(1)).toEpochDay())
    }

    private fun update(f: (com.irontrack.app.data.db.ProgramEntity) -> com.irontrack.app.data.db.ProgramEntity) = viewModelScope.launch {
        val p = program.value?.program ?: c.programs.programFull(programId)?.program ?: return@launch
        c.programs.updateProgram(f(p))
        changed()
    }

    fun addSession(week: String, day: Int?, name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(c.programs.addSession(programId, week, day, name)); changed()
    }

    fun toggleSession(s: SessionFull) = viewModelScope.launch {
        c.programs.updateSession(s.session.copy(enabled = !s.session.enabled)); changed()
    }

    fun copySession(s: SessionFull, week: String, day: Int?) = viewModelScope.launch { c.programs.copySession(s.session.id, week, day); changed() }
    fun deleteSession(s: SessionFull) = viewModelScope.launch { c.programs.deleteSession(s.session); changed() }
}

@Composable
fun ProgramEditorScreen(nav: NavHostController, programId: Long) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm = appViewModel(key = "program_$programId") { ProgramEditorViewModel(it, app, programId) }
    val program by vm.program.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var weekTab by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Int?>(null) } // jour (0 = non planifié)
    var deleting by remember { mutableStateOf<SessionFull?>(null) }

    val p = program
    AppScaffold(
        title = p?.program?.name ?: stringResource(R.string.nav_programs),
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename)) }
        },
    ) { padding ->
        if (p == null) return@AppScaffold
        val week = if (p.program.abEnabled && weekTab == 1) "B" else "A"
        val days = stringArrayResource(R.array.weekdays)
        val order = (0 until 7).map { ((settings.firstDayOfWeek.value - 1 + it) % 7) + 1 }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item { ProgramHeader(p, vm, settings.firstDayOfWeek) }
            if (p.program.abEnabled) {
                item {
                    TabRow(selectedTabIndex = weekTab, modifier = Modifier.padding(top = 12.dp)) {
                        Tab(selected = weekTab == 0, onClick = { weekTab = 0 }, text = { Text(stringResource(R.string.week_a)) })
                        Tab(selected = weekTab == 1, onClick = { weekTab = 1 }, text = { Text(stringResource(R.string.week_b)) })
                    }
                }
            }
            val weekSessions = p.sessions.filter { it.session.week == week || (!p.program.abEnabled && it.session.week == "A") }
            order.forEach { day ->
                val list = weekSessions.filter { it.session.dayOfWeek == day }.sortedBy { it.session.timeOfDayMinutes ?: Int.MAX_VALUE }
                item(key = "day_${week}_$day") {
                    SectionTitle(days[day - 1], trailing = {
                        TextButton(onClick = { adding = day }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.action_add_session))
                        }
                    })
                    if (list.isEmpty()) {
                        Text(stringResource(R.string.rest_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    list.forEach { s ->
                        SessionCard(s, p.program.abEnabled,
                            onOpen = { nav.navigate(Routes.session(s.session.id)) },
                            onToggle = { vm.toggleSession(s) },
                            onCopyOtherWeek = { vm.copySession(s, if (s.session.week == "A") "B" else "A", s.session.dayOfWeek) },
                            onDuplicate = { vm.copySession(s, s.session.week, s.session.dayOfWeek) },
                            onDelete = { deleting = s })
                    }
                }
            }
            val unscheduled = weekSessions.filter { it.session.dayOfWeek == null }
            item(key = "unscheduled_$week") {
                SectionTitle(stringResource(R.string.unscheduled_sessions), trailing = {
                    TextButton(onClick = { adding = 0 }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.action_add_session))
                    }
                })
                if (unscheduled.isEmpty()) {
                    Text(stringResource(R.string.unscheduled_sessions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                unscheduled.forEach { s ->
                    SessionCard(s, p.program.abEnabled,
                        onOpen = { nav.navigate(Routes.session(s.session.id)) },
                        onToggle = { vm.toggleSession(s) },
                        onCopyOtherWeek = { vm.copySession(s, if (s.session.week == "A") "B" else "A", null) },
                        onDuplicate = { vm.copySession(s, s.session.week, null) },
                        onDelete = { deleting = s })
                }
            }
        }

        adding?.let { day ->
            TextInputDialog(
                title = stringResource(R.string.action_add_session),
                initial = "",
                label = stringResource(R.string.session_name),
                onConfirm = { name -> adding = null; vm.addSession(week, day.takeIf { it > 0 }, name) { nav.navigate(Routes.session(it)) } },
                onDismiss = { adding = null },
            )
        }
    }

    if (renaming && p != null) {
        TextInputDialog(stringResource(R.string.action_rename), p.program.name, stringResource(R.string.program_name),
            onConfirm = { vm.rename(it); renaming = false }, onDismiss = { renaming = false })
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.delete_session_title),
            text = stringResource(R.string.delete_session_message, s.session.name),
            confirmLabel = stringResource(R.string.action_delete), destructive = true,
            onConfirm = { vm.deleteSession(s); deleting = null }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProgramHeader(p: ProgramFull, vm: ProgramEditorViewModel, firstDay: java.time.DayOfWeek) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.program_active_label), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.program_active_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = p.program.isActive, onCheckedChange = { vm.setActive(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.program_ab_label), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.program_ab_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = p.program.abEnabled, onCheckedChange = { vm.setAb(it) })
            }
            if (p.program.abEnabled) {
                val current = ScheduleService.currentWeek(p, LocalDate.now(), firstDay)
                Text(stringResource(R.string.program_current_week), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = current == WeekSlot.A, onClick = { vm.setCurrentWeek(WeekSlot.A) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.week_a)) }
                    SegmentedButton(selected = current == WeekSlot.B, onClick = { vm.setCurrentWeek(WeekSlot.B) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.week_b)) }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    s: SessionFull,
    ab: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onCopyOtherWeek: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val alpha = if (s.session.enabled) 1f else 0.5f
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                val parts = buildList {
                    s.session.timeOfDayMinutes?.let { add(FrenchFormat.time(it)) }
                    s.session.estimatedDurationMinutes?.let { add(Fmt.minutes(it)) }
                    add(pluralStringResource(R.plurals.exercises_count, s.exercises.size, s.exercises.size))
                    if (!s.session.enabled) add(stringResource(R.string.session_disabled))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (s.session.enabled) R.string.action_disable_session else R.string.action_enable_session)) },
                        onClick = { menu = false; onToggle() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
                    if (ab) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (s.session.week == "A") R.string.action_copy_to_week_b else R.string.action_copy_to_week_a)) },
                            onClick = { menu = false; onCopyOtherWeek() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
