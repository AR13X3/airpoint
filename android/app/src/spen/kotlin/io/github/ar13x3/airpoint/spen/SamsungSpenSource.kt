package io.github.ar13x3.airpoint.spen

import android.app.Activity
import android.content.Context
import android.util.Log
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import io.github.ar13x3.airpoint.core.SpenProblem
import java.lang.ref.WeakReference

object SpenSources {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): SpenSource = SamsungSpenSource()
}

/**
 * Samsung S Pen Remote SDK. Things worth knowing:
 *  - SpenRemote is a process-wide singleton and only one app can hold the pen. If Air actions
 *    or another app owns it, connect() reports CONNECTION_FAILED rather than throwing.
 *  - connect() needs BLUETOOTH_CONNECT granted (Android 12+) and an Activity context.
 *  - disconnect() must get the same Activity, or unbindService throws and the pen stays claimed.
 *  - The SpenUnitManager is only valid after onSuccess.
 */
private class SamsungSpenSource : SpenSource {
    override val isSimulated = false

    private val remote = SpenRemote.getInstance()
    private var unitManager: SpenUnitManager? = null
    private var activityRef: WeakReference<Activity>? = null
    private var listener: SpenSource.Listener? = null

    init {
        remote.setConnectionStateChangeListener { state ->
            if (state == SpenRemote.State.DISCONNECTED || state == SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON) {
                unitManager = null
                listener?.onDisconnected()
            }
        }
    }

    override fun connect(activity: Activity, listener: SpenSource.Listener) {
        this.listener = listener
        if (remote.isConnected) {
            listener.onConnected(hasButton(), hasAirMotion())
            return
        }
        activityRef = WeakReference(activity)
        try {
            remote.connect(activity, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    unitManager = manager
                    register(manager)
                    listener.onConnected(hasButton(), hasAirMotion())
                }

                override fun onFailure(error: Int) {
                    Log.w(TAG, "connect failed: $error")
                    listener.onConnectFailed(
                        when (error) {
                            SpenRemote.Error.UNSUPPORTED_DEVICE -> SpenProblem.Unsupported
                            SpenRemote.Error.CONNECTION_FAILED -> SpenProblem.ConnectionFailed
                            else -> SpenProblem.Unknown
                        }
                    )
                }
            })
        } catch (e: SecurityException) {
            listener.onConnectFailed(SpenProblem.PermissionDenied)
        } catch (e: RuntimeException) {
            // Thrown on devices without the S Pen framework at all.
            Log.w(TAG, "connect threw", e)
            listener.onConnectFailed(SpenProblem.Unsupported)
        }
    }

    override fun disconnect() {
        val activity = activityRef?.get()
        try {
            unitManager?.let { m ->
                m.getUnit(SpenUnit.TYPE_BUTTON)?.let { m.unregisterSpenEventListener(it) }
                m.getUnit(SpenUnit.TYPE_AIR_MOTION)?.let { m.unregisterSpenEventListener(it) }
            }
            if (remote.isConnected && activity != null) remote.disconnect(activity)
        } catch (e: RuntimeException) {
            Log.w(TAG, "disconnect failed: ${e.message}")
        }
        activityRef = null
        unitManager = null
        listener = null
    }

    private fun hasButton() = remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON)
    private fun hasAirMotion() = remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION)

    private fun register(manager: SpenUnitManager) {
        if (hasButton()) {
            manager.getUnit(SpenUnit.TYPE_BUTTON)?.let { unit ->
                manager.registerSpenEventListener(SpenEventListener { e ->
                    listener?.onButton(ButtonEvent(e).action == ButtonEvent.ACTION_DOWN)
                }, unit)
            }
        }
        if (hasAirMotion()) {
            manager.getUnit(SpenUnit.TYPE_AIR_MOTION)?.let { unit ->
                manager.registerSpenEventListener(SpenEventListener { e ->
                    val m = AirMotionEvent(e)
                    listener?.onAirMotion(m.deltaX, m.deltaY)
                }, unit)
            }
        }
    }

    private companion object {
        const val TAG = "SamsungSpen"
    }
}
