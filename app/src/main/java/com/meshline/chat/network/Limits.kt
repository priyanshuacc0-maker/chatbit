package com.meshline.chat.network

/** Size limits shared by the UI and the protocol. */
object Limits {
    const val MAX_CHARS = 4_000
    const val MAX_UTF8_BYTES = 8 * 1024
    /** Nearby Connections' hard limit for a BYTES payload is 32 KiB. */
    const val MAX_PACKET_BYTES = 32 * 1024
}

/** Pure text helpers (no Android dependencies, so they are unit-testable). */
object TextRules {
    /**
     * Strips control characters (they JSON-escape to 6 bytes each) and normalises line breaks.
     * With controls removed, JSON escaping can at most double the size of the text, so an
     * 8 KiB body always fits comfortably inside one 32 KiB Nearby packet after AES-GCM + Base64.
     */
    fun sanitizeBody(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n')
            .filter { it == '\n' || it == '\t' || !it.isISOControl() }
            .trim()

    fun fits(body: String): Boolean =
        body.isNotEmpty() && body.length <= Limits.MAX_CHARS &&
            body.toByteArray(Charsets.UTF_8).size <= Limits.MAX_UTF8_BYTES

    fun sanitizeName(raw: String, fallback: String = "Nearby device"): String =
        raw.filter { !it.isISOControl() }.trim().take(48).ifBlank { fallback }

    fun truncateUtf8(s: String, maxBytes: Int): String {
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val b = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + b > maxBytes) break
            sb.appendCodePoint(cp)
            bytes += b
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
