package com.azurpilot.ghio.ui.logs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.R
import com.azurpilot.ghio.log.AzurPilotErrorDetailViewModel
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AppCardSurface
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * 一个 AzurPilot 错误现场的详情（二级页面）：log.txt 用共用的尾部加载查看器，
 * 下方一排 PNG 截图缩略图，点开全屏（fit-center，不做捏合缩放）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzurPilotErrorDetailScreen(
    dirName: String,
    onBack: () -> Unit,
    viewModel: AzurPilotErrorDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(dirName) { viewModel.load(dirName) }
    var fullscreenImage by remember { mutableStateOf<File?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(logTimestamp(dirName.toLongOrNull() ?: 0L)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> AzurPilotErrorMessage(R.string.common_loading, Modifier.weight(1f))
                state.missing -> AzurPilotErrorMessage(R.string.log_tail_missing, Modifier.weight(1f))
                state.logTxt == null && state.images.isEmpty() ->
                    AzurPilotErrorMessage(R.string.log_tail_empty, Modifier.weight(1f))

                else -> {
                    if (state.logTxt != null) {
                        LogTailContent(file = state.logTxt!!, modifier = Modifier.weight(1f))
                    } else {
                        // 现场只剩截图：文案占位，图在下面
                        AzurPilotErrorMessage(R.string.log_tail_missing, Modifier.weight(1f))
                    }
                    if (state.images.isNotEmpty()) {
                        HorizontalDivider()
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(AppTokens.Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                        ) {
                            items(state.images, key = { it.name }) { image ->
                                AzurPilotErrorThumbnail(
                                    file = image,
                                    onClick = { fullscreenImage = image },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fullscreenImage?.let { image ->
        AzurPilotErrorFullscreen(image = image, onDismiss = { fullscreenImage = null })
    }
}

@Composable
private fun AzurPilotErrorMessage(textRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(AppTokens.Spacing.lg),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AzurPilotErrorThumbnail(file: File, onClick: () -> Unit) {
    val bitmap by rememberDecodedBitmap(file, maxDimension = THUMB_SIZE_PX)
    AppCardSurface(
        modifier = Modifier
            .size(THUMB_SIZE_DP)
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AzurPilotErrorFullscreen(image: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val bitmap by rememberDecodedBitmap(image, maxDimension = FULLSCREEN_MAX_PX)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = image.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 按目标尺寸采样解码；原图直接进内存是 1080p PNG 的数倍，缩略图必须 inSampleSize */
@Composable
private fun rememberDecodedBitmap(file: File, maxDimension: Int) = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = file,
    key2 = maxDimension,
) {
    value = withContext(AppDispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimension &&
                bounds.outHeight / (sample * 2) >= maxDimension
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }.getOrNull()
    }
}

private val THUMB_SIZE_DP = 96.dp
private const val THUMB_SIZE_PX = 192
private const val FULLSCREEN_MAX_PX = 1920
