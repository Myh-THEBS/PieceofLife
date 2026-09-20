# 热更新记录

> 记录第五阶段开发过程中插入的 BUG 修复和小功能更新。

---

## 2026-09-20 (v2.2.0.0920)

### fix: 进入「每周计划」页崩溃（x + width must be <= bitmap.width）
- **原因**：三态框取的是 `button_24x24` 的 **34/35/36 帧，恰是图集最末三帧**；而 `SpriteLoader.setButton` 不传 `downFrame` 时会自动取「当前帧 +1」作为按下态 → 画叉帧 36 的按下态取到不存在的 37
- **修改**：`QuestViewHolder.kt` — 三态框显式传 `downFrame = 自身`；并补 `click_background` 保留按下反馈（三张精灵是三种**并列状态**，不是常态/按下对，自动配对在语义上也不成立）

### fix: 冲正后返回「每周计划」，列表与界面不更新
- **原因**：`LogEditorActivity` 收到 `RESULT_REVERSED` 后以 `RESULT_DELETED` 结束，而周视图的 `editorLauncher` 只判断了 `RESULT_OK`
- **修改**：`QuestViewActivity.kt` — 追加判断 `RESULT_DELETED`（该结果码同时覆盖「冲正」与「删除」两条链路，沿用主页既有约定，未新造结果码）

### fix: 周条选中色覆盖面板边框、日期数字底部被裁
- **原因**：9-patch 面板边框厚度固定为 `16px × pixelScale`（= 80px，与屏幕密度无关），而周条高度是硬编码 62dp、格子又 `match_parent` 撑满；同时格子内「星期 + 日期」实际行高约 47dp，超过硬编码的 44dp
- **修改**：`activity_quest_view.xml` + `QuestViewActivity.kt` — 周条与列表合并进同一面板 `contentPanel`（周条不再有独立边框），高度改 `wrap_content`，内边距按边框厚度让位

### fix: 日期行与任务条目左右不对齐
- **原因**：`item_quest_entry.xml` 自带 12dp 内边距，与组容器内边距叠加后与组头错位
- **修改**：`item_quest_day_group.xml` — 横向内边距下移到「组头 / 分割线 / 空提示」，使日期文字、分割线、任务复选框三者左边缘一致（合计 24dp，与设置页同宽）

### fix: 内容区与底栏之间出现空隙
- **原因**：调整面板分隔时误加的 `layout_marginBottom="3dp"`
- **修改**：`activity_quest_view.xml` — 移除该边距，内容区紧贴底栏

### fix: 工具链升级后 kspDebugKotlin 崩溃
- **原因**：Room 2.6.1 处理 Kotlin 2.2 元数据时内部异常（`IllegalStateException: unexpected jvm signature V`）。以 `git stash` 还原到改动前的原始 DAO 后复现，确认与业务代码无关
- **修改**：`gradle/libs.versions.toml` — Room 2.6.1 → 2.8.5（独立提交，与功能提交分开）

### fix: 任务提醒闹钟「排上却从不触发」——广播从未送达 Receiver
- **原因**：排闹钟的 `PendingIntent` 内嵌的是**仅有 action 的隐式 Intent**，Android 8.0 起对"隐式广播投递给清单声明的接收器"有后台执行限制，会在投递环节把广播丢弃
- **症状**：闹钟按时触发、系统唤醒进程，但 `onReceive` 一次都没执行（日志里连第一行 `>>> onReceive entered` 都没有）
- **排查过程**（值得记下）：
  - `dumpsys alarm` 显示该 tag 有 `3 wakes 3 alarms, last -2m40s…` → 证明闹钟**已触发并唤醒进程**，排除"闹钟没响"
  - `am get-standby-bucket` 返回 `5`（`EXEMPTED`）、`deviceidle whitelist` 已加入 → 排除系统节流
  - 于是问题收敛到"广播投递"这一环，指向隐式 Intent
- **修改**：`QuestReminderReceiver.kt` — 改为显式组件 `Intent(context, QuestReminderReceiver::class.java)`；`scheduleDailyReminder` 与取消路径必须用**同一 Intent 构造**，否则 `cancel` 匹配不到已排的闹钟
- **验证**：前台与后台（冷进程，PID 变化）均可弹出横幅 + 响铃，点击通知正确回到主页

