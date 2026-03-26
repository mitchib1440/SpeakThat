package com.micoyc.speakthat.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog
import com.micoyc.speakthat.R
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RuleConfigManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates permission prompting as a single flow:
 * - Request missing runtime permissions as one batch.
 * - Then open system settings screens sequentially (overlay, exact alarms).
 * - Finally reconcile feature toggles that depend on missing permissions.
 *
 * This is designed to be used immediately after config import.
 */
object PermissionSyncManager {

    private const val REQUEST_CODE_PERMISSION_SYNC = 9050

    enum class SettingsStep {
        OVERLAY,
        EXACT_ALARM
    }

    data class PermissionSyncPlan(
        val syncCore: Boolean,
        val runtimePermissionsToRequest: List<String>,
        val settingsStepsToOpen: List<SettingsStep>,
        val missingFeatureLabels: List<String>
    ) {
        fun hasAnyMissing(): Boolean =
            runtimePermissionsToRequest.isNotEmpty() || settingsStepsToOpen.isNotEmpty()
    }

    @JvmStatic
    fun startSync(
        activity: AppCompatActivity,
        syncCore: Boolean,
        importedRules: List<Rule>?,
        callback: Runnable
    ): PermissionSyncSession {
        val plan = buildPlan(activity, syncCore, importedRules)
        return PermissionSyncSession(
            activity = activity,
            requestCode = REQUEST_CODE_PERMISSION_SYNC,
            plan = plan,
            callback = callback
        ).also { it.start() }
    }

    @JvmStatic
    fun buildPlan(
        activity: AppCompatActivity,
        syncCore: Boolean,
        importedRules: List<Rule>?
    ): PermissionSyncPlan {
        val context = activity.applicationContext
        val missingLabels = mutableListOf<String>()
        val runtimePermissions = linkedSetOf<String>()
        val settingsSteps = linkedSetOf<SettingsStep>()

        // -----------------------------------------------------------------
        // Core features (phone state, POST_NOTIFICATIONS, summary overlay, ...)
        // -----------------------------------------------------------------
        if (syncCore) {
            val state = PermissionCatalog.loadCoreFeatureState(context)

            if (state.honourPhoneCallsEnabled && !PermissionCatalog.isReadPhoneStateGranted(context)) {
                runtimePermissions.add(Manifest.permission.READ_PHONE_STATE)
                missingLabels.add("Honour Phone Calls")
            }

            if (state.postNotificationsFeatureEnabled && !PermissionCatalog.isPostNotificationsGranted(context)) {
                runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                missingLabels.add("Notifications (POST_NOTIFICATIONS)")
            }

            if (state.updaterEnabled && !PermissionCatalog.isInstallPackagesGranted(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runtimePermissions.add(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                missingLabels.add("Self-updater (install packages)")
            }

            if (state.summaryOverlayEnabled && !PermissionCatalog.isOverlayGranted(context)) {
                settingsSteps.add(SettingsStep.OVERLAY)
                missingLabels.add("Summary overlay")
            }

            val exactNeeded = state.summarySchedulerEnabled || state.clockPrecisionModeEnabled
            if (exactNeeded && !PermissionCatalog.canScheduleExactAlarms(context)) {
                settingsSteps.add(SettingsStep.EXACT_ALARM)
                missingLabels.add("Exact alarms (Summary / Clock precision)")
            }
        }

        // -----------------------------------------------------------------
        // Conditional rules: WiFi & Bluetooth
        // -----------------------------------------------------------------
        if (importedRules != null && importedRules.isNotEmpty()) {
            val requiredTypes = RuleConfigManager.getRequiredPermissionTypes(importedRules)

            val sdkInt = Build.VERSION.SDK_INT
            if (requiredTypes.contains(RuleConfigManager.RulePermissionType.WIFI) &&
                !PermissionCatalog.hasAllWifiPermissions(context)
            ) {
                PermissionCatalog.getWifiPermissionsForSdk(sdkInt).forEach { perm ->
                    if (ActivityCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                        runtimePermissions.add(perm)
                    }
                }
                if (runtimePermissions.any { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.NEARBY_WIFI_DEVICES || it == Manifest.permission.ACCESS_BACKGROUND_LOCATION }) {
                    missingLabels.add("Wi-Fi rules (network + location)")
                }
            }

            if (requiredTypes.contains(RuleConfigManager.RulePermissionType.BLUETOOTH) &&
                !PermissionCatalog.hasBluetoothPermissions(context)
            ) {
                PermissionCatalog.getBluetoothPermissionsForSdk(sdkInt).forEach { perm ->
                    if (ActivityCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                        runtimePermissions.add(perm)
                    }
                }
                if (runtimePermissions.any { it == Manifest.permission.BLUETOOTH_CONNECT || it == Manifest.permission.BLUETOOTH_SCAN || it == Manifest.permission.BLUETOOTH || it == Manifest.permission.BLUETOOTH_ADMIN }) {
                    missingLabels.add("Bluetooth rules")
                }
            }
        }

        return PermissionSyncPlan(
            syncCore = syncCore,
            runtimePermissionsToRequest = runtimePermissions.toList(),
            settingsStepsToOpen = settingsSteps.toList(),
            missingFeatureLabels = missingLabels.distinct()
        )
    }
}

class PermissionSyncSession internal constructor(
    private val activity: AppCompatActivity,
    private val requestCode: Int,
    private val plan: PermissionSyncManager.PermissionSyncPlan,
    private val callback: Runnable
) {
    private val started = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)

    private var runtimePermissionsRequested = false
    private var settingsIndex = 0
    private var waitingForSettings = false

    fun isFinished(): Boolean = finished.get()

    fun start() {
        if (!started.compareAndSet(false, true)) return

        if (!plan.hasAnyMissing()) {
            finalizeSync()
            return
        }

        val message = buildString {
            append(activity.getString(R.string.permissions_required_message))
            append("\n\n")
            plan.missingFeatureLabels.forEach { label ->
                append("- ")
                append(label)
                append('\n')
            }
        }.trimEnd()

        AlertDialog.Builder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.permissions_required_enable) { _, _ ->
                beginPermissionRequests()
            }
            .setNegativeButton(R.string.permissions_required_not_now) { _, _ ->
                finalizeSync()
            }
            .setCancelable(true)
            .show()
    }

