package top.e404.eclean.clean

import org.bukkit.Bukkit
import top.e404.eclean.util.Compatibility
import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.config.forWorld
import top.e404.eclean.util.info
import top.e404.eclean.util.isMatch
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage
import top.e404.eplugin.EPlugin.Companion.placeholder

private inline val livingCfg get() = Config.config.living

/**
 * 最近一次清理生物实体的数量
 */
var lastLiving = 0
    private set

/**
 * 清理全服生物
 */
fun cleanLiving() {
    if (!livingCfg.enable) {
        PL.debug { "生物清理已禁用" }
        return
    }
    val worlds = Bukkit.getWorlds().filterNot { livingCfg.disableWorld.any { regex -> it.name matches regex } }
    PL.buildDebug {
        append("开始清理生物, 启用生物清理的世界: [")
        worlds.joinTo(this, ", ", transform = World::getName)
        append("]")
    }
    PL.debug { if (livingCfg.settings.name) "清理被命名的生物" else "不清理被命名的生物" }
    PL.debug { if (livingCfg.settings.lead) "清理拴绳拴住的生物" else "不清理拴绳拴住的生物" }
    PL.debug { if (livingCfg.settings.mount) "清理乘骑中的生物" else "不清理乘骑中的生物" }

    var time = System.currentTimeMillis()
    val result = worlds.map { it.cleanLiving() }
    time = System.currentTimeMillis() - time

    lastLiving = result.sumOf { it.first }
    PL.debug { "生物清理共${lastLiving}个, 耗时${time}ms" }

    if (noOnline) {
        if (noOnlineMessage) {
            val all = result.sumOf { it.second }
            val finish = livingCfg.finish
            if (finish.isNotBlank()) PL.broadcastMsg(finish.placeholder(mapOf("clean" to lastLiving, "all" to all)))
        }
    } else {
        val all = result.sumOf { it.second }
        val finish = livingCfg.finish
        if (finish.isNotBlank()) PL.broadcastMsg(finish.placeholder(mapOf("clean" to lastLiving, "all" to all)))
    }
}

/**
 * 清理指定世界的生物
 *
 * @return Pair(clean, all)
 */
fun World.cleanLiving(): Pair<Int, Int> {
    val livingCfg = Config.config.living.forWorld(name)
    val all = livingEntities.filterNot { it is Player }.toMutableList()
    val total = all.size

    PL.debug { "" }
    PL.debug { "开始清理世界${name}的生物" }
    PL.buildDebug {
        append("所有实体共").append(total).append("个: [")
        all.info().entries.joinTo(this, ", ") { (k, v) -> "$k: $v" }
        append("]")
    }

    val groupBy = mutableMapOf<String, MutableList<LivingEntity>>()
    for ((type, candidates) in all.groupBy { it.type.name }) {
        val rule = livingCfg.entities[type]
        val matches = type.isMatch(livingCfg.match) != null
        val clean = rule?.clean ?: (matches == livingCfg.black)
        if (!clean) continue
        val settings = rule?.settings?.resolve(livingCfg.settings) ?: livingCfg.settings
        val eligible = candidates.filter { entity ->
            (settings.name || entity.customName == null) &&
                (settings.lead || !entity.isLeashed) &&
                (settings.mount || (!entity.isInsideVehicle && !Compatibility.hasPassengers(entity)))
        }.toMutableList()
        PL.debug { "世界${name}清理${type}x${eligible.size}, 实体规则=${rule != null}" }
        groupBy[type] = eligible
    }

    var count = 0
    groupBy.values.forEach {
        count += it.size
        it.forEach(LivingEntity::remove)
    }
    PL.debug { "世界${name}生物清理完成($count/${total})" }
    return count to total
}