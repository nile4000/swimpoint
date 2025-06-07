package ch.swimpoint.wear.app.data

import android.util.Log

class StrokeDetector(
    private val onStrokeCountChanged: (Int) -> Unit,
    private val onCycleCompleted: () -> Unit = {},
    private val strokesPerCycle: Int = 7,
    private val accThreshold: Float = 2.5f,
    private val minTimeBetweenPeaksMs: Long = 300L,
    private val tag: String = "StrokeDetector"
) {
    private var strokeCount = 0
    private var isStrokeInProgress = false
    private var lastPeakTime = 0L

    fun process(magnitude: Float) {
        if (magnitude > accThreshold && !isStrokeInProgress) {
            val now = System.currentTimeMillis()
            if (now - lastPeakTime > minTimeBetweenPeaksMs) {
                strokeCount++
                isStrokeInProgress = true
                lastPeakTime = now

                Log.d(tag, "Stroke erkannt! strokeCount = $strokeCount")
                onStrokeCountChanged(strokeCount)

                if (strokeCount >= strokesPerCycle) {
                    onCycleCompleted()
                    strokeCount = 0
                    onStrokeCountChanged(strokeCount)
                }
            }
        } else if (magnitude < accThreshold && isStrokeInProgress) {
            isStrokeInProgress = false
        }
    }

    fun reset() {
        strokeCount = 0
        isStrokeInProgress = false
        lastPeakTime = 0L
    }
}
