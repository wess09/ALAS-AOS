package com.aliothmoon.azurpilot.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.azurpilot.AppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AzurPilotErrorDetailUiState(
    /** 现场正文（可能不存在：AzurPilot 写现场失败时只有截图） */
    val logTxt: File? = null,
    /** 现场截图，按文件名排（同一现场多张时名字自带序号） */
    val images: List<File> = emptyList(),
    val loading: Boolean = true,
    val missing: Boolean = false,
)

/** 一个 AzurPilot 错误现场（`error/<毫秒时间戳>/`）的内容：log.txt + PNG 截图 */
class AzurPilotErrorDetailViewModel(
    private val source: AzurPilotLogSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AzurPilotErrorDetailUiState())
    val uiState: StateFlow<AzurPilotErrorDetailUiState> = _uiState.asStateFlow()

    private var loaded: String? = null

    fun load(dirName: String) {
        if (loaded == dirName) return
        loaded = dirName
        viewModelScope.launch {
            val state = withContext(AppDispatchers.IO) {
                val dir = source.errorDir(dirName)
                    ?: return@withContext AzurPilotErrorDetailUiState(loading = false, missing = true)
                val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
                AzurPilotErrorDetailUiState(
                    logTxt = files.firstOrNull { it.name == "log.txt" },
                    images = files.filter { it.name.endsWith(".png") }.sortedBy { it.name },
                    loading = false,
                )
            }
            _uiState.value = state
        }
    }
}
