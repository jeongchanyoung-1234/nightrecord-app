package app.nightrecord.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.ui.theme.NightAtmosphere
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightText

@Composable
fun LockScreen(
    message: String?,
    onUnlock: () -> Unit,
    onUsePassword: () -> Unit,
) {
    LaunchedEffect(Unit) { onUnlock() }
    NightAtmosphere {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "밤의 기록",
                color = NightText,
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .border(1.dp, NightLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = NightGold, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "잠금 상태입니다",
                color = NightText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message ?: "지문 또는 얼굴 인증으로 기록을 엽니다.",
                color = NightMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NightGold, contentColor = NightBg),
            ) {
                Text("생체 인증으로 열기", modifier = Modifier.padding(vertical = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUsePassword) {
                Text("비밀번호로 다시 로그인", color = NightMuted, fontSize = 13.sp)
            }
        }
    }
}
