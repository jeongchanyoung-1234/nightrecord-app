package app.nightrecord.data

import org.json.JSONArray
import org.json.JSONObject

data class MeProfile(
    val id: String,
    val profileKey: String,
    val tag: String,
    val saveId: String,
    val displayTag: String,
    val avatar: String?,
    val names: List<String>,
    val matches: Int,
    val wins: Int,
)

data class SearchHit(
    val tag: String,
    val displayTag: String,
    val names: List<String>,
    val matches: Int,
    val wins: Int,
    val peakRating: Int,
    val peakDepth: Int,
)

object ProfileMapper {
    fun mapMeProfiles(me: JSONObject): List<MeProfile> {
        val arr = me.optJSONArray("profiles") ?: return emptyList()
        val out = ArrayList<MeProfile>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            out.add(
                MeProfile(
                    id = p.optString("id"),
                    profileKey = p.optString("profileKey").ifBlank { p.optString("profile_key") },
                    tag = p.optString("displayTag").ifBlank { p.optString("tag") },
                    saveId = p.optString("saveId").ifBlank { p.optString("tag") },
                    displayTag = p.optString("displayTag"),
                    avatar = p.optString("avatar").takeIf { it.isNotBlank() },
                    names = stringList(p.optJSONArray("names")),
                    matches = p.optInt("matches"),
                    wins = p.optInt("wins"),
                ),
            )
        }
        return out
    }

    fun mapSearchHits(arr: JSONArray): List<SearchHit> {
        val out = ArrayList<SearchHit>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            out.add(
                SearchHit(
                    tag = p.optString("tag"),
                    displayTag = p.optString("displayTag").ifBlank { p.optString("tag") },
                    names = stringList(p.optJSONArray("names")),
                    matches = p.optInt("matches"),
                    wins = p.optInt("wins"),
                    peakRating = p.optInt("peakRating", p.optInt("peak_rating")),
                    peakDepth = p.optInt("peakDepth", p.optInt("peak_depth")),
                ),
            )
        }
        return out
    }

    fun deepFromDetail(detail: JSONObject): List<app.nightrecord.parser.DeepRating> {
        val arr = detail.optJSONArray("deepRatings") ?: detail.optJSONArray("deep_ratings") ?: return emptyList()
        val out = ArrayList<app.nightrecord.parser.DeepRating>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val name = row.optString("name")
            val rating = row.optInt("rating", 0)
            if (name.isBlank() || rating <= 0) continue
            out.add(
                app.nightrecord.parser.DeepRating(
                    slot = row.optInt("slot", 0),
                    name = name,
                    depth = row.optInt("depth", 0),
                    rating = rating,
                    ratingRaw = row.optInt("ratingRaw", rating),
                ),
            )
        }
        return out.sortedByDescending { it.rating }
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }
}

object ProfileAvatars {
    data class Avatar(val id: String, val jobId: Int, val name: String, val asset: String)

    val ALL = listOf(
        Avatar("wylder", 1, "추적자", "avatars/wylder.webp"),
        Avatar("guardian", 2, "수호자", "avatars/guardian.webp"),
        Avatar("ironeye", 3, "철의 눈", "avatars/ironeye.webp"),
        Avatar("dutchess", 4, "레이디", "avatars/dutchess.webp"),
        Avatar("raider", 5, "무뢰한", "avatars/raider.webp"),
        Avatar("revenant", 6, "복수자", "avatars/revenant.webp"),
        Avatar("recluse", 7, "은둔자", "avatars/recluse.webp"),
        Avatar("executor", 8, "집행자", "avatars/executor.webp"),
        Avatar("scholar", 9, "학자", "avatars/scholar.webp"),
        Avatar("undertaker", 10, "장의사", "avatars/undertaker.webp"),
    )

    fun byId(id: String?): Avatar? = ALL.firstOrNull { it.id == id }
}
