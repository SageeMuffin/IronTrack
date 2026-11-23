package com.irontrack.app.ui.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.irontrack.app.R
import com.irontrack.app.importer.EditableDay
import com.irontrack.app.importer.EditableExercise
import com.irontrack.app.importer.ExerciseChoice
import com.irontrack.app.importer.SharedImport
import com.irontrack.app.importer.SharedImportHolder
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.DurationSheet
import com.irontrack.app.ui.components.Pill
import com.irontrack.app.ui.components.RepsSheet
import com.irontrack.app.ui.components.RestSheet
import com.irontrack.app.ui.components.RirRangeSheet
import com.irontrack.app.ui.components.RpeSheet
import com.irontrack.app.ui.components.TextInputDialog
import com.irontrack.app.ui.components.ValueCell
import com.irontrack.app.ui.components.WeightSheet
import com.irontrack.app.ui.exercises.ExercisePickerDialog
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.targetSummary
import com.irontrack.app.ui.theme.WarningAmber
import com.irontrack.app.ui.weightText
import com.irontrack.core.importer.SetTarget
import com.irontrack.core.importer.WeightUnit
import java.io.File

private val documentMimeTypes = arrayOf(
    "text/plain", "application/pdf", "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif",
)

@Composable
fun ImportScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm = appViewModel { ImportViewModel(it, app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.imported_program_default_name)

    // Contenu partagé depuis une autre application.
    LaunchedEffect(Unit) {
        when (val s = SharedImportHolder.consume()) {
            is SharedImport.Text -> vm.analyzeText(s.text, defaultName)
            is SharedImport.Document -> vm.analyzeFiles(listOf(s.uri to s.mime), defaultName)
            null -> {}
        }
    }
    LaunchedEffect(state) {
        (state as? ImportUiState.Saved)?.let {
            nav.navigate(Routes.program(it.programId)) { popUpTo(Routes.IMPORT) { inclusive = true } }
        }
    }

    AppScaffold(
        title = stringResource(R.string.import_program),
        onBack = { if (state is ImportUiState.Preview || state is ImportUiState.Error) vm.reset() else nav.popBackStack() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ImportUiState.Input -> InputStep(
                    onText = { vm.analyzeText(it, defaultName) },
                    onFiles = { vm.analyzeFiles(it, defaultName) },
                )
                is ImportUiState.Analyzing -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.import_analyzing), style = MaterialTheme.typography.titleMedium)
                    s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                is ImportUiState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                    Button(onClick = vm::reset) { Text(stringResource(R.string.action_retry)) }
                }
                is ImportUiState.Preview -> PreviewStep(s, vm)
                is ImportUiState.Saved -> {}
            }
        }
    }
}

// ============================================================================ Saisie

@Composable
private fun InputStep(onText: (String) -> Unit, onFiles: (List<Pair<Uri, String?>>) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFiles(uris.map { it to context.contentResolver.getType(it) })
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onFiles(uris.map { it to context.contentResolver.getType(it) })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) onFiles(listOf(u to "image/jpeg"))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        MethodCard(Icons.Filled.Description, stringResource(R.string.import_method_document), stringResource(R.string.import_method_document_hint)) {
            Button(onClick = { docLauncher.launch(documentMimeTypes) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_file)) }
        }
        MethodCard(Icons.Filled.Image, stringResource(R.string.import_method_image), stringResource(R.string.import_method_image_hint)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_from_gallery))
                }
                OutlinedButton(onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "programme_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    photoUri = uri
                    camera.launch(uri)
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.import_take_photo))
                }
            }
        }
        MethodCard(Icons.Filled.ContentPaste, stringResource(R.string.import_method_paste), stringResource(R.string.import_method_paste_hint)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.import_paste_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onText(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_analyze_program))
            }
        }
    }
}

@Composable
private fun MethodCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

// ============================================================================ Aperçu

private sealed interface ImportEdit {
    val day: Long
    val ex: Long
    data class Reps(override val day: Long, override val ex: Long, val i: Int, val t: SetTarget) : ImportEdit
    data class Rir(override val day: Long, override val ex: Long, val i: Int, val t: SetTarget) : ImportEdit
    data class Rpe(override val day: Long, override val ex: Long, val i: Int, val t: SetTarget) : ImportEdit
    data class Weight(override val day: Long, override val ex: Long, val i: Int, val t: SetTarget) : ImportEdit
    data class Rest(override val day: Long, override val ex: Long, val i: Int, val t: SetTarget) : ImportEdit
}

