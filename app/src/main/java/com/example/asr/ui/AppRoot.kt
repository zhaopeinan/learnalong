package com.example.asr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.asr.AsrApplication
import com.example.asr.R
import com.example.asr.audio.AudioImporter
import com.example.asr.data.local.entity.ChatMode
import com.example.asr.data.settings.AppSettings
import com.example.asr.ui.about.AboutScreen
import com.example.asr.ui.agreement.AgreementScreen
import com.example.asr.ui.backup.BackupScreen
import com.example.asr.ui.chat.ChatScreen
import com.example.asr.ui.children.ChildrenScreen
import com.example.asr.ui.components.TourOverlay
import com.example.asr.ui.components.TourPlan
import com.example.asr.ui.components.rememberPhotoCapture
import com.example.asr.ui.components.tourTarget
import com.example.asr.ui.detail.RecordingDetailScreen
import com.example.asr.ui.guide.GuideArticleScreen
import com.example.asr.ui.guide.GuideScreen
import com.example.asr.ui.kid.KidNavBar
import com.example.asr.ui.kid.KidProgressScreen
import com.example.asr.ui.mine.MineScreen
import com.example.asr.ui.record.RecordScreen
import com.example.asr.ui.recordings.RecordingsScreen
import com.example.asr.ui.settings.SettingsScreen
import com.example.asr.ui.splash.SplashScreen
import com.example.asr.ui.today.TodayScreen
import com.example.asr.ui.weakpoints.WeakPointExerciseScreen
import com.example.asr.ui.weakpoints.WeakPointsScreen
import com.example.asr.ui.work.WorkDetailScreen
import com.example.asr.ui.work.WorkHomeScreen
import com.example.asr.ui.work.WorkHomeViewModel
import com.example.asr.ui.work.WorkRecordScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val activeIconRes: Int,
)

