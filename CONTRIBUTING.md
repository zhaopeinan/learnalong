# 贡献指南 · Contributing to LearnAlong

感谢你愿意参与这个项目 🙌 以下约定尽量少，但请尽量遵守。

## 开始之前 · Before you start

- 先在 [Issues](https://github.com/zhaopeinan/learnalong/issues) 里搜一下，避免重复。
- **较大的改动请先开 Issue 讨论**（新功能、重构、更换第三方服务），避免写完才发现方向不一致。
- 修 bug 不必先讨论，直接提 PR，但请说清复现步骤。

## 开发环境 · Development setup

- Android Studio（支持 AGP 9.3）
- JDK 17 或更高
- Android SDK：`compileSdk 37`
- 设备或模拟器：Android 9（API 28）及以上

```bash
git clone https://github.com/zhaopeinan/learnalong.git
cd learnalong
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 提交前必须验证 · Required checks

改动 `app/` 下的任何代码后，**必须**在本地跑通这两条命令再提交：

```bash
./gradlew assembleDebug && ./gradlew testDebugUnitTest
```

CI 会在 PR 上重复执行同样的命令，未通过无法合并。

## 编码约定 · Coding conventions

- **分层**：纯逻辑放进 `domain/`，不要依赖 Android SDK——这样才能被 `app/src/test/` 下的单元测试直接覆盖。
- **状态**：`ViewModel` 暴露 `StateFlow` / `Flow`，不要在 Composable 里直接访问 DAO。
- **数据库**：实体变更必须新增 Room 迁移，迁移链统一写在 `data/local/AppDatabase.kt`，不要用破坏性迁移。
- **网络解析**：新增或修改第三方响应解析时，请同步补 `app/src/test/` 下的解析器测试。
- **依赖注入**：项目刻意不引入 Hilt/Koin，新依赖请在 `AsrApplication.kt` 的 `AppContainer` 中手工组装。
- **注释与文档**：源码注释用中文，`README.md` 保持中英混合风格。

## 提交规范 · Commit convention

使用 [Conventional Commits](https://www.conventionalcommits.org/)，允许中文描述：

```
feat: 新增 xxx 能力
fix: 修复 xxx 场景下的 xxx 问题
docs: 补充 xxx 说明
chore: 升级 xxx 依赖
```

## 必须同步更新的文件 · Keep these in sync

| 改动类型 | 需要同步 |
|---|---|
| 任何面向用户的改动 | 根目录 `CHANGELOG.md`（追加到顶部「未发布」一节） |
| 架构或模块结构变化 | `IMPLEMENTATION_NOTES.md` |
| 第三方服务（ASR / LLM / VLM / TTS / 备份）变化 | 应用内隐私政策文案（`domain/LegalText.kt`） |

## 不要做的事 · Please don't

- 不要在应用内文案、代码注释或配置中写入真实 API Key、账号口令或任何个人联系方式。
- 不要提交 `local.properties`、签名文件、`.idea/` 下的本机配置。
- 不要引入与现有能力重复的重量级依赖。

## 提 PR · Pull request

1. 从 `main` 切出分支，命名如 `feat/xxx`、`fix/xxx`。
2. 一个 PR 只做一件事，描述里写清「改了什么 / 为什么 / 怎么验证的」。
3. 附上验证证据：命令输出、截图或录屏。
4. 关联相关 Issue（`Closes #123`）。

## 安全问题 · Security

如果发现的是**安全或隐私问题**（例如 Key 泄露、越权访问云端数据），请**不要**开公开 Issue，改为在 GitHub 上通过仓库的 Security 页面提交私密报告。
