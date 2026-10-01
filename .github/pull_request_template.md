## 改了什么 · What

<!-- 一句话说清这个 PR 做了什么 -->

## 为什么 · Why

<!-- 关联 Issue：Closes #123 -->

## 怎么验证的 · How verified

- [ ] `./gradlew assembleDebug` 通过
- [ ] `./gradlew testDebugUnitTest` 通过
- [ ] 在真机 / 模拟器上手动验证（说明机型与路径）

<!-- 贴命令输出或截图 -->

## 清单 · Checklist

- [ ] 已更新 `CHANGELOG.md`（面向用户的改动）
- [ ] 若涉及数据库实体变更，已新增 Room 迁移（未使用破坏性迁移）
- [ ] 若涉及第三方服务，已检查隐私文案（`domain/LegalText.kt`）
- [ ] 纯逻辑改动已补 / 已更新 `app/src/test/` 下的单元测试
- [ ] 未提交任何 API Key、口令或本机配置
