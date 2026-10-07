package io.github.ar13x3.airpoint.session

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import io.github.ar13x3.airpoint.core.LinkPhase
import io.github.ar13x3.airpoint.core.LinkProblem
import io.github.ar13x3.airpoint.core.LinkState
import io.github.ar13x3.airpoint.core.PadEvent
import io.github.ar13x3.airpoint.core.PairedPc
import io.github.ar13x3.airpoint.core.PcEndpoint
import io.github.ar13x3.airpoint.core.SessionState
import io.github.ar13x3.airpoint.core.SettingsRepository
import io.github.ar13x3.airpoint.core.SpenPhase
import io.github.ar13x3.airpoint.core.SpenProblem
import io.github.ar13x3.airpoint.core.SpenState
import io.github.ar13x3.airpoint.core.UserSettings
import io.github.ar13x3.airpoint.net.Discovery
import io.github.ar13x3.airpoint.net.PairOutcome
import io.github.ar13x3.airpoint.net.PcLink
import io.github.ar13x3.airpoint.net.pairWithPin
import io.github.ar13x3.airpoint.service.PointerService
import io.github.ar13x3.airpoint.spen.SpenSource
import io.github.ar13x3.airpoint.spen.SpenSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns a pointing session: the S Pen connection, the link to the PC, automatic reconnects,
 * and the translation from pen input to cursor events. The UI observes [state] and [pad].
 * The foreground [PointerService] only keeps the process alive while a session is active.
 */
