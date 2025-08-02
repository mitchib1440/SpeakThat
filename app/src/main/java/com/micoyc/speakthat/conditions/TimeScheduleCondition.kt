package com.micoyc.speakthat.conditions

import android.content.Context
import android.util.Log
import com.micoyc.speakthat.BaseCondition
import com.micoyc.speakthat.ConditionChecker
import com.micoyc.speakthat.InAppLogger
import java.util.Calendar

/**
 * Time schedule condition
 * Allows notifications only during specific time windows and days
 */
data class TimeScheduleCondition(
    override val enabled: Boolean = false,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59,
    val daysOfWeek: Set<Int> = setOf(0,1,2,3,4,5,6) // Sunday = 0, Saturday = 6
) : BaseCondition(enabled, "time_schedule") {
    
    override fun createChecker(context: Context): ConditionChecker {
        return TimeScheduleConditionChecker(this, context)
    }
}

/**
 * Time schedule condition checker implementation
 */
class TimeScheduleConditionChecker(
    private val condition: TimeScheduleCondition,
    private val context: Context
) : ConditionChecker {
    
    companion object {
        private const val TAG = "TimeScheduleCondition"
    }
    
    override fun shouldAllowNotification(context: Context): Boolean {
        if (!condition.enabled) {
            return true // No restriction if disabled
        }
        
        try {
            val calendar = Calendar.getInstance()
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-based (Sunday = 0)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            
            // Check if current day is allowed
            if (!condition.daysOfWeek.contains(currentDayOfWeek)) {
                Log.d(TAG, "Time schedule condition failed: day ${currentDayOfWeek} not allowed")
                InAppLogger.logFilter("Time schedule condition: Day ${getDayName(currentDayOfWeek)} not allowed")
                return false
            }
            
            // Convert current time to minutes for easier comparison
            val currentTimeMinutes = currentHour * 60 + currentMinute
            val startTimeMinutes = condition.startHour * 60 + condition.startMinute
            val endTimeMinutes = condition.endHour * 60 + condition.endMinute
            
            // Handle overnight schedules (e.g., 22:00 to 06:00)
            val isInTimeWindow = if (endTimeMinutes < startTimeMinutes) {
                // Overnight schedule
                currentTimeMinutes >= startTimeMinutes || currentTimeMinutes <= endTimeMinutes
            } else {
                // Same day schedule
                currentTimeMinutes >= startTimeMinutes && currentTimeMinutes <= endTimeMinutes
            }
            
            if (isInTimeWindow) {
                Log.d(TAG, "Time schedule condition passed: ${currentHour}:${currentMinute.toString().padStart(2, '0')} is within allowed window")
                InAppLogger.logFilter("Time schedule condition: Time ${currentHour}:${currentMinute.toString().padStart(2, '0')} is within allowed window")
                return true
            } else {
                Log.d(TAG, "Time schedule condition failed: ${currentHour}:${currentMinute.toString().padStart(2, '0')} is outside allowed window")
                InAppLogger.logFilter("Time schedule condition: Time ${currentHour}:${currentMinute.toString().padStart(2, '0')} is outside allowed window")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking time schedule condition", e)
            InAppLogger.logError("TimeScheduleCondition", "Error checking time schedule: ${e.message}")
            // Fail-safe: allow notification if we can't check time
            return true
        }
    }
    
    override fun getConditionName(): String {
        return "Time Schedule"
    }
    
    override fun getConditionDescription(): String {
        val startTime = "${condition.startHour.toString().padStart(2, '0')}:${condition.startMinute.toString().padStart(2, '0')}"
        val endTime = "${condition.endHour.toString().padStart(2, '0')}:${condition.endMinute.toString().padStart(2, '0')}"
        val days = condition.daysOfWeek.map { getDayName(it) }.joinToString(", ")
        
        return "Only between $startTime and $endTime on $days"
    }
    
    override fun isEnabled(): Boolean {
        return condition.enabled
    }
    
    override fun getLogMessage(): String {
        val startTime = "${condition.startHour.toString().padStart(2, '0')}:${condition.startMinute.toString().padStart(2, '0')}"
        val endTime = "${condition.endHour.toString().padStart(2, '0')}:${condition.endMinute.toString().padStart(2, '0')}"
        return "Time schedule condition: Current time outside allowed window ($startTime - $endTime)"
    }
    
    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            0 -> "Sunday"
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Unknown"
        }
    }
} 