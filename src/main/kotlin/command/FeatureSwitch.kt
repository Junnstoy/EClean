package top.e404.eclean.command

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.config.ConfigData
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.papi.FeaturePlaceholders
import top.e404.eclean.util.Compatibility
import top.e404.eplugin.command.ECommand
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class Feature(val command: String, val label: String) {
    WORLD_RULES("worldrules", "世界及实体规则"), REDSTONE("redstone", "红石统计及高频抑制"), CHUNK_UNLOAD("chunkunload", "闲置区块卸载");
    val permission get() = "eclean.$command"
    fun enabled(config: ConfigData) = when (this) {
        WORLD_RULES -> config.worldRules
        REDSTONE -> config.redstone.enable
        CHUNK_UNLOAD -> config.chunkUnload.enable
    }
}

/** Called on the main thread. Do not change live state until the save succeeds. */
internal fun setFeature(feature: Feature, enabled: Boolean) {
    val current = Config.config
    val next = current.copy(
        worldRules = if (feature == Feature.WORLD_RULES) enabled else current.worldRules,
        redstone = if (feature == Feature.REDSTONE) current.redstone.copy(enable = enabled) else current.redstone,
        chunkUnload = if (feature == Feature.CHUNK_UNLOAD) current.chunkUnload.copy(enable = enabled) else current.chunkUnload,
    )
    val file = Config.file.toPath()
    val backup = file.resolveSibling("config.yml.before-feature-switch")
    if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup)
    val temp = Files.createTempFile(file.parent, "config-", ".tmp")
    try {
        Files.write(temp, Config.format.encodeToString(ConfigData.serializer(), next).toByteArray(Charsets.UTF_8))
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temp) }
    current.worldRules = next.worldRules
    current.redstone = next.redstone
    current.chunkUnload = next.chunkUnload
    when (feature) {
        Feature.WORLD_RULES -> Unit
        Feature.REDSTONE -> RedstoneMonitor.restart()
        Feature.CHUNK_UNLOAD -> ChunkUnloader.restart()
    }
    FeaturePlaceholders.refresh()
}

internal fun featureCommand(sender: CommandSender, args: Array<out String>, feature: Feature): Boolean {
    val action = args.getOrNull(1)?.lowercase()
    if (action !in listOf("on", "off", "status")) return false
    if (args.size != 2) { sender.sendMessage("用法: /eclean ${feature.command} <on|off|status>"); return true }
    if (action != "status") {
        if (action == "on" && feature == Feature.CHUNK_UNLOAD && !Compatibility.supportsChunkUnload) {
            sender.sendMessage("无法启用：服务端缺少安全区块检查 API"); return true
        }
        try { setFeature(feature, action == "on") }
        catch (ex: Exception) {
            PL.warn("保存功能开关失败", ex)
            sender.sendMessage("保存配置失败，功能开关未修改，请查看控制台"); return true
        }
    }
    val configured = feature.enabled(Config.config)
    val running = when (feature) {
        Feature.WORLD_RULES -> configured
        Feature.REDSTONE -> RedstoneMonitor.window != null
        Feature.CHUNK_UNLOAD -> ChunkUnloader.policy != null
    }
    sender.sendMessage("${feature.label}: 配置=${if (configured) "on" else "off"}, 运行=${if (running) "on" else "off"}")
    if (feature == Feature.CHUNK_UNLOAD && running) sender.sendMessage("区块保护: ${Compatibility.chunkProtection}")
    return true
}

open class FeatureSwitch(private val feature: Feature) : ECommand(PL, feature.command, "(?i)${feature.command}", false, feature.permission) {
    override val usage = "/eclean ${feature.command} <on|off|status>"
    override fun onCommand(sender: CommandSender, args: Array<out String>) {
        if (!featureCommand(sender, args, feature)) sender.sendMessage(usage)
    }
    override fun onTabComplete(sender: CommandSender, args: Array<out String>, complete: MutableList<String>) {
        if (args.size == 2) complete.addAll(listOf("on", "off", "status"))
    }
}
object WorldRules : FeatureSwitch(Feature.WORLD_RULES)
object ChunkUnload : FeatureSwitch(Feature.CHUNK_UNLOAD)
