package app.nightrecord.data

import android.content.Context
import app.nightrecord.data.local.OfflineStore
import app.nightrecord.data.local.WidgetSummary
import app.nightrecord.parser.DeepRating
import app.nightrecord.parser.OverviewStats
import app.nightrecord.parser.StatsBuilder
import app.nightrecord.widget.NightWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HomeData(
    val me: JSONObject,
    val relics: JSONArray,
    val stats: OverviewStats,
    val deepRatings: List<DeepRating>,
    val profiles: List<MeProfile>,
    val syncedAt: Long,
)

object HomeSync {
    suspend fun fetch(context: Context, api: ApiClient, owner: String): HomeData {
        val (me, runs, relics) = withContext(Dispatchers.IO) {
            Triple(
                api.fetchMe(),
                api.fetchAllExpeditions(),
                runCatching { api.fetchRelics() }.getOrElse { JSONArray() },
            )
        }
        val now = System.currentTimeMillis()
        val data = withContext(Dispatchers.Default) { build(me, runs, relics, now) }
        withContext(Dispatchers.IO) {
            runCatching {
                OfflineStore(context).saveSnapshot(owner, me, runs, relics, WidgetSummary.fromStats(data.stats, now))
            }
        }
        NightWidget.refresh(context)
        return data
    }

    suspend fun cached(context: Context, owner: String?): HomeData? {
        val snap = withContext(Dispatchers.IO) { OfflineStore(context).loadSnapshot(owner) } ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { build(snap.me, snap.runs, snap.relics, snap.syncedAt) }.getOrNull()
        }
    }

    private fun build(me: JSONObject, runsJson: JSONArray, relics: JSONArray, syncedAt: Long): HomeData {
        val runs = RunMapper.mapRuns(runsJson)
        val stats = StatsBuilder.build(RunMapper.rosterFromRuns(runs), runs)
        return HomeData(
            me = me,
            relics = relics,
            stats = stats,
            deepRatings = RunMapper.deepRatingsFromMe(me),
            profiles = ProfileMapper.mapMeProfiles(me),
            syncedAt = syncedAt,
        )
    }
}
