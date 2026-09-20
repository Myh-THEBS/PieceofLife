# 片语 · Pieces of Life

一个纯离线的个人日志与任务管理 Android 应用。像素画（pixel-art）界面，无账号、无联网，数据全部保存在设备本地。

<p align="center">
  <img src="app/src/main/play_store_512.png" width="180" alt="片语">
</p>

## 特性

### 日志

- 富文本编辑：加粗 / 下划线 / 删除线 / 字号 / 颜色，10 步撤销重做
- 快捷插入：`@@` 插入属性、`##` 插入标签、`$$` 插入常用短语
- 属性生效：日志中出现的属性缩写按规则增减属性值，技能经验达标自动升级
- 图片日志：从相册导入，自动生成缩略图，支持全屏查看与手势缩放
- 文档日志：导入 `.txt` / `.md` / `.py` / `.html` / `.cpp` / `.js` 等纯文本，统一存为 `.md`，支持编辑与预览切换

### 任务

- 期限 / 无期限（长期任务）、重复模式、默认型与惩罚型两类任务
- 完成或失败按类型增减属性；超时任务在每日检测时自动判定失败，重复任务自动生成下一周期
- 每日提醒：精确闹钟 + 高优先级通知，可在设置中开关并指定时间

### 每周计划

- 以自然周（周一 ~ 周日）为单位查看与执行任务
- 周条 7 格，点击进入当日视图，再点同一天返回整周
- 三色周进度条：按周汇总「完成 / 失败 / 未完成」占比

### 其他

- 数据统计：绿墙日历 + 条数 / 字数聚合
- 日志查询：按日期与关键字检索
- 属性账单、回收站、日志信息（冲正 / 重命名 / 覆盖上传 / 导出）
- 单文件数据备份与恢复（含数据库、图片、文档与全部设置）
- 自定义用户名、头像、属性 / 技能 / 短语 / 标签

## 项目文档

本项目**代码注释极少**，设计意图、约定与历史都记录在 `docs/` 下。接手代码（或让 AI 助手恢复上下文）时，建议按此顺序阅读：

| # | 文档 | 定位 |
|---|---|---|
| 1 | [项目上下文快照](docs/project_context.md) | **权威**：当前架构、模块职责、页面状态机、活跃决策日志（D01~）——**建议先读这份** |
| 2 | [热更新记录](docs/hotfix_record.md) | 修复账本：每次 Bug 的根因、排查过程与改动 |
| 3 | [开发者总结报告](docs/开发者总结报告.md) | 项目基础信息、文件结构与开发规则（v2.0 时期快照） |
| 4 | [项目重构报告](docs/项目重构报告.md) | Java → Kotlin 重构的过程与结果（v2.0.1 时期快照） |

另见 [docs/README.md](docs/README.md)（文档索引与阅读建议）与 [AGENTS.md](AGENTS.md)（给 AI 编程助手的最小上下文与易踩的坑）。

## 技术栈

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 2.2.10（Java 17 目标） |
| 构建 | AGP 9.3.0 · Gradle 9.5.0 · KSP 2.3.6 |
| 数据库 | Room 2.8.5 |
| 偏好存储 | DataStore Preferences 1.1.1 |
| UI | 传统 Activity + ViewBinding + RecyclerView |
| 协程 | kotlinx-coroutines 1.8.1 |
| 图片手势 | PhotoView 2.3.0 |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 |

## 构建

需要 **JDK 17** 与 Android SDK（compileSdk 35）。

```bash
git clone https://github.com/Myh-THEBS/PieceofLife.git
cd PieceofLife
./gradlew assembleDebug
```

- `local.properties`（指向本机 Android SDK）不随仓库提供，由 Android Studio 自动生成，或手动创建：

  ```
  sdk.dir=/path/to/Android/Sdk
  ```

- 依赖来自 `google()` / `mavenCentral()` / **JitPack**（PhotoView），已声明在 `settings.gradle.kts`，无需额外配置。
- release 构建未内置签名配置，如需签名请自行添加 `signingConfig`。

## 项目结构

```
app/src/main/java/com/archite/piecesoflife/
├── data/   Room 实体、DAO、Repository、偏好设置
├── ui/     Activity、自定义 View、Dialog、ViewHolder
└── util/   精灵加载、文本渲染、文件与图片处理、通知调度、每日检测
```

## 字体与美术资源

| 资源 | 说明 |
|---|---|
| `wqy_12px` ~ `wqy_16px.ttf` | 文泉驿点阵字体，遵循其 GPL 许可（含字体嵌入例外条款） |
| `silver.ttf`、`fonsung.ttf`（坊宋 16） | 免费商用字体 |
| 像素精灵与图标（`button_*.png`、`background*.png`、`icons.png` 等） | 本项目自制 |

字体文件遵循各自的上游许可，独立于本项目的 MIT 许可；再分发或商用前请自行确认上游条款。

## 许可

代码以 [MIT](LICENSE) 许可发布。字体资源遵循各自的上游许可（见上表）。
