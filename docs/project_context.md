# PiecesOfLife — 项目上下文快照

> 最后更新：2026-09-20 | 每周计划视图完成 + 任务提醒修复，v2.2.0 发布

---

## 一、项目概览

Android 个人日志/任务管理 APP，纯 Kotlin，pixel-art 像素风。

| 层面 | 选型 |
|------|------|
| 数据层 | Room（log_data 表）+ DataStore（偏好设置） |
| UI | 传统 Activity + ViewBinding + RecyclerView |
| 弹窗 | PixelDialog（7 按钮模式）+ SelectorDialog（选择器） |
| 精灵系统 | SpriteDef + SpriteLoader（9-patch 背景、B16/B24/B32/B72 按钮） |
| 构建 | Gradle KTS，minSdk 26，targetSdk 35，versionName 2.2.0 |

---

## 二、已完成（第四阶段 — 核心编辑器完整实现）

**Phase 1~4 全部完成，零诊断错误。**

### 核心文件与职责

| 文件 | 职责 |
|------|------|
| `LogEditorActivity.kt` | 日志编辑器，CRUD + 格式工具栏 + 触发器插入 + itemsJson 属性生效 |
| `NoteContentEditText.kt` | 自定义富文本 EditText，10 步撤销/重做 + 防抖快照 + `@@` `$$` 触发器检测 |
| `SelectorDialog.kt` | 选择器弹窗，支持 `setItems`/`setItemsWithRemark`/`setCenteredItems`/`setColorItems`，链式调用 |
| `LogItemChange.kt` | itemsJson 解析/序列化，7 种 ApplyMode（ALL/DELETE_ALL/DEFAULT_SUCCESS/DEFAULT_FAILURE/PENALTY_SUCCESS/PENALTY_FAILURE/UNFINISH_QUEST） |
| `LogRepository.kt` + `LogDao.kt` | CRUD + `insertOrUpdate`/`softDeleteById`/`permanentlyDeleteById`/`completeQuest` |
| `LogViewHolder.kt` / `QuestViewHolder.kt` / `LogAdapter.kt` | RecyclerView 三类型，长按编辑回调 |
| `MainViewModel.kt` | `completeQuestSync`（属性生效）+ 非调试模式过滤 DEBUG/ERROR |
| `SpanTextBuilder.kt` | 主页文本渲染：时间戳格式 + 属性图标替换 + remark 格式恢复 |

### 关键设计决策

- `NEW_LOG_DEFAULT = -1L`（新建日志）、`NEW_LOG_QUEST = -2L`（新建任务）
- 标题动态拼接：`(新建/编辑) + (日志/任务/提示)`
- 保存时属性生效流程：`apply(oldDeltas, mode.inverse())` 还原 → `apply(newDeltas, mode)` 应用
- 未完成任务使用 `UNFINISH_QUEST` 模式（delta=0，自逆），编辑时不触发属性变更
- 软删除还原属性，已删除日志二次点击弹窗永久删除

---

## 三、第六阶段（Hotfix）编码进度

### 关键 Bug 修复

| 问题 | 原因 | 文件 |
|:-----|:-----|:------|
| 软删除日志无法编辑 | `LogDao.getById` 含 `AND is_deleted = 0` | `LogDao.kt` |
| 图像预览按钮间隙过大 | padding + margin 导致精灵被压缩 | `activity_image_preview.xml` |
| 技能升级检测错误 | `itemsBeforeNewApply` 记录在 `apply(inverse)` 之后 | `LogEditorActivity.kt` |
| 重复任务不克隆 | `completeQuest` 只改 flag0，无下一周期 | `NewDayChecker.kt` |
| 重复任务 LogInfo 显示「是否无期限」 | `QuestType.DAY` 误用设置界面字符串 | `LogInfoActivity.kt` |
| 自动失败任务 buildDate 未更新 | `expiredQuest.copy` 保留原 `buildDate`/`buildTime` | `NewDayChecker.kt` |
| 空内容日志允许保存 | `saveAndFinish` 未拦截空文本 | `LogEditorActivity.kt` |
| 无变更时更新时间被刷新 | `saveAndFinish` 无变更检测 | `LogEditorActivity.kt` |
| 重复克隆后 flag0 还原偏移错误 | `+-100` 应为 `+-50` | `NewDayChecker.kt` |
| 编辑→LogInfo 更改丢失 | 确认回调只保存 DOCUMENT | `LogEditorActivity.kt` |
| 通知真机无法触发 | **隐式 Intent 的广播被 Android 8.0+ 后台执行限制在投递环节丢弃**（v2.1.0 的"改精确闹钟"未真正解决，2026-09-20 才定位到根因） | `QuestReminderReceiver.kt` |

