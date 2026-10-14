package com.instantstatusbutton.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.instantstatusbutton.app.data.Provider
import com.instantstatusbutton.app.data.SecureStore
import com.instantstatusbutton.app.data.Tokens
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** OAuth 2.0 for X (authorization code + PKCE) and LinkedIn (authorization code, `w_member_social`). */
object OAuthManager {
    const val REDIRECT_X = "instantstatusbutton://oauth/x"
    const val REDIRECT_LINKEDIN = "instantstatusbutton://oauth/linkedin"

    private const val X_AUTHORIZE = "https://twitter.com/i/oauth2/authorize"
    private const val X_TOKEN = "https://api.twitter.com/2/oauth2/token"
    private const val X_ME = "https://api.twitter.com/2/users/me"
    private const val X_SCOPES = "tweet.read tweet.write users.read offline.access"

    private const val LI_AUTHORIZE = "https://www.linkedin.com/oauth/v2/authorization"
    private const val LI_TOKEN = "https://www.linkedin.com/oauth/v2/accessToken"
    private const val LI_USERINFO = "https://api.linkedin.com/v2/userinfo"

    // w_member_social posts; openid+profile are only used to learn the member id (needed for the author URN).
    private const val LI_SCOPES = "w_member_social openid profile"

    private const val REFRESH_MARGIN_MS = 120_000L

    fun redirectUri(p: Provider) = if (p == Provider.X) REDIRECT_X else REDIRECT_LINKEDIN

