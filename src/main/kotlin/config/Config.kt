package top.e404.eclean.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.clean.Trashcan
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eplugin.config.JarConfigDefault
import top.e404.eplugin.config.KtxConfig
import top.e404.eplugin.config.serialization.RegexSerialization

object Config : KtxConfig<ConfigData>(
    plugin = PL,
    path = "config.yml",
    default = JarConfigDefault(PL, "config.yml"),
    serializer = ConfigData.serializer(),
    format = Yaml(configuration = YamlConfiguration(strictMode = false))
) {
    // 加载时处理清理task
    override fun onLoad(config: ConfigData, sender: CommandSender?) {
        if (Bukkit.isPrimaryThread()) {
            Trashcan.schedule()
            RedstoneMonitor.restart()
            return
        }
        plugin.runTask {
            Trashcan.schedule()
            RedstoneMonitor.restart()
        }
    }
}

@Serializable
data class ConfigData(
    var debug: Boolean = false,
    var update: Boolean = true,
    var duration: Long,
    var message: MutableMap<Long, String>,
    var living: LivingConfig,
    var drop: DropConfig,
    var chunk: ChunkConfig,
    var trashcan: TrashcanConfig,
    @SerialName("no_online")
    var noOnline: NoOnlineConfig = NoOnlineConfig(),
    var redstone: RedstoneConfig = RedstoneConfig(),
)

@Serializable
data class DropConfig(
    var enable: Boolean = true,
    @SerialName("disable_world")
    var disableWorld: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var finish: String = "",
    @SerialName("is_black")
    var black: Boolean = true,
    var enchant: Boolean = false,
    var lore: Boolean = false,
    @SerialName("written_book")
    var writtenBook: Boolean = false,
    var match: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var worlds: Map<String, DropOverride> = emptyMap(),
    var materials: Map<String, DropItemRule> = emptyMap(),
)

@Serializable
data class LivingConfig(
    var enable: Boolean = true,
    @SerialName("disable_world")
    var disableWorld: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var finish: String = "",
    var settings: Settings = Settings(),
    @SerialName("is_black")
    var black: Boolean = true,
    var match: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var worlds: Map<String, LivingOverride> = emptyMap(),
    var entities: Map<String, LivingEntityRule> = emptyMap(),
)

@Serializable
data class Settings(
    var name: Boolean = false,
    var lead: Boolean = false,
    var mount: Boolean = false,
)

@Serializable
data class ChunkConfig(
    var enable: Boolean = true,
    @SerialName("disable_world")
    var disableWorld: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var finish: String = "",
    var settings: Settings = Settings(),
    var count: Int = 50,
    var format: String? = null,
    var limit: MutableMap<@Serializable(RegexSerialization::class) Regex, Int> = mutableMapOf(),
    var worlds: Map<String, ChunkOverride> = emptyMap(),
    var entities: Map<String, ChunkEntityRule> = emptyMap(),
) {
    init {
        require(count >= 0) { "chunk.count must be non-negative" }
        require(limit.values.all { it >= 0 }) { "chunk.limit must be non-negative" }
    }
}

@Serializable
data class TrashcanConfig(
    var enable: Boolean = true,
    var collect: Boolean = true,
    var duration: Long? = 6000,
    var despawn: DespawnConfig = DespawnConfig(),
)

@Serializable
data class DespawnConfig(
    var enable: Boolean = false,
    @SerialName("disable_world")
    var disableWorlds: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
    var match: MutableList<@Serializable(RegexSerialization::class) Regex> = mutableListOf(),
)

@Serializable
data class NoOnlineConfig(
    var clean: Boolean = true,
    var message: Boolean = true,
)
