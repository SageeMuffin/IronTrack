package com.irontrack.app.importer

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contenu partagé vers IronTrack depuis une autre application. */
sealed interface SharedImport {
    data class Text(val text: String) : SharedImport
    data class Document(val uri: Uri, val mime: String) : SharedImport
    /** Lien de partage d'un programme IronTrack. */
    data class ProgramLink(val link: String) : SharedImport
}

object SharedImportHolder {
    private val _pending = MutableStateFlow<SharedImport?>(null)
    val pending: StateFlow<SharedImport?> = _pending.asStateFlow()
    fun post(item: SharedImport) { _pending.value = item }
    fun consume(): SharedImport? = _pending.value.also { _pending.value = null }
    /** Consomme uniquement un contenu destiné à l'écran d'import (texte ou document). */
    fun consumeDocument(): SharedImport? = _pending.value?.takeIf { it !is SharedImport.ProgramLink }?.also { _pending.value = null }
    fun consumeProgramLink(): SharedImport.ProgramLink? = (_pending.value as? SharedImport.ProgramLink)?.also { _pending.value = null }
}
