package top.e404.eclean.test.papi

import org.bukkit.event.block.BlockRedstoneEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.e404.eclean.command.Feature
import top.e404.eclean.command.setFeature
import top.e404.eclean.config.*
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.papi.FeaturePlaceholders
import top.e404.eclean.papi.Papi
import top.e404.eclean.test.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

abstract class FeaturePlaceholdersTest {
    private lateinit var original: String
    @BeforeEach fun prepare() {
        original = Config.file.readText(); resetConfig()
        RedstoneMonitor.stop(); ChunkUnloader.stop(); FeaturePlaceholders.start()
    }
    @AfterEach fun restore() {
        FeaturePlaceholders.stop(); RedstoneMonitor.stop(); ChunkUnloader.stop()
        Config.file.writeText(original); resetConfig()
    }
    private fun value(key: String) = Papi.onRequest(null, key)
    @Test fun advertisesAndResolvesEveryFeatureWithoutAPlayer() {
        FeaturePlaceholders.keys.forEach {
            assertNotNull(value(it), it); assertTrue("%eclean_${it}%" in Papi.getPlaceholders())
        }
        assertEquals("true", value("WORLDRULES_ENABLED"))
        assertEquals("false", value("redstone_running")); assertEquals("0", value("redstone_events"))
        assertNotNull(value("last_drop")); assertNull(value("unknown_feature"))
    }
    @Test fun separatesConfiguredAndRunningStateAndCountsUniqueConfiguredWorlds() {
        Config.config.living.worlds = mapOf("a" to LivingOverride())
        Config.config.drop.worlds = mapOf("a" to DropOverride(), "b" to DropOverride())
        Config.config.chunk.worlds = mapOf("b" to ChunkOverride())
        Config.config.redstone = RedstoneConfig(enable = true)
        FeaturePlaceholders.refresh()
        assertEquals("true", value("redstone_enabled")); assertEquals("false", value("redstone_running"))
        assertEquals("2", value("worldrules_worlds"))
        setFeature(Feature.WORLD_RULES, false)
        assertEquals("false", value("worldrules_enabled")); assertEquals("2", value("worldrules_worlds"))
    }
    @Test fun reportsTrackedOverflowAndActualSuppressedRisingEventsThenResets() {
        Config.config.redstone = RedstoneConfig(enable = true, mode = "suppress", windowTicks = 1,
            maxChanges = 1, consecutiveWindows = 1, cooldownTicks = 20, maxTrackedBlocks = 1)
        RedstoneMonitor.restart()
        val block = world.getBlockAt(1, 70, 1)
        repeat(2) { RedstoneMonitor.onRedstone(BlockRedstoneEvent(block, 0, 15)) }
        RedstoneMonitor.window!!.advance()
        RedstoneMonitor.onRedstone(BlockRedstoneEvent(block, 0, 15))
        RedstoneMonitor.onRedstone(BlockRedstoneEvent(block, 15, 0))
        RedstoneMonitor.onRedstone(BlockRedstoneEvent(world.getBlockAt(2, 70, 1), 0, 15))
        FeaturePlaceholders.refresh()
        assertEquals("5", value("redstone_events")); assertEquals("1", value("redstone_tracked"))
        assertEquals("1", value("redstone_untracked")); assertEquals("1", value("redstone_suppressed"))
        setFeature(Feature.REDSTONE, false)
        assertEquals("false", value("redstone_running")); assertEquals("0", value("redstone_events"))
        assertEquals("0", value("redstone_suppressed"))
    }
    @Test fun asyncReadsUsePublishedSnapshotUntilTheNextScheduledRefresh() {
        Config.config.worldRules = false
        val read = CompletableFuture.supplyAsync {
            repeat(1000) { assertEquals("true", value("worldrules_enabled")) }
            value("chunkunload_scanned")
        }
        assertEquals("0", read.get(5, TimeUnit.SECONDS))
        server.scheduler.performTicks(20)
        assertEquals("false", value("worldrules_enabled"))
    }
    @Test fun reloadRefreshesValuesAndSamplerDoesNotDuplicateOrSurviveStop() {
        setFeature(Feature.REDSTONE, true)
        assertEquals("true", value("redstone_running"))
        Config.file.writeText(original); Config.load(null)
        assertEquals(Config.config.redstone.enable.toString(), value("redstone_enabled"))
        FeaturePlaceholders.start()
        FeaturePlaceholders.start()
        FeaturePlaceholders.stop()
        server.scheduler.performTicks(40)
        assertNull(FeaturePlaceholders.resolve("redstone_running"))
    }
}
