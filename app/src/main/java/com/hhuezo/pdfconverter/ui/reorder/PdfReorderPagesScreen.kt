package com.hhuezo.pdfconverter.ui.reorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfDocumentSession
import com.hhuezo.pdfconverter.pdf.PdfPageReorderer
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.PdfSaveOutcome
import com.hhuezo.pdfconverter.util.queryPdfInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class ReorderUiState {
    Idle,
    Processing,
    Ready,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReorderPagesScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val fileInfo = remember(uri) { context.queryPdfInfo(uri) }
    var session by remember { mutableStateOf<PdfDocumentSession?>(null) }
    var openError by remember { mutableStateOf(false) }
    var pageOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var uiState by remember { mutableStateOf(ReorderUiState.Idle) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var totalPageCount by remember { mutableIntStateOf(0) }

    DisposableEffect(uri) {
        val opened = runCatching { PdfDocumentSession(context, uri) }.getOrElse {
            openError = true
            null
        }
        session = opened
        onDispose {
            opened?.close()
            session = null
        }
    }

    LaunchedEffect(session?.pageCount) {
        val count = session?.pageCount ?: return@LaunchedEffect
        if (pageOrder.size != count) {
            pageOrder = List(count) { it }
        }
    }

    fun downloadResult(file: File) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val baseName = fileInfo.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                PdfFileSaver.saveToDownloads(
                    context = context,
                    source = file,
                    displayName = "${baseName}_reordenado.pdf",
                )
            }
            if (saved != null) {
                snackbar.showSnackbar(context.getString(R.string.reorder_pages_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.reorder_pages_download_error))
            }
        }
    }

    fun saveResult(file: File) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val baseName = fileInfo.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                PdfFileSaver.saveOverwritingOrCopy(
                    context = context,
                    originalUri = uri,
                    source = file,
                    fallbackDisplayName = "${baseName}_reordenado.pdf",
                )
            }
            val message = when (outcome) {
                PdfSaveOutcome.Overwritten -> R.string.action_save_original_success
                PdfSaveOutcome.SavedAsCopy -> R.string.action_save_copy_success
                PdfSaveOutcome.Failed -> R.string.action_save_error
            }
            snackbar.showSnackbar(context.getString(message))
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val file = outputFile
        if (granted && file != null) {
            downloadResult(file)
        } else {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.reorder_pages_download_error))
            }
        }
    }

    fun requestDownload() {
        val file = outputFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadResult(file)
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            downloadResult(file)
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    fun requestSave() {
        val file = outputFile ?: return
        saveResult(file)
    }

    fun movePage(from: Int, to: Int) {
        if (uiState == ReorderUiState.Processing) return
        if (pageOrder.size <= 1) return
        if (from !in pageOrder.indices || to !in pageOrder.indices) return
        if (from == to) return
        val mutable = pageOrder.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        pageOrder = mutable
        uiState = ReorderUiState.Idle
        outputFile = null
    }

    fun resetOrder() {
        if (uiState == ReorderUiState.Processing) return
        val count = session?.pageCount ?: return
        pageOrder = List(count) { it }
        uiState = ReorderUiState.Idle
        outputFile = null
    }

    fun applyReorder() {
        if (session == null) return
        if (uiState == ReorderUiState.Processing) return
        val identity = List(pageOrder.size) { it }
        when {
            pageOrder.size < 2 -> {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.reorder_pages_single_page))
                }
            }
            pageOrder == identity -> {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.reorder_pages_error_none))
                }
            }
            else -> {
                uiState = ReorderUiState.Processing
                outputFile = null
                val orderSnapshot = pageOrder
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = File(
                                context.cacheDir,
                                "reordenado_${System.currentTimeMillis()}.pdf",
                            )
                            val pages = PdfPageReorderer(context).reorderPages(
                                uri = uri,
                                pageOrder = orderSnapshot,
                                outputFile = out,
                            )
                            out to pages
                        }
                    }
                    result.fold(
                        onSuccess = { (file, pages) ->
                            outputFile = file
                            totalPageCount = pages
                            uiState = ReorderUiState.Ready
                        },
                        onFailure = {
                            uiState = ReorderUiState.Idle
                            snackbar.showSnackbar(context.getString(R.string.reorder_pages_error))
                        },
                    )
                }
            }
        }
    }

    val pageCount = session?.pageCount ?: 0
    val hasChanges = pageOrder.isNotEmpty() && pageOrder != List(pageOrder.size) { it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.reorder_pages_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pageCount > 0) {
                            Text(
                                text = stringResource(
                                    if (hasChanges) {
                                        R.string.reorder_pages_changed_meta
                                    } else {
                                        R.string.reorder_pages_selection_meta
                                    },
                                    pageCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    if (hasChanges && uiState != ReorderUiState.Processing) {
                        IconButton(onClick = ::resetOrder) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.reorder_pages_reset),
                            )
                        }
                    }
                },
                colors = androsTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarInsetPadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                if (uiState == ReorderUiState.Ready) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                            )
                            Text(
                                text = stringResource(
                                    R.string.reorder_pages_ready,
                                    totalPageCount,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { requestSave() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_save))
                            }
                            OutlinedButton(
                                onClick = { outputFile?.let { sharePdf(context, it) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.reorder_pages_share))
                            }
                        }
                        Button(
                            onClick = { requestDownload() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.reorder_pages_download))
                        }
                    }
                } else {
                    Button(
                        onClick = ::applyReorder,
                        enabled = !openError && pageCount > 1 &&
                            uiState != ReorderUiState.Processing &&
                            hasChanges,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (uiState == ReorderUiState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.reorder_pages_processing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Icon(Icons.Outlined.Reorder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.reorder_pages_action),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            openError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reorder_pages_error_open),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            else -> {
                val doc = session!!
                val isSinglePage = pageCount <= 1
                val canReorder = !isSinglePage && uiState != ReorderUiState.Processing
                val haptic = LocalHapticFeedback.current
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    // Solo hay ítems de página en la lista (el encabezado va fuera).
                    movePage(from.index, to.index)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isSinglePage) {
                            SinglePageBlockedBanner(
                                message = stringResource(R.string.reorder_pages_single_page),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.reorder_pages_tap_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.reorder_pages_info),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = pageOrder,
                            key = { _, originalIndex -> originalIndex },
                        ) { position, originalIndex ->
                            ReorderableItem(
                                state = reorderableLazyListState,
                                key = originalIndex,
                                enabled = canReorder,
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 6.dp else 0.dp,
                                    label = "reorderElevation",
                                )
                                ReorderPageRow(
                                    session = doc,
                                    originalPageIndex = originalIndex,
                                    newPosition = position + 1,
                                    moved = originalIndex != position,
                                    isDragging = isDragging,
                                    dragEnabled = canReorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .shadow(elevation, RoundedCornerShape(16.dp))
                                        .then(
                                            if (canReorder) {
                                                Modifier.longPressDraggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureThresholdActivate,
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureEnd,
                                                        )
                                                    },
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderPageRow(
    session: PdfDocumentSession,
    originalPageIndex: Int,
    newPosition: Int,
    moved: Boolean,
    isDragging: Boolean,
    dragEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(originalPageIndex) { mutableStateOf<Bitmap?>(null) }
    val thumbWidthPx = 160

    LaunchedEffect(session, originalPageIndex) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching { session.renderPage(originalPageIndex, thumbWidthPx) }.getOrNull()
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            moved -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "reorderContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primary
            moved -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "reorderBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isDragging) 2.dp else 1.dp,
        label = "reorderBorderWidth",
    )
    val accentColor = when {
        isDragging || moved -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(borderWidth, borderColor, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = newPosition.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(36.dp),
        )

        Box(
            modifier = Modifier
                .width(72.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val pageBitmap = bitmap
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.reorder_pages_position, newPosition),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reorder_pages_original, originalPageIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.reorder_pages_drag_handle),
            tint = if (dragEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SinglePageBlockedBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun sharePdf(context: android.content.Context, file: File) {
    val shareUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            share,
            context.getString(R.string.reorder_pages_share_title),
        ),
    )
}
