# MockX 完整功能扩展与步行模拟规划

> 项目：`/Volumes/SanDisk/Projects/XposedFakeLocation`
>
> 目标：在现有固定虚拟定位和高德步行模拟基础上，完成可靠的路线模拟、系统通知体验、路线组织和 Android 系统集成。
>
> 日期：2026-09-19
>
> 状态：规划文档，不在本次任务中实现代码。
>
> 相关文档：`规划.md`、`追踪.md`、`通知体验升级规划.md`

---

## 1. 总体目标

最终形成以下完整能力：

1. 固定位置虚拟定位。
2. 高德步行路线规划。
3. 沿道路路线连续模拟移动。
4. 暂停、继续、停止、到达和失败恢复。
5. 切换 App、锁屏和后台期间继续运行。
6. Android 9–15 标准前台进度通知。
7. Android 16 官方 Live Updates / `Notification.ProgressStyle`。
8. 小米 HyperOS 超级岛增强通知。
9. 多停靠点步行路线。
10. 到点提醒和路线完成提醒。
11. 已走/未走路线渲染。
12. 收藏分组和常用路线管理。
13. 中国大陆边界与坐标系精度增强。
14. 中英文及现有语言的本地化完善。
15. Quick Settings Tile、App Shortcuts 和受控 Intent。
16. 完整的测试、构建、真机验收和回滚能力。

总原则：

```text
路线规划、步行状态、共享位置状态是核心事实来源；
通知、地图、快捷控制和超级岛都是展示/控制层，不能各自维护第二套状态。
```

---

## 2. 当前基线与约束

### 2.1 已有能力

当前项目已经具备：

- Xposed/LSPosed 定位 Hook；
- 固定位置虚拟定位；
- 目标 App 过滤；
- system_server 位置服务 Hook；
- Wi-Fi、基站等位置泄漏防护；
- osmdroid 地图；
- 高德瓦片图源；
- GCJ-02/WGS-84 转换；
- 高德步行路线客户端和 polyline 解析；
- 路线插值和速度推进；
- 前台 `WalkingSimulationService`；
- 暂停、继续、停止和到达状态；
- 共享 Preference 状态；
- 当前标准前台通知；
- Android `minSdk = 29`、`targetSdk = 36`、`compileSdk = 36`；
- 多语言资源基础；
- 单元测试和 Debug/Release 构建流程。

### 2.2 已知未完成项

`追踪.md` 中的核心设备验收仍需完成：

- 实体设备路线规划；
- 动态位置注入；
- 暂停/继续/停止；
- 锁屏后台运行；
- Service 重建；
- 终态清理；
- Wi-Fi/基站防泄漏；
- 测试 Key 清除和重置。

### 2.3 不得破坏的行为

任何新功能都不得破坏：

- 固定位置模式；
- 现有目标 App 包名过滤；
- system_server Hook；
- Wi-Fi/基站防泄漏；
- GCJ-02/WGS-84 现有正确区域行为；
- 现有收藏、历史和设置数据；
- 外部控制默认关闭策略；
- 现有构建和 Release 打包流程。

---

## 3. 总体架构

```text
┌────────────────────────────────────────────┐
│ Manager App                                 │
│                                            │
│ 地图选点 / 收藏 / 多停靠点                  │
│          ↓                                 │
│ 真实位置获取                               │
│          ↓                                 │
│ 高德步行路线 API                            │
│          ↓                                 │
│ GCJ-02 → WGS-84                             │
│          ↓                                 │
│ 路线模型 / 分段模型                         │
│          ↓                                 │
│ WalkingSimulationService                    │
│          ↓                                 │
│ 共享动态位置 + 会话状态                     │
│          ├─ 地图路线和 Marker                │
│          ├─ 标准前台通知                     │
│          ├─ Android 16 Live Update          │
│          ├─ Xiaomi HyperIsland              │
│          ├─ Quick Settings / Shortcuts      │
│          └─ 到点提醒                         │
└──────────────────┬─────────────────────────┘
                   ↓
┌────────────────────────────────────────────┐
│ Xposed / system_server                      │
│ 读取唯一动态位置状态并构造 Location          │
└────────────────────────────────────────────┘
```

