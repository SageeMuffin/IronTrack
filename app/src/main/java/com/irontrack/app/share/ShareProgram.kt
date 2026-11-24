package com.irontrack.app.share

import android.content.Context
import android.content.Intent
import com.irontrack.app.AppContainer
import com.irontrack.app.R

/** Ouvre le menu de partage Android avec le lien du programme (WhatsApp, SMS, e-mail…). */
suspend fun shareProgram(context: Context, container: AppContainer, programId: Long) {
    val (name, link) = container.sharing.shareLink(programId) ?: return
    val text = context.getString(R.string.share_message, name, link)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject, name))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_program)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
