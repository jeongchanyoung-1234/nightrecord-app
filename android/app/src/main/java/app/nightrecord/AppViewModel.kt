package app.nightrecord

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nightrecord.data.ApiClient
import app.nightrecord.data.ApiException
import app.nightrecord.data.BiometricVault
import app.nightrecord.data.HomeData
import app.nightrecord.data.HomeSync
import app.nightrecord.data.MeProfile
import app.nightrecord.data.ProfileMapper
import app.nightrecord.data.RunMapper
import app.nightrecord.data.SearchHit
import app.nightrecord.data.SessionStore
import app.nightrecord.data.SyncWorker
import app.nightrecord.data.local.OfflineStore
import app.nightrecord.parser.DeepRating
import app.nightrecord.parser.OverviewStats
import app.nightrecord.parser.StatsBuilder
import app.nightrecord.widget.NightWidget
import app.nightrecord.relics.RelicEngine
import app.nightrecord.relics.RelicOptions
import app.nightrecord.relics.RelicRecommendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.crypto.Cipher

enum class AppTab { Stats, Search, Relics, Me }

sealed interface AppUiState {
    data object Login : AppUiState
    data object Loading : AppUiState
    data class Locked(val message: String? = null) : AppUiState
    data class Ready(
        val username: String,
        val stats: OverviewStats,
        val deepRatings: List<DeepRating>,
        val profiles: List<MeProfile>,
        val tab: AppTab = AppTab.Stats,
        val publicView: PublicProfileView? = null,
        val searchQuery: String = "",
        val searchSuggest: List<SearchHit> = emptyList(),
        val searchResults: List<SearchHit> = emptyList(),
        val searchBusy: Boolean = false,
        val relicsJson: String = "[]",
        val relicResult: RelicRecommendResult? = null,
        val relicBusy: Boolean = false,
        val relicError: String? = null,
        val relicJobId: Int = 1,
        val relicBuildId: String? = null,
        val relicVessel: String? = null,
        val relicIncludeDeep: Boolean = true,
        val relicIncludeShop: Boolean = true,
        val meMessage: String? = null,
        val meBusy: Boolean = false,
        val offline: Boolean = false,
        val syncing: Boolean = false,
        val syncedAt: Long = 0L,
        val biometricEnabled: Boolean = false,
        val biometricAvailable: Boolean = false,
        val biometricHint: String? = null,
    ) : AppUiState

    data class Error(val message: String, val backToLogin: Boolean = false) : AppUiState
}

