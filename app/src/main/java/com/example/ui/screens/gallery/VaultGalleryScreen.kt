package com.example.ui.screens.gallery

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.EncryptedPhotoEntity
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.OnPrimaryDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultGalleryScreen(
    viewModel: VaultViewModel,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val totalPhotos by viewModel.totalPhotoCount.collectAsState()
    val totalSizeBytes by viewModel.totalVaultSize.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Multiple photo picker launcher using Android zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importPhotos(uris)
        }
    }

    // MediaStore system confirmation launcher for deleting original gallery photos (Android 11+)
    val mediaStoreDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Original gallery photos deleted successfully")
            }
        }
        viewModel.dismissDeleteOriginalsDialog()
    }

    // Handle Snackbars
    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_gallery_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentCyan.copy(alpha = 0.15f))
                                .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Photo Vault",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "AES-256 Hardware Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentGreen
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("lock_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault Now",
                            tint = AccentCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null
                    )
                },
                text = { Text("Import Photos", fontWeight = FontWeight.SemiBold) },
                containerColor = AccentCyan,
                contentColor = OnPrimaryDark,
                modifier = Modifier.testTag("import_photos_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Import Progress Indicator
            if (uiState.isImporting) {
                LinearProgressIndicator(
                    progress = { uiState.importProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentCyan,
                    trackColor = DarkSurfaceVariant
                )
            }

            // Stats & Encryption Banner
            VaultStatsHeader(
                photoCount = totalPhotos,
                totalSizeBytes = totalSizeBytes ?: 0L
            )

            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search encrypted photos...", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.filter == VaultFilter.ALL,
                        onClick = { viewModel.setFilter(VaultFilter.ALL) },
                        label = { Text("All (${totalPhotos})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AccentCyan,
                            selectedLeadingIconColor = AccentCyan,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )

                    FilterChip(
                        selected = uiState.filter == VaultFilter.FAVORITES,
                        onClick = { viewModel.setFilter(VaultFilter.FAVORITES) },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentRed.copy(alpha = 0.2f),
                            selectedLabelColor = AccentRed,
                            selectedLeadingIconColor = AccentRed,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            // Photos Grid or Empty State
            if (uiState.photos.isEmpty() && !uiState.isImporting) {
                EmptyVaultState(
                    isSearch = uiState.searchQuery.isNotEmpty() || uiState.filter == VaultFilter.FAVORITES,
                    onImportClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vault_photos_grid")
                ) {
                    items(
                        items = uiState.photos,
                        key = { it.id }
                    ) { photo ->
                        EncryptedPhotoGridItem(
                            photo = photo,
                            onDecryptThumbnail = { size -> viewModel.decryptThumbnail(photo.encryptedFileName, size) },
                            onClick = { viewModel.selectPhoto(photo) },
                            onToggleFavorite = { viewModel.toggleFavorite(photo) }
                        )
                    }
                }
            }
        }
    }

    // Full-screen Decrypted Photo Viewer Dialog
    uiState.selectedPhotoForViewing?.let { photo ->
        DecryptedPhotoViewerDialog(
            photo = photo,
            onDecryptFullImage = { viewModel.decryptFullImage(photo.encryptedFileName) },
            onDismiss = { viewModel.selectPhoto(null) },
            onToggleFavorite = { viewModel.toggleFavorite(photo) },
            onSaveNotes = { notes -> viewModel.updateNotes(photo.id, notes) },
            onExport = { viewModel.exportPhotoToGallery(photo) },
            onDelete = { viewModel.deletePhoto(photo) }
        )
    }

    // Post-Import MediaStore Delete Confirmation Dialog
    if (uiState.showDeleteOriginalsDialog && uiState.recentlyImportedUris.isNotEmpty()) {
        DeleteOriginalsDialog(
            uriCount = uiState.recentlyImportedUris.size,
            onConfirmDelete = {
                val uris = uiState.recentlyImportedUris
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                        mediaStoreDeleteLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: standard resolver deletion
                        deleteUrisFallback(context, uris)
                        viewModel.dismissDeleteOriginalsDialog()
                    }
                } else {
                    deleteUrisFallback(context, uris)
                    viewModel.dismissDeleteOriginalsDialog()
                }
            },
            onKeepOriginals = {
                viewModel.dismissDeleteOriginalsDialog()
            }
        )
    }
}

