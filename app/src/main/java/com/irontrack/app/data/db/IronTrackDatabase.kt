package com.irontrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ExerciseEntity::class,
        ProgramEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        PlannedSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        RescheduleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IronTrackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun backupDao(): BackupDao

    companion object {
        /**
         * Migrations explicites : les données de l'utilisateur ne sont JAMAIS effacées
         * lors d'une mise à jour (pas de fallbackToDestructiveMigration).
         * Ajouter ici chaque migration future (ex. MIGRATION_1_2) et augmenter `version`.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): IronTrackDatabase =
            Room.databaseBuilder(context, IronTrackDatabase::class.java, "irontrack.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
