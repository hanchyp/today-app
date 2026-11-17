package com.farhan.today.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.prefs.AppModule
import com.farhan.today.ui.settings.ExercisesRoute
import com.farhan.today.ui.settings.OnboardingScreen
import com.farhan.today.ui.settings.SettingsRoute
import com.farhan.today.ui.settings.SettingsRoutes
import com.farhan.today.ui.calendar.CalendarRoute
import com.farhan.today.ui.career.ApplicationDetailRoute
import com.farhan.today.ui.career.ApplicationFormRoute
import com.farhan.today.ui.career.CareerRoute
import com.farhan.today.ui.career.ScheduleEventRoute
import com.farhan.today.ui.finance.BillsRoute
import com.farhan.today.ui.finance.BudgetsRoute
import com.farhan.today.ui.finance.CategoriesRoute
import com.farhan.today.ui.finance.FinanceRoute
import com.farhan.today.ui.finance.FinanceRoutes
import com.farhan.today.ui.finance.MonthSummaryRoute
import com.farhan.today.ui.finance.TransactionsRoute
import com.farhan.today.ui.tasks.ProjectDetailRoute
import com.farhan.today.ui.tasks.TasksRoute
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayType
import com.farhan.today.ui.today.TodayRoute
import com.farhan.today.ui.workout.ExerciseProgressRoute
import com.farhan.today.ui.workout.RoutineFormRoute
import com.farhan.today.ui.workout.SessionRoute
import com.farhan.today.ui.workout.SessionSummaryRoute
import com.farhan.today.ui.workout.WorkoutRoute
import java.time.LocalDate

object Routes {
    const val TODAY = "today"
    const val WORKOUT = "workout"
    const val CAREER = "career"
    const val FINANCE = "finance"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar?date={date}&event={event}&add={add}"
    const val TASK_FORM = "tasks/form?task={task}"
    const val CAREER_NEW = "career-new"
    const val CAREER_DETAIL = "career/{id}?focusEvent={focusEvent}"
    const val WORKOUT_PLAN = "workout/plan/{planId}/{planDate}"
    const val CAREER_EDIT = "career/{id}/edit"
    const val CAREER_SCHEDULE = "career/{id}/schedule?eventId={eventId}"
    const val PROJECT = "project/{id}"
    const val SETTINGS = "settings"
    const val ROUTINE_NEW = "routine-new"
    const val ROUTINE_EDIT = "routine/{id}"
    const val WORKOUT_SESSION = "workout/session/{id}"
    const val WORKOUT_SUMMARY = "workout/summary/{id}?celebrate={celebrate}"
    const val EXERCISE_PROGRESS = "exercise/{id}"

    /** Kalender, opsional langsung memilih tanggal tertentu. */
    fun calendar(date: LocalDate?, event: String? = null, add: Boolean = false): String =
        "calendar?date=${date?.toEpochDay() ?: -1L}&event=${event.orEmpty()}&add=$add"
    fun taskForm(id: String? = null) = "tasks/form?task=${id.orEmpty()}"
    fun careerDetail(id: String, eventId: String? = null) =
        if (eventId == null) "career/$id" else "career/$id?focusEvent=$eventId"
    fun workoutPlan(id: String, date: LocalDate) = "workout/plan/$id/${date.toEpochDay()}"
    fun careerEdit(id: String) = "career/$id/edit"
    fun careerSchedule(id: String, eventId: String? = null) = "career/$id/schedule?eventId=${eventId.orEmpty()}"
    fun project(id: String) = "project/$id"
    fun routine(id: String) = "routine/$id"
    fun workoutSession(id: String) = "workout/session/$id"
    fun workoutSummary(id: String, celebrate: Boolean = false) = "workout/summary/$id?celebrate=$celebrate"
    fun exercise(id: String) = "exercise/$id"

    /** Rute akar tab: hanya di sini bottom nav tampil, dan antar-rute ini transisinya "fade through". */
    private val tabRoots = setOf(TODAY, WORKOUT, WORKOUT_PLAN, CAREER, FINANCE, FinanceRoutes.ADD, TASKS)
    fun isTabRoot(route: String?): Boolean = route == null || route in tabRoots

