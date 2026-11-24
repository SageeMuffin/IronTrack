package com.irontrack.core

import com.irontrack.core.util.PerformanceDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceDeltaTest {
    @Test fun volumeWhenWeightAndReps() {
        assertEquals(10.0, PerformanceDelta.percent(100.0, 10, 110.0, 10)!!, 1e-9)
        assertEquals(-10.0, PerformanceDelta.percent(100.0, 10, 100.0, 9)!!, 1e-9)
    }
    @Test fun repsOnlyForBodyweight() {
        assertEquals(20.0, PerformanceDelta.percent(null, 10, null, 12)!!, 1e-9)
    }
    @Test fun weightOnlyWhenRepsMissing() {
        assertEquals(5.0, PerformanceDelta.percent(80.0, null, 84.0, null)!!, 1e-9)
    }
    @Test fun notComparable() {
        assertNull(PerformanceDelta.percent(null, null, 80.0, 10))
        assertNull(PerformanceDelta.percent(80.0, 10, null, 10))
        assertNull(PerformanceDelta.percent(null, 0, null, 5))
    }
    @Test fun formatting() {
        assertEquals("+10 %", PerformanceDelta.format(10.0))
        assertEquals("−2,5 %", PerformanceDelta.format(-2.5))
        assertEquals("=", PerformanceDelta.format(0.01))
    }
}
