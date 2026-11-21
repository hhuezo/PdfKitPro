package com.hhuezo.pdfconverter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme
import com.hhuezo.pdfconverter.ui.theme.PdfAccent
import com.hhuezo.pdfconverter.ui.theme.Primary
import com.hhuezo.pdfconverter.ui.theme.StudioBackground
import com.hhuezo.pdfconverter.ui.theme.StudioHeroEnd
import com.hhuezo.pdfconverter.ui.theme.StudioHeroStart
import com.hhuezo.pdfconverter.ui.theme.iosGlass

data class RecentPdfFile(
    val id: String,
    val name: String,
    val meta: String,
)

@Composable
fun HomeScreen(
    recentFiles: List<RecentPdfFile> = emptyList(),
    onOpenPdf: () -> Unit = {},
    onRecentFileClick: (RecentPdfFile) -> Unit = {},
    onRecentFileRemove: (RecentPdfFile) -> Unit = {},
    onClearRecentHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
    ) {
        OpenPdfHero(onClick = onOpenPdf)
        Spacer(modifier = Modifier.height(20.dp))
        RecentFilesSection(
            files = recentFiles,
            onFileClick = onRecentFileClick,
            onFileRemove = onRecentFileRemove,
            onClearHistory = onClearRecentHistory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenPdfHero(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Primary.copy(alpha = 0.32f),
                spotColor = Primary.copy(alpha = 0.28f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(StudioHeroStart, StudioHeroEnd),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 500f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 28.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(140.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(R.string.open_pdf),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.open_pdf_subtitle),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                ),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun RecentFilesSection(
    files: List<RecentPdfFile>,
    onFileClick: (RecentPdfFile) -> Unit,
    onFileRemove: (RecentPdfFile) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recent_files),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (files.isNotEmpty()) {
                Text(
                    text = files.size.toString(),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .iosGlass(shape = RoundedCornerShape(50), elevation = 0.dp, tinted = true)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.recent_clear_all),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Primary,
                )
            }
        }

        if (files.isEmpty()) {
            EmptyRecentFiles()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    RecentFileRow(
                        file = file,
                        onClick = { onFileClick(file) },
                        onRemove = { onFileRemove(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecentFiles() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlass(shape = shape, elevation = 6.dp, tinted = true)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = PdfAccent.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_recent_files),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.no_recent_files_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun RecentFileRow(
    file: RecentPdfFile,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlass(shape = shape, elevation = 8.dp, tinted = true)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfThumbnailBadge()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = file.meta,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recent_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfThumbnailBadge() {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Primary)
            .border(1.dp, Primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PDF",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                lineHeight = 14.sp,
            ),
            color = Color.White,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    AndrosTheme {
        StudioBackground {
            HomeScreen(recentFiles = sampleRecentFiles)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    AndrosTheme {
        StudioBackground {
            HomeScreen(recentFiles = emptyList())
        }
    }
}

private val sampleRecentFiles = listOf(
    RecentPdfFile("1", "1795199117738_E66BCBD7-....pdf", "130 KB • 20 nov 2026"),
    RecentPdfFile("2", "1795199112872_E66BCBD7-....pdf", "130 KB • 20 nov 2026"),
    RecentPdfFile("3", "1795195758732_E66BCBD7-....pdf", "130 KB • 20 nov 2026"),
    RecentPdfFile("4", "1795194795975_Factura_DT....pdf", "1019 KB • 20 nov 2026"),
    RecentPdfFile("5", "1795194783889_Factura_DT....pdf", "1018 KB • 20 nov 2026"),
)
