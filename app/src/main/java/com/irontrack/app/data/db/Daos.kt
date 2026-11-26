package com.irontrack.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name COLLATE LOCALIZED")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE LOCALIZED")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE catalogKey = :key")
    suspend fun byCatalogKey(key: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ExerciseEntity>): List<Long>

    @Insert
    suspend fun insert(item: ExerciseEntity): Long

    @Update
    suspend fun update(item: ExerciseEntity)

    @Delete
    suspend fun delete(item: ExerciseEntity)

    @Query("SELECT (SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :id) + (SELECT COUNT(*) FROM workout_exercises WHERE exerciseId = :id)")
    suspend fun usageCount(id: Long): Int
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY isActive DESC, updatedAt DESC")
    fun observePrograms(): Flow<List<ProgramEntity>>

    @Transaction
    @Query("SELECT * FROM programs ORDER BY isActive DESC, updatedAt DESC")
    fun observeProgramsFull(): Flow<List<ProgramFull>>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    fun observeProgramFull(id: Long): Flow<ProgramFull?>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun programFull(id: Long): ProgramFull?

    @Transaction
    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    fun observeActiveFull(): Flow<ProgramFull?>

    @Transaction
    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    suspend fun activeFull(): ProgramFull?

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun program(id: Long): ProgramEntity?

    @Insert
    suspend fun insertProgram(p: ProgramEntity): Long

    @Update
    suspend fun updateProgram(p: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("UPDATE programs SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("UPDATE programs SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE programs SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    // --- Séances types
    @Transaction
    @Query("SELECT * FROM program_sessions WHERE id = :id")
    fun observeSessionFull(id: Long): Flow<SessionFull?>

    @Transaction
    @Query("SELECT * FROM program_sessions WHERE id = :id")
    suspend fun sessionFull(id: Long): SessionFull?

    @Query("SELECT * FROM program_sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Update
    suspend fun updateSession(s: SessionEntity)

    @Query("DELETE FROM program_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // --- Exercices d'une séance
    @Insert
    suspend fun insertSessionExercise(e: SessionExerciseEntity): Long

    @Update
    suspend fun updateSessionExercise(e: SessionExerciseEntity)

    @Update
    suspend fun updateSessionExercises(e: List<SessionExerciseEntity>)

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun sessionExercise(id: Long): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    suspend fun sessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    // --- Séries prévues
    @Insert
    suspend fun insertPlannedSet(s: PlannedSetEntity): Long

    @Insert
    suspend fun insertPlannedSets(s: List<PlannedSetEntity>)

    @Update
    suspend fun updatePlannedSet(s: PlannedSetEntity)

    @Update
    suspend fun updatePlannedSets(s: List<PlannedSetEntity>)

    @Query("SELECT * FROM planned_sets WHERE sessionExerciseId = :id ORDER BY setIndex")
    suspend fun plannedSets(id: Long): List<PlannedSetEntity>

    @Query("DELETE FROM planned_sets WHERE id = :id")
    suspend fun deletePlannedSet(id: Long)

    // --- Reports ponctuels
    @Query("SELECT * FROM reschedules WHERE sessionId IN (SELECT id FROM program_sessions WHERE programId = :programId)")
    fun observeReschedules(programId: Long): Flow<List<RescheduleEntity>>

    @Query("SELECT * FROM reschedules WHERE sessionId IN (SELECT id FROM program_sessions WHERE programId = :programId)")
    suspend fun reschedules(programId: Long): List<RescheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReschedule(r: RescheduleEntity): Long

    @Query("DELETE FROM reschedules WHERE sessionId = :sessionId AND originalEpochDay = :originalEpochDay")
    suspend fun deleteReschedule(sessionId: Long, originalEpochDay: Long)
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(w: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkoutFull(id: Long): Flow<WorkoutFull?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workoutFull(id: Long): WorkoutFull?

    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE scheduledEpochDay BETWEEN :from AND :to")
    fun observeScheduledBetween(from: Long, to: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE startedAt BETWEEN :fromMillis AND :toMillis ORDER BY startedAt")
    fun observeStartedBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutEntity>>

    @Query(
        """
        SELECT w.id, w.name, w.week, w.startedAt, w.endedAt, w.estimatedDurationMinutes, w.status,
          (SELECT COUNT(*) FROM workout_exercises we WHERE we.workoutId = w.id) AS exerciseCount,
          (SELECT COUNT(*) FROM workout_sets ws JOIN workout_exercises we2 ON ws.workoutExerciseId = we2.id
             WHERE we2.workoutId = w.id AND ws.completed = 1) AS completedSets
        FROM workouts w WHERE w.status != 'IN_PROGRESS' ORDER BY w.startedAt DESC
        """
    )
    fun observeHistory(): Flow<List<WorkoutSummary>>

    @Insert
    suspend fun insertWorkoutExercise(e: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(e: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun workoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun workoutExercise(id: Long): WorkoutExerciseEntity?

    @Insert
    suspend fun insertSet(s: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(s: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(s: WorkoutSetEntity)

    @Update
    suspend fun updateSets(s: List<WorkoutSetEntity>)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun set(id: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :id ORDER BY setIndex")
    suspend fun sets(id: Long): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    /** Dernière séance terminée (hors séance courante) contenant cet exercice. */
    @Query(
        """
        SELECT we.id FROM workout_exercises we JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.id != :excludeWorkoutId AND w.status IN ('COMPLETED','PARTIAL')
          AND w.startedAt < :before
          AND EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.workoutExerciseId = we.id AND ws.completed = 1)
        ORDER BY w.startedAt DESC LIMIT 1
        """
    )
    suspend fun lastWorkoutExerciseId(exerciseId: Long, excludeWorkoutId: Long, before: Long): Long?

    @Query("SELECT startedAt FROM workouts WHERE id = (SELECT workoutId FROM workout_exercises WHERE id = :workoutExerciseId)")
    suspend fun startedAtOfWorkoutExercise(workoutExerciseId: Long): Long?

    @Query(
        """
        SELECT w.id AS workoutId, w.name AS workoutName, w.startedAt AS startedAt, ws.setIndex AS setIndex,
               ws.actualReps AS actualReps, ws.actualRir AS actualRir, ws.actualWeightKg AS actualWeightKg, ws.completed AS completed
        FROM workout_sets ws
        JOIN workout_exercises we ON ws.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.status IN ('COMPLETED','PARTIAL') AND ws.completed = 1
        ORDER BY w.startedAt, ws.setIndex
        """
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSetHistoryRow>>

    @Query(
        """
        SELECT we.exerciseId AS exerciseId, e.name AS exerciseName, w.id AS workoutId, w.startedAt AS startedAt,
               ws.actualReps AS actualReps, ws.actualWeightKg AS actualWeightKg
        FROM workout_sets ws
        JOIN workout_exercises we ON ws.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        JOIN exercises e ON we.exerciseId = e.id
        WHERE w.status IN ('COMPLETED','PARTIAL') AND ws.completed = 1
        """
    )
    fun observeAllCompletedSets(): Flow<List<CompletedSetRow>>

    @Query(
        """
        SELECT DISTINCT e.* FROM exercises e JOIN workout_exercises we ON we.exerciseId = e.id
        JOIN workout_sets ws ON ws.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE ws.completed = 1 AND w.status IN ('COMPLETED','PARTIAL')
        ORDER BY e.name COLLATE LOCALIZED
        """
    )
    fun observeTrainedExercises(): Flow<List<ExerciseEntity>>
}

/** Accès brut pour la sauvegarde / restauration. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises") suspend fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM programs") suspend fun programs(): List<ProgramEntity>
    @Query("SELECT * FROM program_sessions") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM session_exercises") suspend fun sessionExercises(): List<SessionExerciseEntity>
    @Query("SELECT * FROM planned_sets") suspend fun plannedSets(): List<PlannedSetEntity>
    @Query("SELECT * FROM workouts") suspend fun workouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises") suspend fun workoutExercises(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM workout_sets") suspend fun workoutSets(): List<WorkoutSetEntity>
    @Query("SELECT * FROM reschedules") suspend fun reschedules(): List<RescheduleEntity>
    @Query("SELECT * FROM body_measurements") suspend fun bodyMeasurements(): List<BodyMeasurementEntity>

    @Insert suspend fun insertExercises(l: List<ExerciseEntity>)
    @Insert suspend fun insertPrograms(l: List<ProgramEntity>)
    @Insert suspend fun insertSessions(l: List<SessionEntity>)
    @Insert suspend fun insertSessionExercises(l: List<SessionExerciseEntity>)
    @Insert suspend fun insertPlannedSets(l: List<PlannedSetEntity>)
    @Insert suspend fun insertWorkouts(l: List<WorkoutEntity>)
    @Insert suspend fun insertWorkoutExercises(l: List<WorkoutExerciseEntity>)
    @Insert suspend fun insertWorkoutSets(l: List<WorkoutSetEntity>)
    @Insert suspend fun insertReschedules(l: List<RescheduleEntity>)
    @Insert suspend fun insertBodyMeasurements(l: List<BodyMeasurementEntity>)

    @Query("DELETE FROM workout_sets") suspend fun clearWorkoutSets()
    @Query("DELETE FROM workout_exercises") suspend fun clearWorkoutExercises()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM reschedules") suspend fun clearReschedules()
    @Query("DELETE FROM planned_sets") suspend fun clearPlannedSets()
    @Query("DELETE FROM session_exercises") suspend fun clearSessionExercises()
    @Query("DELETE FROM program_sessions") suspend fun clearSessions()
    @Query("DELETE FROM programs") suspend fun clearPrograms()
    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM body_measurements") suspend fun clearBodyMeasurements()
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_measurements ORDER BY epochDay")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE metric = :metric ORDER BY epochDay")
    fun observeMetric(metric: String): Flow<List<BodyMeasurementEntity>>

    /** Remplace la valeur existante du même jour (index unique metric + epochDay). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: BodyMeasurementEntity): Long

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: Long)
}
