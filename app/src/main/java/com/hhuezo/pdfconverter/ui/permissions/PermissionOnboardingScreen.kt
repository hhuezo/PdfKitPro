package com.hhuezo.pdfconverter.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.util.hasAllStartupPermissions
import com.hhuezo.pdfconverter.util.missingStartupPermissions
import com.hhuezo.pdfconverter.util.requiredStartupPermissions

private data class PermissionCardModel(
    val permission: String,
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

@Composable
fun PermissionOnboardingScreen(
    onAllGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    var missing by remember { mutableStateOf(missingStartupPermissions(context).toList()) }

    fun refreshPermissions() {
        missing = missingStartupPermissions(context).toList()
        if (missing.isEmpty()) {
            onAllGranted()
        }
    }

    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestedOnce = true
        refreshPermissions()
    }

    val permanentlyDenied = requestedOnce &&
        missing.isNotEmpty() &&
        activity != null &&
        missing.none { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

    BackHandler {
        activity?.moveTaskToBack(true)
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
    }

    fun onPrimaryClick() {
        if (hasAllStartupPermissions(context)) {
            onAllGranted()
            return
        }
        if (permanentlyDenied) {
            openAppSettings()
            return
        }
        val toRequest = missingStartupPermissions(context)
        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest)
        } else {
            onAllGranted()
        }
    }

    val cards = requiredStartupPermissions().mapNotNull { permission ->
        when (permission) {
            Manifest.permission.CAMERA -> PermissionCardModel(
                permission = permission,
                icon = Icons.Outlined.PhotoCamera,
                titleRes = R.string.permissions_camera_title,
                bodyRes = R.string.permissions_camera_body,
            )
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> PermissionCardModel(
                permission = permission,
                icon = Icons.Outlined.Folder,
                titleRes = R.string.permissions_storage_title,
                bodyRes = R.string.permissions_storage_body,
            )
            else -> null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarInsetPadding(),
    ) {
        PermissionHeader()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            cards.forEach { card ->
                PermissionInfoCard(
                    model = card,
                    granted = card.permission !in missing,
                )
            }
            if (requestedOnce && missing.isNotEmpty()) {
                Text(
                    text = stringResource(
                        if (permanentlyDenied) {
                            R.string.permissions_denied_permanent
                        } else {
                            R.string.permissions_denied_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = ::onPrimaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(
                        when {
                            permanentlyDenied -> R.string.permissions_open_settings
                            requestedOnce -> R.string.permissions_retry
                            else -> R.string.permissions_allow
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (requestedOnce && !permanentlyDenied) {
                OutlinedButton(
                    onClick = { openAppSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permissions_open_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(top = 28.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 4.dp,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = stringResource(R.string.permissions_header_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionInfoCard(
    model: PermissionCardModel,
    granted: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = model.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(model.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(model.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PermissionOnboardingScreenPreview() {
    AndrosTheme {
        PermissionOnboardingScreen(onAllGranted = {})
    }
}
