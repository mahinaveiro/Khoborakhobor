package mahin.studio.khoborakhobor

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

class AdBlockController(
    context: android.content.Context,
    runtime: GeckoRuntime,
    private val uBlockOriginManager: UBlockOriginManager = UBlockOriginManager(context, runtime),
    private val privacyBlocker: GeckoPrivacyBlocker = GeckoPrivacyBlocker(runtime)
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var desiredEnabled = false
    private var uBlockUnavailable = false
    private var uBlockReady = false
    private var lastError: Throwable? = null
    private var enableSource = WebExtensionController.EnableSource.APP

    fun start(enabled: Boolean, onStateChanged: (AdBlockState) -> Unit) {
        desiredEnabled = enabled
        enableSource = WebExtensionController.EnableSource.APP
        emit(
            onStateChanged,
            AdBlockState(
                enabled = enabled,
                status = if (enabled) UBlockStatus.LOADING else UBlockStatus.DISABLED
            )
        )
        uBlockOriginManager.ensureBuiltIn { result ->
            result.fold(
                onSuccess = {
                    uBlockUnavailable = false
                    uBlockReady = true
                    lastError = null
                    applyUBlockState(enableSource, onStateChanged)
                },
                onFailure = {
                    Log.e(TAG, "uBlock failed with real exception", it)
                    uBlockUnavailable = true
                    uBlockReady = false
                    lastError = it
                    applyFallbackState(it, onStateChanged)
                }
            )
        }
    }

    fun setEnabled(enabled: Boolean, onStateChanged: (AdBlockState) -> Unit) {
        desiredEnabled = enabled
        enableSource = WebExtensionController.EnableSource.USER
        when {
            uBlockReady -> applyUBlockState(enableSource, onStateChanged)
            uBlockUnavailable -> applyFallbackState(lastError, onStateChanged)
            !enabled -> emit(
                onStateChanged,
                AdBlockState(
                    enabled = false,
                    status = UBlockStatus.DISABLED
                )
            )
            else -> emit(
                onStateChanged,
                AdBlockState(
                    enabled = enabled,
                    status = if (enabled) UBlockStatus.LOADING else UBlockStatus.DISABLED
                )
            )
        }
    }

    private fun applyUBlockState(
        source: Int,
        onStateChanged: (AdBlockState) -> Unit
    ) {
        uBlockOriginManager.setEnabled(desiredEnabled, source) { result ->
            result.fold(
                onSuccess = {
                    privacyBlocker.setEnabled(false)
                    emit(
                        onStateChanged,
                        AdBlockState(
                            enabled = desiredEnabled,
                            status = if (desiredEnabled) UBlockStatus.ACTIVE else UBlockStatus.DISABLED,
                            fallbackActive = false
                        )
                    )
                },
                onFailure = {
                    Log.e(TAG, "uBlock failed with real exception", it)
                    Log.e(TAG, "Falling back to GeckoView privacy blocker after uBlock state change failed", it)
                    uBlockUnavailable = true
                    uBlockReady = false
                    lastError = it
                    applyFallbackState(it, onStateChanged)
                }
            )
        }
    }

    private fun applyFallbackState(
        error: Throwable?,
        onStateChanged: (AdBlockState) -> Unit
    ) {
        privacyBlocker.setEnabled(desiredEnabled)
        emit(
            onStateChanged,
            AdBlockState(
                enabled = desiredEnabled,
                status = if (desiredEnabled) UBlockStatus.FALLBACK_ACTIVE else UBlockStatus.DISABLED,
                fallbackActive = desiredEnabled,
                error = if (desiredEnabled) error.shortMessage() else null
            )
        )
    }

    private fun emit(onStateChanged: (AdBlockState) -> Unit, state: AdBlockState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onStateChanged(state)
        } else {
            mainHandler.post { onStateChanged(state) }
        }
    }

    private companion object {
        const val TAG = "AdBlockController"
    }
}

private fun Throwable?.shortMessage(): String? {
    val rawMessage = this?.toString() ?: return null
    return rawMessage
        .lineSequence()
        .firstOrNull()
        ?.take(160)
}
