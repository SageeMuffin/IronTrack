package com.irontrack.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class WeightUnitPref { KG, LB }
enum class ThemePref { DARK, LIGHT, SYSTEM }

/** Couleur principale de l'interface. */
enum class AccentColor { ORANGE, RED, PINK, PURPLE, BLUE, CYAN, GREEN, YELLOW }

data class AppSettings(
    val weightUnit: WeightUnitPref = WeightUnitPref.KG,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Pas du sélecteur de charge, dans l'unité choisie. */
    val weightIncrement: Double = 2.5,
    val theme: ThemePref = ThemePref.DARK,
    val accent: AccentColor = AccentColor.ORANGE,
    /** Noir intégral en mode sombre (écrans OLED). */
    val oledBlack: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 60,
    /** Repos par défaut : utilisé uniquement si l'utilisateur l'a explicitement défini. */
    val defaultRestSeconds: Int? = null,
    val showEstimatedDuringWorkout: Boolean = true,
    val autoUpdateCheck: Boolean = true,
    val includePrereleases: Boolean = false,
    val lastUpdateCheckMillis: Long? = null,
    val latestKnownVersion: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "irontrack_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val unit = stringPreferencesKey("weight_unit")
        val firstDay = intPreferencesKey("first_day_of_week")
        val increment = doublePreferencesKey("weight_increment")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent_color")
        val oled = booleanPreferencesKey("oled_black")
        val notif = booleanPreferencesKey("notifications_enabled")
        val reminder = intPreferencesKey("reminder_minutes_before")
        val defaultRest = intPreferencesKey("default_rest_seconds")
        val showEstimated = booleanPreferencesKey("show_estimated_during_workout")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
        val prerelease = booleanPreferencesKey("include_prereleases")
        val lastCheck = longPreferencesKey("last_update_check")
        val latestVersion = stringPreferencesKey("latest_known_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            weightUnit = p[K.unit]?.let { runCatching { WeightUnitPref.valueOf(it) }.getOrNull() } ?: WeightUnitPref.KG,
            firstDayOfWeek = p[K.firstDay]?.let { DayOfWeek.of(it.coerceIn(1, 7)) } ?: DayOfWeek.MONDAY,
            weightIncrement = p[K.increment] ?: if (p[K.unit] == WeightUnitPref.LB.name) 5.0 else 2.5,
            theme = p[K.theme]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.DARK,
            accent = p[K.accent]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.ORANGE,
            oledBlack = p[K.oled] ?: false,
            notificationsEnabled = p[K.notif] ?: false,
            reminderMinutesBefore = p[K.reminder] ?: 60,
            defaultRestSeconds = p[K.defaultRest],
            showEstimatedDuringWorkout = p[K.showEstimated] ?: true,
            autoUpdateCheck = p[K.autoUpdate] ?: true,
            includePrereleases = p[K.prerelease] ?: false,
            lastUpdateCheckMillis = p[K.lastCheck],
            latestKnownVersion = p[K.latestVersion],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWeightUnit(u: WeightUnitPref) = context.dataStore.edit {
        it[K.unit] = u.name
        it[K.increment] = if (u == WeightUnitPref.LB) 5.0 else 2.5
    }
    suspend fun setFirstDayOfWeek(d: DayOfWeek) = context.dataStore.edit { it[K.firstDay] = d.value }
    suspend fun setWeightIncrement(v: Double) = context.dataStore.edit { it[K.increment] = v }
    suspend fun setTheme(t: ThemePref) = context.dataStore.edit { it[K.theme] = t.name }
    suspend fun setAccent(a: AccentColor) = context.dataStore.edit { it[K.accent] = a.name }
    suspend fun setOledBlack(v: Boolean) = context.dataStore.edit { it[K.oled] = v }
    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[K.notif] = v }
    suspend fun setReminderMinutesBefore(v: Int) = context.dataStore.edit { it[K.reminder] = v }
    suspend fun setDefaultRestSeconds(v: Int?) = context.dataStore.edit {
        if (v == null) it.remove(K.defaultRest) else it[K.defaultRest] = v
    }
    suspend fun setShowEstimated(v: Boolean) = context.dataStore.edit { it[K.showEstimated] = v }
    suspend fun setAutoUpdateCheck(v: Boolean) = context.dataStore.edit { it[K.autoUpdate] = v }
    suspend fun setIncludePrereleases(v: Boolean) = context.dataStore.edit { it[K.prerelease] = v }
    suspend fun recordUpdateCheck(millis: Long, latest: String?) = context.dataStore.edit {
        it[K.lastCheck] = millis
        if (latest != null) it[K.latestVersion] = latest
    }
}
