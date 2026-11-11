package app.nightrecord.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightBgSoft
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightLineSoft
import app.nightrecord.ui.theme.NightLoss
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText
import app.nightrecord.ui.theme.NightWin
import kotlin.math.roundToInt

// ───────── 이미지 ─────────

object AssetImages {
    private val cache = LruCache<String, ImageBitmap>(40)

    fun load(context: Context, path: String, maxPx: Int): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()?.asImageBitmap() ?: return null
        cache.put(path, bitmap)
        return bitmap
    }
}

fun jobIconAssetPath(jobId: Int): String? = JOB_ICON_FILES[jobId]?.let { "jobicons/$it.webp" }

private val JOB_ICON_FILES = mapOf(
    1 to "wylder",
    2 to "guardian",
    3 to "ironeye",
    4 to "dutchess",
    5 to "raider",
    6 to "revenant",
    7 to "recluse",
    8 to "executor",
    9 to "scholar",
    10 to "undertaker",
)

@Composable
fun JobIcon(jobId: Int, diameter: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(jobId) {
        JOB_ICON_FILES[jobId]?.let { AssetImages.load(context, "jobicons/$it.webp", 96) }
    }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(NightBgSoft)
            .border(1.dp, NightLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(diameter * 0.14f),
            )
        }
    }
}

@Composable
fun JobIconStack(jobIds: List<Int>, diameter: Dp = 26.dp) {
    val overlap = diameter * 0.32f
    Box(modifier = Modifier.width(diameter + (diameter - overlap) * (jobIds.size - 1).coerceAtLeast(0))) {
        jobIds.forEachIndexed { index, jobId ->
            JobIcon(
                jobId = jobId,
                diameter = diameter,
                modifier = Modifier.offset(x = (diameter - overlap) * index),
            )
        }
    }
}

// ───────── 패널 · 헤더 ─────────

@Composable
fun NightPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(16.dp))
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = NightGold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = NightMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Text(
        message,
        color = NightMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(16.dp))
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(16.dp),
    )
}