    /** Opens the provider's consent page in a Custom Tab. Returns an error message, or null if started. */
    fun start(context: Context, provider: Provider): String? {
        val store = SecureStore.get(context)
        val clientId = store.clientId(provider)
        if (clientId.isBlank()) return "Enter the ${name(provider)} client ID first"

        val state = Pkce.randomString(16)
        val verifier = Pkce.randomString(48)
        store.savePending(provider, state, verifier)

        val url = when (provider) {
            Provider.X -> Uri.parse(X_AUTHORIZE).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_X)
                .appendQueryParameter("scope", X_SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", Pkce.challengeFor(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .build()
            Provider.LINKEDIN -> Uri.parse(LI_AUTHORIZE).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_LINKEDIN)
                .appendQueryParameter("scope", LI_SCOPES)
                .appendQueryParameter("state", state)
                .build()
        }
        val tabs = CustomTabsIntent.Builder().build()
        if (context !is Activity) tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tabs.launchUrl(context, url)
        return null
    }

    /** Called with the redirect URI. Returns a message to show the user. */
    suspend fun handleRedirect(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val store = SecureStore.get(context)
        val (provider, state, verifier) = store.pending() ?: return@withContext "No sign-in in progress"
        store.clearPending()
        val label = name(provider)

        uri.getQueryParameter("error")?.let { err ->
            return@withContext "$label sign-in failed: ${uri.getQueryParameter("error_description") ?: err}"
        }
        if (uri.getQueryParameter("state") != state) return@withContext "$label sign-in failed: state mismatch"
        val code = uri.getQueryParameter("code") ?: return@withContext "$label sign-in failed: no code returned"

        try {
            val tokens = exchangeCode(store, provider, code, verifier)
            store.saveTokens(provider, tokens)
            val account = when (provider) {
                Provider.X -> fetchXAccount(tokens.accessToken)
                Provider.LINKEDIN -> fetchLinkedInAccount(store, tokens.accessToken)
            }
            store.setAccountName(provider, account)
            "Connected to $label" + (account?.let { " as $it" } ?: "")
        } catch (e: Exception) {
            store.clearTokens(provider)
            "$label sign-in failed: ${e.message}"
        }
    }

    fun disconnect(context: Context, p: Provider) {
        val store = SecureStore.get(context)
        store.clearTokens(p)
        if (p == Provider.LINKEDIN) store.linkedInMemberId = null
    }

    /**
     * Returns a usable access token, refreshing first when it is (about to be) expired.
     * @throws PostException (non-retryable) when not connected or the session cannot be renewed.
     */
    suspend fun validAccessToken(context: Context, p: Provider, forceRefresh: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val store = SecureStore.get(context)
            val t = store.tokens(p) ?: throw PostException("${name(p)} is not connected. Open the app and connect it.")
            val expired = t.expiresAtMillis != 0L && System.currentTimeMillis() > t.expiresAtMillis - REFRESH_MARGIN_MS
            if (!expired && !forceRefresh) return@withContext t.accessToken
            val refresh = t.refreshToken
                ?: throw PostException("${name(p)} session expired. Open the app and connect again.")
            try {
                val fresh = refresh(store, p, refresh)
                store.saveTokens(p, fresh)
                fresh.accessToken
            } catch (e: IOException) {
                throw PostException("Network error while refreshing ${name(p)} login", true, e)
            }
        }

    // ---- token endpoints -------------------------------------------------------------------

    private fun exchangeCode(store: SecureStore, p: Provider, code: String, verifier: String): Tokens {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri(p))
            .add("client_id", store.clientId(p))
        if (p == Provider.X) form.add("code_verifier", verifier) else form.add("client_secret", store.clientSecret(p))
        return tokenRequest(store, p, form.build())
    }

    private fun refresh(store: SecureStore, p: Provider, refreshToken: String): Tokens {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", store.clientId(p))
        if (p == Provider.LINKEDIN) form.add("client_secret", store.clientSecret(p))
        val fresh = tokenRequest(store, p, form.build())
        // Providers may or may not rotate the refresh token; keep the old one if none returned.
        return if (fresh.refreshToken == null) fresh.copy(refreshToken = refreshToken) else fresh
    }

    private fun tokenRequest(store: SecureStore, p: Provider, body: FormBody): Tokens {
        val req = Request.Builder().url(if (p == Provider.X) X_TOKEN else LI_TOKEN).post(body)
        // X confidential clients authenticate with HTTP Basic; public clients send only client_id.
        if (p == Provider.X && store.clientSecret(p).isNotBlank()) {
            req.header("Authorization", Credentials.basic(store.clientId(p), store.clientSecret(p)))
        }
        Http.client.newCall(req.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(errorText(resp.code, text))
            val j = JSONObject(text)
            val expiresIn = j.optLong("expires_in", 0L)
            return Tokens(
                accessToken = j.getString("access_token"),
                refreshToken = j.optString("refresh_token", "").ifEmpty { null },
                expiresAtMillis = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
            )
        }
    }

    private fun fetchXAccount(token: String): String? = try {
        Http.client.newCall(Request.Builder().url(X_ME).header("Authorization", "Bearer $token").build())
            .execute().use { r ->
                if (!r.isSuccessful) null
                else JSONObject(r.body?.string().orEmpty()).optJSONObject("data")?.optString("username")?.let { "@$it" }
            }
    } catch (_: Exception) { null }

    private fun fetchLinkedInAccount(store: SecureStore, token: String): String? {
        Http.client.newCall(Request.Builder().url(LI_USERINFO).header("Authorization", "Bearer $token").build())
            .execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw IOException(
                        "could not read your LinkedIn profile (HTTP ${r.code}). " +
                            "Enable the 'Sign In with LinkedIn using OpenID Connect' product on your LinkedIn app."
                    )
                }
                val j = JSONObject(text)
                store.linkedInMemberId = j.getString("sub")
                return j.optString("name", "").ifEmpty { null }
            }
    }

    private fun errorText(code: Int, body: String): String {
        val detail = try {
            val j = JSONObject(body)
            j.optString("error_description").ifEmpty { j.optString("error") }.ifEmpty { j.optString("detail") }
        } catch (_: Exception) { "" }
        return "HTTP $code" + if (detail.isNotEmpty()) ": $detail" else ""
    }

    fun name(p: Provider) = if (p == Provider.X) "X" else "LinkedIn"
}
