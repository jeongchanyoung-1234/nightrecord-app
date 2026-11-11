package app.nightrecord.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

class ApiClient(
    private val session: SessionStore,
    private val baseUrl: String = "https://nightrecord.app",
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    fun login(username: String, password: String): JSONObject {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
        val res = post("/api/auth/login", body, authed = false)
        session.accessToken = res.optString("access_token").ifBlank { null }
        session.refreshToken = res.optString("refresh_token").ifBlank { null }
        session.username = res.optJSONObject("user")?.optString("email") ?: username
        return res
    }

    fun logout() {
        try {
            if (!session.accessToken.isNullOrBlank()) {
                post("/api/auth/logout", JSONObject(), authed = true, retry = false)
            }
        } catch (_: Exception) {
            // ignore network errors on logout
        } finally {
            session.clear()
        }
    }

    fun refreshSession(): Boolean {
        val refresh = session.refreshToken ?: return false
        return try {
            val body = JSONObject().put("refresh_token", refresh)
            val res = post("/api/auth/refresh", body, authed = false, retry = false)
            val access = res.optString("access_token")
            if (access.isBlank()) return false
            session.accessToken = access
            val nextRefresh = res.optString("refresh_token")
            if (nextRefresh.isNotBlank()) session.refreshToken = nextRefresh
            true
        } catch (_: Exception) {
            false
        }
    }

    fun fetchMe(): JSONObject = get("/api/me")

    fun changePassword(current: String, next: String): JSONObject {
        val body = JSONObject()
            .put("current_password", current)
            .put("password", next)
        return post("/api/me/password", body, authed = true)
    }

    fun updateTag(profileId: String, tag: String): JSONObject {
        val body = JSONObject()
            .put("id", profileId)
            .put("tag", tag)
        return post("/api/me/tag", body, authed = true)
    }

    fun updateAvatar(profileId: String, avatar: String): JSONObject {
        val body = JSONObject()
            .put("id", profileId)
            .put("avatar", avatar)
        return post("/api/me/avatar", body, authed = true)
    }

    fun suggestProfiles(query: String): JSONArray {
        val body = JSONObject().put("query", query)
        return postArray("/api/profiles/suggest", body)
    }

    fun searchProfiles(query: String): JSONArray {
        val body = JSONObject().put("query", query)
        return postArray("/api/profiles/search", body)
    }

    fun profileDetail(tag: String): JSONObject {
        val body = JSONObject().put("tag", tag)
        return post("/api/profiles/detail", body, authed = true)
    }

    fun fetchRelics(profileKey: String? = null): JSONArray {
        val path = if (profileKey.isNullOrBlank()) {
            "/api/relics"
        } else {
            "/api/relics?profile_key=${java.net.URLEncoder.encode(profileKey, Charsets.UTF_8.name())}"
        }
        return getArray(path)
    }

    fun fetchAllExpeditions(): JSONArray {
        val all = JSONArray()
        var offset = 0
        while (true) {
            val page = getArray("/api/expeditions?offset=$offset")
            for (i in 0 until page.length()) all.put(page.getJSONObject(i))
            if (page.length() < 500) break
            offset += 500
        }
        return all
    }

    private fun get(path: String): JSONObject {
        val req = requestBuilder(path).get().build()
        return executeObject(req, retry = true)
    }

    private fun getArray(path: String): JSONArray {
        val req = requestBuilder(path).get().build()
        return executeArray(req, retry = true)
    }

    private fun post(path: String, body: JSONObject, authed: Boolean, retry: Boolean = true): JSONObject {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
        if (authed) session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return executeObject(builder.build(), retry = retry && authed)
    }

    private fun postArray(path: String, body: JSONObject): JSONArray {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .apply { session.accessToken?.let { header("Authorization", "Bearer $it") } }
            .post(body.toString().toRequestBody(jsonType))
        return executeArray(builder.build(), retry = true)
    }

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .apply { session.accessToken?.let { header("Authorization", "Bearer $it") } }

    private fun apiMessage(text: String, code: Int): String {
        return runCatching {
            JSONObject(text).optString("message")
                .ifBlank { JSONObject(text).optString("error") }
        }.getOrNull()?.ifBlank { null } ?: "요청에 실패했습니다. ($code)"
    }

    private fun refreshAfterUnauthorized(req: Request): Boolean = synchronized(REFRESH_LOCK) {
        val sent = req.header("Authorization")?.removePrefix("Bearer ")
        val current = session.accessToken
        if (!current.isNullOrBlank() && current != sent) true else refreshSession()
    }

    private fun executeObject(req: Request, retry: Boolean): JSONObject {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && retry && refreshAfterUnauthorized(req)) {
                val rebuilt = req.newBuilder()
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
                return executeObject(rebuilt, retry = false)
            }
            if (!resp.isSuccessful) throw ApiException(resp.code, apiMessage(text, resp.code))
            if (text.isBlank()) return JSONObject()
            return JSONObject(text)
        }
    }

    private fun executeArray(req: Request, retry: Boolean): JSONArray {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && retry && refreshAfterUnauthorized(req)) {
                val rebuilt = req.newBuilder()
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
                return executeArray(rebuilt, retry = false)
            }
            if (!resp.isSuccessful) throw ApiException(resp.code, apiMessage(text, resp.code))
            if (text.isBlank()) return JSONArray()
            return JSONArray(text)
        }
    }

    private companion object {
        val REFRESH_LOCK = Any()
    }
}
