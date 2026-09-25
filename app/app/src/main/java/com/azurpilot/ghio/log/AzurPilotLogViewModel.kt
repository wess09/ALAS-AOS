package com.azurpilot.ghio.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azurpilot.ghio.AppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AzurPilotErrorDirInfo(
    /** 目录名（毫秒时间戳），路由参数 */
    val name: String,
    val timestamp: Long,
    val fileCount: Int,
)

data class AzurPilotDailyLogInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

data class AzurPilotLogUiState(
    val errorDirs: List<AzurPilotErrorDirInfo> = emptyList(),
    val dailyLogs: List<AzurPilotDailyLogInfo> = emptyList(),
    val loading: Boolean = true,
)

/** AzurPilot 日志列表（`rootfs/opt/run/log`）：错误现场 + 按天日志两区；正文归 [LogTailViewModel] */
class AzurPilotLogViewModel(
    private val source: AzurPilotLogSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AzurPilotLogUiState())
    val uiState: StateFlow<AzurPilotLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val state = withContext(AppDispatchers.IO) {
            AzurPilotLogUiState(
                errorDirs = source.errorDirs().map { dir ->
                    AzurPilotErrorDirInfo(
                        name = dir.name,
                        timestamp = dir.name.toLongOrNull() ?: 0L,
                        fileCount = dir.listFiles()?.count { it.isFile } ?: 0,
                    )
                },
                dailyLogs = source.dailyLogs().map {
                    AzurPilotDailyLogInfo(
                        name = it.name,
                        sizeBytes = it.length(),
                        lastModified = it.lastModified(),
                    )
                },
                loading = false,
            )
        }
        _uiState.value = state
    }
}
