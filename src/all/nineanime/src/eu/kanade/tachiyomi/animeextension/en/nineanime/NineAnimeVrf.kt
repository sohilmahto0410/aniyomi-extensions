package eu.kanade.tachiyomi.animeextension.en.nineanime

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 9anime uses a VRF (Verification Request Filter) system to protect its API.
 * The client must encrypt parameters and send the result as a `vrf` query param.
 *
 * The implementation below is based on the publicly reverse-engineered algorithm
 * used by 9anime.to as of early 2024. It may break whenever 9anime rotates their key.
 *
 * Key rotation is the most common cause of extension breakage — bump [KEY] and
 * [CIPHER_KEY] when that happens.
 */
object NineAnimeVrf {

    // ── Keys (rotate here when 9anime changes them) ──────────────────────────
    private val KEY = "5WoeIxX7rFcJDwMk".toByteArray(StandardCharsets.UTF_8)
    private val CIPHER_KEY = "xtEBJFZJ&rq$2Puf".toByteArray(StandardCharsets.UTF_8)

    // ── Encoding table used by 9anime's custom base64 variant ─────────────────
    private const val ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Produces a VRF string for a given [id] (anime id, episode id, etc.).
     * Call this to build `?vrf=<result>` query parameters.
     */
    fun encode(id: String): String {
        val rc4Encrypted = rc4Encrypt(id.toByteArray(StandardCharsets.UTF_8), KEY)
        val b64 = Base64.encodeToString(rc4Encrypted, Base64.NO_WRAP)
        return base64urlEncode(vrfShift(b64))
    }

    /**
     * Decodes a VRF-encoded URL returned by the server back to a plain URL.
     */
    fun decode(encoded: String): String {
        val decoded = base64urlDecode(encoded)
        val shifted = vrfShift(decoded)
        val b64Decoded = Base64.decode(shifted, Base64.DEFAULT)
        return rc4Decrypt(b64Decoded, CIPHER_KEY).toString(StandardCharsets.UTF_8)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** RC4 encrypt — used for the VRF encode path. */
    private fun rc4Encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "RC4"))
        return cipher.doFinal(data)
    }

    /** RC4 decrypt — used for the VRF decode path. */
    private fun rc4Decrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RC4")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "RC4"))
        return cipher.doFinal(data)
    }

    /**
     * 9anime's custom Caesar-like shift applied to the base64 string.
     * Each character's code-point is shifted by a position-dependent amount.
     */
    private fun vrfShift(input: String): String {
        val sb = StringBuilder(input.length)
        for (i in input.indices) {
            val c = input[i]
            val shift = if (i % 2 == 0) 1 else -1
            sb.append((c.code + shift).toChar())
        }
        return sb.toString()
    }

    /** URL-safe base64 encode (replaces +/= with -%7C). */
    private fun base64urlEncode(input: String): String =
        Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")

    /** Inverse of [base64urlEncode]. */
    private fun base64urlDecode(input: String): String {
        val normalised = input.replace("-", "+").replace("_", "/").let {
            when (it.length % 4) {
                2 -> "$it=="
                3 -> "$it="
                else -> it
            }
        }
        return Base64.decode(normalised, Base64.DEFAULT).toString(StandardCharsets.UTF_8)
    }
}
