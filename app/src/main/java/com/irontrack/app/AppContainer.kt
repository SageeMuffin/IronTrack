package com.irontrack.app

import android.content.Context
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.repo.BackupManager
import com.irontrack.app.data.repo.ExerciseRepository
import com.irontrack.app.data.repo.ProgramRepository
import com.irontrack.app.data.repo.ScheduleService
import com.irontrack.app.data.repo.SettingsRepository
import com.irontrack.app.data.repo.WorkoutRepository
import com.irontrack.app.importer.DocumentTextExtractor
import com.irontrack.app.importer.ImportRepository
import com.irontrack.app.share.ProgramShareRepository
import com.irontrack.app.update.UpdateManager

/** Injection de dépendances manuelle : une seule instance de chaque dépôt pour l'application. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: IronTrackDatabase = IronTrackDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val exercises = ExerciseRepository(database.exerciseDao())
    val programs = ProgramRepository(database)
    val workouts = WorkoutRepository(database)
    val schedule = ScheduleService(programs, workouts, settings)
    val backup = BackupManager(appContext, database)
    val documentExtractor = DocumentTextExtractor(appContext)
    val imports = ImportRepository(database, exercises)
    val updates = UpdateManager(appContext, settings)
    val sharing = ProgramShareRepository(database)
}
