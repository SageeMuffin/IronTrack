package com.irontrack.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.irontrack.app.data.repo.CalendarEntry
import com.irontrack.app.data.repo.EntryStatus
import com.irontrack.app.data.repo.ScheduleService
import com.irontrack.app.data.repo.ScheduleSnapshot
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.StatusPill
import com.irontrack.app.ui.components.TimeOfDaySheet
import com.irontrack.app.ui.components.WeekPill
import com.irontrack.app.ui.components.statusColor
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.theme.WeekAColor
import com.irontrack.app.ui.theme.WeekBColor
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.WeekRotation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CalendarMode { MONTH, WEEK }

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    val mode = MutableStateFlow(CalendarMode.MONTH)
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())

    val snapshot = combine(mode, month, selected, c.settings.settings) { m, ym, sel, s -> Triple(m, ym to sel, s.firstDayOfWeek) }
        .flatMapLatest { (m, p, firstDay) ->
            val (ym, sel) = p
            val (from, to) = if (m == CalendarMode.MONTH) {
                val start = WeekRotation.weekStart(ym.atDay(1), firstDay)
                start to start.plusDays(41)
            } else {
                val start = WeekRotation.weekStart(sel, firstDay)
                start to start.plusDays(6)
            }
            c.schedule.observe(from, to)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun start(e: CalendarEntry, onStarted: (Long) -> Unit) {
        val sid = e.session?.session?.id ?: return
        viewModelScope.launch { onStarted(c.workouts.startFromSession(sid, e.originalDate ?: e.date, e.week)) }
    }

    fun reschedule(e: CalendarEntry, date: LocalDate, time: Int?) = viewModelScope.launch {
        val sid = e.session?.session?.id ?: return@launch
        c.programs.reschedule(sid, e.originalDate ?: e.date, date, time)
        ReminderScheduler.reschedule(app)
    }

    fun cancelOccurrence(e: CalendarEntry) = viewModelScope.launch {
        val sid = e.session?.session?.id ?: return@launch
        c.programs.reschedule(sid, e.originalDate ?: e.date, null, null)
        ReminderScheduler.reschedule(app)
    }

    fun restoreOccurrence(e: CalendarEntry) = viewModelScope.launch {
        val sid = e.session?.session?.id ?: return@launch
        c.programs.clearReschedule(sid, e.originalDate ?: e.date)
        ReminderScheduler.reschedule(app)
    }

    fun markNotDone(e: CalendarEntry) = viewModelScope.launch {
        val sid = e.session?.session?.id ?: return@launch
        c.workouts.markNotDone(sid, e.originalDate ?: e.date)
    }
}

