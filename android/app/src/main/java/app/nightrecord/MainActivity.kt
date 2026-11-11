package app.nightrecord

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import app.nightrecord.ui.screens.LockScreen
import app.nightrecord.ui.screens.LoginScreen
import app.nightrecord.ui.screens.MeScreen
import app.nightrecord.ui.screens.RelicsScreen
import app.nightrecord.ui.screens.SearchScreen
import app.nightrecord.ui.screens.StatsScreen
import app.nightrecord.ui.theme.NightAtmosphere
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLoss
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText
import app.nightrecord.ui.theme.NightrecordTheme

class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NightrecordTheme {
                val state by viewModel.state.collectAsState()
                when (val ui = state) {
                    AppUiState.Login -> LoginScreen(onLogin = viewModel::login)
                    AppUiState.Loading -> NightAtmosphere {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NightGold)
                        }
                    }
                    is AppUiState.Locked -> LockScreen(
                        message = ui.message,
                        onUnlock = ::unlock,
                        onUsePassword = viewModel::backToLogin,
                    )
                    is AppUiState.Ready -> ReadyShell(
                        ui = ui,
                        vm = viewModel,
                        onToggleBiometric = { enable -> if (enable) enableBiometric() else viewModel.disableBiometric() },
                    )
                    is AppUiState.Error -> NightAtmosphere {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("문제가 발생했습니다", color = NightText, fontSize = 20.sp)
                                Text(ui.message, color = NightMuted, modifier = Modifier.padding(top = 8.dp))
                                TextButton(
                                    onClick = {
                                        if (ui.backToLogin) viewModel.backToLogin()
                                        else viewModel.refreshAll()
                                    },
                                ) {
                                    Text(
                                        if (ui.backToLogin) "로그인으로" else "다시 시도",
                                        color = NightGold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onBackground()
    }

    private fun unlock() {
        val cipher = viewModel.unlockCipher() ?: return
        authenticate(
            title = "밤의 기록 잠금 해제",
            subtitle = "지문 또는 얼굴로 인증하세요",
            cipher = cipher,
            onSuccess = viewModel::onUnlocked,
            onFailure = viewModel::onUnlockError,
        )
    }

    private fun enableBiometric() {
        val cipher = viewModel.prepareEnableBiometric() ?: return
        authenticate(
            title = "생체 인증 잠금 설정",
            subtitle = "앞으로 앱을 열 때 이 인증을 사용합니다",
            cipher = cipher,
            onSuccess = viewModel::confirmEnableBiometric,
            onFailure = viewModel::cancelEnableBiometric,
        )
    }

    private fun authenticate(
        title: String,
        subtitle: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked != null) onSuccess(unlocked) else onFailure("인증 결과를 확인하지 못했습니다.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFailure(if (cancelled) null else errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("취소")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

@Composable
private fun SyncBanner(ui: AppUiState.Ready) {
    if (!ui.offline && !ui.syncing) return
    Column(Modifier.fillMaxWidth()) {
        if (ui.syncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = NightGold,
                trackColor = NightBg,
            )
        }
        if (ui.offline && !ui.syncing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NightPanel)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = NightLoss, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                val time = if (ui.syncedAt > 0) {
                    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(ui.syncedAt))
                } else {
                    "알 수 없음"
                }
                Text(
                    "오프라인 · 저장된 기록 ($time 기준)",
                    color = NightMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadyShell(ui: AppUiState.Ready, vm: AppViewModel, onToggleBiometric: (Boolean) -> Unit) {
    val tabs = listOf(
        NavSpec(AppTab.Stats, "전적", Icons.Outlined.Home),
        NavSpec(AppTab.Search, "검색", Icons.Outlined.Search),
        NavSpec(AppTab.Relics, "유물", Icons.Outlined.AutoAwesome),
        NavSpec(AppTab.Me, "마이", Icons.Outlined.Person),
    )
    NightAtmosphere {
        Scaffold(
            containerColor = NightBg.copy(alpha = 0f),
            bottomBar = {
                if (ui.publicView == null) {
                    NavigationBar(containerColor = NightPanel) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = ui.tab == tab.id,
                                onClick = { vm.selectTab(tab.id) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NightGold,
                                    selectedTextColor = NightGold,
                                    unselectedIconColor = NightMuted,
                                    unselectedTextColor = NightMuted,
                                    indicatorColor = NightBg,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
            SyncBanner(ui)
            Box(Modifier.fillMaxSize()) {
                val public = ui.publicView
                if (public != null) {
                    StatsScreen(
                        title = public.title,
                        subtitle = "공개 프로필 · #${public.tag}",
                        stats = public.stats,
                        deepRatings = public.deepRatings,
                        onBack = vm::clearPublicView,
                    )
                } else {
                    when (ui.tab) {
                        AppTab.Stats -> StatsScreen(
                            title = ui.username,
                            subtitle = if (ui.profiles.isNotEmpty()) {
                                "연동 프로필 ${ui.profiles.size}개 · 서버 누적"
                            } else {
                                "서버 누적 전적"
                            },
                            stats = ui.stats,
                            deepRatings = ui.deepRatings,
                            onRefresh = vm::refreshAll,
                        )
                        AppTab.Search -> SearchScreen(
                            query = ui.searchQuery,
                            suggest = ui.searchSuggest,
                            results = ui.searchResults,
                            busy = ui.searchBusy,
                            onQuery = vm::onSearchQuery,
                            onSearch = vm::runSearch,
                            onOpen = vm::openPublicProfile,
                        )
                        AppTab.Relics -> RelicsScreen(
                            engine = vm.relicEngineOrNull(),
                            result = ui.relicResult,
                            busy = ui.relicBusy,
                            error = ui.relicError,
                            jobId = ui.relicJobId,
                            buildId = ui.relicBuildId,
                            vesselName = ui.relicVessel,
                            includeDeep = ui.relicIncludeDeep,
                            includeShop = ui.relicIncludeShop,
                            onJob = { vm.setRelicOptions(jobId = it, clearVessel = true) },
                            onBuild = { vm.setRelicOptions(buildId = it) },
                            onVessel = { vm.setRelicOptions(vessel = it, clearVessel = it == null) },
                            onIncludeDeep = { vm.setRelicOptions(includeDeep = it) },
                            onIncludeShop = { vm.setRelicOptions(includeShop = it) },
                            onRefresh = vm::refreshRelics,
                        )
                        AppTab.Me -> MeScreen(
                            username = ui.username,
                            profiles = ui.profiles,
                            busy = ui.meBusy,
                            message = ui.meMessage,
                            onOpenStats = { vm.selectTab(AppTab.Stats) },
                            onChangePassword = vm::changePassword,
                            onUpdateTag = vm::updateTag,
                            onUpdateAvatar = vm::updateAvatar,
                            onLogout = vm::logout,
                            biometricEnabled = ui.biometricEnabled,
                            biometricAvailable = ui.biometricAvailable,
                            biometricHint = ui.biometricHint,
                            onToggleBiometric = onToggleBiometric,
                        )
                    }
                }
            }
            }
        }
    }
}

private data class NavSpec(val id: AppTab, val label: String, val icon: ImageVector)