### 3.1 坐标边界

统一采用：

```text
高德 API / 高德地图显示：GCJ-02
核心路线 / 共享状态 / Android Location：WGS-84
```

规则：

- 路线核心模型只保存 WGS-84；
- 高德 API 请求前按接口要求转换坐标；
- 高德 polyline 返回后转换为 WGS-84；
- 高德图源绘制时 WGS-84 转回 GCJ-02；
- OSM/天地图等 WGS-84 图源直接绘制；
- Xposed 注入始终使用 WGS-84；
- 任何单一坐标不得重复转换。

---

## 4. 阶段 0：合规、基线和任务分工

### 4.1 官方资料核对

执行前核对最新官方文档：

- 高德路径规划 2.0 步行 API；
- Android Live Updates；
- `Notification.ProgressStyle`；
- Android 前台服务和位置类型权限；
- 小米 HyperOS 岛通知；
- Android Quick Settings Tile 和 App Shortcuts。

### 4.2 仓库基线

记录：

- `git status --short --branch`；
- 当前 HEAD；
- JDK、Gradle、AGP、Android SDK；
- 现有测试数量；
- Debug/Release 构建结果；
- 当前 APK SHA-256；
- 当前设备和 LSPosed 配置；
- 不覆盖工作区已有无关改动。

### 4.3 子代理规则

可以使用子代理，但必须遵守：

- 只读审计任务可以并行；
- 可写任务必须使用独立 worktree；
- 可写任务必须有互不重叠的文件范围；
- 共享 Service、Manifest、Preference schema、最终 UI 和工程配置由主代理整合；
- 子代理必须报告修改文件、测试命令和未验证风险；
- 主代理必须复核 diff，不能只接受“已完成”描述。

### 4.4 阶段 0 可交给子代理

#### [子代理-只读 A] Android 官方系统能力审计

核对：

- Live Updates API 36 要求；
- `ProgressStyle` 方法；
- 推广通知权限；
- 前台服务位置限制；
- Quick Settings 和 App Shortcuts 生命周期。

不得修改生产文件。

#### [子代理-只读 B] 小米超级岛官方契约审计

核对：

- `miui.focus.param`；
- 官方模板字段；
- `sequence`、`updatable`、`business`；
- 本地客户端通知和 MIPush 的边界；
- 最小步行场景 payload。

不得修改生产文件。

#### [子代理-只读 C] 当前步行 Service/Hook 审计

输出：

- 状态调用链；
- 停止、失败、到达竞态；
- 共享状态读写顺序；
- 通知和定位状态不同步风险。

不得修改生产文件。

#### [子代理-只读 D] 当前 UI、设置、本地化审计

扫描：

- 用户可见硬编码文本；
- 设置页可复用组件；
- 收藏数据结构；
- 地图交互入口；
- 语言资源缺失。

---

## 5. 阶段 1：步行会话可靠性

这是所有后续功能的决策门。未完成前，不开始多停靠点、通知增强和快捷控制的最终整合。

### 5.1 会话世代隔离

新增 `sessionId` 或 `generationId`：

- 每次开始新会话生成新 ID；
- Service、tick、恢复、停止、失败和到达写入携带 ID；
- 旧任务写共享状态前检查 ID；
- ID 不匹配时丢弃旧回调；
- 到达与停止同时发生时只有一个终态生效。

### 5.2 幂等状态清理

状态流程必须保证：

```text
取消 tick
→ 串行完成共享状态写入
→ 清理动态位置状态
→ 设置 is_playing
→ 移除通知
→ 释放 WakeLock
→ stopSelf
```

要求：

