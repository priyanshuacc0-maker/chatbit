package com.meshline.chat.network

import com.meshline.chat.security.SessionCrypto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketSizeTest {
    /** Mirrors ChatRepository.sendSecure() to measure the real wire size of a message. */
    private fun wireSize(body: String): Int {
        val a = SessionCrypto.newKeyPair()
        val b = SessionCrypto.newKeyPair()
        val key = SessionCrypto.deriveSessionKey(a, SessionCrypto.encodePublicKey(b), "a".repeat(64), "b".repeat(64))
        val inner = JSONObject().put("kind", "message").put("id", "x".repeat(36))
            .put("body", body).put("sentAt", System.currentTimeMillis())
        val packetId = "p".repeat(36)
        val (iv, data) = SessionCrypto.encrypt(inner.toString(), key, packetId)
        return JSONObject().put("type", "secure").put("from", "a".repeat(64))
            .put("packetId", packetId).put("iv", iv).put("data", data)
            .toString().toByteArray(Charsets.UTF_8).size
    }

    @Test
    fun worstCaseAllowedMessageFitsInOneNearbyPacket() {
        // Quotes/newlines/backslashes escape to 2 bytes; '€' (3 bytes) escapes to 6 in some JSON libraries.
        val worstCases = listOf(
            "a" + "\"".repeat(Limits.MAX_CHARS - 2) + "a",
            "a" + "\n".repeat(Limits.MAX_CHARS - 2) + "a",
            "a" + "\\".repeat(Limits.MAX_CHARS - 2) + "a",
            "€".repeat(Limits.MAX_UTF8_BYTES / 3),
        )
        for (raw in worstCases) {
            val body = TextRules.sanitizeBody(raw)
            assertTrue("body must be accepted by the limits", TextRules.fits(body))
            val size = wireSize(body)
            assertTrue("wire size $size must be under the 32 KiB payload limit", size < Limits.MAX_PACKET_BYTES)
        }
    }

    @Test
    fun controlCharactersAreStrippedBecauseTheyBalloonWhenEscaped() {
        assertEquals("ab", TextRules.sanitizeBody("a\u0000\u0001b"))
        assertEquals("a\nb", TextRules.sanitizeBody("a\r\nb"))
    }

    @Test
    fun limitsAreEnforced() {
        assertFalse(TextRules.fits(""))
        assertTrue(TextRules.fits("hello"))
        assertFalse(TextRules.fits("x".repeat(Limits.MAX_CHARS + 1)))
        // 3-byte characters: under the char limit but over the byte limit.
        assertFalse(TextRules.fits("€".repeat(Limits.MAX_CHARS)))
    }

    @Test
    fun advertisedNameIsTruncatedOnUtf8BoundaryWithoutSplittingCharacters() {
        val truncated = TextRules.truncateUtf8("😀".repeat(20), 40)   // 4 bytes each
        assertEquals(10, truncated.codePointCount(0, truncated.length))
        assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= 40)
    }
}
