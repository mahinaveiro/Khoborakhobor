package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeProvider {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context, disableAds: Boolean): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .extensionsWebAPIEnabled(true)
                    .contentBlocking(GeckoPrivacyBlocker.contentBlockingSettings(disableAds))
                    .build()
            ).also {
                runtime = it
                Log.i(TAG, "GeckoRuntime created")
            }
        }
    }

    private const val TAG = "GeckoRuntimeProvider"
}
