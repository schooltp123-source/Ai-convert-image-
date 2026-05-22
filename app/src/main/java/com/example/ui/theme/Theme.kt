package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PurplePrimaryDark,
    secondary = PurpleSecondaryDark,
    tertiary = PurpleTertiaryDark,
    background = PurpleBackgroundDark,
    surface = PurpleSurfaceDark,
    surfaceVariant = PurpleSurfaceVariantDark,
    onPrimary = PurpleOnPrimaryDark,
    onSecondary = PurpleOnSecondaryDark,
    onBackground = PurpleOnSurfaceDark,
    onSurface = PurpleOnSurfaceDark,
    primaryContainer = PurplePrimaryContainerDark,
    onPrimaryContainer = PurpleOnPrimaryContainerDark,
    secondaryContainer = PurpleSecondaryContainerDark,
    onSecondaryContainer = PurpleOnSecondaryContainerDark,
    outline = PurpleOutlineDark,
    outlineVariant = PurpleOutlineVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PurplePrimaryLight,
    secondary = PurpleSecondaryLight,
    tertiary = PurpleTertiaryLight,
    background = PurpleBackgroundLight,
    surface = PurpleSurfaceLight,
    surfaceVariant = PurpleSurfaceVariantLight,
    onPrimary = PurpleOnPrimaryLight,
    onSecondary = PurpleOnSecondaryLight,
    onBackground = PurpleOnSurfaceLight,
    onSurface = PurpleOnSurfaceLight,
    primaryContainer = PurplePrimaryContainerLight,
    onPrimaryContainer = PurpleOnPrimaryContainerLight,
    secondaryContainer = PurpleSecondaryContainerLight,
    onSecondaryContainer = PurpleOnSecondaryContainerLight,
    outline = PurpleOutlineLight,
    outlineVariant = PurpleOutlineVariantLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Use static customized signature colors by default for high visual fidelity
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
