package com.irontrack.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.irontrack.app.data.db.SessionFull
import com.irontrack.app.data.db.WorkoutEntity
import com.irontrack.app.data.repo.CalendarEntry
import com.irontrack.app.data.repo.EntryStatus
import com.irontrack.app.data.repo.ScheduleService
import com.irontrack.app.data.repo.ScheduleSnapshot
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.EmptyState
import com.irontrack.app.ui.components.StatusPill
import com.irontrack.app.ui.components.WeekPill
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.navigation.navigateTab
import com.irontrack.app.ui.workout.PreviousPerformanceText
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.WeekRotation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val snapshot: ScheduleSnapshot? = null,
    val inProgress: WorkoutEntity? = null,
    val firstDay: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
) {
    val todayEntries: List<CalendarEntry> get() = snapshot?.entriesOn(today).orEmpty()
    val nextEntry: CalendarEntry? get() = snapshot?.entries?.firstOrNull { it.date.isAfter(today) && it.status == EntryStatus.UPCOMING }
    val weekEntries: List<CalendarEntry>
        get() {
            val start = WeekRotation.weekStart(today, firstDay)
            return snapshot?.entries.orEmpty().filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) && it.session != null }
        }
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val today = LocalDate.now()
    val state: StateFlow<HomeState> = combine(
        c.schedule.observe(today.minusDays(7), today.plusDays(21)),
        c.workouts.inProgress,
        c.settings.settings,
    ) { snap, inProgress, s -> HomeState(today, snap, inProgress, s.firstDayOfWeek) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun start(entry: CalendarEntry, onStarted: (Long) -> Unit) {
        val sid = entry.session?.session?.id ?: return
        viewModelScope.launch {
            onStarted(c.workouts.startFromSession(sid, entry.originalDate ?: entry.date, entry.week))
        }
    }

    /**
     * Lance une séance choisie librement. Si cette séance est justement prévue aujourd'hui et pas encore
     * commencée, elle est rattachée à cette date du calendrier ; sinon elle est enregistrée comme séance
     * réalisée hors calendrier, à la date du jour.
     */
    fun startSession(s: SessionFull, onStarted: (Long) -> Unit) {
        val st = state.value
        val todayEntry = st.todayEntries.firstOrNull { it.session?.session?.id == s.session.id && it.workout == null }
        if (todayEntry != null) return start(todayEntry, onStarted)
        viewModelScope.launch { onStarted(c.workouts.startFromSession(s.session.id, null, s.session.week)) }
    }
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Démarrage en attente de confirmation (une autre séance est déjà en cours).
    var confirmStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var picking by remember { mutableStateOf(false) }
    val openWorkout: (Long) -> Unit = { id -> nav.navigate(Routes.workout(id)) }
    fun guarded(action: () -> Unit) { if (state.inProgress != null) confirmStart = action else action() }
    val latest = com.irontrack.app.ui.LocalAppSettings.current.latestKnownVersion
    val updateAvailable = latest?.let { com.irontrack.core.util.SemVer.parse(it) }?.let { it.code > com.irontrack.app.BuildConfig.VERSION_CODE } == true

    AppScaffold(title = stringResource(R.string.app_title)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(FrenchFormat.formatLong(state.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.snapshot?.program?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(p.program.name, style = MaterialTheme.typography.bodyMedium)
                        if (p.program.abEnabled) {
                            WeekPill(ScheduleService.currentWeek(p, state.today, state.firstDay).name)
                        }
                    }
                }
            }
            if (updateAvailable) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.update_available), modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { nav.navigate(Routes.ABOUT) }) { Text(stringResource(R.string.action_view)) }
                        }
                    }
                }
            }
            state.inProgress?.let { w ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.home_in_progress), style = MaterialTheme.typography.labelLarge)
                            Text(w.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { nav.navigate(Routes.workout(w.id)) }) { Text(stringResource(R.string.action_resume_workout)) }
                        }
                    }
                }
            }
            if (state.snapshot?.program == null && state.snapshot != null) {
                item {
                    EmptyState(stringResource(R.string.home_no_program_title), stringResource(R.string.home_no_program_message)) {
                        Button(onClick = { nav.navigateTab(Routes.PROGRAMS) }) { Text(stringResource(R.string.action_create_program)) }
                    }
                }
            }
            val todays = state.todayEntries.filter { it.session != null }
            if (todays.isNotEmpty()) {
                item { Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                todays.forEach { e ->
                    item {
                        TodayCard(e, onStart = { guarded { vm.start(e, openWorkout) } }, onOpen = { e.workout?.let { nav.navigate(if (it.status == "IN_PROGRESS") Routes.workout(it.id) else Routes.workoutDetail(it.id)) } })
                    }
                }
            }
            if (isFreeProgram(state.snapshot?.program)) {
                // Programme sans jours attribués : on choisit sa séance quand on veut.
                val p = state.snapshot!!.program!!
                item {
                    Text(stringResource(R.string.home_free_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_free_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                startableSessions(p).forEach { s ->
                    item(key = "free_${s.session.id}") {
                        StartSessionCard(s, showWeek = p.program.abEnabled) { guarded { vm.startSession(s, openWorkout) } }
                    }
                }
            } else if (todays.isEmpty() && state.snapshot?.program != null) {
                item {
                    Card { Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.home_rest_day), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.home_rest_day_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            }
            state.nextEntry?.let { n ->
                item {
                    Text(stringResource(R.string.home_next_workout), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    FrenchFormat.capitalize(n.date.format(FrenchFormat.shortDate)) + (n.timeOfDayMinutes?.let { " · " + FrenchFormat.time(it) } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                n.session?.session?.estimatedDurationMinutes?.let {
                                    Text(stringResource(R.string.estimated_duration_value, Fmt.minutes(it)), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            WeekPill(n.week.takeIf { state.snapshot?.program?.program?.abEnabled == true })
                        }
                    }
                }
            }
            val program = state.snapshot?.program
            if (program != null && !isFreeProgram(program) && startableSessions(program).isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.action_start_other_session))
                    }
                }
            }
            val week = state.weekEntries
            if (week.isNotEmpty()) {
                item {
                    val done = week.count { it.status == EntryStatus.COMPLETED || it.status == EntryStatus.PARTIAL }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.home_week_progress), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.home_week_progress_value, done, week.size), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { if (week.isEmpty()) 0f else done.toFloat() / week.size }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = { nav.navigateTab(Routes.PROGRAMS) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.home_my_program)) }
                    OutlinedButton(onClick = { nav.navigateTab(Routes.STATS) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.home_history)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (picking) {
        state.snapshot?.program?.let { p ->
            SessionPickerDialog(p, onPick = { s -> picking = false; guarded { vm.startSession(s, openWorkout) } }, onDismiss = { picking = false })
        }
    }
    confirmStart?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.home_already_in_progress_title),
            text = stringResource(R.string.home_already_in_progress_message),
            confirmLabel = stringResource(R.string.action_start_anyway),
            onConfirm = { confirmStart = null; action() },
            onDismiss = { confirmStart = null },
        )
    }
}

@Composable
private fun TodayCard(e: CalendarEntry, onStart: () -> Unit, onOpen: () -> Unit) {
    val s = e.session ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.session.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val sub = buildList {
                        e.timeOfDayMinutes?.let { add(FrenchFormat.time(it)) }
                        s.session.estimatedDurationMinutes?.let { add(Fmt.minutes(it)) }
                    }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.status != EntryStatus.TODAY) StatusPill(e.status)
            }
            if (s.exercises.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                s.sortedExercises.forEach { ex ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        val count = ex.sets.size
                        Text(
                            ex.exercise.name + if (count > 0) " — " + pluralSets(count) else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PreviousPerformanceText(ex.exercise.id, excludeWorkoutId = e.workout?.id ?: -1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (e.status) {
                EntryStatus.COMPLETED, EntryStatus.PARTIAL, EntryStatus.IN_PROGRESS ->
                    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_view_workout)) }
                else -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.action_start_workout), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun pluralSets(n: Int): String = androidx.compose.ui.res.pluralStringResource(R.plurals.sets_count, n, n)