### fix: 提醒无法弹出横幅 —— 通知渠道重要级别对已存在渠道无效
- **原因**：`createNotificationChannel` 对**已存在**的渠道会忽略 `importance` 变更，仅把代码从 `IMPORTANCE_DEFAULT` 改成 `IMPORTANCE_HIGH` 在本机不生效
- **修改**：`QuestReminderReceiver.kt` — 渠道 id 改为 `quest_due_reminder`；`PieceOfLifeApp.kt` — 级别改 `IMPORTANCE_HIGH`；通知 `PRIORITY_HIGH`
- **注意**：旧渠道 `quest_reminder` 会作为空渠道残留在系统设置里，需手动删除或卸载重装

### fix: 提醒闹钟无续期 + 未做精确闹钟授权检查
- **原因**：① `onReceive` 内不续排下一次（原 D31 的刻意设计），只有"用户每天开一次 APP 且跨天"才会重排 → 连续两天不开 APP 就断档；② `SCHEDULE_EXACT_ALARM` 在 Android 14+ 对 targetSdk ≥33 默认拒绝，而代码只静默 `catch` 后降级为不精确闹钟，用户无从得知
- **修改**：
  - `QuestReminderReceiver.kt` — 排闹钟时把提醒时间写入 `EXTRA_REMINDER_TIME`，`onReceive` 据此续排次日（不读 DataStore，保留 D32 初衷）；新增 `canScheduleExactAlarms()` 检查，未授权时走不精确闹钟并明确打日志
  - `AppSettingActivity.kt` — 打开提醒开关时若未授权，弹 `PixelDialog` 引导跳系统「闹钟和提醒」页

### refactor: 清理无用权限与死代码
- **内容**：
  - 移除 `WAKE_LOCK` 权限（唤醒设备由闹钟类型 `RTC_WAKEUP` 保证，代码从未申请唤醒锁；原注释"允许广播接收器唤醒设备"是错的）
  - 删除零调用的 `QuestNotificationScheduler.cancelDailyReminder()`（关闭提醒由 `scheduleDailyReminder(enabled = false)` 覆盖）
  - 删除无生产者的 `AddonToolActivity.RESULT_SETTINGS_CHANGED` 及 `MainActivity` 中与 `else` 分支完全等价的对应分支

---

## 2026-06-22 (v2.1.0.0622)

### fix: 重复任务克隆后 flag0 被错误还原为已完成/已失败
- **原因**：`processQuestLog` 中克隆时 `clonedFlag0` 使用 `+-100` 偏移，但 `QuestFlag` 的已完成/失败与未完成之间的差值只有 `+-50`，导致 `50-100=-50`（已完成→已失败）而非 `0`（已完成→未完成）
- **修改**：`NewDayChecker.kt` — `+-100` 改为 `+-50`

### fix: 通知系统真机无法触发
> **补充（2026-09-20）**：本条的原因判断有误 —— 该问题当时的真正根因是**隐式 Intent 的广播被丢弃**，改成精确闹钟并没有解决它，提醒实际一直没工作过。最终修复见本文档 2026-09-20 章节。
- **原因**：`setAndAllowWhileIdle` 在真机（VIVO 智能控制）上被系统拦截。改为 `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM` 权限声明 + try-catch `SecurityException` 降级
- **修改**：
  - `QuestReminderReceiver.kt` — `setAndAllowWhileIdle` → `setExactAndAllowWhileIdle` + try-catch 降级
  - `AndroidManifest.xml` — 恢复 `SCHEDULE_EXACT_ALARM` 权限
  - 移除 `Thread`/`GlobalScope`，改为同步 `runBlocking`
  - 去除链式调度，改为 APP 内事件驱动（NewDayChecker / AppSetting / PieceOfLifeApp）

### fix: 通知日志无法捕获
- **原因**：`BroadcastReceiver` 中 `Thread { runBlocking(IO) { ... } }` 在真机上 13ms 内被回收，Room 查询未完成
- **修改**：`QuestReminderReceiver.kt` — 移除 `Thread`/`GlobalScope.launch`，改为直接在 `onReceive` 中同步 `runBlocking(Dispatchers.IO)` 执行

### fix: 从日志编辑进入 LogInfo 时更改丢失
- **原因**：`checkUnsavedAndNavigate` 的确认回调只调了 `saveDocContent()`（仅保存 DOCUMENT 的 .md 文件），DEFAULT/PICTURE 模式的更改未写入 DB
- **修改**：`LogEditorActivity.kt` — 改为「放弃修改」弹窗（`DUAL_IMPORT_CONFIRM`），确认后直接跳转，不保存（用户建议的简化方案）

