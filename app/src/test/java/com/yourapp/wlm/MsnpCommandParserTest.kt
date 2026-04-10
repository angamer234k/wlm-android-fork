package com.yourapp.wlm

import com.yourapp.wlm.data.remote.msnp.MsnpCommandParser
import org.junit.Assert.*
import org.junit.Test

class MsnpCommandParserTest {

    @Test
    fun `parse NLN command`() {
        val line = "NLN 12345678 user@hotmail.com Display%20Name"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("NLN", parsed.command)
        assertEquals(12345678, parsed.trid)
        assertEquals(3, parsed.parts.size)
    }

    @Test
    fun `parse FLN command`() {
        val line = "FLN user@hotmail.com"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("FLN", parsed.command)
        assertNull(parsed.trid)
        assertEquals("user@hotmail.com", parsed.parts[0])
    }

    @Test
    fun `parse ILN command`() {
        val line = "ILN NLN user@hotmail.com Display%20Name 0"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("ILN", parsed.command)
        assertNull(parsed.trid)
        assertEquals("NLN", parsed.parts[0])
        assertEquals("user@hotmail.com", parsed.parts[1])
    }

    @Test
    fun `parse MSG command`() {
        val line = "MSG user@hotmail.com Display%20Name 42"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("MSG", parsed.command)
        assertNull(parsed.trid)
        assertEquals(4, parsed.parts.size)
    }

    @Test
    fun `parse XFR SB command`() {
        val line = "XFR 5 SB sb.server.com:1863 CKI token123"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("XFR", parsed.command)
        assertEquals(5, parsed.trid)
        assertEquals("SB", parsed.parts[0])

        val sbInfo = MsnpCommandParser.parseXfrResponse(parsed)
        assertNotNull(sbInfo)
        assertEquals("sb.server.com", sbInfo!!.host)
        assertEquals(1863, sbInfo.port)
        assertEquals("token123", sbInfo.authToken)
    }

    @Test
    fun `parse RNG command`() {
        val line = "RNG 123 sb.server.com:1863 CKI token456 caller@hotmail.com Caller%20Name"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("RNG", parsed.command)
        val rngInfo = MsnpCommandParser.parseRngInvitation(parsed)
        assertNotNull(rngInfo)
        assertEquals("123", rngInfo!!.sessionId)
        assertEquals("sb.server.com", rngInfo.host)
        assertEquals(1863, rngInfo.port)
        assertEquals("token456", rngInfo.authToken)
        assertEquals("caller@hotmail.com", rngInfo.callerEmail)
        assertEquals("Caller%20Name", rngInfo.callerDisplayName)
    }

    @Test
    fun `parse USR OK command`() {
        val line = "USR 7 OK user@hotmail.com Display%20Name 1"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("USR", parsed.command)
        assertEquals(7, parsed.trid)

        val userInfo = MsnpCommandParser.parseUsrOk(parsed)
        assertNotNull(userInfo)
        assertEquals("user@hotmail.com", userInfo!!.email)
        assertEquals("Display%20Name", userInfo.displayName)
        assertTrue(userInfo.verified)
    }

    @Test
    fun `parse presence status from NLN`() {
        val line = "NLN 12345678 user@hotmail.com Display%20Name"
        val parsed = MsnpCommandParser.parse(line)

        val presence = MsnpCommandParser.parsePresenceStatus(parsed)
        assertNotNull(presence)
        assertEquals("user@hotmail.com", presence!!.email)
        assertEquals(com.yourapp.wlm.domain.model.PresenceStatus.ONLINE, presence.status)
        assertEquals("Display%20Name", presence.displayName)
    }

    @Test
    fun `parse presence status from FLN`() {
        val line = "FLN user@hotmail.com"
        val parsed = MsnpCommandParser.parse(line)

        val presence = MsnpCommandParser.parsePresenceStatus(parsed)
        assertNotNull(presence)
        assertEquals("user@hotmail.com", presence!!.email)
        assertEquals(com.yourapp.wlm.domain.model.PresenceStatus.OFFLINE, presence.status)
    }

    @Test
    fun `parse UBX personal message`() {
        val body = "<Data><PSM>Hello World</PSM></Data>"
        val pm = MsnpCommandParser.parseUbxPersonalMessage(body)

        assertEquals("Hello World", pm)
    }

    @Test
    fun `parse empty UBX personal message`() {
        val body = "<Data><PSM></PSM></Data>"
        val pm = MsnpCommandParser.parseUbxPersonalMessage(body)

        assertEquals("", pm)
    }

    @Test
    fun `parse command without trid`() {
        val line = "PNG"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("PNG", parsed.command)
        assertNull(parsed.trid)
        assertTrue(parsed.parts.isEmpty())
    }

    @Test
    fun `parse empty line`() {
        val line = ""
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("", parsed.command)
        assertNull(parsed.trid)
        assertTrue(parsed.parts.isEmpty())
    }

    @Test
    fun `parse invalid command`() {
        val line = "INVALID_COMMAND some data"
        val parsed = MsnpCommandParser.parse(line)

        assertEquals("INVALID_COMMAND", parsed.command)
        assertEquals(2, parsed.parts.size)
    }
}
