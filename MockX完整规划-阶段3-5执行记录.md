# MockX 完整功能规划 — 阶段 3/4/5 执行记录（统一通知、Live Updates、小米超级岛）

> 依据：`MockX完整功能规划.md` §16 推荐顺序的「阶段 3（通知统一抽象 + 低版本回退）」「阶段 4（Android 16 Live Updates）」「阶段 5（小米超级岛）」，设计蓝本为 `通知体验升级规划.md`，官方契约以 2026-09-19 调研结论为准（调研存档：`/Volumes/SanDisk/Developer/Caches/mockx-research/`）。
>
> 执行日期：2026-09-19
>
> 关联文档：`MockX完整规划-阶段0-1执行记录.md`（上一轮）、`追踪.md`（P1-004 真机验收项）

---

## 1. 范围与前置条件

- ✅ 阶段 0/1/2 已完成（见阶段 0-1 执行记录）；本轮按用户指令在 P1-003 真机验收就绪的前提下推进通知增强，真机验收合并为 P1-004 一并执行。
- ✅ 官方契约调研完成：Android Live Updates（developer.android.com live-update / progress-centric-notifications / platform-samples 官方示例）与小米超级岛（dev.mi.com pId=2131/2146、《小米超级岛模板库》20260129 版 PDF、社区 HyperBridge/livebridge/Gramophone 实现、HyperIsland-ToolKit 源码）。

## 2. 架构落地（通知体验升级规划.md §6）

新增 `manager/notification/` 包，五个文件全部按设计文档职责划分：

| 文件 | 职责 |
| --- | --- |
| `WalkingNotificationState.kt` | 统一状态模型：phase、0..1000 千分比进度、travelled/total、ETA、单调 sequence；`fromSession()` 工厂按相位规则装配（ARRIVED 钉满进度、仅 WALKING 有 ETA）；phase→Action 映射 |
| `NotificationContentFormatter.kt` | 纯文本规则（`NotificationTextResolver`，JVM 可测）+ Context 资源装配；距离格式化沿用既有 "850 m / 1.24 km" 规则 |
| `AndroidLiveUpdateAdapter.kt` | 纯 `LiveUpdateStyleSpec` 推导（可测）+ `@RequiresApi(36)` 薄映射：`NotificationCompat.ProgressStyle`（单段 1000 刻度 + `styledByProgress(true)` + tracker 图标）+ `setRequestPromotedOngoing(true)`；SDK_INT 守卫确保 API 36 类低版本不加载（§7.5） |
| `XiaomiIslandPayload.kt` | 纯 Gson `JsonObject` payload 构建（零新依赖）：`param_v2` protocol=3、business=mockx.walking、updatable=true、enableFloat=false、islandFirstFloat=false、ticker/aodTitle、progressInfo（0..100 线性进度条）、baseInfo(type=2)、param_island（大岛 imageTextInfoLeft + 小岛 picInfo，引用 `miui.focus.pic_island`）；构建失败返回 null |
| `XiaomiIslandAdapter.kt` | 低成本设备门禁（`Build.MANUFACTURER == Xiaomi`）+ extras 注入（`miui.focus.param` JSON + `miui.focus.pics` 应用图标 Bundle）；失败静默退化，不自行 notify |
| `WalkingNotificationBuilder.kt` | 统一 Builder：标准基座（标题/内容/进度条/Action/CHANNEL/CATEGORY_NAVIGATION）→ WALKING 期 ETA 倒计时 chronometer → API 36+ 注入 Live Update → 小米设备注入超级岛 extras → 单一通知 ID/单一 Channel |

**sequence 决策**：官方字段表标注 `sequence` 为 MIPUSH 专用（本地 `notify()` 无此要求，HyperIsland-ToolKit 的 ParamV2 亦无此字段），故 sequence 只留在 `WalkingNotificationState` 做 Service 侧乱序丢弃守卫，不写入小米 JSON——避免未确认字段。

## 3. Service 集成改动（`WalkingSimulationService.kt`）

- `startInForeground(phase)` 显式传相位：命令路径（START/RESUME→WALKING、PAUSE→PAUSED）不依赖可能滞后一次串行写的共享偏好，消除“START 后短暂无 Action 通知”窗口。
- `buildState(phase)` 每次装配自增 `AtomicLong` sequence；`updateNotification(phase)` 串行锁内做双守卫：**节流**（同相位 2 秒内跳过，`NOTIFICATION_UPDATE_INTERVAL_MS=2000`，规划 §9.6）+ **sequence 乱序丢弃**；异常仅记日志，绝不停止模拟。
- 通知路径收敛：tick→`updateNotification(WALKING)`（2s 节流）、暂停/到达→即时（相位变化即触发）；START/RESUME/恢复路径的 `startForeground` 即首个通知，删除冗余更新调用。
- 旧 `buildNotification()/progressPerMille()/formatMeters()/createNotificationChannel()` 全部迁出 Service；`formatMeters` 行为由 `NotificationContentFormatter.formatDistance` 等价承接（含非有限值守卫）。
- 通道（`walking_simulation`，IMPORTANCE_LOW）与通知 ID（4201）不变，老用户升级无重复渠道。

