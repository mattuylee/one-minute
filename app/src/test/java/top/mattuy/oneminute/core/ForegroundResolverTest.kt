package top.mattuy.oneminute.core

import org.junit.Assert.assertEquals
import org.junit.Test
import top.mattuy.oneminute.core.ForegroundResolver.Kind.*
import top.mattuy.oneminute.core.ForegroundResolver.Window
import top.mattuy.oneminute.core.ForegroundResolver.Target

class ForegroundResolverTest {
    @Test fun `package lookup only reads the chosen app and never background windows`() {
        val reads = mutableListOf<Int>()
        val target = ForegroundResolver.resolve(listOf(
            Window(APPLICATION, false, false, null),
            Window(APPLICATION, true, true, null),
            Window(APPLICATION, false, true, null))) { index -> reads += index; "focused-app" }
        assertEquals(Target.App("focused-app"), target)
        assertEquals(listOf(1), reads)
    }
    @Test fun `system shade and absent apps require no package lookup`() {
        val lookup: (Int) -> String? = { error("Unexpected root lookup") }
        assertEquals(Target.SystemInterruption, ForegroundResolver.resolve(listOf(
            Window(SYSTEM, true, true, null), Window(APPLICATION, false, true, null)), lookup))
        assertEquals(Target.Unknown, ForegroundResolver.resolve(emptyList(), lookup))
    }
    @Test fun `our own overlay is not an application switch`() {
        assertEquals(Target.App("video"), ForegroundResolver.resolve(listOf(
            Window(OVERLAY, true, true, "self"), Window(APPLICATION, false, true, "video"))))
    }
    @Test fun `keyboard preserves the underlying application`() {
        assertEquals(Target.App("chat"), ForegroundResolver.resolve(listOf(
            Window(KEYBOARD, true, false, "ime"), Window(APPLICATION, false, true, "chat"))))
    }
    @Test fun `notification shade takes precedence over underlying app`() {
        assertEquals(Target.SystemInterruption, ForegroundResolver.resolve(listOf(
            Window(SYSTEM, true, true, "system"), Window(APPLICATION, false, true, "video"))))
    }
    @Test fun `focused pane wins over another active pane`() {
        assertEquals(Target.App("browser"), ForegroundResolver.resolve(listOf(
            Window(APPLICATION, false, true, "video"), Window(APPLICATION, true, true, "browser"))))
    }
    @Test fun `system UI reported as an application is still a temporary interruption`() {
        assertEquals(Target.SystemInterruption, ForegroundResolver.resolve(listOf(
            Window(APPLICATION, true, true, "com.android.systemui"),
            Window(APPLICATION, false, false, "video"))))
        assertEquals(Target.SystemInterruption, ForegroundResolver.resolve(listOf(
            Window(APPLICATION, false, true, "com.android.systemui"))))
    }
    @Test fun `missing root never invents a foreground package`() {
        assertEquals(Target.Unknown, ForegroundResolver.resolve(listOf(Window(APPLICATION, true, true, null))))
    }
}
