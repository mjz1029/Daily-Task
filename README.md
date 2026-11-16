# 今日待办 · Android 版

Kotlin + Jetpack Compose 写的本地待办应用（由 Google AI Studio 导出的工程整理而来）。

一句话说清它能干什么：用一句话录入任务（"今晚八点前把季报发给王总"）、四个视角筛任务（今日待办 / 即将截止 / 今日已办 / 全部日程）、单条任务生成二维码或口令发给别人、换手机时整库迁移、桌面小组件、到点本地提醒。**数据全在本机**，不上云、不需要账号。

约 8.4k 行 Kotlin，单 Activity + Compose，无 Room、无 Hilt，只依赖 DataStore / WorkManager / Glance / CameraX / ZXing / Firebase AI Logic。

## 与 Swift 版的关系

| | 仓库 | 平台 |
|---|---|---|
| 原版（本仓库） | [mjz1029/Daily-Task](https://github.com/mjz1029/Daily-Task) | Android |
| 移植版 | [mjz1029/TodayTasksSwift](https://github.com/mjz1029/TodayTasksSwift) | macOS + iPhone |

两端**数据互通**：任务 JSON 字段名一致（时间统一毫秒 `Long`）；`TT1:` 单条分享码是 `base64URL(gzip(JSON))`；`TD1:` 迁移口令是 `salt16 ‖ iv12 ‖ AES-GCM`，密钥由 PBKDF2-HMAC-SHA256(10000 轮, 32 字节) 派生——Swift 版按同样参数实现，Android 生成的码能在 iPhone 上导回来。

## 功能与实现状态

| 模块 | 状态 | 关键实现 |
|---|---|---|
| 任务模型 / 时间文案 / 今日归属 | 完成 | `model/TaskItem.kt`、`util/DeadlineFormatter.kt` |
| 本地存储 | 完成 | `data/LocalTasksStore.kt`：DataStore Preferences 存整表 JSON，原子写、指纹去重导入 |
| 主界面 | 完成 | `ui/screens/TasksWorkspaceScreen.kt`：四个筛选、任务卡左滑已办/删除、编辑器抽屉、空态 |
| 折叠屏适配 | 完成 | `ui/adaptive/FoldableLayoutState.kt`：分屏 / 双栏 / 上下分栏三种布局 |
| 自然语言建任务 | 完成 | `ai/NaturalLanguageTaskParser.kt`：Firebase AI Logic(Gemini + App Check) / OpenAI 兼容 / Anthropic 三路，400 时去掉 `response_format` 重试 |
| 二维码与口令分享 | 完成 | `transfer/SingleTaskShareCodec.kt`（`TT1:` 协议）、`transfer/QrCodeHelper.kt`（ZXing 生成）、CameraX 实时扫码 |
| 换机转移 | 完成 | `transfer/DevicePairingTransfer.kt`：6 位配对码 + 局域网 HTTP，失败 12 次后 429；离线口令 `TD1:` |
| 系统日历 | 完成 | `util/CalendarHelper.kt`：`ACTION_INSERT` 交给日历应用，不申请日历权限 |
| 桌面小组件 | 完成 | `widget/`：Glance |
| 本地提醒 | 完成 | `notifications/TaskReminderScheduler.kt`：`setAndAllowWhileIdle` + WorkManager 15 分钟周期兜底 |
| 后台同步 | 完成 | `sync/TaskSyncWorker.kt` |

## 构建

**要求**：Android Studio（Ladybug 或更新）、JDK 17、Android SDK 36。

1. Android Studio → Open → 选本目录，让它自动同步
2. 本仓库**不带 `gradle-wrapper.jar`**，AS 打开时会提示生成 wrapper；或者装好 Gradle 后执行 `gradle wrapper --gradle-version 9.3.1`
3. `app/google-services.json` 已入库，可以直接编译。要换成自己的 Firebase 项目：控制台新建 → 添加 Android 应用，包名填 `com.aistudio.todaytasks.kqzvmp` → 下载覆盖
4. 运行

版本组合：AGP 9.1.1 / Gradle 9.3.1 / Kotlin 2.2.10 / compileSdk 36（见 `gradle/libs.versions.toml`、`app/build.gradle.kts`）

> `app/build.gradle.kts` 里有一行 `signingConfig = signingConfigs.getByName("debugConfig")`，是 AI Studio 导出时加的，只在本地调试签名下成立；要发布请换成自己的签名配置。

### 关于 `app/google-services.json`

里面的 Firebase API key 会打包进客户端，本身不算机密；但公开仓库建议在 Google Cloud Console 给这个 key 加限制（限定包名 + 签名 SHA-1，或只放行 Firebase 相关 API）。不想公开就把它从仓库移除、加进 `.gitignore`，本地自备一份。

## 权限

```
INTERNET / ACCESS_NETWORK_STATE   AI 接口与局域网转移
POST_NOTIFICATIONS                到点提醒（用户操作时才申请）
CAMERA                            扫码分享/接收（声明为 optional，无摄像头也能用）
```

没有申请 `SCHEDULE_EXACT_ALARM`，也没有 `RECEIVE_BOOT_COMPLETED`：提醒走 `setAndAllowWhileIdle`（不精确但免权限），重启后靠 WorkManager 周期任务把提醒重排回来。

## 目录

```
app/src/main/java/com/example/
  MainActivity.kt            单 Activity 入口
  model/TaskItem.kt          任务模型、优先级、今日归属
  data/LocalTasksStore.kt    本地库（DataStore + JSON）
  viewmodel/TaskViewModel.kt 界面状态
  ui/screens/                主工作区
  ui/components/             任务卡、编辑器、速记条、多草稿确认、布局菜单
  ui/adaptive/               折叠屏布局状态
  ui/theme/                  调色板与排版
  ai/                        自然语言解析、模型配置存储、App Check
  transfer/                  分享码、二维码、配对换机
  notifications/             提醒调度
  sync/                      WorkManager 同步
  widget/                    桌面小组件
  util/                      时间文案、日历
app/src/test/                单元测试（模型、存储、解析、分享码、换机协议、日历 intent）
app/src/androidTest/         仪器测试骨架
gradle/libs.versions.toml    版本目录
firestore.rules / .test.js   Firestore 规则与规则测试
```

## 已知问题

这些是代码里确实存在的缺陷，**本仓库未修**（保持原行为，方便与 Swift 版逐条对比）：

1. **没有排序入口**：排序规则写死在存储层（未完成 → 截止时间 → 高优先级），界面没有排序 UI。
2. **清空数据无入口**：`clearAll()` 有实现但没有任何调用方，设置页也没有按钮。
3. **删除无撤销**：删了就是删了。
4. **删任务不取消闹钟**：`syncReminders` 只遍历现存任务，被删任务的提醒到点仍会弹。
5. **小组件勾选不重排提醒**：从小组件标记完成，提醒不会重新同步。
6. **AI 解析在部分布局下静默失败**：错误提示只挂在三种布局上，`TOP_BOTTOM_SPLIT` / `BOOK_TWO_PANE` 下点"填充"没有反应。
7. **多条草稿被丢弃**：非批量路径只用 `drafts.first()`，一次解析出多条时多余的静默丢掉。

Swift 版把这 7 条逐一改掉了，说明见那边 README 的「与 Android 版的刻意偏差」。

## AI Studio 原始工程

- 应用页：https://ai.studio/apps/7ae3d496-baf4-40e1-aca6-fb689afdfa6e
- 内置 Gemini 走 Firebase AI Logic + App Check，不需要单独的 API key；外部模型（DeepSeek / OpenAI / Qwen / Claude 等）在应用设置里填 baseURL + model + key，配置存本机。
- `.env.example` 只有说明，不含任何密钥。
- 导出时的原始说明保留在 [`docs/ai-studio-readme.md`](docs/ai-studio-readme.md)。

## 许可

[MIT License](LICENSE) © 2026 Mao Jizhou

保留版权声明即可任意使用、修改、商用、闭源分发。
