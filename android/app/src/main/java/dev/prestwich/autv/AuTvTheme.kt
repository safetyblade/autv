package dev.prestwich.autv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The original guide/player palette, shared with Settings rather than approximated.
internal val Ink = Color(0xFF070A10)
internal val Glass = Color(0xF0181C24)
internal val WarmWhite = Color(0xFFF2EFE9)
internal val Silver = Color(0xFFCEC4B4)
internal val Muted = Color(0xFFA5A8AF)

@Composable
internal fun AuTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Silver, onPrimary = Ink, background = Ink, surface = Glass,
        onSurface = WarmWhite, onSurfaceVariant = Muted, outline = Color(0xFF595B62),
        primaryContainer = Color(0xFF34343B), onPrimaryContainer = WarmWhite,
        secondary = Silver, secondaryContainer = Color(0xFF34343B), onSecondaryContainer = WarmWhite,
        surfaceTint = Silver, surfaceContainer = Glass, surfaceContainerHigh = Glass,
        surfaceContainerLow = Ink, error = Color(0xFFD8B3AD)), content = content)
}
