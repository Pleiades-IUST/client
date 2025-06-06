// SmsTester.kt
package com.example.parvin_project

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build // Import Build class
import android.telephony.SmsManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap // To safely store send times

/**
 * Handles sending SMS messages and tracking their delivery times.
 * This class uses BroadcastReceivers to get SENT and DELIVERED reports.
 *
 * IMPORTANT: SMS sending can incur charges. Ensure 'recipientPhoneNumber'
 * is a number you control for testing purposes.
 *
 * @param context The application context (ideally Activity context for BroadcastReceiver registration).
 * @param onSmsDeliveryResult A callback function to receive the messageId and delivery time in milliseconds.
 */
class SmsTester(private val context: Context, private val onSmsDeliveryResult: (messageId: String, deliveryTimeMs: Double?) -> Unit) { // Callback includes messageId

    // IMPORTANT: Replace with a valid phone number you control for testing.
    // Sending SMS can incur charges.
    private val RECIPIENT_PHONE_NUMBER = "+989124904530" // Using +989124904530 as the common emulator/test number. Adjust if needed.

    private val SENT_SMS_ACTION = "SMS_SENT_ACTION_PARVIN" // Made action unique to avoid conflicts
    private val DELIVERED_SMS_ACTION = "SMS_DELIVERED_ACTION_PARVIN" // Made action unique
    private val SMS_MESSAGE_ID_EXTRA = "sms_message_id"

    // Map to store the send time (nanoTime) of each message, keyed by its unique ID
    private val sentMessageTimestamps = ConcurrentHashMap<String, Long>()

    // BroadcastReceiver for when the SMS is sent from the device
    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val messageId = intent?.getStringExtra(SMS_MESSAGE_ID_EXTRA) ?: return
            val resultCode = resultCode

