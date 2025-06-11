// PermissionHandler.kt
package com.example.parvin_project

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHandler(private val activity: Activity, private val requestCode: Int) {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        // Manifest.permission.READ_CELL_BROADCASTS is not a standard dangerous permission, removed from here.
        Manifest.permission.INTERNET, // Considered normal, usually granted by default if manifest-only
        Manifest.permission.ACCESS_NETWORK_STATE, // Considered normal
        Manifest.permission.ACCESS_WIFI_STATE, // Considered normal
        Manifest.permission.POST_NOTIFICATIONS // For Android 13+ (API 33) notifications
    ).apply {
        // Add FOREGROUND_SERVICE_LOCATION for Android 14+ (API 34) if targeting it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }.toTypedArray()


    /**
     * Checks if all required permissions are granted. If not, requests them.
     * @return true if all permissions are already granted, false otherwise.
     */
    fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                requestCode
            )
            return false // Permissions were requested
        }
        return true // All permissions already granted
    }

    /**
     * Handles the result of the permission request.
     * @param requestCode The request code passed in checkAndRequestPermissions.
     * @param grantResults The grant results for the corresponding permissions.
     * @return true if all permissions were granted, false otherwise.
     */
    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == this.requestCode) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            return allGranted
        }
        return false
    }
}
