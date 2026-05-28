package mahin.studio.khoborakhobor

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeProvider {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context, disableAds: Boolean): GeckoRuntime {
        return runtime ?: synchronized(this) {
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

    fun isCreated(): Boolean = runtime != null
}
