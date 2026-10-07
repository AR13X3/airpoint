package io.github.ar13x3.airpoint.ui.pair

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.core.DiscoveredPc
import io.github.ar13x3.airpoint.core.PairedPc
import io.github.ar13x3.airpoint.core.PcEndpoint
import io.github.ar13x3.airpoint.net.Discovery
import io.github.ar13x3.airpoint.net.PairOutcome
import io.github.ar13x3.airpoint.ui.components.PinState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PairStep {
    data object Choose : PairStep
    data class Pin(val endpoint: PcEndpoint) : PairStep
}

sealed interface PinMessage {
    data object Wrong : PinMessage
    data class Locked(val until: Long) : PinMessage
    data object Unreachable : PinMessage
    data object Incompatible : PinMessage
}

class PairViewModel(app: AirpointApp) : ViewModel() {
    private val controller = app.controller

    val found = Discovery.scan()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_500), emptyList())
    val paired = app.settings.pairedPcs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var step by mutableStateOf<PairStep>(PairStep.Choose)
        private set
    var pin by mutableStateOf("")
        private set
    var pinState by mutableStateOf(PinState.Idle)
        private set
    var message by mutableStateOf<PinMessage?>(null)
        private set

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    fun choose(pc: DiscoveredPc) {
        val known = paired.value.firstOrNull { it.id == pc.id }
        if (known != null) use(known.copy(host = pc.host, port = pc.port, name = pc.name)) else openPin(pc.endpoint)
    }

    fun use(pc: PairedPc) {
        controller.usePc(pc)
        _done.trySend(Unit)
    }

    fun manual(host: String, port: Int) = openPin(PcEndpoint(null, host, host, port))

    private fun openPin(endpoint: PcEndpoint) {
        pin = ""
        pinState = PinState.Idle
        message = null
        step = PairStep.Pin(endpoint)
    }

    fun back() {
        step = PairStep.Choose
    }

    fun onPin(value: String) {
        val locked = (message as? PinMessage.Locked)?.let { SystemClock.elapsedRealtime() < it.until } == true
        if (locked) return
        if (pinState == PinState.Error) {
            pinState = PinState.Idle
            message = null
        }
        pin = value
        if (value.length == 6) submit()
    }

    private fun submit() {
        val endpoint = (step as? PairStep.Pin)?.endpoint ?: return
        pinState = PinState.Checking
        viewModelScope.launch {
            when (val r = controller.pair(endpoint, pin)) {
                is PairOutcome.Paired -> {
                    step = PairStep.Pin(endpoint.copy(id = r.pcId, name = r.name))
                    pinState = PinState.Success
                    delay(1_150)
                    _done.trySend(Unit)
                }
                PairOutcome.WrongPin -> fail(PinMessage.Wrong)
                is PairOutcome.LockedOut -> fail(PinMessage.Locked(SystemClock.elapsedRealtime() + r.seconds * 1000L))
                PairOutcome.Unreachable -> fail(PinMessage.Unreachable)
                PairOutcome.Incompatible -> fail(PinMessage.Incompatible)
            }
        }
    }

    private suspend fun fail(m: PinMessage) {
        pinState = PinState.Error
        message = m
        delay(500)
        pin = ""
    }
}
