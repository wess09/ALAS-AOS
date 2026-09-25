package com.aliothmoon.azurpilot.ui.hangar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.constant.DefaultDisplayConfig
import com.aliothmoon.azurpilot.theme.AppTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全屏预览 + 触摸转发链（移植自 m0 TasksPreviewSection，分辨率从会话态简化为
 * DefaultDisplayConfig 常量——虚拟屏 1280×720 在 AzurPilot 桥配置里钉死）
 *
 * 三段链：
 * - [rememberMovablePreview]：预览面做成 movableContent，在内嵌卡片与全屏宿主之间搬家
 * - [FullscreenPreview]：全屏宿主（挂 AppRoot 顶层），隐藏系统栏、强制横屏、黑底 contain、
 *   右上角 X、BackHandler 退出、触摸 Down/Move/Up 换算后注入虚拟屏
 * - 内嵌卡片（HangarScreen.VdPreview）：单击进全屏，内嵌画面本身不转发触摸（防误触）
 */

/** 全屏预览上的手动操作；坐标由 UI 换算到虚拟屏坐标系后随动作一起上报 */
enum class PreviewTouchAction { Down, Move, Up }

/**
 * `setFixedSize` 必须延后一拍（m0 PreviewSurface 同款处理）：
 * surfaceCreated 里同步调会被 attach 流程吞掉，画面按错误比例贴上来
 */
private const val FIXED_SIZE_DELAY_MS = 50L

/**
 * 预览面做成 movableContent：在内嵌卡片与全屏宿主之间搬家时复用同一份组合状态
 *
 * 「已挂上的 Surface」与「画面就绪」放在 movableContent **外层**：
 * SurfaceView 搬家时必然走一轮 detach/attach，surfaceDestroyed 与 surfaceCreated 成对触发，
 * 判重状态若放在里面会跟着一起搬，拿不准新旧 Surface 的对应关系（m0 同款布局）
 *
 * 闭包里读到的值必须走 rememberUpdatedState：movableContentOf 只创建一次，直接捕获会永远停在首帧
 *
 * [active] 是挂机 tab 可见性：tab 切走时 SurfaceView 多半还活着（pager 预组合），
 * surfaceChanged 不会再发，靠外层的 DisposableEffect 补一次摘/挂
 */
@Composable
fun rememberMovablePreview(
    active: Boolean,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
): @Composable () -> Unit {
    val currentActive by rememberUpdatedState(active)
    val currentAvailable by rememberUpdatedState(onSurfaceAvailable)
    val currentDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    var attachedSurface by remember { mutableStateOf<Surface?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(active) {
        if (active) {
            attachedSurface?.let { currentAvailable(it) }
        } else {
            currentDestroyed()
        }
        onDispose { }
    }

    return remember {
        movableContentOf {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                            attachedSurface = holder.surface
                                            surfaceReady = true
                                            if (currentActive) {
                                                currentAvailable(holder.surface)
                                            }
                                        }
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        attachedSurface = null
                                        surfaceReady = false
                                        currentDestroyed()
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
                                text = stringResource(R.string.hangar_preview_waiting),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 全屏宿主；与内嵌卡片共用同一份 movableContent
 *
 * 必须挂在 AppRoot 顶层而不是 pager 里，否则盖不住底部 tab 栏。
 * 进出时接管系统栏与屏幕方向，退出时一律还原成进来前的值（m0 同款行为）
 */
@Composable
fun FullscreenPreview(
    onExit: () -> Unit,
    onTouch: (x: Int, y: Int, action: PreviewTouchAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity) {
        val controller = activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // 虚拟屏是横的，竖着看只有中间一条；退出时还原用户原本的方向设置
    DisposableEffect(activity) {
        val original = activity?.requestedOrientation
        if (activity?.resources?.configuration?.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { if (original != null) activity.requestedOrientation = original }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .previewTouchInput(onTouch),
        contentAlignment = Alignment.Center,
    ) {
        content()
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(AppTokens.Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.hangar_fullscreen_exit),
                tint = Color.White,
                modifier = Modifier.size(AppTokens.IconSize.md),
            )
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
