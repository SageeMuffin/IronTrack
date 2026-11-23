package com.irontrack.app.importer

import androidx.room.withTransaction
import com.irontrack.app.data.db.IronTrackDatabase
import com.irontrack.app.data.db.PlannedSetEntity
import com.irontrack.app.data.db.ProgramEntity
import com.irontrack.app.data.db.SessionEntity
import com.irontrack.app.data.db.SessionExerciseEntity
import com.irontrack.app.data.repo.ExerciseRepository
import com.irontrack.core.importer.ImportIssue
import com.irontrack.core.importer.ParsedProgram
import com.irontrack.core.importer.SetTarget
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/** Choix de l'exercice IronTrack correspondant à un nom importé. */
sealed interface ExerciseChoice {
    data class Existing(val exerciseId: Long, val name: String) : ExerciseChoice
    /** Créer un exercice personnalisé avec le nom d'origine (aucune traduction imposée). */
    data class CreateNew(val name: String) : ExerciseChoice
}

data class ImportCandidate(val exerciseId: Long, val name: String, val score: Double)

data class EditableExercise(
    val key: Long,
    val rawName: String,
    val sourceLine: String,
    val choice: ExerciseChoice,
    val candidates: List<ImportCandidate>,
    /** true si le rapprochement est fiable ; false = l'utilisateur doit confirmer. */
    val matchConfident: Boolean,
    val sets: List<SetTarget>,
    val defaultTarget: SetTarget,
    val notes: String?,
    val issues: List<ImportIssue>,
    val lowOcrConfidence: Boolean,
    val include: Boolean = true,
) {
    val uncertain: Boolean get() = !matchConfident || issues.isNotEmpty() || lowOcrConfidence
}

data class EditableDay(
    val key: Long,
    val title: String,
    val sourceWeek: String?,
    /** Semaine IronTrack de destination : "A" ou "B". */
    val week: String,
    /** Jour attribué (1-7) ; null = à attribuer par l'utilisateur ou séance non planifiée. */
    val dayOfWeek: Int?,
    /** true si le jour vient explicitement du document. */
    val dayFromSource: Boolean,
    val estimatedDurationMinutes: Int?,
    val notes: String?,
    val exercises: List<EditableExercise>,
    val include: Boolean = true,
)

data class ImportDraft(
    val programName: String,
    val parsed: ParsedProgram,
    val days: List<EditableDay>,
    val restDays: List<Int>,
    val abEnabled: Boolean,
    val activate: Boolean,
) {
    val includedDays get() = days.filter { it.include }
    val exerciseCount get() = includedDays.sumOf { d -> d.exercises.count { it.include } }
    val totalSets get() = includedDays.sumOf { d -> d.exercises.filter { it.include }.sumOf { it.sets.size } }
    val uncertainCount get() = includedDays.sumOf { d -> d.exercises.count { it.include && it.uncertain } }
}

private val keys = AtomicLong(1)
fun nextKey() = keys.getAndIncrement()

class ImportRepository(private val db: IronTrackDatabase, private val exercises: ExerciseRepository) {

