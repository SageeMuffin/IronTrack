package com.irontrack.core.importer

import com.irontrack.core.catalog.ExerciseCatalog
import com.irontrack.core.util.TextNormalizer

/**
 * Transforme le texte d'un programme (saisi, collé, extrait d'un PDF ou reconnu par OCR)
 * en structure de programme. L'ordre d'origine est toujours conservé.
 */
class ProgramTextParser {

    fun parse(text: String): ParsedProgram =
        parseLines(text.lines().map { SourceLine(it) })

    fun parseLines(lines: List<SourceLine>): ParsedProgram = Builder().apply { lines.forEach(::accept) }.build()

    // ------------------------------------------------------------------------------------

    private class DayBuilder(
        var title: String,
        val weekLabel: String?,
        var dayOfWeek: Int?,
        var isRest: Boolean,
        var duration: Int? = null,
    ) {
        val exercises = mutableListOf<ExBuilder>()
        val notes = mutableListOf<String>()
    }

    private class ExBuilder(
        val rawName: String,
        val sourceLine: String,
        var setCount: Int?,
        val sets: MutableList<SetTarget>,
        var defaultTarget: SetTarget,
        val notes: MutableList<String>,
        val issues: MutableList<ImportIssue>,
        val lowConfidence: Boolean,
    ) {
        /** Séries détaillées ligne par ligne (« Série 1 : 10 reps, RIR 2 »). */
        val explicitSets = sortedMapOf<Int, SetTarget>()
    }

    private data class Columns(
        val name: Int, val sets: Int?, val reps: Int?, val rir: Int?, val rpe: Int?,
        val weight: Int?, val rest: Int?, val notes: Int?, val separator: Regex,
    )