### 通知系统（完整重构）

| 文件 | 职责 |
|------|------|
| `QuestReminderReceiver.kt` | BroadcastReceiver，同步 `runBlocking` 执行 Room 查询并弹通知 |
| `QuestNotificationScheduler` | 调度器，`canScheduleExactAlarms()` 判定 + `setExactAndAllowWhileIdle`/降级 + Intent Extra 传递开关与提醒时间 |
| `PieceOfLifeApp.kt` | 通知渠道创建（`quest_due_reminder`，`IMPORTANCE_HIGH`）+ 启动时调度 |
| `MainViewModel.kt` | `runNewDayCheck` 后刷新提醒闹钟 |
| `AppSettingActivity.kt` | 修改设置后即时调度；打开提醒开关时做精确闹钟授权引导 |
| `AndroidManifest.xml` | `SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS` 权限声明 |

### 通知系统架构（v2.2.0 最终）

```
调度触发点（3 个入口）→ scheduleDailyReminder(context, enabled, time)
  ├── PieceOfLifeApp.onCreate()              ← 首次安装 / 重启后首次打开（重启会清空全部闹钟）
  ├── MainViewModel.runNewDayCheck()         ← 每天首次打开 APP
  └── AppSettingActivity.applyAndLog()        ← 用户修改设置
       ├── Intent 写入 EXTRA_REMINDER_ENABLED + EXTRA_REMINDER_TIME
       ├── 显式组件 Intent(context, QuestReminderReceiver::class.java)   ← 隐式广播会被丢弃（D49）
       └── canScheduleExactAlarms() ? setExactAndAllowWhileIdle : setAndAllowWhileIdle（并打日志）

onReceive(intent)
  ├── intent.getBooleanExtra(EXTRA_REMINDER_ENABLED)  ← 从 Intent 读，不碰 DataStore
  ├── intent.getIntExtra(EXTRA_REMINDER_TIME) → 立即续排次日（D31 修订）
  └── runBlocking(IO) { Room 查询 + 统计 + 弹通知 }
        └── 渠道 quest_due_reminder（IMPORTANCE_HIGH），通知 PRIORITY_HIGH
```

**v2.2.0 修复要点**：① 广播 Intent 必须显式指定组件 —— 隐式广播被 Android 8.0+ 后台执行限制丢弃，症状是"闹钟触发、系统唤醒进程、Receiver 却从不执行"；② 渠道重要级别对**已存在**渠道无效，调级只能换 id；③ Receiver 内续排次日，避免"连续两天不开 APP 断档"；④ 补 `canScheduleExactAlarms()` 检查与授权引导。

### 新一天检测触发点

| 触发点 | 位置 |
|:-------|:------|
| `onResume()` | `MainActivity.kt` — 覆盖首次启动 + 后台切回跨天 |
| `btnTool1` 点击 | `MainActivity.kt` — 用户查看最新日志时 |

**防重复机制**：仅有 `onResume` + `btnTool1` 两个入口，`runNewDayCheck` 内部 `today <= lastDate` 守卫。

---

## 四、第七阶段 — 每周计划视图（v2.2.0）

### 功能定位

以「自然周（周一~周日）」为单位查看与执行任务，入口为工具宝箱第 8 格。**恒为周范围，与全局 `day_group` 解耦**。

### 新增文件

| 文件 | 职责 |
|------|------|
| `ui/QuestViewActivity.kt` | 每周计划主页：周条状态机 + 日期分组列表 + 底栏翻周 + 新增/勾选/长按编辑 |
| `ui/ThreeColorProgressBar.kt` | 三色周进度条自定义 View（`onMeasure` 按精灵尺寸定高） |
| `layout/activity_quest_view.xml` | 顶栏 + `contentPanel`（周条 / 进度条 / 列表同一面板）+ 底栏三按钮 |
| `layout/item_week_cell.xml` | 周条单格（星期 + 日） |
| `layout/item_quest_day_group.xml` | 日期分组：组头（日期 + 相对周标签 + `[+]`）+ 空提示 + 任务容器 |
| `drawable/process_bar_3color.png` | 8×8 × 8 帧：左盖 / 右盖 / 绿 / 红 / 白 / 绿红 / 绿白 / 红白过渡 |