@Composable
private fun PreviewStep(s: ImportUiState.Preview, vm: ImportViewModel) {
    val d = s.draft
    var edit by remember { mutableStateOf<ImportEdit?>(null) }
    var picking by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var renamingDay by remember { mutableStateOf<EditableDay?>(null) }
    var durationDay by remember { mutableStateOf<EditableDay?>(null) }
    var showSource by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SummaryCard(s, vm) }
        if (s.warnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f))) {
                    Column(Modifier.padding(12.dp)) { s.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
        items(d.days, key = { it.key }) { day ->
            DayCard(
                day = day, ab = d.abEnabled, vm = vm,
                onEdit = { edit = it },
                onPick = { exKey -> picking = day.key to exKey },
                onRename = { renamingDay = day },
                onDuration = { durationDay = day },
            )
        }
        if (d.restDays.isNotEmpty()) {
            item {
                val names = stringArrayResource(R.array.weekdays)
                Text(stringResource(R.string.import_rest_days, d.restDays.distinct().sorted().joinToString(", ") { names[it - 1] }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (d.parsed.unrecognized.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.import_unrecognized_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.import_unrecognized_hint), style = MaterialTheme.typography.bodySmall)
                        d.parsed.unrecognized.forEach { l ->
                            Text("« ${l.text.trim()} »" + (l.page?.let { " (p. $it)" } ?: ""), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = { showSource = !showSource }) {
                Text(stringResource(if (showSource) R.string.import_hide_source else R.string.import_show_source))
            }
            if (showSource) {
                Card { Text(s.sourceLines.joinToString("\n") { it.text }, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Button(onClick = vm::save, enabled = d.includedDays.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_import_program), style = MaterialTheme.typography.titleMedium)
            }
            if (d.uncertainCount > 0) {
                Text(stringResource(R.string.import_uncertain_can_import), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    picking?.let { (dayKey, exKey) ->
        ExercisePickerDialog(onDismiss = { picking = null }, onPicked = { id ->
            picking = null
            vm.chooseExercise(dayKey, exKey, ExerciseChoice.Existing(id, ""))
        })
    }
    renamingDay?.let { day ->
        TextInputDialog(stringResource(R.string.action_rename), day.title, stringResource(R.string.session_name),
            onConfirm = { n -> vm.updateDay(day.key) { it.copy(title = n) }; renamingDay = null }, onDismiss = { renamingDay = null })
    }
    durationDay?.let { day ->
        DurationSheet(stringResource(R.string.estimated_duration), day.estimatedDurationMinutes, onDismiss = { durationDay = null }) { v ->
            vm.updateDay(day.key) { it.copy(estimatedDurationMinutes = v) }; durationDay = null
        }
    }
    val close = { edit = null }
    when (val e = edit) {
        is ImportEdit.Reps -> RepsSheet(stringResource(R.string.target_reps), e.t.repsMin, e.t.repsMax, allowRange = true, onDismiss = close) { a, b ->
            vm.updateSet(e.day, e.ex, e.i) { it.copy(repsMin = a, repsMax = b) }; close()
        }
        is ImportEdit.Rir -> RirRangeSheet(stringResource(R.string.target_rir), e.t.rirMin, e.t.rirMax, onDismiss = close) { a, b ->
            vm.updateSet(e.day, e.ex, e.i) { it.copy(rirMin = a, rirMax = b) }; close()
        }
        is ImportEdit.Rpe -> RpeSheet(stringResource(R.string.target_rpe), e.t.rpe, onDismiss = close) { v ->
            vm.updateSet(e.day, e.ex, e.i) { it.copy(rpe = v) }; close()
        }
        is ImportEdit.Weight -> WeightSheet(stringResource(R.string.target_weight), e.t.weightKg(), onDismiss = close) { kg ->
            vm.updateSet(e.day, e.ex, e.i) { it.copy(weight = kg, weightUnit = if (kg == null) null else WeightUnit.KG) }; close()
        }
        is ImportEdit.Rest -> RestSheet(stringResource(R.string.rest_time), e.t.restSeconds, onDismiss = close) { v ->
            vm.updateSet(e.day, e.ex, e.i) { it.copy(restSeconds = v) }; close()
        }
        null -> {}
    }
}

@Composable
private fun SummaryCard(s: ImportUiState.Preview, vm: ImportViewModel) {
    val d = s.draft
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(d.programName, vm::setName, label = { Text(stringResource(R.string.program_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.import_summary_days, d.includedDays.size), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.import_summary_exercises, d.exerciseCount), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.import_summary_sets, d.totalSets), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.import_summary_split, d.parsed.split.labelFr), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (d.parsed.hasAbWeeks) R.string.import_summary_ab_yes else R.string.import_summary_ab_no),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.ocrUsed) Text(stringResource(R.string.import_ocr_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.uncertainCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pluralStringResource(R.plurals.import_uncertain_count, d.uncertainCount, d.uncertainCount), style = MaterialTheme.typography.bodyMedium, color = WarningAmber)
                }
            }
            // Plusieurs semaines numérotées : l'utilisateur choisit, rien n'est converti automatiquement en A/B.
            val weeks = d.parsed.weekLabels
            if (!d.parsed.hasAbWeeks && weeks.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.import_weeks_detected, weeks.size), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weeks.forEach { w ->
                        FilterChip(selected = !s.mapTwoWeeksToAB && s.selectedWeek == w, onClick = { vm.changeWeekMode(w, false) },
                            label = { Text(stringResource(R.string.import_week_label, w)) })
                    }
                    FilterChip(selected = s.mapTwoWeeksToAB, onClick = { vm.changeWeekMode(null, true) },
                        label = { Text(stringResource(R.string.import_map_weeks_ab, weeks[0], weeks[1])) })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.program_ab_label), style = MaterialTheme.typography.bodyLarge)
                    if (d.parsed.hasAbWeeks) Text(stringResource(R.string.import_ab_detected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = d.abEnabled, onCheckedChange = vm::setAb)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.import_activate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = d.activate, onCheckedChange = vm::setActivate)
            }
        }
    }
}

