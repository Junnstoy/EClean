package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eplugin.command.ECommand

object Redstone : ECommand(PL, "redstone", "(?i)redstone", false, "eclean.admin") {
    override val usage = "/eclean redstone [世界名]"

    override fun onCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size !in 1..2) { sender.sendMessage(usage); return }
        val world = args.getOrNull(1)
        if (world != null && Bukkit.getWorld(world) == null) {
            sender.sendMessage("世界不存在: $world"); return
        }
        val state = RedstoneMonitor.window
        if (state == null) { sender.sendMessage("红石统计未启用"); return }
        sender.sendMessage("红石活动（每窗口${state.config.windowTicks} tick，非耗时排名），模式=${state.config.mode}，容量溢出事件=${state.untracked}")
        val entries = state.top(world)
        if (entries.isEmpty()) sender.sendMessage("暂无记录")
        entries.forEach { (p, activity) ->
            sender.sendMessage("${p.world} ${p.x},${p.y},${p.z}: 当前=${activity.current}, 上窗=${activity.previous}, 连续超标=${activity.streak}")
        }
    }
}