            when (resultCode) {
                Activity.RESULT_OK -> Log.d("SmsTester", "SMS with ID $messageId sent successfully by device.")
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Log.e("SmsTester", "SMS with ID $messageId generic failure (check signal, balance, etc.).")
                SmsManager.RESULT_ERROR_NO_SERVICE -> Log.e("SmsTester", "SMS with ID $messageId no service (network unavailable or airplane mode).")
                SmsManager.RESULT_ERROR_NULL_PDU -> Log.e("SmsTester", "SMS with ID $messageId null PDU (invalid SMS format/length).")
                SmsManager.RESULT_ERROR_RADIO_OFF -> Log.e("SmsTester", "SMS with ID $messageId radio off (device radio/SIM is off).")
                else -> Log.e("SmsTester", "SMS with ID $messageId unknown error code on SENT: $resultCode")
            }
            // If SENT fails, immediately inform MainActivity that delivery failed
            if (resultCode != Activity.RESULT_OK) {
                sentMessageTimestamps.remove(messageId) // Clean up pending entry
                onSmsDeliveryResult(messageId, null) // Corrected: Call onSmsDeliveryResult
            }
        }
    }

    // BroadcastReceiver for when the SMS is delivered to the recipient
    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val messageId = intent?.getStringExtra(SMS_MESSAGE_ID_EXTRA) ?: return
            val sendTimeNs = sentMessageTimestamps.remove(messageId) // Remove after processing
            if (sendTimeNs == null) {
                Log.w("SmsTester", "Delivery report for unknown SMS ID: $messageId. Or sent time missing.")
                onSmsDeliveryResult(messageId, null) // Still report failure if no sendTime, use messageId
                return
            }

            val deliveryTimeNs = System.nanoTime()
            val durationNs = deliveryTimeNs - sendTimeNs
            val durationMs = durationNs / 1_000_000.0 // Convert nanoseconds to milliseconds

            val resultCode = resultCode // This is a member of BroadcastReceiver

            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d("SmsTester", "SMS with ID $messageId delivered successfully in ${String.format("%.2f", durationMs)} ms.")
                    onSmsDeliveryResult(messageId, durationMs) // Corrected: Call onSmsDeliveryResult
                }
                else -> {
                    Log.e("SmsTester", "SMS with ID $messageId delivery failed. Result code: $resultCode. Took ${String.format("%.2f", durationMs)} ms until failed status.")
                    onSmsDeliveryResult(messageId, null) // Corrected: Call onSmsDeliveryResult, indicate failure
                }
            }
        }
    }

    /**
     * Registers the BroadcastReceivers. Call this in onCreate or onResume.
     */
    fun registerReceivers() {
        try {
            context.registerReceiver(smsSentReceiver, IntentFilter(SENT_SMS_ACTION))
            context.registerReceiver(smsDeliveredReceiver, IntentFilter(DELIVERED_SMS_ACTION))
            Log.d("SmsTester", "SMS BroadcastReceivers registered.")
        } catch (e: Exception) {
            Log.e("SmsTester", "Error registering SMS receivers: ${e.message}", e)
        }
    }

    /**
     * Unregisters the BroadcastReceivers. Call this in onDestroy or onPause.
     */
    fun unregisterReceivers() {
        try {
            context.unregisterReceiver(smsSentReceiver)
            context.unregisterReceiver(smsDeliveredReceiver)
            Log.d("SmsTester", "SMS BroadcastReceivers unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.e("SmsTester", "Receivers already unregistered or not registered: ${e.message}. This is normal during destruction.")
        } catch (e: Exception) {
            Log.e("SmsTester", "Unexpected error unregistering SMS receivers: ${e.message}", e)
        }
    }

    /**
     * Sends an SMS message and starts tracking its delivery time.
     * This function should be called from a background (IO) thread.
     * @param message The content of the SMS message.
     */
    fun sendSmsAndTrackDelivery(message: String) {
        if (RECIPIENT_PHONE_NUMBER.isBlank() || (!RECIPIENT_PHONE_NUMBER.startsWith("+") && RECIPIENT_PHONE_NUMBER != "+989124904530")) {
            Log.e("SmsTester", "RECIPIENT_PHONE_NUMBER not set or invalid in SmsTester.kt. Please update it with a valid international format (e.g., +447911123456) or '+989124904530' for emulator. SMS NOT SENT.")
            onSmsDeliveryResult("INVALID_NUMBER", null) // Report failure with a dummy ID
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        if (smsManager == null) {
            Log.e("SmsTester", "SmsManager is NULL. Device likely does not support telephony (e.g., Wi-Fi tablet or emulator without phone features). SMS NOT SENT.")
            onSmsDeliveryResult("SMS_MANAGER_NULL", null) // Report failure
            return
        }

        val messageId = System.currentTimeMillis().toString() + "_" + (0..999).random() // Unique ID for this message

        val sentIntent = Intent(SENT_SMS_ACTION).apply {
            putExtra(SMS_MESSAGE_ID_EXTRA, messageId)
        }
        val deliveredIntent = Intent(DELIVERED_SMS_ACTION).apply {
            putExtra(SMS_MESSAGE_ID_EXTRA, messageId)
        }

        // FLAG_IMMUTABLE is required for Android 12+ (API 31+). FLAG_UPDATE_CURRENT for updating extras.
        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val sentPI: PendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.hashCode(), // Use hashcode as request code (needs to be unique per PendingIntent)
            sentIntent,
            pendingFlags
        )
        val deliveredPI: PendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.hashCode() + 1, // Ensure different request code for delivered PI
            deliveredIntent,
            pendingFlags
        )

        sentMessageTimestamps[messageId] = System.nanoTime() // Record the precise time SMS was sent

        try {
            smsManager.sendTextMessage(
                RECIPIENT_PHONE_NUMBER,
                null, // SC Address (null for default SMSC)
                message,
                sentPI,
                deliveredPI
            )
            Log.d("SmsTester", "SMS sending initiated for ID: $messageId to $RECIPIENT_PHONE_NUMBER")
        } catch (e: SecurityException) {
            Log.e("SmsTester", "SecurityException: SMS permission not granted or revoked. Ensure SEND_SMS is granted. ${e.message}", e)
            sentMessageTimestamps.remove(messageId) // Clean up
            onSmsDeliveryResult(messageId, null) // Report failure
        } catch (e: IllegalArgumentException) {
            Log.e("SmsTester", "IllegalArgumentException: Invalid phone number format or message. ${e.message}", e)
            sentMessageTimestamps.remove(messageId) // Clean up
            onSmsDeliveryResult(messageId, null) // Report failure
        } catch (e: Exception) {
            Log.e("SmsTester", "General Exception during SMS sending for ID $messageId: ${e.message}", e)
            sentMessageTimestamps.remove(messageId) // Clean up
            onSmsDeliveryResult(messageId, null) // Report failure
        }
    }
}