- START 重复调用不产生多个 tick；
- STOP 重复调用不崩溃；
- FAILED 清理与 STOP 清理互斥；
- Service 被销毁时关键清理不被取消；
- 清理完成后 Xposed 不再读取旧动态位置。

### 5.3 时间和状态新鲜度

统一使用 `System.currentTimeMillis()` 记录共享持久化状态的更新时间，读取时使用相同基准：

```text
age < 0       → 按时钟回拨处理，不永久判定有效
age <= 15s    → 有效
age > 15s     → WALKING 动态状态过期
```

`SystemClock.elapsedRealtime()` 只用于进程内 tick 间隔和日志限流，不能和 Unix 时间戳相减。

### 5.4 暂停/继续

- `WALKING -> PAUSED`；
- `PAUSED -> WALKING`；
- 暂停不增加路线距离；
- 暂停保持最后有效坐标；
- 无 ticker 的暂停命令仍必须写入 PAUSED；
- Service 重建后 PAUSED 不自动恢复行走；
- 通知、地图 UI、共享 Preference、Hook 观察结果一致。

### 5.5 可交给子代理

#### [子代理-可写 A]

只新增纯 Kotlin 状态策略和测试：

```text
app/src/test/java/.../manager/walking/
app/src/test/java/.../xposed/utils/
```

不得修改 Service、Manifest 和 Preference。

#### [子代理-只读 B]

建立 START/PAUSE/STOP/ARRIVED/FAILED 时序矩阵和异常恢复测试设计。

### 5.6 主代理任务

- 整合 `sessionId`；
- 修改 Service；
- 修改共享状态写入；
- 修改 Xposed 动态状态有效性；
- 完成实体设备压力测试。

---

## 6. 阶段 2：高德步行路线与基础模拟

### 6.1 Key 管理

- 设置页输入高德 Web 服务 Key；
- 保存到本地设置；
- 不写入源码、资源、BuildConfig、APK、Git 和日志；
- 清除时删除本地值；
- 测试完成后在高德控制台重置测试 Key。

### 6.2 路线接口

使用高德路径规划 2.0 步行接口：

```text
https://restapi.amap.com/v5/direction/walking
```

规则：

- 真实位置作为起点；
- 地图选点作为终点；
- 发送经度在前、纬度在后的参数；
- 请求 `show_fields` 获取 polyline 和 cost；
- 校验 HTTP 状态和业务状态；
- 不在 Xposed Hook/system_server 发网络请求；
- 所有网络 I/O 在 `Dispatchers.IO`；
- 超时、无路线、Key 错误、配额错误分别处理。

### 6.3 路线解析

- 解析 `route.paths[].steps[].polyline`；
- 兼容分段 polyline 格式；
- 校验经纬度范围；
- 去除相邻重复点；
- 限制路线点数量；
- 转换为 WGS-84；
- 计算累计距离；
- 保存 API 报告距离作为校验信息；
- 不把路线数据写入日志。

### 6.4 基础步行模型

```kotlin
data class WalkingRoute(
    val origin: Coordinate,
    val destination: Coordinate,
    val points: List<Coordinate>,
    val totalDistanceMeters: Double,
    val expectedDurationSeconds: Int?,
)
```

速度预设：

```text
轻松：1.0 m/s
正常：1.4 m/s
快速：1.8 m/s
```

推进要求：

- 按真实 elapsed 时间推进；
- 按累计距离查找路线段；
- 线性插值坐标；
- 计算 bearing；
- 设置 Location speed/bearing/time/elapsed realtime；
- 到达后固定在终点。

### 6.5 基础 UI

- 选择目标点；
- 规划路线；
- 预览路线；
- 显示距离和预计时间；
- 选择速度；
- 开始/暂停/继续/停止；
- 规划失败可重试；
- 地图交互在活动会话期间锁定；
- 地图路线显示遵循坐标图源转换规则。

### 6.6 基础验收

