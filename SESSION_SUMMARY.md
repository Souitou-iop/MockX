# MockX (原 XposedFakeLocation) 会话开发与重构总结

本文档详细记录了本次会话中关于 **架构升级、UI 全面重构为 Miuix 规范、底层防回跳拦截封堵、应用全新品牌升级（MockX）** 以及真机适配的所有工作与技术细节。

---

## 一、本次会话完成的核心任务概览

| 维度 | 原始状态 | 重构后状态 | 核心收益 |
| :--- | :--- | :--- | :--- |
| **设计规范** | Google 原生 Material 3 基础组件 | 官方 `compose-miuix-ui` 跨平台原生组件库 | 获得真实的连续平滑圆角（Squircle）、物理弹簧回弹开关、宽轨滑块与毛玻璃微岛 |
| **二级菜单与弹窗** | 手搓 Compose 遮罩与普通 Dialog | 官方 `WindowIconDropdownMenu`、`WindowDialog`、`RadioButtonPreference` | 彻底消除手搓痕迹，对齐 HyperOS / MIUI 原生系统交互与视觉质感 |
| **防回跳定位** | 国内地图 2 秒内回跳真实位置 | Wi-Fi BSSID/SSID 实体拦截 + 基站回调清洗 + 裸卫星观测屏蔽 + 1s 心跳分发 | 彻底封堵多通道硬件级泄漏，实现高德/百度/腾讯地图常态化稳定伪装 |
| **品牌与包名** | `XposedFakeLocation` (`com.noobexon.xposedfakelocation`) | **`MockX`** (`io.github.souitou.mockx`) | 统一全语言命名，摆脱系统绑定词，实现全新独立模块分发 |
| **应用图标** | 原版普通地图图标 | macOS Icon Composer 设计的高清立体晶体水滴 + 同心空间波纹双层自适应图标 | 完美适配 Android Adaptive Icon 规范，支持桌面动态圆角与视差微动效 |
| **界面交互体验** | 顶部留白过大、内嵌第三方群组外链 | 紧凑标准留白、去除社区外链、新增 GitHub Fork 源码卡片 | 布局利落，项目直达跳转闭环 |

---

## 二、关键技术实现与架构演进

### 1. 接入官方 `compose-miuix-ui` 组件库
针对用户指出“手搓 UI 质感与原生 MIUI/HyperOS 差距明显”的问题，工程彻底放弃了手动绘制的包装层，接入官方社区库并解决了多项底层编译兼容问题：
- **引入依赖**：
  - `top.yukonga.miuix.kmp:miuix-ui:0.9.3`
  - `top.yukonga.miuix.kmp:miuix-preference:0.9.3`
  - `top.yukonga.miuix.kmp:miuix-icons:0.9.3`
- **编译与环境适配**：
  - 适配 JDK 21 工具链，规避了高版本 JDK 的 class 元数据解析异常；
  - 在 `build.gradle.kts` 中关闭冲突的 AAR Metadata 检查，并添加 `-Xskip-metadata-version-check`；
  - 引入 `androidx.navigationevent:navigationevent-compose:1.1.2` 与 `navigationevent:1.1.2`，在全局 `Theme.kt` 中注入 `LocalNavigationEventDispatcherOwner`，解决了官方 `WindowListPopup` 和 `WindowIconDropdownMenu` 弹出时缺少返回事件调度器引起的闪退。

### 2. 二级菜单与系统弹窗官方化
- **操作气泡菜单（三点菜单）**：
  - 主地图页（`MapScreen.kt`）、设置页（`SettingsScreen.kt`）、目标应用页（`TargetAppsScreen.kt`）的右上角菜单全面切换为 `WindowIconDropdownMenu` 与 `DropdownItem`。
- **单选与图源弹窗**：
  - `MapSourceSelectionDialog.kt` 重构为 `WindowDialog` + `Card` + `RadioButtonPreference`，拥有标准 Squircle 底衬和原生单选反馈。
- **输入与确认弹窗**：
  - 坐标跳转（`GoToPointDialog`）、收藏夹增删改（`AddToFavoritesDialog`、`EditFavoriteDialog`、`DeleteConfirmationDialog`）、设置重置与重启弹窗全部统一由官方 `WindowDialog` 和 `TextButton` 驱动。

### 3. 底层多通道防回跳拦截体系（高德/百度/腾讯地图验证）
针对开启虚拟定位数秒后回跳真实定位的问题，重构了 Xposed 层的底层清洗链条：
1. **Wi-Fi 探针阻断**：
   - 在 `AppWifiHooks.kt` 中直接对 `android.net.wifi.WifiInfo` 实体类的 `getBSSID()`、`getSSID()`、`getMacAddress()` 进行反射拦截，阻断现代地图 SDK 通过 `ConnectivityManager.getNetworkCapabilities()` 间接提取路由器硬件 MAC；
   - 彻底置空 `WifiManager.getScanResults()`。
2. **蜂窝基站主动推送清洗**：
   - 在 `AppTelephonyHooks.kt` 拦截 `TelephonyManager.listen`，使用掩码 `(0x10 or 0x400).inv()` 清空 `LISTEN_CELL_LOCATION` 与 `LISTEN_CELL_INFO` 监听位；
   - 在 Android 12+ (API 31+) 拦截 `registerTelephonyCallback`，阻断基站定位主动分发。
3. **硬件原始卫星测量值阻断**：
   - 拦截 `LocationManager.registerGnssMeasurementsCallback` 与 `registerGnssNavigationMessageCallback`，阻断 SDK 获取伪距等原始观测数据在 native 层绕过定位框架自行解算真实坐标。
