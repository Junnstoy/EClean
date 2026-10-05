package top.e404.eclean.command

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eplugin.command.ECommand

object UnloadStats : ECommand(PL, "unloadstats", "(?i)unloadstats", false, "eclean.chunkunload") {
    override val usage = "/eclean unloadstats"
    override fun onCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size != 1) { sender.sendMessage(usage); return }
        val state = ChunkUnloader.policy
        if (state == null) { sender.sendMessage("闲置区块卸载未运行"); return }
        sender.sendMessage("区块检查=${state.scanned}, 跳过=${state.skipped}, 请求尝试=${state.attempted}, 已入队=${state.accepted}（不代表已完成卸载）")
    }
}
