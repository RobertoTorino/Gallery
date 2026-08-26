package com.robertotorino.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycledItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecycledItem): Long

    @Update
    suspend fun update(item: RecycledItem)

    @Delete
    suspend fun delete(item: RecycledItem)

    @Query("SELECT * FROM recycled_items WHERE id = :id")
    suspend fun getItemById(id: Long): RecycledItem?

    @Query("SELECT * FROM recycled_items WHERE isArchived = 0 ORDER BY deletedTimestamp DESC")
    fun getAllRecycled(): Flow<List<RecycledItem>>

    @Query("SELECT * FROM recycled_items WHERE isArchived = 1 ORDER BY deletedTimestamp DESC")
    fun getAllArchived(): Flow<List<RecycledItem>>

    @Query("SELECT * FROM recycled_items WHERE deletedTimestamp < :threshold")
    suspend fun getItemsToCleanup(threshold: Long): List<RecycledItem>

    @Query("SELECT * FROM recycled_items")
    suspend fun getAll(): List<RecycledItem>
}
