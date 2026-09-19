# MockX 完整功能规划 — 阶段 0/1 执行记录

> 依据：`MockX完整功能规划.md`（执行其第 16 节推荐顺序的「阶段 0」与「阶段 1」；阶段 2 做对照核查）。
>
> 执行日期：2026-09-19
>
> 关联文档：`规划.md`（阶段 2 已于第一轮实现）、`追踪.md`（问题闭环状态）、`通知体验升级规划.md`

---

## 1. 本次执行范围与决策门

按规划 §16 的顺序与门禁执行：

- ✅ **阶段 0**：仓库基线记录（§4.2）、官方资料核对（§4.1，子代理 A/B）、当前代码审计（子代理 C/D，因子代理并发限制由主代理完成）。
- ✅ **阶段 1**：步行会话可靠性——会话世代隔离（§5.1）、幂等终态清理与顺序（§5.2）、时间基准复核（§5.3）、暂停/继续一致性（§5.4）。
- ✅ **阶段 2（核查）**：对照 §6.1–6.5 逐项确认第一轮实现已覆盖，无代码缺口。
- ⛔ **阶段 3–12 未启动**：规划 §5 明确「阶段 1 未完成前，不开始多停靠点、通知增强和快捷控制的最终整合」，而阶段 1 的完成包含实体设备压力测试（§5.6，属 P1-003，只能由用户执行）。
- ⚠️ 例外：审计 B（小米超级岛契约）虽服务于阶段 5，但其结果决定阶段 5 是否可行（决策门：「小米字段未被官方文档确认：只做普通通知」），故按 §4.1 提前核对完毕。

---

## 2. 仓库基线（规划 §4.2）

| 项目 | 值 |
|---|---|
| 分支 / HEAD | `master` @ `7195b29`（2026-09-15，rebrand to MockX） |
| 与远端关系 | 落后 `origin/master` 7 个提交；**步行功能全部为未提交的工作区改动，因此不可 pull/rebase** |
| JDK | Temurin 21.0.12.1（`/Volumes/SanDisk/Developer/SDKs/openjdk-21`） |
| Gradle / AGP / Kotlin | 8.9（腾讯镜像 wrapper）/ 8.7.0 / 2.2.10 |
| SDK | compileSdk 36、targetSdk 36、minSdk 29 |
| 测试规模 | 基线 45 个 `@Test` → 本轮后 54 个 |
| adb 设备 | 无连接（P1-003 只能由用户执行） |
| 基线 APK | 第一轮产物（2026-09-19 01:15/01:16）：debug `c655469a…fca20`、release `37b6823f…90d66` |
| 工作区 | 保留全部既有改动；本轮仅新增/修改规划 §5.1 相关文件（见 §5） |

---

## 3. 官方资料核对（规划 §4.1）

### 3.1 [子代理 B] 小米 HyperOS 超级岛契约 — 已完成，结论可用于阶段 5 设计

要点（详见子代理报告，来源均为小米官方 dev.mi.com 文档 + 模板库 PDF）：

- `miui.focus.param` 为 `notification.extras` 中的 **String extra**，JSON 根节点 `param_v2`；必选字段 `business`、`param_island`（内含 `bigIslandArea`/`smallIslandArea`），控制字段 `updatable`（持续通知建议 `true`）、`sequence`（long，单调递增防乱序）、`enableFloat`、`timeout`（分钟）/`islandTimeout`（秒）。
- **本地 `NotificationManager.notify()` / `startForeground()` 官方明确支持**（"客户端实现"为官方第一种接入方式），不依赖 MIPush；社区多个无授权开源项目在 HyperOS 3 真机本地注入上岛。
- 支持边界：HyperOS 2 = 焦点通知（状态栏胶囊等），HyperOS 3 = 超级岛（大/小岛 + 展开态）；OS1 不建议接入。不支持设备**忽略该 extra 按普通通知展示**，`filterWhenNoPermission` 默认 `false`，退化路径干净 —— 满足决策门「通知适配失败必须能回退到标准前台通知」。
- 可编程探测：`persist.sys.feature.island`（反射）、`Settings.System "notification_focus_protocol"`（1/2/3）、`canShowFocus` call。
- 官方已确认的**最小步行 payload**（`baseInfo` 文本 + `progressInfo` 纯进度条 + `bigIslandArea` 摘要 + OS2 `ticker`）已存档于子代理报告，可直接作为阶段 5 `XiaomiIslandAdapter` 的输入。
- 【未确认，留待真机验证】本地上岛是否长期免"平台提报审核"；低 importance 通道行为；`progressInfo` 与 `baseInfo` 平级放置细节。