    /** Tab bawah yang aktif untuk sebuah rute. */
    fun tabOf(route: String?): String? = when {
        route == null -> null
        route.startsWith("calendar") -> TODAY
        route.startsWith("career") -> CAREER
        route.startsWith("project") || route.startsWith("tasks") -> TASKS
        route.startsWith("finance") -> FINANCE
        route.startsWith("settings") -> TODAY
        route.startsWith("workout") || route.startsWith("routine") || route.startsWith("exercise") -> WORKOUT
        else -> route
    }
}

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int, val module: AppModule? = null)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", R.drawable.ic_nav_today),
    Tab(Routes.WORKOUT, "Latihan", R.drawable.ic_nav_workout, AppModule.WORKOUT),
    Tab(Routes.CAREER, "Karier", R.drawable.ic_nav_career, AppModule.CAREER),
    Tab(Routes.FINANCE, "Uang", R.drawable.ic_nav_finance, AppModule.FINANCE),
    Tab(Routes.TASKS, "Tugas", R.drawable.ic_nav_tasks, AppModule.TASKS),
)

/**
 * @param deepLink rute dari notifikasi (EXTRA_ROUTE); [onDeepLinkHandled] dipanggil setelah dibuka.
 */
@Composable
fun TodayApp(deepLink: String? = null, onDeepLinkHandled: () -> Unit = {}) {
    val prefs = LocalContext.current.appContainer.prefs
    val onboarded by prefs.onboarded.collectAsStateWithLifecycle(initialValue = null)
    when (onboarded) {
        null -> Box(Modifier.fillMaxSize().background(TodayColors.Background))
        false -> OnboardingScreen()
        true -> MainShell(deepLink, onDeepLinkHandled)
    }
}

