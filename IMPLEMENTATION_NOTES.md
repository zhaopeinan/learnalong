# 伴学记 Android 实现说明

微信小程序「课后有光」的 Android 移植版（应用名「伴学记」）：Jetpack Compose 单 Activity（`MainActivity` + `ui/AppRoot.kt` 内的 NavHost），包名 `com.example.asr`，minSdk 见 `app/build.gradle.kts`。功能与交互逐页对齐小程序，页面路由集中在 `ui/Routes.kt`。

## 模块结构

```
app/src/main/java/com/example/asr/
├── AsrApplication.kt          # Application：手写服务定位容器 AppContainer（不引 Hilt）+ 启动自动备份/提醒
├── MainActivity.kt            # 单 Activity 入口；分享导入（ACTION_SEND audio/*）→ pendingImport
├── audio/
│   ├── AudioRecorder.kt       # MediaRecorder 封装（AAC .m4a）
│   ├── RecordingService.kt    # 录音前台服务：600s 自动分段续录（rec_<ts>_sN.m4a）、暂停/继续、来电抢占、常驻通知
│   ├── AudioSplitter.kt       # 超 25MB 音频本地无损切块（m4a/mp3/wav/aac，按时间偏移）
│   ├── AudioImporter.kt       # 外部分享/文件导入：格式嗅探、转封装、存储配额检查
│   └── TestAudio.kt           # 设置页「测试转写」用内置样本
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt     # Room v11，16 张表（见下）；含全部迁移
│   │   ├── entity/Entities.kt # 全部实体 + 联查投影（ReviewTaskWithWeakPoint 等）
│   │   ├── dao/Daos.kt        # 各表 DAO
│   │   ├── MediaStorage.kt    # 媒体存储管理：占用统计、音频清理（可先发云端）、孤儿文件清理
│   │   └── DemoSeeder.kt      # 首启空库播种示例孩子/薄弱点（新手引导用）
│   ├── remote/                # SiliconFlowApi（转写+chat+vision）、MiniMaxApi（TTS+声音复刻）、
│   │                          #   NetworkClient（OkHttp/Retrofit）、各 Parser、DebugLog（调试模式失败请求日志）
│   ├── repository/            # Recording / Tutor（分析+复习+出题）/ Chat / Child / Work 五个 Repository
│   ├── settings/SettingsStore.kt  # DataStore：API Key、模型、科目、提醒、清理模式、家长锁、appMode 等
│   └── sync/                  # WebDavClient（坚果云）、SyncManager（backup.json/config.json 备份恢复）、
│                              #   BackupController（共享备份执行器）、NetworkCheck（WiFi 判断）
├── domain/                    # 纯逻辑（可单测）：艾宾浩斯排期、各 LLM Prompt、成长周报、家长锁、
│                              #   孩子激励、存储清理规则、引导/协议文案、音色目录等
├── media/                     # PhotoImporter（拍照/相册落盘）、SpeechSynthesizer（MiniMax TTS）、TtsPlayer
├── worker/                    # DailyReviewWorker（每日复习提醒）、NotificationHelper
└── ui/
    ├── Routes.kt / AppRoot.kt # 路由表 + 三端底部栏（家长 4 tab + 中央麦克风动作面板 / 孩子 / 工作）
    ├── components/            # 公共组件：卡片、顶栏、骨架屏、练习区块、掌握度曲线、拍照、新手引导浮层
    ├── splash/ today/ recordings/ record/ detail/ weakpoints/（含练习页） mine/
    ├── children/ settings/ backup/ agreement/ about/ guide/（含图文教程）
    ├── chat/                  # 问老师：按住说话（左滑取消/右滑转文字）、拍照提问、TTS 播报
    ├── kid/                   # 孩子端：我的闯关、小怪兽图鉴、星星、家长锁、kid-nav
    ├── work/                  # 工作端：会议/谈话/通话 → 转写 → AI 纪要 + 待办
    ├── theme/                 # Compose 主题（Color.kt 不改）
    └── util/Formatters.kt
```

