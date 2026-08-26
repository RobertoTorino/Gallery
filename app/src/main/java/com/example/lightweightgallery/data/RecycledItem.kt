package com.robertotorino.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycled_items")
data class RecycledItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPath: String,
    val currentPath: String,
    val deletedTimestamp: Long,
    val isArchived: Boolean = false,
    val notified24h: Boolean = false,
    val notified12h: Boolean = false,
    val notified1h: Boolean = false
)