private fun deleteUrisFallback(context: Context, uris: List<Uri>) {
    try {
        uris.forEach { uri ->
            context.contentResolver.delete(uri, null, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun VaultStatsHeader(
    photoCount: Int,
    totalSizeBytes: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Encrypted Vault Storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$photoCount Files",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = " • ${formatBytes(totalSizeBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Text(
                        text = "SECURED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentGreen
                    )
                }
            }
        }
    }
}

/**
 * Grid cell for encrypted photo. Dynamically requests in-memory decryption
 * into [Bitmap] on background thread without exposing raw files to disk.
 */
@Composable
private fun EncryptedPhotoGridItem(
    photo: EncryptedPhotoEntity,
    onDecryptThumbnail: suspend (Int) -> Bitmap?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val bitmapState by produceState<Bitmap?>(initialValue = null, key1 = photo.encryptedFileName) {
        value = onDecryptThumbnail(300)
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("encrypted_photo_item_${photo.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val bmp = bitmapState
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = photo.originalFileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = AccentCyan
                    )
                }
            }

            // Bottom gradient overlay with file info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = photo.originalFileName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Icon top-end
            if (photo.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = AccentRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultState(
    isSearch: Boolean,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(1.5.dp, AccentCyan.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearch) Icons.Default.Search else Icons.Default.Security,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isSearch) "No Matching Photos Found" else "Your Vault is Empty",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSearch) {
                    "Try adjusting your search query or filter."
                } else {
                    "Import sensitive photos to protect them with hardware-backed AES-256-GCM encryption."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!isSearch) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = OnPrimaryDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Photos to Encrypt", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Full-screen in-memory Decrypted Photo Viewer with pinch zoom, metadata details,
 * note taking, unvault (export) and permanent delete.
 */
@Composable
private fun DecryptedPhotoViewerDialog(
    photo: EncryptedPhotoEntity,
    onDecryptFullImage: suspend () -> Bitmap?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val fullBitmapState by produceState<Bitmap?>(initialValue = null, key1 = photo.encryptedFileName) {
        value = onDecryptFullImage()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditingNotes by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf(photo.notes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interactive Zoomable Image Canvas
                val bmp = fullBitmapState
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = photo.originalFileName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Decrypting AES-256 In-Memory...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Top Viewer Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = photo.originalFileName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(photo.importedTimestamp)),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (photo.isFavorite) AccentRed else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { showInfoSheet = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Photo Details",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export to Gallery
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = AccentCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("export_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unvault / Export")
                    }

                    // Delete Photo
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed.copy(alpha = 0.2f),
                            contentColor = AccentRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("delete_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    // Metadata Details Dialog
    if (showInfoSheet) {
        AlertDialog(
            onDismissRequest = { showInfoSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security & Metadata", color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(label = "Original File", value = photo.originalFileName)
                    DetailRow(label = "Encrypted Name", value = photo.encryptedFileName)
                    DetailRow(label = "Encryption", value = "AES-256 GCM (AEAD)")
                    DetailRow(label = "Key Protection", value = "Android Keystore (Hardware)")
                    DetailRow(label = "Payload Size", value = formatBytes(photo.fileSizeBytes))
                    DetailRow(label = "Resolution", value = if (photo.width > 0) "${photo.width} x ${photo.height}" else "Standard")
                    DetailRow(label = "MIME Type", value = photo.mimeType)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Encrypted Note:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        placeholder = { Text("Add private note...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveNotes(notesText)
                        showInfoSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = OnPrimaryDark)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfoSheet = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = AccentRed) },
            title = { Text("Permanently Delete Photo?", color = TextPrimary) },
            text = {
                Text(
                    "This will irreversibly shred and delete the AES-256 encrypted file from internal storage.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun DeleteOriginalsDialog(
    uriCount: Int,
    onConfirmDelete: () -> Unit,
    onKeepOriginals: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepOriginals,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Delete Plaintext Originals?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "$uriCount photo(s) have been encrypted with AES-256-GCM and saved in your secure vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "To ensure maximum privacy, you should delete the unprotected original copies from your public gallery so they are no longer visible to other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = OnPrimaryDark)
            ) {
                Text("Delete Originals from Gallery", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepOriginals) {
                Text("Keep in Gallery", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes B"
    }
}
