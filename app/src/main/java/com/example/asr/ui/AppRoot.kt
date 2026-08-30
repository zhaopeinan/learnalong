package com.example.asr.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.asr.AsrApplication
import com.example.asr.R
import com.example.asr.ui.backup.BackupScreen
import com.example.asr.ui.children.ChildrenScreen
import com.example.asr.ui.detail.RecordingDetailScreen
import com.example.asr.ui.mine.MineScreen
import com.example.asr.ui.record.RecordScreen
import com.example.asr.ui.recordings.RecordingsScreen
import com.example.asr.ui.settings.SettingsScreen
import com.example.asr.ui.today.TodayScreen
import com.example.asr.ui.weakpoints.WeakPointExerciseScreen
import com.example.asr.ui.weakpoints.WeakPointsScreen
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
)

// 图标来自 better-icons 检索的 Material Design Icons（mdi）
private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.TODAY, "今日", R.drawable.ic_nav_today),
    TopLevelDestination(Routes.RECORDINGS, "记录", R.drawable.ic_nav_records),
    TopLevelDestination(Routes.WEAK_POINTS, "薄弱点", R.drawable.ic_nav_weakpoints),
    TopLevelDestination(Routes.MINE, "我的", R.drawable.ic_nav_mine),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    androidx.compose.ui.graphics.vector.ImageVector.vectorResource(dest.iconRes),
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            // 只消费底部 inset（导航栏/手势条）；顶部留给各页面顶栏自己延伸到状态栏，
            // 让品牌色与信号栏融为一体
            modifier = Modifier.padding(
                PaddingValues(bottom = innerPadding.calculateBottomPadding())
            ),
        ) {
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
                )
            }
            composable(Routes.RECORD) {
                RecordScreen(onSaved = { navController.popBackStack() })
            }
            composable(Routes.CHILDREN) {
                ChildrenScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
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
                WeakPointExerciseScreen(
                    weakPointId = entry.arguments?.getLong("weakPointId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // 超过 3 天未备份：启动时提醒，可直接在弹窗内完成备份
    val app = LocalContext.current.applicationContext as AsrApplication
    val backupReminder by app.container.backupReminder.collectAsStateWithLifecycle()
    val backupState by app.container.backupController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
