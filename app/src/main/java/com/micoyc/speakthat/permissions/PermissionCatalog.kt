package com.micoyc.speakthat.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.MainActivity
import com.micoyc.speakthat.NotificationReaderService
import com.micoyc.speakthat.R
import com.micoyc.speakthat.UpdateFeature
import com.micoyc.speakthat.settings.BehaviorSettingsStore
import com.micoyc.speakthat.summary.SummaryConstants
import com.micoyc.speakthat.summary.SummaryScheduler
import com.micoyc.speakthat.summary.SummarySettingsGate
import com.micoyc.speakthat.utils.BackgroundLocationHelper

/**
 * Centralized permission + feature gating logic.
 *
 * Encapsulates SDK-gated permission arrays and the reconciler that disables
 * imported toggles when their required permissions are missing.
 */
object PermissionCatalog {

    data class CoreFeatureState(
        val honourPhoneCallsEnabled: Boolean,
        val postNotificationsFeatureEnabled: Boolean,
        val summaryOverlayEnabled: Boolean,
        val summarySchedulerEnabled: Boolean,
        val clockEnabled: Boolean,
        val clockPrecisionModeEnabled: Boolean,
        val updaterEnabled: Boolean
    )

    @JvmStatic
    fun loadCoreFeatureState(context: Context): CoreFeatureState {
        val mainPrefs = context.getSharedPreferences(context.getString(R.string.prefs_speakthat), Context.MODE_PRIVATE)
        val behaviorPrefs = context.getSharedPreferences(BehaviorSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)

        val masterEnabled = MainActivity.isMasterSwitchEnabled(context)

        val honourPhoneCallsEnabled = behaviorPrefs.getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS,
            BehaviorSettingsStore.DEFAULT_HONOUR_PHONE_CALLS
        )

        val persistentNotification = mainPrefs.getBoolean("persistent_notification", false)
        val notificationWhileReading = mainPrefs.getBoolean("notification_while_reading", false)
        val postNotificationsFeatureEnabled = masterEnabled && (persistentNotification || notificationWhileReading)

