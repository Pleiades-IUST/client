// ServiceConstants.kt
package com.example.parvin_project

object ServiceConstants {
    const val NOTIFICATION_CHANNEL_ID = "NetworkMonitorChannel"
    const val NOTIFICATION_CHANNEL_NAME = "Network Monitor Service"
    const val NOTIFICATION_ID = 101 // Unique ID for the foreground service notification

    const val ACTION_START_RECORDING = "com.example.parvin_project.ACTION_START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.example.parvin_project.ACTION_STOP_RECORDING"
    const val ACTION_UPDATE_UI = "com.example.parvin_project.ACTION_UPDATE_UI"
    const val ACTION_REQUEST_FULL_LOGS = "com.example.parvin_project.ACTION_REQUEST_FULL_LOGS"
    const val ACTION_RECEIVE_FULL_LOGS = "com.example.parvin_project.ACTION_RECEIVE_FULL_LOGS"

    const val EXTRA_LIVE_DATA = "live_data"
    const val EXTRA_FULL_LOGS = "full_logs"
}