// 图标与微信小程序 custom-tab-bar 一致（dark 变体在 drawable-night 自动切换）
private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.TODAY, "今日", R.drawable.today_normal, R.drawable.today_active),
    TopLevelDestination(Routes.RECORDINGS, "记录", R.drawable.records_normal, R.drawable.records_active),
    TopLevelDestination(Routes.WEAK_POINTS, "薄弱点", R.drawable.weakpoints_normal, R.drawable.weakpoints_active),
    TopLevelDestination(Routes.MINE, "我的", R.drawable.mine_normal, R.drawable.mine_active),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val context = LocalContext.current
    val app = context.applicationContext as AsrApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPanel by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // 孩子端模式：底部栏换成 KidNavBar，家长端 tab 与动作面板隐藏
    val appSettings by app.container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val kidMode = appSettings?.appMode == AppSettings.MODE_KID
    // 工作端模式：底部栏换成工作端自有导航（记录 / 中央大录音钮 / 待办）
    val workMode = appSettings?.appMode == AppSettings.MODE_WORK
    val showParentBar = !kidMode && !workMode && currentRoute in topLevelDestinations.map { it.route }
    val showKidBar = kidMode && currentRoute == Routes.KID_PROGRESS
    val showWorkBar = workMode && currentRoute == Routes.WORK_HOME

    // 工作端首页 ViewModel：页面与工作端底部导航共享（切换记录/待办视图 + 待办数角标）
    val workHomeVm: WorkHomeViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkHomeViewModel(app.container.workRepository) }
    })

    /** 退出孩子端（家长锁验证通过）：回家长端今日页并清空回退栈 */
    fun exitKidToParent() {
        navController.navigate(Routes.TODAY) {
            popUpTo(0) { inclusive = true }
        }
    }

    /** 退出工作端（对齐小程序 work home 左上角返回）：回家长端「我的」并清空回退栈 */
    fun exitWorkToParent() {
        scope.launch {
            app.container.settingsStore.setAppMode(AppSettings.MODE_PARENT)
            navController.navigate(Routes.MINE) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 动作面板拍照 / 相册选图：产出已复制进私有目录的照片，进记录页确认归属
    val photoCapture = rememberPhotoCapture(
        onResult = { files ->
            app.container.pendingPhotoImport.value = files
            navigateTopLevel(Routes.RECORDINGS)
        },
        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
    )

    // 动作面板导入音频：复用分享导入的 pendingImport 模式，进记录页确认归属
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                app.container.pendingImport.value = AudioImporter.import(context, uri)
                withContext(Dispatchers.Main) { navigateTopLevel(Routes.RECORDINGS) }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("导入失败：${e.message}")
            }
        }
    }

    fun dismissPanel() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) showPanel = false
        }
    }

    // 录音常驻通知点击：跳回录音页（录音在前台服务中持续，回页后自动恢复展示）
    val pendingOpenRecord by app.container.pendingOpenRecord.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenRecord) {
        if (pendingOpenRecord) {
            app.container.pendingOpenRecord.value = false
            // 录音通知点击：按当前模式回家长端/工作端录音页
            navController.navigate(if (workMode) Routes.WORK_RECORD else Routes.RECORD) {
                launchSingleTop = true
            }
        }
    }

    // 新手引导：路由变化时通知控制器（首次进「今日」自动开始；引导中跟随跳转）
    LaunchedEffect(currentRoute) {
        app.container.tourController.onRouteShown(currentRoute)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showKidBar) {
                KidNavBar(
                    currentRoute = currentRoute,
                    onProgress = {
                        if (currentRoute != Routes.KID_PROGRESS) {
                            navController.navigate(Routes.KID_PROGRESS) { launchSingleTop = true }
                        }
                    },
                    onAsk = {
                        navController.navigate(
                            Routes.chat(ChatMode.FREE, childId = appSettings?.kidChildId)
                        )
                    },
                    onExitToParent = { exitKidToParent() },
                )
            } else if (showWorkBar) {
                WorkNavBar(
                    vm = workHomeVm,
                    onRecord = { navController.navigate(Routes.WORK_RECORD) },
                )
            } else if (showParentBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { navigateTopLevel(it.route) },
                    onCenterClick = { showPanel = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            // 只消费底部 inset（导航栏/手势条）；顶部留给各页面顶栏自己延伸到状态栏，
            // 让品牌色与信号栏融为一体
            modifier = Modifier.padding(
                PaddingValues(bottom = innerPadding.calculateBottomPadding())
            ),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.AGREEMENT,
                arguments = listOf(
                    navArgument("gate") {
                        type = NavType.BoolType
                        defaultValue = true
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "terms"
                    },
                ),
            ) { entry ->
                val gate = entry.arguments?.getBoolean("gate") ?: true
                AgreementScreen(
                    gate = gate,
                    initialType = entry.arguments?.getString("type") ?: "terms",
                    onAgree = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.TODAY) { TodayScreen() }
            composable(Routes.RECORDINGS) {
                RecordingsScreen(
                    onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                    onRecord = { navController.navigate(Routes.RECORD) },
                )
            }
            composable(Routes.WEAK_POINTS) {
                WeakPointsScreen(
                    onOpenExercise = { id -> navController.navigate(Routes.exercise(id)) },
                )
            }
            composable(Routes.MINE) {
                MineScreen(
                    onOpenChildren = { navController.navigate(Routes.CHILDREN) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenGuide = { navController.navigate(Routes.GUIDE) },
                    onOpenAgreement = { navController.navigate(Routes.agreementReadonly()) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onEnterKidMode = {
                        navController.navigate(Routes.KID_PROGRESS) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEnterWorkMode = {
                        navController.navigate(Routes.WORK_HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.WORK_HOME) {
                WorkHomeScreen(
                    vm = workHomeVm,
                    onOpenDetail = { id -> navController.navigate(Routes.workDetail(id)) },
                    onExitToParent = { exitWorkToParent() },
                )
            }
            composable(Routes.WORK_RECORD) {
                WorkRecordScreen(
                    onSaved = { id ->
                        // 对齐小程序 redirectTo：保存后替换录音页进详情，自动开始转写分析
                        navController.navigate(Routes.workDetail(id, auto = true)) {
                            popUpTo(Routes.WORK_RECORD) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.WORK_DETAIL,
                arguments = listOf(
                    navArgument("recordingId") { type = NavType.LongType },
                    navArgument("auto") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                WorkDetailScreen(
                    recordingId = entry.arguments?.getLong("recordingId") ?: 0L,
                    autoStart = entry.arguments?.getBoolean("auto") ?: false,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.KID_PROGRESS) {
                KidProgressScreen(
                    onAskTutor = { weakPointId, childId ->
                        navController.navigate(Routes.chat(ChatMode.WEAKPOINT, childId, weakPointId))
                    },
                    onExitToParent = { exitKidToParent() },
                )
            }
            composable(Routes.RECORD) {
                RecordScreen(onSaved = { navController.popBackStack() })
            }
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = ChatMode.FREE
                    },
                    navArgument("childId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("refId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                ChatScreen(
                    mode = entry.arguments?.getString("mode") ?: ChatMode.FREE,
                    childId = entry.arguments?.getLong("childId")?.takeIf { it > 0 },
                    refId = entry.arguments?.getLong("refId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CHILDREN) {
                ChildrenScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GUIDE) {
                GuideScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { id -> navController.navigate(Routes.guideArticle(id)) },
                    onRestartTour = {
                        scope.launch {
                            app.container.demoSeeder.seedIfEmpty()
                            app.container.tourController.restart()
                            navController.navigate(Routes.TODAY) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
            composable(
                Routes.GUIDE_ARTICLE,
                arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
            ) { entry ->
                GuideArticleScreen(
                    articleId = entry.arguments?.getString("articleId") ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTerms = { navController.navigate(Routes.agreementReadonly("terms")) },
                    onOpenPrivacy = { navController.navigate(Routes.agreementReadonly("privacy")) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("recordingId") { type = NavType.LongType }),
            ) { entry ->
                RecordingDetailScreen(
                    recordingId = entry.arguments?.getLong("recordingId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EXERCISE,
                arguments = listOf(navArgument("weakPointId") { type = NavType.LongType }),
            ) { entry ->
                val weakPointId = entry.arguments?.getLong("weakPointId") ?: 0L
                WeakPointExerciseScreen(
                    weakPointId = weakPointId,
                    onBack = { navController.popBackStack() },
                    onAskTutor = { childId ->
                        navController.navigate(Routes.chat(ChatMode.EXERCISE, childId, weakPointId))
                    },
                )
            }
        }
    }

    // 新手引导浮层（挖孔高亮 + 提示卡；完成/跳过写 tourDoneV1）
    TourOverlay(
        controller = app.container.tourController,
        currentRoute = currentRoute,
        onNavigate = { route ->
            if (route == Routes.RECORD) {
                navController.navigate(Routes.RECORD) { launchSingleTop = true }
            } else {
                navigateTopLevel(route)
            }
        },
    )

    // 中央麦克风钮的快捷动作面板
    if (showPanel) {
        ActionPanel(
            sheetState = sheetState,
            onDismiss = { showPanel = false },
            onAction = { action ->
                dismissPanel()
                when (action) {
                    PanelAction.CHAT -> navController.navigate(Routes.chat(ChatMode.FREE))
                    PanelAction.RECORD -> navController.navigate(Routes.RECORD)
                    PanelAction.CAMERA -> photoCapture.launchCamera()
                    PanelAction.ALBUM -> photoCapture.launchGallery()
                    PanelAction.AUDIO -> audioPicker.launch(arrayOf("audio/*"))
                }
            },
        )
    }

    // 超过 3 天未备份：启动时提醒，可直接在弹窗内完成备份
    val backupReminder by app.container.backupReminder.collectAsStateWithLifecycle()
    val backupState by app.container.backupController.state.collectAsStateWithLifecycle()
    if (backupReminder) {
        AlertDialog(
            onDismissRequest = {
                if (!backupState.running) app.container.backupReminder.value = false
            },
            title = { Text("很久没备份了") },
            text = {
                Column {
                    Text("距离上次备份已超过 3 天，建议备份一次，防止数据丢失。")
                    backupState.progress?.let { p ->
                        Text(
                            "正在同步 ${p.current}/${p.total}：${p.itemName}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    backupState.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !backupState.running,
                    onClick = {
                        scope.launch {
                            if (app.container.backupController.backupNow()) {
                                app.container.backupReminder.value = false
                            }
                        }
                    },
                ) { Text(if (backupState.running) "备份中…" else "立即备份") }
            },
            dismissButton = {
                TextButton(
                    enabled = !backupState.running,
                    onClick = { app.container.backupReminder.value = false },
                ) { Text("稍后") }
            },
        )
    }
}

/**
 * 自绘底部导航：左右各两个 tab，中央凸起麦克风钮（对齐小程序 custom-tab-bar）。
 * 栏体 64dp，中央钮 58dp 向上凸起 18dp，整体高度预留凸起余量保证凸起部分可点击。
 */
@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onTabSelected: (TopLevelDestination) -> Unit,
    onCenterClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(82.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    topLevelDestinations.take(2).forEach { dest ->
                        TabItem(
                            dest = dest,
                            selected = currentRoute == dest.route,
                            onClick = { onTabSelected(dest) },
                        )
                    }
                    // 中央占位：与凸起麦克风钮等宽，保证四格间距均匀
                    Spacer(Modifier.width(76.dp))
                    topLevelDestinations.drop(2).forEach { dest ->
                        TabItem(
                            dest = dest,
                            selected = currentRoute == dest.route,
                            onClick = { onTabSelected(dest) },
                        )
                    }
                }
            }
            CenterMicButton(
                onClick = onCenterClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .tourTarget(TourPlan.TAG_TABBAR_CENTER),
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 工作端底部导航（对齐小程序 work home 的 workbar）：
 * 左「记录」/ 中央凸起录音钮 / 右「待办」（带未完成数），复用家长端中央麦克风钮样式。
 */
@Composable
private fun WorkNavBar(
    vm: WorkHomeViewModel,
    onRecord: () -> Unit,
) {
    val view by vm.view.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(82.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    WorkNavItem(
                        emoji = "🗂️",
                        label = "记录",
                        selected = view == WorkHomeViewModel.VIEW_LIST,
                        onClick = { vm.switchView(WorkHomeViewModel.VIEW_LIST) },
                    )
                    // 中央占位：与凸起麦克风钮等宽
                    Spacer(Modifier.width(76.dp))
                    WorkNavItem(
                        emoji = "✅",
                        label = if (pendingCount > 0) "待办 $pendingCount" else "待办",
                        selected = view == WorkHomeViewModel.VIEW_TODOS,
                        onClick = { vm.switchView(WorkHomeViewModel.VIEW_TODOS) },
                    )
                }
            }
            CenterMicButton(
                onClick = onRecord,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun RowScope.WorkNavItem(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowScope.TabItem(
    dest: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(if (selected) dest.activeIconRes else dest.iconRes),
                contentDescription = dest.label,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            dest.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            // 未选中 #77867B（暗色 #8A9488）、选中 #2E6B4F（暗色 #8FCEA9）
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// 中央钮渐变（小程序 145deg linear-gradient）
private val MicGradientTop = Color(0xFF3D8266)
private val MicGradientMid = Color(0xFF2E6B4F)
private val MicGradientBottom = Color(0xFF245741)

@Composable
private fun CenterMicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(120),
        label = "micScale",
    )
    val sizePx = with(LocalDensity.current) { 58.dp.toPx() }
    Box(
        modifier = modifier
            .size(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = MicGradientMid,
                spotColor = MicGradientMid,
            )
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to MicGradientTop,
                        0.6f to MicGradientMid,
                        1f to MicGradientBottom,
                    ),
                    start = Offset.Zero,
                    end = Offset(sizePx * 0.5f, sizePx),
                ),
                CircleShape,
            )
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.mic_white),
            contentDescription = "快捷操作",
            modifier = Modifier.size(26.dp),
        )
    }
}

private enum class PanelAction { CHAT, RECORD, CAMERA, ALBUM, AUDIO }

private data class PanelActionItem(
    val action: PanelAction,
    val title: String,
    val desc: String,
    val badgeColor: Color,
    val iconTint: Color,
    @DrawableRes val iconRes: Int,
)

// 徽标配色与小程序 panel-badge 一致（深色主题下不切换，同 wxss）
private val panelActionItems = listOf(
    PanelActionItem(PanelAction.CHAT, "问老师", "AI 苏格拉底式提问辅导", Color(0xFFE9E2F7), Color(0xFF5B3E9E), R.drawable.ic_action_chat),
    PanelActionItem(PanelAction.RECORD, "开始录音", "录下辅导过程，自动分离转写", Color(0xFF2E6B4F), Color.White, R.drawable.mic_white),
    PanelActionItem(PanelAction.CAMERA, "拍错题", "拍一张或多张错题照片", Color(0xFFF8E3B8), Color(0xFF8A6100), R.drawable.ic_action_camera),
    PanelActionItem(PanelAction.ALBUM, "相册选图", "从相册导入错题照片", Color(0xFFD3E7DA), Color(0xFF17271F), R.drawable.ic_action_image),
    PanelActionItem(PanelAction.AUDIO, "导入音频", "从文件中选辅导录音", Color(0xFFE4EAE2), Color(0xFF46524A), R.drawable.ic_action_audio),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPanel(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onAction: (PanelAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Text(
            "记录孩子的这一刻",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        panelActionItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAction(item.action) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(item.badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(item.iconTint),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
