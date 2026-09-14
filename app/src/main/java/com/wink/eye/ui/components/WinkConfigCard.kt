package com.wink.eye.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wink.eye.ui.theme.WinkLayoutOverlay

/**
 * 配置项列表的统一尺寸规格。
 *
 * Wink 页与 EarClock 页的列表项共用同一份规格，任何一处调整都会同时作用于两页，
 * 避免再次出现「两页卡片高度不一致、切换时列表跳动」的问题。
 */
object WinkListSpec {

    /** 内容层使用克制的分组表面，留白来自排版而不是厚重阴影。 */
    val CardContentPadding = 18.dp

    /** 主标题与主值大字之间的间距 */
    val TitleToMainValueGap = 4.dp

    /** 主值大字行最小高度（titleLarge 行高 28sp），两页取同一值保证卡片等高 */
    val MainValueRowMinHeight = 28.dp

    /** 主值大字与底部信息行之间的间距 */
    val MainValueToMetaGap = 2.dp

    /**
     * 副信息行的最小行高。两页徽标与描述使用同一套字体规格且均限制单行，
     * 这里再兜一个最小高度，保证卡片高度完全一致（用最小高度而非固定高度，字体放大时不会被裁切）。
     */
    val MetaRowMinHeight = 20.dp

    /** 徽标与描述文字之间的横向间距 */
    val MetaGap = 8.dp

    /** 删除图标尺寸 */
    val DeleteIconSize = 20.dp

    /** 列表左右内边距 */
    val ListHorizontalPadding = 12.dp

    /** 列表项之间的纵向间距 */
    val ItemSpacing = 8.dp

    /** 列表顶部 / 底部额外留白 */
    val ListEdgeSpacer = 8.dp
}

/**
 * 配置项卡片统一骨架，固定三行结构：
 *
 * 1. 主标题（titleMedium 加粗）
 * 2. 主值大字（titleLarge 加粗，如闹钟时间、规则时长）
 * 3. 徽标 + 描述（labelLarge 主色 + bodySmall 次要色，二者同行）
 *
 * 三行高度全部由 [WinkListSpec] 决定，与文案长度无关（标题 / 主值 / 描述均单行省略），
 * 因此 Wink 页与 EarClock 页的列表项高度天然一致。
 *
 * @param title 主标题，单行省略
 * @param mainValue 主值大字，卡片中最醒目的信息
 * @param badge 底部信息行左侧的强调徽标，用主色加粗展示
 * @param subtitle 底部信息行右侧的描述文字，传空串时只显示徽标
 * @param enabled 是否启用，决定边框与底色
 * @param onToggle 开关切换回调
 * @param onDelete 删除按钮回调
 * @param deleteContentDescription 删除按钮的无障碍描述
 * @param onClick 卡片点击回调
 */
@Composable
fun WinkConfigCard(
    title: String,
    mainValue: String,
    badge: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    deleteContentDescription: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(180),
        label = "config_card_surface"
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WinkListSpec.CardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 第一行：主标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(WinkListSpec.TitleToMainValueGap))
                // 第二行：主值大字
                Row(
                    modifier = Modifier.defaultMinSize(minHeight = WinkListSpec.MainValueRowMinHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mainValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(WinkListSpec.MainValueToMetaGap))
                // 第三行：徽标 + 描述
                Row(
                    modifier = Modifier.defaultMinSize(minHeight = WinkListSpec.MetaRowMinHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.width(WinkListSpec.MetaGap))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = deleteContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(WinkListSpec.DeleteIconSize)
                )
            }
        }
    }
}

/**
 * 配置项列表统一容器：统一左右内边距、项间距、首尾留白与底部悬浮菜单栏遮挡高度。
 *
 * @param list 数据源
 * @param key 列表项唯一键
 * @param modifier 尺寸修饰符，由调用方决定列表占位方式
 * @param itemContent 单项内容
 */
@Composable
fun <T> WinkConfigList(
    list: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    /**
     * 顶部额外留白。列表顶部若被悬浮玻璃顶栏覆盖，应传入顶栏完整高度，
     * 让首项不被遮挡，同时滚动过程中内容可以从玻璃下方穿过。
     */
    extraTopPadding: Dp = 0.dp,
    itemContent: @Composable (T) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = WinkListSpec.ListHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(WinkListSpec.ItemSpacing),
        contentPadding = PaddingValues(
            top = extraTopPadding,
            bottom = WinkLayoutOverlay.BottomBarOverlayHeight
        )
    ) {
        item { Spacer(Modifier.height(WinkListSpec.ListEdgeSpacer)) }
        items(list, key = key) { element ->
            Box(Modifier.animateItem()) { itemContent(element) }
        }
        item { Spacer(Modifier.height(WinkListSpec.ListEdgeSpacer)) }
    }
}

/**
 * 空状态统一组件，保证两页无数据时的排版完全一致。
 *
 * @param title 主提示文案
 * @param subtitle 副提示文案
 * @param modifier 尺寸修饰符
 */
@Composable
fun WinkEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
