# AGENTS.md

本文件为 AI 编程助手（以及新加入的开发者）提供最小必要的项目上下文。
**本项目的文档比代码注释完整得多** —— 动手前请先按下面的顺序阅读。

## 项目是什么

「片语 / Pieces of Life」—— 纯离线的个人日志与任务管理 Android 应用。
像素画（pixel-art）风格，无账号、无联网、无第三方统计，数据全部保存在设备本地。

## 先读文档

| 顺序 | 文档 | 内容 |
|---|---|---|
| 1 | [docs/project_context.md](docs/project_context.md) | **权威**：当前架构、模块职责、页面状态机、活跃决策日志（D01~）。"为什么这样写"的答案基本都在这里 |
| 2 | [docs/hotfix_record.md](docs/hotfix_record.md) | 按日期的修复账本，含每次 Bug 的根因与排查过程。排查相似问题前先搜这里 |
| 3 | [docs/开发者总结报告.md](docs/开发者总结报告.md) | 项目基础信息与文件结构（v2.0 时期快照，现状以 ① 为准） |
| 4 | [docs/项目重构报告.md](docs/项目重构报告.md) | Java → Kotlin 重构过程（历史） |

## 代码在哪

```
app/src/main/java/com/archite/piecesoflife/
├── data/   Room 实体、DAO、Repository、DataStore 偏好
├── ui/     Activity、自定义 View、Dialog、ViewHolder
└── util/   精灵加载与绘制、文本渲染、文件与图片、通知调度、每日检测
app/src/main/res/
├── drawable/   精灵图集（button_16x16 / 24x24 / 32x32 / 72x32、icons、background 等）
└── font/       像素字体
```

## 构建与验证

需要 **JDK 17** 与 Android SDK（compileSdk 35）。

```bash
./gradlew assembleDebug
```

- **项目没有单元测试**，验证靠编译 + 真机手动走查
- 依赖含 JitPack（PhotoView），已在 `settings.gradle.kts` 声明
- `local.properties` 不入库，由 Android Studio 生成

## 编码风格约束（务必遵守）

1. **不添加注释**，除非被明确要求
2. **优先修改现有文件**，不创建不必要的文件
3. **不创建 `.md` 文档**，除非被明确要求
4. **先读后写**：修改前先读目标文件，理解现有风格
5. 遵循现有命名：变量/函数 `camelCase`、类/文件 `PascalCase`、常量 `UPPER_CASE`
6. **不引入第三方库**；图片处理用 `BitmapFactory` + 现有 9-patch 精灵体系
7. 修改后保持**零诊断错误**
8. `AppSetting` / `UserSetting` 的变更需要写 `LogType.DEBUG` 日志

## 容易踩的坑（都是真机验证过的）

- **精灵按下态**：`SpriteLoader.setButton` 不传 `downFrame` 时会自动取「当前帧 + 1」。
  并列状态类精灵（如任务三态框）必须显式传 `downFrame = 自身`，否则按下会变成下一个状态；
  若本身就是图集末帧，还会直接崩溃（`x + width must be <= bitmap.width()`）
- **广播 Intent 必须显式指定组件**：`Intent(context, Receiver::class.java)`。
  仅有 action 的隐式 Intent 会被 Android 8.0+ 的后台执行限制在投递环节丢弃 ——
  症状是闹钟按时触发、进程被唤醒，但 `onReceive` 从不执行
- **通知渠道的 `importance` 对已存在渠道无效**：创建后无法通过代码改级别，只能换 channel id
- **9-patch 面板边框厚度是 `16px × pixelScale`**（与屏幕密度无关），面板内的内容要按此让位，
  否则填充色会盖住边框
- **`changeTime` 约定**：任务恒为 `235959`（只论日期不论时刻），`buildDate` 才承载"创建日/完成日"

## 修改后的收尾

改动完成后，同步更新 `docs/project_context.md`（若涉及结构、行为或决策）与
`docs/hotfix_record.md`（若是 Bug 修复），并说明原因与改动点。
