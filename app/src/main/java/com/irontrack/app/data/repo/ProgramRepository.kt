package com.irontrack.app.data.repo

import androidx.room.withTransaction
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.db.PlannedSetEntity
import com.irontrack.app.data.db.ProgramEntity
import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.data.db.RescheduleEntity
import com.irontrack.app.data.db.SessionEntity
import com.irontrack.app.data.db.SessionExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ProgramRepository(private val db: IronTrackDatabase) {
    private val dao = db.programDao()

    val programs: Flow<List<ProgramFull>> = dao.observeProgramsFull()
    val activeProgram: Flow<ProgramFull?> = dao.observeActiveFull()

    fun observeProgram(id: Long) = dao.observeProgramFull(id)
    fun observeSession(id: Long) = dao.observeSessionFull(id)
    fun observeReschedules(programId: Long) = dao.observeReschedules(programId)
    suspend fun activeFull() = dao.activeFull()
    suspend fun programFull(id: Long) = dao.programFull(id)
    suspend fun sessionFull(id: Long) = dao.sessionFull(id)
    suspend fun reschedules(programId: Long) = dao.reschedules(programId)

    suspend fun createProgram(name: String, abEnabled: Boolean = false): Long = db.withTransaction {
        val hasActive = dao.activeFull() != null
        dao.insertProgram(
            ProgramEntity(
                name = name.trim(),
                abEnabled = abEnabled,
                isActive = !hasActive,
                anchorEpochDay = LocalDate.now().toEpochDay(),
            )
        )
    }

    suspend fun updateProgram(p: ProgramEntity) = dao.updateProgram(p.copy(updatedAt = System.currentTimeMillis()))
    suspend fun setActive(id: Long) = dao.setActive(id)
    suspend fun deactivate(id: Long) = dao.deactivate(id)

    /** Supprime le programme ; l'historique des séances réalisées est conservé (lien mis à null). */
    suspend fun deleteProgram(id: Long) = dao.deleteProgram(id)

    /** Copie complète, durées estimées et séries individuelles comprises. */
    suspend fun duplicate(id: Long, newName: String): Long = db.withTransaction {
        val src = dao.programFull(id) ?: return@withTransaction -1L
        val newId = dao.insertProgram(
            src.program.copy(id = 0, name = newName.trim(), isActive = false,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        )
        for (s in src.sessions.sortedBy { it.session.position }) {
            val sid = dao.insertSession(s.session.copy(id = 0, programId = newId))
            for (e in s.sortedExercises) {
                val eid = dao.insertSessionExercise(e.item.copy(id = 0, sessionId = sid))
                dao.insertPlannedSets(e.sortedSets.map { it.copy(id = 0, sessionExerciseId = eid) })
            }
        }
        newId
    }

    // ------------------------------------------------------------------ séances
    suspend fun addSession(programId: Long, week: String, dayOfWeek: Int?, name: String): Long {
        val count = dao.programFull(programId)?.sessions?.size ?: 0
        val id = dao.insertSession(SessionEntity(programId = programId, week = week, dayOfWeek = dayOfWeek, name = name.trim(), position = count))
        dao.touch(programId)
        return id
    }

    suspend fun updateSession(s: SessionEntity) {
        dao.updateSession(s)
        dao.touch(s.programId)
    }

    suspend fun deleteSession(s: SessionEntity) {
        dao.deleteSession(s.id)
        dao.touch(s.programId)
    }

    /** Copie une séance (ex. vers l'autre semaine ou un autre jour). */
    suspend fun copySession(sessionId: Long, week: String, dayOfWeek: Int?): Long = db.withTransaction {
        val src = dao.sessionFull(sessionId) ?: return@withTransaction -1L
        val sid = dao.insertSession(src.session.copy(id = 0, week = week, dayOfWeek = dayOfWeek))
        for (e in src.sortedExercises) {
            val eid = dao.insertSessionExercise(e.item.copy(id = 0, sessionId = sid))
            dao.insertPlannedSets(e.sortedSets.map { it.copy(id = 0, sessionExerciseId = eid) })
        }
        dao.touch(src.session.programId)
        sid
    }

    // ------------------------------------------------------------------ exercices de séance
    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long): Long {
        val pos = dao.sessionExercises(sessionId).size
        return dao.insertSessionExercise(SessionExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, position = pos))
    }

    suspend fun updateSessionExercise(e: SessionExerciseEntity) = dao.updateSessionExercise(e)

    suspend fun removeSessionExercise(id: Long) = db.withTransaction {
        val e = dao.sessionExercise(id) ?: return@withTransaction
        dao.deleteSessionExercise(id)
        dao.updateSessionExercises(dao.sessionExercises(e.sessionId).mapIndexed { i, x -> x.copy(position = i) })
    }

    suspend fun reorderSessionExercises(sessionId: Long, orderedIds: List<Long>) = db.withTransaction {
        val current = dao.sessionExercises(sessionId).associateBy { it.id }
        dao.updateSessionExercises(orderedIds.mapIndexedNotNull { i, id -> current[id]?.copy(position = i) })
    }

    // ------------------------------------------------------------------ séries prévues
    /** Ajoute une série vide (aucune valeur inventée) ou une copie explicite d'une série existante. */
    suspend fun addPlannedSet(sessionExerciseId: Long, copyOf: PlannedSetEntity? = null): Long {
        val idx = (dao.plannedSets(sessionExerciseId).maxOfOrNull { it.setIndex } ?: 0) + 1
        val base = copyOf?.copy(id = 0, setIndex = idx) ?: PlannedSetEntity(sessionExerciseId = sessionExerciseId, setIndex = idx)
        return dao.insertPlannedSet(base.copy(sessionExerciseId = sessionExerciseId))
    }

    suspend fun updatePlannedSet(s: PlannedSetEntity) = dao.updatePlannedSet(s)

    suspend fun removePlannedSet(s: PlannedSetEntity) = db.withTransaction {
        dao.deletePlannedSet(s.id)
        dao.updatePlannedSets(dao.plannedSets(s.sessionExerciseId).mapIndexed { i, x -> x.copy(setIndex = i + 1) })
    }

    // ------------------------------------------------------------------ calendrier
    suspend fun reschedule(sessionId: Long, original: LocalDate, newDate: LocalDate?, newTimeMinutes: Int?) {
        dao.deleteReschedule(sessionId, original.toEpochDay())
        dao.upsertReschedule(RescheduleEntity(sessionId = sessionId, originalEpochDay = original.toEpochDay(), newEpochDay = newDate?.toEpochDay(), newTimeMinutes = newTimeMinutes))
    }

    suspend fun clearReschedule(sessionId: Long, original: LocalDate) = dao.deleteReschedule(sessionId, original.toEpochDay())
}
