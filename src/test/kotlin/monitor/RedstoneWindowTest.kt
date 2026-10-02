package top.e404.eclean.monitor

import org.junit.jupiter.api.Test
import top.e404.eclean.config.RedstoneConfig
import kotlin.test.*

class RedstoneWindowTest {
    private val p = RedstonePosition("world", 1, 2, 3)
    private fun tickWindow(window: RedstoneWindow) = (1..window.config.windowTicks).flatMap { window.advance() }

    @Test
    fun sustainedActivityTriggersButReportModeNeverSuppresses() {
        val window = RedstoneWindow(RedstoneConfig(windowTicks = 2, maxChanges = 1, consecutiveWindows = 2))
        repeat(2) { assertFalse(window.record(p)) }
        assertTrue(tickWindow(window).isEmpty())
        repeat(2) { window.record(p) }
        assertEquals(listOf(p), tickWindow(window))
        assertFalse(window.record(p))
    }

    @Test
    fun quietWindowBreaksTheStreak() {
        val window = RedstoneWindow(RedstoneConfig(windowTicks = 2, maxChanges = 1, consecutiveWindows = 2))
        repeat(2) { window.record(p) }; tickWindow(window)
        tickWindow(window)
        repeat(2) { window.record(p) }
        assertTrue(tickWindow(window).isEmpty())
    }

    @Test
    fun suppressionExpiresAndIdleEntriesAreRemoved() {
        val window = RedstoneWindow(RedstoneConfig(mode = "suppress", windowTicks = 2, maxChanges = 1,
            consecutiveWindows = 1, cooldownTicks = 4))
        repeat(2) { window.record(p) }; tickWindow(window)
        assertTrue(window.record(p))
        repeat(6) { window.advance() }
        assertTrue(window.top().isEmpty())
        assertFalse(window.record(p))
    }

    @Test
    fun capacityIsBoundedAndWorldsRemainSeparate() {
        val window = RedstoneWindow(RedstoneConfig(maxTrackedBlocks = 2))
        window.record(p)
        window.record(p.copy(world = "other"))
        window.record(p.copy(x = 99))
        assertEquals(1L, window.untracked)
        assertEquals(2, window.top().size)
        assertEquals(1, window.top("other").size)
    }
}
