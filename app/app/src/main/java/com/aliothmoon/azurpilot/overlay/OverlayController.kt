package com.aliothmoon.azurpilot.overlay

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aliothmoon.azurpilot.MainActivity
import com.aliothmoon.azurpilot.domain.OverlayControlMode
import com.aliothmoon.azurpilot.overlay.border.BorderOverlayManager
import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.service.AccessibilityHelperService
import com.aliothmoon.azurpilot.service.HostState
import com.aliothmoon.azurpilot.settings.AppSettingsGateway
import com.aliothmoon.azurpilot.theme.AzurPilotTheme
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxDisplayMode
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.IKeyBackListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 外壳控制层：悬浮球/面板是环境状态的开关与仪表盘
 *
 * 观察 [HostState]：环境起来（虚拟屏在且桥通）出控制球/边框，环境撤了收起来。
 * 球点开出面板，面板上「启动环境 = 特权连接 → setup() → startVirtualDisplay()」
 * 与「停止环境 = stopVirtualDisplay()」两个动作直接打 [HostState]
 */
class OverlayController(
    private val context: Application,
    private val hostState: HostState,
    private val appSettings: AppSettingsGateway,
    val borderOverlayManager: BorderOverlayManager,
    private val viewModelOwner: OverlayViewModelOwner,
    private val runController: AzurPilotRunController,
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val isPanelLocked = MutableStateFlow(true)

    private var currentMode: OverlayControlMode = OverlayControlMode.FLOAT_BALL
    private var hostJob: Job? = null
    private var panelLayout: Pair<Int, Int>? = null

    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val next = calculatePanelLayout(newConfig)
            if (next == panelLayout) return
            panelLayout = next
            applyPanelLayout(newConfig, next)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    fun setup() {
        context.registerComponentCallbacks(configCallback)
        install()
        configCallback.onConfigurationChanged(context.resources.configuration)
        observeHost()
        scope.launch {
            appSettings.overlayControlMode.collect { applyMode(it) }
        }
    }

    // ── 环境态 ──

    private fun observeHost() {
        if (hostJob != null) return
        hostJob = scope.launch {
            var wasUp = hostState.snapshot.value.environmentUp
            hostState.snapshot.collect { snapshot ->
                val up = snapshot.environmentUp
                if (up == wasUp) return@collect
                wasUp = up
                // 面板在屏时球不复活：球只是面板的入口，入口开着就不需要第二个入口
                if (up) {
                    if (FloatingX.controlOrNull(PANEL_TAG)?.isShow() != true) showControl()
                } else {
                    hideControl()
                }
            }
        }
    }

    private suspend fun showControl() {
        when (currentMode) {
            OverlayControlMode.FLOAT_BALL -> showBall()
            OverlayControlMode.ACCESSIBILITY -> borderOverlayManager.show()
        }
    }

    private suspend fun hideControl() {
        hideBall()
        borderOverlayManager.hide()
    }

    // ── 装卸 ──

    private fun install() {
        if (!FloatingX.isInstalled(PANEL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(PANEL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(createPanelView())
                setEnableEdgeAdsorption(false)
                setGravity(FxGravity.CENTER)
                setEnableSafeArea(false)
                setEnableAnimation(true)
                setDisplayMode(FxDisplayMode.ClickOnly)
                setEnableKeyBoardAdapt(true)
                setKeyBackListener(object : IKeyBackListener {
                    // 消费返回键：不然会落到下面的目标应用，把它退出去
                    override fun onBackPressed(): Boolean = true
                })
            }
        }
        if (!FloatingX.isInstalled(BALL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(BALL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(createBallView())
                setEnableEdgeAdsorption(true)
                setGravity(FxGravity.RIGHT_OR_CENTER)
                setEnableAnimation(true)
            }
        }
        Timber.d("Control overlay attached")
    }

    private fun createPanelView(): ComposeView = newComposeView().apply {
        panelLayout?.let { layoutParams = ViewGroup.LayoutParams(it.first, it.second) }
        setContent {
            AzurPilotTheme {
                // 不用 collectAsStateWithLifecycle：悬浮窗隐藏时 owner 停在 CREATED，
                // 那样收不到环境态变化，再显示出来就是过期数据
                val snapshot by hostState.snapshot.collectAsState()
                val locked by isPanelLocked.collectAsState()
                val run by runController.state.collectAsState()
                OverlayPanel(
                    snapshot = snapshot,
                    run = run,
                    isLocked = locked,
                    onStart = { scope.launch { hostState.ensureEnvironmentStarted() } },
                    onStop = { scope.launch { hostState.stopEnvironment() } },
                    onRunStart = { runController.startRunner() },
                    onRunStop = { runController.stopRunner() },
                    onToolStart = { runController.startTool(it) },
                    onToolStop = { runController.stopTool() },
                    onBackToApp = ::bringAppToFront,
                    onLockToggle = { setPanelLocked(it) },
                    onClose = ::onPanelClosed,
                )
            }
        }
    }

    private fun createBallView(): ComposeView = newComposeView().apply {
        setContent {
            AzurPilotTheme {
                val snapshot by hostState.snapshot.collectAsState()
                FloatBall(running = snapshot.environmentUp, onClick = ::onBallClick)
            }
        }
    }

    private fun newComposeView(): ComposeView = ComposeView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setViewTreeLifecycleOwner(viewModelOwner)
        setViewTreeViewModelStoreOwner(viewModelOwner)
        setViewTreeSavedStateRegistryOwner(viewModelOwner)
    }

    // ── 交互 ──

    private fun onBallClick() {
        hideBall()
        showPanel()
    }

    private fun onPanelClosed() {
        hidePanel()
        if (currentMode == OverlayControlMode.FLOAT_BALL &&
            hostState.snapshot.value.environmentUp
        ) {
            showBall()
        }
    }

    private fun bringAppToFront() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Failed to return to app") }
    }

    private fun setPanelLocked(locked: Boolean) {
        isPanelLocked.value = locked
        FloatingX.controlOrNull(PANEL_TAG)?.updateConfig {
            setDisplayMode(if (locked) FxDisplayMode.ClickOnly else FxDisplayMode.Normal)
        }
    }

    // ── 显隐 ──

    private fun showPanel() {
        viewModelOwner.start()
        FloatingX.controlOrNull(PANEL_TAG)?.show()
    }

    private fun hidePanel() {
        FloatingX.controlOrNull(PANEL_TAG)?.hide()
        viewModelOwner.stop()
    }

    private fun showBall() = FloatingX.controlOrNull(BALL_TAG)?.show()

    private fun hideBall() = FloatingX.controlOrNull(BALL_TAG)?.hide()

    private fun togglePanel() {
        if (FloatingX.controlOrNull(PANEL_TAG)?.isShow() == true) hidePanel() else showPanel()
    }

    private suspend fun applyMode(mode: OverlayControlMode) {
        if (currentMode == mode) return
        Timber.d("Control overlay mode $currentMode -> $mode")
        when (currentMode) {
            OverlayControlMode.ACCESSIBILITY -> borderOverlayManager.hide()
            OverlayControlMode.FLOAT_BALL -> hideBall()
        }
        currentMode = mode
        if (!hostState.snapshot.value.environmentUp) return
        when (mode) {
            OverlayControlMode.ACCESSIBILITY -> {
                registerVolumeKeyListener()
                borderOverlayManager.show()
            }

            OverlayControlMode.FLOAT_BALL -> {
                unregisterVolumeKeyListener()
                showBall()
            }
        }
    }

    private fun registerVolumeKeyListener() {
        AccessibilityHelperService.onVolumeUpDownPressed.set { scope.launch { togglePanel() } }
    }

    private fun unregisterVolumeKeyListener() {
        AccessibilityHelperService.onVolumeUpDownPressed.set(null)
    }

    // ── 布局 ──

    /** 横屏时高度吃满一点：可用高度本来就少，按竖屏那个比例会挤成一条 */
    private fun calculatePanelLayout(config: Configuration): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val heightRatio =
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 0.85f else 0.6f
        return (config.screenWidthDp * density * 0.85f).toInt() to
                (config.screenHeightDp * density * heightRatio).toInt()
    }

    private fun applyPanelLayout(config: Configuration, layout: Pair<Int, Int>) {
        val control = FloatingX.controlOrNull(PANEL_TAG) ?: return
        val density = context.resources.displayMetrics.density
        val (width, height) = layout
        val wasShowing = control.isShow()
        if (wasShowing) control.hide()
        control.move(
            (config.screenWidthDp * density - width) / 2,
            (config.screenHeightDp * density - height) / 2
        )
        // 尺寸变了必须换视图：FloatingX 不会因为 layoutParams 改了就重新测量已挂载的那份
        control.updateView(createPanelView())
        if (wasShowing) control.show()
    }

    private companion object {
        const val PANEL_TAG = "azurpilot_overlay_panel"
        const val BALL_TAG = "azurpilot_overlay_ball"
    }
}