@Composable
private fun DayCard(
    day: EditableDay,
    ab: Boolean,
    vm: ImportViewModel,
    onEdit: (ImportEdit) -> Unit,
    onPick: (Long) -> Unit,
    onRename: () -> Unit,
    onDuration: () -> Unit,
) {
    val days = stringArrayResource(R.array.weekdays)
    var dayMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = day.include, onCheckedChange = { v -> vm.updateDay(day.key) { it.copy(include = v) } })
                Column(Modifier.weight(1f).clickable(onClick = onRename)) {
                    Text(day.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    day.sourceWeek?.let { Text(stringResource(R.string.import_source_week, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (!day.include) return@Column
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    AssistChip(onClick = { dayMenu = true }, label = {
                        Text(day.dayOfWeek?.let { days[it - 1] } ?: stringResource(R.string.import_assign_day))
                    })
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        days.forEachIndexed { i, n ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { dayMenu = false; vm.updateDay(day.key) { it.copy(dayOfWeek = i + 1) } })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.not_scheduled)) }, onClick = { dayMenu = false; vm.updateDay(day.key) { it.copy(dayOfWeek = null) } })
                    }
                }
                if (ab) {
                    FilterChip(selected = day.week == "A", onClick = { vm.updateDay(day.key) { it.copy(week = "A") } }, label = { Text(stringResource(R.string.week_a)) })
                    FilterChip(selected = day.week == "B", onClick = { vm.updateDay(day.key) { it.copy(week = "B") } }, label = { Text(stringResource(R.string.week_b)) })
                }
                AssistChip(onClick = onDuration, label = {
                    Text(day.estimatedDurationMinutes?.let { stringResource(R.string.estimated_duration_value, Fmt.minutes(it)) }
                        ?: stringResource(R.string.import_duration_not_set))
                })
            }
            if (day.dayOfWeek == null) {
                Text(stringResource(R.string.import_day_not_in_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (day.exercises.isEmpty()) {
                Text(stringResource(R.string.session_no_exercises), style = MaterialTheme.typography.bodySmall)
            }
            day.exercises.forEach { ex ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ExerciseRow(day.key, ex, vm, onEdit, onPick)
            }
        }
    }
}

