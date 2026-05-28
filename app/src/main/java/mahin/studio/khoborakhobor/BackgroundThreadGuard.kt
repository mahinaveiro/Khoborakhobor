package mahin.studio.khoborakhobor

import android.os.Looper

object BackgroundThreadGuard {
    fun requireBackgroundThread(
        operation: String,
        isMainThread: () -> Boolean = {
            Looper.getMainLooper().thread === Thread.currentThread()
        }
    ) {
        check(!isMainThread()) { "$operation must run on Dispatchers.IO or another background thread." }
    }
}
