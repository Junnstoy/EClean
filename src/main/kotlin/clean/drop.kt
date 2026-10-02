package top.e404.eclean.clean

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.inventory.meta.BookMeta
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.config.forWorld
import top.e404.eclean.util.isMatch
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage
import top.e404.eplugin.EPlugin.Companion.placeholder

private inline val dropCfg get() = Config.config.drop
private inline val trashcanCfg get() = Config.config.trashcan


/**
 * 最近一次清理掉落物的数量
 */
var lastDrop = 0
    private set

/**
 * 清理全服掉落物
 */
fun cleanDrop() {
    if (!dropCfg.enable) {
        PL.debug { "掉落物清理已禁用" }
        return
    }
    val worlds = Bukkit.getWorlds().filterNot { dropCfg.disableWorld.any { regex -> it.name matches regex } }
    PL.buildDebug {
        append("开始清理掉落物, 启用掉落物清理的世界: [")
        worlds.joinTo(this, ", ", transform = World::getName)
        append("]")
    }
    PL.debug { if (dropCfg.enchant) "不清理附魔的物品" else "清理附魔的物品" }
    PL.debug { if (dropCfg.writtenBook) "不清理成书" else "清理成书" }

    var time = System.currentTimeMillis()
    val result = worlds.map { it.cleanDrop() }
    time = System.currentTimeMillis() - time

    lastDrop = result.sumOf { it.first }
    PL.debug { "掉落物清理共${lastDrop}个, 耗时${time}ms" }

    if (noOnline) {
        if (noOnlineMessage) {
            val all = result.sumOf { it.second }
            val finish = dropCfg.finish
            if (finish.isNotBlank()) PL.broadcastMsg(finish.placeholder("clean" to lastDrop, "all" to all))
        }
    } else {
        val all = result.sumOf { it.second }
        val finish = dropCfg.finish
        if (finish.isNotBlank()) PL.broadcastMsg(finish.placeholder("clean" to lastDrop, "all" to all))
    }
}

/**
 * 清理指定世界的掉落物
 *
 * @return Pair(clean, all)
 */
fun World.cleanDrop(): Pair<Int, Int> {
    val dropCfg = Config.config.drop.forWorld(name)
    PL.debug { "" }
    PL.debug { "开始清理世界${name}中的掉落物" }
    // 所有物品
    val waitingForClean = entities.filterIsInstance<Item>().toMutableList()
    PL.buildDebug {
        val items = mutableMapOf<String, Int>()
        append("世界").append(name).append("中的所有掉落物: [")
        for (item in waitingForClean) {
            items.compute(item.itemStack.type.name) { _, v -> (v ?: 0) + 1 }
        }
        items.entries.joinTo(this, ", ") { (k, v) -> "$k: $v" }
        append("]")
    }

    // Preserve the existing "all" count: protected items are excluded before matching.
    waitingForClean.removeIf { item ->
        val stack = item.itemStack
        val rule = dropCfg.materials[stack.type.name]
        ((rule?.enchant ?: dropCfg.enchant) && stack.itemMeta?.hasEnchants() == true) ||
            ((rule?.writtenBook ?: dropCfg.writtenBook) && stack.type == Material.WRITABLE_BOOK
                && (stack.itemMeta as? BookMeta)?.hasPages() == true) ||
            ((rule?.lore ?: dropCfg.lore) && stack.itemMeta?.hasLore() == true)
    }
    val items = mutableMapOf<String, List<Item>>()
    for ((type, candidates) in waitingForClean.groupBy { it.itemStack.type.name }) {
        val rule = dropCfg.materials[type]
        val matches = type.isMatch(dropCfg.match) != null
        if (rule?.clean ?: (matches == dropCfg.black)) {
            PL.debug { "世界${name}清理${type}x${candidates.size}, 物品规则=${rule != null}" }
            items[type] = candidates
        }
    }

    var count = 0
    // 启用从垃圾清理中收集掉落物
    if (trashcanCfg.enable && trashcanCfg.collect) {
        items.values.forEach {
            count += it.size
            Trashcan.addItems(it.map(Item::getItemStack))
            it.forEach(Item::remove)
        }
        Trashcan.update()
    }
    // 不启用垃圾箱收集
    else {
        items.values.forEach {
            count += it.size
            it.forEach(Item::remove)
        }
    }
    PL.debug { "世界${name}掉落物清理完成($count/${waitingForClean.size})" }
    return count to waitingForClean.size
}