## 4. 依赖升级与连带影响（如实记录）

- `androidx.core:core-ktx 1.10.1 → 1.19.0`（`NotificationCompat.ProgressStyle` + `setRequestPromotedOngoing` 硬性要求 ≥1.17.0；compileSdk 36 已满足）。
- **连带**：core 1.19.0 传递约束把 Compose 全栈从 BOM 2024.04.01（1.6.x）整体解析升级至 **runtime/ui/foundation/animation 1.11.2 + material3 1.4.0 + lifecycle-viewmodel-compose 2.9.4**，整栈内部自洽（androidx 向后二进制兼容）。Debug APK 83.4MB→125.4MB（未收缩 compose dex 膨胀），Release 4.68MB→4.69MB（R8 收缩后基本不变）。**UI 界面需随 P1-004 一并真机回归**；后续可选项：显式升级 Compose BOM 使版本声明与实际解析一致。
- 未新增任何第三方依赖（小米 payload 用已有 Gson 构建；评估过 HyperIsland-ToolKit `io.github.d4viddf:hyperisland_kit`，Apache 2.0，Maven Central 0.4.4——因需引入 kotlinx-serialization 且我们只需其最小字段子集，按最小影响原则未采用，其字段模型作为参考已验证）。

## 5. 权限与文案

- Manifest 新增 `POST_PROMOTED_NOTIFICATIONS`（normal|appops 非运行时权限；aapt2 badging 已核验进入 APK）。
- **零新增字符串**：全部复用既有 `walk_notification_*`、`walk_status_*`、`walk_pause/resume/stop`、`walk_eta_format`（en/zh 双语齐备）；values-de 的既有缺口不变（本轮未扩大）。

## 6. 验证证据

- `./gradlew :app:testDebugUnitTest --no-daemon`：**87/87 通过**（上轮 54 + 本轮 33：`WalkingNotificationStateTest` 10、`NotificationContentFormatterTest` 8、`AndroidLiveUpdateAdapterTest` 5、`XiaomiIslandPayloadTest` 10）。
- `./gradlew assembleDebug assembleRelease -PappVersionName=v1.0beta --no-daemon`：成功（R8 kotlin-metadata 警告为既有已知项）。
  - `app-debug.apk` 125,383,335 B，SHA-256 `f8c914758467ed9481941c5aca141af2775b6697b51178348c4c246b9572ebdb`
  - `app-release.apk` 4,693,249 B，SHA-256 `87f0dd875887a857f54706bdec6a0ecf2601e9e55cfcd09436211a43d9fd64b4`
- APK 安全检查（release dex strings）：32 位十六进制 Key 模式 **0**；`restapi.amap.com` 仅 1 处（既有客户端常量）；测试坐标（37.7749/116.397）**0**；`ProgressStyle` 类已保留（4 处引用）；无路线 JSON/日志 dump。
- `git diff --check`：通过。

## 7. 已知边界与未验证项（待 P1-004 真机）

1. **Live Update 推广展示**依赖用户在系统设置开启 Live Updates（`canPostPromotedNotifications()`），关闭时按官方规则退化为普通通知——无法在 JVM 验证。
2. **超级岛实机渲染**：未申请小米平台权限时 `canShowFocus` 返回值与 `miui.focus.param` 实际渲染行为因 ROM 而异（社区证据可上岛、无官方承诺）；`filterWhenNoPermission` 默认 false 保证权限关闭时通知正常显示，退化路径安全。
3. ETA 倒计时 chronometer 在 API 29–35 标准通知头部的显示样式未经真机确认。
4. `progressInfo` 进度条在超级岛摘要态/展开态的实际渲染位置需真机确认（字段本身已由官方模板库与社区实现双源验证）。
5. FAILED/STOPPING 相位仍维持“直接移除通知”的既有行为（设计文档 §4.1 的失败通知为后续增强，本轮未扩 scope）。

## 8. 回滚策略

本轮全部为增量：通知包 6 个文件可整体删除回退；Service 改动集中于通知 region，`git revert` 单提交或按文件还原即可恢复旧行为（`formatMeters` 等价性有单测背书）；androidx.core 回退 1.10.1 需同时删除 `AndroidLiveUpdateAdapter.apply` 的 ProgressStyle 调用与 Manifest 权限。禁止 `git reset --hard`。
