package top.mattuy.oneminute.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.mattuy.oneminute.core.WaitSession
import top.mattuy.oneminute.core.ForegroundResolver
import top.mattuy.oneminute.data.AppCatalog
import top.mattuy.oneminute.data.SettingsStore
import top.mattuy.oneminute.data.WaitingSettings
import top.mattuy.oneminute.ui.WaitOverlayView

class WaitAccessibilityService : AccessibilityService() {
    private val session = WaitSession()
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var settings: WaitingSettings? = null
    private var protected = emptySet<String>()
    private var overlay: WaitOverlayView? = null
    private var displayedId: Long? = null
    private var receiverRegistered = false
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) leaveForeground()
            else handler.postDelayed({ refreshForeground() }, 150)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (locked()) { leaveForeground(); return }
            session.tick(SystemClock.elapsedRealtime())
            render()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceDiagnostics.install(this)
        ServiceDiagnostics.record(this, "服务连接")
        ServiceStatus.refreshPermission(this)
        scope?.cancel()
        reset()
        protected = AppCatalog.protectedPackages(this)
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        ServiceStatus.error(null)
        ServiceStatus.connected(true)
        scope = CoroutineScope(Job() + Dispatchers.Main.immediate).also { serviceScope ->
            serviceScope.launch {
                try {
                    SettingsStore(this@WaitAccessibilityService).settings.collect {
                        settings = it.copy(packages = it.packages - protected)
                        session.setReturnGraceMinutes(it.returnGraceMinutes)
                        session.retainSelectedPackages(it.packages - protected)
                        refreshForeground()
                    }
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) {
                    Log.e(TAG, "Failed to load waiting rules; interception stopped", error)
                    ServiceDiagnostics.record(this@WaitAccessibilityService, "规则加载或初次窗口处理失败：${error.javaClass.simpleName}")
                    settings = null
                    reset()
                    ServiceStatus.error("规则读取失败，请重新开启无障碍服务")
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || settings == null) return
        refreshForeground()
    }

    /** Only inspect window metadata and the root's package name; never traverse page content. */
    private fun refreshForeground() {
        if (locked()) { leaveForeground(); return }
        val rules = settings ?: return
        val target = ForegroundResolver.resolve(windows.map { window ->
            val kind = when (window.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> ForegroundResolver.Kind.APPLICATION
                AccessibilityWindowInfo.TYPE_SYSTEM -> ForegroundResolver.Kind.SYSTEM
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> ForegroundResolver.Kind.KEYBOARD
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> ForegroundResolver.Kind.OVERLAY
                else -> ForegroundResolver.Kind.OTHER
            }
            ForegroundResolver.Window(kind, window.isFocused, window.isActive,
                if (kind == ForegroundResolver.Kind.APPLICATION) window.root?.packageName?.toString() else null)
        })
        val activePackage = when (target) {
            is ForegroundResolver.Target.App -> target.packageName
            ForegroundResolver.Target.SystemInterruption -> {
                session.systemInterruption()
                hideOverlay()
                return
            }
            ForegroundResolver.Target.Unknown -> return
        }
        session.foreground(activePackage, rules.seconds.takeIf { activePackage in rules.packages }, SystemClock.elapsedRealtime())
        render()
    }

    private fun render() {
        handler.removeCallbacks(tick)
        val gate = session.gate
        if (session.systemInterrupted || gate == null || gate.phase == WaitSession.Phase.ALLOWED) {
            hideOverlay(); return
        }
        if (displayedId != gate.id) {
            hideOverlay()
            val info = try { packageManager.getApplicationInfo(gate.packageName, 0) }
                catch (error: android.content.pm.PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Selected application was removed: ${gate.packageName}", error)
                    session.clear(); return
                }
            val view = WaitOverlayView(this, packageManager.getApplicationLabel(info).toString(),
                packageManager.getApplicationIcon(info),
                onContinue = {
                    // Recheck the focused app before accepting a possibly stale button click.
                    refreshForeground()
                    if (session.proceed(gate.id, SystemClock.elapsedRealtime())) render()
                },
                onCancel = {
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) leaveForeground()
                    else {
                        Log.e(TAG, "System refused GLOBAL_ACTION_HOME while cancelling wait")
                        Toast.makeText(this, "未能返回桌面，请使用系统主页手势", Toast.LENGTH_SHORT).show()
                    }
                })
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE
            ).apply {
                title = "稍等 · 等待页"
                // Keep system bars outside the touchable window, not just outside its content.
                setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setFitInsetsIgnoringVisibility(true)
            }
            try {
                windowManager.addView(view, params)
                overlay = view; displayedId = gate.id
                ServiceStatus.error(null)
            } catch (error: RuntimeException) {
                Log.e(TAG, "Failed to attach waiting overlay", error)
                ServiceDiagnostics.record(this, "遮罩添加失败：${error.javaClass.simpleName}")
                ServiceStatus.error("等待页显示失败，请重新开启无障碍服务")
                performGlobalAction(GLOBAL_ACTION_HOME)
                session.clear()
                return
            }
        }
        overlay?.update(gate.remainingSeconds(SystemClock.elapsedRealtime()), gate.seconds)
        if (gate.phase == WaitSession.Phase.WAITING) handler.postDelayed(tick, 200)
    }

    private fun locked(): Boolean = !getSystemService(PowerManager::class.java).isInteractive ||
        getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun hideOverlay() {
        handler.removeCallbacks(tick)
        overlay?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
        }
        overlay = null; displayedId = null
    }
    private fun reset() { session.clear(); hideOverlay() }
    private fun leaveForeground() { session.leave(SystemClock.elapsedRealtime()); hideOverlay() }
    // This callback interrupts spoken/haptic feedback, not the accessibility connection.
    // We provide neither; preserve the visual gate and grants until a real window change.
    override fun onInterrupt() { ServiceDiagnostics.record(this, "收到反馈中断回调（不是断开连接）") }
    override fun onUnbind(intent: Intent?): Boolean {
        ServiceDiagnostics.record(this, "服务解除绑定")
        cleanup(); ServiceStatus.refreshPermission(this)
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        ServiceDiagnostics.record(this, "服务销毁")
        cleanup(); super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        hideOverlay(); render()
    }
    private fun cleanup() {
        scope?.cancel(); scope = null
        handler.removeCallbacksAndMessages(null)
        reset(); settings = null
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        ServiceStatus.connected(false)
    }
    companion object { private const val TAG = "OneMinute.Service" }
}
