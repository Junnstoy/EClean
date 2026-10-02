package top.e404.eclean.test.clean

import com.charleskorn.kaml.Yaml
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.e404.eclean.clean.cleanDenseEntities
import top.e404.eclean.clean.cleanDrop
import top.e404.eclean.clean.cleanLiving
import top.e404.eclean.config.*
import top.e404.eclean.test.*
import kotlin.test.*

abstract class WorldRulesTest {
    private val other get() = server.getWorld("rules_other") ?: server.addSimpleWorld("rules_other")
    private val here get() = Location(world, 8.0, 8.0, 8.0)
    private val there get() = Location(other, 8.0, 8.0, 8.0)

    @BeforeEach
    fun prepareRules() {
        resetConfig()
        world.getChunkAt(0, 0).load()
        other.getChunkAt(0, 0).load()
    }

    @AfterEach
    fun clearRules() {
        other.entities.filterNot { it is Player }.forEach { it.remove() }
        resetConfig()
    }

    @Test
    fun worldSelectionAndEntityOverridesAreSharedByManualAndGlobalCleanup() {
        Config.config.living.apply {
            enable = true
            match = mutableListOf(Regex("ZOMBIE"))
            worlds = mapOf(world.name to LivingOverride(match = listOf(Regex("SHEEP"))))
            entities = mapOf("COW" to LivingEntityRule(clean = true), "SHEEP" to LivingEntityRule(clean = false))
        }
        val localZombie = world.spawnEntity(here, EntityType.ZOMBIE)
        val otherZombie = other.spawnEntity(there, EntityType.ZOMBIE)
        val sheep = world.spawnEntity(here, EntityType.SHEEP)
        val cow = world.spawnEntity(here, EntityType.COW)
        world.cleanLiving()
        assertTrue(localZombie.isValid)
        assertTrue(sheep.isValid)
        assertFalse(cow.isValid)
        assertTrue(otherZombie.isValid)
        cleanLiving()
        assertFalse(otherZombie.isValid)
        assertTrue(localZombie.isValid)
    }

    @Test
    fun entitySettingsOverrideWorldSettingsWithoutLosingInheritedFields() {
        Config.config.living.apply {
            enable = true
            match = mutableListOf(Regex("ZOMBIE|SHEEP"))
            worlds = mapOf(world.name to LivingOverride(settings = SettingsOverride(name = true)))
            entities = mapOf("ZOMBIE" to LivingEntityRule(settings = SettingsOverride(name = false)))
        }
        val zombie = world.spawnEntity(here, EntityType.ZOMBIE).apply { customName = "protected" }
        val sheep = world.spawnEntity(here, EntityType.SHEEP).apply { customName = "world override" }
        cleanLiving()
        assertTrue(zombie.isValid)
        assertFalse(sheep.isValid)
        assertEquals(Settings(false, true, false), SettingsOverride(name = false).resolve(Settings(true, true, false)))
    }

    @Test
    fun materialRulesOverrideWorldProtectionButOtherMaterialsKeepIt() {
        Config.config.drop.apply {
            enable = true
            black = false
            worlds = mapOf(world.name to DropOverride(lore = true))
            materials = mapOf("STONE" to DropItemRule(lore = false))
        }
        fun loreStack(material: Material) = ItemStack(material).apply {
            itemMeta = itemMeta!!.apply { setLore(listOf("keep")) }
        }
        val stone = world.dropItem(here, loreStack(Material.STONE))
        val dirt = world.dropItem(here, loreStack(Material.DIRT))
        val otherDirt = other.dropItem(there, loreStack(Material.DIRT))
        cleanDrop()
        assertFalse(stone.isValid)
        assertTrue(dirt.isValid)
        assertFalse(otherDirt.isValid)
    }

    @Test
    fun exactEntityLimitNeverFallsThroughToLowerPriorityGroup() {
        Config.config.chunk.apply {
            enable = true
            limit = mutableMapOf(Regex("ZOMBIE|SHEEP") to 5)
            worlds = mapOf(world.name to ChunkOverride(limit = mapOf(Regex("ZOMBIE|SHEEP") to 0)))
            entities = mapOf("ZOMBIE" to ChunkEntityRule(limit = 2))
        }
        val localZombies = world.spawnEntities(here, EntityType.ZOMBIE, 4)
        val localSheep = world.spawnEntities(here, EntityType.SHEEP, 3)
        val otherSheep = (0 until 3).map { other.spawnEntity(there, EntityType.SHEEP) }
        cleanDenseEntities()
        assertEquals(2, localZombies.count { it.isValid })
        assertTrue(localSheep.none { it.isValid })
        assertTrue(otherSheep.all { it.isValid })
        cleanDenseEntities()
        assertEquals(2, localZombies.count { it.isValid }, "At-limit entities must not fall through on the next cleanup")
    }

