/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.server.edge.gallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.server.edge.gallery.proto.Theme

private val lightScheme =
  lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
  )

private val darkScheme =
  darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
  )

@Immutable
data class CustomColors(
  val appTitleGradientColors: List<Color> = listOf(),
  val tabHeaderBgColor: Color = Color.Transparent,
  val taskCardBgColor: Color = Color.Transparent,
  val taskBgColors: List<Color> = listOf(),
  val taskBgGradientColors: List<List<Color>> = listOf(),
  val taskIconColors: List<Color> = listOf(),
  val taskIconShapeBgColor: Color = Color.Transparent,
  val homeBottomGradient: List<Color> = listOf(),
  val userBubbleBgColor: Color = Color.Transparent,
  val agentBubbleBgColor: Color = Color.Transparent,
  val linkColor: Color = Color.Transparent,
  val successColor: Color = Color.Transparent,
  val recordButtonBgColor: Color = Color.Transparent,
  val waveFormBgColor: Color = Color.Transparent,
  val modelInfoIconColor: Color = Color.Transparent,
  val warningContainerColor: Color = Color.Transparent,
  val warningTextColor: Color = Color.Transparent,
  val errorContainerColor: Color = Color.Transparent,
  val errorTextColor: Color = Color.Transparent,
  val newFeatureContainerColor: Color = Color.Transparent,
  val newFeatureTextColor: Color = Color.Transparent,
  val bgStarColor: Color = Color.Transparent,
  val promoBannerBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
  val promoBannerIconBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

private val HarborShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

val lightCustomColors =
  CustomColors(
    appTitleGradientColors = listOf(Color(0xFF8C3E22), Color(0xFF1A5C56)),
    tabHeaderBgColor = Color(0xFF8C3E22),
    taskCardBgColor = surfaceContainerLowestLight,
    taskBgColors =
      listOf(
        Color(0xFFF8EBE4),
        Color(0xFFE5F3F0),
        Color(0xFFF6EEDC),
        Color(0xFFEFE7DC),
      ),
    taskBgGradientColors =
      listOf(
        listOf(Color(0xFFC56A45), Color(0xFF8C3E22)),
        listOf(Color(0xFF3E8F86), Color(0xFF1A5C56)),
        listOf(Color(0xFFC4A15A), Color(0xFF6B5424)),
        listOf(Color(0xFF8A6A4A), Color(0xFF4E3824)),
      ),
    taskIconColors =
      listOf(
        Color(0xFF8C3E22),
        Color(0xFF1A5C56),
        Color(0xFF6B5424),
        Color(0xFF4E3824),
      ),
    taskIconShapeBgColor = Color(0xFFFFFCF7),
    homeBottomGradient = listOf(Color(0x00F6F1E8), Color(0x66E7C7B4)),
    agentBubbleBgColor = Color(0xFFEBE4D8),
    userBubbleBgColor = Color(0xFF6E3A24),
    linkColor = Color(0xFF1A5C56),
    successColor = Color(0xFF1A5C56),
    recordButtonBgColor = Color(0xFF9F2D2D),
    waveFormBgColor = Color(0xFFB7A894),
    modelInfoIconColor = Color(0xFF85786A),
    warningContainerColor = Color(0xFFF6EEDC),
    warningTextColor = Color(0xFF8A5A12),
    errorContainerColor = Color(0xFFF8D9D4),
    errorTextColor = Color(0xFF9F2D2D),
    newFeatureContainerColor = Color(0xFFF3E4C0),
    newFeatureTextColor = Color(0xFF4E3824),
    bgStarColor = Color(0x338C3E22),
    promoBannerBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.0f to Color(0x33C56A45),
            0.55f to Color(0x221A5C56),
            1.0f to Color(0x146B5424),
          ),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
      ),
    promoBannerIconBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.2f to Color(0x668C3E22),
            0.7f to Color(0x661A5C56),
          ),
        start = Offset(0f, 1f),
        end = Offset(1f, 0f),
      ),
  )

val darkCustomColors =
  CustomColors(
    appTitleGradientColors = listOf(Color(0xFFF0B08A), Color(0xFF8ED4CB)),
    tabHeaderBgColor = Color(0xFF6E3A24),
    taskCardBgColor = surfaceContainerHighDark,
    taskBgColors =
      listOf(
        Color(0xFF2A1C16),
        Color(0xFF142422),
        Color(0xFF2A2418),
        Color(0xFF241E18),
      ),
    taskBgGradientColors =
      listOf(
        listOf(Color(0xFFE09570), Color(0xFFC56A45)),
        listOf(Color(0xFF8ED4CB), Color(0xFF3E8F86)),
        listOf(Color(0xFFE4C98A), Color(0xFFC4A15A)),
        listOf(Color(0xFFC4A88A), Color(0xFF8A6A4A)),
      ),
    taskIconColors =
      listOf(
        Color(0xFFF0B08A),
        Color(0xFF8ED4CB),
        Color(0xFFE4C98A),
        Color(0xFFC4A88A),
      ),
    taskIconShapeBgColor = Color(0xFF1A1814),
    homeBottomGradient = listOf(Color(0x0012110E), Color(0x338C3E22)),
    agentBubbleBgColor = Color(0xFF2C2822),
    userBubbleBgColor = Color(0xFF6E3A24),
    linkColor = Color(0xFF8ED4CB),
    successColor = Color(0xFF8ED4CB),
    recordButtonBgColor = Color(0xFFE07A72),
    waveFormBgColor = Color(0xFF9C8F80),
    modelInfoIconColor = Color(0xFF9C8F80),
    warningContainerColor = Color(0xFF574418),
    warningTextColor = Color(0xFFE4C98A),
    errorContainerColor = Color(0xFF5C2A26),
    errorTextColor = Color(0xFFF2B8B5),
    newFeatureContainerColor = Color(0xFF574418),
    newFeatureTextColor = Color(0xFFE4C98A),
    bgStarColor = Color(0x33F0B08A),
    promoBannerBgBrush =
      Brush.linearGradient(
        colorStops = arrayOf(0.0f to Color(0x886E3A24), 1.0f to Color(0x6612110E)),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
      ),
    promoBannerIconBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.2f to Color(0x99C56A45),
            0.8f to Color(0x993E8F86),
          ),
        start = Offset(0f, 1f),
        end = Offset(1f, 0f),
      ),
  )

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

/**
 * Controls the color of the phone's status bar icons based on whether the app is using a dark
 * theme.
 */
@Composable
fun StatusBarColorController(useDarkTheme: Boolean) {
  val view = LocalView.current
  val currentWindow = (view.context as? Activity)?.window

  if (currentWindow != null) {
    SideEffect {
      WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
      val controller = WindowCompat.getInsetsController(currentWindow, view)
      controller.isAppearanceLightStatusBars = !useDarkTheme // Set to true for light icons
    }
  }
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
  val themeOverride = ThemeSettings.themeOverride
  val darkTheme: Boolean =
    (isSystemInDarkTheme() || themeOverride.value == Theme.THEME_DARK) &&
      themeOverride.value != Theme.THEME_LIGHT
  val view = LocalView.current

  StatusBarColorController(useDarkTheme = darkTheme)

  val colorScheme =
    when {
      darkTheme -> darkScheme
      else -> lightScheme
    }

  val customColorsPalette = if (darkTheme) darkCustomColors else lightCustomColors

  CompositionLocalProvider(LocalCustomColors provides customColorsPalette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = HarborShapes,
      content = content,
    )
  }

  // Make sure the navigation bar stays transparent on manual theme changes.
  LaunchedEffect(darkTheme) {
    val window = (view.context as Activity).window

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
  }
}
