package mahin.studio.khoborakhobor

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mahin.studio.khoborakhobor.ui.theme.KhoborakhoborTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class NativeTab(
    val label: String,
    val menuId: Int,
    val tag: String
) {
    Home("Home", R.id.nav_home, "tab_home"),
    Sources("Sources", R.id.nav_sources, "tab_sources"),
    Offline("Offline", R.id.nav_offline, "tab_offline"),
    Favorites("Favorites", R.id.nav_favorites, "tab_favorites"),
    Settings("Settings", R.id.nav_settings, "tab_settings");

    companion object {
        fun fromMenuId(menuId: Int): NativeTab? = entries.firstOrNull { it.menuId == menuId }
    }
}

internal interface NativeStateConsumer {
    fun render(state: AppUiState, darkTheme: Boolean)
}

internal class HomeNativeFragment : SourceRecyclerFragment() {
    override val screenName: String = "Home"

    override fun buildItems(state: AppUiState): List<SourceListItem> {
        if (!state.sourcesLoaded) return emptyList()
        val popular = state.sources.filter { it.country == "BD" && it.type == "newspaper" }.take(10)
        val popularIds = popular.map { it.id }.toSet()
        val english = state.sources
            .filter { (it.category == "English" || it.language == "en") && it.id !in popularIds }
            .take(8)
        val featuredIds = popularIds + english.map { it.id }
        val international = state.sources
            .filter { it.category.startsWith("International") && it.id !in featuredIds }
            .take(8)
        return buildList {
            add(SourceListItem.HomeHeader(state.sources.size, HOME_CATEGORIES.size, state.disableAds, state.adBlockState.status))
            add(SourceListItem.Section("Popular in Bangladesh", popular.size))
            addAll(popular.map { SourceListItem.SourceCard(it, it.id in state.favoriteIds) })
            add(SourceListItem.Section("English picks", english.size))
            addAll(english.map { SourceListItem.SourceCard(it, it.id in state.favoriteIds) })
            add(SourceListItem.Section("International picks", international.size))
            addAll(international.map { SourceListItem.SourceCard(it, it.id in state.favoriteIds) })
        }
    }
}

internal class SourcesNativeFragment : SourceRecyclerFragment() {
    override val screenName: String = "Sources"
    private var query: String = ""

    override fun buildItems(state: AppUiState): List<SourceListItem> {
        if (!state.sourcesLoaded) return emptyList()
        val visibleSources = state.sources.filter { source ->
            val matchesQuery = query.isBlank() ||
                source.name.contains(query, ignoreCase = true) ||
                source.category.contains(query, ignoreCase = true) ||
                source.type.contains(query, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || source.nativeMatchesCategory(selectedCategory)
            matchesQuery && matchesCategory
        }
        return buildList {
            add(SourceListItem.Search(query))
            add(SourceListItem.FilterRow(SOURCE_FILTER_CATEGORIES, selectedCategory))
            if (visibleSources.isEmpty()) {
                add(SourceListItem.Empty("No sources match this search."))
            } else {
                addAll(visibleSources.map { SourceListItem.SourceCard(it, it.id in state.favoriteIds) })
            }
        }
    }

    override fun onSearchChanged(value: String) {
        if (query == value) return
        query = value
        render(lastState, lastDarkTheme)
    }
}

internal class FavoritesNativeFragment : SourceRecyclerFragment() {
    override val screenName: String = "Favorites"

    override fun buildItems(state: AppUiState): List<SourceListItem> {
        if (!state.sourcesLoaded) return emptyList()
        val favorites = state.sources.filter { it.id in state.favoriteIds }
        return if (favorites.isEmpty()) {
            listOf(SourceListItem.Empty("Favorite sources will appear here."))
        } else {
            favorites.map { SourceListItem.SourceCard(it, isFavorite = true) }
        }
    }
}

internal abstract class SourceRecyclerFragment : Fragment(R.layout.fragment_recycler), NativeStateConsumer {
    protected abstract val screenName: String
    protected var selectedCategory: String = "All"
    protected var lastState = AppUiState()
    protected var lastDarkTheme = false
    private var firstRenderLogged = false
    private lateinit var fragmentRoot: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingText: TextView
    private lateinit var adapter: SourceListAdapter

