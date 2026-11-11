package app.nightrecord.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.data.MeProfile
import app.nightrecord.data.ProfileAvatars
import app.nightrecord.ui.components.AssetImages
import app.nightrecord.ui.components.EmptyState
import app.nightrecord.ui.components.NightPanel
import app.nightrecord.ui.components.SectionHeader
import app.nightrecord.ui.components.SegmentedControl
import app.nightrecord.ui.components.WinRateRing
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightBgSoft
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText

@Composable
fun MeScreen(
    username: String,
    profiles: List<MeProfile>,
    busy: Boolean,
    message: String?,
    onOpenStats: (MeProfile) -> Unit,
    onChangePassword: (current: String, next: String) -> Unit,
    onUpdateTag: (profileId: String, tag: String) -> Unit,
    onUpdateAvatar: (profileId: String, avatar: String) -> Unit,
    onLogout: () -> Unit,
    biometricEnabled: Boolean = false,
    biometricAvailable: Boolean = false,
    biometricHint: String? = null,
    onToggleBiometric: (Boolean) -> Unit = {},
    initialTab: String = "records",
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
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
                Text("마이페이지", color = NightText, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(username, color = NightMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "로그아웃", tint = NightMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("records" to "내 기록", "settings" to "설정"),
            selected = tab,
            onSelect = { tab = it },
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = NightGold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NightGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            "records" -> RecordsPane(profiles, onOpenStats)
            else -> SettingsPane(
                profiles = profiles,
                busy = busy,
                onChangePassword = onChangePassword,
                onUpdateTag = onUpdateTag,
                onUpdateAvatar = onUpdateAvatar,
                biometricEnabled = biometricEnabled,
                biometricAvailable = biometricAvailable,
                biometricHint = biometricHint,
                onToggleBiometric = onToggleBiometric,
            )
        }
    }
}

@Composable
private fun AvatarImage(avatarId: String?, diameter: Dp) {
    val context = LocalContext.current
    val avatar = ProfileAvatars.byId(avatarId)
    val bitmap = remember(avatar?.asset) { avatar?.let { AssetImages.load(context, it.asset, 200) } }
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(NightBgSoft)
            .border(1.dp, NightLine, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = avatar?.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("?", color = NightMuted)
        }
    }
}

@Composable
private fun RecordsPane(profiles: List<MeProfile>, onOpenStats: (MeProfile) -> Unit) {
    if (profiles.isEmpty()) {
        EmptyState("연동된 프로필이 없습니다. 웹에서 세이브를 분석하면 여기에 나타납니다.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        items(profiles) { profile ->
            val rate = if (profile.matches == 0) 0.0 else profile.wins.toDouble() / profile.matches
            NightPanel(modifier = Modifier.clickable { onOpenStats(profile) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(profile.avatar, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile.displayTag.ifBlank { profile.tag }.ifBlank { profile.saveId },
                            color = NightText,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            profile.names.take(3).joinToString(" · ").ifBlank { "세이브 ${profile.saveId}" },
                            color = NightMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${profile.matches}전 ${profile.wins}승", color = NightMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    WinRateRing(rate, diameter = 50.dp)
                }
            }
        }
    }
}

@Composable
private fun SettingsPane(
    profiles: List<MeProfile>,
    busy: Boolean,
    onChangePassword: (String, String) -> Unit,
    onUpdateTag: (String, String) -> Unit,
    onUpdateAvatar: (String, String) -> Unit,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    biometricHint: String?,
    onToggleBiometric: (Boolean) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf(profiles.firstOrNull()?.id) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    var tagDraft by remember(selected?.id, selected?.displayTag) { mutableStateOf(selected?.displayTag.orEmpty()) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NightGold,
        unfocusedBorderColor = NightLine,
        focusedTextColor = NightText,
        unfocusedTextColor = NightText,
        cursorColor = NightGold,
        focusedContainerColor = NightPanel,
        unfocusedContainerColor = NightPanel,
        focusedLabelColor = NightGold,
        unfocusedLabelColor = NightMuted,
    )
    val buttonColors = ButtonDefaults.buttonColors(containerColor = NightGold, contentColor = NightBg)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            NightPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader(
                            "생체 인증 잠금",
                            if (biometricEnabled) "앱을 열 때 지문·얼굴 인증이 필요합니다" else "켜면 앱을 열 때 지문·얼굴 인증을 요구합니다",
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        enabled = !busy && (biometricEnabled || biometricAvailable),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NightBg,
                            checkedTrackColor = NightGold,
                            uncheckedThumbColor = NightMuted,
                            uncheckedTrackColor = NightBgSoft,
                            uncheckedBorderColor = NightLine,
                        ),
                    )
                }
                val note = when {
                    !biometricEnabled && !biometricAvailable -> biometricHint
                    biometricEnabled -> "잠금 중에는 로그인 정보가 기기 보안 영역의 키로 암호화되며, 홈 위젯 자동 갱신은 앱을 열 때만 이뤄집니다."
                    else -> null
                }
                if (note != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, color = NightMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        if (selected != null) {
            if (profiles.size > 1) {
                item {
                    SegmentedControl(
                        options = profiles.map { it.id to it.displayTag.ifBlank { it.saveId } },
                        selected = selected.id,
                        onSelect = { selectedId = it },
                    )
                }
            }
            item {
                NightPanel {
                    SectionHeader("프로필 이미지", "검색 결과와 공개 전적에 보이는 이미지")
                    Spacer(Modifier.height(12.dp))
                    ProfileAvatars.ALL.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { avatar ->
                                val active = selected.avatar == avatar.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            if (active) 2.dp else 1.dp,
                                            if (active) NightGold else NightLine,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable(enabled = !busy) { onUpdateAvatar(selected.id, avatar.id) }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                        AvatarFill(avatar.id)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        avatar.name,
                                        color = if (active) NightGold else NightMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item {
                NightPanel {
                    SectionHeader("검색 태그", "세이브 식별자 ${selected.saveId}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        label = { Text("표시 태그 (2~16자)") },
                        singleLine = true,
                        colors = colors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onUpdateTag(selected.id, tagDraft.trim()) },
                        enabled = !busy && selected.id.isNotBlank(),
                        colors = buttonColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("태그 저장")
                    }
                }
            }
        }
        item {
            NightPanel {
                SectionHeader("비밀번호 변경")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("현재 비밀번호") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = colors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("새 비밀번호 (8자 이상)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = colors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("새 비밀번호 확인") },
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != next,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = colors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onChangePassword(current, next)
                        current = ""
                        next = ""
                        confirm = ""
                    },
                    enabled = !busy && current.isNotEmpty() && next.length >= 8 && next == confirm,
                    colors = buttonColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("비밀번호 저장")
                }
            }
        }
    }
}

@Composable
private fun AvatarFill(avatarId: String) {
    val context = LocalContext.current
    val avatar = ProfileAvatars.byId(avatarId)
    val bitmap = remember(avatar?.asset) { avatar?.let { AssetImages.load(context, it.asset, 200) } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(NightBgSoft),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = avatar?.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