### 页面结构

```
[顶栏]  返回                    每周计划
[contentPanel  共用 9-patch 面板]
  ├── 周条 7 格（今天不特殊着色，选中格用 lightDark 底）
  ├── 三色进度条（整周 完成 / 失败 / 未完成 占比）
  └── 日期分组列表（组 12dp + item 自带 12dp = 左右各 24dp）
[底栏]  上一周 · 重置 · 下一周
```

### 状态机

| 当前 | 操作 | 结果 |
|------|------|------|
| 周视图 | 点周条某天 | 该天的日视图 |
| 日视图 | 再点同一天 | 回周视图 |
| 日视图 | 点另一天 | 切到那天的日视图 |
| 任意 | 底栏任一按钮 / 冲正返回 | 跳到目标周 + 重置为周视图 |

### 条目与新增

- 三态框（B24 34/35/36，显式 `downFrame = 自身`）：点空框弹确认（成功/失败）；已完成/失败整行减淡（0.45）且不可点，冲正走长按进编辑器
- 组内排序：`(未完成在前, buildDate 升序, buildTime 升序)`
- 组头 `[+]` → `LogEditorActivity(NEW_LOG_QUEST)`，经 `EXTRA_QUEST_DEADLINE` 透传截止日；过去日期置灰（避免建完即被 NewDayChecker 判失败）
- 长按条目 → `LogEditorActivity` 编辑；单击文本区无操作
- 完成/失败复用 `MainViewModel.completeQuestSync`（属性生效 + 技能升级检测）

### 渲染与数据

- `SpanTextBuilder.buildDisplayText(..., plainText = true)`：跳时间前缀与 remark 格式，保留属性 emoji 与标签高亮；主页传 false 行为不变
- `LogDao.getQuestsInDateRange`：`change_date` 落在周区间内的 QUEST，自动排除无期限任务（≥ 30000000）
- 进度条与列表**同源同口径**（同一批数据、同样按 `changeDate`），数字必然一致

### 已移除

| 项 | 原因 |
|----|------|
| `ui/WeekPlanActivity.kt`、`layout/activity_week_plan.xml`、顶栏入口、10 个 `week_plan_*` 字符串 | 远距离跳周与月统计使用频率极低，功能由「底栏翻周 + 三色进度条」覆盖 |
| `TimeUtil.getIsoWeek()` | 仅被上述页面使用 |

---

## 五、核心架构（v2.0 最终设计）

### LogType 定义

```kotlin
LogType.ERROR = -2
LogType.DEBUG = -1
LogType.HINT = 0
LogType.DEFAULT = 1
LogType.QUEST = 2
LogType.PICTURE = 3    // 替代 TEMPLATE，图片日志
LogType.DOCUMENT = 4   // 长文/代码文件（统一存为 .md）
```

### 新增职责（LogEditorActivity 三模式）

| 模式 | 内容区 | 底部工具栏 | 说明 |
|:----:|--------|-----------|------|
| `MODE_DEFAULT` | `NoteContentEditText`（提示"请输入文字："） | 撤销/重做/加粗/下划线/删除线/字号/颜色 + 标签/短语/属性 | 现有逻辑不变 |
| `MODE_PICTURE` | `NoteContentEditText`（限制高度+字数，提示"请输入图片描述"，默认填充文件名）<br>下方 `ImageView` 显示缩略图 | 撤销/重做 + 标签/短语/属性（移除格式工具） | 正常解析 itemsJson |
| `MODE_DOCUMENT` | `EditText`（全量加载 .md 文件文本）<br>`RecyclerView`（MD 渲染预览）<br>编辑/预览切换 | [编辑 toggle(btn16 25)] + 标签/短语/属性 | 不保存 itemsJson |

### 新增文件

| 文件 | 职责 |
|------|------|
| `util/ImageUtil.kt` | 文件复制/重命名/缩略图/预览/裁剪/头像 |
| `ui/ImagePreviewActivity.kt` | 全屏 + Matrix 手势 |
| `ui/LogInfoActivity.kt` | 信息展示 + 冲正 + 文件管理 |
| `ui/DataStatsActivity.kt` | 绿墙日历 + 聚合统计 |
| `ui/CalendarHeatmapViewController.kt` | 31 级色阶日历组件 |
| `ui/NumberPickerDialog.kt` | 时间选择器 |
| `ui/ProgressDialog.kt` | 进度条对话框 |
| `util/QuestReminderReceiver.kt` | 通知系统（Receiver + Scheduler） |

