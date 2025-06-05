// PermissionHandler.kt
package com.example.parvin_project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles runtime permissions for the application.
 * It checks for required permissions and requests them if not granted.
 */
class PermissionHandler(private val activity: ComponentActivity, private val requestCode: Int) {

    // Define all permissions required by the app, including SMS.
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS,      // Added SMS sending permission
        Manifest.permission.READ_SMS,      // Added SMS reading permission (for delivery reports)
        Manifest.permission.RECEIVE_SMS    // Added SMS receiving permission (for delivery reports)
    )

    /**
     * Checks if all required permissions are granted.
     * @return true if all permissions are granted, false otherwise.
     */
    fun checkPermissionsWithoutRequest(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }.also { granted ->
            if (!granted) {
                Log.w("PermissionHandler", "Not all required permissions are granted: ${
                    REQUIRED_PERMISSIONS.filter {
                        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                    }.joinToString(", ")
                }")
            }
        }
    }

    /**
     * Checks if all required permissions are granted. If not, it requests them.
     * @return true if all permissions are already granted, false if a request was made.
     */
    fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PermissionHandler", "Requesting permissions: ${permissionsToRequest.joinToString(", ")}")
            ActivityCompat.requestPermissions(activity, permissionsToRequest, requestCode)
            return false // Permissions were requested, not all are granted yet.
        }
        Log.d("PermissionHandler", "All required permissions already granted.")
        return true // All permissions are already granted.
    }

    /**
     * Handles the result of a permission request.
     * Call this from your Activity's onRequestPermissionsResult method.
     * @param requestCode The request code passed to requestPermissions.
     * @param grantResults The grant results for the corresponding permissions.
     * @return true if all permissions in REQUIRED_PERMISSIONS are now granted, false otherwise.
     */
    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == this.requestCode) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d("PermissionHandler", "All requested permissions granted by user.")
            } else {
                val deniedPermissions = mutableListOf<String>()
                for (i in REQUIRED_PERMISSIONS.indices) {
                    if (i < grantResults.size && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(REQUIRED_PERMISSIONS[i])
                    }
                }
                Log.w("PermissionHandler", "Some permissions denied by user: ${deniedPermissions.joinToString(", ")}")
            }
            return allGranted
        }
        return false
    }
}
