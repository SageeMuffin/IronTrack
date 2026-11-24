package com.irontrack.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramShareCodecTest {
    private val program = SharedProgram(
        name = "Push Pull Legs « été »",
        abEnabled = true,
        sessions = listOf(
            SharedSession(
                name = "Push", week = "A", dayOfWeek = 1, estimatedDurationMinutes = 75,
                exercises = listOf(
                    SharedExercise(
                        name = "Développé couché", catalogKey = "bench_press", restSeconds = 150,
                        sets = listOf(SharedSet(repsMin = 8, repsMax = 10, rir = 2, weightKg = 80.0), SharedSet(repsMin = 8)),
                    ),
                    // Exercice sans aucune série prévue : doit rester sans série.
                    SharedExercise(name = "Pompes"),
                ),
            ),
            SharedSession(name = "Pull", week = "B", dayOfWeek = null),
        ),
    )

    @Test fun roundTripKeepsEverythingAndNulls() {
        val link = ProgramShareCodec.link(program)
        assertTrue(link.startsWith(ProgramShareCodec.DEFAULT_BASE_URL + "#1."))
        val back = ProgramShareCodec.decode(link)
        assertEquals(program, back)
        val pompes = back.sessions[0].exercises[1]
        assertTrue(pompes.sets.isEmpty())
        assertNull(back.sessions[0].exercises[0].sets[1].rir)
        assertNull(back.sessions[1].dayOfWeek)
    }

    @Test fun extractsFromSharedMessageAndCustomScheme() {
        val payload = ProgramShareCodec.encode(program)
        val message = "Salut ! Voici mon programme IronTrack : https://sageemuffin.github.io/IronTrack/p/#$payload à bientôt"
        assertEquals(program, ProgramShareCodec.decode(message))
        assertEquals(program, ProgramShareCodec.decode("irontrack://programme?d=$payload"))
        assertNull(ProgramShareCodec.extractPayload("Développé couché 3x10"))
    }

    @Test(expected = ShareFormatException::class)
    fun rejectsDamagedLink() {
        val payload = ProgramShareCodec.encode(program)
        ProgramShareCodec.decode("https://sageemuffin.github.io/IronTrack/p/#" + payload.dropLast(12))
    }

    @Test fun sanitizesOutOfRangeValues() {
        val bad = program.copy(abEnabled = false, sessions = listOf(
            SharedSession(name = "X", week = "B", dayOfWeek = 9, exercises = listOf(SharedExercise(name = "Y", sets = listOf(SharedSet(rir = 42, repsMin = 10)))))
        ))
        val back = ProgramShareCodec.decode(ProgramShareCodec.encode(bad))
        assertEquals("A", back.sessions[0].week)
        assertNull(back.sessions[0].dayOfWeek)
        assertNull(back.sessions[0].exercises[0].sets[0].rir)
        assertEquals(10, back.sessions[0].exercises[0].sets[0].repsMin)
    }

    @Test fun linkStaysShortForTypicalProgram() {
        val big = SharedProgram("Gros programme", sessions = (1..6).map { d ->
            SharedSession("Séance $d", dayOfWeek = d, exercises = (1..7).map { i ->
                SharedExercise("Exercice $i", catalogKey = "key_$i", restSeconds = 120, sets = List(4) { SharedSet(8, 12, 2) })
            })
        })
        assertTrue(ProgramShareCodec.link(big).length < 4000)
    }
}
