package com.azurpilot.ghio.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.constant.AppPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppLogFileInfo(
    /** 相对 `log/` 目录的路径（app.log、proot/session.log、crash/xxx.txt） */
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

data class AppLogUiState(
    val files: List<AppLogFileInfo> = emptyList(),
    val loading: Boolean = true,
)

sealed interface AppLogIntent {
    data object ClearAll : AppLogIntent
}

/**
 * 启动器日志的文件列表（整个 `log/` 目录递归）；正文归 [LogTailViewModel]
 *
 * 「全部清除」的范围维持 app.log 滚动系列（`AppLogWriter.purge()`），不扩大到
 * session.log 与 crash——那两份分别是会话时间线与崩溃现场，清了等于自断排查后路
 */
class AppLogViewModel(
    private val writer: AppLogWriter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLogUiState())
    val uiState: StateFlow<AppLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun onIntent(intent: AppLogIntent) {
        when (intent) {
            AppLogIntent.ClearAll -> viewModelScope.launch {
                // 必须 join：删除排在写入通道上，不等它刷出来的还是旧的那几份
                writer.purge().join()
                reload()
            }
        }
    }

    private suspend fun reload() {
        val root = AppPaths.LOG_DIR
        val files = withContext(AppDispatchers.IO) {
            LauncherLogScanner.scan(root).map {
                AppLogFileInfo(
                    name = it.relativeTo(root).invariantSeparatorsPath,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified(),
                )
            }
        }
        _uiState.value = AppLogUiState(files = files, loading = false)
    }
}
