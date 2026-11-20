package com.hhuezo.pdfconverter

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhuezo.pdfconverter.data.RecentPdf
import com.hhuezo.pdfconverter.data.RecentPdfsRepository
import com.hhuezo.pdfconverter.ui.about.AboutScreen
import com.hhuezo.pdfconverter.ui.deletepages.PdfDeletePagesScreen
import com.hhuezo.pdfconverter.ui.reorder.PdfReorderPagesScreen
import com.hhuezo.pdfconverter.ui.rotate.PdfRotatePagesScreen
import com.hhuezo.pdfconverter.ui.home.HomeScreen
import com.hhuezo.pdfconverter.ui.permissions.PermissionOnboardingScreen
import com.hhuezo.pdfconverter.ui.home.RecentPdfFile
import com.hhuezo.pdfconverter.ui.merge.PdfMergeScreen
import com.hhuezo.pdfconverter.ui.reader.PdfReaderScreen
import com.hhuezo.pdfconverter.ui.scan.PdfScanScreen
import com.hhuezo.pdfconverter.ui.settings.SettingsScreen
import com.hhuezo.pdfconverter.ui.sign.PdfSignScreen
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme
import com.hhuezo.pdfconverter.ui.theme.Primary
import com.hhuezo.pdfconverter.ui.theme.PrimaryFixed
import com.hhuezo.pdfconverter.ui.toimage.PdfToImageScreen
import com.hhuezo.pdfconverter.ui.toword.PdfToWordScreen
import com.hhuezo.pdfconverter.ui.tools.QuickToolId
import com.hhuezo.pdfconverter.ui.tools.ToolsScreen
import com.hhuezo.pdfconverter.util.OpenWritablePdfDocument
import com.hhuezo.pdfconverter.util.formatFileSize
import com.hhuezo.pdfconverter.util.formatRecentDate
import com.hhuezo.pdfconverter.util.PdfFileInfo
import com.hhuezo.pdfconverter.util.ensureDurablePdfAccess
import com.hhuezo.pdfconverter.util.hasAllStartupPermissions
import com.hhuezo.pdfconverter.util.isPdfUriAccessible
import com.hhuezo.pdfconverter.util.queryPdfInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val externalPdfUri = MutableStateFlow<Uri?>(null)
    private var pendingIntentFlags: Int = 0
    /** PDF abierto desde otra app (WhatsApp, archivos) sin estar ya dentro de esta. */
    private val returnToCaller = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Primary.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
        )
        returnToCaller.value = savedInstanceState?.getBoolean(STATE_RETURN_TO_CALLER)
            ?: isExternalPdfIntent(intent)
        handleIncomingIntent(intent)
        setContent {
            AndrosTheme {
                val incomingUri by externalPdfUri.collectAsStateWithLifecycle()
                val leaveToCaller by returnToCaller.collectAsStateWithLifecycle()
                AndrosApp(
                    externalPdfUri = incomingUri,
                    externalIntentFlags = pendingIntentFlags,
                    returnToCaller = leaveToCaller,
                    onExternalPdfConsumed = {
                        externalPdfUri.value = null
                        pendingIntentFlags = 0
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RETURN_TO_CALLER, returnToCaller.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask reutiliza esta actividad. Si no estaba en primer plano,
        // el usuario venía de la otra app y Atrás debe volver allí.
        if (isExternalPdfIntent(intent) &&
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            returnToCaller.value = true
        }
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = extractPdfUri(intent) ?: return
        pendingIntentFlags = intent.flags
        externalPdfUri.value = uri
    }

    private fun isExternalPdfIntent(intent: Intent?): Boolean {
        return intent != null && extractPdfUri(intent) != null
    }

    private fun extractPdfUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> null
        }
    }

    private companion object {
        const val STATE_RETURN_TO_CALLER = "return_to_caller"
    }
}

