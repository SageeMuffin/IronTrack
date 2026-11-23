package com.irontrack.app.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irontrack.app.AppContainer
import com.irontrack.app.importer.EditableDay
import com.irontrack.app.importer.EditableExercise
import com.irontrack.app.importer.ExerciseChoice
import com.irontrack.app.importer.ExtractionResult
import com.irontrack.app.importer.ImportDraft
import com.irontrack.app.importer.ImportException
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.core.importer.ProgramTextParser
import com.irontrack.core.importer.SetTarget
import com.irontrack.core.importer.SourceLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUiState {
    data object Input : ImportUiState
    data class Analyzing(val message: String?) : ImportUiState
    data class Error(val message: String) : ImportUiState
    data class Preview(
        val draft: ImportDraft,
        val sourceLines: List<SourceLine>,
        val ocrUsed: Boolean,
        val warnings: List<String>,
        /** Semaine retenue pour un programme à semaines numérotées. */
        val selectedWeek: String?,
        val mapTwoWeeksToAB: Boolean,
    ) : ImportUiState
    data class Saved(val programId: Long) : ImportUiState
}

class ImportViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Input)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val parser = ProgramTextParser()
    private var lastExtraction: ExtractionResult? = null

    fun reset() { _state.value = ImportUiState.Input; lastExtraction = null }

    fun analyzeText(text: String, defaultName: String) {
        if (text.isBlank()) return
        process(defaultName) { ExtractionResult(text.lines().map { SourceLine(it) }, 1, false, emptyList()) }
    }

    fun analyzeFiles(uris: List<Pair<Uri, String?>>, defaultName: String) {
        if (uris.isEmpty()) return
        process(defaultName) { c.documentExtractor.extractAll(uris) { msg -> _state.value = ImportUiState.Analyzing(msg) } }
    }

    private fun process(defaultName: String, extract: suspend () -> ExtractionResult) = viewModelScope.launch {
        _state.value = ImportUiState.Analyzing(null)
        try {
            val r = extract()
            lastExtraction = r
            if (r.lines.none { it.text.any(Char::isLetterOrDigit) }) {
                _state.value = ImportUiState.Error(app.getString(com.irontrack.app.R.string.import_error_no_text))
                return@launch
            }
            buildPreview(r, defaultName, null, false)
        } catch (e: ImportException) {
            _state.value = ImportUiState.Error(e.message ?: app.getString(com.irontrack.app.R.string.import_error_generic))
        } catch (e: Exception) {
            _state.value = ImportUiState.Error(app.getString(com.irontrack.app.R.string.import_error_generic))
        }
    }

    private suspend fun buildPreview(r: ExtractionResult, defaultName: String, week: String?, mapAB: Boolean) {
        val parsed = withContext(Dispatchers.Default) { parser.parseLines(r.lines) }
        val numbered = !parsed.hasAbWeeks && parsed.weekLabels.size > 1
        val selectedWeek = if (numbered && !mapAB) (week ?: parsed.weekLabels.first()) else null
        val draft = c.imports.buildDraft(parsed, selectedWeek, mapAB, defaultName)
        _state.value = ImportUiState.Preview(draft, r.lines, r.ocrUsed, r.warnings, selectedWeek, mapAB)
    }

    /** Change la semaine retenue ou l'association explicite semaine 1/2 -> A/B. */
    fun changeWeekMode(week: String?, mapAB: Boolean) {
        val p = _state.value as? ImportUiState.Preview ?: return
        val r = lastExtraction ?: return
        viewModelScope.launch { buildPreview(r, p.draft.programName, week, mapAB) }
    }

    // ------------------------------------------------------------------ modifications de l'aperçu
    private fun edit(f: (ImportDraft) -> ImportDraft) {
        val p = _state.value as? ImportUiState.Preview ?: return
        _state.value = p.copy(draft = f(p.draft))
    }

    fun setName(n: String) = edit { it.copy(programName = n) }
    fun setActivate(v: Boolean) = edit { it.copy(activate = v) }
    fun setAb(v: Boolean) = edit { d -> d.copy(abEnabled = v, days = if (v) d.days else d.days.map { it.copy(week = "A") }) }

    fun updateDay(key: Long, f: (EditableDay) -> EditableDay) = edit { d -> d.copy(days = d.days.map { if (it.key == key) f(it) else it }) }

    fun updateExercise(dayKey: Long, exKey: Long, f: (EditableExercise) -> EditableExercise) =
        updateDay(dayKey) { d -> d.copy(exercises = d.exercises.map { if (it.key == exKey) f(it) else it }) }

    fun chooseExercise(dayKey: Long, exKey: Long, choice: ExerciseChoice) =
        updateExercise(dayKey, exKey) { it.copy(choice = choice, matchConfident = true) }

    fun updateSet(dayKey: Long, exKey: Long, index: Int, f: (SetTarget) -> SetTarget) =
        updateExercise(dayKey, exKey) { e -> e.copy(sets = e.sets.mapIndexed { i, s -> if (i == index) f(s) else s }) }

    fun addSet(dayKey: Long, exKey: Long) = updateExercise(dayKey, exKey) { it.copy(sets = it.sets + SetTarget()) }
    fun removeSet(dayKey: Long, exKey: Long, index: Int) = updateExercise(dayKey, exKey) { e -> e.copy(sets = e.sets.filterIndexed { i, _ -> i != index }) }
    fun resolveIssues(dayKey: Long, exKey: Long) = updateExercise(dayKey, exKey) { it.copy(issues = emptyList(), lowOcrConfidence = false, matchConfident = true) }

    fun save() {
        val p = _state.value as? ImportUiState.Preview ?: return
        viewModelScope.launch {
            try {
                val id = c.imports.save(p.draft)
                ReminderScheduler.reschedule(app)
                _state.value = ImportUiState.Saved(id)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(app.getString(com.irontrack.app.R.string.import_error_save))
            }
        }
    }
}
