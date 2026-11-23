package com.irontrack.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class WeekSlot { A, B }

/** Calcule la semaine A ou B d'une date, à partir de la semaine de référence (semaine A) du programme. */
object WeekRotation {
    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun slotFor(date: LocalDate, anchorWeekA: LocalDate, firstDayOfWeek: DayOfWeek, abEnabled: Boolean): WeekSlot {
        if (!abEnabled) return WeekSlot.A
        val weeks = ChronoUnit.WEEKS.between(weekStart(anchorWeekA, firstDayOfWeek), weekStart(date, firstDayOfWeek))
        return if (Math.floorMod(weeks, 2L) == 0L) WeekSlot.A else WeekSlot.B
    }
}