- 真实起点不来自当前虚拟位置；
- 高德路线贴合道路；
- 目标 App 收到连续变化坐标；
- 不跳回真实位置；
- 停止后恢复真实位置；
- 固定位置模式无回归。

---

## 7. 阶段 3：多停靠点路线

### 7.1 目标

扩展：

```text
真实起点 → 停靠点 A → 停靠点 B → 停靠点 C → 终点
```

第一版最多 5 个停靠点，避免路线 API 调用、UI 和状态复杂度失控。

### 7.2 数据模型

```kotlin
data class WalkingStop(
    val id: String,
    val coordinate: Coordinate,
    val name: String?,
    val order: Int,
)

data class WalkingRouteSegment(
    val fromStopId: String,
    val toStopId: String,
    val route: WalkingRoute,
)

data class MultiStopWalkingRoute(
    val stops: List<WalkingStop>,
    val segments: List<WalkingRouteSegment>,
    val totalDistanceMeters: Double,
)
```

核心模型仍统一保存 WGS-84。

### 7.3 规划流程

- 起点到第一个停靠点；
- 停靠点之间逐段请求；
- 最后停靠点到终点；
- 所有段成功后才能进入 READY；
- 任一段失败，整个规划失败；
- 分段路线可缓存，但不能使用过期或坐标不匹配的缓存；
- API 调用应有速率限制和取消支持。

### 7.4 Service 行为

- 当前段索引；
- 当前段距离；
- 总路线距离；
- 到达停靠点后进入下一段；
- 每个停靠点触发一次到点事件；
- 支持暂停等待；
- 支持停止后清理全部段状态；
- 多段路线到达终点才进入 ARRIVED。

### 7.5 可交给子代理

#### [子代理-可写 C]

独立实现纯 Kotlin 多停靠点模型、合并距离算法和单元测试。

写入范围：

```text
app/src/main/java/.../manager/route/MultiStop*.kt
app/src/test/java/.../manager/route/MultiStop*.kt
```

不得修改 Service、MapScreen、Preference schema。

#### [子代理-只读 D]

设计多停靠点 API 调用次数、失败恢复和缓存测试矩阵。

### 7.6 主代理任务

- 接入 MapViewModel；
- 接入地图停靠点编辑；
- 接入 Preference/Codec；
- 接入 Service；
- 完成真机路线验收。

---

## 8. 阶段 4：路线渲染与到点提醒

### 8.1 路线显示

地图层增加：

- 未行走路线；
- 已行走路线；
- 当前路线段；
- 当前模拟 Marker；
- 起点 Marker；
- 停靠点 Marker；
- 终点 Marker；
- 当前停靠点突出显示。

路线数据不能因为显示颜色或图源切换而被修改。

### 8.2 到点判定

默认到达半径 10–30 米，可配置但第一版使用固定默认值：

```text
remainingDistance <= arrivalRadius
```

要求：

- 每个停靠点只触发一次；
- 到点通知和 Service 状态串行；
- 误差抖动不重复触发；
- 终点事件和停靠点事件区分；
- 到达后是否自动停止由设置决定，默认保留终点模拟位置。

### 8.3 通知事件

- 即将到达；
- 已到达停靠点；
- 路线全部完成；
- 规划失败；
- Service 异常停止。

不显示精确坐标，不把私人地点名默认显示在锁屏。

### 8.4 可交给子代理

#### [子代理-可写 D]

实现纯 Kotlin 到点判定、去重触发和事件策略测试。

不得修改 NotificationBuilder、Service 和 UI。

---

## 9. 阶段 5：统一通知、Android Live Updates 和小米超级岛

### 9.1 统一方案

只维护一条通知：

```text
标准 NotificationCompat
  + API 36 ProgressStyle（可用时）
  + miui.focus.param（小米增强时）
```

禁止为三种展示方式创建三个通知 ID。

### 9.2 通知状态模型

