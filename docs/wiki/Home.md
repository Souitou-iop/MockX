# MockX Code Wiki

> 本 Wiki 基于仓库当前代码状态（`master` 分支）编写，是理解 MockX 代码库的完整入口。

**MockX**（原 [XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) 的增强分支，应用包名 `io.github.souitou.mockx`）是一个 **Android 应用 + LSPosed 模块二合一** 的虚拟定位工具：

- 以 **现代 libxposed API（Xposed API 101+）** 为基础，需配合支持新 API 的 LSPosed 使用；
- 面向 **指定 App 精准伪造 GPS 位置**，可选扩展至 `system_server` / `com.android.phone` 系统级拦截；
- 内置 **国内地图源**（高德矢量/卫星、天地图）与 **GCJ-02 ↔ WGS-84 自动纠偏**；
- 构建了针对国内地图 SDK 的 **多通道防泄漏体系**（Wi-Fi 探针、基站、NMEA/GNSS 原始报文、心跳分发）。

## 快速导航

| 文档 | 内容 |
| :--- | :--- |
| [01 - 项目整体架构](01-Architecture.md) | 三进程运行形态、整体架构图、数据流、Hook 安装决策 |
| [02 - 管理端 App](02-Manager-App.md) | `manager` 包：UI 屏幕体系、ViewModel、导航、主题/本地化、高刷新率、外部控制 |
| [03 - Xposed 模块层](03-Xposed-Module.md) | `xposed` 包：入口分发、七组 Hook 类逐个详解、防泄漏链路 |
| [04 - 数据层](04-Data-Layer.md) | `data` 包：常量、数据模型、双偏好存储机制（远程/本地） |
| [05 - 关键类与函数速查](05-Key-Classes-Reference.md) | 全部关键类、公开函数签名与职责的速查表 |
| [06 - 依赖关系](06-Dependencies.md) | 技术栈版本、第三方库清单、包间依赖规则 |
| [07 - 构建与运行](07-Build-And-Run.md) | 环境要求、构建/测试命令、版本号机制、CI、安装使用流程 |

## 项目速览

| 项 | 值 |
| :--- | :--- |
| 应用名 / 图标 | MockX（全语言统一） |
| `applicationId` | `io.github.souitou.mockx` |
| 命名空间（源码包） | `com.noobexon.xposedfakelocation` |
| 上游仓库 | [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation) |
| Fork 仓库 | [Souitou-iop/XposedFakeLocation](https://github.com/Souitou-iop/XposedFakeLocation) |
| 语言 / UI | Kotlin 2.2.10 / Jetpack Compose（Material 3 + Miuix 0.9.3） |
| Xposed API | libxposed `api` / `service` 101.0.0（`minApiVersion=101`） |
| SDK | compileSdk 36 / targetSdk 36 / **minSdk 29（Android 10）** |
| JVM 目标 | 21 |
| 构建 | Gradle 8.9 + AGP 8.7.0，默认仅打包 `arm64-v8a` |
| 许可证 | MIT |

## 顶层目录结构

```text
XposedFakeLocation_transfer/
├── app/                    # 唯一的应用模块（管理端 UI + Xposed 模块同体）
│   ├── build.gradle.kts    # 模块构建配置（版本号、ABI 过滤、混淆、打包合并）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/noobexon/xposedfakelocation/
│       │   │   ├── data/          # 常量、模型、偏好仓库（共享给 hook 端与管理端）
│       │   │   ├── manager/       # 管理端 App：UI、控制、本地化、高刷
│       │   │   └── xposed/        # LSPosed 模块：入口 + 各进程 Hook 集合
│       │   ├── resources/META-INF/xposed/   # libxposed 模块声明
│       │   │   ├── module.prop    #   minApiVersion / targetApiVersion = 101
│       │   │   ├── java_init.list #   入口类：…xposed.ModuleEntry
│       │   │   └── scope.list      #   空 = scope 由管理端动态写入
│       │   └── res/               # 资源（中/英/德三语 strings）
│       └── test/                  # JVM 单元测试（坐标转换、Wi-Fi 策略）
├── docs/                   # 文档（EXTERNAL_CONTROL.md、本 wiki/）
├── gradle/                 # wrapper 与版本目录（libs.versions.toml）
├── icon/                   # 图标设计源文件（MockX.icon 及导出 PNG）
├── images/                 # README 用图片资源
├── .github/workflows/      # release.yml：发布时构建并上传 APK
├── build.gradle.kts / settings.gradle.kts
└── README.md / README.zh-CN.md / LICENSE / CONTRIBUTING.md
```

## 阅读建议

- 想快速上手构建 → 直接看 [07 - 构建与运行](07-Build-And-Run.md)；
- 想理解伪造位置如何生效 → [01 - 架构](01-Architecture.md) + [03 - Xposed 模块层](03-Xposed-Module.md)；
- 想改 UI / 加设置项 → [02 - 管理端 App](02-Manager-App.md) + [04 - 数据层](04-Data-Layer.md)；
- 查某个类干什么用 → [05 - 关键类速查](05-Key-Classes-Reference.md)。
