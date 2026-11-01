package com.meshline.chat.security

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Optional QR pairing. The inviter shows a QR holding its device ID and a one-time secret token.
 * The joiner scans it, connects straight to that device and proves it saw the QR by sending an
 * HMAC over both IDs (the token itself never goes over the air). The joiner trusts the inviter
 * because the inviter's ID comes from the QR and is verified by signature in the normal handshake.
 * Pure JVM, so it is unit-testable.
 */
object PairInvite {
    const val SCHEME_PREFIX = "meshline://pair?"
    const val TOKEN_BYTES = 16

    class Invite(val deviceId: String, val token: ByteArray, val name: String)

    fun newToken(): ByteArray = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }

    fun encode(invite: Invite): String {
        val t = Base64.getUrlEncoder().withoutPadding().encodeToString(invite.token)
        val n = URLEncoder.encode(invite.name.take(32), "UTF-8")
        return "${SCHEME_PREFIX}i=${invite.deviceId}&t=$t&n=$n"
    }

    /** Returns null for anything that is not a well-formed Meshline invite. */
    fun decode(raw: String): Invite? {
        val text = raw.trim()
        if (!text.startsWith(SCHEME_PREFIX) || text.length > 400) return null
        val params = HashMap<String, String>()
        for (part in text.removePrefix(SCHEME_PREFIX).split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val value = try {
                URLDecoder.decode(part.substring(eq + 1), "UTF-8")
            } catch (_: Exception) {
                return null
            }
            params[part.substring(0, eq)] = value
        }
        val id = params["i"] ?: return null
        if (id.length != 64 || !id.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val tokenText = params["t"] ?: return null
        val token = try {
            Base64.getUrlDecoder().decode(tokenText)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (token.size != TOKEN_BYTES) return null
        val name = (params["n"] ?: "").filter { !it.isISOControl() }.trim().take(48).ifBlank { "Nearby device" }
        return Invite(id, token, name)
    }

    fun proof(token: ByteArray, joinerId: String, inviterId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token, "HmacSHA256"))
        val out = mac.doFinal("Meshline/pair/v1|$joinerId|$inviterId".toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().withoutPadding().encodeToString(out)
    }

    fun verifyProof(token: ByteArray, joinerId: String, inviterId: String, proof: String): Boolean =
        MessageDigest.isEqual(
            proof(token, joinerId, inviterId).toByteArray(StandardCharsets.UTF_8),
            proof.toByteArray(StandardCharsets.UTF_8)
        )
}