```kotlin
data class WalkingNotificationState(
    val phase: WalkingPhase,
    val progress: Int,
    val progressMax: Int,
    val travelledMeters: Double,
    val remainingMeters: Double,
    val remainingSeconds: Long?,
    val currentStopIndex: Int?,
    val totalStopCount: Int?,
    val currentStopName: String?,
    val sequence: Long,
)
```

所有通知后端消费同一状态，不各自读取 Preference 计算进度。

### 9.3 Android 官方 Live Updates

API 36+：

- 使用 `Notification.ProgressStyle`；
- Segment 表示路线段；
- Point 表示停靠点；
- Tracker icon 表示当前位置；
- 使用 ongoing、contentTitle 和推广请求；
- 不使用 RemoteViews；
- 不使用 group summary；
- Channel 不使用 IMPORTANCE_MIN；
- 用户关闭推广能力时退化为普通通知。

API 29–35：

- 继续使用 `NotificationCompat.setProgress()`；
- 保持标题、内容、Action、Channel 和通知 ID；
- 不加载 API 36 类路径；
- 低版本行为和当前通知一致。

需要核对并按当前 SDK 支持声明：

```xml
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

### 9.4 小米 HyperOS 超级岛

- 在现有 Notification extras 中加入 `miui.focus.param`；
- 使用官方已确认的最小模板；
- `sequence` 单调递增；
- `updatable = true`；
- 摘要态显示模拟步行、进度和状态；
- 展开态显示更完整的距离和操作；
- 不使用 MIPush；
- 不使用隐藏 API；
- JSON 失败时只退化到普通通知；
- 不能假定所有 HyperOS 机型都显示超级岛。

### 9.5 通知 Builder

建议新增：

```text
manager/notification/WalkingNotificationState.kt
manager/notification/WalkingNotificationBuilder.kt
manager/notification/AndroidLiveUpdateAdapter.kt
manager/notification/XiaomiIslandAdapter.kt
manager/notification/NotificationContentFormatter.kt
```

Service 只负责：

- 生成状态；
- 调用 Builder；
- startForeground/notify；
- 更新节流；
- 状态终态清理。

### 9.6 更新频率

- 位置更新：每秒；
- 通知更新：默认每 2 秒；
- 状态变化：立即；
- 到达/停止/失败：立即；
- 通知失败不能停止位置 Service。

### 9.7 子代理任务

#### [子代理-只读 E] Android Live Updates 审计

核对 API 36、ProgressStyle、推广权限和 AndroidX 兼容方法。

#### [子代理-只读 F] 小米超级岛审计

核对官方字段、模板和支持边界，输出最小安全 payload。

#### [子代理-可写 E] 通知纯模型和格式化

写入通知状态、文案格式化和测试，不改 Service。

#### [子代理-可写 F] Android Live Update Adapter

只写 API 36 适配器和测试，不改 Manifest/Service。

#### [子代理-可写 G] Xiaomi Adapter

只写 payload 和 extras 适配器，不调用 notify，不改 Service。

### 9.8 必须由主代理完成

- Manifest 权限；
- Notification Channel；
- Service 接入；
- sequence 来源；
- 终态清理；
- API 版本分支；
- 真实 Android 16/HyperOS 验收。

---

## 10. 阶段 6：收藏分组和常用路线

### 10.1 收藏分组

最小功能：

- 新建分组；
- 重命名分组；
- 删除分组；
- 收藏移动分组；
- 未分组默认兜底；
- 旧收藏自动迁移；
- 删除分组时收藏回退未分组。

不做：

- 嵌套分组；
- 云同步；
- 多用户共享；
- 复杂标签系统。

### 10.2 常用路线

支持保存：

- 起点/终点；
- 多停靠点顺序；
- 路线提供商；
- 坐标系版本；
- 速度偏好；
- 创建和最后使用时间。

路线缓存不得绕过重新规划的有效性检查。

### 10.3 可交给子代理

#### [子代理-可写 H]

独立实现收藏分组模型、迁移函数和单元测试。

不得修改 FavoritesScreen、SettingsScreen 和共享工程配置。

#### [子代理-只读 I]

审计当前收藏 JSON/Preference 格式和兼容迁移风险。

### 10.4 主代理任务

- 迁移旧收藏；
- 接入 UI；
- 接入多停靠点路线；
- 真机检查旧数据可读。

---

## 11. 阶段 7：中国大陆边界和坐标精度

### 11.1 当前问题

当前已有矩形范围判断，但边境、港澳台和海域区域需要更精确策略。

### 11.2 评估顺序

1. 建立固定回归点集；
2. 明确大陆、香港、澳门、台湾、境外和海域策略；
3. 评估简化多边形、栅格或轻量二进制边界；
4. 确认数据来源和许可证；
5. 控制 APK 体积和查询耗时；
6. 再决定是否加入离线边界资源。

不直接引入大型 GIS 依赖，不把未经许可的边界文件提交到仓库。

### 11.3 测试点

至少覆盖：

- 广州、深圳、北京；
- 香港、澳门、台湾；
- 中俄、中蒙、中越边境附近；
- 东京、伦敦、纽约；
- 海域点；
- 矩形边界外但接近中国的点。

### 11.4 可交给子代理

#### [子代理-只读 J]

审计候选边界数据来源、许可证、格式和体积。

#### [子代理-可写 I]

只新增坐标边界回归测试和测试数据，不修改生产转换算法。

### 11.5 主代理任务

- 选定数据源；
- 修改 CoordinateTransform；
- 评估包体积；
- 真实地点验收；
- 保证路线坐标转换不被二次影响。

---

## 12. 阶段 8：本地化和应用内语言切换

### 12.1 支持语言

至少保持：

- 跟随系统；
- 简体中文；
- English；
- 当前项目已有其他语言资源。

### 12.2 清理范围

扫描并替换：

- 步行状态；
- API 错误；
- Service 通知；
- Live Update 内容；
- 超级岛摘要；
- 到点提醒；
- 收藏分组；
- 快捷控制；
- 权限说明；
- 设置页。

通知和超级岛文案也必须来自资源，不在 Service 硬编码中文。

### 12.3 语言切换

- 复用现有 `LanguageOption`/`LocaleController`；
- 修改后刷新 Activity；
- 通知 Channel 名称受系统限制，必要时引导用户到系统设置；
- 当前进行中的通知下一次更新使用新语言；
- 不改变路线和位置状态。

### 12.4 可交给子代理

#### [子代理-只读 K]

扫描用户可见硬编码文本并输出文件/行号。

#### [子代理-可写 J]

只补充字符串资源和资源测试，不修改业务逻辑。

### 12.5 主代理任务

- 统一资源 key；
- 检查 Compose 预览和通知；
- 真机切换语言验收。

---

## 13. 阶段 9：Android 系统快捷控制

### 13.1 Quick Settings Tile

实现一个可选 Tile：

- 未运行：开始最近一次保存路线或打开 App；
- 行走中：暂停；
- 已暂停：继续；
- 已到达：打开 App 或停止；
- 无保存路线：打开地图页面。

Tile 只发送受控 Service 命令，不直接修改路线坐标。

### 13.2 App Shortcuts

提供：

- 开始最近路线；
- 暂停步行；
- 继续步行；
- 停止并恢复真实位置；
- 打开地图。

### 13.3 受控 Intent/Broadcast

继续沿用现有外部控制默认关闭策略：

- 不允许传入高德 Key；
- 不允许第三方注入未验证 route JSON；
- 坐标和速度严格校验；
- 只开放明确动作；
- 显式组件优先；
- 失败有可观察日志；
- 不泄露敏感数据。

### 13.4 可交给子代理

#### [子代理-可写 K]

独立实现 Tile/Shortcut 的纯动作映射和测试桩，不修改 Service 状态机。

#### [子代理-只读 L]

审计外部广播权限、默认关闭行为和安全边界。

### 13.5 主代理任务

- Manifest 组件；
- Service 命令接入；
- Tile 状态刷新；
- 真机通知栏/桌面/锁屏验证。

---

## 14. 阶段 10：测试、构建和安全验收

### 14.1 单元测试

必须覆盖：

- 高德响应解析；
- 坐标转换；
- 路线点去重；
- 距离和插值；
- bearing；
- 时间基准和过期；
- 会话世代隔离；
- STOP/FAIL 清理；
- PAUSE 无 ticker；
- 多停靠点模型；
- 到点事件去重；
- 通知 phase/action/progress；
- Android 16 adapter；
- 小米 payload；
- sequence；
- 收藏迁移；
- 边界点；
- 本地化资源；
- Tile/Shortcut 命令。

### 14.2 构建命令

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
./gradlew assembleRelease --no-daemon
git diff --check
```

