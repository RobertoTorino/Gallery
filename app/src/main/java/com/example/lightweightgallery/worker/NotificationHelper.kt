package com.robertotorino.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.robertotorino.gallery.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "cleanup_notifications"
        const val CHANNEL_NAME = "Cleanup Tasks"
        const val NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for automatic picture cleanup"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPreCleanupNotification(count: Int, isArchiving: Boolean, hoursRemaining: Int) {
        val action = if (isArchiving) "archived" else "deleted"
        val title = "Pictures will be $action soon"
        val text = "$count picture${if (count > 1) "s" else ""} will be $action after $hoursRemaining hour${if (hoursRemaining > 1) "s" else ""}."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + hoursRemaining, notification)
    }

    fun sendFinalNotification(count: Int, isArchived: Boolean) {
        val action = if (isArchived) "moved to archive" else "permanently removed"
        val title = "Cleanup Complete"
        val text = "$count picture${if (count > 1) "s" else ""} ${if (count > 1) "were" else "was"} $action!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 100, notification)
    }
}