### fix: 新一天检测重复执行
- **原因**：`onCreate` + `onResume` 各启动一个协程，竞态条件下两个协程都认为「是新的一天」
- **修改**：`MainActivity.kt` — 移除 `onCreate` 中的调用，仅由 `onResume` 和 `btnTool1` 触发

### fix: APP 后台跨天时不触发新一天检测
- **原因**：`runNewDayCheck` 仅在 `onCreate` 调用，熬夜跨天或 APP 挂后台到第二天不会触发
- **修改**：`MainActivity.kt` — 新增 `onResume` + `btnTool1` 点击时调用

### feat: 通知系统完善
- **内容**：通知点点击打开 APP 主页面、通知调度改为 APP 内事件驱动、新增完整 Logcat 调试日志
- **修改**：
  - `QuestReminderReceiver.kt` — 添加 `setContentIntent` + 关键路径 Log 输出（TAG=QuestReminder）
  - `MainViewModel.kt` — `runNewDayCheck` 后刷新提醒闹钟
  - `PieceOfLifeApp.kt` — 启动时调度提醒
  - `AppSettingActivity.kt` — 修改设置后即时调度/取消

### feat: 版本号显示
- **内容**：APP 设置页最下方显示版本号 `v2.1.0.0622`
- **修改**：`activity_app_setting.xml` + `strings.xml` — 新增 `app_version_name` 字符串资源，居中灰色小字

### feat: 编辑时光标自动移到末尾
- **内容**：编辑已有内容的日志时，光标自动定位到文本末尾
- **修改**：`LogEditorActivity.kt` — DEFAULT/PICTURE 模式 `loadFromLog` 后调用 `setSelection(logText.length)`

---

## 2026-06-21

### fix: 软删除日志无法正常进入编辑模式
- **原因**：`LogDao.getById` 查询条件含 `AND is_deleted = 0`，回收站中的已删除日志无法被加载，导致编辑时文本为空
- **修改**：`LogDao.kt` — 移除 `AND is_deleted = 0`，getById 返回所有日志不论软删除状态

### fix: 图像预览界面按钮精灵图未填满容器
- **原因**：btnClose / btnEdit 各设 `padding="8dp"` 导致精灵图被压缩在 32dp 区域内，外加 `marginEnd="8dp"` 产生额外间距
- **修改**：`activity_image_preview.xml` — 移除 padding 和多余 margin，添加 `scaleType="fitCenter"`

### fix: 编辑日志时技能升级检测被错误触发
- **原因**：`itemsBeforeNewApply` 快照记录在旧属性还原（`apply(inverse)`）之后而非之前，导致每次编辑都错误地比较了「还原后的中间态 vs 新终态」
- **修改**：`LogEditorActivity.kt` — 将快照移到 `apply(inverse)` 之前，比较「编辑前的原始状态 vs 新终态」

### fix: 重复任务不会正常重复 + 重复任务 LogInfo 显示错误
- **原因**：`completeQuest` 只修改了 flag0，未对重复任务创建下一周期实例。`buildRepeatString` 中 `QuestType.DAY` 误用了任务设置界面的字符串「是否无期限」
- **修改**：
  - `NewDayChecker.kt` — 重写 `processQuestLog` 为原项目的三遍模式（先收集 → 再克隆 → 后标记失败），克隆时修改原任务 `flag1 = DEFAULT` 避免重复克隆，失败时统一交集任务的 `flag1`
  - `LogInfoActivity.kt` — `QuestType.DAY` 改为「每天重复」
- **影响**：手动完成任务和新一天检测都会触发重复克隆，恢复原项目行为

### fix: 任务自动失败时 buildDate/buildTime 未更新
- **原因**：`expiredQuest.copy` 保留了原 `buildDate`/`buildTime`，导致过期任务出现在创建当天而非 NewDay 检测当天
- **修改**：`NewDayChecker.kt` — copy 中添加 `buildDate = today, buildTime = nowTime`

### fix: 默认类型日志允许保存空内容
- **原因**：`saveAndFinish` 未对 `logText` 为空的情况做拦截，导致空白日志被存入数据库
- **修改**：`LogEditorActivity.kt` — DEFAULT 模式下 `logText.isBlank()` 时弹出 PixelDialog 提示并 `return@launch`