data class PublicProfileView(
    val tag: String,
    val title: String,
    val stats: OverviewStats,
    val deepRatings: List<DeepRating>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    private val api = ApiClient(session)
    private val offline = OfflineStore(app)
    private var relicEngine: RelicEngine? = null
    private var suggestJob: Job? = null
    private var parkedReady: AppUiState.Ready? = null
    private var backgroundAt = 0L

    private val _state = MutableStateFlow(
        when {
            session.hasSession() && session.biometricEnabled -> AppUiState.Locked()
            session.hasSession() -> AppUiState.Loading
            else -> AppUiState.Login
        },
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        if (_state.value == AppUiState.Loading) openSession()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RelicEngine.load(getApplication()) }
                .onSuccess { relicEngine = it }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = AppUiState.Loading
            try {
                withContext(Dispatchers.IO) {
                    session.clear()
                    api.login(username.trim(), password)
                }
                sync()
            } catch (e: Exception) {
                _state.value = AppUiState.Error(e.message ?: "로그인에 실패했습니다.", backToLogin = true)
            }
        }
    }

    fun refreshAll() {
        if (_state.value is AppUiState.Ready) {
            updateReady { it.copy(syncing = true) }
        } else {
            _state.value = AppUiState.Loading
        }
        viewModelScope.launch { sync() }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.logout() }
            wipeLocalData()
            _state.value = AppUiState.Login
        }
    }

    fun backToLogin() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { session.clear() }
            wipeLocalData()
            _state.value = AppUiState.Login
        }
    }

    /** Returns a cipher for BiometricPrompt, or null when the key is gone (re-login required). */
    fun unlockCipher(): Cipher? {
        return when (val result = session.unlockCipher()) {
            is BiometricVault.CipherResult.Ready -> result.cipher
            BiometricVault.CipherResult.Invalidated -> {
                forceRelogin("기기의 생체 정보가 변경되어 잠금이 해제되었습니다. 다시 로그인해 주세요.")
                null
            }
            BiometricVault.CipherResult.Missing -> {
                forceRelogin("저장된 로그인 정보를 찾을 수 없습니다. 다시 로그인해 주세요.")
                null
            }
        }
    }

    fun onUnlocked(cipher: Cipher) {
        if (!session.unlock(cipher)) {
            forceRelogin("로그인 정보를 복원하지 못했습니다. 다시 로그인해 주세요.")
            return
        }
        val parked = parkedReady
        parkedReady = null
        if (parked != null) {
            _state.value = parked.copy(syncing = true)
            viewModelScope.launch { sync() }
        } else {
            _state.value = AppUiState.Loading
            openSession()
        }
    }

    fun onUnlockError(message: String?) {
        if (_state.value is AppUiState.Locked) _state.value = AppUiState.Locked(message)
    }

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun onForeground() {
        val away = System.currentTimeMillis() - backgroundAt
        if (backgroundAt == 0L || away < LOCK_AFTER_MS) return
        val ready = _state.value as? AppUiState.Ready ?: return
        if (!session.biometricEnabled) return
        parkedReady = ready.copy(meMessage = null)
        session.lock()
        _state.value = AppUiState.Locked()
    }

    fun prepareEnableBiometric(): Cipher? {
        val cipher = session.prepareBiometric()
        if (cipher == null) updateReady { it.copy(meMessage = "생체 인증을 설정할 수 없습니다.") }
        return cipher
    }

    fun confirmEnableBiometric(cipher: Cipher) {
        if (session.commitBiometric(cipher)) {
            SyncWorker.cancel(getApplication())
            updateReady {
                it.copy(
                    biometricEnabled = true,
                    meMessage = "생체 인증 잠금을 켰습니다. 앱을 열 때마다 인증합니다.",
                )
            }
        } else {
            updateReady { it.copy(meMessage = "생체 인증 확인에 실패했습니다.") }
        }
    }

    fun cancelEnableBiometric(message: String?) {
        session.cancelBiometric()
        updateReady { it.copy(meMessage = message ?: "생체 인증 설정을 취소했습니다.") }
    }

    fun disableBiometric() {
        session.disableBiometric()
        SyncWorker.schedule(getApplication())
        updateReady { it.copy(biometricEnabled = false, meMessage = "생체 인증 잠금을 껐습니다.") }
    }

    fun selectTab(tab: AppTab) {
        updateReady { it.copy(tab = tab, publicView = null, meMessage = null) }
        if (tab == AppTab.Relics) ensureRelicRecommend()
    }

    fun clearPublicView() = updateReady { it.copy(publicView = null) }

    fun onSearchQuery(query: String) {
        updateReady { it.copy(searchQuery = query) }
        suggestJob?.cancel()
        if (query.trim().length < 3) {
            updateReady { it.copy(searchSuggest = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(220)
            try {
                val hits = withContext(Dispatchers.IO) {
                    ProfileMapper.mapSearchHits(api.suggestProfiles(query.trim()))
                }
                updateReady { it.copy(searchSuggest = hits) }
            } catch (_: Exception) {
                updateReady { it.copy(searchSuggest = emptyList()) }
            }
        }
    }

    fun runSearch() {
        val ready = _state.value as? AppUiState.Ready ?: return
        val q = ready.searchQuery.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            updateReady { it.copy(searchBusy = true) }
            try {
                val hits = withContext(Dispatchers.IO) {
                    ProfileMapper.mapSearchHits(api.searchProfiles(q))
                }
                updateReady { it.copy(searchResults = hits, searchBusy = false, searchSuggest = emptyList()) }
            } catch (e: Exception) {
                updateReady { it.copy(searchBusy = false, meMessage = e.message) }
            }
        }
    }

    fun openPublicProfile(tag: String) {
        viewModelScope.launch {
            updateReady { it.copy(searchBusy = true) }
            try {
                val detail = withContext(Dispatchers.IO) { api.profileDetail(tag) }
                val runs = RunMapper.mapRuns(detail.optJSONArray("runs") ?: JSONArray())
                val roster = RunMapper.rosterFromRuns(runs)
                val stats = StatsBuilder.build(roster, runs)
                val deep = ProfileMapper.deepFromDetail(detail)
                val title = detail.optString("displayTag").ifBlank {
                    detail.optJSONArray("names")?.optString(0).orEmpty().ifBlank { tag }
                }
                updateReady {
                    it.copy(
                        searchBusy = false,
                        publicView = PublicProfileView(tag = tag, title = title, stats = stats, deepRatings = deep),
                        tab = AppTab.Stats,
                    )
                }
            } catch (e: Exception) {
                updateReady { it.copy(searchBusy = false, meMessage = e.message) }
            }
        }
    }

    fun setRelicOptions(
        jobId: Int? = null,
        buildId: String? = null,
        vessel: String? = null,
        includeDeep: Boolean? = null,
        includeShop: Boolean? = null,
        clearVessel: Boolean = false,
    ) {
        updateReady {
            it.copy(
                relicJobId = jobId ?: it.relicJobId,
                relicBuildId = buildId ?: it.relicBuildId,
                relicVessel = if (clearVessel) null else vessel ?: it.relicVessel,
                relicIncludeDeep = includeDeep ?: it.relicIncludeDeep,
                relicIncludeShop = includeShop ?: it.relicIncludeShop,
            )
        }
        ensureRelicRecommend()
    }

    fun refreshRelics() {
        viewModelScope.launch {
            val ready = _state.value as? AppUiState.Ready ?: return@launch
            updateReady { it.copy(relicBusy = true, relicError = null) }
            try {
                val relics = withContext(Dispatchers.IO) {
                    val key = ready.profiles.firstOrNull()?.profileKey
                    api.fetchRelics(key)
                }
                updateReady { it.copy(relicsJson = relics.toString(), relicBusy = false) }
                withContext(Dispatchers.IO) { runCatching { offline.saveRelics(relics) } }
                ensureRelicRecommend()
            } catch (e: Exception) {
                updateReady { it.copy(relicBusy = false, relicError = e.message) }
            }
        }
    }

    fun changePassword(current: String, next: String) {
        viewModelScope.launch {
            updateReady { it.copy(meBusy = true, meMessage = null) }
            try {
                withContext(Dispatchers.IO) { api.changePassword(current, next) }
                updateReady { it.copy(meBusy = false, meMessage = "비밀번호를 변경했습니다.") }
            } catch (e: Exception) {
                updateReady { it.copy(meBusy = false, meMessage = e.message) }
            }
        }
    }

    fun updateTag(profileId: String, tag: String) {
        viewModelScope.launch {
            updateReady { it.copy(meBusy = true, meMessage = null) }
            try {
                withContext(Dispatchers.IO) { api.updateTag(profileId, tag) }
                reloadProfilesOnly("검색 태그를 저장했습니다.")
            } catch (e: Exception) {
                updateReady { it.copy(meBusy = false, meMessage = e.message) }
            }
        }
    }

    fun updateAvatar(profileId: String, avatar: String) {
        viewModelScope.launch {
            updateReady { it.copy(meBusy = true, meMessage = null) }
            try {
                withContext(Dispatchers.IO) { api.updateAvatar(profileId, avatar) }
                reloadProfilesOnly("프로필 이미지를 저장했습니다.")
            } catch (e: Exception) {
                updateReady { it.copy(meBusy = false, meMessage = e.message) }
            }
        }
    }

    fun relicEngineOrNull(): RelicEngine? = relicEngine

    private suspend fun reloadProfilesOnly(message: String) {
        val me = withContext(Dispatchers.IO) {
            api.fetchMe().also { runCatching { offline.saveMe(it) } }
        }
        updateReady {
            it.copy(
                profiles = ProfileMapper.mapMeProfiles(me),
                meBusy = false,
                meMessage = message,
            )
        }
    }

    private fun ensureRelicRecommend() {
        val ready = _state.value as? AppUiState.Ready ?: return
        val engine = relicEngine
        if (engine == null) {
            updateReady { it.copy(relicError = "유물 엔진을 준비 중입니다. 잠시 후 다시 시도하세요.") }
            return
        }
        viewModelScope.launch {
            updateReady { it.copy(relicBusy = true, relicError = null) }
            try {
                val result = withContext(Dispatchers.Default) {
                    engine.recommend(
                        ready.relicsJson,
                        RelicOptions(
                            jobId = ready.relicJobId,
                            buildId = ready.relicBuildId,
                            vesselName = ready.relicVessel,
                            includeDeep = ready.relicIncludeDeep,
                            includeShop = ready.relicIncludeShop,
                            profileKey = ready.profiles.firstOrNull()?.profileKey,
                        ),
                    )
                }
                updateReady {
                    it.copy(
                        relicResult = result,
                        relicBusy = false,
                        relicBuildId = result.activeBuildId ?: it.relicBuildId,
                        relicError = result.message,
                    )
                }
            } catch (e: Exception) {
                updateReady { it.copy(relicBusy = false, relicError = e.message) }
            }
        }
    }

    private fun openSession() {
        viewModelScope.launch {
            val cached = HomeSync.cached(getApplication(), session.username)
            if (cached != null) applyHome(cached, offline = true, syncing = true)
            sync()
        }
    }

    private suspend fun sync() {
        val owner = session.username.orEmpty()
        try {
            val data = HomeSync.fetch(getApplication(), api, owner)
            applyHome(data, offline = false, syncing = false)
            if (!session.biometricEnabled) SyncWorker.schedule(getApplication())
        } catch (e: Exception) {
            if ((e as? ApiException)?.status == 401) {
                forceRelogin("세션이 만료되었습니다. 다시 로그인해 주세요.")
                return
            }
            val ready = _state.value as? AppUiState.Ready
            if (ready != null) {
                updateReady { it.copy(offline = true, syncing = false) }
            } else {
                val cached = HomeSync.cached(getApplication(), owner)
                if (cached != null) {
                    applyHome(cached, offline = true, syncing = false)
                } else {
                    _state.value = AppUiState.Error(e.message ?: "데이터를 불러오지 못했습니다.")
                }
            }
        }
    }

    private fun applyHome(data: HomeData, offline: Boolean, syncing: Boolean) {
        val previous = _state.value as? AppUiState.Ready ?: parkedReady
        val bio = biometricStatus()
        val base = previous ?: AppUiState.Ready(
            username = session.username ?: data.me.optString("email"),
            stats = data.stats,
            deepRatings = data.deepRatings,
            profiles = data.profiles,
        )
        _state.value = base.copy(
            username = session.username ?: data.me.optString("email").ifBlank { base.username },
            stats = data.stats,
            deepRatings = data.deepRatings,
            profiles = data.profiles,
            relicsJson = data.relics.toString(),
            offline = offline,
            syncing = syncing,
            syncedAt = data.syncedAt,
            biometricEnabled = session.biometricEnabled,
            biometricAvailable = bio.first,
            biometricHint = bio.second,
        )
        if ((_state.value as AppUiState.Ready).tab == AppTab.Relics) ensureRelicRecommend()
    }

    private fun biometricStatus(): Pair<Boolean, String?> {
        val code = BiometricManager.from(getApplication())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> true to null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false to "기기 설정에서 지문 또는 얼굴을 먼저 등록해 주세요."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> false to "이 기기는 강력한 생체 인증을 지원하지 않습니다."
            else -> false to "지금은 생체 인증을 사용할 수 없습니다."
        }
    }

    private fun forceRelogin(message: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { session.clear() }
            wipeLocalData()
            parkedReady = null
            _state.value = AppUiState.Error(message, backToLogin = true)
        }
    }

    private suspend fun wipeLocalData() {
        parkedReady = null
        SyncWorker.cancel(getApplication())
        withContext(Dispatchers.IO) { runCatching { offline.clear() } }
        NightWidget.refresh(getApplication())
    }

    private fun updateReady(block: (AppUiState.Ready) -> AppUiState.Ready) {
        _state.update { current ->
            if (current is AppUiState.Ready) block(current) else current
        }
    }

    private companion object {
        const val LOCK_AFTER_MS = 60_000L
    }
}
