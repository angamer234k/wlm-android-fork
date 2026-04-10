package com.yourapp.wlm

import com.yourapp.wlm.domain.model.PresenceStatus
import org.junit.Assert.*
import org.junit.Test

class PresenceStatusTest {

    @Test
    fun `fromMsnpCode returns correct status for NLN`() {
        assertEquals(PresenceStatus.ONLINE, PresenceStatus.fromMsnpCode("NLN"))
    }

    @Test
    fun `fromMsnpCode returns correct status for AWY`() {
        assertEquals(PresenceStatus.AWAY, PresenceStatus.fromMsnpCode("AWY"))
    }

    @Test
    fun `fromMsnpCode returns correct status for BSY`() {
        assertEquals(PresenceStatus.BUSY, PresenceStatus.fromMsnpCode("BSY"))
    }

    @Test
    fun `fromMsnpCode returns correct status for BRB`() {
        assertEquals(PresenceStatus.BE_RIGHT_BACK, PresenceStatus.fromMsnpCode("BRB"))
    }

    @Test
    fun `fromMsnpCode returns correct status for PHN`() {
        assertEquals(PresenceStatus.ON_THE_PHONE, PresenceStatus.fromMsnpCode("PHN"))
    }

    @Test
    fun `fromMsnpCode returns correct status for LUN`() {
        assertEquals(PresenceStatus.OUT_TO_LUNCH, PresenceStatus.fromMsnpCode("LUN"))
    }

    @Test
    fun `fromMsnpCode returns correct status for HDN`() {
        assertEquals(PresenceStatus.APPEAR_OFFLINE, PresenceStatus.fromMsnpCode("HDN"))
    }

    @Test
    fun `fromMsnpCode returns correct status for IDL`() {
        assertEquals(PresenceStatus.IDLE, PresenceStatus.fromMsnpCode("IDL"))
    }

    @Test
    fun `fromMsnpCode returns correct status for FLN`() {
        assertEquals(PresenceStatus.OFFLINE, PresenceStatus.fromMsnpCode("FLN"))
    }

    @Test
    fun `fromMsnpCode returns OFFLINE for unknown code`() {
        assertEquals(PresenceStatus.OFFLINE, PresenceStatus.fromMsnpCode("UNKNOWN"))
    }

    @Test
    fun `fromMsnpCode returns OFFLINE for empty string`() {
        assertEquals(PresenceStatus.OFFLINE, PresenceStatus.fromMsnpCode(""))
    }

    @Test
    fun `msnpCode matches status code`() {
        assertEquals("NLN", PresenceStatus.ONLINE.msnpCode)
        assertEquals("AWY", PresenceStatus.AWAY.msnpCode)
        assertEquals("BSY", PresenceStatus.BUSY.msnpCode)
        assertEquals("BRB", PresenceStatus.BE_RIGHT_BACK.msnpCode)
        assertEquals("PHN", PresenceStatus.ON_THE_PHONE.msnpCode)
        assertEquals("LUN", PresenceStatus.OUT_TO_LUNCH.msnpCode)
        assertEquals("HDN", PresenceStatus.APPEAR_OFFLINE.msnpCode)
        assertEquals("IDL", PresenceStatus.IDLE.msnpCode)
        assertEquals("FLN", PresenceStatus.OFFLINE.msnpCode)
    }

    @Test
    fun `displayName is not empty for all statuses`() {
        PresenceStatus.entries.forEach { status ->
            assertTrue("${status.name} has empty displayName", status.displayName.isNotEmpty())
        }
    }

    @Test
    fun `defaultStatus returns ONLINE`() {
        assertEquals(PresenceStatus.ONLINE, PresenceStatus.defaultStatus())
    }

    @Test
    fun `all enum entries have unique msnpCode`() {
        val codes = PresenceStatus.entries.map { it.msnpCode }
        assertEquals(codes.size, codes.toSet().size)
    }
}
