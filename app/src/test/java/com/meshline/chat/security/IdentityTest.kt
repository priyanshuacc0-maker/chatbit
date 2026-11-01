package com.meshline.chat.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class IdentityTest {
    private fun newIdentityKey() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(key: java.security.PrivateKey, payload: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(key); update(payload); sign() }

    @Test
    fun idIsDerivedFromThePublicKeyAndIsStable() {
        val a = newIdentityKey()
        val b = newIdentityKey()
        assertEquals(Identity.idFromPublicKey(a.public.encoded), Identity.idFromPublicKey(a.public.encoded))
        assertNotEquals(Identity.idFromPublicKey(a.public.encoded), Identity.idFromPublicKey(b.public.encoded))
        assertEquals(64, Identity.idFromPublicKey(a.public.encoded).length)
    }

    @Test
    fun validSignatureVerifies() {
        val k = newIdentityKey()
        val payload = Identity.signaturePayload("alice", "bob", "EPHEMERAL")
        assertTrue(Identity.verify(k.public.encoded, payload, sign(k.private, payload)))
    }

    @Test
    fun signatureFromAnotherKeyIsRejected() {
        val real = newIdentityKey()
        val imposter = newIdentityKey()
        val payload = Identity.signaturePayload("alice", "bob", "EPHEMERAL")
        assertFalse(Identity.verify(real.public.encoded, payload, sign(imposter.private, payload)))
    }

    @Test
    fun signatureIsBoundToTheEphemeralKeyAndToTheIntendedPeer() {
        val k = newIdentityKey()
        val signed = sign(k.private, Identity.signaturePayload("alice", "bob", "EPH1"))
        // Swapped ephemeral key (man in the middle) and replay toward a different peer both fail.
        assertFalse(Identity.verify(k.public.encoded, Identity.signaturePayload("alice", "bob", "EPH2"), signed))
        assertFalse(Identity.verify(k.public.encoded, Identity.signaturePayload("alice", "carol", "EPH1"), signed))
    }

    @Test
    fun garbageKeyMaterialDoesNotCrash() {
        assertFalse(Identity.verify(ByteArray(10), ByteArray(4), ByteArray(4)))
    }
}
