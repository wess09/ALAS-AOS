package com.azurpilot.ghio.ui.azurpilot

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.proot.ProotHost
import com.azurpilot.ghio.proot.ProotPhase
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.sections.ConfigSection
import com.azurpilot.ghio.ui.azurpilot.sections.LogsSection
import com.azurpilot.ghio.ui.azurpilot.sections.OverviewSection
import com.azurpilot.ghio.ui.azurpilot.sections.StatisticsSection
import com.azurpilot.ghio.ui.azurpilot.sections.TaskConfigPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.AnnouncementPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.DeploySettingsPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.InstancesPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.MeowfficerPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.SettingsSection
import com.azurpilot.ghio.ui.azurpilot.sections.settings.UpdaterPage
import org.koin.compose.koinInject

/**
 * AzurPilot 页：原生实现的 WebUI
 *
 * WebUI 的前端本身也是拿 `schema.get` 下发的定义现渲染的，所以这里照同一份定义渲染，
 * 功能面就不会有偏差——菜单、任务、参数、翻译全部来自运行时，App 侧不硬编码任何一项。
 *
 * 进程的启停**不在这里**：宿主只保留一条控制面（主页控制面板 / 悬浮窗走 `/android/…`），
 * 多一条控制面会和它在设备上抢。这里管的是内容面（总览 / 配置 / 日志 / 统计 / 部署设置）。
 */
