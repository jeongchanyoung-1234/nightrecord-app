package app.nightrecord.parser

import kotlin.math.pow
import kotlin.math.roundToInt

object StatsBuilder {
    private const val ELO_K = 24.0
    private const val ELO_BASE = 1000.0

    fun build(roster: List<RosterEntry?>, runs: List<ExpeditionRun>): OverviewStats {
        val orderedBase = runs.sortedBy { it.timestamp }
        val emptyParty = PartySizes(0, 0, 0)
        if (orderedBase.isEmpty()) {
            return OverviewStats(
                empty = true,
                total = 0,
                wins = 0,
                losses = 0,
                winRate = 0.0,
                currentStreak = 0,
                bestStreak = 0,
                totalMs = 0,
                avgMs = 0,
                winMs = 0,
                finalReached = 0,
                finalRuns = 0,
                finalRate = 0.0,
                recent10Wins = 0,
                recent10Total = 0,
                jobs = emptyList(),
                bosses = emptyList(),
                teammates = emptyList(),
                compositions = emptyList(),
                partySizes = emptyParty,
                roster = roster.filterNotNull(),
                modeCounts = mapOf("normal" to 0, "deep" to 0, "unknown" to 0),
                firstPlayed = null,
                lastPlayed = null,
                runs = emptyList(),
                insights = emptyList(),
            )
        }

        val ordered = annotateRates(orderedBase)
        val wins = ordered.count { it.win }
        val losses = ordered.size - wins
        val totalMs = ordered.sumOf { it.durationMs }
        val winMsList = ordered.filter { it.win }.map { it.durationMs }
        val winMs = if (winMsList.isEmpty()) 0L else winMsList.average().toLong()
        val finalWithStage = ordered.filter { it.stage != null }
        val finalReached = finalWithStage.count { it.stage == 6 }

        var current = 0
        for (run in ordered.asReversed()) {
            if (run.win) current++ else break
        }
        var best = 0
        var streak = 0
        for (run in ordered) {
            if (run.win) {
                streak++
                best = maxOf(best, streak)
            } else {
                streak = 0
            }
        }

        val recent = ordered.takeLast(10)
        val jobs = buildJobs(ordered)
        val bosses = buildBosses(ordered)
        val teammates = buildTeammates(ordered)
        val compositions = buildCompositions(ordered)
        val partySizes = buildPartySizes(ordered)

        val modeCounts = mutableMapOf("normal" to 0, "deep" to 0, "unknown" to 0)
        for (run in ordered) {
            modeCounts[run.mode] = (modeCounts[run.mode] ?: 0) + 1
        }

        val stats = OverviewStats(
            empty = false,
            total = ordered.size,
            wins = wins,
            losses = losses,
            winRate = wins.toDouble() / ordered.size,
            currentStreak = current,
            bestStreak = best,
            totalMs = totalMs,
            avgMs = totalMs / ordered.size,
            winMs = winMs,
            finalReached = finalReached,
            finalRuns = finalWithStage.size,
            finalRate = if (finalWithStage.isEmpty()) 0.0 else finalReached.toDouble() / finalWithStage.size,
            recent10Wins = recent.count { it.win },
            recent10Total = recent.size,
            jobs = jobs,
            bosses = bosses,
            teammates = teammates,
            compositions = compositions,
            partySizes = partySizes,
            roster = roster.filterNotNull(),
            modeCounts = modeCounts,
            firstPlayed = ordered.first().timestamp,
            lastPlayed = ordered.last().timestamp,
            runs = ordered,
        )
        return stats.copy(insights = buildInsights(stats))
    }

    fun filterByMode(stats: OverviewStats, mode: String?): OverviewStats {
        if (mode.isNullOrBlank() || mode == "all") return stats
        val filtered = stats.runs.filter { it.mode == mode }
        return build(stats.roster.map { it }, filtered)
    }

    fun filterByCharacter(runs: List<ExpeditionRun>, character: String?): List<ExpeditionRun> {
        if (character.isNullOrBlank()) return runs
        return runs.filter { it.character == character }
    }

