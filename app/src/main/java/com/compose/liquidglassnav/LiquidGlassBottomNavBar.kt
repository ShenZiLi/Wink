package com.compose.liquidglassnav

import androidx.compose.material3.Icon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wink.eye.ui.components.WinkGlassDefaults
import com.wink.eye.ui.components.winkGlassSurface
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/** 单个导航项的占位尺寸，必须与 [NavBarItem] 内部使用的尺寸一致 */
private val NavBarItemSize = 60.dp

/** 选中高亮指示器尺寸 */
private val IndicatorSize = 56.dp

/**
 * 悬浮液态玻璃底部导航栏。
 *
 * 高亮指示器的位置按 [Arrangement.SpaceEvenly] 的排布规则**直接计算**得到，
 * 不依赖 `onGloballyPositioned` 测量结果。
 *
 * 这样做的原因：导航栏在进入二级页面时会从组合树中移除，返回时重建，
 * 若位置依赖测量就会出现「首帧尚无测量结果 → 指示器落到错误位置且不再修正」的问题
 * （表现为从二级页返回后高亮块跑到图标左边）。
 */
@Composable
fun LiquidGlassBottomNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1E2E),
    selectedColor: Color = Color.White,
    unselectedColor: Color = Color.White.copy(alpha = 0.6f),
    activeColor: Color = Color(0xFF00D9FF),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    barHeight: Dp = 70.dp,
    cornerRadius: Dp = 30.dp,
    showBorder: Boolean = true,
    /**
     * 与内容层共享的背景采样源。传入后导航栏使用真实的背景模糊（液态玻璃）；
     * 为 null 时退回静态半透明渐变，保证组件可独立使用。
     */
    hazeState: HazeState? = null
) {
    var currentSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        if (currentSelectedIndex != selectedIndex) {
            currentSelectedIndex = selectedIndex
        }
    }

    val glassShape = RoundedCornerShape(cornerRadius)

    // 玻璃材质：优先用真实背景模糊；无采样源时退回静态渐变
    val glassModifier = if (hazeState != null) {
        Modifier.winkGlassSurface(
            state = hazeState,
            shape = glassShape,
            blurRadius = WinkGlassDefaults.NavigationBarBlur,
            drawHighlight = showBorder
        )
    } else {
        Modifier
            .clip(glassShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.5f),
                        backgroundColor.copy(alpha = 0.35f),
                        backgroundColor.copy(alpha = 0.2f)
                    )
                )
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + 20.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // 浮起的玻璃需要阴影与内容分离，阴影随主题自适应
                .shadow(
                    elevation = 5.dp,
                    shape = glassShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.16f)
                )
                .then(glassModifier)
        ) {
            val densityValue = LocalDensity.current.density

            // SpaceEvenly：n 个等宽项 + (n+1) 个等宽间隙，间隙宽度由玻璃条可用宽度决定
            val itemCount = items.size
            val gap = if (itemCount > 0) {
                (maxWidth - NavBarItemSize * itemCount) / (itemCount + 1)
            } else {
                0.dp
            }

            // 指示器相对玻璃条左边缘的目标位置（居中于选中项）
            val indicatorTargetX = gap * (currentSelectedIndex + 1) +
                    NavBarItemSize * currentSelectedIndex +
                    (NavBarItemSize - IndicatorSize) / 2f

            // 首帧即为目标值，因此不存在「先出现在 0 再滑过去」的闪烁；
            // 之后仅在切换选中项时做平滑滑动
            val animatedOffset by animateFloatAsState(
                targetValue = indicatorTargetX.value * densityValue,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "indicator_offset"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // lambda 版 offset：动画只触发重新布局，不触发重组
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .size(IndicatorSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.1f),
                                Color.Transparent,
                                activeColor.copy(alpha = 0.1f),
                            ),
                            radius = 8f
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    key(index) {
                        NavBarItem(
                            item = item,
                            isSelected = index == currentSelectedIndex,
                            onClick = {
                                if (currentSelectedIndex != index) {
                                    currentSelectedIndex = index
                                    onItemSelected(index)
                                }
                            },
                            selectedColor = selectedColor,
                            unselectedColor = unselectedColor,
                            activeColor = activeColor
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color,
    unselectedColor: Color,
    activeColor: Color
) {
    // Enhanced scale animation
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(250),
        label = "color"
    )

    // Alpha animation
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.65f,
        animationSpec = tween(250),
        label = "alpha"
    )

    // Remember badge text to avoid recalculation
    val badgeText = remember(item.badge) {
        item.badge?.let { count ->
            if (count > 99) "99+" else count.toString()
        }
    }

    Box(
        modifier = Modifier.size(NavBarItemSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(NavBarItemSize)
                .scale(scale)
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = 0.4f),
                                    activeColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Icon(
                imageVector = if (isSelected && item.activeIcon != null) item.activeIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor.copy(alpha = alpha),
                modifier = Modifier.size(if (isSelected) 30.dp else 27.dp)
            )
        }

        // Badge support - positioned outside the scaled container to prevent overflow
        badgeText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }
    }
}