@Composable
fun AzurPilotPage(
    active: Boolean,
    modifier: Modifier = Modifier,
    repository: AzurPilotRepository = koinInject(),
    hostState: HostState = koinInject(),
    prootHost: ProotHost = koinInject(),
    runController: AzurPilotRunController = koinInject(),
) {
    val connected by repository.connected.collectAsStateWithLifecycle()
    val hostSnapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val prootState by prootHost.state.collectAsStateWithLifecycle()
    val runState by runController.state.collectAsStateWithLifecycle()
    val message by repository.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 和主页一样：这一页可见时才补一次环境拉起，避免在后台白转
    LaunchedEffect(active, hostSnapshot.privilegedConnected) {
        if (active && hostSnapshot.privilegedConnected) hostState.ensureEnvironmentStarted()
    }

    // 网关只在被订阅时才推截图帧，离开前台就把带大帧的订阅放掉
    LaunchedEffect(active) { repository.setForeground(active) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            repository.dismissMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (connected) {
            AzurPilotContent(repository = repository)
        } else {
            RuntimeGate(
                reachable = runState.reachable,
                phase = prootState.phase,
                detail = prootState.detail,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(AppTokens.Spacing.lg),
        )
    }
}

/**
 * 网关还没连上时的接管屏
 *
 * 「未就绪」与「环境正在起」要分开说：准备链要几分钟，只显示一句「未就绪」会让人以为坏了。
 */
@Composable
private fun RuntimeGate(reachable: Boolean, phase: ProotPhase, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = when {
                phase == ProotPhase.FAILED -> stringResource(R.string.proot_phase_failed, detail)
                phase == ProotPhase.PREPARING -> stringResource(R.string.proot_phase_preparing)
                phase == ProotPhase.UPDATING -> stringResource(R.string.proot_phase_updating)
                phase == ProotPhase.STARTING -> stringResource(R.string.proot_phase_starting)
                reachable -> stringResource(R.string.ap_connecting)
                else -> stringResource(R.string.ap_runtime_idle)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppTokens.Spacing.lg),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AzurPilotContent(repository: AzurPilotRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val section = AzurPilotSection.parentOfRoute(route)
    val isDetail = route != null && route in AzurPilotSection.DETAILS
    val instances by repository.instances.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()

    fun openSection(target: AzurPilotSection) {
        navController.navigate(target.route) {
            // 分区之间是平级切换：不留历史，否则返回键会在标签之间来回跳
            popUpTo(AzurPilotSection.Overview.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // AppRoot 的 Scaffold 已经处理过系统栏，这里不能再吃掉一遍
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isDetail) detailTitle(route.orEmpty()) else stringResource(section.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.ap_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = section.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                AzurPilotSection.entries.forEach { entry ->
                    Tab(
                        selected = entry == section,
                        onClick = { openSection(entry) },
                        text = { Text(stringResource(entry.labelRes), maxLines = 1) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InstanceChipRow(
                instances = instances.map { it.name },
                selected = selected,
                onSelect = repository::selectInstance,
                onManage = { navController.navigate(AzurPilotSection.INSTANCES) },
            )
            NavHost(
                navController = navController,
                startDestination = AzurPilotSection.Overview.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(AzurPilotSection.Overview.route) { OverviewSection(repository) }
                composable(AzurPilotSection.Config.route) {
                    ConfigSection(repository, onOpenTask = { navController.navigate(AzurPilotSection.task(it)) })
                }
                composable(AzurPilotSection.Logs.route) { LogsSection(repository) }
                composable(AzurPilotSection.Statistics.route) { StatisticsSection(repository) }
                composable(AzurPilotSection.Settings.route) {
                    SettingsSection(
                        repository = repository,
                        onOpenInstances = { navController.navigate(AzurPilotSection.INSTANCES) },
                        onOpenAnnouncement = { navController.navigate(AzurPilotSection.ANNOUNCEMENT) },
                        onOpenMeowfficer = { navController.navigate(AzurPilotSection.MEOWFFICER) },
                        onOpenUpdater = { navController.navigate(AzurPilotSection.UPDATER) },
                        onOpenDeploy = { navController.navigate(AzurPilotSection.DEPLOY) },
                    )
                }
                composable(
                    route = AzurPilotSection.TASK,
                    arguments = listOf(
                        navArgument(AzurPilotSection.TASK_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    TaskConfigPage(
                        repository = repository,
                        task = entry.arguments?.getString(AzurPilotSection.TASK_ARG).orEmpty(),
                    )
                }
                composable(AzurPilotSection.INSTANCES) { InstancesPage(repository) }
                composable(AzurPilotSection.ANNOUNCEMENT) { AnnouncementPage(repository) }
                composable(AzurPilotSection.MEOWFFICER) { MeowfficerPage(repository) }
                composable(AzurPilotSection.UPDATER) { UpdaterPage(repository) }
                composable(AzurPilotSection.DEPLOY) { DeploySettingsPage(repository) }
            }
        }
    }
}

@Composable
private fun detailTitle(route: String): String = when {
    route.startsWith("ap/task/") -> stringResource(R.string.ap_title_task_config)
    route == AzurPilotSection.INSTANCES -> stringResource(R.string.ap_title_instances)
    route == AzurPilotSection.ANNOUNCEMENT -> stringResource(R.string.ap_title_announcement)
    route == AzurPilotSection.MEOWFFICER -> stringResource(R.string.ap_title_meowfficer)
    route == AzurPilotSection.UPDATER -> stringResource(R.string.ap_title_updater)
    else -> stringResource(R.string.ap_title_deploy)
}

/**
 * 实例选择条
 *
 * 常显而不是藏进顶栏菜单：实例是这一页的**上下文**，所有分区的内容都随它变。
 * 藏起来会出现「看了半天才发现看的是另一个实例」。
 */
@Composable
private fun InstanceChipRow(
    instances: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppTokens.Spacing.lg, vertical = AppTokens.Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (instances.isEmpty()) {
            Text(
                text = stringResource(R.string.ap_instance_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        instances.forEach { name ->
            FilterChip(
                selected = name == selected,
                onClick = { onSelect(name) },
                label = { Text(name) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onManage,
            label = { Text(stringResource(R.string.ap_instance_manage)) },
            leadingIcon = {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AppTokens.IconSize.sm))
            },
        )
    }
}
