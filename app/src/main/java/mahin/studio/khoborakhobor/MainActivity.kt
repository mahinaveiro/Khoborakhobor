package mahin.studio.khoborakhobor

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.android.material.bottomnavigation.BottomNavigationView
import mahin.studio.khoborakhobor.ui.theme.KhoborakhoborTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val APP_TAG = "Khoborakhobor"
private const val KEY_SELECTED_ITEM_ID = "selected_item_id"
// Debug + Android Studio instrumentation is slower; validate performance with release APK or Run without Debugging.

class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val controllerStore = RuntimeControllerStore()
    private val readerRepository by lazy(LazyThreadSafetyMode.NONE) {
        ReaderPageRepository(applicationContext)
    }
    internal val sourceIconCacheManager by lazy(LazyThreadSafetyMode.NONE) {
        SourceIconCacheManager(applicationContext)
    }

    private lateinit var jankTracker: AppJankTracker
    private lateinit var topBar: View
    private lateinit var topBarMark: ImageView
    private lateinit var topDivider: View
    private lateinit var bottomDivider: View
    private lateinit var topBarTitle: TextView
    private lateinit var themeButton: ImageButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tabs: List<NativeTab>
    private var selectedItemId: Int = R.id.nav_home
    private var currentState = AppUiState()
    private var overlayFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val appStartMillis = SystemClock.elapsedRealtime()
        installSplashScreen()
        installDebugStrictMode()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!GeckoRuntimeProvider.isCreated()) {
            PerformanceLogger.mark("GeckoRuntime not created during app startup")
        }

        jankTracker = AppJankTracker(window)
        selectedItemId = savedInstanceState?.getInt(KEY_SELECTED_ITEM_ID) ?: R.id.nav_home
        setContentView(R.layout.activity_main_native)
        bindShellViews()
        tabs = createNativeTabs()
        applyInsets()
        setupBottomNav()
        setupThemeButton()
        updateJankStateForShell()

        window.decorView.doOnPreDraw {
            PerformanceLogger.logDuration("AppStart first native frame", appStartMillis)
        }
        PerformanceLogger.logDuration("MainActivity onCreate", appStartMillis)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    currentState = state
                    applyShellState(state)
                    updateCurrentFragmentState(state)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sourceIconCacheManager.isLoading.collectLatest { isLoading ->
                    jankTracker.setState("isIconLoading", isLoading.toString())
                }
            }
        }
        window.decorView.post {
            setupTabFragments()
            updateCurrentFragmentState(currentState)
            viewModel.loadInitial()
        }
    }

    override fun onDestroy() {
        jankTracker.stop()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_ITEM_ID, selectedItemId)
        super.onSaveInstanceState(outState)
    }

    internal fun onOpenSource(source: NewsSource) {
        showOverlay(
            fragment = BrowserComposeFragment.newInstance(source.id),
            tag = BrowserComposeFragment.TAG,
            screen = "Browser"
        )
    }

    internal fun onOpenOfflinePage(page: OfflinePage) {
        showOverlay(
            fragment = OfflineReaderComposeFragment.newInstance(page.id),
            tag = OfflineReaderComposeFragment.TAG,
            screen = "Reader"
        )
    }

    internal fun closeOverlay() {
        val fragment = overlayFragment ?: supportFragmentManager.findFragmentByTag(BrowserComposeFragment.TAG)
            ?: supportFragmentManager.findFragmentByTag(OfflineReaderComposeFragment.TAG)
        if (fragment != null) {
            val selectedFragment = selectedTab()?.fragment
            supportFragmentManager.commitNow {
                remove(fragment)
                tabFragments().forEach { tabFragment ->
                    if (tabFragment == selectedFragment) show(tabFragment) else hide(tabFragment)
                }
            }
        }
        overlayFragment = null
        setShellVisible(true)
        updateJankStateForShell()
    }

    internal fun onToggleFavorite(source: NewsSource) {
        viewModel.toggleFavorite(source.id)
    }

    internal fun onDeleteOfflinePage(page: OfflinePage) {
        viewModel.deleteOfflinePage(page) { deleted ->
            Toast.makeText(
                applicationContext,
                if (deleted) "Deleted" else "Could not delete page",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    internal fun onThemePreferenceChange(themePreference: ThemePreference) {
        viewModel.setThemePreference(themePreference)
    }

    internal fun onDisableAdsChange(enabled: Boolean) {
        viewModel.setDisableAds(enabled)
        val controller = controllerStore.adBlockController
        if (controller != null) {
            controller.setEnabled(enabled) { nextState ->
                viewModel.setAdBlockState(nextState)
            }
        }
    }

    internal fun onWebsiteDarkModeChange(enabled: Boolean) {
        viewModel.setWebsiteDarkMode(enabled)
    }

    internal fun onClearCache(onResult: (Boolean) -> Unit) {
        val runtime = GeckoRuntimeProvider.getIfCreated()
        if (runtime == null) {
            PerformanceLogger.mark("Gecko cache clear skipped runtime not initialized")
            onResult(true)
        } else {
            clearGeckoCache(runtime, onResult)
        }
    }

    internal suspend fun buildReaderPage(
        source: NewsSource,
        url: String,
        darkMode: Boolean
    ): Result<ReaderPage> {
        return withContext(Dispatchers.IO) {
            readerRepository.buildReaderPage(url, source, darkMode)
        }
    }

    internal suspend fun buildOfflineReaderPage(
        page: OfflinePage,
        darkMode: Boolean
    ): Result<ReaderPage> {
        return withContext(Dispatchers.IO) {
            readerRepository.buildOfflineReaderPage(page, darkMode)
        }
    }

    internal suspend fun saveOfflinePage(
        source: NewsSource,
        url: String
    ): Result<OfflinePage> {
        val result = withContext(Dispatchers.IO) {
            PerformanceLogger.trace("saveOfflinePage") {
                readerRepository.fetchPageSnapshot(url, source).fold(
                    onSuccess = { snapshot -> viewModel.saveOfflinePage(source, snapshot) },
                    onFailure = { error -> Result.failure(error) }
                )
            }
        }
        if (result.isSuccess) {
            viewModel.reloadOfflinePages()
        }
        return result
    }

    internal fun runtimeWithExtensions(): GeckoRuntime {
        val runtime = GeckoRuntimeProvider.get(
            context = applicationContext,
            disableAds = currentState.disableAds,
            currentScreen = "Browser"
        )
        jankTracker.setState("isGeckoRuntimeInitialized", GeckoRuntimeProvider.isCreated().toString())
        jankTracker.setState("isGeckoActive", GeckoRuntimeProvider.isCreated().toString())
        if (currentState.disableAds) {
            ensureAdBlockController(runtime)
        }
        return runtime
    }

    internal fun runtimeOnly(): GeckoRuntime {
        val runtime = GeckoRuntimeProvider.get(
            context = applicationContext,
            disableAds = currentState.disableAds,
            currentScreen = "Reader"
        )
        jankTracker.setState("isGeckoRuntimeInitialized", GeckoRuntimeProvider.isCreated().toString())
        jankTracker.setState("isGeckoActive", GeckoRuntimeProvider.isCreated().toString())
        return runtime
    }

    internal fun setJankState(key: String, value: String) {
        jankTracker.setState(key, value)
    }

    internal fun isDarkTheme(): Boolean = resolveDarkTheme(currentState.themePreference)

    private fun bindShellViews() {
        topBar = findViewById(R.id.topBar)
        topBarMark = findViewById(R.id.topBarMark)
        topDivider = findViewById(R.id.topDivider)
        bottomDivider = findViewById(R.id.bottomDivider)
        topBarTitle = findViewById(R.id.topBarTitle)
        themeButton = findViewById(R.id.themeButton)
        bottomNav = findViewById(R.id.bottom_navigation)
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.appRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(topBar.paddingLeft, bars.top, topBar.paddingRight, topBar.paddingBottom)
            topBar.layoutParams = topBar.layoutParams.apply {
                height = dp(56) + bars.top
            }
            bottomNav.setPadding(0, 0, 0, bars.bottom)
            bottomNav.layoutParams = bottomNav.layoutParams.apply {
                height = dp(64) + bars.bottom
            }
            insets
        }
    }

    private fun setupTabFragments() {
        val selectedTab = tabForItemId(selectedItemId) ?: tabs.first().also {
            selectedItemId = it.itemId
        }
        supportFragmentManager.commitNow {
            tabs.forEach { tab ->
                if (!tab.fragment.isAdded) {
                    add(R.id.fragment_container, tab.fragment, tab.tag)
                }
                if (tab == selectedTab) {
                    show(tab.fragment)
                } else {
                    hide(tab.fragment)
                }
            }
        }
    }

    private fun setupBottomNav() {
        if (bottomNav.menu.findItem(selectedItemId) == null) {
            selectedItemId = R.id.nav_home
        }
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId, logSwitch = true)
            true
        }
    }

    private fun setupThemeButton() {
        themeButton.setOnClickListener {
            onThemePreferenceChange(
                if (isDarkTheme()) ThemePreference.Light else ThemePreference.Dark
            )
        }
    }

    private fun showTab(itemId: Int, logSwitch: Boolean) {
        val tab = tabForItemId(itemId) ?: return
        if (itemId == selectedItemId && overlayFragment == null) return
        val start = SystemClock.elapsedRealtime()
        selectedItemId = itemId
        supportFragmentManager.commitNow {
            tabs.forEach { currentTab ->
                if (!currentTab.fragment.isAdded) {
                    add(R.id.fragment_container, currentTab.fragment, currentTab.tag)
                }
                if (currentTab == tab) {
                    show(currentTab.fragment)
                } else {
                    hide(currentTab.fragment)
                }
            }
        }
        setShellVisible(true)
        overlayFragment = null
        applyShellState(currentState)
        updateCurrentFragmentState(currentState)
        updateJankStateForShell()
        if (logSwitch) {
            PerformanceLogger.logDuration("Tab switch ${tabLabel(itemId)}", start)
        }
    }

    private fun showOverlay(fragment: Fragment, tag: String, screen: String) {
        setShellVisible(false)
        overlayFragment?.let { existing ->
            supportFragmentManager.commitNow { remove(existing) }
        }
        supportFragmentManager.commitNow {
            tabFragments().forEach { tabFragment -> hide(tabFragment) }
            add(R.id.fragment_container, fragment, tag)
        }
        overlayFragment = fragment
        jankTracker.setState("currentScreen", screen)
        jankTracker.setState("selectedTab", tabLabel(selectedItemId))
        jankTracker.setState("isBrowserVisible", (screen == "Browser").toString())
        jankTracker.setState("isGeckoActive", GeckoRuntimeProvider.isCreated().toString())
        jankTracker.setState("isGeckoRuntimeInitialized", GeckoRuntimeProvider.isCreated().toString())
        GeckoRuntimeProvider.setCurrentScreen(screen)
    }

    private fun setShellVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = visibility
        topDivider.visibility = visibility
        bottomDivider.visibility = visibility
        bottomNav.visibility = visibility
    }

    private fun applyShellState(state: AppUiState) {
        topBarTitle.text = if (selectedItemId == R.id.nav_home) getString(R.string.app_name) else tabLabel(selectedItemId)
        themeButton.visibility = View.VISIBLE
        themeButton.setImageResource(
            if (resolveDarkTheme(state.themePreference)) R.drawable.ic_khobor_sun_24 else R.drawable.ic_khobor_moon_24
        )
        applyNativeColors(resolveDarkTheme(state.themePreference))
    }

    @Suppress("DEPRECATION")
    private fun applyNativeColors(darkTheme: Boolean) {
        val background = if (darkTheme) 0xFF080808.toInt() else 0xFFF4EFE6.toInt()
        val text = if (darkTheme) 0xFFF5F2EC.toInt() else 0xFF17130D.toInt()
        val muted = if (darkTheme) 0xFFA8A29A.toInt() else 0xFF6F675C.toInt()
        val divider = if (darkTheme) 0xFF2A2A2A.toInt() else 0xFFDED6CA.toInt()
        val ripple = 0x00000000
        findViewById<View>(R.id.appRoot).setBackgroundColor(background)
        topBar.setBackgroundColor(background)
        bottomNav.setBackgroundColor(background)
        topDivider.setBackgroundColor(divider)
        bottomDivider.setBackgroundColor(divider)
        topBarMark.imageTintList = ColorStateList.valueOf(text)
        topBarTitle.setTextColor(text)
        themeButton.imageTintList = ColorStateList.valueOf(muted)
        bottomNav.itemRippleColor = ColorStateList.valueOf(ripple)
        bottomNav.itemTextColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(text, muted)
        )
        bottomNav.itemIconTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(text, muted)
        )
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = !darkTheme
        }
    }

    private fun updateCurrentFragmentState(state: AppUiState) {
        if (overlayFragment != null) return
        (selectedTab()?.fragment as? NativeStateConsumer)?.render(state, isDarkTheme())
    }

    private fun updateJankStateForShell() {
        val screen = tabLabel(selectedItemId)
        jankTracker.setState("currentScreen", screen)
        jankTracker.setState("selectedTab", screen)
        jankTracker.setState("isBrowserVisible", "false")
        jankTracker.setState("isGeckoActive", GeckoRuntimeProvider.isCreated().toString())
        jankTracker.setState("isGeckoRuntimeInitialized", GeckoRuntimeProvider.isCreated().toString())
        jankTracker.setState("isGeneratingReader", "false")
        jankTracker.setState("isSavingOffline", "false")
        GeckoRuntimeProvider.setCurrentScreen(screen)
    }

    private fun ensureAdBlockController(runtime: GeckoRuntime): AdBlockController {
        controllerStore.adBlockController?.let { return it }
        return AdBlockController(applicationContext, runtime).also { controller ->
            controllerStore.adBlockController = controller
            controller.start(currentState.disableAds) { viewModel.setAdBlockState(it) }
        }
    }

    private fun createNativeTabs(): List<NativeTab> {
        return listOf(
            NativeTab(R.id.nav_home, existingOrCreateTabFragment("tab_home") { HomeNativeFragment() }, "tab_home"),
            NativeTab(R.id.nav_sources, existingOrCreateTabFragment("tab_sources") { SourcesNativeFragment() }, "tab_sources"),
            NativeTab(R.id.nav_offline, existingOrCreateTabFragment("tab_offline") { OfflineNativeFragment() }, "tab_offline"),
            NativeTab(R.id.nav_favorites, existingOrCreateTabFragment("tab_favorites") { FavoritesNativeFragment() }, "tab_favorites"),
            NativeTab(R.id.nav_settings, existingOrCreateTabFragment("tab_settings") { SettingsNativeFragment() }, "tab_settings")
        )
    }

    private fun existingOrCreateTabFragment(tag: String, create: () -> Fragment): Fragment {
        return supportFragmentManager.findFragmentByTag(tag) ?: create()
    }

    private fun tabForItemId(itemId: Int): NativeTab? {
        return tabs.firstOrNull { it.itemId == itemId }
    }

    private fun selectedTab(): NativeTab? {
        return if (::tabs.isInitialized) tabForItemId(selectedItemId) else null
    }

    private fun tabFragments(): List<Fragment> {
        return if (::tabs.isInitialized) tabs.map { it.fragment } else emptyList()
    }

    private fun tabLabel(itemId: Int): String {
        return when (itemId) {
            R.id.nav_home -> "Home"
            R.id.nav_sources -> "Sources"
            R.id.nav_offline -> "Offline"
            R.id.nav_favorites -> "Favorites"
            R.id.nav_settings -> "Settings"
            else -> "Home"
        }
    }

    private fun resolveDarkTheme(themePreference: ThemePreference): Boolean {
        return when (themePreference) {
            ThemePreference.System -> {
                val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                false
            }
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
    }

    private fun installDebugStrictMode() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class NativeTab(
        val itemId: Int,
        val fragment: Fragment,
        val tag: String
    )
}

