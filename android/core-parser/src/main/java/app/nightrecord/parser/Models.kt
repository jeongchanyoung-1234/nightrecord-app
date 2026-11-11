package app.nightrecord.parser

data class RosterEntry(
    val slot: Int,
    val name: String,
)

data class PartyMember(
    val index: Int,
    val name: String,
    val jobId: Int,
    val jobName: String,
    val jobNameEn: String,
    val level: Int?,
    val self: Boolean,
)

data class ExpeditionRun(
    val id: Int,
    val slot: Int,
    val character: String,
    val jobId: Int,
    val jobName: String,
    val jobNameEn: String,
    val level: Int?,
    val win: Boolean,
    val mode: String,
    val modeLabel: String,
    val depthCandidate: Int,
    val ratingCandidate: Int,
    val ratingRaw: Int,
    val durationMs: Long,
    val timestamp: Long,
    val stage: Int?,
    val stageLabel: String,
    val bossId: Int?,
    val bossName: String,
    val bossNameEn: String,
    val bossVariant: String,
    val party: List<PartyMember>,
    val teammates: List<String>,
    val partyKey: String,
    val partySize: Int,
    val source: String = "saved",
    val sourceEntry: Int? = null,
    val elo: Int? = null,
    val rollingWinRate: Double? = null,
    val cumulativeWinRate: Double? = null,
)

data class DeepRating(
    val slot: Int,
    val name: String,
    val depth: Int,
    val rating: Int,
    val ratingRaw: Int,
)

data class JobStat(
    val jobId: Int,
    val name: String,
    val nameEn: String,
    val runs: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
)

data class MateStat(
    val name: String,
    val runs: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val lastPlayed: Long,
)

data class CompositionStat(
    val key: String,
    val label: String,
    val runs: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
)

data class BossStat(
    val key: String,
    val bossId: Int,
    val name: String,
    val nameEn: String,
    val variant: String,
    val variantLabel: String,
    val runs: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val ownJobs: List<JobStat>,
    val compositions: List<CompositionStat>,
)

data class PartySizes(
    val solo: Int,
    val duo: Int,
    val trio: Int,
)

data class SaveIdentity(
    val profileKey: String?,
    val names: List<String>,
)

data class ParseDiagnostics(
    val savedCount: Int,
    val recoveredCount: Int,
    val historyStart: Int,
    val stageMatchedCount: Int,
)

data class ParsedSave(
    val roster: List<RosterEntry?>,
    val runs: List<ExpeditionRun>,
    val recentRuns: List<ExpeditionRun>,
    val deepRatings: List<DeepRating>,
    val diagnostics: ParseDiagnostics,
    val identity: SaveIdentity,
)

data class OverviewStats(
    val empty: Boolean,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalMs: Long,
    val avgMs: Long,
    val winMs: Long,
    val finalReached: Int,
    val finalRuns: Int,
    val finalRate: Double,
    val recent10Wins: Int,
    val recent10Total: Int,
    val jobs: List<JobStat>,
    val bosses: List<BossStat>,
    val teammates: List<MateStat>,
    val compositions: List<CompositionStat>,
    val partySizes: PartySizes,
    val roster: List<RosterEntry>,
    val modeCounts: Map<String, Int>,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
    val runs: List<ExpeditionRun>,
    val insights: List<String> = emptyList(),
)
