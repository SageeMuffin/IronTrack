package com.irontrack.core

import com.irontrack.core.importer.ParamParser
import com.irontrack.core.importer.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamParserTest {
    @Test fun setsTimesReps() {
        val r = ParamParser.parse("3 x 10")
        assertEquals(3, r.setCount); assertEquals(10, r.common.repsMin); assertEquals(10, r.common.repsMax)
        assertNull(r.common.rirMin); assertNull(r.common.weight); assertNull(r.common.restSeconds)
    }
    @Test fun unicodeTimes() { assertEquals(3, ParamParser.parse("3×10").setCount) }
    @Test fun range() {
        val r = ParamParser.parse("3 x 8-12")
        assertEquals(8, r.common.repsMin); assertEquals(12, r.common.repsMax)
    }
    @Test fun frenchWords() {
        val r = ParamParser.parse("3 séries de 10 répétitions")
        assertEquals(3, r.setCount); assertEquals(10, r.common.repsMin)
    }
    @Test fun englishWords() {
        val r = ParamParser.parse("3 sets of 10 reps")
        assertEquals(3, r.setCount); assertEquals(10, r.common.repsMin)
    }
    @Test fun repsOnlyNoSets() {
        val r = ParamParser.parse("8-10 reps")
        assertNull(r.setCount); assertEquals(8, r.common.repsMin); assertEquals(10, r.common.repsMax)
    }
    @Test fun rirAt() {
        val r = ParamParser.parse("3x10 @ RIR 2")
        assertEquals(3, r.setCount); assertEquals(2, r.common.rirMin); assertEquals(2, r.common.rirMax)
    }
    @Test fun rpeKeptAsRpe() {
        val r = ParamParser.parse("3 séries à RPE 8")
        assertEquals(3, r.setCount); assertEquals(8.0, r.common.rpe!!, 0.0); assertNull(r.common.rirMin)
        assertNull(r.common.repsMin)
    }
    @Test fun weight() {
        val r = ParamParser.parse("4 x 12 @ 60 kg")
        assertEquals(4, r.setCount); assertEquals(12, r.common.repsMin); assertEquals(60.0, r.common.weight!!, 0.0)
        assertEquals(WeightUnit.KG, r.common.weightUnit)
    }
    @Test fun decimalCommaWeight() { assertEquals(62.5, ParamParser.parse("3x5 62,5kg").common.weight!!, 0.0) }
    @Test fun restWords() {
        val r = ParamParser.parse("3 sets, 90 seconds rest")
        assertEquals(3, r.setCount); assertEquals(90, r.common.restSeconds); assertNull(r.common.repsMin)
    }
    @Test fun rirAfterNumber() {
        val r = ParamParser.parse("3x10-12, 2 RIR")
        assertEquals(10, r.common.repsMin); assertEquals(12, r.common.repsMax); assertEquals(2, r.common.rirMin)
    }
    @Test fun perSetList() {
        val r = ParamParser.parse("3x12/10/8")
        assertEquals(3, r.setCount); assertEquals(listOf(12, 10, 8), r.perSet!!.map { it.repsMin })
    }
    @Test fun restFormats() {
        assertEquals(90, ParamParser.parse("repos 1min30").common.restSeconds)
        assertEquals(120, ParamParser.parse("repos : 2 min").common.restSeconds)
        assertEquals(150, ParamParser.parse("2'30 de repos").common.restSeconds)
        assertEquals(90, ParamParser.parse("3x10, 90s").common.restSeconds)
    }
    @Test fun ambiguousRestNotInvented() {
        val r = ParamParser.parse("3x10 repos 2")
        assertNull(r.common.restSeconds); assertTrue(r.issues.isNotEmpty())
    }
    @Test fun rirRange() {
        val r = ParamParser.parse("3x8 RIR 1-2")
        assertEquals(1, r.common.rirMin); assertEquals(2, r.common.rirMax)
    }
    @Test fun rangeWithA() {
        val r = ParamParser.parse("3 séries de 8 à 12 reps")
        assertEquals(8, r.common.repsMin); assertEquals(12, r.common.repsMax)
    }
    @Test fun rirZeroIsExplicit() {
        val r = ParamParser.parse("2x10 RIR 0")
        assertEquals(0, r.common.rirMin)
    }
    @Test fun pounds() { assertEquals(WeightUnit.LB, ParamParser.parse("3x5 225 lbs").common.weightUnit) }
}
