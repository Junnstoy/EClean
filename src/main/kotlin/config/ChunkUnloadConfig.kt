package top.e404.eclean.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChunkUnloadConfig(
    val enable: Boolean = false,
    @SerialName("period_ticks") val periodTicks: Int = 100,
    @SerialName("idle_ticks") val idleTicks: Int = 1200,
    @SerialName("keep_radius") val keepRadius: Int = 10,
    @SerialName("scan_budget") val scanBudget: Int = 64,
    @SerialName("request_budget") val requestBudget: Int = 4,
    @SerialName("ignored_worlds") val ignoredWorlds: Set<String> = emptySet(),
) {
    init {
        require(periodTicks > 0 && idleTicks > 0 && keepRadius >= 0)
        require(scanBudget in 1..4096 && requestBudget in 1..scanBudget)
    }
}
