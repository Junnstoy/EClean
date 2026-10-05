package top.e404.eclean.monitor

import top.e404.eclean.config.RedstoneConfig

internal data class RedstonePosition(val world: String, val x: Int, val y: Int, val z: Int)

/** Counts event activity, not CPU time. Holds coordinates only, never Block/Chunk references. */
internal class RedstoneWindow(val config: RedstoneConfig) {
    data class Activity(var current: Int = 0, var previous: Int = 0, var streak: Int = 0,
                        var until: Long = 0, var seen: Long = 0)
    var tick = 0L
        private set
    var untracked = 0L
        private set
    var events = 0L
        private set
    private val entries = mutableMapOf<RedstonePosition, Activity>()
    val trackedBlocks get() = entries.size

    fun record(position: RedstonePosition): Boolean {
        events++
        val entry = entries[position] ?: run {
            if (entries.size >= config.maxTrackedBlocks) { untracked++; return false }
            Activity().also { entries[position] = it }
        }
        if (entry.current < Int.MAX_VALUE) entry.current++
        entry.seen = tick
        return config.mode == "suppress" && entry.until > tick
    }

    fun advance(): List<RedstonePosition> {
        tick++
        if (tick % config.windowTicks != 0L) return emptyList()
        val alerts = mutableListOf<RedstonePosition>()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (position, entry) = iterator.next()
            entry.previous = entry.current
            entry.streak = if (entry.current > config.maxChanges)
                (entry.streak + 1).coerceAtMost(config.consecutiveWindows) else 0
            entry.current = 0
            if (entry.streak >= config.consecutiveWindows && entry.until <= tick) {
                entry.until = tick + config.cooldownTicks
                alerts.add(position)
            }
            if (tick - entry.seen >= maxOf(config.windowTicks.toLong() * 2, config.cooldownTicks.toLong())
                && entry.until <= tick) iterator.remove()
        }
        return alerts
    }

    fun top(world: String? = null) = entries.entries.asSequence()
        .filter { world == null || it.key.world == world }
        .sortedByDescending { maxOf(it.value.current, it.value.previous) }
        .take(10).map { it.key to it.value.copy() }.toList()
}
