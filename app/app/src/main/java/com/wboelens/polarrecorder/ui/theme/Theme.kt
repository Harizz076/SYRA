package com.wboelens.polarrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── SYRA Light Colour Scheme (app is always light — research lab standard) ──
private val syraLightColorScheme = lightColorScheme(
    primary                = primaryLight,
    onPrimary              = onPrimaryLight,
    primaryContainer       = primaryContainerLight,
    onPrimaryContainer     = onPrimaryContainerLight,
    secondary              = secondaryLight,
    onSecondary            = onSecondaryLight,
    secondaryContainer     = secondaryContainerLight,
    onSecondaryContainer   = onSecondaryContainerLight,
    tertiary               = tertiaryLight,
    onTertiary             = onTertiaryLight,
    tertiaryContainer      = tertiaryContainerLight,
    onTertiaryContainer    = onTertiaryContainerLight,
    error                  = errorLight,
    onError                = onErrorLight,
    errorContainer         = errorContainerLight,
    onErrorContainer       = onErrorContainerLight,
    background             = backgroundLight,
    onBackground           = onBackgroundLight,
    surface                = surfaceLight,
    onSurface              = onSurfaceLight,
    surfaceVariant         = surfaceVariantLight,
    onSurfaceVariant       = onSurfaceVariantLight,
    outline                = outlineLight,
    outlineVariant         = outlineVariantLight,
    scrim                  = scrimLight,
    inverseSurface         = inverseSurfaceLight,
    inverseOnSurface       = inverseOnSurfaceLight,
    inversePrimary         = inversePrimaryLight,
    surfaceDim             = surfaceDimLight,
    surfaceBright          = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow    = surfaceContainerLowLight,
    surfaceContainer       = surfaceContainerLight,
    surfaceContainerHigh   = surfaceContainerHighLight,
    surfaceContainerHighest= surfaceContainerHighestLight,
)

// ─── Extended Color Scheme (warning tones for sensor status) ─────────────────
@Immutable
data class ExtendedColorScheme(
    val warning: ColorFamily,
    val success: ColorFamily,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
)

private val syraExtendedLight = ExtendedColorScheme(
    warning = ColorFamily(
        color            = warningLight,
        onColor          = onWarningLight,
        colorContainer   = warningContainerLight,
        onColorContainer = onWarningContainerLight,
    ),
    success = ColorFamily(
        color            = SyraSuccess,
        onColor          = SyraWhite,
        colorContainer   = Color(0xFFB7E5CC),
        onColorContainer = Color(0xFF042414),
    ),
)

val LocalExtendedColorScheme = staticCompositionLocalOf<ExtendedColorScheme> {
    error("No ExtendedColorScheme provided")
}

// ─── AppTheme ─────────────────────────────────────────────────────────────────
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use deprecated API only below API 35 where edge-to-edge is not forced.
            // On API 35+ the system ignores these values anyway.
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 35) {
                window.statusBarColor     = backgroundLight.toArgb()
                window.navigationBarColor = backgroundLight.toArgb()
            }
            // Ensure status-bar icons are dark (background is always light in SYRA)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    CompositionLocalProvider(LocalExtendedColorScheme provides syraExtendedLight) {
        MaterialTheme(
            colorScheme = syraLightColorScheme,
            typography  = AppTypography,
            content     = content,
        )
    }
}
