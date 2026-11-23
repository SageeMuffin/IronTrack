package com.irontrack.core.importer

import com.irontrack.core.util.TextNormalizer

/**
 * Analyse la partie « paramètres » d'une ligne d'exercice :
 * « 3 x 8-10 @ RIR 2, 90 s de repos », « 3 séries de 10 répétitions », « 4x12 @ 60 kg »…
 *
 * Principe : chaque motif reconnu est retiré du texte ; ce qui reste devient une note
 * ou un signalement. Rien n'est inventé : un paramètre absent reste `null`.
 */
object ParamParser {

    data class Result(
        val setCount: Int?,
        /** Cibles individuelles par série si la source les détaille (ex. 3x12/10/8). */
        val perSet: List<SetTarget>?,
        /** Cibles communes (s'appliquent à toutes les séries). */
        val common: SetTarget,
        val tempo: String?,
        val leftover: String,
        val issues: List<ImportIssue>,
    ) {
        val foundAnything: Boolean
            get() = setCount != null || perSet != null || !common.isEmpty || tempo != null
    }

    private const val REPS_WORD = "(?:reps?|rép(?:étition)?s?|rep(?:etition)?s?|répétitions?|repetitions?|r\\b)"
    private const val NUM = "(\\d{1,3})"
    private const val DEC = "(\\d{1,4}(?:[.,]\\d{1,2})?)"
    private val opt = setOf(RegexOption.IGNORE_CASE)

    private val tempoRe = Regex("\\btempo\\s*[:=]?\\s*([0-9xX]{1,2}(?:\\s*[-/]\\s*[0-9xX]{1,2}){2,3})", opt)
    private val percentRe = Regex("@?\\s*(\\d{1,3}(?:[.,]\\d)?)\\s*%\\s*(?:(?:du|de|of)\\s*)?(?:1\\s*rm|rm|1rm|max)?", opt)

    // Repos avec mot-clé, avant ou après la durée.
    private const val DUR = "(\\d{1,2}\\s*h\\s*\\d{0,2}|\\d{1,2}\\s*:\\s*\\d{2}|\\d{1,2}\\s*'\\s*\\d{0,2}(?:''|\")?|\\d{1,3}\\s*(?:min(?:utes?)?|mn|m)\\s*\\d{0,2}\\s*(?:s|sec)?|\\d{1,4}\\s*(?:s|sec|secs|secondes?|seconds?)\\b|\\d{1,4})"
    private val restKeywordBefore = Regex("(?:repos|rest|r[ée]cup(?:[ée]ration)?|pause)\\s*(?:de|:|=|-)?\\s*$DUR(?:\\s*(?:-|à|a|to)\\s*$DUR)?", opt)
    private val restKeywordAfter = Regex("$DUR\\s*(?:de\\s*|of\\s*)?(?:repos|rest|r[ée]cup(?:[ée]ration)?|pause)", opt)
    private val bareDuration = Regex("(?<![\\d.,])(\\d{1,3}\\s*(?:min(?:utes?)?|mn)\\s*\\d{0,2}\\s*(?:s|sec)?|\\d{1,4}\\s*(?:s|sec|secs|secondes?|seconds?)\\b|\\d{1,2}\\s*'\\s*\\d{0,2}(?:''|\")?)", opt)

    private val rirBefore = Regex("\\brir\\s*[:=]?\\s*$NUM(?:\\s*(?:-|à|a|to)\\s*$NUM)?", opt)
    private val rirAfter = Regex("(?<![\\d.,])$NUM(?:\\s*-\\s*$NUM)?\\s*rir\\b", opt)
    private val rpeBefore = Regex("\\brpe\\s*[:=]?\\s*$DEC(?:\\s*(?:-|à|a|to)\\s*$DEC)?", opt)
    private val rpeAfter = Regex("(?<![\\d.,])$DEC\\s*rpe\\b", opt)
    private val weightRe = Regex("(?<![\\d.,])$DEC\\s*(kg|kgs|kilos?|lbs?|livres?)\\b", opt)
    private val weightKeyword = Regex("\\b(?:charge|poids|weight|load)\\s*[:=]?\\s*$DEC\\b(?!\\s*(?:x|reps?|séries?|sets?))", opt)

