package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun WallpaperBackground(
    wallpaperType: String,
    customWallpaperUri: String? = null,
    overlayOpacity: Float = 0.5f,
    blurIntensity: Float = 10f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background Base Render
        if (wallpaperType == "custom_uri" && !customWallpaperUri.isNull_Blank()) {
            AsyncImage(
                model = Uri.parse(customWallpaperUri),
                contentDescription = "Custom Alarm Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurIntensity > 0f) Modifier.blur(blurIntensity.dp) else Modifier)
            )
        } else {
            // Preset canvas gradients
            val brush = when (wallpaperType) {
                "preset_library" -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFFB45309),
                        Color(0xFF0F172A)
                    )
                )
                "preset_cyber" -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0284C7),
                        Color(0xFF0F172A),
                        Color(0xFF7E22CE),
                        Color(0xFF090D16)
                    )
                )
                "preset_nordic" -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF064E3B),
                        Color(0xFF0F172A),
                        Color(0xFF047857),
                        Color(0xFF062C22)
                    )
                )
                else -> // "preset_sunrise" / default
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB45309),
                            Color(0xFFD97706),
                            Color(0xFF451A03),
                            Color(0xFF0F172A)
                        )
                    )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush)
                    .then(if (blurIntensity > 0f) Modifier.blur(blurIntensity.dp) else Modifier)
            )
        }

        // Dark Overlay Tint for Readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayOpacity.coerceIn(0.1f, 0.9f)))
        )

        // Foreground Content
        content()
    }
}

private fun String?.isNull_Blank(): Boolean = this.isNullOrEmpty() || this.trim().isEmpty()
