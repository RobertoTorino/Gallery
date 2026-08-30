package com.robertotorino.gallery.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.robertotorino.gallery.data.MediaItem
import com.robertotorino.gallery.data.RecycledItem
import com.robertotorino.gallery.data.RecycledItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MediaRepository(
    private val context: Context,
    private val recycledItemDao: RecycledItemDao
) {

    init {
        createNoMediaFile()
    }

    private fun createNoMediaFile() {
        listOf("RecycleBin", "Archive").forEach { dirName ->
            context.getExternalFilesDir(dirName)?.let { dir ->
                val noMedia = File(dir, ".nomedia")
                if (!noMedia.exists()) {
                    try {
                        noMedia.createNewFile()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private val recycleBinDir: File? by lazy {
        context.getExternalFilesDir("RecycleBin")
    }

    private val archiveDir: File? by lazy {
        context.getExternalFilesDir("Archive")
    }

    suspend fun copyToRecycleBin(item: MediaItem): RecycledItem? = withContext(Dispatchers.IO) {
        val targetDir = recycleBinDir ?: return@withContext null
        copyToFolder(item, targetDir, isArchived = false)
    }

    suspend fun copyToArchive(item: MediaItem): RecycledItem? = withContext(Dispatchers.IO) {
        val targetDir = archiveDir ?: return@withContext null
        copyToFolder(item, targetDir, isArchived = true)
    }

    private fun copyToFolder(item: MediaItem, targetDir: File?, isArchived: Boolean): RecycledItem? {
        if (targetDir == null) return null
        if (!targetDir.exists()) targetDir.mkdirs()

        var targetFile = File(targetDir, item.displayName)
        if (targetFile.exists()) {
            val nameWithoutExtension = targetFile.nameWithoutExtension
            val extension = targetFile.extension
            val timestamp = System.currentTimeMillis()
            targetFile = File(targetDir, "${nameWithoutExtension}_$timestamp.$extension")
        }

        return try {
            val sourceFile = File(item.path)
            val copied = if (sourceFile.exists() && sourceFile.renameTo(targetFile)) {
                true
            } else {
                copyUriToFile(item.uri, targetFile)
            }

            if (copied) {
                RecycledItem(
                    originalPath = item.path,
                    currentPath = targetFile.absolutePath,
                    deletedTimestamp = System.currentTimeMillis(),
                    isArchived = isArchived
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun finalizeMove(recycledItem: RecycledItem) {
        recycledItemDao.insert(recycledItem)
    }

    suspend fun abandonMove(recycledItem: RecycledItem) {
        val file = File(recycledItem.currentPath)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun moveToRecycleBin(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val recycledItem = copyToRecycleBin(item) ?: return@withContext false
        
        // Try silent delete (works on older Android or if app is owner)
        val deletedCount = try {
            context.contentResolver.delete(item.uri, null, null)
        } catch (e: Exception) {
            0
        }

        if (deletedCount > 0) {
            finalizeMove(recycledItem)
            true
        } else {
            abandonMove(recycledItem)
            false
        }
    }

    suspend fun moveToArchive(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val recycledItem = copyToArchive(item) ?: return@withContext false

        val deletedCount = try {
            context.contentResolver.delete(item.uri, null, null)
        } catch (e: Exception) {
            0
        }

        if (deletedCount > 0) {
            finalizeMove(recycledItem)
            true
        } else {
            abandonMove(recycledItem)
            false
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreItem(recycledItem: RecycledItem): Boolean = withContext(Dispatchers.IO) {
        val currentFile = File(recycledItem.currentPath)
        val originalFile = File(recycledItem.originalPath)

        if (!currentFile.exists()) return@withContext false

        // Ensure parent directory exists
        originalFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        try {
            val restored = if (currentFile.renameTo(originalFile)) {
                true
            } else {
                copyFile(currentFile, originalFile)
                currentFile.delete()
            }

            if (restored) {
                // Delete from Room
                recycledItemDao.delete(recycledItem)

                // Rescan MediaStore
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(originalFile.absolutePath),
                    null
                ) { _, _ -> }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun permanentlyDeleteItem(recycledItem: RecycledItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(recycledItem.currentPath)
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            recycledItemDao.delete(recycledItem)
            true
        } else {
            false
        }
    }

    suspend fun moveRecycledToArchive(recycledItem: RecycledItem): Boolean = withContext(Dispatchers.IO) {
        val currentFile = File(recycledItem.currentPath)
        if (!currentFile.exists()) return@withContext false

        val targetDir = archiveDir ?: return@withContext false
        if (!targetDir.exists()) targetDir.mkdirs()

        var targetFile = File(targetDir, currentFile.name)
        if (targetFile.exists()) {
            val nameWithoutExtension = currentFile.nameWithoutExtension
            val extension = currentFile.extension
            val timestamp = System.currentTimeMillis()
            targetFile = File(targetDir, "${nameWithoutExtension}_$timestamp.$extension")
        }

        try {
            val moved = if (currentFile.renameTo(targetFile)) {
                true
            } else {
                copyFile(currentFile, targetFile)
                currentFile.delete()
            }

            if (moved) {
                val updatedItem = recycledItem.copy(
                    currentPath = targetFile.absolutePath,
                    isArchived = true,
                    // Reset notification flags for the next threshold (60 days)
                    notified24h = false,
                    notified12h = false,
                    notified1h = false
                )
                recycledItemDao.update(updatedItem)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun emptyRecycleBin(): Unit = withContext(Dispatchers.IO) {
        val items = recycledItemDao.getAll()
        items.filter { !it.isArchived }.forEach { item ->
            permanentlyDeleteItem(item)
        }
    }

    @Throws(IOException::class)
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}
