package io.github.ar13x3.airpoint.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.ui.home.HomeScreen
import io.github.ar13x3.airpoint.ui.onboarding.OnboardingScreen
import io.github.ar13x3.airpoint.ui.pair.PairScreen
import io.github.ar13x3.airpoint.ui.settings.SettingsScreen
import io.github.ar13x3.airpoint.ui.theme.Motion
import kotlinx.coroutines.launch

private const val START_KEY = "start"

@Composable
fun AirpointNav(showOnboarding: Boolean) {
    val app = LocalContext.current.applicationContext as AirpointApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    // Decided once: finishing onboarding flips the setting, which must not rebuild the graph.
    val startDestination = remember { if (showOnboarding) "onboarding" else "home" }

    // Shared X-axis: the new screen slides in a quarter-width while the old one drifts back.
    NavHost(
        nav,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(Motion.offsetCalm) { it / 4 } + fadeIn(tween(Motion.MEDIUM, 40)) },
        exitTransition = { slideOutHorizontally(Motion.offsetCalm) { -it / 8 } + fadeOut(tween(Motion.SHORT)) },
        popEnterTransition = { slideInHorizontally(Motion.offsetCalm) { -it / 8 } + fadeIn(tween(Motion.MEDIUM, 40)) },
        popExitTransition = { slideOutHorizontally(Motion.offsetCalm) { it / 4 } + fadeOut(tween(Motion.SHORT)) },
    ) {
        composable("onboarding") {
            OnboardingScreen(onDone = {
                scope.launch { app.settings.setOnboarded() }
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable("home") { entry ->
            val start by entry.savedStateHandle.getStateFlow(START_KEY, false).collectAsStateWithLifecycle()
            HomeScreen(
                onOpenPair = { startAfter -> nav.navigate("pair?start=$startAfter") },
                onOpenSettings = { nav.navigate("settings") },
                startRequested = start,
                onStartHandled = { entry.savedStateHandle[START_KEY] = false },
            )
        }
        composable(
            "pair?start={start}",
            arguments = listOf(navArgument("start") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val startAfter = entry.arguments?.getBoolean("start") ?: false
            PairScreen(
                onBack = { nav.popBackStack() },
                onPaired = {
                    nav.previousBackStackEntry?.savedStateHandle?.set(START_KEY, startAfter)
                    nav.popBackStack()
                },
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
