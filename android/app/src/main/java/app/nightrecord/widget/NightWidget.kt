package app.nightrecord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.nightrecord.MainActivity
import app.nightrecord.data.local.OfflineStore
import app.nightrecord.data.local.WidgetSummary
import app.nightrecord.ui.components.jobIconAssetPath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val WBg = Color(0xFF101014)
private val WGold = Color(0xFFC9A227)
private val WText = Color(0xFFECE7DD)
private val WMuted = Color(0xFF9B948A)
private val WWin = Color(0xFF7FBF8A)
private val WLoss = Color(0xFFC96A5F)
private val WEmpty = Color(0x22FFFFFF)

class NightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = OfflineStore(context).widgetSummary()
        val icon = summary?.topJobId?.let { loadIcon(context, it) }
        provideContent { WidgetBody(summary, icon) }
    }

    private fun loadIcon(context: Context, jobId: Int): Bitmap? {
        val path = jobIconAssetPath(jobId) ?: return null
        return runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    companion object {
        private val SMALL = DpSize(110.dp, 50.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val WIDE = DpSize(260.dp, 110.dp)

        suspend fun refresh(context: Context) {
            runCatching { NightWidget().updateAll(context) }
        }
    }
}

class NightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NightWidget()
}

@Composable
private fun WidgetBody(summary: WidgetSummary?, icon: Bitmap?) {
    val size = LocalSize.current
    val compact = size.height < 100.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WBg)
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (summary == null || summary.total == 0) {
            Text("밤의 기록", style = TextStyle(color = ColorProvider(WGold), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "앱에서 로그인하면 전적이 표시됩니다",
                style = TextStyle(color = ColorProvider(WMuted), fontSize = 11.sp),
                maxLines = 2,
            )
            return@Column
        }
        val rate = "${(summary.winRate * 100).roundToInt()}%"
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(rate, style = TextStyle(color = ColorProvider(WGold), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text("${summary.wins}승 ${summary.losses}패", style = TextStyle(color = ColorProvider(WText), fontSize = 12.sp), maxLines = 1)
                    FormDots(summary.recent, 6.dp)
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "밤의 기록",
                style = TextStyle(color = ColorProvider(WGold), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(summary.updatedAt)),
                style = TextStyle(color = ColorProvider(WMuted), fontSize = 10.sp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(rate, style = TextStyle(color = ColorProvider(WText), fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Text(
                    "${summary.wins}승 ${summary.losses}패 · ${summary.total}원정",
                    style = TextStyle(color = ColorProvider(WMuted), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            if (icon != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(provider = ImageProvider(icon), contentDescription = summary.topJobName, modifier = GlanceModifier.size(34.dp))
                    if (size.width >= 240.dp) {
                        Text(summary.topJobName.orEmpty(), style = TextStyle(color = ColorProvider(WMuted), fontSize = 10.sp), maxLines = 1)
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            FormDots(summary.recent, 8.dp)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "연승 ${summary.currentStreak} · 최고 ${summary.bestStreak}",
                style = TextStyle(color = ColorProvider(WMuted), fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FormDots(recent: String, dot: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val padded = recent.padStart(10, '-').takeLast(10)
        padded.forEachIndexed { index, c ->
            if (index > 0) Spacer(GlanceModifier.width(3.dp))
            val color = when (c) {
                'W' -> WWin
                'L' -> WLoss
                else -> WEmpty
            }
            Box(modifier = GlanceModifier.size(dot).cornerRadius(dot / 2).background(color)) {}
        }
    }
}
