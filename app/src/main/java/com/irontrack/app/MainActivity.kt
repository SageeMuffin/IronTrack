package com.irontrack.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irontrack.app.importer.SharedImport
import com.irontrack.core.share.ProgramShareCodec
import com.irontrack.app.importer.SharedImportHolder
import com.irontrack.app.ui.LocalAppSettings
import com.irontrack.app.ui.navigation.IronTrackNavHost
import com.irontrack.app.ui.theme.IronTrackTheme
import com.irontrack.app.data.repo.AppSettings
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** L'application est entièrement en français, quelle que soit la langue du téléphone. */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        Locale.setDefault(Locale.FRANCE)
        config.setLocale(Locale.FRANCE)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShareIntent(intent)
        val container = (application as IronTrackApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CompositionLocalProvider(LocalAppSettings provides settings) {
                IronTrackTheme(settings.theme, settings.accent, settings.oledBlack) {
                    IronTrackNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        // Lien de partage d'un programme (irontrack://programme?d=… ou page web IronTrack).
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.dataString ?: return
            if (ProgramShareCodec.containsLink(data)) SharedImportHolder.post(SharedImport.ProgramLink(data))
            setIntent(Intent(this, MainActivity::class.java))
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type ?: return
        if (type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                SharedImportHolder.post(if (ProgramShareCodec.containsLink(text)) SharedImport.ProgramLink(text) else SharedImport.Text(text))
            }
            return
        }
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) SharedImportHolder.post(SharedImport.Document(uri, type))
    }
}