    abstract fun buildItems(state: AppUiState): List<SourceListItem>
    open fun onSearchChanged(value: String) = Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentRoot = view.findViewById(R.id.fragmentRoot)
        recyclerView = view.findViewById(R.id.recyclerView)
        loadingText = view.findViewById(R.id.loadingText)
        adapter = SourceListAdapter(
            iconCacheManager = (requireActivity() as MainActivity).sourceIconCacheManager,
            onOpenSource = { (requireActivity() as MainActivity).onOpenSource(it) },
            onToggleFavorite = { (requireActivity() as MainActivity).onToggleFavorite(it) },
            onCategorySelected = {
                selectedCategory = it
                (requireActivity() as MainActivity).setJankState("selectedCategory", it)
                render(lastState, lastDarkTheme)
            },
            onSearchChanged = { onSearchChanged(it) },
            darkThemeProvider = { lastDarkTheme }
        )
        adapter.setHasStableIds(true)
        val layoutManager = GridLayoutManager(requireContext(), 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.currentList.getOrNull(position) is SourceListItem.SourceCard) 1 else 2
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(12)
        recyclerView.setPadding(dp(16), dp(6), dp(16), dp(14))
        recyclerView.addItemDecoration(GridSpacingDecoration(dp(12)))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (requireActivity() as MainActivity).sourceIconCacheManager.iconUpdates.collectLatest { sourceId ->
                    adapter.notifySourceIconChanged(sourceId)
                }
            }
        }
    }

    override fun render(state: AppUiState, darkTheme: Boolean) {
        if (!::adapter.isInitialized) return
        val themeChanged = lastDarkTheme != darkTheme
        lastState = state
        lastDarkTheme = darkTheme
        val items = buildItems(state)
        fragmentRoot.setBackgroundColor(nativeBackground(darkTheme))
        recyclerView.setBackgroundColor(nativeBackground(darkTheme))
        loadingText.setTextColor(nativeMuted(darkTheme))
        loadingText.visibility = if (!state.sourcesLoaded && items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty() && !state.sourcesLoaded) View.GONE else View.VISIBLE
        adapter.submitList(items) {
            if (themeChanged) {
                adapter.notifyDataSetChanged()
            }
            if (!firstRenderLogged && state.sourcesLoaded) {
                firstRenderLogged = true
                recyclerView.doOnPreDraw {
                    PerformanceLogger.mark("$screenName first render")
                }
            }
        }
    }
}

internal class OfflineNativeFragment : Fragment(R.layout.fragment_recycler), NativeStateConsumer {
    private var firstRenderLogged = false
    private var lastDarkTheme = false
    private lateinit var fragmentRoot: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingText: TextView
    private lateinit var adapter: OfflinePageAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentRoot = view.findViewById(R.id.fragmentRoot)
        recyclerView = view.findViewById(R.id.recyclerView)
        loadingText = view.findViewById(R.id.loadingText)
        adapter = OfflinePageAdapter(
            onOpen = { (requireActivity() as MainActivity).onOpenOfflinePage(it) },
            onDelete = { (requireActivity() as MainActivity).onDeleteOfflinePage(it) },
            darkThemeProvider = { (requireActivity() as MainActivity).isDarkTheme() }
        )
        adapter.setHasStableIds(true)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.setHasFixedSize(true)
        recyclerView.setPadding(dp(16), dp(6), dp(16), dp(14))
        recyclerView.addItemDecoration(LinearSpacingDecoration(dp(12)))
    }

    override fun render(state: AppUiState, darkTheme: Boolean) {
        if (!::adapter.isInitialized) return
        val themeChanged = lastDarkTheme != darkTheme
        lastDarkTheme = darkTheme
        fragmentRoot.setBackgroundColor(nativeBackground(darkTheme))
        recyclerView.setBackgroundColor(nativeBackground(darkTheme))
        loadingText.setTextColor(nativeMuted(darkTheme))
        loadingText.visibility = if (!state.offlineLoaded) View.VISIBLE else View.GONE
        recyclerView.visibility = if (state.offlineLoaded) View.VISIBLE else View.GONE
        val rows = if (state.offlinePages.isEmpty() && state.offlineLoaded) {
            listOf(OfflineListItem.Empty("No offline pages yet"))
        } else {
            state.offlinePages.map { OfflineListItem.Page(it) }
        }
        adapter.submitList(rows) {
            if (themeChanged) {
                adapter.notifyDataSetChanged()
            }
            if (!firstRenderLogged && state.offlineLoaded) {
                firstRenderLogged = true
                recyclerView.doOnPreDraw {
                    PerformanceLogger.mark("Offline first render")
                }
            }
        }
    }
}

