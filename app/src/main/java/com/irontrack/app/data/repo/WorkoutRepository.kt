package com.irontrack.app.data.repo

import androidx.room.withTransaction
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.db.WorkoutEntity
import com.irontrack.app.data.db.WorkoutExerciseEntity
import com.irontrack.app.data.db.WorkoutSetEntity
import com.irontrack.core.schedule.WorkoutStatusCalculator
import java.time.LocalDate

class WorkoutRepository(private val db: IronTrackDatabase) {
    private val dao = db.workoutDao()
    private val programDao = db.programDao()

    val inProgress = dao.observeInProgress()
    val history = dao.observeHistory()
    val trainedExercises = dao.observeTrainedExercises()
    val allCompletedSets = dao.observeAllCompletedSets()

    fun observeWorkout(id: Long) = dao.observeWorkoutFull(id)
    suspend fun workoutFull(id: Long) = dao.workoutFull(id)
    fun observeExerciseHistory(exerciseId: Long) = dao.observeExerciseHistory(exerciseId)
    fun observeScheduledBetween(from: LocalDate, to: LocalDate) = dao.observeScheduledBetween(from.toEpochDay(), to.toEpochDay())
    fun observeStartedBetween(fromMillis: Long, toMillis: Long) = dao.observeStartedBetween(fromMillis, toMillis)

    /**
     * Démarre une séance à partir d'une séance type. Les cibles sont copiées telles quelles
     * (valeurs absentes = absentes) ; les valeurs réalisées restent vides.
     */
    suspend fun startFromSession(sessionId: Long, scheduledDate: LocalDate?, week: String?): Long = db.withTransaction {
        val s = programDao.sessionFull(sessionId) ?: error("Séance introuvable")
        val wid = dao.insertWorkout(
            WorkoutEntity(
                programId = s.session.programId,
                sessionId = s.session.id,
                name = s.session.name,
                week = week ?: s.session.week,
                scheduledEpochDay = scheduledDate?.toEpochDay(),
                startedAt = System.currentTimeMillis(),
                estimatedDurationMinutes = s.session.estimatedDurationMinutes,
            )
        )
        s.sortedExercises.forEachIndexed { i, e ->
            val weId = dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = wid, exerciseId = e.exercise.id, position = i, notes = e.item.notes,
                    restSeconds = e.item.restSeconds,
                    targetRepsMin = e.item.targetRepsMin, targetRepsMax = e.item.targetRepsMax,
                    targetRir = e.item.targetRir, targetRirMax = e.item.targetRirMax,
                    targetRpe = e.item.targetRpe, targetWeightKg = e.item.targetWeightKg,
                )
            )
            dao.insertSets(e.sortedSets.mapIndexed { k, p ->
                WorkoutSetEntity(
                    workoutExerciseId = weId, setIndex = k + 1, planned = true,
                    targetRepsMin = p.repsMin, targetRepsMax = p.repsMax, targetRir = p.rir, targetRirMax = p.rirMax,
                    targetRpe = p.rpe, targetWeightKg = p.weightKg, targetRestSeconds = p.restSeconds ?: e.item.restSeconds,
                )
            })
        }
        wid
    }

    /** Séance libre, sans programme. */
    suspend fun startEmpty(name: String): Long = dao.insertWorkout(
        WorkoutEntity(name = name, startedAt = System.currentTimeMillis(), scheduledEpochDay = LocalDate.now().toEpochDay())
    )

    suspend fun addExercise(workoutId: Long, exerciseId: Long): Long {
        val pos = dao.workoutExercises(workoutId).size
        return dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = pos))
    }

    suspend fun removeExercise(id: Long) = dao.deleteWorkoutExercise(id)

    /** Ajoute une série pendant la séance ; elle reprend les cibles par défaut de l'exercice si elles existent. */
    suspend fun addSet(workoutExerciseId: Long): Long {
        val we = dao.workoutExercise(workoutExerciseId) ?: return -1
        val idx = (dao.sets(workoutExerciseId).maxOfOrNull { it.setIndex } ?: 0) + 1
        return dao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId, setIndex = idx, planned = false,
                targetRepsMin = we.targetRepsMin, targetRepsMax = we.targetRepsMax,
                targetRir = we.targetRir, targetRirMax = we.targetRirMax, targetRpe = we.targetRpe,
                targetWeightKg = we.targetWeightKg, targetRestSeconds = we.restSeconds,
            )
        )
    }

    suspend fun removeSet(set: WorkoutSetEntity) = db.withTransaction {
        dao.deleteSet(set.id)
        dao.updateSets(dao.sets(set.workoutExerciseId).mapIndexed { i, s -> s.copy(setIndex = i + 1) })
    }

    /** Sauvegarde immédiate (chaque modification est écrite en base : pas de perte en cas d'interruption). */
    suspend fun updateSet(set: WorkoutSetEntity) = dao.updateSet(set)

    suspend fun updateWorkout(w: WorkoutEntity) = dao.updateWorkout(w)

    suspend fun finish(workoutId: Long): String = db.withTransaction {
        val full = dao.workoutFull(workoutId) ?: error("Séance introuvable")
        val states = full.exercises.flatMap { e -> e.sets.map { WorkoutStatusCalculator.SetState(it.planned, it.completed) } }
        val status = WorkoutStatusCalculator.finalStatus(states).name
        // Une séance rouverte pour correction garde sa durée réelle d'origine.
        dao.updateWorkout(full.workout.copy(endedAt = full.workout.endedAt ?: System.currentTimeMillis(), status = status))
        status
    }

    /** Marque une séance prévue comme non effectuée (depuis le calendrier). */
    suspend fun markNotDone(sessionId: Long, date: LocalDate) = db.withTransaction {
        val s = programDao.session(sessionId) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.insertWorkout(
            WorkoutEntity(
                programId = s.programId, sessionId = s.id, name = s.name, week = s.week,
                scheduledEpochDay = date.toEpochDay(), startedAt = now, endedAt = now,
                estimatedDurationMinutes = s.estimatedDurationMinutes, status = "NOT_DONE",
            )
        )
    }

    suspend fun delete(workoutId: Long) = dao.deleteWorkout(workoutId)

    /** Rouvre une séance terminée pour la corriger ; le statut sera recalculé à la fin. */
    suspend fun reopen(workoutId: Long) {
        val w = dao.workout(workoutId) ?: return
        dao.updateWorkout(w.copy(status = "IN_PROGRESS"))
    }

    /** Séries réalisées lors de la dernière séance contenant cet exercice. */
    suspend fun previousPerformance(exerciseId: Long, excludeWorkoutId: Long, before: Long = Long.MAX_VALUE): PreviousPerformance? {
        val weId = dao.lastWorkoutExerciseId(exerciseId, excludeWorkoutId, before) ?: return null
        val sets = dao.sets(weId).filter { it.completed }
        val at = dao.startedAtOfWorkoutExercise(weId) ?: return null
        return PreviousPerformance(at, sets)
    }
}

data class PreviousPerformance(val startedAt: Long, val sets: List<WorkoutSetEntity>)
