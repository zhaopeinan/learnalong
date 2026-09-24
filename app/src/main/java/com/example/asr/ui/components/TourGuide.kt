package com.example.asr.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.AsrApplication
import com.example.asr.data.settings.SettingsStore
import com.example.asr.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 新手引导步骤（文案与小程序 utils/tour.ts TOUR_STEPS 逐字对齐；targetTag 对应小程序选择器） */
data class TourStep(
    /** 所属页面路由 */
    val route: String,
    /** 高亮目标 tag（null = 居中弹窗，不高亮） */
    val targetTag: String?,
    val title: String,
    val desc: String,
)

object TourPlan {
    const val TAG_TABBAR_CENTER = "tabbar_center"
    const val TAG_TODAY_CARD = "today_card"
    const val TAG_RECORD_STAGE = "record_stage"
    const val TAG_REC_CARD = "rec_card"
    const val TAG_WP_CARD = "wp_card"
    const val TAG_MENU_GUIDE = "menu_guide"

    val STEPS = listOf(
        TourStep(
            route = Routes.TODAY,
            targetTag = null,
            title = "欢迎来到伴学记",
            desc = "录下你辅导孩子的过程，AI 自动分离人声、转写内容、找出孩子的薄弱点，并按艾宾浩斯曲线安排复习。花 1 分钟，带你完整走一遍。",
        ),
        TourStep(
            route = Routes.TODAY,
            targetTag = TAG_TODAY_CARD,
            title = "今日复习",
            desc = "每天打开「今日」，这里会按艾宾浩斯记忆曲线列出今天该复习的薄弱点，点开就能直接带孩子练。",
        ),
        TourStep(
            route = Routes.TODAY,
            targetTag = TAG_TABBAR_CENTER,
            title = "录下辅导过程",
            desc = "点底部中央的绿色麦克风，录下你给孩子讲题的过程。也可以点「下一步」，跟着引导进录音页看看。",
        ),
        TourStep(
            route = Routes.RECORD,
            targetTag = TAG_RECORD_STAGE,
            title = "开始一次真实录音",
            desc = "先选孩子和科目，再点中央大圆钮开始录音；讲完后点「结束」自动保存。录音只用手机麦克风即可。",
        ),
        TourStep(
            route = Routes.RECORDINGS,
            targetTag = TAG_REC_CARD,
            title = "记录与 AI 分析",
            desc = "录音都保存在「记录」里。点进一条记录：转写文稿、拍错题照片，并让 AI 分析出孩子的薄弱点。",
        ),
        TourStep(
            route = Routes.WEAK_POINTS,
            targetTag = TAG_WP_CARD,
            title = "薄弱点自动汇总",
            desc = "分析出的薄弱知识点会汇总到这里，掌握度一目了然，并自动生成「今日」里的复习任务。",
        ),
        TourStep(
            route = Routes.MINE,
            targetTag = TAG_MENU_GUIDE,
            title = "随时可以重看",
            desc = "忘记用法时，来「我的 → 使用指南」查看图文教程，也可以重新体验本引导。刚才看到的「示例·小明」是演示数据，不需要可以在薄弱点页删除。去录下第一次真实的辅导吧！",
        ),
    )
}

/**
 * 新手引导控制器（对应小程序 tour.ts + components/tour-guide）：
 * 首次进「今日」自动开始；步骤跟随路由；高亮目标由各页面通过 tourTarget(tag) 上报。
 */
