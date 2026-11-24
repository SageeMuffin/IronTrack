package com.irontrack.app.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irontrack.app.R
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.appContainer
import com.irontrack.core.share.ProgramShareCodec
import kotlinx.coroutines.launch

/** Confirmation avant d'importer un programme reçu par lien. Rien n'est enregistré sans validation. */
@Composable
fun ProgramLinkDialog(link: String, onClose: () -> Unit, onImported: (Long) -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val program = remember(link) { runCatching { ProgramShareCodec.decode(link) }.getOrNull() }
    var activate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    if (program == null || failed) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.share_invalid_title)) },
            text = { Text(stringResource(if (failed) R.string.share_import_error else R.string.share_invalid_message)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(R.string.share_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    pluralStringResource(R.plurals.sessions_count, program.sessions.size, program.sessions.size) + " · " +
                        pluralStringResource(R.plurals.exercises_count, program.exerciseCount, program.exerciseCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (program.abEnabled) Text(stringResource(R.string.program_ab), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.share_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clickable { activate = !activate }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = activate, onCheckedChange = { activate = it })
                    Text(stringResource(R.string.import_activate))
                }
                if (saving) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    runCatching { container.sharing.import(program, activate) }
                        .onSuccess { id ->
                            if (activate) ReminderScheduler.reschedule(context.applicationContext)
                            onImported(id)
                        }
                        .onFailure { saving = false; failed = true }
                }
            }) { Text(stringResource(R.string.share_import_button)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
    )
}
