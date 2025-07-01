// PermissionHandler.kt
package com.example.parvin_project

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHandler(private val activity: Activity, private val requestCode: Int) {

    // Define all permissions your app might need here
    private val appPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        // SMS permissions only needed if SMS test is enabled and used directly by app
        // For background service, permissions are checked when service is started.
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.INTERNET, // INTERNET is usually granted by default, but good to include
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION
        // No need to include POST_NOTIFICATIONS here, as it's handled separately for API 33+
    )

    /**
     * Checks if all required permissions are granted.
     * @return true if all permissions are granted, false otherwise.
     */
    fun checkPermissionsWithoutRequest(): Boolean {
        return appPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if all required permissions are granted, and requests them if not.
     * @return true if all permissions are currently granted, false if request was made.
     */
    fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = appPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest, requestCode)
            return false // Permissions were requested, not all are granted yet
        }
        return true // All permissions already granted
    }

    /**
     * Handles the result of a permission request.
     * Call this from your Activity's onRequestPermissionsResult method.
     * @param requestCode The request code passed to requestPermissions.
     * @param grantResults The grant results for the corresponding permissions.
     * @return true if all permissions for this handler's request code are granted, false otherwise.
     */
    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == this.requestCode) {
            return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }
        return false // Not our request code
    }
}
