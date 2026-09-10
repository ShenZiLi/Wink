package com.wink.eye.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 液态玻璃（Liquid Glass）统一规范层。
 *
 * 设计依据 Apple WWDC25 Liquid Glass：
 * - 玻璃属于**导航层**，不放进内容层，也不与其他玻璃互相嵌套
 * - 材质由多层构成：背景模糊（backdrop blur）+ 自适应染色（tint）+ 高光（highlight）+ 阴影
 * - 变体分 Regular（自适应）与 Clear（永久透明，需调光层），二者不混用
 * - 染色只给主色元素用，不做整体染色
 *
 * 实现基于 Haze 1.x 的 backdrop blur：内容层打 [winkGlassSource]，导航层打 [winkGlassSurface]，
 * 二者共享同一个 [HazeState]。
 */

/** 液态玻璃变体 */
enum class WinkGlassVariant {
    /** 导航层默认变体：跟随明暗主题自适应染色，正文可读性优先 */
    Regular,

    /** 永久透明变体：仅用于内容色彩丰富、且上方文字足够粗壮的场景 */
    Clear
}

/** 玻璃材质尺寸规格 */
object WinkGlassDefaults {
    /** 悬浮底部导航栏：体量较大，用更厚的材质与更明显的模糊 */
    val NavigationBarBlur = 30.dp

    /** 顶部导航栏：横向条状，模糊略轻 */
    val TopBarBlur = 24.dp
}

/**
 * 构造当前主题下的玻璃样式。
 *
 * @param variant 玻璃变体
 * @param blurRadius 背景模糊半径
 * @param tintBoost 染色强度微调，正值更实、负值更透
 */
@Composable
fun winkGlassStyle(
    variant: WinkGlassVariant = WinkGlassVariant.Regular,
    blurRadius: Dp = WinkGlassDefaults.NavigationBarBlur,
    tintBoost: Float = 0f
): HazeStyle {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    // Regular：底色跟随主题表面色，保证内容可读；Clear：几乎不着色
    val baseAlpha = when (variant) {
        WinkGlassVariant.Regular -> if (isDark) 0.58f else 0.66f
        WinkGlassVariant.Clear -> if (isDark) 0.10f else 0.14f
    }.coerceIn(0f, 1f) + tintBoost

    // 高光染色：亮色主题用白提亮；暗色主题必须压到极低，
    // 否则在这种「表面色 == 背景色」的深色主题下，玻璃会比背景亮出一整档、显得像贴了块板
    val sheenAlpha = when (variant) {
        WinkGlassVariant.Regular -> if (isDark) 0.015f else 0.16f
        WinkGlassVariant.Clear -> if (isDark) 0.008f else 0.08f
    }

    return HazeStyle(
        backgroundColor = scheme.surface.copy(alpha = baseAlpha.coerceIn(0f, 1f)),
        tints = listOf(HazeTint(Color.White.copy(alpha = sheenAlpha))),
        // 磨砂颗粒，避免大面积纯平滑模糊带来的塑料感
        noiseFactor = 0.015f,
        blurRadius = blurRadius,
        // 设备不支持模糊时的回退：用不透明底色保证可读性
        fallbackTint = HazeTint(scheme.surface.copy(alpha = 0.94f))
    )
}

/**
 * 把当前节点标记为**玻璃采样源**：其绘制内容会被上方玻璃模糊采样。
 *
 * 用在内容层（列表、表单等），不产生任何视觉效果。
 */
fun Modifier.winkGlassSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * 把当前节点变成一块玻璃：背景模糊 + 染色 + 顶部高光描边。
 *
 * @param state 与内容层共享的采样源状态
 * @param shape 玻璃外形
 * @param variant 玻璃变体
 * @param blurRadius 背景模糊半径
 * @param drawHighlight 是否绘制顶部高光描边（组件自带高光时可关闭）
 */
@Composable
fun Modifier.winkGlassSurface(
    state: HazeState,
    shape: Shape,
    variant: WinkGlassVariant = WinkGlassVariant.Regular,
    blurRadius: Dp = WinkGlassDefaults.NavigationBarBlur,
    tintBoost: Float = 0f,
    drawHighlight: Boolean = true
): Modifier {
    var modifier = this
        .clip(shape)
        .hazeEffect(
            state = state,
            style = winkGlassStyle(variant = variant, blurRadius = blurRadius, tintBoost = tintBoost)
        )

    if (drawHighlight) {
        // 暗色主题下白色高光非常扎眼，压到 30% 只保留一道可辨识的轮廓
        val highlightStrength = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.3f else 1f
        modifier = modifier.glassHighlight(shape, strength = highlightStrength)
    }
    return modifier
}

/**
 * 玻璃顶部高光描边。
 *
 * 模拟光源自上方照射：顶边最亮，向下迅速衰减，底边留一道极弱的反射光，
 * 让玻璃具有可辨识的薄厚度，而不是一块平铺的半透明色块。
 */
fun Modifier.glassHighlight(
    shape: Shape,
    strength: Float = 1f
): Modifier = this.border(
    width = 1.dp,
    brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.55f * strength),
        0.28f to Color.White.copy(alpha = 0.10f * strength),
        0.72f to Color.White.copy(alpha = 0.04f * strength),
        1f to Color.White.copy(alpha = 0.26f * strength)
    ),
    shape = shape
)

/**
 * 悬浮液态玻璃顶栏。
 *
 * 与底部导航栏同属导航层：玻璃背景由下方内容采样而来，因此**不要**放进 Scaffold 的 topBar 槽位，
 * 而应作为悬浮层叠在内容之上，并让内容的顶部留白等于 [totalHeight]，
 * 这样滚动时列表会从玻璃下方穿过，玻璃才有可模糊的内容。
 *
 * @param title 标题
 * @param hazeState 与内容层共享的采样源
 * @param modifier 位置修饰符
 * @param navigationIcon 左侧导航图标，通常为返回按钮
 * @param actions 右侧操作区
 * @param centeredTitle 标题是否居中。编辑类页面按项目规范要求居中（左「返回」右「保存」）
 */
@Composable
fun WinkGlassTopBar(
    title: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    centeredTitle: Boolean = false
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 阴影负责把玻璃从内容上「抬起来」；亮色主题下玻璃与背景都很浅，仅靠高光描边无法形成边缘定义
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shadowAlpha = if (isDark) 0.28f else 0.16f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = WinkGlassShapes.TopBar,
                clip = false,
                ambientColor = Color.Black.copy(alpha = shadowAlpha),
                spotColor = Color.Black.copy(alpha = shadowAlpha + 0.04f)
            )
            .winkGlassSurface(
                state = hazeState,
                shape = WinkGlassShapes.TopBar,
                blurRadius = WinkGlassDefaults.TopBarBlur
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop)
                .height(WinkGlassTopBarDefaults.Height)
                .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centeredTitle) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (navigationIcon != null) 4.dp else 0.dp)
            )
            actions()
        }
    }
}

/** 顶栏尺寸 */
object WinkGlassTopBarDefaults {
    /** 不含状态栏的顶栏内容高度 */
    val Height = 64.dp

    /** 含状态栏的完整高度，供内容层计算顶部留白 */
    @Composable
    fun totalHeight(): Dp = Height + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
}

/** 常用玻璃形状 */
object WinkGlassShapes {
    /** 悬浮导航栏：胶囊感的大圆角 */
    val NavigationBar = RoundedCornerShape(30.dp)

    /** 顶栏：只保留下方圆角 */
    val TopBar = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    /** 无圆角，用于贴边的顶栏 */
    val TopBarFlat = RoundedCornerShape(0.dp)
}
