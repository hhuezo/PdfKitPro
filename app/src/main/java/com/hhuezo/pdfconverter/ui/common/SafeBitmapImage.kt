package com.hhuezo.pdfconverter.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.hhuezo.pdfconverter.util.isSafeToDraw

@Composable
fun SafeBitmapImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (!bitmap.isSafeToDraw()) return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
