package com.robertotorino.gallery

import android.content.Context
import android.net.Uri
import com.robertotorino.gallery.data.RecycledItemDao
import com.robertotorino.gallery.repository.MediaRepository

internal fun areAllPermissionsGranted(permissions: Map<String, Boolean>): Boolean {
    return permissions.values.all { it }
}

internal suspend fun moveToRecycleBinInternal(
    uris: List<Uri>,
    context: Context,
    repository: MediaRepository,
    mediaItemProvider: (Context, Uri) -> com.robertotorino.gallery.data.MediaItem? = ::getMediaItemFromUri
): Int {
    var successCount = 0
    uris.forEach { uri ->
        val item = mediaItemProvider(context, uri)
        if (item != null && repository.moveToRecycleBin(item)) {
            successCount++
        }
    }
    return successCount
}

internal suspend fun archiveImagesInternal(
    uris: List<Uri>,
    context: Context,
    repository: MediaRepository,
    mediaItemProvider: (Context, Uri) -> com.robertotorino.gallery.data.MediaItem? = ::getMediaItemFromUri
): Int {
    val mediaItems = uris.mapNotNull { mediaItemProvider(context, it) }
    var successCount = 0
    mediaItems.forEach { item ->
        if (repository.moveToArchive(item)) {
            successCount++
        }
    }
    return successCount
}

internal suspend fun restoreFromArchiveInternal(
    recycledItemDao: RecycledItemDao,
    repository: MediaRepository
): Int {
    val archivedItems = recycledItemDao.getAll().filter { it.isArchived }
    var successCount = 0
    archivedItems.forEach { item ->
        if (repository.restoreItem(item)) {
            successCount++
        }
    }
    return successCount
}
