package com.instantstatusbutton.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** OAuth tokens for one provider. [expiresAtMillis] == 0 means "unknown / does not expire". */
data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
)

enum class Provider(val id: String) { X("x"), LINKEDIN("li") }

/**
 * Everything sensitive (developer client IDs/secrets, access and refresh tokens, pending OAuth state)
 * is kept in EncryptedSharedPreferences.
 */
class SecureStore private constructor(private val prefs: SharedPreferences) {

    fun clientId(p: Provider): String = prefs.getString("${p.id}.client_id", "") ?: ""
    fun clientSecret(p: Provider): String = prefs.getString("${p.id}.client_secret", "") ?: ""

    fun setClientCredentials(p: Provider, id: String, secret: String) {
        prefs.edit().putString("${p.id}.client_id", id.trim()).putString("${p.id}.client_secret", secret.trim()).apply()
    }

    fun tokens(p: Provider): Tokens? {
        val access = prefs.getString("${p.id}.access", null) ?: return null
        return Tokens(access, prefs.getString("${p.id}.refresh", null), prefs.getLong("${p.id}.expires_at", 0L))
    }

    fun saveTokens(p: Provider, t: Tokens) {
        prefs.edit()
            .putString("${p.id}.access", t.accessToken)
            .putString("${p.id}.refresh", t.refreshToken)
            .putLong("${p.id}.expires_at", t.expiresAtMillis)
            .apply()
    }

    fun clearTokens(p: Provider) {
        prefs.edit().remove("${p.id}.access").remove("${p.id}.refresh").remove("${p.id}.expires_at")
            .remove("${p.id}.account").apply()
    }

    /** Human-readable account (X username, LinkedIn name) and, for LinkedIn, the member id. */
    fun accountName(p: Provider): String? = prefs.getString("${p.id}.account", null)
    fun setAccountName(p: Provider, name: String?) = prefs.edit().putString("${p.id}.account", name).apply()

    var linkedInMemberId: String?
        get() = prefs.getString("li.member_id", null)
        set(v) = prefs.edit().putString("li.member_id", v).apply()

    fun isConnected(p: Provider) = tokens(p) != null

    // Pending OAuth authorization (state + PKCE verifier) while the browser is open.
    fun savePending(p: Provider, state: String, verifier: String) {
        prefs.edit().putString("pending.provider", p.id).putString("pending.state", state)
            .putString("pending.verifier", verifier).apply()
    }

    fun pending(): Triple<Provider, String, String>? {
        val p = Provider.entries.firstOrNull { it.id == prefs.getString("pending.provider", null) } ?: return null
        val state = prefs.getString("pending.state", null) ?: return null
        return Triple(p, state, prefs.getString("pending.verifier", "") ?: "")
    }

    fun clearPending() {
        prefs.edit().remove("pending.provider").remove("pending.state").remove("pending.verifier").apply()
    }

    companion object {
        @Volatile private var instance: SecureStore? = null

        fun get(context: Context): SecureStore = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): SecureStore {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "secure_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecureStore(prefs)
        }
    }
}
