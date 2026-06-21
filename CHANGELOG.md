# PiecesOfLife 优化与更新日志

> 用于记录 v2.0 之后的所有 Bug 修复、小功能新增、性能优化和系统调整。
>
> 格式说明：
> - **每次更新一行**，按日期倒序排列
> - 严重 Bug 使用 `🔴` 前缀，小功能使用 `🟢`，优化使用 `🔧`
> - 涉及多个文件时在括号中标注关键文件

---

## [2.1.0] — 2026-06-22

### 新增
- 🟢 通知系统（任务临期提醒，`QuestReminderReceiver.kt` + `QuestNotificationScheduler`）
  - 基于 AlarmManager + `setExactAndAllowWhileIdle`，真机关闭电池优化后正常触发
  - APP 内事件驱动（NewDayChecker / AppSetting / PieceOfLifeApp），不依赖链式续期
  - 通知点击打开 APP 主页面
  - 完整 Logcat 调试日志（TAG=QuestReminder）
- 🟢 编辑日志时光标自动移到末尾（`LogEditorActivity.kt`）
- 🟢 APP 设置页版本号显示 v2.1.0.0622（`activity_app_setting.xml` + `strings.xml`）

### 修复
- 🔴 重复任务克隆后 flag0 被错误还原（`NewDayChecker.kt` — `+-100` → `+-50`）
- 🔴 其它次要BUG

### 优化
- 🔧 通知调度改为 APP 内事件驱动，去除链式续期，消除 DataStore 在 BroadcastReceiver 中不可靠的问题（`QuestReminderReceiver.kt`）
- 🔧 新一天检测改为 `onResume` + `btnTool1` 触发，覆盖熬夜跨天场景（`MainActivity.kt`）

---

## [2.0.1] — 2026-06-01

### 优化
- 🔧 非当天的历史页面不再展示未完成任务（仅 `focusDate = Today` 时显示）（`MainViewModel.kt`）
- 🔧 HINT/DEBUG 日志过期后从软删除改为硬删除，不再保留 14 天（`NewDayChecker.kt`）
- 🔧 HINT 日志文字颜色改为 `#C8C8C8` 深白色，与普通日志区分（`SpanTextBuilder.kt`）

### 新增
- 🟢 技能等级提升时自动创建 HINT 通知日志（`LogItemChange.kt` + `LogEditorActivity.kt` + `LogInfoActivity.kt` + `MainViewModel.kt`）
  - 日志编辑保存时检测（排除还原步骤）
  - 任务完成时检测
  - 任务冲正时检测
  - 删除时不检测

---

## 更新说明

### 如何添加新条目

```markdown
## [版本号] — 日期

### 修复
- 🔴 修复的具体问题描述（`涉及文件.kt`）

### 新增
- 🟢 新功能描述（`涉及文件.kt`）

### 优化
- 🔧 优化内容描述（`涉及文件.kt`）
```

### 版本号规则
- **主版本号**：重大重构或架构调整时递增（如 3.0）
- **次版本号**：新功能或大规模变更时递增（如 2.1）
- **补丁号**：Bug 修复或小优化时递增（如 2.0.1）
