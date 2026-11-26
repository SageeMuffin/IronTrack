package com.irontrack.core.body

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class BodyStatsTest {
    @Test
    fun emptyHasNoSummary() {
        assertNull(BodyStats.summary(emptyList()))
    }

    @Test
    fun singleValueHasNoChange() {
        val s = BodyStats.summary(listOf(MeasurePoint(10, 80.0)))!!
        assertEquals(80.0, s.latest.value, 1e-9)
        assertNull(s.changeSincePrevious)
        assertNull(s.changeSinceFirst)
        assertEquals(1, s.count)
    }

    @Test
    fun changesAreComputedInDateOrder() {
        val s = BodyStats.summary(listOf(MeasurePoint(20, 78.4), MeasurePoint(10, 80.0), MeasurePoint(15, 79.1)))!!
        assertEquals(78.4, s.latest.value, 1e-9)
        assertEquals(-0.7, s.changeSincePrevious!!, 1e-9)
        assertEquals(-1.6, s.changeSinceFirst!!, 1e-9)
        assertEquals(3, s.count)
    }

    @Test
    fun lastEntryOfTheSameDayWins() {
        val n = BodyStats.normalize(listOf(MeasurePoint(5, 80.0), MeasurePoint(5, 79.5), MeasurePoint(3, 81.0)))
        assertEquals(listOf(MeasurePoint(3, 81.0), MeasurePoint(5, 79.5)), n)
    }

    @Test
    fun keysAreStableAndUnique() {
        assertEquals(BodyMetric.entries.size, BodyMetric.entries.map { it.key }.toSet().size)
        assertEquals(BodyMetric.WAIST, BodyMetric.fromKey("waist"))
        assertNull(BodyMetric.fromKey("inconnu"))
    }

    @Test
    fun clampKeepsValuesInRange() {
        assertEquals(30.0, BodyStats.clamp(BodyMetric.WEIGHT, 12.0), 1e-9)
        assertEquals(82.3, BodyStats.clamp(BodyMetric.WAIST, 82.34), 1e-9)
    }
}
