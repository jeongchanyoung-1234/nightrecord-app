package app.nightrecord.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.parser.BossStat
import app.nightrecord.parser.CompositionStat
import app.nightrecord.parser.DeepRating
import app.nightrecord.parser.ExpeditionRun
import app.nightrecord.parser.JobStat
import app.nightrecord.parser.MateStat
import app.nightrecord.parser.OverviewStats
import app.nightrecord.parser.StatsBuilder
import app.nightrecord.ui.components.BarSegment
import app.nightrecord.ui.components.ChoiceChip
import app.nightrecord.ui.components.CompactRateRow
import app.nightrecord.ui.components.EmptyState
import app.nightrecord.ui.components.ExpandablePanel
import app.nightrecord.ui.components.FormDots
import app.nightrecord.ui.components.JobIcon
import app.nightrecord.ui.components.JobIconStack
import app.nightrecord.ui.components.LineChart
import app.nightrecord.ui.components.ModeSegment
import app.nightrecord.ui.components.NightPanel
import app.nightrecord.ui.components.Pill
import app.nightrecord.ui.components.RowDivider
import app.nightrecord.ui.components.SectionHeader
import app.nightrecord.ui.components.SegmentedControl
import app.nightrecord.ui.components.ShowMoreButton
import app.nightrecord.ui.components.StackedBar
import app.nightrecord.ui.components.StatTile
import app.nightrecord.ui.components.ThinBar
import app.nightrecord.ui.components.WinRateRing
import app.nightrecord.ui.components.fmtDuration
import app.nightrecord.ui.components.fmtHours
import app.nightrecord.ui.components.pct
import app.nightrecord.ui.components.pctShort
import app.nightrecord.ui.theme.NightBlue
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightGoldSoft
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightLoss
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText
import app.nightrecord.ui.theme.NightWin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TABS = listOf("개요", "직업", "보스", "매칭", "기록")
private val LIST_PADDING = PaddingValues(bottom = 28.dp)

@Composable
fun StatsScreen(
    title: String,
    subtitle: String,
    stats: OverviewStats,
    deepRatings: List<DeepRating>,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var mode by rememberSaveable { mutableStateOf("all") }
    val filtered = remember(stats, mode) { StatsBuilder.filterByMode(stats, mode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("밤의 기록", color = NightGold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Text(
                    title,
                    color = NightText,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "새로고침", tint = NightGold)
                }
            }
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.Close, contentDescription = "닫기", tint = NightMuted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ModeSegment(selected = mode, counts = stats.modeCounts, onSelect = { mode = it })
        Spacer(Modifier.height(6.dp))

        TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = NightGold,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[tab]),
                    color = NightGold,
                    height = 2.dp,
                )
            },
            divider = { RowDivider() },
        ) {
            TABS.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            label,
                            color = if (tab == index) NightGold else NightMuted,
                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.empty) {
            EmptyState(
                if (stats.empty) {
                    "아직 서버에 쌓인 원정이 없습니다. PC에서 nightrecord.app 에 로그인한 뒤 세이브를 분석하세요."
                } else {
                    "이 모드에 해당하는 원정이 없습니다."
                },
            )
            return
        }

        when (tab) {
            0 -> OverviewTab(stats, filtered, deepRatings, showModeCompare = mode == "all")
            1 -> JobsTab(filtered)
            2 -> BossesTab(filtered)
            3 -> MatchingTab(filtered)
            4 -> RecordsTab(filtered.runs)
        }
    }
}

// ───────── 개요 ─────────

private enum class OverviewChart(val label: String) { Cumulative("누적 승률"), Rolling("최근 10판"), Elo("참고 지수") }

