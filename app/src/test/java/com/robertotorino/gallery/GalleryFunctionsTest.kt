package com.robertotorino.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.robertotorino.gallery.data.MediaItem
import com.robertotorino.gallery.data.RecycledItem
import com.robertotorino.gallery.data.RecycledItemDao
import com.robertotorino.gallery.repository.MediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import android.database.Cursor

class GalleryFunctionsTest {
    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        mockkStatic(ContentUris::class)
        mockkStatic(MediaStore.Images.Media::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(ContentUris::class)
        unmockkStatic(MediaStore.Images.Media::class)
    }

    @Test
    fun areAllPermissionsGranted_returnsTrueOnlyWhenAllValuesTrue() {
        val allGranted = mapOf("a" to true, "b" to true)
        val oneDenied = mapOf("a" to true, "b" to false)

        assertEquals(true, areAllPermissionsGranted(allGranted))
        assertEquals(false, areAllPermissionsGranted(oneDenied))
    }

    @Test
    fun getUriSizeInBytes_usesPrimaryUriSizeWhenAvailable() {
        val uri = mockk<Uri>()
        every { Uri.parse("content://example/image/1") } returns uri
        
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor.moveToFirst() } returns true
        every { cursor.isNull(0) } returns false
        every { cursor.getLong(0) } returns 123L
        every { cursor.close() } returns Unit

        every { context.contentResolver } returns resolver
        every { resolver.query(eq(uri), any(), isNull(), isNull(), isNull()) } returns cursor

        val result = getUriSizeInBytes(context, uri)

