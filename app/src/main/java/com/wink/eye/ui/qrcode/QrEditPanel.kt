package com.wink.eye.ui.qrcode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wink.eye.R

/** 编辑面板顶部圆角，与主题里 large 容器圆角保持一致 */
private val PanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** 工具图标按钮边长。取 44dp 是在「一行塞下 5 个」与「尽量靠近 48dp 可达性建议」之间取的折中 */
private val IconActionSize = 44.dp

/** 工具图标尺寸 */
private val IconGlyphSize = 20.dp

/** 底部主行动按钮高度 */
private val ActionButtonHeight = 44.dp

/**
 * 二维码页底部编辑面板：内容文本框 + 一行工具按钮 + 一行主操作。
 *
 * 面板表面色一直延伸到屏幕底边，让悬浮的液态玻璃底部导航栏叠在它之上、
 * 由面板提供被模糊的内容，从而获得真实的液态玻璃观感。
 *
 * @param bottomReservedHeight 底部为悬浮导航栏预留的高度。键盘弹出时导航栏被遮挡，
 *   调用方应传 0 避免白留一大块空白。
 */
@Composable
fun QrEditPanel(
    text: String,
    copied: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    bottomReservedHeight: Dp,
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPickFromGallery: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
        ) {
            // 工具行：左侧标签 + 右侧 5 个紧凑操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IconActionSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.qr_editor_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(
                    visible = copied,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = stringResource(R.string.qr_copied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                QrIconAction(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.qr_backspace),
                    enabled = hasText,
                    onClick = onBackspace
                )
                QrIconAction(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.qr_clear),
                    enabled = hasText,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onClear
                )
                QrIconAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.qr_copy),
                    enabled = hasText,
                    onClick = onCopy
                )
                QrIconAction(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.qr_undo),
                    enabled = canUndo,
                    onClick = onUndo
                )
                QrIconAction(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.qr_redo),
                    enabled = canRedo,
                    onClick = onRedo
                )
            }

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.qr_editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 3
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier
                        .weight(1f)
                        .height(ActionButtonHeight),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.qr_pick_from_gallery),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onGenerate,
                    enabled = hasText,
                    modifier = Modifier
                        .weight(1f)
                        .height(ActionButtonHeight),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.qr_generate),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // 为悬浮玻璃导航栏让位；面板表面色继续向下延伸到屏幕底边
            Spacer(Modifier.height(bottomReservedHeight))
        }
    }
}

/**
 * 面板内的紧凑工具按钮。
 *
 * 不能用 `Surface(onClick = ...)`：那一重载会把最小点击尺寸强制成 48dp，
 * 五个并排会超出面板宽度，导致最右侧按钮被裁切。
 */
@Composable
private fun QrIconAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val baseColor = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(IconActionSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) baseColor else baseColor.copy(alpha = 0.35f),
            modifier = Modifier.size(IconGlyphSize)
        )
    }
}
