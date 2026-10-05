package top.e404.eclean.test.command

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.bukkit.permissions.PermissionAttachment
import top.e404.eclean.command.*
import top.e404.eclean.config.Config
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.test.*
import kotlin.test.*

abstract class FeaturePermissionsTest {
    private lateinit var original: String
    private val attachments = mutableListOf<PermissionAttachment>()
    @BeforeEach fun prepare() { original = Config.file.readText(); resetConfig(); player.isOp = false }
    @AfterEach fun restore() {
        attachments.forEach { player.removeAttachment(it) }; attachments.clear()
        player.isOp = false; Config.file.writeText(original)
        RedstoneMonitor.stop(); ChunkUnloader.stop(); resetConfig()
    }
    private fun command(vararg args: String) {
        Commands.onCommand(player, plugin.getCommand("eclean")!!, "eclean", args)
    }
    @Test fun ordinaryPlayerCannotToggleAnyFeature() {
        command("worldrules", "off"); command("redstone", "on"); command("chunkunload", "on")
        assertTrue(Config.config.worldRules); assertFalse(Config.config.redstone.enable); assertFalse(Config.config.chunkUnload.enable)
        assertFalse(UnloadStats.hasPerm(player))
    }
    @Test fun individualPermissionsGrantOnlyTheirFeatureAndItsCompletion() {
        for (feature in Feature.values()) {
            val attachment = player.addAttachment(plugin, feature.permission, true)
            try {
                command(feature.command, "on"); assertTrue(feature.enabled(Config.config))
                command(feature.command, "off"); assertFalse(feature.enabled(Config.config))
                val complete = Commands.onTabComplete(player, plugin.getCommand("eclean")!!, "eclean", arrayOf(""))
                assertTrue(feature.command in complete)
                Feature.values().filter { it != feature }.forEach { assertFalse(it.command in complete) }
                assertEquals(feature == Feature.CHUNK_UNLOAD, UnloadStats.hasPerm(player))
                assertFalse(player.hasPermission("eclean.admin"))
            } finally { player.removeAttachment(attachment) }
        }
    }
    @Test fun adminInheritsAllFeaturesAndExplicitChildDenialIsHonoured() {
        val attachment = player.addAttachment(plugin); attachments += attachment
        attachment.setPermission("eclean.admin", true)
        Feature.values().forEach { assertTrue(player.hasPermission(it.permission)) }
        attachment.setPermission("eclean.redstone", false)
        assertFalse(player.hasPermission("eclean.redstone"))
        command("redstone", "on"); assertFalse(Config.config.redstone.enable)
        command("chunkunload", "on"); assertTrue(Config.config.chunkUnload.enable)
        player.isOp = true
        assertFalse(player.hasPermission("eclean.redstone"))
    }
}
