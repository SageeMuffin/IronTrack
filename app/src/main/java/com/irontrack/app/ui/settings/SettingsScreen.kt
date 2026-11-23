package com.irontrack.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.BuildConfig
import com.irontrack.app.R
import com.irontrack.app.data.repo.ThemePref
import com.irontrack.app.data.repo.WeightUnitPref
import com.irontrack.app.notifications.ReminderScheduler
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.ui.components.ConfirmDialog
import com.irontrack.app.ui.components.IntSheet
import com.irontrack.app.ui.components.RestSheet
import com.irontrack.app.ui.components.SectionTitle
import com.irontrack.app.ui.components.SettingRow
import com.irontrack.app.ui.components.WheelSheet
import com.irontrack.app.ui.components.WheelSpec
import com.irontrack.app.ui.navigation.Routes
import com.irontrack.app.ui.unitLabel
import com.irontrack.core.util.FrenchFormat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class SettingsViewModel(val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    private val s = c.settings
    fun setUnit(u: WeightUnitPref) = viewModelScope.launch { s.setWeightUnit(u) }
    fun setIncrement(v: Double) = viewModelScope.launch { s.setWeightIncrement(v) }
    fun setFirstDay(d: DayOfWeek) = viewModelScope.launch { s.setFirstDayOfWeek(d) }
    fun setTheme(t: ThemePref) = viewModelScope.launch { s.setTheme(t) }
    fun setDefaultRest(v: Int?) = viewModelScope.launch { s.setDefaultRestSeconds(v) }
    fun setShowEstimated(v: Boolean) = viewModelScope.launch { s.setShowEstimated(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { s.setNotificationsEnabled(v); ReminderScheduler.reschedule(app) }
    fun setReminder(v: Int) = viewModelScope.launch { s.setReminderMinutesBefore(v); ReminderScheduler.reschedule(app) }

    suspend fun export(uri: Uri): Result<Int> = runCatching { c.backup.export(uri, BuildConfig.VERSION_NAME) }
    suspend fun restore(uri: Uri): Result<Int> = runCatching { c.backup.restore(uri).also { ReminderScheduler.reschedule(app) } }
}

private enum class Picker { UNIT, INCREMENT, FIRST_DAY, THEME, REST, REMINDER }

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm = appViewModel { SettingsViewModel(it, app) }
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var showRirHelp by remember { mutableStateOf(false) }
    val days = stringArrayResource(R.array.weekdays)

    val msgExportOk = stringResource(R.string.export_success)
    val msgExportError = stringResource(R.string.export_error)
    val msgRestoreOk = stringResource(R.string.restore_success)
    val msgRestoreError = stringResource(R.string.restore_error)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(vm.export(uri).fold({ String.format(msgExportOk, it) }, { msgExportError }))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setNotifications(granted) }

    AppScaffold(title = stringResource(R.string.nav_settings), snackbarHostState = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_units), Modifier.padding(horizontal = 16.dp))
            SettingRow(stringResource(R.string.settings_weight_unit), unitLabel(), onClick = { picker = Picker.UNIT })
            SettingRow(stringResource(R.string.settings_weight_increment), "${FrenchFormat.number(settings.weightIncrement, 2)} ${unitLabel()}", onClick = { picker = Picker.INCREMENT })
            SettingRow(stringResource(R.string.settings_first_day), days[settings.firstDayOfWeek.value - 1], onClick = { picker = Picker.FIRST_DAY })

            SectionTitle(stringResource(R.string.settings_workout), Modifier.padding(horizontal = 16.dp))
            SettingRow(
                stringResource(R.string.settings_default_rest),
                settings.defaultRestSeconds?.let { Fmt.rest(it) } ?: stringResource(R.string.not_set),
                subtitle = stringResource(R.string.settings_default_rest_hint),
                onClick = { picker = Picker.REST },
            )
            SwitchRow(stringResource(R.string.settings_show_estimated), settings.showEstimatedDuringWorkout) { vm.setShowEstimated(it) }
            SettingRow(stringResource(R.string.rir_help_title), null, onClick = { showRirHelp = true })

            SectionTitle(stringResource(R.string.settings_notifications), Modifier.padding(horizontal = 16.dp))
            SwitchRow(stringResource(R.string.settings_notifications_enabled), settings.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.setNotifications(on)
            }
            if (settings.notificationsEnabled) {
                SettingRow(stringResource(R.string.settings_reminder_before), stringResource(R.string.minutes_before, settings.reminderMinutesBefore),
                    onClick = { picker = Picker.REMINDER })
            }

            SectionTitle(stringResource(R.string.settings_appearance), Modifier.padding(horizontal = 16.dp))
            SettingRow(stringResource(R.string.settings_theme), themeLabel(settings.theme), onClick = { picker = Picker.THEME })

            SectionTitle(stringResource(R.string.settings_data), Modifier.padding(horizontal = 16.dp))
            SettingRow(stringResource(R.string.settings_export), null, subtitle = stringResource(R.string.settings_export_hint),
                onClick = { exportLauncher.launch("irontrack-sauvegarde-${LocalDate.now()}.json") })
            SettingRow(stringResource(R.string.settings_restore), null, subtitle = stringResource(R.string.settings_restore_hint),
                onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })

            SectionTitle(stringResource(R.string.settings_about), Modifier.padding(horizontal = 16.dp))
            SettingRow(stringResource(R.string.about_title), stringResource(R.string.version_value, BuildConfig.VERSION_NAME), onClick = { nav.navigate(Routes.ABOUT) })
            HorizontalDivider(Modifier.padding(top = 16.dp))
        }
    }

    when (picker) {
        Picker.UNIT -> ChoiceDialog(stringResource(R.string.settings_weight_unit),
            listOf(stringResource(R.string.unit_kg_long), stringResource(R.string.unit_lb_long)),
            if (settings.weightUnit == WeightUnitPref.KG) 0 else 1, onDismiss = { picker = null }) {
            vm.setUnit(if (it == 0) WeightUnitPref.KG else WeightUnitPref.LB); picker = null
        }
        Picker.INCREMENT -> {
            val values = listOf(0.25, 0.5, 1.0, 1.25, 2.0, 2.5, 5.0, 10.0)
            WheelSheet(
                title = stringResource(R.string.settings_weight_increment),
                wheels = listOf(WheelSpec(values.map { FrenchFormat.number(it, 2) + " " + unitLabel() }, values.indexOf(settings.weightIncrement).coerceAtLeast(0))),
                onDismiss = { picker = null },
                onConfirm = { vm.setIncrement(values[it[0]]); picker = null },
            )
        }
        Picker.FIRST_DAY -> ChoiceDialog(stringResource(R.string.settings_first_day), listOf(days[0], days[5], days[6]),
            when (settings.firstDayOfWeek) { DayOfWeek.SATURDAY -> 1; DayOfWeek.SUNDAY -> 2; else -> 0 }, onDismiss = { picker = null }) {
            vm.setFirstDay(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)[it]); picker = null
        }
        Picker.THEME -> ChoiceDialog(stringResource(R.string.settings_theme), ThemePref.entries.map { themeLabel(it) }, settings.theme.ordinal,
            onDismiss = { picker = null }) { vm.setTheme(ThemePref.entries[it]); picker = null }
        Picker.REST -> RestSheet(stringResource(R.string.settings_default_rest), settings.defaultRestSeconds, onDismiss = { picker = null }) {
            vm.setDefaultRest(it); picker = null
        }
        Picker.REMINDER -> IntSheet(stringResource(R.string.settings_reminder_before), listOf(0, 10, 15, 30, 45, 60, 90, 120, 180),
            settings.reminderMinutesBefore, "min", onDismiss = { picker = null }) { vm.setReminder(it); picker = null }
        null -> {}
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(R.string.restore_confirm_message),
            confirmLabel = stringResource(R.string.settings_restore),
            destructive = true,
            onConfirm = {
                pendingRestore = null
                scope.launch { snackbar.showSnackbar(vm.restore(uri).fold({ String.format(msgRestoreOk, it) }, { msgRestoreError })) }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    if (showRirHelp) {
        val meanings = stringArrayResource(R.array.rir_meanings)
        AlertDialog(
            onDismissRequest = { showRirHelp = false },
            title = { Text(stringResource(R.string.rir_help_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rir_help_text))
                    meanings.take(6).forEachIndexed { i, m -> Text("RIR $i : $m", style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.rpe_help_text), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showRirHelp = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun themeLabel(t: ThemePref) = stringResource(
    when (t) {
        ThemePref.DARK -> R.string.theme_dark
        ThemePref.LIGHT -> R.string.theme_light
        ThemePref.SYSTEM -> R.string.theme_system
    }
)

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selected, onClick = { onSelect(i) })
                        TextButton(onClick = { onSelect(i) }) { Text(o) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