internal class SettingsNativeFragment : Fragment(R.layout.fragment_settings), NativeStateConsumer {
    private lateinit var scrollRoot: View
    private lateinit var container: LinearLayout
    private var built = false
    private val createdViews = mutableMapOf<String, View>()
    private val panelViews = mutableListOf<LinearLayout>()
    private val themeOptionKeys = setOf("themeSystem", "themeLight", "themeDark")
    private val statusTextKeys = setOf("adBlockText", "readerDarkText")
    private val actionButtonKeys = setOf("clearCache")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scrollRoot = view.findViewById(R.id.settingsScroll)
        container = view.findViewById(R.id.settingsContainer)
    }

    override fun render(state: AppUiState, darkTheme: Boolean) {
        if (!::container.isInitialized) return
        if (!built) {
            built = true
            buildSettings()
            view?.doOnPreDraw { PerformanceLogger.mark("Settings first render") }
        }
        (createdViews["themeSystem"] as TextView).isSelected = state.themePreference == ThemePreference.System
        (createdViews["themeLight"] as TextView).isSelected = state.themePreference == ThemePreference.Light
        (createdViews["themeDark"] as TextView).isSelected = state.themePreference == ThemePreference.Dark
        setSwitchChecked("disableAds", state.disableAds)
        setSwitchChecked("websiteDark", state.websiteDarkMode)
        (createdViews["adBlockText"] as TextView).text = nativeAdBlockStatusText(state.disableAds, state.adBlockState)
        (createdViews["readerDarkText"] as TextView).text = if (state.websiteDarkMode) "On" else "Off"
        createdViews.values.forEach { it.refreshDrawableState() }
        applySettingsColors(darkTheme)
    }

    private fun buildSettings() {
        addPanel("Appearance").also { panel ->
            panel.addView(themeSelector())
        }
        addPanel("Reading").also { panel ->
            panel.addView(settingsSwitch("Disable Ads", "adBlockText", "disableAds") {
                (requireActivity() as MainActivity).onDisableAdsChange(it)
            })
            panel.addView(settingsDivider())
            panel.addView(settingsSwitch("Webpage dark mode", "readerDarkText", "websiteDark") {
                (requireActivity() as MainActivity).onWebsiteDarkModeChange(it)
            })
        }
        addPanel("Storage").also { panel ->
            panel.addView(settingsActionButton("Clear browser cache", "clearCache") {
                (requireActivity() as MainActivity).onClearCache { ok ->
                    Toast.makeText(
                        requireContext(),
                        if (ok) "Cache cleared" else "Could not clear cache",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun addPanel(title: String): LinearLayout {
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        panel.addView(TextView(requireContext()).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, 0, dp(12))
        })
        container.addView(panel)
        panelViews.add(panel)
        return panel
    }

    private fun themeSelector(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            minimumHeight = dp(40)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            )
            createdViews["themeGroup"] = this
            addView(themeSegment("System", "themeSystem", ThemePreference.System), segmentParams(hasEndMargin = true))
            addView(themeSegment("Light", "themeLight", ThemePreference.Light), segmentParams(hasEndMargin = true))
            addView(themeSegment("Dark", "themeDark", ThemePreference.Dark), segmentParams(hasEndMargin = false))
        }
    }

    private fun themeSegment(text: String, key: String, preference: ThemePreference): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener {
                (requireActivity() as MainActivity).onThemePreferenceChange(preference)
            }
            applySelectableForeground()
            createdViews[key] = this
        }
    }

    private fun segmentParams(hasEndMargin: Boolean): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            if (hasEndMargin) marginEnd = dp(3)
        }
    }

    private fun settingsSwitch(
        title: String,
        subtitleKey: String,
        switchKey: String,
        onChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(0, dp(8), 0, dp(8))
        }
        val textColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(requireContext()).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        textColumn.addView(TextView(requireContext()).apply {
            textSize = 13f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
            createdViews[subtitleKey] = this
        })
        val switch = SwitchMaterial(requireContext()).apply {
            showText = false
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            createdViews[switchKey] = this
        }
        row.addView(textColumn)
        row.addView(switch)
        return row
    }

    private fun settingsActionButton(text: String, key: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(42)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { onClick() }
            applySelectableForeground()
            createdViews[key] = this
        }
    }

    private fun settingsDivider(): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        }
    }

    private fun applySettingsColors(darkTheme: Boolean) {
        val palette = nativePalette(darkTheme)
        scrollRoot.setBackgroundColor(palette.appBackground)
        container.setBackgroundColor(palette.appBackground)
        panelViews.forEach { panel ->
            panel.background = roundedDrawable(palette.cardBackground, palette.cardBorder, dp(18))
            setTextColors(panel, palette.primaryText, palette.secondaryText)
        }
        createdViews["themeGroup"]?.background = roundedDrawable(palette.surfaceAlt, palette.cardBorder, dp(16))
        themeOptionKeys.forEach { key ->
            val textView = createdViews[key] as TextView
            val selected = textView.isSelected
            textView.background = roundedDrawable(
                fill = if (selected) palette.chipSelectedBg else ThemePalette.TRANSPARENT,
                stroke = ThemePalette.TRANSPARENT,
                radius = dp(13)
            )
            textView.setTextColor(if (selected) palette.chipSelectedText else palette.primaryText)
        }
        statusTextKeys.forEach { key ->
            (createdViews[key] as TextView).apply {
                this.background = null
                setTextColor(palette.secondaryText)
            }
        }
        actionButtonKeys.forEach { key ->
            (createdViews[key] as TextView).apply {
                this.background = roundedDrawable(palette.outlinedButtonBg, palette.outlinedButtonBorder, dp(14))
                setTextColor(palette.outlinedButtonText)
            }
        }
        applySwitchColors(createdViews["disableAds"] as SwitchMaterial, darkTheme)
        applySwitchColors(createdViews["websiteDark"] as SwitchMaterial, darkTheme)
        colorDividers(container, palette.cardBorder)
    }

    private fun setSwitchChecked(key: String, checked: Boolean) {
        val switch = createdViews[key] as SwitchMaterial
        if (switch.isChecked != checked) switch.isChecked = checked
    }

    private fun applySwitchColors(switch: SwitchMaterial, darkTheme: Boolean) {
        val palette = nativePalette(darkTheme)
        switch.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.buttonText, palette.switchOffThumb)
        )
        switch.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.buttonBg, palette.switchOffTrack)
        )
    }
}

