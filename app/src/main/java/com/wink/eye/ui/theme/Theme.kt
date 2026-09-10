package com.wink.eye.ui.theme

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode {
    LIGHT, DARK
}

object ThemeManager {
    private const val PREFS_NAME = "wink_prefs"
    private const val KEY_THEME = "theme_mode"

    private val _themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeMode: Flow<ThemeMode> = _themeMode

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        _themeMode.value = ThemeMode.valueOf(name)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }

    fun toggle(context: Context): ThemeMode {
        val next = if (_themeMode.value == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        setThemeMode(context, next)
        return next
    }
}

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceTint = LightSurfaceTint,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    error = LightError,
    onError = LightOnError
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceTint = DarkSurfaceTint,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    error = DarkError,
    onError = DarkOnError
)

/**
 * 现代无衬线强调排版：display/headline 用系统 sans + 加粗、适度负字距，
 * 标题 semi-bold，正文 regular。干净、高可读、现代编辑气质。
 */
private val WinkTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp,
        lineHeight = 42.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        lineHeight = 36.sp, letterSpacing = (-0.4).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp,
        lineHeight = 30.sp, letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp,
        lineHeight = 34.sp, letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
        lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
        lineHeight = 26.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        lineHeight = 26.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 22.sp, letterSpacing = 0.1.sp
    )
)

@Composable
fun WinkTheme(content: @Composable () -> Unit) {
    val themeMode by ThemeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)

    val darkTheme = themeMode == ThemeMode.DARK

    /**
     * 用颜色插值实现主题过渡，**不要**改用 Crossfade。
     *
     * Crossfade 会为新的 targetState 重新组合内容子树，导致 [content] 内的
     * `rememberNavController()` 被重建、导航栈重置回起始目的地
     * （表现为在 EarClock 页切换主题后自动跳回 Wink 页）。
     * 插值方案只改变配色，内容树的组合位置完全不变，导航状态得以保留。
     */
    var isFirstComposition by remember { mutableStateOf(true) }
    val darkFraction by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        // 首次组合直接到位，避免启动时出现一次从亮到暗的闪烁
        animationSpec = if (isFirstComposition) snap() else tween(300),
        label = "dark_fraction"
    )
    LaunchedEffect(Unit) { isFirstComposition = false }

    MaterialTheme(
        colorScheme = lerpColorScheme(LightColorScheme, DarkColorScheme, darkFraction),
        typography = WinkTypography,
        shapes = WinkShapes,
        content = content
    )
}

/**
 * 逐字段插值两套配色，得到过渡中间态。
 *
 * 当前 Material3 版本没有提供 `lerp(ColorScheme, ColorScheme, Float)`，故自行实现；
 * `copy` 未列出的字段（如各 fixed 系列）沿用起始配色，项目未使用这些字段。
 */
private fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return start
    if (fraction >= 1f) return stop
    fun blend(a: Color, b: Color): Color = lerp(a, b, fraction)
    return start.copy(
        primary = blend(start.primary, stop.primary),
        onPrimary = blend(start.onPrimary, stop.onPrimary),
        primaryContainer = blend(start.primaryContainer, stop.primaryContainer),
        onPrimaryContainer = blend(start.onPrimaryContainer, stop.onPrimaryContainer),
        inversePrimary = blend(start.inversePrimary, stop.inversePrimary),
        secondary = blend(start.secondary, stop.secondary),
        onSecondary = blend(start.onSecondary, stop.onSecondary),
        secondaryContainer = blend(start.secondaryContainer, stop.secondaryContainer),
        onSecondaryContainer = blend(start.onSecondaryContainer, stop.onSecondaryContainer),
        tertiary = blend(start.tertiary, stop.tertiary),
        onTertiary = blend(start.onTertiary, stop.onTertiary),
        tertiaryContainer = blend(start.tertiaryContainer, stop.tertiaryContainer),
        onTertiaryContainer = blend(start.onTertiaryContainer, stop.onTertiaryContainer),
        background = blend(start.background, stop.background),
        onBackground = blend(start.onBackground, stop.onBackground),
        surface = blend(start.surface, stop.surface),
        onSurface = blend(start.onSurface, stop.onSurface),
        surfaceVariant = blend(start.surfaceVariant, stop.surfaceVariant),
        onSurfaceVariant = blend(start.onSurfaceVariant, stop.onSurfaceVariant),
        surfaceTint = blend(start.surfaceTint, stop.surfaceTint),
        inverseSurface = blend(start.inverseSurface, stop.inverseSurface),
        inverseOnSurface = blend(start.inverseOnSurface, stop.inverseOnSurface),
        error = blend(start.error, stop.error),
        onError = blend(start.onError, stop.onError),
        errorContainer = blend(start.errorContainer, stop.errorContainer),
        onErrorContainer = blend(start.onErrorContainer, stop.onErrorContainer),
        outline = blend(start.outline, stop.outline),
        outlineVariant = blend(start.outlineVariant, stop.outlineVariant),
        scrim = blend(start.scrim, stop.scrim),
        surfaceBright = blend(start.surfaceBright, stop.surfaceBright),
        surfaceDim = blend(start.surfaceDim, stop.surfaceDim),
        surfaceContainer = blend(start.surfaceContainer, stop.surfaceContainer),
        surfaceContainerHigh = blend(start.surfaceContainerHigh, stop.surfaceContainerHigh),
        surfaceContainerHighest = blend(start.surfaceContainerHighest, stop.surfaceContainerHighest),
        surfaceContainerLow = blend(start.surfaceContainerLow, stop.surfaceContainerLow),
        surfaceContainerLowest = blend(start.surfaceContainerLowest, stop.surfaceContainerLowest)
    )
}

/** 现代圆角：小 6、按钮 10、卡片 14、大容器 20、胶囊/pill 28 */
private val WinkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 布局尺寸常量。object 成员便于跨包引用。
 */
object WinkLayoutOverlay {
    /**
     * 底部悬浮液态玻璃菜单栏在内容区预留的遮挡高度
     * （玻璃条 70 + 上下内边距 24 + 悬浮留白 12 + 额外间距 ≈ 116）。
     * 各页面底部留白统一引用此常量，保证切换时列表底部对齐一致。
     */
    val BottomBarOverlayHeight = 116.dp

    /**
     * Wink 页底部调试面板的底边留白。
     *
     * 比列表用的 [BottomBarOverlayHeight] 小 16dp，让面板整体更贴近悬浮菜单栏；
     * 面板 Card 自带 8dp 内边距，叠加后与玻璃条顶部仍留有约 14dp 呼吸间距，不会被遮挡。
     */
    val DebugPanelBottomPadding = 100.dp
}