### 3.2 [子代理 A] Android 官方系统能力（Live Updates / ProgressStyle / 前台服务 / Tile / Shortcuts）— 已完成

对后续阶段有决定性影响的结论（完整契约与来源 URL 见附录）：

- **ProgressStyle 真实 API 与规划假设不同**：方法是 `addProgressSegment` / `addProgressPoint` / `setProgressTrackerIcon`；`Segment(int length)`（位置由系统按累计长度排布，所有段长之和 = progressMax）、`Point(int position)`（无 icon）；`ProgressStyle` 本身无 `setColor`（整体色走 `Builder#setColor`）。
- **推荐统一走 Compat**：`NotificationCompat.ProgressStyle` + `setRequestPromotedOngoing()` 自 androidx.core **1.17.0** 起可用（项目当前 core 版本需在阶段 3 确认/升级），在 API < 36 **自动回退普通通知**——阶段 3（低版本回退）与阶段 4（Live Updates）可共用一条 Builder 路径，无需 `SDK_INT` 分支。
- Live Update 资格：样式白名单（含 ProgressStyle）、必须 `FLAG_ONGOING_EVENT` + contentTitle + 主动请求提升；禁令：RemoteViews、group summary、`setColorized`、`IMPORTANCE_MIN`；用户关闭后退化为普通通知（`canPostPromotedNotifications()` 探测）。`POST_PROMOTED_NOTIFICATIONS` 为 normal|appops 权限（API 36.1），是 POST_NOTIFICATIONS 的**附加**而非替代。
- 前台服务 location：现有实现（三参 `startForeground` + `FOREGROUND_SERVICE_LOCATION` + 运行时定位权限）已符合 API 29–36 契约；后台启动 location FGS 会 `SecurityException`，但**通知交互/Tile/widget/PendingIntent 启动属豁免路径**——直接支撑阶段 11 的 Tile/Shortcut 设计。Android 16 无 location FGS 新增限制。
- Tile：公开 API **不存在** `TILE_MODE`/"passive tile"，模式区分是 `META_DATA_ACTIVE_TILE`；`requestListeningState` 仅对 active tile 生效；API 34+ 必须用 `startActivityAndCollapse(PendingIntent)`。
- Shortcuts：static+dynamic 合计大多数启动器显示上限 4 个（精确值 `getMaxShortcutCountPerActivity()`）；shortcut intent 直达 Service 无官方明文禁止但有推断风险，**推荐透明中转 Activity（trampoline）→ `startForegroundService`**。

### 3.3 [主代理] 当前代码审计（原子代理 C 范围）

调用链核实：UI/通知按钮 → `startService(action)` → `WalkingSimulationService.onStartCommand` → `PreferencesRepository.editRemote(commit=true)` → 远程偏好 → Hook 侧 `PreferencesUtil`/`LocationUtil.tryApplyWalkingLocation`。

发现并已在本轮修复的缺陷（均属阶段 1 范围）：

| # | 缺陷 | 级别 | 触发时序 | 修复 |
|---|---|---|---|---|
| C1 | 旧一代异步写可覆盖新会话：`finalizeSession` 的终态清理协程不携带会话标识，若用户"停止后立即开始新会话"，迟到的清理写会把新会话的 `walking_enabled/is_playing/路线` 全部抹掉，Hook 静默停止伪造而 UI 显示行走中 | P1 | STOP →（清理协程排队）→ 新 START 提交 → 旧清理执行 | 会话世代隔离（§5） |
| C2 | 同实例 STOP→START 后 `finalizing` AtomicBoolean 永久不复位：该实例后续 STOP/FAIL 的终态清理被 `compareAndSet` 永久拒绝，服务可能滞留前台且状态不清理 | P1 | 同一 Service 实例内 先 STOP 后 START 再 STOP/FAIL | `startSession` 开启新世代时重新武装 `finalizing` |
| C3 | `resumeSession` 无相位门：停止后误触"继续"会把已清理会话打成 `FAILED`（误报错误）或对 `ARRIVED` 重新开 tick | P2 | RESUME 在 IDLE/ARRIVED/FAILED 到达 | `WalkingCommandPolicy.canResume`（仅 WALKING/PAUSED），IDLE/FAILED 时安全关闭服务 |

