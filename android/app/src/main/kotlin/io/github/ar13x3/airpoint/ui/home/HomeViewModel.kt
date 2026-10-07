package io.github.ar13x3.airpoint.ui.home

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.core.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val app: AirpointApp) : ViewModel() {
    private val controller = app.controller

    val session = controller.state
    val pad = controller.pad
    val isSimulated get() = controller.isSimulated
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val paired = app.settings.pairedPcs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Slider positions live here for instant feedback; they apply to the live session at once
    // and are written to disk shortly after the finger stops.
    var sensitivity by mutableFloatStateOf(UserSettings().sensitivity)
        private set
    var smoothness by mutableFloatStateOf(UserSettings().smoothness)
        private set
    private var saveSensitivity: Job? = null
    private var saveSmoothness: Job? = null

    init {
        viewModelScope.launch {
            val s = app.settings.current()
            sensitivity = s.sensitivity
            smoothness = s.smoothness
        }
    }

    fun onSensitivity(v: Float) {
        sensitivity = v
        controller.previewTuning(sensitivity = v)
        saveSensitivity?.cancel()
        saveSensitivity = viewModelScope.launch { delay(SAVE_DELAY_MS); app.settings.setSensitivity(v) }
    }

    fun onSmoothness(v: Float) {
        smoothness = v
        controller.previewTuning(smoothness = v)
        saveSmoothness?.cancel()
        saveSmoothness = viewModelScope.launch { delay(SAVE_DELAY_MS); app.settings.setSmoothness(v) }
    }

    fun start(activity: Activity) = controller.start(activity)
    fun stop() = controller.stop()
    fun retrySpen(activity: Activity) = controller.retrySpen(activity)
    fun retryPc() = controller.retryPc()
    fun center() = controller.centerCursor()

    private companion object {
        const val SAVE_DELAY_MS = 150L
    }
}