### AddonTool 功能面板（7 工具）

| 面板 | ID | 功能 |
|:----:|:--:|------|
| 工具 1 | `toolPanel1` | 新建任务 |
| 工具 2 | `toolPanel2` | 新建文档（空白/导入） |
| 工具 3 | `toolPanel3` | 添加图片 |
| 工具 4 | `toolPanel4` | 随机一下（HINT 日志） |
| 工具 5 | `toolPanel5` | 数据统计（绿墙日历） |
| 工具 6 | `toolPanel6` | 回收站（软删除日志查看） |
| 工具 7 | `toolPanel7` | 属性账单 |
| 工具 8 | `toolPanel8` | 每周计划（v2.2.0） |

### 文件存储策略

```
files/
├── images/
│   ├── {YYYYMMDD}_{hhmmss}_{原始文件名}.jpg         ← 原图（≤8MB）
│   ├── thumb_{YYYYMMDD}_{hhmmss}_{原始文件名}.jpg   ← 缩略图（720px 宽）
│   └── avatar.jpg                                    ← 用户头像
└── documents/
    └── {YYYYMMDD}_{hhmmss}_{原始文件名}.md           ← 纯文本/代码文件（≤512KB）
```

### 主页渲染

| 类型 | 渲染方式 |
|------|---------|
| PICTURE | 时间戳 + 描述文本 + 图片预览（双层 ImageView：内层圆角缩略图 + 外层 9-patch 边框） |
| DOCUMENT | 时间戳 + 描述文本（文件名）+ 特殊 TextView 边框 显示最多 5 行非空预览 + 截断符 |
| QUEST（已超时） | 前缀显示「已超时」红色标记 |

### 导航保存协议

```
btnTool2（信息按钮）→ 检测变更 → 无变更直接打开 LogInfoActivity
                                  → 有变更弹窗确认 → 静默保存 → 打开 LogInfoActivity
LogInfoActivity 返回 → MODE_DOCUMENT：重新加载 .md 文件内容
                     → MODE_PICTURE：从 DB 重载 LogEntity + 从文件系统重载缩略图
```

| 操作 | logText | 文件内容 | 属性变更 | 行为 |
|------|:-------:|:--------:|:--------:|------|
| btnContinue | ✅ 保存 | ✅ 保存 | ✅ 生效 | finish 回主页 |
| btnTool2→LogInfo | ✅ 保存 | ✅ 保存 | ✅ 生效 | 打开 LogInfoActivity |
| LogInfoActivity 返回 | — | — | — | 从 DB/文件重新加载 |

### 通知系统

```
PieceOfLifeApp.onCreate()
  └── 创建通知渠道 quest_due_reminder（IMPORTANCE_HIGH）

调度触发点（3 个入口）→ scheduleDailyReminder(context, enabled, time)
  ├── PieceOfLifeApp.onCreate()              ← 首次安装 / 重启后首次打开（重启会清空全部闹钟）
  ├── MainViewModel.runNewDayCheck()         ← 每天首次打开 APP
  └── AppSettingActivity.applyAndLog()        ← 用户修改设置
       ├── Intent 写入 EXTRA_REMINDER_ENABLED + EXTRA_REMINDER_TIME
       ├── 显式组件 Intent(context, QuestReminderReceiver::class.java)  ← 隐式广播会被丢弃（D49）
       └── canScheduleExactAlarms() ? setExactAndAllowWhileIdle : setAndAllowWhileIdle（并打日志）

QuestReminderReceiver.onReceive()
  ├── intent.getBooleanExtra(EXTRA_REMINDER_ENABLED)  ← 从 Intent 读，不碰 DataStore
  ├── intent.getIntExtra(EXTRA_REMINDER_TIME) → 立即续排次日（D31 修订）
  └── runBlocking(Dispatchers.IO)
        ├── Room 查询今天 + 未来 6 天到期任务
        └── 有到期任务 → NotificationManagerCompat.notify()

通知内容：
  统计今天到期 + 未来 6 天到期任务数
  如果 today=0 且 未来6天=0 → 不发送通知
  点击通知 → getLaunchIntentForPackage() 打开 APP 主页面

调试：adb logcat -s QuestReminder AndroidRuntime
```

