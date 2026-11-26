package com.irontrack.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.irontrack.app.R
import com.irontrack.app.ui.components.AutoShrinkText
import com.irontrack.app.importer.SharedImport
import com.irontrack.app.importer.SharedImportHolder
import com.irontrack.app.share.ProgramLinkDialog
import com.irontrack.app.ui.about.AboutScreen
import com.irontrack.app.ui.calendar.CalendarScreen
import com.irontrack.app.ui.exercises.ExerciseLibraryScreen
import com.irontrack.app.ui.home.HomeScreen
import com.irontrack.app.ui.importer.ImportScreen
import com.irontrack.app.ui.programs.ProgramEditorScreen
import com.irontrack.app.ui.programs.ProgramsScreen
import com.irontrack.app.ui.programs.SessionEditorScreen
import com.irontrack.app.ui.settings.SettingsScreen
import com.irontrack.app.ui.stats.BodyMetricScreen
import com.irontrack.app.ui.stats.ExerciseStatsScreen
import com.irontrack.app.ui.stats.StatsScreen
import com.irontrack.app.ui.stats.WorkoutDetailScreen
import com.irontrack.app.ui.workout.WorkoutScreen

object Routes {
    const val HOME = "home"
    const val PROGRAMS = "programs"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val PROGRAM = "program/{id}"
    const val SESSION = "session/{id}"
    const val WORKOUT = "workout/{id}"
    const val WORKOUT_DETAIL = "workoutDetail/{id}"
    const val EXERCISE_STATS = "exerciseStats/{id}"
    const val EXERCISES = "exercises"
    const val IMPORT = "import"
    const val ABOUT = "about"
    const val BODY_METRIC = "body/{key}"

    fun program(id: Long) = "program/$id"
    fun session(id: Long) = "session/$id"
    fun workout(id: Long) = "workout/$id"
    fun workoutDetail(id: Long) = "workoutDetail/$id"
    fun exerciseStats(id: Long) = "exerciseStats/$id"
    fun bodyMetric(key: String) = "body/$key"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    Tab(Routes.PROGRAMS, R.string.nav_programs, Icons.Filled.FitnessCenter),
    Tab(Routes.CALENDAR, R.string.nav_calendar, Icons.Filled.CalendarMonth),
    Tab(Routes.STATS, R.string.nav_stats, Icons.Filled.Insights),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun IronTrackNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    // Contenu partagé vers IronTrack (texte, PDF, image) : ouvrir l'import.
    val shared by SharedImportHolder.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null && shared !is SharedImport.ProgramLink && currentRoute != Routes.IMPORT) nav.navigate(Routes.IMPORT)
    }
    // Programme reçu par lien de partage : confirmation puis ouverture du programme importé.
    (shared as? SharedImport.ProgramLink)?.let { link ->
        ProgramLinkDialog(
            link = link.link,
            onClose = { SharedImportHolder.consumeProgramLink() },
            onImported = { id ->
                SharedImportHolder.consumeProgramLink()
                nav.navigateTab(Routes.PROGRAMS)
                nav.navigate(Routes.program(id))
            },
        )
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { AutoShrinkText(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.PROGRAMS) { ProgramsScreen(nav) }
            composable(Routes.CALENDAR) { CalendarScreen(nav) }
            composable(Routes.STATS) { StatsScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.PROGRAM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ProgramEditorScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SessionEditorScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(Routes.WORKOUT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutDetailScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(Routes.EXERCISE_STATS, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ExerciseStatsScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(Routes.EXERCISES) { ExerciseLibraryScreen(nav) }
            composable(Routes.IMPORT) { ImportScreen(nav) }
            composable(Routes.ABOUT) { AboutScreen(nav) }
            composable(Routes.BODY_METRIC, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
                BodyMetricScreen(nav, it.arguments!!.getString("key").orEmpty())
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