@Composable
private fun ExerciseRow(dayKey: Long, ex: EditableExercise, vm: ImportViewModel, onEdit: (ImportEdit) -> Unit, onPick: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(ex.uncertain) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
            Checkbox(checked = ex.include, onCheckedChange = { v -> vm.updateExercise(dayKey, ex.key) { it.copy(include = v) } })
            Column(Modifier.weight(1f)) {
                Text(ex.rawName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val target = when (val c = ex.choice) {
                    is ExerciseChoice.Existing -> stringResource(R.string.import_matched_to, c.name.ifBlank { stringResource(R.string.import_library_exercise) })
                    is ExerciseChoice.CreateNew -> stringResource(R.string.import_new_exercise, c.name)
                }
                Text(target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(setsSummary(ex), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ex.uncertain) Pill(stringResource(R.string.import_to_check), WarningAmber)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (expanded && ex.include) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!ex.matchConfident) {
                    Text(stringResource(R.string.import_ambiguous_name), style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ex.candidates.forEach { cnd ->
                        FilterChip(
                            selected = (ex.choice as? ExerciseChoice.Existing)?.exerciseId == cnd.exerciseId,
                            onClick = { vm.chooseExercise(dayKey, ex.key, ExerciseChoice.Existing(cnd.exerciseId, cnd.name)) },
                            label = { Text(cnd.name) },
                        )
                    }
                    FilterChip(
                        selected = ex.choice is ExerciseChoice.CreateNew,
                        onClick = { vm.chooseExercise(dayKey, ex.key, ExerciseChoice.CreateNew(ex.rawName)) },
                        label = { Text(stringResource(R.string.import_keep_original_name)) },
                    )
                    AssistChip(onClick = { onPick(ex.key) }, label = { Text(stringResource(R.string.import_search_library)) })
                }
                ex.issues.forEach { issue ->
                    Text("⚠ " + issue.message + (issue.sourceText?.let { " : « $it »" } ?: ""), style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }
                if (ex.lowOcrConfidence) Text(stringResource(R.string.import_low_ocr), style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                Text(stringResource(R.string.import_source_line, ex.sourceLine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ex.notes?.let { Text(stringResource(R.string.import_notes, it), style = MaterialTheme.typography.bodySmall) }

                if (ex.sets.isEmpty()) {
                    Text(stringResource(R.string.import_no_sets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val t = ex.defaultTarget
                    targetSummary(t.repsMin, t.repsMax, t.rirMin, t.rirMax, t.rpe, t.weightKg(), t.restSeconds)?.let {
                        Text(stringResource(R.string.import_default_targets, it), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row {
                        Text("#", Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.col_reps), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.col_rir), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.col_weight), Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.col_rest), Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(36.dp))
                    }
                    ex.sets.forEachIndexed { i, t ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${i + 1}", Modifier.width(20.dp))
                                ValueCell(Fmt.reps(t.repsMin, t.repsMax), { onEdit(ImportEdit.Reps(dayKey, ex.key, i, t)) }, Modifier.weight(1f))
                                ValueCell(Fmt.rir(t.rirMin, t.rirMax), { onEdit(ImportEdit.Rir(dayKey, ex.key, i, t)) }, Modifier.weight(1f))
                                ValueCell(weightText(t.weightKg()), { onEdit(ImportEdit.Weight(dayKey, ex.key, i, t)) }, Modifier.weight(1.2f))
                                ValueCell(t.restSeconds?.let { Fmt.rest(it) }, { onEdit(ImportEdit.Rest(dayKey, ex.key, i, t)) }, Modifier.weight(1.1f))
                                IconButton(onClick = { vm.removeSet(dayKey, ex.key, i) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_remove_set))
                                }
                            }
                            t.rpe?.let { rpe ->
                                Text(stringResource(R.string.rpe_original_value, Fmt.rpe(rpe), Fmt.approxRirFromRpe(rpe)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp).clickable { onEdit(ImportEdit.Rpe(dayKey, ex.key, i, t)) })
                            }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { vm.addSet(dayKey, ex.key) }) { Text(stringResource(R.string.action_add_set)) }
                    if (ex.uncertain) TextButton(onClick = { vm.resolveIssues(dayKey, ex.key) }) { Text(stringResource(R.string.import_mark_checked)) }
                }
            }
        }
    }
}

@Composable
private fun setsSummary(ex: EditableExercise): String {
    if (ex.sets.isEmpty()) {
        val t = ex.defaultTarget
        return targetSummary(t.repsMin, t.repsMax, t.rirMin, t.rirMax, t.rpe, t.weightKg(), t.restSeconds)
            ?: stringResource(R.string.no_planned_sets_short)
    }
    val distinct = ex.sets.distinct()
    val head = pluralStringResource(R.plurals.sets_count, ex.sets.size, ex.sets.size)
    return if (distinct.size == 1) {
        val t = distinct[0]
        listOfNotNull(head, targetSummary(t.repsMin, t.repsMax, t.rirMin, t.rirMax, t.rpe, t.weightKg(), t.restSeconds)).joinToString(" · ")
    } else {
        head + " · " + stringResource(R.string.import_individual_sets)
    }
}
