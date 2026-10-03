package top.mattuy.oneminute.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Native view shared by the service and the in-app preview. No timer or business state lives here. */
class WaitOverlayView(
    context: Context,
    private val appLabel: String,
    appIcon: Drawable?,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) : FrameLayout(context) {
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val backgroundColor: Int = Color.parseColor(if (dark) "#1D1B22" else "#F7F6FB")
    private val ink = Color.parseColor(if (dark) "#F4F1FA" else "#302A40")
    private val muted = Color.parseColor(if (dark) "#B8B0C5" else "#7C748B")
    private val accent = Color.parseColor(if (dark) "#BBAAF3" else "#7563C7")
    private val track = Color.parseColor(if (dark) "#393343" else "#EAE5F4")
    private val counter: TextView
    private val counterHint: TextView
    private val ring: RingView
    private val continueButton: Button
    private val helper: TextView
    private var lastSeconds = -1
    private var lastTotal = -1

    init {
        // Both this view and the separate system-bar backdrop use the same explicit palette.
        isForceDarkAllowed = false
        setBackgroundColor(backgroundColor)
        isClickable = true
        val scroll = ScrollView(context).apply { isFillViewport = true; clipToPadding = false }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(30), dp(28), dp(24))
        }
        scroll.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(text("稍 等", 15, muted, bold = true), params(bottom = 30))
        if (appIcon != null) {
            column.addView(ImageView(context).apply {
                setImageDrawable(appIcon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(16) })
        }
        column.addView(text("正在打开 $appLabel", 15, muted), params(bottom = 26))
        val circle = FrameLayout(context)
        ring = RingView(context, accent, track)
        circle.addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        counter = text("60", 62, ink, bold = false).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        circle.addView(counter, LayoutParams(LayoutParams.MATCH_PARENT, dp(92), Gravity.CENTER).apply { topMargin = -dp(12) })
        counterHint = text("秒后再决定", 13, muted)
        circle.addView(counterHint, LayoutParams(LayoutParams.MATCH_PARENT, dp(28), Gravity.CENTER).apply { topMargin = dp(80) })
        column.addView(circle, LinearLayout.LayoutParams(dp(220), dp(220)).apply { bottomMargin = dp(28) })
        column.addView(text("现在，真的需要打开吗？", 23, ink, bold = true), params(bottom = 12))
        helper = text("放慢一点。\n给自己一次重新选择的机会。", 15, muted).apply {
            setLineSpacing(dp(5).toFloat(), 1f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        column.addView(helper, params(bottom = 32))
        continueButton = Button(context).apply {
            isAllCaps = false; textSize = 16f; minHeight = dp(56)
            background = rounded(track); setTextColor(muted)
            setOnClickListener { onContinue() }
        }
        column.addView(continueButton, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        val cancel = Button(context).apply {
            text = "先不打开了"; textSize = 15f; isAllCaps = false
            setTextColor(accent); background = rounded(Color.TRANSPARENT)
            setOnClickListener { onCancel() }
        }
        column.addView(cancel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(8) })
        update(60, 60)
    }

    fun update(remaining: Int, total: Int) {
        if (remaining == lastSeconds && total == lastTotal) return
        lastSeconds = remaining
        lastTotal = total
        counter.text = if (remaining > 0) remaining.toString() else "✓"
        counterHint.text = if (remaining > 0) "秒后再决定" else "准备好了"
        counter.contentDescription = if (remaining > 0) "还需等待 $remaining 秒" else "等待结束"
        ring.fraction = 1f - remaining.toFloat() / total.coerceAtLeast(1)
        continueButton.isEnabled = remaining == 0
        continueButton.text = if (remaining == 0) "继续打开 $appLabel" else "还有 $remaining 秒"
        continueButton.background = rounded(if (remaining == 0) accent else track)
        continueButton.setTextColor(if (remaining == 0) Color.parseColor(if (dark) "#211A31" else "#FFFFFF") else muted)
        if (remaining == 0) {
            helper.text = "等待结束。\n现在，由你决定。"
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(28).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class RingView(context: Context, private val accent: Int, private val track: Int) : View(context) {
        var fraction: Float = 0f
            set(value) { field = value.coerceIn(0f, 1f); invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        override fun onDraw(canvas: Canvas) {
            val inset = 5 * resources.displayMetrics.density
            paint.strokeWidth = 4 * resources.displayMetrics.density
            val rect = RectF(inset, inset, width - inset, height - inset)
            paint.color = track; canvas.drawArc(rect, 0f, 360f, false, paint)
            paint.color = accent; canvas.drawArc(rect, -90f, 360f * fraction, false, paint)
        }
    }
}
