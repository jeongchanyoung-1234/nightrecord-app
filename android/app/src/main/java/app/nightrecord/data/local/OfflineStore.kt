package app.nightrecord.data.local

import android.content.Context
import app.nightrecord.parser.OverviewStats
import org.json.JSONArray
import org.json.JSONObject

data class OfflineSnapshot(
    val owner: String,
    val me: JSONObject,
    val runs: JSONArray,
    val relics: JSONArray,
    val syncedAt: Long,
)

data class WidgetSummary(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val recent: String,
    val topJobId: Int?,
    val topJobName: String?,
    val updatedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("total", total)
        .put("wins", wins)
        .put("losses", losses)
        .put("winRate", winRate)
        .put("currentStreak", currentStreak)
        .put("bestStreak", bestStreak)
        .put("recent", recent)
        .put("topJobId", topJobId ?: JSONObject.NULL)
        .put("topJobName", topJobName ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)
        .toString()

    companion object {
        fun fromStats(stats: OverviewStats, updatedAt: Long): WidgetSummary {
            val top = stats.jobs.maxByOrNull { it.runs }
            return WidgetSummary(
                total = stats.total,
                wins = stats.wins,
                losses = stats.losses,
                winRate = stats.winRate,
                currentStreak = stats.currentStreak,
                bestStreak = stats.bestStreak,
                recent = stats.runs.takeLast(10).joinToString("") { if (it.win) "W" else "L" },
                topJobId = top?.jobId,
                topJobName = top?.name,
                updatedAt = updatedAt,
            )
        }

        fun fromJson(text: String): WidgetSummary? = runCatching {
            val o = JSONObject(text)
            WidgetSummary(
                total = o.optInt("total"),
                wins = o.optInt("wins"),
                losses = o.optInt("losses"),
                winRate = o.optDouble("winRate", 0.0),
                currentStreak = o.optInt("currentStreak"),
                bestStreak = o.optInt("bestStreak"),
                recent = o.optString("recent"),
                topJobId = if (o.isNull("topJobId")) null else o.optInt("topJobId"),
                topJobName = if (o.isNull("topJobName")) null else o.optString("topJobName"),
                updatedAt = o.optLong("updatedAt"),
            )
        }.getOrNull()
    }
}

class OfflineStore(context: Context) {
    private val dao = NightDatabase.get(context).cacheDao()

    suspend fun saveSnapshot(
        owner: String,
        me: JSONObject,
        runs: JSONArray,
        relics: JSONArray,
        summary: WidgetSummary,
    ) {
        val now = summary.updatedAt
        val rows = (0 until runs.length()).mapNotNull { i ->
            runs.optJSONObject(i)?.let { ExpeditionEntity(timestamp = it.optLong("timestamp"), payload = it.toString()) }
        }
        dao.replaceSnapshot(
            rows,
            listOf(
                CacheEntry(KEY_META, JSONObject().put("owner", owner).put("syncedAt", now).toString(), now),
                CacheEntry(KEY_ME, me.toString(), now),
                CacheEntry(KEY_RELICS, relics.toString(), now),
                CacheEntry(KEY_WIDGET, summary.toJson(), now),
            ),
        )
    }

    suspend fun loadSnapshot(owner: String?): OfflineSnapshot? = runCatching {
        val meta = dao.get(KEY_META)?.let { JSONObject(it.json) } ?: return null
        val cachedOwner = meta.optString("owner")
        if (owner != null && !cachedOwner.equals(owner, ignoreCase = true)) return null
        val runs = JSONArray()
        dao.runPayloads().forEach { runs.put(JSONObject(it)) }
        OfflineSnapshot(
            owner = cachedOwner,
            me = dao.get(KEY_ME)?.let { JSONObject(it.json) } ?: JSONObject(),
            runs = runs,
            relics = dao.get(KEY_RELICS)?.let { JSONArray(it.json) } ?: JSONArray(),
            syncedAt = meta.optLong("syncedAt"),
        )
    }.getOrNull()

    suspend fun saveRelics(relics: JSONArray) {
        dao.put(CacheEntry(KEY_RELICS, relics.toString(), System.currentTimeMillis()))
    }

    suspend fun saveMe(me: JSONObject) {
        dao.put(CacheEntry(KEY_ME, me.toString(), System.currentTimeMillis()))
    }

    suspend fun widgetSummary(): WidgetSummary? =
        runCatching { dao.get(KEY_WIDGET)?.json?.let(WidgetSummary::fromJson) }.getOrNull()

    suspend fun clear() = dao.clearAll()

    private companion object {
        const val KEY_META = "meta"
        const val KEY_ME = "me"
        const val KEY_RELICS = "relics"
        const val KEY_WIDGET = "widget"
    }
}