使用 JDK 21，不在共享 `gradle.properties` 保存机器专属绝对路径。

### 14.3 APK 安全检查

- 不包含高德 Key；
- 不包含精确测试坐标；
- 不包含路线 JSON；
- 不包含 Logcat 或测试 dump；
- 不引入未使用的厂商 SDK；
- Manifest 权限和 Service 正确；
- API 36 类有低版本保护；
- 没有重复通知 ID；
- 没有重复前台 Service。

### 14.4 实体设备矩阵

至少：

1. Android 9–15 非小米设备；
2. Android 16 设备或模拟器；
3. 支持超级岛的 Xiaomi/HyperOS 设备；
4. 已启用 LSPosed 的实际目标设备。

每台设备验证：

- 路线规划；
- 位置推进；
- 通知展示；
- 暂停/继续；
- 锁屏；
- Service 重建；
- 到达；
- 停止；
- 真实位置恢复；
- 目标 App 不回跳真实位置；
- Wi-Fi/基站泄漏防护。

### 14.5 Key 清理

验收完成后：

- 清除 App 本地测试 Key；
- 在高德控制台重置 Key；
- 扫描 Git、APK、日志和文档；
- 不把 Key 写入最终验收报告。

---

## 15. 子代理总任务表

| 编号 | 任务 | 类型 | 是否可并行 | 写入范围 |
|---|---|---|---|---|
| A | Android 官方 API 审计 | 子代理-只读 | 是 | 无 |
| B | 小米超级岛官方契约审计 | 子代理-只读 | 是 | 无 |
| C | 步行 Service/Hook 审计 | 子代理-只读 | 是 | 无 |
| D | UI/设置/本地化审计 | 子代理-只读 | 是 | 无 |
| E | 状态策略和时序测试 | 子代理-可写 | 是 | 测试目录 |
| F | 多停靠点模型 | 子代理-可写 | 是 | 独立 route 文件 |
| G | 到点判定算法 | 子代理-可写 | 是 | 纯 Kotlin 文件/测试 |
| H | 通知状态与格式化 | 子代理-可写 | 是 | notification 纯模型 |
| I | Android Live Update Adapter | 子代理-可写 | 是 | LiveUpdate 文件 |
| J | Xiaomi Adapter | 子代理-可写 | 是 | Xiaomi 文件 |
| K | 收藏分组模型/迁移 | 子代理-可写 | 是 | 独立模型/测试 |
| L | 边界数据审计 | 子代理-只读 | 是 | 无 |
| M | 边界回归测试 | 子代理-可写 | 是 | 测试数据/测试 |
| N | 硬编码文本扫描 | 子代理-只读 | 是 | 无 |
| O | 字符串资源补齐 | 子代理-可写 | 是 | 资源目录 |
| P | Tile/Shortcut 映射 | 子代理-可写 | 是 | 独立组件/测试 |
| Q | 外部控制安全审计 | 子代理-只读 | 是 | 无 |

