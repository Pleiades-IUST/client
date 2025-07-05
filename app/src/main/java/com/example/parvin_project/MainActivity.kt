// MainActivity.kt
package com.example.parvin_project

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.transition.TransitionManager


class MainActivity : AppCompatActivity() {

    private var isRecordingServiceRunning: Boolean = false // Track service state
    private lateinit var infoTextView: TextView
    private lateinit var toggleButton: Button
    private lateinit var copyButton: Button
    private lateinit var rootLayout: ViewGroup
    private lateinit var captureDownloadRateCheckBox: CheckBox
    private lateinit var capturePingTestCheckBox: CheckBox
    private lateinit var captureSmsTestCheckBox: CheckBox
    private lateinit var captureDnsTestCheckBox: CheckBox
    private lateinit var captureUploadRateCheckBox: CheckBox

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var localBroadcastManager: LocalBroadcastManager // For receiving updates from service

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 102
        private const val FGS_LOCATION_REQUEST_CODE = 103   // ← new
    }



    // BroadcastReceiver to get updates from the service
    private val serviceDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ServiceConstants.ACTION_UPDATE_UI -> {
                    val liveData = intent.getStringExtra(ServiceConstants.EXTRA_LIVE_DATA)
                    liveData?.let {
                        TransitionManager.beginDelayedTransition(rootLayout)
                        infoTextView.text = it // Directly display formatted live data
                    }
                }
                ServiceConstants.ACTION_RECEIVE_FULL_LOGS -> { // This action now carries API upload status
                    val apiStatusMessage = intent.getStringExtra(ServiceConstants.EXTRA_FULL_LOGS)
                    apiStatusMessage?.let {
                        TransitionManager.beginDelayedTransition(rootLayout)
                        infoTextView.text = it // Display API upload status/error
                        Toast.makeText(context, "API Upload Status Received!", Toast.LENGTH_SHORT).show()
                        // After upload, revert button state to START
                        updateToggleButtonState(false) // Set to STOPPED state
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // HIDE THE ACTION BAR (TITLE BAR)
        supportActionBar?.hide() // ADDED THIS LINE

        // Initialize UI elements
        infoTextView = findViewById(R.id.infoTextView)
        toggleButton = findViewById(R.id.toggleButton)
        copyButton = findViewById(R.id.copyButton)
        rootLayout = findViewById(R.id.rootLayout)
        captureDownloadRateCheckBox = findViewById(R.id.captureDownloadRateCheckBox)
        capturePingTestCheckBox = findViewById(R.id.capturePingTestCheckBox)
        captureSmsTestCheckBox = findViewById(R.id.captureSmsTestCheckBox)
        captureDnsTestCheckBox = findViewById(R.id.captureDnsTestCheckBox)
        captureUploadRateCheckBox = findViewById(R.id.captureUploadRateCheckBox)

        permissionHandler = PermissionHandler(this, PERMISSION_REQUEST_CODE)
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        // Set initial state of checkboxes and their listeners
        captureDownloadRateCheckBox.isChecked = true
        capturePingTestCheckBox.isChecked = true
        captureSmsTestCheckBox.isChecked = true
        captureDnsTestCheckBox.isChecked = true
        captureUploadRateCheckBox.isChecked = false

        captureDownloadRateCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        capturePingTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureSmsTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureDnsTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureUploadRateCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }


        // Initial UI State
        TransitionManager.beginDelayedTransition(rootLayout)
        updateInfoTextViewHint()
        updateToggleButtonState(false) // Set initial button state to STOPPED

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            if (isRecordingServiceRunning) {
                // STOP logic: Just tell the service to stop. It will then send the full logs.
                stopRecordingService()
                updateToggleButtonState(false)
                // infoTextView will be updated by the ACTION_RECEIVE_FULL_LOGS broadcast
            } else {
                // START logic:

                // Request POST_NOTIFICATIONS for Android 13+ first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.FOREGROUND_SERVICE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // request it, then return—wait for callback
                        requestPermissions(
                            arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION),
                            FGS_LOCATION_REQUEST_CODE
                        )
                        return@setOnClickListener
                    }
                }

                // Proceed with other permissions after notification permission or if not needed
                if (permissionHandler.checkAndRequestPermissions()) {
                    startRecordingService()
                    updateToggleButtonState(true)
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Starting data collection service... Please wait for first live update."
                    Toast.makeText(this, "Recording started. See persistent notification.", Toast.LENGTH_LONG).show() // New Toast
                } else {
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Requesting permissions... Please grant all required permissions to proceed."
                    Log.w("MainActivity", "Permissions were requested. Waiting for user response.")
                }
            }
        }

        // Set click listener for the copy button
        copyButton.setOnClickListener {
            copyTextToClipboard(infoTextView.text.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        // Register BroadcastReceiver to receive updates from the service
        val filter = IntentFilter().apply {
            addAction(ServiceConstants.ACTION_UPDATE_UI)
            addAction(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
        }
        localBroadcastManager.registerReceiver(serviceDataReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // Unregister BroadcastReceiver when activity is no longer visible
        localBroadcastManager.unregisterReceiver(serviceDataReceiver)
    }

    override fun onPause() {
        super.onPause()
        // Inform user that the activity is paused but service may continue
        if (isRecordingServiceRunning) {
            Toast.makeText(this, "App in background, recording continues via notification.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No explicit stopService call here, as the service should manage its lifecycle (START_STICKY)
        // and ideally, the user explicitly stops it via the button.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (permissionHandler.handlePermissionsResult(requestCode, grantResults)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), POST_NOTIFICATIONS_REQUEST_CODE)
                        return
                    }
                }
                startRecordingService()
                updateToggleButtonState(true)
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Permissions granted. Starting data collection service... Data will be uploaded to API on STOP." // Updated message
                Toast.makeText(this, "Recording started. See persistent notification.", Toast.LENGTH_LONG).show()
            } else {
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Not all required permissions were granted. Cannot start service. Please grant them in app settings."
                updateToggleButtonState(false)
            }
        } else if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            // ... (keep this part as is) ...
            startRecordingService()
            updateToggleButtonState(true)
            TransitionManager.beginDelayedTransition(rootLayout)
            infoTextView.text = "Notification permission granted. Starting data collection service... Data will be uploaded to API on STOP." // Updated message
            Toast.makeText(this, "Recording started. See persistent notification.", Toast.LENGTH_LONG).show()
        } else if (requestCode == FGS_LOCATION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Now that FGS_LOCATION is granted, restart the flow:
                startRecordingService()
                updateToggleButtonState(true)
                Toast.makeText(this, "Service‑location permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                // User denied—the service can’t start with location type
                Toast.makeText(this, "Cannot start service without location‑type permission.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
            action = ServiceConstants.ACTION_START_RECORDING
            putExtra("shouldCaptureDownloadRate", captureDownloadRateCheckBox.isChecked)
            putExtra("shouldCapturePingTest", capturePingTestCheckBox.isChecked)
            putExtra("shouldCaptureSmsTest", captureSmsTestCheckBox.isChecked)
            putExtra("shouldCaptureDnsTest", captureDnsTestCheckBox.isChecked)
            putExtra("shouldCaptureUploadRate", captureUploadRateCheckBox.isChecked)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        isRecordingServiceRunning = true
        Log.d("MainActivity", "Recording service START command sent.")
    }

    private fun stopRecordingService() {
        val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
            action = ServiceConstants.ACTION_STOP_RECORDING
        }
        startService(serviceIntent)
        isRecordingServiceRunning = false
        Log.d("MainActivity", "Recording service STOP command sent to service.")
    }

    private fun updateToggleButtonState(isServiceRunning: Boolean) {
        if (isServiceRunning) {
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
            toggleButton.text = "STOP (Recording)"
        } else {
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
            toggleButton.text = "START"
        }
        this.isRecordingServiceRunning = isServiceRunning
    }

    /**
     * Helper function to copy text to the clipboard.
     */
    private fun copyTextToClipboard(text: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Network Logs", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Updates the initial hint text based on selected checkboxes.
     */
    private fun updateInfoTextViewHint() {
        val downloadPart = if (captureDownloadRateCheckBox.isChecked) "Download" else ""
        val pingPart = if (capturePingTestCheckBox.isChecked) "Ping" else ""
        val smsPart = if (captureSmsTestCheckBox.isChecked) "SMS" else ""
        val dnsPart = if (captureDnsTestCheckBox.isChecked) "DNS" else ""
        val uploadPart = if (captureUploadRateCheckBox.isChecked) "Upload" else ""
        val networkTests = listOf(downloadPart, pingPart, smsPart, dnsPart, uploadPart).filter { it.isNotEmpty() }.joinToString(" & ")

        infoTextView.text = "Press START to begin recording network, cell, and location data.\n" +
                "Network tests to capture: ${networkTests.ifEmpty { "None" }}."
    }
}
