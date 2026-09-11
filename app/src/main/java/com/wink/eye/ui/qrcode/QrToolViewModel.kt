package com.wink.eye.ui.qrcode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wink.eye.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 二维码页 UI 状态。
 *
 * 沿用源项目的「文本快照栈」方案：扫码追加与手动编辑共用一套 undo/redo。
 */
data class QrToolUiState(
    /** 当前文本框内容，同时作为生成二维码的输入 */
    val text: String = "",
    /** 是否可撤销 */
    val canUndo: Boolean = false,
    /** 是否可恢复 */
    val canRedo: Boolean = false,
    /** 用户期望的摄像头开关状态 */
    val cameraEnabled: Boolean = true,
    /** 是否已获得相机权限 */
    val hasCameraPermission: Boolean = false,
    /** 扫码成功动效是否进行中 */
    val scanSuccess: Boolean = false,
    /** 复制提示是否显示中 */
    val copied: Boolean = false,
    /** 生成二维码叠加层是否显示 */
    val showGeneratedQr: Boolean = false,
    /** 已生成的二维码位图 */
    val generatedQr: Bitmap? = null,
    /** 生成该二维码时使用的文本快照，保存到相册时按它重渲染，避免与后续编辑串味 */
    val generatedQrText: String? = null,
    /** 一次性提示文案，界面消费后清空 */
    @StringRes val messageRes: Int? = null,
    /**
     * 提示序号，每发出一条提示自增。
     *
     * 界面按它触发 Toast：若只监听 [messageRes]，连续两次相同提示（例如连续两次保存失败）
     * 值没有变化，副作用不会重新执行，用户就看不到第二条提示。
     */
    val messageId: Long = 0L
)

/**
 * 二维码页 ViewModel。
 *
 * 文本内容存放在 ViewModel 里而不是 `remember`：本页在 NavHost 中是一个目的地，
 * 切到别的 Tab 会离开组合，`remember` 状态会丢失，而 ViewModel 挂在 Activity 作用域上可以保留。
 */
class QrToolViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QrToolUiState())
    val uiState: StateFlow<QrToolUiState> = _uiState.asStateFlow()

    /** 历史快照栈 */
    private val undoStack = ArrayDeque<String>()

    /** 恢复快照栈 */
    private val redoStack = ArrayDeque<String>()

    /** 上次入栈的稳定文本，用于 debounce 判定 */
    private var lastCommittedText: String = ""

    /** 手动编辑 debounce 定时器 */
    private var debounceJob: Job? = null

    /** 扫码成功动效定时器 */
    private var scanAnimationJob: Job? = null

    /** 复制提示定时器 */
    private var copiedJob: Job? = null

    /** 生成二维码的异步任务 */
    private var generateJob: Job? = null

    /** 动效进行中暂停扫码，防止同一次扫码被重复追加 */
    private var scanPaused: Boolean = false

    // region 扫码

    /**
     * 取景扫码成功回调：当前文本入栈 → 清空 redo → 追加扫码文本 → 触发动效。
     *
     * 两个守卫只作用于**取景扫码**：动效进行中防止同一帧被重复追加，
     * 生成叠加层显示期间取景框本就停用，不应再接受扫码结果。
     * 相册识别走 [appendScannedText]，不受这两个守卫影响。
     */
    fun onScanSuccess(scannedText: String) {
        if (scanPaused) return
        if (_uiState.value.showGeneratedQr) return
        appendScannedText(scannedText)
        triggerScanAnimation()
    }

    /** 把一段文本追加到现有内容之后，并把它并入撤销栈 */
    private fun appendScannedText(scannedText: String) {
        val current = _uiState.value.text
        pushUndo(current)
        redoStack.clear()
        lastCommittedText = current + scannedText
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = current + scannedText,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
    }

    /** 触发扫码成功动效，期间暂停分析 */
    private fun triggerScanAnimation() {
        scanAnimationJob?.cancel()
        scanPaused = true
        _uiState.update { it.copy(scanSuccess = true) }
        scanAnimationJob = viewModelScope.launch {
            delay(AnimationDurationMs)
            scanPaused = false
            _uiState.update { it.copy(scanSuccess = false) }
        }
    }

    // endregion

    // region 文本输入

    /**
     * 手动编辑回调，维护 500ms debounce。
     *
     * 「可撤销」在这里就点亮，而不是等 debounce 把快照真正入栈。
     * 否则连续输入期间按钮要等 500ms 才可用，用户会以为撤销坏了；
     * 快照入栈仍由 debounce 合并，撤销粒度不受影响。
     */
    fun onTextChanged(newText: String) {
        _uiState.update {
            it.copy(
                text = newText,
                canUndo = newText != lastCommittedText || undoStack.isNotEmpty()
            )
        }
        scheduleDebounce(newText)
    }

    private fun scheduleDebounce(newText: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DebounceDurationMs)
            if (newText != lastCommittedText) {
                pushUndo(lastCommittedText)
                redoStack.clear()
                lastCommittedText = newText
                _uiState.update {
                    it.copy(
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = false
                    )
                }
            }
        }
    }

    private fun cancelDebounce() {
        debounceJob?.cancel()
        debounceJob = null
    }

    /** 清空整个文本框（清空前的内容进入撤销栈） */
    fun onClear() {
        val current = _uiState.value.text
        if (current.isEmpty()) return
        pushUndo(current)
        redoStack.clear()
        lastCommittedText = ""
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = "",
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
    }

    /** 退格：删除末尾一个字符，走 debounce 逻辑 */
    fun onBackspace() {
        val current = _uiState.value.text
        if (current.isEmpty()) return
        onTextChanged(current.dropLast(1))
    }

    /** 复制到剪贴板并短暂提示 */
    fun onCopy(context: Context) {
        val text = _uiState.value.text
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(ClipboardLabel, text))
        _uiState.update { it.copy(copied = true) }
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            delay(CopyHintDurationMs)
            _uiState.update { it.copy(copied = false) }
        }
    }

    // endregion

    // region 撤销 / 恢复

    /** 撤销：当前文本压入 redo 栈，从 undo 栈弹出上一个快照 */
    fun onUndo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.text)
        val previous = undoStack.removeLast()
        lastCommittedText = previous
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    /** 恢复：当前文本压入 undo 栈，从 redo 栈弹出下一个快照 */
    fun onRedo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.text)
        val next = redoStack.removeLast()
        lastCommittedText = next
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // endregion

    // region 生成二维码

    /**
     * 生成二维码并在取景框中展示。
     *
     * 按钮只承担「生成 / 刷新」职责：叠加层已显示时再次点击按当前文本重新渲染，
     * 不做开关切换。关闭叠加层只走 [onHideQr]（取景框上的关闭按钮），
     * 两个职责分开后「想换个内容重生成」和「想关掉」不会互相打架。
     */
    fun onGenerateQr() {
        val text = _uiState.value.text
        if (text.isEmpty()) {
            emitMessage(R.string.qr_empty_text)
            return
        }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            when (val result = QrCodeRenderer.render(text)) {
                is QrRenderResult.Success -> _uiState.update {
                    it.copy(
                        showGeneratedQr = true,
                        generatedQr = result.bitmap,
                        generatedQrText = text
                    )
                }

                QrRenderResult.Empty -> emitMessage(R.string.qr_empty_text)
                QrRenderResult.TooLong -> emitMessage(R.string.qr_too_long)
            }
        }
    }

    /** 关闭二维码叠加层，取景框随之恢复 */
    fun onHideQr() {
        _uiState.update {
            it.copy(
                showGeneratedQr = false,
                generatedQr = null,
                generatedQrText = null
            )
        }
    }

    /**
     * 把当前二维码按导出尺寸重新渲染后保存到相册。
     *
     * 用 [QrToolUiState.generatedQrText] 而不是当前文本：用户可能在生成之后又改了内容，
     * 那时「看到的二维码」和「存下来的二维码」就会不一致。
     */
    fun onSaveQrToGallery(context: Context) {
        val text = _uiState.value.generatedQrText ?: return
        viewModelScope.launch {
            when (val result = QrCodeRenderer.render(text, QrExportSize)) {
                is QrRenderResult.Success -> {
                    val uri = QrGallerySaver.save(context, result.bitmap)
                    emitMessage(if (uri != null) R.string.qr_saved else R.string.qr_save_failed)
                }

                QrRenderResult.Empty -> emitMessage(R.string.qr_empty_text)
                QrRenderResult.TooLong -> emitMessage(R.string.qr_too_long)
            }
        }
    }

    // endregion

    // region 从相册识别

    /**
     * 解码相册图片中的二维码并追加到文本框。
     *
     * 读取失败与「图中无码」要给出不同提示，否则用户无法判断是图片问题还是识别问题。
     * 开始前先收起生成叠加层：它显示时编辑面板仍然可点，若不收起，
     * 用户会觉得「选了图片却没反应」。
     */
    fun onDecodeFromGallery(context: Context, uri: Uri) {
        if (_uiState.value.showGeneratedQr) onHideQr()
        viewModelScope.launch {
            QrImageDecoder.decode(context, uri)
                .onSuccess { text ->
                    if (text.isNullOrEmpty()) {
                        emitMessage(R.string.qr_not_found_in_image)
                    } else {
                        appendScannedText(text)
                        triggerScanAnimation()
                    }
                }
                .onFailure { emitMessage(R.string.qr_gallery_failed) }
        }
    }

    // endregion

    // region 相机

    /** 切换摄像头开关 */
    fun onToggleCamera() {
        _uiState.update { it.copy(cameraEnabled = !it.cameraEnabled) }
    }

    /** 更新相机权限状态；授权后自动开启摄像头 */
    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = granted,
                cameraEnabled = granted
            )
        }
    }

    // endregion

    /** 消费一次性提示 */
    fun consumeMessage() {
        _uiState.update { it.copy(messageRes = null) }
    }

    private fun emitMessage(@StringRes resId: Int) {
        // 序号自增，保证「连续两条相同提示」也能各自触发一次界面副作用
        _uiState.update { it.copy(messageRes = resId, messageId = ++messageSeq) }
    }

    private fun pushUndo(snapshot: String) {
        undoStack.addLast(snapshot)
    }

    /** 一次性提示的序号计数器 */
    private var messageSeq: Long = 0L

    private companion object {
        const val DebounceDurationMs = 500L
        const val AnimationDurationMs = 800L
        const val CopyHintDurationMs = 1500L
        const val ClipboardLabel = "QRCode"
    }
}