复核确认无问题项：START 重复调用不会产生双 ticker（`tickJob.isActive` 早退）；FAILED 与 STOP 清理经 `finalizing` + 世代校验互斥；时间基准无混用残留（`elapsedRealtime` 仅用于 tick 间隔与日志限流）；通知文案全部来自资源。

残余风险（如实记录）：世代校验为「读 id → 写状态」两步，非原子事务，存在微秒级 TOCTOU 窗口（远程偏好无事务 API，概率与影响均可忽略）；通知渲染直接读偏好，与串行写之间存在单键粒度的瞬时不一致（下一秒 tick 自愈）。

### 3.4 [主代理] UI / 设置 / 本地化审计（原子代理 D 范围）

- **硬编码文本**：`manager/walking/`、`manager/route/` 无用户可见硬编码中文（通知文案均走 `R.string.walk_*`），满足 §12.2 对新增代码的要求。
- **本地化资源**：`values-zh` 与 `values` 仅差 `unit_meters`、`unit_meters_per_second` 两个通用单位 key（zh 回退英文 "m"/"m/s"，可接受）；**`values-de` 缺失 59 个 key，其中 47 个为第一轮步行/高德 Key 功能新增**——德语用户将回退英文。属阶段 8（本地化）的既有缺口，已登记，不在本轮越阶段补齐。
- **设置组件**：`SettingsScreen` 已有 `SecretTextRow` 等可复用条目组件，后续"到点提醒开关/自动停止开关/Tile 开关"可直接复用。
- **收藏数据结构**：`FavoriteLocation(name, latitude, longitude, description)` 以 Gson JSON 存于 `KEY_FAVORITES`，**无分组概念**；阶段 8 迁移设计输入：新增可空 `groupId` 字段 + 旧 JSON 反序列化天然回退未分组，删除分组回退未分组即可，风险低。
- **地图交互入口**：选点/交互锁定（`isMapInteractionEnabled`）与 `WalkingControlCard` 已集中挂载，阶段 6 多停靠点编辑 UI 可挂靠在同一卡片区域。

---

## 4. 阶段 1 实现内容

### 4.1 会话世代隔离（规划 §5.1）

- 新增共享键 `KEY_WALKING_SESSION_ID`（`data/Constants.kt`）：`startWalkingSession` 在同一次 commit 中写入新 UUID；`stopWalkingSession`/`markWalkingFailed`/`savePreparedWalkingRoute`(READY)/`clearWalkingRoute` 清除之——"当前存在 id"即"存在活跃世代"。
- 新增纯策略 `manager/walking/WalkingSessionPolicy.kt`：
  - `mayWrite(writer, current)`：tick/暂停/继续/到达写入前校验，世代不符即丢弃；
  - `isSuperseded(writer, current)`：终态 finalizer 专用，**只有双侧 id 均非空且不同才判定被取代**——id 为 null（远程偏好暂不可达或已清理）不得视为被取代，否则 finalizer 会跳过清理与关停、卡死前台通知。
- `WalkingSimulationService`：持有 `sessionId` 字段（START/收养/恢复时从共享状态捕获）；新增 `launchSessionWrite` 取代原 `launchStateWrite`（写入携带世代并在执行时校验）；`finalizeSession` 被取代时跳过状态写、`stopForeground`、`releaseWakeLock` 与 `stopSelf`（通知/WakeLock/服务生命周期已归新一代所有）。
- 到达与停止几乎同时发生时：串行队列按命令顺序落盘，后到者胜出；若停止先执行（世代被清除），迟到的到达写被丢弃——始终只有一个终态生效。

### 4.2 幂等终态清理与顺序（规划 §5.2）

终态路径现在严格满足：取消 tick → 串行完成共享状态写（同 commit 内清动态位置、置 `is_playing=false`）→ 移除前台通知 → 释放 WakeLock → `stopSelf`。WakeLock 释放从命令入口移入 finalizer（写入期间保持持锁，降低远程偏好提交被休眠拖延的风险）；`onDestroy` 保留兜底释放。

### 4.3 时间与状态新鲜度（规划 §5.3）

