package com.irontrack.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irontrack.app.R
import com.irontrack.app.data.db.ProgramFull
import com.irontrack.app.data.db.SessionFull
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.components.WeekPill

/** Séances qu'on peut lancer à tout moment : toutes les séances actives du programme, semaine A puis B. */
fun startableSessions(program: ProgramFull?): List<SessionFull> =
    program?.sessions.orEmpty()
        .filter { it.session.enabled && (program!!.program.abEnabled || it.session.week == "A") }
        .sortedWith(compareBy({ it.session.week }, { it.session.dayOfWeek ?: 8 }, { it.session.position }, { it.session.id }))

/** Vrai si le programme n'a aucune séance attribuée à un jour : programme « libre ». */
fun isFreeProgram(program: ProgramFull?): Boolean {
    val list = startableSessions(program)
    return list.isNotEmpty() && list.all { it.session.dayOfWeek == null }
}

/** Ligne descriptive : jour (ou « Sans jour fixe »), nombre d'exercices, durée estimée. */
@Composable
fun sessionSubtitle(s: SessionFull): String {
    val days = stringArrayResource(R.array.weekdays)
    return buildList {
        add(s.session.dayOfWeek?.let { days[it - 1] } ?: stringResource(R.string.free_session_no_day))
        if (s.exercises.isNotEmpty()) add(pluralStringResource(R.plurals.exercises_count, s.exercises.size, s.exercises.size))
        s.session.estimatedDurationMinutes?.let { add(Fmt.minutes(it)) }
    }.joinToString(" · ")
}

/** Carte d'une séance avec un bouton « Commencer ». */
@Composable
fun StartSessionCard(s: SessionFull, showWeek: Boolean, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(s.session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sessionSubtitle(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showWeek) WeekPill(s.session.week, Modifier.padding(top = 4.dp))
            }
            FilledTonalButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.action_start))
            }
        }
    }
}

/** Choix d'une séance du programme à lancer maintenant, quel que soit le jour prévu. */
@Composable
fun SessionPickerDialog(program: ProgramFull, onPick: (SessionFull) -> Unit, onDismiss: () -> Unit) {
    val sessions = startableSessions(program)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_session_title)) },
        text = {
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.pick_session_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.session.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.session.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(sessionSubtitle(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (program.program.abEnabled) WeekPill(s.session.week)
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_start), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
