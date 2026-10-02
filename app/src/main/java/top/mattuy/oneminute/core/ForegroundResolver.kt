package top.mattuy.oneminute.core

/** Pure policy: overlay and keyboard windows must never be mistaken for a new application. */
object ForegroundResolver {
    enum class Kind { APPLICATION, SYSTEM, KEYBOARD, OVERLAY, OTHER }
    data class Window(val kind: Kind, val focused: Boolean, val active: Boolean, val packageName: String?)
    sealed interface Target {
        data class App(val packageName: String) : Target
        data object SystemInterruption : Target
        data object Unknown : Target
    }
    fun resolve(windows: List<Window>): Target {
        val focused = windows.firstOrNull { it.focused && it.kind != Kind.OVERLAY }
        if (focused?.kind == Kind.SYSTEM) return Target.SystemInterruption
        val app = windows.firstOrNull { it.kind == Kind.APPLICATION && it.focused }
            ?: windows.firstOrNull { it.kind == Kind.APPLICATION && it.active }
        return app?.packageName?.takeIf { it.isNotBlank() }?.let(Target::App) ?: Target.Unknown
    }
}
