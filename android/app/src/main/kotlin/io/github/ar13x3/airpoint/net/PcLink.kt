package io.github.ar13x3.airpoint.net

import io.github.ar13x3.airpoint.core.PcEndpoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.coroutines.resume

const val PROTOCOL_VERSION = 1
const val DEFAULT_PORT = 8765

/**
 * One WebSocket session with the Airpoint desktop app (see docs/PROTOCOL.md).
 *
 * The first frame is a `hello` carrying either a saved token or a PIN. Nothing else is sent
 * until the PC answers `welcome`. Motion is coalesced: deltas accumulate and are flushed on
 * a fixed 12 ms timer, so a burst of S Pen events can't back up the TCP queue and make the
 * cursor trail behind your hand. Nagle's algorithm is disabled for the same reason.
 */
class PcLink(
    private val deviceId: String,
    private val deviceName: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onWelcome(pcId: String, name: String, token: String?)

        /** The PC refused us: pairing_required, bad_pin, locked_out, unsupported_version. */
        fun onRejected(code: String, retryAfterSeconds: Int)

        /** The connection ended (or never opened) without an explicit rejection. */
        fun onClosed(wasWelcomed: Boolean)
    }

    private var socket: WebSocket? = null
    @Volatile private var welcomed = false
    @Volatile private var finished = false

    private val motionLock = Any()
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var flushTask: ScheduledFuture<*>? = null

    fun connect(endpoint: PcEndpoint, token: String?, pin: String?) {
        check(socket == null) { "PcLink is single-use" }
        val request = Request.Builder().url("ws://${endpoint.host}:${endpoint.port}").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject()
                    .put("type", "hello").put("v", PROTOCOL_VERSION)
                    .put("device", deviceName).put("device_id", deviceId)
                token?.let { hello.put("token", it) }
                pin?.let { hello.put("pin", it) }
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("type")) {
                    "welcome" -> {
                        welcomed = true
                        flushTask = flusher.scheduleAtFixedRate({ flushMotion() }, FLUSH_MS, FLUSH_MS, TimeUnit.MILLISECONDS)
                        listener.onWelcome(
                            msg.optString("pc_id"),
                            msg.optString("name").ifBlank { endpoint.name },
                            msg.optString("token").ifBlank { null },
                        )
                    }
                    "error" -> finish { listener.onRejected(msg.optString("code"), msg.optInt("retry_after")) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                finish { listener.onClosed(welcomed) }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                finish { listener.onClosed(welcomed) }
        })
    }

    /** Report exactly once, however the session ends. */
    private fun finish(report: () -> Unit) {
        synchronized(this) {
            if (finished) return
            finished = true
        }
        stopFlushing()
        report()
    }

    /** Close quietly: the listener is not told (the caller already knows). */
    fun close() {
        synchronized(this) { finished = true }
        stopFlushing()
        socket?.close(1000, "bye")
    }

    fun queueMotion(dx: Float, dy: Float) {
        if (!welcomed) return
        synchronized(motionLock) {
            pendingDx += dx
            pendingDy += dy
        }
    }

    private fun flushMotion() {
        val dx: Float
        val dy: Float
        synchronized(motionLock) {
            dx = pendingDx; dy = pendingDy
            pendingDx = 0f; pendingDy = 0f
        }
        if (dx == 0f && dy == 0f) return
        send(JSONObject().put("type", "motion").put("dx", dx.toDouble()).put("dy", dy.toDouble()))
    }

    private fun stopFlushing() {
        flushTask?.cancel(false)
        flushTask = null
        synchronized(motionLock) { pendingDx = 0f; pendingDy = 0f }
    }

    /** Buttons are rare and time-sensitive: sent immediately, never coalesced. */
    fun sendButton(pressed: Boolean) =
        send(JSONObject().put("type", "button").put("action", if (pressed) "down" else "up"))

    fun sendCenter() {
        synchronized(motionLock) { pendingDx = 0f; pendingDy = 0f }
        send(JSONObject().put("type", "center"))
    }

    fun sendSmoothing(alpha: Float) =
        send(JSONObject().put("type", "config").put("smooth_alpha", alpha.toDouble()))

    private fun send(json: JSONObject): Boolean = welcomed && socket?.send(json.toString()) == true

    companion object {
        private const val FLUSH_MS = 12L // ~83 frames/s

        private val flusher = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "airpoint-motion").apply { isDaemon = true }
        }

        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .socketFactory(NoDelaySocketFactory())
            .build()
    }
}

sealed interface PairOutcome {
    data class Paired(val pcId: String, val name: String, val token: String) : PairOutcome
    data object WrongPin : PairOutcome
    data class LockedOut(val seconds: Int) : PairOutcome
    data object Incompatible : PairOutcome
    data object Unreachable : PairOutcome
}

/** Open a throwaway session with [pin] to obtain a token, then close it. */
suspend fun pairWithPin(endpoint: PcEndpoint, pin: String, deviceId: String, deviceName: String): PairOutcome =
    withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { cont ->
            lateinit var link: PcLink
            link = PcLink(deviceId, deviceName, object : PcLink.Listener {
                override fun onWelcome(pcId: String, name: String, token: String?) {
                    link.close()
                    val outcome = if (token != null) PairOutcome.Paired(pcId, name, token) else PairOutcome.Incompatible
                    if (cont.isActive) cont.resume(outcome)
                }

                override fun onRejected(code: String, retryAfterSeconds: Int) {
                    val outcome = when (code) {
                        "bad_pin", "pairing_required" -> PairOutcome.WrongPin
                        "locked_out" -> PairOutcome.LockedOut(retryAfterSeconds.coerceAtLeast(1))
                        else -> PairOutcome.Incompatible
                    }
                    if (cont.isActive) cont.resume(outcome)
                }

                override fun onClosed(wasWelcomed: Boolean) {
                    if (cont.isActive) cont.resume(PairOutcome.Unreachable)
                }
            })
            cont.invokeOnCancellation { link.close() }
            link.connect(endpoint, token = null, pin = pin)
        }
    } ?: PairOutcome.Unreachable

/** SocketFactory that disables Nagle's algorithm on every socket OkHttp creates. */
private class NoDelaySocketFactory : SocketFactory() {
    private val delegate = getDefault()
    private fun tuned(s: Socket) = s.apply { tcpNoDelay = true }

    override fun createSocket(): Socket = tuned(delegate.createSocket())
    override fun createSocket(host: String?, port: Int): Socket = tuned(delegate.createSocket(host, port))
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        tuned(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress?, port: Int): Socket = tuned(delegate.createSocket(host, port))
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        tuned(delegate.createSocket(address, port, localAddress, localPort))
}
