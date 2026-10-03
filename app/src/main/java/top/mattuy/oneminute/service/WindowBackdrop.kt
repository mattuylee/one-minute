package top.mattuy.oneminute.service

/** A non-interactive backdrop follows the app window, underneath the real system bars. */
internal class WindowBackdrop(private val createLayer: (Int) -> Layer) {
    data class Target(val windowId: Int, val width: Int, val height: Int)

    interface Layer {
        fun show(width: Int, height: Int, color: Int)
        fun hide()
        fun close()
    }

    private var layer: Layer? = null
    private var target: Target? = null
    private var color: Int? = null
    private var visible = false

    fun show(next: Target, backgroundColor: Int) {
        if (next.width <= 0 || next.height <= 0) { hide(); return }
        if (target?.windowId != next.windowId) {
            close()
            layer = createLayer(next.windowId)
        }
        if (visible && target == next && color == backgroundColor) return
        layer!!.show(next.width, next.height, backgroundColor)
        target = next
        color = backgroundColor
        visible = true
    }

    fun hide() {
        if (!visible) return
        layer?.hide()
        visible = false
    }

    fun close() {
        val previous = layer
        layer = null; target = null; color = null; visible = false
        previous?.close()
    }
}
