package top.e404.eclean.maintenance

import java.util.ArrayDeque
import java.util.UUID
import top.e404.eclean.config.ChunkUnloadConfig

internal data class ChunkAddress(val world: UUID, val x: Int, val z: Int)

internal interface ChunkAccess {
    fun snapshot(): List<ChunkAddress>
    fun safe(address: ChunkAddress): Boolean
    fun request(address: ChunkAddress): Boolean
}

/** Bounded inspection/request work; snapshots enumerate only chunks already loaded. */
internal class IdleChunkPolicy(private val config: ChunkUnloadConfig, private val access: ChunkAccess) {
    private val pending = ArrayDeque<ChunkAddress>()
    val pendingCount get() = pending.size
    private val since = mutableMapOf<ChunkAddress, Long>()
    private var tick = 0L
    var scanned = 0L
        private set
    var skipped = 0L
        private set
    var attempted = 0L
        private set
    var accepted = 0L
        private set

    fun forget(address: ChunkAddress) { since.remove(address) }

    fun runPass() {
        tick += config.periodTicks
        if (pending.isEmpty()) {
            val loaded = access.snapshot().toSet()
            since.keys.retainAll(loaded)
            pending.addAll(loaded)
        }
        var requests = 0
        repeat(config.scanBudget) {
            if (pending.isEmpty() || requests >= config.requestBudget) return
            val address = pending.removeFirst()
            scanned++
            if (!access.safe(address)) {
                since.remove(address)
                skipped++
                return@repeat
            }
            val start = since.getOrPut(address) { tick }
            if (tick - start < config.idleTicks) return@repeat
            // Recheck on every attempt, then use the server's safe request API.
            attempted++
            requests++
            if (access.request(address)) accepted++
            // Both accepted and rejected requests must wait again before another attempt.
            since[address] = tick
        }
    }
}