private class RuntimeControllerStore {
    var adBlockController: AdBlockController? = null
}

private data class LoadedPreferences(
    val themePreference: ThemePreference,
    val favoriteIds: Set<String>,
    val disableAds: Boolean,
    val websiteReaderDarkModeEnabled: Boolean
)

@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.khobor_mark_topbar),
        contentDescription = "Khoborakhobor",
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Fit
    )
}

private fun clearGeckoCache(
    runtime: GeckoRuntime,
    onResult: (Boolean) -> Unit
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val flags = StorageController.ClearFlags.ALL_CACHES or
        StorageController.ClearFlags.IMAGE_CACHE or
        StorageController.ClearFlags.NETWORK_CACHE

    runtime.storageController.clearData(flags).accept(
        {
            mainHandler.post { onResult(true) }
        },
        { throwable ->
            Log.e(APP_TAG, "Could not clear GeckoView cache", throwable)
            mainHandler.post { onResult(false) }
        }
    )
}

private enum class AppTab(
    val label: String,
    val icon: ImageVector? = null,
    @param:DrawableRes val iconRes: Int? = null
) {
    Home("Home", icon = Icons.Filled.Home),
    Sources("Sources", icon = Icons.Filled.Menu),
    Offline("Offline", iconRes = R.drawable.ic_khobor_download_24),
    Favorites("Favorites", icon = Icons.Filled.Favorite),
    Settings("Settings", icon = Icons.Filled.Settings)
}

