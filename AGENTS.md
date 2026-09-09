# 课后有光（微信小程序）

记录孩子辅导过程与结果的教育小程序：录音转写（说话人分离）→ 薄弱点分析 → 艾宾浩斯复习任务 + 孩子端 AI 语音辅导。

## 工程结构

- `miniprogram/src/`：源码（TypeScript + SCSS），页面 wxml/json/png 直接改产物目录 `miniprogram/miniprogram/`
- `miniprogram/miniprogram/`：构建产物 + 手写 wxml/wxss 资源
- `miniprogram/screenshot/`：典型页面截图（自动化脚本在 `miniprogram/.tmp/e2e/`，不入库）

## 构建与验证

```bash
cd miniprogram && npm run build && npm run typecheck
```

任何 `src/` 改动后必须跑这条验证通过再提交。

## 协作约定（每次提交必守）

1. **提交代码的同时更新根目录 `CHANGELOG.md`**：新改动追加到顶部「未发布」一节，一句话说清面向用户的改动；发布时把「未发布」改为正式版本号
2. 版本号：`miniprogram/src/utils/version.ts` 的 `APP_VERSION` 随发布递增（界面显示优先读 `wx.getAccountInfoSync()` 的运行包真实版本）
3. git 仓库在根目录，提交用中文 conventional commits 风格（feat/fix/docs/chore）
4. 第三方服务变更（ASR/LLM/VLM/TTS/备份）时，同步检查 `miniprogram/src/utils/legal.ts` 的隐私政策第 3 章是否需要更新
5. 不公开开发者个人联系方式（QQ/邮箱）在任何应用内文案中
