package com.meshline.chat.security

import java.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Ephemeral P-256 ECDH + HKDF-like SHA-256 derivation + AES-256-GCM. */
object SessionCrypto {
    private val random = SecureRandom()

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun encodePublicKey(pair: KeyPair): String =
        Base64.getEncoder().withoutPadding().encodeToString(pair.public.encoded)

    fun deriveSessionKey(
        localPair: KeyPair,
        remotePublicKeyBase64: String,
        localDeviceId: String,
        remoteDeviceId: String,
    ): SecretKeySpec {
        require(localDeviceId.isNotBlank() && remoteDeviceId.isNotBlank())
        require(localDeviceId != remoteDeviceId) { "A device cannot pair with itself" }
        val remoteBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
        val remotePublic = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(remoteBytes))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPair.private)
        agreement.doPhase(remotePublic, true)
        val sharedSecret = agreement.generateSecret()
        try {
            val ids = listOf(localDeviceId, remoteDeviceId).sorted().joinToString("|")
            val context = ("Meshline/session/v1|$ids").toByteArray(StandardCharsets.UTF_8)
            // HKDF-SHA256 (extract + one-block expand) with a transcript-bound context.
            val salt = MessageDigest.getInstance("SHA-256").digest(context)
            val extract = Mac.getInstance("HmacSHA256")
            extract.init(SecretKeySpec(salt, "HmacSHA256"))
            val pseudorandomKey = extract.doFinal(sharedSecret)
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudorandomKey, "HmacSHA256"))
            expand.update("Meshline AES-256-GCM key".toByteArray(StandardCharsets.UTF_8))
            expand.update(1.toByte())
            val key = expand.doFinal()
            salt.fill(0)
            pseudorandomKey.fill(0)
            return SecretKeySpec(key, "AES")
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun encrypt(plainText: String, key: SecretKeySpec, associatedId: String): Pair<String, String> {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedId.toByteArray(StandardCharsets.UTF_8))
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().withoutPadding().encodeToString(iv) to
            Base64.getEncoder().withoutPadding().encodeToString(cipherText)
    }

    fun decrypt(
        ivBase64: String,
        cipherTextBase64: String,
        key: SecretKeySpec,
        associatedId: String,
    ): String {
        val iv = Base64.getDecoder().decode(ivBase64)
        require(iv.size == 12) { "Invalid AES-GCM nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedId.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64)), StandardCharsets.UTF_8)
    }
}