### 必须由主代理完成

- 最终架构决策；
- 共享 Preference schema；
- `WalkingSimulationService`；
- Xposed 动态状态接入；
- Manifest/Gradle/依赖；
- MapScreen/MapViewModel 主流程；
- SettingsScreen 主流程；
- 多停靠点最终整合；
- 通知 Builder 最终整合；
- Tile/Shortcut 最终整合；
- 实体设备验收；
- APK 和密钥安全检查；
- 追踪文档更新。

---

## 16. 推荐执行顺序

```text
阶段 0：基线、官方 API、许可和安全审计
    ↓
阶段 1：步行 Service 状态可靠性和实体设备验收
    ↓
阶段 2：高德基础路线模拟闭环
    ↓
阶段 3：通知统一抽象和 Android 低版本回退
    ↓
阶段 4：Android 16 Live Updates
    ↓
阶段 5：小米超级岛
    ↓
阶段 6：多停靠点路线
    ↓
阶段 7：路线渲染和到点提醒
    ↓
阶段 8：收藏分组和常用路线
    ↓
阶段 9：中国大陆边界精度
    ↓
阶段 10：本地化和应用内语言切换
    ↓
阶段 11：Quick Settings、App Shortcuts、受控 Intent
    ↓
阶段 12：完整构建、真机矩阵和发布前清理
```

