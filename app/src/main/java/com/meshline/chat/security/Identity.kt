package com.meshline.chat.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Pure-JVM identity helpers. A device's ID is the SHA-256 of its long-term public key, so an ID
 * cannot be claimed without holding the matching private key (self-certifying identity).
 */
object Identity {
    fun idFromPublicKey(encoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }

    /** What a device signs to prove that an ephemeral key belongs to it, for this specific peer. */
    fun signaturePayload(signerId: String, verifierId: String, ephemeralKeyBase64: String): ByteArray =
        "Meshline/key/v1|$signerId|$verifierId|$ephemeralKeyBase64".toByteArray(StandardCharsets.UTF_8)

    fun verify(identityPublicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean = try {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(identityPublicKey))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        verifier.verify(signature)
    } catch (_: Exception) {
        false
    }
}

/** This phone's long-term signing identity. The private key lives in the Android Keystore. */
class DeviceIdentity {
    private companion object { const val ALIAS = "meshline.identity.ecdsa.v1" }

    private val entry: KeyStore.PrivateKeyEntry = loadOrCreate()
    val publicKeyEncoded: ByteArray = entry.certificate.publicKey.encoded
    val id: String = Identity.idFromPublicKey(publicKeyEncoded)

    fun sign(payload: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(entry.privateKey)
        signer.update(payload)
        return signer.sign()
    }

    private fun loadOrCreate(): KeyStore.PrivateKeyEntry {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { return it }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
        return store.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
    }
}
