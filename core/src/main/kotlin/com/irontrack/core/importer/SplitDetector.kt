package com.irontrack.core.importer

import com.irontrack.core.util.TextNormalizer

object SplitDetector {
    fun detect(days: List<ParsedDay>): SplitType {
        if (days.isEmpty()) return SplitType.CUSTOM
        val titles = days.map { TextNormalizer.normalizeName(it.title) }
        fun count(vararg keys: String) = titles.count { t -> keys.any { k -> Regex("\\b$k\\b").containsMatchIn(t) } }

        val push = count("push", "poussee")
        val pull = count("pull", "tirage")
        val legs = count("legs", "leg", "jambes", "leg day")
        val upper = count("upper", "haut", "haut du corps", "torse")
        val lower = count("lower", "bas", "bas du corps")
        val full = count("full body", "fullbody", "full", "corps entier")

        return when {
            full == days.size -> SplitType.FULL_BODY
            push > 0 && pull > 0 && legs > 0 && upper == 0 && lower == 0 ->
                if (push >= 2 && pull >= 2 && legs >= 2) SplitType.PPL_X2 else SplitType.PPL
            upper > 0 && lower > 0 && push == 0 && pull == 0 && upper + lower == days.size -> SplitType.UPPER_LOWER
            days.size >= 3 && titles.all { t ->
                t.split(' ').any { it in ProgramTextParserMuscles.words }
            } && push == 0 && pull == 0 && upper == 0 -> SplitType.BRO_SPLIT
            else -> SplitType.CUSTOM
        }
    }
}

internal object ProgramTextParserMuscles {
    val words = setOf("pecs", "pectoraux", "dos", "epaules", "bras", "biceps", "triceps", "jambes", "quadriceps",
        "ischios", "fessiers", "mollets", "abdos", "chest", "back", "shoulders", "arms", "legs", "glutes", "hamstrings", "calves")
}
