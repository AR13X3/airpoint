package io.github.ar13x3.airpoint.core

/** A computer we can talk to. [id] is null for a manually entered address we haven't met yet. */
data class PcEndpoint(
    val id: String?,
    val name: String,
    val host: String,
    val port: Int,
)

/** A computer this phone has paired with, and the token that proves it. */
data class PairedPc(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val lastUsed: Long,
) {
    val endpoint get() = PcEndpoint(id, name, host, port)
}

/** A computer that answered a discovery broadcast. */
data class DiscoveredPc(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val version: String,
) {
    val endpoint get() = PcEndpoint(id, name, host, port)
}

// ---- S Pen ------------------------------------------------------------------------------

enum class SpenPhase { Off, Connecting, Ready, Failed }

enum class SpenProblem {
    /** Nearby devices (BLUETOOTH_CONNECT) permission not granted. */
    PermissionDenied,

    /** Not a Samsung device with a Bluetooth S Pen. */
    Unsupported,

    /** The pen is busy (Air actions or another app), asleep, or Bluetooth is off. */
    ConnectionFailed,

    /** The pen dropped while we were using it. */
    Lost,
    Unknown,
}

data class SpenState(
    val phase: SpenPhase = SpenPhase.Off,
    val problem: SpenProblem? = null,
    val hasButton: Boolean = false,
    val hasAirMotion: Boolean = false,
)

// ---- PC link ----------------------------------------------------------------------------

enum class LinkPhase { Idle, Connecting, Connected, Reconnecting, Failed }

enum class LinkProblem {
    /** No route to the PC: wrong network, PC asleep, app not running, or firewall. */
    Unreachable,

    /** The PC doesn't know this phone (never paired, or it was forgotten on the PC). */
    NotPaired,

    /** The PC runs an incompatible protocol version. */
    Incompatible,
}

data class LinkState(
    val phase: LinkPhase = LinkPhase.Idle,
    val pc: PcEndpoint? = null,
    val problem: LinkProblem? = null,
    /** Next automatic retry, as an elapsedRealtime timestamp, while [phase] is Reconnecting. */
    val retryAt: Long = 0L,
)

data class SessionState(
    /** The user has pressed Start and hasn't stopped. */
    val active: Boolean = false,
    val spen: SpenState = SpenState(),
    val link: LinkState = LinkState(),
) {
    val pointing get() = active && spen.phase == SpenPhase.Ready && link.phase == LinkPhase.Connected
}

/** Raw S Pen input, mirrored to the UI's live preview. */
sealed interface PadEvent {
    data class Motion(val dx: Float, val dy: Float) : PadEvent
    data class Button(val pressed: Boolean) : PadEvent
    data object Center : PadEvent
}
