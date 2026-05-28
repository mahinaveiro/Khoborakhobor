package mahin.studio.khoborakhobor

import android.os.SystemClock
import android.os.Trace
import android.util.Log

object PerformanceLogger {
    private const val TAG = "KhoborPerf"

    @Volatile
    var lastOperation: String = "none"
        private set

    fun mark(label: String) {
        Log.i(TAG, label)
    }

    fun logDuration(label: String, startMillis: Long) {
        val duration = SystemClock.elapsedRealtime() - startMillis
        lastOperation = "$label ${duration}ms"
        Log.i(TAG, lastOperation)
    }

    inline fun <T> trace(label: String, block: () -> T): T {
        val startMillis = SystemClock.elapsedRealtime()
        Trace.beginSection(label)
        return try {
            block()
        } finally {
            Trace.endSection()
            logDuration(label, startMillis)
        }
    }

    suspend fun <T> traceSuspend(label: String, block: suspend () -> T): T {
        val startMillis = SystemClock.elapsedRealtime()
        Trace.beginSection(label)
        return try {
            block()
        } finally {
            Trace.endSection()
            logDuration(label, startMillis)
        }
    }
}
