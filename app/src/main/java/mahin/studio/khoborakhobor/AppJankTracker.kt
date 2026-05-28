package mahin.studio.khoborakhobor

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

class AppJankTracker(window: Window) {
    private val metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView)
    private val jankStats = JankStats.createAndTrack(window) { frameData ->
        if (frameData.isJank) {
            val stateText = frameData.states.joinToString(",") { state ->
                "${state.key}=${state.value}"
            }
            val event = "${frameData.frameDurationUiNanos / NANOS_PER_MS}ms $stateText"
            lastJankEvent = event
            Log.w(TAG, event)
        }
    }

    fun setState(key: String, value: String) {
        metricsStateHolder.state?.putState(key, value)
    }

    fun removeState(key: String) {
        metricsStateHolder.state?.removeState(key)
    }

    fun stop() {
        jankStats.isTrackingEnabled = false
    }

    companion object {
        private const val TAG = "KhoborJank"
        private const val NANOS_PER_MS = 1_000_000L

        @Volatile
        var lastJankEvent: String = "none"
            private set
    }
}
