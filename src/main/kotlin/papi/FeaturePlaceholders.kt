package top.e404.eclean.papi

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.util.Compatibility

/** Publish on the main thread; placeholder readers never visit worlds or mutable counters. */
object FeaturePlaceholders {
    val keys = listOf(
        "worldrules_enabled", "worldrules_worlds",
        "redstone_enabled", "redstone_running", "redstone_mode", "redstone_tracked",
        "redstone_events", "redstone_untracked", "redstone_suppressed",
        "chunkunload_enabled", "chunkunload_running", "chunkunload_protection",
        "chunkunload_scanned", "chunkunload_skipped", "chunkunload_attempted",
        "chunkunload_accepted", "chunkunload_pending",
    )
    @Volatile private var values: Map<String, String> = emptyMap()
    private var task: BukkitTask? = null

    fun resolve(key: String): String? = values[key]

    fun start() {
        stop()
        refresh()
        task = PL.runTaskTimer(20, 20) { refresh() }
    }

    fun stop() {
        task?.cancel()
        task = null
        values = emptyMap()
    }

    fun refresh() {
        check(Bukkit.isPrimaryThread()) { "Publish feature placeholders on the server thread" }
        val config = Config.config
        val redstone = RedstoneMonitor.window
        val unload = ChunkUnloader.policy
        values = mapOf(
            "worldrules_enabled" to config.worldRules.toString(),
            "worldrules_worlds" to (config.living.worlds.keys + config.drop.worlds.keys + config.chunk.worlds.keys).size.toString(),
            "redstone_enabled" to config.redstone.enable.toString(),
            "redstone_running" to (redstone != null).toString(),
            "redstone_mode" to config.redstone.mode,
            "redstone_tracked" to (redstone?.trackedBlocks ?: 0).toString(),
            "redstone_events" to (redstone?.events ?: 0).toString(),
            "redstone_untracked" to (redstone?.untracked ?: 0).toString(),
            "redstone_suppressed" to RedstoneMonitor.suppressedEvents.toString(),
            "chunkunload_enabled" to config.chunkUnload.enable.toString(),
            "chunkunload_running" to (unload != null).toString(),
            "chunkunload_protection" to if (Compatibility.supportsChunkUnload) Compatibility.chunkProtection else "unavailable",
            "chunkunload_scanned" to (unload?.scanned ?: 0).toString(),
            "chunkunload_skipped" to (unload?.skipped ?: 0).toString(),
            "chunkunload_attempted" to (unload?.attempted ?: 0).toString(),
            "chunkunload_accepted" to (unload?.accepted ?: 0).toString(),
            "chunkunload_pending" to (unload?.pendingCount ?: 0).toString(),
        )
    }
}
