package app.nightrecord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.relics.RelicColor
import app.nightrecord.relics.RelicEngine
import app.nightrecord.relics.RelicItem
import app.nightrecord.relics.RelicLoadout
import app.nightrecord.relics.RelicRecommendResult
import app.nightrecord.ui.components.ChoiceChip
import app.nightrecord.ui.components.EmptyState
import app.nightrecord.ui.components.ExpandablePanel
import app.nightrecord.ui.components.JobIcon
import app.nightrecord.ui.components.Pill
import app.nightrecord.ui.components.RowDivider
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightBlue
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightLoss
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText
import kotlin.math.roundToInt

private val RelicRed = Color(0xFFD0645A)
private val RelicBlue = Color(0xFF6E9BE0)
private val RelicYellow = Color(0xFFE0C35A)
private val RelicGreen = Color(0xFF79B97A)

private fun relicColor(color: Int): Color = when (color) {
    RelicColor.RED -> RelicRed
    RelicColor.BLUE -> RelicBlue
    RelicColor.YELLOW -> RelicYellow
    RelicColor.GREEN -> RelicGreen
    else -> NightMuted
}

@Composable
fun RelicsScreen(
    engine: RelicEngine?,
    result: RelicRecommendResult?,
    busy: Boolean,
    error: String?,
    jobId: Int,
    buildId: String?,
    vesselName: String?,
    includeDeep: Boolean,
    includeShop: Boolean,
    onJob: (Int) -> Unit,
    onBuild: (String) -> Unit,
    onVessel: (String?) -> Unit,
    onIncludeDeep: (Boolean) -> Unit,
    onIncludeShop: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val jobs = engine?.jobs().orEmpty()
    val builds = result?.builds ?: engine?.buildsForJob(jobId).orEmpty()
    val vessels = result?.vessels ?: engine?.vesselsForJob(jobId).orEmpty()
    val jobName = jobs.firstOrNull { it.jobId == jobId }?.nameKo ?: "직업"
    val buildName = builds.firstOrNull { it.id == buildId }?.name ?: builds.firstOrNull()?.name ?: "빌드"
    val vesselLabel = vessels.firstOrNull { it.name == vesselName }?.nameKo ?: "그릇 자동"
    var conditionsOpen by rememberSaveable { mutableStateOf(true) }
    var openLoadout by rememberSaveable { mutableIntStateOf(0) }
    val loadouts = result?.loadouts.orEmpty()

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
                    "추천 유물 조합기",
                    color = NightText,
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text("웹에서 분석한 유물 인벤으로 조합합니다.", color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Sync, contentDescription = "유물 동기화", tint = NightGold)
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                ExpandablePanel(
                    expanded = conditionsOpen,
                    onToggle = { conditionsOpen = !conditionsOpen },
                    header = {
                        JobIcon(jobId, diameter = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$jobName · $buildName", color = NightText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                            Text(
                                listOf(vesselLabel, if (includeDeep) "심도 포함" else "심도 제외", if (includeShop) "상점 포함" else "상점 제외").joinToString(" · "),
                                color = NightMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                ) {
                    RowDivider()
                    Spacer(Modifier.height(10.dp))
                    ConditionLabel("직업")
                    ChipRow { jobs.forEach { job -> ChoiceChip(job.nameKo, jobId == job.jobId) { onJob(job.jobId) } } }
                    Spacer(Modifier.height(12.dp))
                    ConditionLabel("빌드")
                    ChipRow { builds.forEach { build -> ChoiceChip(build.name, buildId == build.id) { onBuild(build.id) } } }
                    Spacer(Modifier.height(12.dp))
                    ConditionLabel("그릇")
                    ChipRow {
                        ChoiceChip("자동", vesselName.isNullOrBlank()) { onVessel(null) }
                        vessels.forEach { vessel -> ChoiceChip(vessel.nameKo, vesselName == vessel.name) { onVessel(vessel.name) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("심도 유물 포함", includeDeep, onIncludeDeep)
                    ToggleRow("상점·도감 유물 포함", includeShop, onIncludeShop)
                }
            }

            if (busy) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = NightGold)
                        Spacer(Modifier.height(8.dp))
                        Text("조합을 계산하는 중…", color = NightMuted, fontSize = 13.sp)
                    }
                }
            } else {
                if (!error.isNullOrBlank()) item { EmptyState(error) }
                if (loadouts.isEmpty() && error.isNullOrBlank()) {
                    item { EmptyState("유물 인벤토리가 없거나 조합 결과가 없습니다. 웹에서 세이브를 분석한 뒤 동기화하세요.") }
                }
                if (loadouts.isNotEmpty()) {
                    item {
                        Text(
                            "추천 ${loadouts.size}개 · 점수는 이론상 최고 조합을 100으로 환산한 값",
                            color = NightMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                itemsIndexed(loadouts) { index, loadout ->
                    LoadoutCard(index + 1, loadout, expanded = openLoadout == index) {
                        openLoadout = if (openLoadout == index) -1 else index
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadoutCard(rank: Int, loadout: RelicLoadout, expanded: Boolean, onToggle: () -> Unit) {
    ExpandablePanel(
        expanded = expanded,
        onToggle = onToggle,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text("#$rank", color = NightGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    loadout.vesselKo,
                    color = NightText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    loadout.slots.forEachIndexed { index, color ->
                        val filled = loadout.relics.getOrNull(index) != null
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(if (index >= 3) RoundedCornerShape(3.dp) else CircleShape)
                                .background(if (filled) relicColor(color) else Color.Transparent)
                                .border(1.dp, relicColor(color), if (index >= 3) RoundedCornerShape(3.dp) else CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${loadout.displayScore.roundToInt()}",
                    color = NightGold,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                )
                Text("점", color = NightMuted, fontSize = 11.sp)
            }
        },
    ) {
        RowDivider()
        Spacer(Modifier.height(10.dp))
        val normal = loadout.relics.take(3)
        val deep = loadout.relics.drop(3)
        Text("일반 유물", color = NightGold, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        normal.forEachIndexed { i, relic -> RelicSlot(loadout.slots.getOrElse(i) { 0 }, relic) }
        if (deep.any { it != null }) {
            Spacer(Modifier.height(6.dp))
            Text("심도 유물", color = NightBlue, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            deep.forEachIndexed { i, relic -> RelicSlot(loadout.slots.getOrElse(i + 3) { 0 }, relic) }
        }
        if (loadout.cursePenalty > 0.0) {
            Spacer(Modifier.height(6.dp))
            Text("저주 페널티 -${loadout.cursePenalty.roundToInt()}", color = NightLoss, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RelicSlot(slotColor: Int, relic: RelicItem?) {
    val color = relicColor(relic?.color ?: slotColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightBg.copy(alpha = 0.4f))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                relic?.label ?: "비어 있음",
                color = if (relic == null) NightMuted else NightText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            )
            if (relic != null) {
                if (relic.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        relic.tags.forEach { tag -> Pill(tag.labelKo, if (tag.name == "MISSING") NightLoss else NightGold) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                relic.effects.forEach { effect ->
                    Text(
                        "· ${effect.name}",
                        color = if (effect.curse != null) NightLoss else NightMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConditionLabel(text: String) {
    Text(text, color = NightMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NightText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightBg,
                checkedTrackColor = NightGold,
                uncheckedThumbColor = NightMuted,
                uncheckedTrackColor = NightPanel,
                uncheckedBorderColor = NightLine,
            ),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}