复核确认第一轮实现已满足（`WalkingStatePolicy`，双侧 `System.currentTimeMillis()`，负 age 安全处理）；本轮无代码改动。

### 4.4 暂停/继续（规划 §5.4）

- 暂停不增加距离、保持最后坐标、无 ticker 分支也写 `PAUSED`、重建后 PAUSED 不自动恢复——第一轮已实现，复核通过。
- 本轮新增：`canResume` 相位门（上表 C3）。

### 4.5 新增/修改文件清单

| 文件 | 变更 |
|---|---|
| `data/Constants.kt` | + `KEY_WALKING_SESSION_ID` |
| `data/repository/PrefrencesRepository.kt` | + `getWalkingSessionId()`；start/stop/failed/READY/clear 五处世代写入与清除；+ `UUID` import |
| `manager/walking/WalkingSessionPolicy.kt` | 新增（纯 Kotlin） |
| `manager/walking/WalkingCommandPolicy.kt` | + `canResume` |
| `manager/walking/WalkingSimulationService.kt` | 世代字段与守卫写、finalizer 超权逻辑、`finalizing` 重武装、RESUME 相位门、WakeLock 顺序 |
| `app/src/test/.../WalkingSessionPolicyTest.kt` | 新增 7 项 |
| `app/src/test/.../WalkingCommandPolicyTest.kt` | + 2 项（canResume 全相位） |

Hook 侧（`PreferencesUtil`/`LocationUtil`）**无需改动**：Hook 依赖 `enabled`/相位/新鲜度三重校验，世代隔离全部收敛在 manager 侧，符合最小影响原则。

---

## 5. 阶段 2 对照核查（规划 §6.1–6.6）

| 规划条目 | 结论 | 证据 |
|---|---|---|
| 6.1 Key 管理（不进源码/资源/Git/APK/日志） | ✅ 已覆盖 | `SecretTextRow` 密文输入、排除于重置、本轮 release dex 扫描无 32 位 hex |
| 6.2 路线接口（v5/walking、lon 在前、show_fields、超时分类） | ✅ 已覆盖 | `AmapWalkingRouteClient`（第一轮，13 项解析测试） |
| 6.3 路线解析（双格式 polyline、去重、限点、GCJ→WGS84） | ✅ 已覆盖 | `AmapRouteParser` + 测试 |
| 6.4 基础模型/速度预设/推进 | ✅ 已覆盖 | `WalkingRoute`(含 API 报告距离与 provider)、三档速度、`RouteProgressEngine` |
| 6.5 基础 UI（含距离与预计时长显示） | ✅ 已覆盖 | `walkingRouteSummary`（距离 · 预计分钟）、进度条、速度 chips、失败重试、交互锁定 |
| 6.6 基础验收 | ⛔ 设备项 | 归入 P1-003 |

---

## 6. 本轮验证证据（P2-002 复核刷新）

- `./gradlew testDebugUnitTest --no-daemon`：**54/54 通过**（基线 45 + 本轮 9；`WalkingSessionPolicyTest` 7、`WalkingCommandPolicyTest` +2）。
- `./gradlew assembleDebug assembleRelease -PappVersionName=v1.0beta --no-daemon`：成功。
  - `app-debug.apk` 83,463,979 B，SHA-256 `f81289e98bdf471fa145ee2e3b5a9b77e4e93261de1501150556a08ee7866aac`
  - `app-release.apk` 4,676,853 B，SHA-256 `05d3e85844c38cd48ea72fdc4c580aaa0fbb690d7299ef7fc637c61cc6451457`
  - aapt2：`versionName='1.0beta' versionCode='10000'`（继承第一轮的版本注入约定）
- `git diff --check`：通过。
- APK 安全检查：release dex 无 32 位十六进制 Key 模式；`restapi.amap.com` 仅 1 处（客户端常量，预期内）；无路线 JSON/精确测试坐标/日志 dump。
- R8 的 kotlin metadata 解析警告为 Kotlin 2.2.10 与 AGP 8.7.0 内置 R8 版本差的既有告警，非本轮引入。

---

## 7. 决策门状态与下一步

