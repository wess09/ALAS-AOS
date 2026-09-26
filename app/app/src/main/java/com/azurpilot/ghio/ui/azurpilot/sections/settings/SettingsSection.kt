package com.azurpilot.ghio.ui.azurpilot.sections.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.ui.azurpilot.ApKeyValueRow
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.azurpilot.instanceStatusText
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.AppNavigationRow

/**
 * 设置：实例、公告、指挥喵、运行时更新、部署设置
 *
 * 这里是「全局性」的东西；任务参数在配置分区里。分隔的依据是**作用域**：
 * 改了只影响一个实例的归配置，影响整个运行环境的归这里。
 */
@Composable
fun SettingsSection(
    repository: AzurPilotRepository,
    onOpenInstances: () -> Unit,
    onOpenAnnouncement: () -> Unit,
    onOpenMeowfficer: () -> Unit,
    onOpenUpdater: () -> Unit,
    onOpenDeploy: () -> Unit,
) {
    val instances by repository.instances.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()
    val updater by repository.updater.collectAsStateWithLifecycle()
    val announcement by repository.announcement.collectAsStateWithLifecycle()
    val deploy by repository.deploySettings.collectAsStateWithLifecycle()

    ApSectionColumn {
        AppCard(title = stringResource(R.string.ap_title_instances)) {
            AppNavigationRow(
                label = stringResource(R.string.ap_settings_instances_manage),
                description = stringResource(R.string.ap_settings_instances_count, instances.size),
                onClick = onOpenInstances,
            )
            selected?.let { name ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ApKeyValueRow(
                    label = stringResource(R.string.ap_settings_current_instance),
                    value = instances.firstOrNull { it.name == name }
                        ?.let { "$name · ${instanceStatusText(it.status)}" }
                        ?: name,
                )
            }
        }

        AppCard {
            AppNavigationRow(
                label = stringResource(R.string.ap_title_announcement),
                description = announcement?.title ?: stringResource(R.string.ap_settings_announcement_empty),
                onClick = onOpenAnnouncement,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AppNavigationRow(
                label = stringResource(R.string.ap_title_meowfficer),
                description = stringResource(R.string.ap_settings_meowfficer_hint),
                onClick = onOpenMeowfficer,
            )
        }

        AppCard(title = stringResource(R.string.ap_title_updater)) {
            AppNavigationRow(
                label = updater?.let { it.localHead?.take(10) ?: it.state } ?: stringResource(R.string.ap_waiting_data),
                description = when {
                    updater == null -> stringResource(R.string.ap_waiting_data)
                    updater!!.managedByAndroid -> stringResource(R.string.ap_updater_managed)
                    updater!!.behind > 0 -> stringResource(R.string.ap_updater_behind, updater!!.behind)
                    else -> stringResource(R.string.ap_updater_current)
                },
                onClick = onOpenUpdater,
            )
        }

        AppCard(title = stringResource(R.string.ap_title_deploy)) {
            if (deploy == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { Text(stringResource(R.string.ap_waiting_data)) }
            } else {
                AppNavigationRow(
                    label = stringResource(R.string.ap_settings_deploy_open),
                    description = stringResource(R.string.ap_settings_deploy_count, deploy!!.groups.size),
                    onClick = onOpenDeploy,
                )
                deploy!!.remote?.let { remote ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ApKeyValueRow(
                        label = stringResource(R.string.ap_settings_remote),
                        value = if (remote.enabled) remote.state else stringResource(R.string.ap_settings_remote_off),
                    )
                }
            }
        }
    }
}
