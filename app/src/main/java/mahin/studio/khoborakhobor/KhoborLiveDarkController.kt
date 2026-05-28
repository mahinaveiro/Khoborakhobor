package mahin.studio.khoborakhobor

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.WeakHashMap

internal class KhoborLiveDarkController(
    private val runtime: GeckoRuntime
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<(WebExtension?) -> Unit>()
    private val attachedSessions = WeakHashMap<GeckoSession, Boolean>()
    private var extension: WebExtension? = null
    private var desiredEnabled = false
    private var loading = false

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            val payload = message as? JSONObject
            val type = payload?.optString("type").orEmpty()
            if (type == "GET_KHOBOR_DARK") {
                return GeckoResult.fromValue(darkStateMessage())
            }
            if (type == "SET_KHOBOR_DARK") {
                desiredEnabled = payload?.optBoolean("enabled", desiredEnabled) ?: desiredEnabled
                return GeckoResult.fromValue(darkStateMessage())
            }
            return GeckoResult.fromValue(darkStateMessage())
        }
    }

    fun attach(session: GeckoSession, enabled: Boolean) {
        desiredEnabled = enabled
        ensure { webExtension ->
            if (webExtension == null) return@ensure
            attachDelegate(webExtension)
            attachSessionDelegate(session, webExtension)
        }
    }

    fun setEnabled(session: GeckoSession, enabled: Boolean, onFallbackReload: () -> Unit) {
        desiredEnabled = enabled
        ensure { webExtension ->
            if (webExtension == null) {
                onMain(onFallbackReload)
                return@ensure
            }
            attachDelegate(webExtension)
            attachSessionDelegate(session, webExtension)
        }
    }

    fun detach(session: GeckoSession) {
        attachedSessions.remove(session)
    }

    private fun attachDelegate(webExtension: WebExtension) {
        webExtension.setMessageDelegate(messageDelegate, NATIVE_APP)
    }

    private fun attachSessionDelegate(session: GeckoSession, webExtension: WebExtension) {
        if (attachedSessions[session] == true) return
        session.webExtensionController.setMessageDelegate(webExtension, messageDelegate, NATIVE_APP)
        attachedSessions[session] = true
    }

    private fun darkStateMessage(): JSONObject {
        return JSONObject()
            .put("type", "SET_KHOBOR_DARK")
            .put("enabled", desiredEnabled)
    }

    private fun ensure(onReady: (WebExtension?) -> Unit) {
        extension?.let {
            onReady(it)
            return
        }
        pending += onReady
        if (loading) return
        loading = true
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept(
                { installed ->
                    extension = installed
                    loading = false
                    if (installed == null) {
                        Log.e(TAG, "Live dark extension returned no WebExtension instance")
                    } else {
                        attachDelegate(installed)
                        Log.d(TAG, "Live dark extension ready: ${installed.id}")
                    }
                    flushPending(installed)
                },
                { throwable ->
                    loading = false
                    Log.e(TAG, "Live dark extension install failed", throwable)
                    flushPending(null)
                }
            )
    }

    private fun flushPending(webExtension: WebExtension?) {
        val callbacks = pending.toList()
        pending.clear()
        callbacks.forEach { it(webExtension) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private companion object {
        const val TAG = "KhoborLiveDark"
        const val EXTENSION_URI = "resource://android/assets/khobor-web-dark/"
        const val EXTENSION_ID = "khobor-web-dark@khoborakhobor.local"
        const val NATIVE_APP = "khobor_dark"
    }
}
