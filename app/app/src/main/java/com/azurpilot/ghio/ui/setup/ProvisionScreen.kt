package com.azurpilot.ghio.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.azurpilot.ghio.R
import com.azurpilot.ghio.provision.ProvisionState
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AppSingleChoiceFlow

/**
 * 首启 rootfs 部署页：未完成时整屏接管（AppRoot 的门）
 *
 * 「等」之外只有两件事：下载源可选（直连 / 镜像，切换即重下），失败给重试；
 * 跳过只留给开发包
 */
@Composable
fun ProvisionScreen(
    state: ProvisionState,
    onRetry: () -> Unit,
    onSkip: (() -> Unit)?,
    useMirror: Boolean,
    onUseMirrorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTokens.Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.provision_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(AppTokens.Spacing.md))
            Text(
                text = stringResource(R.string.provision_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppTokens.Spacing.xl))

            when (state) {
                ProvisionState.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    StatusLine(stringResource(R.string.provision_checking))
                }

                is ProvisionState.Extracting -> {
                    val progress =
                        if (state.totalBytes > 0) state.doneBytes.toFloat() / state.totalBytes else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StatusLine(
                        stringResource(
                            R.string.provision_extracting,
                            (progress * 100).toInt(),
                            state.doneBytes / 1_000_000,
                            state.totalBytes / 1_000_000,
                        )
                    )
                }

                is ProvisionState.Downloading -> {
                    val progress = if (state.totalBytes > 0) state.doneBytes.toFloat() / state.totalBytes else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    StatusLine(stringResource(R.string.provision_downloading, (progress * 100).toInt()))
                }

                is ProvisionState.LowDisk -> {
                    StatusLine(
                        stringResource(
                            R.string.provision_low_disk,
                            state.freeBytes / 1_000_000,
                        ),
                        isError = true,
                    )
                    RetryButton(onRetry)
                }

                ProvisionState.NotBundled -> {
                    StatusLine(
                        stringResource(R.string.provision_not_bundled),
                        isError = true,
                    )
                    RetryButton(onRetry)
                }

                is ProvisionState.Failed -> {
                    StatusLine(
                        stringResource(R.string.provision_failed, state.reason),
                        isError = true,
                    )
                    RetryButton(onRetry)
                }

                ProvisionState.Ready -> Unit // 门已开，本页不再渲染
            }

            // 开发包（未内置资产）的逃生门：下载不下来也得能进外壳
            if (onSkip != null) {
                Spacer(Modifier.height(AppTokens.Spacing.sm))
                Button(onClick = onSkip) {
                    Text(stringResource(R.string.provision_skip))
                }
            }

            // 磁盘不足与下载源无关，别在那里堆选项
            if (state !is ProvisionState.LowDisk) {
                Spacer(Modifier.height(AppTokens.Spacing.xl))
                Text(
                    text = stringResource(R.string.provision_source),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppTokens.Spacing.sm))
                AppSingleChoiceFlow(
                    options = listOf(
                        false to stringResource(R.string.provision_source_direct),
                        true to stringResource(R.string.provision_source_mirror),
                    ),
                    selected = useMirror,
                    onSelect = onUseMirrorChange,
                    arrangement = Alignment.CenterHorizontally,
                )
                Spacer(Modifier.height(AppTokens.Spacing.sm))
                Text(
                    text = stringResource(R.string.provision_source_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, isError: Boolean = false) {
    Spacer(Modifier.height(AppTokens.Spacing.md))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    Spacer(Modifier.height(AppTokens.Spacing.lg))
    Button(onClick = onRetry) {
        Text(stringResource(R.string.provision_retry))
    }
}
