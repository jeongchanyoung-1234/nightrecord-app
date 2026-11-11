package app.nightrecord.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NightBg = Color(0xFF0A0A0C)
val NightBgSoft = Color(0xFF101014)
val NightPanel = Color(0xDC121217)
val NightPanel2 = Color(0xB318171C)
val NightGold = Color(0xFFC9A227)
val NightGoldSoft = Color(0xFFE3C766)
val NightGoldDim = Color(0xFF8A7122)
val NightText = Color(0xFFECE7DD)
val NightMuted = Color(0xFF9B948A)
val NightWin = Color(0xFF7FBF8A)
val NightLoss = Color(0xFFC96A5F)
val NightBlue = Color(0xFF7F9FD4)
val NightLine = Color(0x2EC9A227)
val NightLineSoft = Color(0x14FFFFFF)

private val Scheme = darkColorScheme(
    primary = NightGold,
    onPrimary = NightBg,
    secondary = NightGoldSoft,
    background = NightBg,
    onBackground = NightText,
    surface = NightPanel,
    onSurface = NightText,
    onSurfaceVariant = NightMuted,
    error = NightLoss,
    outline = NightLine,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun NightrecordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun NightAtmosphere(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(NightGold.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(120f, -40f),
                    radius = 900f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(NightBlue.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(980f, 80f),
                    radius = 800f,
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.22f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.40f),
                    ),
                ),
            ),
    ) {
        content()
    }
}
