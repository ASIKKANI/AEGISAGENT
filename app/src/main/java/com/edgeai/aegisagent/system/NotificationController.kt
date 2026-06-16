package com.edgeai.aegisagent.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controller class executing Android Notification pushes and Coroutine-based background alarms.
 */
class NotificationController(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationIdCounter = AtomicInteger(100)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "aegis_os_notifications"
        private const val CHANNEL_NAME = "AegisAgent Alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and timers triggered by Aegis agent"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Send an immediate OS push notification.
     */
    fun showNotification(title: String, message: String) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(notificationIdCounter.incrementAndGet(), builder.build())
            Log.d("NotificationController", "Notification pushed: '$title' - '$message'")
        } catch (e: Exception) {
            Log.e("NotificationController", "Failed to push notification: ${e.message}")
        }
    }

    /**
     * Set a background alarm/reminder that triggers a notification after a set delay.
     */
    fun scheduleReminder(title: String, delayMinutes: Int): String {
        val delayMillis = delayMinutes * 60 * 1000L
        scope.launch {
            Log.d("NotificationController", "Reminder '$title' scheduled in $delayMinutes min")
            delay(delayMillis)
            showNotification(
                title = "Focus Timer Alert ⏰",
                message = "Time's up for your task: '$title'!"
            )
        }
        return "Reminder for '$title' set to trigger in $delayMinutes minutes."
    }

    /**
     * Formats and logs custom calendar additions.
     */
    fun scheduleCalendarMeeting(title: String, date: String, time: String): String {
        Log.d("NotificationController", "Calendar Meeting added: '$title' on $date at $time")
        showNotification(
            title = "Calendar Event Scheduled 📅",
            message = "'$title' has been planned for $date at $time."
        )
        return "Meeting '$title' successfully scheduled for $date at $time."
    }
}
