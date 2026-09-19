<div align="center">

<img src="images/mockx_logo.png" alt="MockX Logo" width="160"/>

# MockX

**现代化的 Android 虚拟定位应用 + LSPosed 模块 —— 内置国内地图源、GCJ-02 自动纠偏与强化的防泄漏 Hook 体系。**

[English](README.md) | **简体中文**

[![GitHub License](https://img.shields.io/github/license/Souitou-iop/MockX?style=for-the-badge&color=red&logo=googledocs&logoColor=red)](https://github.com/Souitou-iop/MockX/blob/master/LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B-green.svg?style=for-the-badge&logo=android)
![Xposed API](https://img.shields.io/badge/Xposed%20API-101%2B-8A2BE2.svg?style=for-the-badge&logo=x)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF.svg?style=for-the-badge&logo=kotlin)

</div>

---

> [!NOTE]
> MockX 是 [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) 的维护性分支，已完成品牌化与深度升级——全新应用包名（`io.github.souitou.mockx`）、Miuix 风格 UI、国内地图源接入、GCJ-02 ↔ WGS-84 纠偏、120Hz 高刷界面，以及经高德/百度/腾讯地图验证的多通道防泄漏 Hook 链条。

> [!IMPORTANT]
> **本模块基于现代 libxposed API（Xposed API 101+）构建。** 必须使用支持新 API 的最新版 **LSPosed**——旧版管理器无法加载本模块。请从官方 Telegram 频道获取最新 LSPosed：**[t.me/LSPosed](https://t.me/LSPosed)**。

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [安装](#安装)
- [使用](#使用)
- [外部控制](#外部控制)
- [开发](#开发)
- [项目文档](#项目文档)
- [参与贡献](#参与贡献)
- [许可证](#许可证)
- [免责声明](#免责声明)
- [致谢](#致谢)

---

## 功能特性

- **按 App 精准伪造** —— 在 MockX 内直接勾选目标应用，选择结果自动同步为 LSPosed 模块 scope，无需手动管理。
- **可选系统级 Hook** —— 一键把伪造扩展到 `system_server` 与 `com.android.phone`（含 MIUI/HyperOS 模糊定位服务），覆盖更深层通道。
- **国内地图源、免 Key 秒开** —— 高德矢量、高德卫星、天地图矢量（自定义 Token）、OpenStreetMap 四图源可切换；高德瓦片在国内无 Key 秒级出图。
- **GCJ-02 ↔ WGS-84 自动纠偏** —— 在高德（火星坐标）底图上点击取点，自动经不动点迭代高精度反算为 WGS-84 坐标（毫米级精度）；图钉与"我的位置"蓝点实时重投影，与道路建筑零漂移对齐。
- **防泄漏强化** —— 伪造开启期间封堵全部位置泄漏通道：Wi-Fi 扫描结果清空且 BSSID/SSID 掩码、基站信息清空并屏蔽基站事件监听、NMEA/GNSS 卫星状态/原始测量注册一律拦截、`isFromMockProvider`/`isMock` 恒返回 `false`。彻底解决国内地图"2 秒后跳回真实位置"的经典问题。
- **1Hz 心跳分发** —— 后台循环每秒向所有已注册的 `LocationListener` 推送新鲜伪造位置，防止地图 SDK 因无卫星信号超时而降级回网络定位。
- **精细化传感器伪造** —— 可自定义水平/垂直精度、海拔、海平面高度（及其精度）、速度（及其精度）与 GPS 噪声。
- **随机化** —— 在设定半径内均匀散布伪造位置，模拟真实移动轨迹（基于 Haversine 公式的圆内均匀采样）。
- **热更新** —— 目标应用仅在**首次**加入 scope 时需要强杀重启一次；此后在 MockX 中的一切改动（位置、设置、开始/停止）均实时生效，无需再次重启。
- **Root 快捷重启** —— 在目标应用页一键强杀并重启目标应用（需 Root），首次注入立即生效。
- **无界面外部控制** —— 通过广播从其他应用或 `adb shell` 控制（默认关闭）。
- **120/144Hz 高刷界面** —— 自动匹配当前物理分辨率的最高刷新率并向视图树注入帧率提示，二级列表页面在 HyperOS / ColorOS / OriginOS 上依然丝滑。
- **Miuix 原生质感 UI** —— 基于官方 `compose-miuix-ui` 组件库：Squircle 连续圆角、弹簧回弹开关、毛玻璃微岛与系统级弹窗。
- **多语言** —— 中文、英文、德文，应用内即时切换。

## 环境要求

- **已 Root 的 Android 设备**（LSPosed 依赖）。
- **Android 10 及以上**（API 29）。
- **新版 LSPosed（新 API）** —— 模块基于 libxposed API（Xposed API 101+）构建。请从官方 Telegram 频道获取最新版：**[t.me/LSPosed](https://t.me/LSPosed)**。不支持旧版 `Xposed` / `EdXposed` 及旧版 LSPosed 管理器。

## 安装

从源码构建（或从 [releases](https://github.com/Souitou-iop/MockX/releases) 页面获取 APK）：

```shell
git clone https://github.com/Souitou-iop/MockX.git
cd MockX
./gradlew assembleRelease          # 仅 arm64-v8a，约 4.5 MB
# 或：./gradlew assembleDebug
adb install app/build/outputs/apk/release/app-release.apk
```

随后：

1. 打开支持新 API 的最新版 **LSPosed 管理器**，启用 **MockX** 模块并重启一次设备。
2. 打开 MockX，在**目标应用**页勾选目标应用——模块的 LSPosed scope 会自动更新，**请勿**在 LSPosed 中手动编辑 scope。
3. **（可选）** 如需系统级 Hook（`system_server` + `com.android.phone`），在设置中开启**启用系统级 Hook**后重启设备（关闭后同样需再重启一次）。

## 使用

1. **地图** —— 点击任意位置放置伪造目标点；右上角菜单切换地图图源；支持坐标跳转与收藏管理。
2. **目标应用** —— 搜索并勾选需要接收伪造位置的应用；首次添加可点击重启按钮（需 Root）立即生效。
3. **设置** —— 调整伪造参数、Wi-Fi 身份、地图图源、语言、主题与各类开关。
4. **开始/停止** —— 悬浮按钮切换伪造状态。只有目标应用页勾选的应用会看到伪造位置，其余应用不受影响。
5. **步行模拟** —— 在地图上放置目标点后点击「步行」，MockX 会以当前真实位置为起点规划真实步行路线（高德 Web 服务，需在 设置 → 高德 Web 服务 Key 中填入自己的 Key），随后由前台服务驱动虚拟位置沿路线按选定速度连续移动，支持暂停/继续/停止。
6. 应用首次加入时：强杀并重新打开一次（用重启按钮或手动），使模块完成注入；此后所有改动实时生效。

> **隐私说明：** 规划步行路线时，起点（当前真实位置）与终点坐标会被发送到高德路径规划服务，且仅在用户主动发起规划时发送。API Key 仅保存在本机设置中，不会随应用分发或发送到其他任何地方。

## 外部控制

可选开启后，任意应用或 `adb shell` 均可无界面控制（设置 → 外部控制，默认关闭）：

```shell
adb shell am broadcast \
  -a io.github.souitou.mockx.action.START \
  -n io.github.souitou.mockx/.manager.control.ControlReceiver \
  --ed latitude 37.7749 --ed longitude -122.4194
```

三个 Action：`io.github.souitou.mockx.action.START` / `.STOP` / `.SET_LOCATION`。输入均做硬校验（纬经度范围、精度 ≤ 100000 米）。详见 [`docs/EXTERNAL_CONTROL.md`](docs/EXTERNAL_CONTROL.md)。

## 开发

```shell
./gradlew testDebugUnitTest    # 单元测试（坐标转换、Wi-Fi 策略）
./gradlew assembleRelease      # 混淆 Release（debug 签名）
./gradlew assembleRelease -PtargetAbi=all   # 全架构
```

环境要求：JDK 21、compileSdk 36 的 Android SDK。完整架构解析见 [Code Wiki](docs/wiki/Home.md)。

## 项目文档

完整的结构化 Code Wiki 位于 [`docs/wiki/`](docs/wiki/Home.md)：

| 文档 | 内容 |
| :--- | :--- |
| [整体架构](docs/wiki/01-Architecture.md) | 三种进程形态、数据流、Hook 分发决策 |
| [管理端 App](docs/wiki/02-Manager-App.md) | UI 屏幕、地图/GCJ-02 链路、高刷新率 |
| [Xposed 模块层](docs/wiki/03-Xposed-Module.md) | 入口与全部 Hook 类详解 |
| [数据层](docs/wiki/04-Data-Layer.md) | 常量、模型、远程/本地双偏好存储 |
| [关键类速查](docs/wiki/05-Key-Classes-Reference.md) | 关键类与函数速查表 |
| [依赖关系](docs/wiki/06-Dependencies.md) | 技术栈与依赖规则 |
| [构建与运行](docs/wiki/07-Build-And-Run.md) | 环境、命令、CI、故障排查 |

## 参与贡献

欢迎贡献！请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md) 了解项目结构、编码规范与 PR 流程。

## 许可证

基于 `MIT License` 开源。详见 [`LICENSE`](LICENSE)。

## 免责声明

本应用仅供**开发与测试用途**。滥用虚拟定位可能违反其他应用与服务的服务条款。风险自负，不对设备的任何损坏承担任何责任。

## 致谢

- [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) —— 本分支的上游项目。
- [GpsSetter](https://github.com/Android1500/GpsSetter) —— 上游项目的灵感来源。
- [libxposed API](https://github.com/libxposed/api) —— 本模块构建所依赖的现代 Xposed API。
- [LSPosed](https://github.com/LSPosed/LSPosed)（[Telegram](https://t.me/LSPosed)）—— Xposed 框架管理器。
- [OSMDroid](https://github.com/osmdroid/osmdroid) —— 开源地图引擎。
- [compose-miuix-ui](https://github.com/miuix-kotlin-multiplatform/miuix) —— Miuix 组件库。
- [Jetpack Compose](https://developer.android.com/jetpack/compose) 与 [Material Design 3](https://m3.material.io/) —— 现代 UI 工具包与设计系统。
- [Line Awesome Icons](https://icons8.com/line-awesome) —— 应用内图标集。
- [FuckLocation](https://github.com/Mikotwa/FuckLocation) —— Android 定位 Hook 的补充参考。