### 头像系统

```
UserSetting（头像行）
  ├── iconAvatarPreview（btn16 帧24/25：有/无头像）
  ├── tvAvatarStatus（"点击预览当前头像" / "当前无自定义头像"）
  └── btnAvatarPicker（系统图片选择器 → cropSquareTopLeft → avatar.jpg → HINT 日志）

MainActivity（主页）
  └── FrameLayout { ivAvatar + ivAvatarBorder(background_input2) }
        └── initAvatar() 在 refresh 时自动重新加载 avatar.jpg
```

### 备份系统（含文件）

```
备份包.piecesbackup (ZIP)
├── manifest.json            ← 用户配置 + file_manifest
├── pieces_of_life.db        ← Room 数据库
├── images/                  ← 全部图片文件（含 avatar.jpg）
└── documents/               ← 全部文档文件

导入时记录缺失文件 → 写入 HINT 日志告知用户
```

---

## 六、导航流程图

```
AddonTool (toolPanel3 添加图片)
  → 系统图库选择 → saveLog(PICTURE) → LogEditorActivity(MODE_PICTURE)

AddonTool (toolPanel2 添加长文)
  → 弹窗（新建空白/从本地导入）
    → 新建：创建空 .md → saveLog(DOCUMENT)
    → 导入：文件选择器 → 复制到 documents/ → 提取预览 → saveLog(DOCUMENT)
  → LogEditorActivity(MODE_DOCUMENT)

LogEditorActivity
  ├── btnContinue → 保存文件 + 属性生效 → finish 回主页
  ├── btnTool2 → 检测变更 → 弹窗 → 静默保存 → LogInfoActivity
  └── btnReturn → finish（无操作）
        ↑
LogInfoActivity
  ├── 信息字符串展示
  ├── [QUEST] 冲正按钮 + PixelDialog 确认
  ├── [PICTURE/DOCUMENT] 文件管理区
  │   ├── 重命名 → PixelDialog 输入 → 更新 remark
  │   ├── 覆盖上传 → 文件选择器 → 替换文件 → 重新生成预览
  │   └── 文件导出 → FileProvider + ACTION_SEND
  └── 返回 → LogEditorActivity 重新加载内容

主页 RecyclerView
  ├── PICTURE 条目
  │   ├── 单击图片 → ImagePreviewActivity（全屏查看）
  │   └── 长按 → LogEditorActivity(MODE_PICTURE)
  └── DOCUMENT 条目
        ├── 单击预览框 → LogEditorActivity(MODE_DOCUMENT) 预览模式
        └── 长按 → LogEditorActivity(MODE_DOCUMENT) 编辑模式
```

---

## 七、活跃决策日志（关键条目）

