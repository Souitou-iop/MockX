# 05 - 关键类与函数速查

按包分组的速查表。所有路径省略前缀 `app/src/main/java/com/noobexon/xposedfakelocation/`。

## 1. `xposed/` — 模块层

### `xposed/ModuleEntry.kt` — 入口分派

| 成员 | 说明 |
| :--- | :--- |
| `onModuleLoaded(param)` | 注入 libxposed logger 到两个工具单例 |
| `onPackageLoaded(param)` | `initRemotePreferences()` 初始化偏好 |
| `onPackageReady(param)` | 进程分派：phone 进程 / 普通目标四件套 + 可选 Toast |
| `onSystemServerStarting(param)` | `enable_system_hooks` 门控后装系统 Hook |
| `showActiveToast(param)` | Hook `Instrumentation.callApplicationOnCreate` 弹提示 |

### `xposed/utils/LocationUtil.kt` — 伪造状态单例

| 函数 | 签名 | 说明 |
| :--- | :--- | :--- |
| `createFakeLocation` | `(originalLocation: Location? = null, provider: String = GPS_PROVIDER): Location` `@Synchronized` | 构造伪造 Location：当前时间戳 + elapsedRealtimeNanos；可选字段非零才覆盖；accuracy 缺省 3.0m；清除 mock 标记 |
| `updateLocation` | `(): Unit` `@Synchronized` | 从 `PreferencesUtil` 拉全部最新设置；随机半径开启时 Haversine 圆内均匀采样 |
| `getRandomLocation` | `(lat, lon, radiusInMeters): Pair<Double,Double>` `private` | 圆内均匀随机点（`sqrt(rand1)` 保证面积均匀），经度归一化、纬度夹取 |
| `attemptHideMockProvider` | `(fakeLocation)` `private` | `HiddenApiBypass.invoke(... "setIsFromMockProvider", false)`，失败静默 |
| 字段 | `latitude/longitude/accuracy/altitude/verticalAccuracy/meanSeaLevel(±Acc)/speed(±Acc)` | 全部 `private set`，仅 `updateLocation` 可写 |

### `xposed/utils/PreferencesUtil.kt` — Hook 端偏好读取

| 函数 | 说明 |
| :--- | :--- |
| `init(prefs)` | 持有远程偏好 + 注册（强引用）变更监听 |
| `getIsPlaying(): Boolean?` | 伪造总开关（所有 Hook 的第一道判断） |
| `getLastClickedLocation(): LastClickedLocation?` | 当前目标点 |
| `getUseXxx() / getXxx()` | 各可选参数开关与数值（`use_accuracy/accuracy` 等成对） |
| `getTargetApps(): Set<String>` | JSON 数组解析，失败回空集 |
| `getEnableSystemHooks(): Boolean` | 系统级 Hook 开关 |
| `getWifiSsid/Bssid/Rssi` | Wi-Fi 身份（含规范化/夹取） |
| `getPreference<T>(key)` `private` | 泛型分派：Double→long bits、Float/Boolean 原生、其余 Gson |

### `xposed/hooks/LocationApiHooks.kt`

| 函数 | 说明 |
| :--- | :--- |
| `init()` | 对 `android.location.Location` 安装全部 getter Hook |
| `Class<*>.hookMethod(name, enabled, spoofed)` `private` | 拦截无参 getter，isPlaying 且 `enabled()` 时替换为 `spoofed()`；Hook `isFromMockProvider`/`isMock` → false |

### `xposed/hooks/LocationManagerApiHooks.kt`

| 函数 | 说明 |
| :--- | :--- |
| `init()` | 安装 9 组 LocationManager Hook + 启动心跳循环 |
| `hookLastKnownLocation / hookLastLocation / hookCurrentLocation` | 拉取式 API 直派伪造（`getCurrentLocation` 反射调 `Consumer.accept`） |
| `hookRequestLocationUpdates / hookRequestSingleUpdate / hookRemoveUpdates` | 监听器注册管理 + 动态 Hook `onLocationChanged` |
| `hookListenerClassIfNeeded(listenerClass)` `private` | 沿父类链递归 Hook `onLocationChanged`，替换 `Location`/`List<Location>` 参数 |
| `startContinuousHeartbeatLoop()` `private` | 1000ms 周期向全部注册监听器主线程推送伪造位置 |
| `hookNmeaListeners / hookGnssStatusCallbacks / hookGnssMeasurements` | 屏蔽 NMEA/卫星状态/原始测量注册 |

