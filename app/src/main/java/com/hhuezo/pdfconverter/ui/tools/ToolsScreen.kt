package com.hhuezo.pdfconverter.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme
import com.hhuezo.pdfconverter.ui.theme.BrandTint
import com.hhuezo.pdfconverter.ui.theme.Primary
import com.hhuezo.pdfconverter.ui.theme.StudioBackground

enum class QuickToolId {
    ToImage,
    ToWord,
    Sign,
    DeletePages,
    ReorderPages,
    RotatePages,
    Merge,
    Scan,
}

// Vibrant pastel glass tints from Studio design
private val TintBlue = listOf(Color(0xFFC2DCFF), Color(0xFFA2C8FF))
private val TintAmber = listOf(Color(0xFFFFE4A0), Color(0xFFFDD271))
private val TintPurple = listOf(Color(0xFFE6D2FF), Color(0xFFD4B6FF))
private val TintCyan = listOf(Color(0xFFB5F4FC), Color(0xFF89E8F8))
private val TintPeach = listOf(Color(0xFFFFD2B8), Color(0xFFFFBCA0))
private val TintMint = listOf(Color(0xFFBFF4D7), Color(0xFF9DEAC0))
private val TintSky = listOf(Color(0xFFBDE1FF), Color(0xFF97D0FF))
private val TintRose = listOf(Color(0xFFFFCCD8), Color(0xFFFFB1C2))

private data class QuickTool(
    val titleRes: Int,
    val icon: ImageVector,
    val tint: List<Color>,
    val iconTint: Color,
    val titleColor: Color,
)

@Composable
fun ToolsScreen(
    onToolClick: (QuickToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        QuickTool(
            titleRes = R.string.reorder_pages,
            icon = Icons.Outlined.Reorder,
            tint = TintBlue,
            iconTint = Color(0xFF1D4ED8),
            titleColor = Color(0xFF10306E),
        ) to QuickToolId.ReorderPages,
        QuickTool(
            titleRes = R.string.rotate_pages_title,
            icon = Icons.Outlined.Rotate90DegreesCw,
            tint = TintAmber,
            iconTint = Color(0xFF925B03),
            titleColor = Color(0xFF6D2E07),
        ) to QuickToolId.RotatePages,
        QuickTool(
            titleRes = R.string.merge_pdfs,
            icon = Icons.AutoMirrored.Outlined.MergeType,
            tint = TintPurple,
            iconTint = Color(0xFF7E22CE),
            titleColor = Color(0xFF4C1D95),
        ) to QuickToolId.Merge,
        QuickTool(
            titleRes = R.string.scan_to_pdf,
            icon = Icons.Outlined.DocumentScanner,
            tint = TintCyan,
            iconTint = Color(0xFF0F766E),
            titleColor = Color(0xFF0D524D),
        ) to QuickToolId.Scan,
        QuickTool(
            titleRes = R.string.sign_pdf,
            icon = Icons.Outlined.Draw,
            tint = TintPeach,
            iconTint = Color(0xFF92400E),
            titleColor = Color(0xFF783E07),
        ) to QuickToolId.Sign,
        QuickTool(
            titleRes = R.string.pdf_to_image,
            icon = Icons.Outlined.Image,
            tint = TintMint,
            iconTint = Color(0xFF047857),
            titleColor = Color(0xFF064E3B),
        ) to QuickToolId.ToImage,
        QuickTool(
            titleRes = R.string.pdf_to_word,
            icon = Icons.AutoMirrored.Outlined.Article,
            tint = TintSky,
            iconTint = Color(0xFF0369A1),
            titleColor = Color(0xFF035582),
        ) to QuickToolId.ToWord,
        QuickTool(
            titleRes = R.string.delete_pages,
            icon = Icons.Outlined.DeleteSweep,
            tint = TintRose,
            iconTint = Color(0xFFBE123C),
            titleColor = Color(0xFF881337),
        ) to QuickToolId.DeletePages,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tools.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { (tool, id) ->
                    ToolCard(
                        tool = tool,
                        onClick = { onToolClick(id) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        ToolsHintBanner()
    }
}

@Composable
private fun ToolCard(
    tool: QuickTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(108.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = tool.tint,
                    start = Offset(0f, 0f),
                    end = Offset(400f, 500f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.85f), shape)
            .clickable(onClick = onClick),
    ) {
        // Specular glass highlight
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.10f),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(500f, 500f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    lineHeight = 18.sp,
                ),
                color = tool.titleColor,
            )
        }
    }
}

@Composable
private fun ToolsHintBanner() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BrandTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = stringResource(R.string.tools_local_hint),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = Color(0xFF475569),
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsScreenPreview() {
    AndrosTheme {
        StudioBackground {
            ToolsScreen(onToolClick = {})
        }
    }
}
