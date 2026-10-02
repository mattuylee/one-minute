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
    @Test fun `system interruption cancels waiting but preserves completed session`() {
        session.foreground("app.a", 60, 0)
        session.systemInterruption()
        assertNull(session.gate)
        session.foreground("app.a", 60, 70000)
        session.proceed(session.gate!!.id, 130000)
        session.systemInterruption()
        assertEquals(WaitSession.Phase.ALLOWED, session.gate!!.phase)
    }
    @Test fun `no rules never produces an overlay`() {
        session.foreground("app.a", null, 0)
        session.foreground("app.b", null, 10000)
        assertNull(session.gate)
    }
}
