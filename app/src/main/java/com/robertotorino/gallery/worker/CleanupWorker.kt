package com.robertotorino.gallery.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.robertotorino.gallery.data.GalleryDatabase
import com.robertotorino.gallery.data.RecycledItem
import com.robertotorino.gallery.repository.MediaRepository
import java.util.concurrent.TimeUnit

class CleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val database = GalleryDatabase.getDatabase(context)
    private val dao = database.recycledItemDao()
    private val repository = MediaRepository(context, dao)
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        notificationHelper.createNotificationChannel()

        val prefs = applicationContext.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
        val recycleBinDays = prefs.getInt("recycle_bin_days", 30)
        val archiveAfter30Days = prefs.getBoolean("archive_after_30_days", false)

        val now = System.currentTimeMillis()
        val allItems = dao.getAll()

        var deletedCount = 0
        var archivedCount = 0
        
        val toUpdate = mutableListOf<RecycledItem>()
        
        // Notification counts
        var bin24h = 0
        var bin12h = 0
        var bin1h = 0

        allItems.forEach { item ->
            // Bin items (isArchived=false) threshold depends on settings
            // Archive items (isArchived=true) are kept forever (no auto-deletion)
            if (item.isArchived) return@forEach

            val thresholdDays = if (archiveAfter30Days) 30 else recycleBinDays
            val thresholdMillis = TimeUnit.DAYS.toMillis(thresholdDays.toLong())
            val expiryTime = item.deletedTimestamp + thresholdMillis
            val remainingMillis = expiryTime - now

            if (remainingMillis <= 0) {
                // Perform final action
                if (archiveAfter30Days) {
                    // Move to archive from bin
                    if (repository.moveRecycledToArchive(item)) {
                        archivedCount++
                    }
                } else {
                    // Permanently delete from bin
                    if (repository.permanentlyDeleteItem(item)) {
                        deletedCount++
                    }
                }
            } else {
                // Check for notifications (24h, 12h, 1h before)
                val hoursRemaining = TimeUnit.MILLISECONDS.toHours(remainingMillis).toInt()
                
                var updatedItem = item
                var modified = false

                // We notify 24h, 12h, and 1h before the specific item's threshold
                if (hoursRemaining <= 1 && !item.notified1h) {
                    updatedItem = updatedItem.copy(notified1h = true)
                    bin1h++
                    modified = true
                } else if (hoursRemaining <= 12 && !item.notified12h) {
                    updatedItem = updatedItem.copy(notified12h = true)
                    bin12h++
                    modified = true
                } else if (hoursRemaining <= 24 && !item.notified24h) {
                    updatedItem = updatedItem.copy(notified24h = true)
                    bin24h++
                    modified = true
                }

                if (modified) {
                    toUpdate.add(updatedItem)
                }
            }
        }

        // Send notifications for items reaching thresholds
        if (bin24h > 0) notificationHelper.sendPreCleanupNotification(bin24h, archiveAfter30Days, 24)
        if (bin12h > 0) notificationHelper.sendPreCleanupNotification(bin12h, archiveAfter30Days, 12)
        if (bin1h > 0) notificationHelper.sendPreCleanupNotification(bin1h, archiveAfter30Days, 1)

        // Update notification flags in database
        toUpdate.forEach { dao.update(it) }

        // Send final results notifications
        if (deletedCount > 0) {
            notificationHelper.sendFinalNotification(deletedCount, false)
        }
        if (archivedCount > 0) {
            notificationHelper.sendFinalNotification(archivedCount, true)
        }

        return Result.success()
    }
}