    private inner class Builder {
        var title: String? = null
        val days = mutableListOf<DayBuilder>()
        val weekLabels = mutableListOf<String>()
        var currentWeek: String? = null
        var currentDay: DayBuilder? = null
        var currentEx: ExBuilder? = null
        var columns: Columns? = null
        val unrecognized = mutableListOf<SourceLine>()

        fun accept(src: SourceLine) {
            val line = TextNormalizer.normalizeLine(src.text).trim()
            if (line.isBlank()) return
            val low = src.confidence != null && src.confidence < LOW_CONFIDENCE
            if (line.all { !it.isLetterOrDigit() }) return // séparateurs « ----- », « ===== »

            // Titre explicite du programme.
            programTitle.find(line)?.let { m ->
                if (title == null) { title = m.groupValues[1].trim().trimEnd(':').ifBlank { null } ?: line; return }
            }

            // En-tête de tableau.
            detectColumns(line)?.let { columns = it; return }
            columns?.let { cols ->
                val cells = line.split(cols.separator).map { it.trim() }
                if (cells.size >= 2 && cells[cols.name].any { it.isLetter() }) {
                    if (tableRow(cells, cols, line, low)) return
                } else if (cells.size < 2) {
                    columns = null
                }
            }

            // Semaine (A/B, 1, 2…).
            weekHeading.find(line)?.let { m ->
                val label = m.groupValues[1].uppercase()
                currentWeek = label
                if (label !in weekLabels) weekLabels += label
                currentDay = null; currentEx = null
                val rest = line.substring(m.range.last + 1).trim().trimStart(':', '-', '–', '.', ')').trim()
                if (rest.isNotEmpty()) accept(src.copy(text = rest))
                return
            }

            // Durée estimée de la séance.
            durationLine.find(line)?.let { m ->
                ParamParser.parseSessionDurationMinutes(m.groupValues[1])?.let { min ->
                    val d = currentDay ?: newDay("Séance ${days.count { !it.isRest } + 1}", null, false)
                    d.duration = min
                    return
                }
            }

            // Notes explicites.
            noteLine.find(line)?.let { m ->
                val txt = m.groupValues[1].trim()
                (currentEx?.notes ?: currentDay?.notes)?.add(txt) ?: run { if (title == null) title = null }
                if (currentEx == null && currentDay == null) unrecognized += src
                return
            }

            // Ligne de série détaillée.
            if (currentEx != null) {
                setLine.find(line)?.let { m ->
                    val idx = m.groupValues[1].toInt()
                    val p = ParamParser.parse(m.groupValues[2])
                    val ex = currentEx!!
                    val n = p.setCount
                    if (n != null && n > 1 && p.perSet == null) {
                        // « Séries 2-3 : … » ou « 2 séries : … »
                        repeat(n) { k -> ex.explicitSets[idx + k] = p.common }
                    } else ex.explicitSets[idx] = p.perSet?.firstOrNull() ?: p.common
                    ex.issues += p.issues
                    if (p.leftover.isNotBlank() && p.issues.isEmpty()) ex.notes += "Série $idx : ${p.leftover}"
                    return
                }
                bulletParamsOnly(line)?.let { params ->
                    val p = ParamParser.parse(params)
                    if (p.foundAnything && (p.common.repsMin != null || p.common.rirMin != null || p.common.weight != null || p.common.rpe != null)) {
                        val ex = currentEx!!
                        val idx = (ex.explicitSets.keys.maxOrNull() ?: 0) + 1
                        ex.explicitSets[idx] = p.perSet?.firstOrNull() ?: p.common
                        ex.issues += p.issues
                        return
                    }
                }
            }

            // En-tête de jour / séance.
            val heading = parseHeading(line)
            if (heading != null) {
                currentEx = null
                if (heading.isTitleOnly) {
                    if (title == null && days.isEmpty()) title = heading.title else unrecognized += src
                    return
                }
                newDay(heading.title, heading.dayOfWeek, heading.isRest).duration = heading.duration
                return
            }

            // Exercice.
            parseExerciseLine(line, low)?.let { ex ->
                val day = currentDay ?: newDay("Séance ${days.count { !it.isRest } + 1}", null, false)
                if (day.isRest) {
                    // Exercice sous un jour « repos » : ce n'est finalement pas un repos.
                    day.isRest = false
                }
                day.exercises += ex
                currentEx = ex
                return
            }

            // Rien de reconnu : on garde la ligne pour l'utilisateur.
            if (title == null && days.isEmpty() && line.any { it.isLetter() } && line.length <= 80) {
                title = line; return
            }
            unrecognized += src
        }

        fun newDay(title: String, dow: Int?, rest: Boolean): DayBuilder {
            val d = DayBuilder(title, currentWeek, dow, rest)
            days += d
            currentDay = d
            currentEx = null
            return d
        }

        private fun tableRow(cells: List<String>, c: Columns, line: String, low: Boolean): Boolean {
            fun cell(i: Int?) = i?.let { cells.getOrNull(it) }?.takeIf { it.isNotBlank() && it != "-" }
            val name = cell(c.name) ?: return false
            if (parseHeading(name) != null && cells.drop(1).all { it.isBlank() }) return false
            val parts = buildList {
                cell(c.sets)?.let { add(if (it.all(Char::isDigit)) "$it séries" else it) }
                cell(c.reps)?.let { add(if (it.matches(Regex("\\d{1,3}(\\s*-\\s*\\d{1,3})?"))) "$it reps" else it) }
                cell(c.rir)?.let { add("RIR $it") }
                cell(c.rpe)?.let { add("RPE $it") }
                cell(c.weight)?.let { add(if (it.any(Char::isLetter)) it else "$it kg") }
                cell(c.rest)?.let { add("repos $it") }
            }
            val ex = buildExercise(name, parts.joinToString(", "), line, low)
            cell(c.notes)?.let { ex.notes += it }
            val day = currentDay ?: newDay("Séance ${days.count { !it.isRest } + 1}", null, false)
            day.exercises += ex
            currentEx = ex
            return true
        }

        fun build(): ParsedProgram {
            // Un premier « jour » vide suivi d'autres jours est en réalité le titre du programme.
            if (title == null && days.size > 1) {
                val first = days.first()
                if (!first.isRest && first.exercises.isEmpty() && first.dayOfWeek == null && first.duration == null) {
                    title = first.title; days.removeAt(0)
                }
            }
            var built = days.map { d ->
                ParsedDay(
                    title = d.title,
                    weekLabel = d.weekLabel,
                    dayOfWeek = d.dayOfWeek,
                    isRest = d.isRest,
                    exercises = d.exercises.map { it.toParsed() },
                    estimatedDurationMinutes = d.duration,
                    notes = d.notes.joinToString("\n").ifBlank { null },
                )
            }
            built = mergeSchedule(built)
            val hasAB = weekLabels.contains("A") && weekLabels.contains("B")
            return ParsedProgram(
                title = title,
                days = built,
                weekLabels = weekLabels.toList(),
                hasAbWeeks = hasAB,
                split = SplitDetector.detect(built.filter { !it.isRest }),
                unrecognized = unrecognized.toList(),
            )
        }
    }

