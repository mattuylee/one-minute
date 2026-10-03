package top.mattuy.oneminute.service

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ForegroundRefreshSchedulerTest {
    @Test fun `a burst scans once and uses latest foreground instead of retained events`() {
        var foreground = "a"
        val scanned = mutableListOf<String>()
        val scheduler = ForegroundRefreshScheduler(Handler(Looper.getMainLooper())) { scanned += foreground }
        repeat(1000) { scheduler.request() }
        foreground = "b"
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("b"), scanned)
    }

    @Test fun `sustained events have bounded work without starving refresh`() {
        var scans = 0
        val scheduler = ForegroundRefreshScheduler(Handler(Looper.getMainLooper())) { scans++ }
        val looper = shadowOf(Looper.getMainLooper())
        scheduler.request(); looper.idle()
        repeat(1000) {
            scheduler.request()
            looper.idleFor(Duration.ofMillis(1))
        }
        assertEquals(11, scans)
        looper.idleFor(Duration.ofSeconds(10))
        assertEquals(11, scans) // No background polling after the event stream ends.
    }

    @Test fun `cancelling on lock or disconnect prevents a delayed scan`() {
        var scans = 0
        val scheduler = ForegroundRefreshScheduler(Handler(Looper.getMainLooper())) { scans++ }
        val looper = shadowOf(Looper.getMainLooper())
        scheduler.request(); looper.idle()
        scheduler.request(); scheduler.cancel()
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(1, scans)
        scheduler.request(); looper.idle()
        assertEquals(2, scans)
    }

    @Test fun `only irrelevant window changes are skipped and unknown masks are conservative`() {
        assertFalse(ForegroundRefreshScheduler.relevant(TYPE_WINDOWS_CHANGED,
            WINDOWS_CHANGE_BOUNDS or WINDOWS_CHANGE_TITLE or WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED))
        for (flag in listOf(WINDOWS_CHANGE_ADDED, WINDOWS_CHANGE_REMOVED, WINDOWS_CHANGE_ACTIVE,
            WINDOWS_CHANGE_FOCUSED, WINDOWS_CHANGE_LAYER, WINDOWS_CHANGE_PIP, 0)) {
            assertTrue(ForegroundRefreshScheduler.relevant(TYPE_WINDOWS_CHANGED, flag))
        }
        assertTrue(ForegroundRefreshScheduler.relevant(TYPE_WINDOWS_CHANGED, WINDOWS_CHANGE_BOUNDS or WINDOWS_CHANGE_FOCUSED))
        assertTrue(ForegroundRefreshScheduler.relevant(TYPE_WINDOW_STATE_CHANGED, 0))
        assertFalse(ForegroundRefreshScheduler.relevant(TYPE_WINDOW_CONTENT_CHANGED, 0))
    }
}
