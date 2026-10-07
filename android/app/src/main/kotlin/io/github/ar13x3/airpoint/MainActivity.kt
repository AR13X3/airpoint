package io.github.ar13x3.airpoint

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ar13x3.airpoint.core.ThemeMode
import io.github.ar13x3.airpoint.core.UserSettings
import io.github.ar13x3.airpoint.ui.AirpointNav
import io.github.ar13x3.airpoint.ui.theme.AirpointTheme

class MainActivity : ComponentActivity() {

    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AirpointApp

        setContent {
            val settings: UserSettings? by app.settings.settings.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent // keep the splash up until settings load
            val dark = when (s.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                ready = true
            }
            AirpointTheme(s.theme) {
                AirpointNav(showOnboarding = !s.onboarded)
            }
        }
    }
}
