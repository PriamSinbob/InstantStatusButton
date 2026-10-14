package com.instantstatusbutton.app.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object Pkce {
    private val random = SecureRandom()

    fun randomString(bytes: Int = 32): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return b64(b)
    }

    fun challengeFor(verifier: String): String =
        b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun b64(b: ByteArray) =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
