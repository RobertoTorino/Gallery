package com.robertotorino.gallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecycledItem::class], version = 1, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun recycledItemDao(): RecycledItemDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getDatabase(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "gallery_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
