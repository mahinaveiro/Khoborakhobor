package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.json.JSONObject
import java.io.FileNotFoundException

class UBlockOriginManager(
    private val context: Context,
    private val runtime: GeckoRuntime
) {
    private var webExtension: WebExtension? = null

    fun ensureBuiltIn(onResult: (Result<WebExtension>) -> Unit) {
        validateManifest().onFailure { error ->
            logEnsureFailure(error)
            onResult(Result.failure(error))
            return
        }

        Log.d(TAG, "Clear app data or reinstall app after changing bundled extension files.")
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept(
                { extension ->
                    if (extension == null) {
                        val error = IllegalStateException("uBlock Origin returned no WebExtension instance")
                        logEnsureFailure(error)
                        onResult(Result.failure(error))
                        return@accept
                    }
                    webExtension = extension
                    Log.d(TAG, "uBlock ensureBuiltIn success: ${extension.id}")
                    onResult(Result.success(extension))
                },
                { throwable ->
                    val error = throwable ?: IllegalStateException("Unknown uBlock Origin load failure")
                    logEnsureFailure(error)
                    onResult(Result.failure(error))
                }
            )
    }

    fun setEnabled(
        enabled: Boolean,
        source: Int,
        onResult: (Result<WebExtension>) -> Unit
    ) {
        val extension = webExtension
        if (extension == null) {
            val error = IllegalStateException("uBlock Origin has not been loaded")
            Log.e(TAG, "Cannot change uBlock Origin state", error)
            onResult(Result.failure(error))
            return
        }

        val result = if (enabled) {
            if (source == WebExtensionController.EnableSource.USER) {
                runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.USER)
            } else {
                runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.APP)
            }
        } else {
            if (source == WebExtensionController.EnableSource.USER) {
                runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.USER)
            } else {
                runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.APP)
            }
        }

        result.accept(
            { updatedExtension ->
                if (updatedExtension == null) {
                    val error = IllegalStateException("uBlock Origin returned no WebExtension instance")
                    Log.e(TAG, "Failed to ${if (enabled) "enable" else "disable"} uBlock Origin", error)
                    onResult(Result.failure(error))
                    return@accept
                }
                webExtension = updatedExtension
                Log.d(TAG, "uBlock ${if (enabled) "enabled" else "disabled"}")
                onResult(Result.success(updatedExtension))
            },
            { throwable ->
                val error = throwable ?: IllegalStateException("Unknown uBlock Origin state change failure")
                Log.e(TAG, "Failed to ${if (enabled) "enable" else "disable"} uBlock Origin", error)
                onResult(Result.failure(error))
            }
        )
    }

    private fun validateManifest(): Result<Unit> {
        return runCatching {
            val manifest = try {
                context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().use { it.readText() }
            } catch (error: FileNotFoundException) {
                throw IllegalStateException("uBlock manifest missing from assets/ublock/manifest.json", error)
            }

            val manifestId = JSONObject(manifest)
                .getJSONObject("browser_specific_settings")
                .getJSONObject("gecko")
                .getString("id")

            if (manifestId != EXTENSION_ID) {
                throw IllegalStateException("uBlock manifest id mismatch: expected $EXTENSION_ID but found $manifestId")
            }
        }
    }

    private fun logEnsureFailure(error: Throwable) {
        if (error.message == "uBlock manifest missing from assets/ublock/manifest.json") {
            Log.e(TAG, "uBlock manifest missing from assets/ublock/manifest.json", error)
        } else {
            Log.e(TAG, "uBlock ensureBuiltIn failed: ${error}", error)
        }
    }

    private companion object {
        const val TAG = "UBlockOriginManager"
        const val MANIFEST_ASSET_PATH = "ublock/manifest.json"
        const val EXTENSION_URI = "resource://android/assets/ublock/"
        const val EXTENSION_ID = "uBlock0@raymondhill.net"
    }
}