@Composable
private fun OverviewTab(
    all: OverviewStats,
    stats: OverviewStats,
    deepRatings: List<DeepRating>,
    showModeCompare: Boolean,
) {
    var chart by rememberSaveable { mutableStateOf(OverviewChart.Cumulative) }
    var eloChar by rememberSaveable { mutableStateOf("") }
    var insightsOpen by rememberSaveable { mutableStateOf(false) }
    val dayFmt = remember { SimpleDateFormat("yy.MM.dd", Locale.KOREA) }
    val characters = remember(stats.runs) {
        stats.runs.map { it.character }.filter { it.isNotBlank() }.distinct()
    }
    val normalStats = remember(all) { StatsBuilder.filterByMode(all, "normal") }
    val deepStats = remember(all) { StatsBuilder.filterByMode(all, "deep") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = LIST_PADDING) {
        item {
            NightPanel(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WinRateRing(stats.winRate, diameter = 86.dp, stroke = 7.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("전체 승률", color = NightMuted, fontSize = 12.sp)
                        Text(
                            "${stats.wins}승 ${stats.losses}패",
                            color = NightText,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            maxLines = 1,
                        )
                        Text("총 ${stats.total}원정", color = NightMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("현재 ${stats.currentStreak}연승", NightGoldSoft)
                            Pill("최고 ${stats.bestStreak}연승", NightMuted)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    "최근 10판",
                    "${stats.recent10Wins}승 ${stats.recent10Total - stats.recent10Wins}패",
                    pct(if (stats.recent10Total == 0) 0.0 else stats.recent10Wins.toDouble() / stats.recent10Total),
                    Modifier.weight(1f),
                )
                StatTile("최종일 도달", pct(stats.finalRate), "${stats.finalReached} / ${stats.finalRuns}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("평균 클리어", fmtDuration(stats.winMs), "승리한 원정 기준", Modifier.weight(1f))
                StatTile("플레이타임", "${fmtHours(stats.totalMs)}시간", "직업 ${stats.jobs.size}종", Modifier.weight(1f))
            }
        }
        if (showModeCompare && !normalStats.empty && !deepStats.empty) {
            item {
                NightPanel {
                    SectionHeader("일반 vs 심도")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCompareCell("일반", normalStats, Modifier.weight(1f))
                        ModeCompareCell("심도", deepStats, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            val stages = remember(stats.runs) {
                val counts = mutableMapOf(2 to 0, 3 to 0, 6 to 0)
                stats.runs.forEach { run -> run.stage?.let { s -> if (s in counts) counts[s] = counts.getValue(s) + 1 } }
                counts
            }
            NightPanel {
                SectionHeader("최근 폼", "최근 10판 · 왼쪽이 오래된 판")
                Spacer(Modifier.height(10.dp))
                FormDots(stats.runs.takeLast(10).map { it.win })
                Spacer(Modifier.height(16.dp))
                Text("도달 단계", color = NightMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                StackedBar(
                    listOf(
                        BarSegment("1일차 종료", stages.getValue(2), NightLoss.copy(alpha = 0.8f)),
                        BarSegment("2일차 종료", stages.getValue(3), NightBlue),
                        BarSegment("최종일", stages.getValue(6), NightGold),
                    ),
                )
            }
        }
        item {
            val eloRuns = remember(stats.runs, eloChar) {
                StatsBuilder.filterByCharacter(stats.runs, eloChar.ifBlank { null })
            }
            val series = when (chart) {
                OverviewChart.Cumulative -> stats.runs.mapNotNull { it.cumulativeWinRate?.toFloat() }
                OverviewChart.Rolling -> stats.runs.mapNotNull { it.rollingWinRate?.toFloat() }
                OverviewChart.Elo -> eloRuns.mapNotNull { it.elo?.toFloat() }
            }
            val sourceRuns = if (chart == OverviewChart.Elo) eloRuns else stats.runs
            NightPanel {
                SectionHeader("추이")
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = OverviewChart.entries.map { it to it.label },
                    selected = chart,
                    onSelect = { chart = it },
                )
                if (chart == OverviewChart.Elo && characters.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChoiceChip("전체 캐릭터", eloChar.isBlank()) { eloChar = "" }
                        characters.forEach { name -> ChoiceChip(name, eloChar == name) { eloChar = name } }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val latest = series.lastOrNull()
                Text(
                    when {
                        latest == null -> "기록 없음"
                        chart == OverviewChart.Elo -> "현재 ${latest.toInt()}"
                        else -> "현재 ${"%.1f".format(latest)}%"
                    },
                    color = NightText,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                LineChart(
                    values = series,
                    reference = if (chart == OverviewChart.Elo) 1000f else 50f,
                    formatY = { v -> if (chart == OverviewChart.Elo) v.toInt().toString() else "${v.toInt()}%" },
                    startLabel = sourceRuns.firstOrNull()?.let { dayFmt.format(Date(it.timestamp)) },
                    endLabel = sourceRuns.lastOrNull()?.let { dayFmt.format(Date(it.timestamp)) },
                    bounds = if (chart == OverviewChart.Elo) null else 0f..100f,
                )
                if (chart == OverviewChart.Elo) {
                    Spacer(Modifier.height(8.dp))
                    Text("캐릭터별 Elo 방식 비공식 지수 · 시작 1000 · K=24", color = NightMuted, fontSize = 11.sp)
                }
            }
        }
        if (stats.insights.isNotEmpty()) {
            item {
                val visible = if (insightsOpen) stats.insights else stats.insights.take(3)
                NightPanel(modifier = Modifier.animateContentSize()) {
                    SectionHeader("인사이트")
                    Spacer(Modifier.height(6.dp))
                    visible.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("◈", color = NightGold, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(line, color = NightText, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                    if (stats.insights.size > 3) {
                        Spacer(Modifier.height(6.dp))
                        ShowMoreButton(insightsOpen, stats.insights.size - 3) { insightsOpen = !insightsOpen }
                    }
                }
            }
        }
        item {
            NightPanel {
                SectionHeader("심도 레이팅")
                Spacer(Modifier.height(4.dp))
                if (deepRatings.isEmpty()) {
                    Text("기록 없음", color = NightMuted, fontSize = 13.sp)
                } else {
                    deepRatings.take(8).forEachIndexed { index, rating ->
                        if (index > 0) RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                rating.name.ifBlank { "슬롯 ${rating.slot}" },
                                color = NightText,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Pill("심도 ${rating.depth}", NightBlue)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${rating.rating}",
                                color = NightGold,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCompareCell(label: String, stats: OverviewStats, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NightPanel)
            .border(1.dp, NightLine, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WinRateRing(stats.winRate, diameter = 46.dp, stroke = 4.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = NightGold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("${stats.total}전", color = NightText, fontSize = 13.sp)
            Text("${stats.wins}승 ${stats.losses}패", color = NightMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

// ───────── 직업 ─────────

@Composable
private fun JobsTab(stats: OverviewStats) {
    var sortByRate by rememberSaveable { mutableStateOf(false) }
    val jobs = remember(stats.jobs, sortByRate) {
        if (sortByRate) {
            stats.jobs.sortedWith(compareByDescending<JobStat> { it.winRate }.thenByDescending { it.runs })
        } else {
            stats.jobs
        }
    }
    val most = stats.jobs.firstOrNull()
    val best = stats.jobs.filter { it.runs >= 5 }.maxWithOrNull(compareBy<JobStat> { it.winRate }.thenBy { it.runs })

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = LIST_PADDING) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (most != null) {
                    HighlightCard("가장 많이 플레이", most.jobId, most.name, "${most.runs}전", Modifier.weight(1f))
                }
                if (best != null) {
                    HighlightCard("최고 승률 (5판+)", best.jobId, best.name, pct(best.winRate), Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            SegmentedControl(
                options = listOf(false to "판수 순", true to "승률 순"),
                selected = sortByRate,
                onSelect = { sortByRate = it },
            )
        }
        items(jobs, key = { it.jobId }) { job ->
            val share = if (stats.total == 0) 0.0 else job.runs.toDouble() / stats.total
            NightPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JobIcon(job.jobId, diameter = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            job.name,
                            color = NightText,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${job.runs}전 ${job.wins}승 ${job.losses}패", color = NightMuted, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("사용 ${pctShort(share)}", color = NightMuted, fontSize = 11.sp, modifier = Modifier.width(62.dp))
                            ThinBar(share.toFloat(), NightGold.copy(alpha = 0.7f), Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    WinRateRing(job.winRate, diameter = 54.dp)
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(label: String, jobId: Int, name: String, value: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightPanel)
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JobIcon(jobId, diameter = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = NightMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(name, color = NightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = NightGold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

// ───────── 보스 ─────────

private data class BossGroup(val combined: BossStat, val variants: List<BossStat>)

private fun mergeJobs(lists: List<List<JobStat>>): List<JobStat> =
    lists.flatten()
        .groupBy { it.jobId }
        .map { (jobId, rows) ->
            val runs = rows.sumOf { it.runs }
            val wins = rows.sumOf { it.wins }
            JobStat(jobId, rows.first().name, rows.first().nameEn, runs, wins, runs - wins, if (runs == 0) 0.0 else wins.toDouble() / runs)
        }
        .sortedByDescending { it.runs }

private fun mergeComps(lists: List<List<CompositionStat>>): List<CompositionStat> =
    lists.flatten()
        .groupBy { it.key }
        .map { (key, rows) ->
            val runs = rows.sumOf { it.runs }
            val wins = rows.sumOf { it.wins }
            CompositionStat(key, rows.first().label, runs, wins, runs - wins, if (runs == 0) 0.0 else wins.toDouble() / runs)
        }
        .sortedByDescending { it.runs }

private fun groupBosses(bosses: List<BossStat>): List<BossGroup> =
    bosses.groupBy { it.bossId }.map { (_, variants) ->
        if (variants.size == 1) {
            BossGroup(variants.first(), variants)
        } else {
            val runs = variants.sumOf { it.runs }
            val wins = variants.sumOf { it.wins }
            val first = variants.first()
            BossGroup(
                combined = first.copy(
                    key = "boss:${first.bossId}",
                    variant = "all",
                    variantLabel = "기본 + 영원한 밤",
                    runs = runs,
                    wins = wins,
                    losses = runs - wins,
                    winRate = if (runs == 0) 0.0 else wins.toDouble() / runs,
                    ownJobs = mergeJobs(variants.map { it.ownJobs }),
                    compositions = mergeComps(variants.map { it.compositions }),
                ),
                variants = variants,
            )
        }
    }

@Composable
private fun BossesTab(stats: OverviewStats) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(stats.bosses, filter) {
        when (filter) {
            "normal" -> stats.bosses.filter { it.variant == "normal" }.map { BossGroup(it, listOf(it)) }
            "everdark" -> stats.bosses.filter { it.variant == "everdark" }.map { BossGroup(it, listOf(it)) }
            else -> groupBosses(stats.bosses)
        }
    }
    val bosses = groups.map { it.combined }
    val mostMet = bosses.maxByOrNull { it.runs }
    val hardest = bosses.filter { it.runs >= 3 }.minByOrNull { it.winRate }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = LIST_PADDING) {
        item {
            SegmentedControl(
                options = listOf("all" to "전체", "normal" to "기본", "everdark" to "영원한 밤"),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        if (bosses.isEmpty()) {
            item { EmptyState("이 조건의 보스 기록이 없습니다.") }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (mostMet != null) {
                        BossHighlight("가장 많이 만남", mostMet.name, "${mostMet.runs}회", Modifier.weight(1f))
                    }
                    if (hardest != null) {
                        BossHighlight("가장 어려움 (3판+)", hardest.name, pct(hardest.winRate), Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text("보스를 누르면 직업별·조합별 상세가 펼쳐집니다.", color = NightMuted, fontSize = 12.sp)
            }
            items(groups, key = { it.combined.key }) { group ->
                val key = group.combined.key
                BossCard(group, expanded = expandedKey == key) {
                    expandedKey = if (expandedKey == key) null else key
                }
            }
        }
    }
}

@Composable
private fun BossHighlight(label: String, name: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightPanel)
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(label, color = NightMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(name, color = NightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        Text(value, color = NightGold, fontSize = 13.sp)
    }
}

@Composable
private fun BossCard(group: BossGroup, expanded: Boolean, onToggle: () -> Unit) {
    val boss = group.combined
    ExpandablePanel(
        expanded = expanded,
        onToggle = onToggle,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    boss.name,
                    color = NightText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("${boss.runs}전 ${boss.wins}승 ${boss.losses}패", color = NightMuted, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.variants.forEach { v ->
                        val label = if (v.variant == "everdark") "영원한 밤" else v.variantLabel
                        Pill(
                            if (group.variants.size > 1) "$label ${pctShort(v.winRate)}" else label,
                            if (v.variant == "everdark") NightBlue else NightMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            WinRateRing(boss.winRate, diameter = 50.dp)
        },
    ) {
        RowDivider()
        Spacer(Modifier.height(8.dp))
        if (group.variants.size > 1) {
            Text("변형별", color = NightGold, fontSize = 12.sp)
            group.variants.forEach { v ->
                CompactRateRow(
                    title = if (v.variant == "everdark") "영원한 밤의 왕" else v.variantLabel,
                    sub = "${v.runs}전",
                    wins = v.wins,
                    losses = v.losses,
                    rate = v.winRate,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("내 직업별", color = NightGold, fontSize = 12.sp)
        boss.ownJobs.take(6).forEach { job ->
            CompactRateRow(
                title = job.name,
                sub = "${job.runs}전",
                wins = job.wins,
                losses = job.losses,
                rate = job.winRate,
                leading = { JobIcon(job.jobId, diameter = 30.dp) },
            )
        }
        if (boss.compositions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("파티 조합", color = NightGold, fontSize = 12.sp)
            boss.compositions.take(4).forEach { comp -> CompositionRow(comp) }
        }
    }
}

// ───────── 매칭 ─────────

private const val LIST_PREVIEW = 8

@Composable
private fun MatchingTab(stats: OverviewStats) {
    var section by rememberSaveable { mutableStateOf("mates") }
    var showAllMates by rememberSaveable { mutableStateOf(false) }
    var showAllComps by rememberSaveable { mutableStateOf(false) }
    val topMates = stats.teammates.take(3)
    val restMates = stats.teammates.drop(3).filter { it.runs >= 2 }
    val visibleMates = if (showAllMates) restMates else restMates.take(LIST_PREVIEW)
    val visibleComps = if (showAllComps) stats.compositions else stats.compositions.take(LIST_PREVIEW)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = LIST_PADDING) {
        item {
            NightPanel {
                SectionHeader("파티 규모")
                Spacer(Modifier.height(10.dp))
                StackedBar(
                    listOf(
                        BarSegment("솔로", stats.partySizes.solo, NightMuted),
                        BarSegment("듀오", stats.partySizes.duo, NightBlue),
                        BarSegment("트리오", stats.partySizes.trio, NightGold),
                    ),
                )
            }
        }
        item {
            SegmentedControl(
                options = listOf("mates" to "함께한 동료", "comps" to "파티 조합"),
                selected = section,
                onSelect = { section = it },
            )
        }
        if (section == "mates") {
            if (topMates.isEmpty()) {
                item { EmptyState("함께한 플레이어 기록이 없습니다.") }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        topMates.forEachIndexed { index, mate -> TopMateCard(index + 1, mate, Modifier.weight(1f)) }
                        repeat(3 - topMates.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item {
                    SectionHeader("자주 만난 동료", "2회 이상 함께한 플레이어 · ${restMates.size}명")
                }
                if (restMates.isEmpty()) {
                    item { Text("상위 3명 외에 2회 이상 만난 동료가 없습니다.", color = NightMuted, fontSize = 13.sp) }
                } else {
                    item {
                        NightPanel(padding = 12.dp) {
                            visibleMates.forEachIndexed { index, mate ->
                                if (index > 0) RowDivider()
                                CompactRateRow(
                                    title = mate.name,
                                    sub = "${mate.runs}회 동행",
                                    wins = mate.wins,
                                    losses = mate.losses,
                                    rate = mate.winRate,
                                    leading = { RankBadge(index + 4) },
                                )
                            }
                        }
                    }
                    item { ShowMoreButton(showAllMates, restMates.size - visibleMates.size) { showAllMates = !showAllMates } }
                }
            }
        } else {
            if (stats.compositions.isEmpty()) {
                item { EmptyState("조합 기록이 없습니다.") }
            } else {
                item { SectionHeader("파티 조합", "직업 구성별 승률 · ${stats.compositions.size}종") }
                item {
                    NightPanel(padding = 12.dp) {
                        visibleComps.forEachIndexed { index, comp ->
                            if (index > 0) RowDivider()
                            CompositionRow(comp)
                        }
                    }
                }
                item {
                    ShowMoreButton(showAllComps, stats.compositions.size - visibleComps.size) { showAllComps = !showAllComps }
                }
            }
        }
    }
}

@Composable
private fun TopMateCard(rank: Int, mate: MateStat, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightPanel)
            .border(1.dp, if (rank == 1) NightGold.copy(alpha = 0.5f) else NightLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("#$rank", color = NightGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        WinRateRing(mate.winRate, diameter = 54.dp)
        Spacer(Modifier.height(8.dp))
        Text(mate.name, color = NightText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${mate.runs}회 동행", color = NightMuted, fontSize = 11.sp, maxLines = 1)
        Text("${mate.wins}승 ${mate.losses}패", color = NightMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NightPanel)
            .border(1.dp, NightLine, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$rank", color = NightMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CompositionRow(comp: CompositionStat) {
    val jobIds = remember(comp.key) { comp.key.split("-").mapNotNull { it.toIntOrNull() } }
    val head = comp.label.substringBefore(" · ")
    val jobs = comp.label.substringAfter(" · ", comp.label)
    CompactRateRow(
        title = "$head · ${comp.runs}전",
        sub = jobs,
        wins = comp.wins,
        losses = comp.losses,
        rate = comp.winRate,
        leading = { JobIconStack(jobIds, diameter = 26.dp) },
    )
}

// ───────── 기록 ─────────

@Composable
private fun RecordsTab(runs: List<ExpeditionRun>) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val dateFmt = remember { SimpleDateFormat("MM.dd HH:mm", Locale.KOREA) }
    val visible = remember(runs, showAll) { if (showAll) runs.asReversed() else runs.takeLast(20).asReversed() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = LIST_PADDING) {
        item {
            SegmentedControl(
                options = listOf(false to "최근 20판", true to "전체 ${runs.size}판"),
                selected = showAll,
                onSelect = { showAll = it },
            )
        }
        item { Text("기록을 누르면 동료·시간·참고 지수가 보입니다.", color = NightMuted, fontSize = 12.sp) }
        items(visible) { run ->
            RunCard(run, dateFmt, expanded = expandedId == run.id) {
                expandedId = if (expandedId == run.id) null else run.id
            }
        }
    }
}

@Composable
private fun RunCard(run: ExpeditionRun, dateFmt: SimpleDateFormat, expanded: Boolean, onToggle: () -> Unit) {
    val accent = if (run.win) NightWin else NightLoss
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanel)
            .border(1.dp, NightLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .animateContentSize(),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JobIcon(run.jobId, diameter = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.character,
                        color = NightText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${run.jobName} · ${run.modeLabel}", color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (run.win) "승리" else "패배",
                        color = accent,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Text(dateFmt.format(Date(run.timestamp)), color = NightMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${run.bossName} · ${run.stageLabel}",
                color = NightText,
                fontSize = 12.sp,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                RowDivider()
                Spacer(Modifier.height(8.dp))
                DetailLine("소요 시간", fmtDuration(run.durationMs))
                DetailLine("참고 지수", run.elo?.toString() ?: "—")
                DetailLine("파티", "${run.partySize}인")
                if (run.teammates.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("동료", color = NightMuted, fontSize = 12.sp)
                    Text(run.teammates.joinToString(", "), color = NightText, fontSize = 13.sp, lineHeight = 18.sp)
                }
            } else if (run.teammates.isNotEmpty()) {
                Text(
                    "동료 ${run.teammates.joinToString(", ")}",
                    color = NightMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = NightMuted, fontSize = 12.sp)
        Text(value, color = NightText, fontSize = 12.sp)
    }
}
