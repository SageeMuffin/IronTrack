package com.irontrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        BodyMeasurementEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class IronTrackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun backupDao(): BackupDao
    abstract fun bodyDao(): BodyDao

    companion object {
        /**
         * Migrations explicites : les données de l'utilisateur ne sont JAMAIS effacées
         * lors d'une mise à jour (pas de fallbackToDestructiveMigration).
         * Ajouter ici chaque migration future (ex. MIGRATION_1_2) et augmenter `version`.
         */
        /** 1 → 2 : ajout des mesures corporelles (aucune donnée existante n'est modifiée). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`metric` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `value` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_body_measurements_metric_epochDay` ON `body_measurements` (`metric`, `epochDay`)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): IronTrackDatabase =
            Room.databaseBuilder(context, IronTrackDatabase::class.java, "irontrack.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
