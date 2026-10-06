package top.mattuy.oneminute.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceControl
import androidx.annotation.RequiresApi

/** One solid pixel, scaled by the compositor. No View, input window, animation or polling. */
@RequiresApi(34)
internal class SurfaceBackdropLayer(service: AccessibilityService, windowId: Int) : WindowBackdrop.Layer {
    private val control = SurfaceControl.Builder()
        .setName("稍等 · 状态栏背景")
        .setBufferSize(1, 1)
        .setFormat(PixelFormat.RGBA_8888)
        .setOpaque(true)
        .setHidden(true)
        .build()
    private var surface: Surface? = null
    private var color: Int? = null

    init {
        try {
            surface = Surface(control)
            // A child of the application's surface stays below the system bars. Attaching to
            // the display instead would place this above the clock and privacy indicators.
            service.attachAccessibilityOverlayToWindow(windowId, control)
        } catch (error: RuntimeException) {
            close()
            throw error
        }
    }

    override fun show(width: Int, height: Int, color: Int) {
        if (this.color != color) {
            val producer = checkNotNull(surface)
            val canvas = producer.lockCanvas(null)
            try { canvas.drawColor(color) }
            finally { producer.unlockCanvasAndPost(canvas) }
            this.color = color
        }
        SurfaceControl.Transaction().use {
            it.setLayer(control, Int.MAX_VALUE)
                .setPosition(control, 0f, 0f)
                .setScale(control, width.toFloat(), height.toFloat())
                .setVisibility(control, true)
                .apply()
        }
    }

    override fun hide() {
        SurfaceControl.Transaction().use { it.setVisibility(control, false).apply() }
    }

    override fun close() {
        try {
            if (control.isValid) SurfaceControl.Transaction().use {
                // Hide before detaching: an outstanding asynchronous attach must never
                // bring an old background back over an app after waiting has ended.
                it.setVisibility(control, false).reparent(control, null).apply()
            }
        } finally {
            surface?.release(); surface = null
            control.release()
        }
    }
}
