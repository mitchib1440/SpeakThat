package com.micoyc.speakthat.gesture

class WaveEvaluator(
    private var targetCount: Int = 1,
    private var waveHoldDurationMs: Long = 0L,
    private var isPocketModeEnabled: Boolean = false
) {
    companion object {
        const val WAVE_DEBOUNCE_MS = 200L
        const val MULTI_WAVE_WINDOW_MS = 1200L
        const val WAVE_STARTUP_GRACE_MS = 500L
        const val MIN_WAVE_HOLD_DURATION_MS = 50L
    }

    private var currentWaveCount = 0
    private var lastWaveTimeMs = 0L
    private var firstWaveTimeMs = 0L

    private var isSensorCurrentlyCovered = false
    private var wasSensorCoveredAtStart = false
    private var hasSensorBeenUncovered = false
    private var hasCapturedStartProximity = false
    
    private var speechStartTimestamp = 0L

    fun setTargetCount(count: Int) {
        targetCount = count
    }

    fun setWaveHoldDurationMs(durationMs: Long) {
        waveHoldDurationMs = durationMs
    }

    fun setPocketModeEnabled(enabled: Boolean) {
        isPocketModeEnabled = enabled
    }

    fun notifySpeechStarted(timestamp: Long) {
        speechStartTimestamp = timestamp
        hasCapturedStartProximity = false
        wasSensorCoveredAtStart = false
        hasSensorBeenUncovered = false
    }

    /**
     * Reset wave state for a new readout and optionally seed pocket mode from a recent proximity sample
     * (matches prior NotificationReaderService behavior at listener registration).
     */
    fun prepareForSpeechSession(
        speechStartMs: Long,
        pocketModeEnabled: Boolean,
        lastProximityTimestampMs: Long,
        lastProximityWasNear: Boolean,
        recentSampleMaxAgeMs: Long
    ) {
        currentWaveCount = 0
        lastWaveTimeMs = 0L
        firstWaveTimeMs = 0L
        isSensorCurrentlyCovered = false
        speechStartTimestamp = speechStartMs
        hasSensorBeenUncovered = false
        hasCapturedStartProximity = false
        wasSensorCoveredAtStart = false

        if (pocketModeEnabled) {
            val hasRecent = lastProximityTimestampMs > 0L &&
                speechStartMs - lastProximityTimestampMs <= recentSampleMaxAgeMs
            if (hasRecent) {
                wasSensorCoveredAtStart = lastProximityWasNear
                hasCapturedStartProximity = true
            }
        }
    }

    fun pocketCoveredAtStart(): Boolean = wasSensorCoveredAtStart

    fun evaluate(isNear: Boolean, isCurrentlySpeaking: Boolean, currentTime: Long = System.currentTimeMillis()): EvaluationResult {
        var windowExpired = false
        var failedCount = 0

        // Handle window timeout for multi-wave
        if (targetCount > 1 && currentWaveCount > 0 && currentTime - firstWaveTimeMs > MULTI_WAVE_WINDOW_MS) {
            failedCount = currentWaveCount
            currentWaveCount = 0
            windowExpired = true
        }

        val wasCovered = isSensorCurrentlyCovered
        isSensorCurrentlyCovered = isNear

        // Pocket mode logic
        if (isPocketModeEnabled && !hasCapturedStartProximity && isCurrentlySpeaking) {
            wasSensorCoveredAtStart = isNear
            hasCapturedStartProximity = true
            if (isNear && currentTime - speechStartTimestamp <= WAVE_STARTUP_GRACE_MS) {
                return if (windowExpired) EvaluationResult.WindowExpired(failedCount) else EvaluationResult.Ignored
            }
        }

        if (wasCovered && !isSensorCurrentlyCovered) {
            hasSensorBeenUncovered = true
            if (targetCount == 1) {
                return EvaluationResult.HoldCancelled
            }
        }

        if (isNear) {
            if (isPocketModeEnabled && wasSensorCoveredAtStart && !hasSensorBeenUncovered) {
                return if (windowExpired) EvaluationResult.WindowExpired(failedCount) else EvaluationResult.Ignored
            }

            if (targetCount == 1) {
                if (!wasCovered) {
                    if (waveHoldDurationMs <= MIN_WAVE_HOLD_DURATION_MS) {
                        return EvaluationResult.TargetReached
                    } else {
                        return EvaluationResult.HoldScheduled(waveHoldDurationMs)
                    }
                }
            } else {
                // Multi-wave logic: Look for FAR -> NEAR transitions
                if (!wasCovered) {
                    if (currentTime - lastWaveTimeMs < WAVE_DEBOUNCE_MS) {
                        return if (windowExpired) EvaluationResult.WindowExpired(failedCount) else EvaluationResult.Ignored
                    }

                    if (currentWaveCount == 0) {
                        firstWaveTimeMs = currentTime
                    }

                    lastWaveTimeMs = currentTime
                    currentWaveCount++

                    if (currentWaveCount >= targetCount) {
                        currentWaveCount = 0
                        return EvaluationResult.TargetReached
                    } else {
                        return EvaluationResult.ValidWave(currentWaveCount)
                    }
                }
            }
        }

        return if (windowExpired) {
            EvaluationResult.WindowExpired(failedCount)
        } else {
            EvaluationResult.NoAction
        }
    }

    fun isSensorCurrentlyCovered(): Boolean {
        return isSensorCurrentlyCovered
    }

    fun isPocketModeBlocking(): Boolean {
        return isPocketModeEnabled && wasSensorCoveredAtStart && !hasSensorBeenUncovered
    }

    fun reset() {
        currentWaveCount = 0
        lastWaveTimeMs = 0L
        firstWaveTimeMs = 0L
        isSensorCurrentlyCovered = false
        wasSensorCoveredAtStart = false
        hasSensorBeenUncovered = false
        hasCapturedStartProximity = false
        speechStartTimestamp = 0L
    }

    sealed class EvaluationResult {
        data class HoldScheduled(val holdDurationMs: Long) : EvaluationResult()
        data object HoldCancelled : EvaluationResult()
        data object TargetReached : EvaluationResult()
        data class ValidWave(val currentCount: Int) : EvaluationResult()
        data class WindowExpired(val failedCount: Int) : EvaluationResult()
        data object Ignored : EvaluationResult()
        data object NoAction : EvaluationResult()
    }
}
