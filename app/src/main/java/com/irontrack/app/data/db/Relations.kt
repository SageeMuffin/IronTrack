package com.irontrack.app.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class SessionExerciseFull(
    @Embedded val item: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<PlannedSetEntity>,
) {
    val sortedSets: List<PlannedSetEntity> get() = sets.sortedBy { it.setIndex }
}

data class SessionFull(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseFull>,
) {
    val sortedExercises: List<SessionExerciseFull> get() = exercises.sortedBy { it.item.position }
}

data class ProgramFull(
    @Embedded val program: ProgramEntity,
    @Relation(entity = SessionEntity::class, parentColumn = "id", entityColumn = "programId")
    val sessions: List<SessionFull>,
)

data class WorkoutExerciseFull(
    @Embedded val item: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
) {
    val sortedSets: List<WorkoutSetEntity> get() = sets.sortedBy { it.setIndex }
}

data class WorkoutFull(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseFull>,
) {
    val sortedExercises: List<WorkoutExerciseFull> get() = exercises.sortedBy { it.item.position }
}

/** Ligne d'historique d'un exercice (une série réalisée). */
data class ExerciseSetHistoryRow(
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val setIndex: Int,
    val actualReps: Int?,
    val actualRir: Int?,
    val actualWeightKg: Double?,
    val completed: Boolean,
)

/** Résumé d'une séance pour l'historique. */
data class WorkoutSummary(
    val id: Long,
    val name: String,
    val week: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val estimatedDurationMinutes: Int?,
    val status: String,
    val exerciseCount: Int,
    val completedSets: Int,
)

/** Série réalisée, tous exercices confondus (calcul des records). */
data class CompletedSetRow(
    val exerciseId: Long,
    val exerciseName: String,
    val workoutId: Long,
    val startedAt: Long,
    val actualReps: Int?,
    val actualWeightKg: Double?,
)
