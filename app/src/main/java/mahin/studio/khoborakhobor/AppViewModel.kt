package mahin.studio.khoborakhobor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val sources: List<NewsSource> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val offlinePages: List<OfflinePage> = emptyList(),
    val themePreference: ThemePreference = ThemePreference.System,
    val disableAds: Boolean = false,
    val websiteDarkMode: Boolean = false,
    val adBlockState: AdBlockState = AdBlockState(
        enabled = false,
        status = UBlockStatus.DISABLED
    ),
    val sourcesLoaded: Boolean = false,
    val offlineLoaded: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val preferences by lazy { AppPreferences(appContext) }
    private val offlineRepository by lazy { OfflinePageRepository(appContext) }
    private val _uiState = MutableStateFlow(AppUiState())
    private var initialLoadStarted = false
    private var reloadOfflineJob: Job? = null

    val uiState: StateFlow<AppUiState> = _uiState

    fun loadInitial() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        viewModelScope.launch {
            val preferencesDeferred = async(Dispatchers.IO) {
                PerformanceLogger.trace("loadPreferences") {
                    LoadedAppPreferences(
                        themePreference = preferences.loadThemePreference(),
                        favoriteIds = preferences.loadFavoriteIds(),
                        disableAds = preferences.loadDisableAds(),
                        websiteReaderDarkModeEnabled = preferences.loadWebsiteDarkMode()
                    )
                }
            }
            val sourcesDeferred = async(Dispatchers.IO) {
                NewsSourceRepository.load(appContext)
            }
            val offlineDeferred = async(Dispatchers.IO) {
                offlineRepository.loadPages()
            }

            val loadedPreferences = preferencesDeferred.await()
            _uiState.update {
                it.copy(
                    themePreference = loadedPreferences.themePreference,
                    favoriteIds = loadedPreferences.favoriteIds,
                    disableAds = loadedPreferences.disableAds,
                    websiteDarkMode = loadedPreferences.websiteReaderDarkModeEnabled,
                    adBlockState = AdBlockState(
                        enabled = loadedPreferences.disableAds,
                        status = if (loadedPreferences.disableAds) {
                            UBlockStatus.LOADING
                        } else {
                            UBlockStatus.DISABLED
                        }
                    )
                )
            }

            val sources = sourcesDeferred.await()
            _uiState.update {
                it.copy(
                    sources = sources,
                    sourcesLoaded = true
                )
            }

            val offlinePages = offlineDeferred.await()
            _uiState.update {
                it.copy(
                    offlinePages = offlinePages,
                    offlineLoaded = true
                )
            }
        }
    }

    fun toggleFavorite(sourceId: String) {
        val next = if (sourceId in _uiState.value.favoriteIds) {
            _uiState.value.favoriteIds - sourceId
        } else {
            _uiState.value.favoriteIds + sourceId
        }
        _uiState.update { it.copy(favoriteIds = next) }
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveFavoriteIds(next)
        }
    }

    fun setThemePreference(themePreference: ThemePreference) {
        _uiState.update { it.copy(themePreference = themePreference) }
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveThemePreference(themePreference)
        }
    }

    fun setDisableAds(enabled: Boolean) {
        _uiState.update {
            it.copy(
                disableAds = enabled,
                adBlockState = AdBlockState(
                    enabled = enabled,
                    status = if (enabled) UBlockStatus.LOADING else UBlockStatus.DISABLED
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveDisableAds(enabled)
        }
    }

    fun setAdBlockState(state: AdBlockState) {
        _uiState.update { it.copy(adBlockState = state) }
    }

    fun setWebsiteDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(websiteDarkMode = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveWebsiteDarkMode(enabled)
        }
    }

    fun deleteOfflinePage(page: OfflinePage, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val deleted = offlineRepository.deletePage(page)
                val pages = offlineRepository.loadPages()
                deleted to pages
            }
            _uiState.update {
                it.copy(
                    offlinePages = result.second,
                    offlineLoaded = true
                )
            }
            onComplete(result.first)
        }
    }

    suspend fun saveOfflinePage(source: NewsSource, snapshot: PageSnapshot): Result<OfflinePage> {
        return offlineRepository.savePage(source, snapshot)
    }

    fun reloadOfflinePages() {
        reloadOfflineJob?.cancel()
        reloadOfflineJob = viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                offlineRepository.loadPages()
            }
            _uiState.update {
                it.copy(
                    offlinePages = pages,
                    offlineLoaded = true
                )
            }
        }
    }
}

private data class LoadedAppPreferences(
    val themePreference: ThemePreference,
    val favoriteIds: Set<String>,
    val disableAds: Boolean,
    val websiteReaderDarkModeEnabled: Boolean
)
