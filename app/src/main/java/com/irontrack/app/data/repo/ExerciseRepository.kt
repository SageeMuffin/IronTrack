package com.irontrack.app.data.repo

import com.irontrack.app.data.db.ExerciseDao
import com.irontrack.app.data.db.ExerciseEntity
import com.irontrack.core.catalog.ExerciseCatalog
import com.irontrack.core.catalog.ExerciseMatcher
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {

    val activeExercises: Flow<List<ExerciseEntity>> = dao.observeActive()
    val allExercises: Flow<List<ExerciseEntity>> = dao.observeAll()

    fun observe(id: Long) = dao.observe(id)
    suspend fun byId(id: Long) = dao.byId(id)

    /**
     * Ajoute les exercices de la bibliothèque intégrée qui manquent (identifiés par leur clé stable).
     * Les exercices existants, éventuellement renommés par l'utilisateur, ne sont jamais modifiés.
     */
    suspend fun seedCatalog() {
        dao.insertIgnore(ExerciseCatalog.all.map {
            ExerciseEntity(
                name = it.nameFr,
                muscleGroup = it.muscleGroup,
                equipment = it.equipment,
                catalogKey = it.key,
                isCustom = false,
            )
        })
    }

    suspend fun create(name: String, description: String?, muscle: String?, equipment: String?, notes: String?): Long =
        dao.insert(
            ExerciseEntity(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                muscleGroup = muscle?.trim()?.ifBlank { null },
                equipment = equipment?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                isCustom = true,
            )
        )

    /** Renommer ne touche que la fiche : l'historique référence l'identifiant, pas le nom. */
    suspend fun update(e: ExerciseEntity) = dao.update(e)

    /** Supprime si jamais utilisé, sinon archive (l'historique reste intact). Renvoie true si supprimé. */
    suspend fun deleteOrArchive(e: ExerciseEntity): Boolean =
        if (dao.usageCount(e.id) == 0) { dao.delete(e); true } else { dao.update(e.copy(archived = true)); false }

    /** Construit un comparateur sur la bibliothèque réelle de l'utilisateur (noms personnalisés inclus). */
    suspend fun matcher(): ExerciseMatcher {
        val list = dao.all().filter { !it.archived }
        return ExerciseMatcher(list.map { e ->
            val cat = e.catalogKey?.let { ExerciseCatalog.byKey(it) }
            val aliases = buildList {
                add(e.name)
                if (cat != null) addAll(ExerciseCatalog.aliases(cat))
            }
            ExerciseMatcher.Entry(e.id.toString(), e.name, aliases)
        })
    }
}