@Composable
private fun KhoborakhoborApp(
    sources: List<NewsSource>,
    sourceIconCacheManager: SourceIconCacheManager,
    getGeckoRuntime: () -> GeckoRuntime,
    getOfflineGeckoRuntime: () -> GeckoRuntime,
    favoriteIds: Set<String>,
    offlinePages: List<OfflinePage>,
    themePreference: ThemePreference,
    darkTheme: Boolean,
    disableAds: Boolean,
    adBlockState: AdBlockState,
    websiteDarkMode: Boolean,
    onToggleFavorite: (NewsSource) -> Unit,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onDisableAdsChange: (Boolean) -> Unit,
    onWebsiteDarkModeChange: (Boolean) -> Unit,
    onBuildReaderPage: suspend (NewsSource, String, Boolean) -> Result<ReaderPage>,
    onBuildOfflineReaderPage: suspend (OfflinePage, Boolean) -> Result<ReaderPage>,
    onSaveOfflinePage: suspend (NewsSource, String) -> Result<OfflinePage>,
    onDeleteOfflinePage: (OfflinePage) -> Unit,
    onClearCache: ((Boolean) -> Unit) -> Unit,
    onJankStateChange: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var selectedOfflinePageId by remember { mutableStateOf<String?>(null) }
    var pendingTabSwitchStart by remember { mutableStateOf<Pair<AppTab, Long>?>(null) }
    var composedTabs by remember { mutableStateOf(setOf(AppTab.Home)) }
    val selectedSource = remember(sources, selectedSourceId) {
        sources.firstOrNull { it.id == selectedSourceId }
    }
    val selectedOfflinePage = remember(offlinePages, selectedOfflinePageId) {
        offlinePages.firstOrNull { it.id == selectedOfflinePageId }
    }
    val stateHolder = rememberSaveableStateHolder()
    val favoriteSources by remember(sources, favoriteIds) {
        derivedStateOf { sources.filter { it.id in favoriteIds } }
    }
    val currentScreen = when {
        selectedSource != null -> "Browser"
        selectedOfflinePage != null -> "Reader"
        else -> selectedTab.label
    }

    LaunchedEffect(currentScreen, selectedTab) {
        onJankStateChange("currentScreen", currentScreen)
        onJankStateChange("selectedTab", selectedTab.label)
        onJankStateChange("isBrowserVisible", (selectedSource != null).toString())
        onJankStateChange("isGeckoRuntimeInitialized", GeckoRuntimeProvider.isCreated().toString())
        GeckoRuntimeProvider.setCurrentScreen(currentScreen)
        if (selectedSource == null) {
            onJankStateChange("isGeneratingReader", "false")
            onJankStateChange("isSavingOffline", "false")
        }
    }

    LaunchedEffect(selectedTab) {
        composedTabs = composedTabs + selectedTab
    }

    LaunchedEffect(sources.isNotEmpty()) {
        if (sources.isNotEmpty()) {
            AppTab.entries.forEach { tab ->
                withFrameNanos { }
                composedTabs = composedTabs + tab
            }
        }
    }

    selectedOfflinePage?.let { page ->
        OfflineReaderScreen(
            page = page,
            getGeckoRuntime = getOfflineGeckoRuntime,
            readerDarkMode = websiteDarkMode,
            onReaderDarkModeChange = onWebsiteDarkModeChange,
            onBuildOfflineReaderPage = onBuildOfflineReaderPage,
            onClose = { selectedOfflinePageId = null },
            onJankStateChange = onJankStateChange
        )
        return
    }

    selectedSource?.let { source ->
        BrowserScreen(
            source = source,
            allSources = sources,
            getGeckoRuntime = getGeckoRuntime,
            websiteDarkMode = websiteDarkMode,
            onWebsiteDarkModeChange = onWebsiteDarkModeChange,
            onBuildReaderPage = onBuildReaderPage,
            onSaveOfflinePage = onSaveOfflinePage,
            onClose = { selectedSourceId = null },
            onHome = {
                selectedSourceId = null
                selectedTab = AppTab.Home
            },
            onJankStateChange = onJankStateChange
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (selectedTab == AppTab.Home) "Khoborakhobor" else selectedTab.label,
                showThemeToggle = selectedTab == AppTab.Home,
                darkTheme = darkTheme,
                onThemeToggle = {
                    onThemePreferenceChange(
                        if (darkTheme) ThemePreference.Light else ThemePreference.Dark
                    )
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab != selectedTab) {
                        pendingTabSwitchStart = tab to SystemClock.elapsedRealtime()
                        selectedTab = tab
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AppTab.entries.forEach { tab ->
                if (tab == selectedTab || tab in composedTabs) {
                    key(tab) {
                        stateHolder.SaveableStateProvider(tab.name) {
                            KeepAliveTabLayer(active = selectedTab == tab) {
                                when (tab) {
                                    AppTab.Home -> HomeScreen(
                                        sources = sources,
                                        sourceIconCacheManager = sourceIconCacheManager,
                                        favoriteIds = favoriteIds,
                                        disableAds = disableAds,
                                        adBlockState = adBlockState,
                                        iconsEnabled = selectedTab == tab,
                                        onOpenSource = { selectedSourceId = it.id },
                                        onToggleFavorite = onToggleFavorite,
                                        onJankStateChange = onJankStateChange
                                    )

                                    AppTab.Sources -> SourcesScreen(
                                        sources = sources,
                                        sourceIconCacheManager = sourceIconCacheManager,
                                        favoriteIds = favoriteIds,
                                        iconsEnabled = selectedTab == tab,
                                        onOpenSource = { selectedSourceId = it.id },
                                        onToggleFavorite = onToggleFavorite,
                                        onJankStateChange = onJankStateChange
                                    )

                                    AppTab.Offline -> OfflineScreen(
                                        pages = offlinePages,
                                        onOpenPage = { selectedOfflinePageId = it.id },
                                        onDeletePage = onDeleteOfflinePage
                                    )

                                    AppTab.Favorites -> SourceCollectionScreen(
                                        title = "Favorites",
                                        sources = favoriteSources,
                                        sourceIconCacheManager = sourceIconCacheManager,
                                        favoriteIds = favoriteIds,
                                        emptyText = "Favorite sources will appear here.",
                                        iconsEnabled = selectedTab == tab,
                                        onOpenSource = { selectedSourceId = it.id },
                                        onToggleFavorite = onToggleFavorite,
                                        onJankStateChange = onJankStateChange
                                    )

                                    AppTab.Settings -> SettingsScreen(
                                        themePreference = themePreference,
                                        disableAds = disableAds,
                                        adBlockState = adBlockState,
                                        websiteDarkMode = websiteDarkMode,
                                        onThemePreferenceChange = onThemePreferenceChange,
                                        onDisableAdsChange = onDisableAdsChange,
                                        onWebsiteDarkModeChange = { onWebsiteDarkModeChange(it) },
                                        onClearCache = onClearCache
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        val pending = pendingTabSwitchStart
        if (pending?.first == selectedTab) {
            PerformanceLogger.logDuration("Tab switch ${selectedTab.label}", pending.second)
            pendingTabSwitchStart = null
        }
    }
}

@Composable
private fun AppTopBar(
    title: String,
    showThemeToggle: Boolean,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppMark(modifier = Modifier.size(38.dp))
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showThemeToggle) {
                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (darkTheme) {
                                R.drawable.ic_khobor_sun_24
                            } else {
                                R.drawable.ic_khobor_moon_24
                            }
                        ),
                        contentDescription = if (darkTheme) "Use light theme" else "Use dark theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
    }
}

@Composable
private fun AppBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    ) {
                        val iconTint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val iconModifier = Modifier
                            .padding(horizontal = 13.dp, vertical = 5.dp)
                            .size(19.dp)
                        if (tab.icon != null) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = iconModifier,
                                tint = iconTint
                            )
                        } else if (tab.iconRes != null) {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = tab.label,
                                modifier = iconModifier,
                                tint = iconTint
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepAliveTabLayer(
    active: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else -1f)
            .alpha(if (active) 1f else 0f)
    ) {
        content()
    }
}

@Composable
private fun HomeScreen(
    sources: List<NewsSource>,
    sourceIconCacheManager: SourceIconCacheManager,
    favoriteIds: Set<String>,
    disableAds: Boolean,
    adBlockState: AdBlockState,
    iconsEnabled: Boolean,
    onOpenSource: (NewsSource) -> Unit,
    onToggleFavorite: (NewsSource) -> Unit,
    onJankStateChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var firstCompositionLogged by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val homeCategories = remember { HomeCategories }
    val popularSources by remember(sources) {
        derivedStateOf { sources.filter { it.country == "BD" && it.type == "newspaper" }.take(10) }
    }
    val popularSourceIds by remember(popularSources) {
        derivedStateOf { popularSources.map { it.id }.toSet() }
    }
    val englishSources by remember(sources, popularSourceIds) {
        derivedStateOf {
            sources
                .filter { (it.category == "English" || it.language == "en") && it.id !in popularSourceIds }
                .take(10)
        }
    }
    val featuredSourceIds by remember(popularSourceIds, englishSources) {
        derivedStateOf { popularSourceIds + englishSources.map { it.id } }
    }
    val liveSources by remember(sources, featuredSourceIds) {
        derivedStateOf { sources.filter { (it.category == "Live" || it.type == "tv") && it.id !in featuredSourceIds } }
    }
    val displayedSources = remember(popularSources, englishSources, liveSources) {
        popularSources + englishSources + liveSources
    }
    LaunchedEffect(Unit) {
        if (!firstCompositionLogged) {
            firstCompositionLogged = true
            PerformanceLogger.mark("Home first composition complete")
        }
    }
    LaunchedEffect(selectedCategory) {
        onJankStateChange("selectedCategory", selectedCategory ?: "All")
    }

    if (sources.isEmpty()) {
        SourceListLoadingState(modifier = modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(
            key = "home-header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "header"
        ) {
            HomeHeader(
                totalSources = sources.size,
                categoryCount = homeCategories.size,
                disableAds = disableAds,
                adBlockState = adBlockState
            )
        }
        item(
            key = "home-categories",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "category-row"
        ) {
            CategoryChipRow(
                categories = homeCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                contentPadding = PaddingValues(0.dp)
            )
        }
        item(
            key = "home-popular-title",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "section-header"
        ) {
            SectionHeader(title = "Popular in Bangladesh", count = popularSources.size)
        }
        items(
            items = popularSources,
            key = { it.id },
            contentType = { "source-card" }
        ) { source ->
            SourceCard(
                source = source,
                isFavorite = source.id in favoriteIds,
                sourceIconCacheManager = sourceIconCacheManager,
                loadIcon = iconsEnabled,
                onOpen = { onOpenSource(source) },
                onToggleFavorite = { onToggleFavorite(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
            )
        }
        item(
            key = "home-english-title",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "section-header"
        ) {
            SectionHeader(title = "English sources", count = englishSources.size)
        }
        items(
            items = englishSources,
            key = { it.id },
            contentType = { "source-card" }
        ) { source ->
            SourceCard(
                source = source,
                isFavorite = source.id in favoriteIds,
                sourceIconCacheManager = sourceIconCacheManager,
                loadIcon = iconsEnabled,
                onOpen = { onOpenSource(source) },
                onToggleFavorite = { onToggleFavorite(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
            )
        }
        item(
            key = "home-live-title",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "section-header"
        ) {
            SectionHeader(title = "Live news", count = liveSources.size)
        }
        items(
            items = liveSources,
            key = { it.id },
            contentType = { "source-card" }
        ) { source ->
            SourceCard(
                source = source,
                isFavorite = source.id in favoriteIds,
                sourceIconCacheManager = sourceIconCacheManager,
                loadIcon = iconsEnabled,
                onOpen = { onOpenSource(source) },
                onToggleFavorite = { onToggleFavorite(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
            )
        }
    }
}

@Composable
private fun HomeHeader(
    totalSources: Int,
    categoryCount: Int,
    disableAds: Boolean,
    adBlockState: AdBlockState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "BD + world newspapers",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderStat(text = "$totalSources sources", modifier = Modifier.weight(1f))
            HeaderStat(text = "$categoryCount categories", modifier = Modifier.weight(1f))
            HeaderStat(
                text = "Blocking ${if (disableAds && (adBlockState.status == UBlockStatus.ACTIVE || adBlockState.status == UBlockStatus.FALLBACK_ACTIVE)) "On" else "Off"}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeaderStat(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourcesScreen(
    sources: List<NewsSource>,
    sourceIconCacheManager: SourceIconCacheManager,
    favoriteIds: Set<String>,
    iconsEnabled: Boolean,
    onOpenSource: (NewsSource) -> Unit,
    onToggleFavorite: (NewsSource) -> Unit,
    onJankStateChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    val gridState = rememberLazyGridState()
    val filterCategories = remember { SourceFilterCategories }
    val filteredSources by remember(query, selectedCategory, sources) {
        derivedStateOf {
            sources.filter { source ->
                val matchesQuery = query.isBlank() ||
                    source.name.contains(query, ignoreCase = true) ||
                    source.category.contains(query, ignoreCase = true) ||
                    source.type.contains(query, ignoreCase = true)
                val matchesCategory = selectedCategory == "All" || source.matchesCategory(selectedCategory)
                matchesQuery && matchesCategory
            }
        }
    }
    LaunchedEffect(selectedCategory) {
        onJankStateChange("selectedCategory", selectedCategory)
    }

    if (sources.isEmpty()) {
        SourceListLoadingState(modifier = modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(
            key = "sources-search",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "search"
        ) {
            SourceSearchField(
                value = query,
                onValueChange = { query = it }
            )
        }
        item(
            key = "sources-categories",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "category-row"
        ) {
            CategoryChipRow(
                categories = filterCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )
        }
        if (filteredSources.isEmpty()) {
            item(
                key = "sources-empty",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "empty"
            ) {
                EmptyState(text = "No sources match this search.")
            }
        } else {
            items(
                items = filteredSources,
                key = { it.id },
                contentType = { "source-card" }
            ) { source ->
                SourceCard(
                    source = source,
                    isFavorite = source.id in favoriteIds,
                    sourceIconCacheManager = sourceIconCacheManager,
                    loadIcon = iconsEnabled,
                    onOpen = { onOpenSource(source) },
                    onToggleFavorite = { onToggleFavorite(source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceCollectionScreen(
    title: String,
    sources: List<NewsSource>,
    sourceIconCacheManager: SourceIconCacheManager,
    favoriteIds: Set<String>,
    emptyText: String,
    iconsEnabled: Boolean,
    onOpenSource: (NewsSource) -> Unit,
    onToggleFavorite: (NewsSource) -> Unit,
    onJankStateChange: (String, String) -> Unit,
    showCategoryFilter: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable(title) { mutableStateOf("All") }
    val gridState = rememberLazyGridState()
    val filterCategories = remember(sources) {
        listOf("All") + sources.map { it.category }.distinct()
    }
    val visibleSources by remember(sources, selectedCategory) {
        derivedStateOf {
            if (selectedCategory == "All") {
                sources
            } else {
                sources.filter { it.matchesCategory(selectedCategory) }
            }
        }
    }
    LaunchedEffect(selectedCategory) {
        onJankStateChange("selectedCategory", selectedCategory)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showCategoryFilter && filterCategories.size > 1) {
            item(
                key = "$title-categories",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "category-row"
            ) {
                CategoryChipRow(
                    categories = filterCategories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }
        }
        if (visibleSources.isEmpty()) {
            item(
                key = "$title-empty",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "empty"
            ) {
                EmptyState(text = emptyText)
            }
        } else {
            items(
                items = visibleSources,
                key = { it.id },
                contentType = { "source-card" }
            ) { source ->
                SourceCard(
                    source = source,
                    isFavorite = source.id in favoriteIds,
                    sourceIconCacheManager = sourceIconCacheManager,
                    loadIcon = iconsEnabled,
                    onOpen = { onOpenSource(source) },
                    onToggleFavorite = { onToggleFavorite(source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineScreen(
    pages: List<OfflinePage>,
    onOpenPage: (OfflinePage) -> Unit,
    onDeletePage: (OfflinePage) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<OfflinePage?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pages.isEmpty()) {
            item(
                key = "offline-empty",
                contentType = "empty"
            ) {
                OfflineEmptyState()
            }
        } else {
            items(
                items = pages,
                key = { it.id },
                contentType = { "offline-page" }
            ) { page ->
                OfflinePageRow(
                    page = page,
                    onOpen = { onOpenPage(page) },
                    onDelete = { pendingDelete = page }
                )
            }
        }
    }

    val pageToDelete = pendingDelete
    if (pageToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete offline page?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeletePage(pageToDelete)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OfflineEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "No offline pages yet",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Open a newspaper page and tap download.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OfflinePageRow(
    page: OfflinePage,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfflinePageLogo(page = page)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = page.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = page.sourceName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = shortSavedAt(page.savedAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_khobor_delete_24),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themePreference: ThemePreference,
    disableAds: Boolean,
    adBlockState: AdBlockState,
    websiteDarkMode: Boolean,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onDisableAdsChange: (Boolean) -> Unit,
    onWebsiteDarkModeChange: (Boolean) -> Unit,
    onClearCache: ((Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var cacheMessage by rememberSaveable { mutableStateOf("") }
    var cacheClearing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsPanel(title = "Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { option ->
                    FilterChip(
                        selected = themePreference == option,
                        onClick = { onThemePreferenceChange(option) },
                        label = { Text(option.name) }
                    )
                }
            }
        }
        SettingsPanel(title = "Reading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Disable Ads", style = MaterialTheme.typography.titleMedium)
                    Text(
                        adBlockStatusText(disableAds = disableAds, adBlockState = adBlockState),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                KhoborSwitch(
                    checked = disableAds,
                    onCheckedChange = onDisableAdsChange
                )
            }
            if (adBlockState.status == UBlockStatus.FALLBACK_ACTIVE) {
                Text(
                    text = "uBlock failed. Using fallback blocker.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (
                (adBlockState.status == UBlockStatus.FAILED ||
                    adBlockState.status == UBlockStatus.FALLBACK_ACTIVE) &&
                adBlockState.error != null
            ) {
                Text(
                    text = "uBlock error: ${adBlockState.error}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Reader Dark Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (websiteDarkMode) "On" else "Off",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                KhoborSwitch(
                    checked = websiteDarkMode,
                    onCheckedChange = onWebsiteDarkModeChange
                )
            }
            OutlinedButton(
                onClick = {
                    cacheClearing = true
                    cacheMessage = ""
                    onClearCache { success ->
                        cacheClearing = false
                        cacheMessage = if (success) "Cache cleared" else "Could not clear cache"
                        Toast.makeText(context, cacheMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !cacheClearing,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (cacheClearing) "Clearing..." else "Clear cache")
            }
            if (cacheMessage.isNotBlank()) {
                Text(
                    text = cacheMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SettingsPanel(title = "About") {
            Text(
                text = "Khoborakhobor is a newspaper website library. It opens trusted sources inside a clean app shell without scraping or rewriting their articles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun KhoborSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(width = 48.dp, height = 28.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = Color(0xFFE8E8E8),
            uncheckedTrackColor = Color(0xFF777777),
            uncheckedBorderColor = Color(0xFF777777),
            disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
            disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
            disabledUncheckedThumbColor = Color(0xFFDADADA),
            disabledUncheckedTrackColor = Color(0xFF8A8A8A)
        )
    )
}

private fun adBlockStatusText(
    disableAds: Boolean,
    adBlockState: AdBlockState
): String {
    return when {
        !disableAds -> "Off"
        adBlockState.status == UBlockStatus.ACTIVE -> "uBlock active"
        adBlockState.status == UBlockStatus.FALLBACK_ACTIVE -> "Privacy blocking active"
        adBlockState.status == UBlockStatus.LOADING -> if (GeckoRuntimeProvider.isCreated()) "Loading..." else "uBlock active"
        else -> "Off"
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            content()
        }
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "$count",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val selected = selectedCategory == category
            FilterChip(
                selected = selected,
                onClick = { onCategorySelected(category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun SourceSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search"
            )
        },
        placeholder = { Text("Search sources") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun SourceListLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = "Loading sources",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: NewsSource,
    isFavorite: Boolean,
    sourceIconCacheManager: SourceIconCacheManager,
    loadIcon: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceLogo(
                    source = source,
                    sourceIconCacheManager = sourceIconCacheManager,
                    loadIcon = loadIcon
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        modifier = Modifier.size(18.dp),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
                        }
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = source.domainLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourcePill(
                    text = source.category,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "${source.language.uppercase()} / ${source.country}",
                    modifier = Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceLogo(
    source: NewsSource,
    sourceIconCacheManager: SourceIconCacheManager,
    loadIcon: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 36.dp
    val iconSizePx = remember(density) {
        with(density) { iconSize.roundToPx() }
    }
    val iconStateFlow = remember(source.id) {
        sourceIconCacheManager.stateFor(source)
    }
    val iconState by iconStateFlow.collectAsState()

    LaunchedEffect(source.id, source.iconUrl, source.url, loadIcon) {
        if (loadIcon) {
            withFrameNanos { }
            sourceIconCacheManager.request(source)
        }
    }

    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        InitialsLogo(initials = SourceIconResolver.fallbackInitials(source))
        val readyState = iconState as? SourceIconState.Ready
        if (readyState != null) {
            val request = remember(readyState.file.absolutePath, iconSizePx) {
                ImageRequest.Builder(context)
                    .data(readyState.file)
                    .size(iconSizePx, iconSizePx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "${source.name} icon",
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Fit,
                onError = { sourceIconCacheManager.request(source) }
            )
        }
    }
}

@Composable
private fun OfflinePageLogo(page: OfflinePage) {
    InitialsLogo(initials = initialsForName(page.sourceName))
}

@Composable
private fun InitialsLogo(
    initials: String
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun SourcePill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
internal fun OfflineReaderScreen(
    page: OfflinePage,
    getGeckoRuntime: () -> GeckoRuntime,
    readerDarkMode: Boolean,
    onReaderDarkModeChange: (Boolean) -> Unit,
    onBuildOfflineReaderPage: suspend (OfflinePage, Boolean) -> Result<ReaderPage>,
    onClose: () -> Unit,
    onJankStateChange: (String, String) -> Unit
) {
    val offlineFile = remember(page.offlineDisplayPath) { File(page.offlineDisplayPath) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var localReaderPath by remember(page.id) { mutableStateOf<String?>(null) }
    var renderError by remember(page.id) { mutableStateOf(false) }
    var readerBusy by remember(page.id) { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(offlineFile.exists()) }
    var progress by remember { mutableFloatStateOf(0f) }

    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    BackHandler(onBack = onClose)

    if (!offlineFile.exists()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            OfflineReaderTopBar(
                title = page.title,
                sourceName = page.sourceName,
                readerDarkMode = readerDarkMode,
                onThemeToggle = { onReaderDarkModeChange(!readerDarkMode) },
                onBack = onClose
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(text = "Offline page file is missing.")
            }
        }
        return
    }

    val session = remember(page.id) {
        GeckoSession().apply {
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    return if (isAllowedOfflineReaderUrl(request.uri)) {
                        GeckoResult.allow()
                    } else {
                        GeckoResult.deny()
                    }
                }

                override fun onSubframeLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    return GeckoResult.allow()
                }
            }
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    onMain {
                        isLoading = true
                        progress = 0.04f
                    }
                }

                override fun onProgressChange(session: GeckoSession, newProgress: Int) {
                    onMain {
                        progress = (newProgress.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f)
                    }
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    onMain {
                        isLoading = false
                        progress = if (success) 1f else 0f
                    }
                }
            }
            PerformanceLogger.trace("openBrowser") {
                open(getGeckoRuntime())
            }
        }
    }

    LaunchedEffect(page.id, readerDarkMode) {
        onJankStateChange("isGeneratingReader", "true")
        readerBusy = true
        isLoading = true
        progress = 0.04f
        val result = onBuildOfflineReaderPage(page, readerDarkMode)
        onJankStateChange("isGeneratingReader", "false")
        readerBusy = false
        result.fold(
            onSuccess = { readerPage ->
                localReaderPath = readerPage.localHtmlPath
                renderError = false
                session.loadUri(Uri.fromFile(File(readerPage.localHtmlPath)).toString())
            },
            onFailure = {
                renderError = true
                isLoading = false
                progress = 0f
            }
        )
    }

    DisposableEffect(session) {
        onDispose {
            session.navigationDelegate = null
            session.progressDelegate = null
            session.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        OfflineReaderTopBar(
            title = page.title,
            sourceName = page.sourceName,
            readerDarkMode = readerDarkMode,
            onThemeToggle = { onReaderDarkModeChange(!readerDarkMode) },
            onBack = onClose
        )
        BrowserProgressBar(
            isLoading = isLoading || readerBusy,
            progress = progress
        )
        when {
            renderError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(text = "Could not open offline page.")
                }
            }

            localReaderPath == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(text = "Preparing offline page.")
                }
            }

            else -> {
                AndroidView(
                    factory = { viewContext ->
                        GeckoView(viewContext).apply {
                            setSession(session)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun OfflineReaderTopBar(
    title: String,
    sourceName: String,
    readerDarkMode: Boolean,
    onThemeToggle: () -> Unit,
    onBack: () -> Unit
) {
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val toolbarBackground = if (darkUi) Color(0xFF080808) else Color(0xFFF4EFE6)
    val toolbarContent = if (darkUi) Color(0xFFF5F2EC) else Color(0xFF17130D)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(toolbarBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = toolbarContent
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = toolbarContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = toolbarContent.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    painter = painterResource(
                        id = if (readerDarkMode) {
                            R.drawable.ic_khobor_sun_24
                        } else {
                            R.drawable.ic_khobor_moon_24
                        }
                    ),
                    contentDescription = if (readerDarkMode) "Use light reader" else "Use dark reader",
                    tint = toolbarContent
                )
            }
        }
        HorizontalDivider(color = if (darkUi) Color(0xFF2A2A2A) else Color(0xFFE2E0D8))
    }
}

@Composable
internal fun BrowserScreen(
    source: NewsSource,
    allSources: List<NewsSource>,
    getGeckoRuntime: () -> GeckoRuntime,
    websiteDarkMode: Boolean,
    onWebsiteDarkModeChange: (Boolean) -> Unit,
    onBuildReaderPage: suspend (NewsSource, String, Boolean) -> Result<ReaderPage>,
    onSaveOfflinePage: suspend (NewsSource, String) -> Result<OfflinePage>,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onJankStateChange: (String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val allowedHosts = remember(allSources) { allSources.mapNotNull { normalizedHost(it.url) }.toSet() }
    val sessionActive = remember(source.id) { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var browserIssue by remember { mutableStateOf<BrowserIssue?>(null) }
    var currentUrl by remember(source.id) { mutableStateOf(source.url) }
    var savingOffline by remember { mutableStateOf(false) }
    var readerBusy by remember { mutableStateOf(false) }
    var readerModeActive by remember(source.id) { mutableStateOf(false) }
    var readerOriginalUrl by remember(source.id) { mutableStateOf<String?>(null) }

    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    val session = remember(source.id) {
        GeckoSession().apply {
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, canNavigateBack: Boolean) {
                    onMain { canGoBack = canNavigateBack }
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    if (url != null && isAllowedTopLevelUrl(url, allowedHosts)) {
                        onMain {
                            currentUrl = url
                            readerModeActive = false
                            readerOriginalUrl = null
                        }
                    }
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    return if (
                        isAllowedTopLevelUrl(request.uri, allowedHosts) ||
                        isAllowedBrowserReaderUrl(request.uri)
                    ) {
                        GeckoResult.allow()
                    } else {
                        onMain {
                            isLoading = false
                            progress = 0f
                            browserIssue = BrowserIssue.Blocked(request.uri)
                        }
                        GeckoResult.deny()
                    }
                }

                override fun onSubframeLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    return GeckoResult.allow()
                }

                override fun onLoadError(
                    session: GeckoSession,
                    uri: String?,
                    error: WebRequestError
                ): GeckoResult<String> {
                    onMain {
                        currentUrl = uri?.takeIf { isAllowedTopLevelUrl(it, allowedHosts) } ?: source.url
                        isLoading = false
                        progress = 0f
                        browserIssue = BrowserIssue.LoadFailure.from(error)
                    }
                    return GeckoResult.fromValue("about:blank")
                }
            }
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    onMain {
                        if (isAllowedTopLevelUrl(url, allowedHosts)) {
                            currentUrl = url
                            readerModeActive = false
                            readerOriginalUrl = null
                        }
                        browserIssue = null
                        isLoading = true
                        progress = 0.04f
                    }
                }

                override fun onProgressChange(session: GeckoSession, newProgress: Int) {
                    onMain {
                        progress = (newProgress.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f)
                    }
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    onMain {
                        isLoading = false
                        progress = if (success) 1f else 0f
                    }
                }
            }
            PerformanceLogger.trace("openBrowser") {
                open(getGeckoRuntime())
            }
            if (sessionActive.value) {
                loadUri(source.url)
            }
        }
    }

    fun currentFetchUrl(): String {
        return readerOriginalUrl?.takeIf { isAllowedTopLevelUrl(it, allowedHosts) }
            ?: currentUrl.takeIf { isAllowedTopLevelUrl(it, allowedHosts) }
            ?: source.url
    }

    fun openReaderMode(targetUrl: String, darkMode: Boolean) {
        if (readerBusy || !isAllowedTopLevelUrl(targetUrl, allowedHosts)) return
        readerBusy = true
        isLoading = true
        progress = 0.04f
        onJankStateChange("isGeneratingReader", "true")
        coroutineScope.launch {
            val result = onBuildReaderPage(source, targetUrl, darkMode)
            onJankStateChange("isGeneratingReader", "false")
            readerBusy = false
            result.fold(
                onSuccess = { page ->
                    readerOriginalUrl = page.originalUrl.ifBlank { targetUrl }
                    readerModeActive = true
                    browserIssue = null
                    session.loadUri(Uri.fromFile(File(page.localHtmlPath)).toString())
                },
                onFailure = {
                    isLoading = false
                    progress = 0f
                    Toast.makeText(context, "Could not open reader", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun reloadCurrentPage() {
        if (readerModeActive) {
            openReaderMode(currentFetchUrl(), websiteDarkMode)
            return
        }
        val target = currentFetchUrl()
        browserIssue = null
        isLoading = true
        progress = 0.04f
        session.loadUri(target)
    }

    fun leaveBlockedPage() {
        browserIssue = null
        if (canGoBack) {
            session.goBack()
        } else {
            session.loadUri(source.url)
        }
    }

    BackHandler {
        when {
            browserIssue is BrowserIssue.Blocked -> leaveBlockedPage()
            canGoBack -> session.goBack()
            else -> onClose()
        }
    }

    fun saveCurrentPage() {
        if (savingOffline) return
        savingOffline = true
        onJankStateChange("isSavingOffline", "true")
        val targetUrl = currentFetchUrl()
        coroutineScope.launch {
            val saveResult = onSaveOfflinePage(source, targetUrl)
            onJankStateChange("isSavingOffline", "false")
            savingOffline = false
            Toast.makeText(
                context,
                if (saveResult.isSuccess) "Saved for offline" else "Could not save page",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(session) {
        onDispose {
            sessionActive.value = false
            session.navigationDelegate = null
            session.progressDelegate = null
            session.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        BrowserTopBar(
            sourceName = source.name,
            readerModeActive = readerModeActive,
            savingOffline = savingOffline || readerBusy,
            onBack = {
                if (browserIssue is BrowserIssue.Blocked) {
                    leaveBlockedPage()
                } else if (canGoBack) {
                    session.goBack()
                } else {
                    onClose()
                }
            },
            onDownload = { saveCurrentPage() },
            onReload = { reloadCurrentPage() },
            onWebsiteDarkModeToggle = {
                if (readerModeActive) {
                    val originalUrl = currentFetchUrl()
                    readerModeActive = false
                    readerOriginalUrl = null
                    browserIssue = null
                    isLoading = true
                    progress = 0.04f
                    session.loadUri(originalUrl)
                } else {
                    openReaderMode(currentFetchUrl(), darkMode = websiteDarkMode)
                }
            },
            onHome = onHome
        )
        BrowserProgressBar(
            isLoading = isLoading || readerBusy,
            progress = progress
        )
        Box(modifier = Modifier.fillMaxSize()) {
            val issue = browserIssue
            if (issue == null) {
                AndroidView(
                    factory = { viewContext ->
                        GeckoView(viewContext).apply {
                            setSession(session)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            } else {
                BrowserIssuePage(
                    issue = issue,
                    onRetry = { reloadCurrentPage() },
                    onGoBack = {
                        if (issue is BrowserIssue.Blocked) {
                            leaveBlockedPage()
                        } else if (canGoBack) {
                            browserIssue = null
                            session.goBack()
                        } else {
                            onClose()
                        }
                    },
                    onOpenExternally = { blockedUrl ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(blockedUrl)))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BrowserTopBar(
    sourceName: String,
    readerModeActive: Boolean,
    savingOffline: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onReload: () -> Unit,
    onWebsiteDarkModeToggle: () -> Unit,
    onHome: () -> Unit
) {
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val toolbarBackground = if (darkUi) Color(0xFF080808) else Color(0xFFF4EFE6)
    val toolbarContent = if (darkUi) Color(0xFFF5F2EC) else Color(0xFF17130D)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(toolbarBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = toolbarContent
                )
            }
            Text(
                text = sourceName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = toolbarContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDownload,
                enabled = !savingOffline
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_khobor_download_24),
                    contentDescription = if (savingOffline) "Saving" else "Save offline",
                    tint = if (savingOffline) toolbarContent.copy(alpha = 0.45f) else toolbarContent
                )
            }
            IconButton(onClick = onReload) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reload",
                    tint = toolbarContent
                )
            }
            IconButton(onClick = onWebsiteDarkModeToggle) {
                Icon(
                    painter = painterResource(
                        id = if (readerModeActive) {
                            R.drawable.ic_khobor_sun_24
                        } else {
                            R.drawable.ic_khobor_moon_24
                        }
                    ),
                    contentDescription = if (readerModeActive) "Back to site" else "Reader",
                    tint = toolbarContent
                )
            }
            IconButton(onClick = onHome) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = toolbarContent
                )
            }
        }
        HorizontalDivider(color = if (darkUi) Color(0xFF2A2A2A) else Color(0xFFE2E0D8))
    }
}

@Composable
private fun BrowserProgressBar(
    isLoading: Boolean,
    progress: Float
) {
    if (isLoading) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    } else {
        HorizontalDivider(
            modifier = Modifier.height(1.dp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun BrowserIssuePage(
    issue: BrowserIssue,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
    onOpenExternally: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = issue.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onGoBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Go back")
                    }
                    if (issue is BrowserIssue.Blocked) {
                        Button(
                            onClick = { onOpenExternally(issue.url) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Open externally")
                        }
                    } else {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
    }
}

private sealed class BrowserIssue(
    val title: String,
    val message: String
) {
    class Blocked(val url: String) : BrowserIssue(
        title = "External site blocked for safety",
        message = "This link leaves the trusted source library."
    )

    class LoadFailure(
        title: String,
        message: String
    ) : BrowserIssue(title, message) {
        companion object {
            fun from(error: WebRequestError): LoadFailure {
                return when {
                    error.code == WebRequestError.ERROR_OFFLINE ||
                        error.code == WebRequestError.ERROR_UNKNOWN_HOST ||
                        error.category == WebRequestError.ERROR_CATEGORY_NETWORK -> {
                        LoadFailure(
                            title = "No internet connection",
                            message = "Check your connection and try again."
                        )
                    }

                    error.code == WebRequestError.ERROR_SECURITY_SSL ||
                        error.code == WebRequestError.ERROR_SECURITY_BAD_CERT ||
                        error.category == WebRequestError.ERROR_CATEGORY_SECURITY -> {
                        LoadFailure(
                            title = "Secure connection failed",
                            message = "The source could not be opened because the secure connection failed."
                        )
                    }

                    else -> {
                        LoadFailure(
                            title = "Page could not load",
                            message = "The source is unavailable right now. Try again in a moment."
                        )
                    }
                }
            }
        }
    }
}

private fun NewsSource.matchesCategory(category: String): Boolean {
    return when (category) {
        "International" -> this.category.startsWith("International")
        else -> this.category == category
    }
}

private fun normalizedHost(url: String): String? {
    return runCatching {
        Uri.parse(url).host
            ?.lowercase()
            ?.removePrefix("www.")
    }.getOrNull()
}

private fun isAllowedTopLevelUrl(url: String, allowedHosts: Set<String>): Boolean {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase()
    if (scheme == "about") return true
    if (scheme != "http" && scheme != "https") return false
    val host = parsed.host?.lowercase()?.removePrefix("www.") ?: return false
    return allowedHosts.any { allowedHost ->
        host == allowedHost || host.endsWith(".$allowedHost")
    }
}

private fun isAllowedOfflineReaderUrl(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
    return scheme == "file" || scheme == "about"
}

private fun isAllowedBrowserReaderUrl(url: String): Boolean {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return parsed.scheme?.lowercase() == "file" &&
        parsed.path?.contains("/reader_cache/") == true
}

private fun NewsSource.domainLabel(): String {
    return normalizedHost(url) ?: url.removePrefix("https://").removePrefix("http://")
}

private fun NewsSource.initials(): String {
    return initialsForName(name)
}

private fun initialsForName(name: String): String {
    return name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { name.take(1).uppercase() }
}

private fun shortSavedAt(savedAt: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(savedAt))
}

private val HomeCategories = listOf(
    "Bangla",
    "English",
    "Portal",
    "Live",
    "Sports",
    "Business",
    "International",
    "Tech"
)

private val SourceFilterCategories = listOf("All") + HomeCategories + listOf("Agency")
