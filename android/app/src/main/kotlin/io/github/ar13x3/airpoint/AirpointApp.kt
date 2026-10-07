package io.github.ar13x3.airpoint

import android.app.Application
import io.github.ar13x3.airpoint.core.SettingsRepository
import io.github.ar13x3.airpoint.session.AirpointController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AirpointApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings by lazy { SettingsRepository(this) }
    val controller by lazy { AirpointController(this, settings, appScope) }
}
