package com.irontrack.core.util

import java.text.Normalizer

object TextNormalizer {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun stripAccents(s: String): String =
        diacritics.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")

    /** Normalise un nom d'exercice pour la comparaison : minuscules, sans accents ni ponctuation. */
    fun normalizeName(s: String): String =
        stripAccents(s.lowercase())
            .replace('’', ' ').replace('\'', ' ')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Normalise une ligne de programme : symboles de multiplication, tirets typographiques,
     * espaces insécables. Ne modifie ni la casse ni les accents (on garde le texte d'origine).
     */
    fun normalizeLine(s: String): String =
        s.replace(' ', ' ').replace(' ', ' ').replace('\t', '\t')
            .replace('×', 'x').replace('✕', 'x').replace('✖', 'x')
            .replace('–', '-').replace('—', '-').replace('−', '-')
            .replace('’', '\'')
            .trimEnd()
}
