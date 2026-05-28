package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeProvider {
    private const val TAG = "KhoborPerf"

    @Volatile
    private var runtime: GeckoRuntime? = null

    @Volatile
    private var currentScreen: String = "Unknown"

    fun setCurrentScreen(screen: String) {
        currentScreen = screen
    }

    fun get(
        context: Context,
        disableAds: Boolean,
        currentScreen: String = this.currentScreen
    ): GeckoRuntime {
        return runtime ?: synchronized(this) {
            if (runtime == null && currentScreen !in BROWSER_RUNTIME_SCREENS) {
                Log.e(TAG, "BUG: Gecko initialized outside browser path")
            }
            runtime ?: PerformanceLogger.trace("GeckoRuntime init") {
                GeckoRuntime.create(
                    context.applicationContext,
                    GeckoRuntimeSettings.Builder()
                        .extensionsWebAPIEnabled(true)
                        .contentBlocking(GeckoPrivacyBlocker.contentBlockingSettings(disableAds))
                        .build()
                )
            }.also {
                runtime = it
            }
        }
    }

    fun getIfCreated(): GeckoRuntime? = runtime

    fun isCreated(): Boolean = runtime != null

    private val BROWSER_RUNTIME_SCREENS = setOf("Browser", "Reader")
}
