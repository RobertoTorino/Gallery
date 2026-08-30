package com.robertotorino.gallery.data

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val path: String,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateTaken: Long
)