internal class BrowserComposeFragment : Fragment() {
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as MainActivity
        val sourceId = requireArguments().getString(ARG_SOURCE_ID).orEmpty()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsState()
                val source = state.sources.firstOrNull { it.id == sourceId }
                KhoborakhoborTheme(darkTheme = host.isDarkTheme()) {
                    if (source == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading")
                        }
                    } else {
                        BrowserScreen(
                            source = source,
                            allSources = state.sources,
                            getGeckoRuntime = { host.runtimeWithExtensions() },
                            websiteDarkMode = state.websiteDarkMode,
                            onWebsiteDarkModeChange = { host.onWebsiteDarkModeChange(it) },
                            onAttachLiveDarkOverlay = { session, enabled -> host.attachLiveDarkOverlay(session, enabled) },
                            onSetLiveDarkOverlay = { session, enabled, onFallbackReload ->
                                host.setLiveDarkOverlay(session, enabled, onFallbackReload)
                            },
                            onDetachLiveDarkOverlay = { session -> host.detachLiveDarkOverlay(session) },
                            onSaveOfflinePage = { saveSource, url ->
                                host.saveOfflinePage(saveSource, url)
                            },
                            onClose = { host.closeOverlay() },
                            onHome = { host.closeOverlay() },
                            onJankStateChange = { key, value -> host.setJankState(key, value) }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "browser_compose"
        private const val ARG_SOURCE_ID = "source_id"
        fun newInstance(sourceId: String): BrowserComposeFragment {
            return BrowserComposeFragment().apply {
                arguments = Bundle().apply { putString(ARG_SOURCE_ID, sourceId) }
            }
        }
    }
}

internal class OfflineReaderComposeFragment : Fragment() {
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as MainActivity
        val pageId = requireArguments().getString(ARG_PAGE_ID).orEmpty()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsState()
                val page = state.offlinePages.firstOrNull { it.id == pageId }
                KhoborakhoborTheme(darkTheme = host.isDarkTheme()) {
                    if (page == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Offline page is unavailable.")
                        }
                    } else {
                        OfflineReaderScreen(
                            page = page,
                            getGeckoRuntime = { host.runtimeOnly() },
                            readerDarkMode = state.websiteDarkMode,
                            onReaderDarkModeChange = { host.onWebsiteDarkModeChange(it) },
                            onBuildOfflineReaderPage = { offlinePage, darkMode ->
                                host.buildOfflineReaderPage(offlinePage, darkMode)
                            },
                            onClose = { host.closeOverlay() },
                            onJankStateChange = { key, value -> host.setJankState(key, value) }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "offline_reader_compose"
        private const val ARG_PAGE_ID = "page_id"
        fun newInstance(pageId: String): OfflineReaderComposeFragment {
            return OfflineReaderComposeFragment().apply {
                arguments = Bundle().apply { putString(ARG_PAGE_ID, pageId) }
            }
        }
    }
}

internal sealed class SourceListItem {
    abstract val stableId: Long

    data class HomeHeader(
        val sourceCount: Int,
        val categoryCount: Int,
        val disableAds: Boolean,
        val blockerStatus: UBlockStatus
    ) : SourceListItem() {
        override val stableId: Long = stableLong("home_header")
    }

    data class Search(val query: String) : SourceListItem() {
        override val stableId: Long = stableLong("search")
    }

    data class FilterRow(val categories: List<String>, val selected: String) : SourceListItem() {
        override val stableId: Long = stableLong("filters")
    }

