# 实现说明（亲子辅导记录 App）

## 文件结构概览

```
app/src/main/java/com/example/asr/
├── AsrApplication.kt            # Application：服务定位容器 AppContainer + WorkManager 排程
├── MainActivity.kt              # 导航宿主入口；申请 POST_NOTIFICATIONS
├── audio/AudioRecorder.kt       # MediaRecorder 封装（AAC .m4a → filesDir/recordings/）
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room 数据库（5 表）
│   │   ├── entity/Entities.kt   # Child / Recording / TranscriptSegment / WeakPoint / ReviewTask
│   │   └── dao/Daos.kt          # 5 个 DAO（含联查投影 ReviewTaskWithWeakPoint / RecordingWithChild）
│   ├── settings/SettingsStore.kt        # DataStore：apiKey / baseUrl / asrModel / llmModel / 提醒时间
│   ├── remote/
│   │   ├── NetworkClient.kt             # OkHttp + Retrofit 构建（按当前 baseUrl）
│   │   ├── SiliconFlowApi.kt            # transcriptions + chat/completions
│   │   ├── dto/ChatDtos.kt              # chat 请求/响应 + WeakPointResult
│   │   ├── TranscriptParser.kt          # 转写解析接口 + verbose_json 默认实现（宽松字段匹配）
│   │   └── AnalysisResultParser.kt      # LLM 输出 JSON 解析（剥离 markdown 围栏）
│   └── repository/              # ChildRepository / RecordingRepository（转写）/ TutorRepository（分析+复习）
├── domain/
│   ├── EbbinghausScheduler.kt   # 艾宾浩斯排期纯函数（间隔 1/2/4/7/15/30 天）
│   └── AnalysisPrompt.kt        # 薄弱点分析 prompt 模板
├── worker/
│   ├── DailyReviewWorker.kt     # 每日到期任务统计 → 通知
│   └── NotificationHelper.kt    # 通知渠道 + 构建
└── ui/
    ├── Routes.kt / AppRoot.kt   # 导航 + 底部导航栏（今日/记录/薄弱点/我的）
    ├── util/Formatters.kt       # SimpleDateFormat 日期/时长格式化（未引入 desugaring）
    ├── today/                   # 今日任务页（已掌握 / 仍薄弱）
    ├── record/                  # 录音页（权限、计时、孩子+科目选择）
    ├── recordings/              # 记录列表（孩子/科目筛选、状态标签）
    ├── detail/                  # 记录详情（转写分色、说话人标注、片段播放、分析触发）
    ├── weakpoints/              # 薄弱点库（筛选、掌握度、下次复习）
    ├── children/                # 孩子管理
    ├── settings/                # 设置页
    └── theme/                   # 模板原有文件，未改动
app/src/test/java/com/example/asr/
├── EbbinghausSchedulerTest.kt   # 排期纯函数单测
└── ParserTest.kt                # 转写/分析解析容错单测
```

## 已知待验证点

1. **ASR 返回格式（最重要）**：`XingChenAGI/XingChenASR-Diarize-V3.0` 的 verbose_json 响应结构未公开验证。
   `VerboseJsonTranscriptParser` 按 Whisper verbose_json 风格解析（segments 数组 + speaker/start/end/text），
   并做了宽松字段匹配（speaker/spk/role、start/begin、end/stop）与降级（无 segments → 整段 text，speaker=UNKNOWN；
   非 JSON → 原始文本整段保留）。**接入真实 API 后第一件事：curl 上传测试音频确认结构**，不符时只需改/换一个
   `TranscriptParser` 实现并在 `RecordingRepository` 构造处注入。
2. **speaker 字段取值形式未知**：可能是 "SPEAKER_00"/"1"/"家长" 等，UI 一律原样显示 speakerLabel 并由用户指派角色。
3. **依赖版本未编译验证**：room 2.7.2 / navigation-compose 2.9.0 / lifecycle 2.9.4 / workmanager 2.10.0 /
   ksp 2.2.10-2.0.2 与 AGP 9.3.0 的兼容性需首次 `./gradlew assembleDebug` 验证。
4. 录音上传进度条未做（当前为不定态 loading）；单文件 50MB/1 小时限制未做前置校验。
5. WorkManager 周期任务的最小间隔为 15 分钟精度量级，提醒时间可能有分钟级漂移（系统行为，非 bug）。

## 接入真实 API 的步骤

1. 设置页填入 SiliconFlow API Key（其余默认值已可用）。
2. 用 curl 验证转写返回结构（参考方案文档第 1 步），必要时调整 `VerboseJsonTranscriptParser`。
3. 真机跑通链路：添加孩子 → 录音 → 记录详情点「开始转写」→ 标注说话人角色 →「分析薄弱点」→ 次日今日任务页复习。
4. 如 LLM 输出不稳定，可在 `AnalysisPrompt` 里加 few-shot 示例或改用 `response_format={"type":"json_object"}`。
