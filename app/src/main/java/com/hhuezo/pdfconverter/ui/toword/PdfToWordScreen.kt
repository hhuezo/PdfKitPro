package com.hhuezo.pdfconverter.ui.toword

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfToWordConverter
import com.hhuezo.pdfconverter.pdf.countPagesInRange
import com.hhuezo.pdfconverter.pdf.parsePageRange
import com.hhuezo.pdfconverter.ui.theme.PdfAccent
import com.hhuezo.pdfconverter.ui.theme.PdfAccentContainer
import com.hhuezo.pdfconverter.ui.theme.SecondaryContainer
import com.hhuezo.pdfconverter.ui.theme.SecondaryFixed
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.formatFileSize
import com.hhuezo.pdfconverter.util.queryPdfInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PageSelectionMode {
    All,
    Custom,
}

private enum class ConvertUiState {
    Idle,
    Processing,
    Ready,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToWordScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val fileInfo = remember(uri) { context.queryPdfInfo(uri) }
    var pageCount by remember { mutableIntStateOf(0) }
    var openError by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(PageSelectionMode.All) }
    var rangeInput by remember { mutableStateOf("") }
    var convertState by remember { mutableStateOf(ConvertUiState.Idle) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var usedOcr by remember { mutableStateOf(false) }

    fun suggestedName(): String {
        val base = fileInfo.displayName
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .ifBlank { "documento" }
        return "$base.docx"
    }

    fun downloadWord() {
        val file = outputFile ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                PdfFileSaver.saveToDownloads(
                    context,
                    file,
                    suggestedName(),
                    PdfToWordConverter.DOCX_MIME,
                )
            }
            snackbar.showSnackbar(
                context.getString(
                    if (saved != null) {
                        R.string.to_word_download_success
                    } else {
                        R.string.to_word_download_error
                    },
                ),
            )
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            downloadWord()
        } else {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.to_word_download_error))
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PdfToWordConverter.DOCX_MIME),
    ) { destUri ->
        val file = outputFile
        if (destUri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                PdfFileSaver.writeToUri(context, destUri, file)
            }
            snackbar.showSnackbar(
                context.getString(
                    if (saved) R.string.action_save_as_success else R.string.action_save_error,
                ),
            )
        }
    }

    fun requestDownload() {
        if (outputFile == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadWord()
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            downloadWord()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(uri) {
        openError = false
        pageCount = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@runCatching 0
                PdfRenderer(pfd).use { it.pageCount }
            }.getOrElse {
                openError = true
                0
            }
        }
        if (pageCount <= 0 && !openError) {
            openError = true
        }
    }

    val selectedCount = when (selectionMode) {
        PageSelectionMode.All -> pageCount
        PageSelectionMode.Custom -> countPagesInRange(rangeInput, pageCount)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.to_word_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                        )
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
                if (convertState == ConvertUiState.Ready) {
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
                                tint = androidx.compose.ui.graphics.Color(0xFF16A34A),
                            )
                            Text(
                                text = stringResource(R.string.to_word_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { createDocumentLauncher.launch(suggestedName()) },
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
                                onClick = {
                                    outputFile?.let { shareWord(context, it) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.to_word_share))
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
                            Text(stringResource(R.string.to_word_download))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (convertState == ConvertUiState.Processing) return@Button
                            val pages = when (selectionMode) {
                                PageSelectionMode.All -> (0 until pageCount).toList()
                                PageSelectionMode.Custom -> parsePageRange(rangeInput, pageCount)
                            }
                            if (pages.isNullOrEmpty()) {
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.to_word_error_range),
                                    )
                                }
                                return@Button
                            }

                            convertState = ConvertUiState.Processing
                            progressDone = 0
                            progressTotal = pages.size
                            outputFile = null
                            usedOcr = false
                            val mainHandler = Handler(Looper.getMainLooper())
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val out = File(
                                            context.cacheDir,
                                            "pdfkit_word_${System.currentTimeMillis()}.docx",
                                        )
                                        PdfToWordConverter(context).convert(
                                            uri = uri,
                                            pageIndices = pages,
                                            outputFile = out,
                                            onProgress = { done, total ->
                                                mainHandler.post {
                                                    progressDone = done
                                                    progressTotal = total
                                                }
                                            },
                                        )
                                    }
                                }
                                result.fold(
                                    onSuccess = { output ->
                                        outputFile = output.file
                                        usedOcr = output.usedOcr
                                        convertState = ConvertUiState.Ready
                                    },
                                    onFailure = { error ->
                                        convertState = ConvertUiState.Idle
                                        val message = if (error.message == "empty-text") {
                                            R.string.to_word_error_empty
                                        } else {
                                            R.string.to_word_error_convert
                                        }
                                        snackbar.showSnackbar(context.getString(message))
                                    },
                                )
                            }
                        },
                        enabled = !openError && pageCount > 0 &&
                            convertState != ConvertUiState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (convertState == ConvertUiState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (progressTotal > 0) {
                                    "${stringResource(R.string.to_word_processing)} $progressDone/$progressTotal"
                                } else {
                                    stringResource(R.string.to_word_processing)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.to_word_convert),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (openError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.to_word_error_open),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FilePreviewCard(
                name = fileInfo.displayName,
                meta = stringResource(
                    R.string.to_word_file_meta,
                    formatFileSize(fileInfo.sizeBytes),
                    pageCount,
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.to_image_pages_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                PageOptionCard(
                    selected = selectionMode == PageSelectionMode.All,
                    title = stringResource(R.string.to_image_all_pages),
                    subtitle = stringResource(R.string.to_image_all_pages_subtitle),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesomeMotion,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    },
                    onClick = {
                        selectionMode = PageSelectionMode.All
                        convertState = ConvertUiState.Idle
                    },
                )
                PageOptionCard(
                    selected = selectionMode == PageSelectionMode.Custom,
                    title = stringResource(R.string.to_image_custom_range),
                    subtitle = stringResource(R.string.to_image_custom_range_subtitle),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        selectionMode = PageSelectionMode.Custom
                        convertState = ConvertUiState.Idle
                    },
                )

                AnimatedVisibility(visible = selectionMode == PageSelectionMode.Custom) {
                    RangeInputCard(
                        value = rangeInput,
                        selectedCount = selectedCount,
                        onValueChange = {
                            rangeInput = it
                            convertState = ConvertUiState.Idle
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryFixed)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryFixed,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        if (usedOcr && convertState == ConvertUiState.Ready) {
                            R.string.to_word_info_ocr
                        } else {
                            R.string.to_word_info
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryFixed,
                )
            }
        }
    }
}

@Composable
private fun FilePreviewCard(name: String, meta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PdfAccentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = PdfAccent,
                modifier = Modifier.size(32.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = PdfAccent,
                        shape = RoundedCornerShape(topStart = 4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.pdf_badge),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageOptionCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun RangeInputCard(
    value: String,
    selectedCount: Int,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.to_image_range_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.to_image_range_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            inner()
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.to_image_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun shareWord(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = PdfToWordConverter.DOCX_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.to_word_share_title)),
    )
}
