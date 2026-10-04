package top.e404.eclean.util

import org.bukkit.Chunk
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** New Bukkit members are resolved without linking them on 1.8. */
object Compatibility {
    private val passengers = runCatching { Entity::class.java.getMethod("getPassengers") }.getOrNull()
    private val passenger = runCatching { Entity::class.java.getMethod("getPassenger") }.getOrNull()
    private val forceLoaded = runCatching { Chunk::class.java.getMethod("isForceLoaded") }.getOrNull()
    private val tickets = runCatching { Chunk::class.java.getMethod("getPluginChunkTickets") }.getOrNull()
    private val viewers = runCatching { Chunk::class.java.getMethod("getPlayersSeeingChunk") }.getOrNull()
    private val inUse = runCatching { World::class.java.getMethod("isChunkInUse", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) }.getOrNull()

    val supportsForceLoadedChunks get() = forceLoaded != null
    val supportsChunkUnload get() = viewers != null || tickets != null || inUse != null
    val chunkProtection get() = when {
        viewers != null -> "player viewers"
        tickets != null -> "empty worlds only (viewer API unavailable)"
        else -> "legacy isChunkInUse + player radius"
    }
    fun hasPassengers(entity: Entity): Boolean = runCatching {
        if (passengers != null) (passengers.invoke(entity) as Collection<*>).isNotEmpty()
        else if (passenger != null) passenger.invoke(entity) != null else true
    }.getOrDefault(true)
    fun isForceLoaded(chunk: Chunk): Boolean = if (forceLoaded == null) false else forceLoaded.invoke(chunk) as Boolean
    fun pluginTickets(chunk: Chunk): Int = if (tickets == null) 0 else (tickets.invoke(chunk) as Collection<*>).size
    fun playerViewers(chunk: Chunk): Int? = when {
        viewers != null -> (viewers.invoke(chunk) as Collection<*>).size
        // Ticket-era isChunkInUse can merely mean isChunkLoaded. An empty world
        // proves there are no viewers; otherwise fail closed without the API.
        tickets != null -> if (chunk.world.players.isEmpty()) 0 else null
        inUse != null -> if (inUse.invoke(chunk.world, chunk.x, chunk.z) == true) 1 else 0
        else -> null
    }
    fun isWritableBook(stack: ItemStack) = stack.type.name == "WRITABLE_BOOK" || stack.type.name == "BOOK_AND_QUILL"
    fun sound(vararg names: String): Sound? = names.firstNotNullOfOrNull { runCatching { Sound.valueOf(it) }.getOrNull() }
    fun playSound(player: Player, vararg names: String) { sound(*names)?.let { player.playSound(player.location, it, 1F, 1F) } }
}
