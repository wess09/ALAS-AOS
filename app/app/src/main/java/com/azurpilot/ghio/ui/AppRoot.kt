package com.azurpilot.ghio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.azurpilot.ghio.R
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.domain.ThemeMode
import com.azurpilot.ghio.log.LogExportKind
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.proot.ProotHost
import com.azurpilot.ghio.provision.ProvisionState
import com.azurpilot.ghio.provision.RootfsProvisioner
import com.azurpilot.ghio.settings.SettingsIntent
import com.azurpilot.ghio.settings.SettingsViewModel
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.run.AzurPilotScreen
import com.azurpilot.ghio.ui.components.ShizukuReadinessDialog
import com.azurpilot.ghio.ui.hangar.FullscreenPreview
import com.azurpilot.ghio.ui.hangar.HangarScreen
import com.azurpilot.ghio.ui.hangar.PreviewTouchAction
import com.azurpilot.ghio.ui.hangar.rememberMovablePreview
import com.azurpilot.ghio.ui.navigation.Routes
import com.azurpilot.ghio.ui.logs.AzurPilotErrorDetailScreen
import com.azurpilot.ghio.ui.logs.AzurPilotLogDetailScreen
import com.azurpilot.ghio.ui.logs.AzurPilotLogScreen
import com.azurpilot.ghio.ui.logs.AppLogDetailScreen
import com.azurpilot.ghio.ui.logs.AppLogScreen
import com.azurpilot.ghio.ui.logs.LogExportController
import com.azurpilot.ghio.ui.settings.SettingsScreen
import com.azurpilot.ghio.ui.setup.ProvisionScreen
import com.azurpilot.ghio.update.AppUpdateManager
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class TopDestination(
    @param:StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    // 默认首页：App 一打开就是挂机页（画面 + 控制面）
    Hangar(R.string.nav_hangar, Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    AzurPilot(R.string.nav_azurpilot, Icons.Outlined.Public, Icons.Filled.Public),
    Settings(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/**
 * 二级页面盖在主 tab 之上时这层的输入处理
 *
 * 主 tab 那层还活着只是被盖住，不截断命中测试就能隔着二级页横滑切页、点到底栏
 */
@Composable
private fun Modifier.subPageOverlayInput(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            // Main pass 排在子节点之后，二级页自己的手势先走，这里只收剩下的
            while (true) {
                awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    }

/** Route：收集 state、承载两个主 tab 与二级页面的 NavHost；VM 为 Activity 作用域 */
@Composable
fun AppRoot(
    onDarkThemeChanged: (Boolean) -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject(),
    provisioner: RootfsProvisioner = koinInject(),
    appUpdateManager: AppUpdateManager = koinInject(),
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val readiness by permissionManager.readiness.collectAsStateWithLifecycle()
    val isGranting by permissionManager.isGranting.collectAsStateWithLifecycle()

    // 首启 rootfs 部署的门：未 Ready 时整屏接管，tab/二级页都在门内
    val provisionState by provisioner.state.collectAsStateWithLifecycle()
    var provisionSkipped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { provisioner.start() }
    val appUpdateState by appUpdateManager.state.collectAsStateWithLifecycle()
    LaunchedEffect(provisionState) {
        if (provisionState is ProvisionState.Ready) appUpdateManager.check()
    }
    val showProvision = provisionState !is ProvisionState.Ready && !provisionSkipped

    // 部署就绪即起内置 AzurPilot 环境（自愈清锁→热更新→wrapper/WebUI；ProotHost 内幂等）
    val prootHost: ProotHost = koinInject()
    LaunchedEffect(provisionState) {
        if (provisionState is ProvisionState.Ready) prootHost.ensureStarted()
    }

    val darkTheme = when (settingsState.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    LaunchedEffect(darkTheme) { onDarkThemeChanged(darkTheme) }

    AzurPilotTheme(darkTheme = darkTheme) {
        appUpdateState.available?.let { update ->
            AlertDialog(
                onDismissRequest = appUpdateManager::dismiss,
                title = { Text(stringResource(R.string.app_update_title)) },
                text = {
                    Text(
                        if (appUpdateState.downloading) stringResource(R.string.app_update_downloading)
                        else if (appUpdateState.error != null) stringResource(R.string.app_update_error, appUpdateState.error!!)
                        else stringResource(R.string.app_update_message, update.versionName, update.azurPilotCommit.take(10)),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !appUpdateState.downloading,
                        onClick = appUpdateManager::downloadAndInstall,
                    ) { Text(stringResource(R.string.app_update_install)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !appUpdateState.downloading,
                        onClick = appUpdateManager::dismiss,
                    ) { Text(stringResource(R.string.app_update_later)) }
                },
            )
        }
        // NavHost 只承载二级页面；主 tab 仍由下面的 HorizontalPager 渲染
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        // 首帧 backStackEntry 还没就绪，那时必然停在 startDestination
        val currentRoute = navBackStackEntry?.destination?.route
        val onSubPage = currentRoute != null && currentRoute !in Routes.mainTabs
        val pagerState = rememberPagerState(pageCount = { TopDestination.entries.size })
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var exportKind by remember { mutableStateOf<LogExportKind?>(null) }

        // 预览面（SurfaceView）的所有权在这一层：全屏宿主必须在 Scaffold 之外才盖得住
        // 底部 tab 栏，而 movableContent 要求内嵌与全屏两处调用点同属一棵组合树（m0 同款）
        val hostState: HostState = koinInject()
        val hangarActive = pagerState.currentPage == TopDestination.Hangar.ordinal
        var previewFullscreen by rememberSaveable { mutableStateOf(false) }
        val previewContent = rememberMovablePreview(
            active = hangarActive,
            onSurfaceAvailable = { hostState.attachPreviewSurface(it) },
            onSurfaceDestroyed = { hostState.detachPreviewSurface() },
        )

        val context = LocalContext.current

        // 底栏实际高度：snackbar 要停在它上面，而 M3 只给了 80dp 的私有常量
        val density = LocalDensity.current
        var bottomBarHeight by remember { mutableStateOf(0.dp) }

        // 未装/未启动/未授权时的引导；needsGuidance 为 false 时自身不渲染
        ShizukuReadinessDialog(
            readiness = readiness,
            // NotInstalled 档：不内置安装包，确认键 = 用户装完后重跑探测
            onInstall = { permissionManager.refresh() },
            onOpenApp = { permissionManager.openShizuku(context) },
            onRequestAuth = { scope.launch { permissionManager.requestRemoteAccess() } },
            onUninstall = { permissionManager.uninstallShizuku(context) },
            onDismiss = { scope.launch { permissionManager.skipShizukuCheck() } },
            onSwitchToRoot = { settingsViewModel.onIntent(SettingsIntent.SetBackend(RemoteBackend.ROOT)) },
            isRequesting = isGranting,
        )

        if (showProvision) {
            ProvisionScreen(
                state = provisionState,
                onRetry = { provisioner.retry() },
                // 跳过只留给开发包（未内置资产）：跳过只能调外壳，AzurPilot 起不来
                onSkip = if (BuildConfig.DEBUG && provisionState is ProvisionState.NotBundled) {
                    { provisionSkipped = true }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.onGloballyPositioned {
                        bottomBarHeight = with(density) { it.size.height.toDp() }
                    },
                ) {
                    TopDestination.entries.forEachIndexed { index, destination ->
                        val selected = pagerState.currentPage == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                // AzurPilot 页会拉起浏览器，页内自己的横滑（网页手势）不该和 pager 切页抢事件；
                // 在 AzurPilot 页禁用用户横滑（切页走底部 tab），其他页保持原样
                userScrollEnabled = TopDestination.entries[pagerState.currentPage] != TopDestination.AzurPilot,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // 页内 imePadding 量的是到窗口底边的距离，而这里的底边已经被底栏顶高了一截；
                    // 不声明这份已让位的 inset，页内就会多减一个底栏，正文与键盘之间空出一条
                    .consumeWindowInsets(padding),
            ) { page ->
                when (TopDestination.entries[page]) {
                    TopDestination.Hangar -> HangarScreen(
                        active = hangarActive,
                        // 全屏时这里让位，同一份 previewContent 搬到下面的全屏宿主
                        previewContent = previewContent.takeUnless { previewFullscreen },
                        onEnterFullscreen = { previewFullscreen = true },
                        modifier = Modifier.fillMaxSize(),
                    )

                    TopDestination.AzurPilot -> AzurPilotScreen(
                        // 从挂机直接动画切到设置时，currentPage 会短暂经过中间的 AzurPilot 页。
                        // 只有动画真正停在该页后才允许自动弹 WebUI。
                        active = pagerState.settledPage == TopDestination.AzurPilot.ordinal,
                        modifier = Modifier.fillMaxSize(),
                    )

                    TopDestination.Settings -> SettingsScreen(
                        state = settingsState,
                        onIntent = settingsViewModel::onIntent,
                        onOpenAppLog = { navController.navigate(Routes.APP_LOG) },
                        onOpenRunnerLog = { navController.navigate(Routes.AZURPILOT_LOG) },
                        onExportRunnerLogs = { exportKind = LogExportKind.AZURPILOT },
                        onExportLauncherLogs = { exportKind = LogExportKind.LAUNCHER },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // 二级页面自成一层：留在 Scaffold 体内会被底栏从高度里扣掉一截，盖不住它；
        // inset 也随之归各页自己吃
        // imePadding 排在指针修饰符之后，命中区仍是整屏，只有内容被压
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (onSubPage) Modifier.subPageOverlayInput() else Modifier)
                .imePadding(),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HANGAR,
                modifier = Modifier.fillMaxSize(),
                // 共享轴 X 前进转场：推进右进左出、返回左进右出
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) {
                // 主 tab 路由空占位：真实内容由上面的 HorizontalPager 渲染
                composable(Routes.HANGAR) {}
                composable(Routes.AZURPILOT) {}
                composable(Routes.SETTINGS) {}
                composable(Routes.APP_LOG) {
                    AppLogScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { navController.navigate(Routes.appLogDetail(it)) },
                    )
                }
                composable(
                    route = Routes.APP_LOG_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.APP_LOG_DETAIL_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    AppLogDetailScreen(
                        fileName = entry.arguments?.getString(Routes.APP_LOG_DETAIL_ARG).orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.AZURPILOT_LOG) {
                    AzurPilotLogScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDaily = { navController.navigate(Routes.azurPilotLogDetail(it)) },
                        onOpenError = { navController.navigate(Routes.azurPilotErrorDetail(it)) },
                    )
                }
                composable(
                    route = Routes.AZURPILOT_LOG_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.AZURPILOT_LOG_DETAIL_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    AzurPilotLogDetailScreen(
                        fileName = entry.arguments?.getString(Routes.AZURPILOT_LOG_DETAIL_ARG).orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.AZURPILOT_ERROR_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.AZURPILOT_ERROR_DETAIL_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    AzurPilotErrorDetailScreen(
                        dirName = entry.arguments?.getString(Routes.AZURPILOT_ERROR_DETAIL_ARG).orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // 挂在二级页面之上，否则整屏的二级页一盖，snackbar 就没人看得见
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (onSubPage) 0.dp else bottomBarHeight),
        )

        // 全屏预览：挂在 Scaffold 之外，才盖得住底部 tab 栏与系统栏（m0 同款）
        if (previewFullscreen) {
            FullscreenPreview(
                onExit = { previewFullscreen = false },
                onTouch = { x, y, action ->
                    when (action) {
                        PreviewTouchAction.Down -> hostState.touchDown(x, y)
                        PreviewTouchAction.Move -> hostState.touchMove(x, y)
                        PreviewTouchAction.Up -> hostState.touchUp(x, y)
                    }
                },
                content = previewContent,
            )
        }
        }
        }

        // 无条件挂在这一层：它注册的 SAF launcher 要活得比 sheet 的显隐久
        LogExportController(
            kind = exportKind,
            onDismiss = { exportKind = null },
            onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
}