### `xposed/hooks/AppWifiHooks.kt`

`init()` 安装四组：`WifiInfo` getter 直 Hook（getBSSID/getSSID/getMacAddress）、`getConnectionInfo`（伪造或消毒）、`getScanResults`（恒空）、`startScan`（吞掉）。`createFakeWifiInfo(identity)` / `createSanitizedWifiInfo()` 用 `WifiInfo.Builder` 构造替代对象。

### `xposed/hooks/AppTelephonyHooks.kt`

`init()` 安装：`getAllCellInfo`→空、`getCellLocation`→null、`getNeighboringCellInfo`→空、`requestCellInfoUpdate`→阻断、`listen`→掩码剥离 cell 事件位 `(0x10 or 0x400).inv()`、`registerTelephonyCallback`（API 31+）→ 拒绝 Cell 类回调。

### `xposed/hooks/SystemServicesHooks.kt`（system_server）

| 函数 | 说明 |
| :--- | :--- |
| `init()` | 装全部系统级 Hook（lastLocation/currentLocation/分发/MIUI/WiFi/GNSS/Geofence） |
| `interceptOnReportLocation(...)` `private` | 按注册归因：目标注册直接投递伪造结果（反射 `acceptLocationChange`+`executeOperation`），非目标走原路 |
| `interceptCallLocationChanged(chain)` `private` | Receiver 归因后替换 Location 参数 |
| `hookMiuiLocationServices(...)` `private` | 小米系设备才装；`MiuiBlurLocationManager(Impl)` 系列清空/替换 |
| `collectPackageNames(value, visited, depth)` `private` | 递归提取调用方包名：WorkSource、`mPackageName`/`callingPackage`/`mAttributionSource` 等字段与 getter、`mRequest` 嵌套 |
| `shouldSpoofArgs(args)` / `shouldSpoofPackage(pkg)` `private` | isPlaying + 参数归因命中 `target_apps` 才伪装 |

### `xposed/hooks/PhoneServicesHooks.kt`（com.android.phone）

Hook `PhoneInterfaceManager.getCellLocation/getAllCellInfo/getNeighboringCellInfo/requestCellInfoUpdateInternal`；`extractPackageName` 从 String 入参提取调用方做归因。

### `xposed/hooks/WifiIdentityHookPolicy.kt`

| 成员 | 说明 |
| :--- | :--- |
| `WifiIdentity` data class | `ssid/bssid/rssi/targetApps` + `targets(pkg)` |
| `readActiveIdentity(module)` | `is_playing && enable_wifi_identity` 才返回身份快照 |
| `targetsSystemWifiCaller(args, targetApps)` | Wi-Fi 系统服务调用首参归因 |

## 2. `manager/` — 管理端

### `manager/App.kt`

| 成员 | 说明 |
| :--- | :--- |
| `serviceState: StateFlow<XposedService?>` | 服务绑定状态（`service` 便捷访问器） |
| `onCreate()` | `registerListener` 恰好一次 + Activity 生命周期高刷回调 |
| `onServiceBind / onServiceDied` | 维护 serviceState |

### `manager/MainActivity.kt`

`attachBaseContext`（LocaleController 包裹）→ `onCreate`（OSMDroid 配置、高刷、EdgeToEdge、主题 + NavGraph）→ `onResume`（再应用高刷）。

### `manager/RefreshRateHelper.kt`

`applyHighRefreshRate(activity)`：匹配当前物理分辨率的最高刷新率模式 → `preferredDisplayModeId`/`preferredRefreshRate` → 反射 `setFrameRate()`/`setFrameRateCategory(HIGH)`。

### `manager/control/ControlReceiver.kt`

`onReceive` → `goAsync()` + IO 协程分发三 Action（START/STOP/SET_LOCATION）；`parseCoordinates` 做纬经度范围与有限性硬校验，`handleSetLocation` 校验 accuracy ∈ [0, 100000]。

