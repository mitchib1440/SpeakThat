package com.micoyc.speakthat.gesture

import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeEvaluator(
    private var targetCount: Int = 1,
    private var threshold: Float = 12.0f
) {
    companion object {
        const val SHAKE_DEBOUNCE_MS = 200L
        const val MULTI_SHAKE_WINDOW_MS = 1000L
    }

    private var currentShakeCount = 0
    private var lastShakeTimeMs = 0L
    private var firstShakeTimeMs = 0L

    fun setTargetCount(count: Int) {
        targetCount = count
    }

    fun setThreshold(newThreshold: Float) {
        threshold = newThreshold
    }

    fun evaluate(x: Float, y: Float, z: Float): EvaluationResult {
        val currentTime = System.currentTimeMillis()
        val shakeValue = (sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH).toFloat()

        // Handle window timeout
        if (currentShakeCount > 0 && currentTime - firstShakeTimeMs > MULTI_SHAKE_WINDOW_MS) {
            val failedCount = currentShakeCount
            currentShakeCount = 0
            return EvaluationResult.WindowExpired(failedCount, shakeValue)
        }

        if (shakeValue >= threshold) {
            // Debounce
            if (currentTime - lastShakeTimeMs < SHAKE_DEBOUNCE_MS) {
                return EvaluationResult.Ignored(shakeValue)
            }

            if (currentShakeCount == 0) {
                firstShakeTimeMs = currentTime
            }

            lastShakeTimeMs = currentTime
            currentShakeCount++

            if (currentShakeCount >= targetCount) {
                currentShakeCount = 0
                return EvaluationResult.TargetReached(shakeValue)
            } else {
                return EvaluationResult.ValidShake(currentShakeCount, shakeValue)
            }
        }

        return EvaluationResult.NoAction(shakeValue)
    }

    fun reset() {
        currentShakeCount = 0
        lastShakeTimeMs = 0L
        firstShakeTimeMs = 0L
    }

    sealed class EvaluationResult {
        abstract val shakeValue: Float
        
        data class TargetReached(override val shakeValue: Float) : EvaluationResult()
        data class ValidShake(val currentCount: Int, override val shakeValue: Float) : EvaluationResult()
        data class WindowExpired(val failedCount: Int, override val shakeValue: Float) : EvaluationResult()
        data class Ignored(override val shakeValue: Float) : EvaluationResult()
        data class NoAction(override val shakeValue: Float) : EvaluationResult()
    }
}
