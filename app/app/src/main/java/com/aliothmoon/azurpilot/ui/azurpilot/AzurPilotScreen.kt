package com.aliothmoon.azurpilot.ui.run

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.proot.ProotHost
import com.aliothmoon.azurpilot.proot.ProotPhase
import com.aliothmoon.azurpilot.service.HostState
import com.aliothmoon.azurpilot.theme.AppTokens
import org.koin.compose.koinInject

/** AzurPilot WebUI：App 内置环境监听的本机回环地址。 */
private val AZURPILOT_WEBUI_URI: Uri = Uri.parse("http://127.0.0.1:25548")

/**
 * AzurPilot 的 React 页面交给浏览器 Custom Tab 渲染。
 *
 * 同机 Chrome 已验证页面正常，而 System WebView 在 fixed 抽屉的合成上存在设备相关
 * 故障。Custom Tab 仍停留在 App 的返回栈内，并直接访问同一设备的回环 WebUI；若浏览器
 * 不支持 Custom Tabs，则退回普通 ACTION_VIEW。
 *
 * **就绪判据是「WebUI 在答」，不是 proot 阶段**：`ProotPhase.RUNNING` 等的是 wrapper 就绪，
 * 而 WebUI 进程往往更早就能服务——实践上 gui.py 一起来，浏览器打开 127.0.0.1:25548 就能用，
 * 此时外壳还停在「环境准备中」，用户被白拦一道。这里改看 `AzurPilotRunController.reachable`
 * （它每 4s 打一次 `/android/configs`），答得上就允许打开并自动打开一次。
 */
@Composable
fun AzurPilotScreen(
    active: Boolean,
    modifier: Modifier = Modifier,
    hostState: HostState = koinInject(),
    prootHost: ProotHost = koinInject(),
    runController: AzurPilotRunController = koinInject(),
) {
    val context = LocalContext.current
    val hostSnapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val prootState by prootHost.state.collectAsStateWithLifecycle()
    val runState by runController.state.collectAsStateWithLifecycle()
    val webUiReady = runState.reachable
    var openedForActivation by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active) {
        if (!active) openedForActivation = false
    }

    LaunchedEffect(active, hostSnapshot.privilegedConnected) {
        if (active && hostSnapshot.privilegedConnected) {
            hostState.ensureEnvironmentStarted()
        }
    }

    LaunchedEffect(active, webUiReady) {
        if (active && webUiReady && !openedForActivation) {
            openedForActivation = true
            openWebUi(context).onFailure {
                browserError = it.message
                openedForActivation = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.azurpilot_browser_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(AppTokens.Spacing.md))
        Text(
            text = if (webUiReady) {
                browserError ?: stringResource(R.string.azurpilot_browser_ready)
            } else when (prootState.phase) {
                ProotPhase.FAILED -> stringResource(R.string.proot_phase_failed, prootState.detail)
                ProotPhase.PREPARING -> stringResource(R.string.proot_phase_preparing)
                ProotPhase.UPDATING -> stringResource(R.string.proot_phase_updating)
                ProotPhase.STARTING -> stringResource(R.string.proot_phase_starting)
                else -> stringResource(R.string.azurpilot_webui_loading)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppTokens.Spacing.lg))
        if (!webUiReady) {
            CircularProgressIndicator()
            Spacer(Modifier.height(AppTokens.Spacing.lg))
        }
        Button(
            enabled = webUiReady,
            onClick = {
                browserError = null
                openWebUi(context).onFailure { browserError = it.message }
            },
        ) {
            Text(stringResource(R.string.azurpilot_browser_open))
        }
    }
}

private fun openWebUi(context: Context): Result<Unit> = runCatching {
    CustomTabsIntent.Builder()
        .setShowTitle(false)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .build()
        .launchUrl(context, AZURPILOT_WEBUI_URI)
}.recoverCatching { customTabError ->
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, AZURPILOT_WEBUI_URI))
    } catch (browserError: ActivityNotFoundException) {
        browserError.addSuppressed(customTabError)
        throw browserError
    }
}
