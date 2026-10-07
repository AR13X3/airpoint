package io.github.ar13x3.airpoint.spen

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SpenSources {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): SpenSource = SimulatedSpenSource()
}

/**
 * A stand-in pen for builds without Samsung's SDK: after a short "connecting" beat it traces
 * a slow figure-eight and clicks now and then, so every screen can be developed and demoed
 * on any phone or emulator.
 */
private class SimulatedSpenSource : SpenSource {
    override val isSimulated = true

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: SpenSource.Listener? = null
    private var t = 0.0
    private var tick = 0

    override fun connect(activity: Activity, listener: SpenSource.Listener) {
        disconnect()
        this.listener = listener
        val th = HandlerThread("simulated-spen").also { it.start() }
        thread = th
        handler = Handler(th.looper).apply {
            postDelayed({
                listener.onConnected(hasButton = true, hasAirMotion = true)
                post(frame)
            }, CONNECT_DELAY_MS)
        }
    }

    private val frame: Runnable = object : Runnable {
        override fun run() {
            val l = listener ?: return
            // Velocity of a Lissajous figure-eight (x = sin t, y = sin 2t / 2), in S Pen units.
            val dt = 2 * PI / (FRAMES_PER_LOOP)
            t += dt
            l.onAirMotion((cos(t) * dt * SCALE).toFloat(), (-cos(2 * t) * dt * SCALE).toFloat())
            tick++
            if (tick % CLICK_EVERY == 0) l.onButton(true)
            if (tick % CLICK_EVERY == 6) l.onButton(false)
            handler?.postDelayed(this, FRAME_MS)
        }
    }

    override fun disconnect() {
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        handler = null
        thread = null
        listener = null
    }

    private companion object {
        const val CONNECT_DELAY_MS = 900L
        const val FRAME_MS = 16L
        const val FRAMES_PER_LOOP = 300.0
        const val SCALE = 0.45
        const val CLICK_EVERY = 210
    }
}
