package com.irontrack.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.irontrack.app.IronTrackApp
import com.irontrack.app.MainActivity
import com.irontrack.app.R
import com.irontrack.core.util.FrenchFormat
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Rappels de séance : un seul travail WorkManager programmé pour la prochaine séance.
 * Il est recalculé au démarrage, après chaque modification du programme et après chaque rappel.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "workout_reminders"
    private const val WORK_NAME = "next_workout_reminder"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_description)
            }
        )
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as IronTrackApp
        val c = app.container
        val wm = WorkManager.getInstance(context)
        val settings = c.settings.current()
        if (!settings.notificationsEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val snapshot = c.schedule.observe(today, today.plusDays(14)).first()
        val zone = ZoneId.systemDefault()
        val next = snapshot.entries.asSequence()
            .filter { it.session != null && it.workout == null && it.timeOfDayMinutes != null }
            .map { e -> e to e.date.atStartOfDay().plusMinutes(e.timeOfDayMinutes!!.toLong()).minusMinutes(settings.reminderMinutesBefore.toLong()) }
            .filter { (_, at) -> at.isAfter(now) }
            .minByOrNull { it.second }
        if (next == null) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val (entry, at) = next
        val delay = at.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to entry.title, "time" to FrenchFormat.time(entry.timeOfDayMinutes!!)))
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val time = inputData.getString("time").orEmpty()
        val ctx = applicationContext
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            val intent = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_title))
                .setContentText(ctx.getString(R.string.notif_text, title, time))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(1001, n)
        }
        // Programmer le rappel suivant.
        ReminderScheduler.reschedule(ctx)
        return Result.success()
    }
}