@Composable
fun StatTile(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(NightPanel, RoundedCornerShape(16.dp))
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = NightText,
            fontSize = 21.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(sub, color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun ExpandToggle(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "접기" else "펼치기",
        tint = NightMuted,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
fun ShowMoreButton(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    if (!expanded && hiddenCount <= 0) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, NightLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (expanded) "접기" else "더 보기 ($hiddenCount)",
            color = NightGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Pill(label: String, color: Color = NightMuted, modifier: Modifier = Modifier) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ───────── 선택 컨트롤 ─────────

@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(12.dp))
            .border(1.dp, NightLine, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) NightGold else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) NightBg else NightText,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ModeSegment(
    selected: String,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
) {
    val total = (counts["normal"] ?: 0) + (counts["deep"] ?: 0) + (counts["unknown"] ?: 0)
    SegmentedControl(
        options = listOf(
            "all" to "전체 $total",
            "deep" to "심도 ${counts["deep"] ?: 0}",
            "normal" to "일반 ${counts["normal"] ?: 0}",
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
fun ChoiceChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) NightBg else NightText,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) NightGold else NightPanel)
            .border(1.dp, if (active) NightGold else NightLine, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ───────── 수치 시각화 ─────────

fun rateColor(rate: Double): Color = when {
    rate >= 0.55 -> NightWin
    rate < 0.45 -> NightLoss
    else -> NightGold
}

@Composable
fun WinRateRing(rate: Double, diameter: Dp = 52.dp, stroke: Dp = 5.dp) {
    val color = rateColor(rate)
    val sweep = 360f * rate.toFloat().coerceIn(0f, 1f)
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = NightLineSoft,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
        Text(
            pctShort(rate),
            color = NightText,
            fontSize = (diameter.value * 0.23f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun ThinBar(fraction: Float, color: Color = NightGold, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(NightBg.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

data class BarSegment(val label: String, val value: Int, val color: Color)

@Composable
fun StackedBar(segments: List<BarSegment>) {
    val total = segments.sumOf { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NightBg.copy(alpha = 0.55f)),
        ) {
            segments.filter { it.value > 0 }.forEach { seg ->
                Box(
                    modifier = Modifier
                        .weight(seg.value.toFloat())
                        .fillMaxHeight()
                        .background(seg.color),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEach { seg ->
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(seg.color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(seg.label, color = NightMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (total == 0) "0" else "${seg.value} · ${pctShort(seg.value.toDouble() / total)}",
                            color = NightText,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 이름 · 보조 설명 · 승률 숫자만 담은 한 줄 행. 그래프 반복 없이 리스트용. */
@Composable
fun CompactRateRow(
    title: String,
    sub: String,
    wins: Int,
    losses: Int,
    rate: Double,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NightText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) {
                Text(sub, color = NightMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(pctShort(rate), color = rateColor(rate), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${wins}승 ${losses}패", color = NightMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NightLineSoft),
    )
}

@Composable
fun FormDots(wins: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        wins.forEach { win ->
            val color = if (win) NightWin else NightLoss
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.18f))
                    .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (win) "W" else "L", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    strokeColor: Color = NightGold,
    reference: Float? = null,
    formatY: (Float) -> String = { it.roundToInt().toString() },
    startLabel: String? = null,
    endLabel: String? = null,
    bounds: ClosedFloatingPointRange<Float>? = null,
) {
    if (values.size < 2) {
        Text("그래프를 그리기엔 기록이 부족합니다.", color = NightMuted, fontSize = 12.sp)
        return
    }
    var min = values.minOrNull() ?: 0f
    var max = values.maxOrNull() ?: 1f
    if (reference != null) {
        min = minOf(min, reference)
        max = maxOf(max, reference)
    }
    val pad = ((max - min) * 0.08f).coerceAtLeast(1f)
    min -= pad
    max += pad
    if (bounds != null) {
        min = min.coerceAtLeast(bounds.start)
        max = max.coerceAtMost(bounds.endInclusive)
    }
    val span = (max - min).takeIf { it > 0.001f } ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatY(max), color = NightMuted, fontSize = 10.sp, maxLines = 1)
                Text(formatY(min), color = NightMuted, fontSize = 10.sp, maxLines = 1)
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NightBg.copy(alpha = 0.35f)),
            ) {
                fun yOf(v: Float) = size.height - ((v - min) / span) * size.height
                fun xOf(i: Int) = size.width * i / (values.size - 1).coerceAtLeast(1)

                if (reference != null) {
                    val ry = yOf(reference)
                    drawLine(
                        color = NightMuted.copy(alpha = 0.45f),
                        start = Offset(0f, ry),
                        end = Offset(size.width, ry),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                    )
                }
                val line = Path()
                val area = Path()
                values.forEachIndexed { i, v ->
                    val x = xOf(i)
                    val y = yOf(v)
                    if (i == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, size.height)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(size.width, size.height)
                area.close()
                drawPath(
                    area,
                    brush = Brush.verticalGradient(listOf(strokeColor.copy(alpha = 0.28f), Color.Transparent)),
                )
                drawPath(line, color = strokeColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawCircle(color = strokeColor, radius = 7f, center = Offset(xOf(values.lastIndex), yOf(values.last())))
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 38.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(startLabel.orEmpty(), color = NightMuted, fontSize = 10.sp)
                Text(endLabel.orEmpty(), color = NightMuted, fontSize = 10.sp)
            }
        }
    }
}

/** 클릭으로 펼치는 패널. 헤더는 항상 보이고 상세는 펼쳤을 때만. */
@Composable
fun ExpandablePanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightPanel)
            .border(1.dp, if (expanded) NightGold.copy(alpha = 0.45f) else NightLine, RoundedCornerShape(16.dp))
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            header()
            Spacer(Modifier.width(6.dp))
            ExpandToggle(expanded)
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                content = content,
            )
        }
    }
}

fun pct(rate: Double): String = "${(rate * 1000).roundToInt() / 10.0}%"

fun pctShort(rate: Double): String = "${(rate * 100).roundToInt()}%"

fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

fun fmtHours(ms: Long): String = String.format("%.1f", ms / 3600000.0)
