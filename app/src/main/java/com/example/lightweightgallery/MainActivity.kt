package com.robertotorino.gallery

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.let
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.robertotorino.gallery.worker.CleanupWorker
import com.robertotorino.gallery.ui.theme.AppTheme
import com.robertotorino.gallery.ui.theme.GalleryTheme
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.runtime.mutableIntStateOf
import com.robertotorino.gallery.data.GalleryDatabase
import com.robertotorino.gallery.data.MediaItem
import com.robertotorino.gallery.repository.MediaRepository
import androidx.compose.ui.platform.LocalLocale

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
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    try {
        context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
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

fun queryImages(context: Context): List<Uri> {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
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
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
        }
    }
    return uris
}

fun resolveToMediaStoreUri(context: Context, uri: Uri): Uri {
    if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
        return uri
    }
    if (uri.toString().contains("photopicker")) {
        val id = uri.lastPathSegment?.toLongOrNull()
        if (id != null) {
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
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
    }
    return uri
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
        if (permissions.values.all { it }) {
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
            text = "A simple app to quickly view your pictures.",
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
            text = "The app needs permissions to access your photos and show notifications.",
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

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(initialUri: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }

    val database = remember { GalleryDatabase.getDatabase(context) }
    val repository = remember { MediaRepository(context, database.recycledItemDao()) }

    var selectedImageUris by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var checkedUris by remember { mutableStateOf(setOf<Uri>()) }

    var imageEdits by rememberSaveable { mutableStateOf(mapOf<Uri, EditState>()) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var urisToDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var urisToArchive by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var excludedFolders by remember { mutableStateOf(setOf<String>()) }
    var showWallpaperConfirm by remember { mutableStateOf(false) }
    var pendingWallpaperUri by remember { mutableStateOf<Uri?>(null) }

    var useRecycleBin by remember { mutableStateOf(prefs.getBoolean("use_recycle_bin", false)) }
    var recycleBinDays by remember { mutableIntStateOf(prefs.getInt("recycle_bin_days", 30)) }
    var archiveAfter30Days by remember { mutableStateOf(prefs.getBoolean("archive_after_30_days", false)) }

    var showRecycleBinSettings by remember { mutableStateOf(false) }
    var showArchiveSettings by remember { mutableStateOf(false) }
    var showRestoreRecycleBinDialog by remember { mutableStateOf(false) }
    var showRestoreArchiveDialog by remember { mutableStateOf(false) }
    var showEmptyRecycleBinDialog by remember { mutableStateOf(false) }

    var showExcludedFoldersDialog by remember { mutableStateOf(false) }
    var showPreExcludeConfirm by remember { mutableStateOf(false) }
    var showPostExcludeConfirm by remember { mutableStateOf(false) }
    var pendingExcludedUri by remember { mutableStateOf<Uri?>(null) }
    var isLoaded by rememberSaveable { mutableStateOf(false) }

    // Load images from MediaStore and saved folders
    LaunchedEffect(Unit) {
        val savedFolders = prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
        excludedFolders = savedFolders

        val savedUris =
            prefs.getStringSet("added_uris", emptySet())?.map { Uri.parse(it) } ?: emptyList()

        val foundUris = queryImages(context)
        selectedImageUris = (foundUris + savedUris).distinct()

        initialUri?.let { uri ->
            val index = selectedImageUris.indexOf(uri)
            if (index != -1) {
                selectedImageIndex = index
            } else {
                selectedImageUris = listOf(uri) + selectedImageUris
                selectedImageIndex = 0
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

    LaunchedEffect(excludedFolders) {
        prefs.edit().putStringSet("excluded_folders", excludedFolders).apply()
    }

    LaunchedEffect(selectedImageUris) {
        if (isLoaded) {
            val uriStrings =
                selectedImageUris.filter { it.scheme == "content" }.map { it.toString() }.toSet()
            prefs.edit().putStringSet("added_uris", uriStrings).apply()
        }
    }

    val filteredUris = remember(selectedImageUris, excludedFolders) {
        selectedImageUris.filter { uri ->
            val path = getRealPathFromUri(context, uri) ?: uri.toString()
            excludedFolders.none { excluded -> path.startsWith(excluded) }
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
            selectedImageUris = selectedImageUris.filterNot { it in urisToDelete }
            if (selectedImageIndex != null && selectedImageUris.isEmpty()) {
                selectedImageIndex = null
            }
            urisToDelete = emptyList()
            Toast.makeText(context, "Picture(s) deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun resolveToMediaStoreUri(uri: Uri): Uri {
        return com.robertotorino.gallery.resolveToMediaStoreUri(context, uri)
    }

    fun deleteImages(uris: List<Uri>) {
        val activity = context.getActivity() ?: return
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (useRecycleBin) {
                        scope.launch {
                            var successCount = 0
                            uris.forEach { uri ->
                                val item = getMediaItemFromUri(context, uri)
                                if (item != null) {
                                    if (repository.moveToRecycleBin(item)) {
                                        successCount++
                                    }
                                }
                            }
                            selectedImageUris = selectedImageUris.filterNot { it in uris }
                            checkedUris = checkedUris.filterNot { it in uris }.toSet()
                            Toast.makeText(context, "Picture(s) moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        try {
                            val resolvedUris = uris.map { resolveToMediaStoreUri(it) }
                            if (resolvedUris.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Could not find picture on device storage",
                                    Toast.LENGTH_LONG
                                ).show()
                                return
                            }
                            val pendingIntent =
                                MediaStore.createDeleteRequest(context.contentResolver, resolvedUris)
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error initiating delete: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
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
            .setSubtitle("Authenticate to delete picture(s)")
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

    fun archiveImages(uris: List<Uri>) {
        scope.launch {
            val mediaItems = uris.mapNotNull { getMediaItemFromUri(context, it) }
            var successCount = 0
            mediaItems.forEach { item ->
                if (repository.moveToArchive(item)) {
                    successCount++
                }
            }
            selectedImageUris = selectedImageUris.filterNot { it in uris }
            checkedUris = checkedUris.filterNot { it in uris }.toSet()
            Toast.makeText(context, "Picture(s) moved to Archive", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreFromRecycleBin() {
        scope.launch {
            val items = database.recycledItemDao().getAll()
            items.filter { !it.isArchived }.forEach { repository.restoreItem(it) }
            val foundUris = queryImages(context)
            selectedImageUris = (foundUris + selectedImageUris).distinct()
            Toast.makeText(context, "Pictures restored from Recycle Bin", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreFromArchive() {
        scope.launch {
            val items = database.recycledItemDao().getAll()
            items.filter { it.isArchived }.forEach { repository.restoreItem(it) }
            val foundUris = queryImages(context)
            selectedImageUris = (foundUris + selectedImageUris).distinct()
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
            selectedImageUris = (selectedImageUris + uris).distinct()
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
                Text(
                    text = "Found ${filteredUris.size} pictures.",
                    modifier = Modifier.padding(16.dp),
                    color = AppTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (filteredUris.isEmpty()) {
                                Modifier.clickable { multiplePhotoPickerLauncher.launch(arrayOf("image/*")) }
                            } else Modifier
                        )
                ) {
                    if (filteredUris.isEmpty()) {
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
                            itemsIndexed(filteredUris) { index, uri ->
                                val isChecked = uri in checkedUris
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
                                                        if (isChecked) checkedUris - uri else checkedUris + uri
                                                } else {
                                                    selectedImageIndex = index
                                                }
                                            },
                                            onLongClick = {
                                                checkedUris =
                                                    if (isChecked) checkedUris - uri else checkedUris + uri
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(uri)
                                            .size(300) // Optimization: Target size for grid thumbnails
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Selected Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
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

            if (checkedUris.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth(0.95f)
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
                        IconButton(onClick = {
                            val firstSelected = checkedUris.firstOrNull()
                            selectedImageIndex =
                                filteredUris.indexOf(firstSelected).takeIf { it != -1 }
                        }) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "View Selected",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(
                                    Intent.EXTRA_STREAM,
                                    ArrayList(checkedUris.toList())
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    "Share selected images"
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
                        if (checkedUris.size == 1) {
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
                                Icons.Default.Inventory,
                                contentDescription = "Archive Selected",
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = {
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

    selectedImageIndex?.let { initialIndex ->
        Dialog(
            onDismissRequest = { selectedImageIndex = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val pagerState =
                rememberPagerState(initialPage = initialIndex, pageCount = { filteredUris.size })
            var isImageZoomed by remember { mutableStateOf(false) }
            var systemBarsVisible by remember { mutableStateOf(true) }
            val activity = LocalContext.current.getActivity()

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

            val currentUri = filteredUris.getOrNull(pagerState.currentPage)
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
                    val uri = filteredUris[page]
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
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp)
                            .navigationBarsPadding()
                            .fillMaxWidth(0.95f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
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
                                    Icons.Default.Inventory,
                                    "Archive",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = {
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
                        text = "${pagerState.currentPage + 1} / ${filteredUris.size}",
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

    if (showSettingsMenu) {
        SettingsDialog(
            onDismiss = { showSettingsMenu = false },
            onExcludeFolders = { showSettingsMenu = false; showExcludedFoldersDialog = true },
            onManagePermissions = {
                showSettingsMenu = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
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
            onRecycleBin = { showSettingsMenu = false; showRecycleBinSettings = true },
            onArchive = { showSettingsMenu = false; showArchiveSettings = true }
        )
    }

    if (showMetadataDialog) {
        filteredUris.getOrNull(selectedImageIndex ?: 0)?.let { uri ->
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
            excludedFolders = excludedFolders,
            onRemoveFolder = { folder -> excludedFolders = excludedFolders - folder },
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
                    "Allow app to exclude folders?",
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
                    "Allow exclusion for the \"$folderName\" folder? Pictures inside this folder will not be visible in the app.",
                    color = AppTheme.colors.textSecondary
                )
            },
            containerColor = AppTheme.colors.background,
            confirmButton = {
                TextButton(onClick = {
                    val path = getRealPathFromTreeUri(pendingExcludedUri!!)
                    if (path != null) {
                        excludedFolders = excludedFolders + path
                        Toast.makeText(context, "Folder excluded", Toast.LENGTH_SHORT).show()
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
                    if (isDefaultSettings) "Before you continue make sure to first check the settings menu for the recycle bin and archive settings. By default selected pictures will be permanently deleted from this device!"
                    else "Are you sure you want to continue? Selected picture(s) will be ${if (useRecycleBin) "moved to the Recycle Bin" else "permanently deleted"}!",
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteImages(urisToDelete) }) {
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
        ArchiveSelectionDialog(
            onConfirm = {
                archiveImages(urisToArchive)
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
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onExcludeFolders: () -> Unit,
    onManagePermissions: () -> Unit,
    onSetAsDefault: () -> Unit,
    onAbout: () -> Unit,
    onRecycleBin: () -> Unit,
    onArchive: () -> Unit
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
                    onClick = onExcludeFolders,
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
                        "Exclude folders",
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
fun ExcludedFoldersDialog(
    excludedFolders: Set<String>,
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
                    text = "Excluded Folders",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (excludedFolders.isEmpty()) {
                    Text(
                        text = "No folders excluded.",
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
                    Text("Add folders for exclusions", color = Color.White)
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

fun getMediaItemFromUri(context: Context, uri: Uri): MediaItem? {
    val mediaStoreUri = resolveToMediaStoreUri(context, uri)
    val projection = arrayOf(
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_ADDED
    )
    
    var mediaItem: MediaItem? = null
    try {
        context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                mediaItem = MediaItem(uri, path, name, size, mimeType, dateAdded)
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
                    mediaItem = MediaItem(uri, uri.path ?: "", name, size, "image/*", System.currentTimeMillis() / 1000)
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("Move Selection to Archive?", color = AppTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Move Selection to Archive", color = AppTheme.colors.accent)
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
