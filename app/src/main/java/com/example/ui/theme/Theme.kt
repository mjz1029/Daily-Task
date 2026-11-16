package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
  primary = JadePrimary,
  onPrimary = JadeOnPrimary,
  primaryContainer = JadePrimaryContainer,
  onPrimaryContainer = JadeOnPrimaryContainer,
  secondary = SlateSecondary,
  onSecondary = SlateOnSecondary,
  secondaryContainer = SlateSecondaryContainer,
  onSecondaryContainer = SlateOnSecondaryContainer,
  tertiary = AmberTertiary,
  onTertiary = AmberOnTertiary,
  tertiaryContainer = AmberTertiaryContainer,
  onTertiaryContainer = AmberOnTertiaryContainer,
  error = CoralError,
  onError = CoralOnError,
  errorContainer = CoralErrorContainer,
  onErrorContainer = CoralOnErrorContainer,
  background = IvoryBackground,
  onBackground = InkOnBackground,
  surface = IvoryBackground,
  onSurface = InkOnBackground,
  surfaceVariant = SurfaceContainerLight,
  onSurfaceVariant = MutedTextLight,
  surfaceContainerLowest = SurfaceContainerLowestLight,
  surfaceContainerLow = SurfaceContainerLowLight,
  surfaceContainer = SurfaceContainerLight,
  surfaceContainerHigh = SurfaceContainerHighLight,
  outline = OutlineLight,
  outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
  primary = JadePrimaryDark,
  onPrimary = JadeOnPrimaryDark,
  primaryContainer = JadePrimaryContainerDark,
  onPrimaryContainer = JadeOnPrimaryContainerDark,
  secondary = SlateSecondaryDark,
  onSecondary = SlateOnSecondaryDark,
  secondaryContainer = SlateSecondaryContainerDark,
  onSecondaryContainer = SlateOnSecondaryContainerDark,
  tertiary = AmberTertiaryDark,
  onTertiary = AmberOnTertiaryDark,
  tertiaryContainer = AmberTertiaryContainerDark,
  onTertiaryContainer = AmberOnTertiaryContainerDark,
  error = CoralErrorDark,
  onError = CoralOnErrorDark,
  errorContainer = CoralErrorContainerDark,
  onErrorContainer = CoralOnErrorContainerDark,
  background = ObsidianBackground,
  onBackground = SnowOnBackground,
  surface = ObsidianBackground,
  onSurface = SnowOnBackground,
  surfaceVariant = SurfaceContainerDark,
  onSurfaceVariant = MutedTextDark,
  surfaceContainerLowest = SurfaceContainerLowestDark,
  surfaceContainerLow = SurfaceContainerLowDark,
  surfaceContainer = SurfaceContainerDark,
  surfaceContainerHigh = SurfaceContainerHighDark,
  outline = OutlineDark,
  outlineVariant = OutlineVariantDark,
)

private val AppShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TodayTasksTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content,
  )
}