@Composable
private fun MainShell(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val modules by LocalContext.current.appContainer.prefs.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val activeTab = Routes.tabOf(entry?.destination?.route)
    val enabled = modules?.modules ?: AppModule.entries.toSet()

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            nav.navigate(deepLink) { launchSingleTop = true }
            onDeepLinkHandled()
        }
    }

    // Tiap layar mengurus inset status bar sendiri, supaya latar layar (krem, putih, gelap)
    // ikut menutupi area status bar. Scaffold hanya menyisakan ruang untuk bottom nav.
    Scaffold(
        containerColor = TodayColors.Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = Routes.isTabRoot(entry?.destination?.route),
                enter = slideInVertically(tween(250, easing = EmphasizedDecelerate)) { it } + expandVertically(tween(250, easing = EmphasizedDecelerate), expandFrom = Alignment.Top),
                exit = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it } + shrinkVertically(tween(200, easing = EmphasizedAccelerate), shrinkTowards = Alignment.Top),
            ) {
                BottomNav(tabs.filter { it.module == null || it.module in enabled }, activeTab) { route ->
                    if (route == Routes.TODAY && entry?.destination?.route != Routes.TODAY) {
                        if (!nav.popBackStack(Routes.TODAY, inclusive = false)) nav.goTab(Routes.TODAY)
                    }
                    else nav.goTab(route)
                }
            }
        },
    ) { inner ->
        NavHost(
            nav, startDestination = Routes.TODAY,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = { if (tabSwitch()) fadeThroughIn() else sharedAxisIn(forward = true) },
            exitTransition = { if (tabSwitch()) fadeThroughOut() else sharedAxisOut(forward = true) },
            popEnterTransition = { if (tabSwitch()) fadeThroughIn() else sharedAxisIn(forward = false) },
            popExitTransition = { if (tabSwitch()) fadeThroughOut() else sharedAxisOut(forward = false) },
        ) {
            composable(Routes.TODAY) { TodayRoute(enabled, onNavigate = { nav.navigate(it) }) }
            composable(
                Routes.CALENDAR,
                arguments = listOf(
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("event") { type = NavType.StringType; defaultValue = "" },
                    navArgument("add") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                val day = e.arguments?.getLong("date") ?: -1L
                CalendarRoute(
                    initialDate = if (day >= 0) LocalDate.ofEpochDay(day) else null,
                    initialEvent = e.arguments?.getString("event")?.ifEmpty { null },
                    openAdd = e.arguments?.getBoolean("add") == true,
                    modules = enabled,
                    onNavigate = { nav.navigate(it) },
                    onBack = { if (!nav.popBackStack()) nav.goTab(Routes.TODAY) },
                )
            }
            // ---------------- Latihan
            listOf(Routes.WORKOUT, Routes.WORKOUT_PLAN).forEach { route ->
                composable(route) { workoutEntry ->
                    WorkoutRoute(
                        onOpenSession = { nav.navigate(Routes.workoutSession(it)) { launchSingleTop = true } },
                        onNewRoutine = { nav.navigate(Routes.ROUTINE_NEW) },
                        onEditRoutine = { nav.navigate(Routes.routine(it)) },
                        onOpenHistory = { nav.navigate(Routes.workoutSummary(it)) },
                        focusId = workoutEntry.arguments?.getString("planId"),
                        focusDate = workoutEntry.arguments?.getString("planDate")?.toLongOrNull()?.let(LocalDate::ofEpochDay),
                    )
                }
            }
            composable(Routes.ROUTINE_NEW) { RoutineFormRoute(null, onBack = { nav.popBackStack() }) }
            composable(Routes.ROUTINE_EDIT) { e -> RoutineFormRoute(e.arguments?.getString("id"), onBack = { nav.popBackStack() }) }
            composable(Routes.WORKOUT_SESSION) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                SessionRoute(
                    sessionId = id,
                    onBack = { if (!nav.popBackStack()) nav.goTab(Routes.WORKOUT) },
                    onFinished = { nav.navigate(Routes.workoutSummary(it, celebrate = true)) { popUpTo(Routes.WORKOUT_SESSION) { inclusive = true } } },
                )
            }
            composable(
                Routes.WORKOUT_SUMMARY,
                arguments = listOf(navArgument("celebrate") { type = NavType.BoolType; defaultValue = false }),
            ) { e ->
                SessionSummaryRoute(
                    sessionId = e.arguments?.getString("id").orEmpty(),
                    celebrate = e.arguments?.getBoolean("celebrate") ?: false,
                    onBack = { if (!nav.popBackStack()) nav.goTab(Routes.WORKOUT) },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.EXERCISE_PROGRESS) { e -> ExerciseProgressRoute(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
            // ---------------- Keuangan
            composable(Routes.FINANCE) { FinanceRoute(openAdd = false, onNavigate = { nav.navigate(it) }) }
            composable(FinanceRoutes.ADD) {
                FinanceRoute(openAdd = true, onNavigate = { nav.navigate(it) }, onCloseAdd = { nav.popBackStack() })
            }
            composable(FinanceRoutes.TRANSACTIONS) { TransactionsRoute(onBack = { nav.popBackStack() }) }
            composable(FinanceRoutes.BUDGETS) { BudgetsRoute(onBack = { nav.popBackStack() }) }
            composable(FinanceRoutes.BILLS) { BillsRoute(onBack = { nav.popBackStack() }) }
            composable(FinanceRoutes.BILL) { entry ->
                BillsRoute(onBack = { nav.popBackStack() }, billId = entry.arguments?.getString("billId"))
            }
            composable(FinanceRoutes.SUMMARY) { MonthSummaryRoute(onBack = { nav.popBackStack() }) }
            composable(FinanceRoutes.CATEGORIES) { CategoriesRoute(onBack = { nav.popBackStack() }) }

            // ---------------- Karier
            composable(Routes.CAREER) {
                CareerRoute(onOpen = { nav.navigate(Routes.careerDetail(it)) }, onAdd = { nav.navigate(Routes.CAREER_NEW) })
            }
            composable(Routes.CAREER_NEW) {
                ApplicationFormRoute(null, onBack = { nav.popBackStack() }) { id ->
                    nav.navigate(Routes.careerDetail(id)) { popUpTo(Routes.CAREER_NEW) { inclusive = true } }
                }
            }
            composable(Routes.CAREER_DETAIL, arguments = listOf(navArgument("focusEvent") { defaultValue = "" })) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                ApplicationDetailRoute(
                    id = id,
                    onBack = { if (!nav.popBackStack()) nav.goTab(Routes.CAREER) },
                    onEdit = { nav.navigate(Routes.careerEdit(id)) },
                    onSchedule = { eventId -> nav.navigate(Routes.careerSchedule(id, eventId)) },
                    onOpenCalendar = { nav.navigate(Routes.calendar(it)) },
                    focusEventId = e.arguments?.getString("focusEvent")?.ifEmpty { null },
                )
            }
            composable(Routes.CAREER_EDIT) { e ->
                ApplicationFormRoute(e.arguments?.getString("id"), onBack = { nav.popBackStack() }) { nav.popBackStack() }
            }
            composable(
                Routes.CAREER_SCHEDULE,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                ScheduleEventRoute(
                    applicationId = id,
                    eventId = e.arguments?.getString("eventId")?.ifEmpty { null },
                    onBack = { nav.popBackStack() },
                    onSaved = { date ->
                        // Alur prototipe: simpan → Kalender di tanggal itu; kembali → Detail lamaran.
                        nav.navigate(Routes.calendar(date)) { popUpTo(Routes.CAREER_SCHEDULE) { inclusive = true } }
                    },
                )
            }

            // ---------------- Pengaturan
            composable(Routes.SETTINGS) { SettingsRoute(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }) }
            composable(SettingsRoutes.EXERCISES) { ExercisesRoute(onBack = { nav.popBackStack() }) }

            // ---------------- Tugas
            composable(Routes.TASKS) { TasksRoute(onOpenProject = { nav.navigate(Routes.project(it)) }) }
            // Dari notifikasi/deep link: daftar Tugas tetap tampil di belakang sheet,
            // bukan layar kosong. Dari Today, sheet dibuka langsung tanpa navigasi.
            composable(Routes.TASK_FORM, arguments = listOf(navArgument("task") { defaultValue = "" })) { e ->
                Box(Modifier.fillMaxSize()) {
                    TasksRoute(onOpenProject = { nav.navigate(Routes.project(it)) })
                    com.farhan.today.ui.tasks.TaskFormDestination(
                        taskId = e.arguments?.getString("task")?.ifEmpty { null },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(Routes.PROJECT) { e ->
                ProjectDetailRoute(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}

// ---------------------------------------------------------------- Transisi (Material motion)

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val AXIS_MS = 300

/** Pindah antar-tab (kedua ujung adalah akar tab) → fade through, bukan geser. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch(): Boolean =
    Routes.isTabRoot(initialState.destination.route) && Routes.isTabRoot(targetState.destination.route)

/** Fade through: halaman lama hilang cepat (90 ms), lalu halaman baru muncul + membesar sedikit. */
private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.94f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing))

/** Shared axis X: geser pendek (~8% lebar) + fade, agar tidak ada dua halaman transparan bertumpuk lama. */
private fun sharedAxisIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { w -> (if (forward) 1 else -1) * w / 12 } +
        fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing))

private fun sharedAxisOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { w -> (if (forward) -1 else 1) * w / 12 } +
        fadeOut(tween(90, easing = FastOutLinearInEasing))

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomNav(tabs: List<Tab>, active: String?, onSelect: (String) -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    NavigationBar(containerColor = TodayColors.Surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == active,
                onClick = { onSelect(tab.route) },
                icon = { Icon(painterResource(tab.icon), if (largeText) tab.label else null, Modifier.size(24.dp)) },
                label = if (largeText) null else ({ Text(tab.label, style = TodayType.Label, maxLines = 1) }),
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = TodayColors.BrandTint,
                    selectedIconColor = TodayColors.BrandText,
                    selectedTextColor = TodayColors.BrandText,
                    unselectedIconColor = TodayColors.Muted,
                    unselectedTextColor = TodayColors.Muted,
                ),
            )
        }
    }
}