class TourController(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {

    var active by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set

    /** 各页面注册的高亮目标位置（窗口坐标） */
    val targets = mutableStateMapOf<String, Rect>()

    private var finishedLoaded = false
    private var finished = false

    val currentStep: TourStep? get() = TourPlan.STEPS.getOrNull(index)

    init {
        scope.launch(Dispatchers.IO) {
            finished = settingsStore.settings.first().tourDoneV1
            finishedLoaded = true
        }
    }

    /** 页面进入时调用：未看过引导则自动开始（今日页）；引导中自动跟进用户跳转 */
    fun onRouteShown(route: String?) {
        route ?: return
        if (!finishedLoaded) return
        if (!active) {
            if (!finished && route == Routes.TODAY) {
                active = true
                index = 0
            }
            return
        }
        val cur = currentStep ?: return
        if (cur.route == route) return
        val next = TourPlan.STEPS.getOrNull(index + 1)
        if (next != null && next.route == route) index += 1
    }

    /** 下一步；返回需要跳转的路由（同页则返回 null；已结束返回 null） */
    fun advance(): String? {
        if (index >= TourPlan.STEPS.size - 1) {
            finish()
            return null
        }
        index += 1
        return currentStep?.route
    }

    fun skip() = finish()

    /** 手动重新开始（使用指南页入口） */
    fun restart() {
        active = true
        index = 0
    }

    /** 跳过或完成：写入标记，不再自动出现 */
    private fun finish() {
        active = false
        finished = true
        scope.launch(Dispatchers.IO) { settingsStore.setTourDoneV1(true) }
    }
}

/**
 * 注册新手引导高亮目标（窗口坐标上报，页面销毁时移除）。
 * 与小程序 class 选择器对应：today_card / rec_card / wp_card / menu_guide / record_stage / tabbar_center。
 */
fun Modifier.tourTarget(tag: String): Modifier = composed {
    val app = LocalContext.current.applicationContext as? AsrApplication
        ?: return@composed this
    val controller = app.container.tourController
    DisposableEffect(tag) {
        onDispose { controller.targets.remove(tag) }
    }
    Modifier.onGloballyPositioned { controller.targets[tag] = it.boundsInWindow() }
}

/** 引导浮层：挖孔蒙版 + 呼吸光圈 + 底部提示卡（对齐小程序 tour-guide 组件） */
@Composable
fun TourOverlay(
    controller: TourController,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val step = controller.currentStep ?: return
    if (!controller.active) return
    if (step.route != currentRoute) return

    val targetRect = step.targetTag?.let { controller.targets[it] }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 拦截点击与滑动穿透（对齐小程序 catchtap/catchtouchmove）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 挖孔蒙版
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mask = Color(0xB8141C18) // rgba(20, 28, 24, 0.72)
            if (targetRect != null) {
                val pad = 8.dp.toPx()
                val r = RoundRect(
                    left = (targetRect.left - pad).coerceAtLeast(0f),
                    top = (targetRect.top - pad).coerceAtLeast(0f),
                    right = (targetRect.right + pad).coerceAtMost(size.width),
                    bottom = (targetRect.bottom + pad).coerceAtMost(size.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
                clipPath(Path().apply { addRoundRect(r) }, clipOp = ClipOp.Difference) {
                    drawRect(mask)
                }
            } else {
                drawRect(mask)
            }
        }

        // 呼吸光圈
        if (targetRect != null) {
            val transition = rememberInfiniteTransition(label = "tourPulse")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "tourPulseScale",
            )
            val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulse),
            ) {
                val r = RoundRect(
                    left = (targetRect.left - pad).coerceAtLeast(0f),
                    top = (targetRect.top - pad).coerceAtLeast(0f),
                    right = (targetRect.right + pad).coerceAtMost(size.width),
                    bottom = (targetRect.bottom + pad).coerceAtMost(size.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
                drawRoundRectOutline(r)
            }
        }

        // 提示卡：停靠底部拇指区
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                Text(
                    "${controller.index + 1} / ${TourPlan.STEPS.size}",
                    fontSize = 11.sp,
                    color = Color(0xFF9AA8A0),
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    step.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E2B24),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    step.desc,
                    fontSize = 14.sp,
                    color = Color(0xFF5A6A60),
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { controller.skip() }) {
                        Text("跳过", color = Color(0xFF9AA8A0))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val route = controller.advance()
                            if (route != null && route != currentRoute) onNavigate(route)
                        },
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            when {
                                controller.index == 0 -> "开始体验"
                                controller.index == TourPlan.STEPS.size - 1 -> "完成，去体验吧"
                                else -> "下一步"
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectOutline(r: RoundRect) {
    drawPath(
        Path().apply { addRoundRect(r) },
        color = Color.White.copy(alpha = 0.95f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )
}
