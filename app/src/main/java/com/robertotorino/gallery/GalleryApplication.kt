package com.robertotorino.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GalleryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(true)
            .build()
    }
}
