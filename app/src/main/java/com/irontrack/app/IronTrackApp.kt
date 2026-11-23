package com.irontrack.app

import android.app.Application
import com.irontrack.app.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IronTrackApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Portée des tâches de fond liées à l'application (indépendante des écrans). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        appScope.launch {
            container.exercises.seedCatalog()
            ReminderScheduler.reschedule(this@IronTrackApp)
            // Mises à jour : nettoyage des APK déjà installés et vérification automatique (24 h max).
            container.updates.cleanup()
            container.updates.check(manual = false)
        }
    }
}
