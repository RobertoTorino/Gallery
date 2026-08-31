package com.robertotorino.gallery

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.robertotorino.gallery.data.GalleryDatabase
import com.robertotorino.gallery.data.MediaItem
import com.robertotorino.gallery.repository.MediaRepository
import com.robertotorino.gallery.ui.theme.AppTheme
import com.robertotorino.gallery.ui.theme.GalleryTheme
import com.robertotorino.gallery.worker.CleanupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToLong
import androidx.media3.common.MediaItem as ExoMediaItem

enum class MediaDateSource {
    MEDIA_STORE,
    EXIF,
    FILENAME,
    VIDEO_METADATA,
    UNKNOWN
}

data class DateResult(val date: Long, val source: MediaDateSource)

fun Context.getActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun setAsWallpaper(context: Context, uri: Uri) {
    try {
        val wallpaperManager = WallpaperManager.getInstance(context)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            wallpaperManager.setStream(inputStream)
            Toast.makeText(context, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun getRealPathFromUri(context: Context, uri: Uri): String? {
    val mediaStoreUri = resolveToMediaStoreUri(context, uri)
    val projection = arrayOf(MediaStore.MediaColumns.DATA)
    try {
        context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex)
                }
            }
        }
    } catch (_: Exception) {
    }
    return uri.path
}

fun getRealPathFromTreeUri(treeUri: Uri): String? {
    if (DocumentsContract.isTreeUri(treeUri)) {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = documentId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val path = split[1]
            val resolvedPath = if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory()}/$path"
            } else {
                "/storage/$type/$path"
            }
            return if (resolvedPath.endsWith(File.separator)) resolvedPath else resolvedPath + File.separator
        }
    }
    return null
}

fun openManagedFolderInPicker(context: Context, folderName: String) {
    val documentId = "primary:Android/data/${context.packageName}/files/$folderName"
    val initialUri = DocumentsContract.buildTreeDocumentUri(
        "com.android.externalstorage.documents",
        documentId
    )
    val openFolderIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(openFolderIntent)
    } catch (_: Exception) {
        val path = context.getExternalFilesDir(folderName)?.absolutePath ?: folderName
        Toast.makeText(context, "Could not open folder picker. Folder path: $path", Toast.LENGTH_LONG).show()
    }
}

fun queryImages(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_TAKEN
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val appFilesDir = context.getExternalFilesDir(null)?.absolutePath
    var selection: String? = null
    var selectionArgs: Array<String>? = null
    if (appFilesDir != null) {
        selection = "${MediaStore.Images.Media.DATA} NOT LIKE ?"
        selectionArgs = arrayOf("$appFilesDir%")
    }

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val path = cursor.getString(dataColumn)
            val name = cursor.getString(nameColumn)
            val size = cursor.getLong(sizeColumn)
            val mime = cursor.getString(mimeColumn)
            val added = cursor.getLong(addedColumn)
            val takenRaw = cursor.getLong(takenColumn)
            val bestDateResult = extractBestDate(context, uri, name, mime, takenRaw)
            val isFallback = bestDateResult.source == MediaDateSource.FILENAME || bestDateResult.source == MediaDateSource.UNKNOWN
            items.add(MediaItem(uri, path, name, size, mime, added, bestDateResult.date, isDateFallback = isFallback))
        }
    }
    return items
}

fun queryVideos(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_TAKEN
    )
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    val appFilesDir = context.getExternalFilesDir(null)?.absolutePath
    var selection: String? = null
    var selectionArgs: Array<String>? = null
    if (appFilesDir != null) {
        selection = "${MediaStore.Video.Media.DATA} NOT LIKE ?"
        selectionArgs = arrayOf("$appFilesDir%")
    }

    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            val path = cursor.getString(dataColumn)
            val name = cursor.getString(nameColumn)
            val size = cursor.getLong(sizeColumn)
            val mime = cursor.getString(mimeColumn)
            val added = cursor.getLong(addedColumn)
            val takenRaw = cursor.getLong(takenColumn)
            val bestDateResult = extractBestDate(context, uri, name, mime, takenRaw)
            val isFallback = bestDateResult.source == MediaDateSource.FILENAME || bestDateResult.source == MediaDateSource.UNKNOWN
            items.add(MediaItem(uri, path, name, size, mime, added, bestDateResult.date, isDateFallback = isFallback))
        }
    }
    return items
}

