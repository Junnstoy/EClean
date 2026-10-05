package top.e404.eclean.test

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import top.e404.eclean.EClean
import top.e404.eclean.command.sendWorldStats
import top.e404.eclean.test.clean.ChunkCleanTest
import top.e404.eclean.test.clean.DropCleanTest
import top.e404.eclean.test.clean.LivingCleanTest
import top.e404.eclean.test.clean.WorldRulesTest
import top.e404.eclean.test.monitor.RedstoneIntegrationTest
import top.e404.eclean.test.command.FeatureCommandsTest
import top.e404.eclean.test.command.FeaturePermissionsTest
import top.e404.eclean.test.papi.FeaturePlaceholdersTest
import top.e404.eclean.unit
import trash.TrashcanTest

@DisplayName("清理单元测试")
class ECleanTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            unit = true
            // Exercise the new version string without pretending MockBukkit is a 26.x API.
            server = MockBukkit.mock(object : ServerMock() {
                override fun getBukkitVersion() = "26.3.build.142-beta"
            })
            plugin = MockBukkit.load(EClean::class.java)
            world = server.addSimpleWorld("world")
            player = server.addPlayer("mock")
            consoleOut
        }

        @JvmStatic
        @AfterAll
        fun finalize() {
            MockBukkit.unmock()
        }
    }

    @Test
    fun worldStatsWithYearBasedVersion() {
        resetConfig()
        val chunk = world.getChunkAt(0, 0)
        chunk.load()
        chunk.isForceLoaded = true
        world.dropItem(Location(world, 8.0, 8.0, 8.0), ItemStack(Material.STONE))
        try {
            server.consoleSender.sendWorldStats(world.name)
            val output = consoleOut
            kotlin.test.assertTrue(output.contains(world.name))
            kotlin.test.assertTrue(output.contains("强加载1个"), output)
        } finally {
            chunk.isForceLoaded = false
            resetConfig()
        }
    }

    @Nested
    @DisplayName("区块清理单元测试")
    inner class TestChunkClean : ChunkCleanTest()

    @Nested
    @DisplayName("掉落物清理单元测试")
    inner class TestDropClean : DropCleanTest()

    @Nested
    @DisplayName("生物清理单元测试")
    inner class TestLivingClean : LivingCleanTest()

    @Nested
    @DisplayName("世界规则与实体优先级")
    inner class TestWorldRules : WorldRulesTest()

    @Nested
    inner class TestRedstone : RedstoneIntegrationTest()

    @Nested
    inner class TestFeatureCommands : FeatureCommandsTest()

    @Nested
    inner class TestFeaturePermissions : FeaturePermissionsTest()

    @Nested
    inner class TestFeaturePlaceholders : FeaturePlaceholdersTest()

    @Nested
    @DisplayName("垃圾桶单元测试")
    inner class TestTrashcan : TrashcanTest()
}
