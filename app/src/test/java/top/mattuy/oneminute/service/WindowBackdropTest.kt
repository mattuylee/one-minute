package top.mattuy.oneminute.service

import org.junit.Assert.*
import org.junit.Test

class WindowBackdropTest {
    private class FakeLayer : WindowBackdrop.Layer {
        var shows = 0
        var visible = false
        var closed = false
        var size = 0 to 0
        var color = 0
        override fun show(width: Int, height: Int, color: Int) {
            check(!closed)
            shows++; visible = true; size = width to height; this.color = color
        }
        override fun hide() { visible = false }
        override fun close() { visible = false; closed = true }
    }

    @Test fun `countdown updates do not redraw or reattach the backdrop`() {
        val layers = mutableListOf<FakeLayer>()
        val backdrop = WindowBackdrop { FakeLayer().also(layers::add) }
        repeat(60) { backdrop.show(WindowBackdrop.Target(1, 1080, 2400), 123) }
        assertEquals(1, layers.size)
        assertEquals(1, layers.single().shows)
    }

    @Test fun `notification shade and returning reuse the hidden layer`() {
        val layers = mutableListOf<FakeLayer>()
        val backdrop = WindowBackdrop { FakeLayer().also(layers::add) }
        val target = WindowBackdrop.Target(1, 1080, 2400)
        backdrop.show(target, 123)
        backdrop.hide()
        assertFalse(layers.single().visible)
        backdrop.show(target, 123)
        assertEquals(1, layers.size)
        assertTrue(layers.single().visible)
    }

    @Test fun `changing app windows closes the previous layer before attaching another`() {
        val layers = mutableListOf<FakeLayer>()
        val backdrop = WindowBackdrop {
            assertTrue(layers.all { it.closed && !it.visible })
            FakeLayer().also(layers::add)
        }
        backdrop.show(WindowBackdrop.Target(1, 1080, 2400), 123)
        backdrop.show(WindowBackdrop.Target(2, 1080, 2400), 123)
        assertEquals(2, layers.size)
        assertFalse(layers.first().visible)
        assertTrue(layers.last().visible)
    }

    @Test fun `rotation and theme changes update the existing layer`() {
        val layer = FakeLayer()
        val backdrop = WindowBackdrop { layer }
        backdrop.show(WindowBackdrop.Target(1, 1080, 2400), 123)
        backdrop.show(WindowBackdrop.Target(1, 2400, 1080), 456)
        assertEquals(2400 to 1080, layer.size)
        assertEquals(456, layer.color)
        assertEquals(2, layer.shows)
    }

    @Test fun `invalid window dimensions hide an existing background`() {
        val layer = FakeLayer()
        val backdrop = WindowBackdrop { layer }
        backdrop.show(WindowBackdrop.Target(1, 1080, 2400), 123)
        backdrop.show(WindowBackdrop.Target(1, 0, 0), 123)
        assertFalse(layer.visible)
    }

    @Test fun `disconnect releases the layer and later connections start fresh`() {
        val layers = mutableListOf<FakeLayer>()
        val backdrop = WindowBackdrop { FakeLayer().also(layers::add) }
        val target = WindowBackdrop.Target(1, 1080, 2400)
        backdrop.show(target, 123)
        backdrop.close()
        backdrop.close()
        assertTrue(layers.single().closed)
        assertFalse(layers.single().visible)
        backdrop.show(target, 123)
        assertEquals(2, layers.size)
        assertTrue(layers.last().visible)
    }
}
