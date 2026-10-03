package top.mattuy.oneminute.core

import org.junit.Assert.*
import org.junit.Test

class WaitSessionTest {
    @Test fun `one minute countdown needs sixty scheduled ticks and aligns after a delayed callback`() {
        val gate = WaitSession.Gate(1, "app", 60, 60123)
        var now = 123L
        var ticks = 0
        while (now < gate.deadline) {
            now += gate.nextTickDelay(now)
            ticks++
        }
        assertEquals(60, ticks)
        assertEquals(60123L, now)
        assertEquals(650L, gate.nextTickDelay(1473))
        assertEquals(1L, gate.nextTickDelay(60122))
    }
    private val session = WaitSession()
    @Test fun `cannot proceed early even if UI button is invoked`() {
        session.foreground("app.a", 60, 1000)
        val id = session.gate!!.id
        assertFalse(session.proceed(id, 60999))
        assertTrue(session.proceed(id, 61000))
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }
    @Test fun `duplicate window events do not restart waiting or reblock an allowed app`() {
        session.foreground("app.a", 60, 0)
        val gate = session.gate!!
        session.foreground("app.a", 60, 40000)
        assertEquals(gate, session.gate)
        session.proceed(gate.id, 60000)
        session.foreground("app.a", 60, 70000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }
    @Test fun `returning from home within five minutes keeps granted access`() {
        session.foreground("app.a", 60, 0)
        session.proceed(session.gate!!.id, 60000)
        session.foreground("launcher", null, 65000)
        assertNull(session.gate)
        session.foreground("app.a", 60, 70000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }
    @Test fun `old completion cannot unlock another app or a later session`() {
        session.foreground("app.a", 60, 0)
        val oldId = session.gate!!.id
        session.foreground("app.b", 60, 10000)
        assertFalse(session.proceed(oldId, 70000))
        session.foreground("app.a", 60, 80000)
        assertFalse(session.proceed(oldId, 200000))
    }
    @Test fun `service restart clears granted access`() {
        session.foreground("app.a", 60, 0)
        session.proceed(session.gate!!.id, 60000)
        session.clear()
        session.foreground("app.a", 60, 70000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }
    @Test fun `unselecting an app removes its gate immediately`() {
        session.foreground("app.a", 60, 0)
        session.foreground("app.a", null, 10000)
        assertNull(session.gate)
    }
    @Test fun `changing duration restarts an active wait with new duration`() {
        session.foreground("app.a", 60, 0)
        session.foreground("app.a", 30, 10000)
        assertEquals(40000L, session.gate!!.deadline)
    }
    @Test fun `display rounds up and never enables at a fractional second`() {
        session.foreground("app.a", 60, 1000)
        assertEquals(1, session.gate!!.remainingSeconds(60999))
        assertEquals(0, session.gate!!.remainingSeconds(61000))
        session.tick(60999)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
        session.tick(61000)
        assertEquals(WaitSession.Phase.READY, session.gate!!.phase)
    }
    @Test fun `notification shade preserves deadline and returning shows remaining time`() {
        session.foreground("app.a", 60, 0)
        val original = session.gate!!
        session.tick(20000)
        session.systemInterruption()
        session.systemInterruption()
        assertTrue(session.systemInterrupted)
        assertEquals(original, session.gate)
        session.foreground("app.a", 60, 30000)
        assertFalse(session.systemInterrupted)
        assertEquals(original.id, session.gate!!.id)
        assertEquals(30, session.gate!!.remainingSeconds(30000))
        assertFalse(session.proceed(original.id, 59999))
        assertTrue(session.proceed(original.id, 60000))
    }
    @Test fun `time elapsed in notification shade is retained but cannot approve while obscured`() {
        session.foreground("app.a", 60, 0)
        val id = session.gate!!.id
        session.systemInterruption()
        assertFalse(session.proceed(id, 70000))
        session.foreground("app.a", 60, 70000)
        assertEquals(id, session.gate!!.id)
        assertEquals(WaitSession.Phase.READY, session.gate!!.phase)
        assertTrue(session.proceed(id, 70000))
    }
    @Test fun `system interruption preserves ready and allowed sessions`() {
        session.foreground("app.a", 60, 0)
        session.tick(60000)
        session.systemInterruption()
        assertEquals(WaitSession.Phase.READY, session.gate!!.phase)
        session.foreground("app.a", 60, 70000)
        assertTrue(session.proceed(session.gate!!.id, 70000))
        session.systemInterruption()
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("app.a", 60, 80000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }
    @Test fun `opening another app from a notification still starts its own wait`() {
        session.foreground("app.a", 60, 0)
        val oldId = session.gate!!.id
        session.systemInterruption()
        session.foreground("app.b", 60, 20000)
        assertFalse(session.systemInterrupted)
        assertEquals("app.b", session.gate!!.packageName)
        assertEquals(80000L, session.gate!!.deadline)
        assertFalse(session.proceed(oldId, 90000))
    }
    @Test fun `locking while notification shade is open clears the interrupted gate`() {
        session.foreground("app.a", 60, 0)
        session.systemInterruption()
        session.leave(20000)
        assertFalse(session.systemInterrupted)
        assertNull(session.gate)
        session.foreground("app.a", 60, 30000)
        assertEquals(90000L, session.gate!!.deadline)
    }
    @Test fun `no rules never produces an overlay`() {
        session.foreground("app.a", null, 0)
        session.foreground("app.b", null, 10000)
        assertNull(session.gate)
    }

    private fun allow(packageName: String, now: Long) {
        session.foreground(packageName, 60, now)
        assertTrue(session.proceed(session.gate!!.id, now + 60000))
    }

    @Test fun `exactly five minutes away is allowed but one millisecond more is not`() {
        allow("app.a", 0)
        session.foreground("home", null, 65000)
        session.foreground("app.a", 60, 365000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("home", null, 400000)
        session.foreground("app.a", 60, 700001)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
        assertEquals(760001L, session.gate!!.deadline)
    }

    @Test fun `continuous use never expires and grace starts only on departure`() {
        allow("app.a", 0)
        session.foreground("app.a", 60, 3600000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("home", null, 3600000)
        session.foreground("app.a", 60, 3800000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }

    @Test fun `switching between two granted apps retains independent grants`() {
        allow("app.a", 0)
        allow("app.b", 70000)
        session.foreground("app.a", 60, 150000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("home", null, 160000)
        session.foreground("app.a", 60, 455000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("app.b", 60, 455001)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `each return starts a new grace period on the next departure`() {
        allow("app.a", 0)
        session.leave(70000)
        session.foreground("app.a", 60, 360000)
        session.leave(400000)
        session.foreground("app.a", 60, 690000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }

    @Test fun `unfinished or ready but unapproved waits never grant grace`() {
        session.foreground("app.a", 60, 0)
        session.leave(10000)
        session.foreground("app.a", 60, 20000)
        assertEquals(80000L, session.gate!!.deadline)
        session.tick(80000)
        session.leave(90000)
        session.foreground("app.a", 60, 100000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
        assertEquals(160000L, session.gate!!.deadline)
    }

    @Test fun `repeated lock events do not extend grace and long locks expire it`() {
        allow("app.a", 0)
        session.leave(70000)
        session.leave(200000)
        session.foreground("app.a", 60, 350000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.leave(400000)
        session.leave(600000)
        session.foreground("app.a", 60, 700001)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `cancelling another apps wait preserves earlier grants`() {
        allow("app.a", 0)
        session.foreground("app.b", 60, 70000)
        session.leave(80000)
        session.foreground("app.a", 60, 90000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.foreground("app.b", 60, 100000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `unselecting a background app revokes its saved grant`() {
        allow("app.a", 0)
        session.leave(70000)
        session.retainSelectedPackages(setOf("app.b"))
        session.retainSelectedPackages(setOf("app.a", "app.b"))
        session.foreground("app.a", 60, 80000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `unselecting a foreground app revokes its current grant`() {
        allow("app.a", 0)
        session.retainSelectedPackages(emptySet())
        assertNull(session.gate)
        session.foreground("app.a", 60, 80000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `changing wait duration preserves completed access`() {
        allow("app.a", 0)
        session.foreground("app.a", 30, 70000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.leave(80000)
        session.foreground("app.a", 30, 100000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }

    @Test fun `restart clears background grants too`() {
        allow("app.a", 0)
        session.leave(70000)
        session.clear()
        session.foreground("app.a", 60, 80000)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `custom grace duration controls the boundary`() {
        session.setReturnGraceMinutes(1)
        allow("app.a", 0)
        session.leave(70000)
        session.foreground("app.a", 60, 130000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.leave(140000)
        session.foreground("app.a", 60, 200001)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `changed grace setting uses original departure time`() {
        allow("app.a", 0)
        session.leave(70000)
        session.setReturnGraceMinutes(15)
        session.foreground("app.a", 60, 600000)
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
        session.leave(700000)
        session.setReturnGraceMinutes(1)
        session.foreground("app.a", 60, 760001)
        assertEquals(WaitSession.Phase.WAITING, session.gate!!.phase)
    }

    @Test fun `invalid grace settings are rejected`() {
        for (minutes in listOf(0, -1, 61)) {
            assertThrows(IllegalArgumentException::class.java) { session.setReturnGraceMinutes(minutes) }
        }
    }
}