    /**
     * Construit le brouillon modifiable à partir de l'analyse. Pour un programme en plusieurs semaines
     * numérotées, seule la semaine [weekFilter] est retenue ; [mapTwoWeeksToAB] associe explicitement
     * semaine 1 -> A et semaine 2 -> B (choix de l'utilisateur, jamais automatique).
     */
    suspend fun buildDraft(parsed: ParsedProgram, weekFilter: String?, mapTwoWeeksToAB: Boolean, defaultName: String): ImportDraft {
        val matcher = exercises.matcher()
        val numbered = !parsed.hasAbWeeks && parsed.weekLabels.size > 1
        val sourceDays = parsed.days.filter { d ->
            when {
                parsed.hasAbWeeks -> true
                numbered && mapTwoWeeksToAB -> d.weekLabel == null || d.weekLabel in parsed.weekLabels.take(2)
                numbered && weekFilter != null -> d.weekLabel == weekFilter || d.weekLabel == null
                else -> true
            }
        }
        val ab = parsed.hasAbWeeks || (numbered && mapTwoWeeksToAB)
        val days = sourceDays.filter { !it.isRest }.map { d ->
            val week = when {
                parsed.hasAbWeeks -> if (d.weekLabel == "B") "B" else "A"
                numbered && mapTwoWeeksToAB -> if (d.weekLabel == parsed.weekLabels.getOrNull(1)) "B" else "A"
                else -> "A"
            }
            EditableDay(
                key = nextKey(),
                title = d.title,
                sourceWeek = d.weekLabel,
                week = week,
                dayOfWeek = d.dayOfWeek,
                dayFromSource = d.dayOfWeek != null,
                estimatedDurationMinutes = d.estimatedDurationMinutes,
                notes = d.notes,
                exercises = d.exercises.map { ex ->
                    val m = matcher.match(ex.rawName)
                    val best = m.best
                    EditableExercise(
                        key = nextKey(),
                        rawName = ex.rawName,
                        sourceLine = ex.sourceLine,
                        choice = if (best != null) ExerciseChoice.Existing(best.id.toLong(), best.displayName) else ExerciseChoice.CreateNew(ex.rawName),
                        candidates = m.candidates.map { ImportCandidate(it.id.toLong(), it.displayName, it.score) },
                        matchConfident = best != null,
                        sets = ex.sets,
                        defaultTarget = ex.defaultTarget,
                        notes = ex.notes,
                        issues = ex.issues,
                        lowOcrConfidence = ex.lowOcrConfidence,
                    )
                },
            )
        }
        return ImportDraft(
            programName = parsed.title?.takeIf { it.isNotBlank() } ?: defaultName,
            parsed = parsed,
            days = days,
            restDays = sourceDays.filter { it.isRest }.mapNotNull { it.dayOfWeek },
            abEnabled = ab,
            activate = false,
        )
    }

    /** Enregistre le programme importé. Seules les valeurs présentes dans la source sont écrites. */
    suspend fun save(draft: ImportDraft): Long = db.withTransaction {
        val programs = db.programDao()
        val exDao = db.exerciseDao()
        val programId = programs.insertProgram(
            ProgramEntity(
                name = draft.programName.trim().ifBlank { "Programme importé" },
                abEnabled = draft.abEnabled,
                isActive = false,
                anchorEpochDay = LocalDate.now().toEpochDay(),
                notes = draft.parsed.unrecognized.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text },
            )
        )
        if (draft.activate) programs.setActive(programId)
        val created = mutableMapOf<String, Long>()
        draft.includedDays.forEachIndexed { i, d ->
            val sid = programs.insertSession(
                SessionEntity(
                    programId = programId,
                    week = d.week,
                    dayOfWeek = d.dayOfWeek,
                    name = d.title,
                    estimatedDurationMinutes = d.estimatedDurationMinutes,
                    position = i,
                    notes = d.notes,
                )
            )
            d.exercises.filter { it.include }.forEachIndexed { pos, ex ->
                val exerciseId = when (val c = ex.choice) {
                    is ExerciseChoice.Existing -> c.exerciseId
                    is ExerciseChoice.CreateNew -> created.getOrPut(c.name.trim().lowercase()) {
                        exDao.insert(com.irontrack.app.data.db.ExerciseEntity(name = c.name.trim(), isCustom = true))
                    }
                }
                val restCommon = (ex.sets.mapNotNull { it.restSeconds }.distinct().singleOrNull()) ?: ex.defaultTarget.restSeconds
                val seId = programs.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sid,
                        exerciseId = exerciseId,
                        position = pos,
                        notes = ex.notes,
                        restSeconds = restCommon,
                        targetRepsMin = ex.defaultTarget.repsMin,
                        targetRepsMax = ex.defaultTarget.repsMax,
                        targetRir = ex.defaultTarget.rirMin,
                        targetRirMax = ex.defaultTarget.rirMax,
                        targetRpe = ex.defaultTarget.rpe,
                        targetWeightKg = ex.defaultTarget.weightKg(),
                    )
                )
                programs.insertPlannedSets(ex.sets.mapIndexed { k, t ->
                    PlannedSetEntity(
                        sessionExerciseId = seId,
                        setIndex = k + 1,
                        repsMin = t.repsMin,
                        repsMax = t.repsMax,
                        rir = t.rirMin,
                        rirMax = t.rirMax,
                        rpe = t.rpe,
                        weightKg = t.weightKg(),
                        restSeconds = t.restSeconds,
                    )
                })
            }
        }
        programId
    }
}