class AirpointController(
    private val app: Application,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _pad = MutableSharedFlow<PadEvent>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pad: SharedFlow<PadEvent> = _pad.asSharedFlow()

    private val spen: SpenSource = SpenSources.create(app)
    val isSimulated: Boolean get() = spen.isSimulated

    @Volatile private var settings = UserSettings()
    @Volatile private var link: PcLink? = null
    private var target: PairedPc? = null
    private var reconnectJob: Job? = null
    private var attempts = 0

    val deviceName: String by lazy {
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }

    init {
        scope.launch {
            settingsRepo.settings.collect { s ->
                val smoothingChanged = s.smoothingAlpha != settings.smoothingAlpha
                settings = s
                if (smoothingChanged) link?.sendSmoothing(s.smoothingAlpha)
            }
        }
    }

    // ---- session lifecycle ------------------------------------------------------------------

    /** Start pointing. The caller must already hold BLUETOOTH_CONNECT. */
    fun start(activity: Activity) {
        if (_state.value.active) return
        _state.value = SessionState(active = true)
        PointerService.start(app)
        connectSpen(activity)
        scope.launch { connectToPreferredPc() }
    }

    fun stop() {
        reconnectJob?.cancel()
        link?.close()
        link = null
        spen.disconnect()
        _state.value = SessionState(active = false)
        PointerService.stop(app)
    }

    fun retrySpen(activity: Activity) {
        if (!_state.value.active) return
        spen.disconnect()
        connectSpen(activity)
    }

    fun retryPc() {
        if (!_state.value.active) return
        attempts = 0
        scope.launch { connectToPreferredPc() }
    }

    /** Switch to [pc] (and remember it as the default). */
    fun usePc(pc: PairedPc) {
        scope.launch {
            settingsRepo.touch(pc.id, pc.host, pc.port, pc.name)
            if (_state.value.active) {
                attempts = 0
                openLink(pc)
            }
        }
    }

    /** Apply slider changes to the live session immediately (persistence happens separately). */
    fun previewTuning(sensitivity: Float? = null, smoothness: Float? = null) {
        val before = settings
        settings = before.copy(
            sensitivity = sensitivity ?: before.sensitivity,
            smoothness = smoothness ?: before.smoothness,
        )
        if (settings.smoothingAlpha != before.smoothingAlpha) link?.sendSmoothing(settings.smoothingAlpha)
    }

    fun centerCursor(): Boolean {
        val l = link ?: return false
        l.sendCenter()
        _pad.tryEmit(PadEvent.Center)
        return _state.value.link.phase == LinkPhase.Connected
    }

    suspend fun pair(endpoint: PcEndpoint, pin: String): PairOutcome {
        val outcome = pairWithPin(endpoint, pin, settingsRepo.deviceId(), deviceName)
        if (outcome is PairOutcome.Paired) {
            val pc = PairedPc(outcome.pcId, outcome.name, endpoint.host, endpoint.port, outcome.token, System.currentTimeMillis())
            settingsRepo.savePaired(pc)
            if (_state.value.active) {
                attempts = 0
                openLink(pc)
            }
        }
        return outcome
    }

    // ---- S Pen --------------------------------------------------------------------------------

    private fun connectSpen(activity: Activity) {
        _state.update { it.copy(spen = SpenState(SpenPhase.Connecting)) }
        spen.connect(activity, spenListener)
    }

    private var lastPressAt = 0L
    private var swallowNextRelease = false

    private val spenListener = object : SpenSource.Listener {
        override fun onConnected(hasButton: Boolean, hasAirMotion: Boolean) =
            _state.update { it.copy(spen = SpenState(SpenPhase.Ready, null, hasButton, hasAirMotion)) }

        override fun onConnectFailed(problem: SpenProblem) =
            _state.update { it.copy(spen = SpenState(SpenPhase.Failed, problem)) }

        override fun onDisconnected() = _state.update {
            if (it.active) it.copy(spen = SpenState(SpenPhase.Failed, SpenProblem.Lost)) else it
        }

        override fun onButton(pressed: Boolean) {
            synchronized(this) { handleButton(pressed) }
        }

        private fun handleButton(pressed: Boolean) {
            val l = link
            if (pressed) {
                val now = SystemClock.uptimeMillis()
                if (settings.doublePressCenters && now - lastPressAt <= DOUBLE_PRESS_MS) {
                    // Double press: recenter instead of a second click.
                    lastPressAt = 0L
                    swallowNextRelease = true
                    l?.sendCenter()
                    _pad.tryEmit(PadEvent.Center)
                    return
                }
                lastPressAt = now
                l?.sendButton(true)
                _pad.tryEmit(PadEvent.Button(true))
            } else {
                if (swallowNextRelease) {
                    swallowNextRelease = false
                    return
                }
                l?.sendButton(false)
                _pad.tryEmit(PadEvent.Button(false))
            }
        }

        override fun onAirMotion(dx: Float, dy: Float) {
            val gain = settings.sensitivity * UserSettings.SENSITIVITY_GAIN
            link?.queueMotion(dx * gain, -dy * gain)
            _pad.tryEmit(PadEvent.Motion(dx, -dy))
        }
    }

    // ---- PC link ----------------------------------------------------------------------------

    private suspend fun connectToPreferredPc() {
        val paired = settingsRepo.paired()
        val last = settingsRepo.current().lastPcId
        val pc = paired.firstOrNull { it.id == last } ?: paired.maxByOrNull { it.lastUsed }
        if (pc == null) {
            _state.update { it.copy(link = LinkState(LinkPhase.Failed, null, LinkProblem.NotPaired)) }
            return
        }
        openLink(pc)
    }

    private suspend fun openLink(pc: PairedPc) {
        reconnectJob?.cancel()
        link?.close()
        target = pc
        _state.update {
            it.copy(link = LinkState(if (attempts == 0) LinkPhase.Connecting else LinkPhase.Reconnecting, pc.endpoint))
        }
        lateinit var created: PcLink
        created = PcLink(settingsRepo.deviceId(), deviceName, object : PcLink.Listener {
            override fun onWelcome(pcId: String, name: String, token: String?) {
                if (link !== created) return
                attempts = 0
                _state.update { it.copy(link = LinkState(LinkPhase.Connected, pc.endpoint.copy(name = name))) }
                created.sendSmoothing(settings.smoothingAlpha)
                scope.launch { settingsRepo.touch(pc.id, pc.host, pc.port, name) }
            }

            override fun onRejected(code: String, retryAfterSeconds: Int) {
                if (link !== created) return
                link = null
                val problem = if (code == "unsupported_version") LinkProblem.Incompatible else LinkProblem.NotPaired
                if (problem == LinkProblem.NotPaired) scope.launch { settingsRepo.forget(pc.id) }
                _state.update { it.copy(link = LinkState(LinkPhase.Failed, pc.endpoint, problem)) }
            }

            override fun onClosed(wasWelcomed: Boolean) {
                if (link !== created) return
                link = null
                if (_state.value.active) scheduleReconnect(pc)
            }
        })
        link = created
        created.connect(pc.endpoint, token = pc.token, pin = null)
    }

    private fun scheduleReconnect(pc: PairedPc) {
        attempts++
        val wait = BACKOFF_MS[(attempts - 1).coerceAtMost(BACKOFF_MS.lastIndex)]
        _state.update {
            it.copy(link = LinkState(LinkPhase.Reconnecting, pc.endpoint, LinkProblem.Unreachable, SystemClock.elapsedRealtime() + wait))
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait)
            // From the second retry on, the PC may have a new IP: ask the network.
            val moved = if (attempts >= 2) Discovery.find(pc.id) else null
            val next = if (moved != null && moved.host != pc.host) {
                pc.copy(host = moved.host, port = moved.port).also { settingsRepo.touch(it.id, it.host, it.port, moved.name) }
            } else pc
            if (_state.value.active) openLink(next)
        }
    }

    private companion object {
        const val DOUBLE_PRESS_MS = 400L
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 12_000)
    }
}