4. **主动心跳分发循环**：
   - 开启后台定时调度线程池 `startContinuousHeartbeatLoop()`，每 1000ms 遍历监听器继承链（递归父类），向所有注册的 `LocationListener` 推送带有当前纳秒级时间戳的假位置，避免因超时引发 SDK 回退机制。

### 4. 品牌全面升级：MockX
- **应用名称统一**：
  - 简体中文（`values-zh`）、英文（`values`）、德语（`values-de`）全语言统称为 **`MockX`**；
  - 抽屉与界面中排查移除了所有“HyperOS 专属”绑定字样，改用“现代化底层定位与模拟工具”，保持通用极客调性。
- **全新包名体系**：
  - `applicationId = "io.github.souitou.mockx"`
  - 广播控制 Action 全面升级为 `io.github.souitou.mockx.action.START / STOP / SET_LOCATION`；
  - 更新白名单包名检测（`MANAGER_APP_PACKAGE_NAME = "io.github.souitou.mockx"`），避免自身被意外伪装。

### 5. Android 自适应图标设计落地
基于 macOS 上通过 Icon Composer 制作的 1024 高清设计图与 `icon.json` 图层描述：
- **背景层 (`ic_launcher_background`)**：天青蓝至纯白平滑垂直线性渐变底板；
- **前景层 (`ic_launcher_foreground`)**：立体亚克力晶体水滴图钉 + 地面空间波纹扩散主体，严格缩放至 72% 安全区域居中对齐；
- **单色层 (`ic_launcher_monochrome`)**：提取 Alpha 通道生成纯白遮罩，适配 Android 13+ 桌面动态主题取色；
- **全套位图资源**：生成并覆盖了 `mdpi`、`hdpi`、`xhdpi`、`xxhdpi`、`xxxhdpi` 全规格 `ic_launcher.png` 与 `ic_launcher_round.png`。

---

## 三、修改与新增的文件清单

```text
app/
├── build.gradle.kts                                      # 接入 miuix 0.9.3 与 navigationevent，变更包名为 io.github.souitou.mockx
├── src/main/AndroidManifest.xml                          # 声明 MockX 广播 Action 与应用配置
├── src/main/java/com/noobexon/xposedfakelocation/
│   ├── data/Constants.kt                                 # 修正 MANAGER_APP_PACKAGE_NAME 为新包名
│   ├── manager/control/ControlReceiver.kt                # 更新外部控制广播 Intent Action
│   ├── manager/ui/about/AboutScreen.kt                   # 修复顶部过多留白，更新 MockX 版本与描述，新增 GitHub Fork 仓库卡片
│   ├── manager/ui/favorites/FavoritesScreen.kt           # 修复顶部留白，弹窗统一对接官方 Miuix
│   ├── manager/ui/map/Drawer.kt                          # 移除社区外链，净化副标题与版本文本
│   ├── manager/ui/map/MapDialogs.kt                      # 坐标与收藏弹窗全面使用 WindowDialog
│   ├── manager/ui/map/MapScreen.kt                       # 右上角三点菜单彻底升级为 WindowIconDropdownMenu
│   ├── manager/ui/map/MapSourceSelectionDialog.kt        # 使用官方 WindowDialog + RadioButtonPreference 重构图源弹窗
│   ├── manager/ui/settings/SettingsScreen.kt             # 修复顶部留白，右上角重置菜单换用官方 WindowIconDropdownMenu
│   ├── manager/ui/targetapps/TargetAppsScreen.kt         # 修复顶部留白，过滤菜单换用官方 WindowIconDropdownMenu，升级 PullToRefreshBox
│   ├── manager/ui/theme/MiuixComponents.kt               # 官方 Miuix 核心封装库（Card、SmallTitle、Switch、Slider、Dialog、Button）
│   ├── manager/ui/theme/Theme.kt                         # 注入 MiuixTheme 与 LocalNavigationEventDispatcherOwner
│   └── xposed/hooks/                                     # Wi-Fi 实体拦截、基站监听过滤与原始 GNSS 观测屏蔽
└── src/main/res/
    ├── mipmap-anydpi/ic_launcher_round.xml               # 新增自适应圆形图标描述
    ├── mipmap-*/                                         # 全分辨率 ic_launcher、foreground、background、monochrome 位图替换
    ├── values*/strings.xml                               # 统一应用名为 MockX，更新描述文案
```

---

## 四、最终构建成果物

所有产物均已编译打包存放在工程根目录：

1. **`MockX-release.apk`** (4.4 MB)
   - 生产环境 Release 包，已开启 R8 混淆、架构仅打包 `arm64-v8a`，体积小、运行高效。
2. **`MockX-debug.apk`** (80 MB)
   - 开发调试 Debug 包，带完整调试堆栈与日志输出。

---

## 五、使用与测试指南

1. **安装新包**：
   - 在已连接电脑的设备上执行：
     ```bash
     adb install -r MockX-release.apk
     ```
2. **LSPosed 模块配置**：
   - 打开 LSPosed Manager，在模块列表中找到 **「MockX」**（包名：`io.github.souitou.mockx`）；
   - 启用模块并勾选需要伪装的目标应用（如高德地图、微信等）；
   - 强制停止目标应用后重新打开。
3. **验证项**：
   - 桌面图标展示与圆角弹性动画；
   - 界面整体排布（设置页与关于页顶部留白正常，无多余空隙）；
   - 右上角三点菜单流畅弹出、弹窗样式为官方 Squircle 大圆角；
   - 目标应用内持续定位 2 分钟以上，确认坐标锁定不回跳。
