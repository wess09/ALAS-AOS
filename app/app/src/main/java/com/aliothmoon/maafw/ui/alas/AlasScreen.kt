package com.aliothmoon.maafw.ui.alas

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
import com.aliothmoon.maafw.R
import com.aliothmoon.maafw.proot.ProotHost
import com.aliothmoon.maafw.proot.ProotPhase
import com.aliothmoon.maafw.service.HostState
import com.aliothmoon.maafw.theme.MaaDesignTokens
import org.koin.compose.koinInject

/** AzurPilot WebUI：App 内置环境监听的本机回环地址。 */
private val AZURPILOT_WEBUI_URI: Uri = Uri.parse("http://127.0.0.1:25548")

/**
 * AzurPilot 的 React 页面交给浏览器 Custom Tab 渲染。
 *
 * 同机 Chrome 已验证页面正常，而 System WebView 在 fixed 抽屉的合成上存在设备相关
 * 故障。Custom Tab 仍停留在 App 的返回栈内，并直接访问同一设备的回环 WebUI；若浏览器
 * 不支持 Custom Tabs，则退回普通 ACTION_VIEW。
 */
@Composable
fun AlasScreen(
    active: Boolean,
    modifier: Modifier = Modifier,
    hostState: HostState = koinInject(),
    prootHost: ProotHost = koinInject(),
) {
    val context = LocalContext.current
    val hostSnapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val prootState by prootHost.state.collectAsStateWithLifecycle()
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

    LaunchedEffect(active, prootState.phase) {
        if (active && prootState.phase == ProotPhase.RUNNING && !openedForActivation) {
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
            .padding(MaaDesignTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.azurpilot_browser_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(MaaDesignTokens.Spacing.md))
        Text(
            text = when (prootState.phase) {
                ProotPhase.FAILED -> stringResource(R.string.proot_phase_failed, prootState.detail)
                ProotPhase.RUNNING -> browserError
                    ?: stringResource(R.string.azurpilot_browser_ready)
                ProotPhase.PREPARING -> stringResource(R.string.proot_phase_preparing)
                ProotPhase.UPDATING -> stringResource(R.string.proot_phase_updating)
                ProotPhase.STARTING -> stringResource(R.string.proot_phase_starting)
                else -> stringResource(R.string.alas_webui_loading)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MaaDesignTokens.Spacing.lg))
        if (prootState.phase != ProotPhase.RUNNING) {
            CircularProgressIndicator()
            Spacer(Modifier.height(MaaDesignTokens.Spacing.lg))
        }
        Button(
            enabled = prootState.phase == ProotPhase.RUNNING,
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
