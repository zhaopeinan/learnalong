package com.example.asr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Forest40,
    onPrimary = Color.White,
    primaryContainer = Forest90,
    onPrimaryContainer = Forest10,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Bone98,
    onBackground = Ink10,
    surface = Color.White,
    onSurface = Ink10,
    surfaceVariant = Bone90,
    onSurfaceVariant = Ink30,
    surfaceBright = Bone98,
    surfaceDim = Bone85,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Bone95,
    surfaceContainer = Bone92,
    surfaceContainerHigh = Bone90,
    surfaceContainerHighest = Bone85,
    outline = Ink50,
    outlineVariant = Bone85,
    inverseSurface = Pine600,
    inverseOnSurface = Bone95,
    inversePrimary = Forest80,
    scrim = Color.Black,
)

private val DarkColorScheme = darkColorScheme(
    primary = Forest80,
    onPrimary = Forest20,
    primaryContainer = Forest30,
    onPrimaryContainer = Forest90,
    secondary = Sage80,
    onSecondary = Sage20,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Pine900,
    onBackground = Bone94,
    surface = Pine900,
    onSurface = Bone94,
    surfaceVariant = Pine500,
    onSurfaceVariant = Bone80,
    surfaceBright = Pine600,
    surfaceDim = Pine900,
    surfaceContainerLowest = Pine950,
    surfaceContainerLow = Pine850,
    surfaceContainer = Pine800,
    surfaceContainerHigh = Pine700,
    surfaceContainerHighest = Pine600,
    outline = Bone60,
    outlineVariant = Pine500,
    inverseSurface = Bone94,
    inverseOnSurface = Pine700,
    inversePrimary = Forest40,
    scrim = Color.Black,
)

@Composable
fun ASRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 品牌色板固定，不使用动态取色
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 主题切换后立即校正状态栏/导航栏图标明暗（enableEdgeToEdge 只在启动时设置一次）
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
