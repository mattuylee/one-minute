package top.mattuy.oneminute.core

/** Single-threaded state machine. All timestamps come from elapsedRealtime, never wall time. */
class WaitSession {
    enum class Phase { WAITING, READY, ALLOWED }
    data class Gate(val id: Long, val packageName: String, val seconds: Int, val deadline: Long,
                    val phase: Phase = Phase.WAITING) {
        fun remainingSeconds(now: Long): Int = ((deadline - now).coerceAtLeast(0) + 999).div(1000).toInt()
    }

    private var nextId = 0L
    private var foreground: String? = null
    var gate: Gate? = null
        private set
    var systemInterrupted = false
        private set

    fun foreground(packageName: String?, seconds: Int?, now: Long) {
        systemInterrupted = false
        tick(now)
        val duration = seconds?.takeIf { it > 0 }?.coerceIn(1, 600)
        if (foreground == packageName && gate?.seconds == duration) return
        foreground = packageName
        gate = if (packageName != null && duration != null) {
            Gate(++nextId, packageName, duration, now + duration * 1000L)
        } else null
    }

    fun tick(now: Long) {
        gate?.takeIf { it.phase == Phase.WAITING && now >= it.deadline }?.let {
            gate = it.copy(phase = Phase.READY)
        }
    }

    fun proceed(id: Long, now: Long): Boolean {
        if (systemInterrupted) return false
        tick(now)
        val current = gate ?: return false
        if (current.id != id || current.phase != Phase.READY) return false
        gate = current.copy(phase = Phase.ALLOWED)
        return true
    }

    /** System UI hides the gate without changing its deadline or revoking granted access. */
    fun systemInterruption() {
        systemInterrupted = true
    }

    fun clear() { foreground = null; gate = null; systemInterrupted = false }
}
