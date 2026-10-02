package top.mattuy.oneminute.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServiceDiagnosticsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun reset() {
        ServiceStatus.connected(false)
        ServiceStatus.error(null)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        context.getSharedPreferences("service_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `enabled setting is independent from service connection and ignores other apps`() {
        val own = ComponentName(context, WaitAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.app/.Reader:$own")
        ServiceStatus.refreshPermission(context)
        assertEquals(true, ServiceStatus.enabled.value)
        assertFalse(ServiceStatus.connected.value)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.app/.Reader")
        ServiceStatus.refreshPermission(context)
        assertEquals(false, ServiceStatus.enabled.value)
    }

    @Test fun `global accessibility off overrides a saved component`() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, WaitAccessibilityService::class.java).flattenToShortString())
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        assertEquals(false, ServiceDiagnostics.enabled(context))
    }

    @Test fun `event history is bounded and report handles missing exit records`() {
        repeat(30) { ServiceDiagnostics.record(context, "event-$it") }
        val events = context.getSharedPreferences("service_diagnostics", Context.MODE_PRIVATE)
            .getString("events", "")!!.lines()
        assertEquals(24, events.size)
        assertTrue(events.first().endsWith("event-6"))
        assertTrue(events.last().endsWith("event-29"))
        val report = ServiceDiagnostics.report(context)
        assertTrue(report.contains("系统未提供退出记录"))
        assertTrue(report.contains("无障碍开关：关闭"))
        assertFalse(report.contains("other.app"))
    }

    @Test fun `recording an exception still delegates the original throwable`() {
        val error = IllegalStateException("original")
        var recorded: Throwable? = null
        var delegated: Throwable? = null
        val handler = RecordingExceptionHandler({ recorded = it }, { _, failure -> delegated = failure })
        handler.uncaughtException(Thread.currentThread(), error)
        assertSame(error, recorded)
        assertSame(error, delegated)
    }

    @Test fun `failure to save diagnostics never prevents normal crash handling`() {
        val original = IllegalStateException("original")
        var delegated: Throwable? = null
        val handler = RecordingExceptionHandler({ throw java.io.IOException("disk full") },
            { _, failure -> delegated = failure })
        handler.uncaughtException(Thread.currentThread(), original)
        assertSame(original, delegated)
    }
}
