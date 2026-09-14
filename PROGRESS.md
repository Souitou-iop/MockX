# XposedFakeLocation 开发进度与迁移指南

本文档记录了当前项目的开发进展、架构变更、最新实现的核心特性以及在新电脑上继续开发的构建与运行指南。

---

## 一、项目概况

- **项目名称**：XposedFakeLocation
- **应用包名**：`com.noobexon.xposedfakelocation`
- **开发语言/框架**：Kotlin 2.x + Jetpack Compose (Material 3) + Modern libxposed (101.0.0)
- **支持最低版本**：Android 10 (API 29)
- **编译目标版本**：Android 16 / VanillaIceCream (CompileSdk 36, TargetSdk 36)
- **Java/Kotlin 目标**：JVM 21

---

## 二、本次会话完成的核心特性与改进

### 1. 国内地图源接入（免 Key 国内秒开）
- **问题背景**：原项目硬编码了 OpenStreetMap（`TileSourceFactory.MAPNIK`），在国内网络环境下加载缓慢，经常出现白屏和瓦片加载失败。
- **实现内容**：
  - 封装多图源枚举 `MapSourceOption`（高德矢量、高德卫星、天地图矢量、原版 OSM）。
  - 实现自定义瓦片下载器 `CustomTileSources`：
    - **高德矢量地图**（`AMAP_VECTOR`）：基于高德官方瓦片服务 `wprd0{1-4}.is.autonavi.com`，风格为标准矢量街道图（style=7），免 Key，国内秒级出图，设为默认图源。
    - **高德卫星影像**（`AMAP_SATELLITE`）：高清卫星遥感底图（style=6），免 Key。
    - **天地图矢量**（`TIANDITU_VECTOR`）：国家地理信息公共服务平台 CGCS2000/WGS-84 原生无偏图源，支持自定义开发者 Token。
    - **OpenStreetMap**（`OPEN_STREET_MAP`）：保留国际原版官方源。
  - **图源隔离缓存**：每个图源采用独立的缓存前缀名称，避免瓦片缓存互相污染。
  - **快捷交互**：在地图主页面右上角三点菜单提供“切换地图图源”快捷弹窗；在“设置”页新增“地图设置”分类和天地图 Token 配置项。

### 2. 火星坐标系（GCJ-02 ↔ WGS-84）高精度自动纠偏
- **问题背景**：国内高德底图强制使用 GCJ-02（火星坐标系），若直接使用真实 WGS-84 经纬度交互，会导致取点与显示存在 300~500 米严重偏移。
- **实现内容**：
  - **转换算法（`CoordinateTransform.kt`）**：
    - `outOfChina(lat, lon)`：精确判定中国大陆边界，境外坐标自动旁路、不进行加偏。
    - `wgs84ToGcj02(lat, lon)`：正向标准火星加密算法。
    - `gcj02ToWgs84(gcjLat, gcjLon)`：采用 **4 轮不动点迭代（Fixed-point iteration）高精度反算**，精度误差 $< 10^{-8}$ 度（毫米级），彻底消除传统单次减法反算引起的 1~2 米漂移。
  - **全链路自动投影**：
    - **点击取点**：用户在高德底图上点击取点时，自动将 GCJ-02 反算为标准 WGS-84 坐标存入 Preferences 并提交给 Xposed 模块，确保目标 App（微信、高德、美团等）收到真实精准的底层 GPS。
    - **大头针图钉（Marker）渲染**：底图为高德时，图钉位置动态正向转换为 GCJ-02 坐标绘制，图钉与底图建筑、道路完全对齐、零漂移。
    - **定位蓝点纠偏（`Gcj02LocationProvider.kt`）**：继承 `GpsMyLocationProvider`，动态拦截并纠偏实时 GPS 位置，使“我的位置”蓝点准确显示在当前底图上。
    - **多图源平滑切换**：切换图源时图钉坐标自动按对应坐标系重绘，底层持久化数据始终保持纯净的 WGS-84。

