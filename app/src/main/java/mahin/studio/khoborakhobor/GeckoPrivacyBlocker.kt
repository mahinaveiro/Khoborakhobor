package mahin.studio.khoborakhobor

import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime

class GeckoPrivacyBlocker(private val runtime: GeckoRuntime) {
    fun setEnabled(enabled: Boolean) {
        applyTo(runtime.settings.contentBlocking, enabled)
        runtime.settings
            .setGlobalPrivacyControl(enabled)
            .setFingerprintingProtection(enabled)
            .setBaselineFingerprintingProtection(enabled)
        Log.i(TAG, "GeckoView privacy fallback ${if (enabled) "enabled" else "disabled"}")
    }

    companion object {
        private const val TAG = "GeckoPrivacyBlocker"

        private val blockingCategories =
            ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING

        fun contentBlockingSettings(enabled: Boolean): ContentBlocking.Settings {
            return ContentBlocking.Settings.Builder()
                .antiTracking(if (enabled) blockingCategories else ContentBlocking.AntiTracking.NONE)
                .enhancedTrackingProtectionLevel(
                    if (enabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.NONE
                )
                .enhancedTrackingProtectionCategory(
                    if (enabled) blockingCategories else ContentBlocking.AntiTracking.NONE
                )
                .strictSocialTrackingProtection(enabled)
                .cookieBehavior(
                    if (enabled) {
                        ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                    } else {
                        ContentBlocking.CookieBehavior.ACCEPT_ALL
                    }
                )
                .cookiePurging(enabled)
                .queryParameterStrippingEnabled(enabled)
                .queryParameterStrippingPrivateBrowsingEnabled(enabled)
                .bounceTrackingProtectionMode(
                    if (enabled) {
                        ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
                    } else {
                        ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_DISABLED
                    }
                )
                .build()
        }

        private fun applyTo(settings: ContentBlocking.Settings, enabled: Boolean) {
            settings
                .setAntiTracking(if (enabled) blockingCategories else ContentBlocking.AntiTracking.NONE)
                .setEnhancedTrackingProtectionLevel(
                    if (enabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.NONE
                )
                .setEnhancedTrackingProtectionCategory(
                    if (enabled) blockingCategories else ContentBlocking.AntiTracking.NONE
                )
                .setStrictSocialTrackingProtection(enabled)
                .setCookieBehavior(
                    if (enabled) {
                        ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                    } else {
                        ContentBlocking.CookieBehavior.ACCEPT_ALL
                    }
                )
                .setCookiePurging(enabled)
                .setQueryParameterStrippingEnabled(enabled)
                .setQueryParameterStrippingPrivateBrowsingEnabled(enabled)
                .setBounceTrackingProtectionMode(
                    if (enabled) {
                        ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
                    } else {
                        ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_DISABLED
                    }
                )
        }
    }
}
