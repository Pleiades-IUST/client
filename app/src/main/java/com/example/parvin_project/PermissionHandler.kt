// PermissionHandler.kt
package com.example.parvin_project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity // Needed for ActivityCompat.requestPermissions

/**
 * Handles runtime permissions for the application.
 * @param activity The AppCompatActivity context, used for requesting permissions.
 * @param requestCode A unique request code for permission requests.
 */
class PermissionHandler(private val activity: AppCompatActivity, private val requestCode: Int) {

    // Define all required permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    /**
     * Checks if all necessary permissions are granted. If not, requests them.
     * @return true if all permissions are already granted, false if requests were made.
     */
    fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), requestCode)
            return false // Permissions were requested, not yet granted
        }
        return true // All permissions are already granted
    }

    /**
     * Checks if all necessary permissions are granted WITHOUT requesting them.
     * Useful for periodic checks within background tasks.
     * @return true if all permissions are granted, false otherwise.
     */
    fun checkPermissionsWithoutRequest(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Handles the result of a permission request.
     * @param incomingRequestCode The request code passed to onRequestPermissionsResult.
     * @param grantResults The results for the requested permissions.
     * @return true if all permissions were granted for this request, false otherwise.
     */
    fun handlePermissionsResult(incomingRequestCode: Int, grantResults: IntArray): Boolean {
        if (incomingRequestCode == requestCode) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            return allPermissionsGranted
        }
        return false // Not our request code
    }
}