enum class AppDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Tools(
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
    ),
    Settings(
        labelRes = R.string.settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndrosApp(
    externalPdfUri: Uri? = null,
    externalIntentFlags: Int = 0,
    returnToCaller: Boolean = false,
    onExternalPdfConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { RecentPdfsRepository(context.applicationContext) }
    val recentPdfs by repository.recentPdfs.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.Home) }

    var readerUri by rememberSaveable { mutableStateOf<String?>(null) }
    var readerName by rememberSaveable { mutableStateOf("") }
    var readerInitialPage by rememberSaveable { mutableIntStateOf(0) }
    var convertToImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var convertToWordUri by rememberSaveable { mutableStateOf<String?>(null) }
    var signPdfUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePagesUri by rememberSaveable { mutableStateOf<String?>(null) }
    var reorderPagesUri by rememberSaveable { mutableStateOf<String?>(null) }
    var rotatePagesUri by rememberSaveable { mutableStateOf<String?>(null) }
    var mergeActive by rememberSaveable { mutableStateOf(false) }
    var scanActive by rememberSaveable { mutableStateOf(false) }
    var aboutActive by rememberSaveable { mutableStateOf(false) }
    var missingRecentPdf by remember { mutableStateOf<RecentPdf?>(null) }
    var showPermissionOnboarding by remember {
        mutableStateOf(!hasAllStartupPermissions(context))
    }

    if (showPermissionOnboarding) {
        PermissionOnboardingScreen(
            onAllGranted = { showPermissionOnboarding = false },
        )
        return
    }

    val showingReader = readerUri != null &&
        signPdfUri == null &&
        convertToImageUri == null &&
        convertToWordUri == null &&
        deletePagesUri == null &&
        reorderPagesUri == null &&
        rotatePagesUri == null &&
        !mergeActive &&
        !scanActive &&
        !aboutActive

    val activity = context as? ComponentActivity
    DisposableEffect(showingReader) {
        activity?.requestedOrientation = if (showingReader) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    suspend fun resolveDurablePdf(uri: Uri, intentFlags: Int = 0): Pair<Uri, PdfFileInfo> {
        return withContext(Dispatchers.IO) {
            val durable = context.ensureDurablePdfAccess(uri, intentFlags)
            durable to context.queryPdfInfo(durable)
        }
    }

    suspend fun rememberRecent(originalUri: Uri, durableUri: Uri, info: PdfFileInfo) {
        val original = originalUri.toString()
        val durable = durableUri.toString()
        if (durable != original) {
            repository.remove(original)
        }
        repository.addOrUpdate(
            uri = durable,
            displayName = info.displayName,
            sizeBytes = info.sizeBytes,
        )
    }

    fun openUri(uri: Uri, preferredPage: Int? = null, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            val original = uri.toString()
            val durable = durableUri.toString()
            val page = preferredPage
                ?: repository.getLastPage(durable)
                ?: repository.getLastPage(original)
            if (durable != original) {
                repository.remove(original)
            }
            repository.addOrUpdate(
                uri = durable,
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
            )
            // Leave tool flows so "Abrir con" / recientes siempre van al lector.
            signPdfUri = null
            convertToImageUri = null
            convertToWordUri = null
            deletePagesUri = null
            reorderPagesUri = null
            rotatePagesUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerName = info.displayName
            readerInitialPage = page
            readerUri = durable
        }
    }

    fun openForImageConversion(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            signPdfUri = null
            deletePagesUri = null
            reorderPagesUri = null
            rotatePagesUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            convertToWordUri = null
            convertToImageUri = durableUri.toString()
        }
    }

    fun openForWordConversion(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            signPdfUri = null
            deletePagesUri = null
            reorderPagesUri = null
            rotatePagesUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            convertToImageUri = null
            convertToWordUri = durableUri.toString()
        }
    }

    fun openForSigning(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            convertToImageUri = null
            convertToWordUri = null
            deletePagesUri = null
            reorderPagesUri = null
            rotatePagesUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            signPdfUri = durableUri.toString()
        }
    }

    fun openForDeletePages(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            signPdfUri = null
            convertToImageUri = null
            convertToWordUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            reorderPagesUri = null
            rotatePagesUri = null
            deletePagesUri = durableUri.toString()
        }
    }

    fun openForReorderPages(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            signPdfUri = null
            convertToImageUri = null
            convertToWordUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            deletePagesUri = null
            rotatePagesUri = null
            reorderPagesUri = durableUri.toString()
        }
    }

    fun openForRotatePages(uri: Uri, intentFlags: Int = 0) {
        scope.launch {
            val (durableUri, info) = resolveDurablePdf(uri, intentFlags)
            rememberRecent(uri, durableUri, info)
            signPdfUri = null
            convertToImageUri = null
            convertToWordUri = null
            mergeActive = false
            scanActive = false
            aboutActive = false
            readerUri = null
            deletePagesUri = null
            reorderPagesUri = null
            rotatePagesUri = durableUri.toString()
        }
    }

    fun openMerge() {
        signPdfUri = null
        convertToImageUri = null
        convertToWordUri = null
        deletePagesUri = null
        reorderPagesUri = null
        rotatePagesUri = null
        readerUri = null
        scanActive = false
        aboutActive = false
        mergeActive = true
    }

    fun openScan() {
        signPdfUri = null
        convertToImageUri = null
        convertToWordUri = null
        deletePagesUri = null
        reorderPagesUri = null
        rotatePagesUri = null
        readerUri = null
        mergeActive = false
        aboutActive = false
        scanActive = true
    }

    fun openAbout() {
        signPdfUri = null
        convertToImageUri = null
        convertToWordUri = null
        deletePagesUri = null
        reorderPagesUri = null
        rotatePagesUri = null
        readerUri = null
        mergeActive = false
        scanActive = false
        aboutActive = true
    }

    LaunchedEffect(externalPdfUri) {
        val uri = externalPdfUri ?: return@LaunchedEffect
        openUri(uri, intentFlags = externalIntentFlags)
        onExternalPdfConsumed()
    }

    fun removeFromRecents(uri: String) {
        scope.launch {
            repository.remove(uri)
            Toast.makeText(
                context,
                context.getString(R.string.recent_removed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openRecent(pdf: RecentPdf) {
        scope.launch {
            val uri = Uri.parse(pdf.uri)
            val accessible = withContext(Dispatchers.IO) {
                context.isPdfUriAccessible(uri)
            }
            if (accessible) {
                openUri(uri, preferredPage = pdf.lastPageIndex)
            } else {
                missingRecentPdf = pdf
            }
        }
    }

    val pickPdf = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openUri(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForImage = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForImageConversion(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForWord = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForWordConversion(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForSign = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForSigning(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForDeletePages = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForDeletePages(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForReorderPages = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForReorderPages(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    val pickPdfForRotatePages = rememberLauncherForActivityResult(
        contract = OpenWritablePdfDocument(),
    ) { uri ->
        uri?.let {
            openForRotatePages(
                it,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun launchPicker() {
        pickPdf.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForImage() {
        pickPdfForImage.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForWord() {
        pickPdfForWord.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForSign() {
        pickPdfForSign.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForDeletePages() {
        pickPdfForDeletePages.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForReorderPages() {
        pickPdfForReorderPages.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForRotatePages() {
        pickPdfForRotatePages.launch(arrayOf("application/pdf"))
    }

    val recentFilesUi = remember(recentPdfs) {
        recentPdfs.map { it.toUiModel() }
    }

    fun navigateBack() {
        when {
            aboutActive -> aboutActive = false
            signPdfUri != null -> signPdfUri = null
            mergeActive -> mergeActive = false
            scanActive -> scanActive = false
            rotatePagesUri != null -> rotatePagesUri = null
            reorderPagesUri != null -> reorderPagesUri = null
            deletePagesUri != null -> deletePagesUri = null
            convertToWordUri != null -> convertToWordUri = null
            convertToImageUri != null -> convertToImageUri = null
            readerUri != null -> {
                if (returnToCaller) {
                    context.findActivity()?.finish()
                } else {
                    readerUri = null
                }
            }
            returnToCaller -> context.findActivity()?.finish()
            currentDestination != AppDestination.Home -> {
                currentDestination = AppDestination.Home
            }
            else -> {
                context.findActivity()?.moveTaskToBack(true)
            }
        }
    }

    BackHandler(onBack = ::navigateBack)

    missingRecentPdf?.let { pdf ->
        AlertDialog(
            onDismissRequest = { missingRecentPdf = null },
            title = {
                Text(text = stringResource(R.string.recent_file_not_found_title))
            },
            text = {
                Text(text = stringResource(R.string.recent_file_not_found_message, pdf.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeFromRecents(pdf.uri)
                        missingRecentPdf = null
                    },
                ) {
                    Text(text = stringResource(R.string.recent_remove_from_list))
                }
            },
            dismissButton = {
                TextButton(onClick = { missingRecentPdf = null }) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            },
        )
    }

    if (aboutActive) {
        AboutScreen(onBack = ::navigateBack)
        return
    }

    val activeSignUri = signPdfUri
    if (activeSignUri != null) {
        PdfSignScreen(
            uri = Uri.parse(activeSignUri),
            onBack = ::navigateBack,
        )
        return
    }

    if (mergeActive) {
        PdfMergeScreen(
            onBack = ::navigateBack,
        )
        return
    }

    if (scanActive) {
        PdfScanScreen(
            onBack = ::navigateBack,
        )
        return
    }

    val activeRotatePagesUri = rotatePagesUri
    if (activeRotatePagesUri != null) {
        PdfRotatePagesScreen(
            uri = Uri.parse(activeRotatePagesUri),
            onBack = ::navigateBack,
        )
        return
    }

    val activeReorderPagesUri = reorderPagesUri
    if (activeReorderPagesUri != null) {
        PdfReorderPagesScreen(
            uri = Uri.parse(activeReorderPagesUri),
            onBack = ::navigateBack,
        )
        return
    }

    val activeDeletePagesUri = deletePagesUri
    if (activeDeletePagesUri != null) {
        PdfDeletePagesScreen(
            uri = Uri.parse(activeDeletePagesUri),
            onBack = ::navigateBack,
        )
        return
    }

    val activeConvertWordUri = convertToWordUri
    if (activeConvertWordUri != null) {
        PdfToWordScreen(
            uri = Uri.parse(activeConvertWordUri),
            onBack = ::navigateBack,
        )
        return
    }

    val activeConvertUri = convertToImageUri
    if (activeConvertUri != null) {
        PdfToImageScreen(
            uri = Uri.parse(activeConvertUri),
            onBack = ::navigateBack,
        )
        return
    }

    val activeUri = readerUri
    if (activeUri != null) {
        PdfReaderScreen(
            uri = Uri.parse(activeUri),
            displayName = readerName,
            initialPageIndex = readerInitialPage,
            onBack = ::navigateBack,
            onPageChanged = { pageIndex ->
                scope.launch {
                    repository.updateLastPage(activeUri, pageIndex)
                }
            },
            onConvertToImage = { convertToImageUri = activeUri },
            onConvertToWord = { convertToWordUri = activeUri },
            onSignPdf = { signPdfUri = activeUri },
            onDeletePages = { deletePagesUri = activeUri },
            onReorderPages = { reorderPagesUri = activeUri },
            onRotatePages = { rotatePagesUri = activeUri },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopAppBar(
                onAboutClick = ::openAbout,
            )
        },
        bottomBar = {
            AndrosBottomBar(
                current = currentDestination,
                onNavigate = { currentDestination = it },
            )
        },
    ) { innerPadding ->
        when (currentDestination) {
            AppDestination.Home -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                recentFiles = recentFilesUi,
                onOpenPdf = ::launchPicker,
                onRecentFileClick = { file ->
                    recentPdfs.firstOrNull { it.uri == file.id }?.let(::openRecent)
                },
                onRecentFileRemove = { file -> removeFromRecents(file.id) },
            )

            AppDestination.Tools -> ToolsScreen(
                modifier = Modifier.padding(innerPadding),
                onToolClick = { toolId ->
                    when (toolId) {
                        QuickToolId.ToImage -> launchPickerForImage()
                        QuickToolId.ToWord -> launchPickerForWord()
                        QuickToolId.Sign -> launchPickerForSign()
                        QuickToolId.DeletePages -> launchPickerForDeletePages()
                        QuickToolId.ReorderPages -> launchPickerForReorderPages()
                        QuickToolId.RotatePages -> launchPickerForRotatePages()
                        QuickToolId.Merge -> openMerge()
                        QuickToolId.Scan -> openScan()
                    }
                },
            )

            AppDestination.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private fun RecentPdf.toUiModel(): RecentPdfFile {
    val date = formatRecentDate(lastOpenedAt)
    val size = formatFileSize(sizeBytes)
    val meta = if (date.isNotEmpty()) "$date • $size" else size
    return RecentPdfFile(
        id = uri,
        name = displayName,
        meta = meta,
    )
}

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    onAboutClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            IconButton(onClick = onAboutClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.about_open),
                    tint = Color.White,
                )
            }
        },
        colors = androsTopAppBarColors(),
    )
}

@Composable
private fun AndrosBottomBar(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarInsetPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppDestination.entries.forEach { destination ->
                    BottomBarTab(
                        destination = destination,
                        selected = destination == current,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = if (selected) PrimaryFixed else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }

    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AndrosAppPreview() {
    AndrosTheme {
        AndrosApp()
    }
}
