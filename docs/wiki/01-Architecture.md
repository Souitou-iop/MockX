# 01 - 项目整体架构

## 1. 一句话概括

MockX 是一个**单 APK、双形态**的项目：同一个 `app` 模块既是普通的 Compose 管理端应用，又是通过 `META-INF/xposed` 声明的 LSPosed 模块；管理端负责"设置什么"，被注入的目标进程负责"让 App 看到什么"。

## 2. 三种运行形态

```mermaid
flowchart TB
    subgraph APK["MockX APK (io.github.souitou.mockx)"]
        M["manager 包<br/>(UI / 地图 / 设置)"]
        X["xposed 包<br/>(ModuleEntry + Hooks)"]
        D["data 包<br/>(常量 / 模型 / 仓库)"]
    end

    M -->|"读写远程偏好<br/>XposedService"| RP[("LSPosed<br/>RemotePreferences<br/>组: settings")]
    X -->|"每次拦截时读取<br/>getRemotePreferences"| RP

    subgraph Targets["LSPosed Scope 内的进程"]
        T1["目标 App 进程<br/>(高德/微信/美团…)"]
        T2["com.android.phone 进程<br/>(可选)"]
        T3["system_server 进程<br/>(可选 system 级 Hook)"]
    end

    RP -.->|"(读取同一份设置)"| T1
    RP -.-> T2
    RP -.-> T3

    M -.->|"选择目标应用时<br/>动态更新 Scope"| LP["LSPosed 框架"]
    LP -->|"注入 xposed 包"| T1 & T2 & T3
```

| 形态 | 载体 | 安装的 Hook | 触发条件 |
| :--- | :--- | :--- | :--- |
| 管理端 App | 用户手动启动 | 无（不 Hook 自身） | — |
| 目标 App 进程 | LSPosed 注入 | `LocationApiHooks` + `LocationManagerApiHooks` + `AppWifiHooks` + `AppTelephonyHooks` | 用户在 Target Apps 屏选中该应用 |
| `com.android.phone` | LSPosed 注入 | `PhoneServicesHooks` | 开启系统级 Hook（加入 scope） |
| `system_server` | LSPosed 注入 | `SystemServicesHooks` | 开启系统级 Hook（加入 scope） |

## 3. 进程内分层

```mermaid
flowchart LR
    subgraph ManagerProcess["管理端进程"]
        UI["UI 层<br/>Compose Screen + ViewModel"]
        REPO["PreferencesRepository<br/>(单一数据源)"]
        SVC["XposedService 绑定<br/>(App.serviceState)"]
        UI --> REPO --> SVC
    end

    subgraph TargetProcess["目标 App 进程 (被注入)"]
        ENTRY["ModuleEntry<br/>(onModuleLoaded/onPackageLoaded/onPackageReady)"]
        HOOKS["Hooks<br/>(Location/LocationManager/Wifi/Telephony)"]
        PUTIL["PreferencesUtil<br/>(hook 端偏好读取)"]
        LUTIL["LocationUtil<br/>(伪造状态 + createFakeLocation)"]
        ENTRY --> HOOKS
        HOOKS --> PUTIL & LUTIL
    end

    SVC <-->|"RemotePreferences<br/>组: settings"| PUTIL
```

- **管理端**：`Screen → ViewModel → PreferencesRepository → App.service（XposedService）→ RemotePreferences`。UI 从不直接触碰 SharedPreferences。
- **Hook 端**：`ModuleEntry` 在 `onPackageLoaded` 阶段初始化 `PreferencesUtil`（远程偏好），各 Hook 在**每次拦截时**调用 `LocationUtil.updateLocation()` 拉取最新设置——这就是"热更新、无需重启目标 App"的实现基础。

## 4. 关键数据流：一次位置伪造

