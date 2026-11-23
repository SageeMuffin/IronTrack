package com.irontrack.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.irontrack.app.AppContainer
import com.irontrack.app.BuildConfig
import com.irontrack.app.R
import com.irontrack.app.ui.Fmt
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.appViewModel
import com.irontrack.app.ui.components.AppScaffold
import com.irontrack.app.update.ReleaseInfo
import com.irontrack.app.update.UpdateCheckResult
import com.irontrack.app.update.UpdateException
import com.irontrack.core.util.FrenchFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: ReleaseInfo?) : UpdateUi
    data class Available(val release: ReleaseInfo) : UpdateUi
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateUi
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateUi
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateUi
}

class AboutViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val ui = _ui.asStateFlow()

    fun check() = viewModelScope.launch {
        _ui.value = UpdateUi.Checking
        _ui.value = when (val r = c.updates.check(manual = true)) {
            is UpdateCheckResult.Available -> UpdateUi.Available(r.release)
            is UpdateCheckResult.UpToDate -> UpdateUi.UpToDate(r.latest)
            is UpdateCheckResult.Failed -> UpdateUi.Error(r.message)
            UpdateCheckResult.Skipped -> UpdateUi.Idle
        }
    }

    fun download(release: ReleaseInfo) = viewModelScope.launch {
        _ui.value = UpdateUi.Downloading(release, null)
        _ui.value = try {
            val f = c.updates.download(release) { p -> _ui.value = UpdateUi.Downloading(release, p) }
            UpdateUi.Ready(release, f)
        } catch (e: UpdateException) {
            UpdateUi.Error(e.message ?: app.getString(R.string.update_error_download), release)
        } catch (e: Exception) {
            UpdateUi.Error(app.getString(R.string.update_error_download), release)
        }
    }

    fun canInstall() = c.updates.canInstallPackages()
    fun installIntent(apk: File) = c.updates.installIntent(apk)
    fun unknownSourcesIntent() = c.updates.unknownSourcesSettingsIntent()
    fun setAuto(v: Boolean) = viewModelScope.launch { c.settings.setAutoUpdateCheck(v) }
    fun setPrerelease(v: Boolean) = viewModelScope.launch { c.settings.setIncludePrereleases(v) }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm = appViewModel { AboutViewModel(it, app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var notes by remember { mutableStateOf<ReleaseInfo?>(null) }
    var needPermission by remember { mutableStateOf(false) }
    var canInstall by remember { mutableStateOf(vm.canInstall()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = vm.canInstall() }

    AppScaffold(title = stringResource(R.string.about_title), onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.about_latest_version, settings.latestKnownVersion ?: stringResource(R.string.unknown)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_last_check,
                            settings.lastUpdateCheckMillis?.let { Fmt.date(it) + " " + Fmt.time(it) } ?: stringResource(R.string.never)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val s = ui) {
                UpdateUi.Checking -> {
                    Text(stringResource(R.string.update_checking))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is UpdateUi.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
                is UpdateUi.Available -> AvailableCard(s.release, onDownload = { vm.download(s.release) }, onNotes = { notes = s.release })
                is UpdateUi.Downloading -> {
                    Text(stringResource(R.string.update_downloading))
                    val p = s.progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is UpdateUi.Ready -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_download_done), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.update_verified), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            if (canInstall) context.startActivity(vm.installIntent(s.apk)) else needPermission = true
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
                    }
                }
                is UpdateUi.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    s.release?.let { r -> OutlinedButton(onClick = { vm.download(r) }) { Text(stringResource(R.string.action_retry)) } }
                }
                UpdateUi.Idle -> {}
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::check, enabled = ui !is UpdateUi.Checking && ui !is UpdateUi.Downloading, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_check_button))
                }
                OutlinedButton(onClick = {
                    val r = (ui as? UpdateUi.Available)?.release ?: (ui as? UpdateUi.UpToDate)?.latest ?: (ui as? UpdateUi.Ready)?.release
                    if (r != null) notes = r
                    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases")))
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.update_whats_new)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.update_auto_check), modifier = Modifier.weight(1f))
                Switch(checked = settings.autoUpdateCheck, onCheckedChange = vm::setAuto)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_prereleases))
                    Text(stringResource(R.string.update_prereleases_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.includePrereleases, onCheckedChange = vm::setPrerelease)
            }
            Text(stringResource(R.string.about_offline_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    notes?.let { r ->
        AlertDialog(
            onDismissRequest = { notes = null },
            title = { Text(stringResource(R.string.update_notes_title, r.version.toString())) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    publishedDate(r)?.let { Text(stringResource(R.string.update_published_on, it), style = MaterialTheme.typography.bodySmall) }
                    Text(r.notes.ifBlank { stringResource(R.string.update_no_notes) })
                }
            },
            confirmButton = { TextButton(onClick = { notes = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (needPermission) {
        AlertDialog(
            onDismissRequest = { needPermission = false },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_message)) },
            confirmButton = {
                TextButton(onClick = { needPermission = false; context.startActivity(vm.unknownSourcesIntent()) }) {
                    Text(stringResource(R.string.update_open_settings))
                }
            },
            dismissButton = { TextButton(onClick = { needPermission = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AvailableCard(r: ReleaseInfo, onDownload: () -> Unit, onNotes: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.update_available), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.update_new_version, r.version.toString()))
            publishedDate(r)?.let { Text(stringResource(R.string.update_published_on, it), style = MaterialTheme.typography.bodySmall) }
            r.apkSize?.let { Text(stringResource(R.string.update_size, FrenchFormat.number(it / 1_048_576.0, 1)), style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), enabled = r.apkUrl != null) { Text(stringResource(R.string.update_download)) }
            TextButton(onClick = onNotes) { Text(stringResource(R.string.update_whats_new)) }
        }
    }
}

private fun publishedDate(r: ReleaseInfo): String? = r.publishedAt?.let {
    runCatching { Fmt.date(OffsetDateTime.parse(it).toLocalDate()) }.getOrNull()
}
