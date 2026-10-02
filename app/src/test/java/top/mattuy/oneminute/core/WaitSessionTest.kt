package top.mattuy.oneminute.core

import org.junit.Assert.*
import org.junit.Test

class WaitSessionTest {
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
    @Test fun `returning from home starts a full new wait`() {
        session.foreground("app.a", 60, 0)
        session.proceed(session.gate!!.id, 60000)
        session.foreground("launcher", null, 65000)
        assertNull(session.gate)
        session.foreground("app.a", 60, 70000)
        assertEquals(130000L, session.gate!!.deadline)
    }
    @Test fun `old completion cannot unlock another app or a later session`() {
        session.foreground("app.a", 60, 0)
        val oldId = session.gate!!.id
        session.foreground("app.b", 60, 10000)
        assertFalse(session.proceed(oldId, 70000))
        session.foreground("app.a", 60, 80000)
        assertFalse(session.proceed(oldId, 200000))
    }
    @Test fun `lock and service restart clear granted access`() {
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
        session.clear()
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
}