    data class Section(val title: String, val count: Int) : SourceListItem() {
        override val stableId: Long = stableLong("section_$title")
    }

    data class SourceCard(val source: NewsSource, val isFavorite: Boolean) : SourceListItem() {
        override val stableId: Long = stableLong(source.id)
    }

    data class Empty(val message: String) : SourceListItem() {
        override val stableId: Long = stableLong("empty_$message")
    }
}

private class SourceListAdapter(
    private val iconCacheManager: SourceIconCacheManager,
    private val onOpenSource: (NewsSource) -> Unit,
    private val onToggleFavorite: (NewsSource) -> Unit,
    private val onCategorySelected: (String) -> Unit,
    private val onSearchChanged: (String) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : ListAdapter<SourceListItem, RecyclerView.ViewHolder>(SOURCE_DIFF) {
    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SourceListItem.HomeHeader -> VIEW_HOME_HEADER
            is SourceListItem.Search -> VIEW_SEARCH
            is SourceListItem.FilterRow -> VIEW_FILTERS
            is SourceListItem.Section -> VIEW_SECTION
            is SourceListItem.SourceCard -> VIEW_SOURCE
            is SourceListItem.Empty -> VIEW_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HOME_HEADER -> HomeHeaderHolder(inflater.inflate(R.layout.item_home_header, parent, false), darkThemeProvider)
            VIEW_SEARCH -> SearchHolder(inflater.inflate(R.layout.item_source_search, parent, false), onSearchChanged, darkThemeProvider)
            VIEW_FILTERS -> FilterHolder(inflater.inflate(R.layout.item_filter_row, parent, false), onCategorySelected, darkThemeProvider)
            VIEW_SECTION -> SectionHolder(inflater.inflate(R.layout.item_section_header, parent, false), darkThemeProvider)
            VIEW_SOURCE -> SourceCardHolder(
                inflater.inflate(R.layout.item_source_card, parent, false),
                iconCacheManager,
                onOpenSource,
                onToggleFavorite,
                darkThemeProvider
            )
            else -> EmptyHolder(inflater.inflate(R.layout.item_empty_state, parent, false), darkThemeProvider)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bind(holder, position, emptyList())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        bind(holder, position, payloads)
    }

    fun notifySourceIconChanged(sourceId: String) {
        currentList.forEachIndexed { index, item ->
            if (item is SourceListItem.SourceCard && item.source.id == sourceId) {
                notifyItemChanged(index, PAYLOAD_ICON)
            }
        }
    }

    private fun bind(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        val start = SystemClock.elapsedRealtime()
        when (val item = getItem(position)) {
            is SourceListItem.HomeHeader -> (holder as HomeHeaderHolder).bind(item)
            is SourceListItem.Search -> (holder as SearchHolder).bind(item)
            is SourceListItem.FilterRow -> (holder as FilterHolder).bind(item)
            is SourceListItem.Section -> (holder as SectionHolder).bind(item)
            is SourceListItem.SourceCard -> (holder as SourceCardHolder).bind(item, payloads.contains(PAYLOAD_ICON))
            is SourceListItem.Empty -> (holder as EmptyHolder).bind(item.message)
        }
        BindSampler.log("Source adapter bind", start)
    }

    private companion object {
        const val VIEW_HOME_HEADER = 1
        const val VIEW_SEARCH = 2
        const val VIEW_FILTERS = 3
        const val VIEW_SECTION = 4
        const val VIEW_SOURCE = 5
        const val VIEW_EMPTY = 6
        const val PAYLOAD_ICON = "icon"
    }
}

private class HomeHeaderHolder(view: View, private val darkThemeProvider: () -> Boolean) : RecyclerView.ViewHolder(view) {
    private val root: View = view.findViewById(R.id.homeHeaderRoot)
    private val title: TextView = view.findViewById(R.id.homeHeaderTitle)
    private val total: TextView = view.findViewById(R.id.homeTotalSources)
    private val categories: TextView = view.findViewById(R.id.homeCategoryCount)
    private val blocking: TextView = view.findViewById(R.id.homeBlockingState)

