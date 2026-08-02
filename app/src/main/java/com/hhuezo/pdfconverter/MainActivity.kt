package com.hhuezo.pdfconverter

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhuezo.pdfconverter.data.RecentPdf
import com.hhuezo.pdfconverter.data.RecentPdfsRepository
import com.hhuezo.pdfconverter.ui.deletepages.PdfDeletePagesScreen
import com.hhuezo.pdfconverter.ui.home.HomeScreen
import com.hhuezo.pdfconverter.ui.home.RecentFilesScreen
import com.hhuezo.pdfconverter.ui.home.RecentPdfFile
import com.hhuezo.pdfconverter.ui.merge.PdfMergeScreen
import com.hhuezo.pdfconverter.ui.reader.PdfReaderScreen
import com.hhuezo.pdfconverter.ui.scan.PdfScanScreen
import com.hhuezo.pdfconverter.ui.sign.PdfSignScreen
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme
import com.hhuezo.pdfconverter.ui.theme.Primary
import com.hhuezo.pdfconverter.ui.theme.PrimaryFixed
import com.hhuezo.pdfconverter.ui.toimage.PdfToImageScreen
import com.hhuezo.pdfconverter.ui.tools.QuickToolId
import com.hhuezo.pdfconverter.ui.tools.ToolsScreen
import com.hhuezo.pdfconverter.util.PdfDeleter
import com.hhuezo.pdfconverter.util.formatFileSize
import com.hhuezo.pdfconverter.util.formatRecentDate
import com.hhuezo.pdfconverter.util.queryPdfInfo
import com.hhuezo.pdfconverter.util.takePersistableReadPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val externalPdfUri = MutableStateFlow<Uri?>(null)
    private var pendingIntentFlags: Int = 0

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
        handleIncomingIntent(intent)
        setContent {
            AndrosTheme {
                val incomingUri by externalPdfUri.collectAsStateWithLifecycle()
                AndrosApp(
                    externalPdfUri = incomingUri,
                    externalIntentFlags = pendingIntentFlags,
                    onExternalPdfConsumed = {
                        externalPdfUri.value = null
                        pendingIntentFlags = 0
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = extractPdfUri(intent) ?: return
        pendingIntentFlags = intent.flags
        externalPdfUri.value = uri
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
    Recent(
        labelRes = R.string.nav_recent,
        selectedIcon = Icons.Outlined.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    Tools(
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndrosApp(
    externalPdfUri: Uri? = null,
    externalIntentFlags: Int = 0,
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
    var signPdfUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePagesUri by rememberSaveable { mutableStateOf<String?>(null) }
    var mergeActive by rememberSaveable { mutableStateOf(false) }
    var scanActive by rememberSaveable { mutableStateOf(false) }
    var startupPermissionsAsked by rememberSaveable { mutableStateOf(false) }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* El usuario puede denegar; cada herramienta vuelve a pedir si hace falta. */ }

    LaunchedEffect(Unit) {
        if (startupPermissionsAsked) return@LaunchedEffect
        startupPermissionsAsked = true
        val missing = missingStartupPermissions(context)
        if (missing.isNotEmpty()) {
            startupPermissionsLauncher.launch(missing)
        }
    }

    val activity = context as? ComponentActivity
    DisposableEffect(readerUri != null) {
        val isReading = readerUri != null
        activity?.requestedOrientation = if (isReading) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun openUri(uri: Uri, preferredPage: Int? = null, intentFlags: Int = 0) {
        context.takePersistableReadPermission(uri, intentFlags)
        val info = context.queryPdfInfo(uri)
        val uriString = uri.toString()
        scope.launch {
            val page = preferredPage ?: repository.getLastPage(uriString)
            repository.addOrUpdate(
                uri = uriString,
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
            )
            // Leave tool flows so "Abrir con" / recientes siempre van al lector.
            signPdfUri = null
            convertToImageUri = null
            deletePagesUri = null
            mergeActive = false
            scanActive = false
            readerName = info.displayName
            readerInitialPage = page
            readerUri = uriString
        }
    }

    fun openForImageConversion(uri: Uri, intentFlags: Int = 0) {
        context.takePersistableReadPermission(uri, intentFlags)
        val info = context.queryPdfInfo(uri)
        scope.launch {
            repository.addOrUpdate(
                uri = uri.toString(),
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
            )
            signPdfUri = null
            deletePagesUri = null
            mergeActive = false
            scanActive = false
            readerUri = null
            convertToImageUri = uri.toString()
        }
    }

    fun openForSigning(uri: Uri, intentFlags: Int = 0) {
        context.takePersistableReadPermission(uri, intentFlags)
        val info = context.queryPdfInfo(uri)
        scope.launch {
            repository.addOrUpdate(
                uri = uri.toString(),
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
            )
            convertToImageUri = null
            deletePagesUri = null
            mergeActive = false
            scanActive = false
            readerUri = null
            signPdfUri = uri.toString()
        }
    }

    fun openForDeletePages(uri: Uri, intentFlags: Int = 0) {
        context.takePersistableReadPermission(uri, intentFlags)
        val info = context.queryPdfInfo(uri)
        scope.launch {
            repository.addOrUpdate(
                uri = uri.toString(),
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
            )
            signPdfUri = null
            convertToImageUri = null
            mergeActive = false
            scanActive = false
            readerUri = null
            deletePagesUri = uri.toString()
        }
    }

    fun openMerge() {
        signPdfUri = null
        convertToImageUri = null
        deletePagesUri = null
        readerUri = null
        scanActive = false
        mergeActive = true
    }

    fun openScan() {
        signPdfUri = null
        convertToImageUri = null
        deletePagesUri = null
        readerUri = null
        mergeActive = false
        scanActive = true
    }

    LaunchedEffect(externalPdfUri) {
        val uri = externalPdfUri ?: return@LaunchedEffect
        openUri(uri, intentFlags = externalIntentFlags)
        onExternalPdfConsumed()
    }

    fun openRecent(pdf: RecentPdf) {
        openUri(Uri.parse(pdf.uri), preferredPage = pdf.lastPageIndex)
    }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { openUri(it) }
    }

    val pickPdfForImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { openForImageConversion(it) }
    }

    val pickPdfForSign = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { openForSigning(it) }
    }

    val pickPdfForDeletePages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { openForDeletePages(it) }
    }

    fun launchPicker() {
        pickPdf.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForImage() {
        pickPdfForImage.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForSign() {
        pickPdfForSign.launch(arrayOf("application/pdf"))
    }

    fun launchPickerForDeletePages() {
        pickPdfForDeletePages.launch(arrayOf("application/pdf"))
    }

    val recentFilesUi = remember(recentPdfs) {
        recentPdfs.map { it.toUiModel() }
    }

    val activeSignUri = signPdfUri
    if (activeSignUri != null) {
        PdfSignScreen(
            uri = Uri.parse(activeSignUri),
            onBack = { signPdfUri = null },
        )
        return
    }

    if (mergeActive) {
        PdfMergeScreen(
            onBack = { mergeActive = false },
        )
        return
    }

    if (scanActive) {
        PdfScanScreen(
            onBack = { scanActive = false },
        )
        return
    }

    val activeDeletePagesUri = deletePagesUri
    if (activeDeletePagesUri != null) {
        PdfDeletePagesScreen(
            uri = Uri.parse(activeDeletePagesUri),
            onBack = { deletePagesUri = null },
        )
        return
    }

    val activeConvertUri = convertToImageUri
    if (activeConvertUri != null) {
        PdfToImageScreen(
            uri = Uri.parse(activeConvertUri),
            onBack = { convertToImageUri = null },
        )
        return
    }

    val activeUri = readerUri
    if (activeUri != null) {
        PdfReaderScreen(
            uri = Uri.parse(activeUri),
            displayName = readerName,
            initialPageIndex = readerInitialPage,
            onBack = { readerUri = null },
            onPageChanged = { pageIndex ->
                scope.launch {
                    repository.updateLastPage(activeUri, pageIndex)
                }
            },
            onConvertToImage = { convertToImageUri = activeUri },
            onSignPdf = {
                signPdfUri = activeUri
                readerUri = null
            },
            onDeletePages = {
                deletePagesUri = activeUri
                readerUri = null
            },
            onDeletePdf = {
                val uriToDelete = Uri.parse(activeUri)
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        PdfDeleter.delete(context, uriToDelete)
                    }
                    repository.remove(activeUri)
                    readerUri = null
                    Toast.makeText(
                        context,
                        context.getString(
                            if (deleted) R.string.reader_delete_success
                            else R.string.reader_delete_error,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopAppBar()
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
                recentFiles = recentFilesUi.take(5),
                onOpenPdf = ::launchPicker,
                onViewAllRecent = { currentDestination = AppDestination.Recent },
                onRecentFileClick = { file ->
                    recentPdfs.firstOrNull { it.uri == file.id }?.let(::openRecent)
                },
            )

            AppDestination.Recent -> RecentFilesScreen(
                modifier = Modifier.padding(innerPadding),
                recentFiles = recentFilesUi,
                onFileClick = { file ->
                    recentPdfs.firstOrNull { it.uri == file.id }?.let(::openRecent)
                },
            )

            AppDestination.Tools -> ToolsScreen(
                modifier = Modifier.padding(innerPadding),
                onToolClick = { toolId ->
                    when (toolId) {
                        QuickToolId.Read -> launchPicker()
                        QuickToolId.ToImage -> launchPickerForImage()
                        QuickToolId.Sign -> launchPickerForSign()
                        QuickToolId.DeletePages -> launchPickerForDeletePages()
                        QuickToolId.Merge -> openMerge()
                        QuickToolId.Scan -> openScan()
                    }
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar() {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
        ),
    )
}

@Composable
private fun AndrosBottomBar(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = PrimaryFixed,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun missingStartupPermissions(context: android.content.Context): Array<String> {
    val required = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    return required.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AndrosAppPreview() {
    AndrosTheme {
        AndrosApp()
    }
}
