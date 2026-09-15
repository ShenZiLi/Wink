package com.wink.eye.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 高质量明暗双主题 token
// 理念：中性底 + 单一靛强调色（克制），细腻表面分层（tonal elevation），
// 无衬线强调字重，现代编辑气质。避免奶油/高饱和（既往方案偏土气）。
// 单强调色在明/暗各自提亮/压暗，保持品牌统一。
// ============================================================

// ---- 亮色 ----
val LightPrimary = Color(0xFF6C5CE7)              // 靛强调
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE6E2FD)
val LightOnPrimaryContainer = Color(0xFF221E5B)

val LightSecondary = Color(0xFF5B6272)            // 冷灰蓝辅助
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0E4EE)
val LightOnSecondaryContainer = Color(0xFF191C28)

val LightTertiary = Color(0xFF9C6A31)             // 琥珀暖辅助（克制）
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF2DFC3)
val LightOnTertiaryContainer = Color(0xFF3B270B)

val LightBackground = Color(0xFFF7F7F9)           // 中性微冷白
val LightOnBackground = Color(0xFF1A1B1E)
val LightSurface = Color(0xFFF7F7F9)
val LightOnSurface = Color(0xFF1A1B1E)
val LightSurfaceVariant = Color(0xFFE9E9EE)
val LightOnSurfaceVariant = Color(0xFF4A4B52)
// —— M3 表面分层（tonal，不用硬阴影）——
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F1F4)
val LightSurfaceContainer = Color(0xFFECECEF)
val LightSurfaceContainerHigh = Color(0xFFE6E6EA)
val LightSurfaceContainerHighest = Color(0xFFE0E1E5)
val LightSurfaceBright = Color(0xFFF7F7F9)
val LightSurfaceDim = Color(0xFFDCDCE0)
val LightSurfaceTint = LightPrimary
val LightOutline = Color(0xFFB9BAC1)
val LightOutlineVariant = Color(0xFFE4E4E9)
val LightScrim = Color(0x4D000000)

val LightError = Color(0xFFC7392F)
val LightOnError = Color(0xFFFFFFFF)

// ---- 暗色 ----
val DarkPrimary = Color(0xFFA79CF7)               // 靛在暗色提亮
val DarkOnPrimary = Color(0xFF2A2560)
val DarkPrimaryContainer = Color(0xFF403B9A)
val DarkOnPrimaryContainer = Color(0xFFE8E4FF)

val DarkSecondary = Color(0xFFC0C7D9)
val DarkOnSecondary = Color(0xFF292E3C)
val DarkSecondaryContainer = Color(0xFF3F4454)
val DarkOnSecondaryContainer = Color(0xFFDDE1EF)

val DarkTertiary = Color(0xFFD9BC8F)
val DarkOnTertiary = Color(0xFF3B2B10)
val DarkTertiaryContainer = Color(0xFF54421E)
val DarkOnTertiaryContainer = Color(0xFFF9E8C6)

val DarkBackground = Color(0xFF121316)            // 中性深黑
val DarkOnBackground = Color(0xFFE7E8E9)
val DarkSurface = Color(0xFF121316)
val DarkOnSurface = Color(0xFFE7E8E9)
val DarkSurfaceVariant = Color(0xFF44454A)
val DarkOnSurfaceVariant = Color(0xFFC4C5CB)
// —— M3 表面分层 ——
val DarkSurfaceContainerLowest = Color(0xFF0D0E10)
val DarkSurfaceContainerLow = Color(0xFF1A1B1E)
val DarkSurfaceContainer = Color(0xFF1E1F23)
val DarkSurfaceContainerHigh = Color(0xFF292A2E)
val DarkSurfaceContainerHighest = Color(0xFF333438)
val DarkSurfaceBright = Color(0xFF393A3E)
val DarkSurfaceDim = Color(0xFF121316)
val DarkSurfaceTint = DarkPrimary
val DarkOutline = Color(0xFF5E5F66)
val DarkOutlineVariant = Color(0xFF333438)
val DarkScrim = Color(0x66000000)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)