    private fun ExBuilder.toParsed(): ParsedExercise {
        var list: List<SetTarget> = sets.toList()
        var count = setCount
        val issuesOut = issues.toMutableList()
        if (explicitSets.isNotEmpty()) {
            val maxIdx = explicitSets.lastKey()
            val n = maxOf(maxIdx, count ?: 0)
            if (count != null && maxIdx > count) {
                issuesOut += ImportIssue("Plus de séries détaillées ($maxIdx) que de séries annoncées ($count)")
            }
            list = (1..n).map { i ->
                val base = list.getOrNull(i - 1) ?: defaultTarget
                explicitSets[i]?.fillFrom(base) ?: base
            }
            count = n
        }
        return ParsedExercise(
            rawName = rawName,
            sourceLine = sourceLine,
            setCount = count,
            sets = list,
            defaultTarget = if (count == null) defaultTarget else SetTarget(),
            notes = notes.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            issues = issuesOut,
            lowOcrConfidence = lowConfidence,
        )
    }

    // ------------------------------------------------------------------------------------
    // Exercices

    private fun parseExerciseLine(line: String, low: Boolean): ExBuilder? {
        var l = line.replace(leadingBullet, "").trim()
        if (l.isEmpty() || l.none { it.isLetter() }) return null

        // Séparation nom / paramètres.
        var name: String
        var params: String
        val colon = l.indexOf(':')
        val dash = Regex("\\s[-|]\\s").find(l)
        val tabCells = l.split(Regex("\\t|\\s{3,}|\\s\\|\\s")).filter { it.isNotBlank() }
        when {
            colon in 1 until l.length - 1 && l.substring(colon + 1).any { it.isDigit() } -> {
                name = l.substring(0, colon); params = l.substring(colon + 1)
            }
            tabCells.size >= 2 && tabCells.drop(1).any { c -> c.any { it.isDigit() } } -> {
                name = tabCells.first(); params = tabCells.drop(1).joinToString(", ")
            }
            dash != null && l.substring(dash.range.last).any { it.isDigit() } && l.substring(0, dash.range.first).any { it.isLetter() } -> {
                name = l.substring(0, dash.range.first); params = l.substring(dash.range.last + 1)
            }
            else -> {
                val m = paramStart.find(l)
                if (m != null && m.range.first > 0) {
                    name = l.substring(0, m.range.first); params = l.substring(m.range.first)
                } else if (m != null) {
                    // Paramètres d'abord : « 3x10 Développé couché »
                    val p = ParamParser.parse(l)
                    val leftoverName = p.leftover.takeIf { it.any(Char::isLetter) } ?: return null
                    return buildExercise(leftoverName, l.replace(leftoverName, ""), line, low)
                } else {
                    name = l; params = ""
                }
            }
        }
        name = name.trim().trimEnd(':', '-', ',', '.', '(').trim()
        // Parenthèses dans le nom : on les garde en note (ex. « Squat (low bar) »).
        val paren = Regex("\\(([^)]*)\\)").find(name)
        var extraNote: String? = null
        if (paren != null && paren.groupValues[1].none { it.isDigit() }) {
            extraNote = paren.groupValues[1].trim()
            name = name.removeRange(paren.range).trim()
        }
        if (name.isEmpty() || name.none { it.isLetter() }) return null
        if (params.isBlank() && !looksLikeExerciseName(name)) return null
        return buildExercise(name, params, line, low).also { b -> extraNote?.let { b.notes.add(0, it) } }
    }

