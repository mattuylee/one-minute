package top.mattuy.oneminute.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, bounded diagnostics. Never records other apps, screen contents or input. */
object ServiceDiagnostics {
    private const val TAG = "OneMinute.Diagnostics"
    private var installed = false

    fun enabled(context: Context): Boolean? = try {
        val expected = ComponentName(context, WaitAccessibilityService::class.java)
        val services = Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            services.split(':').any { ComponentName.unflattenFromString(it) == expected }
    } catch (error: SecurityException) {
        Log.e(TAG, "Unable to read accessibility switch", error)
        null
    }

    @Synchronized fun install(context: Context) {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous == null) {
            Log.e(TAG, "No default crash handler; relying on system exit history")
            return
        }
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(RecordingExceptionHandler({ error ->
            File(app.filesDir, "last-service-crash.txt").writeText(
                "${time(System.currentTimeMillis())}\n${error.stackTraceToString().take(24000)}")
        }, previous))
        installed = true
    }

    @Synchronized fun record(context: Context, event: String) {
        Log.i(TAG, event)
        val prefs = context.getSharedPreferences("service_diagnostics", Context.MODE_PRIVATE)
        val history = prefs.getString("events", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        prefs.edit().putString("events", (history + "${time(System.currentTimeMillis())} $event")
            .takeLast(24).joinToString("\n")).apply()
    }

    /** Call off the main thread: exit history is an IPC and the crash record is a local file. */
    fun report(context: Context): String = buildString {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        appendLine("稍等 $version · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} / ${Build.DISPLAY}")
        appendLine("生成时间：${time(System.currentTimeMillis())}")
        appendLine("无障碍开关：${when (enabled(context)) { true -> "开启"; false -> "关闭"; null -> "无法读取" }}")
        appendLine("本进程服务连接：${if (ServiceStatus.connected.value) "已连接" else "未连接"}")
        appendLine("服务错误：${ServiceStatus.error.value ?: "无"}")
        appendLine("系统电池优化豁免：${context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}")
        appendLine("（此项不能代表厂商自启动或后台管理设置。）")
        appendLine(ServiceWorkStats.summary())
        appendLine("\n最近的进程退出记录（系统提供）：")
        val exits = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 5)
        if (exits.isEmpty()) appendLine("系统未提供退出记录，不能据此判断未发生异常。")
        exits.forEach { exit ->
            appendLine("${time(exit.timestamp)} · ${reason(exit.reason)} [${exit.reason}]")
            appendLine("pid=${exit.pid} status=${exit.status} importance=${exit.importance}")
            appendLine("系统描述：${exit.description?.take(1200) ?: "无"}")
        }
        appendLine("退出原因不等于无障碍开关关闭的原因；SIGKILL、强停类记录也不能单独证明是省电策略或人为操作。")
        appendLine("\n服务生命周期（最多 24 条，系统直接杀进程时可能没有结束回调）：")
        appendLine(context.getSharedPreferences("service_diagnostics", Context.MODE_PRIVATE)
            .getString("events", "尚无记录"))
        appendLine("\n最近一次捕获的未处理异常（可能早于本次退出，以时间为准）：")
        val crash = File(context.filesDir, "last-service-crash.txt")
        appendLine(if (crash.exists()) crash.readText().take(24000) else "尚无记录")
        appendLine("\n诊断仅保存在本机，复制后由你决定是否分享。")
    }

    private fun time(timestamp: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(timestamp))

    internal fun reason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Java/Kotlin 未处理异常"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生代码崩溃"
        ApplicationExitInfo.REASON_ANR -> "应用无响应（ANR）"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "系统内存不足"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "强停或移除任务等请求"
        ApplicationExitInfo.REASON_USER_STOPPED -> "所属系统用户停止"
        ApplicationExitInfo.REASON_SIGNALED -> "进程收到终止信号"
        ApplicationExitInfo.REASON_EXIT_SELF -> "进程自行退出"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "权限变化"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源使用超限"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依赖进程退出"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失败"
        ApplicationExitInfo.REASON_OTHER -> "其他系统原因"
        14 -> "系统冻结器终止"
        15 -> "应用状态变化"
        16 -> "安装更新"
        else -> "未知原因"
    }
}

/** Recording must never swallow a crash or replace Android's original termination behavior. */
internal class RecordingExceptionHandler(
    private val record: (Throwable) -> Unit,
    private val delegate: Thread.UncaughtExceptionHandler,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { record(error) }
        catch (recordError: Exception) { Log.e("OneMinute.Diagnostics", "Failed to persist crash", recordError) }
        finally { delegate.uncaughtException(thread, error) }
    }
}