## 数据层（Room v11，16 张表）

`children`、`recordings`（含 segments 分段 JSON、polishedText、audioRemoved/audioBackedUp）、
`transcript_segments`、`weak_points`（含 exerciseCache 练习缓存）、`review_tasks`、
`mastery_history`（掌握度快照）、`recording_photos`（错题照片）、`chat_sessions`、
`chat_messages`、`kid_stars`、`work_recordings`、`work_todos`，以及积分乐园四张表：
`kid_points`（当前积分）、`point_tasks`（加分任务）、`point_goals`（兑换目标）、
`point_records`（积分流水）。迁移链 v1→v11 全部在 `AppDatabase.kt`；
实体带 `@Serializable` 直接用于备份快照。

## 外部通道

- **SiliconFlow**（`data/remote/SiliconFlowApi.kt`）：
  - ASR：`/audio/transcriptions`，verbose_json，说话人分离；`TranscriptParser` 宽松解析可换实现；
  - LLM：`/chat/completions`（润色/薄弱点分析/查重/出题/换一题/苏格拉底对话/工作纪要）；
  - VLM：同端点 vision 请求（错题照片分析、孩子拍照提问的图片描述）。
- **MiniMax**（`data/remote/MiniMaxApi.kt`）：TTS 合成（AI 播报、孩子端「读出来」）与家长声音复刻
  （上传样本 → 复刻 → 入库音色）；孩子可按人配置音色（`children.voiceId`）。
- **坚果云 WebDAV**（`data/sync/`）：`ASRTutor/backup.json`（数据快照）+ `config.json`（配置快照）
  + `recordings/` `photos/` 媒体文件；增量比对（同名同大小跳过），恢复按主键 REPLACE upsert。

## 录音与转写管线

录音走前台服务 `RecordingService`：切后台/锁屏不中断，满 600s（`SEGMENT_DURATION_SEC`，与小程序一致）
自动无缝分段续录，段文件写入 `recordings.segments`（JSON 数组，首段与 filePath 相同）。
转写时逐段上传，时间偏移 = 段序 × 600s；单文件超 25MB 再由 `AudioSplitter` 无损切块，
块内偏移精确叠加。播放时按 `floor(startSec / 600)` 定位分段文件再换算段内 seek 偏移。

## 三端模式

`appMode`（DataStore）：`parent`（默认，4 tab + 动作面板）、`kid`（孩子端闯关，进出需家长锁 PIN）、
`work`（工作端，记录/待办双视图 + 中央录音钮）。`AppRoot` 按模式切换底部栏与导航。

## 备份互通约定（与小程序同一份云端数据）

- `backup.json` 字段名与实体字段一致，`ignoreUnknownKeys + 默认值` 容忍两端版本差；
- 辅导录音的 `audioRemoved=true` 记录恢复时不拉回音频（已主动清理的不再占空间）；
- **chat_sessions / chat_messages / kid_stars 不纳入备份**：小程序 ExportData 本就不含这两类
  （会话为一次性辅导上下文、星星为本地激励），Android 端有意对齐，不补齐；
- 积分乐园四张表（kid_points / point_tasks / point_goals / point_records）为 Android 原生功能，
  同样不纳入备份（小程序无对应数据）；
- 工作端录音/待办在 v1.0.14 起纳入备份，早于该版本的小程序备份没有这两个字段，恢复时按缺省空表处理。

## 已知限制

1. 录音常驻通知无「停止」动作按钮（只能点通知回录音页停止），低优先级遗留；
2. 转写/分析/润色等长任务在应用被杀后不续跑（小程序同），需回详情页手动重试；
3. 每日复习提醒依赖 WorkManager 周期任务，分钟级漂移属系统行为；
4. MiniMax TTS 播报链接 24h 过期，过期后重播会重新合成（chat_messages.audioUrl 仅作缓存）。
