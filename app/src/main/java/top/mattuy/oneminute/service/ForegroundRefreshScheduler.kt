package top.mattuy.oneminute.service

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** At most one queued scan; continuous events cannot postpone the pending scan forever. */
internal class ForegroundRefreshScheduler(private val handler: Handler, private val refresh: () -> Unit) {
    private var lastScan: Long? = null
    private var pending = false
    private val scan = Runnable {
        pending = false
        lastScan = SystemClock.uptimeMillis()
        refresh()
    }

    fun request() {
        if (pending) return
        val delay = lastScan?.let { (it + 100L - SystemClock.uptimeMillis()).coerceAtLeast(0) } ?: 0L
        pending = true
        handler.postDelayed(scan, delay)
    }

    fun cancel() { handler.removeCallbacks(scan); pending = false; lastScan = null }

    companion object {
        fun relevant(type: Int, changes: Int): Boolean = when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Bounds/title/accessibility-focus-only updates cannot change our foreground policy.
                val mask = AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
                    AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or
                    AccessibilityEvent.WINDOWS_CHANGE_LAYER or AccessibilityEvent.WINDOWS_CHANGE_PARENT or
                    AccessibilityEvent.WINDOWS_CHANGE_CHILDREN or AccessibilityEvent.WINDOWS_CHANGE_PIP
                changes == 0 || changes and mask != 0
            }
            else -> false
        }
    }
}
