package com.irontrack.core

import com.irontrack.core.schedule.Reschedule
import com.irontrack.core.schedule.ScheduleCalculator
import com.irontrack.core.schedule.SessionSlot
import com.irontrack.core.schedule.WorkoutStatus
import com.irontrack.core.schedule.WorkoutStatusCalculator
import com.irontrack.core.schedule.WorkoutStatusCalculator.SetState
import com.irontrack.core.util.Durations
import com.irontrack.core.util.FrenchFormat
import com.irontrack.core.util.SemVer
import com.irontrack.core.util.WeekRotation
import com.irontrack.core.util.WeekSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleAndUtilsTest {
    private val monday = LocalDate.of(2026, 11, 23) // lundi

    @Test fun abRotation() {
        assertEquals(WeekSlot.A, WeekRotation.slotFor(monday, monday, DayOfWeek.MONDAY, true))
        assertEquals(WeekSlot.B, WeekRotation.slotFor(monday.plusDays(7), monday, DayOfWeek.MONDAY, true))
        assertEquals(WeekSlot.A, WeekRotation.slotFor(monday.plusDays(14), monday, DayOfWeek.MONDAY, true))
        assertEquals(WeekSlot.B, WeekRotation.slotFor(monday.minusDays(1), monday, DayOfWeek.MONDAY, true))
        assertEquals(WeekSlot.A, WeekRotation.slotFor(monday.plusDays(7), monday, DayOfWeek.MONDAY, false))
    }

    @Test fun occurrencesAlternate() {
        val slots = listOf(
            SessionSlot(1, WeekSlot.A, 1, 18 * 60, true),
            SessionSlot(2, WeekSlot.B, 1, 18 * 60, true),
            SessionSlot(3, WeekSlot.A, null, null, true), // non planifiée
        )
        val occ = ScheduleCalculator.occurrences(slots, emptyList(), monday, true, DayOfWeek.MONDAY, monday, monday.plusDays(13))
        assertEquals(listOf(1L, 2L), occ.map { it.sessionId })
    }

    @Test fun rescheduleMovesWithoutChangingProgram() {
        val slots = listOf(SessionSlot(1, WeekSlot.A, 1, 18 * 60, true))
        val r = listOf(Reschedule(1, monday, monday.plusDays(1), 19 * 60))
        val occ = ScheduleCalculator.occurrences(slots, r, monday, false, DayOfWeek.MONDAY, monday, monday.plusDays(7))
        assertEquals(monday.plusDays(1), occ[0].date)
        assertEquals(19 * 60, occ[0].timeOfDayMinutes)
        assertEquals(monday.plusDays(7), occ[1].date) // semaine suivante inchangée
    }

    @Test fun cancelledOccurrence() {
        val slots = listOf(SessionSlot(1, WeekSlot.A, 1, null, true))
        val occ = ScheduleCalculator.occurrences(slots, listOf(Reschedule(1, monday, null, null)), monday, false, DayOfWeek.MONDAY, monday, monday)
        assertTrue(occ.isEmpty())
    }

    @Test fun statusNoPlannedSetsIsNotIncomplete() {
        assertEquals(WorkoutStatus.COMPLETED, WorkoutStatusCalculator.finalStatus(listOf(SetState(false, true))))
        assertEquals(WorkoutStatus.PARTIAL, WorkoutStatusCalculator.finalStatus(listOf(SetState(true, true), SetState(true, false))))
        assertEquals(WorkoutStatus.NOT_DONE, WorkoutStatusCalculator.finalStatus(emptyList()))
    }

    @Test fun durations() {
        assertEquals("1h15", Durations.formatMinutes(75))
        assertEquals("45 min", Durations.formatMinutes(45))
        assertEquals("+7 min", Durations.difference(75, 82))
        assertEquals("-10 min", Durations.difference(60, 50))
        assertNull(Durations.difference(null, 50))
        assertEquals("1 min 30", Durations.formatRest(90))
    }

    @Test fun semver() {
        assertEquals(10000, SemVer.parse("v1.0.0")!!.code)
        assertTrue(SemVer.parse("v1.0.10")!! > SemVer.parse("v1.0.9")!!)
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
        assertNull(SemVer.parse("v1.0.0-beta"))
        assertEquals(SemVer(1, 2, 3), SemVer.fromCode(10203))
    }

    @Test fun frenchNumbers() {
        assertEquals("62,5", FrenchFormat.number(62.5))
        assertEquals("60", FrenchFormat.number(60.0))
        assertEquals("08:05", FrenchFormat.time(8 * 60 + 5))
    }
}
