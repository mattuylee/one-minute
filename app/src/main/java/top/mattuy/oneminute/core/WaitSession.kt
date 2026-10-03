package top.mattuy.oneminute.core

/** Single-threaded state machine. All timestamps come from elapsedRealtime, never wall time. */
class WaitSession {
    enum class Phase { WAITING, READY, ALLOWED }
    data class Gate(val id: Long, val packageName: String, val seconds: Int, val deadline: Long,
                    val phase: Phase = Phase.WAITING) {
        fun remainingSeconds(now: Long): Int = ((deadline - now).coerceAtLeast(0) + 999).div(1000).toInt()
        fun nextTickDelay(now: Long): Long = ((deadline - now).coerceAtLeast(1) - 1) % 1000 + 1
    }

    private var nextId = 0L
    private var foreground: String? = null
    private val allowedAwaySince = mutableMapOf<String, Long>()
    private var returnGraceMillis = 5 * 60 * 1000L

    fun setReturnGraceMinutes(minutes: Int) {
        require(minutes in 1..60)
        returnGraceMillis = minutes * 60 * 1000L
    }
    var gate: Gate? = null
        private set
    var systemInterrupted = false
        private set

    fun foreground(packageName: String?, seconds: Int?, now: Long) {
        systemInterrupted = false
        tick(now)
        val duration = seconds?.takeIf { it > 0 }?.coerceIn(1, 600)
        if (foreground == packageName && gate?.seconds == duration) return
        if (foreground == packageName && duration != null && gate?.phase == Phase.ALLOWED) {
            gate = gate?.copy(seconds = duration)
            return
        }
        if (foreground != packageName) leave(now)
        expireGrants(now)
        foreground = packageName
        gate = if (packageName != null && duration != null) {
            val allowed = allowedAwaySince.remove(packageName)?.let { now - it <= returnGraceMillis } == true
            Gate(++nextId, packageName, duration, if (allowed) now else now + duration * 1000L,
                if (allowed) Phase.ALLOWED else Phase.WAITING)
        } else {
            allowedAwaySince.remove(packageName)
            null
        }
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

    /** Only completed waits get a grace period, measured from the actual departure. */
    fun leave(now: Long) {
        gate?.takeIf { it.phase == Phase.ALLOWED }?.let {
            allowedAwaySince[it.packageName] = now
        }
        foreground = null; gate = null; systemInterrupted = false
        expireGrants(now)
    }

    fun retainSelectedPackages(packages: Set<String>) {
        allowedAwaySince.keys.retainAll(packages)
        if (gate?.packageName !in packages) gate = null
    }

    /** Service restarts never restore access grants. */
    fun clear() { foreground = null; gate = null; systemInterrupted = false; allowedAwaySince.clear() }

    private fun expireGrants(now: Long) {
        allowedAwaySince.entries.removeAll { now - it.value > returnGraceMillis }
    }
}