### `manager/localization/LocaleController.kt` / `LanguageOption.kt`

`attachBaseContext(base)`：读本地 `language_tag`，非空则 `Locale.createConfigurationContext` 包裹。

### `manager/ui/map/MapViewModel.kt`

| 函数 | 说明 |
| :--- | :--- |
| `togglePlaying()` | 乐观本地更新 + 异步持久化 is_playing |
| `updateClickedLocation(geoPoint?)` | 设置/清除目标点并持久化（null 时同时 is_playing=false） |
| `setMapSource(option)` / `updateMapZoom(zoom)` | 图源与缩放持久化 |
| `showGoToPointDialog/confirmGoToPoint` | 坐标跳转弹窗（校验 ±90/±180，成功发 `goToPointEvent`） |
| `showAddToFavoritesDialog/confirmAddFavorite` | 收藏弹窗（预填当前 Marker，校验后持久化并跳转收藏页） |
| `triggerCenterMapEvent` / `consumeReopenDrawerRequest` | 一次性事件（Channel / 单次消费标志） |

### `manager/ui/map/CoordinateTransform.kt`

| 函数 | 说明 |
| :--- | :--- |
| `outOfChina(lat, lon)` | 中国大陆边界判定（境外旁路不加偏） |
| `wgs84ToGcj02(lat, lon)` | 正向加偏 |
| `gcj02ToWgs84(gcjLat, gcjLon)` | 4 轮不动点迭代反算，误差 < 1e-8 度 |

### `manager/ui/map/CustomTileSources.kt` / `MapSourceOption.kt` / `Gcj02LocationProvider.kt`

- `MapSourceOption`：四图源枚举（`fromTag(tag)` 反查）；
- `CustomTileSources`：高德 `wprd0{1-4}.is.autonavi.com`（style=7 矢量 / 6 卫星）、天地图（CGCS2000 原生无偏 + 自定义 Token）、OSM；每图源独立缓存前缀；
- `Gcj02LocationProvider`：继承 `GpsMyLocationProvider`，实时纠偏蓝点到当前底图坐标系。

### `manager/ui/settings/`、`targetapps/`、`favorites/`、`about/`、`permissions/`

标准 `Screen + ViewModel (+Model)` 三件套；Settings 的条目由 `SettingsModel` 分组声明（含地图设置、Wi-Fi 身份、系统级 Hook、外部控制等），`SettingsViewModel` 绑定对应 Flow。TargetApps 勾选驱动 scope + `target_apps` 偏好。详见 [02 - 管理端](02-Manager-App.md)。

## 3. `data/` — 数据层

### `data/Constants.kt`

见 [04 - 数据层](04-Data-Layer.md#2-constantskt--契约中枢)。

### `data/repository/PrefrencesRepository.kt`

| 函数 | 说明 |
| :--- | :--- |
| `remoteFlow<T>(key, default, read)` `private` | `serviceState.flatMapLatest` + 偏好变更 Flow（未绑定回默认值） |
| `localFlow<T>(key, read)` `private` | 本地偏好变更 Flow |
| `editRemote/editLocal` `private` | 写入（远程 commit=true；未绑定丢弃并告警） |
| `getIsPlayingFlow/saveIsPlaying` 等 | 每设置项三件套（Flow / 同步读 / 挂起写） |
| `clearLastClickedLocation()` | 清目标点并强制 is_playing=false |
| `addFavorite/removeFavorite/updateFavorite` | 收藏读-改-写 |

### `data/model/`

`LastClickedLocation(lat, lon)`、`FavoriteLocation(name, lat, lon, description)` —— Gson 序列化。

## 4. 测试类

| 文件 | 覆盖 |
| :--- | :--- |
| `app/src/test/.../map/CoordinateTransformTest.kt` | 坐标转换精度与边界（境外旁路、往返一致性） |
| `app/src/test/.../hooks/WifiIdentityHookPolicyTest.kt` | Wi-Fi 身份门控逻辑（开关组合、SSID/BSSID 规范化） |
| `app/src/test/.../ExampleUnitTest.kt` | 占位示例 |
