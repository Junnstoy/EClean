package top.e404.eclean.test.command

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.e404.eclean.command.Feature
import top.e404.eclean.command.setFeature
import top.e404.eclean.config.*
import top.e404.eclean.maintenance.ChunkUnloader
import top.e404.eclean.monitor.RedstoneMonitor
import top.e404.eclean.test.*
import kotlin.test.*

abstract class FeatureCommandsTest {
    private lateinit var original: String
    @BeforeEach fun prepare() { original = Config.file.readText(); resetConfig() }
    @AfterEach fun restore() {
        Config.file.writeText(original)
        RedstoneMonitor.stop(); ChunkUnloader.stop(); resetConfig()
    }
    @Test fun disabledRulesBypassOverridesWithoutDeletingThem() {
        Config.config.living.worlds = mapOf("world" to LivingOverride(black = false))
        Config.config.living.entities = mapOf("COW" to LivingEntityRule(clean = true))
        Config.config.drop.materials = mapOf("STONE" to DropItemRule(clean = true))
        Config.config.chunk.entities = mapOf("COW" to ChunkEntityRule(limit = 0))
        setFeature(Feature.WORLD_RULES, false)
        assertTrue(Config.config.living.forWorld("world").black)
        assertTrue(Config.config.living.forWorld("world").entities.isEmpty())
        assertTrue(Config.config.drop.forWorld("world").materials.isEmpty())
        assertTrue(Config.config.chunk.forWorld("world").entities.isEmpty())
        assertFalse(Config.config.living.entities.isEmpty())
        Config.load(null); assertFalse(Config.config.worldRules)
        setFeature(Feature.WORLD_RULES, true)
        assertFalse(Config.config.living.forWorld("world").black)
        assertEquals(true, Config.config.living.forWorld("world").entities["COW"]?.clean)
    }
    @Test fun switchesPersistAndReleaseTheirState() {
        for (feature in listOf(Feature.REDSTONE, Feature.CHUNK_UNLOAD)) {
            setFeature(feature, true); assertTrue(feature.enabled(Config.config))
            Config.load(null); assertTrue(feature.enabled(Config.config))
            setFeature(feature, false); assertFalse(feature.enabled(Config.config))
        }
        assertNull(RedstoneMonitor.window); assertNull(ChunkUnloader.policy)
    }
    @Test fun failedSaveDoesNotChangeLiveConfiguration() {
        val source = Config.file.readText()
        assertTrue(Config.file.delete()); assertTrue(Config.file.mkdir())
        try {
            Config.file.resolve("blocked").writeText("test")
            assertFails { setFeature(Feature.WORLD_RULES, false) }
            assertTrue(Config.config.worldRules)
        } finally { Config.file.deleteRecursively(); Config.file.writeText(source) }
    }
}
