package com.irontrack.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Règle d'intégrité : tout paramètre d'entraînement est nullable.
 * null = « non renseigné ». Zéro n'est jamais utilisé pour représenter une valeur absente
 * (un RIR absent n'est pas un RIR 0, des répétitions absentes ne sont pas 0 répétition).
 */

@Serializable
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["catalogKey"], unique = true), Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val muscleGroup: String? = null,
    val equipment: String? = null,
    val notes: String? = null,
    /** Identifiant stable de la bibliothèque intégrée ; null pour un exercice personnalisé. */
    val catalogKey: String? = null,
    val isCustom: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val abEnabled: Boolean = false,
    val isActive: Boolean = false,
    /** Jour (epochDay) appartenant à une semaine A : référence de l'alternance. */
    val anchorEpochDay: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "program_sessions",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    /** "A" ou "B". */
    val week: String = "A",
    /** 1 = lundi … 7 = dimanche ; null = séance non planifiée. */
    val dayOfWeek: Int? = null,
    val name: String,
    /** Heure d'entraînement en minutes depuis minuit ; null = non renseignée. */
    val timeOfDayMinutes: Int? = null,
    /** Durée estimée facultative ; jamais remplie automatiquement. */
    val estimatedDurationMinutes: Int? = null,
    val enabled: Boolean = true,
    val position: Int = 0,
    val notes: String? = null,
)

@Serializable
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String? = null,
    /** Repos par défaut de l'exercice (facultatif). */
    val restSeconds: Int? = null,
    // Cibles appliquées aux séries ajoutées pendant la séance quand aucune série n'est prévue.
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRir: Int? = null,
    val targetRirMax: Int? = null,
    val targetRpe: Double? = null,
    val targetWeightKg: Double? = null,
)

@Serializable
@Entity(
    tableName = "planned_sets",
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionExerciseId")],
)
data class PlannedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val rir: Int? = null,
    val rirMax: Int? = null,
    /** RPE d'origine (import) ; conservé séparément du RIR. */
    val rpe: Double? = null,
    val weightKg: Double? = null,
    val restSeconds: Int? = null,
)

@Serializable
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("programId"), Index("sessionId"), Index("scheduledEpochDay"), Index("startedAt")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long? = null,
    val sessionId: Long? = null,
    val name: String,
    val week: String? = null,
    /** Date prévue par le programme (epochDay), pour relier la séance au calendrier. */
    val scheduledEpochDay: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Copie de la durée estimée au moment de la séance (facultative). */
    val estimatedDurationMinutes: Int? = null,
    /** IN_PROGRESS, COMPLETED, PARTIAL, NOT_DONE */
    val status: String = "IN_PROGRESS",
    val notes: String? = null,
)

@Serializable
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String? = null,
    val restSeconds: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRir: Int? = null,
    val targetRirMax: Int? = null,
    val targetRpe: Double? = null,
    val targetWeightKg: Double? = null,
)

@Serializable
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(WorkoutExerciseEntity::class, ["id"], ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    /** true si la série provient du programme ; false si ajoutée pendant la séance. */
    val planned: Boolean,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRir: Int? = null,
    val targetRirMax: Int? = null,
    val targetRpe: Double? = null,
    val targetWeightKg: Double? = null,
    val targetRestSeconds: Int? = null,
    val actualReps: Int? = null,
    val actualRir: Int? = null,
    val actualWeightKg: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "reschedules",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId", "originalEpochDay"], unique = true)],
)
data class RescheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val originalEpochDay: Long,
    /** null = séance annulée pour ce jour. */
    val newEpochDay: Long?,
    val newTimeMinutes: Int? = null,
)

/**
 * Mesure corporelle (poids, tour de taille…). [metric] = clé stable de [com.irontrack.core.body.BodyMetric].
 * Le poids est toujours stocké en kg, les tours en cm, la masse grasse en %.
 * Une seule valeur par mesure et par jour : une nouvelle saisie le même jour remplace la précédente.
 */
@Serializable
@Entity(
    tableName = "body_measurements",
    indices = [Index(value = ["metric", "epochDay"], unique = true)],
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val epochDay: Long,
    val value: Double,
    val createdAt: Long = System.currentTimeMillis(),
)
