package com.micoyc.speakthat.rules

/**
 * Notification-scoped context used for rule evaluation.
 */
data class NotificationContext(
    val packageName: String,
    val appLabel: String,
    val notificationTitle: String?,
    val notificationText: String?,
    val notificationCategory: String?,
    val notificationChannelId: String?,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun empty(): NotificationContext {
            return NotificationContext(
                packageName = "",
                appLabel = "",
                notificationTitle = null,
                notificationText = null,
                notificationCategory = null,
                notificationChannelId = null
            )
        }
    }
}
