package app.nightrecord.data

import app.nightrecord.parser.DeepRating
import app.nightrecord.parser.ExpeditionRun
import app.nightrecord.parser.Labels
import app.nightrecord.parser.PartyMember
import app.nightrecord.parser.RosterEntry
import org.json.JSONArray
import org.json.JSONObject

object RunMapper {
    fun mapRuns(array: JSONArray): List<ExpeditionRun> {
        val out = ArrayList<ExpeditionRun>(array.length())
        for (i in 0 until array.length()) {
            mapRun(array.getJSONObject(i))?.let { out.add(it) }
        }
        return out.sortedBy { it.timestamp }
    }

    fun rosterFromRuns(runs: List<ExpeditionRun>): List<RosterEntry?> {
        val bySlot = LinkedHashMap<Int, String>()
        for (run in runs) {
            if (run.slot in 0..9 && run.character.isNotBlank()) {
                bySlot.putIfAbsent(run.slot, run.character)
            }
        }
        return List(10) { slot -> bySlot[slot]?.let { RosterEntry(slot, it) } }
    }

    fun deepRatingsFromMe(me: JSONObject): List<DeepRating> {
        val profiles = me.optJSONArray("profiles") ?: return emptyList()
        val out = ArrayList<DeepRating>()
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            val deep = profile.optJSONArray("deepRatings") ?: profile.optJSONArray("deep_ratings")
            if (deep != null) {
                for (j in 0 until deep.length()) {
                    val row = deep.optJSONObject(j) ?: continue
                    val name = row.optString("name")
                    val rating = row.optInt("rating", 0)
                    if (name.isBlank() || rating <= 0) continue
                    out.add(
                        DeepRating(
                            slot = row.optInt("slot", 0),
                            name = name,
                            depth = row.optInt("depth", 0),
                            rating = rating,
                            ratingRaw = row.optInt("ratingRaw", rating),
                        ),
                    )
                }
            } else {
                val peak = profile.optInt("peakRating", profile.optInt("peak_rating", 0))
                val depth = profile.optInt("peakDepth", profile.optInt("peak_depth", 0))
                val tag = profile.optString("displayTag").ifBlank { profile.optString("tag") }
                if (peak > 0) {
                    out.add(DeepRating(slot = 0, name = tag.ifBlank { "프로필" }, depth = depth, rating = peak, ratingRaw = peak))
                }
            }
        }
        return out.sortedByDescending { it.rating }
    }

    private fun mapRun(obj: JSONObject): ExpeditionRun? {
        val id = obj.optInt("id", 0)
        if (id <= 0) return null
        val jobId = obj.optInt("jobId", 0)
        if (jobId !in 1..10) return null
        val timestamp = obj.optLong("timestamp", 0L)
        if (timestamp <= 0L) return null
        val mode = obj.optString("mode", "unknown").ifBlank { "unknown" }
        val party = mapParty(obj.optJSONArray("party"))
        val stage = if (obj.has("stage") && !obj.isNull("stage")) obj.optInt("stage") else null
        val bossId = if (obj.has("bossId") && !obj.isNull("bossId")) obj.optInt("bossId") else null
        return ExpeditionRun(
            id = id,
            slot = obj.optInt("slot", 0),
            character = obj.optString("character").ifBlank { "나이트파러" },
            jobId = jobId,
            jobName = obj.optString("jobName").ifBlank { Labels.jobKo(jobId) },
            jobNameEn = obj.optString("jobNameEn").ifBlank { Labels.jobEn(jobId) },
            level = obj.optInt("level").takeIf { it in 1..15 },
            win = obj.optBoolean("win", false),
            mode = mode,
            modeLabel = obj.optString("modeLabel").ifBlank { Labels.mode(mode) },
            depthCandidate = obj.optInt("depthCandidate", 0),
            ratingCandidate = obj.optInt("ratingCandidate", 0),
            ratingRaw = obj.optInt("ratingRaw", 0),
            durationMs = obj.optLong("durationMs", 0L),
            timestamp = timestamp,
            stage = stage,
            stageLabel = obj.optString("stageLabel").ifBlank { Labels.stage(stage) },
            bossId = bossId?.takeIf { it in 0..9 },
            bossName = obj.optString("bossName").ifBlank {
                bossId?.let { Labels.bossKo(it) } ?: "—"
            },
            bossNameEn = obj.optString("bossNameEn"),
            bossVariant = obj.optString("bossVariant", "unknown"),
            party = party,
            teammates = jsonStringList(obj.optJSONArray("teammates")),
            partyKey = obj.optString("partyKey"),
            partySize = obj.optInt("partySize", party.size),
            source = obj.optString("source", "saved"),
            sourceEntry = if (obj.has("sourceEntry")) obj.optInt("sourceEntry") else null,
        )
    }

    private fun mapParty(array: JSONArray?): List<PartyMember> {
        if (array == null) return emptyList()
        val out = ArrayList<PartyMember>()
        for (i in 0 until array.length()) {
            val m = array.optJSONObject(i) ?: continue
            val jobId = m.optInt("jobId", 0)
            if (jobId !in 1..10) continue
            out.add(
                PartyMember(
                    index = m.optInt("index", i + 1),
                    name = m.optString("name"),
                    jobId = jobId,
                    jobName = m.optString("jobName").ifBlank { Labels.jobKo(jobId) },
                    jobNameEn = m.optString("jobNameEn").ifBlank { Labels.jobEn(jobId) },
                    level = m.optInt("level").takeIf { it in 1..15 },
                    self = m.optBoolean("self", i == 0),
                ),
            )
        }
        return out
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }
}