### fix: 编辑无变更时仍修改更新时间
- **原因**：`saveAndFinish` 无论内容是否变更都执行 `baseLog.copy(changeDate = now, ...)` 并 `saveLog`，导致 changeDate 被刷新
- **修改**：`LogEditorActivity.kt` — 新增无变更检测，`logText` 和 `remark` 均未变化时直接 `finish()`，不碰 DB

### feat: 技能等级提升时自动创建 HINT 通知日志
- **内容**：检测 `LogItemChange.apply` 前后技能等级（`value / levelExp`）变化，提升时写入 HINT 日志
- **修改**：
  - `LogItemChange.kt` — 新增 `detectSkillLevelUp()` 函数
  - `LogEditorActivity.kt` — 保存时检测（排除还原步骤）
  - `LogInfoActivity.kt` — 冲正时检测
  - `MainViewModel.kt` — 任务完成时检测
  - 删除时不检测

### feat: HINT/DEBUG 过期日志改为硬删除
- **内容**：系统日志次日直接永久删除，不再走回收站保留 14 天
- **修改**：`NewDayChecker.kt` — `softDeleteLog` → `permanentlyDeleteLog`

### feat: HINT 日志文字颜色区分
- **内容**：HINT 日志正文使用 `#C8C8C8`（`TEXT_white1`）深白色，与普通日志默认白色区分
- **修改**：`SpanTextBuilder.kt` — HINT 类型添加 `ForegroundColorSpan("#C8C8C8")`

### feat: 通知系统重构与修复
- **原因**：原通知系统因 `setInexactRepeating` 在 targetSdk=35 上被弃用、BroadcastReceiver 生命周期不足、时区硬编码等问题无法正常工作
- **修改**：
  - `QuestReminderReceiver.kt` — 使用 `goAsync()` 延长生命周期至 30 秒，单线程顺序执行
  - `setAndAllowWhileIdle` → `setExactAndAllowWhileIdle`（含 `SCHEDULE_EXACT_ALARM` 权限声明 + try-catch 降级）
  - 取消硬编码 `GMT+08:00`，改用系统默认时区
  - 新增 `BOOT_COMPLETED` 处理，重启后自动恢复调度
  - 添加通知点击意图，点击通知打开 APP 主页面
  - 闹钟触发时先重调度再弹通知，形成自维护的每日循环

---


## 2026-05-26

### fix: SelectorDialog 属性插入器传递错误的值
- **原因**：`buildItemListWithRemark()` 的点击回调传递了 `title`（带图标的显示名称）和 index（列表中的位置），而非 `remark`（纯缩写）
- **修改**：`SelectorDialog.kt` — `onItemSelectedAction?.invoke(title, index)` → `invoke(title, remark)`

### fix: SpanTextBuilder 图标替换导致 Remark Span 偏移
- **原因**：先用 `String.replace()` 将文本中的缩写替换为 `图标+缩写`（字符串长度变化），再 `applyRemarkFormats()` 使用原始偏移位置，导致 Span 对齐错误
- **修改**：`SpanTextBuilder.kt` — 改为先 `sb.append(log.logText)` → `applyRemarkFormats()` → 再用 `SpannableStringBuilder.replace()` 原位替换缩写（框架自动调整 Span 位置）

### fix: 编辑完成返回主页不再触发滚动到底部
- **原因**：`logEditorLauncher` 在编辑成功后 `scrollTarget = ScrollTarget.BOTTOM`，破坏了用户当前的浏览位置
- **修改**：`MainActivity.kt` — 改为始终 `ScrollTarget.NONE`，仅刷新列表内容

### fix: HINT+flag0=999 时间戳遗漏星期字段
- **原因**：`SpanTextBuilder` 对 HINT+flag0=999 的日志使用 `dateInt2String(buildDate)` 重新生成日期字符串，该函数不含星期。而 `NewDayChecker` 保存的 `logText` 已通过 `TIME_TYPE_DATE_WEEK` 包含星期
- **修改**：`SpanTextBuilder.kt` — 直接使用 `log.logText` 显示，而非重新计算

### feat: NewDayChecker 新增每日欢迎 HINT 语句
- **内容**：每日首次打开 APP 时，生成一条 HINT 日志（buildTime=1，寿命 1 天），包含用户名、前 3 项属性值、失败任务数、未完成任务数、即将到期任务数
- **格式**：`欢迎回来，{用户名}！您当前的属性为：{图标}{名称} {值}，...。有N个任务已失败。您目前还有N个未完成的任务。有N个任务即将到期。`
- **参考**：原项目 `TheMainActivity.java` 中 `getWelcomeString()` + 任务计数拼接逻辑