    fun bind(item: SourceListItem.HomeHeader) {
        val palette = nativePalette(darkThemeProvider())
        root.background = roundedDrawable(palette.cardBackground, palette.cardBorder, dp(16))
        title.setTextColor(palette.primaryText)
        total.text = "${item.sourceCount} sources"
        categories.text = "${item.categoryCount} categories"
        val active = item.disableAds && item.blockerStatus != UBlockStatus.DISABLED
        blocking.text = "Blocking ${if (active) "On" else "Off"}"
        listOf(total, categories, blocking).forEach {
            it.background = roundedDrawable(palette.surfaceAlt, palette.cardBorder, dp(17))
            it.setTextColor(palette.secondaryText)
        }
    }
}

private class SearchHolder(
    view: View,
    private val onSearchChanged: (String) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : RecyclerView.ViewHolder(view) {
    private val editText = view as EditText
    private var watcher: TextWatcher? = null

    fun bind(item: SourceListItem.Search) {
        val palette = nativePalette(darkThemeProvider())
        editText.background = roundedDrawable(palette.cardBackground, palette.cardBorder, dp(16))
        editText.setTextColor(palette.primaryText)
        editText.setHintTextColor(palette.secondaryText)
        watcher?.let { editText.removeTextChangedListener(it) }
        if (editText.text.toString() != item.query) {
            editText.setText(item.query)
            editText.setSelection(editText.text.length)
        }
        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onSearchChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }.also { editText.addTextChangedListener(it) }
    }
}

private class FilterHolder(
    view: View,
    private val onCategorySelected: (String) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : RecyclerView.ViewHolder(view) {
    private val container: LinearLayout = view.findViewById(R.id.filterContainer)

    fun bind(item: SourceListItem.FilterRow) {
        val palette = nativePalette(darkThemeProvider())
        container.removeAllViews()
        item.categories.forEach { category ->
            val chip = TextView(container.context).apply {
                text = category
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                minHeight = dp(34)
                setPadding(dp(13), 0, dp(13), 0)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val selected = category == item.selected
                setTextColor(if (selected) palette.chipSelectedText else palette.chipUnselectedText)
                background = roundedDrawable(
                    fill = if (selected) palette.chipSelectedBg else palette.chipUnselectedBg,
                    stroke = palette.cardBorder,
                    radius = dp(18)
                )
                applySelectableForeground()
                setOnClickListener { onCategorySelected(category) }
            }
            container.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply { marginEnd = dp(8) })
        }
    }
}

private class SectionHolder(view: View, private val darkThemeProvider: () -> Boolean) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.sectionTitle)
    private val count: TextView = view.findViewById(R.id.sectionCount)

    fun bind(item: SourceListItem.Section) {
        title.text = item.title
        count.text = item.count.toString()
        title.setTextColor(nativeText(darkThemeProvider()))
        count.setTextColor(nativeMuted(darkThemeProvider()))
    }
}

