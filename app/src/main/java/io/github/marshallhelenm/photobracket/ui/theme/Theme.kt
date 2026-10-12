package io.github.marshallhelenm.photobracket.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Bright palette colors carry dark text; white on Tangerine or Blush would be too low-contrast.
private val DarkColorScheme = darkColorScheme(
    primary = Tangerine,
    onPrimary = Ink,
    primaryContainer = Terracotta,
    onPrimaryContainer = Ink,
    secondary = Orchid,
    onSecondary = Ink,
    secondaryContainer = Plum,
    onSecondaryContainer = Color.White,
    tertiary = Blush,
    onTertiary = Ink,
    tertiaryContainer = Rosewood,
    onTertiaryContainer = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Tangerine,
    onPrimary = Ink,
    primaryContainer = Pumpkin,
    onPrimaryContainer = Ink,
    secondary = Plum,
    onSecondary = Color.White,
    secondaryContainer = Orchid,
    onSecondaryContainer = Ink,
    tertiary = Clay,
    onTertiary = Color.White,
    tertiaryContainer = Blush,
    onTertiaryContainer = Ink,
)

@Composable
fun PhotoBracketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Android 12+) would replace the brand palette with wallpaper colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Tangerine text is too faint on light backgrounds, so text-only buttons use the secondary color. */
@Composable
fun textButtonColors() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

@Composable
fun outlinedButtonColors() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