    // 3x12/10/8 ou 3 x 12, 10, 8
    private val setsRepsList = Regex("(?<![\\d.,])$NUM\\s*x\\s*(\\d{1,3}(?:\\s*[/,]\\s*\\d{1,3}){1,9})\\s*$REPS_WORD?", opt)
    // 3x10 / 3 x 8-12 / 3x10-12
    private val setsXReps = Regex("(?<![\\d.,])$NUM\\s*x\\s*$NUM(?:\\s*-\\s*$NUM)?\\s*$REPS_WORD?(?![\\d])", opt)
    // 3 séries de 10 répétitions / 3 sets of 8-12 reps / 3 séries à 10 reps
    private val setsWordReps = Regex(
        "(?<![\\d.,])$NUM\\s*(?:séries?|series?|sets?|serie)\\s*(?:de|of|x|à|a|:|-)?\\s*$NUM(?:\\s*-\\s*$NUM)?\\s*$REPS_WORD", opt)
    private val setsOnly = Regex("(?<![\\d.,])$NUM\\s*(?:séries?|series?|sets?|serie)\\b", opt)
    private val setsKeyword = Regex("\\b(?:séries?|series|sets?)\\s*[:=]\\s*$NUM\\b", opt)
    private val repsList = Regex("(?<![\\d.,x])(\\d{1,3}(?:\\s*/\\s*\\d{1,3}){1,9})\\s*$REPS_WORD?", opt)
    private val repsOnly = Regex("(?<![\\d.,])$NUM(?:\\s*-\\s*$NUM)?\\s*$REPS_WORD", opt)
    private val repsKeyword = Regex("\\b(?:reps?|répétitions?|repetitions?)\\s*[:=]?\\s*$NUM(?:\\s*-\\s*$NUM)?", opt)
    private val amrapRe = Regex("\\b(amrap|max reps|échec|echec|to failure|failure)\\b", opt)

    private val stopWords = setOf("de", "of", "a", "à", "at", "x", "et", "and", "avec", "with", "puis", "then", "au", "@", "-", "par", "per")

    fun parse(input: String): Result {
        var s = " " + TextNormalizer.normalizeLine(input)
            .replace(Regex("(\\d)\\s*(?:à|a|to)\\s*(\\d)"), "$1-$2") // « 8 à 12 » -> « 8-12 »
            .replace(Regex("(?i)(\\d)\\s*[xX*]\\s*(\\d)"), "$1x$2") + " "
        val issues = mutableListOf<ImportIssue>()
        val notes = mutableListOf<String>()

        fun cut(m: MatchResult) {
            s = s.replaceRange(m.range, " ".repeat(m.value.length))
        }

        var tempo: String? = null
        tempoRe.find(s)?.let { tempo = it.groupValues[1].replace(" ", ""); cut(it) }

        percentRe.find(s)?.let {
            notes += "${it.groupValues[1]} % du max"
            cut(it)
        }

        // --- Repos
        var rest: Int? = null
        (restKeywordBefore.find(s) ?: restKeywordAfter.find(s))?.let { m ->
            val d = parseDuration(m.groupValues[1])
            if (d.ambiguous) {
                issues += ImportIssue("Durée de repos ambiguë : unité non précisée", m.value.trim())
            } else rest = d.seconds
            if (m.groupValues.size > 2 && m.groupValues[2].isNotBlank()) {
                notes += "Repos : ${m.value.trim()}"
            }
            cut(m)
        }

        // --- RIR
        var rirMin: Int? = null
        var rirMax: Int? = null
        (rirBefore.find(s) ?: rirAfter.find(s))?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues.getOrNull(2)?.toIntOrNull()
            rirMin = minOf(a, b ?: a); rirMax = if (b != null) maxOf(a, b) else a
            if (rirMax!! > 10) { issues += ImportIssue("RIR inhabituel", m.value.trim()) }
            cut(m)
        }

        // --- RPE (conservé tel quel)
        var rpe: Double? = null
        (rpeBefore.find(s) ?: rpeAfter.find(s))?.let { m ->
            rpe = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val second = m.groupValues.getOrNull(2)
            if (!second.isNullOrBlank()) notes += "RPE ${m.groupValues[1]}-$second"
            if (rpe != null && (rpe!! < 1 || rpe!! > 10)) issues += ImportIssue("RPE hors de l'échelle 1-10", m.value.trim())
            cut(m)
        }

        // --- Charge
        var weight: Double? = null
        var unit: WeightUnit? = null
        weightRe.find(s)?.let { m ->
            weight = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            unit = if (m.groupValues[2].lowercase().startsWith("l")) WeightUnit.LB else WeightUnit.KG
            cut(m)
        } ?: weightKeyword.find(s)?.let { m ->
            weight = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            issues += ImportIssue("Unité de charge non précisée (kg supposé à vérifier)", m.value.trim())
            unit = WeightUnit.KG
            cut(m)
        }

        // --- Séries / répétitions
        var setCount: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var perSetReps: List<Int>? = null

        setsRepsList.findAll(s).firstOrNull { m ->
            // Liste séparée par des virgules acceptée seulement si elle correspond exactement au nombre de séries
            // (« 3x10, 90s » ne doit pas devenir deux séries).
            val list = m.groupValues[2].split('/', ',')
            m.groupValues[2].contains('/') || list.size == m.groupValues[1].toInt()
        }?.let { m ->
            val n = m.groupValues[1].toInt()
            val list = m.groupValues[2].split('/', ',').map { it.trim().toInt() }
            setCount = n
            if (list.size == n) perSetReps = list
            else {
                // Incohérence : on garde ce qui est écrit, sans deviner.
                perSetReps = list.take(n)
                issues += ImportIssue("Nombre de séries ($n) différent du nombre de répétitions indiquées (${list.size})", m.value.trim())
            }
            cut(m)
        } ?: setsXReps.find(s)?.let { m ->
            setCount = m.groupValues[1].toInt()
            val a = m.groupValues[2].toInt()
            val b = m.groupValues[3].toIntOrNull()
            repsMin = minOf(a, b ?: a); repsMax = if (b != null) maxOf(a, b) else a
            cut(m)
        } ?: setsWordReps.find(s)?.let { m ->
            setCount = m.groupValues[1].toInt()
            val a = m.groupValues[2].toInt()
            val b = m.groupValues[3].toIntOrNull()
            repsMin = minOf(a, b ?: a); repsMax = if (b != null) maxOf(a, b) else a
            cut(m)
        }

