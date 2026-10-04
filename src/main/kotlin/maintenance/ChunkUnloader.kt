package top.e404.eclean.maintenance

import org.bukkit.Bukkit
import top.e404.eclean.util.Compatibility
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.scheduler.BukkitTask
import top.e404.eclean.PL
import top.e404.eclean.config.ChunkUnloadConfig
import top.e404.eclean.config.Config
import top.e404.eplugin.listener.EListener
import kotlin.math.abs

internal fun safeToUnload(forced: Boolean, pluginTickets: Int?, playerViewers: Int?, nearPlayer: Boolean) =
    !forced && pluginTickets == 0 && playerViewers == 0 && !nearPlayer

internal class BukkitChunkAccess(private val config: ChunkUnloadConfig) : ChunkAccess {
    companion object {
        val supported get() = Compatibility.supportsChunkUnload
    }

    override fun snapshot() = Bukkit.getWorlds().filterNot { it.name in config.ignoredWorlds }.flatMap { world ->
        world.loadedChunks.map { ChunkAddress(world.uid, it.x, it.z) }
    }

    override fun safe(address: ChunkAddress): Boolean = runCatching {
        val world = Bukkit.getWorld(address.world) ?: return false
        if (world.name in config.ignoredWorlds || !world.isChunkLoaded(address.x, address.z)) return false
        // Main-thread check then lookup: never intentionally load an unloaded candidate.
        val chunk = world.getChunkAt(address.x, address.z)
        val pluginTickets = Compatibility.pluginTickets(chunk)
        // Paper 26.3's deprecated World.isChunkInUse simply returns isChunkLoaded.
        // Checking it here would reject every loaded candidate, even with no players.
        val playerViewers = Compatibility.playerViewers(chunk)
        val radius = maxOf(config.keepRadius, Bukkit.getViewDistance())
        val nearby = world.players.any {
            val loc = it.location
            abs((loc.blockX shr 4).toLong() - address.x) <= radius &&
                abs((loc.blockZ shr 4).toLong() - address.z) <= radius
        }
        safeToUnload(Compatibility.isForceLoaded(chunk), pluginTickets, playerViewers, nearby)
    }.getOrDefault(false) // Unknown API state must never become permission to unload.

    override fun request(address: ChunkAddress): Boolean {
        if (!safe(address)) return false
        return runCatching {
            Bukkit.getWorld(address.world)?.unloadChunkRequest(address.x, address.z) == true
        }.getOrDefault(false)
    }
}

object ChunkUnloader : EListener(PL) {
    private var task: BukkitTask? = null
    internal var policy: IdleChunkPolicy? = null
        private set

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        policy?.forget(ChunkAddress(event.world.uid, event.chunk.x, event.chunk.z))
    }

    fun restart() {
        stop()
        val config = Config.config.chunkUnload
        if (!config.enable) return
        if (!BukkitChunkAccess.supported) {
            PL.warn("区块卸载未启动：当前 API 缺少安全区块检查能力")
            return
        }
        val state = IdleChunkPolicy(config, BukkitChunkAccess(config))
        policy = state
        task = PL.runTaskTimer(config.periodTicks.toLong(), config.periodTicks.toLong()) {
            state.runPass()
        }
    }

    fun stop() {
        task?.cancel()
        task = null
        policy = null
    }
}
