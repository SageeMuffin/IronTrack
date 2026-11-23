package com.irontrack.app.importer

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contenu partagé vers IronTrack depuis une autre application. */
sealed interface SharedImport {
    data class Text(val text: String) : SharedImport
    data class Document(val uri: Uri, val mime: String) : SharedImport
}

object SharedImportHolder {
    private val _pending = MutableStateFlow<SharedImport?>(null)
    val pending: StateFlow<SharedImport?> = _pending.asStateFlow()
    fun post(item: SharedImport) { _pending.value = item }
    fun consume(): SharedImport? = _pending.value.also { _pending.value = null }
}
