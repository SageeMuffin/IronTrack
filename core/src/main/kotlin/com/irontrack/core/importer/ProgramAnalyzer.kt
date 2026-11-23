package com.irontrack.core.importer

import com.irontrack.core.catalog.ExerciseMatcher

/**
 * Point d'entrée de l'analyse : texte -> structure -> rapprochement des exercices avec la bibliothèque.
 * Indépendant du format d'origine (TXT, texte collé, PDF, OCR d'image).
 */
class ProgramAnalyzer(
    private val matcher: ExerciseMatcher = ExerciseMatcher.fromCatalog(),
    private val parser: ProgramTextParser = ProgramTextParser(),
) {
    fun analyze(text: String): ParsedProgram = analyzeLines(text.lines().map { SourceLine(it) })

    fun analyzeLines(lines: List<SourceLine>): ParsedProgram {
        val program = parser.parseLines(lines)
        return program.copy(days = program.days.map { d ->
            d.copy(exercises = d.exercises.map { ex -> ex.copy(match = matcher.match(ex.rawName)) })
        })
    }
}
