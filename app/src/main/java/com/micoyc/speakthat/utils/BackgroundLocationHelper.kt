package com.micoyc.speakthat.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.R

/**
 * Centralised helper for the two-step background-location permission flow
 * required by Android 11+ for WiFi SSID detection while running in the background.
 */
object BackgroundLocationHelper {

    private const val TAG = "BackgroundLocationHelper"

    // ------------------------------------------------------------------
    // Permission checks
    // ------------------------------------------------------------------

    fun hasForegroundLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNearbyWifiPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when foreground location, background location, and (API 33+)
     * NEARBY_WIFI_DEVICES are all granted.
     */
    fun hasAllWifiPermissions(context: Context): Boolean {
        return hasForegroundLocationPermission(context)
                && hasNearbyWifiPermission(context)
                && hasBackgroundLocationPermission(context)
    }

    // ------------------------------------------------------------------
    // Foreground permission list (Step 1)
    // ------------------------------------------------------------------

    fun getForegroundWifiPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // API 30-32: foreground only; background must be requested separately
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // API 29: bundle background with foreground so the system dialog
                // shows "Allow all the time" in a single step
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
            else -> {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // ------------------------------------------------------------------
    // Disclosure dialog (Step 2 – before opening Settings)
    // ------------------------------------------------------------------

    /**
     * Shows a Google-Play-compliant prominent disclosure explaining why
     * background location is needed, then calls [onAccepted] or [onDeclined].
     */
    fun showBackgroundLocationDisclosure(
        activity: Activity,
        onAccepted: () -> Unit,
        onDeclined: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.background_location_disclosure_title))
            .setMessage(activity.getString(R.string.background_location_disclosure_message))
            .setPositiveButton(activity.getString(R.string.background_location_disclosure_accept)) { _, _ ->
                onAccepted()
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                onDeclined()
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------
    // Request background location permission (Step 2)
    // ------------------------------------------------------------------

    fun requestBackgroundLocation(activity: Activity, requestCode: Int) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // API 30+: request background alone — the system opens the
                // location-permission page with "Allow all the time" visible
                InAppLogger.logDebug(TAG, "Requesting ACCESS_BACKGROUND_LOCATION via system dialog (API 30+)")
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    requestCode
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // API 29: requesting background alone is unreliable on some OEMs.
                // Request both foreground + background together so the system shows
                // a single dialog with "Allow all the time".
                InAppLogger.logDebug(TAG, "Requesting foreground + background location together (API 29)")
                activity.requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ),
                    requestCode
                )
            }
            else -> {
                InAppLogger.logDebug(TAG, "Falling back to app settings page for background location")
                openAppSettings(activity)
            }
        }
    }

    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
