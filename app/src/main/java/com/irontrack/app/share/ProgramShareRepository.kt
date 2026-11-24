package com.irontrack.app.share

import androidx.room.withTransaction
import com.irontrack.app.data.db.ExerciseEntity
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.db.PlannedSetEntity
import com.irontrack.app.data.db.ProgramEntity
import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.data.db.SessionEntity
import com.irontrack.app.data.db.SessionExerciseEntity
import com.irontrack.core.share.ProgramShareCodec
import com.irontrack.core.share.SharedExercise
import com.irontrack.core.share.SharedProgram
import com.irontrack.core.share.SharedSession
import com.irontrack.core.share.SharedSet
import java.time.LocalDate

/** Conversion d'un programme en lien de partage, et import d'un programme reçu par lien. */
class ProgramShareRepository(private val db: IronTrackDatabase) {

    suspend fun shareLink(programId: Long): Pair<String, String>? {
        val full = db.programDao().programFull(programId) ?: return null
        return full.program.name to ProgramShareCodec.link(toShared(full))
    }

    fun toShared(full: ProgramFull): SharedProgram = SharedProgram(
        name = full.program.name,
        abEnabled = full.program.abEnabled,
        notes = full.program.notes,
        sessions = full.sessions.sortedBy { it.session.position }.map { s ->
            SharedSession(
                name = s.session.name,
                week = s.session.week,
                dayOfWeek = s.session.dayOfWeek,
                timeOfDayMinutes = s.session.timeOfDayMinutes,
                estimatedDurationMinutes = s.session.estimatedDurationMinutes,
                enabled = s.session.enabled,
                notes = s.session.notes,
                exercises = s.sortedExercises.map { e ->
                    val i = e.item
                    val target = SharedSet(i.targetRepsMin, i.targetRepsMax, i.targetRir, i.targetRirMax, i.targetRpe, i.targetWeightKg)
                    SharedExercise(
                        name = e.exercise.name,
                        catalogKey = e.exercise.catalogKey,
                        muscleGroup = e.exercise.muscleGroup,
                        equipment = e.exercise.equipment,
                        notes = i.notes,
                        restSeconds = i.restSeconds,
                        target = target.takeIf { it != SharedSet() },
                        sets = e.sortedSets.map { p -> SharedSet(p.repsMin, p.repsMax, p.rir, p.rirMax, p.rpe, p.weightKg, p.restSeconds) },
                    )
                },
            )
        },
    )

    /**
     * Crée le programme reçu (inactif : l'utilisateur choisit de l'activer). Les exercices sont rattachés
     * à ceux du catalogue (même clé) ou de la bibliothèque (même nom) ; sinon un exercice personnalisé est créé.
     */
    suspend fun import(p: SharedProgram, activate: Boolean): Long = db.withTransaction {
        val programs = db.programDao()
        val exercises = db.exerciseDao()
        val library = exercises.all()
        val resolved = mutableMapOf<String, Long>()
        suspend fun exerciseId(e: SharedExercise): Long {
            val cacheKey = e.catalogKey ?: ("name:" + e.name.lowercase())
            resolved[cacheKey]?.let { return it }
            val id = e.catalogKey?.let { exercises.byCatalogKey(it)?.id }
                ?: library.firstOrNull { !it.archived && it.name.equals(e.name, ignoreCase = true) }?.id
                ?: exercises.insert(ExerciseEntity(name = e.name, muscleGroup = e.muscleGroup, equipment = e.equipment, isCustom = true))
            resolved[cacheKey] = id
            return id
        }

        val programId = programs.insertProgram(
            ProgramEntity(name = p.name, abEnabled = p.abEnabled, isActive = false, anchorEpochDay = LocalDate.now().toEpochDay(), notes = p.notes)
        )
        if (activate) programs.setActive(programId)
        p.sessions.forEachIndexed { pos, s ->
            val sid = programs.insertSession(
                SessionEntity(
                    programId = programId, week = s.week, dayOfWeek = s.dayOfWeek, name = s.name,
                    timeOfDayMinutes = s.timeOfDayMinutes, estimatedDurationMinutes = s.estimatedDurationMinutes,
                    enabled = s.enabled, position = pos, notes = s.notes,
                )
            )
            s.exercises.forEachIndexed { k, e ->
                val t = e.target ?: SharedSet()
                val seId = programs.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sid, exerciseId = exerciseId(e), position = k, notes = e.notes, restSeconds = e.restSeconds,
                        targetRepsMin = t.repsMin, targetRepsMax = t.repsMax, targetRir = t.rir, targetRirMax = t.rirMax,
                        targetRpe = t.rpe, targetWeightKg = t.weightKg,
                    )
                )
                programs.insertPlannedSets(e.sets.mapIndexed { n, x ->
                    PlannedSetEntity(
                        sessionExerciseId = seId, setIndex = n + 1, repsMin = x.repsMin, repsMax = x.repsMax,
                        rir = x.rir, rirMax = x.rirMax, rpe = x.rpe, weightKg = x.weightKg, restSeconds = x.restSeconds,
                    )
                })
            }
        }
        programId
    }
}
