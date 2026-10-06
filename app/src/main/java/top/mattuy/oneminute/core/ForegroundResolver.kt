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
    fun resolve(windows: List<Window>, packageAt: (Int) -> String? = { windows[it].packageName }): Target {
        val focused = windows.firstOrNull { it.focused && it.kind != Kind.OVERLAY }
        if (focused?.kind == Kind.SYSTEM) {
            return Target.SystemInterruption
        }
        val focusedApp = windows.indexOfFirst { it.kind == Kind.APPLICATION && it.focused }
        val index = if (focusedApp >= 0) focusedApp else windows.indexOfFirst { it.kind == Kind.APPLICATION && it.active }
        if (index < 0) return Target.Unknown
        val packageName = packageAt(index)
        // Some system surfaces are reported as application windows instead of TYPE_SYSTEM.
        if (packageName == "com.android.systemui") return Target.SystemInterruption
        return packageName?.takeIf { it.isNotBlank() }?.let(Target::App) ?: Target.Unknown
    }
}