| 门 | 状态 |
|---|---|
| 步行状态机真机验收（P1-003 + §5.6 压力测试） | **未通过 → 通知增强（阶段 3–5）与快捷控制（阶段 11）整合、多停靠点最终整合继续冻结** |
| Android 16 API 契约确认 | ✅ 已确认（§3.2/附录；ProgressStyle 真实 API 与规划假设有偏差，以审计为准） |
| 小米字段官方确认 | ✅ 已确认（§3.1），阶段 5 可设计 |
| 多停靠点纯模型 | 未开始（模型测试通过前不接 Service） |
| Key/坐标泄漏 | 未发现 |

**下一步（按顺序）**：
1. 用户执行 P1-003 实体设备 14 步验收（重点覆盖：STOP→快速 START 的世代隔离新路径、STOP 后 RESUME 不再误报 FAILED、暂停/继续/锁屏/Service 重建），记录证据至 `追踪.md`；
2. 验收通过后按 §16 进入阶段 3（统一通知抽象 + API 29–35 回退）：优先采用 `NotificationCompat.ProgressStyle`（androidx.core ≥ 1.17.0，<36 自动回退）而非平台类双分支；子代理 A/B 的契约结论作为阶段 4/5 输入；
3. 阶段 8 需补 `values-de` 缺失的 47 个步行相关 key；阶段 10 顺手补 `unit_meters(_per_second)` 的 zh 值。

## 8. 回滚策略（规划 §18）

本轮改动可整体独立回滚：世代隔离涉及 7 个文件均为增量修改，回滚实现、保留 `WalkingSessionPolicyTest`/`WalkingCommandPolicyTest` 中不依赖实现的策略测试即可；旧一代行为（无世代校验）在共享键缺失时自然退化——`getWalkingSessionId()` 返回 null、`mayWrite` 拒绝写入的仅是"远程偏好不可达"场景（与旧代码的 no-op 等价），无数据迁移负担。禁止 `git reset --hard`。

---

## 附录：子代理 A 报告（Android 官方系统能力契约核对，2026-09-19）

### A.1 Android 16 Live Updates 与 Notification.ProgressStyle（API 36）

- 平台类 `android.app.Notification.ProgressStyle`，无参构造，方法：`addProgressSegment(Segment)`、`addProgressPoint(Point)`、`setProgress(int)`、`setProgressSegments/setProgressPoints(List)`、`setProgressTrackerIcon(Icon)`、`setProgressStartIcon/EndIcon(Icon)`、`setProgressIndeterminate`、`setStyledByProgress`（`setStyledByProgress(false)` = 已走/未走不同样式）。
- `Segment(int length)`：length ≥ 1，位置系统自动排布，所有段长之和 = `getProgressMax()`（无段时默认 100）；`setColor(int)`、`getId/setId`。
- `Point(int position)`：position ≥ 1（相对 progressMax），可乱序添加（系统排序）；`setColor`、`getId/setId`；**无 icon**。
- Live Update 资格：样式 ∈ {Standard, BigText, CallStyle, ProgressStyle, MetricStyle}；manifest 声明 `POST_PROMOTED_NOTIFICATIONS`（非运行时权限，protection `normal|appops`，Added 36.1，是 POST_NOTIFICATIONS 的附加项）；经 `EXTRA_REQUEST_PROMOTED_ONGOING`（Compat：`setRequestPromotedOngoing`）主动请求；`FLAG_ONGOING_EVENT`；contentTitle 必需。
- 禁令：RemoteViews（customContentView）、group summary、`setColorized(true)`、channel `IMPORTANCE_MIN`。
- 用户授权：`NotificationManager.canPostPromotedNotifications()`（API 36）；`hasPromotableCharacteristics()` 不含用户开关；用户可随时 demote 为普通通知；划掉后勿重发（`setDeleteIntent` 感知）。
- 【未确认】AppOp 公开常量名；设置跳转 action 官方两页矛盾（`ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` vs `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`），以 AOSP 实测为准。
- 来源：developer.android.com/reference/android/app/Notification.ProgressStyle（及 .Segment/.Point）、/about/versions/16/features/progress-centric-notifications、/develop/ui/views/notifications/live-updates、/reference/android/Manifest.permission、/reference/android/app/NotificationManager。

### A.2 AndroidX 兼容层