        val summaryPrefs = SummarySettingsGate.prefs(context)
        val summaryOverlayEnabled = summaryPrefs.getBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false)
        val summarySchedulerEnabled = summaryPrefs.getBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false) && summaryOverlayEnabled

        val clockEnabled = mainPrefs.getBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_ENABLED, false)
        val clockPrecisionModeEnabled =
            mainPrefs.getBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_PRECISION_MODE, false) && clockEnabled

        val updaterEnabled = UpdateFeature.isEnabled() && mainPrefs.getBoolean("auto_update_enabled", true)

        return CoreFeatureState(
            honourPhoneCallsEnabled = honourPhoneCallsEnabled,
            postNotificationsFeatureEnabled = postNotificationsFeatureEnabled,
            summaryOverlayEnabled = summaryOverlayEnabled,
            summarySchedulerEnabled = summarySchedulerEnabled,
            clockEnabled = clockEnabled,
            clockPrecisionModeEnabled = clockPrecisionModeEnabled,
            updaterEnabled = updaterEnabled
        )
    }

    // ---------------------------------------------------------------------
    // Runtime permissions: Phone, POST_NOTIFICATIONS, install packages
    // ---------------------------------------------------------------------
    @JvmStatic
    fun isReadPhoneStateGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isInstallPackagesGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.REQUEST_INSTALL_PACKAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------------
    // WiFi permissions
    // ---------------------------------------------------------------------
    @JvmStatic
    fun hasAllWifiPermissions(context: Context): Boolean {
        return BackgroundLocationHelper.hasAllWifiPermissions(context)
    }

    @JvmStatic
    fun getWifiPermissionsForSdk(sdkInt: Int): List<String> {
        val perms = mutableListOf<String>()
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (sdkInt >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        return perms
    }

    // ---------------------------------------------------------------------
    // Bluetooth permissions
    // ---------------------------------------------------------------------
    @JvmStatic
    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Keep aligned with the app's current Bluetooth evaluation + request logic.
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    @JvmStatic
    fun getBluetoothPermissionsForSdk(sdkInt: Int): List<String> {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    // ---------------------------------------------------------------------
    // System settings: Overlay + Exact alarms
    // ---------------------------------------------------------------------
    @JvmStatic
    fun isOverlayGranted(context: Context): Boolean {
        return SummarySettingsGate.isOverlayPermissionGranted(context)
    }

    @JvmStatic
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    // ---------------------------------------------------------------------
    // Reconciliation helpers (disable toggles when permissions missing).
    // ---------------------------------------------------------------------
    @JvmStatic
    fun reconcileCoreFeaturesIfNeeded(context: Context): ReconciliationResult {
        val state = loadCoreFeatureState(context)

        var disabledPhoneCalls = false
        var disabledPostNotifications = false
        var disabledSummaryOverlay = false
        var disabledSummaryScheduler = false
        var disabledClockPrecision = false
        var disabledUpdater = false

        // 1) Phone calls
        if (state.honourPhoneCallsEnabled && !isReadPhoneStateGranted(context)) {
            context.getSharedPreferences(BehaviorSettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS, false)
                .apply()
            disabledPhoneCalls = true
        }

        // 2) POST_NOTIFICATIONS-dependent features
        if (state.postNotificationsFeatureEnabled && !isPostNotificationsGranted(context)) {
            val prefs = context.getSharedPreferences(context.getString(R.string.prefs_speakthat), Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("persistent_notification", false)
                .putBoolean("notification_while_reading", false)
                .apply()
            disabledPostNotifications = true
        }

        // 3) Summary overlay + scheduler (SYSTEM_ALERT_WINDOW + exact alarms)
        val summaryPrefs = SummarySettingsGate.prefs(context)
        val overlayGranted = isOverlayGranted(context)
        val exactAlarmsGranted = canScheduleExactAlarms(context)

        if (summaryPrefs.getBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false) && !overlayGranted) {
            summaryPrefs.edit()
                .putBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false)
                .putBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false)
                .apply()
            SummaryScheduler.cancel(context)
            disabledSummaryOverlay = true
        } else if (summaryPrefs.getBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false) && !exactAlarmsGranted) {
            summaryPrefs.edit()
                .putBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false)
                .apply()
            SummaryScheduler.cancel(context)
            disabledSummaryScheduler = true
        }

        // 4) Clock precision mode (SCHEDULE_EXACT_ALARM)
        val clockPrecisionEnabled = context.getSharedPreferences(
            context.getString(R.string.prefs_speakthat),
            Context.MODE_PRIVATE
        ).getBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_PRECISION_MODE, false)

        if (clockPrecisionEnabled && !exactAlarmsGranted) {
            context.getSharedPreferences(context.getString(R.string.prefs_speakthat), Context.MODE_PRIVATE).edit()
                .putBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_PRECISION_MODE, false)
                .apply()
            disabledClockPrecision = true
        }

        // 5) GitHub updater install permission
        if (state.updaterEnabled && !isInstallPackagesGranted(context)) {
            val prefs = context.getSharedPreferences(context.getString(R.string.prefs_speakthat), Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("auto_update_enabled", false)
                .apply()
            // Lets the flavor-specific UpdateFeature reconcile WorkManager jobs.
            UpdateFeature.onAutoUpdatePreferenceChanged(context)
            disabledUpdater = true
        }

        return ReconciliationResult(
            disabledPhoneCalls = disabledPhoneCalls,
            disabledPostNotifications = disabledPostNotifications,
            disabledSummaryOverlay = disabledSummaryOverlay,
            disabledSummaryScheduler = disabledSummaryScheduler,
            disabledClockPrecision = disabledClockPrecision,
            disabledUpdater = disabledUpdater
        )
    }

    data class ReconciliationResult(
        val disabledPhoneCalls: Boolean,
        val disabledPostNotifications: Boolean,
        val disabledSummaryOverlay: Boolean,
        val disabledSummaryScheduler: Boolean,
        val disabledClockPrecision: Boolean,
        val disabledUpdater: Boolean
    )
}

