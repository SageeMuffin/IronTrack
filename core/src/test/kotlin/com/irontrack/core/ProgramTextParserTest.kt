package com.irontrack.core

import com.irontrack.core.importer.ProgramAnalyzer
import com.irontrack.core.importer.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTextParserTest {
    private val analyzer = ProgramAnalyzer()

    @Test fun upperLowerExample() {
        val p = analyzer.analyze("""
            UPPER A
            Bench press: 3 x 8-10, RIR 2
            Incline dumbbell press: 3 x 10, RIR 1
            Lateral raises: 4 x 15, RIR 2
            Triceps pushdown: 3 x 12, RIR 1
            LOWER A
            Squat: 3 x 6-8, RIR 2
            Leg curl: 3 x 12, RIR 1
            Calf raises: 4 x 15
        """.trimIndent())
        assertEquals(2, p.days.size)
        assertEquals("UPPER A", p.days[0].title)
        assertEquals(4, p.days[0].exercises.size)
        val bench = p.days[0].exercises[0]
        assertEquals("Bench press", bench.rawName)
        assertEquals("bench_press", bench.match?.best?.id)
        assertEquals(3, bench.sets.size)
        assertEquals(8, bench.sets[0].repsMin); assertEquals(10, bench.sets[0].repsMax); assertEquals(2, bench.sets[0].rirMin)
        val calves = p.days[1].exercises[2]
        assertNull(calves.sets[0].rirMin)
        assertEquals(SplitType.UPPER_LOWER, p.split)
        assertFalse(p.hasAbWeeks)
        assertEquals(7, p.exerciseCount)
        assertEquals(3 + 3 + 4 + 3 + 3 + 3 + 4, p.totalPlannedSets)
        assertNull(p.days[0].dayOfWeek) // aucun jour imposé
    }

    @Test fun perSetDetail() {
        val p = analyzer.analyze("""
            Push
            Bench press:
            Set 1: 10 reps, RIR 2
            Set 2: 9 reps, RIR 2
            Set 3: 8 reps, RIR 1
        """.trimIndent())
        val ex = p.days.single().exercises.single()
        assertEquals(listOf(10, 9, 8), ex.sets.map { it.repsMin })
        assertEquals(listOf(2, 2, 1), ex.sets.map { it.rirMin })
    }

    @Test fun weeklyCalendarAndRest() {
        val p = analyzer.analyze("""
            MONDAY: PUSH
            TUESDAY: PULL
            WEDNESDAY: LEGS
            THURSDAY: REST
            FRIDAY: UPPER
            SATURDAY: LOWER
        """.trimIndent())
        assertEquals(6, p.days.size)
        assertEquals(1, p.days[0].dayOfWeek)
        assertTrue(p.days[3].isRest)
        assertEquals(4, p.days[3].dayOfWeek)
        assertEquals(5, p.trainingDays.size)
    }

    @Test fun scheduleMergedWithDetails() {
        val p = analyzer.analyze("""
            Lundi : Push
            Mercredi : Pull
            Push
            Développé couché 3x8
            Pull
            Tractions 3x8
        """.trimIndent())
        assertEquals(2, p.trainingDays.size)
        assertEquals(1, p.trainingDays.first { it.title == "Push" }.dayOfWeek)
        assertEquals(3, p.trainingDays.first { it.title == "Pull" }.dayOfWeek)
    }

    @Test fun abWeeks() {
        val p = analyzer.analyze("""
            Semaine A
            Lundi - Haut du corps
            Développé couché : 4x6
            Semaine B
            Lundi - Haut du corps
            Développé incliné haltères : 3x10
        """.trimIndent())
        assertTrue(p.hasAbWeeks)
        assertEquals(listOf("A", "B"), p.weekLabels)
        assertEquals("A", p.days[0].weekLabel); assertEquals("B", p.days[1].weekLabel)
        assertEquals("incline_db_press", p.days[1].exercises[0].match?.best?.id)
    }

    @Test fun numberedWeeksAreNotAB() {
        val p = analyzer.analyze("""
            Semaine 1
            Jour 1
            Squat 3x5
            Semaine 2
            Jour 1
            Squat 3x4
        """.trimIndent())
        assertFalse(p.hasAbWeeks)
        assertEquals(listOf("1", "2"), p.weekLabels)
    }

    @Test fun exerciseWithoutParamsHasNoSets() {
        val p = analyzer.analyze("""
            Full body
            Squat
            Tractions
        """.trimIndent())
        val ex = p.days.single().exercises
        assertEquals(2, ex.size)
        assertNull(ex[0].setCount)
        assertTrue(ex[0].sets.isEmpty())
    }

    @Test fun frenchFormatsAndDuration() {
        val p = analyzer.analyze("""
            PROGRAMME HYPERTROPHIE
            Séance 1 - Pecs / Triceps (1h15)
            1. Développé couché : 3 séries de 8 à 10 répétitions, RIR 2, repos 2 min
            2. Écarté poulie : 3x12-15 RPE 8
        """.trimIndent())
        assertEquals("PROGRAMME HYPERTROPHIE", p.title)
        val d = p.days.single()
        assertEquals(75, d.estimatedDurationMinutes)
        val dc = d.exercises[0]
        assertEquals(3, dc.sets.size); assertEquals(120, dc.sets[0].restSeconds)
        assertEquals(8.0, d.exercises[1].sets[0].rpe!!, 0.0)
        assertNull(d.exercises[1].sets[0].rirMin)
    }

    @Test fun table() {
        val p = analyzer.analyze("""
            Jour 1
            | Exercice | Séries | Reps | RIR | Repos |
            | Squat | 4 | 6-8 | 2 | 3 min |
            | Leg press | 3 | 10 | 1 | 90s |
        """.trimIndent())
        val ex = p.days.single().exercises
        assertEquals(2, ex.size)
        assertEquals(4, ex[0].sets.size); assertEquals(6, ex[0].sets[0].repsMin); assertEquals(180, ex[0].sets[0].restSeconds)
        assertEquals("leg_press", ex[1].match?.best?.id)
    }

    @Test fun unreadableLinesKept() {
        val p = analyzer.analyze("""
            Push
            Bench press 3x8
            ~~ 3#4 %% 12
        """.trimIndent())
        assertEquals(1, p.unrecognized.size)
    }

    @Test fun ambiguousNameProposesCandidates() {
        val p = analyzer.analyze("Jour 1\nCurl 3x10")
        val m = p.days.single().exercises.single().match!!
        assertNull(m.best)
        assertTrue(m.candidates.size >= 2)
    }

    @Test fun frenchEquivalents() {
        val p = analyzer.analyze("""
            Jour A
            Élévations latérales 4x15
            Extension triceps poulie 3x12
            Presse à cuisses 3x10
        """.trimIndent())
        assertEquals(listOf("lateral_raise", "triceps_pushdown", "leg_press"), p.days.single().exercises.map { it.match?.best?.id })
    }

    @Test fun pplX2() {
        val p = analyzer.analyze(listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs").joinToString("\n") { "$it\nSquat 3x5" })
        assertEquals(SplitType.PPL_X2, p.split)
    }
}
