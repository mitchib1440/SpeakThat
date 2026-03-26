package com.micoyc.speakthat.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCatalogTest {

    @Test
    fun `wifi permissions for sdk 33 include nearby and background`() {
        val perms = PermissionCatalog.getWifiPermissionsForSdk(33)
        assertTrue(perms.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test
    fun `wifi permissions for sdk 30 include fine and background but not nearby`() {
        val perms = PermissionCatalog.getWifiPermissionsForSdk(30)
        assertFalse(perms.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test
    fun `wifi permissions for sdk 28 include only fine`() {
        val perms = PermissionCatalog.getWifiPermissionsForSdk(28)
        assertFalse(perms.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test
    fun `bluetooth permissions for sdk below S use classic bluetooth permissions`() {
        val perms = PermissionCatalog.getBluetoothPermissionsForSdk(30)
        assertEquals(listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN), perms)
    }

    @Test
    fun `bluetooth permissions for sdk S and above use connect and scan`() {
        val perms = PermissionCatalog.getBluetoothPermissionsForSdk(33)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), perms)
    }
}