    private fun annotateRates(ordered: List<ExpeditionRun>): List<ExpeditionRun> {
        val eloByCharacter = HashMap<String, Double>()
        var rollingSum = 0
        val window = 10
        return ordered.mapIndexed { i, run ->
            rollingSum += if (run.win) 1 else 0
            if (i >= window) rollingSum -= if (ordered[i - window].win) 1 else 0
            val windowLength = minOf(i + 1, window)
            val rolling = rollingSum.toDouble() / windowLength * 100.0
            val cumulative = ordered.take(i + 1).count { it.win }.toDouble() / (i + 1) * 100.0

            val eloKey = run.character.trim().ifBlank { "__slot_${run.slot}" }
            var elo = eloByCharacter[eloKey] ?: ELO_BASE
            val expected = 1.0 / (1.0 + 10.0.pow((ELO_BASE - elo) / 400.0))
            elo += ELO_K * ((if (run.win) 1.0 else 0.0) - expected)
            eloByCharacter[eloKey] = elo

            run.copy(
                elo = elo.roundToInt(),
                rollingWinRate = rolling,
                cumulativeWinRate = cumulative,
            )
        }
    }

    private fun buildInsights(stats: OverviewStats): List<String> {
        val items = ArrayList<String>()
        val best = stats.jobs.filter { it.runs >= 5 }.maxWithOrNull(
            compareBy<JobStat> { it.winRate }.thenBy { it.runs },
        )
        val most = stats.jobs.firstOrNull()
        val recentPct = if (stats.recent10Total == 0) 0.0 else stats.recent10Wins.toDouble() / stats.recent10Total * 100.0
        val overallPct = stats.winRate * 100.0
        val diff = recentPct - overallPct
        val trend = if (diff >= 0) "전체 대비 +${fmt1(diff)}%p" else "전체 대비 ${fmt1(diff)}%p"

        if (best != null) {
            items.add("5판 이상 최고 승률 직업은 ${best.name} (${best.runs}전 ${best.wins}승 · ${pct(best.winRate)})")
        }
        items.add(
            "최근 ${stats.recent10Total}판 ${stats.recent10Wins}승 ${stats.recent10Total - stats.recent10Wins}패 · ${pct(recentPct / 100.0)} ($trend)",
        )
        if (most != null) {
            items.add("가장 많이 쓴 직업은 ${most.name} (${most.runs}전 · ${pct(most.winRate)})")
        }
        items.add(
            "최종일 도달 ${stats.finalReached}/${stats.finalRuns} (${pct(stats.finalRate)}) · 평균 클리어 ${fmtDuration(stats.winMs)}",
        )
        items.add("현재 연승 ${stats.currentStreak} · 최고 연승 ${stats.bestStreak}")
        items.add(
            "총 ${stats.total}원정 · ${(stats.totalMs / 3600000.0).let { fmt1(it) }}시간 · 직업 ${stats.jobs.size}종",
        )
        return items
    }

    private fun runParty(run: ExpeditionRun): List<PartyMember> {
        if (run.party.isNotEmpty()) return run.party
        return listOf(
            PartyMember(
                index = 1,
                name = run.character.ifBlank { "나" },
                jobId = run.jobId,
                jobName = run.jobName,
                jobNameEn = run.jobNameEn,
                level = run.level,
                self = true,
            ),
        )
    }

    private fun compositionLabel(party: List<PartyMember>): String {
        val jobs = party.sortedBy { it.jobId }
        val prefix = if (party.size == 1) "솔로" else "${party.size}인"
        return "$prefix · ${jobs.joinToString(" + ") { it.jobName }}"
    }

    private fun buildJobs(ordered: List<ExpeditionRun>): List<JobStat> {
        val byJob = LinkedHashMap<Int, MutableList<ExpeditionRun>>()
        for (run in ordered) {
            byJob.getOrPut(run.jobId) { ArrayList() }.add(run)
        }
        return byJob.entries
            .map { (jobId, list) ->
                val jobWins = list.count { it.win }
                JobStat(
                    jobId = jobId,
                    name = list.first().jobName.ifBlank { Labels.jobKo(jobId) },
                    nameEn = list.first().jobNameEn.ifBlank { Labels.jobEn(jobId) },
                    runs = list.size,
                    wins = jobWins,
                    losses = list.size - jobWins,
                    winRate = jobWins.toDouble() / list.size,
                )
            }
            .sortedByDescending { it.runs }
    }