    @Test
    fun playersAndExplicitlyProtectedEntitiesSurviveBroadChunkRules() {
        Config.config.chunk.apply {
            enable = true
            settings = Settings(true, true, true)
            limit = mutableMapOf(Regex(".*") to 0)
            entities = mapOf("SHEEP" to ChunkEntityRule(clean = false), "PLAYER" to ChunkEntityRule(limit = 0))
        }
        val visitor = server.addPlayer()
        visitor.teleport(here)
        val sheep = world.spawnEntity(here, EntityType.SHEEP)
        cleanDenseEntities()
        assertTrue(sheep.isValid)
        assertTrue(visitor.isValid)
    }

    @Test
    fun globalDisableAndExcludedWorldStillPreventAutomaticCleanup() {
        Config.config.living.entities = mapOf("ZOMBIE" to LivingEntityRule(clean = true))
        val zombie = world.spawnEntity(here, EntityType.ZOMBIE)
        cleanLiving()
        assertTrue(zombie.isValid)
        Config.config.living.enable = true
        Config.config.living.disableWorld = mutableListOf(Regex(world.name))
        cleanLiving()
        assertTrue(zombie.isValid)
    }

    @Test
    fun yamlDistinguishesAbsentFalseZeroAndEmptyOverrides() {
        val config = Yaml.default.decodeFromString(ChunkConfig.serializer(), """
            count: 50
            limit:
              ZOMBIE: 10
            worlds:
              world:
                count: 0
                format: ""
                limit: {}
            entities:
              ZOMBIE:
                limit: 0
                settings:
                  name: false
        """.trimIndent())
        val resolved = config.forWorld("world")
        assertEquals(0, resolved.count)
        assertEquals("", resolved.format)
        assertTrue(resolved.limit.isEmpty())
        assertEquals(0, config.entities["ZOMBIE"]!!.limit)
        assertEquals(false, config.entities["ZOMBIE"]!!.settings.name)
        assertNull(config.entities["ZOMBIE"]!!.settings.lead)
        assertEquals(10, config.forWorld("other").limit.values.single())
        val living = Yaml.default.decodeFromString(LivingConfig.serializer(), """
            match: [ZOMBIE]
            worlds:
              world:
                is_black: false
                match: []
        """.trimIndent()).forWorld("world")
        assertFalse(living.black)
        assertTrue(living.match.isEmpty())
    }

    @Test
    fun negativeLimitsAreRejectedDuringDeserialization() {
        listOf("limit: {ZOMBIE: -1}", "worlds: {world: {limit: {ZOMBIE: -1}}}",
            "entities: {ZOMBIE: {limit: -1}}", "worlds: {world: {count: -1}}"
        ).forEach { yaml ->
            assertFails("Must reject $yaml") { Yaml.default.decodeFromString(ChunkConfig.serializer(), yaml) }
        }
    }

    @Test
    fun changingWorldRulesDoesNotReuseStaleResolution() {
        Config.config.living.apply {
            enable = true
            worlds = mapOf(world.name to LivingOverride(match = listOf(Regex("ZOMBIE"))))
        }
        val first = world.spawnEntity(here, EntityType.ZOMBIE)
        cleanLiving()
        assertFalse(first.isValid)
        Config.config.living.worlds = mapOf(world.name to LivingOverride(match = emptyList()))
        val second = world.spawnEntity(here, EntityType.ZOMBIE)
        cleanLiving()
        assertTrue(second.isValid)
    }

    @Test
    fun reloadAcceptsValidRulesAndRetainsActiveConfigWhenNewLimitsAreInvalid() {
        val file = plugin.dataFolder.resolve("config.yml")
        val original = file.readText()
        try {
            val next = Config.config.copy(chunk = ChunkConfig(
                worlds = mapOf(world.name to ChunkOverride(count = 0))))
            val yaml = Config.format.encodeToString(ConfigData.serializer(), next)
            file.writeText(yaml)
            Config.load(null)
            assertEquals(0, Config.config.chunk.forWorld(world.name).count)
            val active = Config.config
            assertTrue(yaml.contains("count: 0"))
            file.writeText(yaml.replace("count: 0", "count: -1"))
            assertFails { Config.load(null) }
            assertSame(active, Config.config)
        } finally {
            file.writeText(original)
            Config.load(null)
        }
    }
}