private class SourceCardHolder(
    view: View,
    private val iconCacheManager: SourceIconCacheManager,
    private val onOpenSource: (NewsSource) -> Unit,
    private val onToggleFavorite: (NewsSource) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : RecyclerView.ViewHolder(view) {
    private val root: LinearLayout = view.findViewById(R.id.sourceCardRoot)
    private val initials: TextView = view.findViewById(R.id.sourceInitials)
    private val icon: ImageView = view.findViewById(R.id.sourceIcon)
    private val name: TextView = view.findViewById(R.id.sourceName)
    private val meta: TextView = view.findViewById(R.id.sourceMeta)
    private val favorite: TextView = view.findViewById(R.id.favoriteButton)

    fun bind(item: SourceListItem.SourceCard, iconOnly: Boolean) {
        val source = item.source
        if (!iconOnly) {
            bindSourceCard(
                root = root,
                initials = initials,
                name = name,
                meta = meta,
                favorite = favorite,
                source = source,
                isSaved = item.isFavorite,
                palette = nativePalette(darkThemeProvider()),
                onOpenSource = onOpenSource,
                onToggleFavorite = onToggleFavorite,
                radius = dp(16),
                iconRadius = dp(11),
                buttonRadius = dp(17)
            )
        }
        val state = iconCacheManager.stateFor(source).value
        val ready = state as? SourceIconState.Ready
        if (ready?.bitmap != null) {
            icon.setImageBitmap(ready.bitmap)
            icon.visibility = View.VISIBLE
        } else {
            icon.setImageDrawable(null)
            icon.visibility = View.GONE
            iconCacheManager.request(source)
        }
    }
}

private fun bindSourceCard(
    root: LinearLayout,
    initials: TextView,
    name: TextView,
    meta: TextView,
    favorite: TextView,
    source: NewsSource,
    isSaved: Boolean,
    palette: ThemePalette,
    onOpenSource: (NewsSource) -> Unit,
    onToggleFavorite: (NewsSource) -> Unit,
    radius: Int,
    iconRadius: Int,
    buttonRadius: Int
) {
    root.background = roundedDrawable(palette.cardBackground, palette.cardBorder, radius)
    root.setOnClickListener { onOpenSource(source) }
    initials.text = nativeInitials(source.name)
    initials.background = roundedDrawable(palette.surfaceAlt, palette.cardBorder, iconRadius)
    initials.setTextColor(palette.primaryText)
    name.text = source.name
    name.setTextColor(palette.primaryText)
    meta.text = "${source.category} | ${source.type}"
    meta.setTextColor(palette.secondaryText)
    favorite.text = if (isSaved) "Saved" else "Save"
    favorite.background = roundedDrawable(
        fill = if (isSaved) palette.buttonBg else palette.outlinedButtonBg,
        stroke = if (isSaved) palette.buttonBorder else palette.outlinedButtonBorder,
        radius = buttonRadius
    )
    favorite.setTextColor(if (isSaved) palette.buttonText else palette.outlinedButtonText)
    favorite.setOnClickListener { onToggleFavorite(source) }
}

private class EmptyHolder(view: View, private val darkThemeProvider: () -> Boolean) : RecyclerView.ViewHolder(view) {
    private val textView = view as TextView

    fun bind(message: String) {
        val dark = darkThemeProvider()
        textView.text = message
        textView.setTextColor(nativeMuted(dark))
        textView.background = roundedDrawable(nativeSurface(dark), nativeDivider(dark), dp(16))
    }
}

private sealed class OfflineListItem {
    abstract val stableId: Long
    data class Page(val page: OfflinePage) : OfflineListItem() {
        override val stableId: Long = stableLong(page.id)
    }
    data class Empty(val message: String) : OfflineListItem() {
        override val stableId: Long = stableLong("offline_empty")
    }
}

private class OfflinePageAdapter(
    private val onOpen: (OfflinePage) -> Unit,
    private val onDelete: (OfflinePage) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : ListAdapter<OfflineListItem, RecyclerView.ViewHolder>(OFFLINE_DIFF) {
    override fun getItemId(position: Int): Long = getItem(position).stableId
    override fun getItemViewType(position: Int): Int = if (getItem(position) is OfflineListItem.Page) 1 else 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            OfflinePageHolder(inflater.inflate(R.layout.item_offline_page, parent, false), onOpen, onDelete, darkThemeProvider)
        } else {
            EmptyHolder(inflater.inflate(R.layout.item_empty_state, parent, false), darkThemeProvider)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val start = SystemClock.elapsedRealtime()
        when (val item = getItem(position)) {
            is OfflineListItem.Page -> (holder as OfflinePageHolder).bind(item.page)
            is OfflineListItem.Empty -> (holder as EmptyHolder).bind(item.message)
        }
        BindSampler.log("Offline adapter bind", start)
    }
}

private class OfflinePageHolder(
    view: View,
    private val onOpen: (OfflinePage) -> Unit,
    private val onDelete: (OfflinePage) -> Unit,
    private val darkThemeProvider: () -> Boolean
) : RecyclerView.ViewHolder(view) {
    private val root: LinearLayout = view.findViewById(R.id.offlineRoot)
    private val initials: TextView = view.findViewById(R.id.offlineInitials)
    private val title: TextView = view.findViewById(R.id.offlineTitle)
    private val source: TextView = view.findViewById(R.id.offlineSource)
    private val date: TextView = view.findViewById(R.id.offlineDate)
    private val delete: ImageButton = view.findViewById(R.id.deleteButton)

    fun bind(page: OfflinePage) {
        val palette = nativePalette(darkThemeProvider())
        root.background = roundedDrawable(palette.cardBackground, palette.cardBorder, dp(16))
        root.setOnClickListener { onOpen(page) }
        initials.text = nativeInitials(page.sourceName)
        initials.background = roundedDrawable(palette.surfaceAlt, palette.cardBorder, dp(12))
        initials.setTextColor(palette.primaryText)
        title.text = page.title
        title.setTextColor(palette.primaryText)
        source.text = page.sourceName
        source.setTextColor(palette.secondaryText)
        date.text = nativeShortSavedAt(page.savedAt)
        date.setTextColor(palette.secondaryText)
        delete.imageTintList = ColorStateList.valueOf(palette.secondaryText)
        delete.setOnClickListener { onDelete(page) }
    }
}

private class GridSpacingDecoration(
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val spanCount = 2
        val layoutParams = view.layoutParams as GridLayoutManager.LayoutParams
        if (layoutParams.spanSize == spanCount) {
            outRect.set(0, spacing / 2, 0, spacing / 2)
        } else {
            val column = layoutParams.spanIndex
            outRect.left = if (column == 0) 0 else spacing / 2
            outRect.right = if (column == 0) spacing / 2 else 0
            outRect.top = spacing / 2
            outRect.bottom = spacing / 2
        }
    }
}

private class LinearSpacingDecoration(
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, spacing / 2, 0, spacing / 2)
    }
}

private object BindSampler {
    private var logs = 0
    fun log(label: String, startMillis: Long) {
        val duration = SystemClock.elapsedRealtime() - startMillis
        if (duration >= 8L && logs < 12) {
            logs += 1
            PerformanceLogger.mark("$label ${duration}ms")
        }
    }
}