    private fun buildTeammates(ordered: List<ExpeditionRun>): List<MateStat> {
        val mateMap = LinkedHashMap<String, MutableList<ExpeditionRun>>()
        for (run in ordered) {
            for (name in run.teammates.distinct()) {
                if (name.isBlank()) continue
                mateMap.getOrPut(name) { ArrayList() }.add(run)
            }
        }
        return mateMap.entries
            .map { (name, list) ->
                val mateWins = list.count { it.win }
                MateStat(
                    name = name,
                    runs = list.size,
                    wins = mateWins,
                    losses = list.size - mateWins,
                    winRate = mateWins.toDouble() / list.size,
                    lastPlayed = list.maxOf { it.timestamp },
                )
            }
            .sortedWith(compareByDescending<MateStat> { it.runs }.thenByDescending { it.lastPlayed }.thenBy { it.name })
    }

    private fun buildCompositions(ordered: List<ExpeditionRun>): List<CompositionStat> {
        val map = LinkedHashMap<String, MutableList<ExpeditionRun>>()
        for (run in ordered) {
            val party = runParty(run)
            val key = party.map { it.jobId }.sorted().joinToString("-")
            map.getOrPut(key) { ArrayList() }.add(run)
        }
        return map.entries
            .map { (key, list) ->
                val wins = list.count { it.win }
                CompositionStat(
                    key = key,
                    label = compositionLabel(runParty(list.first())),
                    runs = list.size,
                    wins = wins,
                    losses = list.size - wins,
                    winRate = wins.toDouble() / list.size,
                )
            }
            .sortedWith(compareByDescending<CompositionStat> { it.runs }.thenByDescending { it.winRate }.thenBy { it.label })
    }

    private fun buildBosses(ordered: List<ExpeditionRun>): List<BossStat> {
        val bossMap = LinkedHashMap<String, MutableList<ExpeditionRun>>()
        for (run in ordered) {
            val bossId = run.bossId ?: continue
            val key = "$bossId:${run.bossVariant}"
            bossMap.getOrPut(key) { ArrayList() }.add(run)
        }
        val variantOrder = mapOf("normal" to 0, "everdark" to 1, "unknown" to 2)
        return bossMap.entries
            .map { (key, list) ->
                val bossWins = list.count { it.win }
                val ownJobs = buildJobs(list)
                val compositions = buildCompositions(list)
                val first = list.first()
                BossStat(
                    key = key,
                    bossId = first.bossId ?: -1,
                    name = first.bossName,
                    nameEn = first.bossNameEn,
                    variant = first.bossVariant,
                    variantLabel = when (first.bossVariant) {
                        "everdark" -> "영원한 밤의 왕"
                        "normal" -> "기본"
                        else -> "미확인"
                    },
                    runs = list.size,
                    wins = bossWins,
                    losses = list.size - bossWins,
                    winRate = bossWins.toDouble() / list.size,
                    ownJobs = ownJobs,
                    compositions = compositions,
                )
            }
            .sortedWith(
                compareBy<BossStat> { it.bossId }
                    .thenBy { variantOrder[it.variant] ?: 9 },
            )
    }

    private fun buildPartySizes(ordered: List<ExpeditionRun>): PartySizes {
        var solo = 0
        var duo = 0
        var trio = 0
        for (run in ordered) {
            when (run.partySize.coerceIn(1, 3)) {
                1 -> solo++
                2 -> duo++
                else -> trio++
            }
        }
        return PartySizes(solo, duo, trio)
    }

    private fun pct(rate: Double): String = "${(rate * 1000).roundToInt() / 10.0}%"
    private fun fmt1(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
    private fun fmtDuration(ms: Long): String {
        if (ms <= 0) return "—"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}
