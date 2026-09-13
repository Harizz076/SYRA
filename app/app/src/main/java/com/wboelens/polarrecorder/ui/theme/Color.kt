@file:Suppress("MagicNumber")

package com.wboelens.polarrecorder.ui.theme

import androidx.compose.ui.graphics.Color

// ─── SYRA Brand Core ─────────────────────────────────────────────────────────
val SyraBlue          = Color(0xFF2F4865) // Primary brand, headers, navigation
val SyraBackground    = Color(0xFFF3EFE3) // Primary canvas / scaffold background
val SyraSurface       = Color(0xFFEDE9DC) // Secondary containers / offset background
val SyraText          = Color(0xFF1E2E40) // Body text / primary typography
val SyraWhite         = Color(0xFFFFFFFF) // Input fields / clean selection indicators

// ─── SYRA Primary Tones ──────────────────────────────────────────────────────
val SyraBlueDark      = Color(0xFF1E3048) // Pressed / active state for primary
val SyraBlueLight     = Color(0xFF4A6585) // Lighter accent for secondary elements
val SyraBlueMuted     = Color(0xFFB8C8D8) // Muted tint for chips, dividers

// ─── SYRA Surface Tones ──────────────────────────────────────────────────────
val SyraSurfaceDim    = Color(0xFFE0DCCF) // Dimmed surface for input backgrounds
val SyraBorder        = Color(0xFFCDC8BA) // Subtle borders / outline variant

// ─── SYRA Semantic Colors ────────────────────────────────────────────────────
val SyraError         = Color(0xFFBA1A1A)
val SyraErrorContainer= Color(0xFFFFDAD6)
val SyraOnError       = Color(0xFFFFFFFF)
val SyraOnErrorContainer = Color(0xFF93000A)

val SyraSuccess       = Color(0xFF2E6E4E) // Connected / recording states
val SyraWarning       = Color(0xFFC76B00) // Caution / pending states

// ─── Light Color Scheme (SYRA default — app is light-only) ───────────────────
val primaryLight                   = SyraBlue
val onPrimaryLight                 = SyraWhite
val primaryContainerLight          = Color(0xFFD6E4F0)
val onPrimaryContainerLight        = Color(0xFF1A2E42)

val secondaryLight                 = Color(0xFF4A6E5A)
val onSecondaryLight               = SyraWhite
val secondaryContainerLight        = Color(0xFFCCE8D8)
val onSecondaryContainerLight      = Color(0xFF1C3828)

val tertiaryLight                  = Color(0xFF6B5778)
val onTertiaryLight                = SyraWhite
val tertiaryContainerLight         = Color(0xFFF0DAFF)
val onTertiaryContainerLight       = Color(0xFF261432)

val errorLight                     = SyraError
val onErrorLight                   = SyraOnError
val errorContainerLight            = SyraErrorContainer
val onErrorContainerLight          = SyraOnErrorContainer

val backgroundLight                = SyraBackground
val onBackgroundLight              = SyraText

val surfaceLight                   = SyraBackground
val onSurfaceLight                 = SyraText
val surfaceVariantLight            = SyraSurface
val onSurfaceVariantLight          = Color(0xFF3C4A5A)

val outlineLight                   = Color(0xFF8A9BAC)
val outlineVariantLight            = SyraBorder

val scrimLight                     = Color(0xFF000000)
val inverseSurfaceLight            = SyraText
val inverseOnSurfaceLight          = SyraBackground
val inversePrimaryLight            = SyraBlueMuted

val surfaceDimLight                = SyraSurfaceDim
val surfaceBrightLight             = SyraBackground
val surfaceContainerLowestLight    = SyraWhite
val surfaceContainerLowLight       = Color(0xFFF8F4EC)
val surfaceContainerLight          = SyraSurface
val surfaceContainerHighLight      = Color(0xFFE8E3D6)
val surfaceContainerHighestLight   = Color(0xFFE2DDD0)

// ─── Warning Extended Color ───────────────────────────────────────────────────
val warningLight          = SyraWarning
val onWarningLight        = SyraWhite
val warningContainerLight = Color(0xFFFFDDB8)
val onWarningContainerLight = Color(0xFF3B1F00)

val warningDark           = Color(0xFFFFB870)
val onWarningDark         = Color(0xFF4A2800)
val warningContainerDark  = Color(0xFF6B3C00)
val onWarningContainerDark = Color(0xFFFFDDB8)
