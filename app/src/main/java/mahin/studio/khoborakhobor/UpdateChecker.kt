package mahin.studio.khoborakhobor

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateTitle: String,
    val updateMessage: String,
    val updateUrl: String,
    val forceUpdate: Boolean
)

object UpdateChecker {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    fun checkForUpdates(): UpdateInfo? {
        val request = Request.Builder()
            .url(AppConfig.UPDATE_JSON_URL)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val obj = JSONObject(body)
                val latestCode = obj.getInt("latestVersionCode")
                val currentCode = BuildConfig.VERSION_CODE
                
                UpdateInfo(
                    hasUpdate = latestCode > currentCode,
                    latestVersionCode = latestCode,
                    latestVersionName = obj.optString("latestVersionName", "1.0"),
                    updateTitle = obj.optString("updateTitle", "Update available"),
                    updateMessage = obj.optString("updateMessage", "A new version of Khoborakhobor is available."),
                    updateUrl = obj.optString("updateUrl", AppLinks.TELEGRAM_URL),
                    forceUpdate = obj.optBoolean("forceUpdate", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