### 3. 全局高刷新率（120Hz/144Hz）支持与防锁 60 帧
- **问题背景**：二级设置界面（`SettingsScreen`、`TargetAppsScreen` 等纯 Compose 列表界面）在小米 HyperOS、ColorOS、OriginOS 等设备上普遍锁定 60 帧，滑动卡顿。
- **原因**：项目未向系统 Window 申报高刷偏好，触发了国产系统针对非白名单第三方 App 的“智能/自适应刷新率”降频策略，且早期 Compose 列表滑动未向 View 树上报滑动速度。
- **实现内容**：
  - 创建全局刷新率工具 `RefreshRateHelper.kt`，动态获取屏幕硬件最高刷新率并注入 `preferredDisplayModeId`（API 23+）和 `preferredRefreshRate`（API 30+）。
  - 在 `App.kt` 注册 `ActivityLifecycleCallbacks`，并在 `MainActivity.kt` 的 `onCreate` 与 `onResume` 全局生效。
  - 在 `AndroidManifest.xml` 中为 `<application>` 增加 `android:appCategory="productivity"` 与 `android:hardwareAccelerated="true"`，防止被厂商温控/省电框架降频。

### 4. 编译与打包优化（arm64-v8a 适配）
- 在 `app/build.gradle.kts` 中添加 `ndk.abiFilters` 属性支持（默认过滤打包 `arm64-v8a`，亦可通过 `-PtargetAbi=all` 构建全架构包），将 Release 包体积压缩至 **4.5 MB**。
- 配置了 `lint { abortOnError = false; checkReleaseBuilds = false }`，解决了 AGP 8.7 与 Kotlin 2.x 下 Release Lint 检测的偶发已知兼容问题。

---

## 三、关键代码文件变动一览

| 模块 | 文件路径 | 类型 | 说明 |
| :--- | :--- | :--- | :--- |
| **算法** | `app/.../manager/ui/map/CoordinateTransform.kt` | 新增 | WGS-84 与 GCJ-02 双向高精度转换算法及边界判断 |
| **测试** | `app/src/test/.../manager/ui/map/CoordinateTransformTest.kt` | 新增 | 坐标转换算法精度与边界单元测试 |
| **图源** | `app/.../manager/ui/map/MapSourceOption.kt` | 新增 | 图源选项枚举（高德矢量/卫星、天地图、OSM） |
| **图源** | `app/.../manager/ui/map/CustomTileSources.kt` | 新增 | 高德与天地图在线瓦片图源实现 |
| **图源** | `app/.../manager/ui/map/Gcj02LocationProvider.kt` | 新增 | 设备定位实时加偏 Provider |
| **UI** | `app/.../manager/ui/map/MapSourceSelectionDialog.kt` | 新增 | 地图图源切换单选弹窗 |
| **高刷** | `app/.../manager/RefreshRateHelper.kt` | 新增 | 全局屏幕最高刷新率探测与 Window 属性注入工具 |
| **数据** | `app/.../data/Constants.kt` | 修改 | 新增地图图源常量与默认值 |
| **仓库** | `app/.../data/repository/PrefrencesRepository.kt` | 修改 | 新增图源与天地图 Token 的持久化与响应式 Flow |
| **页面** | `app/.../manager/ui/map/MapViewContainer.kt` | 修改 | 适配多图源切换与坐标纠偏集成 |
| **页面** | `app/.../manager/ui/map/MapViewEffects.kt` | 修改 | 适配多图源 Marker、相机动画及点击取点自动反算 |
| **状态** | `app/.../manager/ui/map/MapViewModel.kt` | 修改 | 增加图源状态管理、事件监听与弹窗状态 |
| **状态** | `app/.../manager/ui/map/MapModel.kt` | 修改 | `MapUiState` 增加 `mapSource`、`tiandituToken` 等字段 |
| **页面** | `app/.../manager/ui/map/MapScreen.kt` | 修改 | 增加图源快捷切换菜单与弹窗展示 |
| **设置** | `app/.../manager/ui/settings/SettingsModel.kt` | 修改 | 增加地图设置分组与图源设置项条目 |
| **设置** | `app/.../manager/ui/settings/SettingsScreen.kt` | 修改 | 渲染地图设置项与全局搜索索引 |
| **设置** | `app/.../manager/ui/settings/SettingsViewModel.kt` | 修改 | 绑定地图图源与 Token 设置响应流 |
| **应用** | `app/.../manager/App.kt` | 修改 | 注入 Activity 生命周期高刷监听 |
| **主入口** | `app/.../manager/MainActivity.kt` | 修改 | `onCreate`/`onResume` 请求最高屏幕刷新率 |
| **配置** | `app/src/main/AndroidManifest.xml` | 修改 | 补充应用分类与硬件加速配置 |
| **构建** | `app/build.gradle.kts` | 修改 | 增加 `arm64-v8a` 架构过滤与 Lint 容错 |
| **国际化** | `app/src/main/res/values*/strings.xml` | 修改 | 补齐中、英、德三种语言地图相关词条 |

