package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eplugin.command.ECommand

object Redstone : ECommand(PL, "redstone", "(?i)redstone", false, "eclean.admin") {
    override val usage = "/eclean redstone [on|off|status|stats [世界名]]"

    override fun onCommand(sender: CommandSender, args: Array<out String>) {
        if (featureCommand(sender, args, Feature.REDSTONE)) return
        val stats = args.getOrNull(1).equals("stats", true)
        if (args.size !in 1..(if (stats) 3 else 2)) { sender.sendMessage(usage); return }
        val world = args.getOrNull(if (stats) 2 else 1)
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
    override fun onTabComplete(sender: CommandSender, args: Array<out String>, complete: MutableList<String>) {
        if (args.size == 2) complete.addAll(listOf("on", "off", "status", "stats"))
        if (args.size == 3 && args[1].equals("stats", true)) complete.addAll(Bukkit.getWorlds().map { it.name })
    }
}