### 决策门

- 步行状态机未通过真机验收：暂停所有通知增强和快捷控制整合；
- Android 16 API 契约未确认：不写 ProgressStyle 生产代码；
- 小米字段未被官方文档确认：只做普通通知，不使用猜测字段；
- 坐标边界资源无许可证：不加入 APK；
- 多停靠点模型未通过纯算法测试：不接入 Service；
- 通知适配失败：必须能回退到标准前台通知；
- 任何 Key/坐标泄漏：停止发布验收并清理。

---

## 17. 完成定义

以下全部满足才算完整功能完成：

- [ ] 固定位置功能无回归。
- [ ] 高德步行路线可规划、预览和模拟。
- [ ] 坐标转换和地图显示正确。
- [ ] 会话世代隔离和终态清理通过测试。
- [ ] Service 重建、锁屏和后台运行通过真机验收。
- [ ] 多停靠点路线可规划、推进和到达。
- [ ] 已走/未走路线渲染正确。
- [ ] 到点提醒只触发一次，路线完成通知正确。
- [ ] Android 9–15 标准通知正常。
- [ ] Android 16 Live Updates 正常或明确记录设备限制。
- [ ] 小米 HyperOS 超级岛正常或明确记录系统限制。
- [ ] 三种通知展示共用一个通知 ID 和一份状态。
- [ ] 收藏分组和旧数据迁移通过。
- [ ] 中国大陆边界回归测试通过。
- [ ] 语言切换和新增文案完整。
- [ ] Quick Settings、App Shortcuts 和外部控制安全可用。
- [ ] 单元测试全部通过。
- [ ] Debug/Release 构建成功。
- [ ] `git diff --check` 通过。
- [ ] APK、日志、Git 和文档无 Key/坐标泄漏。
- [ ] 高德测试 Key 已清除并重置。
- [ ] `追踪.md` 已记录所有实体设备证据和剩余风险。

---

## 18. 回滚策略

每个阶段独立提交、独立回滚：

- 步行状态修复：回滚实现但保留测试；
- 通知增强：关闭 Live Update/超级岛适配，保留标准通知；
- 多停靠点：回退为单起终点路线；
- 到点提醒：关闭提醒不影响路线推进；
- 收藏分组：迁移失败回退旧收藏读取；
- 边界资源：回退到旧转换逻辑；
- 本地化：回退到系统语言；
- Tile/Shortcut：移除组件，不影响 App 内控制。

禁止使用 `git reset --hard`、全量清理工作区或覆盖用户无关改动来回滚。
