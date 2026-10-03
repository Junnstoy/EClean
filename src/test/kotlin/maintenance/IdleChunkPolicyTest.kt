package top.e404.eclean.maintenance

import org.junit.jupiter.api.Test
import top.e404.eclean.config.ChunkUnloadConfig
import java.util.UUID
import kotlin.test.*

class IdleChunkPolicyTest {
    private val a = ChunkAddress(UUID(0, 1), 0, 0)
    private class Access(var chunks: List<ChunkAddress>) : ChunkAccess {
        var eligible = true
        var accept = true
        var inspections = 0
        val requests = mutableListOf<ChunkAddress>()
        override fun snapshot() = chunks
        override fun safe(address: ChunkAddress): Boolean { inspections++; return eligible }
        override fun request(address: ChunkAddress): Boolean { requests.add(address); return accept }
    }
    private fun config(scan: Int = 2, requests: Int = 1) = ChunkUnloadConfig(
        periodTicks = 1, idleTicks = 2, scanBudget = scan, requestBudget = requests)

    @Test
    fun everyProtectionAndUnknownTicketStatePreventsUnloading() {
        assertTrue(safeToUnload(false, 0, 0, false))
        assertFalse(safeToUnload(true, 0, 0, false))
        assertFalse(safeToUnload(false, 1, 0, false))
        assertFalse(safeToUnload(false, null, 0, false))
        assertFalse(safeToUnload(false, 0, 1, false))
        assertFalse(safeToUnload(false, 0, 0, true))
        assertFalse(safeToUnload(false, 0, null, false))
    }

    @Test
    fun waitsBeforeFirstAttemptAndCountsQueueAcceptanceSeparately() {
        val access = Access(listOf(a))
        val policy = IdleChunkPolicy(config(), access)
        repeat(2) { policy.runPass() }
        assertEquals(0, access.requests.size)
        policy.runPass()
        assertEquals(listOf(a), access.requests)
        assertEquals(1L, policy.accepted)
        policy.runPass()
        assertEquals(1, access.requests.size)
    }

    @Test
    fun busyObservationAndReloadBothResetTheIdleTimer() {
        val access = Access(listOf(a))
        val policy = IdleChunkPolicy(config(), access)
        repeat(2) { policy.runPass() }
        access.eligible = false
        policy.runPass()
        access.eligible = true
        repeat(2) { policy.runPass() }
        assertTrue(access.requests.isEmpty())
        policy.forget(a)
        repeat(2) { policy.runPass() }
        assertTrue(access.requests.isEmpty())
        policy.runPass()
        assertEquals(1, access.requests.size)
    }

    @Test
    fun inspectionAndRequestBudgetsAreBothEnforced() {
        val access = Access((0..9).map { a.copy(x = it) })
        val policy = IdleChunkPolicy(config(), access)
        repeat(20) {
            val scans = access.inspections
            val requests = access.requests.size
            policy.runPass()
            assertTrue(access.inspections - scans <= 2)
            assertTrue(access.requests.size - requests <= 1)
        }
        assertEquals(10, access.requests.toSet().size)
    }

    @Test
    fun rejectionIsNotReportedAsAnUnloadAndDoesNotRetryImmediately() {
        val access = Access(listOf(a)).apply { accept = false }
        val policy = IdleChunkPolicy(config(), access)
        repeat(4) { policy.runPass() }
        assertEquals(1L, policy.attempted)
        assertEquals(0L, policy.accepted)
    }

    @Test
    fun disappearingChunksLoseTheirIdleHistory() {
        val access = Access(listOf(a))
        val policy = IdleChunkPolicy(config(), access)
        repeat(2) { policy.runPass() }
        access.chunks = emptyList()
        policy.runPass()
        access.chunks = listOf(a)
        repeat(2) { policy.runPass() }
        assertTrue(access.requests.isEmpty())
    }

    @Test
    fun invalidBudgetsAreRejectedAndFeatureDefaultsOff() {
        assertFalse(ChunkUnloadConfig().enable)
        assertFailsWith<IllegalArgumentException> { ChunkUnloadConfig(idleTicks = 0) }
        assertFailsWith<IllegalArgumentException> { ChunkUnloadConfig(scanBudget = 1, requestBudget = 2) }
        assertFailsWith<IllegalArgumentException> { ChunkUnloadConfig(keepRadius = -1) }
    }
}
