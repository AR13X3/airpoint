package io.github.ar13x3.airpoint.spen

import android.app.Activity
import io.github.ar13x3.airpoint.core.SpenProblem

/**
 * Where S Pen input comes from. The real implementation (src/spen) wraps Samsung's S Pen
 * Remote SDK. Builds without the SDK use a simulated pen (src/simulated). Both provide
 * `SpenSources.create()`.
 *
 * All callbacks may arrive on any thread.
 */
interface SpenSource {
    val isSimulated: Boolean

    /** The SDK binds to a service through the Activity; [disconnect] must follow on the same one. */
    fun connect(activity: Activity, listener: Listener)

    fun disconnect()

    interface Listener {
        fun onConnected(hasButton: Boolean, hasAirMotion: Boolean)
        fun onConnectFailed(problem: SpenProblem)
        fun onDisconnected()
        fun onButton(pressed: Boolean)
        fun onAirMotion(dx: Float, dy: Float)
    }
}
