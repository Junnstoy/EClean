package top.e404.eclean.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.e404.eplugin.config.serialization.RegexSerialization

/** Null means inherit; false, zero and empty collections are explicit overrides. */
@Serializable
data class SettingsOverride(
    val name: Boolean? = null,
    val lead: Boolean? = null,
    val mount: Boolean? = null,
) {
    fun resolve(default: Settings) = Settings(
        name = name ?: default.name,
        lead = lead ?: default.lead,
        mount = mount ?: default.mount,
    )
}

@Serializable
data class LivingOverride(
    val settings: SettingsOverride = SettingsOverride(),
    @SerialName("is_black") val black: Boolean? = null,
    val match: List<@Serializable(RegexSerialization::class) Regex>? = null,
)

@Serializable
data class LivingEntityRule(
    val clean: Boolean? = null,
    val settings: SettingsOverride = SettingsOverride(),
)

@Serializable
data class DropOverride(
    @SerialName("is_black") val black: Boolean? = null,
    val match: List<@Serializable(RegexSerialization::class) Regex>? = null,
    val enchant: Boolean? = null,
    val lore: Boolean? = null,
    @SerialName("written_book") val writtenBook: Boolean? = null,
)

@Serializable
data class DropItemRule(
    val clean: Boolean? = null,
    val enchant: Boolean? = null,
    val lore: Boolean? = null,
    @SerialName("written_book") val writtenBook: Boolean? = null,
)

@Serializable
data class ChunkOverride(
    val settings: SettingsOverride = SettingsOverride(),
    val count: Int? = null,
    val format: String? = null,
    val limit: Map<@Serializable(RegexSerialization::class) Regex, Int>? = null,
) {
    init {
        require(count == null || count >= 0) { "world chunk.count must be non-negative" }
        require(limit == null || limit.values.all { it >= 0 }) { "world chunk.limit must be non-negative" }
    }
}

@Serializable
data class ChunkEntityRule(
    val clean: Boolean? = null,
    val limit: Int? = null,
    val settings: SettingsOverride = SettingsOverride(),
) {
    init {
        require(limit == null || limit >= 0) { "entity chunk.limit must be non-negative" }
    }
}

fun LivingConfig.forWorld(world: String): LivingConfig {
    val rule = worlds[world] ?: return this
    return copy(settings = rule.settings.resolve(settings), black = rule.black ?: black,
        match = rule.match?.toMutableList() ?: match)
}

fun DropConfig.forWorld(world: String): DropConfig {
    val rule = worlds[world] ?: return this
    return copy(black = rule.black ?: black, match = rule.match?.toMutableList() ?: match,
        enchant = rule.enchant ?: enchant, lore = rule.lore ?: lore,
        writtenBook = rule.writtenBook ?: writtenBook)
}

fun ChunkConfig.forWorld(world: String): ChunkConfig {
    val rule = worlds[world] ?: return this
    return copy(settings = rule.settings.resolve(settings), count = rule.count ?: count,
        format = rule.format ?: format, limit = rule.limit?.toMutableMap() ?: limit)
}
