package top.e404.eclean.command

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.clean.Clean
import top.e404.eclean.config.Config
import top.e404.eclean.config.Lang
import top.e404.eplugin.command.ECommand

object Reload : ECommand(
    PL,
    "reload",
    "(?i)r|reload",
    false,
    "eclean.admin"
) {
    override val usage get() = Lang["command.usage.reload"]

    override fun onCommand(sender: CommandSender, args: Array<out String>) {
        try {
            Lang.load(sender)
            Config.load(sender)
            Clean.schedule()
            plugin.sendMsgWithPrefix(sender, Lang["command.reload_done"])
        } catch (ex: Exception) {
            plugin.warn("重新加载配置失败", ex)
            sender.sendMessage("重新加载失败，原有清理配置仍生效，请查看控制台")
        }
    }
}
