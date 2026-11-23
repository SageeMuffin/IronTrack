package com.irontrack.core.importer

import com.irontrack.core.catalog.ExerciseMatch

enum class WeightUnit { KG, LB }

/**
 * Cible d'une série telle que lue dans la source. Chaque champ est indépendant et
 * `null` signifie « non renseigné » (jamais zéro).
 */
data class SetTarget(
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val rirMin: Int? = null,
    val rirMax: Int? = null,
    /** RPE d'origine, conservé tel quel (jamais converti silencieusement). */
    val rpe: Double? = null,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val restSeconds: Int? = null,
) {
    val isEmpty: Boolean
        get() = repsMin == null && repsMax == null && rirMin == null && rirMax == null &&
            rpe == null && weight == null && restSeconds == null

    /** Complète les champs manquants à partir d'une autre cible (sans écraser l'existant). */
    fun fillFrom(o: SetTarget): SetTarget {
        val hasReps = repsMin != null || repsMax != null
        val hasRir = rirMin != null || rirMax != null
        return SetTarget(
            repsMin = if (hasReps) repsMin else o.repsMin,
            repsMax = if (hasReps) repsMax else o.repsMax,
            rirMin = if (hasRir) rirMin else o.rirMin,
            rirMax = if (hasRir) rirMax else o.rirMax,
            rpe = rpe ?: o.rpe,
            weight = weight ?: o.weight,
            weightUnit = if (weight != null) weightUnit else o.weightUnit,
            restSeconds = restSeconds ?: o.restSeconds,
        )
    }

    /** Poids converti en kilogrammes (stockage interne). */
    fun weightKg(): Double? = weight?.let { if (weightUnit == WeightUnit.LB) it * 0.45359237 else it }
}

/** Remarque sur une donnée importée qui doit être vérifiée par l'utilisateur. */
data class ImportIssue(val message: String, val sourceText: String? = null)

data class ParsedExercise(
    val rawName: String,
    val sourceLine: String,
    /** Nombre de séries indiqué par la source, ou null s'il n'est pas précisé. */
    val setCount: Int?,
    /** Séries prévues (une entrée par série, chacune avec ses propres cibles). */
    val sets: List<SetTarget>,
    /** Cibles indiquées sans nombre de séries (ex. « 8-10 reps ») : appliquées aux séries ajoutées. */
    val defaultTarget: SetTarget,
    val notes: String?,
    val issues: List<ImportIssue>,
    /** Rapprochement avec la bibliothèque (rempli par [ProgramAnalyzer]). */
    val match: ExerciseMatch? = null,
    /** Ligne issue d'une reconnaissance de texte peu fiable. */
    val lowOcrConfidence: Boolean = false,
) {
    val isUncertain: Boolean
        get() = issues.isNotEmpty() || lowOcrConfidence || (match != null && match.best == null)
}

data class ParsedDay(
    val title: String,
    /** Libellé de semaine : "A", "B", "1", "2"… ou null si la source n'en indique pas. */
    val weekLabel: String?,
    /** Jour de la semaine (1 = lundi … 7 = dimanche) uniquement s'il est explicite dans la source. */
    val dayOfWeek: Int?,
    val isRest: Boolean,
    val exercises: List<ParsedExercise>,
    val estimatedDurationMinutes: Int? = null,
    val notes: String? = null,
)

enum class SplitType(val labelFr: String) {
    FULL_BODY("Full body"),
    UPPER_LOWER("Haut / Bas du corps (Upper / Lower)"),
    PPL("Push / Pull / Legs"),
    PPL_X2("Push / Pull / Legs × 2"),
    BRO_SPLIT("Split par groupe musculaire"),
    CUSTOM("Organisation personnalisée"),
}

data class SourceLine(val text: String, val confidence: Float? = null, val page: Int? = null)

data class ParsedProgram(
    val title: String?,
    val days: List<ParsedDay>,
    /** Semaines détectées, dans l'ordre (ex. ["A","B"] ou ["1","2","3"]). Vide si une seule semaine implicite. */
    val weekLabels: List<String>,
    val hasAbWeeks: Boolean,
    val split: SplitType,
    /** Lignes qui n'ont pas pu être interprétées : jamais supprimées silencieusement. */
    val unrecognized: List<SourceLine>,
) {
    val trainingDays: List<ParsedDay> get() = days.filter { !it.isRest }
    val exerciseCount: Int get() = trainingDays.sumOf { it.exercises.size }
    val totalPlannedSets: Int get() = trainingDays.sumOf { d -> d.exercises.sumOf { it.sets.size } }
    val uncertainCount: Int get() = trainingDays.sumOf { d -> d.exercises.count { it.isUncertain } } + unrecognized.size
}
