package com.irontrack.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.irontrack.app.data.db.ExerciseEntity
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.db.PlannedSetEntity
import com.irontrack.app.data.db.ProgramEntity
import com.irontrack.app.data.db.RescheduleEntity
import com.irontrack.app.data.db.SessionEntity
import com.irontrack.app.data.db.SessionExerciseEntity
import com.irontrack.app.data.db.WorkoutEntity
import com.irontrack.app.data.db.WorkoutExerciseEntity
import com.irontrack.app.data.db.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: String = "irontrack-backup",
    val formatVersion: Int = 1,
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val exercises: List<ExerciseEntity>,
    val programs: List<ProgramEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val plannedSets: List<PlannedSetEntity>,
    val workouts: List<WorkoutEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val workoutSets: List<WorkoutSetEntity>,
    val reschedules: List<RescheduleEntity>,
)

class BackupManager(private val context: Context, private val db: IronTrackDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, appVersion: String): Int = withContext(Dispatchers.IO) {
        val d = db.backupDao()
        val file = db.withTransaction {
            BackupFile(
                schemaVersion = db.openHelper.readableDatabase.version,
                exportedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                exercises = d.exercises(), programs = d.programs(), sessions = d.sessions(),
                sessionExercises = d.sessionExercises(), plannedSets = d.plannedSets(), workouts = d.workouts(),
                workoutExercises = d.workoutExercises(), workoutSets = d.workoutSets(), reschedules = d.reschedules(),
            )
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
            ?: error("Impossible d'écrire le fichier")
        file.workouts.size
    }

    /** Remplace toutes les données par celles de la sauvegarde (dans une seule transaction). */
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Impossible de lire le fichier")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == "irontrack-backup") { "Ce fichier n'est pas une sauvegarde IronTrack" }
        val d = db.backupDao()
        db.withTransaction {
            d.clearWorkoutSets(); d.clearWorkoutExercises(); d.clearWorkouts(); d.clearReschedules()
            d.clearPlannedSets(); d.clearSessionExercises(); d.clearSessions(); d.clearPrograms(); d.clearExercises()
            d.insertExercises(file.exercises); d.insertPrograms(file.programs); d.insertSessions(file.sessions)
            d.insertSessionExercises(file.sessionExercises); d.insertPlannedSets(file.plannedSets)
            d.insertWorkouts(file.workouts); d.insertWorkoutExercises(file.workoutExercises)
            d.insertWorkoutSets(file.workoutSets); d.insertReschedules(file.reschedules)
        }
        file.workouts.size
    }
}