        assertEquals(123L, result)
    }

    @Test
    fun getUriSizeInBytes_fallsBackToResolvedMediaStoreUri() {
        val pickerUri = mockk<Uri>()
        val resolvedUri = mockk<Uri>()
        every { Uri.parse("content://com.android.providers.media.photopicker/media/321") } returns pickerUri
        every { pickerUri.scheme } returns "content"
        every { pickerUri.authority } returns "com.android.providers.media.photopicker"
        every { pickerUri.toString() } returns "content://com.android.providers.media.photopicker/media/321"
        every { pickerUri.lastPathSegment } returns "321"
        every { ContentUris.withAppendedId(any(), 321L) } returns resolvedUri

        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()

        val emptyPrimaryCursor = mockk<Cursor>()
        every { emptyPrimaryCursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { emptyPrimaryCursor.moveToFirst() } returns true
        every { emptyPrimaryCursor.isNull(0) } returns true
        every { emptyPrimaryCursor.close() } returns Unit

        val fallbackCursor = mockk<Cursor>()
        every { fallbackCursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { fallbackCursor.moveToFirst() } returns true
        every { fallbackCursor.isNull(0) } returns false
        every { fallbackCursor.getLong(0) } returns 987L
        every { fallbackCursor.close() } returns Unit

        every { context.contentResolver } returns resolver
        every { resolver.query(eq(pickerUri), any(), isNull(), isNull(), isNull()) } returns emptyPrimaryCursor
        every { resolver.query(eq(resolvedUri), any(), isNull(), isNull(), isNull()) } returns fallbackCursor

        val result = getUriSizeInBytes(context, pickerUri)

        assertEquals(987L, result)
    }

    @Test
    fun calculateUsedStorageBytes_sumsSizesAcrossUris() {
        val uriOne = mockk<Uri>()
        val uriTwo = mockk<Uri>()
        every { Uri.parse("content://example/image/1") } returns uriOne
        every { Uri.parse("content://example/image/2") } returns uriTwo

        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()

        val cursorOne = mockk<Cursor>()
        every { cursorOne.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursorOne.moveToFirst() } returns true
        every { cursorOne.isNull(0) } returns false
        every { cursorOne.getLong(0) } returns 10L
        every { cursorOne.close() } returns Unit

        val cursorTwo = mockk<Cursor>()
        every { cursorTwo.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursorTwo.moveToFirst() } returns true
        every { cursorTwo.isNull(0) } returns false
        every { cursorTwo.getLong(0) } returns 25L
        every { cursorTwo.close() } returns Unit

        every { context.contentResolver } returns resolver
        every { resolver.query(eq(uriOne), any(), isNull(), isNull(), isNull()) } returns cursorOne
        every { resolver.query(eq(uriTwo), any(), isNull(), isNull(), isNull()) } returns cursorTwo

        val result = calculateUsedStorageBytes(context, listOf(uriOne, uriTwo))

        assertEquals(35L, result)
    }

    @Test
    fun moveToRecycleBinInternal_countsOnlySuccessfulMoves() {
        runTest {
            val firstUri = mockk<Uri>()
            val secondUri = mockk<Uri>()
            val thirdUri = mockk<Uri>()
            every { Uri.parse("content://example/image/1") } returns firstUri
            every { Uri.parse("content://example/image/2") } returns secondUri
            every { Uri.parse("content://example/image/3") } returns thirdUri

            val context = mockk<Context>()
            val repository = mockk<MediaRepository>()
            val firstItem = MediaItem(firstUri, "/a.jpg", "a.jpg", 1L, "image/jpeg", 1L)
            val secondItem = MediaItem(secondUri, "/b.jpg", "b.jpg", 1L, "image/jpeg", 1L)

            coEvery { repository.moveToRecycleBin(firstItem) } returns true
            coEvery { repository.moveToRecycleBin(secondItem) } returns false

            val count = moveToRecycleBinInternal(
                uris = listOf(firstUri, secondUri, thirdUri),
                context = context,
                repository = repository,
                mediaItemProvider = { _, uri ->
                    when (uri) {
                        firstUri -> firstItem
                        secondUri -> secondItem
                        else -> null
                    }
                }
            )

            assertEquals(1, count)
            coVerify(exactly = 1) { repository.moveToRecycleBin(firstItem) }
            coVerify(exactly = 1) { repository.moveToRecycleBin(secondItem) }
        }
    }

    @Test
    fun archiveImagesInternal_countsOnlySuccessfulMoves() {
        runTest {
            val firstUri = mockk<Uri>()
            val secondUri = mockk<Uri>()
            every { Uri.parse("content://example/image/1") } returns firstUri
            every { Uri.parse("content://example/image/2") } returns secondUri

            val context = mockk<Context>()
            val repository = mockk<MediaRepository>()
            val firstItem = MediaItem(firstUri, "/a.jpg", "a.jpg", 1L, "image/jpeg", 1L)
            val secondItem = MediaItem(secondUri, "/b.jpg", "b.jpg", 1L, "image/jpeg", 1L)

            coEvery { repository.moveToArchive(firstItem) } returns true
            coEvery { repository.moveToArchive(secondItem) } returns false

            val count = archiveImagesInternal(
                uris = listOf(firstUri, secondUri),
                context = context,
                repository = repository,
                mediaItemProvider = { _, uri -> if (uri == firstUri) firstItem else secondItem }
            )

            assertEquals(1, count)
            coVerify(exactly = 1) { repository.moveToArchive(firstItem) }
            coVerify(exactly = 1) { repository.moveToArchive(secondItem) }
        }
    }

    @Test
    fun restoreFromArchiveInternal_restoresOnlyArchivedItems() {
        runTest {
            val dao = mockk<RecycledItemDao>()
            val repository = mockk<MediaRepository>()
            val archivedOne = RecycledItem(id = 1, originalPath = "/a.jpg", currentPath = "/archive/a.jpg", deletedTimestamp = 1, isArchived = true)
            val activeRecycleBin = RecycledItem(id = 2, originalPath = "/b.jpg", currentPath = "/recycle/b.jpg", deletedTimestamp = 1, isArchived = false)
            val archivedTwo = RecycledItem(id = 3, originalPath = "/c.jpg", currentPath = "/archive/c.jpg", deletedTimestamp = 1, isArchived = true)

            coEvery { dao.getAll() } returns listOf(archivedOne, activeRecycleBin, archivedTwo)
            coEvery { repository.restoreItem(archivedOne) } returns true
            coEvery { repository.restoreItem(archivedTwo) } returns false

            val count = restoreFromArchiveInternal(dao, repository)

            assertEquals(1, count)
            coVerify(exactly = 1) { repository.restoreItem(archivedOne) }
            coVerify(exactly = 1) { repository.restoreItem(archivedTwo) }
            coVerify(exactly = 0) { repository.restoreItem(activeRecycleBin) }
        }
    }
}
