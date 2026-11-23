package com.irontrack.core.catalog

import com.irontrack.core.util.TextNormalizer
import kotlin.math.max
import kotlin.math.min

/** Résultat de la recherche d'un exercice dans une bibliothèque. */
data class ExerciseMatch(
    /** Meilleure correspondance jugée fiable, ou null si ambiguë / inconnue. */
    val best: Candidate?,
    /** Propositions classées (même si [best] est null) pour laisser l'utilisateur choisir. */
    val candidates: List<Candidate>,
) {
    data class Candidate(val id: String, val displayName: String, val score: Double)

    val isAmbiguous: Boolean get() = best == null && candidates.isNotEmpty()
}

/**
 * Rapproche un nom d'exercice libre (français ou anglais, éventuellement abîmé par l'OCR)
 * d'une bibliothèque d'exercices. Ne choisit automatiquement que lorsque la correspondance
 * est exacte ou très nettement la meilleure ; sinon il propose des candidats.
 */
class ExerciseMatcher(entries: List<Entry>) {

    /** Une entrée de bibliothèque avec tous ses alias. */
    data class Entry(val id: String, val displayName: String, val aliases: List<String>)

    private val index: List<Pair<Entry, List<String>>> =
        entries.map { e -> e to (e.aliases + e.displayName).map(TextNormalizer::normalizeName).filter { it.isNotBlank() }.distinct() }

    fun match(rawName: String, maxCandidates: Int = 4): ExerciseMatch {
        val q = cleanQuery(rawName)
        if (q.isBlank()) return ExerciseMatch(null, emptyList())

        // 1. Correspondance exacte sur un alias -> fiable.
        index.firstOrNull { (_, al) -> q in al }?.let { (e, _) ->
            return ExerciseMatch(ExerciseMatch.Candidate(e.id, e.displayName, 1.0), listOf(ExerciseMatch.Candidate(e.id, e.displayName, 1.0)))
        }

        // 2. Score approché.
        val scored = index.map { (e, al) ->
            ExerciseMatch.Candidate(e.id, e.displayName, al.maxOf { similarity(q, it) })
        }.filter { it.score >= 0.45 }
            .sortedByDescending { it.score }
            .take(maxCandidates)

        val top = scored.firstOrNull()
        val second = scored.getOrNull(1)
        val reliable = top != null && top.score >= 0.88 && (second == null || top.score - second.score >= 0.08)
        return ExerciseMatch(if (reliable) top else null, scored)
    }

    companion object {
        fun fromCatalog(): ExerciseMatcher = ExerciseMatcher(
            ExerciseCatalog.all.map { Entry(it.key, it.nameFr, ExerciseCatalog.aliases(it)) }
        )

        private val noise = Regex("\\b(the|le|la|les|du|de|des|a|au|aux|avec|with|on|sur)\\b")

        internal fun cleanQuery(raw: String): String =
            TextNormalizer.normalizeName(raw)

        private fun tokens(s: String): Set<String> =
            noise.replace(s, " ").split(' ').filter { it.length > 1 }.map { stem(it) }.toSet()

        private fun stem(t: String): String = when {
            t.length > 4 && t.endsWith("es") -> t.dropLast(2)
            t.length > 3 && t.endsWith("s") -> t.dropLast(1)
            else -> t
        }

        /** Similarité combinant les mots en commun et la distance d'édition (robuste aux fautes d'OCR). */
        fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            val ta = tokens(a)
            val tb = tokens(b)
            val tokenScore = if (ta.isEmpty() || tb.isEmpty()) 0.0 else {
                // appariement souple des mots (fautes de frappe tolérées)
                var matched = 0.0
                for (x in ta) {
                    val bestTok = tb.maxOf { y -> ratio(x, y) }
                    if (bestTok >= 0.8) matched += bestTok
                }
                matched / max(ta.size, tb.size)
            }
            val editScore = ratio(a.replace(" ", ""), b.replace(" ", ""))
            return max(tokenScore, editScore * 0.97)
        }

        fun ratio(a: String, b: String): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            val d = levenshtein(a, b)
            return 1.0 - d.toDouble() / max(a.length, b.length)
        }

        fun levenshtein(a: String, b: String): Int {
            val prev = IntArray(b.length + 1) { it }
            val cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                System.arraycopy(cur, 0, prev, 0, cur.size)
            }
            return prev[b.length]
        }
    }
}
