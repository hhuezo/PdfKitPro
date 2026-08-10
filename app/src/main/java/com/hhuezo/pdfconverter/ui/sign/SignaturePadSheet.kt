package com.hhuezo.pdfconverter.ui.sign

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.ui.theme.Primary
import kotlin.math.roundToInt

private data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val widthDp: Float,
)

private val SignatureColors = listOf(
    Color(0xFF1565C0), // Azul lapicero (default)
    Color(0xFF191C1E), // Negro
    Color(0xFF424242),
    Color(0xFFB7131A),
    Color(0xFFE53935),
    Color(0xFFD81B60),
    Color(0xFF8E24AA),
    Color(0xFF5E35B1),
    Color(0xFF3949AB),
    Color(0xFF039BE5),
    Color(0xFF00ACC1),
    Color(0xFF00897B),
    Color(0xFF2E7D32),
    Color(0xFF7CB342),
    Color(0xFFF9A825),
    Color(0xFFFB8C00),
    Color(0xFF6D4C41),
    Color(0xFF546E7A),
)

private const val MinStrokeDp = 1f
private const val MaxStrokeDp = 12f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadSheet(
    title: String,
    onDismiss: () -> Unit,
    onSaved: (Bitmap) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedColor by remember { mutableStateOf(SignatureColors[0]) }
    var selectedWidthDp by remember { mutableFloatStateOf(3.5f) }
    val density = LocalDensity.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.15f)
                    .height(4.dp)
                    .background(Color(0xFFE4BEB9), RoundedCornerShape(999.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF191C1E),
                )
                TextButton(
                    onClick = {
                        strokes.clear()
                        currentPoints = emptyList()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.sign_clear),
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = stringResource(R.string.sign_color),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF5B403D),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignatureColors.forEach { color ->
                    val selected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(if (selected) 34.dp else 30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) Primary else Color(0xFFE4BEB9),
                                shape = CircleShape,
                            )
                            .clickable {
                                selectedColor = color
                                // Apply to the signature already drawn on the canvas.
                                for (i in strokes.indices) {
                                    strokes[i] = strokes[i].copy(color = color)
                                }
                            },
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.sign_thickness_value,
                    selectedWidthDp.roundToInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF5B403D),
            )
            Slider(
                value = selectedWidthDp,
                onValueChange = { value ->
                    selectedWidthDp = value
                    // Live preview on the real signature, not a separate sample bar.
                    for (i in strokes.indices) {
                        strokes[i] = strokes[i].copy(widthDp = value)
                    }
                },
                valueRange = MinStrokeDp..MaxStrokeDp,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Primary,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Primary.copy(alpha = 0.2f),
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .border(
                        width = 2.dp,
                        color = Color(0xFFE4BEB9),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(Color(0xFFF2F4F6), RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(selectedColor, selectedWidthDp) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints = listOf(offset)
                            },
                            onDragEnd = {
                                if (currentPoints.size > 1) {
                                    strokes += InkStroke(
                                        points = currentPoints,
                                        color = selectedColor,
                                        widthDp = selectedWidthDp,
                                    )
                                }
                                currentPoints = emptyList()
                            },
                            onDragCancel = {
                                currentPoints = emptyList()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints = currentPoints + change.position
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun drawStroke(stroke: InkStroke) {
                        if (stroke.points.size < 2) return
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            for (i in 1 until stroke.points.size) {
                                lineTo(stroke.points[i].x, stroke.points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = Stroke(
                                width = with(density) { stroke.widthDp.dp.toPx() },
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                    strokes.forEach(::drawStroke)
                    if (currentPoints.size > 1) {
                        drawStroke(
                            InkStroke(
                                points = currentPoints,
                                color = selectedColor,
                                widthDp = selectedWidthDp,
                            ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.sign_cancel))
                }
                Button(
                    onClick = {
                        val allStrokes = strokes.toList() + listOfNotNull(
                            currentPoints.takeIf { it.size > 1 }?.let {
                                InkStroke(it, selectedColor, selectedWidthDp)
                            },
                        )
                        val bitmap = renderSignatureBitmap(
                            strokes = allStrokes,
                            widthPx = canvasSize.width.coerceAtLeast(1),
                            heightPx = canvasSize.height.coerceAtLeast(1),
                            densityDpiScale = density.density,
                        )
                        if (bitmap != null) {
                            onSaved(bitmap)
                        }
                    },
                    enabled = strokes.isNotEmpty() || currentPoints.size > 1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(stringResource(R.string.sign_save_signature))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun renderSignatureBitmap(
    strokes: List<InkStroke>,
    widthPx: Int,
    heightPx: Int,
    densityDpiScale: Float,
): Bitmap? {
    if (strokes.all { it.points.size < 2 }) return null

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke.widthDp * densityDpiScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        path.moveTo(stroke.points.first().x, stroke.points.first().y)
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].x, stroke.points[i].y)
        }
        canvas.drawPath(path, paint)
    }

    return trimTransparent(bitmap) ?: bitmap
}

private fun trimTransparent(source: Bitmap): Bitmap? {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            val alpha = pixels[y * width + x] ushr 24
            if (alpha > 8) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < minX || maxY < minY) return null
    val pad = 8
    val left = (minX - pad).coerceAtLeast(0)
    val top = (minY - pad).coerceAtLeast(0)
    val right = (maxX + pad).coerceAtMost(width - 1)
    val bottom = (maxY + pad).coerceAtMost(height - 1)
    return Bitmap.createBitmap(
        source,
        left,
        top,
        (right - left + 1).coerceAtLeast(1),
        (bottom - top + 1).coerceAtLeast(1),
    )
}
