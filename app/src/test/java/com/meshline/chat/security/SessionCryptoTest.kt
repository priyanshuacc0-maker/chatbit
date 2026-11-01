package com.meshline.chat.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class SessionCryptoTest {
    @Test
    fun bothPeersDeriveTheSameKeyAndCanExchangeEncryptedMessages() {
        val alice = SessionCrypto.newKeyPair()
        val bob = SessionCrypto.newKeyPair()
        val aliceId = "alice-device"
        val bobId = "bob-device"

        val aliceKey = SessionCrypto.deriveSessionKey(
            alice, SessionCrypto.encodePublicKey(bob), aliceId, bobId
        )
        val bobKey = SessionCrypto.deriveSessionKey(
            bob, SessionCrypto.encodePublicKey(alice), bobId, aliceId
        )
        assertArrayEquals(aliceKey.encoded, bobKey.encoded)

        val packetId = "packet-1"
        val (iv, cipherText) = SessionCrypto.encrypt("hello over the air", aliceKey, packetId)
        assertEquals("hello over the air", SessionCrypto.decrypt(iv, cipherText, bobKey, packetId))
    }

    @Test
    fun changingTheAssociatedPacketIdInvalidatesTheMessage() {
        val a = SessionCrypto.newKeyPair()
        val b = SessionCrypto.newKeyPair()
        val keyA = SessionCrypto.deriveSessionKey(a, SessionCrypto.encodePublicKey(b), "a", "b")
        val keyB = SessionCrypto.deriveSessionKey(b, SessionCrypto.encodePublicKey(a), "b", "a")
        val (iv, ciphertext) = SessionCrypto.encrypt("authenticated", keyA, "message-123")
        try {
            SessionCrypto.decrypt(iv, ciphertext, keyB, "message-456")
            fail("AES-GCM must reject a changed associated-data identifier")
        } catch (_: Exception) {
            // Expected authentication failure.
        }
    }

    @Test
    fun eachEncryptionUsesANewNonce() {
        val a = SessionCrypto.newKeyPair()
        val b = SessionCrypto.newKeyPair()
        val key = SessionCrypto.deriveSessionKey(a, SessionCrypto.encodePublicKey(b), "a", "b")
        val one = SessionCrypto.encrypt("same", key, "one")
        val two = SessionCrypto.encrypt("same", key, "two")
        assertNotEquals(one.first, two.first)
    }
}
