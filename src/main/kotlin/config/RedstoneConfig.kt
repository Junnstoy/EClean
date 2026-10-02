package top.e404.eclean.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RedstoneConfig(
    val enable: Boolean = false,
    val mode: String = "report",
    @SerialName("window_ticks") val windowTicks: Int = 100,
    @SerialName("max_changes") val maxChanges: Int = 100,
    @SerialName("consecutive_windows") val consecutiveWindows: Int = 3,
    @SerialName("cooldown_ticks") val cooldownTicks: Int = 200,
    @SerialName("max_tracked_blocks") val maxTrackedBlocks: Int = 10000,
    @SerialName("ignored_worlds") val ignoredWorlds: Set<String> = emptySet(),
) {
    init {
        require(mode == "report" || mode == "suppress") { "redstone.mode must be report or suppress" }
        require(windowTicks > 0 && maxChanges > 0 && consecutiveWindows > 0 && cooldownTicks > 0)
        require(maxTrackedBlocks in 1..100000) { "redstone.max_tracked_blocks must be 1..100000" }
    }
}
