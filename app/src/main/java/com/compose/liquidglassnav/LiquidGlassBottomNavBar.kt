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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap.Companion.Square
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    showBorder: Boolean = true
) {
    var currentSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    val itemPositions = remember { mutableStateMapOf<Int, Float>() }
    val itemWidths = remember { mutableStateMapOf<Int, Float>() }

    val targetPosition = remember(currentSelectedIndex) {
        itemPositions[currentSelectedIndex] ?: 0f
    }

    val animatedOffset by animateFloatAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicator_offset"
    )

    val blobScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "blob_scale"
    )

    LaunchedEffect(selectedIndex) {
        if (currentSelectedIndex != selectedIndex) {
            currentSelectedIndex = selectedIndex
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + 24.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.5f),
                            backgroundColor.copy(alpha = 0.35f),
                            backgroundColor.copy(alpha = 0.2f)
                        )
                    )
                )
        ) {
            if (showBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    borderColor.copy(alpha = 0.09f),
                                    Color.Transparent,
                                    borderColor.copy(alpha = 0.09f)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            if (animatedOffset > 0f || currentSelectedIndex == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = with(density) { animatedOffset.toDp() })
                        .size(56.dp * blobScale)
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
            }

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
                            activeColor = activeColor,
                            onPositioned = { x, width ->
                                itemPositions[index] = x + (width / 2) - 28.dp.value * density.density
                                itemWidths[index] = width
                            }
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
    activeColor: Color,
    onPositioned: (x: Float, width: Float) -> Unit = { _, _ -> }
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

    // Vertical offset for bounce effect
    val offsetY by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offset_y"
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
        modifier = Modifier
            .size(60.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned(
                    coordinates.positionInParent().x,
                    coordinates.size.width.toFloat()
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .offset(y = offsetY.dp)
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