    private fun beginPermissionRequests() {
        if (plan.runtimePermissionsToRequest.isNotEmpty()) {
            runtimePermissionsRequested = true
            ActivityCompat.requestPermissions(
                activity,
                plan.runtimePermissionsToRequest.toTypedArray(),
                requestCode
            )
            return
        }

        if (plan.settingsStepsToOpen.isNotEmpty()) {
            openCurrentSettingsStep()
            return
        }

        finalizeSync()
    }

    private fun openCurrentSettingsStep() {
        while (settingsIndex < plan.settingsStepsToOpen.size) {
            val step = plan.settingsStepsToOpen[settingsIndex]
            val pkg = activity.packageName

            val shouldOpen = when (step) {
                PermissionSyncManager.SettingsStep.OVERLAY -> shouldOpenOverlayStep()
                PermissionSyncManager.SettingsStep.EXACT_ALARM -> shouldOpenExactAlarmStep()
            }

            if (!shouldOpen) {
                settingsIndex++
                continue
            }

            waitingForSettings = true
            when (step) {
                PermissionSyncManager.SettingsStep.OVERLAY -> {
                    val intent = Settings.ACTION_MANAGE_OVERLAY_PERMISSION.let { action ->
                        android.content.Intent(action, android.net.Uri.parse("package:$pkg"))
                    }
                    activity.startActivity(intent)
                    return
                }

                PermissionSyncManager.SettingsStep.EXACT_ALARM -> {
                    val intent = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM.let { action ->
                        android.content.Intent(action, android.net.Uri.parse("package:$pkg"))
                    }
                    activity.startActivity(intent)
                    return
                }
            }
        }

        finalizeSync()
    }

    private fun shouldOpenOverlayStep(): Boolean {
        val state = PermissionCatalog.loadCoreFeatureState(activity)
        return state.summaryOverlayEnabled && !PermissionCatalog.isOverlayGranted(activity)
    }

    private fun shouldOpenExactAlarmStep(): Boolean {
        if (PermissionCatalog.canScheduleExactAlarms(activity)) return false
        val state = PermissionCatalog.loadCoreFeatureState(activity)
        val overlayGranted = PermissionCatalog.isOverlayGranted(activity)

        val summaryNeedsExact = state.summarySchedulerEnabled && overlayGranted
        val clockNeedsExact = state.clockPrecisionModeEnabled
        return summaryNeedsExact || clockNeedsExact
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (finished.get()) return
        if (requestCode != this.requestCode) return
        if (!runtimePermissionsRequested) return

        // We don't need the exact grant map: reconciliation + rule filtering uses current
        // "isGranted" checks after the flow completes.
        runtimePermissionsRequested = false

        waitingForSettings = false
        settingsIndex = 0

        if (plan.settingsStepsToOpen.isNotEmpty()) {
            openCurrentSettingsStep()
        } else {
            finalizeSync()
        }
    }

    fun onResume() {
        if (finished.get()) return
        if (!waitingForSettings) return

        waitingForSettings = false
        settingsIndex++

        if (settingsIndex >= plan.settingsStepsToOpen.size) {
            finalizeSync()
        } else {
            openCurrentSettingsStep()
        }
    }

    private fun finalizeSync() {
        if (!finished.compareAndSet(false, true)) return

        if (plan.syncCore) {
            PermissionCatalog.reconcileCoreFeaturesIfNeeded(activity)
        }

        callback.run()
    }
}

