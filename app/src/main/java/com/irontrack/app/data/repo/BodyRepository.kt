package com.irontrack.app.data.repo

import com.irontrack.app.data.db.BodyDao
import com.irontrack.app.data.db.BodyMeasurementEntity
import com.irontrack.core.body.BodyMetric
import com.irontrack.core.body.BodyStats
import com.irontrack.core.body.MeasurePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Mesures corporelles : poids de corps, masse grasse et tours (cm). */
class BodyRepository(private val dao: BodyDao) {
    /** Toutes les mesures, regroupées par type connu (les clés inconnues sont ignorées). */
    val all: Flow<Map<BodyMetric, List<BodyMeasurementEntity>>> = dao.observeAll().map { list ->
        list.mapNotNull { m -> BodyMetric.fromKey(m.metric)?.let { it to m } }
            .groupBy({ it.first }, { it.second })
    }

    fun observe(metric: BodyMetric): Flow<List<BodyMeasurementEntity>> = dao.observeMetric(metric.key)

    /** Enregistre une valeur (en kg, % ou cm) ; remplace celle du même jour s'il y en a une. */
    suspend fun save(metric: BodyMetric, date: LocalDate, value: Double) {
        dao.upsert(BodyMeasurementEntity(metric = metric.key, epochDay = date.toEpochDay(), value = BodyStats.clamp(metric, value)))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        fun points(list: List<BodyMeasurementEntity>) = list.map { MeasurePoint(it.epochDay, it.value) }
    }
}
