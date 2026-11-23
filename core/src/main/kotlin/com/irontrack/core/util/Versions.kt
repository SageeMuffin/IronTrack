package com.irontrack.core.util

/**
 * Version sémantique « MAJEUR.MINEUR.CORRECTIF ». Le versionCode Android est dérivé
 * numériquement (majeur * 10000 + mineur * 100 + correctif) : la comparaison
 * ne repose jamais sur des chaînes de caractères.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    val code: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: SemVer): Int = code.compareTo(other.code)
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val re = Regex("^v?(\\d{1,3})\\.(\\d{1,2})(?:\\.(\\d{1,2}))?$")

        /** Analyse « v1.2.3 » ou « 1.2 ». Renvoie null pour un format non reconnu (ex. « v1.0.0-beta »). */
        fun parse(tag: String): SemVer? {
            val m = re.find(tag.trim()) ?: return null
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0)
        }

        fun fromCode(code: Int): SemVer = SemVer(code / 10_000, (code / 100) % 100, code % 100)
    }
}
