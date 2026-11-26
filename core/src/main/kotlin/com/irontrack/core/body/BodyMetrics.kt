package com.irontrack.core.body

/** Unité de stockage d'une mesure corporelle (le poids est toujours stocké en kg). */
enum class MeasureUnit { KG, PERCENT, CM }

/**
 * Mesures corporelles suivies. [key] est la valeur stockée en base et dans les sauvegardes :
 * elle ne doit jamais changer. [min]/[max]/[default] bornent la saisie par molettes.
 */
enum class BodyMetric(val key: String, val unit: MeasureUnit, val min: Int, val max: Int, val default: Double) {
    WEIGHT("weight", MeasureUnit.KG, 30, 250, 75.0),
    BODY_FAT("body_fat", MeasureUnit.PERCENT, 3, 60, 18.0),
    NECK("neck", MeasureUnit.CM, 20, 70, 38.0),
    SHOULDERS("shoulders", MeasureUnit.CM, 70, 180, 115.0),
    CHEST("chest", MeasureUnit.CM, 60, 170, 100.0),
    WAIST("waist", MeasureUnit.CM, 45, 170, 82.0),
    HIPS("hips", MeasureUnit.CM, 60, 170, 98.0),
    ARM("arm", MeasureUnit.CM, 15, 70, 35.0),
    FOREARM("forearm", MeasureUnit.CM, 15, 50, 29.0),
    THIGH("thigh", MeasureUnit.CM, 30, 100, 57.0),
    CALF("calf", MeasureUnit.CM, 20, 60, 38.0);

    companion object {
        fun fromKey(key: String): BodyMetric? = entries.firstOrNull { it.key == key }
    }
}

/** Une valeur mesurée un jour donné. */
data class MeasurePoint(val epochDay: Long, val value: Double)

/** Résumé d'une série de mesures : dernière valeur et évolutions. */
data class MetricSummary(
    val latest: MeasurePoint,
    /** Écart avec la mesure précédente ; null s'il n'y en a qu'une. */
    val changeSincePrevious: Double?,
    /** Écart avec la toute première mesure ; null s'il n'y en a qu'une. */
    val changeSinceFirst: Double?,
    val first: MeasurePoint,
    val count: Int,
)

object BodyStats {
    /** Trie par date ; si plusieurs valeurs le même jour, garde la dernière saisie (ordre d'entrée). */
    fun normalize(points: List<MeasurePoint>): List<MeasurePoint> =
        points.withIndex().groupBy { it.value.epochDay }
            .map { (_, list) -> list.maxBy { it.index }.value }
            .sortedBy { it.epochDay }

    fun summary(points: List<MeasurePoint>): MetricSummary? {
        val list = normalize(points)
        if (list.isEmpty()) return null
        val latest = list.last()
        val prev = list.getOrNull(list.size - 2)
        return MetricSummary(
            latest = latest,
            changeSincePrevious = prev?.let { round1(latest.value - it.value) },
            changeSinceFirst = if (list.size > 1) round1(latest.value - list.first().value) else null,
            first = list.first(),
            count = list.size,
        )
    }

    /** Valeur bornée à la plage de saisie, arrondie au dixième. */
    fun clamp(metric: BodyMetric, v: Double): Double = round1(v.coerceIn(metric.min.toDouble(), metric.max + 0.9))

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