```mermaid
sequenceDiagram
    participant U as 用户
    participant VM as MapViewModel
    participant REPO as PreferencesRepository
    participant RP as RemotePreferences
    participant H as 目标进程 Hooks
    participant APP as 目标 App

    U->>VM: 地图上点击一个点
    VM->>REPO: saveLastClickedLocation(lat, lon)
    REPO->>RP: putString("last_clicked_location", json)
    U->>VM: 点击 Play
    VM->>REPO: saveIsPlaying(true)
    REPO->>RP: putBoolean("is_playing", true)

    APP->>H: getLastKnownLocation() / requestLocationUpdates(...)
    H->>RP: 读取 is_playing / last_clicked_location / 各开关
    H->>H: LocationUtil.updateLocation() + createFakeLocation()
    H-->>APP: 返回伪造 Location（含随机半径、精度、海拔、速度等）
    loop 每 1000ms 心跳
        H-->>APP: listener.onLocationChanged(伪造位置)
    end
```

要点：

1. **坐标写入永远是 WGS-84**。高德底图（GCJ-02）上的点击由 UI 层先经 `CoordinateTransform.gcj02ToWgs84()` 反算再持久化（详见 [02 - 管理端](02-Manager-App.md#32-坐标系-transformcoordinate)）。
2. **Hook 端零缓存决策**：`getIsPlaying()`、`getUseAccuracy()` 等每次拦截都重新读取远程偏好，保证管理端改动实时生效。
3. **可选字段带开关**：精度/海拔/垂直精度/海平面/速度等字段均有 `use_xxx` 布尔开关，关闭时回退原始 Location 的对应值。

## 5. Hook 安装决策流程

`ModuleEntry`（[代码](../../app/src/main/java/com/noobexon/xposedfakelocation/xposed/ModuleEntry.kt)）按进程分派：

```mermaid
flowchart TB
    A["LSPosed 加载模块"] --> B["onModuleLoaded<br/>注入 logger 到 LocationUtil / PreferencesUtil"]
    A --> C["onPackageLoaded<br/>初始化 RemotePreferences"]
    A --> D["onPackageReady<br/>(isFirstPackage)"]
    D --> E{"packageName ==<br/>com.android.phone ?"}
    E -->|是| F["PhoneServicesHooks"]
    E -->|否| G["LocationApiHooks<br/>LocationManagerApiHooks<br/>AppWifiHooks<br/>AppTelephonyHooks"]
    G --> H{"隐藏 Toast 开关?"}
    H -->|否| I["Hook Instrumentation.callApplicationOnCreate<br/>显示 'Fake Location Is Active!'"]
    A --> J["onSystemServerStarting<br/>(仅当 system 在 scope 内)"]
    J --> K{"enable_system_hooks?"}
    K -->|是| L["SystemServicesHooks"]
    K -->|否| M["跳过"]
```

## 6. Scope 管理：从"手动勾选"到"App 内托管"

`META-INF/xposed/scope.list` 为**空文件**（`staticScope=false`），即模块不声明静态 scope。管理端在 Target Apps 屏勾选应用时：

1. 包名集合写入远程偏好 `target_apps`（JSON 数组）——供 `SystemServicesHooks` / `PhoneServicesHooks` 做**调用方归因判断**（`shouldSpoofPackage`）；
2. 同时通过 XposedService 动态更新 LSPosed 模块 scope——新增应用需强杀重启该应用一次以完成注入，之后所有改动热生效。

系统级 Hook 开关（`enable_system_hooks`）会向 scope 添加/移除 `system` 与 `com.android.phone` 两个虚拟包名（见 `Constants.SYSTEM_HOOK_PACKAGES`）。

## 7. 设计取舍备忘

| 决策 | 原因 |
| :--- | :--- |
| 命名空间与 applicationId 不一致 | 保留上游包名 `com.noobexon.xposedfakelocation` 降低合并冲突，仅重命名对外标识（`io.github.souitou.mockx`） |
| `compileOnly` libxposed api + `implementation` libxposed service | api 由 LSPosed 运行时提供；service 需打进 APK 供管理端绑定 |
| Hook 端每次拦截都读偏好而非监听变更 | 实现最简单的热更新，偏好读取为内存读，开销可忽略 |
| 1 秒心跳主动分发 | 防止高德等 SDK 在室内无卫星信号时超时降级到网络定位 |
| `Double` 以 long bits 存 SharedPreferences | `SharedPreferences` 无 `putDouble`，读写两侧（Repository / PreferencesUtil）对称编码 |