| # | 议题 | 决策 |
|:-:|------|------|
| D01 | 图片存储 | 复制到 APP 私有目录，不依赖 Content URI |
| D02 | 缩略图生成时机 | 用户上传图片时生成（720px 宽） |
| D03 | 文件命名 | `{YYYYMMDD}_{hhmmss}_{原始文件名}.ext`，不含 logId |
| D15 | DOCUMENT LogType | 新增 `DOCUMENT = 4`，与 PICTURE 平级 |
| D17 | 文档大小限制 | 512KB；图片仍为 8MB |
| D18 | 文件存储路径 | 图片 `files/images/`，文档 `files/documents/` |
| D19 | 预览文本存储 | txt/md 前 5 行非空内容，存 `itemsJson` |
| D21 | FileLogActivity | **废弃删除**，功能合并 LogEditorActivity 三模式 + LogInfoActivity |
| D22 | DOCUMENT 主页渲染 | 特殊边框显示 5 行预览 + 截断符 |
| D23 | 纯文本格式限定 | 仅 .txt/.md/.py/.html/.cpp/.js，统一存为 `.md` |
| D25 | LogEditorActivity | 三种 MODE 共用同一个 Activity |
| D26 | 大图/小图模式 | 全局 DataStore 设置，取消单条覆盖 |
| D28 | 导航保存协议 | btnTool2 弹窗确认 → 静默保存 → 打开 LogInfoActivity；返回重载 |
| D30 | LogInfoActivity 文件管理 | 重命名 B24 20/21 / 上传 B24 26/27 / 导出 B24 28/29 |
| D31 | 通知调度 | APP 内事件驱动（Application 启动 / NewDayChecker / AppSetting 三个入口）+ **Receiver 内按 Intent 携带的 `reminder_time` 续排次日**（2026-09-20 修订：原为"不链式续期"，会导致连续两天不开 APP 就断档） |
| D32 | 通知状态传递 | PendingIntent Extra 携带 `reminder_enabled` **+ `reminder_time`**，`onReceive` 不读 DataStore |
| D33 | 精确闹钟 | 先 `canScheduleExactAlarms()` 判定：有权限走 `setExactAndAllowWhileIdle`，无权限走 `setAndAllowWhileIdle` 并打日志；AppSetting 未授权时弹窗引导跳系统「闹钟和提醒」页 |
| D34 | 通知 Receiver 执行方式 | 同步 `runBlocking(IO)`，去掉 `Thread`/`GlobalScope`，避免被系统回收 |
| D35 | 新一天检测触发 | `onResume` + `btnTool1` 点击，移除 `onCreate` 调用，防竞态 |
| D36 | 编辑→LogInfo 未保存处理 | 放弃确认弹窗（`DUAL_IMPORT_CONFIRM`），不保存直接跳转 |
| D37 | `clonedFlag0` 还原偏移 | `+-50`（QuestFlag 已完成/失败与未完成之间的差值） |
| D38 | 每周计划范围 | 恒为自然周（周一~周日），与全局 `day_group` 解耦 |
| D39 | 周视图状态机 | 点某天进日视图 / 再点同一天返回 / 切另一天进那天的日视图 / 任何跨周动作一律重置为周视图 |
| D40 | 新增任务截止日 | = 所在分组的日期（经 `EXTRA_QUEST_DEADLINE` 透传）；过去日期 `[+]` 置灰 |
| D41 | 三态框 | 空/钩/叉为三张**并列状态**精灵（B24 34/35/36），显式 `downFrame = 自身`，用 `click_background` 提供按下反馈 |
| D42 | 条目渲染 | `plainText = true`：跳时间前缀与 remark 格式，保留属性 emoji 与标签高亮 |
| D43 | 组内排序 | `(未完成在前, buildDate 升序, buildTime 升序)` |
| D44 | 三色进度条 | 整周一根，填充在下、左右盖（含透明窗）在上，颜色交界处绘制过渡帧；口径与列表同源 |
| D45 | 面板结构 | 周条与列表合并进同一个 9-patch 面板，避免两个面板边框叠加 |
| D46 | 周计划设置页 | 删除（跨周跳转与月统计价值不足），由底栏翻周 + 三色进度条覆盖 |
| D47 | 任务 `changeTime` | 统一 `235959`（原先编辑保存写入当前时刻） |
| D48 | 周视图排除项 | 不显示无期限任务，也不显示任务类型 |
| D49 | 广播 Intent | **必须显式指定组件**（`Intent(context, Receiver::class.java)`）—— 仅有 action 的隐式广播会被 Android 8.0+ 的后台执行限制在投递环节丢弃；症状是"闹钟触发、系统唤醒进程、Receiver 却从不执行" |
| D50 | PendingIntent 一致性 | `schedule` 与 `cancel` 必须用**同一 Intent 构造**，否则 `cancel` 匹配不到已排的闹钟 |
| D51 | 通知渠道调级 | Android 对**已存在**渠道忽略 `importance` 变更 → 调级只能换 channel id（旧渠道会作为空渠道残留，需手动删除或卸载重装） |
| D52 | `WAKE_LOCK` | 移除声明。唤醒设备由闹钟类型 `RTC_WAKEUP` 保证，仅在真正调用 `PowerManager.newWakeLock()` 时才需要该权限 |

---

## 八、编码风格约束

1. **不添加注释**，除非被要求
2. **不创建不必要的文件**，优先修改现有文件
3. **不创建文档（.md）**，除非明确要求
4. **先读后写**，理解现有代码风格再修改
5. **零诊断错误**，修改后必须验证 `GetDiagnostics`
6. **遵循现有命名规范**：`camelCase` 变量/函数，`PascalCase` 类/文件，`UPPER_CASE` 常量
7. **不引入第三方库**，图片处理使用 `BitmapFactory` + 现有 9-patch 体系
8. **修改前先 `Read` 目标文件**，理解上下文
9. **使用 `SearchCodebase` 优先于 `Grep`**
10. **变更记录日志**：AppSetting/UserSetting 变更需写 `LogType.DEBUG` 日志
