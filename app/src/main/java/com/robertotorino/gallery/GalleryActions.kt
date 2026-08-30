package com.robertotorino.gallery

import android.content.Context
import android.net.Uri
import com.robertotorino.gallery.data.RecycledItemDao
import com.robertotorino.gallery.repository.MediaRepository

internal fun areAllPermissionsGranted(permissions: Map<String, Boolean>): Boolean {
    return permissions.values.all { it }
}

internal suspend fun prepareRecycleBinMoveInternal(
    uris: List<Uri>,
    context: Context,
    repository: MediaRepository,
    mediaItemProvider: (Context, Uri) -> com.robertotorino.gallery.data.MediaItem? = ::getMediaItemFromUri
): List<Pair<Uri, com.robertotorino.gallery.data.RecycledItem>> {
    val prepared = mutableListOf<Pair<Uri, com.robertotorino.gallery.data.RecycledItem>>()
    uris.forEach { uri ->
        val item = mediaItemProvider(context, uri)
        if (item != null) {
            val recycledItem = repository.copyToRecycleBin(item)
            if (recycledItem != null) {
                prepared.add(uri to recycledItem)
            }
        }
    }
    return prepared
}

internal suspend fun prepareArchiveMoveInternal(
    uris: List<Uri>,
    context: Context,
    repository: MediaRepository,
    mediaItemProvider: (Context, Uri) -> com.robertotorino.gallery.data.MediaItem? = ::getMediaItemFromUri
): List<Pair<Uri, com.robertotorino.gallery.data.RecycledItem>> {
    val prepared = mutableListOf<Pair<Uri, com.robertotorino.gallery.data.RecycledItem>>()
    uris.forEach { uri ->
        val item = mediaItemProvider(context, uri)
        if (item != null) {
            val recycledItem = repository.copyToArchive(item)
            if (recycledItem != null) {
                prepared.add(uri to recycledItem)
            }
        }
    }
    return prepared
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