    private fun buildExercise(name: String, params: String, line: String, low: Boolean): ExBuilder {
        val p = ParamParser.parse(params)
        val sets: MutableList<SetTarget> = when {
            p.perSet != null -> p.perSet.toMutableList()
            p.setCount != null -> MutableList(p.setCount) { p.common }
            else -> mutableListOf()
        }
        val notes = mutableListOf<String>()
        if (p.tempo != null) notes += "Tempo ${p.tempo}"
        if (p.leftover.isNotBlank()) notes += p.leftover
        val issues = p.issues.toMutableList()
        if (low) issues += ImportIssue("Texte peu lisible : à vérifier", line)
        return ExBuilder(
            rawName = name.trim(),
            sourceLine = line,
            setCount = p.setCount,
            sets = sets,
            defaultTarget = if (p.setCount == null) p.common else SetTarget(),
            notes = notes,
            issues = issues,
            lowConfidence = low,
        )
    }

    private fun looksLikeExerciseName(name: String): Boolean {
        val words = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 8 || name.length > 60) return false
        if (name.endsWith(".") && words.size > 4) return false // phrase
        val letters = name.count { it.isLetter() }
        return letters >= 3 && letters.toDouble() / name.length > 0.6
    }

    // ------------------------------------------------------------------------------------
    // En-têtes

    private data class Heading(
        val title: String,
        val dayOfWeek: Int?,
        val isRest: Boolean,
        val duration: Int?,
        val isTitleOnly: Boolean = false,
    )

    private fun parseHeading(raw: String): Heading? {
        var line = raw.replace(leadingBullet, "").trim().trim('#', '*', '=', '_').trim()
        if (line.isEmpty()) return null
        val norm = TextNormalizer.normalizeName(line)

        // Durée entre parenthèses « (1h15) » / « (~75 min) ».
        var duration: Int? = null
        Regex("[(\\[]\\s*(?:~|env\\.?|environ|≈)?\\s*(\\d{1,2}\\s*h\\s*\\d{0,2}|\\d{1,3}\\s*(?:min|mn|minutes))\\s*[)\\]]", RegexOption.IGNORE_CASE)
            .find(line)?.let { m ->
                duration = ParamParser.parseSessionDurationMinutes(m.groupValues[1])
                line = line.removeRange(m.range).trim()
            }

        // Jour de la semaine en tête de ligne.
        val dowMatch = weekdayStart.find(TextNormalizer.stripAccents(line.lowercase()))
        if (dowMatch != null) {
            val dow = weekdayIndex(dowMatch.groupValues[1])
            val rest = line.substring(dowMatch.range.last + 1).trim().trimStart(':', '-', '.', ')', '–', ',').trim()
            val restNorm = TextNormalizer.normalizeName(rest)
            if (rest.isNotEmpty() && paramStart.containsMatchIn(rest) && parseExerciseLine(rest, false) != null &&
                splitWords.none { restNorm.contains(it) }) {
                return null // « Lundi 3x10 squat » : rare, on laisse l'analyse d'exercice faire
            }
            val isRest = rest.isEmpty().not() && restWords.containsMatchIn(restNorm)
            val title = when {
                rest.isEmpty() -> WEEKDAYS_FR[dow - 1]
                isRest -> "Repos"
                else -> rest
            }
            return Heading(title, dow, isRest, duration)
        }

        if (paramStart.containsMatchIn(line)) return null
        val hasDigitsParams = Regex("\\d+\\s*(?:x|reps?|séries?|sets?)", RegexOption.IGNORE_CASE).containsMatchIn(line)
        if (hasDigitsParams) return null

        val words = norm.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 8) return null
        val endsColon = line.endsWith(":")
        val letters = line.filter { it.isLetter() }
        val upper = letters.length >= 3 && letters.all { it.isUpperCase() }
        val isDayLabel = dayLabel.containsMatchIn(norm)
        val hasSplitWord = words.any { w -> w in splitWords } || splitPhrases.any { norm.contains(it) }
        val onlyMuscles = words.all { it in muscleWords || it in connectorWords } && words.any { it in muscleWords }
        val isRestLine = restWords.matches(norm) || norm in setOf("repos", "rest", "off", "rest day", "jour de repos", "recuperation")
        val catalogExact = ExerciseCatalog.all.any { e -> ExerciseCatalog.aliases(e).contains(norm) }

        val isTitle = programWords.containsMatchIn(norm) && !isDayLabel && !hasSplitWord
        if (isTitle) return Heading(line.trimEnd(':').trim(), null, false, duration, isTitleOnly = true)

        val heading = isRestLine || isDayLabel || (hasSplitWord && !catalogExact) || onlyMuscles ||
            (endsColon && !catalogExact) || (upper && !catalogExact && words.size <= 6)
        if (!heading) return null
        val t = line.trimEnd(':').trim()
        return Heading(if (isRestLine) "Repos" else t, null, isRestLine, duration)
    }

    private fun detectColumns(line: String): Columns? {
        val sep = when {
            line.contains('|') -> Regex("\\s*\\|\\s*")
            line.contains('\t') -> Regex("\\t+")
            line.contains(';') -> Regex("\\s*;\\s*")
            else -> Regex("\\s{2,}")
        }
        val cells = line.trim().trim('|').split(sep).map { TextNormalizer.normalizeName(it) }
        if (cells.size < 2) return null
        fun idx(vararg keys: String) = cells.indexOfFirst { c -> keys.any { k -> c == k || c.startsWith("$k ") || c.startsWith(k) } }.takeIf { it >= 0 }
        val name = idx("exercice", "exercise", "exercices", "exercises", "mouvement", "movement", "nom") ?: return null
        val sets = idx("series", "serie", "sets", "set", "nb series")
        val reps = idx("reps", "repetitions", "rep", "reps cible")
        if (sets == null && reps == null) return null
        return Columns(
            name = name, sets = sets, reps = reps,
            rir = idx("rir"), rpe = idx("rpe"),
            weight = idx("charge", "poids", "weight", "load", "kg"),
            rest = idx("repos", "rest", "recup", "recuperation", "pause"),
            notes = idx("notes", "note", "remarques", "commentaire", "commentaires", "consignes"),
            separator = if (line.trim().startsWith("|")) Regex("\\s*\\|\\s*") else sep,
        ).let { c -> if (line.trim().startsWith("|")) c.copy(name = c.name + 1, sets = c.sets?.plus(1), reps = c.reps?.plus(1), rir = c.rir?.plus(1), rpe = c.rpe?.plus(1), weight = c.weight?.plus(1), rest = c.rest?.plus(1), notes = c.notes?.plus(1)) else c }
    }

    private fun bulletParamsOnly(line: String): String? {
        val l = line.replace(leadingBullet, "").trim()
        if (l.length == line.trim().length && !line.trim().first().isDigit()) {
            // Pas de puce : on n'accepte que si la ligne ne contient aucun mot hors vocabulaire des paramètres
        }
        val words = TextNormalizer.normalizeName(l).split(' ').filter { it.any(Char::isLetter) }
        return if (words.all { it in paramVocabulary }) l else null
    }

    /** Fusionne un calendrier (« LUNDI : PUSH ») avec les séances détaillées plus loin (« PUSH »). */
    private fun mergeSchedule(days: List<ParsedDay>): List<ParsedDay> {
        val scheduleEntries = days.filter { it.dayOfWeek != null && !it.isRest && it.exercises.isEmpty() }
        if (scheduleEntries.isEmpty()) return days
        val detailed = days.filter { it.exercises.isNotEmpty() }
        val result = days.toMutableList()
        val usedDetailed = mutableSetOf<ParsedDay>()
        for (entry in scheduleEntries) {
            val key = TextNormalizer.normalizeName(entry.title)
            val target = detailed.firstOrNull { TextNormalizer.normalizeName(it.title) == key && (it.weekLabel == entry.weekLabel || entry.weekLabel == null || it.weekLabel == null) }
                ?: continue
            result.remove(entry)
            val idx = result.indexOf(target)
            if (target.dayOfWeek == null && target !in usedDetailed) {
                result[idx] = target.copy(dayOfWeek = entry.dayOfWeek)
                usedDetailed += result[idx]
                // On garde la référence à jour pour les prochaines entrées.
                val i2 = detailed.indexOf(target)
                if (i2 >= 0) (detailed as MutableList<ParsedDay>)[i2] = result[idx]
            } else {
                val base = result.getOrNull(idx) ?: target
                result.add(base.copy(dayOfWeek = entry.dayOfWeek))
            }
        }
        // Jours de repos du calendrier conservés pour l'organisation hebdomadaire.
        return result.sortedWith(compareBy({ it.weekLabel ?: "" }, { if (it.dayOfWeek == null) Int.MAX_VALUE else 0 }))
            .let { sorted -> if (result.all { it.dayOfWeek != null }) sorted.sortedWith(compareBy({ it.weekLabel ?: "" }, { it.dayOfWeek })) else result }
    }

    companion object {
        const val LOW_CONFIDENCE = 0.6f

        val WEEKDAYS_FR = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

        private val leadingBullet = Regex("^\\s*(?:[-•*·▪►>✓✔]+|\\d{1,2}[.)]|[A-Za-z]\\d{1,2}[.):]|\\(\\d{1,2}\\))\\s*")

        private val programTitle = Regex("^(?:programme|program|plan|routine)\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        private val weekHeading = Regex("^\\s*(?:semaine|week|sem\\.?|wk)\\s*[:#n°-]?\\s*([AB]|\\d{1,2})\\b", RegexOption.IGNORE_CASE)
        private val durationLine = Regex("^(?:durée(?:\\s+estimée)?|duree(?:\\s+estimee)?|duration|temps\\s+estimé|temps\\s+estime|estimated\\s+duration)\\s*[:=-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        private val noteLine = Regex("^(?:notes?|nb|remarques?|conseils?|consignes?)\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        private val setLine = Regex("^\\s*[-•*]?\\s*(?:s[ée]ries?|set|s)\\s*#?\\s*(\\d{1,2})\\s*[:.)\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        val paramStart = Regex("(?<![A-Za-z])\\d{1,3}\\s*[xX×*]\\s*\\d|\\d{1,3}\\s*(?:séries?|series|sets?|reps?|répétitions?|repetitions?)\\b", RegexOption.IGNORE_CASE)

        private val weekdayStart = Regex("^(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|monday|tuesday|wednesday|thursday|friday|saturday|sunday|lun|mar|mer|jeu|ven|sam|dim|mon|tue|wed|thu|fri|sat|sun)(?=$|[\\s:.\\-),])")

        private fun weekdayIndex(w: String): Int = when (w) {
            "lundi", "monday", "lun", "mon" -> 1
            "mardi", "tuesday", "mar", "tue" -> 2
            "mercredi", "wednesday", "mer", "wed" -> 3
            "jeudi", "thursday", "jeu", "thu" -> 4
            "vendredi", "friday", "ven", "fri" -> 5
            "samedi", "saturday", "sam", "sat" -> 6
            else -> 7
        }

        private val restWords = Regex("^(?:repos|rest|off|rest day|jour de repos|recuperation|recup|repos actif|active rest)\\b")
        private val dayLabel = Regex("^(?:jour|day|seance|session|workout|entrainement|training|j|d)\\s*[0-9a-z]{1,2}\\b|^(?:jour|day|seance|session|workout|entrainement|training)\\b")
        private val programWords = Regex("\\b(?:programme|program|plan|routine|cycle|bloc|block|mesocycle|split\\s+\\d)\\b")
        val splitWords = setOf("upper", "lower", "push", "pull", "legs", "leg", "full", "fullbody", "haut", "bas", "torse",
            "jambes", "poussee", "tirage", "ppl", "ul")
        private val splitPhrases = listOf("full body", "haut du corps", "bas du corps", "corps entier", "leg day")
        val muscleWords = setOf("pecs", "pectoraux", "pecto", "dos", "epaules", "epaule", "bras", "biceps", "triceps", "jambes",
            "quadriceps", "quads", "ischios", "ischio", "fessiers", "mollets", "abdos", "abdominaux", "chest", "back", "shoulders",
            "arms", "legs", "glutes", "hamstrings", "calves", "abs", "trapezes", "traps", "delts", "deltoides", "lats")
        private val connectorWords = setOf("et", "and", "plus", "a", "b", "c", "x")
        private val paramVocabulary = setOf("reps", "rep", "rir", "rpe", "kg", "kgs", "lb", "lbs", "x", "s", "sec", "min", "repos",
            "rest", "de", "of", "a", "serie", "series", "set", "sets", "repetitions", "repetition", "r", "et", "and", "mn")
    }
}