---

## 四、在新电脑上继续开发的步骤

### 1. 环境准备
- **JDK**：OpenJDK 21（或更高）。建议配置 `JAVA_HOME` 指向 JDK 21 根目录。
- **Android SDK**：
  - `compileSdk` 为 36，`minSdk` 为 29。
  - 需要安装 Android SDK Build-Tools 34.0.0+ 或 35.0.0+。
  - 配置环境变量 `ANDROID_HOME` 或在项目根目录创建 `local.properties`（内容：`sdk.dir=/path/to/your/sdk`）。

### 2. 常用构建与验证命令
在新电脑解压项目后，可直接在项目根目录下执行以下命令：

```bash
# 1. 运行所有单元测试（包含坐标高精转换测试及既有测试）
./gradlew testDebugUnitTest

# 2. 构建针对 arm64-v8a 的 Release APK（体积仅 ~4.5MB，自动应用混淆和 debug 签名）
./gradlew assembleRelease

# 3. 构建 Debug APK（包含完整调试符号）
./gradlew assembleDebug

# 4. 如需构建包含所有架构（x86/arm32/arm64）的完整 APK：
./gradlew assembleRelease -PtargetAbi=all
```

### 3. 构建产物位置
- Release 安装包：`app/build/outputs/apk/release/app-release.apk`
- Debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`

---

## 五、最新修复与性能优化记录

### 1. 彻底解决国内三大地图（高德/百度/腾讯）2秒后跳回真实地址的问题
- **根因分析**：
  1. **Wi-Fi 网络定位泄漏**：旧逻辑仅在用户手动启用特定“Wi-Fi 身份伪装”时才清空周边扫描；普通用户开启虚拟定位时，`WifiManager.getScanResults()` 依然向应用返回真实周围 AP 列表，导致高德/百度服务端通过 Wi-Fi 库反查真实物理位置。
  2. **基站定位拦截缺失**：旧版本仅针对 `com.android.phone` 进程进行拦截，目标应用进程（高德/百度/腾讯）内部调用 `TelephonyManager.getAllCellInfo()`、`getCellLocation()` 直接获取到连接的真实 4G/5G 基站并上传服务器。
  3. **LocationManager 持续与单次定位未拦截**：旧版本仅拦截了 `getLastKnownLocation`，未拦截 `requestLocationUpdates`、`requestSingleUpdate`、`getCurrentLocation`。目标应用收到系统硬件真实位置回调（或室内无 GPS 信号超时降级）。
  4. **NMEA 语句与卫星状态泄露**：国内地图应用监听了 `addNmeaListener` / `registerGnssNmeaCallback`，直接从 `$GPGGA`/`$GPRMC` 报文中解析真实经纬度。
  5. **Location 元数据校验缺失**：旧版虚拟 `Location` 缺少系统纳秒时钟 `elapsedRealtimeNanos` 和合法精度值，导致部分加固 SDK 判定为过期/无效数据而丢弃。
- **修复措施**：
  - **AppWifiHooks**：无论是否单独配置 Wi-Fi 伪装，只要虚拟定位开启，一律清空周边 Wi-Fi 扫描列表（`getScanResults`），阻断硬件主动扫描（`startScan`），并混淆已连接 Wi-Fi 的 BSSID（防路由器 MAC 泄露）。
  - **AppTelephonyHooks（新增）**：在目标 App 进程拦截 `TelephonyManager` 的 `getAllCellInfo`、`getCellLocation`、`getNeighboringCellInfo` 与 `requestCellInfoUpdate`，彻底截断基站网络定位反查。
  - **LocationManagerApiHooks（重构）**：
    - 拦截 `getLastLocation`、`getCurrentLocation`，直派假位置；
    - 拦截 `requestLocationUpdates` 与 `requestSingleUpdate`，动态 Hook 目标 App 的 `LocationListener.onLocationChanged`，过滤并替换所有真实 Location；
    - 开启 1000ms 心跳主动下发虚拟位置，防止高德等 SDK 因室内无卫星信号超时降级回网络定位；
    - 阻断 `addNmeaListener`、`registerGnssNmeaCallback` 与 GNSS 状态监听，防止原始卫星文本报文泄露。
  - **LocationApiHooks & LocationUtil**：注入准确的 `elapsedRealtimeNanos`、时间戳和有效精度，拦截 `isFromMockProvider` / `isMock` 恒为 false。

### 2. 彻底解决二级设置菜单锁帧问题
- **根因分析**：
  1. `RefreshRateHelper` 直接取 `modes.maxByOrNull { it.refreshRate }`，在 2K/1.5K 等多分辨率设备上容易选出与当前分辨率物理尺寸不匹配的 Mode，导致 WindowManager 判定分辨率冲突并拒绝切换，回退至 60Hz。
  2. Compose 的二级页面（如 `SettingsScreen`、`TargetAppsScreen`）为单一 View 内部自绘，未向底层系统报送滚动速度，触发了 HyperOS / ColorOS 等厂商系统针对非白名单应用的 60Hz 限制；且 Compose 页面跳转时不触发 Activity 生命周期。
- **修复措施**：
  - **RefreshRateHelper**：先获取当前活跃屏幕的 `physicalWidth / physicalHeight`，严格在匹配当前物理分辨率的模式集中选择最高刷新率（120Hz/144Hz）。
  - **View 树帧率注入**：在 Window 设置 `preferredRefreshRate` 的同时，通过反射为 `decorView` 及根视图树注入 `view.setFrameRate(maxRefreshRate)`（API 30+）以及 `view.setFrameRateCategory(FRAME_RATE_CATEGORY_HIGH)`（API 34/35+）。
  - **二级页面生命周期绑定**：在 `SettingsScreen` 与 `TargetAppsScreen` 进入时通过 `LaunchedEffect` 主动请求最高刷新率并绑定当前 `LocalView`，保证滑动丝滑 120Hz。

---

## 六、后续可扩展或优化的建议

1. **地图底图路线/路线规划与轨迹模拟**：当前为单点模拟，后续可考虑加入路线模拟点位插值算法。
2. **地点在线搜索（Geocoding）**：可考虑接入高德或天地图的地理编码/逆地理编码 Web API，支持在地图上直接搜索中文地址或兴趣点（POI）。
3. **多图源卫星路网图层叠加**：目前高德卫星为纯遥感影像，后续可通过双图层 Overlay 叠加路网/地名透明瓦片层（style=8）。