        if (setCount == null) {
            (setsOnly.find(s) ?: setsKeyword.find(s))?.let { m -> setCount = m.groupValues[1].toInt(); cut(m) }
        }
        if (repsMin == null && perSetReps == null) {
            repsList.find(s)?.let { m ->
                val list = m.groupValues[1].split('/').map { it.trim().toInt() }
                if (setCount == null || setCount == list.size) {
                    perSetReps = list
                    if (setCount == null) setCount = list.size
                    cut(m)
                }
            }
        }
        if (repsMin == null && perSetReps == null) {
            (repsOnly.find(s) ?: repsKeyword.find(s))?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toIntOrNull()
                repsMin = minOf(a, b ?: a); repsMax = if (b != null) maxOf(a, b) else a
                cut(m)
            }
        }
        amrapRe.find(s)?.let { notes += it.value.uppercase().let { v -> if (v.startsWith("AMRAP")) "AMRAP" else "Jusqu'à l'échec" }; cut(it) }

        // Durée seule sans mot-clé (ex. « 3x10, 90s ») : interprétée comme repos.
        if (rest == null) {
            bareDuration.find(s)?.let { m -> parseDuration(m.value).let { d -> if (!d.ambiguous) rest = d.seconds }; cut(m) }
        }

        if (setCount != null && (setCount!! <= 0 || setCount!! > 20)) {
            issues += ImportIssue("Nombre de séries inhabituel ($setCount)")
            if (setCount!! <= 0) setCount = null
        }
        if (repsMax != null && repsMax!! > 100) issues += ImportIssue("Nombre de répétitions inhabituel ($repsMax)")

        val leftover = cleanLeftover(s)
        if (leftover.isNotEmpty()) {
            if (leftover.any { it.isDigit() }) {
                issues += ImportIssue("Texte non interprété", leftover)
            }
            notes += leftover
        }

        val common = SetTarget(
            repsMin = repsMin, repsMax = repsMax, rirMin = rirMin, rirMax = rirMax, rpe = rpe,
            weight = weight, weightUnit = unit, restSeconds = rest,
        )
        val perSet = perSetReps?.map { r -> common.copy(repsMin = r, repsMax = r) }
        return Result(setCount, perSet, common, tempo, notes.joinToString(" · ").ifBlank { "" }, issues)
    }

    private fun cleanLeftover(s: String): String {
        val words = s.replace(Regex("[,;|@()\\[\\]{}]"), " ")
            .split(Regex("\\s+")).map { it.trim('-', '.', ':', '/', '+', '=', '*') }
            .filter { it.isNotBlank() && it.lowercase() !in stopWords }
        return words.joinToString(" ")
    }

    data class Duration(val seconds: Int?, val ambiguous: Boolean)

    /** Convertit « 90s », « 2 min », « 1min30 », « 1'30 », « 2:00 », « 1h15 » en secondes. */
    fun parseDuration(raw: String): Duration {
        val t = raw.lowercase().replace(" ", "")
        Regex("^(\\d{1,2})h(\\d{0,2})$").find(t)?.let {
            return Duration(it.groupValues[1].toInt() * 3600 + (it.groupValues[2].toIntOrNull() ?: 0) * 60, false)
        }
        Regex("^(\\d{1,2}):(\\d{2})$").find(t)?.let {
            return Duration(it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt(), false)
        }
        Regex("^(\\d{1,2})'(\\d{0,2})(?:''|\")?$").find(t)?.let {
            return Duration(it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0), false)
        }
        Regex("^(\\d{1,3})(?:minutes?|min|mn|m)(\\d{0,2})(?:s|sec)?$").find(t)?.let {
            return Duration(it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0), false)
        }
        Regex("^(\\d{1,4})(?:s|sec|secs|secondes?|seconds?)$").find(t)?.let {
            return Duration(it.groupValues[1].toInt(), false)
        }
        // Nombre sans unité : on ne devine pas.
        return Duration(null, true)
    }

    /** Durée de séance (« 1h15 », « 75 min », « 1h ») en minutes, ou null. */
    fun parseSessionDurationMinutes(raw: String): Int? {
        val t = raw.lowercase().replace(" ", "")
        Regex("(\\d{1,2})h(\\d{1,2})?").find(t)?.let {
            return it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0)
        }
        Regex("(\\d{1,3})(?:minutes?|min|mn)").find(t)?.let { return it.groupValues[1].toInt() }
        return null
    }
}
