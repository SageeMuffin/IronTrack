package com.irontrack.app.data.repo

import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.data.db.RescheduleEntity
import com.irontrack.app.data.db.SessionFull
import com.irontrack.app.data.db.WorkoutEntity
import com.irontrack.core.schedule.Occurrence
import com.irontrack.core.schedule.Reschedule
import com.irontrack.core.schedule.ScheduleCalculator
import com.irontrack.core.schedule.SessionSlot
import com.irontrack.core.util.WeekRotation
import com.irontrack.core.util.WeekSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class EntryStatus { COMPLETED, PARTIAL, NOT_DONE, IN_PROGRESS, MISSED, TODAY, UPCOMING }

/** Une séance dans le calendrier : prévue par le programme, ou réalisée hors programme. */
data class CalendarEntry(
    val date: LocalDate,
    val originalDate: LocalDate?,
    val timeOfDayMinutes: Int?,
    val session: SessionFull?,
    val week: String?,
    val rescheduled: Boolean,
    val status: EntryStatus,
    val workout: WorkoutEntity?,
) {
    val title: String get() = workout?.name ?: session?.session?.name ?: ""
}

data class ScheduleSnapshot(
    val program: ProgramFull?,
    val entries: List<CalendarEntry>,
) {
    fun entriesOn(d: LocalDate) = entries.filter { it.date == d }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleService(
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsRepository,
) {
    fun observe(from: LocalDate, to: LocalDate): Flow<ScheduleSnapshot> =
        programs.activeProgram.flatMapLatest { program ->
            val reschedulesFlow: Flow<List<RescheduleEntity>> =
                if (program == null) flowOf(emptyList()) else programs.observeReschedules(program.program.id)
            val zone = ZoneId.systemDefault()
            combine(
                reschedulesFlow,
                settings.settings,
                workouts.observeScheduledBetween(from.minusDays(40), to.plusDays(40)),
                workouts.observeStartedBetween(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
            ) { reschedules, s, scheduledWorkouts, startedWorkouts ->
                build(program, reschedules, s.firstDayOfWeek, scheduledWorkouts, startedWorkouts, from, to)
            }
        }

    companion object {
        fun currentWeek(program: ProgramFull?, date: LocalDate, firstDay: java.time.DayOfWeek): WeekSlot =
            if (program == null) WeekSlot.A
            else WeekRotation.slotFor(date, LocalDate.ofEpochDay(program.program.anchorEpochDay), firstDay, program.program.abEnabled)

        fun build(
            program: ProgramFull?,
            reschedules: List<RescheduleEntity>,
            firstDay: java.time.DayOfWeek,
            scheduledWorkouts: List<WorkoutEntity>,
            startedWorkouts: List<WorkoutEntity>,
            from: LocalDate,
            to: LocalDate,
            today: LocalDate = LocalDate.now(),
        ): ScheduleSnapshot {
            val zone = ZoneId.systemDefault()
            val entries = mutableListOf<CalendarEntry>()
            val usedWorkoutIds = mutableSetOf<Long>()
            if (program != null) {
                val sessions = program.sessions.associateBy { it.session.id }
                val occ: List<Occurrence> = ScheduleCalculator.occurrences(
                    slots = program.sessions.map {
                        SessionSlot(it.session.id, if (it.session.week == "B") WeekSlot.B else WeekSlot.A,
                            it.session.dayOfWeek, it.session.timeOfDayMinutes, it.session.enabled)
                    },
                    reschedules = reschedules.map {
                        Reschedule(it.sessionId, LocalDate.ofEpochDay(it.originalEpochDay), it.newEpochDay?.let(LocalDate::ofEpochDay), it.newTimeMinutes)
                    },
                    anchorWeekA = LocalDate.ofEpochDay(program.program.anchorEpochDay),
                    abEnabled = program.program.abEnabled,
                    firstDayOfWeek = firstDay,
                    from = from, to = to,
                )
                for (o in occ) {
                    val w = scheduledWorkouts.filter { it.sessionId == o.sessionId && it.scheduledEpochDay == o.originalDate.toEpochDay() }
                        .maxByOrNull { it.startedAt }
                    w?.let { usedWorkoutIds += it.id }
                    val status = when {
                        w != null -> workoutStatus(w)
                        o.date.isBefore(today) -> EntryStatus.MISSED
                        o.date == today -> EntryStatus.TODAY
                        else -> EntryStatus.UPCOMING
                    }
                    entries += CalendarEntry(o.date, o.originalDate, o.timeOfDayMinutes, sessions[o.sessionId],
                        o.week.name, o.rescheduled, status, w)
                }
            }
            // Séances réalisées hors calendrier (séance libre, séance lancée un autre jour…).
            for (w in startedWorkouts) {
                if (w.id in usedWorkoutIds || w.status == "NOT_DONE") continue
                if (w.scheduledEpochDay != null && scheduledWorkouts.any { it.id == w.id } &&
                    entries.any { it.workout?.id == w.id }) continue
                val d = Instant.ofEpochMilli(w.startedAt).atZone(zone).toLocalDate()
                if (d.isBefore(from) || d.isAfter(to)) continue
                entries += CalendarEntry(d, null, null, program?.sessions?.firstOrNull { it.session.id == w.sessionId },
                    w.week, false, workoutStatus(w), w)
            }
            return ScheduleSnapshot(program, entries.sortedWith(compareBy({ it.date }, { it.timeOfDayMinutes ?: Int.MAX_VALUE })))
        }

        private fun workoutStatus(w: WorkoutEntity) = when (w.status) {
            "COMPLETED" -> EntryStatus.COMPLETED
            "PARTIAL" -> EntryStatus.PARTIAL
            "NOT_DONE" -> EntryStatus.NOT_DONE
            else -> EntryStatus.IN_PROGRESS
        }
    }
}
