package com.irontrack.core.schedule

import com.irontrack.core.util.WeekRotation
import com.irontrack.core.util.WeekSlot
import java.time.DayOfWeek
import java.time.LocalDate

/** Séance type d'un programme, réduite à ce qu'il faut pour la planification. */
data class SessionSlot(
    val sessionId: Long,
    val week: WeekSlot,
    /** 1 = lundi … 7 = dimanche ; null = séance non planifiée (jamais placée dans le calendrier). */
    val dayOfWeek: Int?,
    val timeOfDayMinutes: Int?,
    val enabled: Boolean,
)

/** Déplacement ponctuel d'une séance (ne modifie pas le programme). newDate = null : séance annulée. */
data class Reschedule(
    val sessionId: Long,
    val originalDate: LocalDate,
    val newDate: LocalDate?,
    val newTimeMinutes: Int?,
)

data class Occurrence(
    val sessionId: Long,
    /** Date prévue par le programme (clé stable de l'occurrence). */
    val originalDate: LocalDate,
    /** Date effective (après éventuel déplacement). */
    val date: LocalDate,
    val timeOfDayMinutes: Int?,
    val week: WeekSlot,
    val rescheduled: Boolean,
)

object ScheduleCalculator {

    /**
     * Génère les occurrences dont la date effective est dans [from, to] (inclus).
     */
    fun occurrences(
        slots: List<SessionSlot>,
        reschedules: List<Reschedule>,
        anchorWeekA: LocalDate,
        abEnabled: Boolean,
        firstDayOfWeek: DayOfWeek,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        if (to.isBefore(from)) return emptyList()
        val bySession = reschedules.associateBy { it.sessionId to it.originalDate }
        val out = mutableListOf<Occurrence>()
        val active = slots.filter { it.enabled && it.dayOfWeek != null && (abEnabled || it.week == WeekSlot.A) }
        // On élargit la fenêtre pour attraper les séances déplacées vers l'intervalle.
        val maxShift = reschedules.maxOfOrNull { r ->
            r.newDate?.let { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(r.originalDate, it)) } ?: 0L
        } ?: 0L
        var d = from.minusDays(maxShift)
        val end = to.plusDays(maxShift)
        while (!d.isAfter(end)) {
            val slot = WeekRotation.slotFor(d, anchorWeekA, firstDayOfWeek, abEnabled)
            for (s in active) {
                if (s.week != slot || s.dayOfWeek != d.dayOfWeek.value) continue
                val r = bySession[s.sessionId to d]
                val eff = if (r != null) r.newDate else d
                if (eff == null) continue // annulée
                if (eff.isBefore(from) || eff.isAfter(to)) continue
                out += Occurrence(
                    sessionId = s.sessionId,
                    originalDate = d,
                    date = eff,
                    timeOfDayMinutes = r?.newTimeMinutes ?: s.timeOfDayMinutes,
                    week = slot,
                    rescheduled = r != null,
                )
            }
            d = d.plusDays(1)
        }
        return out.sortedWith(compareBy({ it.date }, { it.timeOfDayMinutes ?: Int.MAX_VALUE }))
    }
}

enum class WorkoutStatus { IN_PROGRESS, COMPLETED, PARTIAL, NOT_DONE }

/** Calcule le statut d'une séance terminée sans pénaliser les exercices sans séries prévues. */
object WorkoutStatusCalculator {
    data class SetState(val planned: Boolean, val completed: Boolean)

    fun finalStatus(sets: List<SetState>): WorkoutStatus {
        val planned = sets.filter { it.planned }
        val anyDone = sets.any { it.completed }
        return when {
            planned.isNotEmpty() && planned.all { it.completed } -> WorkoutStatus.COMPLETED
            planned.isEmpty() && anyDone -> WorkoutStatus.COMPLETED
            anyDone -> WorkoutStatus.PARTIAL
            else -> WorkoutStatus.NOT_DONE
        }
    }
}