private val SOURCE_DIFF = object : DiffUtil.ItemCallback<SourceListItem>() {
    override fun areItemsTheSame(oldItem: SourceListItem, newItem: SourceListItem): Boolean {
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: SourceListItem, newItem: SourceListItem): Boolean {
        return oldItem == newItem
    }
}

private val OFFLINE_DIFF = object : DiffUtil.ItemCallback<OfflineListItem>() {
    override fun areItemsTheSame(oldItem: OfflineListItem, newItem: OfflineListItem): Boolean {
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: OfflineListItem, newItem: OfflineListItem): Boolean {
        return oldItem == newItem
    }
}

private fun setTextColors(view: View, text: Int, muted: Int) {
    when (view) {
        is TextView -> view.setTextColor(if (view.typeface?.isBold == true) text else muted)
        is ViewGroup -> {
            for (index in 0 until view.childCount) {
                setTextColors(view.getChildAt(index), text, muted)
            }
        }
    }
}

private fun colorDividers(view: View, color: Int) {
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            colorDividers(view.getChildAt(index), color)
        }
    } else if (view.layoutParams?.height == 1) {
        view.setBackgroundColor(color)
    }
}

private fun View.applySelectableForeground() {
    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
    val typedArray = context.obtainStyledAttributes(attrs)
    foreground = try {
        typedArray.getDrawable(0)
    } finally {
        typedArray.recycle()
    }
}

private fun roundedDrawable(fill: Int, stroke: Int, radius: Int): GradientDrawable {
    return GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(1, stroke)
    }
}

private fun nativePalette(dark: Boolean): ThemePalette = ThemeManager.palette(dark)
private fun nativeBackground(dark: Boolean): Int = nativePalette(dark).appBackground
private fun nativeSurface(dark: Boolean): Int = nativePalette(dark).cardBackground
private fun nativeSurfaceAlt(dark: Boolean): Int = nativePalette(dark).surfaceAlt
private fun nativeText(dark: Boolean): Int = nativePalette(dark).primaryText
private fun nativeMuted(dark: Boolean): Int = nativePalette(dark).secondaryText
private fun nativeDivider(dark: Boolean): Int = nativePalette(dark).cardBorder
private fun nativeSubtleIcon(dark: Boolean): Int = nativePalette(dark).subtleIcon
private fun nativeSwitchOffTrack(dark: Boolean): Int = nativePalette(dark).switchOffTrack
private fun nativeSwitchOffThumb(dark: Boolean): Int = nativePalette(dark).switchOffThumb
private fun nativeButtonBg(dark: Boolean): Int = nativePalette(dark).buttonBg
private fun nativeButtonText(dark: Boolean): Int = nativePalette(dark).buttonText
private fun nativeOutlinedButtonBg(dark: Boolean): Int = nativePalette(dark).outlinedButtonBg
private fun nativeOutlinedButtonText(dark: Boolean): Int = nativePalette(dark).outlinedButtonText
private fun nativeOutlinedButtonBorder(dark: Boolean): Int = nativePalette(dark).outlinedButtonBorder
private fun nativeControlTextOnPrimary(dark: Boolean): Int = nativeButtonText(dark)

private fun NewsSource.nativeMatchesCategory(category: String): Boolean {
    return when (category) {
        "International" -> this.category.startsWith("International")
        else -> this.category == category
    }
}

private fun nativeInitials(name: String): String {
    return name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { name.take(1).uppercase() }
}

private fun nativeShortSavedAt(savedAt: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(savedAt))
}

private fun nativeAdBlockStatusText(disableAds: Boolean, adBlockState: AdBlockState): String {
    return when {
        !disableAds -> "Off"
        adBlockState.status == UBlockStatus.ACTIVE -> "uBlock active"
        adBlockState.status == UBlockStatus.FALLBACK_ACTIVE -> "Privacy blocking active"
        adBlockState.status == UBlockStatus.LOADING -> if (GeckoRuntimeProvider.isCreated()) "Loading..." else "uBlock active"
        else -> "Off"
    }
}

private fun stableLong(value: String): Long = value.hashCode().toLong() and 0x00000000ffffffffL

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
private fun Fragment.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
private fun RecyclerView.ViewHolder.dp(value: Int): Int = (value * itemView.resources.displayMetrics.density).toInt()

private val HOME_CATEGORIES = listOf(
    "Bangla",
    "English",
    "Portal",
    "Live",
    "Sports",
    "Business",
    "International",
    "Tech"
)

private val SOURCE_FILTER_CATEGORIES = listOf("All") + HOME_CATEGORIES + listOf("Agency")
