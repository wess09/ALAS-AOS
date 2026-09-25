package com.aliothmoon.azurpilot.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.azurpilot.AppDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class LogTailUiState(
    val lines: List<String> = emptyList(),
    /** false = 已经翻到文件头，「加载更早」不再出现 */
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingEarlier: Boolean = false,
    /** 文件不存在或读失败（多半刚被清理掉） */
    val missing: Boolean = false,
)

/**
 * 尾部加载查看器的会话：打开读最后一块，「加载更早」往前续
 *
 * 启动器日志与 AzurPilot 日志 txt 共用；按绝对路径加载，路径解析归调用方
 */
class LogTailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogTailUiState())
    val uiState: StateFlow<LogTailUiState> = _uiState.asStateFlow()

    private var path: String? = null
    private var fromOffset: Long = 0
    private var earlierJob: Job? = null

    /** 幂等：重组重放同一个路径不重复读 */
    fun load(filePath: String) {
        if (path == filePath) return
        path = filePath
        viewModelScope.launch {
            val chunk = withContext(AppDispatchers.IO) {
                val file = File(filePath)
                if (!file.isFile) return@withContext null
                runCatching { LogTailReader.readTail(file) }
                    .onFailure { Timber.w(it, "读日志失败：%s", filePath) }
                    .getOrNull()
            }
            _uiState.value = if (chunk == null) {
                LogTailUiState(loading = false, missing = true)
            } else {
                fromOffset = chunk.fromOffset
                LogTailUiState(
                    lines = chunk.text.toLines(),
                    hasMore = chunk.hasMore,
                    loading = false,
                )
            }
        }
    }

    fun loadEarlier() {
        val filePath = path ?: return
        if (!_uiState.value.hasMore || earlierJob?.isActive == true) return
        earlierJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingEarlier = true) }
            val chunk = withContext(AppDispatchers.IO) {
                runCatching { LogTailReader.readChunk(File(filePath), fromOffset) }
                    .onFailure { Timber.w(it, "往前读日志失败：%s", filePath) }
                    .getOrNull()
            }
            if (chunk == null) {
                _uiState.update { it.copy(loadingEarlier = false) }
            } else {
                fromOffset = chunk.fromOffset
                val earlier = chunk.text.toLines()
                _uiState.update {
                    it.copy(
                        lines = earlier + it.lines,
                        hasMore = chunk.hasMore,
                        loadingEarlier = false,
                    )
                }
            }
        }
    }

    /** 尾部空行只是收尾换行符，不是内容 */
    private fun String.toLines(): List<String> = lines().dropLastWhile { it.isEmpty() }
}