fun resolveToMediaStoreUri(context: Context, uri: Uri): Uri {
    if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
        return uri
    }
    if (uri.toString().contains("photopicker")) {
        val id = uri.lastPathSegment?.toLongOrNull()
        if (id != null) {
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val baseUri = if (mimeType?.startsWith("video/") == true) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            return ContentUris.withAppendedId(baseUri, id)
        }
    }
    if (DocumentsContract.isDocumentUri(context, uri)) {
        val docId = DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("image:")) {
            val id = docId.split(":")[1].toLongOrNull()
            if (id != null) {
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        if (docId.startsWith("video:")) {
            val id = docId.split(":")[1].toLongOrNull()
            if (id != null) {
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
    }
    return uri
}

fun getUriSizeInBytes(context: Context, uri: Uri): Long {
    fun querySize(targetUri: Uri): Long? {
        context.contentResolver.query(
            targetUri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
        return null
    }

    return querySize(uri) ?: querySize(resolveToMediaStoreUri(context, uri)) ?: 0L
}

fun calculateUsedStorageBytes(context: Context, uris: List<Uri>): Long {
    return uris.sumOf { uri -> getUriSizeInBytes(context, uri) }
}

fun getVideoThumbnailBitmap(context: Context, uri: Uri): Bitmap? {
    val mediaStoreUri = resolveToMediaStoreUri(context, uri)
    val id = mediaStoreUri.lastPathSegment?.toLongOrNull() ?: return null
    val mediaUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    return try {
        context.contentResolver.loadThumbnail(mediaUri, Size(512, 512), null)
    } catch (_: Exception) {
        null
    }
}

fun formatMegabytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes <= 0L) return "0.0 MB"
    val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (megabytes >= 1000.0) {
        val grouped = String.format(Locale.US, "%,d", megabytes.roundToLong()).replace(',', '.')
        "$grouped MB"
    } else if (megabytes >= 100.0) {
        String.format(locale, "%.0f MB", megabytes)
    } else {
        String.format(locale, "%.1f MB", megabytes)
    }
}

fun cropImageAndSaveCopy(context: Context, uri: Uri): Uri? {
    val resolver = context.contentResolver
    val sourceUri = resolveToMediaStoreUri(context, uri)
    val sourceBitmap = resolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream) ?: return null
    val cropSize = min(sourceBitmap.width, sourceBitmap.height)
    val left = (sourceBitmap.width - cropSize) / 2
    val top = (sourceBitmap.height - cropSize) / 2
    val croppedBitmap = Bitmap.createBitmap(sourceBitmap, left, top, cropSize, cropSize)

    val baseName = resolver.query(
        sourceUri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }?.substringBeforeLast('.') ?: "image"

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "${baseName}_crop_$timestamp.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Gallery")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: run {
            if (croppedBitmap != sourceBitmap) croppedBitmap.recycle()
            sourceBitmap.recycle()
            return null
        }

    return try {
        val outputStream = resolver.openOutputStream(outputUri) ?: throw IOException("Output stream is null")
        outputStream.use { stream ->
            if (!croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IOException("Failed to compress cropped image")
            }
        }
        val finalizeValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(outputUri, finalizeValues, null, null)
        outputUri
    } catch (_: IOException) {
        resolver.delete(outputUri, null, null)
        null
    } catch (_: SecurityException) {
        resolver.delete(outputUri, null, null)
        null
    } catch (_: IllegalArgumentException) {
        resolver.delete(outputUri, null, null)
        null
    } finally {
        if (croppedBitmap != sourceBitmap) croppedBitmap.recycle()
        sourceBitmap.recycle()
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Cleanup Worker
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )

        val initialUri = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent {
            GalleryTheme {
                var hasPermission by remember {
                    val permissions = listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                    mutableStateOf(
                        permissions.all {
                            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background
                ) {
                    if (hasPermission) {
                        GalleryScreen(initialUri = initialUri)
                    } else {
                        PermissionScreen(onPermissionGranted = { hasPermission = true })
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onPermissionGranted: () -> Unit) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (areAllPermissionsGranted(permissions)) {
            onPermissionGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to\nGallery",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "A simple app to quickly view your photos and videos.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "The app needs permissions to access your photos, videos and show notifications.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent)
        ) {
            Text("Grant Permission", color = Color.Black)
        }
    }
}

data class EditState(
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isCropped: Boolean = false,
    val isGrayscale: Boolean = false,
    val metadataRemoved: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(rotation)
        parcel.writeByte(if (flipHorizontal) 1 else 0)
        parcel.writeByte(if (flipVertical) 1 else 0)
        parcel.writeByte(if (isCropped) 1 else 0)
        parcel.writeByte(if (isGrayscale) 1 else 0)
        parcel.writeByte(if (metadataRemoved) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EditState> {
        override fun createFromParcel(parcel: Parcel): EditState = EditState(parcel)
        override fun newArray(size: Int): Array<EditState?> = arrayOfNulls(size)
    }
}

sealed class GalleryItem {
    data class Header(val label: String) : GalleryItem()
    data class Media(val media: MediaItem) : GalleryItem()
}

fun groupMedia(
    items: List<MediaItem>,
    filterByDate: Boolean,
    filterByExifSoftware: Boolean,
    filterByExifArtist: Boolean,
    exifInfoByUri: Map<Uri, Pair<String?, String?>>
): List<GalleryItem> {
    if (!filterByDate && !filterByExifSoftware && !filterByExifArtist) {
        return items.map { GalleryItem.Media(it) }
    }

    // Sort: First by capture date (if valid), fallback to dateAdded. Unsorted (0L) go to the end.
    val sortedItems = items.sortedWith(
        compareByDescending<MediaItem> { it.dateTaken > 0 }
            .thenByDescending { if (it.dateTaken > 0) it.dateTaken else it.dateAdded * 1000 }
    )

    val grouped = mutableListOf<GalleryItem>()
    val dateFormat = SimpleDateFormat("EEEE MMMM d yyyy", Locale.US)
    var currentHeader: String? = null

    sortedItems.forEach { item ->
        val (software, artist) = exifInfoByUri[item.uri] ?: (null to null)
        val groupLabel = when {
            filterByExifArtist -> "Author name: ${artist?.takeIf { it.isNotBlank() } ?: "Unknown"}"
            filterByExifSoftware -> "App name: ${software?.takeIf { it.isNotBlank() } ?: "Unknown"}"
            item.dateTaken > 0 -> dateFormat.format(Date(item.dateTaken))
            else -> "Unsorted"
        }

        if (groupLabel != currentHeader) {
            grouped.add(GalleryItem.Header(groupLabel))
            currentHeader = groupLabel
        }
        grouped.add(GalleryItem.Media(item))
    }
    return grouped
}

fun extractExifSoftwareAndArtist(context: Context, uri: Uri): Pair<String?, String?> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val artist = exif.getAttribute(ExifInterface.TAG_ARTIST)
            software to artist
        } ?: (null to null)
    } catch (_: Exception) {
        null to null
    }
}

enum class ExclusionMediaType {
    PICTURES,
    VIDEOS
}

enum class DeleteMediaType {
    PICTURES,
    VIDEOS
}

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(initialUri: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }

    val database = remember { GalleryDatabase.getDatabase(context) }
    val repository = remember { MediaRepository(context, database.recycledItemDao()) }

    var imageItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var checkedUris by remember { mutableStateOf(setOf<Uri>()) }

    var imageEdits by rememberSaveable { mutableStateOf(mapOf<Uri, EditState>()) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var showVideoMetadataDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var urisToDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var urisToArchive by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var excludedPictureFolders by remember { mutableStateOf(setOf<String>()) }
    var excludedVideoFolders by remember { mutableStateOf(setOf<String>()) }
    var showWallpaperConfirm by remember { mutableStateOf(false) }
    var pendingWallpaperUri by remember { mutableStateOf<Uri?>(null) }
    var preparedRecycledItems by remember { mutableStateOf<List<Pair<Uri, com.robertotorino.gallery.data.RecycledItem>>>(emptyList()) }

    var useRecycleBin by remember { mutableStateOf(prefs.getBoolean("use_recycle_bin", false)) }
    var recycleBinDays by remember { mutableIntStateOf(prefs.getInt("recycle_bin_days", 30)) }
    var archiveAfter30Days by remember { mutableStateOf(prefs.getBoolean("archive_after_30_days", false)) }
    var filterByDate by remember { mutableStateOf(prefs.getBoolean("filter_by_date", false)) }
    var filterByExifSoftware by remember { mutableStateOf(prefs.getBoolean("filter_by_exif_software", false)) }
    var filterByExifArtist by remember { mutableStateOf(prefs.getBoolean("filter_by_exif_artist", false)) }

    var showRecycleBinSettings by remember { mutableStateOf(false) }
    var showArchiveSettings by remember { mutableStateOf(false) }
    var showFilterSettings by remember { mutableStateOf(false) }
    var showRestoreRecycleBinDialog by remember { mutableStateOf(false) }
    var showRestoreArchiveDialog by remember { mutableStateOf(false) }
    var showEmptyRecycleBinDialog by remember { mutableStateOf(false) }
    var showUsedStorageDialog by remember { mutableStateOf(false) }
    var usedPictureBytes by remember { mutableStateOf(0L) }
    var usedVideoBytes by remember { mutableStateOf(0L) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var videoMetadataRemovedUris by rememberSaveable { mutableStateOf(setOf<Uri>()) }
    var deleteMediaType by remember { mutableStateOf(DeleteMediaType.PICTURES) }

    var showExcludedFoldersDialog by remember { mutableStateOf(false) }
    var exclusionMediaType by remember { mutableStateOf(ExclusionMediaType.PICTURES) }
    var showPreExcludeConfirm by remember { mutableStateOf(false) }
    var showPostExcludeConfirm by remember { mutableStateOf(false) }
    var pendingExcludedUri by remember { mutableStateOf<Uri?>(null) }
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    var selectedMediaTab by rememberSaveable { mutableIntStateOf(0) }
    var videoItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Load images from MediaStore and saved folders
    LaunchedEffect(Unit) {
        val (foundImages, foundVideos, savedItems) = withContext(Dispatchers.IO) {
            val savedPictureFolders =
                prefs.getStringSet(
                    "excluded_picture_folders",
                    prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
                ) ?: emptySet()
            val savedVideoFolders = prefs.getStringSet("excluded_video_folders", emptySet()) ?: emptySet()
            excludedPictureFolders = savedPictureFolders
            excludedVideoFolders = savedVideoFolders

            val savedUris =
                prefs.getStringSet("added_uris", emptySet())?.map { Uri.parse(it) } ?: emptyList()

            val foundImages = queryImages(context)
            val savedItems = savedUris.mapNotNull { getMediaItemFromUri(context, it) }
            val foundVideos = queryVideos(context)
            Triple(foundImages, foundVideos, savedItems)
        }

        imageItems = (foundImages + savedItems).distinctBy { it.uri }
        videoItems = foundVideos

        initialUri?.let { uri ->
            val index = imageItems.indexOfFirst { it.uri == uri }
            if (index != -1) {
                selectedImageIndex = index
            } else {
                getMediaItemFromUri(context, uri)?.let { item ->
                    imageItems = listOf(item) + imageItems
                    selectedImageIndex = 0
                }
            }
        }
        isLoaded = true
    }

    LaunchedEffect(useRecycleBin) {
        prefs.edit().putBoolean("use_recycle_bin", useRecycleBin).apply()
    }
    LaunchedEffect(recycleBinDays) {
        prefs.edit().putInt("recycle_bin_days", recycleBinDays).apply()
    }
    LaunchedEffect(archiveAfter30Days) {
        prefs.edit().putBoolean("archive_after_30_days", archiveAfter30Days).apply()
    }
    LaunchedEffect(filterByDate) {
        prefs.edit().putBoolean("filter_by_date", filterByDate).apply()
    }
    LaunchedEffect(filterByExifSoftware) {
        prefs.edit().putBoolean("filter_by_exif_software", filterByExifSoftware).apply()
    }
    LaunchedEffect(filterByExifArtist) {
        prefs.edit().putBoolean("filter_by_exif_artist", filterByExifArtist).apply()
    }

    LaunchedEffect(excludedPictureFolders) {
        prefs.edit().putStringSet("excluded_picture_folders", excludedPictureFolders).apply()
    }

    LaunchedEffect(excludedVideoFolders) {
        prefs.edit().putStringSet("excluded_video_folders", excludedVideoFolders).apply()
    }

    LaunchedEffect(imageItems) {
        if (isLoaded) {
            val uriStrings =
                imageItems.filter { it.uri.scheme == "content" }.map { it.uri.toString() }.toSet()
            prefs.edit().putStringSet("added_uris", uriStrings).apply()
        }
    }

    val filteredItems = remember(imageItems, excludedPictureFolders, filterByExifSoftware, filterByExifArtist) {
        imageItems.filter { item ->
            val path = getRealPathFromUri(context, item.uri) ?: item.uri.toString()
            val notExcluded = excludedPictureFolders.none { excluded -> path.startsWith(excluded) }
            val hasRequiredExif = if (!filterByExifSoftware && !filterByExifArtist) {
                true
            } else {
                hasRequestedExifTags(
                    context = context,
                    uri = item.uri,
                    requireSoftware = filterByExifSoftware,
                    requireArtist = filterByExifArtist
                )
            }
            notExcluded && hasRequiredExif
        }
    }
    val filteredVideoItems = remember(videoItems, excludedVideoFolders) {
        videoItems.filter { item ->
            val path = getRealPathFromUri(context, item.uri) ?: item.uri.toString()
            excludedVideoFolders.none { excluded -> path.startsWith(excluded) }
        }
    }

    val imageExifInfoByUri = remember(filteredItems, filterByExifSoftware, filterByExifArtist) {
        if (!filterByExifSoftware && !filterByExifArtist) {
            emptyMap()
        } else {
            filteredItems.associate { item ->
                item.uri to extractExifSoftwareAndArtist(context, item.uri)
            }
        }
    }

    val groupedItems = remember(filteredItems, filterByDate, filterByExifSoftware, filterByExifArtist, imageExifInfoByUri) {
        groupMedia(
            items = filteredItems,
            filterByDate = filterByDate,
            filterByExifSoftware = filterByExifSoftware,
            filterByExifArtist = filterByExifArtist,
            exifInfoByUri = imageExifInfoByUri
        )
    }

    val groupedVideoItems = remember(filteredVideoItems, filterByDate) {
        groupMedia(
            items = filteredVideoItems,
            filterByDate = filterByDate,
            filterByExifSoftware = false,
            filterByExifArtist = false,
            exifInfoByUri = emptyMap()
        )
    }

    LaunchedEffect(showUsedStorageDialog, filteredItems, filteredVideoItems) {
        if (showUsedStorageDialog) {
            withContext(Dispatchers.IO) {
                usedPictureBytes = calculateUsedStorageBytes(context, filteredItems.map { it.uri })
                usedVideoBytes = calculateUsedStorageBytes(context, filteredVideoItems.map { it.uri })
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            pendingExcludedUri = it
            showPostExcludeConfirm = true
        }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deletedUris = urisToDelete

            // Finalize Recycle Bin / Archive moves if any
            if (preparedRecycledItems.isNotEmpty()) {
                scope.launch {
                    preparedRecycledItems.forEach { (originalUri, recycledItem) ->
                        // Only finalize if the original was actually deleted from MediaStore
                        if (originalUri in deletedUris) {
                            repository.finalizeMove(recycledItem)
                        } else {
                            repository.abandonMove(recycledItem)
                        }
                    }
                    preparedRecycledItems = emptyList()
                }
            }

            imageItems = imageItems.filterNot { it.uri in deletedUris }
            videoItems = videoItems.filterNot { it.uri in deletedUris }
            checkedUris = checkedUris.filterNot { it in deletedUris }.toSet()
            selectedImageIndex = selectedImageIndex?.takeIf { index ->
                val currentItem = filteredItems.getOrNull(index)
                currentItem != null && currentItem.uri !in deletedUris
            }
            if (selectedVideoUri in deletedUris) {
                selectedVideoUri = null
                showVideoPlayer = false
            }
            urisToDelete = emptyList()
            val deletedMediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "Video(s)" else "Picture(s)"
            val actionLabel = if (preparedRecycledItems.isNotEmpty()) {
                if (preparedRecycledItems.first().second.isArchived) "moved to Archive" else "moved to Recycle Bin"
            } else "deleted"
            Toast.makeText(context, "$deletedMediaLabel $actionLabel", Toast.LENGTH_SHORT).show()
        } else {
            // Abandon all prepared items if user cancels deletion
            if (preparedRecycledItems.isNotEmpty()) {
                scope.launch {
                    preparedRecycledItems.forEach { (_, recycledItem) ->
                        repository.abandonMove(recycledItem)
                    }
                    preparedRecycledItems = emptyList()
                }
            }
        }
    }

    fun resolveToMediaStoreUri(uri: Uri): Uri {
        return resolveToMediaStoreUri(context, uri)
    }

    fun deleteMedia(uris: List<Uri>) {
        val activity = context.getActivity() ?: return
        val executor = ContextCompat.getMainExecutor(activity)
        val targetMediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "video(s)" else "picture(s)"

        val biometricPrompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    scope.launch {
                        val resolvedUris = uris.map { resolveToMediaStoreUri(it) }
                        if (resolvedUris.isEmpty()) {
                            Toast.makeText(context, "Could not find media on device storage", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        if (useRecycleBin) {
                            val prepared = prepareRecycleBinMoveInternal(uris, context, repository)
                            if (prepared.isEmpty()) {
                                Toast.makeText(context, "Failed to move items to Recycle Bin", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            preparedRecycledItems = prepared
                            urisToDelete = prepared.map { it.first }

                            try {
                                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, prepared.map { resolveToMediaStoreUri(it.first) })
                                intentSenderLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } catch (e: Exception) {
                                prepared.forEach { repository.abandonMove(it.second) }
                                preparedRecycledItems = emptyList()
                                Toast.makeText(context, "Error initiating delete: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            try {
                                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, resolvedUris)
                                intentSenderLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error initiating delete: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT)
                        .show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Deletion")
            .setSubtitle("Authenticate to delete $targetMediaLabel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Authentication failed to start: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun archiveMedia(uris: List<Uri>) {
        val targetMediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "video(s)" else "picture(s)"
        scope.launch {
            val prepared = prepareArchiveMoveInternal(uris, context, repository)
            if (prepared.isEmpty()) {
                Toast.makeText(context, "Failed to move $targetMediaLabel to Archive", Toast.LENGTH_SHORT).show()
                return@launch
            }
            preparedRecycledItems = prepared
            urisToDelete = prepared.map { it.first }

            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    prepared.map { resolveToMediaStoreUri(it.first) }
                )
                intentSenderLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                prepared.forEach { repository.abandonMove(it.second) }
                preparedRecycledItems = emptyList()
                Toast.makeText(context, "Error initiating archive: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun restoreFromRecycleBin() {
        scope.launch {
            val items = database.recycledItemDao().getAll()
            items.filter { !it.isArchived }.forEach { repository.restoreItem(it) }
            val foundImages = queryImages(context)
            imageItems = (foundImages + imageItems).distinctBy { it.uri }
            Toast.makeText(context, "Pictures restored from Recycle Bin", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreFromArchive() {
        scope.launch {
            restoreFromArchiveInternal(database.recycledItemDao(), repository)
            val foundImages = queryImages(context)
            imageItems = (foundImages + imageItems).distinctBy { it.uri }
            Toast.makeText(context, "Pictures restored from Archive", Toast.LENGTH_SHORT).show()
        }
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            val newItems = uris.mapNotNull { getMediaItemFromUri(context, it) }
            imageItems = (imageItems + newItems).distinctBy { it.uri }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Gallery",
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (checkedUris.isNotEmpty()) {
                            Text(
                                "${checkedUris.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.accent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.cardBackground),
                actions = {
                    IconButton(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                }
            )
        },
        containerColor = AppTheme.colors.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppTheme.colors.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(
                    selectedTabIndex = selectedMediaTab,
                    containerColor = AppTheme.colors.background,
                    contentColor = AppTheme.colors.textPrimary
                ) {
                    Tab(
                        selected = selectedMediaTab == 0,
                        onClick = { selectedMediaTab = 0 },
                        text = { Text("Pictures") }
                    )
                    Tab(
                        selected = selectedMediaTab == 1,
                        onClick = {
                            selectedMediaTab = 1
                            checkedUris = emptySet()
                            selectedImageIndex = null
                        },
                        text = { Text("Videos") }
                    )
                }

                Text(
                    text = "Found ${filteredItems.size} pictures and ${filteredVideoItems.size} videos.",
                    modifier = Modifier.padding(16.dp),
                    color = AppTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (selectedMediaTab == 0 && filteredItems.isEmpty()) {
                                Modifier.clickable { multiplePhotoPickerLauncher.launch(arrayOf("image/*")) }
                            } else Modifier
                        )
                ) {
                    if (selectedMediaTab == 0) {
                        if (filteredItems.isEmpty()) {
                            Text(
                                text = "Tap anywhere to choose pictures.",
                                modifier = Modifier.align(Alignment.Center),
                                color = AppTheme.colors.textSecondary
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = 4.dp,
                                    bottom = 80.dp
                                )
                            ) {
                                items(
                                    items = groupedItems,
                                    span = { item ->
                                        if (item is GalleryItem.Header) GridItemSpan(3) else GridItemSpan(1)
                                    }
                                ) { galleryItem ->
                                    when (galleryItem) {
                                        is GalleryItem.Header -> {
                                            Column(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp)
                                                    .padding(top = 20.dp, bottom = 8.dp)
                                            ) {
                                                Text(
                                                    text = galleryItem.label,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                HorizontalDivider(
                                                    thickness = 0.5.dp,
                                                    color = Color.White.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                        is GalleryItem.Media -> {
                                            val media = galleryItem.media
                                            val isChecked = media.uri in checkedUris
                                            Box(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(AppTheme.colors.cardBackground)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (checkedUris.isNotEmpty()) {
                                                                checkedUris =
                                                                    if (isChecked) checkedUris - media.uri else checkedUris + media.uri
                                                            } else {
                                                                selectedImageIndex = filteredItems.indexOf(media)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            checkedUris =
                                                                if (isChecked) checkedUris - media.uri else checkedUris + media.uri
                                                        }
                                                    )
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(media.uri)
                                                        .size(300)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Selected Picture",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (filterByDate && media.isDateFallback) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "Missing metadata",
                                                        tint = Color.Yellow.copy(alpha = 0.8f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(4.dp)
                                                            .size(16.dp)
                                                    )
                                                }
                                                if (filterByDate && media.isDateFallback) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "Missing metadata",
                                                        tint = Color.Yellow.copy(alpha = 0.8f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(4.dp)
                                                            .size(16.dp)
                                                    )
                                                }
                                                if (checkedUris.isNotEmpty()) {
                                                    Icon(
                                                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                        contentDescription = null,
                                                        tint = if (isChecked) AppTheme.colors.accent else Color.White.copy(
                                                            alpha = 0.5f
                                                        ),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (filteredVideoItems.isEmpty()) {
                            Text(
                                text = "No videos found.",
                                modifier = Modifier.align(Alignment.Center),
                                color = AppTheme.colors.textSecondary
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = 4.dp,
                                    bottom = 80.dp
                                )
                            ) {
                                items(
                                    items = groupedVideoItems,
                                    span = { item ->
                                        if (item is GalleryItem.Header) GridItemSpan(3) else GridItemSpan(1)
                                    }
                                ) { galleryItem ->
                                    when (galleryItem) {
                                        is GalleryItem.Header -> {
                                            Column(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp)
                                                    .padding(top = 20.dp, bottom = 8.dp)
                                            ) {
                                                Text(
                                                    text = galleryItem.label,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                HorizontalDivider(
                                                    thickness = 0.5.dp,
                                                    color = Color.White.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                        is GalleryItem.Media -> {
                                            val media = galleryItem.media
                                            val isChecked = media.uri in checkedUris
                                            val videoThumbnail = remember(media.uri) { getVideoThumbnailBitmap(context, media.uri) }
                                            Box(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(AppTheme.colors.cardBackground)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (checkedUris.isNotEmpty()) {
                                                                checkedUris =
                                                                    if (isChecked) checkedUris - media.uri else checkedUris + media.uri
                                                            } else {
                                                                selectedVideoUri = media.uri
                                                                showVideoPlayer = true
                                                                showVideoMetadataDialog = false
                                                            }
                                                        },
                                                        onLongClick = {
                                                            checkedUris =
                                                                if (isChecked) checkedUris - media.uri else checkedUris + media.uri
                                                        }
                                                    )
                                            ) {
                                                if (videoThumbnail != null) {
                                                    AsyncImage(
                                                        model = videoThumbnail,
                                                        contentDescription = "Selected Video",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(AppTheme.colors.cardBackground),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Fullscreen,
                                                            contentDescription = null,
                                                            tint = AppTheme.colors.accent,
                                                            modifier = Modifier.size(32.dp)
                                                        )
                                                    }
                                                }
                                                if (checkedUris.isNotEmpty()) {
                                                    Icon(
                                                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                        contentDescription = null,
                                                        tint = if (isChecked) AppTheme.colors.accent else Color.White.copy(
                                                            alpha = 0.5f
                                                        ),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (checkedUris.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 12.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = AppTheme.colors.cardBackground.copy(alpha = 0.85f),
                    contentColor = AppTheme.colors.textPrimary,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedMediaTab == 0) {
                            IconButton(onClick = {
                                val firstSelected = checkedUris.firstOrNull()
                                selectedImageIndex =
                                    filteredItems.indexOfFirst { it.uri == firstSelected }.takeIf { it != -1 }
                            }) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "View Selected",
                                    tint = AppTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = if (selectedMediaTab == 0) "image/*" else "video/*"
                                putParcelableArrayListExtra(
                                    Intent.EXTRA_STREAM,
                                    ArrayList(checkedUris.toList())
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    if (selectedMediaTab == 0) "Share selected images" else "Share selected videos"
                                )
                            )
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Selected",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        if (selectedMediaTab == 0 && checkedUris.size == 1) {
                            IconButton(onClick = {
                                pendingWallpaperUri = checkedUris.first()
                                showWallpaperConfirm = true
                            }) {
                                Icon(
                                    Icons.Default.Wallpaper,
                                    contentDescription = "Set as Wallpaper",
                                    tint = AppTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            urisToArchive = checkedUris.toList(); showArchiveDialog = true
                        }) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = "Archive Selected",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = {
                            deleteMediaType = if (selectedMediaTab == 0) DeleteMediaType.PICTURES else DeleteMediaType.VIDEOS
                            urisToDelete = checkedUris.toList(); showDeleteDialog = true
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { checkedUris = emptySet() }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedMediaTab == 0) {
        selectedImageIndex?.let { initialIndex ->
            Dialog(
                onDismissRequest = { selectedImageIndex = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                val pagerState =
                    rememberPagerState(initialPage = initialIndex, pageCount = { filteredItems.size })
                var isImageZoomed by remember { mutableStateOf(false) }
                var systemBarsVisible by remember { mutableStateOf(true) }
                val activity = LocalContext.current.getActivity()

                LaunchedEffect(pagerState.currentPage) {
                    selectedImageIndex = pagerState.currentPage
                }

                LaunchedEffect(systemBarsVisible) {
                    activity?.window?.let { window ->
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        if (systemBarsVisible) {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                        } else {
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        activity?.window?.let { window ->
                            WindowInsetsControllerCompat(window, window.decorView).show(
                                WindowInsetsCompat.Type.systemBars()
                            )
                        }
                    }
                }

                val currentUri = filteredItems.getOrNull(pagerState.currentPage)?.uri
                val currentEditState = currentUri?.let { imageEdits[it] } ?: EditState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { systemBarsVisible = !systemBarsVisible }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isImageZoomed,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val uri = filteredItems[page].uri
                        val editState = imageEdits[uri] ?: EditState()
                        ZoomableImage(
                            model = uri,
                            contentDescription = "Full Screen Image",
                            rotation = editState.rotation,
                            flipHorizontal = editState.flipHorizontal,
                            flipVertical = editState.flipVertical,
                            isCropped = editState.isCropped,
                            isGrayscale = editState.isGrayscale,
                            onZoomStateChanged = { isZoomed -> isImageZoomed = isZoomed },
                            onTap = { systemBarsVisible = !systemBarsVisible }
                        )
                    }

                    if (systemBarsVisible) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 4.dp)
                                .padding(bottom = 12.dp)
                                .navigationBarsPadding()
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = AppTheme.colors.cardBackground.copy(alpha = 0.9f),
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        imageEdits =
                                            imageEdits + (uri to currentEditState.copy(rotation = currentEditState.rotation - 90f))
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.RotateLeft,
                                        "Rotate Anti-clockwise",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        imageEdits =
                                            imageEdits + (uri to currentEditState.copy(rotation = currentEditState.rotation + 90f))
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.RotateRight,
                                        "Rotate Clockwise",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        imageEdits =
                                            imageEdits + (uri to currentEditState.copy(flipHorizontal = !currentEditState.flipHorizontal))
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        "Flip Horizontal",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        imageEdits =
                                            imageEdits + (uri to currentEditState.copy(flipVertical = !currentEditState.flipVertical))
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.SwapVert,
                                        "Flip Vertical",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        imageEdits =
                                            imageEdits + (uri to currentEditState.copy(isGrayscale = !currentEditState.isGrayscale))
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.FilterBAndW,
                                        "Black and White",
                                        tint = if (currentEditState.isGrayscale) AppTheme.colors.accent else AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        scope.launch {
                                            val croppedUri = withContext(Dispatchers.IO) {
                                                cropImageAndSaveCopy(context, uri)
                                            }
                                            if (croppedUri != null) {
                                                getMediaItemFromUri(context, croppedUri)?.let { item ->
                                                    imageItems = (listOf(item) + imageItems).distinctBy { it.uri }
                                                }
                                                Toast.makeText(context, "Cropped copy saved", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Could not crop image", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Crop,
                                        "Crop and Save Copy",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = { showMetadataDialog = true }) {
                                    Icon(
                                        Icons.Default.Info,
                                        "Metadata",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    currentUri?.let { uri ->
                                        urisToArchive = listOf(uri)
                                        showArchiveDialog = true
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Archive,
                                        "Archive",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    deleteMediaType = DeleteMediaType.PICTURES
                                    urisToDelete = listOf(currentUri!!); showDeleteDialog = true
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "Delete",
                                        tint = AppTheme.colors.boxText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${pagerState.currentPage + 1} / ${filteredItems.size}",
                            color = AppTheme.colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 40.dp)
                                .background(
                                    AppTheme.colors.cardBackground.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsMenu) {
        SettingsDialog(
            onDismiss = { showSettingsMenu = false },
            onExcludePictureFolders = {
                exclusionMediaType = ExclusionMediaType.PICTURES
                showSettingsMenu = false
                showExcludedFoldersDialog = true
            },
            onExcludeVideoFolders = {
                exclusionMediaType = ExclusionMediaType.VIDEOS
                showSettingsMenu = false
                showExcludedFoldersDialog = true
            },
            onManagePermissions = {
                showSettingsMenu = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onCloudMediaAccess = {
                showSettingsMenu = false
                val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            onSetAsDefault = {
                showSettingsMenu = false
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            onAbout = { showSettingsMenu = false; showAboutDialog = true },
            onFixMetadata = { showSettingsMenu = false; showFixMetadataDialog = true },
            onRecycleBin = { showSettingsMenu = false; showRecycleBinSettings = true },
            onArchive = { showSettingsMenu = false; showArchiveSettings = true },
            onFilterSettings = { showSettingsMenu = false; showFilterSettings = true },
            onOpenRecycleBinFolder = {
                showSettingsMenu = false
                openManagedFolderInPicker(context, "RecycleBin")
            },
            onOpenArchiveFolder = {
                showSettingsMenu = false
                openManagedFolderInPicker(context, "Archive")
            },
            onUsedStorage = { showSettingsMenu = false; showUsedStorageDialog = true }
        )
    }

    if (showVideoPlayer && selectedVideoUri != null) {
        VideoPlayerDialog(
            uri = selectedVideoUri!!,
            onDismiss = {
                showVideoPlayer = false
                showVideoMetadataDialog = false
                selectedVideoUri = null
            },
            onMetadata = { showVideoMetadataDialog = true },
            onDelete = { uri ->
                deleteMediaType = DeleteMediaType.VIDEOS
                urisToDelete = listOf(uri)
                showDeleteDialog = true
            }
        )
    }

    if (showVideoMetadataDialog && selectedVideoUri != null) {
        val uri = selectedVideoUri!!
        VideoMetadataDialog(
            uri = uri,
            metadataRemoved = uri in videoMetadataRemovedUris,
            onRemoveMetadata = { videoMetadataRemovedUris = videoMetadataRemovedUris + uri },
            onDismiss = { showVideoMetadataDialog = false }
        )
    }

    if (showMetadataDialog) {
        filteredItems.getOrNull(selectedImageIndex ?: 0)?.let { item ->
            val uri = item.uri
            MetadataDialog(
                uri = uri,
                editState = imageEdits[uri] ?: EditState(),
                onRemoveMetadata = {
                    val current = imageEdits[uri] ?: EditState()
                    imageEdits = imageEdits + (uri to current.copy(metadataRemoved = true))
                },
                onDismiss = { showMetadataDialog = false }
            )
        }
    }

    if (showExcludedFoldersDialog) {
        ExcludedFoldersDialog(
            title = if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                "Excluded Folders"
            } else {
                "Excluded Folders"
            },
            excludedFolders = if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                excludedPictureFolders
            } else {
                excludedVideoFolders
            },
            emptyText = if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                "No folders excluded."
            } else {
                "No folders excluded."
            },
            addButtonLabel = if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                "Add folders for exclusions"
            } else {
                "Add folders for exclusions"
            },
            onRemoveFolder = { folder ->
                if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                    excludedPictureFolders = excludedPictureFolders - folder
                } else {
                    excludedVideoFolders = excludedVideoFolders - folder
                }
            },
            onAddFolder = { showPreExcludeConfirm = true },
            onDismiss = { showExcludedFoldersDialog = false }
        )
    }

    if (showPreExcludeConfirm) {
        AlertDialog(
            onDismissRequest = { showPreExcludeConfirm = false },
            title = {
                Text(
                    "Exclude Folders",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                        "Allow app to exclude picture folders?"
                    } else {
                        "Allow app to exclude video folders?"
                    },
                    color = AppTheme.colors.textSecondary
                )
            },
            containerColor = AppTheme.colors.background,
            confirmButton = {
                TextButton(onClick = {
                    showPreExcludeConfirm = false; folderPickerLauncher.launch(
                    null
                )
                }) {
                    Text("Continue", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreExcludeConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    if (showPostExcludeConfirm && pendingExcludedUri != null) {
        val folderName = try {
            val docId = DocumentsContract.getTreeDocumentId(pendingExcludedUri!!)
            docId.split(":").lastOrNull()?.split("/")?.lastOrNull() ?: "Folder"
        } catch (_: Exception) {
            "Folder"
        }
        AlertDialog(
            onDismissRequest = { showPostExcludeConfirm = false; pendingExcludedUri = null },
            title = {
                Text(
                    "Confirm Exclusion",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                        "Allow exclusion for the \"$folderName\" folder? Pictures inside this folder will not be visible in the app."
                    } else {
                        "Allow exclusion for the \"$folderName\" folder? Videos inside this folder will not be visible in the app."
                    },
                    color = AppTheme.colors.textSecondary
                )
            },
            containerColor = AppTheme.colors.background,
            confirmButton = {
                TextButton(onClick = {
                    val path = getRealPathFromTreeUri(pendingExcludedUri!!)
                    if (path != null) {
                        if (exclusionMediaType == ExclusionMediaType.PICTURES) {
                            excludedPictureFolders = excludedPictureFolders + path
                            Toast.makeText(context, "Folder excluded", Toast.LENGTH_SHORT).show()
                        } else {
                            excludedVideoFolders = excludedVideoFolders + path
                            Toast.makeText(context, "Folder excluded", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Could not resolve folder path", Toast.LENGTH_SHORT)
                            .show()
                    }
                    showPostExcludeConfirm = false
                    pendingExcludedUri = null
                }) {
                    Text("Allow", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPostExcludeConfirm = false; pendingExcludedUri = null
                }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    if (showDeleteDialog) {
        val isDefaultSettings = !useRecycleBin && !archiveAfter30Days
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = AppTheme.colors.background,
            title = {
                Text(
                    if (isDefaultSettings) "Warning!" else "Confirm Deletion",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isDefaultSettings) {
                        val mediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "videos" else "pictures"
                        "Before you continue make sure to first check the settings menu for the recycle bin and archive settings. By default selected $mediaLabel will be permanently deleted from this device!"
                    }
                    else {
                        val mediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "video(s)" else "picture(s)"
                        "Are you sure you want to continue? Selected $mediaLabel will be ${if (useRecycleBin) "moved to the Recycle Bin" else "permanently deleted"}!"
                    },
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteMedia(urisToDelete) }) {
                    Text("Yes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("No", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    if (showRecycleBinSettings) {
        RecycleBinSettingsDialog(
            useRecycleBin = useRecycleBin,
            recycleBinDays = recycleBinDays,
            onUseRecycleBinChanged = { useRecycleBin = it },
            onRecycleBinDaysChanged = { recycleBinDays = it },
            onRestorePictures = { showRestoreRecycleBinDialog = true },
            onEmptyRecycleBin = { showEmptyRecycleBinDialog = true },
            onDismiss = { showRecycleBinSettings = false }
        )
    }

    if (showArchiveSettings) {
        ArchiveSettingsDialog(
            archiveAfter30Days = archiveAfter30Days,
            onArchiveAfter30DaysChanged = { archiveAfter30Days = it },
            onRestorePictures = { showRestoreArchiveDialog = true },
            onDismiss = { showArchiveSettings = false }
        )
    }

    if (showArchiveDialog) {
        val mediaLabel = if (deleteMediaType == DeleteMediaType.VIDEOS) "video(s)" else "picture(s)"
        ArchiveSelectionDialog(
            mediaLabel = mediaLabel,
            onConfirm = {
                archiveMedia(urisToArchive)
                showArchiveDialog = false
            },
            onDismiss = { showArchiveDialog = false }
        )
    }

    if (showRestoreRecycleBinDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreRecycleBinDialog = false },
            title = { Text("Restore Pictures", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Restore all pictures from Recycle Bin?", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { restoreFromRecycleBin(); showRestoreRecycleBinDialog = false }) {
                    Text("Restore", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreRecycleBinDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.background
        )
    }

    if (showRestoreArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreArchiveDialog = false },
            title = { Text("Restore Pictures", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Restore all pictures from Archive?", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { restoreFromArchive(); showRestoreArchiveDialog = false }) {
                    Text("Restore", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreArchiveDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.background
        )
    }

    if (showEmptyRecycleBinDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyRecycleBinDialog = false },
            title = { Text("Empty Recycle Bin", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete all pictures in the Recycle Bin?", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.emptyRecycleBin()
                        showEmptyRecycleBinDialog = false
                    }
                }) {
                    Text("Empty", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyRecycleBinDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.background
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showFixMetadataDialog) {
        FixMetadataDialog(
            onFixDateTimeOriginal = {
                val selectedUri = filteredItems.getOrNull(selectedImageIndex ?: 0)?.uri
                if (selectedUri == null) {
                    Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                    showFixMetadataDialog = false
                    return@FixMetadataDialog
                }

                scope.launch {
                    val fixed = withContext(Dispatchers.IO) {
                        fixDateTimeOriginalIfMissing(context, selectedUri)
                    }

                    Toast.makeText(
                        context,
                        if (fixed) "DateTimeOriginal fixed" else "DateTimeOriginal already set or no date was found",
                        Toast.LENGTH_SHORT
                    ).show()
                    showFixMetadataDialog = false
                }
            },
            onDismiss = { showFixMetadataDialog = false }
        )
    }

    if (showUsedStorageDialog) {
        UsedStorageDialog(
            pictureBytes = usedPictureBytes,
            videoBytes = usedVideoBytes,
            onDismiss = { showUsedStorageDialog = false }
        )
    }

    if (showWallpaperConfirm) {
        AlertDialog(
            onDismissRequest = { showWallpaperConfirm = false; pendingWallpaperUri = null },
            containerColor = AppTheme.colors.background,
            title = {
                Text(
                    "Confirm Wallpaper",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to set this picture as your wallpaper?",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingWallpaperUri?.let { setAsWallpaper(context, it) }
                    showWallpaperConfirm = false
                    pendingWallpaperUri = null
                }) {
                    Text("Set as Wallpaper", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWallpaperConfirm = false; pendingWallpaperUri = null }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            }
        )
    }

    if (showFilterSettings) {
        FilterSettingsDialog(
            filterByDate = filterByDate,
            filterByExifSoftware = filterByExifSoftware,
            filterByExifArtist = filterByExifArtist,
            onFilterByDateChanged = { filterByDate = it },
            onFilterByExifSoftwareChanged = { filterByExifSoftware = it },
            onFilterByExifArtistChanged = { filterByExifArtist = it },
            onRepairMissingDates = {
                scope.launch {
                    val itemsToRepair = filteredItems.filter { it.isDateFallback }
                    var repairedCount = 0
                    withContext(Dispatchers.IO) {
                        itemsToRepair.forEach { item ->
                            if (fixDateTimeOriginalIfMissing(context, item.uri)) {
                                repairedCount++
                            }
                        }
                    }
                    Toast.makeText(context, "Repaired $repairedCount items", Toast.LENGTH_SHORT).show()
                    showFilterSettings = false
                }
            },
            onDismiss = { showFilterSettings = false }
        )
    }
}

@Composable
fun FilterSettingsDialog(
    filterByDate: Boolean,
    filterByExifSoftware: Boolean,
    filterByExifArtist: Boolean,
    onFilterByDateChanged: (Boolean) -> Unit,
    onFilterByExifSoftwareChanged: (Boolean) -> Unit,
    onFilterByExifArtistChanged: (Boolean) -> Unit,
    onRepairMissingDates: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filter by date", color = AppTheme.colors.textPrimary)
                    Switch(
                        checked = filterByDate,
                        onCheckedChange = onFilterByDateChanged
                    )
                }

                if (filterByDate) {
                    Button(
                        onClick = onRepairMissingDates,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.boxBackground)
                    ) {
                        Text("Repair missing dates", color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filter by EXIF software", color = AppTheme.colors.textPrimary)
                    Switch(
                        checked = filterByExifSoftware,
                        onCheckedChange = onFilterByExifSoftwareChanged
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Filter by EXIF author", color = AppTheme.colors.textPrimary)
                    Switch(
                        checked = filterByExifArtist,
                        onCheckedChange = onFilterByExifArtistChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close", color = AppTheme.colors.accent) }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onExcludePictureFolders: () -> Unit,
    onExcludeVideoFolders: () -> Unit,
    onManagePermissions: () -> Unit,
    onCloudMediaAccess: () -> Unit,
    onSetAsDefault: () -> Unit,
    onAbout: () -> Unit,
    onFixMetadata: () -> Unit,
    onRecycleBin: () -> Unit,
    onArchive: () -> Unit,
    onFilterSettings: () -> Unit,
    onOpenRecycleBinFolder: () -> Unit,
    onOpenArchiveFolder: () -> Unit,
    onUsedStorage: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                TextButton(
                    onClick = onFixMetadata,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Fix metadata",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onRecycleBin,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Recycle Bin",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onArchive,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Archive",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onFilterSettings,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FilterBAndW,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Filter",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onOpenRecycleBinFolder,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Open Recycle Bin folder",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onOpenArchiveFolder,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Open Archive folder",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onExcludePictureFolders,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Exclude picture folders",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onExcludeVideoFolders,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Exclude video folders",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }
                TextButton(
                    onClick = onManagePermissions,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Manage Permissions",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }
                TextButton(
                    onClick = onCloudMediaAccess,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Cloud media access",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }
                TextButton(
                    onClick = onSetAsDefault,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Set as default app",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }
                TextButton(
                    onClick = onUsedStorage,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "Used storage",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }

                TextButton(
                    onClick = onAbout,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary
                        )
                        Spacer(Modifier.width(12.dp)); Text(
                        "About",
                        color = AppTheme.colors.textPrimary
                    )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close", color = AppTheme.colors.accent) }
            }
        }
    }
}

@Composable
fun UsedStorageDialog(
    pictureBytes: Long,
    videoBytes: Long,
    onDismiss: () -> Unit
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.ROOT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Used storage",
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Pictures: ${formatMegabytes(pictureBytes, locale)}",
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Videos: ${formatMegabytes(videoBytes, locale)}",
                    color = AppTheme.colors.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppTheme.colors.accent)
            }
        },
        containerColor = AppTheme.colors.background
    )
}

@Composable
fun FixMetadataDialog(
    onFixDateTimeOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Fix metadata",
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Repair missing EXIF metadata for the selected image.",
                color = AppTheme.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onFixDateTimeOriginal) {
                Text("Fix date time original", color = AppTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.background
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onMetadata: () -> Unit,
    onDelete: (Uri) -> Unit
) {
    val context = LocalContext.current
    var showVideoToolbar by remember { mutableStateOf(false) }
    val player = remember(context, uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        hideController()
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            showVideoToolbar = visibility == View.VISIBLE
                        })
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close video",
                    tint = Color.White
                )
            }

            if (showVideoToolbar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 76.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onMetadata) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Video metadata",
                                tint = AppTheme.colors.boxText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { onDelete(uri) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete video",
                                tint = AppTheme.colors.boxText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoMetadataDialog(
    uri: Uri,
    metadataRemoved: Boolean,
    onRemoveMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("Unknown") }
    var fileSize by remember { mutableStateOf("Unknown") }
    var videoMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(uri, metadataRemoved) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: "Unknown"
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = "${cursor.getLong(sizeIndex) / 1024} KB"
                    }
                }
            }
        } catch (_: Exception) {
            fileName = uri.lastPathSegment ?: "Unknown"
        }

        if (!metadataRemoved) {
            val mediaStoreUri = resolveToMediaStoreUri(context, uri)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaStoreUri)
                val metadata = linkedMapOf<String, String>()
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                    metadata["Duration"] = "${it.toLongOrNull()?.div(1000) ?: 0}s"
                }
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (!width.isNullOrBlank() && !height.isNullOrBlank()) {
                    metadata["Resolution"] = "${width}x$height"
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                    metadata["MIME type"] = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                    metadata["Date"] = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let {
                    metadata["Location"] = it
                }
                videoMetadata = metadata
            } catch (_: Exception) {
                videoMetadata = emptyMap()
            } finally {
                retriever.release()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Metadata",
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        containerColor = AppTheme.colors.background,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("File Name: $fileName", color = AppTheme.colors.textPrimary)
                Text("Size: $fileSize", color = AppTheme.colors.textPrimary)
                if (!metadataRemoved) {
                    videoMetadata.forEach { (key, value) ->
                        Text("$key: $value", color = AppTheme.colors.textPrimary)
                    }
                } else {
                    Text("Metadata has been removed.", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppTheme.colors.accent)
            }
        },
        dismissButton = {
            if (!metadataRemoved) {
                TextButton(onClick = onRemoveMetadata) {
                    Text("Remove Metadata", color = Color.White)
                }
            }
        }
    )
}

@Composable
fun ExcludedFoldersDialog(
    title: String,
    excludedFolders: Set<String>,
    emptyText: String,
    addButtonLabel: String,
    onRemoveFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (excludedFolders.isEmpty()) {
                    Text(
                        text = emptyText,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        excludedFolders.forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folder.trimEnd(File.separatorChar).split(File.separator)
                                        .lastOrNull() ?: folder,
                                    color = AppTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(onClick = { onRemoveFolder(folder) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddFolder,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.boxBackground)
                ) {
                    Text(addButtonLabel, color = Color.White)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close", color = AppTheme.colors.accent) }
            }
        }
    }
}

@SuppressLint("NonObservableLocale")
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LocalLocale.current.platformLocale).format(Date())
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.background,
        title = {
            Text(
                "About",
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("App name: Gallery", color = AppTheme.colors.textPrimary)
                Text("Version: 1.0.0", color = AppTheme.colors.textPrimary)
                Text("Build date: $buildDate", color = AppTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "© $currentYear RobertoTorino",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppTheme.colors.accent)
            }
        }
    )
}

@Composable
fun MetadataDialog(
    uri: Uri,
    editState: EditState,
    onRemoveMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("Unknown") }
    var fileSize by remember { mutableStateOf("Unknown") }
    var exifData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(uri) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "Unknown"
                    fileSize = "${cursor.getLong(sizeIndex) / 1024} KB"
                }
            }
        } catch (_: Exception) {
            fileName = uri.lastPathSegment ?: "Unknown"
        }
        if (!editState.metadataRemoved) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    val attributes = mutableMapOf<String, String>()
                    val tags = listOf(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_F_NUMBER,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_IMAGE_WIDTH,
                        ExifInterface.TAG_IMAGE_LENGTH,
                        ExifInterface.TAG_ARTIST,
                        ExifInterface.TAG_IMAGE_DESCRIPTION,
                        ExifInterface.TAG_BRIGHTNESS_VALUE,
                        ExifInterface.TAG_X_RESOLUTION,
                        ExifInterface.TAG_Y_RESOLUTION,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE
                    )
                    tags.forEach { tag -> exif.getAttribute(tag)?.let { attributes[tag] = it } }
                    exifData = attributes
                }
            } catch (_: Exception) {
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Metadata",
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        containerColor = AppTheme.colors.background,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("File Name: $fileName", color = AppTheme.colors.textPrimary)
                Text("Size: $fileSize", color = AppTheme.colors.textPrimary)
                if (!editState.metadataRemoved) {
                    exifData.forEach { (tag, value) ->
                        Text(
                            "$tag: $value",
                            color = AppTheme.colors.textPrimary
                        )
                    }
                } else {
                    Text("Metadata has been removed.", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Close",
                    color = AppTheme.colors.accent
                )
            }
        },
        dismissButton = {
            if (!editState.metadataRemoved) {
                TextButton(onClick = onRemoveMetadata) {
                    Text(
                        "Remove Metadata",
                        color = Color.White
                    )
                }
            }
        }
    )
}

private val screenshotRegex = Regex("""Screenshot_(\d{8})_(\d{6})""")
private val signalRegex = Regex("""signal-(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})""")
private val photoDashedDateTimeRegex = Regex("""PHOTO-((?:19|20)\d{2}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})""")
private val compactDateTimeRegex = Regex("""(?:^|\D)((?:19|20)\d{2})(\d{2})(\d{2})[_-]?(\d{2})(\d{2})(\d{2})(?:\D|$)""")

fun extractBestDate(context: Context, uri: Uri, displayName: String?, mimeType: String?, mediaStoreDateTaken: Long): DateResult {
    // 1. MediaStore DATE_TAKEN is generally reliable if present
    if (mediaStoreDateTaken > 0) return DateResult(mediaStoreDateTaken, MediaDateSource.MEDIA_STORE)

    // 2. Try EXIF DateTimeOriginal/DateTime/Digitized when available for images
    if (mimeType?.startsWith("image/") == true) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                exifDate?.let { dateStr ->
                    parseExifDateToMillis(dateStr)?.let { parsed ->
                        return DateResult(parsed, MediaDateSource.EXIF)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 3. Try MediaMetadataRetriever for videos
    if (mimeType?.startsWith("video/") == true) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            retriever.release()
            dateStr?.let {
                val formats = arrayOf(
                    "yyyyMMdd'T'HHmmss.SSS'Z'",
                    "yyyyMMdd'T'HHmmss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "EEE MMM dd HH:mm:ss zzz yyyy"
                )
                for (format in formats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.US)
                        if (format.contains("'Z'") || format.contains("zzz")) {
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        sdf.parse(it)?.time?.let { parsed ->
                            return DateResult(parsed, MediaDateSource.VIDEO_METADATA)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    // 4. Try Filename parsing (Fast)
    displayName?.let { name ->
        screenshotRegex.find(name)?.let { match ->
            try {
                val datePart = match.groupValues[1]
                val timePart = match.groupValues[2]
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                sdf.parse("${datePart}_${timePart}")?.time?.let { return DateResult(it, MediaDateSource.FILENAME) }
            } catch (_: Exception) {}
        }
        signalRegex.find(name)?.let { match ->
            try {
                val dateStr = match.groupValues[1]
                val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                sdf.parse(dateStr)?.time?.let { return DateResult(it, MediaDateSource.FILENAME) }
            } catch (_: Exception) {}
        }
        photoDashedDateTimeRegex.find(name)?.let { match ->
            try {
                val dateStr = match.groupValues[1]
                val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
                sdf.parse(dateStr)?.time?.let { return DateResult(it, MediaDateSource.FILENAME) }
            } catch (_: Exception) {}
        }
        compactDateTimeRegex.find(name)?.let { match ->
            try {
                val dateStr = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]} ${match.groupValues[4]}:${match.groupValues[5]}:${match.groupValues[6]}"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.parse(dateStr)?.time?.let { return DateResult(it, MediaDateSource.FILENAME) }
            } catch (_: Exception) {}
        }
    }

    return DateResult(0L, MediaDateSource.UNKNOWN)
}

fun parseExifDateToMillis(dateStr: String): Long? {
    val raw = dateStr.trim()
    val normalized = raw.replace('T', ' ')
        .replace('-', ':')
        .replace('/', ':')

    val candidates = sequence {
        yield(raw)
        yield(normalized)
        if (normalized.length >= 19) yield(normalized.substring(0, 19))
    }.distinct()

    val formats = arrayOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy:MM:dd HH:mm",
        "yyyyMMdd HHmmss",
        "yyyyMMdd_HHmmss"
    )

    for (candidate in candidates) {
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US).apply { isLenient = false }
                sdf.parse(candidate)?.time?.let { return it }
            } catch (_: Exception) {}
        }

    }
    return null
}

fun fixDateTimeOriginalIfMissing(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            val exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)

            var mediaStoreDateTaken = 0L
            var mediaStoreDateAdded = 0L
            var displayName: String? = null
            val mimeType = context.contentResolver.getType(uri) ?: ""

            context.contentResolver.query(
                resolveToMediaStoreUri(context, uri),
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DATE_ADDED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dateTakenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (dateTakenIndex >= 0) mediaStoreDateTaken = cursor.getLong(dateTakenIndex)
                    if (dateAddedIndex >= 0) mediaStoreDateAdded = cursor.getLong(dateAddedIndex)
                }
            }

            val isExifMissing = exifDate.isNullOrBlank() || exifDate.contains("0000:00:00")

            if (!isExifMissing) {
                if (mediaStoreDateTaken <= 0) {
                    val dateMillis = parseExifDateToMillis(exifDate!!)
                    if (dateMillis != null && dateMillis > 0) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DATE_TAKEN, dateMillis)
                        }
                        context.contentResolver.update(resolveToMediaStoreUri(context, uri), values, null, null)
                        return true
                    }
                }
                return false
            }

            val bestDateResult = extractBestDate(context, uri, displayName, mimeType, mediaStoreDateTaken)
            var dateMillis = bestDateResult.date
            
            if (dateMillis <= 0L) {
                if (mediaStoreDateAdded > 0) {
                    dateMillis = mediaStoreDateAdded * 1000
                } else {
                    val path = getRealPathFromUri(context, uri)
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            dateMillis = file.lastModified()
                        }
                    }
                }
            }

            if (dateMillis <= 0L) return false

            val dateToWrite = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(dateMillis))
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateToWrite)
            exif.saveAttributes()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATE_TAKEN, dateMillis)
            }
            context.contentResolver.update(resolveToMediaStoreUri(context, uri), values, null, null)

            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

fun hasRequestedExifTags(
    context: Context,
    uri: Uri,
    requireSoftware: Boolean,
    requireArtist: Boolean
): Boolean {
    if (!requireSoftware && !requireArtist) return true
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val hasSoftware = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.isNotBlank() == true
            val hasArtist = exif.getAttribute(ExifInterface.TAG_ARTIST)?.isNotBlank() == true
            (!requireSoftware || hasSoftware) && (!requireArtist || hasArtist)
        } ?: false
    } catch (_: Exception) {
        false
    }
}

fun writeBackMissingExifTags(
    context: Context,
    uri: Uri,
    software: String?,
    artist: String?
): Boolean {
    if (software.isNullOrBlank() && artist.isNullOrBlank()) return false
    return try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            var hasChanges = false

            if (!software.isNullOrBlank() &&
                exif.getAttribute(ExifInterface.TAG_SOFTWARE).isNullOrBlank()
            ) {
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, software)
                hasChanges = true
            }

            if (!artist.isNullOrBlank() &&
                exif.getAttribute(ExifInterface.TAG_ARTIST).isNullOrBlank()
            ) {
                exif.setAttribute(ExifInterface.TAG_ARTIST, artist)
                hasChanges = true
            }

            if (hasChanges) {
                exif.saveAttributes()
            }
            hasChanges
        } ?: false
    } catch (_: Exception) {
        false
    }
}

fun getMediaItemFromUri(context: Context, uri: Uri): MediaItem? {
    val mediaStoreUri = resolveToMediaStoreUri(context, uri)
    val projection = arrayOf(
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DATE_TAKEN
    )

    var mediaItem: MediaItem? = null
    try {
        context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                val dateTakenRaw = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                val bestDateResult = extractBestDate(context, uri, name, mimeType, dateTakenRaw)
                val isFallback = bestDateResult.source == MediaDateSource.FILENAME || bestDateResult.source == MediaDateSource.UNKNOWN
                mediaItem = MediaItem(uri, path, name, size, mimeType, dateAdded, bestDateResult.date, isDateFallback = isFallback)
            }
        }
    } catch (_: Exception) {}

    if (mediaItem == null) {
        // Fallback for non-MediaStore URIs
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: "Unknown"
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val now = System.currentTimeMillis()
                    val bestDateResult = extractBestDate(context, uri, name, mimeType, 0L)
                    mediaItem = MediaItem(uri, uri.path ?: "", name, size, mimeType, now / 1000, bestDateResult.date, isDateFallback = true)
                }
            }
        } catch (_: Exception) {}
    }

    return mediaItem
}

@Composable
fun RecycleBinSettingsDialog(
    useRecycleBin: Boolean,
    recycleBinDays: Int,
    onUseRecycleBinChanged: (Boolean) -> Unit,
    onRecycleBinDaysChanged: (Int) -> Unit,
    onRestorePictures: () -> Unit,
    onEmptyRecycleBin: () -> Unit,
    onDismiss: () -> Unit
) {
    var showEnableConfirm by remember { mutableStateOf(false) }
    var showDisableWarning by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()) {
                Text(
                    text = "Recycle Bin Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use Recycle Bin", color = AppTheme.colors.textPrimary)
                    Switch(
                        checked = useRecycleBin,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showEnableConfirm = true
                            } else {
                                showDisableWarning = true
                            }
                        }
                    )
                }

                if (!useRecycleBin) {
                    Text(
                        "Without using a recycle bin your pictures will be immediately and permanently removed from your device when you choose to delete them!",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Permanently remove pictures from the Recycle Bin after:",
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onRecycleBinDaysChanged(30) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = recycleBinDays == 30,
                            onClick = { onRecycleBinDaysChanged(30) }
                        )
                        Text("30 Days", color = AppTheme.colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onRecycleBinDaysChanged(60) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = recycleBinDays == 60,
                            onClick = { onRecycleBinDaysChanged(60) }
                        )
                        Text("60 Days", color = AppTheme.colors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRestorePictures,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.boxBackground)
                    ) {
                        Text("Restore pictures from recycle bin", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onEmptyRecycleBin,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text("Empty recycle bin now", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close", color = AppTheme.colors.accent) }
            }
        }
    }

    if (showEnableConfirm) {
        AlertDialog(
            onDismissRequest = { showEnableConfirm = false },
            title = { Text("Use Recycle Bin?", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("When setting the recycle bin your pictures will be moved to the recycle bin upon deletion.", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onUseRecycleBinChanged(true)
                    showEnableConfirm = false
                }) {
                    Text("Use Recycle Bin", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.background
        )
    }

    if (showDisableWarning) {
        AlertDialog(
            onDismissRequest = { showDisableWarning = false },
            title = { Text("Warning", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Without using a recycle bin your pictures will be immediately and permanently removed from your device when you choose to delete them!", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onUseRecycleBinChanged(true) // Stay enabled
                    showDisableWarning = false
                }) {
                    Text("Use Recycle Bin", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUseRecycleBinChanged(false)
                    showDisableWarning = false
                }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.background
        )
    }
}

@Composable
fun ArchiveSettingsDialog(
    archiveAfter30Days: Boolean,
    onArchiveAfter30DaysChanged: (Boolean) -> Unit,
    onRestorePictures: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()) {
                Text(
                    text = "Archive Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use Archive", color = AppTheme.colors.textPrimary)
                    Switch(
                        checked = archiveAfter30Days,
                        onCheckedChange = onArchiveAfter30DaysChanged
                    )
                }

                Text(
                    "When activated deleted pictures will be moved to Archive after 30 days instead of permanently deleted.",
                    color = AppTheme.colors.textPrimary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRestorePictures,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.boxBackground)
                ) {
                    Text("Restore pictures from Archive", color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close", color = AppTheme.colors.accent) }
            }
        }
    }
}

@Composable
fun ArchiveSelectionDialog(
    mediaLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("Move $mediaLabel to Archive?", color = AppTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Archive", color = AppTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.background
    )
}
