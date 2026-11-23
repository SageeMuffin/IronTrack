package com.irontrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irontrack.app.R
import com.irontrack.app.data.repo.EntryStatus
import com.irontrack.app.ui.theme.MissedRed
import com.irontrack.app.ui.theme.SuccessGreen
import com.irontrack.app.ui.theme.WarningAmber
import com.irontrack.app.ui.theme.WeekAColor
import com.irontrack.app.ui.theme.WeekBColor

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowEmpty: Boolean = false,
    singleLine: Boolean = true,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = singleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = allowEmpty || value.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun WeekPill(week: String?, modifier: Modifier = Modifier) {
    when (week) {
        "A" -> Pill(stringResource(R.string.week_a), WeekAColor, modifier)
        "B" -> Pill(stringResource(R.string.week_b), WeekBColor, modifier)
        else -> {}
    }
}

fun statusColor(s: EntryStatus): Color = when (s) {
    EntryStatus.COMPLETED -> SuccessGreen
    EntryStatus.PARTIAL -> WarningAmber
    EntryStatus.NOT_DONE, EntryStatus.MISSED -> MissedRed
    EntryStatus.IN_PROGRESS -> WarningAmber
    EntryStatus.TODAY -> Color(0xFFFF6B2C)
    EntryStatus.UPCOMING -> Color(0xFF8E9099)
}

@Composable
fun statusLabel(s: EntryStatus): String = stringResource(
    when (s) {
        EntryStatus.COMPLETED -> R.string.status_completed
        EntryStatus.PARTIAL -> R.string.status_partial
        EntryStatus.NOT_DONE -> R.string.status_not_done
        EntryStatus.MISSED -> R.string.status_missed
        EntryStatus.IN_PROGRESS -> R.string.status_in_progress
        EntryStatus.TODAY -> R.string.status_today
        EntryStatus.UPCOMING -> R.string.status_upcoming
    }
)

@Composable
fun StatusPill(s: EntryStatus, modifier: Modifier = Modifier) = Pill(statusLabel(s), statusColor(s), modifier)

fun workoutStatusToEntry(status: String): EntryStatus = when (status) {
    "COMPLETED" -> EntryStatus.COMPLETED
    "PARTIAL" -> EntryStatus.PARTIAL
    "NOT_DONE" -> EntryStatus.NOT_DONE
    else -> EntryStatus.IN_PROGRESS
}

/** Cellule de valeur cliquable (ouvre une molette). Affiche « — » si non renseignée. */
@Composable
fun ValueCell(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = NONE,
    highlighted: Boolean = false,
) {
    Box(
        modifier
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun SettingRow(title: String, value: String?, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
