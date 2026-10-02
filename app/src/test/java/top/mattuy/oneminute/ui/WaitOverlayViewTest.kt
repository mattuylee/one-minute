package top.mattuy.oneminute.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WaitOverlayViewTest {
    @Test fun `waiting disables continue but keeps cancellation available`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var continued = false
        var cancelled = false
        val view = WaitOverlayView(activity, "哔哩哔哩", null, { continued = true }, { cancelled = true })
        activity.setContentView(view)
        view.update(60, 60)
        val buttons = descendants(view).filterIsInstance<Button>()
        val proceed = buttons.first { it.text.toString().startsWith("还有") }
        val cancel = buttons.first { it.text == "先不打开了" }
        assertFalse(proceed.isEnabled)
        assertTrue(cancel.isEnabled)
        assertFalse(continued)
        capture(view, "waiting-light")
        cancel.performClick()
        assertTrue(cancelled)
        view.update(0, 60)
        assertTrue(proceed.isEnabled)
        proceed.performClick()
        assertTrue(continued)
        capture(view, "ready-light")
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp-night-xhdpi")
    fun `dark mode waiting screen renders`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = WaitOverlayView(controller.get(), "哔哩哔哩", null, {}, {})
        controller.get().setContentView(view)
        view.update(37, 60)
        capture(view, "waiting-dark")
        controller.pause().stop().destroy()
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun capture(view: View, name: String) {
        val width = 822
        val height = 1782
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/ui/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
