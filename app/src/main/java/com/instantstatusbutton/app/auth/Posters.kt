package com.instantstatusbutton.app.auth

import android.content.Context
import com.instantstatusbutton.app.data.Provider
import com.instantstatusbutton.app.data.SecureStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val JSON = "application/json; charset=utf-8".toMediaType()

object XPoster {
    private const val TWEETS = "https://api.twitter.com/2/tweets"

    /** Posts [text] with `POST /2/tweets`. Retries once with a refreshed token on 401. */
    suspend fun post(context: Context, text: String) = withContext(Dispatchers.IO) {
        var token = OAuthManager.validAccessToken(context, Provider.X)
        var attempt = 0
        while (true) {
            val body = JSONObject().put("text", text).toString().toRequestBody(JSON)
            val req = Request.Builder().url(TWEETS).header("Authorization", "Bearer $token").post(body).build()
            try {
                Http.client.newCall(req).execute().use { r ->
                    val resp = r.body?.string().orEmpty()
                    when {
                        r.isSuccessful -> return@withContext
                        r.code == 401 && attempt == 0 -> Unit // refresh below
                        else -> throw failure("X", r.code, resp)
                    }
                }
            } catch (e: IOException) {
                throw PostException("Network error posting to X", true, e)
            }
            attempt++
            token = OAuthManager.validAccessToken(context, Provider.X, forceRefresh = true)
        }
    }
}

object LinkedInPoster {
    private const val POSTS = "https://api.linkedin.com/rest/posts"

    /** LinkedIn API version header (YYYYMM). Bump if LinkedIn sunsets this one. */
    private const val API_VERSION = "202510"

    suspend fun post(context: Context, text: String) = withContext(Dispatchers.IO) {
        val store = SecureStore.get(context)
        val memberId = store.linkedInMemberId
            ?: throw PostException("LinkedIn member id unknown. Disconnect and connect LinkedIn again.")
        val payload = JSONObject()
            .put("author", "urn:li:person:$memberId")
            .put("commentary", escapeCommentary(text))
            .put("visibility", "PUBLIC")
            .put(
                "distribution",
                JSONObject()
                    .put("feedDistribution", "MAIN_FEED")
                    .put("targetEntities", JSONArray())
                    .put("thirdPartyDistributionChannels", JSONArray()),
            )
            .put("lifecycleState", "PUBLISHED")
            .put("isReshareDisabledByAuthor", false)
            .toString()

        var token = OAuthManager.validAccessToken(context, Provider.LINKEDIN)
        var attempt = 0
        while (true) {
            val req = Request.Builder().url(POSTS)
                .header("Authorization", "Bearer $token")
                .header("LinkedIn-Version", API_VERSION)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .post(payload.toRequestBody(JSON)).build()
            try {
                Http.client.newCall(req).execute().use { r ->
                    val resp = r.body?.string().orEmpty()
                    when {
                        r.isSuccessful -> return@withContext
                        r.code == 401 && attempt == 0 && store.tokens(Provider.LINKEDIN)?.refreshToken != null -> Unit
                        r.code == 401 -> throw PostException("LinkedIn session expired. Open the app and connect again.")
                        else -> throw failure("LinkedIn", r.code, resp)
                    }
                }
            } catch (e: IOException) {
                throw PostException("Network error posting to LinkedIn", true, e)
            }
            attempt++
            token = OAuthManager.validAccessToken(context, Provider.LINKEDIN, forceRefresh = true)
        }
    }

    /** The Posts API treats these characters as "little text" markup; escape them to post literally. */
    internal fun escapeCommentary(s: String): String {
        val reserved = "\\|{}@[]()<>#*_~"
        val sb = StringBuilder()
        for (c in s) {
            if (c in reserved) sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }
}

private fun failure(network: String, code: Int, body: String): PostException {
    val detail = try {
        val j = JSONObject(body)
        listOf("detail", "message", "title", "error_description").map { j.optString(it) }.firstOrNull { it.isNotEmpty() }
    } catch (_: Exception) { null }
    val retryable = code == 429 || code >= 500
    return PostException("$network error $code" + (detail?.let { ": $it" } ?: ""), retryable)
}
