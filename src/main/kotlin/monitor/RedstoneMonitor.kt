package top.e404.eclean.monitor

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.scheduler.BukkitTask
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eplugin.listener.EListener

object RedstoneMonitor : EListener(PL) {
    private var task: BukkitTask? = null
    internal var window: RedstoneWindow? = null
        private set
    internal var suppressedEvents = 0L
        private set

    fun restart() {
        stop()
        val config = Config.config.redstone
        if (!config.enable) return
        val state = RedstoneWindow(config)
        window = state
        task = PL.runTaskTimer(1, 1) {
            val alerts = state.advance()
            alerts.take(10).forEach { p ->
                PL.warn("红石高频活动: ${p.world} ${p.x},${p.y},${p.z}; 模式=${config.mode} (事件次数不代表耗时)")
            }
            if (alerts.size > 10) PL.warn("本窗口另有${alerts.size - 10}处红石高频活动，请使用 /eclean redstone 查看")
        }
    }

    fun stop() {
        task?.cancel()
        task = null
        window = null
        suppressedEvents = 0L
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRedstone(event: BlockRedstoneEvent) {
        val state = window ?: return
        if (event.oldCurrent == event.newCurrent) return
        val block = event.block
        if (block.world.name in state.config.ignoredWorlds) return
        val suppress = state.record(RedstonePosition(block.world.name, block.x, block.y, block.z))
        // Allow falling edges so the mitigation does not deliberately latch a powered block on.
        // This API does not cancel all physics or guarantee that an entire circuit will stop.
        if (suppress && event.newCurrent > event.oldCurrent) {
            event.newCurrent = event.oldCurrent
            suppressedEvents++
        }
    }
}
