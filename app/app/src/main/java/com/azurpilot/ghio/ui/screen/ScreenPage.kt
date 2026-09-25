package com.azurpilot.ghio.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.azurpilot.ghio.R
import com.azurpilot.ghio.constant.DefaultDisplayConfig
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AppTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * `setFixedSize` 必须延后一拍（m0 PreviewSurface 同款处理）：
 * surfaceCreated 里同步调会被 attach 流程吞掉，画面会按错误比例贴上来
 */
private const val FIXED_SIZE_DELAY_MS = 50L

/** 虚拟屏上的手动操作；坐标由 UI 换算到虚拟屏坐标系后随动作一起上报 */
private enum class PreviewTouchAction { Down, Move, Up }

/**
 * 虚拟屏 tab：实时画面 + 直接触摸操作
 *
 * 进页即转横屏（虚拟屏本身是横的，竖着看只有中间一条），退出还原用户原本的方向设置。
 * 画面走 native bridge_preview 通道（AIDL setMonitorSurface，零拷贝）；
 * 触摸按 contain 缩放反算回虚拟屏坐标后注入，落在黑边上直接丢弃
 */
@Composable
fun ScreenPage(
    active: Boolean,
    modifier: Modifier = Modifier,
    hostState: HostState = koinInject(),
) {
    val activity = LocalContext.current.findActivity()

    // 必须挂在 active 上：pager 会预组合相邻页（beyondViewportPageCount = 1），
    // 只看 activity 的话，停在主页/AzurPilot 页时这一页也被组合，屏幕会莫名其妙转横
    DisposableEffect(activity, active) {
        if (active) {
            val original = activity?.requestedOrientation
            if (activity?.resources?.configuration?.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            onDispose { if (original != null) activity.requestedOrientation = original }
        } else {
            onDispose { }
        }
    }

    var surfaceReady by remember { mutableStateOf(false) }
    val currentAttach by rememberUpdatedState(hostState::attachPreviewSurface)
    val currentDetach by rememberUpdatedState(hostState::detachPreviewSurface)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .previewTouchInput { x, y, action ->
                when (action) {
                    PreviewTouchAction.Down -> hostState.touchDown(x, y)
                    PreviewTouchAction.Move -> hostState.touchMove(x, y)
                    PreviewTouchAction.Up -> hostState.touchUp(x, y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 只在可见时才创建 SurfaceView：它是独立图层，不跟随 Compose 的位移与裁剪，
        // pager 把相邻页挪到屏幕外它不认，会以黑底留在可视区、盖住别的页
        if (active) {
            // contain：画面按虚拟屏比例居中缩放，两侧/上下的黑边由外层底色兜住
            Box(
                Modifier.aspectRatio(
                    DefaultDisplayConfig.WIDTH.toFloat() / DefaultDisplayConfig.HEIGHT
                )
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.setFormat(PixelFormat.RGBA_8888)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    scope.launch {
                                        delay(FIXED_SIZE_DELAY_MS)
                                        holder.setFixedSize(
                                            DefaultDisplayConfig.WIDTH,
                                            DefaultDisplayConfig.HEIGHT,
                                        )
                                    }
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int,
                                ) {
                                    // setFixedSize 是异步的：只在尺寸对齐虚拟屏后才上报，
                                    // 提前交出还是布局尺寸的 Surface 会按错误比例贴画面
                                    if (width == DefaultDisplayConfig.WIDTH &&
                                        height == DefaultDisplayConfig.HEIGHT
                                    ) {
                                        surfaceReady = true
                                        currentAttach(holder.surface)
                                    }
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    surfaceReady = false
                                    currentDetach()
                                }
                            })
                        }
                    },
                )
                if (!surfaceReady) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppTokens.IconSize.md),
                            strokeWidth = AppTokens.Border.marker,
                            color = Color.LightGray,
                        )
                        Text(
                            text = stringResource(R.string.screen_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                        )
                    }
                }
            }
            if (surfaceReady) {
                Text(
                    text = stringResource(R.string.screen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = AppTokens.Alpha.disabledContent),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(AppTokens.Spacing.md),
                )
            }
        }
    }
}

/**
 * 把手指位置换算到虚拟屏坐标再上报（m0 previewTouchInput 同款）
 *
 * 画面按 contain 方式居中缩放，两侧/上下可能有黑边，落在黑边上的点直接丢掉——
 * 那里没有对应的虚拟屏像素，硬算会得到越界坐标
 */
private fun Modifier.previewTouchInput(
    onTouch: (x: Int, y: Int, action: PreviewTouchAction) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val action = when (event.type) {
                PointerEventType.Press -> PreviewTouchAction.Down
                PointerEventType.Move -> if (change.pressed) PreviewTouchAction.Move else null
                PointerEventType.Release -> PreviewTouchAction.Up
                else -> null
            }
            if (action != null) {
                val scale = minOf(
                    size.width / DefaultDisplayConfig.WIDTH.toFloat(),
                    size.height / DefaultDisplayConfig.HEIGHT.toFloat(),
                )
                val offsetX = (size.width - DefaultDisplayConfig.WIDTH * scale) / 2f
                val offsetY = (size.height - DefaultDisplayConfig.HEIGHT * scale) / 2f
                val vx = ((change.position.x - offsetX) / scale).toInt()
                val vy = ((change.position.y - offsetY) / scale).toInt()
                if (vx in 0 until DefaultDisplayConfig.WIDTH &&
                    vy in 0 until DefaultDisplayConfig.HEIGHT
                ) {
                    onTouch(vx, vy, action)
                }
            }
            change.consume()
        }
    }
}

/** Compose 的 LocalContext 可能是 ContextWrapper，逐层剥到 Activity */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
