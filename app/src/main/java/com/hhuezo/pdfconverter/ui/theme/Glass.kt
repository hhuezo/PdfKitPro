package com.hhuezo.pdfconverter.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS frosted glass — translucent white with soft vertical frost. */
val IosGlassFill: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.52f),
        ),
    )

val IosGlassBarFill: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF4F4F6).copy(alpha = 0.94f),
            Color(0xFFE8E8EC).copy(alpha = 0.88f),
        ),
    )

val IosGlassTintedFill: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.72f),
            Color(0xFFFFF8F8).copy(alpha = 0.48f),
        ),
    )

val IosGlassBorder = Color.White.copy(alpha = 0.55f)
val IosGlassHighlight = Color.White.copy(alpha = 0.7f)
val IosGlassShadow = Color.Black.copy(alpha = 0.07f)

/**
 * iOS-style glass material: soft shadow, frost gradient, hairline border
 * and a specular top highlight (UIVisualEffectView feel).
 */
fun Modifier.iosGlass(
    shape: Shape = RectangleShape,
    elevation: Dp = 8.dp,
    bar: Boolean = false,
    tinted: Boolean = false,
): Modifier {
    val fill = when {
        bar -> IosGlassBarFill
        tinted -> IosGlassTintedFill
        else -> IosGlassFill
    }
    return this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = IosGlassShadow,
                    spotColor = IosGlassShadow,
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(brush = fill, shape = shape)
        .border(width = 0.8.dp, color = IosGlassBorder, shape = shape)
        .drawWithContent {
            drawContent()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        IosGlassHighlight,
                        Color.Transparent,
                    ),
                ),
                start = Offset(size.width * 0.08f, 1.5f),
                end = Offset(size.width * 0.92f, 1.5f),
                strokeWidth = 2.2f,
            )
        }
}

@Composable
fun IosGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape,
    elevation: Dp = 8.dp,
    tinted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.iosGlass(
            shape = shape,
            elevation = elevation,
            tinted = tinted,
        ),
        content = content,
    )
}

/** Ambient canvas so frosted glass reads clearly. */
fun iosStudioBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF9F1F2),
        Color(0xFFF3F3F6),
        Color(0xFFF7F0F2),
        Color(0xFFEEEFF3),
    ),
)

fun Modifier.studioBackground(): Modifier =
    background(iosStudioBackgroundBrush())

/**
 * Shared app canvas — same gradient + brand orbs as Home.
 * Use behind every screen so the look stays consistent.
 */
@Composable
fun StudioBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .studioBackground(),
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDC2626).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}
