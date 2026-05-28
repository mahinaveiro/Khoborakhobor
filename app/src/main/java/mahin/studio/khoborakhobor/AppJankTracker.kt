package mahin.studio.khoborakhobor

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AppJankTracker(window: Window) {
    private val metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView)
    private val frameCount = AtomicInteger(0)
    private val jankCount = AtomicInteger(0)
    private val worstFrameMs = AtomicLong(0L)
    private val worstState = java.util.concurrent.atomic.AtomicReference("none")
    private val jankStats = JankStats.createAndTrack(window) { frameData ->
        frameCount.incrementAndGet()
        val frameMs = frameData.frameDurationUiNanos / NANOS_PER_MS
        if (frameMs > worstFrameMs.get()) {
            worstFrameMs.set(frameMs)
            worstState.set(frameData.states.joinToString(",") { state ->
                "${state.key}=${state.value}"
            }.ifBlank { "none" })
        }
        if (frameData.isJank) {
            jankCount.incrementAndGet()
            val event = "${frameMs}ms ${worstState.get()}"
            lastJankEvent = event
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
        Log.i(
            TAG,
            "JankStats summary frames=${frameCount.get()} " +
                "jank=${jankCount.get()} worst=${worstFrameMs.get()}ms state=${worstState.get()}"
        )
    }

    companion object {
        private const val TAG = "KhoborJank"
        private const val NANOS_PER_MS = 1_000_000L

        @Volatile
        var lastJankEvent: String = "none"
            private set
    }
}