@Composable
fun CalendarScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm = appViewModel { CalendarViewModel(it, app) }
    val mode by vm.mode.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val firstDay = LocalAppSettings.current.firstDayOfWeek
    var rescheduling by remember { mutableStateOf<CalendarEntry?>(null) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    AppScaffold(title = stringResource(R.string.nav_calendar)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = mode == CalendarMode.MONTH, onClick = { vm.mode.value = CalendarMode.MONTH },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.calendar_month)) }
                    SegmentedButton(selected = mode == CalendarMode.WEEK, onClick = { vm.mode.value = CalendarMode.WEEK },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.calendar_week)) }
                }
            }
            item {
                if (mode == CalendarMode.MONTH) {
                    MonthHeader(month, onPrev = { vm.month.value = month.minusMonths(1) }, onNext = { vm.month.value = month.plusMonths(1) })
                    MonthGrid(month, firstDay, selected, snapshot, onSelect = { vm.selected.value = it })
                } else {
                    val start = WeekRotation.weekStart(selected, firstDay)
                    WeekHeader(start, onPrev = { vm.selected.value = selected.minusWeeks(1) }, onNext = { vm.selected.value = selected.plusWeeks(1) })
                    WeekList(start, selected, snapshot, onSelect = { vm.selected.value = it })
                }
                Legend()
            }
            item {
                val program = snapshot?.program
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(FrenchFormat.formatLong(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (program?.program?.abEnabled == true) WeekPill(ScheduleService.currentWeek(program, selected, firstDay).name)
                }
            }
            val entries = snapshot?.entriesOn(selected).orEmpty()
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(if (snapshot?.program == null) R.string.calendar_no_program else R.string.rest_day),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entries.forEach { e ->
                item {
                    EntryCard(
                        e,
                        onOpen = {
                            val w = e.workout
                            when {
                                w != null && w.status == "IN_PROGRESS" -> nav.navigate(Routes.workout(w.id))
                                w != null && w.status != "NOT_DONE" -> nav.navigate(Routes.workoutDetail(w.id))
                                e.session != null -> vm.start(e) { nav.navigate(Routes.workout(it)) }
                            }
                        },
                        onReschedule = { rescheduling = e },
                        onCancel = { vm.cancelOccurrence(e) },
                        onRestore = { vm.restoreOccurrence(e) },
                        onNotDone = { vm.markNotDone(e) },
                        onEditSession = { e.session?.let { nav.navigate(Routes.session(it.session.id)) } },
                    )
                }
            }
        }
    }

    rescheduling?.let { e ->
        if (pickedDate == null) {
            val state = rememberDatePickerState(initialSelectedDateMillis = e.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { rescheduling = null },
                confirmButton = {
                    TextButton(onClick = {
                        pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        if (pickedDate == null) rescheduling = null
                    }) { Text(stringResource(R.string.action_next)) }
                },
                dismissButton = { TextButton(onClick = { rescheduling = null }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state = state, title = { Text(stringResource(R.string.reschedule_title), Modifier.padding(16.dp)) }) }
        } else {
            TimeOfDaySheet(
                title = stringResource(R.string.reschedule_time),
                minutes = e.timeOfDayMinutes,
                onDismiss = { rescheduling = null; pickedDate = null },
            ) { t ->
                vm.reschedule(e, pickedDate!!, t)
                rescheduling = null; pickedDate = null
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
        Text(
            FrenchFormat.capitalize(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", FrenchFormat.locale))),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
    }
}

@Composable
private fun WeekHeader(start: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
        Text(
            stringResource(R.string.week_range, Fmt.date(start), Fmt.date(start.plusDays(6))),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, firstDay: DayOfWeek, selected: LocalDate, snapshot: ScheduleSnapshot?, onSelect: (LocalDate) -> Unit) {
    val shortDays = stringArrayResource(R.array.weekdays_short)
    val start = WeekRotation.weekStart(month.atDay(1), firstDay)
    val today = LocalDate.now()
    Column {
        Row {
            (0 until 7).forEach { i ->
                val dow = ((firstDay.value - 1 + i) % 7)
                Text(shortDays[dow], Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        (0 until 6).forEach { week ->
            val weekStart = start.plusWeeks(week.toLong())
            if (week == 5 && weekStart.month != month.month) return@forEach
            Row {
                (0 until 7).forEach { d ->
                    val date = weekStart.plusDays(d.toLong())
                    DayCell(date, date.month == month.month, date == selected, date == today, snapshot?.entriesOn(date).orEmpty(), snapshot, firstDay,
                        Modifier.weight(1f)) { onSelect(date) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate, inMonth: Boolean, isSelected: Boolean, isToday: Boolean, entries: List<CalendarEntry>,
    snapshot: ScheduleSnapshot?, firstDay: DayOfWeek, modifier: Modifier, onClick: () -> Unit,
) {
    val program = snapshot?.program
    val weekColor = if (program?.program?.abEnabled == true && date.dayOfWeek == firstDay) {
        if (ScheduleService.currentWeek(program, date, firstDay).name == "A") WeekAColor else WeekBColor
    } else null
    Box(
        modifier.aspectRatio(1f).padding(2.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(10.dp))
            .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                entries.take(3).forEach { e -> Box(Modifier.size(6.dp).background(statusColor(e.status), CircleShape)) }
            }
        }
        weekColor?.let { Box(Modifier.align(Alignment.TopStart).padding(3.dp).size(5.dp).background(it, CircleShape)) }
    }
}

@Composable
private fun WeekList(start: LocalDate, selected: LocalDate, snapshot: ScheduleSnapshot?, onSelect: (LocalDate) -> Unit) {
    val days = stringArrayResource(R.array.weekdays)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 7).forEach { i ->
            val d = start.plusDays(i.toLong())
            val entries = snapshot?.entriesOn(d).orEmpty()
            Row(
                Modifier.fillMaxWidth()
                    .background(if (d == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                    .clickable { onSelect(d) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(0.35f)) {
                    Text(days[d.dayOfWeek.value - 1], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(Fmt.date(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(0.65f)) {
                    if (entries.isEmpty()) Text(stringResource(R.string.rest_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entries.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(statusColor(e.status), CircleShape))
                            Spacer(Modifier.size(6.dp))
                            Text(e.title + (e.timeOfDayMinutes?.let { " · " + FrenchFormat.time(it) } ?: ""), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(EntryStatus.COMPLETED, EntryStatus.MISSED, EntryStatus.UPCOMING).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(statusColor(s), CircleShape))
                Spacer(Modifier.size(4.dp))
                Text(com.irontrack.app.ui.components.statusLabel(s), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EntryCard(
    e: CalendarEntry,
    onOpen: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onNotDone: () -> Unit,
    onEditSession: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sub = buildList {
                    e.timeOfDayMinutes?.let { add(FrenchFormat.time(it)) }
                    e.session?.session?.estimatedDurationMinutes?.let { add(stringResource(R.string.estimated_duration_value, Fmt.minutes(it))) }
                    if (e.rescheduled && e.originalDate != null) add(stringResource(R.string.rescheduled_from, Fmt.date(e.originalDate)))
                }
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(e.status)
                    WeekPill(e.week.takeIf { e.session != null })
                }
                when (e.status) {
                    EntryStatus.TODAY, EntryStatus.UPCOMING, EntryStatus.MISSED ->
                        Text(stringResource(R.string.tap_to_start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    else -> {}
                }
            }
            if (e.session != null && e.workout == null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_reschedule)) }, onClick = { menu = false; onReschedule() })
                        if (e.rescheduled) DropdownMenuItem(text = { Text(stringResource(R.string.action_restore_schedule)) }, onClick = { menu = false; onRestore() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_cancel_occurrence)) }, onClick = { menu = false; onCancel() })
                        if (e.status == EntryStatus.MISSED || e.status == EntryStatus.TODAY) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_mark_not_done)) }, onClick = { menu = false; onNotDone() })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_edit_session)) }, onClick = { menu = false; onEditSession() })
                    }
                }
            }
        }
    }
}
