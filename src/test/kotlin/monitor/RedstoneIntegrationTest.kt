package top.e404.eclean.test.monitor

import org.bukkit.event.block.BlockRedstoneEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import top.e404.eclean.config.Config
import top.e404.eclean.config.RedstoneConfig
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.test.world
import kotlin.test.*

abstract class RedstoneIntegrationTest {
    @AfterEach
    fun stop() {
        RedstoneMonitor.stop()
        Config.config.redstone = RedstoneConfig()
    }

    @Test
    fun optionalSuppressionOnlyBlocksRisingEdgesAndRestartClearsState() {
        Config.config.redstone = RedstoneConfig(enable = true, mode = "suppress", windowTicks = 1,
            maxChanges = 1, consecutiveWindows = 1)
        RedstoneMonitor.restart()
        val block = world.getBlockAt(1, 10, 1)
        repeat(2) { RedstoneMonitor.onRedstone(BlockRedstoneEvent(block, 0, 15)) }
        RedstoneMonitor.window!!.advance()
        val rising = BlockRedstoneEvent(block, 0, 15)
        RedstoneMonitor.onRedstone(rising)
        assertEquals(0, rising.newCurrent)
        val falling = BlockRedstoneEvent(block, 15, 0)
        RedstoneMonitor.onRedstone(falling)
        assertEquals(0, falling.newCurrent)
        RedstoneMonitor.restart()
        assertTrue(RedstoneMonitor.window!!.top().isEmpty())
    }

    @Test
    fun ignoredWorldIsNeitherRecordedNorModified() {
        Config.config.redstone = RedstoneConfig(enable = true, ignoredWorlds = setOf(world.name))
        RedstoneMonitor.restart()
        val event = BlockRedstoneEvent(world.getBlockAt(1, 10, 1), 0, 15)
        RedstoneMonitor.onRedstone(event)
        assertEquals(15, event.newCurrent)
        assertTrue(RedstoneMonitor.window!!.top().isEmpty())
    }
}
