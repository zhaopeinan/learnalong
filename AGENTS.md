# LearnAlong · 伴学记

本地优先（local-first）的 Android 家庭教育工具：录音转写（带说话人分离）→ 薄弱点分析 →
艾宾浩斯间隔复习 → 孩子端 AI 语音辅导。

- 应用名：伴学记 / LearnAlong
- 包名：`com.example.asr`
- 架构总览见 `IMPLEMENTATION_NOTES.md`（模块结构、数据层、外部通道、备份约定、已知限制）
- 版本变更见根目录 `CHANGELOG.md`

## 工程结构

```
app/src/main/java/com/example/asr/
├── AsrApplication.kt   # AppContainer（手写服务定位，不引 Hilt）+ 启动自动备份/提醒
├── MainActivity.kt     # 单 Activity 入口；分享导入（ACTION_SEND audio/*）
├── audio/              # 录音、前台服务（600s 分段续录）、无损切块、导入
├── data/
│   ├── local/          # Room v11（16 表）+ 迁移、实体、DAO、媒体存储、DemoSeeder
│   ├── remote/         # SiliconFlow（转写/chat/vision）、MiniMax（TTS/声音复刻）、解析器
│   ├── repository/     # Recording / Tutor / Chat / Child / Work
│   ├── settings/       # SettingsStore（DataStore）
│   └── sync/           # WebDavClient、SyncManager、BackupController
├── domain/             # 纯 Kotlin 逻辑（可单测）：艾宾浩斯排期、各 Prompt、家长锁、积分、清理规则
├── media/              # PhotoImporter、SpeechSynthesizer（MiniMax TTS）、TtsPlayer
├── worker/             # DailyReviewWorker、NotificationHelper
└── ui/                 # Compose 页面 + components/ + theme/（单 Activity + NavHost）
```

## 构建与验证

改动 `app/` 后**必跑**：

```bash
./gradlew assembleDebug && ./gradlew testDebugUnitTest
```

CI（`.github/workflows/android-ci.yml`）会在 PR 上重复执行同样两条命令，未通过无法合并。

## 协作约定（每次提交必守）

1. **提交代码的同时更新根目录 `CHANGELOG.md`**：新改动追加到顶部「未发布」一节，一句话说清面向用户的改动；发布时把「未发布」改为正式版本号。
2. git 提交信息用 conventional commits 风格（feat/fix/docs/chore），允许中文描述。
3. 纯逻辑改动请补 `app/src/test/` 下的单元测试（现有 19 个测试覆盖排期、积分、家长锁、切块、解析器等）。
4. 数据库实体变更必须新增 Room 迁移，迁移链统一写在 `data/local/AppDatabase.kt`，不要用破坏性迁移。
5. 第三方服务变更（ASR / LLM / VLM / TTS / 备份）时，同步检查 `domain/LegalText.kt` 的隐私政策第 3 章是否需要更新。
6. **不得**在应用内文案、注释或配置中写入真实 API Key、口令或开发者个人联系方式（QQ/邮箱）。
7. 不提交 `local.properties`、签名文件、`.idea/` 下的本机配置。

## 对外协作

- 贡献流程见 `CONTRIBUTING.md`
- 界面预览截图放在 `docs/screenshots/`，README 头图放在 `docs/images/hero.png`