- androidx.core 最新稳定 **1.19.0**（2026-06-03）；`NotificationCompat.ProgressStyle` + `setRequestPromotedOngoing()` 自 **1.17.0 稳定版**（2025-08-13）可用。
- Compat 版在 API < 36 自动回退默认通知样式（官方参考页原文），Compat 路线无需 `SDK_INT` 分支；若直接用平台类，防护写法 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA`（=36）。
- Compat API 签名与平台一致，icon 参数为 `IconCompat?`；整体色用 `Builder#setColor`。
- 来源：/jetpack/androidx/releases/core、/reference/kotlin/androidx/core/app/NotificationCompat.ProgressStyle、/reference/android/os/Build.VERSION_CODES。

### A.3 前台服务 location 类型（API 29–36）

- 分层契约：API 28+ `FOREGROUND_SERVICE`；API 29+ `foregroundServiceType="location"` 与三参 `startForeground`；API 34 起强制声明类型 + `FOREGROUND_SERVICE_LOCATION`（normal|instant）；运行时前提：定位服务开启 + 已授 `ACCESS_COARSE/FINE_LOCATION` 至少其一。
- 后台启动限制（Android 14+）：后台创建 location FGS 抛 `SecurityException`；豁免：系统组件、**通知交互启动**、widget、其他可见 App 的 PendingIntent、DPC 等 → Tile 点击/通知按钮/前台 Activity 均为安全路径。
- Android 15 `BOOT_COMPLETED` 禁启列表不含 location；Android 16 行为变更无 location FGS 新增条目。
- `startForegroundService()` 后必须尽快 `startForeground()`（【未确认】具体秒数，惯例约 5s）。
- 来源：/develop/background-work/services/fg-service-types、/fgs/restrictions-bg-start（含豁免清单原文）、/about/versions/15/behavior-changes-15、/about/versions/16/behavior-changes-16、/reference/android/app/Service。

### A.4 Quick Settings Tile

- 生命周期：`onTileAdded/onTileRemoved/onStartListening/onStopListening/onClick`（API 24）；`getQsTile()` 仅 listening 区间有效。
- 模式：公开 API **无** `TILE_MODE`/"passive tile"；`META_DATA_ACTIVE_TILE`（"android.service.quicksettings.ACTIVE_TILE"）声明 active 模式，否则默认 non-active；`META_DATA_TOGGLEABLE_TILE`（API 30）；`META_DATA_TILE_CATEGORY`/`CATEGORY_*`（36.1，Optional but recommended）。
- 刷新：`requestListeningState(Context, ComponentName)` 仅对 active tile 有效；更新后必须 `qsTile.updateTile()`。
- 跳转：`startActivityAndCollapse(Intent)` API 34 弃用 → `startActivityAndCollapse(PendingIntent)`（Intent 需 `FLAG_ACTIVITY_NEW_TASK`）。Android 13+ `requestAddTileService()` 引导添加。
- 来源：/reference/android/service/quicksettings/TileService、/Tile、/develop/ui/views/quicksettings-tiles。

### A.5 App Shortcuts

- 三类：static（shortcuts.xml）、dynamic（ShortcutManager/Compat）、pinned（API 26+，用户钉住，不占额度，App 只能 disable 不能删）。仅 `ACTION_MAIN`+`CATEGORY_LAUNCHER` 的 Activity 可承载；meta-data `android.app.shortcuts` 放在（alias 的）launcher activity 上。
- 上限：多数启动器 static+dynamic 合计显示 4 个，精确值 `getMaxShortcutCountPerActivity()`；`<shortcut>` 标签属性：`shortcutId`/`enabled`/`icon`（不可 tint）/`shortcutShortLabel`(≤10)/`shortcutLongLabel`(≤25)/`shortcutDisabledMessage`；每个 shortcut 至少一个带 `android:action` 的 `<intent>`，末尾 intent 决定落地页。
- 动态管理：`disableShortcuts/enableShortcuts`（API 25）、`removeDynamicShortcuts`、`requestPinShortcut`（API 26）；推荐 `ShortcutManagerCompat.pushDynamicShortcut`（容量满 LRU 淘汰；`addDynamicShortcuts` 满时抛异常）。
- intent 直达 Service：官方无明文禁止但有推断风险 → **推荐透明中转 Activity → `startForegroundService()`**（配合 A.3 的通知交互豁免路径）。
- 来源：/develop/ui/views/launch/shortcuts、/launch/shortcuts/creating-shortcuts、/reference/android/content/pm/ShortcutInfo、/reference/android/content/pm/ShortcutManager（该页抓取不完整，签名以 API 25 稳定契约交叉印证）。
